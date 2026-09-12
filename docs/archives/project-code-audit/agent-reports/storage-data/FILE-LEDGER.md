# Storage / content workstream — FILE LEDGER

Status: R = read in full. S = sampled (head / targeted grep). U = unread (see GAPS.md).

## Composition / Gradle

| File | Status | Notes |
|---|---|---|
| composeApp/.../di/StorageModule.kt | R | FileBackedSnapshotStore only |
| composeApp/.../di/ContentModule.kt | R | OfflineRemote bind |
| composeApp/.../di/AppModule.kt | R | allModules order |
| composeApp/.../storage/PlatformStorage.kt | R | expect + migrate helper |
| composeApp/.../storage/PlatformStorage.android.kt | R | |
| composeApp/.../storage/PlatformStorage.ios.kt | R | |
| composeApp/.../storage/PlatformStorage.desktop.kt | R | InMemory secure backing |
| shared/storage/build.gradle.kts | R | core + engine + json |
| shared/content/build.gradle.kts | R | ktor-client-core on commonMain |
| game-modes/whodunit/.../di/WhodunitDiModule.kt | R | bundled source + catalog |
| game-modes/mafia/.../di/MafiaDiModule.kt | R | definition only |
| shared/transport-p2p/.../P2pTransportModule.kt | R | expect |
| shared/transport-p2p/.../P2pTransportModule.android.kt | R | secureStorage = get() |
| shared/transport-p2p/.../P2pTransportModule.ios.kt | R | same |
| shared/transport-p2p/.../P2pTransportModule.desktop.kt | R | same; in-mem identity store |

## :shared:storage commonMain (every file)

| File | Status |
|---|---|
| snapshot/SnapshotStore.kt | R |
| snapshot/FileBackedSnapshotStore.kt | R |
| snapshot/SerializedSnapshotWriter.kt | R |
| snapshot/InMemorySnapshotStore.kt | R |
| settings/Settings.kt | R |
| settings/PersistentSettingsStore.kt | R |
| settings/InMemorySettingsStore.kt | R |
| secure/SecureStorage.kt | R |
| secure/PlatformKeyedSecureStorage.kt | R |

commonMain file count: 9. All read. No other source-set mains.

## :shared:storage tests

| File | Status |
|---|---|
| snapshot/FileBackedSnapshotStoreTest.kt | R |
| snapshot/SerializedSnapshotWriterTest.kt | R (full + tail) |
| settings/PersistentSettingsStoreTest.kt | R |
| secure/PlatformKeyedSecureStorageTest.kt | R |

## composeApp platform storage

| File | Status |
|---|---|
| androidMain/.../AndroidSnapshotFileSystem.kt | R |
| androidMain/.../AndroidSecureKeyValueBacking.kt | R |
| androidMain/.../AndroidGcmRecordPolicy.kt | R |
| androidMain/.../AndroidSettingsKeyValueBacking.kt | R |
| androidMain/AndroidManifest.xml | R |
| androidMain/res/xml/backup_rules.xml | R |
| androidMain/res/xml/data_extraction_rules.xml | R |
| iosMain/.../IosSnapshotFileSystem.kt | R |
| iosMain/.../IosSnapshotKeychain.kt | R |
| iosMain/.../IosSecureKeyValueBacking.kt | R |
| iosMain/.../IosSettingsKeyValueBacking.kt | R |
| desktopMain/.../DesktopSnapshotFileSystem.kt | R |
| desktopMain/.../DesktopSettingsKeyValueBacking.kt | R |
| commonTest/.../PlatformStorageMigrationTest.kt | R |
| iosTest/.../IosStorageSafetyTest.kt | R |

## :shared:content commonMain (every file)

| File | Status |
|---|---|
| datasource/DataSources.kt | R |
| datasource/OfflineRemoteCaseDataSource.kt | R |
| datasource/KtorRemoteCaseDataSource.kt | R |
| datasource/InMemoryCachedCaseDataSource.kt | R |
| repository/CaseRepository.kt | R |
| repository/DefaultCaseRepository.kt | R |
| schema/CaseEnvelope.kt | R |
| schema/CaseSummary.kt | R |
| validation/CaseValidator.kt | R |
| validation/DefaultCaseValidator.kt | R |
| validation/CaseSummaryValidator.kt | R |
| validation/ValidatedCase.kt | R |

commonMain file count: 12. All read.

## :shared:content tests

| File | Status |
|---|---|
| datasource/OfflineRemoteCaseDataSourceTest.kt | R |
| repository/DefaultCaseRepositoryTest.kt | R (full + remainder) |
| validation/DefaultCaseValidatorBoundaryTest.kt | S |
| validation/CaseSummaryValidatorTest.kt | S |

## Bundled Whodunit content

| File | Status |
|---|---|
| content/BundledWhodunitCatalog.kt | R |
| content/BundledWhodunitCases.kt | R |
| content/WhodunitContentIdentity.kt | R |
| content/WhodunitPayloadValidator.kt | S (header + contract) |
| files/cases/last-dinner.json | S (envelope) |
| files/cases/layla-halabi.json | S (envelope) |
| files/cases/jasmine-ring.json | S (envelope) |
| files/cases/khan-el-khalili.json | S (envelope) |
| files/cases/iskenderia-corniche.json | S (envelope) |
| files/cases/zamalek-ramadan.json | S (envelope) |
| files/cases/saidi-inheritance.json | S (envelope) |
| desktopTest/.../CasePickerDiscoveryTest.kt | S (catalog==disk) |

## Snapshot codecs / recovery (persistence view)

| File | Status |
|---|---|
| shared/engine/.../snapshot/SnapshotCodec.kt | R |
| shared/core/.../ids/Ids.kt | R |
| whodunit/.../snapshot/WhodunitSnapshotCodec.kt | R |
| whodunit/.../snapshot/WhodunitSnapshotFormat.kt | R |
| whodunit/.../ui/flow/WhodunitGameFlow.kt | S (load/write/identity) |
| mafia/.../snapshot/MafiaSnapshotCodec.kt | R |
| mafia/.../snapshot/MafiaSnapshotRecovery.kt | R |
| mafia/.../ui/flow/passandplay/MafiaGameFlow.kt | S (writer + load) |

## Credential store (storage view only)

| File | Status |
|---|---|
| transport-p2p/.../ResumableCredentialStore.kt | R |
| transport-p2p/.../ResumableCredentialStoreTest.kt | U (existence grepped) |

## Shell recovery (routing only, not UI)

| File | Status |
|---|---|
| composeApp/.../LocalResumeRouter.kt | R |
| composeApp/.../shell/home/HomeRecoveryAvailability.kt | R |

## Out of this workstream (not read as product)

- Game reducers, Mafia/Whodunit UI screens
- P2pKitRoomTransport body (4589) beyond credential bind
- Whodunit payload validator remainder / case JSON bodies
- Snapshot golden JSON under desktopTest/resources
