# IOS-R1: invocation-only, same-app recovery probe

**Audit fixture only. Not compiled/run yet. Independent helper review and root-owned orchestration are required.** No production source, dependency, entitlement, app identity or existing test was modified.

## Files and source binding

- `IOSR1AppHostProbe.kt`: one Main-dispatched, process-one-shot Swift entry. Uses the existing real Koin graph and actual `loadHomeRecoveryAvailability`; forwards the original `SnapshotStore.listUnfinished` and `RoomTransport.resumableSession` results unchanged. Nonempty list aborts before metadata access; nonnull multiplayer result aborts. No host/join/resume/save/discard operation is requested.
- `IOSR1NativeCorroboration.kt`: optional **subsequent** equivalent read in the same app. No key/item write and no returned CFData copied/logged. All CF ownership is released. It is not interception of the original production OSStatus.
- `../iosr1_apphost.init.gradle`: explicit invocation-only addition to `:composeApp` `iosMain`. Requires unsigned Debug simulator context, allowed ARM64 framework task, exact original/input hashes and exactly two additive Kotlin files. Never add it globally or apply it to normal build/test/clean/stop commands.
- `source-bindings.json`: 43 relevant original-file hashes plus the two additions. These are source/API binding, not new review/running-test evidence. Root must still reconcile the complete baseline and current worktree.
- `ContentView.swift.patch.in`, `IOSAppLaunchUITests.swift.patch.in`: apply **only inside the task-owned copied wrapper**. The original controller call, lifecycle methods and privacy cover stay unchanged. The original app Home renders before the audit-only button is tapped.
- `copy-adjustments.json`: exact 18-file tracked wrapper/version allowlist; no xcuserdata/private state/tree copying. Preserve relative directory layout beneath a new task-owned copy root.
- `copied-kotlin-phase.sh.in`: copy-only phase template, not a runner. Substitute only the known source/init paths. It retains required framework bytes but immediately stops Gradle before Swift linking and fails closed on compile/stop failure.

## Before execution

1. Wait for the current shared Android lane to finish and clean. Root alone creates the new simulator, copied wrapper and DerivedData and owns cleanup. Never use a phone or existing simulator/profile.
2. Reopen all fixture files, original APIs and manifests independently. Compare source hashes and complete baseline. Do not silently refresh a failing source binding.
3. The init script requires `PARLOR_AUDIT_IOSR1_APPHOST=1`, `CONFIGURATION=Debug`, actual `SDK_NAME=iphonesimulator...`, `CODE_SIGNING_ALLOWED=NO`, `CODE_SIGNING_REQUIRED=NO`, and empty identity/team/expanded identity. Use JDK21/checked-in wrapper/strict verification/one worker; keep the environment sanitized.
4. Build only the ARM64 Debug framework for header inspection with the explicit init script (or use the embedding phase with a controlled pre-Swift inspection stop). Immediately stop Gradle. Preserve that required framework/source output only until header binding, embedding and runtime collection finish. Record why it remains.
5. Inspect generated `ComposeApp.framework/Headers/ComposeApp.h`. Replace the two Swift call tokens with the **observed** exported object/start/cancel spelling and closure signature; only then remove the deliberate `#error`. No exported symbol name is asserted here. Retain the header excerpt and final copied-wrapper diff. Do not guess and claim a working binding.
6. In the copied project only, replace the Kotlin phase with the template and both framework-search occurrences with the original repository root listed in `copy-adjustments.json`. No other project/identity/version/plist/asset/signing change is intended.
7. Run the copied one-test XCTest on the owned simulator with the audited wrapper. The old runner is not parameterized for this and must not be invoked unchanged as if it performed the new probe. Root must write/review its small orchestration separately.

No full runner, simulator, copied project or generated framework was created by this fixture task.

## Optional native query

Default copied-test arguments request production attribution only. To opt in, add `--parlor-audit-iosr1-native` to the **copied** test's launch arguments and record that exact copy diff. It runs only after a successful empty local list and actual multiplayer `SecureStorageUnavailable` result. The return labels always say `subsequent_same_app_equivalent_read` and `original_production_status=false`.

If optional corroboration aborts/times out after production observation, the result is `harness_status=aborted` with `completed_production_observation` retained separately. Do not erase already completed source outcomes or treat a partial result as a healthy run. The coroutine timeout is cooperative: a blocking native API cannot be forcibly interrupted by `withTimeout`. The root-owned outer process deadline and simulator/app finalizer remain mandatory.

## Result and XCTest meaning

The only collected app file is `tmp/parlor-audit-iosr1-result.json` from the new UUID's verified app data container. Maximum: 8,192 UTF-8 bytes. The copied Swift callback refuses an existing result, writes atomically, and prints no NSError or paths. No broad container/Keychain/preferences dump is permitted.

The XCTest observes the original warning before triggering the production-backed rerun, attaches a Boolean observation and screenshot, then waits for the audit receipt-delivery indicator. **A green XCTest proves delivery, not healthy recovery.** Root must schema-check the JSON and separately classify source outcomes. `observation_complete` can legitimately contain a production source failure; it is not an application fix. Non-reproduction of the original warning is recorded honestly.

Allowed output: schema/harness labels, literal error categories, source success/failure/null/empty, zero entry count, combined availability, and optional numeric status/constants/null result. Never output session/player IDs, display names, snapshots, credentials, host state/seeds, queries, paths, or raw exception/object descriptions. The few `toString` calls serialize only newly constructed primitive JSON objects, not domain results/errors.

This is an in-process rerun of exact production code and instances. It does not observe the original private `produceState` value or reconstruct the already-deleted historical simulator container. Any original-vs-rerun difference remains explicit.

## Lifetime, cleanup and remaining limits

Repeated triggers cannot create a second probe. Cancellation is preserved; the probe's own scope is cancelled on completion/disappearance, never an app-owned scope/Koin/transport. A cancelled/no-result run is not PASS. Existing production storage can create its own protected directory during the empty scan; a genuine empty credential requires no Keychain write.

After each generating command: record status, immediately stop Gradle, preserve required evidence, remove precise task-owned module/root/build-logic outputs once no longer required, remove copied wrapper/DerivedData, stop the owned app/test, shut down/delete only the owned simulator, then verify worker/output/UUID absence. Keep original source, credentials, global caches, user processes/devices and all prior audit evidence untouched. Cleanup failures must not bypass remaining finalizers.

No native compilation/runtime, header export, signed/device behavior, recovery durability, LAN/gameplay or UI accessibility/performance success is claimed. Source fixtures and in-memory patch/hash checks are not those gates.
