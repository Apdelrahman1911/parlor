# Performance review

No profiler was run. Separate **code facts** from **needs measurement**.

## Code facts

- Explicit traffic bounds (`P2pTrafficPolicy`): 16×40KiB host queue, 8×272KiB
  peer queue, 32 burst / 16 fps sustain, 17 pending admissions.
- Host mailbox capacity **8** with suspending `send` from inbound collect
  (F-006 / SN-001/007). Can stall start/heartbeat under burst or slow
  `applyCommand`/JSON encode. Not unbounded; it is a small rendezvous.
- Snapshots encoded on every accepted command; payload cap 256 KiB.
- Bundled cases ~37–42 KiB × 7 ≈ 283 KiB. Fonts ~1.06 MiB.
- Diagnostic ring 256, rate-limited 10 lines/s.
- In-memory case cache never filled in production (remote always Unreachable).
- Whodunit discussion ticker is a UI coroutine, not a reducer spin loop.
- Android snapshot `readBytes()` after length check can allocate up to 8 MiB cap.

## Needs profiling / devices

- Compose recomposition of `App` Crossfade + large game flows.
- Snapshot JSON cost with a full Whodunit case on low-end phones.
- mDNS/discovery battery.
- 16-player Mafia night on mid-range Android/iOS.
- R8 cold start.

**Verdict:** resource policy is deliberate and mostly bounded. The host
mailbox is the only demonstrated liveness risk in source. Do not treat
green desktopTest as a performance certificate.
