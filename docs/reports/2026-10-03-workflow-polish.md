# History and source workflow improvements

Date: 2026-10-03. Baseline: `490baab` on `main`, published app version 0.4.1.
This report is in progress. Implementation is not yet a published update.

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

Core: `234` tests, zero failures/skips. Repository hygiene: `264` files read, six executable scripts checked,
zero unreadable files; checker self-test passed. On 2026-10-04 the corrected nine-task build passed in 16m 49s:
debug/test/release APKs built and all four strict lint XML reports contain zero issues. The first complete app device run executed 297 cases: 283 passed, four new UI automation cases failed,
and ten documented opt-in cases were not enabled. None of the four failures is counted as passing; they concern
expert collapse/input actions and contextual-help navigation. That failed run is retained separately. The corrected complete app rerun passed 287 tests, zero failures;
ten opt-in cases remain separate. The complete extractor run passed all 55 tests with every update/live flag enabled,
zero failures/skips. Four separate process/permission recovery stages and the real engine-pinning case each passed
with no skips. Offline restart, metered wait and unmetered resume each passed. The app automatically acquired
a process after Wi-Fi returned, before resume instrumentation started; original network settings were restored.
Screenshots, provider opt-ins, final dependency verification, upgrade and publication remain pending.

On 2026-10-04 an isolated negative-control copy restored the old 40-byte filename stem cap and disabled all upload timing
callbacks. The complete `234`-test core suite failed exactly four relevant tests: readable long titles, long-title budgeting,
upload timing excluding JSON/server wait, and failed-body timing preserving submission uncertainty. The unmodified sources
remained green. The controlled copy was then removed: `.local-tools/workflow-core-negative`, 14,749,542 regular-file bytes;
failure XML and control descriptions remain in ignored `.local-tools/build-reports/2026-10-04-core-negative-control`.

## Independent review in progress

The first bounded native reviews covered export names, history, setup/help, and quick-start/folder integration.
They identified two normal-flow defects: an identical newly shared link could be cleared by an earlier successful start,
and a partly recorded phase lost its phase label. Both were corrected; targeted UI/guard regressions are added.
Minor findings corrected in this round include the old 40-byte title cap and changing folder-row heights as labels load.
Setup/help follow-ups cover expert draft-name retention, readable context status at large fonts, and excessive overlay height.
The focused setup follow-up found no remaining demonstrated defect. A proposed size issue was withdrawn:
each overlay has a fixed topic while open; separate topics need not all reserve the longest topic's height.
Actual native-review runtime metadata was checked: all five reviewers ran `gpt-6-luna`, four at `xhigh` and the timing reviewer
at `max`; IDs and routes are in ignored `.local-tools/build-reports/2026-10-03-workflow-review-runtime.json`.
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

Release dependency check found KSP `2.3.12` newer than the pinned `2.3.11`; the upgrade awaits compilation/runtime
verification after this feature slice. Evidence: [Maven metadata](https://repo.maven.apache.org/maven2/com/google/devtools/ksp/symbol-processing-gradle-plugin/maven-metadata.xml),
[official release notes](https://github.com/google/ksp/releases/tag/2.3.12). Other catalogue stable pins matched their queried
authoritative repositories. OSV had no matches for the queried catalogue/BOM versions; transitive dependencies were not audited.
The measured Gradle 9.8.0 warning incompatibility remains documented in BUILD; the wrapper is still 9.7.1.

On 2026-10-04 the UI failures were traced separately: HelpScreen intentionally scrolls to the focused topic,
so its search field was not a valid visible postcondition. Three action failures involved stale Compose virtual
nodes; both old and freshly queried cached nodes returned `refresh=false`. The tests now clear the UiAutomation
cache on API 34+, reacquire valid nodes, and assert the actual full-help destination. Seven targeted UI tests pass,
zero failures/skips. Both subsequent test builds passed strict debug lint (zero issues). Raw failed diagnostics,
positive UI output, and the valid expert control remain under `.local-tools/build-reports/2026-10-04-*`.
