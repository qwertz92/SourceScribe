# History and source workflow improvements

Date: 2026-10-03. Baseline: `490baab` on `main`, published app version 0.4.1.
Published 2026-10-04T05:57:01Z from signed source `df32dede854355ac38f933c8cdb0a266c3ba0657`; APK SHA-256 `7b1fac241672c7bb16739d440069ea97edeffc6c6e03857e3c250c69d2690c26`, `147200333` bytes. The unauthenticated release download matched the local APK. The signed release and verification details are below.

## Scope and acceptance

- Compact completed history cards; hide actions when a successful card is collapsed, preserve actions after expansion.
- Show the source that actually produced each result and its channel; distinguish configured fallback providers.
- Export `Channel - Title.ext`; use numeric suffixes only for collisions in that destination and never overwrite.
- Start sources with the current settings without a manual preview, retaining validation, consent, and cost limits.
- Group expert options and remove unused spacing; maintain stable live controls and accessible touch targets.
- Show contextual help as an overlay with an explicit link to the full help page.
- Open export folders from history; use Android's app chooser or its default app according to settings.
- Keep total time after completion and show measured phase durations. Old records and interrupted measurements stay explicitly unknown.
- Run independent adversarial reviews, regression controls, device checks, a signed in-place upgrade, and public APK delivery.

The persistence and workflow contract is recorded in [ADR 0015](../adr/0015-job-timing-and-quick-start.md).
Android folder handling follows the native [Intent chooser contract](https://developer.android.com/reference/android/content/Intent)
and [document tree contract](https://developer.android.com/reference/android/provider/DocumentsContract).
An installed file manager must support viewing document directories; no particular file manager is required by the app.

## Reproduced causes and test-first evidence

The old collapsed finished card passed a null current attempt into a component that still reserved live phase/progress height.
Expanded cards also reserved empty transfer/error slots for finished attempts. Provider labels came from configuration rather
than artifact provenance. Source snapshots already contained channel metadata, but history did not render it.
The database had no completion timestamp or durable phase measurements, so old jobs cannot acquire trustworthy durations.

New filename regressions were run against the old implementation in an immutable native Linux snapshot.
`230` core tests ran, with four expected failures in `TranscriptExporterTest`: channel/title naming, numeric collision suffix,
fallback names, and UTF-8 filename budgeting. The first attempt on the Windows-mounted checkout failed before tests because
Gradle could not read its `core/providers` test directory; that failed build is not counted as a negative control.
Raw output and the failing JUnit XML are retained in ignored `.local-tools/build-reports/2026-10-03-filename-*` receipts.

## Verification status

Core: `234` passed, zero failures/skips. Four strict lint reports: zero issues. The earlier feedback build passed in 11m 04s; the folder-label correction rebuild passed in 8m 32s. App instrumentation: `291` passed, zero failures, 10 documented opt-in skips; all ten opt-ins were separately exercised. Extractor: `55/55` passed with all flags enabled. Separate cases passed for four process stages, engine pinning, offline restart, metered wait/unmetered resume and one seeded UI fixture. Groq Turbo and AssemblyAI `universal-3-5-pro` each produced one successful live transcript on the exact public 19-second source. KSP `2.3.12` passed the compatibility gate.

The candidate upgrade from signed 0.4.1 preserved the completed job, displayed transcript hash, settings, app ID and app-data inodes. A final replacement-artifact recheck was also completed on the exact published APK. The final release build embeds the source SHA; its signed tag and public APK hashes are recorded in [STATUS](../STATUS.md) and summarized at the top of this report.

On 2026-10-04 an isolated negative-control copy restored the old 40-byte filename stem cap and disabled all upload timing
callbacks. The complete `234`-test core suite failed exactly four relevant tests: readable long titles, long-title budgeting,
upload timing excluding JSON/server wait, and failed-body timing preserving submission uncertainty. The unmodified sources
remained green. The controlled copy was then removed: `.local-tools/workflow-core-negative`, 14,749,542 regular-file bytes;
failure XML and control descriptions remain in ignored `.local-tools/build-reports/2026-10-04-core-negative-control`.

## Independent review and negative controls

Six native GPT-6 Luna product-review stages covered export names, history, setup/help and quick-start/folder integration; an independent KSP metadata review also completed.
They identified two normal-flow defects: an identical newly shared link could be cleared by an earlier successful start,
and a partly recorded phase lost its phase label. Both were corrected; targeted UI/guard regressions are added.
Minor findings corrected in this round include the old 40-byte title cap and changing folder-row heights as labels load.
Setup/help follow-ups cover expert draft-name retention, readable context status at large fonts, and excessive overlay height.
The focused setup follow-up found no remaining demonstrated defect. Across six stages the independent readers identified and rechecked the fixes; the final scoped passes reported no demonstrated P1/P2. A later P2 showed that export success/error snackbars were hidden behind the full-screen result viewer. The viewer now hosts that feedback, and export-action rows wrap on narrow screens. Both new regressions timed out against the old APK and passed against the final APK. The narrow-folder label issue was rated P3, fixed with one-line ellipsis, and checked against the 173-character negative case (212 px before versus 53 px normally); the final installed build passed the positive check. A proposed size issue was withdrawn:
each overlay has a fixed topic while open; separate topics need not all reserve the longest topic's height.
Actual native-review runtime metadata was checked; all six review stages ran native GPT-6 Luna. IDs and routes are in ignored `.local-tools/build-reports/2026-10-04-workflow-final-review-runtime.json`.
Android SAF providers may rename a newly created document under their own constraints or an external creation race;
the app retains the returned document URI and never opens an existing file for overwrite. That platform behavior is not
evidence of an overwrite or a false saved filename in the app.

The timing review identified a persisted-cancellation window after timing setup. A local HTTPS/Room trigger
regression failed against the earlier code with exactly one unwanted Groq fixture request. The fence now runs
after timing/progress setup immediately before submission; the targeted device test now passes with no fixture request. A separate rare cancellation scheduling gap can leave
upload timing incomplete when an OkHttp body writer starts after the final snapshot. Cached OkHttp 5.5.0 bytecode
supports that ordering, but establishes no second request or changed job/submission state. It is recorded as P3
BUGS75, with a multi-hour deterministic scheduling/lifecycle check deferred under the repository review rule.

The final timing review also found that an ordinary progress-database exception could hide an already accepted,
durably spooled provider response. A Room trigger rejecting positive progress updates reproduced this with exactly
one request: the submission remained `SENDING` rather than `RESPONSE_SAVED`. All five progress writes now use one
best-effort helper that preserves cancellation. A fresh focused native Luna review found no further issue; the
positive device regression passed in the complete app run, retaining the accepted response and exactly one fixture request. A proposed total-duration issue was withdrawn because the label explicitly
includes waiting and recovery until the durable terminal job transition.

The first Android control run exercised eight cases: six failed at the expected regression assertions, and the
new cancellation guard passed. The expert draft-name case initially stopped at test navigation, so that run was
not a valid control. After correcting the fixture, a single rerun against the preserved controlled APK failed
exactly at “Collapsing expert options discarded the unsaved name.” The correct app was immediately reinstalled
without clearing data. This establishes a seventh valid control; the separate cancellation control also remains valid. Other demonstrated
controls cover identical-input clearing, incomplete phase labels, blank overlay height, large-font guidance,
and timing-finalization failure. A P4 coverage limitation remains: the revision guard test does not exercise the
production Android share-intent wiring itself; source review verifies its current wiring.

The first full build completed APK packaging but failed during lint due to host memory exhaustion. A separate
lint retry then reported three real issues: an unguarded notification constant, an intentional portrait-only
test activity, and an unused resource. These were respectively guarded, narrowly documented/suppressed in the
fixture, and removed. Neither failed build is counted as a passing gate; the later corrected strict lint passed.

An immutable baseline snapshot was removed after its failing JUnit XML and output were secured:
`/tmp/sourcescribe-filename-before.OcS2xO`, 14,653,011 regular-file bytes. Its source is recoverable from Git.

KSP `2.3.12` was upgraded and passed the compatibility gate after review at `c735`. Evidence: [Maven metadata](https://repo.maven.apache.org/maven2/com/google/devtools/ksp/symbol-processing-gradle-plugin/maven-metadata.xml),
[official release notes](https://github.com/google/ksp/releases/tag/2.3.12). Other catalogue stable pins matched their queried
authoritative repositories. OSV had no matches for the queried catalogue/BOM versions; transitive dependencies were not audited.
The measured Gradle 9.8.0 warning incompatibility remains documented in BUILD; the wrapper is still 9.7.1.

On 2026-10-04 the UI failures were traced separately: HelpScreen intentionally scrolls to the focused topic,
so its search field was not a valid visible postcondition. Three action failures involved stale Compose virtual
nodes; both old and freshly queried cached nodes returned `refresh=false`. The tests now clear the UiAutomation
cache on API 34+, reacquire valid nodes, and assert the actual full-help destination. Seven targeted UI tests pass,
zero failures/skips. Both subsequent test builds passed strict debug lint (zero issues). Raw failed diagnostics,
positive UI output, and the valid expert control remain under `.local-tools/build-reports/2026-10-04-*`.


## Final live and device evidence

The 0.4.2 live provider runs used exactly `https://www.youtube.com/watch?v=jNQXAC9IVRw`, a public 19-second video. Groq Turbo produced one successful transcript; Groq returned no model field, so the reported model remains unknown. AssemblyAI `universal-3-5-pro` produced one successful transcript after one audio-upload request, one paid transcript POST and two retrieval GETs. The earlier 0.4.1 evidence was three successful Groq jobs and two paid AssemblyAI submissions, one partial and one complete. OpenAI has no supplied key and has no live coverage. No provider request was repeated for the 0.4.2 UI feedback changes.

History opened the export directory through Android DocumentsUI at `Downloads/SourceScribe-0.4.2-test`, where `jawed - Me_at_the_zoo.md` was visible. Both `chooseFolderApp=true` and `false` branches succeeded. Only one compatible handler was installed, so Android's multi-handler chooser and Solid Explorer were not tested. The folder-name row fix caps the native label at one line with ellipsis: a 173-character label measured 212 px before the fix versus 53 px after it on the final installed build. The last reader identified this P3; under the stop rule no new review stage was started after the small correction.

The signed 0.4.1-to-0.4.2 upgrade and the final same-versionCode replacement install both retained the job, transcript text hash, settings, app ID and app-data inodes. Seven prior Android controls, the cancellation control, the share recovery control two feedback controls, and the final terminal-status control each failed on their corresponding prior behavior; the final app passed all positive checks. In total, 12 distinct automated Android controls were valid; the manual folder-layout check is separate. The four-test core negative control also failed at the intended regressions. Physical ARM64 and complete TalkBack navigation remain unverified.

A share-cache UUID fix (`13f9493`) passed independent review and negative control before the release source commit. A cancellation bug during phase-timing setup, a result-hidden feedback P2, and the final long-folder-label P3 were fixed. Both feedback regression cases failed their negative tests against the old APK and passed against the final APK. The export action rows also wrap with `FlowRow` at narrow widths. KSP `2.3.12` passed. The final review found no remaining P1/P2 in the changed scope; minor residuals are tracked in the existing issue list.

The final terminal-status correction stops reserving queue-message lines after a job finishes or is cancelled. Its new geometry regression failed against the prior APK (252 px versus the actual 84 px line at 200% font), then passed at both font scales. In the final signed German card, the successful outcome uses 42 px instead of the previous 84 px. The last complete build passed in 9m 51s; the source-stamped release rebuild passed in 2m 02s. The final app suite passed 291 cases with the same ten documented opt-in skips. This adds the twelfth distinct automated Android negative control; no new review stage was started for this small final correction under the owner's stop rule.

One production quick-start job used the exact shared YouTube source and completed from captions in seven seconds. The input was cleared, the result origin and channel were visible, and all recorded times persisted after force-stop/restart: resolve 5s, retrieve captions 1s, process result 3ms, persist 13ms. No production STT credential was configured for this caption-only outcome; technical details correctly showed no configured credential. A second manual export produced `_1.md` without changing the first file; the new job's automatic export produced `_2.md`.

Final publication, exact APK, provider request counts, test totals, upgrade chain, scope limits and cleanup receipts are summarized in the [structured release evidence](2026-10-04-release-0.4.2-evidence.json).

## Cleanup and retention

Removed `app/build` (1,358,560,143 bytes), `extractor/build` (605,594,031), `core/build` (10,859,789), `.local-tools/workflow-app-control` (475,028,469), and the old `.local-tools/releases/SourceScribe-0.4.1.apk` (147,040,589), plus 22 temporary helper/patch/fixture files. Total: 2,597,271,399 regular-file bytes across 27 paths. The structured receipt lists every path and size and verifies absence. Superseded unpublished candidates were replaced separately; their replacement sizes are not counted as extra reclaimed bytes.

Only the canonical signed 0.4.2 APK remains locally. Own debug/test packages and three device UI dumps were removed; the agent emulator was stopped, and the owner's emulator was untouched. The signing key, SDK/dependency caches and raw test receipts remain. Upgrade10 retains the final production app, two real caption jobs, the persisted folder grant and three tiny caption exports (10,163 bytes) as a concrete future upgrade/export baseline; the empty long-name folder fixture was removed.

Independent public [GitHub Actions run 37180877887](https://github.com/qwertz92/SourceScribe/actions/runs/37180877887) also passed on the exact released source `df32dede854355ac38f933c8cdb0a266c3ba0657`. Final repository checks read 266 files, found zero unreadable files, verified six executable scripts, and passed the checker self-test.
