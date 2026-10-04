package app.sourcescribe.ui

import android.app.Activity
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.sourcescribe.MainActivity
import app.sourcescribe.R
import java.util.ArrayDeque
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HelpOverlayTest {
    @Test(timeout = TEST_TIMEOUT_MS)
    fun questionMarkShowsTheTopicBeforeTheExplicitHelpPageActionNavigates() {
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

            val questionLabels = localized(context, R.string.help_open).flatMap { prefix ->
                localized(context, R.string.help_modes_title).map { "$prefix: $it" }
            }.toSet()
            val question = waitForVisibleNode(automation, "acquisition help button") { node ->
                node.isClickable && node.isEnabled && node.hasAnyLabel(questionLabels)
            }
            assertTrue("Question mark did not open its topic", question.performAction(AccessibilityNodeInfo.ACTION_CLICK))

            val pageActionLabels = localized(context, R.string.help_overlay_open_page)
            val pageAction = waitForNode(automation, "explicit help-page action") { node ->
                node.isClickable && node.hasAnyLabel(pageActionLabels)
            }
            val overlayTexts = automation.freshRoot()?.nodeStrings().orEmpty()
            assertTrue("Overlay title is missing", overlayTexts.any { it in localized(context, R.string.help_modes_title) })
            assertTrue("Overlay topic body is missing", overlayTexts.any { it in localized(context, R.string.help_modes_body) })
            assertTrue("The ? button navigated before the explicit action", pageAction.isEnabled)

            val close = waitForNode(automation, "overlay close button") { node ->
                node.isClickable && node.hasAnyLabel(localized(context, R.string.help_overlay_close))
            }
            assertTrue("Overlay close failed", close.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            waitForVisibleNode(automation, "source controls after closing the overlay") { node ->
                node.hasAnyLabel(localized(context, R.string.mode))
            }

            val reopenedQuestion = waitForVisibleNode(automation, "acquisition help button after close") { node ->
                node.isClickable && node.isEnabled && node.hasAnyLabel(questionLabels)
            }
            assertTrue("Question mark did not reopen its topic", reopenedQuestion.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            val pageActionAfterReopen = waitForVisibleNode(automation, "help-page action after reopening") { node ->
                node.isClickable && node.hasAnyLabel(pageActionLabels)
            }
            assertTrue("Explicit help-page action failed", pageActionAfterReopen.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            waitForVisibleNode(automation, "focused topic entry on the Help page") { node ->
                node.isClickable && node.hasAnyLabel(localized(context, R.string.help_modes_title))
            }
            waitForVisibleNode(automation, "focused topic body on the Help page") { node ->
                node.hasAnyLabel(localized(context, R.string.help_modes_body))
            }
            val pageLabels = automation.freshRoot()?.nodeStrings().orEmpty()
            assertTrue("Help overlay close action remains after navigating to the Help page",
                pageLabels.none { it in localized(context, R.string.help_overlay_close) })
            assertTrue("Help overlay page action remains after navigating to the Help page",
                pageLabels.none { it in localized(context, R.string.help_overlay_open_page) })
        } finally {
            activity?.let { started -> instrumentation.runOnMainSync { started.finish() } }
        }
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    fun questionMarkInsideAnExpertSwitchDoesNotChangeItsCheckedState() {
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

            val speakerLabels = localized(context, R.string.diarization)
            val speakerSwitch = waitForVisibleNode(automation, "speaker-separation switch") { node ->
                node.isCheckable && node.isClickable && node.hasAnyLabel(speakerLabels)
            }
            assertTrue("Speaker-separation switch must be enabled for the tap regression", speakerSwitch.isEnabled)
            // The boolean accessor keeps this fixture runnable on API 29–35; getChecked() starts at API 36.
            @Suppress("DEPRECATION")
            val before = speakerSwitch.isChecked
            val topicLabels = localized(context, R.string.help_open).flatMap { prefix ->
                localized(context, R.string.help_diarization_title).map { "$prefix: $it" }
            }.toSet()
            val question = waitForVisibleNode(automation, "speaker-separation help button") { node ->
                node.isClickable && node.hasAnyLabel(topicLabels)
            }
            val questionPredicate: (AccessibilityNodeInfo) -> Boolean = { node ->
                node.isClickable && node.hasAnyLabel(topicLabels)
            }
            performFreshAction(
                automation,
                "speaker-separation help button",
                question,
                questionPredicate,
                AccessibilityNodeInfo.ACTION_CLICK,
            )
            val close = waitForNode(automation, "expert-option help overlay") { node ->
                node.isClickable && node.hasAnyLabel(localized(context, R.string.help_overlay_close))
            }
            assertTrue("Could not close expert-option help", close.performAction(AccessibilityNodeInfo.ACTION_CLICK))

            val after = waitForVisibleNode(automation, "speaker-separation switch after closing help") { node ->
                node.isCheckable && node.isClickable && node.hasAnyLabel(speakerLabels)
            }
            @Suppress("DEPRECATION") // Same API 29–35 compatibility reason as the read before opening help.
            val afterChecked = after.isChecked
            assertEquals("The question-mark tap changed the switch", before, afterChecked)
        } finally {
            activity?.let { started -> instrumentation.runOnMainSync { started.finish() } }
        }
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    // This fixture deliberately measures portrait; ActivityScenario closes the temporary Activity afterwards.
    @SuppressLint("SourceLockedOrientationActivity")
    fun shortHelpTopicDoesNotReserveTheWholeDialogHeight() {
        // The earlier exact 78 %-height dialog left most of the window blank for this shortest topic.
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        val context = instrumentation.targetContext
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            val orientationDeadline = SystemClock.uptimeMillis() + ORIENTATION_TIMEOUT_MS
            var orientation = Configuration.ORIENTATION_UNDEFINED
            while (orientation != Configuration.ORIENTATION_PORTRAIT && SystemClock.uptimeMillis() < orientationDeadline) {
                scenario.onActivity { orientation = it.resources.configuration.orientation }
                if (orientation == Configuration.ORIENTATION_PORTRAIT) break
                SystemClock.sleep(POLL_MS)
            }
            assertEquals("Could not place the short help overlay in portrait", Configuration.ORIENTATION_PORTRAIT, orientation)
            var density = 0f
            var maximumHeightDp = 0f
            scenario.onActivity { activity ->
                density = activity.resources.displayMetrics.density
                maximumHeightDp = minOf(activity.resources.configuration.screenHeightDp * 0.78f, 600f)
                activity.setContent {
                    CompositionLocalProvider(LocalDensity provides Density(density, 1f)) {
                        MaterialTheme {
                            HelpOverlay(HelpTopic.CONTEXT_TERMS, close = {}, openHelp = {})
                        }
                    }
                }
            }

            val title = waitForNode(automation, "short-topic overlay title") { node ->
                node.hasAnyLabel(localized(context, R.string.help_context_title))
            }
            val close = waitForNode(automation, "short-topic close action") { node ->
                node.isClickable && node.hasAnyLabel(localized(context, R.string.help_overlay_close))
            }
            val titleBounds = Rect().also(title::getBoundsInScreen)
            val closeBounds = Rect().also(close::getBoundsInScreen)
            val contentHeightDp = (closeBounds.bottom - titleBounds.top) / density
            assertTrue("Short topic reserved a tall blank dialog (${contentHeightDp}dp; max ${maximumHeightDp}dp)",
                contentHeightDp < maximumHeightDp - 80f)
        }
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    fun overlayActionsStayReachableAtLargeTextInLandscape() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        val context = instrumentation.targetContext
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            val orientationDeadline = SystemClock.uptimeMillis() + ORIENTATION_TIMEOUT_MS
            var orientation = Configuration.ORIENTATION_UNDEFINED
            while (orientation != Configuration.ORIENTATION_LANDSCAPE && SystemClock.uptimeMillis() < orientationDeadline) {
                scenario.onActivity { orientation = it.resources.configuration.orientation }
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) break
                SystemClock.sleep(POLL_MS)
            }
            assertEquals("Could not place the help overlay in landscape", Configuration.ORIENTATION_LANDSCAPE, orientation)
            var density = 0f
            var displayWidth = 0
            var displayHeight = 0
            scenario.onActivity { activity ->
                density = activity.resources.displayMetrics.density
                displayWidth = activity.resources.displayMetrics.widthPixels
                displayHeight = activity.resources.displayMetrics.heightPixels
                activity.setContent {
                    CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                        MaterialTheme {
                            HelpOverlay(HelpTopic.CONTEXT_TERMS, close = {}, openHelp = {})
                        }
                    }
                }
            }

            val pageAction = waitForVisibleNode(automation, "large-text help-page action") { node ->
                node.isClickable && node.isEnabled && node.hasAnyLabel(localized(context, R.string.help_overlay_open_page))
            }
            val close = waitForVisibleNode(automation, "large-text close action") { node ->
                node.isClickable && node.isEnabled && node.hasAnyLabel(localized(context, R.string.help_overlay_close))
            }
            val minimumTargetPx = (48 * density).toInt()
            for ((name, node) in listOf("help page" to pageAction, "close" to close)) {
                val bounds = Rect().also(node::getBoundsInScreen)
                assertTrue("$name action is not visible at large text", node.isVisibleToUser)
                assertTrue("$name target is under 48 dp high: $bounds", bounds.height() >= minimumTargetPx)
                assertTrue("$name action is clipped at the display edge: $bounds",
                    bounds.left >= 0 && bounds.top >= 0 && bounds.right <= displayWidth && bounds.bottom <= displayHeight)
            }
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
        }
    }

    private fun android.app.UiAutomation.freshRoot(): AccessibilityNodeInfo? {
        // API 37 can retain removed Compose virtual nodes after a scroll or recomposition.
        if (Build.VERSION.SDK_INT >= 34) check(clearCache()) { "Could not clear the accessibility cache" }
        return rootInActiveWindow
    }

    private fun waitForNode(
        automation: android.app.UiAutomation,
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
        automation: android.app.UiAutomation,
        description: String,
        previouslyResolved: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean,
        action: Int,
        arguments: android.os.Bundle? = null,
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

    private fun waitForVisibleNode(
        automation: android.app.UiAutomation,
        description: String,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + NODE_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            val root = automation.freshRoot()
            val target = root?.findDeepest(predicate)
            if (target?.isVisibleToUser == true && target.refresh()) return target

            val scroller = root?.visibleScrollContainer()
            if (scroller != null) {
                val viewport = Rect().also(scroller::getBoundsInScreen)
                val targetBounds = target?.let { Rect().also(it::getBoundsInScreen) }
                val action = if (targetBounds != null && targetBounds.bottom <= viewport.top) {
                    AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                } else {
                    AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                }
                if (!scroller.performAction(action) && action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) {
                    scroller.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
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

    private fun AccessibilityNodeInfo.nodeStrings(): List<String> = buildList {
        text?.toString()?.takeIf(String::isNotBlank)?.let(::add)
        contentDescription?.toString()?.takeIf(String::isNotBlank)?.let(::add)
        for (index in 0 until childCount) getChild(index)?.nodeStrings()?.let(::addAll)
    }

    private fun AccessibilityNodeInfo.hasAnyLabel(labels: Set<String>): Boolean = nodeStrings().any(labels::contains)

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
        const val ORIENTATION_TIMEOUT_MS = 10_000L
    }
}
