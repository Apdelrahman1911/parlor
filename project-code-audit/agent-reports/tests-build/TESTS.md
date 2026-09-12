# Tests — matrix, wiring, holes

## Source-set inventory (`*Test*.kt`, excluding `build/`)

| Source set | Files | Where CI runs them |
|---|---|---|
| `commonTest` | 54 | Host JVM via module `allTests` / `desktopTest` (common tests are compiled into desktop + Android unit + iOS simulator when those tasks run) |
| `desktopTest` | 86 | `productionDesktopCheck` (every module) and Linux `allTests` |
| `androidUnitTest` | 3 | `productionAndroidCheck` → `:composeApp:testDebugUnitTest` + `testReleaseUnitTest` |
| `iosTest` | 1 | `productionIosSimulatorRuntimeTests` (macOS only) |
| `androidInstrumentedTest` / `androidTest` | **0** | Detekt hook exists; no sources; no connected-device job |

Per module (`*Test*.kt`):

| Module | commonTest | desktopTest | androidUnitTest | iosTest |
|---|---|---|---|---|
| composeApp | 11 | 5 | 3 | **1** |
| game-modes/whodunit | 2 | 43 | 0 | 0 |
| game-modes/mafia | **0** (deps declared, no files) | 24 | 0 | 0 |
| shared/session | 10 | 1 | 0 | 0 |
| shared/transport-p2p | 5 | 9 | 0 | 0 |
| shared/networking | 8 | 0 | 0 | 0 |
| shared/content | 4 | 0 | 0 | 0 |
| shared/storage | 4 | 0 | 0 | 0 |
| shared/design-system | 5 | 2 | 0 | 0 |
| shared/engine | **0 files** (deps declared) | 2 Konsist | 0 | 0 |
| shared/engine-testing | 3 | 0 | 0 | 0 |
| shared/core | 2 | 0 | 0 | 0 |
| shared/networking-testing | **0 tests** (fixture module) | 0 | 0 | 0 |

144 `*Test*.kt` files total.

## How tasks attach

Convention `parlor.kmp.library` registers Android + `jvm("desktop")` +
iosX64/iosArm64/iosSimulatorArm64 and `useJUnitPlatform()` on Gradle `Test`.
`:composeApp` duplicates JUnit Platform locally (cannot use the library
convention).

Root wiring (`build.gradle.kts`):

- every KMP `desktopTest` → `productionDesktopCheck`
- every `iosSimulatorArm64Test` → `productionIosSimulatorRuntimeTests`
- every module `allTests` → root `allTests`
- `productionAndroidCheck` only names **composeApp** Android unit-test tasks
  (`testDebugUnitTest` / `testReleaseUnitTest`). Other modules’ Android
  compilations of `commonTest` are **not** named there; they ride
  `allTests` on Linux if the Kotlin report includes them.

`./gradlew help --task allTests` (this session): root depends on all 13
included KMP modules’ `allTests`. On this macos-arm64 host Gradle warned
`iosX64Test` is disabled (arch mismatch). Linux CI cannot execute any
ios*Test; the Apple job is the only simulator runtime.

Xcode scheme `iosApp.xcscheme` has **empty `<Testables>`**. Swift wrapper
has no XCTest. iOS runtime coverage is the single Kotlin `iosTest` file
plus commonTest compiled for `iosSimulatorArm64`.

## `@Ignore` / `@Disabled`

Only three, all in
`shared/transport-p2p/.../P2pKitRoomTransportLoopbackTest.kt`:

- `peer_can_join_a_hosted_room_and_membership_appears_on_host`
- `peer_to_host_message_round_trips_and_host_to_peer_message_arrives_back`
- `host_broadcast_reaches_every_peer`

Reason in source: single-JVM mDNS loopback is unreliable; “run the
manual LAN runbook.” The one non-ignored test only hosts and asserts a
6-char room code. **No automated host↔peer P2pKit round-trip.**

No `@Disabled`. No other `@Ignore`.

## Tests that only (or primarily) parse docs / config

These sit on `desktopTest` → `productionDesktopCheck` → Linux CI. They
are real gates, but they prove **string presence in files**, not runtime
behavior.

| Test | Reads |
|---|---|
| `MultiplayerDocumentationContractTest` | `docs/P2P_MANUAL_TEST.md`, `docs/P2P_REMEDIATION_STATUS.md`, `docs/PRODUCTION_ARCHITECTURE.md`, `docs/CONTENT_SCHEMA.md`, `docs/CONTENT_REVIEW.md`, `docs/MOCK_BACKEND.md`, `docs/MOTION_DOWNGRADE.md`, `docs/ACCESSIBILITY_AUDIT.md`, `docs/IOS_SETUP.md`, plus `ARCHITECTURE.md`, `CLAUDE.md`, `README.md` |
| `ProductionVerificationWorkflowContractTest` | workflows, Gradle, pbxproj, verification-metadata slices, **`docs/IOS_SETUP.md`**, **`docs/RELEASE_RUNBOOK.md`** |
| `AndroidReleaseLintContractTest` | lint inventory + **`docs/ANDROID_LINT_TRIAGE.md`** (`"reported 59 warnings"`, `"contains 29"`) |
| `P2pKitMavenProvenanceContractTest` | catalog + verification-metadata SHA list |

`:shared:transport-p2p:desktopTest` declares those docs/manifests/plists
as task inputs (stale PASS after a doc edit is prevented).

Python: `scripts/release/tests/` (116) + `workflow_contract.py` run inside
`productionReleaseAutomationCheck` on Linux. They parse YAML/Gradle/policy;
they do not call Play/ASC unless `--execute` (blocked by identity).

## What each CI job therefore proves

**Linux `productionCheck` + `allTests` proves:**

- every `desktopTest` + commonTest-on-JVM
- composeApp Android debug **and** release unit tests (3 files + commonTest)
- Detekt (plain + type-aware common/desktop/Android)
- unsigned R8 AAB + lint inventory equality + merged-manifest policy
- `verifyApplicationIdentities` (ID must stay `com.parlor.app`)
- `verifyGameShellDispatch` (token grep)
- release-script unittests + actionlint + shellcheck
- strict dependency-verification checksums

**Linux does not prove:**

- iOS simulator runtime (the one `iosTest` + commonTest-on-Native)
- Apple type-aware Detekt / framework link
- signed AAB / Play
- instrumented Android
- host↔peer LAN transport (ignored)

**macOS Apple job proves:**

- `iosSimulatorArm64Test` for every module that has it (today: composeApp
  `IosStorageSafetyTest` + commonTest compiled for that target)
- type-aware iOS Detekt
- `linkReleaseFrameworkIos{Arm64,SimulatorArm64,X64}`
- unsigned simulator `Parlor.app` identity + version macros
- plist/privacy lint

**macOS Apple job does not prove:**

- device / App Store IPA (candidate workflow only)
- XCTest
- `iosX64Test` on arm64 runners (disabled)
- `allTests` (explicitly excluded from the Apple step)

**Candidate `publish=true` would additionally prove** signed AAB/IPA
validation scripts — and then **fail** at
`assert-store-identity-approved` before a Store mutation.

## Matrix holes

1. **No androidInstrumentedTest anywhere.** UI, permissions, backup, LAN
   multicast, and process death are untested on device.
2. **One Kotlin iosTest file** (`IosStorageSafetyTest`). No Swift tests.
   commonTest-on-iOS is compile+run of JVM-oriented tests, not UIKit.
3. **Mafia has no commonTest sources** — rules live only in `desktopTest`.
   They run on Linux; they do not run as Native tests unless KMP copies
   desktopTest (it does not).
4. **Engine has no commonTest** — only two desktop Konsist files.
5. **P2pKit join/broadcast ignored** — production transport path is
   host-advertise-only in CI.
6. **Doc-parsing tests** can fail CI when prose drifts; they cannot fail
   CI when runtime diverges from that prose.
7. **`productionDesktopCheck` never compiles the desktop application**
   (`:composeApp:compileKotlinDesktop` / `run` / package) despite its
   description.
8. **Xcode scheme TestAction is empty** — `xcodebuild test` would run
   nothing even if someone invoked it.
9. Dirty-tree MOBILE_RELEASE tests are **not on HEAD**; HEAD CI does not
   yet assert those aliases.

## Ignore policy vs product risk

The three ignored tests are the only automated attempt at real P2pKit
membership + message round-trip. Session/protocol tests use
`InMemoryRoomBus` (`:shared:networking-testing`). A green
`productionCheck` is compatible with a broken LAN adapter.
