# Final session cross-check — SN-C1 / SN-C2

Reviewer: `/root/factory_review` (not a fix author). Recorded `2026-09-05T23:36:10.063447+00:00`.

**APPROVED within reviewed source + executed deterministic JVM scope.** No new scoped blocker established. This supplements—not replaces—the original independent dossiers; it is not a whole-repository or device/Store readiness verdict.

## Exact source and review coverage

- Branch `main`, HEAD `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`; dirty remediation checkout.
- Frozen manifest `e59da533dbec2f02f6f2b460ce72e2a7337af1e44abfb0bc6d533304d4127ec7`. Tracked diff `60b555c0dd323a086e959ffbc5aa3fd7c6629ae1d0ad016021572e7ae272e4dd`, independently recomputed equal.
- Supplemental ledger: 11files, 6complete reads, 2188lines. JSON records absolute paths, hashes and exact ranges; partial files are explicitly partial.
- Full397-line handshake and334-line regression read; actual both-game callers, retained ownership, hostcommit/retry, peer replay/snapshot reconciliation and transportsend followed. Two SN-C2 helpers reopened fully.
- All14source/test/build rows from `SN-C2-independent-remediation-review-02.json` match current bytes, frozen identity and both combined-cycle manifests. Prior line-by-line approval is reused honestly, not asserted as a fresh full reread.

## SN-C1 conclusion

The new ACK location fixes the root cause: a matching, validated irreversible commit returns from the receive-timeout scope **before** its separately bounded best-effort ACK. A slow, dropped, throwing or timed-out ACK no longer converts that committed start to receive timeout. Genuine owner cancellation remains cancellation through the helper, retained operation and merged-flow finalizer. Invalid/missing/foreign commits retain existing failure behavior. Hostauthority, exact4.2compatibility, game-specific preparation, sequence/revision checks, snapshot privacy and formats are unchanged.

The original deterministic witness really executed1test and failed Success-vs-Failure; its draft NOTEXECUTEDcomment is stale. It is defect evidence—not a repair pass. Current9tests assert lateACKsuccess, bounded ACKtimeout, failed/throwingACK, true cancellation, missing/foreign/invalidcommit, duplicateACK and revision-zero snapshot acceptance. All9execute and pass.

Counter-evidence: immediate droppedACK already worked before this patch; it did not cover the deadline schedule. Ordinary simultaneous timer/receive cancellation semantics are not redesigned or claimed race-free. ACK delivery remains best effort, not verified physical delivery. During collector handoff, eager snapshots may be dropped; unchanged actual coordinator attaches its inbox then performs bounded authoritative snapshot reconciliation.

## SN-C2 conclusion

The earlier independent approval still applies to the exact frozen bytes: cancellation-safe factory ownership across allplatforms, post-advertise registration ownership, rollback before queued lifecycle transitions, host/peer/resume cleanup, and preservation of real cancellation/committed credentials. Existing original proof, counter-evidence, SDK research and full covered ranges remain in the SN-C2dossier. No rule/seed/projection/wire change or game logic moved into shared infrastructure.

## Independently inspected execution and cleanup

Root-owned `evidence/combined-production-04/receipt.json` (SHA256`4f8374afedbc3fec40353f0a95556766eaf8fa393daf1f8d307104e05694b9f4`) records fresh strict `productionCheck`, exit0, `2026-09-05T22:24:12.261351+00:00`→`2026-09-05T22:30:19.979017+00:00`; source unchanged. Raw log confirms both desktopTest tasks actually execute and type-aware staticanalysis passes.

- Session: **145PASS**, 13suites, zero failures/errors/skips; SN-C1nine included.
- Transport: **249PASS +3SKIP**, 20suites, zero failures/errors. SN-C2**29PASS** across creation9, hostopening5, peeropening12, registration3.
- All individual XML descriptors/timestamps/hashes and the exact command are in JSON.
- The three intentionally ignored physical join/message/broadcast tests remain skips. Hostadvertisement is not physicalLANproof.
- Immediate Gradle-stop exit0 at `2026-09-05T22:30:20.307635+00:00`; raw stoplog reports no running daemons.15precisely-owned generated directories removed; retained/remaining outputs, ownedworkers and cleanuperrors all empty. Cleanup completes `2026-09-05T22:30:22.205283+00:00`. This records that cycle only—not any later root build lane.

## Research, limits and hygiene

Official coroutines1.11.0 `Timeout.kt` source fetched from the exact tag (URL/access/hash in `sn-c1-timeout-research-01.json`) confirms timeout ownership151–168 and disposal223–234. Its asynchronous timeout caution was considered; no broader undocumented guarantee invented.

No reviewer builds/tests/native tools or appworkers were launched; only compact owned evidence added. Physical Android/iOS lifecycle, LAN, coldresume, actual sockets, platformUI and Store/signing remain distinct gates. Root owns combined Apple/Android execution reporting. No source/config/test/Git edits or cleanup of another task's resources performed.
