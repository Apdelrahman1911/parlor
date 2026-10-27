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
4. **Dependency-only persistence.** SHA-pinned GitHub-owned
   `actions/cache/restore` and `actions/cache/save` cache only Gradle
   `caches/modules-2` and `wrapper/dists`. Keys isolate OS, architecture, JDK,
   Gradle, Kotlin, Xcode and the reviewed dependency/verification graph; there
   are no broad fallback keys. PRs and dispatches are
   read-only; only protected `main` pushes write. It does not persist workspace
   build outputs, reports, signing material, configuration-cache state, Konan
   binaries or optimized frameworks. Strict dependency verification remains
   mandatory. Cache misses/eviction must remain fully reproducible.
5. **Current evidence only.** The previously reviewed #268 exclusions remove
   historical `remediation-runs/**` and `docs/archives/**` from ordinary uploads,
   never current/new-module reports or external lifecycle custody.

No Kotlin/Gradle/AGP/analyzer version, Release optimization, shipping application code,
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

### Retained failed attempts and integration corrections

1. [40510032203](https://github.com/Apdelrahman1911/parlor/actions/runs/40510032203)
   was rejected at startup with zero jobs: repository policy denied the
   Gradle-owned caching action. GitHub-owned cache actions replace it; no policy
   was loosened. This is not a runner-speed measurement.
2. [40510904388](https://github.com/Apdelrahman1911/parlor/actions/runs/40510904388)
   runs frozen `b9d6f780` on standard ARM64 Release workers. Common/Android and
   the three desktop host lanes pass. KMP Simulator672 passes; Swift/native is
   9passes/1failure at Arabic Egyptian Dominoes pre-tap settlement (#267).
   See [the source-bound investigation](2026-10-27-IOS-SETTLEMENT.md): no Host
   gesture was synthesized and no application touch root cause is established.
   Runtime cleanup independently passes **with the failed run outcome retained**.
3. The same sample's device optimized Release link **builds successfully in
   45m17s**, then fails at the new architecture check. `lipo -verify_arch`
   consumes the remaining arguments as architecture names, so placing the
   binary after `arm64` is invalid. The x64 lane has the same argument-order
   defect. Device log SHA256:
   `cb5d3bbac009987a49c9a953c96c9ce2efa16f80617e2a0400bec6ca7042dea0`.
   Both commands now put the binary **before** `-verify_arch`. The executable
   workflow contract requires the exact target, optimized strict Gradle link,
   nonempty binary, architecture check, `file` metadata and SHA256 output.
   Regression mutations reject the original order, wrong target/architecture,
   removed evidence and `|| true`; architecture verification is not dropped.

Simulator Release starts16.82min after dispatch; include this queue offset in
reported elapsed time, without inferring a different account limit. Remaining
Release-worker timing/evidence is retained as a **failed-workflow sample**, not
a qualified speedup or winner. The observer cannot dispatch Intel after failure.

The integration candidate also includes previously independent, locally verified
artifact/source scope and relocated-lint-cache fixes (#268/#269/#271), and the
bounded single-snapshot public XCTest sampler. It does not mutate the already
frozen `b9d6f780` branch or relabel its evidence. Fresh full checks and identical
new-head ARM64/Intel samples are required before selecting/merging a configuration.
Swift helper compilation/execution awaits macOS; local source-contract tests do
not substitute for it. All10 existing native/UI cases and six new frame-state
cases remain required. **No speedup or fixed native-input root cause is certified.**

### Corrected-source ARM64 sample40521388558 — not qualified

[Run40521388558](https://github.com/Apdelrahman1911/parlor/actions/runs/40521388558)
executed frozen `c02ad9e2` and failed. Device arm64 and simulator x64 optimized
Release links **and their corrected architecture checks passed**. Common/Android,
all three desktop lanes and all672 KMP Simulator cases passed. All10 original
Swift/container/UI cases passed; one new invalid-geometry method failed because
the sampler admitted the finite-valued `CGRect.infinite` sentinel. Its guard is
corrected without removing the test; see the settlement report above.

The simulator Release lane passed complete static analysis, optimized Kotlin
framework/Swift compilation, binary and package inspection, and both native
resource retirements. Its evidence upload and later cleanup-receipt upload
failed separately with **`Failed to CreateArtifact ... ENOTFOUND`**. Only32
current files were selected for upload. This is a runner-side DNS/request failure,
not a compiler failure or evidence of a storage/billing quota. Endpoint identity
and underlying DNS cause were not exposed. Output cleanup correctly refused to
delete required evidence after the failed upload; the required join rejected
the failed lane. No uploaded simulator Release custody exists for this attempt.

The complete run is retained as a failed timing sample. No Intel comparison
was dispatched: the observer stopped nonzero during transient log retrieval,
and subsequent bounded read-only retrieval preserved the logs/runtime artifact.
No benchmark winner, merge qualification or paid infrastructure is claimed.
Rerun full verification at the corrected frozen source, preserving the missing
evidence/cleanup gates rather than masking uploads or replaying application actions.

These are compilation/CI benchmarks, not physical iOS gameplay, memory, battery,
thermal, data-protection or Store signing/release qualification.
