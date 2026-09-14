# ADR 0009 — Clear Old Engine Slots When a New Engine Finds No Room

Date: September 13, 2026. Status: implemented in round 17, corrected in round 18 (last section). Which
checks have actually run is recorded in [STATUS.md](../STATUS.md).

## Context

`EngineUpdateManager` holds at most five engine slots (`MAX_INSTALLATIONS`). Through round 17, it never
removed a healthy installation: `discardUnhealthyCandidate` only deletes unhealthy candidates, and
`cleanupCrashOrphans` only removes slots that no entry in `state.json` names. Once all five slots were
occupied — say, by the bundled engine plus four loaded updates — every further `stage` call ended in
`STORAGE`. Worse, an app update carrying a different bundled yt-dlp version hit the same wall:
`ensureBundledLocked` found no free slot and also threw `STORAGE`. Because `active()`, `installations()`,
`rollbackTarget()`, `stage()`, and `activate()` all call that function first, the app could then no longer
check any YouTube source, nor start or resume any job — permanently, and with no action that fixed it.
The round 17 invariant reviewer reported this.

Requirement from `docs/SECURITY_UPDATES.md`, section S7: "Keep the active, the previous healthy, and the
bundled version; clean up older versions only once nothing active still references them."

## Decision

- **When clearing happens:** only when a new slot needs to be created and none is free, inside
  `materializeSlot`. For loading an update, that is the moment right after download and signature
  verification. Before the download, `stage` only asks whether room *could* be made, and removes nothing
  at that point: no installation is lost for a download that might still fail.
- **Never removed:** the active and the previous installation, the engine bundled with this app version,
  the incoming installation itself, and any installation an unfinished attempt is bound to — whether it's
  running or waiting on the network, a provider, or the user.
- **Order:** first, slot directories that no entry names; then the oldest installations that nothing
  references. `state.json` keeps entries in the order they were first recorded. A finished attempt
  references no engine, not even one with a partial result (corrected in round 18).
- **How the manager knows what jobs need:** the `extractor` module knows nothing about jobs. The app
  passes a `references` function into the constructor (`AppModule`), which answers `engineReferences`
  through Room. It's only asked when something actually has to go. If that query fails, its error
  propagates unchanged, and nothing is removed: without this knowledge, no slot can be safely deleted,
  and no reason nobody actually checked gets asserted.
- **How removal happens:** first the entry in `state.json` (via `AtomicFile`), then the directory. A crash
  in between leaves a slot with no entry, which the next load of a readable state removes. If the write
  fails, the in-memory state keeps the entry too.
- **Dedicated codes:** if what's removable still isn't enough, the call ends with `SLOTS_IN_USE` before
  anything is removed. `STORAGE` remains for cases where the file system itself fails, including a
  directory that couldn't be deleted. Both codes have their own text in the app.
- **Unified along the way:** the caption branch in `JobCoordinator.captions` now looks up the bound engine
  the same way `SttStep.pinnedEngine` does, and waits with `ENGINE_NOT_AVAILABLE` instead of throwing a
  nameless exception via `single`. `ENGINE_NOT_AVAILABLE` has its own text.

## Alternatives Rejected

- **Remove only loaded updates, never bundled engines:** old bundled engines would keep accumulating
  across app updates; the actual problem would remain.
- **A "delete engines" button:** the user can't see which engine an unfinished job still needs, and
  `ensureBundledLocked` also runs in the background, with nobody there to tap anything.
- **Accept a failure from `ensureBundledLocked` as long as the active engine is healthy:** that would
  break the guarantee, relied on at six call sites, that the bundled engine is always available as a
  fallback.
- **Also keep references in `state.json`:** a second source of truth alongside Room, one that can drift
  out of sync with it on abort and deletion.

## Consequences and Residual Risks

- An unfinished attempt holds on to its engine even during phases where it no longer runs it — a
  "missing only" attempt, for instance. This protects more than strictly necessary, never less.
- `stage` queries references separately before the download and again, for clearing, afterward. If a
  "missing only" attempt appeared in between and took over its predecessor's engine without asking the
  manager — and that engine was exactly the one slated for clearing — the update would end with
  `SLOTS_IN_USE` after the download, and nothing would be removed. Today, only the lock on
  `MainViewModel.action` prevents that; both the update and the retry go through it, and it applies per
  view model. Every other new attempt on a YouTube source asks the manager for the active engine and
  waits until `stage` releases it, or falls back to the engine of a caption attempt that still holds it
  at that moment, as its STT fallback.
- If `stage` fails after clearing — at the self-test, say — the removed installation stays removed.
- If `state.json` isn't readable, the state names no slot, and every existing directory counts as
  removable first, as long as no job is bound to it. This just anticipates what happens anyway:
  `ensureBundledLocked` rewrites the state in the process, and the next load clears slots with no entry.

## Tests

`EngineUpdateManagerTest` verifies ordering, protection, refusal without removal, a failing query, and
loading an update that's either rejected before the download or fails during it. `EngineReferencesTest`
verifies the Room query, `AppPipelineTest.aCaptionAttemptWhoseEngineIsGoneWaitsWithThatReason` the caption
branch, and `SttMissingRetryTest.aMissingChunkRetryOfAVideoCompletesWithoutTheEngineItIsBoundTo` that
"missing only" manages without its engine. Only the separate live test verifies the path all the way to a
successfully loaded update, because a valid signature belongs to a real release.

## Correction in Round 18

Through round 18, the manager additionally held on to the engine of a job's most recent STT attempt
whenever that job's result wasn't confirmed complete, and let it yield only as a last resort for the
bundled engine, never for an update. The reasoning — that "missing only" keeps running on that engine —
was wrong: `SttStep.prepareMissingRetry` creates the new attempt in `Phase.SUBMIT`, using the audio
segments its predecessor already prepared, and only `resolve` and `download` query the bound engine. The
new attempt carries its ID forward as a record of where the audio came from (ADR 0006), not as a file it
executes. The protection, meanwhile, made a voluntary update fail with `SLOTS_IN_USE`, whose message
demanded finishing or deleting the partial result. The round 18 invariant reviewer found this. Since
then, only an unfinished attempt holds on to its engine.
