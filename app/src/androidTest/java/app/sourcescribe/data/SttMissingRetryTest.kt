package app.sourcescribe.data

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.sourcescribe.core.AcquisitionMode
import app.sourcescribe.core.ArtifactFiles
import app.sourcescribe.core.AudioTrack
import app.sourcescribe.core.AudioRetention
import app.sourcescribe.core.Branch
import app.sourcescribe.core.ExecutionState
import app.sourcescribe.core.Interval
import app.sourcescribe.core.JobConfig
import app.sourcescribe.core.Origin
import app.sourcescribe.core.Outcome
import app.sourcescribe.core.Phase
import app.sourcescribe.core.Provider
import app.sourcescribe.core.ProviderHttp
import app.sourcescribe.core.Provenance
import app.sourcescribe.core.Region
import app.sourcescribe.core.Source
import app.sourcescribe.core.SourceKind
import app.sourcescribe.core.SubmissionResult
import app.sourcescribe.core.TranscriptDocument
import app.sourcescribe.core.TranscriptScope
import app.sourcescribe.core.TranscriptionRequest
import app.sourcescribe.core.providers.GroqAdapter
import app.sourcescribe.extractor.EngineUpdateManager
import app.sourcescribe.extractor.ExtractorEngine
import app.sourcescribe.extractor.NativeRuntime
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipInputStream
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
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SttMissingRetryTest {
    @Test
    fun middleMissingChunkIssuesOneRequestAndPreservesOriginalEvidence() = completeMissingRetry(cancelled = false)

    @Test
    fun cancelledPartialArtifactReusesSuccessfulChunksWithoutChargingAgain() = completeMissingRetry(cancelled = true)

    private fun completeMissingRetry(
        cancelled: Boolean,
        firstResponse: String = response("zero"),
        expectedOutcome: Outcome = Outcome.SUCCESS,
    ) = withFixture {
        var seeded = seed(firstResponse = firstResponse)
        if (cancelled) {
            val previous = seeded.previous.copy(state = ExecutionState.CANCELLED, outcome = Outcome.CANCELLED)
            dao.updateAttempt(previous)
            seeded = seeded.copy(previous = previous)
        }
        server.enqueue(MockResponse().setResponseCode(200).setBody(response("middle")))

        val prepared = step.prepareMissingRetry(
            seeded.previous,
            seeded.target,
            seeded.config,
            seeded.partial,
            storage,
        )

        assertEquals(listOf(0, 2), prepared.submissions.map { it.chunkIndex })
        assertTrue(prepared.submissions.all { it.estimatedMicrousd == 0L })
        assertEquals(
            seeded.originalSubmissions.filter { it.chunkIndex != 1 }.map { it.id },
            prepared.submissions.map { it.reusedFromId },
        )
        val unchanged = dao.submissions(seeded.previous.id)
        assertEquals(seeded.originalSubmissions, unchanged)
        for (index in 0..2) {
            val oldAudio = attemptDirectory(seeded.previous.id).resolve("audio-$index.mp3")
            val newAudio = attemptDirectory(prepared.attempt.id).resolve("audio-$index.mp3")
            assertEquals(oldAudio.readBytes().toList(), newAudio.readBytes().toList())
            assertFalse(Files.isSymbolicLink(newAudio.toPath()))
            assertFalse(Files.isSameFile(oldAudio.toPath(), newAudio.toPath()))
        }

        database.withTransaction {
            dao.insertAttempt(prepared.attempt)
            for (submission in prepared.submissions) dao.insertSubmission(submission)
        }
        val owner = "retry-owner-${UUID.randomUUID()}"
        var row = prepared.attempt.copy(
            state = ExecutionState.RUNNING,
            leaseOwner = owner,
            leaseUntil = System.currentTimeMillis() + LEASE_MS,
        ).also { dao.updateAttempt(it) }
        row = step.run(row, owner, seeded.config)
        assertEquals(Phase.NORMALIZE, row.phase)
        row = step.run(row, owner, seeded.config)
        assertEquals(Phase.PERSIST, row.phase)
        row = step.run(row, owner, seeded.config)

        assertEquals(ExecutionState.FINISHED, row.state)
        assertEquals(expectedOutcome, row.outcome)
        assertEquals(1, server.requestCount)
        val targetCheckpoint = json.decodeFromString<FixtureCheckpoint>(row.checkpoint)
        val document = artifacts.read(targetCheckpoint.artifactId)
        assertEquals(listOf("zero", "middle", "two"), document.segments.map { it.text })
        if (expectedOutcome == Outcome.PARTIAL_SUCCESS) {
            assertTrue(document.warnings.contains("CHUNK_0_MISSING_SEGMENT_TIMESTAMPS"))
        }
        assertEquals(seeded.partial.artifactId, document.provenance.reusedArtifactId)
        assertEquals(emptyList<Int>(), document.scope.missingChunks)
        assertEquals(listOf(0, 1, 2), dao.submissions(row.id).map { it.chunkIndex })
        assertEquals(listOf("chunk-0.json", "chunk-1.json", "chunk-2.json"), rawEntries(requireNotNull(artifacts.rawFile(document.artifactId))))
        assertEquals(seeded.originalSubmissions, dao.submissions(seeded.previous.id))
    }

    @Test
    fun retainedIncompleteResponseRemainsPartialAfterRetry() = completeMissingRetry(
        cancelled = false,
        firstResponse = """{"text":"zero","language":"en"}""",
        expectedOutcome = Outcome.PARTIAL_SUCCESS,
    )

    @Test
    fun validJsonTextTamperFailsClosedWithoutProviderRequest() = withFixture {
        val seeded = seed()
        responseFile(seeded.previous.id, seeded.originalSubmissions.first().id)
            .writeText(response("tampered"), StandardCharsets.UTF_8)

        expectMissingRetryData {
            step.prepareMissingRetry(seeded.previous, seeded.target, seeded.config, seeded.partial, storage)
        }

        assertEquals(0, requestAttempts.get())
        assertFalse(attemptDirectory(seeded.target.id).exists())
    }

    @Test
    fun wrongSubmissionBindingsFailClosedWithoutProviderRequest() = withFixture {
        val seeded = seed()
        val original = seeded.originalSubmissions.first()
        val wrongBindings = listOf(
            original.copy(provider = Provider.OPENAI.name),
            original.copy(credentialId = UUID.randomUUID().toString()),
            original.copy(region = Region.EU.name),
            original.copy(inputHash = "1".repeat(64)),
            original.copy(configHash = "0".repeat(64)),
            original.copy(rawResponsePath = root.resolve("outside.json").absolutePath),
        )
        for (wrong in wrongBindings) {
            dao.updateSubmission(wrong)
            expectMissingRetryData {
                step.prepareMissingRetry(seeded.previous, seeded.target, seeded.config, seeded.partial, storage)
            }
            dao.updateSubmission(original)
        }
        val missing = seeded.originalSubmissions.single { it.chunkIndex == 1 }
        dao.updateSubmission(missing.copy(configHash = "0".repeat(64)))
        expectMissingRetryData {
            step.prepareMissingRetry(seeded.previous, seeded.target, seeded.config, seeded.partial, storage)
        }

        assertEquals(0, requestAttempts.get())
        assertFalse(attemptDirectory(seeded.target.id).exists())
    }

    @Test
    fun missingPreparedAudioFailsClosedWithoutProviderRequest() = withFixture {
        val seeded = seed()
        assertTrue(attemptDirectory(seeded.previous.id).resolve("audio-1.mp3").delete())

        expectMissingRetryData {
            step.prepareMissingRetry(seeded.previous, seeded.target, seeded.config, seeded.partial, storage)
        }

        assertEquals(0, requestAttempts.get())
        assertFalse(attemptDirectory(seeded.target.id).exists())
    }

    @Test
    fun missingChunkWithoutSubmissionCanBePreparedWithoutProviderRequest() = withFixture {
        val seeded = seed(missingSubmissionState = null)

        val prepared = step.prepareMissingRetry(
            seeded.previous,
            seeded.target,
            seeded.config,
            seeded.partial,
            storage,
        )

        assertEquals(listOf(0, 2), prepared.submissions.map { it.chunkIndex })
        assertEquals(0, requestAttempts.get())
    }

    @Test
    fun missingChunkWithSavedResponseRequiresExplicitFullRetry() = withFixture {
        val seeded = seed(missingSubmissionState = SubmissionState.RESPONSE_SAVED)

        expectMissingRetryResponseSaved {
            step.prepareMissingRetry(seeded.previous, seeded.target, seeded.config, seeded.partial, storage)
        }

        assertEquals(0, requestAttempts.get())
        assertEquals(0, server.requestCount)
        assertFalse(attemptDirectory(seeded.target.id).exists())
    }

    @Test
    fun missingChunkWithUnsafeSubmissionStateFailsClosed() = withFixture {
        val seeded = seed()
        val missing = seeded.originalSubmissions.single { it.chunkIndex == 1 }
        for (state in listOf(
            SubmissionState.PREPARED,
            SubmissionState.SENDING,
            SubmissionState.ACCEPTED,
            SubmissionState.UNCERTAIN,
            SubmissionState.REMOTE_DELETED,
        )) {
            dao.updateSubmission(missing.copy(state = state, rejectionCode = null))
            expectMissingRetryData {
                step.prepareMissingRetry(seeded.previous, seeded.target, seeded.config, seeded.partial, storage)
            }
        }

        assertEquals(0, requestAttempts.get())
        assertFalse(attemptDirectory(seeded.target.id).exists())
    }

    @Test
    fun retainedProviderWarningsMustMatchPartialArtifact() = withFixture {
        val seeded = seed()
        val first = seeded.originalSubmissions.single { it.chunkIndex == 0 }
        responseFile(seeded.previous.id, first.id).writeText(
            response("zero").dropLast(1) + ",\"model\":\"${"x".repeat(201)}\"}",
            StandardCharsets.UTF_8,
        )

        expectMissingRetryData {
            step.prepareMissingRetry(seeded.previous, seeded.target, seeded.config, seeded.partial, storage)
        }

        assertEquals(0, requestAttempts.get())
        assertFalse(attemptDirectory(seeded.target.id).exists())
    }

    @Test
    fun youtubeRetryRequiresExactTrackAndVideoBinding() = withFixture {
        val wrongBindings = listOf(
            seed(youtube = true, checkpointAudioId = "251"),
            seed(youtube = true, audioSourceVideoId = "differentVideo"),
        )

        for (seeded in wrongBindings) {
            expectMissingRetryData {
                step.prepareMissingRetry(seeded.previous, seeded.target, seeded.config, seeded.partial, storage)
            }
            assertFalse(attemptDirectory(seeded.target.id).exists())
        }
        assertEquals(0, requestAttempts.get())
    }

    @Test
    fun youtubeRetryValidatesRetainedSourceAudioWhenPresent() = withFixture {
        val seeded = seed(youtube = true)
        attemptDirectory(seeded.previous.id).resolve("source.audio").writeBytes(byteArrayOf(1, 2, 3))

        expectMissingRetryData {
            step.prepareMissingRetry(seeded.previous, seeded.target, seeded.config, seeded.partial, storage)
        }

        assertEquals(0, requestAttempts.get())
        assertFalse(attemptDirectory(seeded.target.id).exists())
    }

    @Test
    fun youtubeRetryOfRetryDoesNotRequireIntentionallyUncopiedSourceAudio() = withFixture {
        val seeded = seed(youtube = true, sourceAudioPresent = false)

        val prepared = step.prepareMissingRetry(
            seeded.previous,
            seeded.target,
            seeded.config,
            seeded.partial,
            storage,
        )

        assertEquals(listOf(0, 2), prepared.submissions.map { it.chunkIndex })
        assertEquals(0, requestAttempts.get())
    }

    @Test
    fun normalizeRawAggregatePartialCannotResubmitSavedMissingChunk() = withFixture {
        val seeded = normalizeRawAggregatePartial()
        assertEquals(listOf(1), seeded.partial.scope.missingChunks)
        assertTrue(seeded.partial.warnings.contains("CHUNK_1_RAW_RESPONSE_AGGREGATE_TOO_LARGE"))

        expectMissingRetryResponseSaved {
            step.prepareMissingRetry(seeded.previous, seeded.target, seeded.config, seeded.partial, storage)
        }

        assertEquals(0, requestAttempts.get())
        assertEquals(0, server.requestCount)
        assertFalse(attemptDirectory(seeded.target.id).exists())
    }

    private fun withFixture(block: suspend MissingRetryFixture.() -> Unit) {
        val fixture = MissingRetryFixture(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            runBlocking { fixture.block() }
        } finally {
            fixture.close()
        }
    }

    private class MissingRetryFixture(base: Context) {
        val root = File(base.cacheDir, "stt-missing-retry-${UUID.randomUUID()}").also { check(it.mkdirs()) }
        private val context = IsolatedContext(base, root)
        val database = Room.inMemoryDatabaseBuilder(context, SourceScribeDatabase::class.java).build()
        val dao = database.records()
        private val credentials = CredentialStore(context)
        private val runtime = NativeRuntime(context)
        private val extractor = ExtractorEngine(runtime)
        private val engines = EngineUpdateManager(context, runtime)
        val artifacts = ArtifactFiles(File(context.filesDir, "artifacts"))
        val storage = StorageBudget(context, SettingsStore(context))
        val server = MockWebServer()
        val requestAttempts = AtomicInteger()
        private val serverCertificate = HeldCertificate.Builder()
            .addSubjectAlternativeName("localhost")
            .addSubjectAlternativeName("127.0.0.1")
            .build()
        private val serverCertificates = HandshakeCertificates.Builder()
            .heldCertificate(serverCertificate)
            .build()
        private val clientCertificates = HandshakeCertificates.Builder()
            .addTrustedCertificate(serverCertificate.certificate)
            .build()
        private val providerHttp: ProviderHttp
        val step: SttStep

        init {
            server.useHttps(serverCertificates.sslSocketFactory(), false)
            server.start()
            val fixtureUrl = server.url("/fixture")
            val client = OkHttpClient.Builder()
                .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
                .hostnameVerifier(HostnameVerifier { host, _ -> host == fixtureUrl.host })
                .addInterceptor(Interceptor { chain ->
                    requestAttempts.incrementAndGet()
                    val original = chain.request()
                    val rewritten = original.url.newBuilder()
                        .scheme(fixtureUrl.scheme)
                        .host(fixtureUrl.host)
                        .port(fixtureUrl.port)
                        .build()
                    chain.proceed(original.newBuilder().url(rewritten).build())
                })
                .build()
            providerHttp = ProviderHttp(client)
            step = SttStep(context, database, dao, credentials, runtime, extractor, engines, artifacts, providerHttp)
        }

        suspend fun seed(
            firstResponse: String = response("zero"),
            missingSubmissionState: SubmissionState? = SubmissionState.REJECTED,
            youtube: Boolean = false,
            checkpointAudioId: String = AUDIO_TRACK_ID,
            sourceAudioTrackId: String? = if (youtube) AUDIO_TRACK_ID else null,
            audioSourceVideoId: String = VIDEO_ID,
            sourceAudioPresent: Boolean = youtube,
            insertArtifact: Boolean = true,
        ): Seeded {
            val credentialId = credentials.save(Provider.GROQ, Region.US, "fixture-${UUID.randomUUID()}")
            val config = JobConfig(
                mode = AcquisitionMode.STT_ONLY,
                provider = Provider.GROQ,
                model = GroqAdapter.MODEL_TURBO,
                region = Region.US,
                credentialId = credentialId,
                uploadApproved = true,
                maxAudioSeconds = 3_600,
                maxCostMicrousd = 1_000_000,
                audioRetention = AudioRetention.TEMPORARY,
                retainRaw = true,
            )
            val now = System.currentTimeMillis()
            val jobId = UUID.randomUUID().toString()
            val previousId = UUID.randomUUID().toString()
            val targetId = UUID.randomUUID().toString()
            val partialId = UUID.randomUUID().toString()
            val targetArtifactId = UUID.randomUUID().toString()
            val source = if (youtube) {
                Source(
                    id = "youtube:$VIDEO_ID",
                    kind = SourceKind.YOUTUBE,
                    canonicalUrl = "https://www.youtube.com/watch?v=$VIDEO_ID",
                    videoId = VIDEO_ID,
                    durationMs = DURATION_MS,
                )
            } else {
                Source(
                    id = "local:${"a".repeat(64)}",
                    kind = SourceKind.LOCAL_AUDIO,
                    contentHash = "a".repeat(64),
                    fileName = "fixture.mp3",
                    mimeType = AUDIO_MIME_TYPE,
                    durationMs = DURATION_MS,
                )
            }
            val audio = if (youtube) {
                AudioTrack(checkpointAudioId, audioSourceVideoId, "en", "original", true, "fixture")
            } else {
                null
            }
            val chunks = SttStep.chunkPlan(DURATION_MS).map { window ->
                val bytes = ByteArray(AUDIO_BYTES) { (window.index + it).toByte() }
                FixtureChunk(
                    index = window.index,
                    offsetMs = window.offsetMs,
                    durationMs = window.durationMs,
                    bytes = bytes.size.toLong(),
                    sha256 = sha256(bytes),
                    mimeType = AUDIO_MIME_TYPE,
                ) to bytes
            }
            val previousDirectory = attemptDirectory(previousId).also { check(it.mkdirs()) }
            chunks.forEach { (chunk, bytes) -> previousDirectory.resolve("audio-${chunk.index}.mp3").writeBytes(bytes) }
            val sourceAudio = ByteArray(AUDIO_BYTES) { (it * 3).toByte() }
            if (sourceAudioPresent) previousDirectory.resolve("source.audio").writeBytes(sourceAudio)
            val rawByIndex = mapOf(0 to firstResponse, 1 to response("middle"), 2 to response("two"))
            val configHash = sha256(json.encodeToString(config).toByteArray(StandardCharsets.UTF_8))
            val submissions = chunks.mapNotNull { (chunk, _) ->
                val state = if (chunk.index == 1) missingSubmissionState ?: return@mapNotNull null else SubmissionState.RESPONSE_SAVED
                val id = UUID.randomUUID().toString()
                val responseFile = responseFile(previousId, id)
                if (state == SubmissionState.RESPONSE_SAVED) {
                    responseFile.apply {
                        parentFile?.let { check(it.isDirectory || it.mkdirs()) }
                        writeText(requireNotNull(rawByIndex[chunk.index]), StandardCharsets.UTF_8)
                    }
                }
                SubmissionRow(
                    id = id,
                    attemptId = previousId,
                    chunkIndex = chunk.index,
                    provider = Provider.GROQ.name,
                    credentialId = credentialId,
                    region = Region.US.name,
                    inputHash = chunk.sha256,
                    configHash = configHash,
                    state = state,
                    createdAt = now,
                    estimatedMicrousd = 100L + chunk.index,
                    rawResponsePath = responseFile.absolutePath,
                    rejectionCode = if (state == SubmissionState.REJECTED) "INVALID_INPUT" else null,
                )
            }
            val transcripts = chunks.filter { it.first.index != 1 }.map { (chunk, _) ->
                val request = TranscriptionRequest(
                    audio = previousDirectory.resolve("audio-${chunk.index}.mp3"),
                    mimeType = AUDIO_MIME_TYPE,
                    config = config,
                    chunkIndex = chunk.index,
                    chunkStartMs = chunk.offsetMs,
                    durationMs = chunk.durationMs,
                )
                (GroqAdapter(providerHttp).parseSavedResponse(
                    requireNotNull(rawByIndex[chunk.index]).toByteArray(StandardCharsets.UTF_8),
                    request,
                ) as SubmissionResult.Direct).transcript
            }
            val previousCheckpoint = FixtureCheckpoint(
                artifactId = partialId,
                artifactCreatedAt = now,
                source = source,
                audio = audio,
                durationMs = DURATION_MS,
                chunkCount = chunks.size,
                nextChunkIndex = chunks.size,
                prepared = chunks.map { it.first },
                missingChunks = listOf(1),
                normalized = true,
                rawExtension = "zip",
                sourceAudioTrackId = sourceAudioTrackId,
                sourceAudioSha256 = if (youtube) sha256(sourceAudio) else null,
            )
            val partial = TranscriptDocument(
                artifactId = partialId,
                source = source,
                acquisition = config,
                provenance = Provenance(
                    origin = Origin.PROVIDER,
                    provider = Provider.GROQ,
                    requestedModel = GroqAdapter.MODEL_TURBO,
                    sourceAudioTrack = audio,
                    languageEvidence = "provider_response",
                    reportedLanguages = listOf("en"),
                ),
                language = "en",
                scope = TranscriptScope(
                    requestedDurationMs = DURATION_MS,
                    processedIntervals = chunks.filter { it.first.index != 1 }
                        .map { (chunk, _) -> Interval(chunk.offsetMs, chunk.offsetMs + chunk.durationMs) },
                    missingChunks = listOf(1),
                    technicallyComplete = false,
                ),
                segments = transcripts.flatMap { it.segments },
                warnings = transcripts.flatMapIndexed { index, transcript ->
                    transcript.warnings.map { "CHUNK_${if (index == 0) 0 else 2}_$it" }
                } + "MISSING_CHUNKS:1",
                createdAt = now,
                words = transcripts.flatMap { it.words },
            )
            val previous = AttemptRow(
                id = previousId,
                jobId = jobId,
                branch = Branch.STT,
                number = 1,
                createdAt = now,
                state = ExecutionState.FINISHED,
                phase = Phase.PERSIST,
                outcome = Outcome.PARTIAL_SUCCESS,
                checkpoint = json.encodeToString(previousCheckpoint),
            )
            val target = AttemptRow(
                id = targetId,
                jobId = jobId,
                branch = Branch.STT,
                number = 2,
                createdAt = now + 1,
                checkpoint = json.encodeToString(
                    FixtureCheckpoint(
                        artifactId = targetArtifactId,
                        artifactCreatedAt = now + 1,
                        source = source,
                    ),
                ),
            )
            dao.createJob(
                SourceRow(source.id, json.encodeToString(source), "Fixture"),
                JobRow(jobId, source.id, json.encodeToString(config), now),
                listOf(previous),
            )
            for (submission in submissions) dao.insertSubmission(submission)
            val partialBytes = json.encodeToString(partial).toByteArray(StandardCharsets.UTF_8)
            if (insertArtifact) {
                dao.insertArtifact(ArtifactRow(
                    id = partialId,
                    jobId = jobId,
                    attemptId = previousId,
                    branch = Branch.STT,
                    createdAt = now,
                    sha256 = sha256(partialBytes),
                    bytes = partialBytes.size.toLong(),
                    language = "en",
                    providerModel = GroqAdapter.MODEL_TURBO,
                    complete = false,
                    warningCount = partial.warnings.size,
                ))
            }
            return Seeded(previous, target, config, partial, submissions)
        }

        suspend fun normalizeRawAggregatePartial(): Seeded {
            val seeded = seed(insertArtifact = false)
            val checkpoint = json.decodeFromString<FixtureCheckpoint>(seeded.previous.checkpoint)
            val rawByIndex = mapOf(
                0 to paddedResponse("zero"),
                1 to paddedResponse("middle"),
                2 to response("two"),
            )
            val submissions = seeded.originalSubmissions.map { submission ->
                responseFile(seeded.previous.id, submission.id).writeText(
                    requireNotNull(rawByIndex[submission.chunkIndex]),
                    StandardCharsets.UTF_8,
                )
                submission.copy(state = SubmissionState.RESPONSE_SAVED, rejectionCode = null)
                    .also { dao.updateSubmission(it) }
            }
            val owner = "normalize-owner-${UUID.randomUUID()}"
            var row = seeded.previous.copy(
                state = ExecutionState.RUNNING,
                phase = Phase.NORMALIZE,
                outcome = Outcome.NONE,
                checkpoint = json.encodeToString(
                    checkpoint.copy(missingChunks = emptyList(), normalized = false, rawExtension = null),
                ),
                leaseOwner = owner,
                leaseUntil = System.currentTimeMillis() + LEASE_MS,
            ).also { dao.updateAttempt(it) }
            row = step.run(row, owner, seeded.config)
            check(row.phase == Phase.PERSIST)
            val document = json.decodeFromString<TranscriptDocument>(
                attemptDirectory(row.id).resolve("normalized.json").readText(StandardCharsets.UTF_8),
            )
            val encoded = json.encodeToString(document).toByteArray(StandardCharsets.UTF_8)
            dao.insertArtifact(
                ArtifactRow(
                    id = document.artifactId,
                    jobId = row.jobId,
                    attemptId = row.id,
                    branch = Branch.STT,
                    createdAt = document.createdAt,
                    sha256 = sha256(encoded),
                    bytes = encoded.size.toLong(),
                    language = document.language,
                    providerModel = document.provenance.reportedModel ?: document.provenance.requestedModel,
                    complete = false,
                    warningCount = document.warnings.size,
                ),
            )
            val finished = row.copy(
                state = ExecutionState.FINISHED,
                outcome = Outcome.PARTIAL_SUCCESS,
                leaseOwner = null,
                leaseUntil = 0,
            ).also { dao.updateAttempt(it) }
            return seeded.copy(previous = finished, partial = document, originalSubmissions = submissions)
        }

        fun attemptDirectory(id: String): File = context.noBackupFilesDir.resolve("attempts/$id")

        fun responseFile(attemptId: String, submissionId: String): File =
            attemptDirectory(attemptId).resolve("responses/$submissionId.json")

        fun close() {
            database.close()
            try {
                server.shutdown()
            } catch (_: IOException) {
                // Test cleanup remains inside the UUID root.
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

    private data class Seeded(
        val previous: AttemptRow,
        val target: AttemptRow,
        val config: JobConfig,
        val partial: TranscriptDocument,
        val originalSubmissions: List<SubmissionRow>,
    )

    @Serializable
    private data class FixtureCheckpoint(
        val artifactId: String,
        val artifactCreatedAt: Long,
        val source: Source,
        val audio: AudioTrack? = null,
        val durationMs: Long? = null,
        val chunkCount: Int = 0,
        val nextChunkIndex: Int = 0,
        val prepared: List<FixtureChunk> = emptyList(),
        val missingChunks: List<Int> = emptyList(),
        val normalized: Boolean = false,
        val rawExtension: String? = null,
        val sourceAudioTrackId: String? = null,
        val sourceAudioSha256: String? = null,
        val reusedArtifactId: String? = null,
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
        private const val AUDIO_BYTES = 4 * 1024
        private const val AUDIO_MIME_TYPE = "audio/mpeg"
        private const val AUDIO_TRACK_ID = "140"
        private const val DURATION_MS = 1_201_000L
        private const val LEASE_MS = 9 * 60_000L
        private const val VIDEO_ID = "dQw4w9WgXcQ"
        private val json = Json {
            encodeDefaults = true
            explicitNulls = true
            ignoreUnknownKeys = true
        }

        private fun response(text: String): String =
            """{"text":"$text","language":"en","segments":[{"id":0,"start":0.0,"end":1.0,"text":"$text"}]}"""

        private fun paddedResponse(text: String): String =
            response(text).dropLast(1) + ",\"padding\":\"${"x".repeat(9 * 1024 * 1024)}\"}"

        private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

        private suspend fun expectMissingRetryData(block: suspend () -> Unit) {
            try {
                block()
                fail("Expected MISSING_RETRY_DATA")
            } catch (failure: JobActionException) {
                assertEquals("MISSING_RETRY_DATA", failure.code)
            }
        }

        private suspend fun expectMissingRetryResponseSaved(block: suspend () -> Unit) {
            try {
                block()
                fail("Expected MISSING_RETRY_RESPONSE_SAVED")
            } catch (failure: JobActionException) {
                assertEquals("MISSING_RETRY_RESPONSE_SAVED", failure.code)
            }
        }

        private fun rawEntries(file: File): List<String> {
            val names = ArrayList<String>()
            ZipInputStream(file.inputStream()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    names += entry.name
                    zip.closeEntry()
                }
            }
            return names
        }
    }
}
