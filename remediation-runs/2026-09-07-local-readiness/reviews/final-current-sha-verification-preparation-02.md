# Final-current-SHA verification preparation

Reviewer: `/root/release_fix_review`. Read-only preparation, not a gate execution or final readiness approval. Recorded: 2026-09-08T06:27:04.082986+00:00.

## Current checkpoint

- Last inspected native14 receipt identifies `8b9e3b0cdfdab82e7cff3ad8135d0eae9a0b8ca3`, tree `c1e92b5e00ab64a293c4d7f9c9b5e2b85887be88`, shipping/source-manifest `c4d3387a95ff1a4462071147afe92411f5a08ee8b10f78852a59494c8d3e4420`. This is **not** a new clean-worktree attestation: its final tracked diff includes in-progress normal-image controls. Root reports a seven-file normal patch now in focused review/controls.
- Native14 is **FAIL**, not a pass to rebind. Root reports the actual `legacy10continue` failure is being diagnosed. This report neither invents its cause nor independently adjudicates that native result. Keep L07/L08 open until root's separate runtime reconciliation.
- Old CI **34152138368/attempt1** really completed all five jobs at **89dbe8aeaf4e2c83f491521629982be09706cf67**. Read its C05 supplement as well as the initial C01–C04 report. Do not repeat historical claims that qualified Apple or managed Android tests never ran, or call that old SHA a new final run.
- Dependency controls04 actually executed **16/16**, no failures/errors/skips, with cleanup. The repaired schema fixture is not an outstanding defect. The fresh frozen-candidate graph/export/render/consumer chain remains outstanding; its initial DRAFT README is historical wording, not current control-execution evidence.

## Freeze and inventory order

1. Finish and independently review the root-adopted normal-image and L08 work, including newly required tests/control files. Keep failed receipts intact. Settle the current seven-file normal patch and any ensuing factory correction **before** final freeze; this is sequencing, not permission to commit additional work.
2. Under root's existing scoped authorization, commit only intentionally adopted source, resource, configuration, documentation and reproducibility controls. Required tracked control changes count even when outside shipping source sets. Preserve unrelated modifications, untracked user work, design assets, stashes and original audit evidence. Audit reports need not all be staged: either intentionally adopt selected evidence before regeneration or retain it separately and hash-bind it. Do not blanket-stage/exclude an entire audit tree.
3. Review build-consumed untracked inputs explicitly. The exporter rejects untracked inputs under composeApp/shared/game-modes/build-logic/gradle/config/iosApp. Do not evade this by deleting or silently staging user files. The normal lane's source manifest excludes audit/design/remediation roots, so separately pin every actually executed control in those roots.
4. **Source/control commits first; inventory regeneration second; inventory-only commit last.** The generator enumerates `git ls-files --cached`, but per-file references come from `BASELINE..HEAD` history. Staging alone fixes enumeration, not post-commit history references. Preserve an old generated CSV if it is needed by prior evidence. Root commands:
   ```text
   /usr/bin/python3 -B scripts/generate_review_inventory.py
   # root reviews/adopts only the generated inventory change
   /usr/bin/python3 -B scripts/generate_review_inventory.py --check
   ```
   Run the final check after the inventory-only commit. It is a mechanical freshness check, never line-by-line audit proof. No source/control edits afterward without invalidating/rebinding the affected final run.
5. Record final full SHA/tree, branch, index/working diff identities, untracked build-input dispositions, complete source and separate runner/control manifests, dependency pins/toolchain, and baseline-user-work preservation. Retain exact failed-preflight statuses; never let a succeeding later command mask them.

## Minimal final execution packets — root sole lane

### A. Existing five-job verification workflow

No new generic CI reader is needed. Root owns new run/job/artifact IDs and exact-SHA binding. Once authorized source is frozen and available remotely, dispatch only verification:

```text
gh workflow run production-verification.yml --repo Apdelrahman1911/parlor --ref fix/local-readiness-2026-09-07
```

Confirm actual `head_sha`, tree/checkouts, attempt, event and all five job conclusions. Do not rerun the old run or overlap a needed same-ref run: workflow concurrency cancels the previous one. No publication workflow, Store credentials or identity changes.

The current workflow already runs productionCheck + productionStaticAnalysis + productionReleaseAutomationCheck + allTests on Linux x64, managed Android release smoke; productionDesktopCheck on Linux arm64/macOS x64/Windows x64 with host-specific distribution/resource tasks; and actual simulator runtime + productionAppleCheck + Swift UI + unsigned Release wrapper on qualified Xcode26.3/17C529.

Keep **five main artifacts and five cleanup artifacts**, raw job logs, source/toolchain receipts, task outcomes and XML/XCResult descriptors, Android artifact+notice+manifest receipts, Apple unsigned package+notice+identity receipts, upload IDs/digests, immediate Gradle-stop/ownership/final-cleanup records. Bind downloaded bytes to API digests and the new run/attempt; use fresh bounded destinations and preserve only needed compact results. Reconcile manually with compact scripts as root directed. XML restored FROM-CACHE/UP-TO-DATE is not newly executed runtime. If fresh runtime is required but cached, run only the affected clean-output no-build-cache suite in the owned lane—not every unrelated aggregate again.

Safe existing producers: `android_release_artifacts.py --root ROOT --package AAB --bundletool PINNED_JAR --dexdump PINNED_TOOL`; `ios_release_artifacts.py --app OWNED_APP --source ROOT --json`; `third_party_notices.py` as already wired in CI. Reuse their source-bound current receipts; do not claim raw remote binaries were independently reinspected when only producer inventories were retained. No local duplicate full R8/static/Apple build is necessary solely to repeat exact-SHA gates supplied by fresh CI.

### B. Fresh dependency-input chain (not supplied by workflow greens)

Use **new** cycle names and new direct-child output directories, with the current reviewed `run_gradle_cycle.py` wrapper:

```text
/usr/bin/python3 -B C/run_gradle_cycle.py final-dependency-export-NN writeResolvedDependencyInventory -I scripts/verification/resolved_dependencies.init.gradle -Pparlor.dependencyInventoryDir=ABS_C/evidence/final-dependency-export-NN/raw-graphs
/usr/bin/python3 -B C/run_gradle_cycle.py final-dependency-render-NN --command /usr/bin/python3 -B scripts/verification/dependency_inventory.py C/evidence/final-dependency-export-NN/raw-graphs ABS_C/evidence/final-dependency-render-NN/report
```

Here `C=remediation-runs/2026-09-07-local-readiness`; `ABS_C=/Users/abdelrahman/Projects/parlor/` plus C. Both cycle receipts must match final source **before and after**, strict verification and successful cleanup. Require exactly four graphs, COMPLETE manifest/digests, no unresolved metadata, artifact pins/variants, and inspect differences from historical graphs rather than hardcoding old counts.

Reuse `reviews/dependency-candidate-consumer-03/candidate_consumer.py`, not archived hardcoded `validate_dependency_sbom_02.py`. Make a new independently reviewed binding for final SHA/tree/empty diff/source-manifest, export/render receipts, graph/report paths, schema directory, lane and consumer hashes. Its CLI is `--root ROOT --binding NEW_RELATIVE_BINDING --binding-sha256 REVIEWED_SHA256 --output NEW_RELATIVE_OUTPUT`. Use the reviewed jsonschema4.25.1/referencing0.36.2 and owned ephemeral rfc3987-syntax1.1.0/lark1.2.2 provisioning. The existing `run_schema_consumer_controls_01.py` is a **no-argument synthetic-test driver**, not the candidate execution command; root must supply/review the thin candidate execution wrapper if absent. Do not globally install packages or waive encountered schema formats. Preserve four SBOMs, exact declaration/POM evidence, source-notice result and bounded consumer receipt. This is resolved-input integrity, not a final-binary/legal certificate.

### C. Native and final preservation

Finish root's separate L07/L08 native work using freshly approved source/control bindings and owned simulator/copy finalization. A successful harmless-child vmmap probe, eight successful XCTest launches, or earlier health probes do not themselves satisfy the failed ninth mapped-image or failed legacy continuation check. Actual final-current-source success or precisely scoped remaining failure must be reported.

After every packet, preserve evidence, immediately stop Gradle, remove only attested generated outputs/DerivedData/scratch, stop again if cleanup starts Gradle, and verify owned workers/output absence. Finish with root's baseline-user-work comparison and final diff/control equality. This reviewer started no builders, tests, native/server workers or temporary artifacts; no root process/output was touched.

## L01–L13 reconciliation target

| Gate | Smallest remaining final evidence |
|---|---|
| L01 | Settled source/control freeze and inventory check above. |
| L02 | Fresh same-SHA C01 aggregate and actual task/test outcomes. |
| L03 | C01 allTests plus C05 native runtime descriptors. Retain KGP fresh-host standalone isolation distinction; not local owned-init UUID evidence. |
| L04 | C05 Apple analysis + all three release links. Retain explicit device/x64 raw framework hash/architecture gap if those separate receipts are still required; sim-arm64 package inventory does not prove them. |
| L05 | C01 unsigned AAB, full DEX/resource/native/manifest/notice producer inventory and exact artifact binding. |
| L06 | C05 unsigned Swift Release wrapper/full package inventory; linkage or Debug alone is insufficient. |
| L07 | Root's normal-source eight cold launches plus separate ninth mapped-image proof at current bindings. |
| L08 | Root's settings/storage/legacy/continuation runtime reconciliation; native14 FAIL is not superseded by CI. |
| L09 | C01 canonical managed R8 release runtime already supplies this scope; do not require redundant local ARM64 alternative. |
| L10 | C01 release controls/inventory/actionlint/ShellCheck; retain valid historical synthetic timestamp-positive evidence with relevant byte-equivalence, no new external timestamp probe just to change its date. |
| L11 | Separate packet B plus packet A final notice/package delivery binding. |
| L12 | Reconcile original16 and subsequent regression descriptors inside C01/C05 plus their scoped native controls. Doctor ON >=3-night/OFF/skip/self/history tests, current content/digests, protocol/privacy and resolved modal policy remain required—not new speculative repairs. |
| L13 | Every packet cleanup plus final source/control/user-work equality. |

These are overlapping gate targets, **not thirteen independent build batches** or a percentage denominator. D01–D03 physical LAN/accessibility/backup and O01–O04 identity/signing/Store/legal/editorial remain separate owner/device requirements. The known identity collision and publication-disablement protections stay intact.

## Reopened contracts and historical evidence

Complete reads: generator1–409, inventory tests1–173, review README1–50, workflow1–669, root build1–204, release validator1–104, exporter1–107; consumer/lane/inspector CLI and freeze paths reopened as referenced above. Generator SHA256 `c65ec66aa1ea18e91ddae521de4feda15a08ecffa024f06b8dbde0c1999c5a52`; workflow `f823182501d7a28dd20d117781b59dd46bc5103baef5b016316a5a672a18258f`; exporter `5120cb8d7c12825ad889f23d73075ef29ae48b66e4966d1e2335aa2d11995f59`; renderer `fc0238d4d6ea0abd60d2985ed214af6516a239e439269978139fcc9519a20534`; lane `5c0e546c819c28762cbc6b355ec2717737fc53f96712af591a27add878579d7a`; consumer `eb6e2baaac22739b4652bc63c6b57a24374ee57fb81d4711b981b872e8ac1c7f`.

Reuse evidence methods, not old PASS labels: `fresh-ci-34152138368-independent-01.json`, its `fresh-ci-34152138368-c05-independent-supplement-01.json`, `ci-apple-native-cleanup-evidence-supplement-01.json`, `ci-final-run-cache-evidence-supplement-01.json`, `dependency04-vmmap-native01-result-independent-01.json`. Old pending/in-progress statuses in these reports remain historical. Current native outcome was only status-read here; no new runtime approval is asserted.
