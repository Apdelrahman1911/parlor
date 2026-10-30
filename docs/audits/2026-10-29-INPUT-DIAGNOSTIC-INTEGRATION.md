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

## Fresh committed-source local execution

At clean `b07730859440357f88b79334b92c5f57cca9595e`, strict `productionCheck allTests`
passes 1,889 Desktop methods (three existing LAN skips), 681 Android per Debug/
Release, 120 GitHub-test and 768 release controls (one platform skip), with Detekt,
lint/R8/unsigned AAB and inventory/shell gates. The 295 unchanged affected Desktop
methods in 15 files match actual XML. All 439 source-derived ordinary readiness
methods pass with six explicit Apple-only Linux skips. Source unchanged/owned stop0.
Full log SHA256 `67143cb85bfda70fb34f484db267ca728566fdae7c51a43c8f6245477a64a92b`;
readiness log `73be1f0e3b70c4d9f1ceb3d7c95fa36ae1440b0152b339224013ae996339c2ca`.

A separately owned filtered Git-source copy executes the actual recorder and
Compose/Nav3 templates. Restoring close-on-any-scope-disposal produces one expected
failure: the actual setup callback runs once but its numeric recorder row is lost.
The corrected template executes all ten methods successfully; all six unchanged
public-pointer methods/60 journeys pass both before and after copied observation.
Independent review matches source manifests, methods, XML, numeric rows, logs and
owned stop0; shipping source stays untouched. Receipt SHA256
`8e48d77edf83431aa0cd8db16c578d7de680a24f3d836e5abcfd09e360b2bfed`.
This is current-source Desktop execution, not UIKit arbitration/timing neutrality,
original lost-input causality, physical-device or energy verification.

## Shallow-checkout integration regression

Fresh [PR40930610448](https://github.com/Apdelrahman1911/parlor/actions/runs/40930610448)
at `2a7d22a0` fails its iOS readiness step **before KMP/Swift runtime execution**.
Two imported controls call `git show` for `9d4f369d`/`841a0b06`, absent from that
lane's shallow checkout. The diagnostic workflow has full history and its controls
pass; local full-history success did not cover this integration boundary. The
original failed job/log remains retained, never rerun or relabeled successful.

Make these software controls hermetic, without widening production checkout or
fetching 1.3 GiB historical source trees. Eight full original source-file fixtures
(107,395 bytes) match existing renderer/decoder SHA256 pins and fixed Git blobs/
commits/trees. Their manifest is independently hash-bound. Reject unknown bindings,
changed/missing/oversized bytes, symbolic/hard links and changing descriptors; no
Git/network/newer-source fallback. Native investigation **still archives the actual
explicit original Git commit**; fixtures never become app/build inputs.

An actual owned-source execution without either historical Git object preserves
RED (15 methods run/two errors, five setup methods unavailable), then executes all
20 original affected methods GREEN and seven fixture adversaries: 27 passes,
zero errors/skips, objects still absent and no fetch. Removing either actual-loader
hash guard produces one assertion failure; corrected seven pass, no errors/skips.
Complete standalone controls now total 84; ordinary readiness totals 446. Fresh
final-source Linux and hosted qualification remains necessary. The first native
run retains its exact clean sparse `2a7d22a0` source for independent readback while
the separate coding worktree is corrected; no old run/source/receipt is rewritten.

## Native software-control temporary scope

At corrected `ccfce05e`, [production40936865715](https://github.com/Apdelrahman1911/parlor/actions/runs/40936865715)
executes all **446** ordinary readiness and 36 protection-application controls on
macOS without errors/skips. The shallow-history repair is exercised successfully.
Before KMP/Swift runtime, the existing outer private-directory `rmdir` then fails
because that directory is nonempty. Original failed job/log is retained. Its exact
leftover filenames/creating API were not recorded; do not invent attribution.

Three newly imported Apple controls isolate source/module-cache files but their
SDK/compiler/Foundation IPC child processes inherit the **outer** `TMPDIR`.
The actual control bodies, executed with synthetic native scratch writers, leave
that outer parent nonempty. Two behavioral assertions and seven child-environment
routing subcases reject the old calls (three methods, nine assertion failures;
zero errors/skips). This is executed process-boundary evidence, not an actual
Linux UIKit compilation or proof of the unknown hosted file's origin.

Give every SDK query, compiler, listener and notification request the exact owned,
canonical **per-test** `TMPDIR`. Child lifetimes and existing listener retirement
remain; the temporary fixture now owns those children's transient files too.
All three new controls pass, including actual old-body RED/changed-body GREEN.
The existing outer empty-directory-only retirement, failures, timeouts, native
SDK compilation/IPC checks and workflow remain unchanged. No unknown output is
force-removed and no check is disabled. Complete ordinary controls now total449,
standalone diagnostic87. Fresh macOS source/runtime/cleanup execution must verify
whether this also resolves the actual hosted retention; local mocks cannot do so.

## Canonical owned fixture roots

Fresh [standalone40939999222](https://github.com/Apdelrahman1911/parlor/actions/runs/40939999222)
at `b0706863` fails three of its 87 software controls, **before application/build
execution**. Original manifest-corruption, source-corruption and hardlink assertions
encounter the loader's earlier `Redirected frozen control fixture` guard instead.
The exact hosted temporary-path alias component was not retained. Preserve the
failed job, absent application artifact and rejected independent native review.

The controls construct copied fixture roots from uncanonicalized `TemporaryDirectory`
names; a real owned temporary-directory alias reproduces exactly the same three
failures on unchanged source (seven methods, zero errors/skips). Original RED log
SHA256 `cf5b00881fb8321927bd070fb98599459023e65ed2cd49f3dbd8e9484beadabf`.
This identifies a fixture prerequisite defect, not a loader bypass or gameplay bug.

Canonicalize only each newly owned temporary root **before** making its test files.
Do not canonicalize candidate inputs or intentional adversarial links. Original
manifest/content hash, missing/oversized source, symlink, hardlink and descriptor
assertions remain unchanged; loader and frozen manifest bytes are unchanged.
The loader SHA256 remains
`6402b9fc49298d143d1893dd6b609079b96fbeeb9672432816cd9357ff310962`.
A new real-directory-alias regression executes the three original control bodies,
requires their owned child directories to retire and retains the intentional alias.

Corrected alias execution passes all eight methods, zero errors/skips; log SHA256
`137a01413746efddd9730edc06bfdf4b277983b2cc34b94154be6b7a8e4d2b0c`.
Standalone discovery now contains88 methods; ordinary readiness450. Fresh complete
committed-source and macOS checks are still required; this Linux reproduction does
not certify the hosted repair. Workflows, deadlines and strict path/hash guards are
unchanged. Production40940007224 remains separately observed at its frozen old head;
no failed native preflight is substituted with the older successful three-arm run.

## Required qualification and remaining limits

Fresh base-main40916055035 independently qualifies PR #288's actual merged
`5958f928`: all eight producers/original Apple Release join, 23 Swift/708 KMP
Simulator/362 readiness and custody; receipt SHA256 `eebb48d83bbcc1bbdf594b2770c2fa5fc1bcd6ceaf2ddb4c2616fd19c8d6344f`.
Final documentation-source gates, fresh candidate Apple/native architecture/runtime
execution, source/product/result/artifact custody, protected merge and distinct
candidate automatic actual-main acceptance remain at this preparation checkpoint.
Old diagnostics do not qualify a newer candidate. No extra agents or paid
infrastructure are used; local cache reuse is not a controlled speed benchmark.

Original iOS26.2 diagnostic40889770560 has six passes, but none triggered failed-
only captures/exports. Recorder callbacks/export pipeline, full 60-journey neutrality
and original input causality remain unproven. #267/#276, physical six-game lifecycle/
accessibility/energy, #258 Strict Complete Protection and Store ownership/signing/
legal/security approvals remain separate gates.


## Protected integration and distinct actual-main acceptance

[PR #289](https://github.com/Apdelrahman1911/parlor/pull/289) normally merged at
22:19 UTC as `c81f75b4f119a6a896cedf89fc13511c10f24cb2`, tree
`5229e18fb2f74a69086b9210addc7883a0ef45ce`. Fresh
[PR40953635018](https://github.com/Apdelrahman1911/parlor/actions/runs/40953635018)
and **distinct automatic
[actual-main40964941119](https://github.com/Apdelrahman1911/parlor/actions/runs/40964941119)**
each independently qualify all eight producers/original Apple Release join,
23 Swift / 708 source-derived KMP Simulator / 450 ordinary readiness methods,
zero macOS skips and exact source/artifact/native/output custody. Main qualification
SHA256 `cd2e6822b689c28177efb7b82b919fb3600ec2bd800ae72b70c53af8a9820327`.
Ruleset 20910226 remains active without bypass actors. Scoped #284 is now closed;
historical failed/cancelled runs retain their original outcomes.

Separate current-harness40953628450 independently matches the three frozen
original/Compose/native source plans and EN/AR outcomes: two passes per arm,
zero failures/skips, owned cleanup receipts and archive custody. ZIP SHA256
`f8ac28ea9a96a6e50d9a72b63b8adef0a32067dc1ac614aca6b8d9a969951537`.
Consistent producer cleanup is not independently live re-attested cleanup.
Actual runtime is iOS18.5/22F77/iPhone16Pro Simulator, Xcode26.3/17C529.

The prior production40940007224 remains 22 Swift PASS / 1 FAIL: English Dominoes
Join/name settlement, before tap/keyboard/typing/networking. The read-only original
xcresult decoder40955026246 matches all 1,247 objects / 23 Swift identifiers; its one
actually viewed public screenshot contains no keyboard/tutorial/alert/overlay.
This is not stale-lookup causality, main-thread liveness or event-delivery evidence.
#267/#276 original input cause, failed-only capture/export and full 60-journey
neutrality, #258 protection, physical-game/lifecycle/energy and Store gates remain.
No deadline increase, replayed input, omitted checks, paid infrastructure or release.
