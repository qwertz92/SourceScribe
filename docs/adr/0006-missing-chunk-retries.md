# ADR 0006: Confirmed Retry of Missing Audio Segments

Date: September 7, 2026. Implementation decision; evidence in the test report.

"Missing only" looks at the most recent attempt of every branch actually
created, including the STT fallback. An earlier success never masks a more
recent partial success. For an STT partial result, only its reported missing
chunks are requested again. A full new attempt remains a separate, explicitly
confirmed action.

The new attempt reuses the unchanged configuration snapshot, the original
engine assignment, and the verified audio chunk plan. Prepared audio files
and successful original responses are copied into its private directory
under storage-quota reservation and re-bound to source, configuration, and
hashes. Missing or altered files block this retry outright; there is no
silent substitute of paying to recompute segments that already succeeded.
Successful responses are re-normalized, never fabricated.

Room version 3 adds `submissions.reusedFromId` and `rejectionCode`. Reused
responses reference their original submission, reserve no cost of their own,
and never alter that submission's original cost entry. The new artifact
names its predecessor artifact in its provenance. Old artifacts stay
unchanged. An explicit resume after correcting a login only resets
submissions unambiguously rejected with `AUTHENTICATION`/`ACCESS_DENIED`.
`SENDING` and `UNCERTAIN` are therefore never resent.

The technical response spool and prepared audio files of a partial result
stay available until the most recent result is complete, or until explicit
deletion. This bounded recovery retention applies even without a durable raw
export; it still counts against the storage limit.

A job already created in Room stays a created job even if the WorkManager
enqueue fails. A not-yet-claimed attempt visibly switches to `WAITING_USER`
with `SCHEDULING_FAILED`; a worker that starts late cannot claim it. An
already running worker is never overwritten in the process. As a result, a
scheduler failure never masquerades as a failed job start, and clicking
again never creates billable duplicates.

A confirmed multi-selection is created as a whole in a single Room
transaction, before the first worker is scheduled. Process loss during that
transaction leaves no partial batch; after the commit, startup recovery
finds every attempt not yet scheduled. The UI removes the previews only
after this durable commit. Any source change, and any change to the
selected audio track, revokes a prior upload authorization.

Review addition from September 8: an output missing from the partial result
can already have a paid-for, stored provider response — for instance, when
the combined responses exceed a local size limit. "Missing only" must never
infer a new upload from that. Such responses, along with uncertain or
still-running submissions, block this retry before any file is copied.

Retry files are created before the Room commit. Startup cleanup therefore
removes only canonical UUID directories in the private `attempts` folder for
which no attempt exists in Room, under the shared lifecycle lock.
Directories of existing attempts, foreign names, and symlinks are all
preserved. This ensures that a process abort during the copy never leaves
behind unreferenced audio and response copies that would otherwise
permanently count against the storage quota.
