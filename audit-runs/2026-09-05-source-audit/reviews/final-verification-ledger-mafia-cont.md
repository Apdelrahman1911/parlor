# Independent final-verification dossier reconciliation — initial version

Reviewer: `/root/mafia_cont`. Source: `main`, commit `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`.

This is an independent administrative/evidence review, **not a new application-source approval, new test execution or final campaign-cleanliness certificate**. The parent exclusively owns the build/device lane. No production/configuration/Git changes, build, app, test or cleanup process were performed by this reviewer.

## Frozen review inputs and reading

The complete original four deliverables were read line by line. Exact original bytes are retained under `evidence/final-verification-review-initial-mafia-cont/`, so parent regeneration cannot invalidate these receipts.

| File | Lines read | SHA-256 |
|---|---:|---|
| `assemble_verification.py` | 1–123 | `7f4a161e3574d9a620a050f4da40a1ff629868ba642ede571b626621aee3c132` |
| `VERIFICATION.md` | 1–64 | `8a34feb2706dcd8e17ef0e5cb69332eee2e151c88409fab50daef393a00df436` |
| `verification-ledger.json` | 1–843 | `8d56df16882ae800c5c8ab747bb2e096c7b88648c321ce6a1dbcf4870b36f918` |
| `CLEANUP_LEDGER.json` | 1–1991 | `db2636ab0530b049c59bcbae96bd5ef6626e56c5eb46b6ae02ebadda53e246ac` |

The original125-line execution/cleanup reconciliation and40-line native-storage supplement were reopened. Original raw receipts remain authoritative; their hashes, projections, test results and retained logs were independently compared rather than trusting summary prose. This adds no new coverage credit for underlying first-party application lines.

Machine comparison: `evidence/final-verification-ledger-mafia-cont.json`. It contains input/ref hashes, every primary/companion projection comparison, per-cycle JUnit totals, omitted task lines and correction qualifications, not duplicate full test logs.

## Gate and reference consistency

- **43 initial gates:16 PASS,13 FAIL,10 BLOCKED,4 NOT_APPLICABLE.** All43 Markdown gate rows exactly match the JSON; the ten manual actions correspond to blocked gates.
- All77 distinct directly linked evidence paths currently exist. The source-preservation reference is present, but the separate final hygiene receipt was not yet produced at this review boundary.
- `candidate.clean=false` honestly preserves untracked user/audit work; it does not contradict unchanged tracked source. Empty candidate `artifacts` means no approved/retained release candidate, not no generated test/build bytes.
- FAIL rows explicitly separate reachable source proofs, deterministic failures and witness observations. Thirteen failed grouped gates are **not thirteen unique application defects**. Counts must come from the independently adjudicated canonical register.
- No unexecuted device/Store/Android-managed gate was made PASS. Database/account/timed-Mafia/Desktop-Store exclusions cite inspected source/build scope rather than treating unexecuted applicable checks as inapplicable.

## Execution evidence rechecked

**759 retained JUnit XML files** were reparsed across the16 primary cycles. Case metadata, failures, skips and suite counters exactly match every available saved `test-receipts.json` and prior reconciliation totals. This is report parsing, not running tests again. Retry/platform counts must not be summed as unique test bodies.

| Cycle/scope | Actual retained result | Qualification |
|---|---|---|
| Focused storage01 | Command0;11 retained cases | XML collector failed; no JSON/full suite count inferred. |
| Focused storage02 | 31 passing | Complete focused Desktop run. |
| Desktop baseline | 1108 descriptors;1107 passing,1 skipped | No physical transport execution. |
| Transport discovery02 | 212 descriptors;211 passing,1 skipped | 222 compiled annotated methods; ten nonvoid methods omitted. |
| `productionCheck` | Desktop1108/1skip; app Android76 each variant;130 Python tests | Includes lint/R8/unsigned AAB, not managed runtime/signing/Apple. |
| `allTests` + ARM64 simulator | 2285 descriptors;2284 passing,1 skipped | Desktop1108; Android392 each variant; iOS393. |
| Apple02 | Three Release framework links | No runtime tests; Xcode26.5 not qualified26.3. |
| Checked-in XCTest | One passing English test | EN/AR screenshots are supplemental, not extra tests/game/gesture coverage. |
| Storage native01 | Three cases:two witness passes,one expected failing assertion | Actual production K/N filesystem with synthetic fixtures; not backup/crypto/Keychain/device proof. |

The19 selected unexecuted task outcomes match exact retained Gradle log lines:13 host-disabled `iosX64Test`, two Native Mafia/engine tasks skipped following NO-SOURCE, and four Android Mafia/engine NO-SOURCE variants. Native Mafia/engine zeroes are not passing tests. Whodunit has nine native tests versus288 Desktop tests.

Three physical `@Ignore` methods exist; one is discovered skipped and two are additionally omitted by invalid nonvoid signatures. Eight enabled omitted method bodies later passed isolated wrappers; ROOT-T3 registration remains unfixed and no physical method ran. Release lint retains32 accepted warnings:OldTargetApi1,AndroidGradlePluginVersion4,GradleDependency3,NewerVersionAvailable24.

The initial ledger preserves harness failures rather than calling them app crashes: early source-set/init hooks, UI-fixture compilation, audit3g heap OOM and unsigned native-probe launch denial. Intentional safety-assertion failures and native-equivalent witness passes retain their separate limits. The DS-C02 later expiry assertion was never reached; timer nonrestart remains source-level proof. The IOS-R1 native Keychain observation does not attribute the actual KMP Home warning.

## Cleanup evidence rechecked

- All **16 primary records** reproduce the exact original receipt fields and hashes; all nine native/probe/companion projections also match. No projection/hash mismatch was found.
- Raw primary command status is seven PASS/nine FAIL; **all16 cleanup outcomes PASS**:wrapper stop0, no cleanup errors, no remaining recorded task outputs. Stop completed0.244456–0.605662seconds after each primary Gradle command finished.
- Original26 receipts plus storage primary/simulator supplement make28. The generated cleanup ledger has25 records intentionally:binary `execution-receipts.json`, binary `cleanup-receipt.json`, and list-root public P2pKit retrieval metadata are not selected dict-root `*/receipt.json` build/native records. Their unchanged hashes and separate reconciliation remain preserved; this is not three missing build cleanups.
- Exact task-generated directories were removed; no broad clean/reset or global-cache deletion. Native/Xcode companions record owned-device/temporary-output cleanup, including immediate Gradle stop after the Xcode build phase. Missing process data is not guessed from absent JSON fields.
- Prior process scans can include unrelated Gradle9.x/Kotlin daemons. Wrapper8.13 stop logs do not establish every user daemon was stopped. This reviewer did not terminate or inspect root's currently active build lane.
- These are completed-cycle receipts, **not** a final-process/output scan. A new Android-managed cycle, if attempted, must be added before claiming campaign-wide cleanup; the initial hardcoded16 count is not future-proof evidence.

## Required administrative corrections

1. **Bind preservation PASS to inspected final evidence.** Initial `assemble_verification.py:24` emits unconditional PASS without reading the linked `final-state.json`. The existing12:20:41 receipt records634 unchanged fingerprints, unchanged refs/stashes/non-audit inventory and exclusions, but the final campaign scan is still pending and additional work may follow. Parent accepted an evidence-conditioned gate/final receipt. Do not claim a broken link:the currently linked source receipt exists.
2. **Correct no-artifact wording.** Initial generator121 / Markdown64 says no release artifact was generated, contradicting the unsigned AAB and Release frameworks that were generated, hashed and cleaned. Parent accepted wording distinguishing no signed/releasable candidate retained or approved. This is an audit-description correction, not an additional app defect.

No unauthorized source remediation is proposed or performed. The two corrections are pending a revised-version check; this review deliberately preserves the erroneous initial text as historical evidence rather than silently replacing it.

## Review-helper limits and continuation

A read-only metadata probe initially assumed the incomplete first storage run had a JSON test receipt; the absent file caused exit1 and was then handled as the already-known evidence gap. A Markdown comparator initially discarded two rows rather than one header; after correction all43 rows matched. Neither helper error is an application/dossier finding, and neither generated build outputs.

Next:reopen the revised generator/ledgers, review any added managed-Android original receipts and cleanup, and inspect final preservation/hygiene evidence. Record a separate supplement; do not reinterpret these frozen16-cycle results as later execution. No READY verdict is supported while confirmed defects and explicitly unexecuted/blocked gates remain.
