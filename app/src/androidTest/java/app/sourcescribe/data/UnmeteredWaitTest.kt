package app.sourcescribe.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * That "unmetered connections only" really makes a job wait, and that it really starts again by itself.
 *
 * Every other test around this reads the constraint the app writes; only the system can say what the
 * constraint then does. So this one runs against the real WorkManager of the installed app and against the
 * real connection of the device, in two stages, with the operator switching the connection in between:
 *
 * ```
 * adb -s emulator-5556 shell svc wifi disable        # the device is left on metered mobile data
 * am instrument -e sourcescribeUnmeteredWait true -e sourcescribeUnmeteredStage wait  …
 * adb -s emulator-5556 shell am kill app.sourcescribe.debug   # the app is closed, not force-stopped
 * adb -s emulator-5556 shell svc wifi enable
 * # watch it come back on its own: pidof app.sourcescribe.debug, and dumpsys jobscheduler
 * am instrument -e sourcescribeUnmeteredWait true -e sourcescribeUnmeteredStage resume …
 * ```
 *
 * `am kill` rather than `am force-stop`: a force-stop puts the package into the stopped state, where
 * Android cancels its scheduled jobs, so the wait would be over for a reason that has nothing to do with
 * the connection. `am kill` only ends the process, which is what closing the app does.
 *
 * The work is the app's own [AcquisitionWorker] under the app's own constraint, with an attempt id that
 * exists nowhere: the coordinator finds no attempt, claims nothing and returns, so the run touches no job
 * and no provider. It is opt-in for the obvious reason that it needs a connection somebody switches.
 */
@RunWith(AndroidJUnit4::class)
class UnmeteredWaitTest {
    @Test
    fun theWorkWaitsWhileTheOnlyConnectionIsMetered() {
        val context = requireStage("wait")
        assumeTrue("switch the device to a metered connection first", !isUnmetered(context))
        val manager = WorkManager.getInstance(context)
        val request = OneTimeWorkRequestBuilder<AcquisitionWorker>()
            .setInputData(workDataOf("attemptId" to "unmetered-wait-${UUID.randomUUID()}"))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
            .build()
        manager.enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
            .result.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)

        // Ten seconds is not a proof of patience; it is the window in which the same work runs when the
        // connection is unmetered, which the second stage measures in single-digit seconds.
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            assertEquals("work ran on a metered connection", WorkInfo.State.ENQUEUED, state(manager))
            Thread.sleep(500)
        }
    }

    @Test
    fun theWaitingWorkRanOnceTheConnectionWasUnmeteredAgain() {
        val context = requireStage("resume")
        assumeTrue("switch the device back to an unmetered connection first", isUnmetered(context))
        val manager = WorkManager.getInstance(context)
        val deadline = System.currentTimeMillis() + 60_000
        var state = state(manager)
        while (state != WorkInfo.State.SUCCEEDED && System.currentTimeMillis() < deadline) {
            Thread.sleep(500)
            state = state(manager)
        }
        assertEquals("the work the first stage left waiting", WorkInfo.State.SUCCEEDED, state)
        manager.pruneWork().result.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    /** The state of the one live request under [UNIQUE_NAME]; a replaced run stays listed as cancelled. */
    private fun state(manager: WorkManager): WorkInfo.State {
        val infos = manager.getWorkInfosForUniqueWork(UNIQUE_NAME).get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .filter { it.state != WorkInfo.State.CANCELLED }
        assertTrue("no work under $UNIQUE_NAME; run the first stage on a metered connection", infos.isNotEmpty())
        return infos.single().state
    }

    private fun isUnmetered(context: Context): Boolean {
        val connectivity = requireNotNull(context.getSystemService(ConnectivityManager::class.java))
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true
    }

    private fun requireStage(expected: String): Context {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("the unmetered wait is opt-in", arguments.getString(OPT_IN_ARGUMENT) == "true")
        assumeTrue("one stage at a time", arguments.getString(STAGE_ARGUMENT) == expected)
        return InstrumentationRegistry.getInstrumentation().targetContext
    }

    private companion object {
        const val OPT_IN_ARGUMENT = "sourcescribeUnmeteredWait"
        const val STAGE_ARGUMENT = "sourcescribeUnmeteredStage"
        const val UNIQUE_NAME = "unmetered-wait-fixture"
        const val TIMEOUT_SECONDS = 30L
    }
}
