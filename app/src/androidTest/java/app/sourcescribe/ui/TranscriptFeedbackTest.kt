package app.sourcescribe.ui

import android.app.UiAutomation
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.sourcescribe.MainActivity
import app.sourcescribe.MainViewModel
import app.sourcescribe.R
import app.sourcescribe.ScreenState
import app.sourcescribe.core.Generation
import app.sourcescribe.core.JobConfig
import app.sourcescribe.core.Origin
import app.sourcescribe.core.Provenance
import app.sourcescribe.core.Segment
import app.sourcescribe.core.Source
import app.sourcescribe.core.SourceKind
import app.sourcescribe.core.TranscriptDocument
import app.sourcescribe.core.Translation
import java.util.ArrayDeque
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TranscriptFeedbackTest {
    @Test(timeout = TEST_TIMEOUT_MS)
    fun exportSuccessNoticeIsVisibleWhileTheTranscriptRemainsOpen() = assertNoticeVisible(
        code = "EXPORT_EXPORTED",
        messageResource = R.string.export_complete,
        title = "Synthetic export success",
    )

    @Test(timeout = TEST_TIMEOUT_MS)
    fun exportFailureNoticeIsVisibleWhileTheTranscriptRemainsOpen() = assertNoticeVisible(
        code = "EXPORT_FAILED",
        messageResource = R.string.export_failed,
        title = "Synthetic export failure",
    )

    /** Injects only in-memory screen state; no export, provider request, or artifact/file operation is invoked. */
    private fun assertNoticeVisible(code: String, messageResource: Int, title: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        lateinit var viewModel: MainViewModel
        lateinit var activityContext: Context

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activityContext = activity
                viewModel = ViewModelProvider(activity)[MainViewModel::class.java]
            }
            awaitInitialization(viewModel)

            val document = syntheticDocument(title)
            updateScreen(viewModel) {
                it.copy(document = document, documentName = null, message = null, exportedFolder = null)
            }
            waitForVisibleText(automation, setOf(title), "synthetic transcript title")
            instrumentation.runOnMainSync { viewModel.notice(code) }
            waitForVisibleText(automation, localized(activityContext, messageResource), "$code message")
            val visibleTitle = waitForVisibleText(automation, setOf(title), "transcript title beside $code")
            assertTrue("The transcript title is not visible while $code is shown", visibleTitle.isVisibleToUser)
        }
    }

    private fun awaitInitialization(viewModel: MainViewModel) {
        val recoveryField = MainViewModel::class.java.getDeclaredField("recovery").apply { isAccessible = true }
        val recovery = recoveryField.get(viewModel) as? Job
            ?: throw AssertionError("Could not read MainViewModel recovery job")
        val deadline = SystemClock.uptimeMillis() + INITIALIZATION_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            val state = viewModel.screen.value
            if (!state.busy && !state.preparingEngine && recovery.isCompleted) return
            SystemClock.sleep(POLL_MS)
        }
        val state = viewModel.screen.value
        throw AssertionError(
            "MainActivity's ViewModel did not finish initialization " +
                "(busy=${state.busy}, preparingEngine=${state.preparingEngine}, recoveryComplete=${recovery.isCompleted})",
        )
    }

    private fun updateScreen(viewModel: MainViewModel, transform: (ScreenState) -> ScreenState) {
        val field = MainViewModel::class.java.getDeclaredField("mutable").apply { isAccessible = true }
        val value = field.get(viewModel)
        if (value !is MutableStateFlow<*>) throw AssertionError("MainViewModel.mutable is not a MutableStateFlow")
        // Reflection erases the generic type of the known MainViewModel screen-state field.
        @Suppress("UNCHECKED_CAST")
        val state = value as MutableStateFlow<ScreenState>
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            state.value = transform(state.value)
        }
    }

    private fun syntheticDocument(title: String) = TranscriptDocument(
        artifactId = "transcript-feedback-${UUID.randomUUID()}",
        source = Source(id = "transcript-feedback-${UUID.randomUUID()}", kind = SourceKind.YOUTUBE, title = title),
        acquisition = JobConfig(),
        provenance = Provenance(Origin.YOUTUBE, Generation.UPLOADER_PROVIDED, Translation.NONE),
        language = "en",
        segments = listOf(Segment("Synthetic transcript text for export feedback.")),
        createdAt = System.currentTimeMillis(),
    )

    private fun waitForVisibleText(
        automation: UiAutomation,
        labels: Set<String>,
        description: String,
    ): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + MESSAGE_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            if (Build.VERSION.SDK_INT >= 34) automation.clearCache()
            val node = automation.rootInActiveWindow?.findDeepest { candidate ->
                candidate.text?.toString() in labels || candidate.contentDescription?.toString() in labels
            }
            if (node?.isVisibleToUser == true) return node
            SystemClock.sleep(POLL_MS)
        }
        throw AssertionError("Timed out waiting for visible $description: $labels")
    }

    private fun AccessibilityNodeInfo.findDeepest(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        val pending = ArrayDeque<AccessibilityNodeInfo>().apply { add(this@findDeepest) }
        var match: AccessibilityNodeInfo? = null
        var visited = 0
        while (pending.isNotEmpty() && visited++ < MAX_NODES) {
            val node = pending.removeFirst()
            if (predicate(node)) match = node
            for (index in 0 until node.childCount) node.getChild(index)?.let(pending::addLast)
        }
        return match
    }

    private fun localized(context: Context, resource: Int): Set<String> {
        val base = Configuration(context.resources.configuration)
        return (listOf("de", "en").map { language ->
            val configuration = Configuration(base).apply { setLocale(Locale.forLanguageTag(language)) }
            context.createConfigurationContext(configuration).resources.getString(resource)
        } + context.getString(resource)).toSet()
    }

    private companion object {
        const val TEST_TIMEOUT_MS = 60_000L
        const val INITIALIZATION_TIMEOUT_MS = 40_000L
        const val MESSAGE_TIMEOUT_MS = 3_000L
        const val RENDER_SETTLE_MS = 100L
        const val POLL_MS = 40L
        const val MAX_NODES = 512
    }
}
