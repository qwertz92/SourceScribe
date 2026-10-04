package app.sourcescribe.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.sourcescribe.MainViewModel
import app.sourcescribe.R
import app.sourcescribe.core.*
import app.sourcescribe.data.ArtifactRow
import app.sourcescribe.data.AttemptRow
import app.sourcescribe.data.ExportRow
import app.sourcescribe.data.JobAction
import app.sourcescribe.data.JobActions
import app.sourcescribe.data.JobRow
import app.sourcescribe.data.JobSituation
import app.sourcescribe.data.JobWaits
import app.sourcescribe.data.PhaseTimingRow
import app.sourcescribe.data.QueueReason
import app.sourcescribe.data.SourceRow
import app.sourcescribe.data.SttStep
import app.sourcescribe.data.decodeStoredJobConfig
import app.sourcescribe.data.decodeStoredSourceKind
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay

private enum class HistoryFilter(val label: Int) {
    ALL(R.string.filter_all),
    ACTIVE(R.string.filter_active),
    ATTENTION(R.string.filter_attention),
    SUCCESS(R.string.filter_success),
    FAILED(R.string.filter_failed);

    fun matches(job: JobRow): Boolean = when (this) {
        ALL -> true
        ACTIVE -> job.state in setOf(ExecutionState.QUEUED, ExecutionState.RUNNING,
            ExecutionState.WAITING_NETWORK, ExecutionState.WAITING_RATE_LIMIT, ExecutionState.WAITING_REMOTE)
        ATTENTION -> job.state in setOf(ExecutionState.WAITING_USER, ExecutionState.SUBMISSION_UNCERTAIN)
        SUCCESS -> job.outcome in setOf(Outcome.SUCCESS, Outcome.SUCCESS_WITH_WARNINGS)
        FAILED -> job.outcome in setOf(Outcome.FAILED, Outcome.PARTIAL_SUCCESS, Outcome.CANCELLED)
    }
}

@Composable
internal fun HistoryScreen(
    jobs: List<JobRow>,
    sources: List<SourceRow>,
    attempts: List<AttemptRow>,
    artifacts: List<ArtifactRow>,
    exports: List<ExportRow>,
    remoteDeletionJobIds: List<String>,
    model: MainViewModel,
    notificationsDisabled: Boolean,
    openHelp: (HelpTopic) -> Unit,
    phaseTimings: List<PhaseTimingRow> = emptyList(),
    onOpenFolders: () -> Unit = {},
    hasExportFolders: Boolean = false,
    prepareAgain: (String) -> Unit,
) {
    var confirmation by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var actionsFor by rememberSaveable { mutableStateOf<String?>(null) }
    var expanded by rememberSaveable { mutableStateOf(setOf<String>()) }
    val context = LocalContext.current
    var retryExportId by rememberSaveable { mutableStateOf<String?>(null) }
    val exportFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val id = retryExportId
        retryExportId = null
        if (uri != null && id != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                model.retryExport(id, uri.toString())
            } catch (_: SecurityException) { model.exportPermissionError() }
        }
    }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(HistoryFilter.ALL) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val hasUnfinishedJobs = jobs.any { it.state !in setOf(ExecutionState.FINISHED, ExecutionState.CANCELLED) }
    LaunchedEffect(hasUnfinishedJobs) {
        while (hasUnfinishedJobs) { delay(1000); now = System.currentTimeMillis() }
    }
    val sourceNames = remember(sources) { sources.associate { it.id to it.title } }
    // Whether a source is a downloaded video or a file already on the device decides whether resolving it
    // needs the network at all, which is what a queued job's own line is allowed to claim.
    val sourceKinds = remember(sources) { sources.associate { it.id to decodeStoredSourceKind(it.snapshot) } }
    val sourceChannels = remember(sources) { sources.associate { it.id to decodeStoredSourceChannel(it.snapshot) } }
    // The date has to be searchable in the form the card shows it; the raw millisecond count matches nothing a reader types.
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    val visible = jobs.filter { job ->
        filter.matches(job) &&
            historySearchText(sourceNames[job.sourceId], job.sourceId, sourceChannels[job.sourceId],
                dateFormat.format(Date(job.createdAt)), job.config)
                .contains(query, ignoreCase = true)
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.search_history)) }, singleLine = true)
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(HistoryFilter.entries.size) { index ->
                    val entry = HistoryFilter.entries[index]
                    FilterChip(filter == entry, { filter = entry }, { Text(stringResource(entry.label)) })
                }
            }
        }
        if (jobs.isNotEmpty()) item {
            Text(pluralStringResource(R.plurals.history_count, jobs.size, visible.size, jobs.size),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (notificationsDisabled && jobs.isNotEmpty()) item {
            Text(stringResource(R.string.notifications_denied), style = MaterialTheme.typography.bodySmall)
        }
        if (hasExportFolders || exports.any { it.documentUri != null }) item {
            TextButton(onOpenFolders, Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.history_export_folders))
            }
        }
        if (exports.isNotEmpty()) item { TextButton({ model.reconcileExports() }) { Text(stringResource(R.string.check_exports)) } }
        if (jobs.isNotEmpty() && visible.isEmpty()) item {
            Text(stringResource(if (query.isBlank()) R.string.no_filter_matches else R.string.no_matches))
        }
        if (jobs.isEmpty()) item {
            Text(stringResource(R.string.no_jobs), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.no_jobs_help), Modifier.padding(top = 12.dp))
        }
        items(visible, key = { it.id }) { job ->
            val jobAttempts = attempts.filter { it.jobId == job.id }
            val jobAttemptIds = jobAttempts.mapTo(hashSetOf()) { it.id }
            JobCard(
                job = job,
                title = sourceNames[job.sourceId] ?: job.sourceId,
                sourceChannel = sourceChannels[job.sourceId],
                sourceKind = sourceKinds[job.sourceId],
                open = job.id in expanded,
                toggle = { expanded = if (job.id in expanded) expanded - job.id else expanded + job.id },
                attempts = jobAttempts,
                jobArtifacts = artifacts.filter { it.jobId == job.id },
                exports = exports,
                phaseTimings = phaseTimings.filter { it.attemptId in jobAttemptIds },
                now = now,
                openHelp = openHelp,
                showActions = { actionsFor = job.id },
                retryExport = { id -> retryExportId = id; exportFolder.launch(null) },
                openArtifact = model::openArtifact,
                shareArtifact = model::shareArtifact,
            )
        }
    }
    actionsFor?.let { id ->
        val job = jobs.firstOrNull { it.id == id }
        if (job == null) {
            actionsFor = null
        } else {
            // Keep the newest attempt of each branch together with its phase so the dialog can explain which
            // branch stopped and show only structured provider details from that checkpoint.
            val latestAttempts = remember(attempts, job.id) {
                attempts.filter { it.jobId == job.id }.groupBy { it.branch }.values
                    .mapNotNull { rows -> rows.maxByOrNull { it.number } }
                    .sortedBy { it.branch.name }
            }
            val errors = latestAttempts.mapNotNull { it.error }.distinct()
            val situation = JobSituation(
                state = job.state,
                outcome = job.outcome,
                errors = errors,
                configReadable = decodeStoredJobConfig(job.config) != null,
                cancelRequested = job.cancelRequested,
                remoteDeletionPossible = job.id in remoteDeletionJobIds,
                incompleteResult = artifacts.any { it.jobId == job.id && it.complete != true },
            )
            JobActionsDialog(
                situation = situation,
                attempts = latestAttempts,
                // A limit belongs to the job for good, so a repeat of this job would end at the same limit again.
                limitReached = errors.any { it in setOf("AUDIO_LONGER_THAN_LIMIT", "AUDIO_DURATION_UNKNOWN") },
                close = { actionsFor = null },
                openHelp = openHelp,
                act = { action ->
                    actionsFor = null
                    when (action) {
                        JobAction.CANCEL -> model.cancel(job.id)
                        JobAction.RESUME -> model.resume(job.id)
                        JobAction.PREPARE_AGAIN -> prepareAgain(job.id)
                        // The three that discard work, cost money at the provider, or delete something ask first.
                        JobAction.RETRY_MISSING -> confirmation = job.id to R.string.retry_missing
                        JobAction.RETRY_ALL -> confirmation = job.id to R.string.retry_all
                        JobAction.DELETE_REMOTE -> confirmation = job.id to R.string.delete_remote
                        JobAction.DELETE_JOB -> confirmation = job.id to R.string.delete_job
                        JobAction.NOTHING -> Unit
                    }
                },
            )
        }
    }
    confirmation?.let { (id, action) ->
        ConfirmationDialog(stringResource(action), stringResource(when (action) {
            R.string.delete_job -> R.string.delete_job_help
            R.string.delete_remote -> R.string.delete_remote_help
            else -> R.string.retry_job_help
        }), { confirmation = null }) {
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

internal data class HistoryResultOrigin(
    val branch: Branch,
    val provider: Provider? = null,
    val model: String? = null,
)

/** Provider settings are origin only after an artifact proves that the STT branch actually ran. */
internal fun historyResultOrigins(artifacts: List<ArtifactRow>, config: JobConfig?): List<HistoryResultOrigin> = buildList {
    if (artifacts.any { it.branch == Branch.CAPTIONS }) add(HistoryResultOrigin(Branch.CAPTIONS))
    artifacts.asSequence().filter { it.branch == Branch.STT }
        .map { HistoryResultOrigin(Branch.STT, config?.provider, it.providerModel) }
        .distinct().forEach { add(it) }
}

internal fun shouldShowJobActions(job: JobRow, open: Boolean): Boolean =
    !job.deleteRequested && (open || job.outcome !in setOf(Outcome.SUCCESS, Outcome.SUCCESS_WITH_WARNINGS))

internal fun historySearchText(
    title: String?,
    sourceId: String,
    channel: String?,
    formattedDate: String,
    config: String,
): String = listOfNotNull(title, sourceId, channel, formattedDate, config).joinToString(" ")

internal fun decodeStoredSourceChannel(raw: String): String? = try {
    kotlinx.serialization.json.Json.decodeFromString<Source>(raw).channel
} catch (_: IllegalArgumentException) {
    null
}

internal data class PhaseTimingSummary(val phase: Phase, val elapsedMs: Long?, val incomplete: Boolean)

internal fun summarizePhaseTimings(rows: List<PhaseTimingRow>): List<PhaseTimingSummary> = Phase.entries.mapNotNull { phase ->
    val phaseRows = rows.filter { it.phase == phase }
    if (phaseRows.isEmpty()) null else {
        val elapsed = phaseRows.mapNotNull { it.elapsedMs }.takeIf { it.isNotEmpty() }?.sum()
        PhaseTimingSummary(phase, elapsed, phaseRows.any { it.elapsedMs == null })
    }
}

internal fun historyElapsedMillis(job: JobRow, now: Long): Long? {
    val end = job.finishedAt ?: if (job.state in setOf(ExecutionState.FINISHED, ExecutionState.CANCELLED)) return null else now
    if (end < job.createdAt) return null
    return (end - job.createdAt).takeIf { it >= 0 }
}

// A stand-in as wide as `byteSize` for a terabyte. It is not a ceiling on the value: `ReservedText`
// measures the real text as well and gives it the room it needs. Zeros stand in for every digit, which
// holds exactly in a font whose digits share one width and approximately in any other.
private const val LONGEST_BYTE_SIZE = "0000.0 GB"

/** Below this the two counts are too close together in time for their difference to be a rate. */
private const val MIN_RATE_INTERVAL_MS = 250L

/**
 * The horizontal content padding Material 3 gives a text button, which is what its label is inset by.
 * `ButtonDefaults.TextButtonContentPadding` is a `PaddingValues` and offers no start value on its own.
 */
private val TEXT_BUTTON_INSET = 12.dp

@Composable
internal fun JobCard(
    job: JobRow,
    title: String,
    sourceChannel: String?,
    sourceKind: SourceKind?,
    open: Boolean,
    toggle: () -> Unit,
    attempts: List<AttemptRow>,
    jobArtifacts: List<ArtifactRow>,
    exports: List<ExportRow>,
    phaseTimings: List<PhaseTimingRow>,
    now: Long,
    openHelp: (HelpTopic) -> Unit,
    showActions: () -> Unit,
    retryExport: (String) -> Unit,
    openArtifact: (String) -> Unit,
    shareArtifact: (String) -> Unit,
) {
    val rotation by animateFloatAsState(if (open) 180f else 0f, label = "job-chevron")
    val savedConfig = remember(job.config) { decodeStoredJobConfig(job.config) }
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    val origins = remember(jobArtifacts, savedConfig) { historyResultOrigins(jobArtifacts, savedConfig) }
    val completed = job.outcome in setOf(Outcome.SUCCESS, Outcome.SUCCESS_WITH_WARNINGS)
    var timingDetails by rememberSaveable(job.id, "timings") { mutableStateOf(false) }
    var technicalDetails by rememberSaveable(job.id, "technical") { mutableStateOf(false) }
    OutlinedCard(onClick = toggle, modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(sourceChannel?.takeIf { it.isNotBlank() },
                        dateFormat.format(Date(job.createdAt))).joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (origins.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        for (origin in origins) Text(historyOriginText(origin),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HistoryElapsedLine(job, now)
                    StatusRow(job, savedConfig, attempts, sourceKind)
                }
                Icon(painterResource(R.drawable.ic_expand_more),
                    contentDescription = stringResource(if (open) R.string.collapse_entry else R.string.expand_entry),
                    modifier = Modifier.size(24.dp).rotate(rotation))
            }
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (open) {
                    if (completed) {
                        TextButton({ technicalDetails = !technicalDetails }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.history_technical_details))
                        }
                        if (technicalDetails) {
                            JobTechnicalDetails(savedConfig, attempts, openHelp)
                        }
                    } else {
                        JobTechnicalDetails(savedConfig, attempts, openHelp)
                    }
                    TextButton({ timingDetails = !timingDetails }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.history_timings))
                    }
                    if (timingDetails) PhaseTimingDetails(phaseTimings)
                    val artifactIds = jobArtifacts.map { it.id }.toSet()
                    exports.filter { it.artifactId in artifactIds }.forEach { row ->
                        val statusMessage = messageText("EXPORT_${row.state.name}")
                        Text("${formatName(ExportFormat.valueOf(row.format))} · $statusMessage", style = MaterialTheme.typography.bodySmall)
                        row.error?.let { error ->
                            val errorMessage = messageText(error)
                            if (errorMessage != statusMessage) Text(errorMessage, style = MaterialTheme.typography.bodySmall)
                        }
                        if (!job.deleteRequested && row.state in setOf(ExportState.FAILED, ExportState.PERMISSION_REQUIRED)) {
                            TextButton({ retryExport(row.id) }) { Text(stringResource(R.string.retry_export_folder)) }
                        }
                    }
                }
                jobArtifacts.forEach { artifact ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton({ openArtifact(artifact.id) }, Modifier.weight(1f).heightIn(min = 48.dp)) {
                            Text("${stringResource(R.string.open_result)} · ${branchName(artifact.branch)}", maxLines = 2)
                        }
                        OutlinedButton({ shareArtifact(artifact.id) }, Modifier.heightIn(min = 48.dp)) {
                            Icon(painterResource(R.drawable.ic_share), stringResource(R.string.share_result), Modifier.size(20.dp))
                        }
                    }
                }
                if (job.deleteRequested) Text(stringResource(R.string.delete_pending))
                else if (shouldShowJobActions(job, open)) OutlinedButton(showActions, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.job_actions))
                }
            }
        }
    }
}

@Composable
private fun historyOriginText(origin: HistoryResultOrigin): String = when (origin.branch) {
    Branch.CAPTIONS -> stringResource(R.string.history_origin_youtube_captions)
    Branch.STT -> {
        val providerModel = listOfNotNull(
            origin.provider?.let(::providerName),
            origin.model?.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
        if (providerModel.isEmpty()) stringResource(R.string.history_origin_audio_transcription)
        else stringResource(R.string.history_origin_stt, providerModel)
    }
}

@Composable
private fun HistoryElapsedLine(job: JobRow, now: Long) {
    val elapsed = historyElapsedMillis(job, now)
    when {
        job.finishedAt != null && elapsed != null ->
            Text(stringResource(R.string.history_total_elapsed, historyDuration(elapsed)), style = MaterialTheme.typography.bodySmall)
        job.finishedAt != null || job.state in setOf(ExecutionState.FINISHED, ExecutionState.CANCELLED) ->
            Text(stringResource(R.string.history_not_recorded), style = MaterialTheme.typography.bodySmall)
        elapsed != null -> ReservedText(
            stringResource(R.string.history_live_elapsed, historyDuration(elapsed)),
            listOf(stringResource(R.string.history_live_elapsed, historyDuration(999L * 60 * 60 * 1000))),
            MaterialTheme.typography.bodySmall,
        )
        else -> Text(stringResource(R.string.history_not_recorded), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun JobTechnicalDetails(
    config: JobConfig?,
    attempts: List<AttemptRow>,
    openHelp: (HelpTopic) -> Unit,
) {
    if (config == null) Text(stringResource(R.string.job_config_invalid), color = MaterialTheme.colorScheme.error)
    else if (config.mode != AcquisitionMode.CAPTIONS_ONLY) {
        val provider = config.provider?.let(::providerName) ?: stringResource(R.string.no_provider)
        Text(stringResource(R.string.stt_configuration,
            listOfNotNull(provider, config.model).joinToString(" · ")), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.limits_summary, limitDuration(config.maxAudioSeconds),
            config.maxCostMicrousd?.let { budgetText(it) } ?: stringResource(R.string.budget_none)),
            style = MaterialTheme.typography.bodySmall)
    }
    attempts.forEach { AttemptLines(it, openHelp) }
}

@Composable
internal fun PhaseTimingDetails(rows: List<PhaseTimingRow>) {
    val summaries = remember(rows) { summarizePhaseTimings(rows) }
    Text(stringResource(R.string.history_timing_scope), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (summaries.isEmpty()) Text(stringResource(R.string.history_no_timings), style = MaterialTheme.typography.bodySmall)
    summaries.forEach { summary ->
        val phase = stringResource(phaseLabel(summary.phase))
        val text = when {
            summary.incomplete && summary.elapsedMs != null -> stringResource(
                R.string.history_timing_recorded_incomplete, phase, historyDuration(summary.elapsedMs))
            summary.incomplete -> stringResource(R.string.history_timing_incomplete, phase)
            else -> stringResource(R.string.history_timing_phase, phase, historyDuration(requireNotNull(summary.elapsedMs)))
        }
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal fun historyDuration(milliseconds: Long): String =
    if (milliseconds in 1L..999L) "$milliseconds ms" else duration(milliseconds)

/**
 * What one attempt of an open job card says about itself: its branch and phase, the rendition it bound, how
 * many bytes have moved, and the reason it stopped.
 *
 * A separate composable from [JobCard] because this is the part of the card whose lines come and go while it
 * is open, and `JobCardLayoutTest` measures it without a view model.
 */
@Composable
internal fun AttemptLines(attempt: AttemptRow, openHelp: (HelpTopic) -> Unit) {
    if (attempt.state in setOf(ExecutionState.FINISHED, ExecutionState.CANCELLED)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.history_attempt_phase,
                branchName(attempt.branch), stringResource(phaseLabel(attempt.phase))),
                style = MaterialTheme.typography.bodySmall)
            val track = remember(attempt.checkpoint) { SttStep.storedAudioTrack(attempt.checkpoint) }
            if (track != null) Text(stringResource(R.string.audio_track_value, audioTrackSummary(track)),
                style = MaterialTheme.typography.bodySmall)
            if (showsTransferProgress(attempt)) {
                val total = attempt.totalBytes?.takeIf { it > 0 && attempt.processedBytes <= it }
                val progress = if (total == null) stringResource(R.string.processed_bytes, byteSize(attempt.processedBytes))
                else stringResource(R.string.transfer_of_total,
                    percentOf(attempt.processedBytes, total), byteSize(attempt.processedBytes), byteSize(total))
                Text(progress, style = MaterialTheme.typography.bodySmall)
            }
            attempt.error?.let { AttemptError(it, openHelp) }
        }
        return
    }
    ReservedText(attemptPhaseText(attempt), attemptPhaseAlternatives(), MaterialTheme.typography.bodySmall,
    )
    // Which rendition this attempt actually bound, from what it stored when it resolved
    // the source. Read once per stored checkpoint rather than on every recomposition.
    //
    // The line keeps its room while it has nothing to say. It is empty until the attempt has resolved the
    // source and full from then on, and both happen while the card sits open in front of the reader, so
    // showing it only when it is filled moved everything under it once per job.
    val boundTrack = remember(attempt.checkpoint) { SttStep.storedAudioTrack(attempt.checkpoint) }
    ReservedText(
        if (boundTrack == null) "" else stringResource(R.string.audio_track_value, audioTrackSummary(boundTrack)),
        listOf(boundTrackAlternative()),
        MaterialTheme.typography.bodySmall)
    // In the units the rest of this app uses for a download — `byteSize`, which is
    // what an audio track's size is shown in two screens away — rather than a raw digit
    // count that grew a character at a time and had no reserved height either. With a
    // percentage and a rate while the bytes are moving, so a wait can be judged; both
    // only where they are real, see `transferRate` and `totalBytes`.
    //
    // Reserved the same way and for the same reason: the line is there for the length of a download or an
    // upload and gone before and after, which is three moves of everything beneath it per transfer.
    val rate = transferRate(attempt.id, attempt.phase, attempt.processedBytes,
        isMoving = isTransferMoving(attempt))
    val total = attempt.totalBytes?.takeIf { it > 0 && attempt.processedBytes <= it }
    val moved = when {
        attempt.processedBytes <= 0 -> ""
        total == null -> stringResource(R.string.processed_bytes, byteSize(attempt.processedBytes))
        else -> stringResource(R.string.transfer_of_total,
            percentOf(attempt.processedBytes, total), byteSize(attempt.processedBytes), byteSize(total))
    }
    ReservedText(
        if (!showsTransferProgress(attempt)) ""
        else if (rate == null) moved else stringResource(R.string.transfer_rate, moved, byteSize(rate)),
        progressAlternatives(),
        MaterialTheme.typography.bodySmall)
    val error = attempt.error
    AttemptErrorSummary(
        message = if (error == null) "" else messageText(error),
        helpTopic = error?.let(::attemptHelpTopic),
        openHelp = openHelp,
    )
}

/** A waiting job explains itself where it is; the length limit additionally links to why it cannot be raised. */
@Composable
private fun AttemptError(code: String, openHelp: (HelpTopic) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(messageText(code), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error)
        attemptHelpTopic(code)?.let { InfoButton(it, openHelp) }
    }
}

/** A two-line summary keeps the expanded card steady; Actions still shows the complete error text. */
@Composable
internal fun AttemptErrorSummary(message: String, helpTopic: HelpTopic?, openHelp: (HelpTopic) -> Unit) {
    val style = MaterialTheme.typography.bodySmall
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val twoLineHeight = remember(textMeasurer, style, density) {
        with(density) { textMeasurer.measure("M\nM", style, maxLines = 2).size.height.toDp() }
    }
    Row(Modifier.heightIn(min = twoLineHeight), verticalAlignment = Alignment.CenterVertically) {
        Text(
            message,
            Modifier.weight(1f),
            style = style,
            color = if (message.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            helpTopic?.let { InfoButton(it, openHelp) }
        }
    }
}

private fun attemptHelpTopic(code: String): HelpTopic? = when (code) {
    "AUDIO_LONGER_THAN_LIMIT" -> HelpTopic.LIMITS
    "CHOOSE_AUDIO_TRACK", "AUDIO_TRACK_CHANGED" -> HelpTopic.AUDIO_TRACK
    "SUBMISSION_UNCERTAIN", "REMOTE_MAY_CONTINUE" -> HelpTopic.STATES
    else -> null
}

/** The share of [total] that [processed] is, as whole percent, never past a hundred. */
internal fun percentOf(processed: Long, total: Long): Int =
    if (total <= 0) 0 else ((processed * 100) / total).coerceIn(0, 100).toInt()

/**
 * The observed transfer rate of one attempt in bytes per second, or null until there is something to observe.
 *
 * Measured, not derived: two byte counts the database delivered and the time between them. The pipeline
 * writes a count about once a second while it moves bytes, so the first rate appears a second in and then
 * follows what is really happening — a number nobody has to stand behind as a forecast. A new attempt or a
 * new phase starts over, because the speed of a download says nothing about the upload that follows it.
 */
@Composable
private fun transferRate(attemptId: String, phase: Phase, bytes: Long, isMoving: Boolean): Long? {
    var previous by remember(attemptId, phase) { mutableStateOf<Pair<Long, Long>?>(null) }
    var rate by remember(attemptId, phase) { mutableStateOf<Long?>(null) }
    LaunchedEffect(attemptId, phase, bytes, isMoving) {
        if (!isMoving || !isTransferPhase(phase)) {
            previous = null
            rate = null
        } else {
            val now = System.currentTimeMillis()
            previous?.let { (measuredAt, measuredBytes) ->
                val elapsed = now - measuredAt
                rate = if (bytes > measuredBytes && elapsed >= MIN_RATE_INTERVAL_MS)
                    (bytes - measuredBytes) * 1000L / elapsed else null
            }
            previous = now to bytes
        }
    }
    return rate.takeIf { isMoving && isTransferPhase(phase) }
}

/** Only phases whose stored byte counters describe an audio download or upload expose transfer progress. */
internal fun isTransferPhase(phase: Phase): Boolean = phase in setOf(Phase.DOWNLOAD_AUDIO, Phase.UPLOAD, Phase.SUBMIT)

/** Transfer progress is present only when bytes have actually moved in a transfer phase. */
internal fun showsTransferProgress(attempt: AttemptRow): Boolean {
    return isTransferPhase(attempt.phase) && attempt.processedBytes > 0
}

/** A complete audio request means the provider has the upload and its response is the next visible wait. */
internal fun isUploadCompleteWaiting(attempt: AttemptRow): Boolean {
    if (attempt.state != ExecutionState.RUNNING) return false
    if (attempt.phase !in setOf(Phase.UPLOAD, Phase.SUBMIT)) return false
    val total = attempt.totalBytes?.takeIf { it > 0 && attempt.processedBytes <= it } ?: return false
    return attempt.processedBytes > 0 && attempt.processedBytes == total
}

/** A completed upload is still shown at 100%, but its last measured speed is no longer current. */
internal fun isTransferMoving(attempt: AttemptRow): Boolean =
    attempt.state == ExecutionState.RUNNING && isTransferPhase(attempt.phase) && !isUploadCompleteWaiting(attempt)

private fun currentAttempt(attempts: List<AttemptRow>): AttemptRow? = attempts
    .asSequence()
    .filter { it.state !in setOf(ExecutionState.FINISHED, ExecutionState.CANCELLED) }
    .maxWithOrNull(compareBy<AttemptRow> { when (it.state) {
        ExecutionState.RUNNING -> 4
        ExecutionState.WAITING_REMOTE -> 3
        ExecutionState.WAITING_NETWORK, ExecutionState.WAITING_RATE_LIMIT,
        ExecutionState.WAITING_USER, ExecutionState.SUBMISSION_UNCERTAIN -> 2
        ExecutionState.QUEUED -> 1
        ExecutionState.FINISHED, ExecutionState.CANCELLED -> 0
    } }
        .thenBy { it.createdAt }.thenBy { it.number })

@Composable
private fun attemptPhaseText(attempt: AttemptRow): String = waitingProviderResponseLabel(attempt)?.let {
    stringResource(it)
} ?: stringResource(R.string.history_current_phase,
    branchName(attempt.branch), stringResource(phaseLabel(attempt.phase)))

internal fun isWaitingForTranscription(attempt: AttemptRow): Boolean =
    attempt.state in setOf(ExecutionState.RUNNING, ExecutionState.WAITING_REMOTE) && attempt.phase == Phase.RETRIEVE

@StringRes
internal fun waitingProviderResponseLabel(attempt: AttemptRow): Int? =
    R.string.history_waiting_provider_response.takeIf {
        isWaitingForTranscription(attempt) || isUploadCompleteWaiting(attempt)
    }

@Composable
private fun attemptPhaseAlternatives(): List<String> = buildList {
    add(stringResource(R.string.history_waiting_provider_response))
    Branch.entries.forEach { branch ->
        Phase.entries.forEach { phase ->
            add(stringResource(R.string.history_current_phase,
                branchName(branch), stringResource(phaseLabel(phase))))
        }
    }
}

@Composable
internal fun CollapsedAttemptLines(attempt: AttemptRow?) {
    ReservedText(
        if (attempt == null) "" else attemptPhaseText(attempt),
        attemptPhaseAlternatives(),
        MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val rate = attempt?.let {
        transferRate(it.id, it.phase, it.processedBytes,
            isMoving = isTransferMoving(it))
    }
    val total = attempt?.totalBytes?.takeIf { it > 0 && attempt.processedBytes <= it }
    val moved = when {
        attempt == null || attempt.processedBytes <= 0 -> ""
        total == null -> stringResource(R.string.processed_bytes, byteSize(attempt.processedBytes))
        else -> stringResource(R.string.transfer_of_total,
            percentOf(attempt.processedBytes, total), byteSize(attempt.processedBytes), byteSize(total))
    }
    ReservedText(
        if (attempt == null || !showsTransferProgress(attempt)) ""
        else if (rate == null) moved else stringResource(R.string.transfer_rate, moved, byteSize(rate)),
        progressAlternatives(),
        MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * The height the bound-rendition line reserves: the shape [audioTrackSummary] gives an ordinary YouTube
 * rendition, at its widest - codec, container, a four-digit data rate, channels, the format id with the
 * longest suffix the extractor appends, the size, and a full language tag.
 *
 * It is not a ceiling on the value. `ReservedText` measures the real summary as well and gives it whatever
 * room it needs, so an unusually long codec name or language tag costs the no-jump guarantee for that one
 * card and never a word of the text.
 */
@Composable
private fun boundTrackAlternative(): String = stringResource(R.string.audio_track_value, listOf(
    "Opus", "WebM",
    stringResource(R.string.audio_track_bitrate, numberText(9999)),
    stringResource(R.string.audio_track_stereo),
    stringResource(R.string.audio_track_format, "000-drc"),
    LONGEST_BYTE_SIZE,
    "xx-XXXX",
).joinToString(" · "))

/** The heights the progress line reserves: every shape it can take, at its widest. */
@Composable
private fun progressAlternatives(): List<String> {
    val plain = stringResource(R.string.processed_bytes, LONGEST_BYTE_SIZE)
    val ofTotal = stringResource(R.string.transfer_of_total, 100, LONGEST_BYTE_SIZE, LONGEST_BYTE_SIZE)
    return listOf(plain, ofTotal,
        stringResource(R.string.transfer_rate, plain, LONGEST_BYTE_SIZE),
        stringResource(R.string.transfer_rate, ofTotal, LONGEST_BYTE_SIZE))
}

@Composable
private fun StatusRow(job: JobRow, config: JobConfig?, attempts: List<AttemptRow>, sourceKind: SourceKind?) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when {
        job.outcome in setOf(Outcome.SUCCESS, Outcome.SUCCESS_WITH_WARNINGS) -> colors.secondaryContainer to colors.onSecondaryContainer
        job.outcome in setOf(Outcome.FAILED, Outcome.CANCELLED) -> colors.errorContainer to colors.onErrorContainer
        job.state in setOf(ExecutionState.WAITING_USER, ExecutionState.SUBMISSION_UNCERTAIN) ->
            colors.errorContainer to colors.onErrorContainer
        else -> colors.surfaceVariant to colors.onSurfaceVariant
    }
    // Why this job has not started, decided in the data layer from the rows alone - see `JobWaits`. The
    // screen only chooses the words, so the one claim it makes about the device's connection is the claim
    // `JobCoordinator.enqueue` really wrote into the work request.
    val reason = remember(job.state, config, attempts, sourceKind) {
        JobWaits.reason(job.state, config, attempts, sourceKind)
    }
    val outcome = stringResource(outcomeLabel(job.outcome))
    val unmetered = stringResource(R.string.waiting_unmetered)
    val otherJob = stringResource(R.string.waiting_other_job)
    val automaticRetry = stringResource(R.string.waiting_automatic_retry)
    // The chip's width follows its word, and its word changes while the list is open, so the outcome
    // goes underneath instead of beside it and nothing moves sideways when a job progresses. A waiting
    // sentence replaces the outcome rather than adding a line, and each reserves the height of the
    // tallest, so a job that leaves the queue moves nothing under it either.
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        StatusChip(stringResource(stateChipLabel(job.state)), container, content)
        ReservedText(
            when (reason) {
                QueueReason.UNMETERED_CONNECTION -> unmetered
                QueueReason.ANOTHER_JOB -> otherJob
                QueueReason.AUTOMATIC_RETRY -> automaticRetry
                QueueReason.UNSTATED -> outcome
            },
            listOf(outcome, unmetered, otherJob, automaticRetry),
            MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        currentAttempt(attempts)?.let { CollapsedAttemptLines(it) }
    }
}

@Composable
private fun JobActionsDialog(
    situation: JobSituation,
    limitReached: Boolean,
    attempts: List<AttemptRow> = emptyList(),
    close: () -> Unit,
    openHelp: (HelpTopic) -> Unit,
    act: (JobAction) -> Unit,
) {
    Dialog(close) {
        Surface(shape = MaterialTheme.shapes.large) {
            JobActionsContent(situation, limitReached, dialogMaxHeight(0.8f), openHelp, act, close, attempts)
        }
    }
}

/**
 * The job actions dialog: what happened, the one action to try, and what each of the others would do.
 *
 * Until 0.4.0 this was a list of every action the state allowed, in the order the code happened to write them,
 * with no word about any of them and no sentence about what had gone wrong. The owner's report of 16 September
 * was exactly that: "Decision required" and six buttons whose names he could not map to anything. The first of
 * them, "Resume safely", was a dead end for the job he had - see [JobActions.offered].
 *
 * The height is fixed rather than bounded, and the list inside it scrolls. A dialog that takes its height from
 * its content is a different size for every job, and the button the reader reaches for sits somewhere else each
 * time; the owner's standing rule is that nothing may jump. `JobActionsLayoutTest` measures it.
 *
 * It is a separate composable from the dialog around it so that test can place it without a window.
 */
@Composable
internal fun JobActionsContent(
    situation: JobSituation,
    limitReached: Boolean,
    height: Dp,
    openHelp: (HelpTopic) -> Unit,
    act: (JobAction) -> Unit,
    close: () -> Unit,
    attempts: List<AttemptRow> = emptyList(),
) {
    val offered = remember(situation) { JobActions.offered(situation) }
    val recommended = remember(situation) { JobActions.recommended(situation) }
    Column(Modifier.fillMaxWidth().height(height).padding(20.dp)) {
        Text(stringResource(R.string.job_actions), style = MaterialTheme.typography.titleLarge)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item(key = "happened") {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.job_actions_what_happened),
                        style = MaterialTheme.typography.titleSmall)
                    // The error's own sentence, the one the job card shows as well, with the same help button
                    // beside it. A job that recorded no error says how it ended instead.
                    val failedAttempts = attempts.filter { it.error != null }
                    if (failedAttempts.isNotEmpty()) {
                        failedAttempts.forEach { attempt ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(stringResource(R.string.failed_branch_step,
                                    branchName(attempt.branch), stringResource(phaseLabel(attempt.phase))),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                AttemptError(requireNotNull(attempt.error), openHelp)
                                ProviderFailureDetails(attempt)
                            }
                        }
                    } else if (situation.errors.isEmpty()) {
                        Text(stringResource(outcomeLabel(situation.outcome)), style = MaterialTheme.typography.bodyMedium)
                    } else {
                        situation.errors.forEach { AttemptError(it, openHelp) }
                    }
                    if (limitReached) {
                        Text(stringResource(R.string.limit_needs_new_job), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item(key = "recommended") {
                Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.job_actions_recommended),
                        style = MaterialTheme.typography.titleSmall)
                    if (recommended == null || recommended == JobAction.NOTHING) {
                        Text(stringResource(R.string.job_actions_nothing), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Button({ act(recommended) }, Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                            Text(stringResource(actionLabel(recommended)))
                        }
                        Text(stringResource(actionExplanation(recommended)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            val others = offered.filter { it != recommended }
            if (others.isNotEmpty()) item(key = "others") {
                Text(stringResource(R.string.job_actions_other), Modifier.padding(top = 16.dp),
                    style = MaterialTheme.typography.titleSmall)
            }
            items(others, key = { it.name }) { action ->
                Column(Modifier.padding(top = 4.dp)) {
                    TextButton({ act(action) }, Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Text(stringResource(actionLabel(action)), Modifier.fillMaxWidth(),
                            fontWeight = if (action == JobAction.DELETE_JOB) FontWeight.SemiBold else null)
                    }
                    // A text button insets its label by `TEXT_BUTTON_INSET`; the line belongs to that label, so it
                    // starts where the label starts instead of at the column's own edge.
                    Text(stringResource(actionExplanation(action)), Modifier.padding(start = TEXT_BUTTON_INSET),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item(key = "help") {
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.help_states_title), Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall)
                    InfoButton(HelpTopic.STATES, openHelp)
                }
            }
        }
        TextButton(close, Modifier.fillMaxWidth()) { Text(stringResource(R.string.back)) }
    }
}

@Composable
private fun ProviderFailureDetails(attempt: AttemptRow) {
    val error = attempt.error ?: return
    val failure = remember(attempt.checkpoint) { SttStep.storedProviderFailure(attempt.checkpoint) }
    val details = if (failure == null) emptyList() else buildList {
        failure.httpStatus?.let { add(stringResource(R.string.provider_http_status, it)) }
        failure.operation?.let {
            add(stringResource(R.string.provider_operation, stringResource(providerOperationLabel(it))))
        }
        failure.reason?.let {
            add(stringResource(R.string.provider_reason, stringResource(providerRejectionReasonLabel(it))))
        }
    }
    if (details.isEmpty() && error in setOf("INVALID_INPUT", "PROVIDER_INVALID_INPUT", "RESPONSE_INVALID_INPUT")) {
        Text(stringResource(R.string.provider_diagnostic_unknown), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        details.forEach { detail ->
            Text(detail, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The name of an action, as it stands on its button. */
@StringRes
private fun actionLabel(action: JobAction): Int = when (action) {
    JobAction.CANCEL -> R.string.cancel_job
    JobAction.RESUME -> R.string.resume_job
    JobAction.RETRY_MISSING -> R.string.retry_missing
    JobAction.RETRY_ALL -> R.string.retry_all
    JobAction.PREPARE_AGAIN -> R.string.prepare_again
    JobAction.DELETE_REMOTE -> R.string.delete_remote
    JobAction.DELETE_JOB -> R.string.delete_job
    // Never on a button; [JobActionsContent] shows a sentence for it instead.
    JobAction.NOTHING -> R.string.job_actions_nothing
}

/** What an action does, and when to reach for it. One line under every button. */
@StringRes
private fun actionExplanation(action: JobAction): Int = when (action) {
    JobAction.CANCEL -> R.string.cancel_job_line
    JobAction.RESUME -> R.string.resume_job_line
    JobAction.RETRY_MISSING -> R.string.retry_missing_line
    JobAction.RETRY_ALL -> R.string.retry_all_line
    JobAction.PREPARE_AGAIN -> R.string.prepare_again_line
    JobAction.DELETE_REMOTE -> R.string.delete_remote_line
    JobAction.DELETE_JOB -> R.string.delete_job_line
    JobAction.NOTHING -> R.string.job_actions_nothing
}

private fun budgetText(microUsd: Long): String =
    microUsd.toBigDecimal().movePointLeft(6).stripTrailingZeros().toPlainString() + " USD"
