# Release/configuration/owner-gate map — follow-up 01

Reviewer: `/root/release_fix_review`; 2026-09-07. Source anchor: `1f809b87c15a4deb079809bc14718a58cf0fe451`, tree `6f6912d8612f71e07d2c338d860e906333ce8aad`. `source-coverage-01.json` records57 bounded source reviews, hashes, full/partial read ranges and explicit gaps. This is not the final campaign assessment: root owns current story edits, Android/iOS execution, final source freeze and combined checks.

## Evidence status and interpretation

- Source/configuration inspection is not a runtime, signed-artifact, live-GitHub or Store pass.
- Existing audit IDs below are retained solely for traceability. Their old conclusions were not reused as current correctness proof.
- The original16 confirmed defects—including **RL-C1/RL-C2/RL-C3**—are code-remediation tasks, not external gates that can be waived. Their final repair evidence belongs in root's issue ledger.
- The known identity collision and disabled publication are intentional safeguards, not permission to invent new IDs or enable publishing.
- The pending APK artifact inspection will receive a separate additive receipt; iOS font inspection proves only two files in a Debug simulator bundle.

## Locally actionable follow-ups, not external blockers

| Original ID | Current independently reopened consequence | Current action/evidence |
|---|---|---|
| DOC-C4 | Protected-branches prose names two jobs; actual workflow has five. Following the stale recipe can omit platform checks. No live misconfiguration alleged. | Root independently confirmed. Five actual names now listed; a workflow-to-prose regression is authored. Execution/review pending. |
| INV-C01 | Git membership/history automatically emit REVIEWED, no-findings and banner-verified claims without source review input. | Root independently confirmed. Generator/README/tests now separate mechanical inventory from attestation. Historical human findings/overrides preserved; exact prior generated CSV archived before pending regeneration. |
| MT-T1 | Component-wide substring checks accept an expected checksum from the wrong sibling artifact. | Root independently confirmed. Shared test-only DOM assertion and18 deterministic regressions drafted in excluded evidence; awaiting root review/application after its APK lane. Metadata and dependency pins unchanged. |

Exact pre-fix paths, line ranges, caller reachability and counter-evidence: `LOCAL_GAPS_SOURCE_PROOF_01.md`. Exact preserved originals: `local-gap-remediation-01/before-receipt.json`; prior CSV SHA256 `b9bfaed8405afc123ec2a95cbf052a3d1d6e0578ba2b831d3e723316edc893ba`. These are documentation/evidence defects, not three new runtime application defects.

Original **SN-T1**, **WD-T1**, **WD-T2** and **IOS-R1** stay linked to the root campaign. This bounded map has not independently reopened all of their current repairs, so it gives them no completion status. In particular, do not reuse historical desktop-only test placement or a past ignored-fixture explanation without checking current source and executed cases. Likewise, do not carry **WD-C4** as an unresolved product decision if root's later explicitly authorized policy work has resolved it.

## Current source-backed release safeguards

All paths are relative to `/Users/abdelrahman/Projects/parlor/`; hashes/ranges are in the coverage ledger unless a narrower structural receipt is cited.

1. `config/release-policy.json:7–29` pins both canonical IDs to `com.parlor.app`, ownership status `blocked`, collision reason, and null verification references. `release/mobile-release.json:12–28` retains blocked shadow identities. `scripts/release/release_tool.py:163–199` rejects the known colliding ID before considering approval fields. `scripts/release/store_api.py:1764–1767` checks execution authorization before Store client/credential branches. iOS candidate scripts invoke the guard before reading signing paths. This is source proof of fail-closed guards, not proof of publisher ownership.
2. Every job in the three candidate/promotion workflows retains `if: ${{ always() && false }}`. `release-guard-map-01.json` lists all11 job conditions and hashes. `scripts/release/workflow_contract.py:168–202` enforces that exact condition; `646–660` dispatches the checks. `scripts/release/validate_release_system.sh:27` and root `build.gradle.kts:65–70,139–148` wire validation into the release aggregate. No workflow was dispatched or enabled here.
3. Shared version authority remains `config/parlor-version.xcconfig`; wrapper/dependency/toolchain pins were read from actual source, not inferred from prose. Pinned Apple qualification is Xcode26.3/17C529 with deployment target16 (`config/release-policy.json:55–60`). Installed-host/version execution receipts remain root-owned; a different local simulator toolchain is not this gate passing.
4. Root tasks separate host-independent `productionCheck` (`build.gradle.kts:139–148`), managed Android runtime (`53–57`), real signing (`59–63`), Apple linkage/type-aware checks (`128–137`) and executable simulator tests (`32–35,194–198`). `.github/workflows/production-verification.yml` was read completely: its five host jobs and unsigned wrapper/managed-device checks are not equivalent to signed-device LAN or final Store delivery.
5. `release/mobile-release.json` is a shadow migration contract, not an active publishing path. Its initial Store locales are en-US; this is not an Arabic application-localization defect. `release/store/` is absent. No private handoff content was inspected and no policy value was guessed.

## Original external/manual gate IDs and exact remaining evidence

| Original gate ID | Status in this bounded map | Source basis and evidence still required | Owner |
|---|---|---|---|
| store-identity-and-live-governance | BLOCKED | Guards above deliberately stop publication. Need separately authorized, current read-only Store ownership verification, approved policy/identity decisions, actual protected-branch/environment reviewer readback, exact branch trees and candidate evidence. The stale local DOC-C4 wording is separately repairable and not excused by this external gate. | Application/release owner plus independent trusted reviewer |
| qualified-apple-toolchain-and-real-signing | BLOCKED | Policy pins the qualified toolchain; no private signing, archive or final signed-device/Store package operation was performed by this reviewer. Root's simulator/linkage receipts remain narrower evidence. Synthetic signing/TSA controls cannot satisfy publisher signing or Store acceptance. | Authorized Apple release owner |
| content-rights-and-store-declarations | BLOCKED | `docs/STORE_METADATA.md:3–4,60–85` explicitly leaves final copy, screenshots, ratings and URLs unverified; the shadow metadata directory is absent. Need owner-approved story/art/icon/dependency rights and final privacy/export/accessibility/availability/contact declarations bound to actual signed artifacts. This is missing approval/evidence, not a claim that rights were violated. | Content/legal/product and release owners |
| physical-lan-and-lifecycle | BLOCKED | Android factory53–71 and iOS factory29–45 configure LAN, authenticated transport and bounded reconnect/close-on-background. That proves selected configuration only. Need controlled physical Android/iOS same-LAN admission, complete-game, disconnect/rejoin, lifecycle and host-loss evidence at the final source. Do not un-ignore physical fixtures or claim pairwise advertisement proves complete games. | Device-validation owner |
| native-cross-host-test-equivalence | NOT REASSESSED BY THIS MAP | Actual current workflow/task graph was read, but root owns test-source relocations and per-host case receipts. Historical desktop-only labels must be reconciled with current source. Linux/Windows/Intel/macOS/Native executions cannot be substituted by a successful Apple-arm64 Desktop run. | Root campaign and CI/platform owner |
| navigation-accessibility-and-complete-ui-journeys | NOT REASSESSED BY THIS MAP | Root owns synthetic simulator/AVD UI evidence. Full LTR/RTL Back, screen-reader, large-text, landscape, focus, contrast, keyboard and complete live-game journeys need explicit scenario receipts; font inclusion/source review alone does not prove them. | Root UI review and device/accessibility owner |
| device-storage-and-app-switcher-privacy | NOT REASSESSED BY THIS MAP | Android source manifests disable backup and point to exclusion rules; iOS declarations include LAN purpose copy/Bonjour/required-reason manifest. Declarations do not prove secure-storage durability, actual OS backup exclusions, lock/background privacy or final SDK compliance. Root owns ST-C1/IOS-R1 and platform evidence; retain unavailable signed physical checks explicitly. | Root storage review and physical-device owner |
| ios-recovery-warning-attribution | NOT REASSESSED BY THIS MAP | IOS-R1 remains an evidence gap unless root independently establishes a new source defect. Do not hide correct fail-closed recovery warnings or insert entitlements blindly. Any subsequent numeric Keychain result must remain attributed to the call that produced it. | Root native evidence reviewer |
| whodunit-modal-clock-policy | DEFER TO CURRENT ROOT POLICY RECORD | Original WD-C4 was not a confirmed defect; later product authorization may supersede the old question. This map makes no game-timer decision. | Product owner and root policy reviewer |

The non-PASS/non-BLOCKED labels in the last five rows are routing notes, **not gate results**. Root's final verification ledger must replace them with current source-backed PASS/FAIL/BLOCKED/NOT_APPLICABLE dispositions; none may be counted as a pass from this map.

## Privacy declarations versus implementation

`composeApp/src/androidMain/AndroidManifest.xml:3–18` requests the four LAN-related permissions, disables backup, links backup/transfer exclusions, enables RTL and disables cleartext traffic. `iosApp/iosApp/Info.plist:23–27,54–59` lists EN/AR and LAN/Bonjour declarations; both localized purpose strings and the39line privacy manifest were read. Those files are declaration inputs, not an audit of every packaged SDK or physical permission behavior.

`ContentModule.kt:59` actually binds `OfflineRemoteCaseDataSource`; its two executable methods return `Unreachable` without HTTP. This corroborates bundled/offline remote-content behavior. It does not alone prove every privacy retention, diagnostics, transport trust or signed-artifact claim in documentation; those boundaries belong to root's broader source/runtime review.

## Font counter-evidence and narrow artifact observation

`FONT_LICENSE_DISPOSITION.md` records that a missing standalone/full OFL file is **not an established defect**. Both shipping font files retain copyright/license links; official OFL FAQ§1.10 expressly permits link-only program-bundled font metadata while recommending full text. Exact Inter revision/license and a byte-identical Google Fonts JetBrains artifact were traced. Root's independent source/reference review remains the approval step.

The task-owned `ios-readiness-01` Debug simulator app contained exactly the two source fonts byte-for-byte (`font-ios-bundle-inspection-01.json`). That remains a narrow packaging observation even though root reported a later overall runner/parser failure. It is not Store-IPA, Android, font rendering or legal approval. Android inspection is pending an explicitly supplied task-owned artifact path.

## Ownership and cleanup

Original audits, human findings, verification metadata, dependencies and all private material were preserved. This reviewer did no Git commit/stage/checkout/push/merge/Store action, signing, Gradle/Xcode build, test/emulator launch or cleanup of another task's files/processes. Public-reference GETs were bounded and completed; comparison-font bytes were discarded from memory. Only compact source snapshots, JSON/Markdown/patch evidence and public license text remain. Root owns its one build lane, daemon shutdown, APK retention and later deletion.

This map supplies neither a project readiness percentage nor a READY verdict. The denominator, actual test execution, final dirty-source identity and remaining code fixes must come from root's consolidated issue/gate ledger.
