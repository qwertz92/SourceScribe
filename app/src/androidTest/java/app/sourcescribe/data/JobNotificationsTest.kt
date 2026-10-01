package app.sourcescribe.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.sourcescribe.MainActivity
import app.sourcescribe.core.AcquisitionMode
import app.sourcescribe.core.Branch
import app.sourcescribe.core.ExecutionState
import app.sourcescribe.core.JobConfig
import app.sourcescribe.core.Outcome
import app.sourcescribe.core.Phase
import app.sourcescribe.core.Source
import app.sourcescribe.core.SourceKind
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JobNotificationsTest {
    @Test
    fun routineAndCancelledStatesNeverCreateNotifications() {
        val states = listOf(
            ExecutionState.QUEUED,
            ExecutionState.RUNNING,
            ExecutionState.WAITING_NETWORK,
            ExecutionState.WAITING_RATE_LIMIT,
            ExecutionState.WAITING_REMOTE,
            ExecutionState.CANCELLED,
        )

        states.forEach { state ->
            assertNull(JobNotificationPolicy.key(job(state), emptyList(), foreground = false))
        }
        assertNull(JobNotificationPolicy.key(job(ExecutionState.FINISHED, Outcome.CANCELLED), emptyList(), false))
        assertNull(JobNotificationPolicy.key(job(ExecutionState.FINISHED), emptyList(), foreground = true))
    }

    @Test
    fun terminalOutcomesNotifyOnlyInBackground() {
        listOf(Outcome.SUCCESS, Outcome.SUCCESS_WITH_WARNINGS, Outcome.PARTIAL_SUCCESS, Outcome.FAILED).forEach { outcome ->
            val key = JobNotificationPolicy.key(job(ExecutionState.FINISHED, outcome), emptyList(), foreground = false)
            assertNotNull(key)
            assertEquals(outcome, key?.outcome)
        }
    }

    @Test
    fun actionableErrorsAreIncludedAndRepeatedSummariesAreDeduplicated() {
        val waiting = attempt(ExecutionState.WAITING_USER, "AUTHENTICATION")
        val initial = requireNotNull(JobNotificationPolicy.key(job(ExecutionState.WAITING_USER), listOf(waiting), false))
        assertEquals(ExecutionState.WAITING_USER, initial.state)

        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("notification-test-${java.util.UUID.randomUUID()}", android.content.Context.MODE_PRIVATE)
        val deduplicator = NotificationDeduplicator(preferences)
        assertTrue(deduplicator.needsNotification("job", initial))
        assertTrue(NotificationDeduplicator(preferences).needsNotification("job", initial))
        deduplicator.recordHandled("job", initial)
        assertFalse(deduplicator.needsNotification("job", initial))

        val changed = requireNotNull(JobNotificationPolicy.key(
            job(ExecutionState.WAITING_USER), listOf(attempt(ExecutionState.WAITING_USER, "ACCESS_DENIED")), false,
        ))
        assertFalse(deduplicator.needsNotification("job", changed))
        assertFalse(NotificationDeduplicator(preferences).needsNotification("job", changed))
        deduplicator.forget("job")
        assertTrue(deduplicator.needsNotification("job", changed))
        preferences.edit { clear() }
    }

    @Test
    fun submissionUncertaintyFromAnAttemptRemainsActionable() {
        val key = JobNotificationPolicy.key(
            job(ExecutionState.RUNNING), listOf(attempt(ExecutionState.SUBMISSION_UNCERTAIN, "SUBMISSION_UNCERTAIN")), false,
        )
        assertEquals(ExecutionState.SUBMISSION_UNCERTAIN, key?.state)
    }

    @Test
    fun foregroundErrorsRemainActionableAndSupersededAttemptsDoNotNotify() {
        val waiting = attempt(ExecutionState.WAITING_USER, "AUTHENTICATION")
        assertNotNull(JobNotificationPolicy.key(job(ExecutionState.WAITING_USER), listOf(waiting), true))
        assertNull(JobNotificationPolicy.key(job(ExecutionState.RUNNING), listOf(waiting,
            waiting.copy(id = "new", number = 2, state = ExecutionState.RUNNING, error = null)), false))
    }

    @Test
    fun actualNotificationsAreQuietDuringWorkAndStayDeduplicatedAfterRestart() {
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        requireNotificationPermission(context)
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        val id = "notification-${java.util.UUID.randomUUID()}"
        val preferences = context.getSharedPreferences("job_notification_events", android.content.Context.MODE_PRIVATE)
        val notifications = JobNotifications(context)
        fun posted(): Boolean = manager.activeNotifications.any { it.tag == id }
        fun awaitPosted() {
            val deadline = System.nanoTime() + 5_000_000_000L
            while (!posted() && System.nanoTime() < deadline) Thread.sleep(20)
            assertTrue("Expected one actionable/completion notification", posted())
        }
        try {
            notifications.setAppForeground(false)
            notifications.update(job(ExecutionState.RUNNING).copy(id = id), emptyList())
            assertFalse(posted())
            notifications.setAppForeground(true)
            val finished = job(ExecutionState.FINISHED, Outcome.SUCCESS).copy(id = id)
            notifications.update(finished, emptyList())
            assertFalse(posted())
            // Leaving the foreground or recovering an old completion must not replay it.
            JobNotifications(context).update(finished, emptyList(), notifyCompletion = false)
            assertFalse(posted())
            val waiting = job(ExecutionState.WAITING_USER).copy(id = id)
            notifications.update(waiting, emptyList())
            awaitPosted()
            manager.cancel(id, 1) // User dismissal must not erase the durable delivered-event signature.
            val cancellationDeadline = System.nanoTime() + 5_000_000_000L
            while (posted() && System.nanoTime() < cancellationDeadline) Thread.sleep(20)
            assertFalse(posted())
            JobNotifications(context).update(waiting, emptyList())
            assertFalse(posted())
            val active = job(ExecutionState.RUNNING).copy(id = id)
            notifications.setAppForeground(false)
            notifications.update(active, emptyList())
            notifications.update(finished, emptyList())
            awaitPosted()
            assertEquals(1, manager.activeNotifications.count { it.tag == id })
        } finally {
            manager.cancel(id, 1)
            preferences.edit { remove(id) }
        }
    }

    @Test
    fun completionAfterActivityStopUsesTheCurrentForegroundState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        requireNotificationPermission(context)
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        val completionId = "foreground-completion-${java.util.UUID.randomUUID()}"
        val backgroundId = "background-completion-${java.util.UUID.randomUUID()}"
        val preferences = context.getSharedPreferences("job_notification_events", Context.MODE_PRIVATE)
        val finished = job(ExecutionState.FINISHED, Outcome.SUCCESS)
        lateinit var notifications: JobNotifications
        fun posted(id: String) = manager.activeNotifications.any { it.tag == id }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    notifications = activity.jobNotifications
                    notifications.update(finished.copy(id = completionId), emptyList())
                    assertFalse("A completion observed while foreground must remain quiet", posted(completionId))
                }
                scenario.moveToState(Lifecycle.State.CREATED)
                // After onStop completes, the same foreground-observed state remains deduplicated while a
                // different completion notifies in the background.
                notifications.update(finished.copy(id = completionId), emptyList())
                assertFalse("Foreground completion must not replay after onStop", posted(completionId))
                notifications.update(finished.copy(id = backgroundId), emptyList())
                awaitNotification(manager, backgroundId)
            }
        } finally {
            manager.cancel(completionId, 1)
            manager.cancel(backgroundId, 1)
            preferences.edit { remove(completionId); remove(backgroundId) }
        }
    }

    @Test
    fun restartingOfflineRefreshesQueuedNetworkWorkFromCurrentSnapshot() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val hasInternet = connectivity.activeNetwork?.let(connectivity::getNetworkCapabilities)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        assumeFalse("This lifecycle regression requires the device to be offline", hasInternet)

        val id = "offline-restart-${java.util.UUID.randomUUID()}"
        val sourceId = "source-$id"
        val database = AppModule.database(context)
        val dao = database.records()
        lateinit var coordinator: JobCoordinator
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity -> coordinator = activity.coordinator }
                runBlocking { coordinator.recover() }
            }
            // Destroy the first activity before inserting the row: a pending initial refresh must not
            // turn this into a false pass before the next activity's onStart reads its own snapshot.
            try {
                val source = Source(
                    sourceId,
                    SourceKind.YOUTUBE,
                    canonicalUrl = "https://www.youtube.com/watch?v=abcdefghijk",
                    videoId = "abcdefghijk",
                )
                val json = Json { encodeDefaults = true }
                val config = json.encodeToString(JobConfig(mode = AcquisitionMode.CAPTIONS_ONLY))
                runBlocking {
                    dao.createJob(
                        SourceRow(sourceId, json.encodeToString(source), "Lifecycle fixture"),
                        JobRow(id, sourceId, config, System.currentTimeMillis()),
                        listOf(AttemptRow("attempt-$id", id, Branch.CAPTIONS, 1, System.currentTimeMillis(), phase = Phase.FETCH_CAPTIONS)),
                    )
                }
                assertEquals(ExecutionState.QUEUED, runBlocking { dao.job(id)?.state })
                ActivityScenario.launch(MainActivity::class.java).use {
                    val deadline = System.nanoTime() + 5_000_000_000L
                    var state: ExecutionState? = null
                    while (state != ExecutionState.WAITING_NETWORK && System.nanoTime() < deadline) {
                        state = runBlocking { dao.job(id)?.state }
                        if (state != ExecutionState.WAITING_NETWORK) Thread.sleep(25)
                    }
                    assertEquals("onStart must seed the current offline network snapshot", ExecutionState.WAITING_NETWORK, state)
                }
            } finally {
                runBlocking {
                    if (dao.requestDeletion(id)) dao.finishDeletion(id)
                }
            }
        } finally {
            database.close()
        }
    }

    private fun requireNotificationPermission(context: Context) {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            assertTrue(
                "The device gate must grant POST_NOTIFICATIONS before this delivery test",
                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED,
            )
        }
    }

    private fun awaitNotification(manager: android.app.NotificationManager, id: String) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (manager.activeNotifications.none { it.tag == id } && System.nanoTime() < deadline) Thread.sleep(20)
        assertTrue("Expected background completion notification", manager.activeNotifications.any { it.tag == id })
    }

    private fun job(state: ExecutionState, outcome: Outcome = Outcome.NONE) =
        JobRow("job", "source", "{}", 1, state = state, outcome = outcome)

    private fun attempt(state: ExecutionState, error: String) =
        AttemptRow("attempt", "job", Branch.CAPTIONS, 1, 1, state = state, error = error)
}
