# Provider reliability and history fixes — 30 September 2026

## Scope and verified causes

The owner's failed link and model selection were not supplied, so that exact failure is not reproduced. A fresh
baseline Groq run already succeeded for the public 19-second source `jNQXAC9IVRw`. The fixes address independently
reproduced defects rather than attributing the owner's failure to a guessed format or size limit.

The app downloads the selected original rendition (often WebM/Opus), then prepares mono 16 kHz, 64 kbps MP3
chunks of at most ten minutes and 24,000,000 bytes. The original WebM file is never sent directly. Current primary
contracts were checked: [Groq speech-to-text](https://console.groq.com/docs/speech-to-text),
[Groq API reference](https://console.groq.com/docs/api-reference), and
[AssemblyAI transcript submission](https://www.assemblyai.com/docs/pre-recorded-audio/api-reference/transcripts/submit).
AssemblyAI's current `universal-3-5-pro` model was retained; an older indexed model page was not treated as authority.

Confirmed defects and changes:

- Provider configuration could fail locally only after download/preparation, yet appear as a remote rejection.
  Preview and execution now use the adapters' shared configuration validation before acquisition, including the
  captions-then-STT fallback. Invalid model/language/context/keyterm options have specific local explanations.
- Remote errors now preserve only typed operation, HTTP status, and recognized reason in the checkpoint.
  Error-body parsing is bounded at 16 KiB; arbitrary provider text, keys, and transcript content are not persisted.
  Actions shows the failed branch and phase and the available diagnostic. Unknown reasons stay unknown.
- Preparing the same source/rendition again can copy a verified original from an earlier attempt. Identity,
  engine binding, private regular-file status, and SHA-256 must all match. Missing, modified, or mismatched originals
  are downloaded again. Paid requests, provider responses, and prepared chunks are not silently reused or repeated.
- WorkManager phase continuations and AssemblyAI polling no longer alternate the visible queued/running status.
  Foreground native network callbacks refresh presentation while retaining the real retry deadline and constraints.
- Upload progress counts the current audio request body, including multipart overhead. AssemblyAI's small JSON
  submission does not overwrite audio-upload progress. Reloading the current row before saving the next phase
  prevents a stale row from erasing byte counters. Percentage and speed appear only from measured data.
- Ordinary progress and waiting states do not notify. Completion notifies in the background; actionable failures
  notify once, including in the foreground. Persistent event deduplication prevents already-recorded dismissed alerts from reappearing
  after recovery. A process death after posting but before recording, followed by dismissal before restart,
  can produce one duplicate; OS delivery and preference persistence are not atomic. Startup does not announce historic completions.
- The card owns the ripple across its whole outline. Error summaries reserve two lines and the help-button slot;
  the complete explanation remains in Actions.

## Provider evidence — 30 September to 1 October 2026

All live jobs used the exact public 19-second source `https://www.youtube.com/watch?v=jNQXAC9IVRw`.
These controlled checks do not reproduce the owner's unspecified URL/model combination. Raw redacted outputs
are in ignored `.local-tools/build-reports/`; console copies are not additional requests.

| Provider / run | Requests and result |
|---|---|
| Groq Turbo baseline | One successful job: one transcription upload, four segments, 197 characters, `en`, 36.964 s. |
| Groq Turbo after fixes | One successful job: one transcription upload, four segments, 197 characters, `en`, 40.344 s. |
| Groq v3, explicit rendition `139-drc` | One successful job: one transcription upload, four segments, 201 characters, `en`, 33.327 s. |
| Groq v3, default rendition `251` | Free download failed with `CHALLENGE_REQUIRED`; zero provider requests. |
| AssemblyAI before sentence fix, `139-drc` | One upload, one paid transcription POST, one poll: stored `PARTIAL_SUCCESS`, one segment, 201 characters, `en`, 74.855 s. |
| AssemblyAI after sentence fix, `139-drc` | One upload, one paid transcription POST, two retrieval GETs: stored `SUCCESS`, four segments, 201 characters, `en`, no warnings, 71.406 s. |

Totals: three successful Groq jobs and two paid AssemblyAI transcription POSTs. Both AssemblyAI jobs retained
the requested and reported `universal-3-5-pro`; no diarization option, model change, or paid resubmission was
used to obtain sentence boundaries. Source duration is not a measurement of invoiced duration or cost. Groq
returns no model field, so its model is recorded as requested and its reported model remains unknown.

The first AssemblyAI job exposed a real integration defect: `utterances` are tied to diarization, so the default
request's segment timestamps need the native `/sentences` endpoint. The adapter now saves the accepted receipt
before scheduling that GET. A failed GET stays in retrieval and cannot turn into another paid POST. Pending
receipts, ID/text/timing validation, composite size limits and atomic response replacement are covered by
regressions. Legacy unmarked receipts remain readable with truthful partial status. See
[ADR 0014](../adr/0014-assemblyai-sentence-retrieval.md) for the boundary and primary sources.

## Local verification

The complete build on 1 October passed `:core:test`, debug/test/release APK generation, the extractor JVM test task (`NO-SOURCE`),
and all four app/extractor debug/release lint tasks. Core: 231 passed, zero failures or skips. Each lint XML
contained zero issues. The app's fresh-APK instrumentation passed 260 of 271 cases, with 11 explicitly opt-in
skips and zero failures (151.345 s). The separately enabled real offline-startup check passed without skipping.
The complete normal build was repeated after all controls and passed (16m 17s, 261 tasks). All 211 implementation/build/tool source hashes matched the pre-build manifest. All four lint XMLs again contained zero issues. Fresh normal APKs were installed and the full app suite again passed: 260 passes, 11 documented opt-in skips, zero failures among 271 cases. The remaining opt-in, visual and signed-upgrade checks are pending.

Negative controls are run against retained tests, then restored byte-for-byte:

- The first HTTP/provider control reverted all five affected implementations and produced seven expected
  failures among 223 core cases.
- Reverting the AssemblyAI adapter produced seven failures among 50 selected adapter/HTTP cases. The new paid
  acceptance/GET-retry device regression was also observed failing before the fix: it entered `SUBMIT` instead
  of `RETRIEVE`. Three focused adapter cost/size regressions failed before their correction.
- The app functional control disabled every configuration preflight, checkpoint diagnostic write, current-row
  upload-counter reload, progress update, reuse call, visible-state transformation and notification gate in its
  plan. It compiled and produced 17 failures, 113 passes and one connected-run offline skip among 131 cases. A prior temporary mutation failed to compile on
  two unused expressions; that run is `NOT_RUN` for device verification, not a successful negative control.
- The audio/offline control disabled all source, rendition, engine, hash, symlink and storage-reservation gates,
  stale diagnostic clearing and both native refresh call sites. Seven device cases failed; all six expected
  oracles were observed, including the separately enabled actual offline startup. There were 46 passes,
  seven failures and one connected-run opt-in skip; the offline invocation itself failed as expected rather
  than skipping. The first compiled attempt could not install because the isolated emulator was gone, so
  that attempt is `NOT_RUN` for device evidence. It was restarted without wiping data, then the same sources
  built up-to-date and the device control completed. All affected source hashes were independently checked
  after restoration.
- The complete UI control removed both the measured row floor and native `Text.minLines`, varied the
  previously fixed help slot, and disabled transfer phase/state guards and neutral retry/error text. It produced
  five failures and 23 passes among 28 device cases; every expected oracle failed. The earlier partial control
  caught four cases but left the native text minimum and help slot in force; it is not presented as a complete
  height control. All source hashes matched after the final restoration.
- `assemblyUploadTransportFailureRemainsBoundedPreparedRetry` and the standalone
  `terminalOutcomesNotifyOnlyInBackground` did not fail in that combined control. Other tests caught audio
  upload progress, both notification foreground behavior and deduplication. A combined control does not prove
  that each individual mutation has an independent oracle.

Coverage boundaries: the cross-thread callback interleaving is not deterministically forced; the callback is
now natively dispatched on the main handler. Visual ripple verification is pending. The local reprepare
setting regression does not independently prove retention of a previously chosen YouTube rendition. Controls
also do not isolate copy cancellation, an in-flight progress checkpoint, every legacy receipt path, or the
OS-post/preference-recording crash race. These limits are not claimed as passing checks.

## Independent review

Native GPT-6 Luna was selected through the actual runtime model override and checked against child runtime
metadata, recorded in `.local-tools/build-reports/2026-09-30-agent-runtime.json`. Reviews covered provider
adapters/HTTP, STT acquisition/recovery, history presentation, and notifications/lifecycle. The final passes
were empty: no demonstrated P1, P2, P3 or P4 finding in those assigned scopes. The AssemblyAI review first
found a paid-resubmission P1; it was reproduced, fixed, negatively controlled and reviewed again. Review pass
counts for the recorded scopes: provider 6, STT 5, history 5, notifications 8. These are scoped pass counts,
not a claim that the project's older issue list is empty.

## Dependency compatibility measurement

Official Google Maven metadata confirmed AGP 9.4.1, WorkManager 2.12.0, and core-ktx 1.19.1 as current stable
updates. Gradle 9.8.0 was actually run with its official checksum
`bafd5ce9cfaea0fbccfdc8439a1ac42fbd4cd9c89dc9a988228d8a2639a58e6c`. Core tests still passed (223/223),
but the strict build failed: AGP 9.4.1's `BasePlugin.createAndroidJdkImageConfiguration` calls deprecated
`Configuration.setVisible`. A second `:help -Dorg.gradle.deprecation.trace=true --stacktrace` run confirmed the
upstream `BasePlugin.kt` frame in `createAndroidJdkImageConfiguration`. No newer stable AGP is published; only 9.5 alpha versions follow it.
The wrapper stays at checksum-pinned 9.7.1; its comment requires remeasurement at the next AGP update.
`org.gradle.warning.mode=fail` remains enabled. New dependency verification entries were additive (50 components
after resolution, zero removals), from the existing official repositories; the final strict metadata-verified build passed with these entries.
