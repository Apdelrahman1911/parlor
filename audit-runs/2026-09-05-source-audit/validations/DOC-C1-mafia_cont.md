# DOC-C1 — independent retention inventory adjudication

**DOCUMENTATION MISMATCH — Low. Not a confirmed application/privacy defect.** Finder `/root/whodunit_cont`, independent validator `/root/mafia_cont`,2026-09-05. Reviewed actual main `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`, no tracked code modification.

Absolute repository base `/Users/abdelrahman/Projects/parlor/`; exact hashes in `validations/DOC-C1-source-hashes-mafia_cont.json`.

## Independent source trace

I read all80 lines of `docs/PRIVACY_AND_COMPLIANCE.md`, both complete snapshot codecs, complete serializable Player, complete SerializedSnapshotWriter/FileBackedSnapshotStore/common storage DI and Android storage binding, then reopened both real producer paths and platform file writes. Coverage receipts include exact complete/partial ranges.

- Data row19 enumerates room-memory and peer credential retention and says cleared with that capability. It does not enumerate protected unfinished local-save rosters, although its purpose includes the local session.
- `WhodunitGameFlow.kt:723–746` and `MafiaGameFlow.kt:185–206` convert normal local entered names to `Player.displayName`, then instantiate local session flows. `Player.kt:10–15` serializes displayName; `WhodunitState.kt:19–33` and `MafiaState.kt:20–27` serialize player lists without a transient annotation. No multiplayer/rejoin credential is required for this path.
- Both definitions explicitly return the inspected codec (`WhodunitDefinition.kt:71`, `MafiaDefinition.kt:70`). Whodunit codec40–52 embeds entire serialized canonical state; Mafia codec28–34 serializes entire canonical state. Neither replaces/removes the roster names before local persistence. Structural validation accepts normal bounded names; it is not a data-removal filter.
- Whodunit local flow814–855 and Mafia268–291 construct these payloads and persist canonical states. `SerializedSnapshotWriter.kt:54–74` saves unfinished states, deleting only completed ones; Whodunit Save/Exit869–886 and Mafia305–315 persist/flush then navigate out. There is no disposal delete and no rejoin store coupling.
- `StorageModule.kt:16–23` chooses FileBackedSnapshotStore, not in-memory fixture. That store59–65 serializes envelope then writes through the platform FS; explicit deletion107–116 is separate. Android platform binding14–15 chooses AndroidSnapshotFileSystem: protected no-backup directory46–52, write64–73, FileOutputStream+sync+move254–274. iOS binding previously independently read13–18 chooses IosSnapshotFileSystem: Application Support/Parlor/snapshots74–105, protected write131–139, NSData atomic file operation347–359. These are real durable-file APIs, not UI-memory objects. No application/defaults/player file was inspected.

## Interpretation/counter-evidence

Game-state row22 **does** disclose local snapshot retention and canonical buckets. It would be an overstatement to claim that this document categorically promises no local data, that snapshots are a new privacy leak, that a Store form is false, or that intended local resume must be deleted. The specific data-inventory row is incomplete/ambiguous, because its enumerated locations omit a real independent name copy and lifecycle. The engineering inventory is intended to inform privacy/release decisions; readers should not need to infer that the distinct game-state row silently broadens name retention.

Expected code behavior remains named-roster local resume. PostGame predicates plus discard/deletion implement the intended separate snapshot lifecycle; this validation did not discover or claim deletion failure. Network peer name transmission, credential rotation, encryption correctness, private-state projection and legal requirements are separate review areas and not broadened into this finding.

## Recommendation and evidence limits

Documentation-only: add protected unfinished local snapshots to display-name retention, distinguish that lifetime from room/rejoin credentials, and cross-reference row22. Clarify completion/explicit discard/app-data deletion without promising a time-based expiry not present in source. Test/inventory coverage could encode+save+reload a synthetic roster for both game codecs and check documented fields against DTOs. No production modification or runtime execution is necessary to prove the documentation omission; no test/device/Store success asserted.

No builds, tests, servers, signing/private data, Git operations or production changes. Only compact audit evidence written; root owns build/cleanup lane.
