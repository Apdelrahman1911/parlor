# Production overhaul: inventory, issue disposition, and release-tool audit

## Evidence boundary

Review date: 2026-10-25. Baseline reviewed: `39aacd310acc92deb91f4e1742c827972dba3e35` (`origin/feat/last-light` at review). This report is one workstream's evidence, **not an exhaustive behavioral qualification, production approval, or a replacement for the other agents' runtime results**. Source and live API state override historical prose. No store workflow, package publication, signing operation, or protection change was performed.

The GitHub inventory contained 207 issues (open and closed) and 49 PRs. All titles/statuses were reviewed for deduplication. Bodies/comments of relevant #230, #6, #251, #252, #253, #128, #19, #138, #126 and PR #255 were inspected. This is not a claim of line-by-line examination of every historical issue body. Existing known findings should be updated, not mass-reopened or recreated.

## Nine-phase execution map

These are scope assignments/status boundaries at this report's checkpoint; the lead must append exact-SHA integration and test outcomes before final delivery.

| Owner phase | Workstream / current evidence | Remaining qualification |
|---|---|---|
| 1. Fix/merge PR #256 | Lead owns current CI failure reproduction, corrective commits, required checks and merge | Fresh required checks, actual merge SHA and post-merge CI; not certified here |
| 2. Comprehensive architecture/behavior audit | This module/issue/release index plus separate network, storage, UI and game investigations | Source inventory is not complete runtime behavior; private security findings remain restricted until mitigation qualified |
| 3. P2pKit RPC | `RPC_AND_LIFECYCLE_2026-10-25.md` from RPC agent: published/API compatibility and incremental-adoption assessment | No production dependency switch without demonstrable benefit and reproducible platform verification; mobile benchmarks unavailable |
| 4. Android/iOS background behavior | Lifecycle agent's current-policy review and added lifecycle stress/exception tests | Actual phones, both host directions, sleep/lock/calls/network/process-death, power/thermal measurements |
| 5. Six-game mechanics | Original-games and new-games workstreams, module-specific tests/fault scenarios | See their exact receipts; no blanket game rule correctness claim from this inventory |
| 6. Issue management | 207-title historical dedup; #230/#6 comments; new #257 and #258; existing #251/#252/#253 retained | Link integrated fixes and verification before closing; public security detail remains gated |
| 7. App UI/UX redesign | UX shell/design-system workstream; capability-aware catalog and responsive/recovery flows | Rendered full journeys, EN/AR, native accessibility and large text on supported devices |
| 8. Per-game UI | Six-game agents; original-game public-vote copy correction and per-game affordance improvements | Native game journeys, hidden-information covers, turns/results/rematch and shared design consistency |
| 9. Final performance/reliability/security verification | Lead aggregates focused tests and repository mandatory gates; this slice executes 127 Python tests | Windows Job Object runtime; final exact-SHA full CI; physical performance/privacy/lifecycle and signing/owner gates |

## Module and behavioral inventory

Counts below describe tracked source/test **files**, not tests executed or unique behaviors. Test files are Kotlin/Swift paths containing a test directory; shared fixture modules consequently count as test support. All 17 modules are included in `settings.gradle.kts`.

| Module | Kotlin files / test files | Responsibility / critical scenarios | Audit qualification in this workstream |
|---|---:|---|---|
| `composeApp` | 117 / 43 | Composition-root registry; startup, locale/settings, catalog, create/join/resume/navigation, native lifecycle | Inventory only; shell agent owns execution |
| `shared/core` | 11 / 2 | IDs, value types, shared errors/results | Inventory only |
| `shared/engine` | 21 / 5 | Pure definitions/reducers, player/public projections; topology independence | Architectural contracts reviewed; not rerun |
| `shared/engine-testing` | 4 / 4 | Non-shipping game/registration fixtures | Inventory only |
| `shared/session` | 29 / 15 | Host authority, start barrier, shadow peer state, synchronization and replay rejection | Critical invariant inventory; network agent owns fault tests |
| `shared/networking` | 27 / 8 | Exact protocol 4.2, room messages/validation, transport interfaces | Inventory; compatibility/privacy remain mandatory |
| `shared/networking-testing` | 4 / 4 | Non-shipping fake buses and sessions | Test infrastructure, not real-LAN evidence |
| `shared/transport-p2p` | 48 / 29 | Sole P2pKit boundary: authenticated discovery/admission, identity, bounded outbox, liveness/recovery | Actual loopback source inspected; three multi-participant tests ignored |
| `shared/storage` | 13 / 4 | Platform-protected snapshots/settings and distinct rejoin credentials | Inventory; strict iOS protection unresolved |
| `shared/content` | 18 / 5 | Bundled offline cases and validation | Inventory; no remote content assumption |
| `shared/design-system` | 68 / 22 | Typography/theme, reusable controls, accessibility, RTL/localization | Inventory; UX agent owns implementation/tests |
| `game-modes/whodunit` | 129 / 68 | Mystery content, clues, voting, private projections, local and multiplayer | Original-games agent; no execution claim here |
| `game-modes/mafia` | 93 / 39 | Roles/night actions/discussion/public votes, local privacy, terminal state | Original-games agent; no execution claim here |
| `game-modes/last-light` | 108 / 38 | Bluffing/cards, turns/recovery, private hands | Original-games agent; no execution claim here |
| `game-modes/egyptian-dominoes` | 34 / 9 | Default/Draw/Block, target score/shutout/teams, private tiles; multiplayer-only | New-games agent; no execution claim here |
| `game-modes/ghamza` | 22 / 5 | Scoreless hidden-role wink play; multiplayer-only | New-games agent; no execution claim here |
| `game-modes/word-impostor` | 24 / 6 | Words/questions/voting, personal/guess points; multiplayer-only | New-games agent; no execution claim here |
| `iosApp` (native wrapper) | 0 / 2 Swift test files | SwiftUI host, locale/permissions, private-content cover, native app lifecycle | No Apple runtime on this Linux host |
| `scripts` / `.github` | 148 / 7 tracked files | Build verification, provenance, release qualification, publishing safeguards | Focused release source, native process fault injection, 127 Python tests |

Architectural strengths: registration at composition root, no game imports in generic shell/transport, host-only reducers, recipient-specific snapshots, bounded and authenticated recovery, pinned dependency verification, separate testing fixtures, and store mutation gates. These remain design/source observations unless backed by another workstream's executed receipt. No host migration, internet matchmaking, raw-IP join or spectator implementation is assumed.

## Findings and issue disposition

| ID / priority | Status and evidence | Action / issue |
|---|---|---|
| REL-PROC-01 / P2 | **Confirmed executable defect.** `github_distribution.run` only stopped the immediate subprocess. Baseline isolated timeout test: 0.2-second deadline raised, child still wrote its marker after 0.5 seconds. Normal successful root exit also left descendants unmanaged. No credential leak/publication corruption claimed. | New [#257](https://github.com/Apdelrahman1911/parlor/issues/257); implemented in `af54105bbace3ae645827ae8c8fc2d6ad1f9161f`, Windows native qualification pending. |
| STORE-01 / release blocker | **Partial historical fix, remaining external blocker.** PR #250 replaced colliding `com.parlor.app` with `me.parlor.android` and `me.parlor.ios`; both policy ownership fields still `blocked/store_ownership_unverified`. | Existing [#230](https://github.com/Apdelrahman1911/parlor/issues/230), [new evidence comment](https://github.com/Apdelrahman1911/parlor/issues/230#issuecomment-6391693345). Keep open; do not repeat obsolete collision source claim. |
| LAN-01 / P1 qualification gap | **Confirmed missing executed boundary.** Real-kit advertising smoke enabled; join, bidirectional messages and broadcast independent-participant fixtures remain `@Ignore`. Fake/session tests do not qualify this boundary. | Existing [#6](https://github.com/Apdelrahman1911/parlor/issues/6), [new evidence comment](https://github.com/Apdelrahman1911/parlor/issues/6#issuecomment-6391693686). |
| IOS-PROTECT-01 / P1 release blocker | **Historical unresolved security qualification**, not reexecuted current failure. PROJECT_STATUS reports A37 0 passes/26 failures; H03 diagnostic Cocoa256/POSIX22 does not identify exact failing syscall or prove simulator-only limitation. | New [#258](https://github.com/Apdelrahman1911/parlor/issues/258), explicitly historical evidence not current reproduction. Preserve original evidence paths and guards. #46/#4 are distinct closed keychain/legacy-file issues. |
| DIST-CONFIG-01 / release blocker | **Live configuration absent**, not a code bypass: protected `github-sign-{android,macos,windows,linux}` and `github-publish` environments do not exist. `require_environment` demands reviewers, no self-review/admin bypass, allowed refs and exact-source acceptance. | Document setup/credential/approval requirement; do not create permissive environments or publish. |
| RECOVERY-01 | #252 body still describes pre-retention policy/proposal. Lifecycle agent independently reports current 15-second healthy retention plus fresh liveness and original 120-second recovery deadline; physical acceptance remains untested. | Update [#252](https://github.com/Apdelrahman1911/parlor/issues/252) with lifecycle agent's current source/test receipt, not duplicate or close on source alone. |
| JOIN-01 | Owner-reported cross-game joining; issue accurately distinguishes report from independent reproduction. | Existing [#251](https://github.com/Apdelrahman1911/parlor/issues/251), network workstream owns verification. |
| RESUME-01 | Missing normal-list deletion path; needs safe confirmation, credential/snapshot separation, list persistence and failure tests. | Existing [#253](https://github.com/Apdelrahman1911/parlor/issues/253), dedicated implementation stream. |
| DOC-01 / P3 | AGENTS.md describes two games, old Store ID and desktop-only testing; current branch has six games, migrated IDs and separate GitHub desktop channels. PROJECT_STATUS retains historical checkpoints, correctly warns they do not qualify later revisions, but needs an updated integration checkpoint after merge. | Documentation correction, not release qualification. Do not modify preserved historical receipts. |
| DEP-01 / P3 optional | Dependabot covers Actions only. #128 deliberately closed as maintenance preference, not demonstrated vulnerability; strict verification/manual provenance remain controls. PR #255 upgrades setup-java/upload-artifact without synchronized contracts. | Do not recreate #128 as a security defect. Review coordinated action pin/contracts separately; no unreviewed automated checksum regeneration. |

New UI findings from other workstreams have no exact duplicates in reviewed issue titles: misleading universal “Local + same Wi-Fi” catalog copy for multiplayer-only games (closest but different closed #178/#179); Mafia public-vote screen labeled “private” (different from closed #80 host-only role exposure and #84 disabled target semantics). Treat layout concerns as risks until rendered/executed, not confirmed accessibility failures by inspection alone.

## Release-tool remediation and verification

`af54105b` changes only release-tool supervision, its tests, and one mandatory native Windows test step/contract:

- POSIX tools start in isolated sessions. Finalization sends TERM/KILL to the owned process group even if the root already exited; unrelated processes are not searched/killed.
- Windows private supervisor creates an unnamed, noninheritable kill-on-close Job Object, assigns **itself before spawning any tool**, and preserves the sole handle until OS process teardown. Setup failure launches nothing. There is no post-launch assignment race or breakaway permission.
- Bounded timeout/output diagnostics and accepted return codes are preserved. Python cancellation finalizes ownership. Deliberately daemonized POSIX tools which escape sessions still require their existing tool-specific finalizers; process groups are not a sandbox for arbitrary hostile descendants.
- Real process regressions cover a TERM-resistant descendant inheriting output, timeout, output overflow, successful root exit, Python cancellation, and unrelated survivor; Windows API/order failure contracts are unit-tested.
- Mandatory Windows production-verification step runs the focused native process suite, with mutation contracts forbidding skip/continue-on-error/substituted tests. Existing required gates unchanged.

Executed at committed `af54105b` on Linux/Python 3.12:

```text
python3 -m unittest scripts.release.tests.test_distribution_process_ownership \
  scripts.release.tests.test_github_distribution scripts.release.tests.test_github_test_release \
  scripts.release.tests.test_workflow_contract -q
127 tests; PASS (7.375 s). Workflow contract PASS.
```

Eight process tests were also run with ResourceWarning promoted to error: PASS. The archive negative test emits its expected duplicate-ZIP-entry warning. No Gradle build, native Windows execution, macOS execution or new store qualification was performed by this workstream. Windows must pass the added native job before claiming Windows containment verified.

## Live safeguards and remaining external evidence

Read-only GitHub API confirmed all three Store workflows `disabled_manually`: candidate, external testing promotion and production promotion. GitHub distribution/public test workflows are active; registration/preflight is not publication. No workflow state was changed.

This environment exposes Android adb but no local Apple toolchain or `/dev/kvm` was found during this workstream's inspection. Device attachment was not assumed. Native physical host/client direction matrices, phone calls, manual/automatic lock, hotspot/Wi-Fi changes, Doze/OEM restrictions, iOS suspension without debugger, force-quit, simultaneous reconnection, extended sessions, power/memory/thermal and accessibility/RTL journey measurements remain separate acceptance requirements. Signing identities, account ownership, protected approvals, legal/content/privacy decisions and A37 resolution remain external release requirements.

Merge and post-merge exact-SHA CI evidence belongs to the lead's final ledger; do not infer it from this baseline audit or 127 focused Python tests. The private security-review workstream is intentionally not reproduced in this public index before its mitigations qualify.
