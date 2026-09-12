# Gaps — session / network

## Physical LAN (unproven)

`@Ignore` in `P2pKitRoomTransportLoopbackTest`:

1. Peer discovers host via mDNS, completes admission, appears in
   `members`.
2. Peer→host heartbeat with forged `actor` is rewritten; host→peer
   `SessionEnded` arrives.
3. Host broadcast reaches two peers.

Also unproven on real radios:

- Android `lastSeen == null` freshness accept vs iOS timestamp path.
- Bonjour goodbye 150ms vs 5–30s ghost rooms.
- Two Parlor builds with different 4.x minors on one LAN.
- Fingerprint pin when a host reinstalls (new identity, same room code).
- 17 remote seats + concurrent resume + continue-without.
- App background on iOS (CloseActiveSessions + 120s) while a peer is
  mid-command.
- Cross-subnet / AP isolation / Personal Hotspot.

## InMemory vs production (what tests do not cover)

- No encrypted channel, no discovery scheduler, no credential store.
- `leave()` is a no-op; `finalLeave` / `closeForRetry` fall back to it.
- No `closeAdmissions` CommandInFlight, no retire, no lifecycle states.
- peerEvents must be synthesized; membership StateFlow is empty unless
  the fixture writes it.
- Queue depths (64/32) differ from production (16/8).
- No traffic guard / admission limiter.

Coordinator and game-bridge desktop tests are therefore
**authority/schema proofs**, not transport proofs.

## Lifecycle / split-brain

- No host migration by design. Unproven: host force-quit vs 120s peer
  resume vs leftover Bonjour advertisement from a dead process.
- Two hosts typing the same accidental code: join tries every
  `parlor-room|*` candidate; WrongCode is recorded and another
  candidate can still be the real room. Unproven on a crowded LAN
  with many Parlor advertisers.
- `connectionEpoch` unused as a generation fence (SN-005).

## Command / mailbox

- No test that 9+ concurrent `Work` items fill `HOST_MAILBOX_CAPACITY`
  during `applyCommand`.
- No test that host inbound Channel(16) backpressures P2pKit when the
  coordinator is paused.
- Peer `RecoveryTimedOut` + later snapshot restart is unit-tested;
  not tested across a real socket replace.

## Resume vs continue-without

Host `retireDisconnectedMember` vs in-flight `completeResume` is
mutex-ordered in `HostP2pRoom`. Game bridges then fail the session if
reducer and transport diverge. **Not** covered by InMemory game tests.
Lifecycle test file is large; a dedicated resume-vs-retire case should
be confirmed before calling this proven.

## Discovery UI

`discoverRooms()` default is empty. `supportsDiscovery = false`.
Join-by-code still scans. There is no browsable room list and no
manual endpoint. Unproven: user-visible timeout taxonomy
(WrongCode vs Timeout vs FailureUnclassified) on iOS Local Network
denial (Apple has no preflight; code refuses to guess PermissionDenied
without `P2pError.PermissionMissing`).

## What this workstream did not read

- Full bodies of every session/transport test (titles + Ignore + key
  contracts only after the production files).
- Mafia/Whodunit lobby Compose beyond admission/`ProcessMultiplayerSessionOwner`
  usage.
- Engine reducers, storage crypto, shell navigation (other workstreams).
- Any `*.md` outside `project-code-audit/`.
