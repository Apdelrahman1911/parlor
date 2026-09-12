# Normal-source iOS launch observation — campaign-only draft

**Status: not executed and not independently approved.** No actual app, Gradle,
Xcode, simulator, `sample`, `vmmap`, signing, or test run was started by the author.
Only source inspection, public help/schema research, and static Python AST reading
occurred. All files live in this campaign; adopted controls and application source
are untouched. Root owns the single execution lane.

## What this runner does

1. Requires a later explicit `bind_source.py` schema-3 binding of the complete
   current tracked/untracked source identity, and an independently reviewed hash
   of the binding plus the executable controls. Refuses stale source, a reused
   evidence directory, concurrent Gradle/Xcode work, or original build outputs.
2. Reuses the current `scripts/verification/ios-readiness` source-copy, process,
   secondary-FIFO, simulator-signing, and artifact helpers **without calling the
   instrumented runner's `main()` or any instrumentation function**.
3. Creates one fresh synthetic iPhone17Pro/iOS26.5 simulator and one owned source
   copy/Gradle registry/DerivedData allocation. Exactly two copied inputs change:
   the UI-test file and the isolated stop/attested-embedding build phase. Every
   Kotlin, production Swift, resource, dependency, and game input must be unchanged.
   The exact two-file diff and complete before/after input hashes are retained.
4. Builds once and runs one selected XCTest method **eight fixed times**, with
   test-process relaunch enabled, no retry-on-failure and no parallel testing.
   The test adds the checked-in English `AppleLanguages`/`AppleLocale` arguments,
   calls public `XCUIApplication.launch()`, waits for foreground/Home, then records
   six observations over at least ten actual post-home seconds. Each observation
   requires foreground, `parlor-home-brand`, and absence of native alerts.
5. Requires both eight actual individually passing `Test Case Run` records in
   XCResult and eight complete, serial UUID marker trains. No skipped/failed/
   expected-failure run is allowed. Forty-eight samples are **not forty-eight
   tests**. Summary method counts alone cannot establish repetitions. Unknown
   XCResult repetition layouts fail closed for evidence-backed parser review.
6. Afterward, performs **one separate ninth launch** using public
   `simctl launch --terminate-running-process` on the same owned installed app.
   The exact returned PID must be new, same-UID, within this launch interval,
   and executable-path/kernel-lifetime/audit-token attested using the approved
   Darwin helper. After a bounded normal startup wait, public `sample` and
   `vmmap -w` must report the app's launcher, debug dylib, and exactly one Compose
   framework, matching current built/installed file hashes, UUIDs and text mapping
   addresses. Owned copied-build framework origins remain distinct from embedded
   origins; other images are not retained. No app code or DYLD probe is injected.

## Evidence limitations

- This is normal **application source**, not an assertion that Apple's XCTest
  machinery performs no instrumentation. The eight repetitions remain XCTest-
  controlled English launches on one fresh install; they do not test Follow
  System, game/session retention, Arabic gestures, full games, LAN or Store builds.
- Periodic public UI checks are not continuous liveness or per-boot PID/image
  observation. Apple documents that `launch()` terminates an already-running app;
  test UUIDs are not represented as kernel app-process identities.
- The ninth launch's external provenance cannot be borrowed as image proof for
  each earlier XCTest repetition. `sample` suspends the process while sampling;
  this separate launch is not passive startup-timing evidence.
- Reported `sample` image UUIDs are matched against artifact UUIDs, not independently
  read from mapped Mach-O headers. Numeric-PID tools cannot atomically reserve a
  generation; pre/post audit-token checks plus tool PID/path/address checks are
  stated precisely, not upgraded into that guarantee. No mapped-page hash claim.
- Built and installed full native inventories must match. This is not proof of
  release signatures, physical devices, launch-crash history, storage health,
  OS language-override ownership or an absence of other bugs.
- Xcode26.5/17F42, SDK/runtime26.5 are the currently researched local tools, **not**
  Store-qualified Xcode26.3/17C529. Read-only host observation on2026-09-07 found
  macOS26.2/25C56, Darwin25.2.0. An earlier helper's “Darwin26” prose must not be
  interpreted as the actual kernel release; its approved implementation is reused.
- Public-tool permissions and actual result formats are not yet exercised. A
  denial/unknown format remains a failed or blocked evidence gate, not an app
  defect and not authorization to change entitlements, attach by name, or use sudo.

## Cleanup and resource ownership

The copied shell phase stops its isolated Gradle registry immediately after
embedding; the Python lane also stops it immediately after `xcodebuild`, including
failed/timed-out/interrupted commands, and in finalization. Build artifacts remain
only until XCResult/native-file/provenance inspection ends; that dependency is
recorded. Root source/build directories are never deleted.

Finalization continues across failures: exact owned-device shutdown/deletion,
observed task-worker shutdown, reviewed secondary-FIFO attestation/cleanup,
unknown-holder checks, post-build input comparison, then exact allocation/inode-
checked removal of copied module/build-logic outputs, isolated Gradle registry,
DerivedData, XCResult, app stdout/stderr and temporary files. Shared global-cache
symlinks are checked and unlinked; global caches are never removed. Unknown file
holders/changed ownership preserve the directory and fail cleanup rather than
authorizing a broad kill/delete. Temporary full stack/mapping/device-list data are
deleted rather than copied into reports. Xcode logs are bounded and compressed;
only structured results, the two-file diff and compact metadata are retained.

## Root review and execution

Do not execute while another lane is active. Independent review must examine all
new source and the referenced approved helper hashes, then run the **67** synthetic
control tests (not Swift compilation or app runtime):

```sh
/usr/bin/python3 -B remediation-runs/2026-09-07-local-readiness/native/normal-ios-launch-proposal-01/run_control_tests.py
```

Use the root's outer receipt/finalization wrapper even for this pure cycle. The
driver records exact imported-module paths, before/after control hashes, static
test IDs, actual discovered IDs, actual execution count, failures and skips.

After the source freeze, use the adopted `bind_source.py` to create a **new** direct
campaign JSON binding. Never silently refresh an older binding. Inspect:

```sh
/usr/bin/python3 -B .../run_normal_ios_launch.py --control-manifest /absolute/campaign/new-source-binding.json
```

Then independently approve that exact printed combined hash. Pick an unused
`ios-readiness-NN` evidence name; this naming is required by the already-reviewed
owned-phase helper, while `execution_kind` explicitly distinguishes this run.
The root-reviewed execution has this shape:

```sh
/usr/bin/python3 -B .../run_normal_ios_launch.py ios-readiness-NN /absolute/campaign/new-source-binding.json REVIEWED_COMBINED_SHA256 --simulator-signing=adhoc
```

Omit the final option for the existing disabled-signing baseline. Ad-hoc mode is
explicit, local, credential-free and must satisfy the actual app-phase signing
preflight; this is not Store signing or publication authorization. No app-source
freeze or native success is implied by this draft's control freeze.

Research receipts/gzipped public sources are adjacent. Apple current launch docs,
the installed Xcode repetition manpage and xcresult0.1.0 schemas, installed
`sample(1)`/`vmmap(1)`, Apple vmmap documentation, installed LLDB help, and current
LLVM command mapping were considered. LLDB was researched but is **not** used as
an unreviewed automatic fallback in this bounded runner.
