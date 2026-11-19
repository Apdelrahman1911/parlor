package com.parlor.transport.p2p

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Guards the executable CI contract. A green workflow must not be possible
 * after silently dropping an automatable release gate from either CI or the
 * root Gradle aggregate that CI invokes.
 */
class ProductionVerificationWorkflowContractTest {

    private val repositoryRoot: File by lazy(::locateRepositoryRoot)

    @Test
    fun public_ios_host_join_taps_require_an_observed_transition_without_replay() {
        val swift = read("iosApp/iosAppUITests/IOSAppLaunchUITests.swift")
        val journey = swift.substringAfter("private func verifyAdditionalGamesSetup(")
            .substringBefore("func testLastLightLocalPlayerFields")
        listOf("host", "join").forEach { action ->
            assertContains(journey, "try tapPublicSetupTransition(app, from: copy.$action, to: copy.permission)")
        }
        val observer = swift.substringAfter("private func tapPublicSetupTransition(")
            .substringBefore("private func tapSettledControl(")
        assertEquals(1, Regex("try tapSetupButton\\(app, prefix: from\\)").findAll(observer).count())
        listOf(
            "destination.exists && !source.exists",
            "XCTWaiter.wait(for: [transitioned], timeout: 10)",
            "if result != .completed",
            "XCTAttachment(screenshot: app.screenshot())",
            "screenshot.lifetime = .keepAlways",
            "sourceExists ? source.isEnabled : false",
            "sourceExists ? source.frame : .zero",
            "XCTAssertEqual(result, .completed",
        ).forEach { assertContains(observer, it) }
        listOf(".tap()", "swipe", "for _", "while ", "app.debugDescription").forEach {
            // Comments may explain why swiping/replay is disallowed; inspect
            // executable lines, not documentation, for prohibited actions.
            val code = observer.lineSequence().map { line -> line.substringBefore("//") }.joinToString("\n")
            assertFalse(it in code, "Public transition observer must not replay or inspect arbitrary screens: $it")
        }
    }

    @Test
    fun public_ios_settlement_uses_one_coherent_snapshot_and_never_taps_after_failure() {
        val swift = read("iosApp/iosAppUITests/IOSAppLaunchUITests.swift")
        val helper = swift.substringAfter("private func tapSettledControl(")
            .substringBefore("private func verifyKeyboardCycle(")
        val code = helper.lineSequence().map { it.substringBefore("//") }.joinToString("\n")
        listOf("control.snapshot()", "snapshot.frame", "snapshot.isEnabled").forEach { attribute ->
            assertEquals(1, Regex(Regex.escape(attribute)).findAll(code).count())
        }
        assertEquals(1, Regex(Regex.escape("control.isHittable")).findAll(code).count())
        assertEquals(1, Regex(Regex.escape("control.tap()")).findAll(code).count())
        assertEquals(1, Regex("\\bprint\\s*\\(").findAll(code).count())
        assertContains(code, """print("Public control settlement: " + samples.joined(separator: "; "))""")
        listOf(
            "let sample = PublicControlSample(read: read ?? {",
            "let snapshot = try control.snapshot()",
            "return (snapshot.frame, snapshot.isEnabled)",
            "let frame = sample.frame",
            "let enabled = sample.enabled",
            "snapshotErrorCode=",
            "let stable = frames.observe(frame: frame, enabled: enabled)",
            "let hittable = stable ? control.isHittable : nil",
            "if stable && hittable != true { frames.reset() }",
            "XCTWaiter.wait(for: [settled], timeout: 10)",
            "if samples.count < 12",
            "if result != .completed",
            "guard result == .completed else",
            """throw NSError(domain: "PublicControlSettlement", code: 1)""",
            "screenshot.lifetime = .keepAlways",
        ).forEach { assertContains(code, it) }
        assertTrue(code.indexOf("guard result == .completed else") < code.indexOf("control.tap()"))
        listOf("control.exists", "control.frame", "control.isEnabled", ".label", ".value", ".placeholderValue", ".identifier",
            ".localizedDescription", ".userInfo", ".domain", ".debugDescription", "sleep(",
            "press(forDuration", "swipe").forEach { assertFalse(it in code) }
        val sampleState = swift.substringAfter("private struct PublicControlSample {")
            .substringBefore("private struct PublicControlFrames")
        assertEquals(1, Regex(Regex.escape("try read()")).findAll(sampleState).count())
        listOf("frame = resolved.frame", "enabled = resolved.enabled", "errorCode = nil",
            "catch {", "frame = nil", "enabled = false", "errorCode = (error as NSError).code",
        ).forEach { assertContains(sampleState, it) }
        val frameState = swift.substringAfter("private struct PublicControlFrames {")
            .substringBefore("final class PublicControlFramesTests")
        listOf("reset()", "return false", "previous = frame", "return previous == frame",
            "!frame.isNull", "!frame.isInfinite", "!frame.isEmpty", "frame.origin.x", "frame.origin.y",
            "frame.size.width", "frame.size.height", "frame.maxX", "frame.maxY",
            "frame.size.width > 0", "frame.size.height > 0", "allSatisfy({ $0.isFinite })",
        ).forEach { assertContains(frameState, it) }
        listOf(
            "testSampleResolvesFrameAndEnabledOnceFromTheSameGeneration",
            "testSnapshotErrorBreaksAdjacencyAndCannotReuseLastEnabledFrame",
            "testDisabledSnapshotIsNotConfusedWithResolutionFailure",
            "testFirstFrameNeverSettlesButTheExactAdjacentFrameDoes",
            "testMotionIsNotRoundedIntoASettledFrame",
            "testMissingControlBreaksFrameAdjacency",
            "testDisabledControlBreaksFrameAdjacency",
            "testInvalidOrEmptyGeometryCannotSettleAndBreaksAdjacency",
            "testHittabilityInterruptionRequiresTwoFreshFrames",
        ).forEach { assertContains(swift, "func $it()") }
    }

    @Test
    fun public_ios_button_geometry_uses_a_unique_bounded_bulk_snapshot_without_remote_reselection() {
        val swift = read("iosApp/iosAppUITests/IOSAppLaunchUITests.swift")
        val setup = swift.substringAfter("private func tapSetupButton(")
            .substringBefore("private func readPublicButtonSample(")
        assertContains(setup, "try tapSettledControl(button, read: {")
        assertContains(setup, "try self.readPublicButtonSample(app, prefix: prefix)")
        val capture = swift.substringAfter("private func readPublicButtonSample(")
            .substringBefore("private func tapPublicSetupTransition(")
        assertEquals(1, Regex(Regex.escape("app.snapshot()")).findAll(capture).count())
        listOf("PublicButtonSnapshot.select(", "children: { $0.children }",
            "isMatch: { $0.elementType == .button && $0.label.hasPrefix(prefix) }",
            "geometry: { ($0.frame, $0.isEnabled) }", "return sample",
            """throw NSError(domain: "PublicButtonSelection", code: 1)""",
        ).forEach { assertContains(capture, it) }
        listOf(".firstMatch", ".matching(", ".isHittable", ".tap()", ".typeText(",
            "print(", ".value", ".identifier", ".debugDescription",
        ).forEach { assertFalse(it in capture) }
        val selection = swift.substringAfter("private enum PublicButtonSnapshot {")
            .substringBefore("final class PublicButtonSnapshotTests")
        listOf("count <= 2048", "depth <= 32", "guard selected == nil else { return nil }",
            "selected = geometry(node)", "descendants.count <= 2048 - count - pending.count",
            "return selected",
        ).forEach { assertContains(selection, it) }
        listOf("XCUIElement", "print(", ".tap()", ".typeText(", ".snapshot()",
            "sleep(", ".label", ".value",
        ).forEach { assertFalse(it in selection) }
        listOf("testUniqueControlUsesOneGenerationWithoutAnotherQuery",
            "testMissingAndDuplicateControlsCannotProvideAFrame",
            "testDisabledControlStillBreaksFrameAdjacency",
            "testOversizedTreeCannotAcceptAnEarlierMatch",
            "testExcessiveDepthCannotAcceptAnEarlierMatch",
        ).forEach { assertContains(swift, "func $it()") }
    }

    @Test
    fun public_ios_keyboard_geometry_rejects_invalid_frames_before_hittability() {
        val swift = read("iosApp/iosAppUITests/IOSAppLaunchUITests.swift")
        val cycle = swift.substringAfter("private func verifyKeyboardCycle(")
            .substringBefore("func testArabicDisplayNameAndAsciiRoomCodeThroughNativeInput")
        listOf("readPublicKeyboardGeometry(app, actionPrefix: actionPrefix)",
            "frames.observe(sample, hittable: { field.isHittable })",
            "XCTWaiter.wait(for: [settled], timeout: 10)",
            "XCTAssertGreaterThanOrEqual(gap, 0)", "XCTAssertLessThanOrEqual(gap, 32)",
        ).forEach { assertContains(cycle, it) }
        val capture = swift.substringAfter("private func readPublicKeyboardGeometry(")
            .substringBefore("// Test-only exact comparison.")
        assertEquals(1, Regex(Regex.escape("app.snapshot()")).findAll(capture).count())
        listOf("fieldFrames.count == 1", "actionFrames.count == 1", "keyFrames.count == 1",
            "count <= 2048", "depth <= 32", "node.label.hasPrefix(actionPrefix)",
        ).forEach { assertContains(capture, it) }
        listOf("print(", ".value", ".debugDescription", "sleep(", ".tap()", ".typeText(").forEach {
            assertFalse(it in capture)
        }
        val frames = swift.substringAfter("private struct PublicKeyboardFrames {")
            .substringBefore("final class PublicKeyboardGeometryTests")
        assertTrue(frames.indexOf("sample.isValid") < frames.indexOf("hittable()"))
        assertTrue(frames.indexOf("guard stable") < frames.indexOf("hittable()"))
        listOf("testInfiniteEmptyAndNegativeFramesNeverRequestAHitPoint",
            "testThirtyThreePointAndKeyboardSizedGapsRemainFailures",
            "testActualIos26AsciiBackgroundIsIncludedWithoutAnOffset",
            "testWholeWindowAndUnrelatedRowsCannotHideExcessiveSpacing",
        ).forEach { assertContains(swift, "func $it()") }
    }

    @Test
    fun public_ios_exact_value_observer_handles_only_the_identified_late_keyboard_tutorial() {
        val swift = read("iosApp/iosAppUITests/IOSAppLaunchUITests.swift")
        val observer = swift.substringAfter("private func assertTextValue(")
            .substringBefore("private func readKeyboardIntroduction(")
        val code = observer.lineSequence().map { it.substringBefore("//") }.joinToString("\n")
        listOf(
            "if let introduction", "dismissKeyboardIntroduction(app, dismissal: introduction)",
            "let value = snapshot?.value as? String",
            "let exact = matchesPublicTextValue(value, expected: expected)",
            "return exact",
            "XCTWaiter.wait(for: [committed], timeout: 10)",
            "XCTAssertEqual(field.value as? String, expected",
            "guard !dismissal.hasDismissed", "try? readKeyboardIntroduction(app)",
            "let frame = observation.targetFrame", "buttonFrame: frame",
            "applicationFrame: observation.applicationFrame", "hittable: button.isHittable",
            "app.buttons.matching(NSPredicate(format: \"label == %@\", \"Continue\")).element",
        ).forEach { assertContains(code, it) }
        assertEquals(1, Regex(Regex.escape("button.tap()")).findAll(code).count())
        listOf(".typeText", "sleep(", "press(forDuration", "while ", "app.debugDescription",
            "addUIInterruptionMonitor").forEach { assertFalse(it in code) }
        val journey = swift.substringAfter("private func verifyAdditionalGamesSetup(")
            .substringBefore("func testLastLightLocalPlayerFields")
        assertEquals(1, Regex(Regex.escape("name.typeText(\"Table Test\")")).findAll(journey).count())
        assertContains(journey, "assertTextValue(name, in: app, equals: \"Table Test\", introduction: introduction)")
        listOf(
            "testLateIntroductionAfterInitialAbsenceCanBeDismissedExactlyOnce",
            "testGenericContinueWithoutIdentifiedTutorialNeverAdmitsATap",
            "testNonhittableIntroductionDoesNotConsumeTheSingleDismissal",
            "testMissingInvalidOffscreenOrUpperScreenButtonCannotBeDismissed",
        ).forEach { assertContains(swift, "func $it()") }
    }

    @Test
    fun public_ios_keyboard_tutorial_is_one_bounded_snapshot_without_repeated_remote_properties() {
        val swift = read("iosApp/iosAppUITests/IOSAppLaunchUITests.swift")
        val capture = swift.substringAfter("private func readKeyboardIntroduction(")
            .substringBefore("private func fullKeyboardTop(")
        val code = capture.lineSequence().map { it.substringBefore("//") }.joinToString("\n")
        assertEquals(1, Regex(Regex.escape("app.snapshot()")).findAll(code).count())
        listOf("count <= 2048", "depth <= 32", "tutorialCount += 1",
            "node.elementType == .staticText", "node.elementType == .button",
            "Speed up your typing by sliding your finger across the letters to compose a word.",
            "node.label == \"Continue\"", "frame: node.frame, enabled: node.isEnabled",
            "applicationFrame: root.frame", "for child in node.children",
        ).forEach { assertContains(code, it) }
        listOf(".exists", "app.frame", "app.buttons", "app.staticTexts", "print(",
            ".value", ".debugDescription", "sleep(", ".tap()", ".typeText(").forEach {
            assertFalse(it in code)
        }
        val dismissal = swift.substringAfter("private func dismissKeyboardIntroduction(")
            .substringBefore("private func readKeyboardIntroduction(")
        listOf("tutorial.exists", "button.exists", "button.frame", "app.frame").forEach {
            assertFalse(it in dismissal)
        }
        assertContains(swift, "tutorialCount == 1, buttons.count == 1, buttons[0].enabled")
        listOf("testOnlyOneEnabledButtonWithOneTutorialIsAccepted",
            "testMissingDuplicateOrDisabledTutorialTargetsCannotAdmitATap",
        ).forEach { assertContains(swift, "func $it()") }
    }

    @Test
    fun public_ios_value_observation_is_coherent_bounded_and_keeps_exact_assertions() {
        val swift = read("iosApp/iosAppUITests/IOSAppLaunchUITests.swift")
        val observer = swift.substringAfter("private func assertTextValue(")
            .substringBefore("private func dismissKeyboardIntroduction(")
        val code = observer.lineSequence().map { it.substringBefore("//") }.joinToString("\n")
        assertEquals(1, Regex(Regex.escape("field.snapshot()")).findAll(code).count())
        // Interpolation-only bans miss direct print(expected). Permit exactly
        // the existing categorical failure-ledger print, never another sink.
        assertEquals(1, Regex("\\bprint\\s*\\(").findAll(code).count())
        assertContains(code, """print("Public text commit: " + samples.joined(separator: "; "))""")
        listOf(
            "snapshot = try field.snapshot()", "snapshot = nil",
            "snapshotErrorCode = (error as NSError).code", "if samples.count < 12",
            "snapshotPresent=", "stringValue=", "empty=", "exact=", "querySeconds=",
            "XCTWaiter.wait(for: [committed], timeout: 10)", "if result != .completed",
            "XCTAssertEqual(result, .completed", "XCTAssertEqual(field.value as? String, expected",
            "screenshot.lifetime = .keepAlways",
        ).forEach { assertContains(code, it) }
        listOf(
            "field.exists", "field.frame", "field.isHittable", ".typeText", ".tap()",
            "sleep(", ".localizedDescription", ".userInfo", ".debugDescription",
            "\\(value)", "\\(expected)", "try? field.snapshot()",
        ).forEach { assertFalse(it in code) }
        val comparison = swift.substringAfter("private func matchesPublicTextValue(")
            .substringBefore("final class PublicTextValueTests")
        assertContains(comparison, "guard let value = value as? String else { return false }")
        assertContains(comparison, "return value == expected")
        listOf("testOnlyTheCompleteExpectedStringMatches",
            "testMissingOrNonStringValueNeverMatchesEvenAnEmptyExpectation",
            "testWhitespaceCaseAndArabicCharactersAreNotRewritten",
        ).forEach { assertContains(swift, "func $it()") }
    }

    @Test
    fun public_ios_reopened_inputs_require_exact_native_edits_without_transport_or_repair() {
        val swift = read("iosApp/iosAppUITests/IOSAppLaunchUITests.swift")
        listOf("English", "Arabic").forEach { language ->
            assertContains(swift, "func testPostDoneNameAndRoomCodeEditsIn$language()")
            assertContains(swift, "func testKeyboardInsetsOnNameAndRoomCodeIn$language()")
        }
        val helper = swift.substringAfter("private func verifyPostDoneInputs(")
            .substringBefore("private func assertTextValue(")
        val code = helper.lineSequence().map { it.substringBefore("//") }.joinToString("\n")
        listOf(
            "verifyPostDoneField(app, actionPrefix: copy.continueToCode, isCode: false)",
            "verifyPostDoneField(app, actionPrefix: copy.findRoom, isCode: true)",
            "let initial = String(repeating: \"A\", count: isCode ? 4 : 5)",
            "field.typeText(initial)", "field.typeText(\"AA\")",
            "field.typeText(XCUIKeyboardKey.delete.rawValue)", "field.typeText(\"A\")",
            "equals: inserted", "equals: deleted", "equals: expectedFinal",
            "XCTAssertEqual(action.isEnabled, !isCode)",
            "!app.keyboards.firstMatch.exists", "XCTWaiter.wait(for: [dismissed], timeout: 10)",
            "XCTAssertTrue(action.exists", "assertTextValue(field, in: app, equals: expected)",
        ).forEach { assertContains(code, it) }
        assertEquals(1, Regex(Regex.escape("try tapSetupButton(app, prefix: copy.continueToCode)")).findAll(code).count())
        assertEquals(1, Regex(Regex.escape("field.typeText(XCUIKeyboardKey.delete.rawValue)")).findAll(code).count())
        listOf("prefix: copy.findRoom)", "action.tap()", "while ", "for _", "sleep(",
            "performTextInput", "rightArrow", "leftArrow", "app.debugDescription",
        ).forEach { assertFalse(it in code) }
    }

    @Test
    fun catalog_scroll_anchor_is_owned_above_navigation_entry_restoration() {
        val app = read("composeApp/src/commonMain/kotlin/com/parlor/app/App.kt")
        assertContains(app, "val homeScrollState = rememberHomeCatalogScrollState()")
        assertTrue(app.indexOf("val homeScrollState = rememberHomeCatalogScrollState()") < app.indexOf("AppNavDisplay("))
        assertContains(app.substringAfter("homeContent = {"), "catalogScrollState = homeScrollState")
        val home = read("composeApp/src/commonMain/kotlin/com/parlor/app/shell/home/HomeScreen.kt")
        assertContains(home, "catalogScrollState: HomeCatalogScrollState = rememberHomeCatalogScrollState()")
        assertContains(home.substringAfter("LazyColumn("), "state = catalogScrollState.listState")
        assertContains(home, "if (!recoveryLoading) catalogScrollState.restoreAfterRecovery(itemKeys)")
        val state = read("composeApp/src/commonMain/kotlin/com/parlor/app/shell/home/HomeCatalogScrollState.kt")
        assertContains(state, "rememberSaveable(saver = HomeCatalogScrollState.saver)")
        assertContains(state, "pendingAnchor?.let { return it }")
        assertContains(state, "snapshotFlow { state.listState.isScrollInProgress }")
        val regression = read(
            "composeApp/src/desktopTest/kotlin/com/parlor/app/shell/home/HomeCatalogRestorationTest.kt",
        )
        listOf("rememberSaveableStateHolder()", "catalogScrollState = catalogScrollState", "loading.value = true",
            "loading.value = false", "repeat(2)", "assertEquals(before, after",
            "verifyRestoration(AppLanguage.Arabic, 2f)",
        ).forEach { assertContains(regression, it) }
        val cold = read("composeApp/src/desktopTest/kotlin/com/parlor/app/shell/home/HomeCatalogColdRestorationTest.kt")
        listOf("registry.value.performSave()", "SaveableStateRegistry(saved)", "visible.value = false",
            "loading.value = true", "loading.value = false", "assertEquals(before, after",
            "verifyRootRestoration(AppLanguage.Arabic, 2f)",
        ).forEach { assertContains(cold, it) }
    }

    @Test
    fun linux_job_enforces_tests_analysis_android_release_and_artifact_inspection() {
        val workflow = read(".github/workflows/production-verification.yml")
        val rootBuild = read("build.gradle.kts")

        listOf(
            "productionCheck",
            "productionStaticAnalysis",
            "allTests",
            "--dependency-verification=strict",
            "unzip -t",
            "base/manifest/AndroidManifest.xml",
            "base/dex/classes.dex",
            "intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml",
            "sha256sum",
        ).forEach { required ->
            assertContains(
                workflow,
                required,
                message = "Linux CI must retain the release gate: $required",
            )
        }

        val androidGate = rootBuild.substringAfter("val productionAndroidCheck")
            .substringBefore("val productionAndroidSigningCheck")
        listOf(
            ":composeApp:testDebugUnitTest",
            ":composeApp:testReleaseUnitTest",
            ":composeApp:compileReleaseKotlinAndroid",
            ":composeApp:lintRelease",
            ":composeApp:verifyReleaseLintWarnings",
            ":composeApp:verifyMergedReleaseManifest",
            ":composeApp:minifyReleaseWithR8",
            ":composeApp:bundleRelease",
        ).forEach { task ->
            assertContains(
                androidGate,
                task,
                message = "productionAndroidCheck must enforce $task",
            )
        }

        val staticGate = rootBuild.substringAfter("val productionStaticAnalysis")
            .substringBefore("tasks.register(\"productionAppleCheck\")")
        assertContains(staticGate, "dependsOn(staticAnalysis, typeAwareStaticAnalysis)")
        assertContains(
            staticGate,
            "Runs the host-independent production static-analysis policy; run " +
                "productionAppleStaticAnalysis on macOS for iOS source sets.",
        )
        val repositoryStaticGate = rootBuild.substringAfter("val staticAnalysis")
            .substringBefore("val productionStaticAnalysis")
        assertContains(repositoryStaticGate, "includedBuild(\"build-logic\")")
        assertContains(repositoryStaticGate, "task(\":convention:detekt\")")
        val detektConvention = read(
            "build-logic/convention/src/main/kotlin/com/parlor/buildlogic/DetektConventionPlugin.kt",
        )
        listOf(
            "reports/detekt/\$name.html",
            "reports/detekt/\$name.sarif",
            "reports/detekt/\$name.xml",
            "element.file.toPath().normalize().startsWith(generatedRoot)",
        ).forEach { contract -> assertContains(detektConvention, contract) }
        assertFalse("baseline.setFrom" in detektConvention)
        listOf(
            "detektMetadataCommonMain",
            "detektDesktopMain",
            "detektDesktopTest",
            "detektAndroidRelease",
            "detektAndroidReleaseUnitTest",
            "detektAndroidDebugAndroidTest",
        ).forEach { task ->
            assertContains(rootBuild, task, message = "Host type-aware analysis must retain $task")
        }
        assertContains(rootBuild, "tasks.named(\"typeAwareStaticAnalysis\")")
        assertContains(rootBuild, "tasks.matching { it.name in hostTypeAwareDetektTasks }.all")
        assertContains(rootBuild, "androidInstrumentedSources.isEmpty")
        assertContains(rootBuild, "it.name == androidInstrumentedTypeAwareDetektTask")
        val productionGate = rootBuild.substringAfter("tasks.register(\"productionCheck\")")
            .substringBefore("subprojects")
        assertContains(productionGate, "productionStaticAnalysis")

        val appBuild = read("composeApp/build.gradle.kts")
        assertContains(
            appBuild,
            "intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml",
        )
        val mergedManifestGate = appBuild.substringAfter("val verifyMergedReleaseManifest")
            .substringBefore("val verifyReleaseSigning")
        listOf(
            "processReleaseManifest",
            "actualPermissions == expectedPermissions",
            "allowBackup",
            "usesCleartextTraffic",
            "debuggable",
            "testOnly",
            "exportedComponents == expectedExportedComponents",
        ).forEach { contract -> assertContains(mergedManifestGate, contract) }
        assertFalse(
            "find composeApp/build/intermediates" in workflow,
            "CI must never select an arbitrary debug/release merged manifest",
        )

        listOf(
            "**/build/reports/detekt/",
            "**/build/reports/tests/",
            "**/build/reports/lint-results-*",
            "composeApp/build/outputs/mapping/release/",
            "composeApp/build/outputs/bundle/release/",
        ).forEach { evidence ->
            assertContains(
                workflow,
                evidence,
                message = "CI must retain diagnostic evidence: $evidence",
            )
        }
    }

    @Test
    fun supported_desktop_hosts_execute_real_host_selected_gradle_graphs() {
        val workflow = read(".github/workflows/production-verification.yml")
        listOf(
            "  desktop-linux-arm64:",
            "  desktop-macos-x64:",
            "  desktop-windows-x64:",
        ).forEach { job -> assertContains(workflow, job) }

        val linuxArm64Job = workflow
            .substringAfter("  desktop-linux-arm64:")
            .substringBefore("  desktop-macos-x64:")
        listOf(
            "runs-on: ubuntu-24.04-arm",
            "test \"\$(uname -s)\" = \"Linux\"",
            "test \"\$(uname -m)\" = \"aarch64\"",
            "./gradlew productionDesktopCheck",
            "--dependency-verification=strict",
        ).forEach { contract -> assertContains(linuxArm64Job, contract) }
        assertFalse(
            "downloadKotlinNativeDistribution" in linuxArm64Job,
            "Linux arm64 support is Desktop-only because Kotlin Native has no Linux arm64 host distribution",
        )

        val macosX64Job = workflow
            .substringAfter("  desktop-macos-x64:")
            .substringBefore("  desktop-windows-x64:")
        listOf(
            "runs-on: macos-15-intel",
            "test \"\$(uname -s)\" = \"Darwin\"",
            "test \"\$(uname -m)\" = \"x86_64\"",
            "./gradlew productionDesktopCheck :composeApp:downloadKotlinNativeDistribution",
            "--dependency-verification=strict",
        ).forEach { contract -> assertContains(macosX64Job, contract) }

        val windowsX64Job = workflow
            .substringAfter("  desktop-windows-x64:")
            .substringBefore("  ios:")
        listOf(
            "runs-on: windows-2025",
            "RuntimeInformation]::OSArchitecture",
            "Architecture]::X64",
            ".\\gradlew.bat productionDesktopCheck",
            ":composeApp:downloadKotlinNativeDistribution",
            ":composeApp:processDebugResources",
            "--dependency-verification=strict",
        ).forEach { contract -> assertContains(windowsX64Job, contract) }

        assertFalse(
            "detachedConfiguration" in workflow,
            "Cross-host CI must execute the real build graphs rather than detached coordinates",
        )
    }

    @Test
    fun third_party_actions_are_pinned_to_reviewed_immutable_commits() {
        val workflow = read(".github/workflows/production-verification.yml")
        val expectedPins = listOf(
            "actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1",
            "actions/setup-java@b843ebf9b913146b38da2eaa7aeddf76074ae1ad # v6.1.0",
            "actions/upload-artifact@cf430e030ddbb5b0abf93d22962f4752f3646cd9 # v7.0.2",
        )
        expectedPins.forEach { assertContains(workflow, it) }

        val unpinned = Regex("uses:\\s+[^\\s@]+@(?:v|main|master)[^\\s]*")
            .findAll(workflow)
            .map { it.value }
            .toList()
        assertFalse(unpinned.isNotEmpty(), "Mutable third-party action refs: $unpinned")
    }

    @Test
    fun clean_ci_can_resolve_required_artifacts_with_strict_dependency_verification() {
        val verificationMetadata = read("gradle/verification-metadata.xml")
        fun verifyComponent(componentMarker: String, artifactName: String, sha256: String) {
            assertContains(verificationMetadata, componentMarker)
            val component = verificationMetadata
                .substringAfter(componentMarker)
                .substringBefore("</component>")
            assertContains(component, "<artifact name=\"$artifactName\">")
            assertContains(
                component,
                "<sha256 value=\"$sha256\"",
                message = "Fresh CI must verify $artifactName before plugin resolution",
            )
        }

        verifyComponent(
            componentMarker =
                "<component group=\"org.jetbrains.kotlinx\" name=\"kotlinx-coroutines-bom\" version=\"1.6.4\">",
            artifactName = "kotlinx-coroutines-bom-1.6.4.pom",
            sha256 = "ab2614855fba66aa8a42514dbe3d5a884315ffe1ed63f5932e710a8006245ce1",
        )
        verifyComponent(
            componentMarker =
                "<component group=\"com.google.guava\" name=\"guava-parent\" version=\"33.3.1-jre\">",
            artifactName = "guava-parent-33.3.1-jre.pom",
            sha256 = "55441db27e8869dfefe053059bdf478bdc7e95585642bf391f0023345fd56287",
        )
        verifyComponent(
            componentMarker = "<component group=\"org.junit\" name=\"junit-bom\" version=\"5.10.2\">",
            artifactName = "junit-bom-5.10.2.module",
            sha256 = "de23b114b3e4119a8fe6eb17bed5a3852816698bace67071579d6d927ebb080a",
        )
        verifyComponent(
            componentMarker = "<component group=\"org.junit\" name=\"junit-bom\" version=\"5.9.2\">",
            artifactName = "junit-bom-5.9.2.module",
            sha256 = "ab137ba5a8e32c9b066bf9126a1c76dd5614b724ba5c0b02549772b5e9f4cf1f",
        )
        verifyComponent(
            componentMarker =
                "<component group=\"org.junit\" name=\"junit-bom\" version=\"5.11.0-M2\">",
            artifactName = "junit-bom-5.11.0-M2.module",
            sha256 = "86477abcf490d6ca059aa9973cb108d22a506f49d1a5569bb32cc6cbf43c2cce",
        )
        verifyComponent(
            componentMarker =
                "<component group=\"org.jetbrains.kotlin\" name=\"kotlin-native-prebuilt\" version=\"2.4.10\">",
            artifactName = "kotlin-native-prebuilt-2.4.10-linux-x86_64.tar.gz",
            sha256 = "c9e356e8518144f275f1514cfe38b07db949f93e47e054832b8974fff1fd33e0",
        )
        verifyComponent(
            componentMarker =
                "<component group=\"org.jetbrains.kotlinx\" name=\"kotlinx-coroutines-bom\" version=\"1.8.0\">",
            artifactName = "kotlinx-coroutines-bom-1.8.0.pom",
            sha256 = "1239e9dbe1397cd5971342956b2511bc3ace7b641842e4372a088dcfa8b9ad55",
        )
        verifyComponent(
            componentMarker =
                "<component group=\"dev.whyoleg.cryptography\" name=\"cryptography-bom\" version=\"0.6.0\">",
            artifactName = "cryptography-bom-0.6.0.pom",
            sha256 = "3e83e5af287ca142a03ab81a35395e59bb20cb43d63915b2f96049835eb22d6b",
        )
        verifyComponent(
            componentMarker =
                "<component group=\"com.android.tools.build\" name=\"aapt2\" version=\"8.13.2-14304508\">",
            artifactName = "aapt2-8.13.2-14304508-linux.jar",
            sha256 = "839609d6d776d6dd60a02aa577d97193ce3e650cf1deaabf062321e23bbd6bf6",
        )
        verifyComponent(
            componentMarker =
                "<component group=\"org.jetbrains.compose.desktop\" name=\"desktop-jvm-linux-x64\" version=\"1.10.3\">",
            artifactName = "desktop-jvm-linux-x64-1.10.3.pom",
            sha256 = "5a6361c112715bea2334898e208ec82640863d592287c2828b9edf56d5d733a9",
        )
        verifyComponent(
            componentMarker =
                "<component group=\"org.jetbrains.skiko\" name=\"skiko-awt-runtime-linux-x64\" version=\"0.9.37.4\">",
            artifactName = "skiko-awt-runtime-linux-x64-0.9.37.4.jar",
            sha256 = "ec796df135d980bbb1740e789fe8668a8184df243e4d1c39997750303c76f13b",
        )
        verifyComponent(
            componentMarker =
                "<component group=\"org.jetbrains.skiko\" name=\"skiko-awt-runtime-linux-x64\" version=\"0.9.37.4\">",
            artifactName = "skiko-awt-runtime-linux-x64-0.9.37.4.pom",
            sha256 = "39d4d3b389fb0191258e0202b7b0de6b09210509bcd6168b2774a5b24f2ed6a2",
        )
    }

    @Test
    fun apple_jobs_run_kotlin_and_app_launch_tests_all_link_targets_and_unsigned_swift_release() {
        val workflow = read(".github/workflows/production-verification.yml")
        val runtimeJob = workflow.substringAfter("\n  ios-kmp:\n").substringBefore("\n  ios:\n")
        val swiftJob = workflow.substringAfter("\n  ios:\n").substringBefore("\n  ios-runtime:\n")
        val releaseJob = workflow.substringAfter("\n  ios-release-simulator:\n").substringBefore("\n  ios-release-device:\n")
        assertContains(
            releaseJob.substringBefore("\n    steps:\n"),
            "timeout-minutes: 180",
            message = "Serial six-game release links need bounded time for the complete Swift package gate",
        )
        val rootBuild = read("build.gradle.kts")
        val composeBuild = read("composeApp/build.gradle.kts")
        val xcodeProject = read("iosApp/iosApp.xcodeproj/project.pbxproj")
        val xcodeScheme = read("iosApp/iosApp.xcodeproj/xcshareddata/xcschemes/iosApp.xcscheme")
        val appLaunchTest = read("iosApp/iosAppUITests/IOSAppLaunchUITests.swift")
        val homeScreen = read(
            "composeApp/src/commonMain/kotlin/com/parlor/app/shell/home/HomeScreen.kt",
        )
        val frameworkNormalizer = read(
            "scripts/release/normalize_embedded_apple_framework.sh",
        )

        listOf(
            "DEVELOPER_DIR: /Applications/Xcode_26.3.app/Contents/Developer",
            "Xcode 26.3",
            "Build version 17C529",
            ".toolchains.apple.minimum_ios_sdk_major",
            "xcrun --sdk iphoneos --show-sdk-version",
            "xcrun --sdk iphonesimulator --show-sdk-version",
            "productionIosSimulatorRuntimeTests",
            "productionAppleCheck",
            "Apple type-aware static analysis",
            "--dependency-verification=strict",
            "xcodebuild",
            "-configuration Release",
            "generic/platform=iOS Simulator",
            "CODE_SIGNING_ALLOWED=NO",
            "CODE_SIGNING_REQUIRED=NO",
            "plutil -lint",
            "runtime tests",
            "linkage-only",
        ).forEach { required ->
            assertContains(
                workflow,
                required,
                message = "Apple CI must retain the release gate: $required",
            )
        }

        val appleTestStep = runtimeJob
            .substringAfter("- name: Run all KMP iOS simulator runtime tests")
            .substringBefore("\n      - name:")
        assertContains(
            appleTestStep,
            "./gradlew productionIosSimulatorRuntimeTests --dependency-verification=strict --no-daemon",
            message = "The runtime job must run every executable KMP simulator test",
        )
        val appleReleaseStep = releaseJob
            .substringAfter("- name: Run complete Apple static analysis")
            .substringBefore("\n      - name:")
        assertContains(
            appleReleaseStep,
            "./gradlew productionAppleStaticAnalysis --dependency-verification=strict --no-daemon",
            message = "The simulator lane retains complete Apple analysis; the required join retains all Release architectures",
        )
        assertFalse(
            "./gradlew allTests" in runtimeJob || "./gradlew allTests" in releaseJob,
            "Apple CI must not duplicate the Linux common/desktop/Android aggregate",
        )
        assertFalse("./gradlew productionAppleCheck" in runtimeJob)
        assertFalse("./gradlew productionIosSimulatorRuntimeTests" in releaseJob)

        val simulatorRuntimeGate = rootBuild
            .substringAfter("val productionIosSimulatorRuntimeTests")
            .substringBefore("val productionAndroidCheck")
        assertContains(simulatorRuntimeGate, "Runs every KMP iosSimulatorArm64 runtime test")
        assertContains(rootBuild, "it.name == \"iosSimulatorArm64Test\"")
        assertContains(rootBuild, "tasks.named(\"productionIosSimulatorRuntimeTests\")")
        assertContains(
            xcodeProject,
            "./gradlew --no-daemon :composeApp:embedAndSignAppleFrameworkForXcode " +
                "--dependency-verification=strict",
            message = "The Xcode shell phase must enforce strict dependency verification",
        )
        assertContains(
            composeBuild,
            "isStatic = false",
            message = "The launchable app must embed a dynamic Kotlin framework, not a static archive",
        )
        assertContains(
            xcodeProject,
            "scripts/release/normalize_embedded_apple_framework.sh",
            message = "The Xcode shell phase must normalize the embedded framework's case",
        )
        listOf(
            "canonical_name=\"\${framework_base_name}.framework\"",
            "candidate_lowercase=",
            "match_count=\$((match_count + 1))",
            ".case-normalization",
            "/bin/mv \"\$match\" \"\$temporary_framework\"",
            "embedded framework executable does not match its Mach-O install name",
        ).forEach { required ->
            assertContains(
                frameworkNormalizer,
                required,
                message = "The Apple build must preserve the framework's case-sensitive identity",
            )
        }

        val appLaunchMarker = "- name: Launch Swift host and Compose root on iOS Simulator"
        val swiftReleaseMarker = "- name: Build unsigned Swift Release wrapper"
        assertContains(swiftJob, appLaunchMarker)
        val appLaunchStep = swiftJob
            .substringAfter(appLaunchMarker)
            .substringBefore("\n      - name:")
        listOf(
            "id: apple_ui_run",
            "scripts.ci.apple_verification_hygiene create-simulator apple-ui",
            "test ! -e build/ci-evidence/ios-ui-tests.xcresult",
            "-configuration Debug",
            "platform=iOS Simulator,id=\$simulator_udid",
            "-parallel-testing-enabled NO",
            "-maximum-concurrent-test-simulator-destinations 1",
            "-resultBundlePath build/ci-evidence/ios-ui-tests.xcresult",
            "test | tee build/ci-evidence/xcode-ui-test.log",
        ).forEach { required ->
            assertContains(
                appLaunchStep,
                required,
                message = "Apple CI must launch the real app through XCTest: $required",
            )
        }
        assertFalse("xcrun simctl list devices available --json" in appLaunchStep)
        assertOwnedAppleUiLifecycle(swiftJob, appLaunchMarker)
        listOf(
            "com.apple.product-type.bundle.ui-testing",
            "IOSAppLaunchUITests.swift in Sources",
            "TEST_TARGET_NAME = iosApp",
        ).forEach { required -> assertContains(xcodeProject, required) }
        listOf(
            "TestableReference",
            "BlueprintName = \"iosAppUITests\"",
        ).forEach { required -> assertContains(xcodeScheme, required) }
        listOf(
            "XCUIApplication()",
            "runningForeground",
            "staticTexts[\"parlor-home-brand\"]",
            "No alert should appear during the simulator cold-start observation window",
            "The Swift host should remain in the foreground after Compose renders",
        ).forEach { required -> assertContains(appLaunchTest, required) }
        listOf(
            "HOME_BRAND_TEST_TAG = \"parlor-home-brand\"",
            ".testTag(HOME_BRAND_TEST_TAG)",
        ).forEach { required ->
            assertContains(
                homeScreen,
                required,
                message = "The iOS launch probe must target a stable Compose home marker",
            )
        }

        val appleEvidenceMarker = "- name: Upload Apple release verification evidence"
        assertContains(releaseJob, swiftReleaseMarker)
        assertContains(releaseJob, appleEvidenceMarker)
        val swiftReleaseStep = releaseJob
            .substringAfter(swiftReleaseMarker)
            .substringBefore(appleEvidenceMarker)
        listOf(
            "ARCHS=arm64",
            "ONLY_ACTIVE_ARCH=YES",
        ).forEach { required ->
            assertContains(
                swiftReleaseStep,
                required,
                message = "The unsigned Swift Release wrapper must remain a deterministic single-architecture build: $required",
            )
        }

        listOf(
            "docs/IOS_SETUP.md",
            "docs/RELEASE_RUNBOOK.md",
        ).forEach { path ->
            val releaseInstructions = read(path)
            assertContains(
                releaseInstructions,
                "-configuration Release",
                message = "$path must document the Release wrapper build",
            )
            assertContains(
                releaseInstructions,
                "ARCHS=arm64",
                message = "$path must document the qualified wrapper architecture",
            )
            assertContains(
                releaseInstructions,
                "ONLY_ACTIVE_ARCH=YES",
                message = "$path must document the qualified wrapper architecture policy",
            )
        }

        val appleGate = rootBuild.substringAfter("tasks.register(\"productionAppleCheck\")")
            .substringBefore("tasks.register(\"productionCheck\")")
        listOf(
            "productionAppleStaticAnalysis",
            ":composeApp:linkReleaseFrameworkIosArm64",
            ":composeApp:linkReleaseFrameworkIosSimulatorArm64",
            ":composeApp:linkReleaseFrameworkIosX64",
        ).forEach { task ->
            assertContains(
                appleGate,
                task,
                message = "productionAppleCheck must retain $task",
            )
        }
        listOf(
            "detektMetadataNativeMain",
            "detektMetadataAppleMain",
            "detektMetadataIosMain",
            "detektIosArm64Main",
            "detektIosArm64Test",
            "detektIosSimulatorArm64Main",
            "detektIosSimulatorArm64Test",
            "detektIosX64Main",
            "detektIosX64Test",
        ).forEach { task ->
            assertContains(rootBuild, task, message = "Apple type-aware analysis must retain $task")
        }
        assertContains(rootBuild, "tasks.named(\"productionAppleStaticAnalysis\")")
        assertContains(rootBuild, "tasks.matching { it.name in appleTypeAwareDetektTasks }.all")
    }

    @Test
    fun split_apple_jobs_keep_independent_source_toolchain_cycles_and_evidence() {
        val workflow = read(".github/workflows/production-verification.yml")
        val runtime = workflow.substringAfter("\n  ios-kmp:\n").substringBefore("\n  ios:\n")
        val swift = workflow.substringAfter("\n  ios:\n").substringBefore("\n  ios-runtime:\n")
        val release = workflow.substringAfter("\n  ios-release-simulator:\n").substringBefore("\n  ios-release-device:\n")
        listOf(runtime to listOf("apple-aggregate"), swift to listOf("apple-ui"),
            release to listOf("apple-aggregate", "apple-wrapper"))
            .forEach { (job, cycles) ->
                val header = job.substringBefore("\n    steps:")
                assertContains(header, if (job != release) "runs-on: macos-15" else "runs-on: \${{ inputs.apple_release_runner == 'intel' && 'macos-15-intel' || 'macos-15' }}")
                assertContains(header, "DEVELOPER_DIR: /Applications/Xcode_26.3.app/Contents/Developer")
                assertFalse("needs:" in header || "strategy:" in header)
                listOf(
                    "java-version: \"21\"",
                    "Build version 17C529",
                    ".toolchains.apple.minimum_ios_sdk_major",
                    "git rev-parse HEAD | tee build/ci-evidence/commit.txt",
                    "test -z \"\$(git status --porcelain)\"",
                    "[\"verification_hygiene.py\", \"prepare\"]",
                    "[\"verification_hygiene.py\", \"cleanup\"]",
                    "name: verification-cleanup-\${{ github.job }}",
                ).forEach { required -> assertContains(job, required) }
                assertEquals(
                    cycles,
                    Regex("scripts\\.ci\\.apple_verification_hygiene finish (apple-[a-z]+)")
                        .findAll(job).map { it.groupValues[1] }.toList(),
                )
                val cleanup = job.substringAfter("- name: Clean only attested verification outputs")
                    .substringBefore("\n      - name:")
                assertEquals(cycles, Regex("\"(apple-[a-z]+)\":").findAll(cleanup).map { it.groupValues[1] }.toList())
                assertContains(cleanup, "steps.verification_artifact.outputs.artifact-id")
                assertContains(cleanup, "steps.verification_artifact.outputs.artifact-digest")
            }
        assertContains(runtime, "name: ios-runtime-verification")
        assertContains(swift, "name: ios-swift-verification")
        assertFalse("productionIosSimulatorRuntimeTests" in swift)
        assertFalse("create-simulator apple-ui" in runtime)
        listOf(runtime, swift).forEach { job ->
            assertContains(job, "test \"\$(cat build/ci-evidence/architecture.txt)\" = arm64")
            assertContains(job, "scripts.ci.apple_runtime_gate lane")
        }
        val join = workflow.substringAfter("\n  ios-runtime:\n").substringBefore("\n  ios-release-simulator:\n")
        assertContains(join, "name: iOS simulator runtime and Swift host")
        assertContains(join, "needs: [ios-kmp, ios]")
        assertContains(join, "if: always() && (github.event_name != 'workflow_dispatch' || inputs.verification_scope == 'full')")
        assertContains(join, "PARLOR_APPLE_RUNTIME_NEEDS: \${{ toJSON(needs) }}")
        assertContains(join, "scripts.ci.apple_runtime_gate aggregate")
        assertContains(join, "if-no-files-found: error")
        assertContains(runtime, "**/build/test-results/**/*.xml")
        assertContains(release, "name: ios-release-verification")
        assertContains(release, "**/build/reports/detekt/")
        assertContains(release, "Inspect complete unsigned iOS Release package")
        assertFalse("- name: Build unsigned Swift Release wrapper" in runtime)
        assertFalse("- name: Launch Swift host and Compose root on iOS Simulator" in release)
    }

    @Test
    fun supplemental_native_diagnostics_do_not_replace_any_full_gate() {
        val workflow = read(".github/workflows/production-verification.yml")
        assertEquals(
            listOf("desktop-android", "desktop-linux-arm64", "desktop-macos-x64", "desktop-windows-x64",
                "ios-kmp", "ios", "ios-runtime", "ios-release-simulator", "ios-release-device", "ios-release-x64", "ios-release", "ios-protection-probe",
                "darwin-host-capability"),
            Regex("(?m)^  ([a-z][a-z0-9-]+):$").findAll(workflow.substringAfter("\njobs:\n"))
                .map { it.groupValues[1] }.toList(),
        )
        val probe = workflow.substringAfter("\n  ios-protection-probe:\n").substringBefore("\n  darwin-host-capability:\n")
        assertContains(probe, "if: github.event_name == 'workflow_dispatch' && inputs.verification_scope == 'ios-protection-probe'")
        assertContains(probe, "timeout-minutes: 20")
        assertContains(probe, "PARLOR_FROZEN_SOURCE_SHA: \${{ inputs.frozen_source_sha }}")
        assertContains(probe, "PARLOR_APPROVED_PROBE_CONTROL_SHA256: \${{ inputs.approved_probe_control_sha256 }}")
        assertContains(probe, "run_probe.py assert-result")
        assertContains(probe, "PARLOR_PROTECTION_CLEANUP_ARTIFACT_DIGEST:")
        assertFalse("./gradlew" in probe || "github.token" in probe || "secrets." in probe)
    }

    @Test
    fun darwin_capability_keeps_both_free_hosts_and_bounded_native_custody() {
        val workflow = read(".github/workflows/production-verification.yml")
        val capability = workflow.substringAfter("\n  darwin-host-capability:\n")
        listOf(
            "if: github.event_name == 'workflow_dispatch' && inputs.verification_scope == 'darwin-host-capability'",
            "runs-on: \${{ matrix.runner }}",
            "timeout-minutes: 5",
            "fail-fast: false",
            "- runner: macos-15\n            architecture: arm64",
            "- runner: macos-15-intel\n            architecture: x86_64",
            "PARLOR_FROZEN_SOURCE_SHA: \${{ inputs.frozen_source_sha }}",
            "PARLOR_EXPECTED_ARCHITECTURE: \${{ matrix.architecture }}",
            "timeout-minutes: 2\n        run: /usr/bin/python3 -B -m scripts.ci.darwin_host_capability",
            "- name: Preserve native capability receipt\n        if: always()",
            "if-no-files-found: error",
            "path: \${{ runner.temp }}/parlor-darwin-capability-",
            "persist-credentials: false",
        ).forEach { assertContains(capability, it) }
        assertEquals(3, Regex("(?m)^      - name:").findAll(capability).count())
        listOf("./gradlew", "build/", "RUNNER_TRACKING_ID:", "self-hosted", "macos-latest", "|| true",
            "continue-on-error", "github.token", "secrets.").forEach { assertFalse(it in capability) }
        val runtime = workflow.substringAfter("\n  ios:\n").substringBefore("\n  ios-runtime:\n")
        assertContains(runtime.substringBefore("\n    steps:"), "inputs.verification_scope != 'darwin-host-capability'")
    }

    @Test
    fun workflow_job_boundaries_are_preserved_for_lf_and_windows_crlf_checkouts() {
        val jobs = listOf(
            "desktop-android", "desktop-linux-arm64", "desktop-macos-x64", "desktop-windows-x64",
            "ios-kmp", "ios", "ios-runtime", "ios-release-simulator", "ios-release-device", "ios-release-x64", "ios-release", "ios-protection-probe",
        )
        val lf = "on:\n  push:\njobs:\n" + jobs.joinToString("") { job -> "  $job:\n    name: $job\n" }
        fun assertJobBoundaries(checkoutText: String) {
            val workflow = normalizeContractText(checkoutText)
            assertEquals(
                jobs,
                Regex("(?m)^  ([a-z][a-z0-9-]+):$").findAll(workflow.substringAfter("\njobs:\n"))
                    .map { it.groupValues[1] }.toList(),
            )
            assertEquals(
                "    name: ios",
                workflow.substringAfter("\n  ios:\n").substringBefore("\n  ios-runtime:\n"),
            )
            assertEquals(
                "    name: ios-release-simulator",
                workflow.substringAfter("\n  ios-release-simulator:\n").substringBefore("\n  ios-release-device:\n"),
            )
            assertEquals(
                "    name: ios-protection-probe\n",
                workflow.substringAfter("\n  ios-protection-probe:\n"),
            )
        }
        listOf("\n", "\r\n").forEach { lineEnding ->
            val checkoutText = lf.replace("\n", lineEnding)
            assertEquals(lf, normalizeContractText(checkoutText))
            assertJobBoundaries(checkoutText)
            listOf("\njobs:\n", "\n  ios:\n", "\n  ios-release-simulator:\n", "\n  ios-protection-probe:\n")
                .forEach { marker ->
                    val missingBoundary = lf.replace(marker, "\n").replace("\n", lineEnding)
                    assertFailsWith<AssertionError> { assertJobBoundaries(missingBoundary) }
                }
        }
        assertEquals("literal\\r\\n and lone\rreturn", normalizeContractText("literal\\r\\n and lone\rreturn"))
    }

    private fun assertOwnedAppleUiLifecycle(workflow: String, appLaunchMarker: String) {
        val prepareMarker = "- name: Claim apple-ui native resource ownership"
        val finishMarker = "- name: Stop Gradle after apple-ui and retire owned Apple resources"
        assertContains(workflow, prepareMarker)
        assertContains(workflow, finishMarker)
        val prepareStep = workflow.substringAfter(prepareMarker).substringBefore("\n      - name:")
        assertContains(prepareStep, "id: apple_ui_prepare")
        assertContains(prepareStep, "scripts.ci.apple_verification_hygiene prepare apple-ui")
        assertTrue(workflow.indexOf(prepareMarker) < workflow.indexOf(appLaunchMarker))
        val nextStepName = workflow.substringAfter(appLaunchMarker)
            .substringAfter("\n      - name:", missingDelimiterValue = "")
            .lineSequence().first().trim()
        assertEquals(
            finishMarker.removePrefix("- name:").trim(),
            nextStepName,
            "Owned UI cleanup must immediately follow XCTest",
        )
        val finishStep = workflow.substringAfter(finishMarker).substringBefore("\n      - name:")
        listOf(
            "id: apple_ui_finish",
            "if: always()",
            "PARLOR_APPLE_PREPARE_OUTCOME: \${{ steps.apple_ui_prepare.outcome }}",
            "PARLOR_APPLE_RUN_OUTCOME: \${{ steps.apple_ui_run.outcome }}",
            "scripts.ci.apple_verification_hygiene finish apple-ui",
        ).forEach { required -> assertContains(finishStep, required) }
    }

    @Test
    fun repository_contract_inputs_invalidate_cached_results_when_ci_or_build_gates_change() {
        val transportBuild = read("shared/transport-p2p/build.gradle.kts")

        listOf(
            "build.gradle.kts",
            ".github/workflows/production-verification.yml",
            "gradle/verification-metadata.xml",
            "iosApp/iosApp.xcodeproj/project.pbxproj",
            "iosApp/iosApp.xcodeproj/xcshareddata/xcschemes/iosApp.xcscheme",
            "iosApp/iosAppUITests/IOSAppLaunchUITests.swift",
        ).forEach { input ->
            assertContains(
                transportBuild,
                input,
                message =
                    "desktopTest must be invalidated when release contract input changes: $input",
            )
        }
    }

    @Test
    fun xcode_resource_artifacts_are_covered_by_strict_dependency_verification() {
        val metadata = read("gradle/verification-metadata.xml")
        val requiredArtifacts = mapOf(
            "koin-compose-4.0.0.kotlin_resources.zip" to
                "3972eaffbc58a5a5319ddc79a5c9d42c23dfdd760338bf7f3a105b6560ea563e",
            "koin-compose-viewmodel-4.0.0.kotlin_resources.zip" to
                "0ae6478b8a832ffc7eec4f662e6cc458586045770a0c2062df2f31333928e3e5",
            "library-1.10.3.kotlin_resources.zip" to
                "4f5c9f70b6260f466e6f72b7788afc1b7772b3801830e81f8d50dfba62c28077",
        )

        requiredArtifacts.forEach { (artifact, sha256) ->
            assertContains(metadata, "artifact name=\"$artifact\"")
            assertContains(metadata, "sha256 value=\"$sha256\"")
        }
    }

    @Test
    fun one_version_source_drives_android_desktop_ios_and_runtime_compatibility() {
        val versionConfig = read("config/parlor-version.xcconfig")
        val composeBuild = read("composeApp/build.gradle.kts")
        val contentModule = read("composeApp/src/commonMain/kotlin/com/parlor/app/di/ContentModule.kt")
        val xcodeConfig = read("iosApp/Configuration/Config.xcconfig")
        val xcodeProject = read("iosApp/iosApp.xcodeproj/project.pbxproj")

        assertContains(versionConfig, "PARLOR_VERSION_NAME = 1.0.0")
        assertContains(versionConfig, "PARLOR_BUILD_NUMBER = 1")
        assertContains(composeBuild, "versionCode = parlorBuildNumber")
        assertContains(composeBuild, "versionName = parlorVersionName")
        assertContains(composeBuild, "packageVersion = parlorVersionName")
        assertContains(composeBuild, "kotlin.srcDir(generateParlorVersion)")
        assertContains(contentModule, "SemVer.parse(PARLOR_VERSION_NAME)")
        assertContains(xcodeConfig, "../../config/parlor-version.xcconfig")
        assertContains(xcodeProject, "CURRENT_PROJECT_VERSION = \"$(PARLOR_BUILD_NUMBER)\"")
        assertContains(xcodeProject, "MARKETING_VERSION = \"$(PARLOR_VERSION_NAME)\"")
        assertFalse("MARKETING_VERSION = 1.0.0" in xcodeProject)
        assertFalse("CURRENT_PROJECT_VERSION = 1;" in xcodeProject)
    }

    @Test
    fun every_version_catalog_alias_has_a_real_build_consumer() {
        val catalog = read("gradle/libs.versions.toml")
        val bundledLibraries = catalogSectionAssignments(catalog, "bundles")
            .flatMap { assignment ->
                Regex("\"([^\"]+)\"").findAll(assignment.substringAfter('='))
                    .map { match -> match.groupValues[1] }
                    .toList()
            }
            .toSet()
        val buildScripts = repositoryRoot.walkTopDown()
            .filter { file ->
                file.isFile &&
                    file.name.endsWith(".gradle.kts") &&
                    file.invariantSeparatorsPath.let { path ->
                        "/build/" !in path && "/.gradle/" !in path
                    }
            }
            .joinToString(separator = "\n", transform = File::readText)

        mapOf(
            "libraries" to "libs.",
            "plugins" to "libs.plugins.",
            "bundles" to "libs.bundles.",
        ).forEach { (section, accessorPrefix) ->
            val missing = catalogAliases(catalog, section).filterNot { alias ->
                val accessor = accessorPrefix + alias.replace('-', '.')
                accessor in buildScripts || section == "libraries" && alias in bundledLibraries
            }
            assertTrue(
                missing.isEmpty(),
                "Unused [$section] aliases must be removed from the release catalog: $missing",
            )
        }

        val referencedVersions = Regex("version\\.ref\\s*=\\s*\"([^\"]+)\"")
            .findAll(catalog)
            .map { match -> match.groupValues[1] }
            .toSet()
        val missingVersions = catalogAliases(catalog, "versions").filterNot { alias ->
            alias in referencedVersions ||
                "libs.versions.${alias.replace('-', '.')}" in buildScripts ||
                "\"$alias\"" in buildScripts
        }
        assertTrue(
            missingVersions.isEmpty(),
            "Unused [versions] aliases must be removed from the release catalog: $missingVersions",
        )
    }

    private fun read(relativePath: String): String {
        val file = File(repositoryRoot, relativePath)
        assertTrue(file.isFile, "Missing release contract file: ${file.absolutePath}")
        return normalizeContractText(file.readText())
    }

    // Git may check out semantic contract text as CRLF on Windows. Keep exact
    // content and section boundaries; only the physical line ending differs.
    private fun normalizeContractText(text: String): String = text.replace("\r\n", "\n")

    private fun catalogAliases(catalog: String, wantedSection: String): List<String> {
        return catalogSectionAssignments(catalog, wantedSection).map { assignment ->
            assignment.substringBefore('=').trim()
        }
    }

    private fun catalogSectionAssignments(
        catalog: String,
        wantedSection: String,
    ): List<String> {
        var section = ""
        return buildList {
            catalog.lineSequence().forEach { rawLine ->
                val line = rawLine.substringBefore('#').trim()
                if (line.startsWith('[') && line.endsWith(']')) {
                    section = line.removeSurrounding("[", "]")
                } else if (section == wantedSection && '=' in line) {
                    add(line)
                }
            }
        }
    }

    private fun locateRepositoryRoot(): File {
        val root = generateSequence(File(".").canonicalFile) { it.parentFile }
            .take(12)
            .firstOrNull { candidate ->
                File(candidate, "settings.gradle.kts").isFile &&
                    File(candidate, ".github/workflows/production-verification.yml").isFile
            }
        return assertNotNull(
            root,
            "Could not locate the Parlor repository root from ${File(".").absoluteFile}",
        )
    }
}
