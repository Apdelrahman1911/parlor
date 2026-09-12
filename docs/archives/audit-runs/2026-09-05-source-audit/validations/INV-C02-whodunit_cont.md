# INV-C02 — independent validation of native test inventory labels

**Classification: DOCUMENTATION MISMATCH / generated-evidence accuracy, Low. Not a shipping-code inclusion defect.** Finder `/root/mafia_cont`; independent validator `/root/whodunit_cont`.

Baseline: `main`, commit `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`. Absolute root `/Users/abdelrahman/Projects/parlor/`; exact independently computed file SHA256s, absolute paths and read ranges are in `validations/INV-C01-C02-source-hashes-whodunit_cont.json`.

## Independent source/consumer tracing

I reopened complete `scripts/generate_review_inventory.py:1–408`, all generator tests1–125, full `iosApp/iosApp.xcodeproj/project.pbxproj:1–529`, full shared scheme1–103, full Swift test1–32 and Java instrumentation test1–62. Actual CSV rows25 and390 were read, with neighboring ordinary app/test entries as counter-examples. Root/release gate wiring was traced independently as documented in INV-C01.

### Swift test incorrectly marked shipping

Generator104–118 assigns every `iosApp/**/*.swift` path `ios-app`;137–138 classifies it as `production-source` before any test check;184–186 then emits `SHIPPING IOS: compiled into the Swift application wrapper`. CSV390 contains exactly that false label for `iosApp/iosAppUITests/IOSAppLaunchUITests.swift`.

Actual PBX source membership is unambiguous. App target133–149 uses source phase251–259, containing only ContentView.swift and iOSApp.swift. The test file build reference15/39 belongs only to source phase260–267, owned by target151–168 whose product type is `com.apple.product-type.bundle.ui-testing`. Its dependency271–275 points **from test to app**, not app to test. No app copy/resources/framework phase packages the test bundle. Scheme9–22 enables the UITest target only for Testing and explicitly disables Running/Profiling/Archiving/Analyzing;39–56 registers it as a testable. The test itself imports XCTest and owns the app-launch assertion, not the application's entry point. Thus the existing inventory claim contradicts executable build membership without requiring an Xcode build to prove it.

### Java test incorrectly marked resource

`composeApp/src/androidInstrumentedTest/java/com/parlor/app/ReleaseRuntimeSmokeTest.java` is CSV25's `test-resource`. Generator139–140 recognizes executable test suffixes only `.kt` and `.swift`. Source13–62 is a real `InstrumentationTestCase` with two runnable test methods. `composeApp/build.gradle.kts:277–282` explicitly feeds its directory to AGP `androidTest.java.srcDir`; it is compiled into the separate test APK, not loaded as a resource. Its nonshipping reachability label is otherwise correct.

## Counter-evidence and impact

Both files remain inventoried. Build configuration correctly isolates the XCTest target and Java test APK. The generator does not configure PBX targets or Gradle source sets; wrong CSV labels cannot themselves package tests into the app or suppress test execution. Ordinary app Swift rows383/389 and Kotlin test row26 are correctly labeled, while fixture modules have a separate nonshipping override181–183. This is a narrow path-heuristic classification defect in evidence metadata, not a new app/privacy/Store bypass. Existing tests exercise determinism, override IDs and synthetic merge stability; none asserts these native target/suffix classifications.

## Recommendation and test coverage

Classify explicit Xcode test targets/paths before broad `iosApp/*.swift`; include `.java` among executable test source suffixes. Prefer inspected target membership where feasible and document any remaining heuristic reachability. Add table-driven cases for both affected files, ordinary Swift app entries, Kotlin tests, Java tests and genuine resources. Re-render evidence only after a separately authorized tooling change; do not change correct application source-set membership.

## Evidence/hygiene

Deterministic source-level proof, not a claimed executed Xcode/Gradle/device reproduction. No builds/tests/generator execution, signed-artifact inspection, application/configuration changes or Store actions were performed. Only task-specific audit evidence was written; no task-owned build outputs/processes require cleanup. Root owns the shared build lane.
