# iOS localized display-name follow-up

- Lead: `reviews/root-platform-shell-notes.md:20` (`/root`). Independent reviewer: `/root/session_cont`.
- Source: `/Users/abdelrahman/Projects/parlor`, `main`, commit `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`, tracked state unchanged.
- **Disposition: FALSE POSITIVE for the hypothesized defect “localization defeats an already-configured distinct Debug display name.”** No such distinct name is configured. Current localized naming follows native platform rules. No new confirmed defect or severity is proposed.

## Complete reachable configuration

1. `iosApp/Configuration/Config.xcconfig:8–12` includes shared version settings and defines `APP_NAME = Parlor` once, without a Debug override.
2. `iosApp/iosApp.xcodeproj/project.pbxproj:398–428,429–459` binds the same config file for both app configurations. Debug sets `PRODUCT_BUNDLE_IDENTIFIER = $(BUNDLE_ID).debug` and `PRODUCT_NAME = $(APP_NAME)`; Release uses `$(BUNDLE_ID)` and the same product name. Debug/Store isolation is by bundle identifier. Both configurations select the same app icon asset and Info.plist. The shared scheme uses Debug for Run/Test and Release for Archive (`iosApp.xcscheme:39–78,99–102`).
3. `iosApp/iosApp/Info.plist:9–16` binds the bundle identifier, fallback display name and product name independently. Lines 23–27 declare English and Arabic.
4. Both two-line `iosApp/iosApp/{en,ar}.lproj/InfoPlist.strings` files define a literal localized display name (`Parlor` / `بارلور`) and localized LAN prompt. `project.pbxproj:14,37–38,120–129,132–149,208–218` connects both files through the variant group and the app's unconditional Resources build phase. They are not orphaned/test-only files.
5. The original root-owned actual Debug Xcode log independently corroborates these settings: `evidence/xcode-ui-01/xcodebuild.log:239,442,620,623` exports APP_NAME=Parlor, FULL_PRODUCT_NAME=Parlor.app, PRODUCT_BUNDLE_IDENTIFIER=com.parlor.app.debug and PRODUCT_NAME=Parlor. Lines 1118–1126 copy/validate both InfoPlist.strings into the Debug app. This proves build consumption, not a physical SpringBoard label/co-install test.

The ordinary Debug app therefore uses the same localized brand labels as Release, not a configured “Parlor Debug” label that is accidentally discarded. A distinct development label/icon may help distinguish two installs, but the current source and mandatory repository contract require the `.debug` identity suffix, not a particular additional launcher-name marker. A skill's default branding recommendation is not evidence of an application requirement. Do not invent an identity migration or rename the app for this lead.

## Native precedence verified, not assumed

Apple's current `Bundle.object(forInfoDictionaryKey:)` documentation says the localized value is returned when available. The archived Information Property List Key Reference (section “Localizing Property List Values”) explicitly says InfoPlist.strings takes precedence for a user's matching locale; Info.plist is fallback. Current CFBundleDisplayName documentation identifies this as the user-visible name used by Siri and the iOS Home screen.

All three official pages were fetched on 2026-09-05; URLs, hashes, access timestamps and limitations are in `research/ios-name-session_cont/research-ledger.json`. The archived rule is independently corroborated by the current Bundle API page. This is source/API proof of metadata resolution; no new simulator/device/signing operation was performed.

## Counter-evidence and limited future consequence

- It is true that *a future/custom* change of APP_NAME alone would not replace the literal localized display names. Such a change must also maintain appropriate localized strings if the desired visible label changes. That is normal platform behavior, not a failure of today's default configuration.
- `docs/IOS_SETUP.md:117` calls APP_NAME a configuration knob, but does not establish a currently distinct Debug native display-name requirement. APP_NAME still controls product/executable/fallback metadata, so this wording alone does not prove a separate documentation defect.
- `composeApp/build.gradle.kts:310–331` is the Android identifier guard; it does not validate iOS display-name separation. `scripts/release/tests/test_workflow_contract.py:75–123` locates both app configurations via their PRODUCT_NAME binding to verify signing scope; it is not a localized-name assertion. Tests are not substituted for the above source trace.
- The current provisional Store identifier collision remains an independent release blocker; native localization neither creates nor fixes it. No Store or signing material was inspected.

If an owner later explicitly requests a visually separate development brand, update both locales/configuration packaging and verify the resolved localized values alongside the bundle IDs; preserve Arabic localization and canonical Store identity. That optional future work is outside this audit-only assignment.

## Evidence/cleanup scope

Exact hashes and reviewed ranges: `reviews/ios-localized-display-name-session_cont.sources.json`. Only first-party source, retained task-owned logs and public official references were read. Public-document fetch process exited 0; generated text evidence is small and intentionally retained. No Gradle/Xcode build, simulator, app, signing operation or test was started by this reviewer, so no build cleanup was due on this lane. Existing root-owned Xcode-cycle receipt already records cleanup. Tracked source and all pre-existing user files remain unchanged.
