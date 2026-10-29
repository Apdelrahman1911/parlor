# Readiness copied-source inventory: reject hidden inputs

## Confirmed defect and scope

Existing [#284](https://github.com/Apdelrahman1911/parlor/issues/284) covers this
false negative; no duplicate issue is needed. Its previously corrected PIN03
diagnostic reader is not present on main. This change repairs the separate,
existing `scripts/verification/ios-readiness/copied_sources.py` consumer.

At main `2f7594c9`, freeze the normal copied-input manifest (1,144 entries), then
add deliberately invalid `.UnregisteredReadinessInput.kt` under
`composeApp/src/commonMain/kotlin/com/parlor/app/`. The unchanged post-build
inspector incorrectly returns `unchanged=true`. Actual strict
`:composeApp:compileKotlinDesktop` identifies that exact hidden file and exits 1.
The isolated Gradle registry stops with code 0; only the owned injection is
removed. Tracked shipping inputs and the original validator remain unchanged.
Compiler log SHA256
`84b1a0fa1c4642e0ccd45e8e08a7e969579435a00f236f87799d7524364e4cbf`.

The cause is pruning hidden directories and every directory named `build`, then
applying intake restrictions during post-build discovery. Those restrictions
exclude hidden Kotlin that the actual compiler nevertheless consumes. Impact:
P2 verification integrity; a changed copied application could be incorrectly
reported source-bound. This is not an observed shipping exploit, native input
cause, secret disclosure or physical-device failure.

## Narrow correction

- Derive exact `build`, `.gradle` and `.kotlin` output paths from frozen recorded
  Gradle project/build files, rather than matching names anywhere in a tree.
- Reject directory symlinks **before** pruning even legitimate output paths.
- Traverse hidden source and fake nested output directories; inventory every
  candidate under the existing build-root/prefix scope, even if intake rejects it.
- Preserve intake restrictions, input hashes, missing-input checks and symlink
  rejection. Add no Python-bytecode or Xcode-userdata exceptions.

The existing positive instrumentation fixture now includes its actual
`composeApp/build.gradle.kts`; expected cardinality increases exactly one.
No source transformations, checks or deadlines are removed. No shipping code,
dependency, workflow architecture, Release optimization or platform policy changes.

## Executed controls and remaining gates

All **362 readiness methods** complete successfully (three explicit Apple-only
Linux skips), including three new methods covering hidden/nested sources,
declared outputs and symlink boundaries. Full software log SHA256
`a5de43f7d5886aede2171fcc4961f1a1525a433320a45319a48d3729231717d6`.
Independent isolated-source sensitivity checks retain:

| Reader | Assertion failures | Errors / skips |
| --- | ---: | ---: |
| Corrected | 0 | 0 / 0 |
| Original main | 7 | 0 / 0 |
| Restored intake filter | 5 | 0 / 0 |
| Basename-only output pruning | 3 | 0 / 0 |
| Output pruning before symlink check | 1 | 0 / 0 |

These are source-inventory software results, not Apple execution or application
qualification. Final committed-source full strict checks, the unchanged complete
hosted Apple control suite, protected integration and fresh actual-main checks
remain mandatory. Keep #284 open until its relevant integration requirements
are met; this narrow reader repair does not integrate the dormant PIN03 harness,
resolve #267/#276, certify observation neutrality or authorize a Store release.
