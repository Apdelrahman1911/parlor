# Free-hosted CI optimization — implementation and benchmark ledger

## Constraints and baseline

The repository API confirms Parlor is public. Official GitHub documentation,
retrieved October 27, 2026, states that standard hosted runners are free and
unlimited for public repositories. GitHub Free permits 20 concurrent jobs,
including **at most five macOS jobs**, shared across workflows in the account.
Capacity is a limit, not a queue-time guarantee.

- [Standard runners](https://docs.github.com/en/actions/reference/runners/github-hosted-runners):
  `macos-15` is M1/arm64, three cores/7 GB RAM; `macos-15-intel` is Intel,
  four cores/14 GB RAM. Both are standard, not paid larger runners.
- [Concurrency/storage limits](https://docs.github.com/en/actions/reference/limits).
- [Billing](https://docs.github.com/en/billing/concepts/product-billing/github-actions)
  and [cache limits](https://docs.github.com/en/actions/using-workflows/caching-dependencies-to-speed-up-workflows).
  The default cache cap is 10 GB/repository with eviction; opting into a larger
  cap can incur charges. No billing settings, cap increases or infrastructure
  purchases are part of this change. The live cache-usage API returned zero
  entries; the cap-management endpoint required a payment method (HTTP 402).
- Both official runner-image manifests list Xcode 26.3/17C529. Every Apple
  lane still verifies that installation, SDK minimum and policy before building.

The executed baseline is [40482354118](https://github.com/Apdelrahman1911/parlor/actions/runs/40482354118)
at `3806921f4902f5d841da7239c70c6c358b374adf`: iOS Release job
`122116292888` took **171.48 minutes**, with 133.03 minutes in
`productionAppleCheck` and 37.12 minutes building the Swift wrapper. Gradle
accounted for 36m29s of the wrapper. Its simulator-arm64 Release link ran twice;
the second was not up-to-date. Exact input invalidation was not established.
Log SHA-256: `2395ba691510131e6b74ab0cf3bccc69d01fabd728531c47898858ac39c1b8a0`.

## Implemented candidate

1. **Three isolated Release lanes.** Device arm64 and simulator x64 each
   execute their existing optimized Release link task. The simulator-arm64 lane
   retains complete Apple type-aware analysis and the unsigned Swift Release
   build. The normal Xcode `embedAndSignAppleFrameworkForXcode` invocation
   produces its optimized framework once; no prelink, forced up-to-date flag,
   skipped build phase or prebuilt framework download is used.
2. **Same required gate.** `iOS release frameworks and Swift wrapper` is now
   a small Linux join. It always evaluates all three lane results during full
   qualification and rejects failed, skipped, cancelled, missing or stale-source
   lanes. Each lane publishes exact source/tree plus uploaded evidence and
   cleanup digests only after successful native retirement and output cleanup.
   All three architectures, Swift/package inspections and independent runtime
   tests remain mandatory. `productionAppleCheck` remains unchanged locally.
3. **Five Mac slots maximum.** Three Release jobs plus the existing simulator
   runtime and desktop-x64 Mac jobs fit the documented Free limit. Native links
   never share a JVM; the local aggregate's memory safeguards remain unchanged.
4. **Dependency-only persistence.** SHA-pinned `gradle/actions/setup-gradle`
   caches only `caches/modules-2` and `wrapper/dists`. PRs and dispatches are
   read-only; only protected `main` pushes write. It does not persist workspace
   build outputs, reports, signing material, configuration-cache state, Konan
   binaries or optimized frameworks. Strict dependency verification remains
   mandatory. Cache misses/eviction must remain fully reproducible.
5. **Current evidence only.** The previously reviewed #268 exclusions remove
   historical `remediation-runs/**` and `docs/archives/**` from ordinary uploads,
   never current/new-module reports or external lifecycle custody.

No Kotlin/Gradle/AGP/analyzer version, Release optimization, application code,
Store workflow or repository protection is changed. Experimental Release binary
caching documented since Kotlin 2.4.20 is not enabled on the pinned 2.4.10.

## Benchmark protocol and status

Use full workflow dispatch at the same frozen head, selecting
`apple_release_runner=arm64`, then `intel`. Selection maps to two fixed standard
labels; arbitrary input can never select paid infrastructure. Avoid overlapping
benchmark workflows: account-level Mac contention would distort the result.

Compare complete workflow elapsed time **including queueing**, each lane's
elapsed time, analysis/link/wrapper steps, task execution counts, source-bound
artifact/cleanup results and cache hit/miss. Keep unsuccessful runs in the
ledger. Prefer the faster configuration only after all affected checks pass;
one run does not establish a statistical distribution or device performance.

**Hosted candidate measurements pending. No speedup is certified yet.** Local
regression checks and fresh source-inventory checks are being performed before
dispatch. This report will be updated with run URLs and measured results.

These are compilation/CI benchmarks, not physical iOS gameplay, memory, battery,
thermal, data-protection or Store signing/release qualification.
