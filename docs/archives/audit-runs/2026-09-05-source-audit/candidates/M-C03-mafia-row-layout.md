# M-C03 — Unweighted Mafia row text can starve trailing roles/controls

- Finder `/root/mafia_cont`; independent validator `/root` (completed).
- Source main `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, unchanged tracked source.
- **CONFIRMED DEFECT — Low, final-role presentation only.** Root independent review and direct production-composable execution: `validations/M-C03-root.md`, `evidence/repro-ui-03/`. Legal32W name at320dp yields role width0. Setup/tally siblings remain UNCONFIRMED and are not counted as confirmed manifestations.

## Primary concrete hypothesis / production reachability

Mafia PostGameScreen displays each `(playerName, finalRole)` in a full-width `Row(SpaceBetween)`. The leading name Text and trailing role Text both lack weight/maximum-width allocation. `RoomInputPolicy` and Mafia's authoritative roster permit a trimmed32-character ASCII name such as32 W characters. Both local and multiplayer phase routers pass the full legal display name unchanged into this public final-role surface. The screen and card apply content padding, reducing available row width. At compact320dp width the legal name's preferred width exceeds the row; Compose can measure the role at zero remaining width. Result: no visible final role for that player, even though the intended row explicitly renders it.

Expected: every player's intentionally revealed final role remains legible alongside a supported display name, using wrapping/reflow/truncation without dropping the actual role. Actual suspected: leading text consumes row budget before trailing text is measured. Neither vertical scrolling nor SpaceBetween reserves trailing width.

## Sibling manifestations to validate, not separately counted

- `VoteAnnouncementScreen` tally rows have the same leading-name/trailing-count shape.
- `MafiaSetupScreen.ToggleRow` places the built-in long label "Doctor can protect the same player on consecutive nights" before an unweighted Switch.
- `RoleCountStepperRow` places label before a trailing row containing decrement/count/increment. The maximum-revotes label is long, especially Arabic/large text. These may compress/clip/overlap controls even without user-created long names. Exact material-control bounds/overdraw still need proof; do not infer identical outcome from the PostGame test alone.

## Evidence and exact-version research

`reproducers/MC03MafiaTrailingContentLayoutTest.kt.txt` directly renders the unmodified public production PostGameScreen at320x640dp, density1/fontScale1 using ParlorTheme, with a valid32W name and five final roles. It asserts measured role width>0 and includes name/role bounds in failure. Intended temporary source injection only into composeApp desktopTest (already has UI-test dependency); no source-set/production changes by finder. At report creation unexecuted.

`evidence/mafia-layout-api/reference.json` records URL/access date/archive hash for Maven Central `org.jetbrains.compose.foundation:foundation-layout:1.10.3` source (catalog CMP pin). Read `Row.kt25–120` and `RowColumnMeasurePolicy.kt77–164`: zero-weight children are measured in order; each receives mainAxisMax minus already-fixedSpace. Exact implementation123–145 supports the starvation hypothesis. Archive was processed in memory; retained only small extracted source/reference text. Root is collecting actual resolved graph: cached newer source versions were not used as proof.

## Counter-evidence and scope limits

- Text ordinarily wraps and these screens vertically scroll, so total content is not intrinsically unbounded. Wrapping does not itself reserve a sibling's width.
- Display names are bounded/validated (no arbitrary megabyte input required), but32 legal wide characters can exceed compact content width.
- Setup rows are wholly toggleable with a semantic switch role, so a clipped visual Switch may still respond to tapping the label and remain screen-reader operable; do not call the setting unusable without measured evidence.
- No secret exposure, rule/reducer change, protocol issue, or canonical-state corruption alleged. This is UI presentation/responsiveness only. No physical Android/iOS, RTL, landscape, VoiceOver/TalkBack or font-scale execution yet.

## Suggested remediation/regression coverage (do not implement)

Reserve trailing-control/value space by weighting the leading text or reflowing rows, not by reducing supported names or removing accessibility scaling. Test32-character wide/Arabic names, compact widths, 2x font, English/Arabic labels, count/role nonzero visible bounds and control containment. Verify both local and multiplayer because they share these production screens.

## Exact source locations / SHA-256

- `/Users/abdelrahman/Projects/parlor/game-modes/mafia/src/commonMain/kotlin/com/parlor/games/mafia/ui/screens/postgame/PostGameScreen.kt` lines 43–80; SHA-256 `051c841fc161560caaffd140014e07ca741375851a86bc45e8a6fe999b94f4e4`.
- `/Users/abdelrahman/Projects/parlor/game-modes/mafia/src/commonMain/kotlin/com/parlor/games/mafia/ui/screens/setup/MafiaSetupScreen.kt` lines 169–175, 200–208, 344–412, 494–527; SHA-256 `63bc679b0b745c9b44e919b337fee9976f07d7ae1cd6831be158044bb4c4c1b5`.
- `/Users/abdelrahman/Projects/parlor/game-modes/mafia/src/commonMain/kotlin/com/parlor/games/mafia/ui/screens/vote/VoteCastScreen.kt` lines 124–149; SHA-256 `fbfa34d07191ac38fed6d6edf1afef23df5e7baf3c84757c1739d216b20c8e3b`.
- `/Users/abdelrahman/Projects/parlor/game-modes/mafia/src/commonMain/kotlin/com/parlor/games/mafia/ui/flow/passandplay/MafiaPassAndPlayPhaseRouter.kt` lines 126–130, 682–688, 748–752; SHA-256 `3df606724bceca7d5a78d6479bcdc44eec8c3f7f135228ccdd308dfc837c42ae`.
- `/Users/abdelrahman/Projects/parlor/game-modes/mafia/src/commonMain/kotlin/com/parlor/games/mafia/ui/flow/multidevice/MafiaMultiDevicePhaseRouter.kt` lines 187–192, 495–501, 616–630; SHA-256 `3d3a643854cdecf4f9ec4983ccdfd92b906c28b21942e758cffc862e449efff8`.
- `/Users/abdelrahman/Projects/parlor/shared/design-system/src/commonMain/kotlin/com/parlor/designsystem/components/ParlorCard.kt` lines 29–54; SHA-256 `e764e6c7e09dc72464ad3eb14b41328a609a7d47475941054fb7af292f7a9d51`.
- `/Users/abdelrahman/Projects/parlor/shared/design-system/src/commonMain/kotlin/com/parlor/designsystem/tokens/ParlorTypography.kt` lines 116–122; SHA-256 `eb442c5c7a41e34a4cf8e1c31466ef1a3e07e6582c3259d9a14c7942ee990f15`.
- `/Users/abdelrahman/Projects/parlor/shared/networking/src/commonMain/kotlin/com/parlor/networking/room/RoomInputPolicy.kt` lines 7, 28–46; SHA-256 `f4fe96788fbb709ac7e9a1d2b66a010b124d4456e5779272527f2392f43bd118`.

## Validation update

Reopened independent root report and execution receipts. One direct PostGame production UI test executed and failed nonzero-role-width assertion; role bounds left256,right256, name left64,right256. Earlier two cycles were audit-harness configuration/compilation failures, not application runtime failures. Scope Low visual final-role presentation, both routers reach same composable; no accessibility failure, privacy, game state or physical-device evidence implied. Root recorded Gradle stop/precise cleanup and unchanged tracked source.
