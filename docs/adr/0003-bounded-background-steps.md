# ADR 0003: Short, Durably Persisted Work Steps

Date: September 7, 2026. Architecture decision; device evidence follows as part of integration.

WorkManager handles recovery, metadata/captions, and individual short steps.
A single run executes at most one due step and persists the next state.
Passive AssemblyAI processing has no worker running continuously: the remote
ID and the next poll time live in Room, and a new bounded `OneTimeWorkRequest`
becomes due afterward. OS scheduling times are not exact timers.

Downloads, audio preparation, and individual uploads are bounded and get
their own checkpoints. A hard limit of eight minutes per HTTP call or work
step prevents a worker from running for hours. Longer, user-initiated
transfers can run as a UIDT job on API 34+, provided the start conditions are
actually met; Android 29–33 uses bounded WorkManager steps. A UIDT job never
stays open for passive provider waiting. There is no UIDT sign-off before
this driver is implemented and proven.

Central claims/leases and resource limits live in Room, not solely in process
semaphores. A new process reconstructs interrupted steps; a persisted,
billable submission intent without a confirmed response becomes
`SUBMISSION_UNCERTAIN`. An expired lease never permits a blind resubmission.
A force-stop is reconciled only at the next permitted app start.

Primary sources, checked on September 7:
[UIDT and WorkManager's fit for short, interruptible transfers](https://developer.android.com/develop/background-work/background-tasks/uidt)
and [long-worker quotas from Android 16](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running).

## Recovery After Process Loss

At the first start, every inherited attempt and resource lease is cleared,
even if the last checkpoint was already `WAITING` or `QUEUED`. Persisted poll
times are preserved. Recovery runs before any new claims; a WorkManager
enqueue that fails later does not repeat this reconciliation while work is
already running. A valid atomic artifact file can only repair its missing
Room row given a unique, matching `PERSIST` checkpoint. Source, configuration
snapshot, branch, provider, and any existing row hashes must all match.
Unmatched files are never attributed to invented jobs.

Temporary attempt files are deleted only after the final checkpoint and a
persisted artifact exist. A repeatable startup cleanup closes the subsequent
crash window. Orphaned partial import files and abandoned import previews are
cleaned up under the same lifecycle locks as start/deletion. Exports get an
independent reconciliation of their external document reference; an
interrupted write stays visible and is retried into a new file only after an
explicit user action. That never triggers a new STT request.
