package app.sourcescribe.ui

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.sourcescribe.MainActivity
import app.sourcescribe.MainViewModel
import app.sourcescribe.R
import app.sourcescribe.core.ExportFormat
import app.sourcescribe.core.JobConfig
import app.sourcescribe.core.OptionAvailability
import app.sourcescribe.core.Provider
import app.sourcescribe.core.Region
import java.util.concurrent.ConcurrentHashMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The new-source screen of round 25, measured the way `PreviewStatusLayoutTest` measures the line under a
 * preview: every state a control can be in is laid out at a phone's width, at the default font scale and at
 * 200 %, and the heights are compared. The owner's standing rule is that nothing may jump, and each of these
 * controls changes while the reader is working the one above it — a provider is chosen, a format is picked,
 * a note about what a provider cannot do appears and goes again.
 */
@RunWith(AndroidJUnit4::class)
class NewSourceLayoutTest {
    @Test
    fun theLinkFieldIsAsTallAsAnOrdinaryFieldAndGrowsOnlyWithItsOwnText() {
        // Item 11. Up to 0.3.0 the field was `minLines = 2`, so a one-line link sat at the top edge of a box
        // with an empty line under it. The fix is only a fix if the empty field, a field holding a link and
        // an ordinary one-line field of this app are all exactly the same height — and if the growth the
        // change promises is real, which the long text shows.
        // Compared against an ordinary `OutlinedTextField` carrying the same label, in the same state: a
        // Material field is not the same height empty as filled — the label sits inside it at full size while
        // it is empty and shrinks to the border once something is typed — so an empty field measured against
        // a filled one would compare two different things. What this test fixes is that the field is one line
        // and not two, which `minLines = 2` made it in both states.
        //
        // A full YouTube address does not fit on one line of a 320 dp field that also carries two icons, so
        // that is the growth case rather than the one-line case.
        val link = "https://www.youtube.com/watch?v=jNQXAC9IVRw"
        val label: @Composable () -> Unit = { Text(stringResource(R.string.source_hint)) }
        val states = linkedMapOf<String, @Composable () -> Unit>(
            "empty" to { SourceLinkField("", {}, enabled = true, onNotice = {}) },
            "short" to { SourceLinkField("x", {}, enabled = true, onNotice = {}) },
            "a long link" to { SourceLinkField(link, {}, enabled = true, onNotice = {}) },
            "an ordinary empty field" to { OutlinedTextField("", {}, Modifier, label = label) },
            "an ordinary short field" to { OutlinedTextField("x", {}, Modifier, label = label) },
        )
        val heights = measure(states)

        for (scale in SCALES) {
            val empty = heights.getValue(key(scale, "empty"))
            val short = heights.getValue(key(scale, "short"))
            val long = heights.getValue(key(scale, "a long link"))
            assertEquals("Font scale $scale: the empty field is not as tall as an ordinary empty field",
                heights.getValue(key(scale, "an ordinary empty field")), empty)
            assertEquals("Font scale $scale: a short entry is not as tall as it is in an ordinary field",
                heights.getValue(key(scale, "an ordinary short field")), short)
            assertTrue("Font scale $scale: the field did not grow with a full address ($long vs $short)",
                long > short)
        }
    }

    @Test
    fun theProviderListKeepsItsHeightWhicheverProviderIsChosenAndWhateverKeysAreStored() {
        // Item 7. The list replaced a dropdown, and each row carries two lines the dropdown did not: whether
        // a key for that provider is stored, and the model it would run. Both change under the reader — a key
        // is added in settings, a model is picked below — and the block sits above the rest of the screen.
        val keyed = Provider.entries.map { Triple("key-${it.name}", it, Region.US) }
        val states = LinkedHashMap<String, @Composable () -> Unit>()
        for ((name, credentials) in listOf("no keys" to emptyList(), "every key" to keyed)) {
            states["nothing chosen, $name"] = { ProviderList(JobConfig(), credentials, true, {}) {} }
            for (provider in Provider.entries) for (model in MainViewModel.models(provider)) {
                val config = JobConfig(provider = provider, model = model)
                states["$provider $model, $name"] = { ProviderList(config, credentials, true, {}) {} }
            }
        }
        assertSameHeightPerScale("provider list", states.keys, measure(states))
    }

    @Test
    fun theExportFormatChipsKeepTheirHeightForEverySelection() {
        // Item 12. A chip that grew a tick or a thicker border when selected would rewrap the row and change
        // the height of the block, which sits above the rest of the advanced options.
        val selections = linkedMapOf(
            "one" to setOf(ExportFormat.MARKDOWN),
            "the last one" to setOf(ExportFormat.RAW),
            "three" to setOf(ExportFormat.MARKDOWN, ExportFormat.SRT, ExportFormat.VTT),
            "all" to ExportFormat.entries.toSet(),
        )
        val states = LinkedHashMap<String, @Composable () -> Unit>()
        for ((name, selection) in selections) states[name] = { ExportFormatChips(selection, true) {} }
        assertSameHeightPerScale("export format chips", states.keys, measure(states))
    }

    @Test
    fun theNoteUnderASwitchComesAndGoesWithoutMovingTheSwitchesBelowIt() {
        // Item 8. The note says why an option cannot be used, and which note it is — or whether there is one
        // at all — changes with the provider chosen a few rows above. The switch is what the reader is
        // looking at; everything under it has to stay where it was.
        val notes = Provider.entries.map { "Not available with ${providerNameOf(it)}" } + "Choose a provider first"
        val shown = linkedMapOf("no note" to "") + notes.associateBy { it }
        val states = LinkedHashMap<String, @Composable () -> Unit>()
        for ((name, note) in shown) {
            states[name] = {
                Toggle(R.string.diarization, checked = false, enabled = false, supporting = note,
                    supportingReserve = notes) {}
            }
        }
        assertSameHeightPerScale("option note", states.keys, measure(states))
    }

    @Test
    fun expertAvailabilityStaysInACompactStableSlot() {
        val states = linkedMapOf<String, @Composable () -> Unit>(
            // Keep the help target present in both measurements; without it the label gets more width, so
            // font scale 2 wraps differently and the comparison falsely attributes that width change to status.
            "switch with help" to {
                Toggle(R.string.diarization, checked = false, enabled = true,
                    info = HelpTopic.DIARIZATION, openHelp = {}, helpExplanation = "") {}
            },
            "available" to {
                Toggle(R.string.diarization, checked = false, enabled = true,
                    availabilityStatus = stringResource(R.string.expert_status_available),
                    info = HelpTopic.DIARIZATION, openHelp = {}, helpExplanation = "") {}
            },
            "provider needed" to {
                Toggle(R.string.diarization, checked = false, enabled = true,
                    availabilityStatus = stringResource(R.string.expert_status_choose_provider),
                    info = HelpTopic.DIARIZATION, openHelp = {}, helpExplanation = "Choose a provider first") {}
            },
            "provider limitation" to {
                Toggle(R.string.diarization, checked = true, enabled = true,
                    availabilityStatus = stringResource(R.string.expert_status_unavailable),
                    info = HelpTopic.DIARIZATION, openHelp = {}, helpExplanation = "Not available with AssemblyAI") {}
            },
        )
        val heights = measure(states)
        assertSameHeightPerScale("expert availability status", states.keys.drop(1), heights)
        for (scale in SCALES) {
            val plain = heights.getValue(key(scale, "switch with help"))
            val withStatus = heights.getValue(key(scale, "provider limitation"))
            assertTrue("Font scale $scale: capability status added more than one text line ($plain vs $withStatus)",
                withStatus <= plain + (48 * InstrumentationRegistry.getInstrumentation()
                    .targetContext.resources.displayMetrics.density).toInt())
        }
    }

    @Test
    fun contextAvailabilityWrapsAtLargeTextAndReservesEveryStatusHeight() {
        // The old one-line ellipsis hid "Choose provider" at 200 %; all three states now share the wrapped
        // height of the longest localized status while the full explanation remains behind its help button.
        val states = linkedMapOf<String, @Composable () -> Unit>(
            "one line" to {
                Text("x", Modifier.width(104.dp), style = MaterialTheme.typography.labelSmall)
            },
            "available" to { ExpertAvailabilityStatus(OptionAvailability.AVAILABLE) },
            "provider needed" to { ExpertAvailabilityStatus(OptionAvailability.PROVIDER_NOT_CHOSEN) },
            "provider limitation" to { ExpertAvailabilityStatus(OptionAvailability.UNSUPPORTED_BY_PROVIDER) },
        )
        val heights = measure(states)
        assertSameHeightPerScale("context availability status", states.keys.drop(1), heights)
        assertTrue("Font scale 2: provider guidance did not wrap in its 104 dp slot",
            heights.getValue(key(2f, "provider needed")) > heights.getValue(key(2f, "one line")))
    }

    private fun providerNameOf(provider: Provider) = when (provider) {
        Provider.ASSEMBLYAI -> "AssemblyAI"
        Provider.OPENAI -> "OpenAI"
        Provider.GROQ -> "Groq"
    }

    /**
     * Lays every state out once per font scale in one scrolling column and answers the measured heights,
     * keyed by scale and name. The activity is the app's own, as `PreviewStatusLayoutTest` does it, so the
     * theme and the density are the ones the screen really runs under.
     */
    private fun measure(states: Map<String, @Composable () -> Unit>): Map<String, Int> {
        val keys = SCALES.flatMap { scale -> states.keys.map { key(scale, it) } }.toSet()
        val heights = ConcurrentHashMap<String, Int>()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    MaterialTheme {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            val density = LocalDensity.current.density
                            for (scale in SCALES) {
                                CompositionLocalProvider(LocalDensity provides Density(density, scale)) {
                                    for ((name, content) in states) {
                                        Box(Modifier.width(WIDTH).onSizeChanged { heights[key(scale, name)] = it.height }) {
                                            content()
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
        assertEquals("Slots that were laid out", keys, heights.keys.toSet())
        return heights
    }

    private fun assertSameHeightPerScale(what: String, names: Collection<String>, heights: Map<String, Int>) {
        for (scale in SCALES) {
            val measured = names.associateWith { heights.getValue(key(scale, it)) }
            assertTrue("Font scale $scale, $what: a state shows nothing: $measured", measured.values.all { it > 0 })
            assertEquals("Font scale $scale, $what: the heights differ: $measured", 1, measured.values.toSet().size)
        }
    }

    private companion object {
        val WIDTH = 320.dp
        val SCALES = listOf(1f, 2f)
        const val TIMEOUT_NANOS = 60L * 1_000_000_000L
        const val POLL_MS = 50L

        fun key(scale: Float, name: String) = "$scale|$name"
    }
}
