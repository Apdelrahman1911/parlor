# Ordered GameEvent residual — bounded source-first recheck

- **Source:** `/Users/abdelrahman/Projects/parlor`, `main`, commit `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`.
- **Reviewer:** `/root/session_cont`, 2026-09-05. Audit-only; no production/test-source changes or builds in this follow-up.
- **Disposition:** **FALSE POSITIVE as a confirmed current-application defect.** Preserve an unallocated generic extension/cancellation-contract uncertainty, not an additional numbered confirmed finding.
- **Provenance:** `reviews/root-residual-event-lead.md` records original finder `/root/session_cont` and separate adjudicator `/root`. This follow-up reopens source to corroborate root's narrow rejection; it is not self-approval of the original candidate or a new independent confirmation. Root's separate adjudication remains the independence evidence.

## 1. Mechanism is real; current-app consequence is not established

`shared/session/src/commonMain/kotlin/com/parlor/session/passandplay/PassAndPlaySessionController.kt:112–130` commits reducer state under its mutex, reserves a chained event completion, releases the mutex, then emits. `OrderedEventEmissionTurn` at `145–159` always completes its own barrier in `finally`, including cancellation while awaiting its predecessor.

The deterministic abstract schedule is:

1. A has begun emitting and has not completed `A.done` (a custom suspended emitter, or a sufficiently slow real subscriber, would supply this condition).
2. B waits on `A.done`; C waits on `B.done`.
3. B is cancelled while waiting. Its `finally` completes `B.done` even though `A.done` is incomplete.
4. C can emit before A's remaining events.

Thus the helper does **not** implement transitive commit-order preservation across cancelled batches. Canonical state ordering is separate: state was already committed inside the mutex before any of these event waits. This schedule alone does not prove a production reducer, persistence, UI, or protocol failure.

No reproducer was executed in this bounded review. This is a source-level scheduling argument, not a claimed runtime failure.

## 2. Shipping reachability and counter-evidence

Paths below are relative to the absolute repository prefix above; exact file hashes, full/partial review ranges and absolute paths are in `ordered-event-residual-session_cont.sources.json`.

| Path inspected | Actual behavior relevant to this lead |
|---|---|
| `SessionController.kt:23–48`; `PartyAwareSession.kt:44–81` | API exposes `events`; decorator merely forwards it. Readiness uses canonical state and submit receipts, not event delivery. |
| `ShadowSessionController.kt:41–60,85–96` | Passive projection installation and command forwarding; `emitEvent` exists but no shipping caller was found. No reducer-event transport/collector is created here. |
| `WhodunitRetainedMultiplayerRuntime.kt:60–87,181–189`; `MafiaRetainedMultiplayerRuntime.kt:54–93,165–172` | Host wrappers forward mutations to game bridges. Mafia retained progression observes `canonicalState`, not `events`. |
| `WhodunitHostRoomBridge.kt:170–188,245–290`; `MafiaHostRoomBridge.kt:125–147,201–248` | Host/remote actions use the coordinator and controller receipts. Per-player snapshot generation reads the current canonical state and applies projections/codecs, not event batches. |
| `AuthoritativeSessionCoordinator.kt:213–251,401–428,504–541,1099–1187` | One mailbox worker serializes incoming commands and host mutations. Commit receipts determine revision/snapshot publication. This event helper is not a parallel protocol writer. |
| `WhodunitGameFlow.kt:810–884,888–950,1034–1063,1298–1351` | Local persistence collects canonical state. Rendering consumes projections. Peer feedback consumes command progress and host-disconnection/connection state, not GameEvents. |
| `MafiaGameFlow.kt:1–509`; `MafiaHostProgression.kt:1–81` | Local flow persists canonical state, renders projection/phase state, and uses state-based progression rather than reducer-event consumers. |
| `MafiaMultiDeviceHostFlow.kt:137–176,191–296` | Uses start gate and host projection for UI and disconnect handling. |
| `MafiaMultiDevicePeerFlow.kt:118–172,174–215` | Feedback is `commandProgress`; exit is `hostDisconnected`; connection chrome is durable connection state; game UI is the own-player projection. |
| Both complete peer room bridges | `connectionEvents` comes from `PeerConnectionTracker` and is typed `PeerEvent`; it is not `SessionController.events` / `GameEvent`. |

The saved explicit `*Main/kotlin` search inspected `GameEvent`, `events`, `eventFlow`, and `::events` occurrences across common/platform sources. Main source sets in `engine-testing` and `networking-testing` were included to avoid blind spots but are **non-shipping fixtures**, not production consumers. Build dependencies were reopened: application main/game/session source sets depend on production modules; test fixtures enter test source sets. Searches are not line-review credit; the above implementations/callers were actually reopened. Search hits in test sources do not make a production subscriber.

### Exact dependency semantics

`gradle/libs.versions.toml:8–9` pins Kotlin 2.4.10 / coroutines 1.11.0. The official `Kotlin/kotlinx.coroutines` **1.11.0** `SharedFlow.kt` implementation and docs were inspected, rather than inferring semantics from the field name or comments:

- No-subscriber emissions do not suspend or use `extraBufferCapacity` (`66–71`, `179–207`).
- Replay defaults to zero (`277–280`).
- `emit` takes the successful `tryEmit` fast path when `nCollectors == 0`; zero replay discards the event immediately (`418–426`, `447–455`).

Parlor creates `MutableSharedFlow<E>(extraBufferCapacity = 64)` at controller line 69 and no shipping subscribers were found. Consequently the proposed slow-subscriber source of backpressure is not present in current application execution. This is **not** a claim that all possible preemption/cancellation schedules are impossible; it is that no current observer or required state/protocol effect was established.

## 3. Tests and contract limitation

`shared/session/src/desktopTest/kotlin/com/parlor/session/passandplay/OrderedEventEmissionTurnTest.kt:14–39` verifies ordering for two live batches. `42–55` cancels a batch whose predecessor remains incomplete, then explicitly expects its successor's emission. It does not establish three-batch transitive order; its premise actually permits the bypass described above.

The exposed generic event API and helper's “commit-ordered” comment are reasons to retain the extension uncertainty, not evidence of an existing shipping consumer. Before introducing an event-dependent consumer, specify cancellation/partial-batch semantics and add deterministic A/B/C cancellation tests, subscribed-flow backpressure tests and consumer-specific privacy filtering. Do not assume raw events are peer-safe: e.g. `WhodunitEvent.VoteCast` carries a target and `MafiaEvent.DetectiveInspectionRecorded` carries private investigative metadata. No current transmission of these event streams was found in the inspected adapters.

No code change is recommended under the audit-only authorization. Any future implementation must keep reducer authority, bounded behavior, and event privacy separate from mere UI presentation.

## 4. Comments were not accepted as executable proof

Minor stale annotations observed during the trace:

- `WhodunitGameFlow.kt:935–937` says the snapshot writer fires on `PauseEngaged`; the actual writer collects canonical state at `853–854`.
- `MafiaEvent.kt:8–10` describes current UI-feedback consumers, while the actual inspected current UI feedback consumes command progress/state. `WhodunitEvent.kt:9–11` similarly describes consumers/persistence triggers that are not present for this stream.

These annotations explain why prose-only reasoning would overstate reachability. They are not additional numbered defects in this review.

## 5. Evidence and limitations

- `reviews/root-residual-event-lead.md` — separate root rejection and provenance.
- `reviews/ordered-event-residual-session_cont.sources.json` plus `coverage/reviews-session_cont.jsonl` — exact first-party source/range receipts; partial files remain partial for this pass.
- `evidence/ordered-event-production-usage-search-session_cont.txt` — corrected source-directory search, no line-credit claim.
- `research/ordered-event-session_cont/research-ledger.json`, `fetch.json`, compact upstream source — official URL, access date, exact version, hashes, reviewed ranges and counter-evidence.

**Verification scope:** source review/reachability and pinned-library implementation research only. No Gradle/Xcode/runtime/device test executed for this lead, no successful test count claimed, no new game consumer added, and no universal correctness verdict.

**Hygiene:** this reviewer generated only compact audit evidence. No build outputs or long-running processes were created; the parent owns the single build/cleanup lane and its daemons/devices were not stopped or modified by this reviewer. Tracked source/index were unchanged when this bounded pass finished. Pre-existing untracked work was preserved. See the source manifest final-state receipt for the exact check.
