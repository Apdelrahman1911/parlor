# Screen map

Journeys start at `App.kt`. Game ids never appear in Home; bindings supply
catalog copy and own every sub-route.

## App destinations

| Screen | Composable | Back (`appBackAction`) | SessionController |
|---|---|---|---|
| Home | `HomeScreen` | AllowPlatformExit | no |
| Settings | `SettingsScreen` | NavigateHome | no — `SettingsStore` |
| Local resume failure | `LocalResumeFailureScreen` | NavigateHome | no — snapshot discard |
| Game | `GameShellBinding.Content` | DelegateToGame | binding-owned |

Platform Back is installed only on Android (`BackHandler`). iOS and Desktop
`PlatformBackHandler` are no-ops. Visible leave/back chrome is required on
those hosts.

## Home → game

`HomeScreen` lists `gameShellRegistry.all`. Open → `GameShellLaunch.New`.
Continue tiles → `ResumeLocal` / `ResumeMultiplayer`. Owned process route
restores `RestoreOwnedMultiplayer`.

Both shipping bindings advertise PassAndPlay + Host + Join. Neither
advertises Solo. The picker still **shows** a disabled Solo card.

## Whodunit binding (`WhodunitShellScreen`)

```
New → Setup (PlayModePicker)
  PassAndPlay → LocalCasePicker → LocalGame (WhodunitGameFlow)
  Host → HostPermission → HostName → HostCasePicker → HostMode → HostLobby
  Join → JoinPermission → JoinName → JoinPrompt → PeerLobby
ResumeLocal → LocalGame
ResumeMultiplayer → ResumePermission → ResumePeer
RestoreOwned Host → HostLobby
RestoreOwned Peer → PeerLobby or ResumePeer
```

`LocalGame` pre-session (inside `WhodunitGameFlow.ConfiguredFlow`, after
session exists only after names):

`ModeSelectionScreen` → `PlayerCountScreen` → `PlayerEntryScreen` →
`SessionDrivenFlow` → `HostPhaseRouter`.

In-session host/local phases (`HostPhaseRouter`):

| Phase | Screen | submit |
|---|---|---|
| Setup | `LoadingScreen` | auto `AssignRoles` |
| PublicIntro | `PublicIntroScreen` | `AdvanceFromIntro` (+ auto ack on MD) |
| RulesBriefing | `RulesBriefingScreen` | `AdvanceBriefingCard` |
| CharacterReveal PnP | Handoff → Gate → Dossier → Hide | `Start`/`CompleteCharacterReveal`, `RequestReroll` |
| CharacterReveal MD | same, own player only | same; host auto-advances |
| Round | title / clue / discussion / vote | `RevealNextClue`, timer, `AdvanceFromDiscussion`, vote actions |
| FinalVote / TiedRevote | ballot / tied card | `CastVote` / `RefuseToVote` / `OpenVote` / `CloseVote` |
| Reveal | `RevealStageScreen` | host `AcknowledgeReveal`; peer `onAcknowledge=null` |
| PostGame | `PostGameScreen` | `BeginReplay` or exit |

Peer (`PeerPhaseRouter`): waiting covers for intro/briefing/round/postgame;
interactive only on own reveal and own ballot. Reveal has no ack.

Overlays on local/host play: `PauseOverlay` (rounds, not during vote),
`PrivacyConcernDialog` (reveal), `SessionExitConfirmation`,
`ContinueWithoutDialog` + `HostDisconnectedOverlay` (MD host),
`ReconnectingOverlay` / `OfflineBanner` (peer).

## Mafia binding (`MafiaShellScreen`)

```
New → Setup (PlayModePicker)
  PassAndPlay → LocalGame (MafiaGameFlow)
  Host → HostPermission → HostName → HostLobby
  Join → JoinPermission → JoinName → JoinPrompt → PeerLobby
Resume / RestoreOwned — same shape as Whodunit, no case/mode picker
```

Local pre-session: `MafiaPlayerCountScreen` → `MafiaPlayerEntryScreen` →
`SessionDrivenFlow` → `MafiaPassAndPlayPhaseRouter`.

PnP phases: Setup (`MafiaSetupScreen` → `ConfigureAndStart`) →
RoleAssignment (Handoff → Gate → Role card → Hide) → Night (per-role
target pickers, same ceremony) → NightAnnouncement → Discussion →
Voting → VoteAnnouncement → PostGame.

MD: `MafiaMultiDevicePhaseRouter`. Host configures in Setup; peers see
waiting. Night resolution is **not** a peer/host button — retained host
progression driver. Peers ack announcements only when
`canAcknowledgeAnnouncement`.

Mafia has **no** pause overlay and **no** privacy-reroll dialog. Privacy
is the handoff cover (`MafiaHideAndPass` / `MafiaRoleRevealGate`).

## Shared chrome screens

| Screen | Used by |
|---|---|
| `PlayModePickerScreen` | both bindings |
| `NameInputScreen` / `JoinPromptScreen` | both bindings |
| `P2pPermissionRationaleScreen` | both bindings |
| `SessionExitConfirmation` / `SessionExitAffordance` | local + MD host/peer |
| `ReconnectingOverlay` / `OfflineBanner` / `HostDisconnectedOverlay` | MD |

## Dead / unwired composables (shipping)

Wired and used: all `*Screen` functions under game `ui/screens` and
composeApp shell screens.

Unwired in production UI (still compiled):

- `ParlorBottomTabBar` — definition only
- `SectionDivider` — definition only
- `ParlorScrim` — definition only

`BringIntoViewOnFocus` is used (via `ParlorTextField` and player-entry
fields). Home comment claiming “no tabs” matches: tab bar is leftover.

Visible-but-disabled: Solo card on the play-mode picker (`UI-001`).
`UnsupportedLocalPlayModeScreen` is a recovery surface, not a catalog
entry.

## Controls that do not call SessionController

Expected (not findings):

- Catalog / settings / recovery / permission / name / join / case pick
- Lobby admit / decline / start (`freezeAdmissions`)
- Leave / save / discard

Ceremony-only (local stage, then a later submit):

- Reveal/vote/night handoff taps
- Dossier “done” → Hide
- Pause affordance **does** submit `Pause`

No disconnected in-game command button found (a button that claims to
cast/advance/configure and then no-ops).
