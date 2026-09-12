# File ledger — game-rules

Status: **read** = production/test source fully or substantially read. **skim** = enough to classify. **skip** = out of scope (UI/layout/transport) or not needed after inventory.

## Whodunit production — domain / snapshot / definition / content

| Path | Status | Notes |
|---|---|---|
| `game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/domain/reducer/WhodunitReducer.kt` | read | Full 1231-line reducer |
| `game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/domain/reducer/WhodunitReducerContext.kt` | read | Case + clock + random |
| `game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/domain/rules/WhodunitRules.kt` | read | Roster, case support, max rounds |
| `game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/domain/rules/WhodunitCluePolicy.kt` | read | Deterministic clue draw |
| `game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/domain/rules/WhodunitRoundPolicy.kt` | read | Authored discussion seconds |
| `game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/domain/phase/WhodunitPhase.kt` | read | Sealed phases |
| `game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/domain/state/WhodunitState.kt` | read | Public / private / hostOnly |
| `game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/domain/state/VoteState.kt` | read | Idle/Collecting/Tied/Resolved/NoResolution |
| `game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/domain/state/WhodunitStateValidator.kt` | read | Full 1082-line trust boundary |
| `game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/domain/state/PartyReadiness.kt` | read | Active roster = players − dropped |
| `game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/domain/party/WhodunitReadinessGate.kt` | read | Local auto-ack; no synthesize rolesViewed |
| `game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/domain/modes/WhodunitModes.kt` | read | Classic 4–8, Elimination 5–8 |
| `game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/domain/authority/WhodunitActionAuthority.kt` | read | HostOnly vs SelfActor |
| `game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/domain/action/WhodunitAction.kt` | read | Sealed vocabulary |
| `game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/domain/action/WhodunitActionCodec.kt` | read | Strict JSON, retired types |
| `game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/domain/event/WhodunitEvent.kt` | read | Events + Verdict + KillerWinCause |
| `game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/domain/projection/WhodunitProjectionPolicy.kt` | read | Vote-target redaction |
| `game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/snapshot/WhodunitSnapshotCodec.kt` | read | Envelope v1 + legacy bare |
| `game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/snapshot/WhodunitSnapshotFormat.kt` | read | kind/schemaVersion |
| `game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/WhodunitDefinition.kt` | read | Initial Setup state |
| `game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/WhodunitPlayModePolicy.kt` | read | Local entry = PassAndPlay only |
| `game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/WhodunitIds.kt` | read | `whodunit`, `classic-vote`, `elimination` |
| `game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/content/WhodunitPayloadValidator.kt` | read | Full content contract |
| `game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/content/WhodunitCase.kt` | read | Payload schema |
| `game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/content/WhodunitContentIdentity.kt` | read | SHA-256 identity |
| `game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/content/BundledWhodunitCatalog.kt` | read | 7 case ids |
| `game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/content/BundledWhodunitCases.kt` | read | Resource loader |

## Whodunit tests in scope

| Path | Status | Notes |
|---|---|---|
| `.../domain/WhodunitReducerProductionGuardsTest.kt` | read | Phase gates, ballot, pause, planted final-two |
| `.../domain/WhodunitRulesInvariantTest.kt` | read | Assignment, reroll, model drive, display names |
| `.../domain/WhodunitTerminalAndCluePolicyTest.kt` | read | Mode-restricted clues, final-round abstain |
| `.../domain/CluePolicyTest.kt` | read | Assertions on validator-illegal fixtures |
| `.../domain/PartyReadinessTest.kt` | read | Helper algebra only |
| `.../domain/rules/WhodunitPolicyGoldenTest.kt` | read | Cross-platform clue + nearest-bucket seconds |
| `.../authority/WhodunitActionAuthorityTest.kt` | read | Omits AdvanceFromCharacterReveal in host-only list |
| `.../action/WhodunitActionCodecTest.kt` | read | Retired actions, generation, UTF-8 |
| `.../snapshot/WhodunitSnapshotValidationTest.kt` | read (to ~250 + grepped rest) | Envelope, final-two, grace expiry |
| `.../snapshot/WhodunitSnapshotRoundTripTest.kt` | skim | Persistence round-trip |
| `.../snapshot/WhodunitLegacySnapshotGoldenTest.kt` | skim | Legacy bare decode |
| `.../snapshot/WhodunitResumeReconstructionTest.kt` | skim | Resume path |
| `.../snapshot/WhodunitCaseBindingTest.kt` | skim | Case identity binding |
| `.../snapshot/InMemorySnapshotFileSystem.kt` | skip | Test fixture |
| `.../flow/TiedRevoteTest.kt` | read | Real session drive; second-tie + deadlock regressions |
| `.../flow/ContinueWithoutPlayerTest.kt` | read | Disconnect/pause/expiry |
| `.../flow/FullGameDriveTest.kt` | skim | End-to-end drive |
| `.../flow/IntroAndBriefingReadinessTest.kt` | skim | Readiness |
| `.../flow/PauseRefuseLeaveTest.kt` | skim | Pause overlay |
| `.../flow/TickerAndRerollTest.kt` | skim | Timer + reroll |
| `.../flow/SimultaneousCharacterRevealTest.kt` | skim | Simultaneous reveal |
| `.../flow/PartyFlowControllerTest.kt` | skip | UI flow controller |
| `.../WhodunitPlayModePolicyTest.kt` | read | Solo/MultiDevice rejected |
| `.../testing/ValidatedWhodunitCaseFixture.kt` | read | Bypasses payload validator |
| `.../content/WhodunitPayloadHardeningTest.kt` | skim | Content rejects blanks |
| `.../content/ArabicCaseValidationTest.kt` | skim | Locale content |
| `.../content/BundledCaseLoadingTest.kt` | skim | Catalog vs disk |
| `.../content/WhodunitContentIdentityTest.kt` | skim | Digest stability |
| `.../content/CasePickerDiscoveryTest.kt` | skip | UI picker |
| `.../content/ResReadBytesProbeTest.kt` | skip | Resource probe |
| `.../projection/VoteRedactionTest.kt` | skim | Projection |
| `.../multidevice/QueuedActionSafetyTest.kt` | skim | Authority at session |
| `.../multidevice/WhodunitPeerProjectionBoundaryTest.kt` | skim | Peer install |
| `.../commonTest/.../WhodunitModeChoiceTest.kt` | skip | UI setup |

## Whodunit UI (out of scope — skip)

All `ui/**` Compose screens, components, timers, flow routers, DI. Listed so they are not silently omitted:

`WhodunitHostRoomBridge`, `WhodunitPeerRoomBridge`, `WhodunitRetainedMultiplayerRuntime`, `WhodunitGameFlow`, `WhodunitPhaseRouter`, `VoteTurnPolicy`, `PartyFlowController`, `DiscussionTickerLoop`, `RoundScreens`, `CharacterRevealScreens`, `RevealStageScreen`, `PauseOverlay`, `PrivacyConcern*`, `PostGameScreen`, `PeerWaitingForHostScreen`, `EliminationOutcomeScreen`, `VoteScreens`, setup screens, layout tests, remaining multidevice UI tests, `di/WhodunitDiModule.kt`.

## Mafia production — domain / snapshot / definition / settings

| Path | Status | Notes |
|---|---|---|
| `game-modes/mafia/src/commonMain/kotlin/com/parlor/games/mafia/domain/reducer/MafiaReducer.kt` | read | Full 1060-line reducer |
| `game-modes/mafia/src/commonMain/kotlin/com/parlor/games/mafia/domain/state/MafiaState.kt` | read | Public/private/hostOnly + logs |
| `game-modes/mafia/src/commonMain/kotlin/com/parlor/games/mafia/domain/state/MafiaRole.kt` | read | Role/Team/detectiveSeesAs |
| `game-modes/mafia/src/commonMain/kotlin/com/parlor/games/mafia/domain/state/MafiaObservableStateValidator.kt` | read | Public-shape invariants |
| `game-modes/mafia/src/commonMain/kotlin/com/parlor/games/mafia/domain/state/MafiaPeerSnapshotValidator.kt` | read | Peer install |
| `game-modes/mafia/src/commonMain/kotlin/com/parlor/games/mafia/domain/phase/MafiaPhase.kt` | read | Night carries coordination round |
| `game-modes/mafia/src/commonMain/kotlin/com/parlor/games/mafia/domain/rules/MafiaSessionRules.kt` | read | Classic-only, 5–16 |
| `game-modes/mafia/src/commonMain/kotlin/com/parlor/games/mafia/domain/rules/WinCheck.kt` | read | Town if 0 mafia; Mafia if ≥ town |
| `game-modes/mafia/src/commonMain/kotlin/com/parlor/games/mafia/domain/rules/NightResolution.kt` | read | Kill/save/inspect; sorted ties |
| `game-modes/mafia/src/commonMain/kotlin/com/parlor/games/mafia/domain/rules/VoteResolution.kt` | read | Day vote + revote policy |
| `game-modes/mafia/src/commonMain/kotlin/com/parlor/games/mafia/domain/rules/RoleAssignment.kt` | read | Seeded shuffle |
| `game-modes/mafia/src/commonMain/kotlin/com/parlor/games/mafia/domain/settings/MafiaSettings.kt` | read | Validation + timer reject |
| `game-modes/mafia/src/commonMain/kotlin/com/parlor/games/mafia/domain/settings/MafiaSettingsPresets.kt` | read | 5–16 presets |
| `game-modes/mafia/src/commonMain/kotlin/com/parlor/games/mafia/domain/authority/MafiaActionAuthority.kt` | read | HostOnly vs SelfActor |
| `game-modes/mafia/src/commonMain/kotlin/com/parlor/games/mafia/domain/action/MafiaAction.kt` | read | Vocabulary |
| `game-modes/mafia/src/commonMain/kotlin/com/parlor/games/mafia/domain/action/MafiaActionCodec.kt` | read | Retired types |
| `game-modes/mafia/src/commonMain/kotlin/com/parlor/games/mafia/domain/event/MafiaEvent.kt` | read | Events |
| `game-modes/mafia/src/commonMain/kotlin/com/parlor/games/mafia/domain/projection/MafiaProjectionPolicy.kt` | read | Terminal role reveal |
| `game-modes/mafia/src/commonMain/kotlin/com/parlor/games/mafia/domain/party/MafiaReadinessGate.kt` | read | Local auto-ack |
| `game-modes/mafia/src/commonMain/kotlin/com/parlor/games/mafia/domain/modes/MafiaModes.kt` | read | Classic only |
| `game-modes/mafia/src/commonMain/kotlin/com/parlor/games/mafia/snapshot/MafiaSnapshotCodec.kt` | read | Canonical + recovery |
| `game-modes/mafia/src/commonMain/kotlin/com/parlor/games/mafia/snapshot/MafiaSnapshotRecovery.kt` | read | Fail-closed resume |
| `game-modes/mafia/src/commonMain/kotlin/com/parlor/games/mafia/MafiaDefinition.kt` | read | Initial Setup + preset |
| `game-modes/mafia/src/commonMain/kotlin/com/parlor/games/mafia/MafiaIds.kt` | read | `mafia` / `classic` |

## Mafia tests in scope

| Path | Status | Notes |
|---|---|---|
| `.../domain/reducer/MafiaReducerTest.kt` | read | Settings, start, night, vote, disconnect set |
| `.../domain/reducer/MafiaReducerEdgeCasesTest.kt` | read | Settings gates, night ready, continue-without ends |
| `.../domain/rules/WinCheckTest.kt` | read | Parity including empty→Town |
| `.../domain/rules/NightResolutionTest.kt` | read | Save, ties, insertion-order invariance |
| `.../domain/rules/VoteResolutionTest.kt` | read | Tie policies + permutations |
| `.../domain/rules/RoleAssignmentTest.kt` | read | Counts, teammates, seed |
| `.../domain/rules/MafiaSessionRulesTest.kt` | read | Roster/mode/name |
| `.../domain/settings/MafiaSettingsValidationTest.kt` | read | All reject branches + presets 5–16 |
| `.../domain/action/MafiaActionCodecTest.kt` | read | Round-trip + retired |
| `.../domain/state/MafiaPeerSnapshotValidatorTest.kt` | skim | Round-two projection |
| `.../domain/projection/MafiaProjectionPolicyTest.kt` | skim | Redaction |
| `.../domain/projection/MafiaHostOnlyRedactionSentinelTest.kt` | skim | Host-only empty |
| `.../snapshot/MafiaSnapshotRecoveryTest.kt` | read | Fail-closed resume |
| `.../snapshot/MafiaSnapshotCodecTest.kt` | read | Round-trip + oversized history |
| `.../multidevice/MafiaPeerActionAuthorityTest.kt` | skim | Wire authority via host bridge |
| `.../multidevice/MafiaAuthoritativeLifecycleTest.kt` | skim | Lifecycle |
| `.../multidevice/MafiaMultiDeviceProgressionTest.kt` | skim | Progression |
| `.../flow/FullGameDriveTest.kt` | skim | Drive + recovery after each step |

## Mafia UI (out of scope — skip)

All `ui/**` screens (night/vote/discussion/setup/reveal/handoff/announce/postgame), flow routers, room bridges, DI, setup-draft tests, TargetPickerSelectionTest, network error key test, pass-and-play night queue UI.

## Shared engine / session (not owned; referenced only)

Did **not** treat session/transport as product evidence. Authority is classified in-game; enforcement on the wire is session-network's workstream.
