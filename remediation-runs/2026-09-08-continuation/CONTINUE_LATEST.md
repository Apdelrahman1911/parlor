# Continuation execution ledger — 2026-09-08

**25b1 execution checkpoint; native investigation underway; NOT READY.** Read `AGENTS.md`,
[`CONTINUE_HERE.md`](../../CONTINUE_HERE.md), the
[Linux handoff](../../docs/AGENT_CONTINUATION_2026-09-08_LINUX.md) and the preserved
[transfer handoff](../../docs/AGENT_CONTINUATION_2026-09-08.md).

This remediation-only ledger is excluded from the ordinary application source
manifest, but tracked ledger edits still change the Git diff. Later updates may
record execution without editing frozen application docs, but cannot waive
commit/tree, controls, inventory or platform bindings. The inventory CSV is in
the source manifest. Never relabel an older CI run as execution at a later
evidence-delivery or inventory commit.

## Repository and freeze state

- Repository: `https://github.com/Apdelrahman1911/parlor.git`.
- Branch: `fix/local-readiness-2026-09-07`.
- Delivered baseline:
  `ec0de52a2c7ff9077482a291dc4fd2591fdea01f`; tree
  `48b099f3cd002a3253f21d2b06a4ece5787e62cb`.
- Reviewed continuation source/control commit:
  `160824e17caa384fa1802a82760638de1f71f2af`
  (`fix(verification): qualify continuation controls for hosted native runs`).
- Subsequent inventory-only commit, **execution HEAD**:
  `25b1c5551aee7630ac22d4b34668dd4b06c71b17`
  (`chore(review): refresh continuation source inventory`); tree
  `629a5d2b699cddb62d61849de3bd185fd4c01378`.
- Inventory: 14,847 rows. Frozen source manifest: 820 entries, SHA-256
  `6e83dcdccc950fde35ccbf8fe2d4c73671a05a5f4a6a3a5068799af86bbd2334`.
  Frozen execution receipts bind an empty tracked diff, SHA-256
  `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`.
- Both commits were normally pushed. The retained native dispatch request's
  `remote_before` matches exact25b1; the coordinator separately re-executed
  `git ls-remote origin refs/heads/fix/local-readiness-2026-09-07` after the
  native/C results and again observed exact25b1. This audit reopened HEAD/tree
  and the local origin tracking ref; it did not substitute that ref for a
  network observation. See `actions/native-evidence-dispatch-01/request.json`.
- The execution below is at25b1 where stated, **not at the next corrected
  source/inventory revision or a final evidence-delivery commit**. New primary-
  error retention and bounded diagnostic implementation is underway. Its
  review, affected controls, new freeze/bindings and refreshed C chain remain.
- No further native retry, diagnostic platform run or `full` dispatch has
  executed at this checkpoint. Combined qualification D remains unexecuted.

Aliases: `C=remediation-runs/2026-09-07-local-readiness`,
`N=remediation-runs/2026-09-08-continuation`.

## Executed Linux evidence

All paths in this table are `$C/evidence/<cycle>/`. Each retains `receipt.json`,
raw logs and stop/cleanup evidence. The first five are working-tree-bound cycles;
the sixth actually executed at clean25b1. None is qualification at a future SHA;
repeated test counts are not unique coverage.

| Cycle | Result and scope | Independent review under `$N/reviews/` |
|---|---|---|
| `continuation-linux-lane-controls-01` | 21 controls PASS; real Java/Javac21 probes. Initial test-file byte-binding limitation retained. | `gradle-lane-portability-evidence-independent-01.json` |
| `continuation-ci-controls-01` | 79 workflow/native-adapter controls PASS, no skips. | `native-continuation-controls-independent-01.json` |
| `continuation-transport-contracts-01` | 22 XML suites: 274 PASS / three existing single-JVM loopback skips / zero failures/errors. | `transport-contracts-evidence-independent-01.json` |
| `continuation-focused-controls-01` | **FAIL**, 358 tests: 356 PASS / one failure / one error. All nine commands ran. | `focused-controls-evidence-independent-01.json`; `focused-controls-01-packet-and-l08-evidence-review.json` |
| `continuation-l08-control-recheck-01` | **PASS**, corrected single-tap14 + unchanged functional-copy12, no skips/errors/failures. | `l08-control-recheck-01-independent-postrun-review.json` |
| `continuation-release-validator-01` | **383 PASS** at25b1, zero failures/errors/skips; actual release validator, inventory freshness and pinned ShellCheck/actionlint. | `release-validator-independent-01.json` |

Focused passing scopes: packet16, lane21, schema-bootstrap26, toolchain12,
composition55, normal187, schema-closure16. Schema closure also produced actual
`PASS_OWNED_SCHEMA_PREREQUISITES` and `PASS_SYNTHETIC_CONTROLS` receipts, **not a
candidate verification or application-runtime receipt**.

Failed single-tap13 counted the Swift `wait(for:)` label as a second loop. The
correction excludes only colon-followed labels, validates the baseline before
mutation tests and explicitly rejects extra `for`/`while`/`repeat` loops. Actual
Swift remained unchanged. Prior mutation OK labels are not valid substitute
witnesses. Failed functional-copy12 correctly caught changed inherited README
bytes; those bytes were restored and new usage moved to already allowlisted
`L08_FUNCTIONAL_README.md`, without changing the guard or clone manifest.

Independent correction reviews: `l08-single-tap-scanner-correction-independent-01.json`
and `native-portability-doc-correction-independent-01.json`. The fresh26 postrun
review SHA-256 is `50e037f9ba514e44a38612a09737e9654e2a52498d80b66513944aa6e707e2ec`.
Both focused/recheck cycles captured matching before/after 204-input manifests;
bound lane21 now has current evidence without rewriting the first21 receipt.

The frozen383 command was `/bin/bash scripts/release/validate_release_system.sh`,
not `productionCheck` or app runtime. Its raw log records 383 tests, `OK`,
inventory14,847 and successful pinned-tool downloads; the script/review confirms
ShellCheck0.11.0 and actionlint1.7.12 were invoked. Receipt SHA-256:
`54f123f4f092c4b7df674eacb8e916f44c87edc98dba16c7558cdba83375c7df`.

All six outer receipts record source/runner stability, **stop0**, no remaining
owned workers/live outputs and no cleanup errors, including the failed cycle.
The three later C cycles below satisfy the same cleanup conditions: nine Linux
outer receipts in total, not nine application-runtime passes. Required evidence
and failed receipts remain retained. This ledger update ran no build/test/native
worker and did not start or clean another agent's lane.

## A / B — actual hosted Apple evidence and remaining scope

Both runs below used `production-verification.yml` at exact25b1, attempt1,
qualified Xcode26.3/17C529, simulator SDK/runtime26.2 on arm64. Four non-Apple
jobs and full Apple aggregate/wrapper steps were **skipped** by focused mode,
not passed. Full raw logs, API run/jobs/artifact identities, ZIPs, extracted
receipts and download receipts are under `$N/actions/<run-id>/`.

### Successful non-runtime preflight

- Run **34214410629**, scope `native-preflight`: workflow/iOS job **SUCCESS**.
  Receipt status `REVIEW_REQUIRED_NOT_RUNTIME_EVIDENCE`; no application build,
  XCTest, Parlor libproc/ABI or storage/protection validation executed.
- Four-file preflight artifact **10051166213**, ZIP SHA-256
  `a64a699e4dd895b14089c9fcd867d3d2aa4166f745891864918aefc944d9de48`.
  Cleanup artifact **10051167436**; preflight cleanup PASS.
- Independent approval: `reviews/native-preflight-34214410629-independent-01.json`.
  Approved manifest `control_sha256` values, not JSON-file digests:
  L08 `07ee6204dc12ff9fb53253818209893f4de7649f21c3e51cc477bf4157d7322a`;
  normal `8128bafc217e491d09ca0a7b54d70c1cb67ff9392ade958b910933d247394750`.

### Failed A17 / B18 application-verification attempt

- Run **34216788568**, scope `native-evidence`: workflow/iOS job **FAILURE**;
  focused step exit2; outer `continuation.json` status `NOT_READY`.
- Both `ios-readiness-17` (A) and `ios-readiness-18` (B) retain canonical **FAIL**,
  runner exit1 and `TimeoutExpired` for exact
  `ps -axo pid=,ppid=,pgid=,lstart=,command=` after15s. Owned simulator boots and
  attempted xcodebuild commands occurred. This establishes an ownership/process-
  observation failure, **not an application crash or XCTest assertion failure**.
- A's 3,949-byte xcodebuild log reaches initial build-tool metadata and ends
  `BUILD INTERRUPTED`; its command row lacks exit/interruption fields. B records
  xcodebuild exit-15/interrupted, but its 35-byte gzip decompresses to **zero
  bytes**. Do not invent test counts or a Kotlin build phase from these logs.
- Neither establishes app installation/launch, executed XCTest cases, functional
  storage/retained-host operations, Foundation protection rows or a Parlor
  libproc query. B explicitly requested `--image-observer=libproc`, but actual
  `provenance_status` and `notice_package_status` remain `NOT_RUN`.
- Native evidence artifact **10052421289**, ZIP SHA-256
  `531b4ef9dd45d2a0a98b52c6fe7bc1dd2cbc3b431a74a556c2273c6776a8a8a1`;
  cleanup artifact **10052423028**, ZIP SHA-256
  `8471be7152e0a4535ae99fe4e0d4ade975c0564aeaa508d72a10fe6ab194f536`.
  Locally retained canonical receipts are under
  `$N/actions/34216788568/native-evidence/ios-readiness-{17,18}/`, not a claimed
  local simulator execution under `$N/evidence/`.
- Both source/control/copy before-after bindings remained stable. Each immediate
  and final isolated Gradle stop exited0; devices absent, owned processes and
  unknown holders empty, temporary copy/build/DerivedData removed, cleanup PASS.
  B began after A cleanup. Outer upload/custody cleanup PASS; only preserved
  task-owned canonical evidence/binding/lock paths were then removed. Safe
  cleanup does **not** convert either runtime failure to PASS.
- Independent reconciliation: `reviews/native-run-34216788568-independent-01.json`.
  Failure diagnosis: `reviews/native-ps-timeout-diagnosis-01.json`. The first
  failing call phase and OS root cause remain unresolved; a fallible command
  cleanup can mask the primary exception. No launched Gradle build JVM is
  evidenced, so attributing this failure to `-Xmx`/OOM is unsupported.

### Required correction/investigation before a justified new run

Use [`scripts/ci/NATIVE_CONTINUATION.md`](../../scripts/ci/NATIVE_CONTINUATION.md),
not a Store workflow. One coordinator owns dispatches; same-ref concurrency can
cancel earlier runs, so do not overlap focused/full executions.

- Additional **source-only** finding (`reviews/l08-strict-protection-investigation-02.md`):
  paired Foundation template/consumer require26.5.0 while the hosted profile
  selects26.2. Exact trusted-profile propagation is being corrected by
  `native_ci`, with independent review by `portability_review`. A17/B18 never
  reached this guard; it is **not their observed ps failure cause**.

1. Independently review the underway primary-error/call-phase retention and
   bounded no-app diagnostic work; execute affected controls locally. Preserve
   primary and secondary cleanup errors separately; retain FAIL and finalizers.
   Do not edit immutable ownership controls, increase timeouts, use stale process
   rows, accept incomplete observations, weaken protection checks or change the
   qualified toolchain as an unexplained workaround.
2. Commit reviewed source/controls, regenerate and commit mechanical inventory
   separately, check/push/verify the exact new SHA, then create fresh bindings.
   The proposed focused diagnostic compares unchanged bounded15s ps observation
   against bounded public libproc metadata/scoped argv before/after one exclusively
   owned simulator boot, with actual CPU/memory/pressure and ps binary identities.
   It must not retain raw unrelated argv/environment or turn a failed observation
   into permission to signal a process or run A/B. A no-app probe cannot prove
   behavior under Xcode build load. Preserve outcomes before deciding a retry.
3. Obtain fresh source/control preflight approval for any later A/B execution;
   the next adapter identities are being moved to `ios-readiness-19` (A) and
   `ios-readiness-20` (B), pending source review/freeze. Never overwrite A17/B18.
   No retry approval has been issued; diagnostic first. No such retry or
   diagnostic platform dispatch has run at this checkpoint.
4. A still needs 13 functional boots / 20 operations / 26 protection comparisons,
   three retained-host fixtures / 42 operations, Settings/OS/lifecycle/image paths.
   Historical native16 remains FAIL: 14/15 observed functional operations passed,
   boot10 Home callback stayed zero, boots11–13 and retained-host paths were not
   reached; all24 strict Complete comparisons failed. A17 adds no new protection
   observations. The companion never proves original strict L08 PASS. Preserve
   and separately report functional outcomes and strict metadata mismatches.
5. B still needs eight genuine normal-source launches plus a separate ninth native
   libproc image query, actual native return/structure sizes, artifact/signature/
   notices bindings and cleanup. Historical native15's eight launches/48 samples
   passed but ninth vmmap provenance failed; 187 controls do not query Parlor.
   A partial/failure permits B only when A's full ownership/source/control/cleanup
   receipts are safe, not merely because its process exited.

## C — completed fresh chain at25b1 only

Actual cycles under `$C/evidence/`:

| Cycle | Actual result |
|---|---|
| `final-dependency-export-linux-01` | PASS/exit0/stop0; strict `writeResolvedDependencyInventory` with existing init script. Five actionable tasks: four executed, one from cache; the export task executed. androidRelease239 components/129 artifacts; each iOS graph167/81. |
| `final-dependency-render-linux-01` | PASS/exit0/stop0; actual production renderer: four graphs, 456 Maven components, unresolved `[]`. |
| `final-candidate-input-linux-01` | PASS/exit0/stop0; actual owned schema prerequisites and approved candidate consumer, **not synthetic controls or app runtime**. |

Candidate binding `$N/candidate-input-binding-01.json`, SHA-256
`d3d1a6d0c3345650e6eeb7f7a473e322a6c122a8e7036684cf701f5977de7fad`,
was independently approved before execution. All four coupled receipts passed:

- `candidate-input-verification.json`: `PASS_SCOPED_CANDIDATE_INPUTS`.
- `candidate-prerequisites.json`: `PASS_SCOPED_CANDIDATE_INPUT_EXECUTION`, exit0,
  no error, optional-package cleanup true.
- `schema-base-prerequisites.json`: `PASS_OWNED_SCHEMA_PREREQUISITES`, inner0,
  candidate mode, no error, base-package cleanup true.
- Outer `receipt.json`: PASS/exit0/stop0, no cleanup failure.

Actual consumer result: 456 Maven components, 459 publisher POMs, 1,507,794 metadata
bytes, 26 source-notice resources and **511 consumed files**, consumed-manifest
SHA-256 `d86f4db4329683fd512123a7236645862ae1c5a15cc80f3a5846705f984046d9`.
Registered schema formats were used; actual `iri-reference` checked and missing
formats `[]`. No unit tests or app runtime were requested by the candidate cycle.
All three complete source-before/after objects match clean25b1/820rows, runners
stable, owned parser directories removed, no workers/live outputs/cleanup errors.

Independent reviews: `reviews/candidate-input-binding-independent-01.json` and
`reviews/candidate-input-postrun-independent-01.json`; postrun conclusion
`PASS_SCOPED_FRESH_C_CHAIN_AT_25B1C555_ONLY`, no open C input/schema/integrity/
cleanup findings. This is not final-package, owner/legal, physical or Store proof.

**Refresh export/render/candidate execution and approval after the upcoming
source/control/inventory freeze.** Never promote the25b1 binding or results to
the next SHA. Follow `reviews/dependency-chain-linux-preparation-01.md`; retain
this successful chain as historical scoped evidence, not an unexecuted claim.

Strict consumer binding keys remain exactly `schema_version, source,
export_receipt, render_receipt, graphs, report, schemas, lane, consumer`;
`source` binds commit/tree/diff/source-manifest hashes. Do not add bootstrap keys
or loosen schemas. Ordinary Linux cycle evidence remains under `$C/evidence/`.

## D — still unexecuted: combined qualification and final reconciliation

After the justified investigation and new freeze, run existing workflow `full`
at that frozen SHA; obtain all five jobs and
five main/five cleanup artifacts. Inspect raw executed tasks/tests, cache hits,
skips, Apple linkage versus runtime and artifact digests. Independently reconcile
A–D, original16/timer evidence and any changed scope; record actual commits/run
identities here; commit/push completed evidence and verify remote equality.

The prior reconciliation `reviews/frozen-source-reconciliation-prep-01.json`
remains a preserved earlier-cutoff audit, not the actual later A/B/C result.
No production/module/dependency/identity/architecture-contract delta was found
from ec0de to25b1; original16/timer evidence retains its reviewed historical
scope. Source/control or semantic-contract changes require affected requalification
and fresh bindings. A reviewed evidence-only plus mechanical-inventory delta may
retain applicability to unchanged runtime bytes, but must separate execution SHA
from delivery SHA and must never claim exact newer-candidate execution.

**Readiness remains NOT READY.** Strict protection observations are unresolved,
not physical-only. Physical LAN/device/accessibility/keybag/backup/restore tests,
private signing/Store operations and separate owner legal/editorial/privacy
declarations remain unperformed or excluded. `com.parlor.app` collision and
disabled publication safeguards remain. Preserve all architectural invariants,
failed evidence, user work and global caches; no merge/force-push/issues/Store
dispatch or physical-device testing is authorized.
