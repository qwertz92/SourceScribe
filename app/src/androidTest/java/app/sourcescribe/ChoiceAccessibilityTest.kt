package app.sourcescribe

import android.app.Activity
import android.app.UiAutomation
import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.ArrayDeque
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

            val settings = waitForNode(automation, "Settings navigation") { node ->
                node.isClickable && node.hasAnyLabel(SETTINGS_LABELS)
            }
            assertTrue("Settings accessibility click failed", settings.performAction(AccessibilityNodeInfo.ACTION_CLICK))

            val language = waitForNode(automation, "App language control") { node ->
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

    private fun waitForNode(
        automation: UiAutomation,
        description: String,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + NODE_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            automation.rootInActiveWindow?.findFirst(predicate)?.let { return it }
            SystemClock.sleep(POLL_MS)
        }
        throw AssertionError("Timed out waiting for $description")
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

    private fun String.matchesLabel(label: String): Boolean =
        lineSequence().flatMap { it.split(',').asSequence() }.any { it.trim().equals(label, true) }

    companion object {
        private const val TEST_TIMEOUT_MS = 60_000L
        private const val NODE_TIMEOUT_MS = 25_000L
        private const val POLL_MS = 50L
        private const val MAX_NODES = 512
        private const val GERMAN_LABEL = "Deutsch"
        private const val ENGLISH_LABEL = "English"
        private val SETTINGS_LABELS = setOf("Mehr", "More")
        private val APP_LANGUAGE_LABELS = setOf("App-Sprache", "App language")
        private val LANGUAGE_VALUES = setOf(GERMAN_LABEL, ENGLISH_LABEL)
    }
}
