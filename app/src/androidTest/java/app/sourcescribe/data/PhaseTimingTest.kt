package app.sourcescribe.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.sourcescribe.core.Branch
import app.sourcescribe.core.ExecutionState
import app.sourcescribe.core.Outcome
import app.sourcescribe.core.Phase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhaseTimingTest {
    @Test
    fun recordsCompletedAndOpenAttemptOwnedSpansAndCascadeDeletesThem() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            SourceScribeDatabase::class.java,
        ).allowMainThreadQueries().build()
        try {
            val dao = database.records()
            val now = System.currentTimeMillis()
            val source = SourceRow("source", "{}", "Timing source")
            val job = JobRow("job", source.id, "{}", now, state = ExecutionState.RUNNING)
            val owner = "worker"
            val attempt = AttemptRow("attempt", job.id, Branch.STT, 1, now,
                state = ExecutionState.RUNNING, leaseOwner = owner, leaseUntil = now + 60_000L)
            dao.createJob(source, job, listOf(attempt))
            val timings = PhaseTimings(dao)

            assertEquals("result", timings.measure(attempt, owner, Phase.RESOLVE) {
                val open = dao.timingsForAttempt(attempt.id).single()
                assertEquals(Phase.RESOLVE, open.phase)
                assertNull("span is durable before its operation completes", open.elapsedMs)
                "result"
            })
            val completed = dao.timingsForAttempt(attempt.id).single()
            assertEquals(Phase.RESOLVE, completed.phase)
            assertTrue(completed.startedAt > 0L)
            assertNotNull("operation failure still closes its measured span", runCatching {
                timings.measure(attempt, owner, Phase.FETCH_CAPTIONS) { error("fixture failure") }
            }.exceptionOrNull())
            assertNotNull(dao.timingsForAttempt(attempt.id).single { it.phase == Phase.FETCH_CAPTIONS }.elapsedMs)

            val open = timings.begin(attempt, owner, Phase.DOWNLOAD_AUDIO)
            assertNull(dao.timingsForAttempt(attempt.id).single { it.phase == Phase.DOWNLOAD_AUDIO }.elapsedMs)
            assertEquals("stale owner still runs without telemetry", "ran", timings.measure(
                attempt, "stale-owner", Phase.PERSIST,
            ) { "ran" })
            assertEquals("unowned phase did not add a span", 3, dao.timingsForAttempt(attempt.id).size)

            // Cancellation can clear the lease before the operation's finally block records its elapsed time.
            dao.updateAttempt(attempt.copy(state = ExecutionState.CANCELLED, leaseOwner = null, leaseUntil = 0))
            timings.finish(open, 17L)
            assertEquals(17L, dao.timingsForAttempt(attempt.id).single { it.phase == Phase.DOWNLOAD_AUDIO }.elapsedMs)

            val finishedJob = job.transitionTo(ExecutionState.FINISHED, Outcome.SUCCESS, now + 1)
            dao.updateJob(finishedJob)
            dao.requestDeletion(job.id)
            assertEquals("deletion preserves the original terminal state and completion time",
                finishedJob.copy(cancelRequested = true, deleteRequested = true),
                dao.job(job.id))
            dao.finishDeletion(job.id)
            assertTrue("attempt deletion cascades completed and incomplete spans", dao.timingsForAttempt(attempt.id).isEmpty())
        } finally {
            database.close()
        }
    }

    @Test
    fun terminalCompletionTimeIsRecordedOnlyOnARealTransition() {
        val queued = JobRow("queued", "source", "{}", 10L)
        val finished = queued.transitionTo(ExecutionState.FINISHED, Outcome.SUCCESS, 25L)
        assertEquals(25L, finished.finishedAt)
        assertEquals(25L, finished.transitionTo(ExecutionState.FINISHED, Outcome.SUCCESS, 40L).finishedAt)

        val historical = queued.copy(state = ExecutionState.FINISHED, outcome = Outcome.SUCCESS)
        assertNull("legacy finished rows remain unknown", historical.transitionTo(ExecutionState.FINISHED, Outcome.SUCCESS, 40L).finishedAt)
        assertNull("retry clears a prior terminal time", finished.transitionTo(ExecutionState.QUEUED, Outcome.NONE, 50L).finishedAt)
    }
}
