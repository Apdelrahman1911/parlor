# Focused native continuation (evidence only)

The existing `production-verification.yml` still defaults to its **five-job full
qualification** on dispatch, push and pull request. The three explicit focused
native dispatch modes run only the existing qualified Apple job; they are not substitutes
for final five-job qualification. Unknown dispatch scope fails validation.

The separate `verification_scope=windows-only` follow-up runs just the unchanged
Windows verification graph, not native evidence or a new five-job result. It
requires `frozen_source_sha` to match the reviewed checkout/workflow SHA and
`native_selection` to remain `paired`. Git long paths are enabled only for that
job before checkout; archived evidence, line endings, and cleanup guards remain.
Results from other SHAs retain those identities and need independent applicability
review, never relabeling as fresh same-source execution.

The coordinator dispatches only after reviewed source/control commits, followed
by the mechanical-inventory-only commit and its freshness check. The A/B preflight
and execution pair must use that same frozen continuation-branch SHA. Never edit controls during
a native run; a correction requires a new freeze and preflight.

## 1. Nonbuilding preflight

Dispatch on `fix/local-readiness-2026-09-07` with:

- `verification_scope=native-preflight`
- `frozen_source_sha=<exact reviewed 40-character SHA>`

This verifies the clean full-history checkout and actual qualified
Xcode 26.3/17C529, SDK/runtime 26.2, Apple Silicon and public tool paths. It runs
the official source binder at the **actual hosted checkout path**, then both
official control-manifest commands. No application build, simulator allocation,
XCTest or native libproc query runs.

The four-file `native-preflight-<run>-<attempt>` artifact contains:

- `preflight.json` — source/producer/toolchain metadata and member digests;
- `ios-readiness-source-ci.json` — unchanged official binding;
- `l08-controls.json` and `normal-controls.json` — exact runner controls.

An independent reviewer must inspect the source binding, complete manifests,
toolchain/path observations and artifact digest. A successful preflight is only
`REVIEW_REQUIRED_NOT_RUNTIME_EVIDENCE`, not execution approval or a runtime pass.

## 2. Independently approved execution

Dispatch again at the **same SHA** with `verification_scope=native-evidence`,
`frozen_source_sha`, and all six inputs below:

- `preflight_run_id`, `preflight_run_attempt`, `preflight_artifact_id`;
- `preflight_artifact_sha256` (exact downloaded ZIP digest, 64 lowercase hex);
- `approved_l08_control_sha256`, `approved_normal_control_sha256` (independently
  reviewed `control_sha256` values, not the JSON-file hashes).

`native_selection` is a closed choice: **`paired` (default)** or `l08-only`.
Omission preserves the A-then-B chain. `l08-only` is accepted only for
`verification_scope=native-evidence`; unknown or other combinations fail before
native allocation. Preflight stays nonbuilding, paired and binds **both** manifests;
L08-only still requires both independently reviewed control hashes. The reviewed
execution request must explicitly select `l08-only`; actual selection is retained
in continuation state/result and the cleanup receipt, not inferred from old evidence.

The coordinator may choose L08-only when independent review of actual B evidence
and source/control applicability justifies avoiding an unchanged B repetition.
Earlier B remains at its original source SHA; selection neither certifies reuse nor
upgrades A's strict protection failures. A fresh reviewed source/inventory freeze
and same-source preflight are still required. L08-only uses the unchanged A runner,
budgets and all lifecycle/ownership/finalizer guards. It admits no B child or B
cleanup claim and records `unselected_lanes.normal=NOT_RUN_THIS_RUN`, never B PASS.
Success is `L08_ONLY_PASS`, never combined `PASS`; ordinary failure is
`L08_ONLY_NOT_READY`, and exceptions remain `FAIL`. No B receipt is synthesized.
Full five-job qualification stays separate and unchanged. Never use selection to
bypass an unsafe finalizer or to reattribute historical evidence to fresh source.

The adapter uses only read-only Actions API requests. It verifies the exact
producer repository, branch, source, workflow, attempt, successful result,
artifact identity/digest and four bounded nonsymlink ZIP members before writing
anything from the archive. It installs the binding byte-for-byte at the same
absolute checkout path and recomputes both control manifests before native work.

By default the canonical runners execute serially on independently owned fresh simulators:

1. A: composed L08, next cycle `ios-readiness-37`;
2. B: normal-source eight launches plus separate image observation, cycle
   `ios-readiness-38`, explicit `--image-observer=libproc`.
   With `l08-only`, B38 is **NOT_RUN_THIS_RUN**, not a new B30 result.

The failed `ios-readiness-17/18` evidence from run `34216788568` and failed
A19 from `34244902185` remain historical failures. A19's simulator cleanup was
not proven; B20 was **NOT_RUN**. The separate diagnostic `34239707887` did retire
its own journaled simulator, but failed other observations and did not run Parlor.
Its direct-child lifecycle precedent is not AppHost/build/provenance proof.
Run `34275491965` reached four passing UIKit methods, then A21's whole Xcode
command timed out during the monolithic functional test. General XCResult/storage
retention was skipped; only the separate Foundation fallback retained raw records,
not validated application provenance. A21's preservation acknowledgement stayed
false and an unknown app holder independently remained: cleanup **FAIL**, B22
**NOT_RUN**. Neither the correction below nor a later run repairs that receipt.

Both next runners explicitly select `--simulator-signing=adhoc`,
`--toolchain=qualified-xcode-26.3` and `--simulator-lifecycle=direct-owned-v1`.
New labels or successful Linux controls do not authorize a native retry: reviewed
source/control commits, a mechanical-inventory freeze, fresh same-source preflight
and independent approval remain required. No private signing or Store operation occurs.
A's strict protection failure/partial result does not suppress B, **provided A's
actual source/control/worker/device/Gradle cleanup receipts are safe and its
execution was not interrupted or timed out**. Admission separately checks both
outer execution and canonical inner top-level/command errors, including lifecycle
`RuntimeError`/`command-timeout`, cancellation codes/types, negative command exits,
deferred signals and malformed error/marker shapes. Ordinary strict/assertion
failure is not relabelled interruption; safe cleanup is not relabelled runtime
success. Failed, missing or contradictory cleanup stops the chain. No successful harness result
is upgraded to application runtime or file-protection enforcement proof.

### Explicit A and job budgets

Only A's explicitly selected `qualified-xcode-26.3` profile uses a whole existing
`xcodebuild test` allowance of5400s, default test execution3000s and maximum3300s.
The local26.5 profile remains2700/1200/1500s; unknown profiles fail before allocation.
The selected values are recorded in `xcode_time_budget`, the actual Xcode argv
and that command's `timeout_seconds`. B's2700s whole command and120/240s XCTest
allowances are unchanged. No XCTest split, test-filter reduction, retry, app or
production timing change is introduced.

Ordinary `native-evidence` has a240-minute job ceiling (the explicitly separate Settings diagnostic below is40 minutes). Full and native-preflight
remain120 minutes; the non-app process probe remains10. The first evidence-only
job step, before checkout, records `CLOCK_MONOTONIC_RAW` via
`time.clock_gettime_ns(time.CLOCK_MONOTONIC_RAW)`, bound to source SHA, job and
run/attempt in `PARLOR_NATIVE_JOB_CLOCK`. The adapter uses the identical shared
kernel clock, not cross-process `time.monotonic()` assumptions on older macOS
Python. No unavailable-clock fallback exists.

Before either A or B starts, a separate durable admission record requires at
least the full unchanged6000s native-child wait,600s finalizer grace and600s
evidence/upload/cleanup reserve within that240-minute clock. Missing, malformed,
cross-source/run, future or expired clocks deny the new lane; a denied B remains
**NOT_RUN** and does not invalidate A's honestly established cleanup safety.
Waits are never shortened to fit. The recorded clock starts at the first step,
not before job/runner startup; that unmeasured overhead is not certified by this
token. The600s reserve is nominal, and the finite ceiling/admission is **not a
guarantee** every setup, global scan, preservation, upload or finalizer worst case
fits, nor permission to repeat an expensive run blindly.

## Explicit lifecycle authority (not an AppHost replacement)

The wrappers' **default remains `legacy-apphost`**. The exact optional
`--simulator-lifecycle=direct-owned-v1` is parsed before allocation; unknown,
duplicate or nonqualified-profile selection fails. There is no ps/lsof-triggered
fallback. The helper is stdlib-only and its module, isolated controls and actual
wrapper integration controls are in all three native control manifests. The
composed draft adaptor's exact driver pin and a new additive nine-file freeze
bind the final companion; earlier immutable freezes are preserved.

Direct mode can issue only `/usr/bin/xcrun simctl` inventory, exact named create,
UUID boot/bootstatus and dedicated journal-authorized shutdown/delete. Each CLI
is captured through its unreaped direct `Popen` handle, with separately live-
bounded stdout/stderr, strict diagnostic-stderr rejection, closed error codes,
actual exit/reap evidence and bounded termination. No numeric-PID/group/name
fallback, shared-service signals, or unrelated inventory retention is introduced.
The exclusive fsynced append-only journal records exact source/control and
allocation/evidence custody, a nonce, pre-create UUID hashes, issued-create proof,
command intents/results and final retirement. Intent alone or a printed UUID
cannot authorize partial-create recovery. A fresh exact Shutdown observation,
successful delete, and both metadata and directory absence are mandatory.

Command allowances remain metadata45s, ordinary lifecycle120s and bootstatus300s;
original AppHost ps15s/lsof30s are untouched. Lifecycle finalization has a separate
480s ceiling within the unchanged outer600s cancellation grace. A new lifecycle
command starts only if its **full** allowance plus10s direct-child retirement fits;
otherwise cleanup is explicitly failed, never shortened, silently skipped or
accepted. This is not a promise every worst-case global scan plus device cleanup
fits the outer grace. Exhaustion retains resources/failed receipts and stops B.

Xcode, Gradle, app launch/container queries, XCResult extraction and image/libproc
observations still use the original strict AppHost authority. Before Xcode
`Popen`, direct mode latches a durable build-attempt marker. Following any build
attempt, A's one-shot raw collector runs after the immediate isolated Gradle stop
on both normal and exceptional paths. Xcode's original exception is sticky even
if stop, extraction or receipt persistence also fails. Summary/tests extraction,
the exact owned container query and allowlisted file attempts are independently
recorded; a failure does not discard another already-written safe observation.
Only bounded stable owned regular-file JSON is copied; storage/host/functional
operations and native failures keep their closed sanitization schemas. Missing
operations remain explicitly missing. Exclusive writes are never retried or
overwritten. No screenshots, automatic attachments, whole XCResult/container or
DerivedData dump is exported. The collector has a420s admission ceiling; each
new tool still requires its full120s AppHost allowance plus10s reserve, never a
shortened extraction. Budget exhaustion is failure, not an accepted skip.

Device destruction still requires the exact prior retention acknowledgement:
both structured XCResult views exited0, the entire safe available-container
retention path completed and its acknowledgement save succeeded. The composed
Foundation result is still conjunctive, never a replacement for the main
acknowledgement. These are retention outcomes, not scenario/runtime success.
Only **then** may original `owner.stop()`, fresh
strict refresh, no unknown/FIFO-attestation errors, and all direct build handles
reaping authorize destruction. Missing/failed preservation acknowledgement or its persistence blocks
simulator destruction **and** temporary copy/DerivedData/XCResult deletion, while
worker/FIFO/final-holder retirement checks still run. This deliberately preserves
resources after incomplete exceptional retention rather than inventing a
no-app/no-evidence success. Stable raw copying never adopts, signals or clears
an unknown live app holder; such a holder remains a separate cleanup denial.
Prebuild journal cleanup is independent of global ps, but final file cleanup
still requires all original worker/holder/FIFO/source/custody guards.

The adapter requests and validates this same mode, checks nonvacuous lifecycle
rows/build barrier and reconciles the actual bounded journal bytes, inode/custody,
source/control bindings, intent ordering and final result before another lane.
Legacy cleanup schema controls remain readable; they do not authorize this path.
Neither this helper nor its tests repairs or qualifies the separately unresolved
build-era global process observations, strict L08 protection, or normal-app libproc.

## Custody and cleanup

Canonical native runners retain their immediate/final isolated Gradle stops,
ownership checks, exact copy/DerivedData/simulator/FIFO cleanup and strict gates.
The adapter forwards cancellation as SIGTERM and allows their bounded finalizer
grace; it never indiscriminately kills workers or calls a global cleaner.

All bounded raw native evidence is copied **before** parsing its receipt, including
malformed/failed evidence. Partial preservation is explicitly failed; it never
authorizes deletion or another cycle. The final upload records artifact custody
before the adapter removes unchanged, exactly owned campaign evidence/binding/
lock and its own external artifact staging. Upload or cleanup failure retains the
last local copy and records failure. No global cache is deleted; missing hosted
dependency/distribution cache directories may be created and are recorded.

Retain the focused evidence artifact and `native-cleanup-<run>-<attempt>`, plus
API/run/artifact SHA-256 identities. Inspect all statuses and actual descriptors.
A strict L08 partial result deliberately fails the focused workflow; successful
cleanup does not disguise that result. Final all-five-job qualification still
requires a separate **full** dispatch at the same frozen source.

Linux controls live in `scripts/release/tests/test_native_continuation.py` and
join `productionReleaseAutomationCheck`; they prove adapter behavior only.

## 3. One non-app process diagnostic

`native-process-probe` is a separate, explicitly approved diagnostic. It neither
downloads A/B preflight evidence nor launches either application runner. After
source review, source/control commit and mechanical-inventory freeze, derive:

```bash
python3 -B scripts/ci/native_process_probe.py controls
```

An independent reviewer approves its complete `control_sha256`. Dispatch the
same continuation branch with only:

- `verification_scope=native-process-probe`;
- `frozen_source_sha=<exact frozen SHA>`;
- `approved_probe_control_sha256=<independently approved control hash>`.

The clean full-history source, workflow/run/attempt, actual qualified Apple
toolchain and complete imported control closure are bound. The original frozen
`snapshot_processes` command and 15-second timeout are checked without importing
or editing the canonical runner. Child environments are a closed public-tool
allowlist: no GitHub/Actions/tracking token, signing credential, DYLD/Python hook,
or arbitrary Gradle/Java option is inherited.

Platform qualification has its own named stage. Only the exact
`/usr/bin/xcrun simctl list runtimes --json` and `... list devicetypes --json`
queries receive the shared qualifier's120s allowance; other adapter commands
retain their20s ceiling. The first runtime query in diagnostic `34232325670`
timed out at the probe's introduced20s cap before any process observation; that
failed receipt is retained and does not establish an OS cold-start cause.
The corrected enumeration budget is operation-specific, not a `ps` timeout
increase or retry. Within the unchanged300s observation budget, a query whose
complete allowance plus direct-child retirement reserve cannot fit is skipped
and qualification fails, never shortened or accepted from partial output.
Bounded Git reconciliation during cleanup retains its existing adaptive budget.

The bounded sequence records:

1. Actual PATH-selected, root-owned `ps` resolved path, SHA256, file identity,
   signature verification/display and entitlements (not an assumed upstream
   build), OS build, CPU/memory and selected numeric `vm_stat` counters.
2. Original full `ps`, metadata `ps ...ucomm=`, and a timed libproc-only
   `PROC_ALL_PIDS`/136-byte BSD metadata observation before and after **one**
   freshly journaled, UUID-owned simulator boot. Bootstatus allows120s because
   run18's successful boot took92.34s; the ownership-observation limit stays15s.
3. The same observations alongside exactly one **nonbuilding**
   `xcodebuild -showBuildSettings` invocation using the frozen source project,
   exact scheme/Debug simulator configuration, package resolution/updates
   disabled and one owned DerivedData. Xcode stdout/stderr goes to DEVNULL.
   Direct-handle liveness before/after each query records whether overlap
   actually occurred; a fast settings exit is not retried or called overlap.
   This does not recreate/assert actool or Kotlin build workload.

Global argv and error text never reach evidence or logs: pipes are bounded while
read and only closed numeric summaries survive. A full `ps` timeout is latched
at15s. A separate at-most4s `sample` command may inspect only that exact still-
unreaped Popen PID; only bounded call-graph symbols survive, not headers, argv
or image paths. `-file /dev/stdout` prevents a default sample file elsewhere.
The timeout remains failed even if the process exits during sampling. Only the
owned direct handle may be terminated/reaped. No broad kill, lookup/adoption,
original-`ps` timeout increase, fallback, or production observer change is authorized.

Libproc result-size/field offsets and self PID/PPID/PGID/UID are actual metadata
observations, **not B's application-image ABI/provenance evidence**. Other-UID
denials, disappearance, short results and partial coverage are explicit; no
equivalence to a global `ps` table or production ownership replacement is claimed.

Observation has a300s budget. Insufficient time skips a new observation rather
than shortening/relabeling its15s deadline. Independent finalization stages have
a separate225s budget: immediate isolated `./gradlew --stop`, direct-child and
exact owned simulator retirement/absence, final isolated stop, holder-checked
owned resource cleanup, source/control reconciliation. There is no Gradle build
or test task. Only the public distribution cache is shared by an owned symlink;
the daemon registry is isolated and no global cache is deleted. Unexpected
holders, lsof warnings/invalid output or unattested resources are preserved and
reported failed, never adopted/killed by name. The10-minute job limit is a backup,
not the finalizer mechanism. The immediate stop permits65s because cold A17
downloaded the wrapper and stopped in54.705s; warm B18's immediate stop took5.113s.
The settings tail wait checks cancellation every at-most0.1s. Evidence-write
failures prohibit new observations, remain explicitly failed, and cannot prevent
later independent cleanup stages. Failed/partial receipt files are retained;
preservation failure never authorizes evidence-staging deletion.

The artifact `native-process-probe-<run>-<attempt>` retains the structured probe
and simulator journals, including failed observations. Native resources are
retired immediately, while evidence staging is deleted only after a successful
upload with artifact ID/digest and unchanged custody/manifest. The separate
`native-process-probe-cleanup-<run>-<attempt>` receipt records that deletion.
Upload failure preserves the last local evidence copy. Expected observation
failures/partial metadata deliberately produce a failed diagnostic step; they
never become an A/B runtime pass, readiness claim, or authorization to retry.

Synthetic controls are in `scripts/release/tests/test_native_process_probe.py`;
they execute no Apple tool, simulator or application.

## Diagnostic-only public Settings sheet

`native_selection=settings-sheet-only` is accepted only with `native-preflight`
or `native-evidence`. Its preflight contains exactly `preflight.json`, the source
binding, and `settings_sheet-controls.json`; it does not replace the unchanged
paired preflight domain. Approve the combined diagnostic control hash with the
existing `approved_probe_control_sha256` input. The process-probe and Settings
control domains are different; a hash or package from the other mode is rejected.
Both dispatches must use the same freshly reviewed frozen source.

Cycle `ios-readiness-36` builds only a disposable, detached UI-test target using
the existing Debug test identity. No Kotlin/Parlor target, normal B launch,
external image probe or Store operation runs. After exact public Settings flow
on the fresh owned simulator, one XCTest records exactly three action labels
(up to128UTF-8 bytes each) from the unique public language-choice sheet. It never
selects a choice or retries discovery; unfamiliar context/counts fail closed.
No hierarchy, screenshot, app container or private state is retained.

The diagnostic Xcode command is capped at600seconds (one180/240-second XCTest),
the child at900seconds, and the iOS evidence job at40minutes. Existing600-second
finalizer grace,600-second custody reserve and kernel-clock admission stay in
force. The original normal runner/control files remain unchanged; a private
module binds inherited checks to the aggregate diagnostic manifest. Actual
Swift build attempts retain the original worker/destructive-cleanup barrier.
Missing/failed XCTest results are recorded, not manufactured; retention alone
can authorize cleanup but never label capture or application qualification.

Success is `SETTINGS_SHEET_CAPTURED_NOT_APP_QUALIFICATION`, not `PASS` for A/B.
Both original lanes remain `NOT_RUN_THIS_RUN`; normal runtime/provenance/notice
statuses stay `NOT_RUN`. Observe and independently review actual labels before
considering any fixture selector correction or another L08 execution.
