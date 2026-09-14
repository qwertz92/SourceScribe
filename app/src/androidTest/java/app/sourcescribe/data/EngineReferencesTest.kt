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
    fun unfinishedAttemptsKeepTheirEnginesAndAPartialResultKeepsNone() = runBlocking {
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
            val uncertain = attempt("c", Branch.STT, 1, ExecutionState.SUBMISSION_UNCERTAIN, "E3")
            val partial = attempt("d", Branch.STT, 1, ExecutionState.FINISHED, "E4", Outcome.PARTIAL_SUCCESS)
            val done = attempt("e", Branch.STT, 1, ExecutionState.FINISHED, "E5", Outcome.SUCCESS)
            val cancelled = attempt("f", Branch.CAPTIONS, 1, ExecutionState.CANCELLED, "E6", Outcome.CANCELLED)
            val localAudio = attempt("g", Branch.STT, 1, ExecutionState.QUEUED, null)

            val source = SourceRow("fixture-source", "{}", "Fixture source")
            val attempts = listOf(waiting, running, uncertain, partial, done, cancelled, localAudio)
            for ((job, rows) in attempts.groupBy { it.jobId }) dao.createJob(source, JobRow(job, source.id, "{}", 1), rows)
            listOf(artifact(partial, false), artifact(done, true)).forEach { dao.insertArtifact(it) }

            // Read back through the same call the app's manager is wired to, not from the list above.
            val references = engineReferences(dao)

            // Waiting for the reader or on a submission whose fate is unknown counts as unfinished just as running
            // does, and a local file pins no engine at all. The partial result keeps none: asking for its missing
            // chunks again never runs an engine (SttMissingRetryTest).
            assertEquals(setOf("E1", "E2", "E3"), references.inUse)
        } finally {
            database.close()
        }
    }
}
