# Try the personal preview 0.4.2

**Status: candidate in progress; not published.** The direct APK URL is reserved for the eventual signed release and is not downloadable yet: [SourceScribe-0.4.2.apk](https://github.com/qwertz92/SourceScribe/releases/download/v0.4.2/SourceScribe-0.4.2.apk). Do not treat this candidate guide as evidence that the APK or release is available. The implementation and current evidence are tracked in the [workflow report](reports/2026-10-03-workflow-polish.md).

## Install and keep your data

When the APK is published, download it from the link above and install it over SourceScribe 0.4.1. Allow your browser or download app to install apps when Android asks, then confirm the update. Do not uninstall the existing app: uninstalling removes its local history and settings. The in-place upgrade has not yet been verified for 0.4.2; report whether your history and settings remain after installation.

SourceScribe supports Android 10/API 29 or later on ARM64 and x86_64. Existing evidence is from an Android 17/API 37 x86_64 emulator. A physical ARM64 phone and complete TalkBack navigation remain unverified; please include device and Android versions in feedback.

## What to try

1. **Start a source quickly.** Paste or share a YouTube link, leave the current settings as-is or change them, then tap **Start with settings**. This skips the manual preview; source validation, track selection, consent, network policy and local cost limits still apply. Ambiguous track/configuration choices should return you to the normal preview rather than silently changing them.
2. **Check history and provenance.** A completed card should be compact while collapsed; expand it to inspect its result and actions. Each result should identify its actual origin, such as YouTube captions or STT with the provider/model, and show the source channel. A configured fallback must not be presented as the provider that produced a different result.
3. **Check timing.** The total includes queueing and waiting. Open **Timings** to inspect recorded app phases. Older jobs may say **not recorded** because their completion time was never stored. A phase interrupted before its end is marked incomplete. These values measure observed app time; the provider's computation time is unknown.
4. **Export and open a folder.** Export a result and check its readable `Channel - Title.ext` filename. A number is added only when that name already exists in the selected destination. In **More → Settings**, find **Open folders with** and choose **Choose an app each time** or **Use Android’s default app**. From History, use **Open export folders**. Whether a file manager can display the selected document folder depends on the apps installed on the phone.
5. **Open contextual help.** Tap a question-mark help icon. Its explanation should appear as an overlay, with **Open help page** for the full glossary entry.

## Provider evidence and limits

The earlier 0.4.1 live checks recorded three successful Groq transcriptions and two paid AssemblyAI transcription submissions; one AssemblyAI result was partial and the later one succeeded. Those checks used a public 19-second clip and do not prove behavior on your source or account. The new provider run for this candidate is pending, so no new live result is claimed here. OpenAI has no configured key and has no live coverage. See the [provider evidence report](reports/2026-09-30-provider-reliability.md) for exact runs and limits.

For a controlled personal test, add your own provider key under **More → Settings → Credentials**, select a short recording you are allowed to upload, and choose the intended provider and model before starting. The app sends audio directly to that provider using your key; charges follow the provider's account. If a request's outcome is uncertain, do not retry blindly: it may already have been accepted and billed.

## Report a problem

Include app version, device and Android version, source type, acquisition mode, provider/model if used, the steps you took, and what you expected versus saw. A screenshot helps with layout issues. Do not send API keys or private transcripts. Open issues are listed in [BUGS.md](BUGS.md).
