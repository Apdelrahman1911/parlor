# Real Gradle fixture readiness — October 28, 2026

## Confirmed failure — #279

The strict `productionCheck allTests` execution at
`3944c0a096e747b6289aa7476334c6d4a02cd33f` failed in the unchanged
`GradleDaemonRegistryTest.test_single_use_timeout_retires_only_its_registry`.
The busy-daemon observer admitted `started.pid` by file existence, then
`int(marker.read_text())` raised `ValueError` because its contents were empty.
Groovy `File.text` creates/truncates before writing, so existence was not a
complete-write readiness signal. This is an actually observed **P2 test race**,
not evidence of a production daemon-retirement regression.

The failed log has SHA256
`27ff76ad1ccccf0287e566cce7078c7b77c818308f8f4637db36d491b4524e70`.
All1,827Desktop cases,652Android cases perDebug/Release variant and112GitHub-test
cases had zero failures/errors; Detekt/lint/R8/unsigned AAB passed. The749 release
controls reported one error and the known Linux skip. **The aggregate failed**;
passing earlier stages do not qualify it. Original logs and source-bound
receipts remain in local agent custody.

[Issue #279](https://github.com/Apdelrahman1911/parlor/issues/279) follows
deduplication against daemon/PID/registry searches and #257/#268/#269.
#257's accepted production process-retirement fix remains resolved; this is
a distinct fixture publication boundary.

## Correction and executed controls

The actual offline Gradle fixture writes its complete PID into
`started.pending`, then publishes `started.pid` with Java NIO's atomic
same-directory move. No non-atomic fallback is accepted. The original consumer,
30-second timeout, actual busy-stop, unrelated-registry-survival and exclusively
owned cleanup assertions remain unchanged.

A new real-Gradle test places a bounded barrier between write and publication.
It requires the final marker to remain absent, verifies the staged actual PID
is alive, releases publication, checks exact final content and removal of the
staging file, then verifies its scoped registry stop. The barrier is confined
to this non-shipping regression, not a longer production timeout or CI retry.

Temporarily publishing the final marker before that barrier produces exactly
one failed test. Source is restored byte-for-byte, and both actual offline
Gradle tests then pass on Linux. No application, signing, network room, private
session, global daemon stop or shared-cache deletion is exercised.

```bash
python3 -B -m unittest scripts.release.tests.test_gradle_daemon_registry -v
```

## Required qualification

The corrected source needs a fresh full strict check, existing hosted gates,
protected integration and automatic merged-main qualification. The original
failure is retained and no blind rerun is used. Linux fixture execution is not
macOS/Windows filesystem, mobile cleanup, physical-device or Store evidence.
All existing platform, dependency-verification and Release gates remain intact.
