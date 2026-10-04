# Try the personal preview 0.4.2

Version 0.4.2, versionCode 5. Signed source `df32dede854355ac38f933c8cdb0a266c3ba0657`; APK SHA-256 `7b1fac241672c7bb16739d440069ea97edeffc6c6e03857e3c250c69d2690c26`; `147200333` bytes; published `2026-10-04T05:57:01Z`. Download the signed [SourceScribe-0.4.2.apk](https://github.com/qwertz92/SourceScribe/releases/download/v0.4.2/SourceScribe-0.4.2.apk). Full test evidence and limits are in the [workflow report](reports/2026-10-03-workflow-polish.md).

## Install and keep your data

Install the APK over SourceScribe 0.4.1. If Android asks, allow your browser or download app to install apps, then confirm the update. Do not uninstall the old app: that removes its local history and settings. The signed 0.4.1-to-0.4.2 in-place upgrade was verified; the existing job, transcript text hash, settings, app ID and app-data inodes remained intact.

SourceScribe supports Android 10/API 29 or later on ARM64 and x86_64. Device evidence is from an Android 17/API 37 x86_64 emulator. A physical ARM64 phone and complete TalkBack navigation remain unverified.

## What to try

1. **Start quickly.** Paste or share a YouTube link and tap **Start with settings**. This skips the manual preview while retaining source validation, track choice, consent, and cost and network limits. An ambiguous source or track returns to the normal preview rather than silently selecting one.
2. **Inspect History.** A completed card is compact while collapsed; expand it to see the result and actions. Each result names its actual origin (YouTube captions or STT with the provider/model) and source channel, even when that differs from the configured fallback. The live caption example exported as `jawed - Me_at_the_zoo.md`.
3. **Check elapsed time.** The total includes queueing and waiting. Open **Timings** for recorded app phases. Older jobs whose completion time was not stored remain **not recorded**; a phase interrupted before completion is marked incomplete. Provider computation time is unknown.
4. **Export and open a folder.** Export names use readable `Channel - Title.ext`; only a destination collision adds a numeric suffix, and an existing file is never overwritten. Tap **More** to open Settings directly. Under **Open folders with**, choose **Choose an app each time** or **Use Android’s default app**. In History tap **Open export folders**. Both preference branches opened `Downloads/SourceScribe-0.4.2-test` in Android DocumentsUI on the emulator. The Settings screen caps a 173-character folder label at one line (212 px before, 53 px after). Only one handler was installed, so routing among several folder apps was not tested.
5. **Use contextual help.** Tap a question-mark icon to see its explanation in an overlay; **Open help page** opens the complete glossary entry.

## Provider evidence

The earlier 0.4.1 live checks recorded three successful Groq transcriptions and two paid AssemblyAI transcript submissions, one partial and one complete. In the 0.4.2 candidate, one live run per provider used the exact public 19-second source `https://www.youtube.com/watch?v=jNQXAC9IVRw`. Groq Turbo succeeded, but Groq returned no model field. AssemblyAI `universal-3-5-pro` succeeded after one audio-upload request, one paid transcript POST and two retrieval GETs. The two transcript results succeeded. OpenAI has no supplied key and no live coverage. These checks do not guarantee behavior on your source or account.

For a personal STT test, tap **More** and open **Credentials**, add your key, choose a short recording you are allowed to upload, and confirm the provider and model before starting. Audio goes directly to that provider using your key; its account determines charges. An uncertain request may already have been accepted and billed, so do not retry blindly.

## Remaining limits

A physical ARM64 phone, complete TalkBack navigation, OpenAI live coverage and the owner’s exact URL/model failure remain unverified. The app did not verify chooser selection when multiple folder apps are installed. With only one folder handler installed, Android’s chooser behavior when several file managers are available also remains unverified. Current open issues are in [BUGS.md](BUGS.md).

When reporting a problem, include app version, device/Android version, source type, acquisition mode, provider/model and exact steps with expected versus actual behavior. A screenshot helps with layout issues. Do not send API keys or private transcripts.
