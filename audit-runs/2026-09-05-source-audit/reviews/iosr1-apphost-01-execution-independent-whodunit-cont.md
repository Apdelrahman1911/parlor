# Independent IOS-R1 app-host cycle01 execution review

Reviewer: `/root/whodunit_cont`; executor: `/root`. Source: `main`, commit `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`. Evidence base: `evidence/iosr1-apphost-01/`.

## Result and classification

**AUDIT HARNESS BUILD-CONFIGURATION FAILURE; not evidence of an application crash or runtime defect.** Root's runner exited 1. Framework compilation/linking succeeded with strict dependency verification, but copied Xcode framework embedding failed before XCTest could run. Runtime source attribution remains unexecuted in this cycle.

| Gate | Evidence-backed result |
| --- | --- |
| Source/input binding | PASS: all 12 execution input hashes independently recomputed and archived; source commit/tree/status match |
| Copied-wrapper binding | PASS: all 18 allowlisted copied files reconstructed in memory with archived exact patch/templates and actual receipt path; hashes match execution manifest |
| Diagnostic Kotlin framework compile/link | PASS: `framework.log:1–211`; 86 executed/4 up-to-date Gradle tasks; no runtime implication |
| Generated probe export | PASS for declaration inspection only: actual void callback and Swift names retained in `probe-export.h:1–14` |
| Owned simulator creation/boot | PASS: exact UUID `4929DE7C-346D-4143-95B3-D200F9B7ACA3`, iPhone 17 Pro, iOS 26.5 ARM64; boot log reaches terminal completion |
| Xcode copied embedding/build | FAIL: exit 65; nested Gradle embedding exits 1 with `codesign` reporting `: no identity found` |
| Actual XCTest execution | BLOCKED by the build failure: zero test cases, zero passed/failed/skipped, result `unknown`, empty `testNodes` |
| First Home warning/source-attributed recovery/native status | BLOCKED: no executed app-host probe or recorded runtime result from this cycle |
| Task-owned cleanup | PASS: independently checked exact paths and all recorded task PIDs after root announced cleanup |

The decisive build failure is `xcodebuild.log:1088–1119`; cancellation of testing because the build failed is at `1126–1136`. `embedded-gradle-stop.txt` records `build_exit=1`, `stop_exit=0`. The zero-count summary is not a successful test suite. The runner's later validation error describes missing successful XCTest evidence, not an app exception.

## Root cause and counter-evidence

Executed runner `6922206db73a2b5e9d01f6fab5d5b63a2983f44a0bf54d5beb2e2d06ee3f7262` (609 lines) explicitly inserted an empty `EXPANDED_CODE_SIGN_IDENTITY` into its whitelisted environment. Exact Kotlin Gradle Plugin 2.4.10 source treats any non-null string, including empty, as an instruction to append `codesign --sign` during embedding. `CODE_SIGNING_ALLOWED=NO` does not guard that callback. Independent source/cache verification and the narrow retry analysis are in `reviews/iosr1-runner-retry-independent-whodunit-cont.md`.

This was introduced in the isolated audit runner, not a production patch. Framework success and the generated header disprove a failure before Kotlin linkage but do not establish successful Swift linkage, app launch, Keychain health, or any missing-entitlement status. No physical-device or Store conclusion follows.

## Cleanup independently checked

Receipt: `evidence/iosr1-apphost-01-cleanup-independent-whodunit-cont.json`, timestamp `2026-09-05T14:04:34.560959Z`.

- Targeted `ps` for the 35 recorded task PIDs found none. No unrelated process was signalled.
- All 17 enumerated potential module/build-logic/root/iOS generated-output paths were absent; the cycle removed 15 that actually existed.
- Raw/canonical task temporary directory and exact owned simulator directory were absent. Recorded metadata progresses Booted → Shutdown → no owned UUID.
- Framework and Xcode stop commands started 0.000888 and 0.000726 seconds after their generating stages finished; both exited 0. The copied nested Gradle stop and final stop also succeeded.
- Shared global Gradle cache/wrapper targets still existed. Tracked working-tree status remained empty; no application/configuration/signing material was changed.

## Evidence scope and preservation

All 12 inputs are archived under `evidence/iosr1-runner-independent-whodunit-cont/cycle01-reviewed-inputs/`; their receipt is `cycle01-input-snapshot-receipt.json`. Root separately retained the exact executed runner and mapping in the cycle evidence directory. All 18 copy hashes were independently reconstructed, not merely trusted from the manifest: `cycle01-copied-wrapper-reconstruction.json`.

I read the complete framework log, receipt, input manifest, copied diff, XCTest summary/tree, copied nested-stop receipt, actual 14-line export excerpt, boot log, and small ownership/stop metadata. I inspected only lines 1–175 and 900–1136 of the 1136-line Xcode log plus the explicitly searched signing-setting matches; lines 176–899 are not claimed fully read. The entire 1846-line generated header was hashed, but only the probe declaration was semantically inspected. These generated-evidence limits do not become application-source coverage claims.

No build or device command was run by this reviewer. Only post-cleanup targeted read-only PID/path checks, source inspection, and pure in-memory copy reconstruction were performed. Needed compact evidence is retained. The retry requires independent execution/cleanup review; it cannot inherit a runtime PASS from this failed cycle.
