# Performance (lead, code-only — no profiler)

## Demonstrated from source (not timed)

- Traffic/work is explicitly bounded: host queue 16×40KiB, peer queue 8×272KiB,
  burst 32 / sustain 16 fps, admission 17 pending / 21 sessions / 128 identities
  (`P2pTrafficPolicy.kt`).
- Host coordinator mailbox is **8** (`HOST_MAILBOX_CAPACITY`) and inbound
  collect uses suspending `send` — liveness couples to apply/snapshot cost
  (SN-001/007).
- Snapshots are per-command, public+one-private, max 256 KiB payload.
- Case JSON: 37–42 KiB × 7 ≈ 283 KiB bundled. Fonts: Inter 877 KiB + Mono 187 KiB.
- `P2pKitRoomTransport.kt` ~203 KiB / 4589 lines; coordinator ~79 KiB / 1936 lines.
  Review cost, not proven runtime cost.
- Diagnostic ring 256 closed-vocab records; DROP_OLDEST; ≤10 lines/s.
- `InMemoryCachedCaseDataSource` is process RAM; production remote never fills it.
- Discussion ticker is a coroutine loop in Whodunit UI, not a reducer clock.
- File snapshot writes are mutexed in `FileBackedSnapshotStore`; Android
  `readBytes()` after length check can materialize up to the 8 MiB cap.

## Needs runtime / profiling

- Compose recomposition of `App` Crossfade + game flows (unstable lambdas likely).
- JSON encode cost of full Whodunit case on every snapshot.
- Discovery/mDNS battery on Android/iOS.
- 16-player Mafia night coordination on mid-range phones.
- R8/minified cold start.

No busy-wait loops found in inspected session/transport hot paths; work is
channel/flow driven.
