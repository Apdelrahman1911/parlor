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
The related release-gate guide now describes all 11 existing job definitions
(eight producers, the Release join and two supplemental diagnostics). Its existing
regression test rejects the old ten-job claim, then passes the corrected prose;
the workflow and required checks are unchanged.

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

At independent committed source `20ca6c37c60a2c5d0871ef1f507ff16221351394`, full
strict `productionCheck allTests` additionally passes 1,847 Desktop cases (three
existing opt-in LAN skips), 659 Android per Debug/Release, 112 GitHub-test and
766 release controls (one platform skip), plus Detekt/lint/R8/unsigned AAB and
inventory/shell gates. Exact source unchanged; owned Gradle stop 0. Log SHA256
`63275e67ed956598753add77e5133fdb70092c479391ab09adabf163b2d9a69a`.
390.4 seconds with 904 executed/665 from cache/99 up-to-date tasks: valid reuse,
not a cold-build benchmark. A separate committed-source readiness run passes all
362 methods (three Apple-only Linux skips); every source-derived method and
terminal status is independently checked, including all three new methods.
Log SHA256 `e328934eb9ff3c57d801a5e01d630f7af6001c60ede217d032e756d9fdf8f5a2`.

Subsequent local preparation normally merges recovery `b439cefa` from PR #287.
At clean combined `0e8341ea0e41703cbe4c2ebc93a6af46993d9db7`, full strict local
checks pass **1,889 Desktop**, **681 Android per Debug/Release**, **120 GitHub-test**
and **766 release controls**, with only the same three LAN/one platform skips.
All 295 changed Desktop methods in 15 files match actual XML; source unchanged,
owned stop 0. Complete ordinary readiness passes all 362 source-derived methods
(three Apple-only Linux skips). Full log SHA256
`891aad5eed7efe317f918898e659d9e1bfa7f9fc529685fbe05de516cfe1cca4`;
readiness log `a97ae9c53045d13d748c0e875726ff78c2b5ddf5eef53972debf9ffabc616ea5`.
297.9 seconds of valid unaffected-input reuse is not a cold-build benchmark.

[PR #287](https://github.com/Apdelrahman1911/parlor/pull/287) subsequently merges
normally as `539f8073fde9e7f168c64890ff89a416ab5551f0` after fresh
[PR40877009596](https://github.com/Apdelrahman1911/parlor/actions/runs/40877009596)
qualifies all eight producers/the Apple join, 23 Swift and 708 source-derived KMP
Simulator methods. [Automatic main40886687896](https://github.com/Apdelrahman1911/parlor/actions/runs/40886687896)
is running at this checkpoint, **not yet post-merge qualified**. Preparatory local
alignment with that merged main changes no source-tree bytes. [Draft #288](https://github.com/Apdelrahman1911/parlor/pull/288)
remains stacked remotely; never merge it into the feature branch. Main qualification,
final documentation-source rechecks, retargeting to main, fresh complete hosted
controls/architectures, protected integration and distinct actual-main checks remain.

These are source-inventory software results, not Apple execution or application
qualification. Keep #284 open until its relevant integration requirements
are met; this narrow reader repair does not integrate the dormant PIN03 harness,
resolve #267/#276, certify observation neutrality or authorize a Store release.
