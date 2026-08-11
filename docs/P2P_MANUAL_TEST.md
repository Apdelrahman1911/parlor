# P2P physical-device validation runbook

Document status: current release-gate procedure.

This is the canonical manual test contract for Parlor multiplayer. A compiler,
simulator, loopback test, or successful run on one device pair does not pass a
physical row. Record every unexecuted row as **UNVERIFIED**.

## Supported product boundary

- Shipping clients: Android 8.0/API 26 or newer and iOS 16 or newer.
- Shipping topology: devices reachable on the same local IP network.
- Transport: P2pKit 0.7.0-rc3 LAN discovery plus authenticated encrypted TCP.
- Room entry: the peer types the six-character admission code shown by the
  host. The code is proved only inside the encrypted session and is not in the
  advertisement.
- Internet rendezvous, NAT traversal, relay, spectators, and host migration
  are unsupported.
- Raw IP/manual endpoint connection is unsupported. Room-code entry still
  relies on LAN discovery. If multicast discovery is blocked, the release has
  no direct-connect fallback; see ADR-0002.

Hotspots are not a blanket product promise. Android and iPhone hotspot
implementations can differ by OS, vendor, topology, and client-isolation
policy. A hotspot configuration is supported only after its required rows
below pass consistently on recorded physical devices.

## Runtime contract under test

### Time budgets

| Operation | Production budget |
|---|---:|
| Discovery, dial, secure handshake, and first admission response combined | 30 seconds |
| One dial plus secure handshake attempt | 5 seconds |
| First admission response after a secure connection | 5 seconds |
| Human host approval after `AdmissionPending` | 60 seconds |
| Initial host start Ready quorum | 20 seconds |
| Peer start preparation after a compatible offer | 20 seconds |
| Peer commit delivery after sending Ready | 20 seconds |
| Start commit-ack delivery/retry window | 20 seconds; expiry does not roll back a committed start |
| One start-control-frame send | 2 seconds |
| Peer transport resume attempt / disconnected-seat grace | 120 seconds |
| App background-to-resume grace | 120 seconds from the first background event |
| Resumable credential cryptographic expiry | 24 hours; this does not extend the host's 120-second seat reservation |

Wrong room codes are candidate-local. A wrong room appearing first must not
finish the join while another candidate can still appear. A final join failure
must occur by the 30-second total deadline unless the request has reached the
separate 60-second human-approval state.

### Lifecycle and rejoin

- Backgrounding a host or peer immediately suspends its logical room. New game
  commands are rejected while suspended. Whodunit freezes its game clock;
  Mafia has no autonomous timer and cannot advance through peer commands.
- Foregrounding inside 120 seconds starts recovery. The room becomes active
  only after transport/admission restoration succeeds; snapshots then restore
  the authoritative revision.
- A peer's transient disconnect, backgrounding, or process death preserves a
  device-protected resumable credential. Relaunch may show the multiplayer
  Resume tile while the same host still owns the room and retains that seat.
- The credential is bound to the player, host peer ID and authenticated
  fingerprint, room/game versions, generation, and expiry. A successful
  resume rotates it transactionally.
- Tapping **Leave** is final: it sends best-effort notice, removes membership,
  and permanently deletes the local resume credential. Leave followed by Join
  is a fresh admission, not rejoin, and is available only if the host still
  accepts new players.
- A host process death, OS termination, explicit exit, or 120-second lifecycle
  expiry destroys the room. There is no host migration. Peers must reach a
  terminal host-lost state and stale credentials must not restore the room.

### Wire and authority

Runtime protocol: `4.1`.

Parlor serializes `RoomMessage` as strict CBOR inside `P2pMessage.Binary`.
Protocol compatibility is exact: a 4.1 binary fails closed when paired with a
different major or minor schema. Admission is transactional:

```text
AdmissionRequest -> AdmissionPending -> host approval
-> AdmissionOffered -> AdmissionConfirmed -> AdmissionCommitted
-> AdmissionReady -> AdmissionCommitAck
```

Resume rotates the credential through:

```text
ResumeRequested -> ResumeOffered -> ResumeConfirmed -> ResumeCommitted
-> ResumeReady -> ResumeCommitAck
```

`AdmissionOffered` and `ResumeOffered` carry the canonical host display name.
Exact duplicate player names are rejected with `DisplayNameInUse` while the
host, a pending request, or a connected/disconnected resumable seat reserves
that name. Case variants are distinct labels; all authorization remains bound
to authenticated player IDs.

Starting gameplay uses the reliable protocol-4.x transaction introduced in
4.0 and retained by 4.1:

```text
SessionStarting(startId) -> SessionStartReady(startId)
-> SessionStartCommitted(startId) -> SessionStartCommitAck(startId)
```

The host retries one immutable offer with a stable `startId`, starting at 250
milliseconds and backing off to at most 2 seconds. Every admitted peer must
validate its game/session/content shape and answer Ready inside the host's
20-second quorum window. A peer gets its own 20-second preparation window only
after a structurally compatible offer arrives, followed by a fresh 20-second
commit-delivery window after it sends Ready. If the Ready quorum is not met,
the host cancels the start and ends that session attempt.

After every peer is Ready, the host commits irreversibly. Only
`SessionStartCommitted` authorizes gameplay. `SessionStartCommitAck` confirms
delivery but is not a rollback boundary: the host retries the same commit for
up to 20 seconds, and a lost acknowledgement cannot undo a committed game. A
resumed seat repeats the same stable offer/Ready and commit/acknowledgement
barrier before it receives live gameplay.

During the start transaction, the host publishes gameplay snapshots only after
commit. The peer attaches its gameplay collector after accepting that commit
and explicitly requests a validated initial snapshot until one arrives;
commands remain blocked before that snapshot. This makes a lost eager
revision-zero snapshot recoverable rather than relying on transport timing.

Gameplay uses `ClientCommand(commandId, clientSequence, expectedRevision)` and
returns `CommandResult`; accepted mutations advance the authoritative revision
and are followed by player-specific `PlayerSnapshot` messages. The host stamps
the actor from the authenticated P2pKit session. Client-supplied actor IDs are
never trusted. Duplicate command IDs are idempotent; stale revisions and
sequence gaps are rejected and trigger snapshot revalidation, not blind replay.

Legacy `JoinRequest`, `ActionSubmit`, split public/private snapshots, and JSON
examples are not the shipping protocol and must not be used for validation.

## Safe diagnostics

Production diagnostics use the `ParlorP2p` tag/prefix and fixed fields only:

```text
seq=<n> elapsed_ms=<n> event=<fixed_name> role=<host|peer|none> result=<fixed_value> reason=<fixed_value> count=<coarse_bucket>
```

Capture them with:

```bash
# Android
adb logcat -v threadtime -s ParlorP2p:I '*:S'

# Desktop development harness
./gradlew :composeApp:run
```

On iOS, run from Xcode or the signed TestFlight build and filter the device
console by process plus `ParlorP2p`.

The recorder retains at most 256 fixed-shape records and platform output has a
one-record backlog emitted at most ten times per second. Under a flood,
intermediate events may be deliberately coalesced; UI state and screenshots
remain primary evidence. Logs never contain room codes, names, peer/player/
session IDs, IP addresses, fingerprints, rejoin tokens, keys, payloads, private
game state, or exception text. Treat any such value in a release log as a
privacy failure and stop the test.

Useful event names include `session_create_started`, `discovery_started`,
`discovery_candidates`, `discovery_attempted`, `connection_secure`,
`admission_requested`, `admission_reserved`, `admission_committed`,
`command_sent`, `command_received`, `command_accepted`, `command_rejected`,
`command_duplicate`, `snapshot_sent`, `snapshot_received`,
`lifecycle_suspended`, `lifecycle_resume_started`, `lifecycle_resumed`,
`lifecycle_expired`, `frame_dropped`, `peer_rate_limited`, and the cleanup
events. Do not expect identifiers or human-readable failure strings.

## Evidence record

Create one record per run, not one summary from memory:

```text
Run ID:
Date/time/time zone:
Parlor Git SHA and protocol version:
Artifact source and checksum (debug / signed internal / TestFlight):
P2pKit Maven coordinate:
Device A model, OS build, role:
Device B model, OS build, role:
Device C model, OS build, role (if used):
Network/hotspot owner, band, security, router/model/settings:
Permission state before run:
Scenario ID and repetition number:
Observed result:
PASS / FAIL / UNVERIFIED:
Screenshots/video:
Redacted ParlorP2p logs:
Issue link and exact reproduction, if failed:
```

For hotspot rows, repeat the full scenario at least three times without
restarting devices. A single success is evidence for that run, not a claim of
100% model/OS compatibility.

## Prerequisites common to every row

1. Run the automated gates for the exact SHA first:

   ```bash
   ./gradlew productionCheck productionAppleCheck allTests \
     --dependency-verification=strict --no-daemon --stacktrace --console=plain
   ```

2. Use the same release candidate on every device. Final release acceptance
   requires signed internal/Play and TestFlight artifacts, not only debug apps.
3. Record Wi-Fi/hotspot state, VPN, Private Relay or vendor network features,
   battery restrictions, and all relevant permissions/settings.
4. Start with cleanly closed Parlor rooms, but do not reboot between repeated-
   session rows.
5. Play both Whodunit and Mafia where the row says "both games".

## Required physical matrix

### LAN and cross-platform fundamentals

| ID | Prerequisites and exact steps | Expected result and PASS criterion | Evidence |
|---|---|---|---|
| PHY-01 Android -> Android | Two Android phones on normal Wi-Fi. A hosts; B enters code; A approves. Complete one game. Swap host and repeat. | Both directions reach secure admission, commands receive explicit results, snapshots stay synchronized, and both games can finish. | Models/API builds, both role directions, lobby/game screenshots, diagnostic logs. |
| PHY-02 iOS -> iOS | Two physical iPhones on normal Wi-Fi. Repeat PHY-01 in both host directions. | Same as PHY-01; Bonjour prompt and recovery copy are truthful. | Models/iOS builds, permission state, both role directions, logs. |
| PHY-03 Android host -> iOS peer | Android hosts on normal Wi-Fi; iPhone joins, is approved, and completes both games. | Discovery, authenticated connection, commands/snapshots, terminal state, and cleanup all pass. | Artifact checksums, roles, screenshots, both logs. |
| PHY-04 iOS host -> Android peer | Reverse PHY-03. | Same binary criteria as PHY-03. | Same evidence with reversed roles. |
| PHY-05 Three-device mixed room | One host plus at least two peers, with both OS families represented. Complete both games. | Host admits exactly the approved peers; all players stay on one revision/outcome; private roles are visible only to their owners. | Three device records, synchronized phase/video, logs. |
| PHY-06 Multiple rooms and late candidate | Two hosts advertise different rooms. Peer enters host B's code while host A appears first; restart B advertisement once. | Wrong-room rejection for A does not finish the join; B is retried/selected before the 30-second deadline. No cross-game state. | Timeline/video and candidate/result diagnostics. |
| PHY-07 Wrong code and cancellation | Enter a valid-format nonexistent code, then repeat and cancel during discovery. | First attempt gives localized failure by 30 seconds; cancelled attempt exits promptly and leaves no discovering ghost. | Screen recording, cleanup diagnostics, new room succeeds afterward. |
| PHY-08 Admission decisions and capacity | Exercise approve, decline, no response for 60 seconds, closed game, and concurrent last-seat requests. | Only approved capacity-reserved peers commit; decline/timeout/full/started are distinct; no ghost member survives failure. | Roster before/after, error screens, admission diagnostics. |

### Android hotspot

| ID | Prerequisites and exact steps | Expected result and PASS criterion | Evidence |
|---|---|---|---|
| HOT-A1 Owner hosts and plays | Android A enables Personal/Portable Hotspot. Android or iPhone B joins it. A hosts and participates through a full game. Repeat three times. | B discovers A; owner-to-client TCP works; commands/snapshots remain synchronized; cleanup permits the next session. | Android model/vendor/OS, hotspot band/security/settings, peer model/OS, three run records. |
| HOT-A2 Connected client hosts | Android A owns hotspot. B and C connect. B hosts; C joins and plays; A also joins as a player. | Owner-to-client and client-to-client discovery/TCP both work. Three devices finish one game with synchronized state. | All roles/topology, three device logs, game completion. |
| HOT-A3 Cross-platform both directions | Repeat HOT-A1/A2 with Android and iPhone alternating host/peer where topology permits. | Every attempted direction is recorded separately; no direction is inferred from another. | Per-direction PASS/FAIL and exact devices/OS. |

### iPhone Personal Hotspot

| ID | Prerequisites and exact steps | Expected result and PASS criterion | Evidence |
|---|---|---|---|
| HOT-I1 Owner hosts and plays | iPhone A enables Personal Hotspot. Android or iPhone B connects. A hosts and participates through a full game. Repeat three times. | Client discovers owner; owner-to-client TCP works; state and cleanup pass. | iPhone model/iOS, Maximize Compatibility setting, peer details, three records. |
| HOT-I2 Connected client hosts | iPhone A owns hotspot. B and C connect. B hosts; C joins; A joins as player. | Owner-to-client and client-to-client discovery/TCP both work, or the row fails for this configuration. | Three devices, topology diagram, logs/screens. |
| HOT-I3 Cross-platform both directions | Repeat with Android/iPhone host roles reversed where possible. | Each hosting direction independently passes discovery, play, rejoin, and cleanup. | Per-direction records; never summarize as universal support. |

If a hotspot row needs "Maximize Compatibility", a specific band, screen-on,
mobile-data state, or manufacturer option, record that limitation in release
copy and support notes. A manual endpoint is not an allowed workaround.

### Recovery and lifecycle

| ID | Prerequisites and exact steps | Expected result and PASS criterion | Evidence |
|---|---|---|---|
| PHY-09 Peer transient loss/rejoin | Mid-game, disable peer Wi-Fi for 5-15 seconds without tapping Leave; restore it. Repeat in both games. | Host marks seat disconnected and blocks progression; peer resumes same seat inside 120 seconds; snapshot restores current revision; no action doubles. | Before/after phase, lifecycle and snapshot diagnostics. |
| PHY-10 Peer background/foreground | Background and screen-lock a peer for 10 seconds, then restore. Repeat near 120 seconds and once beyond it. | Short interruption resumes; the original deadline is not extended by repeated background events; beyond 120 seconds expires cleanly. | Timestamps, UI overlays, lifecycle diagnostics. |
| PHY-11 Host background/foreground | Background/lock host for 10 seconds and return; then test beyond 120 seconds. | Short interruption freezes room and restores it. Long interruption ends room; peers do not migrate host or continue stale play. | Host and peer timelines/logs. |
| PHY-12 Peer process death/relaunch | Force-stop/terminate a peer without Leave; relaunch within 120 seconds and choose multiplayer Resume. | Protected credential restores same host/seat, rotates, and receives current snapshot. No room code/token is shown in logs. | Relaunch video, secure resume/lifecycle diagnostics. |
| PHY-13 Final Leave | Peer taps Leave. Relaunch app and inspect Home; attempt resume, then a fresh join if lobby remains open. | Resume tile/capability is gone and old credential cannot resume. Any fresh join is a new host-approved admission. | Home screen, host roster, cleanup diagnostics. |
| PHY-14 Host exit/disappearance | Test explicit host Leave and force-termination separately. | Peers reach terminal host-lost UX; no migration; room stops being joinable; a fresh host/session works without device restart. | Peer terminal screen, cleanup/discovery results. |
| PHY-15 Network switch | Move a peer and then a host between reachable Wi-Fi/hotspot networks during play. | Recovery succeeds only if the same room remains reachable inside grace; otherwise deterministic expiry/terminal UX, never stale commands. | Network timeline, result, lifecycle diagnostics. |

### Protocol, gameplay, and sustained use

| ID | Prerequisites and exact steps | Expected result and PASS criterion | Evidence |
|---|---|---|---|
| PHY-16 Simultaneous legitimate actions | In both games, coordinate multiple players to act against one revision. | Host serializes effects; one command may be stale/rejected with visible retry guidance, but no action executes twice and final state is valid. | Video, final tally/state, command result diagnostics. |
| PHY-17 Duplicate/delayed/malformed/version faults | Use the deterministic adapter/fault harness for crafted frames; use physical debug fault controls only if present. | Duplicate is idempotent; stale/order/version/payload failures are closed and bounded; unaffected peer remains usable. | Automated report plus any device fault evidence; never hand-edit a release binary. |
| PHY-18 Repeated lifecycle | Without restarting devices, create, join, play/exit, and destroy at least ten rooms, alternating host and game. | Every cycle works; no ghost room, duplicate member, stale resume tile, growing delay, crash, or resource exhaustion. | Ten-cycle sheet, memory/battery observation, first/last logs. |
| PHY-19 Sustained session | Play continuously for at least 60 minutes with periodic actions, backgrounding, and one transient loss. | Stable state, bounded diagnostics/queues, acceptable battery/heat, no leaked room after exit. | Duration, device thermal/battery notes, final cleanup. |
| PHY-20 iOS denial and recovery | Fresh install or reset Local Network permission; deny first attempt, retry, open app Settings, enable Local Network, return and retry. | App never claims a grant before real LAN success; actionable denial offers Settings; unclassified failures remain unclassified; enabled access succeeds. | Permission screens, Settings, `permission`/operational diagnostics. |
| PHY-21 Android network conditions | Test Wi-Fi off/on, multicast-restricted network, and normal LAN. Confirm no Nearby/Location runtime prompt appears. | Normal LAN works; restricted paths fail truthfully and recover; app requests no provisioning-only runtime permission. | Merged manifest from artifact, UI/video, logs. |
| PHY-22 Signed artifacts | Install Play internal signed AAB and TestFlight build, then rerun PHY-01 through PHY-05, PHY-09 through PHY-14, and applicable hotspot rows. | Results match debug automation/device runs and artifact SHA/build numbers match release receipt. | Store-delivered build metadata and full receipts. |

## Direct/manual endpoint row

`MAN-00` is **N/A — unsupported by accepted ADR-0002**. Do not mark it PASS
because P2pKit offers an optional API that Parlor has not integrated. If direct
endpoint connection becomes a release requirement, reopen P2P-10 and implement
the fingerprint-pinned transport-independent design and its entire parity
matrix before testing it.

## Release interpretation

- **PASS:** every stated condition passed on the recorded devices/artifact.
- **FAIL:** any condition failed, including discovery that works only after an
  undocumented setting change.
- **UNVERIFIED:** row not executed, evidence missing, simulator/debug evidence
  substituted for a required signed physical run, or result cannot be repeated.
- **N/A:** only a capability explicitly excluded by an accepted ADR.

Do not say hotspot, iOS, Android, or cross-platform multiplayer is "fully
supported" until every applicable mandatory row passes consistently. Even
then, state the tested device/OS/network scope; no finite matrix proves 100%
compatibility with every hotspot or router implementation.
