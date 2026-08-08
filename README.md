# Parlor

Parlor is a Kotlin Multiplatform party-game container for Android and iOS.
It currently ships two game modules:

- **Whodunit** — *The Last Dinner*, with Classic Vote and Elimination modes.
- **Mafia** — a host-authoritative social-deduction game.

Both games support local play and same-LAN multi-device play through P2pKit.
Desktop is a development and deterministic-test target, not a shipping target.
Room-code entry uses LAN discovery; raw-IP/manual endpoint connection,
public-internet rendezvous/NAT traversal, relay, spectators, and host migration
are not supported in the first release. Hotspot behavior remains a
device/OS/topology-specific physical release gate, not a universal promise.

The current architecture and release contracts are documented in:

- [`docs/PRODUCTION_ARCHITECTURE.md`](docs/PRODUCTION_ARCHITECTURE.md)
- [`ARCHITECTURE.md`](ARCHITECTURE.md) — original design history
- [`docs/adr/0001-game-module-registration.md`](docs/adr/0001-game-module-registration.md)
- [`docs/adr/0002-manual-endpoint-connection.md`](docs/adr/0002-manual-endpoint-connection.md)
- [`docs/HOW_TO_ADD_A_GAME.md`](docs/HOW_TO_ADD_A_GAME.md)
- [`docs/P2P_MANUAL_TEST.md`](docs/P2P_MANUAL_TEST.md)
- [`docs/P2P_REMEDIATION_STATUS.md`](docs/P2P_REMEDIATION_STATUS.md)
- [`docs/RELEASE_GATES.md`](docs/RELEASE_GATES.md)
- [`docs/PRIVACY_AND_COMPLIANCE.md`](docs/PRIVACY_AND_COMPLIANCE.md)

## Requirements

- JDK 21
- Android SDK 36 (`minSdk = 26`, `targetSdk = 36`)
- Xcode 16 or newer for Apple builds (minimum iOS 16)
- P2pKit 0.7.0-rc2 from Maven Central

The checked-in Gradle 8.13 wrapper is the build entry point.

## Dependency provenance

Production and local builds resolve the pinned P2pKit modules directly from
Maven Central:

```kotlin
io.github.apdelrahman1911:p2p-core:0.7.0-rc2
io.github.apdelrahman1911:p2p-transport-lan:0.7.0-rc2
```

The build does not use `mavenLocal()`, a sibling checkout, or a repository
override. Keep both P2pKit modules on the same pinned version.

## Run

```bash
# Android device or emulator
./gradlew :composeApp:installDebug

# Desktop development target
./gradlew :composeApp:run
```

For iOS, open `iosApp/iosApp.xcodeproj`, select a local development team, and
run the app from Xcode.

## Production verification

```bash
# All common/desktop tests plus unsigned Android release bundle and lint
./gradlew productionCheck

# Release Kotlin frameworks for physical iOS and Apple-silicon simulator
./gradlew productionAppleCheck
```

These commands prove local source and unsigned-artifact quality. Store signing,
physical two-device networking, accessibility with TalkBack/VoiceOver, store
privacy forms, and provider configuration remain explicit external gates; see
`docs/RELEASE_GATES.md`.

## Project layout

```text
parlor/
├── build-logic/                  # Gradle convention plugins
├── composeApp/                   # Shared Compose app and platform entry points
├── iosApp/                       # Thin Xcode wrapper
├── game-modes/
│   ├── whodunit/                 # Rules, protocol adapter, UI, assets, tests
│   └── mafia/                    # Rules, protocol adapter, UI, assets, tests
├── shared/
│   ├── core/                     # IDs, result, time, logging, telemetry contracts
│   ├── design-system/            # Tokens, components, localization, motion
│   ├── engine/                   # Generic game contracts and registry
│   ├── engine-testing/           # Non-shipping extensibility fixture
│   ├── session/                  # Local and host-authoritative session logic
│   ├── networking/               # Versioned transport-independent protocol
│   ├── transport-p2p/            # The only P2pKit adapter
│   ├── content/                  # Bundled offline content and validation
│   ├── storage/                  # Settings and protected snapshots
│   └── navigation/               # Module navigation descriptors
└── docs/                         # Rules, ADRs, test matrices, release runbooks
```

Game modules do not import P2pKit. Discovery, admission, reconnect, ordering,
deduplication, and terminal behavior belong to shared session/networking code.
The non-shipping second-game fixture proves that a definition can be registered
and exercised without changing that networking core.
