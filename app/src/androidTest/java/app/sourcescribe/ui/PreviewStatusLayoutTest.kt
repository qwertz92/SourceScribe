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
import app.sourcescribe.MainViewModel
import app.sourcescribe.core.JobLimits
import java.util.concurrent.ConcurrentHashMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreviewStatusLayoutTest {
    @Test
    fun theLineUnderAPreviewKeepsItsHeightWhateverItSays() {
        // Defect 36. The line under a preview's settings changes while the reader works the controls above it: a
        // missing key reads as one sentence, a source past the app's ceiling as a warning of its own, and a source
        // that can start as a short sentence. Each change of height moves the start button under the card. Measured
        // as the card lays the line out, at a phone's width, at the default font scale and at 200 %, for a source
        // well inside the ceiling and for one beyond it.
        val states = listOf<String?>(null) + MainViewModel.PREVIEW_ERRORS_SHOWN_AS_TEXT + "SOURCE_LONGER_THAN_LIMIT"
        val sources = linkedMapOf(
            "a source inside the ceiling" to TWO_HOURS_MS,
            "a source beyond the ceiling" to JobLimits.MAX_AUDIO_SECONDS * 1_000L + TWO_HOURS_MS,
        )
        val keys = SCALES.flatMap { scale -> sources.keys.flatMap { source -> states.map { key(scale, source, it) } } }
        val heights = ConcurrentHashMap<String, Int>()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    MaterialTheme {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            val density = LocalDensity.current.density
                            for (scale in SCALES) {
                                CompositionLocalProvider(LocalDensity provides Density(density, scale)) {
                                    for ((source, durationMs) in sources) for (state in states) {
                                        Box(Modifier.width(WIDTH).onSizeChanged { heights[key(scale, source, state)] = it.height }) {
                                            PreviewStatus(state, durationMs) {}
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
        for (scale in SCALES) for (source in sources.keys) {
            val measured = states.associateWith { heights.getValue(key(scale, source, it)) }
            assertTrue("Font scale $scale, $source: a state shows nothing: $measured", measured.values.all { it > 0 })
            assertEquals("Font scale $scale, $source: the heights differ: $measured", 1, measured.values.toSet().size)
        }
    }

    private companion object {
        val WIDTH = 320.dp
        val SCALES = listOf(1f, 2f)
        const val TWO_HOURS_MS = 2L * 60L * 60L * 1_000L
        const val TIMEOUT_NANOS = 30L * 1_000_000_000L
        const val POLL_MS = 50L

        fun key(scale: Float, source: String, state: String?) = "$scale|$source|$state"
    }
}
