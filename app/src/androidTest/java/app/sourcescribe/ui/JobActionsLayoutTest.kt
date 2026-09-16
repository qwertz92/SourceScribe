package app.sourcescribe.ui

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.sourcescribe.MainActivity
import app.sourcescribe.core.ExecutionState
import app.sourcescribe.core.Outcome
import app.sourcescribe.data.JobSituation
import java.util.concurrent.ConcurrentHashMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The job actions dialog keeps one size, whatever job it was opened for.
 *
 * Until 0.4.0 its content column was bounded rather than fixed (`heightIn(max = …)` around a `LazyColumn` with
 * `fill = false`), so the dialog was as tall as the actions that particular job allowed: three buttons for a
 * running job, seven for a finished one that can still be retried and whose provider data can be removed. Opening
 * two entries in a row put the buttons in different places, and the owner's standing rule is that nothing may
 * jump. Measured the way `PreviewStatusLayoutTest` measures the line under a preview: at a phone's width, at the
 * default font scale and at 200 %, for every shape the dialog can take.
 */
@RunWith(AndroidJUnit4::class)
class JobActionsLayoutTest {
    @Test
    fun theActionsDialogIsTheSameHeightForEveryJobItCanBeOpenedFor() {
        val situations = linkedMapOf(
            // Nothing wrong and nothing to do: the shortest the dialog gets.
            "finished, nothing to do" to JobSituation(
                state = ExecutionState.FINISHED,
                outcome = Outcome.SUCCESS,
            ),
            // Every action at once: over, retryable, and the provider still holds data for it.
            "finished, everything offered" to JobSituation(
                state = ExecutionState.FINISHED,
                outcome = Outcome.PARTIAL_SUCCESS,
                errors = listOf("PROVIDER_REMOTE_FAILED"),
                remoteDeletionPossible = true,
                incompleteResult = true,
            ),
            // Running: cancel, prepare again, delete.
            "running" to JobSituation(state = ExecutionState.RUNNING, outcome = Outcome.NONE),
            // The case the dialog was rebuilt for: resume withheld, a new run recommended.
            "stranded on a replaced component" to JobSituation(
                state = ExecutionState.WAITING_USER,
                outcome = Outcome.NONE,
                errors = listOf("ENGINE_NOT_AVAILABLE"),
            ),
            // Resume offered and recommended.
            "waiting for the network" to JobSituation(
                state = ExecutionState.WAITING_USER,
                outcome = Outcome.NONE,
                errors = listOf("NETWORK"),
            ),
            // Two reasons at once, so two sentences under "What happened".
            "two branches, two reasons" to JobSituation(
                state = ExecutionState.WAITING_USER,
                outcome = Outcome.FAILED,
                errors = listOf("NETWORK", "CHECKPOINT_DAMAGED"),
            ),
            // A reason with the extra note about the job's limits, which is the longest text the dialog shows.
            "past the length limit" to JobSituation(
                state = ExecutionState.WAITING_USER,
                outcome = Outcome.FAILED,
                errors = listOf("AUDIO_LONGER_THAN_LIMIT"),
            ),
            // No recommendation at all: a sentence stands where the highlighted button would be.
            "submission uncertain" to JobSituation(
                state = ExecutionState.SUBMISSION_UNCERTAIN,
                outcome = Outcome.NONE,
                errors = listOf("SUBMISSION_UNCERTAIN"),
            ),
        )
        val keys = SCALES.flatMap { scale -> situations.keys.map { key(scale, it) } }
        val heights = ConcurrentHashMap<String, Int>()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    MaterialTheme {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            val density = LocalDensity.current.density
                            for (scale in SCALES) {
                                CompositionLocalProvider(LocalDensity provides Density(density, scale)) {
                                    for ((name, situation) in situations) {
                                        Box(
                                            Modifier.width(WIDTH)
                                                .onSizeChanged { heights[key(scale, name)] = it.height },
                                        ) {
                                            JobActionsContent(
                                                situation = situation,
                                                limitReached = situation.errors.contains("AUDIO_LONGER_THAN_LIMIT"),
                                                height = HEIGHT,
                                                openHelp = {},
                                                act = {},
                                                close = {},
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            val deadline = System.nanoTime() + TIMEOUT_NANOS
            while (heights.size < keys.size && System.nanoTime() < deadline) Thread.sleep(POLL_MS)
        }

        assertEquals("Slots that were laid out", keys.toSet(), heights.keys.toSet())
        val measured = heights.toMap()
        assertTrue("A dialog measured nothing at all: $measured", measured.values.all { it > 0 })
        // One height for every job and both font scales. The content is given a fixed height in density-
        // independent pixels, so a larger font makes the list inside it scroll rather than the dialog grow.
        assertEquals("The dialog is not one size: $measured", 1, measured.values.toSet().size)
    }

    private companion object {
        val WIDTH = 320.dp

        /**
         * Taller than the content of the shortest of the situations above at the default font scale, and that is
         * what makes this test able to fail: a dialog that was merely bounded by this height rather than fixed at
         * it would be its content's height here, and the shortest and the longest situation would differ. A
         * height the content always exceeds would clamp every one of them to the same number and prove nothing -
         * measured at 480 dp in the negative control of 16 September 2026, where all sixteen came out equal even
         * with the fix reverted.
         */
        val HEIGHT = 900.dp
        val SCALES = listOf(1f, 2f)
        const val TIMEOUT_NANOS = 30L * 1_000_000_000L
        const val POLL_MS = 50L

        fun key(scale: Float, situation: String) = "$scale|$situation"
    }
}
