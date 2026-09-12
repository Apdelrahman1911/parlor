# Architecture workstream — NOTES

CODE-ONLY. Reconstructed from Gradle graphs, Koin wiring, and Kotlin imports.
No `*.md` outside `project-code-audit/` was read.

## Declared module graph (settings + build.gradle.kts)

Included modules (`settings.gradle.kts`):

- Application: `:composeApp`
- Shared: `:shared:core`, `:shared:design-system`, `:shared:engine`,
  `:shared:engine-testing`, `:shared:session`, `:shared:content`,
  `:shared:networking`, `:shared:networking-testing`, `:shared:storage`,
  `:shared:transport-p2p`
- Games: `:game-modes:whodunit`, `:game-modes:mafia`

`:shared:navigation` is **not** included. Directory still exists.

Convention plugins (`build-logic/convention`):

- `parlor.kmp.library` — Android + desktop JVM + iOS x64/arm64/simulatorArm64, Java 21
- `parlor.kmp.compose.library` — layers Compose plugins on the library convention
- `parlor.detekt` — applied to every subproject from root `build.gradle.kts`

`:composeApp` does **not** use the library convention (Android application + JUnit 5 applied locally).

## Runtime call path (not package names)

```
UI (composeApp App / GameShellBinding)
  -> SessionController (PassAndPlay / Shadow / PartyAware)
       -> GameReducer via GameDefinition (pure)
  -> RoomTransport (Koin) = P2pKitRoomTransport
       -> RoomMessage CBOR (networking protocol 4.2)
  -> HostAuthoritativeSessionCoordinator / PeerAuthoritativeSessionCoordinator
       -> applyCommand + player-specific snapshots
```

Host is the only multiplayer reducer owner. Peers submit commands and apply
snapshots. No optimistic peer mutation in the session contract.

## Koin composition root

`composeApp/.../di/AppModule.kt` `allModules`:

1. `coreModule` — Clock, SessionSeedSource, AppLifecycleCoordinator,
   ProcessMultiplayerSessionOwner, strict Json
2. `whodunitModule` — WhodunitDefinition, PayloadValidator, BundledFallbackCaseDataSource
3. `mafiaModule` — MafiaDefinition only
4. `contentModule` — **the catalog registration site**
5. `storageModule` + `platformStorageModule()`
6. `p2pBootstrapModules()` → `p2pTransportModule` (always on)

`contentModule` constructs:

- `WhodunitGameShellBinding` + `MafiaGameShellBinding`
- `DefaultGameShellRegistry(listOf(those two))`
- `DefaultGameRegistry(shell.all.map { it.definition })`
- `RemoteCaseDataSource` = `OfflineRemoteCaseDataSource()`
- `CachedCaseDataSource` = `InMemoryCachedCaseDataSource()`
- `CaseValidator` / `CaseRepository`

Game modules do not append themselves to the catalog. They only publish
definition/content singles. Catalog membership is an explicit list in
`contentModule`.

## Isolation checks (Gradle + imports)

| Invariant | Gradle | Imports |
|---|---|---|
| engine ↛ UI/DI/transport/games | engine depends only on `:shared:core` + kotlinx | commonMain imports are `com.parlor.core.*`, `com.parlor.engine.*`, kotlinx only |
| shared ↛ shipping games | only `:composeApp` `project(":game-modes:*")` | no `import com.parlor.games` under `shared/` |
| P2pKit only in transport-p2p | only that module lists `libs.p2pkit.*` | `dev.p2pkit` imports only under `shared/transport-p2p` |
| networking-testing test-only | `commonTest` of session, whodunit, mafia | `InMemoryRoomBus` not in any `*Main` outside that module |
| engine-testing test-only | composeApp `commonTest`, content/session `commonTest` | `RoundRobinAnnounceGame` never referenced from `*Main` |
| MockEngine test-only | `ktor-client-mock` only in test source sets | `MockEngine` only in `*Test` |

## Dual registries

`GameShellRegistry` (composeApp, `internal`) owns UI bindings + capabilities.
`GameRegistry` (engine, public) owns definitions for content validation.
They are derived from the same binding list at the composition root
(`GameShellCompositionTest` asserts id lists match).

## `verifyGameShellDispatch`

Registered in `composeApp/build.gradle.kts`, required by root `productionCheck`.
String-scans lowercase tokens `whodunit`, `mafia`, `com.parlor.games.` in:

- `App.kt`, `AppBackPolicy.kt`, `LocalResumeRouter.kt`, `HomeScreen.kt`
- `shell/multiplayer/**`

Does **not** scan `GameShellRegistry.kt` or `shell/game/**` (bindings are
allowed to name games). Grep of the scanned files: no forbidden tokens.

## Leftover navigation

`shared/navigation/` has `.DS_Store`, empty `src/`, and a full stale `build/`
(jars, test results for `NavGraphRegistryTest`, iOS kexe). No `build.gradle.kts`,
not in `settings.gradle.kts`, no `project(":shared:navigation")`.

## Public vs internal (sampled)

Default visibility is public across engine, session, networking, content,
storage, core. transport-p2p is the exception (most types `internal`), but
`P2pKitFactory` and `P2pKitRoomTransport` are public and mention `dev.p2pkit`
types. composeApp shell types (`GameShellBinding`, `GameShellRegistry`) are
`internal`.

## Complexity vs actual product

Shipped product: two games, same-LAN host-authoritative play, offline bundled
content, desktop = run/tests only.

Justified: engine/session/networking/transport split; composition-root catalog;
P2pKit isolation; test-fixture modules.

Not justified by current runtime: unused `GameSession` + `TimerService`;
declared but unused `transport-p2p → session` edge; public unused
`KtorRemoteCaseDataSource` + `ktor-client-core` on the shipping content
classpath; test fakes in production source sets; 4.5k-line transport file;
Whodunit host/peer lobby implemented in composeApp while Mafia lobby lives in
the game module.
