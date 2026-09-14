# Security, Updates, and Personal Distribution

## S1 — Threat Model and Trust Boundaries

What needs protecting: API keys, audio/transcript content, personal source history, local files, provider budgets, and the integrity of the executable code. Untrusted: pasted links, shared content, titles/subtitles, media, API responses, and downloaded update artifacts. HTTPS alone does not make third-party code trustworthy.

The app runs third-party code for extraction and audio processing. A process with its own PID but the same Android UID stays within the same app trust boundary. Do not claim that a separately started Python process or encrypted API keys would stop a compromised extractor from reaching app data. Android explicitly states that dynamically loaded code runs with the app's own permissions. [R20–R21]

For the personal v1, this residual risk is made transparently acceptable through restricted sources, verified packages, minimal options, current vetted runtimes, and separated data flows. The keystore protects stored keys, not every later plaintext use inside an already-compromised app. Genuine isolation with its own UID/isolated service and controlled I/O would be a separate design that still needs practical validation — not a free side effect of running an extra process.

## S2 — Secrets and Data

Encrypt API keys with a non-exportable Android Keystore key, e.g., AES-GCM with a fresh nonce per encryption. Only ciphertext and metadata live in private app storage; no plaintext in Room, DataStore, BuildConfig, resources, WorkManager data, or process arguments. Bind the credential ID and provider region in as well. Use currently maintained Android/crypto libraries; no home-grown cryptography. [R22]

For unattended jobs, it must be clear whether a key may be available without a fresh biometric prompt. The default may allow background jobs after a normal device unlock. Handle keystore invalidation, device transfer/restore, and a missing unlock understandably. Never fall back to plaintext storage when decryption fails.

Mask key entry, and limit sensitive screenshots/task previews where reasonable. No secrets in test reports or ADB command lines. Debug and release logging redact authorization headers, signed media/upload URLs, context terms, and full provider responses. Diagnostic exports contain error categories, versions, and pseudonymized IDs by default — not audio, transcript, URL tokens, or keys. Show the export to the user before it is shared.

Disable automatic cloud backup for secrets and sensitive app data by default, or exclude them explicitly; verify the actual backup/device-transfer rules. Restoring an old encrypted blob without its device-bound key is not a working credential backup. Preset export without secrets, yes; full data/key backups are not in v1.

Internally stored transcripts are app-private, but without a separately implemented vault, do not call them application-encrypted. External TXT/MD/JSON exports are plain readable documents; when the chosen `DocumentsProvider` is cloud-backed, they can be subject to its sync. Never state a blanket "all data stays offline": STT sends audio to the chosen provider.

## S3 — Network, Input, and Permissions

Only internet access and the Android permissions functionally needed. No `MANAGE_EXTERNAL_STORAGE`, no accessibility-service permission, no microphone access without an actual new recording feature. SAF instead of broad storage access. Request notification permission at the right moment; a denial must never cause silent or broken behavior. The app is not a generic proxy for arbitrary URLs.

Strict YouTube host/URL validation, covering userinfo, Unicode lookalikes, nested redirect parameters, and multiple URLs. No shell-capable free-form parameters. The downloader gets only a controlled configuration set; check file names/archives against path traversal and disallowed absolute paths. Provider API hosts come from vetted profiles — no freely entered OpenAI-compatible endpoints in v1.

Never carry secrets across a redirect to a different host. Configure API, media, and update HTTP clients separately. Use HTTPS with normal certificate validation; no global trust-all certificates. Test-only HTTP exceptions belong exclusively in clearly separated test build variants. Bound parser sizes, decompression, output volume, and process runtimes. Never execute media/metadata file content as instructions.

## S4 — Two Different Update Classes

**Component update:** a swappable yt-dlp/EJS package within a proven-compatible embedded runtime. Can happen without reinstalling the APK.

**App/runtime update:** changes to Kotlin code, native Python/JS/FFmpeg binaries, ABI, SDK, or incompatible interfaces. Use a signed APK update path for these. No promise that every future change can be fixed with the yt-dlp update button.

Ship an actually verified combination with the app initially. The app must not depend on an initial internet download to function at all. Show the versions and origin of every component in settings and diagnostics. A runtime requirement is `REQUIRES_APP_UPDATE`, not "update successful."

## S5 — Update Policy for the Personal Sideload App

Channels: Stable and Nightly. Nightly may be the default when the shipped combination was verified from that channel; choosing a channel does not mean automatic installation. Default: manual installation, with an optional metadata check and notification at most once a day. Offer "Check for update / Update and retry" on a plausible extractor failure. No checking out `main`/`master` and no arbitrary repository URLs in v1.

An optional "update once after a matching extraction error" requires explicit opt-in. Never update reflexively on offline state, HTTP 429, a private video, or a locked source. Before any retry, take the video ID and prior billable steps into account. A release note does not prove that the exact observed bug was fixed; the UI says "new version available," not "guaranteed fix."

Update checks are OS-scheduled, not second-accurate notifications. Cache check requests, use ETag/backoff where available, and deduplicate notifications. No infinite loop after one failed update/retry.

## S6 — Integrity and Authenticity

A SHA-256 hash fetched, unauthenticated, from the same compromised source as the file only proves the two match — it proves nothing about independent publisher authenticity. The minimum requirement is an **authenticated binding** between a trusted publisher, package contents, version, and compatibility.

Preferably verify official, signed release/checksum metadata against a public key whose trust is already established. Prove the signature algorithm, the artifacts actually published, and key verification in the P0 test. Do not reload a public key alongside the signature, uncontrolled, on every update. A key change needs a traceable trust path or an app update.

If an Android wrapper needs its own repackaged/lazily fetched artifacts, explicitly check their relationship to upstream, the build path, and publisher trust. Alternatively, build a package from pinned upstream releases in controlled project CI and sign it with a project release key — trust then additionally rests on the project's own build/signing process, and that must be documented. No inventing "official" status for forks. Never put private signing keys in the repository or on the phone.

Cryptographic verification is mandatory, not an optional cosmetic feature. If no solid trust path exists for the chosen hot-update artifact, do not force an insecure implementation. Report the component update as blocked, use the verified bundled version, and keep signed APK updates working. That alone still does not satisfy full hot-update acceptance.

## S7 — Compatibility Package and Activation

An installable engine package verifiably contains or references: package format version, publisher, channel, release/commit ID, yt-dlp and EJS versions, supported app/Python/JS versions, artifact hashes/sizes, and the required signature. Respect the actual upstream coupling between EJS and yt-dlp; do not pin each piece to "latest" in isolation. [R12]

Update flow: verify metadata → check compatibility → download, bounded, into a private staging area → verify signature/hashes → unpack archives safely → verify local initialization → run a controlled functional test → activate the candidate. No unbounded decompression, zip-slip paths, or executable files on publicly writable storage.

Running jobs pin the engine version they actually use. Never swap files out from under a running process. New jobs use only released versions. Activation and rollback go through small, atomically switched metadata/directory slots with crash recovery. For in-process Python, keep in mind that loaded modules do not reload cleanly just because the underlying file changed; a process restart or a proven reload strategy is mandatory.

A local initialization failure triggers a rollback. A failed live smoke test due to being offline or a YouTube outage is, by contrast, inconclusive at first — not solid proof of a broken new version. The defined evidence must be satisfied before activation; inconclusive candidates stay staged. Keep the active, the previous healthy, and the bundled version; clean up older versions only once nothing active still references them. Flag a rollback to a known vulnerability with a warning and block it where appropriate. No "previous = safe" just because it is older.

## S8 — APK Delivery Without Constant Manual Work

Clearly separate debug builds from personal release builds. Use the same controlled release signing key across successive personal updates, incrementing version codes. Store the key securely outside Git; CI signing only when the secret has been deliberately provisioned. First install/update goes through the normal Android install dialog — never claim silent installation.

CI can produce APK artifacts. Actually publishing a release, or wiring up an automatic update source, needs an explicit instruction; Codex must not create a public repository or release unnoticed. Optional in-app APK update notices are fine, but do not build a second, complex app-store platform. This path stays necessary even once component updates work.

Before publishing, check dependency licenses, the concrete FFmpeg configuration, notice/source-disclosure obligations, and the app's own license. Do not reflexively license the whole project MIT when bundled components carry other terms. The personal sideload architecture is not automatically a Google-Play-vetted one. [R09, R19–R21]
