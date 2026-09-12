# Architecture review

Lead verdict after verifying AR-* against Gradle + imports + Koin.

## What holds

- Inward dependency graph: games → shared; shared ↛ games.
- `:shared:engine` is pure Kotlin (Konsist `PurityTest` + import scan).
- Only `:shared:transport-p2p` imports P2pKit. Always on the graph.
- `:shared:networking-testing` and `:shared:engine-testing` are not shipping
  `*Main` dependencies.
- Composition root (`allModules` + `contentModule` binding list) is the only
  catalog registration site.
- `verifyGameShellDispatch` keeps `App.kt`, `HomeScreen`, resume router, and
  `shell/multiplayer/**` game-id-neutral.
- Host authority is structural: peers install snapshots; they do not reduce.
- Privacy buckets (public / per-player private / host-only) are engine-level
  and used by both games’ codecs.

## What does not match the layering story

- **AR-005:** Whodunit host/join/case-picker UI lives in `composeApp/shell/game/whodunit/`.
  Mafia lobby lives in `:game-modes:mafia`. Extensibility is uneven.
- **AR-001:** `GameSession` and `TimerService` in engine are unused. Real I/O
  boundary is `SessionController`.
- **AR-002:** transport Gradle depends on `:shared:session` but never imports it.
- **AR-003 / ST-002 / SP-002:** public `KtorRemoteCaseDataSource` + ktor-client-core
  on shipping content classpath; DI binds offline stub.
- **AR-004:** `FakeClock`, `InMemorySnapshotStore`, `InMemorySettingsStore` are
  public commonMain. Desktop credentials are in-memory by design.
- **AR-006:** `P2pKitRoomTransport` (~4589 lines) and
  `AuthoritativeSessionCoordinator` (~1936 lines) concentrate the runtime spine.
- **AR-007:** empty leftover `shared/navigation/` not in settings.
- **AR-008:** `P2pKitFactory` public API returns `dev.p2pkit.core.P2pKit`.

## Complexity vs product

Justified: host-authoritative protocol, projection policy, credential store,
exact 4.2 codec, game plugin types.

Not justified for a two-game offline LAN app: unused engine session/timer,
three-source content repo whose remote/cache never run in prod, unused Gradle
edge, public HTTPS adapter.

**Assessment:** layering is production-grade. Module hygiene and file size are
not. The architecture would support a third game if lobby UI stays in the game
module (Mafia pattern), not the Whodunit-in-composeApp pattern.
