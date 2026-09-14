# Working rules for SourceScribe

## Orientation and responsibility

This is a personal Android project, not a SaaS product. `docs/PRODUCT.md` defines the scope; `docs/ROADMAP.md` sets the order of work. Architecture, integration contracts, and security live in the documents of the same name — read the relevant one before changing its area. Documentation, code identifiers, comments, commit messages, and release notes are in English; agents talk to the owner in the owner's language.

`docs/INDEX.md` is the shared entry point to project knowledge. Do not copy requirements into a second wiki; keep decisions, sources, and real test results in the linked documents. `CLAUDE.md` remains nothing but the bridge `@AGENTS.md`.

Preserve existing user files and uncommitted changes. Commit and push verified work to `main`; no release, billing change, lowering of the security level, or deletion of other people's data without the owner's approval. A genuine blocker warrants a clear interim status, not a fabricated claim of completion.

## Non-negotiable invariants

- Process exactly the specified source. No substitute videos, search results, or invented transcripts.
- The chosen acquisition mode and cost budget hold even when something fails. No silent provider switches, uploads, or paid retries.
- An uncertain outcome for a cost-relevant request is not the same as "did not happen."
- Persist internally first, then export. An export failure must not trigger another STT request.
- Disclose provenance, language, the actual model used, and known uncertainty. Never invent timestamps, speaker identities, progress percentages, or accuracy figures.
- A partial result must never appear as a full success. Successful sibling artifacts must be preserved.
- Never write API keys, temporary audio sources, or full transcripts to logs, Git, WorkManager input data, or diagnostic exports.
- No unreviewed update code, no shell interpolation of untrusted input, no freely configurable yt-dlp plugins or `--exec` hooks.
- A separate process under the same Android UID is not a security boundary against the app's own data.

## Implementation style

Kotlin, Compose, Coroutines/Flow; keep the UI free of networking or file-processing logic. Keep domain logic JVM-testable wherever possible. Make small changes with clear contracts, typed errors, cancellation support, and traceable state. No framework abstractions without a concrete benefit. Pin library and tool versions in the version catalogue and the build; no dynamic `+` versions.

Document a short architecture decision before changing the data model, job semantics, update trust, or network boundaries. The technical statements in `docs/RESEARCH.md` are dated research, not permanent version mandates. Check integration-critical details against current primary sources and the code that actually runs.

## Subagents and review

Delegate sensibly wherever the environment supports real subagents. Good independent units are provider adapters with their contract tests, the caption parser/exporter, the UI, and targeted security or lifecycle reviews. Shared interfaces, database migrations, and the scheduler each need one responsible integrator. Define scope, allowed files, expected tests, and result format per task; parallel code changes only in disjoint files and never with overlapping builds — worktrees or branches only with the owner's consent.

A reviewer should not simply approve their own code. Findings need a file/location, preconditions, a reproducible sequence, expected versus actual behavior, and a severity. The lead agent checks the evidence. Where delegation is unavailable, run separate review roles sequentially; never claim a fictitious agent.

## Evidence and completion

Record real commands and their results. Tests must never be weakened, deleted, or skipped just to turn green. Reproduce network or provider failures with controlled fixtures; document live tests separately. Report missing credentials or devices as `BLOCKED`/`NOT_RUN`, never as `PASS`.

Add a matching regression test after every review finding. Before release, rerun all affected tests and run one final, independent review pass. P1 and P2 findings are fixed; only a P2 whose fix needs a design decision or costs far more than the defect is worth is recorded and put to the owner instead. P3 and P4 findings that take minutes are fixed on the spot; everything else goes into `docs/BUGS.md` with a priority and is reported to the owner. Only open P1 or P2 findings, or unmet acceptance criteria, block a release.

## Repository hygiene

Version source code, tests, small approved fixtures, lock/version files, Room schemas, and documentation. Ignore keys, local SDK paths, signing files, private test data, audio downloads, and runtime logs. Run CI without real provider keys. Test helpers, cleartext HTTP for local fixtures, and demo providers must not be reachable in the personal release build. No persistent test backdoors.

Keep `docs/STATUS.md` up to date; it describes the current state only. `docs/HISTORY.md` is the compact work log: one dated entry per release, review round, or notable mistake, one to three lines each, naming what went wrong and the `docs/LEARNINGS.md` entry it produced, so that a later agent does not repeat it. Keep it under 12 KB by condensing the oldest period into a short summary; the full text stays in Git. The final report distinguishes implemented, fixture-tested, live-verified, and blocked. What is actually missing is listed in `docs/STATUS.md` and `docs/BUGS.md` — there and nowhere else.
