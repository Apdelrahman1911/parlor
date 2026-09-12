# SN-C2 — independent factory ownership sibling validation

**Classification: CONFIRMED DEFECT (Medium), same cancellation/ownership root cause as SN-C2, not a seventeenth issue.**

- Finder: `/root/mafia_cont`; independent validator: `/root/factory_review`.
- Review date: 2026-09-06 Africa/Cairo. Baseline: `main`, commit `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, Git tree `db7f3d2afe73a13628296daee2cce71165eebc8d`, with the in-progress remediation modifications preserved.
- First-party root for paths below: `/Users/abdelrahman/Projects/parlor/`.
- Exact reviewed hashes/ranges: `SN-C2-factory-independent-proof-01.json`. This is bounded sibling validation, not a claim of fresh whole-file review of the very large transport or session owner.
- Original registration finding and validation reread: `audit-runs/2026-09-05-source-audit/{candidates/SN-C2-host-registration-cancellation.md,validations/SN-C2-root.md}`.

## Complete reachable path

1. Both shipping games supply `transport.host`, `transport.join`, or `transport.resumeLastSession` to `ProcessMultiplayerSessionOwner.acquire`: Mafia host flow 155–177, peer flow 161–175; Whodunit host flow 195–218, peer flow 191–205. The application's DI gives this owner a `Dispatchers.Default + SupervisorJob()` scope (`composeApp/.../di/AppModule.kt:33–60`).
2. `ProcessMultiplayerSessionOwner.kt:857–878` runs the opening operation in that process scope. Explicit user Cancel/Back/Leave calls `leaveRoute`; during `Opening`, 654–670 cancels and joins the opening job. A transient composition remount alone **does not** cancel this job and is not the reproducing prerequisite.
3. All three actual platform factories currently return `withContext(initializationDispatcher) { P2pKit.create { ... } }`: Android uses IO and its initialization mutex; Desktop uses IO; iOS uses Default. These are real composition-root bindings, not test-only factories. `shared/transport-p2p/build.gradle.kts` includes their source sets and the pinned rc3 dependencies.
4. Pinned P2pKit rc3 `P2pKit.create` synchronously calls builder `build` (`P2pKit.kt:248–266`, `dsl/Builders.kt:165–201`), then `newP2pKit`. `P2pKitImpl.kt:1427–1447` acquires the secure-identity lease and loads the key. 1461–1489 constructs the kit with **`parentJob = null`**. Its own scope uses `SupervisorJob(parentJob)` at112 and Default at125; it is not a child of Parlor's cancellable opening transaction.
5. Resources are live **before** `kit.start()`: constructor lines244–306 construct/start `PeerRegistry`; registry lines82–89 launch discovery collectors plus the eviction loop. Its 294–311 loop repeatedly delays1000ms while the kit scope is active. The scheduled continuation retains that scope/registry. The constructor also starts incoming-session collection at307. There is no application-owned returned kit reference yet.
6. Cancellation can win while synchronous creation finishes, or while dispatching its successful result back. Exact coroutines1.11.0 `withContext` (Builders.common.kt:472–504,585–588) gives the new coroutine the caller's Job and uses cancellable result delivery. `DispatchedTask.kt:91–100` discards a successful result when the caller Job is inactive.
7. **Same dispatcher is not a counterexample.** The iOS Default→Default fast path creates a child ScopeCoroutine/UndispatchedCoroutine and calls `startUndispatchedOrReturn`. The coroutine is linked to its parent (`AbstractCoroutine.kt:42–57`); cancellation during synchronous creation records a cancelling state. `Undispatched.kt:82–93` calls `makeCompletingOnce(result)`; `JobSupport.kt:191–234,877–946` resolves that cancelling state to an exception rather than the successful kit. Thus no dispatch-back hop is necessary for loss of the resource. This is the actual1.11.0 behavior, not an assumption from older documentation.
8. In `P2pKitRoomTransport.kt:272–281,362–375,729–745`, the assignment receiving `createKit` never completes. Existing host/join/resume cleanup only runs after that assignment. Factory cancellation is correctly rethrown, but no outer layer knows the kit to stop. The process owner can clean an orphan only from a returned `Result.Success` (`opened` at868/873 and913–918), so it also cannot compensate.
9. The independent kit job/eviction loop remains running; its secure lease is not released by caller cancellation. rc3's explicit terminal `stop` is the route that clears private key bytes, cancels/joins kit children, and releases usage (971–1068,1170–1203). SDK construction-error cleanup at1491–1494 cannot run here: SDK construction succeeded, and cancellation was raised by the surrounding Parlor factory coroutine after its return.

## Independent version verification

`independent-version-research-01.json` records this reviewer's own network access dates, URLs, archive hashes, and byte comparisons. Maven Central rc3 common, JVM, Android and iOS-arm64 source archives contain byte-identical relevant factory/constructor/registry implementations. The reviewed coroutines source agrees byte-for-byte with the official `Kotlin/kotlinx.coroutines` **1.11.0** tag for Builders, Undispatched and JobSupport. Catalog pins were independently reopened.

Downloaded archives were examined in memory and not retained. No confidential source excerpts, credentials, or player data were sent to a service.

## Counter-evidence and limits

- Before creation starts, cancelled `withContext` skips the body; there is no newly created kit to stop. The fix must preserve that behavior.
- Failed synchronous SDK construction has its own identity failure cleanup. This proof concerns a successfully created kit whose coroutine result is subsequently lost; it does not claim every SDK constructor exception leaks.
- Already-returned kits are protected by the transport's existing start/advertising/registration cleanup. Those fixes do not extend backward through a suspend call whose result was never assigned.
- `P2pKit.start` laziness avoids opening advertised sockets in this interval, but does not prevent the constructor's child jobs or secure identity lease. No claim of an already-advertised orphan in this earlier window is necessary.
- Existing `kit_factory_cancellation_is_never_mapped_to_transport_failure` only throws before returning a resource. Existing `cancellation_during_kit_start_cleans_up...` returns a kit synchronously from a fake factory; it tests the later ownership interval. Neither exercises lost factory results.
- Process-owner UI waiter separation is intentional; the production cancellation witness is an explicit opening-route Leave, not recomposition.
- This is a complete reachable source-level proof. No physical network, private identity, real-device resource measurement, Gradle test, or native test was run by this reviewer. Root's forthcoming deterministic tests must prove the repair, not merely succeed as a defect witness.

## Proposed correction reviewed

Use one small **transport-only** common helper to capture the new kit immediately inside `withContext`, before returning through that cancellation boundary. Check the caller remains active before transferring ownership. If any failure/cancellation prevents return, attempt terminal `stop` in `NonCancellable`; preserve the original failure and attach, rather than replace it with, a cleanup failure. Reuse the helper in all three platform factories, retaining the existing dispatcher, Android initialization mutex, identity stores, security mode, reconnect configuration and lifecycle policy.

Do not wrap the whole public opening flow in NonCancellable, create a new protocol/session abstraction, clear peer credentials, or transmit anything. No game, protocol, snapshot, content, persistent-identity or rejoin format needs a change. An unreturned factory kit has no application room or LeaveNotice to send.

Suggested executable coverage: successful ownership transfer/no early stop; cancelled before entry/no creation; cancellation during synchronous create in same and different dispatchers; completed creation with cancellation while its outgoing continuation waits; suspending stop still completes under cancellation; cleanup failure does not replace the original exception; host/join/resume integration; assertions that all actual platform factories retain this helper wiring. Final author diff and raw execution receipts remain separately reviewable.

## Hygiene

No production/test/configuration files edited, no builds or background workers started, and no Git history/index/ref mutation performed by this reviewer. Only compact independent evidence was written under this dedicated directory. No task-owned generated build outputs or archive downloads remain.
