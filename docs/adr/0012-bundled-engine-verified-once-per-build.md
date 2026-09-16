# ADR 0012 — The bundled engine is fully verified once per app build

Date: 14 September 2026. Status: Implemented for release 0.3.0.

## Context

Up to release 0.2.0, `EngineUpdateManager.ensureBundledLocked` kept the verified bundled engine only in memory. Every
new app process therefore repeated the whole check: it copied yt-dlp out of the APK into a working directory, verified
the signature of the checksum file and the archive, hashed the slot, and ran the runtime self-test, seven native probes
of Python, EJS, QuickJS, yt-dlp, FFmpeg and FFprobe. `MainViewModel` ran all of it inside its start-up action, together
with job recovery. That action set `busy` and held the action gate, so for as long as it ran, the language, provider and
key controls, checking a source, importing audio and starting a job were disabled (defect 57).

Measured on emulator-5556 (API 37), freshly restarted, with a temporary log line that was never committed, from process
start until that action ended: 15.9 s on the first start after installing the APK, 12.6 s of it with the screen locked;
6.6 s, 6.7 s and 6.8 s on the next three starts, locked for 4.8 s to 5.0 s. Recovery took 0.3 s at most; the engine
check took the rest. The same four starts on that emulator before its restart, after hours of use, took 26.7 s, 12.7 s,
11.4 s and 9.8 s.

A downloaded engine the user activated, in turn, was never checked again against the runtime of a newer app build
(defect 47).

## Decision

1. Once a full check of the bundled engine has passed, the state it produced is saved and the activated engine has been
   checked as in point 3, the manager writes `engines/bundled-check.json` in `noBackupFilesDir`. The marker holds the
   package's version code, its last update time, and the SHA-256 of the bundled engine resource.
2. A later start reads the same three values. If they match the marker, the state records this engine as healthy and
   bundled, and the file in its slot still hashes to the slot's name, the start uses the engine as it is: no copy, no
   signature check, no runtime self-test. In every other case the full check runs as before, including the repair of a
   damaged slot (ADR 0011) and the switch to a newly bundled engine (ADR 0010).
3. The full check also runs the runtime self-test with the active installation when that is not the engine this build
   bundles, typically a downloaded engine the user activated. If the runtime of this build does not confirm the versions
   recorded for it, the installation stops being healthy, and `active()` falls back to the bundled engine, as it does
   for every active installation that is not healthy. A probe that fails or times out confirms nothing and counts the
   same way.
4. A marker that cannot be written costs time and nothing else: the next start runs the full check again. So does a
   start whose check of the activated engine fails with anything other than a verdict on that engine.
5. Start-up work no longer holds the view model's action gate or sets `busy`. Recovery, the key list and the engine
   preparation run beside the screen. Every action that takes the gate waits for recovery first, so none of them runs
   before interrupted jobs are reconciled. Checking a source and preparing a YouTube job again wait for a preparation
   that is still running; meanwhile the new-source screen says so in a line whose height is always reserved. The engine
   settings keep the space of the first list entry while the list is still empty and say there that the engine is being
   prepared.

## Why the update-trust invariants still hold

- A later start uses exactly the bytes a full check verified. A slot is named by the SHA-256 that the signed checksum
  file confirmed, and every start still hashes the file against that name. A truncated, altered or replaced file fails
  the comparison and is replaced from the APK by the full check.
- The resource the marker names cannot change without a new APK. The platform installs an update only when it is signed
  with the app's key, and every install or update changes the last update time; an update also changes the version
  code. The resource hash ties the marker to the exact engine bytes on top of that.
- The marker, the state and the slots are app-private. Whatever can write them runs with the app's UID, and a process
  with that UID is no security boundary: it could as well replace the runtime, the state or the database. Skipping the
  signature check on a later start gives such a process nothing it did not have. What the signature protects against,
  bytes yt-dlp never signed entering a slot, is still checked for every engine that enters one.
- Nothing changes for downloaded engines: `stage` and `activate` verify the signature and run the probe as before. No
  network request, update path or trust anchor is added.
- The runtime self-test guards against a runtime and an engine that do not fit together. Both are fixed for one APK
  build, so the build is the unit to test once; an app update brings the test back, now for an activated engine too.

## Consequences

- Later starts no longer run the signature check and the runtime self-test. Measured with the same method on the same
  emulator session as the figures above, the first start after installing the APK had a ready engine after 11.5 s and
  never locked the screen; the next three starts had one after 1.8 s, 2.1 s and 1.7 s, between 0.06 s and 0.13 s after
  the view model was created. Settings, keys and text input are usable during the full check.
- Damage to the runtime files between two starts of the same build is no longer caught by a start-up probe.
  `NativeRuntime.initialize` still checks on first use that its files are there; damage beyond that shows up as a
  failed extraction, as damage after the start-up probe always did.
- The first start after an app update runs up to seven more probes when an engine other than the bundled one is active.
  An activated engine whose probe times out there, on a slow or busy device for example, is replaced by the bundled
  engine like one that fails. That happens without a message of its own, as for every active installation that is not
  healthy; the settings list still shows it.
- The engines directory holds one more file. Slot cleanup leaves it alone: its name is neither a hash nor a temporary
  name.

## Rejected alternatives

- **Keep the full check on every start and only move it off the screen:** the screen is free, but every start still
  spends seconds of CPU and battery on it, and the first check of a source waits for it.
- **Key the marker on the version code alone:** installing the same version again, which every debug build does, can
  change the runtime without changing the version code.
- **Trust the marker without hashing the slot:** a damaged engine would run until an extraction fails.

## Tests

- `BundledEngineMarkerTest` (JVM): only the version code, update time and resource hash a full check passed for skip
  it; each of them changed alone, and a missing, empty or truncated marker, bring the full check back.
- `EngineUpdateManagerTest.aLaterStartOfTheSameAppBuildSkipsTheSignatureAndRuntimeChecks`: a second manager with an
  unchanged marker calls neither the verifier nor the runtime self-test.
- `EngineUpdateManagerTest.aLaterStartStillHashesTheBundledEngineAndChecksADamagedOneFully`: a bundled engine one byte
  short is found on the later start, checked fully and repaired.
- `EngineUpdateManagerTest.anotherAppBuildChecksTheBundledEngineAgainAndTheActivatedOneAgainstItsRuntime`: a marker of
  the previous build brings the full check back, the activated engine is probed too, and its failure makes the bundled
  engine active; the start after that checks nothing.
- `ViewModelStateTest.startUpWorkLeavesTheSettingsAndKeysUsable`: while the start checks the engine, nothing holds the
  screen busy, and saving a key succeeds.

## Amended 2026-09-16 — a probe that only timed out concludes nothing

Status: implemented for release 0.4.0. Decision 3 above and the paragraph of "Consequences" that accepted a timed-out
probe as a failure are replaced by what follows; everything else in this document stands as written.

### What the review of 16 September found

`recheckActiveLocked` probed the activated engine through `verifyRuntimeCompatibility`, and that function had one
catch clause for every `NativeRuntimeException`, `RuntimeFailureCode.TIMED_OUT` included, which it turned into
`PROBE_FAILED`. The interactive `activate` path already told the two apart for its own yt-dlp probe: a timeout there
raises `UNCERTAIN_PROBE`, because a runtime that never answered has said nothing about the engine. The first start of
a new app build did not. So on a slow or busy device, one probe that ran into its timeout was enough to take an
engine the reader had chosen away from them for good: the installation stopped being healthy, `active()` fell back to
the bundled engine, and every unfinished attempt pinned to the old engine stopped with `ENGINE_NOT_AVAILABLE` -
`SttStep.pinnedEngine` accepts only a healthy installation. There was no retry and no message of its own.

### Decision

1. **A probe that answers is a verdict; a probe that times out is not.** `verifyRuntimeCompatibility` raises
   `UNCERTAIN_PROBE` for `TIMED_OUT` and `PROBE_FAILED` for every other `NativeRuntimeException`, which is the
   distinction `activate` already made. `REQUIRES_APP_UPDATE` for reported versions that do not match is unchanged.
2. **A verdict against the activated engine replaces it, as before.** The installation stops being healthy,
   `active()` falls back to the bundled engine, and `EngineInstallation.healthLoss` records which verdict it was:
   `RUNTIME_MISMATCH` when this build's runtime reported other versions for it, `PROBE_FAILED` when the runtime could
   not run it at all. The field is persisted in `engines/state.json` and cleared again the moment a check confirms
   the engine.
3. **An uncertain probe changes nothing.** The engine stays active and healthy, no reason is written, and
   `recheckActiveLocked` answers that it reached no verdict. `ensureBundledLocked` then writes **no** marker, so the
   next start runs the full check again and probes the engine once more. This extends decision 4 above: a start whose
   check of the activated engine reaches no verdict costs the next start its full check, and nothing else.
4. **The engine list in the settings says which installation is which.** Under every entry it now names whether the
   app is using it, whether it was checked, whether it came with the app, and, for a replaced one, which of the two
   verdicts it was. Until 0.4.0 all entries read alike, so a replaced engine looked exactly like the running one.
5. **An attempt still pinned to a replaced engine is the job actions dialog's business, not this manager's.** The
   dialog recommends a new run, which binds to `active()`; the manager never rebinds an existing attempt. See item 15
   of the 0.4.0 plan and `JobActions`.

### Why the update-trust invariants still hold

- Nothing is trusted that was not verified. An uncertain probe leaves the state exactly as it was: the engine was
  healthy because an earlier full check verified its signature and ran its self-test, and it stays healthy on that
  same evidence. No check is skipped - the opposite, the next start runs one more.
- A marker is still only ever written after a completed check. The new condition makes it harder to write, never
  easier: the marker now also requires that the activated engine got an answer.
- The new field is a label on an installation that is already not healthy. It grants nothing, and `active()`,
  `pinnedEngine` and `rollbackTargetLocked` keep reading `healthy` and `healthyIds` as before. A `healthLoss` value
  a state file does not name, or names with an unknown word, reads as null.

### Consequences

- A working engine on a slow device is no longer lost to one timeout. The cost is that such a device runs the full
  check on every start until a probe answers - seconds of CPU per start, which is what release 0.2.0 did on every
  start for the bundled engine as well.
- A runtime that hangs forever would mean a full check on every start forever. The probe is bounded by
  `NativeRuntime`'s own timeout, so each start pays that bound once and nothing waits indefinitely.
- `engines/state.json` gains one optional key per installation. A file an older build wrote reads unchanged, and an
  older build reading a newer file ignores the key, since `parseState` reads named keys only.

### Tests

- `EngineUpdateManagerTest.anActivatedEngineWhoseProbeOnlyTimesOutKeepsItsPlaceAndIsProbedAgainNextStart`: with a
  `probeRuntime` that raises `TIMED_OUT` for the activated engine, the engine stays active and healthy, no reason is
  recorded, and the following start verifies and probes both engines again.
- `EngineUpdateManagerTest.anActivatedEngineWhoseProbeFailsOutrightIsReplacedWithTheReasonRecorded`: with a
  `probeRuntime` that raises `START_FAILED`, the bundled engine becomes active, the installation carries
  `PROBE_FAILED`, and the start after it checks nothing.
- `EngineUpdateManagerTest.anotherAppBuildChecksTheBundledEngineAgainAndTheActivatedOneAgainstItsRuntime`, extended:
  a version the runtime does not confirm now also records `RUNTIME_MISMATCH`.
