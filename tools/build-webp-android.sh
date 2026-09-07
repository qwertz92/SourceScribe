#!/usr/bin/env bash
set -euo pipefail

# Rebuild only the five WebP shared libraries used by the extractor.
# The default output stays under .local-tools and is ignored by Git.  Passing
# --install-assets copies the already verified files into the source tree.

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
SOURCE_URL="https://github.com/webmproject/libwebp/archive/refs/tags/v1.6.0-rc1.tar.gz"
SOURCE_SHA256="a8822fbd36e43fa1e5a83a7104d86c5be8692cee1e323d57030b5562ef884a8a"
SOURCE_ARCHIVE=${WEBP_SOURCE_ARCHIVE:-"$ROOT_DIR/.local-tools/downloads/libwebp-v1.6.0-rc1.tar.gz"}
NDK_DIR=${ANDROID_NDK_HOME:-"$ROOT_DIR/.local-tools/ndk-r28c/android-ndk-r28c"}
CMAKE_BIN=${CMAKE_BIN:-"$ROOT_DIR/.local-tools/cmake-3.31.6/bin/cmake"}
NINJA_BIN=${NINJA_BIN:-"$ROOT_DIR/.local-tools/cmake-3.31.6/bin/ninja"}
OUT_DIR=${WEBP_OUT_DIR:-"$ROOT_DIR/.local-tools/webp-out"}
JOBS=${WEBP_JOBS:-2}
API_LEVEL=29
INSTALL_ASSETS=0

die() {
  echo "Fehler: $*" >&2
  exit 1
}

[[ "$#" -le 1 ]] || die "zu viele Argumente"
case "${1:-}" in
  "") ;;
  --install-assets) INSTALL_ASSETS=1 ;;
  --help)
    cat <<'EOF'
Verwendung: tools/build-webp-android.sh [--install-assets]

Baut libwebp v1.6.0-rc1 für arm64-v8a und x86_64 unter .local-tools/webp-out.
--install-assets kopiert die verifizierten fünf SOs je ABI und COPYING nach
extractor/src/main/assets/webp/.
EOF
    exit 0
    ;;
  *) die "unbekanntes Argument: $1" ;;
esac

[[ -f "$SOURCE_ARCHIVE" ]] || die "Quellarchiv fehlt: $SOURCE_ARCHIVE (erwartete Quelle: $SOURCE_URL)"
command -v sha256sum >/dev/null || die "sha256sum fehlt"
command -v tar >/dev/null || die "tar fehlt"
[[ -x "$CMAKE_BIN" ]] || die "CMake fehlt oder ist nicht ausführbar: $CMAKE_BIN"
[[ -x "$NINJA_BIN" ]] || die "Ninja fehlt oder ist nicht ausführbar: $NINJA_BIN"
[[ -f "$NDK_DIR/source.properties" ]] || die "NDK source.properties fehlt: $NDK_DIR"
grep -Fxq "Pkg.Revision = 28.2.13676358" "$NDK_DIR/source.properties" \
  || die "unerwartete NDK-Version; erwartet wird r28c (28.2.13676358)"

actual_sha256=$(sha256sum "$SOURCE_ARCHIVE" | cut -d' ' -f1)
[[ "$actual_sha256" == "$SOURCE_SHA256" ]] \
  || die "SHA256 des Quellarchivs stimmt nicht: $actual_sha256"

TOOLCHAIN_BIN="$NDK_DIR/toolchains/llvm/prebuilt/linux-x86_64/bin"
READELF_BIN="$TOOLCHAIN_BIN/llvm-readelf"
STRIP_BIN="$TOOLCHAIN_BIN/llvm-strip"
[[ -x "$READELF_BIN" && -x "$STRIP_BIN" ]] \
  || die "NDK llvm-readelf/llvm-strip fehlen unter $TOOLCHAIN_BIN"

SOURCE_DIR="$OUT_DIR/source-libwebp-1.6.0-rc1"
mkdir -p "$OUT_DIR"
rm -rf "$SOURCE_DIR"
mkdir -p "$SOURCE_DIR"
archive_root=$(tar -tzf "$SOURCE_ARCHIVE" | sed -n '1s@^\([^/]\+\)/.*@\1@p')
[[ "$archive_root" == "libwebp-1.6.0-rc1" ]] \
  || die "unerwarteter Archivwurzelname: $archive_root"
tar -xzf "$SOURCE_ARCHIVE" --strip-components=1 -C "$SOURCE_DIR"
[[ -f "$SOURCE_DIR/CMakeLists.txt" ]] || die "WebP-Quellbaum ist unvollständig"

declare -a LIBRARIES=(libsharpyuv.so libwebp.so libwebpmux.so libwebpdemux.so libwebpdecoder.so)
declare -A EXPECTED_NEEDED=(
  [libsharpyuv.so]='libc.so libm.so'
  [libwebp.so]='libc.so libm.so libsharpyuv.so'
  [libwebpmux.so]='libc.so libwebp.so'
  [libwebpdemux.so]='libc.so libwebp.so'
  [libwebpdecoder.so]='libc.so'
)

verify_so() {
  local so=$1 expected_machine=$2
  local machine soname needed load_count
  [[ -f "$so" ]] || die "erwartete Bibliothek fehlt: $so"
  machine=$("$READELF_BIN" -h "$so" | sed -n 's/^ *Machine: *//p')
  [[ "$machine" == "$expected_machine" ]] \
    || die "unerwartete ELF-Maschine in $so"
  soname=$("$READELF_BIN" -d "$so" | sed -n 's/.*Library soname: \[\(.*\)\].*/\1/p')
  [[ "$soname" == "$(basename "$so")" ]] \
    || die "unerwarteter SONAME in $so: $soname"
  needed=$("$READELF_BIN" -d "$so" | sed -n 's/.*Shared library: \[\(.*\)\].*/\1/p' | sort | paste -sd' ' -)
  [[ "$needed" == "${EXPECTED_NEEDED[$(basename "$so")]}" ]] \
    || die "unerwartete DT_NEEDED in $so: $needed"
  if "$READELF_BIN" -d "$so" | grep -Eq 'RUNPATH|RPATH'; then
    die "RPATH/RUNPATH ist in $so nicht erlaubt"
  fi
  load_count=0
  while IFS= read -r alignment; do
    load_count=$((load_count + 1))
    [[ "$alignment" == "0x4000" ]] \
      || die "PT_LOAD-Ausrichtung in $so ist $alignment statt 0x4000"
  done < <("$READELF_BIN" -lW "$so" | awk '$1 == "LOAD" {print $NF}')
  (( load_count >= 2 )) || die "keine ausreichenden PT_LOAD-Segmente in $so"
  # libc/libm version requirements are supplied by the Android NDK and are
  # retained.  The packaged libraries must not add their own symbol versions.
  if "$READELF_BIN" -V "$so" 2>/dev/null | grep -q 'Version definition section'; then
    die "unerwartete eigene ELF-Symbolversionen in $so"
  fi
}

build_abi() {
  local abi=$1 machine=$2 triple=$3
  local build_dir="$OUT_DIR/build-$abi"
  local install_dir="$build_dir/install"
  local stage_dir="$OUT_DIR/release/$abi"
  local compiler="$TOOLCHAIN_BIN/${triple}${API_LEVEL}-clang"
  local configure_log="$OUT_DIR/configure-$abi.log"
  local build_log="$OUT_DIR/build-$abi.log"
  [[ -x "$compiler" ]] || die "Android-Clang fehlt: $compiler"
  rm -rf "$build_dir" "$stage_dir"
  mkdir -p "$stage_dir"

  # CMAKE_SYSTEM_NAME=Linux is deliberate: the compiler wrapper still targets
  # Android/API 29 and defines __ANDROID__, while this avoids WebP's legacy
  # CMake cpufeatures target, which would add libdl.so to every shared object.
  timeout 600s "$CMAKE_BIN" \
    -S "$SOURCE_DIR" -B "$build_dir" -G Ninja \
    -DCMAKE_MAKE_PROGRAM="$NINJA_BIN" \
    -DCMAKE_SYSTEM_NAME=Linux \
    -DCMAKE_SYSTEM_PROCESSOR="$machine" \
    -DCMAKE_C_COMPILER="$compiler" \
    -DCMAKE_AR="$TOOLCHAIN_BIN/llvm-ar" \
    -DCMAKE_RANLIB="$TOOLCHAIN_BIN/llvm-ranlib" \
    -DCMAKE_STRIP="$STRIP_BIN" \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_INSTALL_PREFIX="$install_dir" \
    -DCMAKE_INSTALL_RPATH= \
    -DCMAKE_PLATFORM_NO_VERSIONED_SONAME=ON \
    -DBUILD_SHARED_LIBS=ON \
    -DWEBP_LINK_STATIC=OFF \
    -DWEBP_ENABLE_SIMD=ON \
    -DWEBP_USE_THREAD=ON \
    -DWEBP_NEAR_LOSSLESS=ON \
    -DWEBP_ENABLE_SWAP_16BIT_CSP=ON \
    -DWEBP_BUILD_ANIM_UTILS=OFF \
    -DWEBP_BUILD_CWEBP=OFF \
    -DWEBP_BUILD_DWEBP=OFF \
    -DWEBP_BUILD_GIF2WEBP=OFF \
    -DWEBP_BUILD_IMG2WEBP=OFF \
    -DWEBP_BUILD_VWEBP=OFF \
    -DWEBP_BUILD_WEBPINFO=OFF \
    -DWEBP_BUILD_WEBPMUX=OFF \
    -DWEBP_BUILD_EXTRAS=OFF \
    -DWEBP_BUILD_FUZZTEST=OFF \
    -DWEBP_BUILD_WEBP_JS=OFF \
    -DWEBP_BUILD_LIBWEBPMUX=ON \
    -DCMAKE_SHARED_LINKER_FLAGS='-Wl,--as-needed -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384' \
    >"$configure_log" 2>&1
  timeout 600s "$NINJA_BIN" -C "$build_dir" -j"$JOBS" >"$build_log" 2>&1
  timeout 120s "$NINJA_BIN" -C "$build_dir" install >>"$build_log" 2>&1

  for library in "${LIBRARIES[@]}"; do
    [[ -f "$install_dir/lib/$library" ]] \
      || die "CMake-Installationsdatei fehlt: $install_dir/lib/$library"
    cp -L "$install_dir/lib/$library" "$stage_dir/$library"
    "$STRIP_BIN" --strip-unneeded "$stage_dir/$library"
    verify_so "$stage_dir/$library" "$machine"
  done
  echo "$abi: fünf verifizierte Bibliotheken unter $stage_dir"
}

build_abi arm64-v8a AArch64 aarch64-linux-android
build_abi x86_64 'Advanced Micro Devices X86-64' x86_64-linux-android

if [[ "$INSTALL_ASSETS" == 1 ]]; then
  asset_root="$ROOT_DIR/extractor/src/main/assets/webp"
  mkdir -p "$asset_root"
  cp "$SOURCE_DIR/COPYING" "$asset_root/COPYING"
  for abi in arm64-v8a x86_64; do
    mkdir -p "$asset_root/$abi"
    for library in "${LIBRARIES[@]}"; do
      cp "$OUT_DIR/release/$abi/$library" "$asset_root/$abi/$library"
    done
  done
  echo "Assets kopiert nach $asset_root"
else
  echo "Assets nicht kopiert; dafür erneut mit --install-assets aufrufen."
fi
