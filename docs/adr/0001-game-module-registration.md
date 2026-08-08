# ADR-0001: Game modules register definitions and navigation

- Status: Accepted
- Date: 2026-07-28
- Owners: Parlor architecture

## Context

Parlor ships multiple games in one app. Lobby, admission, reconnect, command
ordering, snapshots, and P2pKit ownership must not be copied into every game or
grow a central game-specific dispatch statement.

## Decision

Each game is a sibling Gradle module and contributes two stable descriptors:

- `GameDefinition<State, Action, Event>` for metadata, modes, initialization,
  reducer, projections, and snapshots; and
- `ModuleNavGraph` for its shell entry route.

Both use the same stable `GameId`. The app composition root assembles the
installed set. Registries are immutable after startup and reject duplicate
IDs.

Game UI depends on `SessionController`. Game protocol adapters depend on the
transport-independent multiplayer contracts. Game modules never import
P2pKit. The host-authoritative session core remains generic and routes by the
protocol envelope's game ID and version.

## Consequences

- Adding a game is localized to a module, settings inclusion, and composition
  root registration.
- Shared networking changes only when the cross-game protocol itself changes.
- Games can have different reducers/codecs without unsafe casts in their
  domain/UI code.
- Removing a module removes all of its rules, screens, and assets.
- Duplicate IDs are startup/test failures rather than order-dependent routing.
- The composition root still has an explicit installed-games list. This is
  intentional visibility, not game-specific orchestration.

## Verification

`GameRegistryExtensibilityTest` registers an existing catalog sentinel and the
non-shipping round-robin fixture, resolves the second definition, and exercises
its reducer to completion. `NavGraphRegistryTest` proves lookup is descriptor
driven. Both tests pin duplicate-ID rejection.
