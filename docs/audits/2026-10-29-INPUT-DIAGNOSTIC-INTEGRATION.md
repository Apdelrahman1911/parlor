# Copy-only input diagnostics: scoped main integration

## Purpose and source boundaries

Existing [#284](https://github.com/Apdelrahman1911/parlor/issues/284) still requires
protected integration of its dormant PIN03 correction. PR #288 repairs the
separate existing readiness consumer; it does not import this diagnostic harness.
This candidate brings the reviewed diagnostic-only files from published
`91eec0eceff4cef067e472b6d2523aaa753af47b` onto current main5958f928 without
importing that older branch's shipping code or reverting accepted output-index,
recovery-clock, native-observer or readiness-reader fixes.

Original app/profile sources, selectors, single gestures, deadlines, privacy
filters, exact native-result parsing and owned cleanup remain. Nothing installs
observers in shipping Kotlin/Swift source. The production workflow, architectures,
Release optimizations, dependencies, strict verification and Store gates do not change.

## Confirmed additional reader gaps

Execute the original published `run_pin03.verify_inputs` on isolated frozen
fixtures. It accepts four symlinks at exact declared outputs (`composeApp/build`,
`.gradle`, `.kotlin` and `iosApp/iosApp.xcodeproj/xcuserdata`) because pruning occurs
before the symlink check. It also ignores two unregistered files outside outputs,
`composeApp/src/Unexpected.pyc` and `iosApp/Unexpected.xcuserstate`, by suffix.
Original reader SHA256
`1bfc2cf2ecd9a4d9a7af8eb8cf693ccaeb40a1aaf8bfcc5a73d758a19477ac4d`.

Impact: P2 nonshipping copied-input integrity. These are actual function executions
on six software fixtures, not new actual-compiler/native reproductions, a shipping
exploit or proof of #267/#276 input causality. Earlier hidden-Kotlin actual-compiler
evidence stays preserved under #284; no duplicate issue is needed.

Reject directory symlinks before pruning and remove blanket suffix exemptions.
Exact regular Gradle/Xcode outputs, intake/hash checks and fail-closed discovery
remain. Three new methods retain all six RED subcases and legitimate outputs.

| Actual-function control | Assertion failures | Errors/skips |
|---|---:|---:|
| Original reader | 6 | 0/0 |
| Prune-before-symlink mutation | 4 | 0/0 |
| Restored suffix exemptions | 2 | 0/0 |
| Corrected | 0 | 0/0 |

Sensitivity controls alter only an in-memory function; recorded source and test
files remain unchanged throughout. They establish guard sensitivity, not Apple
application execution or observation neutrality.

## Diagnostic registration and complete checks

The read-only standard `macos-15` workflow retains its published source/runtime/
device profiles. One explicit integration-branch trigger enables fresh native
qualification without changing a frozen historical branch or dispatching duplicates.
Exact reviewed workflow SHA256:
`c5b316d9ad127e36ef430dacd4c93e5811d52ed6529087f687b8ef34b69c97cc`.
Twenty source mutations reject privilege, paid-runner, trigger, cancellation,
cache-write, retry, source/runtime/device substitution, test-removal and upload
drift. This is supplemental investigation, never a replacement check or release.

Ordinary readiness includes every diagnostic control, including thirty artifact/
worker methods previously confined to standalone discovery. All **77** standalone
diagnostic methods pass locally (three explicit Apple-only skips), including three
new reader methods. A separate inventory regression rejects misclassifying this
workflow as production qualification; corrected mechanical inventory remains
expressly **not independently attested source review**.

## Required qualification and remaining limits

Fresh complete committed-source Linux gates, all439 ordinary readiness methods,
actual Apple/native architecture/runtime execution, source/product/result/artifact
custody, protected merge and distinct automatic actual-main acceptance remain.
An old diagnostic pass does not qualify this candidate. Publication is held while
already running actual-main verification completes; no extra agents or paid
infrastructure are used. Local cache reuse is not a controlled speed benchmark.

Original iOS26.2 diagnostic40889770560 has six passes, but none triggered failed-
only captures/exports. Recorder callbacks/export pipeline, full60-journey neutrality
and original input causality remain unproven. #267/#276, physical six-game lifecycle/
accessibility/energy, #258 Strict Complete Protection and Store ownership/signing/
legal/security approvals remain separate gates.
