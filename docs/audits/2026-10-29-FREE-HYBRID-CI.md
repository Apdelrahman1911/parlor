# Free-hosted hybrid CI experiment

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
