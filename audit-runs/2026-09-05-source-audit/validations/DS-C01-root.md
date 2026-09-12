# DS-C01 — independent root validation

**CONFIRMED DEFECT — Low.** Finder `/root/mafia_cont`; validator `/root`.
Reviewed unchanged `main` commit `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree
`db7f3d2afe73a13628296daee2cce71165eebc8d`. Absolute base:
`/Users/abdelrahman/Projects/parlor/`. Exact hashes are in the candidate and
`validations/pending-root-source-hashes.json`.

## Independent trace and expectation

Reopened `shared/design-system/src/iosMain/kotlin/com/parlor/designsystem/localization/LocalAppLocale.ios.kt:1–91`,
`.../commonMain/kotlin/com/parlor/designsystem/localization/ProvideAppLanguage.kt:1–62`,
`composeApp/src/iosMain/kotlin/com/parlor/app/storage/{IosSettingsKeyValueBacking.kt:1–40,PlatformStorage.ios.kt:1–19}`,
and `App.kt:45–100`, `shell/settings/SettingsScreen.kt:74–155`. The persisted language flows into
the actual root composition. Selecting Follow System writes null, not a deliberate English override.
PersistentSettingsStore and SettingsMutationDispatcher were also reopened in the preceding root pass.

The iOS effect reads a resolved defaults value at line35, writes a persistent app-domain override at37,
and restores the captured resolved value at52. It neither distinguishes domain ownership nor reconciles
an override left by the preceding process. After an explicit English choice is persisted and the process
ends without disposal, a new effect captures that same English override. Choosing System disposes the
effect by writing English again; the new null effect does not clear it. The setting says System while
the locale remains the old explicit choice. This applies to iOS chrome for both games, not reducers or
seeds. Follow System's documented API and actual settings label establish the expectation.

## Evidence and counter-evidence

The root executed two distinct native Swift/Foundation processes using a fresh UUID defaults suite
and a synthetic preference key, never standard defaults, AppleLanguages, or real app/player data.
`evidence/native-preference-01/{seed.log,restore.log,receipt.json}` shows both exit0, persisted English
recaptured on restart, and System restoring English over an Arabic fallback. It also demonstrates that
the resolved fallback can be promoted into the persistent app domain during a clean in-process cycle.
This is a native **preference-ownership witness**, not an iOS app/device reproduction.

Reopened the authoritative Apple preference-domain guidance and pinned CMP1.10.3 locale/resource
paths recorded in `evidence/design-locale-api/research.json`. Official JetBrains guidance itself uses
AppleLanguages, so that API choice alone is not a defect; its null branch removes the temporary key.
Normal same-process switching can work when the original capture is correct. Immediate termination
before asynchronous persistence need not reproduce. External OS per-app overrides require an explicit
ownership policy; unconditional deletion of arbitrary preferences is not the recommended fix.

## Recommendation and limits

Track actual app-owned override state separately from the resolved platform fallback; reconcile stale
owned overrides on relaunch/System, without recreating navigation/session composition. Test English,
Arabic, System, persisted restart, interrupted writes and external per-app preference changes. Verify
both resources and Compose/UIKit direction on real iOS after authorization. No crash/privacy impact
is asserted. Native suite was absent after cleanup, temporary compiler cache removed, Gradle stop0;
the redundant defaults-delete exit1 indicates the suite had already been removed, not a leaked suite.
