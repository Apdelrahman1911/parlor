# Coverage ledger

Last updated: 2026-09-01 (canonical reports written)

## Exclusions (unchanged)

`.git`, `.gradle`, `.kotlin`, `build/`, `.idea`, all repo `*.md` outside
this folder, binary fonts/png (existence only), `gradle-wrapper.jar`.

## Workstream reports (reviewed by lead)

| Stream | Files | Lead action |
|---|---|---|
| architecture | 5 | AR-001–009 accepted; graph claims spot-checked |
| game-rules | 6 | GR-001/002/003 verified in reducer/validator source |
| session-network | 7 | SN-001/007 mailbox + protocol 4.2 verified |
| security-privacy | 5 | SP-001 seed filename verified in WhodunitGameFlow |
| storage-data | 6 | ST-* accepted; catalog 1:1 not re-hashed |
| ui-a11y | 7 | UI-001/008 verified via bindings + PlatformBackHandler |
| tests-build | 6 | TB-001/002 verified via release-policy + workflow YAML |
| product-features | 5 | Lead-authored after agent drop |
| performance | 2 | Lead-authored after agent drop |

## Lead-traced production symbols (not exhaustive of 442 kt)

App.kt, AppBackPolicy, ContentModule, AppModule, P2pBootstrap,
GameShellRegistry, both bindings (first 100 lines + capabilities),
WhodunitReducer openVote/advanceFromDiscussion, WhodunitStateValidator
phase shape, MafiaReducer markDisconnected, MafiaState kdoc,
Protocol.kt version, AuthoritativeSessionCoordinator mailbox,
P2pTrafficPolicy limits, P2pPermissionGate, AndroidManifest,
release-policy.json, testing-candidate.yml header, Whodunit/Mafia
Definition + Ids, Settings.kt, FileBacked path via reports.

## Not personally line-read by lead (relied on agent ledgers)

Full `P2pKitRoomTransport.kt` 4589 lines (agent claimed chunked read;
Critical/High from that file independently checked only for mailbox,
rejoinToken null via report, actor-stamp via report+Protocol kdoc).
Every game UI screen. Every test assertion except those cited in
verified findings. Entire `verification-metadata.xml` body.

**Residual coverage risk:** transport file interior races beyond SN-005
as reported. A follow-up should chunk-read `replaceSession` if MP ship
is imminent.

## Commands

| Command | Result | Proves |
|---|---|---|
| `./gradlew :shared:core:desktopTest :shared:engine:desktopTest --offline` | FAIL ~2s | Offline cache missing assertk/opentest4j. Not a product test fail. Also: Gradle 8.13 deprecation; iosX64Test disabled on arm64. |
| `productionCheck` / `allTests` | **not run** | Expensive; would not close F-007/F-001 |

## Resume

Canonical 00–18 exist. Do not relaunch 9 agents. Next useful work is
implementation (out of scope) or device/LAN evidence (external).
