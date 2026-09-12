# SN-C2 — independent remediation review (final scoped approval)

**Reviewer:** `/root/factory_review` (not a fix author). **Authors:** `/root/session_cont` (host/peer/coordinator), `/root` (factory). **Verdict:** **APPROVED for the scoped source correction and deterministic JVM verification.** No unexecuted native/platform gate is declared passing.

Baseline: `main` / `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, Git tree `db7f3d2afe73a13628296daee2cce71165eebc8d`, dirty remediation checkout preserved. Repository root: `/Users/abdelrahman/Projects/parlor`. The companion JSON gives absolute paths, current SHA-256 hashes, honest reviewed ranges, exact command/source bindings, all test names and receipts. Scoped reviewed manifest: `265f570510cd249f30b6f881ef79e35b2b0a4a44ce6c129e07f252427711a885`.

## Original defect independently rechecked

The original host source, original candidate and root validation were reopened. Archived raw XML `audit-runs/2026-09-05-source-audit/evidence/repro-ui-transport-02/.../TEST-com.parlor.transport.p2p.SN_C2HostRegistrationCancellationTest.xml` contains one executed failure: expected one kit stop after cancellation, actual zero. This is original-defect evidence, not a repair PASS. Both shipping host flows route through process ownership, whose cleanup cannot see a room that transport never returns.

The related factory ownership hole was independently confirmed before repair in `SN-C2-factory-independent-proof-01.*`: P2pKit rc3 constructs parentless collectors/eviction workers and takes a secure-identity lease before `start()`. Coroutines1.11.0 can discard a successfully constructed result on cancelled `withContext` completion, including same-dispatcher construction. Actual rc3 and coroutines tagged sources were fetched independently; precise URLs, dates and hashes remain in the research records.

## Correction and surrounding-path review

- `P2pKitRoomTransport.kt:259–337` now owns the host kit/room through lifecycle registration and success diagnostics; unreturned ownership closes NonCancellable. Actual host acceptor ownership, registration/background behavior, grace expiry and complete `leave` path (`1702–2248`, `3230–3589`) were traced.
- `AppLifecycleRoomCoordinator.kt:49–75` rolls back the exact failed/cancelled registration while still holding the transition mutex. Queued foreground cannot revive the abandoned room; its independent state mutex allows the room-close callback without reentrant deadlock. Cancellation before lock acquisition leaves the previous room intact.
- Initial join/cold resume (`P2pKitRoomTransport.kt:343–624`, `702–839`) retain `openingRoom`. Their new finalizer (`1607–1629`) closes the **room**, not only the kit. The full peer implementation (`3636–4811`) was read: close-for-retry cancels owned collectors/resume/expiry jobs, closes the session/channel/kit, and removes only matching lifecycle registration. It sends no LeaveNotice and does not revoke a valid committed credential; newer replacement credentials survive the abandoned timer window.
- `P2pKitCreation.kt:1–39` keeps construction ownership before dispatcher return, waits for NonCancellable stop when that return is cancelled, and rethrows the original failure. All three actual factories were fully read; Android's initialization lock, original dispatchers, identity stores, authentication and lifecycle configuration remain unchanged. They immediately return the synchronously built kit, as the helper contract requires.
- The entire touched production diff and all four new regression classes were reviewed. Large supporting files are not falsely marked fully reviewed: precise read ranges are recorded. Original/recovered exception distinction and sibling close paths were checked, not inferred from test names.

## Executed evidence independently inspected

`evidence/transport-static-green-02/` ran `:shared:transport-p2p:desktopTest staticAnalysis` with JDK21, checked-in wrapper, one worker, strict dependency verification, no signing credentials and exit0. Independently parsed raw XML: **249 executed PASS, zero failures/errors, three physical-test SKIPs**. The **29 SN-C2 regression tests** all executed: factory9, host5, peer12, coordinator3. The separate Jupiter discovery contract also executed. All scoped source/test hashes still match that cycle. Detekt XML contains no findings; the Gradle log completes `staticAnalysis` successfully (unchanged modules may be FROM-CACHE).

Tests cover creation cancellation before/during/after dispatch (same and different dispatcher), cleanup awaiting/failure, live ownership success, host registration/advertising/start failure, peer admission/resume Ready and ACK cancellation, non-cancellable registration with queued foreground, replacement credentials, single cleanup and ordinary lost ACK. These are actual adapter/room/codec/store code with synthetic kit/session/storage fixtures, not physical LAN evidence.

The first factory run's six exception-identity assertion failures were reviewed rather than erased: coroutine JVM stacktrace recovery copies exceptions. The correction preserves ordinary cancellation fixtures and exact original cause identity in a bounded chain, checks suppression at the helper boundary, and leaves resource/lifetime assertions intact. It does not disable recovery or modify production semantics to satisfy tests. Fake-stop comments now truthfully describe the detector as stricter than rc3's idempotent stop.

Earlier `games-session-desktop-03` independently contains session145 PASS, transport240 PASS/3SKIP and Mafia267 PASS, but **the batch FAILED** at Whodunit test compilation. It is supporting session/transport evidence only, not a combined green. The final transport cycle supersedes its factory code identity.

## Compatibility and limitations

No protocol4.2, credential/snapshot/content version, reducer, seed, game rule, projection or admission/identity validation was changed. Failed opening is not explicit Leave. Both games retain the same host-authoritative shared transport, while pass-and-play is unaffected. No new private state or secret transmission/logging is introduced.

Android/iOS actual wiring is source-reviewed; this review does **not** attest their compilation, native runtime or physical radio behavior. Three physical tests remain skipped: two-device join, bidirectional messages, three-device broadcast. Broader platform/combined gates and final documentation-contract reruns belong to root's single build lane. A scoped repair approval is not project/Store readiness or proof of absence of other defects.

## Preservation and cleanup

Both inspected remediation cycles stopped Gradle (exit0), removed only owned generated outputs, and recorded no remaining owned workers/outputs or cleanup errors. The reviewer launched no builds/native processes and changed no application, test, configuration, Git history or old evidence; only these compact owned reports were added. XML parsing used `/usr/bin/python3 -B` after the unrelated Homebrew interpreter's expat import failed. No task-owned workers or disposable build outputs remain from this review.
