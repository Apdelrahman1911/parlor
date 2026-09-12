# DOC-C1 — Display-name retention inventory omits local resume snapshots

**DOCUMENTATION MISMATCH — independently validated, Low.** Finder `/root/whodunit_cont`; independent validator `/root/mafia_cont`, report `validations/DOC-C1-mafia_cont.md` and accompanying source hashes. Not a privacy leak, unauthorized retention code change, or Store declaration verdict.

Baseline main `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`; no tracked changes.

## Exact document claim

`/Users/abdelrahman/Projects/parlor/docs/PRIVACY_AND_COMPLIANCE.md:19` (SHA256 `299d248dea1938a445624842ab6b741b54221c06516689ec712eafcfe7b2bea9`) says display names are retained in room memory and, on a peer, inside the protected resumable credential, and are cleared with that capability. It omits the independent durable local-save location and lifetime.

## Reachable source path

Absolute repository base `/Users/abdelrahman/Projects/parlor/`:

1. `game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/ui/flow/WhodunitGameFlow.kt:723–746` converts actual locally entered names into Player.displayName and starts SessionDrivenFlow. This needs no LAN room or resumable credential.
2. `shared/engine/src/commonMain/kotlin/com/parlor/engine/state/Player.kt:10–15` is serializable with displayName. `game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/domain/state/WhodunitState.kt:19–33` serializes that roster as players/playersAtTable.
3. `game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/snapshot/WhodunitSnapshotCodec.kt:40–52` serializes the full canonical state. `WhodunitGameFlow.kt:814–855` uses that payload in SerializedSnapshotWriter and persists each unfinished canonical state.
4. `shared/storage/src/commonMain/kotlin/com/parlor/storage/snapshot/SerializedSnapshotWriter.kt:54–74` saves unfinished data. `WhodunitGameFlow.kt:869–885` flushes rather than discards on ordinary Save/Exit. `FileBackedSnapshotStore.kt:59–65` writes the encoded envelope through the platform-protected filesystem. No rejoin credential is involved.
5. Local display names therefore survive UI/room exit and process restart as part of the encrypted resume snapshot until that independent snapshot is completed/deleted. Mafia has the same producer/writer contract: `game-modes/mafia/src/commonMain/kotlin/com/parlor/games/mafia/ui/flow/passandplay/MafiaGameFlow.kt:185–206,265–291,305–315`. The independent validator fully read both codecs, Player, writer and file store, reopened both producers and actual Android/iOS durable-file APIs, and confirmed the separate local roster retention in both games.

## Expectation, impact, counter-evidence

The heading identifies an engineering **data inventory**. Per-data retention rows should name every implemented durable location so privacy/release reviewers do not conclude that deleting a rejoin capability also deletes an unrelated local saved roster. The expected **code behavior remains local resume with names**, not removal of an intended feature.

Counter-evidence: Game-state row22 generally acknowledges local snapshot data and public/private/host-only buckets; a careful reader might infer names are included. This may justify classifying as incomplete/ambiguous inventory rather than a contradictory product guarantee. Local data is protected, not uploaded; no actual player data was read. Completion deletion is implemented via the writers' PostGame predicates and is not claimed broken. The suspicion concerns only the misleadingly narrower name-retention row, not proof a published policy or Store form is wrong.

## Recommended documentation-only action

Explicitly include player display names/roster in protected unfinished local snapshots, distinguish the local-save deletion lifecycle from multiplayer credentials, and cross-reference the game-state row. No production change is recommended. A synthetic codec/save/reload trace can pin the inventory against actual DTO fields; existing local recovery tests remain relevant. No runtime test executed by finder.
