# Independent IOS-R1 app-host cycle02 execution and cleanup review

Reviewer: `/root/whodunit_cont`; runner/fixture executor: `/root`. Continuous review completed on 2026-09-05. Repository `/Users/abdelrahman/Projects/parlor`, branch `main`, commit `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`. Tracked status remained empty. Untracked instructions, design, handoff, prior audit and current audit material were preserved. This child ran no Gradle, Xcode, simulator/device, signing, app, or process-mutation command.

## Conclusion and boundaries

**PASS for one actual unsigned iOS app-host XCTest and source-attributed diagnostic observation. Not PASS for healthy recovery.** The existing Home warning was observed before the probe; a subsequent rerun using the original production Koin/store/transport objects returned empty local inventory and unavailable multiplayer secure storage. An additional equivalent native query returned `-34018` in the same unsigned app. Neither the original Home source results nor its original native OSStatus were intercepted.

**Final known task-owned cleanup: PASS AFTER supplemental cleanup.** The runner's original cleanup PASS was incomplete: an attested Apple `ibtoold` FIFO directory survived. This reviewer found it, root independently verified ownership and safely removed only the exact dead process's FIFOs/empty directories, and this reviewer independently rechecked absence. Original receipts were not overwritten. This is an audit-harness cleanup gap, not a Parlor application defect.

This review confirms execution/evidence integrity within the stated scope. It does not classify IOS-R1 as a confirmed application defect; `/root/session_cont` owns the separate application-candidate disposition. No production fix, issue closure, signed-device validation, Store operation, or broad readiness claim is supported by this cycle.

## Exact executed input and copy binding

- Actual runner: 622 lines, SHA-256 `7e6abf8cb71ea66fe73123323f6cbc907381af595a0a3127679627bd9fc1b5aa`; root tool session `94158` exited `0`.
- Immutable executed snapshot: `evidence/iosr1-apphost-02/runner-executed.py`, equal to independently pre-reviewed `evidence/iosr1-runner-independent-whodunit-cont/draft-7e6abf8cb71e.py` and the current runner.
- Actual execution receipt: `evidence/iosr1-apphost-02/receipt.json`, SHA-256 `2dd7b901ddc2f33dda5fcb91c108f97156fb94f2d209e565754858d6032ec332`.
- Independently recomputed all **12 input hashes**, matching the executed manifest and archived reviewed bytes; independently recomputed **45 production/additive source bindings**. All still match at report generation.
- Reconstructed all **18 copied wrapper/config/asset files in memory**, including the three narrowly modified copy files and the actual cycle02 embedded-stop receipt path. All reconstructed hashes match the actual copied manifest; original files are unchanged. This is reconstruction, not recompilation.
- AST-extracted only `verify_xctest`, `verify_probe`, and the one-file patch function from the exact archived runner. Actual retained XCTest/probe JSON passes its strict validators. No orchestration was imported/executed by the reviewer.
- Prior retry approval and 106 pure synthetic PASS cases remain in `reviews/iosr1-runner-retry-independent-whodunit-cont.md` and its evidence. They test harness safety, not the app runtime.

Detailed reconciliation: `evidence/iosr1-runner-independent-whodunit-cont/cycle02-execution-binding-reconciliation.json`. Per-file hashes and honest source/evidence ranges: `coverage/reviews-iosr1-apphost-02-whodunit_cont.jsonl`. The 45 hash bindings are not represented as 45 fresh full-file source reviews.

## What actually ran

| Gate | Result | Actual evidence and scope |
| --- | --- | --- |
| `:composeApp:linkDebugFrameworkIosSimulatorArm64` | PASS | `framework.log:1–211`; strict verification, wrapper Gradle 8.13/JDK21; 90 actionable tasks, 86 executed/4 up-to-date; exit0. Additive audit source only. |
| Copied Xcode embedded Gradle phase | PASS | `xcodebuild.log:818–1093`; `embedAndSignAppleFrameworkForXcode`, 137 actionable tasks, 58 executed/79 up-to-date; embedded receipt `build_exit=0`, `stop_exit=0`. |
| Generated probe export | PASS, bounded | Full generated header SHA-256 `47bf42c606417baf1e7fddac4138c01874448df0079f815a349b2b707163774e`; reviewed only `ComposeApp.generated.h:153–166` / complete `probe-export.h`: singleton `shared`, `cancel()`, `start(onJson:)` with `void (^)(NSString *)`. Actual Swift caller compiled and ran. |
| Actual XCTest | PASS, one method | `IOSAppLaunchUITests/testAuditProductionRecoverySourceAttribution()`; 1 passed, 0 failed/skipped/expected failures; `xcresult-summary.json`, `xcresult-tests.json`, `xcodebuild.log:1510–1577`. Test duration11.966s. |
| Source-attributed diagnostic collection | PASS as observation | `probe-result.json`, strict schema and source binding reconciled. Healthy multiplayer recovery is **not** asserted. |
| Signed/physical storage and Store behavior | BLOCKED / outside cycle | Unsigned fresh simulator; no real provisioning/entitlements, private credentials, device, Store-qualified toolchain or Store action. |

Actual simulator: fresh task-owned iPhone17Pro ARM64, iOS26.5/build23F77, UUID `441CC892-68AB-4C6A-8B0C-739C6B21B06B`. Xcode26.5/build17F42 was logged; this is not repository Store-qualified Xcode26.3/17C529. App metadata is `com.parlor.app.debug`, version1.0.0/build1, minimumOS16. Signing was explicitly disabled; the KGP environment omitted `EXPANDED_CODE_SIGN_IDENTITY` rather than supplying the blank string that caused cycle01's tool failure. Cycle01 remains a failed harness/build attempt, not an app crash or successful runtime run.

The copied XCTest retains the original launch/Home/foreground/no-alert assertions, observes the original warning, captures a screenshot, taps the audit trigger and waits for a receipt-complete marker. `defer` terminates the app. The no-alert assertion's retained wait is only2s; this is not a repeated-launch, extended stability, or every-screen test. XCTest runner PID68318 and application PID68419 are visible in the actual log and absent in subsequent scoped checks.

Warnings about the host-disabled `iosX64Test`, Gradle/Kotlin future compatibility, experimental lint pin, missing AppIntents metadata, system accessibility duplicate classes and debugger/launch metrics do not turn skipped architectures into evidence or the successful XCTest into an observed app crash. The alert-absence timeout emits diagnostic triage wording; the actual test count/result is authoritative. No policy/validator was weakened for the run.

## Production source path and observed outcomes

The reopened path is:

`ContentView.ComposeView -> MainViewController/startKoinOnce -> allModules(platform storage, storage, P2pKit DI) -> App Home produceState -> loadHomeRecoveryAvailability`.

The concurrent local branch calls `readLocalRecoveryInventory -> FileBackedSnapshotStore.listUnfinished -> IosSnapshotFileSystem.list`. The multiplayer branch calls the real `P2pKitRoomTransport.resumableSession -> ResumableCredentialStore.loadResumeCandidate/loadRecord -> PlatformKeyedSecureStorage.get -> IosSecureKeyValueBacking.get/read -> SecItemCopyMatching`.

Source anchors (absolute paths are in the coverage ledger):

- `composeApp/src/commonMain/kotlin/com/parlor/app/shell/home/HomeRecoveryAvailability.kt:48–85,87–146`: bounded local scan, concurrent lookup and explicit unavailable-vs-empty projection.
- `composeApp/src/iosMain/kotlin/com/parlor/app/storage/IosSecureKeyValueBacking.kt:74–112`: not-found maps to null; unexpected native status throws a sanitized error; returned CoreFoundation value is released.
- `shared/storage/src/commonMain/kotlin/com/parlor/storage/secure/PlatformKeyedSecureStorage.kt:32–40`: cancellation propagates; other exceptions become `IoError("secure_storage_io")`.
- `shared/transport-p2p/src/commonMain/kotlin/com/parlor/transport/p2p/ResumableCredentialStore.kt:146–152,310–408` and `P2pKitRoomTransport.kt:615–680`: credential lookup failure maps to unavailable rather than fabricated no-session success.
- `composeApp/src/commonMain/kotlin/com/parlor/app/App.kt:95–151,210–247` and `shell/home/HomeScreen.kt:85–134,247–291`: production loader inputs, warning card and Retry refresh path.

Observed sanitized JSON:

- `probe_kind=production_koin_rerun`, `harness_status=observation_complete`.
- `original_home_invocation_intercepted=false`.
- Local: `success_empty`, entry_count0.
- Multiplayer: `failure`, `error_category=secure_storage_unavailable`.
- Combined: `has_unavailable_source=true`, local_count0, has_multiplayerfalse.
- Native corroboration: `origin=subsequent_same_app_equivalent_read`, `original_production_status=false`, OSStatus`-34018`, result absent, not-found constant`-25300`, missing-entitlement constant`-34018`.

The probe uses existing production objects, not replacement in-memory storage. It records only fixed categories/counts/booleans and an optional numeric native status; it does not export key/record/metadata/player contents. It wraps the original store list result without changing its outcome. The separate native read is gated after the empty-local/unavailable-multiplayer outcome. It is not proof of the original Home call's native return, of the reason for every prior launch problem, or of signed runtime behavior.

Counter-evidence was explicitly retained: `errSecItemNotFound` is already handled, so warning-on-truly-absent-record is not established; intentional error projection distinguishes unavailable from empty. Empty local inventory never exercises encryption, key creation, data reads, writes, deletes, persistence durability or real rejoin. The warning is supported by the observed rerun, rather than proving a wrong application warning or a newly fixed storage defect.

## Independent visual and artifact evidence

The full attachment manifest binds one text record and one PNG to this exact test/device. Text is exactly `original_home_warning_present=true` (34bytes, no newline), SHA-256 `9555edb211f815a00e79567874be62581404b0651a415e5ed70b9ca81665c0d4`.

The PNG is352499bytes,1206x2622, SHA-256 `1898984f1f4c2c56176577271b5e871f8ea607807833215db818d5b9d3f50371`. Its header and actual `view_image` rendering were inspected: Home warning/body/Retry are visible before the trigger; the copy-only “Run recovery audit”/`idle` overlay is visible. This diagnostic overlay overlaps Settings deliberately in the copied wrapper and is not a shipping UI defect. Manifest timestamps1788617734.602/.711 and the XCTest event ordering put both observations before the probe trigger.

Only the root runner's recorded hashes of `Parlor` (40344bytes) and `Frameworks/ComposeApp.framework/ComposeApp` (82171688bytes) were retained for executable artifacts. Those cleaned binary bytes cannot now be independently rehashed; `Parlor.debug.dylib`, the complete bundle and all nested contents were not completely inventoried/hashed. This is source-bound unsigned diagnostic evidence, **not full artifact/signature/release provenance**. The snapshot is not full UI, RTL, accessibility, safe-area, app-switcher or gameplay proof.

## Cleanup: initial gap, supplemental correction, final scoped PASS

1. Original runner executed immediate isolated `./gradlew --stop` after the standalone framework task (0.001023s after task completion), immediately after Xcode returned (0.002759s), and once more in finalization; all exit0. The copied embedded Gradle phase also stops immediately before retaining its framework for the required Swift link/runtime. Framework/compiler outputs were retained only to finish that same dependent verification, as explicitly recorded, then removed.
2. The runner shut down/deleted its exact simulator, stopped only attested workers, removed15 generated output paths (17potential paths checked) and its exact copied wrapper/DerivedData/isolated home/temp. Shared cache links were removed without deleting global targets.
3. At `2026-09-05T14:26:02.265270Z`, this reviewer independently found all73 attested PIDs absent and all expected root/module/build-logic outputs/main temp/device paths absent, **but** `/var/folders/6m/vxwlbjsn7vs6h80_98w6x7p40000gn/T/ibtoold-67921` remained. The runner's own process-ownership argv links PID67921 to AssetCatalogSimulatorAgent68144 and exact twoFIFO paths. Initial independent `cleanup_verified=false` remains in `evidence/iosr1-apphost-02-cleanup-independent-whodunit-cont.json` (SHA-256 `a25e292a631345d300791ef05a6dd22f32f990f5744479a9d8d9617120fb0bc4`).
4. Root independently verified lineage, uid, inode/birthtime, no symlinks, bothPIDs absent and no open holder using exact-root metadata checks. At14:30:53.978–14:30:54.042Z root unlinked only the twozero-byteFIFO names and removed the now-empty IB/root. No build or process termination occurred. Complete94line additive receipt: `evidence/iosr1-apphost-02-secondary-cleanup/receipt.json`, SHA-256 `86f62321ef1c39952b6f9a9aeeae54b7d273e810335622655540f2991ba8359d`.
5. At `2026-09-05T14:36:10.894788Z`, this reviewer independently repeated the exact73PID and path checks: all73absent, all17potential outputs absent, main raw/canonical temp and device plus the secondary root absent, global Gradle cache/wrapper targets preserved, tracked status empty. Final combined scoped PASS: `evidence/iosr1-apphost-02-post-secondary-cleanup-independent-whodunit-cont.json`, SHA-256 `389f5a4e7ede107644fa1812c706cf70a2c7e4f8200763c8900340e610f4aea4`.

The earlier `iosr1-apphost-01` and `xcode-ui-01` exact recorded primary temp/device paths were also absent. Their retained provenance identifies no analogous secondary Apple path, so this review does not infer ownership or promise that every global temp file from every earlier cycle is gone. It performs no broad temp deletion, global process scan/termination or private user-container inspection. The original runner receipt, initial independent failure and additive correction are all retained together.

## Remaining limits and continuation

- Separate IOS-R1 disposition by `/root/session_cont` must cite this observed unavailable branch and the native-query attribution limitation, not invent a healthy-storage PASS or an application fix.
- Physical Android/iOS LAN, signed Keychain read/write/delete/durability/rejoin, repeated cold launches, Store identity/signing/provisioning and actual Store-qualified build gates remain outside this run.
- Only explicitly listed source/evidence ranges are claimed read. The entire1160line receipt and211line framework log were read; only stated chunks of the1577line Xcode log were read. Only the14line probe declaration of the1846line generated header was inspected semantically.
- No application changes, Git operations beyond read-only identity/status, new issue, merge or Store operations occurred. No new generated build outputs were created by this child's read/AST/receipt work; all known cycle02 task-owned outputs/processes were independently gone after the correction.

Machine-readable final receipt: `evidence/iosr1-apphost-02-independent-whodunit-cont.json`. This bounded review cannot establish whole-project READY or absence of other defects.
