package app.sourcescribe.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.sourcescribe.core.Branch
import app.sourcescribe.core.ExecutionState
import app.sourcescribe.core.JobConfig
import app.sourcescribe.core.SourceResolver
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun cancelledParentCannotAddFallbackAndUncertainCostsRemainVisible() = runBlocking {
        Room.inMemoryDatabaseBuilder(context, SourceScribeDatabase::class.java).build().useDatabase { database ->
            val dao = database.records()
            val initial = seed(dao)
            val claimed = requireNotNull(dao.claim(initial.id, "owner", 100, 1000, 2))
            dao.insertSubmission(SubmissionRow(UUID.randomUUID().toString(), initial.id, 0, "GROQ", "test-credential",
                "US", "input-hash", "config-hash", SubmissionState.SENDING, 1, 1))
            assertTrue(dao.cancelJob(initial.jobId))
            val stopped = requireNotNull(dao.attempt(initial.id))
            assertEquals(ExecutionState.SUBMISSION_UNCERTAIN, stopped.state)
            assertEquals("REMOTE_MAY_CONTINUE", stopped.error)
            val child = AttemptRow(fallbackAttemptId(initial.id), initial.jobId, Branch.STT, 1, 2)
            assertFalse(dao.insertFallback(claimed, "owner", child, now = 100))
            assertEquals(1, dao.attempts(initial.jobId).size)
            assertNull(dao.claim(initial.id, "new", 1100, 2000, 2))
        }
    }

    @Test
    fun cancellingFinishedJobIsNoOpAndFallbackIsInsertedOnce() = runBlocking {
        Room.inMemoryDatabaseBuilder(context, SourceScribeDatabase::class.java).build().useDatabase { database ->
            val dao = database.records()
            val initial = seed(dao)
            val claimed = requireNotNull(dao.claim(initial.id, "owner", 100, 1000, 2))
            val child = AttemptRow(fallbackAttemptId(initial.id), initial.jobId, Branch.STT, 1, 2)
            assertTrue(dao.insertFallback(claimed, "owner", child, now = 100))
            assertFalse(dao.insertFallback(claimed, "owner", child.copy(id = UUID.randomUUID().toString()), now = 100))
            // A new explicit caption attempt must still get its own fallback after a clock rollback.
            val retried = claimed.copy(id = UUID.randomUUID().toString(), number = 2, createdAt = child.createdAt - 1)
            dao.insertAttempt(retried)
            val secondChild = child.copy(id = fallbackAttemptId(retried.id), number = 2, createdAt = retried.createdAt)
            assertTrue(dao.insertFallback(retried, "owner", secondChild, now = 100))
            assertFalse(dao.insertFallback(retried, "owner", secondChild, now = 100))
            dao.attempts(initial.jobId).forEach { dao.updateAttempt(it.copy(state = ExecutionState.FINISHED)) }
            val before = requireNotNull(dao.job(initial.jobId))
            assertFalse(dao.cancelJob(initial.jobId))
            assertEquals(before, dao.job(initial.jobId))
        }
    }

    @Test
    fun concurrentClaimsRespectJobLimitAndDoNotClaimSameAttemptTwice() = runBlocking {
        Room.inMemoryDatabaseBuilder(context, SourceScribeDatabase::class.java).build().useDatabase { database ->
            val dao = database.records()
            val attempts = List(4) { seed(dao) }
            val claims = coroutineScope {
                (0..15).map { index ->
                    async { dao.claim(attempts[index % 4].id, "owner-$index", 100, 1000, 2) }
                }.awaitAll().filterNotNull()
            }
            assertEquals(2, claims.size)
            assertEquals(2, claims.map { it.id }.distinct().size)
            assertEquals(2, dao.interrupted().size)
        }
    }

    @Test
    fun staleOwnerCannotOverwriteRecoveredStateOrReleaseAnotherOwner() = runBlocking {
        Room.inMemoryDatabaseBuilder(context, SourceScribeDatabase::class.java).build().useDatabase { database ->
            val dao = database.records()
            val initial = seed(dao)
            val old = requireNotNull(dao.claim(initial.id, "old", 100, 200, 2))
            dao.updateAttempt(old.copy(state = ExecutionState.QUEUED, leaseOwner = null, leaseUntil = 0))
            val current = requireNotNull(dao.claim(initial.id, "new", 300, 1000, 2))
            assertFalse(dao.saveClaimed(old.copy(state = ExecutionState.FINISHED), "old"))
            dao.release(initial.id, "old")
            assertEquals(current, dao.attempt(initial.id))
        }
    }

    @Test
    fun cpuLeaseAndCancelledJobAreIndependentOfOtherWork() = runBlocking {
        Room.inMemoryDatabaseBuilder(context, SourceScribeDatabase::class.java).build().useDatabase { database ->
            val dao = database.records()
            val first = seed(dao)
            val second = seed(dao)
            val job = requireNotNull(dao.job(first.jobId))
            dao.updateJob(job.copy(cancelRequested = true))
            assertNull(dao.claim(first.id, "first", 100, 1000, 2))
            assertNotNull(dao.claim(second.id, "second", 100, 1000, 2))
            assertTrue(dao.claimResource("audio", "second", 100, 1000))
            assertFalse(dao.claimResource("audio", "third", 100, 1000))
            dao.releaseResources("first")
            assertFalse(dao.claimResource("audio", "third", 100, 1000))
            dao.releaseResources("second")
            assertTrue(dao.claimResource("audio", "third", 100, 1000))
        }
    }

    @Test
    fun fileDatabaseRetainsImmutableSnapshotOnReopen() = runBlocking {
        val name = "test-${UUID.randomUUID()}.db"
        try {
            val first = Room.databaseBuilder(context, SourceScribeDatabase::class.java, name).build()
            val row = try { seed(first.records()) } finally { first.close() }
            Room.databaseBuilder(context, SourceScribeDatabase::class.java, name).build().useDatabase { reopened ->
                val job = requireNotNull(reopened.records().job(row.jobId))
                assertEquals(JobConfig(), Json.decodeFromString<JobConfig>(job.config))
                assertEquals(row, reopened.records().attempt(row.id))
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    private suspend fun seed(dao: SourceScribeDao): AttemptRow {
        val source = SourceResolver.youtube("https://youtu.be/BaW_jenozKc")
        val job = JobRow(UUID.randomUUID().toString(), source.id, Json.encodeToString(JobConfig()), 1)
        val attempt = AttemptRow(UUID.randomUUID().toString(), job.id, Branch.CAPTIONS, 1, 1)
        dao.createJob(SourceRow(source.id, Json.encodeToString(source), "Public test source"), job, listOf(attempt))
        return attempt
    }
}

private inline fun <T> SourceScribeDatabase.useDatabase(block: (SourceScribeDatabase) -> T): T =
    try { block(this) } finally { close() }
