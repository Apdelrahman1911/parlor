# DS-C03 — light-theme recovery Leave labels use dark text on black

Finder `/root/mafia_cont`; independent validator `/root/whodunit_cont` requested. **CONFIRMED DEFECT — independent `/root/whodunit_cont` validation complete.** Severity Low: accessible/legible recovery action; not data loss or input disablement. main `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, no production edits.

`ReconnectingOverlay.kt:57–65,107–112` draws opaque coverScreen and a Ghost Leave button. `HostDisconnectedOverlay.kt:44–52,82–87` does the same. `ParlorButton.kt:78–80,139–142,170–174` supplies Ghost foreground colors.textSecondary on transparent background. Light palette `ParlorColors.kt:134,150` is textSecondary#55524D and coverScreen#000000. Exact sRGB linear-light contrast is2.70034067862029:1, below both normal-text4.5 and large-text3 requirements. Underlying theme `ParlorTheme.kt:87–93` honors explicit Light/System light; `ParlorAccent.kt:44–53` changes accents only, not these tokens.

Reachable examples: MafiaMultiDevicePeerFlow93–108 startup recovery,174–202 authoritative snapshot wait/disconnected seat/command pending; Mafia host disconnected overlay; both Whodunit host/peer recovery routers use same components. This is an enabled escape action, not exempt disabled/incidental text. The overlay's title deliberately uses coverScreenTextPrimary; Ghost button cannot receive a cover-specific text override, so normal surface-oriented color is used on black.

Expected: recovery Leave remains legible under supported Light theme and accessible contrast policy; use correct surface-aware/cover text tokens, not globally changing Ghost buttons on normal light surfaces. Existing PaletteCoverageTest129–148 checks standard surfaces excluding cover; active pairs151–178 omit Ghost-on-cover. Contract string tests do not prove nested composited contrast.

Evidence from source and deterministic color arithmetic only; no physical/simulator screenshot or screen-reader claim. Dark mode textSecondary equals cover secondary and is counter-evidence to a both-themes assertion. Clicks and semantic label still work; do not claim the action vanishes completely. Review sibling black cover screens for other surface-oriented components and add light/dark/accent normal/pressed/disabled contrast + UI pixel checks without changing game rules.

## Source hashes

- `/Users/abdelrahman/Projects/parlor/shared/design-system/src/commonMain/kotlin/com/parlor/designsystem/components/ReconnectingOverlay.kt`: `b4865b98b90a9feff58123cdd7423526a9910c6dc772303fffe7fdf30c30e2b1`
- `/Users/abdelrahman/Projects/parlor/shared/design-system/src/commonMain/kotlin/com/parlor/designsystem/components/HostDisconnectedOverlay.kt`: `17f851f027ca029dba7277c4cd353e9c98bd800ba997c3e1906746420be7f624`
- `/Users/abdelrahman/Projects/parlor/shared/design-system/src/commonMain/kotlin/com/parlor/designsystem/components/ParlorButton.kt`: `d3d5d4038e8fa499ed2c6e95a851cbd5f8da1a37fb35fed97b716c7a34007c38`
- `/Users/abdelrahman/Projects/parlor/shared/design-system/src/commonMain/kotlin/com/parlor/designsystem/tokens/ParlorColors.kt`: `99c60bb3b07cd6295c51adf7d9cf684bf5890d401395d323e1d7a4f11c6bb5e8`
- `/Users/abdelrahman/Projects/parlor/shared/design-system/src/commonMain/kotlin/com/parlor/designsystem/theme/ParlorAccent.kt`: `dc8145f4594dc0b10feb636de11b3e871f48d00081ff3c627f6da2b21f8b7428`
- `/Users/abdelrahman/Projects/parlor/shared/design-system/src/commonMain/kotlin/com/parlor/designsystem/theme/ParlorTheme.kt`: `b751abc30f088d8e8a8b30bcaa43b7927a97ba06b7040f472c20f5e5c9d8ff00`

## Independent validation update

`validations/DS-C03-whodunit_cont.md` and hashes independently reopened source/theme/bindings and both games host paths. Same exact2.70034067862029:1 ratio computed independently, official W3C reference read/retained `research/DS-C03-w3c-whodunit_cont.json`. ContinueWithoutDialog and filled SessionExit buttons expressly excluded. No rendering, runtime/device or screen-reader assertion. Finder reopened full validation report.
