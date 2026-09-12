# Architecture workstream — FILE LEDGER

Status: R = read in full or near-full. S = sampled (head / targeted grep). U = unread (see GAPS.md).

## Gradle / conventions

| File | Status | Notes |
|---|---|---|
| settings.gradle.kts | R | 13 modules; navigation not included |
| build.gradle.kts | R | allTests / production* gates; verifyGameShellDispatch |
| gradle.properties | R | AGP lint/R8 pins; no mavenLocal |
| gradle/libs.versions.toml | R | p2pkit 0.7.0-rc3; ktor 3.0.3; koin 4.0.0 |
| build-logic/settings.gradle.kts | R | |
| build-logic/convention/build.gradle.kts | R | three convention plugins |
| build-logic/convention/.../KmpLibraryConventionPlugin.kt | R | |
| build-logic/convention/.../KmpComposeLibraryConventionPlugin.kt | R | |
| build-logic/convention/.../DetektConventionPlugin.kt | R | |
| composeApp/build.gradle.kts | R | deps + verifyGameShellDispatch |
| shared/core/build.gradle.kts | R | |
| shared/engine/build.gradle.kts | R | Konsist on desktopTest |
| shared/engine-testing/build.gradle.kts | R | |
| shared/session/build.gradle.kts | R | networking-testing / engine-testing test-only |
| shared/networking/build.gradle.kts | R | api engine (Player on SessionStarting) |
| shared/networking-testing/build.gradle.kts | R | |
| shared/transport-p2p/build.gradle.kts | R | p2pkit + unused session dep |
| shared/storage/build.gradle.kts | R | |
| shared/content/build.gradle.kts | R | ktor-client-core on commonMain |
| shared/design-system/build.gradle.kts | R | |
| game-modes/whodunit/build.gradle.kts | R | |
| game-modes/mafia/build.gradle.kts | R | |

## Engine commonMain (every file)

| File | Status |
|---|---|
| definition/GameDefinition.kt | R |
| definition/GameMode.kt | R |
| definition/GameMetadata.kt | R |
| registry/GameRegistry.kt | R |
| reducer/GameReducer.kt | R |
| reducer/ReducerContext.kt | R |
| session/GameSession.kt | R |
| session/SessionConfig.kt | R |
| action/GameAction.kt | R |
| event/GameEvent.kt | R |
| state/GameState.kt | R |
| state/GameStateContainer.kt | R |
| state/Player.kt | R |
| projection/Projections.kt | R |
| snapshot/SnapshotCodec.kt | R |
| phase/GamePhase.kt | R |
| timer/TimerService.kt | R |

Engine commonMain file count: 17. All read. No other commonMain files.

## Engine / fixture tests

| File | Status |
|---|---|
| engine/.../architecture/PurityTest.kt | R |
| engine/.../architecture/NoWhodunitInEngineTest.kt | R |
| engine-testing/.../fakes/RoundRobinAnnounceGame.kt | R |
| engine-testing/.../registry/GameRegistryExtensibilityTest.kt | R |
| engine-testing/.../reducer/ReducerSmokeTest.kt | S |

## Koin / composition root

| File | Status |
|---|---|
| composeApp/.../di/AppModule.kt | R |
| composeApp/.../di/ContentModule.kt | R |
| composeApp/.../di/StorageModule.kt | R |
| composeApp/.../p2p/P2pBootstrap.kt | R |
| composeApp/.../storage/PlatformStorage.kt | R |
| composeApp/.../storage/PlatformStorage.desktop.kt | R |
| composeApp/.../di/GameShellCompositionTest.kt | R |
| transport-p2p/.../P2pTransportModule.kt | R |
| transport-p2p/.../P2pTransportModule.android.kt | R |
| transport-p2p/.../P2pTransportModule.ios.kt | R |
| transport-p2p/.../P2pTransportModule.desktop.kt | R |
| transport-p2p/.../P2pKitFactory.kt | R |
| whodunit/.../di/WhodunitDiModule.kt | R |
| mafia/.../di/MafiaDiModule.kt | R |

## Shell / registry

| File | Status |
|---|---|
| composeApp/.../shell/game/GameShellRegistry.kt | R |
| composeApp/.../shell/game/GameShellSupport.kt | R |
| composeApp/.../shell/game/WhodunitGameShellBinding.kt | S (1–80, 80–159) |
| composeApp/.../shell/game/MafiaGameShellBinding.kt | S (1–80, 77–156) |
| composeApp/.../App.kt | S (1–120) |
| composeApp/.../shell/home/HomeScreen.kt | S (1–80) |
| composeApp/.../shell/game/whodunit/WhodunitHostSessionFlow.kt | S (1–80); 673 lines |
| composeApp/.../shell/game/whodunit/WhodunitPeerSessionFlow.kt | U (713 lines; imports grepped) |
| composeApp/.../shell/game/whodunit/WhodunitCasePickerScreen.kt | U (221 lines; imports grepped) |

## Session / networking / content / storage / transport (sampled)

| File | Status |
|---|---|
| session/SessionController.kt | R |
| session/.../AuthoritativeSessionCoordinator.kt | S (1–120); 1936 lines |
| session/.../ProcessMultiplayerSessionOwner.kt | S (1–60); 1034 lines |
| networking/transport/RoomTransport.kt | R |
| networking/protocol/Protocol.kt | S (1–80) |
| networking-testing/.../InMemoryRoomBus.kt | S (1–40) |
| content/datasource/DataSources.kt | R |
| content/datasource/OfflineRemoteCaseDataSource.kt | R |
| content/datasource/KtorRemoteCaseDataSource.kt | R |
| content/repository/DefaultCaseRepository.kt | S (1–80) |
| storage/snapshot/InMemorySnapshotStore.kt | R |
| core/time/Clock.kt | R |
| transport-p2p/P2pKitRoomTransport.kt | S (70–119); 4589 lines |
| transport-p2p/.../TestTransportIsolationContractTest.kt | R |

## Leftover

| Path | Status |
|---|---|
| shared/navigation/ | listed; no Kotlin sources; stale `build/` |

## Import sweeps (grep, not per-file read)

- `import com.parlor.games` under `shared/` → 0
- engine commonMain forbidden packages → 0
- `dev.p2pkit` / `p2pkit` in `*.kt`/`*.kts` → only `:shared:transport-p2p`
- `project(":game-modes:` → only composeApp commonMain
- `project(":shared:networking-testing")` → session/whodunit/mafia `commonTest` only
- `RoundRobinAnnounceGame` / `engine-testing` → test source sets only
- `MockEngine` → `*Test` only
- `GameSession` / `TimerService` usages → declarations only
- `com.parlor.session` under `transport-p2p` → 0
- forbidden tokens in verifyGameShellDispatch files → 0
