# Snapshot payload ownership — October 31, 2026

## Confirmed finding and boundaries

[#304](https://github.com/Apdelrahman1911/parlor/issues/304) is a P2 generic
coordinator defect at independently accepted main `16d4ecab`. `sendSnapshot`
validates a `PlayerSnapshotPayload`, then queues its mutable arrays directly.
An asynchronous outbox can serialize after the next recipient callback overwrites
a reused private buffer, or after its producer mutates a captured public buffer.
The direct recipient and revision metadata remain unchanged while their bytes do not.

This is an executed in-process API counterexample, **not an observed shipping-game
disclosure, remote authentication bypass, physical-device incident or native-input
cause**. Reviewed Whodunit/Mafia encoders use new `encodeToByteArray` results;
Last Light and the three shared-shell game codecs also allocate encoding results.
The host remains trusted. Concurrent mutation during capture is forbidden: a
defensive copy cannot synchronize with an independently writing producer.

## Actual original-production execution

Four new common methods execute the actual host coordinator/outboxes with a
deterministic transport fixture. The fixture copies bytes at the real `send`
entry or after its explicitly held send, not retrospectively after the assertions.
Against byte-exact original production: **two assertion failures /two passing
size controls /zero errors or skips**.

- Two recipient callbacks share a private buffer. Peer-one receives22 instead of11.
- A held send retains revision0 but its public byte changes from1 to9.
- Exact combined256KiB and empty payloads remain accepted.
- Oversized payloads are not sent; a subsequent valid publication still succeeds.

Original XML SHA256:
`860c4ed5e10f20eb74da59236049495282554669a5a73aaf8c3a2d12ac359457`.
Original log SHA256:
`ecdafd0998ca6d5fbb0854bd37fed553d841684cbaf832639fdcedb69181cf2d`.
The original failed methods are retained, not normalized or replaced.

## Isolated correction

Validate the unchanged metadata/combined256KiB limit first, then copy public and
private bytes before handing them to the outbox. No domain serialization, protocol,
retry, recipient policy, mailbox/outbox capacity, worker/thread, dependency or
Release optimization changes. Each accepted payload adds bounded copy work;
transient allocation/CPU/energy on phones is **not measured**. This is an ownership
correction, not a mobile performance optimization or immutable-domain guarantee.

## Verification and integration status

Corrected affected suites independently pass **173 Desktop /170 Android Debug
/170 Android Release**, zero failures/errors/skips. Exact previous method sets
plus all four common additions are preserved; plain and type-aware Detekt pass.
Affected exact-method receipt SHA256:
`840f64c13ab73362429714c1ca11e7dee50119102d07325e38fe61951edabfd1`.

Final committed-source strict checks and fresh complete hosted/native, protected
integration and distinct actual-main acceptance remain required before closure.
Original PR296/300 failures and held #299/#301 fixes are not cleared by this work.
No failed workflow retry, copied observer shipping, added agent, paid runner,
physical qualification or Store release is claimed.

All open/closed issue titles and snapshot/buffer/caller-owned searches were reviewed,
including #11/#260/#301/#299. The closed recipient-test gap, game-state/concealment
acceptance and membership/start-roster aliasing are different boundaries.
