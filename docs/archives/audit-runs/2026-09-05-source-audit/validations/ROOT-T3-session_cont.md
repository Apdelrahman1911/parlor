# ROOT-T3 — Independent validation: non-void test methods are not discovered

- **Finder:** `/root` (two absent ignored loopback tests); sibling expansion by validator.
- **Independent validator:** `/root/session_cont`.
- **Classification:** **CONFIRMED DEFECT — test registration**, with a resulting **TEST/EVIDENCE GAP**, not a demonstrated shipping-app failure.
- **Severity:** **Medium**. Eight enabled transport regression tests silently do not run; two intentionally ignored real-LAN tests do not even appear as skipped. This undermines claimed regression coverage without itself proving faulty application behavior.
- **Source:** `main`, commit `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`; tracked files unchanged. Exact source hashes are in `evidence/root-t3-independent-session_cont/source-hashes.json`.

## Exact locations

All paths below are relative to the absolute repository root `/Users/abdelrahman/Projects/parlor`.

`shared/transport-p2p/src/desktopTest/kotlin/com/parlor/transport/p2p/P2pKitRoomTransportLifecycleTest.kt`:

| One-based lines | Enabled annotated method | Compiled return type |
|---|---|---|
| 427–450 | `disconnect_during_acceptance_rolls_back_the_seat_without_a_ghost_member` | `assertk.Assert<Result.Success<?>>` |
| 452–472 | `cancellation_during_acceptance_propagates_after_rolling_back_the_seat` | `assertk.Assert<Result.Success<?>>` |
| 3666–3736 | `wrong_room_does_not_end_search_before_a_late_correct_candidate_appears` | `assertk.Assert<Result.Success<?>>` |
| 3790–3848 | `admission_pending_opens_a_separate_host_approval_window` | `assertk.Assert<Result.Success<?>>` |
| 4612–4623 | `host_send_propagates_coroutine_cancellation` | `java.util.concurrent.CancellationException` |
| 4625–4637 | `peer_send_propagates_coroutine_cancellation` | `java.util.concurrent.CancellationException` |
| 4639–4658 | `kit_factory_cancellation_is_never_mapped_to_transport_failure` | `java.util.concurrent.CancellationException` |
| 4758–4782 | `fatal_send_and_cleanup_failures_are_never_converted_to_normal_results` | `java.lang.AssertionError` |

`shared/transport-p2p/src/desktopTest/kotlin/com/parlor/transport/p2p/P2pKitRoomTransportLoopbackTest.kt`:

| One-based lines | Intentionally ignored annotated method | Compiled return type |
|---|---|---|
| 157–205 | `peer_to_host_message_round_trips_and_host_to_peer_message_arrives_back` | `assertk.Assert<HostMessage.SessionEnded>` |
| 207–249 | `host_broadcast_reaches_every_peer` | `assertk.Assert<HostMessage.SessionEnded>` |

## Complete reachable proof

1. `settings.gradle.kts:48–51` includes the transport module. Its `build.gradle.kts:1–4,23–27` applies `parlor.kmp.library`, Kotlin test, AssertK and coroutine test dependencies. `build-logic/convention/src/main/kotlin/com/parlor/buildlogic/KmpLibraryConventionPlugin.kt:33–37,60–62` creates the Desktop JVM target and selects JUnit Platform for test tasks. These are real inputs to the normal `:shared:transport-p2p:desktopTest`, not orphaned source.
2. Both classes import `kotlin.test.Test` and `kotlinx.coroutines.runBlocking`; lifecycle also imports `kotlin.test.assertFailsWith` (`:85–87`). The affected methods are expression-bodied functions with no explicit `Unit` type: `fun test() = runBlocking { ... }`.
3. At the exact resolved versions, `runBlocking<T>` returns its block result (`kotlinx-coroutines-core-jvm:1.11.0`, `concurrentMain/Builders.concurrent.kt:155–172`), `AssertK.isInstanceOf(KClass<T>)` returns `Assert<T>` (`assertk-jvm:0.28.1`, `commonMain/assertk/assertions/any.kt:255–262`), and `assertFailsWith<T>` returns the caught throwable (`kotlin-test:2.4.10`, `Assertions.kt:661–672,708–720`). Thus each affected final expression makes the enclosing JVM method non-void. `@IgnorableReturnValue` on Kotlin assertions does not force an expression-bodied caller to return `Unit`; actual bytecode inspection confirms this.
4. `kotlin-test-junit5:2.4.10` aliases `Test` to `org.junit.jupiter.api.Test` and `Ignore` to `Disabled`. Jupiter engine **5.10.1**, `IsTestMethod:25–28`, delegates with `mustReturnVoid=true`; `IsTestableMethod:37–52` rejects a method when `returnsVoid(candidate)` differs. `ClassSelectorResolver:129–140` selects class methods with this predicate before execution. The ignored loopback methods are filtered before disabled-test reporting.
5. Root's fresh compiled-metadata inspection at the same tracked tree found **222** actual Jupiter `@Test` methods and **10** non-void methods, exactly the table above, with zero parameters and public-final visibility. The eight lifecycle methods have no `Disabled` annotation; the two loopback methods do. See `evidence/inspect-transport-tests-02/compiled-test-methods.json`, independently reopened by this validator.
6. The normal transport test task in that same cycle returned success but reported **212 cases: 211 passed, one skipped**. Lifecycle reports 122 cases despite 130 compiled annotations; loopback reports two cases despite four compiled annotations. Baseline repository-wide XML shows the same omission. This is absent discovery, not a reported test failure or an ordinary skip.

## Evidence and counter-evidence

- `evidence/inspect-transport-tests-02/gradle.log:81,96,98,108–112,131,160,163,182–186` records the actual selected AssertK, Kotlin test, coroutines, Jupiter and Platform versions; `:417` records `annotated=222 nonVoid=10`. This avoids inferring the graph only from verification metadata or a POM.
- `evidence/inspect-transport-tests-02/receipt.json` records the exact command, source identity, exit zero, immediate Gradle stop and removal of all task-created build outputs. Root exclusively executed this cycle. The earlier init-script scoping failure is separate `inspect-transport-tests-01` evidence, not an app exception.
- The independent source-annotation/XML triage spans all 13 included modules' common/Desktop tests. Only transport has missing names; see `evidence/root-t3-independent-session_cont/all-module-annotation-xml-triage.json`. This regex/name triage is not a Kotlin AST proof and does not replace file review or the compiled transport-method evidence.
- Sibling lifecycle tests ending `isEqualTo`, collection assertions, explicit `Unit` helpers or `runTest` are present in XML. Not every `runBlocking` test is broken, and returning a caught exception does not mean the test threw that exception.
- Physical-loopback `@Ignore` is intentional and must remain until the required environment genuinely runs the tests. This finding does **not** request removing those annotations. Independent stale-fixture issues are tracked as **SN-T1**, not multiplied into ten application defects.
- This validation has not invoked the eight previously undiscovered test bodies. Their eventual pass/fail status remains separate from the now-proven discovery defect. Root may add an isolated wrapper run; neither discovery inspection nor fake-kit execution proves physical LAN behavior.

## Suggested remediation and regression coverage

After separate authorization, make annotated test methods explicitly `Unit`-returning, preferably block-bodied wrappers around `runBlocking`, retaining all assertions, teardown and physical-test ignores. Add a compiled-test contract that rejects non-void Jupiter `@Test` methods in the intended Desktop classes; do not indiscriminately forbid value-returning `@TestFactory` methods. Rerun the enabled fake-kit cases and baseline suite, then confirm ten newly discovered descriptors, of which the existing two physical cases remain skipped. Investigate any exposed failing body independently rather than weakening assertions.

## Authoritative research

Accessed **2026-09-05 UTC**. Exact Maven Central source archives, member names, extraction SHA-256 values and URLs are in `evidence/root-t3-independent-session_cont/research.jsonl`. Only public dependency sources were downloaded; no private code or player data was sent externally. Principal URLs:

- `https://repo.maven.apache.org/maven2/org/junit/jupiter/junit-jupiter-engine/5.10.1/junit-jupiter-engine-5.10.1-sources.jar`
- `https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/kotlin-test-junit5/2.4.10/kotlin-test-junit5-2.4.10-sources.jar`
- `https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/kotlin-test/2.4.10/kotlin-test-2.4.10-sources.jar`
- `https://repo.maven.apache.org/maven2/com/willowtreeapps/assertk/assertk-jvm/0.28.1/assertk-jvm-0.28.1-sources.jar`
- `https://repo.maven.apache.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-core-jvm/1.11.0/kotlinx-coroutines-core-jvm-1.11.0-sources.jar`

**Independent conclusion:** confirmed Medium test-registration defect at the recorded tree. No production code was changed; no claim that the underlying application scenarios necessarily fail.

## Subsequent isolated body execution — evidence independently reopened2026-09-05

Root ran the explicit audit-only Unit wrappers in `reproducers/transport_registration/ROOT_T3RegisteredAuditWrappersTest.kt` using invocation-only `repro_pending.init.gradle`. This validator reopened the complete wrapper and injection script, raw XML, test receipt, cycle receipt and stop/cleanup evidence. Each wrapper creates a fresh original lifecycle fixture, calls exactly one of the eight previously missing enabled methods, and invokes original `cancelScope()` teardown in finally. No original annotations or application tests changed; the two ignored physical loopback methods were not invoked.

`evidence/repro-pending-01/reports/` contains `TEST-com.parlor.transport.p2p.audit.ROOT_T3RegisteredAuditWrappersTest.xml`: **8tests,0failures,0errors,0skipped**, timestamp2026-09-05T08:26:17.035Z. These eight fake-kit method bodies now have positive execution evidence at this tree. The overall mixed cycle exited1 because other independent audit reproducers failed; it must not be reported as an overall passing build. `evidence/repro-pending-01/receipt.json` records immediate wrapper Gradle stop exit0, exact task-created output cleanup and no remaining outputs or cleanup errors; unrelated pre-existing Gradle9.x processes were correctly not terminated.

The registration finding is unchanged: the ordinary task still filters these annotated non-void methods; isolated wrappers add evidence but are not a fix. Passing fake-kit bodies do not establish physical LAN delivery or fulfill the intentional loopback device gate. The earlier statement that these bodies had not run is superseded by this later scoped evidence.
