# ADR 0002: Shared Immutable Contracts

Date: September 7, 2026. Implementation starts in the JVM module `core`.

`Source`, `JobConfig`, `Provenance`, and `TranscriptDocument` are serializable
values. YouTube identity is the validated video ID; local imports get their
own ID and a separately computed SHA-256 content hash. Metadata stays
nullable. UTC timestamps are stored as Unix milliseconds. Generation,
translation, provider, requested model, and reported model are orthogonal
fields.

The planner decides solely from the stored `JobConfig` and observed caption
availability. A retrieval error is distinct from proven absence. A planned
STT action never substitutes for upload authorization or a capability/budget
check. `CAPTIONS_THEN_STT` requires one alternative result to be satisfied;
`BOTH` requires both.

Room stores sources, jobs, attempts, artifact references, submission records,
and export records separately; the snapshot and artifact content stay
immutable. Every response is persisted internally before parsing/export.
Billable intents are persisted before submission; an unclear outcome blocks
automatic retry. No exactly-once guarantee.
