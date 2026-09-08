package app.sourcescribe.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Data
import androidx.work.WorkManager
import androidx.work.impl.WorkManagerImpl
import androidx.work.testing.WorkManagerTestInitHelper
import app.sourcescribe.core.AcquisitionMode
import app.sourcescribe.core.ArtifactFiles
import app.sourcescribe.core.AudioRetention
import app.sourcescribe.core.Branch
import app.sourcescribe.core.CaptionTrack
import app.sourcescribe.core.ExportFormat
import app.sourcescribe.core.ExportState
import app.sourcescribe.core.ExecutionState
import app.sourcescribe.core.Generation
import app.sourcescribe.core.JobConfig
import app.sourcescribe.core.Origin
import app.sourcescribe.core.Phase
import app.sourcescribe.core.Provenance
import app.sourcescribe.core.Provider
import app.sourcescribe.core.ProviderHttp
import app.sourcescribe.core.Region
import app.sourcescribe.core.Segment
import app.sourcescribe.core.Source
import app.sourcescribe.core.SourceKind
import app.sourcescribe.core.TranscriptDocument
import app.sourcescribe.core.TranscriptScope
import app.sourcescribe.core.Translation
import app.sourcescribe.extractor.EngineUpdateManager
import app.sourcescribe.extractor.ExtractorEngine
import app.sourcescribe.extractor.NativeRuntime
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.Files
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** End-to-end Room/Coordinator fixtures; provider and extractor traffic is forbidden. */
@RunWith(AndroidJUnit4::class)
class AppPipelineTest {
    @Test
    fun recoverPersistsCaptionOnceAndPermissionExportDoesNotResubmit() = withFixture {
        val seeded = seedCaption(state = ExecutionState.RUNNING, leaseOwner = "stale-owner")

        assertFalse(coordinator.run(seeded.caption.id))

        val attempt = requireNotNull(dao.attempt(seeded.caption.id))
        val artifact = requireNotNull(dao.artifact(seeded.artifactId))
        assertEquals(ExecutionState.FINISHED, attempt.state)
        assertEquals(Phase.PERSIST, attempt.phase)
        assertEquals(Branch.CAPTIONS, artifact.branch)
        assertEquals(seeded.document, artifacts.read(seeded.artifactId))
        assertTrue(artifacts.canonicalFile(seeded.artifactId).isFile)

        coordinator.runExports(seeded.caption.id)
        coordinator.runExports(seeded.caption.id)

        assertEquals(1, dao.exports(seeded.artifactId).size)
        assertEquals(ExportState.PERMISSION_REQUIRED, dao.exports(seeded.artifactId).single().state)
        assertEquals(1, dao.artifacts(seeded.job.id).size)
        assertEquals(0, providerRequests())

        coordinator.recover()
        assertFalse(coordinator.run(seeded.caption.id))
        assertEquals(1, dao.artifacts(seeded.job.id).size)
        assertEquals(0, providerRequests())
    }

    @Test
    fun persistedAcquisitionAndExportWorkContainOnlyAttemptId() = withFixture {
        val canaryUri = "content://fixture/CANARY_URI_MUST_NOT_REACH_WORK_DATA"
        val canaryApiKey = "CANARY_API_KEY_MUST_NOT_REACH_WORK_DATA"
        val canaryTranscript = "CANARY_TRANSCRIPT_MUST_NOT_REACH_WORK_DATA"
        val config = captionConfig().copy(
            credentialId = canaryApiKey,
            exportTreeUri = canaryUri,
        )
        val seeded = seedCaption(config = config)
        writeAttemptFile(
            seeded.caption.id,
            "normalized.json",
            json.encodeToString(
                seeded.document.copy(segments = listOf(Segment(canaryTranscript, 0, 1_000))),
            ),
        )
        fun assertAttemptOnly(input: Data) {
            assertEquals(mapOf("attemptId" to seeded.caption.id), input.keyValueMap)
            val persisted = input.keyValueMap.toString()
            assertFalse(persisted.contains(canaryUri))
            assertFalse(persisted.contains(canaryApiKey))
            assertFalse(persisted.contains(canaryTranscript))
        }

        coordinator.scheduleNext(seeded.caption.id)
        assertAttemptOnly(persistedWorkInput("attempt:${seeded.caption.id}"))

        assertFalse(coordinator.run(seeded.caption.id))
        assertAttemptOnly(persistedWorkInput("exports:${seeded.caption.id}"))
        assertEquals(0, providerRequests())
    }

    @Test
    fun bothKeepsCaptionArtifactWhileSttWaitsForMissingCredential() = withFixture {
        val seeded = seedBoth()

        assertFalse(coordinator.run(seeded.caption.id))
        val captionArtifact = requireNotNull(dao.artifact(seeded.captionArtifactId))
        assertEquals(Branch.CAPTIONS, captionArtifact.branch)
        assertEquals(seeded.captionDocument, artifacts.read(seeded.captionArtifactId))

        assertFalse(coordinator.run(seeded.stt.id))
        val stt = requireNotNull(dao.attempt(seeded.stt.id))
        val job = requireNotNull(dao.job(seeded.job.id))
        assertEquals(ExecutionState.WAITING_USER, stt.state)
        assertEquals("CREDENTIAL_REQUIRED", stt.error)
        assertEquals(ExecutionState.WAITING_USER, job.state)
        assertTrue(dao.submissions(seeded.stt.id).isEmpty())
        assertEquals(1, dao.artifacts(seeded.job.id).size)
        assertEquals(Branch.CAPTIONS, dao.artifacts(seeded.job.id).single().branch)

        coordinator.recover()
        assertFalse(coordinator.run(seeded.caption.id))
        assertFalse(coordinator.run(seeded.stt.id))
        assertEquals(1, dao.artifacts(seeded.job.id).size)
        assertEquals(0, providerRequests())
    }

    @Test
    fun cancelStopsQueuedAttemptAndResumeContinuesPersistedAttempt() {
        withFixture {
            val seeded = seedCaption()

            coordinator.cancel(seeded.job.id)

            assertEquals(ExecutionState.CANCELLED, requireNotNull(dao.job(seeded.job.id)).state)
            assertTrue(requireNotNull(dao.job(seeded.job.id)).cancelRequested)
            assertEquals(ExecutionState.CANCELLED, requireNotNull(dao.attempt(seeded.caption.id)).state)
            assertFalse(coordinator.run(seeded.caption.id))
            assertTrue(dao.artifacts(seeded.job.id).isEmpty())
            assertEquals(0, providerRequests())
        }

        withFixture {
            val seeded = seedCaption(state = ExecutionState.WAITING_USER, error = "FIXTURE_PAUSED")

            coordinator.resume(seeded.job.id)

            assertEquals(ExecutionState.QUEUED, requireNotNull(dao.attempt(seeded.caption.id)).state)
            assertFalse(coordinator.run(seeded.caption.id))
            assertEquals(ExecutionState.FINISHED, requireNotNull(dao.attempt(seeded.caption.id)).state)
            assertEquals(1, dao.artifacts(seeded.job.id).size)
            assertEquals(seeded.document, artifacts.read(seeded.artifactId))
            assertEquals(0, providerRequests())
        }
    }

    @Test
    fun resumePreparesAuthenticationAndAccessDeniedButPreservesOtherSubmissionStates() = withFixture {
        val seeded = seedCaption(state = ExecutionState.WAITING_USER, error = "CREDENTIAL_REQUIRED")
        fun submission(
            chunkIndex: Int,
            state: SubmissionState,
            rejectionCode: String? = null,
            rawResponsePath: String? = null,
        ) = SubmissionRow(
            id = "fixture-submission-$chunkIndex",
            attemptId = seeded.caption.id,
            chunkIndex = chunkIndex,
            provider = Provider.GROQ.name,
            credentialId = "fixture-credential",
            region = Region.US.name,
            inputHash = "fixture-input-$chunkIndex",
            configHash = "fixture-config-hash",
            state = state,
            createdAt = seeded.caption.createdAt + chunkIndex,
            estimatedMicrousd = 1,
            rawResponsePath = rawResponsePath,
            rejectionCode = rejectionCode,
        )
        dao.insertSubmission(submission(0, SubmissionState.REJECTED, rejectionCode = "AUTHENTICATION"))
        dao.insertSubmission(submission(1, SubmissionState.REJECTED, rejectionCode = "ACCESS_DENIED"))
        dao.insertSubmission(submission(2, SubmissionState.REJECTED, rejectionCode = "INVALID_INPUT"))
        dao.insertSubmission(submission(3, SubmissionState.RESPONSE_SAVED, rawResponsePath = "fixture-response.json"))

        coordinator.resume(seeded.job.id)

        val submissions = dao.submissions(seeded.caption.id).sortedBy { it.chunkIndex }
        assertEquals(SubmissionState.PREPARED, submissions[0].state)
        assertNull(submissions[0].rejectionCode)
        assertEquals(SubmissionState.PREPARED, submissions[1].state)
        assertNull(submissions[1].rejectionCode)
        assertEquals(SubmissionState.REJECTED, submissions[2].state)
        assertEquals("INVALID_INPUT", submissions[2].rejectionCode)
        assertEquals(SubmissionState.RESPONSE_SAVED, submissions[3].state)
        assertNull(submissions[3].rejectionCode)
        assertEquals("fixture-response.json", submissions[3].rawResponsePath)
        assertEquals(ExecutionState.QUEUED, requireNotNull(dao.attempt(seeded.caption.id)).state)
        assertNull(requireNotNull(dao.attempt(seeded.caption.id)).error)
        assertEquals(0, providerRequests())
    }

    @Test
    fun resumeRefusesUncertainSubmissionWithoutChangingSttAttempt() = withFixture {
        val seeded = seedLocalJob(state = ExecutionState.WAITING_USER, phase = Phase.SUBMIT)
        val waitingAttempt = seeded.attempt.copy(
            error = "SUBMISSION_UNCERTAIN",
        )
        dao.updateAttempt(waitingAttempt)
        val rejected = SubmissionRow(
            id = "fixture-rejected-submission",
            attemptId = waitingAttempt.id,
            chunkIndex = 0,
            provider = Provider.GROQ.name,
            credentialId = "fixture-credential",
            region = Region.US.name,
            inputHash = "fixture-rejected-input",
            configHash = "fixture-config-hash",
            state = SubmissionState.REJECTED,
            createdAt = waitingAttempt.createdAt,
            estimatedMicrousd = 1,
            rejectionCode = "AUTHENTICATION",
        )
        val uncertain = rejected.copy(
            id = "fixture-uncertain-submission",
            chunkIndex = 1,
            inputHash = "fixture-uncertain-input",
            state = SubmissionState.UNCERTAIN,
            rejectionCode = null,
        )
        dao.insertSubmission(rejected)
        dao.insertSubmission(uncertain)

        val failure = try {
            coordinator.resume(seeded.job.id)
            null
        } catch (error: JobActionException) {
            error
        }

        assertEquals("SUBMISSION_UNCERTAIN", requireNotNull(failure).code)
        assertEquals(listOf(rejected, uncertain), dao.submissions(waitingAttempt.id))
        assertEquals(waitingAttempt, dao.attempt(waitingAttempt.id))
        assertEquals(seeded.job, dao.job(seeded.job.id))
        assertEquals(0, providerRequests())
    }

    @Test
    fun resumeCaptionSiblingDoesNotResetUncertainSttSubmission() = withFixture {
        val seeded = seedBoth()
        val waitingCaption = seeded.caption.copy(
            state = ExecutionState.WAITING_USER,
            error = "CAPTION_TRACK_CHANGED",
        )
        val uncertainStt = seeded.stt.copy(
            state = ExecutionState.SUBMISSION_UNCERTAIN,
            phase = Phase.SUBMIT,
            error = "SUBMISSION_UNCERTAIN",
        )
        dao.updateAttempt(waitingCaption)
        dao.updateAttempt(uncertainStt)
        val uncertain = SubmissionRow(
            id = "fixture-uncertain-sibling-submission",
            attemptId = uncertainStt.id,
            chunkIndex = 0,
            provider = Provider.GROQ.name,
            credentialId = "fixture-credential",
            region = Region.US.name,
            inputHash = "fixture-uncertain-sibling-input",
            configHash = "fixture-config-hash",
            state = SubmissionState.UNCERTAIN,
            createdAt = uncertainStt.createdAt,
            estimatedMicrousd = 1,
        )
        dao.insertSubmission(uncertain)

        coordinator.resume(seeded.job.id)

        assertEquals(ExecutionState.QUEUED, requireNotNull(dao.attempt(waitingCaption.id)).state)
        assertEquals(uncertainStt, dao.attempt(uncertainStt.id))
        assertEquals(listOf(uncertain), dao.submissions(uncertainStt.id))
        assertEquals(0, providerRequests())
    }

    @Test
    fun deleteRemovesOnlyFixtureJobAndArtifactAndRetainsSharedSourceSibling() = withFixture {
        val seeded = seedCaption()
        assertFalse(coordinator.run(seeded.caption.id))
        coordinator.runExports(seeded.caption.id)

        val siblingJobId = UUID.randomUUID().toString()
        val siblingAttemptId = UUID.randomUUID().toString()
        val siblingArtifactId = UUID.randomUUID().toString()
        val siblingAttempt = AttemptRow(
            id = siblingAttemptId,
            jobId = siblingJobId,
            branch = Branch.CAPTIONS,
            number = 1,
            createdAt = seeded.job.createdAt + 1,
            state = ExecutionState.FINISHED,
            phase = Phase.PERSIST,
            outcome = app.sourcescribe.core.Outcome.SUCCESS,
            checkpoint = json.encodeToString(AttemptCheckpoint(source = seeded.source)),
        )
        val siblingJob = JobRow(
            id = siblingJobId,
            sourceId = seeded.source.id,
            config = json.encodeToString(seeded.config),
            createdAt = seeded.job.createdAt + 1,
            state = ExecutionState.FINISHED,
            outcome = app.sourcescribe.core.Outcome.SUCCESS,
        )
        dao.createJob(
            SourceRow(seeded.source.id, json.encodeToString(seeded.source), "Fixture source"),
            siblingJob,
            listOf(siblingAttempt),
        )
        val siblingDocument = seeded.document.copy(
            artifactId = siblingArtifactId,
            createdAt = seeded.document.createdAt + 1,
        )
        insertArtifact(siblingJob, siblingAttempt, siblingDocument)

        coordinator.delete(seeded.job.id)

        assertNull(dao.job(seeded.job.id))
        assertTrue(dao.attempts(seeded.job.id).isEmpty())
        assertTrue(dao.artifacts(seeded.job.id).isEmpty())
        assertTrue(dao.exports(seeded.artifactId).isEmpty())
        assertFalse(artifacts.canonicalFile(seeded.artifactId).isFile)
        assertNotNull(dao.source(seeded.source.id))
        assertEquals(1, dao.sourceReferences(seeded.source.id))
        assertEquals(siblingJob, dao.job(siblingJob.id))
        assertEquals(listOf(siblingAttempt), dao.attempts(siblingJob.id))
        assertEquals(listOf(siblingDocument), dao.artifacts(siblingJob.id).map { artifacts.read(it.id) })
        assertTrue(artifacts.canonicalFile(siblingArtifactId).isFile)
        assertEquals(0, providerRequests())
    }

    @Test
    fun schedulingFailureMarksOnlyUnclaimedAttemptAndKeepsJobWithoutDuplicate() = withFixture {
        val seeded = seedCaption()
        val before = requireNotNull(dao.attempt(seeded.caption.id))
        var callbackCalls = 0

        coordinator.schedulePersisted(before) {
            callbackCalls += 1
            throw IOException("fixture scheduling failure")
        }

        val failed = requireNotNull(dao.attempt(seeded.caption.id))
        val job = requireNotNull(dao.job(seeded.job.id))
        assertEquals(1, callbackCalls)
        assertEquals(ExecutionState.WAITING_USER, failed.state)
        assertEquals("SCHEDULING_FAILED", failed.error)
        assertNull(failed.leaseOwner)
        assertEquals(0L, failed.leaseUntil)
        assertEquals(seeded.job.id, job.id)
        assertEquals(ExecutionState.WAITING_USER, job.state)
        assertEquals(1, dao.attempts(seeded.job.id).size)
        assertTrue(dao.artifacts(seeded.job.id).isEmpty())
        assertEquals(0, providerRequests())
    }

    @Test
    fun schedulingFailureLeavesClaimedRunningAttemptUntouched() = withFixture {
        val seeded = seedCaption(state = ExecutionState.RUNNING, leaseOwner = "fixture-owner")
        val runningJob = seeded.job.copy(state = ExecutionState.RUNNING)
        dao.updateJob(runningJob)
        val before = requireNotNull(dao.attempt(seeded.caption.id))
        var callbackCalls = 0

        coordinator.schedulePersisted(before) {
            callbackCalls += 1
            throw IOException("fixture scheduling failure")
        }

        assertEquals(1, callbackCalls)
        assertEquals(before, requireNotNull(dao.attempt(seeded.caption.id)))
        assertEquals(runningJob, requireNotNull(dao.job(seeded.job.id)))
        assertEquals(0, providerRequests())
    }

    @Test
    fun successfulSchedulingCallbackDoesNotChangePersistedState() = withFixture {
        val seeded = seedCaption()
        val beforeAttempt = requireNotNull(dao.attempt(seeded.caption.id))
        val beforeJob = requireNotNull(dao.job(seeded.job.id))
        var callbackCalls = 0

        coordinator.schedulePersisted(beforeAttempt) {
            callbackCalls += 1
        }

        assertEquals(1, callbackCalls)
        assertEquals(beforeAttempt, requireNotNull(dao.attempt(seeded.caption.id)))
        assertEquals(beforeJob, requireNotNull(dao.job(seeded.job.id)))
        assertEquals(0, providerRequests())
    }

    @Test
    fun captionRateLimitRetriesThenWaitsWithoutStartingStt() = withFixture {
        val seeded = seedCaptionFailure(
            captionThenSttConfig(fallbackOnCaptionError = false),
            CaptionFailure.RATE_LIMIT,
        )

        repeat(4) { retry ->
            if (retry > 0) {
                dao.updateAttempt(requireNotNull(dao.attempt(seeded.attempt.id)).copy(nextAt = 0))
            }

            assertEquals(retry < 3, coordinator.run(seeded.attempt.id))
            val attempt = requireNotNull(dao.attempt(seeded.attempt.id))
            assertEquals(
                if (retry < 3) ExecutionState.WAITING_RATE_LIMIT else ExecutionState.WAITING_USER,
                attempt.state,
            )
            assertEquals("RATE_LIMIT", attempt.error)
            assertEquals(retry + 1, attempt.retries)
            assertTrue(dao.attempts(seeded.job.id).none { it.branch == Branch.STT })
            assertTrue(dao.submissions(seeded.attempt.id).isEmpty())
            assertEquals(0, providerRequests())
        }

        assertEquals(ExecutionState.WAITING_USER, requireNotNull(dao.job(seeded.job.id)).state)
    }

    @Test
    fun captionParserFailureDoesNotCreateFallbackOutsideExplicitCaptionThenSttOptIn() {
        val cases = listOf(
            captionThenSttConfig(fallbackOnCaptionError = false),
            captionConfig().copy(fallbackOnCaptionError = true),
            bothConfig().copy(fallbackOnCaptionError = true),
        )
        cases.forEach { config ->
            withFixture {
                val seeded = seedCaptionFailure(config, CaptionFailure.MALFORMED_VTT)
                val beforeSttIds = dao.attempts(seeded.job.id)
                    .filter { it.branch == Branch.STT }
                    .map { it.id }

                assertFalse(coordinator.run(seeded.attempt.id))

                val caption = requireNotNull(dao.attempt(seeded.attempt.id))
                assertEquals(config.mode.name, ExecutionState.WAITING_USER, caption.state)
                assertEquals(config.mode.name, "MALFORMED_INPUT", caption.error)
                val afterSttIds = dao.attempts(seeded.job.id)
                    .filter { it.branch == Branch.STT }
                    .map { it.id }
                assertEquals(config.mode.name, beforeSttIds, afterSttIds)
                assertTrue(config.mode.name, dao.submissions(seeded.attempt.id).isEmpty())
                assertEquals(config.mode.name, 0, providerRequests())
            }
        }
    }

    @Test
    fun captionParserFailureCreatesFallbackOnlyWithExplicitOptIn() = withFixture {
        val seeded = seedCaptionFailure(
            captionThenSttConfig(fallbackOnCaptionError = true),
            CaptionFailure.MALFORMED_VTT,
        )

        assertFalse(coordinator.run(seeded.attempt.id))

        val caption = requireNotNull(dao.attempt(seeded.attempt.id))
        assertEquals(ExecutionState.FINISHED, caption.state)
        assertEquals(app.sourcescribe.core.Outcome.FAILED, caption.outcome)
        assertEquals("MALFORMED_INPUT", caption.error)
        val fallback = dao.attempts(seeded.job.id).single { it.branch == Branch.STT }
        assertEquals(fallbackAttemptId(seeded.attempt.id), fallback.id)
        assertEquals(ExecutionState.QUEUED, fallback.state)
        assertTrue(dao.submissions(fallback.id).isEmpty())
        assertEquals(0, providerRequests())
    }

    @Test
    fun retryMissingOnlyUsesLatestFallbackSttAttemptInsteadOfOlderSuccess() = withFixture {
        prepareNativeRuntime()
        val config = captionConfig().copy(mode = AcquisitionMode.CAPTIONS_THEN_STT)
        val seeded = seedCaption(config = config)
        val checkpoint = json.encodeToString(AttemptCheckpoint(source = seeded.source))
        val oldStt = AttemptRow(
            id = fallbackAttemptId(seeded.caption.id),
            jobId = seeded.job.id,
            branch = Branch.STT,
            number = 1,
            createdAt = seeded.job.createdAt + 1,
            state = ExecutionState.FINISHED,
            phase = Phase.PERSIST,
            outcome = app.sourcescribe.core.Outcome.SUCCESS,
            checkpoint = checkpoint,
        )
        val latestStt = oldStt.copy(
            id = UUID.randomUUID().toString(),
            number = 2,
            createdAt = seeded.job.createdAt + 2,
            state = ExecutionState.WAITING_USER,
            outcome = app.sourcescribe.core.Outcome.FAILED,
            error = "MISSING_CHUNK",
        )
        dao.insertAttempt(oldStt)
        dao.insertAttempt(latestStt)
        val oldArtifactId = UUID.randomUUID().toString()
        insertArtifact(
            seeded.job,
            oldStt,
            sttDocument(oldArtifactId, seeded.source, config, oldStt.createdAt),
        )
        dao.updateJob(seeded.job.copy(state = ExecutionState.WAITING_USER))

        coordinator.retry(seeded.job.id, missingOnly = true)

        val attempts = dao.attempts(seeded.job.id)
        val sttAttempts = attempts.filter { it.branch == Branch.STT }.sortedBy { it.number }
        assertEquals(3, sttAttempts.size)
        assertEquals(oldStt, sttAttempts[0])
        assertEquals(ExecutionState.CANCELLED, sttAttempts[1].state)
        assertEquals(3, sttAttempts[2].number)
        assertEquals(ExecutionState.QUEUED, sttAttempts[2].state)
        assertEquals(seeded.source, json.decodeFromString<AttemptCheckpoint>(sttAttempts[2].checkpoint).source)
        assertEquals(1, attempts.count { it.branch == Branch.CAPTIONS })
        assertEquals(listOf(oldArtifactId), dao.artifacts(seeded.job.id).map { it.id })
        assertEquals(0, providerRequests())
    }

    @Test
    fun retryCleanupDeletesOnlyAttemptThatWasNeverCommitted() = withFixture {
        val seeded = seedCaption()
        val committedFile = writeAttemptFile(seeded.caption.id, "committed-retry.audio")
        val orphan = seeded.caption.copy(id = UUID.randomUUID().toString(), number = 2)
        val orphanFile = writeAttemptFile(orphan.id, "orphan-retry.audio")
        val beforeJob = requireNotNull(dao.job(seeded.job.id))
        val beforeAttempt = requireNotNull(dao.attempt(seeded.caption.id))
        val cancellation = CancellationException("fixture cancellation after commit")

        coordinator.discardUncommittedRetryFiles(listOf(seeded.caption, orphan), cancellation)

        assertTrue(committedFile.isFile)
        assertTrue(File(attemptPath(seeded.caption.id), "normalized.json").isFile)
        assertFalse(orphanFile.exists())
        assertFalse(attemptPath(orphan.id).exists())
        assertEquals(beforeJob, dao.job(seeded.job.id))
        assertEquals(beforeAttempt, dao.attempt(seeded.caption.id))
        assertNull(dao.attempt(orphan.id))
        assertTrue(cancellation.suppressed.isEmpty())
        assertEquals(0, providerRequests())
    }

    @Test
    fun exportSchedulingFailureCreatesOneRepairableFailureWithoutResettingAcquisition() = withFixture {
        val seeded = seedCaption(state = ExecutionState.RUNNING, leaseOwner = "stale-owner")
        assertFalse(coordinator.run(seeded.caption.id))

        val finishedAttempt = requireNotNull(dao.attempt(seeded.caption.id))
        val finishedJob = requireNotNull(dao.job(seeded.job.id))
        val artifact = requireNotNull(dao.artifact(seeded.artifactId))
        assertEquals(ExecutionState.FINISHED, finishedAttempt.state)
        assertEquals(ExecutionState.FINISHED, finishedJob.state)

        var callbackCalls = 0
        repeat(2) {
            coordinator.scheduleExportsPersisted(finishedAttempt) {
                callbackCalls += 1
                throw IOException("fixture export scheduling failure")
            }
        }

        val failedExports = dao.exports(artifact.id)
        assertEquals(2, callbackCalls)
        assertEquals(1, failedExports.size)
        assertEquals(ExportState.FAILED, failedExports.single().state)
        assertEquals("EXPORT_SCHEDULING_FAILED", failedExports.single().error)
        assertEquals(ExecutionState.FINISHED, requireNotNull(dao.attempt(seeded.caption.id)).state)
        assertEquals(ExecutionState.FINISHED, requireNotNull(dao.job(seeded.job.id)).state)
        assertEquals(1, dao.artifacts(seeded.job.id).size)

        coordinator.runExports(seeded.caption.id)

        assertEquals(failedExports, dao.exports(artifact.id))
        assertEquals(0, providerRequests())
    }

    @Test
    fun recoverClearsInheritedLeasesAndLeavesWaitingUserResumable() = withFixture {
        val seeded = seedCaption(state = ExecutionState.WAITING_USER, leaseOwner = "stale-owner")
        dao.updateJob(seeded.job.copy(state = ExecutionState.RUNNING))
        val now = System.currentTimeMillis()
        assertTrue(dao.claimResource("fixture-engine", "stale-resource-owner", now, now + LEASE_MS))

        coordinator.recover()

        val recoveredAttempt = requireNotNull(dao.attempt(seeded.caption.id))
        assertEquals(ExecutionState.WAITING_USER, recoveredAttempt.state)
        assertNull(recoveredAttempt.leaseOwner)
        assertEquals(0L, recoveredAttempt.leaseUntil)
        assertEquals(ExecutionState.WAITING_USER, requireNotNull(dao.job(seeded.job.id)).state)
        assertTrue(dao.claimResource("fixture-engine", "fresh-resource-owner", now, now + LEASE_MS))

        coordinator.resume(seeded.job.id)
        assertFalse(coordinator.run(seeded.caption.id))
        assertEquals(ExecutionState.FINISHED, requireNotNull(dao.attempt(seeded.caption.id)).state)
        assertEquals(1, dao.artifacts(seeded.job.id).size)
        assertEquals(0, providerRequests())
    }

    @Test
    fun recoverPreservesFutureRemoteRetryTimeWhileClearingLease() = withFixture {
        val seeded = seedWaitingRemote()
        val future = System.currentTimeMillis() + 6 * 60 * 60 * 1000L
        dao.updateAttempt(
            seeded.attempt.copy(
                state = ExecutionState.WAITING_REMOTE,
                phase = Phase.RETRIEVE,
                nextAt = future,
                leaseOwner = "stale-owner",
                leaseUntil = future,
            ),
        )
        dao.updateJob(seeded.job.copy(state = ExecutionState.WAITING_REMOTE))

        coordinator.recover()

        val recoveredAttempt = requireNotNull(dao.attempt(seeded.attempt.id))
        assertEquals(ExecutionState.WAITING_REMOTE, recoveredAttempt.state)
        assertEquals(future, recoveredAttempt.nextAt)
        assertNull(recoveredAttempt.leaseOwner)
        assertEquals(0L, recoveredAttempt.leaseUntil)
        assertEquals(ExecutionState.WAITING_REMOTE, requireNotNull(dao.job(seeded.job.id)).state)
        assertEquals(0, providerRequests())
    }

    @Test
    fun recoverRepairsFinalizedArtifactWithoutRoomRowExactlyOnce() = withFixture {
        val seeded = seedCaption()
        artifacts.write(seeded.document)
        assertNull(dao.artifact(seeded.artifactId))
        assertEquals(Phase.PERSIST, requireNotNull(dao.attempt(seeded.caption.id)).phase)

        coordinator.recover()

        val repairedArtifact = requireNotNull(dao.artifact(seeded.artifactId))
        assertEquals(seeded.job.id, repairedArtifact.jobId)
        assertEquals(seeded.caption.id, repairedArtifact.attemptId)
        assertEquals(Branch.CAPTIONS, repairedArtifact.branch)
        assertEquals(ExecutionState.FINISHED, requireNotNull(dao.attempt(seeded.caption.id)).state)
        assertEquals(ExecutionState.FINISHED, requireNotNull(dao.job(seeded.job.id)).state)
        assertEquals(seeded.document, artifacts.read(seeded.artifactId))

        val restarted = restartedCoordinator()
        restarted.recover()

        assertEquals(1, dao.artifacts(seeded.job.id).size)
        assertNotNull(dao.artifact(seeded.artifactId))
        assertEquals(seeded.document, artifacts.read(seeded.artifactId))
        assertEquals(0, providerRequests())
    }

    @Test
    fun startupCleanupRemovesFinishedAttemptFilesAndIsIdempotent() = withFixture {
        val seeded = seedCaption(state = ExecutionState.FINISHED)
        insertArtifact(seeded.job, seeded.caption, seeded.document)
        dao.updateJob(
            seeded.job.copy(
                state = ExecutionState.FINISHED,
                outcome = app.sourcescribe.core.Outcome.SUCCESS,
            ),
        )
        val stale = writeAttemptFile(seeded.caption.id, "stale.tmp")
        val nested = writeAttemptFile(seeded.caption.id, "nested/stale.part")
        assertTrue(stale.isFile)
        assertTrue(nested.isFile)

        coordinator.recover()

        assertFalse(attemptPath(seeded.caption.id).exists())
        assertTrue(artifacts.canonicalFile(seeded.artifactId).isFile)
        assertEquals(1, dao.artifacts(seeded.job.id).size)

        restartedCoordinator().recover()

        assertFalse(attemptPath(seeded.caption.id).exists())
        assertTrue(artifacts.canonicalFile(seeded.artifactId).isFile)
        assertEquals(1, dao.artifacts(seeded.job.id).size)
        assertEquals(0, providerRequests())
    }

    @Test
    fun startupCleanupRemovesCancelledFilesWithoutTouchingSibling() = withFixture {
        val seeded = seedCaption(state = ExecutionState.CANCELLED, error = "FIXTURE_CANCELLED")
        dao.updateAttempt(
            seeded.caption.copy(
                state = ExecutionState.CANCELLED,
                outcome = app.sourcescribe.core.Outcome.CANCELLED,
            ),
        )
        dao.updateJob(
            seeded.job.copy(
                state = ExecutionState.CANCELLED,
                outcome = app.sourcescribe.core.Outcome.CANCELLED,
                cancelRequested = true,
            ),
        )
        val fixtureFile = writeAttemptFile(seeded.caption.id, "cancelled.tmp")

        val siblingJobId = UUID.randomUUID().toString()
        val siblingAttemptId = UUID.randomUUID().toString()
        val siblingAttempt = AttemptRow(
            id = siblingAttemptId,
            jobId = siblingJobId,
            branch = Branch.CAPTIONS,
            number = 1,
            createdAt = seeded.job.createdAt + 1,
            state = ExecutionState.WAITING_USER,
            phase = Phase.PERSIST,
            error = "SIBLING_PAUSED",
            checkpoint = json.encodeToString(AttemptCheckpoint(source = seeded.source)),
        )
        val siblingJob = JobRow(
            id = siblingJobId,
            sourceId = seeded.source.id,
            config = json.encodeToString(seeded.config),
            createdAt = seeded.job.createdAt + 1,
            state = ExecutionState.WAITING_USER,
        )
        dao.createJob(
            SourceRow(seeded.source.id, json.encodeToString(seeded.source), "Fixture source"),
            siblingJob,
            listOf(siblingAttempt),
        )
        val siblingFile = writeAttemptFile(siblingAttemptId, "sibling.tmp")

        coordinator.recover()

        assertFalse(fixtureFile.exists())
        assertFalse(attemptPath(seeded.caption.id).exists())
        assertTrue(siblingFile.isFile)
        assertTrue(attemptPath(siblingAttemptId).isDirectory)
        assertEquals(2, dao.sourceReferences(seeded.source.id))
        assertEquals(siblingJob, dao.job(siblingJobId))
        assertEquals(listOf(siblingAttempt), dao.attempts(siblingJobId))
        assertEquals(0, providerRequests())
    }

    @Test
    fun startupCleanupRemovesOnlyAbandonedImportParts() = withFixture {
        val imports = importsDirectory()
        val abandoned = File(imports, "import-stale.part").also { it.writeText("partial") }
        val ordinaryPart = File(imports, "manual.part").also { it.writeText("keep") }
        val finalAudio = File(imports, "import-stale.audio").also { it.writeText("keep") }
        val unusualPart = File(imports, "import-stale_name.part").also { it.writeText("keep") }
        val orphanHash = "0123456789abcdef".repeat(4)
        val orphanAudio = File(imports, "$orphanHash.audio").also { it.writeText("orphan") }

        coordinator.recover()

        assertFalse(abandoned.exists())
        assertTrue(ordinaryPart.isFile)
        assertTrue(finalAudio.isFile)
        assertTrue(unusualPart.isFile)
        assertFalse(orphanAudio.exists())

        restartedCoordinator().recover()

        assertFalse(abandoned.exists())
        assertTrue(ordinaryPart.isFile)
        assertTrue(finalAudio.isFile)
        assertTrue(unusualPart.isFile)
        assertFalse(orphanAudio.exists())
        assertEquals(0, providerRequests())
    }

    @Test
    fun startupCleanupRemovesUncommittedRetryCopiesWithoutTouchingKnownAttemptsOrLinks() = withFixture {
        val known = seedCaption(state = ExecutionState.WAITING_USER)
        val retained = writeAttemptFile(known.caption.id, "audio-0.mp3", "retained paid evidence")
        val abandonedId = UUID.randomUUID().toString()
        val copiedAudio = writeAttemptFile(abandonedId, "audio-0.mp3", "orphan copied audio")
        val copiedResponse = writeAttemptFile(abandonedId, "response.json", "orphan copied response")
        val abandonedDirectory = requireNotNull(copiedAudio.parentFile)
        val attempts = requireNotNull(abandonedDirectory.parentFile)
        val unrelated = File(attempts, "manual-files").also { check(it.mkdir()) }
        val sentinel = File(unrelated, "keep").also { it.writeText("keep") }
        val link = File(attempts, UUID.randomUUID().toString()).toPath()
        Files.createSymbolicLink(link, unrelated.toPath())
        assertNull(dao.attempt(abandonedId))

        coordinator.recover()
        restartedCoordinator().recover()

        assertFalse(copiedAudio.exists())
        assertFalse(copiedResponse.exists())
        assertFalse(abandonedDirectory.exists())
        assertTrue(retained.isFile)
        assertTrue(sentinel.isFile)
        assertTrue(Files.isSymbolicLink(link))
        assertNotNull(dao.attempt(known.caption.id))
        assertEquals(0, providerRequests())
    }

    @Test
    fun previewPinnedFinishedLocalJobKeepsAudioAcrossNewJobAndOldDeletion() = withFixture {
        val old = seedLocalJob(
            state = ExecutionState.FINISHED,
            phase = Phase.PERSIST,
            outcome = app.sourcescribe.core.Outcome.SUCCESS,
        )
        val document = sttDocument(old.artifactId, old.source, old.config, old.attempt.createdAt)
        insertArtifact(old.job, old.attempt, document)

        val owner = "fixture-preview-${UUID.randomUUID()}"
        SourceFiles.registerPreviewOwner(owner)
        SourceFiles.reservePreview(old.source.id, owner)
        try {
            coordinator.recover()
            assertTrue(SourceFiles.hasPreview(old.source.id))
            assertTrue(old.audioFile.isFile)

            val siblingJobId = UUID.randomUUID().toString()
            val siblingAttempt = AttemptRow(
                id = UUID.randomUUID().toString(),
                jobId = siblingJobId,
                branch = Branch.STT,
                number = 1,
                createdAt = old.job.createdAt + 1,
                state = ExecutionState.WAITING_USER,
                phase = Phase.RESOLVE,
                checkpoint = json.encodeToString(AttemptCheckpoint(source = old.source)),
            )
            val siblingJob = JobRow(
                id = siblingJobId,
                sourceId = old.source.id,
                config = json.encodeToString(old.config),
                createdAt = old.job.createdAt + 1,
                state = ExecutionState.WAITING_USER,
            )
            dao.createJob(
                SourceRow(
                    old.source.id,
                    json.encodeToString(old.source),
                    "Fixture local source",
                    old.audioFile.absolutePath,
                ),
                siblingJob,
                listOf(siblingAttempt),
            )
            assertEquals(2, dao.sourceReferences(old.source.id))

            SourceFiles.releasePreview(old.source.id, owner)
            SourceFiles.unregisterPreviewOwner(owner)
            assertFalse(SourceFiles.hasPreview(old.source.id))

            coordinator.delete(old.job.id)
            assertNull(dao.job(old.job.id))
            assertNotNull(dao.job(siblingJob.id))
            assertTrue(old.audioFile.isFile)

            restartedCoordinator().recover()
            assertTrue(old.audioFile.isFile)
            assertEquals(1, dao.sourceReferences(old.source.id))
            assertEquals(0, providerRequests())
        } finally {
            SourceFiles.unregisterPreviewOwner(owner)
        }
    }

    @Test
    fun normalizedCaptionWithSameSourceIdButChangedIdentityCannotBePersisted() = withFixture {
        val seeded = seedCaption(state = ExecutionState.RUNNING, leaseOwner = "stale-owner")
        val tampered = seeded.document.copy(
            source = seeded.document.source.copy(
                canonicalUrl = "https://www.youtube.com/watch?v=fixture-other-video",
            ),
            provenance = seeded.document.provenance.copy(
                origin = Origin.PROVIDER,
                generation = Generation.UNKNOWN,
                translation = Translation.UNKNOWN,
                captionTrack = null,
            ),
        )
        writeAttemptFile(seeded.caption.id, "normalized.json", json.encodeToString(tampered))

        assertFalse(coordinator.run(seeded.caption.id))

        val attempt = requireNotNull(dao.attempt(seeded.caption.id))
        assertEquals(ExecutionState.WAITING_USER, attempt.state)
        assertEquals("NORMALIZED_ARTIFACT_INVALID", attempt.error)
        assertNull(dao.artifact(seeded.artifactId))
        assertFalse(artifacts.canonicalFile(seeded.artifactId).exists())
        assertEquals(0, providerRequests())
    }

    @Test
    fun normalizedCaptionRequiresExactSourceTimeAndProvenanceBindings() {
        val mutations: Map<String, (TranscriptDocument) -> TranscriptDocument> = mapOf(
            "source" to { document -> document.copy(source = document.source.copy(channel = "Other channel")) },
            "createdAt" to { document -> document.copy(createdAt = document.createdAt + 1) },
            "origin" to { document -> document.copy(provenance = document.provenance.copy(origin = Origin.PROVIDER)) },
            "provider" to { document -> document.copy(provenance = document.provenance.copy(provider = Provider.GROQ)) },
            "captionTrack" to { document ->
                document.copy(provenance = document.provenance.copy(
                    captionTrack = requireNotNull(document.provenance.captionTrack).copy(id = "other-caption"),
                ))
            },
            "generation" to { document ->
                document.copy(provenance = document.provenance.copy(generation = Generation.AUTOMATIC))
            },
            "translation" to { document ->
                document.copy(provenance = document.provenance.copy(translation = Translation.AUTOMATIC))
            },
            "engineVersions" to { document ->
                document.copy(provenance = document.provenance.copy(engineVersions = mapOf("yt-dlp" to "wrong")))
            },
        )

        for ((binding, mutate) in mutations) withFixture {
            val seeded = seedCaption(state = ExecutionState.RUNNING, leaseOwner = "stale-owner")
            writeAttemptFile(seeded.caption.id, "normalized.json", json.encodeToString(mutate(seeded.document)))

            assertFalse(binding, coordinator.run(seeded.caption.id))

            val attempt = requireNotNull(dao.attempt(seeded.caption.id))
            assertEquals(binding, ExecutionState.WAITING_USER, attempt.state)
            assertEquals(binding, "NORMALIZED_ARTIFACT_INVALID", attempt.error)
            assertNull(binding, dao.artifact(seeded.artifactId))
            assertFalse(binding, artifacts.canonicalFile(seeded.artifactId).exists())
            assertEquals(binding, 0, providerRequests())
        }
    }

    @Test
    fun oversizedNormalizedCaptionCannotBePersisted() = withFixture {
        val seeded = seedCaption(state = ExecutionState.RUNNING, leaseOwner = "stale-owner")
        RandomAccessFile(File(attemptPath(seeded.caption.id), "normalized.json"), "rw").use {
            it.setLength(ArtifactFiles.MAX_CANONICAL_BYTES.toLong() + 1)
        }

        assertFalse(coordinator.run(seeded.caption.id))

        val attempt = requireNotNull(dao.attempt(seeded.caption.id))
        assertEquals(ExecutionState.WAITING_USER, attempt.state)
        assertEquals("NORMALIZED_ARTIFACT_INVALID", attempt.error)
        assertNull(dao.artifact(seeded.artifactId))
        assertFalse(artifacts.canonicalFile(seeded.artifactId).exists())
        assertEquals(0, providerRequests())
    }

    @Test
    fun oversizedRawCaptionCannotBePersisted() = withFixture {
        val seeded = seedCaption(
            state = ExecutionState.RUNNING,
            leaseOwner = "stale-owner",
            config = captionConfig().copy(retainRaw = true),
        )
        RandomAccessFile(File(attemptPath(seeded.caption.id), "raw.vtt"), "rw").use {
            it.setLength(ArtifactFiles.MAX_RAW_BYTES.toLong() + 1)
        }

        assertFalse(coordinator.run(seeded.caption.id))

        val attempt = requireNotNull(dao.attempt(seeded.caption.id))
        assertEquals(ExecutionState.WAITING_USER, attempt.state)
        assertEquals("NORMALIZED_ARTIFACT_INVALID", attempt.error)
        assertNull(dao.artifact(seeded.artifactId))
        assertFalse(artifacts.canonicalFile(seeded.artifactId).exists())
        assertEquals(0, providerRequests())
    }

    @Test
    fun symlinkedNormalizedCaptionCannotBePersisted() = withFixture {
        val seeded = seedCaption(state = ExecutionState.RUNNING, leaseOwner = "stale-owner")
        val normalized = File(attemptPath(seeded.caption.id), "normalized.json")
        val target = writeAttemptFile(seeded.caption.id, "linked-normalized.json", normalized.readText())
        assertTrue(normalized.delete())
        Files.createSymbolicLink(normalized.toPath(), target.toPath())

        assertFalse(coordinator.run(seeded.caption.id))

        val attempt = requireNotNull(dao.attempt(seeded.caption.id))
        assertEquals(ExecutionState.WAITING_USER, attempt.state)
        assertEquals("NORMALIZED_ARTIFACT_INVALID", attempt.error)
        assertNull(dao.artifact(seeded.artifactId))
        assertFalse(artifacts.canonicalFile(seeded.artifactId).exists())
        assertEquals(0, providerRequests())
    }

    @Test
    fun cancelledLocalJobWithoutArtifactKeepsOriginalAudioWithDefaultRetention() = withFixture {
        val seeded = seedLocalJob(
            state = ExecutionState.WAITING_USER,
            phase = Phase.RESOLVE,
            config = sttConfig().copy(audioRetention = JobConfig().audioRetention),
        )
        assertEquals(AudioRetention.UNTIL_PERSISTED, seeded.config.audioRetention)

        coordinator.cancel(seeded.job.id)

        assertEquals(ExecutionState.CANCELLED, requireNotNull(dao.job(seeded.job.id)).state)
        assertEquals(ExecutionState.CANCELLED, requireNotNull(dao.attempt(seeded.attempt.id)).state)
        assertNull(dao.artifact(seeded.artifactId))
        assertTrue(seeded.audioFile.isFile)
        assertEquals(seeded.audioFile.absolutePath, dao.source(seeded.source.id)?.importedPath)

        restartedCoordinator().recover()

        assertTrue(seeded.audioFile.isFile)
        assertEquals(seeded.audioFile.absolutePath, dao.source(seeded.source.id)?.importedPath)
        assertEquals(0, providerRequests())
    }

    @Test
    fun previewOfRemovedLocalAudioFailsWithoutCreatingPreviewPin() = withFixture {
        val seeded = seedLocalJob(state = ExecutionState.WAITING_USER)
        val owner = "fixture-preview-missing-${UUID.randomUUID()}"
        SourceFiles.registerPreviewOwner(owner)
        try {
            assertTrue(seeded.audioFile.delete())

            val failure: JobActionException? = try {
                coordinator.sourceForPreview(seeded.source.id, owner)
                null
            } catch (error: JobActionException) {
                error
            }

            assertNotNull(failure)
            assertEquals("IMPORTED_AUDIO_NOT_FOUND", requireNotNull(failure).code)
            assertFalse(SourceFiles.hasPreview(seeded.source.id))
            assertEquals(0, providerRequests())
        } finally {
            SourceFiles.unregisterPreviewOwner(owner)
        }
    }

    @Test
    fun recoverDeletesFinalizedArtifactForDeleteRequestedJob() = withFixture {
        val seeded = seedCaption()
        artifacts.write(seeded.document)
        dao.updateAttempt(
            seeded.caption.copy(
                state = ExecutionState.CANCELLED,
                phase = Phase.PERSIST,
                outcome = app.sourcescribe.core.Outcome.CANCELLED,
                leaseOwner = null,
                leaseUntil = 0,
            ),
        )
        dao.updateJob(
            seeded.job.copy(
                state = ExecutionState.CANCELLED,
                outcome = app.sourcescribe.core.Outcome.CANCELLED,
                cancelRequested = true,
                deleteRequested = true,
            ),
        )
        assertNull(dao.artifact(seeded.artifactId))
        assertTrue(artifacts.canonicalFile(seeded.artifactId).isFile)

        coordinator.recover()

        assertNull(dao.job(seeded.job.id))
        assertNull(dao.attempt(seeded.caption.id))
        assertNull(dao.artifact(seeded.artifactId))
        assertFalse(artifacts.canonicalFile(seeded.artifactId).isFile)
        assertEquals(0, providerRequests())
    }

    @Test
    fun cancelledSendingSubmissionRecoversAsUncertainAndRetainsResponseSpool() = withFixture {
        val seeded = seedWaitingRemote()
        val now = System.currentTimeMillis()
        val running = seeded.attempt.copy(
            state = ExecutionState.RUNNING,
            phase = Phase.SUBMIT,
            leaseOwner = "fixture-worker",
            leaseUntil = now + LEASE_MS,
            nextAt = 0,
        )
        dao.updateJob(seeded.job.copy(state = ExecutionState.RUNNING))
        dao.updateAttempt(running)

        val submissionId = UUID.randomUUID().toString()
        val rawResponse = writeAttemptFile(
            running.id,
            "responses/$submissionId.json",
            "{\"fixture\":\"spooled-before-cancel\"}",
        )
        dao.insertSubmission(
            SubmissionRow(
                id = submissionId,
                attemptId = running.id,
                chunkIndex = 0,
                provider = Provider.GROQ.name,
                credentialId = "fixture-credential",
                region = Region.US.name,
                inputHash = "fixture-input-hash",
                configHash = "fixture-config-hash",
                state = SubmissionState.SENDING,
                createdAt = now,
                estimatedMicrousd = 1,
                rawResponsePath = rawResponse.absolutePath,
            ),
        )

        coordinator.cancel(seeded.job.id)

        val cancelledAttempt = requireNotNull(dao.attempt(running.id))
        assertEquals(ExecutionState.SUBMISSION_UNCERTAIN, cancelledAttempt.state)
        assertEquals(ExecutionState.CANCELLED, requireNotNull(dao.job(seeded.job.id)).state)

        val restarted = restartedCoordinator()
        restarted.recover()

        val recoveredAttempt = requireNotNull(dao.attempt(running.id))
        assertEquals(ExecutionState.SUBMISSION_UNCERTAIN, recoveredAttempt.state)
        assertEquals("SUBMISSION_UNCERTAIN", recoveredAttempt.error)
        val recoveredSubmission = dao.submissions(running.id).single()
        assertTrue(recoveredSubmission.state in setOf(SubmissionState.SENDING, SubmissionState.UNCERTAIN))
        assertEquals(rawResponse.absolutePath, recoveredSubmission.rawResponsePath)
        assertTrue(rawResponse.isFile)
        assertEquals("{\"fixture\":\"spooled-before-cancel\"}", rawResponse.readText())
        assertEquals(0, providerRequests())
    }

    @Test
    fun keepAudioRetentionCleanupDeletesEphemeralAttemptFilesOnly() = withFixture {
        val seeded = seedWaitingRemote()
        val config = sttConfig().copy(
            exportFormats = emptySet(),
            retainRaw = false,
            audioRetention = AudioRetention.KEEP,
        )
        val source = json.decodeFromString<Source>(requireNotNull(dao.source(seeded.job.sourceId)).snapshot)
        val artifactId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val checkpoint = AttemptCheckpoint(
            artifactId = artifactId,
            artifactCreatedAt = now,
            source = source,
        )
        val finishedAttempt = seeded.attempt.copy(
            createdAt = now,
            state = ExecutionState.FINISHED,
            phase = Phase.PERSIST,
            outcome = app.sourcescribe.core.Outcome.SUCCESS,
            checkpoint = json.encodeToString(checkpoint),
            leaseOwner = null,
            leaseUntil = 0,
        )
        val finishedJob = seeded.job.copy(
            config = json.encodeToString(config),
            createdAt = now,
            state = ExecutionState.FINISHED,
            outcome = app.sourcescribe.core.Outcome.SUCCESS,
        )
        dao.updateJob(finishedJob)
        dao.updateAttempt(finishedAttempt)
        val document = sttDocument(artifactId, source, config, now)
        insertArtifact(finishedJob, finishedAttempt, document)

        val sourceAudio = writeAttemptFile(finishedAttempt.id, "source.audio")
        val preparedAudio = writeAttemptFile(finishedAttempt.id, "audio-0.mp3")
        val normalized = writeAttemptFile(finishedAttempt.id, "normalized.json")
        val rawProviderJson = writeAttemptFile(finishedAttempt.id, "raw.provider.json")
        val rawProviderZip = writeAttemptFile(finishedAttempt.id, "raw.provider.zip")
        val response = writeAttemptFile(finishedAttempt.id, "responses/fixture-response.json")
        val responses = requireNotNull(response.parentFile)

        coordinator.recover()

        assertTrue(sourceAudio.isFile)
        assertTrue(preparedAudio.isFile)
        assertFalse(normalized.exists())
        assertFalse(rawProviderJson.exists())
        assertFalse(rawProviderZip.exists())
        assertFalse(response.exists())
        assertFalse(responses.exists())
        assertTrue(attemptPath(finishedAttempt.id).isDirectory)
        assertEquals(1, dao.artifacts(finishedJob.id).size)
        assertTrue(artifacts.canonicalFile(artifactId).isFile)

        restartedCoordinator().recover()

        assertTrue(sourceAudio.isFile)
        assertTrue(preparedAudio.isFile)
        assertFalse(attemptPath(finishedAttempt.id).listFiles().orEmpty().any { it.name == "normalized.json" })
        assertEquals(1, dao.artifacts(finishedJob.id).size)
        assertEquals(0, providerRequests())
    }

    @Test
    fun corruptedUnattributedArtifactDoesNotBlockRecoveryExportOrDeletion() = withFixture {
        val seeded = seedCaption()
        artifacts.write(seeded.document)
        val corruptedId = UUID.randomUUID().toString()
        val corrupted = seeded.document.copy(artifactId = corruptedId, createdAt = seeded.document.createdAt + 1)
        artifacts.write(corrupted)
        File(artifacts.canonicalFile(corruptedId).path).writeText("{\"corrupted\":true}")

        coordinator.recover()
        coordinator.runExports(seeded.caption.id)

        assertEquals(ExecutionState.FINISHED, requireNotNull(dao.attempt(seeded.caption.id)).state)
        assertNotNull(dao.artifact(seeded.artifactId))
        assertEquals(ExportState.PERMISSION_REQUIRED, dao.exports(seeded.artifactId).single().state)

        coordinator.delete(seeded.job.id)

        assertNull(dao.job(seeded.job.id))
        assertFalse(artifacts.canonicalFile(seeded.artifactId).exists())
        assertTrue(artifacts.canonicalFile(corruptedId).isFile)
        assertEquals(0, providerRequests())
    }

    @Test
    fun corruptedAttributedArtifactPausesOnlyItsAttempt() = withFixture {
        val valid = seedCaption()
        val corrupted = seedCaption()
        artifacts.write(valid.document)
        artifacts.write(corrupted.document)
        artifacts.canonicalFile(corrupted.artifactId).writeText("{\"corrupted\":true}")

        coordinator.recover()

        assertEquals(ExecutionState.FINISHED, requireNotNull(dao.attempt(valid.caption.id)).state)
        assertNotNull(dao.artifact(valid.artifactId))
        val failed = requireNotNull(dao.attempt(corrupted.caption.id))
        assertEquals(ExecutionState.WAITING_USER, failed.state)
        assertEquals("CORRUPT_CANONICAL", failed.error)
        assertNull(dao.artifact(corrupted.artifactId))
        assertTrue(artifacts.canonicalFile(corrupted.artifactId).isFile)
        assertEquals(0, providerRequests())
    }

    @Test
    fun recoveryRejectsHashConsistentArtifactWithWrongSourceTimeOrProvenance() {
        val mutations: Map<String, (TranscriptDocument) -> TranscriptDocument> = mapOf(
            "source" to { document -> document.copy(source = document.source.copy(channel = "Wrong channel")) },
            "createdAt" to { document -> document.copy(createdAt = document.createdAt + 1) },
            "provenance" to { document ->
                document.copy(provenance = document.provenance.copy(
                    captionTrack = requireNotNull(document.provenance.captionTrack).copy(evidence = "wrong"),
                ))
            },
            "engineVersions" to { document ->
                document.copy(provenance = document.provenance.copy(engineVersions = mapOf("yt-dlp" to "wrong")))
            },
        )

        for ((binding, mutate) in mutations) withFixture {
            val seeded = seedCaption()
            artifacts.write(mutate(seeded.document))

            coordinator.recover()

            val attempt = requireNotNull(dao.attempt(seeded.caption.id))
            assertEquals(binding, ExecutionState.WAITING_USER, attempt.state)
            assertEquals(binding, "ARTIFACT_BINDING_MISMATCH", attempt.error)
            assertNull(binding, dao.artifact(seeded.artifactId))
            assertTrue(binding, artifacts.canonicalFile(seeded.artifactId).isFile)
            assertEquals(binding, 0, providerRequests())
        }
    }

    @Test
    fun recoveryRejectsSttArtifactWithWrongEngineVersionBinding() = withFixture {
        val seeded = seedLocalJob(state = ExecutionState.QUEUED, phase = Phase.PERSIST)
        val document = sttDocument(
            seeded.artifactId,
            seeded.source,
            seeded.config,
            seeded.attempt.createdAt,
        )
        artifacts.write(document.copy(
            provenance = document.provenance.copy(engineVersions = mapOf("ffmpeg" to "wrong")),
        ))

        coordinator.recover()

        val attempt = requireNotNull(dao.attempt(seeded.attempt.id))
        assertEquals(ExecutionState.WAITING_USER, attempt.state)
        assertEquals("ARTIFACT_BINDING_MISMATCH", attempt.error)
        assertNull(dao.artifact(seeded.artifactId))
        assertTrue(artifacts.canonicalFile(seeded.artifactId).isFile)
        assertEquals(0, providerRequests())
    }

    @Test
    fun roomArtifactWithoutFinalizedFilesPausesOnlyItsAttempt() {
        for (removeDirectory in listOf(true, false)) withFixture {
            val broken = seedCaption()
            val healthy = seedCaption()
            insertArtifact(broken.job, broken.caption, broken.document)
            insertArtifact(healthy.job, healthy.caption, healthy.document)
            if (removeDirectory) artifacts.delete(broken.artifactId)
            else assertTrue(File(artifacts.canonicalFile(broken.artifactId).parentFile, ".commit.json").delete())

            coordinator.recover()

            assertEquals(ExecutionState.WAITING_USER, dao.attempt(broken.caption.id)?.state)
            assertEquals("ARTIFACT_FILE_MISSING", dao.attempt(broken.caption.id)?.error)
            assertEquals(ExecutionState.WAITING_USER, dao.job(broken.job.id)?.state)
            assertNotNull(dao.artifact(broken.artifactId))
            assertEquals(ExecutionState.FINISHED, dao.attempt(healthy.caption.id)?.state)
            assertEquals(healthy.document, artifacts.read(healthy.artifactId))
            assertEquals(0, providerRequests())
        }
    }

    @Test
    fun malformedJobConfigurationIsIsolatedAndCanStillBeDeleted() = withFixture {
        val broken = seedCaption()
        val healthy = seedCaption()
        artifacts.write(broken.document)
        artifacts.write(healthy.document)
        dao.updateJob(broken.job.copy(config = "{\"mode\":\"BROKEN\"}"))

        coordinator.recover()
        restartedCoordinator().recover()

        assertEquals(ExecutionState.WAITING_USER, dao.job(broken.job.id)?.state)
        assertEquals("JOB_CONFIG_INVALID", dao.attempt(broken.caption.id)?.error)
        assertEquals(ExecutionState.FINISHED, dao.attempt(healthy.caption.id)?.state)
        assertEquals(healthy.document, artifacts.read(healthy.artifactId))
        assertTrue(artifacts.canonicalFile(broken.artifactId).isFile)
        coordinator.delete(broken.job.id)
        assertNull(dao.job(broken.job.id))
        assertFalse(artifacts.canonicalFile(broken.artifactId).exists())
        assertEquals(healthy.document, artifacts.read(healthy.artifactId))
        assertEquals(0, providerRequests())
    }

    @Test
    fun cancelledResponseSavedAttemptRetainsPaidResponseSpool() = withFixture {
        val seeded = seedLocalJob(state = ExecutionState.WAITING_USER, phase = Phase.RETRIEVE)
        val submissionId = UUID.randomUUID().toString()
        val response = writeAttemptFile(
            seeded.attempt.id,
            "responses/$submissionId.json",
            "{\"fixture\":\"paid-response\"}",
        )
        dao.insertSubmission(
            SubmissionRow(
                id = submissionId,
                attemptId = seeded.attempt.id,
                chunkIndex = 0,
                provider = Provider.GROQ.name,
                credentialId = "fixture-credential",
                region = Region.US.name,
                inputHash = "fixture-input-hash",
                configHash = "fixture-config-hash",
                state = SubmissionState.RESPONSE_SAVED,
                createdAt = seeded.attempt.createdAt,
                estimatedMicrousd = 1,
                rawResponsePath = response.absolutePath,
            ),
        )

        coordinator.cancel(seeded.job.id)

        assertEquals(ExecutionState.CANCELLED, requireNotNull(dao.attempt(seeded.attempt.id)).state)
        assertTrue(response.isFile)
        assertEquals("{\"fixture\":\"paid-response\"}", response.readText())

        restartedCoordinator().recover()

        assertTrue(response.isFile)
        assertEquals(response.absolutePath, dao.submissions(seeded.attempt.id).single().rawResponsePath)
        assertEquals(0, providerRequests())
    }

    @Test
    fun deletingLastPreviewPinnedLocalJobDefersSourceCleanupUntilPreviewDiscard() = withFixture {
        val seeded = seedLocalJob(state = ExecutionState.WAITING_USER)
        val owner = "fixture-last-job-preview-${UUID.randomUUID()}"
        SourceFiles.registerPreviewOwner(owner)
        SourceFiles.reservePreview(seeded.source.id, owner)
        try {
            coordinator.delete(seeded.job.id)

            assertNull(dao.job(seeded.job.id))
            assertNotNull(dao.source(seeded.source.id))
            assertTrue(seeded.audioFile.isFile)

            SourceFiles.releasePreview(seeded.source.id, owner)
            coordinator.discardImportPreview(seeded.source.id)

            assertNull(dao.source(seeded.source.id))
            assertFalse(seeded.audioFile.exists())
            assertEquals(0, providerRequests())
        } finally {
            SourceFiles.unregisterPreviewOwner(owner)
        }
    }

    @Test
    fun historicalCompleteAttemptCleansItsFilesWhileLatestPartialAttemptKeepsSpools() = withFixture {
        val complete = seedLocalJob(
            state = ExecutionState.FINISHED,
            phase = Phase.PERSIST,
            outcome = app.sourcescribe.core.Outcome.SUCCESS,
        )
        val completeDocument = sttDocument(complete.artifactId, complete.source, complete.config, complete.attempt.createdAt)
        insertArtifact(complete.job, complete.attempt, completeDocument)
        val partialAttempt = complete.attempt.copy(
            id = UUID.randomUUID().toString(),
            number = 2,
            createdAt = complete.attempt.createdAt + 1,
            outcome = app.sourcescribe.core.Outcome.PARTIAL_SUCCESS,
            checkpoint = json.encodeToString(
                AttemptCheckpoint(
                    artifactId = UUID.randomUUID().toString(),
                    artifactCreatedAt = complete.attempt.createdAt + 1,
                    source = complete.source,
                ),
            ),
        )
        dao.insertAttempt(partialAttempt)
        val partialDocument = sttDocument(
            json.decodeFromString<AttemptCheckpoint>(partialAttempt.checkpoint).artifactId,
            complete.source,
            complete.config,
            partialAttempt.createdAt,
        ).copy(scope = TranscriptScope(requestedDurationMs = 1_000, technicallyComplete = false, missingChunks = listOf(0)))
        insertArtifact(complete.job, partialAttempt, partialDocument)
        dao.updateJob(complete.job.copy(state = ExecutionState.FINISHED, outcome = app.sourcescribe.core.Outcome.PARTIAL_SUCCESS))
        val retiredAudio = writeAttemptFile(complete.attempt.id, "audio-0.mp3")
        val retiredNormalized = writeAttemptFile(complete.attempt.id, "normalized.json")
        val partialAudio = writeAttemptFile(partialAttempt.id, "audio-0.mp3")
        val partialResponse = writeAttemptFile(partialAttempt.id, "responses/paid.json")

        coordinator.recover()

        assertFalse(retiredAudio.exists())
        assertFalse(retiredNormalized.exists())
        assertFalse(attemptPath(complete.attempt.id).exists())
        assertTrue(partialAudio.isFile)
        assertTrue(partialResponse.isFile)
        assertEquals(2, dao.artifacts(complete.job.id).size)
        assertEquals(0, providerRequests())
    }

    private fun <T> withFixture(block: suspend PipelineFixture.() -> T): T {
        val fixture = PipelineFixture(InstrumentationRegistry.getInstrumentation().targetContext)
        return try {
            runBlocking { fixture.block() }
        } finally {
            fixture.close()
        }
    }

    private class PipelineFixture(private val base: Context) {
        private val root = File(base.cacheDir, "app-pipeline-${UUID.randomUUID()}").also { check(it.mkdirs()) }
        private val context = IsolatedContext(base, root)
        private val runtimeLink = File(context.noBackupFilesDir, "youtubedl-android")
        private val workManager = ensureWorkManager(base)
        private val database = Room.inMemoryDatabaseBuilder(context, SourceScribeDatabase::class.java).build()
        private val jobIds = mutableSetOf<String>()
        val dao = database.records()
        private val settings = SettingsStore(context)
        private val runtime = NativeRuntime(context)
        private val extractor = ExtractorEngine(runtime)
        private val engines = EngineUpdateManager(context, runtime)
        val artifacts = ArtifactFiles(File(context.filesDir, "artifacts"))
        private val exports = ExportStore(context, dao, artifacts)
        private val notifications = JobNotifications(context)
        private val storage = StorageBudget(context, settings)
        private val credentials = CredentialStore(context)
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
        val coordinator = JobCoordinator(
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

        suspend fun seedCaption(
            state: ExecutionState = ExecutionState.QUEUED,
            leaseOwner: String? = null,
            error: String? = null,
            config: JobConfig = captionConfig(),
        ): CaptionSeed {
            val jobId = UUID.randomUUID().toString()
            val attemptId = UUID.randomUUID().toString()
            val artifactId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val caption = CaptionTrack(
                id = "fixture-caption",
                sourceVideoId = SOURCE_VIDEO_ID,
                language = "en",
                name = "Fixture captions",
                format = "vtt",
                generation = Generation.UPLOADER_PROVIDED,
                translation = Translation.NONE,
                evidence = "fixture",
            )
            val checkpoint = AttemptCheckpoint(
                artifactId = artifactId,
                artifactCreatedAt = now,
                source = source,
                caption = caption,
                rawExtension = caption.format,
            )
            val attempt = AttemptRow(
                id = attemptId,
                jobId = jobId,
                branch = Branch.CAPTIONS,
                number = 1,
                createdAt = now,
                state = state,
                phase = Phase.PERSIST,
                checkpoint = json.encodeToString(checkpoint),
                error = error,
                leaseOwner = leaseOwner,
                leaseUntil = if (leaseOwner == null) 0 else now + LEASE_MS,
            )
            val job = JobRow(jobId, source.id, json.encodeToString(config), now)
            dao.createJob(SourceRow(source.id, json.encodeToString(source), "Fixture source"), job, listOf(attempt))
            jobIds += jobId
            val document = document(artifactId, config, now)
            writeNormalized(attemptId, document)
            return CaptionSeed(job, attempt, source, config, artifactId, document)
        }

        suspend fun seedCaptionFailure(config: JobConfig, failure: CaptionFailure): AttemptSeed {
            prepareNativeRuntime()
            val engineId = installCaptionFixtureEngine(failure)
            val jobId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val caption = AttemptRow(
                id = UUID.randomUUID().toString(),
                jobId = jobId,
                branch = Branch.CAPTIONS,
                number = 1,
                createdAt = now,
                checkpoint = json.encodeToString(AttemptCheckpoint(source = source)),
                engineId = engineId,
            )
            val attempts = mutableListOf(caption)
            if (config.mode == AcquisitionMode.BOTH) {
                attempts += AttemptRow(
                    id = UUID.randomUUID().toString(),
                    jobId = jobId,
                    branch = Branch.STT,
                    number = 1,
                    createdAt = now,
                    checkpoint = json.encodeToString(AttemptCheckpoint(source = source)),
                    engineId = engineId,
                )
            }
            val job = JobRow(jobId, source.id, json.encodeToString(config), now)
            dao.createJob(SourceRow(source.id, json.encodeToString(source), "Fixture source"), job, attempts)
            jobIds += jobId
            return AttemptSeed(job, caption)
        }

        suspend fun seedBoth(): BothSeed {
            val config = bothConfig()
            val caption = seedCaption(config = config)
            val stt = AttemptRow(
                id = UUID.randomUUID().toString(),
                jobId = caption.job.id,
                branch = Branch.STT,
                number = 1,
                createdAt = caption.job.createdAt,
                state = ExecutionState.QUEUED,
                phase = Phase.RESOLVE,
                checkpoint = json.encodeToString(AttemptCheckpoint(source = caption.source)),
            )
            dao.insertAttempt(stt)
            return BothSeed(
                job = caption.job,
                caption = caption.caption,
                stt = stt,
                captionArtifactId = caption.artifactId,
                captionDocument = caption.document,
            )
        }

        suspend fun seedWaitingRemote(): AttemptSeed {
            val jobId = UUID.randomUUID().toString()
            val attemptId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val localSource = Source(
                id = "fixture-local-source",
                kind = SourceKind.LOCAL_AUDIO,
                fileName = "fixture.wav",
                mimeType = "audio/wav",
                fileBytes = 1_000,
                durationMs = 1_000,
            )
            val config = sttConfig()
            val attempt = AttemptRow(
                id = attemptId,
                jobId = jobId,
                branch = Branch.STT,
                number = 1,
                createdAt = now,
                state = ExecutionState.WAITING_REMOTE,
                phase = Phase.RETRIEVE,
                checkpoint = json.encodeToString(AttemptCheckpoint(source = localSource)),
                engineId = null,
                leaseOwner = "stale-owner",
                leaseUntil = now + LEASE_MS,
            )
            val job = JobRow(
                jobId,
                localSource.id,
                json.encodeToString(config),
                now,
                state = ExecutionState.WAITING_REMOTE,
            )
            dao.createJob(
                SourceRow(localSource.id, json.encodeToString(localSource), "Fixture local source"),
                job,
                listOf(attempt),
            )
            jobIds += jobId
            return AttemptSeed(job, attempt)
        }

        suspend fun seedLocalJob(
            state: ExecutionState = ExecutionState.QUEUED,
            phase: Phase = Phase.RESOLVE,
            outcome: app.sourcescribe.core.Outcome = app.sourcescribe.core.Outcome.NONE,
            config: JobConfig = sttConfig(),
        ): LocalSeed {
            val jobId = UUID.randomUUID().toString()
            val attemptId = UUID.randomUUID().toString()
            val artifactId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val audioBytes = "fixture-local-audio-$jobId".toByteArray(Charsets.UTF_8)
            val hash = sha256(audioBytes)
            val source = Source(
                id = "local:$hash",
                kind = SourceKind.LOCAL_AUDIO,
                contentHash = hash,
                title = "Fixture local source",
                fileName = "fixture.wav",
                mimeType = "audio/wav",
                fileBytes = audioBytes.size.toLong(),
                durationMs = 1_000,
            )
            val audioFile = File(importsDirectory(), "$hash.audio").also { it.writeBytes(audioBytes) }
            val checkpoint = AttemptCheckpoint(
                artifactId = artifactId,
                artifactCreatedAt = now,
                source = source,
            )
            val attempt = AttemptRow(
                id = attemptId,
                jobId = jobId,
                branch = Branch.STT,
                number = 1,
                createdAt = now,
                state = state,
                phase = phase,
                outcome = outcome,
                checkpoint = json.encodeToString(checkpoint),
            )
            val job = JobRow(
                id = jobId,
                sourceId = source.id,
                config = json.encodeToString(config),
                createdAt = now,
                state = state,
                outcome = outcome,
            )
            dao.createJob(
                SourceRow(source.id, json.encodeToString(source), requireNotNull(source.title), audioFile.absolutePath),
                job,
                listOf(attempt),
            )
            jobIds += jobId
            return LocalSeed(job, attempt, source, config, audioFile, artifactId)
        }

        suspend fun insertArtifact(job: JobRow, attempt: AttemptRow, document: TranscriptDocument) {
            val stored = artifacts.write(document)
            dao.insertArtifact(
                ArtifactRow(
                    id = document.artifactId,
                    jobId = job.id,
                    attemptId = attempt.id,
                    branch = attempt.branch,
                    createdAt = document.createdAt,
                    sha256 = stored.sha256,
                    bytes = stored.bytes,
                    language = document.language,
                    providerModel = document.provenance.reportedModel,
                    complete = document.scope.technicallyComplete,
                    warningCount = document.warnings.size,
                ),
            )
        }

        private fun writeNormalized(attemptId: String, document: TranscriptDocument) {
            writeAttemptFile(attemptId, "normalized.json", json.encodeToString(document))
        }

        fun restartedCoordinator() = JobCoordinator(
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

        fun attemptPath(attemptId: String): File = File(context.noBackupFilesDir, "attempts/$attemptId")

        fun writeAttemptFile(attemptId: String, name: String, content: String = "fixture") =
            File(attemptPath(attemptId), name).also { file ->
                check(file.parentFile?.mkdirs() == true || file.parentFile?.isDirectory == true)
                file.writeText(content)
            }

        fun importsDirectory(): File = File(context.noBackupFilesDir, "imports").also { check(it.mkdirs()) }

        fun providerRequests(): Int = providerGuard.requestCount

        // WorkInfo 2.11.2 omits request input; this test-only library-group access reads the
        // WorkSpec that WorkManager actually persisted instead of inspecting the request builder.
        @SuppressLint("RestrictedApi")
        fun persistedWorkInput(uniqueName: String): Data {
            val info = workManager.getWorkInfosForUniqueWork(uniqueName)
                .get(30, TimeUnit.SECONDS)
                .single()
            val implementation = workManager as WorkManagerImpl
            return requireNotNull(
                implementation.workDatabase.workSpecDao().getWorkSpec(info.id.toString()),
            ).input
        }

        suspend fun prepareNativeRuntime() {
            NativeRuntime(base).initialize()
            val actualRuntime = File(base.noBackupFilesDir, runtimeLink.name)
            check(actualRuntime.isDirectory)
            Files.createSymbolicLink(runtimeLink.toPath(), actualRuntime.toPath())
        }

        private fun installCaptionFixtureEngine(failure: CaptionFailure): String {
            val script = """
                # SourceScribe T05 Android instrumentation fixture; never packaged in production.
                import json
                import pathlib
                import sys

                video_id = "$SOURCE_VIDEO_ID"
                if "--dump-single-json" in sys.argv:
                    print(json.dumps({
                        "id": video_id,
                        "title": "T05 fixture source",
                        "duration": 1,
                        "language": "en",
                        "subtitles": {"en": [{
                            "ext": "vtt",
                            "name": "English",
                            "url": f"https://www.youtube.com/api/timedtext?v={video_id}&lang=en",
                        }]},
                    }))
                    sys.exit(0)
                if "--load-info-json" in sys.argv:
                    if "${failure.name}" == "RATE_LIMIT":
                        sys.stderr.write("HTTP Error 429: Too Many Requests")
                        sys.exit(1)
                    info = pathlib.Path(sys.argv[sys.argv.index("--load-info-json") + 1])
                    (info.parent / f"{video_id}.en.vtt").write_text("not a WEBVTT document", encoding="utf-8")
                    print(f"SS_SOURCE_ID={video_id}")
                    sys.exit(0)
                sys.exit(64)
            """.trimIndent().toByteArray(Charsets.UTF_8)
            val id = sha256(script)
            val enginesDirectory = File(context.noBackupFilesDir, "engines").also { check(it.mkdirs()) }
            val slot = File(enginesDirectory, id).also { check(it.mkdirs()) }
            File(slot, "yt-dlp").writeBytes(script)
            val installation = JSONObject()
                .put("id", id)
                .put("version", "t05-fixture")
                .put("ejsVersion", "t05-fixture")
                .put("channel", "STABLE")
                .put("sha256", id)
                .put("healthy", true)
                .put("bundled", false)
            File(enginesDirectory, "state.json").writeText(
                JSONObject()
                    .put("active", id)
                    .put("previous", JSONObject.NULL)
                    .put("healthy", JSONArray().put(id))
                    .put("installations", JSONArray().put(installation))
                    .put("lastCheckedMs", 0)
                    .put("nextAllowedMs", 0)
                    .toString(),
            )
            return id
        }

        fun close() {
            jobIds.forEach { workManager.cancelAllWorkByTag("job:$it") }
            database.close()
            if (Files.isSymbolicLink(runtimeLink.toPath())) Files.delete(runtimeLink.toPath())
            root.deleteRecursively()
        }

        private val source = Source(
            id = "fixture-youtube-source",
            kind = SourceKind.YOUTUBE,
            canonicalUrl = "https://www.youtube.com/watch?v=$SOURCE_VIDEO_ID",
            videoId = SOURCE_VIDEO_ID,
            title = "Fixture source",
            channel = "Fixture channel",
            durationMs = 1_000,
            publishedDate = "2026-09-07",
            thumbnailUrl = "https://i.ytimg.com/vi/$SOURCE_VIDEO_ID/hqdefault.jpg",
            originalLanguage = "en",
        )
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
            throw AssertionError("provider HTTP is forbidden in AppPipelineTest")
        }
        val requestCount: Int get() = requests.get()
    }

    private data class CaptionSeed(
        val job: JobRow,
        val caption: AttemptRow,
        val source: Source,
        val config: JobConfig,
        val artifactId: String,
        val document: TranscriptDocument,
    )

    private data class BothSeed(
        val job: JobRow,
        val caption: AttemptRow,
        val stt: AttemptRow,
        val captionArtifactId: String,
        val captionDocument: TranscriptDocument,
    )

    private data class AttemptSeed(
        val job: JobRow,
        val attempt: AttemptRow,
    )

    private enum class CaptionFailure { RATE_LIMIT, MALFORMED_VTT }

    private data class LocalSeed(
        val job: JobRow,
        val attempt: AttemptRow,
        val source: Source,
        val config: JobConfig,
        val audioFile: File,
        val artifactId: String,
    )

    companion object {
        private const val SOURCE_VIDEO_ID = "dQw4w9WgXcQ"
        private const val LEASE_MS = 9 * 60_000L
        private val json = Json {
            encodeDefaults = true
            explicitNulls = true
            ignoreUnknownKeys = true
        }

        private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

        private fun captionConfig() = JobConfig(
            mode = AcquisitionMode.CAPTIONS_ONLY,
            exportFormats = setOf(ExportFormat.MARKDOWN),
            audioRetention = AudioRetention.UNTIL_PERSISTED,
        )

        private fun captionThenSttConfig(fallbackOnCaptionError: Boolean) = JobConfig(
            mode = AcquisitionMode.CAPTIONS_THEN_STT,
            provider = Provider.GROQ,
            model = app.sourcescribe.core.providers.GroqAdapter.MODEL_TURBO,
            region = Region.US,
            uploadApproved = true,
            fallbackOnCaptionError = fallbackOnCaptionError,
            exportFormats = setOf(ExportFormat.MARKDOWN),
            audioRetention = AudioRetention.UNTIL_PERSISTED,
        )

        private fun bothConfig() = JobConfig(
            mode = AcquisitionMode.BOTH,
            provider = Provider.GROQ,
            model = app.sourcescribe.core.providers.GroqAdapter.MODEL_TURBO,
            region = Region.US,
            uploadApproved = true,
            exportFormats = setOf(ExportFormat.MARKDOWN),
            audioRetention = AudioRetention.UNTIL_PERSISTED,
        )

        private fun sttConfig() = JobConfig(
            mode = AcquisitionMode.STT_ONLY,
            provider = Provider.GROQ,
            model = app.sourcescribe.core.providers.GroqAdapter.MODEL_TURBO,
            region = Region.US,
            credentialId = "fixture-credential",
            uploadApproved = true,
            exportFormats = setOf(ExportFormat.MARKDOWN),
            audioRetention = AudioRetention.UNTIL_PERSISTED,
        )

        private fun document(artifactId: String, config: JobConfig, createdAt: Long) = TranscriptDocument(
            artifactId = artifactId,
            source = Source(
                id = "fixture-youtube-source",
                kind = SourceKind.YOUTUBE,
                canonicalUrl = "https://www.youtube.com/watch?v=$SOURCE_VIDEO_ID",
                videoId = SOURCE_VIDEO_ID,
                title = "Fixture source",
                channel = "Fixture channel",
                durationMs = 1_000,
                publishedDate = "2026-09-07",
                thumbnailUrl = "https://i.ytimg.com/vi/$SOURCE_VIDEO_ID/hqdefault.jpg",
                originalLanguage = "en",
            ),
            acquisition = config,
            provenance = Provenance(
                origin = Origin.YOUTUBE,
                generation = Generation.UPLOADER_PROVIDED,
                translation = Translation.NONE,
                captionTrack = CaptionTrack(
                    id = "fixture-caption",
                    sourceVideoId = SOURCE_VIDEO_ID,
                    language = "en",
                    name = "Fixture captions",
                    format = "vtt",
                    generation = Generation.UPLOADER_PROVIDED,
                    translation = Translation.NONE,
                    evidence = "fixture",
                ),
                languageEvidence = "fixture",
            ),
            language = "en",
            scope = TranscriptScope(requestedDurationMs = 1_000, technicallyComplete = true),
            segments = listOf(Segment("fixture caption", 0, 1_000)),
            createdAt = createdAt,
        )

        private fun sttDocument(
            artifactId: String,
            source: Source,
            config: JobConfig,
            createdAt: Long,
        ) = TranscriptDocument(
            artifactId = artifactId,
            source = source,
            acquisition = config,
            provenance = Provenance(
                origin = Origin.PROVIDER,
                provider = config.provider,
                requestedModel = config.model,
                reportedModel = config.model,
                languageEvidence = "fixture",
            ),
            language = "en",
            scope = TranscriptScope(requestedDurationMs = source.durationMs, technicallyComplete = true),
            segments = listOf(Segment("fixture stt transcript", 0, source.durationMs ?: 1_000)),
            createdAt = createdAt,
        )

        private fun ensureWorkManager(context: Context): WorkManager = try {
            WorkManager.getInstance(context)
        } catch (_: IllegalStateException) {
            WorkManagerTestInitHelper.initializeTestWorkManager(context)
            WorkManager.getInstance(context)
        }
    }
}
