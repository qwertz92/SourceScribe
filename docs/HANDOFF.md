# Handoff After Personal Preview 0.2.0-preview.1

**As of 2026-09-14.** After the 0.1.0 preview (2026-09-08), the owner tested the app and sent 20 items of
feedback on 2026-09-10; 17 are implemented, and 23 rounds of independent adversarial review checked code and
docs. `0.2.0-preview.1` is published with its signed APK, and the work towards `0.3.0` follows
[NEXT_STEPS.md](NEXT_STEPS.md): P1 findings are always fixed, findings that take minutes are fixed on the spot,
and everything else is recorded in [BUGS.md](BUGS.md) with its priority.

## Starting up on another agent or machine

1. Read [AGENTS.md](../AGENTS.md), [PRODUCT.md](PRODUCT.md), [ROADMAP.md](ROADMAP.md), [STATUS.md](STATUS.md),
   [BUGS.md](BUGS.md), [NEXT_STEPS.md](NEXT_STEPS.md), [DEFECTS.md](DEFECTS.md), and the
   [0.2.0 preview report](reports/2026-09-14-preview-0.2.md). Read other technical docs as needed for the
   specific change at hand.
2. Check git state, recent commits, toolchain, and `adb devices -l`. Work on `main`; keep existing changes.
   The 0.2.0 APKs are built at commit `1fe2dad`; later documentation-only commits do not change those inputs.
3. On a fresh Linux/WSL system, use [BUILD.md](BUILD.md). SDK, caches, APKs, and raw logs are deliberately not
   in git. Source, the wrapper, checksums, Room schemas, small fixtures, and reports travel with the repo.
4. Work the user's chosen, bounded target. Do not redo checks that already passed. Reproduce UI feedback on
   the actually installed version and look at the screenshot yourself.

## Where things stand

- All P0-P5 functional areas are integrated; full v1 acceptance is open. Live speech-to-text runs need the
  owner's API keys and cost money: agents do not enter API keys, and the owner's own tests with a key do not
  authorize an agent to make paid calls.
- Preview 0.2.0 at commit `1fe2dad`: 187 JVM tests pass; on `emulator-5556`, 202 app-suite tests pass (6
  opt-in skips, the 4 process-death stages passing individually), 46 extractor-suite tests pass (4 opt-in
  skips); all 4 lint reports clean; static release check on the unsigned APK passes; CI run
  [34865638431](https://github.com/qwertz92/SourceScribe/actions/runs/34865638431) is green. Details:
  [preview report](reports/2026-09-14-preview-0.2.md).
- Release app `app.sourcescribe`, version 0.2.0 / code 2, tag `v0.2.0-preview.1`. Unsigned APK at
  `.local-tools/releases/SourceScribe-0.2.0-preview.1-1fe2dad-unsigned.apk`; the user signs it with the 0.1.0
  key via `tools/sign-release.sh` (commands in [BUILD.md](BUILD.md)). The signed 0.1.0 APK is no longer under
  `app/build/outputs/`; its hash and certificate are in the
  [0.1.0 preview report](reports/2026-09-08-preview.md).
- Debug app `app.sourcescribe.debug` stays separate. On `emulator-5554` it held earlier real caption jobs and
  a synthetic 10,000-segment file as of 2026-09-08 — do not delete or overwrite it. App data does not travel
  with the git clone; API keys need setting up again on a new device.
- The device step of CI failed in four runs on 2026-09-10 and 2026-09-14 on `ChoiceAccessibilityTest`; the
  last showed the app was still starting up. Since commit `1fe2dad` the test waits for startup to finish, and
  the run on that commit is green.

## Private files and portable evidence

The personal signing key lives outside the repository. On this machine, `.local-tools/PRIVATE-RELEASE.md`
names the private storage locations and backup steps — no password values. Use the same key and a higher
version code for updates. Without a separate secure key transfer, another machine can build but cannot sign a
compatible update to this personal install.

`.local-tools/build-reports/` holds ignored raw logs, hash lists, and screenshots, the 0.2.0 preview's under
`preview-0.2.0-1fe2dad/`. The portable summary for 0.1.0 is
[preview-evidence.json](reports/2026-09-08-preview-evidence.json); selected harmless screenshots are
versioned. An additional build on a fresh clone is still `NOT_RUN`.

## Local environment

Windows ADB: `/mnt/c/Users/thoma/AppData/Local/Android/Sdk/platform-tools/adb.exe`. `emulator-5554` (API
37/x86_64/16 KB, 1280x2856, density 480) belongs to the user — agents do not operate or read it. Agents use
`emulator-5556`, API 37/x86_64/16 KB, 1080x2424, density 420. Windows ADB expects Windows paths for `install`
(`wslpath -w`); Linux ADB expects Linux paths. Java 17.0.20.1, Gradle 9.7.1, AGP 9.4.0, Kotlin 2.4.20, Compose
BOM 2026.08.00. SDK and caches under `.local-tools/`; no global configuration needed.

16 GiB of WSL swap is active. One Gradle process, one worker, 2 GiB heap; do not move SDK/build data to
RAM-backed `/tmp`. The lead agent runs the only ADB check stream; no build-input changes while Gradle runs.
Reviewers read an export (`git archive`) while a build or device check runs alongside. Confirm findings
before acting on them.

As of 2026-09-08 on `emulator-5554`: TalkBack off, font scale 1, temporary accessibility/network/display
values restored. Announce audible tests beforehand. Stop your own workers and save changes before turning
anything off later; do not kill shared emulator/ADB/Codex processes without cause. See also
[LEARNINGS.md](LEARNINGS.md).
