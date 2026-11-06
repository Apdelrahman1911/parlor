# Open-Issue CI acceptance audit — November 6, 2026

## Scope and evidence rules

All twelve Issues open at intake were reviewed, including their original
acceptance and later comments: #182, #230, #251, #252, #253, #258, #259, #260,
#267, #276, #285 and #308. #285 subsequently met its remaining requirement and
closed. Passing general CI does not close an unmet device, security or upstream
requirement. This is an execution matrix, **not a claim that all CI work is done**.

Accepted baseline is actual main `c3c0e2b3dc299ba48a71d40c302377c46da745f7`,
tree `453f235a653e537743a21f52a854a0a35685b5a2`, independently verified by
[42120235829](https://github.com/Apdelrahman1911/parlor/actions/runs/42120235829).
All eight producers and optimized Apple Release join pass, with source/artifact
and owned-cleanup bindings. Receipt SHA256:
`41473ac4d5cdceb35f522f1aff1410e90a67811ff4c547ae0755284083261fe4`.
Counts: 2,022 Desktop methods (three established single-JVM LAN skips),
756 Android methods per unit variant, 802 KMP Simulator, 40 Swift, 457 readiness
and 73 worker controls; both Android runtime lanes pass 3/3. These are baseline
results, not qualification of changes prepared after that source.

## Acceptance matrix

| Issue / priority | Implemented and verified scope | Work still required / disposition |
|---|---|---|
| [#285](https://github.com/Apdelrahman1911/parlor/issues/285), CI P1 | Production worker ownership was merged in PR322. Missing Intel cancellation/holding control now independently passes [42141604588](https://github.com/Apdelrahman1911/parlor/actions/runs/42141604588), including actual signal, client exit, worker absence and final cleanup. | **CLOSED.** Exact cold-start versus acquisition boundary and receipts are in the [worker/capacity report](2026-11-06-CI-CAPACITY-AND-WORKER-QUALIFICATION.md). No duplicate production integration or successful ARM replay. |
| [#308](https://github.com/Apdelrahman1911/parlor/issues/308), CI P1 | Fourteen-root pre-build cleanup is independently main-qualified, but earlier passes used larger filesystems. | **CI in progress:** PR327 retires only a successful lane's owned AVD before the next lane. Full [42142636265](https://github.com/Apdelrahman1911/parlor/actions/runs/42142636265) and actual constrained-free-space [42143063097](https://github.com/Apdelrahman1911/parlor/actions/runs/42143063097) require terminal evidence, normal merge and distinct main. A reserved-space experiment is not the historical smaller total filesystem. |
| [#182](https://github.com/Apdelrahman1911/parlor/issues/182), navigation P2 | PR311/326 correct retained phase/identity ownership. EN/AR duplicate/stale Name/code regressions, protected merge and distinct main are complete. Stale merge checkbox and `invalid` label corrected. | **Device acceptance:** real TalkBack/VoiceOver single/repeated confirmation, with Host remaining Host and Join requiring explicit code. Do not relabel original HID cancellation as fixed by these callback guards. |
| [#230](https://github.com/Apdelrahman1911/parlor/issues/230), release critical | PR250 migrated executable IDs to `me.parlor.android` / `me.parlor.ios`, with Debug suffixes. Unsigned identity/packaging gates pass. Issue title/body now reflect the replacement IDs. | **External accounts/credentials/approval:** verify ownership, signing, provisioning and release/privacy/legal gates. Both policy records remain blocked. Disabled Store workflows remain disabled; repeated unsigned CI cannot supply ownership. |
| [#251](https://github.com/Apdelrahman1911/parlor/issues/251), admission P1 | `PeerOpeningCancellationTest`, session start/deadline and authoritative-envelope tests exercise game/version rejection before credential staging, valid joins, selected rejoin and start. Generic registry dispatch and exact protocol4.2 remain. | **Device matrix:** wrong/same-game joins in both Android↔iOS host directions, start eligibility/commit and selected rejoin. Existing same-machine separate-process P2pKit results are not this cross-platform matrix. |
| [#252](https://github.com/Apdelrahman1911/parlor/issues/252), lifecycle P2 | Fifteen-second best-effort retention, fresh liveness, immediate concealment, monotonic bounded recovery, explicit cold-peer Resume and awaited cleanup are integrated. Lifecycle/concurrency/clock fault tests pass. | **Device/energy:** Home, calls, lock, suspension, Doze, Wi-Fi switch, memory/process death, simultaneous recovery and long sessions. Host death ends the room; no indefinite iOS hosting or migration. Unmeasured Compose accessibility idle-work risk remains a risk, not a measured battery regression. |
| [#253](https://github.com/Apdelrahman1911/parlor/issues/253), recovery P2 | Local/multiplayer Discard UI, confirmation/cancel/busy/failure/stale callbacks and credential ownership are implemented; common/rendered tests and iOS native discard/path/error controls pass. | **CI preparation:** seven actual Android Keystore/files/preferences methods, described [here](2026-11-06-NATIVE-RECOVERY-AND-PUBLIC-UX-ACCEPTANCE.md). Then verify native relaunch/actual Resume UI and iOS positive protected persistence; #258 blocks its positive supported-volume prerequisite. Object reconstruction is not process death. |
| [#258](https://github.com/Apdelrahman1911/parlor/issues/258), security P1 | Confirmed NSError, key-read/error classification and explicit-discard corrections are merged. Held `c3406ec3` Complete readback/fail-closed candidate compiles all six iOS main/test frontends ([42129599541](https://github.com/Apdelrahman1911/parlor/actions/runs/42129599541)); four original negative controls remain source-bound. | **Unsupported observed hosted-volume prerequisite + physical security:** original strict **0 PASS/6 FAIL** is not waived. Need supported-volume positive metadata/save/load/migration/recovery, applicable candidate qualification and protected merge; real keybag/locked-access/unlock must be tested on devices. Missing capability/`MNT_CPROTECT` does not prove every Simulator fails or identify Foundation's precise historical EINVAL operation. |
| [#259](https://github.com/Apdelrahman1911/parlor/issues/259), UX P2 | Capability filters/counts, registry-selected artwork, rounded feedback, large-text adaptive screens and restoration have deterministic/rendered coverage. | **CI preparation / Phase5:** fix confirmed unuploaded screenshot paths across all games/shell/design system; inspect actual retained EN/AR/theme/2x images and improve confirmed UI defects. Real gestures, native inset/keyboard journeys, TalkBack/VoiceOver and subjective usability remain separate. |
| [#260](https://github.com/Apdelrahman1911/parlor/issues/260), gameplay P1/P2 | All six recipient validators and local concealment are integrated. Domino/Ghamza/Word Impostor recovery journeys, Mafia full-state-machine/projection and Whodunit/Last Light authority/recovery suites pass. PR295's two Ghamza public-deduction tests were integrated, not left pending. | **CI visual readback + devices:** review retained six-game layouts. Then complete legal/invalid/duplicate/concurrent actions, privacy, reconnect, terminal/rematch and extended sessions on both host directions. Ghamza's documented public deduction is an established-rule balance tradeoff, not a raw private-slice leak; do not silently change mechanics. |
| [#267](https://github.com/Apdelrahman1911/parlor/issues/267), native input P1 | App retained callback and XCTest geometry/query defects have separate verified fixes. Original native evidence reaches same-sender HID registration/discard/cancellation. | **External runner/platform boundary remains:** supported correction or usable native acquisition at [runner-images#15252](https://github.com/actions/runner-images/issues/15252); no new upstream fix at intake. Full diagnostic neutrality is not certified by a scoped passing pair. Do not retry unchanged probes or activate cancelled touches. |
| [#276](https://github.com/Apdelrahman1911/parlor/issues/276), native typing P1 | End-to-end evidence confirms deferred UIKit insertion after keyboard-service delay, not lost Kotlin draft updates. Confirmed app handoff defects are separately fixed; current full EN/AR journeys pass. | **External runner/platform boundary remains:** latest retained proxy→daemon delay33.130487s; alternate macOS26 collection produced zero usable frames at unchanged bounds. No speculative focus/autocorrect patch, keyboard warmup, extra input or longer assertion. A green run alone is not a permanent upstream correction. |

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
