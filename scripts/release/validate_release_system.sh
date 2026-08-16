#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "$0")/../.." && pwd -P)
cd "$repo_root"

temporary_dir=$(mktemp -d "${TMPDIR:-/tmp}/parlor-release-tools.XXXXXX")
cleanup() {
  local original_status=${1:-$?}
  local cleanup_status=0
  trap - EXIT INT TERM
  if ! rm -rf "$temporary_dir"; then
    echo "could not remove the temporary release-tool directory" >&2
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

python3 -m py_compile scripts/release/*.py
python3 -m unittest discover -s scripts/release/tests -p 'test_*.py' -v
python3 scripts/release/workflow_contract.py

os_name=$(uname -s | tr '[:upper:]' '[:lower:]')
case $(uname -m) in
  x86_64 | amd64) architecture=amd64 ;;
  arm64 | aarch64) architecture=arm64 ;;
  *) echo "unsupported release-tool architecture" >&2; exit 2 ;;
esac
case "$os_name" in
  linux | darwin) ;;
  *) echo "unsupported release-tool operating system" >&2; exit 2 ;;
esac
platform_key="$os_name-$architecture"

download_tool() {
  local tool=$1
  local executable_name=$2
  local archive="$temporary_dir/$tool.tar.gz"
  local destination="$temporary_dir/$executable_name"
  local url
  local digest
  url=$(python3 -c 'import json,sys; print(json.load(open("config/release-policy.json"))["tools"][sys.argv[1]]["artifacts"][sys.argv[2]]["url"])' "$tool" "$platform_key")
  digest=$(python3 -c 'import json,sys; print(json.load(open("config/release-policy.json"))["tools"][sys.argv[1]]["artifacts"][sys.argv[2]]["sha256"])' "$tool" "$platform_key")
  curl --fail --silent --show-error --location \
    --proto '=https' \
    --tlsv1.2 \
    --retry 3 \
    --retry-all-errors \
    --max-filesize 209715200 \
    --max-time 120 \
    "$url" \
    --output "$archive" || return
  printf '%s  %s\n' "$digest" "$archive" | shasum -a 256 -c - >&2 || return
  python3 - "$archive" "$executable_name" "$destination" <<'PY' || return
import os
import sys
import tarfile
from pathlib import PurePosixPath

archive_path, expected_name, destination = sys.argv[1:]
with tarfile.open(archive_path, "r:gz") as archive:
    members = archive.getmembers()
    if len(members) > 100:
        raise SystemExit("release-tool archive has too many entries")
    matches = []
    for member in members:
        path = PurePosixPath(member.name)
        if path.is_absolute() or ".." in path.parts or member.issym() or member.islnk():
            raise SystemExit("unsafe release-tool archive")
        if path.name == expected_name and member.isfile():
            matches.append(member)
    if len(matches) != 1 or matches[0].size > 100 * 1024 * 1024:
        raise SystemExit("release-tool archive does not contain one bounded executable")
    source = archive.extractfile(matches[0])
    if source is None:
        raise SystemExit("cannot read release-tool executable")
    with open(destination, "wb") as output:
        output.write(source.read())
    os.chmod(destination, 0o755)
PY
  printf '%s\n' "$destination"
}

shellcheck_command=$(download_tool shellcheck shellcheck) || exit $?
actionlint_command=$(download_tool actionlint actionlint) || exit $?
"$shellcheck_command" -x scripts/release/*.sh
"$actionlint_command" -shellcheck "$shellcheck_command" .github/workflows/*.yml
