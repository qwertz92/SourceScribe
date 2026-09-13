package app.sourcescribe.data

import app.sourcescribe.core.Branch
import app.sourcescribe.core.ExecutionState
import app.sourcescribe.extractor.EngineReferences

/** What [records] hold right now; `EngineUpdateManager` asks only when a new engine needs a slot and none is free. */
internal suspend fun engineReferences(records: SourceScribeDao): EngineReferences =
    engineReferences(records.allAttempts(), records.allArtifacts())

/**
 * The engine installations that work in this app still points at, for `EngineUpdateManager` to leave in place when
 * it makes room for a new one (ADR 0009).
 *
 * In use: every installation an attempt is pinned to that has neither finished nor been cancelled, whether it runs
 * or waits for the network, a provider or the reader. Retained for a retry: the installation of the latest
 * speech-to-text attempt of a job whose own result is not confirmed complete, because asking for the missing chunks
 * again continues on that attempt's engine (ADR 0006) and starts by resolving the source with it. That is exactly
 * the attempt `JobCoordinator.retry` continues when only the missing part is asked for; any other retry runs on the
 * active engine.
 */
internal fun engineReferences(attempts: List<AttemptRow>, artifacts: List<ArtifactRow>): EngineReferences {
    val inUse = attempts.filter { it.state != ExecutionState.FINISHED && it.state != ExecutionState.CANCELLED }
        .mapNotNullTo(HashSet()) { it.engineId }
    val partial = artifacts.filter { it.branch == Branch.STT && it.complete != true }.mapTo(HashSet()) { it.attemptId }
    val retainedForRetry = attempts.filter { it.branch == Branch.STT }.groupBy { it.jobId }.values
        .map { rows -> rows.maxBy { it.number } }
        .filter { it.id in partial }
        .mapNotNullTo(HashSet()) { it.engineId }
    return EngineReferences(inUse, retainedForRetry)
}
