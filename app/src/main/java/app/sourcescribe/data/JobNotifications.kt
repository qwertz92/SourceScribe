package app.sourcescribe.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.sourcescribe.R
import app.sourcescribe.core.ExecutionState
import app.sourcescribe.core.Outcome
import app.sourcescribe.core.Phase
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JobNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val notifications = NotificationManagerCompat.from(context)

    fun update(job: JobRow, attempts: List<AttemptRow>) {
        if (!notificationsAllowed()) return
        ensureChannel()
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        val phase = attempts
            .firstOrNull { it.state in ACTIVE_STATES }
            ?.phase
            ?: attempts.firstOrNull { it.state !in TERMINAL_STATES }?.phase
            ?: attempts.maxByOrNull { it.createdAt }?.phase
            ?: Phase.RESOLVE
        val stateLabel = context.getString(stateResource(job.state))
        val phaseLabel = context.getString(phaseResource(phase))
        val outcomeLabel = if (job.outcome == Outcome.NONE) null else context.getString(outcomeResource(job.outcome))
        val text = listOfNotNull(stateLabel, phaseLabel, outcomeLabel).joinToString(" · ")
        val ongoing = job.state in ACTIVE_STATES
        val pendingIntent = PendingIntent.getActivity(
            context,
            job.id.hashCode(),
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .setOnlyAlertOnce(true)
            .build()
        try {
            notifications.notify(job.id, NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission may be revoked between the check and notify().
        }
    }

    fun dismiss(jobId: String) {
        notifications.cancel(jobId, NOTIFICATION_ID)
    }

    private fun notificationsAllowed(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return notifications.areNotificationsEnabled()
    }

    private fun ensureChannel() {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun stateResource(state: ExecutionState): Int = when (state) {
        ExecutionState.QUEUED -> R.string.state_queued
        ExecutionState.RUNNING -> R.string.state_running
        ExecutionState.WAITING_NETWORK -> R.string.state_waiting_network
        ExecutionState.WAITING_RATE_LIMIT -> R.string.state_waiting_rate_limit
        ExecutionState.WAITING_USER -> R.string.state_waiting_user
        ExecutionState.WAITING_REMOTE -> R.string.state_waiting_remote
        ExecutionState.SUBMISSION_UNCERTAIN -> R.string.state_submission_uncertain
        ExecutionState.FINISHED -> R.string.state_finished
        ExecutionState.CANCELLED -> R.string.state_cancelled
    }

    private fun phaseResource(phase: Phase): Int = when (phase) {
        Phase.RESOLVE -> R.string.phase_resolve
        Phase.FETCH_CAPTIONS -> R.string.phase_fetch_captions
        Phase.DOWNLOAD_AUDIO -> R.string.phase_download_audio
        Phase.PREPARE_AUDIO -> R.string.phase_prepare_audio
        Phase.UPLOAD -> R.string.phase_upload
        Phase.SUBMIT -> R.string.phase_submit
        Phase.RETRIEVE -> R.string.phase_retrieve
        Phase.NORMALIZE -> R.string.phase_normalize
        Phase.PERSIST -> R.string.phase_persist
    }

    private fun outcomeResource(outcome: Outcome): Int = when (outcome) {
        Outcome.NONE -> R.string.outcome_none
        Outcome.SUCCESS -> R.string.outcome_success
        Outcome.SUCCESS_WITH_WARNINGS -> R.string.outcome_success_with_warnings
        Outcome.PARTIAL_SUCCESS -> R.string.outcome_partial_success
        Outcome.FAILED -> R.string.outcome_failed
        Outcome.CANCELLED -> R.string.outcome_cancelled
    }

    private companion object {
        const val CHANNEL_ID = "job_updates"
        const val NOTIFICATION_ID = 1
        val ACTIVE_STATES = setOf(
            ExecutionState.RUNNING,
            ExecutionState.WAITING_NETWORK,
            ExecutionState.WAITING_RATE_LIMIT,
            ExecutionState.WAITING_REMOTE,
        )
        val TERMINAL_STATES = setOf(ExecutionState.FINISHED, ExecutionState.CANCELLED)
    }
}
