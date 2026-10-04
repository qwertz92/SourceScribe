# History

Compact chronological work log, kept so that a later agent does not repeat a mistake. For the full
round-by-round text it condenses (in German), run `git show 1118adc:docs/STATUS.md`. Reusable technical lessons
live in [LEARNINGS.md](LEARNINGS.md) and are referenced below by title, not repeated. Open items are in
[BUGS.md](BUGS.md), the only list of them. Entries stay one to three lines; when the file nears 16 KB, the
oldest period is condensed into a short summary.

## 2026-09-07 to 2026-09-14 - Initial preview and review lessons

Repository initialized at `758186b`; the signed first preview was `f1a791c`. The owner's device feedback
triggered 23 adversarial rounds from `782aef5` to `1fe2dad`: fixes repeatedly introduced new defects, tests
sometimes encoded them, and the extractor suite had initially been omitted. The lasting rules are in
[LEARNINGS.md](LEARNINGS.md): verify every module and opt-in, use independent readers before mutation,
validate negative controls, and check primary-source conditions. Detailed round results remain in Git's
prior HISTORY and `git show 1118adc:docs/STATUS.md`; preview evidence is in the
[initial preview report](reports/2026-09-08-preview.md). The 23-round loop and its replacement rule follow below.

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

Work towards 0.3.0 started that night with the start-up lock fix (`e66761a`).

## 2026-09-15 to 2026-09-16 - 0.3.0 dropped; the owner's second phone test and the 0.4.0 work

Version raised to 0.3.0 (`5ebc11e`); JVM tests were green there, but the release-APK build attempt right after
ended when the Gradle build daemon crashed mid-packaging, before a real 0.3.0 APK - signed or unsigned - ever
existed. Before a retry, the owner's second device test (of the published 0.2.0-preview.1) found every job over
ten minutes stopping with `PREPARED_AUDIO_INVALID` (P1) plus 15 further points, so 0.3.0 was dropped: every fix
planned for it, plus the phone-test fixes, ships as 0.4.0 instead.

**P1 cause:** the bundled ffmpeg's chunk encoder always produces audio 84-96 ms longer than the requested
window (a whole MP3-frame count plus its own encoder delay); the planner made every window but the last exactly
the 600,000 ms hard bound, so the encoder's own surplus pushed every chunk past it - every source over ten
minutes failed on its first chunk, regardless of provider or codec. Fixed by storing the planned window, not the
encoded length, as the chunk's duration (`f18937f`). LEARNINGS: the encoder-surplus tolerance rule.

Three work packages ran one after another on the one checkout and emulator: **A**, transcription pipeline
(`f18937f`, `618e9f3`, `494bdad`, `580be9f`, `57d9641`, `4c90ad2`, then `cd124d4`/`bf86675` for the live test);
**B**, new-source screen (`e3f4a87`, `3709f2e`, `b184399`, `bcf8ad1`); **C**, history actions and the engine
re-check (`c77c933`, `5332af8`, `a157cd0`, `b0b33b5`). This project's first real provider request: one Groq call,
16 September, through `LiveGroqTranscriptionTest` against a 19-second public video, complete and stored - which
also showed Groq reports no `model` field and names its language as a full word rather than a code; both fixed
the same day. Independent review of the 0.4.0 work: round 1 (Sonnet, read-only, over the sixteen 0.4.0 commits `f18937f` to `f22389c`): 0 P1, 2 P2, 3 P3, 1 P4; all five fixed in `46d92e3`, `d0c55fb`, `c832a9c`, `0d1953d` and `75af4f3`, each with its test seen failing first, plus BUGS item 60 closed in `96b9588` and item 63 by the new `JobCardLayoutTest`; the round's other P3, progress that goes back after a retried upload, is recorded as deliberate in LEARNINGS, round 2 (Sonnet, read-only, over the seven fix commits `46d92e3` to `f7dd7d2`): 0 P1, 1 reported P2 that verification re-rated P3 because it needs two engine switches during one job's life (BUGS item 72), 2 further P3 (items 73 and 74, recorded), 1 overstated KDoc corrected in `7868497`; no code changed, so no third round. Version raised to 0.4.0
(`f22389c`).

## 2026-09-30 to 2026-10-01 - Candidate reliability and provider verification

Provider checks exposed missing default AssemblyAI sentence timestamps and a paid-resubmission path; the fix now stores acceptance before sentence retrieval (LEARNINGS: “Persist paid acceptance before performing another HTTP request”). Native two-line layout closed BUGS74; the first candidate slice was signed and pushed as `c93d5e8`.
Implementation `297e553` is signed/pushed. Three Groq jobs and two AssemblyAI paid POSTs were tested. Final build, all opt-ins, negative controls, whole-card ripple, signing and 0.4.0-to-0.4.1 upgrade passed; scoped Luna reviews were empty. Physical/full-accessibility limits remain in [the report](reports/2026-09-30-provider-reliability.md).
CI initially missed the notification runtime grant (four real delivery-test failures); `2cdd6ee` applies the local setup; GitHub run `36846190794` passed. LEARNINGS: “Grant runtime permissions in every device gate that tests delivery.”

## 2026-10-03 - Publish 0.4.1 and remove obsolete APKs

The tested APK had remained local, leaving the owner installing 0.4.0. Published signed tag `v0.4.1` at `9de3509` and the APK as latest; verified its public download. Removed 15 obsolete APKs (2,087,611,391 bytes). LEARNINGS: “An Android release must be downloadable.”

## 2026-10-04 — publish workflow update 0.4.2

Published v0.4.2 (`df32dede854355ac38f933c8cdb0a266c3ba0657`; APK `7b1fac241672c7bb16739d440069ea97edeffc6c6e03857e3c250c69d2690c26`, `147200333` bytes; `2026-10-04T05:57:01Z`); public download matched.
Gates: core 234, lint 4 clean, app 291 plus 10 skips (all opt-ins separately exercised), extractor 55; upgrade and folder opening passed.
Two live transcripts succeeded; six Luna review stages found no P1/P2. Device, TalkBack, OpenAI and other limits: [report](reports/2026-10-03-workflow-polish.md).
