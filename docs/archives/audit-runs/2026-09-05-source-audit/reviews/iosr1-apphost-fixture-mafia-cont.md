# IOS-R1 app-host fixture — independent source/safety review

Reviewer: `/root/mafia_cont`; author: `/root/session_cont`. Baseline `main`, commit `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`.

## Decision

**Source/safety approved for root-owned orchestration and compilation, subject to the prerequisites below.** Not compiled, not runtime-verified, not an application fix or shipping-signature approval. No fixture/production file was edited by this reviewer. Exact nine-file snapshots, hashes, independent patch results and source bindings are in `evidence/iosr1-fixture-independent-mafia-cont/independent-static-receipt.json`.

All nine fixture files were read completely (882 lines total): main probe209, native88, init84, Swift patches78/44, README49, phase18, copy manifest119, source binding193. The independent in-memory patcher matched both author's final template hashes, three/two hunks respectively. All43 original +2 additive source hashes and18 copy-allowlist hashes match. Hashing is not substituted for source review; actual reopened source ranges are recorded separately in the coverage receipts.

## Reachable production path

`MainViewController.kt:18–32` initializes original `allModules`; `App.kt:73–77,120–143` requests the same singleton SnapshotStore/RoomTransport/GameShellRegistry as the probe. `StorageModule.kt:16–23`, `PlatformStorage.ios.kt:13–19`, `P2pTransportModule.ios.kt:51–72`, and `ContentModule.kt:39–55` independently establish the concrete bindings. The fixture constructs only a delegating observer and the pure GameShellRouter, not substitute Koin, storage or networking services.

`HomeRecoveryAvailability.kt:48–146` receives the original list/multiplayer results unchanged. Empty local IDs reach no loadMetadata and the observer explicitly refuses metadata calls. Failure categories are literal enums/labels, never error payloads. `P2pKitRoomTransport.kt:625–655` reaches credential storage, not host/join/discovery; its existing lifecycle collector belongs to the app and is left untouched. The report distinguishes local success-empty/failure, multiplayer success-null/failure, and the original resolver's combined availability.

## Mandatory privacy/lifetime conditions

- **Fresh, newly created, task-owned simulator before any original app launch.** Bundle ID/launch flags and the late empty-result check are not a privacy sandbox. `IosSnapshotFileSystem.kt:150–164` can read/migrate legacy contents before listUnfinished returns; `resumableSession` may decode/invalidate an existing credential before the observer sees a result. Never reuse an existing simulator/container, even if its UI appears empty. Root must attest creation/install/data-container ownership before launch and collect only the bounded named result file.
- Single Main-dispatched probe, cooperative20-second timeout, preserved CancellationException, and final cancellation affect the probe's own SupervisorJob only. No app/Koin/transport shutdown is requested. Native synchronous I/O can outlive coroutine timeout until it returns; an outer root-owned process deadline/finalizer is required.
- Primitive JSON only (ASCII literal categories, Booleans, zero counts, optional OSStatus). Swift enforces8192UTF-8 bytes, refuses an existing result and atomically writes only the named task result. No credential, path, domain object, traceback, CFData or query is logged/accessibility-labeled. The overlay is active-only, below the unchanged original privacy cover. Original lifecycle/controller tail and exactly one original MainViewController call were independently byte-compared.
- Copied-wrapper edits and output roots only; preserve18-file allowlist, original bundle/configuration/signing policy and resource inputs. The copied phase's `--stop` must inherit a task-owned Gradle daemon registry; no unrelated user worker may be stopped. Retain needed framework bytes only through header/Swift/runtime inspection, then apply root finalization.

## API and evidence limits

KoinPlatform.getKoin, typed SnapshotStore/RoomTransport APIs, Result/DataError cases, and Foundation/Security query construction match reopened production usage at Kotlin2.4.10, coroutines/serialization1.11.0, Koin4.0.0. This comparison is not compilation. Swift exported object/method/closure names remain deliberately unresolved behind `#error`; root must inspect this build's generated header before substitution. Groovy task/source-set hooks and all Kotlin/Swift syntax still require actual compilation.

Optional native corroboration exactly matches the credential read's generic-password/service/account/non-synchronizing/return-data/match-one attributes. CF dictionaries, strings and returned value have nested finally cleanup; no key/item is created and no returned bytes are copied. It runs **after** production observations, and its JSON explicitly says `original_production_status=false`. It must never be reported as the original Home query's OSStatus. The main fixture reruns the original function on the same instances, not interception of its private initial produceState value.

The copied XCTest proves Home/foreground/no-alert smoke and sanitized receipt delivery only. Its PASS permits an aborted harness report or failed recovery source; root must independently schema-check and classify the JSON. No test/compile/native/device command ran in this review. Only required snapshots, unbound text templates and compact audit evidence were created; no generated build or running process requires cleanup.
