# Frozen-source applicability — 360fc

Reviewer `/root/ci_review`: read-only source/Git/stdlib inspection. No tests, builds, network or runtime-evidence review. **Not readiness PASS.**

F=`360fc797a85e1e494334fd8cc11b4e739981cea8`, tree `8bfe56a21074ba51a4b668bad0728e42007a7593`.
S=`b8683056ff1e312acf5571d231e13a7643f43686`; S→F changes only `docs/review/INDEPENDENT_REVIEW_INVENTORY.csv`.
E(delivery)=`ec0de52a2c7ff9077482a291dc4fd2591fdea01f`; L(prior)=`52f60924a89f788aab78c826096f34a32af40292`.
C=`remediation-runs/2026-09-07-local-readiness`; N=`remediation-runs/2026-09-08-continuation`.

## Git scope

Command: `git diff --raw --no-abbrev --no-renames START F -- PATHS`; SHA256 hashes raw stdout. Substitute full SHAs/expand C.

1. E→F: `composeApp game-modes iosApp shared build-logic build.gradle.kts settings.gradle.kts gradle gradle.properties gradlew gradlew.bat config AGENTS.md docs/PRODUCTION_ARCHITECTURE.md docs/RELEASE_GATES.md docs/HOW_TO_ADD_A_GAME.md docs/adr docs/WHODUNIT_DISCUSSION_TIMING.md ':(exclude)shared/networking-testing'`: **zero changes**, SHA `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`.
2. E→F: `shared/networking-testing`: two paths/414B, SHA `7a846cadf07e67ae6f2bf7c49d8139920824d97211db5ddf850e019880b027cb`: modified `src/commonMain/kotlin/com/parlor/networking/testing/ControlledStartRoom.kt`; added corresponding `src/commonTest/.../ControlledStartRoomReentrancyTest.kt`.
3. E→F: `.github scripts`: 17 paths/2571B, SHA `d47c8b09669a2b02946da8b79c233e49df41b6f690d58dbacc6713d686170555`. Native verification/portability/process controls, tests/template and workflow-contract changes; no shipping graph change.
4. L→F: `.github scripts shared composeApp game-modes iosApp C/l08-storage-functional-companion-02 C/native/normal-ios-launch-proposal-01`: 18 paths/3484B, SHA `1be44ec16a1a36eda4473aa64d015eebb4b512f35d4cf5921f1ac50c0ba4931b`. Fixture/guard, A29/B30 labels and ten B30 controls.

## Qualified review reuse

Reopened C/reviews/:
- `original16-timer-commit-evidence-map-01.json`, SHA `82cc49b33044b27c1be99115847a04bfa505fa5ee28706ccfd7e30f4f0e6f0ff`: **all16 original IDs, all50 unique mechanism files match recorded hashes at F**.
- `original16-timer-regression-descriptors-01.json`, SHA `b2fee80c8d9210fb44ab8b226a97912d30f381bc3eeea7ec9d4cb1a0e8d7e7af`: all45 unique mapped test-source hashes match F; historical descriptors not rerun.
- `independent-session-readiness-fresh-01.json`, SHA `6b1fee29ad0f6b04f3eaf184380700fb819cdbc330b8fa112b314fa91c20b88c`: 36/39 ledger files match. Two LeaveTimer test versions were superseded before E by mapped followups; ControlledStartRoom now has the correction below. Whole older ledger/runtime binding is not reusable unqualified.

Original16 and settled timer **source conclusions remain applicable within recorded scope**. Transitive fixture/control changes need fresh qualification, not automatic runtime reuse. Backup-retention source approval does not clear strict file-protection observations.

## Reopened compatibility edges

`Protocol.kt:29–46` and `ProtocolValidation.kt:40` retain exact4.2/equality compatibility. `AuthoritativeSessionCoordinator.kt:1155,1173–1187` owns command application and public+recipient-private snapshots. Whodunit projection clears host-only/other-private state. `SessionSeedSource.kt`, `RandomSource.kt` and Mafia reducer retain secret host seed/deterministic derivation. Mafia settings, Doctor eligibility/reducer/night resolution retain default-OFF repeat protection, ON allowance and independent self-heal. Snapshot/content sources and Discussion Leave-exclusion policy/router/ticker are unchanged. This is source applicability, not fresh privacy/gameplay proof.

ControlledStartRoom reserves validated Ready before reentrant send and rolls back newly added state on failure/cancellation. Canonical and both templates match SHA `1de4cad503f42fc3e9cb198a8df04b7613eab303a3cafd6b31cfabd14e142ab9`. Independent source approval: N/reviews/`native-a29-host-correction-independent-01.json`, SHA `ed924058075686d799d8b31f6af0262f25c1073f5b134a2459855ff6656b9d6d` (runtime sections not audited). Gradle edges place networking-testing only in Mafia/Whodunit/session **commonTest**, never shipping main dependencies. L08 probes/templates enter owned instrumented copies, not canonical app sources or normal-binary provenance.

P2pKit core/LAN retain Maven Central/shared `0.7.0-rc3` pin and strict verification bytes; no mavenLocal/sibling replacement. Android/iOS Store ID remains `com.parlor.app`, Debug `.debug`; collision unresolved. Store candidate/external/production workflows are byte-unchanged, all4/4/3 jobs false-guarded. Production verification retains five-job default/full gates; focused modes are not combined qualification.

Native/full-D results require separate admission. No physical-device, Store-operation or owner/legal requirement is satisfied. Only this note was written; no build resources/cleanup owed.
