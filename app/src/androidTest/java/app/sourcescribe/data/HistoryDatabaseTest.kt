package app.sourcescribe.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.sourcescribe.core.Branch
import app.sourcescribe.core.ExecutionState
import app.sourcescribe.core.ExportState
import app.sourcescribe.core.JobConfig
import app.sourcescribe.core.Outcome
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryDatabaseTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun claimRejectsDeletionRequestedJobEvenWhenCancellationIsFalse() = runBlocking {
        Room.inMemoryDatabaseBuilder(context, SourceScribeDatabase::class.java).build().useDatabase { database ->
            val dao = database.records()
            val seeded = seed(dao)
            dao.updateJob(requireNotNull(dao.job(seeded.job.id)).copy(deleteRequested = true))

            assertNull(dao.claim(seeded.attempt.id, "owner", 100, 1_000, 1))
            val job = requireNotNull(dao.job(seeded.job.id))
            assertTrue(job.deleteRequested)
            assertFalse(job.cancelRequested)
            assertEquals(seeded.attempt, dao.attempt(seeded.attempt.id))
        }
    }

    @Test
    fun expiredOwnerCannotCommitAttemptState() = runBlocking {
        Room.inMemoryDatabaseBuilder(context, SourceScribeDatabase::class.java).build().useDatabase { database ->
            val dao = database.records()
            val seeded = seed(dao)
            val expired = seeded.attempt.copy(state = ExecutionState.RUNNING, leaseOwner = "expired", leaseUntil = 1)
            dao.updateAttempt(expired)
            assertFalse(dao.saveClaimed(expired.copy(state = ExecutionState.FINISHED), "expired"))
            assertEquals(expired, dao.attempt(expired.id))
        }
    }

    @Test
    fun requestDeletionPersistsBothFlagsAndPreservesStartedSubmissionUncertainty() = runBlocking {
        listOf(SubmissionState.SENDING, SubmissionState.UNCERTAIN).forEach { submissionState ->
            Room.inMemoryDatabaseBuilder(context, SourceScribeDatabase::class.java).build().useDatabase { database ->
                val dao = database.records()
                val seeded = seed(dao)
                val running = seeded.attempt.copy(
                    state = ExecutionState.RUNNING,
                    leaseOwner = "worker",
                    leaseUntil = 1_000,
                )
                dao.updateAttempt(running)
                val submission = SubmissionRow(
                    id = UUID.randomUUID().toString(),
                    attemptId = running.id,
                    chunkIndex = 0,
                    provider = "GROQ",
                    credentialId = "fixture-credential",
                    region = "US",
                    inputHash = "input-hash",
                    configHash = "config-hash",
                    state = submissionState,
                    createdAt = 1,
                    estimatedMicrousd = 1,
                )
                dao.insertSubmission(submission)

                assertTrue(dao.requestDeletion(seeded.job.id))

                assertEquals(
                    JobRow(
                        id = seeded.job.id,
                        sourceId = seeded.job.sourceId,
                        config = seeded.job.config,
                        createdAt = seeded.job.createdAt,
                        state = ExecutionState.CANCELLED,
                        outcome = Outcome.CANCELLED,
                        cancelRequested = true,
                        deleteRequested = true,
                    ),
                    dao.job(seeded.job.id),
                )
                assertEquals(
                    running.copy(
                        state = ExecutionState.SUBMISSION_UNCERTAIN,
                        outcome = Outcome.CANCELLED,
                        error = "REMOTE_MAY_CONTINUE",
                        leaseOwner = null,
                        leaseUntil = 0,
                    ),
                    dao.attempt(running.id),
                )
                assertEquals(submission, dao.submissions(running.id).single())
                assertEquals(seeded.job.id, dao.pendingDeletion().single().id)
            }
        }
    }

    @Test
    fun deletingOneJobRemovesAllItsRowsAndRetainsSharedSourceAndOtherJob() = runBlocking {
        Room.inMemoryDatabaseBuilder(context, SourceScribeDatabase::class.java).build().useDatabase { database ->
            val dao = database.records()
            val source = SourceRow("shared-source", "snapshot", "Shared source")
            val first = seed(dao, source, Branch.CAPTIONS)
            val second = seed(dao, source, Branch.STT)
            val firstArtifact = ArtifactRow(
                id = "artifact-${first.job.id}",
                jobId = first.job.id,
                attemptId = first.attempt.id,
                branch = Branch.CAPTIONS,
                createdAt = 2,
                sha256 = "first-sha",
                bytes = 10,
                language = "de",
                providerModel = null,
                complete = true,
                warningCount = 0,
            )
            val secondArtifact = firstArtifact.copy(
                id = "artifact-${second.job.id}",
                jobId = second.job.id,
                attemptId = second.attempt.id,
                branch = Branch.STT,
                sha256 = "second-sha",
            )
            dao.insertArtifact(firstArtifact)
            dao.insertArtifact(secondArtifact)
            val firstSubmission = submission(first.attempt.id, "submission-${first.job.id}")
            val secondSubmission = submission(second.attempt.id, "submission-${second.job.id}")
            dao.insertSubmission(firstSubmission)
            dao.insertSubmission(secondSubmission)
            val firstExport = ExportRow(
                id = "export-${first.job.id}",
                artifactId = firstArtifact.id,
                format = "MARKDOWN",
                treeUri = "content://fixture/tree",
                createdAt = 3,
                state = ExportState.EXPORTED,
                documentUri = "content://fixture/first.md",
            )
            val secondExport = firstExport.copy(
                id = "export-${second.job.id}",
                artifactId = secondArtifact.id,
                documentUri = "content://fixture/second.md",
            )
            dao.insertExport(firstExport)
            dao.insertExport(secondExport)

            assertTrue(dao.requestDeletion(first.job.id))
            dao.finishDeletion(first.job.id)

            assertNull(dao.job(first.job.id))
            assertTrue(dao.attempts(first.job.id).isEmpty())
            assertTrue(dao.artifacts(first.job.id).isEmpty())
            assertTrue(dao.submissionsForJob(first.job.id).isEmpty())
            assertTrue(dao.exports(firstArtifact.id).isEmpty())
            assertEquals(source, dao.source(source.id))
            assertEquals(1, dao.sourceReferences(source.id))
            assertEquals(second.job, dao.job(second.job.id))
            assertEquals(listOf(second.attempt), dao.attempts(second.job.id))
            assertEquals(listOf(secondArtifact), dao.artifacts(second.job.id))
            assertEquals(listOf(secondSubmission), dao.submissionsForJob(second.job.id))
            assertEquals(listOf(secondExport), dao.exports(secondArtifact.id))

            assertTrue(dao.requestDeletion(second.job.id))
            dao.finishDeletion(second.job.id)
            assertNull(dao.source(source.id))
        }
    }

    private suspend fun seed(
        dao: SourceScribeDao,
        source: SourceRow = SourceRow("source-${UUID.randomUUID()}", "snapshot", "Test source"),
        branch: Branch = Branch.CAPTIONS,
    ): Seeded {
        val job = JobRow(
            id = "job-${UUID.randomUUID()}",
            sourceId = source.id,
            config = Json.encodeToString(JobConfig()),
            createdAt = 1,
        )
        val attempt = AttemptRow(
            id = "attempt-${UUID.randomUUID()}",
            jobId = job.id,
            branch = branch,
            number = 1,
            createdAt = 1,
        )
        dao.createJob(source, job, listOf(attempt))
        return Seeded(job, attempt)
    }

    private fun submission(attemptId: String, id: String) = SubmissionRow(
        id = id,
        attemptId = attemptId,
        chunkIndex = 0,
        provider = "GROQ",
        credentialId = "fixture-credential",
        region = "US",
        inputHash = "input-hash-$id",
        configHash = "config-hash-$id",
        state = SubmissionState.RESPONSE_SAVED,
        createdAt = 1,
        estimatedMicrousd = 1,
    )

    private data class Seeded(val job: JobRow, val attempt: AttemptRow)
}

private inline fun <T> SourceScribeDatabase.useDatabase(block: (SourceScribeDatabase) -> T): T =
    try { block(this) } finally { close() }
