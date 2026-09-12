# System map (code-reconstructed)

Evidence: Gradle graph, Koin `allModules`, `App.kt`, session/transport/protocol,
game definitions. No documentation files used.

## Product

A Kotlin Multiplatform / Compose Multiplatform **party-game container**. Two
shipping games register at the composition root. Play is either **one-device
pass-and-play** or **same-LAN multi-device** with a host-owned reducer.

There is no account system, no analytics SDK, no remote case server in
production DI, no spectator role, no host migration, no raw-IP join.

## Platforms

| Target | How it exists | Shipping? |
|---|---|---|
| Android | `:composeApp` androidTarget + `ParlorApplication`/`MainActivity` | Intended Store target (`applicationId`, minify, AAB tasks) |
| iOS | Kotlin frameworks + `iosApp` Swift host | Intended Store target (Release scheme, IPA scripts) |
| Desktop JVM | `jvm("desktop")` + `Main.kt` + compose.desktop | Dev/test harness (unsigned distributions exist, ungated) |

Convention: Android + desktop + iosX64 + iosArm64 + iosSimulatorArm64, JVM 21.

## Runtime composition

```
ParlorApplication / Main / iOSApp
        │
        ▼
Koin allModules
  coreModule (Clock, SecureSessionSeedSource, AppLifecycleCoordinator,
              ProcessMultiplayerSessionOwner)
  whodunitModule, mafiaModule
  contentModule (OfflineRemoteCaseDataSource, two GameShellBindings, GameRegistry)
  storageModule + platformStorageModule()
  p2pTransportModule          ← required; not optional
        │
        ▼
App()
  SettingsStore → language/theme/reducedMotion
  Home | Settings | Game(binding.Content) | LocalResumeFailure
        │
        ▼
GameShellBinding
  local: PassAndPlaySessionController + game UI
  host/peer: RoomTransport (P2pKitRoomTransport) + Host/Peer coordinators
             + game room bridges
```

## Authority and data flow (multi-device)

1. Host creates a room; P2pKit advertises a generic same-app service (room
   code is **not** in mDNS).
2. Peer discovers, opens authenticated session, sends code + display name.
3. Host limiter → protocol/code checks → host tap → seat bind.
   Transport **overwrites** every peer `actor` with the authenticated id.
4. Start barrier: `SessionStarting` → `SessionStartReady` → irreversible
   `SessionStartCommitted` → ack. Only then gameplay snapshots.
5. Peer submits `ClientCommand` (commandId, clientSequence, expectedRevision).
6. Host mailbox applies: protocol, size, actor, order, dedup, revision,
   game authority, **pure reducer**. Peers never reduce.
7. Host sends `CommandResult` + atomic public + recipient-private snapshot.
8. Peer installs only monotonic revisions. Rejected non-idempotent actions
   are not auto-retried.

Protocol: `PARLOR_PROTOCOL_MAJOR=4`, `MINOR=2`, `isCompatibleWith` is **exact
equality** (`Protocol.kt`).

## Persistence

- Settings: language, theme tag, reduced motion (platform prefs).
- Local game snapshots: AEAD in platform FS (Android Keystore + noBackup;
  iOS Keychain + exclude-from-backup; Desktop file key).
- Multiplayer rejoin: separate `SecureStorage` credentials, 120s host grace.
- Production cases: seven bundled Whodunit JSON files via
  `BundledWhodunitCatalog`. `OfflineRemoteCaseDataSource` always Unreachable.

## Module direction (verified)

Games → session/engine/content/storage/networking/design-system/core.
Shared never depends on a game.
Only `:shared:transport-p2p` imports `dev.p2pkit` / `io.github.apdelrahman1911:p2p-*`.
`:shared:engine` imports none of UI/DI/transport/games (Konsist + imports).
