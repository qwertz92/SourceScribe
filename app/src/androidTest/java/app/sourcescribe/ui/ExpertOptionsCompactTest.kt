package app.sourcescribe.ui

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.app.UiAutomation
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Bundle
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.sourcescribe.MainActivity
import app.sourcescribe.R
import java.util.ArrayDeque
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExpertOptionsCompactTest {
    @Test(timeout = TEST_TIMEOUT_MS)
    fun expertSectionHasAVisibleHeaderAndAnAccessibleCollapseAction() {
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

            waitForVisibleNode(automation, "expert options button") { node ->
                node.isClickable && node.hasAnyLabel(localized(context, R.string.advanced))
            }.performAction(AccessibilityNodeInfo.ACTION_CLICK)

            waitForVisibleNode(automation, "visible expert-options heading") { node ->
                node.hasAnyLabel(localized(context, R.string.expert_options_header))
            }
            val collapse = waitForVisibleNode(automation, "expert-options collapse button") { node ->
                node.isClickable && node.isEnabled && node.hasAnyLabel(localized(context, R.string.expert_options_collapse))
            }
            val bounds = Rect().also(collapse::getBoundsInScreen)
            val minimumTargetPx = (48 * context.resources.displayMetrics.density).toInt()
            assertTrue("Collapse target is under 48 dp high: $bounds", bounds.height() >= minimumTargetPx)
            assertTrue("Collapsing the expert options failed", collapse.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            waitForVisibleNode(automation, "collapsed expert-options button") { node ->
                node.isClickable && node.hasAnyLabel(localized(context, R.string.advanced))
            }
            val visible = automation.freshRoot()?.nodeStrings().orEmpty()
            assertFalse("The collapse action remains visible after collapsing",
                visible.any { it in localized(context, R.string.expert_options_collapse) })
        } finally {
            activity?.let { started -> instrumentation.runOnMainSync { started.finish() } }
        }
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    fun quickStartRequiresNonblankInput() {
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

            val button = waitForVisibleNode(automation, "quick-start button") { node ->
                node.isClickable && node.hasAnyLabel(localized(context, R.string.quick_start_label))
            }
            assertFalse("Quick start is enabled without a source", button.isEnabled)
            waitForVisibleNode(automation, "quick-start cost and upload explanation") { node ->
                node.hasAnyLabel(localized(context, R.string.quick_start_explanation))
            }

            val sourcePredicate: (AccessibilityNodeInfo) -> Boolean = { node ->
                node.className == "android.widget.EditText" && node.isEditable &&
                    node.hasAnyLabel(localized(context, R.string.source_hint))
            }
            val source = waitForVisibleNode(automation, "source field", sourcePredicate)
            val value = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "https://www.youtube.com/watch?v=jNQXAC9IVRw")
            }
            performFreshAction(
                automation,
                "source field",
                source,
                sourcePredicate,
                AccessibilityNodeInfo.ACTION_SET_TEXT,
                value,
            )
            automation.dismissInputMethodIfOpen()
            waitForVisibleNode(automation, "quick start enabled for a source") { node ->
                node.isClickable && node.hasAnyLabel(localized(context, R.string.quick_start_label)) && node.isEnabled
            }
        } finally {
            activity?.let { started -> instrumentation.runOnMainSync { started.finish() } }
        }
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    fun keytermSetNameDraftSurvivesCollapsingExpertOptions() {
        // Before this regression, the name was remembered inside KeytermSetControls, which leaves composition
        // when the section collapses, so an unsaved set name was lost on reopening.
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

            val mode = waitForVisibleNode(automation, "acquisition mode control") { node ->
                node.isClickable && node.hasAnyLabel(localized(context, R.string.mode))
            }
            assertTrue("Could not open acquisition modes", mode.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            val sttOnly = waitForNode(automation, "STT-only mode option") { node ->
                node.isClickable && node.hasAnyLabel(localized(context, R.string.mode_stt_only))
            }
            assertTrue("Could not select STT-only mode", sttOnly.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            val assemblyAi = waitForVisibleNode(automation, "AssemblyAI provider option") { node ->
                node.isClickable && node.nodeStrings().contains("AssemblyAI")
            }
            assertTrue("Could not select AssemblyAI", assemblyAi.performAction(AccessibilityNodeInfo.ACTION_CLICK))

            waitForVisibleNode(automation, "expert options button") { node ->
                node.isClickable && node.hasAnyLabel(localized(context, R.string.advanced))
            }.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            val nameField = waitForVisibleNode(automation, "keyterm-set name field") { node ->
                node.isEditable && node.hasAnyLabel(localized(context, R.string.keyterm_set_name))
            }
            val expectedName = "Draft across collapse"
            val value = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, expectedName)
            }
            assertTrue("Keyterm-set name field rejected the draft", nameField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, value))
            automation.dismissInputMethodIfOpen()

            val collapse = waitForVisibleNode(automation, "expert-options collapse button") { node ->
                node.isClickable && node.hasAnyLabel(localized(context, R.string.expert_options_collapse))
            }
            val collapsePredicate: (AccessibilityNodeInfo) -> Boolean = { node ->
                node.isClickable && node.hasAnyLabel(localized(context, R.string.expert_options_collapse))
            }
            performFreshAction(
                automation,
                "expert-options collapse button",
                collapse,
                collapsePredicate,
                AccessibilityNodeInfo.ACTION_CLICK,
            )
            val collapsed = waitForVisibleNode(automation, "collapsed expert-options button") { node ->
                node.isClickable && node.hasAnyLabel(localized(context, R.string.advanced))
            }
            val collapsedPredicate: (AccessibilityNodeInfo) -> Boolean = { node ->
                node.isClickable && node.hasAnyLabel(localized(context, R.string.advanced))
            }
            performFreshAction(
                automation,
                "collapsed expert-options button",
                collapsed,
                collapsedPredicate,
                AccessibilityNodeInfo.ACTION_CLICK,
            )

            val restored = waitForVisibleNode(automation, "restored keyterm-set name field") { node ->
                node.isEditable && node.hasAnyLabel(localized(context, R.string.keyterm_set_name))
            }
            assertEquals("Collapsing expert options discarded the unsaved name", expectedName, restored.text?.toString())
        } finally {
            activity?.let { started -> instrumentation.runOnMainSync { started.finish() } }
        }
    }

    private fun android.app.UiAutomation.freshRoot(): AccessibilityNodeInfo? {
        // API 37 can retain removed Compose virtual nodes after a scroll or recomposition.
        if (Build.VERSION.SDK_INT >= 34) check(clearCache()) { "Could not clear the accessibility cache" }
        return rootInActiveWindow
    }

    private fun waitForNode(
        automation: UiAutomation,
        description: String,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + NODE_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            automation.freshRoot()?.findDeepest(predicate)?.let { if (it.refresh()) return it }
            SystemClock.sleep(POLL_MS)
        }
        throw AssertionError("Timed out waiting for $description")
    }

    private fun performFreshAction(
        automation: UiAutomation,
        description: String,
        previouslyResolved: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean,
        action: Int,
        arguments: Bundle? = null,
    ) {
        val previousRefresh = previouslyResolved.refresh()
        val current = automation.freshRoot()?.findDeepest(predicate)
            ?: throw AssertionError("Could not reacquire $description from the active window")
        val refreshed = current.refresh()
        val bounds = Rect().also(current::getBoundsInScreen)
        val actions = current.actionList.map { it.id }
        val details = "previousRefresh=$previousRefresh, currentRefresh=$refreshed, " +
            "window=${current.windowId}, class=${current.className}, bounds=$bounds, actions=$actions"
        assertTrue("$description fresh node could not be refreshed ($details)", refreshed)
        assertTrue("$description fresh node is not visible ($details)", current.isVisibleToUser)
        assertTrue("$description fresh node does not expose action $action ($details)", actions.contains(action))
        assertTrue("$description action $action was rejected after reacquisition ($details)",
            current.performAction(action, arguments))
    }

    private fun UiAutomation.dismissInputMethodIfOpen() {
        if (windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }) {
            assertTrue("Could not dismiss the visible input method",
                performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK))
        }
    }

    private fun waitForVisibleNode(
        automation: UiAutomation,
        description: String,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + NODE_TIMEOUT_MS
        // LazyColumn omits distant items, so keep searching the same way while the target has no bounds.
        var searchDirection = AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        while (SystemClock.uptimeMillis() < deadline) {
            val root = automation.freshRoot()
            val target = root?.findDeepest(predicate)
            if (target?.isVisibleToUser == true && target.refresh()) return target

            val scroller = root?.visibleScrollContainer()
            if (scroller != null) {
                val viewport = Rect().also(scroller::getBoundsInScreen)
                val targetBounds = target?.let { Rect().also(it::getBoundsInScreen) }
                if (targetBounds != null) {
                    searchDirection = if (targetBounds.bottom <= viewport.top) {
                        AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                    } else {
                        AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                    }
                }
                if (!scroller.performAction(searchDirection)) {
                    searchDirection = if (searchDirection == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) {
                        AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                    } else {
                        AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                    }
                    scroller.performAction(searchDirection)
                }
            }
            SystemClock.sleep(POLL_MS)
        }
        throw AssertionError("Timed out waiting for visible $description")
    }

    private fun AccessibilityNodeInfo.findDeepest(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        val pending = ArrayDeque<AccessibilityNodeInfo>().apply { add(this@findDeepest) }
        var deepest: AccessibilityNodeInfo? = null
        var visited = 0
        while (pending.isNotEmpty() && visited++ < MAX_NODES) {
            val node = pending.removeFirst()
            if (predicate(node)) deepest = node
            for (index in 0 until node.childCount) node.getChild(index)?.let(pending::addLast)
        }
        return deepest
    }

    private fun AccessibilityNodeInfo.visibleScrollContainer(): AccessibilityNodeInfo? {
        val pending = ArrayDeque<AccessibilityNodeInfo>().apply { add(this@visibleScrollContainer) }
        var visited = 0
        var largest: AccessibilityNodeInfo? = null
        var largestArea = 0L
        while (pending.isNotEmpty() && visited++ < MAX_NODES) {
            val node = pending.removeFirst()
            if (node.isScrollable && node.isVisibleToUser) {
                val bounds = Rect().also(node::getBoundsInScreen)
                val area = bounds.width().toLong() * bounds.height()
                if (area > largestArea) {
                    largest = node
                    largestArea = area
                }
            }
            for (index in 0 until node.childCount) node.getChild(index)?.let(pending::addLast)
        }
        return largest
    }

    private fun AccessibilityNodeInfo.nodeStrings(): List<String> = buildList {
        text?.toString()?.takeIf(String::isNotBlank)?.let(::add)
        contentDescription?.toString()?.takeIf(String::isNotBlank)?.let(::add)
        for (index in 0 until childCount) getChild(index)?.nodeStrings()?.let(::addAll)
    }

    private fun AccessibilityNodeInfo.hasAnyLabel(labels: Set<String>): Boolean = nodeStrings().any(labels::contains)

    private fun localized(context: Context, id: Int): Set<String> {
        val base = Configuration(context.resources.configuration)
        return (listOf("de", "en").map { language ->
            val configuration = Configuration(base).apply { setLocale(Locale.forLanguageTag(language)) }
            context.createConfigurationContext(configuration).resources.getString(id)
        } + context.getString(id)).toSet()
    }

    private companion object {
        const val TEST_TIMEOUT_MS = 120_000L
        const val NODE_TIMEOUT_MS = 25_000L
        const val POLL_MS = 50L
        const val MAX_NODES = 512
    }
}
