# Known bugs by priority

**As of:** September 14, 2026, preview 0.2.0-preview.1 at commit `1fe2dad`, after 23 rounds of adversarial review.
This file lists every known, unfixed item with its impact and a priority. Code location, trigger, and evidence are in
[DEFECTS.md](DEFECTS.md) under the same number. Items 1 to 3 are the open feedback from the September 10 test.
Closed, and kept only as a number there, are 6, 7, 8, 9, 19, 31, and 54. Fixes towards 0.3.0 are in progress; see
[NEXT_STEPS.md](NEXT_STEPS.md).

## Scale

There is no widely accepted scale for functional bugs the way CVSS exists for security vulnerabilities. The usual
approach separates two questions: how severe a bug is, and how urgently it should be fixed. DEFECTS states severity
from the review's viewpoint: critical, high, medium, or low. Priority here asks what the user notices and how often,
so it can differ from severity. Item 57, for instance, is low in DEFECTS but P2 here, because it affects every app
start.

| Level | Meaning | Handling |
|---|---|---|
| P1 | The app or a core function is unusable: fetching subtitles, transcribing audio, saving or exporting a result. Also data loss, unapproved cost, or an exploitable security hole. | Fix immediately. |
| P2 | A normal flow gives a wrong or misleading result, or is clearly disruptive; a workaround exists. | Fix it; only when the fix needs a design decision or costs far more than the defect is worth, record the effort and let the owner decide. |
| P3 | Minor annoyance: imprecise text, display, diagnostics, cleanup, or a bug that only occurs under rare conditions in normal use. | Fix on the spot if it takes minutes; otherwise record it here and report it. |
| P4 | Not noticeable: tests, check scripts, code that works correctly today but could easily become wrong, or a case only a contrived flow reaches. | Same as P3. |

**Unverified** means: derived from the code but not reproduced or measured. The priority given is for the likely
case; where a worse case would rate higher, that is noted. For the security-adjacent items 37, 40, and 42, no
exploitable hole is demonstrated, so none of them carries a CVSS score.

50 items are open: 2 P2, 19 P3, 28 P4, and item 55, which stays untested by the owner's decision (see "Not verified").

## P1

None known. What could be hiding a P1 is in the next section.

## Not verified

These gaps are not known bugs. If any of them fails, it is a P1.

- **Real transcription with AssemblyAI, OpenAI, and Groq.** Never run in this project with a real key, only against
  simulated provider responses.
- **An ARM64 device, like almost every current phone.** All device tests ran on x86_64 emulators with Android 17
  (API 37). The ARM64 build of the app never ran in any of these tests.
- **Updating an installed preview 0.1.0.** Preview 0.2.0 migrates the database from schema 3 to schema 4. This is
  only verified against test databases in `MigrationTest`.

Full operation with TalkBack is also not verified.

**Untested by decision: item 55, Android before version 17.** The app installs from Android 10 (API 29) onward and
checks the signature of its bundled engine with Bouncy Castle 1.86, but every device test runs on Android 17 (API 37).
If that check failed on an older version, the app could not process YouTube links there; Bouncy Castle's release
notes suggest it holds up. On 14 September 2026 the owner decided that tests on Android 17 are enough.

## P2

| Item | What happens | Note |
|---|---|---|
| 57 | After every start, checking a source, importing an audio file, and starting a job are locked, along with language, provider, and key selection in settings. A thin progress bar runs at the top the whole time. During this the app checks its jobs, credentials, and bundled engine. | Unverified how long. In CI it took more than 25 seconds on a freshly installed emulator. If it is over in a moment on the device, this is P4. |
| 1 | In the September 10 test, the result view did not scroll at the right edge and only scrolled halfway. It has since been rebuilt. On the emulator, with a 286-section transcript, it scrolls at the right edge too, all the way to the last section. | Unverified whether it still occurs on the user's device. |

## P3

| Item | What happens | Note |
|---|---|---|
| 2 | The bottom button "could use more space." Today all primary buttons are at least 52dp tall and full width. | Question below. |
| 3 | The glossary says an AssemblyAI key is valid in only one region, EU or US, and that a call in the wrong region ends with an authentication error. This is not checked against the provider's current documentation. | Text. |
| 4 | Dozens of error codes have no dedicated text; on September 11 there were at least 43. The app then shows "Operation could not be completed" with the technical code, even for ordinary outcomes like `REMOTE_TIMEOUT` or `NO_TRANSCRIPT`. | Only on error; medium in DEFECTS. |
| 5 | The preview and the job measure length differently: the preview from yt-dlp's reported value, the job from the downloaded audio track. If the length is within milliseconds of the job's limit, it can pass the preview and then abort after download at no cost, or the preview can wrongly block it. | Costs at most one download. |
| 11 | "Retry only missing sections" is permanently refused for a partial result if a newer app version reads different warnings from its stored response, for instance for an empty model field. | The failure itself costs nothing, but a new job also pays for the sections already paid for. |
| 12 | The size estimate is missing for some audio tracks, namely when yt-dlp reports an implausible audio bitrate alongside a usable overall bitrate. | Display. |
| 13 | If an exported document cannot be queried at all, the app reports the file as gone, even though only the check failed. | Misleading message. |
| 14 | Transcripts from captions created before September 11 report unreadable sections as missing timestamps instead of missing text. | Older results only. |
| 17 | The German strings say "Tonspur" in some places and "Audiospur" in others (both mean "audio track"). | Word choice, see below. |
| 18 | If every section of a provider response is unreadable, the app falls back to the full text, but warns as if text were missing, even though only the structure and timestamps are missing. | Misleading warning. |
| 24 | Next to the cost estimate is only the pricing date, not the provider page it was checked against. | Verifiability. |
| 25 | The app allows 25MB per file for Groq, even when a paid Groq key allows 100MB. | Only with a paid Groq key; deliberately on the safe side. |
| 35 | At very large font sizes, text on the new-source screen and in history could be clipped. Only the cost line is measured, and it displays in full even at double font size. | Unverified. |
| 36 | The preview's error line appears and disappears, and the start button below it shifts by its height. | Decision below. |
| 37 | Rolling back to an older engine warns but blocks nothing, even for a version with a known vulnerability; the app does not know which versions have vulnerabilities. | Would need a field in the signed engine package. |
| 41 | The app rejects links of the form `youtube-nocookie.com/embed/…`. | Workaround: the same address with `youtube.com`. |
| 42 | Shared exports, including whole transcripts, remain in the app's private cache until Android clears it. | No new exposure path. |
| 46 | Two edge cases when an app update brings a new engine after engines were switched by hand. In the second, a previously used engine is no longer reachable via "Revert to previous engine." | Decision below. |
| 47 | A manually activated engine is not re-checked against the bundled runtime after an app update. | Unverified whether it then fails. Workaround: "Revert to previous engine." |

## P4

| Item | What it is |
|---|---|
| 10 | One error text also covers an internal programming error; no trigger in normal use is known. |
| 15 | Free text made of capital letters in the warning list would display like a code; today nothing places such text there. |
| 16 | A future misnamed `RESPONSE_…` warning would be reported as a lost section; today the rule holds for every one. |
| 20 | The length limit for reported model names exists independently in two parsers. |
| 21 | With a device clock set grossly wrong, the day and month are missing from the file name. |
| 22 | Two audio tracks with an overlong language tag both appear as "Unknown," with no indication; real language tags are much shorter. |
| 23 | The 600-minute maximum duration exists as an independent number in the code and in two text strings. |
| 26 | Whether Groq's and OpenAI's "25MB" is meant decimally or in binary is open; the app takes the smaller number. |
| 27 | A cost pre-check computes without surcharges; today there are none there, and the binding check does include them. |
| 28 | The repository's check script misses some cases: UTF-16 or UTF-32 without a byte-order mark, text past the first mebibyte, unversioned workflow files, and some ways of writing versions in license notices. None apply today. |
| 29 | OpenAI's pricing page does not name `whisper-1` verbatim; the price is correct. |
| 30 | A source under 160ms would show a tiny price for AssemblyAI and would then be rejected. |
| 32 | A green CI run does not show how many device tests were skipped. |
| 33 | The backoff rule on the acquisition job is never triggered, because no worker requests a retry. |
| 34 | A test that checks numbers in the code does not understand nested strings inside templates; today none of them contain anything that would trip it up. |
| 38 | That every change to the job draft goes through `withDraft` is a convention in the code, not an enforced barrier. |
| 39 | The elapsed-time display in history reserves space for up to 999 hours; beyond that, the card can grow one line taller, once. |
| 40 | A short window exists between checking and starting an engine that only a process with the app's own privileges could exploit. |
| 43 | If a new engine fails its self-test after an unused installation was cleared to make room for it, both are gone. |
| 44 | A message about occupied engine slots names an update step even though nobody is updating; this requires unfinished jobs bound to at least three other engines. |
| 45 | One write path for the completeness flag has no dedicated test. |
| 48 | An engine update can fail on occupied slots if a retry attempt arises during it; a lock today makes this rare. |
| 49 | A preview does not pin its engine; the same lock makes this case rare today. |
| 50 | Device tests enqueue work in the app's own WorkManager, without effect. |
| 51 | Migration tests create their databases next to the app's own and delete them afterward. |
| 52 | Settings resolve their file already at construction time, on the main thread; whether this is ever noticeably slow or fails is not measured. |
| 53 | After a failed repair, a metadata file in the engine slot can be stale; nothing ever reads it. |
| 56 | If deleting the inspection view also fails while checking an engine package, the first error message is lost; the package is rejected either way. |

## User decisions

These items are design questions; nothing about them changes without asking first.

- **Item 2, "could use more space":** Did this mean height, the gap to the navigation bar, or thumb reachability?
  Today all primary buttons are at least 52dp tall and full width.
- **Item 17, word choice:** "Tonspur" or "Audiospur" for all German text?
- **Item 36, preview error line:** As long as provider, model, or key are missing, an error line sits below the cost
  line; once everything is chosen, it disappears and the start button moves up. Either this stays as is, or the
  space always stays reserved, holding a short sentence like "This source is ready to start" in the valid state,
  with the length warning occupying the same height.
- **"Expanding shifts what is below it,"** among the deliberate decisions in DEFECTS: expandable elements shift
  what sits below them when opening and closing — for instance the job card in history, help entries, the
  provenance section in the result view, and the advanced options. Likewise, in job settings above "Check source,"
  the hint "YouTube captions need no provider. …" disappears once a key is chosen. The rule so far: movement is
  acceptable where the user just tapped, and reserving space for collapsed content would defeat the point of
  collapsing. If this should change, every occurrence gets counted first and then changed together.
- **Item 46(b), engines:** Someone who activates a downloaded engine and then reverts to the bundled one can no
  longer reach the downloaded one via "Revert to previous engine" after an app update brings a new engine. Either
  this stays as is, with the way back reaching one step, or a new feature is added: activating any installed
  engine directly, with the same confirmation as reverting.
