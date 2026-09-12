# IOS-R1 — fresh unsigned simulator Home recovery warning

## Independent classification

- Finder: `/root`; independent validator: `/root/session_cont`.
- **UNCONFIRMED — BLOCKED as an application defect.** The visible warning is real; its exact originating failure in the actual KMP app was not captured. The retained evidence establishes a **test/environment evidence gap**, not a confirmed shipping-storage defect.
- No defect severity is assigned. Do not include IOS-R1 in confirmed-code counts or describe it as fixed.
- Reviewed source: branch `main`, commit `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`; no tracked/index changes. Per-file hashes and reviewed ranges are in `IOS-R1-session_cont.sources.json`.

## Observation and actual test scope

I independently inspected `evidence/xcode-ui-01/home-en.png` and `home-ar.png`: both show the inline recovery-unavailable card on the newly created iPhone 17 Pro simulator. The English card says “Some recovery information is unavailable.” This is not a native Alert.

The original root-owned cycle used Xcode 26.5 / 17F42 and simulator iOS 26.5 / 23F77, app `com.parlor.app.debug` version 1.0.0 (1), with `CODE_SIGNING_ALLOWED=NO`, `CODE_SIGNING_REQUIRED=NO`, empty signing identity/team and task-owned fresh output/container. Its XCTest result is genuinely **1 passed / 0 failed / 0 skipped**. `iosApp/iosAppUITests/IOSAppLaunchUITests.swift:9–30` asserts foreground launch, the Compose home brand, no native Alert, and continued foreground state; it does **not** assert successful recovery-store initialization or absence of this inline card. The Arabic screenshot is supplemental visual evidence, not a second XCTest.

The original simulator/container was correctly destroyed after collection. Retained logs contain no Keychain OSStatus, Foundation NSError stage/code, or separated local/multiplayer result. Xcode signing/build warnings or test-runner accessibility diagnostics do not attribute this banner.

## Production path reopened independently

All paths below are beneath `/Users/abdelrahman/Projects/parlor/` (the manifest records absolute paths).

1. `composeApp/src/commonMain/kotlin/com/parlor/app/App.kt:120–143` starts `loadHomeRecoveryAvailability(snapshotStore, roomTransport::resumableSession, …)` at Home. `di/AppModule.kt:61–74`, `di/StorageModule.kt:1–23` and `src/iosMain/kotlin/com/parlor/app/storage/PlatformStorage.ios.kt:1–19` bind the real platform implementations, not a preview fixture.
2. `composeApp/src/commonMain/kotlin/com/parlor/app/shell/home/HomeRecoveryAvailability.kt:88–101` runs the local and multiplayer probes concurrently. Lines 110–145 preserve either failure as `hasUnavailableSource=true`; an empty successful local inventory and successful null multiplayer record yield false. `shell/home/HomeScreen.kt:253–279` displays the ordinary Compose card.
3. Local: `shared/storage/src/commonMain/kotlin/com/parlor/storage/snapshot/FileBackedSnapshotStore.kt:118–149` lists snapshot names and sanitizes failures to `snapshot_io`. `composeApp/src/iosMain/kotlin/com/parlor/app/storage/IosSnapshotFileSystem.kt:74–106` lazily locates/creates Application Support, creates `Parlor/snapshots` with intermediate parents and complete file protection, and sets backup exclusion. Lines 109–123 derive the legacy Documents path; 150–166 check the legacy directory exists before listing, then list the new protected directory. Lines 219–228 accept an empty array but fail on a nil listing. Lines 361–370 fail if backup exclusion cannot be set. An empty scan does not read or create the snapshot encryption Keychain key (those paths begin with encryption/decryption of existing/new records at 241 onward).
4. Multiplayer: `shared/transport-p2p/src/commonMain/kotlin/com/parlor/transport/p2p/ResumableCredentialStore.kt:146–151,318–340` reads the credential key and returns successful null immediately if absent. `P2pKitRoomTransport.kt:625–655` preserves storage failures as `SecureStorageUnavailable`, rather than reporting an empty store. Existing malformed, expired, or unsupported records have additional paths, but none was demonstrated in the fresh installation.
5. Native credential read: `composeApp/src/iosMain/kotlin/com/parlor/app/storage/IosSecureKeyValueBacking.kt:74–112,145–179` uses `SecItemCopyMatching`; **errSecItemNotFound returns null**, success requires actual CFData, and every other status throws the sanitized credential-read error. Its query is generic-password/service `com.parlor.app.resumable-session.v1`/account `p2p-resumable-session-v1`, synchronizable false, return data true, limit one, with **no explicit access group**. `shared/storage/src/commonMain/kotlin/com/parlor/storage/secure/PlatformKeyedSecureStorage.kt:32–39` converts that exception to `secure_storage_io`. The numerical status is deliberately not retained in this path.

Thus absence alone is handled in both probes. A real Keychain or filesystem failure remains visible by design. There is no source-backed reason to suppress the banner, reinterpret every Keychain error as absence, or add a sharing entitlement blindly.

## Controlled native counter-test

The root executed an audit-only Objective-C UIKit application, not a modified Parlor build. I independently read all 90 lines of `reproducers/IOSR1NativeProbe.m`, all 171 lines of `run_iosr1_native_probe.py`, both receipts, numeric output, signature-inspection/launch/compile/cleanup logs, and compared the probe calls against the Kotlin code above.

- The Keychain query attributes match the production read query. The probe does not write a Keychain item, print a query/returned data/error description, or access an existing player container.
- The Foundation calls match the relevant empty-directory stages: Documents lookup without creation, absent legacy existence test, Application Support creation, intermediate protected-directory creation, backup exclusion and directory listing. It executes on a background queue. It does not exercise the Kotlin bridge, Koin, actual Home orchestration, encryption, migration or credential decoding.
- `evidence/iosr1-native-01/receipt.json`: compilation/installation succeeded with `-Wl,-no_adhoc_codesign`; simulator launch was refused (`FBSOpenApplicationServiceErrorDomain`, code 1), and **no API result exists**. This failed experimental harness attempt is not a Parlor crash or a storage result. Terminate exit 3 explicitly says nothing was running; shutdown/delete succeeded.
- `evidence/iosr1-native-02/receipt.json`: fresh task-owned simulator, default arm64 simulator linker stamp, no codesign signing operation/certificate/profile/private key/team entitlement, isolated `audit.parlor.iosr1-native` bundle. Compilation/install/launch and witness collection succeeded. Signature inspection emits no entitlements. This is not a signed Store/device artifact.
- `evidence/iosr1-native-02/probe-result.json` records **Keychain status -34018**, null result; the compiled `errSecMissingEntitlement` constant is also -34018 (distinct from `errSecItemNotFound`, -25300). Documents/Support lookup, protected-directory creation, backup exclusion and listing all succeed; legacy directory is absent, protected list count is zero, all captured NSError dictionaries are empty.

This is concrete, independent native evidence that the equivalent query can fail for missing entitlement in the tested unsigned simulator environment while the empty-directory operations work. It supports an environmental explanation for the app observation. It does **not** establish the original app's precise OSStatus or exclude a KMP/production-specific filesystem failure: the original app and probe are separate bundles/processes, and original per-source results were never collected. No signed or physical-device storage conclusion follows.

## Counter-evidence and tests checked

- The hypothesized “missing snapshots folder always causes the warning” is contradicted by explicit directory creation/existence guards, Apple's directory API contracts, and native-02's successful empty list.
- `errSecItemNotFound` is explicitly handled as an empty credential store; failure cannot be inferred merely from no prior game.
- A missing explicit `keychain-access-groups` setting is not by itself a source defect. Apple documents application-identifier/default-group access and advises hardware-target entitlement inspection for entitlement diagnosis. The audit did not inspect signing credentials or infer a Store entitlement from simulator output.
- `HomeRecoveryAvailabilityTest.kt:1–230` covers fake/in-memory partial failure and empty/null behavior, including the concurrent probe fixture at 133–161. `IosStorageSafetyTest.kt:1–195` covers bounded reads, empty CFData, CBC and legacy deletion, not an actual fresh UIKit-hosted Keychain read. The selected beginning of `ResumableCredentialStoreTest.kt:1–90` uses an in-memory backing. These are source inspections, not newly executed test claims by this validator.
- The launch smoke legitimately passes its narrow checks and must not be presented as complete recovery/platform verification.

## Minimal remaining verification / recommendation

Retain IOS-R1 outside confirmed-defect counts. To attribute it, execute a same-source, source-aware, isolated app-host repro that records only (a) local versus multiplayer success/failure and (b) sanitized native stage plus numerical OSStatus/NSError domain/code. Use synthetic data and an owned fresh simulator; do not log credentials, player data, full queries or raw persisted records. An audit-only same-compilation wrapper is preferable to production instrumentation. Compare authorized, correctly signed app/device behavior separately if credentials/environment become available; this audit neither authorizes nor performs signing.

Only after attribution should an implementation change be proposed. Preserve the fail-closed distinction between unavailable and genuinely empty stores. Add an app-hosted storage-health assertion if that behavior is expected from the selected test configuration; do not weaken guards to obtain a green smoke.

## Research and cleanup

`research/IOS-R1-session_cont/research-ledger.json` records authoritative Apple URLs/access timestamps, relevant claims, version applicability, local SDK excerpts/hashes and explicit limitations. The native SDK pins -34018/-25300; online descriptions alone never supplied a runtime result.

Both native cycles' receipts record immediate and final `./gradlew --stop` exit 0, task-owned simulator shutdown/delete exit 0, UUID absent, no remaining UUID processes, temporary app/compiler output removed, no generated repository build output, and identical tracked source identity before/after. The original Xcode cycle also cleaned its DerivedData, framework/module outputs and owned simulator. This validator ran no Gradle, Xcode, simulator, device or signing commands and did not disturb the root-owned lane; only source/evidence reads and audit-file writes occurred. All pre-existing user files remain untouched.
