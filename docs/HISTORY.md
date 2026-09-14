# History

Compact chronological work log, kept so that a later agent does not repeat a mistake. For the full
round-by-round text it condenses (in German), run `git show 1118adc:docs/STATUS.md`. Reusable technical lessons
live in [LEARNINGS.md](LEARNINGS.md) and are referenced below by title, not repeated. Open items are in
[BUGS.md](BUGS.md) and [DEFECTS.md](DEFECTS.md). Entries stay one to three lines; when the file nears 16 KB, the
oldest period is condensed into a short summary.

## 2026-09-07 - Project start

Repository initialized (`758186b`). First integration pass: Android build and source-acquisition contracts,
three provider adapters (AssemblyAI, OpenAI, Groq) with provenance preservation, signed-engine verification,
and the WSL memory/build-worker limits needed to build at all (`b3c93d2` onward). Component licenses and
public-binary release limits recorded (`e97513f`).

## 2026-09-08 - Preview 0.1.0-preview.1

Durable transcription workflow, process recovery, credential storage, SAF export, app languages and
accessible layouts, bounded CI (`a801be3` onward). Built, gated, and signed as `v0.1.0-preview.1` at commit
`f1a791c`. Evidence: [2026-09-08 preview report](reports/2026-09-08-preview.md); earlier failed attempts:
[2026-09-07 integration report](reports/2026-09-07-integration.md). Internal round labels from this period
(such as `r81`) are build/test checkpoints, not the 23 adversarial-review rounds below.

## 2026-09-10 - Owner's device test

The owner tested the preview on-device and reported 20 items. 17 were implemented and verified on-device or
by test; the 3 that stayed open are tracked in [BUGS.md](BUGS.md) and [DEFECTS.md](DEFECTS.md). This triggered 23 rounds of
independent adversarial review with Sonnet-5 agents, each round reviewing only the previous round's fixes.

## 2026-09-11 to 2026-09-14 - 23 rounds of adversarial review

Baseline `782aef5` (2026-09-10). Rounds 1-10 used up to 5 reviewers per round; round 11 introduced the
discipline that held for the rest of the loop - read-only reviewers run together, a mutating reviewer (for
counter-tests) runs alone afterward, never beside a reader. From round 16 on, reviewer identity and model
were read from `meta.json` rather than assumed, and roles split into code / invariants / consistency /
(from round 21) release reviewer.

- **R1** (`5a6bfef`, 09-11): 5 reviewers over baseline `782aef5`. Fixed a hidden-subtitle-toggle bug, an
  audio-description track auto-selected as if it were dialogue, a 0-byte size read as confirmed, and 41
  missing error texts. LEARNINGS: "A dexing error with a lowercased project path is stale Gradle state" and
  "Don't edit the repository during a running build."
- **R2** (`42f723e`, 09-11): fixed an instrumentation test waiting on a renamed status code, missing error
  texts for local audio prep, a mismatched message for a corrupted partial result, and 3 glossary quotes
  that no longer matched on-screen labels.
- **R3** (`7da4cdf`, 09-11): top finding sat in round 2's own fix - the new hit-count line had no reserved
  height and could wrap on the first keystroke, the exact layout shift the task was meant to remove. Also:
  the warning list grew with response size, not real problem count.
- **R4** (`477dc5b`, `029a53f`, 09-11): the same defect class recurred twice at adjacent spots: round 3's
  provenance-line fix closed only half the gap, and a "no results" label kept reading stale input for a
  moment during a search clear. Warning codes replaced by 12 plain sentences.
- **R5** (`69176ab`, 09-11): third round in a row whose top finding sat in the previous round's own fix -
  `MALFORMED_SEGMENT`'s sentence understated the damage. Also a family-name regex bug: leftmost-match ate
  the last word of any family name that itself ended in that word.
- **R6** (`f0f6884`, 09-11): fourth in a row - the new warning-kind cap counted entries, so one badly broken
  section could crowd out every later warning kind, and an existing test had encoded that bug as expected
  behavior. `family()`/`looksLikeCode()` rewritten without regex.
- **R7** (`968fb21`, 09-11): the kind-cap from R6 bounded call sites, not the number of kinds; the
  family-to-group mapping moved into data (`GROUPED_FAMILIES`) with a hard ceiling.
- **R8** (`35a277c`, 09-11): top finding was in a round-7 *test*: its rename-typo guard built expected and
  actual from the same map, so it could never fail. Fixed by sourcing expected values from the four real
  emitter files.
- **R9** (`28273f6`, 09-11): round 8's 100-char cap broke a real invariant - two different language tags
  truncating to the same 100 characters made automatic track selection stop refusing to choose between them.
  Truncation replaced by reject-whole for identifier-like fields.
- **R10** (`c795ab0`, 09-11): round 9's fix moved the bug rather than closing it - a rejected value and a
  never-stated one both read as `null`. Added `AudioTrack.languageRefused`; found the same confusion 3 more
  times in the same file.
- **R11** (`4dd406f`, 09-11): switched to the read-then-mutate reviewer discipline. Found the `extractor`
  module's 39 instrumentation tests had not run in any of the first 10 rounds, and that the flag-less run
  silently skips 14 of them while still printing `OK`. Centralized every priced/limited number into
  `StatedNumbersTest`.
- **R12** (`bd4d2f5`, 09-11): its own headline finding (a keyterms-surcharge condition) was itself wrong and
  reverted next round. Real finding, outside the assigned diff: the cost shown before starting a job was
  computed a third, independent way from the one that actually bills.
- **R13** (`2bd73f8`, 09-12): round 12's "fix" was the bug - AssemblyAI includes the keyterms surcharge in
  the cheaper model's price and not the pricier one, the reverse of what round 12 wrote, reached by reading
  a *summarized* fetch of the pricing page instead of its markup. Produced the sourcing rule: read
  cost-relevant provider pages from their markup, never from a summary.
- **R14** (`65bea1a`, 09-12): two of three headline fixes were themselves wrong - the UTF-16-vs-32 BOM fix
  from R13 made UTF-32 files silently decode, and count, as read UTF-16 text, and a comment declared an
  OpenAI model had "no published price" while its per-token price sat in the very page dump being read.
- **R15** (`9e9f5b5`, 09-13): round 14's own count of how many bypass forms its number-scanner had was wrong
  in three different places at once. Settled only by running old and new scanner over a fixed case set; the
  pattern-matching scanner was replaced with a small lexer (`codeOnly`).
- **R16-18** (`51329fb`, 09-13/14, grouped in one doc commit): R16 found R15's list-field fix missed the
  budget field, whose separate digit-shuffling logic reproduced the exact bug R15 had just rejected. R17
  found a real deadlock predicted by a security doc since the first commit and never implemented: a
  bundled-engine app update could permanently wedge the app once all 5 engine slots were full. R18 found an
  ADR's own justification was a false claim about code it had never checked, and a settings-storage test
  double that didn't recreate a real `Context`'s directory-creation contract, breaking 7 tests in a
  neighboring class until the gate caught it. LEARNINGS: low-memory lint crashes, lint-before-commit, a K2
  smart-cast trap, no instrumentation beside a Gradle build, `preferencesDataStore` binding once per
  process, fake contexts needing real contracts, `Context.deleteDatabase` leaving `.lck` files, and a check
  script's exit code carrying its verdict.
- **R19** (`b204a47`, 09-14): a reviewer's finding described JUnit 4.12's exception-swallowing; the project
  runs 4.13.2, where it doesn't reproduce. Also found 8 of 13 line-number references in DEFECTS.md pointed
  at different code after later edits. LEARNINGS: "A reviewer's finding about a library's behavior names a
  version," the rename-not-remove file-replacement rule, and the stale-reference rule that also produced
  `tools/check-repository.py`'s ban on citing source line numbers in docs.
- **R20** (`eb4eb1d`, 09-14): the script that built round 19's commits had written every file as mode 100644,
  silently dropping `check-repository.py`'s executable bit. Also found the new line-number ban didn't yet
  recognize link anchors or abbreviations pointing at a line. LEARNINGS: commit-building tools must carry
  the file mode; a file cannot be renamed over a directory.
- **R21** (`7d9ce41`, 09-14): a commit message's claim that "55 classes call an API absent on old Android"
  was confirmed by a reviewer re-running the identical `grep`; reading actual bytecode constant-pool
  references found zero real callers. Also found the Bouncy Castle 1.86 bump had left both license-notice
  copies at 1.85. LEARNINGS: `grep -l` counts matching files, not calls; lint compares versions against a
  cache; evidence must name the code that ran, not just `HEAD`.
- **R22** (`12e1040`, 09-14): a verifier's "exactly one signature" check only held for what the ASCII-armor
  parser handed back - Bouncy Castle silently drops anything after the armor footer, so a second signature
  block behind the real one was invisible rather than rejected. Also confirmed Compose masks a password
  field from accessibility in the bundled version (a reviewer's source-only claim was wrong), and hit a
  genuinely flaky CI test. LEARNINGS: the armor-footer behavior, password-field masking, a green CI run not
  ruling out flakiness, and `wsl.exe`'s stderr overwriting stdout when both are redirected to one file from
  Git Bash.
- **R23** (`0d7ac78`, 09-14): even round 22's footer fix only worked with a trailing line break; text glued
  directly to the footer with none was still swallowed by the library's own parser. Also fixed the CI-only
  `ChoiceAccessibilityTest` flake: the app was still starting, not the test being flaky. LEARNINGS extended
  with the no-line-break case, plus a new entry, "A test that checks an interaction first waits for the app
  to allow it." The loop was stopped after this round, following the owner's criticism that more than
  20 hours had gone into review without a new build to test; the owner had not asked for a stop, although the
  old STATUS.md says so. A started round 24 was abandoned without a report.

## 2026-09-14 - Preview 0.2.0-preview.1 and the review-rule change

Round 23's own doc commit (`0d7ac78`) created [BUGS.md](BUGS.md), which sorts every open item on a P1-P4
priority scale, and ended the "loop until a round finds nothing" approach: the loop had run 23 rounds and over
20 hours, mostly on P3/P4 findings, before the owner had a build to test again. The rules that replaced it, as
revised the same evening: P1 and P2 findings are fixed, P3/P4 findings that take minutes are fixed on the spot,
everything else is recorded in BUGS.md, and rounds stop when no P1 or P2 is left to fix.

Version raised to 0.2.0 (`152d7a6`) and Bouncy Castle updated to 1.86 (`d994c23`); round 21 then found both
license-notice copies still naming 1.85. Built and gated at `1fe2dad`, the last code commit of round 23; tagged
`v0.2.0-preview.1` on the following commit `1118adc`. Release signing was then fixed (`6559633`) to read the
key's password from a file instead of exposing it, and the resulting APK was signed and attached to the
GitHub release. Full evidence: [2026-09-14 preview report](reports/2026-09-14-preview-0.2.md).

Documentation was then translated from German to English and condensed: third-party notices (`56d7541`),
STATUS/NEXT_STEPS/HANDOFF/LEARNINGS condensed (`573ff50` - the old 174 KB STATUS.md whose round-by-round
detail this file condenses stays readable via `git show 1118adc:docs/STATUS.md`), README/AGENTS/user guides (`082da99`),
technical documents and ADRs (`a1b0598`), and finally the defect list, known bugs, and reports (`8a9e7bc`).

Work towards 0.3.0 started that night with the start-up lock fix (`e66761a`); the plan is in
[NEXT_STEPS.md](NEXT_STEPS.md).
