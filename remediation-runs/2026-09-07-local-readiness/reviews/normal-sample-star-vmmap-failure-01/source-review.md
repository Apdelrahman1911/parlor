# Bounded normal-launch tool correction — source freeze

Author: `/root/native_fix_review`. Independent reviewer: **pending root**.
Status: source correction and static inventory prepared; **177 controls NOT EXECUTED by the author**.
No native/build/process inspection or cleanup occurred in this task; root owns the active lane.

## Original evidence and reachable regressions

`baseline.json` binds the five original files (compressed original bytes under `before/`),
all original 163 test signatures/file hashes, and unchanged native13 evidence.
Native13 passed eight XCTest repetitions, notices and cleanup but failed provenance.
Its preserved image diagnostic has three strict Binary Images rows, at table lines 1,
3 and 12. For installed artifact indexes 1, 2 and 0 respectively, every path component
except username matches the unique canonical artifact; username is literal `*`.
The UUID relations include those same artifact indexes. The header separately reports
`USER`. The old finite map contains only exact and USER keys; `parse_sample` resolves
the star row to no canonical artifact and rejects each shipping image name as unbound.
This proves the narrow parser mismatch, not independent mapped-image path/byte proof.

Native13 command metadata establishes an actual `/usr/bin/vmmap -w PID` exit255.
The old `Lane.command` returns nonzero after closing output; `require` raises, normal
or sibling observation fails, and raw-file finalization deletes the output without
capturing its cause. **That deleted cause is unknown.** The harmless-child vmmap probe
is capability evidence only, not app provenance. Old receipts and verdicts are unmodified.

## Correction and boundaries

- A second finite exact-string map adds only installed-app `/Users/*/...` forms for
  sample Binary Images. USER/exact header and vmmap handling, other origins, artifact
  inventories, duplicate/collision checks, UUIDs, addresses and lifetime guards remain.
  No glob/regex matching of wildcard input is introduced. Star presentation has a
  distinct lossy label and does not establish byte or kernel-path equivalence.
- A completed nonzero exact `vmmap -w PID` command preserves closed failure metadata
  at the existing command boundary before raw cleanup. Both existing invocation paths
  use it; neither gains an extra command, retry, relaxed predicate or provenance PASS.
- The source reader accepts only its exact owned regular single-link temporary file,
  no-follow opens it, verifies inode/size, bounds the read and verifies read length,
  size and mtime. It closes the descriptor in `finally`.
- Diagnostic tokens are limited to a fixed vocabulary and REQUESTED_PID; unknown
  bytes/paths/numbers are hashed. Input 16MiB; first8lines, first512bytes/line,
  first32tokens/line; serialized output64KiB. Overall/line hashes and lengths record
  truncation. It never infers a cause. Collector failure preserves original failure.
- Existing timeout/interruption/output-budget paths bypass completed-failure capture.
  Raw cleanup, signal/lifetime ownership, phase binding, app inputs, build helpers,
  Foundation composition freeze and all original test files remain unchanged.

## New controls and static review

`test_sample_literal_star.py` declares seven controls for the observed shape, no-policy
rejection, exact/USER vmmap, star-header/vmmap refusal, partial/foreign/other-component
refusal, copied/DerivedData origin separation, collisions/mutated inventory, malformed
rows, UUID/address/count/container guards and failure-only diagnostic classification.

`test_vmmap_command_failure.py` declares seven controls for bounded sanitized tokens,
actual mocked Lane.command completion on both invocation paths, primary-error identity,
collector errors/interruption, command cancellation/timeout, success/non-vmmap exclusion,
and exact/symlink/hardlink/descriptor cleanup. Popen is inert and native helpers are
mocked. They are unit controls, not physical/runtime network or iOS evidence.

Static AST parsing with Python3.9 grammar succeeded. Original163 test file bytes and
method IDs match baseline; new14 produce177 unique declared IDs. Runtime discovery,
assertion execution and root's independent review remain required. No new suite PASS
or native provenance PASS is claimed. `run_control_tests.py` requires exact discovery,
execution count, unchanged imported controls, no errors/failures and zero skips.

Complete modified/new small files and complete diff were reopened; runner import/context,
command/caller/failure/finalizer regions were reopened. Unchanged runner bodies were
compared structurally, not represented as fresh whole-file audit coverage. Baseline
native13 diagnostic indexes were independently traced to canonical artifact components.

## Required root verification

After the active lane finishes and independent review approves the frozen diff:

```sh
/usr/bin/python3 -B remediation-runs/2026-09-07-local-readiness/native/normal-ios-launch-proposal-01/run_control_tests.py
```

Use root's shared finalization/receipt wrapper. If green, a fresh normal-launch source
binding and independent combined-control approval are still required before native
execution. No application source instrumentation, entitlement change, root attach,
private material, original evidence rewrite or Store action is authorized here.

## Coordination and cleanup

Only seven normal-runner control/doc/test files were modified/added; audit evidence is
additive in this folder. Root reported native14's broad source fingerprint can observe
these tooling-only edits. Preserve that raw failure if emitted; this task does not
rewrite, waive or reclassify native14. Root must separately reconcile unchanged actual
copy/control evidence. All this agent's commands were short-lived static readers,
AST/hash computations or scoped file writes with `-B`; no Gradle/Xcode/native processes,
simulators, test allocations, bytecode or generated build outputs were created. No
unrelated process, cache, application source, prior audit material or file was removed.
