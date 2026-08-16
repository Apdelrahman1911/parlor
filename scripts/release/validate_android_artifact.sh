#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "usage: $0 <aab> <bundletool.jar> <application-id> <version-name> <version-code> <certificate-sha256> <dependency-report> <output.json>" >&2
  exit 64
}

[[ $# -eq 8 ]] || usage

aab=$1
bundletool=$2
expected_application_id=$3
expected_version_name=$4
expected_version_code=$5
expected_certificate=$(printf '%s' "${6//:/}" | tr '[:upper:]' '[:lower:]')
dependency_report=$7
output=$8

[[ -f "$aab" && ! -L "$aab" ]] || { echo "AAB is not a regular file" >&2; exit 2; }
[[ $(stat -f %z "$aab" 2>/dev/null || stat -c %s "$aab") -le 536870912 ]] || { echo "AAB exceeds the reviewed 512 MiB bound" >&2; exit 2; }
[[ -f "$bundletool" && ! -L "$bundletool" ]] || { echo "bundletool is not a regular file" >&2; exit 2; }
[[ "$expected_application_id" == "com.parlor.app" ]] || { echo "non-Store Android identity rejected" >&2; exit 2; }
[[ "$expected_application_id" != *.debug ]] || { echo "Debug Android identity rejected" >&2; exit 2; }
[[ "$expected_version_code" =~ ^[1-9][0-9]*$ ]] || { echo "invalid Android version code" >&2; exit 2; }
[[ "$expected_certificate" =~ ^[0-9a-f]{64}$ ]] || { echo "invalid certificate fingerprint" >&2; exit 2; }
[[ -s "$dependency_report" && -f "$dependency_report" && ! -L "$dependency_report" ]] || { echo "Android release dependency report is missing" >&2; exit 2; }
[[ $(stat -f %z "$dependency_report" 2>/dev/null || stat -c %s "$dependency_report") -le 10485760 ]] || { echo "Android dependency report exceeds the reviewed 10 MiB bound" >&2; exit 2; }

repo_root=$(cd "$(dirname "$0")/../.." && pwd -P)
expected_bundletool=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["tools"]["bundletool"]["sha256"])' "$repo_root/config/release-policy.json")
actual_bundletool=$(shasum -a 256 "$bundletool" | awk '{print $1}')
[[ "$actual_bundletool" == "$expected_bundletool" ]] || { echo "bundletool digest mismatch" >&2; exit 2; }

temporary_dir=$(mktemp -d "${TMPDIR:-/tmp}/parlor-aab-validation.XXXXXX")
cleanup() {
  local original_status=${1:-$?}
  local cleanup_status=0
  trap - EXIT INT TERM
  if ! rm -rf "$temporary_dir"; then
    echo "could not remove the temporary Android validation directory" >&2
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

unzip -tqq "$aab"
jarsigner -verify -strict -certs "$aab" >"$temporary_dir/jarsigner.txt" 2>&1
keytool -printcert -jarfile "$aab" >"$temporary_dir/certificate.txt"
actual_certificate=$(awk -F': ' '/SHA256:/{gsub(":", "", $2); print tolower($2); exit}' "$temporary_dir/certificate.txt")
[[ "$actual_certificate" == "$expected_certificate" ]] || { echo "Android upload certificate mismatch" >&2; exit 2; }

java -jar "$bundletool" dump manifest --bundle="$aab" --module=base >"$temporary_dir/AndroidManifest.xml"

unzip -Z1 "$aab" >"$temporary_dir/entries.txt"
python3 - "$aab" "$temporary_dir/AndroidManifest.xml" "$temporary_dir/entries.txt" \
  "$expected_application_id" "$expected_version_name" "$expected_version_code" \
  "$actual_certificate" "$dependency_report" "$output" <<'PY'
import hashlib
import json
import os
import stat
import sys
import zipfile
import xml.etree.ElementTree as ET
from pathlib import Path, PurePosixPath

aab = Path(sys.argv[1])
manifest_path = Path(sys.argv[2])
entries_path = Path(sys.argv[3])
expected_id = sys.argv[4]
expected_name = sys.argv[5]
expected_code = int(sys.argv[6])
certificate = sys.argv[7]
dependency_report = Path(sys.argv[8])
output = Path(sys.argv[9])
android = "{http://schemas.android.com/apk/res/android}"

with zipfile.ZipFile(aab) as archive:
    infos = archive.infolist()
    if not infos or len(infos) > 100_000:
        raise SystemExit("AAB entry count is outside the reviewed bound")
    if len({info.filename for info in infos}) != len(infos):
        raise SystemExit("AAB contains duplicate archive paths")
    total = 0
    for info in infos:
        path = PurePosixPath(info.filename)
        if path.is_absolute() or ".." in path.parts or stat.S_ISLNK(info.external_attr >> 16):
            raise SystemExit("AAB contains an unsafe archive entry")
        if info.flag_bits & 1:
            raise SystemExit("AAB contains an encrypted archive entry")
        total += info.file_size
        if total > 1024 * 1024 * 1024:
            raise SystemExit("AAB expands beyond the reviewed 1 GiB bound")

root = ET.parse(manifest_path).getroot()
if root.tag != "manifest" or root.attrib.get("package") != expected_id:
    raise SystemExit("AAB package name mismatch")
if root.attrib.get(android + "sharedUserId"):
    raise SystemExit("AAB unexpectedly declares a shared user ID")
if int(root.attrib.get(android + "versionCode", "0")) != expected_code:
    raise SystemExit("AAB version code mismatch")
if root.attrib.get(android + "versionName") != expected_name:
    raise SystemExit("AAB version name mismatch")
uses_sdk = root.find("uses-sdk")
if uses_sdk is None:
    raise SystemExit("AAB manifest lacks uses-sdk")
min_sdk = int(uses_sdk.attrib.get(android + "minSdkVersion", "0"))
target_sdk = int(uses_sdk.attrib.get(android + "targetSdkVersion", "0"))
if min_sdk != 26 or target_sdk != 36:
    raise SystemExit("AAB SDK levels differ from the release policy")
application = root.find("application")
if application is None:
    raise SystemExit("AAB manifest lacks application")
if application.attrib.get(android + "debuggable") == "true" or application.attrib.get(android + "testOnly") == "true":
    raise SystemExit("AAB is debug/test-only")
if application.attrib.get(android + "usesCleartextTraffic") != "false":
    raise SystemExit("AAB permits cleartext traffic")
if application.attrib.get(android + "allowBackup") != "false":
    raise SystemExit("AAB permits Android backup")
if application.attrib.get(android + "name") != "com.parlor.app.ParlorApplication":
    raise SystemExit("AAB application class differs from the reviewed release entry point")

exported_components = []
launcher_components = []
for tag in ("activity", "activity-alias", "service", "receiver", "provider"):
    for component in application.findall(tag):
        name = component.attrib.get(android + "name", "")
        exported = component.attrib.get(android + "exported") == "true"
        if exported:
            exported_components.append({
                "type": tag,
                "name": name,
                "permission": component.attrib.get(android + "permission", ""),
            })
        actions = {
            item.attrib.get(android + "name", "")
            for intent_filter in component.findall("intent-filter")
            for item in intent_filter.findall("action")
        }
        categories = {
            item.attrib.get(android + "name", "")
            for intent_filter in component.findall("intent-filter")
            for item in intent_filter.findall("category")
        }
        if "android.intent.action.MAIN" in actions and "android.intent.category.LAUNCHER" in categories:
            launcher_components.append({"type": tag, "name": name, "exported": exported})
expected_exported_components = [
    {"type": "activity", "name": "com.parlor.app.MainActivity", "permission": ""},
    {
        "type": "receiver",
        "name": "androidx.profileinstaller.ProfileInstallReceiver",
        "permission": "android.permission.DUMP",
    },
]
if exported_components != expected_exported_components:
    raise SystemExit("AAB exported-component surface differs from the reviewed allowlist")
if launcher_components != [{"type": "activity", "name": "com.parlor.app.MainActivity", "exported": True}]:
    raise SystemExit("AAB launcher entry point differs from the reviewed release entry point")

permissions = {
    node.attrib.get(android + "name", "")
    for node in root.findall("uses-permission")
}
expected_permissions = {
    "android.permission.INTERNET",
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.ACCESS_WIFI_STATE",
    "android.permission.CHANGE_WIFI_MULTICAST_STATE",
    "com.parlor.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
}
if permissions != expected_permissions:
    raise SystemExit("AAB permissions differ from the reviewed allowlist")

entries = [line.strip() for line in entries_path.read_text().splitlines() if line.strip()]
required_release_metadata = {
    "BUNDLE-METADATA/com.android.tools.build.libraries/dependencies.pb",
    "BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map",
    "BUNDLE-METADATA/com.android.tools/r8.json",
}
missing_release_metadata = required_release_metadata - set(entries)
if missing_release_metadata:
    raise SystemExit(f"AAB lacks required release/dependency metadata: {sorted(missing_release_metadata)}")
dex_entries = sorted(item for item in entries if item.startswith("base/dex/") and item.endswith(".dex"))
if not dex_entries:
    raise SystemExit("AAB contains no base DEX")
native_entries = sorted(item for item in entries if item.startswith("base/lib/") and item.endswith(".so"))
abis = sorted({item.split("/")[2] for item in native_entries})
for item in native_entries:
    parts = PurePosixPath(item).parts
    if len(parts) != 4 or parts[2] not in {"arm64-v8a", "armeabi-v7a", "x86", "x86_64"}:
        raise SystemExit("AAB contains an unexpected native-library path")
if any("debug" in part.lower() for item in entries for part in PurePosixPath(item).parts if item.startswith("base/")):
    # Resource names can contain the ordinary word debug; only reject known
    # build-identity namespaces/metadata rather than arbitrary user content.
    forbidden = [item for item in entries if "com.parlor.app.debug" in item.lower() or "/debug/" in item.lower()]
    if forbidden:
        raise SystemExit("AAB contains Debug-identity content")

digest_state = hashlib.sha256()
with aab.open("rb") as stream:
    for chunk in iter(lambda: stream.read(1024 * 1024), b""):
        digest_state.update(chunk)
digest = digest_state.hexdigest()
dependency_bytes = dependency_report.read_bytes()
if not dependency_bytes.strip():
    raise SystemExit("Android release dependency report is empty")
result = {
    "schema_version": 1,
    "artifact_type": "android-app-bundle",
    "application_id": expected_id,
    "version_name": expected_name,
    "version_code": expected_code,
    "min_sdk": min_sdk,
    "target_sdk": target_sdk,
    "debuggable": False,
    "test_only": False,
    "permissions": sorted(permissions),
    "exported_components": exported_components,
    "launcher_components": launcher_components,
    "dex_entries": dex_entries,
    "native_abis": abis,
    "native_library_count": len(native_entries),
    "archive_entry_count": len(entries),
    "release_metadata_entries": sorted(required_release_metadata),
    "dependency_report_sha256": hashlib.sha256(dependency_bytes).hexdigest(),
    "signing_certificate_sha256": certificate,
    "artifact_sha256": digest,
    "size_bytes": aab.stat().st_size,
}
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n")
os.chmod(output, 0o600)
PY

# Inspect all DEX files with the Android build-tools parser. This proves the
# shipping application namespace exists without accepting a filename-only
# assertion. Third-party package namespaces are expected and are recorded by
# the dependency report produced by the candidate workflow.
dexdump_bin="${ANDROID_HOME:-}/build-tools/36.0.0/dexdump"
[[ -x "$dexdump_bin" ]] || { echo "pinned Android dexdump is unavailable" >&2; exit 2; }
found_application_namespace=false
while IFS= read -r dex_entry; do
  [[ -n "$dex_entry" ]] || continue
  dex_path="$temporary_dir/$(basename "$dex_entry")"
  unzip -p "$aab" "$dex_entry" >"$dex_path"
  "$dexdump_bin" -l plain "$dex_path" >"$dex_path.txt"
  if grep -Fq "Class descriptor  : 'Lcom/parlor/app/" "$dex_path.txt"; then
    found_application_namespace=true
  fi
  if grep -Fq "Class descriptor  : 'Lcom/parlor/app/debug/" "$dex_path.txt"; then
    echo "Debug namespace found in release DEX" >&2
    exit 2
  fi
done < <(python3 -c 'import json,sys; print("\n".join(json.load(open(sys.argv[1]))["dex_entries"]))' "$output")
[[ "$found_application_namespace" == true ]] || { echo "release DEX lacks the Parlor application namespace" >&2; exit 2; }
