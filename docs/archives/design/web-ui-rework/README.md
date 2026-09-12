# Parlor UI Rework Concept

Archived on 2026-09-12 for design reference. This is not a shipping
implementation or a current product/release contract.

This folder is an isolated, dependency-free web prototype for a possible
Parlor UI refresh. It does **not** replace the Compose UI or implement game or
network behavior.

## Product model used

The concept used the following product model when it was created:

- The home screen is a game-first library with local-save and multiplayer
  recovery entry points.
- Whodunit has seven bundled English/Arabic stories. Shipping play requires
  six players and supports Classic Vote and Elimination. Its flow includes
  public intro, rules, private dossiers, clue/discussion rounds, voting,
  reveal, and replay.
- Mafia supports 5–16 players. Its flow includes role setup, private role
  reveal, role-specific night actions, dawn, discussion, public voting, and
  final role reveal. Timed Mafia rounds are intentionally not presented.
- Available topologies are Pass & Play, Host a Room, and Join a Room. Rooms
  use six-character codes and same-LAN discovery; there is no internet
  matchmaking, manual IP join, spectator mode, or host migration.
- In multiplayer the host is authoritative. Peers submit actions and wait for
  confirmation; UI wording never implies that a peer applied canonical state.
- Private dossiers, roles, night choices, and detective results stay behind a
  hand-off/privacy gate. Lobby and game screens distinguish public, host-only,
  and player-private moments.
- The concept retains English/Arabic directionality, light/dark appearance,
  reduced motion, recovery, disconnect, and explicit destructive decisions.

## Design direction: “The Table Is the Interface”

The visual system combines a quiet editorial shell with game-specific stage
lighting. Warm amber identifies Whodunit, red identifies Mafia, and green is
reserved for connection/readiness. A persistent context ribbon answers three
questions at a glance: **whose screen is this, is it private, and who may
advance the game?**

The proposal also reduces setup density. Unsupported Solo play is explained in
a compact note rather than promoted as a full disabled card. Pass & Play and
same-Wi-Fi choices are grouped by the number of devices, while Host and Join
remain separate actions.

## Run the prototype

From the repository root:

```bash
python3 -m http.server 4173 --bind 127.0.0.1
```

Open <http://127.0.0.1:4173/docs/archives/design/web-ui-rework/>.
No install or network dependency is required. Use the left scenario rail or buttons inside the phone
to navigate. The appearance, RTL, and reduced-motion controls are functional.
Individual screens can be linked directly, for example
`?screen=mafia` or `?screen=host&game=mafia`.

## Files

- `index.html` — semantic prototype and SVG icon definitions.
- `styles.css` — responsive concept tokens, components, and phone/studio layout.
- `app.js` — local-only scenario state and interactions.

## Implementation boundary

This prototype is a discussion artifact, not a source of game truth. A Compose
implementation should reuse existing `GameShellBinding`, `SessionController`,
projection, snapshot, and protocol boundaries. Visual changes must not move
reducers into UI code, expose host-only data, invent optimistic peer updates,
or change protocol 4.2.
