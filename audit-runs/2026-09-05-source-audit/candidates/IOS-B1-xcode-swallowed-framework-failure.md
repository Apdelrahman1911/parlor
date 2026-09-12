# IOS-B1 — Xcode can continue after the Kotlin framework build fails

**CONFIRMED DEFECT — Medium.** Finder `/root`; independent validator `/root/session_cont`.
Newly discovered in this audit; not evidence explaining historical repeated launch crashes.
Source main `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree
`db7f3d2afe73a13628296daee2cce71165eebc8d`. No tracked changes.

## Location, reachability and impact

- `/Users/abdelrahman/Projects/parlor/iosApp/iosApp.xcodeproj/project.pbxproj:132–149,228–246`.
  SHA256 `b3719c640b8d9f588d0ccb8b2cf2f4343216d328ec967d04873ff03198d58731`.
- `/Users/abdelrahman/Projects/parlor/scripts/release/normalize_embedded_apple_framework.sh:1–49`.

The actual app's Compile Kotlin Framework phase runs `/bin/sh` without error propagation, invokes
strict Gradle embedding, then separately invokes a normalizer. When an earlier incremental build left
a structurally valid embedded framework, that normalizer can return0 even after Gradle failed. The
phase reports success and later Xcode work can continue using old Kotlin output. Expected: a failed
required producer must fail the build, regardless of stale files. Apple documents nonzero script status
as the build failure signal. This is iOS development/build evidence integrity, not a proved shipped crash.

## Native witness

`reproducers/run_platform_witnesses.py` copied the unchanged inline phase and real normalizer into
a fresh synthetic Xcode aggregate target, set the IDE overrideNO, substituted a wrapper exiting42 and
provided a synthetic stale framework placeholder. Under Xcode26.5/17F42, native xcodebuild logged
the intentional Gradle failure and `BUILD SUCCEEDED`, exit0. Its generated script has no implicit `-e`.
Evidence: `evidence/native-xcode-phase-01/{receipt.json,xcodebuild.log}` and retained generated script.
No real app compiled/launched and no signing material or private data was accessed.

## Counter-evidence and recommendation

Fresh/missing output makes normalization fail; not every Gradle failure is masked. Candidate tooling
uses fresh archive directories and current Store guards remain disabled/blocked; no publication bypass
is established. The explicit IDE skip is intentional and not the tested failure. A child normalizer's
`set -euo pipefail` cannot change the caller shell's behavior.

After authorization, propagate failure from cd/Gradle before normalization, preserving strict dependency
verification and intended IDE handling. Test failed Gradle with stale valid output, missing output,
failed cd, successful producer, case normalization and deliberate skip. Rerun the real wrapper build
on qualified Xcode26.3 as a separate gate;26.5 evidence cannot establish26.3 runtime/signing behavior.

Independent verdict, reopened source/callers/counter-evidence and authoritative references:
`validations/IOS-B1-session_cont.md` and `research/xcode-phase-session_cont/fetches.json`.
Native witness stop0; task workspace/DerivedData deleted; no application edits or lingering build process.
