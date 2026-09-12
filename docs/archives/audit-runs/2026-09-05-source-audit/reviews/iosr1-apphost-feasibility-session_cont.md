# IOS-R1 — bounded same-app feasibility design

- Reviewer: `/root/session_cont`; requested by `/root`.
- Cutoff: **2026-09-05T13:06:33.103481+00:00**. `main`, commit `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`; tracked/index changes absent.
- **Design only. No fixture, copied app, build, SDK/device command, signing action or runtime execution was created/performed in this pass.** Existing runners and production entry paths were reopened. This is not a new finding approval.

## Feasibility conclusion

A small additive `:composeApp` **iosMain** fixture can call actual production recovery code and retrieve the **same Koin singletons used by the original running App**. It does not need fake storage, a second DI graph, native method swizzling, dependency changes, entitlements or Store credentials. A copied Swift wrapper can expose a one-shot, launch-argument-gated audit trigger **after the unchanged original Home has rendered**. This is materially better than the earlier standalone Objective-C bundle for attributing local-versus-multiplayer failure.

The current APIs deliberately erase numerical native status. Do **not** promise the original `SecItemCopyMatching` OSStatus/NSError from the high-level result. A subsequent identical read inside the same app can corroborate an environment error, explicitly labeled as a different invocation. No need for invasive source replacement or an unsafe debugger/interposition scheme merely to obtain a number.

## Actual entry and data path

All paths here are relative to `/Users/abdelrahman/Projects/parlor`; the companion manifest includes absolute paths, hashes and actual reviewed ranges.

1. `iosApp/iosApp/iOSApp.swift:3–9` → `ContentView.swift:14–35,54–61` → `MainViewController.kt:18–33` calls `startKoin { modules(allModules) }` and original `App()` once. The copied wrapper must retain this call, all lifecycle bridges and the original privacy cover. Do not call `startKoin` or replace `allModules` from the fixture.
2. `di/AppModule.kt:67–74` installs `storageModule`, real `platformStorageModule()` and required P2P bootstrap. `StorageModule.kt:16–23` binds `FileBackedSnapshotStore`; `PlatformStorage.ios.kt:13–19` supplies `IosSnapshotFileSystem`, `IosSecureKeyValueBacking` and `PlatformKeyedSecureStorage` singletons.
3. Original `App.kt:73–77,120–143` obtains those objects and runs local/multiplayer Home discovery together. `HomeRecoveryAvailability.kt:48–85,88–145` contains callable **internal** production functions `readLocalRecoveryInventory`, `loadHomeRecoveryAvailability`, `resolveHomeRecoveryAvailability`. An added iosMain file is in this same app compilation, so it can reference them without making production APIs public.
4. `P2pBootstrap.kt:13`, `P2pTransportModule.ios.kt:51–71` bind actual `P2pKitRoomTransport`. Its `resumableSession():625–655` reads `ResumableCredentialStore:146–151,318–340`; a genuinely absent credential is successful null, not failure. The fixture must never call `host`, `join`, `resume`, `discardResumableSession`, save or credential mutation.
5. `FileBackedSnapshotStore.kt:118–149` and `IosSnapshotFileSystem.kt:74–123,150–166,219–228,361–370` own the local scan. Empty fresh storage can create its protected directory but does not require a snapshot encryption key. It is not valid to assume every empty-scan failure is Keychain-related.
6. `IosSecureKeyValueBacking.kt:74–112` throws a fixed error for non-success/non-not-found read status; `PlatformKeyedSecureStorage.kt:32–39` maps it to fixed `secure_storage_io`; transport maps failure to `SecureStorageUnavailable`. The original status is gone. Filesystem native NSError values are similarly not surfaced.
7. `App.kt:229–241` maps combined readiness into the real Home warning. `HomeScreen.kt:109–114,253–279` renders the inline card. English source title: `composeApp/.../values/strings.xml:147`. This is not a native alert, which explains why the old narrow launch test can pass while the warning is visible.

## Proposed minimum additive inputs

Create these **only if root chooses the next verification cycle**:

- `reproducers/iosr1_apphost/IOSR1AppHostProbe.kt`: one additive iosMain file, preferably package `com.parlor.app.audit`; one public Swift-callable start function, internal result mapping and bounded one-shot coroutine ownership. No existing Kotlin source copies/replacements needed.
- `reproducers/iosr1_apphost.init.gradle`: explicitly add only that directory to `project(':composeApp').kotlin.sourceSets['iosMain'].kotlin.srcDir(...)`; require exact repository root, a task-scoped audit marker, `Debug`, and `iphonesimulator` SDK. Apply **only** to the isolated Xcode Gradle phase, never normal build/clean/stop commands. Do not use global init scripts, dependency/repository overrides or source-set exclusions.
- A task-owned temporary **copy of the allowlisted first-party iOS wrapper** (not a Git worktree/checkout; no private config/user state). Keep original iOSApp, Info.plist, assets, privacy declarations, version and Debug identity. Change only copied `ContentView.swift` for the gated audit control and copied XCTest for its trigger/assertion; change copied project shell/search paths for original repository framework compilation. Preserve the exact copy diff/manifest.
- Small root-owned runner supplement, based on `run_xcode_ui_cycle.py`, with an explicit actual-result collection step and reliable nested finalizers. Do not turn this into a new app architecture or large test harness.

### Kotlin probe behavior

Use a non-suspending exported function accepting a `(String) -> Unit` callback; inspect the generated framework header to confirm its actual Swift symbol/callback signature before relying on a guessed name. Trigger it on the main thread after Home observation. Obtain `KoinPlatform.getKoin()`—the same API used by `MainViewController.kt:55–56`—then obtain `SnapshotStore`, `RoomTransport`, and `GameShellRegistry`. Optional Boolean type checks can attest `FileBackedSnapshotStore`/`P2pKitRoomTransport` and iOS backing types without printing object descriptions.

The minimum source-result probe is:

```text
in a one-shot owned Main coroutine with a bounded timeout:
    koin = the already-running application's Koin
    store, transport, registry = koin singletons
    router = GameShellRouter(registry)
    concurrently:
        local = readLocalRecoveryInventory(store)         // original production function
        multiplayer = transport.resumableSession()       // original production instance
    ready = resolveHomeRecoveryAvailability(
        local, multiplayer,
        the same local/multiplayer router predicates as App.kt:130–141)
    emit only sanitized categories, counts and ready.hasUnavailableSource
```

This repeats the eight-line orchestration at `HomeRecoveryAvailability.kt:93–101`, while calling the original local reader, actual transport and original resolver; disclose it as an **in-process production-backed rerun**, not interception of the private `produceState` value from the first Home frame. Do not create another `App()`/controller, `KoinApplication`, store or transport.

If avoiding even that tiny orchestration duplication is preferred, call original `loadHomeRecoveryAvailability` with a **pass-through `SnapshotStore` delegate** observing `listUnfinished` and a wrapping `loadMultiplayer` lambda observing the real transport result. On the fresh-empty prerequisite, `Success(emptyList())` completely determines the local inventory; `Failure` is propagated unchanged. Preserve all return values/cancellation. Unexpected nonempty records must abort the fresh-data diagnostic rather than be logged or mislabeled as empty. This alternative is slightly more plumbing and does not capture the original first Home invocation either.

Do not block the UI thread with `runBlocking`. Use a probe-owned job rather than canceling an app-owned scope/Koin. Main-thread one-shot admission, `withTimeout`, typed cancellation handling and final job cleanup must prevent repeated taps or disappearing views from leaving collectors/poll loops. A timeout is `HARNESS_TIMEOUT`, not `SecureStorageUnavailable` or a passing check. Swift should receive only a final bounded JSON string on Main.

### Trigger and original observation

A copied `ContentView` can insert a small audit-only button inside the original ZStack **below the existing privacy cover**, guarded by a unique launch argument and active scene phase. Its action calls the added Kotlin function. It does not change `ComposeView.makeUIViewController`, the original `App`, language handling or lifecycle callbacks.

The copied XCTest launches with original English arguments plus the audit flag, waits for original `parlor-home-brand`, then observes the warning before triggering the probe. It may wait a bounded interval for the exact English title above and retain an original-Home screenshot. Failure to see a warning is a **non-reproduction observation**, not proof that recovery initialized successfully; record it and still obtain the source results. Use a stable audit-only accessibility ID for the one-shot button and completion status. Do not replace semantic state observation with an unexplained sleep or assume no native Alert means recovery is healthy.

The Swift callback writes only the sanitized JSON atomically to a fixed task-owned app-temporary path, for example `tmp/parlor-audit-iosr1-result.json`, and sets an audit completion ID after successful write. Report write failure as a separate harness failure, without printing raw NSError. Parent obtains only this fixed file from the **new UUID's** `get_app_container ... data` path; verify ownership and a small byte ceiling before parsing. Do not recursively dump preferences, Documents, Library, Keychain, environment or the app container.

## Optional same-app numerical corroboration

Only after the actual production results have been captured, optionally call the equivalent read-only Security query inside the same app on Default/background dispatch. The existing safe query shape is in `reproducers/IOSR1NativeProbe.m:14–28`; production binding is `IosSecureKeyValueBacking.kt:145–163` and credential constant `ResumableCredentialStore.kt:367`.

Keep generic-password/service/account/synchronizable/return-data/limit exactly equal. Capture only numerical OSStatus, result-present Boolean and the compiled not-found/missing-entitlement constants; release returned CFData without copying or printing it. No add/update/delete, entitlement injection, access-group change, new key, or raw query/error-description logging. Label `origin = subsequent_same_app_equivalent_read`, never `original_production_status`.

Alternatively, a direct repeat read through the same Koin `SecureKeyValueBacking` can map an **allowlisted fixed exception message** to `native_read_status_rejected` without exposing the message or numerical status. Unexpected successful bytes are zeroed and recorded only as `UNEXPECTED_NONEMPTY_FIXTURE`. This is another invocation; it is not required for basic local/multiplayer attribution.

Do not add Foundation companion operations unless the actual local result fails. A generic native program already showed Foundation success; more equivalent operations are unnecessary if the real local scan now succeeds. If a local failure needs stage diagnosis, the same-instance repeat `SnapshotFileSystem.list()` can distinguish allowlisted fixed failure stages, but original NSError remains unavailable without a separately authorized deeper capture.

## Source/artifact identity and build safety

- Fresh input manifest: original branch/HEAD/tree, tracked/index status, relevant Kotlin/native/build/config hashes plus all additive fixture/copy hashes. Record every copied-wrapper difference. No signature of a commit alone substitutes for those inputs.
- Preserve Debug `com.parlor.app.debug`, version source and actual installed Xcode/runtime values. Do not substitute the standalone probe's `audit.parlor...` identity; own the simulator instead. Use original unsigned flags (`CODE_SIGNING_ALLOWED=NO`, `CODE_SIGNING_REQUIRED=NO`, empty team/identity), not actual signing material.
- The copied project needs the original root as the explicit Gradle working directory and framework search root; default `SRCROOT/..` would point at the temporary copy. Preserve the version xcconfig relative include by copying only its known tracked input or binding it explicitly and hashing it.
- Pass `-I <fixture-init>` explicitly in the copied Gradle shell phase. Keep wrapper/JDK21, strict verification, original dependency pins, one worker, no parallel/cache, known memory budget. No graph override. Record the actual compile source inputs; verify original Kotlin paths and additive file only, not a generated replacement of App/native storage.
- Make the copied phase fail closed and record original Gradle exit before normalization; stop Gradle immediately after the embedding task returns. That test-only shell behavior does not fix IOS-B1 in production. Required framework bytes remain until Swift link/runtime/header inspection completes, with explicit temporary-retention reason.
- `run_xcode_ui_cycle.py:103–125` already has source identity, unsigned flags, one destination, result extraction and binary hashes. Its current implementation is not a parameterized copied-project/probe runner; the supplement must explicitly update the project path, result collection and owned-temp paths. Do not accidentally invoke it unchanged and report the old single launch test as the new probe.
- Capture app/framework hashes, build/configuration identity, generated API header excerpt, relevant compiled source binding, test method/results, probe schema-valid JSON, timestamps and pre/post original warning observations. An unsigned augmented wrapper is a diagnostic artifact, not a shipping candidate.

## Evidence and classification contract

Suggested new immutable cycle: `evidence/iosr1-apphost-01/` with `receipt.json`, `input-manifest.json`, `copied-wrapper.diff`, `probe-result.json`, test/command logs, selected header/identity metadata and before/after screenshots. Keep only compact required evidence.

Example allowed fields (values are **not predictions or observed results**):

```text
probe_kind: production_koin_rerun
local: { kind: success|failure, entry_count, unreadable, additional, error_category }
multiplayer: { kind: success_null|failure|unexpected_nonnull, error_category }
combined: { has_unavailable_source, local_count, has_multiplayer }
optional_native: { origin: subsequent_same_app_equivalent_read, os_status, result_present }
harness: { completed, timeout, unexpected_fixture_data }
```

Never serialize local session IDs, game snapshots, display names, credential bytes, assignments, seeds, private state, error causes/descriptions, Keychain queries, URLs/container paths or object `toString()` results. Map `DataError`/`NetError` through explicit allowlisted category names; do not print payload-bearing `IoError`, `Unknown` or `TransportFailure` text. Empty-fixture violations are harness failures, not app defects.

If this reproduces local success/zero entries plus multiplayer `SecureStorageUnavailable`, the actual production-backed same-app call is attributed to multiplayer storage; the original resolver deterministically explains the warning. A same-app `-34018` companion supports the unsigned-environment explanation but **does not capture the original invocation's number**. It still does not prove shipping-device failure or justify suppressing the warning. If both sources succeed, the new run does not reproduce the prior warning; retain the historical observation and do not invent a fix. Other results require independent examination before changing IOS-R1's current unconfirmed classification.

## Finalization requirements

Use the root-owned lane only after Android is finished/clean. Fresh task-owned simulator/DerivedData, no existing user device/profile and no existing output ownership ambiguity. The new helper needs independent source/safety review before execution.

Stop Gradle immediately after any nested output-generating invocation (including failures/timeouts), preserve required result evidence, then remove exact task-created root/module/build-logic outputs and copied-wrapper/DerivedData temp roots. Terminate only this test app/process and shut down/delete only its UUID; verify app/test/owned worker termination and UUID absence. Treat individual finalizer failures independently so a failed stop, extraction, termination or shutdown does not prevent remaining safe cleanup. Never delete global caches, user simulators, source, credentials, configs or earlier evidence. The existing runners are useful patterns, not proof of a future cleanup result.

## Remaining limitations

This proposed experiment has not run or compiled. It would not re-create the already-deleted historical app container, intercept the first original Home call, or establish its exact OSStatus. It would not verify authenticated save/decrypt/migration durability, real Keychain entitlements, device lock/background/backup, physical LAN/rejoin, complete gameplay, full UI/RTL/a11y/performance, signed release or qualified Xcode26.3 behavior. Existing authoritative Apple references are retained in `research/IOS-R1-session_cont/research-ledger.json`; no new online/API-success claim is made here. Exact exported symbols and runtime behavior remain build/runtime verification, not an assumed PASS.
