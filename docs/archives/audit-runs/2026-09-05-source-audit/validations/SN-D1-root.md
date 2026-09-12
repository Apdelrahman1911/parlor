# SN-D1 — independent root validation

**DOCUMENTATION MISMATCH — Low.** Finder `/root/session_cont`; validator `/root`.
Source main `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree
`db7f3d2afe73a13628296daee2cce71165eebc8d`; absolute base `/Users/abdelrahman/Projects/parlor/`.

Reopened `docs/P2P_MANUAL_TEST.md:229–267`. PHY-01 explicitly provisions two Android phones and
requires game completion; PHY-02 repeats this with two iPhones. HOT-A2 explicitly says three devices
finish a game. HOT-A1/HOT-I1 list one owner and one client with full-game criteria.

Independently checked shipped capacity in `composeApp/src/commonMain/kotlin/com/parlor/app/shell/game/WhodunitGameShellBinding.kt:35–86`,
all bundled story headers and engine/case range intersection (prior root pass), and actual roster/UI:
`game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/ui/flow/multiplayer/WhodunitHostSessionFlow.kt:328–348,370–388,540–562`.
It creates one host Player and one Player per admitted peer, and enables Start only at the supported
count (currently exactly6). There is no pass-and-play seat multiplexing behind a LAN peer.

Mafia's independent sibling proof: `game-modes/mafia/src/commonMain/kotlin/com/parlor/games/mafia/domain/settings/MafiaSettings.kt:19–85`
requires5–16, and `.../ui/flow/multidevice/MafiaHostLobbyFlow.kt:278–294,315–331,481–496`
uses the same one-device/one-seat count and disables Start below5. Two or three devices therefore
cannot execute the named full-game completion gate. This is deterministic arithmetic/source proof,
not a fabricated physical run.

Counter-evidence: PHY-05 says at least two peers, allowing a larger valid roster despite its title.
Pairwise discovery/admission/transport tests remain valid. Other rows not explicitly limiting the
roster are underspecified rather than separately impossible. Engine Whodunit ranges are broader than
the currently bundled content; do not change game rules or infer extra bundled support.

Recommend splitting pairwise transport checks from full games (6devices for the current Whodunit
catalog;5–16 for Mafia), recording all participating devices and keeping OS/host/hotspot direction
evidence separate. No code defect or physical test result is asserted. Exact file hashes are in
`validations/pending-root-source-hashes.json` and original range receipts.
