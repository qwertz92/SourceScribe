package app.sourcescribe.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.sourcescribe.MainViewModel
import app.sourcescribe.R
import app.sourcescribe.ScreenState
import app.sourcescribe.SourcePreview
import app.sourcescribe.core.*
import java.util.Locale

@Composable
internal fun NewSourceScreen(
    input: String,
    setInput: (String) -> Unit,
    config: JobConfig,
    change: (JobConfig) -> Unit,
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
) {
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            OutlinedTextField(input, setInput, Modifier.fillMaxWidth(), enabled = !state.starting,
                label = { Text(stringResource(R.string.source_hint)) }, minLines = 2, maxLines = 5,
                trailingIcon = {
                    Row {
                        if (input.isNotEmpty()) IconButton({ setInput("") }, enabled = !state.starting) {
                            Icon(painterResource(R.drawable.ic_close), stringResource(R.string.clear_input), Modifier.size(20.dp))
                        }
                        IconButton({
                            val pasted = clipboardText(context)
                            if (pasted == null) onNotice("CLIPBOARD_EMPTY") else setInput(pasted)
                        }, enabled = !state.starting) {
                            Icon(painterResource(R.drawable.ic_paste), stringResource(R.string.paste_clipboard), Modifier.size(20.dp))
                        }
                    }
                })
            Text(stringResource(R.string.source_help), style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp))
        }
        if (settings.presets.isNotEmpty()) item {
            Choice(stringResource(R.string.preset), stringResource(R.string.choose), settings.presets.keys.toList(), { it },
                enabled = !state.starting, info = HelpTopic.PRESETS, openHelp = openHelp) { name ->
                onPreset(settings.presets.getValue(name))
            }
        }
        item {
            ConfigControls(config, change, state.credentials.map { Triple(it.id, it.provider, it.region) },
                state.previews.any { it.resolved.source.kind == SourceKind.LOCAL_AUDIO }, openHelp, enabled = !state.starting)
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(inspect, Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = input.isNotBlank() && !state.busy) {
                    Text(stringResource(R.string.inspect_source))
                }
                OutlinedButton(onImport, Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = !state.busy) {
                    Text(stringResource(R.string.import_audio))
                }
            }
        }
        if (state.previews.isNotEmpty()) {
            item { SectionTitle(R.string.preview, HelpTopic.WORKFLOW, openHelp) }
            items(state.previews, key = { it.resolved.source.id }) { preview ->
                PreviewCard(preview, state, change, onTrack, openHelp)
            }
            item {
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
    change: (JobConfig) -> Unit,
    onTrack: (String, String?, String?) -> Unit,
    openHelp: (HelpTopic) -> Unit,
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val source = preview.resolved.source
            Text(source.title ?: source.fileName ?: source.videoId.orEmpty(), style = MaterialTheme.typography.titleMedium,
                minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOf(
                source.channel ?: stringResource(R.string.unknown),
                source.durationMs?.let(::duration) ?: stringResource(R.string.unknown),
                source.publishedDate?.let(::publishedDate) ?: stringResource(R.string.unknown),
            ).joinToString(" · "))
            Text(source.canonicalUrl.orEmpty(), style = MaterialTheme.typography.bodySmall)
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
                    supporting = described.firstOrNull { it.track.id == preview.config.audioTrackId }?.let { audioTrackDetail(it) },
                    info = HelpTopic.AUDIO_TRACK,
                    openHelp = openHelp,
                ) { onTrack(source.id, null, it.track.id) }
            }

            if (AcquisitionPlanner.mayUseSpeechToText(preview.config.mode)) {
                val capability = MainViewModel.capabilities(preview.config)
                val price = capability?.priceMicrousdPerHour
                val durationMs = source.durationMs
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (price != null && durationMs != null) stringResource(R.string.estimated_cost,
                            String.format(Locale.ROOT, "%.4f", price * durationMs.toDouble() / 3_600_000 / 1_000_000),
                            capability.priceAsOf.orEmpty())
                        else stringResource(R.string.price_unknown),
                        Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                    )
                    InfoButton(HelpTopic.COST, openHelp)
                }
            }

            val error = MainViewModel.previewError(preview, state.credentials)
            if (error == "SOURCE_LONGER_THAN_LIMIT") {
                LengthLimitWarning(requireNotNull(source.durationMs), preview.config, change, openHelp, enabled = !state.starting)
            } else if (error != null) {
                Text(messageText(error), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** Names both numbers and offers the only correct remedy: raising the limit before the job exists. */
@Composable
private fun LengthLimitWarning(
    durationMs: Long,
    config: JobConfig,
    change: (JobConfig) -> Unit,
    openHelp: (HelpTopic) -> Unit,
    enabled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.source_longer_than_limit, duration(durationMs), limitDuration(config.maxAudioSeconds)),
            color = MaterialTheme.colorScheme.error)
        val suggestion = MainViewModel.suggestedLimitSeconds(durationMs)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (suggestion != null) FilledTonalButton({ change(config.copy(maxAudioSeconds = suggestion)) },
                Modifier.weight(1f), enabled = enabled) {
                Text(stringResource(R.string.raise_limit, limitDuration(suggestion)))
            } else {
                Text(stringResource(R.string.source_beyond_ceiling, duration(durationMs), limitDuration(JobLimits.MAX_AUDIO_SECONDS)),
                    Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            }
            InfoButton(HelpTopic.LIMITS, openHelp)
        }
    }
}

@Composable
private fun ConfigControls(
    config: JobConfig,
    change: (JobConfig) -> Unit,
    credentials: List<Triple<String, Provider, Region>>,
    localAudio: Boolean,
    openHelp: (HelpTopic) -> Unit,
    enabled: Boolean = true,
) {
    var advanced by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (localAudio) Text(stringResource(R.string.local_stt_mode))
        else Choice(stringResource(R.string.mode), stringResource(modeLabel(config.mode)), AcquisitionMode.entries,
            { modeName(it) }, enabled = enabled, info = HelpTopic.MODES, openHelp = openHelp) { change(config.copy(mode = it)) }
        if (AcquisitionPlanner.mayUseSpeechToText(config.mode)) {
            Choice(stringResource(R.string.provider), config.provider?.let(::providerName) ?: stringResource(R.string.no_provider),
                Provider.entries, { providerName(it) }, enabled = enabled, info = HelpTopic.PROVIDERS, openHelp = openHelp,
                placeholder = stringResource(R.string.no_provider)) { provider ->
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
            LimitFields(config, change, openHelp, enabled)
        }
        TextButton({ advanced = !advanced }, enabled = enabled) { Text(stringResource(R.string.advanced)) }
        if (advanced) {
            // These five only steer which caption track is read, so pure speech-to-text has no use for them.
            if (AcquisitionPlanner.usesCaptions(config.mode)) {
                Toggle(R.string.original_language, config.preferOriginalLanguage, enabled) { change(config.copy(preferOriginalLanguage = it)) }
                Toggle(R.string.uploader_captions, config.allowUploaderCaptions, enabled,
                    info = HelpTopic.CAPTION_TRACK, openHelp = openHelp) { change(config.copy(allowUploaderCaptions = it)) }
                Toggle(R.string.automatic_captions, config.allowAutomaticCaptions, enabled) { change(config.copy(allowAutomaticCaptions = it)) }
                Toggle(R.string.translated_captions, config.allowTranslatedCaptions, enabled) { change(config.copy(allowTranslatedCaptions = it)) }
                OutlinedTextField(config.preferredLanguages.joinToString(","),
                    { change(config.copy(preferredLanguages = it.split(',').map(String::trim).filter(String::isNotBlank))) },
                    Modifier.fillMaxWidth(), enabled = enabled, label = { Text(stringResource(R.string.languages)) })
            }
            if (AcquisitionPlanner.mayUseSpeechToText(config.mode)) {
                val cap = MainViewModel.capabilities(config)
                Toggle(R.string.fallback_errors, config.fallbackOnCaptionError, enabled) { change(config.copy(fallbackOnCaptionError = it)) }
                OutlinedTextField(config.language.orEmpty(), { change(config.copy(language = it.ifBlank { null })) },
                    Modifier.fillMaxWidth(), enabled = enabled, label = { Text(stringResource(R.string.stt_language)) })
                Toggle(R.string.diarization, config.diarization, enabled && cap?.diarization == true,
                    info = HelpTopic.DIARIZATION, openHelp = openHelp) { change(config.copy(diarization = it)) }
                Toggle(R.string.word_times, config.wordTimestamps, enabled && cap?.wordTimestamps == true,
                    info = HelpTopic.TIMESTAMPS, openHelp = openHelp) { change(config.copy(wordTimestamps = it)) }
                Toggle(R.string.segment_times, config.segmentTimestamps, enabled && cap?.segmentTimestamps == true) {
                    change(config.copy(segmentTimestamps = it))
                }
                if (cap?.contextTerms == true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.context_terms), Modifier.weight(1f),
                            style = MaterialTheme.typography.labelLarge)
                        InfoButton(HelpTopic.CONTEXT_TERMS, openHelp)
                    }
                    OutlinedTextField(config.contextTerms.joinToString("\n"),
                        { change(config.copy(contextTerms = it.lines().filter(String::isNotBlank))) },
                        Modifier.fillMaxWidth(), enabled = enabled, minLines = 2, maxLines = 4)
                }
            }
            Toggle(R.string.retain_raw, config.retainRaw, enabled, info = HelpTopic.RETENTION, openHelp = openHelp) {
                change(config.copy(retainRaw = it))
            }
            Toggle(R.string.unmetered, config.networkPolicy == NetworkPolicy.UNMETERED, enabled) {
                change(config.copy(networkPolicy = if (it) NetworkPolicy.UNMETERED else NetworkPolicy.ANY))
            }
            Choice(stringResource(R.string.audio_retention), audioRetentionName(config.audioRetention), AudioRetention.entries,
                { audioRetentionName(it) }, enabled = enabled, info = HelpTopic.RETENTION, openHelp = openHelp) {
                change(config.copy(audioRetention = it))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.export_formats), Modifier.weight(1f))
                InfoButton(HelpTopic.EXPORT_FORMATS, openHelp)
            }
            ExportFormat.entries.forEach { format ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .toggleable(value = format in config.exportFormats, enabled = enabled, role = Role.Checkbox) { checked ->
                        val selection = if (checked) config.exportFormats + format else config.exportFormats - format
                        if (selection.isNotEmpty()) change(config.copy(exportFormats = selection,
                            retainRaw = config.retainRaw || ExportFormat.RAW in selection))
                    }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(format in config.exportFormats, null, enabled = enabled)
                    Text(formatName(format))
                }
            }
        }
    }
}

/**
 * The length limit and the budget are the two settings that stop a job after it started, so they are
 * visible wherever speech-to-text is involved instead of hiding behind the advanced switch.
 */
@Composable
private fun LimitFields(config: JobConfig, change: (JobConfig) -> Unit, openHelp: (HelpTopic) -> Unit, enabled: Boolean) {
    var minutes by rememberSaveable { mutableStateOf((config.maxAudioSeconds / 60).toString()) }
    var budget by rememberSaveable {
        mutableStateOf(config.maxCostMicrousd?.toBigDecimal()?.movePointLeft(6)?.stripTrailingZeros()?.toPlainString().orEmpty())
    }
    // Both directions run through the same mapping. When they disagreed, an out-of-range entry was
    // rewritten to "0" while the reader was still typing it.
    LaunchedEffect(config.maxAudioSeconds) {
        if (config.maxAudioSeconds > 0 && limitSeconds(minutes) != config.maxAudioSeconds) {
            minutes = (config.maxAudioSeconds / 60).toString()
        }
    }
    LaunchedEffect(config.maxCostMicrousd) {
        if (budgetValue(budget) != config.maxCostMicrousd) {
            budget = config.maxCostMicrousd?.toBigDecimal()?.movePointLeft(6)?.stripTrailingZeros()?.toPlainString().orEmpty()
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.limits), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
        InfoButton(HelpTopic.LIMITS, openHelp)
    }
    OutlinedTextField(minutes,
        { text -> minutes = text; change(config.copy(maxAudioSeconds = limitSeconds(text) ?: 0)) },
        Modifier.fillMaxWidth(), enabled = enabled, isError = config.maxAudioSeconds !in 1..JobLimits.MAX_AUDIO_SECONDS,
        label = { Text(stringResource(R.string.duration_limit)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
    OutlinedTextField(budget, { text -> budget = text; change(config.copy(maxCostMicrousd = budgetValue(text))) },
        Modifier.fillMaxWidth(), enabled = enabled, isError = config.maxCostMicrousd?.let { it < 0 } == true,
        label = { Text(stringResource(R.string.cost_limit)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
}

/** Minutes as typed to seconds, or null when the entry is not a limit this app accepts. */
private fun limitSeconds(text: String): Long? =
    text.trim().toLongOrNull()?.takeIf { it in 1..JobLimits.MAX_AUDIO_MINUTES }?.times(60)

private fun budgetValue(text: String): Long? = if (text.isBlank()) null else runCatching {
    text.replace(',', '.').toBigDecimal().takeIf { it.signum() >= 0 }?.movePointRight(6)?.longValueExact() ?: -1L
}.getOrDefault(-1L)
