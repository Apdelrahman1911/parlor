# SN-C2 — Cancellation after host advertising but before ownership return

> **Final independent disposition (2026-09-05): CONFIRMED DEFECT — Medium.** Validator `/root`; see `validations/SN-C2-root.md`. Production transport with a fake kit verifies omitted cleanup after cancelled registration; physical native-resource leakage was not measured. Original candidate text below is preserved; its pending language is superseded. Administrative banner added by `/root/whodunit_cont` at `/root` request, not a new source approval.

- Status: **UNCONFIRMED — independent validation / reproducer execution pending**.
- Originator: `/root/session_cont`; independent validator requested: `/root`.
- Suggested severity if confirmed: Medium (room/native-resource ownership and abandoned host advertisement).
- Reviewed source: `main`, `3625d0663ba6eb51338cbd5f9dc45f859ec18846`; no tracked modifications.
- Base path for source references: `/Users/abdelrahman/Projects/parlor/`.

## Proposed reachable failure

1. Either shipping game's host flow calls process-owner acquire and `transport.host`: `game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/ui/flow/multiplayer/WhodunitHostSessionFlow.kt:195–218` and `game-modes/mafia/src/commonMain/kotlin/com/parlor/games/mafia/ui/flow/multidevice/MafiaHostLobbyFlow.kt:155–177`.
2. `shared/transport-p2p/src/commonMain/kotlin/com/parlor/transport/p2p/P2pKitRoomTransport.kt:259–337` constructs the kit/room and starts advertising; `initializationComplete=true` at304 ends the cleanup `finally` at317 **before** the suspending lifecycle registration at319 and result return325.
3. `AppLifecycleRoomCoordinator.kt:44–54` installs this room and, if the process background event already arrived, calls its suspending `appBackgrounded`. The host's implementation schedules expiry, then awaits `kit.stopAdvertising` and its room-owned close coroutine scope (`P2pKitRoomTransport.kt:3204–3282`). This is not an atomic synchronous registration.
4. The host screen supports Cancel/Back while room acquisition is in progress. Whodunit `WhodunitHostSessionFlow.kt:161–176,241–245` and Mafia `MafiaHostLobbyFlow.kt:138–153` call `leaveRoute`. `ProcessMultiplayerSessionOwner.kt:654–678` cancels and joins the opening job.
5. If cancellation is observed inside registration after the inner cleanup region, `host` rethrows at327 without calling `room.leave()` or `kit.stop()`. The owner has not received a `Result.Success` room: `ProcessMultiplayerSessionOwner.kt:868–877,913–918` can close only an `opened` success. Its null result cannot compensate for a room still owned internally by the transport.
6. The lifecycle coordinator may retain that unreturned room and its incoming-session collector; native data transports are not shut down by advertising-only cleanup. Later foreground may advertise an empty orphan again and cancel its lifecycle expiry. This latter behavior is a source-level consequence to verify, not device evidence.

## Counter-evidence investigated

- Cancellation during kit start / advertising **is** covered by the inner `finally`; the candidate targets the subsequent registration window only.
- Owner cleanup handles returned-but-unadopted rooms; it cannot see a room that `host` never returned.
- Exact Maven Central rc3 source shows `P2pKitImpl.stopFeature:756–785` uses `NonCancellable` resource cleanup. The reproducer therefore completes that cleanup after requesting cancellation; it does not assume cancellability of the native stop itself. Cancellation may still be observed by the subsequent `coroutineScope` in Parlor's host background operation / registration lock boundary. This requires execution or independent coroutine-semantics validation before confirmation.
- The process lifecycle coordinator serializes foreground/background with registration but does not implement rollback when registration throws.
- Normal lifecycle expiry eventually calls `leave`; a foreground arriving after cancellation may instead restore the orphan. Neither is timely ownership cleanup of a cancelled creation transaction.

## Evidence and proposed verification

- Isolated test: `reproducers/SN-C2HostRegistrationCancellationTest.kt` (prepared, **not executed by originator**).
- Uses the existing desktopTest `FakeP2pKit`, with only a bounded test-controlled NonCancellable stop barrier; no real network or native process starts.
- Pin exact source hashes through `coverage/reviews-session_cont.jsonl`.
- Exact-version reference/ZIP hash: `evidence/session_cont-p2pkit-research.jsonl`; excerpts: `evidence/p2pkit-0.7.0-rc3-session_cont-excerpts.txt`. Official Central sources accessed 2026-09-05. Archives inspected in memory, none retained.

## Recommended remediation if confirmed

Extend the host ownership-transfer `try/finally` through lifecycle registration and final success return; on cancellation/failure detach/leave the exact created room under cancellation-safe cleanup. Investigate sibling join/resume registration and initial-ready cancellation paths separately. Preserve same-generation room-close guards and avoid deleting valid peer rejoin credentials during retry cleanup.

Regression tests: cancellation during post-advertise registration; no remaining acceptor subscriptions; kit terminal stop once; queued foreground does not restart an orphan; subsequent independent host generation works. Physical LAN and platform background timing remain separately unverified.
