# Last Light: Standard table integration

Last Light is Parlor's card-bluffing game for 2–6 players. The `last-light`
game module adapts PartyDeck's Standard Compose table and rules to Parlor's
existing local and host-authoritative LAN sessions.

## Source and scope

The reference is PartyDeck's `docs/STANDARD-TABLE-PORTING-GUIDE.md`, which
describes commit `6b6acdf29779347404020ccd3ba9ce0c687e6faa`. The inspected local
source was `df649e6c896203bdf93130f6497e229757d5da30`. Source files and
`docs/game-rules.md` were checked alongside the guide before implementation.

This port includes the Standard table, card vectors, typography, selection and
winner animations, sound cues, and haptics. Godot presentations, PartyDeck's
application controller, transport, invitation formats, and practice bots are
outside this integration. Parlor's pinned toolchain remains authoritative.
Copied font licenses ship under the game module's `files/licenses/` resources.

## How the two applications map

| PartyDeck responsibility | Parlor integration |
|---|---|
| `LastLightEngine`, rules and canonical state | Pure game reducer, `LastLightDefinition`, and public/private/host-only state buckets |
| `GameView` | Recipient view derived from a Parlor projection; never a canonical-state argument to the table |
| Controller action callbacks | `SessionController`, pending-command gate, local controller or authoritative room bridge |
| Standard table and theme | Game-local Compose UI, EN/AR resources, scoped typography and effective reduced-motion preference |
| Match setup | Parlor catalog binding, pass-and-play roster, or existing room admission and start barrier |
| Session transport | Existing Parlor protocol 4.2, host/peer coordinators and P2pKit adapter |
| App interruption/privacy handling | App-owned visibility and concealment epoch supplied through the game binding |
| Accepted-view feedback | Game-local feedback service; initial snapshots and reconnection do not replay cues |
| Local recovery | Versioned game codec, strict recovery validation, and protected `SnapshotStore` |

Registration consists of module inclusion, app dependency/DI, and
`LastLightGameShellBinding`. Shared engine, session, networking and transport
runtime source do not branch on this game. The stable protocol/persistence identity is
`last-light`, mode `standard`, game version `1`.

## Rules preserved

- Each survivor receives five cards from a fresh 30-card deck: nine Crown,
  nine Moon, nine Star, and three Wild. A round claims one non-Wild rank.
- On your turn, play one to three distinct owned cards face down, or challenge
  the latest claim. Wild matches every table rank. A challenge reveals only
  the challenged cards; one mismatch makes the whole claim a bluff.
- A truthful claim penalizes the challenger; a bluff penalizes the claimant.
  Each player has one secret, fixed burnout step from one to six. Reaching it
  eliminates that player. A challenge always ends the round.
- Turns skip eliminated players and empty hands. Emptying a hand does not win.
  The last player holding cards must challenge. The last survivor wins.
- The host advances rounds even if their own player is eliminated. Openers
  rotate clockwise among survivors; penalty counts persist across rounds.

## Privacy, authority and recovery

Only the host reduces a LAN match. Commands retain authenticated actor identity,
expected revision and duplicate handling. UI selection never removes cards
optimistically, and rejected non-idempotent actions are not automatically retried.

Public views contain hand counts and claim counts. Only the recipient gets their
own hand. Unchallenged card ranks, other hands, undealt cards, burnout positions,
and randomness remain host-only. Concealed hands remove private semantics as
well as card faces. Viewer changes, interruption epochs and session changes
invalidate reveal/selection state.

Local saves retain the secret randomness needed for deterministic continuation.
Recovery checks game/version/session metadata and reducer-reachable state.
An unreadable save remains available for Retry, Back or explicit Discard.
Parlor's 32-character display-name policy is preserved when adapting PartyDeck's
original 24-character limit.

A missing surviving LAN player pauses play, including a player with an empty
hand. Rejoin remains tied to the same host and seat. Expiry or the host's explicit
decision ends the interrupted match; it does not remove hidden cards and invent
a new turn. Eliminated participants cannot take turns, while an eliminated host
retains session controls.

LAN rematch opens a fresh host lobby and room code after closing the old
session. Peers rejoin that room; commands from the completed session cannot
reach the new match. Local rematch returns to roster setup with a fresh
session identifier and seed.

## Verification

Run the focused suite first, then the repository gates:

```bash
./gradlew :game-modes:last-light:desktopTest :composeApp:desktopTest \
  :composeApp:verifyGameShellDispatch --dependency-verification=strict
./gradlew productionCheck allTests --dependency-verification=strict
# macOS:
./gradlew productionAppleCheck productionIosSimulatorRuntimeTests \
  --dependency-verification=strict
```

Coverage must include complete seeded matches, illegal actions, actor authority,
redaction, strict codecs/recovery, duplicate/stale/reordered commands, reconnect,
terminal cleanup and rematch. UI checks cover conceal/reveal, selection limits,
forced challenge, EN/AR, large text, narrow phones, landscape and tablet layouts.
Native audio/haptics, device LAN and platform interruption behavior also require
Android/iOS runtime checks. Earlier Parlor or PartyDeck receipts do not certify
this integration; report only checks executed against the changed source.
