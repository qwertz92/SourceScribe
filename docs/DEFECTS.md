# Known issues and open items

**As of:** September 14, 2026, after twenty-three rounds of adversarial review; the user then paused the loop. Open
items ordered by user priority are in [BUGS.md](BUGS.md). This file is for the next agent and lists what is **not**
fully done. A closed item keeps its number and a short note so references from other documents stay valid. What is
not listed here is either done or described in [STATUS.md](STATUS.md).

Each entry names the file and location, the precondition, expected versus actual behavior, and what is missing to
close it. Entries without a reproducible flow are marked **unverified**; they are suspected cases, not demonstrated
defects. A location names the file and function, or quotes the expression — never a source line number: line numbers
shift with every change above them, and `tools/check-repository.py` rejects them in `docs/`.

## Feedback from the September 10, 2026 test

The user's list had 20 items. 17 of them are implemented and demonstrated on the emulator or through tests. These
three are open or only partly closed:

### 1. Result view scrolling "not at the right edge, only halfway" — cause never reproduced

- **Location:** `app/src/main/java/app/sourcescribe/ui/TranscriptScreen.kt`
- **What changed:** The view was a fixed-size `Dialog` (94% width, 92% height). It is now a dedicated full-screen
  destination with `windowInsetsPadding(WindowInsets.safeDrawing)`, a single `LazyColumn`, and a fixed footer,
  removing fixed fractional heights and hidden margins as possible causes.
- **What is demonstrated:** verified on `emulator-5556` on September 11, 2026 with a real 286-section transcript:
  swiping at the right edge scrolls, and the list runs to the last section. Full-text search keeps filtering
  correctly, including with the keyboard open.
- **What is missing:** landscape orientation and a second device. The originally reported flow was not reproduced;
  whether it still occurs on the user's device is unverified.

### 2. Bottom action button "could use more space"

- **Location:** `NewSourceScreen.kt` ("Check source" / "Confirm and start"), `TranscriptScreen.kt` (footer)
- **Status:** all primary buttons are now at least 52dp tall and full width. Whether this matches what was meant
  was never asked — the wording leaves open whether it was about height, distance to the navigation bar, or thumb
  reachability.
- **What is missing:** asking the user, or a before/after screenshot comparison.

### 3. AssemblyAI EU/US: glossary claim not checked against current provider documentation

- **Location:** `app/src/main/res/values/strings.xml`, `help_region_body`
- **Demonstrated:** the adapter genuinely uses two different base URLs (`api.assemblyai.com` vs.
  `api.eu.assemblyai.com`, `core/.../providers/AssemblyAiAdapter.kt`), and OpenAI and Groq only know `Region.US`.
  The glossary states this correctly.
- **Unverified:** that an AssemblyAI key is bound to exactly one region and that a call in the wrong region ends in
  an authentication error. This matches the known product design but was not checked against AssemblyAI's current
  documentation this session. To close: read the primary source, confirm or correct the sentence, and date it.

## Open review findings

### 4. Internal integrity codes without their own text (medium)

- **Location:** `app/src/main/java/app/sourcescribe/ui/Labels.kt`, `messageText`
- **Status:** the families a user meets in everyday use have their own text: invalid links, provider errors,
  extraction errors, engine updates, audio import, and, since the second review round, nine of the ten local audio
  preparation codes (`AudioPreparationCode`). The tenth, `AUDIO_STORAGE_FAILED`, still shares the collective text
  `reason_storage` with six other prefixes; that is acceptable because a storage error names the cause in every one
  of these cases. The `else` branch still shows "Operation could not be completed" plus the technical status.
- **Open:** at least 43 codes still fall into that branch, counted on September 11, 2026 from the sites that write
  the fields `messageText` reads (`SttStep`, `JobCoordinator`, `ExportStore`, `MainViewModel`) against the codes the
  `when` answers.
- **That count is provably incomplete**, found in round 9: `JobCoordinator`'s `catch` maps exceptions to error codes
  and copies `CaptionParseException.reason`, `ArtifactFilesException.reason`, and `StorageBudgetException.reason`
  straight through. Those three classes' codes live in `core/.../CaptionParser.kt`, `core/.../ArtifactFiles.kt`, and
  `app/.../data/StorageBudget.kt` — none were in the count above. `PATH_ESCAPE` and `RAW_HASH_MISMATCH`, this item's
  two original examples, come from exactly there. Closing this item means starting from those three files and
  recounting; 43 is a lower bound, not a count.
- **The item's own premise does not survive the count:** these codes do *not* all mean "an internal binding or
  validation step failed." A good share are ordinary operational outcomes — `INTERRUPTED`, `REMOTE_TIMEOUT`,
  `NO_TRANSCRIPT`, `ENGINE_NOT_AVAILABLE`, `AUDIO_TRACK_MISSING` — for which that sentence would simply be wrong. A
  single sentence for the whole branch would commit exactly the error this review loop otherwise hunts: claiming a
  cause that is not the one that occurred.
- **Two codes were wrongly listed here, found in round 9.** `RESPONSE_NOT_READY` (`SttStep.normalize`) arises from
  `state != RESPONSE_SAVED` **or** from a `bindingMatches` failure — the second case genuinely is a binding check,
  so this code belongs in both camps and needs wording that covers both. For `SOURCE_NOT_FOUND` it is open whether
  it is reachable in normal operation at all: both foreign keys involved are `RESTRICT`, and `JobCoordinator` waits
  for work to cancel before deleting. That points more toward an internal-check problem than an ordinary outcome;
  neither is proven.
- **What this means for the fix:** the integrity codes need an explicitly enumerated list, not a name pattern —
  a pattern claims every future code that happens to end in the same word, which item 16 already flags as a risk
  for `RESPONSE_`. Operational outcomes need their own text or stay deliberately generic. Eleven further names in
  the count are export states and validation values not yet shown to reach `messageText` at all.
- **Since round 17,** `ENGINE_NOT_AVAILABLE`, one of the examples above, has its own text with no step name, since
  no update step failed, and `ENGINE_SLOTS_IN_USE` is new with its own text. The lower bound of 43 has not been
  recounted since.

### 5. The pre-check and the actual limit measure two different durations (medium, partly unverified)

- **Location:** `core/.../JobLimits.exceeds` (preview) vs. `app/.../data/SttStep.kt` (`preparation.probe`)
- **Precondition:** the preview checks the video length yt-dlp reports; the actual limit checks the measured
  length of the downloaded audio track. Both compare strictly with `>`, no tolerance.
- **Consequence:** if the two values differ by milliseconds, a job can pass the preview and then end with
  `AUDIO_LONGER_THAN_LIMIT`. That costs a download but no money — the abort happens before `SUBMIT`. Conversely,
  the preview can block a job that would have gone through.
- **Unverified:** how often and how far the two values diverge in practice was not measured. To close: log both
  values on several real videos and decide from that whether a tolerance (analogous to
  `PREPARED_DURATION_TOLERANCE_MS`) is justified. A tolerance would widen the cost boundary slightly and must not
  be introduced without that measurement.

### 6. Six-byte hash suffix in the generated file name — fixed September 11, 2026

Kept as a number so references stay valid. The hash is now six bytes wide (`TranscriptExporter.SHORT_ID_BYTES`),
pushing the same collision probability past sixteen million names.
`theIdentityInAGeneratedNameIsADigestOfAStatedWidth` pins the width and that it is a hash, not a prefix.

### 7. A deleted export document stayed counted as a taken name — fixed September 11, 2026

Kept as a number so references stay valid. `reconcile` now clears `documentUri` exactly when the provider was asked
and confirmed the document gone, rather than on every failure (which would itself have been a defect, since a write
interrupted mid-line still owns a real file). Two tests cover both cases:
`aChosenNameIsFreeAgainOnceItsDocumentIsProvenGone` and `anInterruptedExportKeepsHoldingItsNameBecauseItsFileIsThere`.
Item 13 remains open.

### 8. AssemblyAI reports a model with no length check — fixed September 11, 2026

Kept as a number so references stay valid. `speech_model_used` is now checked against the same 128-character limit
already used for OpenAI and Groq, and rejected with `REPORTED_MODEL_TOO_LONG` instead of being shown when too long
(not truncated, since a truncated name would be a value nobody reported).
`aReportedModelIsRefusedRatherThanShownAtAnyLength` covers both sides of the limit. Whether AssemblyAI ever reports
anything but a short model name remains unverified — no real provider run has happened in this project — but the
check does not depend on that.

### 9. Warning codes in the result view — fixed September 11, 2026

Kept as a number so references stay valid. Over 50 code families now map to thirteen sentences
(`core/.../TranscriptWarnings.kt`, testable without a device). A second list, copied from the generators, catches
any name that drifts from its generator. Raw codes still show under result-view details; an unmapped code still
displays technically instead of being swallowed.

### 10. One error text covers two different causes (low, unverified)

- **Location:** `app/src/main/java/app/sourcescribe/ui/Labels.kt`, `AUDIO_INVALID_INPUT` / `AUDIO_INPUT_NOT_FILE`
  branch
- **Status:** both show "This job's audio file was not readable." That is correct for `AUDIO_INPUT_NOT_FILE`.
  `AUDIO_INVALID_INPUT` instead arises in `AudioPreparation.validateChunkRequest` from an impossible parameter — a
  programming error, not a broken file.
- **Unverified:** no trigger in normal use was found; the check guards against internal misuse.
- **Missing:** either dedicated text in the internal-integrity-code family (item 4), or proof the code never
  reaches the user.

### 11. A change to warning generation permanently blocks reusing paid sections (low)

- **Location:** `app/src/main/java/app/sourcescribe/data/SttStep.kt`, `prepareMissingRetry()`: the comparison
  `artifact.warningCount != partial.warnings.size` and the comparison against `providerWarnings.distinct()`
- **Precondition:** a job is `PARTIAL_SUCCESS`. For an already-paid section, the response had more than 64 distinct
  warnings, and the app was later updated to a build with the warning cap.
- **Flow:** "Retry only missing sections" re-parses the stored raw data and compares it to what was saved on the
  first run. The new parse yields 65 entries instead of the old count, the comparison fails, and the path is
  permanently refused with `MISSING_RETRY_DATA`.
- **What is already fine:** the failure is safe, not silent. `JobCoordinator.retry` catches it before the
  transaction; no cost, no silent retry, and the partial state is preserved.
  `SttMissingRetryTest.retainedProviderWarningsMustMatchPartialArtifact` covers exactly this with
  `assertEquals(0, requestAttempts.get())`.
- **Not reachable today:** no real provider run has happened in this project (see item 8), so no stored partial
  state with provider warnings exists yet. It becomes reachable once real runs exist and warning generation is
  changed again afterward.
- **On September 11, 2026 that exact kind of change happened, a second case of it:** `SyncTranscriptParser` now
  reports `REPORTED_MODEL_MALFORMED` for a present but unusable `model` field, where it used to stay silent. A
  stored response with `"model":""` therefore yields one more warning than the count on re-parse, and "missing
  sections only" is permanently refused for that partial state. The failure stays the safe one described above —
  no cost risk, no silent retry, partial state kept — but it shows this is not a one-off: every future correction
  to warning generation hits it again as long as `normalizationVersion` is undecided.
- **Missing:** `TranscriptDocument.normalizationVersion` in `core/.../Domain.kt` is fixed at `"1"`, never bumped,
  never checked — only shown in export. It appears meant for exactly this case. To close: write a short
  architecture decision on what a version mismatch should mean (skip the comparison? re-normalize the partial
  state?), then implement it. This touches job semantics and must not happen incidentally inside another fix.

### 12. `abr` overrides `tbr` even when its value is then discarded (low)

- **Location:** `core/src/main/kotlin/app/sourcescribe/core/ExtractorMetadata.kt`, `rate`
- **Precondition:** a format carries a number outside 1..10000 in `abr` and a usable one in `tbr`.
- **Expected vs. actual:** the usable value should win. Instead `abr` wins merely by being present; when it then
  fails the range check, `bitrateKbps` stays empty even though `tbr` would have worked, and the track gets no size
  estimate.
- **Why it stands:** found while closing the provenance line in round 4. Which field supplies a bitrate is a data
  decision, not part of a provenance fix, so it is recorded here rather than changed in passing.
- **Missing:** a decision on switching to "first usable value," plus a test. The new test
  `provenanceDoesNotNameANumberFieldWhoseValueWasRejectedAsImplausible` pins today's behavior and would be
  deliberately updated on such a change.

### 13. An unverifiable export document is reported as provably gone (low)

- **Location:** `app/src/main/java/app/sourcescribe/data/ExportStore.kt`, `reconcile`, the `IllegalArgumentException`
  branch
- **Precondition:** the stored document URI cannot be queried at the provider at all, for instance because it is no
  longer a usable document URI.
- **Expected vs. actual:** expected is a statement of what was actually established. Instead the line carries the
  same reason, `EXTERNAL_DOCUMENT_MISSING`, as a genuinely deleted document, and the user reads "The exported file
  is no longer there," even though only the check failed — the same class of error as presenting an uncertain
  outcome as a certain one.
- **Why it stands:** found while closing item 7. The naming half is solved, because the unverifiable case keeps its
  URI and the name counts as taken out of caution; the text needs its own error code and its own sentence in both
  languages, which is a separate change.
- **Missing:** a second error code, two strings, a test with a URI that cannot be queried.

### 14. Old artifacts keep the old caption code and keep getting the timestamp sentence (low)

- **Location:** `core/src/main/kotlin/app/sourcescribe/core/CaptionParser.kt` against already-stored
  `TranscriptDocument.warnings`
- **Precondition:** a transcript created from a caption track before September 11, 2026, which recorded
  `MALFORMED_SEGMENTS_<n>`.
- **Expected vs. actual:** the caption case is now called `MALFORMED_CAPTION_SEGMENTS_<n>`, because the provider
  parser used the same name with a different meaning. New jobs get the correct sentence; an old entry still falls
  into the timestamp group and says "timestamps are missing" even though text is what is missing.
- **Why it stands:** the index that would distinguish the two sources is deliberately dropped when summarizing.
  Rewriting stored warning lists touches `normalizationVersion` and hinges on the same decision as item 11.
- **Missing:** a decision, together with item 11, on whether stored warning lists are ever migrated.

### 15. A note made of capital letters would be treated like a code (low, unverified)

- **Location:** `core/src/main/kotlin/app/sourcescribe/core/TranscriptWarnings.kt`, `looksLikeCode`
- **Precondition:** something later puts free text into `TranscriptDocument.warnings` that consists only of capital
  letters, digits, and underscores.
- **Expected vs. actual:** such text would be cut at the first colon and framed as a technical status. Not reachable
  today: the only place that puts free text into this list is the UI test fixture, and its two sentences contain
  spaces and lowercase letters.
- **Missing:** nothing, as long as the list carries codes. If it is ever meant for text, it needs its own field
  instead of a shape check.

### 16. The `RESPONSE_` branch is not protected against a future misnamed code (low)

- **Location:** `core/src/main/kotlin/app/sourcescribe/core/TranscriptWarnings.kt`, the `else` branch of `group`
- **Precondition:** someone names a new warning `RESPONSE_...` that does not mean a lost section.
- **Expected vs. actual:** it would silently be reported as a lost section, i.e. worse damage than actually
  occurred. The rule holds today: every `RESPONSE_` code actually produced arises in `SttStep` at a site that sets
  `missing += chunk.index` in the same step — verified for all twelve `ProviderErrorCode` values by
  `everyRefusalTheProviderGivesForOneSectionCountsAsALostSection`.
- **Missing:** either a closed list instead of the prefix rule, or a test that pins the coupling to `missing` at the
  point of creation.
- **Since round 7:** the count of these families is available as `TranscriptWarnings.PREFIXED_FAMILY_COUNT` and
  feeds into `Warnings`'s upper bound. That does not change the mapping question here.

### 17. "Tonspur" vs. "Audiospur": English is consistent, German is not (low)

- **Location:** `app/src/main/res/values/strings.xml`, `step_prepare_audio` and the four `reason_audio_*` strings
  against the rest of the file
- **Expected vs. actual:** English says "audio track" at all ten corresponding places. German processing text says
  "Tonspur," selection text says "Audiospur." One reading would justify this — selectable track vs. processed
  content — but `help_audio_track_body` breaks that reading itself.
- **Missing:** a decision for one word and a pass over every occurrence in one sweep.

### 18. If the whole segment list fails, the sentence claims missing text (low)

- **Location:** `core/src/main/kotlin/app/sourcescribe/core/providers/SyncTranscriptParser.kt`, `parse`, against
  `TranscriptWarnings.group` for `MALFORMED_SEGMENT`
- **Precondition:** every entry of a provider response's segment list is unreadable.
- **Expected vs. actual:** if no segment survives, the parser replaces the list with the response's full-text
  field. No text is then missing, only structure and timestamps. But the warnings look identical to a partial loss,
  and the sentence says sections are missing from the result. For the common case — a few unreadable entries — the
  sentence is correct, and only that case is dangerous, because a partial loss is quiet.
- **Why it stands:** the mapping only sees the code list and cannot tell whether the fallback engaged; that would
  need a dedicated warning at the point of creation.
- **Missing:** a warning that records the fallback from segment list to full text, plus its own sentence — also
  independently useful, since today a result with no structure at all is indistinguishable from a structured one.

### 19. The RAW rule in the retry check — fixed September 11, 2026

Recorded and closed the same day. A sibling line in `RAW` format yields `null` for its extension, because the
extension comes from the retained provider file rather than the export row, so it counts as a taken name out of
caution. `aRawSiblingCountsAsATakenNameBecauseTheRowDoesNotRecordItsExtension` now runs a real RAW export followed
by a text export of the same artifact with a self-assigned name; it gets a disambiguating suffix even though
`.json3` and `.txt` could not actually collide — the deliberately cautious side, which the test records rather than
asserts as necessary. Recording the written extension in the export row would avoid the suffix but costs a column
and a migration for a cosmetic gain, so it was not done.

### 20. The two model-length limits are independently hardcoded (low)

- **Location:** `core/.../providers/AssemblyAiAdapter.kt` and `core/.../providers/SyncTranscriptParser.kt`, each a
  private `MAX_REPORTED_MODEL_LENGTH = 128`; tests each hardcode a separate `129`
- **Precondition:** someone changes the limit at one of the two sites.
- **Expected vs. actual:** both parsers should give the same answer for the same value — the guarantee round 7
  established via the order of rejection reasons. Nothing actually ties the two numbers together: no shared
  constant, no test comparing them. The drift would only surface if someone tested both parsers with the same value.
- **Missing:** a shared location for the limit. It does not belong in either adapter, and whether `ProviderContract`
  is the right place for a parser limit is a decision, not a mechanical edit — recorded here instead of done in
  passing.
- **Since round 8, the same applies to the warning names:** `SyncTranscriptParser` writes `"REPORTED_MODEL_MALFORMED"`
  and `"REPORTED_MODEL_TOO_LONG"` as raw strings; the AssemblyAI adapter carries them as named constants with the
  same values. Renaming one side would make the two parsers report the same situation under different codes, and
  the summary would no longer group the new name. The round-8 test that checks each family against a second list
  catches that second half, not the parsers drifting apart.

### 21. A timestamp far outside the ordinary range yields no date in the file name (low)

- **Location:** `core/src/main/kotlin/app/sourcescribe/core/TranscriptExporter.kt`, `identitySuffix`,
  `createdAtUtc(document.createdAt).take(10)`
- **Precondition:** `createdAt` falls outside the years 0000–9999.
- **Expected vs. actual:** a `YYYY-MM-DD` slice is expected. Instead `Instant.toString()` writes such years with a
  sign and a variable digit count, so the first ten characters read `+292278994` — day and month are gone, and two
  runs on the same day could no longer be told apart by date in the name.
- **Why not fixed:** `createdAt` comes from the device clock, not a response; the case needs a grossly wrong clock.
  The name's byte limits still hold, since this representation is plain ASCII. Derived from the documented form of
  `Instant.toString()`, not executed — `assertEquals("+292278994", Instant.ofEpochMilli(Long.MAX_VALUE).toString().take(10))`
  would settle it. (An earlier version of this entry had the value one digit short: `Long.MAX_VALUE` milliseconds
  fall in the year 292,278,994 — nine digits plus sign, exactly the ten characters `take(10)` takes.)

### 22. The audio-track follow-up question does not show why it is asked (display: low; provenance claim: high — fixed)

- **Location:** `core/src/main/kotlin/app/sourcescribe/core/AudioTracks.kt`, `describe`, together with track
  selection in the preparation view.
- **Precondition:** two audio tracks whose language the source named, but which were longer than a hundred
  characters and therefore not carried into the record (`AudioTrack.languageRefused`).
- **Expected vs. actual:** since round 10, `automatic` correctly refuses to choose silently here — the source did
  distinguish the two. But the user then gets a list where neither track names a language, with no hint that one
  actually was named. The question is correctly asked but hard to answer.
- **Fixed since round 11 — the more serious half:** a reviewer found that `TranscriptExporter` wrote
  `language=unknown` even when the source had in fact named a language. Since the export is this project's
  provenance record, and disclosing language and uncertainty is an invariant, that was a false statement. It now
  writes `language=stated-but-unusable`.
- **Why the display half is not fixed:** unobserved in practice and practically unreachable with real yt-dlp data —
  real language tags run under twenty characters. A dedicated sentence would need two new translated strings for a
  state nobody will likely see; truncating and flagging the value as truncated would break the project's
  all-or-nothing rule. The false silent decision is fixed; the badly answerable question remains, along with
  `AudioTracks.readingOrder` deliberately keeping a refused-language track in the same sort group as one with no
  language at all, since sorting by a difference the list does not display would be unexplainable to the reader.

### 23. The maximum duration exists as an independent number in three places (low)

- **Location:** `core/src/main/kotlin/app/sourcescribe/core/JobLimits.kt` (`MAX_AUDIO_SECONDS = 36_000`) and
  `invalid_duration` in `app/src/main/res/values/strings.xml` and `values-en/strings.xml`, both saying "600 minutes."
- **Precondition:** any change to the maximum duration.
- **Expected vs. actual:** one number should imply the others. Instead three independent numbers exist; neither
  text reads the constant, so a changed constant would let the app enforce something other than what both texts
  promise.
- **Since round 10:** `JobLimitsTest` pins the constant at 36,000 seconds and 600 minutes and names the text key in
  a comment, so a change there fails a test that points at the texts. That is a bridge, not a fix: changing the
  texts without the constant still falls through no net. A format string with `%d` sourced from `MAX_AUDIO_MINUTES`
  would be the actual fix.

### 24. A price's source page is recorded but shown nowhere (low)

- **Location:** `ProviderCapabilities.pricingSource` in `core/src/main/kotlin/app/sourcescribe/core/ProviderContract.kt`,
  set by all three adapters.
- **Precondition:** none; the value always exists.
- **Expected vs. actual:** next to the cost estimate is the pricing date. That date is only useful if the reader can
  check what it was verified against; the page for that lives in the record but reaches neither the display, nor
  export, nor diagnostics. A search for `pricingSource` finds three assignments and no reader.
- **Why it stays open:** help text is static per topic and does not know the chosen provider; a dynamic paragraph
  there is a change to the help model, not a one-liner. Writing the three URLs into `help_cost_body` instead would
  state each address a second time — exactly what `StatedNumbersTest` exists to prevent. Found in round 12 while
  checking the claim that both the pricing date and its source appear next to the amount.

### 25. Groq's upload limit is the smaller of two tiers (low)

- **Location:** `GroqAdapter.MAX_UPLOAD_BYTES` in `core/src/main/kotlin/app/sourcescribe/core/providers/GroqAdapter.kt`.
- **Precondition:** a paid Groq key ("dev tier") and a file between 25 and 100MB.
- **Expected vs. actual:** Groq documents (checked September 11, 2026) 25MB for the free tier and 100MB for the paid
  tier. The app does not know the tier of a user-supplied key and holds everyone to the smaller limit; a paying user
  gets a local rejection for a file the provider would have accepted.
- **Why it stays this way:** the alternative is worse. Raising the limit turns a local rejection into a failure at
  the provider, the costlier of the two outcomes for a paid service. The real fix is a tier field in the
  credentials, not a bigger number. Since round 12, a comment and `StatedNumbersTest` say this is a decision of this
  program, not a provider figure.

### 26. "25MB" is not defined as decimal or binary at two providers (low)

- **Location:** `GroqAdapter.MAX_UPLOAD_BYTES` and `OpenAiAdapter.MAX_UPLOAD_BYTES`, both `25_000_000`.
- **Precondition:** a file between 25,000,000 and 26,214,400 bytes.
- **Expected vs. actual:** both providers write "25MB" without saying whether they mean decimal or binary. The code
  takes decimal, the smaller and safer reading — but whether the server actually cuts off at 25,000,000 or
  26,214,400 bytes is unproven.
- **Why it stays open:** only a real request at the boundary — i.e. a paid call — could settle it. Flagged as
  speculation, not a finding, by the reviewer who raised it in round 12 and marked it explicitly not executed.

### 27. A fourth cost calculation, the only one without surcharges (low, harmless today)

- **Location:** `SyncProviderSupport.validateOptions` in `core/src/main/kotlin/app/sourcescribe/core/providers/SyncTranscriptParser.kt`,
  used by `GroqAdapter` and `OpenAiAdapter`.
- **Precondition:** a Groq or OpenAI model with a priced add-on — speaker separation or a term list with its own
  hourly rate. There is none today.
- **Expected vs. actual:** four places in this program convert a duration to money; three add surcharges, this one
  does not. That this changes nothing today is checked, not assumed: Groq reports `diarization = false` for every
  model, and OpenAI's one diarizing model has `priceMicrousdPerHour = null` and is rejected a line later instead of
  estimated. Round 13 recorded the wrong reason for that — "no published price." `gpt-4o-transcribe-diarize` is in
  fact priced, but per token: $2.50 and $10.00 per million. The "$0.006/minute" nearby sits in a column the page
  itself labels "Estimated cost." A duration cannot be multiplied by a token count, so there is no hourly rate to
  carry, and `null` is correct for a different reason than stated (re-read from the page's markup on
  September 12, 2026).
- **Why it stays open:** surcharges live per adapter, not in `ProviderCapabilities`; folding them in here would mean
  adding them to the contract — a contract change, not a line. As a pre-check the site stays harmless; what binds is
  `SttStep.submit`, which checks with surcharges across the whole section plan. Since round 13 the comment says
  both. Found by the reading reviewer in round 13 while asking which calculation appears more than once in the tree.

### 28. What `tools/check-repository.py` still does not read (low)

- **Location:** `_read_text`, `_check_actions`, and `_check_notice_versions` in `tools/check-repository.py`.
- **Precondition:** a UTF-16 or UTF-32 file **without** a byte-order mark, a text file over one mebibyte, or an
  unversioned file under `.github/workflows/`.
- **Expected vs. actual:** three gaps remain after round 13 closed UTF-16 and round 14 closed UTF-32 **with** a
  mark. Without one, both are indistinguishable from binary by bytes alone and still slip through. A text file over
  `MAX_SCAN_BYTES` is cut at 1,048,576 bytes; a secret past that point is not found, and only the closing line shows
  the truncation (`… read only to 1048576 bytes`), not the exit code. `_check_actions` searches the filesystem
  instead of `git ls-files`, so it checks an unversioned workflow file for unpinned actions while the secret scan
  never sees it.
- **What round 14 closed, and why it was worse than the gap before it:** a UTF-32LE mark is `ff fe 00 00`, whose
  first two bytes are exactly a UTF-16LE mark. Round 13 checked only two bytes, so a UTF-32LE file was decoded as
  UTF-16 — text with a null byte between every character, which matches no pattern — and **counted as read**.
  Before round 13, the null-byte probe would have honestly called the same file binary. The four bytes are now
  checked first.
- **What round 15 added:** this only holds for little-endian. A UTF-32BE file begins with `00 00 fe ff`, which
  round 13's two-byte check would never have mistaken for UTF-16 — but the big-endian entry in `UTF32_BOMS` had no
  test exercising it, because `.encode("utf-32")` writes the little-endian mark on these machines; the self-test now
  includes a UTF-32BE file.
- **Why the rest stays open:** all three are empty today — no file in the tree carries a UTF-16 or UTF-32 mark, the
  only file over one mebibyte is the packed extractor engine and is genuinely binary, and `.github/workflows/`
  contains only versioned files. The further direction — checking more, not less — is the safe one for the actions
  check. Found by the reading reviewer in rounds 13 and 14, both times by running the functions outside the
  repository against purpose-built files.
- **What round 22 added:** `_check_notice_versions` only ties a version-catalogue name to a version that follows it
  on the same line (with or without a leading "v" since round 22). A version in its own table column, or a name a
  license notice spells differently than the catalogue (e.g. "Bouncy Castle PG" instead of `bcpg`), goes
  unrecognized. Today no version-catalogue name's version sits in its own table column in `THIRD_PARTY_NOTICES.md`;
  the WebP row separates name and version, and WebP is not in the catalogue. Found by the round-22 code reviewer,
  running the function against purpose-built notices.

### 29. OpenAI's pricing page does not name `whisper-1` (low)

- **Location:** `OpenAiAdapter.PRICING_SOURCE` and `PRICE_WHISPER_MICRO_USD_PER_HOUR` in
  `core/src/main/kotlin/app/sourcescribe/core/providers/OpenAiAdapter.kt`.
- **Precondition:** someone follows the URL to verify the number next to the pricing date.
- **Expected vs. actual:** the number is correct — $0.006/minute is exactly 360,000 micro-USD/hour. But the string
  `whisper-1` does not appear on the page at all (checked in the markup on September 12, 2026: zero hits); the row
  with this price is named "Whisper" and sits in the table's collapsed half. Searching for `whisper-1` only finds
  `gpt-realtime-whisper`, a different model at a different price. The row for `gpt-transcribe` is open in the table.
- **Why it stays open:** the page belongs to the provider. What this project can do is state the last step instead
  of assuming it — recorded in a comment next to the number since round 13. Depends on item 24 above: as long as
  the URL reaches nobody, this caveat reaches nobody either.

### 30. A model's minimum duration is a third length boundary the display does not know (low)

- **Location:** `AssemblyAiAdapter.MIN_DURATION_MS` (160ms) and `GroqAdapter.MIN_DURATION_MS` (10ms) vs.
  `MainViewModel.sourceTooLong` and `MainViewModel.estimatedCostMicrousd`.
- **Precondition:** a source under 160ms for AssemblyAI or under 10ms for Groq. Section planning passes it through
  as a single section, since `MIN_FINAL_CHUNK_DURATION_MS` only applies with more than one section.
- **Expected vs. actual:** round 13 stopped the cost line from pricing a source that is **too long**. The same does
  not hold at the other end: a source under the minimum duration shows a tiny estimated price, while submission
  would reject it with `INVALID_INPUT`.
- **Why it stays open:** a source under a tenth of a second is unrealistic for a transcription tool, and the fix
  would be a third condition inside a rule named "too long" — a too-short source needs a different statement, not
  the same one. Deliberately recorded rather than fixed in passing. Found by the reading reviewer in round 14.

### 31. An empty term list was only rejected at submission — fixed September 13, 2026

Raised in round 14, closed in round 15, and broader than described: the item named lists made entirely of blank
entries, but the mechanism actually caught any list with one blank entry, including a mixed one like
`["Kubernetes", ""]` — for which, despite the round-14 fix, the cost line still added the surcharge. Both were only
reachable via a stored or resumed job, since the input field strips blank lines while typing. The rule now lives
once, in `core/src/main/kotlin/app/sourcescribe/core/ContextTerms.kt`, and every site that decides about a list asks
it: both provider paths, `SttStep.validate`, the cost formula in `SttStep`, and `configError` and
`estimatedCostMicrousd` in `MainViewModel`. Covered by
`ViewRulesTest.aTermListTheProviderRefusesIsNamedAsAnErrorAndNotPriced` and two assertions in
`SttStepTest.estimateCostCeilsMinimumAndAssemblyAddonsAndBlocksUnknownPrice`. Round 17 additionally closed a related
gap: a stored `[""]` list looked like an empty field while the preview still reported `CONTEXT_TERM_BLANK`; start
now drops blank entries (`MainViewModel.configurationForStart` via `ContextTerms.withoutBlanks`), and both the error
and cost lines judge the configuration that start actually builds. A job stored before round 17 still hits
`CONTEXT_TERM_BLANK` at `SttStep.validate` even after a retry that reuses its stored configuration; "Prepare again"
and a subsequent start create a job without blank entries.

### 32. A green CI run does not show how many instrumentation tests were skipped (low)

- **Location:** `.github/workflows/android.yml`, the step running `:app:connectedDebugAndroidTest` and
  `:extractor:connectedDebugAndroidTest`, and its `cleanup()` function.
- **Precondition:** a CI run that ends green.
- **Expected vs. actual:** an instrumentation run ends green even when tests are skipped by assumption — without
  `sourcescribeEngineUpdate` that is eighteen in the `extractor` module (see the instrumentation-test maintenance
  note below). The workflow sets that flag, but a skip is not made visible: `cleanup()` only prints test reports on
  failure, and there is no `upload-artifact` step. If a larger share of the suite silently shifts to skipped later,
  say because a flag stops being passed through, CI stays green and the count only exists in files discarded with
  the runner.
- **Why it stays open:** the change would be small — a summary of `tests`, `failures`, `errors`, and `skipped` per
  report, regardless of outcome — but it is not locally runnable, and a workflow step nobody has seen work would be
  exactly the unverified claim this loop otherwise removes. Reported by the invariant reviewer in round 15; their
  claim that `cleanup()` "never reads `<skipped>`" holds only for individual elements — on failure the script also
  prints `root.attrib`, and whether a skip count lives there is unchecked against any real report.

### 33. `setBackoffCriteria` on the acquisition job never engages (low, informational)

- **Location:** `app/src/main/java/app/sourcescribe/data/JobCoordinator.kt`,
  `.setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)`.
- **Precondition:** none; the line is in every enqueued acquisition job.
- **Expected vs. actual:** WorkManager applies a backoff rule after `Result.retry()`. No worker ever returns
  `Result.retry()` — `git grep` finds it in neither `app/src/main` nor `extractor/src/main` — the app schedules
  retries itself via `nextAt` in the database and fresh enqueuing with `setInitialDelay`. The line suggests a
  mechanism that does not operate here.
- **Why it stays open:** whether WorkManager still applies the rule to a worker the system stopped and rescheduled
  is unchecked on-device. Removing the line could change exactly that path, and changing unmeasured behavior would
  be worse than a misleading line. Reported by the invariant reviewer in round 15.

### 34. The number-check lexer does not understand a string inside a string template (low, harmless today)

- **Location:** `codeOnly` in `core/src/test/kotlin/app/sourcescribe/core/StatedNumbersTest.kt`.
- **Precondition:** a string template whose expression itself contains a string, and inside that inner string a
  comment opener, e.g. `"${x ?: "/*"}"`.
- **Expected vs. actual:** the lexer ends the outer string at the first inner quote and reads the inner string's
  contents as code. A `/*` there opens a comment that does not exist, up to the next `*/` anywhere after it; a `//`
  discards the rest of the line. Either can silently swallow a real declaration, the same way as the two silent
  forms round 15 closed.
- **Why it stays open:** a search for lines with `${` and a later quote finds 54 in the `core` module, including
  templates with an inner string in `TranscriptExporter`, `ExtractorMetadata`, and `SyncTranscriptParser`, and none
  contain `/*` or `//`. Across the tree the lexer reads the same 33 constants as the scanner before it, measured
  against the model. Modeling nested templates would mean tracking a stack of lexer states — more code that would
  itself need checking, for a case that does not exist. Round 14's version had the same case worse: its
  block-comment pattern fired inside any string, not only templates. Found while asking what round 15's correction
  cannot do.
- **Addendum from round 16, the other direction:** the same bug can also fabricate a declaration. If the outer
  string ends at the inner string's opening quote, a new one begins at its closing quote and runs to the next real
  quote, and whatever sits between the two is read as code. `; const val FAKE = 1` there would make `DECLARATION`
  find a constant that does not exist, with no `/*` or `//` needed. This direction would be loud, since the tree
  comparison would fail on a name missing from the list. The round-16 code reviewer traced this by hand against the
  code, not by executing it. Recounted on September 13, 2026:
  `grep -rnoE '\$\{[^}]*"[^}]*\}' core/src/main/kotlin` finds 16 templates with an inner string, the same count
  before and after round 16's changes, none containing more than a short word like `unknown`, `WORD`, or
  `;hls-vtt-assembled`.

### 35. Line-wrapping limits on two screens are only partly measured on-device (low)

- **Location:** cost line in `NewSourceScreen.kt`, since `579f972` a `ReservedText` sized to its tallest text;
  preview error line in `NewSourceScreen.kt`; elapsed time and byte count in `HistoryScreen.kt` (`ReservedText`).
- **Measured:** before `579f972`, the cost line clipped at 200% font — the pricing date halfway in English, entirely
  in German (`emulator-5556`, 1080×2424px, 420dpi). Remeasured on September 14 at `39a1cdc`, each time fully inside
  the scrolling form: in both languages the line wraps to two lines at font scale 1.0 and 1.3 (84px and 110px tall)
  and three lines at 2.0 (252px), with the full text, pricing date included, visible in all six screenshots. Only
  `estimated_cost` is measured, not `cost_source_too_long` or `price_unknown`.
- **Not measured:** whether the history card's placeholders (`LONGEST_ELAPSED`, `LONGEST_BYTE_SIZE`) are actually
  the tallest cases, and the preview error line at 200% font.
- **A measurement trap:** uiautomator reports only a node's visible portion. On `8e41bf4` the cost line sat partly
  under the navigation bar at 130% (English) and 200% (German) and measured shorter than it actually was. A
  measurement only counts once the line sits fully inside the scrolling form.
- **Why it stays open:** a Compose UI test using `onTextLayout` to check line count and clipping is missing; it
  would be the better approach because it persists. Proposed by the round-15 code reviewer.

### 36. The preview's error line appears and disappears (low)

- **Location:** `PreviewCard` in `NewSourceScreen.kt`, the branch below the cost line.
- **Precondition:** a preview whose settings go from incomplete to complete, or a source longer than the job limit.
- **Flow:** choosing provider, model, and key. While anything is missing, the error line is shown; once nothing is
  missing, it disappears and the start button below the card shifts up by its height — the button tapped next. The
  length warning (`SOURCE_LONGER_THAN_LIMIT`) replaces the line with a sentence with its own button and different
  height.
- **Expected vs. actual:** expected is that nothing below the card moves. Since round 15, nothing moves when one
  error text replaces another; the card's height still changes on appearing and disappearing.
- **Why it stays open:** reserving the height even without an error would leave an empty area the size of the
  longest error text in the most common state. A proposal: fill that space in the valid state with a short sentence
  like "This source is ready to start," and give the length warning the same reserved height. That is a design
  decision, not a fix, so it was not made unilaterally. Found in round 15 while rebuilding the line.

### 37. Rolling back to an older engine warns but blocks nothing (low)

- **Location:** `EngineUpdateManager.rollbackTarget` and `rollback(expectedId)` in
  `extractor/src/main/java/app/sourcescribe/extractor/EngineUpdateManager.kt`, confirmed via
  `MainViewModel.prepareRollback` in `SettingsScreen.kt`. Required by `docs/SECURITY_UPDATES.md`, section S7: "flag
  rollback to known security holes as a warning and block it where applicable."
- **Precondition:** an updated engine is active, and an earlier healthy or the bundled one is available.
- **Status since round 16:** until then, a single tap switched with no warning at all, reported as a medium finding
  by the round-16 invariant reviewer. Now the button opens a confirmation naming the version that would become
  active again, stating that an older version can contain vulnerabilities a later one already closes, and that
  SourceScribe does not check for that. Only the named installation is switched to: if the target changed since
  opening, `rollback` ends with `ROLLBACK_TARGET_CHANGED` (`ENGINE_ROLLBACK_TARGET_CHANGED` in the app) and switches
  nothing.
- **Missing:** knowledge of which version has a known hole. Nothing in the tree carries that — no minimum version,
  no list of affected versions; searched on September 13, 2026 for `CVE-`, `vulnerab`, `blocklist`, `denylist`,
  `minVersion`, and the German term for "security hole." So there is nothing for a block to check against. A list
  in app code would be outdated by the next disclosed vulnerability; this needs a signed field in the engine
  package, which is a decision about the update format, not about this button.

### 38. That every draft change goes through `withDraft` is a convention, not a barrier (low, harmless today)

- **Location:** `ScreenState.draft` and `withDraft` in `app/src/main/java/app/sourcescribe/MainViewModel.kt`,
  `DraftTextField` in `app/src/main/java/app/sourcescribe/ui/NewSourceScreen.kt`.
- **Precondition:** a future call site in the view model sets the draft with `copy(draft = …)` instead of via
  `withDraft`.
- **Expected vs. actual:** a job-settings text field shows its typed text only while its setting's epoch matches,
  and `withDraft` advances the epoch of any setting a change displaces. A change bypassing `withDraft` advances
  nothing, so a previously typed field would keep showing stale text over a draft that no longer contains it — the
  bug the epochs closed in round 16.
- **Why it stays open:** today eight call sites write the draft, all through `withDraft`: `inspect`, `selectTrack`,
  `startPreviews`, `clearPreview`, `changeDraft`, `importAudio`, `prepareAgain`, and `deleteCredential`. A search for
  `draft =` outside `withDraft` finds nothing. `ViewModelStateTest` checks epochs on typing, on a preset, on track
  selection, on closing the preview, and on "Prepare again," but not individually for `inspect`, `startPreviews`,
  `importAudio`, and `deleteCredential`. A real barrier would be a class for draft-plus-epochs with a private
  constructor whose only mutator advances both together. That touches every site that builds
  `ScreenState(draft = …)`, tests included, and was therefore not done in the same round as the epochs themselves.

### 39. History's elapsed-time display reserves space for at most three-digit hours (low)

- **Location:** `LONGEST_ELAPSED = "000:00:00"` in `app/src/main/java/app/sourcescribe/ui/HistoryScreen.kt` vs.
  `duration` in `app/src/main/java/app/sourcescribe/ui/Labels.kt`.
- **Precondition:** a job sits on `WAITING_REMOTE` for 1000 hours or more (about 41 days).
- **Expected vs. actual:** `duration` does not cap the hours; from 1000 hours it shows `1000:00:00`, one character
  wider than the placeholder. `ReservedText` truncates nothing and measures the shown text, so the card only grows
  taller if that one character needs a new line, and only once, at the transition. (The round-16 code reviewer
  computed this case and wrote "11 characters"; it is 10 against 9.)
- **Why it stays open:** a wider placeholder only moves the boundary — to about 416 days at four digits — and could
  reserve a line that stays empty in the normal case at large font sizes. Whether any provider keeps a job open that
  long is unchecked.

### 40. A short window sits between checksum and start of an engine (low, residual risk from S1)

- **Location:** `EngineUpdateManager.file` in `extractor/src/main/java/app/sourcescribe/extractor/EngineUpdateManager.kt`
  checks the slot's SHA-256 via `validSlot` on every call; the file is started afterward in `NativeRuntime`.
- **Precondition:** a process able to write with the app's UID replaces the file exactly between check and start.
- **Why it stays open:** anyone writing with the same UID does not need this window — they can already change app
  data and slots directly. `docs/SECURITY_UPDATES.md` names exactly this in S1 as an accepted residual risk: a
  process with its own PID but the same Android UID stays inside the same trust boundary. Nobody has shown a path
  beyond that; the round-16 invariant reviewer classified the point itself as speculative.

### 41. `youtube-nocookie.com` is not recognized as a YouTube source (low, safe direction)

- **Location:** `youtubeHosts` in `core/src/main/kotlin/app/sourcescribe/core/SourceResolver.kt`, checked in
  `SourceResolver.youtube`.
- **Precondition:** a link of the form `https://www.youtube-nocookie.com/embed/<id>`.
- **Expected vs. actual:** the embed address is rejected with `INVALID_HOST`, while `youtube.com/embed/<id>` is
  accepted. Nothing is processed incorrectly; the link is simply not accepted.
- **Why it stays open:** not requested, and not worth adding without a test for exactly this form that also shows
  the canonical address stays the same. Found by the round-16 invariant reviewer.

### 42. Shared exports remain in the cache (low, hygiene)

- **Location:** `shareArtifact` and `shareDiagnostics` in `app/src/main/java/app/sourcescribe/MainViewModel.kt` write
  to `cacheDir/shares/`.
- **Expected vs. actual:** the file stays after sharing until Android clears the cache, including a full transcript.
  The folder is inside the app-private cache; this creates no new exposure path.
- **Why it stays open:** SourceScribe never learns when the receiving app has read the file. Deleting on the next
  share or at app start could take the file away from an app that reads it only later, say a draft email — plausible
  but unverified. A time limit is possible, but any number for it would be a guess. Found by the round-16 invariant
  reviewer.

### 43. If an update fails after cleanup, the removed installation stays removed (low)

- **Location:** `EngineUpdateManager.stage` in `extractor/src/main/java/app/sourcescribe/extractor/EngineUpdateManager.kt`:
  `materializeSlot` cleans up, then `verifyRuntimeCompatibility` runs.
- **Flow:** cleanup happens only after download and signature verification, but before the runtime self-test. If the
  new engine fails it, the new slot is gone and so is the cleaned-up one.
- **Why it stays open:** running the self-test before cleanup would mean executing a file outside its hash slot,
  which `file()` deliberately disallows. Only an installation nothing references is ever cleaned up anyway
  ([ADR 0009](adr/0009-engine-slot-cleanup.md)). Raised in round 17.

### 44. `ENGINE_SLOTS_IN_USE` names an update step even where nobody is updating (low)

- **Location:** `stepText` in `app/src/main/java/app/sourcescribe/ui/Labels.kt`, which prefixes every code with the
  `ENGINE_` prefix with "While updating the extraction component."
- **Flow:** if the bundled engine finds no room after an app update, every caller of the manager that first runs
  `ensureBundledLocked` — preview, start, and a job looking up its engine — ends with this code, and the display
  names the update step, even though the user updated nothing; the app was setting up its own bundled engine.
- **Why it stays open:** the sentence after it is correct, only the step name is wrong, and the case requires
  unfinished jobs bound to at least three engines that are neither active nor previous. Raised in round 17.

### 45. `ArtifactRow.complete`'s STT writer has no dedicated test (low, harmless today)

- **Location:** `app/src/main/java/app/sourcescribe/data/SttStep.kt`, `complete = document.scope.confirmedComplete`.
- **Why it stays open:** the difference from `technicallyComplete` only shows with a scope whose flag is set and a
  section missing, and the STT branch never produces that; a test would have to push it past normalization straight
  into the storage step. `JobCoordinator`'s two writers are covered
  (`AppPipelineTest.aResultWithAMissingChunkIsStoredAsPartialByBothArtifactWriters`); the counter-check is recorded
  in STATUS under round 17. Raised in round 17.

### 46. Two edge cases in switching to a newly bundled engine (low)

- **Location:** `EngineUpdateManager.ensureBundledLocked`, the branch for a newly bundled engine;
  [ADR 0010](adr/0010-bundled-engine-after-app-update.md).
- **(a) An already-loaded version comes back bundled.** If someone had loaded, as an update, exactly the version an
  app update now bundles, and had since reverted to the old bundled one, the app update still switches to it,
  because its entry was never marked bundled.
- **(b) A third engine loses its place as "previous."** Someone activates a loaded engine D and reverts to bundled
  A; D is now "previous." An app update brings bundled engine C; C becomes active and A becomes previous. D is then
  no longer reachable via "Revert to previous engine." It still lists in settings, which is display-only, and an
  update only offers the channel's newest release. It can be cleared up during cleanup, since nothing protects it
  anymore. Reported by the round-18 code reviewer, who rated it medium.
- **Why low and open:** any manual activation displaces the previous engine the same way; in this model the way back
  reaches exactly one step, and the app update is itself an explicit action. Activating any installed engine
  directly would be a new feature with the same confirmation as rollback (S7), not a fix to the switch-over, and is
  therefore a question for the user. (b) has no test yet;
  `anAppUpdateMakesItsBundledEngineActiveOnceAndKeepsTheOldOneAsTheWayBack` never sets a previous engine.

### 47. An activated engine is not re-checked against the runtime after an app update (low, unverified)

- **Location:** `EngineUpdateManager.active`; `verifyRuntimeCompatibility` only runs in `stage`, `activate`, and
  `ensureBundledLocked`.
- **Precondition:** a manually activated, downloaded engine and an app update that changes Python or the JavaScript
  runtime.
- **Unverified:** whether a yt-dlp version actually fails against a newer bundled runtime — yt-dlp supports multiple
  Python versions. If it fails, extraction errors would show it, and "Revert to previous engine" or an update helps.
  To close: have `active()` check the runtime once after an app version change, with a test. Raised in round 17.

### 48. `stage` checks references separately before download and during cleanup (low)

- **Location:** `EngineUpdateManager.stage`: `ensureRoomLocked(update.sha256, remove = false)` before download,
  `materializeSlot` with `ensureRoomLocked(installation.id, remove = true)` afterward. `JobCoordinator.retry` gives a
  "missing only" retry its predecessor's engine without asking the manager.
- **Precondition:** five occupied slots where exactly one installation is dispensable, and a job with a partial
  result whose last STT attempt was bound to exactly that one.
- **Flow:** the dry run finds room. During the throttled download (64 KiB/s), a "missing only" attempt for this job
  starts; it is unfinished and holds its predecessor's engine. Cleanup after download then finds no room, `stage`
  ends with `SLOTS_IN_USE`, the verified download is discarded, and nothing is removed.
- **Why it barely happens today:** update and retry both run through `MainViewModel.action`, whose lock allows one
  action at a time, and `stageAndActivate` holds it for the whole download. But the lock belongs to a view model. A
  second `MainActivity` instance — startup mode `standard`, accepting shared text — with its own view model is not
  excluded, and unverified on-device. Any other new YouTube-source attempt asks the manager for the active engine
  and waits for `stage` to release it, or falls back to STT holding a caption attempt's engine while it still has it.
- **Why it stays open:** the outcome is safe and has its own text; it costs the download. Holding references under
  the manager's lock would mean jobs wait on the manager for their own Room writes. Reported by the round-18 code
  reviewer; [ADR 0009](adr/0009-engine-slot-cleanup.md) names the case.

### 49. A preview does not pin its engine (low)

- **Location:** `JobCoordinator.inspect` passes `engines.file(engines.active())` to `extractor.resolve` without
  creating an attempt; `EngineReferences.inUse` only knows attempts.
- **What it would take:** while yt-dlp runs for the preview, its engine would need to become neither active nor
  previous and then get cleaned up, meaning two activations and a cleanup inside one call.
- **Why it barely happens today:** `inspect` and `prepareAgain` run under the same `MainViewModel.action` lock as
  `stageAndActivate` and `rollback`, and only those two activate, revert, or clean up for an update. Switch-over and
  cleanup after an app update happen on the manager's first call in the process, and `inspect` asks
  `engines.active()` before taking the file. A second activity instance with its own view model lifts the lock as in
  item 48; the two activations would then need to happen there during one `yt-dlp` call.
- **Why it is still recorded:** safety here rests on a view-model lock, not on the manager. A future caller of
  `stage`, `activate`, or `rollback` outside that lock — a background update, say — opens the window. Reported by
  the round-18 code reviewer.

### 50. Instrumentation tests enqueue work in the app's own WorkManager (low, harmless today)

- **Location:** `ensureWorkManager` in `AppPipelineTest`, `BatchCreationTest`, `EngineJobPinningTest`,
  `ParallelJobsTest`, `ProcessRecoveryTest`, and `ViewModelStateTest` under
  `app/src/androidTest/java/app/sourcescribe/`; `SourceScribeApplication` as `Configuration.Provider`.
- **What happens:** tests run in the app's own process. `WorkManager.getInstance` there returns the app's own
  instance with its `androidx.work.workdb` database, since the application itself supplies the configuration, so the
  `catch` fallback to `WorkManagerTestInitHelper` is never reached. Each test environment builds its own
  `JobCoordinator` with its own Room database but enqueues work under `attempt:<id>` and `exports:<id>` into this
  same WorkManager database. A copy taken September 14 at 01:47 held 235 completed entries, 176 successful and 59
  aborted; how many originate from tests is not counted.
- **Why harmless today:** the IDs are random UUIDs, and tests only cancel work tagged with their own job IDs during
  cleanup. A worker whose attempt does not exist in the app's database finds nothing and ends without effect via
  `Result.success()` (`AcquisitionWorker.doWork`, `SourceScribeDao.claim`). The input data carries only the attempt
  ID. If a test run dies before cleanup, such work could later run in the app's own process, also without effect;
  unobserved.
- **Why it stays open:** the fix is a dedicated WorkManager instance for tests, e.g. via `WorkManagerTestInitHelper`
  with its own test application, which changes how these classes run their workers. Found in round 18 while reading
  app data on `emulator-5556`; a helper agent verified the load-bearing points.

### 51. Migration tests create their databases between the app's own (low, harmless today)

- **Location:** `MigrationTest` under `app/src/androidTest/java/app/sourcescribe/data/`:
  `MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), SourceScribeDatabase::class.java)` and names
  `migration-<UUID>.db`.
- **What happens:** the helper creates test databases using the app's context, i.e. in its own `databases/`
  directory next to its real database, separated only by the random name. Since `39a1cdc`, an outer rule deletes
  each test's database plus `-journal`, `-shm`, `-wal`, and `.lck` afterward and checks nothing with its name
  remains.
- **Why harmless today:** the names never collide with the app's own, and only what the tests themselves create gets
  deleted. If the process dies mid-test, the files remain, as they did before `39a1cdc`.
- **Why it stays open:** a separate directory would need absolute paths as database names, or a context with its own
  database directory; whether `MigrationTestHelper` and the `.lck` lock file work with that is unchecked. Reported
  by the round-19 invariant reviewer; the same kind of issue as item 50.

### 52. `SettingsStore` resolves its DataStore in the constructor and keeps it for the process's lifetime (low)

- **Location:** `settingsDataStore` and the `stores` map in `app/src/main/java/app/sourcescribe/data/SettingsStore.kt`,
  since `8fe0dd5`, called from `SettingsStore`'s constructor.
- **What happens:** the constructor calls `preferencesDataStoreFile`, which asks `Context.getFilesDir()`, and
  `File.canonicalPath`, which resolves the path on the filesystem and may throw `IOException`. Hilt builds the
  `@Singleton` on first need, and `MainViewModel`, created on the main thread, is among the consumers. The replaced
  `preferencesDataStore` delegate only asked for the file when DataStore first read it. Each new path also creates
  an entry in `stores` that persists for the rest of the process.
- **Why low:** the app has one path and therefore one entry, as with the old delegate, and DataStore forbids two
  instances for the same file. Further entries only arise in instrumentation tests with their own contexts. Whether
  the constructor access is ever noticeably slow or throws is unmeasured.
- **Missing to close:** resolve the DataStore only on first read or write, without allowing two stores for the same
  file. Reported by the round-19 code reviewer; the claim that the old delegate queried the file in its constructor
  just the same does not hold.

### 53. After a failed second rename, stale metadata can remain in the slot (low, harmless today)

- **Location:** `replaceInSlot` in `extractor/src/main/java/app/sourcescribe/extractor/EngineUpdateManager.kt`, since
  `a1a5af1`.
- **What happens:** when repairing a damaged bundled-engine slot, `replaceInSlot` first renames the verified file
  into the slot, then `metadata.json`. If only the second rename fails, the call ends with `STORAGE`, leaving the
  verified file in the slot next to the metadata that was there before. The next call finds the slot valid, since
  `validSlot` only checks the file, and never rewrites the metadata.
- **Why harmless:** `metadata.json` is only written and moved, never read anywhere. In the normal case, the stale
  metadata describes an earlier setup of the same hash.
- **Missing to close:** whoever reads `metadata.json` in the future must account for metadata from an earlier setup,
  or reconcile it while checking the slot. The intended outcome is in
  [ADR 0011](adr/0011-damaged-bundled-engine-slot.md), pinned by
  `aSlotRepairWhoseMetadataCannotFollowFailsWithStorageAndLeavesTheEngineRepaired`. Reported by the round-20
  invariant reviewer.

### 54. `ChoiceAccessibilityTest` failed in CI while the app was still starting up — fixed September 14, 2026

Kept as a number so references stay valid. The language-choice control was visible and clickable but
`enabled=false` while `MainViewModel` was still busy starting up, causing four CI failures (September 10 run
34544393441; September 14 runs 34838928729, 34841018134, 34855355701); root cause identified via commit `20ca738`.
Fixed in `1fe2dad`: the test now waits up to 150 seconds for the startup progress bar to clear before proceeding.
How long startup locks the UI is tracked as item 57.

### 55. Bouncy Castle 1.86 has not run on any Android version before API 37 (low, unverified)

- **Location:** `bcpg` and `bcprov` in `gradle/libs.versions.toml`, used by `EngineVerifier` on every engine-update
  check. `minSdk` is 29.
- **Missing:** a run of the signature check on API 29 through 36. Only an API-37 system image exists locally, and CI
  also uses API 37. On 14 September 2026 the owner decided that tests on Android 17 are enough, so no older image
  is planned.
- **What suggests it holds:** per the release notes, 1.86's build checks every module's base classes with
  AnimalSniffer against API level 26. None of 1.86's three files reference any of `java.math.BigInteger`'s
  `…ValueExact` methods, which the same notes say made 1.85 fail on older Android versions (STATUS, round 21).
- **Missing to close:** the `extractor` module's suite with a signed update on an API-29 emulator.

### 56. If deleting the inspection view also fails after a check error, the first message is lost (low, unverified)

- **Location:** the `finally` block of `inspectArchive` in `core/src/main/kotlin/app/sourcescribe/core/EngineVerifier.kt`.
- **Precondition:** checking an archive fails after `inspectArchive` created its inspection view
  `.engine-inspect-*.zip` next to the archive, and deleting that view also fails.
- **Expected vs. actual:** the first error, e.g. an oversized entry, should be reported. The `finally` block throws
  its own `EngineVerificationException` on a failed delete, and an exception from `finally` replaces the one in
  flight — leaving "temporary inspection view could not be removed." The update is rejected either way; only the
  diagnosis is lost.
- **Why unverified:** a test would need to fail the delete exactly between creation and cleanup, and `EngineVerifier`
  has no seam for that. That the view disappears after a failure is covered since round 22 by
  `aFailedInspectionRemovesItsTemporaryView`. Reported by the round-22 code reviewer.

### 57. The app locks its UI on every start, and for how long is unmeasured (low)

- **Location:** the `init` block of `MainViewModel`, which runs `coordinator.recover()`, `refreshCredentials()`, and
  `refreshEngines()` as one exclusive action, and `EngineUpdateManager.ensureBundledLocked`, reached from
  `refreshEngines()` via `installations()`.
- **Precondition:** every start of the app process. `ensureBundledLocked` holds the verified engine only in memory,
  so after a process restart it re-copies the bundled engine into a working directory, checks hashes and signature,
  creates its slot only if missing or invalid, and checks it against the runtime.
- **Expected vs. actual:** expected is a UI that becomes usable shortly after start. While the action runs,
  `MainActivity` shows a thin progress bar at the top, and `state.busy` locks, among other things, checking a
  source, importing an audio file, starting a job, and, in settings, language, provider, and key selection. In CI
  this took more than 25 seconds on a freshly installed emulator (item 54); exactly how long, and which step causes
  it, is not logged. On `emulator-5556`, both `ChoiceAccessibilityTest` tests together passed in 24 and 29 seconds,
  each waiting out the end of startup; the lock's own duration is not measured separately.
- **Missing to close:** measure the lock's duration on a physical device, both on first start after install and on a
  later one, and show which step causes it. Then decide whether checking the engine must lock the UI or can run in
  the background without a job ever using an unverified engine.

## Deliberate decisions that look like bugs

### A normal tap of Start after "Prepare again" is enough authorization

A fourth-round review reported that a re-prepared job starts and incurs cost with a single tap of "Start," because
`configurationForStart` recomputes authorization from mode, model, and matching stored key and never reads the prior
value. The mechanism is correctly described; classifying it as a defect is not.

This is intentional, app-wide, not only on re-preparing: tapping "Start" **is** the deliberate authorization, bound
to exactly that configuration.
`ViewRulesTest.deliberateStartBindsApprovalToModeCredentialProviderAndRegionWithoutChangingTheDraft` has long pinned
this. It is exactly what the September 10 fix for a blocking user error relies on: a job stuck at its own length
limit should be startable again with one changed value, without re-choosing provider and key. The invariant requires
no extra hurdle, only that nothing is repeated **silently** — a tap is not silent. What was fair in the original
report: the test only checked the draft before this gate, and its comment read as if more than an ordinary tap were
needed. Both are corrected; the test now checks, with a genuinely registered key, what happens at the gate, and
additionally that nothing is authorized without a matching key.

### `MALFORMED_SEGMENT` and `MALFORMED_WORD` deliberately sit in different groups

Both arise on the same line of `parseEntries`, and both drop the entry. They still do not tell the reader the same
thing: the segment list is the displayed reading text, so a missing entry there is a missing passage. The word list
only carries per-word timing and appears in export as a word count, so a missing entry there is a missing timestamp.
That is why `MALFORMED_SEGMENT` and `MISSING_SEGMENT_TEXT` belong with missing text, and `MALFORMED_WORD` and
`MISSING_WORD_TEXT` belong with word timing — even though both pairs come from the same line of the same function.

### The title's 40-byte cap also carries the name-length limit

A round-7 reviewer reported as a high-severity finding that `TranscriptExporter.compose` computes the title's budget
as the byte limit minus the *character count* of the part that must survive. Language and source ID each reserve 40
bytes, so with three-byte characters the budget can run up to 52 bytes too large, the name overflows its limit, and
the final truncation cuts from the end — where the hash sits.

The unit mix-up was real and is fixed. It was never triggerable, and that part has to stay recorded here, since
otherwise it gets reported again as a high finding every round: `generatedStem` already runs the title through
`safePart` with its default of 40 bytes beforehand. The widest name the builder can assemble stays under the
180-byte limit — demonstrated by reintroducing the bug, under which its test passes while six others fail.

It follows that the title's 40-byte cap is load-bearing, not cosmetic. Increasing the title's share of the name — the
most obvious next change to this file — removes the limit's second backstop. The test
`noPartOfANameMeasuredInCharactersPushesTheIdentityOutOfIt` is therefore not a regression probe but a boundary probe
over 648 name combinations.

### Unreachable `PROVIDER_` and `RESPONSE_` branches in `messageText`

A review showed that most `PROVIDER_*` and `RESPONSE_*` branches are unreachable today, because `SttStep` handles
provider errors itself within each phase and stores the bare code. The branches stay regardless: they are the
safety net for a `ProviderError` that escapes phase handling, and the step name ("While submitting to the provider")
would be correct in exactly that case. The eight `AUDIO_*` branches modeled after `ExtractionFailure` were different
— no conceivable producer existed for them, and they were removed.

### Expanding shifts what is below it

The invariant reviewer noted in round 15 that expandable elements shift their neighbors on opening and closing: the
job card in history, help entries, the provenance section in the result view, and, in job settings above "Check
source," the hint `no_provider_help`, which disappears once a key is chosen. Until round 16 this list also named, as
a fourth site, that model and key selection in the preview only appear once a provider is chosen — a different
location, also in job settings rather than the preview, found by the round-16 consistency reviewer. Neither list is
complete: the advanced options expand the same way, and further conditionally shown blocks are not counted. This
stays as is, recorded here so it is not re-reported every round. The no-jump rule here is read as a rule against
motion nobody triggered — a line growing while someone is reading or operating something else. Here the change
happens where the user just tapped and shows what was asked for; reserving space for collapsed content would defeat
the point of collapsing. Anyone deciding otherwise counts every occurrence first and changes them together.

### After a process death, job fields show the draft, not the typed text

The round-16 code reviewer reported that text preserved via `rememberSaveable` vanished again immediately after
process death. This is intentional since the epochs were introduced: the draft lives only in the view model and dies
with the process; the new view model has a new session ID, and a field then shows what the draft holds, i.e. the
saved defaults. Showing the preserved text would mean displaying a value no job would actually start with, since
start reads the draft, not the field. Verified on-device in round 18 at `39a1cdc`: typed `0.5` into the budget field,
sent the app to background, killed its process (`am kill`, confirmed nothing left running), and started the app as
the launcher would. The field was then empty, not `0.5`. Preserving typed values across a process death means saving
the draft, e.g. via `SavedStateHandle`, not a field's text.

## Maintenance notes that are not defects

### A provider figure is read from markup, not from a summary

Round 12 removed a correct pricing condition because the pricing page had been read through a summarizing fetch
tool that hands a small model the page and returns its answer. AssemblyAI's add-on table has one column per model,
and its "Keyterms Prompting" row says **"Included"** under Universal-2. The summary turned that into
"+$0.05/hr for both models" — reproduced with the same tool on September 12, 2026 to rule out a page change.

Anyone changing a number that affects money should fetch the page directly and read its table:

```bash
curl -sL --max-time 60 -A "Mozilla/5.0" https://www.assemblyai.com/pricing -o page.html
```

Then print the `<table>` blocks with header and cells, rather than searching for the number alone — which column a
cell belongs to is the whole question. A word like "Included" sits exactly where a price otherwise sits and passes
as a price in any summary.

### Instrumentation tests do not run over Gradle from WSL

Gradle runs in WSL; the emulator runs under Windows. The Windows `adb` server only listens on `127.0.0.1`, which WSL
cannot reach (measured September 11, 2026: `ADB_SERVER_SOCKET=tcp:172.20.112.1:5037` ends in `Connection timed out`).
`connectedDebugAndroidTest` is therefore not runnable locally without restarting the Windows `adb` server, which
would also disconnect the user's own emulator. The working path without touching someone else's device:

```bash
bash tools/build-local.sh :app:assembleDebug :app:assembleDebugAndroidTest :extractor:assembleDebugAndroidTest
```

then, under Windows, install the APKs and start both instrumentation suites:

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb -s emulator-5556 install -r -t app/build/outputs/apk/debug/app-debug.apk
& $adb -s emulator-5556 install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
& $adb -s emulator-5556 install -r -t extractor/build/outputs/apk/androidTest/debug/extractor-debug-androidTest.apk
& $adb -s emulator-5556 shell am instrument -w -r app.sourcescribe.debug.test/androidx.test.runner.AndroidJUnitRunner
& $adb -s emulator-5556 shell am instrument -w -r -e sourcescribeEngineUpdate true app.sourcescribe.extractor.test/androidx.test.runner.AndroidJUnitRunner
```

**The second suite and its flag both matter.** The `extractor` module has 39 of its own instrumentation tests;
review rounds 6 through 10 only ran the `app` module's 191 and reported that count as the gate. Without
`-e sourcescribeEngineUpdate true`, the second suite skips fourteen `EngineUpdateManagerTest` tests by assumption and
still looks green with 21 passed; with the flag it is 35 passed, 4 skipped, 0 failed. The four need a real source or
a real release and stay `BLOCKED/NOT_RUN`. One of them,
`EngineUpdateManagerTest.realReleaseStageActivateAndRollbackSurvivesManagerRestart`, additionally needs
`-e sourcescribeEngineLiveUpdate true` and `-e engineProbeSource <URL>`; the procedure in [NEXT_STEPS.md](NEXT_STEPS.md)
names all three.

**A failed install looks like a code defect.** On September 13, 2026, `/data` on `emulator-5556` was 91% full, and
`adb install -r` failed for the app APK and the `extractor` test APK with `INSTALL_FAILED_INSUFFICIENT_STORAGE`,
while the app's small test APK installed. Instrumentation then ran new tests against a previous round's app and
reported two failures that looked like defects. After every install, check the output says `Success`; when in doubt,
read `adb shell dumpsys package app.sourcescribe.debug | grep lastUpdateTime`, and uninstall old debug and test
packages first when storage is tight. The same evening, installation failed a second time, this time with 523MB
free, and the check run aborted as designed before any test ran. `adb shell pm trim-caches 4G` plus uninstalling
`app.sourcescribe.extractor.test` freed `/data` from 609MB to 933MB; the retried `extractor` run that evening brought
the test package back, leaving 607MB free. The second failed install had happened at 523MB.

Since September 12, when the Play Store updated preinstalled apps on `emulator-5556`, there is no longer enough room
there to install the test packages over their old versions. The check scripts now uninstall both test packages,
`app.sourcescribe.extractor.test` and `app.sourcescribe.debug.test`, before every install, never the app itself
(which would erase its settings and history). In round 18 this raised free space from 565MB to 886MB, settling at
560MB after the three installs.

### The debug app on `emulator-5556` carries settings left by tests

Until round 18, instrumentation tests wrote to `app.sourcescribe.debug`'s settings, most recently on September 14,
2026 at 00:47. `files/datastore/settings.preferences_pb` has since held only: STT, Groq with `whisper-large-v3`, the
term `mutated-default`, two parallel jobs, system theme, a 2GiB storage limit, and no preset. Nothing has been reset,
since nobody knows its prior contents, and uninstalling the app would also delete its history. Anyone testing
something on this device that depends on settings should read them first. Whether a run changes them shows in the
SHA-256 before and after:

```bash
adb -s emulator-5556 exec-out run-as app.sourcescribe.debug cat files/datastore/settings.preferences_pb | sha256sum
```

### Regenerate dependency verification after every version change

`gradle/verification-metadata.xml` holds SHA-256 checksums for every resolved artifact. A version change in the
catalogue therefore first fails with `dependency verification failed`. The procedure:

```bash
bash tools/build-local.sh --write-verification-metadata sha256 :core:test :app:compileDebugKotlin :app:lintDebug
```

Then check the file's diff: only entries for the new versions should be added. If existing entries disappear, the
run resolved too few configurations; repeat with more tasks rather than accepting the loss.

### Lint reports new library versions as an error

`lint { warningsAsErrors = true }` turns `GradleDependency` into an error. As soon as Google publishes a new Compose
BOM or Room version, the lint run fails with nothing changed in the code. This is intentional: the rule forces
updates to actually happen. The procedure is the section above.

Locally the finding can be absent while CI fails on it: `NewerVersionAvailable` reads the latest versions from each
module's `maven-metadata.xml` in the lint cache under `build/intermediates/lint-cache`. Local copies there were dated
September 7–8 when Bouncy Castle 1.86 appeared on September 11, so only CI failed until `d994c23` caught the version
up. Delete these directories before a local lint run meant to match CI.
