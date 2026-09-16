package app.sourcescribe.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.sourcescribe.DraftEdits
import app.sourcescribe.MainViewModel
import app.sourcescribe.R
import app.sourcescribe.ScreenState
import app.sourcescribe.SourcePreview
import app.sourcescribe.TypedSetting
import app.sourcescribe.core.*
import java.util.Locale

/**
 * Which set of sources is currently checked, or null while none is.
 *
 * The screen scrolls to the results once per check (item 10). What may not restart that scroll is a change to
 * a track, an option or the provider: each of those rebuilds `state.previews` as a new list with new
 * configurations in it, so anything that watched the list itself would scroll the reader back to the top
 * every time they worked a control. The identity of the checked sources is what a check changes and an option
 * does not.
 */
internal fun checkedSourcesKey(previews: List<SourcePreview>): String? =
    previews.takeIf { it.isNotEmpty() }?.joinToString("|") { it.resolved.source.id }

@Composable
internal fun NewSourceScreen(
    input: String,
    setInput: (String) -> Unit,
    config: JobConfig,
    change: (JobConfig) -> Unit,
    type: (TypedSetting, (JobConfig) -> JobConfig) -> Boolean,
    state: ScreenState,
    settings: AppSettings,
    openHelp: (HelpTopic) -> Unit,
    inspect: () -> Unit,
    onPreset: (JobConfig) -> Unit,
    onTrack: (String, String?, String?) -> Unit,
    onStart: () -> Unit,
    onCancelPreview: () -> Unit,
    onImport: () -> Unit,
    onNotice: (String) -> Unit,
    onSaveKeyterms: (String) -> Unit,
    onDeleteKeyterms: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    val checked = state.previews.isNotEmpty()
    // Item 10: after a check the view goes to the top of the new content, where the source card now sits with
    // the results directly under it. Once per check, and remembered across a visit to another page, so
    // coming back to this screen does not throw away where the reader had scrolled to.
    val sourcesKey = checkedSourcesKey(state.previews)
    var scrolledFor by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(sourcesKey) {
        if (sourcesKey == null) scrolledFor = null
        else if (sourcesKey != scrolledFor) {
            listState.animateScrollToItem(0)
            scrolledFor = sourcesKey
        }
    }
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Every item carries a key of its own. A LazyColumn without them holds its scroll position by index,
        // so an item appearing above the viewport shifts everything under it.
        if (!checked) {
            item(key = "input") {
                SourceLinkField(input, setInput, enabled = !state.starting, onNotice = onNotice)
                Text(stringResource(R.string.source_help), style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp))
            }
            if (settings.presets.isNotEmpty()) item(key = "presets") {
                Choice(stringResource(R.string.preset), stringResource(R.string.choose), settings.presets.keys.toList(), { it },
                    enabled = !state.starting, info = HelpTopic.PRESETS, openHelp = openHelp) { name ->
                    onPreset(settings.presets.getValue(name))
                }
            }
        } else {
            // The input has collapsed into the head of each preview card, which names the source and offers
            // "Change"; the results follow inside the same card instead of below a second copy of the title.
            item(key = "preview-title") { SectionTitle(R.string.preview, HelpTopic.WORKFLOW, openHelp) }
            items(state.previews, key = { "preview-${it.resolved.source.id}" }) { preview ->
                PreviewCard(preview, state, onTrack, openHelp, onCancelPreview)
            }
        }
        item(key = "config") {
            ConfigControls(config, change, type, state.draftEdits, state.credentials.map { Triple(it.id, it.provider, it.region) },
                state.previews.any { it.resolved.source.kind == SourceKind.LOCAL_AUDIO }, settings.keytermSets,
                onSaveKeyterms, onDeleteKeyterms, openHelp, enabled = !state.starting)
        }
        if (!checked) item(key = "actions") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // As tall as the sentence while it is empty too, so the buttons stay where they are when it comes and goes.
                ReservedText(
                    if (state.waitingForEngine) stringResource(R.string.engine_preparing) else "",
                    listOf(stringResource(R.string.engine_preparing)),
                    MaterialTheme.typography.bodySmall,
                )
                Button(inspect, Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = input.isNotBlank() && !state.busy) {
                    Text(stringResource(R.string.inspect_source))
                }
                OutlinedButton(onImport, Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = !state.busy) {
                    Text(stringResource(R.string.import_audio))
                }
            }
        } else item(key = "start") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.previews.any { it.config.provider != null && AcquisitionPlanner.mayUseSpeechToText(it.config.mode) }) {
                    Text(stringResource(R.string.upload_help), style = MaterialTheme.typography.bodySmall)
                }
                Button(onStart, Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = !state.busy && state.previews.all {
                    MainViewModel.previewError(it, state.credentials) == null
                }) { Text(stringResource(R.string.start_jobs)) }
                TextButton(onCancelPreview, Modifier.fillMaxWidth(), enabled = !state.starting) { Text(stringResource(R.string.cancel)) }
            }
        }
    }
}

/**
 * The field the source is typed or pasted into (item 11).
 *
 * Up to 0.3.0 it was `minLines = 2`, so an ordinary one-line link sat against the top edge of a box with an
 * empty line under it. One line is the height it starts at now, the text sits in the middle of it as it does
 * in every other field of this app, and it grows to five lines for shared text that really is that long —
 * growth the reader's own typing causes and can see, rather than a permanent gap.
 */
@Composable
internal fun SourceLinkField(
    input: String,
    setInput: (String) -> Unit,
    enabled: Boolean,
    onNotice: (String) -> Unit,
) {
    val context = LocalContext.current
    OutlinedTextField(input, setInput, Modifier.fillMaxWidth(), enabled = enabled,
        label = { Text(stringResource(R.string.source_hint)) }, minLines = 1, maxLines = 5,
        trailingIcon = {
            Row {
                if (input.isNotEmpty()) IconButton({ setInput("") }, enabled = enabled) {
                    Icon(painterResource(R.drawable.ic_close), stringResource(R.string.clear_input), Modifier.size(20.dp))
                }
                IconButton({
                    val pasted = clipboardText(context)
                    if (pasted == null) onNotice("CLIPBOARD_EMPTY") else setInput(pasted)
                }, enabled = enabled) {
                    Icon(painterResource(R.drawable.ic_paste), stringResource(R.string.paste_clipboard), Modifier.size(20.dp))
                }
            }
        })
}

private fun clipboardText(context: Context): String? {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
    val clip = manager.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context).toString().take(32769).ifBlank { null }
}

@Composable
private fun PreviewCard(
    preview: SourcePreview,
    state: ScreenState,
    onTrack: (String, String?, String?) -> Unit,
    openHelp: (HelpTopic) -> Unit,
    onChange: () -> Unit,
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val source = preview.resolved.source
            // The head of this card is the collapsed input (item 10): what was checked, and the way back to
            // the field it was typed into. "Change" drops every preview and shows the input again with its
            // text, because all previews on this screen came out of that one text.
            Row(verticalAlignment = Alignment.Top) {
                Text(source.title ?: source.fileName ?: source.videoId.orEmpty(), Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                TextButton(onChange, Modifier.heightIn(min = 48.dp), enabled = !state.starting) {
                    Text(stringResource(R.string.source_change))
                }
            }
            Text(listOf(
                source.channel ?: stringResource(R.string.unknown),
                source.durationMs?.let(::duration) ?: stringResource(R.string.unknown),
                source.publishedDate?.let(::publishedDate) ?: stringResource(R.string.unknown),
            ).joinToString(" · "))
            // An imported file has no address, and an empty text in its place still took a line of height.
            source.canonicalUrl?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (source.kind == SourceKind.YOUTUBE) Text(stringResource(R.string.whole_video), style = MaterialTheme.typography.labelLarge)
            if (preview.previousJob != null) Text(stringResource(R.string.duplicate_warning), color = MaterialTheme.colorScheme.error)

            if (AcquisitionPlanner.usesCaptions(preview.config.mode)) {
                val choices = TrackSelection.captions(preview.resolved, preview.config.copy(captionTrackId = null))
                if (choices.isEmpty()) Text(stringResource(R.string.no_captions))
                else Choice(
                    label = stringResource(R.string.caption_track),
                    selected = choices.firstOrNull { it.id == preview.config.captionTrackId }?.let { captionTrackTitle(it) }
                        ?: stringResource(R.string.choose),
                    options = choices,
                    name = { captionTrackTitle(it) },
                    optionName = { captionTrackOption(it) },
                    enabled = !state.starting,
                    placeholder = stringResource(R.string.choose),
                    info = HelpTopic.CAPTION_TRACK,
                    openHelp = openHelp,
                ) { onTrack(source.id, it.id, null) }
            }

            if (AcquisitionPlanner.mayUseSpeechToText(preview.config.mode) && source.kind == SourceKind.YOUTUBE) {
                val described = remember(preview.resolved.audio, source.durationMs) {
                    AudioTracks.describe(preview.resolved.audio, source.durationMs)
                }
                if (described.isEmpty()) Text(stringResource(R.string.no_audio))
                else Choice(
                    label = stringResource(R.string.audio_track),
                    selected = described.firstOrNull { it.track.id == preview.config.audioTrackId }?.let { audioTrackTitle(it) }
                        ?: stringResource(R.string.choose),
                    options = described,
                    name = { audioTrackTitle(it) },
                    optionName = { audioTrackOption(it) },
                    enabled = !state.starting,
                    placeholder = stringResource(R.string.choose),
                    supporting = described.firstOrNull { it.track.id == preview.config.audioTrackId }
                        ?.let { audioTrackDetail(it) } ?: stringResource(R.string.audio_track_none_chosen),
                    info = HelpTopic.AUDIO_TRACK,
                    openHelp = openHelp,
                ) { onTrack(source.id, null, it.track.id) }
            }

            if (AcquisitionPlanner.mayUseSpeechToText(preview.config.mode)) {
                val capability = MainViewModel.capabilities(preview.config)
                // The figure the reader is shown is the one the budget is measured against, from the same
                // function. Length times hourly rate left out the surcharges for speakers and keyterms and
                // the minimum length Groq bills, so a budget chosen from what stood here could be refused
                // by a check that had counted differently.
                //
                // Too long for this job to run at all. The rule is in `MainViewModel` and not written out
                // here, for the same reason the sum above is: a screen that computes its own version of a
                // rule the run enforces elsewhere is the defect this review loop has found more often than
                // any other. Saying "price unknown" instead would be an untrue statement about the tariff
                // rather than about the source; what it is about the source is what the line below says.
                val tooLong = MainViewModel.sourceTooLong(source.durationMs)
                // Priced as Start will create the job, which is also what the error line below judges. A term list
                // stored with a blank entry has no price as it stands, and Start drops that entry.
                val estimate = source.durationMs
                    ?.takeIf { !tooLong }
                    ?.let { MainViewModel.estimatedCostMicrousd(MainViewModel.configurationForStart(preview.config, state.credentials), it) }
                // The length beside the price, because the two belong together and because it is now the
                // source's own number rather than a limit anybody typed. Always shown, so it cannot move the
                // line under it; for a source that states no length it says so, which is also why that source
                // cannot start (`SOURCE_DURATION_UNKNOWN` below).
                Text(stringResource(R.string.source_length, source.durationMs?.let(::duration)
                    ?: stringResource(R.string.unknown)), style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val priceDate = capability?.priceAsOf.orEmpty()
                    ReservedText(
                        if (estimate != null && capability != null) stringResource(R.string.estimated_cost,
                            String.format(Locale.ROOT, "%.4f", estimate / 1_000_000.0), priceDate)
                        else if (tooLong) stringResource(R.string.cost_source_too_long)
                        else stringResource(R.string.price_unknown),
                        // As tall as the tallest of the three at this width and font, whichever is shown. Choosing
                        // another provider or model swaps a sentence here for a price, and the rest of the screen
                        // must not move while the reader is working the control. Until round 16 a two-line cap did
                        // that job, and round 16 measured what its comment called unmeasured: at font scale 2.0 on
                        // emulator-5556 the estimate lost its end to the ellipsis, in German the whole date.
                        listOf(stringResource(R.string.estimated_cost, "000.0000", priceDate.ifEmpty { "0000-00-00" }),
                            stringResource(R.string.cost_source_too_long), stringResource(R.string.price_unknown)),
                        MaterialTheme.typography.bodySmall, Modifier.weight(1f),
                    )
                    InfoButton(HelpTopic.COST, openHelp)
                }
            }

            PreviewStatus(
                MainViewModel.previewError(preview, state.credentials),
                source.durationMs,
                openHelp,
            )
        }
    }
}

/**
 * The line under a preview's settings: whether this source can start and, where it cannot, why.
 *
 * What it says switches while the reader works the controls above: a missing provider reads as one sentence, a
 * blank keyterm as another, a source past the app's ceiling as a warning of its own, and a source that can start
 * as a short sentence. The line always keeps the height of the tallest of them, measured at this width
 * and font scale, so the start button under the card stays where it is whatever the line says (defect 36). The cost
 * row's two-line cap would not do here: these texts were not kept short for a cap, and the longest runs past a
 * hundred characters in both languages.
 */
@Composable
internal fun PreviewStatus(
    error: String?,
    durationMs: Long?,
    openHelp: (HelpTopic) -> Unit,
) {
    val ready = stringResource(R.string.preview_ready)
    val sentences = listOf(ready) + MainViewModel.PREVIEW_ERRORS_SHOWN_AS_TEXT.map { messageText(it) }
    // The warning only appears for a source of known length, and then with this source's numbers in it.
    val warning = durationMs?.let { length -> @Composable { LengthLimitWarning(length) {} } }
    val alternatives: List<@Composable () -> Unit> =
        sentences.map { sentence -> @Composable { Text(sentence) } } + listOfNotNull(warning)
    ReservedBox(alternatives) {
        when (error) {
            null -> Text(ready, color = MaterialTheme.colorScheme.primary)
            "SOURCE_LONGER_THAN_LIMIT" -> LengthLimitWarning(requireNotNull(durationMs), openHelp)
            else -> Text(messageText(error), color = MaterialTheme.colorScheme.error)
        }
    }
}

/**
 * Names both numbers: how long this source runs and how much the app processes in one job.
 *
 * Up to 0.3.0 this warning carried a button that raised the typed length limit, because that limit was the
 * usual reason a perfectly ordinary video was refused. There is no typed limit any more, so there is nothing
 * to raise: what is left is the app's own ceiling, and a source past it cannot run as one job at all.
 */
@Composable
private fun LengthLimitWarning(durationMs: Long, openHelp: (HelpTopic) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.source_beyond_ceiling, duration(durationMs), limitDuration(JobLimits.MAX_AUDIO_SECONDS)),
            Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
        InfoButton(HelpTopic.LIMITS, openHelp)
    }
}

/**
 * The speech-to-text providers as a list rather than a dropdown (item 7).
 *
 * A dropdown showed one name and hid the two facts that decide the choice: whether a key for that provider is
 * stored at all, and which model it would run. Both now stand on every row, and the row that is selected is
 * the one the job uses. Each row keeps a fixed height — the two lines under the name are reserved for the
 * longest they can say — so selecting a provider moves nothing under the list.
 */
@Composable
internal fun ProviderList(
    config: JobConfig,
    credentials: List<Triple<String, Provider, Region>>,
    enabled: Boolean,
    openHelp: (HelpTopic) -> Unit,
    choose: (Provider) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.provider), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            InfoButton(HelpTopic.PROVIDERS, openHelp)
        }
        val keyStored = stringResource(R.string.provider_key_stored)
        val keyMissing = stringResource(R.string.provider_key_missing)
        Provider.entries.forEach { provider ->
            val selected = config.provider == provider
            val hasKey = credentials.any { it.second == provider }
            Surface(
                onClick = { choose(provider) },
                modifier = Modifier.fillMaxWidth().semantics { role = Role.RadioButton },
                shape = MaterialTheme.shapes.small,
                enabled = enabled,
                // An always-present border of the same width, only its colour changes: a border that appears
                // with the selection would move the text inside the row by its own thickness.
                border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant),
                color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                contentColor = if (!enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    else if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp).heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RadioButton(selected, null, enabled = enabled)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(providerName(provider), style = MaterialTheme.typography.titleMedium)
                        ReservedText(if (hasKey) keyStored else keyMissing, listOf(keyStored, keyMissing),
                            MaterialTheme.typography.bodySmall)
                        val models = MainViewModel.models(provider)
                        ReservedText(
                            stringResource(R.string.provider_row_model, MainViewModel.modelInUse(config, provider)),
                            models.map { stringResource(R.string.provider_row_model, it) },
                            MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigControls(
    config: JobConfig,
    change: (JobConfig) -> Unit,
    type: (TypedSetting, (JobConfig) -> JobConfig) -> Boolean,
    edits: DraftEdits,
    credentials: List<Triple<String, Provider, Region>>,
    localAudio: Boolean,
    keytermSets: Map<String, List<String>>,
    saveKeyterms: (String) -> Unit,
    deleteKeyterms: (String) -> Unit,
    openHelp: (HelpTopic) -> Unit,
    enabled: Boolean = true,
) {
    var advanced by rememberSaveable { mutableStateOf(false) }
    val cap = MainViewModel.capabilities(config)
    // Items 8 and 9 in one place: what an option can do in this job. An option nothing in the chosen mode
    // reads is left out — the mode control is a few rows above and the group moves as a whole — and one the
    // chosen provider cannot do stays visible with the reason written under it.
    fun availability(option: ExpertOption) = ExpertOptions.availability(option, config, cap)
    fun shown(option: ExpertOption) = availability(option) != OptionAvailability.POINTLESS_FOR_MODE
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (localAudio) Text(stringResource(R.string.local_stt_mode))
        else Choice(stringResource(R.string.mode), stringResource(modeLabel(config.mode)), AcquisitionMode.entries,
            { modeName(it) }, enabled = enabled, info = HelpTopic.MODES, openHelp = openHelp) { change(config.copy(mode = it)) }
        if (AcquisitionPlanner.mayUseSpeechToText(config.mode)) {
            ProviderList(config, credentials, enabled, openHelp) { provider ->
                val key = credentials.firstOrNull { it.second == provider }
                change(MainViewModel.modelDefaults(config.copy(provider = provider, credentialId = key?.first,
                    region = key?.third ?: Region.US), MainViewModel.models(provider).first()))
            }
            config.provider?.let { provider ->
                Choice(stringResource(R.string.model), config.model.orEmpty(), MainViewModel.models(provider), { it },
                    enabled = enabled) { change(MainViewModel.modelDefaults(config, it)) }
                val keys = credentials.filter { it.second == provider }
                if (keys.isNotEmpty()) Choice(stringResource(R.string.credentials),
                    keys.firstOrNull { it.first == config.credentialId }?.let { "${regionName(it.third)} · ••••${it.first.takeLast(4)}" }
                        ?: stringResource(R.string.choose),
                    keys, { "${regionName(it.third)} · ••••${it.first.takeLast(4)}" }, enabled = enabled,
                    info = HelpTopic.API_KEY, openHelp = openHelp, placeholder = stringResource(R.string.choose)) {
                    change(config.copy(credentialId = it.first, region = it.third, uploadApproved = false))
                }
            }
            if (config.credentialId == null) Text(stringResource(R.string.no_provider_help), style = MaterialTheme.typography.bodySmall)
            LimitFields(config, type, edits, openHelp, enabled)
        }
        TextButton({ advanced = !advanced }, enabled = enabled) { Text(stringResource(R.string.advanced)) }
        if (advanced) {
            // These five only steer which caption track is read, so a mode that reads none leaves them out.
            if (shown(ExpertOption.ORIGINAL_LANGUAGE)) {
                Toggle(R.string.original_language, config.preferOriginalLanguage, enabled) { change(config.copy(preferOriginalLanguage = it)) }
                Toggle(R.string.uploader_captions, config.allowUploaderCaptions, enabled,
                    info = HelpTopic.CAPTION_TRACK, openHelp = openHelp) { change(config.copy(allowUploaderCaptions = it)) }
                Toggle(R.string.automatic_captions, config.allowAutomaticCaptions, enabled) { change(config.copy(allowAutomaticCaptions = it)) }
                Toggle(R.string.translated_captions, config.allowTranslatedCaptions, enabled) { change(config.copy(allowTranslatedCaptions = it)) }
                DraftTextField(edits.epoch(TypedSetting.CAPTION_LANGUAGES), config.preferredLanguages.joinToString(","), { typed ->
                    type(TypedSetting.CAPTION_LANGUAGES) { it.copy(preferredLanguages = typed.split(',').map(String::trim).filter(String::isNotBlank)) }
                }, enabled, label = { Text(stringResource(R.string.languages)) })
            }
            // Read in exactly one branch of `AcquisitionPlanner.plan`: captions first, a provider only if
            // fetching them failed. In every other mode this switch did nothing at all (item 9).
            if (shown(ExpertOption.FALLBACK_ON_CAPTION_ERROR)) {
                Toggle(R.string.fallback_errors, config.fallbackOnCaptionError, enabled) { change(config.copy(fallbackOnCaptionError = it)) }
            }
            if (shown(ExpertOption.STT_LANGUAGE)) {
                DraftTextField(edits.epoch(TypedSetting.STT_LANGUAGE), config.language.orEmpty(), { typed ->
                    type(TypedSetting.STT_LANGUAGE) { it.copy(language = typed.ifBlank { null }) }
                }, enabled, label = { Text(stringResource(R.string.stt_language)) })
            }
            // The four options a provider decides, plus the keyterm sets that feed one of them. All four share
            // one mode rule — a job that may reach a provider — so one of them stands for the group here.
            if (shown(ExpertOption.DIARIZATION)) {
                val notes = optionNotes()
                // What the model cannot do stays operable while it is still set, because it was set for a model
                // chosen before and the preview refuses the job as UNSUPPORTED_OPTION until it is gone. Enabled
                // only by the capability, a switch that was on sat greyed out where it could not be turned off.
                OptionToggle(ExpertOption.DIARIZATION, R.string.diarization, config.diarization, config, cap, notes,
                    enabled, HelpTopic.DIARIZATION, openHelp) { change(config.copy(diarization = it)) }
                OptionToggle(ExpertOption.WORD_TIMESTAMPS, R.string.word_times, config.wordTimestamps, config, cap, notes,
                    enabled, HelpTopic.TIMESTAMPS, openHelp) { change(config.copy(wordTimestamps = it)) }
                OptionToggle(ExpertOption.SEGMENT_TIMESTAMPS, R.string.segment_times, config.segmentTimestamps, config, cap, notes,
                    enabled, null, openHelp) { change(config.copy(segmentTimestamps = it)) }
                // Always in its place, so choosing a model that takes no terms moves nothing below it.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.context_terms), Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge)
                    InfoButton(HelpTopic.CONTEXT_TERMS, openHelp)
                }
                ReservedText(optionNote(ExpertOption.CONTEXT_TERMS, config, cap), notes,
                    MaterialTheme.typography.bodySmall, Modifier.padding(horizontal = 4.dp),
                    MaterialTheme.colorScheme.onSurfaceVariant)
                val termsOperable = enabled && ExpertOptions.operable(ExpertOption.CONTEXT_TERMS, config, cap)
                DraftTextField(edits.epoch(TypedSetting.CONTEXT_TERMS), config.contextTerms.joinToString("\n"), { typed ->
                    type(TypedSetting.CONTEXT_TERMS) { it.copy(contextTerms = ContextTerms.withoutBlanks(typed.lines())) }
                }, termsOperable, minLines = 2, maxLines = 4)
                KeytermSetControls(config, keytermSets, termsOperable, openHelp, change, saveKeyterms, deleteKeyterms)
            }
            Toggle(R.string.retain_raw, config.retainRaw, enabled, info = HelpTopic.RETENTION, openHelp = openHelp) {
                change(config.copy(retainRaw = it))
            }
            Toggle(R.string.unmetered, config.networkPolicy == NetworkPolicy.UNMETERED, enabled) {
                change(config.copy(networkPolicy = if (it) NetworkPolicy.UNMETERED else NetworkPolicy.ANY))
            }
            // Nothing is downloaded in a caption-only job, so there is no downloaded audio to keep (item 9).
            if (shown(ExpertOption.AUDIO_RETENTION)) {
                Choice(stringResource(R.string.audio_retention), audioRetentionName(config.audioRetention), AudioRetention.entries,
                    { audioRetentionName(it) }, enabled = enabled, info = HelpTopic.RETENTION, openHelp = openHelp) {
                    change(config.copy(audioRetention = it))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.export_formats), Modifier.weight(1f))
                InfoButton(HelpTopic.EXPORT_FORMATS, openHelp)
            }
            ExportFormatChips(config.exportFormats, enabled) { selection ->
                change(config.copy(exportFormats = selection,
                    retainRaw = config.retainRaw || ExportFormat.RAW in selection))
            }
        }
    }
}

/**
 * Which formats an export writes, as chips (item 12).
 *
 * Six checkbox rows stood directly against each other, the label of one a few pixels under the box of the
 * next. Chips give each format a gap and a shape of its own. A chip carries no leading tick and its border
 * keeps the same width whether or not it is selected, so selecting one changes its colours and not its size:
 * the points at which the row wraps, and with them the height of the whole block, do not move. The touch
 * target stays at least 48 dp tall, the same as the rows it replaces.
 *
 * At least one format stays selected. Nothing else on this screen would say what an export with no format is
 * meant to write, and the settings file refuses such a configuration outright.
 */
@Composable
internal fun ExportFormatChips(
    formats: Set<ExportFormat>,
    enabled: Boolean,
    change: (Set<ExportFormat>) -> Unit,
) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExportFormat.entries.forEach { format ->
            val selected = format in formats
            FilterChip(
                selected = selected,
                onClick = {
                    val selection = if (selected) formats - format else formats + format
                    if (selection.isNotEmpty()) change(selection)
                },
                label = { Text(formatName(format)) },
                modifier = Modifier.heightIn(min = 48.dp),
                enabled = enabled,
                border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant),
            )
        }
    }
}

/** Every sentence an option note can be, so the line that holds one keeps its height while it changes. */
@Composable
private fun optionNotes(): List<String> =
    Provider.entries.map { stringResource(R.string.option_unavailable, providerName(it)) } +
        stringResource(R.string.option_needs_provider)

/** Why this option cannot be used, or an empty string while it can. */
@Composable
private fun optionNote(option: ExpertOption, config: JobConfig, cap: ProviderCapabilities?): String =
    when (ExpertOptions.availability(option, config, cap)) {
        OptionAvailability.UNSUPPORTED_BY_PROVIDER -> stringResource(R.string.option_unavailable,
            config.provider?.let(::providerName) ?: stringResource(R.string.unknown))
        OptionAvailability.PROVIDER_NOT_CHOSEN -> stringResource(R.string.option_needs_provider)
        else -> ""
    }

@Composable
private fun OptionToggle(
    option: ExpertOption,
    @StringRes label: Int,
    checked: Boolean,
    config: JobConfig,
    cap: ProviderCapabilities?,
    notes: List<String>,
    enabled: Boolean,
    info: HelpTopic?,
    openHelp: (HelpTopic) -> Unit,
    change: (Boolean) -> Unit,
) {
    Toggle(
        label = label,
        checked = checked,
        enabled = enabled && ExpertOptions.operable(option, config, cap),
        supporting = optionNote(option, config, cap),
        supportingReserve = notes,
        info = info,
        openHelp = openHelp,
        change = change,
    )
}

/**
 * Keyterm lists saved under a name (item 13).
 *
 * The same field holds the name of the set being loaded and the name it would be saved under, because those
 * are the same thing to a reader: pick "technical", change a term, save it back. Both buttons are always
 * there and only their enabled state changes, so nothing moves while a name is typed.
 */
@Composable
private fun KeytermSetControls(
    config: JobConfig,
    sets: Map<String, List<String>>,
    enabled: Boolean,
    openHelp: (HelpTopic) -> Unit,
    change: (JobConfig) -> Unit,
    save: (String) -> Unit,
    delete: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    val names = remember(sets) { KeytermSets.names(sets) }
    val none = stringResource(R.string.keyterm_set_none)
    Choice(
        label = stringResource(R.string.keyterm_sets),
        selected = name.ifBlank { none },
        options = names,
        name = { it },
        enabled = enabled && names.isNotEmpty(),
        placeholder = none,
        info = HelpTopic.CONTEXT_TERMS,
        openHelp = openHelp,
    ) { chosen ->
        name = chosen
        change(config.copy(contextTerms = sets[chosen].orEmpty()))
    }
    OutlinedTextField(name, { name = it.take(KeytermSets.MAX_NAME_LENGTH) }, Modifier.fillMaxWidth(),
        enabled = enabled, singleLine = true, label = { Text(stringResource(R.string.keyterm_set_name)) })
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton({ save(name) }, Modifier.weight(1f).heightIn(min = 48.dp),
            enabled = enabled && name.isNotBlank() && ContextTerms.charged(config.contextTerms)) {
            Text(stringResource(R.string.keyterm_set_save))
        }
        TextButton({ delete(name); name = "" }, Modifier.weight(1f).heightIn(min = 48.dp),
            enabled = enabled && name.trim() in sets) {
            Text(stringResource(R.string.keyterm_set_delete))
        }
    }
}

/**
 * A text field over one [TypedSetting] of the draft that shows what was typed into it, not the draft as it
 * comes back.
 *
 * A keystroke hands its value to the view model at once, and the draft reaches the screen a frame or two
 * later. Several keys can be typed in between, so the value a field is composed with can be older than its
 * own text, and every earlier version of these fields went wrong on that. Bound straight to the draft, a
 * field can be handed the older value, and the next key then lands after it. Holding the text and comparing
 * the draft that comes back against it, to follow a change made elsewhere — a preset, a job prepared again,
 * a saved keyterm set — cannot tell such a change from a late value of the field's own: the
 * list fields reset the text to a late list mid-word ("Kubernetes" came out as "Kuberes" on a device in
 * round 15), and the budget typed fast as `0.123456` read `0.134562` in round 16. Remembering the lists
 * handed on until they came back narrowed that, and could still leave a change from outside unshown when it
 * matched one of them.
 *
 * So this field does not compare. [epoch] is the one [DraftEdits] holds for the setting, which the view model
 * moves whenever anything but a keystroke here changes it, and the text typed under an epoch is shown for
 * exactly as long as that epoch is current. After that the field shows [shown], the setting as the draft
 * holds it, and the next keystroke continues from there. Nothing is rewritten while it is being typed, an
 * entry the setting cannot take included.
 */
@Composable
private fun DraftTextField(
    epoch: String,
    shown: String,
    type: (String) -> Boolean,
    enabled: Boolean,
    label: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE,
) {
    // Saved together with its epoch. A rotation keeps the view model and the epoch, so the text comes back
    // exactly as typed, separators included. After the process died both are new, so a restored text is not
    // shown over a draft it was never typed into.
    var typedEpoch by rememberSaveable { mutableStateOf<String?>(null) }
    var typedText by rememberSaveable { mutableStateOf("") }
    OutlinedTextField(if (typedEpoch == epoch) typedText else shown, { value ->
        // Only text the draft took becomes the field's own. A keystroke refused while a start is under way leaves
        // the field on what the draft holds, so it never shows a value that nothing is going to use.
        if (type(value)) {
            typedEpoch = epoch
            typedText = value
        }
    }, Modifier.fillMaxWidth(), enabled = enabled, label = label, isError = isError, keyboardOptions = keyboardOptions,
        minLines = minLines, maxLines = maxLines)
}

/**
 * The budget is the one setting that stops a job after it started, so it is visible wherever speech-to-text
 * is involved instead of hiding behind the advanced switch.
 *
 * Up to 0.3.0 a typed length limit stood beside it, defaulting to sixty minutes. It stopped ordinary videos
 * for a number the reader had never chosen, and the only correct answer to that warning was to raise it, so
 * since 0.4.0 the job takes its length from the source and the app's own ceiling is the only limit left.
 */
@Composable
private fun LimitFields(
    config: JobConfig,
    type: (TypedSetting, (JobConfig) -> JobConfig) -> Boolean,
    edits: DraftEdits,
    openHelp: (HelpTopic) -> Unit,
    enabled: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.limits), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
        InfoButton(HelpTopic.LIMITS, openHelp)
    }
    DraftTextField(edits.epoch(TypedSetting.BUDGET),
        config.maxCostMicrousd?.toBigDecimal()?.movePointLeft(6)?.stripTrailingZeros()?.toPlainString().orEmpty(), { typed ->
            type(TypedSetting.BUDGET) { it.copy(maxCostMicrousd = budgetValue(typed)) }
        }, enabled, isError = config.maxCostMicrousd?.let { it < 0 } == true,
        label = { Text(stringResource(R.string.cost_limit)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
}

private fun budgetValue(text: String): Long? = if (text.isBlank()) null else runCatching {
    text.replace(',', '.').toBigDecimal().takeIf { it.signum() >= 0 }?.movePointRight(6)?.longValueExact() ?: -1L
}.getOrDefault(-1L)
