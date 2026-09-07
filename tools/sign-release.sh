#!/usr/bin/env bash
set -euo pipefail

die() {
  printf 'sign-release: %s\n' "$*" >&2
  exit 1
}

if [[ $# -ne 2 ]]; then
  die "usage: tools/sign-release.sh <unsigned-apk> <new-output-apk>"
fi

unsigned_apk=$1
output_apk=$2
project_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
output_parent=$(dirname -- "$output_apk")
[[ -d "$output_parent" ]] || die "output directory does not exist: $output_parent"
output_dir=$(cd -- "$output_parent" && pwd -P)
output_name=$(basename -- "$output_apk")
output_path="$output_dir/$output_name"

[[ -f "$unsigned_apk" ]] || die "unsigned APK does not exist: $unsigned_apk"
[[ ! -e "$output_path" && ! -L "$output_path" ]] || die "refusing to overwrite existing output: $output_apk"

: "${KEYSTORE_PATH:?set KEYSTORE_PATH to an existing absolute keystore path outside the repository}"
: "${KEY_ALIAS:?set KEY_ALIAS to the keystore alias}"
: "${KEYSTORE_PASSWORD:?set KEYSTORE_PASSWORD in the environment}"
: "${KEY_PASSWORD:?set KEY_PASSWORD in the environment}"

[[ "$KEYSTORE_PATH" = /* ]] || die "KEYSTORE_PATH must be an absolute path"
[[ -f "$KEYSTORE_PATH" ]] || die "keystore does not exist: $KEYSTORE_PATH"
keystore_real=$(realpath -- "$KEYSTORE_PATH")
project_real=$(realpath -- "$project_root")
case "$keystore_real" in
  "$project_real"|"$project_real"/*)
    die "keystore must stay outside the repository"
    ;;
esac

sdk_root=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}
[[ -n "$sdk_root" ]] || die 'ANDROID_SDK_ROOT or ANDROID_HOME is required'
build_tools="$sdk_root/build-tools/37.0.0"
zipalign="$build_tools/zipalign"
apksigner="$build_tools/apksigner"
[[ -x "$zipalign" ]] || die "missing Android build tool: $zipalign"
[[ -x "$apksigner" ]] || die "missing Android build tool: $apksigner"

unsigned_real=$(realpath -- "$unsigned_apk")
output_real=$(realpath -m -- "$output_path")
[[ "$unsigned_real" != "$output_real" ]] || die 'input and output APK must differ'

work_dir=$(mktemp -d "$output_dir/.sourcescribe-sign.XXXXXX")
trap 'rm -rf -- "$work_dir"' EXIT
aligned_apk="$work_dir/aligned.apk"
signed_apk="$work_dir/signed.apk"

timeout 180s "$zipalign" -P 16 -f 4 "$unsigned_apk" "$aligned_apk"
timeout 180s "$apksigner" sign \
  --ks "$keystore_real" \
  --ks-key-alias "$KEY_ALIAS" \
  --ks-pass 'env:KEYSTORE_PASSWORD' \
  --key-pass 'env:KEY_PASSWORD' \
  --out "$signed_apk" \
  "$aligned_apk"
timeout 180s "$apksigner" verify "$signed_apk"

# The temporary directory shares the output filesystem, so this final move is atomic.
mv -n -- "$signed_apk" "$output_path"
[[ ! -e "$signed_apk" ]] || die "output appeared during signing; refusing overwrite: $output_apk"
printf 'signed APK: %s\n' "$output_apk"
