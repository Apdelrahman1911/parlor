# Journeys

## Process start

- Android: `ParlorApplication` → Koin `allModules` → `MainActivity` → `App()`
- Desktop: `Main.kt` → Koin → `App()`
- iOS: `iOSApp.swift` → `MainViewController` → `App()`

`allModules` = core + whodunit + mafia + content + storage + platformStorage +
`p2pTransportModule`. Missing P2pKit is a startup/build failure, not a
pass-and-play-only fallback (`P2pBootstrap.kt`).

## App screens (`AppBackPolicy.kt`)

`Home` | `Settings` | `Game(launch)` | `LocalResumeFailure(sessionId)`

Back: Home = platform exit; Settings/failure = Home; Game = binding-owned.

## New local Whodunit

Home → binding `New` → PlayMode PassAndPlay → case list from
`CaseRepository.listCases(whodunit)` → mode Classic/Elimination → player names
→ `WhodunitGameFlow` + `PassAndPlaySessionController` + snapshot writer.

## New local Mafia

Home → PassAndPlay → `MafiaGameFlow` setup (player count/roles/settings) →
handoff night/day. Snapshot writer only on this PaP path.

## Host

PlayMode Host → permission gate → display name → (Whodunit: case+mode) →
create room via `RoomTransport` → lobby approve joiners → start handshake →
gameplay. Room retained by `ProcessMultiplayerSessionOwner`.

## Join

PlayMode Join → permission → name → 6-char code → host approval → peer lobby
→ start barrier → peer coordinator installs snapshots only.

## Resume

Home `produceState` reads `SnapshotStore` + `roomTransport.resumableSession()`.
Local: load + validate + `ResumeLocal`. MP: `ResumeMultiplayer` after
permission. Owned live route auto-restores if process still holds the room.
