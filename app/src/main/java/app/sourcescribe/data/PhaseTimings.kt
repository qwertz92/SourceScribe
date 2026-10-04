package app.sourcescribe.data

import app.sourcescribe.core.Phase
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Best-effort durable spans. Timing storage failures must never change the measured operation. */
internal class PhaseTimings(private val dao: SourceScribeDao) {
    suspend fun begin(row: AttemptRow, owner: String, phase: Phase): ActivePhaseTiming {
        val id = UUID.randomUUID().toString()
        val startedAt = System.currentTimeMillis()
        val persisted = try {
            dao.beginPhaseTiming(PhaseTimingRow(id, row.id, phase, startedAt), owner)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
        return ActivePhaseTiming(id.takeIf { persisted }, System.nanoTime())
    }

    suspend fun finish(
        span: ActivePhaseTiming,
        elapsedMs: Long = span.elapsedMs(),
        startedAt: Long? = null,
    ) {
        val id = span.id ?: return
        withContext(NonCancellable) {
            try {
                dao.finishPhaseTiming(id, elapsedMs.coerceAtLeast(0), startedAt)
            } catch (_: Exception) {
                // Incomplete is the truthful durable state when finalization itself fails.
            }
        }
    }

    suspend fun discard(span: ActivePhaseTiming) {
        val id = span.id ?: return
        withContext(NonCancellable) {
            try {
                dao.discardPhaseTiming(id)
            } catch (_: Exception) {
                // Keep an open marker if its removal could not be confirmed.
            }
        }
    }

    suspend fun <T> measure(
        row: AttemptRow,
        owner: String,
        phase: Phase,
        operation: suspend () -> T,
    ): T {
        val span = begin(row, owner, phase)
        return try {
            operation()
        } finally {
            finish(span)
        }
    }
}

internal data class ActivePhaseTiming(val id: String?, private val startedNanos: Long) {
    fun elapsedMs(): Long = TimeUnit.NANOSECONDS.toMillis((System.nanoTime() - startedNanos).coerceAtLeast(0))
}
