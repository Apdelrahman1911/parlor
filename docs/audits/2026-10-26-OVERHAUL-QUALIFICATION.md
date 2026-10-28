# Production overhaul qualification — 2026-10-26

## Integration boundary

PR #256 is merged as `81ee9b7b5e1b3fad3c86b9d0cdb2e692d43276c7`.
The exact feature run [40148791277](https://github.com/Apdelrahman1911/parlor/actions/runs/40148791277)
and fresh main run [40156824703](https://github.com/Apdelrahman1911/parlor/actions/runs/40156824703)
passed all six mandatory lanes; all twelve cleanup receipts were checked.

The follow-up combines three reviewed workstreams through normal protected
[PR #263](https://github.com/Apdelrahman1911/parlor/pull/263):
[#261](https://github.com/Apdelrahman1911/parlor/pull/261) (`da13ff2f`, release
supervision), [#262](https://github.com/Apdelrahman1911/parlor/pull/262)
(`baab5f1d`, application overhaul), and aggregate `ba0c1e43` (real-kit verification,
expanded game acceptance and additional reproduced layout corrections).
Their actual ancestry, not merely equivalent patches, is present.

GitHub records all three PRs **MERGED** at 2026-10-26 23:19:28 UTC through
`d72a0d839cf0232e8c4fbf0940ccd1d614acbac4`. Verified parents are previous main
`81ee9b7b5e1b3fad3c86b9d0cdb2e692d43276c7` and aggregate
`ba0c1e439d0632fd516dc9660256606e3caaedb4`; the merge tree equals the frozen
aggregate tree below. No admin/protection bypass or Store publication occurred.

**Fresh merged-main verification passed:**
[run 40418964553](https://github.com/Apdelrahman1911/parlor/actions/runs/40418964553)
executed `d72a0d83`; all six mandatory jobs passed. At October 27 01:51:44 UTC,
the read-only checker independently verified all six exact-source ownership,
artifact digest and cleanup bindings, Android emulator absence, and four Apple
resource-retirement claim/stop records. PR qualification was not substituted for
this fresh merge execution. This does not certify subsequent diagnostic controls,
physical-device behavior or a production release.

### Per-PR qualification and sequencing correction

| PR | Frozen head | Successful full CI run | Executed attempt |
|---|---|---|---|
| #261 | `da13ff2f5f32aa2f511d694643d29ec110c62381` | [40352669569](https://github.com/Apdelrahman1911/parlor/actions/runs/40352669569) | All six lanes: 1 |
| #262 | `baab5f1d8cfeb6a3a131cdc9bf31544fa505d89e` | [40353098825](https://github.com/Apdelrahman1911/parlor/actions/runs/40353098825) | iOS Release: 2; other five: 1 |
| #263 | `ba0c1e439d0632fd516dc9660256606e3caaedb4` | [40363906241](https://github.com/Apdelrahman1911/parlor/actions/runs/40363906241) | All six lanes: 1 |

#262's first iOS Release attempt reached the actual 180-minute job deadline.
Its unchanged retry completed analysis/three links, Swift wrapper, unsigned
package inspection, upload and cleanup successfully. The other lanes were not
rerun; both attempts' immutable artifacts remain separate and preserved.

A final premerge helper accidentally selected an older five-lane #261
full-source packet. Its assertion failed, but the surrounding shell lacked
`set -e` and continued into the already authorized normal protected merge.
Before merge, all three six-lane current-head CI results and eighteen cleanup/
artifact metadata records passed; full #262/#263 source/Apple-cycle checks also
passed. The final full six-lane #261 source/Apple-cycle check finished **after
merge at 23:21:29 UTC**, all PASS. Do not claim the full check preceded merge.
Dependent mutations now stay inside checked Python subprocess flow or errexit
shells. Repository protections remained enforced; this is an execution-sequencing
error, not a waiver or fabricated check.

## Exact aggregate verification

Source: `ba0c1e439d0632fd516dc9660256606e3caaedb4`.
Tree: `832f2f779800ce6f1d5fb8a973969dd27bc92766`.

- Local `productionCheck allTests --continue --dependency-verification=strict`
  passed at 17:05 UTC: 1,776 Desktop cases (three existing real-LAN skips),
  646 Android unit cases per Debug/Release variant, 688 release Python cases
  (one local environment skip). Valid unchanged-input up-to-date outputs were
  reused; these counts do not mean every local test reran.
- [Hosted run 40363906241](https://github.com/Apdelrahman1911/parlor/actions/runs/40363906241)
  passed all six mandatory lanes. Current-module reports contain the same Desktop
  and Android counts, 107 additional GitHub-test variant unit cases, six managed
  Android device cases, 672 iOS Simulator KMP cases, ten Swift host/UI cases and
  18 Windows process-ownership cases. Platform repetitions are not unique behaviors.
- The six managed-device cases are three API34 navigation and three API35
  cold-start tests, not the 27 rows obtained by including archived reports.
- Hosted PR checkout `2ec543b2ffacd637a66b1a18c8fd8891eaa8643f` is a synthetic merge
  with the exact source tree above. Artifact digests, source/ownership claims,
  all six cleanup records and all four Apple native-cycle cleanup hashes were
  verified. Successful cleanup does not turn a failed build into a passing build.
- Apple used Xcode 26.3 / 17C529. Three Release framework links, the unsigned
  Simulator Swift wrapper and package/notices inspection passed. This is not
  signed Release runtime, hardware enforcement or Store qualification.
- The separately selected strict-protection diagnostic was not executed. Its
  skip is not a pass and does not resolve historical A37.

The tracked real-kit Linux command also passed ten invocations at `ba0c1e43`
(16:58–17:01 UTC): advertising once and approved join, authenticated bidirectional
messages and two-peer broadcast three times each. Each fixture uses one JVM with
separate identities and actual sockets; invocation teardown, natural JVM exit,
private Gradle stop, 1,054 runtime-file hashes and helper bytes were checked.
Nineteen harness regressions passed. This is not independent-process or
physical-phone LAN acceptance and does not close #6.

## Implemented changes

- **Security and authority:** bounded iterative JSON nesting checks before
  decoding; malformed-then-valid mailbox regression; game/version admission
  binding before credentials or admission confirmation. Host-only reducers,
  exact protocol 4.2, authenticated identity and recipient-only private slices
  remain enforced.
- **Persistence/recovery:** explicit confirmation-gated Resume deletion with
  separate snapshot/credential ownership, stale/cancelled callback protection
  and visible failure; every connection-tracker close caller now awaits cleanup.
  Lifecycle retention/clock/exception stress cases extend deterministic coverage.
- **Application design:** capability-correct catalog and filters, adaptive
  setup/join/lobbies/Settings, consistent headings and context components,
  recovery feedback and accessible confirmation flows.
- **All six games:** bilingual primers and clearer phase/action/result hierarchy;
  public/private vote guidance; private-cover and recipient-state consistency;
  phase-keyed save/scroll restoration. Additional acceptance includes 144 seeded
  Mafia traces, Domino differential rule variants and 132 Ghamza/Word reducer/
  codec recovery scenarios. These loops are not extra JUnit cases or network tests.
- **Reproduced layout defects:** enlarged Settings labels squeezed by switches,
  fragmented Domino opponent names, and host room codes wrapping at ordinary
  text size on 320dp. Baseline tests failed before adaptive fixes. Full font
  scaling, LTR room-code value, public semantics and private-data boundaries remain.
- **Release reliability:** scoped POSIX descendants/Windows Job Object ownership,
  bounded diagnostic reads and invocation-private Gradle registries. Native
  Windows, timeout/cancellation/overflow and unrelated-process-survival tests pass.
- **Dependency consistency:** removed a redundant compile-only compiler alias
  responsible for environment-dependent lint advisory presence. Compiler/analyzer
  pins, notice text and dependency verification remain intact; the reviewed
  catalog fingerprint was synchronized rather than weakening its validator.

## RPC and lifecycle decisions

Keep published P2pKit 0.7.0-rc3 and the existing single-connection protocol.
Published RPC starts in 0.8, owns a separate kit and has a different trust/network
policy. Upstream PR #524 adds readiness/capacity work, not initial RPC publication;
its source changes are absent from published 0.8.0-rc7. No executed comparative
mobile-host benchmark justifies a transport migration. This is an incompatibility/
benefit-evidence decision, **not a measured claim that RPC is slower**.

The existing 15-second socket-retention window is best effort, not an Android/iOS
execution entitlement. Fresh liveness and the original 120-second seat-recovery
boundary remain. Cold peer process startup still requires Resume; host process
death ends the room. No indefinite iOS LAN hosting, unrelated background mode,
unverified foreground service, host migration or automatic non-idempotent retry
was introduced. See [the platform/RPC assessment](RPC_AND_LIFECYCLE_2026-10-25.md).

## Fresh standalone protected-storage investigation

A lead review compared the diagnostic controls with `45df1cce`: only the reviewed
workflow changed; shipping writer/native diagnostic/ownership controls retain
their bytes. All 52 synthetic diagnostic tests passed. Source and all 20 controls
were rebound to the equal-tree merged source, control SHA256
`5cfd56cab744a310d5011b96af8c4ef0518c928e04b743c40eeb669650c3db0e`.
The preserved `fix/local-readiness-2026-09-07` branch was **non-force fast-forwarded**
to the merge, without changing its branch/source/ownership guards.

[Run 40419513891](https://github.com/Apdelrahman1911/parlor/actions/runs/40419513891)
selected only the standalone paired same-inode Simulator/host diagnostic.
Source/control Git blobs, raw Simulator/host/setter reports, ZIP digests and
cleanup were independently verified. Collection and cleanup PASS; strict
synthetic Complete **0 PASS / 5 FAIL**. Simulator FileManager key was missing;
same-inode host FileManager and file NSURL reported until-first-authentication.
Raw class3 and unsupported APFS content protection were observed. The one-call
host Complete setter returned false / Cocoa256 and retained class3; this fresh
record does not establish historical H03's underlying POSIX22.

This selection is neither Parlor runtime nor physical protection proof; green
collection cannot become a strict-protection PASS. The writer remains unchanged;
A37 and APP04 historical failures remain separate. Actual-app read-only preflight
[40419989333](https://github.com/Apdelrahman1911/parlor/actions/runs/40419989333)
verified source/control/artifact bindings but built no app. Later reproduced
diagnostic identity/seam/fixture drift is separately tracked in #265 and the
[repair report](2026-10-27-IOS-DIAGNOSTIC-CONTROLS.md). Revised controls require a
fresh committed-source preflight and reviewed hashes before application execution.

## Issue disposition and outstanding acceptance

The 207-title historical issue inventory and relevant bodies/PRs were reviewed;
this is not line-by-line review of every historical issue body. Findings and
implementation evidence reuse existing issues where applicable:

| Issue | Implemented/evidence | Remaining acceptance |
|---|---|---|
| [#251](https://github.com/Apdelrahman1911/parlor/issues/251) | Game/version-bound admission and regression tests | Physical cross-game join and both platform directions |
| [#253](https://github.com/Apdelrahman1911/parlor/issues/253) | Confirmed, ownership-safe Resume deletion | Native EN/AR/a11y relaunch and persistence journeys |
| [#257](https://github.com/Apdelrahman1911/parlor/issues/257) | Scoped release supervision; native Windows/Linux fault tests and fresh merged-main source/artifact/cleanup verification | **CLOSED** after all acceptance criteria passed |
| [#259](https://github.com/Apdelrahman1911/parlor/issues/259) | Catalog, shared flows and public/private guidance; enlarged-text fixes | Full native onboarding/create/join/recovery and screen-reader journeys |
| [#260](https://github.com/Apdelrahman1911/parlor/issues/260) | Game privacy/actions/results/RTL; explicit EN/AR Light/Dark Desktop interaction tests | Native complete sessions, fairness/pacing playtests and fluent Arabic editorial review |
| [#252](https://github.com/Apdelrahman1911/parlor/issues/252) / [#6](https://github.com/Apdelrahman1911/parlor/issues/6) | Lifecycle/fault tests and opt-in real-socket Linux fixtures | Android/iOS hosts/clients, mixed LAN, lock/calls/switching/Wi-Fi/Doze/force-quit/extended sessions |
| [#258](https://github.com/Apdelrahman1911/parlor/issues/258) | Preserved A37/H03 evidence and unchanged strict controls | Historical A37 0/26 unresolved; causal diagnosis, current strict/functional rerun, physical protected-storage behavior |
| [#265](https://github.com/Apdelrahman1911/parlor/issues/265) | Reproduced diagnostic seam/identity/fixture drift; regression repairs and macOS enforcement | Exact-head hosted verification and fresh reviewed application preflight; not A37 causality |
| [#230](https://github.com/Apdelrahman1911/parlor/issues/230) | Replacement identifiers are implemented | Account ownership, signing, protected environments and owner/legal/content/privacy approval |

CPU/RAM, allocation, threads, network latency, battery and thermal mobile-host
comparison remain unmeasured. Emulator/Simulator execution and Desktop screenshots
do not replace physical devices or TalkBack/VoiceOver. No production or public
store release was performed; all three Store workflows remain disabled.

## Evidence navigation

- [Verification ledger](2026-10-25-VERIFICATION-LEDGER.md): earlier checkpoints,
  failed/superseded executions, corrections and source boundaries.
- [Audit index](2026-10-25-AUDIT-INDEX.md): all 17 modules, issue inventory and risks.
- [Network/security](NETWORK_SECURITY_AUDIT_2026-10-25.md),
  [original games](ORIGINAL_GAMES_2026-10-25.md),
  [game acceptance](2026-10-26-GAME-ACCEPTANCE.md),
  [room-code accessibility](2026-10-26-ROOM-CODE-ACCESSIBILITY.md),
  [real-kit Linux](2026-10-26-REAL-KIT-LINUX.md),
  [release supervision](2026-10-25-RELEASE-SUPERVISION.md).

Local custody under `.git/agent-custody/overhaul-2026-10-26/final/` contains
source-bound logs, selected raw receipts, hashes and synthetic screenshots, but
is not included in a fresh clone. Linked exact-source runs/PR records are the
durable remote references; GitHub artifacts have finite retention.
