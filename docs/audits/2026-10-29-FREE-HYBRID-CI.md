# Free-hosted hybrid CI experiment

## November 19 Phase 1 baseline

These samples come from the Production verification workflow and its Actions
Jobs API. Wall time runs from workflow creation to the last job's finish.
Start delay is measured from creation and includes scheduling and runner
assignment. Dependency joins also wait on their producers.

| Run | Outcome | Wall time | Last prerequisite |
| --- | --- | ---: | --- |
| [43016537257](https://github.com/Apdelrahman1911/parlor/actions/runs/43016537257), main `7affb37c` | Pass | 68m50s | x64 Release, 15m53s start delay + 52m33s job |
| [42997452058](https://github.com/Apdelrahman1911/parlor/actions/runs/42997452058), main `f53767b2` | Pass | 65m45s | Release wrapper, 16m19s start delay + 49m02s job |
| [42997955471](https://github.com/Apdelrahman1911/parlor/actions/runs/42997955471), PR #340 | Pass | 114m58s | Device Release, 63m43s start delay + 50m53s job |
| [42997962768](https://github.com/Apdelrahman1911/parlor/actions/runs/42997962768), PR #341 | Fail | 161m28s | Release wrapper, 93m40s start delay + 67m22s job. Swift failed inventory before tests. |

The two November 11 PR runs started three seconds apart. Use them as observed
contention cases. GitHub documents a five-macOS-job concurrency ceiling for
the standard free profile, while a full Parlor run schedules six macOS jobs.
The API gives the delay but does not identify all account or provider
contention. [GitHub Actions limits](https://docs.github.com/en/actions/reference/limits)

| Latest main job | Start delay (min) | Job (min) | Main work from steps or logs |
| --- | ---: | ---: | --- |
| Common, Desktop and Android | 0.03 | 39.12 | 19m06s aggregate, 11m52s managed-device smoke, 4m43s recovery, 1m20s SDK setup |
| Desktop Linux arm64 | 0.07 | 5.60 | 4.90 min host-selected build and test |
| Desktop macOS x64 | 0.08 | 19.60 | 16.62 min host-selected build and test |
| Desktop Windows x64 | 0.05 | 10.42 | 8.30 min host-selected build and test |
| KMP iOS simulator arm64 | 0.08 | 15.77 | 13m03s Gradle aggregate within a 14m24s step |
| Swift host arm64 | 27.38 | 39.05 | 37m22s Simulator/Xcode step, including 17m27s Gradle embed and 15m17s XCTest suite |
| Release simulator and Swift wrapper | 0.62 | 47.18 | 43m35s wrapper build including 42m45s Gradle embed, plus 2m02s Apple analysis |
| Release device arm64 | 0.10 | 41.43 | 40m03s Release-link step |
| Release simulator x64 | 15.88 | 52.55 | 51m03s Release-link step, 50m54s Gradle build |
| Apple runtime join | 66.47 | 0.35 | Both simulator runtime producers |
| Apple Release join | 68.47 | 0.37 | Three optimized Release architecture producers |

The x64 Release log reports an exact dependency-cache hit and all 136 Gradle
tasks executed. The visible link-task line and successful-build banner are
46m45s apart. This is a log-delimited interval that includes finalization.
The Release wrapper reused 40 tasks within its job; it executed 179, with the
Gradle embed accounting for most of the 43m35s build step. The Swift Debug
build also hit the dependency cache, yet executed all 219 Gradle tasks.
The observed Gradle dependency-cache restores on the three ARM64 Apple jobs
above took 14–23 seconds. The latest wrapper's explicit package inspection
took eight seconds. On the latest Linux job the managed-device Gradle
invocations reused 606 Release and 294 Debug up-to-date tasks respectively.
Splitting them before timing the extra build and runner work has no measured
critical-path benefit.

The existing logs do not isolate exact per-task timings for buffered Xcode
output or all simulator startup costs. The candidate adds the Xcode timing
summary to the existing Release wrapper log. Use a local Gradle profile for
configuration, resolution and task timings. Distinguish its clean-output and
warm-repeat samples from a cold dependency cache and from hosted Apple builds.
The project already enables Gradle parallelism, configuration cache and build
cache. The hosted cache stores strict-verified dependency and wrapper files,
with separately keyed host architectures and writes limited to trusted main.
The full Release lanes still execute optimized linking on a clean runner.
Changing Release compiler optimization or admitting reused test reports would
change the product or its evidence. A remote build-output cache would require
trusted writers, complete cache key and relocation checks, and a fresh
qualification of the affected gates. [Gradle profiling](https://docs.gradle.org/current/userguide/inspect.html) ·
[Kotlin/Native compilation](https://kotlinlang.org/docs/native-improving-compilation-time.html) ·
[GitHub cache scope](https://docs.github.com/en/actions/reference/workflows-and-actions/dependency-caching) ·
[Xcode timing](https://developer.apple.com/documentation/xcode/improving-the-speed-of-incremental-builds)

### Local Gradle baseline

On Linux x64 with JDK 21, Gradle 8.13 and a fresh output tree at `7affb37c`,
the focused session suite passed all 176 methods. The dependency cache was
already populated. With task output caching disabled, Gradle executed 24 tasks
in a 78.228-second wall-clock build.

| Gradle profile entry | Clean-output run | Warm unchanged repeat |
| --- | ---: | ---: |
| Total wall time | 78.228s | 7.155s |
| Project configuration | 27.535s | 0s (configuration cache reused) |
| Dependency resolution | 0.305s | 0s |
| Reported task execution | 44.533s | 0.434s |
| Session main Kotlin compilation | 6.151s | Up-to-date |
| Session test Kotlin compilation | 8.557s | Up-to-date |
| Session Desktop tests | 3.016s | Up-to-date |

The warm run executed six tasks and reused 14 outputs. Reproduce the pair in a
clean worktree with the same dependency cache, running this command twice
without changing inputs. Gradle writes the two reports under
`build/reports/profile`.

```sh
xvfb-run -a ./gradlew :shared:session:desktopTest --dependency-verification=strict \
  --profile --no-build-cache --no-daemon --max-workers=1 \
  -Dorg.gradle.jvmargs=-Xmx2200m -Pkotlin.compiler.execution.strategy=in-process --console=plain
```

### Hosted reproduction

Reproduce the hosted ledger with:

```sh
repo=Apdelrahman1911/parlor
run=43016537257
gh api "repos/$repo/actions/runs/$run" --jq '{head_sha, run_attempt, created_at, conclusion}'
gh api "repos/$repo/actions/runs/$run/jobs?per_page=100" \
  --jq '.jobs[] | {id, name, conclusion, started_at, completed_at, steps: [.steps[] | {name, conclusion, started_at, completed_at}]}'
gh api "repos/$repo/actions/jobs/130508941691/logs"
```

For the candidate, first qualify the automatic ARM64 PR run. Then dispatch
full ARM64 and hybrid profiles sequentially at the identical branch head
with no other Parlor workflows overlapping. Use the dispatch pair for the
comparison because the automatic PR run checks out GitHub's test merge.
Record run timestamps, runner architectures, cache hits or misses, compiler
and wrapper durations, and all producer and cleanup receipts. Verify all nine
producers and both joins. Repeat successful comparable samples before changing
the default. The previous older-source Intel result showed a potential x64
benefit, but the starting optimized link and wrapper times still exceed
30 minutes without queueing. Set the first target from matched results.

## Status and constraints

**Prepared, not yet benchmarked.** Keep `arm64` as the default and the fastest
fully qualified configuration. The earlier matched-source comparison was
ARM52.55 minutes versus whole-Intel107.72 minutes; Intel's x64 link was faster,
but its wrapper and queues made the complete workflow slower. See the
[existing benchmark ledger](2026-10-27-FREE-HOSTED-CI.md).

Official GitHub documentation and the public-repository API were rechecked
October29 at23:31UTC: standard public-repository runner compute is free;
GitHub Free allows20 concurrent standard jobs, including five macOS jobs.
`macos-15` (M1,3 cores/7GB) and `macos-15-intel` (Intel,4 cores/14GB) are both
standard runners. Capacity and queue time are not guaranteed. This does not
assert unlimited free artifact storage. No billing, cache-cap, artifact
retention or infrastructure settings change.

- [Standard runners](https://docs.github.com/en/actions/reference/runners/github-hosted-runners)
- [Concurrency limits](https://docs.github.com/en/actions/reference/limits)
- [Actions billing](https://docs.github.com/en/billing/concepts/product-billing/github-actions)

## Opt-in implementation

`workflow_dispatch` adds `apple_release_runner=hybrid`: **only** the simulator
x64 optimized Release lane uses `macos-15-intel`. The device-arm64 and
simulator-arm64/Swift Release lanes stay ARM; independent runtime and desktop
host lanes are unchanged. Automatic PR/push runs still use the original ARM
default. Unknown values cannot select paid or arbitrary labels.

All architectures, Release optimization, strict dependency verification,
static analysis, runtime tests, source/artifact custody, resource cleanup and
the original required Apple join remain mandatory. Xcode26.3 stays pinned.
There is no build-output caching or verification shortcut. Regression tests
evaluate actual workflow selectors and reject altered defaults, paid labels,
retargeted lanes and weakened compilation/join checks.

## Matched-source acceptance protocol

1. Integrate normally with qualified main; freeze the exact clean source/tree.
2. Independently qualify its fresh automatic ARM full run.
3. After other Parlor workflows finish, dispatch exactly one **full** hybrid
   run at the identical frozen source. Record misses/hits and retained failures.
4. Require all eight producers, the original Apple join, current source-derived
   native/readiness methods and source/upload/native/output custody in both.
5. Compare whole-workflow elapsed time including queues, individual lanes and
   link/wrapper steps. Never infer phone performance or a statistical guarantee.

Do not change the default unless the complete measured hybrid configuration is
both faster and qualified. A default change then needs fresh affected-source
PR and post-merge checks; the earlier benchmark cannot certify newer code.
