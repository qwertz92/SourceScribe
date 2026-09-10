package app.sourcescribe

import android.content.ClipData
import android.content.Intent
import androidx.core.net.toUri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.sourcescribe.core.*
import app.sourcescribe.ui.*
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
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

internal enum class Page { NEW, HISTORY, SETTINGS, HELP }

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
    val remoteDeletionJobIds by model.remoteDeletionJobIds.collectAsStateWithLifecycle()
    val dark = settings.theme == "DARK" || settings.theme == "SYSTEM" && isSystemInDarkTheme()
    SideEffect {
        (context as? ComponentActivity)?.window?.let { window ->
            androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    val colors = if (dark) darkColorScheme(primary = Color(0xff77d6c5), onPrimary = Color(0xff00382f),
        secondary = Color(0xffb6c9c6), secondaryContainer = Color(0xff334c46), onSecondaryContainer = Color(0xffcbe8df))
        else lightColorScheme(primary = Color(0xff006b5c), onPrimary = Color.White, secondary = Color(0xff41665e),
            secondaryContainer = Color(0xffdcefe8), onSecondaryContainer = Color(0xff103d32),
            surface = Color(0xfff8faf8), background = Color(0xfff8faf8))
    val navigationLabelWidth = (with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() } - 32.dp) / Page.entries.size

    // A visit history rather than a single index, so the system back gesture returns where the user came from.
    var visited by rememberSaveable { mutableStateOf(listOf(Page.NEW.ordinal)) }
    val page = Page.entries[visited.last()]
    val focusManager = LocalFocusManager.current
    fun go(target: Page) {
        if (target.ordinal == visited.last()) return
        // Restoring a screen must never re-open the keyboard on a field the reader did not touch.
        focusManager.clearFocus(force = true)
        visited = (visited.filterNot { it == target.ordinal } + target.ordinal).takeLast(8)
    }
    fun back(): Boolean {
        if (visited.size <= 1) return false
        focusManager.clearFocus(force = true)
        visited = visited.dropLast(1)
        return true
    }

    var helpFocus by rememberSaveable { mutableStateOf<String?>(null) }
    val openHelp: (HelpTopic) -> Unit = { topic -> helpFocus = topic.name; go(Page.HELP) }

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
        if (incoming.isNotBlank() && shareSerial != consumedShare) {
            input = incoming
            // Through go(), so a share that arrives while another page is open clears the keyboard and
            // still leaves that page reachable with the back gesture.
            go(Page.NEW)
            model.clearPreview()
            consumedShare = shareSerial
        }
    }
    MaterialTheme(colorScheme = colors) {
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
            NavigationBar {
                Page.entries.forEach { entry ->
                    NavigationBarItem(selected = page == entry, onClick = { go(entry) },
                        icon = { Icon(painterResource(when (entry) {
                            Page.NEW -> R.drawable.ic_add
                            Page.HISTORY -> R.drawable.ic_history
                            Page.SETTINGS -> R.drawable.ic_settings
                            Page.HELP -> R.drawable.ic_help
                        }), contentDescription = null, modifier = Modifier.size(24.dp)) },
                        label = { Text(stringResource(when (entry) {
                            Page.NEW -> R.string.new_job
                            Page.HISTORY -> R.string.history
                            Page.SETTINGS -> R.string.settings
                            Page.HELP -> R.string.help
                        }), Modifier.widthIn(max = navigationLabelWidth), minLines = 2, maxLines = 2,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center) })
                }
            }
        }) { insets ->
            Column(Modifier.fillMaxSize().padding(insets)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp).semantics { heading() })
                Box(Modifier.fillMaxWidth().height(4.dp)) { if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth()) }
                pageState.SaveableStateProvider(page.name) { when (page) {
                    Page.NEW -> NewSourceScreen(input, { input = it; model.clearPreview() }, config, change, state, settings,
                        openHelp = openHelp,
                        inspect = { model.inspect(input, config) }, onPreset = { change(it) }, onTrack = model::selectTrack,
                        onStart = {
                            if (android.os.Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(context,
                                    android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            }
                            model.startPreviews(); go(Page.HISTORY)
                        }, onCancelPreview = model::clearPreview,
                        onImport = { audioPicker.launch(arrayOf("audio/*", "video/mp4", "video/webm")) },
                        onNotice = model::notice)
                    Page.HISTORY -> HistoryScreen(jobs, sources, attempts, artifacts, exports, remoteDeletionJobIds, model,
                        !notificationsGranted, openHelp) { id -> model.prepareAgain(id, config); go(Page.NEW) }
                    Page.SETTINGS -> SettingsScreen(settings, config, state, jobs, change, model, openHelp)
                    Page.HELP -> HelpScreen(helpFocus?.let { name -> HelpTopic.entries.firstOrNull { it.name == name } }) { helpFocus = null }
                } }
            }
        }
        BackHandler(enabled = visited.size > 1 && state.document == null && state.information == null) { back() }
        state.information?.let { text ->
            InformationDialog(text, state.informationShareable, model::closeInformation, model::shareDiagnostics)
        }
        // A full-screen result covers the page beneath it; a field left focused there would raise the keyboard.
        LaunchedEffect(state.document != null, state.information != null) { focusManager.clearFocus(force = true) }
        state.document?.let { document ->
            TranscriptScreen(document, state.documentName, model::closeArtifact,
                { model.shareArtifact(document.artifactId) },
                { format, tree -> model.exportArtifact(document.artifactId, format, tree) },
                { name -> model.renameArtifact(document.artifactId, name) }, openHelp)
        }
    }
}

@Composable
private fun InformationDialog(text: String, shareable: Boolean, close: () -> Unit, share: () -> Unit) {
    androidx.compose.ui.window.Dialog(close) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(20.dp)) {
                Text(stringResource(if (shareable) R.string.diagnostics else R.string.licenses),
                    style = MaterialTheme.typography.titleLarge)
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(text.lines().size) { index -> Text(text.lines()[index], style = MaterialTheme.typography.bodySmall) }
                }
                if (shareable) Button(share, Modifier.fillMaxWidth()) { Text(stringResource(R.string.share_file)) }
                TextButton(close, Modifier.fillMaxWidth()) { Text(stringResource(R.string.back)) }
            }
        }
    }
}
