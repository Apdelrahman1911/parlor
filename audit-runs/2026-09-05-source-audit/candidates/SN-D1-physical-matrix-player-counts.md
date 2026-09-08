# SN-D1 — Physical full-game instructions specify too few devices

> **Final independent disposition (2026-09-05): DOCUMENTATION MISMATCH — Low; no game-rule defect.** Validator `/root`; see `validations/SN-D1-root.md`. The original candidate text below is preserved as the pre-validation record; its pending language is superseded by this disposition.

- **Finder:** `/root/session_cont`.
- **Independent validator:** requested from `/root`; pending. This candidate is **not self-confirmed**.
- **Proposed classification:** DOCUMENTATION MISMATCH / TEST-EVIDENCE GAP, **Low** severity. Not a game-engine or transport defect.
- **Source:** `main` at `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`; tracked sources unchanged.
- **Primary file:** `/Users/abdelrahman/Projects/parlor/docs/P2P_MANUAL_TEST.md:234–238,247–257`.

## Expected versus actual

A canonical physical validation row must specify enough real authenticated player seats to reach the game whose completion it requires. PHY-01 explicitly provisions **two Android phones**, A hosting and B joining, then requires completing a game and says both games can finish. PHY-02 specifies two iPhones and repeats that row. HOT-A1 and HOT-I1 specify owner A and connected client B then require a full game; HOT-A2 explicitly says **three devices finish one game**.

Current production LAN gameplay creates **one host player plus one player per admitted peer**, not pass-and-play seats behind a single peer. The two- and three-seat arrangements therefore cannot enable Start in either shipping game. Whodunit's **engine** permits Classic 4–8 and Elimination 5–8, but every **currently bundled case** is narrower: **exactly six players**. Mafia supports **5–16**. A six-device group can run both games; lowering a game rule is not an appropriate fix.

## Deterministic reachable source path

Paths below are under `/Users/abdelrahman/Projects/parlor`:

1. `composeApp/src/commonMain/kotlin/com/parlor/app/shell/game/WhodunitGameShellBinding.kt:41,54–58,78–84` exposes shipped multiplayer capacity `6..6`. All seven catalog resources under `game-modes/whodunit/src/commonMain/composeResources/files/cases/` declare `"supportedPlayerCounts": [6, 6]` at **line 9**; their headers were independently reopened. Catalog IDs are in `game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/content/BundledWhodunitCatalog.kt:10–18`.
2. `game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/domain/modes/WhodunitModes.kt:10–30` defines the broader engine ranges. `.../domain/rules/WhodunitRules.kt:44–52` intersects selected case and mode capacity rather than enlarging the content range.
3. `game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/ui/flow/multiplayer/WhodunitHostSessionFlow.kt:178–207,334–343,375–384,553–559` loads the case, derives capacity, constructs one player per seat, computes `connectedMembers.size + 1`, and disables Start outside that capacity. Two or three admitted devices leave Start disabled.
4. `game-modes/mafia/src/commonMain/kotlin/com/parlor/games/mafia/domain/settings/MafiaSettings.kt:28–31,64–66` enforces 5–16 players. `.../ui/flow/multidevice/MafiaHostLobbyFlow.kt:280–290,318–329,487–493` constructs that same one-member/one-player roster and guards Start by those bounds.

## Reproduction

On the recorded checkout, follow PHY-01 literally: install the app on two Android devices, host a bundled Whodunit case on A, join and approve B, and attempt the required full game. The lobby count is two, supported range is six, and `canStart` is false. The same arithmetic blocks Mafia at two and HOT-A2 at three. This is a complete source-level proof of procedure infeasibility; **no physical run was performed by the finder**.

## Counter-evidence and scope limits

- PHY-05 says "at least two peers", which technically permits five peers; that row is underspecified rather than strictly impossible. Its three-device title/evidence instructions should be clarified, not misreported as forbidding a larger group.
- PHY-03/04 name an Android/iPhone pair without explicitly forbidding additional peers. The decisive contradictions are the explicitly two-phone/full-game and three-device/full-game rows above.
- HOT-I2 itself only specifies joining/transport behavior, not an explicit full-game finish; it is not independently alleged impossible. References that expand it into "play" need the same capacity clarification.
- `docs/MULTIPLAYER_PLAYTEST.md:12–13` says "at least three", so six meets that prerequisite. It should state the game-specific minimum for its full-game step at `:38–50`, but this wording is not a second mathematical contradiction.
- Two-device LAN discovery, admission, terminal and cleanup testing is useful and valid. The issue is attaching a full-game completion criterion to an insufficient roster, not allowing pairwise network tests.
- Pass-and-play can host multiple local people on one phone, but these rows explicitly test LAN admission and synchronized peer commands. Switching modes would not validate their intended transport path.
- Existing `MultiplayerDocumentationContractTest.kt:103–123` checks runtime markers, not physical-roster feasibility. `WhodunitShippedPlayerCountTest.kt:9–20` checks the shipped range but does not reconcile the runbook.

## Recommended correction and evidence

Keep pairwise transport/admission cases; separate full-game scenarios with exactly **six devices for currently bundled Whodunit**, and 5–16 for Mafia. Preserve the OS/host-direction/hotspot coverage within those sufficient rosters. Make extra participating devices explicit in the evidence template and do not substitute mocks for actual device seats. Add a maintained game-capacity reference or structured runbook check tied to the shipped catalog.

Current text hashes and reviewed ranges are in `coverage/reviews-session_cont.jsonl`; supporting reconciliation is in `reviews/seven-docs-session_cont.md`. This report proposes a documentation correction only; no repository source, game rules or existing procedure was changed.
