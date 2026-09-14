# Try the personal preview 0.2.0-preview.1

App version 0.2.0, version code 2; the second tagged preview, not a full v1. Android API 29 (Android 10) or later, ARM64 or x86_64. So far, only Android 17/API 37/x86_64 with 16 KB pages has actually been verified, in an emulator; a physical ARM64 phone is still outstanding.

## Installing and starting

Commit `1fe2dad` is what was built for this preview; its SHA-256, the gate results, and the device run are in the [verification report](reports/2026-09-14-preview-0.2.md).

On the phone, open the [releases page](https://github.com/qwertz92/SourceScribe/releases) and download the APK for v0.2.0-preview.1. Android will ask permission to install from whichever app you downloaded it with (usually the browser) — allow that; you can revoke the permission again afterward. Confirm the install dialog, then start the app from its icon: a document with an audio waveform, named SourceScribe.

An installed 0.1.0-preview.1 is updated in place, because both are signed with the same key, and its data is kept. On first start after the update, the app migrates its database from schema 3 to 4; a test (`MigrationTest`) covers that migration against a test database, but it has not yet been run on a real phone.

Device tests for this preview ran against the debug build of the same commit — a separate local artifact, not published to GitHub Releases. It needs no signature from you, installs as its own app (`app.sourcescribe.debug`) with its own history alongside the release app, and carries this machine's debug key. Don't uninstall the debug app if you want to keep its earlier test data.

If it starts in English: **More → App language → Deutsch**. **More** also has **System**, **Light**, or **Dark**. App language and transcription language are independent. Without notification permission, the history screen still shows progress; Android's background rules still apply.

## New since 0.1.0

108 commits from 10 to 14 September 2026 separate `v0.1.0-preview.1` from the built commit `1fe2dad`. They act on your feedback from 10 September and include the fixes from 23 rounds of adversarial review. Things worth noticing while testing:

- **Choices and terms explained.** Audio tracks show codec, bitrate, channels, and size instead of format numbers, technical terms have explanations, and a valid maximum audio duration no longer produces the message to enter a value between 1 and 600 minutes (`782aef5`).
- **Warnings in words.** The results view says what is missing from a result instead of printing codes (`029a53f`).
- **Cost line.** It shows the total the budget is checked against, including the surcharges for speaker diarization and technical terms, computed per section; a source that fails the length limit gets no price shown; the line never shifts other content and is not truncated at large font sizes (`c12b7ee`, `f5cc8c5`, `7e0b625`, `579f972`; technical-terms surcharges `41d9eb7`, `d6a773c`, `710c64f`, `8833d07`).
- **Input stays as typed.** Separators in list fields stay put, quickly typed values in job settings no longer get swapped, empty technical terms from a saved list no longer block a start, and whatever blocks the start after a model change stays reachable (`ab9f36e`, `1b2dc45`, `85a2a50`, `0fdf8c0`, `67c2c44`).
- **Less jumping.** The preview's error line, the waiting time, and the byte counter in the history screen reserve their height; the empty address line disappears for imported audio (`e33ac54`, `5da4530`, `c5a6564`).
- **Partial results stay recognizable.** SRT and VTT exports of an incomplete result start with a notice and mark missing sections, without covering up transcribed text; the viewer and the saved entry follow the same rule for "complete" (`c3ae9e5`, `ab34dd1`, `4ddca61`).
- **Engine.** A rollback names the version it activates; the yt-dlp version shipped with an app update becomes active; full engine slots and a corrupted engine slot no longer stall the app; yt-dlp's plugin loader is off in every child process (`23d8a0c`, `8e41bf4`, `23aab99`, `89eecad`, `a1a5af1`, `eaba2d4`).
- **Signature verification for engine updates.** It uses Bouncy Castle 1.86 (`d994c23`, license notes `c523a16`) and rejects a signature file that carries a second block or extra text after the signature, even when it runs on right after the footer with no line break (`8097c08`, `20c0689`).

17 of your 20 pieces of feedback from 10 September are implemented. Open: scrolling in the results view, whose cause was never reproduced; the bottom button's spacing, where what you meant is unclear; and the glossary's claim about the AssemblyAI region, which has not been checked against the provider's documentation. Details are in [DEFECTS.md](DEFECTS.md) under "Feedback from the 10 September 2026 test."

On startup, the app checks its jobs, credentials, and bundled engine. While the thin progress bar at the top is running, checking a source, importing an audio file, and starting a job are all locked, and so are language, provider, and key in settings. In CI, this took longer than 25 seconds on a freshly installed emulator ([DEFECTS](DEFECTS.md), item 57).

## First test without an STT provider

1. Under **New source → Acquisition**, choose **YouTube only**.
2. Enter a public YouTube single-video link, or share it into SourceScribe from YouTube. Choose **Check source** and wait for it to resolve.
3. Check the title/source and the **caption track**, then choose **Confirm and start**.
4. Under **History → Open result**, review the provenance and the text. If captions are missing, this mode reports the error without calling any STT provider.
5. Search inside the viewer; under **Actions**, copy the text, share it as a file, or export it to a folder of your choice. Without a chosen export folder, the transcript stays available internally — export can still happen later without transcribing again.

## What's included

| Area | Capabilities |
|---|---|
| Sources | Explicit YouTube single videos via share/paste, multiple links with one shared confirmation, or importing a local audio file. Whole videos only; no automatic clip selection from URL timestamps. |
| Four modes | **YouTube only**, **YouTube, then STT**, **STT only**, **Both**. Both produces two separate results; a failing branch does not delete the successful one. |
| Providers | AssemblyAI (US/EU), OpenAI, and Groq. Store your own keys securely; choose the model and its model-specific options. Live transcription has not yet been verified by development agents. |
| Advanced options | Caption languages/types, allowing translation, a separate fallback for retrieval errors, STT language, supported timing/speaker data, and context terms; explicit audio/caption track selection. |
| Defaults | Global settings, named presets, and overrides before a job starts. Jobs already running keep their saved configuration snapshot. |
| Limits | Audio duration and local budget, network policy, 1-4 parallel jobs, storage limit, and audio retention. Budgets are local safety limits, not binding provider billing. |
| History | Search, phase/result/export shown separately, cancel, re-run, catch up on missing work where it's safe to do so, switch to another provider, delete an entry. Remote deletion on AssemblyAI when a matching remote ID exists. |
| Results | Provenance, source, language, model/timing/speaker data where available, warnings, search even in long transcripts, copy, and Android file sharing. |
| Export | Markdown, TXT, JSON; SRT/VTT only with suitable timing data. If a result is not confirmed complete, the file starts with a notice cue, and untranscribed sections appear as their own cues. Raw captions where available. A failed export never triggers a new STT request. |
| Maintenance | Redacted diagnostics, verified signed yt-dlp/EJS updates, and rollback. The Python/JS runtime and FFmpeg need APK updates; running jobs keep their engine. |

For your own STT test, first set up provider/region and the API key under **More → Credentials**. Then choose a short recording of your own via **Import audio file**, check the model and options, and start deliberately. That avoids YouTube as an extra source of failure. Test YouTube audio and Both afterward. An uncertain submission status means: possibly already accepted and billed by the provider — do not just restart blindly. A remote job you cancelled locally can keep running at the provider.

## TalkBack and known limits

TalkBack is Google's Android screen reader: it reads interface elements aloud and lets you operate the phone without looking at the screen. SourceScribe does not install it. The app provides accessible labels and control roles. The earlier unexpected speech output came from accessibility tests deliberately enabled in the emulator; that service is off now. You don't need to turn it on unless you want to. [Official explanation](https://support.google.com/accessibility/android/answer/6283677?hl=de).

Still open: real transcription with all three providers, a physical ARM64 device, full TalkBack operation, and signature verification for engine updates on Android versions before API 37 ([DEFECTS](DEFECTS.md), item 55). The core caption path was actually verified for 0.1.0; for 0.2.0 the live tests did not run, and provider failures are still checked in a controlled way with fixtures. No summarization API, no web frontend, no backend service of its own, and no automatic filing into a specific ChatGPT project.

## Useful feedback

Please state device/Android version, app version, mode/provider/model, exact steps, and expected versus actual result. A screenshot often shows spacing and alignment better than a description. Don't send API keys; redact private text where needed. For errors, the time it happened and the technical status shown both help. Known remaining work is in [NEXT_STEPS](NEXT_STEPS.md); known issues by priority are in [BUGS](BUGS.md).

The app's adaptive icon is rendered natively from vector paths; here is a 256-pixel preview with the rounded launcher mask, not a newly generated AI image:

![Document with an audio waveform on a dark green background](reports/screenshots/icon-preview.png)
