# JSON nesting guard: executed JVM diagnostic

## Scope and provenance

Executed 2026-10-25 on Linux x86_64, JDK 21.0.12, using the committed driver in
`scripts/verification/json-nesting-benchmark/`. Full machine-readable results,
jar/tool hashes, source hashes and individual outcomes are in
[the JSON receipt](JSON_NESTING_MICROBENCHMARK_2026-10-25.json).

Production guard was compiled from networking workstream source
`aefa85ac99e08a4516250fd4e3510795a696226d`, guard SHA-256
`22f3e47b74272305b174256ba88ef5adf60c7876b9791c6cfbe747de5d02b09d`.
Parser/core libraries are the actual catalog-pinned serialization **1.11.0** jars.
Compiler and stdlib are the actual catalog-pinned **2.4.10**. This is still an
isolated utility compile, not full Gradle or production build qualification.
The earlier provisional 2.4.0 run is retained in
[its separate receipt](JSON_NESTING_MICROBENCHMARK_2026-10-25_diagnostic-2.4.0.json),
with its matching historical runner in commit `c42102e5`; results below describe
only the newer pinned-toolchain run. Do not add platform/repeated counts together.

No dependency changes, trust regeneration, downloads, private payloads or Gradle
builds were performed. Every case/mode used a separate capped JVM. This measures
structural parsing of synthetic command/snapshot shapes, **not game-schema
execution, encryption, P2pKit, RPC, an Android/iOS runtime or real networking**.

## Observed results

| Synthetic input | Bytes | Parse p50 / p95, µs | Guarded parse p50 / p95, µs | Guarded thread CPU, µs/op | Guarded allocation, bytes/op |
|---|---:|---:|---:|---:|---:|
| Command | 49 | 1.173 / 2.517 | 1.113 / 3.283 | 1.956 | 1,193 |
| 1 KiB snapshot | 1,024 | 1.374 / 2.989 | 3.065 / 5.479 | 3.979 | 3,530 |
| 8 KiB snapshot | 8,192 | 5.185 / 13.661 | 13.153 / 23.421 | 15.876 | 17,865 |
| 32 KiB snapshot | 32,768 | 14.142 / 37.900 | 37.460 / 51.733 | 41.123 | 67,017 |
| Depth 64 array | 129 | 2.506 / 5.708 | 2.681 / 6.367 | 3.742 | 6,689 |

Each accepted row: 3,000 warmup and 10,000 measured operations per mode. Guard-only
accepted scans recorded approximately **0.1 allocated bytes/op**, including loop
measurement overhead; they do not allocate a syntax tree. Guarded parsing's
allocation is effectively the same as the unguarded parser, within measurement
noise. Larger payloads incur a full linear scan and measurable CPU cost; the
scanner is not free, and these data do not justify removing a security check.

### Hostile nesting

- Depth 65 (131 bytes): all **10,000/10,000** guarded operations rejected before
  parsing; the unguarded parser accepted the payload.
- Depth 4096 (8,193 bytes): **10/10** unguarded measured operations threw
  `StackOverflowError` with a 1 MiB JVM stack. This input is much smaller than
  common allowed frame sizes. That is an executed resource-exhaustion reproducer,
  not a theoretical concern.
- The same depth-4096 input: **10,000/10,000** guarded operations rejected with an
  ordinary bounded exception; guarded p50/p95 **1.031/2.943 µs**, about 728
  allocated bytes per rejection. Guard-only results were similar. The scanner
  stops at excess depth instead of traversing the whole hostile tree.
- Rejection prevents recursive parser entry; it does not validate complete JSON,
  UTF-8, schema, frame size, caller identity or authorization. Keep those checks.

Native JVM thread endpoints ranged 18–21, with guard-only cases 18→18; this is
not a thread-per-client benchmark or a maximum-lifetime thread count. RSS endpoint
values are retained in the receipt, not interpreted as application working-set
or leak measurements. Neither interval snapshots nor a small synthetic test
establish steady-state mobile RAM behavior.

## Interpretation and limitations

The concrete benefit is safe rejection of malicious nesting without adding
significant accepted-path allocation. Linear scanning costs increase with payload
size; the measured 32 KiB parser-plus-guard consumed roughly 41 µs of caller CPU
per operation on this server, **not a promised phone latency or capacity**.

One fork per case, concurrent server builds, tiered JIT, GC and nonrandomized case
order limit comparative precision. For example, the tiny guarded command has a
lower median but higher p95/CPU than the parser-only fork; this is not evidence
that adding a scan speeds up parsing. Deep unguarded samples deliberately remain
small to contain the failing parser; no statistical latency claim is made for
that failure. Snapshot fixtures contain a large string, not actual game objects.
A release decision still needs full regression suites, actual schema codecs,
representative device workloads and existing security/CI gates.

**RPC comparison remains unperformed.** This decoder experiment supplies no
RPC latency, bandwidth, threads, battery, recovery or architectural evidence.
