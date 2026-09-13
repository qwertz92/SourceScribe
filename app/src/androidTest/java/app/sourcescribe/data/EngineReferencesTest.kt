package app.sourcescribe.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.sourcescribe.core.Branch
import app.sourcescribe.core.ExecutionState
import app.sourcescribe.core.Outcome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EngineReferencesTest {
    @Test
    fun unfinishedAttemptsAndContinuablePartialResultsKeepTheirEngines() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, SourceScribeDatabase::class.java).build()
        try {
            val dao = database.records()
            fun attempt(job: String, branch: Branch, number: Int, state: ExecutionState, engine: String?, outcome: Outcome = Outcome.NONE) =
                AttemptRow("$job-$branch-$number", job, branch, number, number.toLong(), state = state, outcome = outcome, engineId = engine)
            fun artifact(of: AttemptRow, complete: Boolean?) =
                ArtifactRow("artifact-${of.id}", of.jobId, of.id, of.branch, 1, "0".repeat(64), 1, null, null, complete, 0)

            val waiting = attempt("a", Branch.CAPTIONS, 1, ExecutionState.WAITING_USER, "E1")
            val running = attempt("b", Branch.STT, 1, ExecutionState.RUNNING, "E2")
            val partial = attempt("c", Branch.STT, 1, ExecutionState.FINISHED, "E3", Outcome.PARTIAL_SUCCESS)
            val retriedPartial = attempt("d", Branch.STT, 1, ExecutionState.FINISHED, "E4", Outcome.PARTIAL_SUCCESS)
            val completedRetry = attempt("d", Branch.STT, 2, ExecutionState.FINISHED, "E4", Outcome.SUCCESS)
            val done = attempt("e", Branch.STT, 1, ExecutionState.FINISHED, "E5", Outcome.SUCCESS)
            val cancelled = attempt("f", Branch.CAPTIONS, 1, ExecutionState.CANCELLED, "E6", Outcome.CANCELLED)
            val captionsPartial = attempt("g", Branch.CAPTIONS, 1, ExecutionState.FINISHED, "E7", Outcome.PARTIAL_SUCCESS)
            val localAudio = attempt("h", Branch.STT, 1, ExecutionState.QUEUED, null)
            val partialBehindCancelledRetry = attempt("i", Branch.STT, 1, ExecutionState.FINISHED, "E8", Outcome.PARTIAL_SUCCESS)
            val cancelledRetry = attempt("i", Branch.STT, 2, ExecutionState.CANCELLED, "E8", Outcome.CANCELLED)

            val source = SourceRow("fixture-source", "{}", "Fixture source")
            val attempts = listOf(waiting, running, partial, retriedPartial, completedRetry, done, cancelled, captionsPartial,
                localAudio, partialBehindCancelledRetry, cancelledRetry)
            for ((job, rows) in attempts.groupBy { it.jobId }) dao.createJob(source, JobRow(job, source.id, "{}", 1), rows)
            listOf(artifact(partial, false), artifact(retriedPartial, false), artifact(completedRetry, true), artifact(done, true),
                artifact(captionsPartial, false), artifact(partialBehindCancelledRetry, false)).forEach { dao.insertArtifact(it) }

            // Read back through the same call the app's manager is wired to, not from the lists above.
            val references = engineReferences(dao)

            // Waiting for the reader counts as unfinished just as running does; a local file pins no engine at all.
            assertEquals(setOf("E1", "E2"), references.inUse)
            // Only the latest speech-to-text attempt of a job counts, and only while its own result is partial. Job d
            // was completed by its retry; job i's latest attempt has no result, so asking for the missing part again
            // would start over on the active engine; and a caption result is never continued chunk by chunk.
            assertEquals(setOf("E3"), references.retainedForRetry)
        } finally {
            database.close()
        }
    }
}
