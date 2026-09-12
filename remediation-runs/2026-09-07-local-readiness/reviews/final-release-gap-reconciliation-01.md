# Final release/configuration gap reconciliation

Reviewer: `/root/release_fix_review`. Bounded source and evidence reconciliation, **not a new full-project audit or release approval**.

## Exact source

`fix/local-readiness-2026-09-07` at `f5ea8045bd903c255041b198f54aca67e893612f`, Git tree `b109c8ec6ec579b9197f449b36bda2c39105a652`. Tracked-clean; all existing untracked audit/design/verification material preserved. The accompanying JSON binds reviewed file ranges/hashes and original receipts.

## Completed evidence that must not remain marked pending

- **RL-C1/C2/C3 and IOS-B1:** current executable/test files still match the successful `combined-production-04` input manifest. Reopened portable file-size checks, approved self-signed upload-certificate validation, exact same-edit Play promotion, Xcode fail-fast embedding and their test bodies. Raw unittest evidence has **172 executed tests / OK**, including **7 size, 21 signature/cleanup, 9 promotion, 5 shell-phase and 3 normalization tests**. This rebind is not a new full-tree run.
- **Timestamp-positive:** a real RFC3161 timestamped synthetic JAR passed the **exact mandatory strict production signature fragment**; wrong fingerprint and modified timestamped payload each failed. Current helper/script hashes match. Independent `native_fix_review` approved the raw result. This is not a complete AAB, real upload key or Store test; no repeat network/signing request is needed to fill the old positive gap.
- **Final story technical verification:** all six final edited source/doc/test hashes and all seven case resources match GREEN13. Independently parsed **65 retained XML suites / 379 actual cases / zero failures, errors or skips**: 356 Whodunit +23 provenance/metadata. Compiled Kotlin canonicalization produced **13 retired +7 current identities**. Jasmine is **1.0.3**, Iskenderia **1.0.2**; old saves remain retained/rejected, never silently rebound. Final editorial decisions are pre-release testing choices, not legal/human production approval.
- **Inventory:** current CSV contains exactly **683 tracked paths**, all explicitly `INDEPENDENT REVIEW NOT ATTESTED`. The old regeneration-pending note is superseded. Filename equality is not execution of `--check` or source-review coverage.
- Earlier WD-C4 decision and retry-producer gaps have newer dedicated evidence: excluded Leave-confirmation time is documented intended behavior with acceptance tests; retry has nine actual recovery-UI cases and is not a confirmed shipping defect. Do not copy old unresolved labels blindly.

## Next gates, in the one root-owned lane

| Gate | Classification / exact action |
|---|---|
| Current combined source | **Locally executable, pending final freeze/run:** `./gradlew productionCheck --dependency-verification=strict --no-daemon --no-parallel --max-workers=1 --no-configuration-cache --rerun-tasks --no-build-cache`. Old broad PASS predates current story/UI/test/inventory changes. |
| Current release tooling | Included above; optional focused `productionReleaseAutomationCheck`. `/usr/bin/python3 -B scripts/generate_review_inventory.py --check` verifies freshness only. Generator/test changes and the new documentation test need current combined evidence. |
| Actual Linux portability | Root-owned isolated Linux: `python3 -B -m unittest discover -s scripts/release/tests -p test_android_artifact_size.py -v`, then appropriate release aggregate. macOS GNU-shaped fixtures do not establish Linux execution. VZ pure-control PASS is not a VM result. |
| Apple frameworks/runtime | `./gradlew productionAppleCheck productionIosSimulatorRuntimeTests --dependency-verification=strict --no-daemon`. Use owned simulator/lane controls. Installed **26.5/17F42** is not qualified **26.3/17C529**; missing exact Xcode is a toolchain/executor gap, not a credentials-only gate. |
| Swift Release wrapper | A previous unsigned ARM64 simulator Release build passed. Rerun at final tree through the owned wrapper controls; exact `xcodebuild` command is in JSON/`docs/IOS_SETUP.md:212–225`. Framework linking alone does not compile the Swift wrapper or prove app runtime. |
| iOS investigation | `ios-readiness02` remains **FAIL**: 4 XCTest passes, 1 evidence-harness JSON-unwrapping failure, no skips. `ios-readiness03` was **RUNNING** when inspected; root/native reviewer own final outcome. Neither the failed harness nor an earlier Keychain status diagnoses the historic launch crashes. |
| Android managed runtime | `./gradlew productionAndroidRuntimeCheck --dependency-verification=strict --no-daemon --no-configuration-cache` on the policy's **Pixel2/API35/google_apis/x86_64 revision9** applicable executor, with an owned synthetic key/device. An ARM64 macOS emulator is useful additional evidence, not this exact KVM gate. |

All commands above are **proposals, not newly executed receipts**. Root should avoid duplicate tasks already covered in its final aggregate. Stop Gradle immediately after each actual cycle, collect compact required evidence, clean attested owned outputs/DerivedData/emulator resources, and stop again if cleanup starts Gradle.

## Actual external/protected requirements

- Both canonical Store identities remain explicitly **blocked/public_store_collision**. `release_tool.py` rejects `com.parlor.app` even if the status flag is changed. All **11** Store-workflow job conditions remain disabled. No identity replacement, workflow enablement, credential use, signing, upload or promotion is authorized by this review.
- Actual signed artifact/fingerprint/profile/team proof, protected environment/branch-rule state, owner-controlled Store records and readback are not supplied by synthetic tests. `productionAndroidSigningCheck` alone is not full certificate/Store validation.
- Production editorial/Arabic playtesting, content and dependency rights, product-license/trademark/export review, owner URLs and truthful Store declarations/screenshots remain required. Current manifests are source declarations, not final signed-artifact or legal attestations.
- Physical LAN/permission/rejoin/radio/lifecycle/app-switcher/backup evidence and native accessibility/large-text/performance work remain separate. Desktop distribution signing/notarization is **not applicable** to this mobile-shipping/development-Desktop contract.

### Font candidate: counter-evidence, not a new defect

Both Inter/JetBrains fonts preserve embedded notices and OFL links; previous Android release-APK and iOS Debug-bundle inspections proved exact font-byte preservation in those artifacts. Official **OFL FAQ §1.10** expressly allows link-only metadata for a font bundled in a program, while recommending full text. Therefore absence of a standalone OFL file does **not** establish the suspected defect. Root owns the final independent rejected-register disposition. This does not close general dependency/legal approval or final Store-artifact inspection.

## Preservation

No production/Git/Store changes, builds, tests, VMs or persistent workers were started by this bounded task. Only this report and its JSON were created. No source, global cache, existing evidence, retained APK or root-owned running task was cleaned or stopped. **No whole-project percentage or READY verdict is supported by this reconciliation alone.**
