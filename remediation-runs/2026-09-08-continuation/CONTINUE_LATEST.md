# Continuation execution ledger — 2026-09-08

**Pre-freeze checkpoint; NOT READY.** Read `AGENTS.md`,
[`CONTINUE_HERE.md`](../../CONTINUE_HERE.md), the
[Linux handoff](../../docs/AGENT_CONTINUATION_2026-09-08_LINUX.md) and the preserved
[transfer handoff](../../docs/AGENT_CONTINUATION_2026-09-08.md).

This remediation-only ledger is excluded from the ordinary application source
manifest. Later updates may record execution without editing frozen application
docs, but cannot waive commit/tree, controls, inventory or platform bindings.
Never relabel an older CI run as execution at a later evidence-only commit.

## Repository and freeze state

- Repository: `https://github.com/Apdelrahman1911/parlor.git`.
- Branch: `fix/local-readiness-2026-09-07`.
- Delivered/current HEAD at this entry:
  `ec0de52a2c7ff9077482a291dc4fd2591fdea01f`; tree
  `48b099f3cd002a3253f21d2b06a4ece5787e62cb`.
- Initial remote matched and checkout was clean/full-history. Continuation
  source, controls, docs and evidence are not yet committed/pushed at this entry.
- Next: independent doc review; source/control commit(s); regenerate inventory;
  inventory-only commit and `--check`; normal push and exact remote verification.
  Then establish fresh frozen-source/control bindings. Record actual SHAs here.
- No native/Actions run, final dependency candidate or combined qualification
  has executed in this continuation at this entry.

Aliases: `C=remediation-runs/2026-09-07-local-readiness`,
`N=remediation-runs/2026-09-08-continuation`.

## Executed Linux evidence

All paths in this table are `$C/evidence/<cycle>/`. Each retains `receipt.json`,
raw logs and stop/cleanup evidence. These are working-tree-bound cycles, not
future frozen-SHA qualification; repeated test counts are not unique coverage.

| Cycle | Result and scope | Independent review under `$N/reviews/` |
|---|---|---|
| `continuation-linux-lane-controls-01` | 21 controls PASS; real Java/Javac21 probes. Initial test-file byte-binding limitation retained. | `gradle-lane-portability-evidence-independent-01.json` |
| `continuation-ci-controls-01` | 79 workflow/native-adapter controls PASS, no skips. | `native-continuation-controls-independent-01.json` |
| `continuation-transport-contracts-01` | 22 XML suites: 274 PASS / three existing single-JVM loopback skips / zero failures/errors. | `transport-contracts-evidence-independent-01.json` |
| `continuation-focused-controls-01` | **FAIL**, 358 tests: 356 PASS / one failure / one error. All nine commands ran. | `focused-controls-evidence-independent-01.json`; `focused-controls-01-packet-and-l08-evidence-review.json` |
| `continuation-l08-control-recheck-01` | **PASS**, corrected single-tap14 + unchanged functional-copy12, no skips/errors/failures. | `l08-control-recheck-01-independent-postrun-review.json` |

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

All five outer receipts record source/runner stability, **stop0**, no remaining
owned workers/live outputs and no cleanup errors, including the failed cycle.
Required evidence and failed receipts remain retained. No native cleanup claim
is implied. This ledger update itself ran no build/test/native worker.

## Next A–D execution and acceptance

### A / B — focused hosted Apple execution

Use [`scripts/ci/NATIVE_CONTINUATION.md`](../../scripts/ci/NATIVE_CONTINUATION.md),
not a Store workflow. One coordinator owns dispatches; same-ref concurrency can
cancel earlier runs, so do not overlap focused/full executions.

1. After freeze/push, dispatch `native-preflight` at the exact frozen SHA.
   Qualified profile: Xcode26.3/17C529, actual simulator SDK/runtime26.2. Preflight
   is nonbuilding, not runtime proof. Independently inspect the actual four-file
   artifact, ZIP digest, producer run/attempt/SHA, source map and both complete
   control manifests. Approved control inputs use manifest **`control_sha256`**,
   not JSON-file digests.
2. Only after independent approval dispatch `native-evidence` at that same SHA.
   A writes `$N/evidence/ios-readiness-17`; B writes `ios-readiness-18` and explicitly
   uses libproc. Preserve artifact identities and native/adapter cleanup receipts.
3. A needs 13 functional boots / 20 operations / 26 protection comparisons,
   three retained-host fixtures / 42 operations, Settings/OS/lifecycle/image paths.
   Historical native16 remains FAIL: 14/15 observed functional operations passed,
   boot10 Home callback stayed zero, boots11–13 and retained-host paths were not
   reached; all24 strict Complete comparisons failed. The companion never proves
   original strict L08 PASS. Preserve and separately report metadata mismatches.
4. B needs eight genuine normal-source launches plus a separate ninth native
   libproc image query, actual native return/structure sizes, artifact/signature/
   notices bindings and cleanup. Historical native15's eight launches/48 samples
   passed but ninth vmmap provenance failed; 187 controls do not query Parlor.
   A partial/failure permits B only when A's full ownership/source/control/cleanup
   receipts are safe, not merely because its process exited.

### C — fresh dependency export/render/candidate chain

Follow `$N/reviews/dependency-chain-linux-preparation-01.md` at frozen source.
Export strict androidRelease/iosArm64/iosSimulatorArm64/iosX64 graphs, run the
production renderer, independently approve the exact binding, then execute the
candidate through owned prerequisites. Never substitute hardcoded old counts.

Require all four coupled receipts:

- `candidate-input-verification.json`: `PASS_SCOPED_CANDIDATE_INPUTS`.
- `candidate-prerequisites.json`: `PASS_SCOPED_CANDIDATE_INPUT_EXECUTION`, exit0,
  no error and required optional-package cleanup.
- `schema-base-prerequisites.json`: `PASS_OWNED_SCHEMA_PREREQUISITES`, inner0,
  no error, exact package/import/control integrity and base-package cleanup.
- Outer lane: PASS/exit0/stop0, unchanged complete before/after bindings, actual
  current source equal to the frozen candidate binding, no cleanup failure.

Strict consumer binding keys remain exactly `schema_version, source,
export_receipt, render_receipt, graphs, report, schemas, lane, consumer`;
`source` binds commit/tree/diff/source-manifest hashes. Do not add bootstrap keys
or loosen schemas. Ordinary Linux cycle evidence remains under `$C/evidence/`.

### D — combined qualification and final reconciliation

Run existing workflow `full` at the same frozen SHA; obtain all five jobs and
five main/five cleanup artifacts. Inspect raw executed tasks/tests, cache hits,
skips, Apple linkage versus runtime and artifact digests. Independently reconcile
A–D, original16/timer evidence and any changed scope; record actual commits/run
identities here; commit/push completed evidence and verify remote equality.

**Readiness remains NOT READY.** Strict protection observations are unresolved,
not physical-only. Physical LAN/device/accessibility/keybag/backup/restore tests,
private signing/Store operations and separate owner legal/editorial/privacy
declarations remain unperformed or excluded. `com.parlor.app` collision and
disabled publication safeguards remain. Preserve all architectural invariants,
failed evidence, user work and global caches; no merge/force-push/issues/Store
dispatch or physical-device testing is authorized.
