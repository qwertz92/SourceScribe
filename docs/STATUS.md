# Project Status

**As of 2026-09-14.** Current release: personal preview `0.2.0-preview.1`. Full v1 acceptance is not reached
yet. App source is the tip of `main`, public repo
[qwertz92/SourceScribe](https://github.com/qwertz92/SourceScribe). Between the two previews, 23 rounds of
independent adversarial review (Sonnet-5 agents, each reviewing the previous round's fixes) ran over code and
docs. The round-by-round findings are not repeated here — see `git log -p -- docs/STATUS.md` for that
history, and [LEARNINGS.md](LEARNINGS.md) for the lessons it produced.

## What it does today

A native Android app (Kotlin, Compose, Material 3) that fetches YouTube captions or transcribes audio via
AssemblyAI, OpenAI, or Groq, across four acquisition modes, with immutable per-job configuration snapshots, a
parallel job queue, process-death recovery, protected credential storage, full provenance/history, SAF-based
export, and signed, rollback-capable updates to the bundled extraction engine (yt-dlp). No summarization API,
no web frontend, no backend service of its own. Full requirements: [PRODUCT.md](PRODUCT.md) (SS-01 to SS-12).
Phase definitions: [ROADMAP.md](ROADMAP.md) (P0-P6).

## Status by phase

| Phase | Implemented / fixture-tested | Live-verified / blocked |
|---|---|---|
| P0 Feasibility | Runtime, verifier, manager; WebP rebuilt for both ABIs with 16 KB page alignment | Python/TLS, JS/EJS, FFmpeg, exact YouTube metadata/caption/audio, and update/rollback are all live-verified. Physical ARM64: not run yet; needs the owner's phone. |
| P1 Real pass-through | URL/planner, caption provenance, Room, export/viewer | Live-verified end to end: Android Share -> real caption -> internal storage -> SAF Markdown (content-compared) -> share dialog. |
| P2 First STT pipeline | Groq, local import, audio prep/chunking, credentials, submission limits | Real Groq transcription: not run yet; needs the owner's API key, which agents may not enter. |
| P3 Full acquisition | AssemblyAI, OpenAI, all four modes, `BOTH` partial-failure handling, language/tracks/options/presets | Real AssemblyAI/OpenAI transcription: not run yet, for the same reason; a mock response is never accepted as a provider pass. |
| P4 Reliability | Queue/limits, recovery, unsafe submissions, export repair, update failures | Live-verified: Android process/permission boundaries, reboot recovery with a provider fixture, a real signed engine update plus rollback, two concurrent fixture jobs. |
| P5 UX and delivery | German/English app language, system/light/dark theme, reworked selection fields and navigation, viewer/search/copy/share, formats, diagnostics, signing path | ADB/screenshot checks incl. 200% font size and landscape: pass. Durable personal release signing and install: pass. Full TalkBack operation: **blocked**, no suitable real-input automation available. |
| P6 Acceptance | Integrated regression plus 23 rounds of independent review; every confirmed defect fixed with a regression test | Full acceptance not reached: live provider runs and a run on a physical ARM64 phone (both need the owner) and full TalkBack coverage are still missing. |

Of the 20 items from the user's 2026-09-10 device test, 17 are implemented and verified on-device or by test;
the 3 that remain open are tracked in [BUGS.md](BUGS.md) and [DEFECTS.md](DEFECTS.md).

## Releases

**0.2.0-preview.1** — built 2026-09-14 at commit `1fe2dad` (the last commit that changes code in round 23);
tag `v0.2.0-preview.1` sits on the following documentation-only commit `1118adc`. Version 0.2.0, versionCode 2.

- Unsigned release APK: `.local-tools/releases/SourceScribe-0.2.0-preview.1-1fe2dad-unsigned.apk`,
  146,638,298 bytes, SHA-256 `339e326e5abce460018f370443237f8f6d5e98fb1b7658c902fdc1148b7e60b4`.
- Device tests ran against the debug APK of the same commit, 157,997,263 bytes, SHA-256
  `3762b0cc0cfb5b0f8b47b76dcf8c441dcc7dfcdf49c2c57e3dd130719ae2ddb3`.
- Signed on 2026-09-14, after the tag, with the release key of 0.1.0 (certificate SHA-256
  `19d1da9a8fe704082a531faed8a24d966c485aae581a7076dd4b4f66c11d3881`) through `tools/sign-release.sh`, and
  attached to the GitHub release as `SourceScribe-0.2.0-preview.1.apk`, 146,688,333 bytes, SHA-256
  `ea8bf17fa1262c5d4869ad7a5db9f6183b54746714f82ac46c7c031c449f971b`. Installing it as an update over 0.1.0 on
  a real phone has not run yet.
- Full evidence: [2026-09-14 preview report](reports/2026-09-14-preview-0.2.md). What's new and how to try
  it: [TRY_PREVIEW.md](TRY_PREVIEW.md).

**0.1.0-preview.1** — built and gated 2026-09-08; tag `v0.1.0-preview.1` at commit `f1a791c`. Version 0.1.0,
versionCode 1.

- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`, 159,065,550 bytes, SHA-256
  `ad94d85da0b1fc3cf3bbc885b3f3fa2857806448d4e20524a3c5971e3a887b32`.
- Personally signed release APK: `SourceScribe-0.1.0-preview.1.apk`, 147,376,546 bytes, SHA-256
  `410b654fd08bdb2b7f8142dd88f440b7f49ffe123e363dd3607582b9e855c20a`. No longer present under
  `app/build/outputs/`; hash and certificate are recorded in the report below.
- Full evidence: [2026-09-08 preview report](reports/2026-09-08-preview.md). Earlier failed attempts and
  review fixes: [2026-09-07 integration report](reports/2026-09-07-integration.md).

## Latest verified gates (2026-09-14, commit `1fe2dad`)

- 187 JVM tests in `core`, 0 failures.
- `app` instrumentation on `emulator-5556`: 208 tests, 202 pass in the combined run plus 6 opt-in skips. 4 of
  those 6 are `ProcessRecoveryTest` process-death stages that must each run as its own invocation; all four
  pass individually. The remaining 2 (a data-seeding helper that is not a real test, and a test that needs a
  real signed engine release) do not run at all.
- `extractor` instrumentation on `emulator-5556`: 50 tests, 46 pass, 4 opt-in skips that need either a real
  video source or a real signed engine release, and do not run at all.
- Total: 445 test methods across the three modules; 439 have actually executed and passed, matching the 6
  named above that have not.
- All 4 lint reports (`app` and `extractor`, debug and release): 0 issues. Lint is configured with
  `abortOnError` and `warningsAsErrors`.
- `tools/check-repository.py`, including its self-test: pass.
- Static checks on the unsigned release APK: pass.
- CI run [34865638431](https://github.com/qwertz92/SourceScribe/actions/runs/34865638431) on commit
  `1fe2dad`: green, including the device step for both `app` and `extractor`.

## Open issues and evidence

Known open issues, with impact and priority, are tracked in [BUGS.md](BUGS.md); as of today no P1 (critical)
issue is open. The underlying technical write-up for each item is in [DEFECTS.md](DEFECTS.md). Raw
build/device/licensing evidence is under [docs/reports/](reports/). Planned next steps and the current
blocker table are in [NEXT_STEPS.md](NEXT_STEPS.md).
