# Roadmap and release stages

## Principle

A complete personal v1 is the goal. The ordering minimizes integration risk; it drops none of the core functionality. Build one real vertical slice first, then expand — no precomputed time promises. Every phase closes with integrated code, real tests, a short decision log, and an updated STATUS.

| Phase | Content | Evidence of completion |
|---|---|---|
| P0 — Feasibility | Workspace/toolchain/ADB, the Android extractor including runtime/EJS/FFmpeg, ABI/16 KB, update trust, the scheduling decision | Real metadata, caption, audio, native, and update/rollback checks; open items visible |
| P1 — Real vertical slice | Share/paste → YouTube only → correct caption provenance → internal storage → MD export → viewer/history | APK on an ADB device; a real source plus deterministic fixtures; a forged source ID rejected |
| P2 — First STT pipeline | Groq as the first, easily verified synchronous adapter; local file + YouTube audio; size limits/chunking; credentials; fallback mode | A real provider run only with explicit test approval; every failure/submission case first covered by fixtures |
| P3 — Full acquisition | AssemblyAI async and OpenAI; BOTH, all four modes, track selection, language/options, presets | Contract tests for every adapter; partial failures handled cleanly; live evidence per approved provider |
| P4 — Reliability | Queue/parallelism, process recovery, quotas, export repair, cost warnings, safe engine update + rollback | Lifecycle, storage, update, and double-billing counter-tests passed |
| P5 — Usability and delivery | Viewer/search, more formats, settings, diagnostics, accessibility, release signing/CI/docs | German UI verified on a real ADB system; every required format and share flow demonstrated |
| P6 — Acceptance | Integrated regression, independent adversarial reviews, release verification | `TEST_PLAN.md` satisfied; no open acceptance violations; clear remaining limits and verification evidence |

The first provider implemented is not a permanent product default. If a Groq test key is missing, continue with fixtures and use a different, explicitly approved provider for the first live verification. All three adapters stay in full scope.

## P0 work order

Compare at most a small, meaningful set of maintained Android integration candidates against each other — no endless library search. Examine at least the obvious yt-dlp Android wrapper, and only pursue an alternative or a custom runtime integration for a real technical reason. Document actual artifact content, not just README promises.

Evaluate the separate component-update path early enough that an unresolvable trust or runtime conflict doesn't surface only after the UI is finished. If a candidate fails, check a controlled alternative. If hot updates stay blocked, document the reason and the bundled-APK fallback path, but do not mark full v1 acceptance as met. No server of our own as a silent architecture change.

## Release labels

**Development stage:** A scaffold or a not-yet-fully-verified implementation. No promise of everyday usability.

**Personal preview:** The core path is verified on the provided ADB system; missing provider/device/update evidence is listed explicitly. Not a synonym for a finished v1.

**Full v1:** The defined mandatory features and acceptance criteria are met. Live provider verification happens with approved credentials — without them, the code can be complete but not fully live-verified.

**Device clearance:** State the specific ABI/API/page-size profile actually tested. An x86_64 emulator test is not evidence for every ARM64 device. If no physical device was available, say so clearly, and still run the ARM64 build and the static native checks.

## Afterward — not to inflate into a prerequisite

Priority after v1: a meaningful transcript diff, additional display/filter options, and personal preset improvements. Later, if a concrete need arises: RSS/further sources, local ASR providers, another client, or an optional personal backend service. No numeric "accuracy score" without reference data, no automatic merging of two transcripts.

## Agent split

One integrator owns product contracts, the data model, and the scheduler. Independent packages for the caption parser/exporter, individual provider adapters, and the UI can be built in parallel once interfaces are stable. Schedule read-only security and lifecycle reviews independent of the author. When an emulator is shared, serialize device/test slots: multiple agents must not stop each other's app runs, delete each other's files, or draw on the same API budget in an uncoordinated way.

## Remaining work after preview 0.2.0-preview.1

Known issues are listed by priority in [BUGS](BUGS.md), details in [DEFECTS](DEFECTS.md), and the starting point with closing evidence in [NEXT_STEPS](NEXT_STEPS.md). The personal signing key is in place, and all but three points of the 10 September feedback are done. Live providers, a physical ARM64 device, and TalkBack remain explicitly open. The review loop is paused after round 23; what gets fixed next is for the owner to choose from BUGS, after testing the preview.
