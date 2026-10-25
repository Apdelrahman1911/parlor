# Production overhaul verification ledger

## Qualification boundary

Checkpoint: **2026-10-25, 01:54 UTC**. Implemented source is on
[PR #262](https://github.com/Apdelrahman1911/parlor/pull/262), stacked on
[PR #261](https://github.com/Apdelrahman1911/parlor/pull/261) and
[PR #256](https://github.com/Apdelrahman1911/parlor/pull/256).
The application branch incorporates the release branch's actual ancestry, not
only equivalent patches. Both overhaul PRs remain drafts at this checkpoint.
**Implementation and focused passes are not final merge or Store approval.**
The linked PRs retain exact-head workflow runs, corrections, merge commits and
subsequent verification receipts; check those live records before qualification.

PR #256's fresh run
[40148791277](https://github.com/Apdelrahman1911/parlor/actions/runs/40148791277)
at `afe783838461afae1029f0369280a1bafadc395f` has five of six mandatory lanes
successful, including Android managed-device tests and iOS simulator/Swift
host runtime. All five completed cleanup receipts passed. The remaining iOS
release-linkage lane is still running; the documented historical six-game
linkage duration was 97 minutes. No merge or post-merge success is inferred.

## Executed receipts and preserved failures

| Source / command or scope | Actual outcome |
|---|---|
| `15f127d9`, strict desktop aggregate | Aggregate failed. New-game modules individually passed 60 tests; this does not certify later additions. |
| `8cdc328f`, strict desktop/static aggregate | Remaining test compilation and old resource/header contracts failed; fixed without dropping assertions. Mafia304 and Whodunit365 tests passed. |
| `57be2cc6`, strict `productionCheck allTests --continue` | Failed after 15m59s: a newly observed concrete Compose compiler advisory, 15 inferred-type Mafia Detekt diagnostics, and an Arabic lazy-list test lookup. Corrected exact advisory inventory/contract, explicit model types and real lazy scrolling; no detector/test disabled. |
| `0a62a3bc`, strict combined rerun | Four finished desktop failures: three calls into Compose's unimplemented desktop restoration helper and an overlooked 37-to-38 advisory-count assertion. Both now corrected. The outer wrapper later exited143 during release automation; cause unproven. This interrupted aggregate is **not** a pass. |
| Same `0a62a3bc`, finished desktop XML | App143, Whodunit365, Mafia304, Last Light198, design-system48, session158, storage33 and other unchanged modules passed their completed suites. Last Light includes four BiDi cases. Transport's sole failure was the advisory-count contract; three existing real-LAN ignores remain. Do not combine partial receipts into aggregate success. |
| Same `0a62a3bc`, separate actual `productionReleaseAutomationCheck` | **PASS**, 2m12s, exit0: 687 Python tests with one environment skip; nested real Gradle registry fixture passed, workflow/inventory checks and checksum-pinned ShellCheck/Actionlint passed. The prior wrapper interruption did not reproduce; no broad-process-kill diagnosis is claimed. |
| Original-games workstream `b53946f9` | Strict Mafia type-aware Detekt plus 144 seeded mixed-setting scenarios passed. Explicit model types resolve analysis without deleting branches or assertions. |
| UX workstream `92adc202` and preceding shared layout source | 31 app rendering/interaction tests plus four adaptive/header/context-ribbon tests passed. Sixteen EN/AR/light/dark/100–200% Home/setup images inspected; enlarged-text Home images rechecked after adaptive introduction changes. |
| New-games workstream `cfe1a99e` / `00be73a2` | Ghamza15/Word19 and final Domino33 tests passed: 67 total, zero failures/skips. Executable registry recreation replaces the desktop stub, preserving same-phase scroll and rejecting old-phase restoration. Twenty-two public/covered synthetic images captured; EN/AR 320px/200% results inspected. |
| Release workstream and #261 first corrected Windows lane | Real Gradle timeout/busy-stop/unrelated-registry controls passed locally. Windows initially failed before tests due to temporary-script import paths; fixed and rerun. Then all nine real process-ownership cases passed; one expected-path assertion exposed Windows short-name canonicalization. Its exact-parent assertion is corrected, with fresh CI still required. |

The release source/count corrections and final game fixes are integrated through
`03cd61d3`; the next full combined run must qualify them. No physical-device
execution, battery/thermal measurement or native assistive-technology acceptance
is represented by desktop screenshots, emulators, simulators or JVM benchmarks.

## Nine-phase delivery and remaining work

| Phase | Implemented or reviewed | Still required |
|---|---|---|
| 1. #256 | Failed-job root cause fixed; fresh six-lane verification underway | Final lane, normal protected merge, verified merged tree and fresh post-merge CI. |
| 2. Architecture/behavior | All17-module inventory; targeted authority, admission, persistence, decoding, lifecycle, game, UI and release investigations | Not an exhaustive proof of every behavior; native extended multiplayer and uncovered scenarios remain explicit gaps. |
| 3. RPC | Actual published API/platform/provenance review; keep current single-connection messaging and P2pKit0.7.0-rc3 | No measured mobile-host advantage. No end-to-end RPC/mobile/battery comparison performed; no unsupported dependency migration. |
| 4. Lifecycle | Official Android/Apple policy research; retention/clock/fault tests; every tracker close caller awaits cleanup | Existing15-second best-effort retention is not indefinite hosting. Cold peer startup still uses manual Resume; host process death ends the room. Physical host/client matrices and any policy-qualified foreground-service experiment remain outstanding. |
| 5. Six games | Recipient-state consistency, privacy/action validation,144 seeded Mafia traces, nine Domino differential variants, restored phase/result navigation | Human fairness/pacing/content playtests and real multiplayer interruption/outcome acceptance. |
| 6. Issues |207 historical titles reviewed, relevant bodies/PRs inspected; existing issues updated and grouped new findings created | This is not line-by-line review of every historical body. Link final evidence and close only actually satisfied acceptance criteria. |
| 7. Application UX | Capability-correct catalog, adaptive entry/join/lobby/settings, accessible confirmations and shared components | Complete native onboarding/create/join/recovery journeys, TalkBack/VoiceOver and device font-size acceptance. |
| 8. Game UX | Bilingual primers, vote/privacy guidance, timers/clues, local concealment, action/result hierarchy, compact readable game headings/scores | Not a claimed bespoke rewrite of every surface. Full native private/public/turn/result/rematch journeys and fluent Arabic editorial review remain. |
| 9. Production | Strict compilation/lint/R8/testing, defensive decoding, scoped release cleanup, source-bound reports and fresh cross-platform CI | Final combined qualification, real-LAN/device stress/performance, strict iOS protection, signing/account ownership and external release approvals. |

## Finding, issue and report map

- [#251](https://github.com/Apdelrahman1911/parlor/issues/251): selected game/version bound before admission confirmation and credential staging; implemented in #262.
- [#253](https://github.com/Apdelrahman1911/parlor/issues/253): confirmation-gated Resume removal, opaque atomic ownership selection, stale/cancelled callback guards; implemented in #262.
- [#259](https://github.com/Apdelrahman1911/parlor/issues/259): capability-correct catalog, adaptive interactions and public/private ballot guidance; #262 and `2026-10-25-UX-SHELL.md`.
- [#260](https://github.com/Apdelrahman1911/parlor/issues/260): three-game recipient consistency, local concealment and action hierarchy; #262 and `../review/THREE_GAMES_PRODUCTION_AUDIT_2026-10-25.md`.
- [#257](https://github.com/Apdelrahman1911/parlor/issues/257): owned subprocesses, bounded logs and private Gradle registries; #261 and `2026-10-25-RELEASE-SUPERVISION.md`. Windows assertion correction still needs fresh qualification.
- [#252](https://github.com/Apdelrahman1911/parlor/issues/252): lifecycle recovery; `RPC_AND_LIFECYCLE_2026-10-25.md`. Physical acceptance remains open.
- [#6](https://github.com/Apdelrahman1911/parlor/issues/6): three pre-existing ignored independent-participant transport fixtures remain; advertising/fake buses do not qualify real multi-device LAN.
- [#258](https://github.com/Apdelrahman1911/parlor/issues/258): historical A37 strict iOS protection0/26 failure; not a current reproduction or proof of a simulator-only limitation.
- [#230](https://github.com/Apdelrahman1911/parlor/issues/230): replacement Store IDs implemented, ownership/signing/account acceptance unresolved. Store workflows remain disabled; protected publishing environments/approvals are not manufactured.

Additional evidence: `NETWORK_SECURITY_AUDIT_2026-10-25.md`,
`JSON_NESTING_MICROBENCHMARK_2026-10-25.md`, `ORIGINAL_GAMES_2026-10-25.md`
and `2026-10-25-AUDIT-INDEX.md`. The parser benchmark is synthetic JVM evidence,
not RPC or mobile power evidence. Independent review drove fixes for credential
rotation deletion, saved-scroll identity and detached Gradle cleanup.

## Environment and safeguards

Linux/JDK21/Gradle8.13; no local Xcode, attached phones or `/dev/kvm`. Hosted CI
supplies native builds and simulator/emulator lanes, not physical phone calls,
lock/force-quit/Doze/OEM/Wi-Fi matrices, thermal/battery measurements or account
ownership. No global Gradle stop, unrelated cache deletion, hidden-information
exposure, test disabling, checksum relaxation, protection bypass or Store release
is authorized by a passing test. Repository safeguards remain in force.
