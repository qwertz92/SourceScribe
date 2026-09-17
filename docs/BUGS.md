# Known bugs

**As of:** 16 September 2026, after the 0.4.0 work, at commit `f7dd7d2`, before the 0.4.0
release. This is the one list of every known, unfixed item, with its location, trigger, evidence, and priority in
one place — `docs/DEFECTS.md` no longer exists; what used to live there is folded into the entry for its number
below. Item 1 is the still-open feedback from the 10 September 2026 device test; item 2 (the bottom button "could use
more space") was set aside on 16 September 2026 because the owner could not recall what was meant, and comes back
only if it is reported again. Closed, and kept only as a number so references stay valid, are 3, 4, 6, 7, 8, 9, 12, 13, 17, 18, 19, 20, 23, 31, 32, 36, 41, 42, 47, 54, 56,
57, 58, 59, 60, and 63; what each of them was and which commit closed it is in `docs/HISTORY.md` and in git.

## Scale

There is no widely accepted scale for functional bugs the way CVSS exists for security vulnerabilities. The usual
approach separates two questions: how severe a bug is, and how urgently it should be fixed. Severity notes in
parentheses after an item's title are the reviewing round's own judgment of how bad the defect is in itself.
Priority — the P-number an item sits under — asks what the user notices and how often, so it can differ from
severity. Item 46(b), for instance, was rated medium severity by its reviewer but sits at P3 here, because it costs
one extra step to reach a downloaded engine, not a lost result, and the fix is a new feature (activating any
installed engine directly) rather than a defect correction.

| Level | Meaning | Handling |
|---|---|---|
| P1 | The app or a core function is unusable: fetching subtitles, transcribing audio, saving or exporting a result. Also data loss, unapproved cost, or an exploitable security hole. | Fix immediately. |
| P2 | A normal flow gives a wrong or misleading result, or is clearly disruptive; a workaround exists. | Fix it; only when the fix needs a design decision or costs far more than the defect is worth, record the effort and let the owner decide. |
| P3 | Minor annoyance: imprecise text, display, diagnostics, cleanup, or a bug that only occurs under rare conditions in normal use. | Fix on the spot if it takes minutes; otherwise record it here and report it. |
| P4 | Not noticeable: tests, check scripts, code that works correctly today but could easily become wrong, or a case only a contrived flow reaches. | Same as P3. |

**Unverified** means: derived from the code but not reproduced or measured. The priority given is for the likely
case; where a worse case would rate higher, that is noted. For the security-adjacent items 37, 40, and 42(closed),
no exploitable hole is demonstrated, so none of them carries a CVSS score.

47 items are open: 1 P2, 10 P3, 35 P4, and item 55, which stays untested by the owner's decision (see
"Not verified").

## P1

None known. What could be hiding a P1 is in the next section.

## Not verified

These gaps are not known bugs. If any of them fails, it is a P1.

- **Real transcription with AssemblyAI, OpenAI, and Groq.** AssemblyAI and OpenAI have never been called with a
  real key in this project, only against simulated provider responses. Groq was called for real exactly once, on
  16 September 2026, through the app's own pipeline end to end (`LiveGroqTranscriptionTest`, the public 19-second
  video `jNQXAC9IVRw`, the app's default model): the run resolved the source with the real engine, downloaded the
  recommended audio rendition, prepared and uploaded it, and stored a complete transcript — outcome `SUCCESS`,
  exactly one provider request, 4 segments, 197 characters, shown afterward in history. That proves the whole path
  once, for one provider, one clip of 19 seconds, one language, and one chunk — it does not prove AssemblyAI,
  OpenAI, a source needing more than one chunk, a provider error against a real endpoint, or a paid Groq tier. The
  same run also showed two things the app had never seen from a real provider before: Groq's response carries no
  `model` field (the app already falls back to the requested model when none is reported, and the live test now
  asserts exactly that), and Groq names its language as the English word "english" rather than a two-letter code,
  which the parser did not recognize until today's fix (see item 69 below) — before that fix, the stored transcript
  from this same run carried no language at all. Whether Groq's `language` field really holds "english" and not,
  say, "en", is itself only known from this one run; the fix reads both shapes, but a second live run is what would
  confirm which one a real Groq response actually sends.
- **An ARM64 device, like almost every current phone.** All device tests still ran on x86_64 emulators with
  Android 17 (API 37). The ARM64 build of the app has never run in any of these tests.
- **Updating an installed preview 0.1.0 or 0.2.0-preview.1 to 0.4.0.** Preview 0.2.0 migrated the database from
  schema 3 to schema 4, verified only against test databases in `MigrationTest`; neither 0.3.0's unpublished build
  nor the 0.4.0 work changed the schema further (it is still version 4), so both an 0.1.0 and an 0.2.0 installation
  would take the same migration path an 0.4.0 update would exercise for the first time on a real phone. Verified on 16 September 2026 on a fresh emulator (Pixel 10, Android 17, the same system image as the test device, started as AVD `Upgrade10` at port 5556 because the usual AVD had no room for a second 147 MB install): a 0.1.0-preview.1 and a 0.2.0-preview.1 install, each with one finished captions job of the 19-second clip, were updated in place to the signed 0.4.0 with `adb install -r`; after the update the job, its stored transcript and the settings were still there and the crash log stayed empty

Full operation with TalkBack is also not verified.

**Untested by decision: item 55, Android before version 17.** The app installs from Android 10 (API 29) onward and
checks the signature of its bundled engine with Bouncy Castle 1.86, but every device test runs on Android 17
(API 37). If that check failed on an older version, the app could not process YouTube links there; Bouncy Castle's
release notes suggest it holds up. On 14 September 2026 the owner decided that tests on Android 17 are enough.

## P2

### 1. Result view scrolling "not at the right edge, only halfway" (unverified whether it still occurs)

- **Reported:** in the owner's 10 September 2026 device test.
- **What changed since:** the view was a fixed-size `Dialog` (94% width, 92% height). It is now a dedicated
  full-screen destination with `windowInsetsPadding(WindowInsets.safeDrawing)`, a single `LazyColumn`, and a fixed
  footer, removing fixed fractional heights and hidden margins as possible causes.
- **What is demonstrated:** verified on `emulator-5556` with a real 286-section transcript: swiping at the right
  edge scrolls, and the list runs to the last section. Full-text search keeps filtering correctly, including with
  the keyboard open.
- **What is missing:** landscape orientation and a second device. The originally reported flow was never
  reproduced; whether it still occurs on the owner's own device is unverified.

## P3

### 5. The pre-check and the actual limit measure two different durations (medium, partly unverified)

- **Location:** `JobLimits.exceeds` (preview) vs. `SttStep.kt` (`preparation.probe`, the actual gate).
- **Precondition:** the preview checks the video length yt-dlp reports; the actual gate checks the measured length
  of the downloaded audio track. Both compare strictly with `>`, no tolerance.
- **Consequence:** if the two values differ by milliseconds, a job can pass the preview and then end with
  `AUDIO_LONGER_THAN_LIMIT`. That costs a download but no money — the abort happens before `SUBMIT`. Conversely,
  the preview can block a job that would have gone through.
- **Changed since 16 September 2026:** the field that used to let someone type a lower limit is gone (item 2 of the
  0.4.0 plan); every new job now carries the app's own fixed ceiling of 36,000 seconds (10 hours,
  `JobLimits.MAX_AUDIO_SECONDS`) on both sides of this check, instead of whatever a typed default or a stored
  preset happened to hold. The two-different-durations mechanism itself is unchanged — the preview still reads
  yt-dlp's value, the gate still reads the measured one — but the boundary the discrepancy would have to land on
  moved from a small, commonly-hit number (an hour, by default) to the app's own outer limit, which almost no real
  source approaches. The case is therefore far less likely to be hit than before, not closed.
- **Unverified:** how often and how far the two values diverge in practice was not measured. To close: log both
  values on several real videos and decide from that whether a tolerance (analogous to
  `PREPARED_DURATION_TOLERANCE_MS`) is justified. A tolerance would widen the cost boundary slightly and must not
  be introduced without that measurement.

### 11. A change to warning generation permanently blocks reusing paid sections (low)

- **Location:** `SttStep.kt`, `prepareMissingRetry()`: the comparison `artifact.warningCount != partial.warnings.size`
  and the comparison against `providerWarnings.distinct()`.
- **Precondition:** a job is `PARTIAL_SUCCESS`. For an already-paid section, the response had more than 64 distinct
  warnings, and the app was later updated to a build with the warning cap.
- **Flow:** "Fetch only what is missing" re-parses the stored raw data and compares it to what was saved on the
  first run. The new parse yields more entries than the old count, the comparison fails, and the path is
  permanently refused with `MISSING_RETRY_DATA`.
- **What is already fine:** the failure is safe, not silent. `JobCoordinator.retry` catches it before the
  transaction; no cost, no silent retry, and the partial state is preserved.
  `SttMissingRetryTest.retainedProviderWarningsMustMatchPartialArtifact` covers exactly this with
  `assertEquals(0, requestAttempts.get())`.
- **Not reachable today:** the one real provider run this project has made (16 September, Groq) produced no partial
  result, so no stored partial state with provider warnings exists yet. It becomes reachable once a real partial
  result exists and warning generation is changed again afterward.

### 14. Old artifacts keep the old caption code and keep getting the timestamp sentence (low)

- **Location:** `CaptionParser.kt` against already-stored `TranscriptDocument.warnings`.
- **Precondition:** a transcript created from a caption track before 11 September 2026, which recorded
  `MALFORMED_SEGMENTS_<n>`.
- **Expected vs. actual:** the caption case is now called `MALFORMED_CAPTION_SEGMENTS_<n>`, because the provider
  parser used the same name with a different meaning. New jobs get the correct sentence; an old entry still falls
  into the timestamp group and says "timestamps are missing" even though text is what is missing.
- **Why it stands:** the index that would distinguish the two sources is deliberately dropped when summarizing.
  Rewriting stored warning lists touches `TranscriptDocument.normalizationVersion` and hinges on the same decision
  as item 11.
- **Missing:** a decision, together with item 11, on whether stored warning lists are ever migrated.

### 24. A price's source page is recorded but shown nowhere (low)

- **Location:** `ProviderCapabilities.pricingSource` in `ProviderContract.kt`, set by all three adapters.
- **Precondition:** none; the value always exists.
- **Expected vs. actual:** next to the cost estimate is the pricing date. That date is only useful if the reader
  can check what it was verified against; the page for that lives in the record but reaches neither the display,
  nor export, nor diagnostics.
- **Why it stays open:** help text is static per topic and does not know the chosen provider; a dynamic paragraph
  there is a change to the help model, not a one-liner. Writing the three URLs into the cost help text instead
  would state each address a second time — exactly what `StatedNumbersTest` exists to prevent.

### 25. Groq's upload limit is the smaller of two tiers (low)

- **Location:** `GroqAdapter.MAX_UPLOAD_BYTES`.
- **Precondition:** a paid Groq key ("dev tier") and a file between 25 and 100MB.
- **Expected vs. actual:** Groq documents 25MB for the free tier and 100MB for the paid tier. The app does not know
  the tier of a user-supplied key and holds everyone to the smaller limit; a paying user gets a local rejection for
  a file the provider would have accepted.
- **Why it stays this way:** the alternative is worse. Raising the limit turns a local rejection into a failure at
  the provider, the costlier of the two outcomes for a paid service. The real fix is a tier field in the
  credentials, not a bigger number.

### 35. Line-wrapping limits on two screens are only partly measured on-device (low)

- **Location:** cost line in `NewSourceScreen.kt` (a `ReservedText` sized to its tallest text); preview error line
  in `NewSourceScreen.kt`; elapsed time and byte count in `HistoryScreen.kt` (`ReservedText`); since 0.4.0 also the
  download/upload progress line in `HistoryScreen.kt` (item 3 of the 0.4.0 plan), which `JobCardLayoutTest`
  measures at font scale 1 and 2 since `c832a9c`.
- **Measured:** the cost line wraps to two lines at font scale 1.0 and 1.3 and three lines at 2.0, with the full
  text, pricing date included, visible in all measured screenshots. Only `estimated_cost` is measured, not
  `cost_source_too_long` or `price_unknown`.
- **Not measured:** whether the history card's placeholders (`LONGEST_ELAPSED`, `LONGEST_BYTE_SIZE`) are actually
  the tallest cases, and the preview error line at 200% font.
- **A measurement trap:** uiautomator reports only a node's visible portion. A measurement only counts once the
  line sits fully inside the scrolling form, not partly under the navigation bar.
- **Why it stays open:** a Compose UI test using `onTextLayout` to check line count and clipping is missing; it
  would be the better approach because it persists.

### 37. Rolling back to an older engine warns but blocks nothing (low)

- **Location:** `EngineUpdateManager.rollbackTarget` and `rollback(expectedId)`, confirmed via
  `MainViewModel.prepareRollback` in `SettingsScreen.kt`. Required by `docs/SECURITY_UPDATES.md`, section S7.
- **Precondition:** an updated engine is active, and an earlier healthy or the bundled one is available.
- **Status:** the button opens a confirmation naming the version that would become active again, stating that an
  older version can contain vulnerabilities a later one already closes, and that SourceScribe does not check for
  that. Only the named installation is switched to: if the target changed since opening, `rollback` ends with
  `ENGINE_ROLLBACK_TARGET_CHANGED` and switches nothing.
- **Missing:** knowledge of which version has a known hole. Nothing in the tree carries that — no minimum version,
  no list of affected versions. A list in app code would be outdated by the next disclosed vulnerability; this
  needs a signed field in the engine package, which is a decision about the update format, not about this button.

### 46. Two edge cases in switching to a newly bundled engine (low; (b) rated medium by its reviewer)

- **Location:** `EngineUpdateManager.ensureBundledLocked`, the branch for a newly bundled engine;
  [ADR 0010](adr/0010-bundled-engine-after-app-update.md).
- **(a) An already-loaded version comes back bundled.** If someone had loaded, as an update, exactly the version an
  app update now bundles, and had since reverted to the old bundled one, the app update still switches to it,
  because its entry was never marked bundled.
- **(b) A third engine loses its place as "previous."** Someone activates a loaded engine D and reverts to bundled
  A; D is now "previous." An app update brings bundled engine C; C becomes active and A becomes previous. D is then
  no longer reachable via "Revert to previous engine." It still lists in settings, which is display-only, and an
  update only offers the channel's newest release. It can be cleared up during cleanup, since nothing protects it
  anymore.
- **Why low and open:** any manual activation displaces the previous engine the same way; in this model the way
  back reaches exactly one step, and the app update is itself an explicit action. Activating any installed engine
  directly would be a new feature with the same confirmation as rollback (S7), not a fix to the switch-over, and is
  therefore a question for the owner; see "User decisions" below. (b) has no test yet.

### 64. A segment timed into the encoder's own surplus above a chunk's planned window would turn a complete result into a partial one (unverified, speculative until observed)

- **Location:** `SttStep.kt`, the completeness check that compares a segment's `end` timestamp against the stored
  chunk's `durationMs`, together with the fix for item 1 of the 0.4.0 plan (commit `f18937f`).
- **Precondition:** that fix keeps the chunk's *planned* window as its stored `durationMs`, while the bundled
  ffmpeg's own encoder always produces audio 84 to 96 milliseconds longer than that window (measured on
  `emulator-5556`, see `docs/HISTORY.md`). The audio actually sent to the provider therefore runs slightly past
  what the chunk claims to cover.
- **Expected vs. actual:** if a provider times a word or a segment's end into that 84-to-96ms surplus — audio that
  really exists and really was sent, just past the window's own boundary — the app would count it as
  `INCOMPLETE_SEGMENT_TIMESTAMPS` and store a transcription that in fact lost nothing as `PARTIAL_SUCCESS`. This
  would be the reverse of the app's own invariant that a partial result must never appear as a full success: here a
  full result would appear as partial instead, which is the conservative direction but still a wrong label.
- **Why it stays open, unverified:** the one real Groq run of 16 September (4 segments over a 19-second clip) did
  not exercise a chunk anywhere near the 10-minute boundary where the surplus matters, and did not hit this case —
  its only reported problem was the missing `model` field (item 0 of package B). Whether a real provider actually
  times segments into an encoder's own trailing silence is unmeasured. If it turns out to happen routinely, this
  would deserve a higher priority than P3.
- **Missing to close:** either widen the completeness check's tolerance by the same encoder surplus the audio
  preparation already tolerates, or measure real provider responses near a chunk boundary first, so the tolerance
  is not introduced blind.

### 72. The partial-retry exclusion is decided for the whole job, not per branch (P3, review round 2)

- **Location:** `JobActions.offered()` and `demanded()` (`CARRIED_INTO_A_PARTIAL_RETRY`); `HistoryScreen.kt`, where
  `JobSituation` is built from one error per branch and one job-wide `incompleteResult`.
- **Precondition:** mode `BOTH`; the speech-to-text branch holds a partial result bound to engine A; an earlier
  "Fetch only what is missing" re-bound the captions branch to a newer engine B after the owner switched engines;
  B is then replaced (app update, failed self-test) while A is still installed, and the captions attempt stops
  with `ENGINE_NOT_AVAILABLE`.
- **Expected vs. actual:** "Fetch only what is missing" would work here — captions bind the active engine, the
  speech-to-text branch reuses A, which exists — but the dialog withholds it and recommends "Run this job again",
  which transcribes the finished chunks a second time. Both branches of a job are bound to the same engine when it
  is created and only a retry after an engine switch separates them, so this takes two engine changes during one
  job's life; the extra cost follows a tap on a button whose description names the new run. Traced in code by
  the round-2 reviewer, not observed on a device.
- **Missing to close:** carry the error and the partial flag per branch in `JobSituation` and withhold the action
  only where a branch would re-pin the missing engine; about an hour, with the `JobActionsTest` case.

### 73. A job WorkManager stopped for its own network constraint shows only its outcome (P3, review round 2)

- **Location:** `JobWaits.reason`; `JobCoordinator.run`'s cancellation handler, which stores the attempt as
  `QUEUED` with error `INTERRUPTED` when WorkManager stops the worker.
- **Precondition:** a job with "unmetered connections only" is in a network phase when the device moves to a
  metered connection; WorkManager stops the worker and holds the re-enqueued work under the constraint.
- **Expected vs. actual:** the card could say "waiting for an unmetered connection"; it says only the outcome,
  because an attempt that carries an error is read as sitting on its own retry delay. Nothing wrong is claimed —
  the conservative direction. Traced in code, not observed on a device.
- **Missing to close:** the rows do not record why the worker was stopped; storing the stop reason
  (`WorkInfo.stopReason`, API 31 and later) at `onStopped` would let the rule tell the constraint from an
  interruption. About an hour, with a `ViewRulesTest` case.

### 74. The error sentence of an open job card appears without reserved space (P3, review round 2, deliberate for now)

- **Location:** `HistoryScreen.kt`, `AttemptLines`, `attempt.error?.let { AttemptError(it, openHelp) }`.
- **What happens:** when an attempt stops while its card is open, its sentence appears and pushes what is below.
  The rendition and transfer lines got reserved space in `c832a9c`; this one did not, because an error sentence
  runs to three or four lines and reserving that for every open attempt would leave a large blank block in the
  normal case.
- **Missing to close:** a decision on that trade-off (a blank block always, or a one-time shift when the job
  stops); `JobCardLayoutTest` then needs a fixture with an error. Minutes once decided.

## P4

### 10. One error text covers two different causes (low, unverified)

- **Location:** `Labels.kt`, `AUDIO_INVALID_INPUT` / `AUDIO_INPUT_NOT_FILE` branch.
- **Status:** both show "This job's audio file was not readable." That is correct for `AUDIO_INPUT_NOT_FILE`.
  `AUDIO_INVALID_INPUT` instead arises in `AudioPreparation.validateChunkRequest` from an impossible parameter — a
  programming error, not a broken file.
- **Unverified:** no trigger in normal use was found; the check guards against internal misuse.
- **Missing:** either dedicated text in the internal-integrity-code family, or proof the code never reaches the
  user.

### 15. A note made of capital letters would be treated like a code (low, unverified)

- **Location:** `TranscriptWarnings.kt`, `looksLikeCode`.
- **Precondition:** something later puts free text into `TranscriptDocument.warnings` that consists only of capital
  letters, digits, and underscores.
- **Expected vs. actual:** such text would be cut at the first colon and framed as a technical status. Not
  reachable today: the only place that puts free text into this list is the UI test fixture, and its sentences
  contain spaces and lowercase letters.
- **Missing:** nothing, as long as the list carries codes. If it is ever meant for text, it needs its own field
  instead of a shape check.

### 16. The `RESPONSE_` branch is not protected against a future misnamed code (low)

- **Location:** `TranscriptWarnings.kt`, the `else` branch of `group`.
- **Precondition:** someone names a new warning `RESPONSE_...` that does not mean a lost section.
- **Expected vs. actual:** it would silently be reported as a lost section, i.e. worse damage than actually
  occurred. The rule holds today: every `RESPONSE_` code actually produced arises in `SttStep` at a site that sets
  `missing += chunk.index` in the same step.
- **Missing:** either a closed list instead of the prefix rule, or a test that pins the coupling to `missing` at
  the point of creation.

### 21. A timestamp far outside the ordinary range yields no date in the file name (low)

- **Location:** `TranscriptExporter.kt`, `identitySuffix`, `createdAtUtc(document.createdAt).take(10)`.
- **Precondition:** `createdAt` falls outside the years 0000-9999.
- **Expected vs. actual:** a `YYYY-MM-DD` slice is expected. Instead `Instant.toString()` writes such years with a
  sign and a variable digit count, so the first ten characters read `+292278994` - day and month are gone.
- **Why not fixed:** `createdAt` comes from the device clock, not a response; the case needs a grossly wrong clock.
  The name's byte limits still hold, since this representation is plain ASCII.

### 22. The audio-track follow-up question does not show why it is asked (display: low; the more serious provenance half is fixed)

- **Location:** `AudioTracks.kt`, `describe`, together with track selection in the preparation view.
- **Precondition:** two audio tracks whose language the source named, but which were longer than a hundred
  characters and therefore not carried into the record (`AudioTrack.languageRefused`).
- **Expected vs. actual:** `automatic` correctly refuses to choose silently here — the source did distinguish the
  two. But the reader then gets a list where neither track names a language, with no hint that one actually was
  named. The question is correctly asked but hard to answer.
- **The more serious half, already fixed:** `TranscriptExporter` used to write `language=unknown` even when the
  source had in fact named a language; it now writes `language=stated-but-unusable`, so the provenance record no
  longer makes a false claim.
- **Why the display half is not fixed:** unobserved in practice and practically unreachable with real yt-dlp data
  — real language tags run under twenty characters. A dedicated sentence would need two new translated strings for
  a state nobody will likely see; truncating and flagging the value as truncated would break the project's
  all-or-nothing rule.

### 26. "25MB" is not defined as decimal or binary at two providers (low)

- **Location:** `GroqAdapter.MAX_UPLOAD_BYTES` and `OpenAiAdapter.MAX_UPLOAD_BYTES`, both `25_000_000`.
- **Precondition:** a file between 25,000,000 and 26,214,400 bytes.
- **Expected vs. actual:** both providers write "25MB" without saying whether they mean decimal or binary. The
  code takes decimal, the smaller and safer reading — but whether the server actually cuts off at 25,000,000 or
  26,214,400 bytes is unproven.
- **Why it stays open:** only a real request at the boundary — i.e. a paid call — could settle it. Flagged as
  speculation, not a finding.

### 27. A fourth cost calculation, the only one without surcharges (low, harmless today)

- **Location:** `SyncProviderSupport.validateOptions` in `SyncTranscriptParser.kt`, used by `GroqAdapter` and
  `OpenAiAdapter`.
- **Precondition:** a Groq or OpenAI model with a priced add-on. There is none today: Groq reports
  `diarization = false` for every model, and OpenAI's one diarizing model has `priceMicrousdPerHour = null` and is
  rejected a line later instead of estimated.
- **Expected vs. actual:** four places in this program convert a duration to money; three add surcharges, this one
  does not.
- **Why it stays open:** surcharges live per adapter, not in `ProviderCapabilities`; folding them in here would
  mean a contract change, not a line. As a pre-check the site stays harmless; what binds is `SttStep.submit`, which
  checks with surcharges across the whole section plan.

### 28. What `tools/check-repository.py` still does not read (low)

- **Location:** `_read_text`, `_check_actions`, and `_check_notice_versions` in `tools/check-repository.py`.
- **Precondition:** a UTF-16 or UTF-32 file **without** a byte-order mark, a text file over one mebibyte, or an
  unversioned file under `.github/workflows/`; or a version-catalogue name whose version sits in its own table
  column, or is spelled differently, in `THIRD_PARTY_NOTICES.md`.
- **Expected vs. actual:** all of these are indistinguishable from what the script does check, and slip through.
  None apply today: no file in the tree carries a mark-less UTF-16/32 header, the only file over one mebibyte is
  the packed extractor engine and is genuinely binary, `.github/workflows/` contains only versioned files, and no
  version-catalogue name's version sits in its own table column in the notices file today.
- **Why it stays open:** the further direction (checking more, not less) is the safe one; each gap was found by
  running the function outside the repository against purpose-built files, not by anything in the tree today.

### 29. OpenAI's pricing page does not name `whisper-1` (low)

- **Location:** `OpenAiAdapter.PRICING_SOURCE` and `PRICE_WHISPER_MICRO_USD_PER_HOUR`.
- **Precondition:** someone follows the URL to verify the number next to the pricing date.
- **Expected vs. actual:** the number is correct — $0.006/minute is exactly 360,000 micro-USD/hour. But the string
  `whisper-1` does not appear on the page at all; the row with this price is named "Whisper."
- **Why it stays open:** the page belongs to the provider. What this project can do is state the last step instead
  of assuming it, which the code comment next to the number already does.

### 30. A model's minimum duration is a third length boundary the display does not know (low)

- **Location:** `AssemblyAiAdapter.MIN_DURATION_MS` (160ms) and `GroqAdapter.MIN_DURATION_MS` (10ms) vs.
  `MainViewModel.sourceTooLong` and `MainViewModel.estimatedCostMicrousd`.
- **Precondition:** a source under 160ms for AssemblyAI or under 10ms for Groq.
- **Expected vs. actual:** the cost line does not price a source that is too long, but the same does not hold at
  the other end: a source under the minimum duration shows a tiny estimated price, while submission would reject
  it with `INVALID_INPUT`.
- **Why it stays open:** a source under a tenth of a second is unrealistic for a transcription tool, and the fix
  would be a third condition inside a rule named "too long" — a too-short source needs a different statement, not
  the same one. Deliberately recorded rather than fixed in passing.

### 33. `setBackoffCriteria` on the acquisition job never engages (low, informational)

- **Location:** `JobCoordinator.kt`, `.setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)`.
- **Precondition:** none; the line is in every enqueued acquisition job.
- **Expected vs. actual:** WorkManager applies a backoff rule after `Result.retry()`. No worker ever returns
  `Result.retry()` — the app schedules retries itself via `nextAt` in the database and fresh enqueuing. The line
  suggests a mechanism that does not operate here.
- **Why it stays open:** whether WorkManager still applies the rule to a worker the system stopped and rescheduled
  is unchecked on-device. Removing the line could change exactly that path, and changing unmeasured behavior would
  be worse than a misleading line.

### 34. The number-check lexer does not understand a string inside a string template (low, harmless today)

- **Location:** `codeOnly` in `StatedNumbersTest.kt`.
- **Precondition:** a string template whose expression itself contains a string, and inside that inner string a
  comment opener, e.g. `"${x ?: "/*"}"`.
- **Expected vs. actual:** the lexer ends the outer string at the first inner quote and reads the inner string's
  contents as code. A `/*` there opens a comment that does not exist; a `//` discards the rest of the line. Either
  can silently swallow a real declaration.
- **Why it stays open:** across the tree the lexer reads the same number of constants as the scanner before it,
  measured against the model, and no template in the tree contains `/*` or `//` inside an inner string today.
  Modeling nested templates would mean tracking a stack of lexer states — more code that would itself need
  checking, for a case that does not exist.

### 38. That every draft change goes through `withDraft` is a convention, not a barrier (low, harmless today)

- **Location:** `ScreenState.draft` and `withDraft` in `MainViewModel.kt`, `DraftTextField` in `NewSourceScreen.kt`.
- **Precondition:** a future call site in the view model sets the draft with `copy(draft = …)` instead of via
  `withDraft`.
- **Expected vs. actual:** a job-settings text field shows its typed text only while its setting's epoch matches,
  and `withDraft` advances the epoch of any setting a change displaces. A change bypassing `withDraft` advances
  nothing, so a previously typed field would keep showing stale text over a draft that no longer contains it.
- **Why it stays open:** confirmed still true after the 0.4.0 rebuild of the new-source screen — today eight call
  sites write the draft, all through `withDraft`: `inspect`, `selectTrack`, `startPreviews`, `clearPreview`,
  `changeDraft`, `importAudio`, `prepareAgain`, and `deleteCredential`. A real barrier would be a class for
  draft-plus-epochs with a private constructor whose only mutator advances both together, touching every site that
  builds `ScreenState(draft = …)`, tests included.

### 39. History's elapsed-time display reserves space for at most three-digit hours (low)

- **Location:** `LONGEST_ELAPSED = "000:00:00"` in `HistoryScreen.kt` vs. `duration` in `Labels.kt`.
- **Precondition:** a job sits on `WAITING_REMOTE` for 1000 hours or more (about 41 days).
- **Expected vs. actual:** `duration` does not cap the hours; from 1000 hours it shows `1000:00:00`, one character
  wider than the reserved placeholder, so the card can grow one line taller, once.
- **Why it stays open:** a job waiting that long is itself the unusual case this is meant to surface, not hide.

### 40. A short window sits between checksum and start of an engine (low, residual risk from S1)

- **Location:** `EngineUpdateManager.file` checks the slot's SHA-256 via `validSlot` on every call; the file is
  started afterward in `NativeRuntime`.
- **Precondition:** a process able to write with the app's UID replaces the file exactly between check and start.
- **Why it stays open:** anyone writing with the same UID does not need this window — they can already change app
  data and slots directly. `docs/SECURITY_UPDATES.md` names exactly this in S1 as an accepted residual risk.

### 43. If an update fails after cleanup, the removed installation stays removed (low)

- **Location:** `EngineUpdateManager.stage`: `materializeSlot` cleans up, then `verifyRuntimeCompatibility` runs.
- **Flow:** cleanup happens only after download and signature verification, but before the runtime self-test. If
  the new engine fails it, the new slot is gone and so is the cleaned-up one.
- **Why it stays open:** running the self-test before cleanup would mean executing a file outside its hash slot,
  which `file()` deliberately disallows. Only an installation nothing references is ever cleaned up anyway
  ([ADR 0009](adr/0009-engine-slot-cleanup.md)).

### 44. `ENGINE_SLOTS_IN_USE` names an update step even where nobody is updating (low)

- **Location:** `stepText` in `Labels.kt`, which prefixes every code with the `ENGINE_` prefix with "While updating
  the extraction component."
- **Flow:** if the bundled engine finds no room after an app update, every caller that first runs
  `ensureBundledLocked` ends with this code, and the display names the update step, even though the reader updated
  nothing.
- **Why it stays open:** the sentence after it is correct, only the step name is wrong, and the case requires
  unfinished jobs bound to at least three engines that are neither active nor previous.

### 45. `ArtifactRow.complete`'s STT writer has no dedicated test (low, harmless today)

- **Location:** `SttStep.kt`, `complete = document.scope.confirmedComplete`.
- **Why it stays open:** the difference from `technicallyComplete` only shows with a scope whose flag is set and a
  section missing, and the STT branch never produces that; a test would have to push it past normalization
  straight into the storage step. `JobCoordinator`'s two writers are covered
  (`AppPipelineTest.aResultWithAMissingChunkIsStoredAsPartialByBothArtifactWriters`).

### 48. `stage` checks references separately before download and during cleanup (low)

- **Location:** `EngineUpdateManager.stage`: `ensureRoomLocked(update.sha256, remove = false)` before download,
  `materializeSlot` with `ensureRoomLocked(installation.id, remove = true)` afterward. `JobCoordinator.retry` gives
  a "missing only" retry its predecessor's engine without asking the manager.
- **Precondition:** five occupied slots where exactly one installation is dispensable, and a job with a partial
  result whose last STT attempt was bound to exactly that one.
- **Flow:** the dry run finds room. During the throttled download, a "missing only" attempt for this job starts;
  it is unfinished and holds its predecessor's engine. Cleanup after download then finds no room, `stage` ends
  with `SLOTS_IN_USE`, the verified download is discarded, and nothing is removed.
- **Why it barely happens today:** update and retry both run through `MainViewModel.action`, whose lock allows one
  action at a time, and `stageAndActivate` holds it for the whole download. A second `MainActivity` instance with
  its own view model is not excluded, and unverified on-device.
- **Why it stays open:** the outcome is safe and has its own text; it costs the download. Holding references under
  the manager's lock would mean jobs wait on the manager for their own Room writes; [ADR 0009](adr/0009-engine-slot-cleanup.md) names the case.

### 49. A preview does not pin its engine (low)

- **Location:** `JobCoordinator.inspect` passes `engines.file(engines.active())` to `extractor.resolve` without
  creating an attempt; `EngineReferences.inUse` only knows attempts.
- **What it would take:** while yt-dlp runs for the preview, its engine would need to become neither active nor
  previous and then get cleaned up, meaning two activations and a cleanup inside one call.
- **Why it barely happens today:** `inspect` and `prepareAgain` run under the same `MainViewModel.action` lock as
  `stageAndActivate` and `rollback`, and only those two activate, revert, or clean up for an update.
- **Why it is still recorded:** safety here rests on a view-model lock, not on the manager. A future caller of
  `stage`, `activate`, or `rollback` outside that lock opens the window.

### 50. Instrumentation tests enqueue work in the app's own WorkManager (low, harmless today)

- **Location:** `ensureWorkManager` in several `app/src/androidTest` classes; `SourceScribeApplication` as
  `Configuration.Provider`.
- **What happens:** tests run in the app's own process, so `WorkManager.getInstance` there returns the app's own
  instance with its own database, and the fallback to `WorkManagerTestInitHelper` is never reached. Each test
  environment builds its own `JobCoordinator` with its own Room database but enqueues work into this same
  WorkManager database.
- **Why harmless today:** the IDs are random UUIDs, and tests only cancel work tagged with their own job IDs during
  cleanup. A worker whose attempt does not exist in the app's database finds nothing and ends without effect.
- **Why it stays open:** the fix is a dedicated WorkManager instance for tests, which changes how these classes run
  their workers.

### 51. Migration tests create their databases next to the app's own (low, harmless today)

- **Location:** `MigrationTest`: `MigrationTestHelper` names `migration-<UUID>.db` inside the app's own context.
- **What happens:** an outer rule deletes each test's database plus `-journal`, `-shm`, `-wal`, and `.lck`
  afterward and checks nothing with its name remains.
- **Why harmless today:** the names never collide with the app's own, and only what the tests themselves create
  gets deleted. If the process dies mid-test, the files remain.
- **Why it stays open:** a separate directory would need absolute paths as database names, or a context with its
  own database directory; whether `MigrationTestHelper` and the `.lck` lock file work with that is unchecked.

### 52. `SettingsStore` resolves its DataStore in the constructor and keeps it for the process's lifetime (low)

- **Location:** `settingsDataStore` and the `stores` map in `SettingsStore.kt`, called from the constructor.
- **What happens:** the constructor calls `preferencesDataStoreFile`, which asks `Context.getFilesDir()`, and
  `File.canonicalPath`, which resolves the path on the filesystem and may throw `IOException`. Hilt builds the
  `@Singleton` on first need, and `MainViewModel`, created on the main thread, is among the consumers.
- **Why low:** the app has one path and therefore one entry; DataStore forbids two instances for the same file.
  Further entries only arise in instrumentation tests with their own contexts. Whether the constructor access is
  ever noticeably slow or throws is unmeasured. Confirmed unaffected by 0.4.0's addition of keyterm sets and
  per-format export folders to the same store (0.4.0 added validation calls, not a new resolution path).
- **Missing to close:** resolve the DataStore only on first read or write, without allowing two stores for the
  same file.

### 53. After a failed second rename, stale metadata can remain in the slot (low, harmless today)

- **Location:** `replaceInSlot` in `EngineUpdateManager.kt`.
- **What happens:** when repairing a damaged bundled-engine slot, `replaceInSlot` first renames the verified file
  into the slot, then `metadata.json`. If only the second rename fails, the call ends with `STORAGE`, leaving the
  verified file in the slot next to the metadata that was there before. The next call finds the slot valid, since
  `validSlot` only checks the file, and never rewrites the metadata.
- **Why harmless:** `metadata.json` is only written and moved, never read anywhere. In the normal case, the stale
  metadata describes an earlier setup of the same hash.
- **Missing to close:** whoever reads `metadata.json` in the future must account for metadata from an earlier
  setup, or reconcile it while checking the slot; see [ADR 0011](adr/0011-damaged-bundled-engine-slot.md).

### 61. Readability of a very large response is decided from only its first 100,000 entries (low, speculative)

- **Location:** `SyncTranscriptParser.parseSections`, `MAX_SEGMENTS` = 100,000.
- **Precondition:** a provider response with more than 100,000 segment entries, every one of the first 100,000
  unreadable, and at least one readable entry beyond that point.
- **Expected vs. actual:** the response should be read as partially readable. Because readability is decided from
  `entries.take(MAX_SEGMENTS)`, such a response would be misclassified as fully unreadable and fall back to full
  text even though a later section could have been read.
- **Why it stays open:** far outside any response a real audio length could produce today, and speculative rather
  than demonstrated. Confirmed untouched by the 0.4.0 work (only `responseLanguages`, a different function in the
  same file, changed).
- **Missing to close:** nothing planned; recorded so a future change to how segments are batched does not
  reintroduce a silent version of the same cap unnoticed.

### 62. A storage failure right after a post-update re-check can make a just-verified engine throw instead of load (low, unverified)

- **Location:** `EngineUpdateManager.ensureBundledLocked`: `recheckActiveLocked` runs before the bundled engine's
  own cache and marker are set.
- **Precondition:** the post-update re-check marks a non-bundled engine unhealthy (or clears an old health-loss
  reason once it is confirmed again), and the `saveStateLocked` call that records that itself fails, for instance
  with a storage error.
- **Expected vs. actual:** the bundled engine, which the same call just finished verifying, should still become
  usable. Instead the storage exception propagates out of `ensureBundledLocked` before the in-memory cache and the
  marker file are written, so `bundled()` and `active()` throw even though nothing is wrong with the bundled engine
  itself.
- **Why it stays open:** needs two independent failures at once, and is unverified. Confirmed still present after
  the 0.4.0 change to this function (`5332af8`): `recheckActiveLocked` still calls `saveStateLocked` on its own,
  ahead of `bundledCache = healthy` and the marker write, now also when it clears an old `healthLoss` reason.
- **Missing to close:** write the marker and cache before, or independently of, saving the re-check's health
  result, or retry the whole check on the next call regardless of which half failed partway.

### 65. Nothing verifies the keyterm-set controls or the export-folder rows on a device (P4)

- **Location:** `NewSourceScreen.kt` (keyterm-set controls, item 13 of the 0.4.0 plan) and `SettingsScreen.kt`
  (per-format export folders, item 14).
- **What exists:** the underlying rules are unit-tested (`KeytermSetsTest`, `ExportTargetsTest`,
  `SettingsStoreTest`) and both controls were seen on screen and photographed during package B's device check, but
  no test saves a keyterm set through the screen and loads it again, and none opens an export folder through the
  screen.
- **Effort to close:** about two hours for an instrumented flow that drives dialogs through accessibility actions.

### 66. `openFolder` has never been run (P4)

- **Location:** `SettingsScreen.kt` / `HistoryScreen.kt`, the "open folder" action added for item 14 of the 0.4.0
  plan (`ACTION_VIEW` on a document URI built from the export tree, with `NO_FOLDER_APP` as the documented failure
  path).
- **Why it stays open:** no device available during the 0.4.0 work had a file manager that handles that intent, so
  neither branch — a successful open or the `NO_FOLDER_APP` fallback — has actually been executed.
- **Effort to close:** minutes on a real phone, or about an hour to write an instrumented test that resolves the
  intent rather than starting it.

### 67. The engine list's new health-state line has no test of its own (P4)

- **Location:** `Labels.engineRowState` (added for item 16 of the 0.4.0 plan) and its use in `SettingsScreen.kt`.
- **What exists:** it is a plain `when` over `healthy`/`healthLoss`/`activeEngineId`; the manager side that feeds
  it is covered by `EngineUpdateManagerTest`, but the four displayed shapes ("In use", "Checked, not in use", "Not
  checked", and the two replacement sentences) were not seen on a device in the 0.4.0 work — reaching the
  replaced-engine state on screen needs an app update with a previously activated, now-replaced engine.
- **Effort to close:** about half an hour for a layout/semantics test over the four shapes, or nothing if a future
  upgrade test happens to show it.

### 68. Nothing drives the new job-actions dialog's buttons on a device (P4)

- **Location:** `HistoryScreen.kt`'s `JobActionsContent`, added for item 15 of the 0.4.0 plan.
- **What exists:** the recommendation rule has five unit tests plus a layout test, and the dialog was opened, read,
  and photographed on the emulator during package C's device check, but no test presses "Run this job again" (or
  any other action) and checks that a new attempt actually appears.
- **Effort to close:** about two hours for an instrumented flow that drives the dialog through accessibility
  actions.

### 69. A provider-reported language that is neither a code nor a name the JVM knows still disappears without a trace (P4)

- **Location:** `LanguageNames.codeFor`, added for item 17 of the 0.4.0 plan.
- **Precondition:** a provider names its language in some third shape — neither a two-letter code nor a full name
  `Locale.getISOLanguages()` recognizes.
- **Expected vs. actual:** unchanged from before 0.4.0: such a value is dropped without a trace, exactly as Groq's
  full-word language was dropped until today's fix.
- **Why it stays open:** not a regression — the 0.4.0 fix closes the one shape that was actually observed (Groq's
  English word) and leaves the general problem exactly where it was. A provider that starts writing something else
  there would silently produce transcripts with no language again.
- **Missing to close:** about an hour — a warning code of its own plus its sentence and its place in
  `TranscriptWarnings`, which needs a decision, because such a warning would then appear on every job of a provider
  that does this.

### 70. `JobActions.actionFor`'s classification is reasoned from code for most of the 197 codes it covers, not observed (P4)

- **Location:** `JobActions.kt`, added for item 15 of the 0.4.0 plan; the inventory it classifies is
  `ShownCodes.ALL` (`app/src/androidTest/java/app/sourcescribe/ShownCodes.kt`).
- **What exists:** every code was placed from where it is raised and what `resume`/`retry` do with it. The ones
  with real evidence behind them are `ENGINE_NOT_AVAILABLE` (the round-24 review that found the dead-end "Resume"),
  `PREPARED_AUDIO_INVALID` (the 0.4.0 P1 fix), and the authentication codes (`resume` un-rejects them in code, read
  and confirmed). The rest of the 197 are reasoned, not observed against a real job that actually stopped there.
- **Why it stays open:** a wrong answer here costs a reader one wasted button press, not data or money — the
  dialog still lists every other action alongside the recommended one.
- **Effort to close:** none planned; would need a real job hitting each of the remaining codes, which is not
  something to manufacture on purpose.

### 71. The reserved note line under four provider-decided switches costs a blank line when it has nothing to say (P4, deliberate)

- **Location:** `Components.kt`'s `Toggle` (`supportingReserve`), used by the four provider-capability switches in
  `NewSourceScreen.kt` added for items 8 and 9 of the 0.4.0 plan.
- **What happens:** the note line under speaker separation, word timestamps, segment timestamps, and context terms
  always reserves its height, so when a provider supports the option, the line is present but empty.
- **Why it stands (a deliberate decision, not an oversight):** the alternative — the note appearing only when there
  is something to say — was measured to move the whole advanced-options block by 84 pixels at font scale 2 every
  time the provider changes, which the owner's standing no-jump rule forbids. The empty line is the smaller,
  chosen cost.
- **Effort to change:** about half an hour to move the note into the help topic instead, but that trades the blank
  line for the exact layout shift this decision avoids — a question for the owner, not a defect to fix silently.

## User decisions

These items are design questions; nothing about them changes without asking first.

- **Item 46(b), engines:** someone who activates a downloaded engine and then reverts to the bundled one can no
  longer reach the downloaded one via "Revert to previous engine" after an app update brings a new engine. Either
  this stays as is, with the way back reaching one step, or a new feature is added: activating any installed
  engine directly, with the same confirmation as reverting.
