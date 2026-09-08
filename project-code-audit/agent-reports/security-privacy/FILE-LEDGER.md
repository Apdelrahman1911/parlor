# File ledger — security / privacy

Status: `full` = entire file read. `section` = listed ranges. Tests: titles /
invariants unless noted.

## Projections

| File | Lines | Status |
|---|---|---|
| `shared/engine/.../projection/Projections.kt` | 36 | full |
| `game-modes/whodunit/.../WhodunitProjectionPolicy.kt` | 87 | full |
| `game-modes/mafia/.../MafiaProjectionPolicy.kt` | 94 | full |
| `game-modes/whodunit/.../WhodunitPeerProjectionBoundaryTest.kt` | 411 | full |
| `game-modes/mafia/.../MafiaProjectionLeakTest.kt` | 208 | full |
| `game-modes/mafia/.../MafiaProjectionPolicyTest.kt` | 314 | full |

## Protocol / identity / credentials

| File | Lines | Status |
|---|---|---|
| `shared/networking/.../protocol/Protocol.kt` | 384 | full |
| `shared/networking/.../protocol/ProtocolValidation.kt` | 239 | full |
| `shared/networking/.../room/RoomInputPolicy.kt` | 97 | full |
| `shared/networking/.../room/LocalRoom.kt` | 223 | full |
| `shared/networking/.../room/DiscoveredRoom.kt` | 20 | full |
| `shared/networking/.../transport/RoomTransport.kt` | 144 | full (JoinConfig / ResumableSessionInfo) |
| `shared/networking/.../security/SecureIds.kt` | 69 | full |
| `shared/networking/.../security/SecureIds.{android,ios,desktop}.kt` | — | full |
| `shared/networking/.../security/SecureHashes.kt` | 30 | full |
| `shared/transport-p2p/.../ResumableCredentialStore.kt` | 408 | full |
| `shared/transport-p2p/.../P2pTrafficPolicy.kt` | 218 | full |
| `shared/transport-p2p/.../P2pKitRoomTransport.kt` | 4589 | sections: 247–270, 850–959, 1300–1348, 1469–1560, 1860–1939, 2140–2419, 3560–3628; actor stamp 1890–1910 |

## Storage

| File | Lines | Status |
|---|---|---|
| `shared/storage/.../secure/SecureStorage.kt` | 23 | full |
| `shared/storage/.../secure/PlatformKeyedSecureStorage.kt` | 83 | full |
| `shared/storage/.../snapshot/FileBackedSnapshotStore.kt` | 203 | full |
| `shared/storage/.../snapshot/SerializedSnapshotWriter.kt` | 157 | full |
| `shared/engine/.../snapshot/SnapshotCodec.kt` | 64 | full |
| `game-modes/whodunit/.../WhodunitSnapshotCodec.kt` | 182 | full |
| `game-modes/mafia/.../MafiaSnapshotCodec.kt` | 55 | full |
| `composeApp/.../androidMain/.../AndroidSecureKeyValueBacking.kt` | 213 | full |
| `composeApp/.../androidMain/.../AndroidSnapshotFileSystem.kt` | 301 | full |
| `composeApp/.../androidMain/.../AndroidSettingsKeyValueBacking.kt` | 65 | full |
| `composeApp/.../androidMain/.../AndroidGcmRecordPolicy.kt` | 12 | full |
| `composeApp/.../androidMain/.../PlatformStorage.android.kt` | 20 | full |
| `composeApp/.../iosMain/.../IosSecureKeyValueBacking.kt` | 262 | full |
| `composeApp/.../iosMain/.../IosSnapshotKeychain.kt` | 205 | full |
| `composeApp/.../iosMain/.../IosSnapshotFileSystem.kt` | 475 | full |
| `composeApp/.../iosMain/.../IosSettingsKeyValueBacking.kt` | 33 | full |
| `composeApp/.../iosMain/.../PlatformStorage.ios.kt` | 19 | full |
| `composeApp/.../desktopMain/.../DesktopSnapshotFileSystem.kt` | 261 | full |
| `composeApp/.../desktopMain/.../DesktopSettingsKeyValueBacking.kt` | 52 | full |
| `composeApp/.../desktopMain/.../PlatformStorage.desktop.kt` | 23 | full |
| `composeApp/.../commonMain/.../di/StorageModule.kt` | 23 | full |
| `shared/storage/.../settings/Settings.kt` | 33 | full |
| `shared/storage/.../settings/PersistentSettingsStore.kt` | 109 | full |

## Host / peer snapshot install

| File | Lines | Status |
|---|---|---|
| `.../WhodunitHostRoomBridge.kt` | 568 | section 260–290 |
| `.../WhodunitPeerRoomBridge.kt` | 215 | section 70–184 |
| `.../MafiaHostRoomBridge.kt` | 493 | section 1–150, 230–248 |
| `.../MafiaPeerRoomBridge.kt` | 185 | full |
| `.../MafiaPeerSnapshotValidator.kt` | 292 | full |
| `.../WhodunitRetainedMultiplayerRuntime.kt` | 189 | section 1–160 |
| `.../MafiaRetainedMultiplayerRuntime.kt` | 172 | section 1–150 |
| `.../WhodunitGameFlow.kt` | 1427 | section 742–835, 971–1039 |
| `.../MafiaGameFlow.kt` | — | section 270–298 |

## Platform policy / diagnostics / DI

| File | Lines | Status |
|---|---|---|
| `composeApp/src/androidMain/AndroidManifest.xml` | 33 | full |
| `composeApp/src/androidMain/res/xml/backup_rules.xml` | 17 | full |
| `composeApp/src/androidMain/res/xml/data_extraction_rules.xml` | 29 | full |
| `iosApp/iosApp/Info.plist` | 61 | full |
| `iosApp/iosApp/PrivacyInfo.xcprivacy` | 39 | full |
| `iosApp/Configuration/Config.xcconfig` | 19 | full |
| `iosApp/iosApp.xcodeproj/project.pbxproj` | — | section 300–383; grep: no entitlements |
| `gradle/verification-metadata.xml` | 11075 | header + existence |
| `shared/transport-p2p/.../P2pDiagnostics.kt` | 195 | full |
| `shared/transport-p2p/.../P2pDiagnostics.{android,ios,desktop}.kt` | — | full |
| `shared/transport-p2p/.../P2pTransportModule.kt` | — | `P2P_APP_ID` |
| `shared/transport-p2p/.../P2pTransportModule.{android,ios}.kt` | — | full (security mode) |
| `shared/transport-p2p/.../AndroidManifestLanPermissionTest.kt` | 98 | full |
| `shared/content/.../KtorRemoteCaseDataSource.kt` | 144 | full |
| `shared/content/.../OfflineRemoteCaseDataSource.kt` | 28 | full |
| `shared/content/build.gradle.kts` | 24 | full |
| `composeApp/.../di/AppModule.kt` | 71 | full |
| `composeApp/.../di/ContentModule.kt` | 87 | full |
| `shared/core/.../random/SessionSeedSource.kt` | 12 | full |
| `composeApp/.../LocalResumeRouter.kt` | 157 | full |
| `composeApp/.../shell/home/HomeScreen.kt` | — | section 100–316 |
| `composeApp/.../shell/home/HomeRecoveryAvailability.kt` | 109 | full |
| `game-modes/mafia/.../PrivateRoleCardScreen.kt` | 137 | full |
| `game-modes/mafia/.../MafiaHandoffScreens.kt` | 157 | full |
| `game-modes/whodunit/.../WaxSealReveal.kt` | 231 | section 1–80, 130–160 |
| `composeApp/build.gradle.kts` | — | section 216–239 |

## Grep-only / listed

A11y `contentDescription` across game + shell UI; `HttpClient` in composeApp
(none); `AdmissionAccepted` / `rejoinToken` production uses; `println`/`Log`/`NSLog`
in production source sets; `debugImplementation` / `src/debug` (none);
`CODE_SIGN_ENTITLEMENTS` / entitlements files (none); FakeClock / InMemory*
commonMain; SerializedSnapshotWriter call sites.
