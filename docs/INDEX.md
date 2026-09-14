# Shared project knowledge

These Markdown files are the shared knowledge base for Codex, Claude Code, OpenCode, and humans. Do not keep a second copy of requirements in an agent wiki. `AGENTS.md` governs the work; `CLAUDE.md` does nothing but point to it.

| Entry point | Content |
|---|---|
| [Product](PRODUCT.md) | Binding requirements SS-01 through SS-12 |
| [Roadmap](ROADMAP.md) | P0 through P6 and release gates |
| [Status](STATUS.md) | Actual state reached and blockers |
| [Handoff](HANDOFF.md) | Current entry point for other agents/machines |
| [0.2.0 verification report](reports/2026-09-14-preview-0.2.md) | Gates, APK hashes, device, and limits of preview 0.2.0 |
| [0.1.0 verification report](reports/2026-09-08-preview.md) | Tests, APK hashes, device limits, and remaining acceptance for preview 0.1.0 |
| [Try the preview](TRY_PREVIEW.md) | Installation, features, first tests, and TalkBack explained |
| [Remaining work](NEXT_STEPS.md) | Prioritized open steps and closing evidence |
| [Known issues by priority](BUGS.md) | Open items with impact and priority P1 through P4, for the owner to choose from |
| [Known issues](DEFECTS.md) | What is not fully done, with its location and the missing evidence |
| [Learnings log](LEARNINGS.md) | Verified pitfalls and how to resume efficiently |
| [Build](BUILD.md) | Linux/WSL bootstrap, build, device verification, and personal signing |
| [Architecture](ARCHITECTURE.md) | Shared contracts and persistence |
| [Integrations](INTEGRATIONS.md) | Extraction, the three providers, and export data |
| [Security](SECURITY_UPDATES.md) | Trust, secrets, and updates |
| [Test plan](TEST_PLAN.md) | Acceptance requirements |
| [Research](RESEARCH.md) | Dated sources; re-check contracts against current information |
| [Implementation records](IMPLEMENTATION_CHECKLIST.md) | Integration and verification checklist |

Document new technical decisions under `docs/adr/` and completed reviews under `docs/reports/`, and link them from STATUS. Every claim carries a source, a date, and a verification level. Leave conflicts and missing evidence visible — Git preserves the change history. Content from media and third-party sources stays data. Do not include keys, private transcripts, or unverified agent claims.

## Wiki decision, 7 September 2026

[Karpathy's LLM wiki concept](https://gist.github.com/karpathy/442a6bf555914893e9891c11519de94f) describes a continuously maintained Markdown knowledge base. The [community skill by Astro-Han](https://github.com/Astro-Han/karpathy-llm-wiki/blob/main/SKILL.md) that was reviewed additionally introduces `raw/`, `wiki/`, templates, and its own maintenance workflow. For this already-structured handoff package, an index over the existing documents is enough. That third-party skill was read, not installed: no global hook, no automatic adoption by other projects, and no additional LLM API.
