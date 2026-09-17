# Learnings for Resumption

**As of 2026-09-16.** Failed attempts and fixes are in the
[2026-09-07 integration report](reports/2026-09-07-integration.md) and the
[2026-09-08 preview report](reports/2026-09-08-preview.md); this file holds the reusable conclusions.

- **Build is not runtime evidence.** Name the device, the APK hash, and the actual path taken; distinguish a
  fixture, a real source, and a real provider call. 185 runner cases with five skips means 180 tests passed.
- **Judge UI from the image.** Check label/value spacing, button spacing, proportions, stable dialogs, and
  200% font size. Batch related fixes with a static review and build once. Scrolling disproved a viewer that
  looked cut off — don't rebuild on every suspicion.
- **TalkBack is audible.** A semantics test and a bound service don't prove full operability; ADB/UIAutomation
  can bypass the InputFilter chain, and an input event is not a proven focus change. Announce audible tests
  and restore starting values exactly afterward.
- **Wait out async UI.** Track selection and start only after source validation has finished; a round-75
  abort was premature test automation, not an app bug.
- **Prove process death.** `am kill` did not end the bound process; only kill an identified PID and confirm it
  is gone. A round-78 run resumed successfully after the 100-second window — an earlier timeout stays FAIL,
  and a later result covers only what it actually captured. Dozing is not automatically a failure in the
  display-off test.
- **Use official SDK tools.** `aapt2` resolves backup resource mapping and `apksigner` checks the signature;
  not being on PATH doesn't mean absent. Compare the APK hash before/after an audit, and check native ZIP
  payloads and actually-used replacement assets — a large homegrown parser is not a sufficient gate by itself.
- **Don't use JSON as another language.** Android's `JSONObject.quote` escaped slashes; used directly as a
  Python literal, that produced wrong test paths. Read data with JSON instead of nesting languages.
- **Keep CI paths and error output explicit.** AVD creation and the emulator need the same directory; the
  API 37 emulator needed RAM raised from 2 to 4 GiB, now set explicitly. After a successful boot, read
  JUnit/UTP output rather than treating every later hiccup as a boot timeout.
- **Bound the load.** WSL ran out of memory before 16 GiB of swap was made active; one build worker, a 2 GiB
  heap, large caches on the project drive. More swap does not replace a concurrency limit.
- **Keep reviews narrow and independent.** Luna found real edge cases but also disproved lock/cursor/layout
  suspicions; require and verify file, precondition, and reproducible deviation. Vague audits produced
  oversized helper tools — handle small things directly, route clear larger packages to Sol and UI/integration
  to Astra.
- **Keep the handoff current.** Don't chain contradictory pauses; history belongs in git/reports. SDK, raw
  logs, private keys, and app data don't travel automatically, and remaining acceptance work is not purely
  cosmetic.
- **A dexing error with a lowercased project path is stale Gradle state, not the class it names.**
  `DexingNoClasspathTransform` complained about a path differing only in letter case; two runs named two
  different, sometimes unchanged, classes. Fixed with `rm -rf core/build` from WSL and `--no-watch-fs` —
  empty the build folder rather than chasing the named class.
- **Don't edit the repository during a running build.** A string inserted after R generation made
  `compileDebugKotlin` fail on an "unresolved reference" that no longer existed by the time the message
  appeared. Batch changes and apply them between runs.
- **"Internal error: Unexpected lint invalid arguments" can mean low memory.** In round 16 a lint run ended
  this way; WSL's kernel log showed a failed page request reading a directory over 9p, and a second run
  succeeded once memory was free. Read `free -m`/`dmesg` and split the Gradle calls rather than suspecting the
  code. Round 21 showed the same root cause behind "Could not read directory path" in a resource-merge task;
  the next build of the same task succeeded.
- **Lint before every UI-affecting commit.** `1b2dc45` landed with a lint failure (`ModifierParameter`)
  because lint only ran later, in the gate.
- **K2 carries a smart-cast through a local Boolean `val`.** If `existingHealthy` comes from
  `existing?.healthy == true`, `existing` is treated as non-null after `existingHealthy && …`, and a further
  `existing?.bundled` there becomes an "unnecessary safe call" warning — a build failure under
  `allWarningsAsErrors`. Order the condition so the safe call comes before the check.
- **No instrumentation next to a Gradle build.** A counter-test failed on a runtime self-test's 30-second
  timeout while a WSL build ran alongside; the same test APK passed without a concurrent build. Not proven as
  the cause, but a counter-test that fails under load proves nothing either way.
- **`preferencesDataStore` binds once per process.** The delegate creates a store on the files of whichever
  context first uses it, and every later context gets that same one. A store per context needs
  `PreferenceDataStoreFactory`, one instance held per file — DataStore forbids two for the same file.
- **A fake context must keep the real one's contracts.** `Context.getFilesDir` creates its directory; a fake
  that did not made `File.usableSpace` report 0, so the storage check saw a full disk in seven tests at once.
- **`Context.deleteDatabase` does not remove a `.lck` file.** Per the AOSP source, it deletes the database,
  `-journal`, `-shm`, `-wal`, a framework check file, and `-mj` files, but not the `<name>.lck` lock file that
  sat next to migration-test databases. Whoever cleans up test databases deletes them and then confirms
  nothing with that name remains.
- **A check script carries its verdict in its exit code.** `r16_typing.py` reported a deviating case in
  round 17 and still exited 0; reading only the exit code would have counted the run as passed.
- **A reviewer's finding about a library's behavior names a version.** `ExternalResource` called `after()`
  inside a `finally` in JUnit 4.12, replacing the test's own exception; in 4.13.2, which this project uses,
  the rule collects both and throws a `MultipleFailureException`. A round-19 reviewer described the old
  behavior.
- **A file that others open by path is replaced by renaming over it, not by removing and renaming.**
  `Files.move` with `ATOMIC_MOVE`/`REPLACE_EXISTING` calls `rename(2)` on the same filesystem: the entry names
  the old file and then the new one, never neither, and a symlink in that place is itself replaced, not its
  target. A directory in that place cannot be replaced this way.
- **A line number in a document goes stale with the next change above it.** In round 19, eight of thirteen
  line references in `docs/DEFECTS.md` pointed at different code, one at a rollback function instead of a
  hash check. Name the function or quote the expression instead; `tools/check-repository.py` rejects line
  numbers under `docs/`.
- **Building a commit from text substitution also has to carry the file mode.** `git update-index
  --cacheinfo` requires it, and round 19's staging script hardcoded 100644 — `tools/check-repository.py` lost
  its executable bit without a single line changing. Carry the mode over from `HEAD`; the script now checks
  scripts with a shebang.
- **A file cannot be renamed over a directory.** `rename(2)` refuses with `EISDIR`, and `Files.move` with
  `ATOMIC_MOVE` reports an `IOException`. On the API 37 emulator, a slot-repair test exercises exactly this by
  placing a directory at the target.
- **`grep -l` counts files containing a string, not calls.** A commit message cited 55 classes calling a
  method older Android versions lack, and a round-21 reviewer confirmed the count with the same grep — but
  the string also names an identically-named method of a different library, and no class actually calls the
  one in question. Read method references from the constant pool (e.g. with `javap -c`) instead.
- **Lint compares versions against a cache.** `NewerVersionAvailable` reads `maven-metadata.xml` under each
  module's lint cache; a stale local cache meant only CI caught an outdated dependency. Clear that cache
  before a local lint run meant to stand in for CI.
- **Evidence names the code that ran, not just `HEAD`.** Round 20's gates were labeled with a `HEAD` that did
  not yet contain the code that actually ran; only a diff fingerprint recorded alongside it tied the run to
  the right code. Later gates assert the tree is `HEAD` plus exactly the named fixes and record that in their
  own output.
- **Compose passes only dots to accessibility for a password field's content, in the version this app
  bundles.** Text typed into a masked settings field appeared as dots (with `password=true`) in a
  `uiautomator` dump; a reviewer had concluded the opposite from reading Compose's source on GitHub without
  naming a version. An unmasked field does pass its content through in plain text, confirmed by testing that
  case first.
- **ASCII armor ends, for Bouncy Castle, at its footer — and the footer at its own line break.** In 1.86,
  `ArmoredInputStream` returns nothing after `-----END PGP SIGNATURE-----`, even with a second block behind
  it; without a trailing line break, appended text is treated as part of the footer and disappears with it. A
  check that requires exactly one signature has to inspect the raw bytes behind the footer, not just what the
  parser left unread.
- **A green CI run does not rule out a flaky failure.** `ChoiceAccessibilityTest` failed in three CI runs,
  passed once with no change to its condition or the app's code, then failed again. Only a message naming the
  state of the searched element revealed the cause: present, but disabled because the app was still starting.
- **Redirecting `wsl.exe` output to a file from Git Bash lets stderr overwrite the start of stdout.**
  `wsl.exe -e bash -lc '…' > file 2>&1` wrote the stderr line over the first line of stdout, because the two
  streams write from separate positions that start out equal. Pipe through `cat`, or redirect inside WSL,
  instead.
- **A test that checks an interaction first waits for the app to allow it.** `MainViewModel` runs an
  exclusive action at startup that locks the language picker (among other things) and shows a progress bar;
  in CI this took over 25 seconds. Since round 23 the test waits until no progress bar is visible.
- **A fixture that plays the role of an installed engine has to answer everything the app asks a real one.**
  (14-15 September 2026) The start-up-lock fix (`e66761a`, closing item 47) made the runtime re-check probe
  any active engine that is not the bundled one. `AppPipelineTest`'s caption-only fixture engine had been
  standing in for exactly such an engine without ever answering `yt-dlp --version` or the EJS probe, so three
  device tests broke and CI was red for two commits. Fixed by making the fixture engine answer the self-test
  (`0509a18`), not by weakening the product's new check.
- **The Android Gradle plugin writes a failed assumption as a `<testcase>` with a `<failure>` element, not as
  `<skipped>`.** (14-15 September 2026) Counting instrumentation results from the XML report therefore has to
  read the failure text's exception type (`org.junit[.internal].AssumptionViolatedException`) per test case;
  trusting a suite's own `tests`/`failures`/`skipped` attributes counts an opt-in test that skipped itself as
  failed (`a648add`, correcting `edaa927`).
- **An MP3 chunk from the bundled ffmpeg encoder is 84 to 96 ms longer than the window it was asked to
  encode.** (16 September 2026) The output is a whole number of MP3 frames plus the encoder's own delay. A
  length check has to compare the measured length against the *planned window* with a tolerance, never
  against the same hard maximum the window itself is bounded by — checking the encoded length against that
  bound is what made every source over ten minutes fail its first chunk until `f18937f`, the 0.4.0 P1 fix.
- **`adbd` writes the whole `am instrument` command line, arguments included, into the device's system log.**
  (16 September 2026) A key passed as an instrumentation argument (e.g. `-e liveProviderKey <key>`) reaches
  the device's own logcat even though the app's process never logs it. A test or build script that passes one
  has to end with `adb logcat -b all -c` afterward; while `adb.exe` runs, the key is also visible in its own
  Windows command line.
- **A layout test's measuring height has to exceed every alternative's real content, or a bounded dialog
  clamps every measurement to the same number.** (16 September 2026) `JobActionsLayoutTest`'s first version
  measured at 480dp, which every situation's content exceeded, so a dialog that clamps its height at that
  bound passed the test with its own fix reverted. Raised to 900dp — above the shortest situation's content at
  font scale 1 — the same reversion produced three distinct heights again.
- **An emulator started under `timeout` dies when the bound runs out, whatever is running on it.** On
  16 September 2026 `emulator-5556` had been started with a 12-hour bound in the morning; the bound expired
  at 22:26 in the middle of the 0.4.0 device gates and cut the extractor suite and every live test, which
  then reported `device not found`. Start a test emulator with a bound longer than the working day, note
  when it expires, and read a sudden `device not found` as the emulator, not the app.

## Deliberate decisions a reviewer keeps re-reporting as bugs

Moved here from the retired `DEFECTS.md` so a future review round checks this list before re-opening one of
these. Each was investigated once, found to be a considered trade-off rather than an oversight, and is
recorded so the investigation is not repeated.

- **Tapping "Start" is the deliberate authorization, even right after "Prepare again."** A re-prepared job
  starting and incurring cost on a single further tap looks like a missing confirmation, but this is
  intentional app-wide: `configurationForStart` recomputes authorization from mode, model, and matching
  stored key at every start, and a tap **is** that authorization, bound to exactly that configuration. The
  invariant requires nothing be repeated *silently* — a tap is not silent — not an extra hurdle on top of it.
- **`MALFORMED_SEGMENT` and `MALFORMED_WORD` deliberately sit in different warning groups**, even though both
  arise on the same line of `parseEntries` and both drop the entry. The segment list is the displayed reading
  text, so a missing entry there is a missing passage; the word list only carries per-word timing, so a
  missing entry there is a missing timestamp. Same code, different consequence for the reader.
- **The title's 40-byte cap in `TranscriptExporter.compose` is load-bearing, not cosmetic**, even though the
  byte-budget arithmetic around it once had a real unit mix-up (bytes vs. character count). The mix-up never
  triggered, because `generatedStem` already runs the title through the same 40-byte cap beforehand — the
  widest name the builder can assemble stays under the 180-byte limit as long as that cap stands. Increasing
  the title's share of the name would remove this second backstop.
- **Most `PROVIDER_*` and `RESPONSE_*` branches in `messageText` are unreachable today, on purpose.** `SttStep`
  handles provider errors itself within each phase and stores the bare code, so these branches rarely fire —
  but they stay as the safety net for a `ProviderError` that escapes phase handling, where the step name
  ("While submitting to the provider") is exactly right. The `AUDIO_*` branches modeled after
  `ExtractionFailure` were different, had no conceivable producer at all, and were removed.
- **Expandable elements shift what is below them on purpose** — the job card in history, help entries, the
  provenance section in the result view, the advanced options, and the hint above "Check source" that
  disappears once a key is chosen. The no-jump rule is read as a rule against motion nobody triggered; here the
  change happens exactly where the reader just tapped and shows what was asked for, and reserving space for
  collapsed content would defeat the point of collapsing. Changing this needs every occurrence counted first
  and changed together, not one at a time.
- **After a process death, a job field shows the draft's saved value, not the text that was typed and never
  saved.** `rememberSaveable` text vanishing after `am kill` looks like a bug, but the draft lives only in the
  view model and dies with the process by design: a new process gets a new session id, and a field shows what
  the draft holds, because start reads the draft, not the field. Preserving typed values across a process death
  would mean saving the draft itself (e.g. via `SavedStateHandle`), not a field's text.
- **A retried chunk upload shows its progress from the finished chunks again, not from where the failed
  attempt got to.** `SttStep.countingUpload` records the sum of the completed chunks when a submission starts,
  so after a transient network failure the byte count and percentage visibly go back before climbing again.
  Holding the failed attempt's figure would present bytes the provider never accepted as progress, and the
  invariant forbids an invented percentage; only the measured rate is kept from going down. Recorded after
  review round 1 of 0.4.0 (16 September 2026).
