# Verified source audio reuse and provider diagnostics

Date: 2026-09-30. Status: accepted for the owner-requested reliability fix.

A retry or a newly prepared job may reuse a downloaded YouTube audio rendition only after resolving the exact source again and verifying the source identity, selected track ID, private regular file and stored SHA-256. Copy that immutable source into the new attempt with storage reservation and atomic commit. Missing, changed or unbound audio follows the normal download path. A new attempt keeps its own configuration, preparation, submissions and results; audio reuse never implies a free transcription or replay of a paid submission.

Provider-specific configuration validation runs before acquisition and uses the same rules as submission. HTTP failures carry only a typed operation, HTTP status and a classified reason in the existing STT checkpoint. Raw provider error text remains transient because it can echo credentials, URLs or transcript content. Missing or unrecognized detail remains unknown. Existing checkpoints remain readable; no Room migration is needed.

The displayed job state treats a queued continuation as processing after work has begun, while real network, quota, resource, user and remote waits remain visible. A remote retrieval stays a remote wait during polling. Native foreground network callbacks refresh displayed waits without changing the scheduled retry time or issuing another request. Notifications describe actionable stops and completed jobs in the background, never internal worker boundaries.

Startup suppresses only already-finished historical jobs. If an attempt finished before its job summary was
committed, recovery may deliver that new background completion. Notification deduplication records successful
posting afterward. A stable tag and Android's only-alert-once behavior replace an already-active notification
without another alert. If the process dies between posting and recording and the user dismisses the notification
before recovery, it can be delivered once more; Android notification delivery and private preference persistence
are not atomic. This bounded duplicate is preferred to silently losing an actionable stop or background result.
No Room schema or notification outbox is introduced.
