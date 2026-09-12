# Feature inventory (lead)

Status key: Fully | Partial | Stub | Test-only | Dev-only | Dead | Configured-not-exercised | Missing

| Feature | Status | Wired | Evidence |
|---|---|---|---|
| Home catalog (Whodunit + Mafia) | Fully | yes `ContentModule` + `HomeScreen` | `DefaultGameShellRegistry` list of two bindings |
| Open game → play-mode picker | Fully | yes | both bindings start `Setup` → `PlayModePickerScreen` |
| Solo play | Dead UI | picker renders; never enabled | `GameEntryMode.Solo` exists; both bindings omit it |
| Pass-and-play Whodunit | Fully | yes | case picker → mode → `WhodunitGameFlow` |
| Pass-and-play Mafia | Fully | yes | `MafiaGameFlow` (no case catalog) |
| Host LAN Whodunit | Fully (code) | yes | permission → name → case → mode → `WhodunitHostSessionFlow` |
| Host LAN Mafia | Fully (code) | yes | permission → name → `MafiaHostLobbyFlow` |
| Join by room code | Fully (code) | yes | permission → name → `JoinPromptScreen` → peer lobby |
| Local snapshot resume | Partial | Whodunit yes; Mafia PaP yes, MP no | `ResumeLocal` + `SnapshotStore`; Home tiles |
| Multiplayer rejoin | Fully (code) | yes | `roomTransport.resumableSession()` + `ResumeMultiplayer` |
| Settings language/theme/reduced-motion | Fully | yes | `SettingsStore` + `SettingsScreen` |
| P2P permission rationale | Partial | yes | Android `NotRequired`; iOS evidence-based; Desktop no-op |
| Process-owned MP session restore | Fully (code) | yes | `ProcessMultiplayerSessionOwner` → `restoreOwned` |
| Local resume failure / discard | Fully | yes | `AppScreen.LocalResumeFailure` |
| Analytics / accounts / internet play | Missing | n/a | no SDK, no login, no remote play path in DI |
| Spectators / host migration / raw IP | Missing | n/a | no types in shell; protocol has no spectator role |

`GameEntryMode.Solo` is a real enum used only as a disabled card. Not a
shipping play mode.
