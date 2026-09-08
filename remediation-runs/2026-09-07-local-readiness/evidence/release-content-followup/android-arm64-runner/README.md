# Owned Android ARM64 runtime draft

**Unexecuted draft; independent review and root build-lane approval required.**
It runs the existing Release instrumentation tests, not new mock substitutes.
It cannot accept an existing AVD or a physical-device serial. The cold-start
test deliberately resets the app's settings, so never transplant its commands
to a personal phone/profile.

## Prerequisites and evidence boundary

- Installed public SDK API35 Google APIs ARM64 image revision9, emulator and adb.
- Installed `avdmanager` (observed Homebrew command-line tools20.0).
- JDK21 on PATH/JAVA_HOME for avdmanager; no SDK/image download in this runner.
- Two already-built APKs from one recorded dirty-source freeze: real R8 Release
  target plus Release AndroidTest, signed only with the same disposable key.
- About2GiB emulator RAM plus rendering/native overhead, one build/runtime lane.
- Reports must accompany the source freeze and Gradle build receipt. A commit
  argument or APK SHA alone does not prove which dirty source was compiled.

This is macOS-hosted ARM64 emulator evidence. It is not the separate pinned
Linux/x86_64/KVM managed-device gate, physical LAN, accessibility certification,
production signing, or Google Play approval.

## Root lane: review/check before runtime

```bash
runner_dir=remediation-runs/2026-09-07-local-readiness/evidence/release-content-followup/android-arm64-runner
/usr/bin/python3 -B -m unittest discover -s "$runner_dir" -p 'test_*.py' -v
```

The41 pure-host tests start no emulator/server/build. They use synthetic process
tables/syscalls and bound, non-listening temporary Unix socket nodes. Preserve their real result;
they do not establish runtime success. Use `-B` to avoid `__pycache__` outputs.

Root should inspect actual task existence before building
`:composeApp:assembleRelease :composeApp:assembleReleaseAndroidTest` with strict
verification, JDK21 and ephemeral injected signing arguments. Do not pass real
Store credentials, enable release workflows or change application identities.
Immediately stop Gradle, copy only the two APKs and required source/log/report
receipts to an owned runtime staging directory, then clean all build roots
owned by that Gradle cycle. Remove the ephemeral keystore after APK inspection.

## Root lane: explicit runtime invocation

```bash
/usr/bin/python3 -B "$runner_dir/owned_arm64_smoke.py" \
  --sdk-root "$HOME/Library/Android/sdk" \
  --avdmanager /opt/homebrew/bin/avdmanager \
  --app-apk /absolute/task-owned/release.apk \
  --test-apk /absolute/task-owned/release-androidTest.apk \
  --source-commit "$(git rev-parse HEAD)" \
  --evidence-dir /absolute/new-task-owned/runtime-receipt
```

The runner creates a private temporary AVD/HOME/Android configuration and an
exclusive ADB socket. USB, mDNS auto-connection, and global emulator scanning
are disabled. Only the owned endpoint is contacted. It attests API/ABI/emulator
and AVD name before installing or running destructive fixtures. It runs the
cold-start class first and the two smoke methods in a separate instrumentation
process, requiring all3 exact start/completion descriptors, not just exit0.

Finally it signals only lifetime-attested workers using Darwin's kernel-bound
audit-token API. `waitid(WNOWAIT)` retains a direct child's PID until descendant
capture/retirement; no reaped/historical group can authorize later adoption.
The runner uses **no numeric `kill`/`killpg` fallback**. Known descendants remain
tracked after reparenting, and foreground commands may not leave daemons.
Root must verify the public ABI on real disposable child processes before an
emulator run; the41 synthetic tests do not prove native-API runtime behavior.

ADB cleanup writes one framed `host:kill` to the inode/UID-attested private Unix
socket; it cannot execute/autostart an ADB server. Cleanup then checks endpoint
closure and task-worker retirement and removes only the inode/UID/token-attested
temporary tree. Any cleanup failure preserves that tree and fails the receipt.
Review compact logs, token-signal receipts, worker statuses, and removal result;
remove staged APKs after final inspection and run required Gradle-stop verification.

## Known unexecuted assumptions to resolve, not hide

- AVD creation from Homebrew command tools against the separate installed SDK.
- Current emulator's owned socket/legacy-port behavior and reported AVD-name
  property. Failure must stop before fixture execution; do not fall back to a
  user's5037 server or existing AVD/device.
- Exact instrumentation APK target/component and actual3-method discovery.
- Renderer support for `swiftshader` (advertised by installed emulator help).
- Public Darwin26 process ABI: native read/token/non-reaping wait/signal behavior
  must run on owned synthetic children before claiming actual worker safety.
- Observed detached descendants are retained, but periodic process discovery is
  not kernel-wide process tracing. Short-lived double-fork/reparent chains can
  escape ancestry sampling. Group grants persist through direct-child retirement,
  foreground daemons are rejected, and the separate owned socket stops an ADB
  server; runtime review must still investigate any untracked native helper.

Official upstream ADB/Lima source references and observed tool versions are in
the adjacent `research-and-download-proposals.json` and environment receipt.
The ADB reference is upstream main, not a proven match to every line in installed
platform-tools37.0.0; runtime attestation and independent review remain required.
`darwin-*-source-research.json` binds public SDK claims to official XNU
`xnu-12377.61.12` (the current host kernel tag). In particular libproc's group/
child-list wrappers return PID **counts**, unlike `proc_listpids`' byte count.
The wrappers return zero with a retained nonzero `errno` on native failure;
that result fails cleanup rather than being accepted as an empty process list.
Only zero with cleared `errno` is a legitimate empty result. The original
37-test receipt/freeze remains historical; the four added checks and revised
controls require a new independent review and execution receipt.
`adb-*-research.json` records the official framing and kill handler, including
the corrected documentation path after a retained404 research attempt.
