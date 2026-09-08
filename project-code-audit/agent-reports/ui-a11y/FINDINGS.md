# UI / a11y findings

IDs UI-001+. Evidence is file:line. Device TalkBack/VoiceOver = Needs runtime.

---

## UI-001 Solo card is visible but permanently disabled for both games

- **Severity:** Medium
- **Confidence:** High
- **Evidence:**
  - `WhodunitGameShellBinding.kt:42-48` capabilities = PassAndPlay, Host, Join
  - `MafiaGameShellBinding.kt:36-42` same
  - `WhodunitPlayModePolicy.kt:14-15` local entry is PassAndPlay only
  - `PlayModePickerScreen.kt:139-153` always renders Solo; `enabled = availability.solo`
  - `PlayModePickerScreen.kt:104-105` comment: unsupported cards stay visible
- **Why it matters:** Every new-game path shows a dead Solo control.
  Assistive users get a disabled radio-like card with
  `setup_mode_unavailable`. Product intent may be “teach the modes”;
  the control is still a disconnected action surface.

---

## UI-002 Android launcher name has no Arabic counterpart

- **Severity:** Low
- **Confidence:** High
- **Evidence:**
  - `composeApp/src/androidMain/res/values/strings.xml` `app_name` translatable=false “Parlor”
  - no `res/values-ar/`
  - iOS `en.lproj` / `ar.lproj` InfoPlist: “Parlor” / “بارلور”
  - `LocalizationResourceContractTest` only walks Compose `composeResources`
- **Why it matters:** Arabic Android users keep a Latin launcher label
  while iOS translates. Native usage description on iOS is localized;
  Android has no equivalent string resource.

---

## UI-003 Eyebrow / settings link use default-locale `uppercase()`

- **Severity:** Low
- **Confidence:** High
- **Evidence:**
  - `EyebrowLabel.kt:17` `text.uppercase()`
  - `HomeScreen.kt:231` `settingsLabel.uppercase()`
  - `SettingsScreen.kt:192` `label.uppercase()`
  - `AppLanguage` supports `ar`; `ProvideAppLanguage` mutates process locale
- **Why it matters:** Chrome labels are locale-sensitive. Arabic is a
  no-op; a Turkish system locale (or any future locale) can recase
  Latin chrome incorrectly. Not a missing translation, but a loc/a11y
  footgun on every eyebrow.

---

## UI-004 Case picker does not filter or label case language

- **Severity:** Medium
- **Confidence:** High
- **Evidence:**
  - `WhodunitCasePickerScreen.kt:83-88` `repository.listCases(WhodunitIds.GameId)`
  - `CaseRow` renders `summary.title` / `subtitle` with no `summary.language`
  - bundled cases: `last-dinner.json` `language=en`; six others `language=ar`
  - `AppLanguage` comment: case content is **not** chrome-translated
- **Why it matters:** An English-UI user sees Arabic case titles (and
  vice versa) with no language chip. Picking the “wrong” language case
  is silent. Host and local pickers share this screen.

---

## UI-005 Custom pressable rows have Role.Button but no accessible name override

- **Severity:** Medium
- **Confidence:** High
- **Evidence:**
  - `Pressable.kt:52-57` `.clickable(..., role = Role.Button)` — no
    `contentDescription`
  - `WhodunitCasePickerScreen.kt:166` case cards
  - `VoteScreens.kt:105` vote candidate rows
  - Contrast: `ParlorButton` requires `contentDescription` (`ParlorButton.kt:52,116`)
  - `ProductionUiAccessibilityContractTest` only requires `role = Role.`
    within 320 characters
- **Why it matters:** TalkBack will merge visible title+subtitle+meta
  into one unlabeled button, or announce a candidate name without
  “vote for”. The contract test cannot catch this. Needs runtime to
  confirm the merged name is usable.

---

## UI-006 Pass-and-play covers are unlabeled full-screen buttons

- **Severity:** Medium
- **Confidence:** Medium (runtime needed for AT wording)
- **Evidence:**
  - `CandlelitCover.kt:32-36` fillMaxSize `.clickable(role = Role.Button)`
  - `HideScreen.kt:70-77` same when `onTap != null`
  - `MafiaCover.kt:27-31` / `60-69` same
  - No `contentDescription` / `onClick(label=)`
- **Why it matters:** The only action is “tap anywhere to continue”.
  Visible title/subtitle may become the name; there is no explicit
  “pass the phone” / “I’m alone” action string on the cover itself
  (gate screens that use `ParlorButton` / wax seal are better).
  Privacy ceremony depends on this control being obvious to AT users.

---

## UI-007 Several fullscreen game cards are not scrollable

- **Severity:** Medium
- **Confidence:** High
- **Evidence:**
  - `RoundTitleCardScreen` (`RoundScreens.kt:47-51`) SpaceBetween, no
    `verticalScroll`
  - `ClueRevealScreen` (`RoundScreens.kt:91-96`) same
  - Contract list in `ProductionUiAccessibilityContractTest.kt:227-248`
    does not include these files
  - Contrast: `DiscussionScreen`, covers, pause, privacy do scroll
- **Why it matters:** Large text / long authored titles can push the
  primary button off-screen with no scroll. The a11y contract’s
  “actions reachable with large text” check is incomplete.

---

## UI-008 iOS and Desktop install no platform Back handler

- **Severity:** Medium
- **Confidence:** High
- **Evidence:**
  - `PlatformBackHandler.ios.kt:6-9` and `.desktop.kt:6-9` = `Unit`
  - `App.kt:183-191` still calls the expect
  - After session start, `SessionExitAffordance` is composed
  - Setup/name/join/case/mode screens only have `ScreenHeader` chevrons
  - No Navigation 3 / iOS swipe-back integration in `App`
- **Why it matters:** Android system Back is the only implicit back.
  iOS users cannot swipe to leave; they must hit the chevron or Leave.
  Desktop Escape does nothing. Cross-platform Back policy is Android-
  only at the platform layer.

---

## UI-009 Dead design-system chrome still ships

- **Severity:** Low
- **Confidence:** High
- **Evidence:**
  - `ParlorBottomTabBar` referenced only by its own file
  - `SectionDivider`, `ParlorScrim` same
  - `HomeScreen.kt:60` “no tabs”
- **Why it matters:** Unused interactive primitives inflate the a11y
  surface and can be wired later without the catalog/contract tests.
  Not user-visible today.
