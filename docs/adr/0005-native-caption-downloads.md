# ADR 0005: Fetch Subtitles Through the Verified Extractor

September 7, 2026. A real Android metadata run for `jNQXAC9IVRw`
returned both direct `api/timedtext` URLs and HLS subtitles via
`manifest.googlevideo.com/api/manifest/hls_timedtext_playlist/`. As a
result, the previous parser rejected even the audio resolution.

The existing yt-dlp downloader handles both direct and segmented subtitles,
so SourceScribe needs no second HLS parser. Fixed arguments lock out audio
downloads, plugins, remote components, automatic updates, and shell hooks.
Source, selected language, generation, and format are all bound; private
output paths, file count, bytes, and runtime stay bounded. The video
identifier reported back must match the confirmed source before the
subtitles are processed. Under this minimal download recipe, that marker
confirms the recipe that ran, not independently the identity of the server
content. The mapping comes from the metadata resolved earlier over HTTPS;
opaque HLS IDs provide no additional video-ID check.

Only the specific YouTube HLS subtitle endpoint extends the metadata
allowlist. Uploader and auto tracks are chosen through separate download
flags. A track that's no longer available must never be replaced by a
different language or generation. HLS assembly is explicitly noted in the
provenance. The optional raw output is the extractor's output format before
the parser and SourceScribe normalization; for HLS, that's the VTT file the
extractor assembled — not a claim of unmodified individual network fragments.

The implementation and its Android/fixture evidence are recorded in the test
report.

## Binding Without Re-Resolving the Source

The download gets a private, minimal file via `--load-info-json`, carrying
the video ID, a constant title, and exactly the verified URL/language/format
combination. It deliberately omits `webpage_url`, media formats, and other
tracks: with `webpage_url` present, yt-dlp could re-extract on its own after
a download error. The fetch also gets no second URL argument. The
combination `--ignore-no-formats-error --skip-download` allows this
subtitle-only recipe. Direct tracks use HTTPS; the allowed HLS endpoint
explicitly uses `m3u8_native`. Both output templates sit in the same
bounded, private UUID directory. An existing `v` query argument must
uniquely match the confirmed video ID. Query names are decoded before
validation; duplicate `v`/`lang`/`tlang` parameters are rejected. The
effective language (`tlang`, else `lang`) must match the selected track,
including the original-track suffix `-orig`. The URL read in therefore stays
the URL fetched; an expired URL produces a visible error, never a silent
track change.
