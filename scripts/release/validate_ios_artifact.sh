#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "usage: $0 <xcarchive> <ipa> <bundle-id> <marketing-version> <build-number> <team-id> <certificate-sha256> <output.json>" >&2
  exit 64
}

[[ $# -eq 8 ]] || usage

archive=$1
ipa=$2
expected_bundle_id=$3
expected_marketing_version=$4
expected_build_number=$5
expected_team_id=$6
expected_certificate=$(printf '%s' "${7//:/}" | tr '[:upper:]' '[:lower:]')
output=$8

repo_root=$(cd "$(dirname "$0")/../.." && pwd -P)
expected_xcode_build=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["toolchains"]["apple"]["xcode_build"])' "$repo_root/config/release-policy.json")
expected_sdk_major=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["toolchains"]["apple"]["minimum_ios_sdk_major"])' "$repo_root/config/release-policy.json")
expected_deployment_target=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["toolchains"]["apple"]["deployment_target"])' "$repo_root/config/release-policy.json")

[[ -d "$archive" && ! -L "$archive" ]] || { echo "xcarchive is not a directory" >&2; exit 2; }
[[ -f "$ipa" && ! -L "$ipa" ]] || { echo "IPA is not a regular file" >&2; exit 2; }
[[ $(stat -f %z "$ipa" 2>/dev/null || stat -c %s "$ipa") -le 2147483648 ]] || { echo "IPA exceeds the reviewed 2 GiB bound" >&2; exit 2; }
[[ "$expected_bundle_id" == "com.parlor.app" && "$expected_bundle_id" != *.debug ]] || { echo "non-Store iOS identity rejected" >&2; exit 2; }
[[ "$expected_build_number" =~ ^[1-9][0-9]*$ ]] || { echo "invalid iOS build number" >&2; exit 2; }
[[ "$expected_team_id" =~ ^[A-Z0-9]{10}$ ]] || { echo "invalid Apple Team ID" >&2; exit 2; }
[[ "$expected_certificate" =~ ^[0-9a-f]{64}$ ]] || { echo "invalid certificate fingerprint" >&2; exit 2; }

temporary_dir=$(mktemp -d "${TMPDIR:-/tmp}/parlor-ipa-validation.XXXXXX")
cleanup() {
  local original_status=${1:-$?}
  local cleanup_status=0
  trap - EXIT INT TERM
  if ! rm -rf "$temporary_dir"; then
    echo "could not remove the temporary iOS validation directory" >&2
    cleanup_status=2
  fi
  if [[ $original_status -ne 0 ]]; then
    exit "$original_status"
  fi
  exit "$cleanup_status"
}
trap 'cleanup $?' EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

# Reject traversal and link entries before any archive content reaches disk.
python3 - "$ipa" <<'PY'
import stat
import sys
import zipfile
from pathlib import Path, PurePosixPath

ipa = Path(sys.argv[1])
with zipfile.ZipFile(ipa) as archive:
    infos = archive.infolist()
    if not infos or len(infos) > 100_000:
        raise SystemExit("IPA entry count is outside the reviewed bound")
    if len({info.filename for info in infos}) != len(infos):
        raise SystemExit("IPA contains duplicate archive paths")
    total = 0
    for info in infos:
        path = PurePosixPath(info.filename)
        if path.is_absolute() or ".." in path.parts or stat.S_ISLNK(info.external_attr >> 16):
            raise SystemExit("IPA contains an unsafe archive entry")
        if info.flag_bits & 1:
            raise SystemExit("IPA contains an encrypted archive entry")
        total += info.file_size
        if total > 2 * 1024 * 1024 * 1024:
            raise SystemExit("IPA expands beyond the reviewed 2 GiB bound")
PY

unzip -tqq "$ipa"
unzip -q "$ipa" -d "$temporary_dir/ipa"
ipa_apps=()
while IFS= read -r -d '' path; do ipa_apps+=("$path"); done < <(find "$temporary_dir/ipa/Payload" -mindepth 1 -maxdepth 1 -type d -name '*.app' -print0)
[[ ${#ipa_apps[@]} -eq 1 ]] || { echo "IPA must contain exactly one application" >&2; exit 2; }
app=${ipa_apps[0]}

archive_apps=()
while IFS= read -r -d '' path; do archive_apps+=("$path"); done < <(find "$archive/Products/Applications" -mindepth 1 -maxdepth 1 -type d -name '*.app' -print0)
[[ ${#archive_apps[@]} -eq 1 ]] || { echo "xcarchive must contain exactly one application" >&2; exit 2; }
archive_app=${archive_apps[0]}

verify_code_tree() {
  local code_root=$1
  codesign --verify --strict --verbose=4 "$code_root"
  local certificate_prefix="$temporary_dir/code-certificate"
  local code_certificate
  rm -f "${certificate_prefix}"*
  codesign -d --extract-certificates "$certificate_prefix" "$code_root" 2>/dev/null
  [[ -f "${certificate_prefix}0" ]] || { echo "signed code has no leaf certificate" >&2; exit 2; }
  code_certificate=$(openssl x509 -inform DER -in "${certificate_prefix}0" -noout -fingerprint -sha256 | awk -F= '{gsub(":", "", $2); print tolower($2)}')
  [[ "$code_certificate" == "$expected_certificate" ]] || { echo "signed code uses an unexpected signing certificate" >&2; exit 2; }
  while IFS= read -r -d '' nested; do
    if [[ "$nested" != "$code_root" ]]; then
      codesign --verify --strict --verbose=4 "$nested"
      rm -f "${certificate_prefix}"*
      codesign -d --extract-certificates "$certificate_prefix" "$nested" 2>/dev/null
      [[ -f "${certificate_prefix}0" ]] || { echo "nested code has no leaf certificate" >&2; exit 2; }
      code_certificate=$(openssl x509 -inform DER -in "${certificate_prefix}0" -noout -fingerprint -sha256 | awk -F= '{gsub(":", "", $2); print tolower($2)}')
      [[ "$code_certificate" == "$expected_certificate" ]] || { echo "nested code uses an unexpected signing certificate" >&2; exit 2; }
    fi
  done < <(find "$code_root" \( -type d \( -name '*.framework' -o -name '*.appex' -o -name '*.app' \) -o -type f -name '*.dylib' \) -print0)
}

verify_code_tree "$archive_app"

archive_info="$archive_app/Info.plist"
archive_bundle_id=$(/usr/libexec/PlistBuddy -c 'Print :CFBundleIdentifier' "$archive_info")
archive_marketing_version=$(/usr/libexec/PlistBuddy -c 'Print :CFBundleShortVersionString' "$archive_info")
archive_build_number=$(/usr/libexec/PlistBuddy -c 'Print :CFBundleVersion' "$archive_info")
archive_xcode_build=$(/usr/libexec/PlistBuddy -c 'Print :DTXcodeBuild' "$archive_info")
archive_sdk_name=$(/usr/libexec/PlistBuddy -c 'Print :DTSDKName' "$archive_info")
archive_minimum_os=$(/usr/libexec/PlistBuddy -c 'Print :MinimumOSVersion' "$archive_info")
[[ "$archive_bundle_id" == "$expected_bundle_id" ]] || { echo "xcarchive Bundle ID mismatch" >&2; exit 2; }
[[ "$archive_marketing_version" == "$expected_marketing_version" ]] || { echo "xcarchive marketing version mismatch" >&2; exit 2; }
[[ "$archive_build_number" == "$expected_build_number" ]] || { echo "xcarchive build number mismatch" >&2; exit 2; }
[[ "$archive_xcode_build" == "$expected_xcode_build" ]] || { echo "xcarchive Xcode build differs from release policy" >&2; exit 2; }
[[ "$archive_sdk_name" =~ ^iphoneos([0-9]+)(\.|$) && "${BASH_REMATCH[1]}" -ge "$expected_sdk_major" ]] || {
  echo "xcarchive does not use the required physical iOS SDK" >&2
  exit 2
}
[[ "$archive_minimum_os" == "$expected_deployment_target" ]] || { echo "xcarchive deployment target mismatch" >&2; exit 2; }
codesign -d --entitlements :- "$archive_app" >"$temporary_dir/archive-entitlements.plist" 2>/dev/null
archive_team=$(/usr/libexec/PlistBuddy -c 'Print :com.apple.developer.team-identifier' "$temporary_dir/archive-entitlements.plist")
archive_application=$(/usr/libexec/PlistBuddy -c 'Print :application-identifier' "$temporary_dir/archive-entitlements.plist")
archive_get_task=$(/usr/libexec/PlistBuddy -c 'Print :get-task-allow' "$temporary_dir/archive-entitlements.plist" 2>/dev/null || echo false)
[[ "$archive_team" == "$expected_team_id" ]] || { echo "xcarchive Team ID mismatch" >&2; exit 2; }
[[ "$archive_application" == "$expected_team_id.$expected_bundle_id" ]] || { echo "xcarchive application identifier mismatch" >&2; exit 2; }
[[ "$archive_get_task" == false ]] || { echo "development xcarchive entitlement rejected" >&2; exit 2; }
[[ -f "$archive_app/PrivacyInfo.xcprivacy" ]] || { echo "xcarchive lacks PrivacyInfo.xcprivacy" >&2; exit 2; }
codesign -d --extract-certificates "$temporary_dir/archive-certificate" "$archive_app" 2>/dev/null
[[ -f "$temporary_dir/archive-certificate0" ]] || { echo "xcarchive leaf certificate is missing" >&2; exit 2; }
archive_certificate=$(openssl x509 -inform DER -in "$temporary_dir/archive-certificate0" -noout -fingerprint -sha256 | awk -F= '{gsub(":", "", $2); print tolower($2)}')
[[ "$archive_certificate" == "$expected_certificate" ]] || { echo "xcarchive distribution certificate mismatch" >&2; exit 2; }

plutil -lint "$app/Info.plist" >/dev/null
actual_bundle_id=$(/usr/libexec/PlistBuddy -c 'Print :CFBundleIdentifier' "$app/Info.plist")
actual_marketing_version=$(/usr/libexec/PlistBuddy -c 'Print :CFBundleShortVersionString' "$app/Info.plist")
actual_build_number=$(/usr/libexec/PlistBuddy -c 'Print :CFBundleVersion' "$app/Info.plist")
actual_xcode_build=$(/usr/libexec/PlistBuddy -c 'Print :DTXcodeBuild' "$app/Info.plist")
actual_sdk_name=$(/usr/libexec/PlistBuddy -c 'Print :DTSDKName' "$app/Info.plist")
actual_minimum_os=$(/usr/libexec/PlistBuddy -c 'Print :MinimumOSVersion' "$app/Info.plist")
executable=$(/usr/libexec/PlistBuddy -c 'Print :CFBundleExecutable' "$app/Info.plist")
[[ "$actual_bundle_id" == "$expected_bundle_id" ]] || { echo "IPA Bundle ID mismatch" >&2; exit 2; }
[[ "$actual_marketing_version" == "$expected_marketing_version" ]] || { echo "IPA marketing version mismatch" >&2; exit 2; }
[[ "$actual_build_number" == "$expected_build_number" ]] || { echo "IPA build number mismatch" >&2; exit 2; }
[[ "$actual_xcode_build" == "$expected_xcode_build" ]] || { echo "IPA Xcode build differs from release policy" >&2; exit 2; }
[[ "$actual_sdk_name" =~ ^iphoneos([0-9]+)(\.|$) && "${BASH_REMATCH[1]}" -ge "$expected_sdk_major" ]] || {
  echo "IPA does not use the required physical iOS SDK" >&2
  exit 2
}
[[ "$actual_minimum_os" == "$expected_deployment_target" ]] || { echo "IPA deployment target mismatch" >&2; exit 2; }
[[ -x "$app/$executable" ]] || { echo "IPA executable is missing" >&2; exit 2; }

verify_code_tree "$app"

codesign -d --entitlements :- "$app" >"$temporary_dir/entitlements.plist" 2>/dev/null
plutil -lint "$temporary_dir/entitlements.plist" >/dev/null
team_id=$(/usr/libexec/PlistBuddy -c 'Print :com.apple.developer.team-identifier' "$temporary_dir/entitlements.plist")
application_identifier=$(/usr/libexec/PlistBuddy -c 'Print :application-identifier' "$temporary_dir/entitlements.plist")
get_task_allow=$(/usr/libexec/PlistBuddy -c 'Print :get-task-allow' "$temporary_dir/entitlements.plist" 2>/dev/null || echo false)
[[ "$team_id" == "$expected_team_id" ]] || { echo "signed app Team ID mismatch" >&2; exit 2; }
[[ "$application_identifier" == "$expected_team_id.$expected_bundle_id" ]] || { echo "signed application identifier mismatch" >&2; exit 2; }
[[ "$get_task_allow" == false ]] || { echo "development get-task-allow entitlement rejected" >&2; exit 2; }

certificate_prefix="$temporary_dir/certificate"
codesign -d --extract-certificates "$certificate_prefix" "$app" 2>/dev/null
[[ -f "${certificate_prefix}0" ]] || { echo "codesign did not expose the leaf certificate" >&2; exit 2; }
actual_certificate=$(openssl x509 -inform DER -in "${certificate_prefix}0" -noout -fingerprint -sha256 | awk -F= '{gsub(":", "", $2); print tolower($2)}')
[[ "$actual_certificate" == "$expected_certificate" ]] || { echo "Apple distribution certificate mismatch" >&2; exit 2; }
openssl x509 -inform DER -in "${certificate_prefix}0" -checkend 0 -noout >/dev/null || { echo "Apple distribution certificate is expired" >&2; exit 2; }
certificate_subject=$(openssl x509 -inform DER -in "${certificate_prefix}0" -noout -subject -nameopt RFC2253)
grep -Fq "OU=$expected_team_id" <<<"$certificate_subject" || { echo "distribution certificate Team ID mismatch" >&2; exit 2; }
grep -Eq 'CN=Apple Distribution([,:]|$)' <<<"$certificate_subject" || { echo "non-distribution Apple certificate rejected" >&2; exit 2; }

profile="$app/embedded.mobileprovision"
[[ -f "$profile" ]] || { echo "IPA lacks an embedded provisioning profile" >&2; exit 2; }
security cms -D -i "$profile" >"$temporary_dir/profile.plist"
plutil -lint "$temporary_dir/profile.plist" >/dev/null
profile_team=$(/usr/libexec/PlistBuddy -c 'Print :TeamIdentifier:0' "$temporary_dir/profile.plist")
profile_app_id=$(/usr/libexec/PlistBuddy -c 'Print :Entitlements:application-identifier' "$temporary_dir/profile.plist")
profile_get_task=$(/usr/libexec/PlistBuddy -c 'Print :Entitlements:get-task-allow' "$temporary_dir/profile.plist" 2>/dev/null || echo false)
profile_uuid=$(/usr/libexec/PlistBuddy -c 'Print :UUID' "$temporary_dir/profile.plist")
profile_name=$(/usr/libexec/PlistBuddy -c 'Print :Name' "$temporary_dir/profile.plist")
expiration=$(/usr/libexec/PlistBuddy -c 'Print :ExpirationDate' "$temporary_dir/profile.plist")
[[ "$profile_uuid" =~ ^[0-9A-Fa-f-]{36}$ ]] || { echo "provisioning profile UUID is invalid" >&2; exit 2; }
[[ "$profile_team" == "$expected_team_id" ]] || { echo "provisioning profile Team ID mismatch" >&2; exit 2; }
[[ "$profile_app_id" == "$expected_team_id.$expected_bundle_id" ]] || { echo "provisioning profile application identifier mismatch" >&2; exit 2; }
[[ "$profile_get_task" == false ]] || { echo "development provisioning profile rejected" >&2; exit 2; }
if /usr/libexec/PlistBuddy -c 'Print :ProvisionedDevices' "$temporary_dir/profile.plist" >/dev/null 2>&1; then
  echo "device provisioning profile rejected for App Store export" >&2
  exit 2
fi
if /usr/libexec/PlistBuddy -c 'Print :ProvisionsAllDevices' "$temporary_dir/profile.plist" >/dev/null 2>&1; then
  echo "enterprise provisioning profile rejected for App Store export" >&2
  exit 2
fi

[[ -f "$app/PrivacyInfo.xcprivacy" ]] || { echo "IPA lacks PrivacyInfo.xcprivacy" >&2; exit 2; }
plutil -lint "$app/PrivacyInfo.xcprivacy" >/dev/null
architectures=$(lipo -archs "$app/$executable")
[[ " $architectures " == *" arm64 "* ]] || { echo "IPA executable lacks arm64" >&2; exit 2; }
[[ " $architectures " != *" x86_64 "* ]] || { echo "simulator architecture found in IPA" >&2; exit 2; }

python3 - "$ipa" "$app" "$temporary_dir/entitlements.plist" "$temporary_dir/archive-entitlements.plist" "$temporary_dir/profile.plist" \
  "$actual_certificate" "$architectures" "$profile_uuid" "$profile_name" "$expiration" \
  "$expected_xcode_build" "$expected_sdk_major" "$expected_deployment_target" "$output" <<'PY'
import datetime as dt
import hashlib
import json
import os
import re
import subprocess
import stat
import sys
import zipfile
from pathlib import Path, PurePosixPath

ipa = Path(sys.argv[1])
app = Path(sys.argv[2])
entitlements = Path(sys.argv[3])
archive_entitlements = Path(sys.argv[4])
profile = Path(sys.argv[5])
certificate = sys.argv[6]
architectures = sys.argv[7].split()
profile_uuid = sys.argv[8]
profile_name = sys.argv[9]
expiration = sys.argv[10]
expected_xcode_build = sys.argv[11]
expected_sdk_major = int(sys.argv[12])
expected_deployment_target = sys.argv[13]
output = Path(sys.argv[14])

with zipfile.ZipFile(ipa) as archive:
    infos = archive.infolist()
    if not infos or len(infos) > 100_000:
        raise SystemExit("IPA entry count is outside the reviewed bound")
    total = 0
    for info in infos:
        path = PurePosixPath(info.filename)
        if path.is_absolute() or ".." in path.parts or stat.S_ISLNK(info.external_attr >> 16):
            raise SystemExit("IPA contains an unsafe archive entry")
        total += info.file_size
        if total > 2 * 1024 * 1024 * 1024:
            raise SystemExit("IPA expands beyond the reviewed 2 GiB bound")

plist = __import__("plistlib")
info = plist.loads((app / "Info.plist").read_bytes())
entitlement_values = plist.loads(entitlements.read_bytes())
archive_entitlement_values = plist.loads(archive_entitlements.read_bytes())
profile_values = plist.loads(profile.read_bytes())

if info.get("CFBundleSupportedPlatforms") != ["iPhoneOS"] or info.get("DTPlatformName") != "iphoneos":
    raise SystemExit("IPA was not built for the physical iOS platform")
if info.get("DTXcodeBuild") != expected_xcode_build:
    raise SystemExit("IPA Xcode build differs from release policy")
sdk_match = re.fullmatch(r"iphoneos([0-9]+)(?:\..*)?", str(info.get("DTSDKName", "")))
if sdk_match is None or int(sdk_match.group(1)) < expected_sdk_major:
    raise SystemExit("IPA was built with an iOS SDK below the release-policy floor")
if info.get("MinimumOSVersion") != expected_deployment_target:
    raise SystemExit("IPA minimum OS differs from the supported deployment target")
if list(app.rglob("*.appex")):
    raise SystemExit("IPA contains an app extension outside the reviewed signing/entitlement contract")

macho_architectures = {}
macho_minimum_os_versions = {}

def version_tuple(value):
    return tuple(int(component) for component in value.split("."))

for candidate in sorted(path for path in app.rglob("*") if path.is_file()):
    probe = subprocess.run(
        ["file", "-b", str(candidate)],
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
        text=True,
    )
    if probe.returncode != 0:
        raise SystemExit("could not inspect an IPA file type")
    if "Mach-O" not in probe.stdout:
        continue
    architecture_probe = subprocess.run(
        ["lipo", "-archs", str(candidate)],
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
        text=True,
    )
    if architecture_probe.returncode != 0:
        raise SystemExit("could not inspect an IPA Mach-O architecture")
    candidate_architectures = architecture_probe.stdout.split()
    if "arm64" not in candidate_architectures or {"x86_64", "i386"} & set(candidate_architectures):
        raise SystemExit("IPA contains a Mach-O file with non-device architectures")
    relative = str(candidate.relative_to(app))
    macho_architectures[relative] = sorted(candidate_architectures)
    build_probe = subprocess.run(
        ["xcrun", "vtool", "-show-build", str(candidate)],
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
        text=True,
    )
    if build_probe.returncode != 0:
        raise SystemExit("could not inspect an IPA Mach-O build platform")
    platforms = re.findall(r"^\s*platform\s+(\S+)\s*$", build_probe.stdout, re.MULTILINE)
    minimum_versions = re.findall(r"^\s*minos\s+([0-9]+(?:\.[0-9]+){1,2})\s*$", build_probe.stdout, re.MULTILINE)
    if not platforms or any(platform != "IOS" for platform in platforms):
        raise SystemExit("IPA contains Mach-O code for a non-device platform")
    if not minimum_versions or any(
        version_tuple(version) > version_tuple(expected_deployment_target)
        for version in minimum_versions
    ):
        raise SystemExit("IPA contains Mach-O code requiring a newer OS than the app deployment target")
    macho_minimum_os_versions[relative] = sorted(set(minimum_versions))
if not macho_architectures:
    raise SystemExit("IPA contains no Mach-O executable code")

allowed_entitlements = {
    "application-identifier",
    "beta-reports-active",
    "com.apple.developer.team-identifier",
    "get-task-allow",
    "keychain-access-groups",
}

def validate_entitlements(values, label):
    unexpected = set(values) - allowed_entitlements
    if unexpected:
        raise SystemExit(f"{label} has unreviewed entitlements: {sorted(unexpected)}")
    if values.get("get-task-allow", False) is not False:
        raise SystemExit(f"{label} permits debugging")
    expected_team = entitlement_values["com.apple.developer.team-identifier"]
    if values.get("com.apple.developer.team-identifier") != expected_team:
        raise SystemExit(f"{label} Team ID differs from the exported app")
    expected_application = f"{expected_team}.{info['CFBundleIdentifier']}"
    if values.get("application-identifier") != expected_application:
        raise SystemExit(f"{label} application identifier mismatch")
    keychain_groups = values.get("keychain-access-groups", [])
    if not isinstance(keychain_groups, list) or any(not isinstance(group, str) for group in keychain_groups):
        raise SystemExit(f"{label} has an invalid Keychain access group")
    if keychain_groups not in ([], [expected_application]):
        raise SystemExit(f"{label} has an unreviewed Keychain access group")

validate_entitlements(entitlement_values, "exported IPA")
validate_entitlements(archive_entitlement_values, "xcarchive")
profile_expiration = profile_values.get("ExpirationDate")
if not isinstance(profile_expiration, dt.datetime):
    raise SystemExit("provisioning profile has no expiration date")
if profile_expiration.replace(tzinfo=dt.timezone.utc) <= dt.datetime.now(dt.timezone.utc):
    raise SystemExit("provisioning profile is expired")
profile_platforms = profile_values.get("Platform", [])
if not isinstance(profile_platforms, list) or "iOS" not in profile_platforms:
    raise SystemExit("provisioning profile is not valid for iOS")
developer_certificates = profile_values.get("DeveloperCertificates", [])
profile_certificate_digests = {
    hashlib.sha256(value).hexdigest()
    for value in developer_certificates
    if isinstance(value, bytes)
}
if certificate not in profile_certificate_digests:
    raise SystemExit("provisioning profile does not contain the signing certificate")
digest_state = hashlib.sha256()
with ipa.open("rb") as stream:
    for chunk in iter(lambda: stream.read(1024 * 1024), b""):
        digest_state.update(chunk)
result = {
    "schema_version": 1,
    "artifact_type": "ios-ipa",
    "bundle_id": info["CFBundleIdentifier"],
    "marketing_version": info["CFBundleShortVersionString"],
    "build_number": str(info["CFBundleVersion"]),
    "minimum_os_version": info["MinimumOSVersion"],
    "xcode_build": info["DTXcodeBuild"],
    "sdk_name": info["DTSDKName"],
    "team_id": entitlement_values["com.apple.developer.team-identifier"],
    "application_identifier": entitlement_values["application-identifier"],
    "get_task_allow": bool(entitlement_values.get("get-task-allow", False)),
    "entitlement_keys": sorted(entitlement_values),
    "archive_entitlement_keys": sorted(archive_entitlement_values),
    "architectures": sorted(architectures),
    "macho_architectures": macho_architectures,
    "macho_minimum_os_versions": macho_minimum_os_versions,
    "signing_certificate_sha256": certificate,
    "provisioning_profile_uuid": profile_uuid,
    "provisioning_profile_name": profile_name,
    "provisioning_profile_expiration": expiration,
    "provisioning_profile_sha256": hashlib.sha256((app / "embedded.mobileprovision").read_bytes()).hexdigest(),
    "privacy_manifest_sha256": hashlib.sha256((app / "PrivacyInfo.xcprivacy").read_bytes()).hexdigest(),
    "embedded_framework_count": len(list(app.rglob("*.framework"))),
    "embedded_extension_count": len(list(app.rglob("*.appex"))),
    "archive_entry_count": len(infos),
    "artifact_sha256": digest_state.hexdigest(),
    "size_bytes": ipa.stat().st_size,
}
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n")
os.chmod(output, 0o600)
PY
