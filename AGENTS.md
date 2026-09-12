# Parlor

KMP/CMP party-game container. Android and iOS ship; Desktop is for `run` and
deterministic tests only. Same-LAN host-authoritative multiplayer over P2pKit;
no public-internet play, raw-IP/manual join, spectators, or host migration.

Executable source and Gradle tasks win over prose. Current contracts:
`docs/PRODUCTION_ARCHITECTURE.md`, `docs/RELEASE_GATES.md`,
`docs/HOW_TO_ADD_A_GAME.md`, accepted `docs/adr/`. Current execution status and
remaining gates: `docs/PROJECT_STATUS.md`. Historical plans, design baselines,
audits, and phase reports live in `docs/archives/history/`; superseded handoffs
live in `docs/archives/handoffs/`. Use `docs/archives/README.md` to resolve their
original paths; historical results do not certify a newer checkout.

## Toolchain

- JDK 21 and `./gradlew` (Gradle 8.13). Never a system Gradle.
- P2pKit `0.7.0-rc3` from Maven Central only; keep `p2p-core` and
  `p2p-transport-lan` on the same version. Do not add `mavenLocal()`, a sibling
  composite build, or a repository override.
- Strict dependency verification is the default. Do not regenerate
  `gradle/verification-metadata.xml` to silence a failure; review the graph
  first (`docs/P2PKIT_MAVEN_PROVENANCE.md`).
- AGP stays 8.13.2. Lint `9.1.1` and R8 `9.1.41` are independent pins so
  analyzers understand Kotlin 2.4 metadata — do not drop them.
- Version name/build number live in `config/parlor-version.xcconfig` (Xcode +
  Gradle). Do not hardcode versions in Gradle or Info.plist.
- iOS: no CocoaPods. Open `iosApp/iosApp.xcodeproj` or the `.run/iOS App`
  configuration. Store-qualified Xcode is 26.3 / 17C529; deployment target 16.
- Debug Android/iOS IDs use a `.debug` suffix so they can sit beside Store
  installs. `com.parlor.app` is pinned by `verifyApplicationIdentities` and is
  a known Store-identity collision; do not “fix” or ship it, and do not
  re-enable the disabled candidate/promotion workflows.

## Commands

Focused work uses a module `desktopTest`, not `productionCheck`:

```bash
./gradlew :composeApp:run
./gradlew :composeApp:installDebug
./gradlew :game-modes:whodunit:desktopTest --tests '*FooTest*'
./gradlew :shared:session:desktopTest
./gradlew productionDesktopCheck          # every module desktopTest + desktop compile
./gradlew productionCheck                 # host-independent release gates; not Apple, not allTests
./gradlew allTests --dependency-verification=strict
./gradlew productionAppleCheck --dependency-verification=strict          # macOS; linkage ≠ runtime
./gradlew productionIosSimulatorRuntimeTests --dependency-verification=strict
./gradlew productionAndroidSigningCheck --no-configuration-cache         # real credentials
./gradlew staticAnalysis
```

`allTests` is an explicit root aggregate — Gradle abbreviation used to bind
whatever module task the caller happened to select. `productionCheck` already
runs desktop/Android tests, Detekt, lint/R8/unsigned AAB, shell-dispatch, and
`scripts/release/validate_release_system.sh`. CI adds `allTests` on Linux and
the Apple tasks on macOS.

## Module boundaries

- `:composeApp` — catalog, navigation, DI, platform entry. Register games at
  the composition root only.
- `:shared:engine` — generic definitions/reducers/projections. No UI, DI,
  transport, storage, or game module. Konsist in `:shared:engine:desktopTest`.
- `:shared:session` — local + host/peer authority. UI talks to
  `SessionController`.
- `:shared:networking` — transport-independent protocol (exact 4.2).
- `:shared:transport-p2p` — the only P2pKit import. Always in the graph.
- `:shared:content` / `:shared:storage` — bundled cases; authenticated
  snapshots and rejoin credentials (distinct stores).
- `:shared:engine-testing` — non-shipping registration fixture.
- `:shared:networking-testing` — test fixtures; never a shipping source-set
  dependency.
- `:game-modes:whodunit` / `:game-modes:mafia` — rules, codecs, UI, resources.

Game domain packages stay pure Kotlin (`:shared:core` / `:shared:engine` only).
Shared modules never depend on a shipping game. Adding a game is module
inclusion + composition-root binding, not a `when` in lobby, transport,
protocol, or session code.

`verifyGameShellDispatch` forbids `whodunit`, `mafia`, and `com.parlor.games.`
in `App.kt`, `AppBackPolicy.kt`, `LocalResumeRouter.kt`, `HomeScreen.kt`, and
`shell/multiplayer/**`. Bindings own setup/lobby/resume routes.

New KMP modules that apply the multiplatform plugin join Detekt,
`productionDesktopCheck`, and `allTests` automatically.

## Invariants

- Reducers are pure and topology-agnostic. The host is the only multiplayer
  reducer owner. No optimistic peer mutations; never auto-retry a rejected
  non-idempotent action.
- Protocol compatibility is exact major.minor. `GameId` values are
  kebab-case protocol/persistence IDs; renaming one is a migration.
- Snapshots: public projection + the receiving player's private slice only.
  Never serialize a host projection, role map, room secret, rejoin credential,
  or another player's private data to a peer, log, preview, or a11y label.
- Production content is bundled and offline (`OfflineRemoteCaseDataSource`).
  Ktor `MockEngine` is tests-only. Whodunit JSON under
  `game-modes/whodunit/src/commonMain/composeResources/files/cases/` must match
  `BundledWhodunitCatalog`.
- Do not normalize corrupt or reducer-impossible snapshots into another game.
- Preserve `CancellationException` across broad `catch`es. Bound queues,
  payloads, retries, and diagnostic ledgers.
- Detekt is repository-wide (`config/detekt/detekt.yml`, `maxIssues: 0`). Fix
  findings; no baselines or blanket suppressions.
- `:shared:transport-p2p:desktopTest` also hashes docs, manifests, plists, and
  verification metadata — those edits re-run that suite.

## Commits

Conventional Commits with optional scopes (`fix(release): …`). Do not weaken
tests, dependency verification, lint, privacy boundaries, or release gates to
get green. Do not commit secrets, signing material, or private player state.
