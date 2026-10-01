package app.sourcescribe.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.sourcescribe.core.Branch
import app.sourcescribe.core.ExecutionState
import app.sourcescribe.core.Phase
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JobStatesTest {
    private val row = AttemptRow("attempt", "job", Branch.STT, 1, 0)

    @Test fun workerContinuationsAndPollClaimsKeepTheirMeaning() {
        for (phase in listOf(Phase.DOWNLOAD_AUDIO, Phase.PREPARE_AUDIO, Phase.SUBMIT, Phase.NORMALIZE, Phase.PERSIST)) {
            assertEquals(ExecutionState.RUNNING, visibleAttemptState(row.copy(phase = phase), true))
        }
        assertEquals(ExecutionState.QUEUED, visibleAttemptState(row, true))
        assertEquals(ExecutionState.WAITING_REMOTE, visibleAttemptState(row.copy(phase = Phase.RETRIEVE, state = ExecutionState.RUNNING), true))
        assertEquals(ExecutionState.WAITING_NETWORK, visibleAttemptState(row.copy(phase = Phase.SUBMIT), false))
    }

    @Test fun aNetworkRetryWithConnectivityRestoredIsQueued() {
        val retry = row.copy(phase = Phase.SUBMIT, state = ExecutionState.WAITING_NETWORK,
            error = "NETWORK", nextAt = System.currentTimeMillis() + 30_000L)
        assertEquals(ExecutionState.QUEUED, visibleAttemptState(retry, true))
        assertEquals(ExecutionState.WAITING_NETWORK, visibleAttemptState(retry, false))
    }

    @Test fun transientFailuresNameTheirRetryWithoutInventingItsCause() {
        val retry = row.copy(phase = Phase.SUBMIT, state = ExecutionState.WAITING_NETWORK,
            error = "NETWORK", nextAt = 30_000L)
        for (error in listOf("NETWORK", "SERVER")) {
            assertEquals(QueueReason.AUTOMATIC_RETRY, JobWaits.reason(ExecutionState.QUEUED,
                app.sourcescribe.core.JobConfig(), listOf(retry.copy(error = error)), app.sourcescribe.core.SourceKind.YOUTUBE))
        }
        val config = app.sourcescribe.core.JobConfig(networkPolicy = app.sourcescribe.core.NetworkPolicy.UNMETERED)
        assertEquals(QueueReason.UNMETERED_CONNECTION, JobWaits.reason(ExecutionState.WAITING_NETWORK,
            config, listOf(retry.copy(state = ExecutionState.QUEUED, error = "INTERRUPTED")),
            app.sourcescribe.core.SourceKind.YOUTUBE))
    }

    @Test fun actualWaitsAndResourceDelaysArePreserved() {
        for (state in listOf(ExecutionState.WAITING_USER, ExecutionState.WAITING_RATE_LIMIT,
            ExecutionState.SUBMISSION_UNCERTAIN, ExecutionState.CANCELLED, ExecutionState.FINISHED)) {
            assertEquals(state, visibleAttemptState(row.copy(phase = Phase.SUBMIT, state = state), true))
        }
        assertEquals(ExecutionState.QUEUED, visibleAttemptState(row.copy(phase = Phase.PREPARE_AUDIO,
            error = "AUDIO_RESOURCE_BUSY", nextAt = 30_000L), true))
    }
}
