# Build and personal release

SourceScribe builds with Java 17, the checked-in Gradle Wrapper 9.7.1, AGP 9.4.0, and compile/target SDK 37. The Android modules are `:app` and `:extractor`; `:core` is a JVM module. CI installs Build Tools 37.0.0 and 36.0.0 so both are available for Android and native checks.

## Fresh clone under Linux or WSL

Java 17, Python 3, Git, Bash, `timeout` (coreutils), and `flock` (util-linux) must be present. Windows Android Studio and a Linux SDK are separate installations. For a first clone, start in Linux/WSL from the desired parent folder; with an existing clone, switch straight to its project folder instead:

```bash
timeout 120 git clone https://github.com/qwertz92/SourceScribe.git
cd SourceScribe
java -version
export ANDROID_HOME="$PWD/.local-tools/sdk"
mkdir -p "$ANDROID_HOME"
```

For a fresh SDK folder, unpack the Linux command-line tools from the official [Android download page](https://developer.android.com/studio#command-line-tools-only) so that `cmdline-tools/latest/bin/sdkmanager` ends up under `ANDROID_HOME`. Then review the licenses interactively and install the packages:

```bash
timeout 300 "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses
timeout 900 "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --install \
  'platforms;android-37.0' 'build-tools;37.0.0' 'build-tools;36.0.0' 'platform-tools'
```

An existing SDK can be used instead by pointing `ANDROID_HOME` at it with an absolute path. A local `local.properties` with `sdk.dir=…` may point there instead; that file is ignored by Git and must never commit another machine's path. Keep any other SDK-related variables consistent with it. `JAVA_HOME` may need to point at JDK 17. Downloading the SDK, the wrapper, and Maven dependencies needs network access. Gradle's dependency verification stays on; check newly missing metadata against primary sources first, rather than turning verification off.

As of its 2 September 2026 revision, the [official SDK documentation](https://developer.android.com/tools/sdkmanager) marks `sdkmanager` as deprecated in favor of the Android CLI. This document keeps describing the SDK installation path actually used by the project and CI; migrating to the CLI is not part of this preview. An additional build from a completely fresh clone has not been run yet.

## Local build

Prerequisites are an Android SDK with `platforms;android-37.0`, Build Tools 37.0.0/36.0.0, and a JDK 17. The reproducible local entry point uses a single build worker, a repo-local Gradle cache, and a lock against parallel builds:

```text
timeout 1500 bash tools/build-local.sh :core:test :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease :app:lintDebug :app:lintRelease :extractor:assembleDebugAndroidTest :extractor:lintDebug :extractor:lintRelease
timeout 45 python3 tools/check-repository.py --self-test
timeout 45 python3 tools/check-repository.py
```

The script keeps its data under `.local-tools/` and writes no global Gradle configuration. It builds the debug variant with the app ID `app.sourcescribe.debug` and the release variant with `app.sourcescribe`. This command does not include provider keys, live calls, ADB installs, or instrumented tests. Fixture and Android integration helpers stay confined to `androidTest`.

The same wrapper call may be used for individual tasks, for example `tools/build-local.sh :app:lintDebug`. Concrete local results and any device/provider checks that were not run belong in a dated report under `docs/reports/`; this document itself makes no claim of live verification.

## Device verification, separate from the build

Even when the build is configured only through `local.properties`, the following device commands additionally need `ANDROID_HOME` set to that same SDK — or `ADB` pointed directly at its `adb` executable. Use an already-running test device or emulator. Take the device id from the first command's output; never pick just any device automatically:

```bash
export ADB="$ANDROID_HOME/platform-tools/adb"
timeout 20 "$ADB" devices -l
export SERIAL=emulator-5556 # replace with the device id "adb devices" actually printed
timeout 90 "$ADB" -s "$SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
timeout 60 "$ADB" -s "$SERIAL" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
timeout 60 "$ADB" -s "$SERIAL" install -r extractor/build/outputs/apk/androidTest/debug/extractor-debug-androidTest.apk
timeout 600 "$ADB" -s "$SERIAL" shell am instrument -w -r \
  -e notClass app.sourcescribe.data.UiFixtureTest \
  app.sourcescribe.debug.test/androidx.test.runner.AndroidJUnitRunner
timeout 600 "$ADB" -s "$SERIAL" shell am instrument -w -r \
  app.sourcescribe.extractor.test/androidx.test.runner.AndroidJUnitRunner
```

When using Windows ADB from WSL, point `ADB` at the actual `adb.exe` path and translate every install argument into a Windows path, e.g. `"$(wslpath -w "$PWD/app/build/outputs/apk/debug/app-debug.apk")"`. The shell and instrumentation arguments stay the same. Do not run a second ADB/UI verification stream at the same time. The large UI/process/live-extractor fixtures are opt-in; their arguments and separate stages are in the integration report. A normal instrumented run is therefore not P0 live or provider evidence.

## GitHub Actions

`.github/workflows/android.yml` runs without secrets and without publishing an APK. The actions are pinned to immutable commits, checked against the official release refs on 7 September 2026:

- `actions/checkout` v7.0.1 — [`3d3c42e5aac5ba805825da76410c181273ba90b1`](https://github.com/actions/checkout/releases/tag/v7.0.1)
- `actions/setup-java` v6.0.0 — [`dd06d9cba3e5552c54d9f8ea23572deb30010f7c`](https://github.com/actions/setup-java/releases/tag/v6.0.0)
- `android-actions/setup-android` v4.0.1 — [`40fd30fb8d7440372e1316f5d1809ec01dcd3699`](https://github.com/android-actions/setup-android/releases/tag/v4.0.1)
- `actions/cache` v6.1.0 — [`55cc8345863c7cc4c66a329aec7e433d2d1c52a9`](https://github.com/actions/cache/releases/tag/v6.1.0)

The CI cache covers only the wrapper download and Gradle dependency files under `.ci-gradle/`; build outputs and a global user cache are not stored. The workflow runs JVM tests, the debug and release APKs, both Android test APKs, and full debug/release lint for both Android modules, with `--no-daemon --max-workers=1`.

AVD creation and the emulator share the same explicit `ANDROID_AVD_HOME` under `.ci-android/avd`. The directory is created before the SDK setup step; after `avdmanager create`, the workflow checks the INI file and the registered AVD name. That way, startup does not depend on the two Android tools defaulting to different paths. On failure, the exit code is preserved and the workflow prints the emulator log excerpt and the ADB device list. It also prints error nodes from the JUnit XML reports and the tail of the UTP test logs, so install or runner failures stay diagnosable.

It then starts a time-limited Android 37 emulator using the official image `system-images;android-37.0;google_apis_ps16k;x86_64`, with 4 GiB RAM and two CPU cores. Before `:app:connectedDebugAndroidTest :extractor:connectedDebugAndroidTest`, it checks that the actual page size is 16384. The emulator configuration follows the official [startup options](https://developer.android.com/studio/run/emulator-commandline) and [hardware/graphics acceleration](https://developer.android.com/studio/run/emulator-acceleration) docs; Linux GitHub runners support [Android hardware acceleration](https://docs.github.com/en/actions/reference/runners/github-hosted-runners). Any KVM access grant needed applies only to this short-lived CI runner's own user.

The tests use synthetic data and local HTTPS servers. Network diagnostics may check public DNS/TLS reachability; real STT requests stay excluded. Update fixtures are enabled; real update downloads, live YouTube sources, and the interactive UI/process-restart fixtures need additional explicit test arguments and do not run automatically. This workflow is implemented; a CI run counts as evidence in the test report only once it is recorded there with its run ID.

## Personal release path

The release build first produces an unsigned APK. `tools/sign-release.sh` aligns exactly that file with the official Android Build Tools 37.0.0, signs it with `apksigner`, and verifies the signature. The keystore stays outside the repository; the script refuses a keystore path that is missing, relative, or inside the repository. It neither creates nor distributes a private key.

By default, the script needs no environment variables at all:

```text
tools/sign-release.sh app/build/outputs/apk/release/app-release-unsigned.apk /absolute/path/outside/SourceScribe/sourcescribe-release.apk
```

It signs with the keystore at `~/.local/share/sourcescribe/signing/sourcescribe-release.p12`, using the alias `sourcescribe` — the same key that signed release 0.1.0. A keystore is a password-protected file holding a private signing key together with its certificate; an alias is the name of one key's entry inside that keystore. `apksigner` reads the keystore password — the password that unlocks the keystore file, and by default the key inside it too — from `keystore-password` in the same directory, so the password never appears on a command line or in the environment.

Overrides, if needed: `SOURCESCRIBE_SIGNING_DIR` (a different directory holding both files), `KEYSTORE_PATH` (a different keystore file, still absolute and outside the repository), `KEY_ALIAS` (a different alias), `KEYSTORE_PASSWORD_FILE` (a different password file), or `KEYSTORE_PASSWORD` and `KEY_PASSWORD` set directly in the environment — the script passes these to `apksigner` as `env:` references, so even then the password is never a plain argument.

The script refuses to produce an APK whose certificate SHA-256 is not `19d1da9a8fe704082a531faed8a24d966c485aae581a7076dd4b4f66c11d3881`: Android only installs an update over an existing app when the new APK carries the same signing certificate, so anything signed with a different key would simply fail to install as an update. The output path must be new — the script refuses to overwrite an existing file — and the unsigned input is left untouched. Use this same externally kept release key, with consecutive version codes, for every personal update.

A persistent personal RSA-3072/PKCS12 key was set up for preview 0.1.0-preview.1 at the location above, and its signature was verified. Whether the keystore and its password file are backed up anywhere is not verified; without them no later version installs as an update over an installed one. The APK hash and the public certificate fingerprint are in the [0.1.0 preview report](reports/2026-09-08-preview.md).

### Publishing a release

In WSL on the build machine, from the project folder. `ANDROID_HOME` must point to the Android SDK; on the original machine that is `.local-tools/sdk` inside the project.

```bash
export ANDROID_HOME="$PWD/.local-tools/sdk"
tools/sign-release.sh .local-tools/releases/SourceScribe-<version>-<commit>-unsigned.apk .local-tools/releases/SourceScribe-<version>.apk
gh release upload v<version> .local-tools/releases/SourceScribe-<version>.apk
```

`<version>` is the app version, for example `0.3.0`, and `<commit>` the short commit the release build ran at. Signing went correctly if the script ends with `signed APK:` and `certificate SHA-256: 19d1da9a8fe704082a531faed8a24d966c485aae581a7076dd4b4f66c11d3881`; otherwise it stops with an error and writes no APK. After the upload, compare the SHA-256 digest GitHub shows for the asset with `sha256sum` of the local file.

Preview 0.2.0-preview.1 was signed this way on 14 September 2026, after its tag, and its APK attached to the release: 146,688,333 bytes, SHA-256 `ea8bf17fa1262c5d4869ad7a5db9f6183b54746714f82ac46c7c031c449f971b`. In the same check the script refused an APK signed with a throwaway key.
