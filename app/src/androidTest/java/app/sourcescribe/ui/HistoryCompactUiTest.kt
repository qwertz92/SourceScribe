package app.sourcescribe.ui

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import android.graphics.Rect
import android.os.Build
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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
import app.sourcescribe.core.Provider
import app.sourcescribe.core.Source
import app.sourcescribe.core.SourceKind
import app.sourcescribe.data.ArtifactRow
import app.sourcescribe.data.JobRow
import app.sourcescribe.data.PhaseTimingRow
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import android.view.accessibility.AccessibilityNodeInfo

@RunWith(AndroidJUnit4::class)
class HistoryCompactUiTest {
    @Test
    fun incompleteRecordedTimingsKeepTheirVisiblePhaseNames() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val rows = listOf(
            PhaseTimingRow("resolve", "attempt", Phase.RESOLVE, 1L, 300L),
            PhaseTimingRow("resolve-open", "attempt", Phase.RESOLVE, 2L, null),
            PhaseTimingRow("download", "attempt", Phase.DOWNLOAD_AUDIO, 3L, 500L),
            PhaseTimingRow("download-open", "attempt", Phase.DOWNLOAD_AUDIO, 4L, null),
        )
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                MaterialTheme {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PhaseTimingDetails(rows)
                    }
                }
            } }
            val deadline = System.nanoTime() + TIMEOUT_NANOS
            do {
                val texts = instrumentation.uiAutomation.rootInActiveWindow?.visibleTexts().orEmpty()
                val resolve = context.getString(phaseLabel(Phase.RESOLVE))
                val download = context.getString(phaseLabel(Phase.DOWNLOAD_AUDIO))
                if (texts.any { resolve in it && "300 ms" in it } &&
                    texts.any { download in it && "500 ms" in it }) return
                Thread.sleep(POLL_MS)
            } while (System.nanoTime() < deadline)
            error("Incomplete timing rows lost their phase names")
        }
    }

    private fun AccessibilityNodeInfo.visibleTexts(): List<String> = buildList {
        text?.toString()?.let(::add)
        for (index in 0 until childCount) getChild(index)?.visibleTexts()?.let(::addAll)
    }

    @Test
    fun onlyPersistedResultBranchesNameAnOriginAndSttUsesTheArtifactModel() {
        val configured = JobConfig(
            mode = AcquisitionMode.CAPTIONS_THEN_STT,
            provider = Provider.ASSEMBLYAI,
            model = "configured-model",
        )
        val captions = artifact("captions", Branch.CAPTIONS)
        val stt = artifact("stt", Branch.STT, "reported-model")

        assertEquals(listOf(HistoryResultOrigin(Branch.CAPTIONS)), historyResultOrigins(listOf(captions), configured))
        assertEquals(
            listOf(
                HistoryResultOrigin(Branch.CAPTIONS),
                HistoryResultOrigin(Branch.STT, Provider.ASSEMBLYAI, "reported-model"),
            ),
            historyResultOrigins(listOf(stt, captions), configured),
        )
        assertTrue(historyResultOrigins(emptyList(), configured).isEmpty())
    }

    @Test
    fun channelIsDecodedFromTheSnapshotAndIncludedInHistorySearch() {
        val source = Source(id = "source", kind = SourceKind.YOUTUBE, channel = "Research Channel")
        val channel = decodeStoredSourceChannel(Json.encodeToString(source))

        assertEquals("Research Channel", channel)
        assertTrue(historySearchText("Video", "source", channel, "Jan 1", "{}").contains("Research Channel"))
    }

    @Test
    fun timingsAddRecordedSpansAndKeepOpenSpansIncompleteInsteadOfZero() {
        val rows = listOf(
            PhaseTimingRow("resolve-1", "attempt-1", Phase.RESOLVE, 1L, 100L),
            PhaseTimingRow("resolve-2", "attempt-2", Phase.RESOLVE, 2L, 200L),
            PhaseTimingRow("resolve-open", "attempt-3", Phase.RESOLVE, 3L, null),
            PhaseTimingRow("fetch-open", "attempt-4", Phase.FETCH_CAPTIONS, 4L, null),
        )

        assertEquals(
            listOf(
                PhaseTimingSummary(Phase.RESOLVE, 300L, incomplete = true),
                PhaseTimingSummary(Phase.FETCH_CAPTIONS, null, incomplete = true),
            ),
            summarizePhaseTimings(rows),
        )
    }

    @Test
    fun elapsedTimeUsesRecordedFinishAndLeavesOldTerminalRowsUnknown() {
        val job = JobRow(
            id = "completed", sourceId = "source", config = "{}", createdAt = 1_000L,
            state = ExecutionState.FINISHED, outcome = Outcome.SUCCESS, finishedAt = 31_000L,
        )

        assertEquals(30_000L, historyElapsedMillis(job, now = 100_000L))
        assertEquals(10_000L, historyElapsedMillis(job.copy(finishedAt = null, state = ExecutionState.RUNNING), now = 11_000L))
        assertNull(historyElapsedMillis(job.copy(finishedAt = null), now = 100_000L))
        assertEquals("300 ms", historyDuration(300L))
    }

    @Test
    fun successfulClosedCardsDropActionsAndTerminalProgressSpaceAtBothFontScales() {
        val artifact = artifact("result", Branch.STT, "reported-model")
        val config = Json.encodeToString(JobConfig(mode = AcquisitionMode.STT_ONLY, provider = Provider.GROQ))
        val completed = JobRow(
            id = "completed", sourceId = "source", config = config, createdAt = 1_000L,
            state = ExecutionState.FINISHED, outcome = Outcome.SUCCESS,
        )
        val queued = completed.copy(id = "queued", state = ExecutionState.QUEUED, outcome = Outcome.NONE)
        val withoutResult = completed.copy(id = "without-result")
        val heights = ConcurrentHashMap<String, Int>()
        val densities = ConcurrentHashMap<Float, Float>()
        val names = listOf("completed", "queued", "without-result")
        val keys = SCALES.flatMap { scale -> names.map { "$scale|$it" } }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    MaterialTheme {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            val density = LocalDensity.current.density
                            for (scale in SCALES) {
                                densities[scale] = density
                                CompositionLocalProvider(LocalDensity provides Density(density, scale)) {
                                    for ((name, job) in listOf("completed" to completed, "queued" to queued, "without-result" to withoutResult)) {
                                        Box(Modifier.width(WIDTH).onSizeChanged { heights["$scale|$name"] = it.height }) {
                                            JobCard(
                                                job = job,
                                                title = "Source",
                                                sourceChannel = "Channel",
                                                sourceKind = null,
                                                open = false,
                                                toggle = {},
                                                attempts = emptyList(),
                                                jobArtifacts = if (name == "without-result") emptyList()
                                                    else listOf(artifact.copy(jobId = job.id)),
                                                exports = emptyList(),
                                                phaseTimings = emptyList(),
                                                now = 10_000L,
                                                openHelp = {},
                                                showActions = {},
                                                retryExport = {},
                                                openArtifact = {},
                                                shareArtifact = {},
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

        assertEquals(keys.toSet(), heights.keys.toSet())
        for (scale in SCALES) {
            val completedHeight = heights.getValue("$scale|completed")
            val queuedHeight = heights.getValue("$scale|queued")
            val withoutResultHeight = heights.getValue("$scale|without-result")
            assertTrue("Font scale $scale: the result card has no height", completedHeight > 0)
            assertTrue("Font scale $scale: result actions should remain visible",
                completedHeight - withoutResultHeight >= 48 * densities.getValue(scale))
            assertTrue(
                "Font scale $scale: a successful card should omit action controls and terminal progress placeholders",
                completedHeight < queuedHeight,
            )
        }
        assertFalse(shouldShowJobActions(completed, open = false))
        assertTrue(shouldShowJobActions(completed, open = true))
        assertTrue(shouldShowJobActions(queued, open = false))
        assertTrue(shouldShowJobActions(completed.copy(outcome = Outcome.PARTIAL_SUCCESS), open = false))
        assertTrue(shouldShowJobActions(completed.copy(outcome = Outcome.FAILED), open = false))
        assertTrue(shouldShowJobActions(completed.copy(state = ExecutionState.CANCELLED, outcome = Outcome.CANCELLED), open = false))
        assertTrue(shouldShowJobActions(completed.copy(state = ExecutionState.SUBMISSION_UNCERTAIN, outcome = Outcome.NONE), open = false))
    }

    @Test
    fun finishedOutcomeUsesOnlyItsTextHeightAtBothFontScales() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val outcome = instrumentation.targetContext.getString(outcomeLabel(Outcome.SUCCESS))
        val job = JobRow("completed", "source", "{}", 1_000L,
            state = ExecutionState.FINISHED, outcome = Outcome.SUCCESS)
        for (scale in SCALES) {
            val expectedHeight = java.util.concurrent.atomic.AtomicInteger()
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity -> activity.setContent {
                    MaterialTheme {
                        val density = LocalDensity.current.density
                        CompositionLocalProvider(LocalDensity provides Density(density, scale)) {
                            Column(Modifier.width(WIDTH)) {
                                Text(outcome, Modifier.clearAndSetSemantics {}.onSizeChanged { expectedHeight.set(it.height) },
                                    style = MaterialTheme.typography.bodySmall)
                                JobCard(job, "Source", null, null, false, {}, emptyList(), emptyList(), emptyList(),
                                    emptyList(), 10_000L, {}, {}, {}, {}, {})
                            }
                        }
                    }
                } }
                val deadline = System.nanoTime() + TIMEOUT_NANOS
                var height: Int? = null
                do {
                    if (Build.VERSION.SDK_INT >= 34) instrumentation.uiAutomation.clearCache()
                    height = instrumentation.uiAutomation.rootInActiveWindow?.textBounds(outcome)?.height()
                    if (height != null && expectedHeight.get() > 0) break
                    Thread.sleep(POLL_MS)
                } while (System.nanoTime() < deadline)
                assertTrue("Font scale $scale: finished outcome reserves empty waiting-message lines " +
                    "($height px instead of ${expectedHeight.get()} px)",
                    height != null && expectedHeight.get() > 0 && kotlin.math.abs(height - expectedHeight.get()) <= 1)
            }
        }
    }

    private fun AccessibilityNodeInfo.textBounds(label: String): Rect? {
        if (text?.toString() == label) return Rect().also(::getBoundsInScreen)
        for (index in 0 until childCount) getChild(index)?.textBounds(label)?.let { return it }
        return null
    }

    private fun artifact(id: String, branch: Branch, model: String? = null) = ArtifactRow(
        id = id,
        jobId = "job",
        attemptId = "attempt-$id",
        branch = branch,
        createdAt = 1_000L,
        sha256 = id.padEnd(64, '0'),
        bytes = 1,
        language = "en",
        providerModel = model,
        complete = true,
        warningCount = 0,
    )

    private companion object {
        val WIDTH = 320.dp
        val SCALES = listOf(1f, 2f)
        const val TIMEOUT_NANOS = 30L * 1_000_000_000L
        const val POLL_MS = 50L
    }
}
