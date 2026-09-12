# ROOT-T3 — Non-Unit Jupiter tests silently omitted from discovery

Administrative mirror created by `/root/whodunit_cont` at `/root` request. **Classification is imported only from the existing separate validator's evidence; this is not a new approval.** Finder `/root`; independent validator `/root/session_cont` (who also identified sibling affected methods).

- **CONFIRMED DEFECT — Medium, test registration**, with a resulting **TEST/EVIDENCE GAP**. Not a demonstrated shipping-application failure.
- Source: `main`, commit `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`; root `/Users/abdelrahman/Projects/parlor/`.
- Full independently approved proof, exact locations, hashes, callers, research and counter-evidence: `validations/ROOT-T3-session_cont.md:1–73`.

## Cause, scope and evidence

Eight enabled methods in `shared/transport-p2p/src/desktopTest/kotlin/com/parlor/transport/p2p/P2pKitRoomTransportLifecycleTest.kt` (427–450,452–472,3666–3736,3790–3848,4612–4623,4625–4637,4639–4658,4758–4782) and two intentionally ignored methods in sibling `P2pKitRoomTransportLoopbackTest.kt` (157–205,207–249) infer non-Unit return values from their final `runBlocking` expressions. Exact resolved JUnit Jupiter5.10.1 filters non-void `@Test` methods before execution/disabled reporting.

`evidence/inspect-transport-tests-02/compiled-test-methods.json` shows222 annotated methods and these10 non-void methods; ordinary XML reports212 cases,211 passing and one skipped. This is silent absence, not10 reported skips or10 application defects. Root's inspection receipt and the validator's exact dependency-source analysis establish the registration problem. All affected source is real Desktop test input, not orphan code.

Subsequent root-owned `repro-pending-01` execution used isolated Unit-returning wrappers for the **eight enabled** original methods, preserving their `cancelScope()` teardown in finally. All8 passed with0failures/0errors/0skips. The two ignored physical-network bodies were not executed or unignored. The mixed reproduction batch overall exited1 due to other deliberate negative assertions. Passing these wrappers does not fix ordinary discovery or prove physical LAN behavior.

## Recommended remediation and count policy

After authorization, make ordinary annotated tests explicitly Unit-returning without removing assertions or physical-environment ignores. Add compiled-discovery coverage and verify the expected added descriptors. Keep SN-T1 separate: it concerns stale admission/identity/storage setup inside ignored fixtures, not method discovery. Count this registration root cause once; retain the passing eight-body evidence without claiming an implemented fix.

Original Gradle stop/precise-output cleanup evidence is in both referenced cycles. No builds/tests/production changes were performed while creating this administrative mirror.
