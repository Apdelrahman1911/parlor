# Networking and concurrency review

## Designed properties (verified in source)

- Exact protocol 4.2; unknown CBOR keys rejected.
- Actor overwrite on every typed peer message.
- Peers never reduce; no optimistic apply.
- Non-idempotent rejected commands are not auto-retried.
- Start handshake is offer → ready → irreversible commit.
- Rejoin: same host/seat, 120s grace, credential rotate, Leave deletes.
- Host process death ends the room (no migration).
- CancellationException rethrown on inspected P2P/storage/bridge paths.
- Queues and ledgers are numerically bounded (command ids 2048, etc.).

## Confirmed issues

- **F-006 / SN-001+007:** host mailbox 8; inbound collect `mailbox.send`
  has no timeout. Couples all host work to apply/snapshot speed and noisy
  peers.
- **SN-003 / TB-006:** only real-P2pKit test that runs is “host advertises
  a 6-char code.” Join, actor-stamp on wire, broadcast `@Ignore`.
- **SN-004:** peer Channel(8) of 272 KiB frames can backpressure host
  sends (2s timeout → that peer Timeout; snapshots conflated).
- **SN-005:** `connectionEpoch` stuck at 1; replay fence is message-id +
  revision. Residual use-after-replace race on shared incoming channel.
- **SN-006:** admission limiter before WrongCode — LAN neighbor can delay
  legitimate joiners.
- **SN-002:** `peerEvents` replay=0; production bridges use `members`
  StateFlow (mitigated).
- **SN-009:** `InMemoryRoomBus` cannot prove resume/LeaveNotice/
  closeAdmissions. Game MP tests use the bus.
- **SN-010:** Broadcast success if ≥1 peer received; unused for gameplay
  today (Direct outboxes).

## Not found

- Split-brain (single host reducer).
- Peer speculation.
- Unbounded retry storms on non-idempotent commands.
- Protocol downgrade path (incompatible → closed failure).

**Largest hole:** physical two/three-device LAN. Code review cannot close it.
