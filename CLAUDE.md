# CLAUDE.md

This file gives repository guidance to Claude Code and other contributors.
Production source and executable tests are authoritative. Historical plans and
audit ledgers are evidence only.

## Product and release scope

Parlor is a Kotlin Multiplatform + Compose Multiplatform party-game container.
Android and iOS are shipping targets; Desktop is a development and deterministic
test target. The release contains two game modules:

- Whodunit, with bundled English/Arabic cases and Classic Vote / Elimination modes;
- Mafia, with local and same-LAN multi-device play.

Both games support local play and host-authoritative same-LAN multiplayer over
P2pKit. Public-internet play, raw-IP/manual connection, spectators, and host
migration are intentionally unsupported. Physical-device, signed-store,
accessibility, privacy, legal, and operational evidence remain external release
gates even when the automated build is green.

Use these current documents before non-trivial changes:

- `docs/PRODUCTION_ARCHITECTURE.md`
- `docs/RELEASE_GATES.md`
- `docs/P2P_MANUAL_TEST.md`
- `docs/HOW_TO_ADD_A_GAME.md`
- accepted decisions under `docs/adr/`

`ARCHITECTURE.md`, `whodunit-game-design.md`, `docs/APP_PLAN.md`, and phase
reports are explicitly historical and are not current behavior contracts.

## Toolchain

- JDK 21
- checked-in Gradle 8.13 wrapper
- Android `compileSdk = 36`, `targetSdk = 36`, `minSdk = 26`
- minimum iOS 16
- P2pKit 0.7.0-rc3, pinned in `gradle/libs.versions.toml` and strict dependency
  verification metadata

Always use `./gradlew`. Do not add `mavenLocal()`, a sibling P2pKit composite
build, or an unverified repository override.

## Verification entry points

```bash
# Local development
./gradlew :composeApp:installDebug
./gradlew :composeApp:run

# Host-independent automated release gates
./gradlew productionCheck allTests --dependency-verification=strict

# Apple static analysis and release framework linkage (macOS)
./gradlew productionAppleCheck --dependency-verification=strict

# Protected signing gate; requires real release credentials
./gradlew productionAndroidSigningCheck --no-configuration-cache
```

`productionCheck` includes Desktop/common tests, Android debug/release unit
tests, release compilation, lint policy, R8/AAB creation, shell dispatch, and
repository-wide static analysis. `productionAppleCheck` links the supported
iOS release frameworks; linkage is not runtime validation. See
`docs/RELEASE_GATES.md` for the exact current matrix and external gates.

## Architecture invariants

The dependency graph points inward toward pure shared contracts:

- `:shared:core` contains common IDs, results, time, randomness, logging, and
  versioning;
- `:shared:engine` contains generic game/reducer/projection contracts and may
  not import UI, DI, transport, storage, or a game module;
- `:shared:engine-testing` is a non-shipping extensibility fixture;
- `:shared:networking` defines the versioned transport-independent wire and
  room contracts;
- `:shared:networking-testing` is the non-shipping in-memory transport fixture;
- `:shared:session` owns local controllers and host/peer authority semantics;
- `:shared:transport-p2p` is the only module allowed to import P2pKit;
- `:shared:storage` owns settings and protected snapshot storage;
- `:shared:content` owns case envelopes, repository contracts, and validation;
- `:game-modes:whodunit` and `:game-modes:mafia` own their rules, projections,
  codecs, UI, and multiplayer bridges;
- `:composeApp` owns catalog/shell/navigation composition, DI, and platform
  entry points.

Game modules depend on shared contracts, never on P2pKit. Shared modules never
depend on a shipping game module. The app shell composes both sides through
`GameShellRegistry`. The non-shipping fixtures in `:shared:engine-testing` and
`:shared:networking-testing` prove registration and transport-isolation
boundaries without entering production catalogs.

Konsist tests in `:shared:engine:desktopTest` enforce engine purity and absence
of Whodunit coupling. Detekt is applied repository-wide by the root build and
is part of `productionCheck`; do not add blanket baselines or broad
suppressions.

## State, authority, and privacy

Reducers are pure and topology-agnostic. `SessionController` is the I/O
boundary used by both local and multi-device flows. The host is the only
canonical multiplayer reducer owner. A peer command must pass authenticated
transport identity binding, protocol/session/game checks, sequence and command
deduplication, expected-revision validation, actor authorization, and reducer
validation before mutation.

Game state is separated into public, per-player private, and host-only data.
Peer snapshots contain a public projection plus exactly the receiving player's
private projection. Never serialize a host projection, role map, room secret,
rejoin credential, security key, or another player's private slice to a peer,
log, error, preview, or accessibility label.

## Content and persistence

Production content is bundled and offline-only. Whodunit JSON resources live
under `game-modes/whodunit/src/commonMain/composeResources/files/cases/` and
are enumerated by `BundledWhodunitCatalog`; an executable contract keeps the
resource set and catalog identical. Production uses
`OfflineRemoteCaseDataSource`; Ktor `MockEngine` appears only in tests. Every
case still passes the common envelope validator and the Whodunit payload
validator before use.

Persisted game snapshots use authenticated, platform-protected storage and
game-owned strict recovery validation. Multiplayer rejoin credentials use the
platform secure-storage adapter and are distinct from local game snapshots.
Do not normalize corrupt or reducer-impossible state into a different game.

## Change discipline

- Preserve unrelated and uncommitted work.
- Add a regression test for each correctness defect where practical.
- Preserve `CancellationException` across broad error boundaries.
- Keep queues, payloads, retries, diagnostics, and ledgers explicitly bounded.
- Do not add optimistic peer mutations or blindly retry non-idempotent actions.
- Do not weaken tests, dependency verification, static analysis, lint policy,
  privacy boundaries, or release gates to obtain a green build.
- Keep implementation commits coherent and recoverable; never publish or sign
  without explicit authorization.
