# UI / a11y workstream — FILE LEDGER

Status: R = read in full or near-full. S = sampled (head / targeted grep). U = unread (see GAPS.md).

## Contract tests

| File | Status | Notes |
|---|---|---|
| composeApp/src/desktopTest/kotlin/com/parlor/app/LocalizationResourceContractTest.kt | R | XML key + format parity only |
| composeApp/src/desktopTest/kotlin/com/parlor/app/ProductionUiAccessibilityContractTest.kt | R | source-string assertions |
| composeApp/src/commonTest/kotlin/com/parlor/app/AppBackPolicyTest.kt | R | Home/Settings/Game/resume-failure |

## App shell

| File | Status |
|---|---|
| composeApp/.../App.kt | R |
| composeApp/.../AppBackPolicy.kt | R |
| composeApp/.../PlatformBackHandler.kt + android/ios/desktop | R |
| composeApp/.../LocalResumeRouter.kt | S |
| composeApp/src/androidMain/.../MainActivity.kt | R |
| composeApp/src/iosMain/.../MainViewController.kt | R |
| composeApp/src/desktopMain/.../Main.kt | S |
| composeApp/src/androidMain/AndroidManifest.xml | R |
| composeApp/src/androidMain/res/values/strings.xml | R |

## Shell screens / bindings

| File | Status |
|---|---|
| shell/home/HomeScreen.kt | R |
| shell/home/LocalResumeFailureScreen.kt | S |
| shell/home/HomeRecoveryAvailability.kt | S |
| shell/settings/SettingsScreen.kt | R |
| shell/settings/SettingsMutationDispatcher.kt | S |
| shell/playmode/PlayModePickerScreen.kt | R |
| shell/multiplayer/NameInputScreen.kt | R |
| shell/multiplayer/JoinPromptScreen.kt | R |
| shell/ErrorMessages.kt | R |
| permissions/P2pPermissionRationaleScreen.kt | S |
| shell/game/GameShellRegistry.kt | R |
| shell/game/GameShellSupport.kt | R |
| shell/game/WhodunitGameShellBinding.kt | R |
| shell/game/MafiaGameShellBinding.kt | R |
| shell/game/whodunit/WhodunitCasePickerScreen.kt | R |
| shell/game/whodunit/WhodunitHostSessionFlow.kt | S |
| shell/game/whodunit/WhodunitPeerSessionFlow.kt | S |

## Design system

| File | Status |
|---|---|
| localization/ProvideAppLanguage.kt | R |
| localization/AppLanguage.kt | R |
| localization/LocalAppLocale.kt + android/ios/desktop | R |
| motion/ParlorMotion.kt | R |
| motion/SystemReducedMotion.kt + android | R |
| theme/ParlorTheme.kt | S |
| components/ParlorButton.kt | R |
| components/ScreenHeader.kt | R |
| components/SessionExitControls.kt | R |
| components/ParlorActivityIndicator.kt | R |
| components/CandleFlame.kt | R |
| components/ReconnectingOverlay.kt | S |
| components/OfflineBanner.kt | R |
| components/Pressable.kt | R |
| components/ParlorTextField.kt | R |
| components/ParlorBottomTabBar.kt | S |
| components/EyebrowLabel.kt | R |
| components/BringIntoViewOnFocus.kt | S |
| composeResources values + values-ar | S (parsed keys) |

## Whodunit UI

| File | Status |
|---|---|
| ui/flow/WhodunitGameFlow.kt | S (setup + session + pause/exit + host attach) |
| ui/flow/WhodunitPhaseRouter.kt | S (~1100 lines; host/peer/reveal/vote) |
| ui/flow/passandplay not present — local lives in WhodunitGameFlow | — |
| ui/screens/safety/PrivacyConcernOverlay.kt | R |
| ui/screens/safety/PauseOverlay.kt | R |
| ui/screens/safety/PrivacyConcernPolicy.kt | R |
| ui/screens/reveal/CharacterRevealScreens.kt | R |
| ui/screens/peer/PeerWaitingForHostScreen.kt | R |
| ui/screens/vote/VoteScreens.kt | R |
| ui/screens/round/RoundScreens.kt | R |
| ui/components/WaxSealReveal.kt | R |
| ui/components/CandlelitCover.kt | R |
| ui/components/TimerRibbon.kt | R |
| ui/components/DossierCard.kt | S |
| remaining screens (postgame, intro, briefing, mode/player setup) | S via grep |
| composeResources values + values-ar | S (parsed keys) |

## Mafia UI

| File | Status |
|---|---|
| ui/flow/passandplay/MafiaGameFlow.kt | S |
| ui/flow/passandplay/MafiaPassAndPlayPhaseRouter.kt | S |
| ui/flow/multidevice/MafiaMultiDevicePhaseRouter.kt | S |
| ui/flow/multidevice/MafiaHostLobbyFlow.kt | S |
| ui/flow/multidevice/MafiaPeerLobbyFlow.kt | S |
| ui/screens/handoff/MafiaHandoffScreens.kt | R |
| ui/screens/night/TargetPickerScreen.kt | R |
| ui/screens/discussion/DiscussionScreen.kt | R |
| ui/screens/setup/MafiaSetupScreen.kt | S |
| ui/components/MafiaCover.kt | R |
| remaining night/vote/postgame/setup screens | S via grep |
| composeResources values + values-ar | S (parsed keys) |

## Native loc

| File | Status |
|---|---|
| iosApp/iosApp/en.lproj/InfoPlist.strings | R |
| iosApp/iosApp/ar.lproj/InfoPlist.strings | R |
| iosApp/iosApp/Info.plist | R |

## Not in this workstream

Domain reducers, networking, storage, case JSON bodies (only language tags sampled).
