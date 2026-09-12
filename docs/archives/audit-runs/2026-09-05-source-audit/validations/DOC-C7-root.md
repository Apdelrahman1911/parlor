# DOC-C7 — stale descriptions of active GameEvent consumers

**DOCUMENTATION MISMATCH — Low; not an application defect.**
Original finder: `/root/session_cont`, in the final ordered-event residual
review. Independent validator: `/root`. Source `main` at
`3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree
`db7f3d2afe73a13628296daee2cce71165eebc8d`.

## Exact, narrowly approved manifestations

Absolute repository prefix: `/Users/abdelrahman/Projects/parlor/`.

- `game-modes/mafia/src/commonMain/kotlin/com/parlor/games/mafia/domain/event/MafiaEvent.kt:8–10`
  expressly says **current consumers drive UI feedback**. Current application
  flows consume canonical/projection state and command progress instead; this
  reducer `GameEvent` stream has no established shipping consumer.
- `game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/ui/flow/WhodunitGameFlow.kt:935–937`
  says the snapshot writer fires on `PauseEngaged`. The actual writer at849–854
  collects `canonicalState` and calls `persist(state)`, not an event subscriber.

The expectation is accurate current dataflow guidance. Misstating ownership can
mislead changes/tests concerning cancellation, persistence and private events.
The comment does not cause a lost save or feedback defect. Severity is Low.

## Independent source proof and counter-evidence

Root reopened the complete Mafia and Whodunit event declarations, the actual
Whodunit writer/pause source, and shipping usage sites. Root's earlier separate
ordered-event review read the complete PassAndPlaySessionController and
SessionController and traced the caller paths; see
`reviews/root-residual-event-lead.md`. The later bounded corroboration at
`reviews/ordered-event-residual-session_cont.md` provides additional exact
consumer paths, not a substitute for root's source review.

`PassAndPlaySessionController.kt:69,112–130` publishes reducer events to its
shared flow; `PartyAwareSession.kt:47` only forwards it. Shadow's `emitEvent`
has no shipping caller. Game host bridges use submit receipts and canonical
projections; peer connection streams are typed `PeerEvent`, not `GameEvent`.
Whodunit's explicit exit also persists the current canonical value at869–883.
No alternative event-triggered snapshot writer rescues the comment's stated
mechanism. These files belong to the current common production source sets;
tests that collect events do not make them current UI consumers.

Counter-evidence: pausing changes canonical state and emits `PauseEngaged` in
the same reduction, so autosave normally occurs at that time. That correlation
does not make the event the trigger. Event declarations remain valid domain
outputs useful to tests or future consumers; they are not useless code to
remove. The broader WhodunitEvent wording “Consumers drive…” may be read as a
generic extension contract, so it is **not** a separately approved manifestation.
No runtime failure, raw-event peer transmission, audio provider, upload system
or need for an event-based architecture is inferred.

## Suggested correction — not implemented

Describe current state/receipt-driven feedback and canonical-state persistence;
label potential event consumers as extension points. Do not add consumers,
delete events, modify snapshot logic, or send raw events to peers just to make
old comments true. Verify wording against both flows after any future change.

Source hashes/ranges: `validations/DOC-C7-root-source-hashes.json` and
`coverage/reviews-root-closeout.jsonl`. Finder record:
`reviews/ordered-event-residual-session_cont.md` section4. No build or runtime
test was required or performed for this documentation comparison.
