# Independently confirmed findings

**16 confirmed defects; no fixes implemented.** Audit-only source: `main` at
`3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`.

The scope is the frozen checkout plus the fingerprinted baseline inventory, not a commit alone.
Each section below incorporates its linked complete independent validation: callers/callees,
guards, expectation, counter-evidence, exact reproduction qualifications and recommendations.
These are current-source audit findings, not GitHub issue statuses. No introduction/regression
commit or first-ever discovery is asserted unless the underlying dossier specifically establishes it.
Severity concerns the stated prerequisite and impact; absence of High/Critical findings does not imply readiness.

## ST-C1 — Failed iOS legacy migration retains backup-eligible application plaintext

**CONFIRMED DEFECT / Medium**. Finder `/root/session_cont`; separate validator `/root`.

**Scope/prerequisites:** iOS local saves retained from the legacy Documents layout; malformed legacy prefix, or damaged protected header plus an old copy. Fresh installs without legacy saves are not the witness.

**Impact/severity:** Medium: host-private application-plaintext remains eligible for system backup despite the current excluded/encrypted-storage policy. Actual transfer or disclosure is not established.

### Exact locations

- `/Users/abdelrahman/Projects/parlor/composeApp/src/iosMain/kotlin/com/parlor/app/storage/IosSnapshotFileSystem.kt` — **74–123, 150–208, 347–382** (one-based).
  SHA-256: `340c62d93104e015bc9dc80f92b04e46e92c77d52947b2623f0fd5c472900216`.

**Expected versus actual / reachable root cause:** Home recovery -> common store list/metadata -> IosSnapshotFileSystem.list/read -> failed migration. Legacy-only framing rejection occurs before encryption/deletion; malformed current magic bypasses the recognized-magic cleanup finally. Only the new Application Support destination is excluded. A corrupt first byte can leave the rest of the private payload recoverable. Expected: safe retained quarantine, not forced deletion of the last copy.

**Reproduction/proof:** Actual production Kotlin/Native filesystem on a fresh iOS 26.5 simulator: two witnesses preserve synthetic bytes and read false Foundation backup-exclusion flags; the desired-safety assertion fails. No Keychain access is needed on these rejecting branches. Three tests executed, two witness passes, one expected failure.

**Counter-evidence/limits:** Healthy migration and recognized-magic missing-key cleanup already work. Sandbox/Data Protection are not the backup-exclusion flag. No real backup, private user data, Android leak or cryptographic bypass was tested or inferred.

**Recommended remediation and regression coverage (not implemented):** Exclude/verify the old directory before migration, or quarantine retained opaque records in protected excluded storage. Preserve Retry/Discard, current-record precedence and corrupt-state rejection. Test both failures, exclusion failure, mixed inventory, oversized/read failures, interrupted replacement/deletion and explicit discard; separately validate real backup behavior.

**Independent conclusion and full source trace:** [validations/ST-C1-root.md](validations/ST-C1-root.md).
- Execution `storage-native-01`: Two current-behavior witnesses PASS; desired-safety assertion FAIL expected. Three executed, zero errors/skips. All task outputs and simulator cleaned. Foundation flags are not actual backup evidence. Evidence: [evidence/storage-native-01/receipt.json](evidence/storage-native-01/receipt.json), [evidence/storage-native-01/test-receipts.json](evidence/storage-native-01/test-receipts.json), [evidence/storage-native-01-simulator/receipt.json](evidence/storage-native-01-simulator/receipt.json).

## SN-C1 — Late valid start commit can fail while sending its best-effort acknowledgement

**CONFIRMED DEFECT / Medium**. Finder `/root/session_cont`; separate validator `/root`.

**Scope/prerequisites:** Whodunit and Mafia LAN peers; a valid session-start commit arrives close to the receive deadline and acknowledgement sending suspends beyond the remaining budget. Not pass-and-play.

**Impact/severity:** Medium: peer shows failed start after the host has irreversibly entered play, forcing recovery rather than installing the committed session.

### Exact locations

- `/Users/abdelrahman/Projects/parlor/shared/session/src/commonMain/kotlin/com/parlor/session/multidevice/SessionStartHandshake.kt` — **210–287, 356–369** (one-based).
  SHA-256: `fc6c7306164d149c3471c021fbbd05ac826f91bc17cacb32f67355cab2ec5b9f`.

**Expected versus actual / reachable root cause:** Both peer flows call awaitAuthoritativeSessionStart. Its outer timeout contains commit reception AND best-effort ACK sending. The valid commit passes all binding checks, but a deadline during ACK turns the whole operation into Failure before Success is returned. The host has already committed Started; peer duplicate recovery is not installed after this failed return. Expected: an accepted commit is not revoked merely by its best-effort delivery receipt.

**Reproduction/proof:** Production handshake with deterministic coroutine transport: commit at 99 ms of a 100-ms wait, ACK takes 10 ms under a separate 20-ms ACK bound. The Success assertion fails. Exact P2pKit rc3 source corroborates genuinely suspending send/mutex semantics; no device latency was measured.

**Counter-evidence/limits:** Immediate failed ACK already succeeds correctly; missing/invalid commits and explicit caller cancellation must remain failures. No peer reducer, secret leak or incidence rate is established.

**Recommended remediation and regression coverage (not implemented):** Separate accepted-commit authority from bounded best-effort acknowledgement without swallowing caller cancellation. Test near-deadline delayed/failed ACK, invalid/no commit, duplicate commit, cancellation and revision-zero snapshot recovery for both games; keep exact protocol 4.2 validation.

**Independent conclusion and full source trace:** [validations/SN-C1-root.md](validations/SN-C1-root.md).
- Execution `repro-session-01`: One expected assertion failure: valid late commit reports failure when best-effort ACK consumes outer deadline. Synthetic transport, production handshake. Evidence: [evidence/repro-session-01/receipt.json](evidence/repro-session-01/receipt.json), [evidence/repro-session-01/test-receipts.json](evidence/repro-session-01/test-receipts.json).

## SN-C2 — Cancellation after host advertising but before ownership return

**CONFIRMED DEFECT / Medium**. Finder `/root/session_cont`; separate validator `/root`.

**Scope/prerequisites:** Both LAN host-opening flows; cancellation while registering an already-created room with background lifecycle state.

**Impact/severity:** Medium: room/kit ownership can escape cleanup, leaving an inaccessible collector/native-kit owner after the user cancels opening.

### Exact locations

- `/Users/abdelrahman/Projects/parlor/shared/transport-p2p/src/commonMain/kotlin/com/parlor/transport/p2p/P2pKitRoomTransport.kt` — **259–337** (one-based).
  SHA-256: `6f2584af596bb437007f856b5c6313b88f864855a31f77085052c3394ca4a263`.
- `/Users/abdelrahman/Projects/parlor/shared/transport-p2p/src/commonMain/kotlin/com/parlor/transport/p2p/AppLifecycleRoomCoordinator.kt` — **44–54** (one-based).
  SHA-256: `0380faee12f2258adb5f1bef64d1f647d6a29a5e4735e405ad4e36beca828540`.

**Expected versus actual / reachable root cause:** P2pKitRoomTransport.host marks initialization complete and exits its cleanup finally before suspending registerHost. The opening owner cleans only returned Success objects. Cancellation during registerHost therefore reaches neither owner nor kit teardown. The kit belongs to a process scope, not the cancelled opening coroutine. Background expiry can help, but an intervening foreground may re-advertise and cancel that expiry.

**Reproduction/proof:** Production transport with fake kit and current background registration: cancellation leaves stopCalls=0 instead of the required 1. The test preserves the library's noncancellable cleanup behavior and tears its fixtures down. It is not a real native-radio leak observation.

**Counter-evidence/limits:** Earlier initialization failures and explicit leave have cleanup. SDK scope ownership was checked at exact rc3; implicit parent-job cancellation does not rescue this gap. Earlier audit init-script failure is not reproduction evidence.

**Recommended remediation and regression coverage (not implemented):** Keep creation and lifecycle registration in one ownership-transfer try/finally; close unreturned resources noncancellably and detach registration. Cover every cancellation boundary, foreground-after-cancel, duplicate close and production-owner Leave. Do not change game authority or protocol.

**Independent conclusion and full source trace:** [validations/SN-C2-root.md](validations/SN-C2-root.md).
- Execution `repro-ui-transport-02`: One expected failure: cancelled post-advertise registration leaves fake kit stopCalls0 rather than1. Separate UI harness failure from this cycle is not counted for M-C03. Evidence: [evidence/repro-ui-transport-02/receipt.json](evidence/repro-ui-transport-02/receipt.json), [evidence/repro-ui-transport-02/test-receipts.json](evidence/repro-ui-transport-02/test-receipts.json).

## WD-C1 — Whodunit peer readiness resubmits when command overlay removes router

**CONFIRMED DEFECT / Medium**. Finder `/root/whodunit_cont`; separate validator `/root/session_cont`.

**Scope/prerequisites:** Whodunit LAN peer PublicIntro/RulesBriefing, host remains in that phase, and a frame observes command progress. Use six seats with shipped content; four seats require explicitly synthetic Classic content.

**Impact/severity:** Medium: automatic readiness can repeatedly send fresh commands, generate invalid-action feedback and churn waiting UI without new user input.

### Exact locations

- `/Users/abdelrahman/Projects/parlor/game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/ui/flow/WhodunitGameFlow.kt` — **1340–1385** (one-based).
  SHA-256: `4d58eb3bebd3777c88ca9f8ab21d97441f6d2cd1a70e28b07a0334fdc0c8ee17`.
- `/Users/abdelrahman/Projects/parlor/game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/ui/flow/WhodunitPhaseRouter.kt` — **329–349** (one-based).
  SHA-256: `b1b7d38583bf32ee04cb3d58eecce7d312944b7b8497e3914d81cb27a03c86fb`.

**Expected versus actual / reachable root cause:** PeerPhaseRouter sends ACK in an entry LaunchedEffect without checking authoritative readiness. Sending sets Awaiting; parent replaces the router with a command overlay. Applied result/snapshot -> Idle remounts the router in the same phase -> another fresh command. Host set-membership guard returns InvalidAction for that duplicate semantic action; outcome acknowledgement returns Idle and repeats. Deduplication correctly distinguishes new command IDs. Expected: one already-recorded readiness action is not automatically reissued on presentation remount.

**Reproduction/proof:** Complete reachable source-level schedule, independently checked against the exact Compose effect implementation. Delay result delivery long enough for Awaiting to render, then alternate results/Idle while host does not advance. No runtime/LAN/UI test was executed for this candidate.

**Counter-evidence/limits:** Fast state conflation can avoid the remount; host progression ends the phase. Ordinary recomposition keys alone do not survive removal. Pass-and-play and Mafia do not share this auto-entry peer ACK path. No host-authority or privacy violation is shown.

**Recommended remediation and regression coverage (not implemented):** Bind one pending/readied action to authoritative phase/seat/generation, and/or preserve effect ownership under noninteractive command chrome. Test delayed transport, pause return, recreation, replay, stale rejection and rejoin; never weaken duplicate/revision validation or blindly retry non-idempotent commands.

**Independent conclusion and full source trace:** [candidates/WD-C1-independent-session_cont.md](candidates/WD-C1-independent-session_cont.md).

## IOS-B1 — Xcode can continue after the Kotlin framework build fails

**CONFIRMED DEFECT / Medium**. Finder `/root`; separate validator `/root/session_cont`.

**Scope/prerequisites:** Actual Xcode app target's Compile Kotlin Framework phase, incremental stale framework present, Gradle fails but normalizer can succeed. Local native witness used Xcode 26.5, not qualified 26.3.

**Impact/severity:** Medium: a failed Kotlin build can be masked, allowing Xcode to continue with stale bytes and invalidating source-to-app verification.

### Exact locations

- `/Users/abdelrahman/Projects/parlor/iosApp/iosApp.xcodeproj/project.pbxproj` — **132–149, 228–246** (one-based).
  SHA-256: `b3719c640b8d9f588d0ccb8b2cf2f4343216d328ec967d04873ff03198d58731`.
- `/Users/abdelrahman/Projects/parlor/scripts/release/normalize_embedded_apple_framework.sh` — **14–49** (one-based).
  SHA-256: `22f0decf09f3aced46cbbd01824acdd242bbf7253e8fc51dc53d973c679c3da8`.

**Expected versus actual / reachable root cause:** The /bin/sh phase executes Gradle and normalizer as independent commands, with no checked failure propagation. Child normalizer's shell options do not affect its caller; it validates shape rather than source freshness. A regular stale framework lets the final command succeed. Expected: failed embedding aborts the app build.

**Reproduction/proof:** Copied unmodified phase and normalizer in an isolated native PBXAggregateTarget; synthetic wrapper exits 42, stale non-executable placeholder satisfies normalizer, actual xcodebuild returns 0. This verifies shell failure masking, not that a stale app was launched or signed.

**Counter-evidence/limits:** Fresh/missing framework outputs normally fail later; fresh candidate directories reduce the prerequisite. Explicit IDE skip is intentional and disabled in the witness. No explanation of historical repeated iPhone crashes follows from this test.

**Recommended remediation and regression coverage (not implemented):** Abort on failed cd/Gradle and normalize only successful embedding, preserving strict verification and intentional IDE skip. Test failure with stale output, fresh/missing output, normalizer case handling and successful build; rerun on qualified Xcode separately.

**Independent conclusion and full source trace:** [validations/IOS-B1-session_cont.md](validations/IOS-B1-session_cont.md).
- Execution `native-xcode-phase-01`: Synthetic Xcode26.5/macOS target: wrapper exit42 observed while Xcode exited0. Placeholder/stale framework survived. Not a real app archive or signing test. Evidence: [evidence/native-xcode-phase-01/receipt.json](evidence/native-xcode-phase-01/receipt.json).

## ROOT-T3 — Non-Unit Jupiter tests silently omitted from discovery

**CONFIRMED DEFECT / Medium**. Finder `/root`; separate validator `/root/session_cont`.

**Scope/prerequisites:** Desktop Jupiter tests in the transport module: eight enabled lifecycle methods and two already-ignored physical-loopback methods. Test registration, not application failure.

**Impact/severity:** Medium: ordinary green suites silently omit eight intended regression bodies; two physical tests do not even appear as skipped.

### Exact locations

- `/Users/abdelrahman/Projects/parlor/shared/transport-p2p/src/desktopTest/kotlin/com/parlor/transport/p2p/P2pKitRoomTransportLifecycleTest.kt` — **427–450, 452–472, 3666–3736, 3790–3848, 4612–4623, 4625–4637, 4639–4658, 4758–4782** (one-based).
  SHA-256: `146b3f6cbbcefd368c36a8d34dae32b1532204ca92d6035cab3d1000c3fac70d`.
- `/Users/abdelrahman/Projects/parlor/shared/transport-p2p/src/desktopTest/kotlin/com/parlor/transport/p2p/P2pKitRoomTransportLoopbackTest.kt` — **157–205, 207–249** (one-based).
  SHA-256: `b473017536585291ea1a3201e44433278e7bc59b5eb97a89cbfc9c3871ccad6f`.

**Expected versus actual / reachable root cause:** Expression-bodied fun test() = runBlocking { ... } infers AssertK assertion or Throwable return values. Actual bytecode has non-void @Test methods. Resolved Jupiter 5.10.1 filters them before execution. Compiled inspection finds 222 annotations/10 non-void; normal task reports 212 cases, 211 passing and one skipped. Expected: all enabled annotated regression tests are discoverable.

**Reproduction/proof:** Bytecode/descriptor reconciliation at the reviewed tree plus isolated Unit wrappers: all eight previously missing enabled bodies execute and pass. Original annotations remain unchanged, so ordinary registration is still defective. The two physical methods were neither enabled nor run.

**Counter-evidence/limits:** Not every runBlocking test is affected; Unit-returning endings are discovered. Returning an exception is not the same as throwing it. Physical ignores are intentional; passing fake-kit wrappers does not prove LAN.

**Recommended remediation and regression coverage (not implemented):** Make test methods explicitly Unit/block-bodied without removing assertions/teardown/ignores. Add compiled Jupiter signature/discovery checks (not a ban on value-returning TestFactory). Verify ten new descriptors with the two existing physical methods still skipped and eight enabled cases passing.

**Independent conclusion and full source trace:** [validations/ROOT-T3-session_cont.md](validations/ROOT-T3-session_cont.md).
- Execution `inspect-transport-tests-02`: Compiled metadata222annotations/10non-void; ordinary execution212reported cases,211pass/1skip. Evidence: [evidence/inspect-transport-tests-02/receipt.json](evidence/inspect-transport-tests-02/receipt.json), [evidence/inspect-transport-tests-02/test-receipts.json](evidence/inspect-transport-tests-02/test-receipts.json), [evidence/inspect-transport-tests-02/compiled-test-methods.json](evidence/inspect-transport-tests-02/compiled-test-methods.json).
- Execution `repro-pending-01`: Eight enabled missing bodies passed via isolated Unit-return wrappers; two ignored physical tests were not enabled or run. Evidence: [evidence/repro-pending-01/receipt.json](evidence/repro-pending-01/receipt.json), [evidence/repro-pending-01/test-receipts.json](evidence/repro-pending-01/test-receipts.json).

## M-C01 — Doctor recovery history can contradict the last resolved night

**CONFIRMED DEFECT / Low**. Finder `/root/mafia_cont`; separate validator `/root/whodunit_cont`.

**Scope/prerequisites:** Mafia local resume of a malformed but authenticated current snapshot; living Doctor after a resolved night, consecutive protection disabled. No ordinary producer or disk-authentication bypass found.

**Impact/severity:** Low, defense-in-depth: accepted recovery can permit consecutive protection or wrongly forbid another target, contradicting retained night history.

### Exact locations

- `/Users/abdelrahman/Projects/parlor/game-modes/mafia/src/commonMain/kotlin/com/parlor/games/mafia/snapshot/MafiaSnapshotRecovery.kt` — **119–124, 176–218, 346–423** (one-based).
  SHA-256: `a2d00bc13749ddbce8759174ccc7729ace240d4a0d23f806306860c23ad15121`.

**Expected versus actual / reachable root cause:** Normal ResolveNight writes previousDoctorProtect and nightLog from the same effective target. Recovery validates the private field and log independently but never binds their final values. Change only the Doctor private previous target to null or another legal seat; codec/resume accepts it. UI/reducer then use that private field to decide legality. Expected: current authenticated recovery preserves reducer-reachable history invariants.

**Reproduction/proof:** Five-seat deterministic session with Doctor protecting X on night 1 and Mafia/civilians skipping; reach night 2, mutate only the synthetic recovered private history. One witness passes, rejection assertion fails. It does not demonstrate corruption through normal UI actions.

**Counter-evidence/limits:** Normal producer is consistent; terminal cleanup clears private fields intentionally. Peer projections lack host logs by design. Dead/absent Doctor and skipped nights need precise exceptions, not secret transmission.

**Recommended remediation and regression coverage (not implemented):** Bind last protection to the latest retained resolved-night record for applicable nonterminal canonical snapshots. Test wrong/null value, before-first-night, skips, dead Doctor, bounded history and PostGame. Do not send host history to peers to repair local validation.

**Independent conclusion and full source trace:** [validations/MF-C1-whodunit_cont.md](validations/MF-C1-whodunit_cont.md).
- Execution `repro-mafia-01`: Two tests: witness passes, required rejection assertion fails. Malformed authenticated snapshot only. Evidence: [evidence/repro-mafia-01/receipt.json](evidence/repro-mafia-01/receipt.json), [evidence/repro-mafia-01/test-receipts.json](evidence/repro-mafia-01/test-receipts.json).

## WD-C3 — Active final-two Elimination snapshot accepted

**CONFIRMED DEFECT / Low**. Finder `/root/whodunit_cont`; separate validator `/root`.

**Scope/prerequisites:** Whodunit Elimination current authenticated snapshot or invalid host projection with an active final-two state. Not normal reducer output, Classic Vote, Mafia, or a cryptographic bypass.

**Impact/severity:** Low, defense-in-depth: accepted impossible recovery can reopen a decided game, change the winner and produce a state that can no longer be saved.

### Exact locations

- `/Users/abdelrahman/Projects/parlor/game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/domain/state/WhodunitStateValidator.kt` — **613–729, 762–775** (one-based).
  SHA-256: `e356c459d1b6ec64d20e6dcb7cf0aacb583f67fa1b35e8674a42d6d6ffca702e`.

**Expected versus actual / reachable root cause:** Normal reducer ends at two survivors. Active-phase canonical/peer validators omit that reachability constraint. A canonical-looking Round4 two-survivor ballot is accepted by codec, local load/content checks and own-peer validators. A subsequent ordinary ballot adds a fifth elimination, changes PlayersWin/KillerWins and violates completed-round history at the next encode. Expected: reject impossible current data rather than repair/reopen it.

**Reproduction/proof:** Actual six-seat bundled Last Dinner, seed 73, real reducer through four innocent eliminations; valid final-two terminal round-trip passes. Synthetic mutation to active Round4 is accepted; remaining innocent accuses killer and killer abstains, creating an unsaveable fifth elimination. Full witness passes; required-rejection assertion fails.

**Counter-evidence/limits:** An existing defensive reducer test intentionally constructs impossible active data; that does not make it an admissible current save. No normal producer or storage bypass is claimed. Legacy migration is a separate policy.

**Recommended remediation and regression coverage (not implemented):** Reject active Elimination Round/TiedRevote with two or fewer survivors at canonical and peer validation boundaries. Test actual bundled six-seat codecs/load/content/peer paths, valid terminal state, early end and replay. Keep defensive fallback tests separate from allowed recovery states.

**Independent conclusion and full source trace:** [validations/WD-C3-root.md](validations/WD-C3-root.md).
- Execution `repro-pending-01`: Two tests: reachable6seat legal-terminal witness passes, malformed active-final-two snapshot rejection assertion fails. Evidence: [evidence/repro-pending-01/receipt.json](evidence/repro-pending-01/receipt.json), [evidence/repro-pending-01/test-receipts.json](evidence/repro-pending-01/test-receipts.json).

## M-C03 — Legal long player name can erase final-role text in Mafia post-game row

**CONFIRMED DEFECT / Low**. Finder `/root/mafia_cont`; separate validator `/root`.

**Scope/prerequisites:** Mafia final-role screen in local and LAN modes; legal 32-character wide name and compact layout. Confirmed manifestation is final-role width only.

**Impact/severity:** Low: intentionally public final role can disappear visually, impairing post-game explanation.

### Exact locations

- `/Users/abdelrahman/Projects/parlor/game-modes/mafia/src/commonMain/kotlin/com/parlor/games/mafia/ui/screens/postgame/PostGameScreen.kt` — **43–80** (one-based).
  SHA-256: `051c841fc161560caaffd140014e07ca741375851a86bc45e8a6fe999b94f4e4`.

**Expected versus actual / reachable root cause:** PostGameScreen places two unweighted Text children in a SpaceBetween Row. Row measures the long first child using available width and gives zero remaining width to the role. SpaceBetween does not reserve trailing width. Both routers pass legal names unchanged. Expected: supported names do not erase required result information.

**Reproduction/proof:** Unchanged production composable/theme rendered on Desktop at 320x640 dp, density/fontScale 1, five roles, policy-valid 32 W name. Role bounds left=right=256 dp; required nonzero width assertion fails. Prior audit configuration/compile failures are not layout evidence.

**Counter-evidence/limits:** Vertical scrolling does not solve horizontal starvation. Accessibility semantics may retain the text; no screen-reader failure or other proposed setup/tally clipping is confirmed. Mobile geometry remains unexecuted.

**Recommended remediation and regression coverage (not implemented):** Reserve role space or weight/truncate/reflow the name. Test compact/landscape/large-text/RTL layouts for both modes without reducing allowed names or accessibility scaling. No game-rule or projection change is needed.

**Independent conclusion and full source trace:** [validations/M-C03-root.md](validations/M-C03-root.md).
- Execution `repro-ui-03`: One production-composable measurement failure, final-role width0 at320dp. Earlier harness failures are not application-defect evidence. Evidence: [evidence/repro-ui-03/receipt.json](evidence/repro-ui-03/receipt.json), [evidence/repro-ui-03/test-receipts.json](evidence/repro-ui-03/test-receipts.json).

## DS-C01 — iOS Follow System can retain the app's explicit language after restart

**CONFIRMED DEFECT / Low**. Finder `/root/mafia_cont`; separate validator `/root`.

**Scope/prerequisites:** iOS app language preference after explicit language, process termination without disposal and restart, then Follow System. Both games' chrome; no reducer effect.

**Impact/severity:** Low: System can retain the previous explicit language and direction instead of following the platform preference.

### Exact locations

- `/Users/abdelrahman/Projects/parlor/shared/design-system/src/iosMain/kotlin/com/parlor/designsystem/localization/LocalAppLocale.ios.kt` — **33–55** (one-based).
  SHA-256: `eb10fc361fc420635023b2e81ceda37965bb5a485d8bffa32f9df9178cda6977`.

**Expected versus actual / reachable root cause:** Locale effect captures resolved defaults, writes an app-domain override, and later restores the captured value without tracking domain ownership. After restart the capture can already be the old app override; selecting null/System restores that same value and the null effect does not clear it. Expected: selecting System releases only the app-owned override.

**Reproduction/proof:** Two separate native Swift/Foundation processes using an isolated UUID defaults suite and synthetic key demonstrate persisted English recaptured/restored over an Arabic fallback. This is equivalent preference-ownership evidence, not actual iOS UI/AppleLanguages/device execution.

**Counter-evidence/limits:** Same-process switching can work when original ownership is correct. Apple's resolved defaults search and official Compose use of AppleLanguages do not make the API itself a defect. External OS per-app overrides must not be indiscriminately deleted.

**Recommended remediation and regression coverage (not implemented):** Track/reconcile app-owned override separately from platform fallback across restart/System. Test English/Arabic/System, restart, interrupted writes and OS per-app changes; separately verify UIKit/resource direction without recreating active sessions.

**Independent conclusion and full source trace:** [validations/DS-C01-root.md](validations/DS-C01-root.md).
- Execution `native-preference-01`: Two separate native Foundation processes and isolated synthetic suite; suite removed afterward. Not an iOS application runtime test. Evidence: [evidence/native-preference-01/receipt.json](evidence/native-preference-01/receipt.json).

## DS-C02 — reusing live-queue-derived toast IDs can hide a new notification permanently

**CONFIRMED DEFECT / Low**. Finder `/root/mafia_cont`; separate validator `/root`.

**Scope/prerequisites:** Shared toast host on Android/iOS and Desktop development; a distinct notification arrives immediately after automatic dismissal before an empty UI frame.

**Impact/severity:** Low: a queued error/feedback toast can remain invisible and miss automatic expiry.

### Exact locations

- `/Users/abdelrahman/Projects/parlor/shared/design-system/src/commonMain/kotlin/com/parlor/designsystem/components/ParlorToastHost.kt` — **75–101, 169–188** (one-based).
  SHA-256: `555c56e57bfc6a7173d9034136f6a3a4de1dce9a068a66b3c04ca4da8a32d3f9`.

**Expected versus actual / reachable root cause:** IDs derive from the live queue. A expires, host remembers visible=false, queue empties; B receives A's old ID. Conflation can hide the empty frame, preserving the same keyed composition and completed expiry effect. Expected: distinct notification lifetimes receive distinct effect identity.

**Reproduction/proof:** Production Desktop state/host/theme; a synthetic producer uses only public show and follows automatic dismissal. Dismissal and queued replacement assertions pass; missing replacement semantics assertion fails. Later expiry assertion is not reached; nonrestart follows from source, not a second runtime assertion.

**Counter-evidence/limits:** Bounded queue, pure CAS and uniqueness within the live queue are correct but insufficient across lifetimes. Coalescing same text is not the fixture, and separated empty frames work. No session/network privacy defect is established.

**Recommended remediation and regression coverage (not implemented):** Use atomic lifetime-unique identity independent of queue contents, with defined overflow behavior. Test same-frame expiry/replacement, concurrent producers, coalescing, eviction and stale timers while keeping bounds.

**Independent conclusion and full source trace:** [validations/DS-C02-root.md](validations/DS-C02-root.md).
- Execution `repro-pending-01`: One expected failure exercising production toast visibility after auto-dismiss/replacement. Later expiry assertion was not reached. Evidence: [evidence/repro-pending-01/receipt.json](evidence/repro-pending-01/receipt.json), [evidence/repro-pending-01/test-receipts.json](evidence/repro-pending-01/test-receipts.json).

## DS-C03 — light-theme recovery Leave labels use dark text on black

**CONFIRMED DEFECT / Low**. Finder `/root/mafia_cont`; separate validator `/root/whodunit_cont`.

**Scope/prerequisites:** Light/System-light theme in ReconnectingOverlay and HostDisconnectedOverlay, both games' LAN recovery surfaces.

**Impact/severity:** Low, legibility/accessibility: active Leave labels have 2.70034:1 contrast, below both normal and large-text minimums.

### Exact locations

- `/Users/abdelrahman/Projects/parlor/shared/design-system/src/commonMain/kotlin/com/parlor/designsystem/components/ReconnectingOverlay.kt` — **57–65, 107–112** (one-based).
  SHA-256: `b4865b98b90a9feff58123cdd7423526a9910c6dc772303fffe7fdf30c30e2b1`.
- `/Users/abdelrahman/Projects/parlor/shared/design-system/src/commonMain/kotlin/com/parlor/designsystem/components/HostDisconnectedOverlay.kt` — **44–52, 82–87** (one-based).
  SHA-256: `17f851f027ca029dba7277c4cd353e9c98bd800ba997c3e1906746420be7f624`.
- `/Users/abdelrahman/Projects/parlor/shared/design-system/src/commonMain/kotlin/com/parlor/designsystem/components/ParlorButton.kt` — **78–80, 139–142, 170–174** (one-based).
  SHA-256: `d3d5d4038e8fa499ed2c6e95a851cbd5f8da1a37fb35fed97b716c7a34007c38`.
- `/Users/abdelrahman/Projects/parlor/shared/design-system/src/commonMain/kotlin/com/parlor/designsystem/tokens/ParlorColors.kt` — **134, 150** (one-based).
  SHA-256: `99c60bb3b07cd6295c51adf7d9cf684bf5890d401395d323e1d7a4f11c6bb5e8`.

**Expected versus actual / reachable root cause:** Recovery paints opaque black coverScreen; enabled Ghost button explicitly uses Light textSecondary #55524D rather than a cover token. Actual theme/accent scopes preserve this pair. Independent W3C sRGB arithmetic proves the contrast. Expected: active recovery actions meet the project's existing text-contrast contract.

**Reproduction/proof:** Deterministic source/color proof. Select Light and reach startup reconnecting or required-seat disconnect; the resolved pair is as above. No actual component screenshot or physical display measurement was executed for this candidate.

**Counter-evidence/limits:** Dark palette contrast is adequate. Other overlay text uses proper tokens; ContinueWithoutDialog has an elevated surface and is not another manifestation. Clickability/semantics remain, so do not claim an invisible or inaccessible-by-screen-reader action.

**Recommended remediation and regression coverage (not implemented):** Use a cover-aware button style/token without changing ordinary light-surface Ghost colors. Add real foreground/background pairs for both themes/accents and component rendering/semantics tests; retain the privacy cover and Leave callback.

**Independent conclusion and full source trace:** [validations/DS-C03-whodunit_cont.md](validations/DS-C03-whodunit_cont.md).

## WD-C2 — Bundled Whodunit chronologies contain incompatible facts

**CONFIRMED DEFECT / Low**. Finder `/root/whodunit_cont`; separate validator `/root/mafia_cont`.

**Scope/prerequisites:** Four bundled Whodunit stories at their supported six-seat count, both modes and shipping platforms. Character-specific contradictions require the relevant seeded killer assignment.

**Impact/severity:** Low, authored-content consistency: players receive incompatible purported factual timelines and final explanations. Not rule nondeterminism or privacy leakage.

### Exact locations

- `/Users/abdelrahman/Projects/parlor/game-modes/whodunit/src/commonMain/composeResources/files/cases/last-dinner.json` — **152–165, 280–284, 335–336, 358–360, 376** (one-based).
  SHA-256: `c36b20ed19f94b7eea5809f7c2f1ef38be1f97ac6e2598a8a5de0233ee9e89af`.
- `/Users/abdelrahman/Projects/parlor/game-modes/whodunit/src/commonMain/composeResources/files/cases/layla-halabi.json` — **18, 79–87, 215–219** (one-based).
  SHA-256: `954752e02c72ab8b4ac8fe7b03598857252c539ba07d1f781683be3b42c07d1d`.
- `/Users/abdelrahman/Projects/parlor/game-modes/whodunit/src/commonMain/composeResources/files/cases/jasmine-ring.json` — **75–88, 323–325** (one-based).
  SHA-256: `d1397ac1e0dc3669d3e5ff5e147aabf88647bc6726621cb7239392eb4e4cd9ca`.
- `/Users/abdelrahman/Projects/parlor/game-modes/whodunit/src/commonMain/composeResources/files/cases/khan-el-khalili.json` — **76–90, 193–211, 329–331** (one-based).
  SHA-256: `b1b1d73cedc751f321c51f1e1523b74e57d515d408a7760add328c73c8dcc5e4`.

**Expected versus actual / reachable root cause:** Catalog -> offline payload validator -> seeded assignment -> authorized dossier/objective clues -> final narrative presents unchanged authored facts. Last Dinner: factual cigar at 9:00 vs unlit until/lit at 9:15; Layla Halabi: public outage 9:45 vs universal 9:05/already out at 9:35; Jasmine Ring: cellar entry 10:50/attack 11:10–12 vs full hour hiding; Khan el-Khalili: poison 9:40 vs minute before 10:15 records alibi. Fake alibis are separately labelled and do not explain these contradictions.

**Reproduction/proof:** Complete data-to-display source proof; compare the relevant authorized dossier/objective clue and final narrative during a supported game. No seed search or physical playthrough was executed by the validator. Four manifestations are one grouped content issue.

**Counter-evidence/limits:** Corniche/Saidi age-order wording and Zamalek two/three-year embezzlement duration are editorial ambiguities, not approved manifestations. Schema validation proves shape/references, not semantic chronology. Intentional lies must remain intentional.

**Recommended remediation and regression coverage (not implemented):** Content owner chooses canonical times; align factual method/timeline/clues/reveal without altering intended lies, reducers or sampling. Review content version/digest and saved-session compatibility. Test structural/catalog/clue invariants and targeted editorial facts across killer variants.

**Independent conclusion and full source trace:** [validations/WD-C2-mafia_cont.md](validations/WD-C2-mafia_cont.md).

## RL-C1 — Android artifact size guard mixes BSD/GNU stat output

**CONFIRMED DEFECT / Medium**. Finder `/root/whodunit_cont`; separate validator `/root/session_cont`.

**Scope/prerequisites:** Latent Android artifact validator on GNU/Linux (declared Ubuntu runner); CLI code exists but candidate workflows and identity are currently blocked.

**Impact/severity:** Medium release-tooling defect: otherwise in-bound artifacts fail size preflight before substantive validation.

### Exact locations

- `/Users/abdelrahman/Projects/parlor/scripts/release/validate_android_artifact.sh` — **20–28** (one-based).
  SHA-256: `1d0d423a3e149d9db85a17d82bfff9f9998239202d04a741e0779ea8218c7428`.

**Expected versus actual / reachable root cause:** BSD-first stat -f %z treats %z as a filename under GNU stat. Failure on that operand does not suppress filesystem stdout for the real artifact; numeric fallback appends its output into the same arithmetic operand. Both AAB and dependency-report guards are affected. Expected: a regular bounded file produces one numeric byte count.

**Reproduction/proof:** Complete shell plus upstream coreutils source proof for GNU option/error/stdout behavior. No genuine GNU/Linux execution or real AAB validation took place on this Darwin host.

**Counter-evidence/limits:** BSD form works on macOS; stderr redirection does not remove stdout. Disabled workflows/identity guards remain effective. No current enabled upload or Store incident is claimed.

**Recommended remediation and regression coverage (not implemented):** Choose the platform form explicitly or use required Python stat, preserving regular-file/no-symlink and bounds. Test Linux/macOS preflight with spaces, missing, exact-limit and oversized synthetic files. Fix before RL-C2's later Linux signature step; do not activate publishing.

**Independent conclusion and full source trace:** [validations/RL-C1-session_cont.md](validations/RL-C1-session_cont.md).

## RL-C2 — Strict JAR verification lacks upload-certificate trust input

**CONFIRMED DEFECT / Medium**. Finder `/root/whodunit_cont`; separate validator `/root/session_cont`.

**Scope/prerequisites:** Latent Android signed-artifact validator with a normal registered self-signed upload certificate, after size preflight. Actual owner certificate and credentials remain uninspected.

**Impact/severity:** Medium release-tooling incompatibility: strict jarsigner can reject an otherwise legitimate registered upload signature before its approved fingerprint is checked.

### Exact locations

- `/Users/abdelrahman/Projects/parlor/scripts/release/validate_android_artifact.sh` — **35–57** (one-based).
  SHA-256: `1d0d423a3e149d9db85a17d82bfff9f9998239202d04a741e0779ea8218c7428`.

**Expected versus actual / reachable root cause:** Script invokes jarsigner -verify -strict -certs without a pinned public-cert truststore, under set -e. JDK 21 strict self-signed/untrusted-chain result returns bit 4; later fingerprint comparison is unreachable. Android's official upload-key flow legitimately uses a generated self-signed certificate. Expected: verify Android upload integrity and exact approved certificate without treating missing public-CA trust as a universal invalid signature.

**Reproduction/proof:** Exact JDK 21.0.11 implementation and official Android/JDK contract source proof. This finding was not validated by generating a key, signing an artifact, inspecting an owner's certificate or performing a Store operation. Disposable installation signing for a separate Android runtime gate is not evidence for this finding.

**Counter-evidence/limits:** A publicly trusted CA chain or explicitly approved truststore differs. Private keystore removal before validation is sound; do not retain secrets to work around this. Current identity/workflow gates block operation; RL-C1 fails earlier on Linux.

**Recommended remediation and regression coverage (not implemented):** Bind an ephemeral public-certificate-only truststore to the approved fingerprint, or use equivalently rigorous Android-aware verification. Do not blindly ignore exit 4 or remove strict checks. After separate authorization test good/wrong signer, tamper/unsigned entries, validity, algorithms and extra signers; real Store signing remains external.

**Independent conclusion and full source trace:** [validations/RL-C2-session_cont.md](validations/RL-C2-session_cont.md).

## RL-C3 — Play destination policy checked in an edit different from mutation

**CONFIRMED DEFECT / Medium**. Finder `/root/whodunit_cont`; separate validator `/root/session_cont`.

**Scope/prerequisites:** Latent Google Play promotion helper with an operator change between a deleted read edit and later mutation edit. Present CLI identity guard and workflow disablement prevent authorized production execution here.

**Impact/severity:** Medium release-policy defect: an intervening 5% rollout of the same legitimate candidate can be silently completed, bypassing explicit refusal to replace staged destinations.

### Exact locations

- `/Users/abdelrahman/Projects/parlor/scripts/release/store_api.py` — **388–393, 475–497, 641–699** (one-based).
  SHA-256: `80c511fc7e504aab6d66b0c7ec15789a8d9cbb7e5bc26b62e844524774840bed`.

**Expected versus actual / reachable root cause:** read_inventory creates/reads/deletes edit A. Source/destination/digest policies inspect A. Helper then inserts edit B and PUTs completed without checking B's copied state. Operator stages the same candidate between A deletion and B creation. B sees staged state but no Parlor guard; staged->completed is valid Google behavior. Expected: policy checks and mutation apply to the same edit snapshot.

**Reproduction/proof:** Real helper/codec logic with only HTTP request replaced by a bounded edit-snapshot model: between-edits guard assertion fails; change after mutation-edit insertion correctly invalidates and its counter-test passes. No actual Google API request, promotion or Store receipt was obtained.

**Counter-evidence/limits:** Google invalidates existing edits, not future ones. Workflow concurrency serializes these jobs, not Console operators. Immutable digest/candidate checks work and the witness uses the same valid candidate. Sibling upload races are not asserted without proof.

**Recommended remediation and regression coverage (not implemented):** Insert mutation edit first, read/validate all source/destination/bundle state inside it, then mutate/validate/commit the same edit. Clean rejected/idempotent exits, retain no-blind-retry and digest binding. Test both race intervals, absent source, validation failure and cleanup. Keep workflows disabled pending owner authorization.

**Independent conclusion and full source trace:** [validations/RL-C3-session_cont.md](validations/RL-C3-session_cont.md).
- Execution `repro-release-race-01`: Two mocked-HTTP tests: between-edits policy guard assertion fails; after-edit-insertion invalidation counter-evidence passes. Evidence: [evidence/repro-release-race-01/receipt.json](evidence/repro-release-race-01/receipt.json), [evidence/repro-release-race-01/test.log](evidence/repro-release-race-01/test.log).

