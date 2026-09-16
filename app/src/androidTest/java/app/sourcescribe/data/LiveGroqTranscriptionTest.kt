package app.sourcescribe.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.util.Log
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Data
import androidx.work.WorkManager
import androidx.work.impl.WorkManagerImpl
import androidx.work.impl.model.WorkSpec
import app.sourcescribe.MainViewModel
import app.sourcescribe.SourcePreview
import app.sourcescribe.core.AcquisitionMode
import app.sourcescribe.core.ArtifactFiles
import app.sourcescribe.core.AudioRetention
import app.sourcescribe.core.Branch
import app.sourcescribe.core.ExecutionState
import app.sourcescribe.core.JobConfig
import app.sourcescribe.core.JobLimits
import app.sourcescribe.core.Origin
import app.sourcescribe.core.Outcome
import app.sourcescribe.core.Phase
import app.sourcescribe.core.Provider
import app.sourcescribe.core.ProviderHttp
import app.sourcescribe.core.Region
import app.sourcescribe.core.SourceKind
import app.sourcescribe.core.SourceResolver
import app.sourcescribe.core.TrackSelection
import app.sourcescribe.core.confirmedComplete
import app.sourcescribe.core.providers.GroqAdapter
import app.sourcescribe.extractor.EngineUpdateManager
import app.sourcescribe.extractor.ExtractorEngine
import app.sourcescribe.extractor.NativeRuntime
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The one test that spends the owner's money: a real speech-to-text request against Groq.
 *
 * Everything else in this repository proves the pipeline against fixtures, and the phone test of 0.2.0
 * showed what that leaves open — the preparation stopped every job before a provider was ever asked, and no
 * request had ever left the device. This test runs the path the app itself runs (resolve the source with the
 * real engine, download the recommended rendition, prepare the audio, upload it, parse the answer, store the
 * transcript, finish the history row) through [JobCoordinator] and [SttStep], not by calling the adapter.
 *
 * It is opt-in because it costs money and needs a key: without `sourcescribeLiveProvider=groq`,
 * `liveProviderKey=<key>` and `publicSourceUrl=<url>` it ends as an assumption failure with that sentence.
 * The clip must be at most [MAX_CLIP_MS]; a longer source fails the test before anything is uploaded, so a
 * mistyped URL cannot spend the free tier.
 *
 * The key arrives as an instrumentation argument and is never printed, never sent to `sendStatus`, and never
 * written to a file by this test: it goes straight into [CredentialStore], which is where the app keeps keys,
 * and the assertions below check the four places the project's invariant names — the log this process can
 * read, the WorkManager input, the diagnostics text and what is persisted in Room and in the artifact.
 */
@RunWith(AndroidJUnit4::class)
class LiveGroqTranscriptionTest {
    @Test
    fun aShortPublicVideoIsTranscribedByGroqAndStoredWithItsProvenance() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(
            "Live Groq evidence is opt-in and chargeable: pass sourcescribeLiveProvider=groq," +
                " liveProviderKey=<key> and publicSourceUrl=<url of a clip of at most 30 seconds>",
            arguments.getString("sourcescribeLiveProvider") == "groq",
        )
        val apiKey = arguments.getString("liveProviderKey").orEmpty().trim()
        assumeTrue(
            "liveProviderKey instrumentation argument is required for a live Groq request",
            apiKey.isNotBlank(),
        )
        val sourceUrl = arguments.getString("publicSourceUrl").orEmpty().trim()
        assumeTrue(
            "publicSourceUrl instrumentation argument is required; the clip must be at most 30 seconds",
            sourceUrl.isNotBlank(),
        )
        val requested = SourceResolver.youtube(sourceUrl)

        // Proves afterwards that the log really was read back: a scan that finds nothing at all would
        // otherwise pass without having looked anywhere.
        val canary = "sourcescribe-live-groq-canary-${UUID.randomUUID()}"
        val logWindowStart = System.currentTimeMillis() - LOG_WINDOW_MARGIN_MS
        Log.i(LOG_TAG, canary)

        withFixture {
            val resolved = coordinator.inspect(requested)
            assertEquals(requested.id, resolved.source.id)
            assertEquals(SourceKind.YOUTUBE, resolved.source.kind)
            val durationMs = requireNotNull(resolved.source.durationMs) { "SOURCE_DURATION_UNKNOWN" }
            assertTrue(
                "the live clip must be at most ${MAX_CLIP_MS / 1000} seconds, this one is $durationMs ms",
                durationMs in 1..MAX_CLIP_MS,
            )
            val track = requireNotNull(TrackSelection.audio(resolved, null)) { "NO_AUDIO_ON_CONFIGURED_SOURCE" }

            val credentialId = credentials.save(Provider.GROQ, Region.US, apiKey)
            val stored = credentials.list()
            // The configuration the new-source screen would hand to the coordinator for this source, built
            // through the screen's own function so this test cannot start a job the screen could not.
            val config = MainViewModel.configurationForStart(
                JobConfig(
                    mode = AcquisitionMode.STT_ONLY,
                    provider = Provider.GROQ,
                    model = GroqAdapter.DEFAULT_MODEL,
                    region = Region.US,
                    credentialId = credentialId,
                    audioTrackId = track.id,
                    audioRetention = AudioRetention.TEMPORARY,
                ),
                stored,
            )
            assertTrue(config.uploadApproved)
            assertEquals(JobLimits.MAX_AUDIO_SECONDS, config.maxAudioSeconds)
            assertNull(MainViewModel.previewError(SourcePreview(resolved, config, null), stored))

            val jobId = coordinator.create(resolved.source, config)
            trackJob(jobId)
            val attemptId = dao.attempts(jobId).single().let { created ->
                assertEquals(Branch.STT, created.branch)
                assertEquals(Phase.RESOLVE, created.phase)
                created.id
            }

            // What the app's own worker does: run one durable phase, and run again while the attempt is
            // still runnable. `nextAt` is honoured instead of spun through, so a provider that asks for a
            // pause gets it here as well.
            val executed = ArrayList<Phase>()
            val deadline = System.currentTimeMillis() + RUN_DEADLINE_MS
            var current = requireNotNull(dao.attempt(attemptId))
            while (true) {
                assertTrue("the attempt did not finish within ${executed.size} phases: $executed", executed.size < MAX_PHASE_RUNS)
                assertTrue("the attempt did not finish within ${RUN_DEADLINE_MS / 1000} s: $executed", System.currentTimeMillis() < deadline)
                val wait = (current.nextAt - System.currentTimeMillis()).coerceIn(0, MAX_WAIT_MS)
                if (wait > 0) delay(wait)
                executed += current.phase
                val runnable = coordinator.run(attemptId)
                current = requireNotNull(dao.attempt(attemptId))
                if (!runnable) break
            }

            // Before the outcome, because a leaked key matters whether or not the transcript arrived.
            assertKeyStaysOutOfEveryPlaceTheAppWrites(apiKey, canary, logWindowStart, jobId, attemptId)

            // Everything the run learnt goes out before the assertions that can stop it, so a failed
            // request is still an answer: the reason and the numbers reach the runner either way.
            val document = dao.artifacts(jobId).singleOrNull()?.let { artifacts.read(it.id) }
            InstrumentationRegistry.getInstrumentation().sendStatus(127, Bundle().apply {
                putString("live.provider", Provider.GROQ.name)
                putString("live.requestedModel", GroqAdapter.DEFAULT_MODEL)
                putString("live.reportedModel", document?.provenance?.reportedModel ?: "NONE")
                putString("live.sourceId", resolved.source.id)
                putString("live.audioTrackId", track.id)
                putLong("live.sourceDurationMs", durationMs)
                putInt("live.providerRequests", providerRequests().size)
                putString("live.language", document?.language ?: "NONE")
                putString("live.reportedLanguages", document?.provenance?.reportedLanguages?.joinToString(",").orEmpty().ifBlank { "NONE" })
                putInt("live.segments", document?.segments?.size ?: 0)
                // Counts only: a full transcript belongs in the artifact, never in test output or a log.
                putInt("live.transcriptChars", document?.text?.length ?: 0)
                putString("live.state", current.state.name)
                putString("live.outcome", current.outcome.name)
                putString("live.error", current.error ?: "NONE")
                putString("live.phases", executed.joinToString(">"))
                putString("live.warnings", document?.warnings?.joinToString(",").orEmpty().ifBlank { "NONE" })
            })

            assertEquals("the attempt stopped with ${current.error}", null, current.error)
            assertEquals(ExecutionState.FINISHED, current.state)
            assertEquals(ExecutionState.FINISHED, requireNotNull(dao.job(jobId)).state)
            assertEquals(
                listOf(Phase.RESOLVE, Phase.DOWNLOAD_AUDIO, Phase.PREPARE_AUDIO, Phase.SUBMIT, Phase.NORMALIZE, Phase.PERSIST),
                executed.distinct(),
            )

            // One short clip is one chunk, so one POST to Groq and nothing else. A second request would
            // mean a retry nobody asked for, and the owner's free tier would pay for it.
            assertEquals(listOf("api.groq.com/openai/v1/audio/transcriptions"), providerRequests())
            val submission = dao.submissions(attemptId).single()
            assertEquals(SubmissionState.RESPONSE_SAVED, submission.state)
            assertEquals(0, submission.chunkIndex)

            val artifact = dao.artifacts(jobId).single()
            val transcript = requireNotNull(document) { "the job finished without a stored transcript" }
            assertEquals(Branch.STT, artifact.branch)
            assertTrue("the stored transcript has no text", transcript.text.isNotBlank())
            assertTrue(transcript.segments.isNotEmpty())
            assertEquals(Origin.PROVIDER, transcript.provenance.origin)
            assertEquals(Provider.GROQ, transcript.provenance.provider)
            assertEquals(GroqAdapter.DEFAULT_MODEL, transcript.provenance.requestedModel)
            assertTrue(
                "the provider named no model: ${transcript.provenance.reportedModel}",
                !transcript.provenance.reportedModel.isNullOrBlank(),
            )
            assertEquals(transcript.provenance.reportedModel ?: transcript.provenance.requestedModel, artifact.providerModel)
            assertEquals(track.id, transcript.provenance.sourceAudioTrack?.id)

            // Language: the app promises to say where a language claim comes from and to keep only what the
            // response itself named as an ISO-639-1 code — `SyncTranscriptParser` accepts nothing else. What
            // Groq's `verbose_json` puts in its `language` field (the code `en`, or the word `english` the
            // OpenAI-shaped API is known for) is not established in this project, so this asserts the app's
            // own rule and the evidence bundle above reports what actually arrived. A run that reports
            // `language=NONE` means Groq named no code and no Groq transcript will ever carry a language.
            assertEquals("provider_response", transcript.provenance.languageEvidence)
            assertEquals(transcript.provenance.reportedLanguages.singleOrNull(), transcript.language)
            assertTrue(
                "a language that is not an ISO-639-1 code was stored: ${transcript.provenance.reportedLanguages}",
                transcript.provenance.reportedLanguages.all { it.length == 2 },
            )
            assertEquals(transcript.language, artifact.language)

            // A complete result, and said to be complete: `technicallyComplete` is what the history, the
            // exports and the result screen read, and the outcome follows from it.
            assertEquals(
                "the transcript was stored as incomplete, warnings: ${transcript.warnings}",
                true,
                transcript.scope.technicallyComplete,
            )
            assertEquals(emptyList<Int>(), transcript.scope.missingChunks)
            assertTrue(transcript.scope.confirmedComplete)
            assertEquals(true, artifact.complete)
            assertTrue(
                "the attempt finished as ${current.outcome}, warnings: ${transcript.warnings}",
                current.outcome in setOf(Outcome.SUCCESS, Outcome.SUCCESS_WITH_WARNINGS),
            )

            // The audio and the chunk the run downloaded and encoded are gone: `AudioRetention.TEMPORARY`
            // has the coordinator retire the attempt directory once the transcript is persisted.
            assertFalse("attempt files survived the finished job", attemptDirectory(attemptId).exists())
        }
    }

    /**
     * Every place the project's invariant forbids a key to reach, checked against the key this run
     * actually used.
     *
     * The log is the one that needs an argument for why the check means anything: this process runs under
     * the app's UID, and `logcat` started from here shows that UID's own entries, which is exactly where a
     * key the app logged would appear. [canary] was written to the same log before the job started, so a
     * scan that returns nothing — a rolled-over buffer, a reader that is refused — fails instead of passing
     * for having looked at an empty string. What this cannot see, and does not claim to, is the system's own
     * buffers: `adbd` writes the whole `am instrument` command line, the argument included, into the system
     * log, which no app may read. The runner script clears the device log afterwards for that reason.
     */
    private suspend fun LiveFixture.assertKeyStaysOutOfEveryPlaceTheAppWrites(
        apiKey: String,
        canary: String,
        logWindowStart: Long,
        jobId: String,
        attemptId: String,
    ) {
        val log = readOwnLogSince(logWindowStart)
        assertTrue(
            "the app's own log could not be read back, so nothing about the key can be concluded from it",
            log.contains(canary),
        )
        assertFalse("the API key reached the app's own log", log.contains(apiKey))

        val inputs = persistedWorkInputs(jobId)
        assertTrue("the job scheduled no work at all", inputs.isNotEmpty())
        inputs.forEach { input ->
            assertEquals(setOf("attemptId"), input.keyValueMap.keys)
            assertFalse("the API key reached WorkManager input data", input.keyValueMap.toString().contains(apiKey))
        }

        assertFalse(
            "the API key reached the diagnostics export",
            Diagnostics(dao, engines).build().contains(apiKey),
        )

        assertFalse("the API key reached the stored job configuration", requireNotNull(dao.job(jobId)).config.contains(apiKey))
        assertFalse("the API key reached the attempt checkpoint", requireNotNull(dao.attempt(attemptId)).checkpoint.contains(apiKey))
        dao.submissions(attemptId).forEach { submission ->
            assertFalse("the API key reached a submission row", submission.toString().contains(apiKey))
        }
        dao.artifacts(jobId).forEach { artifact ->
            assertFalse(
                "the API key reached a stored artifact",
                artifacts.canonicalFile(artifact.id).readText(StandardCharsets.UTF_8).contains(apiKey),
            )
        }
    }

    private fun withFixture(block: suspend LiveFixture.() -> Unit) {
        val fixture = LiveFixture(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            runBlocking {
                fixture.prepareNativeRuntime()
                fixture.block()
            }
        } finally {
            fixture.close()
        }
    }

    /**
     * Isolated database, artifacts, engines and attempt directories, so a live run leaves the installed
     * app's own data untouched. The native runtime is the exception every device test here makes: it is
     * linked to the copy the app already unpacked, because a second one costs hundreds of megabytes.
     */
    private class LiveFixture(private val base: Context) {
        private val root = File(base.cacheDir, "live-groq-${UUID.randomUUID()}").also { check(it.mkdirs()) }
        private val context = IsolatedContext(base, root)
        private val runtimeLink = File(context.noBackupFilesDir, "youtubedl-android")
        private val workManager = WorkManager.getInstance(base)
        private val database = Room.inMemoryDatabaseBuilder(context, SourceScribeDatabase::class.java).build()
        private val jobIds = mutableSetOf<String>()
        private val settings = SettingsStore(context)
        private val runtime = NativeRuntime(context)
        private val extractor = ExtractorEngine(runtime)
        private val requests = Collections.synchronizedList(ArrayList<String>())
        private val providerHttp = ProviderHttp(
            OkHttpClient.Builder().addInterceptor(
                Interceptor { chain ->
                    // The host and path only. A provider request carries the key in its Authorization
                    // header, and nothing in this test may hold a copy of it.
                    requests += "${chain.request().url.host}${chain.request().url.encodedPath}"
                    chain.proceed(chain.request())
                },
            ).build(),
        )

        val dao = database.records()
        val credentials = CredentialStore(context)
        val engines = EngineUpdateManager(context, runtime)
        val artifacts = ArtifactFiles(File(context.filesDir, "artifacts"))

        private val stt = SttStep(context, database, dao, credentials, runtime, extractor, engines, artifacts, providerHttp)
        val coordinator = JobCoordinator(
            context,
            database,
            dao,
            settings,
            extractor,
            engines,
            artifacts,
            stt,
            ExportStore(context, dao, artifacts),
            JobNotifications(context),
            StorageBudget(context, settings),
            credentials,
            providerHttp,
        )

        fun trackJob(jobId: String) {
            jobIds += jobId
        }

        fun providerRequests(): List<String> = requests.toList()

        fun attemptDirectory(attemptId: String): File = File(context.noBackupFilesDir, "attempts/$attemptId")

        suspend fun prepareNativeRuntime() {
            NativeRuntime(base).initialize()
            val actualRuntime = File(base.noBackupFilesDir, runtimeLink.name)
            check(actualRuntime.isDirectory)
            Files.createSymbolicLink(runtimeLink.toPath(), actualRuntime.toPath())
        }

        /** Every work item this job persisted, read from the WorkSpec rather than from the request builder. */
        @SuppressLint("RestrictedApi")
        fun persistedWorkInputs(jobId: String): List<Data> {
            val implementation = workManager as WorkManagerImpl
            return workManager.getWorkInfosByTag("job:$jobId").get(30, TimeUnit.SECONDS).mapNotNull { info ->
                implementation.workDatabase.workSpecDao().getWorkSpec(info.id.toString())?.let(WorkSpec::input)
            }
        }

        /**
         * The app's own logcat entries since [since]. `logcat` is started as a child of this process, so the
         * log daemon returns this UID's entries and no other app's.
         */
        fun readOwnLogSince(since: Long): String {
            val format = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
            val process = ProcessBuilder("logcat", "-d", "-t", format.format(Date(since)))
                .redirectErrorStream(true)
                .start()
            return try {
                process.inputStream.use { input ->
                    val output = StringBuilder()
                    val buffer = ByteArray(64 * 1024)
                    while (output.length < MAX_LOG_CHARS) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.append(String(buffer, 0, count, StandardCharsets.UTF_8))
                    }
                    output.toString()
                }
            } finally {
                process.waitFor(30, TimeUnit.SECONDS)
                process.destroy()
            }
        }

        fun close() {
            try {
                jobIds.forEach { workManager.cancelAllWorkByTag("job:$it") }
                database.close()
            } finally {
                try {
                    Files.deleteIfExists(runtimeLink.toPath())
                } finally {
                    // The downloaded audio, the encoded chunk, the saved response and the artifact all live
                    // under this one directory, so this is what deletes them.
                    check(root.deleteRecursively()) { "live run files could not be deleted: $root" }
                }
            }
        }
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

    private companion object {
        const val LOG_TAG = "SourceScribeLiveGroq"
        const val MAX_CLIP_MS = 30_000L
        const val LOG_WINDOW_MARGIN_MS = 5_000L
        const val MAX_LOG_CHARS = 4 * 1024 * 1024
        const val MAX_PHASE_RUNS = 16
        const val MAX_WAIT_MS = 120_000L
        const val RUN_DEADLINE_MS = 15 * 60_000L
    }
}
