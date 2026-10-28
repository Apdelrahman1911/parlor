# Whodunit exhaustive-trace timeout — 2026-10-28

## Confirmed failure and scope

[PR #266](https://github.com/Apdelrahman1911/parlor/pull/266), source `1761ad2d`,
[run 40595428550](https://github.com/Apdelrahman1911/parlor/actions/runs/40595428550),
is **failed**, not merge-qualified. The first macOS Desktop attempt builds/tests
successfully, then evidence CreateArtifact times out (`ETIMEDOUT`). Its failed
cleanup preserves required evidence. One guarded specific-job rerun is accepted;
no expensive Release job is re-executed.

The retry's upload and cleanup pass, but [macOS job 122535156315](https://github.com/Apdelrahman1911/parlor/actions/runs/40595428550/job/122535156315)
fails `TestingStoryGameTraceTest` with `UncompletedCoroutinesError: After waiting
for 1m, the test body did not run to completion`. The source/digest-verified XML
records one failed case, **73.435 seconds**. This is a **P1 verification blocker**,
not a demonstrated gameplay defect or coroutine leak. See [#274](https://github.com/Apdelrahman1911/parlor/issues/274)
and the parent [CI ledger #270](https://github.com/Apdelrahman1911/parlor/issues/270).

## Root cause and preserved coverage

Seven bundled cases × two modes × six possible killers = **84 combinations**.
Each is played twice: **168 full-game traces**, including replay, per-transition
snapshot encoding/decoding and loaded-case recovery validation. All previously
shared one default 60-second `runTest` wall deadline. That aggregate budget couples
independent scenarios to host throughput. The underlying CPU/GC/scheduling cause
of this slower hosted sample was not measured and must not be invented.

The implementation registers **14 named JUnit5 case/mode tests**, each retaining
the default coroutine deadline. Every killer, original deterministic repetition,
valid six-seat fixture, reducer action, codec/recovery assertion, verdict and
replay check remains. A fifteenth metadata test enforces seven cases, both modes,
14 unique rows and the original 84-combination envelope. The existing JUnit5 API
is already in the strict dependency graph; no dependency or runner is added.

## Execution and fault-injection evidence

| Actual observation | Result |
| --- | --- |
| Original hosted Windows trace | PASS, 20.295s |
| Original hosted Linux ARM64 trace | PASS, 8.490s |
| Exact-source original focused local rerun | PASS, 5.748s |
| Revised focused local execution | 15/15 PASS, 7.482s total |
| Original + controlled 10ms/action pacing | FAIL, aggregate timeout, 123.229s |
| Revised + identical pacing | 15/15 PASS, 122.633s total; longest case 10.273s |

The temporary pacing injection changes no input, action, seed, case, mode or
assertion and is **not committed**. It demonstrates the harness-budget defect,
not real macOS/device throughput or improved computation speed. Similar paced
total duration confirms that coverage was not removed. Normal focused time is
slightly longer, not advertised as a speedup; avoiding false whole-job failures
is the reliability benefit. A deliberately omitted-row control **fails the metadata guard** (one expected
failure: expected 14 rows, actual 13). Its original XML/log is retained. A
driver-only message-format assumption was corrected against that existing XML;
no test was retried or the failing control relabeled as passing source.

## Evidence custody and honest attribution

Failed retry log SHA256:
`ce8fd9236b44f6b7dc8bc28102d82f188416fe544c75dfb947cc15ff39d3643f`.
Failed test artifact **12779674173**, SHA256:
`3d7bd3e6931c19846c02fcad8b1e56a151711fdc167c28d8a8eb51dac0e6d99b`.
Successful cleanup artifact **12779743934**, SHA256:
`053cd0b10353b5e8f5d58f3c48101d53edb380339b139fe76c9d6645c6c6f1b6`.
Cleanup PASS does not certify a failed test.

Independent read-only checking preserves **seven unaffected producers plus the
original Apple join**, exact source, artifact digests, native/output retirement,
672 KMP Simulator cases and 16 Swift methods. The partial receipt explicitly
says `full_run_qualified=false`; SHA256:
`4699ba14d72d07f086cf8fb986b0feb74257a8d34fa716cd393691411f8c602b`.
GitHub rematerializes API job IDs/attempt fields for carried-over successes;
every original execution/step timestamp is unchanged. Uploaded ownership
receipts, not those API fields, establish their actual executed attempt 1.

PR checkout/join source is merge commit `647cf581`, whose parents are reviewed
head `1761ad2d` and main `9d4f369d`, with the exact reviewed tree. An operational
reader's head-only join assumption was corrected using the same source/tree/
parent proof as producer claims, plus identical producer/join checkout. Its
initial assertion failure and original reader remain retained, not relabeled CI
failures or successful full qualification.

## Remaining gates

Fresh full strict local and complete hosted qualification at the committed fix
are required before normal protected merge and fresh main verification. All
architectures, Release optimizations, reports, dependency verification, privacy,
cleanup and required checks remain. Free standard ARM64 remains the default;
failed Intel timing is not a qualified winner. No extra agent, paid infrastructure,
billing/cache-cap/protection change or Store release is used.

Original native-input #267, strict protection #258, cold-process restoration,
physical mobile LAN/background/accessibility/power/thermal testing, signing/legal,
Store identity and release authorization remain separate. This Desktop test-only
change does not resolve or certify those scenarios.
