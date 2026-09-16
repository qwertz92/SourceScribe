package app.sourcescribe.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
    LaunchedEffect(jobs.any { it.state == ExecutionState.WAITING_REMOTE }) {
        while (jobs.any { it.state == ExecutionState.WAITING_REMOTE }) { delay(1000); now = System.currentTimeMillis() }
    }
    val sourceNames = remember(sources) { sources.associate { it.id to it.title } }
    // Whether a source is a downloaded video or a file already on the device decides whether resolving it
    // needs the network at all, which is what a queued job's own line is allowed to claim.
    val sourceKinds = remember(sources) { sources.associate { it.id to decodeStoredSourceKind(it.snapshot) } }
    // The date has to be searchable in the form the card shows it; the raw millisecond count matches nothing a reader types.
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    val visible = jobs.filter { job ->
        filter.matches(job) &&
            "${sourceNames[job.sourceId]} ${job.sourceId} ${dateFormat.format(Date(job.createdAt))} ${job.config}"
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
        if (exports.isNotEmpty()) item { TextButton({ model.reconcileExports() }) { Text(stringResource(R.string.check_exports)) } }
        if (jobs.isNotEmpty() && visible.isEmpty()) item {
            Text(stringResource(if (query.isBlank()) R.string.no_filter_matches else R.string.no_matches))
        }
        if (jobs.isEmpty()) item {
            Text(stringResource(R.string.no_jobs), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.no_jobs_help), Modifier.padding(top = 12.dp))
        }
        items(visible, key = { it.id }) { job ->
            JobCard(
                job = job,
                title = sourceNames[job.sourceId] ?: job.sourceId,
                sourceKind = sourceKinds[job.sourceId],
                open = job.id in expanded,
                toggle = { expanded = if (job.id in expanded) expanded - job.id else expanded + job.id },
                attempts = attempts.filter { it.jobId == job.id },
                jobArtifacts = artifacts.filter { it.jobId == job.id },
                exports = exports,
                now = now,
                model = model,
                openHelp = openHelp,
                showActions = { actionsFor = job.id },
                retryExport = { id -> retryExportId = id; exportFolder.launch(null) },
            )
        }
    }
    actionsFor?.let { id ->
        val job = jobs.firstOrNull { it.id == id }
        if (job == null) {
            actionsFor = null
        } else {
            // What the newest attempt of every branch stopped with, which is what the dialog explains and what
            // the recommendation is derived from. One code per branch: an older attempt of the same branch has
            // been superseded by the one that followed it.
            val errors = remember(attempts, job.id) {
                attempts.filter { it.jobId == job.id }.groupBy { it.branch }.values
                    .mapNotNull { rows -> rows.maxBy { it.number }.error }.distinct()
            }
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

// Stand-ins as wide as what `duration` and `byteSize` put into those two labels. `duration` has no unit
// above hours, so a three-digit hour count is what the first reserves for; `byteSize` has none above GB,
// so a terabyte prints as four digits of gigabytes, which is what the second reserves for. Neither is a
// ceiling on the value: `ReservedText` measures the real text as well and gives it the room it needs, so a
// longer one only costs the no-jump guarantee for that one case. Zeros stand in for every digit, which
// holds exactly in a font whose digits share one width and approximately in any other.
private const val LONGEST_ELAPSED = "000:00:00"
private const val LONGEST_BYTE_SIZE = "0000.0 GB"

/** Below this the two counts are too close together in time for their difference to be a rate. */
private const val MIN_RATE_INTERVAL_MS = 250L

/**
 * The horizontal content padding Material 3 gives a text button, which is what its label is inset by.
 * `ButtonDefaults.TextButtonContentPadding` is a `PaddingValues` and offers no start value on its own.
 */
private val TEXT_BUTTON_INSET = 12.dp

@Composable
private fun JobCard(
    job: JobRow,
    title: String,
    sourceKind: SourceKind?,
    open: Boolean,
    toggle: () -> Unit,
    attempts: List<AttemptRow>,
    jobArtifacts: List<ArtifactRow>,
    exports: List<ExportRow>,
    now: Long,
    model: MainViewModel,
    openHelp: (HelpTopic) -> Unit,
    showActions: () -> Unit,
    retryExport: (String) -> Unit,
) {
    val rotation by animateFloatAsState(if (open) 180f else 0f, label = "job-chevron")
    val savedConfig = remember(job.config) { decodeStoredJobConfig(job.config) }
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column {
            Surface(onClick = toggle, color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().semantics { role = Role.Button }) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(
                            dateFormat.format(Date(job.createdAt)),
                            savedConfig?.provider?.let(::providerName)
                                ?: stringResource(R.string.mode_captions_only).takeIf { savedConfig?.mode == AcquisitionMode.CAPTIONS_ONLY },
                        ).joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        StatusRow(job, savedConfig, attempts, sourceKind)
                    }
                    Icon(painterResource(R.drawable.ic_expand_more),
                        contentDescription = stringResource(if (open) R.string.collapse_entry else R.string.expand_entry),
                        modifier = Modifier.size(24.dp).rotate(rotation))
                }
            }
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (open) {
                    if (savedConfig == null) Text(stringResource(R.string.job_config_invalid), color = MaterialTheme.colorScheme.error)
                    else if (savedConfig.mode != AcquisitionMode.CAPTIONS_ONLY) {
                        val provider = savedConfig.provider?.let(::providerName) ?: stringResource(R.string.no_provider)
                        Text(stringResource(R.string.stt_configuration,
                            listOfNotNull(provider, savedConfig.model).joinToString(" · ")),
                            style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.limits_summary, limitDuration(savedConfig.maxAudioSeconds),
                            savedConfig.maxCostMicrousd?.let { budgetText(it) } ?: stringResource(R.string.budget_none)),
                            style = MaterialTheme.typography.bodySmall)
                    }
                    // The one line on this screen that changes without anybody touching it: `now` is
                    // refreshed every second while a job waits, and the time inside it grows from 9:59 to
                    // 10:00 to 1:00:00. Whether a wrap point falls inside that range depends on the width
                    // and the font scale, and it has not been measured on a device. Reserving the widest
                    // case is what makes the answer not matter: the card cannot gain a line mid-wait.
                    if (job.state == ExecutionState.WAITING_REMOTE) ReservedText(
                        stringResource(R.string.provider_elapsed, duration((now - job.createdAt).coerceAtLeast(0))),
                        listOf(stringResource(R.string.provider_elapsed, LONGEST_ELAPSED)),
                        MaterialTheme.typography.bodySmall)
                    attempts.forEach { attempt -> AttemptLines(attempt, openHelp) }
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
                        FilledTonalButton({ model.openArtifact(artifact.id) }, Modifier.weight(1f).heightIn(min = 48.dp)) {
                            Text("${stringResource(R.string.open_result)} · ${branchName(artifact.branch)}", maxLines = 2)
                        }
                        OutlinedButton({ model.shareArtifact(artifact.id) }, Modifier.heightIn(min = 48.dp)) {
                            Icon(painterResource(R.drawable.ic_share), stringResource(R.string.share_result), Modifier.size(20.dp))
                        }
                    }
                }
                if (job.deleteRequested) Text(stringResource(R.string.delete_pending))
                else OutlinedButton(showActions, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.job_actions))
                }
            }
        }
    }
}

/**
 * What one attempt of an open job card says about itself: its branch and phase, the rendition it bound, how
 * many bytes have moved, and the reason it stopped.
 *
 * A separate composable from [JobCard] because this is the part of the card whose lines come and go while it
 * is open, and `JobCardLayoutTest` measures it without a view model.
 */
@Composable
internal fun AttemptLines(attempt: AttemptRow, openHelp: (HelpTopic) -> Unit) {
    Text("${if (attempt.branch == Branch.CAPTIONS) stringResource(R.string.mode_captions_only) else stringResource(R.string.mode_stt_only)} · ${stringResource(phaseLabel(attempt.phase))}",
        style = MaterialTheme.typography.bodySmall)
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
    val rate = transferRate(attempt.id, attempt.phase, attempt.processedBytes)
    val total = attempt.totalBytes?.takeIf { it > 0 && attempt.processedBytes <= it }
    val moved = when {
        attempt.processedBytes <= 0 -> ""
        total == null -> stringResource(R.string.processed_bytes, byteSize(attempt.processedBytes))
        else -> stringResource(R.string.transfer_of_total,
            percentOf(attempt.processedBytes, total), byteSize(attempt.processedBytes), byteSize(total))
    }
    ReservedText(
        if (moved.isEmpty() || rate == null) moved else stringResource(R.string.transfer_rate, moved, byteSize(rate)),
        progressAlternatives(),
        MaterialTheme.typography.bodySmall)
    attempt.error?.let { AttemptError(it, openHelp) }
}

/** A waiting job explains itself where it is; the length limit additionally links to why it cannot be raised. */
@Composable
private fun AttemptError(code: String, openHelp: (HelpTopic) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(messageText(code), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error)
        when (code) {
            "AUDIO_LONGER_THAN_LIMIT" -> InfoButton(HelpTopic.LIMITS, openHelp)
            "CHOOSE_AUDIO_TRACK", "AUDIO_TRACK_CHANGED" -> InfoButton(HelpTopic.AUDIO_TRACK, openHelp)
            "SUBMISSION_UNCERTAIN", "REMOTE_MAY_CONTINUE" -> InfoButton(HelpTopic.STATES, openHelp)
            else -> Unit
        }
    }
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
private fun transferRate(attemptId: String, phase: Phase, bytes: Long): Long? {
    var previous by remember(attemptId, phase) { mutableStateOf<Pair<Long, Long>?>(null) }
    var rate by remember(attemptId, phase) { mutableStateOf<Long?>(null) }
    LaunchedEffect(attemptId, phase, bytes) {
        val now = System.currentTimeMillis()
        previous?.let { (measuredAt, measuredBytes) ->
            val elapsed = now - measuredAt
            if (bytes > measuredBytes && elapsed >= MIN_RATE_INTERVAL_MS) {
                rate = (bytes - measuredBytes) * 1000L / elapsed
            }
        }
        previous = now to bytes
    }
    return rate
}

/**
 * The height the bound-rendition line reserves: the shape [audioTrackSummary] gives an ordinary YouTube
 * rendition, at its widest - codec, container, a four-digit data rate, channels, the format id with the
 * longest suffix the extractor appends, the size, and a full language tag.
 *
 * Like [LONGEST_ELAPSED] it is not a ceiling on the value. `ReservedText` measures the real summary as well
 * and gives it whatever room it needs, so an unusually long codec name or language tag costs the no-jump
 * guarantee for that one card and never a word of the text.
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
    // The chip's width follows its word, and its word changes while the list is open, so the outcome
    // goes underneath instead of beside it and nothing moves sideways when a job progresses. A waiting
    // sentence replaces the outcome rather than adding a line, and all three reserve the height of the
    // tallest, so a job that leaves the queue moves nothing under it either.
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        StatusChip(stringResource(stateChipLabel(job.state)), container, content)
        ReservedText(
            when (reason) {
                QueueReason.UNMETERED_CONNECTION -> unmetered
                QueueReason.ANOTHER_JOB -> otherJob
                QueueReason.UNSTATED -> outcome
            },
            listOf(outcome, unmetered, otherJob),
            MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun JobActionsDialog(
    situation: JobSituation,
    limitReached: Boolean,
    close: () -> Unit,
    openHelp: (HelpTopic) -> Unit,
    act: (JobAction) -> Unit,
) {
    Dialog(close) {
        Surface(shape = MaterialTheme.shapes.large) {
            JobActionsContent(situation, limitReached, dialogMaxHeight(0.8f), openHelp, act, close)
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
                    if (situation.errors.isEmpty()) {
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
