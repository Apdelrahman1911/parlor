# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this project is

Parlor is a party-games app built on Kotlin Multiplatform + Compose Multiplatform, targeting Android, iOS, and Desktop. **Whodunit** (case: *The Last Dinner*) is the first game module — Parlor is deliberately built as a multi-game platform, not a single-game app. The "platform vs. game module" split is load-bearing; do not collapse it.

Authoritative design docs (read these before non-trivial changes):

- `ARCHITECTURE.md` — system architecture. Section numbers (e.g. §3.3, §7) are referenced from code comments and tests.
- `whodunit-game-design.md` — game design source of truth for Whodunit rules, phases, content.
- `docs/APP_PLAN.md` — phase-by-phase execution plan and MVP scope.
- `docs/CONTENT_SCHEMA.md`, `docs/DESIGN_TOKENS.md`, `docs/IOS_SETUP.md`, `docs/MOCK_BACKEND.md` — supporting specs.

## Toolchain & bootstrap

- **JDK 21** required (Java 21 source/target across all modules).
- Android `compileSdk = 35`, `minSdk = 26`.
- The Gradle wrapper jar is **not committed**. On a fresh clone, with system Gradle on `PATH`:
  ```bash
  gradle wrapper --gradle-version 8.11.1
  ```
  This materializes `gradle/wrapper/gradle-wrapper.jar`. All subsequent builds use `./gradlew`.

## Common commands

```bash
# Build / run
./gradlew :composeApp:installDebug                # Android device or emulator
./gradlew :composeApp:run                         # Desktop (JVM)
./gradlew :composeApp:embedAndSignAppleFrameworkForXcode   # iOS framework (Xcode build-phase target)
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64  # iOS framework (CLI)

# Tests — run from JVM ("desktop") source set
./gradlew test                                    # All JVM tests across all modules (includes Konsist architecture tests)
./gradlew :shared:engine:allTests                 # Engine module only
./gradlew :game-modes:whodunit:desktopTest        # Whodunit tests
./gradlew :game-modes:whodunit:desktopTest --tests "com.parlor.games.whodunit.flow.FullGameDriveTest"  # Single test
```

iOS run is via Xcode (`iosApp/iosApp.xcodeproj`) or the Android Studio **iOS App** run configuration (`.run/iOS App.run.xml`), which calls `embedAndSignAppleFrameworkForXcode` as a pre-step.

## Architecture invariants (enforced by tests — do not weaken)

The dependency graph is one-way: `:composeApp → :shared:* → :game-modes:*` (and `:game-modes:*` depend on `:shared:*`, never the reverse). Two Konsist tests in `shared/engine/src/desktopTest` fail the build on violations:

- `PurityTest` — `:shared:engine` may import only Kotlin stdlib, `kotlinx.coroutines`, `kotlinx.serialization`, and `:shared:core`. **No Koin/Compose/Ktor/SQLDelight/platform APIs anywhere in the engine**, ever. Same purity rule applies to game-module `domain/` packages.
- `NoWhodunitInEngineTest` — `:shared:engine` has zero references to any Whodunit term.

Consequences when adding code:

- New cross-cutting helper? It belongs in `:shared:core` (pure Kotlin) or `:shared:engine` (if engine-shaped). Never reach down from `:shared:*` into a game module.
- Engine collaborators (clock, random, logger, timer service) enter the engine via constructor parameters. DI assembly happens at the app boundary (`composeApp/`), not inside `shared/engine`.
- Game-module UI/DI lives in sibling packages (`ui/`, `presentation/`, `di/`), never inside `domain/`.

## Privacy: three state buckets (ARCHITECTURE.md §7)

All game state is split into three **typed** buckets, enforced at the engine level — not a UI convention:

- `PublicState` — visible to everyone in the room.
- `PrivatePlayerState` — only the owning player.
- `HostOnlyState` — never leaves the host device (e.g. killer identity, killer variant seed).

Each `GameDefinition` ships a `ProjectionPolicy` that strips host-only fields from public projections and strips other players' private fields from per-player projections. Adding a new piece of private information is a **deliberate act**: declare it in the right bucket and update the projection policy. The type system prevents `HostOnlyState` from leaking into a `PublicProjection`.

## Topology boundary: `SessionController` (ARCHITECTURE.md §6)

The reducer is **pure** and **topology-agnostic**. The I/O boundary is `SessionController`. Pass-and-play (the MVP topology) and future local-multiplayer share the same reducer and same UI — only the controller implementation differs. Privacy enforcement is UI ceremony in pass-and-play (cover screens, hold-to-reveal, hide-and-pass) and transport-level in multi-device (the host never sends another player's private state). If you find yourself branching reducer logic on topology, the design is wrong.

## P2P (multi-device) is opt-in

Multi-device support depends on the external **P2pKit** library, which is not on Maven Central. The flag `parlor.p2p.enabled` controls integration in three steps (settings.gradle.kts has the full comment):

1. Explicit override in `gradle.properties` or `-P` always wins.
2. Auto-detect: enabled when P2pKit 0.6.0 is published to `~/.m2/.../dev/p2pkit/`.
3. Default off.

When off: `:shared:transport-p2p` is excluded from the build, and `composeApp` compiles `src/p2pDisabledMain/` (which returns an empty Koin modules list from `p2pBootstrapModules()`). When on: the alternate `src/p2pEnabledMain/` source dir is compiled and wires the real transport. **The pass-and-play code path must remain entirely independent of P2P** — never import from `:shared:transport-p2p` outside the `p2pEnabledMain` source dir or the transport module itself.

Note: `gradle.properties` currently sets `parlor.p2p.enabled=true` — builds will fail unless P2pKit 0.6.0 is in mavenLocal. To build without it, override with `-Pparlor.p2p.enabled=false` or edit the property.

## Player counts are declared, never hardcoded (ARCHITECTURE.md §1.4)

The effective player range is the intersection of: engine absolute (`3..16`), `GameDefinition`, `GameMode`, and validated case content. *The Last Dinner* ships with 6 characters → plays 4–6 Classic / 5–6 Elimination. **No layer of the codebase may hardcode `6` as a player ceiling.** Future cases with larger character pools must work without engine/mode/shell changes.

## Module map (where things live)

```
composeApp/                          # KMP+CMP app — shell, DI assembly, platform entry points
  src/commonMain/.../app/            # App.kt, di/, shell/ (home/library/settings/multiplayer), permissions/, storage/
  src/p2pEnabledMain | p2pDisabledMain   # Mutually exclusive — see "P2P is opt-in"
  src/{android,ios,desktop}Main      # Platform launchers + expect/actual shims only

shared/core                          # Pure Kotlin: Result, ids, clock, random, logger, UiText
shared/engine                        # Pure Kotlin engine — contracts only. Konsist-enforced
shared/engine-testing                # Test doubles for engine collaborators
shared/design-system                 # Tokens, ParlorTheme, components, motion, AmbientBackdrop
shared/session                       # SessionController contract + PassAndPlay impl
shared/content                       # Case schema base, repository, validator, Ktor MockEngine wiring
shared/networking                    # LocalRoom + transport contracts (interfaces)
shared/storage                       # Snapshot persistence, settings, secure storage
shared/navigation                    # Type-safe routes, NavGraphRegistry
shared/transport-p2p                 # P2pKit adapter — only included when parlor.p2p.enabled

game-modes/whodunit                  # Whodunit module: domain/ (pure), ui/, snapshot/, di/, content/
content/                             # Case JSON drafts (e.g., last-dinner.draft.json)

build-logic/convention/              # Precompiled plugins: parlor.kmp.library, parlor.kmp.compose.library,
                                     #                     parlor.android.app, parlor.detekt
```

`settings.gradle.kts` enables `TYPESAFE_PROJECT_ACCESSORS` — use `projects.shared.engine` style accessors in new build scripts.

## Content path

Cases are loaded as Compose Multiplatform resources from the Whodunit module (`game-modes/whodunit/src/commonMain/composeResources/files/cases/`), served in dev via Ktor's `MockEngine` (see `docs/MOCK_BACKEND.md`). **No case prose is generated by the reducer or hardcoded inline** — the reducer chooses *which* content to show, never *what it says*. Schema validation lives in `:shared:content` + the Whodunit-specific validator in the module.

## Style & test stack

- Kotlin official code style (`kotlin.code.style=official`).
- Detekt is configured via the `parlor.detekt` convention plugin.
- Tests run on `useJUnitPlatform()` (JUnit 5) — KMP tests live in `desktopTest` source sets (Konsist requires JVM source-tree access; only `desktopTest` runs the architecture checks).
