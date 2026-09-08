# Combined production gate — independent evidence approval

**APPROVED for its actual host-independent scope.** Reviewer `/root/release_fix_review`; root owns implementation and execution. Read complete1,577-line raw log, parsed all216 raw JUnit reports/cases and43 Detekt reports, inspected lint/manifest/artifact receipts, and reopened actual Gradle wiring plus changed test blocks. This is not a new exhaustive source audit or a global READY verdict.

## Exact verified identity

- Branch `main`; commit `3625d0663ba6eb51338cbd5f9dc45f859ec18846`; Git tree `db7f3d2afe73a13628296daee2cce71165eebc8d`.
- Dirty source manifest: **658files**, `e59da533dbec2f02f6f2b460ce72e2a7337af1e44abfb0bc6d533304d4127ec7`.
- Tracked binary diff SHA-256: `60b555c0dd323a086e959ffbc5aa3fd7c6629ae1d0ad016021572e7ae272e4dd`.
- Before/after/current source hashes agree; all branches/refs and stash list match remediation baseline. Existing `AGENTS.md` unchanged.
- Exact command and input hashes are in companion JSON. `productionCheck --no-build-cache --continue` ran with strict dependency verification, JDK21 selection, no parallel builds, one worker, in-process compiler and signing credentials removed/empty.

## Independently reconciled results

| Gate/evidence | Result |
| --- | --- |
|JVM reports|**1,375 descriptors: 1,372PASS,0failure/error,3skipped**,216suites. Counts match every raw testcase/header and compact receipt; no duplicate task/class/method descriptor.|
|Desktop|13modules:1,223descriptors,1,220PASS,3physical-loopback skips.|
|Android app JVM unit tests|Debug76PASS, Release76PASS; not emulator/device execution.|
|Freshness|All15test tasks freshly execute; no FROM-CACHE label anywhere. Final Gradle actionable summary:826executed,77up-to-date. Actionless/preparation task labels are a different denominator; do not claim literally every task reran.|
|Detekt|43plain/host-type-aware XML reports;0findings. Apple-specific type analysis is separate.|
|Android release lint|**32warnings**, exactly matching unchanged accepted-warning multiset. Not zero warnings.|
|Android packaging/security contracts|Fresh R8, unsigned AAB, full lint, lint inventory, merged-manifest and Debug/Store identity tasks complete.|
|Release automation|**172/172Python tests PASS,0skip**, workflow contract, inventory freshness, mandatory checksum-verified ShellCheck/actionlint succeed.|
|Generic game shell|Fresh `verifyGameShellDispatch` completes.|
|Aggregate|`productionCheck` exit0 / terminal receiptPASS, source stable.|

Raw XML timestamps22:27:38.555Z–22:28:38.841Z fall within the22:24:12–22:30:19UTC cycle. Test stderr contains only three SLF4J no-provider/NOP notices. Log also retains Gradle-version/experimental-lint/iOS-X64-host warnings, two R8 provider warnings and the unstripped graphics-path-library notice; none is hidden by this approval.

## Skips and artifacts: precise boundaries

Three `P2pKitRoomTransportLoopbackTest` peer tests remain explicitly ignored: membership/join, bidirectional exchange, and host broadcast. Source requires two/three physical same-LAN devices because single-JVM multicast is unreliable. The executed host advertisement test is **not peer-communication proof**. Counts are executed descriptors, not distinct assertions or exhaustive behavior coverage.

Root's compact artifact receipt records an **unsigned8,802,894-byte AAB**, SHA-256 `d046d2a2ed9856d5e77888acc9a31a41dd46d1bd64faa3585a16008be8d3f01e`,354ZIP entries, oneDEX and graphics-path libraries for fourABIs. No JAR signature entries are recorded. All seven shipped story lengths/CRC32 agree with current authored files; that is inventory consistency, not chronology approval or cryptographic content verification. Reviewer inspected retained merged manifest and receipts, **not AAB bytes after their required cleanup or decoded DEX**. No complete signed-artifact/bundletool/Store gate is inferred.

Workflow/policy/protocol4.2 files, Gradle wrapper/catalog/verification metadata, accepted lint inventory, versions and Detekt policy match baseline hashes. Application IDs are still deliberately collision-blocked; publication remains disabled. Synthetic signing/fake Store clients do not establish credentialed signing, upload or promotion readiness. The tracked/history-derived628-row inventory gate is not dirty-tree line-review coverage.

## Follow-up source review and cleanup

The five source deltas since the earlier broad-release receipt are only locale structural-test/input registration, explicit Mafia fixture types, a typed reference to the real suspending Whodunit loader, and a test-owned toast dispatcher. Changed blocks reopened and author-independent approvals/source hashes cross-checked; JSON references the native/factory reviewers. This does not replace their full issue-level reviews or resolve DS-C01's stated native/provenance limits.

Terminal receipt retains stop exit0 at22:30:20.307UTC; exact task-owned module/root/build-logic outputs removed by22:30:22.205UTC; no retained outputs, remaining owned workers, cleanup errors or deferred signals. Cycle scratch is independently absent. The reviewed finalizer stops before collecting/cleaning and never starts another Gradle daemon for cleanup. Root's later native/Apple lane is separate; reviewer did not touch its outputs/processes.

No builds, app/test workers, source edits, Git mutations or Store operations were performed by this reviewer. Only compact owned review evidence was written. Initial read-only Homebrew-Python XML parsing failed on host libexpat linkage; systemPython3.9.6 parsing succeeded and supplied the above independent reconciliation.

**Conclusion:** accept combined-production-04 for the gates it actually runs. `allTests`, Apple linkage/runtime/static checks, Android instrumentation, physical LAN/privacy/accessibility, real signing/Store/legal requirements and unresolved product decisions need their own evidence. Do not derive “16/16 fixed,” “no other bugs,” or Store readiness from this green aggregate.
