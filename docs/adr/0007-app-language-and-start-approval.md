# ADR 0007 — App Language and Authorization Through Job Start

Date: September 8, 2026. Status: implemented and functionally verified; TalkBack device acceptance is separately blocked.

The user wants an explicit German/English app language choice and no
separate, general audio-submission toggle. The confirmed provider choice and
starting the concrete job together constitute authorization. Modes,
provider/credential binding, and budget remain mandatory, unchanged.

For this, `startPreviews` creates immutable configurations only at start
time, with an upload authorization bound to the provider, region, and the
existing credential reference. The preview checks that same resulting
configuration but mutates neither the draft nor stored jobs. Without a
matching key, caption-first stays possible; an STT branch is never silently
authorized as a result. The coordinator and provider adapters keep their
independent security checks. Global defaults, presets, other sources, and
earlier jobs never inherit authorization.

The app language uses AndroidX `AppCompatDelegate.setApplicationLocales`
with `AppCompatActivity`, standard Android resources, and `autoStoreLocales`
for Android 12 and below. No second settings copy in Room/DataStore.
AppCompat 1.8.0 was verified against current release notes and Google Maven
metadata. On Android 13+, the same choice is visible in system settings.
Language settings are independent of ASR language and job configuration.
Non-Activity text uses the localized `ContextCompat` context.

Sources: [Android per-app language preferences](https://developer.android.com/guide/topics/resources/app-languages?hl=en),
[AppCompat 1.8.0](https://developer.android.com/jetpack/androidx/releases/appcompat?hl=en).
On older Android versions, AndroidX manages a small synchronous locale file;
this documented platform solution replaces the need for custom startup
persistence logic.

Acceptance: German/English including a process restart, stable preview/job
data across a language switch, no unsolicited submission, start
authorization limited to the chosen configuration, theme choice still
independent, screenshots in both languages and at 200% font scale. This
decision alone does not yet constitute a test PASS.
