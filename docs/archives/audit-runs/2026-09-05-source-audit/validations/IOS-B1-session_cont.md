# IOS-B1 — independent validation of swallowed Xcode framework-build failure

**CONFIRMED DEFECT — Medium.** Finder `/root`; independent validator `/root/session_cont`. Exact source: `main` `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`; no tracked modifications. Absolute root `/Users/abdelrahman/Projects/parlor/`.

## Reachable cause and impact

`iosApp/iosApp.xcodeproj/project.pbxproj:132–149` attaches the Compile Kotlin Framework phase to the actual app before Sources/Resources/Frameworks. Its `:228–246` script is `/bin/sh`, changes to the repository, optionally returns0 for the explicit IDE override, then runs the strict Gradle embed task and the normalizer as separate commands. No `set -e`, `&&`, or checked exit propagates Gradle failure. The normalizer's own `set -euo pipefail` applies only to that child process, not its caller.

`scripts/release/normalize_embedded_apple_framework.sh:14–49` succeeds when the embedded output directory already contains exactly one case-matching ComposeApp.framework and a regular ComposeApp file. It does not require that the preceding build succeeded or that bytes belong to this source. Thus a prior incremental build's embedded framework can make this phase return0 after a new Gradle invocation fails. Actual target configurations retain framework search paths in composeApp/build/xcode-frameworks (`project.pbxproj:409–412,439–442`); the shared scheme23–38,58–102 builds/runs/archives that app. With valid stale outputs, later Xcode phases may continue or run old Kotlin code. This is build failure/source-artifact trust loss, not a deterministic assertion that every such app build or launch succeeds.

## Native reproduction independently inspected

Root executed `reproducers/run_platform_witnesses.py` mode `xcode-phase`. I read the entire152-line runner, copied inline script, generated Xcode shell script, receipt and relevant raw log. The witness parses the real project, copies the unchanged phase and real normalizer into a fresh synthetic PBXAggregateTarget, substitutes a deliberately failing wrapper (`exit42`), and supplies a non-executable synthetic stale framework placeholder. No app/private data/signing artifact was used.

`evidence/native-xcode-phase-01/receipt.json` binds the actual project SHA256 and script bytes. Installed **Xcode26.5 /17F42** logs `/bin/sh -c <generated-script>` with no implicit `-e`, then `AUDIT synthetic Gradle failure (exit42)` and `BUILD SUCCEEDED`; xcodebuild exit0. This proves the important counter-hypothesis (Xcode automatically aborts on an intermediate shell error) false in the actual local toolchain. Generated script contains only `#!/bin/sh` plus the unchanged inline body. Failure propagation requirement FAIL; the synthetic-build command itself returning0 is the defect, not a successful app verification.

## Counter-evidence and limits

- Fresh outputs or missing/malformed framework cause the normalizer to fail, so not every Gradle failure is masked. The signed candidate helper's fresh archive/export/DerivedData guard (`scripts/release/build_ios_candidate.sh:166–176`) reduces the stale-embedded prerequisite; it does not add failure propagation to this project phase. Current Store identity guard and disabled candidate jobs are independent barriers.
- Outer CI `set -euo pipefail` around xcodebuild cannot detect a phase that itself returns0. Existing tests check invocation strings and normalizer behavior (`ProductionVerificationWorkflowContractTest.kt:296–471`; normalizer tests1–64) but do not inject a failed producer into the whole phase.
- The IDE override explicitly requests skipping Gradle; the reproducer sets itNO. This finding concerns unintended skipping after failure, not that intended override.
- Store-qualified Xcode26.3/17C529 was **not** run; native reproduction uses26.5. No signed archive, iOS simulator/device runtime, or prior four-launch-crash cause is proved. Upstream SwiftBuild source below is corroborative current source, not a claimed mapping to26.3 binaries.

## Authoritative research

Accessed2026-09-05, URLs/hashes in `research/xcode-phase-session_cont/fetches.json`:
- Apple `https://developer.apple.com/documentation/xcode/running-custom-scripts-during-a-build` (official DocC JSON retained) specifies that a failing script must return nonzero and Xcode treats nonzero as failure.
- Official SwiftBuild at frozen commit `b2e24c9a875bc38370c9da36ea25bc2877522540`, `Sources/SWBTaskConstruction/TaskProducers/BuildPhaseTaskProducers/ShellScriptTaskProducer.swift:189–204,281–300`, writes the configured shebang + body and executes `/bin/sh -c` without `-e`; `ShellScriptTool.swift:23–49` passes that command through. These implementation excerpts agree with the local observed native invocation. Unrelated file-list logic and all possible upstream versions were not reviewed as part of this candidate.

## Recommended fix/tests (not implemented)

After authorization, make directory change and Gradle failure unconditionally abort the phase; normalize only after successful embedding. Use compatible shell failure propagation rather than assuming a child script's options affect its caller. Preserve strict dependency verification/explicit IDE behavior. Add isolated phase tests for failing Gradle plus stale valid output (must fail), failed cd, missing output, successful build, canonical/lowercase normalizer behavior, and intentional IDE override. Recheck actual qualified Xcode26.3 shell behavior and a real unsigned iOS wrapper build without mistaking either for device/Store evidence.

Medium is justified by routine local incremental-build correctness/verification impact; no credential exposure, rules change, shipping crash, or Store publication is established.

## Hygiene

Native receipt reports Gradle stop exit0 (“No Gradle daemons are running”), exact temporary workspace/DerivedData removed and tracked status unchanged. This independent reviewer ran no build/test/app/server and created only audit research/review/coverage files. No app code or release configuration changed.
