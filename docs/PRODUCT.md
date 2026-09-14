# Product scope and behavior

## Goal and boundaries

SourceScribe acquires traceable transcripts for later analysis in ChatGPT. Its primary user is technically fluent, works mainly on Android, and still wants a fast share-sheet workflow. Good defaults, thorough advanced options, and understandable errors all matter equally.

v1 covers single YouTube videos and importing local audio files. The latter uses the same STT pipeline and keeps working even during a YouTube outage. Not in v1: playlists, live ongoing streams, DRM circumvention, cookie extraction from other apps, automatic web search for substitute sources, a Substack scraper, local ML models, or a summarization/fact-check API. Website transcripts are handled in the separate ChatGPT workflow. No hidden translation or text improvement.

## Requirements

### SS-01 — Identical source

Accept URLs via Android share, paste, or typed text; allow several explicitly entered URLs as separate jobs with one shared confirmation. Strictly validate YouTube hosts, canonicalize the video ID, and cross-check it against the resolved metadata. Strip tracking parameters; never silently treat a timestamp as a clip boundary — v1 processes the whole video and shows that it does. When a URL carries both a video and a playlist ID, use only the explicit video; reject a bare playlist. Treat Shorts and completed livestreams as single videos where technically accessible. Do not record an ongoing stream or a premiere of unbounded length before it starts.

Show title, channel, duration, publish date, and thumbnail only when actually resolved. Mark missing metadata as unknown. Re-check the video ID whenever the audio source is re-resolved. Local files get their own source ID, documented file metadata, and a content hash after import, but never an invented YouTube association.

### SS-02 — Four mandatory acquisition modes

| Mode | Behavior | No acceptable YouTube track |
|---|---|---|
| `CAPTIONS_ONLY` — YouTube only | The selected caption track; no audio/STT download | Fails clearly, never falls back to a provider |
| `CAPTIONS_THEN_STT` — YouTube, then STT | An acceptable caption track; otherwise the chosen provider | Acquire audio and start STT, provided that was allowed in advance |
| `STT_ONLY` — STT only | Transcribe the chosen audio track; archive no captions | Not relevant to this mode |
| `BOTH` — Both | Run the caption and STT branches independently | Keep the STT result; leave the missing caption branch open as a partial result |

"Default" simply means adopting the global configuration; it is not a fifth processing mode. "Quality First" and "Auto" do not exist as additional, competing algorithms. Anyone who wants to archive captions while in STT mode chooses BOTH.

Global default on first run: `CAPTIONS_THEN_STT`, original language preferred, channel-provided tracks before auto-generated ones, automatic translation off. A provider is chosen explicitly during onboarding. Without a configured provider, YouTube-only jobs remain possible; a fallback that cannot run is shown before the job starts.

Deliberately choosing the cloud provider and starting the specific job together count as authorization for the audio submission that mode and cost budget require. There is no separate, general submission switch. The internal binding of that authorization to the source and configuration snapshot, and the ban on silent provider switching, both still apply. This describes product behavior — it does not authorize development agents to run real provider tests.

A confirmed absence of acceptable captions is not the same as an HTTP 429, an offline state, or a parser error. For such retrieval errors, retry a limited number of times first, then ask the user or pause by default. The user may optionally allow STT fallback for this case too, globally or per job; the decision is stored with the job. Never re-transcribe automatically just because of a health signal.

### SS-03 — Global defaults, presets, and job overrides

Settings cover mode, allowed caption types, language preferences, whether translation is allowed, provider/model/region, supported STT options, export formats, target folder, audio retention, network policy, and parallelism. A choice made before starting a job may override the global defaults without changing them.

Save the resolved configuration as an immutable snapshot at start; a running job does not change when settings change afterward. Reference credentials rather than copying them in. Saved named presets use the same configuration type. Reasonable examples: "YouTube first," "New STT," "Archive both." Every preset uses the fixed mode and the same validated options — no extra rule or scripting language. No preset names like "100% accurate" or "guaranteed free."

Quick controls: mode, provider/model, and preset. Advanced options are collapsible. In YouTube-only mode, no confusingly active provider switch. An explicit audio-track and caption-track picker appears once several suitable tracks exist. Keep the original language and the selected audio version distinct; never silently label an auto-synced or auto-translated audio track as the original source.

### SS-04 — Three STT providers

Fully integrate AssemblyAI, OpenAI, and Groq, but present options according to what each model actually supports. German/English and automatic language detection are core use cases. Offer speaker diarization, word/segment timestamps, and context terms only where supported. Explain incompatible combinations before uploading. The user configures their own account; the app ships no shared API keys.

Existing AssemblyAI credit can be used; Groq is a selectable low-cost alternative. There is no fixed, universal accuracy ranking. No automatic fallback to a different provider: submitting audio and incurring possible cost both require a policy chosen explicitly in advance. Model/account access must be verified; successfully listing available models alone does not prove that transcription will succeed.

### SS-05 — History and multiple jobs

A persistent history with search, source, date, provider/model, phase, result/export status, and an understandable error view. Retry, retry only the missing branch, cancel, re-run with a different provider, and delete. Keep a re-run as a new attempt; never overwrite a completed result.

1-4 simultaneously active jobs, configurable, default 2. CPU-intensive audio preparation defaults to one at a time. Respect provider limits separately. Sharing the same link twice must never silently trigger another billable job: show the existing job and offer a new run explicitly. Different configurations and deliberately wanted comparisons both remain possible.

### SS-06 — Background execution and honest state

Navigation, the screen turning off, and a normal process restart must never lose history. Work continues as far as the OS allows, or resumes from safe checkpoints. After the user force-stops the app, never promise silent self-reactivation. On the next open, reconcile state and continue in an understandable way.

Show phase and real byte/chunk progress. Without a percentage from the provider, show "provider processing" with elapsed time, not an invented progress bar. Distinguish waiting states for network, user decision, quota, device unlock, and export permission. In BOTH, a successful artifact can be opened immediately while the other branch is still running.

### SS-07 — Provenance and result quality

Document YouTube auto-captions, channel-provided captions, automatic translation, external STT, and local files as distinct provenance types. Uploader-provided does not mean proven to be manually created. Leave unclear information as unknown. Never force provider and model into the same field as caption provenance.

Store the original data on request; keep derived, cleaned-up text separate. No LLM correction, no apparently-best word choice merged from two transcripts. Neither readability nor text agreement proves accuracy. Structural warnings may flag loops, missing chunks, broken timestamps, or an empty result, but must never produce a fabricated WER/accuracy score. Genuine speech repetitions must never be deleted globally.

### SS-08 — Storage and exports

Keep one canonical, permanently stored version internally. Choose external target folders through the Android Storage Access Framework, never treat one as an unchecked filesystem path. Markdown is the default; TXT, structured JSON, and SRT/VTT when suitable timing data exists. A partial result stays recognizable there too, since a player only shows cues: a notice cue at the start, and separate cues over untranscribed sections. Raw captions are an additional output. Preserve the actual raw format; label any conversion.

If folder permission is lost, the transcript stays available internally and gets `EXPORT_PENDING`/`FAILED` — never a new STT request. Export is repeatable and avoids collisions. Filenames include the video/source ID, source/model, and language, since the title alone is not unique. Export metadata stays meaningful even after renaming.

Audio retention: only technically necessary temporary files, kept until the transcript is durably persisted (the default), or kept permanently if explicitly chosen. A storage ceiling and safe cleanup apply; never delete active files or successful transcripts as cache. "Never retain" does not mean "never write to disk during processing."

### SS-09 — Viewer and sharing

Display long transcripts without blocking the UI; search, copy, and share as a file through Android Share. Provenance, source, language, timing/speaker data, and warnings all stay visible. BOTH shows separate artifacts, with no automatic merging. A switchable view comes first; side-by-side is optional on large displays. A full algorithmic diff is a lower priority.

No promise that Android will automatically file a document into a "Summarize" ChatGPT project. The standard share sheet and a saved file are the reliable interface. No separate OpenAI LLM key is needed just for summarization.

### SS-10 — Security and component updates

Store API keys securely; redact diagnostic exports; no telemetry or third-party analytics. Verify the provenance of update code; update and roll back compatible yt-dlp/EJS packages independently of the APK, to the extent this is technically demonstrated. Native runtimes and incompatible changes may still require an APK update. Exact release and failure rules are in `docs/SECURITY_UPDATES.md`.

### SS-11 — Cost, limits, and transparency

Before a cost-relevant job, show the estimated audio duration and the available pricing information with its date and rate. Show "unknown" for an unknown price, never zero. Personal local budgets conservatively cap new submissions; they do not replace provider billing. Overlap, retries, and provider rounding can cause extra usage. A remote job can keep running and being billed after a local cancellation.

Local usage counting is only a partial view from this device, not a binding organization-wide quota display. Queue on a rate limit instead of looping forever; honor `Retry-After`. Never make a paid upgrade or a budget change automatically.

### SS-12 — Usability and evidence

German and English as explicitly selectable app languages (`de`/`en`) with systematic resource localization, independent of the transcription language. System/light/dark theming, readable typography, large enough touch targets, TalkBack semantics, font scaling, and clear empty/error states. Bottom buttons need visible spacing; the acquisition, provider, and model selection fields should stay compact, without truncated content or layout shifts at larger font sizes. Elaborate graphics must never crowd out function or performance. Read the clipboard only after a user action, never monitor it continuously. Show running jobs through appropriate notifications; handle denied permissions in an understandable way.

A complete repository, a traceable build guide, CI, test reports, a debug APK, and a release path that can be signed reproducibly for personal updates. Full v1 acceptance follows `docs/TEST_PLAN.md`, not the mere existence of screenshots.
