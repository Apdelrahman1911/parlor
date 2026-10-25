# JSON nesting guard diagnostic

Offline JDK 21 microbenchmark of Parlor's **actual** `requireBoundedJsonNesting`
source and the catalog-pinned kotlinx.serialization JSON structural parser.
No Gradle daemon, network downloads, dependency overrides or production changes.
This does **not** compare P2pKit RPC, execute game-schema decoding or qualify a
mobile device, application build, network workload, battery or thermal behavior.

Run after the nesting guard is present in the chosen checkout:

```sh
python3 scripts/verification/json-nesting-benchmark/run.py \
  --repository "$PWD" --kotlin-version 2.4.0 \
  --output /tmp/parlor-json-nesting-benchmark.json
```

The explicit diagnostic compiler/stdlib 2.4.0 is **not** Parlor's catalog compiler
2.4.10. It permits an isolated source probe with already-cached tools while the
full Gradle toolchain is unavailable. Missing artifacts fail closed; this script
never installs them. Other compiler versions require reviewing the driver's
compiler dependency list rather than silent fallback. Runtime serialization
version is read from the selected checkout's catalog. Every jar and the guard,
Java harness and Python driver are SHA-256 recorded in the result.

Each case/mode runs in a new JVM with `-Xms64m -Xmx256m -Xss1m`, a 25-second
subprocess deadline and synthetic, non-private fixtures. Accepted cases run
3,000 warmup + 10,000 measured operations. Excessive-depth unguarded parsing
runs only 2 warmup + 10 operations; expected `StackOverflowError` is deliberately
contained in that disposable JVM. This is a defect probe, not a reliable latency
sample. Unexpected failures stop the driver.

Modes: `parse`, `guard`, `guardedParse`. Parsing includes UTF-8 String creation
in both parser modes. Fixtures are a small command, 1/8/32 KiB snapshots dominated
by a synthetic string, and nested arrays of depth 64/65/4096. They are workload
shapes, not production player payloads. Strings exercise a full scan without
conflating larger trees with larger byte counts.

The result contains per-operation p50/p95 elapsed time, measured-loop CPU and
allocation bytes for the calling thread (JDK ThreadMXBean), accepted/rejected/
stack-overflow counts, endpoint RSS/native thread counts on Linux and tool/source
provenance. `-1` means a metric is unavailable. Allocation includes measurement
bookkeeping; RSS includes JVM/JIT/GC and is not retained-payload size. Thread
counts are endpoints, not lifetime maxima. Concurrent load, fork-to-fork JIT and
GC make this a diagnostic, not a calibrated JMH performance gate. Do not infer
mobile battery, native networking threads or full application memory from it.
