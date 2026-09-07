package app.sourcescribe.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.file.Files
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StorageBudgetTest {
    private lateinit var root: File

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        root = File(context.cacheDir, "storage-budget-test-${UUID.randomUUID()}")
        check(root.mkdirs())
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun usageIncludesUserDataAndExcludesBundledRuntime() = runBlocking {
        val data = File(root, "data").also { check(it.mkdirs()) }
        write(File(data, "user.bin"), 3)
        write(File(data, "sourcescribe-runtime/runtime.bin"), 71)
        write(File(data, "nested/artifact.json"), 5)

        val snapshot = newBudget().snapshot()

        assertEquals(8L, snapshot.usedBytes)
    }

    @Test
    fun usageDoesNotFollowSymlinkTargets() = runBlocking {
        val data = File(root, "data").also { check(it.mkdirs()) }
        val outside = File(root, "outside.bin").also { write(it, 97) }
        Files.createSymbolicLink(File(data, "escape").toPath(), outside.toPath())

        assertEquals(0L, newBudget().snapshot().usedBytes)
    }

    @Test
    fun symlinkStorageRootFailsClosed() = runBlocking {
        val real = File(root, "real").also { check(it.mkdirs()) }
        write(File(real, "user.bin"), 1)
        val link = File(root, "data-link")
        Files.createSymbolicLink(link.toPath(), real.toPath())
        val budget = StorageBudgetBackend(
            roots = listOf(StorageBudgetRoot(link)),
            attemptsRoot = File(root, "attempts").also { check(it.mkdirs()) },
            limitProvider = { 256L * 1024L * 1024L },
            usableSpaceProvider = { Long.MAX_VALUE },
        )

        assertReason(StorageBudgetException.STORAGE_SCAN_FAILED) { budget.snapshot() }
    }

    @Test
    fun scanEntryCapFailsClosed() = runBlocking {
        val data = File(root, "data").also { check(it.mkdirs()) }
        write(File(data, "first"), 1)
        write(File(data, "second"), 1)

        assertReason(StorageBudgetException.STORAGE_SCAN_FAILED) {
            newBudget(maxEntries = 2).snapshot()
        }
    }

    @Test
    fun concurrentReservationsCannotDoubleSpendAndRunOutsideMutex() = runBlocking {
        val budget = newBudget(limit = 8L * 1024L * 1024L)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        coroutineScope {
            val first = async {
                budget.withReservation(4L * 1024L * 1024L) {
                    entered.complete(Unit)
                    release.await()
                }
            }
            entered.await()
            assertReason(StorageBudgetException.STORAGE_LIMIT) {
                budget.withReservation(4L * 1024L * 1024L) {}
            }
            assertEquals(4L * 1024L * 1024L, budget.snapshot().reservedBytes)
            release.complete(Unit)
            first.await()
        }

        assertEquals(0L, budget.snapshot().reservedBytes)
    }

    @Test
    fun reservationIsReleasedAfterExceptionAndCancellation() = runBlocking {
        val budget = newBudget()
        try {
            budget.withReservation(1L) { error("expected test failure") }
            fail("reservation block should fail")
        } catch (_: IllegalStateException) {
            // The caller failure must not strand its reservation.
        }
        assertEquals(0L, budget.snapshot().reservedBytes)

        val entered = CompletableDeferred<Unit>()
        val cancelled = launch {
            budget.withReservation(1L) {
                entered.complete(Unit)
                awaitCancellation()
            }
        }
        entered.await()
        cancelled.cancel()
        cancelled.join()

        assertEquals(0L, budget.snapshot().reservedBytes)
    }

    @Test
    fun freeSpaceMarginHasStableFailureReason() = runBlocking {
        val free = StorageBudget.FREE_SPACE_MARGIN_BYTES + StorageBudget.DATABASE_GROWTH_RESERVE_BYTES + 1L
        val budget = newBudget(freeSpace = free)

        assertReason(StorageBudgetException.DEVICE_STORAGE_LOW) {
            budget.withReservation(1L) {}
        }
    }

    @Test
    fun cleanupDeletesOnlyTheRetiredAttemptDirectory() = runBlocking {
        val budget = newBudget()
        val attemptId = UUID.randomUUID().toString()
        val attempt = File(root, "attempts/$attemptId").also { check(it.mkdirs()) }
        write(File(attempt, "source.audio"), 2)
        write(File(attempt, "responses/provider.json"), 4)
        val artifact = File(root, "artifact/transcript.json").also { write(it, 6) }

        assertTrue(budget.deleteAttemptFiles(attemptId))
        assertFalse(attempt.exists())
        assertTrue(artifact.isFile)
    }

    @Test
    fun keepAudioRetainsOnlyDirectAudioAndLeavesSiblingBehindSymlink() = runBlocking {
        val budget = newBudget()
        val attemptId = UUID.randomUUID().toString()
        val siblingId = UUID.randomUUID().toString()
        val attempt = File(root, "attempts/$attemptId").also { check(it.mkdirs()) }
        val sibling = File(root, "attempts/$siblingId").also { check(it.mkdirs()) }
        write(File(sibling, "sibling.json"), 11)
        write(File(attempt, "source.audio"), 2)
        write(File(attempt, "audio-0.mp3"), 3)
        write(File(attempt, "audio-other.mp3"), 5)
        write(File(attempt, "nested/audio-1.mp3"), 7)
        write(File(attempt, "normalized.json"), 13)
        write(File(attempt, "raw.provider.json"), 17)
        write(File(attempt, "responses/provider.json"), 19)
        val siblingLink = File(attempt, "sibling-link")
        Files.createSymbolicLink(siblingLink.toPath(), sibling.toPath())
        val audioNamedLink = File(attempt, "audio-99.mp3")
        Files.createSymbolicLink(audioNamedLink.toPath(), sibling.toPath())

        assertTrue(budget.deleteAttemptFiles(attemptId, keepAudio = true))
        assertTrue(File(attempt, "source.audio").isFile)
        assertTrue(File(attempt, "audio-0.mp3").isFile)
        assertFalse(File(attempt, "audio-other.mp3").exists())
        assertFalse(File(attempt, "nested").exists())
        assertFalse(File(attempt, "normalized.json").exists())
        assertFalse(File(attempt, "raw.provider.json").exists())
        assertFalse(File(attempt, "responses").exists())
        assertFalse(siblingLink.exists() || Files.isSymbolicLink(siblingLink.toPath()))
        assertFalse(audioNamedLink.exists() || Files.isSymbolicLink(audioNamedLink.toPath()))
        assertTrue(File(sibling, "sibling.json").isFile)
    }

    @Test
    fun cleanupRejectsInvalidIdsAndSymlinkTargets() = runBlocking {
        val budget = newBudget()
        assertReason(StorageBudgetException.INVALID_ATTEMPT_ID) {
            budget.deleteAttemptFiles("../outside")
        }

        val attemptId = UUID.randomUUID().toString()
        val outside = File(root, "outside-attempt").also {
            check(it.mkdirs())
            write(File(it, "keep.bin"), 9)
        }
        Files.createSymbolicLink(
            File(root, "attempts/$attemptId").toPath(),
            outside.toPath(),
        )
        assertReason(StorageBudgetException.CLEANUP_FAILED) {
            budget.deleteAttemptFiles(attemptId)
        }
        assertTrue(File(outside, "keep.bin").isFile)
    }

    private fun newBudget(
        limit: Long = 256L * 1024L * 1024L,
        maxEntries: Int = StorageBudget.MAX_SCAN_ENTRIES,
        freeSpace: Long = Long.MAX_VALUE,
    ): StorageBudgetBackend {
        val data = File(root, "data").also { if (!it.exists()) check(it.mkdirs()) }
        val attempts = File(root, "attempts").also { if (!it.exists()) check(it.mkdirs()) }
        return StorageBudgetBackend(
            roots = listOf(
                StorageBudgetRoot(
                    data,
                    excludedTopLevelNames = setOf("sourcescribe-runtime"),
                ),
            ),
            attemptsRoot = attempts,
            limitProvider = { limit },
            usableSpaceProvider = { freeSpace },
            maxEntries = maxEntries,
        )
    }

    private fun write(file: File, bytes: Int) {
        file.parentFile?.let { check(it.isDirectory || it.mkdirs()) }
        file.writeBytes(ByteArray(bytes))
    }

    private suspend fun assertReason(reason: String, operation: suspend () -> Unit) {
        try {
            operation()
            fail("Expected storage failure $reason")
        } catch (failure: StorageBudgetException) {
            assertEquals(reason, failure.reason)
        }
    }
}
