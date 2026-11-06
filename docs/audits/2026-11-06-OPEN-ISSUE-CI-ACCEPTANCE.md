# Open-Issue CI acceptance audit — November 6, 2026

## Scope and evidence rules

All twelve Issues open at intake were reviewed, including their original
acceptance and later comments: #182, #230, #251, #252, #253, #258, #259, #260,
#267, #276, #285 and #308. #285 and #308 subsequently met their remaining
requirements and closed; ten remain open. Passing general CI does not close an unmet device, security or upstream
requirement. This is an execution matrix, **not a claim that all CI work is done**.
The later native acceptance investigation found a distinct Android locale
lifetime defect, closed #329 below, and live configuration-reset defect #332.
Neither is one of the original twelve; #332 remains in active correction.

Accepted baseline is actual main `6028009f43c8fcd6b5d22ce8d94e0b822d5685d0`,
tree `70b4cbe78dde95c1ea4519f60dbbda36f79c133a`, independently verified by
[42193749434](https://github.com/Apdelrahman1911/parlor/actions/runs/42193749434)
after normally protected PR328. All eight producers and optimized Apple Release
join pass original source/artifact/XML/owned-cleanup readback. Receipt SHA256:
`a481e50621ba1ab7370a372979175154b39fbab57228afbee5a789ba778a5bb8`.
Counts per Desktop host: 2,030 methods (2,027 pass; three established single-JVM
LAN skips). Android: 763 methods per unit variant, 120 GitHub-test methods,
27 API34 native / 3 optimized API35 native and 12 process cases/24 original phases.
iOS: 809 KMP Simulator / 42 Swift / 457 readiness methods; 844 release and
73 worker controls pass. All257 synthetic PNGs per Desktop host are retained and
decoded, with explicitly scoped original-image review rather than a claim that
every frame was manually inspected. This qualifies the baseline, not newer
main. PR330's game-layout and PR331's navigation-fixture corrections are merged,
but actual main `d7d6f415`/42227976735 still fails Android process verification.
Fresh native correction, complete protected checks and independent main remain
required; see the [handled-configuration report](2026-11-06-ANDROID-HANDLED-CONFIGURATION-LOCALE.md).

## Acceptance matrix

| Issue / priority | Implemented and verified scope | Work still required / disposition |
|---|---|---|
| [#285](https://github.com/Apdelrahman1911/parlor/issues/285), CI P1 | Production worker ownership was merged in PR322. Missing Intel cancellation/holding control now independently passes [42141604588](https://github.com/Apdelrahman1911/parlor/actions/runs/42141604588), including actual signal, client exit, worker absence and final cleanup. | **CLOSED.** Exact cold-start versus acquisition boundary and receipts are in the [worker/capacity report](2026-11-06-CI-CAPACITY-AND-WORKER-QUALIFICATION.md). No duplicate production integration or successful ARM replay. |
| [#308](https://github.com/Apdelrahman1911/parlor/issues/308), CI P1 | Fourteen-root pre-build cleanup plus exclusive per-invocation AVD retirement are independently main-qualified. Actual constrained-free-space [42143063097](https://github.com/Apdelrahman1911/parlor/actions/runs/42143063097) passed both runtime lanes and owned cleanup. | **CLOSED:** PR327's all-eight full [42142636265](https://github.com/Apdelrahman1911/parlor/actions/runs/42142636265), protected merge `705c934b` and distinct [main42150601522](https://github.com/Apdelrahman1911/parlor/actions/runs/42150601522) passed independent readback. Each successful runtime retires only its owned AVD; ~26.015GiB reserved-free-space acceptance is not the historical smaller total filesystem, peak-use measurement or a speed benchmark. |
| [#182](https://github.com/Apdelrahman1911/parlor/issues/182), navigation P2 | PR311/326 correct retained phase/identity ownership. EN/AR duplicate/stale Name/code regressions, protected merge and distinct main are complete. Stale merge checkbox and `invalid` label corrected. | **Device acceptance:** real TalkBack/VoiceOver single/repeated confirmation, with Host remaining Host and Join requiring explicit code. Do not relabel original HID cancellation as fixed by these callback guards. |
| [#230](https://github.com/Apdelrahman1911/parlor/issues/230), release critical | PR250 migrated executable IDs to `me.parlor.android` / `me.parlor.ios`, with Debug suffixes. Unsigned identity/packaging gates pass. Issue title/body now reflect the replacement IDs. | **External accounts/credentials/approval:** verify ownership, signing, provisioning and release/privacy/legal gates. Both policy records remain blocked. Disabled Store workflows remain disabled; repeated unsigned CI cannot supply ownership. |
| [#251](https://github.com/Apdelrahman1911/parlor/issues/251), admission P1 | `PeerOpeningCancellationTest`, session start/deadline and authoritative-envelope tests exercise game/version rejection before credential staging, valid joins, selected rejoin and start. Generic registry dispatch and exact protocol4.2 remain. | **Device matrix:** wrong/same-game joins in both Android↔iOS host directions, start eligibility/commit and selected rejoin. Existing same-machine separate-process P2pKit results are not this cross-platform matrix. |
| [#252](https://github.com/Apdelrahman1911/parlor/issues/252), lifecycle P2 | Fifteen-second best-effort retention, fresh liveness, immediate concealment, monotonic bounded recovery, explicit cold-peer Resume and awaited cleanup are integrated. Lifecycle/concurrency/clock fault tests pass. | **Device/energy:** Home, calls, lock, suspension, Doze, Wi-Fi switch, memory/process death, simultaneous recovery and long sessions. Host death ends the room; no indefinite iOS hosting or migration. Unmeasured Compose accessibility idle-work risk remains a risk, not a measured battery regression. |
| [#253](https://github.com/Apdelrahman1911/parlor/issues/253), recovery P2 | Confirmed Discard/cancel/busy/failure/stale callbacks and credential ownership are implemented. PR328/main42193749434 independently qualify actual Android Keystore/files/preferences7, native shipping Home8 (six legal-game EN/AR relaunch plus two deletion-error/live-region journeys), API34 observer/navigation controls and all12 process cases/24 original native phases. iOS native discard/path/error controls also pass. | **iOS protection + devices:** supported-volume positive iOS persistence depends on #258. Physical screen-reader, real admission/rejoin and in-game restoration remain separate. The Android process matrix is encrypted synthetic local/membership storage, not authenticated multiplayer rejoin. [Native](2026-11-06-NATIVE-RECOVERY-AND-PUBLIC-UX-ACCEPTANCE.md) / [process](2026-11-06-ANDROID-PROCESS-RECOVERY.md) reports retain exact source/evidence boundaries. |
| [#258](https://github.com/Apdelrahman1911/parlor/issues/258), security P1 | Confirmed NSError, key-read/error classification and explicit-discard corrections are merged. Held `c3406ec3` Complete readback/fail-closed candidate compiles all six iOS main/test frontends ([42129599541](https://github.com/Apdelrahman1911/parlor/actions/runs/42129599541)); four original negative controls remain source-bound. | **Unsupported observed hosted-volume prerequisite + physical security:** original strict **0 PASS/6 FAIL** is not waived. Need supported-volume positive metadata/save/load/migration/recovery, applicable candidate qualification and protected merge; real keybag/locked-access/unlock must be tested on devices. Missing capability/`MNT_CPROTECT` does not prove every Simulator fails or identify Foundation's precise historical EINVAL operation. |
| [#259](https://github.com/Apdelrahman1911/parlor/issues/259), UX P2 | Capability filters/counts, registry artwork and adaptive navigation are integrated. PR328/main qualify retained257-render output paths, improved 200%-text Home first-card actions and Last Light nameplates; representative original EN/AR six-game, shell, settings and room-code images were actually inspected. This is not a manual review of every257 image. | **Active focused Phase5 follow-up:** reproduced Word vote-choice, Ghamza initial/rematch Reveal, Last Light Show-hand and Arabic exit-title defects now have scoped passing fixes/regressions; full new-source/protected/main qualification remains required. See the four linked game reports in [status](../PROJECT_STATUS.md). Physical gestures/insets/keyboards, TalkBack/VoiceOver and subjective usability remain open. |
| [#260](https://github.com/Apdelrahman1911/parlor/issues/260), gameplay P1/P2 | All six recipient validators and local concealment are integrated. Deterministic complete-match/recovery/duplicate-delivery and existing authority/rule/privacy tests pass; PR295's Ghamza regressions are integrated. Representative six-game original EN/AR renders were reviewed, with confirmed first-action/title findings corrected in the focused follow-up rather than inferred from intentional scrolling. | **Focused UI qualification + devices:** complete the current three-game follow-up's full protected/main checks, then the recorded legal/invalid/duplicate/concurrent action, privacy, reconnect, terminal/rematch and long-session matrix in both native host directions. Ghamza's documented public deduction is an established-rule tradeoff, not a raw private-slice leak; mechanics remain unchanged. |
| [#267](https://github.com/Apdelrahman1911/parlor/issues/267), native input P1 | App retained callback and XCTest geometry/query defects have separate verified fixes. Original native evidence reaches same-sender HID registration/discard/cancellation. | **External runner/platform boundary remains:** supported correction or usable native acquisition at [runner-images#15252](https://github.com/actions/runner-images/issues/15252); no new upstream fix at intake. Full diagnostic neutrality is not certified by a scoped passing pair. Do not retry unchanged probes or activate cancelled touches. |
| [#276](https://github.com/Apdelrahman1911/parlor/issues/276), native typing P1 | End-to-end evidence confirms deferred UIKit insertion after keyboard-service delay, not lost Kotlin draft updates. Retained app handoff defects are separately fixed. PR328's distinct sequential tutorial-query interruption now has a bounded coherent [observer correction](2026-11-06-IOS-KEYBOARD-TUTORIAL-OBSERVATION.md), independently passing full PR/main42 Swift/809 KMP Simulator/457 readiness methods with original deadlines/actions intact. | **External runner/platform boundary remains:** latest retained proxy→daemon delay33.130487s; alternate macOS26 acquisition produced zero usable frames at unchanged bounds. No supported deeper HID/service-startup fix at runner-images#15252. No keyboard warmup, speculative focus change, extra input or longer assertion; a green run is not a permanent upstream correction. |
| [#329](https://github.com/Apdelrahman1911/parlor/issues/329), Android locale P2 (new) | Actual42169900414 reproduced English after Activity relaunch despite persisted Arabic and intact sibling storage. Exact acquisition identity/successor-aware restoration fixes late same-language disposal; seven common regressions and all27 current native API34 methods preserve EN/AR Home outcomes. | **CLOSED:** PR328's full42185129610, normal protected merge6028009f and distinct main42193749434 independently pass all producers/Release join/source/artifact/cleanup acceptance. The original failed evidence remains failed. Broader iOS protection/device #253/#259 gates are not closed by this fix. |
| [#332](https://github.com/Apdelrahman1911/parlor/issues/332), Android configuration locale P2 (new) | Native42246135518 confirms actual English after one handled rotation, with persisted Arabic and the same live Activity; clearing the observer cache makes no difference. Exact-owner configuration refresh and17 common regressions are prepared. | **Active CI correction:** integrate the executed EN/AR reproducer, qualify the correction through all required native/optimized-platform checks, protected merge and distinct main. This is separate from #329's verified disposal fix and does not prove the original cold-process failure sequence. |

## What this audit does not repeat

- PR256 and Phase2 #293/#299/#301 integrations are complete; PR282/295 were
  superseded by their qualified integrations. #312 and skills-repository PR13
  are already resolved/published. No republishing or completed benchmark rerun.
- Three historical single-JVM multicast skips remain explicit. Existing real
  separate-process socket/selected-resume evidence is retained, not described
  as Android↔iOS or physical-device execution.
- The optional draft PR291 hybrid-runner experiment has no qualified successful
  matched-source comparison. It is not needed for these acceptance fixes and
  must not be merged as a purported measured speedup.
- Historical custody, original failures and `AGENTS.md` remain unchanged. No
  Store release, dependency override, paid runner or branch-protection bypass.

## Device handoff prerequisites — reserved, not started here

Use the **eventual independently accepted main/artifact**, not the baseline
numbers above if source has changed. Record commit/tree, artifact checksum,
device/OS, host/peer direction, language/theme/font/motion and PASS/FAIL per row.
Use synthetic players and no credential, role-map, real name or room-secret logs.
Keep signing/account evidence in the intended protected environment. Use JDK21,
the Gradle wrapper and Xcode26.3/17C529 for Store-qualified build work.

1. **Admission (#251):** same-game passing control and every relevant wrong-game
   pair, host approval, Start and selected Resume in both host directions.
   A mismatched binding never stages credentials, becomes start-eligible or
   decodes a different game's snapshot.
2. **Recovery (#252):** [PHY-09–15 and the brief-interruption matrix](../P2P_MANUAL_TEST.md#recovery-and-lifecycle).
   Include 1/3/5/10/14/16/30/>120s, calls/inactive sheets versus background,
   manual/auto lock, Doze/low power, Wi-Fi switch and process termination without
   a debugger sustaining iOS. Preserve the original deadline/seat/revision and
   never duplicate an action or expose hidden information. Record battery,
   CPU/RAM, temperature and actual retained versus securely rejoined behavior.
3. **Discard (#253):** legal local saves for Last Light/Mafia/Whodunit plus a
   multiplayer rejoin entry; cancel changes nothing, confirmed delete removes
   only the selection and stays removed after process termination/relaunch.
   Verify busy/error feedback, unrelated saves/credentials, stale activations,
   EN/AR and screen readers; unsupported/locked storage remains visible, not
   silently treated as empty. Do not clear all app data to obtain a pass.
4. **Protection (#258):** passcode-enabled iPhone, exact Complete metadata on
   new/existing/migrated records, authenticated encryption/no-backup policy,
   healthy save/load and selected deletion. Lock with protected data unavailable:
   protected reads/writes fail closed without plaintext fallback or automatic
   deletion. Unlock and restore exact state; verify expiry, interrupted atomic
   replacement and recovery. Retain applicable original six-check results and
   native operation outcomes. Simulator metadata is not physical enforcement.
5. **UX/gameplay (#182/#259/#260):** all six catalog→setup→lobby→game→results→rematch
   journeys, actual minimum/maximum supported rosters, small/large/native
   orientation, EN/AR, 200% text, TalkBack/VoiceOver and reduced motion. Test
   repeated/stale public confirmation, accessible name/role/order/touch bounds,
   own-only private views, invalid/concurrent/delayed actions and cleanup.

The upstream input and supported-volume prerequisites are **not mislabeled as
completed CI or device-only work**. Owner device findings can supply new evidence;
they do not retroactively turn failed hosted controls into successful ones.
