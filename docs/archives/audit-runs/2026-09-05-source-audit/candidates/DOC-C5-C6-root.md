# DOC-C5 / DOC-C6 — root source-comment candidates

Finder: `/root`. Source: `main` at
`3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree
`db7f3d2afe73a13628296daee2cce71165eebc8d`. Independently validated by
`/root/mafia_cont`; both are **Low DOCUMENTATION MISMATCH**, not application defects.

## DOC-C5 — storage interface names legacy destinations as current

`/Users/abdelrahman/Projects/parlor/shared/storage/src/commonMain/kotlin/com/parlor/storage/snapshot/FileBackedSnapshotStore.kt:204–208`
names Android `Context.filesDir` and iOS `NSDocumentDirectory`. Actual production
implementations write to `noBackupFilesDir/snapshots` and Application Support
`Parlor/snapshots`; the named paths are migration inputs. Root reopened the
interface, binding, current filesystem and migration paths. The comment cannot
change runtime behavior. Update the guidance, not the storage format or locations.

Complete separate validation, reachable DI paths, counter-evidence, remediation:
`validations/DOC-C5-mafia_cont.md`.

## DOC-C6 — peer countdown assertion contradicts its rendering branch

`/Users/abdelrahman/Projects/parlor/game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/ui/flow/WhodunitPhaseRouter.kt:868–869`
claims peers render the countdown. The actual peer discussion branch at378–411
uses `PeerWaitingForHostScreen`; host/local RoundSegment uses DiscussionScreen
and TimerRibbon. Root discovered this while independently tracing WD-C4. A
public timer in a received projection is not evidence that UI displays it.

Complete separate validation, authority/flow guards and exact read hashes:
`validations/DOC-C6-mafia_cont.md` and
`validations/DOC-C5-C6-mafia_cont-source-hashes.json`.
Correct only the comment. No peer-countdown requirement, timer change, protocol
change, or new application defect is inferred. No device execution was needed
or claimed for either source-documentation comparison.
