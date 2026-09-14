# Implementation and Evidence Checklist

**As of 2026-09-08** (the 0.1.0 preview milestone), with later corrections noted per row. End states per work
package: `complete`, `no_change`, `blocked`, `conflict`. A package stays open until it has evidence. The
evidence labels IMPLEMENTED / TESTED_WITH_FIXTURES / LIVE_VERIFIED are not interchangeable. Current test
counts and CI status are in [STATUS.md](STATUS.md); this table is not the place to look for today's numbers.

| Package | Acceptance / evidence | Owner | State |
|---|---|---|---|
| Wiki | Shared index instead of a duplicated requirements doc; Claude bridge file | Lead agent | complete |
| Environment/Git | Toolchain/network/ADB measured, data preserved, public `main`/commits | Lead agent | complete |
| P0 runtime, x86_64 | Real Android Python/JS/EJS/FFmpeg/TLS/YouTube check (round 48), 16 KB pages | Lead agent | complete |
| P0 ARM64, static | Both ABIs built; current APK incl. 528 inner ELF files checked | Lead agent | complete |
| P0 ARM64, physical | Not run: every device test used an x86_64 emulator | Needs the owner's phone | blocked |
| P0 updates | Signed nightly activated and rolled back; malformed/error fixtures; round 70 pins two running fixture jobs | Lead agent | complete |
| P1 | Real share -> caption -> provenance -> Room -> MD/SAF -> viewer/share | Lead agent | complete |
| P2 implementation/fixtures | Groq, import, prep/chunking, credentials, submission limits | Lead agent | complete |
| P3 implementation/fixtures | AssemblyAI/OpenAI, four modes, tracks/options/presets, partial failures | Lead agent | complete |
| P4 implementation/fixtures | Queue/limits, recovery/uncertainty, export repair, update limits; full run round 81 | Lead agent | complete |
| P5 UI feedback | Field/button spacing, German/English, system/light/dark, start approval without a general upload switch, actual screenshot review | Lead agent / Astra | complete |
| P5 viewer/export | 10,000 segments, search/share/copy selection, no invented timestamp formats; SAF error regressions | Lead agent | complete |
| P5 TalkBack | Semantics and dialog activation checked; full TalkBack traversal not possible with available input automation | Tooling limit | blocked |
| P5 signing | Durable external personal key; round 81 signed, apksigner/zipalign checked and installed. Separate key backup and a future app upgrade still `NOT_RUN` | Lead agent | complete |
| P5 CI | Run 34252821287: build/JVM/lint and emulator boot passed; `connectedDebugAndroidTest` failed, root cause unknown at the time; a new run was paused. The device step later passed in CI run 34865638431 on commit `1fe2dad` — see [STATUS.md](STATUS.md). | Lead agent | complete |
| Last history view, round 80 | Round 81 on the current APK: 180 app tests, an after-screenshot reviewed, static release audit passed | Lead agent | complete |
| P6 local regression/reviews | 103 JVM + 180 executed Android app tests; independent reviews and documented regressions | Lead agent + reviewers | complete |
| P6 full acceptance | Required evidence incomplete because of the external limits below | Lead agent | blocked |
| Live providers | Not run for AssemblyAI, OpenAI or Groq: needs the owner's API keys, which agents may not enter | Needs the owner | blocked |

`complete` on a fixture package is not a live-provider clearance. Phases with a blocked required part are not
fully accepted as a whole phase. Current raw evidence names, APK hashes, and check levels are in the
[0.1.0](reports/2026-09-08-preview.md) and [0.2.0](reports/2026-09-14-preview-0.2.md) preview reports.

## Starting measurement

- Handoff documents only at the start, no existing app code; local git initialized on `main`.
- GitHub access available as qwertz92; `qwertz92/SourceScribe` did not exist yet.
- ADB via the existing Windows SDK install: `emulator-5554`, Android 17/API 37, `sdk_gphone16k_x86_64`, ABI
  list `x86_64,arm64-v8a`, `PAGE_SIZE=16384`. ARM64 in that list is not native physical ARM64 execution.
- WSL: OpenJDK 17.0.20.1; Gradle 9.6.0 started successfully. An isolated local SDK/Gradle cache started under
  `/tmp`; after an out-of-space error it moved to `.local-tools/` on the project drive. No global security
  settings changed.
