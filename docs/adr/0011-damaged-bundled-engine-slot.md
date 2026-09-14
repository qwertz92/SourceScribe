# ADR 0011 — A Damaged Bundled-Engine Slot Gets Replaced

Date: September 14, 2026. Status: implemented in round 18, switched to an in-place slot replacement in
round 19, extended in round 20 to cover the outcome when only the second rename fails.
Which checks have actually run is recorded in [STATUS.md](../STATUS.md).

## Context

Since `06b996a`, `EngineUpdateManager.materializeSlot` has rejected a slot whose directory exists but
whose file doesn't hold the bytes whose SHA-256 is the slot's name — including a symbolic link sitting in
its place: `VERIFICATION`, without changing anything. That's correct for an update. For the bundled
engine, it was a dead end. `bundled()`, `active()`, `installations()`, `rollbackTarget()`, `rollback()`,
`stage()`, and `activate()` all go through `ensureBundledLocked` first — every manager call except
`check()`, `discardUnhealthyCandidate()`, and `file()`. So every one of them ended in `VERIFICATION`,
after every app start: no YouTube source could be checked anymore, and no job that needs an engine could
start, until someone deleted the app's data — and with it, its history.

How a slot can end up like this: a storage error; a removal that aborted between deleting the file and
deleting the directory while `state.json` was unreadable, so the next load cleans up nothing; a process
sharing the same UID, which is no boundary anyway. None of these has actually been observed. The engines
live in `noBackupFilesDir`, and backups are disabled, so a restore is ruled out as the cause. Found in
round 18 while reviewing ADR 0010; not a reviewer finding.

## Decision

`ensureBundledLocked` replaces such a slot with the bytes it has just copied from the APK and verified
against checksums and signature. The copy sits complete and verified in a temporary directory before it
replaces anything. If the slot is a directory, a single rename takes the place of the file inside it, a
second rename does the same for the metadata, and the directory itself stays. If something else sits in
its place — a symbolic link, say — or the file can't be replaced that way, the slot is removed and the
temporary directory is renamed into its place instead. A symbolic link itself gets replaced or removed
this way, never its target. `stage` continues to reject such a slot.

`docs/SECURITY_UPDATES.md` requires, in S7, never swapping files out from under a running process. That
applies to a valid engine. Bytes that don't match their hash aren't one, and whoever executes them is
running damaged data. The exception is a read error while hashing an intact file, which `validSlot`
treats as invalid, like any other exception — in which case the same bytes come back. A process that
already has the file open keeps reading the old one, and one that opens it by path finds either the old
or the new one, because the rename swaps the entry in a single step. That holds for each of the up to
four jobs allowed to run at once. Only where the slot is removed and recreated does that in-between path
go missing. That happens where no directory sits in its place, where part of its path is a symbolic link,
or where a directory sits inside it instead of the file; `file()` rejects such a slot, and no job starts
from it. But it also happens when the rename over an intact file fails — on a storage error, say — in
which case the in-between path is missing, just as it was in round 18 for every replacement. yt-dlp only
runs while resolving a source and while downloading, meaning before any request to a provider; a failure
there never repeats a billable request.

If only the second rename fails, the verified file already sits in the slot, next to the old metadata,
and the call still ends in `STORAGE`: a storage write failure is never hidden from the caller. The next
call finds the slot valid, because `validSlot` checks only the file, and uses it. The old metadata is
left behind; `metadata.json` is only ever written and moved, never read anywhere.

## Alternatives Rejected

- **Also replace in `stage`:** an update is voluntary, and there, refusal only blocks that one update.
  The slot can belong to a downloaded engine that a running job is bound to. Through round 19, this entry
  also claimed there was no deterministic test for reaching the slot, because a valid signature belongs
  to a real release. But the bundled engine is itself a signed release, and a test can deliver it to
  `stage` through a fake of the network.
- **Always remove the slot and rename the temporary directory into its place, as in round 18:** the path
  goes missing in between, and a job that starts the engine at exactly that moment fails, even when only
  a read error made the slot look invalid. Reported by the round 19 code reviewer.
- **Let the call succeed as soon as the file is in the slot, even if the metadata doesn't follow:** the
  engine would become usable one call sooner, but the storage failure would go unnoticed. Proposed by the
  round 20 code reviewer.
- **Delete every invalid slot on load:** this also hits slots that jobs are bound to, and replaces
  nothing.
- **Accept the failure:** the only workaround was deleting the app's data.

## Tests

`EngineUpdateManagerTest.aDamagedBundledSlotIsReplacedWithTheVerifiedEngineInsteadOfStoppingEveryCall`
truncates the file while the manager that verified it is running, deletes it before a restart, and puts a
symbolic link in its place whose target must stay unchanged. A file sitting next to the engine in the
slot is left alone every time, because the directory itself is never replaced, and finally a symbolic
link takes the place of the whole slot, while the directory it points to stays exactly as it was.

`EngineUpdateManagerTest.stageRefusesADamagedSlotOfTheEngineItDownloadedInsteadOfReplacingIt` delivers the
bundled engine to `stage` through a fake of the network, damages its slot during the download, and
asserts `VERIFICATION` with the slot left exactly as it was; only the next call that sets up the bundled
engine replaces it.

`EngineUpdateManagerTest.aSlotRepairWhoseMetadataCannotFollowFailsWithStorageAndLeavesTheEngineRepaired`
puts a directory where the metadata should go — something no file rename can replace — and truncates the
engine. The call ends in `STORAGE`; the slot then holds the verified bytes, and the next call uses them.
