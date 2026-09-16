package app.sourcescribe.data

import app.sourcescribe.core.Branch
import app.sourcescribe.core.ExecutionState
import app.sourcescribe.core.JobConfig
import app.sourcescribe.core.NetworkPolicy
import app.sourcescribe.core.Phase
import app.sourcescribe.core.SourceKind

/**
 * What a queued job is waiting for, as far as its own stored rows can say it.
 *
 * `QUEUED` is one state with several causes, and the history card has to name the right one or say nothing:
 * a sentence about Wi-Fi sends the reader to the settings of a device whose connection was never the problem.
 */
enum class QueueReason {
    /**
     * Nothing this app can state beyond "queued". The card says what it said before - the job's outcome - and
     * the expanded card still shows the attempt's own error sentence where there is one.
     */
    UNSTATED,

    /** WorkManager holds the attempt until the connection is unmetered, which is what the reader asked for. */
    UNMETERED_CONNECTION,

    /** Another job on this device holds the audio or the provider lock; the attempt retries by itself. */
    ANOTHER_JOB,
}

/**
 * Why a job sits in the queue, decided from the rows alone so it can be tested without a screen.
 *
 * Until 0.4.0 the history card asked only `state == QUEUED && networkPolicy == UNMETERED` and then wrote
 * "waiting for an unmetered connection". Two ordinary ways into `QUEUED` have nothing to do with the network:
 * `SttStep` requeues an attempt with `AUDIO_RESOURCE_BUSY` or `PROVIDER_RESOURCE_BUSY` while another job holds
 * a shared lock, which is the normal case when two sources are started at once, and a phase that needs no
 * network at all - preparing, normalizing, persisting - is queued like every other. Both read as a wait for
 * Wi-Fi, for half a minute at a time, while the device was on Wi-Fi the whole while.
 */
object JobWaits {
    /** The two codes [SttStep] requeues an attempt with while another job holds a shared lock. */
    private val HELD_BY_ANOTHER_JOB = setOf("AUDIO_RESOURCE_BUSY", "PROVIDER_RESOURCE_BUSY")

    /**
     * Whether the work for this phase is enqueued under a network constraint.
     *
     * This is the rule `JobCoordinator.enqueue` builds its `Constraints` from, and it is here rather than
     * there so the sentence on the screen and the constraint in WorkManager cannot drift apart: a phase this
     * answers false for is enqueued as `NetworkType.NOT_REQUIRED` and is never held back by the connection,
     * whatever the job's network policy says.
     *
     * A source whose record cannot be read counts as one that needs the network, which is what `enqueue`
     * does with the same null.
     */
    fun needsNetwork(branch: Branch, phase: Phase, sourceKind: SourceKind?): Boolean = when (branch) {
        Branch.CAPTIONS -> phase != Phase.PERSIST
        Branch.STT ->
            phase == Phase.RESOLVE && sourceKind != SourceKind.LOCAL_AUDIO ||
                phase in setOf(Phase.DOWNLOAD_AUDIO, Phase.UPLOAD, Phase.SUBMIT, Phase.RETRIEVE)
    }

    /**
     * What to say about a job that has not started yet.
     *
     * Only the newest attempt of each branch counts, the same set `JobCoordinator.summarize` derives the job's
     * own state from; an older attempt has been superseded and its error says nothing about the wait.
     *
     * An attempt that carries an error is on a retry delay it wrote itself - the resource locks above, an
     * `INTERRUPTED` run, a recovered lease - so it is not the connection holding it even where the constraint
     * also applies, and every clean phase advance clears the error. Where a branch really is held by the
     * constraint and another only waits for a lock, the connection is named: it is the one of the two the
     * reader can do something about.
     */
    fun reason(
        state: ExecutionState,
        config: JobConfig?,
        attempts: List<AttemptRow>,
        sourceKind: SourceKind?,
    ): QueueReason {
        if (state != ExecutionState.QUEUED) return QueueReason.UNSTATED
        val queued = attempts.groupBy { it.branch }.values
            .map { rows -> rows.maxBy { it.number } }
            .filter { it.state == ExecutionState.QUEUED }
        val held = config?.networkPolicy == NetworkPolicy.UNMETERED &&
            queued.any { it.error == null && needsNetwork(it.branch, it.phase, sourceKind) }
        return when {
            held -> QueueReason.UNMETERED_CONNECTION
            queued.any { it.error in HELD_BY_ANOTHER_JOB } -> QueueReason.ANOTHER_JOB
            else -> QueueReason.UNSTATED
        }
    }
}
