package app.sourcescribe.data

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import app.sourcescribe.core.AcquisitionMode
import app.sourcescribe.core.ArtifactFiles
import app.sourcescribe.core.AudioRetention
import app.sourcescribe.core.Branch
import app.sourcescribe.core.ExecutionState
import app.sourcescribe.core.JobConfig
import app.sourcescribe.core.Phase
import app.sourcescribe.core.Provider
import app.sourcescribe.core.ProviderHttp
import app.sourcescribe.core.Region
import app.sourcescribe.core.Source
import app.sourcescribe.core.SourceKind
import app.sourcescribe.core.providers.GroqAdapter
import app.sourcescribe.core.providers.OpenAiAdapter
import app.sourcescribe.extractor.EngineUpdateManager
import app.sourcescribe.extractor.ExtractorEngine
import app.sourcescribe.extractor.NativeRuntime
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.HostnameVerifier
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Parallel-job checks use only private files, Room, and a local HTTPS fixture. */
@RunWith(AndroidJUnit4::class)
class ParallelJobsTest {
    @Test
    fun twoProvidersRunConcurrentlyWhileCancellationAndSettingsChangesStayIsolated() = withFixture {
        val cancelled = seed(Provider.GROQ, "original-a")
        val completed = seed(Provider.OPENAI, "original-b")
        val blocked = seed(Provider.GROQ, "never-submitted")
        settings.update { it.copy(parallelJobs = 2) }

        coroutineScope {
            val cancelledRun = async(kotlinx.coroutines.Dispatchers.IO) { coordinator.run(cancelled.attempt.id) }
            val completedRun = async(kotlinx.coroutines.Dispatchers.IO) { coordinator.run(completed.attempt.id) }
            try {
                assertTrue("both provider submissions did not arrive", requestsArrived.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                assertEquals(SubmissionState.SENDING, dao.submissions(cancelled.attempt.id).single().state)
                assertEquals(SubmissionState.SENDING, dao.submissions(completed.attempt.id).single().state)
                assertEquals(ExecutionState.RUNNING, requireNotNull(dao.attempt(cancelled.attempt.id)).state)
                assertEquals(ExecutionState.RUNNING, requireNotNull(dao.attempt(completed.attempt.id)).state)

                assertTrue(coordinator.run(blocked.attempt.id))
                assertTrue(dao.submissions(blocked.attempt.id).isEmpty())
                assertEquals(2, requestCount)

                settings.update {
                    it.copy(defaults = JobConfig(
                        mode = AcquisitionMode.STT_ONLY,
                        provider = Provider.GROQ,
                        model = GroqAdapter.MODEL_V3,
                        contextTerms = listOf("mutated-default"),
                    ))
                }

                coordinator.cancel(cancelled.jobId)
                cancelledRun.cancel()
                releaseGroq.countDown()
                cancelledRun.cancelAndJoin()

                assertEquals(ExecutionState.CANCELLED, requireNotNull(dao.job(cancelled.jobId)).state)
                assertEquals(ExecutionState.SUBMISSION_UNCERTAIN, requireNotNull(dao.attempt(cancelled.attempt.id)).state)
                assertEquals(SubmissionState.SENDING, dao.submissions(cancelled.attempt.id).single().state)
                assertFalse(coordinator.run(cancelled.attempt.id))

                releaseOpenAi.countDown()
                assertTrue(completedRun.await())
                assertEquals(Phase.NORMALIZE, requireNotNull(dao.attempt(completed.attempt.id)).phase)
                assertTrue(coordinator.run(completed.attempt.id))
                assertEquals(Phase.PERSIST, requireNotNull(dao.attempt(completed.attempt.id)).phase)
                assertFalse(coordinator.run(completed.attempt.id))

                val finished = requireNotNull(dao.attempt(completed.attempt.id))
                assertEquals(ExecutionState.FINISHED, finished.state)
                val artifact = dao.artifacts(completed.jobId).single()
                assertTrue(artifacts.canonicalFile(artifact.id).isFile)
                assertEquals(completed.config, artifacts.read(artifact.id).acquisition)
                assertEquals(Provider.OPENAI, artifacts.read(artifact.id).provenance.provider)

                assertEquals(1, groqRequests.get())
                assertEquals(1, openAiRequests.get())
                assertTrue(requireNotNull(groqBody).contains(GroqAdapter.MODEL_TURBO))
                assertTrue(requireNotNull(groqBody).contains("original-a"))
                assertTrue(requireNotNull(openAiBody).contains(OpenAiAdapter.MODEL_WHISPER_1))
                assertTrue(requireNotNull(openAiBody).contains("original-b"))
                assertFalse(requireNotNull(groqBody).contains("mutated-default"))
                assertFalse(requireNotNull(openAiBody).contains("mutated-default"))
            } finally {
                releaseGroq.countDown()
                releaseOpenAi.countDown()
                cancelledRun.cancelAndJoin()
                completedRun.cancelAndJoin()
            }
        }
    }

    private fun withFixture(block: suspend Fixture.() -> Unit) {
        val fixture = Fixture(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            runBlocking { fixture.block() }
        } finally {
            fixture.close()
        }
    }

    private class Fixture(base: Context) {
        private val root = File(base.cacheDir, "parallel-jobs-${UUID.randomUUID()}").also { check(it.mkdirs()) }
        private val context = IsolatedContext(base, root)
        private val workManager = ensureWorkManager(base)
        private val database = Room.inMemoryDatabaseBuilder(context, SourceScribeDatabase::class.java).build()
        val dao = database.records()
        val settings = SettingsStore(context)
        val artifacts = ArtifactFiles(File(context.filesDir, "artifacts"))
        val requestsArrived = CountDownLatch(2)
        val releaseGroq = CountDownLatch(1)
        val releaseOpenAi = CountDownLatch(1)
        val groqRequests = AtomicInteger()
        val openAiRequests = AtomicInteger()
        @Volatile var groqBody: String? = null
        @Volatile var openAiBody: String? = null
        private val jobIds = mutableSetOf<String>()
        private val json = Json { encodeDefaults = true }
        private val server = MockWebServer()
        private val certificate = HeldCertificate.Builder()
            .addSubjectAlternativeName("localhost")
            .addSubjectAlternativeName("127.0.0.1")
            .build()
        private val serverCertificates = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        private val clientCertificates = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        private val credentials = CredentialStore(context)
        private val runtime = NativeRuntime(context)
        private val extractor = ExtractorEngine(runtime)
        private val engines = EngineUpdateManager(context, runtime)
        private val providerHttp: ProviderHttp
        private val storage = StorageBudget(context, settings)
        val coordinator: JobCoordinator
        val requestCount get() = server.requestCount

        init {
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val release = when (request.path) {
                        GROQ_PATH -> {
                            groqRequests.incrementAndGet()
                            groqBody = request.body.readUtf8()
                            releaseGroq
                        }
                        OPENAI_PATH -> {
                            openAiRequests.incrementAndGet()
                            openAiBody = request.body.readUtf8()
                            releaseOpenAi
                        }
                        else -> return MockResponse().setResponseCode(404)
                    }
                    requestsArrived.countDown()
                    return if (release.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                        MockResponse().setResponseCode(200).setBody(PROVIDER_RESPONSE)
                    } else {
                        MockResponse().setResponseCode(504)
                    }
                }
            }
            server.useHttps(serverCertificates.sslSocketFactory(), false)
            server.start()
            val fixtureUrl = server.url("/fixture")
            val rewrite = Interceptor { chain ->
                val original = chain.request()
                val rewritten = original.url.newBuilder()
                    .scheme(fixtureUrl.scheme)
                    .host(fixtureUrl.host)
                    .port(fixtureUrl.port)
                    .build()
                chain.proceed(original.newBuilder().url(rewritten).build())
            }
            val client = OkHttpClient.Builder()
                .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
                .hostnameVerifier(HostnameVerifier { host, _ -> host == fixtureUrl.host })
                .addInterceptor(rewrite)
                .build()
            providerHttp = ProviderHttp(client)
            val step = SttStep(context, database, dao, credentials, runtime, extractor, engines, artifacts, providerHttp)
            coordinator = JobCoordinator(
                context,
                database,
                dao,
                settings,
                extractor,
                engines,
                artifacts,
                step,
                ExportStore(context, dao, artifacts),
                JobNotifications(context),
                storage,
                credentials,
                providerHttp,
            )
        }

        suspend fun seed(provider: Provider, contextTerm: String): Seeded {
            val audio = ByteArray(AUDIO_BYTES) { index -> (index + contextTerm.hashCode()).toByte() }
            val hash = sha256(audio)
            val source = Source(
                id = "local:$hash",
                kind = SourceKind.LOCAL_AUDIO,
                contentHash = hash,
                fileName = "${provider.name.lowercase()}.mp3",
                mimeType = AUDIO_MIME_TYPE,
                fileBytes = audio.size.toLong(),
                durationMs = DURATION_MS,
            )
            val credentialId = credentials.save(provider, Region.US, "synthetic-canary-${UUID.randomUUID()}")
            val config = JobConfig(
                mode = AcquisitionMode.STT_ONLY,
                provider = provider,
                model = when (provider) {
                    Provider.GROQ -> GroqAdapter.MODEL_TURBO
                    Provider.OPENAI -> OpenAiAdapter.MODEL_WHISPER_1
                    Provider.ASSEMBLYAI -> error("fixture only supports synchronous providers")
                },
                region = Region.US,
                credentialId = credentialId,
                language = if (provider == Provider.GROQ) "de" else "en",
                contextTerms = listOf(contextTerm),
                uploadApproved = true,
                maxCostMicrousd = 1_000_000,
                audioRetention = AudioRetention.TEMPORARY,
            )
            val now = System.currentTimeMillis()
            val jobId = UUID.randomUUID().toString()
            val attemptId = UUID.randomUUID().toString()
            val artifactId = UUID.randomUUID().toString()
            val checkpoint = FixtureCheckpoint(
                artifactId = artifactId,
                artifactCreatedAt = now,
                source = source,
                durationMs = DURATION_MS,
                chunkCount = 1,
                nextChunkIndex = 1,
                prepared = listOf(FixtureChunk(0, 0, DURATION_MS, audio.size.toLong(), hash, AUDIO_MIME_TYPE)),
            )
            val directory = File(context.noBackupFilesDir, "attempts/$attemptId").also { check(it.mkdirs()) }
            File(directory, "audio-0.mp3").writeBytes(audio)
            val attempt = AttemptRow(
                id = attemptId,
                jobId = jobId,
                branch = Branch.STT,
                number = 1,
                createdAt = now,
                phase = Phase.SUBMIT,
                checkpoint = json.encodeToString(checkpoint),
            )
            dao.createJob(
                SourceRow(source.id, json.encodeToString(source), source.fileName.orEmpty()),
                JobRow(jobId, source.id, json.encodeToString(config), now),
                listOf(attempt),
            )
            jobIds += jobId
            return Seeded(jobId, attempt, config)
        }

        fun close() {
            releaseGroq.countDown()
            releaseOpenAi.countDown()
            jobIds.forEach { workManager.cancelAllWorkByTag("job:$it").result.get(TIMEOUT_SECONDS, TimeUnit.SECONDS) }
            database.close()
            try {
                server.shutdown()
            } catch (_: IOException) {
                // The UUID fixture root remains the only cleanup target.
            }
            root.deleteRecursively()
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

    private data class Seeded(val jobId: String, val attempt: AttemptRow, val config: JobConfig)

    @Serializable
    private data class FixtureCheckpoint(
        val artifactId: String,
        val artifactCreatedAt: Long,
        val source: Source,
        val durationMs: Long,
        val chunkCount: Int,
        val nextChunkIndex: Int,
        val prepared: List<FixtureChunk>,
        val missingChunks: List<Int> = emptyList(),
        val normalized: Boolean = false,
        val engineVersions: Map<String, String> = emptyMap(),
    )

    @Serializable
    private data class FixtureChunk(
        val index: Int,
        val offsetMs: Long,
        val durationMs: Long,
        val bytes: Long,
        val sha256: String,
        val mimeType: String,
    )

    private companion object {
        const val TIMEOUT_SECONDS = 10L
        const val AUDIO_BYTES = 4 * 1024
        const val DURATION_MS = 1_000L
        const val AUDIO_MIME_TYPE = "audio/mpeg"
        const val GROQ_PATH = "/openai/v1/audio/transcriptions"
        const val OPENAI_PATH = "/v1/audio/transcriptions"
        const val PROVIDER_RESPONSE =
            """{"text":"completed B","language":"en","segments":[{"id":0,"start":0.0,"end":1.0,"text":"completed B"}]}"""

        fun ensureWorkManager(context: Context): WorkManager = try {
            WorkManager.getInstance(context)
        } catch (_: IllegalStateException) {
            WorkManagerTestInitHelper.initializeTestWorkManager(context)
            WorkManager.getInstance(context)
        }

        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(Locale.ROOT, it) }
    }
}
