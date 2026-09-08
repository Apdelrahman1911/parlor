# INV-C02 — Generated review inventory misclassifies native test files

Finder: `/root/mafia_cont`. Independent validation: `/root/whodunit_cont`, complete. Classification: **DOCUMENTATION MISMATCH** / generated-evidence accuracy. Suggested severity: Low. No shipping-code inclusion or privacy defect is alleged.

Baseline: `main` / `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`. Source SHA256s in `evidence/review-inventory-inspection/source-hashes.json`.

## Exact locations

- `/Users/abdelrahman/Projects/parlor/scripts/generate_review_inventory.py:104–118,121–142,181–205`.
- `/Users/abdelrahman/Projects/parlor/docs/review/INDEPENDENT_REVIEW_INVENTORY.csv:25,390`.
- `/Users/abdelrahman/Projects/parlor/iosApp/iosApp.xcodeproj/project.pbxproj:132–168,250–267`.
- `/Users/abdelrahman/Projects/parlor/iosApp/iosApp.xcodeproj/xcshareddata/xcschemes/iosApp.xcscheme:9–35,39–56`.
- `/Users/abdelrahman/Projects/parlor/composeApp/build.gradle.kts:277–282`.

## Reachable deterministic proof

1. The generator treats every `iosApp/**/*.swift` file as `ios-app` and `production-source`, then labels it `SHIPPING IOS: compiled into the Swift application wrapper` without inspecting target membership.
2. Existing CSV390 applies this to `iosApp/iosAppUITests/IOSAppLaunchUITests.swift`.
3. Actual PBX target `iosAppUITests` has product type `com.apple.product-type.bundle.ui-testing`; its separate source phase contains the XCTest file. The app source phase contains only ContentView.swift and iOSApp.swift. The scheme includes the test target for Testing but explicitly excludes it from Running, Profiling, Archiving and Analyzing. XCTest source1–32 imports XCTest and contains one launch test. Thus this file is test code, not compiled into the shipping Swift app wrapper.
4. Sibling CSV25 classifies `composeApp/src/androidInstrumentedTest/java/com/parlor/app/ReleaseRuntimeSmokeTest.java` as `test-resource`, because the recognized source suffix set is only `.kt`/`.swift`. Actual Gradle277–282 maps that directory to the Android test Java compiler; actual file1–62 is an executable InstrumentationTestCase, not a resource. Its nonshipping reachability is otherwise correctly described.

The existing production gate re-renders these same wrong labels and compares exact text; it does not detect this classification mismatch. No test or build execution was performed by this reviewer. No generated inventory was updated.

## Counter-evidence and scope

The application build itself correctly isolates the test targets. The CSV is a reviewer/release-tool artifact, not source-set configuration, so these labels do not accidentally package XCTest into Parlor or remove the Java test from execution. Fixture modules have separate explicit nonshipping reachability handling. Both affected files are still listed, not missing. This is a narrow inventory/documentation accuracy issue, not proof of broad source coverage failure.

## Recommendation

Recognize explicit Xcode test paths/target membership before broad iOS Swift classification; include Java in executable test-source classification. Add assertions for these exact production/test files plus ordinary iOS Swift entry points and test resources. If generic metadata remains path-derived, document that limitation rather than presenting inferred labels as artifact inspection.

Origin: previously present inventory classifier behavior; no evidence that this is a newly introduced application regression.

## Independent adjudication

Independent validator `/root/whodunit_cont` reopened the generator, tests, actual callers/guards and native build-membership source. Its full report was read by the finder after delivery: `validations/INV-C02-whodunit_cont.md`; exact reopened source identities/ranges are in `validations/INV-C01-C02-source-hashes-whodunit_cont.json`. It confirms the narrow evidence/documentation classification, not an application defect, accidental shipping test inclusion, privacy issue or Store authorization bypass. Deterministic source proof only; no repository mutation/test execution is claimed.
