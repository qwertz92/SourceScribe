package app.sourcescribe.data

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.net.toUri
import android.os.Process
import android.util.Log
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
import app.sourcescribe.core.Outcome
import app.sourcescribe.core.Phase
import app.sourcescribe.core.Provider
import app.sourcescribe.core.ProviderHttp
import app.sourcescribe.core.Region
import app.sourcescribe.core.Source
import app.sourcescribe.core.SourceKind
import app.sourcescribe.core.providers.AssemblyAiAdapter
import app.sourcescribe.extractor.EngineUpdateManager
import app.sourcescribe.extractor.ExtractorEngine
import app.sourcescribe.extractor.NativeRuntime
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in, two-invocation proof that AAI recovery survives an actual process boundary. */
@RunWith(AndroidJUnit4::class)
class ProcessRecoveryTest {
    @Test
    fun stage1PersistsAcceptedAssemblyRemote() {
        val base = requireStage(STAGE_ONE)
        val root = File(base.noBackupFilesDir, ROOT_NAME)
        assertFalse("pending process fixture already exists: run stage 2 or inspect it", root.exists())
        assertTrue("could not create isolated process fixture root", root.mkdir())
        val context = IsolatedContext(base, root)
        val database = Room.databaseBuilder(context, SourceScribeDatabase::class.java, DATABASE_NAME).build()
        var evidence: StageEvidence? = null
        try {
            FixtureNetwork(
                MockResponse().setResponseCode(200).setBody(AAI_UPLOAD_RESPONSE),
                MockResponse().setResponseCode(200).setBody(AAI_QUEUED_RESPONSE),
            ).use { network ->
                runBlocking {
                    val fixture = Fixture(context, database, network.providerHttp)
                    val seeded = fixture.seed()

                    val waiting = fixture.step.run(seeded.attempt, seeded.owner, seeded.config)

                    assertEquals(Phase.RETRIEVE, waiting.phase)
                    assertEquals(ExecutionState.WAITING_REMOTE, waiting.state)
                    val submission = fixture.dao.submissions(waiting.id).single()
                    assertEquals(SubmissionState.ACCEPTED, submission.state)
                    assertEquals(REMOTE_ID, submission.remoteId)
                    assertEquals(1, fixture.dao.submissions(waiting.id).size)
                    assertTrue(requireNotNull(submission.rawResponsePath).let(::File).isFile)
                    assertEquals(2, network.requestCount)
                    val requests = network.takeRequests(2)
                    assertEquals(listOf("POST /v2/upload", "POST /v2/transcript"), requests.map { "${it.method} ${it.path}" })
                    assertTrue(requests.all { it.getHeader("Authorization") == SYNTHETIC_CANARY_KEY })
                    assertEquals(1, requests.count(::isPaidTranscriptPost))
                    evidence = StageEvidence(
                        stageOnePid = Process.myPid(),
                        attemptId = waiting.id,
                        jobId = waiting.jobId,
                        artifactId = seeded.artifactId,
                        remoteId = REMOTE_ID,
                        stageOneRequestCount = requests.size,
                        stageOnePaidPostCount = requests.count(::isPaidTranscriptPost),
                        stageOneSubmissionCount = fixture.dao.submissions(waiting.id).size,
                    )
                }
            }
        } finally {
            database.close()
        }
        assertTrue("Room database was not persisted on disk", context.getDatabasePath(DATABASE_NAME).isFile)
        writeDurably(File(root, EVIDENCE_NAME), json.encodeToString(requireNotNull(evidence)).toByteArray(StandardCharsets.UTF_8))
        Log.i(TAG, "stage=1 result=ACCEPTED tls_requests=2 paid_posts=1 submissions=1 db=closed evidence=durable")
    }

    @Test
    fun stage2ReopensAndFinalizesWithoutResubmission() {
        val base = requireStage(STAGE_TWO)
        val root = File(base.noBackupFilesDir, ROOT_NAME)
        val evidenceFile = File(root, EVIDENCE_NAME)
        assertTrue("stage 1 evidence is missing", evidenceFile.isFile)
        val evidenceBytes = readBounded(evidenceFile)
        val evidence = json.decodeFromString<StageEvidence>(evidenceBytes.toString(StandardCharsets.UTF_8))
        assertNotEquals("stage 2 must run in a new instrumentation process", evidence.stageOnePid, Process.myPid())
        assertEquals(2, evidence.stageOneRequestCount)
        assertEquals(1, evidence.stageOnePaidPostCount)
        assertEquals(1, evidence.stageOneSubmissionCount)
        assertEquals(REMOTE_ID, evidence.remoteId)

        val context = IsolatedContext(base, root)
        assertTrue("Room database from stage 1 is missing", context.getDatabasePath(DATABASE_NAME).isFile)
        val database = Room.databaseBuilder(context, SourceScribeDatabase::class.java, DATABASE_NAME).build()
        var fixture: Fixture? = null
        try {
            FixtureNetwork(MockResponse().setResponseCode(200).setBody(AAI_COMPLETED_RESPONSE)).use { network ->
                runBlocking {
                    fixture = Fixture(context, database, network.providerHttp)
                    val activeFixture = requireNotNull(fixture)
                    val persisted = requireNotNull(activeFixture.dao.attempt(evidence.attemptId))
                    val job = requireNotNull(activeFixture.dao.job(evidence.jobId))
                    val config = json.decodeFromString<JobConfig>(job.config)
                    val before = activeFixture.dao.submissions(persisted.id).single()
                    assertEquals(Phase.RETRIEVE, persisted.phase)
                    assertEquals(ExecutionState.WAITING_REMOTE, persisted.state)
                    assertEquals(SubmissionState.ACCEPTED, before.state)
                    assertEquals(evidence.remoteId, before.remoteId)

                    activeFixture.coordinator.recover()
                    val recovered = requireNotNull(activeFixture.dao.attempt(persisted.id))
                    val recoveredSubmission = activeFixture.dao.submissions(recovered.id).single()
                    assertEquals(Phase.RETRIEVE, recovered.phase)
                    assertEquals(ExecutionState.WAITING_REMOTE, recovered.state)
                    assertEquals(null, recovered.leaseOwner)
                    assertEquals(0L, recovered.leaseUntil)
                    assertEquals(SubmissionState.ACCEPTED, recoveredSubmission.state)
                    assertEquals(evidence.remoteId, recoveredSubmission.remoteId)

                    // Advance only this isolated fixture's scheduler clock after testing startup recovery.
                    activeFixture.dao.updateAttempt(recovered.copy(nextAt = 0))
                    val owner = "process-recovery-stage-2-${UUID.randomUUID()}"
                    var result = requireNotNull(
                        activeFixture.dao.claim(recovered.id, owner, System.currentTimeMillis(), System.currentTimeMillis() + LEASE_MS, 1),
                    )
                    result = activeFixture.step.run(result, owner, config)
                    assertEquals(Phase.NORMALIZE, result.phase)
                    result = activeFixture.step.run(result, owner, config)
                    assertEquals(Phase.PERSIST, result.phase)
                    result = activeFixture.step.run(result, owner, config)

                    assertEquals(ExecutionState.FINISHED, result.state)
                    assertEquals(Outcome.SUCCESS, result.outcome)
                    val after = activeFixture.dao.submissions(result.id).single()
                    assertEquals(before.id, after.id)
                    assertEquals(before.createdAt, after.createdAt)
                    assertEquals(before.estimatedMicrousd, after.estimatedMicrousd)
                    assertEquals(SubmissionState.RESPONSE_SAVED, after.state)
                    assertEquals(evidence.remoteId, after.remoteId)
                    assertEquals(evidence.stageOneSubmissionCount, activeFixture.dao.submissions(result.id).size)

                    val artifactRow = requireNotNull(activeFixture.dao.artifact(evidence.artifactId))
                    assertEquals(result.id, artifactRow.attemptId)
                    assertEquals(true, artifactRow.complete)
                    val artifact = activeFixture.artifacts.read(evidence.artifactId)
                    assertEquals(SYNTHETIC_TRANSCRIPT, artifact.text)
                    assertEquals(SourceKind.LOCAL_AUDIO, artifact.source.kind)
                    assertEquals(Provider.ASSEMBLYAI, artifact.provenance.provider)
                    assertEquals(AssemblyAiAdapter.MODEL_U2, artifact.provenance.reportedModel)

                    val requests = network.takeRequests(1)
                    assertEquals(listOf("GET /v2/transcript/$REMOTE_ID"), requests.map { "${it.method} ${it.path}" })
                    assertTrue(requests.all { it.getHeader("Authorization") == SYNTHETIC_CANARY_KEY })
                    assertEquals(0, requests.count(::isPaidTranscriptPost))
                    assertEquals(1, network.requestCount)
                    assertArrayEquals(evidenceBytes, readBounded(evidenceFile))
                }
            }
        } finally {
            try {
                fixture?.cancelFixtureWork(evidence.jobId)
            } finally {
                database.close()
            }
        }
        assertArrayEquals(evidenceBytes, readBounded(evidenceFile))
        Log.i(TAG, "stage=2 result=FINISHED process_changed=true tls_requests=1 paid_posts=0 submissions=1 artifact=durable evidence=unchanged")
        assertTrue("verified process fixture root could not be removed", root.deleteRecursively())
        assertFalse(root.exists())
    }

    @Test
    fun stage1PersistsRevokedLocalAudioImport() {
        val base = requireStage(IMPORT_STAGE_ONE)
        val root = File(base.noBackupFilesDir, IMPORT_ROOT_NAME)
        assertFalse("pending audio import fixture already exists: run stage 2 or inspect it", root.exists())
        assertTrue("could not create isolated audio import fixture root", root.mkdir())
        val context = IsolatedContext(base, root)
        var evidence: ImportEvidence? = null
        var providerReset = false
        try {
            val treeUri = providerAdmin {
                ExportFixtureProvider.configure(base, ExportFixtureProvider.Mode.SUCCESS, base.packageName)
            }
            val documentUri = providerAdmin {
                ExportFixtureProvider.seedDocument(base, IMPORT_DOCUMENT_NAME, AudioImportFixtureProvider.WAV_BYTES)
            }.toUri()
            assertTrue("fixture document read grant was not installed", hasReadGrant(base, documentUri))

            val database = Room.databaseBuilder(context, SourceScribeDatabase::class.java, DATABASE_NAME).build()
            try {
                runBlocking {
                    val dao = database.records()
                    val imported = AudioImport(context, NativeRuntime(base), SettingsStore(context), dao).import(documentUri)
                    val row = requireNotNull(dao.source(imported.id))
                    val importedFile = requireNotNull(row.importedPath).let(::File)
                    val expectedFile = File(context.noBackupFilesDir, "imports/${AudioImportFixtureProvider.WAV_SHA256}.audio")

                    assertEquals("local:${AudioImportFixtureProvider.WAV_SHA256}", imported.id)
                    assertEquals(SourceKind.LOCAL_AUDIO, imported.kind)
                    assertEquals(AudioImportFixtureProvider.WAV_SHA256, imported.contentHash)
                    assertEquals(AudioImportFixtureProvider.WAV_BYTES.size.toLong(), imported.fileBytes)
                    assertEquals(100L, imported.durationMs)
                    assertTrue(imported.mimeType.orEmpty().startsWith("audio/"))
                    assertFalse("durable source snapshot retained the external URI", row.snapshot.contains(documentUri.toString()))
                    assertEquals(expectedFile.absolutePath, importedFile.absolutePath)
                    assertTrue("private imported audio is missing", importedFile.isFile)
                    assertFalse("private imported audio must not be a symbolic link", Files.isSymbolicLink(importedFile.toPath()))
                    val privateBytes = importedFile.readBytes()
                    assertArrayEquals(AudioImportFixtureProvider.WAV_BYTES, privateBytes)
                    assertEquals(AudioImportFixtureProvider.WAV_SHA256, sha256(privateBytes))

                    evidence = ImportEvidence(
                        stageOnePid = Process.myPid(),
                        sourceId = imported.id,
                        contentHash = requireNotNull(imported.contentHash),
                        fileBytes = requireNotNull(imported.fileBytes),
                        durationMs = requireNotNull(imported.durationMs),
                        importedPath = importedFile.absolutePath,
                        documentUri = documentUri.toString(),
                        grantBeforeImport = true,
                        grantAfterRevocation = false,
                        externalFixtureDeleted = true,
                    )
                }
            } finally {
                database.close()
            }

            assertTrue("Room database was not persisted on disk", context.getDatabasePath(DATABASE_NAME).isFile)
            providerAdmin { ExportFixtureProvider.revokeTreeGrant(base, treeUri) }
            assertFalse("fixture document grant remained after explicit revocation", hasReadGrant(base, documentUri))
            providerAdmin { ExportFixtureProvider.reset(base) }
            providerReset = true
        } finally {
            if (!providerReset) {
                providerAdmin { ExportFixtureProvider.reset(base) }
            }
        }

        writeDurably(
            File(root, IMPORT_EVIDENCE_NAME),
            json.encodeToString(requireNotNull(evidence)).toByteArray(StandardCharsets.UTF_8),
        )
        Log.i(TAG, "stage=import-1 result=COPIED grant=revoked source=deleted db=closed evidence=durable")
    }

    @Test
    fun stage2ReopensPrivateAudioAfterGrantAndSourceLoss() {
        val base = requireStage(IMPORT_STAGE_TWO)
        val root = File(base.noBackupFilesDir, IMPORT_ROOT_NAME)
        val evidenceFile = File(root, IMPORT_EVIDENCE_NAME)
        assertTrue("audio import stage 1 evidence is missing", evidenceFile.isFile)
        val evidenceBytes = readBounded(evidenceFile)
        val evidence = json.decodeFromString<ImportEvidence>(evidenceBytes.toString(StandardCharsets.UTF_8))
        assertNotEquals("audio import stage 2 must run in a new instrumentation process", evidence.stageOnePid, Process.myPid())
        assertTrue(evidence.grantBeforeImport)
        assertFalse(evidence.grantAfterRevocation)
        assertTrue(evidence.externalFixtureDeleted)
        assertEquals(AudioImportFixtureProvider.WAV_SHA256, evidence.contentHash)
        assertEquals(AudioImportFixtureProvider.WAV_BYTES.size.toLong(), evidence.fileBytes)
        assertEquals(100L, evidence.durationMs)
        val documentUri = evidence.documentUri.toUri()
        assertFalse("revoked fixture grant unexpectedly returned in the new process", hasReadGrant(base, documentUri))

        val context = IsolatedContext(base, root)
        assertTrue("Room database from audio import stage 1 is missing", context.getDatabasePath(DATABASE_NAME).isFile)
        val database = Room.databaseBuilder(context, SourceScribeDatabase::class.java, DATABASE_NAME).build()
        try {
            runBlocking {
                val row = requireNotNull(database.records().source(evidence.sourceId))
                val source = json.decodeFromString<Source>(row.snapshot)
                val importedFile = requireNotNull(row.importedPath).let(::File)
                val expectedFile = File(context.noBackupFilesDir, "imports/${evidence.contentHash}.audio")

                assertEquals(evidence.sourceId, source.id)
                assertEquals(SourceKind.LOCAL_AUDIO, source.kind)
                assertEquals(evidence.contentHash, source.contentHash)
                assertEquals(evidence.fileBytes, source.fileBytes)
                assertEquals(evidence.durationMs, source.durationMs)
                assertTrue(source.mimeType.orEmpty().startsWith("audio/"))
                assertFalse("reopened source snapshot retained the external URI", row.snapshot.contains(evidence.documentUri))
                assertEquals(evidence.importedPath, importedFile.absolutePath)
                assertEquals(expectedFile.absolutePath, importedFile.absolutePath)
                assertTrue("reopened private imported audio is missing", importedFile.isFile)
                assertFalse("reopened private imported audio must not be a symbolic link", Files.isSymbolicLink(importedFile.toPath()))
                val privateBytes = importedFile.readBytes()
                assertArrayEquals(AudioImportFixtureProvider.WAV_BYTES, privateBytes)
                assertEquals(evidence.contentHash, sha256(privateBytes))
                assertArrayEquals(evidenceBytes, readBounded(evidenceFile))
            }
        } finally {
            database.close()
        }
        assertArrayEquals(evidenceBytes, readBounded(evidenceFile))
        Log.i(TAG, "stage=import-2 result=PRIVATE_COPY_VERIFIED process_changed=true grant=denied access=room_file_only evidence=unchanged")
        assertTrue("verified audio import fixture root could not be removed", root.deleteRecursively())
        assertFalse(root.exists())
    }

    private fun requireStage(expected: String): Context {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("process fixture is opt-in", arguments.getString(OPT_IN_ARGUMENT) == "true")
        assertEquals("select exactly one process fixture stage", expected, arguments.getString(STAGE_ARGUMENT))
        return InstrumentationRegistry.getInstrumentation().targetContext
    }

    private class Fixture(
        private val context: IsolatedContext,
        private val database: SourceScribeDatabase,
        providerHttp: ProviderHttp,
    ) {
        val dao = database.records()
        val artifacts = ArtifactFiles(File(context.filesDir, "artifacts"))
        private val settings = SettingsStore(context)
        private val credentials = CredentialStore(context)
        private val runtime = NativeRuntime(context)
        private val extractor = ExtractorEngine(runtime)
        private val engines = EngineUpdateManager(context, runtime)
        private val exports = ExportStore(context, dao, artifacts)
        private val notifications = JobNotifications(context)
        private val storage = StorageBudget(context, settings)
        private val workManager = ensureWorkManager(context.baseContext)
        val step = SttStep(context, database, dao, credentials, runtime, extractor, engines, artifacts, providerHttp)
        val coordinator by lazy {
            JobCoordinator(
                context,
                database,
                dao,
                settings,
                extractor,
                engines,
                artifacts,
                step,
                exports,
                notifications,
                storage,
                credentials,
                providerHttp,
            )
        }

        fun cancelFixtureWork(jobId: String) {
            workManager.cancelAllWorkByTag("job:$jobId").result.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }

        suspend fun seed(): Seeded {
            val audio = ByteArray(AUDIO_BYTES) { index -> (index and 0xff).toByte() }
            val audioHash = sha256(audio)
            val source = Source(
                id = "local:$audioHash",
                kind = SourceKind.LOCAL_AUDIO,
                contentHash = audioHash,
                fileName = "process-fixture.mp3",
                mimeType = AUDIO_MIME_TYPE,
                fileBytes = audio.size.toLong(),
                durationMs = DURATION_MS,
            )
            val credentialId = credentials.save(Provider.ASSEMBLYAI, Region.US, SYNTHETIC_CANARY_KEY)
            val config = JobConfig(
                mode = AcquisitionMode.STT_ONLY,
                provider = Provider.ASSEMBLYAI,
                model = AssemblyAiAdapter.MODEL_U2,
                region = Region.US,
                credentialId = credentialId,
                uploadApproved = true,
                maxAudioSeconds = 3_600,
                maxCostMicrousd = 1_000_000,
                audioRetention = AudioRetention.TEMPORARY,
                retainRaw = false,
            )
            val now = System.currentTimeMillis()
            val artifactId = UUID.randomUUID().toString()
            val jobId = UUID.randomUUID().toString()
            val attemptId = UUID.randomUUID().toString()
            val owner = "process-recovery-stage-1-${UUID.randomUUID()}"
            val checkpoint = FixtureCheckpoint(
                artifactId = artifactId,
                artifactCreatedAt = now,
                source = source,
                durationMs = DURATION_MS,
                chunkCount = 1,
                nextChunkIndex = 1,
                prepared = listOf(FixtureChunk(0, 0, DURATION_MS, audio.size.toLong(), audioHash, AUDIO_MIME_TYPE)),
            )
            val attemptDirectory = File(context.noBackupFilesDir, "attempts/$attemptId")
            assertTrue(attemptDirectory.mkdirs())
            writeDurably(File(attemptDirectory, "audio-0.mp3"), audio)
            val attempt = AttemptRow(
                id = attemptId,
                jobId = jobId,
                branch = Branch.STT,
                number = 1,
                createdAt = now,
                state = ExecutionState.RUNNING,
                phase = Phase.SUBMIT,
                checkpoint = json.encodeToString(checkpoint),
                leaseOwner = owner,
                leaseUntil = now + LEASE_MS,
                processedBytes = audio.size.toLong(),
                totalBytes = audio.size.toLong(),
            )
            dao.createJob(
                SourceRow(source.id, json.encodeToString(source), "Synthetic process fixture"),
                JobRow(jobId, source.id, json.encodeToString(config), now),
                listOf(attempt),
            )
            return Seeded(attempt, config, owner, artifactId)
        }
    }

    private class FixtureNetwork(vararg responses: MockResponse) : AutoCloseable {
        private val server = MockWebServer()
        private val serverCertificate = HeldCertificate.Builder()
            .addSubjectAlternativeName("localhost")
            .addSubjectAlternativeName("127.0.0.1")
            .build()
        private val serverCertificates = HandshakeCertificates.Builder().heldCertificate(serverCertificate).build()
        private val clientCertificates = HandshakeCertificates.Builder()
            .addTrustedCertificate(serverCertificate.certificate)
            .build()
        val providerHttp: ProviderHttp
        val requestCount get() = server.requestCount

        init {
            responses.forEach(server::enqueue)
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
        }

        fun takeRequests(expected: Int): List<RecordedRequest> = List(expected) { index ->
            requireNotNull(server.takeRequest(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                "timed out waiting for local TLS request ${index + 1}"
            }
        }

        override fun close() {
            server.shutdown()
        }
    }

    private class IsolatedContext(base: Context, root: File) : ContextWrapper(base) {
        private val files = File(root, "files").also { check(it.mkdirs() || it.isDirectory) }
        private val noBackup = File(root, "no-backup").also { check(it.mkdirs() || it.isDirectory) }
        private val cache = File(root, "cache").also { check(it.mkdirs() || it.isDirectory) }
        private val databases = File(root, "databases").also { check(it.mkdirs() || it.isDirectory) }

        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = files
        override fun getNoBackupFilesDir(): File = noBackup
        override fun getCacheDir(): File = cache
        override fun getDatabasePath(name: String): File {
            require(name == DATABASE_NAME)
            return File(databases, name)
        }
    }

    private data class Seeded(
        val attempt: AttemptRow,
        val config: JobConfig,
        val owner: String,
        val artifactId: String,
    )

    @Serializable
    private data class StageEvidence(
        val stageOnePid: Int,
        val attemptId: String,
        val jobId: String,
        val artifactId: String,
        val remoteId: String,
        val stageOneRequestCount: Int,
        val stageOnePaidPostCount: Int,
        val stageOneSubmissionCount: Int,
    )

    @Serializable
    private data class ImportEvidence(
        val stageOnePid: Int,
        val sourceId: String,
        val contentHash: String,
        val fileBytes: Long,
        val durationMs: Long,
        val importedPath: String,
        val documentUri: String,
        val grantBeforeImport: Boolean,
        val grantAfterRevocation: Boolean,
        val externalFixtureDeleted: Boolean,
    )

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

    companion object {
        private const val TAG = "SourceScribeProcessTest"
        private const val OPT_IN_ARGUMENT = "sourcescribeProcessFixture"
        private const val STAGE_ARGUMENT = "sourcescribeProcessStage"
        private const val STAGE_ONE = "1"
        private const val STAGE_TWO = "2"
        private const val IMPORT_STAGE_ONE = "import-1"
        private const val IMPORT_STAGE_TWO = "import-2"
        private const val ROOT_NAME = "process-recovery-fixture-v1"
        private const val IMPORT_ROOT_NAME = "process-audio-import-fixture-v1"
        private const val DATABASE_NAME = "process-recovery.db"
        private const val EVIDENCE_NAME = "stage-1-evidence.json"
        private const val IMPORT_EVIDENCE_NAME = "import-stage-1-evidence.json"
        private const val IMPORT_DOCUMENT_NAME = "process-audio-import.wav"
        private const val AUDIO_MIME_TYPE = "audio/mpeg"
        private const val AUDIO_BYTES = 4 * 1024
        private const val DURATION_MS = 1_000L
        private const val LEASE_MS = 9 * 60_000L
        private const val REQUEST_TIMEOUT_SECONDS = 5L
        private const val MAX_EVIDENCE_BYTES = 16 * 1024L
        private const val REMOTE_ID = "2072a82b-aa22-4962-add2-6121c36c17c6"
        private const val SYNTHETIC_CANARY_KEY = "sourcescribe-process-fixture-canary-not-a-provider-key"
        private const val SYNTHETIC_TRANSCRIPT = "synthetic process recovery transcript"
        private const val AAI_UPLOAD_RESPONSE = "{\"upload_url\":\"https://cdn.assemblyai.com/upload/process-fixture\"}"
        private const val AAI_QUEUED_RESPONSE = "{\"id\":\"$REMOTE_ID\",\"status\":\"queued\"}"
        private const val AAI_COMPLETED_RESPONSE = "{\"id\":\"$REMOTE_ID\",\"status\":\"completed\",\"text\":\"$SYNTHETIC_TRANSCRIPT\",\"language_code\":\"en\",\"speech_model_used\":\"universal-2\",\"words\":[{\"text\":\"synthetic\",\"start\":0,\"end\":1000}],\"utterances\":[{\"text\":\"$SYNTHETIC_TRANSCRIPT\",\"start\":0,\"end\":1000}]}"
        private val json = Json {
            encodeDefaults = true
            explicitNulls = true
            ignoreUnknownKeys = true
        }

        private fun isPaidTranscriptPost(request: RecordedRequest): Boolean =
            request.method == "POST" && request.path == "/v2/transcript"

        private fun ensureWorkManager(context: Context): WorkManager = try {
            WorkManager.getInstance(context)
        } catch (_: IllegalStateException) {
            WorkManagerTestInitHelper.initializeTestWorkManager(context)
            WorkManager.getInstance(context)
        }

        private fun hasReadGrant(context: Context, uri: Uri): Boolean =
            context.checkUriPermission(uri, Process.myPid(), Process.myUid(), Intent.FLAG_GRANT_READ_URI_PERMISSION) ==
                PackageManager.PERMISSION_GRANTED

        private inline fun <T> providerAdmin(operation: () -> T): T {
            val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
            uiAutomation.adoptShellPermissionIdentity(ExportFixtureProvider.CONTROL_PERMISSION)
            return try {
                operation()
            } finally {
                uiAutomation.dropShellPermissionIdentity()
            }
        }

        private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(Locale.ROOT, it) }

        private fun readBounded(file: File): ByteArray {
            val length = file.length()
            require(length in 1..MAX_EVIDENCE_BYTES) { "process evidence size is invalid" }
            return file.readBytes().also { require(it.size.toLong() == length) }
        }

        private fun writeDurably(target: File, bytes: ByteArray) {
            require(!target.exists()) { "refusing to overwrite durable process fixture data" }
            val parent = requireNotNull(target.parentFile)
            check(parent.isDirectory || parent.mkdirs())
            val temporary = File(parent, ".${target.name}.new")
            require(!temporary.exists()) { "refusing to overwrite pending process fixture data" }
            var moved = false
            try {
                FileOutputStream(temporary).use { output ->
                    output.write(bytes)
                    output.flush()
                    output.fd.sync()
                }
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
                moved = true
            } finally {
                if (!moved) temporary.delete()
            }
        }
    }
}
