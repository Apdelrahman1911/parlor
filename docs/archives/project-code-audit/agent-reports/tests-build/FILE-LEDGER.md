# Tests / build / release — FILE LEDGER

Status: R = read in full or near-full. S = sampled (header / targeted range).
U = unread (see GAPS.md).

## Gradle / conventions

| File | Status | Notes |
|---|---|---|
| settings.gradle.kts | R | 13 modules; JetBrains compose/dev repo; no mavenLocal |
| build.gradle.kts | R | all aggregates; productionDesktopCheck = desktopTest only |
| gradle.properties | R | lint 9.1.1, R8 9.1.41, config-cache on |
| gradle/libs.versions.toml | R | AGP 8.13.2, p2pkit 0.7.0-rc3, material3 beta |
| gradle/wrapper/gradle-wrapper.properties | R | 8.13 + sha256 |
| gradle/verification-metadata.xml | S | header + policy only (11075 lines). verify-metadata true, signatures false |
| build-logic/settings.gradle.kts | R | |
| build-logic/convention/build.gradle.kts | R | 3 plugins |
| build-logic/.../KmpLibraryConventionPlugin.kt | R | Android+desktop+3 iOS; JUnit Platform |
| build-logic/.../KmpComposeLibraryConventionPlugin.kt | R | |
| build-logic/.../DetektConventionPlugin.kt | R | src/**/*.kt, no baseline |
| composeApp/build.gradle.kts | R | dirty vs HEAD (MOBILE_RELEASE signing aliases) |
| composeApp/proguard-rules.pro | R | attributes only; no keep rules |
| shared/*/build.gradle.kts | R | all 10 shared modules |
| game-modes/{whodunit,mafia}/build.gradle.kts | R | |

## CI / policy / version

| File | Status | Notes |
|---|---|---|
| .github/workflows/production-verification.yml | R | live on push/PR |
| .github/workflows/testing-candidate.yml | R | 1220 lines; publish path live |
| .github/workflows/testing-external-promotion.yml | R | no rebuild |
| .github/workflows/production-promotion.yml | R | no rebuild |
| .github/dependabot.yml | R | actions only |
| config/release-policy.json | R | identity blocked |
| config/parlor-version.xcconfig | R | 1.0.0 / 1 |
| config/android-lint-accepted-warnings.txt | R | 29 advisory lines |
| config/detekt/detekt.yml | R | maxIssues 0 |
| config/candidate-manifest.schema.json | S | identity const com.parlor.app |

## Release scripts

| File | Status | Notes |
|---|---|---|
| scripts/release/validate_release_system.sh | R | |
| scripts/release/workflow_contract.py | R | |
| scripts/release/release_tool.py | S | identity + CLI; 1430 lines |
| scripts/release/store_api.py | S | execute-mode identity gate ~1760 |
| scripts/release/build_ios_candidate.sh | R | |
| scripts/release/upload_ios_candidate.sh | S | identity assert at top |
| scripts/release/validate_android_artifact.sh | S | first 40 + identity lines |
| scripts/release/validate_ios_artifact.sh | S | identity line |
| scripts/release/tests/test_*.py | S | identity + new MOBILE_RELEASE tests; 116 ran OK |

## iOS host

| File | Status | Notes |
|---|---|---|
| iosApp/Configuration/Config.xcconfig | R | dirty |
| iosApp/iosApp.xcodeproj/project.pbxproj | S | identities + Kotlin phase + Release signing (dirty) |
| iosApp/.../iosApp.xcscheme | R | no Testables |
| iosApp/iosApp/Info.plist | R | versions via xcconfig macros |
| iosApp/iosApp/PrivacyInfo.xcprivacy | R | linted in CI |

## Android packaging

| File | Status | Notes |
|---|---|---|
| composeApp/src/androidMain/AndroidManifest.xml | R | |
| composeApp/src/androidMain/res/xml/{backup,data_extraction}_rules.xml | U | referenced, not opened |

## Tests inspected (not every body)

| File | Status | Notes |
|---|---|---|
| transport-p2p/.../ProductionVerificationWorkflowContractTest.kt | R | parses CI + Gradle + docs |
| transport-p2p/.../MultiplayerDocumentationContractTest.kt | S | parses many docs/*.md |
| transport-p2p/.../P2pKitMavenProvenanceContractTest.kt | S | |
| transport-p2p/.../AndroidReleaseLintContractTest.kt | S | asserts 29 lint inventory rows + docs/ANDROID_LINT_TRIAGE.md |
| transport-p2p/.../P2pKitRoomTransportLoopbackTest.kt | S | 3 @Ignore |
| transport-p2p/.../TestTransportIsolationContractTest.kt | S | |
| engine/.../PurityTest.kt | S | Konsist |
| engine/.../NoWhodunitInEngineTest.kt | S | Konsist |
| composeApp/.../IosStorageSafetyTest.kt | S | only iosTest file |
| All other *Test.kt | listed | counts in TESTS.md |

## Adjacent, not treated as gates

| File | Status | Notes |
|---|---|---|
| release/mobile-release.json | S | untracked; restates blocked identity |
| release/private/ | U | not opened |
