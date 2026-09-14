package app.sourcescribe.data

import app.sourcescribe.core.ExecutionState
import app.sourcescribe.extractor.EngineReferences

/** What [records] hold right now; `EngineUpdateManager` asks only when a new engine needs a slot and none is free. */
internal suspend fun engineReferences(records: SourceScribeDao): EngineReferences =
    engineReferences(records.allAttempts())

/**
 * The engine installations that work in this app still points at, for `EngineUpdateManager` to leave in place when
 * it makes room for a new one (ADR 0009): every installation an attempt is pinned to that has neither finished nor
 * been cancelled, whether it runs or waits for the network, a provider or the reader.
 *
 * A finished attempt keeps no engine, not even one whose result is partial. Asking for the missing chunks again
 * starts the new attempt at `Phase.SUBMIT` with the audio its predecessor prepared, and no step from there on runs an
 * engine; the new attempt carries the engine's id only as the record of where that audio came from (ADR 0006). Every
 * other retry is pinned to the engine that was active when it was created, and holds it while it is unfinished.
 */
internal fun engineReferences(attempts: List<AttemptRow>): EngineReferences = EngineReferences(
    inUse = attempts.filter { it.state != ExecutionState.FINISHED && it.state != ExecutionState.CANCELLED }
        .mapNotNullTo(HashSet()) { it.engineId },
)
