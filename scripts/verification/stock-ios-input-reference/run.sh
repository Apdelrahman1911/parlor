#!/bin/bash
# Standalone UIKit reference, NOT Parlor/application qualification.
set -euo pipefail
: "${RUNNER_TEMP:?}" "${GITHUB_SHA:?}" "${GITHUB_RUN_ID:?}" "${GITHUB_RUN_ATTEMPT:?}"
root="$(git rev-parse --show-toplevel)"
fixture="$root/scripts/verification/stock-ios-input-reference"
out="$RUNNER_TEMP/stock-input-$GITHUB_RUN_ID-$GITHUB_RUN_ATTEMPT"
mkdir "$out"
export STOCK_REFERENCE_OUTPUT="$out"
[[ "$(uname -m)" == arm64 ]]
[[ "$(xcodebuild -version)" == $'Xcode 26.3\nBuild version 17C529' ]]
[[ "$(xcrun --sdk iphonesimulator --show-sdk-version)" == 26.2 ]]
[[ "$(git rev-parse HEAD)" == "$GITHUB_SHA" ]]
[[ "$(shasum -a 256 "$root/AGENTS.md" | cut -d ' ' -f1)" == d9505fa9e8338a8a1ca85a63e87ffc72a4a0013468116f6f6d7a5c4c7dfbc02b ]]
git rev-parse HEAD HEAD^{tree} > "$out/source.txt"
xcodebuild -version > "$out/xcode.txt"
xcrun --sdk iphonesimulator --show-sdk-version > "$out/sdk.txt"
xcrun simctl list -j > "$out/simulators-before.json"
hash_tree() {
  /usr/bin/python3 - "$1" <<'PY'
from pathlib import Path
import hashlib, os, stat, sys
root = Path(sys.argv[1]).resolve(strict=True)
for path in sorted(root.rglob('*')):
    mode = path.lstat().st_mode
    name = path.relative_to(root).as_posix()
    if stat.S_ISLNK(mode):
        # Preserve link identity; never read an external target or follow directories.
        path.resolve(strict=True).relative_to(root)
        raw = os.fsencode(os.readlink(path))
        print(hashlib.sha256(raw).hexdigest(), 'symlink', name)
    elif stat.S_ISREG(mode):
        print(hashlib.sha256(path.read_bytes()).hexdigest(), 'file', name)
    elif not stat.S_ISDIR(mode):
        raise ValueError('Nonregular product/input entry')
PY
}
hash_tree "$fixture" > "$out/inputs.sha256"
shasum -a 256 "$root/config/parlor-version.xcconfig" > "$out/version-input.sha256"
udid=""
cleanup() {
  local result=$? shutdown=unavailable deletion=unavailable
  trap - EXIT
  if [[ -n "$udid" ]]; then
    set +e
    xcrun simctl shutdown "$udid" > "$out/shutdown.log" 2>&1
    shutdown=$?
    xcrun simctl delete "$udid" > "$out/delete.log" 2>&1
    deletion=$?
    xcrun simctl list -j > "$out/simulators-after.json"
    set -e
  fi
  printf 'workflow_exit=%s\nshutdown_exit=%s\ndelete_exit=%s\nudid=%s\n' \
    "$result" "$shutdown" "$deletion" "$udid" > "$out/cleanup.txt"
  # Cleanup cannot convert a failed test into success. Failure is not qualified.
  if [[ "$result" == 0 && ( "$shutdown" != 0 || "$deletion" != 0 ) ]]; then result=1; fi
  exit "$result"
}
trap cleanup EXIT
udid="$(xcrun simctl create "parlor-stock-$GITHUB_RUN_ID-$GITHUB_RUN_ATTEMPT" \
  com.apple.CoreSimulator.SimDeviceType.iPhone-17-Pro com.apple.CoreSimulator.SimRuntime.iOS-26-2)"
[[ "$udid" =~ ^[0-9A-F]{8}-([0-9A-F]{4}-){3}[0-9A-F]{12}$ ]]
printf '%s\n' "$udid" > "$out/owned-simulator.txt"
args=(-project "$fixture/StockInput.xcodeproj" -scheme StockInput -configuration Debug
  -sdk iphonesimulator -destination "platform=iOS Simulator,id=$udid"
  -derivedDataPath "$out/DerivedData" -parallel-testing-enabled NO
  -maximum-concurrent-test-simulator-destinations 1 -jobs 1)
printf '%q ' xcodebuild "${args[@]}" build-for-testing > "$out/build-command.txt"
printf '\n' >> "$out/build-command.txt"
set +e
xcodebuild "${args[@]}" build-for-testing 2>&1 | tee "$out/build.log"
status=("${PIPESTATUS[@]}")
set -e
printf '%s\n' "${status[0]}" > "$out/build.exit"
[[ "${status[0]}" == 0 && "${status[1]}" == 0 ]]
hash_tree "$out/DerivedData/Build/Products" > "$out/products.sha256"
xcrun simctl boot "$udid"
xcrun simctl bootstatus "$udid" -b
xcrun simctl list -j > "$out/simulators-at-test.json"
printf '%q ' xcodebuild "${args[@]}" -resultBundlePath "$out/tests.xcresult" test-without-building \
  > "$out/test-command.txt"
printf '\n' >> "$out/test-command.txt"
set +e
xcodebuild "${args[@]}" -resultBundlePath "$out/tests.xcresult" test-without-building \
  2>&1 | tee "$out/test.log"
status=("${PIPESTATUS[@]}")
set -e
printf '%s\n' "${status[0]}" > "$out/test.exit"
# Collection/admission has its own status; never replace the retained XCTest exit.
set +e
xcrun xcresulttool get test-results summary --path "$out/tests.xcresult" > "$out/summary.json" 2> "$out/summary-export.log"
summary_status=$?
xcrun xcresulttool get test-results tests --path "$out/tests.xcresult" > "$out/tests.json" 2> "$out/tests-export.log"
tests_status=$?
xcrun xcresulttool export attachments --path "$out/tests.xcresult" --output-path "$out/attachments" > "$out/attachments-export.log" 2>&1
attachments_status=$?
printf 'summary=%s\ntests=%s\nattachments=%s\n' "$summary_status" "$tests_status" "$attachments_status" > "$out/collection.txt"
/usr/bin/python3 "$fixture/verify_result.py" "$out" "$udid" > "$out/admission.log" 2>&1
admission_status=$?
printf '%s\n' "$admission_status" > "$out/admission.exit"
set -e
# Actual failed assertions remain failed, even if collection also failed.
if [[ "${status[0]}" != 0 ]]; then exit "${status[0]}"; fi
[[ "${status[1]}" == 0 && "$summary_status" == 0 && "$tests_status" == 0 && "$attachments_status" == 0 && "$admission_status" == 0 ]]
exit 0
