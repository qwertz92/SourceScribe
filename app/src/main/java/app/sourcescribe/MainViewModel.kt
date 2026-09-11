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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/** The UI sees source metadata and durable records, never signed media URLs or saved API keys. */
data class SourcePreview(val resolved: ResolvedSource, val config: JobConfig, val previousJob: String?)
data class ScreenState(
    val busy: Boolean = false,
    val starting: Boolean = false,
    val draft: JobConfig? = null,
    val message: String? = null,
    val previews: List<SourcePreview> = emptyList(),
    val document: TranscriptDocument? = null,
    /** The stored export name of the open result, or null while it uses the generated one. */
    val documentName: String? = null,
    val credentials: List<CredentialInfo> = emptyList(),
    val installations: List<EngineInstallation> = emptyList(),
    val update: AvailableEngine? = null,
    val shareUri: String? = null,
    val shareMime: String = "text/markdown",
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
    private val mutable = MutableStateFlow(ScreenState())
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

    init { action { coordinator.recover(); refreshCredentials(); refreshEngines() } }

    fun inspect(text: String, config: JobConfig) {
        val revision = previewRevision.incrementAndGet()
        action(revision) {
        val selected = config.copy(uploadApproved = false, captionTrackId = null, audioTrackId = null)
        val sources = SourceResolver.sharedText(text)
        val previews = sources.map { source ->
            val resolved = coordinator.inspect(source)
            val captions = TrackSelection.captions(resolved, selected)
            val audio = TrackSelection.audio(resolved, null)
            SourcePreview(resolved, selected.copy(captionTrackId = captions.firstOrNull()?.id,
                audioTrackId = audio?.id), records.latestJob(source.id)?.id)
        }
        if (revision == previewRevision.get()) {
            abandonCurrentImports()
            mutable.update { it.copy(draft = selected, previews = previews, message = null) }
        }
        }
    }

    fun selectTrack(sourceId: String, caption: String? = null, audio: String? = null) {
        if (screen.value.starting) return
        previewRevision.incrementAndGet()
        val audioChanged = audio != null && screen.value.previews.any { it.resolved.source.id == sourceId && it.config.audioTrackId != audio }
        mutable.update { state -> state.copy(draft = if (audioChanged) state.draft?.copy(uploadApproved = false) else state.draft, previews = state.previews.map {
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
        mutable.update { it.copy(previews = emptyList(), draft = it.draft?.copy(uploadApproved = false), message = "JOBS_CREATED") }
    }

    fun clearPreview() {
        if (screen.value.starting) return
        previewRevision.incrementAndGet()
        abandonCurrentImports()
        mutable.update { it.copy(previews = emptyList(), draft = it.draft?.copy(uploadApproved = false)) }
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
    fun updatePreviewConfig(config: JobConfig) {
        if (screen.value.starting) return
        previewRevision.incrementAndGet()
        val effective = if (screen.value.previews.any { it.resolved.source.kind == SourceKind.LOCAL_AUDIO }) config.copy(mode = AcquisitionMode.STT_ONLY) else config
        mutable.update { state -> state.copy(draft = effective, previews = state.previews.map { preview ->
            val selected = effective.copy(mode = if (preview.resolved.source.kind == SourceKind.LOCAL_AUDIO) AcquisitionMode.STT_ONLY else config.mode,
                captionTrackId = null, audioTrackId = null)
            val captions = TrackSelection.captions(preview.resolved, selected)
            preview.copy(config = selected.copy(captionTrackId = captions.firstOrNull { it.id == preview.config.captionTrackId }?.id ?: captions.firstOrNull()?.id,
                audioTrackId = preview.resolved.audio.firstOrNull { it.id == preview.config.audioTrackId }?.id ?: TrackSelection.audio(preview.resolved, null)?.id))
        }) }
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
            mutable.update { it.copy(draft = selected, previews = listOf(SourcePreview(
                ResolvedSource(source, emptyList(), emptyList(), emptyMap()), selected, previousJob))) }
        }
    }

    fun dismissMessage() { mutable.update { it.copy(message = null) } }
    fun exportPermissionError() { mutable.update { it.copy(message = "EXPORT_PERMISSION_REQUIRED") } }
    /** Surfaces a local, non-exceptional outcome such as an empty clipboard. */
    fun notice(code: String) { mutable.update { it.copy(message = code) } }
    fun exportArtifact(id: String, format: ExportFormat, treeUri: String) = action {
        val result = coordinator.exportArtifact(id, format, treeUri)
        mutable.update { it.copy(message = "EXPORT_${result.state.name}") }
    }
    fun retryExport(id: String, treeUri: String) = action {
        val result = coordinator.retryExport(id, treeUri)
        mutable.update { it.copy(message = "EXPORT_${result.state.name}") }
    }
    fun reconcileExports() = action { coordinator.reconcileExports() }
    fun shareArtifact(id: String) = action {
        val document = artifactFiles.read(id)
        val chosen = records.artifact(id)?.displayName
        val directory = File(context.cacheDir, "shares").also { check(it.isDirectory || it.mkdirs()) }
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
        val directory = File(context.cacheDir, "shares").also { check(it.isDirectory || it.mkdirs()) }
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
            mutable.update { it.copy(draft = selected, previews = listOf(SourcePreview(resolved, selected, jobId))) }
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
        mutable.update { it.copy(draft = it.draft?.let(::clear), previews = it.previews.map { preview -> preview.copy(config = clear(preview.config)) }) }
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
    fun rollback() = action { engines.rollback(); refreshEngines(); mutable.update { it.copy(message = "ENGINE_ACTIVE") } }

    private suspend fun refreshEngines() { val installed = engines.installations(); mutable.update { it.copy(installations = installed) } }

    private fun refreshCredentials() { mutable.update { it.copy(credentials = credentialStore.list()) } }
    private fun action(revision: Long? = null, starting: Boolean = false, exclusive: Boolean = true, block: suspend () -> Unit) {
        if (exclusive && !actionGate.tryLock()) {
            mutable.update { it.copy(message = "ACTION_BUSY") }
            return
        }
        if (exclusive) mutable.update { it.copy(busy = true, starting = starting, message = null) }
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { if (exclusive) cleanupAbandonedImports(); block() } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                val code = when (failure) {
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

        /** Only the deliberate Start action persists this approval, bound to its exact job configuration. */
        fun configurationForStart(config: JobConfig, credentials: List<CredentialInfo>): JobConfig = config.copy(
            uploadApproved = config.mode != AcquisitionMode.CAPTIONS_ONLY && config.model != null &&
                credentials.any { it.id == config.credentialId && it.provider == config.provider && it.region == config.region },
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
            // The source length is already known here, so a doomed run is refused before it costs a download.
            if (sttPossible && JobLimits.exceeds(preview.resolved.source.durationMs, config.maxAudioSeconds)) {
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
                    config.diarization && !cap.diarization || config.wordTimestamps && !cap.wordTimestamps ||
                        config.segmentTimestamps && !cap.segmentTimestamps || config.contextTerms.isNotEmpty() && !cap.contextTerms -> "UNSUPPORTED_OPTION"
                    config.maxCostMicrousd != null && cap.priceMicrousdPerHour == null -> "PRICE_UNKNOWN"
                    else -> null
                }
            }
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
            var total = 0L
            for (window in SttStep.chunkPlan(durationMs)) {
                val part = SttStep.estimateCostMicrousd(capability, config, window.durationMs) ?: return null
                total = if (total > Long.MAX_VALUE - part) return Long.MAX_VALUE else total + part
            }
            return total
        }
    }
}
