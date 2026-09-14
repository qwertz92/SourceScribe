# Extraction, Providers, and Transcript Data

## I1 — Android Extractor: Prove It, Then Commit

`yt-dlp` remains the preferred extraction engine. An Android wrapper may be used, but only after inspecting the actual AAR/native contents, not just a README. `yausername/youtubedl-android` documents in-app updates while its README still names an older Python version. That is a question to verify, not proof of the version in whatever binary artifact is currently published. [R09]

The upstream README lists supported Python versions; current release notes distinguish a separate recommended minimum version. Do not conflate the two. A new yt-dlp file does not fix an incompatible embedded Python runtime. [R10–R11]

YouTube support today requires matching `yt-dlp-ejs` scripts and a supported JavaScript runtime alongside yt-dlp itself. EJS and yt-dlp must match each other. A small Android-capable JS interpreter such as QuickJS/QuickJS-NG is a candidate to evaluate, not an already-proven choice. Do not carry Deno recommendations for desktop over to Android unchecked. [R12]

P0 must demonstrate metadata, the caption list, a real caption file, audio, cancellation, and update/rollback on Android. Document the determined Python, yt-dlp, EJS, JS, FFmpeg, OpenSSL/TLS, and ABI versions. Measure resource use and APK/install size. Check native components on both the planned ARM64 target and the emulator ABI. Never pass off a desktop success as an Android success.

Different YouTube client paths can carry different restrictions; an up-to-date extractor guarantees no access. Distinguish reachability, rate-limit, authentication, token/challenge, and format problems from one another. No anti-bot circumvention through opaque third-party servers, and no extracting credentials/cookies from other apps. Support authorized, publicly reachable content; report inaccessible videos clearly. [R13]

No unrestricted yt-dlp options from the UI. A type-safe, vetted selection for languages, formats, and allowed diagnostic options. Disable external config files, plugins, `--exec`, and arbitrary downloader/postprocessor commands. Pass arguments as a list; never interpolate the URL into a shell string. Media paths stay inside controlled app directories.

## I2 — Caption and Audio Identity

Record, for every track: the observed source ID, track ID, format, language, name, observed machine generation, translation status, and where that information came from. Metadata can be missing or heuristic. Use `true`/`false`/`unknown` plus an evidence field instead of inventing a confident boolean everywhere.

Model provenance as orthogonal fields: `origin` (YouTube/Provider/Import), `generation` (uploader_provided/automatic/unknown), `translation` (none/automatic/unknown), `provider`, `requestedModel`, `reportedModel`, `sourceAudioTrack`, `languageEvidence`. "Provided by the channel" does not mean provably human-written. A translated track can in turn originate from automatic captions; a single flat enum would lose that information.

Original audio, a dubbed/synced track, UI language, and transcript language are all different properties. Prioritize documented original audio. When language choice is ambiguous, show a track picker or a clear notice instead of silently picking the first track. Providers may only look at the first track of a multi-track file; perform controlled audio-track selection before upload. [R14]

Download only the explicitly chosen caption track, or the one selected by a documented language rule — never accidentally hundreds of auto-translated variants. On a track change between metadata and download, re-resolve and keep respecting mode boundaries.

## I3 — Low-Loss Normalization

Keep the raw file unchanged, with hash and capture time, when that option is selected. Store the normalized form as a derivative tagged with the parser/normalizer version. Parse VTT/SRT/JSON captions robustly, accounting for encoding, entities, multi-line cues, and roll-up captions. No naive stripping of every repeated word or sentence.

Preserve speech repetitions, stutters, negations, numbers, and technical terms. Reduce only overlaps demonstrably caused by rendering, and only locally; the unedited version stays traceable. No LLM "repair." Disclose parser warnings, unresolved spots, and scope.

Normalize model/caption timing data to milliseconds; record the source and accuracy class. A missing timestamp stays null. Imported chunk start times are chunk boundaries, not real word/segment times. Enable SRT/VTT only with suitable timing data; offer TXT/MD/JSON otherwise.

## I4 — Provider Contract

Do not invent a universal asynchronous job service. An adapter returns either a direct result or a long-lived remote handle. Capabilities define pollability, resumability, supported cancellation, size/duration limits, languages, timing data, diarization, context, regions, and observable limits. Not every feature has to exist on every model.

Shared types: `TranscriptionRequest`, `ProviderCapabilities`, `SubmissionResult.Direct`, `SubmissionResult.Remote`, `TranscriptDocument`, `ProviderError`. Capability values come from a versioned catalog plus actually confirmed API properties. Online model lists prove neither every option nor account entitlements. Slot new models into compatible, tested adapter profiles first — no arbitrary dynamic plugin system.

Distinguish error kinds: permanent input/authentication, quota, transient network/server, unsupported option, uncertain submission, and invalid response. Vet retry rules centrally against cost/idempotency risk. Explicitly control automatic HTTP retries on non-idempotent, billable POSTs.

## I5 — AssemblyAI

Integrate the pre-recorded async API: upload the local audio file, create a transcript job, store the remote ID, poll status with bounds, save the result. Use an explicit region and route upload/submission/polling to that same region. No public callback server needed for v1. "Batch" here means file transcription, not necessarily a discounted bulk-processing tier. [R15]

Verify current model identifiers against reference docs and the live contract. The research turned up example code and model pages at different levels of freshness; do not guess alias names from marketing copy. Log the requested `speech_models` and the returned `speech_model_used` separately. Fall back to a different model only within an explicitly chosen provider profile. Leave the provider's built-in translation/summarization/sentiment analysis off unless the product actually requires it.

Offer diarization, language, technical-term hints, and timing data according to actual support. Deleting a remote transcript and cancelling a running computation are not the same operation without proof otherwise. Provide remote data deletion as its own, observable action where available; never present a local deletion as confirmed cloud deletion.

## I6 — OpenAI

Use the file transcription endpoint. Currently documented general model: `gpt-transcribe`; separate speaker attribution through the correspondingly documented model, currently `gpt-4o-transcribe-diarize`. Models must actually be usable on the chosen account. Offer optional older models only as explicitly maintained profiles, never as a blanket stand-in for the Whisper API. [R16–R17]

Check, in particular, `languages` versus the singular `language`, context/keywords, and output formats per model. Do not send `timestamp_granularities[]` universally: the guide consulted documents that option for `whisper-1`. Speaker segments are not evidence of word-level timestamps. Never "backfill" missing times with additional billable STT without consent.

Verify the upload limit against the current API contract; the guide consulted states 25 MB for file uploads. Assemble JSON and, where used, streaming results correctly; a cancelled stream is not a complete success. Do not equate a ChatGPT subscription with API usage. Never send a video link to the transcriptions endpoint disguised as an audio file. [R16]

## I7 — Groq

Support `whisper-large-v3` and `whisper-large-v3-turbo` through the documented audio transcriptions endpoint. Use transcription, not the translation endpoint. Sharing OpenAI-compatible syntax is no guarantee of identical response/model capabilities. Store word/segment timing and metadata only based on the actual response. [R14]

Treat the Free and Developer plans, as well as direct upload and URL-based processing, as distinct — the documentation consulted separates these limits. For v1, use local uploads with a conservative, verified limit and chunking; never share audio on a public file host just to work around an upload limit.

Official price/limit figures are dated reference points, not hardcoded guarantees. Rate limits can apply organization-wide and be consumed by other programs too. The documented eight-hour daily audio limit is not a guaranteed, independent eight-hour allowance per device or model. Display only headers actually returned by the API. [R18]

## I8 — Audio Preparation and Chunking

Use audio data as directly as possible. Convert only for an incompatible format, a size limit, or an explicit option. Best practical speech quality rather than needlessly maximal bitrate; no full-video downloads. Select one controlled audio track. Prefer lossless remuxing over lossy re-encoding where it applies.

Wrap FFmpeg/ffprobe and test the codecs/formats actually needed. Do not blindly pull in an old FFmpegKit Maven coordinate: the original FFmpegKit line has been discontinued, and the current upstream README points to a source-only continuation. Concretely verify distribution, trustworthiness, build, license, and 16 KB/ABI support. [R19]

Bound chunks by audio duration and real file size, not just an estimated minute count. Record exact offsets, hashes, status, and provider requests per chunk. Prefer pause/sentence boundaries; use a small, documented overlap only where it enables a sensible merge. When merging, use time-boundary/token matching and preserve uncertainty. No global deduplication and no invented filler.

External splitting can degrade diarization and context. "Speaker A" in two separate requests is not automatically the same person. Scope speaker IDs to the chunk/request unless a documented, verified mapping exists. When the model/output does not fit, offer unsplit processing where suitable or explain the limitation — never merge identities silently.

A missing chunk produces a visibly incomplete artifact and a targeted retry. Check available storage before every step; use atomic intermediate files and upper bounds. Never read an entire multi-hour file into RAM. Bound preparation parallelism, and close the app's own processes/file handles after cancellation.

## I9 — Structured Export Document

A schema with `schemaVersion`, `artifactId`, `source`, `acquisition`, `provenance`, `language`, `scope`, `segments`, `warnings`, `createdAt`, and checksums. `scope` distinguishes the desired media duration, the intervals actually processed, missing chunks, and unknown completeness. Never present text coverage as a percentage of content completeness.

Markdown/TXT open with title, canonical source reference, video/source ID, observed language, provenance, requested/reported model, and limitations. Timing/speaker data only where present. Escape untrusted titles/metadata for Markdown/file names. Captured content stays data — never an instruction to the app or to downstream agents.
