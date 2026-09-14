# Architecture and Technical Decisions

## A1 — Native Android App

**Decision:** Kotlin, Jetpack Compose, and Material 3. ViewModels expose immutable UI state via StateFlow; coroutines handle asynchronous work. Room stores job/artifact metadata, DataStore holds non-secret settings. OkHttp and kotlinx.serialization handle provider and metadata traffic. Hilt covers the app's modest dependency wiring; no additional custom DI platform.

The app needs share intents, durable background work, the Storage Access Framework, protected keys, and native processes. That rules out a Svelte/Capacitor frontend: a web UI would not remove these native requirements, only add another bridge. This is a project-specific call, not a general verdict against Svelte. A future web client or backend would be its own architecture decision, not a precautionary v1 component. [R01–R03]

Three Gradle modules to start: `:app` for UI/Android integration/persistence/scheduling, `:core` for JVM-testable models/rules/parsers, and `:extractor` for the Android/native integration boundary. Provider adapters live in separate packages; split into further modules only once there is a demonstrated need. A separate module is not an OS sandbox. No unnecessary microservices, no Kubernetes, no Node process, and no Python UI.

**Build proposal:** minSdk 29; compileSdk/targetSdk on whichever stable SDK version is current at implementation time, not a preview. Determine, record, and verify the actually compatible set of JDK, Gradle, AGP, Kotlin, and Compose BOM versions — current stable releases don't mean every latest individual version combines freely. Target `arm64-v8a` for the real phone and `x86_64` for a typical desktop emulator; measure the actual ADB ABIs and page size. No blanket assumption based on an emulator's name alone. Also check native libraries for 16 KB page-size compatibility. [R04]

## A2 — Small, Clear Boundaries

`SourceResolver`: validates input and determines canonical identity.

`ExtractorEngine`: fetches metadata, tracks, and the audio file for the exact source ID. The rest of the app never sees wrapper-specific types or free-form shell commands.

`AcquisitionPlanner`: translates resolved configuration into a deterministic plan. Only this component decides whether captions, audio, and/or STT are required.

`ProviderAdapter`: capability-dependent transcription, returning typed direct results or remote handles.

`JobCoordinator`: long-lived state, claims, checkpoints, cancellation, and resumption. Android execution mechanisms are interchangeable drivers, not the data model.

`ArtifactFiles` and `ExportStore`: strictly separate internal result storage from writing to external document URIs; `TranscriptExporter` produces export formats without writing them itself.

`EngineUpdateManager`: trust-verified, compatible component packages and their activation; not part of the regular provider path.

The UI knows use cases and states, not request bodies, API keys, yt-dlp paths, or FFmpeg commands.

## A3 — Data Model

| Entity | Purpose |
|---|---|
| `Source` | UUID, type, original/canonical URL, video ID or file hash; observed metadata |
| `JobRow` | User request, immutable configuration snapshot, desired branches, creation time |
| `AttemptRow` | Execution attempt per branch: versions, options, stage, request state, checkpoints, errors |
| `ArtifactRow` | Immutable, internally persisted result with provenance, scope, quality warnings |
| `ExportRow` | Format, target URI, content version, write status, and export errors of an artifact |
| `SubmissionRow` | Provider, account reference, region, audio/configuration hash, submission state, and remote ID |
| `EngineInstallation` | Package identity, versions/hashes, trust evidence, health state, usage/pinning |

The names in A2 and in this table are the ones actually used in code. The first version of this document (`758186b`, September 7, 2026) called them `ArtifactStore`, `Exporter`, `ExtractorUpdateManager`, `Job`, `Attempt`, `TranscriptArtifact`, `ExportRecord`, and `SubmissionRecord`; none of those names occur in the code. Aligned on September 13, 2026, because `AGENTS.md` requires reading this document before changing its area. The prose below the table still uses "job" and "attempt" as terms, not as type names.

Store requested model names separately from what the provider actually reports. Without a reported snapshot, never invent a supposedly exact model version. Log fallback lists and the alternative actually chosen. Store timestamps in UTC; display them locally, with a fixed time zone in export metadata where relevant. Configuration snapshots hold no secrets; key rotation goes through a credential reference.

A source can have many jobs. A job can have multiple attempts and artifacts. BOTH is therefore not a special case with two file paths in a single job row. Re-exporting never triggers a new transcription. A deliberate re-transcription creates a new attempt, never a mutation of a finished transcript.

## A4 — States Without Losing Meaning

Three independent dimensions:

**Execution:** `QUEUED`, `RUNNING`, `WAITING_NETWORK`, `WAITING_RATE_LIMIT`, `WAITING_USER`, `WAITING_REMOTE`, `SUBMISSION_UNCERTAIN`, `FINISHED`, `CANCELLED`.

**Phase:** resolve, fetch_captions, download_audio, prepare_audio, upload, submit, retrieve, normalize, persist. Phases describe work, not success.

**Outcome:** `NONE`, `SUCCESS`, `SUCCESS_WITH_WARNINGS`, `PARTIAL_SUCCESS`, `FAILED`, `CANCELLED`. Export status is tracked separately: `NOT_REQUESTED`, `PENDING`, `WRITING`, `EXPORTED`, `PERMISSION_REQUIRED`, `FAILED`.

`SUCCESS` requires that every transcript branch requested in the plan was obtained in full, to the technically verifiable extent, and safely persisted internally. Missing audio chunks or known retrieval gaps produce `PARTIAL_SUCCESS`, even if a readable file exists; unknown content completeness stays explicitly unknown. BOTH with only one successful branch is `PARTIAL_SUCCESS`. A structural warning is not a missing branch. A successful internal transcript with a failed export is shown as "transcript available; export failed," never simply as "all done." Cancellation does not automatically delete results already produced.

All transitions go through traceable, tested rules; no arbitrary strings scattered across workers. Transactional claims/leases prevent concurrent handling of the same step. An expired lease permits state reconciliation but does not prove that an external, billable submission never went out.

## A5 — Execution on Android

No worker loop ever sleeps for hours at a stretch. Plan short, persisted work steps; keep downloads/uploads and CPU-bound processing clearly separate from passively waiting on provider results.

Use WorkManager for durable, deferrable work, recovery, and bounded polling. For longer, directly user-initiated transfers, evaluate user-initiated data transfer jobs on supported Android versions. Use foreground services only with the matching type, start conditions, notification, and timeout handling. No abuse of `mediaPlayback`, alarms, or endless wake locks. [R05–R07]

Starting with Android 16, even long-running foreground workers can consume job quota, and version-/target-dependent runtime limits also apply to `dataSync` and `mediaProcessing`. The P0 proof must therefore pin down the concrete scheduling strategy for the chosen SDK/device combination. Do not write "WorkManager guarantees unlimited background execution" into the documentation. [R05–R06]

Remote polling: store the ID and the next poll time, release execution, and run a bounded poll once it is due. No `PeriodicWorkRequest` with assumed second-level accuracy. The visible UI may poll faster briefly; background timing stays OS-controlled. Passive cloud processing consumes no audio CPU slot and does not needlessly keep the radio awake.

Treat OS process loss, swiping the app away, a task-manager kill, an explicit force-stop, reboot, and device lock as distinct cases. After a force-stop, reconstruct state only at the next permitted app start. [R25] Wait while credential/file storage is not yet accessible. On cancellation, terminate HTTP calls and the app's own native subprocesses deliberately; never kill someone else's process. Remote cancellation is capability-dependent and does not imply a refund.

**Concurrency:** a configured job limit of 1–4, separate per-provider limits, one audio-preparation process by default. Controlled through durable job data plus bounded execution resources, not just a volatile global semaphore. Retries get backoff with jitter and caps. Progress means real byte, chunk, or step progress — never an estimated provider percentage.

## A6 — Resume Safely Instead of Blindly Resending

Before any billable submission, persist the submission intent together with the provider, input hash, and options. Save the remote ID immediately after confirmed acceptance. Use documented idempotency mechanisms where they exist; never infer idempotency from the mere existence of an HTTP POST or a made-up header.

A timeout after possible acceptance becomes `SUBMISSION_UNCERTAIN`. Reconcile first through documented means. When a synchronous transcription cannot be recovered after process loss, a repeat transcription may become necessary; possible double billing must be surfaced before any retry. Never claim a general exactly-once guarantee for external APIs.

Persist a provider response already received to an app-internal, bounded spool before any expensive or error-prone follow-up operation, so parser or export failures never force another submission. Content data stays private and follows a deletion policy. Upload URLs can expire; keep the source ID and controlled local files around long-term rather than assuming signed URLs stay valid.

## A7 — Result Storage and External Folders

The canonical structured transcript is a versioned JSON file in the app's internal files area; Room holds a reference, hash, metadata, and optionally a rebuildable search/segment index. The index is never a second, competing source of truth. Store raw captions/provider data separately, and only according to the chosen retention setting.

Local write order: a unique temporary file, write/close it fully with the required sync, finalize atomically on the same file system, then publish the database reference. Provide reconciliation for a crash between the file operation and the DB operation. Never claim that the file system and SQLite automatically form one shared transaction.

SAF targets are `content://` URIs; never convert them into imagined `/storage/...` paths. Take persistable permission only to the extent it is actually granted. Provide a testable abstraction over `ContentResolver`/`DocumentsProvider`. External providers can support rename, read, or atomic operations differently; never guarantee universal atomicity or cloud sync. A write counts as done only once it has closed successfully; where readable, verify hash/byte count and record the verification level achieved. Clean up failed partial files in a controlled way, keeping the internal result intact. [R08]

Use `FileProvider` with time- and scope-limited read grants for sharing; no `file://` URIs and no globally open exports. Stream large files; never load hours of audio as a full `ByteArray`. WorkManager gets IDs, not audio, transcript text, or keys. Respect cache limits; do not move resumption data the app still needs into a cache the OS can evict at will.

## A8 — Decisions Deliberately Left Open

The P0 test decides the Android wrapper, the bundled Python/JS runtime, the FFmpeg distribution, a compatible hot-update package, and the concrete scheduling assignment. This is not an invitation to stay in research indefinitely: each item needs a candidate, an actually executed test, a result, and a decision. Contracts and product scope stay stable. Unverified upstream binaries or uncontrolled third-party servers are not an acceptable shortcut.

Record architecture decisions afterward, briefly, under `docs/adr/`: context, alternatives, decision, consequences, measured versions, and tests. Never carry over API/tool versions from this spec without checking them.
