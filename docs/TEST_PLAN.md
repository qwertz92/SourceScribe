# Test and Acceptance Plan

## T0 — Evidence Format

Every case gets `PASS`, `FAIL`, `BLOCKED`, or `NOT_RUN`, plus verification level, date, commit, the actual command/procedure run, device/API/ABI/page size, result, and sanitized evidence. Collect execution evidence under a documented test report; never commit private logs by default. In the original handover package, every app test was `NOT_RUN`. The current evidence status is in [STATUS](STATUS.md) and the test reports linked there.

Distinguish levels: JVM unit, provider/extractor contract with fixtures, Android instrumentation, a real public source, a real billable provider API, and a physical device. A simulated update check is valuable but not proof that a genuine upstream release could actually be activated on Android.

## T1 — Mandatory Deterministic Cases

| ID | Requirement | Check and expected result |
|---|---|---|
| T01 | SS-01 | watch/youtu.be/shorts, tracking parameters, video+playlist combos: same explicit ID; a plain playlist is rejected |
| T02 | SS-01/10 | Lookalike hosts, userinfo, encoding tricks, shell/filename payloads: no foreign source and no command execution |
| T03 | SS-01 | Metadata/download return a different video ID: hard failure before storage/upload |
| T04 | SS-02 | Four modes × captions present/missing/temporarily unavailable: exactly the permitted steps run, no silent uploads |
| T05 | SS-02/11 | Don't treat a caption 429/parser error as "doesn't exist"; the default waits/asks instead of paying for STT |
| T06 | SS-03 | Change global settings after start: the running configuration snapshot stays unchanged |
| T07 | SS-03/07 | Multilingual, uploader/auto/translated, and unknown tracks: correct selection and honest provenance |
| T08 | SS-03/07 | Multiple audio tracks/auto-dub: explicit track choice, never a silently wrong original language |
| T09 | SS-07 | Roll-up captions, entities, multi-line VTT/SRT, Unicode, genuine repetitions, numbers/negations: content is preserved |
| T10 | SS-04 | Every adapter: success, 401/403, 429, timeout, 5xx, invalid JSON, missing text, unsupported options |
| T11 | SS-04/06 | Reconstruct the AssemblyAI remote ID across a process restart; no repeat submission while polling |
| T12 | SS-04/11 | Timeout after possible acceptance: SUBMISSION_UNCERTAIN; no automatic duplicate billable request |
| T13 | SS-04/07 | Size/duration limit enforced before upload; chunk offsets/overlaps; a missing chunk means incomplete; no cross-chunk phantom speaker |
| T14 | SS-05 | Job A fails/is cancelled, job B stays unaffected; the resource limit is actually respected |
| T15 | SS-05 | Duplicate share intents/job claims never create a silent second billable job |
| T16 | SS-05/07 | BOTH: one branch succeeds, one is missing/broken; PARTIAL_SUCCESS, separate artifacts, targeted retry |
| T17 | SS-08 | Successful internal result plus a revoked SAF permission: the transcript stays intact; export retry without STT |
| T18 | SS-08 | Disk full, write aborted, duplicate names, unsupported rename, file deleted externally: an honest export state |
| T19 | SS-08 | Crash between the temporary file, finalization, and the Room commit: reconciliation without losing the result or resubmitting |
| T20 | SS-08 | Cleanup and audio retention: active files/transcripts/sibling artifacts are preserved |
| T21 | SS-09 | Search/view/share large transcripts; no OOM/ANR, no full audio loaded as a `ByteArray` |
| T22 | SS-07/09 | No timing/speaker data: no invented labels, SRT/VTT locked accordingly; TXT/MD still available |
| T23 | SS-10 | A canary API key appears in none of: logs, plaintext DB, exported diagnostics, backup rules, or WorkManager data |
| T24 | SS-10 | Update with a wrong signature/hash, zip traversal, a decompression bomb, or the wrong runtime: no execution whatsoever |
| T25 | SS-10 | Cancellation/disk full during an update: the current engine stays usable; crash recovery; rollback works |
| T26 | SS-10 | Update while two jobs are running: version pinning holds, no partially replaced files or mixed EJS versions |
| T27 | SS-10 | Offline/a private source/429 never triggers a reflexive update; update retries follow the policy at most |
| T28 | SS-11 | Estimated prices/limits unknown or stale: shown as such; never present an organization-wide quota as a local balance |
| T29 | SS-10/11 | HTTP redirect/retry: no secret reaches a foreign host, no uncontrolled repeat of a submission |
| T30 | SS-12 | German/English app language switch, system/light/dark theme, 200% font scale, TalkBack, long titles, rotation, small/large displays, and denied notifications; critically check screenshots of the views for spacing, alignment, and proportions |
| T31 | SS-04/10 | Fixture providers, test HTTP, and debug actions are unreachable in the personal release build |
| T32 | SS-05/08 | A Room migration/upgrade preserves history and artifact references; no destructive fallback without being asked |
| T33 | SS-08/09 | Local audio import via a content URI, a temporary grant, and a process restart: a controlled internal copy/permission |
| T34 | SS-07/10 | Malicious caption/Markdown instructions stay data; no execution, external forwarding, or automatic tool invocation |

Test provider contracts against a controlled HTTP test server. Don't deliberately provoke errors against real providers when fixtures can test the same thing deterministically. Generate small, cleared audio fixtures yourself, or source them under a clear license; never store someone else's confidential audio as a public fixture. Test streaming and cancelled responses separately where the adapter supports them.

## T2 — Mandatory ADB/UI Checks

Determine the environment for real, e.g., with `adb devices -l`, API/ABI queries, and `adb shell getconf PAGE_SIZE`. Record version data in the report. Test native functionality, not just "the app launches." Primarily use the system the user provided; add supplementary API/16 KB tests where infrastructure allows. If a combination wasn't available, don't list it as verified.

Flow: fresh install → onboarding → folder selection → share intent with a real, permitted YouTube link → verify metadata → YouTube-only transcript → viewer → read the external file and compare provenance → share. Additionally, import a local audio file and run the chosen provider path with permitted credentials.

Run all four modes, including missing captions and a BOTH partial success. Cover deterministic no-caption/error cases through test-only adapters/HTTP fixtures; separately verify at least one real capture path and one real audio path. Available public videos can change; record the source ID and timestamp, and don't assume caption availability stays fixed forever.

At least two concurrent jobs, cancelling one while the other keeps running. Check backgrounding the app, screen off, navigation, rotation, normal process termination, reboot, and an explicit force-stop as separate cases. A force-stop should not produce any magical continuation; prove correct reconciliation the next time the app opens. Check interruption during download, upload, remote waiting, internal result storage, and export.

Trigger airplane mode/network changes, rate limiting, an expired URL, a revoked folder permission, and storage errors under controlled conditions. Document battery/OS restrictions instead of running every test only with the app permanently in the visible foreground. Check Logcat for crashes, ANRs, and secret leaks. Screenshots complement the functional check; they don't replace it. For Compose, we follow [Chris Banes's testing checklist](https://github.com/chrisbanes/skills/blob/main/skills/compose-ui-testing-patterns/SKILL.md) (reviewed September 8, 2026), which we have read: semantic tests for behavior, real screenshots for spacing, alignment, clipping, color, and type. Data for these layout cases is deterministic; real-source checks stay separate. No extra screenshot-testing platform solely to apply this checklist. Announce audible TalkBack tests beforehand, and afterward reset any temporary accessibility settings to the state measured before.

## T3 — Real Provider and Update Evidence

Real STT calls need credentials, content, and a cost ceiling the user has released for this purpose. The test budget defaults to **no billable calls without authorization**. No account upgrades and no automatic top-ups. Before testing starts, fix an estimate and the number of planned requests; respect the abort threshold. Never write credentials as CLI arguments or into screenshot test scripts.

For every available provider, run a short end-to-end test: source/file → preparation → submission → complete result → internal persistence → exported file. Verify runtime, the model actually used where reported, a redacted provider ID, and format. The "all providers live-tested" sign-off stays blocked if only one provider was actually available, even if every adapter passed with fixtures.

Updater: test both deterministic malicious/error scenarios and a real, verified release-artifact fetch, activation, and return to the previous/bundled engine. When no new release exists, use a suitable, permitted version change in a separate test install. An identical version string with a faked success message is not an update test. Never activate a deliberately vulnerable engine for a live network test.

## T4 — Adversarial Review Passes

Three independent viewpoints are enough as a starting point: (1) lifecycle/persistence/cost, (2) security/updater/input, (3) data quality/provider contracts/UX. No fixed number of "superagents" as a substitute for quality. Use real subagents where available; otherwise, name separate review passes openly as such.

Reviewers get requirements, code, and test reports — not just the implementer's self-assessment. Findings come with reproducible evidence and a severity level. The integrator reviews, fixes, adds a regression test, and has it retested. A second, final pass after the fixes is mandatory. Never confirm a fix using only the same test that missed the bug in the first place.

## T5 — CI and Release Acceptance

CI without provider secrets: Gradle wrapper/dependency verification, lint, unit/contract tests, a debug APK, and a sensible static check. Pin GitHub Actions to verified immutable references; least-privilege tokens. Untrusted pull requests get no signing/provider secrets. Room schema/migration checks and a basic secret scan are part of this.

Fill in exact commands only once the project structure exists, and actually run them; no copy-pasted command with a fictional module name passed off as evidence. Instrumentation where CI infrastructure supports it, plus real ADB evidence on top. Separately verify the release build for the absence of debug backdoors, manifest permissions, library/APK alignment, and the correct signature.

Acceptance: full v1 scope, every associated mandatory case passed, no open critical/high defects, and no open acceptance-requirement violation. Document known low risks. External blockers justify an honest preview, not a false claim of full release readiness. Never claim "no bugs exist" — instead, name the scenarios verified and their limits.

## T6 — Tests Skipped by Default

Several instrumentation tests are opt-in and do not run in the default suite; each needs a specific instrumentation argument.

`ProcessRecoveryTest` simulates Android killing the app in the middle of a job: stage 1 starts the work, the process is then killed from outside, and stage 2 runs in a fresh process and checks that nothing was lost, repeated, or charged twice. A single instrumentation run cannot kill its own process between two tests, so each stage is its own test method: `stage1PersistsAcceptedAssemblyRemote`/`stage2ReopensAndFinalizesWithoutResubmission` for a running job, and `stage1PersistsRevokedLocalAudioImport`/`stage2ReopensPrivateAudioAfterGrantAndSourceLoss` for an import. Opt in with `sourcescribeProcessFixture=true` and `sourcescribeProcessStage`.

`EngineJobPinningTest#runningCoordinatorJobsFinishWithTheirPinnedEngineAcrossRealActivation` downloads a real yt-dlp release from GitHub and activates it while two real jobs run against a real YouTube video, then checks that each job finished with the engine it started with. It needs internet, GitHub, and YouTube, and YouTube may block or throttle CI runners, so it does not run by default; it runs before every release. Opt in with `sourcescribeEngineJobPinning=true` and `engineProbeSource=<YouTube URL>`.

`EngineUpdateManagerTest#realReleaseStageActivateAndRollbackSurvivesManagerRestart` performs a real download, activation, a probe against a real video, rollback, and a manager restart. Same reason as above: it runs before every release, not by default. Opt in with `sourcescribeEngineLiveUpdate=true` and `engineProbeSource`.

`ExtractionChainTest` (`actualCaptionIsFetchedAndParsedForExactSource` and `actualAudioIsDownloadedProbedAndPreparedForExactSource`) fetches and parses the real captions and downloads and prepares the real audio of a real video. Same reason: it runs before every release, not by default. Opt in with `publicSourceUrl`.

`NativeRuntimeTest#publicSourceProbeUsesOnlyConfiguredSourceIdAndCounts` checks against a real video that the engine requests only the configured video ID. Same reason: it runs before every release, not by default. Opt in with `publicSourceUrl`.

`LiveGroqTranscriptionTest#aShortPublicVideoIsTranscribedByGroqAndStoredWithItsProvenance` is the only test that spends real money: it resolves a real source with the real engine, downloads the recommended audio rendition, prepares and uploads it, and drives the app's own pipeline through to a stored, provenance-checked transcript, asserting exactly one provider request. Opt in with `sourcescribeLiveProvider=groq`, `liveProviderKey=<key>`, and `publicSourceUrl=<url of a clip of at most 30 seconds>`; missing any of the three ends the test as a named assumption failure, and a source longer than 30 seconds fails before any upload. It also asserts the key never reaches the app's own logcat entries, WorkManager input data, the diagnostics text, or any stored row or artifact — but not the device's system log, which `adbd` writes the whole instrumentation command line into regardless (see [BUILD.md](BUILD.md) and [LEARNINGS.md](LEARNINGS.md)). Run to prove a fix, not routinely, and only against a short public clip, respecting the owner's free-tier limits. As of 16 September 2026 it has been run for real exactly once, against a 19-second video, and passed with a complete transcript.

`UiFixtureTest#seedSyntheticUiFixtureForAdbViewer` is not a behavior test: it fills the app with synthetic history so the screens can be inspected by hand over adb. It is not part of a release run. Opt in with `sourcescribeUiFixture=true`.

The regular `EngineUpdateManagerTest` cases require `sourcescribeEngineUpdate=true`; CI passes that flag, so they run on every push.
