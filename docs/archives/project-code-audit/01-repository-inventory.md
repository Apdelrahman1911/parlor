# Repository inventory

Evidence: filesystem walk + `settings.gradle.kts` + module `build.gradle.kts` filenames. Documentation files were not used as product truth.

## What this tree is (structural, not behavioral)

A Gradle 8.13 KMP/CMP application (`rootProject.name = "parlor"`) with one Android/Desktop/iOS Compose app module, shared libraries, two game modules, included `build-logic`, an Xcode wrapper, and GitHub/Python release automation.

## Top-level (auditable)

| Path | Role |
|---|---|
| `settings.gradle.kts` | module inclusion, repos, R8 pluginManagement override |
| `build.gradle.kts` | root verification aggregates + Detekt wiring |
| `gradle.properties` | JVM 6g, configuration-cache, lint 9.1.1, R8 9.1.41 |
| `gradle/libs.versions.toml` | version catalog |
| `gradle/verification-metadata.xml` | strict dependency verification |
| `gradle/wrapper/` | Gradle 8.13 |
| `composeApp/` | application |
| `shared/*` | libraries |
| `game-modes/*` | shipping games |
| `build-logic/` | convention plugins |
| `iosApp/` | Swift host |
| `config/` | version xcconfig, detekt, lint allowlist, release-policy.json |
| `.github/workflows/` | CI |
| `scripts/release/` | release tool + validators |
| `.run/` | IDE run configs |
| `assets/` | not yet classified beyond existence |
| `local.properties` | local SDK path; gitignored conceptually, present on disk |

## Gradle modules and source sets

Kotlin counts are files, not tests.

### `:composeApp` (72 kt)

Source sets: `commonMain` 28, `commonTest` 11, `androidMain` 10, `androidUnitTest` 3, `desktopMain` 6, `desktopTest` 5, `iosMain` 8, `iosTest` 1.

Entry points:

- Android: `ParlorApplication`, `MainActivity`
- Desktop: `Main.kt` / `mainClass = com.parlor.app.MainKt` (from composeApp build)
- iOS Kotlin: `MainViewController.kt` hosted by `iosApp/iosApp/iOSApp.swift`

Also: `AndroidManifest.xml`, backup/data-extraction XML, launcher resources, EN+AR compose strings, `proguard-rules.pro`.

### `:shared:core` (11 kt)

`commonMain` 9, `commonTest` 2. IDs, Result, Clock, Random/SessionSeed, SemVer, UiText.

### `:shared:engine` (19 kt)

`commonMain` 17, `desktopTest` 2 (Konsist purity). GameDefinition, reducer, projections, registry, snapshot codec, timer, session types.

### `:shared:engine-testing` (4 kt)

Non-shipping `RoundRobinAnnounceGame` + tests. Included in settings.

### `:shared:session` (24 kt)

`commonMain` 13, `commonTest` 10, `desktopTest` 1. SessionController, pass-and-play, authoritative coordinator, handshake, outbox, process owner.

### `:shared:networking` (26 kt)

`commonMain` 12, `commonTest` 8, plus android/desktop/ios SecureHashes/SecureIds actuals.

### `:shared:networking-testing` (1 kt)

`InMemoryRoomBus` in `commonMain` only (test fixture module).

### `:shared:transport-p2p` (29 kt)

Always included. `P2pKitRoomTransport` + diagnostics + credential store + platform Koin modules. desktopTest includes contract tests over manifests/docs/workflows.

### `:shared:content` (16 kt)

Case envelope/repo/validators; `OfflineRemoteCaseDataSource`, `KtorRemoteCaseDataSource` (wiring TBD by storage-data agent).

### `:shared:storage` (13 kt)

Settings, snapshot store, secure storage. commonMain + commonTest only.

### `:shared:design-system` (44 kt)

Tokens, components, EN/AR strings, fonts, locale/reduced-motion actuals.

### `:game-modes:whodunit` (104 kt)

`commonMain` 56 + 7 case JSON + EN/AR strings; `commonTest` 3; `desktopTest` 45 + 3 snapshot goldens.

### `:game-modes:mafia` (76 kt)

`commonMain` 52 + EN/AR strings; `desktopTest` 24. No commonTest dir. No bundled JSON cases.

### `:build-logic:convention` (3 kt)

`parlor.kmp.library`, `parlor.kmp.compose.library` (filename), `parlor.detekt`.

## Leftovers

`shared/navigation/src/commonTest` exists with **zero files**. Not included in settings. Dead directory.

## Platforms encoded in convention plugin

`KmpLibraryConventionPlugin`: `androidTarget`, `jvm("desktop")`, `iosX64`, `iosArm64`, `iosSimulatorArm64`. Java/JVM 21.

## CI / release config present

- `.github/workflows/production-verification.yml`
- `.github/workflows/testing-candidate.yml`
- `.github/workflows/testing-external-promotion.yml`
- `.github/workflows/production-promotion.yml`
- `scripts/release/*.py`, `*.sh`, tests
- `config/release-policy.json`, `config/parlor-version.xcconfig`, `config/android-lint-accepted-warnings.txt`

Behavioral interpretation deferred to tests-build workstream.

## Full Kotlin path list

The complete per-module file list was generated 2026-09-01 from a filesystem walk and is the coverage baseline. Agents must mark each file inspected in their report. Lead will not repeat the 400+ path dump here; it lives in the start-pass tool output and in workstream file ledgers.
