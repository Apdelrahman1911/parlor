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
├── <Game>Definition.kt
└── <Game>NavGraph.kt
```

The domain package remains pure Kotlin. It may depend on `:shared:core` and
`:shared:engine`, but never Compose, Koin, storage, networking, or P2pKit.

## Registration contract

1. Implement `GameDefinition<State, Action, Event>`.
2. Give the game a stable kebab-case `GameId`. IDs are protocol and persistence
   identifiers; renaming one is a migration, not copy editing.
3. Declare metadata, modes, and supported player ranges in the definition.
   The shared engine/session layers consume these values; the current
   composition root still owns the user-facing game cards and route adapters,
   so those shell adapters must be added explicitly until the planned
   descriptor-driven shell is completed.
4. Implement a pure reducer, a projection policy for public/private/host-only
   state, and a versioned snapshot codec.
5. Implement `ModuleNavGraph` with the same `GameId`.
6. Export one Koin module containing the definition, graph, and game-local
   dependencies.
7. Add that Koin module and its two registry contributions at the app
   composition root. Add the small game-specific shell adapter (home card,
   setup/lobby route, and any game-specific resume entry) in the same change.
   A composition-root list and localized route adapter are acceptable; adding
   a game-specific `when` to lobby, transport, protocol routing, or shared
   session code is not.

`DefaultGameRegistry` and `DefaultNavGraphRegistry` fail fast on duplicate game
IDs. A duplicate must therefore fail at startup or in tests rather than
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
Its acceptance test registers a second definition, resolves it, and drives it
to completion without importing or changing the networking core:

```bash
./gradlew :shared:engine-testing:desktopTest
```

## Current shell limitation

The networking, session, registry, and game-domain seams are extensible, but
the shipping shell still has explicit Whodunit and Mafia route adapters in
`composeApp/.../App.kt`, `HomeScreen.kt`, and the shared Whodunit case/setup
screens. A new game therefore requires those localized shell edits today.
This is a known architectural follow-up, not a reason to duplicate lobby or
transport code. Cold-start resume is currently implemented only for Whodunit;
Mafia and future games must add a game-aware snapshot/resume adapter before
they can expose a Continue tile.

## Definition of done

A new game is complete only when its module can be removed without editing
shared engine/session/networking/transport source, and re-added through module
inclusion plus its localized shell adapter and composition-root registration.
`productionDesktopCheck`,
`productionAndroidCheck`, and `productionAppleCheck` must all include it.
