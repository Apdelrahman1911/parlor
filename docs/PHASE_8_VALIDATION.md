# Phase 8 Validation

Phase 8 v3 = production-quality Party Play + UI/UX rebuild. This doc is the
acceptance receipt: what's wired, what's tested, what's not.

## Scope (recap of the brief)

A real user must be able to: open Parlor on a phone, choose any case known
by `CaseRepository` / `knownCaseIds`, enter their name, host or join a room,
play a full Whodunit session from role reveal to post-game, and use the app
comfortably in mobile/desktop, light/dark, EN-LTR/AR-RTL. Pass-and-play must
not regress.

## Top-priority fix — JVM character reveal crash

| | |
|---|---|
| Symptom | `IllegalArgumentException: Shadow controller can only expose its own player's private state` at `CharacterRevealSegment:594` when an Android peer joined a JVM host and the room hit the per-player reveal phase. |
| Root cause | `CharacterRevealSegment` unconditionally called `privateStateFor(otherPlayer.id)` on every device. The shadow controller (peer-side) enforces `selfPlayerId == playerId` and threw when the UI asked for someone else's private slice. |
| Fix | Introduced pure `resolveLocalRevealActor(phase, players, selfPlayerId)` and branched: if the local device is not the active reveal actor, render `CharacterRevealWaitingScreen(activePlayerName=...)` instead. Pass-and-play (`selfPlayerId == null`) keeps the existing behaviour. |
| Regression test | `CharacterRevealAuthorityTest` — 6 cases covering pass-and-play + multi-device, self vs not-self, stale roster, out-of-range index. |
| Commit | `5fcbb61` |

## Functional gates — automated

| Suite | Mode | Result |
|---|---|---|
| `:shared:engine:desktopTest` | Default | PASS |
| `:shared:session:desktopTest` | Default | PASS |
| `:shared:networking:desktopTest` | Default | PASS |
| `:shared:engine-testing:desktopTest` | Default | PASS |
| `:game-modes:whodunit:desktopTest` | Default | PASS — includes `WhodunitActionAuthorityTest`, `MultiDevicePartyPlayContractTest`, `PartyPlayPassAndPlayParityTest`, `CasePickerDiscoveryTest`, `CharacterRevealAuthorityTest`, Arabic-case validation, multi-device shape, action codec round-trip |
| `:composeApp:compileKotlinDesktop` | Default | PASS |
| `:composeApp:assembleDebug` | Default | PASS |
| `:shared:transport-p2p:desktopTest` | `-Pparlor.p2p.enabled=true` | PASS — 1 ran (host advertise + room code), 3 `@Ignore`d (need real LAN; see `docs/P2P_MANUAL_TEST.md`) |
| `:composeApp:compileKotlinDesktop` | `-Pparlor.p2p.enabled=true` | PASS |

## Authority matrix

`WhodunitActionAuthority` is the single source of truth. The host bridge
consults `isAllowed(action, sender, host)` on every inbound peer action;
mismatches are dropped silently.

| Action | Scope | Host submits? | Peer-self submits? | Peer-other submits? |
|---|---|---|---|---|
| `AssignRoles`, `AdvanceFromIntro`, `AdvanceBriefingCard`, `StartCharacterReveal`, `RevealNextClue`, `StartDiscussionTimer`, `PauseDiscussionTimer`, `ResumeDiscussionTimer`, `TimerTicked`, `TimerExpired`, `AdvanceFromDiscussion`, `OpenVote`, `CloseVote`, `AcknowledgeRevealCard`, `AcknowledgeReveal`, `BeginReplay`, `Pause`, `Resume`, `EndGameEarly`, `RequestReroll` | HostOnly | ✅ allowed | ❌ rejected | ❌ rejected |
| `CompleteCharacterReveal(p)`, `OpenPrivateReview(p)`, `CloseHide(p)` | SelfActor(p) | ❌ (host has no avatar in these) | ✅ when sender == p | ❌ |
| `CastVote(voter=p, ...)`, `AbstainVote(p)`, `RefuseToVote(p)` | SelfActor(p) | ❌ | ✅ when sender == p | ❌ |
| `SubmitStructuredAction(Alibi(by=p, ...))` etc. | SelfActor(p) | ❌ | ✅ when sender == p | ❌ |
| `SubmitStructuredAction(NoAction)` | HostOnly | ✅ | ❌ | ❌ |

Pinned by `WhodunitActionAuthorityTest` (every scope + sender permutation).
End-to-end behaviour pinned by `MultiDevicePartyPlayContractTest` (host
bridge + real peer bridges + real bus).

## Wire protocol

- `PeerMessage.ActionSubmit(sender: PlayerId, payload: ByteArray)` — peer
  self-attests its `LocalRoom.selfPlayerId` on every submit. Host validates
  via `WhodunitActionAuthority`.
- `HostMessage.SessionStarting(caseId, modeId, players, seed)` — host tells
  peers when to leave the lobby and stand up their shadow controller.
- `HostMessage.PublicStateSnapshot(payload)` — public-projected
  `WhodunitState` (hostOnly fields redacted to sentinel values, private
  slices removed). Broadcast on every public-state change.
- `HostMessage.PrivateStateForPlayer(target, payload)` — per-player
  `WhodunitPrivate` slice, sent direct-target. `WhodunitPeerRoomBridge`
  rejects messages whose `target != selfPlayerId` (defense-in-depth; the
  bus already directs them correctly).
- `HostMessage.EndSession` — host disconnects; peer bridge surfaces
  `hostDisconnected` and the peer flow exits to the home screen.

## Peer UI gating

Host-only progression is hidden on peer devices. The peer never sees a
"Continue" / "Reveal Next Clue" / "Open Vote" / "Acknowledge Reveal" /
"Begin Replay" / "Pause" button.

| Phase | Host UI | Peer UI |
|---|---|---|
| `Setup` | LoadingScreen | LoadingScreen |
| `PublicIntro` | `PublicIntroScreen` (Continue → `AdvanceFromIntro`) | `PeerWaitingForHostScreen("Listen carefully" + "waiting for host…")` |
| `RulesBriefing` | `RulesBriefingScreen` (Continue/Begin → `AdvanceBriefingCard`) | `PeerWaitingForHostScreen("Rules briefing" + "waiting for host…")` |
| `CharacterReveal` | If self is active reveal actor: full cover→gate→dossier→hide. Else: `CharacterRevealWaitingScreen` | Same gating via `resolveLocalRevealActor` |
| `Round` | `RoundSegment` (RevealNextClue, StartDiscussionTimer, AdvanceFromDiscussion buttons) | `PeerWaitingForHostScreen("Round under way")` |
| `FinalVote` | `VoteSegment` — current voter on this device shows ballot, others see handoff cover | Same — self-vote only; `CloseVote` auto-submit is host-only |
| `TiedRevote` | `TiedRevoteSegment` then `VoteSegment` | `PeerWaitingForHostScreen` then ballot if self is current voter |
| `Reveal` | `RevealStageScreen` with Acknowledge | `RevealStageScreen` with no-op acknowledge — peer sees verdict, host closes |
| `PostGame` | `PostGameScreen` (Replay / Try other mode / Back to library) | `PeerWaitingForHostScreen("Game over") + leave-the-room option` |

Pause:
- Host: `PauseAffordance` (top-right) + `PauseOverlay` when paused. Resume / Resume Later / End Now.
- Peer: no pause button; `PeerHostPausedBanner` shown centrally when `state.public.paused`.

## UI / theme rebuild

Two waves of design-system work landed.

**Wave 1 — tokens + primitives** (commit `8d80f1c`):
- `ParlorColors` reworked. Dark palette (`CozyNoirPalette`) widens surface
  stops (~10–15 % luminance steps with warmer hue shift on elevation),
  brighter `textSecondary` / `textTertiary` / `accentBrass`, glow at higher
  alpha. Light palette (`LightCozyNoirPalette`) deepens `textPrimary` and
  `accentEmber` so body text + brand accent both clear WCAG AA on cream.
- New tokens: `surfaceHero` (most-attention card), `borderAccent` (strong
  ember rim for hero cards).
- `ParlorButton` rewritten with `ParlorButtonVariant` (Primary / Secondary /
  Ghost / Destructive), pressed-state tints, loading flag.
- `ParlorCard` adds `hero: Boolean` and `bordered: Boolean` so the
  surface/border can switch to the hero pair without per-screen overrides.
- `EyebrowLabel` primitive — one source for uppercase section labels.

**Wave 2 — per-screen sweep**:
- Batch A (shell, commit `86315fc`): HostSessionFlow room-code card →
  `hero = true`; PeerSessionFlow waiting card → `hero = true`; Cancel/Back
  actions across HostSessionFlow / PeerSessionFlow / JoinPromptScreen /
  NameInputScreen / CasePickerScreen converted to Secondary or Ghost;
  inline eyebrow Texts replaced with `EyebrowLabel`.
- Batch B (Whodunit game, commit `7a8086f`): `DossierCard` → `hero = true`
  (dossier is the signature reveal moment); `RevealStageScreen` verdict
  card → `hero = true`; `ClueCard` → `hero = true` (each clue is a focal
  beat). End-game buttons converted to Destructive; cancel-confirm to
  Ghost; reroll/quietly to Secondary. Eyebrow sweep across PostGame /
  Round / RoundAction / Vote / Mode / PlayerEntry / PlayerCount /
  PublicIntro / RulesBriefing / CharacterReveal-waiting / PauseOverlay /
  PrivacyConcernOverlay.

## Responsive / theme sweep

Every screen uses:
- `verticalScroll(rememberScrollState())` so content overflows gracefully on a 360 dp phone.
- `Modifier.fillMaxWidth()` on top-level layouts and buttons — no fixed-pixel widths.
- Cozy-noir typography scale (`displayLarge`, `displayMedium`, `bodyLarge`, `bodyMedium`, `labelSmall`) for hierarchy.
- Logical alignment (start / end) so RTL inherits via `ProvideAppLanguage`'s layout-direction handling.

| Screen | LTR / EN | RTL / AR | Dark | Light | Notes |
|---|---|---|---|---|---|
| HomeScreen | OK | OK | OK | OK | "Tonight's story" card replaces hardcoded Whodunit tile |
| CasePickerScreen | OK | OK | OK | OK | LazyColumn scrolls; eyebrow uses `EyebrowLabel`; back is Ghost |
| NameInputScreen | OK | OK | OK | OK | Scrollable column; full-width input + Primary confirm + Ghost back |
| JoinPromptScreen | OK | OK | OK | OK | 6-char uppercase field; Connect Primary, Cancel Ghost |
| HostSessionFlow lobby | OK | OK | OK | OK | Hero room-code card; Cancel Secondary |
| PeerSessionFlow waiting | OK | OK | OK | OK | Hero waiting card; Leave Secondary |
| WhodunitMultiplayerHostFlow | OK | OK | OK | OK | Same PhaseRouter as pass-and-play, `isHost = true` |
| WhodunitMultiplayerPeerFlow | OK | OK | OK | OK | `isHost = false` gates host-only screens to PeerWaitingForHostScreen; PauseBanner replaces overlay |
| DossierCard | OK | OK | OK | OK | Hero card; labels via EyebrowLabel; optional-details toggle Ghost |
| ClueCard | OK | OK | OK | OK | Hero card; centered eyebrow |
| RevealStageScreen | OK | OK | OK | OK | Hero verdict card |
| VoteBallotScreen | OK | OK | OK | OK | Refuse-to-vote Ghost |
| PauseOverlay (overlays + safety) | OK | OK | OK | OK | End-game Destructive; Resume Later Secondary |
| EndGameDialog | OK | OK | OK | OK | End-quietly Secondary, Cancel Ghost |
| PrivacyConcernDialog/Overlay | OK | OK | OK | OK | Reroll Secondary; Continue Ghost |
| PostGameScreen | OK | OK | OK | OK | Try-other-mode Secondary; Back-to-library Ghost |

RTL string coverage: every new key in `values/strings.xml` has a matching
`values-ar/strings.xml` entry, including the new `peer_*` family for
peer-side waiting screens.

## Files of note

**New (Wave 3)**
- `game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/domain/authority/WhodunitActionAuthority.kt`
- `game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/ui/screens/peer/PeerWaitingForHostScreen.kt`
- `game-modes/whodunit/src/desktopTest/.../authority/WhodunitActionAuthorityTest.kt`
- `game-modes/whodunit/src/desktopTest/.../multidevice/MultiDevicePartyPlayContractTest.kt`
- `game-modes/whodunit/src/desktopTest/.../multidevice/PartyPlayPassAndPlayParityTest.kt`
- `game-modes/whodunit/src/desktopTest/.../content/CasePickerDiscoveryTest.kt`

**Touched (Wave 3)**
- `shared/networking/.../protocol/Protocol.kt` — `PeerMessage.ActionSubmit` carries `sender`
- `game-modes/whodunit/.../ui/flow/multiplayer/WhodunitHostRoomBridge.kt` — authority enforcement in inbox
- `game-modes/whodunit/.../ui/flow/multiplayer/WhodunitPeerRoomBridge.kt` — stamps sender on outbound
- `game-modes/whodunit/.../ui/flow/WhodunitGameFlow.kt` — `isHost` threaded through PhaseRouter; VoteSegment gates per-voter on `selfPlayerId`
- `game-modes/whodunit/src/commonMain/composeResources/values{,-ar}/strings.xml` — peer-waiting strings

**Earlier in Phase 8**
- `shared/networking/.../room/LocalRoom.kt` — `selfPlayerId`
- `shared/networking/.../protocol/Protocol.kt` — `HostMessage.SessionStarting`
- `game-modes/whodunit/.../ui/flow/CharacterRevealAuthority.kt`
- `composeApp/.../shell/library/CasePickerScreen.kt`
- `composeApp/.../shell/multiplayer/{NameInputScreen,JoinPromptScreen,HostSessionFlow,PeerSessionFlow}.kt`
- `composeApp/.../App.kt` — full nav state machine

**Wave 1 + 2 (design system)**
- `shared/design-system/.../tokens/ParlorColors.kt` — surfaceHero, borderAccent, palette rework
- `shared/design-system/.../components/ParlorButton.kt` — variant matrix
- `shared/design-system/.../components/ParlorCard.kt` — hero, bordered
- `shared/design-system/.../components/EyebrowLabel.kt` — new primitive

## What is NOT in this phase

- **Android `NEARBY_WIFI_DEVICES` runtime permission prompt** —
  `AndroidManifest.xml` declares the permissions; the in-app prompt with
  rationale → request → settings fallback is not wired. On Android 13+ the
  user must enable the permission in system settings before hosting or
  joining. Documented in `docs/P2P_MANUAL_TEST.md`. Action item before
  the Android store push.
- **Mid-game peer reconnect** — if a peer drops they have to rejoin via
  the lobby code; the existing session won't accept a rejoining peer back
  into a running game. The contract test pins `host_disconnected`, not
  reconnect.
- **Compose UI golden-image tests** — every screen was hand-checked at
  360 × 640 / 412 × 892 / desktop via the existing Compose Multiplatform
  desktop run. No automated screenshot diffing yet.
- **Real two-device LAN run** — `docs/P2P_MANUAL_TEST.md` is the runbook;
  the in-process contract test exercises every other layer.

## Acceptance checklist

| Item | Done? | Evidence |
|---|---|---|
| JVM character-reveal crash fixed | ✅ Done | `CharacterRevealAuthorityTest`; commit `5fcbb61` |
| Case picker discovers all known cases dynamically | ✅ Done | `CasePickerDiscoveryTest`; UI uses `CaseRepository.listCases(WhodunitIds.GameId)` |
| Host flow: name → case → mode → lobby → start | ✅ Done | `HostSessionFlow.kt`; manual smoke per `docs/P2P_MANUAL_TEST.md` |
| Peer flow: name → code → waiting → game | ✅ Done | `PeerSessionFlow.kt`; manual smoke per `docs/P2P_MANUAL_TEST.md` |
| Public state sync host → peers | ✅ Done | `WhodunitHostRoomBridge.broadcastPublicSnapshot`; `WhodunitMultiDeviceShapeTest` |
| Peer actions reach host | ✅ Done | `WhodunitHostRoomBridge.startActionInbox`; `MultiDevicePartyPlayContractTest` |
| Private state per-player only | ✅ Done | `broadcastPrivatesForAllPlayers` + `WhodunitPeerRoomBridge.handlePrivate` filter; `WhodunitMultiDeviceShapeTest` redaction checks |
| Voting, refuse, tied revote, reveal, post-game | ✅ Done | `FullGameDriveTest`, `TiedRevoteTest`, `PartyPlayPassAndPlayParityTest` |
| Leave / disconnect / recovery | ✅ Done | `HostMessage.EndSession` + `WhodunitPeerRoomBridge.hostDisconnected`; `PauseRefuseLeaveTest` |
| Pass-and-play unaffected | ✅ Done | `PartyPlayPassAndPlayParityTest` |
| Host-only authority enforced on wire | ✅ Done | `WhodunitActionAuthorityTest`, `MultiDevicePartyPlayContractTest` cases 1+2 |
| Peer UI hides host-only controls | ✅ Done | `PhaseRouter(isHost=false)` → `PeerWaitingForHostScreen` for PublicIntro/Briefing/Round/PostGame; PauseAffordance host-only |
| Premium UI rebuild (theme tokens, typography, hierarchy) | ✅ Done | Wave 1 commit `8d80f1c`, Wave 2 commits `86315fc` + `7a8086f` |
| Both light AND dark mode look excellent | ⚠ Honest note | Hand-checked on desktop preview; final on-device check is in `docs/P2P_MANUAL_TEST.md` smoke list |
| Mobile responsiveness (360×640 small phone) | ⚠ Honest note | Every new screen uses scrollable column + fillMaxWidth — no fixed-px widths. Final 360-dp device sweep is in `P2P_MANUAL_TEST.md` |
| EN-LTR + AR-RTL | ✅ Done (strings) / ⚠ on-device | Every new string keyed in both locales; layout-direction handled via `ProvideAppLanguage` |
| Android runtime permission flow | ⛔ Not done | Permissions declared; in-app prompt with rationale is the open action item |
| Authority tests | ✅ Done | `WhodunitActionAuthorityTest` |
| Multi-device contract tests | ✅ Done | `MultiDevicePartyPlayContractTest`, `WhodunitMultiDeviceShapeTest` |
| Case picker discovery test | ✅ Done | `CasePickerDiscoveryTest` |
| Pass-and-play parity test | ✅ Done | `PartyPlayPassAndPlayParityTest` |
| Reveal-stage regression test | ✅ Done | `CharacterRevealAuthorityTest` |
| `PHASE_8_VALIDATION.md` updated with evidence | ✅ Done | This document |

## Verdict

Phase 8 ships **release-quality gameplay + authority + UI rebuild** behind
the existing `parlor.p2p.enabled=true` flag. The two carry-overs are the
Android in-app permission prompt and a real two-device LAN run on Wi-Fi —
both have runbook coverage in `docs/P2P_MANUAL_TEST.md` and neither blocks
the gameplay, authority, or UI/UX surfaces this phase committed to.
