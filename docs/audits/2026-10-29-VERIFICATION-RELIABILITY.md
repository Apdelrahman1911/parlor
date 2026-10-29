# Independent verification reliability follow-up

## Scope and integration boundary

This small main-based change separates verified tooling and regression coverage
from unresolved native input loss. Base is `7c87a8726bab4da85cbd3824b30d2fcc709ad894`;
its production run40759303257 failed. PR#282 stays draft and unmerged.
No cold-recovery implementation or copied input-observation harness is included.

## Exact Xcode worker custody — #285

`apple_verification_hygiene.py` admits only the independently inventoried Xcode
26.3/17C529 DTServiceHub path, not a basename wildcard. Existing source, toolchain,
UID, original runner marker, new lifetime, baseline and audit-token checks remain.
Unknown workers still fail closed; rejection records only bounded canonical public
SDK-relative paths, never argv, environment or foreign paths.

Read-only inventory40812954673 and actual three-arm40833776865 establish the
installed component and consistent producer cleanup receipts: Gradle stop0,
owned Simulator retirement, exact worker SIGTERM/SIGKILL and observed absence.
This is diagnostic evidence, not independent post-run process re-attestation
or current-main production qualification. Seventy local cleanup controls pass;
removing exact admission makes the targeted regression fail once.

## Credential availability — #252 / #253

Six registered `commonTest` methods cover nine read-failed transactions, pending-
only recovery, failed staging/commit writes, store recreation, failed selected
removal and failed partial-deletion writes retaining an unrelated membership.
Failures remain `Unavailable`, durable generations survive, and later explicit
operations use the correct ownership. Focused execution:6PASS/0FAIL/0SKIP,
owned stop0. The tested store matches unchanged main bytes (`a541a49a…e5e1c`).

Three deliberate, subsequently restored controls normalize failed reads to absence,
failed removals to success or failed writes to success. Exactly2/1/3 selected
regressions fail, respectively; no errors/skips and all owned stops succeed.
These controls inject before backing-store mutation: they do not prove partial-
write atomicity, locked Keychain, OS process death, phone networking or battery use.
No shipping store correction was required by these tested paths.

## Required verification

```sh
./gradlew :shared:transport-p2p:desktopTest --tests '*ResumableCredentialAvailabilityTest*'
python3 -B -m unittest scripts.release.tests.test_apple_ci_hygiene -v
./gradlew productionCheck allTests --dependency-verification=strict
```

Fresh full committed-source gates, all required free hosted architectures/Release/
native jobs, protected integration and automatic actual-main checks remain
mandatory. A diagnostic pass never replaces them. #284's separate source-copy
tooling, #267's original cause, #258 and physical/Store acceptance stay unresolved.
