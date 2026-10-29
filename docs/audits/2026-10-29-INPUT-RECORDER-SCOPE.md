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
