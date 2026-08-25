# How to add a game

Parlor games are plugins at the source-module boundary. A game owns its rules,
state, wire payload codecs, UI, resources, and tests. It does not own room
discovery, admission, reconnect policy, or a P2pKit instance.

## Required module shape

Create `:game-modes:<game-id>` and apply the appropriate Parlor KMP convention
plugin. Keep the following responsibilities inside that module:

```text
game-modes/<game-id>/
├── domain/
│   ├── <Game>State.kt
│   ├── <Game>Action.kt
│   ├── <Game>Event.kt
│   ├── <Game>Reducer.kt
│   ├── <Game>ProjectionPolicy.kt
│   └── <Game>SnapshotCodec.kt
├── protocol/                  # game payload codecs/version, not transport
├── ui/                        # screens and SessionController-facing flows
├── di/<Game>DiModule.kt
└── <Game>Definition.kt
```

The domain package remains pure Kotlin. It may depend on `:shared:core` and
`:shared:engine`, but never Compose, Koin, storage, networking, or P2pKit.

## Registration contract

1. Implement `GameDefinition<State, Action, Event>`.
2. Give the game a stable kebab-case `GameId`. IDs are protocol and persistence
   identifiers; renaming one is a migration, not copy editing.
3. Declare metadata, modes, and supported player ranges in the definition.
   The shared engine/session layers consume these values; the composition root
   installs the binding list, while each binding owns its game-specific setup,
   content, lobby, and resume routes.
4. Implement a pure reducer, a projection policy for public/private/host-only
   state, and a versioned snapshot codec.
5. Implement `<Game>GameShellBinding` in
   `composeApp/src/commonMain/kotlin/com/parlor/app/shell/game/` as a
   `GameShellBinding`. Its `definition` must expose the same stable `GameId`;
   it supplies the catalog card, setup/lobby route, local snapshot/resume
   route, and any game-specific multiplayer start flow. The binding owns the
   game's Compose content; it is the shell adapter, not a navigation graph.
6. Export one Koin module containing the definition and game-local
   dependencies. Keep the shell binding in the app-shell module so game
   modules remain independent of app navigation and transport wiring.
7. Add that Koin module and binding at the app composition root. Register the
   binding in `DefaultGameShellRegistry`; the root `GameShellRouter` remains
   game-id neutral. Adding a game-specific `when` to lobby, transport,
   protocol routing, or shared session code is not allowed.

`DefaultGameRegistry` and `DefaultGameShellRegistry` fail fast on duplicate
game IDs. A duplicate must therefore fail at startup or in tests rather than
silently routing to whichever module was registered last.

## Multiplayer boundary

The game UI talks to `SessionController`; it never imports
`:shared:transport-p2p` or `dev.p2pkit`. The host-authoritative session layer:

- authenticates the actor from the admitted room seat;
- rejects actions that actor is not allowed to perform;
- passes a decoded game action to the game adapter/reducer;
- advances one canonical revision at a time; and
- publishes a public projection plus only the recipient's private projection.

A game supplies only its payload codec, action-authority policy, state
projection, and snapshot codec. Adding a game must not change room admission,
ordering/deduplication, heartbeat, reconnect, or transport framing.

## Minimum acceptance suite

Before registering a shipping game, add tests for:

- every reducer transition and illegal action;
- authority for host, self, another player, and stale actor;
- deterministic setup/randomness from an agreed seed;
- public/private/host-only redaction sentinels;
- snapshot and wire-codec round trips, unknown fields/versions, and payload
  limits;
- duplicate, delayed, reordered, and replayed commands;
- disconnect, terminal state, reset, and rematch; and
- EN/AR resources plus the supported layout sizes.

The non-shipping fixture at
`shared/engine-testing/.../RoundRobinAnnounceGame.kt` proves the platform seam.
`GameShellRegistryExtensibilityTest` registers a third fixture binding and
exercises catalog discovery, supported entry modes, local start, snapshot
round-trip, local resume, and owned host/peer route restoration without a
central game branch or networking-core change:

```bash
./gradlew :composeApp:desktopTest
```

## Shell boundary

The app root is a registry-driven dispatcher. `App.kt`, `HomeScreen.kt`,
`LocalResumeRouter.kt`, and the shared multiplayer lobby helpers contain no
game ids or game-specific branches. Each binding owns its localized setup,
content picker, host/peer flow, and resume route; Whodunit's case picker and
start handshake live under `shell/game/whodunit`, while Mafia's equivalent
flows remain in the Mafia module. `composeApp:verifyGameShellDispatch` is a
release gate that scans the neutral root and shared multiplayer helpers for
game-specific tokens.

Cold-start resume is exposed only by a binding whose transport/session adapter
can validate the saved game id and version. A game without that adapter is
rejected by the router rather than routed into another game's UI.

## Definition of done

A new game is complete only when its module can be removed without editing
shared engine/session/networking/transport source, and re-added through module
inclusion plus its localized shell adapter and composition-root registration.
`productionDesktopCheck`,
`productionAndroidCheck`, and `productionAppleCheck` must all include it.
