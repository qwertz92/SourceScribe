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
