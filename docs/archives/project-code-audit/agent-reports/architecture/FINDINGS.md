# Architecture findings

IDs AR-001+. Evidence is file:line. Severity is about architecture risk, not
whether the app currently ships a wrong catalog.

---

## AR-001 Unused engine contracts (`GameSession`, `TimerService`)

- **Severity:** Medium
- **Confidence:** High
- **Evidence:**
  - `shared/engine/src/commonMain/kotlin/com/parlor/engine/session/GameSession.kt:22` defines `GameSession`
  - `shared/engine/src/commonMain/kotlin/com/parlor/engine/timer/TimerService.kt:13` defines `TimerService`
  - repo-wide grep of both names: only those declarations plus a `GameAction` kdoc mention
  - runtime I/O boundary is `shared/session/.../SessionController.kt:23`
- **Why it matters:** The engine advertises a session/timer runtime it does not
  own. Callers must discover that `SessionController` is the real contract.
  Two unused public types inflate the “generic engine” story for two games.

---

## AR-002 `:shared:transport-p2p` declares `:shared:session` but never imports it

- **Severity:** Medium
- **Confidence:** High
- **Evidence:**
  - `shared/transport-p2p/build.gradle.kts:11` `implementation(project(":shared:session"))`
  - grep `com.parlor.session` under `shared/transport-p2p` → 0
  - transport implements `RoomTransport` and uses `SecureStorage` only
- **Why it matters:** Declared edge is a latent cycle risk
  (`session → networking ← transport`, plus unused `transport → session`).
  The physical layering is cleaner than Gradle claims. Dead edges hide
  accidental future imports.

---

## AR-003 Public unused HTTPS content adapter on the shipping classpath

- **Severity:** Medium
- **Confidence:** High
- **Evidence:**
  - `shared/content/build.gradle.kts:13` `implementation(libs.ktor.client.core)` on commonMain
  - `KtorRemoteCaseDataSource` is a public `class` at `shared/content/.../KtorRemoteCaseDataSource.kt:40`
  - production bind is `composeApp/.../di/ContentModule.kt:59` `OfflineRemoteCaseDataSource()`
  - `MockEngine` / `ktor-client-mock` appear only in test source sets
- **Why it matters:** Offline-only release still compiles a public remote
  client into the content library. Reachability from DI is correctly closed,
  but any future Koin edit can flip the app onto an unreviewed HTTPS path
  without a new module. Adapter should be internal and/or not on commonMain
  until a remote product exists.

---

## AR-004 Test fakes live in production source sets

- **Severity:** Medium
- **Confidence:** High
- **Evidence:**
  - `shared/core/.../Clock.kt:23` public `FakeClock` in commonMain
  - `shared/storage/.../InMemorySnapshotStore.kt:17` public in commonMain
  - `shared/storage/.../InMemorySettingsStore.kt` (class in commonMain)
  - `shared/storage/.../InMemorySecureKeyValueBacking` used by
    `composeApp/src/desktopMain/.../PlatformStorage.desktop.kt:21`
  - Contrast: `RoundRobinAnnounceGame` and `InMemoryRoomBus` are correctly isolated
- **Why it matters:** Desktop is a non-shipping harness, so in-memory secure
  backing there is intentional. Shipping `FakeClock` / `InMemorySnapshotStore`
  as public production API still lets a mistaken Koin bind persist snapshots
  only in RAM on a mobile build. Isolation policy is inconsistent with
  networking-testing / engine-testing.

---

## AR-005 Whodunit setup/lobby UI lives in composeApp; Mafia’s does not

- **Severity:** Medium
- **Confidence:** High
- **Evidence:**
  - composeApp owns `shell/game/whodunit/{WhodunitHostSessionFlow,WhodunitPeerSessionFlow,WhodunitCasePickerScreen}.kt` (673 / 713 / 221 lines)
  - those files import `com.parlor.games.whodunit.*` rules, case types, and multiplayer flows
  - Mafia binding (`MafiaGameShellBinding.kt:22-25`) delegates lobby to
    `MafiaHostLobbyFlow` / `MafiaPeerLobbyFlow` inside `:game-modes:mafia`
  - `verifyGameShellDispatch` (`composeApp/build.gradle.kts:500-525`) does not
    scan `shell/game/**`, so this split is invisible to the gate
- **Why it matters:** Composition-root registration is correct, but Whodunit
  host/join orchestration is app-shell code. Adding a third game by copying
  Whodunit would grow composeApp, not the game module. The “bindings own
  setup/lobby” rule is only half-implemented.

---

## AR-006 God objects at the host-authoritative boundary

- **Severity:** Medium
- **Confidence:** High
- **Evidence:**
  - `P2pKitRoomTransport.kt` 4589 lines, `@Suppress("LargeClass")` at line 99
  - `AuthoritativeSessionCoordinator.kt` 1936 lines (host + peer coordinators in one file)
  - `ProcessMultiplayerSessionOwner.kt` 1034 lines
- **Why it matters:** These are the actual runtime spine for two LAN games.
  Size is not automatically wrong, but the transport file mixes discovery,
  admission, resume credentials, diagnostics, and lifecycle. Review and
  change isolation are poor. This is the complexity that *is* load-bearing —
  and it is concentrated, not modularized.

---

## AR-007 Leftover `:shared:navigation` tree

- **Severity:** Low
- **Confidence:** High
- **Evidence:**
  - `settings.gradle.kts` has no `include(":shared:navigation")`
  - no `project(":shared:navigation")` in any `*.kts`
  - `shared/navigation/` still has empty `src/`, `.DS_Store`, and a full `build/`
    (desktop/android/ios artifacts, `NavGraphRegistryTest` results)
  - Konsist `PurityTest.kt:42` still forbids `com.parlor.navigation`
- **Why it matters:** Dead module debris. Stale build outputs can confuse
  inventory and search. Not on the compile graph.

---

## AR-008 Default-public APIs; P2pKit types leak from transport

- **Severity:** Low
- **Confidence:** High
- **Evidence:**
  - engine / session / networking / content / storage types are public by default
  - `P2pKitFactory.kt:22` public interface returns `dev.p2pkit.core.P2pKit`
  - `P2pKitRoomTransport` is a public class; most sibling types are `internal`
  - composeApp shell types are correctly `internal`
- **Why it matters:** KMP `internal` is module-scoped. Public factory/transport
  types let any downstream (composeApp, theoretically others) mention P2pKit
  without a Gradle p2pkit dependency only until they import the factory.
  Isolation today is “only this module imports p2pkit,” not “P2pKit cannot
  appear in a public signature.”

---

## AR-009 Abstraction weight vs two-game LAN product

- **Severity:** Low
- **Confidence:** Medium
- **Evidence:**
  - two shipping `GameDefinition`s registered as a hardcoded list in `ContentModule.kt:44-50`
  - `RoomTransport` comments (`RoomTransport.kt:17-21`) describe future extra transports; production always binds P2pKit (`P2pBootstrap.kt:13`)
  - content has three-source repository + unused Ktor adapter for bundled-only cases
  - engine `GameStateContainer` three-bucket model *is* used by both games’ privacy/snapshot path
- **Why it matters:** Plugin registry + transport interface + content pipeline
  are sized for a catalog/platform. Current runtime is two games, one LAN
  transport, offline bundles. The split is justified where it enforces
  host authority and privacy. It is not justified for unused session/timer
  types, unused Gradle edges, or a dormant HTTP client. Confidence Medium
  because some of the weight is prepaid insurance for a third game, which
  the composition-root design does make cheap *if* lobby UI stays in the
  game module (it does not, for Whodunit — AR-005).

---

## Explicitly verified non-findings

These were in scope and **passed**:

- engine does not import UI/DI/transport/games (imports + Konsist)
- shared modules do not depend on shipping games (Gradle + imports)
- only transport-p2p imports P2pKit
- networking-testing is not a shipping source-set dependency
- MockEngine is not on any `*Main` source set
- `KtorRemoteCaseDataSource` is not bound in production DI
- `RoundRobinAnnounceGame` cannot enter production catalogs via current Koin
- composition root is the only game-registration site
- no circular Gradle dependencies
- `verifyGameShellDispatch` files contain no forbidden tokens
