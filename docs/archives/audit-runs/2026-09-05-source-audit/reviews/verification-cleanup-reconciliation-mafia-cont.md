# Verification and cleanup reconciliation

Reviewer: `/root/mafia_cont`. Audit-only reconciliation on 2026-09-05; no build, app, simulator, signing, Store operation, process termination, or application change by this reviewer.

## Scope and evidence method

Repository `/Users/abdelrahman/Projects/parlor`, branch `main`, commit `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`. Tracked status remained empty. Pre-existing untracked work was preserved.

Reopened **26 original receipt files / 73,714 bytes completely**, including object- and array-root JSON. This covers **15 primary Gradle cycles**, seven additional native/Xcode/Python observations, one simulator-lifecycle companion, two binary-inspection execution/cleanup records, and one research receipt. The companion is not another test execution.

The machine index is `evidence/verification-cleanup-reconciliation-mafia-cont.json`. It records receipt hashes, actual commands/times/exit codes, per-cycle scope, counts, cleanup fields, relevant log hashes, source-wiring reads, and remaining limitations. Source/receipt reading ranges were appended to `coverage/reviews-mafia_cont.jsonl`.

Independently parsed **758 retained JUnit XML files** and compared every available test-receipt JSON case (name, class, failure/skip flags and suite counts) against its XML: **no mismatch**. One XML is the incomplete first storage collection; 757 have corresponding JSON records. This is mechanical evidence reconciliation, not a claim to reread every test implementation in this assignment. Failure/skip details, task wiring, relevant log intervals and all 25 stop logs were separately inspected.

Counts below are **per execution**, not distinct tests across retries. `tests` includes skipped descriptors. Passing a diagnostic witness does not fix the witnessed defect, and failing a deliberately unmet regression assertion is not an ordinary baseline-suite failure.

## Ordinary repository gates

All times below are UTC on 2026-09-05. Full flags are preserved in each cycle receipt. Gradle cycles used the checked-in wrapper, JDK21, strict dependency verification, one worker, no parallel build/cache/configuration cache, in-process Kotlin compilation, and blank/removed release-signing inputs.

| Cycle / command | Time | Result and actual coverage |
|---|---|---|
| `focused-storage-01`: graph + `:shared:storage:desktopTest` | 06:42:30–06:43:30 | **PASS command / incomplete report collection.** Host XML parser failed. Only one 11-case passing XML survived; the full first-run count is not established. |
| `focused-storage-02`: storage Desktop tests | 06:47:11–06:47:30 | **PASS**, 31 executed cases in four suites. |
| `baseline-desktop-static-01`: `productionDesktopCheck staticAnalysis :composeApp:verifyGameShellDispatch` + graph | 07:46:17–07:47:39 | **PASS**, 1,108 discovered / 1,107 passing / one skipped; 14 Detekt XML reports, zero error entries. |
| `inspect-transport-tests-02`: method inspection + transport Desktop tests | 08:13:35–08:14:22 | **PASS inspection/discovered suite**, 212 discovered / 211 passing / one skipped. Separately exposes ROOT-T3, not complete authored-test execution. |
| `production-check-01`: `productionCheck --continue` | 08:36:38–08:43:15 | **PASS**: Desktop 1,108 with one skip; app Android Debug and Release unit tests 76 each; 130 Python release-validator tests. 43 Detekt XML reports have zero error entries. Release lint has **32 accepted warnings**, not zero warnings. R8/unsigned bundle, manifest, identity and shell gates executed. |
| `apple-check-02`: `productionAppleCheck --continue`, 6g heap | 09:42:19–09:51:58 | **PASS analysis/linkage**, zero runtime tests. Four Detekt XML reports have zero error entries. Three release frameworks linked: iOS device arm64, simulator arm64 and x64. |
| `native-alltests-01`: `allTests productionIosSimulatorRuntimeTests --continue` | 10:02:03–10:09:38 | **PASS executed matrix**: 2,285 discovered / 2,284 passing / one skipped. Arm64 simulator 393; Desktop 1,108 with one skip; Android unit 392 per variant. Nonexecuted tasks are detailed below. |
| `xcode-ui-01`: unsigned Debug `xcodebuild … test` | 10:24:35–10:26:13 | **PASS narrow app smoke**, one English XCTest. Foreground, Home-brand existence, no UIKit alert during a short observation window, then foreground again. EN/AR supplemental launches and screenshots are observations, not additional automated assertions. |

The 32 lint warnings are `OldTargetApi` ×1, `AndroidGradlePluginVersion` ×4, `GradleDependency` ×3 and `NewerVersionAvailable` ×24. The checked-in warning validator passed; acceptance is not an assertion that dependencies are latest or every warning harmless in every future release.

`productionCheck` does **not** run Android managed-device instrumentation, real signing, Apple checks or physical LAN. Its Android unit tasks select the app, whereas root `allTests` later includes library unit tasks. Root `build.gradle.kts:37–70,128–148,185–202` was reopened to verify this distinction.

Apple runs used installed **Xcode26.5 / 17F42**, not the policy-qualified **26.3 / 17C529** in `config/release-policy.json:55–60`. Apple linkage success is neither signed-release evidence nor policy-qualified Store validation.

## Skipped, absent and undiscovered tests

### Actual `allTests` matrix

| Module | Desktop descriptors | iOS arm64 executed | Android unit, each variant |
|---|---:|---:|---:|
| `composeApp` | 95 | 77 | 76 |
| `game-modes/mafia` | 232 | 0 | 0 |
| `game-modes/whodunit` | 288 | 9 | 9 |
| `shared/content` | 24 | 24 | 24 |
| `shared/core` | 12 | 12 | 12 |
| `shared/design-system` | 30 | 13 | 13 |
| `shared/engine` | 4 | 0 | 0 |
| `shared/engine-testing` | 6 | 6 | 6 |
| `shared/networking` | 34 | 34 | 34 |
| `shared/networking-testing` | 4 | 4 | 4 |
| `shared/session` | 136 | 133 | 133 |
| `shared/storage` | 31 | 31 | 31 |
| `shared/transport-p2p` | 212, including one skip | 50 | 50 |

There are **19 selected nonexecuted test tasks** in the native cycle:

- **13 `iosX64Test` tasks** are host-disabled on this arm64 Gradle host. The ownership init script records `AUDIT_SIMULATOR_HOST_DISABLED` explicitly (`gradle.log:40–52`). Framework linkage on x64 does not run these tests.
- **Two `iosSimulatorArm64Test` tasks**, Mafia and engine, are skipped after test compilation/link `NO-SOURCE`.
- **Four Android unit tasks**, Mafia and engine × Debug/Release, are `NO-SOURCE`.
- Those two modules currently contain only `desktopTest` test sources; declaring commonTest dependencies does not supply common test bodies. The directories, module scripts and convention hierarchy were inspected. This is not proof that their code fails on Native, but their Desktop tests did **not** run there.
- Other resource/Java/Detekt `NO-SOURCE` tasks are not additional skipped test cases. Native Whodunit's nine cases must not be advertised as all 288 Desktop game tests having run natively.

The one reported Desktop skip is `peer_can_join_a_hosted_room_and_membership_appears_on_host`. The loopback file actually declares **three physically gated `@Ignore` methods** (`P2pKitRoomTransportLoopbackTest.kt:125–249`). Two also have non-void inferred return types and disappear before disabled reporting. The executing host-advertisement test checks room-code/Hosting/host state, **not** end-to-end peer admission or message exchange.

**ROOT-T3 remains a confirmed test-registration defect, not repaired:** compiled inspection found 222 annotated transport methods, including ten non-void methods; ordinary Jupiter discovery reports 212. Eight are enabled lifecycle tests; two are the intentionally ignored physical methods. The later isolated Unit wrappers executed only the eight enabled bodies, all passing. Neither the wrappers nor ordinary green builds fix registration or satisfy physical LAN evidence. See `validations/ROOT-T3-session_cont.md` and `evidence/inspect-transport-tests-02/compiled-test-methods.json`.

## Deliberate reproducers and failed harness attempts

| Cycle | Exit / evidence | Classification |
|---|---|---|
| `repro-session-01` | 1; one failed assertion | **Intentional SN-C1 regression assertion:** validated start expected Success, received Failure. Deterministic delayed-ack path, not an unexpected baseline test failure. |
| `repro-mafia-01` | 1; one witness passes, one rejection assertion fails | **Intentional M-C01 reproduction.** Contradictory synthetic doctor-history state accepted. Does not establish an ordinary save producer or peer exploit. |
| `repro-ui-transport-01` | 1; no tests | **Audit init-script failure:** queried `desktopTest` before source-set creation. No layout/transport runtime result. |
| `repro-ui-transport-02` | 1 | **Mixed:** SN-C2 test fails expected kit-stop count1 versus0; M-C03 audit test fails compilation on unresolved `width`. Only transport is reproduced in this cycle. |
| `repro-ui-03` | 1; one failed assertion | **M-C03 executed layout proof:** final role measured width0 in the 320dp legal-long-name scenario. No physical-device rendering claim. |
| `inspect-transport-tests-01` | 1; no tests | **Audit init-script failure:** hook ran for included `build-logic` and looked up a nonexistent transport project. Fixed harness rerun is cycle02; not an app defect. |
| `repro-pending-01` | 1; nine passing / two failing cases | **Mixed intentional reproductions:** eight enabled ROOT-T3 wrappers pass; WD-C3 witness passes and malformed-final-two rejection fails; DS-C02 replacement-toast display assertion fails. The later toast-expiry assertion was never reached; timer nonrestart remains source-level proof. |
| `repro-release-race-01` | 1; one passing / one failing Python case | **RL-C3 synthetic HTTP reproduction:** after-mutation-edit invalidation counter-evidence passes; between-edits staged-rollout refusal fails. No real Store call or promotion. |
| `apple-check-01` | 1; three release-link failures | **Host/audit heap failure:** audit lowered checked-in6g to3g. Native compiler reports heap/GC exhaustion (and a diagnostic effective2GiB). Same source passes the6g rerun. Not an app crash or proof of a production memory leak. |
| `native-xcode-phase-01` | `xcodebuild`0 despite synthetic wrapper42 | **Successful reproducer of a failing contract:** copied production inline phase leaves stale placeholder and masks wrapper failure. `failure_propagation_requirement_passed=false`; do not label this ordinary application verification PASS. |
| `native-preference-01` | seed0 / restore0 | **Native-equivalent DS-C01 observation:** explicit English persists after simulated System restoration; fallback Arabic can be promoted into persistence. Synthetic macOS Foundation suite only, not real iOS UI/player settings. |
| `filehandle-api-01` | clang0 / executable3, `opened_directory=false` | **Negative probe / counter-evidence:** directory-open premise did not reproduce on macOS Foundation. Does not prove iOS post-open exception behavior safe. |
| `iosr1-native-01` | clang0 / install0 / launch1 | **Probe launch failure:** simulator denied the wholly unsigned, ad-hoc-disabled probe. No API result and no evidence of an application exception. Terminate3 explicitly says nothing to terminate. |
| `iosr1-native-02` | compile/install/launch0 | **Native-equivalent observation only:** filesystem setup/list/protection/backup-exclusion succeed; keychain query returns `-34018` missing entitlement. Default simulator linker code directory is not private-key signing. No actual KMP/Home-error attribution or Store-signing evidence. |

No receipt in this reconciled set establishes a `--tests` filter matching zero tests as an application failure. The genuine failures above distinguish init configuration, audit-test compilation, compiler OOM, deliberate assertion failure and simulator launch denial. ROOT-T3 is a separate **silent discovery** defect.

`xcode-ui-01`'s passing `testColdLaunchRendersComposeHomeWithoutUnexpectedAlert` checks `app.alerts`, not every in-Compose error/banner. Its Home recovery-unavailable observation was separately tracked as IOS-R1. Do not infer a healthy recovery store, a signed app, repeated cold-launch stability, Arabic gestures or full game flow from that one passing XCTest.

The binary-inspection execution record preserves five attempts: final compatible-Python inspection passes, as do corrected-shell and ancillary retries; initial zsh assignment to read-only `status` and Homebrew pyexpat linkage failures are harness/host problems. Some early helper attempts lack exact start timestamps, explicitly retained as a limitation. The array-root P2pKit receipt is public-source retrieval metadata, not a transport test.

## Cleanup reconciliation

- All **15 primary Gradle receipts** record initially absent owned build directories, immediate wrapper stop exit0, empty cleanup errors and empty remaining-output lists. Stop completed **0.244–0.606 seconds** after each Gradle task finished.
- Cleanup used precise task-created module/root/build-logic `build/` removal, not broad Git/filesystem cleaning. No cleanup Gradle invocation was needed, so those cycles could not start a second cleanup daemon. Global caches, verification metadata, configuration, source and user work were not deleted.
- All **25 preserved stop logs** contain `No Gradle daemons are running.` This applies to the checked-in wrapper's daemon registry, **not every daemon version owned by the user**.
- Nonempty post-cycle scans contain other Gradle **9.4.1,9.5.0,9.7.0** processes, recurring pre-existing Kotlin daemons and, once, process-query commands. No captured process is a Gradle8.13 daemon. `pgrep` exit0 means a match exists, not an empty process list. Other-user/task processes were correctly not killed. Stored scans do not include parent/start-time attribution, so do not overstate per-PID provenance beyond the baseline/ownership evidence.
- The arm64-runtime simulator companion records stop0, shutdown0, delete0, owned UUID absent and no UUID-bearing processes. Both native probes record the same owned-device cleanup and temporary-output removal. Xcode UI records app termination after both supplemental launches, shutdown/delete0, temporary DerivedData/result-bundle removal, and all generated module outputs gone.
- Pre-launch `simctl terminate` exit3 in Xcode UI and the failed native probe means **already not running**; it is not a leaked-process failure. Preference-domain cleanup exit1 similarly says the synthetic domain was already absent.
- Three explicitly recorded task temporary roots (Xcode UI and both native probes) were independently checked absent during this reconciliation. The current expected module/root/build-logic/iOS build-directory check is empty. Evidence directories whose copied report paths contain `build/` are intentionally preserved audit evidence, not live intermediates.
- Filehandle, preference, Xcode-phase and release-race receipts record exact synthetic temporary cleanup. The binary-inspection cleanup removed only its own downloaded full guide/flattened derivative; compact checksums/excerpts/receipts remain. No deletion or process operation occurred during reconciliation itself.

Cleanup receipts corroborate these completed cycles; they are not a blanket claim about unrelated processes or future work. The root finalizer must still perform the final campaign-wide source/output comparison.

## Gates still unexecuted or blocked

| Gate | Status | Source-backed reason |
|---|---|---|
| Real two-/three-device LAN, disconnect/rejoin, lifecycle, recipient privacy | **BLOCKED** | Physically gated methods remain ignored; JVM advertisement/in-memory/native mocks are not device evidence. |
| Android managed R8 Release runtime | **BLOCKED / unexecuted** | Separate `productionAndroidRuntimeCheck` → `pixel2Api35ReleaseAndroidTest`; disposable managed-device and install-ready ephemeral-signing setup not authorized/executed here. Unit tests and unsigned AAB do not cover it. |
| Real Android signing / `productionAndroidSigningCheck` | **BLOCKED** | Private signing use is outside audit authorization; no such task ran. |
| Policy-qualified Apple release/archive/signing | **BLOCKED** | Xcode26.3/17C529 not used; no authorized real signing or archive validation. |
| Store identities, external testing/promotion/submission, owner declarations | **BLOCKED** | Tracked policy marks ownership blocked; no live owner/Store evidence obtained and publishing workflows were not enabled. Synthetic helpers are not Store operations. |
| Full physical accessibility, large-text/layout/RTL gestures, app-switcher privacy and complete mobile game journeys | **BLOCKED / unexecuted** | Current evidence is source, deterministic tests and narrow simulator smoke, not a complete device matrix. |

These are not all external Store requirements: confirmed source, test-registration and latent-release defects remain in the separately independently validated register. The normal gates passing therefore cannot support a READY verdict.

## Evidence limitations and continuation

The commit/tree identifies tracked application source. Baseline inventory separately binds build-consumed untracked work and exclusions; audit init scripts intentionally add isolated reproducer tests. Earlier failing init/UI helper versions were adjusted between retries. Their original diagnostics remain, but current helper hashes must not be presented as historical byte identities for failed attempts.

This report introduces **no new independently approved application finding**. It reconciles execution evidence for the root's final gate ledger. If another cycle runs, add that cycle's original receipt and reports before using this index as final coverage. Reports, raw evidence and user files were not overwritten; only this new index/report and additional read receipts were created.
