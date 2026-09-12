# IOS-R1 — independent app-host result adjudication (cycle02)

## Classification and scope

**Primary classification: TEST/EVIDENCE GAP. Severity: null; no application-defect severity assigned.** The captured unavailable-state presentation is **INTENDED BEHAVIOR**: a failed recovery lookup must not masquerade as a successfully empty store. IOS-R1 is not a confirmed application defect, is not a fix, and must remain outside confirmed-code-defect counts. This supplement changes the earlier blocked adjudication only as described below; the original candidate, validation, failed attempts and receipts remain historical evidence.

Original finder and execution owner: `/root`. Independent application source/result validator: `/root/session_cont`. I authored the additive Kotlin/Swift-template fixture; `/root/mafia_cont` independently reviewed fixture safety. `/root/whodunit_cont` independently reviewed runner/execution/cleanup evidence. This is not self-approval of fixture safety and is not a repository-wide readiness verdict.

Reviewed repository: `/Users/abdelrahman/Projects/parlor`, branch `main`, commit `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`. Current tracked/index status is empty. The existing untracked `AGENTS.md`, `audit-runs/`, `design/`, `docs/PARLOR_PROJECT_HANDOFF.md` and `project-code-audit/` are preserved. This diagnostic deliberately adds two audit-only iosMain files through an invocation-only init script and patches three files in an isolated wrapper copy; it is not a byte-identical shipping build. Exact input hashes, copied-file hashes and diff are retained. Companion `.sources.json` and `coverage/reviews-iosr1-apphost02-session_cont.jsonl` identify actual reread ranges and limitations; no unread remainder is promoted to complete review.

`A/` below means `/Users/abdelrahman/Projects/parlor/audit-runs/2026-09-05-source-audit/`.

## Evidence validity independently checked

The interpretation was preregistered in `A/reviews/iosr1-pre-runtime-criteria-session_cont.md` before cycle02 produced an app-host observation. I reopened the actual fixture, source path, complete copied diff, generated callback export, raw report, XCTest nodes/counts, selected build-log ranges, and relevant authoritative documentation rather than accepting the runner's PASS marker.

`A/evidence/iosr1-apphost02-independent-session_cont/binding-and-result-receipt.json` records **114 passing read-only consistency checks, not 114 application tests**. Checks include all 12 execution-input hashes, all 43 original plus two additive source pins, the exact 18-file wrapper-copy inventory, and independent in-memory reconstruction of all three copied changed files from the retained unified diff. The reconstructed hashes match the copy manifest; no production or copied file was written by that check. The actual generated header's `IOSR1AppHostProbe.shared`, `cancel()` and `start(onJson:)` declarations use a **void callback receiving NSString*** (`ComposeApp.generated.h:153–166`), and the 14-line export is byte-exact. Original MainViewController export remains present at 614–629. The source-binding marker appears in both Gradle build logs.

The runner first copies the bounded result directly from the new simulator's actual Parlor app container (`run_iosr1_apphost_cycle.py:499–512`) and then validates it (528–535). Its validator permits different guarded source outcomes and native status integers; it does not manufacture or require -34018. The fixture obtains existing real Koin bindings and passes observed source results unchanged into the actual Home resolver. It neither intercepts nor replaces the initial Home load and does not publish its diagnostic rerun into Home UI state.

Actual run: `2026-09-05T14:12:49.920567Z`–`14:15:46.739122Z`, Xcode 26.5 / 17F42, iPhone 17 Pro ARM64 simulator iOS 26.5 / 23F77, new UUID `441CC892-68AB-4C6A-8B0C-739C6B21B06B`. App identity is Debug `com.parlor.app.debug`, version 1.0.0 (1), deployment target 16. Both signing-allowed/required flags are NO; no real signing identity, team, credential or profile was supplied. The erroneous empty `EXPANDED_CODE_SIGN_IDENTITY` environment entry from cycle01 is omitted. Cycle01's codesign build failure remains an audit-harness failure, not a Parlor crash or a storage result (`reviews/iosr1-codesign-session_cont.md`).

Framework compilation, nested embed task and Xcode each exited 0. Actual XCTest is exactly **1 passed / 0 failed / 0 skipped / 0 expected failures**, method `IOSAppLaunchUITests/testAuditProductionRecoverySourceAttribution()`, duration 11.9656010866 seconds. It asserts foreground Home/brand/no native Alert and delivery of a diagnostic receipt, **not healthy recovery storage**. Passing compilation, this XCTest and `harness_status=observation_complete` must not be relabeled storage-health success.

**Artifact provenance limit:** the execution receipt records the `Parlor` launcher SHA256 `b3e604caacfde2add2c5c5782485c3574a8b8180b12e12a9cbd375c4a25aafa4` (40,344 bytes) and embedded Compose framework `6e3a5506c096bc2d1dc6e45f8ef9aefaf6dfb8ec99677b7ef3330865e4262016` (82,171,688 bytes). These are retained execution-time hashes, not independently rehashable binaries after mandatory cleanup. There is no full bundle/component manifest, including no retained `Parlor.debug.dylib` hash. Do not claim complete bundle attestation, release provenance, or signed-artifact validation.

## Three observations — do not conflate them

| Observation | What was actually established | What was not established |
|---|---|---|
| Original Home load, before trigger | Attachment contains `original_home_warning_present=true`; screenshot visibly shows the ordinary inline unavailable card while the additive overlay is **idle**. Xcode trace records both attachments before trigger tap. | Original local/multiplayer per-source results and original native error/status were not intercepted. |
| Diagnostic production rerun, same app/Koin graph | Local snapshot inventory: `success_empty`, count 0. Multiplayer: `failure`, category `secure_storage_unavailable`. Actual combined Home resolver: unavailable true, local count 0, no multiplayer resume. | The production pipeline sanitizes failures; this does not capture the numerical Keychain status of that production call or prove every other storage operation healthy. |
| Subsequent equivalent native read, same app process | The direct `SecItemCopyMatching` call returns **-34018**, null result, matching the compiled `errSecMissingEntitlement` constant; `errSecItemNotFound` is -25300. JSON explicitly says `origin=subsequent_same_app_equivalent_read` and `original_production_status=false`. | This later query's number cannot be assigned retroactively to either original Home or the production rerun. No correctly signed physical-device or Store result follows. |

Raw report is `A/evidence/iosr1-apphost-02/probe-result.json` (568 bytes). Its source outcomes match the preregistered “local empty + credential pipeline failure + later -34018” branch exactly. The unavailable flag is the required result for those source values, not a state-resolution bug.

The screenshot is visual evidence of Home only. The audit-copy overlay overlaps part of the bottom Settings label; that additive diagnostic UI does not exist in production and is not a production layout finding. The observed card is not a native Alert. Xcode test-runner accessibility/debugger diagnostics do not establish an app exception, launch crash or operating-system termination; the test explicitly terminates the app after receiving its receipt.

## Reachable source path and expected versus actual behavior

All locations below use one-based source lines at the pinned commit; per-file SHA256 values and absolute paths are also in the companion source ledger.

1. `/Users/abdelrahman/Projects/parlor/composeApp/src/iosMain/kotlin/com/parlor/app/MainViewController.kt:18–32` invokes App with original `allModules`; `composeApp/src/commonMain/kotlin/com/parlor/app/di/AppModule.kt:67–74` and `p2p/P2pBootstrap.kt:13` include production platform/P2P bindings. `di/StorageModule.kt:16–23` binds the real singleton `FileBackedSnapshotStore`. `/Users/abdelrahman/Projects/parlor/composeApp/src/iosMain/kotlin/com/parlor/app/storage/PlatformStorage.ios.kt:13–19` binds the real iOS filesystem, secure backing and `PlatformKeyedSecureStorage`. `/Users/abdelrahman/Projects/parlor/shared/transport-p2p/src/iosMain/kotlin/com/parlor/transport/p2p/P2pTransportModule.ios.kt:51–72` supplies that secure storage to `P2pKitRoomTransport`; the constructor's unavailable default is **not** the production binding.
2. `/Users/abdelrahman/Projects/parlor/composeApp/src/commonMain/kotlin/com/parlor/app/App.kt:73–77,120–143,220–241` obtains those singletons, starts original Home loading and passes the resulting flag to Home. `/Users/abdelrahman/Projects/parlor/composeApp/src/commonMain/kotlin/com/parlor/app/shell/home/HomeRecoveryAvailability.kt:88–146` concurrently starts both probes, awaits both and ORs their unavailable flags. Both-success empty/null means false; any failed source means true. `/Users/abdelrahman/Projects/parlor/composeApp/src/commonMain/kotlin/com/parlor/app/shell/home/HomeScreen.kt:109–114,253–279` gives Loading precedence, otherwise displays the polite live-region unavailable card and retry action. No raw error is exposed.
3. Local: `/Users/abdelrahman/Projects/parlor/shared/storage/src/commonMain/kotlin/com/parlor/storage/snapshot/FileBackedSnapshotStore.kt:118–149` safely lists/sanitizes failures. `/Users/abdelrahman/Projects/parlor/composeApp/src/iosMain/kotlin/com/parlor/app/storage/IosSnapshotFileSystem.kt:74–123,150–166,219–228` creates the protected Application Support directory, guards absent legacy directories and accepts an empty returned list. The real rerun's successful empty inventory establishes this inventory operation succeeded. It does not exercise encrypted snapshot write/read/resume, prove original first-scan success, or prove migration behavior with records.
4. Credential pipeline: `/Users/abdelrahman/Projects/parlor/shared/transport-p2p/src/commonMain/kotlin/com/parlor/transport/p2p/P2pKitRoomTransport.kt:175,625–655` calls its credential store and preserves failure as SecureStorageUnavailable. `/Users/abdelrahman/Projects/parlor/shared/transport-p2p/src/commonMain/kotlin/com/parlor/transport/p2p/ResumableCredentialStore.kt:146–151,318–340` serializes reads: failed secure get becomes Unavailable; absent value is successful null; malformed/oversized records become Corrupted; returned bytes are zeroed. Transport also maps corruption and expiry-invalidation failure into SecureStorageUnavailable. Therefore the category alone is deliberately not a numeric native diagnosis or evidence that a record was deleted.
5. Native: `/Users/abdelrahman/Projects/parlor/shared/storage/src/commonMain/kotlin/com/parlor/storage/secure/PlatformKeyedSecureStorage.kt:32–39` sanitizes backing exceptions while preserving cancellation. `/Users/abdelrahman/Projects/parlor/composeApp/src/iosMain/kotlin/com/parlor/app/storage/IosSecureKeyValueBacking.kt:74–112,145–179,230–243` implements the actual read. **errSecItemNotFound returns null**, success requires CFData, and other statuses produce a sanitized failure without retaining the numeric status. The query attributes really match the later audit query: generic password, service `com.parlor.app.resumable-session.v1`, account `p2p-resumable-session-v1` (`ResumableCredentialStore.kt:367`), synchronizable false, return-data true and match-limit one. No explicit access group is requested.

Source and captured rerun agree with the executable failure-distinction contract. The later actual missing-entitlement result corroborates an unsigned-environment explanation. The exact original Home failure remains unobserved; no contradictory production branch or reproducible violation of the applicable contract has been established. **No application root-cause defect or production fix is approved.**

## Counter-evidence and privacy checks

- A nonexistent snapshot directory is not enough to infer failure: explicit creation/legacy guards exist, the real production rerun returns an empty successful inventory, and the earlier separate native test also returned an empty successful list. This does not retroactively attribute the original invocation.
- No prior room/record is not proof of a healthy credential store: absence is only successful when the native read returns item-not-found or a valid null result through the contract. Reinterpreting other failures as empty would weaken the intended safety boundary.
- Absence of an explicit `keychain-access-groups` entitlement is not a defect by itself. Apple documents the app identifier/default access groups and explicitly recommends hardware-target entitlement diagnosis. No signing material was inspected; no missing Store entitlement is inferred from unsigned simulator output.
- `/Users/abdelrahman/Projects/parlor/composeApp/src/commonTest/kotlin/com/parlor/app/shell/home/HomeRecoveryAvailabilityTest.kt:1–230` was reread, including assertions for failed/partial/unsupported sources and the async empty/null case at 133–161. These fake/in-memory checks support resolver expectations, not actual UIKit-hosted Keychain health. This validator did not execute that test class during this closeout.
- `/Users/abdelrahman/Projects/parlor/iosApp/iosAppUITests/IOSAppLaunchUITests.swift:1–32` is intentionally narrow launch smoke, not false evidence that every store works. The copied test preserves those checks and adds receipt delivery; it does not assert the observed storage error away.
- The diagnostic reads only the task-owned fresh simulator. Concrete-type checks and late nonempty-result aborts are **not** a privacy sandbox for an existing container: the real filesystem can migrate legacy data before returning, and credential loading can decode/invalidate a record before observation. New UUID and no room/game actions are essential prerequisites. The optional native read never creates an item and aborts before copying any returned data. Output is bounded allowlisted primitives, not exceptions, native record bytes, player metadata, keys or credentials.
- Original Swift/Compose/Koin initialization, platform lifecycle callbacks and privacy cover are unchanged in the copy. The audit scope is cancelled separately and never stops Koin or an application-owned transport scope. The transport constructor starts its lifecycle consumer, not a LAN room or credential creation (`P2pKitRoomTransport.kt:191–210`). No LAN join/host interaction or physical network test occurred.

## Authoritative research

Retained URLs, access timestamps and hashes are in `A/research/IOS-R1-session_cont/research-ledger.json`; relevant complete extracted pages were reopened:

- https://developer.apple.com/documentation/security/errsecmissingentitlement — accessed 2026-09-05T10:47:46Z: required entitlement missing; hardware-target recommendation limits simulator inference.
- https://developer.apple.com/documentation/security/errsecitemnotfound — accessed 2026-09-05T10:47:49Z: item cannot be found, distinct from unavailability.
- https://developer.apple.com/documentation/security/ksecattraccessgroup — accessed 2026-09-05T10:47:51Z: default app access groups; own-app storage does not inherently require an explicit sharing group; CopyMatching searches allowed groups by default.

These API descriptions apply to the actual Security calls but do not prove unsigned simulators always fail. The compiled constants and actual subsequent-query result supply this run's numerical evidence. Kotlin 2.4.10 source/version-specific empty-signing-environment investigation remains in the separate codesign review; it does not supply a runtime storage diagnosis.

## Verification disposition / proposed gate wording

These are narrowly scoped proposed gate dispositions, not additions to the canonical ledger by this reviewer.

| Gate | Disposition | Evidence / limit |
|---|---|---|
| App-host diagnostic execution and receipt delivery | **PASS** | Actual one passing, unskipped copied XCTest; complete raw receipt from owned Parlor container. Health is not asserted. |
| Source/result consistency and Home fail-closed mapping | **PASS** | Reopened sources, exact input/diff/header checks and captured real rerun agree. This is not full bundle attestation. |
| Both recovery sources healthy on this unsigned simulator | **FAIL (observed credential-source unavailability)** | Local inventory succeeds; multiplayer lookup fails. This observed gate failure is a test/environment evidence gap, not a confirmed application defect. |
| Numerical attribution of initial Home or production rerun | **BLOCKED** | Production status was sanitized, original call not intercepted; later -34018 is a distinct read. |
| Correctly signed physical-device recovery/storage/durability | **BLOCKED** | Not executed; requires authorized real signing/device environment and synthetic records. |
| Store qualification / physical LAN and rejoin | **BLOCKED in this diagnostic** | No Store-qualified signed candidate, physical LAN, credential transaction or complete game/resume scenario exercised. Do not override other repository gates with this result. |
| Cycle02 known owned processes/outputs cleanup | **PASS after additive correction** | Root's original receipt plus secondary FIFO cleanup and independent post-check; no global-temporary-file guarantee. |

Proposed canonical text:

> **IOS-R1 — TEST/EVIDENCE GAP (severity null).** In the fresh, unsigned iOS 26.5 app-host diagnostic, the real Koin-bound Home recovery rerun returns local success-empty, multiplayer SecureStorageUnavailable and a correct unavailable projection. A subsequent equivalent Keychain read in the same app returns -34018/errSecMissingEntitlement; that numerical result is not the original Home or production-rerun status. Initial Home warning remains a separately observed inline card. Presentation is intended fail-closed handling; no application defect or fix is established. Signed-device storage and original-call numeric attribution remain unverified. One copied launch/receipt XCTest passed; it did not assert storage health.

## Cleanup and source preservation

Original cycle02 receipt alone was insufficient: its initial cleanup omitted one Apple-owned external `ibtoold-67921` FIFO directory. That omission was detected independently and preserved in `A/evidence/iosr1-apphost-02-cleanup-independent-whodunit-cont.json`; no historical receipt or runtime result was rewritten. Root's additive `A/evidence/iosr1-apphost-02-secondary-cleanup/receipt.json` records exact owned FIFO metadata, lineage/no-holder checks, removal of only those FIFOs and empty directories, and no additional build or process termination.

`A/evidence/iosr1-apphost-02-post-secondary-cleanup-independent-whodunit-cont.json` independently confirms at 2026-09-05T14:36:10.894788Z that all **73 attested PIDs**, all **17 potential generated-output paths**, the owned simulator UUID directory, primary temporary path aliases and recorded secondary FIFO root are absent; global dependency/wrapper caches remain; tracked status is unchanged. Scope is attested paths/processes, not a global temp scan. Earlier cycles' exact recorded primary paths are also checked; no claim is made about unrecorded secondary Apple paths.

The original build receipts show immediate `./gradlew --stop` exit 0 after the framework and Xcode tasks; the copied embedded phase separately records `build_exit=0` / `stop_exit=0`; the final stop also exits 0. Framework outputs were retained only for the immediately following Swift link/runtime inspection, then removed with the task-owned DerivedData and wrapper/container. This validator ran no Gradle, Xcode, simulator, compiler, signing or process-control command and owns no generated build output. Only bounded source/evidence reads, in-memory checks and additive audit reports occurred.

## Remaining verification / no unauthorized remedy

Do not suppress the unavailable card, relabel every Keychain failure as empty, add an entitlement blindly, or change signing configuration based on this result. If separately authorized, verify the real app's empty and synthetic-record recovery paths in a correctly signed supported device configuration, including storage durability/lock-unlock and app restart. Keep source outcomes and privacy-safe native stage/status distinct, and test the real boundary rather than only fake stores. If exact original-call attribution is still required, it needs an independently reviewed invocation-specific observer; this later diagnostic cannot reconstruct a discarded status.

No production change is proposed as an approved defect fix, no GitHub issue is changed, and no release readiness is inferred. The remaining gap is honest platform/storage evidence, with the precise unsigned-simulator failure now captured at production-source category level.

## Compact evidence hash index

The companion source/evidence ledger records all referenced review items; key immutable receipts are:

| Relative to A/ | SHA256 |
|---|---|
| `reviews/iosr1-pre-runtime-criteria-session_cont.md` | `b6dd7acfd4ef803118a7dbb55a091e3ae3109cf74628ea1f1fdeb11e2e7f5f15` |
| `evidence/iosr1-apphost-02/probe-result.json` | `514aebf2e896acb6ddd6e13a86709f9fce8838a98ef08cd349433a316ee34a4e` |
| `evidence/iosr1-apphost-02/receipt.json` | `2dd7b901ddc2f33dda5fcb91c108f97156fb94f2d209e565754858d6032ec332` |
| `evidence/iosr1-apphost-02/input-manifest.json` | `a1db86512aac817c45ad77835f28ab00241136098a2a2c6c4f2f3cab19cf6169` |
| `evidence/iosr1-apphost-02/copied-wrapper.diff` | `a832621739b2813b780f1ad71965b7b7d9a65d57d7396bfcac85bc105e006639` |
| `evidence/iosr1-apphost-02/ComposeApp.generated.h` | `47bf42c606417baf1e7fddac4138c01874448df0079f815a349b2b707163774e` |
| `evidence/iosr1-apphost-02/xcresult-summary.json` | `55ce269c1ddf53526a13fae37eaec6e4b9cb24a7bcecb51642ba39da2db760ab` |
| `evidence/iosr1-apphost-02/xcresult-tests.json` | `7cd9826f15b1739990916702a4cdfafab6eb07ef1149ad17452f2f553ba78668` |
| `evidence/iosr1-apphost02-independent-session_cont/binding-and-result-receipt.json` | `7689b5ea6e037a29e74392e081a63c379cb13cfebcb83ff24f4e01171675c10a` |
| `evidence/iosr1-apphost-02-secondary-cleanup/receipt.json` | `86f62321ef1c39952b6f9a9aeeae54b7d273e810335622655540f2991ba8359d` |
| `evidence/iosr1-apphost-02-post-secondary-cleanup-independent-whodunit-cont.json` | `389f5a4e7ede107644fa1812c706cf70a2c7e4f8200763c8900340e610f4aea4` |
