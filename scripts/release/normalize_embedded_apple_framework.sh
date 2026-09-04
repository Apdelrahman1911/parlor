#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "usage: $0 <frameworks-directory> <framework-base-name>" >&2
  exit 2
fi

frameworks_directory=$1
framework_base_name=$2
canonical_name="${framework_base_name}.framework"
canonical_lowercase=$(printf '%s' "$canonical_name" | tr '[:upper:]' '[:lower:]')

[[ -d "$frameworks_directory" ]] || {
  echo "embedded frameworks directory does not exist: $frameworks_directory" >&2
  exit 2
}

match=
match_count=0
for candidate in "$frameworks_directory"/*.framework; do
  [[ -d "$candidate" ]] || continue
  candidate_name=${candidate##*/}
  candidate_lowercase=$(printf '%s' "$candidate_name" | tr '[:upper:]' '[:lower:]')
  if [[ "$candidate_lowercase" == "$canonical_lowercase" ]]; then
    match=$candidate
    match_count=$((match_count + 1))
  fi
done

[[ $match_count -eq 1 ]] || {
  echo "expected exactly one case-insensitive $canonical_name in $frameworks_directory" >&2
  exit 2
}

if [[ ${match##*/} != "$canonical_name" ]]; then
  temporary_framework="$frameworks_directory/.${canonical_name}.case-normalization"
  [[ ! -e "$temporary_framework" ]] || {
    echo "stale framework case-normalization path exists: $temporary_framework" >&2
    exit 2
  }
  /bin/mv "$match" "$temporary_framework"
  /bin/mv "$temporary_framework" "$frameworks_directory/$canonical_name"
fi

[[ -f "$frameworks_directory/$canonical_name/$framework_base_name" ]] || {
  echo "embedded framework executable does not match its Mach-O install name" >&2
  exit 2
}
