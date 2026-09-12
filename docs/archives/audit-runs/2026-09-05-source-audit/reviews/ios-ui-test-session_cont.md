# iOS Swift-wrapper UI test — source and command review

Reviewer `/root/session_cont`, checkout `main` / `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`. Audit-only source/official-reference inspection. This reviewer ran **no Gradle, Xcode, simulator, signing, or device commands**. Exact source hashes/ranges are in the adjacent `.sources.json`.

## Actual registered test and scope

`iosApp.xcscheme:39–57` registers `iosAppUITests` once, enabled and nonparallel, under Debug. Its build references include both app and tests; `project.pbxproj:151–167,270–275` binds the UI-test bundle to `iosApp`. There are no test-plan/repetition or test-only startup arguments in the scheme. The entire32-line Swift test contains one method:

`iosAppUITests/IOSAppLaunchUITests/testColdLaunchRendersComposeHomeWithoutUnexpectedAlert`

It supplies English OS arguments, launches `XCUIApplication`, requires foreground within30s, waits up to30s for `staticTexts["parlor-home-brand"]`, observes no Alert for2s, and checks foreground again. `HomeScreen.kt:175–194,555` attaches that tag to the brand Text. `ContentView.swift:54–60` calls the exported Kotlin controller; `MainViewController.kt:18–33` initializes Koin then renders `App`. The privacy scenePhase overlay remains real production code; no startup bypass was found in these paths.

This is **launch smoke**, not an assertion that recovery is available, UI layout is correct, navigation works, permissions succeed, or games/LAN are playable. Inline failure cards are not `app.alerts`.

## Build identity and safe invocation

Project Debug uses `$(BUNDLE_ID).debug` (`project.pbxproj:421`), resolving from the public template to `com.parlor.app.debug`. Test target is `$(BUNDLE_ID).uitests`. Version/build values come from the checked-in xcconfig include. No Store identity replacement, Team selection, provisioning update, or project edit is needed for an unsigned simulator attempt.

The checked-in shell phase calls `./gradlew --no-daemon :composeApp:embedAndSignAppleFrameworkForXcode --dependency-verification=strict`; leave `OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED` unset/NO. Its known fail-fast defect is separately IOS-B1: require a fresh task-owned DerivedData and no retained stale framework outputs, and inspect the Gradle result—not only Xcode's exit code. Do not run a second competing Gradle lane.

Recommended command structure after root owns a newly created, booted simulator UUID and a fresh temporary directory:

```sh
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -configuration Debug -sdk iphonesimulator \
  -destination "platform=iOS Simulator,id=$OWNED_UUID" \
  -destination-timeout 120 -derivedDataPath "$OWNED_TMP/DerivedData" \
  -resultBundlePath "$OWNED_TMP/Results.xcresult" \
  -parallel-testing-enabled NO \
  -maximum-concurrent-test-simulator-destinations 1 \
  CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO \
  CODE_SIGN_IDENTITY= DEVELOPMENT_TEAM= ONLY_ACTIVE_ARCH=YES test
```

Root's actual completed invocation also limits Xcode jobs and test timeouts. No automatic retry flag is recommended. Full `test` covers the scheme's sole registered test; using the exact `-only-testing:<identifier>` above is optional and must still produce one executed test. Do not accept zero tests as success. Never select an arbitrary first available/user-owned simulator.

## Actual root-owned receipt reopened

During this source review, root completed `evidence/xcode-ui-01/receipt.json`, `xcresult-summary.json`, `xcresult-tests.json`, and log1508–1554. They record one test passed, zero failed/skipped, on a newly created iPhone17Pro simulator, iOS26.5(23F77), with Xcode26.5(17F42), unsigned Debug1.0.0(1). This reviewer independently viewed both supplemental screenshots. They show the recovery-unavailable banner: **IOS-R1 is a separate unresolved runtime observation**, not erased by the passing launch test. Original container and generated artifacts were removed, so absent OSStatus/per-source evidence cannot be reconstructed by assumption.

The receipt records immediate Gradle stop0, final stop0, UUID shutdown/delete0, removal of15 task-owned root/module/build-logic output directories and task-owned temporary DerivedData/result bundle, no remaining outputs, and unchanged tracked source. These are root's executed receipts, not this reviewer's own cleanup run.

## Evidence to retain / cleanup

Keep the command/status/source identity, test-summary/test-tree JSON, necessary sanitized failure diagnostics, artifact identities/hashes, and at most the useful screenshots. Installed `xcresulttool` supports `get test-results summary`, `get test-results tests`, and `export attachments --only-failures`. Inspect/export evidence before removing its result bundle. After each build cycle stop Gradle immediately; cleanup only owned generated outputs (including build-logic and DerivedData), stop again if cleanup started Gradle, terminate only task-owned apps/simulators, and verify final tracked status. Do not delete global caches or user simulators.

## Authoritative references and version limits

- Apple TN2339, https://developer.apple.com/library/archive/technotes/tn2339/_index.html (accessed2026-09-05): test/build-for-testing distinction, simulator UUID destinations and `TestTarget[/TestClass[/TestMethod]]` selection.
- Exact installed Xcode26.5 manpages copied under `research/ios-ui-test-session_cont/`: `xcodebuild.1` options165–168,236–242,402–508,619–703 and `xcresulttool.1` complete174lines. These bind the local flags; the historical note does not establish modern platform behavior by itself.
- KGP2.4.10 upstream/cache-identical `AppleXcodeTasks.kt:369–420`, `XcodeEnvironment.kt:1–97`: framework embedding/signing is driven by Xcode environment; optional signing action depends on `EXPANDED_CODE_SIGN_IDENTITY`. Fetch URLs/hashes/date are in `kgp-framework-source-manifest.json`. No signing success inferred.

Xcode26.5 is not the Store-qualified26.3/17C529 pin. Simulator smoke is not hardware, signing, Keychain entitlement, or Store-readiness evidence.
