# UI / a11y workstream — NOTES

CODE-ONLY. No `*.md` outside `project-code-audit/` was read. Production
was not modified. TalkBack / VoiceOver / visual RTL = Needs runtime.

## Shell architecture (from App.kt)

`App` is a four-destination Crossfade, not Navigation 3:

- `Home` / `Settings` / `LocalResumeFailure` / `Game(GameShellLaunch)`
- Language: `settings.languageOverride` → `AppLanguage.fromTag` → `ProvideAppLanguage`
- Motion: `settings.reducedMotion || rememberSystemReducedMotion()` → `ParlorTheme`
- Back: `appBackAction(screen)` + `PlatformBackHandler`
- Games enter only through `GameShellRegistry` / `GameShellRouter`

Bindings own setup + in-game sub-navigation. `verifyGameShellDispatch`
forbids game ids in `App.kt`, `AppBackPolicy.kt`, `LocalResumeRouter.kt`,
`HomeScreen.kt`, and `shell/multiplayer/**`.

## SessionController vs chrome

In-game actions that mutate rules go through `session.submit(...)`.
Chrome that must **not** call `SessionController`:

- Home / Settings / permission / name / join-code / case picker
- Host lobby `freezeAdmissions` / admit / decline (transport)
- Leave / save / discard (`snapshotWriter`, `ProcessMultiplayerSessionOwner`)

No in-game action button was found that looks like a reducer command
and then fails to call `SessionController`. Handoff taps only flip local
ceremony stage; the next confirm submits.

## Contract tests (what they actually assert)

`LocalizationResourceContractTest` walks every
`src/commonMain/composeResources/values/strings.xml`, requires
`values-ar/strings.xml`, equal key sets, no duplicate names, and matching
indexed `%N$X` tokens. It does **not** scan Android `res/`, iOS
`InfoPlist.strings`, unused keys, identical translations, plurals, or
case JSON.

`ProductionUiAccessibilityContractTest` is a source-grep suite. It does
not compose UI or run TalkBack/VoiceOver. See A11Y.md.

## Locale / RTL

`ProvideAppLanguage` sets `LocalLayoutDirection` from `AppLanguage` and
wraps `PlatformAppLocale` (process locale override + restore). Settings
can persist `null` (follow system), `en`, or `ar`. Android
`supportsRtl="true"`. No `padding(left/right)` / `Alignment.Left/Right`
in scoped UI.

## Content vs chrome

Chrome is Compose resources (EN+AR, 693 keys, full key parity).
Whodunit case payloads are separate: 1 `language=en` + 6 `language=ar`.
The case picker lists all of them and renders `CaseSummary.title` as-is.
