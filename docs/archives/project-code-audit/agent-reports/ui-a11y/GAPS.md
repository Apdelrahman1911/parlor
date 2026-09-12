# Gaps (this workstream)

## Needs runtime (cannot close in a code-only pass)

- TalkBack and VoiceOver on every journey in UI-MAP.md
- iOS interactive swipe-back / RTL edge (no handler exists — confirm
  nothing else intercepts)
- Predictive Back / gesture conflict with Leave / pause
- Large-text / fontScale on `RoundTitleCardScreen`, `ClueRevealScreen`,
  lobby, Mafia setup
- Contrast of ember-on-noir and cover-screen text
- Whether merged names on `pressableSurface` rows are acceptable
- Whether a screen reader can leak a dossier during PnP reveal
- iOS Reduce Motion vs `rememberSystemReducedMotion`
- Desktop keyboard focus order
- Visual RTL of timer digits, room codes, and mixed EN/AR case titles

## Not fully read (sampled)

- Entire `WhodunitGameFlow.kt` / `WhodunitPhaseRouter.kt` /
  `MafiaPassAndPlayPhaseRouter.kt` / MD lobby files (~3k lines)
- Every Mafia night/vote/postgame screen body
- DossierCard internals
- Design-system tests (`ScreenHeaderDirectionTest`,
  `SessionExitBackPolicyTest`, `ProvideAppLanguageDesktopTest`)
- Case JSON bodies (only `language` tags)

## Out of scope

- Domain reducers / protocol / persistence
- Canonical audit docs 00–18
- Production edits

## Contract blind spots (automation)

`LocalizationResourceContractTest` does not see Android `res/`, iOS
InfoPlist, desktop window title, or case-language vs UI-language.

`ProductionUiAccessibilityContractTest` is grep-only: roles within 320
chars, a fixed scroll-file list, and selected overlay strings. It will
not fail UI-005, UI-006, UI-007 (unlisted files), or UI-008.
