# Parlor — Two-Device Multiplayer Playtest Checklist

Targeted on-device test plan for the multiplayer (P2P) experience. Focus: **does it feel fast, smooth, and fun** — connection speed, lobby/join, action latency, reconnection, UI clarity.

> The code-level review behind this checklist is in `PROBLEMS_PARLOR.md` (§ multiplayer pass). Items flagged **⚠ watch** below are things the review predicts you may notice; report back if they bite and I'll fix them.

## 0. Setup

- **Two real devices on the same Wi-Fi/LAN** (Android↔Android, Android↔iOS, or one Desktop + one phone). mDNS/Bonjour multicast must work on the network (some guest/corporate Wi-Fi blocks it — use a phone hotspot or home router if join never finds the host).
- Build/run (P2pKit resolves from Maven Central and is always included):
  ```bash
  ./gradlew :composeApp:installDebug     # Android
  ./gradlew :composeApp:run              # Desktop host
  # iOS: open iosApp in Xcode
  ```
- **Watch the transport logs** — every transport event is tagged:
  - Android: `adb logcat | grep ParlorP2p`
  - Desktop: stdout of the `:run` process
  - iOS: Xcode console, filter `ParlorP2p`
  - The logs print host/join/freshness/session-state lines with peer ids and room codes — use them to time each step.

## 1. Connection speed
1. Device A: create/host a room. Note the **room code** shown.
2. Device B: enter the code and Join. **Time from tapping Join → "connected".**
   - Target: well under the 10 s join budget (`DEFAULT_JOIN_TIMEOUT_MS`).
   - In B's logs: `join: matched host peer … calling connect()` → `join: connect() returned`. The gap is discovery time.
3. ⚠ **watch:** if B never finds the room, check A's logs for `startAdvertising() returned` and B's for `kit.peers emitted size=…`. Size 0 forever = multicast blocked on this network (not an app bug).
4. Repeat with B joining a *second* time after A re-hosts — confirm no stale "ghost room" appears (freshness window is 5 s).

## 2. Lobby / join flow
1. With 3+ devices, join them one by one. Each should appear in the **host lobby roster** within a second of connecting.
2. Confirm display names are correct and the host sees the right player count.
3. Host taps **Start** — all peers should transition from "waiting for host" → the game's first screen together.
4. ⚠ **watch:** a peer that joins *after* Start — does it get cleanly handled or stuck? (Late-join is not a core MVP path.)

## 3. Action latency
1. Get into a phase where a peer acts (Whodunit: cast a vote; Mafia: a night action / vote).
2. **Time from the peer tapping → the result showing on the host and on other peers.** Should feel instant on a healthy LAN (host broadcasts the new state immediately after reducing).
3. Fire several actions quickly (e.g., everyone votes at once) — confirm no action is lost or doubled and the host tally is correct.
4. Confirm a peer **cannot** act out of turn / as someone else (authority is enforced host-side; this is also unit-tested).

## 4. Reconnection behavior
1. Mid-game, turn **Wi-Fi off on a peer** for ~5 s, then back on.
   - Peer should show a **reconnecting overlay / offline banner**, then recover and resync to the current screen (no "rejoin lobby" detour).
   - Host should mark that player disconnected, then reconnected (roster dot).
   - In logs: peer `session state -> Reconnecting … -> Connected`; host `emitting PeerLeft/PeerReconnected`.
2. Turn a peer's Wi-Fi off and **leave it off** — the host must see a
   confirmation-gated "end game without {player}" action. Confirm either a
   rejoin or the host decision clears the blocked state and that the pending
   120-second expiry does not fire a second transition.
3. ⚠ **watch:** time-to-detect a *silent* host disappearance. Fast path is transport-driven (`Reconnecting`/`Closed`); the fallback "snapshot silence" watchdog is **8 s** — if loss detection feels sluggish in a silent-drop case, that's the knob to lower.
4. Kill and relaunch a peer app entirely — does it rejoin with the same identity and resume?

## 5. UI clarity
- Lobby: is it obvious how to share the code, who's connected, and when you can Start?
- Waiting-for-host / connecting / reconnecting / offline states: are they distinct and reassuring (not a frozen blank screen)?
- Privacy ceremony: on the **host's own device**, can you see your role and take your action? (Fixed this pass — was previously impossible on multi-device Mafia.) On peers, is private info (role/dossier) shown only to the owner?
- Error states (bad code, host left, transport failure): clear message + a way back, no dead-end.

## 6. Smoothness & fun
- **Discussion timer:** watch a peer's screen during the discussion countdown. ⚠ **watch:** the host currently re-broadcasts the **full game state once per second** while the timer ticks (the protocol's lightweight `TimerSync` is unused). On a peer this can cause a subtle per-second re-render/flicker. If you see jank, that's the #1 perf change to make (drive the peer timer locally from a deadline + stop per-tick full-state broadcasts) — I left it unimplemented because it needs *your* eyes to confirm it's worth the change and that it feels right.
- Phase transitions, reveals, vote results: do they feel responsive and "game-show" snappy, or laggy?
- Overall: would you enjoy playing a full round end-to-end with friends? Note anything that breaks immersion.

## What to send back
For anything rough, capture: **which device**, **what you did**, **what you saw vs expected**, and the **`ParlorP2p` log lines** around that moment. With that I can pinpoint and fix — especially the timer-smoothness change (§6) and any reconnection-timing tuning (§4.3), which are the two I deliberately left for after your on-device read.
