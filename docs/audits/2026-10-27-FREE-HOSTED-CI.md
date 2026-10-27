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

### Qualified ARM64 sample40532378656

[Run40532378656](https://github.com/Apdelrahman1911/parlor/actions/runs/40532378656)
passed at source `0cd9a9dc1c57e76cafe0d109659888cdd53f9095`, tree
`862062c8cdb868f43b9a40ac235a2ebf5dd9bb5d`. Independent read-only qualification
at16:52:25UTC checked all eight producer lanes, the original required Release
join, exact checkout/tree, retained uploads and actual native/output retirement.
All672 current KMP Simulator cases and all16 current Swift/container/UI methods
passed. This includes the invalid-geometry regression and English/Arabic game
journeys; it does not establish the original intermittent shipping-input cause
in #267. The opt-in strict protection diagnostic was skipped, not certified.

| Measurement | Minutes |
| --- | ---: |
| Complete workflow, including queueing | 75.88 |
| Simulator Release/Swift lane | 54.82 |
| Its queue offset from dispatch | 20.57 |
| Complete Apple static analysis | 2.87 |
| Normal optimized Kotlin/Swift Release wrapper | 50.28 |
| Device Release lane / optimized link | 54.32 / 53.20 |
| Simulator x64 Release lane / optimized link | 48.82 / 47.87 |

Relative to the171.48-minute baseline **Release job**, the54.82-minute
simulator Release job is approximately68% shorter. This is one qualified
sample, not a whole-workflow68% speedup, statistical/warm-cache distribution,
or mobile-runtime measurement. No Intel winner or merge is implied.

### Intel sample40545185630 — rejected before Release compilation

[Run40545185630](https://github.com/Apdelrahman1911/parlor/actions/runs/40545185630)
uses the identical source/tree. All three Intel Release jobs reject the native
ownership preparation because `DarwinWorkerBackend` explicitly allows only
ARM64 hosts. They do not execute the optimized links. The cleanup and required
join correctly retain/reject the failures: these short jobs are **not** Intel
speed measurements or evidence that standard Intel runners are unavailable.

The independent ARM64 runtime lane also fails before tests because the Gradle
wrapper cannot resolve `services.gradle.org` (`UnknownHostException`); its stop
attempt encounters the same bootstrap failure. No application runtime was
executed in that lane. This is separate from Intel admission; neither failure
is masked by blind retries, an alternative distribution or weaker cleanup.

The failed workflow completed in29.63minutes; all four Common/Android and
desktop lanes passed. Its2.25–2.62-minute rejected Release jobs contain no
optimized compilation and cannot be compared with the qualified ARM64 links.

[The guarded host-ABI port and supplemental native capability control](2026-10-27-DARWIN-HOST-ABI.md)
must qualify on both standard hosts before another full comparison. Keep ARM64
as the default meanwhile; rerun all affected full checks at the new frozen
source before selecting/merging. Retain failed samples, including missing
artifacts and unresolved DNS evidence, rather than relabeling them successful.

The supplemental control subsequently passed on both hosts at `559793e6`
([40551462094](https://github.com/Apdelrahman1911/parlor/actions/runs/40551462094));
its source-bound native/ABI/reaping and downloaded artifact receipts were
independently verified. It qualifies host capability, **not** Release compilation,
runtime, final architecture selection or a merge. The later Kotlin job-registry
test integration fix leaves these workflow/native-control bytes unchanged.


### Qualified fresh-source ARM64 sample40553214698

[Run40553214698](https://github.com/Apdelrahman1911/parlor/actions/runs/40553214698)
passed at frozen source `711cc1abe4aad7d32911fe7357043cda8f54e74b`, tree
`96b8b189041a2431bea600a59c43e075703047c3`. Read-only qualification
independently verified all eight producer lanes, the original required Apple
join, exact source/tree, uploaded artifact digests and native/output cleanup.
All672 current KMP Simulator cases and16 Swift/container/UI methods passed.
Optional protection and native-capability diagnostics were correctly skipped;
the separate unchanged-byte native control retains its own559793e6 custody.

| Measurement | Minutes |
| --- | ---: |
| Complete workflow, including queueing | 55.50 |
| Simulator x64 Release lane / optimized link | 54.67 / 53.48 |
| Simulator arm64 Release/Swift lane | 54.32 |
| Complete Apple static analysis | 3.12 |
| Normal optimized Kotlin/Swift Release wrapper | 49.63 |
| Device arm64 Release lane / optimized link | 50.32 / 49.13 |
| Runtime lane / queue offset | 42.67 / 8.13 |
| macOS desktop lane / queue offset | 16.27 / 35.37 |

The longest Release **job** is approximately68% shorter than the171.48-minute
baseline Release job. A separate read of all six successful baseline jobs also
establishes **173.33minutes complete elapsed time**, including queues, versus
**55.50minutes** for this candidate (about68% shorter). This whole-workflow
observation compares older3806921f CI with711cc1ab, not identical-source
profiles or a statistical distribution. The three candidate Release logs each
show a dependency-cache miss; no warm-cache benefit or mobile CPU/memory/battery
result is claimed. Cache writes remain disabled for these dispatch samples.

The first external qualifier stopped after four lanes because its copied
script pointed at an obsolete local checkout. This was **not a CI failure**.
Its failed observer record and partial evidence remain retained. The repaired
read-only qualifier requires an explicit checkout and checks clean HEAD/tree
against the run before and after all original artifact/runtime assertions.
Existing successful ARM64 execution was requalified without rerunning CI or
changing its source. Intel was dispatched exactly once only after qualification:
[40563194924](https://github.com/Apdelrahman1911/parlor/actions/runs/40563194924),
at the identical frozen head/tree. At this documentation checkpoint Intel is
pending; ARM64 remains the default and no fastest-profile or merge claim is made.

The separate final-integration branch adds the already tested #272 inventory
CLI correction and this evidence ledger while leaving the benchmark source
frozen. Those edits need their own affected local and fresh protected PR checks;
the711cc1ab workflow does not qualify a newer integration head.


### Independent integration-head strict local verification

At `08a87d6aca2b4b3688ac3fe605ab3cf7c93422ee`, tree
`70c393884fdfb26490e9f36dc7b0176f2152519f`, the separate final-integration
checkout passes strict `productionCheck allTests --continue --no-daemon` in
3m11s; Gradle stop0 and source unchanged. It has3,187 current module report
cases (Desktop1,788, Android646 per variant, GitHub-test107), zero failures/errors
and three existing real-LAN skips. Release Python749 controls pass with one
known local-environment skip; Detekt/lint/R8/unsigned AAB, current inventory and
pinned ShellCheck/Actionlint pass. Of1,668 actionable tasks,988 executed and680
restored valid cache outputs. This is not wholly fresh test execution or Apple,
physical-device, signing, protection or protected-merge qualification.

Log SHA256 `97a254d0fbe2e1d8a315db1fcbc54f42eadfadc7a72f6f7f2819ea1a3a8a493f`.
Documentation recorded afterward requires affected source-contract checks again;
no older receipt certifies a newer tree. The benchmark remains frozen at711cc1ab.
