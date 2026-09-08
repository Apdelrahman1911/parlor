# SN-C1 / SN-C2 / ST-C1 — first implementation and self-review

Author `/root/session_cont`; separate validator pending. Baseline main3625d0663ba6eb51338cbd5f9dc45f859ec18846. Changed hashes/ranges are in implementation-source-receipt-01.json. Existing AGENTS.md/audits/user files and other agents' changes remain intact. No Git writes, builds, compilers, Xcode, device/Store/signing or process operations performed by this author. `git diff --check` returned0; that is whitespace evidence, not test evidence.

## SN-C1

The validated committed-result branch now exits the receive deadline before sending its best-effort acknowledgement. The same bounded send helper, typed protocol rejection and structural cancellation behavior remain. No wire DTO/version, reducer, content, private projection or snapshot changes. A first/eager snapshot can already be missed during handoff; existing peer coordinator recovery still owns that path and the new tests include duplicate-commit ACK/revision-zero installation.

Nine new deterministic common tests use actual awaitAuthoritativeSessionStart, structurally valid exact4.2 offer/header IDs, virtual99/100ms receive timing and10/20ms ACK timing. They cover slow, failed, thrown and stalled ACKs, caller cancellation, missing commit, unrelated start, incompatible version and subsequent coordinator recovery. No test has executed yet. Rerun full session suite (including existing owner/lifecycle/revision-zero cases), not only the new class.

## SN-C2

The host's try/finally now retains ownership through registration and successful-result construction. On failure/cancellation the existing NonCancellable room/kit teardown executes; ownership transfers only at a non-suspending successful return boundary. AppLifecycleRoomCoordinator clears an incomplete exact registration before releasing its transition mutex, preventing a queued foreground from selecting an unreturned host during asynchronous goodbye cleanup. An explicit ensureActive catches cancellation even after a native NonCancellable callback returns normally. State-mutex-only roomClosed remains reentrancy-safe and generation-scoped.

Five new actual-transport/fake-kit tests cover the confirmed rc3-shaped stop barrier with queued foreground/replacement host, ordinary registration exception, cancellation during kit.start/advertising and successful ownership/idempotent Leave. Three common coordinator tests cover cancellation/failure rollback and cancellation while waiting behind an existing owner's transition. No protocol/credential/game logic changes. Existing fake helpers and root-owned signature corrections were not edited.

**Sibling uncertainty sent for independent review, not silently counted/fixed:** accepted join/resume construct process-owned peer collectors before suspending final handoff/registration, while their outer failure finally stops only the kit. Pending independent review must determine whether matching peer-room closeForRetry is also required; this implementation makes no claim those sibling paths are fixed. Physical Bonjour/socket cleanup remains unexecuted.

## ST-C1

Existing Documents/snapshots is excluded before cold inventory (outside per-record catch) and every direct read/migration. Malformed/oversized originals are not destroyed or repaired. Exclusion is reapplied rather than cached; the shared native setter now verifies its result using a fresh NSURL to avoid setter-cached metadata. Missing keys/setter/readback failures fail closed with sanitized errors. An internal excluder callback permits deterministic protection-failure tests; default platform behavior is real Foundation. Current output location, safe filename checks, atomic encrypted writes, version/AES/HMAC/name binding/Keychain ownership, bounds, recognized-magic precedence and explicit Discard implementation remain unchanged. Discard does not call legacy exclusion, so a legacy-only protection failure is not an obstacle to removal.

Eight new native tests check the two original failure forms, direct read, oversized preservation, failed exclusion on list/read with successful Discard, repeated exclusion attempts, nonexistent path failure and actual temporary-directory recreation. All content is synthetic UUID-named data; cleanup removes only its own files, never the shared snapshot directory. The eight tests require no Keychain. Existing native safety6 and common migration/protection tests should also run.

The source path prevents backup eligibility after successful exclusion; it cannot prove a real backup transfer, deletion of historical backups, actual old-version release incidence, real device Data Protection or healthy signed Keychain roundtrip. Those remain separate gates. Failure to set OS exclusion necessarily leaves bytes retained and exposes an explicit storage error; it is not falsely reported as protected or fixed externally.

## Required next steps

Root-owned focused/full session+transport lane, native iOS storage selectors in a disposable simulator, Detekt/platform compilation, then independent remediation approval against these exact diffs and test receipts. Build cleanup/Gradle stop belong to root's mandatory finalization lane. Do not claim completion from this self-review.
