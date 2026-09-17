# Try the personal preview 0.4.0

App version 0.4.0, version code 3; the third tagged preview, not a full v1. Android API 29 (Android 10) or later,
ARM64 or x86_64. So far, only Android 17/API 37/x86_64 with 16 KB pages has actually been verified, in an emulator;
a physical ARM64 phone is still outstanding.

## Installing and updating

Commit `f7dd7d2` is what was built for this preview; its SHA-256, the gate results, and the device run are in
the [verification report](reports/2026-09-16-preview-0.4.md).

On the phone, open the [releases page](https://github.com/qwertz92/SourceScribe/releases) and download the APK for
v0.4.0. Android will ask permission to install from whichever app you downloaded it with (usually the browser) —
allow that; you can revoke the permission again afterward. Confirm the install dialog, then start the app from its
icon.

Both an installed 0.1.0-preview.1 and an installed 0.2.0-preview.1 update in place, because all three are signed
with the same key, and existing data is kept. Since 0.2.0 the app's database schema is version 4; there has been no
further schema change since, so updating from either previous preview takes the same, already-tested migration
path. Verified on 16 September 2026 on a fresh emulator (Pixel 10, Android 17, the same system image as the test device, started as AVD `Upgrade10` at port 5556 because the usual AVD had no room for a second 147 MB install): a 0.1.0-preview.1 and a 0.2.0-preview.1 install, each with one finished captions job of the 19-second clip, were updated in place to the signed 0.4.0 with `adb install -r`; after the update the job, its stored transcript and the settings were still there and the crash log stayed empty

Device tests for this preview ran against the debug build of the same commit — a separate local artifact, not
published to GitHub Releases. It needs no signature from you, installs as its own app (`app.sourcescribe.debug`)
with its own history alongside the release app, and carries this machine's debug key. Don't uninstall the debug
app if you want to keep its earlier test data.

## What changed since 0.2.0, answering your 16 September findings

Everything below responds to the phone test you ran on 0.2.0-preview.1, in the order you reported it. Numbers in
parentheses are this project's internal item numbers, for cross-referencing against [HISTORY.md](HISTORY.md) and
[BUGS.md](BUGS.md).

1. **Transcription never worked (item 1, the P1).** Every speech-to-text job on a source longer than ten minutes
   stopped with a technical error before it ever reached a provider. Cause: the audio-preparation step's own
   length check compared the encoded chunk's length against the exact same hard limit that chunk's length is
   planned to, and the encoder always produces a few dozen milliseconds more audio than it was asked for, so every
   long chunk tripped over its own limit. Fixed by keeping the *planned* length as the chunk's official length and
   checking the encoder's real output against that, with room for its own overhead.
2. **The length limit (item 2).** The field asking for a maximum audio duration in minutes, defaulting to 60, is
   gone. A job now takes its length from the source itself — yt-dlp's own metadata for a YouTube video, the file
   itself for an imported one — and the preview shows that length next to the price. The only limit left is the
   app's own outer ceiling of ten hours.
3. **No sense of progress (item 3).** While a video's audio downloads and while a chunk uploads, the job's card in
   history now shows a percentage and a rate, updated at most once a second. A percentage only appears where a
   real total byte count is known; YouTube does not always state one, in which case you still see the bytes moved
   and the rate.
4. **Which audio track was actually used (item 4).** A job's details in history now name the exact rendition an
   attempt used: codec, bitrate, size as downloaded, and language.
5. **The recommended audio track (item 5).** The picker used to recommend the smallest file. It now recommends the
   best one instead: Opus before AAC, the highest bitrate within that, skipping dynamic-range-compressed copies
   where a plain one exists. The size of every option stays visible, so you can still pick a smaller one yourself
   on a metered connection.
6. **Jobs on Wi-Fi only (item 6).** A job limited to unmetered connections now says "waiting for an unmetered
   connection" while it waits, instead of looking identical to a job that is about to run, and starts on its own
   once Wi-Fi is back — including after the app was closed.
7. **The provider selector (item 7).** The dropdown for choosing a speech-to-text provider is gone. In its place is
   a plain list, one row per provider, each showing its name, whether a key is stored for it, and the model in
   use.
8. **Expert options that don't apply to the chosen provider (item 8).** An option a provider's model cannot do —
   speaker separation on a model without it, for instance — is now shown greyed out with a short note saying which
   provider would support it, instead of just sitting there as if it worked.
9. **Expert options that don't apply to the chosen mode (item 9).** An option that makes no sense for the
   acquisition mode you picked — "try speech-to-text after a failed caption fetch" when speech-to-text is already
   the only mode, for instance — now disappears instead of standing there doing nothing.
10. **Results hidden below the fold (item 10).** After "Check source" succeeds, the input area collapses into a
    compact card (title, channel, duration, and a "Change" button), the results appear directly under it, and the
    screen scrolls to them once by itself. Picking a track or an option afterward no longer moves the view.
11. **The link field (item 11).** It is now a proper field with the text vertically centred, growing with what you
    paste instead of sitting at the top of a tall, mostly empty box.
12. **The export-format boxes (item 12).** Text/Markdown/… are now chips with real spacing between them, at the
    same touch size as before.
13. **Reusing the same keyterms (item 13).** A list of keyterms can now be saved under a name (for a recurring
    topic, say) and picked again for the next job; hidden for providers that don't support keyterms at all.
14. **Exporting to different folders (item 14).** Each export format can optionally have its own folder — useful
    if you want subtitles to land somewhere different from the transcript itself. After an export, a button offers
    to open the folder it went to.
15. **The job-actions dialog (item 15).** A failed or stopped job's action list used to name actions like "retry
    missing branch" without saying what went wrong, what each action does, or which one to pick. It now leads with
    the error's own sentence, highlights one recommended action, and gives every other action a one-line
    explanation of what it does and when to use it; an action that cannot help in the job's current state is no
    longer offered at all.
16. **An engine re-check that could strand a job (item 16).** If you had activated a downloaded engine and an app
    update's post-update check of it merely timed out — on a slow moment, not a real failure — the engine used to
    be disabled outright, the same as if it had actually failed, and a stuck job's most obvious recovery button
    would then do nothing. A timeout no longer counts as a failure: the engine stays active, and the app checks it
    again on the next start. The engine list in settings now also says which installation is in use and whether it
    passed its check.

One further fix, found while proving the pipeline against a real provider rather than reported by you: Groq names
its detected language as a full English word rather than a two-letter code, and the app used to keep only
two-letter codes, so every Groq transcript carried no language at all. The parser now reads a full name too.

One point from your 10 September test, the bottom action button ("could use more space"), is set aside: on 16 September you could not recall what was meant, so it comes back only if you see it again. Everything that is still open is in [BUGS.md](BUGS.md).

## What to test first

1. **The update itself.** Install 0.4.0 over your existing 0.1.0 or 0.2.0-preview.1 install and confirm your
   history and settings are still there afterward.
2. **A real, short transcription with your own key.** Only Groq has been proven end to end by this project so far,
   once, against a 19-second clip. AssemblyAI and OpenAI have never been tried with a real key at all — your test
   would be the first for either.
3. **The new-source screen**, end to end: the provider list, an expert option that greys out when you switch
   provider or mode, the collapsed source card and the scroll-to-results behavior, the link field, the
   export-format chips, saving and loading a keyterm set.
4. **The actions dialog on a job that failed or was stopped** — does the recommended action match what you'd
   expect, and does each explanation make sense before you press anything.
5. **"Open folder" on a phone**, after an export. This was never run on any device available during development,
   because none of them had a file manager that could handle it; on your phone it may work on the first try, or it
   may show the "no app for this" message, which would itself be useful to know.

## What's included

| Area | Capabilities |
|---|---|
| Sources | Explicit YouTube single videos via share/paste, multiple links with one shared confirmation, or importing a local audio file. Whole videos only; no automatic clip selection from URL timestamps. |
| Four modes | **YouTube only**, **YouTube, then STT**, **STT only**, **Both**. Both produces two separate results; a failing branch does not delete the successful one. |
| Providers | AssemblyAI (US/EU), OpenAI, and Groq, shown as a list with your stored key and model. Real transcription has been verified once, with Groq. |
| Advanced options | Caption languages/types, allowing translation, a separate fallback for retrieval errors, STT language, supported timing/speaker data, and named keyterm sets; explicit audio/caption track selection; every option greyed out or hidden where the provider or mode cannot use it. |
| Defaults | Global settings, named presets, and overrides before a job starts. Jobs already running keep their saved configuration snapshot. |
| Limits | Audio duration taken from the source itself (ten-hour app ceiling), local cost budget, network policy, 1-4 parallel jobs, storage limit, and audio retention. Budgets are local safety limits, not binding provider billing. |
| History | Search, phase/result/export shown separately, live download/upload progress, which audio track was used, cancel, a job-actions dialog that explains itself, catch up on missing work where it's safe to do so, switch to another provider, delete an entry. |
| Results | Provenance, source, language, model/timing/speaker data where available, warnings, search even in long transcripts, copy, and Android file sharing. |
| Export | Markdown, TXT, JSON; SRT/VTT only with suitable timing data; an optional separate folder per format, with a button to open it afterward. If a result is not confirmed complete, the file starts with a notice cue. A failed export never triggers a new STT request. |
| Maintenance | Redacted diagnostics, verified signed yt-dlp/EJS updates, rollback, and a settings list that shows which engine is active and whether it passed its check. |

For your own STT test, first set up provider/region and the API key under **More → Credentials**. Then choose a
short recording of your own via **Import audio file**, check the model and options, and start deliberately. That
avoids YouTube as an extra source of failure. An uncertain submission status means: possibly already accepted and
billed by the provider — do not just restart blindly.

## TalkBack and known limits

TalkBack is Google's Android screen reader. SourceScribe does not install it; the app provides accessible labels
and control roles. Still open: real transcription with AssemblyAI and OpenAI, a physical ARM64 device, full
TalkBack operation, and the signature check for engine updates on Android versions before API 37. The core caption
and one real Groq path have been verified; every other provider path is still checked in a controlled way with
fixtures. No summarization API, no web frontend, no backend service of its own.

## Useful feedback

State device/Android version, app version, mode/provider/model, exact steps, and expected versus actual result. A
screenshot often shows spacing and alignment better than a description. Don't send API keys; redact private text
where needed. Known remaining work is in [BUGS.md](BUGS.md).
