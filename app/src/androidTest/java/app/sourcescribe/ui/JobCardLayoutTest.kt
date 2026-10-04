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
import app.sourcescribe.R
import app.sourcescribe.core.AudioTrack
import app.sourcescribe.core.Branch
import app.sourcescribe.core.ExecutionState
import app.sourcescribe.core.Phase
import app.sourcescribe.data.AttemptRow
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun attemptErrorSummaryReservesTwoLinesAndHelpSlotWithLongErrorAtBothFontScales() {
        val keys = SCALES.flatMap { scale -> listOf("empty", "long error with help").map { key(scale, it) } }
        val heights = ConcurrentHashMap<String, Int>()
        lateinit var fullError: String

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                fullError = activity.getString(R.string.audio_longer_than_limit)
                activity.setContent {
                    MaterialTheme {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            val density = LocalDensity.current.density
                            for (scale in SCALES) {
                                CompositionLocalProvider(LocalDensity provides Density(density, scale)) {
                                    for ((name, message) in listOf("empty" to "", "long error with help" to fullError)) {
                                        Box(Modifier.width(WIDTH).onSizeChanged { heights[key(scale, name)] = it.height }) {
                                            AttemptErrorSummary(
                                                message = message,
                                                helpTopic = HelpTopic.LIMITS.takeIf { message.isNotEmpty() },
                                                openHelp = {},
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

        assertTrue("The full localized explanation must reach the summary", fullError.length > 100)
        assertEquals("Slots that were laid out", keys.toSet(), heights.keys.toSet())
        for (scale in SCALES) {
            val measured = listOf("empty", "long error with help").associateWith { heights.getValue(key(scale, it)) }
            assertTrue("Font scale $scale: error summary has no height: $measured", measured.values.all { it > 0 })
            assertEquals("Font scale $scale: error/help state changed the reserved height: $measured",
                1, measured.values.toSet().size)
        }
    }

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
            "error without help" to bare.copy(error = "PROVIDER_INVALID_INPUT"),
            "long error with help" to bare.copy(error = "AUDIO_LONGER_THAN_LIMIT"),
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

    @Test
    fun terminalAttemptLinesDoNotReserveEmptyTrackProgressOrErrorSlots() {
        val base = AttemptRow(
            id = "terminal", jobId = "job", branch = Branch.STT, number = 1, createdAt = 0L,
            state = ExecutionState.FINISHED, phase = Phase.PERSIST,
        )
        val details = base.copy(
            phase = Phase.DOWNLOAD_AUDIO,
            checkpoint = Json.encodeToString(StoredAudio(TRACK)),
            processedBytes = 2_400_000L,
            totalBytes = 5_200_000L,
            error = "AUDIO_LONGER_THAN_LIMIT",
        )
        val keys = SCALES.flatMap { scale -> listOf("empty", "recorded details").map { key(scale, it) } }
        val heights = ConcurrentHashMap<String, Int>()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    MaterialTheme {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            val density = LocalDensity.current.density
                            for (scale in SCALES) {
                                CompositionLocalProvider(LocalDensity provides Density(density, scale)) {
                                    for ((name, attempt) in listOf("empty" to base, "recorded details" to details)) {
                                        Box(Modifier.width(WIDTH).onSizeChanged { heights[key(scale, name)] = it.height }) {
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
            val emptyHeight = heights.getValue(key(scale, "empty"))
            val detailsHeight = heights.getValue(key(scale, "recorded details"))
            assertTrue("Font scale $scale: terminal phase line has no height", emptyHeight > 0)
            assertTrue("Font scale $scale: real terminal details should be shown", detailsHeight > emptyHeight)
        }
    }

    @Test
    fun collapsedPhaseAndTransferLinesKeepTheirHeightAndOnlyShowRealTransfers() {
        val base = AttemptRow(
            id = "summary", jobId = "job", branch = Branch.STT, number = 1, createdAt = 0L,
            state = ExecutionState.RUNNING, phase = Phase.RESOLVE,
        )
        val total = 5_200_000L
        val attempts = linkedMapOf<String, AttemptRow?>(
            "no current attempt" to null,
            "resolve" to base,
            "download at zero" to base.copy(phase = Phase.DOWNLOAD_AUDIO, totalBytes = total),
            "download halfway" to base.copy(phase = Phase.DOWNLOAD_AUDIO, processedBytes = total / 2, totalBytes = total),
            "download complete" to base.copy(phase = Phase.DOWNLOAD_AUDIO, processedBytes = total, totalBytes = total),
            "upload halfway" to base.copy(phase = Phase.SUBMIT, processedBytes = total / 2, totalBytes = total),
            "upload complete awaiting provider" to base.copy(phase = Phase.SUBMIT, processedBytes = total, totalBytes = total),
            "waiting for transcription" to base.copy(phase = Phase.RETRIEVE, state = ExecutionState.WAITING_REMOTE,
                processedBytes = total, totalBytes = total),
            "polling for transcription" to base.copy(phase = Phase.RETRIEVE, state = ExecutionState.RUNNING,
                processedBytes = total, totalBytes = total),
            "result processing" to base.copy(phase = Phase.NORMALIZE, processedBytes = total, totalBytes = total),
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
                                            CollapsedAttemptLines(attempt)
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
            assertTrue("Font scale $scale: a phase/progress slot has no height: $measured", measured.values.all { it > 0 })
            assertEquals("Font scale $scale: phase/progress changed the collapsed height: $measured",
                1, measured.values.toSet().size)
        }

        assertEquals(setOf(Phase.DOWNLOAD_AUDIO, Phase.UPLOAD, Phase.SUBMIT),
            Phase.entries.filter(::isTransferPhase).toSet())
        assertTrue(showsTransferProgress(base.copy(phase = Phase.DOWNLOAD_AUDIO, processedBytes = 1, totalBytes = 2)))
        assertTrue(showsTransferProgress(base.copy(phase = Phase.UPLOAD, processedBytes = 1, totalBytes = 2)))
        assertTrue(showsTransferProgress(base.copy(phase = Phase.SUBMIT, processedBytes = 1, totalBytes = 2)))
        assertFalse(showsTransferProgress(base.copy(phase = Phase.RETRIEVE, processedBytes = 2, totalBytes = 2)))
        val completedUpload = base.copy(phase = Phase.SUBMIT, processedBytes = 2, totalBytes = 2)
        assertTrue(showsTransferProgress(completedUpload))
        assertEquals(100, percentOf(completedUpload.processedBytes, requireNotNull(completedUpload.totalBytes)))
        assertTrue(isUploadCompleteWaiting(completedUpload))
        assertFalse(isTransferMoving(completedUpload))
        assertEquals(R.string.history_waiting_provider_response, waitingProviderResponseLabel(completedUpload))
        assertFalse(isUploadCompleteWaiting(completedUpload.copy(processedBytes = 3)))
        val rejectedAfterUpload = completedUpload.copy(state = ExecutionState.WAITING_USER)
        assertFalse(isUploadCompleteWaiting(rejectedAfterUpload))
        assertNull(waitingProviderResponseLabel(rejectedAfterUpload))
        val completedRequestUpload = completedUpload.copy(phase = Phase.UPLOAD)
        assertTrue(showsTransferProgress(completedRequestUpload))
        assertEquals(100, percentOf(completedRequestUpload.processedBytes,
            requireNotNull(completedRequestUpload.totalBytes)))
        assertEquals(R.string.history_waiting_provider_response, waitingProviderResponseLabel(completedRequestUpload))
        assertFalse(isTransferMoving(completedRequestUpload))
        assertTrue(isWaitingForTranscription(base.copy(phase = Phase.RETRIEVE, state = ExecutionState.WAITING_REMOTE)))
        val polling = base.copy(phase = Phase.RETRIEVE, state = ExecutionState.RUNNING)
        assertTrue(isWaitingForTranscription(polling))
        assertEquals(R.string.history_waiting_provider_response, waitingProviderResponseLabel(polling))
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
