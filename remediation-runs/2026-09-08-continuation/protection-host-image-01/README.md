# Host image-predicate diagnostic

Authored controls, not execution evidence. This lane observes the original
sampler's image-identity predicates on **host macOS**, not an iOS app or
simulator. It does not change the sampler guard, read production snapshots,
query filesystem protection, or qualify L08, normal app provenance, readiness,
hardware protection, or Store operation.

## Invocation and bindings

Root alone reviews, tests, approves frozen controls, and dispatches. The existing
`ios-protection-probe` workflow job/scope accepts `native_selection:
protection-host-only` and exports `PARLOR_PROTECTION_SELECTION` with that exact
value. The old `paired` standalone route remains separate. Commands are closed:

```sh
python3 -B remediation-runs/2026-09-08-continuation/protection-host-image-01/run_host_probe.py controls
# Approved hosted workflow only: run, cleanup, assert-result
```

There is no remote native-preflight requirement for this standalone diagnostic.
The explicit `PARLOR_APPROVED_PROBE_CONTROL_SHA256` must match the reviewed local
manifest. It binds these six files, the original sampler and standalone helper,
helper dependencies, workflow/contracts, and Gradle wrapper used only for stops.
The actual clean tracked, full-history checkout, repository, branch, workflow
and run must match the frozen source. Source and controls are checked again
before compilation, after observation, and around cleanup.

Admission requires non-root, equal real/effective UID, Darwin arm64, macOS15,
selected Xcode **26.3/17C529**, host `macosx` SDK **26.2** from that Xcode, and
public JDK21 for mandatory stops. Actual macOS version/build and SDK path are
retained. No `simctl`, simulator identity, app install, KMP/Gradle build, or clean
task is used. Child environments exclude tokens and loader/Java/Gradle overrides.

The native record binds five strings: random 32-hex `run_token`, frozen Git40
`source_sha`, approved64 `control_sha256`, and decimal `run_id`/`run_attempt`.
The main target must pass the original guard and match the independently
captured built arm64 UUID and basename, actual direct child's PID/UID, and
observed host version. FileManager and NSURL method targets remain independent:
their original `REJECTED` results and failed predicates are retained, not
rewritten as success. The fixed NSURL only constructs a receiver; no resource
query or mutation is performed.

## Evidence, bounds, and cleanup

One owned copy is compiled for `arm64-apple-macosx15.0` (90s), ad-hoc signed and
verified, then run directly once (30s). The copied sampler receives only the
pinned pre-guard observation hook; the original guard remains byte-identical.
An exact bounded `host-copy.patch` for that injection and template expansion,
source/copy hashes, compiler/signature/UUID logs, raw `host-image.stdout.json`,
stderr, and validated summary are retained before scratch removal. Native JSON
is at most256KiB; the reused command collector has a2MiB combined stream cap,
96-command ceiling, exact Popen-handle ownership and bounded termination/reaping.
No binary or cache archive is needed.

The run budget is300s plus90s finalization. Cleanup has180s plus65s final stop
reserve. Every complete command timeout plus retirement must fit before launch;
timeouts are not shortened to make a run fit. Evidence is preserved before the
mandatory immediate `./gradlew --stop`; cleanup stops before retirement and
again at its end. These commands never configure/build/clean the project.

The existing workflow uploads these unchanged roots:

`$RUNNER_TEMP/parlor-protection-probe-$GITHUB_RUN_ID-$GITHUB_RUN_ATTEMPT/{evidence,cleanup}`

Only the exact request-bound `resources` sibling may be removed after confirmed
evidence upload, unchanged source/controls, settled direct children and bounded,
same-owner/device, non-symlink scratch inspection. Timeout, uncertain ownership,
preservation failure or failed upload retains scratch as cleanup FAIL. Original
evidence is never deleted or appended during cleanup. The second upload and
distinct immutable artifact IDs/digests are required by `assert-result`.
There is no simulator cleanup receipt, fabricated UDID, or `NOT_CREATED` claim.

`CAPTURED_HOST_IMAGE_PREDICATES_NOT_APP_QUALIFICATION` means collection only,
including honestly rejected method-image predicates. Cleanup PASS means only
owned-resource retirement. Neither result changes historical qualification.
