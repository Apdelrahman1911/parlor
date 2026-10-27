# Opt-in real P2pKit verification on Linux

This **same-machine** experiment opens real mDNS and authenticated LAN sockets
through the production adapter and pinned P2pKit `0.7.0-rc3`. It is not a physical
LAN, Android/iOS, gameplay-reducer, background-lifecycle or release qualification.
There are no network mocks, public endpoints, real player data or persisted user
identities. Each existing fixture creates a unique app namespace and separate
in-memory device identity/credential stores.

## Run deliberately

Use a clean committed isolated checkout, Linux with `/proc`, Python 3, JDK 21,
and a multicast-capable local network environment. The wrapper must be able to
resolve its strictly verified dependencies. Other platforms fail visibly rather
than report a skipped test as success.

```bash
python3 scripts/verification/real-kit-lan/run.py --allow-local-lan
# Shorter smoke: one advertisement and one of each peer scenario
python3 scripts/verification/real-kit-lan/run.py --allow-local-lan --iterations 1
# Regression tests need no LAN access:
python3 -m unittest discover -s scripts/verification/real-kit-lan -p 'test_*.py' -v
```

## Separate host and peer processes

```bash
python3 scripts/verification/real-kit-lan/processes.py --allow-local-lan
python3 scripts/verification/real-kit-lan/processes.py --allow-local-lan --iterations 1
```

This alternative launches two distinct JVMs per pair (1–3 pairs, default3).
The host advertises through real mDNS, explicitly approves the synthetic peer,
checks authenticated actor replacement, sends a direct terminal envelope and
observes graceful peer disconnection. The peer checks the response and closes
its room. Every endpoint must pass assertions/teardown and exit naturally0;
failed, missing or ambiguous output never becomes a pass.

Strict preparation explicitly compiles `MultiProcessEndpoint.kt` into isolated
test outputs through `process-helper.init.gradle`. It reuses the existing
in-memory device fixture; no shipping source, dependency or existing ignored-fixture
selection is changed. Run in a clean isolated checkout: subsequent ordinary
test compilation owns its normal source inputs again. Neither helper starts
automatically in CI. Only the nonnetworking Python ownership guards join normal
release-tool checks.

Each JVM is limited to512MiB, each pair to110seconds and the whole invocation
to900seconds including preparation/hashing. Logs are bounded1MiB per endpoint;
cleanup retires both owned process groups before fallible evidence hashing.
Its private Gradle registry stop is separately bounded60seconds. Receipts under
`build/real-kit-processes/` bind the clean source, four helper sources and executed
runtime bytes before/after. Role-specific socket/CPU/RSS/thread samples describe
whole synthetic JVMs, not action latency or per-phone cost. This adds process
separation, **not physical LAN, mobile background/process-death recovery,
gameplay, long-session, battery/thermal or RPC-comparison qualification**.

The explicit flag is required before any preparation. The helper never changes
`@Ignore`, normal test selection, CI, shipping source or dependency versions. It
calls four allowlisted existing fixture methods directly, always runs teardown,
and creates a new JVM for every case. Defaults: one advertisement plus three
iterations each of approved join, authenticated bidirectional delivery/actor
binding, and broadcast to two distinct peers (10 cases).

## Containment and evidence

- Each JVM has a 512 MiB heap limit and 120-second deadline; total execution is
  governed by a 900-second work budget, including compilation and custody hashes.
  Bounded owned cleanup may extend completion (up to 90 seconds for the private
  Gradle stop, plus process finalization waits). Iterations are restricted to 1–3.
- Strict Gradle preparation uses two workers, a 3 GiB heap and a fresh private
  daemon registry. Cleanup stops only that registry; caches remain shared.
- Child process groups use the repository's existing scoped finalizer, including
  timeout, interruption and output overflow. No global process/name cleanup.
- Logs are retained locally under a new `build/real-kit-lan/<invocation>/` directory,
  with 32 MiB preparation and 1 MiB per-case output budgets. Failed cases fail the
  command; they are not silently retried or treated as successful samples.
- `receipt.json` binds the clean commit/tree, executed classpath artifact/class
  hashes, helper bytes, fixture/teardown outcomes and resource samples. Source is
  rechecked after preparation and execution. Keep failure receipts too.

CPU, RSS, native thread and held-socket samples concern the **entire synthetic JVM**,
including startup, JIT, crypto setup and teardown; shared-machine load varies.
Socket addresses, credentials and player state are omitted. These are not message
latency, mobile-host resource, battery/thermal or RPC comparison benchmarks.
Normal CI still ignores the three platform-sensitive multi-kit fixtures. Successful
Linux runs do not establish Windows multicast reliability or close physical-device
acceptance in [the manual plan](../../../docs/P2P_MANUAL_TEST.md) / issue #6.
