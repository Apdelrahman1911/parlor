# Separate-process real P2pKit verification — 2026-10-27

## Finding and implemented boundary

[Issue #6](https://github.com/Apdelrahman1911/parlor/issues/6) identifies the
missing independent-participant real-transport regression boundary. Existing
adapter/fake tests remain valuable and the three platform-sensitive LAN JUnit
fixtures remain ignored in ordinary CI. An earlier opt-in runner exercised real
SDK fixtures in a single JVM; it did not establish process separation.

`scripts/verification/real-kit-lan/processes.py` now starts a distinct host and
peer JVM for each pair. `MultiProcessEndpoint.kt` is compiled and analyzed in
`:shared:transport-p2p:desktopTest`, never a shipping source set. The normal
release Python suite exercises nonnetworking ownership/failure guards only.
The actual network probe requires explicit consent:

```bash
python3 scripts/verification/real-kit-lan/processes.py --allow-local-lan --iterations 3
```

It uses the production transport adapter and strictly verified, published
P2pKit `0.7.0-rc3`. No private Kotlin-source injection, dependency override,
mock networking, new shipping layer or verification bypass is used. Synthetic
in-memory identities and a new discovery namespace isolate each pair.

## Executed committed-source evidence

At commit `66c578b6210399441e83e148383909c61737d666`, tree
`317b9f891cb31a08755484f1e534ec469ed5ea52`, on Linux x64 / JDK 21:

- Strict transport desktop tests, plain/type-aware Detekt and release automation
  passed. Transport: 333 cases, zero failures/errors, three existing LAN skips.
  Release Python: 705 cases, one environment skip. The independent runner suites
  passed all 34 nonnetworking cases (19 existing plus 15 process guards).
- **3/3 actual host/peer pairs passed**, October 27 11:50:17–11:50:55 UTC.
  All six distinct JVMs exited naturally with code 0 and passed owned teardown.
  Real discovery, explicit host admission, authenticated actor replacement,
  direct host response and graceful peer disconnection were asserted.
- Four helper-source hashes and 1,071 executed runtime files were bound
  before/after; unchanged clean source was checked. Both processes are retired
  before fallible evidence hashing. The private Gradle registry stopped with 0.
- Runtime receipt SHA256:
  `643e988fb867f7c63d37bf3420c2a985f57b739cd0a21eb5ba3739953ac46f1b`.
  Strict checks-log SHA256:
  `1c0fa399de3d178d32a0fe582a530c406b42e989bb1be34923f9a38fae5864ba`.

Custody is retained locally beneath
`.git/agent-custody/overhaul-2026-10-27/diagnostic-controls/multiprocess-lan-3806921f/`;
raw logs, addresses and credentials are not published in this report.

## Resource samples and limits

Each endpoint has a 512 MiB heap and bounded four-thread fixture dispatcher.
Pairs have 110-second deadlines; the invocation has a 900-second work budget.
Logs are bounded to 1 MiB per role. Cleanup is owned-process-only, with a separate
60-second private Gradle-stop limit. Tests reproduce child failure, missing or
ambiguous advertisement, missing teardown, timeout, overflow, interruption and
hashing failure without starting real networking.

Observed pair durations were 13.132, 12.331 and 12.217 seconds. Sampled whole-JVM
RSS ranged 237,875,200–248,004,608 bytes, CPU totals 4.57–5.52 seconds, and native
thread maxima 42–44. These include startup, JIT, crypto and teardown. Sampling
had one incomplete observation. They are not action latency, allocation,
per-player, mobile-host, battery or thermal measurements.

## Acceptance still outstanding

This is same-machine Linux multicast/socket execution, not physical Android/iOS
LAN, gameplay synchronization, background recovery, abrupt process death,
extended-session, power/thermal, accessibility or RPC-comparison evidence.
P2pKit messaging remains unchanged; this probe does not justify RPC migration.
Issue #6 stays open pending reviewed integration and its remaining acceptance
scope. Exact-head hosted verification and protected integration of this new
runner remain pending; no physical or Store readiness is claimed. Documentation
added after the executed source above does not retroactively certify a newer head.
