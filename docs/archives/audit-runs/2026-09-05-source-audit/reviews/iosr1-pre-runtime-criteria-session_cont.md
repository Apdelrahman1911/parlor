# IOS-R1: pre-runtime-result interpretation criteria

Reviewer: `/root/session_cont`. Prepared 2026-09-05 after cycle01's build-only failure and **before receiving any app-host production/native observation**. This fixes interpretation criteria before seeing a successful retry's result; it is not a runtime finding or approval.

Baseline: `main`, commit `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`. Original candidate finder: `/root`; independent source/result validator: `/root/session_cont`. I authored the additive fixture; `/root/mafia_cont` independently approved fixture safety. Root owns execution; `/root/whodunit_cont` independently reviews runner/artifact/cleanup evidence. No self-approval of fixture safety is claimed here.

## 1. Establish evidence validity first

Require source/input hash bindings, allowlisted copied-wrapper differences, actual generated-header/callback verification, explicit unsigned Debug simulator prerequisites, and a new task-owned simulator UUID created before installation/launch. Require recorded commands/exits, raw XCTest nodes/counts, sanitized primitive report and its exact app-container origin. Recheck that original controller/DI/lifecycle/privacy paths remain unchanged. No existing simulator, user record, private credentials or blanket simulator/process cleanup is acceptable.

A successful framework compile is not an app run. A successful copied XCTest is Home/foreground/no-native-alert smoke plus receipt delivery only; its assertions explicitly do not assert recovery health. `harness_status=observation_complete` also does not mean both production sources succeeded. Build/test timeouts, failed selectors, missing result, aborted harness and production failure must remain separate.

For `harness_status=aborted`, preserve any valid `completed_production_observation` independently from the incomplete optional native corroboration. Otherwise no source outcome may be inferred from the reason string or passing XCTest.

## 2. Interpret the actual production rerun

The fixture reruns `loadHomeRecoveryAvailability` on the existing Koin-bound stores and transport; it does not intercept the initial `App.kt:120–143` produceState call. Both inputs pass through unchanged, and the combined value comes from the real resolver (`HomeRecoveryAvailability.kt:88–146`). The empty-data prerequisites imply:

| Local observation | Multiplayer observation | Required combined unavailable |
|---|---|---|
| `success_empty`, count0 | `success_null` | `false` |
| `failure` | `success_null` | `true` |
| `success_empty`, count0 | `failure` | `true` |
| `failure` | `failure` | `true` |

Combined local count must remain0 and multiplayer presence false. A contradictory result first invalidates assumptions/binding/fixture evidence for investigation; it is not an immediately approved production defect.

- Local failure attributes this **rerun** to the real local snapshot inventory path, not a numeric Foundation error. The file store sanitizes exceptions (`FileBackedSnapshotStore.kt:118–149`); the fixture returns category only.
- Multiplayer `secure_storage_unavailable` attributes this **rerun** to the credential pipeline, not necessarily to a particular SecItemCopyMatching status. `ResumableCredentialStore.kt:318–340` maps storage-get failure to Unavailable and malformed/oversize bytes to Corrupted; `P2pKitRoomTransport.kt:625–655` maps both to SecureStorageUnavailable and can also map expiry-invalidation failure there. Neither malformed-record failure nor this category alone proves a deletion or native entitlement failure.
- Native absence is deliberately successful: `IosSecureKeyValueBacking.kt:89–112` maps errSecItemNotFound to null; the credential store's absent read returns successful null. A failure must not be relabeled empty merely because no game was expected.
- Fresh simulator/no game or room actions is an essential prerequisite, not something the late observer proves. `IosSnapshotFileSystem.kt:150–166` can migrate/read legacy data before returning; credential loading can decode/invalidate a record before the fixture sees the outcome. The abort guards do not make an existing container safe to probe.

## 3. Interpret optional subsequent same-app native observation

It is requested only after successful empty local inventory and a production multiplayer SecureStorageUnavailable result. The query uses the same service/account/generic-password/non-synchronizing/return-data/match-one attributes as production, but is a **subsequent independent call**. Its `original_production_status=false` must be retained in every summary.

- OSStatus `-34018` with null result establishes errSecMissingEntitlement for **that later same-app query**. It corroborates an unsigned-environment explanation and source-consistent warning behavior, not the original Home call's discarded numeric status or a correctly signed device defect.
- `-25300` with null result establishes absence for the later query only. It does not erase the earlier pipeline failure; stage/timing differences remain unresolved.
- No native observation means no numeric attribution. A failure/timeout during corroboration cannot invalidate an already-completed production observation, but leaves the numeric question blocked.
- Any nonnull returned value aborts without copying contents; that violates the expected empty-query prerequisite. Status0 with null result or other statuses require separate applicability/semantic investigation, not a claim of healthy credential storage.

Apple's errSecMissingEntitlement/default access-group documentation is already retained with URLs/access dates in `research/IOS-R1-session_cont/`; reread references confirm absence of an explicit sharing group is not itself a defect and entitlement diagnostics should use the hardware target. Do not add an entitlement, supply signing credentials, or suppress a failure warning based on unsigned-simulator output.

## 4. Preserve initial versus later observations

The original-warning XCTest attachment and screenshot are separate from the rerun result. Warning present before rerun proves only that original Home projected unavailability; it does not separate original sources/native causes. No warning found within10s is non-observation (could still be loading, offscreen or inaccessible), not proof of successful original storage. Initial warning plus healthy rerun is a temporal difference, not an application fix. A later numerical status must never be assigned retroactively to the initial invocation or even the production rerun call.

## 5. Classification and remaining limits

If the full valid evidence shows credential-pipeline failure, source-consistent combined warning and later same-app missing-entitlement status under the deliberately unsigned prerequisites, classify the observed presentation as intended fail-closed handling with a test/environment evidence gap; disclose the residual original-call numeric limitation. Do not count a confirmed application defect without a reachable source failure or reproducible behavior violating the applicable contract. If attribution still cannot be made reliably, retain IOS-R1 as UNCONFIRMED — BLOCKED, outside confirmed-code counts. Any materially new source-level defect requires separate independent validation.

No outcome here establishes signed-device storage availability/durability, migration behavior with records, physical LAN/rejoin correctness, Store signing/identity correctness, release readiness or absence of other iOS defects. The original xcode-ui-01 and native01/native02 evidence remains historical and unchanged; cycle01's codesign build failure is a harness result only (`reviews/iosr1-codesign-session_cont.md`). Actual retry results require an additive validation supplement rather than rewriting earlier evidence.

## 6. Cleanup/review obligations

Require root's immediate Gradle-stop receipts, task-owned output/DerivedData/container removal, exact owned-process shutdown, source preservation and independent cleanup review after each cycle. No device/build command was run by this reviewer. Only bounded retained source/research/review evidence was written; no generated build output requires this reviewer's cleanup.
