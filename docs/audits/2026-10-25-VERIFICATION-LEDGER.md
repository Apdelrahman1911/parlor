# Production overhaul verification ledger

## Integrated October 26 checkpoint

PRs #261, #262 and #263 are merged through `d72a0d83` (23:19:28 UTC),
tree `832f2f779800ce6f1d5fb8a973969dd27bc92766`. All six jobs passed at each
frozen PR head; eighteen source/artifact/cleanup records are now verified.
#262's iOS Release success is attempt 2 after the first job's 180-minute timeout;
the remaining five successful jobs are carried over from attempt 1.

The [qualification report](2026-10-26-OVERHAUL-QUALIFICATION.md) preserves exact
heads/runs, counts, merge ancestry and the final-helper sequencing error:
#261's complete six-lane source/Apple-cycle recheck finished **after merge**.
Required checks and protections remained enforced; this ordering is not hidden.

Strict local aggregate passed at `ba0c1e43` (17:05 UTC): 1,776 Desktop cases,
646 Android unit cases per variant, 688 release Python cases; three real-LAN
Desktop skips and one local Python environment skip remain. Valid up-to-date
outputs were reused. Hosted aggregate run40363906241 also passed all six lanes,
including six current managed Android device cases, 672 iOS KMP Simulator cases,
ten Swift host/UI cases and 18 Windows process-ownership cases. These are
platform-repeated receipts, not unique behavior or physical-device counts.

Fresh [main run40418964553](https://github.com/Apdelrahman1911/parlor/actions/runs/40418964553)
is **pending**, not post-merge qualification. Separately dispatched standalone
paired [protection run40419513891](https://github.com/Apdelrahman1911/parlor/actions/runs/40419513891)
also has pending collection/cleanup; it cannot establish Parlor application or
physical protection. No release publication or historical strict-gate waiver occurred.

## Prior qualification boundary (historical)

Checkpoint: **2026-10-26, 16:55 UTC**. The aggregate integration tip is
[PR #263](https://github.com/Apdelrahman1911/parlor/pull/263), containing the actual
ancestry of [#261](https://github.com/Apdelrahman1911/parlor/pull/261) and
[#262](https://github.com/Apdelrahman1911/parlor/pull/262), not just equivalent patches.
All remain drafts at this checkpoint. The plan is one normal protected #263 merge
after all six latest-head jobs and source-bound cleanup receipts for **each** PR
qualify, review threads are resolved, and the current base/heads/merge tree are
rechecked. This avoids invalidating downstream strict up-to-date checks through
intermediate main merges; it waives no check, protection or approval.

**Implementation and focused passes are not final merge or Store approval.**
PR records retain subsequent exact-head runs, corrections, integration and fresh
main CI. This committed checkpoint cannot pre-certify those future outcomes.

PR #256 is merged as `81ee9b7b5e1b3fad3c86b9d0cdb2e692d43276c7`.
[Feature run 40148791277](https://github.com/Apdelrahman1911/parlor/actions/runs/40148791277)
at `afe783838461afae1029f0369280a1bafadc395f` and
[post-merge run 40156824703](https://github.com/Apdelrahman1911/parlor/actions/runs/40156824703)
at the merge SHA both passed all six mandatory lanes. All twelve cleanup receipts
were independently inspected: PASS, no errors, successful upload and Gradle stop.
Merge parents are the previous `main` and the exact qualified feature head;
its tree equals the qualified feature tree. The separately scoped historical
strict-protection diagnostic is not part of these six passes and remains unqualified.

### Current integration evidence

- **Strict local aggregate PASS at `baab5f1d`**, 15:52:54 UTC: `productionCheck
  allTests --continue --dependency-verification=strict`, exit 0, 2m21s. Current
  module XML: 1,748 desktop cases (three existing skips), 644 Android debug and
  644 release unit cases, zero failures. Release Python: 688 cases, one local
  environment skip. Valid unchanged-source up-to-date outputs were reused;
  this is not a claim every case reran at that commit.
- Hosted #261 `da13ff2f` / run40352669569 and #262 `baab5f1d` / run40353098825
  now pass their Linux/Android and desktop lanes, including exact notices/lint
  gates; Apple qualification remains in progress. #261's previously timed-out
  Swift assertion passed unchanged in the new 10-case native host/UI run.
- **#263's `6151fb4b` run40353447607 is being superseded by real layout fixes**,
  not counted as qualifying the changed source. Five completed mandatory lanes
  and their cleanup receipts passed; Apple release linkage was still running.
  The new head requires all six fresh checks and source-bound cleanup receipts.
- Expanded acceptance exposed Settings labels squeezed beside a fixed switch
  (two real English failures) and Domino opponent names split by fixed-width
  cards (four real EN/AR failures). Adaptive layouts preserve full font scaling,
  single-action semantics and concealed hands; Domino's optional roster follows
  the board and active controls. Settings/app source `ad113dc1`: full app
  **149 tests passed**, zero failures/skips. New-game source `0107522c`:
  **85 freshly executed tests passed**, relevant plain/type-aware analysis passed,
  and 48 explicit EN/AR light/dark frames inspected. Ghamza/Word's 132 differential
  scenarios cover reducer lifecycle/codec restoration, not network/session recovery.
- The lead separately reproduced original-three host code wrapping even at 100%
  with a valid wide-glyph identifier. Four baseline failures at `6704ed9c`; shared
  scalable/LTR code display passes four methods/eight configurations plus metadata
  and desktop-test Detekt at `d9b44f5b`. See
  `2026-10-26-ROOM-CODE-ACCESSIBILITY.md` and `2026-10-26-GAME-ACCEPTANCE.md`.
- The tracked opt-in real-kit Linux runner executed **10/10 adapter invocations**
  at `1b468e9f`; 19 harness regressions passed. Real sockets/distinct identities in
  each single-JVM case do not establish independent-process or physical LAN
  acceptance. See `2026-10-26-REAL-KIT-LINUX.md`; #6 remains open.

The integrated layout/source changes require a new strict aggregate and full CI.
Only #257 is potentially closable after qualified integration/post-merge evidence;
#251/#253/#252/#6/#258 and remaining native/accessibility acceptance stay open.

### Historical checkpoints and resolved/superseded CI failures

- **Subsequent integration `7592c518` did not qualify.** Its full local strict
  aggregate completed in 11m42s: all 1,748 desktop cases (three existing skips)
  and 644 cases per Android unit variant had zero failures, but release automation
  failed. Removing the unused compiler library alias changed the catalog fingerprint
  without updating the notice review binding: 688 Python cases produced 15 failures
  and eight errors at that prerequisite. Hosted runs40348730480/#261 and
  40349132004/#262 reproduced the same failure and were cancelled, not qualified.
  #261 also observed an independent Google Maven HTTP502 in Windows; no dependency
  or repository override was introduced. #262's Windows lane passed.
- **Notice applicability reviewed and corrected in `94aa1a1c` / `da13ff2f`.**
  Independent old/new TOML comparison confirmed unchanged version pins, plugin
  aliases, bundles and every remaining library; only the unused compile-only
  alias was removed. All 26 notice texts, their byte hashes and the strict verifier
  remain unchanged. Updated only the reviewed catalog hash. Source notice
  verification, 32 adversarial notice cases and the complete release script passed:
  688 Python cases, one environment skip, plus real scoped Gradle cleanup,
  workflow/inventory and pinned tools. This correction is integrated; fresh combined
  local and all six hosted checks are required at the new head before merging.
- **Local strict aggregate PASS at `53a72779`**, October 25 02:06 UTC,
  `productionCheck allTests --continue --dependency-verification=strict` under Xvfb;
  exit0, 4m2s, 1,655 actionable tasks (72 executed, 1,583 valid up-to-date results).
  Current module XML: 1,747 desktop cases, zero failures, three pre-existing
  real-LAN skips; 644 Android debug and 644 release unit cases, zero failures/skips.
  Release tooling: 688 Python cases, one environment skip; workflow, inventory,
  strict dependency, lint, static analysis, unsigned packaging and pinned tools passed.
  Do not count archived/remediation XML or aggregate duplicates as new test cases.
- **#262 hosted run [40152904168](https://github.com/Apdelrahman1911/parlor/actions/runs/40152904168)**
  at the same head passed Windows, Linux ARM64, macOS x64, iOS release linkage and
  iOS simulator/Swift runtime. Linux/Android failed because the exact lint inventory
  expected a concrete Compose compiler advisory absent from this environment.
  No unexpected lint row was reported. Managed-device steps did not run after that
  failure. This run is not full qualification despite the local success.
- **#261 hosted run [40152548602](https://github.com/Apdelrahman1911/parlor/actions/runs/40152548602)**
  at `b5b01ec2` passed Windows (including actual native process ownership), Linux
  ARM64, macOS x64 and iOS release linkage. Linux/Android has the same missing-row
  failure; iOS runtime failed an English setup field assertion. Both are under
  investigation, not suppressed or declared harmless based on another branch.
- Exact `53a72779` original-game visual rerun passed; six EN/AR 200% synthetic
  screenshots inspected. Context labels no longer split mid-word and Mafia target
  selection remains visible. These are desktop rendering evidence, not phone or
  assistive-technology acceptance.

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
| Release workstream and #261 earlier Windows corrections | Real Gradle timeout/busy-stop/unrelated-registry controls passed locally. Windows initially failed before tests due to temporary-script import paths; fixed and rerun. Then all nine real process-ownership cases passed; one expected-path assertion exposed Windows short-name canonicalization. Its exact-parent assertion is corrected; the later native Windows lane above passed. |

The local pass above qualifies the integrated source through `53a72779` within
its executed scope; the hosted lint inconsistency still blocks merging. A missing
remote update advisory is not evidence that its accepted dependency changed. No physical-device
execution, battery/thermal measurement or native assistive-technology acceptance
is represented by desktop screenshots, emulators, simulators or JVM benchmarks.

## Nine-phase delivery and remaining work

| Phase | Implemented or reviewed | Still required |
|---|---|---|
| 1. #256 | Fixed, merged, verified tree and fresh six-lane post-merge CI; all cleanup receipts passed | Complete within mandatory CI scope; no physical/Store qualification inferred. |
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
- [#257](https://github.com/Apdelrahman1911/parlor/issues/257): owned subprocesses, bounded logs and private Gradle registries; #261 and `2026-10-25-RELEASE-SUPERVISION.md`. Fresh Windows native ownership and complete Windows lane passed; remaining Linux/iOS failures still block the PR.
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
