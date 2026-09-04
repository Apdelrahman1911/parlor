# Independent production-review findings register

This is the code-first register for the independent review that began at
`9cd4040a81c4f2f8fe6f5f161dabcd5351682c02`. Production source, executable
tests, resolved artifacts, and generated release outputs are authoritative.
Earlier plans, ledgers, and readiness labels are evidence only.

The original review was performed on
`codex/parlor-independent-review-9cd4040`; its reviewed remediations are now in
`main`. Preserved history includes
`8186f7d70786057b791bd5c1aa80ca868835ec37`,
`dff3fcb317fdb89e310db70eb2c44643672c8c6b`, and
`37a249676fd8d6de109800cd136352bdd55e32ee`; none was rewritten.

## Closure policy

`CLOSED` means the root cause was removed, a focused regression or structural
contract exists where practical, valid behavior still passes, a broader suite
passed after the change, and the exact fix commit is named. It does **not** mean
physical networking, signed-store delivery, accessibility-device behavior, or
legal/compliance gates passed. The final code-review verdict is deliberately
derived only after the forced exact-HEAD matrix and final read-only pass; this
register does not substitute for that receipt.

The broad intermediate matrix passed after the production changes through
`365e7a0`: `allTests`, `productionCheck`, `productionAppleCheck`, and
`productionStaticAnalysis` with strict dependency verification. After the
documentation changes, the documentation/content/game/static regression bundle
passed with `--rerun-tasks` at `d6133ef`. Those historical receipts are
superseded by the full-tree rerun below.

## 2026-09-04 full-tree re-review

A fresh file inventory and execution-path review covered every tracked module
again after the UI and Navigation 3 work. The frozen executable tree was
`0c7cc33e9668029fbabdb00c7c33429235c4a9c1` (tree
`2e99401ab29d8d8b975c46054252936695fababd`). The review traced both games from
setup through terminal recovery; pass-and-play and host/peer ownership; protocol
4.2 codecs, ordering, projections, and rejoin; protected storage and content;
Android/iOS/Desktop platform adapters; localization, accessibility, layout and
Back handling; and build/release workflows. No database, account backend,
internet matchmaking, spectators, host migration, or timed Mafia rounds exist,
so those product surfaces remain not applicable rather than untested features.

The following clean-tree commands passed at that executable head:
`productionCheck` (903 tasks), `allTests` (861 tasks),
`productionAppleCheck` (147 tasks),
`productionIosSimulatorRuntimeTests` (188 tasks), and
`scripts/release/validate_release_system.sh` (128 tests). Dependency
verification was strict where required. Generated build outputs were removed
and Gradle daemons stopped after each batch. Passing automation does not replace
the external gates listed below.

### Findings added by the re-review

| ID | Severity | Exact location and failure scenario | Root cause and resolution | Evidence / classification | Status |
|---|---|---|---|---|---|
| RR-INPUT-01 | Medium | `shared/networking/src/commonMain/kotlin/com/parlor/networking/room/RoomInputPolicy.kt`: Unicode such as `ß` or full-width letters could expand during uppercasing and become a valid-looking room code. | Whole-string Unicode uppercasing ran before the ASCII allowlist. Filter ASCII first and uppercase per character. | `RoomInputPolicyTest`; newly discovered defect; `5965baa`. | CLOSED |
| RR-PROTOCOL-01 | High | `shared/networking/src/commonMain/kotlin/com/parlor/networking/protocol/ProtocolValidation.kt`: peer envelopes accepted nonzero header sequences and sequenced host envelopes accepted zero, weakening directional ordering and deduplication domains. | A shared nonnegative check did not encode sender-specific invariants. Peer traffic now requires sequence `0`; sequenced host traffic requires a positive sequence. | `ProtocolValidationTest`; newly discovered defect; `2ac26e5`. Protocol remains exactly 4.2. | CLOSED |
| RR-STORAGE-01 | High | `composeApp/src/androidMain/kotlin/com/parlor/app/storage/AndroidSnapshotFileSystem.kt`: an Android `File.listFiles()` failure was treated as an empty snapshot directory, hiding protected or legacy saves. | Nullable platform I/O was normalized to absence. Listing now throws `SnapshotProtectionException` and preserves explicit recovery. | `AndroidSnapshotDirectoryListingTest`; newly discovered defect; `3ec396c`. | CLOSED |
| RR-SESSION-01 | High | `shared/session/src/commonMain/kotlin/com/parlor/session/passandplay/PassAndPlaySessionController.kt`: concurrent submits committed state in one order but could emit reducer event batches in another. | Event emission occurred outside the mutex without reserving a commit-order turn. Ordered completion tokens now serialize batches without holding the reducer mutex during suspension. | `OrderedEventEmissionTurnTest`; newly discovered defect; `bc77a46`. | CLOSED |
| RR-SESSION-02 | Critical | `shared/session/src/commonMain/kotlin/com/parlor/session/multidevice/AuthoritativeSessionCoordinator.kt`, `SessionStartHandshake.kt`, `shared/networking/src/commonMain/kotlin/com/parlor/networking/room/LocalRoom.kt`, and `shared/transport-p2p/src/commonMain/kotlin/com/parlor/transport/p2p/P2pKitRoomTransport.kt`: a host terminal frame could revoke peer rejoin state before full protocol/revision validation, while failed credential deletion could still be presented as a completed end. | Physical authentication and logical terminal acceptance were one premature operation. The transport now stages bounded frames; the session layer validates them; an ownership-checked, rollback-safe transaction durably revokes the credential before publishing terminal state. | Coordinator, handshake, and P2pKit lifecycle terminal-frame tests; newly discovered defect; `388cb27`. | CLOSED |
| RR-RECOVERY-01 | High | `shared/session/src/commonMain/kotlin/com/parlor/session/multidevice/ProcessMultiplayerSessionOwner.kt` and `shared/transport-p2p/src/commonMain/kotlin/com/parlor/transport/p2p/P2pKitRoomTransport.kt`: Leave after a failed cold resume had no retained room through which to erase the persisted credential. | Credential discard was exposed only by `LocalRoom`. `RoomTransport` now owns an identity-checked cold-discard transaction, injected into the process owner; failures remain retryable. | `ProcessMultiplayerSessionOwnerTest`, `P2pKitRoomTransportLifecycleTest`; newly discovered recovery defect; `02bf235`. | CLOSED |
| RR-CONTENT-01 | High | `shared/content/src/commonMain/kotlin/com/parlor/content/validation/AuthoredTextPolicy.kt`, `CaseSummaryValidator.kt`, and `game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/content/WhodunitPayloadValidator.kt`: authored summaries/payload strings allowed control, bidi-formatting, or malformed surrogate characters. | Length/nonblank checks did not enforce safe display text. Shared authored-text validation now rejects unsafe code points at both summary and payload boundaries. | `CaseSummaryValidatorTest`, `WhodunitPayloadHardeningTest`; newly discovered defect; `acee9e6`. | CLOSED |
| RR-WHO-01 | High | `game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/domain/state/WhodunitStateValidator.kt`: a terminal peer projection could name one killer in the verdict while its clue history was only reachable for another character. | Clue reachability considered any privacy-compatible killer instead of the terminal verdict character. Terminal validation now binds both. | `WhodunitSnapshotValidationTest`; newly discovered projection-integrity defect; `cf589ec`. | CLOSED |
| RR-WHO-02 | Medium | `game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/ui/screens/reveal/RevealStageScreen.kt`: Continue was actionable before the final reveal narrative became visible/accessible, allowing the ceremony to be skipped accidentally. | Action availability was not tied to animation/reduced-motion completion. The button is now disabled until the narrative stage is accessible. | `WhodunitAccessibilitySemanticsTest`; newly discovered UI defect; `3db3d68`. | CLOSED |
| RR-NAV-01 | High | `shared/design-system/src/commonMain/kotlin/com/parlor/designsystem/localization/ProvideAppLanguage.kt`, `composeApp/src/desktopMain/kotlin/com/parlor/app/PlatformBackHandler.desktop.kt`, and `composeApp/src/iosMain/kotlin/com/parlor/app/PlatformBackHandler.ios.kt`: locale changes could recreate remembered app/session state; Desktop Escape and iOS edge Back bypassed the shared guarded-exit policy. | Locale provisioning keyed the subtree, while two platform adapters were no-ops. State is retained across language changes and both adapters now register with Navigation Event/Navigation 3. | Locale-preservation, Desktop Back, app Back-policy, and Apple compile/runtime tests; migration regressions; `79b1cec`, `ad1b815`, `9196e88`. | CLOSED |
| RR-UI-01 | Medium | `ParlorSafeArea.kt`, `ParlorToastHost.kt`, `SessionExitControls.kt`, `StickyActionBar.kt`, Whodunit lobby/case picker: safe-area spacing could clip content, toasts/actions could overlap it, a lobby row was not localized, and an empty filtered catalog lacked an explanation. | Edge-to-edge migration distributed inset ownership inconsistently and missed two localized states. Centralized safe-area/overlay/sticky-action measurement and added English/Arabic copy. | Safe-area, toast, session-exit, sticky-layout, localization parity, and case-picker tests; migration regressions; `bd7bca1`, `f201781`, `e8e1f42`, `272112b`, `9a55346`, `72ab8bf`. | CLOSED |
| RR-DOC-01 | Low | `IosSettingsKeyValueBacking.kt`, `PersistentSettingsStore.kt`, `Settings.kt`, `docs/PRODUCTION_ARCHITECTURE.md`: prose implied synchronous durability although iOS writes are asynchronously serialized. | Documentation overstated the API guarantee. Comments now distinguish immediate in-process publication from ordered eventual persistence. | Documentation drift, no runtime defect; `926e9e0`. | CLOSED |
| RR-RELEASE-01 | High | `iosApp/iosApp.xcodeproj/project.pbxproj` and `scripts/release/normalize_embedded_apple_framework.sh`: case-sensitive packaging could embed `composeApp.framework` although the Mach-O identity is `ComposeApp`. | Xcode/KMP output casing was not normalized or fail-closed. An idempotent shell step validates exactly one framework and canonical executable. | Shell unit tests, workflow contract, Apple linkage/wrapper builds; newly discovered release defect; `27bebac`. | CLOSED |
| RR-BUILD-01 | Medium | `composeApp/build.gradle.kts`, `config/android-lint-accepted-warnings.txt`, `docs/ANDROID_LINT_TRIAGE.md`: Navigation 3 dependency advisories were unreviewed and lint changed equivalent dependency warning IDs/messages across invocations. | The accepted inventory depended on unstable renderer text/IDs. The verifier now canonicalizes only the reviewed dependency-warning family while retaining exact deterministic inventory comparison. | Forced lint plus `AndroidReleaseLintContractTest`; newly discovered gate defect; `20a02c5`, `b909b58`. | CLOSED |
| RR-TEST-01 | Medium | `shared/transport-p2p/src/desktopTest/kotlin/com/parlor/transport/p2p/P2pKitRoomTransportLifecycleTest.kt`: the admission-rate-limit test timed out while waiting for four physical-session closes during a concurrent `productionCheck`. | The rate-limit assertion was coupled to four real-time 100 ms best-effort rejection-flush delays. It now waits for the admission rejection, which is the contract under test; separate real-time and virtual-time tests retain rejection-before-close coverage. | Reproduced at `89d1ec9`; focused test and the complete `:shared:transport-p2p:desktopTest` passed after the change; newly discovered test/gate flake, not a production defect; `5a1b1de`. | CLOSED |

The visual redesign, icon conversion, edge-to-edge migration, and single-engine
Navigation 3 migration in `26ac4b0`, `42ec623`, `526338e`, `2546a05`,
`c6af17e`, and `db0c8c3` were reviewed feature changes, not defect closures.
They preserve typed routes, application-owned stack state, binding-owned game
composition, and the host-authoritative session boundary.

## Findings

| ID | Severity / confidence | Component and exact location | Root cause, reachable scenario, and production impact | Root-cause fix and compatibility | Dedicated regression evidence | Fix commit(s) | Status |
|---|---|---|---|---|---|---|---|
| IR-BOUNDARY-01 | High / high | `MafiaActionCodec`, `WhodunitActionCodec`, both action authorities/reducers/validators; `KtorRemoteCaseDataSource`; `FileBackedSnapshotStore`; `ResumableCredentialStore` | Active serialized actions without shipping semantics and permissive UTF-8/projection boundaries let malformed or reducer-impossible input reach trusted game/storage paths. A modified peer or corrupt record could obtain a silent no-op, inconsistent state, or ambiguous decode. Android/iOS/Desktop shared paths were affected. | Removed unsupported active actions, made codecs/authority explicit, added game-specific structural projection validation, and fail-closed strict UTF-8 decoding. Valid current wire/snapshot data remains compatible; unsupported legacy mutations decode only where explicitly versioned and cannot mutate. | `MafiaActionCodecTest`, `MafiaPeerPrivateTargetGateTest`, `WhodunitActionCodecTest`, `WhodunitActionAuthorityTest`, `WhodunitPeerProjectionBoundaryTest`, `WhodunitSnapshotValidationTest`, `OfflineRemoteCaseDataSourceTest`, `FileBackedSnapshotStoreTest`, `ResumableCredentialStoreTest`. | `849feef` | CLOSED |
| IR-LIFE-01 | High / high | `PeerConnectionTracker`; Mafia/Whodunit state/reducers; `HomeScreen`, `PlayModePickerScreen`, `ScreenHeader`, `ParlorBottomTabBar` | A peer lifecycle generation could accept a stale callback, and several shell/game accessibility paths lacked explicit semantics or exposed obsolete state strings. Stale connectivity could mutate a newer room; assistive UI could lose the actionable control context. | Added generation-aware peer transitions/idempotency and explicit accessibility semantics; removed stale states/resources. No protocol or persisted schema break. | `PeerConnectionTrackerTest`, `AndroidReleaseLintContractTest`, subsequent `ProductionUiAccessibilityContractTest`. | `b9d5121` | CLOSED |
| IR-CI-01 | Medium / high | `.github/workflows/production-verification.yml`; `ProductionVerificationWorkflowContractTest`; root/compose build tasks | CI actions/toolchains and the release gate graph were not immutable or complete enough to prove the same production truth as local verification. A protected branch could be green while static analysis, Apple qualification, or merged-manifest evidence was omitted. | Pinned actions/toolchains by immutable SHA/version, enforced Android/JVM/Apple/static/artifact tasks, and added executable workflow contract checks. Linkage remains labelled linkage, not runtime. | `ProductionVerificationWorkflowContractTest`; root `productionCheck` and `productionAppleCheck` task graphs; merged-manifest task output. | `1f112f9`, `fdd758a`, `ff92f92` | CLOSED |
| IR-APPLE-01 | Medium / high | `.github/workflows/production-verification.yml`; `ProductionVerificationWorkflowContractTest`; `IOS_SETUP.md`; `RELEASE_RUNBOOK.md`; `RELEASE_GATES.md` | The supported unsigned Swift Release-wrapper build was correctly single-architecture in CI, but neither its structural contract nor operator instructions required `ARCHS=arm64` plus `ONLY_ACTIVE_ARCH=YES`. Removing the flags could leave the contract green while a generic dual-architecture invocation failed during Kotlin framework assembly. | Made the single-architecture wrapper boundary executable policy in tests and current release instructions while retaining independent `iosSimulatorArm64`, `iosX64`, and device `iosArm64` linkage. No production binary, deployment target, or public API changed. The linked app remains minimum iOS 16.0; the observed 17.2-tagged ICU archive members contain constant data and no executable text. | Focused `ProductionVerificationWorkflowContractTest` (24/24 tasks executed); unsigned arm64 Release wrapper `xcodebuild`; Mach-O/load-command and symbol inspection; final `productionAppleCheck`. | `bb08c76` | CLOSED |
| IR-COMMAND-01 | Critical / high | `AuthoritativeSessionCoordinator`; `ProtocolValidation`; Mafia/Whodunit peer bridges and retained runtimes; `SessionController` implementations | Peer command completion, expected revision, and session close were not one coherent ownership contract. A disconnect, duplicate result, or concurrent command could complete against the wrong revision or leave intent ambiguous. | Host-bound actor validation, strict result semantics, one peer mutation in flight, authoritative revision waiting, sequence restoration from snapshots, and cancellation-aware close were centralized. Protocol advanced compatibly through explicit version rejection; no blind retry of non-idempotent actions. | `AuthoritativeSessionCoordinatorTest`, `ProtocolValidationTest`, `MafiaAuthoritativeLifecycleTest`, `MultiDevicePartyPlayContractTest`, `SessionStartHandshakeTest`, `BoundedPeerOutboxTest`. | `17bd615`, `a64c078`, `31c18a5` | CLOSED |
| IR-TEST-01 | Medium / high | `shared/networking-testing/InMemoryRoomBus`; module dependencies; `TestTransportIsolationContractTest` | The in-memory transport lived in a production session source set and test fixtures could accidentally validate a development implementation rather than production boundaries. | Moved the fake to a dedicated testing module and added a structural isolation contract. Shipping modules cannot reach it; game/session fixtures share the same explicit test dependency. | `TestTransportIsolationContractTest` plus all Mafia/Whodunit multi-device fixtures. | `56ccf0e` | CLOSED |
| IR-PROTOCOL-01 | High / high | `Protocol.kt`, `ProtocolValidation.kt`, `RoomMessageCodec`; P2P adapter; Whodunit action codec/router | Obsolete wire variants and weak semantic identity checks enlarged the attack/compatibility surface. Unsafe game/session/player identifiers or obsolete messages could be accepted even when no current reducer path existed. | Pruned obsolete current-wire variants, strictly validates bounded canonical identifiers and authenticated peer identity compatibility, and rejects unknown/unsafe current protocol input. Versioned incompatibility is explicit. | `RoomMessageCodecTest`, `ProtocolValidationTest`, `P2pKitRoomTransportLoopbackTest`, `MafiaPeerActionAuthorityTest`, `WhodunitMultiDeviceShapeTest`. | `b5e4877`, `96070ed`, `551bf27` | CLOSED |
| IR-LIFE-02 | High / high | `P2pKitRoomTransport`, `AppLifecycleRoomCoordinator`, platform P2P modules | Room cleanup used asynchronous callbacks that could be lost, collectors could be installed after a replay-zero event, and lifecycle registration raced foreground/background state. This could leak discovery/session jobs, miss the first connection/frame, or background a newly registered room after foreground. | Made cleanup lossless, arms accept/inbound collectors before advertisement/handoff, retires physical sessions deterministically on background, and serializes lifecycle plus room registration by generation. No P2pKit API change. | `TestTransportIsolationContractTest`, `P2pKitRoomTransportLifecycleTest` queued-dispatcher/startup/background cases, `AppLifecycleRoomCoordinatorTest`. | `eae771c`, `cc88a88`, `2b35a97`, `365e7a0` | CLOSED |
| IR-BUILD-01 | Medium / high | version catalog, KMP conventions, P2pKit build, clock/localization APIs, dependency verification/provenance | Deprecated time/localization/dependency APIs and a stale P2pKit toolchain contract made future builds fragile and could resolve an unreviewed platform variant. | Replaced deprecated APIs, aligned all targets on P2pKit `0.7.0-rc3`, pinned strict checksums/variants, and reconciled provenance/contracts. No casual dependency upgrade was used to hide lint advisories. | Full game suites after API migration; `P2pKitMavenProvenanceContractTest`; strict Android/JVM/iOS dependency insight; `productionCheck`. | `0d75fae`, `51054f1` | CLOSED |
| IR-WHO-01 | High / high | `WhodunitReducer`, `WhodunitStateValidator`, action/authority/phase, reveal and phase UI | Reveal/round logic had duplicated UI authority, obsolete private-review/structured paths, and state combinations the canonical reducer did not own. That permitted silent no-ops, double ownership, or snapshots unreachable from legal play. | Reducer is the only transition owner; the reveal contract is consolidated; unsupported actions/screens/resources were removed; phase, clue, terminal, elimination, and reveal history invariants are shared by runtime and recovery. Valid versioned snapshots remain accepted. | `WhodunitReducerProductionGuardsTest`, `SimultaneousCharacterRevealTest`, `RevealCompletionGateTest`, `TickerAndRerollTest`, `ContinueWithoutPlayerTest`, `WhodunitSnapshotValidationTest`. | `551f5de`, `0164be7`, `4a9890b`, `70c1d88`, `82ad9d5`, `ff014a0` | CLOSED |
| IR-L10N-01 | Medium / high | all `composeResources/values*`; `LocalizationResourceContractTest`; Whodunit fallback/default identity/formatting call sites | Fallback game text and the default identity bypassed resources, indexed format arguments were manually interpolated, and locale parity was not repository-wide. Arabic could show English/malformed values or miss a shipping key. | Moved copy/defaults to Compose resources, uses generated formatters, and mechanically enforces English/Arabic key and placeholder parity across every shipping bundle. | `LocalizationResourceContractTest`, `ProductionUiAccessibilityContractTest`, Whodunit screen tests. | `f6b02d7`, `dac1ace`, `97a8763`, `2f51f6d` | CLOSED |
| IR-CANCEL-01 | High / high | Mafia/Whodunit host and peer room bridges | Broad bridge catches could convert `CancellationException` into a normal network/game error, violating structured concurrency and prolonging stale work. | Every bridge rethrows cancellation before typed failure mapping; cleanup owns child jobs. Public APIs unchanged. | Existing host/peer lifecycle cancellation tests for both games plus type-aware Detekt catch-site enforcement. | `62d5e7a` | CLOSED |
| IR-INPUT-01 | Medium / high | `RoomInputPolicy`, `LocalRoom`, protocol validators, resumable credential validation, setup/player-entry UI | Code-point truncation could split Unicode, canonical room/player constraints diverged across UI/protocol/storage, and malformed resumable identity fields could cross the trust boundary. This could create unjoinable rooms, identity ambiguity, or corrupt display/storage. | Centralized bounded canonical input policy, preserves Unicode boundaries, validates room/game/player/fingerprint/generation fields at every authoritative boundary, and rejects control/unsafe values. | `RoomInputPolicyTest`, `ProtocolValidationTest`, `MafiaSessionRulesTest`, `WhodunitRulesInvariantTest`, `ResumableCredentialStoreTest`. | `39cc843`, `e6588fb`, `b579819` | CLOSED |
| IR-LIFE-03 | High / high | `PeerConnectionTracker`; `ProcessMultiplayerSessionOwner`; game bridges; P2P transport | Teardown did not join callback jobs, durable host-loss state was not reconciled after restart, peer resume ownership could overlap, and a reconnected peer did not always reactivate host transport. Stale work could mutate a replacement session or leave the host suspended. | Generation-owned callback jobs are joined; host-loss state is durable/terminal; resume is linearized; P2P host activation follows authenticated reconnect. | `PeerConnectionTrackerTest`, `HostLostTimeoutTest`, `ProcessMultiplayerSessionOwnerTest`, `P2pKitRoomTransportLifecycleTest`. | `5e1881b`, `776b462`, `64b9d1d`, `23edaea` | CLOSED |
| IR-SHELL-01 | High / high | `App.kt`, `AppBackPolicy`, `LocalResumeRouter`, `GameShellRegistry`/bindings, home recovery model | Multiplayer/local exits and recovery lookup were not transactional shell operations. Navigation could discard a live room or hide a corrupt/unreadable save as “no save,” and invalid route data could reach an owner. | Added binding-owned transactional exit, fail-closed route validation, explicit unreadable-save recovery UI, and distinct saved-game availability states. Game/session identifiers unchanged. | `AppBackPolicyTest`, `SessionExitBackPolicyTest`, `MultiplayerRouteRestorationTest`, `LocalResumeRouterTest`, `HomeRecoveryAvailabilityTest`, `GameShellRegistryExtensibilityTest`. | `ca3f277`, `4e95bad`, `1ce2ef0`, `b3d8fd0`, `ec7e8b9` | CLOSED |
| IR-SETTINGS-01 | Medium / high | `Settings`, `PersistentSettingsStore`, `SettingsMutationDispatcher`, settings screen; removed logger/telemetry/sound contracts | The UI exposed settings and platform abstractions with no production effect, creating false product promises and dead architectural seams. | Removed unimplemented contracts/options and routes mutations through the persisted, serialized settings owner. Persisted supported keys remain compatible. | `SettingsMutationDispatcherTest`, `PersistentSettingsStoreTest`, UI structural contracts. | `0736742` | CLOSED |
| IR-RNG-01 | High / high | `SessionSeedSource`, app DI, Mafia/Whodunit local flows, Whodunit replay reducer | Local games reused weak/default seeds and Whodunit replay could retain the same killer. Predictable/repeated assignments undermine fairness. | Production seed source uses platform entropy, tests inject deterministic sources, host remains the only multiplayer RNG owner, and replay excludes the prior killer when a valid alternative exists. | `ProductionSessionSeedSourceTest`, `FullGameDriveTest` replay coverage, Mafia role-distribution model tests. | `2433d59`, `47c275a`, `7cf0bea` | CLOSED |
| IR-BACKPRESSURE-01 | Critical / high | `BoundedPeerOutbox`, `DiscoveryCandidateScheduler`, `P2pKitRoomTransport`, process owner | Terminal sends, discovery candidates/retries, and session-expiry resources had ownership gaps that could grow retained work, duplicate terminal frames, or leave callers/jobs alive under slow/malicious peers. | Enforced frame/byte/rate/candidate/session bounds, idempotent atomic terminal transactions, deterministic timeout/close, and cleanup of transport plus owner resources. | `BoundedPeerOutboxTest` saturation/terminal/close tests, `DiscoveryCandidateSchedulerTest`, `P2pKitRoomTransportLifecycleTest`, `AuthoritativeSessionCoordinatorTest`. | `a4051a2`, `878ace2` | CLOSED |
| IR-REJOIN-01 | Critical / high | `ResumableCredentialStore`, P2P admission/resume, game retained runtimes, coordinator start barrier | Crash recovery could select an older credential generation; retained peers missed the replayed start offer because their lobby collector was gone, so transport reconnection did not produce resumable gameplay. | Selects the newest committed/staged admissible generation, validates all bound identities, retains the exact accepted offer, replays Ready, rejects mutated offers, and idempotently re-acks duplicate commits. Explicit Leave still destroys rejoin capability. | `ResumableCredentialStoreTest`, `P2pKitRoomTransportLifecycleTest`, `AuthoritativeSessionCoordinatorTest`, `MafiaAuthoritativeLifecycleTest`, `PartyConnectionEventsTest`. | `51be952`, `3cc8249` | CLOSED |
| IR-MAFIA-01 | High / high | `MafiaReducer`, observable/peer validators, snapshot codec/recovery, host/peer bridges | Recovery and peer projection accepted phase/role/vote/history combinations the reducer could not produce; missing role maps and setup disconnects could normalize into stuck or unsafe states. | Encodes complete reducer-reachability invariants, strict current codec validation, fail-closed missing roles, setup cancellation semantics, and phase-specific peer history validation. Invalid installation is atomic and leaves prior state unchanged. | `MafiaSnapshotRecoveryTest`, `MafiaSnapshotCodecTest`, `MafiaPeerSnapshotValidatorTest`, `MafiaReducerEdgeCasesTest`, `MafiaPeerPrivateTargetGateTest`. | `e6c32eb`, `7b39293`, `7e94641`, `60a8b5a` | CLOSED |
| IR-MAFIA-02 | High / high | Mafia setup draft/settings/action/reducer; retained host progression and multi-device phase router | Supported role formulas/settings were partly hidden in UI, setup start was a multi-action partial transition, and UI-owned night progression could disappear across lifecycle/recomposition or resolve twice. | Exposes every supported validated setting, starts atomically with one authorized action, retains host progression in process-owned runtime, removes passive/duplicate host controls, and models every supported player distribution. | `MafiaSetupDraftTest`, `MafiaActionCodecTest`, `MafiaReducerTest`, `FullGameDriveTest`, `MafiaMultiDeviceProgressionTest`, `NightResolutionTest`. | `9882491`, `85541f5`, `7137ec8`, `4fc52b6`, `8844512`, `26d8d5f` | CLOSED |
| IR-DISCONNECT-01 | High / high | Mafia/Whodunit host bridges, reducers, and validators | Physical P2P connections that never owned a player seat were conflated with game participants during disconnect recovery. A rejected/pending connection could create spectator-like dropped state, consume capacity semantics, or make snapshots unreachable. | Membership-to-seat binding is checked before game disconnect/reconnect/expiry actions; no spectator role exists. Reducers/validators reject non-roster recovery history. | `MafiaAuthoritativeLifecycleTest`, `MafiaSnapshotRecoveryTest`, `PartyConnectionEventsTest`, `WhodunitSnapshotValidationTest`, production guards. | `04e37d5` | CLOSED |
| IR-STORAGE-01 | High / high | Android/iOS secure and snapshot backings; `PlatformStorage`; local resume | Platform persistence lacked complete record bounds/authentication/migration safety and unreadable encrypted saves could be silently treated as absent. This risks data leakage, stale resurrection, or unrecoverable navigation. | Android AES-GCM Keystore/no-backup and iOS protected Keychain/file envelopes enforce bounds and authenticated records; migrations require durable protected writes; corrupt saves remain explicit/recoverable and cannot contaminate a new session. | `AndroidGcmRecordPolicyTest`, `AndroidSettingsKeyValueBackingTest`, `IosStorageSafetyTest`, `PlatformStorageMigrationTest`, `LocalResumeRouterTest`. | `39dbcad`, `1ce2ef0` | CLOSED |
| IR-CONTENT-01 | High / high | `DefaultCaseValidator`, `WhodunitContentIdentity`, `BundledWhodunitCatalog`, DI, content data sources | Unverified signatures, manually duplicated bundled lists, and weak identity/catalog checks could admit substituted, draft, duplicate, or unshipped case content. | Rejects unsupported/unverified signatures, makes packaged catalog authoritative, validates exact content/game/language/mode/roster identity, and verifies every bundled resource. Optional remote source remains nonshipping. | `DefaultCaseValidatorBoundaryTest`, `ArabicCaseValidationTest`, `CasePickerDiscoveryTest`, `WhodunitPayloadHardeningTest`, `BundledCaseLoadingTest`. | `4db636d`, `d12f456` | CLOSED |
| IR-CRYPTO-01 | Medium / high | `SecureHashes.ios.kt` | Native hashing did not safely handle an empty byte array, a valid input used by transcript/digest boundaries. A platform-only crash/divergence was possible. | Handles empty input without invalid native pointer access while preserving SHA behavior. | `SecureHashesTest` including empty vector on configured Apple tests. | `c07049d` | CLOSED |
| IR-STATIC-01 | Medium / high | root/convention build logic, `DetektConventionPlugin`, all production Kotlin source sets | Detekt/static analysis was either incomplete, untyped, or could miss broad catch sites/source sets; version ownership also diverged between Gradle/Xcode. A green gate could omit production code. | Repository-wide type-aware KMP Detekt is applied and aggregated, no blanket baseline, narrow reviewed suppressions only, CI enforcement, and a single version source for platform builds. | `productionStaticAnalysis`, per-module Detekt XML reports, `ProductionVerificationWorkflowContractTest`, strict catch-site/source-set scans. | `96df456`, `5a3ad80` | CLOSED |
| IR-DESKTOP-01 | Medium / high | Desktop `Main.kt`, P2P process/transport scopes | Closing the Desktop development window did not deterministically close multiplayer and cancel the process transport scope, masking leaks in shared lifecycle tests. | Desktop shutdown closes the process owner and cancels the P2P scope in order. No mobile artifact includes the Desktop target. | `DesktopProcessShutdownTest`. | `f4c607f`, `f9382cd` | CLOSED |
| IR-DEPS-01 | Low / high | root/module Gradle files, version catalog, app/content DI | Unconsumed declarations and dead dependencies obscured the actual runtime graph and produced misleading update/lint signals. | Removed only proven-unreachable declarations and added workflow/dependency contract assertions. Runtime P2pKit and game dependencies are unchanged. | Strict dependency verification, dependency reports, `ProductionVerificationWorkflowContractTest`, all module compilation. | `5cffb50` | CLOSED |
| IR-SHELL-02 | High / high | `GameShellRegistry` and bindings, play-mode picker, setup/local flows, home catalog | Setup UI and catalog entries still encoded game-specific/placeholder behavior instead of the registered binding contract. A third game could register a reducer yet require central shell changes. | Binding owns supported modes, player bounds, setup, local/host/peer/resume composition; removed placeholder catalog entries. A nonshipping fixture composes on Desktop without a central game switch. | `GameShellRegistryExtensibilityTest`, `GameShellRegistryCompositionTest`, `WhodunitModeChoiceTest`, `ProductionUiAccessibilityContractTest`. | `c3cbe3a`, `357e39f` | CLOSED |
| IR-SESSION-01 | High / high | `PassAndPlaySessionController` | Close and reducer commit/save were not linearized. A late mutation/save could resurrect state after explicit close. | One ownership mutex linearizes commit, persistence, publication, and close; late actions fail and close wins deterministically. | `PassAndPlaySessionControllerCloseRaceTest`, `RestoredStateTest`. | `dc7392a` | CLOSED |
| IR-UI-01 | Medium / high | shared design system; both game phase routers/screens; permission, settings, lobby/error UI | Several controls were passive/duplicated, full-screen overlays could hide the only exit/action, network failures were collapsed to generic copy, and reduced-motion/RTL/accessibility semantics diverged by platform. Players could double-own progression or become trapped without an actionable retry/leave path. | UI consumes authoritative state/results, removes duplicate host progression actions, preserves typed error keys, exposes bounded exit/retry/settings controls, enforces minimum targets/headings/live regions/RTL, and honors platform reduced motion. | `ProductionUiAccessibilityContractTest`, `TargetPickerSelectionTest`, `ScreenHeaderDirectionTest`, `ParlorMotionTest`, `HostLobbyOperationFeedbackContractTest`, network error key tests. | `55cd941`, `4eab031`, `8844512`, `01e33b4`, `1c7772d`, `c8e12a1` | CLOSED |
| IR-ANDROID-01 | Medium / high | `MainActivity` startup and storage DI | Synchronous settings preload could touch disk on the Android main thread before first composition, creating StrictMode/startup risk. | Preload is suspendable and dispatched to the owned IO context; composition receives completed state without screen-owned work. | `MainActivityStartupTest`, Android debug/release unit variants and release compilation. Physical StrictMode remains an external device gate. | `4ba3f9b` | CLOSED |
| IR-DOC-01 | Low / high | active contributor/architecture/content/accessibility/motion/mock-backend documents and production KDoc | Active docs/comments described historical mock remote content, Phase/Wave work, automatic motion/performance behavior, spectator terminology, or old protocol/content semantics as current truth. Operators could run the wrong qualification procedure. | Rewrote active contracts from current code, placed visible historical banners on superseded reports, and added source/document drift checks tied to runtime constants/owners. No runtime behavior changed to match stale prose. | `MultiplayerDocumentationContractTest`; repository protocol/Phase/Wave/spectator searches; content/game/static regression bundle at `d6133ef`. | `c9dbe83`, `d6133ef` | CLOSED |

## Independent revalidation of P2P-01 through P2P-11

The earlier P2P labels were rechecked against current code, not inherited from
`P2P_REMEDIATION_STATUS.md`.

| Original ID | Current code evidence | Independent regression evidence | Disposition |
|---|---|---|---|
| P2P-01 lifecycle | Process-owned room/runtime, `AppLifecycleRoomCoordinator`, background retirement, generation-safe callbacks; host loss remains terminal with no migration. | `AppLifecycleRoomCoordinatorTest`, `PeerConnectionTrackerTest`, both game lifecycle suites, P2P lifecycle tests. | Code CLOSED; physical background/lock/OS-kill remains external. |
| P2P-02 rejoin | Protected opaque credentials; fingerprint/player/room/game/generation/expiry binding; digest-only host storage; transactional rotation; Leave deletion; retained start handshake. | `ResumableCredentialStoreTest`, P2P lifecycle admission/resume matrix, retained peer rejoin tests. | Code CLOSED; process-death device proof remains external. |
| P2P-03 commands | Authenticated peer-to-actor binding, IDs/sequences/revisions, explicit ack/reject/duplicate, no blind mutation retry, snapshot-carried next sequence. | Protocol/coordinator/both-game authority, stale/duplicate/simultaneous/snapshot tests. | Code CLOSED; physical simultaneous UI evidence remains external. |
| P2P-04 abuse/backpressure | Frame, byte, queue, rate, candidate, admission, diagnostic, and dedupe bounds with per-peer isolation. | Outbox saturation, flood/rate, oversize, diagnostics, admission and repeated cleanup tests. | Code CLOSED; device memory/battery/thermal observation remains external. |
| P2P-05 discovery | Deadline-owned bounded multi-candidate scheduler, dedupe, candidate-local wrong room, retry/backoff, disappearance and cancellation. | `DiscoveryCandidateSchedulerTest` and adapter late/wrong/transient/cancel cases. | Code CLOSED; real multi-room/network-switch evidence remains external. |
| P2P-06 main-thread initialization | Android P2pKit/identity/settings initialization is suspendable on injected IO ownership. | factory/dispatcher/cancellation and `MainActivityStartupTest`; Android build. | Code CLOSED; device StrictMode receipt remains external. |
| P2P-07 cancellation | Reviewed broad boundaries rethrow cancellation; NonCancellable is restricted to owned cleanup. | cancellation tests across factory/start/discovery/send/resume/storage and type-aware catch-site gate. | CLOSED. |
| P2P-08 capacity | Mutex-owned reservations count committed plus pending seats atomically. | deterministic concurrent final-seat/capacity tests. | Code CLOSED; three-device physical race remains external. |
| P2P-09 admission rollback | Explicit pending/offered/confirmed/committed/ready/acknowledged ownership with rollback on disconnect/send/cancel/timeout. | fault-injected admission stage matrix and repeated cleanup tests. | Code CLOSED; physical disconnect-during-approval remains external. |
| P2P-10 direct connect | Capability is false; room code is discovery filtering, not raw IP; no sidecar/provisioning transport is packaged. | capability/adapter and documentation contract tests; ADR-0002. | ACCEPTED PRODUCT LIMITATION, not a code defect or PASS for manual direct connect. |
| P2P-11 iOS permission | No invented preflight grant; operational only after real advertise/connect; typed actionable denial; Settings recovery resets only for retry. | permission mapping/recovery tests, plist contract, Apple compilation/linkage. | Code CLOSED; fresh-install denial/recovery remains external. |

## Commit coverage

The independent-review finding commits are attributable below. Later
issue-tracker remediations integrated by `1759655` retain their issue/PR
evidence and per-file generated-inventory attribution. Merge commits carry no
independent tree change and are omitted. This prevents a quickly fixed issue
from disappearing from the register without pretending that merge mechanics
are additional fixes.

| Finding/evidence group | Commits |
|---|---|
| IR-BOUNDARY-01 | `849feef` |
| IR-LIFE-01 | `b9d5121` |
| IR-CI-01 | `1f112f9`, `fdd758a`, `ff92f92` |
| IR-APPLE-01 | `bb08c76` |
| IR-COMMAND-01 | `17bd615`, `a64c078`, `31c18a5` |
| IR-TEST-01 | `56ccf0e` |
| IR-PROTOCOL-01 | `b5e4877`, `96070ed`, `551bf27` |
| IR-LIFE-02 | `eae771c`, `cc88a88`, `2b35a97`, `365e7a0` |
| IR-BUILD-01 | `0d75fae`, `51054f1` |
| IR-WHO-01 | `551f5de`, `0164be7`, `4a9890b`, `70c1d88`, `82ad9d5`, `ff014a0` |
| IR-L10N-01 | `f6b02d7`, `dac1ace`, `97a8763`, `2f51f6d` |
| IR-CANCEL-01 | `62d5e7a` |
| IR-INPUT-01 | `39cc843`, `e6588fb`, `b579819` |
| IR-LIFE-03 | `5e1881b`, `776b462`, `64b9d1d`, `23edaea` |
| IR-SHELL-01 | `ca3f277`, `4e95bad`, `1ce2ef0`, `b3d8fd0`, `ec7e8b9` |
| IR-SETTINGS-01 | `0736742` |
| IR-RNG-01 | `2433d59`, `47c275a`, `7cf0bea` |
| IR-BACKPRESSURE-01 | `a4051a2`, `878ace2` |
| IR-REJOIN-01 | `51be952`, `3cc8249` |
| IR-MAFIA-01 | `e6c32eb`, `7b39293`, `7e94641`, `60a8b5a` |
| IR-MAFIA-02 | `9882491`, `85541f5`, `7137ec8`, `4fc52b6`, `8844512`, `26d8d5f` |
| IR-DISCONNECT-01 | `04e37d5` |
| IR-STORAGE-01 | `39dbcad`, `1ce2ef0` |
| IR-CONTENT-01 | `4db636d`, `d12f456` |
| IR-CRYPTO-01 | `c07049d` |
| IR-STATIC-01 | `96df456`, `5a3ad80` |
| IR-DESKTOP-01 | `f4c607f`, `f9382cd` |
| IR-DEPS-01 | `5cffb50` |
| IR-SHELL-02 | `c3cbe3a`, `357e39f` |
| IR-SESSION-01 | `dc7392a` |
| IR-UI-01 | `55cd941`, `4eab031`, `8844512`, `01e33b4`, `1c7772d`, `c8e12a1` |
| IR-ANDROID-01 | `4ba3f9b` |
| IR-DOC-01 | `c9dbe83`, `d6133ef` |
| RR-INPUT-01 | `5965baa` |
| RR-PROTOCOL-01 | `2ac26e5` |
| RR-STORAGE-01 | `3ec396c` |
| RR-SESSION-01 | `bc77a46` |
| RR-SESSION-02 | `388cb27` |
| RR-RECOVERY-01 | `02bf235` |
| RR-CONTENT-01 | `acee9e6` |
| RR-WHO-01 | `cf589ec` |
| RR-WHO-02 | `3db3d68` |
| RR-NAV-01 | `79b1cec`, `ad1b815`, `9196e88` |
| RR-UI-01 | `bd7bca1`, `f201781`, `e8e1f42`, `272112b`, `9a55346`, `72ab8bf` |
| RR-DOC-01 | `926e9e0` |
| RR-RELEASE-01 | `27bebac` |
| RR-BUILD-01 | `20a02c5`, `b909b58` |
| RR-TEST-01 | `5a1b1de` |
| Reviewed feature migrations, no defect closure | `26ac4b0`, `42ec623`, `526338e`, `2546a05`, `c6af17e`, `db0c8c3` |
| Regression/review infrastructure, no product defect | `05a3c1b`, `64f58e6`, `b6dbb52`, `0738676` |

Some commits intentionally appear in two groups where one atomic change closed
two inseparable root causes (for example unreadable storage plus recovery UI,
or passive Mafia UI plus retained authoritative progression). No commit is used
as closure evidence without the tests named in the corresponding finding.

## External gates retained after code closure

- GitHub issue `#6` remains open: physical Android↔Android, iOS↔iOS, and both
  cross-platform host directions have no exact-candidate real-LAN receipt.
- Normal LAN and supported Android/iPhone hotspot topologies, including three
  devices, background/foreground, lock/unlock, network switch, process death,
  rejoin, host loss, and repeated sessions.
- GitHub issue `#230` remains open: the pinned Store identity
  `com.parlor.app` is a known collision. Owner-selected identifiers, signing,
  provisioning, Play Console, and App Store Connect setup are required; the
  disabled candidate/promotion workflows must not be re-enabled beforehand.
- Signed Play Internal and TestFlight artifacts, with artifact identity tied to
  the reviewed commit.
- Store qualification still requires Xcode `26.3` build `17C529`; the local
  machine has Xcode `26.5` build `17F42`, so its successful Apple linkage and
  simulator runs are not represented as the pinned Store-toolchain receipt.
- Physical TalkBack/VoiceOver, large text, RTL, contrast, and motion checks.
- Privacy disclosure, export-compliance, licenses/SBOM, store metadata, legal,
  incident response, and publisher-key ownership decisions.

These are not converted into automated PASS results and do not permit a
production-ready claim.

## Working-tree qualification boundary and verdict

After closing `RR-TEST-01`, clean commit
`fc29fa0b9d87e803937468e45021a515ed0f6920` (tree
`5c36eb77307770986657cd20e1e91a2bb6c69f37`) passed a fresh exact-HEAD matrix:
`productionCheck` (903 tasks), `allTests` (861 tasks),
`productionAppleCheck` (147 tasks),
`productionIosSimulatorRuntimeTests` (188 tasks), and
`scripts/release/validate_release_system.sh` (128 tests and 626 reviewed
inventory rows). Dependency verification was strict for every Gradle matrix
task. The three ignored P2pKit loopback tests still explicitly require two or
three physical LAN devices; skipped x64 runtime tasks are host-architecture
limitations, while x64 compilation/linkage passed in `productionAppleCheck`.
Build outputs were cleaned and Parlor Gradle daemons stopped after every batch.

### Preserved working-tree finding

| ID | Severity | Exact location and reproduction | Root cause and recommended fix | Classification / status |
|---|---|---|---|---|
| WT-RELEASE-01 | Medium | Uncommitted `scripts/release/tests/test_workflow_contract.py`, `WorkflowContractTest.test_mobile_release_kit_ios_signing_mapping_is_release_target_only`: the focused test fails because the parsed `Release` settings belong to the UI-test target and therefore lack the new Mobile Release Kit mappings. | A dictionary keyed only by `Debug`/`Release` accepts every target containing `PRODUCT_BUNDLE_IDENTIFIER`, so later UI-test configurations overwrite the application configurations. Select the two blocks whose exact `PRODUCT_NAME` is `$(APP_NAME)`, assert that exactly two matched, then build the dictionary. | Pre-existing uncommitted user work, not a committed-candidate regression; OPEN and intentionally not modified without owner approval. |

The fix above was independently exercised in an isolated worktree without
altering the primary checkout: its focused test passed, followed by all 130
release-system tests and inventory validation. The remaining Mobile Release
Kit changes are still uncommitted and outside the qualified tree; after owner
approval, the one-hunk test correction and the complete feature must be
reviewed, committed on a focused branch, and requalified together.

**Final verdict: NOT READY.** The blockers are the failing uncommitted release
work, open issue `#230` (Store identity/credentials/infrastructure), open issue
`#6` (real multi-device LAN evidence), and the remaining physical-device,
signed-artifact, Store, accessibility, and owner/legal gates above. No mock,
simulator, unsigned build, or source inspection is represented as satisfying
those external requirements.
