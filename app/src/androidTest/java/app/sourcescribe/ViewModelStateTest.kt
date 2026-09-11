package app.sourcescribe

import android.content.Context
import android.content.ContextWrapper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import app.sourcescribe.core.AcquisitionMode
import app.sourcescribe.core.ArtifactFiles
import app.sourcescribe.core.AudioTrack
import app.sourcescribe.core.CaptionTrack
import app.sourcescribe.core.Generation
import app.sourcescribe.core.JobConfig
import app.sourcescribe.core.Provider
import app.sourcescribe.core.ProviderHttp
import app.sourcescribe.core.Region
import app.sourcescribe.core.ResolvedSource
import app.sourcescribe.core.Source
import app.sourcescribe.core.SourceKind
import app.sourcescribe.core.Translation
import app.sourcescribe.core.providers.AssemblyAiAdapter
import app.sourcescribe.core.providers.GroqAdapter
import app.sourcescribe.data.AudioImport
import app.sourcescribe.data.AudioImportFixtureProvider
import app.sourcescribe.data.CredentialStore
import app.sourcescribe.data.Diagnostics
import app.sourcescribe.data.ExportStore
import app.sourcescribe.data.JobCoordinator
import app.sourcescribe.data.JobNotifications
import app.sourcescribe.data.JobRow
import app.sourcescribe.data.SettingsStore
import app.sourcescribe.data.SourceRow
import app.sourcescribe.data.SourceScribeDatabase
import app.sourcescribe.data.StorageBudget
import app.sourcescribe.data.SttStep
import app.sourcescribe.extractor.EngineUpdateManager
import app.sourcescribe.extractor.ExtractorEngine
import app.sourcescribe.extractor.NativeRuntime
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ViewModelStateTest {
    @Test
    fun clearPreviewRevokesDraftUploadApproval() = withFixture {
        awaitInitialization()
        val config = approvedConfig()
        setScreen(ScreenState(draft = config, previews = listOf(preview(config))))

        onMain { viewModel.clearPreview() }

        assertTrue(viewModel.screen.value.previews.isEmpty())
        assertFalse(requireNotNull(viewModel.screen.value.draft).uploadApproved)
    }

    @Test
    fun selectTrackRevokesApprovalOnlyForChangedAudio() = withFixture {
        awaitInitialization()
        val config = approvedConfig(captionTrackId = CAPTION_A, audioTrackId = AUDIO_A)
        setScreen(ScreenState(draft = config, previews = listOf(preview(config))))

        onMain { viewModel.selectTrack(SOURCE_ID, caption = CAPTION_B) }
        assertEquals(config, viewModel.screen.value.draft)
        assertEquals(config.copy(captionTrackId = CAPTION_B), viewModel.screen.value.previews.single().config)

        onMain { viewModel.selectTrack(SOURCE_ID, audio = AUDIO_A) }
        assertEquals(config, viewModel.screen.value.draft)
        assertEquals(
            config.copy(captionTrackId = CAPTION_B),
            viewModel.screen.value.previews.single().config,
        )

        onMain { viewModel.selectTrack(SOURCE_ID, audio = AUDIO_B) }
        assertEquals(config.copy(uploadApproved = false), viewModel.screen.value.draft)
        assertEquals(
            config.copy(captionTrackId = CAPTION_B, audioTrackId = AUDIO_B, uploadApproved = false),
            viewModel.screen.value.previews.single().config,
        )
    }

    @Test
    fun savingCredentialKeepsOpenCustomPreviewAndDraft() = withFixture {
        awaitInitialization()
        val custom = JobConfig(
            mode = AcquisitionMode.BOTH,
            provider = Provider.ASSEMBLYAI,
            model = AssemblyAiAdapter.MODEL_U2,
            region = Region.EU,
            credentialId = UUID.randomUUID().toString(),
            captionTrackId = CAPTION_B,
            audioTrackId = AUDIO_B,
            uploadApproved = true,
        )
        val openPreview = preview(custom)
        setScreen(ScreenState(draft = custom, previews = listOf(openPreview)))

        onMain { viewModel.saveCredential(Provider.GROQ, Region.US, UUID.randomUUID().toString()) }
        val completed = withTimeout(TIMEOUT_MS) {
            viewModel.screen.first { !it.busy && it.message == "KEY_SAVED" }
        }

        assertEquals(custom, completed.draft)
        assertEquals(openPreview, completed.previews.single())
        val credential = completed.credentials.single()
        assertEquals(Provider.GROQ, credential.provider)
        assertEquals(Region.US, credential.region)
        val defaults = withTimeout(TIMEOUT_MS) { settings.settings.first() }.defaults
        assertEquals(Provider.GROQ, defaults.provider)
        assertEquals(GroqAdapter.MODEL_V3, defaults.model)
        assertEquals(credential.id, defaults.credentialId)
        assertFalse(defaults.uploadApproved)
    }

    @Test
    fun savingCredentialKeepsCustomDraftWithoutPreview() = withFixture {
        awaitInitialization()
        val custom = JobConfig(
            mode = AcquisitionMode.BOTH,
            provider = Provider.ASSEMBLYAI,
            model = AssemblyAiAdapter.MODEL_U2,
            region = Region.EU,
            credentialId = UUID.randomUUID().toString(),
            preferredLanguages = listOf("de-AT", "en"),
            allowAutomaticCaptions = false,
            fallbackOnCaptionError = true,
            language = "de",
            diarization = true,
            contextTerms = listOf("SourceScribe"),
            retainRaw = true,
            uploadApproved = true,
            maxAudioSeconds = 987,
            maxCostMicrousd = 123_456,
        )
        setScreen(ScreenState(draft = custom))

        onMain { viewModel.saveCredential(Provider.GROQ, Region.US, UUID.randomUUID().toString()) }
        val completed = withTimeout(TIMEOUT_MS) {
            viewModel.screen.first { !it.busy && it.message == "KEY_SAVED" }
        }

        assertEquals(custom, completed.draft)
        assertTrue(completed.previews.isEmpty())
        val credential = completed.credentials.single()
        assertEquals(Provider.GROQ, credential.provider)
        assertEquals(Region.US, credential.region)
        val defaults = withTimeout(TIMEOUT_MS) { settings.settings.first() }.defaults
        assertEquals(Provider.GROQ, defaults.provider)
        assertEquals(Region.US, defaults.region)
        assertEquals(GroqAdapter.MODEL_V3, defaults.model)
        assertEquals(credential.id, defaults.credentialId)
        assertFalse(defaults.uploadApproved)
    }

    @Test
    fun importingLocalAudioClearsPriorYoutubeTrackSelections() = withFixture {
        awaitInitialization()
        val requested = approvedConfig(captionTrackId = CAPTION_B, audioTrackId = AUDIO_B)

        onMain { viewModel.importAudio(AudioImportFixtureProvider.WAV_URI, requested) }
        val completed = withTimeout(TIMEOUT_MS) {
            viewModel.screen.first { state ->
                !state.busy && (state.previews.isNotEmpty() || state.message != null)
            }
        }

        assertNull(completed.message)
        val selected = requested.copy(
            mode = AcquisitionMode.STT_ONLY,
            uploadApproved = false,
            captionTrackId = null,
            audioTrackId = null,
        )
        assertEquals(selected, completed.draft)
        val imported = completed.previews.single()
        assertEquals(SourceKind.LOCAL_AUDIO, imported.resolved.source.kind)
        assertEquals(AudioImportFixtureProvider.WAV_SHA256, imported.resolved.source.contentHash)
        assertEquals(selected, imported.config)
    }

    @Test
    fun prepareAgainCarriesTheSettingsButNeverTheUploadApproval() = withFixture {
        awaitInitialization()
        // A real registered key, not a random id: the approval at Start is granted by matching the
        // configuration against the stored keys, so a fixture without one would test the wrong refusal.
        onMain { viewModel.saveCredential(Provider.GROQ, Region.US, UUID.randomUUID().toString()) }
        val stored = withTimeout(TIMEOUT_MS) {
            viewModel.screen.first { !it.busy && it.credentials.size == 1 }
        }.credentials.single()
        val requested = approvedConfig().copy(credentialId = stored.id)
        val jobId = seedRetainedLocalJob(requested)

        onMain { viewModel.prepareAgain(jobId, requested) }
        val completed = withTimeout(TIMEOUT_MS) {
            viewModel.screen.first { state ->
                !state.busy && state.previews.singleOrNull()?.previousJob == jobId
            }
        }

        val selected = completed.previews.single().config
        assertEquals(AcquisitionMode.STT_ONLY, selected.mode)
        // Re-preparing exists so a job that ran into its own limit can be started again with one value
        // changed, which is why it carries the stored settings over.
        assertEquals(requested.provider, selected.provider)
        assertEquals(requested.model, selected.model)
        assertEquals(requested.credentialId, selected.credentialId)
        // A track id from the previous run is not reused; the tracks are resolved again for this preview.
        assertNull(selected.captionTrackId)
        assertNull(selected.audioTrackId)
        // The approval itself is not carried, so the draft as it comes back is refused.
        assertFalse(selected.uploadApproved)
        assertEquals("UPLOAD_APPROVAL_REQUIRED", MainViewModel.configError(selected))
        assertEquals(selected, completed.draft)

        // What Start then does with it decides whether anything can cost money, so it is pinned here
        // rather than left to be inferred. The approval is recomputed from mode, model and a matching
        // stored key, and the previous value is never read: a re-prepared job is therefore startable by
        // the ordinary Start action, which is exactly the point of re-preparing. That action is a
        // deliberate tap; re-preparing on its own reaches no provider.
        val atStart = MainViewModel.configurationForStart(selected, completed.credentials)
        assertTrue(atStart.uploadApproved)
        assertNull(MainViewModel.previewError(completed.previews.single().copy(config = atStart), completed.credentials))
        // And the key is what carries it: without a stored key that matches, Start grants nothing and the
        // configuration stays refused however often it is re-prepared.
        val withoutKey = MainViewModel.configurationForStart(selected, emptyList())
        assertFalse(withoutKey.uploadApproved)
        assertEquals("UPLOAD_APPROVAL_REQUIRED", MainViewModel.configError(withoutKey))

        val preservedPreviews = completed.previews
        onMain { viewModel.prepareAgain(UUID.randomUUID().toString(), requested) }
        val failed = withTimeout(TIMEOUT_MS) {
            viewModel.screen.first { !it.busy && it.message == "LOCAL_PROCESSING_FAILED" }
        }
        assertEquals(selected, failed.draft)
        assertEquals(preservedPreviews, failed.previews)
    }

    private fun <T> withFixture(block: suspend Fixture.() -> T): T {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking { NativeRuntime(base).initialize() }
        val fixture = Fixture(base)
        return try {
            runBlocking { fixture.block() }
        } finally {
            fixture.close()
        }
    }

    private class Fixture(base: Context) {
        private val instrumentation = InstrumentationRegistry.getInstrumentation()
        private val root = File(base.cacheDir, "view-model-state-${UUID.randomUUID()}").also { check(it.mkdirs()) }
        private val context = IsolatedContext(base, root)
        private val runtimeLink = File(context.noBackupFilesDir, "youtubedl-android")
        init {
            val actualRuntime = File(base.noBackupFilesDir, runtimeLink.name)
            check(actualRuntime.isDirectory)
            Files.createSymbolicLink(runtimeLink.toPath(), actualRuntime.toPath())
            ensureWorkManager(base)
        }
        private val database = Room.inMemoryDatabaseBuilder(context, SourceScribeDatabase::class.java).build()
        private val dao = database.records()
        val settings = SettingsStore(context)
        private val runtime = NativeRuntime(context)
        private val extractor = ExtractorEngine(runtime)
        private val engines = EngineUpdateManager(context, runtime)
        private val artifacts = ArtifactFiles(File(context.filesDir, "artifacts"))
        private val exports = ExportStore(context, dao, artifacts)
        private val notifications = JobNotifications(context)
        private val storage = StorageBudget(context, settings)
        private val credentials = CredentialStore(context)
        private val audioImport = AudioImport(context, runtime, settings, dao, storage)
        private val providerGuard = ProviderRequestGuard()
        private val providerHttp = ProviderHttp(
            OkHttpClient.Builder().addInterceptor(providerGuard.interceptor).build(),
        )
        private val stt = SttStep(
            context,
            database,
            dao,
            credentials,
            runtime,
            extractor,
            engines,
            artifacts,
            providerHttp,
        )
        private val coordinator = JobCoordinator(
            context,
            database,
            dao,
            settings,
            extractor,
            engines,
            artifacts,
            stt,
            exports,
            notifications,
            storage,
            credentials,
            providerHttp,
        )
        private val viewModelStore = ViewModelStore()
        val viewModel: MainViewModel

        init {
            lateinit var created: MainViewModel
            instrumentation.runOnMainSync {
                created = ViewModelProvider.create(
                    viewModelStore,
                    MainViewModelFactory {
                        MainViewModel(
                            context,
                            coordinator,
                            dao,
                            settings,
                            credentials,
                            artifacts,
                            engines,
                            audioImport,
                            Diagnostics(dao, engines),
                        )
                    },
                )[MainViewModel::class]
            }
            viewModel = created
        }

        suspend fun awaitInitialization() {
            val initialized = withTimeout(TIMEOUT_MS) { viewModel.screen.first { !it.busy } }
            check(initialized.message == null) { "ViewModel initialization failed: ${initialized.message}" }
        }

        fun onMain(action: () -> Unit) = instrumentation.runOnMainSync(action)

        fun setScreen(state: ScreenState) = onMain {
            val field = MainViewModel::class.java.getDeclaredField("mutable").apply { isAccessible = true }
            val value = field.get(viewModel)
            check(value is MutableStateFlow<*>)
            @Suppress("UNCHECKED_CAST") // Test-only state setup; production exposes no preview seeding hook.
            (value as MutableStateFlow<ScreenState>).value = state
        }

        suspend fun seedRetainedLocalJob(config: JobConfig): String {
            val bytes = "retained-${UUID.randomUUID()}".toByteArray()
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            val imports = File(context.noBackupFilesDir, "imports").also { check(it.mkdirs()) }
            val audio = File(imports, "$hash.audio").also { it.writeBytes(bytes) }
            val source = Source(
                id = "local:$hash",
                kind = SourceKind.LOCAL_AUDIO,
                contentHash = hash,
                title = "Retained fixture",
                fileName = "fixture.wav",
                mimeType = "audio/wav",
                fileBytes = bytes.size.toLong(),
                durationMs = 1_000,
            )
            val jobId = UUID.randomUUID().toString()
            dao.createJob(
                SourceRow(source.id, JSON.encodeToString(source), requireNotNull(source.title), audio.absolutePath),
                JobRow(jobId, source.id, JSON.encodeToString(config), System.currentTimeMillis()),
                emptyList(),
            )
            return jobId
        }

        fun close() {
            instrumentation.runOnMainSync { viewModelStore.clear() }
            runBlocking { withTimeout(TIMEOUT_MS) { viewModel.screen.first { !it.busy } } }
            val providerRequests = providerGuard.requestCount
            database.close()
            if (Files.isSymbolicLink(runtimeLink.toPath())) Files.delete(runtimeLink.toPath())
            root.deleteRecursively()
            check(providerRequests == 0) { "Provider request escaped the test guard" }
        }
    }

    private class MainViewModelFactory(
        private val create: () -> MainViewModel,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T =
            requireNotNull(modelClass.java.cast(create()))
    }

    private class IsolatedContext(base: Context, root: File) : ContextWrapper(base) {
        private val files = File(root, "files").also { check(it.mkdirs()) }
        private val noBackup = File(root, "no-backup").also { check(it.mkdirs()) }
        private val cache = File(root, "cache").also { check(it.mkdirs()) }

        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = files
        override fun getNoBackupFilesDir(): File = noBackup
        override fun getCacheDir(): File = cache
    }

    private class ProviderRequestGuard {
        private val requests = AtomicInteger()
        val interceptor = Interceptor {
            requests.incrementAndGet()
            throw AssertionError("Provider HTTP is forbidden in ViewModelStateTest")
        }
        val requestCount: Int get() = requests.get()
    }

    companion object {
        private const val TIMEOUT_MS = 60_000L
        private const val SOURCE_ID = "fixture-youtube-source"
        private const val VIDEO_ID = "jNQXAC9IVRw"
        private const val CAPTION_A = "caption-a"
        private const val CAPTION_B = "caption-b"
        private const val AUDIO_A = "audio-a"
        private const val AUDIO_B = "audio-b"
        private val JSON = Json { encodeDefaults = true }

        private fun approvedConfig(
            captionTrackId: String? = CAPTION_A,
            audioTrackId: String? = AUDIO_A,
        ) = JobConfig(
            mode = AcquisitionMode.STT_ONLY,
            provider = Provider.GROQ,
            model = GroqAdapter.MODEL_TURBO,
            region = Region.US,
            credentialId = UUID.randomUUID().toString(),
            captionTrackId = captionTrackId,
            audioTrackId = audioTrackId,
            uploadApproved = true,
        )

        private fun preview(config: JobConfig): SourcePreview {
            val source = Source(
                id = SOURCE_ID,
                kind = SourceKind.YOUTUBE,
                canonicalUrl = "https://www.youtube.com/watch?v=$VIDEO_ID",
                videoId = VIDEO_ID,
                title = "Fixture source",
            )
            return SourcePreview(
                ResolvedSource(
                    source,
                    listOf(
                        CaptionTrack(CAPTION_A, VIDEO_ID, "en", "English", "vtt", Generation.UPLOADER_PROVIDED, Translation.NONE, "fixture"),
                        CaptionTrack(CAPTION_B, VIDEO_ID, "de", "Deutsch", "vtt", Generation.UPLOADER_PROVIDED, Translation.NONE, "fixture"),
                    ),
                    listOf(
                        AudioTrack(AUDIO_A, VIDEO_ID, "en", "English", true, "fixture"),
                        AudioTrack(AUDIO_B, VIDEO_ID, "de", "Deutsch", false, "fixture"),
                    ),
                    emptyMap(),
                ),
                config,
                null,
            )
        }

        private fun ensureWorkManager(context: Context): WorkManager = try {
            WorkManager.getInstance(context)
        } catch (_: IllegalStateException) {
            WorkManagerTestInitHelper.initializeTestWorkManager(context)
            WorkManager.getInstance(context)
        }
    }
}
