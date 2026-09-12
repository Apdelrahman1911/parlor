# Storage / content workstream — NOTES

CODE-ONLY. Reconstructed from Kotlin, Gradle, Android XML, and bundled JSON.
No `*.md` outside `project-code-audit/` was read. Production was not modified.

## Wiring (composition root)

`composeApp/.../di/AppModule.kt` `allModules`:

1. `coreModule` — strict `Json` (`ignoreUnknownKeys=false`, `isLenient=false`)
2. `whodunitModule` — `WhodunitDefinition`, `PayloadValidator<WhodunitCase>`,
   `BundledFallbackCaseDataSource` (`BundledWhodunitCases` + catalog ids)
3. `mafiaModule` — `MafiaDefinition` only (no cases, no bundled source)
4. `contentModule` — catalog + `OfflineRemoteCaseDataSource` + in-memory cache
5. `storageModule` — `FileBackedSnapshotStore(fileSystem, json)`
6. `platformStorageModule()` — FS + settings + `SecureStorage`
7. `p2pBootstrapModules()` — `P2pKitRoomTransport(secureStorage = get())`

There is no Room / SQLDelight / DataStore. Persistence is files +
SharedPreferences / NSUserDefaults / Java Preferences + Keystore / Keychain.

## Three durable stores (must stay distinct)

| Store | Interface | Production bind | Secrets? |
|---|---|---|---|
| Snapshots | `SnapshotStore` | `FileBackedSnapshotStore` + platform AEAD FS | Yes (full host state) |
| Settings | `SettingsStore` | `PersistentSettingsStore` + platform prefs | No |
| Credentials | `SecureStorage` | `PlatformKeyedSecureStorage` + platform backing | Yes (resume secret) |

`ResumableCredentialStore` is the only transport type that talks to
`SecureStorage`. Snapshot files use a **separate** platform key (not this KV).

## Who writes snapshots

- **Whodunit local** (`WhodunitGameFlow.SessionDrivenFlow`):
  `SerializedSnapshotWriter` on every canonical-state emission. Deletes on
  `PostGame`. Metadata: `playMode` (PaP only), `caseVersion`, `caseDigest`.
  Multi-device does **not** use this writer.
- **Mafia local PaP** (`MafiaGameFlow.SessionDrivenFlow`): same writer.
  Metadata: `playMode=PassAndPlay`. Deletes on `PostGame`.
- **Mafia multi-device**: no `SerializedSnapshotWriter`. No local snapshot.
- Desktop/Android/iOS share the same `SnapshotStore` singleton.

## Content pipeline (production)

```
UI / game flow
  -> CaseRepository = DefaultCaseRepository
       remote  = OfflineRemoteCaseDataSource   // always Unreachable
       cache   = InMemoryCachedCaseDataSource  // process RAM; never filled in prod
       bundled = BundledWhodunitCases(catalog)
       validator = DefaultCaseValidator + WhodunitPayloadValidator
```

`KtorRemoteCaseDataSource` is compiled into `:shared:content` commonMain and
tested with `MockEngine`, but **not** bound. `refresh()` is test-only.

## Catalog vs disk

`bundledWhodunitCaseIds` (7) == `composeResources/files/cases/*.json` (7).
Each file's `caseId` equals its filename stem. `gameId` is `whodunit`.
Contract test: `CasePickerDiscoveryTest.production_catalog_exactly_matches_packaged_case_resources`.

## Overlap with other workstreams

- AR-003 = same fact as ST-002 (Ktor on shipping classpath).
- AR-004 = in-memory fakes in commonMain (`InMemorySnapshotStore`,
  `InMemorySettingsStore`, `InMemorySecureKeyValueBacking`). Desktop
  **intentionally** binds the in-memory secure backing. Not re-filed.
- Session/network owns P2P protocol; this stream only followed the
  credential store + `secureStorage = get()` bind.
