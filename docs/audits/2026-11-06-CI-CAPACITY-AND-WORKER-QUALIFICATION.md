# CI capacity and worker-custody qualification — November 6, 2026

## Scope and accepted baseline

This follow-up covers #308 and #285, not physical-device, iOS input-delivery,
Strict Complete Protection, or Store acceptance. Baseline `c3c0e2b3` passed
independent main [42120235829](https://github.com/Apdelrahman1911/parlor/actions/runs/42120235829).
Only standard free GitHub runners are used. Required checks, architectures,
Release optimizations, strict dependency verification and privacy remain intact.

## #285: completed native worker acceptance

The production exact-path SwiftBuild admission was protected-merged in PR322
and independently verified on main42011471187. Its three hygiene/identity files
are unchanged at the accepted baseline. The missing Intel fixture qualification
was distinct: failed41985183754 started its ten-second holding-acquisition clock
at cold `xcodebuild` launch. The exact service was born13.207428s later, during
cleanup, before a holding marker had been observed. Both finalizers passed, but
that did not pass the holding control.

Correction [47d29ee6](https://github.com/Apdelrahman1911/parlor/commit/47d29ee6baad487eaaf8578e777734d08096d412)
starts the unchanged10s acquisition at the existing PBX holding marker. Cold
startup has a separate30s bound; the fixture still holds for30s, has a10-minute
workflow bound, and changes no application/finalizer timeout. Seven portable
controls cover cold startup, missing readiness, late/slow acquisition, ended
client, oversized output, exact worker identity and receipt-preserving cleanup.
This does not retroactively pass the old startup measurement.

Actual free Intel [42141604588](https://github.com/Apdelrahman1911/parlor/actions/runs/42141604588)
passed: startup22.129451s; exact service acquisition0.013470s; native owned signal,
client exit-15, independent service absence, final quiescence, and synthetic
fixture/log retirement. Xcode26.3/17C529's119,520-byte component SHA256 remains
`044787e793e5d2db4dff1cc99eed8e98a2d36e10dc6a1d3abee9aebd95f5f7f9`.
Artifact13468302735 ZIP SHA256:
`9ece5b8f8998da8f8a3b805506c8541cf3b2c5e81f9a02b28dfa2f0b74bfe17a`.
Independent admission receipt:
`c9ec0c2a87f842951558dcfddad9223279ac7c0523edb19517ab99b48f3c4062`.
All three production helpers match accepted main byte-for-byte. No app build,
Simulator, repeated successful ARM campaign, or production qualification is
claimed from this isolated control. #285 is closed for its satisfied scope.

## #308: reduce the actual overlapping disk demand

The original smaller-host failure41824962427 had a76,887,154,688-byte filesystem.
After earlier cleanup it had23,934,611,456 bytes free. API35 Release passed, but
API34 setup reported only6,239.27MB available versus7,372.80MB needed for userdata.
This is not the #312 GPU incident. PR316's fourteen-root cleanup and26GiB minimum
reserve passed full PR/main gates on larger154,894,188,544-byte filesystems;
those passes were not proof of the smaller-host capacity boundary.

The runtime wrapper previously retired processes but retained both AVDs' writable
images at once. Reviewed pinned AGP8.13.2 delegates its managed-device directory
to Android common31.13.2's `getGradleAvdLocation()`, which resolves below
`getAvdLocation()` and honors `ANDROID_AVD_HOME`. A global `cleanManagedDevices`
operation is unnecessary and would have broader ownership than this task.

The Linux supervisor now optionally allocates a fresh0700 temporary AVD home
for exactly one invocation. Both existing Release/API35 and Debug/API34 commands
opt in. After a successful runtime and kernel `ECHILD` proof that every direct
and adopted descendant has exited, it checks directory identity, mount/entry
bounds and link-safe deletion, then retires only its own directory. The next
lane cannot start if cleanup fails. Failed/interrupted builds retain their AVD
for investigation. Reports, APKs, mappings, unsigned AAB, SDK images, preferences,
keys, dependency caches, other users' AVDs and all custody are untouched.

The receipt records allocated blocks, real free bytes before/after deletion and
filesystem total. These are boundary measurements, not peak-memory/disk or speed
benchmarks. The existing26GiB pre-restore minimum remains fail-closed.

## Verification and limitations

Portable tests cover wrong directory identity/permissions, symlinks, mounts,
entry bounds, failed deletion, failed/unretired runtime preservation, unchanged
parent environment, both exact GPU/strict/signing commands and no failed-lane
replay. The live Linux descendant fixture also checks the allocated home and
success-versus-failure retention. This container lacks the required procfs child
file; that established local skip must execute successfully on hosted Linux.

Full [PR42142636265](https://github.com/Apdelrahman1911/parlor/actions/runs/42142636265)
passed all eight producers and the optimized Apple Release join. Distinct
constrained-free-space [42143063097](https://github.com/Apdelrahman1911/parlor/actions/runs/42143063097)
held a real, non-sparse79,682,158,592-byte reservation through both runtime lanes
and upload, with27,934,056,448 bytes (about26.015GiB) free before restore.
Both Android lanes passed3/3 and retired their exclusively owned AVDs. This is
actual constrained-free-space acceptance, not receipt of the historical smaller
total filesystem, peak-use measurement or a speed benchmark. Do not replay the
successful experiment to obtain another equivalent observation.

[PR327](https://github.com/Apdelrahman1911/parlor/pull/327) normally
protected-merged as `705c934babd6b746e4850114b2c3fe99f118b361`. Distinct automatic
[main42150601522](https://github.com/Apdelrahman1911/parlor/actions/runs/42150601522)
also finished successfully. Independent admission binds all eight producers,
the optimized Apple join, exact native method rosters, artifacts and owned
cleanup to tree `e9a6566ccc3f7092ffaf46fa2f8eef41fdf64ca8`.
Final main receipt SHA256:
`12c9f1adc9ffeb65ee84c81de871584e8a6b8aabaa72de3ed4aeae9c6fc40541`.
#308 closed only after this independent main acceptance. Later PR328 native/UX
changes require their own qualification; this result does not certify them.

No issue is closed merely because a workflow starts or compilation succeeds.
