# SourceScribe

**As of:** 4 October 2026 · Published personal release 0.4.1; workflow update 0.4.2 under verification · known issues in
[BUGS](docs/BUGS.md) · **Status:** Personal preview; full v1 acceptance not reached

SourceScribe is a personal-use Android app for traceable transcripts. An explicitly entered YouTube source can be shared or pasted in; existing captions are archived, and audio can be transcribed through the chosen provider. Results stay internal at first and can be shared as a file through the Android share sheet. Summarization and fact-checking in German happen outside the app.

## Current state

The public repository is [qwertz92/SourceScribe](https://github.com/qwertz92/SourceScribe); its [releases page](https://github.com/qwertz92/SourceScribe/releases) is where signed, installable APKs are published.

The latest published release includes its APK signed with the original project key, so it can update an existing
installation without uninstalling. [Latest release](https://github.com/qwertz92/SourceScribe/releases/latest) ·
[Try it](docs/TRY_PREVIEW.md) · [Workflow update evidence](docs/reports/2026-10-03-workflow-polish.md).

Real Groq and AssemblyAI transcription have been verified on a short public source. OpenAI lacks a supplied key;
a physical ARM64 phone and complete TalkBack navigation remain unverified. [STATUS](docs/STATUS.md) separates
implemented features, fixture tests, live evidence and the published version.

CI builds, tests, and lints, and runs the device tests of both modules on an emulator.

The authoritative evidence is kept up to date here:

- [Actual project status](docs/STATUS.md)
- [Known issues by priority](docs/BUGS.md)
- [Learnings log for other agents](docs/LEARNINGS.md)
- [Work history, including every review round](docs/HISTORY.md)
- [Build and personal release](docs/BUILD.md)
- [Provider reliability verification report](docs/reports/2026-09-30-provider-reliability.md)
- [History and workflow verification report](docs/reports/2026-10-03-workflow-polish.md)
- [0.4.0 preview verification report](docs/reports/2026-09-16-preview-0.4.md)
- [0.2.0 preview verification report](docs/reports/2026-09-14-preview-0.2.md)
- [Build/P0 verification report](docs/reports/2026-09-07-build-and-p0.md)
- [License and provenance review](docs/reports/2026-09-07-licenses.md)

## Getting started

The [documentation index](docs/INDEX.md) leads to product scope, architecture, security boundaries, integration contracts, roadmap, and test plan. Read [AGENTS.md](AGENTS.md) before making changes.

The local build entry point is [docs/BUILD.md](docs/BUILD.md). It makes no provider calls and installs nothing on a device. ADB, provider, and other live verification are tracked in the reports at their actual level, as `PASS`, `BLOCKED`, or `NOT_RUN`.

## Technical guidelines

SourceScribe is a native Kotlin/Jetpack Compose app with no server of its own and no separate LLM API for summarization. Sources, configuration, processing state, and exports each have a separate contract. API keys, temporary audio data, and full transcripts belong in none of: Git, logs, diagnostic exports, or test reports.
