# Real-LAN selected Resume — October 28, 2026

## Executed scope and source

At `f1ed1cb5a54b4f7ccbea14a803fbb9f9f279ffc6` (tree`c4c0440b95831d6db824d846e0a9859f376790f4`), the optional
`--scenario resume` launches three separate synthetic Linux host/peer JVM
pairs using published P2pKit0.7.0-rc3 and the actual Parlor transport. All three
pairs pass; all six JVMs naturally exit0 with owned cleanup. The original
receipt SHA256 is`3f04cf8910c53dc042d7ccab1370e3c5442a34ea3368da13fa0739e26341e941`. Pair elapsed times are14.641/14.669/
14.673seconds, including startup/handshake/cleanup, **not action-latency or
mobile-performance benchmarks**.

The host approves the synthetic selected game/version, freezes its exact remote
roster, independently observes peer physical closure, then sees the same
stable authenticated seat reconnect without opening new admission. The runner's
explicit host-close barrier synchronizes this experiment; it does not retry
player actions or change shipping recovery deadlines. The peer rejects wrong
game/version without another real-kit creation attempt or credential mutation,
then successfully resumes, preserves its seat/membership and rotates its secret.
A validated authoritative terminal envelope revokes the retained membership.
No real player identity, room secret or private game data is logged.

## Verification and retained failures

Strict classpath preparation passes and its exclusive Gradle registry stops0.
Six helper-source hashes and executed runtime artifacts are bound before/after.
Independent original-receipt checking rechecks the clean source, runtime bytes,
role assertions, log hashes, natural exits and cleanup.19nonnetworking Python
controls pass; admission-only success cannot qualify Resume, an invalid scenario
cannot spawn a helper, and both owned groups retire after failure/interruption.
Normal CI compiles/analyzes the endpoint but does not open LAN sessions.

The earlierf7b19889 experiment remains **FAIL**,0completed pairs. Its host
incorrectly expected the transport roster to include the host. Existing source
and deterministic tests confirm that `HostP2pRoom.members`/`closeAdmissions`
contain remote seats; the corrected fixture requires the one exact remote seat
and does not weaken any gameplay roster assertion. The peer's checkpoint timeout
is downstream of that failed host assertion. Both groups were retired. Initial
read-only verifier assumptions about `source.head` and Gradle stop-output format
were separately corrected without rerunning or relabeling that runtime.
Local failed/pass evidence remains under `.git/agent-custody/overhaul-2026-10-28/
resume-binding/real-lan/`; it is not a signed public artifact or Store approval.

```bash
python3 scripts/verification/real-kit-lan/processes.py --allow-local-lan --scenario resume
python3 -m unittest discover -s scripts/verification/real-kit-lan -p 'test_processes.py' -v
```

## Acceptance boundaries

The peer process survives, with test-only in-memory identity/credential storage.
This is not process termination/restart, native protected-storage, phone-call,
lock/Doze/suspension, Wi-Fi switching, physical/mobile LAN or game-reducer
execution. Whole-JVM CPU/RSS/thread/socket samples do not measure phone battery,
thermal cost or RPC benefits. #251/#252 physical identity/recovery and #258
strict protection remain open. Complete current-head local/hosted checks,
normal protected merge and fresh actual-main qualification remain required.
No dependency, architecture, Release optimization, workflow, paid runner,
signing bypass or Store release is introduced.
