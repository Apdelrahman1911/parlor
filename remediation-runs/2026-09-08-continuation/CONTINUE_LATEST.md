# Continuation execution ledger — 2026-09-08

**Latest: HEAD/remote still e511; the post-A21 qualified-budget, exceptional raw
retention and strict B-admission corrections are independently source-reviewed.
The affected286 Linux controls, standalone workflow contract and transport05
have actually passed, with independently reconciled cleanup; delivery review and
reviewed source/inventory commits are next. These are dirty-checkout control
executions, not a new native qualification. At clean e511, historical A21 remains
FAIL/cleanup FAIL, B22 NOT_RUN and C03 scoped PASS. A23/B24/C04/full D NOT_RUN;
fresh frozen-source bindings and specific execution approval remain required.
NOT READY.** Read `AGENTS.md`,
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
- Earlier inventory-only commit, **A17/B18/C01 execution HEAD**:
  `25b1c5551aee7630ac22d4b34668dd4b06c71b17`
  (`chore(review): refresh continuation source inventory`); tree
  `629a5d2b699cddb62d61849de3bd185fd4c01378`.
- At25b1: inventory14,847 rows; frozen source manifest820 entries, SHA-256
  `6e83dcdccc950fde35ccbf8fe2d4c73671a05a5f4a6a3a5068799af86bbd2334`.
  Frozen execution receipts bind an empty tracked diff, SHA-256
  `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`.
- Both commits were normally pushed. The retained native dispatch request's
  `remote_before` matches exact25b1; the coordinator separately re-executed
  `git ls-remote origin refs/heads/fix/local-readiness-2026-09-07` after the
  native/C results and again observed exact25b1. This audit reopened HEAD/tree
  and the local origin tracking ref; it did not substitute that ref for a
  network observation. See `actions/native-evidence-dispatch-01/request.json`.
- Subsequent evidence-only delivery commit:
  `4b8c586ce8d1228dda1420c470dff95dd599ddc6`
  (`docs(verification): preserve frozen execution and native failure evidence`);
  tree `b70b8d7478db79251796a500c40ef61dcdccbe92`. It does not relocate prior
  execution or refresh the inventory.
- Reviewed native source/control/evidence commit, now pushed:
  `22467846fbd48934226de5f06026b511d15c1b63`
  (`fix(verification): harden native receipts and add bounded hosted probe`);
  tree `25b6f87943867d2b5efe4e2b6820519db7779a8d`. It commits primary-error
  retention, exact Foundation-profile propagation and the bounded no-app probe.
- Following inventory-only commit, **initial diagnostic execution HEAD**:
  `1efed50cbc7856c11867e4cb088ba3b68716c8fc`; tree
  `bcabd2fd29eaea276bee31ec4ff6b74b63a79366`. Inventory15,553 rows, SHA-256
  `3e067a3ccf70967663c2f6ddedff1c078a993312b13c968fd8d34d524fa7cc89`;
  frozen source825 entries, SHA-256
  `9aafa1d908884f2c083d75c2959be625806ea7984da4c72e07b0f2e60f45ff4c`.
  `reviews/hosted-probe-freeze-01.json` retains normal push exit0 and exact remote
  equality. A direct `git ls-remote` during that earlier refresh also returned exact1efed50.
- Fresh validator02 and the authorized initial diagnostic actually executed at
  clean1efed50, not a future correction/delivery revision. The diagnostic failed
  before process observations, as detailed below. The narrowly scoped qualifier-
  budget correction was independently source-reviewed; its actual45 controls and
  affected transport03 checks passed at1efed50 plus reviewed working-tree changes.
- Subsequent reviewed source/control/evidence commit:
  `0607ced90fbd817ecff6994bf68ad51a9f30ebca`
  (`fix(verification): preserve qualified simulator discovery budgets`); tree
  `28ed2a46e5ea0f126f42655c16c38bca898fd9bb`.
- Following inventory-only commit, **A19/C02 execution HEAD (historical freeze)**:
  `5f35a20d3c9c846cd98b38a16881997ecf577497`
  (`chore(review): freeze qualified discovery controls inventory`); tree
  `e0e7abfff89975682a96a884cb745bcbc12e7238`. Inventory15,628 rows, SHA-256
  `0f99bb5bb32977587c51ce61e46b240778b98d85ae0e0cc466b1efa921c45dac`;
  frozen source825 entries, SHA-256
  `eb18e3a2d498ef43432d5ad97c7a885db9b648ae7981cfdadafb10e039c23413`.
  New execution receipts bind the clean tracked diff, not this later ledger edit.
  `reviews/hosted-probe-freeze-02.json` (SHA-256
  `ca885a8475e9e598e02bc07f01f1aac43bee0783f603dc38229b89d88a8bcf01`)
  retains normal push0 and exact remote5f35. This refresh reopened the retained
  receipts; the independent dispatch02 reviewer separately performed a direct
  `git ls-remote` and confirmed the same exact remote, not merely a tracking ref.
- Preserve the failed local pre-push preparation assertion in
  `reviews/hosted-probe-freeze-02-binding-preparation-failure.json`, SHA-256
  `94ceca439aa570aa712acaf4d7bf2af666d4fe2978f8771a2cbccae9c5e12307`.
  The preliminary reviewer digest serialized a path-keyed dictionary rather than
  the required sorted list. All13 source hashes agreed; the correct controls hash is
  `eaf596f24601bfe908cae23039dc99e383ac3f2b29fd775e303bbe395485153b`.
  No source edit, test rerun or dispatch occurred during reconciliation. Independent
  `reviews/native-process-probe-dispatch-independent-02.json`, SHA-256
  `9b80b40470010592ee9edf3b7fbb9ee18982241fbf890b566e541c4e184b1b1f`,
  closes that preparation error, not by deleting or relabeling it as a test PASS.
- Validator03, corrected diagnostic34239707887, fresh preflight34242374948,
  A19 attempt34244902185 and C02 actually executed at clean5f35. Their distinct
  results are below; B20 did not start and full D has not run. The subsequent
  metadata/lifecycle correction and affected Linux evidence are recorded below;
  neither relocates these historical executions or authorizes another native run.
- Earlier normally pushed **evidence-only delivery**:
  `700243c3426db2da3855a9d3a3df58ada607ffc5`; tree
  `bd93099fe19ccf46e27eb67f22d694cc1ae074aa`, parent5f35. Its616 paths comprise614
  evidence payloads plus two safety reports, with no source/control/inventory
  change. `reviews/post5f35-evidence-delivery-controller-01.json` records push0
  and the coordinator's direct exact-remote observation; independently approved
  by `reviews/post5f35-evidence-delivery-independent-01.json`. This ledger author
  did not repeat that network check. It remains a historical delivery, not the
  later source/control freeze.
- Reviewed source/control/evidence delivery:
  `4276854c19afafdfd109be063a02dd5534fa86d0`; tree
  `60e3f5eb0af63a72a397b03da8cdc99c7fd398f8`, parent700243,
  `fix(verification): bind direct-owned simulator lifecycle controls`.
  The exact115-file /3,298,753-byte delivery commits the reviewed metadata,
  lifecycle and legacy-fixture corrections and their evidence.
- Following inventory-only commit, **A21/C03 execution HEAD**:
  `e5111ea49113c3f12cb577058c3f926dfebdc325`; tree
  `a88872673bd4cfc366236362f287c1db0a04a5cd`,
  `chore(review): freeze direct-owned lifecycle source inventory`.
  Inventory16,343 rows /10,181,341 bytes, SHA-256
  `c876f976881b70a3c4aaba9d2d2785f397ebbedcbef66a5ea632c5a05d9139a4`;
  source828 manifest SHA-256
  `6e313ce760d840aa08b617229c03263ce90eda715cb6c442cbe9738045a63e8e`,
  empty tracked-diff hash as above. Both commits were normally pushed;
  `reviews/direct-lifecycle-freeze-01.json`, SHA-256
  `18a502b32cf1349e06bc5efbc33fdc90bc1dc3bd7389c3a87f4e1ba77a82b3ad`,
  retains push0/query0 and the coordinator's exact direct remote e511 observation.
  Independent delivery/freeze approval:
  `reviews/post700243-lifecycle-delivery-freeze-independent-01.json`, SHA-256
  `d4176aa25c2d8244fc4631fb6bb74e4cb306c7ed31b0623de919a6c1db8f9ba7`.
  This ledger author reopened those receipts, not another network query.
  Preflight34273018746, A21 and C03 actually executed at this freeze. The new
  post-A21 corrections are not a later frozen execution or dispatch approval.

Aliases: `C=remediation-runs/2026-09-07-local-readiness`,
`N=remediation-runs/2026-09-08-continuation`.

## Executed Linux evidence

All paths in this table are `$C/evidence/<cycle>/`. Each retains `receipt.json`,
raw logs and stop/cleanup evidence. The first five are working-tree-bound cycles;
the sixth actually executed at clean25b1; the next two bind4b8 plus reviewed
working-tree changes; validator02 executed at clean1efed50. The next two bind1efed50
plus the reviewed budget correction and then-held ledger; validator03 actually
executed at clean5f35. The last three listed bind700243 plus held working-tree changes,
not a clean freeze. None qualifies a future SHA; repeated counts are not unique coverage.

| Cycle | Result and scope | Independent review under `$N/reviews/` |
|---|---|---|
| `continuation-linux-lane-controls-01` | 21 controls PASS; real Java/Javac21 probes. Initial test-file byte-binding limitation retained. | `gradle-lane-portability-evidence-independent-01.json` |
| `continuation-ci-controls-01` | 79 workflow/native-adapter controls PASS, no skips. | `native-continuation-controls-independent-01.json` |
| `continuation-transport-contracts-01` | 22 XML suites: 274 PASS / three existing single-JVM loopback skips / zero failures/errors. | `transport-contracts-evidence-independent-01.json` |
| `continuation-focused-controls-01` | **FAIL**, 358 tests: 356 PASS / one failure / one error. All nine commands ran. | `focused-controls-evidence-independent-01.json`; `focused-controls-01-packet-and-l08-evidence-review.json` |
| `continuation-l08-control-recheck-01` | **PASS**, corrected single-tap14 + unchanged functional-copy12, no skips/errors/failures. | `l08-control-recheck-01-independent-postrun-review.json` |
| `continuation-release-validator-01` | **383 PASS** at25b1, zero failures/errors/skips; actual release validator, inventory freshness and pinned ShellCheck/actionlint. | `release-validator-independent-01.json` |
| `continuation-native-repair-controls-01` | **Outer/controller FAIL**, despite all436 controls PASS with no skips and static workflow contract PASS; postflight composition-allocation parser mismatch, detailed below. | `native-repair-controls-postrun-independent-01.json` (312); `native-process-probe-controls-independent-01.json` (124). |
| `continuation-transport-contracts-02` | **PASS**, actual `:shared:transport-p2p:desktopTest`: 22 XML suites, 277 cases / 274 PASS / three existing skips / zero failures/errors. | `transport-contracts-02-evidence-independent-01.json` |
| `continuation-release-validator-02` | **426 PASS** at1efed50, zero failures/errors/skips; actual release validator, inventory15,553 freshness and pinned tools. | `native-process-probe-dispatch-independent-01.json` |
| `continuation-process-probe-budget-controls-01` | **45 PASS**, zero failures/errors/skips; actual focused Python controls, not native observations. | `process-probe-budget-controls-independent-01.json` |
| `continuation-transport-contracts-03` | **PASS**, actual `:shared:transport-p2p:desktopTest`: 22 XML suites, 277 cases / 274 PASS / three existing skips / zero failures/errors. | `transport-contracts-03-evidence-independent-01.json` |
| `continuation-release-validator-03` | **432 PASS** at5f35, zero failures/errors/skips; actual release validator, inventory15,628 freshness and pinned tools. | `native-process-probe-dispatch-independent-02.json`; `frozen-source-scope-reconciliation-5f35a20d-01.json` |
| `continuation-transport-contracts-04` | **PASS**, actual strict `:shared:transport-p2p:desktopTest`: 22 XML suites, 277 cases / 274 PASS / three existing skips / zero failures/errors. | `transport-contracts-04-evidence-independent-01.json` |
| `continuation-native-lifecycle-controls-01` | **FAIL**, all ten commands ran: 436 tests / 415 PASS / one failure / 20 errors / zero skips. Separate external/ordinary cleanup independently cleared. | `native-lifecycle-controls-postrun-independent-01.json` |
| `continuation-normal-lifecycle-recheck-01` | **187 PASS**, zero failures/errors/skips; only the affected normal-control scope reran after the reviewed legacy-fixture correction. | `normal-lifecycle-recheck-postrun-independent-01.json` |

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

The same actual validator freshly ran426 tests at clean1efed50 in
`continuation-release-validator-02`: raw `Ran 426 tests ... OK`, script exit0,
matching source825/runner before-after bindings, immediate stop0 and no cleanup
errors/workers/live outputs. Receipt SHA-256
`1c538a25c7fdb6da3a6b0b6a72ce5d87047f7cf18e30963f8f061c07413bff6b`.
This is Linux release-control execution, not `productionCheck` or native runtime.

The actual validator freshly ran432 tests at clean5f35 in
`continuation-release-validator-03`: raw `Ran 432 tests in 44.372s`, `OK`,
script exit0. All432 verbose test IDs reconcile, including two Git-fixture
interleavings rather than inline `ok` labels. Inventory15,628 freshness and
pinned ShellCheck/actionlint stages passed. Source825/runner before-after bindings
match, immediate stop0, no workers/live outputs/cleanup errors; cleanup completed
at `2026-09-08T14:22:42.477099+00:00`. Receipt SHA-256
`e39cba487668d9dcd3a0f294078422cee63dcbe18d08a8ddcdb8f8bfe4c89eef`.
This is a new execution at5f35, not a relabeling of426/45 results, and is not
`productionCheck`, an application test or final combined qualification.

### Resumed source/control checkpoint

`continuation-native-repair-controls-01` actually ran primary-errors12,
toolchain-profile12, Foundation-profile20, single-tap14, functional-copy12,
composition55, normal187, release-focused122 and workflow-hygiene2: **436 PASS**,
zero test failures/errors/skips. All ten commands exited0, including the separate
static workflow contract. These are pure controls, not native/application proof.

Preserve **FAIL/exit1**: `native-repair-results.json` records the sole postflight
error `composition: exactly one ALLOCATION marker required`. Raw `composition.log`
has four allocations and four corresponding cleanup PASS records (outer plus
three nested launcher-test allocations), not the one assumed by orchestration;
one nested allocation shares a unittest line. Additive author reconciliation
`reviews/native-repair-external-postflight-reconciliation-01.json` and the
independent raw-evidence review `reviews/native-process-probe-controls-independent-01.json`
verify all five exact external custody/cleanup pairs and absent paths (one
Foundation-profile, four composition). The author's addendum is also independently
approved in `reviews/native-repair-external-postflight-independent-addendum-01.json`.
No controls reran, no cleanup guard was
waived, and the original FAIL remains unchanged. This parser defect is no longer
an external-cleanup blocker to preparing the reviewed freeze. Outer receipt SHA-256:
`d8d65f92d4d10014c62ca1b08a0e67ccbe697d6c72141815814b2a221c0f627d`.

The actual `native-repair-inputs-{before,after}.json` each contain **261** entries
and are byte-identical, SHA-256
`ac0ab3e888ca838fbf02f7d06cc858d0bdb8cab7df74fae76340696fc9b8288a`.
Both newer cycles bind4b8 plus tracked diff SHA-256
`bb5d58442137496d82d7b1cc98b123ee9b6cf33562507c3f67a8f23aee2ae431`,
with matching before/after source/runner objects, not a future clean candidate.

Transport02's raw XML and independent review confirm three existing
`P2pKitRoomTransportLoopbackTest` skips, not network passes. Its Gradle log records
**21 actionable tasks: 14 executed, seven compilation tasks from cache**;
`desktopTest` executed. Receipt
SHA-256 `94e4724ec6ee9c6b3032698d49bed313407850fdf69698365cc86fc8ac259b26`.

### Reviewed qualifier-budget correction and affected Linux checks

The exact three-file correction is independently approved in
`reviews/native-process-probe-budget-independent-01.json`, SHA-256
`53934d03592b917f311724e7e586744bfee36acad97a0fd88a7ea12c98cf7cda`.
Only `/usr/bin/xcrun simctl list runtimes --json` and the exact corresponding
`devicetypes` query receive120s; ordinary adapter commands retain20s. Valid explicit smaller
timeouts are honored; invalid or excessive values reject. Observation commands
must fit their whole allowance plus the existing4s retirement reserve or record
`SKIPPED_BUDGET` without launching. Only finalizing Git reconciliation retains
its existing remaining-time minimum. The named `qualified-platform` stage now
records qualification failure before observations. Observation300s, cleanup225s,
original ps15s, strict platform/toolchain, ownership/privacy and all five
finalizers are unchanged. This is a source correction, not proof that hosted
enumeration or the original ps observation succeeds.

`continuation-process-probe-budget-controls-01` actually ran
`/usr/bin/python3 -B -m unittest -v scripts.release.tests.test_native_process_probe`:
raw `Ran 45 tests in 0.181s`, `OK`, with all45 unique verbose IDs passing.
Receipt SHA-256
`d2a468ee6bbedeb8c2f06c16b5afe21d38036adfc560dd1f09658c7c59796d9e`.
Independent postrun approval
`reviews/process-probe-budget-controls-independent-01.json`, SHA-256
`bc2de7978febf58d17813762b16b8f36c78d5be7b60bbf39924ee74a6c73a08b`,
confirms the actual IDs, bindings and cleanup. The16 printed diagnostic/custody
JSONs, including expected `FAILED` results, are synthetic fault-test output;
none is a hosted probe, app, XCTest or Parlor libproc receipt.

Transport03 freshly re-executed the affected documentation/hash gate under
strict verification. Raw Gradle records **21 actionable tasks: 14 executed,
seven compilation tasks from cache**; `desktopTest` executed. All22 retained XML
suites reconcile to277 cases, 274 PASS and three existing
`P2pKitRoomTransportLoopbackTest` skips, not network passes. Receipt SHA-256
`230bea21323f6443e2d87e82d90e6326bf66651d05a9e667adadf242ccecb312`.
Independent postrun approval
`reviews/transport-contracts-03-evidence-independent-01.json`, SHA-256
`73e05c66850697a44583673b7ac5b7851690f4d4b7f91713aaaa50315faee651`,
confirms all22 raw XML suites, source/runner bindings and cleanup. Linux-disabled
Apple tasks and cached compilation are not newly executed platform evidence.

Both cycles bind1efed50 plus the reviewed three-file correction and the then-held
ledger, tracked diff SHA-256
`a7c128e8a6a14954963b8f1818885cf403c0bcd2092ff93d529df5805ed92cf5`;
source825 manifest SHA-256
`fa02baf9686cb9e553157aedc35ada87e613b912f66192a4baa7a615011b179a`.
Their complete before/after source and runner objects match. Both immediately
stopped Gradle with exit0, retained no workers/outputs/errors, and removed owned
scratch; transport03 also removed seven exact generated directories.
Cleanup completed at `2026-09-08T13:59:04.319495+00:00` and
`2026-09-08T14:00:25.079678+00:00`, respectively. This later ledger update is
outside those execution bindings, not a new frozen qualification.

### Lifecycle controls at700243 — failed packet retained, affected recheck passed

Transport04 re-executed the affected documentation/hash gate: **21 actionable
tasks, 14 executed, seven compilation cache hits**; `desktopTest` actually ran.
Receipt SHA-256 `03ade63d4dcaff14da734fd8ccf79732538bdb87c0ca542a902d6dd3e30f07af`.

Lifecycle-controls01 passed primary-errors26, simulator-lifecycle51,
lifecycle-integration32, toolchain12, Foundation20, functional-copy12,
composition55, native-adapter40 and **one selected probe-cycle guard**, not all45
probe controls. Normal187 had166 PASS / one failure /20 errors; total436 remains
**415 PASS / one failure /20 errors / zero skips**, not436 PASS. Receipt SHA-256
`0e0cfaf79422d1627622ab0cae21b535a923e77d270d638cfacb8ea0f2f72e05`.
Independent postrun review SHA-256
`ce9e68960dc161c4199deb3186dd5d940d8e1c587c7f5e5918ed1d5522b5abfa`
reconciles all five real Linux Python stand-ins (exact handles reaped, no fallback
retirement/errors, paths absent) and five exact Foundation custody/cleanup pairs
(one profile, four composition, including mid-line markers). These are not
Apple processes, native timeouts or app/runtime observations. Original FAIL stays.

The failed normal fixtures bypassed the constructor with `object.__new__` and
omitted lifecycle state. The independently reviewed correction adds only
`value.lifecycle_mode, value.lifecycle = runner.LEGACY_LIFECYCLE, None` to that
legacy test factory; no production fallback, assertion/test deletion or skip.
See `reviews/normal-legacy-lifecycle-fixture-independent-01.json`.
The separate recheck actually ran `run_control_tests.py`: raw **187 tests, OK**,
all187 declared/discovered/verbose IDs agree; receipt SHA-256
`3e0e95d184bf832225286fe9424ae2e98e61f9a0de95ec3fda35a620758f1b6e`.
Formal independent postrun review SHA-256
`0d10fe368f7f2812981e3059e8a3ba2b6322b5e5b5b1ab4963a3c7ae286fb9a0`
confirms corrected fixture bytes, stable93 controls/36 actual imports and cleanup.
The249 unchanged passing controls did not rerun; this is not a new436-PASS cycle
or actual Parlor libproc evidence.

Those three cycles bindHEAD700243 and source828 manifest SHA-256
`6fc7298b3b927aee32359326ef68f75a0a3d9928e73e47b7d5a3f82c4828fe15`.
Transport04/lifecycle01 bind tracked diff
`6c6e32a20e6d6b404d197634ff40e27424b070e01248334bfddca6a5b6542190`;
the fixture-only recheck binds
`64d20511dd19955a8e503845a020760cf94739c8bc4a9bc53073faf45831c1c0`.
Full source/runner objects match before/after. Lifecycle255 control inputs matched
before/after that failed cycle;
recheck93/full828 independently captured at19:15:47Z in
`reviews/normal-lifecycle-recheck-current-bindings-independent-01.json`, SHA-256
`21bc29aa218ca7986aedcf60ea7fd61aad106089ea304d776fc6e5624931dd95`.
That held capture is not a native preflight. All three immediately stopped Gradle0;
ordinary cleanup finished at `2026-09-08T18:11:50.304473+00:00`,
`2026-09-08T18:30:50.549665+00:00`, `2026-09-08T19:03:56.219181+00:00`, respectively.
No owned workers/live outputs/errors remained; transport04 removed seven exact
generated directories. This later ledger edit is outside those execution bindings.

All fifteen outer receipts record source/runner stability, **stop0**, no remaining
owned workers/live outputs and no outer cleanup errors, including all three failed
cycles. Their failed test/postflight outcomes and additive external reconciliations
remain separate above. The nine C01–C03 cycles below satisfy the same outer cleanup
conditions: **24 Linux outer receipts**, not24 application-runtime passes.
This Linux cleanup conclusion does **not** apply to A19's or A21's failed native
cleanup.
Required evidence and failed receipts remain retained. This ledger update ran
no build/test/native worker and did not start or clean another agent's lane.

## A / B — actual hosted Apple evidence and remaining scope

The earlier preflight and A17/B18 runs used `production-verification.yml` at exact25b1, attempt1,
qualified Xcode26.3/17C529, simulator SDK/runtime26.2 on arm64. Four other
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

### Failed initial no-app diagnostic at1efed50

- Initial diagnostic **34232325670**, attempt1, `native-process-probe`: API/run
  and iOS job **FAILURE**; probe step exit2. It was independently authorized for
  one initial non-app run by `reviews/native-process-probe-dispatch-independent-01.json`.
  Request/approval binding: `actions/native-process-probe-dispatch-01/request.json`;
  exact13-file `control_sha256`
  `0b60d4219b279c1022232d18a261a21ac7c577357cafbac2d695e58fbcf92a87`.
- Raw `actions/34232325670/native-process-probe/probe.json` retains
  `OBSERVATIONS_WITH_FAILURES_NOT_RUNTIME_EVIDENCE`, `stages: []` and closed
  primary error `RuntimeError`. The qualifier's
  `/usr/bin/xcrun simctl list runtimes --json` hit its20s cap: observation20.003s,
  total20.017s including retirement, exit-15, direct child reaped.
  **No ps/libproc comparison, host metrics, installed-ps identity, Simulator
  creation or nonbuilding showBuildSettings observation executed.** This does
  not reproduce or diagnose A17/B18's ps timeout and adds no app/L08/B evidence.
- The new probe callback capped every ordinary command at20s, including calls
  from `native.qualified_platform`; the existing `native.command` contract is120s.
  That introduced qualification-budget mismatch is source-evidenced. The
  underlying OS/runtime-enumeration delay is unknown; a larger correct qualifier
  budget is not evidence that enumeration would succeed. The original ps15s
  observation bound remains unchanged and was not exercised by this run.
- All five cleanup stages completed: immediate/final isolated Gradle stops0,
  simulator `NOT_CREATED` (no create intent), exact owned resources removed and
  source/control reconciliation unchanged. Preservation errors/signals were empty.
  Post-upload custody cleanup was `COMPLETE`, with owned staging removed and the
  failure status preserved. Four other jobs and full Apple/A/B steps were skipped,
  not passed. No app build/XCTest or Store operation occurred.
- Main artifact **10058341166**, ZIP SHA-256
  `123a83ba96d901d13ad10c4b06b53c3f394c79e6d458ee53a102d85b63b3bbcb`;
  cleanup artifact **10058342975**, ZIP SHA-256
  `4acbc5e2cdeb23c897412ac7437b75b3d920c525db2fe3511514a9776cbc321c`.
  Raw API/logs, both ZIPs, extracted receipts and API-matching digest receipt are
  retained under `actions/34232325670/`. Download integrity and cleanup are not
  successful diagnostic observations. Independent review
  `reviews/native-process-probe-34232325670-independent-01.json` and its cross-checked
  `reviews/native-process-probe-platform-diagnosis-01.json` close the evidence
  reconciliation, not correction/tests or another dispatch. Original failed
  receipts remain immutable.

### Corrected no-app diagnostic at5f35 — actual FAIL with observations

- Run **34239707887**, attempt1, `native-process-probe`: API/run and iOS job
  **FAILURE**, probe step exit2. The fresh one-run dispatch02 approval was consumed
  by this run. Raw `actions/34239707887/native-process-probe/probe.json`, SHA-256
  `b598069f8c26e3d1851dc7e4eaade20fe72968f22403b32716fd996535990bb0`,
  retains `OBSERVATIONS_WITH_FAILURES_NOT_RUNTIME_EVIDENCE`.
- Actual platform: macOS15.7.9/24G830 arm64, Xcode26.3/17C529,
  iOS26.2/23C54. Runtime/devicetype enumeration completed in6.115s/0.175s under
  the corrected120s caps. That does not explain the prior20s failure or prove
  that the larger allowance caused success.
- The original unchanged15s full-ps command completed before boot, after boot
  and during nonbuilding Xcode overlap in **0.040s / 4.128s / 10.207s**, exit0,
  complete parsed counts. All three separate metadata-ps observations completed.
  No global PID/argv rows were retained. These phases did not reproduce A17/B18's
  timeout and did not observe ps during the synchronous bootstatus wait.
- All three libproc **metadata** observations were partial: denied248/255/255;
  the middle also had six disappeared processes. The actual136-byte structure
  and successful self-row metadata are **not** a complete census, an ownership
  fallback, a Parlor image query or normal Debug-binary provenance. Do not suppress
  the denials or promote these observations to PASS.
- The single `xcodebuild -showBuildSettings` command timed out at60s
  (60.639s including retirement), exit-15, direct child reaped. Its output was
  deliberately DEVNULL. The recorded overlap/live flags do not establish a
  build/actool/Kotlin workload, successful settings extraction or a timeout cause.
  All eight stage callbacks completed, **not all observations passed**. No app
  build/XCTest/protection operation or timed-out-ps stack sampling occurred.
- All five cleanup finalizers completed: immediate/final isolated Gradle stops0,
  exact owned simulator shutdown0/delete0 with absence guards, exact resources/
  DerivedData removal and unchanged source/control reconciliation. Preservation
  errors/signals were empty. Post-upload custody was `COMPLETE`, preserving the
  failed probe verdict. Four other jobs and full Apple/A/B steps were skipped.
- Main artifact **10061558867**, ZIP SHA-256
  `e5208be6be2c9603c622dbe8c6eb09072fdea9d4accb8174b2a5f661343bcd97`;
  cleanup artifact **10061560599**, ZIP SHA-256
  `687c3e2b09c8e34f4e3c0f299c2f0b62caa249da08168d9eb12f951c7f10c3f7`.
  Raw API/log/ZIPs/extracted/download receipts are retained under
  `actions/34239707887/`. Independent review
  `reviews/native-process-probe-34239707887-independent-01.json`, SHA-256
  `e281ebb0e606febac31c3ac012f495d11715430b9947a38d89acc200c7f99984`,
  and diagnosis `reviews/native-process-probe-platform-diagnosis-02.json`, SHA-256
  `75a859b15fc3ac22163c277ad1900db71e4c3503eb886360459a15661fb3a3e9`,
  approve the factual reconciliation and a fresh binding-only preflight, not a
  third unchanged diagnostic or an application-runtime success.

### Fresh binding-only preflight at5f35 — actual SUCCESS

- Run **34242374948**, attempt1, `native-preflight`: API/run and iOS job
  **SUCCESS**. `actions/34242374948/native-preflight/preflight.json`, SHA-256
  `66fcb21e70d6bbce8836ff3c63a4628638b67856c17941d38dcd2531deb25c9a`,
  is `REVIEW_REQUIRED_NOT_RUNTIME_EVIDENCE`. No simulator allocation, app build,
  XCTest, Parlor libproc or protection validation executed.
- Fresh manifest `control_sha256` values, not JSON-file digests:
  L08 `b2afb7dc5b900ff7ac8dac9fb9458b1aa762fba6ef4cd0c16513ac9757c37a99`;
  normal `e0fd9c7859af3bdc20b82ca8807a6f5a053c09fde4e5d5a4b96896aafc3ebc71`.
  Source825 and181 frozen control files plus the fresh binding were independently
  reconstructed; A has96 control entries and normal91.
- Main artifact **10062533514**, ZIP SHA-256
  `fde8891243b7a33a70f4bfa275325844f7949e55c3bf00192fff9476213902e3`;
  cleanup artifact **10062534419**, ZIP SHA-256
  `2297a255d46fc8db92f09edad77f93de6db8d668393ec787b2042a554af705ed`.
  Cleanup PASS, errors `[]`, binding removed after confirmed upload and source
  unchanged. No build or simulator was allocated to retire. Four other jobs and
  full Apple steps were skipped, not passed.
- Independent `reviews/native-preflight-34242374948-independent-01.json`, SHA-256
  `fc0a6a976cca30ee86a74137fc8b7fefcb8ab80c234c5754120ac0418a96038c`,
  authorized exactly one initial A19, then B20 only if A's preservation and cleanup
  were safe. **Run34244902185 consumed that approval. It is not retry permission.**

### A19 at5f35 — FAIL / runtime NOT_RUN / cleanup FAIL; B20 NOT_RUN

- Run **34244902185**, attempt1, `native-evidence`: API/run and iOS job102124280589
  **FAILURE**. Focused native and custody-cleanup steps each exited1; both evidence
  uploads succeeded. Four other jobs and full Apple steps were skipped.
- Canonical `actions/34244902185/native-evidence/ios-readiness-19/receipt.json`,
  SHA-256 `872ba1e34987928fbe8981f6abe39cab8bd6b517d6f1bcd8fca2dc4f9bdb65c1`,
  retains **FAIL**, runner exit1, `runtime_evidence_status: NOT_RUN`,
  `cleanup_status: FAIL`, `strict_l08_gate: NOT_SATISFIED_BY_COMPANION`.
  No application build, XCTest, single-tap interaction, functional-storage,
  retained-host, strict-protection or normal Parlor libproc query executed.
- The13 command rows contain ten successful commands (eight toolchain/host
  metadata queries, create and boot). The exact owned UUID
  `2BE2D866-E8C8-42E6-B5EC-22D6789EECDA` was created/booted. Zero-based row10,
  `simctl bootstatus ... -b`, preserves the original **ps15 TimeoutExpired** as
  `primary_error` and the separate exact-owned-root **lsof30 TimeoutExpired**
  as `command_cleanup_error`. The generic primary-error repair actually worked;
  the individual registration-versus-monitoring census call remains unknown.
- Row10 has no recorded child exit. Raw `bootstatus.log` reaches terminal
  `Finished` at15:31:15Z/elapsed01:41, **after** the wrapper's
  `finished_at: 15:31:01.112444Z`. That log is not a recorded command PASS, does
  not establish direct-child retirement, and is not a300s boot-deadline failure.
- Finalizer rows11/12 are `xcrun simctl list devices -j`, with no exit or
  primary/secondary error fields and no accepted owned-metadata files.
  **The actual shutdown/delete commands were never reached; exact simulator
  absence was not attested.** Shutdown finalization surfaced lsof30 and delete
  finalization ps15. Their original metadata-operation triggers are unrecoverable
  because the separate metadata wrapper can mask them. Do not claim that simctl
  inventory itself timed out45s.
- The later six finalizers passed: owned-worker stop, secondary FIFO cleanup,
  worker verification, copied-input verification, exact temporary copy/DerivedData/
  home removal and final source identity. `owned_processes_remaining` and
  `unknown_holders` are empty; source825/controls/copy764 bindings remained stable.
  Those results **do not** retire the simulator or turn native cleanup into PASS.
  **Zero Gradle build or stop commands executed**: `gradle_attempted` never became
  true. Generic `cleanup_method` policy prose is not evidence of isolated stops.
- Outer `continuation.json`, SHA-256
  `27ffc9124098845dfd80b3af82bc3a4db42aeb0c933b87d1d518c002e0edb93d`,
  is FAIL with `cleanup_safe: false` and
  `native-cleanup-or-source-identity-unsafe-do-not-start-another-cycle`.
  Only `runs.l08` exists. **B20 is NOT_RUN, not an independently failed B runtime.**
  Canonical evidence preservation is COMPLETE/errors `[]`; the guard correctly
  stopped the serial chain despite successful evidence preservation/upload.
- Outer cleanup receipt SHA-256
  `a771df59e74c61cfb89d580a7c75f256bece1b630138c34f9a1d8eb192956631`
  is FAIL, `removed: []`, `native-cleanup-unsafe-retain-owned-evidence`.
  Canonical evidence, binding, lock and staging were deliberately retained.
  Generic Actions `Cleaning up orphan processes` is not exact UUID/PID retirement
  proof; local review cannot retroactively clean the ended hosted runner.
- Main artifact **10063811944**, ZIP SHA-256
  `b18ef5c99f5a52e3c014fbe7eb8ba0202c3da9f6c3015c0c804b719492aa6a62`;
  cleanup artifact **10063816208**, ZIP SHA-256
  `5b98cf597751077b4ead067dc2d347dc179934ef304e70803a863413cf053340`.
  Raw API/attempt/jobs/log/ZIPs/extracted/download receipts remain under
  `actions/34244902185/`. Independent postrun
  `reviews/native-evidence-34244902185-independent-01.json`, SHA-256
  `cab3c048a245f5dfba84ce1e652005d05b47e1a5ad620181d2ca92a54f534413`,
  reconciles eight downloads,29 ZIP members and20 canonical A files without
  upgrading failures. Diagnosis `reviews/native-ps-timeout-diagnosis-02.json`,
  SHA-256 `db299fa48618a0c2232961c4ac50e8c94c2ae8bafa982cff7706994cb8b7785e`,
  has independent factual approval in
  `reviews/native-ps-timeout-diagnosis-independent-02.json`, SHA-256
  `3a0c2a12a7e57458fd34057afcf663ae08d40d7a57c49bf17b8b244da7eb2cc6`.
  That addendum authorizes no source edit, test/build or further native dispatch.

### Fresh binding-only preflight at e511 — actual SUCCESS, approval consumed

- Run **34273018746**, attempt1, `native-preflight`: API/run and iOS job
  **SUCCESS** at clean e511. Raw `actions/34273018746/native-preflight/preflight.json`,
  SHA-256 `6dcfd9a4bcf817f664dba411be5b38277d94bff4aa165621fcb960013f798751`,
  remains `REVIEW_REQUIRED_NOT_RUNTIME_EVIDENCE`. No build, simulator allocation,
  XCTest or Parlor libproc query occurred. Cleanup PASS/errors `[]`; the binding
  was removed after confirmed upload.
- Fresh manifest `control_sha256` values:
  L08 `840467f0656642c46bdef61c9c48e6355d2b78ac7dee1d00b302a61d4c798c3a`;
  normal `427b30f1ec821438cf6ecc8a2fbe0e01e5509220a0adde26ecd3b5da6e662983`.
  Main artifact **10074652767**, ZIP SHA-256
  `ef09855f3ac458341a810eb4bc6353d07996c2c8883d2f0a9bd0902ec6b1175d`.
  Independent `reviews/native-preflight-34273018746-independent-01.json`, SHA-256
  `fce841a029d8874d88d6aec24d772cd7ca2f34b0dc1495eb002285ac87e4875f`,
  approved exactly one dispatch. **A21 run34275491965 consumed it; no retry or
  later-source permission remains.**

### A21 at e511 — FAIL with partial application observations; B22 NOT_RUN

- Run **34275491965**, attempt1, `native-evidence`: workflow/run and iOS
  job102227367061 **FAILURE**. Native execution and custody cleanup failed;
  both evidence uploads succeeded. Four other jobs and full Apple steps were
  skipped by focused mode, not passed. Actual platform: macOS15.7.9/24G830 arm64,
  Xcode26.3/17C529, iOS26.2/23C54; copied Debug `com.parlor.app.debug`, ad-hoc,
  `direct-owned-v1`. All canonical A files below are under
  `actions/34275491965/native-evidence/ios-readiness-21/`.
- Raw `receipt.json`, SHA-256
  `2d8168608c71fbc4bbed03cc23f595eafc95473e7e4fa3ec1277f61961ed18a3`,
  retains **FAIL**, `runtime_evidence_status: FAIL`, `cleanup_status: FAIL`,
  `strict_l08_gate: NOT_SATISFIED_BY_COMPANION`. Source/control before-after
  bindings match clean e511. Postbuild copied-source verification was **not
  completed**; its initialized false flag is not observed proof of mutation.
- Actual cold compilation occurred: raw `xcodebuild.log`, SHA-256
  `dd68f0f5ee696408953e9b3dcdc11bf6a9c9a70d2593ea4c57c13333b48ac468`,
  records embedded Gradle **BUILD SUCCESSFUL in30m47s;138 actionable/138 executed**.
  Four `ComposeContainerViewControllerTests` actually passed (raw suite4/0failures).
  The fifth combined Settings/local-session/OS/L08 test began21:12:19.843Z;
  last activity **t639.75** was followed by `BUILD INTERRUPTED`, not completion.
  No xcresult summary/test
  extraction exists; do not infer full-suite PASS or complete failure/skip counts.
- The2700s **whole xcodebuild command** deadline expired:2061.720921s elapsed
  before the first XCTest, leaving638.279079s. The primary `TimeoutExpired` and
  exit-15 are not a per-test allowance failure, an app crash, a new ps/lsof
  timeout, or a demonstrated storage defect. Build success is not qualification.
- Eight actual copy-instrumented cold-launch markers /eight unique boots /
  **48 samples** were retained. `probe-readiness-result.json`, SHA-256
  `b5f51e1025e6fe7c84b7e1a1e9b224cf16ebadd9b73495fe2d4d5d1d9f8e1ca3`,
  records completedtrue/74 observations. These are partial **A** observations,
  not normal-source B repetitions or libproc queries; final boot-health,
  built/installed artifact/signature and full-matrix reports are absent.
- Functional-storage boot1/save completed; boot2 launched and read load state.
  Its first Home preparation recorded observer setup, fresh zero counters and
  two stable valid geometry samples. **Zero target taps or callback observations**
  occurred before interruption; boot2 has no completed marker and corrected
  boot10 was not reached. The required13 boots /20 operations /26 Complete
  comparisons remain incomplete. Retained hosts: **0/3 fixtures,0/42 operations**;
  later local-game/OS paths were not reached. Partial logs do not qualify the
  Settings/storage/recovery matrix.
- Outer `actions/34275491965/native-evidence/continuation.json`, SHA-256
  `92d0128851378a445ab8ecccc779539adfa4b58a8c6a93172b00c990c5cbe07d`,
  is FAIL with only `runs.l08` and `cleanup_safe: false`. **B22 NOT_RUN**:
  zero actual normal repetitions, ninth launch, Parlor libproc queries or native
  return/structure-size observations. The serial guard correctly denied B.

#### A21 strict-protection observations — preserved raw data, collection NOT_RUN

`parlor-l08-app-foundation.json`, SHA-256
`c21e69e3ea4e5cce3bc005d25d35dd3b499c9885758bf24095ad13a410c4ec2b`,
has raw status `OBSERVATIONS_COMPLETE`, preservation `PRESERVED`, but final
collection **NOT_RUN**. On runtime26.2/Foundation UUID
`c153116f-dd31-3fa9-89bb-04b47c1fa83d`, all eight operations returnedtrue/resulttrue
without an exception or NSError. All eight FileManager dictionaries lacked the
protection key (`missing`); five regular-file URL observations reported
`until-first-authentication`; all eight volume observations reported `unsupported`.
Historical native16/runtime26.5 reported volume `supported`, with the same
FileManager/URL values. The fixture's own root was removed; hardware protection
and requirement waivers remainfalse. These are real new native observations,
**not**26 application Complete comparisons, qualified collection, strict L08
acceptance or proof of universal simulator incapability/physical-only blocking.

#### A21 native cleanup — FAIL, not repaired by artifact upload

Immediate and final Gradle stops actually exited0 (both `No Gradle daemons are
running.`). The direct lifecycle journal records six successful/reaped commands:
three inventories and create/boot/bootstatus. Exact owned UUID
`86A86790-96CE-4E95-8132-3EEAE7731A2E` remained last-observed **Booted**;
there are **no shutdown/delete commands or destructive cleanup barrier**.

`postbuild_evidence_preserved: false`: the timeout skipped ordinary raw xcresult/
app-result preservation and its required acknowledgement. Composer finalization
retained Foundation/readiness observations but ANDed the earlier false value;
successfully saving that false value is not successful required preservation.
The missing raw acknowledgement correctly denied both device destruction and
temporary-copy/results removal. This is distinct from the reviewed policy for a
later summary-save failure after successful barriers.

Owned processes remaining were empty, but an installed Parlor **unknown holder
PID22519** remained. An app-looking path is not PID/start ownership or kill/adoption
authority. The strict stop/fresh-census/direct-build-handle barrier was not reached;
six reaped lifecycle handles do not prove it. The copy/DerivedData/results were
not retired (`temporary_directory_removed: false`); copied-source inspection
was prevented, not a demonstrated source mutation. FIFO, worker, fixture-root and
Gradle successes do not erase these failures. Linux artifact review cannot
retroactively establish hosted device/process retirement.

Outer `actions/34275491965/native-cleanup/parlor-native-34275491965-1-cleanup.json`,
SHA-256 `841a2ef0c1cb4d3ccb9fa930625e230c6bb85df1234f36339aad0f9b18c26bed`,
retains **FAIL**, `removed: []`, `native-cleanup-unsafe-retain-owned-evidence`.
Main artifact **10077343585**, ZIP SHA-256
`ea22338008d089a93ab7baa2c3a86dfb4cc090e6bb885306597f797a8fec442c`;
cleanup artifact **10077346157**, ZIP SHA-256
`440f1a2d744d691e5566f38921279f1bfc1a985c2a80f41223d8f58155b8dee9`.
Complete transfer of28 canonical files is not complete required application
evidence or safe native cleanup. Raw API/logs/ZIPs/receipts remain retained.

Formal independent postrun: `reviews/native-34275491965-postrun-independent-01.json`,
SHA-256 `85ca368c5ee2850cc8fdad3449eca87c1a057e5d3adbad89585e525d608adc31`,
decision **`CONFIRMED_A21_FAIL_WITH_PARTIAL_APPLICATION_OBSERVATIONS_B22_NOT_RUN_AND_CLEANUP_FAIL`**.
This reconciles actual evidence; it does not approve corrections or another run.

### Remaining A/B work and safe next-operation gates

Use [`scripts/ci/NATIVE_CONTINUATION.md`](../../scripts/ci/NATIVE_CONTINUATION.md),
not a Store workflow. One coordinator owns dispatches; same-ref concurrency can
cancel earlier runs, so do not overlap focused/full executions.

- Additional **source-only** finding (`reviews/l08-strict-protection-investigation-02.md`):
  at25b1, paired Foundation template/consumer required26.5.0 while the hosted
  profile selected26.2. Exact trusted-profile propagation has been implemented and
  independently source-reviewed, with the actual20 profile controls passing.
  The bounded investigation itself is independently approved in
  `reviews/l08-strict-protection-investigation-independent-02.json`. A17/B18 never
  reached this guard; it is **not their observed ps failure cause**. A19 reached
  no app/Foundation execution either. A21 now adds the actual26.2 raw observations
  above, not a completed qualified collection. Public Foundation-source or these
  raw observations do not waive the strict gate or make failures physical-only.

1. Source approval is recorded in
   `reviews/native-command-and-runtime-repair-independent-correction-02.json`
   (the current corrected approval, not the superseded initial test-entry bytes)
   and `reviews/native-process-probe-source-independent-01.json`. Actual affected
   controls436, transport02 and independent external reconciliation are recorded
   above and were committed in22467846, then frozen by1efed50. The introduced
   qualifier20s-cap correction has
   independent source approval, actual focused45 PASS and affected transport03
   PASS; committed0607, frozen5f35 and fresh validator432 are now recorded above.
   The copy-only single-tap geometry/zero-counter fixture correction is also
   already reviewed (`reviews/l08-single-tap-independent-01.json`) and committed;
   do not add retaps or redo it as an unimplemented repair. A21 reached only
   pre-tap preparation in boot2, not the corrected boot10 callback. These
   completed source/control changes do not approve later changes or retries.
   Do not blindly repeat successful controls or erase retained failures.
2. A19 exposes a separate mutable-companion seam at frozen5f35
   `C/l08-storage-functional-companion-02/run_ios_readiness.py:434–470`:
   `own_simulator_metadata` uses unguarded `except BaseException -> owner.stop ->
   raise`, allowing cleanup to replace the primary error. The narrow correction
   is now **implemented and independently source-reviewed; its14 metadata controls
   passed within the26 primary-error controls**
   (`reviews/native-metadata-error-preservation-independent-01.json` plus
   lifecycle-controls01 above). It preserves bounded primary/secondary
   reporting, timestamps and canonical ownership clones. It was committed4276854
   and frozen e511. A21 exercised the new bound controls, but did not reproduce
   the old metadata fault; it is not proof of that fault's OS cause or successful
   simulator retirement.
3. The opt-in `--simulator-lifecycle=direct-owned-v1` implementation and controls
   are now independently reviewed; default remains `legacy-apphost`, with no
   automatic fallback. See `reviews/direct-owned-simulator-lifecycle-implementation-independent-01.json`,
   `reviews/direct-simulator-lifecycle-controls-independent-01.json` and
   `reviews/direct-lifecycle-author-review-closure-independent-01.json`.
   Exact unreaped child handles and a durable exact-resource journal govern only
   simulator inventory/create/boot/bootstatus/shutdown/delete. Build/runtime
   AppHost ownership still requires ps15/lsof30. Postbuild destruction requires
   retained raw acknowledgement, strict owner stop/fresh census, reaped handles
   and journal/source/custody checks. Metadata45/ordinary120/bootstatus300 remain;
   the separate480s lifecycle ceiling sits within unchanged600s outer grace;
   full command allowance plus10s retirement must fit. This does not guarantee
   all global scans/finalizers fit.
   The reviewed persistence distinction permits safe journal-attested cleanup
   after a later summary-save failure only when the earlier barriers succeeded;
   failure stays latched, cannot yield cleanup/runtime PASS or admit another lane.
   Required raw-ack or journal-append failure still denies destruction; see
   `reviews/direct-owned-simulator-lifecycle-persistence-policy-independent-01.json`.
   Committed4276854/frozen e511, A21 actually completed six direct lifecycle
   commands, but failed the postbuild barrier and never reached shutdown/delete.
   The Linux controls alone are **not native verification or dispatch approval**.
   Prior active-boot overlap remains a hypothesis, not a proven common failure
   cause. No unchanged retry, global-process authority, stale rows, privilege
   increase or larger ps/lsof timeout is justified.
4. Any approved next native scope requires separate source/control review,
   focused affected checks, committed reviewed changes, separately regenerated
   and committed mechanical inventory, exact normal push/remote confirmation and
   fresh bindings/independent dispatch approval. This ledger authorizes none.
   Never overwrite A17/B18/A19/A21, reuse consumed one-run approvals, or start B after
   unsafe A merely because the process exited. Preserve new outcomes and inspect
   them before another cycle; a full matrix is not a repair for ownership or
   preservation failure. **A23/B24 are future identities only: NOT_RUN**; no
   fresh post-A21 freeze, native bindings or dispatch approval is claimed here.
5. A still needs 13 functional boots / 20 operations / 26 protection comparisons,
   three retained-host fixtures / 42 operations, Settings/OS/lifecycle/image paths.
   Historical native16 remains FAIL: 14/15 observed functional operations passed,
   boot10 Home callback stayed zero, boots11–13 and retained-host paths were not
   reached; all24 strict Complete comparisons failed. A17/A19 add no new protection
   observations. A21 completed only boot1/save and part of boot2, with zero target
   taps; its new raw Foundation26.2 observations do not satisfy the26 application
   comparisons or qualify collection. The companion never proves original strict
   L08 PASS. Preserve and separately report functional outcomes and strict
   metadata mismatches.
6. B still needs eight genuine normal-source launches plus a separate ninth native
   libproc image query, actual native return/structure sizes, artifact/signature/
   notices bindings and cleanup. Historical native15's eight launches/48 samples
   passed but ninth vmmap provenance failed; 187 controls and diagnostic metadata
   do not query Parlor. B18 failed before that scope; B20/B22 never started.
   A21's eight copy-instrumented launches are not normal-source B evidence.
   A partial/failure permits B only when A's full ownership/source/control/cleanup
   receipts are safe, not merely because its process exited.

#### Historical post-A21 proposal cutoff — no execution or retry approval

`reviews/a21-qualified-budget-proposal-01.json`, SHA-256
`434a4dcbccfa56852ac99f7393804ef049987040a5ffc894f80c32291776c660`,
is **PROPOSAL_ONLY_NOT_SOURCE_REVIEW_EXECUTION_OR_DISPATCH_APPROVAL**. At the
preserved proposal cutoff, the assigned authors reported implementation in progress,
not held final source or independent approval:

- Qualified A-only proposal: command5400s, XCTest default3000s/max3300s; local A
  stays2700/1200/1500, normal B stays2700/120/240. The focused `native-evidence`
  job ceiling is proposed240min, with a conservative full-allowance guard;
  full/preflight120min and probe10min remain. Inner6000s/finalizer600s, AppHost
  ps/lsof and lifecycle budgets are not enlarged. These numbers are proposed
  control changes, not assurance that the complete hosted scenario will fit.
- The companion driver correction is a one-shot bounded exceptional raw collector
  **after immediate Gradle stop**, preserving primary/secondary errors and exact
  custody. It must not fabricate acknowledgement or bypass unknown holders.
- The separate adapter correction must deny B after inner timeout/cancellation
  even if cleanup appears safe. Workflow/contract and
  `scripts/ci/NATIVE_CONTINUATION.md` changes, tests, composer/pins and a new
  draft-freeze record need final different-agent review and affected execution.

No retaps, reduced scenario selection, protection/ownership/census weakening or
path-based process adoption is authorized. Final driver/composer pins are not
approved by the proposal. Complete the held source reviews and focused checks,
then reviewed commit, regenerated inventory commit, normal push/exact remote,
fresh preflight/bindings and independent one-run approval before A23/B24.
Historical A21 FAIL/cleanup FAIL and B22 NOT_RUN remain immutable.

#### Post-A21 implementation and actual affected Linux checks

The later source/control closure supersedes the proposal's pending-implementation
state, not any historical execution result. No application/gameplay/protocol,
privacy, protection, content or timer rule changed.

- Qualified A selects the existing single Xcode command's5400s whole allowance
  and3000/3300s per-test allowances; local A and normal B retain their old budgets.
  No test filter, split, retap or shortened wait was introduced.
- Exceptional and normal Xcode completion now both perform immediate Gradle stop
  followed by one bounded, allowlisted raw collector. The original exception is
  sticky. Missing/corrupt required evidence keeps acknowledgement false; the
  Foundation result remains conjunctive. This does not authorize unknown-holder
  adoption or destruction after incomplete retention.
- Only focused native-evidence has240min. Its first step creates the exact
  source/run/attempt/job-bound `CLOCK_MONOTONIC_RAW` token, using the same API in
  producer and consumer. Each lane must have full6000+600+600s remaining. The
  clock begins at the first step, not runner startup, and its nominal reserve
  is not a worst-case completion guarantee. Hosted execution remains unproven.
- B admission rejects canonical inner timeouts/cancellation/malformed receipts
  and negative command exits, even when outer cleanup appears safe. Ordinary
  strict/assertion failure may admit B only with all existing safety guards.
- The composition has12 reversible transforms; only retention seam8 changed.
  Six pins and the new nine-file freeze match;17 protected definitions and18
  historical freezes remain unchanged. The new rendered runner hashes to
  `dcb1f6ce32da3fc83b24bf2b2b868de68ac7fb35ca1539fc31140ce2589e60d7`.

Independent source reviews, all under `$N/reviews/`:

| Scope | Exact report / SHA-256 |
|---|---|
| Companion and retention interface | `hosted-l08-exception-budget-source-independent-01.json` / `ec35843efe93b07879ce581dde38f0f1add9abb183f9ac2e6a17bb2bb2f09e2e` |
| Adapter, workflow, contract and guide | `hosted-native-budget-admission-source-independent-01.json` / `579614bf2bb71a8440acaa811e7b63a20d058576fe1579fb29bc940868e281a8`; additive precision `hosted-native-budget-admission-source-precision-01.json` / `34c374048e169531a34380dda00f57ee2b84b8408054311bdabc277bfe627711` |
| Composer, pins and additive freeze | `a23-budget-preservation-composition-independent-01.json` / `9c4d3345f00e50e5902f8501269119b2bc538ea54db289e72cfbd8d62d784055` |
| Final regression source | `hosted-native-budget-preservation-controls-independent-02.json` / `2ef2bd8ce85db1e900dedfce1ad283fbdbf7c3eb1da68e0e75a0c27081af54af`, together with preserved01 |
| Exact eight-command packet | `native-budget-control-command-independent-01.json` / `4e2e46e86b6a48096aa4ae2084e1e77fa3b746917d118cceb197febc76e84b1f` |

Before execution, regression review corrected actual-main budget wiring, a
postread mutation phase, timeout-oracle negative-exit confounding and two
oversize fixtures. The last pair now uses valid JSON+whitespace at exactly
maximum+1 and requires the bounded reader's exact error, rather than accepting
a later JSON failure. Report01 is retained and superseded for those two oracles
by02. The optional size-guard mutant was only a static witness, not an executed
extra test. The final integration file is
`c5601e2dbc64956fa70310add4f8f25841e2d6e82af2f2e09798d38f93f7c09a`.

Actual completed cycles under `$C/evidence/`:

| Cycle | Actual result / outer receipt SHA-256 |
|---|---|
| `continuation-workflow-contract-01` | Static production workflow contract PASS, exit0; not the full release validator. `2de079b549c5c643e9330e2749d55b0a5d01d54eea84871e14b9f8b364e6dcef` |
| `continuation-transport-contracts-05` | Strict desktopTest actually executed:22 XML suites,277 cases,274 PASS,three existing physical multi-device loopback skips,zero failures/errors.49s,21 actionable tasks:14 executed/seven prerequisites from cache. `66cfc67cbdcc80576d5eb9fbd09b08a92a0e01fcc09fceba65ec592d03dcd0c2` |
| `continuation-native-budget-controls-01` | Eight actual commands and286 PASS,zero failures/errors/skips: primary29, integration54, Foundation-profile20, functional-copy12, composition55, adapter63, workflow52, sole probe-cycle guard1. `49294736ee15693a49c0728c52d1e04d733ddd058d12e12b042d3aa2f7e54dc3` |

The286 packet `native-budget-control-command-01.json` is bound at
`333afd6636ee2e41d3f9b780ea7b58554e199e5685131db0f5133e3abf103986`.
All110 pins and278 before/after control inputs match. Its eight child commands
reuse nine unchanged function definitions; the packet's narrower eight-function
prose is precisely corrected by its independent review, not by changing the
command or test count. Unchanged normal187, low-level lifecycle51, single-tap14,
toolchain12 and the other44 probe methods were unrequested, not reported skipped
or newly executed. Their source applicability was independently inspected.

Each cycle's complete before/after source and runner objects match. All ran at
e511 plus held working-tree edits, not a clean/future freeze. The earlier static
contract and transport runs bind integration667bcdf5; only the two reviewed
oversize-test fixtures changed afterward to finalc560 before286. Do not relabel
those earlier whole-source bindings as the later one.

All three immediate Gradle stops returned0, with no remaining owned workers,
outputs or cleanup errors. Transport's seven exact live build directories and
every cycle's scratch were removed; archived reports were retained. Root also
reconciled all five external Foundation/composition allocation-cleanup pairs,
including a marker embedded after the unittest prefix, and freshly observed all
five exact parents absent. The composition's55 distinct IDs and two test hashes
match its raw result marker; `native_execution` is false. Independent actual
postrun review `reviews/native-budget-controls-postrun-independent-01.json`,
SHA-256 `fb3c0a104850a2f2a0a60b26503ec526243e2f86cb80482b9e8b1c58eb536c68`,
has now accepted these scoped results and separately cleared all five external
custody/absence pairs. The packet's pending-external-review field is preserved;
this additive review closes it, not outer PASS alone. Neither is native proof
or dispatch approval.

Current source-based strict-protection context has independent review
`strict-protection-a23-investigation-context-independent-01.json`, SHA-256
`805f2da84ce7811a6cd27c325d66be2b74c570100a9e4afcd35e51e35834cd39`.
A16/A21's16 raw Foundation rows differ only in eight volume values; neither
comparison establishes SDK causality. A21 collection is still NOT_RUN. The
prospective26 production comparisons include the final boot12 retained-dual
pair; these are not Foundation's13 FileManager/URL query observations. No
strict failure is waived or reclassified as physical-only.

Next: close independent delivery review, commit source/control,
regenerate and separately commit inventory, check and push with exact remote
equality. At that frozen source run the full local release validator04, including
fresh inventory and pinned ShellCheck/actionlint, then fresh nonbuilding native
preflight and independent exact bindings before one justified A23/B24 run.
Preserve every old failure. This ledger itself is not execution authorization.

## C — C01 at25b1, C02 at5f35; fresh C03 completed at e511 only

### Historical C01 — preserve its25b1 execution binding

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

C01 did not execute at22467846,1efed50 or5f35. Never promote its25b1 binding/results
to a later SHA. The fresh C02 below is a separately approved, newly executed chain,
not relocation of historical evidence. Preparation remains documented in
`reviews/dependency-chain-linux-preparation-01.md`.

### Fresh C02 — actual export/render/candidate consumption at clean5f35

| Cycle under `$C/evidence/` | Actual result and outer receipt SHA-256 |
|---|---|
| `final-dependency-export-linux-02` | PASS/exit0/stop0; strict `writeResolvedDependencyInventory` executed. Five actionable tasks: four executed, one compilation task from cache. androidRelease239 components/129 artifacts; each iOS graph167/81. `ef7d3881f77ad0ff4177f7247f6865f9a1996a2fa904b441898af82c11c400ae` |
| `final-dependency-render-linux-02` | PASS/exit0/stop0; actual renderer: four graphs,456 Maven components, unresolved `[]`. `30b10b62d293ad35d0b7c32b6a15719b532278cbd5580d1ce06f09522aa91971` |
| `final-candidate-input-linux-02` | PASS/exit0/stop0; actual candidate consumer, not `--controls`, tests or app runtime. `dff3d1ff4369f338ede10946892b67ad0157c92b2305dd97b07223721dbbf64f` |

Fresh `$N/candidate-input-binding-02.json`, SHA-256
`f6a469a0df64b1f9c294c5ecdbd6f7b2f36642153928c7470d0827126e26c5af`,
was approved before candidate execution in
`reviews/candidate-input-binding-independent-02.json`, SHA-256
`fc03d69f55d09e6ff6b4f7a05a12eba933fccae6c55dfd8b03204995af762462`.
All four **C02** coupled receipts actually passed:

- Consumer `candidate-input-verification.json`: `PASS_SCOPED_CANDIDATE_INPUTS`.
- Adapter `candidate-prerequisites.json`:
  `PASS_SCOPED_CANDIDATE_INPUT_EXECUTION`, consumer0, optional parser cleanup true.
- Bootstrap `schema-base-prerequisites.json`: `PASS_OWNED_SCHEMA_PREREQUISITES`,
  candidate mode, inner0, base-package cleanup true.
- Outer `receipt.json`: PASS/exit0/stop0, no cleanup errors.

The actual C02 consumer reconciles456 Maven components,459 publisher POMs,
1,507,794 metadata bytes,26 source-notice resources and511 consumed files.
Consumed-manifest SHA-256
`5abe9120172a3e91961eec3a78868dd5a362935fbb251125d3b9538a54e6fe96`.
**Four actual BOMs were validated using three pinned schema files**;
`iri-reference` was encountered and checked, missing formats `[]`. Registration
of other formats is not a claim they were all exercised. This is dependency/input/
source-notice/schema consumption evidence, not app runtime or final-package proof.

All three before/after source objects bind clean5f35/source825 and match; runner
bindings are stable. Each immediate stop exited0; owned parser/scratch directories
were removed, no owned workers/live outputs/cleanup errors remained. Cleanup
completed at `2026-09-08T14:50:22.552970+00:00`,
`2026-09-08T14:54:01.612109+00:00` and `2026-09-08T15:10:15.550640+00:00`.
These passing Linux cleanups do not supersede A19's later failed native cleanup.

Author postrun `reviews/candidate-input-postrun-author-02.json`, SHA-256
`d22679559d86f8481659296623dc4f0773d148b68ea4070fb01570c64beb0599`,
has independent raw-evidence approval in
`reviews/candidate-input-postrun-independent-02.json`, SHA-256
`f84071e1b693f0effc266b311dbdd084946952444d6093b55375917805e5b16e`:
**`PASS_SCOPED_FRESH_C02_CHAIN_AT_5F35A20D_ONLY`**, no blocking C02 findings.
This closes the fresh C02 chain at5f35, not A/B/D or owner/legal/Store requirements.
A further source/control/inventory freeze requires fresh applicability/binding
review; do not claim that these checks executed at an untested future SHA.
The C03 packet `reviews/dependency-chain-afterfreeze-execution-03.json` and
`reviews/dependency-chain-afterfreeze-execution-independent-03.json` are reviewed
**historical preparation only**. The separately bound and executed C03 below
supersedes their pending-execution state without relabeling either packet or C02.

### Fresh C03 — actual export/render/candidate consumption at clean e511

| Cycle under `$C/evidence/` | Actual result and outer receipt SHA-256 |
|---|---|
| `final-dependency-export-linux-03` | PASS/exit0/stop0; strict `writeResolvedDependencyInventory` executed. Five actionable tasks: four executed, one convention compilation task from cache. androidRelease239 components/129 artifacts; each iOS graph167/81. `2a5462f3092a9313cc32e4fe86a80cdbcdf2cfdb502467fca72fc47d7c426571` |
| `final-dependency-render-linux-03` | PASS/exit0/stop0; actual renderer: four graphs,456 Maven components, unresolved `[]`. `2385794da9181199763be4d47c7b1ef2b6c589bd8c0807d83d52d649ea33a428` |
| `final-candidate-input-linux-03` | PASS/exit0/stop0; actual owned-schema candidate consumer, not synthetic controls or application runtime. `753d4aa0c45152344b65cb77eba9ec1eebc11ad744eecbe71173aea2461e7063` |

Fresh `$N/candidate-input-binding-03.json`, SHA-256
`f2cb3be23bd20b5a2ff2bc1411561bc291c74cb04a0970a1a274e5db7f81bd01`,
received pre-execution independent approval in
`reviews/candidate-input-binding-independent-03.json`, SHA-256
`19e67054888e1ed633125e93f88d8630d920d208333ba57e2e4e778958bd346d`.
All four coupled C03 receipts actually passed:

- Consumer `candidate-input-verification.json`:
  `PASS_SCOPED_CANDIDATE_INPUTS`, SHA-256
  `298168f6a8dcacce81d0a8fee13ce832d08c68a6ac5cdd858c97c2cf059a1b85`.
- Adapter `candidate-prerequisites.json`:
  `PASS_SCOPED_CANDIDATE_INPUT_EXECUTION`, consumer0, optional-package cleanuptrue,
  SHA-256 `69d4ae97bc28526327090f9a105e9f3dc2e6bfabe3bf143d5b1bf2ac94db5cf5`.
- Base `schema-base-prerequisites.json`: `PASS_OWNED_SCHEMA_PREREQUISITES`,
  inner0, base-package cleanuptrue, SHA-256
  `c96b86014a895d81b0af413fb968dc476df321a4d236971b960aa588554bdc9b`.
- Outer `receipt.json`: PASS/exit0/stop0, no cleanup errors.

The actual consumer validated **four BOMs with three pinned official schemas**:
456 Maven components,459 publisher POMs,1,507,794 metadata bytes,26 source-notice
resources /99,515 bytes,511 consumed files /5,567,850 bytes. Consumed-manifest
SHA-256 `19512563acee499b327948c1f564797cfa90ec180354cb4cedeb75979e9e3eb6`.
Actual `iri-reference` was encountered and checked, missing formats `[]`; other
registered formats are not claimed exercised. The owned base records retain
six packages /137 members /eight actual module hashes; optional records retain
two packages /55 members, **not** a separate optional-import hash map or random
temporary child paths that were never retained.

All three complete source/runner before-after objects match clean e511/source828;
held-current full bindings are independently captured in
`reviews/candidate-input-current-bindings-independent-03.json`, SHA-256
`5c0fd556e06f2f9b7ae909b06ae5d7c540ac913cd7029838c7608bfccff1ad99`.
Each immediate stop exited0, with no workers/live outputs/cleanup errors; owned
parser/scratch directories were removed. Cleanup completed at
`2026-09-08T20:04:20.370714+00:00`, `2026-09-08T20:08:39.237197+00:00` and
`2026-09-08T20:24:28.044916+00:00`. These Linux results do not supersede A21's
later failed native cleanup.

Author postrun `reviews/candidate-input-postrun-author-03.json`, SHA-256
`3f326b55875ca10f42b65a35e39ee624d0a21c233bee1b8e81410fa324355e52`,
has independent raw-evidence approval in
`reviews/candidate-input-postrun-independent-03.json`, SHA-256
`6e6d21a6fb9b085fbbcbcf3cba8d74bde392ee09f45085e59c17a84b926b5df3`:
**`PASS_SCOPED_C03_CHAIN_AT_E511_INDEPENDENTLY_ACCEPTED`**, findings `[]`.
This completes scoped C03 **at e511 only**, not a later source freeze or A/B/D,
final-package, owner/legal, physical-device, signing or Store readiness.

Strict consumer binding keys remain exactly `schema_version, source,
export_receipt, render_receipt, graphs, report, schemas, lane, consumer`;
`source` binds commit/tree/diff/source-manifest hashes. Do not add bootstrap keys
or loosen schemas. Ordinary Linux cycle evidence remains under `$C/evidence/`.

Fresh C04 preparation is now separately reviewed in
`reviews/dependency-chain-afterfreeze-execution-04.json` (SHA-256
`48cc66b28c3016c2e0f7c85dc9b7a929fe2c861ce90c177802757ac1f4c7e049`) and
`reviews/dependency-chain-afterfreeze-execution-independent-04.json` (SHA-256
`7c9990b816096d63963bdd794cd8c316cbc94daf10343fbdf2d333d3de6d3fb8`).
The three commands change only cycle suffixes;11 control and three schema pins
remain unchanged. This is preparation only: C04 producers/consumer are NOT_RUN
and binding04 must be freshly created/reviewed after actual frozen producers.

## D — still unexecuted: combined qualification and final reconciliation

The packet `reviews/full-qualification-afterfreeze-01.json` and
`reviews/full-qualification-afterfreeze-independent-01.json` are reviewed
historical preparation only, not a dispatch approval. D remains **NOT_RUN** and
is held while the post-A21 corrections/reviews/freeze are unfinished; do not
launch the stale prepared expensive matrix. After the independently reviewed
new source/inventory freeze and fresh scope/binding approval, run existing workflow
`full` at that frozen SHA; obtain all five jobs and
five main/five cleanup artifacts. Inspect raw executed tasks/tests, cache hits,
skips, Apple linkage versus runtime and artifact digests. Independently reconcile
A–D, original16/timer evidence and any changed scope; record actual commits/run
identities here; commit/push completed evidence and verify remote equality.

The additive current packet `reviews/full-qualification-afterfreeze-02.json`
(`feba34720798a3a70608dcf81b6239a2357c21fb53e94214601390b5febc8403`) has
independent preparation-only approval
`reviews/full-qualification-afterfreeze-independent-02.json`
(`10c0acc986adf9175ca4c3a10acb798206a46ea9f6a6920f80c7e6c24267b716`).
It binds current held controls, not a future approved SHA. Full remains five
jobs/ten main+cleanup artifacts and120min Apple; its nine focused-only steps
are intentionally skipped, including the new clock. Full ignores the focused
`frozen_source_sha` input: reconcile all five actual checkout/claim identities.
Use the reviewed gh2.45-compatible raw `--paginate` collection with complete JSON
decoding, REST `run_attempt` and attempt-specific logs; do not repeat unsupported
`--slurp` or `gh run view --json attempt`. Inspect A/B/C before a fresh one-run
full approval. No Store workflow is enabled or dispatched by either packet.

The prior reconciliation `reviews/frozen-source-reconciliation-prep-01.json`
remains a preserved earlier-cutoff audit, not the actual later A/B/C result.
Fresh `reviews/frozen-source-scope-reconciliation-5f35a20d-01.json`, SHA-256
`c22bce87856e880b1f0f27a2410a8b385746f2885016b1495f4f50a237babcf0`,
reopened632 module/runtime files plus root/build/contracts and found no executable
application/build/architecture-contract delta from ec0de/25b1 to5f35. Original16/
timer evidence retains its reviewed historical scope, without repeating that
campaign. Actual432 validator results and changed-control applicability were
reconciled; the earlier45/transport03 cycles remain1efed50-plus-dirty executions,
not new5f35 runs. This report deliberately does not certify C02, A/B or full D;
their separately scoped evidence/remaining work is recorded above.
Source/control or semantic-contract changes require affected requalification
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
