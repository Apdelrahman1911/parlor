# Final project summary (code-only)

For an engineer who has never seen this repository.

## What it is

**Parlor** is a Kotlin Multiplatform + Compose Multiplatform application that
hosts party games on one phone (pass-and-play) or several phones on the same
LAN (host-authoritative). Android and iOS are the intended Store products.
Desktop JVM exists to run the UI and deterministic tests; it is not packaged
as a Store app.

There are exactly two production games, registered as a hardcoded list in
`composeApp/.../di/ContentModule.kt`:

1. **Whodunit** (`GameId("whodunit")`) — murder mystery, 4–8 players.
   Modes: `classic-vote` (4–8) and `elimination` (5–8). Seven bundled JSON
   cases under the game module’s composeResources (one English
   `last-dinner`, six Arabic). Content is validated then played offline.
2. **Mafia** (`GameId("mafia")`) — hidden-role day/night, 5–16 players,
   one mode `classic`. Roles: Mafia, Detective (0–1), Doctor (0–1), Civilian.
   No bundled story JSON; setup is player count + settings presets.

There is no login, no analytics/crash SDK, no public-internet play, no
spectator, no host migration, no raw-IP join. Room join is: discover the
generic Parlor LAN service → type a 6-character code → host approves.

## How a process starts

- Android: `ParlorApplication` starts Koin `allModules`, `MainActivity` shows `App()`.
- Desktop: `Main.kt` same.
- iOS: `iosApp/iOSApp.swift` hosts `MainViewController()`.

`allModules` = core (clock, CSPRNG session seed, lifecycle, process-owned
multiplayer session) + both game Koin modules + content + storage +
platform storage + **required** `p2pTransportModule`. A missing P2pKit
artifact fails the build; the app does not degrade to pass-and-play-only.

## What the user sees

`App` is a Crossfade of Home, Settings, a Game launch, or local-resume
failure. Home lists the two bindings’ catalog cards plus continue tiles
for local snapshots and a resumable multiplayer credential.

Opening a game goes to `PlayModePickerScreen`: Pass and Play, Host, Join.
A **Solo** card is always drawn and always disabled (neither binding
advertises Solo).

**Local Whodunit:** pick a case → pick Classic/Elimination → names →
sequential dossier/clue/vote UI driven by `PassAndPlaySessionController`
and `WhodunitReducer`. Snapshots write to encrypted files named
`local-<hex seed>.snapshot.json`.

**Local Mafia:** setup draft → handoff covers so only one person sees a
role or night action → simultaneous night submits conceptually, sequential
phone passing in UI → day vote → postgame. Snapshots only on this path.

**Host:** permission gate (Android: not required for this transport; iOS:
unknown until a real LAN operation) → display name → (Whodunit also case
and mode) → `RoomTransport.create` → lobby, approve joiners, start
handshake, then game UI. The live room is owned by
`ProcessMultiplayerSessionOwner` so process recreation can restore the
route.

**Join:** name → 6-char code → wait for host → start barrier → peer UI
that **only installs host snapshots**.

Settings persist language override, theme tag, reduced motion.

## How multiplayer actually works

`shared/networking` defines protocol **4.2** with **exact** major.minor
compatibility. Messages are CBOR, unknown keys rejected, size-capped
(32 KiB commands, 256 KiB snapshot payloads, 272 KiB frames).

`P2pKitRoomTransport` is the only P2pKit user. It advertises a generic
same-app service (the human code is not in mDNS), rate-limits admission,
binds seats to authenticated transport identities, and **overwrites**
every peer `actor` field so payload impersonation does not work after
admission.

`HostAuthoritativeSessionCoordinator` is the only multiplayer reducer
owner. Start is `SessionStarting` → peer `Ready` → irreversible
`SessionStartCommitted`. Commands carry commandId, client sequence, and
expected revision. Duplicates are ledgered; rejected non-idempotent
actions are not auto-retried. Each peer snapshot is public projection
plus **that** player’s private slice. Host-only (full role map, killer
id, seed) stays on the host.

Rejoin is the same host and seat for 120 seconds via a credential in
platform secure storage (not the snapshot store). Host process death
destroys the room.

## Module map

```
composeApp          shell, DI, platform entry, Whodunit host/join UI (exception)
game-modes/*        rules, codecs, most UI, resources
shared/engine       GameDefinition / reducer / projection contracts (pure)
shared/session      SessionController, coordinators, handshake
shared/networking   protocol + RoomTransport interface
shared/transport-p2p P2pKit adapter (always included)
shared/content      case envelope + offline remote stub (+ unused Ktor class)
shared/storage      settings, snapshot store, secure storage
shared/core         ids, Result, clock, seed, SemVer
shared/design-system tokens, components, EN/AR, fonts
engine-testing / networking-testing   fixtures, not in production catalog
iosApp              thin Swift host, no CocoaPods
build-logic         parlor.kmp.library / compose / detekt
```

Shared modules do not depend on games. Engine does not import UI, DI,
transport, or `com.parlor.games`.

## Persistence and content

No SQL database. Settings are platform preferences. Snapshots are JSON
then platform AEAD (Android Keystore + no-backup dir; iOS Keychain +
exclude-from-backup; Desktop file key). Production remote content is
hard-`Unreachable`; seven Whodunit files are the catalog.

## Build and release reality

JDK 21, `./gradlew` 8.13, strict dependency verification (checksums, not
signatures), Detekt maxIssues 0. `productionCheck` is desktop tests +
Android unit + unsigned minified AAB + lint allowlist + script tests.
Apple linkage and simulator tests are a separate macOS job. Real P2pKit
join tests are `@Ignore`. There are no Android instrumented tests and no
Xcode test targets.

`com.parlor.app` is pinned by `verifyApplicationIdentities` and blocked
as a known Store collision. Candidate/promotion workflows exist as live
`workflow_dispatch` YAML and fail closed on that identity. **The app
cannot be uploaded to Play or App Store from this tree.**

Working tree at audit time was dirty and 35 commits behind `origin/main`.
Conclusions describe the **on-disk** tree.

## Maturity in one paragraph

The host-authoritative LAN design, privacy projections, offline content
pipeline, and Gradle gates are unusually thorough. Game rules are mostly
real and tested. The product is not Store-ready: colliding identity,
unproven physical LAN, Elimination OpenVote hole, Mafia disconnect
contract drift, accessibility gaps, and no device test matrix.
