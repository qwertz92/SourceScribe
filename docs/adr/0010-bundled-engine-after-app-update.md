# ADR 0010 — After an App Update, the Newly Bundled Engine Becomes Active

Date: September 13, 2026. Status: implemented in round 17, edge cases corrected in round 18, one
justification corrected in round 19. Which checks have actually run is recorded in [STATUS.md](../STATUS.md).

## Context

`EngineUpdateManager.ensureBundledLocked` set the active installation only when none was set yet. Anyone
who had never activated an engine update themselves kept the **old** app version's bundled engine active
after an app update that shipped a different bundled yt-dlp version: `active()` returns the active
installation as long as it's healthy. So the app update never actually reached extraction. The new engine
just sat there as a rollback target, and "Switch to previous engine" offered it with a note that an older
version might have gaps. Found in round 17 while reading the code for ADR 0009; not a reviewer finding.

## Decision

At the first start where this app version's engine has no healthy entry with `bundled = true` yet, it
becomes active **if the currently active installation is itself a bundled one** — that is, one belonging
to an earlier app version. The previously active installation becomes the previous one, staying available
as the way back. If the user had activated a downloaded engine (`bundled = false`), it stays active; that
was a deliberate choice, whereas keeping the old bundled one was not.

After that, it never happens again: if someone later deliberately switches back to the old bundled
engine, it stays active, because the new one by then has a healthy bundled entry. Running and waiting
jobs keep their engine as before (S7).

## Edge Cases

- If someone had already loaded exactly this version as an update and then gone back to the old bundled
  engine, the app update switches to it anyway: until then, its entry wasn't marked as bundled.
- If a third engine had been the previous one, having deliberately switched back to the old bundled
  engine from it, that third engine is no longer reachable via "Switch to previous engine" afterward,
  because the old bundled engine becomes the previous one instead. That's how every manual activation
  ends: the way back reaches exactly one step. The installation stays in the list until it yields during
  clearing (ADR 0009).
- A crash between the two writes inside `ensureBundledLocked` only postpones the switch to the next
  start. In between, nobody can switch back to the old engine, because `rollback()` and `activate()` both
  run `ensureBundledLocked` first, which catches up on the switch itself.

The first two cases are rare, and an app update is itself an explicit action.

Through round 18, the first edge case listed here claimed that, after such a crash, or with a slot that no
longer passes verification, the switch would happen again, even after a deliberate switch-back. Both were
wrong: the crash allows no switch-back in between, and an invalid slot ended in `materializeSlot` with
`VERIFICATION`, without switching anything, on every manager call that runs `ensureBundledLocked` first.
Since [ADR 0011](0011-damaged-bundled-engine-slot.md), it gets replaced instead; the healthy entry stays,
and nothing gets switched. The round 18 code reviewer reported the second case. The round 19 code
reviewer showed that the third case's reasoning overreached: `check()`, `discardUnhealthyCandidate()`,
and `file()` don't go through `ensureBundledLocked` at all, but they don't switch anything either.

## Alternatives Rejected

- **Never switch:** the app update would then never take effect for extraction, and the new app's runtime
  would have to work with a yt-dlp version that was never verified against it.
- **Always switch to the newest version:** version numbers aren't reliably comparable across the Stable
  and Nightly channels, and a deliberately activated engine would get overwritten.
- **Ask at start:** a dialog before every use, one that background jobs can't answer.

## Tests

`EngineUpdateManagerTest` verifies the switch at the first start, that it's kept on the second start and
after a deliberate switch-back, and that an activated downloaded engine stays active.
