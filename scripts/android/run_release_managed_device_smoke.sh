#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
temporary_dir=

cleanup() {
  local status=$1
  local cleanup_status=0
  trap - EXIT
  set +e
  "$repo_root/gradlew" --stop >/dev/null 2>&1
  cleanup_status=$?
  if [[ -n "$temporary_dir" ]]; then
    rm -rf "$temporary_dir"
    if [[ $? -ne 0 && $cleanup_status -eq 0 ]]; then
      cleanup_status=1
    fi
  fi
  if [[ $status -eq 0 && $cleanup_status -ne 0 ]]; then
    status=$cleanup_status
  fi
  exit "$status"
}
trap 'cleanup $?' EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

temporary_dir=$(mktemp -d "${TMPDIR:-/tmp}/parlor-android-runtime.XXXXXX")
chmod 700 "$temporary_dir"

keystore="$temporary_dir/managed-device.p12"
keystore_password=parlor-managed-device-only
key_alias=parlor-managed-device

keytool -genkeypair \
  -alias "$key_alias" \
  -keyalg RSA \
  -keysize 2048 \
  -validity 2 \
  -dname "CN=Parlor Managed Device,OU=CI,O=Parlor,L=Cairo,C=EG" \
  -keystore "$keystore" \
  -storetype PKCS12 \
  -storepass "$keystore_password" \
  -keypass "$keystore_password" \
  -noprompt

cd "$repo_root"
./gradlew productionAndroidRuntimeCheck \
  --dependency-verification=strict \
  --no-daemon \
  --max-workers=2 \
  --stacktrace \
  --console=plain \
  "-Pandroid.injected.signing.store.file=$keystore" \
  "-Pandroid.injected.signing.store.password=$keystore_password" \
  "-Pandroid.injected.signing.key.alias=$key_alias" \
  "-Pandroid.injected.signing.key.password=$keystore_password"
