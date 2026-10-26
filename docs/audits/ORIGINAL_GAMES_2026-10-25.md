# Original games audit — 2026-10-25

Scope: Whodunit, Mafia, and Last Light on the `feat/last-light` integration baseline
`39aacd31`. This report distinguishes source review from executed verification.
It does not certify Android/iOS devices, networking or the entire application.

## Final integrated visual checkpoint — October 26 readback

The exact `53a72779` worktree reran the three original-game screenshot classes
on October 25, 02:03:48–02:04:22 UTC: **7 tests, zero failures/errors/skips**,
`BUILD SUCCESSFUL` in 33 seconds. Six 320dp/200%-text English/Arabic synthetic
screenshots were inspected independently: the stacked ContextRibbon no longer
splits words, the actual Doctor target selection remains visible, Whodunit's
clues/advance controls are reachable and Last Light's hand remains concealed
while its public guide is open. Deliberately scrolled viewport edges are not
horizontal clipping. These executed results supersede the pending screenshot
paragraphs below; they are not native screen-reader or physical-device evidence.

The full strict local aggregate also passed at `53a72779`; consult the
[integrated ledger](2026-10-25-VERIFICATION-LEDGER.md) for exact outcomes and the
remaining hosted CI qualification, rather than treating older receipts as final.

## Architecture and rules reviewed

- **Whodunit:** pure authoritative reducer; readiness-gated dossier reveal;
  authored discussion duration; first-write, turn-ordered secret ballots;
  bounded tied revote; elimination/final-two endings; paused disconnect/rejoin;
  terminal cleanup and replay; redacted peer votes and per-player dossiers.
  Reviewed reducer, projection policy, and existing rules/recovery/flow tests.
- **Mafia:** 5–16 seats; validated role distributions; seeded role assignment and
  tie breaking; first-write night/vote actions; Doctor repeat/self protection;
  Detective result acknowledgement before night resolution; tied Mafia revote;
  parity/Town wins; early termination, grace expiry and final role reveal.
  Reviewed reducer, resolution helpers, settings, projection and recovery tests.
- **Last Light:** 2–6 seats; fresh 30-card rounds; fixed private burnout steps;
  five-card deal; owned/distinct 1–3-card play; Wild truth matching; forced final
  holder challenge; rotation, eliminations and finite match bounds. Reviewed
  engine/reducer and replay-based canonical validation. Existing adversarial
  tests cover conserved physical cards, fixed entropy and complete seeded games.

No new rules defect is claimed solely from this inspection. Existing intentional
mechanics, role information and authority boundaries remain unchanged.

## Findings and implementation

| Finding | Classification | Change / remaining work |
|---|---|---|
| Mafia public ballots displayed the private-night privacy banner | Confirmed privacy-expectation defect (not a new network leak) | Picker defaults private; voting explicitly declares public visibility in EN/AR; regression checks the actual ballot screen |
| Night target selection conveyed visually by border color alone; skip appeared as another primary action | Confirmed UI/accessibility weakness | Added visible radio indicator without a second clickable semantics target; weighted long names; secondary skip |
| Mafia peer discussion showed roster with no explanation of who advances | Confirmed UI clarity gap | Localized host/peer next-step ribbon and concise spoken-discussion/privacy guidance |
| Whodunit timer used one non-wrapping row for status, large digits and duration | Source-confirmed narrow/large-text layout risk; device severity unmeasured | Separate status, wrapping time row and non-animated progress; preserve one spoken value and threshold-only warning |
| Whodunit discussion evidence appeared as undifferentiated paragraphs | Usability improvement | Separate public clue cards with round attribution; unchanged order/content |
| Last Light challenge consequences required remembering prior instructions | First-time usability improvement | Optional inline bilingual public rules; never opens private cards, changes game state or forces a modal |
| Deep JSON nesting can overflow action decoding before typed validation | Confirmed by security workstream, not reproduced here | Security agent owns W/M codec-depth preflight and regression tests; link its report/commit during integration |

## New verification

`MafiaSeededStateMachineTest` drives 144 deterministic seed/table combinations
across all supported table sizes, mixed rule settings, night targets/skips,
random submission order, voting/revotes, and transient reconnects. Every command
checks observable canonical shape, every recipient projection, private-bucket
isolation and duplicate-command idempotence. Connected states must pass local
recovery; disconnected LAN states must be rejected by the local-only restore
validator (which intentionally cannot recover network seats). Natural completion must produce a winner
within a bounded command budget. This is reducer/state-machine execution, **not**
real socket concurrency or device recovery.

Compose regression tests verify public ballots never promise private voting and
exercise target selection/submission with long names
at 2× text in English/Arabic, and the optional challenge guide at 320dp/2× text
without revealing cards. Existing Whodunit timer semantics tests remain required.

The first coordinated run compiled the changes and exposed two test-contract
mismatches: the new model incorrectly treated the local-only restore validator
as a LAN recovery validator, and an existing Whodunit static formatting test
still required bullets after evidence became attributed cards. Both assertions
were updated to check the actual intended contracts, not removed; rerun pending.
The coordinated rerun receipts below supersede those two failed assertions.
XML parsing and unique, matching EN/AR resource-key checks passed locally;
`git diff --check` passed. These are not compilation/runtime evidence.

## Remaining qualification

- Actual Android/iOS host/client flows, calls, screen locks, suspension, process
  death, simultaneous network reconnections and physical privacy checks.
- Full six-game journey redesign, native screen-reader navigation and visual
  acceptance across phones/tablets, including Arabic review by a fluent owner.
- CPU, allocation, battery, temperature and latency measurements on mobile
  hardware. Static/non-animated UI changes are not a measured battery claim.
- Longer property exploration beyond the seeded sample; bounded replay validation
  cost should be profiled on slower hosts before considering optimization.

## Executed evidence (integrated checkout)

The lead ran the second integrated Desktop/static-analysis cycle against
`8cdc328f3a24b99f3d061e6dd637ef2ef040ba34`:

- Mafia: **304 tests, zero failures**, fresh XML timestamps
  `2026-10-25T01:17:10Z`–`01:17:36Z`. The 144-scenario model test passed in
  5.259 seconds. This wall time is not a mobile performance benchmark.
- Whodunit: **365 tests, zero failures**, fresh timestamps
  `2026-10-25T01:17:41Z`–`01:19:37Z`. The two new large-text discussion rendering
  tests passed, including reachable attributed clues and advance callbacks.
- Last Light's subsequent Bidi test compilation exposed a missing Compose
  assertion import, corrected separately. The earlier 194 passing tests do
  **not** qualify the newer name-isolation changes; fresh execution is required.

Actual generated 320dp/200%-text screenshots were inspected under
`game-modes/{mafia,whodunit}/build/reports/ux/`. The timer and target selection
fit in both directions. Inspection also exposed a **confirmed shared
ContextRibbon defect**: the equal-width status/detail columns split English
and Arabic words at this size. The shared UX workstream owns the responsive
stacking fix and its focused tests. Rerender these game scenes after integration;
passing click/visibility tests alone did not establish visual quality.

The Mafia screenshot fixture was subsequently changed to the actual localized
Doctor screen instead of placeholder English chooser copy. That follow-up,
Last Light RTL assertions and guide screenshots remain pending the next
integrated run. All screenshots use synthetic names and no real player data.
