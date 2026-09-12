# Uninstrumented Swift-wrapper smoke — review draft

Author: `/root/factory_review`. This is task-owned verification infrastructure,
not application code. **Not executed; independent control review is required.**
The root agent owns the sole Gradle/Xcode lane and must not start this while
another cycle or its cleanup remains active.

## Purpose and exact scope

Exercise the current Swift/Compose application wrapper without the observer or
synthetic invocation changes used by `dsc01_apphost_v6`. Run exactly these five
existing tests from the current `iosAppUITests` target:

- `IOSAppLaunchUITests/testColdLaunchRendersComposeHomeWithoutUnexpectedAlert()`
- `ComposeContainerViewControllerTests/testOneStableChildKeepsItsExplicitSemanticsIndependentOfTheOuterView()`
- `ComposeContainerViewControllerTests/testChildUsesFullBoundsAcrossSizesAndOpposingSemanticDirections()`
- `ComposeContainerViewControllerTests/testSystemDecorationAndOrientationPoliciesStillBelongToTheChild()`
- `ComposeContainerViewControllerTests/testDefaultAppearanceForwardingReachesTheSameChildExactlyOnce()`

The first test launches the actual application with the existing English launch
arguments, waits for foreground and `parlor-home-brand`, rejects an alert during
its observation window, and checks continued foreground state. The other four
compile the same production container source into the test target; their children
are UIKit fixtures, **not actual Compose controllers**.

This narrow smoke does not prove System-language ownership, Settings behavior,
active-session retention, Arabic Compose semantics, interactive Back, general
accessibility, physical LAN, full game traces, signing, or Store readiness. It does
not replace V6's separate instrumented language/session matrix or erase earlier
red witnesses.

## Source identity and allowed copy delta

`source-bindings.json` is copied byte-for-byte from V6 and binds freeze 02:

- Base commit: `3625d0663ba6eb51338cbd5f9dc45f859ec18846` on `main`.
- Base Git tree: `db7f3d2afe73a13628296daee2cce71165eebc8d`.
- Dirty/untracked source manifest: **669 inputs**,
  SHA-256 `9ac2be536979f2216b39c10a6d148125a12380987d4efd483bbcbaeb1733318e`.
- Tracked diff SHA-256:
  `148fb4583d8fa8341b3cbc48a7fc0bc7564bf4df5c27e9d80e32c6bfdc57b784`.
- Copy-only build inventory: **613 inputs**, checked before and after execution.

The new temporary build copy keeps all Kotlin, Swift, test, resource and dependency
inputs byte-identical. Its only file change is the copied
`iosApp/iosApp.xcodeproj/project.pbxproj` shell phase: the unchanged V6 phase
template embeds with strict dependency verification, immediately stops its
isolated Gradle registry, records both exits, and normalizes only after success.
The runner records the exact copied diff and rejects any other input change,
addition, missing file or source symlink. Fresh copy-relative framework search
paths prevent linking an original-worktree or stale framework.

`copied_sources.py` intentionally remains byte-identical to V6. It contains unused
instrumentation helper definitions, but this runner imports/calls only source-copy
and post-build identity helpers. No observer templates are supplied, no observer
is added, no test is replaced, and the phase-only copy verifier would reject such
changes. Helpers, phase template and original V6 runner are explicit hash controls;
the latter is read by the ownership-equivalence contract test.

## Ownership, cleanup and evidence

The V6 lane lock, source/controls binding, process ownership, cancellation/failure
finalizer and `SecondaryFifoLedger` are retained. Only a fresh, uniquely named
iPhone 17 Pro / iOS 26.5 ARM64 simulator is created. Signing is disabled; the app
must have `com.parlor.app.debug` identity. No application data/container or
preferences are read by this runner. No user simulator, phone, signing inputs,
global Gradle cache or unrelated process is owned or deleted.

After Xcode, including failure, stop the isolated Gradle daemon immediately.
Retain structured XCTest results, source/control/copy manifests, compact command
logs, app/framework hashes and cleanup receipts. Require exactly five passed,
unskipped tests on the exact owned simulator. Then shut down/delete that simulator,
stop only attested workers, remove attested FIFO paths, verify copied input hashes,
unlink shared-cache symlinks, and remove the owned copy/DerivedData/temporary home.
Unknown holders or incomplete ownership attestation fail cleanup rather than
authorizing broad deletion. Original-worktree outputs are never removed by this
runner. Its unchanged cold-launch test has no explicit app teardown; owned-device
shutdown provides teardown even when that test fails.

## Proposed root-only execution

These commands are proposals, **not successful execution evidence**. First the
independent reviewer must approve the exact control manifest. Root then runs the
ten smoke contracts and copied FIFO fixture tests under its normal recorded cycle
finalizer; those tests create only synthetic temporary files/FIFOs and send no
process signals:

```sh
/usr/bin/python3 -B -m unittest discover \
  -s remediation-runs/2026-09-06-approved-policy-completion/native/ios_wrapper_smoke_v1 \
  -p 'test_*.py' -v
```

Only after the control cycle passes and cleanup finishes:

```sh
/usr/bin/python3 -B \
  remediation-runs/2026-09-06-approved-policy-completion/native/ios_wrapper_smoke_v1/run_ios_wrapper_smoke_cycle.py \
  ios-wrapper-smoke-01 INDEPENDENTLY_REVIEWED_CONTROL_SHA256
```

`author-control-manifest-01.json` is the review request, not self-approval.
`runner-diff-final-01.patch` is the final draft diff against V6; the earlier
`runner-diff-from-v6.patch` is preserved but predates the added base-runner control
binding and is not the final identity.
