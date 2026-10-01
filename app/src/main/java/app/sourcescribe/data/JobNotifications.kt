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
import androidx.core.content.edit
import app.sourcescribe.R
import app.sourcescribe.core.ExecutionState
import app.sourcescribe.core.Outcome
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JobNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val notifications = NotificationManagerCompat.from(context)
    private val deduplicator = NotificationDeduplicator(context.getSharedPreferences("job_notification_events", Context.MODE_PRIVATE))
    @Volatile private var appForeground = false

    fun setAppForeground(foreground: Boolean) {
        appForeground = foreground
    }

    @Synchronized
    fun update(job: JobRow, attempts: List<AttemptRow>, notifyCompletion: Boolean = true) {
        if (!notifyCompletion && job.state == ExecutionState.FINISHED) {
            JobNotificationPolicy.key(job, attempts, foreground = false)?.let { deduplicator.recordHandled(job.id, it) }
            return
        }
        val key = JobNotificationPolicy.key(job, attempts, appForeground)
        if (key == null) {
            notifications.cancel(job.id, NOTIFICATION_ID)
            if (job.state == ExecutionState.FINISHED) {
                JobNotificationPolicy.key(job, attempts, foreground = false)?.let { deduplicator.recordHandled(job.id, it) }
            } else if (job.state !in setOf(ExecutionState.WAITING_USER, ExecutionState.SUBMISSION_UNCERTAIN)) {
                deduplicator.forget(job.id)
            }
            return
        }
        if (!notificationsAllowed()) return
        ensureChannel()
        val localized = ContextCompat.getContextForLanguage(context)
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        if (!deduplicator.needsNotification(job.id, key)) return
        val text = when (key.state) {
            ExecutionState.WAITING_USER, ExecutionState.SUBMISSION_UNCERTAIN -> localized.getString(stateResource(key.state))
            else -> localized.getString(outcomeResource(key.outcome))
        }
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
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        try {
            notifications.notify(job.id, NOTIFICATION_ID, notification)
            // Posting first avoids losing an event; a crash before this mark can replay the stable tagged notice.
            deduplicator.recordHandled(job.id, key)
        } catch (_: SecurityException) {
            // Permission may be revoked between the check and notify().
        }
    }

    fun dismiss(jobId: String) {
        notifications.cancel(jobId, NOTIFICATION_ID)
        deduplicator.forget(jobId)
    }

    private fun notificationsAllowed(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        }
        return notifications.areNotificationsEnabled()
    }

    private fun ensureChannel() {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun stateResource(state: ExecutionState): Int = when (state) {
        ExecutionState.WAITING_USER -> R.string.state_waiting_user
        ExecutionState.SUBMISSION_UNCERTAIN -> R.string.state_submission_uncertain
        else -> error("No notification label for $state")
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
    }
}

internal data class JobNotificationKey(
    val state: ExecutionState,
    val outcome: Outcome,
)

internal object JobNotificationPolicy {
    fun key(job: JobRow, attempts: List<AttemptRow>, foreground: Boolean): JobNotificationKey? {
        val latest = attempts.groupBy { it.branch }.values.map { rows -> rows.maxBy { it.number } }
        val actionableState = when {
            job.state == ExecutionState.WAITING_USER || job.state == ExecutionState.SUBMISSION_UNCERTAIN -> job.state
            latest.any { it.state == ExecutionState.SUBMISSION_UNCERTAIN } -> ExecutionState.SUBMISSION_UNCERTAIN
            latest.any { it.state == ExecutionState.WAITING_USER } -> ExecutionState.WAITING_USER
            else -> null
        }
        if (foreground && actionableState == null) return null
        val state = actionableState ?: if (job.state == ExecutionState.FINISHED && job.outcome in TERMINAL_OUTCOMES) {
            ExecutionState.FINISHED
        } else {
            return null
        }
        return JobNotificationKey(state, job.outcome)
    }

    private val TERMINAL_OUTCOMES = setOf(
        Outcome.SUCCESS,
        Outcome.SUCCESS_WITH_WARNINGS,
        Outcome.PARTIAL_SUCCESS,
        Outcome.FAILED,
    )
}

internal class NotificationDeduplicator(private val preferences: android.content.SharedPreferences) {

    @Synchronized
    fun needsNotification(jobId: String, key: JobNotificationKey): Boolean = preferences.getString(jobId, null) != signature(key)

    @Synchronized
    fun recordHandled(jobId: String, key: JobNotificationKey) {
        // Synchronous durability prevents an immediate process death from reposting the same event.
        preferences.edit(commit = true) { putString(jobId, signature(key)) }
    }

    @Synchronized
    fun forget(jobId: String) {
        preferences.edit(commit = true) { remove(jobId) }
    }

    private fun signature(key: JobNotificationKey) = "${key.state.name}|${key.outcome.name}"
}
