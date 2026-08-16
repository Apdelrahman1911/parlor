#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "usage: $0 <archive.xcarchive> <export-directory> <derived-data-directory> <evidence-directory>" >&2
  exit 64
}

[[ $# -eq 4 ]] || usage

archive_path=$1
export_path=$2
derived_data_path=$3
evidence_path=$4

: "${PARLOR_APPLE_CERTIFICATE_P12_PATH:?missing certificate path}"
: "${PARLOR_APPLE_CERTIFICATE_PASSWORD:?missing certificate password}"
: "${PARLOR_APPLE_PROFILE_PATH:?missing provisioning profile path}"
: "${PARLOR_APPLE_TEAM_ID:?missing Team ID}"
: "${PARLOR_APPLE_BUNDLE_ID:?missing Bundle ID}"
: "${PARLOR_APPLE_DISTRIBUTION_CERT_SHA256:?missing distribution certificate fingerprint}"

[[ -f "$PARLOR_APPLE_CERTIFICATE_P12_PATH" && ! -L "$PARLOR_APPLE_CERTIFICATE_P12_PATH" ]] || { echo "invalid certificate file" >&2; exit 2; }
[[ -f "$PARLOR_APPLE_PROFILE_PATH" && ! -L "$PARLOR_APPLE_PROFILE_PATH" ]] || { echo "invalid provisioning profile file" >&2; exit 2; }
[[ "$PARLOR_APPLE_TEAM_ID" =~ ^[A-Z0-9]{10}$ ]] || { echo "invalid Apple Team ID" >&2; exit 2; }
[[ "$PARLOR_APPLE_BUNDLE_ID" == "com.parlor.app" ]] || { echo "non-Store iOS Bundle ID rejected" >&2; exit 2; }

temporary_dir=$(mktemp -d "${RUNNER_TEMP:-${TMPDIR:-/tmp}}/parlor-ios-signing.XXXXXX")
keychain="$temporary_dir/parlor.keychain-db"
installed_profile=""
original_default=""
original_keychains=()

cleanup() {
  local original_status=${1:-$?}
  local cleanup_status=0
  trap - EXIT INT TERM
  if [[ -n "$installed_profile" && -f "$installed_profile" ]]; then
    rm -f "$installed_profile" || cleanup_status=2
  fi
  if [[ ${#original_keychains[@]} -gt 0 ]]; then
    if ! security list-keychains -d user -s "${original_keychains[@]}" >/dev/null 2>&1; then
      echo "could not restore the original user keychain search list" >&2
      cleanup_status=2
    fi
  fi
  if [[ -n "$original_default" ]]; then
    if ! security default-keychain -d user -s "$original_default" >/dev/null 2>&1; then
      echo "could not restore the original default keychain" >&2
      cleanup_status=2
    fi
  fi
  if [[ -e "$keychain" ]] && ! security delete-keychain "$keychain" >/dev/null 2>&1; then
    echo "could not delete the ephemeral signing keychain" >&2
    cleanup_status=2
  fi
  rm -rf "$temporary_dir" || cleanup_status=2
  if [[ $original_status -ne 0 ]]; then
    exit "$original_status"
  fi
  exit "$cleanup_status"
}
trap 'cleanup $?' EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

keychain_password=$(openssl rand -hex 32)
original_default=$(security default-keychain -d user | sed 's/^[[:space:]]*"//; s/"[[:space:]]*$//')
while IFS= read -r value; do
  value=$(printf '%s' "$value" | sed 's/^[[:space:]]*"//; s/"[[:space:]]*$//')
  [[ -n "$value" ]] && original_keychains+=("$value")
done < <(security list-keychains -d user)
[[ -n "$original_default" && ${#original_keychains[@]} -gt 0 ]] || {
  echo "could not capture the original user keychain state" >&2
  exit 2
}

mkdir -p "$evidence_path"

export PARLOR_APPLE_CERTIFICATE_PASSWORD
actual_certificate=$(
  openssl pkcs12 \
    -in "$PARLOR_APPLE_CERTIFICATE_P12_PATH" \
    -clcerts \
    -nokeys \
    -passin env:PARLOR_APPLE_CERTIFICATE_PASSWORD 2>/dev/null |
    openssl x509 -noout -fingerprint -sha256 |
    awk -F= '{gsub(":", "", $2); print tolower($2)}'
)
expected_certificate=$(printf '%s' "$PARLOR_APPLE_DISTRIBUTION_CERT_SHA256" | tr -d ':' | tr '[:upper:]' '[:lower:]')
[[ "$actual_certificate" == "$expected_certificate" ]] || { echo "distribution certificate fingerprint mismatch" >&2; exit 2; }

security create-keychain -p "$keychain_password" "$keychain"
security set-keychain-settings -lut 21600 "$keychain"
security unlock-keychain -p "$keychain_password" "$keychain"
security import "$PARLOR_APPLE_CERTIFICATE_P12_PATH" \
  -k "$keychain" \
  -P "$PARLOR_APPLE_CERTIFICATE_PASSWORD" \
  -T /usr/bin/codesign \
  -T /usr/bin/security >/dev/null
security set-key-partition-list \
  -S apple-tool:,apple: \
  -s \
  -k "$keychain_password" \
  "$keychain" >/dev/null
security list-keychains -d user -s "$keychain"
security default-keychain -d user -s "$keychain"

security cms -D -i "$PARLOR_APPLE_PROFILE_PATH" >"$temporary_dir/profile.plist"
plutil -lint "$temporary_dir/profile.plist" >/dev/null
profile_uuid=$(/usr/libexec/PlistBuddy -c 'Print :UUID' "$temporary_dir/profile.plist")
profile_name=$(/usr/libexec/PlistBuddy -c 'Print :Name' "$temporary_dir/profile.plist")
profile_team=$(/usr/libexec/PlistBuddy -c 'Print :TeamIdentifier:0' "$temporary_dir/profile.plist")
profile_application=$(/usr/libexec/PlistBuddy -c 'Print :Entitlements:application-identifier' "$temporary_dir/profile.plist")
profile_get_task=$(/usr/libexec/PlistBuddy -c 'Print :Entitlements:get-task-allow' "$temporary_dir/profile.plist" 2>/dev/null || echo false)
[[ "$profile_team" == "$PARLOR_APPLE_TEAM_ID" ]] || { echo "provisioning profile Team ID mismatch" >&2; exit 2; }
[[ "$profile_application" == "$PARLOR_APPLE_TEAM_ID.$PARLOR_APPLE_BUNDLE_ID" ]] || { echo "provisioning profile Bundle ID mismatch" >&2; exit 2; }
[[ "$profile_get_task" == false ]] || { echo "development provisioning profile rejected" >&2; exit 2; }
if /usr/libexec/PlistBuddy -c 'Print :ProvisionedDevices' "$temporary_dir/profile.plist" >/dev/null 2>&1; then
  echo "device provisioning profile rejected" >&2
  exit 2
fi

profile_directory="$HOME/Library/MobileDevice/Provisioning Profiles"
mkdir -p "$profile_directory"
profile_destination="$profile_directory/$profile_uuid.mobileprovision"
if [[ -e "$profile_destination" ]]; then
  echo "refusing to overwrite an installed provisioning profile" >&2
  exit 2
fi
install -m 600 "$PARLOR_APPLE_PROFILE_PATH" "$profile_destination"
installed_profile=$profile_destination

# The archive only needs the imported keychain identity and installed profile.
# Do not expose source signing-file paths or the PKCS#12 password to Xcode or
# any build phase it launches.
unset PARLOR_APPLE_CERTIFICATE_PASSWORD
unset PARLOR_APPLE_CERTIFICATE_P12_PATH
unset PARLOR_APPLE_PROFILE_PATH

export_options="$temporary_dir/ExportOptions.plist"
python3 - "$export_options" "$PARLOR_APPLE_TEAM_ID" "$PARLOR_APPLE_BUNDLE_ID" "$profile_name" <<'PY'
import plistlib
import sys
from pathlib import Path

output, team_id, bundle_id, profile_name = sys.argv[1:]
value = {
    "method": "app-store-connect",
    "destination": "export",
    "signingStyle": "manual",
    "teamID": team_id,
    "manageAppVersionAndBuildNumber": False,
    "stripSwiftSymbols": True,
    "uploadSymbols": True,
    "provisioningProfiles": {bundle_id: profile_name},
}
Path(output).write_bytes(plistlib.dumps(value, fmt=plistlib.FMT_XML, sort_keys=True))
PY
plutil -lint "$export_options" >/dev/null

for candidate_output in "$archive_path" "$export_path" "$derived_data_path"; do
  [[ -n "$candidate_output" && "$candidate_output" != / && "$candidate_output" != "$HOME" ]] || {
    echo "unsafe iOS build output path" >&2
    exit 2
  }
  [[ ! -e "$candidate_output" ]] || {
    echo "iOS build output already exists; use a fresh candidate workspace" >&2
    exit 2
  }
done
mkdir -p "$export_path" "$derived_data_path"

xcodebuild \
  -project iosApp/iosApp.xcodeproj \
  -scheme iosApp \
  -configuration Release \
  -destination 'generic/platform=iOS' \
  -archivePath "$archive_path" \
  -derivedDataPath "$derived_data_path" \
  DEVELOPMENT_TEAM="$PARLOR_APPLE_TEAM_ID" \
  PRODUCT_BUNDLE_IDENTIFIER="$PARLOR_APPLE_BUNDLE_ID" \
  CODE_SIGN_STYLE=Manual \
  CODE_SIGN_IDENTITY='Apple Distribution' \
  PROVISIONING_PROFILE_SPECIFIER="$profile_name" \
  archive | tee "$evidence_path/xcode-archive.log"

xcodebuild \
  -exportArchive \
  -archivePath "$archive_path" \
  -exportPath "$export_path" \
  -exportOptionsPlist "$export_options" | tee "$evidence_path/xcode-export.log"

ipa_files=()
while IFS= read -r -d '' path; do ipa_files+=("$path"); done < <(find "$export_path" -maxdepth 1 -type f -name '*.ipa' -print0)
[[ ${#ipa_files[@]} -eq 1 ]] || { echo "Xcode export did not produce exactly one IPA" >&2; exit 2; }
printf '%s\n' "${ipa_files[0]}" >"$evidence_path/ipa-path.txt"
printf '%s\n' "$profile_uuid" >"$evidence_path/provisioning-profile-uuid.txt"
printf '%s\n' "$actual_certificate" >"$evidence_path/distribution-certificate.sha256"
