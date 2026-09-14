# ADR 0001: Verify the Android Runtime Practically First

Date: September 7, 2026. Status: P0 under review, not approved.

The candidate under review is `io.github.junkfood02.youtubedl-android` 0.18.1
(`library` and `ffmpeg` from Maven Central). The review is based on the source
JAR and the AAR. The wrapper ships Python, QuickJS, and FFmpeg as Android
components; instrumentation tests must prove the actual versions and execution.

We initially use its bundled initialization for the P0 proof. Our own
execution boundary gets only controlled argument lists and output/time limits.
The wrapper's source contains an unbounded `StringBuffer` for process output
and a shell pipeline for killing the process tree. These execution and update
features do not become part of the product boundary unchecked.

The wrapper's own updater is locked out until a proven trust path exists.
Hot updates need verified package identity/compatibility and separate slots;
the Maven artifact alone proves no safe dynamic update path.

Build candidate: AGP 9.4.0, Gradle 9.6.0, Kotlin 2.4.20, Compose BOM 2026.08.00,
compile/target SDK 37, min SDK 29, JDK 17. The concrete Maven metadata and
[AGP compatibility](https://developer.android.com/build/releases/agp-9-4-0-release-notes)
were retrieved. Only the combined build actually verifies their combination.

P0 device: API 37, x86_64, 16 KB pages. Additionally check ARM64 binaries
statically. No claim of sign-off on a physical ARM64 device.
