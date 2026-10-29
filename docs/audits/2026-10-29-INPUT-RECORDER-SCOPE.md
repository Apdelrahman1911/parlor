# Public-input recorder scope ownership

## Confirmed non-shipping counterexample

The PIN01/PIN03 copied recorder can miss a real callback without losing the
application input. At base `d7fd8d2d9b2e2e617ee2fa1bd22ef167400bc242`, an actual
Desktop `AppNavDisplay` transition mounts public Setup before outgoing Home is
disposed. The old unconditional `publicStage(5)` disposal closes the shared
recorder after Setup's `publicStage(0)`. A single50ms touch executes the actual
Compose callback once, but the recorder reports zero callbacks and pointer rows.

The executed control uses production Nav3 rendering and copy-only stage helpers
with synthetic public widgets. It does **not** execute the entire application,
UIKit, or the original iOS failed event. It confirms a diagnostic lifecycle bug,
not a shipping input-loss cause. Track this investigation under existing
[#267](https://github.com/Apdelrahman1911/parlor/issues/267), not duplicate issues.

The first fixture compilation fails on an invalid top-level `assertExists`
import; no journey executes. Removing that import preserves the assertion as an
actual interaction member. The subsequent counterexample executes1case,
1failure/0errors/0skips after its real-callback equality assertion passes.
Log SHA256: `4474649c20ccb8680b30a876da9d4ed83ca6f266a36e318dfd0b9b7b738def15`.
Both owned Gradle stops succeed; source checkouts remain unchanged.

## Implemented correction and regression evidence

`PIN01Observation.kt.in` now owns at most8 mounted public scopes. Mount order,
not recomposition order, chooses the active scope. An older scope cannot close
or overwrite its successor; removing the active scope restores the last remaining
scope's public stage. Unknown or removed owners cannot revive capture. Overflow
increments the bounded invalid-evidence counter instead of throwing into input.
Owner references and scope bookkeeping are never serialized.

`PIN03NavigationScopeTest.kt.in` retains the actual Nav3 transition and single
touch. Callback observations use a per-test baseline, not assumed empty global
history. Four further recorder controls cover equal-stage successors, outgoing
recomposition, active-scope fallback/stale owners, and capacity overflow.

The exact corrected template and both fixtures execute10Desktop cases with
zero failures/errors/skips in an owned instrumented source copy. Private Gradle
stop0. Log SHA256:
`b84ad1958d56ae3c400ff25332ad7b4100de2e4cf2a71c610711edee6685cee8`.
The separate scoped Nav3 control also passes1case with no skips. These are
actual Kotlin/Compose controls, not Python simulations of their behavior.

To reproduce, materialize a frozen, caller-owned source copy using the PIN03
archive/plan controls, register both fixture files in its input manifest, then
run there—not in shipping source:

```sh
./gradlew :composeApp:desktopTest \
  --tests '*PIN01TemplateControlTest*' --tests '*PIN03NavigationScopeTest*' \
  --dependency-verification=strict
```

## Acceptance limits

Current hosted diagnostic40814363116 retains its original d7fd8d2d harness and
is not retroactively corrected. Missing observations there cannot establish
missing UIKit/Compose delivery. Original failed xcresults stay failed; the
existing decoders still refuse to certify causality or full native neutrality.
Fresh committed-source gates, native linkage/behavior of the corrected template,
protected integration, original iOS26.2 and physical-device acceptance remain
separate requirements. No shipping source, gesture, deadline, permission,
production workflow, architecture, Release optimization or Store release changes.

## Completed native investigation (05:12 UTC)

[Run40814363116](https://github.com/Apdelrahman1911/parlor/actions/runs/40814363116)
completes all three arms at original application841a0b06/harnessd7fd8d2d.
Independent frozen source/manifests and actual xcresult counts agree:
original1PASS/1FAIL, Compose2PASS, native2PASS, no skips. The original Arabic
failure is **pre-tap public control settlement**, not the earlier post-catalog
Host assertion. Its failed result remains failed; no shipping cause is proven.
No post-transition numeric capture is produced, because the observed arms pass.
Different outcomes do not establish observer neutrality or a repair.

All three owned cleanup receipts pass; each records delivered SIGTERM and
SIGKILL for the exact inventoried DTServiceHub path. Those are producer ownership
receipts, not independent post-run process re-attestation. ZIP SHA256:
`41d75ff88eeb1f3a1e39e1f66b45d1b028ff98bb8cff06f3e026ab23d4ab85dc`.

The independent review's elapsed-time dictionary mistakenly uses duplicate
`*-tests` labels: JSON result decoding overwrites actual XCTest duration.
Original command rows are intact: XCTest398.754/511.733/479.884seconds for
original/Compose/native, not0.088/0.141/0.080seconds. New decoder labels are
distinct, with a regression control; preserve the prior receipt and this erratum.

Before another application execution, a separate standard free macOS lane reads
only that exact retained original xcresult. Artifact/run/source/digest/path
controls reject substitution; Xcode decodes activities, details and already
recorded settlement samples. No build, app launch, event replay, Simulator,
permission change, cache write, signing or production-check substitution occurs.
The corrected recorder's native application linkage remains unverified.

### Retained-result decoder failure and narrowed location

[40824385175](https://github.com/Apdelrahman1911/parlor/actions/runs/40824385175)
passes all actual hosted controls, independently verifies/extracts the exact
original artifact, and decodes its Arabic test details and activities. It then
fails its real CLI capability check: Xcode26.3 has no `export logs` subcommand.
No application or Simulator executes; no input/result byte changes. This tooling
failure remains failed, not relabeled as acceptance. Supported `get log` action
and console views replace the nonexistent command, with an exact-command
regression and strict bounded numeric-only sample retention controls.

The retained activities establish the first Egyptian Dominoes Arabic journey:
catalog tap25.485s, Host30.826s, permission Continue36.309s, Back42.471s,
catalog47.871s, Join52.030s, then failed settlement67.376s before the second
permission Continue tap. These times are relative to test start, not networking
latency. The target's repeated native queries—not a completed Continue gesture—
precede the assertion. Geometry/timing samples are still required to establish
why the original criterion fails; this does not identify a shipping root cause
or explain the original Ghamza Host loss.

[40825194544](https://github.com/Apdelrahman1911/parlor/actions/runs/40825194544)
confirms the actual supported `get log` CLI and decodes its action view, then
fails numeric candidate validation before its independently declared console
read. The failure remains failed; exact930-file extraction manifests and failed
method/owned device match independently. No rejected raw candidate is retained,
so its particular field/content is not yet established. The previous progress
notification's queued state races this completion; the retained final is FAIL.

The reader now recognizes an emitted marker only at the start of a logical
line, rather than misclassifying source/context quoting it. A concrete quoted
source regression exercises that parser defect, **not a proven identification
of the particular rejected native candidate**. Numeric grammar remains strict.
Each declared log view is read independently even after a rejection; bounded
numeric field/category metadata explains rejections without retaining raw logs.
Any rejected view or missing samples still fails the complete-reader gate.

### Exact-source schema correction

[40826563143](https://github.com/Apdelrahman1911/parlor/actions/runs/40826563143)
remains failed: action output contains one rejected344-byte/two-sample
`emittedOutput` candidate; console exits1 with32stderr bytes. Rejected text and
raw stderr were discarded, so the precise native values/message are not known.
The exact930-file result manifest, failed method and iOS18.5 device independently
match the original artifact. All direct decoder children are reaped.

A subsequent source/manifests comparison confirms a reader defect: the original
arm's driver at841a0b06 emits **six sequential-query fields**, but the reader
requires the newer coherent-snapshot driver's seventh `snapshotErrorCode` field.
Original source, state and original-arm input manifest agree on driver SHA256
`c65fe65aee8442aa06f02007b57daaf3e9113f5f61046edb59e361c2e4a6e115`.
A six-field regression fails against the previous reader before the correction.
This establishes a schema mismatch, not the contents of the discarded candidate
or an application input-loss cause.

The reader now binds that exact frozen driver hash in Git and both artifact
manifests, uses its six-field schema only, and validates each of four CGRect
coordinates instead of accepting arbitrary numeric punctuation. New-driver
seven-field output is rejected rather than silently treated as old output.
Failed console reads retain bounded closed-vocabulary error tokens, byte count
and digest—not raw logs, arbitrary words or paths. Both declared views still
execute and any read error still fails complete decoding. No application retry,
production-test change, gesture/deadline adjustment or Store qualification.

### Actual numeric decode and optional console absence

[40828492426](https://github.com/Apdelrahman1911/parlor/actions/runs/40828492426)
atc08c1ac1 passes all actual hosted controls and the corrected action parser.
It still fails the console requirement, explicitly retained as failure. Exact
source/result/manifests and the failed method/device agree independently; artifact
SHA256 `9b1bd616de8bfeb06087dea6c8aa5d98c635f824e7d38a4c11de7670bf840999`.

Action output contains five instances of **one unique two-sample packet**, not
five independent failures. Both observed frames are
`(32,466.3333435058594,338,52.000030517578125)` and enabled. Sample1 at1.031727s
has query0.265524s and no adjacent stable frame yet. Sample2 at2.040363s has
query8.262880s, stable=true, but native hittable=false. This identifies the
failed predicate component, **not why native hittability fails**. Query duration
covers the whole sequential predicate, not solely `isHittable`; equal sampled
frames are not proof of continuous UI stability. No second Continue tap occurs.
This remains different from production's completed Word Impostor catalog event.

The console observation is now precise: reaped CLI exit1, empty stdout,
32stderr bytes, tokens `Error: No console log available`. Reconstructing that
exact message with its newline matches observed SHA256
`7edd4c9be5a9e23098ec98cc71122a9fbd83e1dead265129a4869665f21454c5`.
Installed Xcode help explicitly identifies action logs for Run/Test actions.
A separate console view is therefore unavailable in this frozen XCTest result,
not evidence that its authoritative action log failed to decode.

The reader preserves both reads and reports this exact optional absence
separately as `FROZEN_ACTION_DECODED_CONSOLE_UNAVAILABLE`. Only byte-exact absence,
exit1, empty stdout and reaped normal completion qualify for that classification.
Other errors, partial output, timeouts and missing/invalid action samples still
fail. Console-only output cannot substitute for action output. A successful
collection means existing evidence decoded, never a passing application journey,
shipping cause, originaliOS26.2/physical verification or production qualification.

### Verified decoder completion and next retained-screen investigation

[40830036426](https://github.com/Apdelrahman1911/parlor/actions/runs/40830036426)
at1c0396af finishes **successful collection**, independently bound to the exact
source, original930-file manifest, failed method/device, five instances/one
unique packet and byte-exact console absence. All direct children are reaped.
Artifact SHA256 `a28bd7529cdfc50a0a84c5134dcfccbc770c07cdd45c0457f3c963dc9f30d645`.
Fresh full strict local gates also pass1,867Desktop (3existing LAN skips),
671Android perDebug/Release,120GitHub-test, release/analysis/lint/R8/AAB/inventory;
source unchanged and stop0. This is not new application execution or a repair
of the original failed journey. PR#282 remains draft/unmerged at841a0b06.

The next explicitly bound read adds existing failed-attachment export, not an
application retry. Eligibility requires this exact public settlement failure
with no recorded typing. The archived driver/source and first failed Arabic
setup journey establish that no room/game operation or private player input
occurs. Installed CLI help must support the original `export attachments`
command and `--only-failures`. Only bounded, CRC-checked PNGs with fixed public
filenames and numeric geometry/digests are retained. Raw export manifests and
recordings are not uploaded; redirections, multi-link files, excessive counts/
sizes, invalid PNG chunks and existing destinations reject before publication.
Five synthetic filesystem controls and the actual archived public-failure
eligibility check execute locally; a corrupt-CRC negative control first fails,
then rejects after correction. These are not native screenshot export or visual
inspection. Actual hosted export and image review remain required before
concluding which screen is present after native hittability fails.
