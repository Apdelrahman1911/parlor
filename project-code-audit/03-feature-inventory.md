# Feature inventory

Derived from composition root + bindings + reducers + transport. Status is
runtime reachability, not “a class exists.”

## Shipping catalog

| Feature | Status | Wired how | Evidence |
|---|---|---|---|
| Whodunit game (`GameId("whodunit")`) | Fully | `WhodunitGameShellBinding` in `contentModule` | `WhodunitIds`, `WhodunitDefinition` |
| Mafia game (`GameId("mafia")`) | Fully | `MafiaGameShellBinding` in `contentModule` | `MafiaIds`, `MafiaDefinition` |
| Third production game | Missing | n/a | only two bindings in `ContentModule.kt` |
| Engine-testing RoundRobin game | Test-only | not in `allModules` | `RoundRobinAnnounceGame` |

## Entry modes

| Feature | Status | Notes |
|---|---|---|
| Pass-and-play | Fully | Both games. Sequential handoff UI. |
| Host LAN | Fully (code) | Both games. Physical LAN unproven (SN-003). |
| Join by 6-char room code | Fully (code) | Discovery + code + host tap. No raw IP. |
| Solo | Dead UI | `GameEntryMode.Solo` enum; both bindings omit it; picker shows disabled card (PF-001 / UI-001). |
| Spectators | Missing | no types |
| Host migration | Missing | host death ends room |

## Whodunit gameplay

| Feature | Status |
|---|---|
| Classic Vote (4–8) | Fully |
| Elimination (5–8) | Fully, with OpenVote hole (F-003 / GR-001) |
| 7 bundled cases (1 EN, 6 AR) | Fully; catalog 1:1 with JSON (ST-011) |
| Case language filter | Missing (UI-004) |
| Seeded killer + clues | Fully |
| Sequential voting, ties, revote | Fully |
| Discussion timer | Fully (Whodunit only) |
| Pause / privacy overlay | Fully (Whodunit) |
| Continue-without → end + reveal | Fully after assignment |
| Local snapshot resume | Fully (PaP) |
| Rematch / replay | Fully from PostGame if no dropped |

## Mafia gameplay

| Feature | Status |
|---|---|
| Classic 5–16 | Fully |
| Roles Mafia/Detective/Doctor/Civilian | Fully |
| Presets + ApplySettings | Fully |
| Timed night/discussion/vote | Stub/rejected (`TimersNotSupported`, GR-006) |
| Simultaneous night + day vote | Fully |
| Host auto-progression | Fully (code) |
| Disconnect pause | **Not implemented**; kdoc claims pause (F-004 / GR-003) |
| Local snapshot | PaP only (ST-009). MP never writes. |
| Continue-without → end game | Fully after start |

## Shell / platform

| Feature | Status |
|---|---|
| Home catalog | Fully |
| Settings: language, theme, reduced motion | Fully |
| Local resume tiles + failure/discard | Fully |
| MP rejoin tile | Fully (code) |
| Process-owned MP restore | Fully (code) |
| Android system Back | Fully |
| iOS swipe-back / Desktop Escape | Missing (UI-008) |
| EN/AR Compose strings | Fully (693/693 keys) |
| Android launcher AR name | Missing (UI-002) |
| Analytics / crash / accounts | Missing |
| Remote case download | Configured-not-exercised (`KtorRemoteCaseDataSource` public, DI offline) |

## Multiplayer internals (user-visible effects)

| Feature | Status |
|---|---|
| Exact protocol 4.2 | Fully |
| Host-only reducer | Fully |
| Public+own-private snapshots | Fully (code); radio unproven |
| 120s rejoin same seat | Fully (code) |
| Room-code not in mDNS | Fully |
| Admission rate limit | Fully; burns tokens before code check (SN-006) |
