# Multiplayer experience playtest

Document status: current UX supplement.

Use this after the objective physical rows in
[`P2P_MANUAL_TEST.md`](P2P_MANUAL_TEST.md). This checklist evaluates whether a
technically correct session is understandable and enjoyable; it does not
replace the release evidence matrix.

## Setup

- Use at least three physical devices and a signed candidate when judging
  release quality.
- Record device model, OS, Parlor SHA/build, roles, game, and network topology.
- Capture the privacy-safe `ParlorP2p` diagnostics described in the canonical
  runbook. They contain fixed event codes and coarse counts only. They do not
  contain names, room codes, peer IDs, tokens, payloads, or private state.
- Test normal Wi-Fi first. Treat hotspot behavior as a separate topology with
  its own required rows; do not infer it from normal-LAN success.

## Connection and lobby

1. Time Host tap to visible room code.
2. Time Join tap to the peer's pending-approval screen, then host approval to
   the waiting lobby. The technical total discovery/dial/first-response budget
   is 30 seconds; a valid request then has a separate 60-second host-approval
   budget. UX should normally feel much faster on a healthy LAN.
3. Confirm wrong-code, host-declined, full-room, game-started, permission,
   timeout, and unclassified-network failures use distinct, localized copy and
   always offer a useful next action.
4. Confirm it is obvious that entering a room code still needs LAN discovery;
   Parlor has no raw-IP/direct-connect fallback.
5. With multiple pending peers, confirm the host understands who is awaiting
   approval and cannot approve beyond the game's capacity.

## Gameplay responsiveness

For both Whodunit and Mafia:

1. Measure tap-to-result latency for peer actions and host actions.
2. Have several players act at nearly the same time. Confirm every command
   receives a result, invalid/stale actions show actionable feedback, and no
   tap is applied twice.
3. Compare all screens after each phase transition. Public phase/outcome must
   match while role/dossier information remains visible only to its owner.
4. Exercise invalid targets, out-of-turn actions, repeated taps, and disabled
   controls. The UI must agree with host-side authority instead of appearing
   to accept something that the host rejected.
5. Complete a full game and rematch. Note pacing, clarity, animation comfort,
   and any point where players stop looking at one another because the app
   demands too much attention.

## Interruption experience

1. Briefly disconnect one peer without tapping Leave. The host should explain
   that play is paused/blocked for that seat; the peer should show recovery,
   return to the current authoritative snapshot, and never revisit setup.
2. Background and foreground a peer and then the host. A short interruption
   should preserve the room; a 120-second expiry should become terminal and
   explain why.
3. Force-terminate a peer and relaunch inside the grace window. The Home screen
   should offer multiplayer Resume and recovery should require no room code.
4. Tap explicit Leave. Relaunch and confirm Resume is gone. This distinction
   must be clear: transient interruption is resumable; Leave is final.
5. Exit or terminate the host. Peers must understand that the room ended and
   that no host migration exists.

## Permission and recovery UX

- On iOS, deny Local Network access, retry, open app Settings, enable it, and
  retry again. The app must not claim permission was granted until a real
  advertise or authenticated connection succeeds.
- A timeout, Wi-Fi-off state, firewall, or blocked Bonjour path must not be
  mislabeled as proven permission denial.
- Android's shipped NSD/TCP path needs no Nearby Devices or Location runtime
  prompt. Seeing one is a release failure.

## Accessibility and localization

Run the complete lobby, error, reconnect, and one full-game path with:

- TalkBack and VoiceOver;
- English LTR and Arabic RTL;
- 200% text or the largest supported accessibility size;
- reduced motion;
- portrait and landscape where supported; and
- phone plus representative tablet size.

Record focus order, announcements for changing connection state, button labels,
touch targets, clipped text, contrast, and whether time-sensitive feedback is
available without color alone.

## Report format

For each observation record:

```text
Device/OS/build/role/network:
Game and phase:
Action taken:
Expected experience:
Observed experience:
PASS / FAIL / UNVERIFIED:
Video/screenshot and redacted ParlorP2p sequence numbers:
```

Do not paste secrets or enable verbose P2pKit payload/frame traces in a release
build. The fixed-shape Parlor diagnostics plus screen evidence are the supported
production debugging surface.
