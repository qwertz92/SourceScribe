# Integration checks after the first Android end-to-end pass

This report is chronological: older FAIL/NOT_RUN notes are kept for traceability. The current combined status is in
the [September 8 preview report](2026-09-08-preview.md).

As of September 8, 2026. Ongoing log; not a full v1 sign-off. Raw logs live locally under
`.local-tools/build-reports/` and are not blanket-versioned because they contain run-time data. Earlier P0/P1
evidence is in the [build and P0 report](2026-09-07-build-and-p0.md).

## Environment

Java 17.0.20.1, AGP 9.4.0, Kotlin 2.4.20, Compose BOM 2026.08.00, compile/target SDK 37, min SDK 29. Gradle was
updated from 9.6.0 to 9.7.1 after lint flagged the newer version. Wrapper and distribution are pinned and checked
against the official Gradle checksums. The additionally needed `kotlin-reflect:2.4.0` JAR/POM were compared against
Maven Central's published SHA-1 files and added to strict Gradle dependency verification with locally computed
SHA-256 values; verification itself was never disabled.

Device: `emulator-5554`, Android 17/API 37, `sdk_gphone16k_x86_64`, 16KB pages. Fingerprint:
`google/sdk_gphone16k_x86_64/emu64xa16k:17/CP31.260618.005/15731206:user/dev-keys`. Not a physical ARM64 proof. One
build or test process runs at a time; Gradle uses one worker, 2GiB heap, and the project drive.

## Runs actually executed

| Run | Command/scope | Result |
|---|---|---|
| r45 build | `tools/build-local.sh :core:test :extractor:assembleDebugAndroidTest :app:assembleDebug :app:assembleDebugAndroidTest :extractor:lintDebug :app:lintDebug` | 93 JVM tests passed; three APKs built; extractor lint passed. App lint failed with 30 findings. |
| r45 Android extraction | Instrumentation: `ExtractionBoundaryTest`, `ExtractionChainTest`; explicit source `jNQXAC9IVRw` | 5 tests passed, 56.618s. Real caption, audio, and preparation chain included. |
| r45 full app run | Instrumentation of the app test APK | Aborted: a test-only DocumentsProvider without required MANAGE_DOCUMENTS protection crashed the process and caused import errors. No full pass. |
| r45 STT/storage subset | `HistoryDatabaseTest,MigrationTest,StorageBudgetTest,SttStepTest,SttRecoveryTest,SttHardeningTest` | 46 tests, 45 passed, 1 failure in the upload-disconnect fixture. No real provider API. |
| r46 JVM | `tools/build-local.sh :core:test` with Gradle 9.7.1 | Strict dependency verification refused the not-yet-recorded kotlin-reflect artifacts; added after checking source and checksums. |
| r47 JVM | `tools/build-local.sh :core:test` | 97 tests, 96 passed. The new TLS error matrix left a synthetic queued request at DISCONNECT_AT_START. |
| r48 JVM | `tools/build-local.sh :core:test :extractor:assembleDebugAndroidTest :extractor:lintDebug` | 97 JVM tests passed; extractor test APK and lint passed, build 10min 5s. |
| r48 Android extraction | Same explicit source and both test classes as r45 | 6 tests passed in 34.024s: four fixture counter-checks and two real extraction checks. |
| r49 | `tools/build-local.sh :core:test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:assembleRelease :app:lintRelease :extractor:lintRelease` | 98 JVM tests passed. App compile aborted after 4min 56s: an unnecessary safe-call on an already-known-non-null CaptionTrack; fixed. Remaining gates in this run not executed. |
| r50 | `tools/build-local.sh :app:assembleDebug :app:assembleRelease` | Debug and unsigned release APK built; release lint-vital passed. Build 15min 46s. Full lint and current instrumentation still open. |
| r51 | Same build as r49 | 103 JVM tests, three failed: the new timeout fixture used a network-interceptor timeout override OkHttp 5 forbids. App gates not reached. |
| r52 | `tools/build-local.sh :core:test` | After fixing only the fixture: 103 tests passed, no failures, no skips; 1min 24s. Covers TLS timeouts, redirect/authorization isolation, and cross-chunk speakers. |

### Device check of the r50 APK on September 8

Debug APK installed (157,172,786 bytes) successfully. The existing r38 history and caption artifact stayed readable
after start. At `font_scale=2.0` and landscape, the viewer and action dialog scrolled; the viewer footer stayed at
the same coordinates across two different scroll positions. The format picker showed further formats after
scrolling and kept its cancel button — a real ADB proof for these specific layout paths, not yet a full
TalkBack/large-transcript sign-off.

Raw evidence: `.local-tools/build-reports/ui-r50-200-landscape*.txt`, `ui-r50-200-actions-scrolled.txt`, and
`ui-r50-200-format-picker*.txt`. Font, rotation, and accessibility baselines were restored afterward from the
previously saved JSON and fully compared. Further changes to key handling and UI state after r50 are not yet
checked by this older APK run.

### Release check of r50

Unsigned APK: 147,238,008 bytes, SHA-256 `e25f93202244ff3f79859128e3df3f61029481c0f8126bc37f2602a978fb0aab`.
`tools/sign-release.sh` run with a purpose-made, later-removed test keystore: signing and `apksigner verify
--verbose` passed, as did `zipalign -c -P 16 4`. A repeat call on the same target was correctly rejected. This is a
tool test, not the personal release signature or a publish.

The release manifest lacks `debuggable`, `testOnly`, and the fixture providers; backup and cleartext traffic are
disabled. The DEX scan found neither AudioImport/ExportFixtureProvider nor UiFixtureTest nor MockWebServer. Twelve
direct ELF files and 512 ELF entries inside runtime ZIPs/WebP assets were statically checked. The ten original WebP
entries in the FFmpeg ZIPs need the bundled 16KB replacement libraries; the native runner puts their verified
directory first in `LD_LIBRARY_PATH`. All other checked entries have at least 16KB `PT_LOAD` alignment. The
already-run 16KB FFmpeg device tests remain the runtime proof; ELF headers alone do not replace them.

Raw evidence: `.local-tools/build-reports/release-r50-signing.txt`, `release-r50-manifest.txt`,
`release-r50-audit.json`, `release-r50-inner-native.txt`. Public APK distribution needs the FFmpeg build/source
package attribution resolved first (see the license report) — an open license task, not evaluated further here.

### Native warning in r50

AGP could not strip `libdatastore_shared_counter.so` and packaged it unmodified. Checking both APKs with an ELF
header parser and NDK-r28c `llvm-readelf -S` showed only 16KB `PT_LOAD` alignment and no `.debug` sections. ARM64:
10,360 bytes, SHA-256 `deed4546c8dafad0e68ea2c25e4c0a62ca97343614ae386b7ed2af6abb7fa999`; x86_64: 9,424 bytes,
`c3973140a0e6144a83e7dd7ee3c4e161f42181591feda254e12c8395d3bbacd0`. A manual `llvm-strip --strip-unneeded` on copies
worked, reducing them to 7,784 and 7,336 bytes, so the originals carry small symbol tables. This one library is now
configured for unmodified packaging like the other audited native artifacts; the few extra KiB are a deliberate
trade-off, and no general strip/lint check was disabled.

## Confirmed defects and fixes

- Caption URLs: check decoded query names and duplicate `v`/`lang`/`tlang`; bind effective language and stated
  video ID to the selected track.
- Download without re-resolving the source: a minimal `--load-info-json` recipe with no `webpage_url` and no second
  URL argument. The local ID marker proves the recipe ran, not independently the server content behind an opaque
  HLS URL.
- Orphaned leases, artifact finalization before the Room insert, quotas, and import previews: recovery and deletion
  now share lifecycle coordination.
- Already-stored starts and exports: scheduler errors are now recorded durably and separately, and no longer look
  like a job that was never created.
- Uncertain submission and AssemblyAI receipt: retained responses/handles stay bound; an abort or missing receipt no
  longer allows a blind resubmission.
- UI: reworked the 200%-landscape viewer; locked start configuration during takeover; cancel is independent of the
  general action gate; a pending share text survives rotation. Current device counter-check still open.
- The test DocumentsProvider is now protected with MANAGE_DOCUMENTS; the resulting URI-grant check stays `NOT_RUN`
  until the next Android run.
- Disconnect fixtures now split by the request actually read, and still check every path and the exact request
  count.

"Retry missing chunks only" and schema 3 are being integrated per
[ADR 0006](../adr/0006-missing-chunk-retries.md). New code paths are `IMPLEMENTED` only, until their tests pass.

## Open evidence

- `BLOCKED`: live STT per provider without approved credentials, audio files, and budget; no physical ARM64 device.
- Open license task: public APK distribution needs sufficient corresponding-source evidence for the concrete FFmpeg
  artifact; see the license report.
- `NOT_RUN`: a full current app test run, new schema-3/chunk-retry regressions, the full UI/TalkBack/lifecycle
  matrix, and the release check.

## Resumption and division of work

The usage limit also interrupted four agents; their file edits survived. The started STT implementation and
pipeline regressions were then handed, with fresh context, to two GPT-5.6-Sol agents, whose runtime models were
verified as `gpt-5.6-sol` in their `turn_context.model`. Independent reviews use Luna at max reasoning; architecture,
UI, and integration stay with the main agent. This is a division of labor, not a measured cost comparison.

### Resumption r53-r57 (September 8, local time)

The new app was actually built as a debug APK in r53 and r55; full sign-off stayed open each time. r53 failed full
app lint on three remaining findings: a redundant empty `super.onCleared()` call, a prefix flag invalid for
`revokeUriPermission` in test cleanup, and an unnecessary `-v26` resource qualifier given minSdk 29 — all three
fixed. r54 then failed on incremental AAPT state after a resource-directory change; only the affected generated
resource directories under `app/build/intermediates` were deleted. r55 processed the same source resources
successfully.

r55 first reached compiling the extended app instrumentation and found a wrongly qualified nested fixture type plus
two unsuitable suspended DAO function references; fixed using the real companion type and direct suspended calls in
loops. r56 then found three nullability warnings in test helpers; explicit `requireNotNull` checks make their
preconditions checkable. The strict compiler/lint rules stayed unchanged. Raw logs: `build-r53.log` through
`build-r57.log`; r57 was still running when logged, no test pass claimed yet.

The final independent Luna review of the bounded recovery area reported no new findings. Statically verified:
corrupted job configs are isolated with no default config; uniquely owned finalized artifacts can be deleted even
without a Room artifact row; cancellation during a suspended engine-binding check propagates; Room artifacts with
missing finalized files lead to `ARTIFACT_FILE_MISSING`. The new Android regression checks were still to be run. An
unreadable checkpoint is deliberately left untouched during file cleanup when no owner can be safely determined.

### Safe checkpoint r57 — September 8, 2026, about 01:56 CEST

**BUILD/LINT PASS**, command:

```text
timeout 1500 bash tools/build-local.sh :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:assembleRelease :app:lintRelease :extractor:lintRelease
```

r57 finished successfully after 11m38s, 219 tasks (71 executed, 148 up to date). The three full lint XML reports for
app debug/release and extractor release each show **0 issues**. Kotlin/Java test compilation passed. The known
external AGP analytics message from a read-only user path stays documented separately; no global write access set
up. No new native-strip warning in the r57 log.

| APK | Bytes | SHA-256 |
|---|---:|---|
| `app/build/outputs/apk/debug/app-debug.apk` | 157943686 | `bb8cfabbcafd38a14fd56c8885796b9c0ebae7ad0da6bf9f6d78b2b5bc53d70f` |
| `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` | 2301475 | `6113217f7c76fd8d6fde7bcec9626dbab5f549d1da8c0bdf6a1e92e03fa7078b` |
| `app/build/outputs/apk/release/app-release-unsigned.apk` | 147294844 | `e0b05e8b8a6368484ecb7349be6c9b5bc7c6a5351a2b21529b5c02cdab0846cc` |

App debug and the current app test APK were each installed successfully with `adb -s emulator-5554 install -r`
(`install-app-r57.txt`, `install-app-test-r57.txt`). The new Android full regression is **NOT_RUN**: the user
ordered the work pause before it started. The last completed core run stays r52 with 103 tests. CI stays
**NOT_RUN**; the final pause commit uses `[skip ci]` specifically to start no new work after the pause — not a
passed or superseded CI check.

**UI evidence, bounded:** system/light/dark is implemented; dark mode was visually checked in r53 and reset to
system. TalkBack 17.0.0.889642762 was actually bound and spoke audibly (confirmed by the user too). Green focus
rings seen on dialog titles and navigation; full traversal/activation and speech quality stay **NOT_RUN** — no full
accessibility sign-off. ADB injection and UI Automator dumps were not yet a reliable end-to-end method for this. The
current [official keymap reference](https://support.google.com/accessibility/android/answer/6110948?hl=en)
distinguishes default and enhanced keymaps.

Restored and read before the pause: `font_scale=1.0`, `user_rotation=0`, `accelerometer_rotation=1`,
`enabled_accessibility_services=null`, `accessibility_enabled=0`; TalkBack `POST_NOTIFICATIONS` regranted as
`false` with only the original `USER_SENSITIVE_WHEN_GRANTED` flag. `dumpsys accessibility` confirms
`touchExplorationEnabled=false`, empty bound/enabled service lists. SourceScribe debug and both test packages were
force-stopped in a controlled way; no provider jobs started. Local evidence: `pause-r57-evidence.json`,
`ui-r53-final-settings-restored.json`, `pause-accessibility.txt`.

**Open review notes:** the AudioImport fixture ignores a SIZE-only projection, so the importer reads the wrong
column (statically confirmed, new Android counter-check open). A claimed export RowBuilder overflow is unconfirmed
so far, since the named-column overload is actually used — verify before changing. An OUTPUT_FAILURE pipe race is
only a hypothesis. These incoming findings, plus new user feedback (field/button spacing, German/English app
language, removing the separate submission toggle), are recorded in HANDOFF for the resumption, not silently
implemented during the pause.

## r58/r59 — resumption, instrumentation, and UI feedback (September 8)

Starting point: pushed pause state `a4862a0`, installed r57 app and test APK. ADB still `emulator-5554`, Android
17/API 37, x86_64, 16KB pages. No real provider credentials used, no deliberate audio output enabled.

- `am instrument -w -e class app.sourcescribe.data.AudioImportTest,app.sourcescribe.data.ExportStoreTest
  app.sourcescribe.debug.test/androidx.test.runner.AndroidJUnitRunner`: 22 tests, 24 failures including teardown.
  The nine audio-import tests passed. The export fixture already rejects the control call with
  `MANAGE_DOCUMENTS`/`ACTION_OPEN_DOCUMENT` — not yet proof of a product export bug. Raw log:
  `android-import-export-r58.txt`.
- `am instrument -w -e class app.sourcescribe.data.AppPipelineTest,app.sourcescribe.data.SttMissingRetryTest,
  app.sourcescribe.data.SttRecoveryTest,app.sourcescribe.data.ParallelJobsTest,app.sourcescribe.data.MigrationTest
  app.sourcescribe.debug.test/androidx.test.runner.AndroidJUnitRunner`: 91 tests, one failure, 28.621s. Affected:
  `retryMissingOnlyUsesLatestFallbackSttAttemptInsteadOfOlderSuccess`, runtime probe `MISSING_BINARY`. 90 tests
  passed, including 14 missing-retry, 34 recovery, parallelism, and migration tests. Raw log:
  `android-recovery-r58.txt`.
- The wrong audio-fixture projection additionally found in review is its own test-infrastructure bug, not a failed
  audio-import test in r58.
- AppCompat 1.8.0 checked against the official AndroidX release page and Google Maven metadata; German/English via
  Android app languages, no second language store. Decision: ADR 0007. Build/language-switch/screenshots after this
  change still NOT_RUN pending actual results below.
- Read the Chris Banes skill `compose-ui-testing-patterns` as a targeted check method, not a global skill/config
  install. The broader Google Android testing setup would add frameworks unnecessarily and was not adopted.

### r59 — real process boundary, controlled provider

Two separate instrumentation processes, with `adb shell am force-stop app.sourcescribe.debug` between them. Opt-in:
`sourcescribeProcessFixture=true`, `sourcescribeProcessStage=1` and `2`; test methods
`ProcessRecoveryTest#stage1PersistsAcceptedAssemblyRemote` and `#stage2ReopensAndFinalizesWithoutResubmission`.

Both passed (1.557s / 1.610s). PID 21609 → 21705. Stage 1: two local TLS fixture requests (upload plus exactly one
submission), one durably stored remote ID. Stage 2: exactly one GET, zero new cost-relevant POSTs, an unchanged
submission, a durable result. This is real Android process evidence, `TESTED_WITH_FIXTURES` for the provider, not an
AssemblyAI live test. Raw data: `android-process-stage1-r59.txt`, `android-process-stage2-r59.txt`,
`android-process-evidence-r59.txt`.

Further app run: `SttHardeningTest,SttStepTest,BatchCreationTest,ViewModelStateTest,SourceFilesTest,
StorageBudgetTest,CredentialStoreTest,DiagnosticsTest,DatabaseTest,HistoryDatabaseTest`: 51 tests, five failures,
all in `ViewModelStateTest` at `ENGINE_PROBE_FAILED` before the actual test action. 46 tests passed. Raw log:
`android-app-rest-r59.txt`.

The runtime cause was narrowed to the process-wide youtubedl-android singleton and diverging isolated fixture
directories. Test fixtures now initialize in the real target context and only symlink this runtime tree; the link
is explicitly removed before recursive fixture cleanup. No assertions weakened. Device regression for this fix still
pending.

Model routing confirmed from actual `turn_context` entries: provider/runtime fixture work on GPT-5.6 Sol/high,
independent start-authorization review on GPT-5.6 Luna/max. Root/Astra implements UI and integrates.

### r59/r60 — UI built, regression, and confirmed export bug

r59: debug app and AndroidTest APK BUILD SUCCESSFUL (17m55s, 100 tasks). AppCompat dependency enrollment checked
separately: all previous SHA-256 values unchanged; all 17 newly added artifacts independently fetched from Google
Maven and matched byte-for-byte (`dependency-delta-r59.json`); six AppCompat files including POMs additionally in
`appcompat-official-r59.json`.

App and test APK installed. Actually operated: Settings → App language → German; Android LocaleManager reports
`[de]`. Display → Dark works. After force-stop and reopening, German and dark persist. Screenshots reviewed:
`ui-before-r59.png`, `ui-new-r59.png`, `ui-settings-en-r59.png`, `ui-language-dialog-r59.png`,
`ui-settings-de-dark-r59.png`. The old empty second field row and touching primary buttons are fixed. A following
polish pass adds icon-based navigation instead of step numbers, better text-on-teal contrast, and compact
confirmation dialogs; that follow-up still needs its own device screenshot.

Device regression r59: 37 tests, ten failures, all in `ExportStoreTest`. `AudioImportTest` 10, `ViewRulesTest` 5,
`ViewModelStateTest` 6, and the previously broken AppPipeline retry method all passed — the native fixture cause was
thus actually confirmed by retest. The export fixture also needed `isChildDocument`; a Java return type changed
during partial compilation caused an ABI error in some cases, so the next full build must use a frozen source state.

Separately confirmed product defect: `DocumentFile.isDirectory()` swallows `SecurityException` (AndroidX
`DocumentsContractApi19.queryForString`, `catch(Exception)` with a default value), yielding FAILED instead of
PERMISSION_REQUIRED on revoked access. `ExportStore` now uses direct DocumentsContract/resolver calls so the real
error survives; written files with an impossible readback are flagged UNVERIFIED. Prior independent regressions
stay unchanged; a new counter-check for a denied readback and an actually revoked tree grant was added.

r60: full build internally aborted after 7m01s at `:app:lintAnalyzeDebug`:
`AsyncExecutionService.getService must not return null`, PSI rebuild of `ExportStore.kt`, which was being edited
during analysis — not a lint pass and no suppression. Repeated with a frozen build source state.

Luna/max review of the start authorization: no confirmed defect in the bounded diff/call-chain review (static only,
no reviewer provider/device test). `ViewRulesTest` adds the actually passed Android regression for all four modes
and exact credential/provider/region binding.

### r61 — emulator restart and frozen integration run

The already-built r59 app additionally passed the two ProcessRecovery stages over a real `adb reboot`: stage 1
1.112s, stage 2 8.153s, one test each. After boot, Android reported `sys.boot_completed=1`, uptime 63.31s, boot ID
`11624e3a-4b9d-49a8-bf53-767f43787f0b`. Stage 2 confirms a different process, exactly one local TLS GET, zero new
submission POSTs, the same submission, a durable artifact. Provider still `TESTED_WITH_FIXTURES`. Raw data:
`android-reboot-stage1-r61.txt`, `android-reboot-stage2-r61.txt`, `android-reboot-r61.json`,
`android-reboot-process-evidence-r61.txt`.

The r61 full run uses a frozen set of 144 source/build files (`source-before-r61.json`). The built debug APKs were
checked on the emulator in parallel with the following release/lint check; no full pass before all gates finish. The
viewer seed still failed with the r59 test APK before execution: Kotlin inferred an Int return from the final
`Log.i`, but JUnit requires void; fixed separately and rerun — the rest of the app run alone does not replace this
open item.

GitHub Actions for `22df8a7` (run 34229703342) failed on four missing dependency-metadata hashes in a cold cache.
The relevant Maven Central files were independently matched against published SHA-256 values or byte-identical
responses from both official endpoints (`ci-metadata-candidates-r61.json`). The addition happens only after the
frozen build ends, with no weakening of dependency verification.

### r61/r62 — export closed, UI, and real update binding

r61 ended at the internal 1200s timeout during `:app:lintAnalyzeDebug`. The SHA-256 set of all 144 build/source
files was identical before and after; nothing changed during the run, and no build Java process remained afterward.
Debug app and test APK were already built/installed before the lint timeout. Instrumentation: runner reported 177
cases, 174 run, 172 passed, two export-fixture failures. EngineJobPinning (1) and ProcessRecovery (2) were opt-in
and not run in this pass; UiFixture was separately excluded for a known invalid signature — not a full app pass.

The two write fixtures were fixed: a small write fit entirely into the existing pipe before its reader closed,
producing a false MISMATCH; `ProxyFileDescriptor` now returns an accepted byte-write first, then EIO. ENOSPC had
been missing a valid `onGetSize`, whose AOSP default throws EBADF and can block FUSE LOOKUP/GETATTR before the
write. Both callbacks now implement size/fsync and keep their handler thread until `onRelease`. Assertions require
the write callbacks to actually run; existing error/cleanup/result assertions were not weakened.

r62 `:app:assembleDebug :app:assembleDebugAndroidTest`: BUILD SUCCESSFUL, 3m59s, 100 tasks (14 executed); again 144
identical source hashes before/after. Warning: several Kotlin daemon sessions from earlier aborted runs, no parallel
Java build found. No compiler rule disabled. `ExportStoreTest`: **17/17 passed**, 3.261s, same API-37/16KB device.
EIO/ENOSPC genuinely occur in proxy write callbacks — a `TESTED_WITH_FIXTURES` SAF counter-check, not a physically
full device. Independent Luna/max close review: no confirmed false-green, permission, or cleanup data-loss finding
in the bounded export diff (static, no reviewer device calls). Raw log: `android-export-r62.txt`.

`EngineJobPinningTest`, opt-in with `sourcescribeEngineJobPinning=true`,
`engineProbeSource=https://www.youtube.com/watch?v=jNQXAC9IVRw`, `engineUpdateChannel=NIGHTLY`: **1/1 passed**,
72.267s. Two claimed jobs keep engine 2026.08.19, SHA-256
`1fa6733c37ea6fb51c99ad8fe785e7b7e5f3246c9b980230329d4fb72ed8d4d6`. The real signed activation uses
2026.08.30.232658, SHA-256 `3f1b267b4488f3aed3731a9e84a44011ca5569901868532e10ee11fd07d69707`; EJS 0.8.0 for both.
The old engine was then actually run for its version query. New jobs get the new engine. Limits:
`job.execution=NOT_RUN`, no proof of two downloads during the update, zero provider requests. Raw log:
`android-engine-pinning-r62.txt` (installed r61 APK).

ADB/UI r61: a real preview of the explicit source `jNQXAC9IVRw` in YouTube-only mode, then de→en and dark→light:
input, mode, title, and caption preview preserved. Screenshots reviewed: `ui-new-de-dark-r61.png`,
`ui-settings-en-light-r61.png`, `ui-settings-200-r61.png`, `ui-language-200-r61.png`,
`ui-language-landscape-200-r61.png`. No paid submission. At 200% font, navigation was initially clipped sideways;
r62 allows two-line labels, whose screenshot still showed uneven icon heights — matching reserved label height is
the following, still-untested fix.

TalkBack 17.0.0.889642762 was announced beforehand, briefly activated, and the values
`enabled_accessibility_services=null`, `accessibility_enabled=0` were verifiably restored afterward
(`talkback-baseline-r62.json`, `talkback-restored-r62.json`). A focus ring is visible. The injected taps, however,
opened a different item than the focused one; reliable TalkBack traversal/activation stays **NOT_RUN/NOT_PROVEN**,
no pass. Gesture sequences follow
[Google's gesture reference](https://support.google.com/accessibility/android/answer/6151827?hl=en).

UiFixture r62: signature fixed, then an error from assuming a new source row must get SQLite row ID 1 — only true
on an empty DB; corrected to the real Room insert contract (not -1). Existing user data is preserved. Seed/viewer
device regression still open. The CI metadata fix is separately committed and pushed as `0883b9e`.

T15, ADB with installed r62 app: the same real `ACTION_SEND` intent twice (`text/plain`, `EXTRA_TEXT` with
`jNQXAC9IVRw`, SingleTop). The UI shows exactly this input. Before/after, in a stopped app process, a consistently
copied Room DB with WAL: one source, one job, one attempt, one artifact, zero submissions each time. **PASS: no
automatic extra execution from duplicate share intents.** Raw evidence: `android-double-share-r62.json`,
`ui-double-share-r62.txt`; temporary DB copies removed afterward. No provider live test and no request counter for a
paid provider from this single UI check.

The export fix was committed and pushed as `812c4dc` after close review. The next cold CI run then reached app
configuration and reported exactly one more missing BOM metadata hash (`kotlinx-serialization-bom:1.7.3`); its
official POM matches the published SHA-256. Added only after the frozen build r63 ends.

## r63/r64 — full regression, update limits, and lint

Source states were compared via SHA-256 before/after every build: 144 build input files unchanged each time. r63:
`:app:assembleDebug :app:assembleDebugAndroidTest :extractor:assembleDebugAndroidTest`, BUILD SUCCESSFUL after
7m06s, 132 tasks (52 executed, 80 up to date). APKs actually installed; no parallel Gradle processes, no repeat
Kotlin-daemon warning in this run.

- App runner: 181 reported cases, 177 run and passed, four opt-in checks not run. 75.206s. Includes all 43 pipeline
  and 17 export tests. T05 adds the real coordinator chain: four 429 cycles up to WAITING_USER, no unintended STT
  fallback on broken captions, and exactly one fallback attempt when the option is explicitly enabled.
- EngineUpdateManager: 17 runner cases = 16 passed fixtures plus one skipped opt-in live test; 83.710s. New cases
  check offline I/O, HTTP 429 with backoff persisted across a manager restart, an injected storage error with
  cleanup and the active slot preserved, and private/offline activation probes. `TESTED_WITH_FIXTURES`, no
  physically full filesystem.
- Separate real update test: 1 pass, 70.939s. Signed nightly 2026.08.30.232658/EJS 0.8.0, SHA-256 `3f1b267b`
  (full hash in the local runner log), a real public activation probe, and rollback to bundled 2026.08.19.
  `LIVE_VERIFIED` on x86_64/API 37/16KB; no ARM64 proof. Independent update-limits review: no confirmed further
  production/security defect.
- `UiFixtureTest`: 1 pass, 0.985s. SQLite insert result correctly checked against `!= -1L` instead of an assumed
  fixed row ID. An explicitly synthetic 10,000-segment artifact was created in the app DB; the existing real caption
  is preserved. A real UI search for SUCHMARKE finds segment 9999 and hides segment 0. No provider/accuracy claim
  from this UI seed.
- r64 lint: FAILED after 4m26s with four concrete findings. Three outdated configuration width/height queries
  replaced with `LocalWindowInfo`/`LocalDensity`. `localeConfig` deliberately applies only from API 33; the
  existing AppCompat `autoStoreLocales` path covers API 29-32. Documented `tools:targetApi="33"` on the manifest, no
  global rule downgrade. Repeat check r65 in progress.

Local raw logs: `android-app-r63.txt`, `android-update-r63.txt`, `android-live-update-r63.txt`,
`android-ui-seed-r63.txt`, `build-r63.log`, `build-r64.log`, `source-before/after-r63.json`,
`source-before/after-r64.json` under `.local-tools/build-reports/`. Screenshots: `ui-viewer-r63.png`,
`ui-viewer-search-r63.png`.

## r65/r66 — import limits, small display, and final lint fix

r65 stopped after 1m39s at compile: the `LocalConfiguration` import, still needed for the locale query, had been
removed while switching the sizing calculation; restored, no pass. r66 built the debug app and AndroidTest APK, both
installed; the full task aborted after 8m43s on a lint finding in the new test: `UseKtx` on `Uri.parse`, fixed with
the existing `String.toUri`. r64's three UI-size findings and `localeConfig` did not reappear. Source freeze
before/after r65 and r66: 144 identical build-input hashes each time.

T33 actually run on API 37/x86_64/16KB:

- `ProcessRecoveryTest#stage1PersistsRevokedLocalAudioImport`, args
  `sourcescribeProcessFixture=true`/`sourcescribeProcessStage=import-1`: 1 pass, 1.780s; PID 15513. Checked: a real
  grant-protected DocumentsProvider content URI, NativeRuntime audio probe, persisted Room row, and internal WAV
  copy. Afterward the Android URI grant was explicitly revoked and the external fixture deleted synchronously; the
  provider checks every deletion and throws on failure, and evidence is only written durably after a successful
  reset.
- `am force-stop app.sourcescribe.debug`, then
  `ProcessRecoveryTest#stage2ReopensPrivateAudioAfterGrantAndSourceLoss`, stage `import-2`: 1 pass, 0.263s, new PID
  15631. Grant still DENIED; Room row, private file, SHA-256, bytes, and metadata unchanged. Stage 2 only uses Room,
  the file, and a local OS grant check — no ContentResolver open and no STT adapter. This is real process/grant
  evidence with synthetic WAV bytes, not a provider transcription proof. The successfully checked test root was
  deleted.
- Independent Luna review: an initially claimed deletion-evidence bug, after checking `ExportFixtureProvider` around
  the relevant lines, was explicitly withdrawn — no confirmed defect. The internal evidence name `grantAfterImport`
  was later renamed to `grantAfterRevocation` for clarity, with no behavior change.

T26 r66: 1 FAIL after 65.685s, `native_runtime:PROBE_FAILED:exit=2` on the test wrapper's extra yt-dlp
version-probe call. The error text carries no stderr diagnosis, so an exact argv0 cause is not proven. The new
wrapper declares the previously really-measured version like the existing script fixtures. Both real old
runtime/hash probes before/after activation are preserved. Wrapper yt-dlp and EJS are explicitly marked
`DECLARED_FROM_REAL_OLD_PROBE` in the result. Two running caption jobs stay `TESTED_WITH_FIXTURES`; the
signature/activation probe and real slots are separately evidenced. Rerun needed in r67. An independent review found
no confirmed pinning/runtime violation. A generic probe label was narrowed from `PUBLIC_YOUTUBE` to
`YOUTUBE_SOURCE_ARGUMENT`, since an unlisted URL can be reachable too.

UI device checks:

- `System` genuinely follows Android's night mode: screenshots `ui-system-dark-r63.png` and
  `ui-system-light-r63.png`; night mode reset to `no` afterward. German language and SYSTEM theme preserved.
- Large synthetic artifact: the copy picker offers 11 sections; the format picker offers exactly `.txt`, `.md`,
  `.json`, no SRT/VTT/RAW without timing/raw data. A local check step initially wrongly searched for uppercase
  `JSON` in the German label; the corrected check on actual file extensions PASSED.
- 320×600dp (WM 960×1800, density 480), 200% font: real clipping of acquisition mode/model and awkward navigation
  wrapping, documented in `ui-small-fields-200-r66.png`. Root/Astra now uses the native TextMeasurer with real
  width/font; the tallest option's height is reserved so full selection values show with no jump on option change.
  Navigation gets somewhat more label width. Repeat check in r67 open.
- The copy hint was shortened in German/English; the actions dialog now has a max height with internal scroll
  instead of a flat 80% empty area. Window size/font_scale restored after the probe.
- TalkBack r66 announced beforehand. `dumpsys` confirms a bound service and `touchExplorationEnabled=true`; ADB key
  combinations partly reached Android system shortcuts instead, so there is still NO TalkBack operation pass.
  Service turned back off; original accessibility values and TalkBack `POST_NOTIFICATIONS` flags exactly restored.
  A new, opt-in-only Android test using real touch injection with
  `FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES` is still NOT_RUN.

CI `a10b105` stopped at `guava-parent-33.6.0-jre.pom`. A bounded scan of project-local cache BOM/parent POMs found
only `coroutines-bom-1.8.1` additionally missing a verification entry. Both were checked byte-for-byte against Maven
Central and its published SHA-256 and added to metadata; no binary hashes removed or verification loosened. Raw
evidence: `ci-metadata-candidates-r66.json`.

T21/T22 additionally r66: a real 10,000-segment share created a 912,857-byte UTF-8 Markdown file, SHA-256
`57535b2781782b4b66ba13e70d4f3ab8c753d5c870f769a6f4b09831f55b484a`. First/last segment and SUCHMARKE checked in the
file content. Android chooser showed the file, no recipient chosen/nothing sent. `ui-share-large-r66.json`,
`ui-share-large-r66.png`, and the synthetic `.md` are local only; no OOM/ANR during the flow. The repository gate
including its self-test also passed during r67.

## r68/r69 — full local build and privacy/import evidence

r67 failed after 3min 55s on the non-public SDK call `UiAutomation.destroy` in the new test design; removed, since
instrumentation manages its own `UiAutomation` lifecycle. Not a production bug, not a test pass.
r68: **BUILD SUCCESSFUL, 16min 43s, 257 tasks (115 executed, 142 up to date)**. Debug app, AndroidTest APK, and
unsigned release APK built; app and app test installed. `:core:test` up to date; app/extractor debug/release lint:
**four XML reports, each 0 issues**. All 145 source/build file hashes identical before/after. Known host warning
persists: AGP metrics init cannot write `/root/.android/analytics.settings` in the sandbox context. No lint rule,
dependency verification, or security check weakened.

T23: Android run with AppPipeline WorkData, diagnostics, and CredentialStore: **14/14 PASS, 6.435s**. WorkManager
2.11.2 does not publish `WorkInfo.inputData`; the internal WorkSpec DAO, used only in androidTest, reads the
actually stored acquisition/export inputs, both consisting exactly of `attemptId`. A separately captured logcat
excerpt for the app/test UIDs during the run: 199 lines, 14 canary strings, zero hits — scoped to this test slice,
not a universal leak exclusion. Independent Luna review confirms the concrete assertions, no further reproducible
defect. Raw evidence: `android-sensitive-data-r68.txt`, `android-canary-logcat-r68.json/.txt`.

T33 re-passed on the current r68 APK: import with a revoked grant and an externally deleted test file (1.811s), an
explicit force-stop, reopening the internal copy in the new process (0.229s). Two separate runner calls with
`sourcescribeProcessStage=import-1/import-2`. `TESTED_WITH_FIXTURES` for the synthetic audio file,
`LIVE_VERIFIED` for the Android grant/process boundary. No provider submission. Raw evidence:
`android-import-1-r68.txt`, `android-import-2-r68.txt`.

T26 stayed open: r68 reached the version-probe step, failed after 96.205s at a 30s wait for two caption barrier
arrivals. The prior test logged no attempt end states, so a cause is not proven. Persisted app parallelism on device
was actually 2. r69 adds only synthetic state/phase/error diagnostics at the timeout, no timeout increase or
loosening.

UI: 320×600dp and 200% font now shows complete, wrapped selection values after text measurement; previous ellipses
removed. "Optionen" still wrapped awkwardly before the last character, so navigation now uses the short labels
"Mehr"/"More". An Android UI dump confirmed the independent review finding: the app-language selector was clickable
but reported as `android.view.View`. Fixed in r69: explicit `Role.Button` and an Android semantic regression for
label/value, button class, and real dialog activation.

The earlier TalkBack gesture design using `UiAutomation.injectInputEvent` is not adopted: AOSP shows injection
bypasses the InputFilter/TouchExplorer chain
([InputDispatcher](https://android.googlesource.com/platform/frameworks/native/+/refs/heads/android16-release/services/inputflinger/dispatcher/InputDispatcher.cpp),
[InputManagerService](https://android.googlesource.com/platform/frameworks/base/+/android16-qpr2-release/services/core/java/com/android/server/input/InputManagerService.java),
both independently read). TalkBack was announced before activation; the second attempt explicitly waited for
`touchExplorationEnabled=true`. Emulator-console keyboard events did not reliably move the green focus: no
operation pass. Accessibility values restored. The actually pre-existing notification grant this time
(`true`, `USER_SET|USER_SENSITIVE_WHEN_GRANTED`) was, after an initial wrong revoke, reset exactly to the measured
state; the old r66 baseline does not count as the current user setting. Computer-use fallback failed to initialize:
`sandboxCwd is not a local file URI: file:///mnt/c/users/thoma/mystuff/personal/projects/sourcescribe` — no Windows
security/config change made as a workaround.

CI: the additionally requested parent/BOM POMs `guava-parent-33.6.0-jre` and `kotlinx-coroutines-bom-1.8.1` were
matched against the Maven Central file, published SHA-256, and local cache bytes, and pushed separately as `f31fa6f`
— no checksums removed. The new CI run is not yet a confirmed full pass.

### r69/r70 — proven test causes instead of raising timeouts

r69: BUILD SUCCESSFUL in 9min 50s, 216 tasks (54 executed, 162 up to date), 145 unchanged source/build hashes.
Current app installed; debug and release lint each 0 issues. r70 changes only the engine test file: AndroidTest APK
and debug lint passed after 3min 33s (111 tasks, 10 executed), hash set unchanged.

The r69 Android full run has 184 runner cases: 178 PASS, 1 FAIL, 5 opt-in skips; 96.284s. The new choice test
checked activation before initialization finished; the field does become active on-device afterward. Its
expectation of the Android class hierarchy was also wrong: Compose exposes the button role as an extra child of the
same clickable control when semantic text children are present — the same structure is visible on an unmodified
Material3 button. Before the production role fix, the choice field lacked that button-role child; afterward it has
one. The regression now checks activation after Ready, label/value, the button role inside the control, and real
dialog opening — no production change made merely to satisfy a wrong framework assumption. `ui-settings-role-r69.xml`
captures both structures. Repeat of this regression needed in r71.

T26 r69: both attempts reached FETCH_CAPTIONS, then paused at NATIVE with no barrier arrival. Android's
`JSONObject.quote` escapes `/` as `\/`, and the test put the resulting JSON string directly into Python code; Python
kept that backslash, making the file path wrong/relative. The actual Android Dalvik probe confirms this exact JSON
output; JSON-decoding it again yields the correct absolute path (`android-json-python-quote-r69.json`). The
`SyntaxWarning` from interpreting the deliberately broken old value is part of this reproduction, not an ignored
production warning. The test now explicitly decodes the existing JSON string with `json.loads`. The production
runtime already used its own matching Python string encoder and was unaffected by this test bug.

The improved share file name was checked on the actual r69 UI (the installed production app r68 with the same
share function): source ID, artifact ID, `model-unknown`, language `de`, and `.md` all present. 912,857 bytes,
SHA-256 unchanged `57535b2781782b4b66ba13e70d4f3ab8c753d5c870f769a6f4b09831f55b484a`. Android chooser opened and
closed with no recipient chosen. `ui-share-filename-r69.json`, `ui-share-filename-r69.png`.

The emulator console confirms keyboard sends with OK, but a concurrently running, time-bounded kernel `getevent`
capture sees no matching events (`console-hardware-events-r69.txt`). Together with the non-starting Windows
computer-use tool, full TalkBack traversal/activation stays **BLOCKED** in this environment — no fabricated
accessibility pass. The separate Android semantic test cannot substitute for this gap.

## r70/r71 — closed regression and current APK evidence

T26 r70: **1 PASS, 81.72s**. After fixing the actually-reproduced Android JSON/Python test path, two different
native processes reach their barrier points within running coordinator jobs. While both jobs hold their
leases/old installation, the real signed nightly is activated. Both synthetic caption jobs finish on the previously
pinned engine; a new job binds the new installation. Real old runtime/file hashes stay identical before/after
activation. Separately labeled raw results: `job.execution=TESTED_WITH_FIXTURES`, `job.finished=2`,
`real.updateVerification=SIGNED_HASH_RUNTIME`. Independent final Luna review of test code and raw log: no new
reproducible finding. Wrapper metadata is declared, not a claim of two real YouTube caption downloads. Raw log:
`android-engine-job-pinning-r70.txt`.

r71: **BUILD SUCCESSFUL, 4min 23s, 111 tasks (10 executed)**. AndroidTest APK and app debug lint; 145 source/build
hashes identical before/after. The full app run, with no new UI fixture creation, finishes after 82.854s: **179
PASS, 5 opt-in skips, no failures**. Runner "OK (184 tests)" includes the skips. All new UI, T05, export, and
privacy regressions are included. The latest production app r69 stays unchanged; only test code was fixed.

Current release check r71: 18 backup/transfer exclusions; backup/cleartext-HTTP/debuggable/testOnly each off; 22
AndroidTest descriptors checked against all DEX files, none found. 528 inner/outer ELF files checked; all ten known
WebP replacement files present with matching 16KB alignment, no unresolved ABI/alignment mismatch — physical ARM64
still not proven by this. The current optimized backup resource is named `res/4j.xml` in the APK; the path was
determined from the actually packaged resource table.

Final viewer counter-check: 200% font in landscape initially shows title/search. The initial visual suspicion that
content was unreachable was disproven by actual scrolling: segment 0 fully visible, Actions/Back reachable — no
production change for an unreproduced bug. `ui-viewer-landscape-200-segments-r71.png/.txt`; fresh font/rotation
baseline exactly restored each time. Actually tapping "Copy section 1 of 11" closes the section picker and returns
to the viewer; no separate system-wide clipboard-content proof is drawn from this.

Current APK bytes/hashes, full build/test commands, versions, and limits are consolidated in the
[preview report](2026-09-08-preview.md). The old failed runs were not deleted or retroactively relabeled as PASS.

## r72-r78 — real OS counter-checks and a targeted history fix

r73 passed the additional real network/display check: a caption-only job created offline (flight mode/WLAN/mobile
data genuinely off, no default network), screen off, network restored. The same WorkManager job finished with a new
internal caption artifact, zero STT submissions; Android was Asleep at completion. Network state identical
before/after. The first r72 attempt had wrongly expected only Asleep instead of first Dozing and was kept as FAIL —
not a production bug. Details in the preview report.

The real history view still showed "Groq · whisper-large-v3" from the unused configuration for this pure caption
job. Root first fixed CAPTIONS_ONLY. Build r74: PASS, 10min 21s, 216 tasks (31 executed), 145 unchanged
source/build hashes, app lint debug/release passed. This intermediate state was not signed off as a complete fix:
independent Luna review reproduced the same unlabeled default on a successful CAPTIONS_THEN_STT job, plus a visible
AssemblyAI delete action with no remote handle.

The full fix explicitly labels STT-capable configurations "STT configuration"; pure caption jobs show no provider.
The remote-delete action now only gets authorized job IDs from a Room query (AssemblyAI, handle present, not
REMOTE_DELETED) and still requires a terminal job state. Remote IDs and keys never reach this UI state; the backend
recheck stays unchanged — no DB migration, provider request, or extra consent from this display change. Sol added a
real Room regression test for missing/wrong/duplicate/deleted handles. Final Luna review of the diff: no further
concrete finding. r77 passed all 216 build tasks after 12min 3s (55 executed), app lint debug/release, and 145
identical source hashes before/after. The installed APK passed 180 Android app tests in 95.977s; five opt-in skips
stay separate. The new Room regression test ran and passed.

CI `a12e2e9` now surfaces the actual startup error instead of just a timeout: emulator 37.1.11 cannot find
`sourcescribe-ci` in its AVD directory. Run 34248225818 passed build/JVM/APK/lint; no Android instrumentation
started. `ci-a12-failed.txt` holds the real emulator output. A suspected unbound-variable bug in the new EXIT trap
was disproven by control-flow review: the trap is installed only after PID assignment. No timeouts or test gates
weakened.

## Final fixes after a real screenshot

`ui-history-r77.png/.txt` shows the fixed YouTube-only history with no foreign provider label. The same check also
found the export status and its matching error message appearing twice, and that "Retry missing branch" was offered
on SUCCESS even though nothing was missing. The minimal UI fix keeps only distinct extra errors and hides that one
action on full success; "New run" and "different provider" stay available. Root/Astra owns this production UI;
build/lint r79 passed after 12min 31s (216 tasks, 31 executed) with the same 145 source hashes. The independent
review additionally found SUCCESS_WITH_WARNINGS was also fully complete; Root confirmed this against
AcquisitionPlanner and latest-attempt aggregation and extended the same guard to SUCCESS_WITH_WARNINGS. Final
static Luna recheck: PASS; "New run," "different provider," and incomplete outcomes remain available. The device
then checks the final r80 state; the original r77 screenshot counter-check remains as evidence of the earlier
duplicated message.

The separate CI fix `558527f` was pushed: `ANDROID_USER_HOME` and `ANDROID_AVD_HOME` now apply both when creating
and when starting the AVD; the directory is created before SDK setup and registration is checked before start. Bash
syntax and repository gates PASS. New real CI run: 34252821287. No Android CI pass claimed before its successful
completion.

## Renewed user pause / r80

At explicit request, due to the weekly budget: no new tests or tasks. r80 finished normally: BUILD SUCCESSFUL after
10min 19s, 216 tasks (29 executed), app lint debug/release PASS. 145 source/build hashes identical before/after.
APK hashes in `apks-r80.json`, not yet installed. The last full app device check remains r77 with 180 passed plus
five opt-in skips. An after screenshot, a release audit of the final hash, and a new CI run are NOT_RUN.
