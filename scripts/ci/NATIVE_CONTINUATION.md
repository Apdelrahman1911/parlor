# Focused native continuation (evidence only)

The existing `production-verification.yml` still defaults to its **five-job full
qualification** on dispatch, push and pull request. The three explicit focused
dispatch modes run only the existing qualified Apple job; they are not substitutes
for final five-job qualification. Unknown dispatch scope fails validation.

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

The adapter uses only read-only Actions API requests. It verifies the exact
producer repository, branch, source, workflow, attempt, successful result,
artifact identity/digest and four bounded nonsymlink ZIP members before writing
anything from the archive. It installs the binding byte-for-byte at the same
absolute checkout path and recomputes both control manifests before native work.

The canonical runners execute serially on independently owned fresh simulators:

1. A: composed L08, next cycle `ios-readiness-19`;
2. B: normal-source eight launches plus separate image observation, cycle
   `ios-readiness-20`, explicit `--image-observer=libproc`.

The failed `ios-readiness-17/18` evidence from run `34216788568` remains
historical evidence, not an overwritten/reclassified attempt. New labels do not
authorize another application run; the independent process diagnostic below
precedes any proposed retry.

Both use explicit `--simulator-signing=adhoc` and
`--toolchain=qualified-xcode-26.3`. No private signing or Store operation occurs.
A's strict protection failure/partial result does not suppress B, **provided A's
actual source/control/worker/device/Gradle cleanup receipts are safe**. Failed,
missing or contradictory cleanup stops the chain. No successful harness result
is upgraded to application runtime or file-protection enforcement proof.

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
timeout increase, fallback, or production observer change is authorized.

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
