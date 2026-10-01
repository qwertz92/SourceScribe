# Provider reliability and history fixes — 30 September 2026

## Scope and verified causes

The owner's failed link and model selection were not supplied, so that exact failure is not reproduced. A fresh
baseline Groq run already succeeded for the public 19-second source `jNQXAC9IVRw`. The fixes address independently
reproduced defects rather than attributing the owner's failure to a guessed format or size limit.

The app downloads the selected original rendition (often WebM/Opus), then prepares mono 16 kHz, 64 kbps MP3
chunks of at most ten minutes and 24,000,000 bytes. The original WebM file is never sent directly. Current primary
contracts were checked: [Groq speech-to-text](https://console.groq.com/docs/speech-to-text),
[Groq API reference](https://console.groq.com/docs/api-reference), and
[AssemblyAI transcript submission](https://www.assemblyai.com/docs/pre-recorded-audio/api-reference/transcripts/submit).
AssemblyAI's current `universal-3-5-pro` model was retained; an older indexed model page was not treated as authority.

Confirmed defects and changes:

- Provider configuration could fail locally only after download/preparation, yet appear as a remote rejection.
  Preview and execution now use the adapters' shared configuration validation before acquisition, including the
  captions-then-STT fallback. Invalid model/language/context/keyterm options have specific local explanations.
- Remote errors now preserve only typed operation, HTTP status, and recognized reason in the checkpoint.
  Error-body parsing is bounded at 16 KiB; arbitrary provider text, keys, and transcript content are not persisted.
  Actions shows the failed branch and phase and the available diagnostic. Unknown reasons stay unknown.
- Preparing the same source/rendition again can copy a verified original from an earlier attempt. Identity,
  engine binding, private regular-file status, and SHA-256 must all match. Missing, modified, or mismatched originals
  are downloaded again. Older checkpoints without a recorded source hash may therefore need one fresh download
  before later attempts can reuse the verified original. Paid requests, provider responses, and prepared chunks
  are not silently reused or repeated.
- WorkManager phase continuations and AssemblyAI polling no longer alternate the visible queued/running status.
  Foreground native network callbacks refresh presentation while retaining the real retry deadline and constraints.
- Upload progress counts the current audio request body, including multipart overhead. AssemblyAI's small JSON
  submission does not overwrite audio-upload progress. Reloading the current row before saving the next phase
  prevents a stale row from erasing byte counters. Percentage and speed appear only from measured data.
- Ordinary progress and waiting states do not notify. Completion notifies in the background; actionable failures
  notify once, including in the foreground. Persistent event deduplication prevents already-recorded dismissed alerts from reappearing
  after recovery. A process death after posting but before recording, followed by dismissal before restart,
  can produce one duplicate; OS delivery and preference persistence are not atomic. Startup does not announce historic completions.
- The card owns the ripple across its whole outline. Error summaries reserve two lines and the help-button slot;
  the complete explanation remains in Actions.

## Provider evidence — 30 September to 1 October 2026

All live jobs used the exact public 19-second source `https://www.youtube.com/watch?v=jNQXAC9IVRw`.
These controlled checks do not reproduce the owner's unspecified URL/model combination. Raw redacted outputs
are in ignored `.local-tools/build-reports/`; console copies are not additional requests.

| Provider / run | Requests and result |
|---|---|
| Groq Turbo baseline | One successful job: one transcription upload, four segments, 197 characters, `en`, 36.964 s. |
| Groq Turbo after fixes | One successful job: one transcription upload, four segments, 197 characters, `en`, 40.344 s. |
| Groq v3, explicit rendition `139-drc` | One successful job: one transcription upload, four segments, 201 characters, `en`, 33.327 s. |
| Groq v3, default rendition `251` | Free download failed with `CHALLENGE_REQUIRED`; zero provider requests. |
| AssemblyAI before sentence fix, `139-drc` | One upload, one paid transcription POST, one poll: stored `PARTIAL_SUCCESS`, one segment, 201 characters, `en`, 74.855 s. |
| AssemblyAI after sentence fix, `139-drc` | One upload, one paid transcription POST, two retrieval GETs: stored `SUCCESS`, four segments, 201 characters, `en`, no warnings, 71.406 s. |

Totals: three successful Groq jobs and two paid AssemblyAI transcription POSTs. Both AssemblyAI jobs retained
the requested and reported `universal-3-5-pro`; no diarization option, model change, or paid resubmission was
used to obtain sentence boundaries. Source duration is not a measurement of invoiced duration or cost. Groq
returns no model field, so its model is recorded as requested and its reported model remains unknown.

The first AssemblyAI job exposed a real integration defect: `utterances` are tied to diarization, so the default
request's segment timestamps need the native `/sentences` endpoint. The adapter now saves the accepted receipt
before scheduling that GET. A failed GET stays in retrieval and cannot turn into another paid POST. Pending
receipts, ID/text/timing validation, composite size limits and atomic response replacement are covered by
regressions. Legacy unmarked receipts remain readable with truthful partial status. See
[ADR 0014](../adr/0014-assemblyai-sentence-retrieval.md) for the boundary and primary sources.

## Local verification

The complete normal build after all controls passed on 1 October: `:core:test`, debug/test/release APK generation,
the extractor JVM test task (`NO-SOURCE`), and all four app/extractor debug/release lint tasks (16m 17s, 261 tasks).
Core: 231 passed, zero failures or skips. Each lint XML contained zero issues. All 211 implementation/build/tool
source hashes matched the pre-build manifest and the committed sources at `297e553`. Fresh normal APKs were
installed; the complete app instrumentation passed 260 cases, with 11 explicitly opt-in skips and zero failures
among 271 cases (144.743 s). The eleven opt-in methods were all separately exercised:

| Additional device check | Result and limit |
|---|---|
| Live Groq and AssemblyAI methods | Passed through real provider calls; exact totals are above. |
| Four process-death stages | 4/4 passed, no skips. Accepted AssemblyAI fixture receipt survives restart with retrieval only; private imported audio survives loss of its external grant and source. Fixture requests do not spend provider credits. |
| Actual offline startup | 1 passed, no skips; a fresh activity recomputes the seeded queued job's waiting state from the offline snapshot. |
| Actual unmetered waiting/resume | 2/2 passed, no skips. No app PID existed before restoring Wi-Fi; WorkManager woke PID 11973 before the second instrumentation invocation. Network settings were restored exactly. The worker uses a nonexistent attempt ID, so no provider job is submitted. |
| Signed engine activation with pinned jobs | 1 passed, no skips (66.899 s). Two coordinator caption **fixture** jobs retained their old engine while a real signed engine activation and native runtime probe were performed. This is not two real caption acquisitions. |
| Synthetic UI fixture | 1 passed, no skips; uses the actual app database builder and migrations. |

The final extractor instrumentation passed all 55 cases, zero failures or skips (261.843 s), with every engine
and public-source option enabled. It exercised actual native metadata/caption/audio/preparation and signed
engine update/rollback: bundled yt-dlp `2026.08.19`, activated `2026.09.27.232945`, EJS `0.8.0`. The public source
was the exact 19-second clip above. These extractor checks made no paid provider request. Counts and timings
are also in the [structured evidence receipt](2026-10-01-candidate-0.4.1-evidence.json).

Negative controls are run against retained tests, then restored byte-for-byte:

- The first HTTP/provider control reverted all five affected implementations and produced seven expected
  failures among 223 core cases.
- Reverting the AssemblyAI adapter produced seven failures among 50 selected adapter/HTTP cases. The new paid
  acceptance/GET-retry device regression was also observed failing before the fix: it entered `SUBMIT` instead
  of `RETRIEVE`. Three focused adapter cost/size regressions failed before their correction.
- The app functional control disabled every configuration preflight, checkpoint diagnostic write, current-row
  upload-counter reload, progress update, reuse call, visible-state transformation and notification gate in its
  plan. It compiled and produced 17 failures, 113 passes and one connected-run offline skip among 131 cases. A prior temporary mutation failed to compile on
  two unused expressions; that run is `NOT_RUN` for device verification, not a successful negative control.
- The audio/offline control disabled all source, rendition, engine, hash, symlink and storage-reservation gates,
  stale diagnostic clearing and both native refresh call sites. Seven device cases failed; all six expected
  oracles were observed, including the separately enabled actual offline startup. There were 46 passes,
  seven failures and one connected-run opt-in skip; the offline invocation itself failed as expected rather
  than skipping. The first compiled attempt could not install because the isolated emulator was gone, so
  that attempt is `NOT_RUN` for device evidence. It was restarted without wiping data, then the same sources
  built up-to-date and the device control completed. All affected source hashes were independently checked
  after restoration.
- The complete UI control removed both the measured row floor and native `Text.minLines`, varied the
  previously fixed help slot, and disabled transfer phase/state guards and neutral retry/error text. It produced
  five failures and 23 passes among 28 device cases; every expected oracle failed. The earlier partial control
  caught four cases but left the native text minimum and help slot in force; it is not presented as a complete
  height control. All source hashes matched after the final restoration.
- `assemblyUploadTransportFailureRemainsBoundedPreparedRetry` and the standalone
  `terminalOutcomesNotifyOnlyInBackground` did not fail in that combined control. Other tests caught audio
  upload progress, both notification foreground behavior and deduplication. A combined control does not prove
  that each individual mutation has an independent oracle.

Coverage boundaries: the cross-thread callback interleaving is not deterministically forced; the callback is
now natively dispatched on the main handler. The whole-card ripple was visually verified below. The local reprepare
setting regression does not independently prove retention of a previously chosen YouTube rendition. Controls
also do not isolate copy cancellation, an in-flight progress checkpoint, every legacy receipt path, or the
OS-post/preference-recording crash race. These limits are not claimed as passing checks.

## Independent review

Native GPT-6 Luna was selected through the actual runtime model override and checked against child runtime
metadata, recorded in `.local-tools/build-reports/2026-09-30-agent-runtime.json`. Reviews covered provider
adapters/HTTP, STT acquisition/recovery, history presentation, and notifications/lifecycle. The final passes
were empty: no demonstrated P1, P2, P3 or P4 finding in those assigned scopes. The AssemblyAI review first
found a paid-resubmission P1; it was reproduced, fixed, negatively controlled and reviewed again. Review pass
counts for the recorded scopes: provider 6, STT 5, history 5, notifications 8. These are scoped pass counts,
not a claim that the project's older issue list is empty.

## Signed candidate, native visual check and upgrade

Implementation `297e553` and the first provider slice `c93d5e8` are signed and pushed to `main`. The local candidate
is `.local-tools/releases/SourceScribe-0.4.1-297e553.apk`: version `0.4.1`, versionCode 4, 147,040,589 bytes,
SHA-256 `a344994e3638494a1edfc96e968dabd5898d06c6662589beb34c3e4b42e72e9c`. `apksigner verify --print-certs`
and `zipalign -c -P 16 4` passed. The existing release certificate SHA-256 is
`19d1da9a8fe704082a531faed8a24d966c485aae581a7076dd4b4f66c11d3881`. No tag or public release was created.

On the dedicated `emulator-5556`, an actual native touch-down, screenshot, then touch-up showed the ripple covers
the card through the Actions footer. The screen contains an explicitly marked synthetic fixture:
[idle screenshot](screenshots/history-041-idle.png), [pressed screenshot](screenshots/history-041-pressed.png).
No owner emulator was operated or read.

The production package was absent on that emulator after its earlier restart; the reason was not established.
An existing signed 0.4.0 APK was therefore installed to create a controlled baseline, not presented as preserved
pre-session owner data. Baseline signed APK SHA-256:
`1578c9e58ee332f9fe09a2cf04054be0aeba3528766c601fc66224b85dfe8ec2` (the older status ledger's `031d37…` is
its **unsigned** build hash). In the ordinary release UI, the exact public clip was processed with **YouTube only**
and its English native JSON3 track. The job completed with six caption passages; appearance was set to Dark.
The candidate was installed with `adb install -r` without uninstalling or deleting app data.

After upgrade, the one completed history entry, all six displayed passages with their times/provenance, English
app language and Dark appearance were retained. The complete displayed-result snapshot was byte-identical
before and after. App ID 10235, first-install timestamp, and both credential/device-encrypted data inodes stayed
unchanged; package version changed from code 3 to code 4. The running candidate's crash buffer had no fatal
exception. This upgrade check made **zero provider calls**. Raw UI snapshots remain ignored rather than placing
full transcript text in Git; the structured receipt records their equality and hash.

Physical ARM64, full TalkBack navigation, a fresh-clone build and OpenAI live coverage (no key) remain unverified.
The owner's exact URL/model failure is unreproduced. Older open items, including the specific running-worker
network interruption in BUGS73, remain in [BUGS.md](../BUGS.md); clean scoped reviews do not close that backlog.

## Dependency compatibility measurement

Official Google Maven metadata confirmed AGP 9.4.1, WorkManager 2.12.0, and core-ktx 1.19.1 as current stable
updates. Gradle 9.8.0 was actually run with its official checksum
`bafd5ce9cfaea0fbccfdc8439a1ac42fbd4cd9c89dc9a988228d8a2639a58e6c`. Core tests still passed (223/223),
but the strict build failed: AGP 9.4.1's `BasePlugin.createAndroidJdkImageConfiguration` calls deprecated
`Configuration.setVisible`. A second `:help -Dorg.gradle.deprecation.trace=true --stacktrace` run confirmed the
upstream `BasePlugin.kt` frame in `createAndroidJdkImageConfiguration`. No newer stable AGP is published; only 9.5 alpha versions follow it.
The wrapper stays at checksum-pinned 9.7.1; its comment requires remeasurement at the next AGP update.
`org.gradle.warning.mode=fail` remains enabled. New dependency verification entries were additive (50 components
after resolution, zero removals), from the existing official repositories; the final strict metadata-verified build passed with these entries.

## GitHub notification test setup — 1 October 2026

[Run 36840202696](https://github.com/qwertz92/SourceScribe/actions/runs/36840202696) on `297e553` failed four
notification-delivery regressions: the ephemeral GitHub emulator had not granted `POST_NOTIFICATIONS`. It
reported 256 passes, 11 opt-in skips, four failures, zero errors among 271 app cases. All four failures explicitly
required that runtime permission; the local gate already grants it and passed 260 cases. Signed/pushed commit
`2cdd6ee` applies the same setup to CI: install the built debug APK and grant the declared notification permission
before the unchanged connected test tasks. No tests were skipped or weakened, and no credentials or paid calls
were added. The workflow YAML parsed and every shell step passed `bash -n`; the exact exported Git index passed
repository checks (247 files, six executable scripts). An earlier export under ignored `.local-tools` scanned zero
files and was rejected, then retried outside ignored directories. A read-only follow-up review was empty; two
stale live-evidence statements in BUGS11/64 were corrected without closing their unverified edge cases.
[Run 36846190794](https://github.com/qwertz92/SourceScribe/actions/runs/36846190794) on `2cdd6ee` then passed:
JVM/build/lint in 9m 20s; device task in 6m 23s. App: 260 passes, 11 documented opt-in skips, zero failures or errors
among 271 cases. Extractor: 51 passes, four opt-in skips, zero failures or errors among 55 cases. Every skipped path
was separately exercised locally as recorded above. Only the workflow and BUILD.md differ between `297e553`
and `2cdd6ee`; the APK implementation is unchanged.

## Temporary verification copies removed — 1 October 2026

Only this task's staged-source and negative-control copies were removed after restored source hashes, normal
checks and the signed/pushed implementation were verified. The source versions remain recoverable from Git.
Raw evidence, SDK/caches, credentials, old releases and the signed candidate were retained. This was source-test
cleanup, not a device-data deletion or a change to backup retention.

| Removed path | Regular-file bytes |
|---|---:|
| `.local-tools/provider-index-check` | 13,931,158 |
| `.local-tools/build-reports/2026-09-30-core-negative-source` | 90,598 |
| `.local-tools/build-reports/2026-10-01-control-backup-functional-compile-failed` | 247,126 |
| `.local-tools/build-reports/2026-10-01-control-backup-functional` | 247,126 |
| `.local-tools/build-reports/2026-10-01-control-backup-safe-install-failed` | 144,216 |
| `.local-tools/build-reports/2026-10-01-control-backup-safe` | 144,216 |
| `.local-tools/build-reports/2026-10-01-control-backup-ui-partial` | 177,615 |
| `.local-tools/build-reports/2026-10-01-control-backup-ui` | 177,615 |
| `/tmp/sourcescribe-assembly-adapter-e33a6b4216d635d0.kt` | 40,956 |

Total removed: 15,200,626 regular-file bytes (about 14.50 MiB); this is not a filesystem allocated-block measurement.

Two additional disposable source exports used for the exact-index CI check were removed:

| Removed path | Regular-file bytes |
|---|---:|
| `/tmp/sourcescribe-ci-index-check-moakqeaa` | 8,768,520 |
| `.local-tools/final-ci-index-check` | 8,768,520 |

These exports contained no unique source data. Their combined regular-file size was 17,537,040 bytes; together
with the nine copies above, 32,737,666 regular-file bytes (about 31.22 MiB) were removed.
The dedicated headless emulator was stopped after the checks; its installed app data was retained.

## Final disposable-helper cleanup — 1 October 2026

After the owner's clarified cleanup instruction, the following finished helpers and redundant evidence copies
were removed. The two runner scripts' exact content was verified recoverable in this task's session record;
the isolated UI probe is reproducible from the retained original by changing its serial and dump filename.
Screenshots already exist in pushed Git, and the duplicate build receipt was byte-identical to the retained one.

| Removed path | Bytes |
|---|---:|
| `.local-tools/run-app-controls.py` | 5,078 |
| `.local-tools/run-final-device.py` | 5,093 |
| `.local-tools/ui_probe_5556.py` | 1,272 |
| `.local-tools/build-reports/2026-10-01-history-ui.png` | 144,658 |
| `.local-tools/build-reports/2026-10-01-history-ripple.png` | 161,198 |
| `.local-tools/build-reports/2026-10-01-layout-fixtures-build.txt` | 6,382 |

Removed from the host: 323,681 additional bytes. Unique raw build/device/provider receipts,
mutation plans, upgrade snapshots and runtime-model evidence remain available for audit/reproduction.
The signed candidate is retained for the owner to install. Existing SDK/caches/build directories and original
helpers predate this task and were not deleted.

Pre-existing historical source copies, outside this task's ownership, remain:

| Retained path | Bytes |
|---|---:|
| `.local-tools/AppPipelineTest-r67.kt` | 87,595 |
| `.local-tools/ChoiceAccessibilityTest-r69.kt` | 5,082 |
| `.local-tools/ChoiceAccessibilityTest-r71.kt` | 5,093 |
| `.local-tools/EngineJobPinningTest-r65.kt` | 18,498 |
| `.local-tools/EngineJobPinningTest-r69.kt` | 19,549 |
| `.local-tools/ProcessRecoveryTest-r65.kt` | 33,258 |
| `.local-tools/TalkBackNavigationTest-r67.kt` | 14,415 |

Recommendation: compare these older copies to reachable Git history before deciding to remove them. Their
unique content has not been established here; they do not affect the app or the clean Git worktree.

The temporary device-side UI dump `/sdcard/sourcescribe-final-window.xml` was also removed from the dedicated
`emulator-5556` (15,487 bytes). Its SourceScribe origin and absence after deletion were checked; the emulator was
then stopped again. Installed app data and the owner's emulator were retained.
