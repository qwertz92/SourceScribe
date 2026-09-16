package app.sourcescribe

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.sourcescribe.core.*
import app.sourcescribe.core.providers.*
import app.sourcescribe.data.*
import app.sourcescribe.extractor.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/** The UI sees source metadata and durable records, never signed media URLs or saved API keys. */
data class SourcePreview(val resolved: ResolvedSource, val config: JobConfig, val previousJob: String?)

/** The settings of the draft a reader types rather than picks, each in a text field of the new-source screen. */
enum class TypedSetting {
    BUDGET, CAPTION_LANGUAGES, STT_LANGUAGE, CONTEXT_TERMS;

    fun of(config: JobConfig): Any? = when (this) {
        BUDGET -> config.maxCostMicrousd
        CAPTION_LANGUAGES -> config.preferredLanguages
        STT_LANGUAGE -> config.language
        CONTEXT_TERMS -> config.contextTerms
    }
}

/**
 * Moves the epoch of a [TypedSetting] whenever anything other than a keystroke in its own field changes it.
 *
 * A field over the draft cannot find that out by comparing. A keystroke reaches the draft at once, the draft
 * reaches the screen a frame or two later, and in between the value a field is composed with is older than
 * the text it holds — a late value that looks exactly like one a preset or a re-prepared job put there. The
 * field shows its own text for as long as the epoch it was typed under is current, and the setting as the
 * draft holds it once it is not.
 *
 * [session] is drawn fresh for every view model. A field saves its text with the epoch it was typed under,
 * and after the process died the draft that text was typed into is gone; a new session keeps the restored
 * text from counting as typed into the draft that replaced it.
 */
data class DraftEdits(
    val session: String = java.util.UUID.randomUUID().toString(),
    val changes: Map<TypedSetting, Long> = emptyMap(),
) {
    fun epoch(setting: TypedSetting): String = "$session:${changes[setting] ?: 0L}"

    /** The edits once the draft went from [before] to [after]; [typed] is the setting a keystroke changed, if any. */
    fun after(before: JobConfig, after: JobConfig, typed: TypedSetting?): DraftEdits {
        val moved = TypedSetting.entries.filter { it != typed && it.of(before) != it.of(after) }
        return if (moved.isEmpty()) this else copy(changes = changes + moved.associateWith { (changes[it] ?: 0L) + 1 })
    }
}

data class ScreenState(
    val busy: Boolean = false,
    val starting: Boolean = false,
    /**
     * True from the start of the view model until the bundled engine is ready or its preparation failed. It disables
     * nothing: an action that needs the engine waits for it inside the engine manager (ADR 0012).
     */
    val preparingEngine: Boolean = false,
    /** True while an action that needs the engine waits for that preparation, which the new-source screen says. */
    val waitingForEngine: Boolean = false,
    /** Written only through `MainViewModel.withDraft`, which keeps [draftEdits] in step with it. */
    val draft: JobConfig? = null,
    val draftEdits: DraftEdits = DraftEdits(),
    val message: String? = null,
    val previews: List<SourcePreview> = emptyList(),
    val document: TranscriptDocument? = null,
    /** The stored export name of the open result, or null while it uses the generated one. */
    val documentName: String? = null,
    val credentials: List<CredentialInfo> = emptyList(),
    val installations: List<EngineInstallation> = emptyList(),
    /**
     * The installation the app is actually using, which is not always the one the reader activated: an engine
     * whose self-test this app build contradicted stops being healthy and the bundled one takes over (ADR 0012).
     * Null while the engine is still being prepared and the list is empty.
     */
    val activeEngineId: String? = null,
    /** The installation a rollback would activate, while the confirmation that names it is open. */
    val rollbackTarget: EngineInstallation? = null,
    val update: AvailableEngine? = null,
    val shareUri: String? = null,
    val shareMime: String = "text/markdown",
    /**
     * The folder the export that [message] reports was written into, while that message stands. The screen
     * offers to open it beside the message, which is the moment a reader wants to look at the file. Null
     * whenever the message is about something else or the export did not reach a folder.
     */
    val exportedFolder: String? = null,
    val information: String? = null,
    val informationShareable: Boolean = false,
)

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val coordinator: JobCoordinator,
    private val records: SourceScribeDao,
    private val settingsStore: SettingsStore,
    private val credentialStore: CredentialStore,
    private val artifactFiles: ArtifactFiles,
    private val engines: EngineUpdateManager,
    private val audioImport: AudioImport,
    private val diagnostics: Diagnostics,
) : ViewModel() {
    private val mutable = MutableStateFlow(ScreenState(preparingEngine = true))
    private val actionGate = Mutex()
    val screen = mutable.asStateFlow()
    val settings = settingsStore.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    val jobs = records.observeJobs().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val sources = records.observeSources().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val attempts = records.observeAttempts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val artifacts = records.observeArtifacts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val exports = records.observeExports().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val remoteDeletionJobIds = records.observeRemoteDeletionJobIds().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val previewOwner = java.util.UUID.randomUUID().toString().also(SourceFiles::registerPreviewOwner)
    private val previewRevision = AtomicLong()
    private val abandonedImports = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /**
     * Start-up work runs beside the screen instead of as an action, so it never holds the action gate or sets `busy`
     * (ADR 0012). Recovery takes well under a second, and every exclusive action waits for it, so none of them runs
     * before interrupted work is reconciled, as before. The engine preparation can take seconds after an install or an
     * update; only the actions that need the engine wait for it, see [awaitEngine].
     */
    private val recovery: Job = viewModelScope.launch(Dispatchers.IO) { startUpStep { coordinator.recover() } }
    private val enginePreparation: Job = viewModelScope.launch(Dispatchers.IO) {
        startUpStep { refreshEngines() }
    }.also { preparation ->
        // A completion handler rather than `finally`: it also runs for a preparation cancelled before it started.
        preparation.invokeOnCompletion { _ -> mutable.update { state -> state.copy(preparingEngine = false) } }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) { startUpStep { refreshCredentials() } }
        // A shared copy has done its job once the receiving app read it; a day later it only keeps a transcript in
        // the cache (defect 42). Best effort: a copy that cannot be removed now is left for a later start.
        viewModelScope.launch(Dispatchers.IO) {
            ShareCache.deleteExpired(File(context.cacheDir, ShareCache.DIRECTORY), System.currentTimeMillis())
        }
    }

    fun inspect(text: String, config: JobConfig) {
        val revision = previewRevision.incrementAndGet()
        action(revision) {
        val selected = config.copy(uploadApproved = false, captionTrackId = null, audioTrackId = null)
        val sources = SourceResolver.sharedText(text)
        // Reading a YouTube address needs the engine. An address the resolver refused is reported without waiting.
        awaitEngine()
        val previews = sources.map { source ->
            val resolved = coordinator.inspect(source)
            val captions = TrackSelection.captions(resolved, selected)
            val audio = TrackSelection.audio(resolved, null)
            SourcePreview(resolved, selected.copy(captionTrackId = captions.firstOrNull()?.id,
                audioTrackId = audio?.id), records.latestJob(source.id)?.id)
        }
        if (revision == previewRevision.get()) {
            abandonCurrentImports()
            mutable.update { it.withDraft(selected).copy(previews = previews, message = null) }
        }
        }
    }

    fun selectTrack(sourceId: String, caption: String? = null, audio: String? = null) {
        if (screen.value.starting) return
        previewRevision.incrementAndGet()
        val audioChanged = audio != null && screen.value.previews.any { it.resolved.source.id == sourceId && it.config.audioTrackId != audio }
        mutable.update { state -> state.withDraft(if (audioChanged) state.draft?.copy(uploadApproved = false) else state.draft).copy(previews = state.previews.map {
            if (it.resolved.source.id != sourceId) it else it.copy(config = it.config.copy(
                captionTrackId = caption ?: it.config.captionTrackId, audioTrackId = audio ?: it.config.audioTrackId,
                uploadApproved = it.config.uploadApproved && (audio == null || audio == it.config.audioTrackId)))
        }) }
    }

    fun startPreviews() = action(starting = true) {
        val current = screen.value
        val previews = current.previews.map { it.copy(config = configurationForStart(it.config, current.credentials)) }
        check(previews.isNotEmpty())
        previews.forEach { preview ->
            val error = previewError(preview, current.credentials)
            if (error != null) {
                mutable.update { it.copy(message = error) }
                return@action
            }
        }
        coordinator.createBatch(previews.map { it.resolved.source to it.config })
        previews.forEach { preview ->
            SourceFiles.releasePreview(preview.resolved.source.id, previewOwner)
        }
        mutable.update { it.withDraft(it.draft?.copy(uploadApproved = false)).copy(previews = emptyList(), message = "JOBS_CREATED") }
    }

    fun clearPreview() {
        if (screen.value.starting) return
        previewRevision.incrementAndGet()
        abandonCurrentImports()
        mutable.update { it.withDraft(it.draft?.copy(uploadApproved = false)).copy(previews = emptyList()) }
        if (abandonedImports.isNotEmpty()) action { }
    }
    private fun abandonCurrentImports() {
        abandonedImports.addAll(screen.value.previews.filter { it.resolved.source.kind == SourceKind.LOCAL_AUDIO }.map { it.resolved.source.id })
    }
    private suspend fun cleanupAbandonedImports() {
        val visible = screen.value.previews.map { it.resolved.source.id }.toSet()
        for (id in abandonedImports.toList()) {
            if (id !in visible) {
                SourceFiles.releasePreview(id, previewOwner)
                coordinator.discardImportPreview(id)
            }
            abandonedImports.remove(id)
        }
    }
    /** A change to the draft from a control that picks a value, a preset, or the button that raises the limit. */
    fun updatePreviewConfig(config: JobConfig) { changeDraft(null) { config } }

    /**
     * A keystroke in the field over [setting]. [change] is applied to the draft as it is at this moment rather
     * than to the copy the field was composed with, which can be a frame older, and the field keeps its text.
     *
     * False when the draft did not take it. While a start is under way the draft is what that start reads, so a
     * keystroke that reached a field still composed as enabled is refused, and the field must then not keep the
     * text as typed: it would go on showing a value that neither this start nor a later one uses (round 17).
     */
    fun typeIntoDraft(setting: TypedSetting, change: (JobConfig) -> JobConfig): Boolean = changeDraft(setting, change)

    private fun changeDraft(typed: TypedSetting?, change: (JobConfig) -> JobConfig): Boolean {
        if (screen.value.starting) return false
        previewRevision.incrementAndGet()
        mutable.update { state ->
            val config = change(state.draft ?: settings.value.defaults)
            val effective = if (state.previews.any { it.resolved.source.kind == SourceKind.LOCAL_AUDIO }) config.copy(mode = AcquisitionMode.STT_ONLY) else config
            state.withDraft(effective, typed).copy(previews = state.previews.map { preview ->
                val selected = effective.copy(mode = if (preview.resolved.source.kind == SourceKind.LOCAL_AUDIO) AcquisitionMode.STT_ONLY else config.mode,
                    captionTrackId = null, audioTrackId = null)
                val captions = TrackSelection.captions(preview.resolved, selected)
                preview.copy(config = selected.copy(captionTrackId = captions.firstOrNull { it.id == preview.config.captionTrackId }?.id ?: captions.firstOrNull()?.id,
                    audioTrackId = preview.resolved.audio.firstOrNull { it.id == preview.config.audioTrackId }?.id ?: TrackSelection.audio(preview.resolved, null)?.id))
            })
        }
        return true
    }

    /**
     * Every write to the draft goes through here, so that [DraftEdits] moves for each typed setting the write
     * changes; [typed] names the one a keystroke changed, whose field keeps its text. Before a draft exists the
     * screen shows the stored defaults, so a first draft is compared against those.
     */
    private fun ScreenState.withDraft(next: JobConfig?, typed: TypedSetting? = null): ScreenState {
        val defaults = settings.value.defaults
        return copy(draft = next, draftEdits = draftEdits.after(draft ?: defaults, next ?: defaults, typed))
    }

    fun importAudio(uri: Uri, config: JobConfig) {
        val revision = previewRevision.incrementAndGet()
        action(revision) {
            val source = audioImport.import(uri, previewOwner)
            val previousJob = records.latestJob(source.id)?.id
            if (revision != previewRevision.get()) {
                abandonedImports += source.id
                return@action
            }
            abandonCurrentImports()
            val selected = config.copy(mode = AcquisitionMode.STT_ONLY, uploadApproved = false, captionTrackId = null, audioTrackId = null)
            mutable.update { it.withDraft(selected).copy(previews = listOf(SourcePreview(
                ResolvedSource(source, emptyList(), emptyList(), emptyMap()), selected, previousJob))) }
        }
    }

    fun dismissMessage() { mutable.update { it.copy(message = null, exportedFolder = null) } }
    fun exportPermissionError() { mutable.update { it.copy(message = "EXPORT_PERMISSION_REQUIRED", exportedFolder = null) } }
    /** Surfaces a local, non-exceptional outcome such as an empty clipboard. */
    fun notice(code: String) { mutable.update { it.copy(message = code, exportedFolder = null) } }
    fun exportArtifact(id: String, format: ExportFormat, treeUri: String) = action {
        val result = coordinator.exportArtifact(id, format, treeUri)
        mutable.update { it.copy(message = "EXPORT_${result.state.name}", exportedFolder = writtenFolder(result)) }
    }
    fun retryExport(id: String, treeUri: String) = action {
        val result = coordinator.retryExport(id, treeUri)
        mutable.update { it.copy(message = "EXPORT_${result.state.name}", exportedFolder = writtenFolder(result)) }
    }
    /** The folder an export actually reached, and nothing where it did not: an offer to open it must not lie. */
    private fun writtenFolder(export: ExportRow): String? =
        export.treeUri.takeIf { export.state == ExportState.EXPORTED && it.isNotBlank() }
    fun reconcileExports() = action { coordinator.reconcileExports() }
    fun shareArtifact(id: String) = action {
        val document = artifactFiles.read(id)
        val chosen = records.artifact(id)?.displayName
        val directory = File(context.cacheDir, ShareCache.DIRECTORY).also { check(it.isDirectory || it.mkdirs()) }
        val file = File(directory, TranscriptExporter.fileName(document, ExportFormat.MARKDOWN, chosen))
        FileOutputStream(file).use { output -> output.write(TranscriptExporter.render(document, ExportFormat.MARKDOWN).toByteArray()); output.fd.sync() }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        mutable.update { it.copy(shareUri = uri.toString(), shareMime = "text/markdown") }
    }
    fun shareConsumed() { mutable.update { it.copy(shareUri = null) } }
    fun showDiagnostics() = action {
        val text = diagnostics.build()
        mutable.update { it.copy(information = text, informationShareable = true) }
    }
    fun showLicenses() = action {
        val text = context.assets.open("legal/THIRD_PARTY_NOTICES.txt").bufferedReader().use { it.readText() } +
            "\n\n" + context.assets.open("legal/LICENSE.txt").bufferedReader().use { it.readText() }
        mutable.update { it.copy(information = text, informationShareable = false) }
    }
    fun closeInformation() { mutable.update { it.copy(information = null) } }
    fun shareDiagnostics() = action {
        check(screen.value.informationShareable)
        val text = requireNotNull(screen.value.information)
        val directory = File(context.cacheDir, ShareCache.DIRECTORY).also { check(it.isDirectory || it.mkdirs()) }
        val file = File(directory, "sourcescribe-diagnostics.txt")
        FileOutputStream(file).use { it.write(text.toByteArray()); it.fd.sync() }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        mutable.update { it.copy(shareUri = uri.toString(), shareMime = "text/plain") }
    }
    fun cancel(jobId: String) = action(exclusive = false) { coordinator.cancel(jobId) }
    fun retry(jobId: String, missingOnly: Boolean) = action { coordinator.retry(jobId, missingOnly) }
    fun resume(jobId: String) = action { coordinator.resume(jobId) }
    fun deleteJob(jobId: String) = action { coordinator.delete(jobId) }
    fun deleteRemote(jobId: String) = action {
        coordinator.deleteRemote(jobId)
        mutable.update { it.copy(message = "REMOTE_DELETE_CONFIRMED") }
    }
    fun prepareAgain(jobId: String, config: JobConfig) {
        val revision = previewRevision.incrementAndGet()
        action(revision) {
        val job = requireNotNull(records.job(jobId))
        val source = coordinator.sourceForPreview(job.sourceId, previewOwner)
        if (source.kind == SourceKind.YOUTUBE) awaitEngine()
        val resolved = if (source.kind == SourceKind.YOUTUBE) coordinator.inspect(source)
            else ResolvedSource(source, emptyList(), emptyList(), emptyMap())
        // Re-preparing reuses what the job was configured with, so limits and options can be adjusted in place.
        val stored = decodeStoredJobConfig(job.config) ?: config
        val base = stored.copy(
            mode = if (source.kind == SourceKind.LOCAL_AUDIO) AcquisitionMode.STT_ONLY else stored.mode,
            uploadApproved = false, captionTrackId = null, audioTrackId = null,
        )
        val selected = base.copy(
            captionTrackId = TrackSelection.captions(resolved, base).firstOrNull()?.id,
            audioTrackId = TrackSelection.audio(resolved, null)?.id,
        )
        if (revision == previewRevision.get()) {
            abandonCurrentImports()
            mutable.update { it.withDraft(selected).copy(previews = listOf(SourcePreview(resolved, selected, jobId))) }
        } else if (source.kind == SourceKind.LOCAL_AUDIO) {
            abandonedImports += source.id
        }
        }
    }
    fun openArtifact(id: String) = action {
        val document = artifactFiles.read(id)
        val name = records.artifact(id)?.displayName
        mutable.update { it.copy(document = document, documentName = name) }
    }
    fun closeArtifact() { mutable.update { it.copy(document = null, documentName = null) } }

    /** Stores a reader-chosen export name for one result, or clears it back to the generated name. */
    fun renameArtifact(id: String, name: String?) = action {
        val stored = name?.let(TranscriptExporter::customStem)
        check(records.renameArtifact(id, stored) == 1)
        mutable.update { state ->
            if (state.document?.artifactId == id) state.copy(documentName = stored, message = "FILE_NAME_SAVED")
            else state.copy(message = "FILE_NAME_SAVED")
        }
    }
    fun saveDefaults(config: JobConfig) = action {
        settingsStore.update { it.copy(defaults = config.copy(uploadApproved = false)) }
        mutable.update { it.copy(message = "DEFAULTS_SAVED") }
    }
    fun savePreset(name: String, config: JobConfig) = action {
        settingsStore.update { it.copy(presets = it.presets + (name to config.copy(uploadApproved = false))) }
        mutable.update { it.copy(message = "PRESET_SAVED") }
    }
    fun deletePreset(name: String) = action { settingsStore.update { it.copy(presets = it.presets - name) } }

    /**
     * Stores [terms] under [name] so the next job on the same topic can pick them again (item 13).
     *
     * The decision is made inside the store's own update, on the settings as they are at that moment, rather
     * than on a copy read a moment earlier: two saves in a row must not have the second one written against a
     * list that no longer exists. `KeytermSets.saved` answers null for anything this app will not keep, and
     * the reader is told that rather than shown a set that is not there.
     */
    fun saveKeytermSet(name: String, terms: List<String>) = action {
        var refused = false
        settingsStore.update { current ->
            val updated = KeytermSets.saved(current.keytermSets, name, terms)
            if (updated == null) { refused = true; current } else current.copy(keytermSets = updated)
        }
        mutable.update { it.copy(message = if (refused) "KEYTERM_SET_REFUSED" else "KEYTERM_SET_SAVED") }
    }

    fun deleteKeytermSet(name: String) = action {
        settingsStore.update { it.copy(keytermSets = it.keytermSets - name.trim()) }
    }
    fun changeSettings(change: (AppSettings) -> AppSettings) = action { settingsStore.update(change) }
    fun saveCredential(provider: Provider, region: Region, key: String) = action {
        val id = credentialStore.save(provider, region, key)
        val selected = modelDefaults(settingsStore.settings.first().defaults.copy(
            provider = provider, region = region, credentialId = id, uploadApproved = false), models(provider).first())
        settingsStore.update { it.copy(defaults = selected) }
        refreshCredentials()
        mutable.update { it.copy(message = "KEY_SAVED") }
    }
    fun deleteCredential(id: String) = action {
        credentialStore.delete(id)
        fun clear(config: JobConfig) = if (config.credentialId == id) config.copy(credentialId = null, uploadApproved = false) else config
        settingsStore.update { it.copy(defaults = clear(it.defaults), presets = it.presets.mapValues { entry -> clear(entry.value) }) }
        mutable.update { it.withDraft(it.draft?.let(::clear)).copy(previews = it.previews.map { preview -> preview.copy(config = clear(preview.config)) }) }
        refreshCredentials()
    }
    fun restoreCredential(jobId: String, key: String) = action {
        val job = records.job(jobId) ?: throw IllegalStateException("JOB_NOT_FOUND")
        check(!job.deleteRequested)
        val config = kotlinx.serialization.json.Json.decodeFromString<JobConfig>(job.config)
        credentialStore.restore(requireNotNull(config.credentialId), requireNotNull(config.provider), config.region, key)
        refreshCredentials()
        mutable.update { it.copy(message = "KEY_SAVED") }
    }
    fun replaceCredential(id: String, provider: Provider, region: Region, key: String) = action {
        credentialStore.replace(id, provider, region, key)
        refreshCredentials()
        mutable.update { it.copy(message = "KEY_SAVED") }
    }
    fun checkEngine(channel: EngineChannel) = action {
        val available = engines.check(channel, force = true)
        mutable.update { it.copy(update = available, message = if (available == null) "ENGINE_CURRENT" else null) }
    }
    fun stageAndActivate(probeUrl: String) = action {
        val update = requireNotNull(screen.value.update)
        val staged = engines.stage(update)
        engines.activate(staged.id, SourceResolver.youtube(probeUrl))
        refreshEngines()
        mutable.update { it.copy(update = null, message = "ENGINE_ACTIVE") }
    }
    /**
     * Opens the confirmation for going back, named with the installation it would activate. Going back is not
     * one tap: an older engine is not safer for being older, and it can lack fixes the current one has.
     */
    fun prepareRollback() = action {
        val target = engines.rollbackTarget() ?: throw EngineUpdateException(EngineUpdateCode.NO_PREVIOUS)
        mutable.update { it.copy(rollbackTarget = target) }
    }
    fun cancelRollback() { mutable.update { it.copy(rollbackTarget = null) } }
    /**
     * Goes back to [expectedId] and nowhere else: the installation the confirmation named. The confirmation closes
     * once the switch is actually under way. While another action holds the screen, the tap is answered with
     * ACTION_BUSY and the confirmation stays, so the version it names can still be confirmed (round 17).
     */
    fun rollback(expectedId: String) = action {
        mutable.update { it.copy(rollbackTarget = null) }
        engines.rollback(expectedId)
        refreshEngines()
        mutable.update { it.copy(message = "ENGINE_ACTIVE") }
    }

    /**
     * The engine list and, beside it, the installation the app is actually using. `active()` is asked rather than
     * the recorded pointer, because that is the function the rest of the app binds new work to: it answers the
     * bundled engine whenever the activated one is not healthy.
     */
    private suspend fun refreshEngines() {
        val installed = engines.installations()
        val active = engines.active().id
        mutable.update { it.copy(installations = installed, activeEngineId = active) }
    }

    private fun refreshCredentials() { mutable.update { it.copy(credentials = credentialStore.list()) } }

    /** Runs one piece of start-up work and reports its failure as an action would, without holding the screen. */
    private suspend fun startUpStep(step: suspend () -> Unit) {
        try { step() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { mutable.update { it.copy(message = failureCode(failure)) } }
    }

    /**
     * Waits for the start-up engine preparation while it still runs, and lets the new-source screen say so meanwhile.
     * The engine manager's lock would hold the action back anyway; this gives the wait its reason on the screen.
     */
    private suspend fun awaitEngine() {
        if (!enginePreparation.isActive) return
        mutable.update { it.copy(waitingForEngine = true) }
        try { enginePreparation.join() } finally { mutable.update { it.copy(waitingForEngine = false) } }
    }

    private fun failureCode(failure: Exception): String = when (failure) {
        is InvalidSource -> failure.reason
        is ExtractionException -> failure.failure.name
        is EngineUpdateException -> "ENGINE_${failure.code.name}"
        is CredentialException -> "KEY_${failure.code.name}"
        is ProviderError -> failure.code.name
        is JobActionException -> failure.code
        is AudioImportException -> "AUDIO_IMPORT_${failure.code.name}"
        // Same wording as the coordinator uses, so one code covers the case everywhere.
        else -> "LOCAL_PROCESSING_FAILED"
    }

    private fun action(revision: Long? = null, starting: Boolean = false, exclusive: Boolean = true, block: suspend () -> Unit) {
        if (exclusive && !actionGate.tryLock()) {
            mutable.update { it.copy(message = "ACTION_BUSY") }
            return
        }
        // The folder offer belongs to the message it was set with, so a new action drops it with that message.
        if (exclusive) mutable.update { it.copy(busy = true, starting = starting, message = null, exportedFolder = null) }
        viewModelScope.launch {
            // An exclusive action waits for start-up recovery, which is short, so it never runs before it.
            try { withContext(Dispatchers.IO) { if (exclusive) { recovery.join(); cleanupAbandonedImports() }; block() } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                val code = failureCode(failure)
                if (revision == null || revision == previewRevision.get()) mutable.update { it.copy(message = code) }
            } finally {
                try { if (exclusive) withContext(NonCancellable + Dispatchers.IO) { cleanupAbandonedImports() } }
                catch (_: Exception) { mutable.update { it.copy(message = "CLEANUP_FAILED") } }
                finally { if (exclusive) { mutable.update { it.copy(busy = false, starting = false) }; actionGate.unlock() } }
            }
        }
    }

    override fun onCleared() {
        SourceFiles.unregisterPreviewOwner(previewOwner)
    }

    companion object {
        fun modelDefaults(config: JobConfig, model: String): JobConfig {
            val selected = config.copy(model = model, uploadApproved = false)
            val cap = capabilities(selected)
            return selected.copy(diarization = false, wordTimestamps = false,
                segmentTimestamps = cap?.segmentTimestamps == true,
                contextTerms = if (cap?.contextTerms == true) config.contextTerms else emptyList())
        }

        /** Android clipboard transactions are bounded; every UTF-16 code point remains in exactly one part. */
        fun clipboardRanges(text: String): List<IntRange> = buildList {
            var start = 0
            while (start < text.length) {
                var end = minOf(text.length, start + 64_000)
                if (end < text.length && text[end - 1].isHighSurrogate() && text[end].isLowSurrogate()) end--
                add(start until end)
                start = end
            }
        }

        /**
         * The job a deliberate Start creates from [config]. Only this action persists the upload approval, bound to
         * its exact job configuration.
         *
         * It also leaves blank keyterms out. The field over the terms drops them as they are typed, but a list
         * stored before it did, in a job prepared again or in a preset, can still hold one. `[""]` shows as an empty
         * field, and every provider refuses the whole list over it, so such a job stayed refused with nothing on
         * the screen to remove (round 17). A blank entry asks for nothing, and the job starts without it.
         */
        fun configurationForStart(config: JobConfig, credentials: List<CredentialInfo>): JobConfig = config.copy(
            uploadApproved = config.mode != AcquisitionMode.CAPTIONS_ONLY && config.model != null &&
                credentials.any { it.id == config.credentialId && it.provider == config.provider && it.region == config.region },
            contextTerms = ContextTerms.withoutBlanks(config.contextTerms),
            // The one place a length limit is written since 0.4.0, where the screen stopped asking for one:
            // the app's own ceiling. A draft restored from stored defaults, from a preset or from a job an
            // older version created can still carry a lower number, and with no field left to raise it that
            // number would refuse a source with nothing on the screen to change about it.
            maxAudioSeconds = JobLimits.MAX_AUDIO_SECONDS,
        )

        /**
         * Every code [previewError] returns that the preview card shows as a sentence: all of them except
         * `SOURCE_LONGER_THAN_LIMIT`, which gets a warning with a button of its own. The card reserves the
         * height of the tallest of their texts, so switching from one to another moves nothing below it. A
         * code missing here is still shown in full and only moves the card when it appears;
         * `ViewRulesTest.everyErrorThePreviewCanShowHasItsHeightReserved` is there to notice one.
         */
        val PREVIEW_ERRORS_SHOWN_AS_TEXT = listOf(
            "NO_ACCEPTABLE_CAPTIONS", "PROVIDER_REQUIRED", "CREDENTIAL_REQUIRED", "NO_AUDIO", "CHOOSE_AUDIO_TRACK",
            // And the ones that come from `configError`. Not AUDIO_DURATION_LIMIT since 0.4.0: the preview
            // asks `configError` about the configuration a start would create, and `configurationForStart`
            // writes the app's own ceiling into it, so no draft can carry a length limit it would refuse.
            // Not UPLOAD_APPROVAL_REQUIRED: the preview asks
            // `configError` only once a model and a key for the chosen provider and region are there, and exactly
            // then `configurationForStart` has set the approval, so that branch never answers the preview. Not
            // CONTEXT_TERM_BLANK either, since round 17: `configurationForStart` leaves blank terms out before
            // `configError` sees the list.
            "BUDGET_INVALID", "PROVIDER_CAPABILITY_OR_CREDENTIAL_INVALID",
            "UNSUPPORTED_OPTION", "PRICE_UNKNOWN", "SOURCE_DURATION_UNKNOWN",
        )

        fun previewError(preview: SourcePreview, credentials: List<CredentialInfo>): String? {
            val config = configurationForStart(preview.config, credentials)
            val captions = TrackSelection.captions(preview.resolved, config)
            if (config.mode == AcquisitionMode.CAPTIONS_ONLY && captions.isEmpty()) return "NO_ACCEPTABLE_CAPTIONS"
            val requiresStt = config.mode in setOf(AcquisitionMode.STT_ONLY, AcquisitionMode.BOTH)
            val availableKey = credentials.any { it.id == config.credentialId && it.provider == config.provider && it.region == config.region }
            if (requiresStt) {
                if (config.provider == null || config.model == null) return "PROVIDER_REQUIRED"
                if (!availableKey) return "CREDENTIAL_REQUIRED"
                configError(config)?.let { return it }
            }
            val sttPossible = requiresStt ||
                config.mode == AcquisitionMode.CAPTIONS_THEN_STT && captions.isEmpty() && config.uploadApproved && availableKey
            if (sttPossible && preview.resolved.source.kind == SourceKind.YOUTUBE &&
                TrackSelection.audio(preview.resolved, config.audioTrackId) == null) {
                return if (preview.resolved.audio.isEmpty()) "NO_AUDIO" else "CHOOSE_AUDIO_TRACK"
            }
            // The length decides two things a job cannot do without: what it will cost, and whether it fits
            // into one run at all. Since 0.4.0 it comes from the source alone — yt-dlp's metadata for a video,
            // ffprobe for an imported file — so a source that states none (a live stream, for instance) is
            // refused here rather than at the point where the price would have to be invented.
            if (sttPossible && preview.resolved.source.durationMs == null) return "SOURCE_DURATION_UNKNOWN"
            // The source length is already known here, so a doomed run is refused before it costs a download.
            if (sttPossible && JobLimits.exceeds(preview.resolved.source.durationMs)) {
                return "SOURCE_LONGER_THAN_LIMIT"
            }
            return null
        }

        const val MAX_AUDIO_SECONDS = JobLimits.MAX_AUDIO_SECONDS

        fun configError(config: JobConfig): String? = when {
            config.maxAudioSeconds !in 1..MAX_AUDIO_SECONDS -> "AUDIO_DURATION_LIMIT"
            config.maxCostMicrousd?.let { it < 0 } == true -> "BUDGET_INVALID"
            config.mode == AcquisitionMode.CAPTIONS_ONLY -> null
            config.provider == null || config.model == null -> "PROVIDER_REQUIRED"
            config.credentialId == null -> "CREDENTIAL_REQUIRED"
            !config.uploadApproved -> "UPLOAD_APPROVAL_REQUIRED"
            else -> {
                val cap = capabilities(config)
                when {
                    cap == null || config.region !in cap.regions -> "PROVIDER_CAPABILITY_OR_CREDENTIAL_INVALID"
                    // Before the capability question, the way both provider paths order it: a blank entry is
                    // refused as invalid input whether or not the model supports a prompt at all.
                    ContextTerms.refused(config.contextTerms) -> "CONTEXT_TERM_BLANK"
                    config.diarization && !cap.diarization || config.wordTimestamps && !cap.wordTimestamps ||
                        config.segmentTimestamps && !cap.segmentTimestamps || config.contextTerms.isNotEmpty() && !cap.contextTerms -> "UNSUPPORTED_OPTION"
                    config.maxCostMicrousd != null && cap.priceMicrousdPerHour == null -> "PRICE_UNKNOWN"
                    else -> null
                }
            }
        }

        /**
         * The model a job would run with at [provider], for the provider list on the new-source screen.
         *
         * For the provider that is selected that is the chosen model; for the others it is the one the app
         * would set the moment their row is tapped, which is what `ConfigControls` does. A model stored for a
         * provider but no longer offered — a configuration an older version wrote — is not claimed to be in
         * use, because a run would not use it either.
         */
        fun modelInUse(config: JobConfig, provider: Provider): String {
            val models = models(provider)
            return config.model?.takeIf { config.provider == provider && it in models } ?: models.first()
        }

        fun models(provider: Provider): List<String> = when (provider) {
            Provider.GROQ -> listOf(GroqAdapter.MODEL_V3, GroqAdapter.MODEL_TURBO)
            Provider.OPENAI -> listOf(OpenAiAdapter.MODEL_GPT_TRANSCRIBE, OpenAiAdapter.MODEL_WHISPER_1, OpenAiAdapter.MODEL_GPT_4O_TRANSCRIBE_DIARIZE)
            Provider.ASSEMBLYAI -> listOf(AssemblyAiAdapter.MODEL_U35, AssemblyAiAdapter.MODEL_U2)
        }
        fun capabilities(config: JobConfig): ProviderCapabilities? = try {
            val adapter = when (config.provider) { Provider.GROQ -> GroqAdapter(); Provider.OPENAI -> OpenAiAdapter(); Provider.ASSEMBLYAI -> AssemblyAiAdapter(); null -> null }
            config.model?.let { adapter?.capabilities(it) }
        } catch (_: ProviderError) { null }

        /**
         * What the whole source is estimated to cost, added up the way a run is actually charged.
         *
         * A job is submitted chunk by chunk and every chunk is priced on its own, so the total is not the
         * length times the hourly rate: the rounding happens per chunk, a minimum billed length applies per
         * chunk, and the surcharges belong to the chunk as well. That is also how the budget is enforced
         * later, which is the reason this function and not a second formula stands behind the figure on
         * screen. `null` means no checked rate is stored for this model, which does not mean free.
         */
        fun estimatedCostMicrousd(config: JobConfig, durationMs: Long): Long? {
            val capability = capabilities(config) ?: return null
            if (durationMs !in 1..SttStep.MAX_AUDIO_DURATION_MS) return null
            // A term list the providers refuse has no price, the same way a source past the ceiling has
            // none: quoting one would put a figure in dollars above the line that says the run is refused.
            // The formula below stays a pure price function so that nothing can reach the budget gate with
            // an unknown estimate; this screen is where the question „is this job runnable“ belongs.
            if (ContextTerms.refused(config.contextTerms)) return null
            var total = 0L
            for (window in SttStep.chunkPlan(durationMs)) {
                val part = SttStep.estimateCostMicrousd(capability, config, window.durationMs) ?: return null
                total = if (total > Long.MAX_VALUE - part) return Long.MAX_VALUE else total + part
            }
            return total
        }

        /**
         * True when this source is longer than a job may run. The cost row reads this so that it never
         * prices a source the same screen refuses a line below.
         *
         * Until 0.3.0 two different numbers could stop a run — the app's ceiling and a length limit the
         * reader typed — and getting that pair wrong was defect 5: round 12 consulted the ceiling alone, so
         * a two-hour source under a one-hour limit was priced in dollars directly above the warning that
         * the run would be refused. Since 0.4.0 there is only the ceiling, and a job takes its length from
         * the source.
         */
        fun sourceTooLong(durationMs: Long?): Boolean = JobLimits.exceeds(durationMs)
    }
}
