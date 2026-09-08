# Focused native continuation (evidence only)

The existing `production-verification.yml` still defaults to its **five-job full
qualification** on dispatch, push and pull request. The two explicit focused
dispatch modes run only the existing qualified Apple job; they are not substitutes
for final five-job qualification. Unknown dispatch scope fails validation.

The coordinator dispatches only after reviewed source/control commits, followed
by the mechanical-inventory-only commit and its freshness check. Both focused
runs must use that same frozen continuation-branch SHA. Never edit controls during
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

1. A: composed L08, cycle `ios-readiness-17`;
2. B: normal-source eight launches plus separate image observation, cycle
   `ios-readiness-18`, explicit `--image-observer=libproc`.

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
