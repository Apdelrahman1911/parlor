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
