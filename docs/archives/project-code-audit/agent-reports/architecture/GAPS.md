# Architecture workstream — GAPS

## Files / surfaces still unread (or only grepped)

These were in or adjacent to scope and were **not** fully read:

### composeApp shell (Whodunit-owned, large)

- `composeApp/src/commonMain/kotlin/com/parlor/app/shell/game/whodunit/WhodunitPeerSessionFlow.kt` (713)
- `composeApp/src/commonMain/kotlin/com/parlor/app/shell/game/whodunit/WhodunitCasePickerScreen.kt` (221)
- remainder of `WhodunitHostSessionFlow.kt` after line 80
- remainder of `WhodunitGameShellBinding.kt` / `MafiaGameShellBinding.kt` after sampled ranges
- remainder of `App.kt` after line 120
- `AppBackPolicy.kt`, `LocalResumeRouter.kt` (token-grepped only)
- `shell/multiplayer/{NameInputScreen,JoinPromptScreen}.kt` (token-grepped only)

### Session / transport internals

- remainder of `AuthoritativeSessionCoordinator.kt` (1936)
- remainder of `ProcessMultiplayerSessionOwner.kt` (1034)
- remainder of `P2pKitRoomTransport.kt` (4589) including `HostP2pRoom` / `PeerP2pRoom`
- `PassAndPlaySessionController.kt`, `ShadowSessionController.kt`, `PartyAwareSession.kt`
- `SessionStartHandshake.kt`, `BoundedPeerOutbox.kt`, `PeerConnectionTracker.kt`

### Networking / content / storage remainder

- `Protocol.kt` after line 80, `ProtocolValidation.kt`, `RoomMessageCodec.kt`
- `LocalRoom.kt`, `RoomLifecycle.kt`, `PeerEvent.kt`, `RoomInputPolicy.kt`
- remainder of `DefaultCaseRepository.kt`, `DefaultCaseValidator.kt`
- platform `PlatformStorage.{android,ios}.kt`
- transport `ResumableCredentialStore.kt`, `P2pDiagnostics.kt`, `P2pTrafficPolicy.kt`

### Game-module internals (out of primary graph scope)

- `WhodunitDefinition` / `MafiaDefinition` bodies
- game reducers, snapshot codecs, host/peer room bridges
- not required to reconstruct the **module** architecture; required to judge
  whether game domain packages stay pure Kotlin

### Engine tests beyond Konsist + fixture

- `engine-testing` `RrSnapshotCodecTest`
- no `shared/engine/src/commonTest` exists

### Leftover navigation build tree

- `shared/navigation/build/**` not inventoried file-by-file
- no production sources present

## Questions this workstream did not close

1. Whether R8 keeps `KtorRemoteCaseDataSource` in the release AAB (class is
   public and referenced from `OfflineRemoteCaseDataSource` kdoc only — likely
   shrinkable; not verified against mapping.txt).
2. Whether `transport-p2p`’s unused session dependency is load-bearing for
   some compile-only expect/actual that grep would miss (none found in `*.kt`).
3. Line-level review of host-authority invariants inside the 4.5k-line
   transport (different workstream).
4. Whether a third game can be added without copying Whodunit lobby into
   composeApp (AR-005 suggests no, today).

## Out of scope by instruction

- Canonical `project-code-audit/00–18` files: not written
- Any `*.md` outside `project-code-audit/`: not read
- Production code: not modified
- Git: no commit
