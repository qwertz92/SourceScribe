# SourceScribe

**As of:** 14 September 2026 · Personal preview 0.2.0-preview.1 with its signed APK; work towards 0.3.0 in progress; known issues in [BUGS](docs/BUGS.md) · **Status:** Personal preview; full v1 acceptance not reached

SourceScribe is a personal-use Android app for traceable transcripts. An explicitly entered YouTube source can be shared or pasted in; existing captions are archived, and audio can be transcribed through the chosen provider. Results stay internal at first and can be shared as a file through the Android share sheet. Summarization and fact-checking in German happen outside the app.

## Current state

The public repository is [qwertz92/SourceScribe](https://github.com/qwertz92/SourceScribe); its [releases page](https://github.com/qwertz92/SourceScribe/releases) is where signed, installable APKs are published.

For preview 0.2.0-preview.1, commit `1fe2dad` built the debug app, both test APKs, and the unsigned release APK; `tools/sign-release.sh` signed the release APK with the key of 0.1.0, and it is attached to the release.
[Try it](docs/TRY_PREVIEW.md) · [preview release](https://github.com/qwertz92/SourceScribe/releases/tag/v0.2.0-preview.1) · [verification report](docs/reports/2026-09-14-preview-0.2.md).
At this commit, 187 JVM tests passed, along with 202 of 208 instrumented tests in the `app` module and 46 of 50 in the `extractor` module, on the API 37/x86_64 emulator with 16 KB pages, `emulator-5556`. The other ten are opt-in: four process-recovery stages ran individually and passed; six tests need a real source, a real engine download, or only set up test data, and did not run. The same commit is green in CI, including the device tests of both modules. Real Android caption/audio extraction and the caption-to-SAF-export path were verified for 0.1.0 and not repeated for 0.2.0. German/English, system/light/dark, redesigned selection fields, and gating a release on a deliberate job start are all in place. [STATUS](docs/STATUS.md) separates implementation, fixture testing, and real-world verification.

Real transcription with AssemblyAI, OpenAI, and Groq has not run yet: it needs the owner's API keys, which agents may not enter. The app has not run on a physical ARM64 phone yet, and full TalkBack operation is not verified with the available input automation.

CI builds, tests, and lints, and runs the device tests of both modules on an emulator. Its device step failed in four runs on 10 and 14 September on `ChoiceAccessibilityTest`. The last of those showed the cause: the language selector stayed locked because the app was still starting, for longer than the 25 seconds the test waited. Since `1fe2dad`, the test first waits for startup to finish, and the run at `1fe2dad` is green ([DEFECTS](docs/DEFECTS.md), item 54).

The authoritative evidence is kept up to date here:

- [Actual project status](docs/STATUS.md)
- [Remaining work and next verification targets](docs/NEXT_STEPS.md)
- [Known issues by priority](docs/BUGS.md)
- [Known issues in detail](docs/DEFECTS.md)
- [Learnings log for other agents](docs/LEARNINGS.md)
- [Work history, including every review round](docs/HISTORY.md)
- [Build and personal release](docs/BUILD.md)
- [0.2.0 preview verification report](docs/reports/2026-09-14-preview-0.2.md)
- [Build/P0 verification report](docs/reports/2026-09-07-build-and-p0.md)
- [License and provenance review](docs/reports/2026-09-07-licenses.md)

## Getting started

The [documentation index](docs/INDEX.md) leads to product scope, architecture, security boundaries, integration contracts, roadmap, and test plan. Read [AGENTS.md](AGENTS.md) before making changes.

The local build entry point is [docs/BUILD.md](docs/BUILD.md). It makes no provider calls and installs nothing on a device. ADB, provider, and other live verification are tracked in the reports at their actual level, as `PASS`, `BLOCKED`, or `NOT_RUN`.

## Technical guidelines

SourceScribe is a native Kotlin/Jetpack Compose app with no server of its own and no separate LLM API for summarization. Sources, configuration, processing state, and exports each have a separate contract. API keys, temporary audio data, and full transcripts belong in none of: Git, logs, diagnostic exports, or test reports.
