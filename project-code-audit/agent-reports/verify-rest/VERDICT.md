# Remaining findings — independent verdicts

Tree: local worktree on `3f825f9a` (behind origin; production sources for these IDs match cited files).
Rule: REAL only if current shipping/test code still has the claimed defect *and* it is not designed-as-such. Aggressive FALSE_POSITIVE / INTENTIONAL / THEORETICAL.

| ID | Verdict | Evidence |
|---|---|---|
| F-009 TB-003 | REAL | `build.gradle.kts:29` claims “desktop application compile”; `:193-195` only `dependsOn(desktopTest)`. |
| F-010 AR-003 ST-002 SP-002 | THEORETICAL | `KtorRemoteCaseDataSource` public + `ktor-client-core` on content commonMain; prod DI is `OfflineRemoteCaseDataSource()` (`ContentModule.kt:59`). Unreachable without a new Koin + engine bind. Adapter kdoc says so. |
| F-011 AR-005 | REAL | `composeApp/.../shell/game/whodunit/{Host,Peer,CasePicker}` still exist; Mafia lobby is in `:game-modes:mafia`. `verifyGameShellDispatch` (`composeApp/build.gradle.kts:503-512`) does not scan `shell/game/**`. |
| F-012 UI-004 | REAL | `WhodunitCasePickerScreen` lists all cases; `CaseRow` never reads `CaseSummary.language`. `AppLanguage` kdoc: case content is not chrome-translated. |
| F-013 UI-008 | INTENTIONAL | `PlatformBackHandler` kdoc: “when that platform exposes one.” iOS/Desktop actuals are `Unit`. Screens have explicit `onBack` chevrons. No iOS swipe / Desktop Escape is a product choice, not a missing Android handler. |
| F-014 UI-005 UI-006 | THEORETICAL | Covers/rows are `Role.Button` with visible title/subtitle/name as children; Compose merges those as the name. No `contentDescription` override. TalkBack wording unproven. Not a silent unlabeled control. |
| F-015 UI-007 | REAL | `RoundTitleCardScreen` / `ClueRevealScreen` (`RoundScreens.kt:47-110`) have no `verticalScroll`. Contract allowlist (`ProductionUiAccessibilityContractTest.kt:227-248`) omits them. `DiscussionScreen` does scroll. |
| F-016 ST-003 | THEORETICAL | iOS settings: `setBool`/`setObject` only (`IosSettingsKeyValueBacking.kt:22-31`). Android `commit()`-fails. `NSUserDefaults` is usually durable; no observed fail-open of secrets. Accessibility prefs *could* vanish; not proven. |
| F-017 ST-001 | THEORETICAL | `FileBackedSnapshotStore` writes JSON; AEAD is in platform `SnapshotFileSystem` (Android/iOS/Desktop all encrypt). Interface kdoc requires AEAD. Only a future raw-FS bind would leak. Current binds encrypt. |
| F-018 GR-010 | REAL | `CluePolicyTest.stateAtRound` empty roles; `WhodunitReducerProductionGuardsTest` plants `droppedPlayers` in Collecting; `MafiaReducerEdgeCasesTest` plants winner on VoteAnnouncement; `validatedWhodunitCaseForTest` bypasses payload validation. Tests do not call `requireValid`. |
| F-020 UI-001 PF-001 | INTENTIONAL | Both bindings omit Solo. Picker comment (`PlayModePickerScreen.kt:104-105`): “Unsupported cards stay visible but disabled.” Disabled hint is `setup_mode_unavailable`. Designed catalog, not a broken entry. |
| F-021 AR-006 | INTENTIONAL | Sizes still 4589 / 1936 / 1034. Load-bearing host spine. Size ≠ defect. Same as PR-004. |
| F-022 AR-001 AR-002 | REAL | `GameSession` / `TimerService` declared only; no impl/callers. `transport-p2p` `implementation(project(":shared:session"))` with zero `com.parlor.session` imports. Dead API + dead Gradle edge. |
| F-023 AR-007 | REAL | `shared/navigation/` exists (`.DS_Store`, empty `src/commonTest`, stale `build/`). Not in `settings.gradle.kts`. Not compiled. Debris. |
| F-024 GR-006 | INTENTIONAL | Timer fields exist; `MafiaSettings.validate` always `TimersNotSupported` if any duration non-null. Tests assert rejection. Fail-closed by design. |
| F-025 GR-005 SN-011 | INTENTIONAL | `PrivacyConcernRaised` is a lone unused ADT member. `PeerP2pRoom.rejoinToken` hard-null; secret is `ResumableCredentialStore`. Default `LocalRoom.rejoinToken` also null. Dead surface, not a leak. |
| F-026 SN-006 SP-006 | INTENTIONAL | Limiter before `WrongCode` (`P2pKitRoomTransport.kt:2163-2176`). Distinct `WrongCode` + non-constant compare. 6×32 alphabet, host tap still required, same-AppId LAN only. Documented residual of LAN admission. |
| F-027 SP-007 TB-008 | INTENTIONAL | `verification-metadata.xml:4-5` `verify-signatures=false`. Checksum policy is explicit. Not a missing file. |
| F-028 TB-007 | INTENTIONAL | Doc/prose contract tests in `desktopTest` are the cache-invalidation mechanism (`transport-p2p` desktopTest inputs include docs). They are file contracts, not product tests — by design. |
| F-029 UI-002 UI-003 | INTENTIONAL | Android `app_name` `translatable="false"` “Parlor”; iOS `ar.lproj` “بارلور”. Brand pin on Android. `.uppercase()` on EN/AR chrome only; AR is a no-op; no Turkish locale ships. |
| F-030 SP-004 ST-005 | INTENTIONAL | `DesktopSnapshotFileSystem` kdoc + `PlatformStorage.desktop.kt:18-21`: Desktop is a non-shipping harness; same-uid key file; credentials RAM-only. |
| F-031 TB-009 | INTENTIONAL | `productionCheck` description already says “host-independent… run productionAppleCheck on macOS separately” (`build.gradle.kts:134`). Naming matches AGENTS.md. Not a false gate. |
| F-032 TB-011 | FALSE_POSITIVE | Dirty files (`composeApp/build.gradle.kts`, xcconfig, pbxproj, workflow test) are *this* worktree, not a HEAD/source defect. Origin/main is not proven to have the alias drift. Audit of committed code cannot treat local dirt as a finding. |
| F-033 SN-004 | THEORETICAL | Peer `Channel(8)` + suspending `send` (`P2pKitRoomTransport.kt:3605-3608,4164`). Outbox timeout 2s (`DEFAULT_OUTBOUND_SEND_TIMEOUT_MS`). Host treats Timeout and continues; snapshots conflate. Possible latency, not authority loss. Unprofiled. |
| F-034 SN-005 | INTENTIONAL | `connectionEpoch` default `1L`; Protocol kdoc: stale callbacks rejected by room/session ownership generation, not epoch rotation. Replay is message-id (`rememberHostMessage`). Designed. Residual replace-session race is theoretical. |
| F-035 AR-004 | INTENTIONAL | `FakeClock` / `InMemorySnapshotStore` / `InMemorySettingsStore` / `InMemorySecureKeyValueBacking` live in commonMain so KMP tests (and Desktop harness) can share types. Shipping mobile binds file/Keystore/Keychain. Comments say test/dev. Not a mistaken prod bind. |
| AR-008 | THEORETICAL | `P2pKitFactory.createKit` returns `dev.p2pkit.core.P2pKit`. Isolation is “only transport-p2p imports p2pkit” (Konsist/Gradle), not signature-sealed. composeApp never mentions P2pKit. Leak requires a new import. |
| AR-009 | INTENTIONAL | Two-game hardcoded registry, one P2pKit bind, unused HTTP adapter. Prepaid plugin shape. Not a runtime bug. Overlaps F-010/F-011/F-022. |
| GR-004 | REAL | `WhodunitPublic.droppedPlayers` kdoc “New reducers never add entries”; `continueWithoutPlayer` writes `droppedPlayers + playerId` then ends. Mafia validator forbids dropped except PostGame; writer then `finishGame`. Comment is stale. |
| GR-007 | THEORETICAL | `MafiaActionAuthority.classify` is exhaustive `when`. No dedicated matrix test; `MafiaPeerActionAuthorityTest` covers the bridge. A wrong SelfActor would compile only if someone edits the `when`. Test-gap, not a wrong classifier. |
| GR-008 | REAL | `applyVoteResolved` sets `winner` only with `phase = PostGame`. Validator forbids winner off PostGame. `advanceFromVoteAnnouncement` winner short-circuit + test plant are unreachable / illegal. |
| GR-009 | INTENTIONAL | `RoomInputPolicy` kdoc + test: `Alice`/`alice` accepted; identity is `PlayerId`. Exact compare is specified. |
| SN-002 | INTENTIONAL | Production hosts set `reconcileRoomTopology = true` and collect `room.members`. `peerEvents` replay=0 is fixture/edge-stream only. Mitigated on the shipping path. |
| SN-008 | INTENTIONAL | `nextHostAdvance` returns null while `disconnectedPlayers` non-empty. Lifecycle `Active` gate in `driveMafiaHostProgression`. Residual “no Whodunit Pause bit” is product, not a silent auto-advance. |
| SN-009 | THEORETICAL | `InMemoryPeerRoom.leave = Unit`; no closeAdmissions/resume. Game tests use the bus; transport lifecycle tests use fake kit. Coverage gap, not a production bug. |
| SN-010 | THEORETICAL | `broadcast` Success if ≥1 connected send and no throw. Coordinators send snapshots/start **Direct** via outboxes. Broadcast unused for gameplay. Footgun if a future caller uses Broadcast for authority frames. |
| SP-003 | THEORETICAL | `toPublic` does not force-null living `revealedRole`. Validator rejects `alive && revealedRole != null`. Current leak would fail peer install. Defense-in-depth only. |
| SP-005 | INTENTIONAL | Diagnostics always log; `exportLine` is closed vocab (event/role/result/reason/count). No secrets. Cadence telemetry by design. |
| SP-009 | THEORETICAL | Source: WaxSeal/Mafia handoff a11y omit roles; role text only after confirm-alone. TalkBack/VoiceOver focus during gate needs a device. No code leak found. |
| SP-010 | THEORETICAL | Android `allowBackup=false` + exclude-all backup/extraction XML; iOS exclude-from-backup + ThisDeviceOnly Keychain. Device-transfer honor is OEM/runtime. Code policy is fail-closed. |
| ST-004 | INTENTIONAL | Settings = `NSUserDefaults` (theme/language/reduced-motion, non-secret). Snapshots/credentials excluded. Split is correct for prefs vs secrets. |
| ST-006 | THEORETICAL | Store mutex; FS actuals have none. Prod is a singleton `SnapshotStore`. Race needs a second writer that does not exist. |
| ST-007 | INTENTIONAL | `file.length()` then `readBytes()` after 8 MiB cap. App-private dir. Bound exists; streaming not required for ≤256 KiB payloads. |
| ST-008 | INTENTIONAL | Mafia codec reuses `MAX_SNAPSHOT_PAYLOAD_BYTES`. Same 256 KiB budget on wire and disk. Coupling, not a wrong cap. |
| ST-009 | INTENTIONAL | Mafia snapshot writer only in PaP `SessionDrivenFlow`. LAN: room is SoT; host death ends party. Designed. |
| ST-010 | INTENTIONAL | `OfflineRemoteCaseDataSource` always Unreachable; cache fills only after remote success. Bundled-only pipeline. Not a defect. |
| UI-009 | INTENTIONAL | `ParlorBottomTabBar` / `SectionDivider` / `ParlorScrim` unused. Home: “no tabs.” Dead design-system chrome, not user-visible. |
| TB-010 | THEORETICAL | `proguard-rules.pro` keepattributes only; minify+shrink on. Relies on consumer rules. Shrink success ≠ device runtime. No keep-mapping hole shown in source. |
| TB-012 | INTENTIONAL | Engine/Mafia `commonTest` empty; tests live in `desktopTest` (+ engine Konsist). Matrix shape, not missing rules. Native Mafia tests absent by this layout. |
| PF-002 | INTENTIONAL | Android gate `NotRequired`. Manifest: INTERNET + WIFI + multicast; no Nearby/Location; `usesCleartextTraffic=false`. Correct for LAN/P2pKit. |
| PF-003 | INTENTIONAL | iOS gate: no preflight; Unknown until advertise/browse; empty discovery ≠ denial (`P2pPermissionGate.ios.kt`, `RoomTransport` kdoc). Matches Apple API. |
| PR-002 | INTENTIONAL | Host re-encodes snapshot after every Applied command (`processCommand` → `publishSnapshots`). Authority model requires it. Bound 256 KiB. Perf, not a bug. |
| PR-003 | INTENTIONAL | `inter.ttf` + `jetbrains_mono.ttf` ≈ 1.0 MiB always packaged. Brand fonts. No subsetting. Size note, not a defect. |

## Compact rollup

| Verdict | IDs |
|---|---|
| REAL | F-009, F-011, F-012, F-015, F-018, F-022, F-023, GR-004, GR-008 |
| INTENTIONAL | F-013, F-020, F-021, F-024, F-025, F-026, F-027, F-028, F-029, F-030, F-031, F-034, F-035, AR-009, GR-009, SN-002, SN-008, SP-005, ST-004, ST-007, ST-008, ST-009, ST-010, UI-009, TB-012, PF-002, PF-003, PR-002, PR-003 |
| THEORETICAL | F-010, F-014, F-016, F-017, F-033, AR-008, GR-007, SN-009, SN-010, SP-003, SP-009, SP-010, ST-006, TB-010 |
| FALSE_POSITIVE | F-032 |

No ALREADY_FIXED / UNREACHABLE as a primary label (F-010 reachability folded into THEORETICAL).
Do not file issues for INTENTIONAL / THEORETICAL / FALSE_POSITIVE.
