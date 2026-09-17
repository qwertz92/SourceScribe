# SourceScribe

**As of:** 16 September 2026 · Personal preview 0.4.0, built and verified 16 September 2026, published 18 September 2026 · known issues in
[BUGS](docs/BUGS.md) · **Status:** Personal preview; full v1 acceptance not reached

SourceScribe is a personal-use Android app for traceable transcripts. An explicitly entered YouTube source can be shared or pasted in; existing captions are archived, and audio can be transcribed through the chosen provider. Results stay internal at first and can be shared as a file through the Android share sheet. Summarization and fact-checking in German happen outside the app.

## Current state

The public repository is [qwertz92/SourceScribe](https://github.com/qwertz92/SourceScribe); its [releases page](https://github.com/qwertz92/SourceScribe/releases) is where signed, installable APKs are published.

For preview 0.4.0, commit `f7dd7d2` built the debug app, both test APKs, and the unsigned release APK;
`tools/sign-release.sh` signed the release APK with the key of 0.1.0, and it is attached to the release.
[Try it](docs/TRY_PREVIEW.md) · [preview release](https://github.com/qwertz92/SourceScribe/releases/tag/v0.4.0) · [verification report](docs/reports/2026-09-16-preview-0.4.md).
At this commit, 215 JVM tests passed, along with the `app` module's instrumented tests (239 run, 230 passed, 0 failed, 9 skipped as opt-in (listed under Skipped), 22:29 to 22:31) and the `extractor`
module's (55 run, 51 passed, 0 failed, 4 skipped as opt-in and run separately right after with their URLs (1 + 1 + 2 + 1 tests, all passed)), on the API 37/x86_64 emulator with 16 KB
pages, `emulator-5556`. This preview answers the owner's 0.2.0 phone-test P1 (every long speech-to-text job used
to stop before reaching a provider) and 15 further points, plus a language-parsing fix found while proving the
pipeline against a real Groq request — the first real provider call this project has made. AssemblyAI and OpenAI
real transcription, and a physical ARM64 phone, are still not run. [STATUS](docs/STATUS.md) separates
implementation, fixture testing, and real-world verification.

CI builds, tests, and lints, and runs the device tests of both modules on an emulator.

The authoritative evidence is kept up to date here:

- [Actual project status](docs/STATUS.md)
- [Known issues by priority](docs/BUGS.md)
- [Learnings log for other agents](docs/LEARNINGS.md)
- [Work history, including every review round](docs/HISTORY.md)
- [Build and personal release](docs/BUILD.md)
- [0.4.0 preview verification report](docs/reports/2026-09-16-preview-0.4.md)
- [0.2.0 preview verification report](docs/reports/2026-09-14-preview-0.2.md)
- [Build/P0 verification report](docs/reports/2026-09-07-build-and-p0.md)
- [License and provenance review](docs/reports/2026-09-07-licenses.md)

## Getting started

The [documentation index](docs/INDEX.md) leads to product scope, architecture, security boundaries, integration contracts, roadmap, and test plan. Read [AGENTS.md](AGENTS.md) before making changes.

The local build entry point is [docs/BUILD.md](docs/BUILD.md). It makes no provider calls and installs nothing on a device. ADB, provider, and other live verification are tracked in the reports at their actual level, as `PASS`, `BLOCKED`, or `NOT_RUN`.

## Technical guidelines

SourceScribe is a native Kotlin/Jetpack Compose app with no server of its own and no separate LLM API for summarization. Sources, configuration, processing state, and exports each have a separate contract. API keys, temporary audio data, and full transcripts belong in none of: Git, logs, diagnostic exports, or test reports.
