# M-C03 — independent validation

- **CONFIRMED DEFECT**, Low, final-role presentation only. Other suggested setup/tally manifestations remain unconfirmed.
- Finder `/root/mafia_cont`; independent validator `/root`.
- main `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, unchanged tracked source.
- Absolute source: `/Users/abdelrahman/Projects/parlor/game-modes/mafia/src/commonMain/kotlin/com/parlor/games/mafia/ui/screens/postgame/PostGameScreen.kt:59–80`.

## Proof and production relevance

I read the complete PostGameScreen, both phase routers' PostGame dispatch and finalRoles helpers, and the roster/name-policy path. Local `MafiaPassAndPlayPhaseRouter.kt:126–130,748–752` and multiplayer `MafiaMultiDevicePhaseRouter.kt:187–192,616–630` pass full legal names without a width cap or alternate layout. RoomInputPolicy permits32 ASCII W characters; local player entry uses that policy too. The name is not a malformed or inaccessible state prerequisite.

Both Text children are unweighted; SpaceBetween arranges measured children but does not reserve width for the role. Vertical scrolling prevents some vertical overflow, not horizontal starvation. The matching CMP1.10.3 RowColumnMeasurePolicy measures zero-weight children sequentially with remaining width (finder's official-source excerpt reopened). Resolved Desktop foundation is1.10.3; Android release resolves AndroidX foundation1.10.5 (`repro-ui-transport-02/gradle.log`). Do not claim an Android runtime measurement from the Desktop run.

`evidence/repro-ui-03/` directly renders the **unchanged production composable**, ParlorTheme,320×640dp,density1,fontScale1, five final roles and a policy-valid32W name. One test compiled and executed; expected nonzero role width failed. Actual role bounds: left256dp,right256dp (width0); name left64dp,right256dp. This is a concrete missing visual final-role value, not a screenshot interpretation. The role may remain in accessibility semantics; no screen-reader failure is established. It is intentionally public end-game data, not a privacy leak.

Cycles01 and02 contained audit-harness configuration/compile errors respectively; neither is a layout reproduction. Cycle03 is the execution proof. All cycles stopped wrapper8.13 and removed task-created outputs; user-owned Gradle9.x processes were preserved.

## Expected / suggested fix / limitations

Expected: intentionally disclosed final roles remain legible for supported names and compact layouts. Reserve trailing role space, weight/truncate or reflow leading labels, and test compact/large-text/LTR/RTL layouts. Do not shrink supported names or suppress accessibility scaling to hide the defect. Both modes use this composable; physical Android/iOS, RTL, landscape and font-scaling runtime matrices remain unexecuted. No reducer, authority, snapshot, or protocol defect is implied.
