package app.sourcescribe

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.sourcescribe.core.*
import app.sourcescribe.data.ArtifactRow
import app.sourcescribe.data.AttemptRow
import app.sourcescribe.data.JobRow
import app.sourcescribe.data.SourceRow
import app.sourcescribe.data.ExportRow
import app.sourcescribe.data.decodeStoredJobConfig
import app.sourcescribe.extractor.EngineChannel
import dagger.hilt.android.AndroidEntryPoint
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val incoming = mutableStateOf("")
    private val shareSerial = mutableIntStateOf(0)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) acceptShare(intent)
        else { incoming.value = savedInstanceState.getString("pendingShare").orEmpty(); shareSerial.intValue = savedInstanceState.getInt("shareSerial") }
        setContent { SourceScribeApp(incoming.value, shareSerial.intValue) }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("pendingShare", incoming.value)
        outState.putInt("shareSerial", shareSerial.intValue)
        super.onSaveInstanceState(outState)
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); acceptShare(intent) }
    private fun acceptShare(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            incoming.value = intent.getStringExtra(Intent.EXTRA_TEXT)?.take(32769).orEmpty()
            shareSerial.intValue++
        }
    }
}

@Composable
private fun SourceScribeApp(incoming: String, shareSerial: Int, model: MainViewModel = viewModel()) {
    val settings by model.settings.collectAsStateWithLifecycle()
    val state by model.screen.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val jobs by model.jobs.collectAsStateWithLifecycle()
    val sources by model.sources.collectAsStateWithLifecycle()
    val attempts by model.attempts.collectAsStateWithLifecycle()
    val artifacts by model.artifacts.collectAsStateWithLifecycle()
    val exports by model.exports.collectAsStateWithLifecycle()
    val dark = settings.theme == "DARK" || settings.theme == "SYSTEM" && isSystemInDarkTheme()
    SideEffect {
        (context as? ComponentActivity)?.window?.let { window ->
            androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    val colors = if (dark) darkColorScheme(primary = Color(0xff77d6c5), secondary = Color(0xffb6c9c6))
        else lightColorScheme(primary = Color(0xff006b5c), secondary = Color(0xff41665e), surface = Color(0xfff8faf8))
    var page by rememberSaveable { mutableIntStateOf(0) }
    var input by rememberSaveable { mutableStateOf("") }
    val config = state.draft ?: settings.defaults
    val change: (JobConfig) -> Unit = model::updatePreviewConfig
    val pageState = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    val snackbar = remember { SnackbarHostState() }
    val message = state.message?.let { messageText(it) }
    LaunchedEffect(message) { if (message != null) { snackbar.showSnackbar(message); model.dismissMessage() } }
    val shareTitle = stringResource(R.string.share_file)
    LaunchedEffect(state.shareUri, shareTitle) {
        state.shareUri?.let { value ->
            val uri = value.toUri()
            val intent = Intent(Intent.ACTION_SEND).setType(state.shareMime).putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply { clipData = ClipData.newRawUri("SourceScribe", uri) }
            context.startActivity(Intent.createChooser(intent, shareTitle))
            model.shareConsumed()
        }
    }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) model.importAudio(uri, config) }
    fun notificationsAllowed() = android.os.Build.VERSION.SDK_INT < 33 ||
        androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
    var notificationsGranted by remember { mutableStateOf(notificationsAllowed()) }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        notificationsGranted = notificationsAllowed()
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsGranted = granted
    }
    var consumedShare by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(incoming, shareSerial) {
        if (incoming.isNotBlank() && shareSerial != consumedShare) { input = incoming; page = 0; model.clearPreview(); consumedShare = shareSerial }
    }
    MaterialTheme(colorScheme = colors) {
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
            NavigationBar {
                listOf(R.string.new_job, R.string.history, R.string.settings).forEachIndexed { index, label ->
                    NavigationBarItem(selected = page == index, onClick = { page = index },
                        icon = { Text((index + 1).toString().padStart(2, '0'), fontWeight = FontWeight.Bold) },
                        label = { Text(stringResource(label)) })
                }
            }
        }) { insets ->
            Column(Modifier.fillMaxSize().padding(insets)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp).semantics { heading() })
                Box(Modifier.fillMaxWidth().height(4.dp)) { if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth()) }
                pageState.SaveableStateProvider(page) { when (page) {
                    0 -> NewSource(input, { input = it; model.clearPreview() }, config, change, state, settings,
                        inspect = { model.inspect(input, config) }, onPreset = { change(it) }, onTrack = model::selectTrack,
                        onStart = {
                            if (android.os.Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(context,
                                    android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            }
                            model.startPreviews(); page = 1
                        }, onCancelPreview = model::clearPreview,
                        onImport = { audioPicker.launch(arrayOf("audio/*", "video/mp4", "video/webm")) })
                    1 -> History(jobs, sources, attempts, artifacts, exports, model, !notificationsGranted) { id ->
                        model.prepareAgain(id, config); page = 0
                    }
                    else -> SettingsScreen(settings, config, state, jobs, change, model)
                } }
            }
        }
        state.information?.let { text ->
            androidx.compose.ui.window.Dialog(model::closeInformation) {
                Surface(shape = MaterialTheme.shapes.large) {
                    Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(20.dp)) {
                        Text(stringResource(if (state.informationShareable) R.string.diagnostics else R.string.licenses), style = MaterialTheme.typography.titleLarge)
                        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(text.lines()) { line -> Text(line, style = MaterialTheme.typography.bodySmall) }
                        }
                        if (state.informationShareable) Button(model::shareDiagnostics, Modifier.fillMaxWidth()) { Text(stringResource(R.string.share_file)) }
                        TextButton(model::closeInformation, Modifier.fillMaxWidth()) { Text(stringResource(R.string.back)) }
                    }
                }
            }
        }
        state.document?.let { document -> TranscriptDialog(document, model::closeArtifact,
            { model.shareArtifact(document.artifactId) }, { format, tree -> model.exportArtifact(document.artifactId, format, tree) }) }
    }
}

@Composable
private fun NewSource(input: String, setInput: (String) -> Unit, config: JobConfig, change: (JobConfig) -> Unit,
    state: ScreenState, settings: AppSettings, inspect: () -> Unit, onPreset: (JobConfig) -> Unit,
    onTrack: (String, String?, String?) -> Unit, onStart: () -> Unit, onCancelPreview: () -> Unit, onImport: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            OutlinedTextField(input, setInput, Modifier.fillMaxWidth(), enabled = !state.starting, label = { Text(stringResource(R.string.source_hint)) }, minLines = 2, maxLines = 5)
            Text(stringResource(R.string.source_help), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        }
        if (settings.presets.isNotEmpty()) item {
            Choice(stringResource(R.string.preset), stringResource(R.string.choose), settings.presets.keys.toList(), { it }, enabled = !state.starting) { name -> onPreset(settings.presets.getValue(name)) }
        }
        item { ConfigControls(config, change, state.credentials.map { Triple(it.id, it.provider, it.region) }, state.previews.any { it.resolved.source.kind == SourceKind.LOCAL_AUDIO }, enabled = !state.starting) }
        item {
            Button(inspect, Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = input.isNotBlank() && !state.busy) { Text(stringResource(R.string.inspect_source)) }
            OutlinedButton(onImport, Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = !state.busy) { Text(stringResource(R.string.import_audio)) }
        }
        if (state.previews.isNotEmpty()) {
            item { SectionTitle(R.string.preview) }
            items(state.previews, key = { it.resolved.source.id }) { preview ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val source = preview.resolved.source
                        Text(source.title ?: source.fileName ?: source.videoId.orEmpty(), style = MaterialTheme.typography.titleMedium,
                            minLines = 2, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text(listOf(source.channel ?: stringResource(R.string.unknown), source.durationMs?.let(::duration) ?: stringResource(R.string.unknown), source.publishedDate ?: stringResource(R.string.unknown)).joinToString(" · "))
                        Text(source.canonicalUrl.orEmpty(), style = MaterialTheme.typography.bodySmall)
                        if (source.kind == SourceKind.YOUTUBE) Text(stringResource(R.string.whole_video), style = MaterialTheme.typography.labelLarge)
                        if (preview.previousJob != null) Text(stringResource(R.string.duplicate_warning), color = MaterialTheme.colorScheme.error)
                        if (preview.config.mode != AcquisitionMode.STT_ONLY) {
                            val choices = TrackSelection.captions(preview.resolved, preview.config.copy(captionTrackId = null))
                            if (choices.isEmpty()) Text(stringResource(R.string.no_captions)) else Choice(stringResource(R.string.caption_track),
                                choices.firstOrNull { it.id == preview.config.captionTrackId }?.let { "${it.language} · ${it.name ?: generationName(it.generation)}" }.orEmpty(), choices,
                                { "${it.language} · ${it.name ?: generationName(it.generation)} · ${translationName(it.translation)}" }, enabled = !state.starting) { onTrack(source.id, it.id, null) }
                        }
                        if (preview.config.mode != AcquisitionMode.CAPTIONS_ONLY && source.kind == SourceKind.YOUTUBE) {
                            val choices = preview.resolved.audio
                            if (choices.isEmpty()) Text(stringResource(R.string.no_audio)) else Choice(stringResource(R.string.audio_track),
                                choices.firstOrNull { it.id == preview.config.audioTrackId }?.let { "${it.language ?: stringResource(R.string.unknown)} · ${it.name ?: it.id}" } ?: stringResource(R.string.choose),
                                choices, { "${it.language ?: stringResource(R.string.unknown)} · ${it.name ?: it.id} (${it.id})" }, enabled = !state.starting) { onTrack(source.id, null, it.id) }
                            Text(stringResource(R.string.choose_audio_help), style = MaterialTheme.typography.bodySmall)
                        }
                        if (preview.config.mode != AcquisitionMode.CAPTIONS_ONLY) {
                            val capability = MainViewModel.capabilities(preview.config)
                            val price = capability?.priceMicrousdPerHour
                            val durationMs = source.durationMs
                            if (price != null && durationMs != null) Text(stringResource(R.string.estimated_cost,
                                String.format(Locale.ROOT, "%.4f", price * durationMs.toDouble() / 3_600_000 / 1_000_000), capability.priceAsOf.orEmpty()))
                            else Text(stringResource(R.string.price_unknown))
                        }
                        MainViewModel.previewError(preview, state.credentials)?.let { Text(messageText(it), color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
            item {
                Button(onStart, Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = !state.busy && state.previews.all {
                    MainViewModel.previewError(it, state.credentials) == null
                }) { Text(stringResource(R.string.start_jobs)) }
                TextButton(onCancelPreview, Modifier.fillMaxWidth(), enabled = !state.starting) { Text(stringResource(R.string.cancel)) }
            }
        }
    }
}

@Composable
private fun ConfigControls(config: JobConfig, change: (JobConfig) -> Unit, credentials: List<Triple<String, Provider, Region>>, localAudio: Boolean, enabled: Boolean = true) {
    var advanced by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (localAudio) Text(stringResource(R.string.local_stt_mode))
        else Choice(stringResource(R.string.mode), stringResource(modeLabel(config.mode)), AcquisitionMode.entries, { modeName(it) }, enabled = enabled) { change(config.copy(mode = it)) }
        if (config.mode != AcquisitionMode.CAPTIONS_ONLY) {
            Choice(stringResource(R.string.provider), config.provider?.let(::providerName) ?: stringResource(R.string.no_provider), Provider.entries, { providerName(it) }, enabled = enabled) { provider ->
                val key = credentials.firstOrNull { it.second == provider }
                change(MainViewModel.modelDefaults(config.copy(provider = provider, credentialId = key?.first,
                    region = key?.third ?: Region.US), MainViewModel.models(provider).first()))
            }
            config.provider?.let { provider ->
                Choice(stringResource(R.string.model), config.model.orEmpty(), MainViewModel.models(provider), { it }, enabled = enabled) { change(MainViewModel.modelDefaults(config, it)) }
                val keys = credentials.filter { it.second == provider }
                if (keys.isNotEmpty()) Choice(stringResource(R.string.credentials), keys.firstOrNull { it.first == config.credentialId }?.let { "${regionName(it.third)} · ••••${it.first.takeLast(4)}" } ?: stringResource(R.string.choose),
                    keys, { "${regionName(it.third)} · ••••${it.first.takeLast(4)}" }, enabled = enabled) { change(config.copy(credentialId = it.first, region = it.third, uploadApproved = false)) }
            }
            if (config.credentialId == null) Text(stringResource(R.string.no_provider_help), style = MaterialTheme.typography.bodySmall)
            Toggle(R.string.upload_approval, config.uploadApproved && credentials.any { it.first == config.credentialId }, enabled = enabled && credentials.any { it.first == config.credentialId && it.second == config.provider && it.third == config.region }) { change(config.copy(uploadApproved = it)) }
            Text(stringResource(R.string.upload_help), style = MaterialTheme.typography.bodySmall)
        }
        TextButton({ advanced = !advanced }, enabled = enabled) { Text(stringResource(R.string.advanced)) }
        if (advanced) {
            Toggle(R.string.original_language, config.preferOriginalLanguage, enabled) { change(config.copy(preferOriginalLanguage = it)) }
            Toggle(R.string.uploader_captions, config.allowUploaderCaptions, enabled) { change(config.copy(allowUploaderCaptions = it)) }
            Toggle(R.string.automatic_captions, config.allowAutomaticCaptions, enabled) { change(config.copy(allowAutomaticCaptions = it)) }
            Toggle(R.string.translated_captions, config.allowTranslatedCaptions, enabled) { change(config.copy(allowTranslatedCaptions = it)) }
            OutlinedTextField(config.preferredLanguages.joinToString(","), { change(config.copy(preferredLanguages = it.split(',').map(String::trim).filter(String::isNotBlank))) },
                Modifier.fillMaxWidth(), enabled = enabled, label = { Text(stringResource(R.string.languages)) })
            if (config.mode != AcquisitionMode.CAPTIONS_ONLY) {
                val cap = MainViewModel.capabilities(config)
                Toggle(R.string.fallback_errors, config.fallbackOnCaptionError, enabled) { change(config.copy(fallbackOnCaptionError = it)) }
                OutlinedTextField(config.language.orEmpty(), { change(config.copy(language = it.ifBlank { null })) }, Modifier.fillMaxWidth(), enabled = enabled, label = { Text(stringResource(R.string.stt_language)) })
                Toggle(R.string.diarization, config.diarization, enabled && cap?.diarization == true) { change(config.copy(diarization = it)) }
                Toggle(R.string.word_times, config.wordTimestamps, enabled && cap?.wordTimestamps == true) { change(config.copy(wordTimestamps = it)) }
                Toggle(R.string.segment_times, config.segmentTimestamps, enabled && cap?.segmentTimestamps == true) { change(config.copy(segmentTimestamps = it)) }
                if (cap?.contextTerms == true) OutlinedTextField(config.contextTerms.joinToString("\n"), { change(config.copy(contextTerms = it.lines().filter(String::isNotBlank))) },
                    Modifier.fillMaxWidth(), enabled = enabled, label = { Text(stringResource(R.string.context_terms)) }, minLines = 2, maxLines = 4)
                LimitFields(config, change, enabled)
            }
            Toggle(R.string.retain_raw, config.retainRaw, enabled) { change(config.copy(retainRaw = it)) }
            Toggle(R.string.unmetered, config.networkPolicy == NetworkPolicy.UNMETERED, enabled) { change(config.copy(networkPolicy = if (it) NetworkPolicy.UNMETERED else NetworkPolicy.ANY)) }
            Choice(stringResource(R.string.audio_retention), audioRetentionName(config.audioRetention), AudioRetention.entries, { audioRetentionName(it) }, enabled = enabled) { change(config.copy(audioRetention = it)) }
            Text(stringResource(R.string.export_formats))
            ExportFormat.entries.forEach { format ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = format in config.exportFormats, enabled = enabled, role = Role.Checkbox) { checked ->
                    val selection = if (checked) config.exportFormats + format else config.exportFormats - format
                    if (selection.isNotEmpty()) change(config.copy(exportFormats = selection, retainRaw = config.retainRaw || ExportFormat.RAW in selection))
                }) {
                    Checkbox(format in config.exportFormats, null, enabled = enabled)
                    Text(formatName(format), Modifier.padding(top = 12.dp))
                }
            }
        }
    }
}

@Composable
private fun LimitFields(config: JobConfig, change: (JobConfig) -> Unit, enabled: Boolean) {
    var minutes by rememberSaveable { mutableStateOf((config.maxAudioSeconds / 60).toString()) }
    var budget by rememberSaveable { mutableStateOf(config.maxCostMicrousd?.toBigDecimal()?.movePointLeft(6)?.stripTrailingZeros()?.toPlainString().orEmpty()) }
    LaunchedEffect(config.maxAudioSeconds) {
        if (config.maxAudioSeconds > 0 && minutes.toLongOrNull()?.times(60) != config.maxAudioSeconds) minutes = (config.maxAudioSeconds / 60).toString()
    }
    LaunchedEffect(config.maxCostMicrousd) {
        if (budgetValue(budget) != config.maxCostMicrousd) budget = config.maxCostMicrousd?.toBigDecimal()?.movePointLeft(6)?.stripTrailingZeros()?.toPlainString().orEmpty()
    }
    OutlinedTextField(minutes, { text -> minutes = text; change(config.copy(maxAudioSeconds = text.toLongOrNull()?.takeIf { it in 1..600 }?.times(60) ?: 0)) },
        Modifier.fillMaxWidth(), enabled = enabled, isError = config.maxAudioSeconds !in 1..36_000, label = { Text(stringResource(R.string.duration_limit)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
    OutlinedTextField(budget, { text -> budget = text; change(config.copy(maxCostMicrousd = budgetValue(text))) },
        Modifier.fillMaxWidth(), enabled = enabled, isError = config.maxCostMicrousd?.let { it < 0 } == true,
        label = { Text(stringResource(R.string.cost_limit)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
}

private fun budgetValue(text: String): Long? = if (text.isBlank()) null else runCatching {
    text.replace(',', '.').toBigDecimal().takeIf { it.signum() >= 0 }?.movePointRight(6)?.longValueExact() ?: -1L
}.getOrDefault(-1L)

@Composable
private fun History(jobs: List<JobRow>, sources: List<SourceRow>, attempts: List<AttemptRow>, artifacts: List<ArtifactRow>, exports: List<ExportRow>, model: MainViewModel, notificationsDisabled: Boolean, otherProvider: (String) -> Unit) {
    var confirmation by remember { mutableStateOf<Pair<String, Int>?>(null) }
    val context = LocalContext.current
    var retryExportId by rememberSaveable { mutableStateOf<String?>(null) }
    val exportFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val id = retryExportId
        retryExportId = null
        if (uri != null && id != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                model.retryExport(id, uri.toString())
            } catch (_: SecurityException) { model.exportPermissionError() }
        }
    }
    var query by rememberSaveable { mutableStateOf("") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(jobs.any { it.state == ExecutionState.WAITING_REMOTE }) {
        while (jobs.any { it.state == ExecutionState.WAITING_REMOTE }) { delay(1000); now = System.currentTimeMillis() }
    }
    val sourceNames = remember(sources) { sources.associate { it.id to it.title } }
    val visible = jobs.filter { "${sourceNames[it.sourceId]} ${it.sourceId} ${it.createdAt} ${it.config}".contains(query, ignoreCase = true) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.search_history)) }, singleLine = true) }
        if (notificationsDisabled && jobs.isNotEmpty()) item { Text(stringResource(R.string.notifications_denied), style = MaterialTheme.typography.bodySmall) }
        if (exports.isNotEmpty()) item { TextButton({ model.reconcileExports() }) { Text(stringResource(R.string.check_exports)) } }
        if (jobs.isNotEmpty() && visible.isEmpty()) item { Text(stringResource(R.string.no_matches)) }
        if (jobs.isEmpty()) item {
            Text(stringResource(R.string.no_jobs), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.no_jobs_help), Modifier.padding(top = 12.dp))
        }
        items(visible, key = { it.id }) { job ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(sourceNames[job.sourceId] ?: job.sourceId, style = MaterialTheme.typography.titleMedium,
                        minLines = 2, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(job.createdAt)), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(stateLabel(job.state)), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(outcomeLabel(job.outcome)))
                    val savedConfig = remember(job.config) { decodeStoredJobConfig(job.config) }
                    if (savedConfig == null) Text(stringResource(R.string.job_config_invalid), color = MaterialTheme.colorScheme.error)
                    else Text(listOfNotNull(savedConfig.provider?.let(::providerName), savedConfig.model).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                    if (job.state == ExecutionState.WAITING_REMOTE) Text(stringResource(R.string.provider_elapsed, duration((now - job.createdAt).coerceAtLeast(0))), style = MaterialTheme.typography.bodySmall)
                    attempts.filter { it.jobId == job.id }.forEach { attempt ->
                        Text("${if (attempt.branch == Branch.CAPTIONS) stringResource(R.string.mode_captions_only) else stringResource(R.string.mode_stt_only)} · ${stringResource(phaseLabel(attempt.phase))}", style = MaterialTheme.typography.bodySmall)
                        if (attempt.processedBytes > 0) Text(stringResource(R.string.processed_bytes, attempt.processedBytes), style = MaterialTheme.typography.bodySmall)
                        attempt.error?.let { Text(messageText(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    }
                    artifacts.filter { it.jobId == job.id }.forEach { artifact ->
                        FilledTonalButton({ model.openArtifact(artifact.id) }, Modifier.fillMaxWidth()) {
                            Text("${stringResource(R.string.open_result)} · ${artifact.language ?: stringResource(R.string.unknown)} · ${branchName(artifact.branch)}")
                        }
                    }
                    val artifactIds = artifacts.filter { it.jobId == job.id }.map { it.id }.toSet()
                    exports.filter { it.artifactId in artifactIds }.forEach { row ->
                        Text("${formatName(ExportFormat.valueOf(row.format))} · ${messageText("EXPORT_${row.state.name}")}", style = MaterialTheme.typography.bodySmall)
                        row.error?.let { Text(messageText(it), style = MaterialTheme.typography.bodySmall) }
                        if (!job.deleteRequested && row.state in setOf(ExportState.FAILED, ExportState.PERMISSION_REQUIRED)) {
                            TextButton({ retryExportId = row.id; exportFolder.launch(null) }) { Text(stringResource(R.string.retry_export_folder)) }
                        }
                    }
                    if (job.deleteRequested) Text(stringResource(R.string.delete_pending)) else {
                        if (job.state !in setOf(ExecutionState.FINISHED, ExecutionState.CANCELLED)) TextButton({ model.cancel(job.id) }) { Text(stringResource(R.string.cancel_job)) }
                        if (savedConfig != null && job.state == ExecutionState.WAITING_USER && !job.cancelRequested) TextButton({ model.resume(job.id) }) { Text(stringResource(R.string.resume_job)) }
                        if (savedConfig != null && job.state != ExecutionState.RUNNING) {
                            TextButton({ confirmation = job.id to R.string.retry_missing }) { Text(stringResource(R.string.retry_missing)) }
                            TextButton({ confirmation = job.id to R.string.retry_all }) { Text(stringResource(R.string.retry_all)) }
                            TextButton({ otherProvider(job.id) }) { Text(stringResource(R.string.other_provider)) }
                        }
                        if (job.state in setOf(ExecutionState.FINISHED, ExecutionState.CANCELLED) &&
                            savedConfig?.provider == Provider.ASSEMBLYAI) {
                            TextButton({ confirmation = job.id to R.string.delete_remote }) { Text(stringResource(R.string.delete_remote)) }
                        }
                        TextButton({ confirmation = job.id to R.string.delete_job }) { Text(stringResource(R.string.delete_job)) }
                    }
                }
            }
        }
    }
    confirmation?.let { (id, action) ->
        ConfirmationDialog(stringResource(action), stringResource(when (action) { R.string.delete_job -> R.string.delete_job_help; R.string.delete_remote -> R.string.delete_remote_help; else -> R.string.retry_job_help }),
            { confirmation = null }) {
            when (action) {
                R.string.delete_job -> model.deleteJob(id)
                R.string.delete_remote -> model.deleteRemote(id)
                R.string.retry_missing -> model.retry(id, true)
                else -> model.retry(id, false)
            }
            confirmation = null
        }
    }
}

@Composable
private fun ConfirmationDialog(title: String, explanation: String, close: () -> Unit, confirm: () -> Unit) {
    androidx.compose.ui.window.Dialog(close) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(20.dp)) {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item { Text(title, style = MaterialTheme.typography.titleLarge) }
                    item { Text(explanation) }
                    item { Button(confirm, Modifier.fillMaxWidth()) { Text(title) } }
                }
                TextButton(close, Modifier.fillMaxWidth()) { Text(stringResource(R.string.cancel)) }
            }
        }
    }
}

@Composable
private fun SettingsScreen(settings: AppSettings, config: JobConfig, state: ScreenState, jobs: List<JobRow>, change: (JobConfig) -> Unit, model: MainViewModel) {
    var provider by rememberSaveable { mutableStateOf(Provider.ASSEMBLYAI) }
    var region by rememberSaveable { mutableStateOf(Region.US) }
    var key by remember { mutableStateOf("") }
    var replacingId by rememberSaveable { mutableStateOf<String?>(null) }
    var restoreJob by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(restoreJob, state.credentials) {
        if (restoreJob != null && state.credentials.any { it.id == replacingId }) restoreJob = null
    }
    val missingKeys = remember(jobs, state.credentials) { jobs.filterNot { it.deleteRequested }.mapNotNull { job ->
        val saved = decodeStoredJobConfig(job.config) ?: return@mapNotNull null
        val id = saved.credentialId
        val selectedProvider = saved.provider
        if (id == null || selectedProvider == null || state.credentials.any { it.id == id }) null
        else job.id to app.sourcescribe.data.CredentialInfo(id, selectedProvider, saved.region)
    }.distinctBy { it.second.id } }
    var presetName by rememberSaveable { mutableStateOf("") }
    var probe by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                change(config.copy(exportTreeUri = uri.toString()))
                model.changeSettings { it.copy(defaults = it.defaults.copy(exportTreeUri = uri.toString())) }
            } catch (_: SecurityException) { model.exportPermissionError() }
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text(stringResource(R.string.privacy)); SectionTitle(R.string.credentials) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Choice(stringResource(R.string.provider), providerName(provider), Provider.entries, { providerName(it) }, enabled = !state.busy && replacingId == null) { provider = it; key = "" }
                if (provider == Provider.ASSEMBLYAI) Choice(stringResource(R.string.region), regionName(region), Region.entries, { regionName(it) }, enabled = !state.busy && replacingId == null) { region = it; key = "" }
                OutlinedTextField(key, { key = it.take(4096) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.api_key)) },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                if (replacingId != null) {
                    Text(stringResource(if (restoreJob == null) R.string.replace_key_help else R.string.restore_key_help, providerName(provider), regionName(region)))
                    TextButton({ replacingId = null; restoreJob = null; key = "" }, enabled = !state.busy) { Text(stringResource(R.string.cancel)) }
                }
                Button({
                    val replacing = replacingId
                    if (restoreJob != null) model.restoreCredential(requireNotNull(restoreJob), key)
                    else if (replacing == null) model.saveCredential(provider, if (provider == Provider.ASSEMBLYAI) region else Region.US, key)
                    else model.replaceCredential(replacing, provider, region, key)
                    key = ""
                }, enabled = key.isNotBlank() && !state.busy) { Text(stringResource(R.string.save_key)) }
            }
        }
        items(state.credentials, key = { it.id }) { credential ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${providerName(credential.provider)} · ${regionName(credential.region)} · ••••${credential.id.takeLast(4)}", Modifier.weight(1f).padding(top = 12.dp))
                Column {
                    TextButton({ replacingId = credential.id; restoreJob = null; provider = credential.provider; region = credential.region; key = "" }, enabled = !state.busy) { Text(stringResource(R.string.replace_key)) }
                    TextButton({ model.deleteCredential(credential.id) }, enabled = !state.busy) { Text(stringResource(R.string.delete_key)) }
                }
            }
        }
        items(missingKeys, key = { "missing-${it.second.id}" }) { (jobId, credential) ->
            TextButton({ replacingId = credential.id; restoreJob = jobId; provider = credential.provider; region = credential.region; key = "" }, enabled = !state.busy) {
                Text(stringResource(R.string.restore_key_action, providerName(credential.provider), regionName(credential.region), credential.id.takeLast(4)))
            }
        }
        item {
            SectionTitle(R.string.defaults)
            Button({ model.saveDefaults(config) }) { Text(stringResource(R.string.save_defaults)) }
            OutlinedTextField(presetName, { presetName = it.take(80) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.preset_name)) })
            TextButton({ model.savePreset(presetName, config); presetName = "" }, enabled = presetName.isNotBlank()) { Text(stringResource(R.string.save_preset)) }
            TextButton({ folder.launch(null) }) { Text(stringResource(R.string.export_tree)) }
            Text(if (config.exportTreeUri == null) stringResource(R.string.no_export_tree) else stringResource(R.string.export_saved), style = MaterialTheme.typography.bodySmall)
        }
        item {
            SectionTitle(R.string.appearance)
            Choice(stringResource(R.string.appearance), themeName(settings.theme), listOf("SYSTEM", "LIGHT", "DARK"), { themeName(it) }) { value -> model.changeSettings { it.copy(theme = value) } }
            Choice(stringResource(R.string.storage_limit), "${settings.storageLimitBytes / 1024 / 1024} MiB", listOf(256L, 512L, 1024L, 2048L, 4096L, 8192L, 16384L, 32768L), { "$it MiB" }) { limit -> model.changeSettings { it.copy(storageLimitBytes = limit * 1024 * 1024) } }
            Choice(stringResource(R.string.parallel_jobs), settings.parallelJobs.toString(), (1..4).toList(), { it.toString() }) { count -> model.changeSettings { it.copy(parallelJobs = count) } }
        }
        item {
            SectionTitle(R.string.diagnostics)
            Text(stringResource(R.string.diagnostics_help))
            TextButton(model::showDiagnostics, enabled = !state.busy) { Text(stringResource(R.string.diagnostics_preview)) }
            TextButton(model::showLicenses, enabled = !state.busy) { Text(stringResource(R.string.licenses)) }
        }
        item {
            SectionTitle(R.string.engines)
            Text(stringResource(R.string.engine_help))
            state.installations.forEach { Text("yt-dlp ${it.version} · EJS ${it.ejsVersion} · ${channelName(it.channel)}", Modifier.padding(vertical = 8.dp)) }
            Row { TextButton({ model.checkEngine(EngineChannel.STABLE) }, enabled = !state.busy) { Text(channelName(EngineChannel.STABLE)) }; TextButton({ model.checkEngine(EngineChannel.NIGHTLY) }, enabled = !state.busy) { Text(channelName(EngineChannel.NIGHTLY)) } }
            state.update?.let { update ->
                Text("yt-dlp ${update.version} · ${channelName(update.channel)}")
                OutlinedTextField(probe, { probe = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.engine_probe)) })
                Button({ model.stageAndActivate(probe) }, enabled = probe.isNotBlank() && !state.busy) { Text(stringResource(R.string.engine_activate)) }
            }
            TextButton(model::rollback, enabled = !state.busy) { Text(stringResource(R.string.engine_rollback)) }
        }
    }
}

@Composable
private fun TranscriptDialog(document: TranscriptDocument, close: () -> Unit, share: () -> Unit, export: (ExportFormat, String) -> Unit) {
    val context = LocalContext.current
    var query by rememberSaveable(document.artifactId) { mutableStateOf("") }
    var format by rememberSaveable(document.artifactId) { mutableStateOf(ExportFormat.MARKDOWN) }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            catch (_: SecurityException) { /* The current grant may still permit this explicit export. */ }
            export(format, uri.toString())
        }
    }
    val segments by produceState(document.segments, document.artifactId, query) {
        value = withContext(Dispatchers.Default) { document.segments.filter { it.text.contains(query, ignoreCase = true) } }
    }
    val copyText by produceState<String?>(null, document.artifactId) { value = withContext(Dispatchers.Default) { document.text } }
    val copyRanges = remember(copyText) { copyText?.let(MainViewModel::clipboardRanges).orEmpty() }
    var chooseCopyPart by rememberSaveable(document.artifactId) { mutableStateOf(false) }
    var showActions by rememberSaveable(document.artifactId) { mutableStateOf(false) }
    val copyPart: (IntRange) -> Unit = { range ->
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(
            ClipData.newPlainText("SourceScribe", requireNotNull(copyText).substring(range)))
        chooseCopyPart = false
    }
    var showProvenance by rememberSaveable(document.artifactId) { mutableStateOf(false) }
    androidx.compose.ui.window.Dialog(onDismissRequest = close,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.92f), shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
                    item { Text(document.source.title ?: stringResource(R.string.result), style = MaterialTheme.typography.titleLarge, maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
                    item { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.search_transcript)) }, singleLine = true) }
                    item {
                        Text("${originName(document.provenance)} · ${document.language ?: stringResource(R.string.unknown)}", style = MaterialTheme.typography.bodySmall)
                        if (document.scope.technicallyComplete != true) Text(stringResource(R.string.technically_partial), color = MaterialTheme.colorScheme.error)
                        if (document.warnings.isNotEmpty()) Text(document.warnings.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                        TextButton({ showProvenance = !showProvenance }) { Text(stringResource(R.string.provenance_details)) }
                        if (showProvenance) {
                            Text("${stringResource(R.string.source)}: ${document.source.canonicalUrl ?: document.source.fileName ?: document.source.id}")
                            Text("ID: ${document.source.id}")
                            Text(stringResource(R.string.model_requested, document.provenance.requestedModel ?: stringResource(R.string.unknown)))
                            Text(stringResource(R.string.model_reported, document.provenance.reportedModel ?: stringResource(R.string.unknown)))
                            Text(stringResource(R.string.original_language_value, document.source.originalLanguage ?: stringResource(R.string.unknown)))
                            Text(stringResource(R.string.translation_value, translationName(document.provenance.translation)))
                            document.provenance.sourceAudioTrack?.let { Text(stringResource(R.string.audio_track_value, "${it.id} · ${it.language ?: stringResource(R.string.unknown)}")) }
                            document.provenance.captionTrack?.let { Text(stringResource(R.string.caption_track_value, "${it.id} · ${it.language}")) }
                        }
                        if (copyRanges.size > 1) Text(stringResource(R.string.copy_large_help), style = MaterialTheme.typography.bodySmall)
                    }
                    if (query.isNotBlank() && segments.isEmpty()) item { Text(stringResource(R.string.no_matches)) }
                    items(segments.size) { index ->
                        val segment = segments[index]
                        Column {
                            val evidence = listOfNotNull(segment.startMs?.let(::duration), segment.speaker)
                            if (evidence.isNotEmpty()) Text(evidence.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text(segment.text)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton({ showActions = true }) { Text(stringResource(R.string.result_actions)) }
                    TextButton(close) { Text(stringResource(R.string.back)) }
                }
            }
        }
    }
    if (showActions) androidx.compose.ui.window.Dialog({ showActions = false }) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().fillMaxHeight(0.8f).padding(16.dp)) {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { Text(stringResource(R.string.result_actions), style = MaterialTheme.typography.titleLarge) }
                    item {
                        TextButton({ showActions = false; if (copyRanges.size == 1) copyPart(copyRanges.single()) else chooseCopyPart = true },
                            Modifier.fillMaxWidth(), enabled = copyRanges.isNotEmpty()) { Text(stringResource(R.string.copy)) }
                    }
                    item { TextButton({ showActions = false; share() }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.share_file)) } }
                    item {
                        Choice(stringResource(R.string.export_formats), formatName(format), ExportFormat.entries.filter {
                            if (it == ExportFormat.RAW) document.acquisition.retainRaw else TranscriptExporter.supports(document, it)
                        }, { formatName(it) }) { format = it }
                    }
                    item { TextButton({ showActions = false; folder.launch(null) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.export)) } }
                }
                TextButton({ showActions = false }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.back)) }
            }
        }
    }
    if (chooseCopyPart) androidx.compose.ui.window.Dialog({ chooseCopyPart = false }) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().fillMaxHeight(0.65f).padding(20.dp)) {
                LazyColumn(Modifier.weight(1f)) {
                    item { Text(stringResource(R.string.copy), style = MaterialTheme.typography.titleLarge) }
                    item { Text(stringResource(R.string.copy_large_help)) }
                    items(copyRanges.size) { index ->
                        TextButton({ copyPart(copyRanges[index]) }, Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.copy_part, index + 1, copyRanges.size))
                        }
                    }
                }
                TextButton({ chooseCopyPart = false }) { Text(stringResource(R.string.back)) }
            }
        }
    }
}

@Composable
private fun <T> Choice(label: String, selected: String, options: List<T>, name: @Composable (T) -> String, enabled: Boolean = true, choose: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    OutlinedButton({ expanded = true }, Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = MaterialTheme.shapes.small, enabled = enabled) {
        Column(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(selected, style = MaterialTheme.typography.bodyLarge, minLines = 2, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
    if (expanded && enabled) androidx.compose.ui.window.Dialog({ expanded = false }) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().fillMaxHeight(0.65f).padding(16.dp)) {
                LazyColumn(Modifier.weight(1f)) {
                    item { Text(label, style = MaterialTheme.typography.titleLarge) }
                    items(options.size) { index ->
                    TextButton({ choose(options[index]); expanded = false }, Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(name(options[index]), Modifier.fillMaxWidth()) }
                } }
                TextButton({ expanded = false }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.cancel)) }
            }
        }
    }
}

@Composable private fun Toggle(label: Int, checked: Boolean, enabled: Boolean = true, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = change), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(label), Modifier.weight(1f).padding(vertical = 12.dp))
        Switch(checked, null, enabled = enabled)
    }
}
@Composable private fun SectionTitle(label: Int) { Text(stringResource(label), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 12.dp).semantics { heading() }) }
private fun providerName(value: Provider): String = when (value) { Provider.ASSEMBLYAI -> "AssemblyAI"; Provider.OPENAI -> "OpenAI"; Provider.GROQ -> "Groq" }
@Composable private fun regionName(value: Region) = stringResource(if (value == Region.EU) R.string.region_eu else R.string.region_us)
@Composable private fun branchName(value: Branch) = stringResource(if (value == Branch.CAPTIONS) R.string.caption_branch else R.string.stt_branch)
@Composable private fun channelName(value: EngineChannel) = stringResource(if (value == EngineChannel.STABLE) R.string.channel_stable else R.string.channel_nightly)
@Composable private fun formatName(value: ExportFormat) = stringResource(when (value) { ExportFormat.MARKDOWN -> R.string.format_markdown; ExportFormat.TEXT -> R.string.format_text; ExportFormat.JSON -> R.string.format_json; ExportFormat.SRT -> R.string.format_srt; ExportFormat.VTT -> R.string.format_vtt; ExportFormat.RAW -> R.string.format_raw })
@Composable private fun modeName(mode: AcquisitionMode) = stringResource(modeLabel(mode))
private fun modeLabel(mode: AcquisitionMode) = when (mode) { AcquisitionMode.CAPTIONS_ONLY -> R.string.mode_captions_only; AcquisitionMode.CAPTIONS_THEN_STT -> R.string.mode_captions_then_stt; AcquisitionMode.STT_ONLY -> R.string.mode_stt_only; AcquisitionMode.BOTH -> R.string.mode_both }
@Composable private fun themeName(theme: String) = stringResource(when (theme) { "DARK" -> R.string.theme_dark; "LIGHT" -> R.string.theme_light; else -> R.string.theme_system })
@Composable private fun audioRetentionName(value: AudioRetention) = stringResource(when (value) { AudioRetention.TEMPORARY -> R.string.audio_temporary; AudioRetention.UNTIL_PERSISTED -> R.string.audio_until_persisted; AudioRetention.KEEP -> R.string.audio_keep })
private fun stateLabel(state: ExecutionState) = when (state) { ExecutionState.QUEUED -> R.string.state_queued; ExecutionState.RUNNING -> R.string.state_running; ExecutionState.WAITING_NETWORK -> R.string.state_waiting_network; ExecutionState.WAITING_RATE_LIMIT -> R.string.state_waiting_rate_limit; ExecutionState.WAITING_USER -> R.string.state_waiting_user; ExecutionState.WAITING_REMOTE -> R.string.state_waiting_remote; ExecutionState.SUBMISSION_UNCERTAIN -> R.string.state_submission_uncertain; ExecutionState.FINISHED -> R.string.state_finished; ExecutionState.CANCELLED -> R.string.state_cancelled }
private fun outcomeLabel(outcome: Outcome) = when (outcome) { Outcome.NONE -> R.string.outcome_none; Outcome.SUCCESS -> R.string.outcome_success; Outcome.SUCCESS_WITH_WARNINGS -> R.string.outcome_success_with_warnings; Outcome.PARTIAL_SUCCESS -> R.string.outcome_partial_success; Outcome.FAILED -> R.string.outcome_failed; Outcome.CANCELLED -> R.string.outcome_cancelled }
private fun phaseLabel(phase: Phase) = when (phase) { Phase.RESOLVE -> R.string.phase_resolve; Phase.FETCH_CAPTIONS -> R.string.phase_fetch_captions; Phase.DOWNLOAD_AUDIO -> R.string.phase_download_audio; Phase.PREPARE_AUDIO -> R.string.phase_prepare_audio; Phase.UPLOAD -> R.string.phase_upload; Phase.SUBMIT -> R.string.phase_submit; Phase.RETRIEVE -> R.string.phase_retrieve; Phase.NORMALIZE -> R.string.phase_normalize; Phase.PERSIST -> R.string.phase_persist }
private fun duration(ms: Long) = String.format(Locale.ROOT, "%d:%02d", ms / 60_000, ms / 1000 % 60)
@Composable private fun messageText(code: String): String = when (code) {
    "NO_ACCEPTABLE_CAPTIONS" -> stringResource(R.string.no_captions)
    "NO_AUDIO" -> stringResource(R.string.no_audio)
    "CHOOSE_AUDIO_TRACK" -> stringResource(R.string.choose_audio_help)
    "AUDIO_TRACK_CHANGED" -> stringResource(R.string.audio_track_changed)
    "IMPORTED_AUDIO_NOT_FOUND" -> stringResource(R.string.reimport_audio)
    "MISSING_RETRY_DATA" -> stringResource(R.string.missing_retry_data)
    "MISSING_RETRY_RESPONSE_SAVED" -> stringResource(R.string.missing_retry_response_saved)
    "JOB_CONFIG_INVALID" -> stringResource(R.string.job_config_invalid)
    "ARTIFACT_FILE_MISSING" -> stringResource(R.string.artifact_file_missing)
    "SCHEDULING_FAILED" -> stringResource(R.string.scheduling_failed)
    "ACTION_BUSY" -> stringResource(R.string.action_busy)
    "CHOOSE_CAPTION_TRACK" -> stringResource(R.string.choose_caption_help)
    "CAPTION_TRACK_CHANGED" -> stringResource(R.string.caption_track_changed)
    "CREDENTIAL_NOT_FOUND", "KEY_NOT_FOUND" -> stringResource(R.string.restore_key)
    "KEY_LOCKED_OR_INVALIDATED", "CREDENTIAL_LOCKED_OR_INVALIDATED", "KEY_CORRUPT", "CREDENTIAL_CORRUPT" -> stringResource(R.string.key_unavailable_help)
    "KEY_STORAGE", "CREDENTIAL_STORAGE" -> stringResource(R.string.key_storage_help)
    "REMOTE_DELETE_CONFIRMED" -> stringResource(R.string.remote_delete_confirmed)
    "NO_REMOTE_HANDLE" -> stringResource(R.string.no_remote_handle)
    "DELETE_PENDING" -> stringResource(R.string.delete_pending)
    "NO_MISSING_BRANCH" -> stringResource(R.string.no_missing_branch)
    "JOB_STILL_RUNNING" -> stringResource(R.string.job_still_running)
    "SUBMISSION_UNCERTAIN" -> stringResource(R.string.state_submission_uncertain) + " · " + stringResource(R.string.remote_may_continue)
    "EXPORT_PENDING", "EXPORT_WRITING" -> stringResource(R.string.export_pending)
    "EXTERNAL_DOCUMENT_MISSING" -> stringResource(R.string.external_document_missing)
    "EXPORT_INTERRUPTED" -> stringResource(R.string.export_interrupted)
    "EXPORT_FAILED" -> stringResource(R.string.export_failed)
    "EXPORT_SCHEDULING_FAILED" -> stringResource(R.string.export_scheduling_failed)
    "JOBS_CREATED" -> stringResource(R.string.jobs_created)
    "KEY_SAVED" -> stringResource(R.string.key_saved)
    "ENGINE_CURRENT" -> stringResource(R.string.engine_current)
    "ENGINE_ACTIVE" -> stringResource(R.string.engine_active)
    "EXPORT_EXPORTED" -> stringResource(R.string.export_complete)
    "EXPORT_PERMISSION_REQUIRED", "PERMISSION_REQUIRED" -> stringResource(R.string.export_permission_required)
    "NOTIFICATIONS_DENIED" -> stringResource(R.string.notifications_denied)
    "REMOTE_MAY_CONTINUE" -> stringResource(R.string.remote_may_continue)
    "PROVIDER_REQUIRED", "CREDENTIAL_REQUIRED", "UPLOAD_APPROVAL_REQUIRED", "MODEL_REQUIRED" -> stringResource(R.string.missing_provider)
    "STORAGE_LIMIT", "AUDIO_IMPORT_STORAGE_LIMIT", "DEVICE_STORAGE_LOW" -> stringResource(R.string.storage_full)
    "BUDGET_EXCEEDED" -> stringResource(R.string.budget_exceeded)
    "BUDGET_INVALID" -> stringResource(R.string.invalid_budget)
    "AUDIO_DURATION_LIMIT" -> stringResource(R.string.invalid_duration)
    "UNSUPPORTED_OPTION", "PROVIDER_CAPABILITY_OR_CREDENTIAL_INVALID" -> stringResource(R.string.unsupported_options)
    "PRICE_UNKNOWN" -> stringResource(R.string.price_unknown)
    else -> stringResource(R.string.operation_failed) + "\n" + stringResource(R.string.error_detail, code)
}

@Composable private fun originName(provenance: Provenance): String = when {
    provenance.origin == Origin.PROVIDER -> "${stringResource(R.string.external_stt)} · ${provenance.provider?.let(::providerName) ?: stringResource(R.string.unknown)}"
    provenance.generation == Generation.UPLOADER_PROVIDED -> stringResource(R.string.origin_uploader)
    provenance.generation == Generation.AUTOMATIC -> stringResource(R.string.origin_automatic)
    else -> stringResource(R.string.origin_unknown)
}
@Composable private fun translationName(value: Translation): String = stringResource(when (value) {
    Translation.NONE -> R.string.translation_none
    Translation.AUTOMATIC -> R.string.translation_automatic
    Translation.UNKNOWN -> R.string.unknown
})

@Composable private fun generationName(value: Generation): String = stringResource(when (value) {
    Generation.UPLOADER_PROVIDED -> R.string.origin_uploader
    Generation.AUTOMATIC -> R.string.origin_automatic
    Generation.UNKNOWN -> R.string.origin_unknown
})
