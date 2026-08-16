#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "usage: $0 <ipa> <private-key.p8> <api-key-id> <issuer-id> <receipt.json> <raw-log>" >&2
  exit 64
}

[[ $# -eq 6 ]] || usage

repo_root=$(cd "$(dirname "$0")/../.." && pwd -P)
python3 "$repo_root/scripts/release/release_tool.py" \
  assert-store-identity-approved --platform ios

ipa=$1
private_key=$2
key_id=$3
issuer_id=$4
receipt=$5
raw_log=$6

[[ -f "$ipa" && ! -L "$ipa" ]] || { echo "invalid IPA" >&2; exit 2; }
[[ -f "$private_key" && ! -L "$private_key" ]] || { echo "invalid App Store Connect key" >&2; exit 2; }
[[ "$key_id" =~ ^[A-Z0-9]{10}$ ]] || { echo "invalid App Store Connect key ID" >&2; exit 2; }
[[ "$issuer_id" =~ ^[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}$ ]] || { echo "invalid App Store Connect issuer ID" >&2; exit 2; }

temporary_dir=$(mktemp -d "${RUNNER_TEMP:-${TMPDIR:-/tmp}}/parlor-apple-upload.XXXXXX")
raw_log_created=false
cleanup() {
  local original_status=${1:-$?}
  local cleanup_status=0
  trap - EXIT INT TERM
  if [[ "$raw_log_created" == true && -e "$raw_log" ]] && ! rm -f "$raw_log"; then
    echo "could not remove the protected Apple upload response" >&2
    cleanup_status=2
  fi
  if ! rm -rf "$temporary_dir"; then
    echo "could not remove the temporary Apple upload directory" >&2
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

# Apple documents API_PRIVATE_KEYS_DIR as the explicit non-default key lookup
# path. Keep the runner's HOME and user configuration untouched.
export API_PRIVATE_KEYS_DIR="$temporary_dir/private_keys"
key_directory=$API_PRIVATE_KEYS_DIR
mkdir -p "$key_directory"
install -m 600 "$private_key" "$key_directory/AuthKey_$key_id.p8"

mkdir -p "$(dirname "$receipt")" "$(dirname "$raw_log")"
[[ ! -e "$receipt" && ! -L "$receipt" ]] || { echo "refusing to overwrite an Apple upload receipt" >&2; exit 2; }
[[ ! -e "$raw_log" && ! -L "$raw_log" ]] || { echo "refusing to overwrite an Apple upload response" >&2; exit 2; }
set +e
raw_log_created=true
xcrun altool \
  --upload-app \
  --file "$ipa" \
  --type ios \
  --apiKey "$key_id" \
  --apiIssuer "$issuer_id" \
  --output-format json >"$raw_log" 2>&1
status=$?
set -e
[[ $status -eq 0 ]] || { echo "App Store Connect upload failed; its raw response was not published" >&2; exit "$status"; }

python3 - "$raw_log" "$ipa" "$receipt" <<'PY'
import hashlib
import json
import os
import re
import sys
from pathlib import Path

log_path = Path(sys.argv[1])
ipa_path = Path(sys.argv[2])
receipt_path = Path(sys.argv[3])
raw = log_path.read_bytes()
if len(raw) > 4 * 1024 * 1024:
    raise SystemExit("altool response exceeds the 4 MiB safety limit")
try:
    parsed = json.loads(raw)
except json.JSONDecodeError:
    raise SystemExit("altool did not return valid JSON")
if not isinstance(parsed, dict):
    raise SystemExit("altool returned a non-object JSON response")
text = raw.decode("utf-8", errors="replace")
uuids = sorted(set(re.findall(r"\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\b", text)))
if len(uuids) != 1:
    raise SystemExit("altool upload did not return exactly one unambiguous delivery identifier")
ipa_digest = hashlib.sha256()
with ipa_path.open("rb") as stream:
    for chunk in iter(lambda: stream.read(1024 * 1024), b""):
        ipa_digest.update(chunk)
result = {
    "schema_version": 1,
    "transport": "xcrun-altool",
    "accepted": True,
    "upload_request_id": uuids[0],
    "response_sha256": hashlib.sha256(raw).hexdigest(),
    "artifact_sha256": ipa_digest.hexdigest(),
}
receipt_path.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n")
os.chmod(receipt_path, 0o600)
PY
