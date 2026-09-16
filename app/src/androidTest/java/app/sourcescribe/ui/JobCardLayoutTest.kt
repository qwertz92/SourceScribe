package app.sourcescribe.ui

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
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
import app.sourcescribe.core.AudioTrack
import app.sourcescribe.core.Branch
import app.sourcescribe.core.ExecutionState
import app.sourcescribe.core.Phase
import app.sourcescribe.data.AttemptRow
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Round 25, finding 3. Two lines of an open job card appeared and disappeared without reserved space: the
 * rendition the attempt bound, which is only known once the source has been resolved, and the transfer line,
 * which is only there while bytes are moving. Both arrive and leave on their own while the card stays open -
 * not from the reader expanding it, which is the documented exception - so everything under them jumped once
 * when a resolve finished and twice more around every download and upload.
 *
 * Measured the way `PreviewStatusLayoutTest` measures the line under a preview: the real composable, at a
 * phone's width, at the default font scale and at 200 %. The attempt block is the part of the card that
 * changes; the rest of the card is the same rows in the same order whatever the attempt says.
 */
@RunWith(AndroidJUnit4::class)
class JobCardLayoutTest {
    @Test
    fun theLinesOfAnAttemptKeepTheirHeightWhetherOrNotTheyHaveAnythingToSay() {
        // One attempt in every combination the two lines can be in. The phase, the branch and the error are
        // held still, because those lines were reserved already and are not what this measures.
        val bare = AttemptRow(
            id = "attempt", jobId = "job", branch = Branch.STT, number = 1, createdAt = 0L,
            state = ExecutionState.RUNNING, phase = Phase.DOWNLOAD_AUDIO,
        )
        val withTrack = bare.copy(checkpoint = Json.encodeToString(StoredAudio(TRACK)))
        val attempts = linkedMapOf(
            "neither line" to bare,
            "the bound rendition" to withTrack,
            // Part-way through a download, which is where the percentage and the rate appear.
            "the transfer" to bare.copy(processedBytes = 2_400_000L, totalBytes = 5_200_000L),
            // A transfer whose total nobody knows takes the shorter shape of the same line.
            "a transfer without a total" to bare.copy(processedBytes = 2_400_000L),
            "both lines" to withTrack.copy(processedBytes = 2_400_000L, totalBytes = 5_200_000L),
        )
        val keys = SCALES.flatMap { scale -> attempts.keys.map { key(scale, it) } }
        val heights = ConcurrentHashMap<String, Int>()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    MaterialTheme {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            val density = LocalDensity.current.density
                            for (scale in SCALES) {
                                CompositionLocalProvider(LocalDensity provides Density(density, scale)) {
                                    for ((name, attempt) in attempts) {
                                        Box(Modifier.width(WIDTH).onSizeChanged { heights[key(scale, name)] = it.height }) {
                                            // The spacing the open card puts between these lines, so the
                                            // measured block is the height the card really gains.
                                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                AttemptLines(attempt) {}
                                            }
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
        for (scale in SCALES) {
            val measured = attempts.keys.associateWith { heights.getValue(key(scale, it)) }
            assertTrue("Font scale $scale: an attempt shows nothing: $measured", measured.values.all { it > 0 })
            assertEquals("Font scale $scale: the heights differ: $measured", 1, measured.values.toSet().size)
        }
    }

    /** The shape `SttStep` writes the bound rendition into the attempt's checkpoint in. */
    @kotlinx.serialization.Serializable
    private data class StoredAudio(val audio: AudioTrack)

    private companion object {
        val WIDTH = 320.dp
        val SCALES = listOf(1f, 2f)
        const val TIMEOUT_NANOS = 30L * 1_000_000_000L
        const val POLL_MS = 50L

        /** An ordinary YouTube rendition: the reservation is written for this shape of summary. */
        val TRACK = AudioTrack(
            id = "251", sourceVideoId = "jNQXAC9IVRw", language = "en", name = null, isOriginal = true,
            evidence = "fixture", codec = "opus", container = "webm", bitrateKbps = 160,
            bytes = 5_200_000L, channels = 2,
        )

        fun key(scale: Float, attempt: String) = "$scale|$attempt"
    }
}
