# Research, Sources, and Uncertainties

**As of: September 5, 2026.** The linked primary sources were researched for this revision. No provider accounts were read here, and no Android binaries were built or tested on an emulator. Marketing pages, READMEs, release notes, and API schemas can be at different levels of freshness; verify implementation-critical statements against the chosen version and the real contract.

The architecture requirements are design decisions. The entries below justify the relevant technical constraints. Prices and quotas are never carried into product rules as eternal constants.

## R01 — Native Android UI

[Android: Jetpack Compose](https://developer.android.com/compose)

Compose is the officially recommended modern toolkit for native Android UIs. That doesn't automatically dictate every app's architecture; for SourceScribe, the Android-specific integrations further support Kotlin/Compose.

## R02 — Svelte

[Svelte: Overview](https://svelte.dev/docs/svelte/overview)

Svelte is a web UI framework. SourceScribe needs no additional web UI; the decision against Svelte concerns this Android-centered scope, not its general quality.

## R03 — Capacitor

[Capacitor: Documentation](https://capacitorjs.com/docs)

Capacitor connects web applications to native platform functionality. It would be a possible alternative, but it doesn't remove the requirements for Android services, native extraction, and storage access. The extra bridging overhead is avoided here.

## R04 — Native Libraries and 16 KB Pages

[Android: Support 16 KB page sizes](https://developer.android.com/guide/practices/page-sizes)

Dependencies with native code require matching build, alignment, and runtime tests. That's why Python, JS, and FFmpeg binaries count here too, not just the Kotlin code. Actually measure the provided test device's ABI and `PAGE_SIZE`; don't adopt any specific Play Store deadline as a fixed product requirement.

## R05 — Long-Running WorkManager Workers

[Android: Support for long-running workers](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running)

Android documents that, starting with Android 16, long-running workers can exhaust job quota even with a foreground service, and names alternatives for suitable use cases. WorkManager is not a blank check for unlimited background execution.

## R06 — Foreground Service Limits

[Android: Behavior changes for apps targeting Android 15](https://developer.android.com/about/versions/15/behavior-changes-15)

Certain service types are subject to runtime/start conditions and timeout handling. The six hours for `dataSync` are a version-/target-dependent execution budget, not a limit on how long an externally transcribed audio file may be. Keep cloud waiting time and active on-device work separate for that reason.

## R07 — User-Initiated Transfers

[Android: User-initiated data transfer](https://developer.android.com/develop/background-work/background-tasks/uidt)

A potentially suitable API for longer, directly user-initiated data transfers. Verify availability and usage conditions against the chosen Android version; don't use it as a universal replacement for regular jobs.

## R08 — Storage Access Framework

[Android: Access documents and other files from shared storage](https://developer.android.com/training/data-storage/shared/documents-files)

Document/tree URIs and permissions enable controlled target folders. A document URI is not an ordinary local path; access can be lost, and individual providers support different operations. Keep internal result storage separate from export.

## R09 — Android Wrapper for yt-dlp

[youtubedl-android: README](https://github.com/yausername/youtubedl-android/blob/master/README.md)

Documents `updateYoutubeDL` with stable/nightly options, while the same README still names an older Python version. The concrete published AAR contents were not inspected for this package. So there's no claim here that any particular latest binary is already compatible or safe. Check the wrapper's/fork's license and update source.

## R10 — yt-dlp Dependencies and Channels

[yt-dlp: README](https://github.com/yt-dlp/yt-dlp/blob/master/README.md)

Documents supported Python versions, dependencies, subtitle options, and release channels. As read: CPython 3.10+ and PyPy 3.11+ as supported versions. Distinguish these from the recommendations in the release notes.

## R11 — Python Recommendation in Release Notes

[yt-dlp: Releases](https://github.com/yt-dlp/yt-dlp/releases)

The release notes consulted name a recommended minimum version raised to Python 3.11, and an upcoming support change. Don't equate "recommended" with "already technically required." Actually measure the native runtime version inside the Android artifact.

## R12 — EJS and JavaScript

[yt-dlp: EJS setup guide](https://github.com/yt-dlp/yt-dlp/wiki/EJS)

[yt-dlp-ejs: Repository](https://github.com/yt-dlp/ejs)

The YouTube extraction chain includes JavaScript challenge scripts and supported runtimes. EJS must match the yt-dlp version. A simple update of just the yt-dlp file is therefore not a complete compatibility strategy. Whether a specific runtime is suitable for Android remains a question for P0.

## R13 — Limits of Current YouTube Extraction

[yt-dlp: PO Token Guide](https://github.com/yt-dlp/yt-dlp/wiki/PO-Token-Guide)

Upstream documents further client-/access-dependent requirements. A new release doesn't automatically clear every download failure. This doesn't specify a circumvention feature — it calls for differentiated error handling and an honest access limit.

## R14 — Groq Speech-to-Text

[Groq: Speech to Text](https://console.groq.com/docs/speech-to-text)

Documents `whisper-large-v3` and `whisper-large-v3-turbo`, output/timing formats, upload differences, and chunking. Reference prices as read: USD 0.111 and USD 0.04 per audio hour, respectively. The documented file/attachment/URL limits and tiers must be treated separately. For v1, direct uploads only, no public audio host.

## R15 — AssemblyAI

[AssemblyAI: Submit a transcript](https://www.assemblyai.com/docs/pre-recorded-audio/api-reference/transcripts/submit)

[AssemblyAI: Models](https://www.assemblyai.com/docs/getting-started/models)

[AssemblyAI: Homepage](https://www.assemblyai.com/)

Async transcription with a remote ID suits the product. The pages/examples retrieved were partly at different model states: the homepage advertises Universal 3.5 Pro, while some example code names Universal 3 Pro. Verify current schema values and actual acceptance; don't guess model names from marketing copy. Keep the requested and the reported model separate.

## R16 — OpenAI File Transcription

[OpenAI: File transcription](https://developers.openai.com/api/docs/guides/speech-to-text)

The guide consulted uses `gpt-transcribe`, documents a 25 MB file limit, and model-dependent options. Don't swap `languages` and `language` interchangeably. `timestamp_granularities[]` is documented there for `whisper-1`, not as a universal option. Provider/model profiles need real contract tests.

## R17 — OpenAI Models

[OpenAI: GPT-Transcribe](https://developers.openai.com/api/docs/models/gpt-transcribe)

[OpenAI: GPT-4o Transcribe Diarize](https://developers.openai.com/api/docs/models/gpt-4o-transcribe-diarize)

The general transcription model and the diarization-capable model are documented separately. GPT-Transcribe reference price as read: USD 0.0045 per minute. That proves neither account access nor identical speaker/timestamp capabilities. Never treat a ChatGPT subscription fee as API credit.

## R18 — Groq Rate Limits

[Groq: Rate Limits](https://console.groq.com/docs/rate-limits)

The table lists, for both Whisper models on the Free plan, 7,200 audio seconds per hour and 28,800 per day — i.e., two and eight hours, respectively — plus request limits. Per the documentation, limits apply organization-wide. Don't interpret them as guaranteed, independent daily budgets per model/device or as a binding billing counter. Account terms and the headers actually returned remain authoritative.

## R19 — FFmpegKit Status

[FFmpegKit: current README](https://github.com/arthenica/ffmpeg-kit/blob/main/README.md)

The README carries an update notice from July 2026: the original FFmpegKit line is discontinued, with FFmpegKitNext as a source-only continuation. For the actual app, don't blindly pick either historical binary coordinates or an arbitrary fork. Build/ABI/license/trust verification remains open until P0.

## R20 — Dynamic Code Loading

[Android: Dynamic Code Loading](https://developer.android.com/privacy-and-security/risks/dynamic-code-loading)

Android describes integrity, tampering, and platform risks. The personal sideload app makes a vetted component-update path plausible, but not risk-free or automatically Play-compatible.

## R21 — Permissions of Dynamically Loaded Code

[Android: Security checklist](https://developer.android.com/privacy-and-security/security-tips)

Dynamically loaded code runs with the app's own security permissions. So a separate process with the same UID and encrypted keys are not complete protection against a malicious update. Signature/publisher verification and restricted sources remain central.

## R22 — Android Keystore

[Android: Keystore system](https://developer.android.com/privacy-and-security/keystore)

Non-exportable keys protect the stored encryption foundation. Availability, device binding, and key invalidation must fit the background workflow. Don't infer any protection guarantee for a plaintext API key inside an already-compromised app.

## R23 — Codex Project Instructions

[OpenAI: Custom instructions with AGENTS.md](https://developers.openai.com/codex/guides/agents-md)

Documents that AGENTS.md is read in as project-level guidance. Hence a short, durable set of rules in the repository plus separate product/architecture documents, rather than just one long chat prompt. The actual working environment must be able to access these files.

## R24 — Codex Subagents

[OpenAI: Subagents](https://developers.openai.com/codex/multi-agent)

Documents delegated, bounded work and flags coordination risks from parallel writes. Hence independent reviews and clearly assigned code areas; no rigid number of "superagents" and no invented delegation.

## R25 — Force-Stop Is Its Own State

[Android: Behavior changes, all apps, Android 15](https://developer.android.com/about/versions/15/behavior-changes-all)

The stopped state is meant to persist until the corresponding user interaction. The app should reconstruct state at the next start, not claim to secretly survive every deliberate stop.

## R26 — Contextualize the Earlier WER Figure, Don't Turn It Into a Product Rule

[Original analysis by a transcription vendor: YouTube Auto-Captions](https://youtube-transcript.ai/blog/youtube-auto-caption-accuracy-study)

The figure mentioned earlier can be traced: an analysis dated August 19, 2026, covering 264 English videos, with a 9.9% median WER. It comes from a transcription-tool vendor and uses provided creator captions as the reference. It is not a controlled head-to-head comparison against Groq on the same material, and it proves no universal quality ranking. Nor is `1 − WER` automatically a general accuracy guarantee. Hence no such ranking or quality-percentage display in SourceScribe.

## Still to Verify in Practice

The Android wrapper/runtime combination, including EJS; actual hot-update artifacts and signature verification; job driver per SDK; model/account capabilities; remote cancel/idempotency/deletion per provider; SAF behavior of the chosen document provider; ARM64/emulator/16 KB runtime behavior. These open points are P0/integration tasks, not properties already tested and confirmed.
