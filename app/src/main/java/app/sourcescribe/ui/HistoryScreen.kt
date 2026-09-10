package app.sourcescribe.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.sourcescribe.MainViewModel
import app.sourcescribe.R
import app.sourcescribe.core.*
import app.sourcescribe.data.ArtifactRow
import app.sourcescribe.data.AttemptRow
import app.sourcescribe.data.ExportRow
import app.sourcescribe.data.JobRow
import app.sourcescribe.data.SourceRow
import app.sourcescribe.data.decodeStoredJobConfig
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
        if (job == null) actionsFor = null else JobActionsDialog(
            job = job,
            // A limit belongs to the job for good, so a repeat of this job would end at the same limit again.
            limitReached = attempts.filter { it.jobId == job.id }
                .groupBy { it.branch }.values.mapNotNull { rows -> rows.maxBy { it.number }.error }
                .firstOrNull { it in setOf("AUDIO_LONGER_THAN_LIMIT", "AUDIO_DURATION_UNKNOWN") },
            remoteDeletionPossible = job.id in remoteDeletionJobIds,
            close = { actionsFor = null },
            openHelp = openHelp,
            cancel = { actionsFor = null; model.cancel(job.id) },
            resume = { actionsFor = null; model.resume(job.id) },
            prepareAgain = { actionsFor = null; prepareAgain(job.id) },
            confirm = { action -> actionsFor = null; confirmation = job.id to action },
        )
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

@Composable
private fun JobCard(
    job: JobRow,
    title: String,
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
                        StatusRow(job)
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
                    if (job.state == ExecutionState.WAITING_REMOTE) Text(
                        stringResource(R.string.provider_elapsed, duration((now - job.createdAt).coerceAtLeast(0))),
                        style = MaterialTheme.typography.bodySmall)
                    attempts.forEach { attempt ->
                        Text("${if (attempt.branch == Branch.CAPTIONS) stringResource(R.string.mode_captions_only) else stringResource(R.string.mode_stt_only)} · ${stringResource(phaseLabel(attempt.phase))}",
                            style = MaterialTheme.typography.bodySmall)
                        if (attempt.processedBytes > 0) Text(stringResource(R.string.processed_bytes, attempt.processedBytes),
                            style = MaterialTheme.typography.bodySmall)
                        attempt.error?.let { AttemptError(it, openHelp) }
                    }
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

@Composable
private fun StatusRow(job: JobRow) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when {
        job.outcome in setOf(Outcome.SUCCESS, Outcome.SUCCESS_WITH_WARNINGS) -> colors.secondaryContainer to colors.onSecondaryContainer
        job.outcome in setOf(Outcome.FAILED, Outcome.CANCELLED) -> colors.errorContainer to colors.onErrorContainer
        job.state in setOf(ExecutionState.WAITING_USER, ExecutionState.SUBMISSION_UNCERTAIN) ->
            colors.errorContainer to colors.onErrorContainer
        else -> colors.surfaceVariant to colors.onSurfaceVariant
    }
    // The chip's width follows its word, and its word changes while the list is open, so the outcome
    // goes underneath instead of beside it and nothing moves sideways when a job progresses.
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        StatusChip(stringResource(stateChipLabel(job.state)), container, content)
        Text(stringResource(outcomeLabel(job.outcome)), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun JobActionsDialog(
    job: JobRow,
    limitReached: String?,
    remoteDeletionPossible: Boolean,
    close: () -> Unit,
    openHelp: (HelpTopic) -> Unit,
    cancel: () -> Unit,
    resume: () -> Unit,
    prepareAgain: () -> Unit,
    confirm: (Int) -> Unit,
) {
    val savedConfig = remember(job.config) { decodeStoredJobConfig(job.config) }
    val maximumHeight = dialogMaxHeight(0.8f)
    Dialog(close) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().heightIn(max = maximumHeight).padding(20.dp)) {
                Text(stringResource(R.string.job_actions), style = MaterialTheme.typography.titleLarge)
                LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (limitReached != null) item {
                        Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(messageText(limitReached), color = MaterialTheme.colorScheme.error)
                            Text(stringResource(R.string.limit_needs_new_job), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(prepareAgain, Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                                Text(stringResource(R.string.prepare_again))
                            }
                        }
                    }
                    if (job.state !in setOf(ExecutionState.FINISHED, ExecutionState.CANCELLED)) item {
                        TextButton(cancel, Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                            Text(stringResource(R.string.cancel_job), Modifier.fillMaxWidth())
                        }
                    }
                    if (savedConfig != null && job.state == ExecutionState.WAITING_USER && !job.cancelRequested) item {
                        TextButton(resume, Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                            Text(stringResource(R.string.resume_job), Modifier.fillMaxWidth())
                        }
                    }
                    if (savedConfig != null && job.state != ExecutionState.RUNNING) {
                        if (job.outcome !in setOf(Outcome.SUCCESS, Outcome.SUCCESS_WITH_WARNINGS)) item {
                            TextButton({ confirm(R.string.retry_missing) }, Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                                Text(stringResource(R.string.retry_missing), Modifier.fillMaxWidth())
                            }
                        }
                        item {
                            TextButton({ confirm(R.string.retry_all) }, Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                                Text(stringResource(R.string.retry_all), Modifier.fillMaxWidth())
                            }
                        }
                        item {
                            Column {
                                TextButton(prepareAgain, Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                                    Text(stringResource(R.string.prepare_again), Modifier.fillMaxWidth())
                                }
                                Text(stringResource(R.string.prepare_again_help), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (job.state in setOf(ExecutionState.FINISHED, ExecutionState.CANCELLED) && remoteDeletionPossible) item {
                        TextButton({ confirm(R.string.delete_remote) }, Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                            Text(stringResource(R.string.delete_remote), Modifier.fillMaxWidth())
                        }
                    }
                    item {
                        TextButton({ confirm(R.string.delete_job) }, Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                            Text(stringResource(R.string.delete_job), Modifier.fillMaxWidth(), fontWeight = FontWeight.SemiBold)
                        }
                    }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.help_states_title), Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall)
                            InfoButton(HelpTopic.STATES, openHelp)
                        }
                    }
                }
                TextButton(close, Modifier.fillMaxWidth()) { Text(stringResource(R.string.back)) }
            }
        }
    }
}

private fun budgetText(microUsd: Long): String =
    microUsd.toBigDecimal().movePointLeft(6).stripTrailingZeros().toPlainString() + " USD"
