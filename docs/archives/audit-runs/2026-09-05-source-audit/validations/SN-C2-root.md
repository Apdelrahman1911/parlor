# SN-C2 — independent validation

- Classification: **CONFIRMED DEFECT**, Medium, cancellation/resource ownership.
- Finder: `/root/session_cont`; independent validator: `/root`.
- Source: main `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`; tracked files unchanged.
- Root for every source path below: `/Users/abdelrahman/Projects/parlor/`.

## Reachable proof

`shared/transport-p2p/src/commonMain/kotlin/com/parlor/transport/p2p/P2pKitRoomTransport.kt:259–337` transfers ownership too early: initializationComplete becomes true at304 and the failure-cleanup finally ends317; lifecycle registration at319 can still suspend/throw before Success326. The outer catch rethrows cancellation but cannot close its now-inaccessible local kit/room.

`AppLifecycleRoomCoordinator.kt:45–54` stores the room and applies existing background state while holding its transition mutex. Host `appBackgrounded` at3204–3281 awaits native advertising shutdown and then enters coroutineScope. Cancellation during this registration escapes without `leave`. Host room acceptor/sessionSupervisor at1799–1827 belongs to the supplied process scope, not the cancelled opening coroutine.

The production owner `shared/session/src/commonMain/kotlin/com/parlor/session/multidevice/ProcessMultiplayerSessionOwner.kt:642–679,857–945` cancels an Opening transaction on route Leave, but can clean an orphan only after openRoom has returned Success. Here `opened` stays null. Both shipping host flows call this owner with `transport.host` and expose cancellation while opening (WhodunitHostSessionFlow199–215/272–286; MafiaHostLobbyFlow158–174/232).

Result: cancelling the hosting operation can leave an unowned kit/collector. Existing background expiry may eventually clean it (3333–3368), but foreground before expiry re-advertises it (3284–3308), and an empty roster satisfies markActiveIfRestored (3311–3330), cancelling expiry. This persistence extension is source-level evidence, not an executed physical radio test.

## Executed evidence and counter-evidence

`evidence/repro-ui-transport-02/`: one isolated test compiled and ran; its expected stopCalls=1 assertion failed with actual0. It invokes the **production transport** with the existing fake kit, background registration, then cancellation. The fake stopAdvertising waits in NonCancellable, matching the rc3 cleanup property rather than assuming cancellation aborts native cleanup. It explicitly ends fake resources in finally. This proves missing ownership cleanup, not an observed native socket/Bonjour leak on a device.

Exact Maven Central rc3 source (`evidence/research-p2p-send/kit-parentjob-excerpts.txt`, source archive receipts) constructs P2pKit with parentJob=null and a SupervisorJob(parentJob). Opening cancellation does not implicitly own/stop the kit. Host leave itself has NonCancellable cleanup, but it is never reached here. Earlier initialization failure is handled correctly; peer paths have separate ownership-finalization logic. Cycle01 failed in audit-init configuration before tests and is **not** reproduction evidence.

## Recommendation

Keep creation plus lifecycle registration under one ownership-transfer try/finally, closing any unreturned room/kit NonCancellable on every failure/cancellation; detach lifecycle registration during close. Preserve SDK and session authority boundaries. Add cancellation-before/during/after-registration tests, foreground-after-cancel, cleanup idempotence, failed registration, and production-owner Leave integration. Test both games' opening Back paths; no protocol/rule change is needed.

Cycle02 stop exit0, cleanup errors[], remaining module outputs[]; no tracked changes. Non-task Gradle9.x processes were preserved, not killed. No physical Android/iOS reproduction was performed.
