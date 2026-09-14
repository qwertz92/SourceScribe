# Next Steps

**As of 2026-09-14.** Preview `0.2.0-preview.1` is published with its signed APK. Work towards `0.3.0` is in
progress.

## Towards 0.3.0, in order

1. Fix, test-driven, the known issues that matter or take minutes: the start-up lock (BUGS item 57, together
   with 47), the quick P3 items 3, 4, 12, 13, 17, 18, 36, 41 and 42, and the quick P4 items 20, 23, 30, 32, 53
   and 56. Every other item stays recorded in [BUGS.md](BUGS.md) with its priority.
2. Keep all repository documentation in English.
3. Run every test for the release, including the opt-in live tests against a public video and the four
   process-death stages, and test the upgrade from an installed 0.1.0 on an emulator.
4. One independent review round over the changes. P1 and P2 findings are fixed; everything else goes into
   BUGS.md.
5. Publish `v0.3.0` with the APK signed by `tools/sign-release.sh`.

In parallel the owner tests `0.2.0-preview.1` on a phone: the update over 0.1.0, scrolling in the result view
(BUGS item 1) and, with the owner's own API key, a short real transcription.

## Running the test gates correctly

Two instrumentation suites, `app` and `extractor`, must both run — commands are in [BUILD.md](BUILD.md). For
`extractor`'s `EngineUpdateManagerTest`, pass `-e sourcescribeEngineUpdate true`: without it, 14 tests are
silently skipped by assumption and the run still reports `OK`. `app`'s `ProcessRecoveryTest` needs one run per
process-death stage (`-e sourcescribeProcessFixture true -e sourcescribeProcessStage <1|2|import-1|import-2>`);
running more than one stage per invocation fails the others. The tests that need a live video
(`EngineJobPinningTest`, `EngineUpdateManagerTest`'s real-release round trip, `ExtractionChainTest` and
`NativeRuntimeTest`'s public-source probe) take `engineProbeSource` or `publicSourceUrl` and run before every
release. `emulator-5556` is the agent device; `emulator-5554` belongs to the owner and agents do not touch it.

## Blockers

| Blocked | Why | What would unblock it |
|---|---|---|
| Physical ARM64 device | All device coverage (Python/TLS, JS/EJS, FFmpeg, caption/audio, update/rollback) ran on x86_64 emulators only. | The owner installs the release APK on the owner's phone and repeats the main flows. |
| Live transcription (AssemblyAI, OpenAI, Groq) | Needs the owner's API keys and costs money; agents may not enter API keys. Only fixture responses have been exercised. | The owner runs a short real recording through each provider. |
| Full TalkBack operation | Semantics and dialog activation are tested, but full focus navigation has no suitable real-input automation; ADB/UIAutomation can bypass the input-filter chain TalkBack relies on. | A human tester, or suitable real-input automation, walking all main screens (track dialog, viewer, export, error views) with TalkBack on. |
| Engine signature check on Android versions before 17 (BUGS item 55) | Every device test ran on API 37, and no older emulator image is installed. | An API 29 emulator image, whose download needs the owner's approval, or a phone with an older Android version. |
| Fresh-clone build | Never run on a second machine or a clean Linux/WSL system without existing project caches. | Run BUILD.md's fresh-clone steps on such a system; confirm wrapper/dependency verification, build, and tests succeed. |
