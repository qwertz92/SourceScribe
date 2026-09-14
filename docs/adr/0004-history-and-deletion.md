# ADR 0004: Resumption, New Attempts, and Deletion

September 7, 2026. A safe resumption reuses the same attempt and its
submission records. An explicitly confirmed new run gets a new attempt ID and
a new artifact; old results are preserved. A targeted retry creates new
attempts only for branches without a complete result. Switching providers is
confirmed, before starting, as a new configuration snapshot in the source
preview. Uncertain or abandoned jobs can keep incurring cost; retrying is
therefore never automatic error handling.

A deletion is first persisted durably as an intent in Room. New claims are
then blocked. Only after local workers finish are this job's files removed
and its DB references deleted — and only those. A crash resumes this
confirmed deletion at the next start. External exports are never deleted.
Import files used by other jobs are preserved. Schema 2 adds the deletion
intent via a non-destructive migration from schema 1.

Global concurrency is the shared scheduler capacity (1–4); provider, cost
ceiling, source choice, and every acquisition option stay in the job
snapshot.

An automatic fallback derives a deterministic UUID from its caption attempt's
ID. This produces at most one STT branch per attempt; a system clock set
back, or identical millisecond timestamps, never suppresses a new, explicit
attempt. The DB transaction additionally checks the work lease's owner and
expiry.

Review addition from September 8: recovery also checks the reverse direction,
"a Room artifact without a finalized file." The affected attempt gets a
visible integrity error; metadata and other results are preserved. An
invalid stored job configuration only blocks the affected job. No replacement
snapshot with default values is generated. History and settings show this
state without ever reloading the broken configuration as valid. Caption
provenance, during both normal persistence and recovery, is additionally
bound to the immutable engine installation pinned to that attempt.
