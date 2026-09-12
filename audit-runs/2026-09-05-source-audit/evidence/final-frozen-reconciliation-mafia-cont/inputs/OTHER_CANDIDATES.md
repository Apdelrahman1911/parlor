# Documentation, evidence gaps, rejected and blocked candidates

Source: `3625d0663ba6eb51338cbd5f9dc45f859ec18846` / tree `db7f3d2afe73a13628296daee2cce71165eebc8d`. No production changes.
Only the confirmed-defect classification is counted in FINDINGS.md. A documentation mismatch
or weak test is not automatically an application defect. Original pending candidate text is
historical; the linked final independent adjudication plus execution qualifications governs.

## Documentation mismatches (9)

### DOC-C1 — Display-name retention inventory omits local resume snapshots

**DOCUMENTATION MISMATCH** / Low. Finder `/root/whodunit_cont`; independent validator `/root/mafia_cont`.

Retention documentation omits local resume snapshot display names; not a newly proved privacy leak or Store declaration verdict.

- `/Users/abdelrahman/Projects/parlor/docs/PRIVACY_AND_COMPLIANCE.md` — **19** (one-based).
  SHA-256: `299d248dea1938a445624842ab6b741b54221c06516689ec712eafcfe7b2bea9`.

Complete reasoning, counter-evidence and recommended action: [validations/DOC-C1-mafia_cont.md](validations/DOC-C1-mafia_cont.md).

### DOC-C2 — Room lifecycle diagram conflates game completion and room termination

**DOCUMENTATION MISMATCH** / Low. Finder `/root/whodunit_cont`; independent validator `/root`.

Clarify natural game completion versus a room-ending SessionEnded envelope; no broken room lifecycle claimed.

- `/Users/abdelrahman/Projects/parlor/docs/PRODUCTION_ARCHITECTURE.md` — **129–148** (one-based).
  SHA-256: `0662f57345e04990baa76f45328779c525d780544585b4bd2b2b4b2dbc1536b3`.

Complete reasoning, counter-evidence and recommended action: [validations/DOC-C2-root.md](validations/DOC-C2-root.md).

### DOC-C3 — Xcode Team-selection instructions promise the wrong change scope

**DOCUMENTATION MISMATCH** / Low. Finder `/root/mafia_cont`; independent validator `/root/session_cont`.

Signing-team edits can modify tracked project settings, contrary to the per-user-only instruction; no signing operation or exploit demonstrated.

- `/Users/abdelrahman/Projects/parlor/docs/IOS_SETUP.md` — **98–100** (one-based).
  SHA-256: `c911af6990b22d550b31e36e1663cb6fda7150ba94a0ff53e724f560c085d823`.
- `/Users/abdelrahman/Projects/parlor/iosApp/Configuration/Config.xcconfig` — **3–6** (one-based).
  SHA-256: `102b93364c12cb6d9055fcb88acdb5ab15bc1a3724bdc350799539bc09d9c0df`.

Complete reasoning, counter-evidence and recommended action: [validations/DOC-C3-session_cont.md](validations/DOC-C3-session_cont.md).

### DOC-C4 — Release branch-protection instruction names two checks instead of five

**DOCUMENTATION MISMATCH** / Low. Finder `/root/whodunit_cont`; independent validator `/root/session_cont`.

Instructions say two required verification jobs although the current workflow declares five; no live GitHub ruleset was inspected.

- `/Users/abdelrahman/Projects/parlor/docs/RELEASE_AUTOMATION.md` — **322–332** (one-based).
  SHA-256: `40ba8c77f755469ac0482af510efe8296d50304af16b0187710df366b698fb7b`.

Complete reasoning, counter-evidence and recommended action: [validations/DOC-C4-session_cont.md](validations/DOC-C4-session_cont.md).

### DOC-C5 — Snapshot filesystem KDoc names legacy storage directories

**DOCUMENTATION MISMATCH** / Low. Finder `/root`; independent validator `/root/mafia_cont`.

Current platform destinations are protected no-backup/Application Support paths. Legacy names in KDoc are inaccurate; no runtime relocation or privacy defect inferred from the comment.

- `/Users/abdelrahman/Projects/parlor/shared/storage/src/commonMain/kotlin/com/parlor/storage/snapshot/FileBackedSnapshotStore.kt` — **204–208** (one-based).
  SHA-256: `e2ca0be28fd2f0eafee1a55022653190cf30a8c6da620dc3dfe56f361e0da27f`.

Complete reasoning, counter-evidence and recommended action: [validations/DOC-C5-mafia_cont.md](validations/DOC-C5-mafia_cont.md).

### DOC-C6 — Peer countdown comment contradicts current waiting UI

**DOCUMENTATION MISMATCH** / Low. Finder `/root`; independent validator `/root/mafia_cont`.

Peer receives public timer state but current discussion UI waits for host. Correct comment only; not a missing-countdown feature request or synchronization defect.

- `/Users/abdelrahman/Projects/parlor/game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/ui/flow/WhodunitPhaseRouter.kt` — **868–869** (one-based).
  SHA-256: `b1b7d38583bf32ee04cb3d58eecce7d312944b7b8497e3914d81cb27a03c86fb`.

Complete reasoning, counter-evidence and recommended action: [validations/DOC-C6-mafia_cont.md](validations/DOC-C6-mafia_cont.md).

### DOC-C7 — Comments describe obsolete GameEvent-driven feedback and persistence

**DOCUMENTATION MISMATCH** / Low. Finder `/root/session_cont`; independent validator `/root`.

Two source comments incorrectly describe current UI-feedback consumers and event-triggered persistence. Actual shipping flows use canonical/projection state and command receipts. Correct wording only; no event-driven app failure or required architectural refactor inferred. Broader generic event-contract wording is not an additional approved manifestation.

- `/Users/abdelrahman/Projects/parlor/game-modes/mafia/src/commonMain/kotlin/com/parlor/games/mafia/domain/event/MafiaEvent.kt` — **8–10** (one-based).
  SHA-256: `bdd9f8db0c3e9562e0e484644b0072127e1e36e09baf7b1525217a6f41319de5`.
- `/Users/abdelrahman/Projects/parlor/game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/ui/flow/WhodunitGameFlow.kt` — **935–937** (one-based).
  SHA-256: `4d58eb3bebd3777c88ca9f8ab21d97441f6d2cd1a70e28b07a0334fdc0c8ee17`.

Complete reasoning, counter-evidence and recommended action: [validations/DOC-C7-root.md](validations/DOC-C7-root.md).

### INV-C02 — Generated review inventory misclassifies native test files

**DOCUMENTATION MISMATCH** / Low. Finder `/root/mafia_cont`; independent validator `/root/whodunit_cont`.

Generated inventory misclassifies native test paths; no shipping source-set contamination established.

- `/Users/abdelrahman/Projects/parlor/scripts/generate_review_inventory.py` — **104–142, 181–205** (one-based).
  SHA-256: `83d837420bade1377ee319bafa1a9431a7a8542c4d84596d4cecf42b0e3aac27`.
- `/Users/abdelrahman/Projects/parlor/docs/review/INDEPENDENT_REVIEW_INVENTORY.csv` — **25, 390** (one-based).
  SHA-256: `b9bfaed8405afc123ec2a95cbf052a3d1d6e0578ba2b831d3e723316edc893ba`.

Complete reasoning, counter-evidence and recommended action: [validations/INV-C02-whodunit_cont.md](validations/INV-C02-whodunit_cont.md).

### SN-D1 — Physical full-game instructions specify too few devices

**DOCUMENTATION MISMATCH** / Low. Finder `/root/session_cont`; independent validator `/root`.

Physical full-game runbook recommends fewer seats/devices than shipping story/Mafia minima; documentation mismatch, not game-rule defect.

- `/Users/abdelrahman/Projects/parlor/docs/P2P_MANUAL_TEST.md` — **234–238, 247–257** (one-based).
  SHA-256: `128c3e0677e5265e5a2dd6b7017f3ed97f9ed8f318fb9b87239c08457f1639bb`.

Complete reasoning, counter-evidence and recommended action: [validations/SN-D1-root.md](validations/SN-D1-root.md).

## Test and evidence gaps (6)

### INV-C01 — Generated inventory does not attest reviewed source identity

**TEST/EVIDENCE GAP** / Low. Finder `/root/mafia_cont`; independent validator `/root/whodunit_cont`.

Generated review status lacks independently bound reviewed-file identity; evidence/wording limitation, not application or release authorization failure.

- `/Users/abdelrahman/Projects/parlor/scripts/generate_review_inventory.py` — **255–276, 316–359** (one-based).
  SHA-256: `83d837420bade1377ee319bafa1a9431a7a8542c4d84596d4cecf42b0e3aac27`.
- `/Users/abdelrahman/Projects/parlor/docs/review/README.md` — **12–19** (one-based).
  SHA-256: `bd11a380fd7ee9ae3ef719d657956efb356ecd514e585b6aa3a2a0a839342ea4`.

Complete reasoning, counter-evidence and recommended action: [validations/INV-C01-whodunit_cont.md](validations/INV-C01-whodunit_cont.md).

### MT-T1 — Checksum contract assertions do not bind hashes to artifact elements

**TEST/EVIDENCE GAP** / Low. Finder `/root/whodunit_cont`; independent validator `/root/session_cont`.

Component-wide assertions can find the expected artifact and hash under different artifact children. Current hashes and strict Gradle verification are not alleged wrong or bypassed.

- `/Users/abdelrahman/Projects/parlor/shared/transport-p2p/src/desktopTest/kotlin/com/parlor/transport/p2p/P2pKitMavenProvenanceContractTest.kt` — **45–62** (one-based).
  SHA-256: `b11524844042a2fc6eec9f82c835e5455331a7c362b3f7b9c563dbfd59da05ce`.
- `/Users/abdelrahman/Projects/parlor/shared/transport-p2p/src/desktopTest/kotlin/com/parlor/transport/p2p/DesktopDependencyVerificationContractTest.kt` — **136–155** (one-based).
  SHA-256: `0189e7ad54db484d2a676e6cc2ee2f822e5cbc6ae296e6c86aefc63537f0c00f`.

Complete reasoning, counter-evidence and recommended action: [validations/MT-T1-session_cont.md](validations/MT-T1-session_cont.md).

### SN-T1 — Ignored peer loopback fixtures no longer model the current admission contract

**TEST/EVIDENCE GAP** / Low. Finder `/root/session_cont`; independent validator `/root`.

Intentionally ignored peer loopback fixtures no longer fulfill current host admission contract. Distinct from non-void test registration; physical tests remain ignored and unexecuted.

- `/Users/abdelrahman/Projects/parlor/shared/transport-p2p/src/desktopTest/kotlin/com/parlor/transport/p2p/P2pKitRoomTransportLoopbackTest.kt` — **125–249** (one-based).
  SHA-256: `b473017536585291ea1a3201e44433278e7bc59b5eb97a89cbfc9c3871ccad6f`.

Complete reasoning, counter-evidence and recommended action: [validations/SN-T1-root.md](validations/SN-T1-root.md).

### WD-T1 — malformed UTF-8 action test corrupts a key, so remains green if boundary is weakened

**TEST/EVIDENCE GAP** / Low. Finder `/root/whodunit_cont`; independent validator `/root`.

Three malformed-UTF8 negative fixtures also fail later JSON/discriminator parsing, so their generic rejection assertions do not isolate strict decoding. Production strict UTF8 checks remain present; no privacy/content-identity exploit or executed mutation test claimed.

- `/Users/abdelrahman/Projects/parlor/game-modes/whodunit/src/desktopTest/kotlin/com/parlor/games/whodunit/action/WhodunitActionCodecTest.kt` — **182–190** (one-based).
  SHA-256: `7081caa7327e31c8d1f1b28fcde67540c63d6550a589843b0caaa80d07f04515`.
- `/Users/abdelrahman/Projects/parlor/game-modes/whodunit/src/desktopTest/kotlin/com/parlor/games/whodunit/multidevice/WhodunitPeerProjectionBoundaryTest.kt` — **245–284** (one-based).
  SHA-256: `49abace07e9a5e1114c439046ce1b6e504198ac1956cc37bc65704402582e390`.
- `/Users/abdelrahman/Projects/parlor/game-modes/whodunit/src/desktopTest/kotlin/com/parlor/games/whodunit/snapshot/WhodunitSnapshotValidationTest.kt` — **135–138** (one-based).
  SHA-256: `28e40104e0b6929aae16ccf22235e32d2017feb99a8931f612306f9631542965`.

Complete reasoning, counter-evidence and recommended action: [validations/WD-T1-root.md](validations/WD-T1-root.md).

### WD-T2 — Ticker and reroll identity assertions do not exercise their stated claims

**TEST/EVIDENCE GAP** / Low. Finder `/root/whodunit_cont`; independent validator `/root`.

One loop for a two-loop claim; session identity compares an immutable argument with its constructor literal, and case identity compares post-reroll projections rather than before/after identity preservation. Existing assignment/generation/event assertions remain genuine; no assignment-variation defect is asserted.

- `/Users/abdelrahman/Projects/parlor/game-modes/whodunit/src/desktopTest/kotlin/com/parlor/games/whodunit/flow/TickerAndRerollTest.kt` — **272–297, 372, 411** (one-based).
  SHA-256: `84c99331d6d8f60ea0edce8e14fd373b49627ab213015dae9e3a8ad06824b07f`.

Complete reasoning, counter-evidence and recommended action: [validations/WD-T2-root.md](validations/WD-T2-root.md).

### IOS-R1 — Unsigned iOS app-host recovery evidence gap; unavailable warning is intended

**TEST/EVIDENCE GAP**. Finder `/root`; independent validator `/root/session_cont`.

Fresh unsigned iOS26.5 app-host diagnostic: real Koin-bound Home recovery rerun returns local success-empty, multiplayer SecureStorageUnavailable and the correct unavailable projection. A subsequent equivalent same-app Keychain read returns -34018/errSecMissingEntitlement; that number is not the original Home or production-rerun status. Original Home warning is a separate observed inline card. Presentation is intended fail-closed handling; no application defect or fix is established. Signed-device storage and original-call numerical attribution remain unverified. One copied launch/receipt XCTest passed; storage health was not asserted.

- `/Users/abdelrahman/Projects/parlor/composeApp/src/commonMain/kotlin/com/parlor/app/shell/home/HomeRecoveryAvailability.kt` — **88–146** (one-based).
  SHA-256: `4c8a962fba7ec8334cc8f4dfe0a75f221b201178481ae115d85ae391647fbb2a`.
- `/Users/abdelrahman/Projects/parlor/composeApp/src/iosMain/kotlin/com/parlor/app/storage/IosSecureKeyValueBacking.kt` — **74–112, 145–179** (one-based).
  SHA-256: `4bce714c161a892ecd66fff662561d925db7e045af8cacf28e5b698c887015c7`.
- `/Users/abdelrahman/Projects/parlor/iosApp/iosAppUITests/IOSAppLaunchUITests.swift` — **1–32** (one-based).
  SHA-256: `e903278fc8defcac9a4d03a52495c1364a361222e5f369059c5d6b6416fb6eb7`.

Complete reasoning, counter-evidence and recommended action: [validations/IOS-R1-session_cont.md](validations/IOS-R1-session_cont.md), [validations/IOS-R1-apphost02-session_cont.md](validations/IOS-R1-apphost02-session_cont.md), [reviews/iosr1-apphost-02-execution-independent-whodunit-cont.md](reviews/iosr1-apphost-02-execution-independent-whodunit-cont.md).

## Rejected numbered candidates (3)

### M-C02 — Rejected connected-seat retirement refusal

**FALSE POSITIVE**. Finder `/root/mafia_cont`; independent validator `/root/session_cont`.

Rejected alleged connected-seat retirement refusal: actual production override has no connected-state rejection. Narrow rejection does not prove all native closure/rejoin schedules safe.

- `/Users/abdelrahman/Projects/parlor/shared/transport-p2p/src/commonMain/kotlin/com/parlor/transport/p2p/P2pKitRoomTransport.kt` — **2781–2851** (one-based).
  SHA-256: `6f2584af596bb437007f856b5c6313b88f864855a31f77085052c3394ca4a263`.

Complete reasoning, counter-evidence and recommended action: [validations/M-C02-session_cont.md](validations/M-C02-session_cont.md).

### MT-C1 — Kotlin build-cache advisory applicability

**FALSE POSITIVE**. Finder `/root/whodunit_cont`; independent validator `/root/session_cont`.

Upstream KAPT build-cache advisory matches a broad pinned package record but the affected KAPT tasks/path are not enabled in current Parlor graph; not a confirmed present vulnerability.

- `/Users/abdelrahman/Projects/parlor/gradle/libs.versions.toml` — **8, 85–103** (one-based).
  SHA-256: `87a031665605a1fb3f932be31c75afd8d2d1bc0d94cfcca36101141c50788399`.

Complete reasoning, counter-evidence and recommended action: [validations/MT-C1-session_cont.md](validations/MT-C1-session_cont.md).

### ROOT-C1 — Rejected corrupt-directory crash candidate

**FALSE POSITIVE**. Finder `/root`; independent validator `/root/mafia_cont`.

Concrete corrupt-directory crash rejected: macOS Foundation opener returns nil before exceptional read. Separate post-open Objective-C I/O-failure behavior remains an uncounted evidence gap; no iOS runtime proof.

- `/Users/abdelrahman/Projects/parlor/composeApp/src/iosMain/kotlin/com/parlor/app/storage/IosSnapshotFileSystem.kt` — **405–406** (one-based).
  SHA-256: `340c62d93104e015bc9dc80f92b04e46e92c77d52947b2623f0fd5c472900216`.

Complete reasoning, counter-evidence and recommended action: [reviews/independent-ROOT-C1-mafia-cont.md](reviews/independent-ROOT-C1-mafia-cont.md).

## Independently reviewed but blocked candidates (1)

### WD-C4 — Leave confirmation freezes Whodunit ticker without canonical pause

**UNCONFIRMED — BLOCKED**, no approved defect severity. Finder `/root/whodunit_cont`; validator `/root`.

Source behavior corroborated: modal replaces RoundSegment, cancels ticker, and Stay resumes. No settled product policy for elapsed time during exit confirmation; not a confirmed timer defect. No runtime reproduction. Broader tab-stranding premise rejected.

- `/Users/abdelrahman/Projects/parlor/game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/ui/flow/multiplayer/WhodunitHostSessionFlow.kt` — **249–258** (one-based).
  SHA-256: `6f65f1c35b2f5ecf6b7f5a18d2c8f022e595c0dccca3f82e4b1c602e1dea61cd`.
- `/Users/abdelrahman/Projects/parlor/game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/ui/flow/WhodunitPhaseRouter.kt` — **827–938** (one-based).
  SHA-256: `b1b7d38583bf32ee04cb3d58eecce7d312944b7b8497e3914d81cb27a03c86fb`.

Actual path, evidence and exact remaining verification: [validations/WD-C4-root.md](validations/WD-C4-root.md).

## Other explicitly retained leads — not extra numbered findings

- **Ordered GameEvent cancellation:** the generic three-batch bypass is real, but no shipping subscriber or consequent UI/state/protocol failure was established. Original finder `/root/session_cont`, independent rejection `/root`: [root disposition](reviews/root-residual-event-lead.md); [source/library follow-up](reviews/ordered-event-residual-session_cont.md). Reject a current-app defect claim; retain extension-contract uncertainty before any future consumer is added.
- **Android legacy retention:** success-only deletion can retain an old copy, but production manifest plus both backup-rule formats exclude that directory/domain. The iOS backup-leak inference is rejected, not copied to Android. A stricter local purge policy remains a product/security question. [Independent root closeout](validations/storage-residual-root.md).
- **iOS post-open NSFileHandle exception:** legacy throwing APIs warrant investigation, but no deterministic reachable read/close failure was established. The macOS directory opener returning nil rejects only the earlier directory-crash premise; it does not prove all iOS post-open I/O safe. UNCONFIRMED — BLOCKED. [Root closeout](validations/storage-residual-root.md).
- **iOS localized Debug display name:** FALSE POSITIVE for localization defeating an already-configured distinct development label: no such label is configured. The actual isolation contract is the `.debug` bundle identifier; the shared localized brand name is intentional. Finder `/root`, separate validator `/root/session_cont`: [configuration, caller and native-precedence proof](reviews/ios-localized-display-name-session_cont.md). A future request for distinct launcher branding is not a current defect.
- **Whodunit editorial ambiguities:** Corniche/Saidi age wording and Zamalek embezzlement duration remain owner-clarification questions, excluded from WD-C2's four confirmed chronologies. [Independent content verdict](validations/WD-C2-mafia_cont.md).
- **Other layout leads:** setup/tally or timer/loading geometry without production measurements is not a confirmed clipping defect. See [Whodunit follow-up](reviews/whodunit-ui-followup-whodunit_cont.md) and M-C03's narrowed validation. Duplicate-name rejection already exists; case variants are intended. Top-level tab navigation cannot strand an active game because the navigator rejects the switch and hides the bar.

No candidate above is marked fixed. None licenses changes to rules, privacy, platform settings or Store workflows.
