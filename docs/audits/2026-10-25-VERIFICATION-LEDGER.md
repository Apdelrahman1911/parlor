# Production overhaul verification ledger

## Status and source binding

**Working checkpoint, not final production qualification.** Prepared 2026-10-25
01:25 UTC from local integration source `8cdc328f3a24b99f3d061e6dd637ef2ef040ba34`,
tree `9805ad05429281816a103ded2b4da51e54efecbb`. The branch
`overhaul/production-experience` had not been pushed at this checkpoint. Later
changes must receive their own affected-test receipt; counts below must not be
promoted to a pass for newer source merely because files compile elsewhere.

PR #256 remained **OPEN**, no merge commit. Full qualification
[40148791277](https://github.com/Apdelrahman1911/parlor/actions/runs/40148791277)
was **IN PROGRESS** at `afe783838461afae1029f0369280a1bafadc395f`, a different
source from the overhaul. Two completed required lanes (Linux ARM64 and Windows)
and successful distribution rehearsals do not replace the remaining required
lanes. The prior run 40145248452 was cancelled, not passed. No production release,
Store upload, required-check bypass or physical qualification is claimed.

## Evidence already available

| Receipt | Source / executed scope | Result and limits |
|---|---|---|
| Focused shared boundary suite | `aefa85ac`, JDK21/Gradle8.13 strict; core18, networking34, session156, focused transport40 | **248 PASS, zero skips**; real codec/adapter fixtures with synthetic sessions, not real-LAN/device evidence. Does not certify later integrated edits. |
| First integration desktop aggregate | `15f127d9`, Linux/Xvfb, strict `productionDesktopCheck --continue` | **Aggregate failed** outside the new-three modules. Egyptian Dominoes30, Ghamza13, Word Impostor17 individually **60 PASS, zero failures/errors/skips**; XML readback recorded in the new-game report. Preserve aggregate failures. |
| Original-game and shell corrections | Integrated through `8cdc328f` | Fixes and tests are committed; updated aggregate **pending**. Earlier failures included shell modifier compilation, a state-machine test using the wrong recovery contract, and stale formatting assertions. No correction is certified merely by editing its assertion. |
| Release process/log suite | Release-workstream `93376762`, integrated as `420a9324` | **164 focused Python tests PASS**, including17 native-compatible ownership/log tests; workflow contracts PASS. Windows native execution of these changes still required. Subsequent actual Gradle-daemon registry work is a distinct follow-up receipt. |
| Resume deletion | `d9fc409c` in integration | Confirmation coordinator, rendering and storage/credential regressions committed; current integrated execution **pending**. The earlier248 shared tests predate this feature. |
| Parser guard microbenchmark | Report `JSON_NESTING_MICROBENCHMARK_2026-10-25.md`; pinned Kotlin2.4.10/serialization1.11.0 | Executed synthetic JVM resource-boundary comparison. Not Android/iOS, RPC, battery, thermal or end-to-end network performance evidence; security disclosure remains coordinated with mitigation qualification. |
| PR #256 qualification | Source `afe78383`, run40148791277 | **Pending**, not merged. Does not include the unpushed overhaul. |

## Nine-phase delivery and gap map

| Phase | Implemented/reviewed at checkpoint | Not finished / required next evidence |
|---|---|---|
| 1. Fix and merge #256 | Lead investigated current CI and prepared corrective feature-branch source; fresh full run underway | Finish all required checks, merge normally, record merge SHA/tree and **post-merge** CI; do not merge the overhaul on the feature branch's evidence. |
| 2. Full architecture/behavior audit |17-module inventory; focused session/transport/storage/game/UI/release investigations; source-confirmed findings and regression work | This is substantial scoped review, not exhaustive behavior proof. Finish integrated fault tests and record every remaining uncovered module/scenario explicitly. |
| 3. P2pKit RPC | Published API/platform/dependency and upstream change reviewed; retain existing events/messages rather than unsupported wholesale migration | No demonstrated mobile-host benefit; no production RPC migration. Latency/CPU/RAM/thread/allocation/network/recovery and power/thermal **device benchmarks not executed**. |
| 4. Android/iOS background recovery | Existing15-second best-effort retention and authoritative foreground validation reviewed; new duplicate-event, exception, rollback and rapid-transition tests added; official platform limitations documented | Added tests pending current integration receipt; phones unavailable. No new foreground service/background entitlement/host migration, and no indefinite iOS hosting promise. Test both host directions, calls/lock/force-quit/Doze/Wi-Fi and expiry. |
| 5. All six game rules | Original-three reducer/rule review and144-scenario Mafia model; new-three projection/own-hand consistency corrections, complete-match Domino differential recovery | Original-three new tests require corrected rerun. New-three60-test pass is source-specific. Human fairness/pacing/content playtests and actual multiplayer outcomes remain unqualified. |
| 6. Issue management | Historical dedup; #251/#252/#253 implementation/status updates; #230/#6 existing gates; new #257/#258 | Link current aggregate/PR/merge evidence before closure. Remaining UI/game findings need grouped issue mappings; no public unmitigated resource-exploit issue. |
| 7. Whole-app UI/UX | Implemented catalog capability filters, adaptive entry/lobby/navigation/settings, shared tutorial primitives, confirmation-gated resume deletion and responsive typography | Current rendered integration tests pending; full onboarding/settings/create/join/recovery/results journeys on Android/iOS and actual accessibility traversal untested. This is not certified complete redesign of every screen. |
| 8. Six individual game interfaces | Public-vote privacy copy, visible target selection, next-step cues, timers/clue cards, optional Last Light guidance; new-game local secret concealment and compact rules/actions | Complete private/public/turn/vote/result/rematch native journeys, screen-reader and large-text device validation; fluent Arabic editorial and game entertainment acceptance. |
| 9. Final production verification | Strict focused tests, fault/model tests, source-bound reports and supervised release tools | Finish fresh full aggregate/platform CI, signing/ownership, A37, real-LAN and device stress/security/performance, legal/privacy/content acceptance. No Store-release authorization inferred. |

## Issue and report index

- [#251 admission identity](https://github.com/Apdelrahman1911/parlor/issues/251#issuecomment-6391868931): implemented locally (`20e2a384`), not merged; exact-source aggregate and physical directions pending.
- [#253 Resume removal](https://github.com/Apdelrahman1911/parlor/issues/253#issuecomment-6391869280): implemented locally (`d9fc409c`), new tests pending; preserve selected-record and credential/snapshot separation.
- [#252 lifecycle](https://github.com/Apdelrahman1911/parlor/issues/252#issuecomment-6391709693): current15-second best-effort implementation documented; hardware acceptance remains open.
- [#230 Store ownership](https://github.com/Apdelrahman1911/parlor/issues/230#issuecomment-6391693345): replacement IDs implemented, owner/account/signing verification unresolved; three Store workflows disabled.
- [#6 real LAN](https://github.com/Apdelrahman1911/parlor/issues/6#issuecomment-6391693686): three ignored independent-participant transport fixtures; advertising smoke/fakes do not satisfy them.
- [#257 release subprocesses](https://github.com/Apdelrahman1911/parlor/issues/257): ordinary child supervision and bounded logs implemented; detached Gradle daemon scope is a separately reviewed follow-up, not covered by process groups alone.
- [#258 A37](https://github.com/Apdelrahman1911/parlor/issues/258): **historical**0/26 strict protection failure unresolved; no current physical reproduction or supported simulator-only conclusion.

Supporting reports: `2026-10-25-AUDIT-INDEX.md`,
`2026-10-25-UX-SHELL.md`, `ORIGINAL_GAMES_2026-10-25.md`,
`RPC_AND_LIFECYCLE_2026-10-25.md`, and
`../review/THREE_GAMES_PRODUCTION_AUDIT_2026-10-25.md`. Follow their individual
source/receipt boundaries; source-review prose is not an executed test.

Missing issue mappings reviewed: capability-label/UI journey improvements,
Mafia public-vote/private-banner copy, and NG01–04/06 new-game corrections have
no exact match in the historical207-title inventory. Prefer a small number of
coherent issues with separate acceptance items, not reopening unrelated fixed
#75/#78/#80/#84/#178/#179. The security coordinator owns any later disclosure.

## Final evidence fields to complete before declaring done

| Required field | Current entry |
|---|---|
| Final overhaul source commit/tree | **PENDING** |
| Full local command, start/end, exit, JUnit counts and preserved failures | **PENDING**; keep receipts per invocation, do not mix stale XML |
| PR #256 merge commit and post-merge verification run | **PENDING** |
| Overhaul PR URL, required-check runs, merge commit/post-merge run | **PENDING** |
| Native Windows process/Gradle containment | **PENDING** |
| Android compilation/R8/lint/managed-runtime evidence | **PENDING** for final overhaul source |
| Apple frameworks, Simulator KMP, Swift-host runtime evidence | **PENDING** for final overhaul source |
| Android/iOS physical host/client and performance matrix | **BLOCKED: no attached devices** |
| iOS strict protection, signing/accounts, ownership and approvals | **OPEN external/security gates** |
| UI screenshot/journey artifact source and reviewer conclusions | **PENDING**; rendered screenshot is not device acceptance |
| Merged commits, resolved issues and precise outstanding gates | **PENDING final reconciliation** |

The Linux environment has no `xcodebuild` or `/dev/kvm`; `adb devices -l` showed
no attached devices in the lifecycle investigation. Remote hosted CI can supply
supported simulator/emulator/build evidence, but cannot be relabeled real-phone
battery/thermal, interruption, accessible interaction or Store-account evidence.
