# ROOT-T3 independent remediation review — APPROVED

Fix author `/root`; independent reviewer `/root/session_cont`;2026-09-05. Original finding and separate dependency-source/bytecode validation reopened. Entire current fix diff, all10 affected complete test bodies, complete40-line new compiled-signature guard, fixture owners/teardown, source-set/Gradle test wiring, red and green receipts/raw relevant XML inspected. This reviewer executed no build/test and modified no application/test sources during this review.

## Root cause and safety

The fix adds only an explicit `: Unit` return to the10 affected `runBlocking` test functions. It preserves all statements, assertions, exceptions, timeouts, fixture state and teardown. This supplies `runBlocking<Unit>`'s expected result so the generated annotated methods return JVM void, addressing Jupiter's actual pre-discovery filter rather than wrapping/ignoring failures. All eight enabled cases remain enabled; both physical-loopback ignores are byte-for-byte retained. No production API, transport policy, game rules, private projection, credentials, protocol4.2, storage or release code changes belong to ROOT-T3.

`JupiterDiscoveryContractTest.kt:1–40` inspects actual compiled classes in its own test code-source directory, loads types without class initialization, visits declared `org.junit.jupiter.api.Test` methods (including Disabled), and rejects non-void/private/static signatures. JDK21 supports Path.of and Stream.toList. Files.walk is closed via use. It intentionally does not forbid value-returning TestFactory methods. It scans this module's compiled test output, not dependency archives/the entire application; the fail-closed directory/nonempty assertions do not promise a generic IDE-JAR runner. Current Gradle layout is verified by red/green execution. No new dependency or suppressed validation is introduced.

## Independently checked receipts

`evidence/root-t3-red/`: new guard executed against unchanged original methods and failed with exactly the10 method signatures from the original finding (8enabled assertion-value/throwable returns and2disabled assertion-value returns). One test, one expected failure, no skip/error; Gradle exit1. This makes the check non-vacuous at the intended build boundary.

`evidence/foundation-transport-green/`: ordinary, unfiltered `:shared:transport-p2p:desktopTest` exited0 with **223reported cases,220passed,3intended physical skips,0failures/errors**. Relevant raw XML independently parsed: Lifecycle130/130 includes all eight formerly absent enabled method names; Loopback4descriptors has one advertising PASS and all three original physical cases SKIP; new guard1/1. Baseline212descriptors plus10restored descriptors plus1new guard explains the count. Neither skipped body was secretly run/unignored. This does not validate stale physical fixture assumptions tracked separately as SN-T1 or real peer delivery.

Both receipts bind source manifests, report no source change during their cycle, immediate Gradle stop exit0, removal of task-created module/root/build-logic outputs, no remaining outputs/cleanup errors/owned worker processes. No signing or device proof is inferred.

**Independent outcome:** ROOT-T3 discovery defect is fixed at the reviewed working-tree versions with true red/green compiled evidence. The eight fake-kit cases have ordinary-suite PASS evidence. Physical LAN cases remain an explicit separate evidence/fixture gate. No branches, commits, integration or issue operations were performed.
