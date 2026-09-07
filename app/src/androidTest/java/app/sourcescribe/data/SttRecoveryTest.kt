package app.sourcescribe.data

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.sourcescribe.core.AcquisitionMode
import app.sourcescribe.core.ArtifactFiles
import app.sourcescribe.core.AudioRetention
import app.sourcescribe.core.AudioTrack
import app.sourcescribe.core.Branch
import app.sourcescribe.core.ExecutionState
import app.sourcescribe.core.Interval
import app.sourcescribe.core.JobConfig
import app.sourcescribe.core.Origin
import app.sourcescribe.core.Phase
import app.sourcescribe.core.Provider
import app.sourcescribe.core.ProviderHttp
import app.sourcescribe.core.Provenance
import app.sourcescribe.core.Region
import app.sourcescribe.core.Segment
import app.sourcescribe.core.Source
import app.sourcescribe.core.SourceKind
import app.sourcescribe.core.TranscriptDocument
import app.sourcescribe.core.TranscriptScope
import app.sourcescribe.core.providers.AssemblyAiAdapter
import app.sourcescribe.core.providers.GroqAdapter
import app.sourcescribe.extractor.EngineUpdateManager
import app.sourcescribe.extractor.ExtractorEngine
import app.sourcescribe.extractor.NativeRuntime
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipInputStream
import javax.net.ssl.HostnameVerifier
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Recovery checks use only the local HTTPS fixture and an isolated app context. */
@RunWith(AndroidJUnit4::class)
class SttRecoveryTest {
    @Test
    fun sendingWithoutRawResponseBecomesUncertainWithoutHttp() = withFixture {
        val seeded = seed(submissionState = SubmissionState.SENDING)

        val result = step.run(seeded.attempt, seeded.owner, seeded.config)

        assertEquals(ExecutionState.SUBMISSION_UNCERTAIN, result.state)
        assertEquals(SubmissionState.UNCERTAIN, dao.submissions(seeded.attempt.id).single().state)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun sendingWithBoundRawResponseReplaysAndPersistsWithoutHttp() = withFixture {
        val seeded = seed(
            submissionState = SubmissionState.SENDING,
            rawResponse = GROQ_RESPONSE,
        )

        var result = step.run(seeded.attempt, seeded.owner, seeded.config)
        assertEquals(Phase.NORMALIZE, result.phase)
        assertEquals(SubmissionState.RESPONSE_SAVED, dao.submissions(seeded.attempt.id).single().state)
        assertEquals(0, server.requestCount)

        result = step.run(result, seeded.owner, seeded.config)
        assertEquals(Phase.PERSIST, result.phase)
        result = step.run(result, seeded.owner, seeded.config)

        assertEquals(ExecutionState.FINISHED, result.state)
        assertNotNull(dao.artifact(seeded.artifactId))
        val responsePath = requireNotNull(dao.submissions(seeded.attempt.id).single().rawResponsePath)
        assertTrue(File(responsePath).isFile)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun wrongInputHashPausesWithoutHttp() = withFixture {
        val seeded = seed(
            submissionState = SubmissionState.SENDING,
            inputHash = "0".repeat(SHA256_HEX_LENGTH),
        )

        val result = step.run(seeded.attempt, seeded.owner, seeded.config)

        assertBindingMismatch(seeded, result)
    }

    @Test
    fun wrongConfigHashPausesWithoutHttp() = withFixture {
        val seeded = seed(
            submissionState = SubmissionState.SENDING,
            persistedConfigHash = "1".repeat(SHA256_HEX_LENGTH),
        )

        val result = step.run(seeded.attempt, seeded.owner, seeded.config)

        assertBindingMismatch(seeded, result)
    }

    @Test
    fun wrongResponsePathPausesWithoutHttp() = withFixture {
        val seeded = seed(
            submissionState = SubmissionState.SENDING,
            persistedRawPath = root.resolve("outside-response.json").absolutePath,
        )

        val result = step.run(seeded.attempt, seeded.owner, seeded.config)

        assertBindingMismatch(seeded, result)
    }

    @Test
    fun freshSubmissionAndStaleRetryIssueOneHttpRequest() = withFixture {
        val seeded = seed()
        server.enqueue(MockResponse().setResponseCode(200).setBody(GROQ_RESPONSE))

        val first = step.run(seeded.attempt, seeded.owner, seeded.config)
        assertEquals(Phase.NORMALIZE, first.phase)
        assertEquals(1, server.requestCount)
        assertEquals(1, dao.submissions(seeded.attempt.id).size)

        // A caller retrying with its stale claimed row must discover the saved response.
        val staleRetry = step.run(seeded.attempt, seeded.owner, seeded.config)

        assertEquals(Phase.NORMALIZE, staleRetry.phase)
        assertEquals(1, server.requestCount)
        assertEquals(1, dao.submissions(seeded.attempt.id).size)
    }

    @Test
    fun assemblySubmitErrorReceiptRemainsAcceptedWithoutResubmit() = withFixture {
        val seeded = seed(provider = Provider.ASSEMBLYAI)
        server.enqueue(MockResponse().setResponseCode(200).setBody(AAI_UPLOAD_RESPONSE))
        server.enqueue(MockResponse().setResponseCode(200).setBody(aaiError(REMOTE_ID_A)))

        val result = step.run(seeded.attempt, seeded.owner, seeded.config)

        assertEquals(Phase.RETRIEVE, result.phase)
        assertEquals(ExecutionState.WAITING_USER, result.state)
        assertEquals("REMOTE_FAILED", result.error)
        val submission = dao.submissions(seeded.attempt.id).single()
        assertEquals(SubmissionState.ACCEPTED, submission.state)
        assertEquals(REMOTE_ID_A, submission.remoteId)
        assertEquals(2, server.requestCount)
        assertEquals("/v2/upload", server.takeRequest().path)
        assertEquals("/v2/transcript", server.takeRequest().path)

        val resumed = step.run(result, seeded.owner, seeded.config)
        assertEquals(Phase.RETRIEVE, resumed.phase)
        assertEquals(ExecutionState.WAITING_USER, resumed.state)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun replayedAssemblySubmitErrorReceiptRecoversRemoteIdWithoutHttp() = withFixture {
        val seeded = seed(
            provider = Provider.ASSEMBLYAI,
            submissionState = SubmissionState.SENDING,
            rawResponse = aaiError(REMOTE_ID_A),
        )

        val result = step.run(seeded.attempt, seeded.owner, seeded.config)

        assertEquals(Phase.RETRIEVE, result.phase)
        assertEquals(ExecutionState.WAITING_USER, result.state)
        assertEquals("REMOTE_FAILED", result.error)
        val submission = dao.submissions(seeded.attempt.id).single()
        assertEquals(SubmissionState.ACCEPTED, submission.state)
        assertEquals(REMOTE_ID_A, submission.remoteId)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun assemblyCompletedSubmitKeepsReceiptIdWithoutResubmit() = withFixture {
        val seeded = seed(provider = Provider.ASSEMBLYAI)
        server.enqueue(MockResponse().setResponseCode(200).setBody(AAI_UPLOAD_RESPONSE))
        server.enqueue(MockResponse().setResponseCode(200).setBody(aaiCompleted(REMOTE_ID_A.uppercase(), "completed directly")))

        var result = step.run(seeded.attempt, seeded.owner, seeded.config)

        assertEquals(Phase.NORMALIZE, result.phase)
        assertEquals(SubmissionState.RESPONSE_SAVED, dao.submissions(seeded.attempt.id).single().state)
        assertEquals(REMOTE_ID_A, dao.submissions(seeded.attempt.id).single().remoteId)
        assertEquals(2, server.requestCount)

        result = step.run(result, seeded.owner, seeded.config)
        result = step.run(result, seeded.owner, seeded.config)

        assertEquals(ExecutionState.FINISHED, result.state)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun replayedAssemblyCompletedResponseKeepsReceiptIdWithoutHttp() = withFixture {
        val seeded = seed(
            provider = Provider.ASSEMBLYAI,
            submissionState = SubmissionState.SENDING,
            rawResponse = aaiCompleted(REMOTE_ID_A.uppercase(), "completed after crash"),
        )

        val result = step.run(seeded.attempt, seeded.owner, seeded.config)

        assertEquals(Phase.NORMALIZE, result.phase)
        assertEquals(ExecutionState.QUEUED, result.state)
        assertEquals(SubmissionState.RESPONSE_SAVED, dao.submissions(seeded.attempt.id).single().state)
        assertEquals(REMOTE_ID_A, dao.submissions(seeded.attempt.id).single().remoteId)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun replayedAssemblyCompletedResponseWithoutReceiptIdStaysUncertain() = withFixture {
        val seeded = seed(
            provider = Provider.ASSEMBLYAI,
            submissionState = SubmissionState.SENDING,
            rawResponse = aaiCompletedWithoutId("missing receipt after crash"),
        )

        val result = step.run(seeded.attempt, seeded.owner, seeded.config)

        assertEquals(ExecutionState.SUBMISSION_UNCERTAIN, result.state)
        assertEquals("REMOTE_RECEIPT_MISSING", result.error)
        assertEquals(SubmissionState.UNCERTAIN, dao.submissions(seeded.attempt.id).single().state)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun assemblyCompletedResponseWithoutReceiptIdStaysUncertain() = withFixture {
        val seeded = seed(provider = Provider.ASSEMBLYAI)
        server.enqueue(MockResponse().setResponseCode(200).setBody(AAI_UPLOAD_RESPONSE))
        server.enqueue(MockResponse().setResponseCode(200).setBody(aaiCompletedWithoutId("missing receipt")))

        val result = step.run(seeded.attempt, seeded.owner, seeded.config)

        assertEquals(ExecutionState.SUBMISSION_UNCERTAIN, result.state)
        assertEquals("REMOTE_RECEIPT_MISSING", result.error)
        assertEquals(SubmissionState.UNCERTAIN, dao.submissions(seeded.attempt.id).single().state)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun abortedProviderResponseBecomesUncertainAndNeverRetries() = withFixture {
        val seeded = seed()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        val first = step.run(seeded.attempt, seeded.owner, seeded.config)
        assertEquals(ExecutionState.SUBMISSION_UNCERTAIN, first.state)
        assertEquals(SubmissionState.UNCERTAIN, dao.submissions(seeded.attempt.id).single().state)
        assertEquals(1, server.requestCount)

        val second = step.run(first, seeded.owner, seeded.config)

        assertEquals(ExecutionState.SUBMISSION_UNCERTAIN, second.state)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun wrongLeaseOwnerCannotCreateSubmissionOrIssueHttp() = withFixture {
        val seeded = seed()

        try {
            step.run(seeded.attempt, "different-owner", seeded.config)
            throw AssertionError("a stale owner must not execute a paid phase")
        } catch (failure: CancellationException) {
            assertEquals("LEASE_LOST", failure.message)
        }

        assertEquals(seeded.attempt, dao.attempt(seeded.attempt.id))
        assertTrue(dao.submissions(seeded.attempt.id).isEmpty())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun expiredLeaseCannotCreateSubmissionOrIssueHttp() = withFixture {
        val seeded = seed(leaseOffsetMs = -1L)

        try {
            step.run(seeded.attempt, seeded.owner, seeded.config)
            throw AssertionError("an expired lease must not execute a paid phase")
        } catch (failure: CancellationException) {
            assertEquals("LEASE_LOST", failure.message)
        }

        assertEquals(seeded.attempt, dao.attempt(seeded.attempt.id))
        assertTrue(dao.submissions(seeded.attempt.id).isEmpty())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun unboundRemoteSourceAudioCannotReachSubmission() = withFixture {
        val seeded = seed(sourceKind = SourceKind.YOUTUBE, phase = Phase.PREPARE_AUDIO)

        val result = step.run(seeded.attempt, seeded.owner, seeded.config)

        assertEquals(ExecutionState.WAITING_USER, result.state)
        assertEquals("SOURCE_AUDIO_UNBOUND", result.error)
        assertTrue(sourceAudioFile(seeded.attempt.id).isFile)
        assertTrue(dao.submissions(seeded.attempt.id).isEmpty())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun acceptedRemoteIdIsNeverReplacedByMismatchedCompletedSpool() = withFixture {
        val seeded = seed(
            provider = Provider.ASSEMBLYAI,
            phase = Phase.RETRIEVE,
            submissionState = SubmissionState.ACCEPTED,
            remoteId = REMOTE_ID_A,
            rawResponse = aaiCompleted(REMOTE_ID_B, "stale response"),
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody(aaiCompleted(REMOTE_ID_A, "current response")))

        val result = step.run(seeded.attempt, seeded.owner, seeded.config)

        assertEquals(Phase.NORMALIZE, result.phase)
        assertEquals(ExecutionState.QUEUED, result.state)
        assertEquals(REMOTE_ID_A, dao.submissions(seeded.attempt.id).single().remoteId)
        assertEquals(SubmissionState.RESPONSE_SAVED, dao.submissions(seeded.attempt.id).single().state)
        assertEquals(1, server.requestCount)
        assertEquals("/v2/transcript/$REMOTE_ID_A", server.takeRequest().path)
    }

    @Test
    fun deeplyNestedRemoteSpoolIsRejectedWithoutStackOverflow() = withFixture {
        val seeded = seed(
            provider = Provider.ASSEMBLYAI,
            phase = Phase.RETRIEVE,
            submissionState = SubmissionState.ACCEPTED,
            remoteId = REMOTE_ID_A,
            rawResponse = deeplyNestedObject(5_000),
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody(aaiCompleted(REMOTE_ID_A, "current response")))

        val result = step.run(seeded.attempt, seeded.owner, seeded.config)

        assertEquals(Phase.NORMALIZE, result.phase)
        assertEquals(SubmissionState.RESPONSE_SAVED, dao.submissions(seeded.attempt.id).single().state)
        assertEquals(REMOTE_ID_A, dao.submissions(seeded.attempt.id).single().remoteId)
        assertEquals(1, server.requestCount)
        assertEquals("/v2/transcript/$REMOTE_ID_A", server.takeRequest().path)
    }

    @Test
    fun livePollResponseIdMismatchCannotAdvanceToNormalize() = withFixture {
        val seeded = seed(
            provider = Provider.ASSEMBLYAI,
            phase = Phase.RETRIEVE,
            submissionState = SubmissionState.ACCEPTED,
            remoteId = REMOTE_ID_A,
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody(aaiCompleted(REMOTE_ID_B, "wrong response")))

        val result = step.run(seeded.attempt, seeded.owner, seeded.config)

        assertEquals(Phase.RETRIEVE, result.phase)
        assertEquals(ExecutionState.WAITING_USER, result.state)
        assertEquals("INVALID_RESPONSE", result.error)
        assertEquals(SubmissionState.ACCEPTED, dao.submissions(seeded.attempt.id).single().state)
        assertEquals(REMOTE_ID_A, dao.submissions(seeded.attempt.id).single().remoteId)
        assertEquals(1, server.requestCount)
        assertEquals("/v2/transcript/$REMOTE_ID_A", server.takeRequest().path)
    }

    @Test
    fun uppercaseAssemblyResponseIdIsCanonicalizedForRemoteBinding() = withFixture {
        val seeded = seed(
            provider = Provider.ASSEMBLYAI,
            phase = Phase.RETRIEVE,
            submissionState = SubmissionState.ACCEPTED,
            remoteId = REMOTE_ID_A,
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody(aaiCompleted(REMOTE_ID_A.uppercase(), "uppercase response")))

        val result = step.run(seeded.attempt, seeded.owner, seeded.config)

        assertEquals(Phase.NORMALIZE, result.phase)
        assertEquals(SubmissionState.RESPONSE_SAVED, dao.submissions(seeded.attempt.id).single().state)
        assertEquals(REMOTE_ID_A, dao.submissions(seeded.attempt.id).single().remoteId)
        assertEquals(1, server.requestCount)
        assertEquals("/v2/transcript/$REMOTE_ID_A", server.takeRequest().path)
    }

    @Test
    fun spooledRemoteFailureDoesNotLoopThroughNormalize() = withFixture {
        val seeded = seed(
            provider = Provider.ASSEMBLYAI,
            phase = Phase.RETRIEVE,
            submissionState = SubmissionState.ACCEPTED,
            remoteId = REMOTE_ID_A,
            rawResponse = aaiError(REMOTE_ID_A),
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody(aaiCompleted(REMOTE_ID_A, "recovered response")))

        val replay = step.run(seeded.attempt, seeded.owner, seeded.config)

        assertEquals(Phase.RETRIEVE, replay.phase)
        assertEquals(ExecutionState.WAITING_USER, replay.state)
        assertEquals("REMOTE_FAILED", replay.error)
        assertFalse(replay.phase == Phase.NORMALIZE)
        assertEquals(0, server.requestCount)

        val polled = step.run(replay, seeded.owner, seeded.config)

        assertEquals(Phase.NORMALIZE, polled.phase)
        assertEquals(SubmissionState.RESPONSE_SAVED, dao.submissions(seeded.attempt.id).single().state)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun pollTransportRetriesBoundedWithoutDroppingAcceptedRemote() = withFixture {
        val seeded = seed(
            provider = Provider.ASSEMBLYAI,
            phase = Phase.RETRIEVE,
            submissionState = SubmissionState.ACCEPTED,
            remoteId = REMOTE_ID_A,
        )
        repeat(4) { server.enqueue(MockResponse().setResponseCode(500)) }

        var result = step.run(seeded.attempt, seeded.owner, seeded.config)
        repeat(3) {
            assertEquals(ExecutionState.WAITING_NETWORK, result.state)
            result = step.run(result, seeded.owner, seeded.config)
        }

        assertEquals(ExecutionState.WAITING_USER, result.state)
        assertEquals("SERVER", result.error)
        assertEquals(SubmissionState.ACCEPTED, dao.submissions(seeded.attempt.id).single().state)
        assertEquals(REMOTE_ID_A, dao.submissions(seeded.attempt.id).single().remoteId)
        assertEquals(4, server.requestCount)
    }

    @Test
    fun assemblyUploadTransportFailureRemainsBoundedPreparedRetry() = withFixture {
        val seeded = seed(provider = Provider.ASSEMBLYAI)
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))

        val result = step.run(seeded.attempt, seeded.owner, seeded.config)

        assertEquals(ExecutionState.WAITING_NETWORK, result.state)
        assertEquals(Phase.SUBMIT, result.phase)
        assertEquals(SubmissionState.PREPARED, dao.submissions(seeded.attempt.id).single().state)
        assertEquals(1, server.requestCount)
        assertEquals("/v2/upload", server.takeRequest().path)
    }

    @Test
    fun malformedAssemblyUploadReceiptIsRejectedBeforePaidSubmit() = withFixture {
        val seeded = seed(provider = Provider.ASSEMBLYAI)
        server.enqueue(MockResponse().setResponseCode(200).setBody("{malformed"))

        val result = step.run(seeded.attempt, seeded.owner, seeded.config)

        assertEquals(ExecutionState.WAITING_USER, result.state)
        assertEquals("INVALID_RESPONSE", result.error)
        val submission = dao.submissions(seeded.attempt.id).single()
        assertEquals(SubmissionState.REJECTED, submission.state)
        assertEquals("INVALID_RESPONSE", submission.rejectionCode)
        assertFalse(File(requireNotNull(submission.rawResponsePath)).exists())
        assertEquals(1, server.requestCount)
        assertEquals("/v2/upload", server.takeRequest().path)
    }

    @Test
    fun malformedPaidAssemblyTranscriptResponseStaysUncertain() = withFixture {
        val seeded = seed(provider = Provider.ASSEMBLYAI)
        server.enqueue(MockResponse().setResponseCode(200).setBody(AAI_UPLOAD_RESPONSE))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{malformed"))

        val result = step.run(seeded.attempt, seeded.owner, seeded.config)

        assertEquals(ExecutionState.SUBMISSION_UNCERTAIN, result.state)
        assertEquals("REMOTE_RECEIPT_MISSING", result.error)
        val submission = dao.submissions(seeded.attempt.id).single()
        assertEquals(SubmissionState.UNCERTAIN, submission.state)
        assertEquals(null, submission.rejectionCode)
        assertTrue(File(requireNotNull(submission.rawResponsePath)).isFile)
        assertEquals(2, server.requestCount)
        assertEquals("/v2/upload", server.takeRequest().path)
        assertEquals("/v2/transcript", server.takeRequest().path)
    }

    @Test
    fun submitRateLimitExhaustionKeepsPreparedBudgetReservation() = withFixture {
        val seeded = seed()
        repeat(4) { server.enqueue(MockResponse().setResponseCode(429)) }

        var result = step.run(seeded.attempt, seeded.owner, seeded.config)
        repeat(3) {
            assertEquals(ExecutionState.WAITING_RATE_LIMIT, result.state)
            result = step.run(result, seeded.owner, seeded.config)
        }

        assertEquals(ExecutionState.WAITING_USER, result.state)
        assertEquals("RATE_LIMIT", result.error)
        assertEquals(SubmissionState.PREPARED, dao.submissions(seeded.attempt.id).single().state)
        assertEquals(4, server.requestCount)
    }

    @Test
    fun completedSiblingAndMalformedSiblingPersistAsPartialWithoutResubmit() = withFixture {
        val seeded = seed(durationMs = MULTI_CHUNK_DURATION_MS)
        server.enqueue(MockResponse().setResponseCode(200).setBody(GROQ_RESPONSE))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{malformed"))

        var result = step.run(seeded.attempt, seeded.owner, seeded.config)
        assertEquals(Phase.SUBMIT, result.phase)
        result = step.run(result, seeded.owner, seeded.config)
        assertEquals(Phase.NORMALIZE, result.phase)
        result = step.run(result, seeded.owner, seeded.config)
        assertEquals(Phase.PERSIST, result.phase)
        val document = json.decodeFromString<TranscriptDocument>(readNormalized(seeded.attempt.id))
        result = step.run(result, seeded.owner, seeded.config)

        assertEquals(ExecutionState.FINISHED, result.state)
        assertEquals(app.sourcescribe.core.Outcome.PARTIAL_SUCCESS, result.outcome)
        assertEquals(2, server.requestCount)
        val artifact = requireNotNull(dao.artifact(seeded.artifactId))
        assertFalse(artifact.complete == true)
        assertTrue(artifact.warningCount > 0)
        assertEquals(listOf(1), document.scope.missingChunks)
        assertTrue(document.warnings.any { it.contains("CHUNK_1_RESPONSE_INVALID_RESPONSE") })
        assertEquals("fixture transcript", document.segments.single().text)
    }

    @Test
    fun largeIgnoredResponsesNormalizeAllChunksWithoutRawRetention() = withFixture {
        val seeded = seed(durationMs = THREE_CHUNK_DURATION_MS)
        listOf("large first", "large second", "large third").forEach { text ->
            server.enqueue(MockResponse().setResponseCode(200).setBody(largeGroqResponse(text)))
        }

        var result = step.run(seeded.attempt, seeded.owner, seeded.config)
        repeat(2) {
            result = step.run(result, seeded.owner, seeded.config)
        }
        assertEquals(Phase.NORMALIZE, result.phase)

        result = step.run(result, seeded.owner, seeded.config)
        assertEquals(Phase.PERSIST, result.phase)
        val document = json.decodeFromString<TranscriptDocument>(readNormalized(seeded.attempt.id))
        assertEquals(emptyList<Int>(), document.scope.missingChunks)
        assertEquals(true, document.scope.technicallyComplete)
        assertEquals(
            listOf("large first", "large second", "large third"),
            document.segments.map { it.text },
        )

        result = step.run(result, seeded.owner, seeded.config)
        assertEquals(ExecutionState.FINISHED, result.state)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun retainedRawBundleKeepsOriginalChunkIndexWhenChunkZeroIsMissing() = withFixture {
        val seeded = seed(
            phase = Phase.NORMALIZE,
            durationMs = THREE_CHUNK_DURATION_MS,
            retainRaw = true,
            submissionState = SubmissionState.RESPONSE_SAVED,
            rawResponse = GROQ_RESPONSE,
            submissionChunkIndexes = listOf(1, 2),
        )

        val result = step.run(seeded.attempt, seeded.owner, seeded.config)

        assertEquals(Phase.PERSIST, result.phase)
        val raw = rawProviderFile(seeded.attempt.id)
        val entries = LinkedHashMap<String, ByteArray>()
        ZipInputStream(raw.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes()
                zip.closeEntry()
            }
        }
        assertEquals(listOf("chunk-1.json", "chunk-2.json"), entries.keys.toList())
        assertEquals(listOf(0), json.decodeFromString<TranscriptDocument>(readNormalized(seeded.attempt.id)).scope.missingChunks)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun persistRejectsNormalizedArtifactWithForgedSource() = withFixture {
        val seeded = seed(phase = Phase.PERSIST)
        writeNormalized(seeded.attempt.id, validNormalizedDocument(seeded).copy(
            source = seeded.source.copy(id = "local:forged-source"),
        ))

        val result = step.run(seeded.attempt, seeded.owner, seeded.config)

        assertEquals(ExecutionState.WAITING_USER, result.state)
        assertEquals("ARTIFACT_BINDING_MISMATCH", result.error)
        assertEquals(null, dao.artifact(seeded.artifactId))
    }

    @Test
    fun persistRejectsNormalizedArtifactWithForgedProviderMetadata() = withFixture {
        val seeded = seed(phase = Phase.PERSIST)
        writeNormalized(seeded.attempt.id, validNormalizedDocument(seeded).copy(
            provenance = validNormalizedDocument(seeded).provenance.copy(
                provider = Provider.ASSEMBLYAI,
                requestedModel = AssemblyAiAdapter.MODEL_U2,
            ),
        ))

        val result = step.run(seeded.attempt, seeded.owner, seeded.config)

        assertEquals(ExecutionState.WAITING_USER, result.state)
        assertEquals("ARTIFACT_BINDING_MISMATCH", result.error)
        assertEquals(null, dao.artifact(seeded.artifactId))
    }

    @Test
    fun actualChunkIntervalGapProducesPartialArtifactWarning() = withFixture {
        val seeded = seed(
            durationMs = MULTI_CHUNK_DURATION_MS,
            actualDurationAdjustments = mapOf(0 to -100L),
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody(GROQ_SHORT_RESPONSE))
        server.enqueue(MockResponse().setResponseCode(200).setBody(GROQ_RESPONSE))

        var result = step.run(seeded.attempt, seeded.owner, seeded.config)
        result = step.run(result, seeded.owner, seeded.config)
        assertEquals(Phase.NORMALIZE, result.phase)
        result = step.run(result, seeded.owner, seeded.config)
        assertEquals(Phase.PERSIST, result.phase)
        val document = json.decodeFromString<TranscriptDocument>(readNormalized(seeded.attempt.id))

        assertEquals(emptyList<Int>(), document.scope.missingChunks)
        assertFalse(document.scope.technicallyComplete == true)
        assertTrue(document.warnings.contains("AUDIO_INTERVAL_GAP_OR_OVERLAP"))
        assertEquals(app.sourcescribe.core.Outcome.PARTIAL_SUCCESS, step.run(result, seeded.owner, seeded.config).outcome)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun completedChunkSurvivesBudgetAbortAsPartialWithoutResubmit() = withFixture {
        val seeded = seed(
            durationMs = MULTI_CHUNK_DURATION_MS,
            maxCostMicrousd = 6_700L,
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody(GROQ_RESPONSE))

        var result = step.run(seeded.attempt, seeded.owner, seeded.config)
        assertEquals(Phase.SUBMIT, result.phase)
        result = step.run(result, seeded.owner, seeded.config)
        assertEquals(Phase.NORMALIZE, result.phase)
        assertEquals("BUDGET_EXCEEDED", result.error)
        result = step.run(result, seeded.owner, seeded.config)
        assertEquals(Phase.PERSIST, result.phase)
        val document = json.decodeFromString<TranscriptDocument>(readNormalized(seeded.attempt.id))
        result = step.run(result, seeded.owner, seeded.config)

        assertEquals(ExecutionState.FINISHED, result.state)
        assertEquals(app.sourcescribe.core.Outcome.PARTIAL_SUCCESS, result.outcome)
        assertEquals(1, server.requestCount)
        assertEquals(listOf(1), document.scope.missingChunks)
    }

    @Test
    fun existingJobSpendBlocksSubmissionAtBudgetUpperLimit() = withFixture {
        val seeded = seed(maxCostMicrousd = 112L, priorSubmissionCostMicrousd = 1L)

        val result = step.run(seeded.attempt, seeded.owner, seeded.config)

        assertEquals(ExecutionState.WAITING_USER, result.state)
        assertEquals("BUDGET_EXCEEDED", result.error)
        assertTrue(dao.submissions(seeded.attempt.id).isEmpty())
        assertEquals(0, server.requestCount)
        assertEquals(1, dao.submissionsForJob(seeded.attempt.jobId).size)
    }

    private fun validNormalizedDocument(seeded: Seeded): TranscriptDocument {
        val duration = seeded.source.durationMs ?: FIXTURE_DURATION_MS
        return TranscriptDocument(
            artifactId = seeded.artifactId,
            source = seeded.source,
            acquisition = seeded.config,
            provenance = Provenance(
                origin = Origin.PROVIDER,
                provider = seeded.config.provider,
                requestedModel = seeded.config.model,
                sourceAudioTrack = null,
            ),
            language = "en",
            scope = TranscriptScope(
                requestedDurationMs = duration,
                processedIntervals = listOf(Interval(0, duration)),
                technicallyComplete = true,
            ),
            segments = listOf(Segment("fixture transcript", 0, duration)),
            createdAt = seeded.attempt.createdAt,
        )
    }

    private suspend fun RecoveryFixture.assertBindingMismatch(seeded: Seeded, result: AttemptRow) {
        assertEquals(ExecutionState.WAITING_USER, result.state)
        assertEquals("SUBMISSION_BINDING_MISMATCH", result.error)
        assertEquals(SubmissionState.SENDING, dao.submissions(seeded.attempt.id).single().state)
        assertEquals(0, server.requestCount)
    }

    private fun withFixture(block: suspend RecoveryFixture.() -> Unit) {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val fixture = RecoveryFixture(base)
        try {
            runBlocking { fixture.block() }
        } finally {
            fixture.close()
        }
    }

    private class RecoveryFixture(base: Context) {
        val root = File(base.cacheDir, "stt-recovery-${UUID.randomUUID()}").also { check(it.mkdirs()) }
        private val context = IsolatedContext(base, root)
        val database = Room.inMemoryDatabaseBuilder(context, SourceScribeDatabase::class.java).build()
        val dao = database.records()
        val credentials = CredentialStore(context)
        private val runtime = NativeRuntime(context)
        private val extractor = ExtractorEngine(runtime)
        private val engines = EngineUpdateManager(context, runtime)
        private val artifacts = ArtifactFiles(File(context.filesDir, "artifacts"))
        val server = MockWebServer()
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
        val providerHttp: ProviderHttp
        val step: SttStep

        init {
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
            step = SttStep(
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
        }

        suspend fun seed(
            provider: Provider = Provider.GROQ,
            phase: Phase = Phase.SUBMIT,
            submissionState: SubmissionState? = null,
            submissionChunkIndexes: List<Int>? = null,
            rawResponse: String? = null,
            remoteId: String? = null,
            sourceKind: SourceKind = SourceKind.LOCAL_AUDIO,
            inputHash: String? = null,
            persistedConfigHash: String? = null,
            persistedRawPath: String? = null,
            maxCostMicrousd: Long? = 1_000_000L,
            priorSubmissionCostMicrousd: Long? = null,
            durationMs: Long = FIXTURE_DURATION_MS,
            leaseOffsetMs: Long = LEASE_MS,
            actualDurationAdjustments: Map<Int, Long> = emptyMap(),
            retainRaw: Boolean = false,
        ): Seeded {
            val audioBytes = ByteArray(FIXTURE_AUDIO_BYTES) { index -> (index and 0xff).toByte() }
            val audioHash = sha256(audioBytes)
            val source = when (sourceKind) {
                SourceKind.LOCAL_AUDIO -> Source(
                    id = "local:$audioHash",
                    kind = SourceKind.LOCAL_AUDIO,
                    contentHash = audioHash,
                    fileName = "fixture.mp3",
                    mimeType = "audio/mpeg",
                    fileBytes = audioBytes.size.toLong(),
                    durationMs = durationMs,
                )
                SourceKind.YOUTUBE -> Source(
                    id = "youtube:fixture-video",
                    kind = SourceKind.YOUTUBE,
                    canonicalUrl = "https://www.youtube.com/watch?v=fixture-video",
                    videoId = "fixture-video",
                    title = "Fixture video",
                    durationMs = durationMs,
                )
            }
            val audio = if (sourceKind == SourceKind.YOUTUBE) AudioTrack(
                id = "fixture-audio",
                sourceVideoId = requireNotNull(source.videoId),
                language = null,
                name = "Fixture audio",
                isOriginal = true,
                evidence = "fixture",
            ) else null
            val credentialId = credentials.save(provider, Region.US, "fixture-test-key-${UUID.randomUUID()}")
            val model = when (provider) {
                Provider.GROQ -> GroqAdapter.MODEL_TURBO
                Provider.ASSEMBLYAI -> AssemblyAiAdapter.MODEL_U2
                Provider.OPENAI -> error("fixture does not seed OpenAI")
            }
            val config = JobConfig(
                mode = AcquisitionMode.STT_ONLY,
                provider = provider,
                model = model,
                region = Region.US,
                credentialId = credentialId,
                uploadApproved = true,
                maxAudioSeconds = 3_600,
                maxCostMicrousd = maxCostMicrousd,
                audioRetention = AudioRetention.TEMPORARY,
                retainRaw = retainRaw,
            )
            val artifactId = UUID.randomUUID().toString()
            val jobId = UUID.randomUUID().toString()
            val attemptId = UUID.randomUUID().toString()
            val owner = "fixture-owner-${UUID.randomUUID()}"
            val now = System.currentTimeMillis()
            val checkpoint = FixtureCheckpoint(
                artifactId = artifactId,
                artifactCreatedAt = now,
                source = source,
                audio = audio,
                durationMs = durationMs,
                chunkCount = SttStep.chunkPlan(durationMs).size,
                nextChunkIndex = SttStep.chunkPlan(durationMs).size,
                prepared = SttStep.chunkPlan(durationMs).map { window ->
                    FixtureChunk(
                        index = window.index,
                        offsetMs = window.offsetMs,
                        durationMs = window.durationMs + (actualDurationAdjustments[window.index] ?: 0L),
                        bytes = audioBytes.size.toLong(),
                        sha256 = audioHash,
                        mimeType = AUDIO_MIME_TYPE,
                    )
                },
            )
            val attemptDirectory = context.noBackupFilesDir
                .resolve(ATTEMPTS_DIRECTORY)
                .resolve(attemptId)
                .also { check(it.mkdirs()) }
            checkpoint.prepared.forEach { chunk ->
                attemptDirectory.resolve("audio-${chunk.index}.mp3").writeBytes(audioBytes)
            }
            if (audio != null) attemptDirectory.resolve("source.audio").writeBytes(audioBytes)
            val attempt = AttemptRow(
                id = attemptId,
                jobId = jobId,
                branch = Branch.STT,
                number = 1,
                createdAt = now,
                state = ExecutionState.RUNNING,
                phase = phase,
                checkpoint = json.encodeToString(checkpoint),
                leaseOwner = owner,
                leaseUntil = now + leaseOffsetMs,
            )
            dao.createJob(
                source = SourceRow(source.id, json.encodeToString(source), "Fixture audio"),
                job = JobRow(jobId, source.id, json.encodeToString(config), now),
                attempts = listOf(attempt),
            )
            if (priorSubmissionCostMicrousd != null) {
                val priorAttemptId = UUID.randomUUID().toString()
                dao.insertAttempt(
                    AttemptRow(
                        id = priorAttemptId,
                        jobId = jobId,
                        branch = Branch.CAPTIONS,
                        number = 1,
                        createdAt = now,
                        state = ExecutionState.FINISHED,
                        phase = Phase.FETCH_CAPTIONS,
                    ),
                )
                dao.insertSubmission(
                    SubmissionRow(
                        id = UUID.randomUUID().toString(),
                        attemptId = priorAttemptId,
                        chunkIndex = 0,
                        provider = Provider.GROQ.name,
                        credentialId = credentialId,
                        region = Region.US.name,
                        inputHash = audioHash,
                        configHash = sha256(
                            json.encodeToString(config).toByteArray(StandardCharsets.UTF_8),
                        ),
                        state = SubmissionState.RESPONSE_SAVED,
                        createdAt = now,
                        estimatedMicrousd = priorSubmissionCostMicrousd,
                    ),
                )
            }
            val submissionChunks = submissionChunkIndexes
                ?: if (submissionState != null) listOf(0) else emptyList()
            val responsesDirectory = attemptDirectory.resolve(RESPONSES_DIRECTORY)
                .also { check(it.mkdirs()) }
            if (submissionState != null) {
                for (submissionChunkIndex in submissionChunks) {
                    val expectedRawPath = responsesDirectory.resolve("${submissionId(attemptId, submissionChunkIndex)}.json")
                    val submissionId = expectedRawPath.name.removeSuffix(".json")
                    val rawPath = if (submissionChunks.size == 1) {
                        persistedRawPath ?: expectedRawPath.absolutePath
                    } else {
                        expectedRawPath.absolutePath
                    }
                    dao.insertSubmission(
                        SubmissionRow(
                            id = submissionId,
                            attemptId = attemptId,
                            chunkIndex = submissionChunkIndex,
                            provider = provider.name,
                            credentialId = credentialId,
                            region = Region.US.name,
                            inputHash = inputHash ?: checkpoint.prepared.first { it.index == submissionChunkIndex }.sha256,
                            configHash = persistedConfigHash ?: sha256(json.encodeToString(config).toByteArray(StandardCharsets.UTF_8)),
                            state = submissionState,
                            createdAt = now,
                            estimatedMicrousd = 112,
                            remoteId = remoteId,
                            rawResponsePath = rawPath,
                        ),
                    )
                    if (rawResponse != null) File(rawPath).apply {
                        parentFile?.mkdirs()
                        writeText(rawResponse, StandardCharsets.UTF_8)
                    }
                }
            }
            return Seeded(attempt, config, owner, artifactId, source)
        }

        fun readNormalized(attemptId: String): String = context.noBackupFilesDir
            .resolve(ATTEMPTS_DIRECTORY)
            .resolve(attemptId)
            .resolve(NORMALIZED_NAME)
            .readText(StandardCharsets.UTF_8)

        fun writeNormalized(attemptId: String, document: TranscriptDocument) {
            context.noBackupFilesDir
                .resolve(ATTEMPTS_DIRECTORY)
                .resolve(attemptId)
                .resolve(NORMALIZED_NAME)
                .writeText(json.encodeToString(document), StandardCharsets.UTF_8)
        }

        fun rawProviderFile(attemptId: String): File = context.noBackupFilesDir
            .resolve(ATTEMPTS_DIRECTORY)
            .resolve(attemptId)
            .resolve(RAW_PROVIDER_NAME)

        fun sourceAudioFile(attemptId: String): File = context.noBackupFilesDir
            .resolve(ATTEMPTS_DIRECTORY)
            .resolve(attemptId)
            .resolve("source.audio")

        fun close() {
            database.close()
            try {
                server.shutdown()
            } catch (_: IOException) {
                // Fixture cleanup must not touch files outside its UUID root.
            }
            root.deleteRecursively()
        }

        private fun submissionId(attemptId: String, chunkIndex: Int): String =
            UUID.nameUUIDFromBytes("$attemptId:$chunkIndex".toByteArray()).toString()
    }

    private class IsolatedContext(base: Context, root: File) : ContextWrapper(base) {
        private val files = File(root, "files").also { check(it.mkdirs()) }
        private val noBackup = File(root, "no-backup").also { check(it.mkdirs()) }

        override fun getApplicationContext(): Context = this

        override fun getFilesDir(): File = files

        override fun getNoBackupFilesDir(): File = noBackup
    }

    private data class Seeded(
        val attempt: AttemptRow,
        val config: JobConfig,
        val owner: String,
        val artifactId: String,
        val source: Source,
    )

    @Serializable
    private data class FixtureCheckpoint(
        val artifactId: String,
        val artifactCreatedAt: Long,
        val source: Source,
        val audio: AudioTrack?,
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
        private const val FIXTURE_AUDIO_BYTES = 4 * 1024
        private const val FIXTURE_DURATION_MS = 1_000L
        private const val AUDIO_MIME_TYPE = "audio/mpeg"
        private const val ATTEMPTS_DIRECTORY = "attempts"
        private const val RESPONSES_DIRECTORY = "responses"
        private const val NORMALIZED_NAME = "normalized.json"
        private const val RAW_PROVIDER_NAME = "raw.provider.zip"
        private const val LEASE_MS = 9 * 60_000L
        private const val MULTI_CHUNK_DURATION_MS = 601_000L
        private const val THREE_CHUNK_DURATION_MS = 1_201_000L
        private const val LARGE_RESPONSE_BYTES = 6 * 1024 * 1024
        private const val SHA256_HEX_LENGTH = 64
        private const val REMOTE_ID_A = "0072a82b-aa22-4962-add2-6121c36c17c6"
        private const val REMOTE_ID_B = "1072a82b-aa22-4962-add2-6121c36c17c6"
        private const val AAI_UPLOAD_RESPONSE = "{\"upload_url\":\"https://cdn.assemblyai.com/upload/fixture\"}"
        private const val GROQ_RESPONSE = """
            {"text":"fixture transcript","language":"en","segments":[{"id":0,"start":0.0,"end":1.0,"text":"fixture transcript"}]}
        """
        private const val GROQ_SHORT_RESPONSE = """
            {"text":"short fixture transcript","language":"en","segments":[{"id":0,"start":0.0,"end":0.8,"text":"short fixture transcript"}]}
        """
        private val json = Json {
            encodeDefaults = true
            explicitNulls = true
            ignoreUnknownKeys = true
        }

        private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(java.util.Locale.ROOT, it) }

        private fun aaiCompleted(id: String, text: String): String = """
            {"id":"$id","status":"completed","text":"$text","language_code":"en","speech_model_used":"universal-2","words":[{"text":"$text","start":0,"end":1000}],"utterances":[{"text":"$text","start":0,"end":1000}]}
        """.trimIndent()

        private fun aaiError(id: String): String =
            "{\"id\":\"$id\",\"status\":\"error\",\"error\":\"fixture provider detail\"}"

        private fun aaiCompletedWithoutId(text: String): String =
            "{\"status\":\"completed\",\"text\":\"$text\",\"language_code\":\"en\"}"

        private fun largeGroqResponse(text: String): String {
            val prefix = "{\"text\":\"$text\",\"language\":\"en\",\"segments\":[{\"id\":0,\"start\":0.0,\"end\":1.0,\"text\":\"$text\"}],\"ignored\":"
            val suffix = "\"}"
            val padding = LARGE_RESPONSE_BYTES - prefix.length - 1 - suffix.length
            check(padding > 0)
            return prefix + "\"" + "x".repeat(padding) + suffix
        }

        private fun deeplyNestedObject(depth: Int): String = buildString(depth * 10 + 2) {
            repeat(depth) { append("{\"nested\":") }
            append("{}")
            repeat(depth) { append('}') }
        }
    }
}
