# Continuation execution ledger — 2026-09-08

**Latest checkpoint (2026-09-10): Windows-only PASS at730a; Settings35 FAIL
before tests/no captured labels. Settings36 CLI correction reviewed/adopted;
6/6 local controls PASS on730a+working edits. A33/strict failures remain;
historical B30 and C06 scopes retained; final same-source D pending. NOT_READY.**

Read `AGENTS.md`, `CONTINUE_HERE.md` and the preserved continuation handoffs.
Paths use `C=remediation-runs/2026-09-07-local-readiness` and
`N=remediation-runs/2026-09-08-continuation`. Ledger edits do not waive clean
commit/tree, inventory, control or platform bindings; never relabel older runs.

## Latest checkpoint — 2026-09-10 (Windows accepted; Settings36 pending)

- Windows/Settings35 execution source: `730a91e4dade4dbc2168b5367e20c3d0f42237e1`,
  tree `1f71dc64bc7680f461efc3e841dc051579a0d993`.
- A33/C06/fullD source: `4ccfafcfc361231cfb730178b3798ebcb6304770`.
- Next source/inventory commits, tree and remote confirmation: **PENDING root insertion**.
- Fresh Settings36 preflight/control approval/evidence run IDs: **PENDING; NOT_RUN**.
  The730a Settings35 preflight cannot authorize changed Settings36 controls.

### Latest actual results — scoped denominators, not readiness percentages

- **Windows34431938089/1 PASS at730a:** 1,337/1,340 cases passed (99.8%),
  three explicit physical-LAN fixture skips, zero failures/errors;203 fresh XMLs
  and13 actually executed desktop-test leaves. Native-distribution download and
  Android resource processing passed, not native/Android app runtime. Other four
  workflow jobs intentionally skipped. Source/167-path cleanup accepted, stops0.
  Evidence: `$N/actions/34431938089/`; review:
  `$N/reviews/windows-34431938089-actual-independent-01.json`.
- **Settings preflight34432704582/1 PASS at730a:** qualified Xcode26.3/17C529,
  iOS26.2 simulator profile; fresh105-row bound control package reviewed.
  Source/control/toolchain observation only: no app build, simulator or tests.
  Review: `$N/reviews/native-settings-preflight34432704582-admission-independent-01.json`.
- **Settings35 evidence34433711685/1 FAIL at730a:** Xcode exited64 before tests:
  `Must specify -test-iterations with more than 1 iteration.` Zero executed
  tests; no captured labels or established detached-test-target build feasibility.
  `build_attempted=true` is not compilation proof. No Parlor/Kotlin build or A/B
  runtime/provenance credit. Inner/outer cleanup PASS, stops0, simulator/temp
  removed,14 lifecycle children reaped. Actual log/xcresult/receipts remain under
  `$N/actions/34433711685/native-evidence/ios-readiness-35/`; review:
  `$N/reviews/native-settings35-34433711685-failure-independent-01.json`.
- **Settings36 correction adopted, not natively executed:** remove both inherited
  XCTest repetition option/value pairs, retain one exact required method and
  unchanged time/ownership/cleanup guards;35→36 avoids reusing the failed cycle.
  No OS selector changed; normal B's eight-repetition helper remains unchanged.
  Source approval: `$N/reviews/settings-single-execution-cli-fix-independent-01.json`;
  exact adoption: `$N/reviews/settings-single-execution-adoption-01.json`.
  **6/6 Linux control methods PASS (100% of these methods)** at730a+working edits,
  not clean730a native execution: five Settings controls plus one cycle guard.
  `$C/evidence/continuation-settings-cli-controls-01/`: exit0/stop0, stable source/
  controls during cycle, scratch removed, no remaining owned outputs/errors.

### Retained A/B/C/D results — do not restart or inflate them

- **A33 FAIL:**34412114359/1 at4cc;4/5 native XCTest cases PASS (80%), one OS
  integration failure at `retainEnglishPrimary`, no skips; xcodebuild65. Missing
  OS report/five-successful-test gate prevented the downstream final validators.
  Strict Complete comparisons: **0/26 PASS (0%);26 FAIL**. Functional storage,
  retained-host partial observations and Foundation controls do not waive strict
  protection or establish an exclusive simulator cause. Historical A31 subscopes
  remain separate. Reviews: `$N/reviews/native-a33-actual-independent-01.json` and
  `$N/reviews/native-a33-strict-protection-reinspection-01.json`.
- **B30 historical scoped PASS:**34362984912/1 at360fc797; eight repetitions of
  one normal Debug XCTest,48 periodic samples, then a separate ninth launch/three
  actual libproc queries. Applicability through730a is independently accepted,
  not a new730a run, identical future binary or whole-source equivalence.
  `$N/reviews/native-b30-730a-source-applicability-01.json`; A/B not run in Settings35.
- **C06 COMPLETE at4cc:** export/render PASS; **4/4 required candidate receipt
  layers PASS (100% of that conjunction)**, parser/base cleanup true, stop0.
  511 consumed files/four graph-BOM pairs establish scoped inputs, not app runtime
  or legal approval. `$N/reviews/candidate-input-c06-actual-independent-01.json`.
  **C07 NOT_RUN:** one fresh final-source export/render/binding/consumer chain,
  not relabeled C06 outputs or a consumer-only replay.
- **FullD34420745957/1 remains FAIL at4cc:** four jobs succeeded; Windows checkout
  and cleanup failed before current tests. Its successful Android/Apple/desktop
  results retain4cc. Windows-only730a does not create a same-SHA five-job PASS.
  Android's4cc cleanup attests Gradle/output paths, not emulator-worker/AVD exit.
  One final full is still required after the last settled source freeze; see
  `$N/reviews/final-qualification-scope-guidance-01.json` and the three fullD reviews.

### Next bounded work and limits

1. Freeze reviewed Settings36 source/inventory; fresh reviewed preflight/bindings,
   then one admitted capped diagnostic. Preserve Settings35 failure; no blind retry.
2. Only actual captured labels can support a separately reviewed copy-only OS
   selector correction and affected A verification. Never weaken strict protection.
3. After actionable source changes settle, run C07 and one final same-source
   five-job full qualification; collect actual tests/artifacts/cleanup and review.

Percentages above count cases/comparisons or required receipt layers only;
scopes overlap and cannot be added into coverage or an overall readiness score.
Original16 repairs and the Whodunit Leave Confirmation timer policy stay committed.
Physical/mixed-device/signed-candidate/accessibility evidence, Store identity,
signing/publication/privacy operations and separate owner/legal approvals remain
unverified/outside this task. **NOT_READY; no Store workflow or device test authorized.**

