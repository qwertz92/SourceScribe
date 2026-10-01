# Project Status

**As of 2026-10-01.** The published personal preview remains `0.4.0` (18 September 2026). Candidate `0.4.1`
(versionCode 4) is signed, locally installable and verified from pushed implementation commit `297e553`.
It has not been tagged or published. The candidate gates below passed; full v1 acceptance is not reached yet.
The published app source is in public repo [qwertz92/SourceScribe](https://github.com/qwertz92/SourceScribe); this
file describes the current state only; the dated log of how it got here, including 23 rounds of independent
adversarial review over 0.1.0/0.2.0 and the 0.4.0 work, is in [HISTORY.md](HISTORY.md), and the lessons that work
produced are in [LEARNINGS.md](LEARNINGS.md). Open items are in [BUGS.md](BUGS.md), the only list of them.

## What it does today

A native Android app (Kotlin, Compose, Material 3) that fetches YouTube captions or transcribes audio via
AssemblyAI, OpenAI, or Groq, across four acquisition modes, with immutable per-job configuration snapshots, a
parallel job queue, process-death recovery, protected credential storage, full provenance/history, SAF-based
export, and signed, rollback-capable updates to the bundled extraction engine (yt-dlp). Since 0.4.0: a job's length
comes from the source itself rather than a typed limit; history shows live download/upload progress, which audio
rendition a job actually used, and when a job is waiting for an unmetered connection; the recommended audio
rendition favors quality (Opus, highest bitrate) over the smallest file; the new-source screen shows providers as a
list, greys out options a provider or mode cannot use instead of hiding them silently, keeps the check results in
view without a manual scroll, and adds named keyterm sets and a folder per export format; a failed or stopped job's
actions dialog says what happened and which action is recommended instead of an unexplained list; an engine whose
post-update self-test only times out is no longer disabled outright; and a provider's language name is read even
when it is spelled out as a word rather than a code. No summarization API, no web frontend, no backend service of
its own. Full requirements: [PRODUCT.md](PRODUCT.md) (SS-01 to SS-12). Phase definitions:
[ROADMAP.md](ROADMAP.md) (P0-P6).

## Status by phase

| Phase | Implemented / fixture-tested | Live-verified / blocked |
|---|---|---|
| P0 Feasibility | Runtime, verifier, manager; WebP rebuilt for both ABIs with 16 KB page alignment | Python/TLS, JS/EJS, FFmpeg, exact YouTube metadata/caption/audio, and update/rollback are all live-verified. Physical ARM64: not run yet; needs the owner's phone. |
| P1 Real pass-through | URL/planner, caption provenance, Room, export/viewer | Live-verified end to end: Android Share -> real caption -> internal storage -> SAF Markdown -> share dialog. |
| P2 First STT pipeline | Groq, local import, audio prep/chunking, credentials, submission limits | Live Groq transcription succeeded; one default-v3 rendition failed during free extraction before any provider call. The owner's unspecified source/model failure remains unreproduced. |
| P3 Full acquisition | AssemblyAI, OpenAI, all four modes, `BOTH` partial-failure handling, language/tracks/options/presets | Live AssemblyAI verification stored a complete post-fix result after the initial run exposed missing default sentence timestamps. OpenAI has no supplied key. |
| P4 Reliability | Queue/limits, recovery, unsafe submissions, export repair, update failures, source length taken from the source itself | Live-verified: process/permission boundaries, reboot recovery with a provider fixture, a signed engine update plus rollback, concurrent fixture jobs, and waiting/resume behavior. The specific interruption when a running job loses its unmetered network remains open under BUGS73. |
| P5 UX and delivery | German/English, stable history cards and actions, measured transfer progress, quiet notifications, viewer/export, signing path | Normal instrumentation, all separately enabled opt-ins, full negative controls, visual whole-card ripple, release signing and the in-place 0.4.0-to-0.4.1 upgrade passed. Full TalkBack operation remains unverified. |
| P6 Acceptance | Integrated regressions; confirmed P1/P2 findings in the changed provider/history/recovery/notification scopes fixed | Core tests, four lint reports and final scoped GPT-6 Luna reviews passed. Full acceptance remains open for physical ARM64, full TalkBack, a fresh-clone build, OpenAI (no key), and the owner's unspecified URL/model failure. Detailed evidence and coverage limits: [provider reliability report](reports/2026-09-30-provider-reliability.md). |

Of the 20 items from the owner's 2026-09-10 device test, 18 are implemented and verified on-device or by test; item 1 (result-view scrolling) is unreproduced and tracked in [BUGS.md](BUGS.md); item 2 (button spacing) was set
aside on 16 September 2026 because the owner could not recall what was meant. The owner's second device test, of preview 0.2.0 on 15-16 September 2026, added a P1
(every long job's speech-to-text stopped, fixed) and 15 further points; all fifteen are answered by the 0.4.0 work
(details in [HISTORY.md](HISTORY.md)).

## Releases

**0.4.1 candidate** — version `0.4.1`, versionCode 4; signed/pushed implementation `297e553` (first slice `c93d5e8`). Local signed APK: `.local-tools/releases/SourceScribe-0.4.1-297e553.apk`, 147,040,589 bytes, SHA-256 `a344994e3638494a1edfc96e968dabd5898d06c6662589beb34c3e4b42e72e9c`; signature and 16 KiB alignment verified with the existing release certificate. No tag or public release was created. Core: 231 passed; all four lint reports: zero issues; normal app instrumentation: 260 passed, 11 documented opt-in skips, zero failures. All app opt-in flows were separately exercised; extractor: 55 passed, zero skips. Three Groq jobs and two paid AssemblyAI POSTs were tested. Signed 0.4.0-to-0.4.1 upgrade preserved the real caption job, all six displayed passages, language and appearance. Independent GitHub run `36846190794` passed on CI setup fix `2cdd6ee`; its normal suites retain documented opt-in skips, all exercised separately locally. Evidence and coverage boundaries: [provider reliability report](reports/2026-09-30-provider-reliability.md), [structured receipt](reports/2026-10-01-candidate-0.4.1-evidence.json).

**0.4.0** — built and verified 16 September 2026 at commit `f7dd7d2`, published 18 September 2026. Version 0.4.0, versionCode 3. Answers the owner's 0.2.0 phone test
(the P1 and 15 further points) and closes BUGS items 58 and 59. Retained unsigned 0.4.0 APK: `.local-tools/releases/SourceScribe-0.4.0-f7dd7d2-unsigned.apk`,
146,859,674 bytes, SHA-256 `031d37af5759a1055153a5f98f6fb7f92e0debe3302624afe93f6fe130dcbd7a`. Tag `v0.4.0`, on the documentation commit that follows `7868497`. Gates: 215 JVM tests, 4 lint reports clean, repository check PASS, app instrumentation 239 tests (230 passed, 9 opt-in skips), 4 process stages, extractor 55 (51 passed, 4 opt-in skips) plus its 5 live tests, 1 real Groq request SUCCESS, CI green at `7868497`, update 0.1.0 to 0.4.0 and 0.2.0 to 0.4.0 PASS on a fresh emulator. Full evidence:
[2026-09-16 preview report](reports/2026-09-16-preview-0.4.md).

**0.3.0** — version bumped at commit `5ebc11e` (16 September 2026, not published). JVM tests were green there
(195 tests, `core` module) and the debug APK built cleanly, but the release-APK build attempt at that commit ended
when the Gradle build daemon crashed during packaging, before a real 0.3.0 release APK — signed or unsigned — ever
existed; the unsigned-APK file a build script copied out under that name is byte-identical to 0.2.0's and is stale,
not a rebuilt artifact. Before a retry, the owner's second device test of 0.2.0 had already found the P1 and 16
further points, so 0.3.0 was not pursued further: every fix planned for it, plus the phone-test fixes, ships as
0.4.0 instead. Nothing under the name "0.3.0" was ever installed, signed, or released.

**0.2.0-preview.1** — built 2026-09-14 at commit `1fe2dad`; tag `v0.2.0-preview.1`. 187 JVM tests, 202/208 app and
46/50 extractor instrumentation tests on `emulator-5556` (the rest opt-in), all 4 lint reports clean, CI green.
Signed with the 0.1.0 release key and attached to the GitHub release: 146,688,333 bytes, SHA-256
`ea8bf17fa1262c5d4869ad7a5db9f6183b54746714f82ac46c7c031c449f971b`. Full evidence:
[2026-09-14 preview report](reports/2026-09-14-preview-0.2.md).

**0.1.0-preview.1** — built and gated 2026-09-08; tag `v0.1.0-preview.1` at commit `f1a791c`. Personally signed
release APK: 147,376,546 bytes, SHA-256 `410b654fd08bdb2b7f8142dd88f440b7f49ffe123e363dd3607582b9e855c20a`. Full
evidence: [2026-09-08 preview report](reports/2026-09-08-preview.md).

## Verified gates for published 0.4.0 (2026-09-16, commit `f7dd7d2`)

- `215` JVM tests in `core`, 0 failures.
- `app` instrumentation on `emulator-5556`: 239 run, 230 passed, 0 failed, 9 skipped as opt-in (listed under Skipped), 22:29 to 22:31.
- `extractor` instrumentation on `emulator-5556`: 55 run, 51 passed, 0 failed, 4 skipped as opt-in and run separately right after with their URLs (1 + 1 + 2 + 1 tests, all passed).
- The one opt-in live test that spends money, `LiveGroqTranscriptionTest`: run once, PASS: one request, outcome SUCCESS, 197 characters in 4 segments, language en.
- All 4 lint reports (`app` and `extractor`, debug and release): `PASS`.
- `tools/check-repository.py`, including its self-test: `PASS (237 files read, 5 scripts checked for their executable bit, no issue)`.
- CI run `35146671787` on commit `7868497`: `PASS`.
- Upgrade test, an installed 0.1.0 or 0.2.0-preview.1 updated in place to 0.4.0: Verified on 16 September 2026 on a fresh emulator (Pixel 10, Android 17, the same system image as the test device, started as AVD `Upgrade10` at port 5556 because the usual AVD had no room for a second 147 MB install): a 0.1.0-preview.1 and a 0.2.0-preview.1 install, each with one finished captions job of the 19-second clip, were updated in place to the signed 0.4.0 with `adb install -r`; after the update the job, its stored transcript and the settings were still there and the crash log stayed empty.
- Independent review rounds over the 0.4.0 work: round 1 (Sonnet, read-only, over the sixteen 0.4.0 commits `f18937f` to `f22389c`): 0 P1, 2 P2, 3 P3, 1 P4; all five fixed in `46d92e3`, `d0c55fb`, `c832a9c`, `0d1953d` and `75af4f3`, each with its test seen failing first, plus BUGS item 60 closed in `96b9588` and item 63 by the new `JobCardLayoutTest`; the round's other P3, progress that goes back after a retried upload, is recorded as deliberate in LEARNINGS, round 2 (Sonnet, read-only, over the seven fix commits `46d92e3` to `f7dd7d2`): 0 P1, 1 reported P2 that verification re-rated P3 because it needs two engine switches during one job's life (BUGS item 72), 2 further P3 (items 73 and 74, recorded), 1 overstated KDoc corrected in `7868497`; no code changed, so no third round.

## Running the test gates correctly

Two instrumentation suites, `app` and `extractor`, must both run — commands are in [BUILD.md](BUILD.md). For
`extractor`'s `EngineUpdateManagerTest`, pass `-e sourcescribeEngineUpdate true`: without it, tests are silently
skipped by assumption and the run still reports `OK`. `app`'s `ProcessRecoveryTest` needs one run per process-death
stage (`-e sourcescribeProcessFixture true -e sourcescribeProcessStage <1|2|import-1|import-2>`); running more than
one stage per invocation fails the others. The tests that need a live video (`EngineJobPinningTest`,
`EngineUpdateManagerTest`'s real-release round trip, `ExtractionChainTest`, and `NativeRuntimeTest`'s public-source
probe) take `engineProbeSource` or `publicSourceUrl` and run before every release.

`app`'s opt-in `LiveGroqTranscriptionTest` sends one real Groq request: with `-e sourcescribeLiveProvider groq
-e liveProviderKey <key> -e publicSourceUrl <url of a clip of at most 30 seconds>` it sends exactly one real request
to Groq through the whole pipeline, and without all three arguments it skips with that sentence. The owner keeps the
Groq key outside version control and has supplied credentials for the other authorized provider checks. Do not put
credential values in this document or logs. The run ends with `adb logcat -b all -c` because `adbd` otherwise keeps
the whole `am instrument` command line — key included — in the device's system log (see [LEARNINGS.md](LEARNINGS.md)).
Commands and details:
[BUILD.md](BUILD.md).

`emulator-5556` is the agent device; `emulator-5554` belongs to the owner and agents do not touch it. Device tests
run on Android 17 (API 37); on 14 September 2026 the owner decided that older Android versions get no separate run.

## Remaining gates and blockers for 0.4.1

| Blocked | Why | What would unblock it |
|---|---|---|
| Physical ARM64 device | All device coverage ran on x86_64 emulators only. | The owner installs the release APK on the owner's phone and repeats the main flows. |
| OpenAI live transcription | No key was supplied. | Owner supplies a key and authorizes a live call. |
| Full TalkBack operation | Semantics and dialog activation are tested, but full focus navigation has no suitable real-input automation. | A human tester, or suitable real-input automation, walking all main screens with TalkBack on. |
| Fresh-clone build | Never run on a second machine or a clean Linux/WSL system without existing project caches. | Run BUILD.md's fresh-clone steps on such a system. |

## Local environment

Windows ADB: `/mnt/c/Users/thoma/AppData/Local/Android/Sdk/platform-tools/adb.exe`. `emulator-5554` (API
37/x86_64/16 KB, 1280x2856, density 480) belongs to the owner — agents do not operate or read it. Agents use
`emulator-5556`, API 37/x86_64/16 KB, 1080x2424, density 420; free space on `/data` there needs watching (uninstall
`app.sourcescribe.extractor.test` and `app.sourcescribe.debug.test` first when tight, never the app itself). Java
17.0.20.1, Gradle 9.7.1 (measured compatibility hold), AGP 9.4.1, Kotlin 2.4.20, Compose BOM 2026.09.00. SDK and caches under `.local-tools/`.

16 GiB of WSL swap is active. One Gradle build worker, 2 GiB heap; do not move SDK/build data to RAM-backed `/tmp`.
Only one build or one ADB check stream runs at a time — never instrumentation beside a running Gradle build. The
personal signing key lives outside the repository; `.local-tools/PRIVATE-RELEASE.md` names its storage and backup
locations (no password values). Details and the full history of environment findings: [LEARNINGS.md](LEARNINGS.md).

## Open issues and evidence

Known open issues, with impact, priority, location, and evidence, are tracked in the single list in
[BUGS.md](BUGS.md). The 0.4.0 release ledger recorded no P1; final scoped GPT-6 Luna reviews of the 0.4.1 changes were empty; the older issue list is not claimed empty.
Raw build/device/licensing evidence is under
[docs/reports/](reports/).
