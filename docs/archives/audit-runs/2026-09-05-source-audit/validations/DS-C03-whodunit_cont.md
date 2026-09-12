# DS-C03 — Independent validation: light-theme recovery labels fail contrast

**Classification: CONFIRMED DEFECT. Severity: Low (UI accessibility/legibility).** Finder `/root/mafia_cont`; independent validator `/root/whodunit_cont`, 2026-09-05. Baseline main `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`; no tracked changes.

## Reachable path and exact proof

All paths below are relative to `/Users/abdelrahman/Projects/parlor/`; exact hashes are in `validations/DS-C03-source-hashes-whodunit_cont.json`.

1. `composeApp/src/commonMain/kotlin/com/parlor/app/shell/settings/SettingsScreen.kt:145–150` allows selecting Light. `App.kt:61–88` reads that preference and supplies `ParlorTheme(themeMode=themeMode)`. `shared/design-system/src/commonMain/kotlin/com/parlor/designsystem/theme/ParlorTheme.kt:72–93,125–143` installs `LightCozyNoirPalette` for Light and for System while the OS is light.
2. `App.kt:217–305` -> `AppNavigationHost.kt:41–53,105–112` -> each game's binding Content (Whodunit72–88; Mafia64–80). These do not force a dark palette. Their `ParlorAccentScope` uses `ParlorAccent.kt:25–54`: Amber returns the original palette; Crimson copies only accent/border tokens, preserving textSecondary and coverScreen.
3. For a concrete reachable recovery route, `WhodunitHostSessionFlow.kt:297–356` freezes the real admitted roster and mounts `WhodunitMultiplayerHostFlow`. `WhodunitGameFlow.kt:1011–1030` renders ReconnectingOverlay during startup/runtime lookup. Later1113–1134 renders HostDisconnectedOverlay when a required seat is away. The corresponding Mafia path is `MafiaHostLobbyFlow.kt:279–302` -> `MafiaMultiDeviceHostFlow.kt:99–134,191–245`. No palette override occurs along either path.
4. `shared/design-system/.../components/ReconnectingOverlay.kt:57–65,107–113` and `HostDisconnectedOverlay.kt:44–52,82–88` paint an opaque coverScreen background and call an enabled Ghost ParlorButton. The calls do not pass `enabled=false` or `loading=true`.
5. `ParlorButton.kt:60–80,85–105,139–142,170–174` uses transparent background/border and textSecondary for that unpressed Ghost label; explicit Text color means Material `LocalContentColor` cannot substitute the cover-specific token. `tokens/ParlorColors.kt:134,148–152` selects opaque `#55524D` on opaque `#000000` in Light.
6. Independent WCAG sRGB arithmetic gives luminance0.08501703393101451 and contrast **2.70034067862029:1**. This is below both normal-text4.5:1 and large-text3:1 thresholds. The label is an active escape/recovery action, not decorative, inactive or exempt content.

These are shipping `commonMain` components (`shared/design-system/build.gradle.kts:5–14`, `composeApp/build.gradle.kts:121–137`). The expected readability is supported by active-label semantics and the repository's own AA normal-text contracts (`PaletteCoverageTest.kt:150–177,244–246`), rather than an invented visual preference.

## Counter-evidence and sibling paths

- Dark palette textSecondary is `#B7B5B0`, giving10.249923175314311:1 on black. Do not claim both themes fail.
- Overlay headings/body already use cover-specific light text. That does not change the button's independently resolved explicit color.
- A transparent button does not acquire the app's cream background: these overlays paint opaque black behind it. Pressed tint is absent in the stable default state.
- ContinueWithoutDialog also has a black outer surface, but its Ghost button sits inside `surfaceElevated` (61 and80–85), so it is **not** another instance of this root cause. SessionExitConfirmation uses Secondary/Primary/Destructive filled buttons185–205, not the failing Ghost pair.
- Labels remain present/clickable and have semantic descriptions. No completely invisible action, screen-reader failure, data loss or reducer/privacy consequence is asserted.
- PaletteCoverageTest checks standard light/dark surfaces129–148 and selected active pairs151–198; these lists omit Ghost-on-cover. ActiveTextContrastCallSiteTest1–69 also does not cover it. Existing passing tests would not refute this pair; this reviewer did not run them.

## Reproduction and limits

Source-level reproduction: select Light; create a normal Whodunit or Mafia LAN room; start and observe initial recovery, or disconnect a required peer while host remains in foreground. The recovery Leave label resolves to the colors above. A deterministic component-only UI test can render either overlay under the real Light theme without a network. **No such rendering/app/device test was executed by this validator.** Only exact source reachability and independent color arithmetic were verified. Physical screen contrast/assistive technology and real network behavior remain separate gates.

## Recommendation

Use a cover-surface-aware button style/token or scoped override for recovery surfaces, preserving ordinary Ghost colors on normal light surfaces. Add actual Ghost foreground/background pairs for light/dark and both accents, pressed state where applicable, and a component pixel/semantics test. Keep the Leave callback and privacy cover unchanged.

## Research/evidence/hygiene

Official W3C reference: <https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html>, fetched2026-09-05 HTTP200. Relevant SC1.4.3 exceptions and exact relative-luminance/contrast definitions were read; this is not a claim to have reviewed the entire external document. URL, access time, body digest and compact relevant excerpts plus arithmetic are retained in `research/DS-C03-w3c-whodunit_cont.json`.

No source/configuration edits, builds, Gradle/Xcode tests, app/server launch, private data, or Store operations. The bounded public-reference fetch finished exit0; only compact required audit evidence remains. No generated build outputs or daemons were created by this validator. Root owns all build/cleanup execution.
