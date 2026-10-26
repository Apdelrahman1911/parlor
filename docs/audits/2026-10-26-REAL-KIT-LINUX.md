# Controlled Linux real-adapter evidence — 2026-10-26

## Scope and result

At clean source `d0f693756e00c07c69c8f364b08e32f2a378681f`, an isolated
Linux worktree compiled the exact `desktopTest` runtime classpath using the
wrapper, JDK 21.0.12 and strict dependency verification (27 seconds; cached
compilation tasks reused their source-bound inputs). An external Java helper
then explicitly called the existing adapter fixtures without editing their
`@Ignore` annotations or changing CI selection.

**13/13 experiments passed:** one advertisement, four approved-join runs, four
bidirectional-message runs and four two-peer broadcasts. Every fixture teardown
passed; every JVM exited naturally with code 0, without a timeout or forced
cleanup. Each invocation used the fixture's unique app namespace and separate
synthetic in-memory device identity/credential stores.

| Repeated scenario (three additional fresh JVMs each) | Passes | Whole-case elapsed range |
| --- | ---: | ---: |
| Explicit host approval, peer join and host membership | 3/3 | 11.808–12.173 s |
| Peer→host actor binding and direct host→peer delivery | 3/3 | 11.912–13.439 s |
| Broadcast delivered to two distinct admitted peers | 3/3 | 14.392–14.927 s |

The roundtrip fixture verifies that an untrusted payload's forged actor is
replaced by the authenticated connection's player identity. Broadcast verifies
three distinct identities and receipt by both peers. These are transport
assertions, not game-rule, hidden-projection or session-start verification.

## Actual sockets and resource observations

Only socket inodes held by the owned Java child were sampled. Peer cases held
two established TCP descriptors; two-peer cases held four. Host listening TCP
and mDNS UDP sockets were also observed. No endpoints, credentials or private
player content were recorded in the receipt.

For the nine repetition runs, sampled whole-JVM CPU was 4.92–7.88 seconds,
peak sampled RSS 234.5–259.4 MiB and sampled native thread count 50–55. These
include several synthetic devices, JVM startup/JIT, cryptography and cleanup.
Shared-host contention was uncontrolled. **These are not packet latency,
per-mobile-host cost, battery/thermal results or an RPC comparison.**

Pinned Maven artifacts actually executed:

- `p2p-core-jvm-0.7.0-rc3.jar` SHA-256:
  `4ffb18b77cf55900ab8210c0e382bef9a25421de9196caa326d79c2da1cb5593`
- `p2p-transport-lan-jvm-0.7.0-rc3.jar` SHA-256:
  `571d8464b069f42e073244194fc338d3199ea5e9e63b0ba600a19e17058fcb1d`

Private local custody: `.git/agent-custody/lan-experiment-2026-10-26/`
(`compile.log`, initial `receipt.json`, `repeats/receipt.json`, helper sources and
per-case logs). An initial init-script configuration mistake targeted the
included build; it failed before tests, was corrected, and its failure log is
retained. It was not an application failure or counted pass.

## Reproducible opt-in runner and remaining gates

[`scripts/verification/real-kit-lan/README.md`](../../scripts/verification/real-kit-lan/README.md)
describes the committed Linux-only runner, explicit LAN consent, strict
preparation, allowlisted fixture entry points, owned process/Gradle cleanup,
bounds and source/artifact receipts. Its own regression suite requires no LAN.
Normal CI and all three existing multi-kit `@Ignore` annotations remain intact.

This disproves a blanket assumption that same-machine peer verification is
impossible in this Linux environment. It does **not** prove Windows multicast
reliability, physical-device discovery, Android/iOS hosting, Wi-Fi changes,
background recovery, force-quit recovery, long-session stability or gameplay
correctness. Issue [#6](https://github.com/Apdelrahman1911/parlor/issues/6) and the
physical-device acceptance plan remain open. The automated runner must itself
be executed at a clean committed source before its output is claimed verified;
the 13 experiments above precede that implementation. The following separate
receipt verifies the committed runner itself.

## Committed-runner verification

At clean commit `1b468e9ff55f77fa2ce61344cfb838faa448f183` (tree
`ce069ff35fddc5f816cb3f225fecd1fde2c5a4da`), the documented default command
completed **10/10 cases**: one advertisement and three repetitions of each peer
scenario. Execution was 2026-10-26 15:33:41–15:36:15 UTC (about 153.5 seconds,
including preparation). Strict Gradle preparation passed in 29 seconds; its
invocation-private registry stop also passed. All ten fixture assertions,
teardowns, natural JVM exits (code 0), and owned cleanup checks passed.

The runner hashed 1,054 executed runtime files before and after execution,
rechecked its separately compiled Java helper bytes, and verified that the
source commit/tree stayed clean and unchanged. The final receipt status is
`passed`. Its SHA-256 is
`4cdd7a5c98023cc8772f5378a9be17f5f8f8ef9fddec6c9e7782abe1d8f683e5`;
local path in the isolated worktree:
`build/real-kit-lan/20261026T153341Z-1995123c/receipt.json`.

At that same commit, **19/19 Python regression tests passed**. They include
actual owned TERM-resistant descendants, timeout/overflow/cancellation cleanup,
unrelated-process survival, cleanup after work-budget expiry, real Git source
mutation, incremental traversal bounds/deadlines, and helper-hash mutation.
Missing LAN consent was separately checked to fail rather than start a probe.

The code was reviewed before publication; subsequent report/inventory-only
commits do not become independently executed sources merely by carrying these
receipts. Full required PR CI and physical-device acceptance remain separate.
