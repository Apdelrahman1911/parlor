# File ledger — session / network

Status: `full` = entire file read. `section` = listed ranges. Tests: titles / `@Ignore` / contracts only unless noted.

## shared/networking

| File | Lines | Status |
|---|---|---|
| `protocol/Protocol.kt` | 384 | full |
| `protocol/ProtocolValidation.kt` | 239 | full |
| `protocol/RoomMessageCodec.kt` | 36 | full |
| `transport/RoomTransport.kt` | 144 | full |
| `room/LocalRoom.kt` | 223 | full |
| `room/RoomLifecycle.kt` | 17 | full |
| `room/PeerEvent.kt` | 58 | full |
| `room/PeerSessionRetryPolicy.kt` | 50 | full |
| `room/RoomInputPolicy.kt` | 97 | full |
| `room/DiscoveredRoom.kt` | 20 | full |
| `security/SecureIds.kt` | 69 | full |
| `security/SecureHashes.kt` | 30 | full |
| `security/SecureIds.{android,ios,desktop}.kt` | expect/actual | listed, not re-read (CSPRNG only) |
| `security/SecureHashes.{android,ios,desktop}.kt` | expect/actual | listed |
| `commonTest/.../ProtocolValidationTest.kt` | 330 | section 1–80 + titles |
| `commonTest/.../RoomMessageCodecTest.kt` | — | listed |
| `commonTest/.../RoomInputPolicyTest.kt` | — | titles |
| `commonTest/.../PeerSessionRetryPolicyTest.kt` | — | listed |
| `commonTest/.../LocalRoomRetryContractTest.kt` | 72 | section 1–60 |
| `commonTest/.../LocalNetworkAccessTest.kt` | — | listed |
| `commonTest/.../SecureIdsTest.kt` / `SecureHashesTest.kt` | — | listed |

## shared/networking-testing

| File | Lines | Status |
|---|---|---|
| `InMemoryRoomBus.kt` | 136 | full |

## shared/session

| File | Lines | Status |
|---|---|---|
| `SessionController.kt` | 58 | full |
| `PlayMode.kt` | 65 | full |
| `ViewerContext.kt` | 23 | full |
| `multidevice/AuthoritativeSessionCoordinator.kt` | 1936 | full (1–400, 401–850, 851–1350, 1351–1800, 1801–1936) |
| `multidevice/ProcessMultiplayerSessionOwner.kt` | 1034 | full (1–350, 351–750, 751–1034) |
| `multidevice/SessionStartHandshake.kt` | 359 | full |
| `multidevice/BoundedPeerOutbox.kt` | 262 | full |
| `multidevice/PeerConnectionTracker.kt` | 168 | full |
| `multidevice/ShadowSessionController.kt` | 97 | full |
| `multidevice/RetainedSessionOperation.kt` | 53 | full |
| `party/PartyAwareSession.kt` | 86 | section 1–80 |
| `party/PartyReadinessGate.kt` | 42 | full |
| `passandplay/PassAndPlaySessionController.kt` | 131 | section 1–40 (local reducer host) |
| commonTest / desktopTest session suites | — | titles for coordinator, handshake, tracker, outbox, owner |

## shared/transport-p2p

| File | Lines | Status |
|---|---|---|
| `P2pKitRoomTransport.kt` | 4589 | **full**, ranges in NOTES.md |
| `P2pTrafficPolicy.kt` | 218 | full |
| `ResumableCredentialStore.kt` | 408 | full |
| `DiscoveryCandidateScheduler.kt` | 276 | full |
| `AppLifecycleRoomCoordinator.kt` | 61 | full |
| `P2pTransportModule.kt` | 25 | full |
| `P2pTransportScope.kt` | 18 | full |
| `P2pKitFactory.kt` | — | listed (interface) |
| `P2pDiagnostics.kt` | — | listed (bounded ledger) |
| `P2pTransportModule.{android,ios,desktop}.kt` | 98 / 72 / 95 | full |
| `P2pKitRoomTransportLoopbackTest.kt` | 291 | section 1–230 (`@Ignore` recorded) |
| `P2pKitRoomTransportLifecycleTest.kt` | large | titles / peerEvents usage |
| other desktopTest / commonTest transport tests | — | listed |

## Game bridges (in scope)

| File | Lines | Status |
|---|---|---|
| `whodunit/.../WhodunitHostRoomBridge.kt` | 568 | full |
| `whodunit/.../WhodunitPeerRoomBridge.kt` | 215 | full |
| `whodunit/.../WhodunitRetainedMultiplayerRuntime.kt` | 189 | full |
| `mafia/.../MafiaHostRoomBridge.kt` | 493 | full |
| `mafia/.../MafiaPeerRoomBridge.kt` | 185 | full |
| `mafia/.../MafiaRetainedMultiplayerRuntime.kt` | 172 | full |
| `mafia/.../MafiaHostProgression.kt` | 81 | section 1–80 |
| other mafia lobby/flow UI files | — | listed; admission UI is in `MafiaHostLobbyFlow` / `WhodunitHostSessionFlow` |

## composeApp (in scope)

| File | Status |
|---|---|
| `p2p/P2pBootstrap.kt` | full (13 lines) |
| `di/AppModule.kt` | section 1–80 (`ProcessMultiplayerSessionOwner` singleton) |
| `App.kt` | section 70–99 (injects owner + `RoomTransport`) |

## Not in this workstream

- Engine reducers, storage snapshot crypto, Compose lobby chrome except
  admission/owner wiring noted above.
- Canonical audit docs `00–18` (forbidden to write).
