# iOS diagnostic-control repair — 2026-10-27

## Scope and issue mapping

[Issue #265](https://github.com/Apdelrahman1911/parlor/issues/265) tracks confirmed
verification-tooling drift found after the October overhaul merge
`d72a0d839cf0232e8c4fbf0940ccd1d614acbac4`. Changes are to copy-only observations,
diagnostic identities, test fixtures and CI enforcement. No shipping Kotlin/Swift,
protection constants, authentication, dependency pins or Store gates are changed.
`AGENTS.md`, the original normal-launch driver, and production FIFO ownership
logic remain byte-identical.

This repair does **not** resolve [#258](https://github.com/Apdelrahman1911/parlor/issues/258),
prove A37's root cause or establish physical-device protection/recovery.

## Reproduced defects and corrections

| Finding | Root cause / impact | Correction and regression |
|---|---|---|
| L08 Home copy fails | Obsolete Resume card signature; actual UI now separates Resume and discard | Inject an optional copy-only modifier into the real clickable Resume Row; preserve enabled/semantics, callback and independent discard. Reject missing/ambiguous/changed targets and repeated instrumentation. |
| Raw-retention fixtures fail | Extracted real validator namespace omits `HOST_PREPARE_FAILURE_STAGES`; downstream finalizer assertions also fail | Include the actual constant, leaving collection/validation code unchanged; complete raw-retention and finalizer integration cases pass. |
| Strict-runner pin stale | Reviewed Debug bundle-ID migration predates the test pin | Pin current exact bytes **and** prove reversing only that migrated line reconstructs the original SHA-256. No blanket pin refresh. |
| Application/normal diagnostic targets obsolete ID | Seven active controls/provenance paths still use `com.parlor.app.debug`; synthetic receipts agree with each other but not the built app | Align Kotlin/Swift/XCTest, receipt validation, container lookup and normal provenance parser with `me.parlor.ios.debug`. Cross-control tests reject old, Release and other-platform identities. |
| FIFO tests fail on Linux | Claimed synthetic fixtures accidentally require native filesystem birthtime | Supply explicitly synthetic creation-time observations to the unchanged real metadata validator; real inode/type/UID/symlink checks remain. Separate tests prove missing birthtime never falls back to ctime and exercise real macOS metadata in Apple CI. |
| Native tests absent from routine macOS verification | Apple Foundation encoder assumes xcrun on every platform; complete control suites were not enforced | Execute full readiness and application-control suites in the existing owned Apple aggregate before KMP runtime tests; retain logs, require pipefail, and reject omitted/narrowed/commented/bypassed/reordered commands. Apple-only tests are platform-attributed on Linux and must execute on macOS. |
| Actual-app copy preparation rejects current Xcode project | Normal copy helper assumes exactly one shell script; the real project also has a legitimate localized-metadata phase. Synthetic one-line PBX fixture hides this drift. | Select the unique known Compile Kotlin object and validate both production scripts, names, shell paths, target ownership, references and framework-search counts before writing. Preserve metadata and every other project byte; reject unknown/duplicate/moved/changed phases and repeated transformation. Regression uses the actual repository PBX project. |

The application's storage lane calls unchanged `normal.Lane.prepare`, not the
L08 Kotlin instrumentation path. Thus the Home seam defect is an independent L08
blocker, not evidence of APP04 storage causality. The obsolete application identity
is a separate directly established execution blocker.

## Local verification (Linux / JDK 21)

The initial broad readiness run contained **338 cases, 8 failures, 44 errors,
1 existing skip**. The original failures are retained; they are not reclassified
as passing evidence.

After repair:

- Complete readiness suite: **345 unittest cases; zero failures/errors; three
  platform skips**. Two skips require actual Apple Foundation/macOS birthtime;
  one is the existing unavailable native-platform test. These are not native
  execution claims.
- Actual-app diagnostic synthetic suite: **27 cases, zero failures/errors/skips**.
- Normal-launch diagnostic suite: **243 cases, zero failures/errors/skips** with
  an explicit private, task-owned `TMPDIR`. Its C observer uses synthetic ABI and
  identity inputs; this does not establish real Darwin ABI/process-map behavior.
  An earlier invocation without the required `TMPDIR` correctly failed before
  that class executed; the supported invocation did not relax the guard.
- Workflow-contract suite: **84 cases, zero failures/errors**.

### First hosted execution exposed a canonical-temporary setup defect

The new complete macOS gate in
[run 40464938770](https://github.com/Apdelrahman1911/parlor/actions/runs/40464938770)
at `70fadca7` ran all 345 readiness cases. The actual Apple Foundation encoder
and native FIFO metadata cases passed, but **32 fixture assertions failed**:
the default macOS temporary path `/var/folders/...` resolves to
`/private/var/folders/...`, violating existing exact-path ownership assertions.
Application controls and KMP runtime tests were correctly not executed after
that failure. Native resource retirement and final checkout cleanup passed;
those cleanup results do not convert the test failure to a pass.

The correction supplies a freshly allocated private `TMPDIR` under the exact,
non-symlink, canonical `RUNNER_TEMP`, scoped to both control-suite subprocesses.
An EXIT finalizer uses only `rmdir`: nonempty contents are preserved and fail;
the original test failure is retained. The scope ends before Gradle starts.
No ownership assertion is relaxed, no failed test is skipped, and no arbitrary
temporary directory is deleted. Workflow mutation tests reject alias checks,
global/default paths, dropped finalization, force removal and cleanup masking.
The revised source requires new full CI and native preflight/control hashes;
the first hosted execution does not qualify it.

The actual revised shell scope was executed locally against canonical and
symlinked parents, an injected test failure, and a synthetic unexpected child.
It preserved failures/unknown contents and retired only empty owned directories.
Both complete Linux suites then passed under that scope: 345 readiness cases
(three platform skips) and 27 application cases; the parent was empty afterward.
The execution parent was outside the checkout, as the existing copy guards
require. An earlier local proof inside the checkout correctly failed those
guards and is retained as rejection evidence. All 85 revised workflow-contract
cases passed. Hosted verification at the corrected head is still required.

The committed-source strict Gradle check at `ccb27eb8` subsequently failed in
release automation: pinned Actionlint/ShellCheck reported SC2329 because the
EXIT trap's unquoted function name was not recognized as a function invocation.
All preceding release Python tests and inventory validation passed; this is
not a successful aggregate check. Quoting the trap command alone at `e070fae1`
did **not** fix SC2329; its fresh check failed with the same finding after 690
release Python cases passed (one environment skip). The finalizer now also has
an explicit invocation after both successful suites, with the EXIT trap retained
for failures. It disables that trap before retiring the empty directory and
exits with the original status, so cleanup runs once on either path. Exact
workflow guards and mutation tests require both paths. No warning suppression,
analyzer downgrade or force deletion was added. Fresh committed-source checks
remain required; the two failed attempts are preserved.

Commands:

```bash
python3 -B -m unittest discover -s scripts/verification/ios-readiness -p 'test_*.py' -v
python3 -B -m unittest discover -s remediation-runs/2026-09-08-continuation/protection-application-01 -p 'test_*.py' -v
# Use an explicitly allocated private TMPDIR for the complete normal-launch suite.
python3 -B -m unittest scripts.release.tests.test_workflow_contract -q
```

Exact committed-source Gradle checks and the full current-head six-lane hosted
run are required before merge. This report does not pre-certify pending checks.

### Current-source actual-app preparation failure

[Preflight 40468591858](https://github.com/Apdelrahman1911/parlor/actions/runs/40468591858)
at `116ed82656a96e0617b293dc8dea49a88e047f54` independently verified 2,345
source files, 2,384 immutable source/control blobs, 114 controls, producer/tree,
ZIP and cleanup bindings. It built no application. The reviewed subsequent
[application diagnostic 40468931134](https://github.com/Apdelrahman1911/parlor/actions/runs/40468931134)
failed **before any build, XCTest, storage invocation or Simulator allocation**:
`Unexpected actual framework-search or build-phase wiring`. The project has
two legitimate shell scripts, not the obsolete synthetic fixture's one.
This establishes copy-tooling causality only; it produces no protection observations.

The inner finalizer reported source/control unchanged, temporary allocation
removed and cleanup PASS. The outer verifier correctly retained canonical
custody with cleanup FAIL and `removed: []`: no direct Simulator lifecycle/journal
exists because preparation stopped before constructing it. An inner PASS does
not satisfy that outer schema. This fail-closed distinction remains unchanged;
no fabricated lifecycle, cleanup exception or legacy fallback was introduced.

The two-phase repair executes **14 focused copy regression cases** (including
adversarial subcases), **251 complete normal-launch controls**, **345 readiness
controls** (three Linux platform skips), and **27 application controls** with
no failures. Rejected projects leave both copied inputs unchanged. These are
local tooling tests, not native runtime results. Fresh source inventory,
committed-source strict checks, hosted CI, control-delta review and a new
preflight must precede another actual-app diagnostic; the old approval is not reused.
At committed inventory head `9f172dc3`, strict `:shared:transport-p2p:desktopTest
productionReleaseAutomationCheck` passed with exit0, private Gradle stop0 and
source unchanged. Transport332 receipts (three real-LAN skips), release690
Python cases (one local environment skip), inventory and checksum-pinned
ShellCheck/Actionlint passed. Later observer edits require new affected checks.

### Fresh main public-navigation failure — not qualified

[Run 40466265136](https://github.com/Apdelrahman1911/parlor/actions/runs/40466265136)
at main `9d4f369d2b1581f5c157b67cce3ff0fada49effa` failed
`testAdditionalGamesExposeOnlyMultiplayerSetupInEnglish` at the Ghamza
Host-to-permission transition. All four native container cases and the other
five application cases passed; the failed case prevents iOS UI qualification.
Linux/Android and three desktop jobs passed; pending/failed Apple work is not
converted to a full-main qualification.

Artifact `12718053090` was downloaded and its ZIP SHA-256 independently matched
`371327dbe077fcf076c555c34c19c541421db56614834d79acdc8bdb4fbb5e2f`
and the producer commit. Retained XCTest activity/attachment references,
binary-plist synthesized event, public accessibility hierarchies and screen
recording show the Host tap at `(201, 329.8333)` inside the visible Host frame
`(24, 279.3, 354, 101)`. The app still displays the same enabled Host/Join setup
at 07:49:43.904 through 07:49:56.798; no permission screen or alert appears.
Thus repeated permission searches/swipes cannot repair the missed transition.
The video also confirms the catalog-to-setup transition completed before this
tap. Why the native tap did not advance remains **unverified**; do not attribute
it to permission evidence, increase a timeout, auto-repeat the action, or claim
a shipping fix without further instrumentation/runtime evidence. Failed
artifacts remain preserved outside shipping source.

[Issue #267](https://github.com/Apdelrahman1911/parlor/issues/267) tracks the
distinct native-input investigation after open/closed issue and related-PR
deduplication. The XCTest journey now observes the actual Host/Join transition
immediately after one unchanged native tap: the source must disappear and the
permission destination must exist. It does not search by swiping another
screen, lengthen the tap, or replay an action. On failure only, it retains a
public setup screenshot and non-sensitive existence/frame/app-state metrics.
A source-contract regression guards those requirements; it is **not** native
touch verification or a root-cause fix. Fresh hosted XCTest remains required.

## Fresh protection observations and outstanding gates

[Standalone probe 40419513891](https://github.com/Apdelrahman1911/parlor/actions/runs/40419513891)
at the merged baseline completed source/control/ZIP/raw-report/cleanup validation.
Strict synthetic Complete remained **0 PASS / 5 FAIL**. Simulator FileManager had
no protection key; same-inode host FileManager and file NSURL reported
until-first-authentication. Raw class was 3; APFS capability was unsupported.
One-call host Complete setter returned false / Cocoa256 and retained class3.
Underlying POSIX22 belongs to historical H03, **not** this fresh setter record.
Scratch observations outside the app sandbox do not identify production causality.

[Application preflight 40419989333](https://github.com/Apdelrahman1911/parlor/actions/runs/40419989333)
verified 2,343 source files and 114 controls, but built no app. The subsequent
identity finding invalidates its suitability for new application execution.
Revised controls require a fresh committed-source preflight, immutable-delta
review and exact hashes before the existing guarded evidence dispatch.
Historical failures are preserved. Physical devices, real-LAN interruption,
lock/unlock, battery/thermal, native accessibility, signing/account acceptance and
release approval remain independent outstanding gates. No Store publication.
