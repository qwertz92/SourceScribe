# WebP 16-KiB rebuild

As of September 7, 2026. This report covers only the targeted rebuild of the five WebP libraries from the FFmpeg
nested artifacts. Gradle, app code, FFmpeg, and device execution were not part of this task.

## Result

For `arm64-v8a` and `x86_64`, these five shared objects were built:

```text
libsharpyuv.so
libwebp.so
libwebpmux.so
libwebpdemux.so
libwebpdecoder.so
```

Every `PT_LOAD` segment is aligned to `0x4000` (16KiB). SONAMEs and `DT_NEEDED` match the previously examined nested
libraries; the new output has no `libdl.so` and no RPATH/RUNPATH. Files were strip-unneeded and copied to
`extractor/src/main/assets/webp/`.

## Provenance and toolchain

Source archive, from the pinned upstream tag:
<https://github.com/webmproject/libwebp/archive/refs/tags/v1.6.0-rc1.tar.gz>

| Artifact | Evidence |
|---|---|
| WebP archive | SHA-256 `a8822fbd36e43fa1e5a83a7104d86c5be8692cee1e323d57030b5562ef884a8a` |
| Termux recipe | read from `/tmp/sourcescribe-p0-research/termux-libwebp-build-b279d510.sh` before the host restart; package version `1.6.0-rc1`, license BSD 3-Clause |
| Android NDK | r28c, `Pkg.Revision = 28.2.13676358`; official archive SHA-1 `a7b54a5de87fecd125a17d54f73c446199e72a64`, local SHA-256 `dfb20d396df28ca02a8c708314b814a4d961dc9074f9a161932746f815aa552f` |
| CMake | 3.31.6, official SHA-1 `a54551dabd5daf8cc99f7f51769fd00ca759be0a`, local SHA-256 `ce136bb4b02580b36e53d9ccfe5275069655e6ef7d46d6fe2fcf88cfdf8fb761` |
| Ninja | 1.12.1, from the verified CMake package |

The archive is kept at `.local-tools/downloads/libwebp-v1.6.0-rc1.tar.gz` for reproduction; the build script does not
auto-fetch and aborts on a missing or wrong SHA-256. The `1.6.0-rc1` version is provenance from the tag/archive name
and the recipe; the source tree's internal `configure.ac` says `1.6.0`, which is not independent proof of archive
origin.

## Reproducible build

Full script: [tools/build-webp-android.sh](../../tools/build-webp-android.sh). The successful run used one Ninja
worker:

```bash
WEBP_JOBS=1 timeout 1800s ./tools/build-webp-android.sh
```

The script checks archive SHA-256, NDK revision, and CMake/Ninja, extracts the source to `.local-tools/webp-out`,
builds each ABI serially, strips the files, and checks ELF machine, SONAME, sorted `DT_NEEDED`, absence of
RPATH/RUNPATH, at least two `PT_LOAD` segments at `0x4000`, and no self-defined symbol-version definitions.

The NDK Clang wrapper targets Android/API 29 (`aarch64-linux-android29-clang` / `x86_64-linux-android29-clang`).
CMake is deliberately given `CMAKE_SYSTEM_NAME=Linux` so WebP's CMakeLists does not take its Android branch, which
would have pulled in the outdated NDK `cpufeatures` library and added `DT_NEEDED libdl.so` to all five `.so` files.
The compiler still targets Android and defines `__ANDROID__`; only CMake's own platform detection is neutralized.

Linker flags used:

```text
-Wl,--as-needed
-Wl,-z,max-page-size=16384
-Wl,-z,common-page-size=16384
```

Also set: `BUILD_SHARED_LIBS=ON`, `WEBP_ENABLE_SIMD=ON`, `WEBP_USE_THREAD=ON`, `WEBP_NEAR_LOSSLESS=ON`,
`WEBP_ENABLE_SWAP_16BIT_CSP=ON`. All command-line tools, extras, image-codec probes, and fuzz targets are disabled;
only `WEBP_BUILD_LIBWEBPMUX=ON` stays on besides the five libraries. Versioned SONAMEs and the CMake install RPATH
are disabled, since the app expects the unversioned names from the existing FFmpeg package.

## ABI evidence

SHA-256 of the files actually in the assets:

| Library | arm64-v8a | x86_64 |
|---|---|---|
| `libsharpyuv.so` | `dfdafcf6cffc7ae1747ffa4165dd08849d62ca1f6302de10becb66ba9ac98a24` | `42545d124de709cd9993daccd347536f49f7034db420a9659bb9b569ce59bea4` |
| `libwebp.so` | `ddbcea8e5049dbcc8d530d01dcf001601f47f83476cc850743265ad170ecf307` | `e03a6dd38a80abb8edf5666417fbc1a79f67cbf346a83af3d1dd42244296b91d` |
| `libwebpmux.so` | `1212479395c0f3479d35d094e386f17c9428b0cc3b5c2f229259caf01e107b7a` | `70dbfdc81c9c69065af334e495fd0c725401cf3f58969591c8e7d4a01ba7db82` |
| `libwebpdemux.so` | `2c27491bbd65b2db4c0cae73259120f3039e70b4d22bce66afa3532e05787d6d` | `928fd701336059e18c873f8bc6c1860a1d35a8f11b8270eacc43b2e06c2b36eb` |
| `libwebpdecoder.so` | `cb8e2dc6a50fe4d6f2f639374705c8c33d635692e817386bdc74a803d5a90055` | `547fce6a9ab4cc93858cd703df8f09c12fe8781e9cbc48586ac0d1615e2974f8` |

Expected dynamic dependencies:

| Library | SONAME | `DT_NEEDED` |
|---|---|---|
| `libsharpyuv.so` | `libsharpyuv.so` | `libc.so`, `libm.so` |
| `libwebp.so` | `libwebp.so` | `libc.so`, `libm.so`, `libsharpyuv.so` |
| `libwebpmux.so` | `libwebpmux.so` | `libc.so`, `libwebp.so` |
| `libwebpdemux.so` | `libwebpdemux.so` | `libc.so`, `libwebp.so` |
| `libwebpdecoder.so` | `libwebpdecoder.so` | `libc.so` |

Before the host restart, the defined global dynsym exports were compared against the nested references then present
at `/tmp/sourcescribe-p0-research/nested-ffmpeg-{arm64,x86_64}/usr/lib`. Counts matched for both ABIs (`sharpyuv 7`,
`webp 86`, `webpmux 27`, `webpdemux 20`, `webpdecoder 46`); `diff` reported no name differences. The reference files
no longer existed after the restart. The Android NDK version entries `LIBC` in `.gnu.version_r` were preserved; the
new `.so` files define no symbol versions of their own.

## License and files

The full upstream license is at `extractor/src/main/assets/webp/COPYING` (BSD 3-Clause, 1,496 bytes, SHA-256
`5aec868f669e384a22372a4e8a1a6cd7d44c64cd451f960ca69cc170d1e13acf`). The ten `.so` files sit under the two ABI
subdirectories in the same asset folder, copied only after the build and ELF check passed; staged and asset files
are byte-identical.

## Checks, errors, and limits

- `bash -n tools/build-webp-android.sh`: PASS.
- `./tools/build-webp-android.sh --help`: PASS.
- A full build run with `WEBP_JOBS=1`: PASS for both ABIs and all ten `.so` files.
- `cmp` of staged vs. asset files: PASS for all ten.
- `llvm-readelf -lW` on all assets: `PT_LOAD p_align=0x4000` only.
- Gradle, ADB, emulator, and physical-device tests: NOT_RUN; left to the integrator.

An initial Android CMake run enabled `cpufeatures` and `libdl.so`; plain `-DANDROID=OFF` was not enough because of
the NDK toolchain. The platform-neutral CMake configuration with the Android Clang compiler fixed exactly this ABI
difference. The `/tmp` working directory was lost on the host restart; the source archive had to be re-fetched from
the fixed URL and re-verified against the unchanged SHA-256.

Still open: the real Android loader check in the full FFmpeg/extractor path. That has to happen separately on the
API-37 emulator; this targeted rebuild proves 16-KiB ELF alignment and the examined ABI compatibility, not the
already-integrated runtime activation.
