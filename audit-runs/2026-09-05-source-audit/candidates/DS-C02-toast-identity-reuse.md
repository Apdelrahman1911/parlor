# DS-C02 — reusing live-queue-derived toast IDs can hide a new notification permanently

> **Final independent disposition (2026-09-05): CONFIRMED DEFECT — Low.** Validator `/root`; see `validations/DS-C02-root.md`. Production-composable auto-dismiss/replacement failure reproduced; the later expiry assertion was not reached. No device reproduction is claimed. Original candidate text below is preserved; its pending language is superseded. Administrative banner added by `/root/whodunit_cont` at `/root` request, not a new source approval.

Finder `/root/mafia_cont`; independent validator requested `/root`. **UNCONFIRMED — source proof/reproducer prepared; runtime and separate validation pending.** Proposed severity Low, intermittent notification presentation/lifecycle. Source main `3625d0663ba6eb51338cbd5f9dc45f859ec18846`; no production edits.

## Source and reachable schedule

`ParlorToastHost.kt:80–93` assigns `(max live id ?: 0)+1`. After the last toast is removed, the next one gets id1 again. Production host169–188 keys both remembered `visible` and `LaunchedEffect` solely by this id. At automatic expiry177–180, it sets visible=false, waits the fade, then calls state.dismiss(id). No fresh identity is reserved across successive empty queues.

Deterministic legal event order: initial A(id1) visible; its own timer sets visible=false and completes fade; internal auto-dismiss emits []; a fresh independent `show(B)` occurs before Compose recomposition observes the empty list; state becomes [B(id1)]. StateFlow may conflate the empty state (or Compose may receive both updates before a frame). The same key remains mounted: `remember(id1)` keeps false and `LaunchedEffect(id1)` keeps its completed job. New B is in the queue but invisible, with no new expiry. If same text/severity is reused, strong equality conflation can suppress the replacement emission entirely as well.

Root App81,320–323 keeps one host/state for the app. Sources of fresh notifications are real asynchronous operations: SettingsScreen80–83 mutation failure; MafiaMultiDevicePeerFlow127–148 reducer rejection/command outcome; app resume/discard operations280–285, and equivalent Whodunit paths. A second event near first toast expiry is valid input timing; production need not itself subscribe to queue emptiness. No manual public dismiss API is assumed (there is none).

## Prepared reproduction and authoritative APIs

`reproducers/DSC02ToastIdentityReuseTest.kt.txt` renders unchanged production ParlorToastHost/State under real ParlorTheme. It leaves dismissal entirely to production delay and uses an isolated Unconfined collector to deterministically schedule public show(B) after auto-dismiss [] and before composition can remove key1. This is a schedule control, not production replacement/mock or mutation of internal visibility. Assertion requires B visible, then independently expired. Unexecuted by finder; root owns lane. If timing harness fails to drive the production timer, that is not application evidence.

`evidence/design-toast-api/research.json`: exact coroutines1.11.0 StateFlow source18–21,51–57,386–407 specifies/implements conflation. CMP runtime1.10.3 source jar is intentionally an EmptyFile wrapper; published module metadata forwards runtime to AndroidX1.10.5. Actual AndroidX1.10.5 Effects.kt269–289,322–337 remembers LaunchedEffectImpl by key; onRemembered launches, same key does not relaunch. Root resolved graph should corroborate wrapper target. Research files retain hashes/URLs/timestamps; this is not a claim that all runtime platforms were executed.

## Counter-evidence / non-claims

Live-queue IDs are atomic and unique among simultaneously active entries, and queue size4 is bounded; neither property prevents consecutive-instance identity reuse across conflated empty states. Ordinary events separated by a composed empty frame correctly recreate the host entry, so this is a timing edge, not every toast. Existing tests only assert bounded/latest queues and adjacent text coalescing; no lifecycle/key regression. State cannot grow unbounded from this alone. No canonical game state, host authority, protocol or secrets are affected; missed failure notification can confuse users.

## Recommended remediation (no implementation)

Separate stable per-notification identity from current live-queue contents using an atomically maintained generation/id (including wrap policy), or key lifecycle on a truly unique emission instance. Keep queue bounded and updates pure under CAS retries. Test auto-dismiss/new-show same-frame race for same and distinct text, old timers versus new item, concurrent writers, queue eviction, motion/language change, and disposal/recomposition.

## Source hashes

- `/Users/abdelrahman/Projects/parlor/shared/design-system/src/commonMain/kotlin/com/parlor/designsystem/components/ParlorToastHost.kt`: `555c56e57bfc6a7173d9034136f6a3a4de1dce9a068a66b3c04ca4da8a32d3f9`
- `/Users/abdelrahman/Projects/parlor/composeApp/src/commonMain/kotlin/com/parlor/app/App.kt`: `622b679a2ef023fab17bf6c8ed8eb38c1fbc2b76a391f5f9a8298d59da9dda59`
- `/Users/abdelrahman/Projects/parlor/composeApp/src/commonMain/kotlin/com/parlor/app/shell/settings/SettingsScreen.kt`: `f97da085d85ed5fe01ac9f4725580ea2f86e399dcd330f532d9babc1925535c5`
- `/Users/abdelrahman/Projects/parlor/game-modes/mafia/src/commonMain/kotlin/com/parlor/games/mafia/ui/flow/multidevice/MafiaMultiDevicePeerFlow.kt`: `67c50637f24919981c8aff238d497e33d46dbef476f490fe511891c36a7100fa`
