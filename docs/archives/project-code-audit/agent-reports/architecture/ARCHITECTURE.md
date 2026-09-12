# Reconstructed architecture (code-only)

## Verdict

**HOLD — layering holds; complexity is only partly justified.**

The Gradle graph and import graph agree on the intended boundaries:

- engine is pure and game-agnostic
- shared modules do not depend on shipping games
- only `:shared:transport-p2p` imports P2pKit
- `:shared:networking-testing` and `:shared:engine-testing` are not shipping source-set dependencies
- production DI binds `OfflineRemoteCaseDataSource`; MockEngine is tests-only
- composition root is the only catalog registration site
- no Gradle cycles

The shipped product is two LAN party games. Several abstractions and leftover
artifacts are unused or leaky relative to that runtime. Those are findings,
not a graph violation.

## Layers (actual, from deps + call paths)

```
                    ┌──────────────── composeApp ────────────────┐
                    │ App / Home / Settings / GameShellRouter    │
                    │ Koin allModules                            │
                    │ GameShellBinding (Whodunit, Mafia)         │
                    └───────┬───────────────┬───────────┬────────┘
                            │               │           │
              :game-modes:* │               │           │ :shared:transport-p2p
              (definition,  │               │           │   RoomTransport impl
               reducer, UI, │               │           │   P2pKit only here
               content)     │               │           │
                            │               │           │
                            ▼               ▼           ▼
                     :shared:session   :shared:content  :shared:storage
                     SessionController CaseRepository   Snapshot/Settings/Secure
                            │               │
                            ▼               ▼
                     :shared:engine    :shared:networking
                     definitions /     protocol 4.2 + RoomTransport iface
                     reducers /        (api engine for Player on SessionStarting)
                     projections
                            │
                            ▼
                     :shared:core
```

`:shared:design-system` is a Compose leaf used by composeApp and both games.
It depends only on `:shared:core`.

Test-only modules (never `*Main` of a shipping module):

- `:shared:engine-testing` — `RoundRobinAnnounceGame` (`GameId("round-robin-test")`)
- `:shared:networking-testing` — `InMemoryRoomBus`

## Declared commonMain edges

| From | To |
|---|---|
| composeApp | core, engine, design-system, session, content, networking, storage, transport-p2p, whodunit, mafia |
| engine | core |
| session | core (api), engine (api), networking (impl) |
| networking | core (api), engine (api) |
| transport-p2p | core (api), networking (api), **session (impl, unused)**, storage (impl), p2pkit, koin |
| content | core (api), engine (impl), ktor-client-core |
| storage | core (api), engine (api) |
| design-system | core (api) |
| engine-testing | core (api), engine (api) — test consumers only |
| networking-testing | core (api), networking (api) — test consumers only |
| whodunit / mafia | core, engine, design-system, session, content, storage, networking, compose, koin |

No game module is a dependency of any shared module.

## Runtime ownership

1. **Catalog.** `contentModule` lists the two bindings. `GameRegistry` is derived
   from `GameShellRegistry.all.map { definition }`. Adding a game requires a
   composeApp edit (module include + `allModules` + binding list). That is
   composition-root registration, not a `when` in lobby/transport/session.
2. **Local play.** Binding builds `PassAndPlaySessionController` + game reducer.
   UI talks to `SessionController`, not `GameSession` (unused interface).
3. **Multiplayer.** Binding obtains `RoomTransport` from Koin
   (`P2pKitRoomTransport`). Host uses `HostAuthoritativeSessionCoordinator`;
   peer uses `PeerAuthoritativeSessionCoordinator` + `ShadowSessionController`.
   Protocol is exact major.minor 4.2. Snapshots are public + that player's
   private slice.
4. **Content.** `DefaultCaseRepository(remote=Offline, cache=in-memory, bundled=Whodunit)`.
   `KtorRemoteCaseDataSource` is public on the content classpath but unbound.

## Guards that match the graph

- Konsist `PurityTest` + `NoWhodunitInEngineTest` on engine desktopTest
- `verifyGameShellDispatch` on productionCheck
- `TestTransportIsolationContractTest` pins networking-testing to `commonTest`

## Where the graph is leaky or heavier than the product

See FINDINGS.md AR-001 … AR-009.

Largest production types observed:

- `P2pKitRoomTransport.kt` 4589 lines (`@Suppress("LargeClass")`)
- `AuthoritativeSessionCoordinator.kt` 1936 lines
- `ProcessMultiplayerSessionOwner.kt` 1034 lines
- Whodunit lobby UI in composeApp: Host 673 / Peer 713 / CasePicker 221
