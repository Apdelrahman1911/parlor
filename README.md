# Parlor

A party-games app built on Kotlin Multiplatform + Compose Multiplatform. Whodunit is the first game module; *The Last Dinner* is the first case.

This repository is the result of Phase 0 (planning + content/schema audit) and Phase 1+ (project skeleton, shared modules, premium design-system baseline, generic engine, content system, Whodunit module, …).

See:
- `whodunit-game-design.md` — game design source of truth.
- `ARCHITECTURE.md` — system architecture source of truth.
- `docs/APP_PLAN.md` — product execution plan, Phase 0–8 + Post-MVP.

## Requirements

- **JDK 21+**
- **Android Studio Iguana or newer** with Android SDK (`compileSdk = 35`, `minSdk = 26`)
- **Xcode 16+** (only required to build/run the iOS target; not required for Android or Desktop)

## First-time bootstrap

The Gradle wrapper jar is not committed. Once Gradle is on `PATH` (or via Android Studio's bundled distribution), run:

```bash
gradle wrapper --gradle-version 8.11.1
```

This materializes `gradle/wrapper/gradle-wrapper.jar`. Subsequent builds use `./gradlew`.

## Run

Android (device or emulator):

```bash
./gradlew :composeApp:installDebug
```

Desktop (JVM):

```bash
./gradlew :composeApp:run
```

iOS: open `iosApp/iosApp.xcodeproj` in Xcode and run.

## Tests

```bash
./gradlew test
./gradlew :shared:engine:allTests
```

## Architecture checks

The project enforces purity and dependency rules from `ARCHITECTURE.md` §3.2 and §3.3 via Konsist tests. They run with `./gradlew test`.

## Project layout

```
parlor/
├── build-logic/                   # Gradle convention plugins
├── composeApp/                    # KMP+CMP app module
├── iosApp/                        # Xcode wrapper (not Gradle) — generated later
├── shared/
│   ├── core/                      # Pure Kotlin: Result, ids, time, logging
│   ├── design-system/             # Parlor base tokens, components, motion
│   ├── engine/                    # Generic game engine (no Whodunit refs)
│   ├── session/                   # SessionController + pass-and-play impl
│   ├── content/                   # Case schema, repository, validation
│   ├── networking/                # LocalRoom + transport contracts (stubs)
│   ├── storage/                   # Snapshots, settings, secure storage
│   └── navigation/                # Type-safe routes, NavGraphRegistry
├── game-modes/
│   └── whodunit/                  # Whodunit module: definition, modes, UI
├── content/                       # Case JSON drafts (e.g., last-dinner.draft.json)
├── docs/                          # Planning + spec docs
└── gradle/libs.versions.toml      # Version catalog
```

## Status

This is the post-Phase-1 scaffold. Phases 2–8 fill in engine logic, content delivery, gameplay screens, safety/persistence, multi-device shape test, and production polish. See `docs/APP_PLAN.md` §5 for the full breakdown.
