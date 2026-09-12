# DS-C01 — iOS Follow System can retain the app's explicit language after restart

> **Final independent disposition (2026-09-05): CONFIRMED DEFECT — Low.** Validator `/root`; see `validations/DS-C01-root.md`. The original candidate text below is preserved as the pre-validation record; its pending language is superseded by this disposition.

- Finder `/root/mafia_cont`; proposed independent validator `/root`.
- **UNCONFIRMED — pending separate-agent validation.** Proposed severity Low (persisted language/RTL preference correctness; no data or privacy loss proved).
- Source main `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`, unchanged tracked application source. iOS only; both game modes/all app chrome use this boundary.

## Root cause and reachable execution path

`shared/design-system/src/iosMain/kotlin/com/parlor/designsystem/localization/LocalAppLocale.ios.kt:33–55` reads `arrayForKey(AppleLanguages)` from the full defaults search list and writes `setObject([languageTag], AppleLanguages)` to persistent app-domain preferences. The only rollback is effect disposal. Captured `previousLanguages` does not distinguish global/system fallback from an app override written by an earlier process. System/null branch33–41 does not clear a stale app-owned override.

Reachable sequence: device preferred language Arabic; Parlor Settings explicitly English; UserDefaults eventually persists the AppleLanguages override; process terminates without effect disposal; reopen Parlor. `PlatformStorage.ios.kt:13–18` constructs persistent production settings, `IosSettingsKeyValueBacking.kt:15–38` restores stored `language_override=en`; `App.kt:62–85` feeds it into `ProvideAppLanguage.kt:49–60`. The iOS effect now captures its own preceding-process `AppleLanguages=[en]` as previousLanguages. User chooses Follow System in `SettingsScreen.kt:111–116`; `SettingsMutationDispatcher.kt:20–27` commits the requested setting through `PersistentSettingsStore.kt:56–61`. Old effect sees installed en, restores previous en; new null effect leaves AppleLanguages alone and reads preferredLanguages still en. UI shows Follow System selected but strings/direction remain English rather than the device's Arabic. Initial null composition before stored flow emission does not clear the override either.

Expected basis: Settings has an explicit Follow System option and stores null for it; ProvideAppLanguage's API defines null as platform-following. This is not an invented rule. Actual outcome follows deterministic preference search/ownership semantics. Requires completed asynchronous preference persistence before process death; immediate crash before persistence need not reproduce.

Secondary manifestation of same root cause, not independently counted: in a clean first process with only global Arabic, disposing an explicit English effect writes the captured resolved Arabic list into the app domain rather than removing the application override. This may pin a future system-language change even though Follow System is selected. Need native lifecycle verification before claiming exact OS-settings refresh behavior.

## Exact-version evidence and counter-evidence

Authoritative reference receipts/extracted source: `evidence/design-locale-api/research.json`. Apple Preferences Guide documents search order (argument, application persistent, global persistent, languages, registration) and that standard defaults writes go into the app domain; AppleLanguages is the global preferred-language key. CMP resources1.10.3 `ResourceEnvironment.kt` reads `Locale.current` for composables; ui-text1.10.3 `Locale.current -> platformLocaleDelegate.current`, darwin NativePlatformLocale maps `NSLocale.preferredLanguages`. Non-composable ResourceEnvironment.ios also reads preferredLanguages. Relevant source jars fetched directly from Maven Central at1.10.3, hashes retained; no version-substituted cache source.

Official JetBrains resource-environment documentation recommends AppleLanguages as a temporary workaround but its null branch explicitly removes the key, unlike this implementation. Thus use of AppleLanguages itself is not the finding; ownership/recovery of its persisted override is. Avoid blindly replacing code with the docs' keyed composition, because that would recreate navigation/session state. Normal in-process explicit→System can work when the initially captured fallback matches the actual system. Android uses configuration/JVM locale state, Desktop uses process Locale; no equivalent persistent AppleLanguages write there. Native external per-app language overrides require deliberate preservation policy, not unconditional deletion of owner settings without investigation.

## Verification / limitations

No device, simulator, app execution or personal defaults inspected. Isolated Foundation witness `reproducers/DSC01PreferenceOwnership.swift.txt` is prepared for root's single execution lane: use a new UUID-named defaults suite and unique synthetic key, not standard defaults or AppleLanguages. Two phases illustrate real persistent-domain capture/restart and resolved-fallback promotion using exactly the preference operations; it is not execution of Compose/iOS app code. Root must independently reopen the reachable source and evaluate platform docs. Do not classify as confirmed until that review.

## Recommended remediation/test design (do not implement)

Define explicit ownership of the temporary platform-language override; preserve actual app-domain absence versus resolved fallback, and clear/reconcile app-owned stale overrides when stored choice is System. Avoid reading the already-overridden process preference as a pristine system baseline. Cover first launch, clean in-process English/Arabic/System switching, persisted explicit override plus process death/relaunch then System, interrupted settings persistence, external per-app language choice, and future OS language change. Retain navigation/session composition and test both localized strings and native/Compose layout directions.

## Source hashes

- `/Users/abdelrahman/Projects/parlor/shared/design-system/src/iosMain/kotlin/com/parlor/designsystem/localization/LocalAppLocale.ios.kt`: `eb10fc361fc420635023b2e81ceda37965bb5a485d8bffa32f9df9178cda6977`
- `/Users/abdelrahman/Projects/parlor/shared/design-system/src/commonMain/kotlin/com/parlor/designsystem/localization/ProvideAppLanguage.kt`: `b4f4286459fe923ebaa3e156f4a9995232ce40ad32ae9bc43f73a929da77dc18`
- `/Users/abdelrahman/Projects/parlor/composeApp/src/commonMain/kotlin/com/parlor/app/App.kt`: `622b679a2ef023fab17bf6c8ed8eb38c1fbc2b76a391f5f9a8298d59da9dda59`
- `/Users/abdelrahman/Projects/parlor/composeApp/src/commonMain/kotlin/com/parlor/app/shell/settings/SettingsScreen.kt`: `f97da085d85ed5fe01ac9f4725580ea2f86e399dcd330f532d9babc1925535c5`
- `/Users/abdelrahman/Projects/parlor/composeApp/src/commonMain/kotlin/com/parlor/app/shell/settings/SettingsMutationDispatcher.kt`: `915149f63b117f3516c749070abea6c538942c2d61c2b9c7224f3551aad45ce5`
- `/Users/abdelrahman/Projects/parlor/shared/storage/src/commonMain/kotlin/com/parlor/storage/settings/PersistentSettingsStore.kt`: `179e00506a2f3c7083d9f9231aa683e8bb73220a807ef6f1f5b04bb5496dac10`
- `/Users/abdelrahman/Projects/parlor/composeApp/src/iosMain/kotlin/com/parlor/app/storage/IosSettingsKeyValueBacking.kt`: `c8162e6713905b66d10aa8bc7e284d18f6815a2ae1754ba836c7241382014a10`
- `/Users/abdelrahman/Projects/parlor/composeApp/src/iosMain/kotlin/com/parlor/app/storage/PlatformStorage.ios.kt`: `cb69b6de029bf7d9f84dbc5bf19139150906a29d64cf29a60994259ca657a0c0`
