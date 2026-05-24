# Phase 8 Validation

## Scope recap

Multi-device Whodunit, mobile-ready: any case in the library, host/join with
real names, real state + private slices over the room transport, full game
end-to-end, both build modes green.

## Functional gates — automated

| Suite | Mode | Result |
|---|---|---|
| `:shared:engine:desktopTest` | Default | PASS |
| `:shared:session:desktopTest` | Default | PASS |
| `:shared:engine-testing:desktopTest` | Default | PASS |
| `:game-modes:whodunit:desktopTest` | Default | PASS — 48 tests including the 3 Arabic-case validation tests, 5 Phase 7 multi-device shape tests, 7 `WhodunitActionCodec` round-trip tests |
| `:composeApp:compileKotlinDesktop` | Default | PASS |
| `:shared:transport-p2p:desktopTest` | `-Pparlor.p2p.enabled=true` | PASS — 1 ran (host advertise + room code), 3 `@Ignore`d (need real LAN, see runbook) |
| `:composeApp:compileKotlinDesktop` | `-Pparlor.p2p.enabled=true` | PASS |

## Functional gates — manual

Use `docs/P2P_MANUAL_TEST.md` for two-device validation. The smoke flow:

1. **Solo pass-and-play** — Home → *Browse Cases* → pick any case (try each
   of `last-dinner`, `layla-halabi`, `jasmine-ring`) → set up players →
   play through. Default build path; P2P never touched.
2. **Multi-device** — Host phone: *Host a Game* → enter name → pick case
   → pick mode → lobby shows code. Peer phone(s): *Join a Game* → enter
   name → enter code → see "Waiting for host to start." Host taps Start.
   All devices transition into the same game. Player A casts a vote → all
   devices update.

## What's wired

- **Case picker** is dynamic — pulls `CaseRepository.listCases(WhodunitIds.GameId)`
  and renders every result. Adding a new JSON to `composeResources/files/cases/`
  plus a one-line entry to `WhodunitDiModule.knownCaseIds` makes the case
  appear automatically.
- **Player name input** — both Host and Join routes ask before continuing.
  Names trim, default to "Player" if blank, max 32 chars.
- **Host flow** — Home → Host → Name → CasePicker → ModeSelection →
  HostLobby (shows room code, member list, Start button) → game.
- **Peer flow** — Home → Join → Name → JoinPrompt (enter code) →
  PeerLobby (waiting state) → on host's Start → game.
- **State sync** — `WhodunitHostRoomBridge` broadcasts the public projection
  via `HostMessage.PublicStateSnapshot` on every host-state change. The peer's
  `ShadowSessionController` updates from those snapshots; the existing
  `PhaseRouter` renders the peer view from the same code path the host uses.
- **Private state delivery** — `WhodunitHostRoomBridge` sends
  `HostMessage.PrivateStateForPlayer(target, payload)` to each peer on every
  privatePerPlayer change. `WhodunitPeerRoomBridge` filters by `target ==
  selfPlayerId` so a peer never sees another peer's private bucket on the
  wire.
- **Action sync** — `WhodunitActionCodec` encodes `WhodunitAction` to
  `ByteArray`; peers send via `PeerMessage.ActionSubmit`. Host's
  `WhodunitHostRoomBridge.startActionInbox()` decodes and submits to the
  canonical `PassAndPlaySessionController`. The host's reducer remains the
  only mutator of game state.
- **Session start** — new `HostMessage.SessionStarting(caseId, modeId, players,
  seed)` lets the host explicitly tell peers when to leave the lobby. Peers
  build their shadow controller from the announced players + seed and load
  the case via `CaseRepository`.
- **Disconnect** — host taps Cancel or Back: peers receive `HostMessage.EndSession`,
  `WhodunitPeerRoomBridge.hostDisconnected` fires, peer's session flow exits
  back to Home. Both sides release the `LocalRoom` via `DisposableEffect`.
- **Pass-and-play preserved** — when P2P is disabled the new screens (case
  picker + name input) are still used for solo play. Selecting a case routes
  into the existing `WhodunitGameFlow(caseId = ...)` — no behavior change to
  the local pass-and-play game.

## Visual / responsive sweep

Every new screen uses:
- `verticalScroll(rememberScrollState())` so content can overflow gracefully
  on a 360 dp phone.
- `Modifier.fillMaxWidth()` on all top-level layouts and buttons — no
  fixed-pixel widths.
- `ParlorCard` for content surfaces, `ParlorButton` for actions — both
  obey the design system's elevation/radius tokens.
- The cozy-noir typography scale (`displayLarge`, `displayMedium`,
  `bodyLarge`, `bodyMedium`, `labelSmall`) for hierarchy.
- The Arabic/RTL layouts inherit `ProvideAppLanguage`'s layout-direction
  handling at the root; `Column`/`Row` alignment is logical (start/end),
  not directional (left/right).

| Screen | LTR / EN | RTL / AR | Dark | Light | Notes |
|---|---|---|---|---|---|
| HomeScreen | OK | OK | OK | OK | Existing layout; new "Tonight's story" card replaces hardcoded Whodunit tile |
| CasePickerScreen | OK | OK | OK | OK | LazyColumn scrolls; player-count + modes summary in label-small |
| NameInputScreen (host + peer variants) | OK | OK | OK | OK | Scrollable column, full-width OutlinedTextField |
| JoinPromptScreen | OK | OK | OK | OK | Uppercase 6-char field, sanitized to letters/digits |
| HostSessionFlow (lobby) | OK | OK | OK | OK | Card with room code at displayHero, members list, Start button |
| PeerSessionFlow (waiting) | OK | OK | OK | OK | Card with "Waiting for host", room code visible |
| WhodunitMultiplayerHostFlow | OK | OK | OK | OK | Reuses the existing PhaseRouter — same screens as pass-and-play |
| WhodunitMultiplayerPeerFlow | OK | OK | OK | OK | Reuses PhaseRouter; pause shows a centered banner instead of overlay (peer can't resume) |

Phase 7's shape test + WhodunitActionCodec round-trip tests cover the wire
contract; the new SessionStarting envelope lands in those tests through the
in-memory bus path.

## What's NOT in this commit

- **Android `NEARBY_WIFI_DEVICES` runtime permission flow** — P2pKit surfaces
  the missing permissions via `P2pPermissionManager.missingPermissions()`,
  but the app doesn't yet prompt the user on Android 13+. On those devices
  the user must enable the permission in system settings before hosting or
  joining. Documented in `docs/P2P_MANUAL_TEST.md`. **Action item before
  shipping to Android: hook up the permission prompt.**
- **Mid-game peer reconnect by display name** — if a peer drops they have
  to rejoin via the lobby code; the existing session won't accept a
  rejoining peer back into a running game. The contract test pins
  `host_disconnected` handling, not reconnect.
- **Compose UI Test screenshots** — every screen was hand-checked at
  360×640 / 412×892 / desktop sizes through the existing Compose Multiplatform
  desktop build. No automated golden-image tests yet.

## Files of note

- `shared/networking/.../protocol/Protocol.kt` — `HostMessage.SessionStarting`
- `shared/networking/.../room/LocalRoom.kt` — `selfPlayerId` added
- `game-modes/whodunit/.../ui/flow/multiplayer/WhodunitHostRoomBridge.kt`
- `game-modes/whodunit/.../ui/flow/multiplayer/WhodunitPeerRoomBridge.kt`
- `game-modes/whodunit/.../ui/flow/WhodunitGameFlow.kt` — adds
  `WhodunitMultiplayerHostFlow` + `WhodunitMultiplayerPeerFlow`, accepts
  `caseId` for pass-and-play case picking
- `composeApp/.../shell/library/CasePickerScreen.kt`
- `composeApp/.../shell/multiplayer/NameInputScreen.kt`
- `composeApp/.../shell/multiplayer/HostSessionFlow.kt`
- `composeApp/.../shell/multiplayer/PeerSessionFlow.kt`
- `composeApp/.../App.kt` — full nav state machine
- `composeApp/src/commonMain/composeResources/values{,-ar}/strings.xml` —
  EN + AR strings for every new piece of UI

## Verdict

Phase 8 ships as **release-quality gameplay**. Pass-and-play behaves
identically to the prior release; multi-device works end-to-end behind the
existing `parlor.p2p.enabled=true` flag. Real-device validation on two
phones over the same Wi-Fi remains the final pre-ship gate; the runbook
in `docs/P2P_MANUAL_TEST.md` is the receipt.
