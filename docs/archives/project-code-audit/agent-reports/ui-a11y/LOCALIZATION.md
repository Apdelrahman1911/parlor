# Localization coverage

Shipping chrome locales: `en` (base) and `ar`. No other Compose
`values-*` trees. No `<plurals>` / `<string-array>` in any bundle.

## Compose resource parity (parsed)

| Bundle | EN keys | AR keys | only-EN | only-AR | format mismatches |
|---|---|---|---|---|---|
| composeApp | 194 | 194 | 0 | 0 | 0 |
| shared/design-system | 16 | 16 | 0 | 0 | 0 |
| game-modes/whodunit | 220 | 220 | 0 | 0 | 0 |
| game-modes/mafia | 263 | 263 | 0 | 0 | 0 |
| **total** | **693** | **693** | **0** | **0** | **0** |

Every key is referenced from Kotlin (unused-key scan = 0). Empty values:
none.

`LocalizationResourceContractTest` encodes this parity for Compose
bundles only.

## Intentional identical EN/AR values

| Key | Value | Verdict |
|---|---|---|
| `settings_language_english` | English | language endonym — OK |
| `settings_language_arabic` | العربية | language endonym — OK |
| `dossier_killer_timeline_row_format` | `%1$s — %2$s` | punctuation template — OK |
| `md_host_player_bullet_format` | `· %1$s` | bullet template — OK |
| `md_peer_room_format` | `%1$s · %2$s` | separator template — OK |

## Native / platform strings

| Surface | EN | AR | In loc contract test? |
|---|---|---|---|
| iOS `InfoPlist.strings` `CFBundleDisplayName` | Parlor | بارلور | no |
| iOS `NSLocalNetworkUsageDescription` | localized | localized | no |
| iOS `Info.plist` fallback `NSLocalNetworkUsageDescription` | English only | — | no |
| `CFBundleLocalizations` | en, ar | — | — |
| Android `res/values/strings.xml` `app_name` | Parlor (`translatable="false"`) | **no `values-ar`** | no |
| Desktop window title (`Main.kt`) | `"Parlor"` literal | n/a | no |

Android launcher name stays Latin “Parlor” in Arabic. iOS display name
is translated. Inconsistent native branding (`UI-002`).

## Runtime language / RTL

- Settings: System / English / Arabic. `null` override = follow system.
- `ProvideAppLanguage` sets `LocalLayoutDirection` (AR = Rtl) and
  platform locale for Compose resources.
- Platform overrides are `DisposableEffect`-scoped (Android/iOS/Desktop).
- Android `supportsRtl="true"`. ScreenHeader chevron flips (`‹` / `›`).
- No physical left/right padding in scoped UI.

`AppLanguage.displayName` is unused by Settings (resources win).

## Hardcoded user-facing chrome

Most UI uses `stringResource`. Remaining literals:

| Site | Literal | Notes |
|---|---|---|
| `WhodunitGameFlow.PauseAffordance` | `"II"` | pause glyph; a11y string exists |
| `TimerRibbon` | `"$mm:$ss"` / `"/ $totalMm:$totalSs"` | numeric clock |
| `RoundScreens` discussion list | `"·  ${c.text}"` | bullet + case text |
| `TiedRevoteScreen` | `tiedNames.joinToString(" · ")` | names |
| `EyebrowLabel` | `text.uppercase()` | default locale (`UI-003`) |
| `HomeTopBar` | `settingsLabel.uppercase()` | same |
| Case picker | `CaseSummary.title` / `subtitle` | authored case language, not chrome |
| Desktop `Window(title=)` | `"Parlor"` | desktop-only |

No English sentence literals in scoped Compose screens.

## Content language (not chrome)

Whodunit bundled cases: `last-dinner` = `en`; six others = `ar`.
`WhodunitCasePickerScreen` lists the full catalog and does not filter
by `AppLanguage` or `CaseSummary.language` (`UI-004`). Case body
strings are payload, not Compose resources.

Mafia has no case catalog.

## What the loc contract does not prove

- Android / iOS native strings
- Unused keys (currently none)
- Identical translations beyond format tokens
- Case JSON language vs UI language
- Visual RTL, line wrapping, or large-text overflow
- Plurals (none exist; counts use format strings)
