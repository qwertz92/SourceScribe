#!/usr/bin/env bash
# Signs a release APK with the SourceScribe release key and refuses any other key.
#
# Usage: tools/sign-release.sh <unsigned-apk> <new-output-apk>
#
# By default the key and its password come from ~/.local/share/sourcescribe/signing/, where the key of release 0.1.0
# was created: sourcescribe-release.p12 (a PKCS12 keystore with the single alias "sourcescribe") and keystore-password
# (its password on one line). apksigner reads the password from that file itself, so it never appears in the
# environment, on a command line or in a log. Overrides: SOURCESCRIBE_SIGNING_DIR, KEYSTORE_PATH, KEY_ALIAS,
# KEYSTORE_PASSWORD_FILE, or KEYSTORE_PASSWORD and KEY_PASSWORD in the environment.
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

signing_dir=${SOURCESCRIBE_SIGNING_DIR:-$HOME/.local/share/sourcescribe/signing}
keystore_path=${KEYSTORE_PATH:-$signing_dir/sourcescribe-release.p12}
key_alias=${KEY_ALIAS:-sourcescribe}
# Android installs an update only when it carries the same certificate as the installed app. Every published APK since
# 0.1.0 carries this one, so an APK signed with any other key is refused here rather than shipped as an update that
# cannot be installed.
expected_cert_sha256=${EXPECTED_CERT_SHA256:-19d1da9a8fe704082a531faed8a24d966c485aae581a7076dd4b4f66c11d3881}

[[ "$keystore_path" = /* ]] || die "KEYSTORE_PATH must be an absolute path"
[[ -f "$keystore_path" ]] || die "keystore does not exist: $keystore_path"
keystore_real=$(realpath -- "$keystore_path")
project_real=$(realpath -- "$project_root")
case "$keystore_real" in
  "$project_real"|"$project_real"/*)
    die "keystore must stay outside the repository"
    ;;
esac

if [[ -n "${KEYSTORE_PASSWORD:-}" ]]; then
  keystore_pass='env:KEYSTORE_PASSWORD'
else
  password_file=${KEYSTORE_PASSWORD_FILE:-$signing_dir/keystore-password}
  [[ -f "$password_file" ]] || die "KEYSTORE_PASSWORD is not set and the password file does not exist: $password_file"
  keystore_pass="file:$password_file"
fi
# Without --key-pass apksigner opens the key with the keystore password, which is how a PKCS12 keystore protects it.
# Passing the same file: source twice would not work: apksigner reads the second password from the file's second line.
key_pass_args=()
if [[ -n "${KEY_PASSWORD:-}" ]]; then
  key_pass_args=(--key-pass 'env:KEY_PASSWORD')
fi

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
# stdin is closed so that a wrong password fails instead of prompting, or reading whatever feeds this script.
timeout 180s "$apksigner" sign \
  --ks "$keystore_real" \
  --ks-key-alias "$key_alias" \
  --ks-pass "$keystore_pass" \
  "${key_pass_args[@]}" \
  --out "$signed_apk" \
  "$aligned_apk" </dev/null
# apksigner prints one line per signature scheme ("V2 Signer: certificate SHA-256 digest: …", "V3 Signer: …"; older
# versions "Signer #1 certificate …"). All of them must name the same single certificate.
certificates=$(timeout 180s "$apksigner" verify --print-certs "$signed_apk")
cert_sha256=$(printf '%s\n' "$certificates" \
  | sed -n 's/^.*[Ss]igner.* certificate SHA-256 digest: \([0-9a-f]\{64\}\)$/\1/p' | sort -u)
[[ -n "$cert_sha256" && "$(printf '%s\n' "$cert_sha256" | wc -l)" -eq 1 ]] \
  || die "expected exactly one signing certificate, found: ${cert_sha256:-none}"
[[ "$cert_sha256" == "$expected_cert_sha256" ]] \
  || die "signed with certificate $cert_sha256, but the release certificate is $expected_cert_sha256"

# The temporary directory shares the output filesystem, so this final move is atomic.
mv -n -- "$signed_apk" "$output_path"
[[ ! -e "$signed_apk" ]] || die "output appeared during signing; refusing overwrite: $output_apk"
printf 'signed APK: %s\ncertificate SHA-256: %s\n' "$output_apk" "$cert_sha256"
