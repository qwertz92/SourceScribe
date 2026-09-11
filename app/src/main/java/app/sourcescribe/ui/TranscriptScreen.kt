package app.sourcescribe.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.sourcescribe.MainViewModel
import app.sourcescribe.R
import app.sourcescribe.core.AudioTracks
import app.sourcescribe.core.ExportFormat
import app.sourcescribe.core.Segment
import app.sourcescribe.core.TranscriptDocument
import app.sourcescribe.core.TranscriptExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The result is a document, so it gets a full screen rather than a dialog: the list can use the whole
 * height, the action bar has room, and the system back gesture returns to the history entry it came from.
 */
@Composable
internal fun TranscriptScreen(
    document: TranscriptDocument,
    storedName: String?,
    close: () -> Unit,
    share: () -> Unit,
    export: (ExportFormat, String) -> Unit,
    rename: (String?) -> Unit,
    openHelp: (HelpTopic) -> Unit,
) {
    val context = LocalContext.current
    var query by rememberSaveable(document.artifactId) { mutableStateOf("") }
    var format by rememberSaveable(document.artifactId) { mutableStateOf(ExportFormat.MARKDOWN) }
    var showActions by rememberSaveable(document.artifactId) { mutableStateOf(false) }
    var showRename by rememberSaveable(document.artifactId) { mutableStateOf(false) }
    var showProvenance by rememberSaveable(document.artifactId) { mutableStateOf(false) }
    var chooseCopyPart by rememberSaveable(document.artifactId) { mutableStateOf(false) }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            } catch (_: SecurityException) { /* The current grant may still permit this explicit export. */ }
            export(format, uri.toString())
        }
    }
    // The filter runs off the main thread, so the result carries the query it was computed for. Count
    // line and list are then always describing the same state, instead of the number racing ahead of
    // the list it claims to count.
    val filtered by produceState(Filtered("", document.segments), document.artifactId, query) {
        value = withContext(Dispatchers.Default) {
            Filtered(query, if (query.isBlank()) document.segments
            else document.segments.filter { it.text.contains(query, ignoreCase = true) })
        }
    }
    val segments = filtered.segments
    val countStyle = MaterialTheme.typography.bodySmall
    val textMeasurer = rememberTextMeasurer()
    val copyText by produceState<String?>(null, document.artifactId) { value = withContext(Dispatchers.Default) { document.text } }
    val copyRanges = remember(copyText) { copyText?.let(MainViewModel::clipboardRanges).orEmpty() }
    val copyPart: (IntRange) -> Unit = { range ->
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(
            ClipData.newPlainText("SourceScribe", requireNotNull(copyText).substring(range)))
        chooseCopyPart = false
    }

    BackHandler(enabled = true) {
        when {
            chooseCopyPart -> chooseCopyPart = false
            showRename -> showRename = false
            showActions -> showActions = false
            else -> close()
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(document.source.title ?: stringResource(R.string.result), Modifier.weight(1f).semantics { heading() },
                    style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                IconButton(close) {
                    Icon(painterResource(R.drawable.ic_close), stringResource(R.string.close), Modifier.size(24.dp))
                }
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
                item {
                    OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text(stringResource(R.string.search_transcript)) })
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${originName(document.provenance)} · ${document.language ?: stringResource(R.string.unknown)}",
                                Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            InfoButton(HelpTopic.PROVENANCE, openHelp)
                        }
                        // While a search is running the total alone is misleading: the list underneath is
                        // the filtered one, so the line says how much of the result is currently visible.
                        // Both wordings are measured at the real width and font scale and the taller one is
                        // reserved, so the first keystroke cannot push the warnings and the list down.
                        val total = document.segments.size
                        val totalText = pluralStringResource(R.plurals.segments_count, total, total)
                        val widestMatchText = stringResource(R.string.segments_matching,
                            numberText(total), numberText(total))
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val countHeight = remember(totalText, widestMatchText, countStyle, constraints.maxWidth, textMeasurer) {
                                listOf(totalText, widestMatchText).maxOf { candidate ->
                                    textMeasurer.measure(candidate, countStyle,
                                        constraints = Constraints(maxWidth = constraints.maxWidth)).size.height
                                }
                            }
                            Text(
                                if (filtered.query.isBlank()) totalText else stringResource(R.string.segments_matching,
                                    numberText(segments.size), numberText(total)),
                                Modifier.heightIn(min = with(LocalDensity.current) { countHeight.toDp() }),
                                style = countStyle, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (document.scope.technicallyComplete != true) Text(stringResource(R.string.technically_partial),
                            color = MaterialTheme.colorScheme.error)
                        if (document.warnings.isNotEmpty()) Text(document.warnings.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall)
                        TextButton({ showProvenance = !showProvenance }) { Text(stringResource(R.string.provenance_details)) }
                        if (showProvenance) {
                            Text("${stringResource(R.string.source)}: ${document.source.canonicalUrl ?: document.source.fileName ?: document.source.id}")
                            Text("ID: ${document.source.id}")
                            Text(stringResource(R.string.model_requested, document.provenance.requestedModel ?: stringResource(R.string.unknown)))
                            Text(stringResource(R.string.model_reported, document.provenance.reportedModel ?: stringResource(R.string.unknown)))
                            Text(stringResource(R.string.original_language_value, document.source.originalLanguage ?: stringResource(R.string.unknown)))
                            Text(stringResource(R.string.translation_value, translationName(document.provenance.translation)))
                            document.provenance.sourceAudioTrack?.let { track ->
                                val described = AudioTracks.describe(listOf(track), document.source.durationMs).first()
                                Text(stringResource(R.string.audio_track_value, audioTrackDetail(described)))
                            }
                            document.provenance.captionTrack?.let {
                                Text(stringResource(R.string.caption_track_value, "${captionTrackTitle(it)} · ${it.format}"))
                            }
                        }
                        if (copyRanges.size > 1) Text(stringResource(R.string.copy_large_help), style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (document.segments.isEmpty()) item { Text(stringResource(R.string.transcript_empty)) }
                // Read from the same state the list below is built from, not from the live search text:
                // those two disagree for as long as the filter is still running, and asking the live text
                // here would drop this line while the list is still empty, collapsing the area to nothing.
                else if (filtered.query.isNotBlank() && segments.isEmpty()) item { Text(stringResource(R.string.no_matches)) }
                items(segments.size) { index ->
                    val segment = segments[index]
                    Column {
                        val evidence = listOfNotNull(segment.startMs?.let(::duration), segment.speaker)
                        if (evidence.isNotEmpty()) Text(evidence.joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(segment.text)
                    }
                }
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button({ showActions = true }, Modifier.weight(1f).heightIn(min = 52.dp)) {
                    Text(stringResource(R.string.result_actions))
                }
                OutlinedButton(share, Modifier.heightIn(min = 52.dp)) {
                    Icon(painterResource(R.drawable.ic_share), stringResource(R.string.share_file), Modifier.size(20.dp))
                }
            }
        }
    }

    if (showActions) ActionsDialog(
        document = document,
        storedName = storedName,
        format = format,
        setFormat = { format = it },
        close = { showActions = false },
        copyEnabled = copyRanges.isNotEmpty(),
        copy = { showActions = false; if (copyRanges.size == 1) copyPart(copyRanges.single()) else chooseCopyPart = true },
        share = { showActions = false; share() },
        exportTo = { showActions = false; folder.launch(null) },
        renameRequested = { showActions = false; showRename = true },
        openHelp = openHelp,
    )

    if (showRename) RenameDialog(document, storedName, { showRename = false }) { name ->
        rename(name)
        showRename = false
    }

    if (chooseCopyPart) Dialog({ chooseCopyPart = false }) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().heightIn(max = dialogMaxHeight(0.65f)).padding(20.dp)) {
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    item { Text(stringResource(R.string.copy), style = MaterialTheme.typography.titleLarge) }
                    item { Text(stringResource(R.string.copy_large_help)) }
                    items(copyRanges.size) { index ->
                        TextButton({ copyPart(copyRanges[index]) }, Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                            Text(stringResource(R.string.copy_part, index + 1, copyRanges.size), Modifier.fillMaxWidth())
                        }
                    }
                }
                TextButton({ chooseCopyPart = false }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.back)) }
            }
        }
    }
}

@Composable
private fun ActionsDialog(
    document: TranscriptDocument,
    storedName: String?,
    format: ExportFormat,
    setFormat: (ExportFormat) -> Unit,
    close: () -> Unit,
    copyEnabled: Boolean,
    copy: () -> Unit,
    share: () -> Unit,
    exportTo: () -> Unit,
    renameRequested: () -> Unit,
    openHelp: (HelpTopic) -> Unit,
) {
    Dialog(close) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().heightIn(max = dialogMaxHeight(0.8f)).padding(20.dp)) {
                Text(stringResource(R.string.result_actions), style = MaterialTheme.typography.titleLarge)
                LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        TextButton(copy, Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = copyEnabled) {
                            Text(stringResource(R.string.copy), Modifier.fillMaxWidth())
                        }
                    }
                    item {
                        TextButton(share, Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                            Text(stringResource(R.string.share_file), Modifier.fillMaxWidth())
                        }
                    }
                    item {
                        Choice(stringResource(R.string.export_formats), formatName(format), ExportFormat.entries.filter {
                            if (it == ExportFormat.RAW) document.acquisition.retainRaw else TranscriptExporter.supports(document, it)
                        }, { formatName(it) }, info = HelpTopic.EXPORT_FORMATS, openHelp = openHelp) { setFormat(it) }
                    }
                    item {
                        TextButton(exportTo, Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                            Text(stringResource(R.string.export), Modifier.fillMaxWidth())
                        }
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.file_name), Modifier.weight(1f),
                                    style = MaterialTheme.typography.labelLarge)
                                InfoButton(HelpTopic.FILE_NAMES, openHelp)
                            }
                            Text(TranscriptExporter.fileName(document, format, storedName),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(renameRequested, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                Text(stringResource(R.string.rename_file), Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
                TextButton(close, Modifier.fillMaxWidth()) { Text(stringResource(R.string.back)) }
            }
        }
    }
}

@Composable
private fun RenameDialog(
    document: TranscriptDocument,
    storedName: String?,
    close: () -> Unit,
    apply: (String?) -> Unit,
) {
    val suggestion = remember(document.artifactId, storedName) {
        storedName ?: document.source.title?.let(TranscriptExporter::customStem) ?: TranscriptExporter.generatedStem(document)
    }
    var value by rememberSaveable(document.artifactId) { mutableStateOf(suggestion) }
    val resolved = TranscriptExporter.customStem(value)
    Dialog(close) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().heightIn(max = dialogMaxHeight(0.8f)).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.rename_file), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.file_name_help), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value, { value = it.take(200) }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.file_name)) }, isError = resolved == null)
                Text(TranscriptExporter.fileName(document, ExportFormat.MARKDOWN, resolved),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button({ apply(resolved) }, Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = resolved != null) {
                    Text(stringResource(R.string.apply))
                }
                TextButton({ apply(null) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.reset_file_name)) }
                TextButton(close, Modifier.fillMaxWidth()) { Text(stringResource(R.string.cancel)) }
            }
        }
    }
}

/** A filtered result together with the search text it belongs to. */
private class Filtered(val query: String, val segments: List<Segment>)
