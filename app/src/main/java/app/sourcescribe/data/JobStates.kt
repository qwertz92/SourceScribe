package app.sourcescribe.data

import app.sourcescribe.core.ExecutionState
import app.sourcescribe.core.Phase

/** Worker boundaries are continuations, while explicit waits remain visible. */
internal fun visibleAttemptState(row: AttemptRow, networkAllowed: Boolean): ExecutionState = when {
    row.state == ExecutionState.WAITING_NETWORK && networkAllowed -> ExecutionState.QUEUED
    row.state == ExecutionState.RUNNING && row.phase == Phase.RETRIEVE -> ExecutionState.WAITING_REMOTE
    row.state in setOf(ExecutionState.QUEUED, ExecutionState.WAITING_REMOTE) && !networkAllowed -> ExecutionState.WAITING_NETWORK
    row.state == ExecutionState.QUEUED && row.phase != Phase.RESOLVE && row.nextAt == 0L && row.error == null -> ExecutionState.RUNNING
    else -> row.state
}
