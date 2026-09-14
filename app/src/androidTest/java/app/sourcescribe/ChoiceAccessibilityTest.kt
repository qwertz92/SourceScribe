package app.sourcescribe

import android.app.Activity
import android.app.UiAutomation
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.ArrayDeque
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Accessibility-semantics regression; this is not a TalkBack gesture proof. */
@RunWith(AndroidJUnit4::class)
class ChoiceAccessibilityTest {
    @Test(timeout = TEST_TIMEOUT_MS)
    fun appLanguageChoiceExposesButtonSemanticsAndOpensItsDialog() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        val context = instrumentation.targetContext
        var activity: Activity? = null
        try {
            activity = instrumentation.startActivitySync(
                Intent(context, MainActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK,
                ),
            )

            // This test is about the control, not about how fast the app starts. Up to release 0.2.0 the start of
            // MainViewModel set busy, which disables the control, and on the CI emulator that outlasted NODE_TIMEOUT_MS
            // (DEFECTS, item 54). The start no longer does (ADR 0012), but any action still does while it runs.
            waitUntilIdle(automation)
            val settings = waitForNode(automation, "Settings navigation") { node ->
                node.isClickable && node.hasAnyLabel(SETTINGS_LABELS)
            }
            assertTrue("Settings accessibility click failed", settings.performAction(AccessibilityNodeInfo.ACTION_CLICK))

            val language = waitForNode(
                automation,
                "App language control",
                nearMiss = { node -> node.isClickable && node.hasAnyLabel(APP_LANGUAGE_LABELS) },
            ) { node ->
                node.isClickable && node.isEnabled && node.hasAnyLabel(APP_LANGUAGE_LABELS) && node.hasAnyLabel(LANGUAGE_VALUES)
            }
            assertTrue("App language control is disabled", language.isEnabled)
            assertTrue("App language control is not clickable", language.isClickable)
            // Compose exposes the role as a same-control child when label/value have semantics.
            val buttonRole = language.findFirst { it.className?.toString() == "android.widget.Button" }
                ?: throw AssertionError("App language control has no Button role")
            val controlBounds = Rect().also(language::getBoundsInScreen)
            val roleBounds = Rect().also(buttonRole::getBoundsInScreen)
            assertTrue("Button role lies outside its clickable control", !roleBounds.isEmpty && controlBounds.contains(roleBounds))
            assertTrue("App language accessibility click failed", language.performAction(AccessibilityNodeInfo.ACTION_CLICK))

            waitForLabels(automation, LANGUAGE_VALUES, "German and English language dialog options")
        } finally {
            activity?.let { started -> instrumentation.runOnMainSync { started.finish() } }
        }
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    fun aTimeoutMessageLeavesOutFieldContentAndCutsLongTexts() {
        // CI prints a timeout message into its public log. What a field holds was typed by someone and may be a key, and
        // a long text may be part of a transcript. The canary goes into the source field through accessibility, is never
        // submitted, and lives only in the saved state of this activity.
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        val context = instrumentation.targetContext
        var activity: Activity? = null
        try {
            activity = instrumentation.startActivitySync(
                Intent(context, MainActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK,
                ),
            )
            val field = waitForNode(automation, "Source field") { node ->
                node.isEditable && node.isEnabled && node.hasAnyLabel(SOURCE_LABELS)
            }
            val canary = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, CANARY)
            }
            assertTrue("Setting the canary failed", field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, canary))
            waitForNode(automation, "Canary in the source field") { node ->
                node.isEditable && node.text?.toString() == CANARY
            }

            val summary = windowSummary(automation) { false }
            assertFalse(summary, summary.contains(CANARY))
            val root = automation.rootInActiveWindow ?: throw AssertionError("No active window")
            assertTrue(
                "None of the first $MAX_TEXTS texts is longer than $MAX_TEXT_LENGTH characters, so the cut is not checked",
                root.nodeStrings().take(MAX_TEXTS).any { it.length > MAX_TEXT_LENGTH },
            )
            val reported = root.reportableTexts(MAX_TEXTS)
            assertTrue("Reported $reported", reported.isNotEmpty() && reported.all { it.length <= MAX_TEXT_LENGTH })
        } finally {
            activity?.let { started -> instrumentation.runOnMainSync { started.finish() } }
        }
    }

    private fun waitForNode(
        automation: UiAutomation,
        description: String,
        nearMiss: (AccessibilityNodeInfo) -> Boolean = { false },
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + NODE_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            automation.rootInActiveWindow?.findFirst(predicate)?.let { return it }
            SystemClock.sleep(POLL_MS)
        }
        // Only CI has timed out here, where nobody can look at the screen, so the message says what the window held.
        throw AssertionError("Timed out waiting for $description; ${windowSummary(automation, nearMiss)}")
    }

    /**
     * Waits until the app shows its navigation and no progress bar. MainActivity draws the progress bar exactly while
     * MainViewModel is busy, which is also what disables most controls.
     */
    private fun waitUntilIdle(automation: UiAutomation) {
        val deadline = SystemClock.uptimeMillis() + STARTUP_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            val root = automation.rootInActiveWindow
            if (root != null && root.findFirst { it.isClickable && it.hasAnyLabel(SETTINGS_LABELS) } != null &&
                root.findFirst(::isProgressBar) == null
            ) {
                return
            }
            SystemClock.sleep(POLL_MS)
        }
        throw AssertionError(
            "The app was still busy after ${STARTUP_TIMEOUT_MS / 1000} s; ${windowSummary(automation) { false }}",
        )
    }

    private fun isProgressBar(node: AccessibilityNodeInfo): Boolean = node.className?.toString() == PROGRESS_BAR

    /**
     * The nodes that nearly matched, with the state that kept them out, whether a progress bar shows, and the first
     * texts of the active window, as [reportableTexts] gives them.
     */
    private fun windowSummary(automation: UiAutomation, nearMiss: (AccessibilityNodeInfo) -> Boolean): String {
        val root = automation.rootInActiveWindow ?: return "no active window"
        val misses = ArrayList<String>()
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        var visited = 0
        while (queue.isNotEmpty() && visited++ < MAX_NODES) {
            val node = queue.removeFirst()
            if (misses.size < MAX_NEAR_MISSES && nearMiss(node)) {
                misses += "clickable=${node.isClickable} enabled=${node.isEnabled} visible=${node.isVisibleToUser} " +
                    "texts=${node.reportableTexts(4)}"
            }
            for (index in 0 until node.childCount) node.getChild(index)?.let(queue::addLast)
        }
        val busy = root.findFirst(::isProgressBar) != null
        return "near misses $misses, busy=$busy, window ${root.packageName} shows ${root.reportableTexts(MAX_TEXTS)}"
    }

    private fun waitForLabels(automation: UiAutomation, labels: Set<String>, description: String) {
        val deadline = SystemClock.uptimeMillis() + NODE_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            val visible = automation.rootInActiveWindow?.nodeStrings().orEmpty()
            if (labels.all { label -> visible.any { it.matchesLabel(label) } }) return
            SystemClock.sleep(POLL_MS)
        }
        throw AssertionError("Timed out waiting for $description")
    }

    private fun AccessibilityNodeInfo.findFirst(
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(this@findFirst) }
        var visited = 0
        while (queue.isNotEmpty() && visited++ < MAX_NODES) {
            val node = queue.removeFirst()
            if (predicate(node)) return node
            for (index in 0 until node.childCount) node.getChild(index)?.let(queue::addLast)
        }
        return null
    }

    private fun AccessibilityNodeInfo.hasAnyLabel(labels: Set<String>): Boolean =
        nodeStrings().any { candidate -> labels.any { label -> candidate.matchesLabel(label) } }

    private fun AccessibilityNodeInfo.nodeStrings(): List<String> = buildList {
        text?.toString()?.takeIf(String::isNotBlank)?.let(::add)
        contentDescription?.toString()?.takeIf(String::isNotBlank)?.let(::add)
        for (index in 0 until childCount) {
            getChild(index)?.nodeStrings()?.let { addAll(it) }
        }
    }

    /**
     * Up to [limit] different texts of this node and its descendants in reading order, for a failure message that CI
     * prints into its public log. What an editable or password node holds was typed by someone and may be a key, so it
     * is left out, while the labels under such a node stay. Every other text ends after [MAX_TEXT_LENGTH] characters,
     * since a long one may be part of a transcript, and at most [MAX_NODES] nodes are visited.
     */
    private fun AccessibilityNodeInfo.reportableTexts(limit: Int): List<String> {
        val texts = LinkedHashSet<String>()
        val pending = ArrayDeque<AccessibilityNodeInfo>().apply { push(this@reportableTexts) }
        var visited = 0
        while (pending.isNotEmpty() && texts.size < limit && visited++ < MAX_NODES) {
            val node = pending.pop()
            if (!node.isEditable && !node.isPassword) {
                listOfNotNull(node.text, node.contentDescription).map(CharSequence::toString).filter(String::isNotBlank)
                    .forEach { texts += it.take(MAX_TEXT_LENGTH) }
            }
            for (index in node.childCount - 1 downTo 0) node.getChild(index)?.let(pending::push)
        }
        return texts.take(limit)
    }

    private fun String.matchesLabel(label: String): Boolean =
        lineSequence().flatMap { it.split(',').asSequence() }.any { it.trim().equals(label, true) }

    companion object {
        private const val TEST_TIMEOUT_MS = 240_000L
        private const val NODE_TIMEOUT_MS = 25_000L
        private const val STARTUP_TIMEOUT_MS = 150_000L
        private const val PROGRESS_BAR = "android.widget.ProgressBar"
        private const val POLL_MS = 50L
        private const val MAX_NODES = 512
        private const val MAX_NEAR_MISSES = 5
        private const val MAX_TEXTS = 24
        private const val MAX_TEXT_LENGTH = 60
        private const val CANARY = "sourcescribe-canary-typed-text"
        private const val GERMAN_LABEL = "Deutsch"
        private const val ENGLISH_LABEL = "English"
        private val SETTINGS_LABELS = setOf("Mehr", "More")
        private val APP_LANGUAGE_LABELS = setOf("App-Sprache", "App language")
        private val SOURCE_LABELS = setOf("YouTube-Link oder geteilter Text", "YouTube link or shared text")
        private val LANGUAGE_VALUES = setOf(GERMAN_LABEL, ENGLISH_LABEL)
    }
}
