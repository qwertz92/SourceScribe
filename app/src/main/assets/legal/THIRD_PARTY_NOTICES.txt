# Third-party notices

As of 14 September 2026. This is an inventory of the direct dependencies visible in the SourceScribe source tree, the
bundled extractor artifacts and the WebP libraries built for the app. It is not yet a complete license and
corresponding-source inventory; the open points are listed at the end.

## This project

SourceScribe's own code is licensed under GPL-3.0-only; the full text is in [`LICENSE`](LICENSE). That is not a legal
claim that the bundled components force every line of the app under the GPL.

## Bundled extractor components

| Component | Version and evidence | License and source |
|---|---|---|
| `youtubedl-android` AARs (`library`, `common`, `ffmpeg`) | 0.18.1; local AARs from the Gradle cache, FFmpeg AAR SHA-256 `0a87ffa6cf912b0fe76c1a99b9107f543ee2f247935fae2c71f0822eb7bc5f49` | The POMs name GPL-3.0; [upstream repository at tag 0.18.1](https://github.com/yausername/youtubedl-android/tree/0.18.1), [license referenced by the POM](https://www.gnu.org/licenses/gpl-3.0.en.html) |
| FFmpeg in the FFmpeg AAR | 7.1.1; artifact metadata and build flags `--enable-gpl --enable-version3` | [FFmpeg legal](https://ffmpeg.org/legal.html), [official release archive](https://ffmpeg.org/releases/ffmpeg-7.1.1.tar.xz), expected source SHA-256 `733984395e0dbbe5c046abda2dc49a5544e7e0e1e2366bba849222ae9e3a03b1`; that the AAR was built bit-identically from it is not proven |
| x264, VMAF, SVT-AV1 and other FFmpeg codecs | Touched by the FFmpeg build; source and license mapping per component still open | x264 source hash unresolved; VMAF and SVT-AV1 license inventory incomplete |
| Python runtime | 3.12.11 according to the inspected AAR artifact | [Python license](https://docs.python.org/3/license.html), PSF-2.0; review of the notices bundled in the AAR not complete |
| QuickJS runtime | 2025-04-26 according to the inspected AAR artifact | [QuickJS license and source](https://bellard.org/quickjs/); review of the bundled notices not complete |
| OpenSSL | Version not determined separately in the current AAR review | [OpenSSL license](https://www.openssl.org/source/license.html); no version or source mapping claimed |
| yt-dlp | Zipapp 2026.08.19, SHA-256 `1fa6733c37ea6fb51c99ad8fe785e7b7e5f3246c9b980230329d4fb72ed8d4d6` | [yt-dlp LICENSE](https://github.com/yt-dlp/yt-dlp/blob/master/LICENSE); the upstream Unlicense statement is still to be matched against the zipapp's content |
| yt-dlp-ejs | EJS 0.8.0 in the inspected zipapp | [EJS LICENSE](https://github.com/yt-dlp/ejs/blob/master/LICENSE); the bundled notice is not archived separately |
| Bouncy Castle | `bcpg` 1.86, `bcprov` 1.86, updated on 14 September 2026 from 1.85 and 1.85.2 | [Bouncy Castle license](https://www.bouncycastle.org/licence.html); JVM cryptography library, not a native AAR component |
| WebP | 1.6.0-rc1, ten newly built shared libraries for `arm64-v8a` and `x86_64`; source archive SHA-256 `a8822fbd36e43fa1e5a83a7104d86c5be8692cee1e323d57030b5562ef884a8a` | BSD 3-Clause in [`extractor/src/main/assets/webp/COPYING`](extractor/src/main/assets/webp/COPYING), plus [`AUTHORS`](extractor/src/main/assets/webp/AUTHORS) and [`PATENTS`](extractor/src/main/assets/webp/PATENTS); [upstream tag](https://github.com/webmproject/libwebp/tree/v1.6.0-rc1) |

The FFmpeg AAR contains no file named `LICENSE`, `NOTICE`, `COPYING` or `README`; its POM only names GPL-3.0. The
WebP companion files are therefore versioned separately in the asset directory.

## Direct and transitive JVM and Android dependencies

The versions are in [`gradle/libs.versions.toml`](gradle/libs.versions.toml) and were checked against the resolved POMs
in the local Gradle cache. The AndroidX, Kotlin and kotlinx, OkHttp, Hilt, Room, WorkManager and DataStore components
follow their upstream Apache-2.0 notices. Bouncy Castle (`bcpg` 1.86, `bcprov` 1.86) follows the
[Bouncy Castle license](https://www.bouncycastle.org/licence.html). JUnit 4.13.2 is test-only and follows the
[EPL 1.0](https://www.eclipse.org/legal/epl-v10.html).

The local resolution contained 73 packaged transitive artifacts and 8 external license or source references reachable
from the metadata. These numbers describe the scope of that check; they do not guarantee that every FFmpeg codec or
generator artifact is covered.

## Corresponding source

Every GitHub release of SourceScribe carries the signed APK, and its source code is the release's tag in this
repository. For the GPL-licensed binaries bundled from the youtubedl-android AAR:

- FFmpeg 7.1.1 source: https://ffmpeg.org/releases/ffmpeg-7.1.1.tar.xz
- Build scripts of the bundled FFmpeg, Python and QuickJS binaries: https://github.com/yausername/youtubedl-android/tree/0.18.1

Still open: proof that these sources correspond exactly to the bundled binaries, the x264 source hash, and the VMAF and
SVT-AV1 license inventory.
