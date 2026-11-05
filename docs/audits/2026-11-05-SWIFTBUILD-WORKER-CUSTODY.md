# SwiftBuild cancellation-survivor custody

Related: [#285](https://github.com/Apdelrahman1911/parlor/issues/285).
This is not the original iOS input cause or a device/Store qualification.

## Confirmed boundary

Obsolete PR321 run [41978547098](https://github.com/Apdelrahman1911/parlor/actions/runs/41978547098),
executed `3d356250` / tree `9ae3e061`, is terminal **CANCELLED**, never qualified.
Its Release-wrapper cancellation leaves a new exact task-tracked SwiftBuild
service that the closed Xcode executable set does not recognize. Cleanup
correctly withholds that helper's signal and retains build/device evidence.
The older XCBBuildService and verified DTServiceHub entries do not match it.

The failed stop receipt and both artifacts remain preserved: Release13392993925
ZIP SHA256 `d6987fa575424f61af40177da761af4a63e83e9ba90a9c043361afe5cc8922ab`;
cleanup13394035049 ZIP SHA256
`99a42ec00c01529acf35e6133021b5dc2759050e4c3195ee306e7db0384f5ae2`.

## Native component review and correction

Read-only [41981135725](https://github.com/Apdelrahman1911/parlor/actions/runs/41981135725),
exact isolated source `ae185288`, verifies the component on standard free
`macos-15` ARM64 and `macos-15-intel` x64, Xcode26.3/17C529:

```text
Contents/SharedFrameworks/SwiftBuild.framework/Versions/A/PlugIns/
SWBBuildService.bundle/Contents/MacOS/SWBBuildService
```

Both hosts report identical 119,520-byte images, SHA256
`044787e793e5d2db4dff1cc99eed8e98a2d36e10dc6a1d3abee9aebd95f5f7f9`;
strict signature verification/display both exit0. Identifier
`com.apple.dt.SWBBuildService`, TeamIdentifier `59GAB85EFG`, Apple Software
Signing → Apple Code Signing Certification Authority → Apple Root CA.
Five new inventory controls pass on each host, no skips; no app build,
Simulator, input, service argv or signal. Independent inventory receipt SHA256
`1c93bf36ced6b79c586fded6bc0fd58c53bfd4f3445618786c9c9881ddcd5aeb`.
This proves the component identity, **not cancellation cleanup**.

Extend only `scripts/ci/apple_verification_hygiene.py`'s exact executable set to
that reviewed path. Configuration still requires the canonical pinned Xcode,
regular executable files and original runner marker. Signal admission still
requires a new lifetime, exact UID, unchanged executable/generation/marker,
native audit token and bounded quiescence. No wildcard, PID/name kill,
unknown-worker exclusion, retry, timeout extension or evidence waiver.

## Regression and remaining acceptance

Three new methods retain exact-path/lookalike rejection, UID/marker/origin
boundaries, and grant revocation after generation/marker/executable changes.
Before correction, three methods produce four assertion failures (subtests);
negative ownership controls already pass. After correction, all73 affected
synthetic ownership methods pass, zero failures/skips. Existing methods remain.

```sh
python3 -B -m unittest scripts.release.tests.test_apple_ci_hygiene -v
```

The one-shot isolated [41985183754](https://github.com/Apdelrahman1911/parlor/actions/runs/41985183754),
source `534e1b49`, remains terminal **FAILURE**: ARM holding-phase control passes;
Intel fails its unchanged 10s acquisition boundary. The Intel service's native
birth is 13.207428s after its client, outside that gate; its deeper startup
cause is not proven. Three new portable controls pass on each host.

Both actual first finalizers still observe and deliver native signals to the
reviewed exact task-owned service, reap nonzero clients, and pass a separate
final worker-quiescence check. ARM additionally attests the holding phase and
explicit target reread absence; Intel does not. This is evidence of the worker
correction, **not a passing complete cancellation workflow** or verified raw
fixture-data retirement. No retry, larger gate, application build, Simulator,
input or private build-log upload follows. Partial readback receipt SHA256
`d0037ed37cee64f8a465e01886877d0ba67e4af1b26177908bfe82556d774bc6`.

Final-source protected PR checks and independent resulting-main verification
remain mandatory and **pending**. The isolated inventory/fixture must not be merged or represented
as shipping qualification. Unknown, preexisting, foreign and surviving workers
must continue to fail or remain excluded according to their existing policy.
Original #267/#276 native boundaries and #258 Complete Protection stay separate.
