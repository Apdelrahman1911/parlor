# Continuation execution ledger — 2026-09-08

**1efed50 diagnostic FAIL; qualifier-budget correction reviewed and Linux-checked; next freeze pending; NOT READY.** Read `AGENTS.md`,
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
- Earlier inventory-only commit, **A17/B18/C execution HEAD**:
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
- Following inventory-only commit, **current pushed execution HEAD**:
  `1efed50cbc7856c11867e4cb088ba3b68716c8fc`; tree
  `bcabd2fd29eaea276bee31ec4ff6b74b63a79366`. Inventory15,553 rows, SHA-256
  `3e067a3ccf70967663c2f6ddedff1c078a993312b13c968fd8d34d524fa7cc89`;
  frozen source825 entries, SHA-256
  `9aafa1d908884f2c083d75c2959be625806ea7984da4c72e07b0f2e60f45ff4c`.
  `reviews/hosted-probe-freeze-01.json` retains normal push exit0 and exact remote
  equality. A direct `git ls-remote` during this refresh also returned exact1efed50.
- Fresh validator02 and the authorized initial diagnostic actually executed at
  clean1efed50, not a future correction/delivery revision. The diagnostic failed
  before process observations, as detailed below. The narrowly scoped qualifier-
  budget correction is independently source-reviewed; its actual45 controls and
  affected transport03 checks passed at1efed50 plus reviewed working-tree changes.
  Next source/inventory commit SHAs and fresh native bindings are **not yet
  established**. A19/B20, refreshed C and full D remain unexecuted; no repeat
  diagnostic approval follows from the earlier one-run approval.

Aliases: `C=remediation-runs/2026-09-07-local-readiness`,
`N=remediation-runs/2026-09-08-continuation`.

## Executed Linux evidence

All paths in this table are `$C/evidence/<cycle>/`. Each retains `receipt.json`,
raw logs and stop/cleanup evidence. The first five are working-tree-bound cycles;
the sixth actually executed at clean25b1; the next two bind4b8 plus reviewed
working-tree changes; validator02 executed at clean1efed50. The last two bind1efed50
plus the reviewed budget correction and then-held ledger. None qualifies a future
SHA; repeated test counts are not unique coverage.

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

All eleven outer receipts record source/runner stability, **stop0**, no remaining
owned workers/live outputs and no outer cleanup errors, including both failed
cycles. Repair-controls01's failed postflight and additive raw-custody reconciliation
remain separate above. The three C cycles below satisfy the same outer cleanup conditions:
fourteen Linux outer receipts in total, not fourteen application-runtime passes.
Required evidence and failed receipts remain retained. This ledger update ran
no build/test/native worker and did not start or clean another agent's lane.

## A / B — actual hosted Apple evidence and remaining scope

The earlier preflight and A17/B18 runs used `production-verification.yml` at exact25b1, attempt1,
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

### Reviewed corrections and next qualification-budget gate

Use [`scripts/ci/NATIVE_CONTINUATION.md`](../../scripts/ci/NATIVE_CONTINUATION.md),
not a Store workflow. One coordinator owns dispatches; same-ref concurrency can
cancel earlier runs, so do not overlap focused/full executions.

- Additional **source-only** finding (`reviews/l08-strict-protection-investigation-02.md`):
  at25b1, paired Foundation template/consumer required26.5.0 while the hosted
  profile selected26.2. Exact trusted-profile propagation has been implemented and
  independently source-reviewed, with the actual20 profile controls passing.
  The bounded investigation itself is independently approved in
  `reviews/l08-strict-protection-investigation-independent-02.json`. A17/B18 never
  reached this guard; it is **not their observed ps failure cause**.

1. Source approval is recorded in
   `reviews/native-command-and-runtime-repair-independent-correction-02.json`
   (the current corrected approval, not the superseded initial test-entry bytes)
   and `reviews/native-process-probe-source-independent-01.json`. Actual affected
   controls436, transport02 and independent external reconciliation are recorded
   above and were committed in22467846, then frozen by1efed50. They do not approve
   unreviewed later changes. The introduced qualifier20s-cap correction now has
   independent source approval, actual focused45 PASS and affected transport03
   PASS as recorded above. Both postrun reviews are closed; this ledger's
   independent review remains a separate gate. These working-tree-bound checks do not authorize
   native dispatch. Do not blindly repeat successful controls or erase the
   retained cycle01 FAIL.
   Primary and secondary cleanup errors remain separate; retain FAIL/finalizers.
   Do not edit immutable ownership controls, increase the original ps15s bound, use stale process
   rows, accept incomplete observations, weaken protection checks or change the
   qualified toolchain as an unexplained workaround.
2. After remaining independent reconciliation, commit reviewed source/controls, regenerate and commit mechanical inventory
   separately, check/push/verify the exact new SHA, then create fresh bindings.
   Obtain a fresh one-run approval before any corrected diagnostic. Its intended
   scope still compares unchanged bounded15s full ps,
   separate metadata-only ps and bounded libproc metadata before/after one owned
   simulator boot, with CPU/memory/VM counters and ps binary identities. It also
   records one nonbuilding `showBuildSettings` overlap/liveness observation.
   It must not retain raw unrelated argv/environment or turn a failed observation
   into permission to signal a process or run A/B. A no-app probe cannot prove
   behavior under Xcode build load. Preserve outcomes before deciding a retry.
3. Obtain fresh source/control preflight approval for any later A/B execution;
   the reviewed next adapter identities are `ios-readiness-19` (A) and
   `ios-readiness-20` (B), still pending source/inventory freeze and dispatch
   approval. Never overwrite A17/B18.
   No A19/B20 or repeat diagnostic approval has been issued at this checkpoint;
   the initial1efed50 diagnostic failed before its observation stages.
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

**Refresh export/render/candidate execution and approval after the next corrected
source/control/inventory freeze.** C did not execute at22467846 or1efed50. Never promote the25b1 binding or results to
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
