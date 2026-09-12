# Gaps — game-rules

## Skipped on purpose (other workstreams)

- All Compose screens, overlays, a11y labels, layout tests.
- P2pKit / `HostRoomBridge` transport internals (only authority classify + one Mafia bridge test skimmed).
- Gradle/CI, Detekt, R8.
- `docs/**` and any `*.md` outside `project-code-audit/`.
- Session controller queues except where a flow test submitted actions into the same reducer.

## Production files not line-read after inventory

Whodunit/Mafia `ui/**`, `di/**`. Domain/snapshot/definition/content validators listed in FILE-LEDGER were read. Snapshot tests after ~line 250 of `WhodunitSnapshotValidationTest` were grepped + sampled, not every assertion.

## Rules not proven by legal-state tests

| Claim | Gap |
|---|---|
| Elimination vote only after discussion | No test that `OpenVote` is a no-op without clue/timer (GR-001) |
| SurvivedToFinalTwo via unresolved vote | Only planted dropped Collecting (GR-002/010) |
| Mafia disconnect freezes play | No assertion (GR-003) |
| Mafia `classify` exhaustiveness | No unit matrix (GR-007) |
| Whodunit rematch excludes previous killer **and** remains persistable | `beginReplay` logic read; no dedicated validator-after-replay assertion in the files fully read (model drive stops at first Reveal) |
| Elimination full length (all rounds, mixed abstain + elim) | Model drive only plays **one** Elimination round then accuses killer |
| Classic 5–8 (4-round) full vote | Model drive does play all Classic rounds for counts 4–8, seeds 0–10 |
| Bundled case × every killer × every round playable | Payload validator checks pool **sizes**; reducer golden uses synthetic cases. `WhodunitPolicyGoldenTest` is synthetic. Bundled JSON not executed through `WhodunitCluePolicy` for every character in this workstream |
| Arabic / RTL does not change rule compares | Content validation skimmed; reducers compare ids/raw strings only. No Arabic **id** (ids are kebab ASCII) |
| Host submitting SelfActor locally | `isAllowed` is wire-only; host session can submit `AcknowledgeIntro(peer)` in P&P via `WhodunitReadinessGate`. That is intended local auto-ack, not a MP hole |
| Mafia `REVOTE_ALL` / `SKIP_ELIMINATION` full reducer drive | `VoteResolutionTest` unit-tests outcomes; reducer drive in edge/full tests uses default `REVOTE_TIED_ONLY` |
| Mafia 13–16 live assignment through win | Settings presets validated; FullGameDrive / reducer tests use 5–7 |
| Snapshot recover **changes** game | Current codecs refuse non-canonical current payloads. Legacy Whodunit bare path **does** rewrite debate seconds / deflection / generation — intended migration, not audited against every golden in this pass (`WhodunitLegacySnapshotGoldenTest` skim only) |
| `WhodunitSnapshotCodec.decodeLegacyBare` + `requireValid` after normalize | Covered by legacy golden (skim). Risk: normalize then validate could accept a repaired state the old build never played |

## Inspector limits

- Did not execute Gradle tests.
- Did not load bundled case JSON as product rules (validator code only).
- Did not read `VoteTurnPolicy` / phase routers to see whether UI can fire `OpenVote` pre-clue (reducer hole stands regardless).

## Suggested next proofs (if lead wants them)

1. Legal-state test: Elimination `Round(1)` no clue → `OpenVote` no-op; after clue no timer → policy decision + `requireValid`.
2. Replace planted final-two CloseVote with a real 5p three-innocent-elim drive; `requireValid` on terminal.
3. Mafia: after disconnect, assert `ResolveNight`/`OpenVote`/`EndGame` policy explicitly.
4. Exhaustive `MafiaActionAuthority` table.
5. One bundled case × one seed × both Whodunit modes through Reveal with `requireValid` each step (beyond last-dinner TiedRevote).
