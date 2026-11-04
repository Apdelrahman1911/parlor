import XCTest

final class IOSAppLaunchUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    @MainActor
    func testColdLaunchRendersComposeHomeWithoutUnexpectedAlert() throws {
        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()

        XCTAssertTrue(
            app.wait(for: .runningForeground, timeout: 30),
            "The Swift host should launch and remain in the foreground",
        )
        XCTAssertTrue(
            app.staticTexts["parlor-home-brand"].waitForExistence(timeout: 30),
            "The Kotlin Compose root should render the home screen",
        )
        XCTAssertFalse(
            app.alerts.firstMatch.waitForExistence(timeout: 2),
            "No alert should appear during the simulator cold-start observation window",
        )
        XCTAssertEqual(
            app.state,
            .runningForeground,
            "The Swift host should remain in the foreground after Compose renders",
        )
    }

    @MainActor
    func testKeyboardInsetsOnNameAndRoomCodeInEnglish() throws {
        try verifyKeyboardLayout(language: "en", locale: "en_US", copy: KeyboardCopy(
            openGame: "Open Whodunit and pick how you want to play.",
            join: "Enter the host's room code to join their table.",
            permission: "Continue and retry the real local-network operation.",
            continueToCode: "Save your name and continue to enter a room code.",
            findRoom: "Connect to the host using the entered room code."
        ))
    }

    @MainActor
    func testKeyboardInsetsOnNameAndRoomCodeInArabic() throws {
        try verifyKeyboardLayout(language: "ar", locale: "ar_EG", copy: KeyboardCopy(
            openGame: "افتح من القاتل واختر طريقة اللعب.",
            join: "أدخل رمز المضيف للدخول إلى طاولته.",
            permission: "تابع وأعد تنفيذ عملية الشبكة المحلية الفعلية.",
            continueToCode: "احفظ اسمك وتابع لإدخال رمز الغرفة.",
            findRoom: "الاتصال بالمضيف باستخدام الرمز المُدخل."
        ))
    }

    private struct KeyboardCopy {
        let openGame: String
        let join: String
        let permission: String
        let continueToCode: String
        let findRoom: String
    }

    @MainActor
    func testAdditionalGamesExposeOnlyMultiplayerSetupInEnglish() throws {
        try verifyAdditionalGamesSetup(language: "en", locale: "en_US", copy: MultiplayerSetupCopy(
            games: ["Open Egyptian Dominoes", "Open Ghamza", "Open Word Impostor"],
            host: "Open a room and show a code your friends can join.",
            join: "Enter the host's room code to join their table.",
            local: "Start a pass-and-play session on this device.",
            permission: "Continue and retry the real local-network operation.",
            hostContinue: "Save your name and continue to set up your table.",
            peerContinue: "Save your name and continue to enter a room code.",
            back: "Return to the home screen.",
            findRoom: "Connect to the host using the entered room code."
        ))
    }

    @MainActor
    func testAdditionalGamesExposeOnlyMultiplayerSetupInArabic() throws {
        try verifyAdditionalGamesSetup(language: "ar", locale: "ar_EG", copy: MultiplayerSetupCopy(
            games: ["افتح الدومنة المصرية", "افتح غمزة", "افتح لعبة الإمبوستر"],
            host: "افتح غرفة وأظهر الرمز لأصدقائك للانضمام.",
            join: "أدخل رمز المضيف للدخول إلى طاولته.",
            local: "ابدأ جلسة تمرير ولعب على هذا الجهاز.",
            permission: "تابع وأعد تنفيذ عملية الشبكة المحلية الفعلية.",
            hostContinue: "احفظ اسمك وتابع لإعداد طاولتك.",
            peerContinue: "احفظ اسمك وتابع لإدخال رمز الغرفة.",
            back: "العودة إلى الشاشة الرئيسية.",
            findRoom: "الاتصال بالمضيف باستخدام الرمز المُدخل."
        ))
    }

    private struct MultiplayerSetupCopy {
        let games: [String]
        let host: String
        let join: String
        let local: String
        let permission: String
        let hostContinue: String
        let peerContinue: String
        let back: String
        let findRoom: String
    }

    @MainActor
    private func verifyAdditionalGamesSetup(language: String, locale: String, copy: MultiplayerSetupCopy) throws {
        // Public navigation only, on an owned clean simulator. Never confirm a
        // host name, submit a room code, start networking, or inspect game data.
        for game in copy.games {
            let app = XCUIApplication()
            app.launchArguments += ["-AppleLanguages", "(\(language))", "-AppleLocale", locale]
            app.launch()
            defer { app.terminate() }
            XCTAssertTrue(app.staticTexts["parlor-home-brand"].waitForExistence(timeout: 30))
            try tapSetupButton(app, prefix: game)
            XCTAssertTrue(setupButton(app, prefix: copy.host).waitForExistence(timeout: 10))
            XCTAssertTrue(setupButton(app, prefix: copy.join).exists)
            XCTAssertFalse(setupButton(app, prefix: copy.local).exists)

            try tapPublicSetupTransition(app, from: copy.host, to: copy.permission)
            try tapSetupButton(app, prefix: copy.permission)
            XCTAssertTrue(app.textViews.firstMatch.waitForExistence(timeout: 10))
            XCTAssertTrue(setupButton(app, prefix: copy.hostContinue).waitForExistence(timeout: 10))
            try tapSetupButton(app, prefix: copy.back)
            // Home restores its LazyColumn position, so the brand header can
            // correctly be offscreen. Prove Back restored the selected catalog
            // entry and removed the name screen, without resetting that scroll.
            XCTAssertTrue(setupButton(app, prefix: game).waitForExistence(timeout: 10),
                          "Back must restore the selected game in the Home catalog")
            XCTAssertFalse(app.textViews.firstMatch.exists,
                           "Back must remove the host-name input")
            XCTAssertFalse(setupButton(app, prefix: copy.hostContinue).exists)

            try tapSetupButton(app, prefix: game)
            try tapPublicSetupTransition(app, from: copy.join, to: copy.permission)
            try tapSetupButton(app, prefix: copy.permission)
            let name = app.textViews.firstMatch
            XCTAssertTrue(name.waitForExistence(timeout: 10))
            try tapSettledControl(name)
            let keyboard = app.keyboards.firstMatch
            XCTAssertTrue(keyboard.waitForExistence(timeout: 10))
            let introduction = dismissKeyboardIntroduction(app)
            name.typeText("Table Test")
            assertTextValue(name, in: app, equals: "Table Test", introduction: introduction)
            name.typeText("\n")
            try tapSetupButton(app, prefix: copy.peerContinue)
            XCTAssertTrue(app.textViews.firstMatch.waitForExistence(timeout: 10))
            XCTAssertEqual(app.textViews.count, 1)
            XCTAssertTrue(setupButton(app, prefix: copy.findRoom).waitForExistence(timeout: 10))
            XCTAssertFalse(setupButton(app, prefix: copy.findRoom).isEnabled)
            XCTAssertFalse(app.alerts.firstMatch.exists, "Public setup must not request network access")
        }
    }

    @MainActor
    func testLastLightLocalPlayerFieldsScrollWithNextAndDismissWithDone() throws {
        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        defer { app.terminate() }
        XCTAssertTrue(app.staticTexts["parlor-home-brand"].waitForExistence(timeout: 30))
        try tapSetupButton(app, prefix: "Open Last Light and choose local or nearby play")
        try tapSetupButton(app, prefix: "Start a pass-and-play session on this device.")
        for _ in 0..<4 { try tapSetupButton(app, prefix: "Add a player") }
        let first = app.textViews["Player 1"]
        for _ in 0..<12 {
            if first.exists, first.isHittable { break }
            app.swipeDown(velocity: .slow)
        }
        XCTAssertTrue(first.isHittable)
        try tapSettledControl(first)
        XCTAssertTrue(app.keyboards.firstMatch.waitForExistence(timeout: 10))
        let introduction = dismissKeyboardIntroduction(app)
        for index in 1...6 {
            let field = app.textViews["Player \(index)"]
            XCTAssertTrue(field.waitForExistence(timeout: 10))
            field.typeText("Keyboard Player \(index)")
            assertTextValue(field, in: app, equals: "Keyboard Player \(index)", introduction: introduction)
            let keyboard = app.keyboards.firstMatch
            XCTAssertTrue(keyboard.exists)
            let top = fullKeyboardTop(app, keyboard: keyboard)
            let visible = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
                field.isHittable && field.frame.minY >= 0 && field.frame.maxY <= top
            }, object: nil)
            XCTAssertEqual(XCTWaiter.wait(for: [visible], timeout: 10), .completed,
                           "Next must scroll the actual focused player field above the keyboard")
            field.typeText("\n")
        }
        let dismissed = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
            !app.keyboards.firstMatch.exists
        }, object: nil)
        XCTAssertEqual(XCTWaiter.wait(for: [dismissed], timeout: 10), .completed)
        for index in 1...6 {
            assertTextValue(app.textViews["Player \(index)"], in: app, equals: "Keyboard Player \(index)")
        }
        // Stop at public setup; do not create a saved game or reveal private state.
    }

    @MainActor
    private func verifyKeyboardLayout(language: String, locale: String, copy: KeyboardCopy) throws {
        // Exercise public setup with synthetic input only. Never submit a room
        // code, start transport, or open private gameplay. Use a clean simulator.
        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(\(language))", "-AppleLocale", locale]
        app.launch()
        defer { app.terminate() }
        XCTAssertTrue(app.staticTexts["parlor-home-brand"].waitForExistence(timeout: 30))
        try tapSetupButton(app, prefix: copy.openGame)
        try tapSetupButton(app, prefix: copy.join)
        try tapSetupButton(app, prefix: copy.permission)
        try verifyKeyboardCycle(app, actionPrefix: copy.continueToCode, input: "Keyboard Test")
        try tapSetupButton(app, prefix: copy.continueToCode)
        try verifyKeyboardCycle(app, actionPrefix: copy.findRoom, input: "AAAAAA")
    }

    @MainActor
    private func setupButton(_ app: XCUIApplication, prefix: String) -> XCUIElement {
        app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", prefix)).firstMatch
    }

    @MainActor
    private func tapSetupButton(_ app: XCUIApplication, prefix: String) throws {
        let button = setupButton(app, prefix: prefix)
        for _ in 0..<8 {
            if button.waitForExistence(timeout: 1), button.isHittable {
                try tapSettledControl(button)
                return
            }
            app.swipeUp(velocity: .slow)
        }
        XCTFail("Public setup control is not reachable: " + prefix)
        throw NSError(domain: "KeyboardLayoutSetup", code: 1)
    }

    @MainActor
    private func tapPublicSetupTransition(_ app: XCUIApplication, from: String, to: String) throws {
        // Only the public Host/Join choices use this observer. Do not replay a
        // missed tap or swipe a different screen while searching its destination.
        try tapSetupButton(app, prefix: from)
        let source = setupButton(app, prefix: from)
        let destination = setupButton(app, prefix: to)
        let transitioned = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
            destination.exists && !source.exists
        }, object: nil)
        let result = XCTWaiter.wait(for: [transitioned], timeout: 10)
        if result != .completed {
            // Capture after the single tap, not before: evidence collection
            // must not lengthen the gesture or mask the failing input sequence.
            let screenshot = XCTAttachment(screenshot: app.screenshot())
            screenshot.name = "Failed public setup transition"
            screenshot.lifetime = .keepAlways
            add(screenshot)
            let sourceExists = source.exists
            let sourceEnabled = sourceExists ? source.isEnabled : false
            let sourceFrame = sourceExists ? source.frame : .zero
            print("Public setup transition: sourceExists=\(sourceExists), " +
                  "sourceEnabled=\(sourceEnabled), sourceFrame=\(sourceFrame), " +
                  "destinationExists=\(destination.exists), appState=\(app.state.rawValue)")
        }
        XCTAssertEqual(result, .completed,
                       "One public setup tap must remove the source and expose its destination")
    }

    @MainActor
    private func tapSettledControl(_ control: XCUIElement) throws {
        // One resolved public snapshot supplies frame and enabled together.
        // Separate XCUIElement property reads each re-evaluate the remote query;
        // an interrupted resolution can return false rather than native disabled
        // state (original failure41180128325). Missing/error samples still break
        // adjacency. Keep exact frames, native hittability, the ten-second
        // deadline and the single ordinary tap; never admit stale cached state.
        var frames = PublicControlFrames()
        var samples: [String] = []
        let started = ProcessInfo.processInfo.systemUptime
        let settled = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
            let sampleStarted = ProcessInfo.processInfo.systemUptime
            let sample = PublicControlSample(read: {
                let snapshot = try control.snapshot()
                return (snapshot.frame, snapshot.isEnabled)
            })
            let frame = sample.frame
            let enabled = sample.enabled
            let stable = frames.observe(frame: frame, enabled: enabled)
            let hittable = stable ? control.isHittable : nil
            if stable && hittable != true { frames.reset() }
            if samples.count < 12 {
                // Geometry/status/timing only: never retain labels, typed
                // values, arbitrary hierarchies or private player information.
                samples.append("at=\(sampleStarted - started), " +
                    "querySeconds=\(ProcessInfo.processInfo.systemUptime - sampleStarted), " +
                    "frame=\(String(describing: frame)), enabled=\(enabled), " +
                    "stable=\(stable), hittable=\(String(describing: hittable)), " +
                    "snapshotErrorCode=\(String(describing: sample.errorCode))")
            }
            return stable && hittable == true
        }, object: nil)
        let result = XCTWaiter.wait(for: [settled], timeout: 10)
        if result != .completed {
            print("Public control settlement: " + samples.joined(separator: "; "))
            let screenshot = XCTAttachment(screenshot: XCUIApplication().screenshot())
            screenshot.name = "Failed public control settlement before tap"
            screenshot.lifetime = .keepAlways
            add(screenshot)
        }
        XCTAssertEqual(result, .completed,
                       "Public setup control must stop scrolling before it is tapped")
        guard result == .completed else {
            throw NSError(domain: "PublicControlSettlement", code: 1)
        }
        control.tap()
    }

    @MainActor
    private func verifyKeyboardCycle(_ app: XCUIApplication, actionPrefix: String, input: String) throws {
        // Compose exposes its single-line editable semantics as a TextView.
        let field = app.textViews.firstMatch
        XCTAssertTrue(field.waitForExistence(timeout: 10))
        XCTAssertEqual(app.textViews.count, 1)
        let action = setupButton(app, prefix: actionPrefix)
        XCTAssertTrue(action.waitForExistence(timeout: 10))
        let restingBottom = action.frame.maxY

        // Reopening must not accumulate an extra inset; Done must restore the
        // original action position. Typing uses the real focused Compose field.
        for cycle in 0..<2 {
            field.tap()
            let keyboard = app.keyboards.firstMatch
            XCTAssertTrue(keyboard.waitForExistence(timeout: 10))
            let introduction = dismissKeyboardIntroduction(app)
            if cycle == 0 { field.typeText(input) }
            assertTextValue(field, in: app, equals: input, introduction: introduction)
            var frames = PublicKeyboardFrames()
            var sample: PublicKeyboardGeometry?
            var snapshotErrorCode: Int?
            let settled = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
                do {
                    sample = try self.readPublicKeyboardGeometry(app, actionPrefix: actionPrefix)
                    snapshotErrorCode = nil
                } catch {
                    sample = nil
                    snapshotErrorCode = (error as NSError).code
                }
                // Resolve geometry once per generation. No hit-point query for
                // missing, invalid, moving, obscured or excessively spaced input.
                return frames.observe(sample, hittable: { field.isHittable })
            }, object: nil)
            let result = XCTWaiter.wait(for: [settled], timeout: 10)
            let gap = sample?.gap ?? .nan
            print("Keyboard geometry: gap=\(gap), inputBottom=\(sample?.field.maxY ?? .nan), " +
                  "actionTop=\(sample?.action.minY ?? .nan), keyboardTop=\(sample?.keyboardTop ?? .nan), " +
                  "snapshotErrorCode=\(String(describing: snapshotErrorCode))")
            let screenshot = XCTAttachment(screenshot: app.screenshot())
            screenshot.name = "Public setup keyboard cycle \(cycle)"
            screenshot.lifetime = .keepAlways
            add(screenshot)
            XCTAssertEqual(result, .completed,
                           "Input must remain visible and the action must sit just above the keyboard")
            // The bar has 16pt visual padding. A second keyboard-sized space is
            // never acceptable (allow a small accessibility/rounding tolerance).
            XCTAssertGreaterThanOrEqual(gap, 0)
            XCTAssertLessThanOrEqual(gap, 32)

            field.typeText("\n")
            let restored = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
                !keyboard.exists && abs(action.frame.maxY - restingBottom) <= 2
            }, object: nil)
            XCTAssertEqual(XCTWaiter.wait(for: [restored], timeout: 10), .completed,
                           "Done must hide the keyboard and restore the un-inset layout")
        }
    }

    @MainActor
    func testArabicDisplayNameAndAsciiRoomCodeThroughNativeInput() throws {
        // Synthetic public input only; never submit a code or start transport.
        let copy = KeyboardCopy(
            openGame: "افتح من القاتل واختر طريقة اللعب.",
            join: "أدخل رمز المضيف للدخول إلى طاولته.",
            permission: "تابع وأعد تنفيذ عملية الشبكة المحلية الفعلية.",
            continueToCode: "احفظ اسمك وتابع لإدخال رمز الغرفة.",
            findRoom: "الاتصال بالمضيف باستخدام الرمز المُدخل."
        )
        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(ar)", "-AppleLocale", "ar_EG"]
        app.launch()
        defer { app.terminate() }
        XCTAssertTrue(app.staticTexts["parlor-home-brand"].waitForExistence(timeout: 30))
        try tapSetupButton(app, prefix: copy.openGame)
        try tapSetupButton(app, prefix: copy.join)
        try tapSetupButton(app, prefix: copy.permission)
        // Actual Arabic scalars go through typeText and the existing exact
        // value/geometry/Done/reopen assertions, not a semantics replacement.
        try verifyKeyboardCycle(app, actionPrefix: copy.continueToCode, input: "أحمد نور")
        try tapSetupButton(app, prefix: copy.continueToCode)
        try verifyKeyboardCycle(app, actionPrefix: copy.findRoom, input: "AAAAAA")
        XCTAssertFalse(app.alerts.firstMatch.exists, "Public input must not request network access")
        // The room-code action is deliberately never tapped.
    }

    @MainActor
    func testPostDoneNameAndRoomCodeEditsInEnglish() throws {
        try verifyPostDoneInputs(language: "en", locale: "en_US", copy: KeyboardCopy(
            openGame: "Open Whodunit and pick how you want to play.",
            join: "Enter the host's room code to join their table.",
            permission: "Continue and retry the real local-network operation.",
            continueToCode: "Save your name and continue to enter a room code.",
            findRoom: "Connect to the host using the entered room code."
        ))
    }

    @MainActor
    func testPostDoneNameAndRoomCodeEditsInArabic() throws {
        try verifyPostDoneInputs(language: "ar", locale: "ar_EG", copy: KeyboardCopy(
            openGame: "افتح من القاتل واختر طريقة اللعب.",
            join: "أدخل رمز المضيف للدخول إلى طاولته.",
            permission: "تابع وأعد تنفيذ عملية الشبكة المحلية الفعلية.",
            continueToCode: "احفظ اسمك وتابع لإدخال رمز الغرفة.",
            findRoom: "الاتصال بالمضيف باستخدام الرمز المُدخل."
        ))
    }

    @MainActor
    private func verifyPostDoneInputs(language: String, locale: String, copy: KeyboardCopy) throws {
        // New native edit coverage, not a replacement for the original typing
        // and geometry journeys. Never submit a code or start a LAN operation.
        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(\(language))", "-AppleLocale", locale]
        app.launch()
        defer { app.terminate() }
        XCTAssertTrue(app.staticTexts["parlor-home-brand"].waitForExistence(timeout: 30))
        try tapSetupButton(app, prefix: copy.openGame)
        try tapSetupButton(app, prefix: copy.join)
        try tapSetupButton(app, prefix: copy.permission)
        verifyPostDoneField(app, actionPrefix: copy.continueToCode, isCode: false)
        // Only this explicit Name Continue may advance to room-code entry.
        try tapSetupButton(app, prefix: copy.continueToCode)
        verifyPostDoneField(app, actionPrefix: copy.findRoom, isCode: true)
        XCTAssertFalse(app.alerts.firstMatch.exists, "Public edits must not request network access")
    }

    @MainActor
    private func verifyPostDoneField(_ app: XCUIApplication, actionPrefix: String, isCode: Bool) {
        let field = app.textViews.firstMatch
        let action = setupButton(app, prefix: actionPrefix)
        XCTAssertTrue(field.waitForExistence(timeout: 10))
        XCTAssertEqual(app.textViews.count, 1)
        XCTAssertTrue(action.waitForExistence(timeout: 10))
        XCTAssertFalse(action.isEnabled)
        let initial = String(repeating: "A", count: isCode ? 4 : 5)
        field.tap()
        XCTAssertTrue(app.keyboards.firstMatch.waitForExistence(timeout: 10))
        let introduction = dismissKeyboardIntroduction(app)
        field.typeText(initial)
        assertTextValue(field, in: app, equals: initial, introduction: introduction)
        XCTAssertEqual(action.isEnabled, !isCode)
        dismissPostDoneField(app, field: field, action: action, expected: initial)

        // A tap can move the caret. Identical characters make these exact
        // insert/delete expectations independent of its collapsed position;
        // no arrow strings, forced selection, fallback, or repair input.
        field.tap()
        XCTAssertTrue(app.keyboards.firstMatch.waitForExistence(timeout: 10))
        let reopenedIntroduction = dismissKeyboardIntroduction(app)
        field.typeText("AA")
        let inserted = initial + "AA"
        assertTextValue(field, in: app, equals: inserted, introduction: reopenedIntroduction)
        XCTAssertTrue(action.isEnabled)
        field.typeText(XCUIKeyboardKey.delete.rawValue)
        let deleted = String(inserted.dropLast())
        assertTextValue(field, in: app, equals: deleted, introduction: reopenedIntroduction)
        XCTAssertEqual(action.isEnabled, !isCode)
        var expectedFinal = deleted
        if isCode {
            field.typeText("A")
            expectedFinal += "A"
            assertTextValue(field, in: app, equals: expectedFinal, introduction: reopenedIntroduction)
            XCTAssertTrue(action.isEnabled)
        }
        dismissPostDoneField(app, field: field, action: action, expected: expectedFinal)
        // The room-code action is deliberately never tapped.
    }

    @MainActor
    private func dismissPostDoneField(
        _ app: XCUIApplication, field: XCUIElement, action: XCUIElement, expected: String
    ) {
        field.typeText("\n")
        let dismissed = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
            !app.keyboards.firstMatch.exists
        }, object: nil)
        XCTAssertEqual(XCTWaiter.wait(for: [dismissed], timeout: 10), .completed,
                       "Done must dismiss the keyboard without submitting or changing the draft")
        XCTAssertTrue(field.exists)
        XCTAssertTrue(action.exists, "Done must retain the current public input screen")
        assertTextValue(field, in: app, equals: expected)
    }

    @MainActor
    private func assertTextValue(
        _ field: XCUIElement,
        in app: XCUIApplication,
        equals expected: String,
        introduction: KeyboardIntroductionDismissal? = nil,
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        // UIKit's idle notification can precede Compose's accessibility
        // snapshot update. Wait for the exact value, never retype, shorten the
        // input, or advance Next while characters are still unobserved. Apple
        // may present its own tutorial after typing begins; handle that exact
        // public interruption within this SAME deadline, never replay input.
        var samples: [String] = []
        let started = ProcessInfo.processInfo.systemUptime
        let committed = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
            let sampleStarted = ProcessInfo.processInfo.systemUptime
            if let introduction {
                self.dismissKeyboardIntroduction(app, dismissal: introduction)
            }
            // Resolve existence and value together. Separate remote queries
            // can observe different generations and consume the same deadline.
            let snapshot: (any XCUIElementSnapshot)?
            var snapshotErrorCode: Int? = nil
            do {
                snapshot = try field.snapshot()
            } catch {
                snapshot = nil
                snapshotErrorCode = (error as NSError).code
            }
            let value = snapshot?.value as? String
            let exact = matchesPublicTextValue(value, expected: expected)
            if samples.count < 12 {
                // Status/timing only; never print the input, expected string,
                // arbitrary hierarchy, error description or player information.
                samples.append("at=\(sampleStarted - started), " +
                    "querySeconds=\(ProcessInfo.processInfo.systemUptime - sampleStarted), " +
                    "snapshotPresent=\(snapshot != nil), stringValue=\(value != nil), " +
                    "empty=\(value?.isEmpty == true), exact=\(exact), " +
                    "snapshotErrorCode=\(String(describing: snapshotErrorCode))")
            }
            return exact
        }, object: nil)
        let result = XCTWaiter.wait(for: [committed], timeout: 10)
        if result != .completed {
            print("Public text commit: " + samples.joined(separator: "; "))
            let screenshot = XCTAttachment(screenshot: app.screenshot())
            screenshot.name = "Failed public text commit"
            screenshot.lifetime = .keepAlways
            add(screenshot)
        }
        XCTAssertEqual(result, .completed,
                       "The full typed value must reach the actual Compose field", file: file, line: line)
        XCTAssertEqual(field.value as? String, expected, file: file, line: line)
    }

    @MainActor
    @discardableResult
    private func dismissKeyboardIntroduction(
        _ app: XCUIApplication,
        dismissal: KeyboardIntroductionDismissal = KeyboardIntroductionDismissal()
    ) -> KeyboardIntroductionDismissal {
        guard !dismissal.hasDismissed else { return dismissal }
        // The retained failing Arabic recording exposes this exact English
        // Apple tutorial. Never tap a generic Continue or a Parlor action.
        let tutorial = app.staticTexts[
            "Speed up your typing by sliding your finger across the letters to compose a word."
        ]
        guard tutorial.exists else { return dismissal }
        let button = app.buttons["Continue"]
        guard button.exists else { return dismissal }
        if dismissal.observe(tutorialExists: true, buttonFrame: button.frame,
                             applicationFrame: app.frame, hittable: button.isHittable) {
            button.tap()
        }
        return dismissal
    }

    @MainActor
    private func fullKeyboardTop(_ app: XCUIApplication, keyboard: XCUIElement) -> CGFloat {
        let keys = keyboard.frame
        let surrounding = app.otherElements.allElementsBoundByIndex.map { $0.frame }
        return PublicKeyboardSurface.top(keys: keys, surrounding: surrounding) ?? .nan
    }

    @MainActor
    private func readPublicKeyboardGeometry(
        _ app: XCUIApplication, actionPrefix: String
    ) throws -> PublicKeyboardGeometry? {
        // Only called on public Name/JoinCode setup, never private gameplay.
        // Snapshot objects/labels are not retained in the geometry state or logs.
        let root = try app.snapshot()
        var pending: [(any XCUIElementSnapshot, Int)] = [(root, 0)]
        var fieldFrames: [CGRect] = []
        var actionFrames: [CGRect] = []
        var keyFrames: [CGRect] = []
        var surrounding: [CGRect] = []
        var count = 0
        while let (node, depth) = pending.popLast() {
            count += 1
            guard count <= 2048, depth <= 32 else { return nil }
            switch node.elementType {
            case .textView: fieldFrames.append(node.frame)
            case .button:
                if node.label.hasPrefix(actionPrefix) { actionFrames.append(node.frame) }
            case .keyboard: keyFrames.append(node.frame)
            case .other: surrounding.append(node.frame)
            default: break
            }
            for child in node.children { pending.append((child, depth + 1)) }
        }
        guard fieldFrames.count == 1, actionFrames.count == 1, keyFrames.count == 1,
              let top = PublicKeyboardSurface.top(keys: keyFrames[0], surrounding: surrounding) else {
            return nil
        }
        return PublicKeyboardGeometry(field: fieldFrames[0], action: actionFrames[0],
                                      keys: keyFrames[0], keyboardTop: top)
    }
}

// Test-only exact comparison. Missing/non-string snapshots are not empty text,
// and partial/normalized input must never admit a subsequent action.
private func matchesPublicTextValue(_ value: Any?, expected: String) -> Bool {
    guard let value = value as? String else { return false }
    return value == expected
}

final class PublicTextValueTests: XCTestCase {
    func testOnlyTheCompleteExpectedStringMatches() {
        XCTAssertTrue(matchesPublicTextValue("Table Test", expected: "Table Test"))
        for value in ["", "Table", "Table Tes", "Table Test extra"] {
            XCTAssertFalse(matchesPublicTextValue(value, expected: "Table Test"))
        }
    }

    func testMissingOrNonStringValueNeverMatchesEvenAnEmptyExpectation() {
        let values: [Any?] = [nil, 0, false, ["Table Test"], NSNull()]
        for value in values {
            XCTAssertFalse(matchesPublicTextValue(value, expected: "Table Test"))
            XCTAssertFalse(matchesPublicTextValue(value, expected: ""))
        }
        XCTAssertTrue(matchesPublicTextValue("", expected: ""))
    }

    func testWhitespaceCaseAndArabicCharactersAreNotRewritten() {
        for value in [" Table Test", "Table Test ", "table test", "Table\nTest"] {
            XCTAssertFalse(matchesPublicTextValue(value, expected: "Table Test"))
        }
        XCTAssertTrue(matchesPublicTextValue("أحمد", expected: "أحمد"))
        XCTAssertFalse(matchesPublicTextValue("احمد", expected: "أحمد"))
        XCTAssertFalse(matchesPublicTextValue("أحمد ", expected: "أحمد"))
    }
}

// Test-only frame state. Losing the control, enabled state or hittability must
// break adjacency; a matching frame before an interruption cannot admit a tap.
// A failed snapshot is unknown, never an enabled observation. Do not retain
// XCUIElementSnapshot, labels, values, hierarchies or error descriptions here.
private struct PublicControlSample {
    let frame: CGRect?
    let enabled: Bool
    let errorCode: Int?

    init(read: () throws -> (frame: CGRect, enabled: Bool)) {
        do {
            let resolved = try read()
            frame = resolved.frame
            enabled = resolved.enabled
            errorCode = nil
        } catch {
            frame = nil
            enabled = false
            errorCode = (error as NSError).code
        }
    }
}

private struct PublicControlFrames {
    private var previous: CGRect?

    mutating func observe(frame: CGRect?, enabled: Bool) -> Bool {
        // CGRect.infinite is a semantic sentinel with finite scalar values.
        // Also inspect raw dimensions: CGRect accessors standardize negative sizes.
        guard enabled, let frame, !frame.isNull, !frame.isInfinite, !frame.isEmpty,
              [frame.origin.x, frame.origin.y, frame.size.width, frame.size.height,
               frame.maxX, frame.maxY].allSatisfy({ $0.isFinite }),
              frame.size.width > 0, frame.size.height > 0 else {
            reset()
            return false
        }
        defer { previous = frame }
        return previous == frame
    }

    mutating func reset() { previous = nil }
}

final class PublicControlFramesTests: XCTestCase {
    private let resting = CGRect(x: 24, y: 279, width: 354, height: 101)

    func testSampleResolvesFrameAndEnabledOnceFromTheSameGeneration() {
        var reads = 0
        let sample = PublicControlSample(read: {
            reads += 1
            return (self.resting, reads == 1)
        })
        XCTAssertEqual(reads, 1)
        XCTAssertEqual(sample.frame, resting)
        XCTAssertTrue(sample.enabled)
        XCTAssertNil(sample.errorCode)
    }

    func testSnapshotErrorBreaksAdjacencyAndCannotReuseLastEnabledFrame() {
        var frames = PublicControlFrames()
        XCTAssertFalse(frames.observe(frame: resting, enabled: true))
        var reads = 0
        let sample = PublicControlSample(read: {
            reads += 1
            throw NSError(domain: "PublicControlSampleTest", code: 1001)
        })
        XCTAssertEqual(reads, 1)
        XCTAssertNil(sample.frame)
        XCTAssertFalse(sample.enabled)
        XCTAssertEqual(sample.errorCode, 1001)
        XCTAssertFalse(frames.observe(frame: sample.frame, enabled: sample.enabled))
        XCTAssertFalse(frames.observe(frame: resting, enabled: true))
        XCTAssertTrue(frames.observe(frame: resting, enabled: true))
    }

    func testDisabledSnapshotIsNotConfusedWithResolutionFailure() {
        let sample = PublicControlSample(read: { (self.resting, false) })
        XCTAssertEqual(sample.frame, resting)
        XCTAssertFalse(sample.enabled)
        XCTAssertNil(sample.errorCode)
    }

    func testFirstFrameNeverSettlesButTheExactAdjacentFrameDoes() {
        var frames = PublicControlFrames()
        XCTAssertFalse(frames.observe(frame: resting, enabled: true))
        XCTAssertTrue(frames.observe(frame: resting, enabled: true))
    }

    func testMotionIsNotRoundedIntoASettledFrame() {
        var frames = PublicControlFrames()
        let moved = resting.offsetBy(dx: 0, dy: 0.001)
        XCTAssertFalse(frames.observe(frame: resting, enabled: true))
        XCTAssertFalse(frames.observe(frame: moved, enabled: true))
        XCTAssertTrue(frames.observe(frame: moved, enabled: true))
    }

    func testMissingControlBreaksFrameAdjacency() {
        var frames = PublicControlFrames()
        XCTAssertFalse(frames.observe(frame: resting, enabled: true))
        for _ in 0..<4 {
            XCTAssertFalse(frames.observe(frame: nil, enabled: false))
        }
        XCTAssertFalse(frames.observe(frame: resting, enabled: true))
        XCTAssertTrue(frames.observe(frame: resting, enabled: true))
    }

    func testDisabledControlBreaksFrameAdjacency() {
        var frames = PublicControlFrames()
        XCTAssertFalse(frames.observe(frame: resting, enabled: true))
        for _ in 0..<4 {
            XCTAssertFalse(frames.observe(frame: resting, enabled: false))
        }
        XCTAssertFalse(frames.observe(frame: resting, enabled: true))
        XCTAssertTrue(frames.observe(frame: resting, enabled: true))
    }

    func testInvalidOrEmptyGeometryCannotSettleAndBreaksAdjacency() {
        // Numeric finiteness is not enough to reject CoreGraphics' infinite sentinel.
        XCTAssertTrue(CGRect.infinite.isInfinite)
        XCTAssertTrue([CGRect.infinite.origin.x, CGRect.infinite.origin.y,
                       CGRect.infinite.size.width, CGRect.infinite.size.height].allSatisfy({ $0.isFinite }))
        let invalidFrames: [(String, CGRect)] = [
            ("zero", .zero), ("null", .null), ("infinite sentinel", .infinite),
            ("NaN x", CGRect(x: CGFloat.nan, y: 279, width: 354, height: 101)),
            ("NaN y", CGRect(x: 24, y: CGFloat.nan, width: 354, height: 101)),
            ("infinite width", CGRect(x: 24, y: 279, width: CGFloat.infinity, height: 101)),
            ("infinite height", CGRect(x: 24, y: 279, width: 354, height: CGFloat.infinity)),
            ("negative width", CGRect(x: 24, y: 279, width: -354, height: 101)),
            ("negative height", CGRect(x: 24, y: 279, width: 354, height: -101)),
            ("overflowing extent", CGRect(x: CGFloat.greatestFiniteMagnitude, y: 279,
                                          width: CGFloat.greatestFiniteMagnitude, height: 101)),
        ]
        for (kind, invalid) in invalidFrames {
            var frames = PublicControlFrames()
            XCTAssertFalse(frames.observe(frame: resting, enabled: true))
            XCTAssertFalse(frames.observe(frame: invalid, enabled: true), kind)
            XCTAssertFalse(frames.observe(frame: invalid, enabled: true), kind)
            XCTAssertFalse(frames.observe(frame: resting, enabled: true), "Adjacency after \(kind)")
            XCTAssertTrue(frames.observe(frame: resting, enabled: true), "Recovery after \(kind)")
        }
    }

    func testHittabilityInterruptionRequiresTwoFreshFrames() {
        var frames = PublicControlFrames()
        XCTAssertFalse(frames.observe(frame: resting, enabled: true))
        XCTAssertTrue(frames.observe(frame: resting, enabled: true))
        frames.reset()
        XCTAssertFalse(frames.observe(frame: resting, enabled: true))
        XCTAssertTrue(frames.observe(frame: resting, enabled: true))
    }
}

// Test-only, per-keyboard-cycle budget for the identified Apple introduction.
// Absence during the first check must not consume the later dismissal; once
// admitted, no second tap is allowed even if UIKit briefly retains the modal.
private final class KeyboardIntroductionDismissal {
    private(set) var hasDismissed = false

    func observe(tutorialExists: Bool, buttonFrame: CGRect?, applicationFrame: CGRect,
                 hittable: Bool) -> Bool {
        guard !hasDismissed, tutorialExists, hittable, let buttonFrame,
              !applicationFrame.isNull, !applicationFrame.isInfinite, !applicationFrame.isEmpty,
              !buttonFrame.isNull, !buttonFrame.isInfinite, !buttonFrame.isEmpty,
              [applicationFrame.minX, applicationFrame.minY, applicationFrame.maxX, applicationFrame.maxY,
               buttonFrame.minX, buttonFrame.minY, buttonFrame.maxX, buttonFrame.maxY]
                .allSatisfy({ $0.isFinite }),
              applicationFrame.size.width > 0, applicationFrame.size.height > 0,
              buttonFrame.size.width > 0, buttonFrame.size.height > 0,
              applicationFrame.contains(buttonFrame), buttonFrame.minY >= applicationFrame.midY else {
            return false
        }
        hasDismissed = true
        return true
    }
}

final class KeyboardIntroductionDismissalTests: XCTestCase {
    private let application = CGRect(x: 0, y: 0, width: 402, height: 874)
    private let introductionButton = CGRect(x: 140, y: 800, width: 122, height: 44)

    func testLateIntroductionAfterInitialAbsenceCanBeDismissedExactlyOnce() {
        let dismissal = KeyboardIntroductionDismissal()
        XCTAssertFalse(dismissal.observe(tutorialExists: false, buttonFrame: nil,
                                         applicationFrame: application, hittable: false))
        XCTAssertFalse(dismissal.hasDismissed)
        XCTAssertTrue(dismissal.observe(tutorialExists: true, buttonFrame: introductionButton,
                                        applicationFrame: application, hittable: true))
        XCTAssertTrue(dismissal.hasDismissed)
        XCTAssertFalse(dismissal.observe(tutorialExists: true, buttonFrame: introductionButton,
                                         applicationFrame: application, hittable: true))
    }

    func testGenericContinueWithoutIdentifiedTutorialNeverAdmitsATap() {
        let dismissal = KeyboardIntroductionDismissal()
        XCTAssertFalse(dismissal.observe(tutorialExists: false, buttonFrame: introductionButton,
                                         applicationFrame: application, hittable: true))
        XCTAssertFalse(dismissal.hasDismissed)
    }

    func testNonhittableIntroductionDoesNotConsumeTheSingleDismissal() {
        let dismissal = KeyboardIntroductionDismissal()
        XCTAssertFalse(dismissal.observe(tutorialExists: true, buttonFrame: introductionButton,
                                         applicationFrame: application, hittable: false))
        XCTAssertTrue(dismissal.observe(tutorialExists: true, buttonFrame: introductionButton,
                                        applicationFrame: application, hittable: true))
    }

    func testMissingInvalidOffscreenOrUpperScreenButtonCannotBeDismissed() {
        let frames: [CGRect?] = [nil, .zero, .null, .infinite,
            CGRect(x: CGFloat.nan, y: 800, width: 122, height: 44),
            CGRect(x: 140, y: 800, width: -122, height: 44),
            CGRect(x: 140, y: 850, width: 122, height: 44),
            CGRect(x: 140, y: 200, width: 122, height: 44)]
        for frame in frames {
            let dismissal = KeyboardIntroductionDismissal()
            XCTAssertFalse(dismissal.observe(tutorialExists: true, buttonFrame: frame,
                                             applicationFrame: application, hittable: true))
            XCTAssertFalse(dismissal.hasDismissed)
        }
    }
}

// Geometry-only state for public setup. A transient AX sentinel must never
// reach XCTest's hit-point calculation; unknown/moving samples cannot settle.
private struct PublicKeyboardGeometry: Equatable {
    let field: CGRect
    let action: CGRect
    let keys: CGRect
    let keyboardTop: CGFloat

    var gap: CGFloat { keyboardTop - action.maxY }

    var isValid: Bool {
        let frames = [field, action, keys]
        guard frames.allSatisfy({ frame in
            !frame.isNull && !frame.isInfinite && !frame.isEmpty
                && frame.size.width > 0 && frame.size.height > 0
                && [frame.origin.x, frame.origin.y, frame.size.width, frame.size.height,
                    frame.maxX, frame.maxY].allSatisfy({ $0.isFinite })
        }) else { return false }
        return keys.height > 100 && keyboardTop.isFinite
            && keyboardTop >= 0 && keyboardTop <= keys.minY
            && field.minY >= 0 && field.maxY <= action.minY
            && gap >= 0 && gap <= 32
    }
}

private struct PublicKeyboardFrames {
    private var previous: PublicKeyboardGeometry?

    mutating func observe(_ sample: PublicKeyboardGeometry?, hittable: () -> Bool) -> Bool {
        guard let sample, sample.isValid else {
            previous = nil
            return false
        }
        let stable = previous == sample
        previous = sample
        guard stable else { return false }
        guard hittable() else {
            previous = nil
            return false
        }
        return true
    }
}

final class PublicKeyboardGeometryTests: XCTestCase {
    private func sample(field: CGRect = CGRect(x: 24, y: 270, width: 354, height: 44),
                        action: CGRect = CGRect(x: 24, y: 498, width: 354, height: 52),
                        keys: CGRect = CGRect(x: 0, y: 583, width: 402, height: 233),
                        top: CGFloat = 566) -> PublicKeyboardGeometry {
        PublicKeyboardGeometry(field: field, action: action, keys: keys, keyboardTop: top)
    }

    func testHittabilityIsQueriedOnlyAfterTwoExactValidSamples() {
        var frames = PublicKeyboardFrames()
        var reads = 0
        let hit = { reads += 1; return true }
        XCTAssertFalse(frames.observe(sample(), hittable: hit))
        XCTAssertEqual(reads, 0)
        XCTAssertTrue(frames.observe(sample(), hittable: hit))
        XCTAssertEqual(reads, 1)
    }

    func testInfiniteEmptyAndNegativeFramesNeverRequestAHitPoint() {
        let invalid: [CGRect] = [.zero, .null, .infinite,
            CGRect(x: CGFloat.infinity, y: CGFloat.infinity, width: 0, height: 0),
            CGRect(x: CGFloat.nan, y: 270, width: 354, height: 44),
            CGRect(x: 24, y: 270, width: -354, height: 44)]
        for field in invalid {
            var frames = PublicKeyboardFrames()
            for _ in 0..<2 {
                XCTAssertFalse(frames.observe(sample(field: field), hittable: {
                    XCTFail("Invalid geometry must not query native hittability")
                    return true
                }))
            }
        }
    }

    func testMissingOrInvalidSampleBreaksAdjacency() {
        for invalid in [nil, sample(field: .null)] {
            var frames = PublicKeyboardFrames()
            XCTAssertFalse(frames.observe(sample(), hittable: { true }))
            XCTAssertFalse(frames.observe(invalid, hittable: { true }))
            XCTAssertFalse(frames.observe(sample(), hittable: { true }))
            XCTAssertTrue(frames.observe(sample(), hittable: { true }))
        }
    }

    func testThirtyThreePointAndKeyboardSizedGapsRemainFailures() {
        XCTAssertEqual(sample(top: 583).gap, 33)
        XCTAssertFalse(sample(top: 583).isValid)
        XCTAssertFalse(sample(action: CGRect(x: 24, y: 198, width: 354, height: 52)).isValid)
        XCTAssertFalse(sample(top: 549).isValid)
        XCTAssertTrue(sample(top: 550).isValid)
        XCTAssertTrue(sample(top: 582).isValid)
    }

    func testFieldOutsideTheVisibleAreaOrBelowTheActionCannotSettle() {
        XCTAssertFalse(sample(field: CGRect(x: 24, y: -1, width: 354, height: 44)).isValid)
        XCTAssertFalse(sample(field: CGRect(x: 24, y: 480, width: 354, height: 44)).isValid)
        XCTAssertFalse(sample(keys: CGRect(x: 0, y: 583, width: 402, height: 100)).isValid)
        XCTAssertFalse(sample(top: CGFloat.nan).isValid)
        XCTAssertFalse(sample(top: CGFloat.infinity).isValid)
    }

    func testSubpointFieldActionAndKeyboardMotionDoesNotSettle() {
        for moved in [sample(field: sample().field.offsetBy(dx: 0, dy: 0.001)),
                      sample(action: sample().action.offsetBy(dx: 0, dy: 0.001)),
                      sample(keys: sample().keys.offsetBy(dx: 0, dy: 0.001)),
                      sample(top: 566.001)] {
            var frames = PublicKeyboardFrames()
            XCTAssertFalse(frames.observe(sample(), hittable: { true }))
            XCTAssertFalse(frames.observe(moved, hittable: { true }))
            XCTAssertTrue(frames.observe(moved, hittable: { true }))
        }
    }

    func testNonhittableFieldRequiresTwoFreshSamples() {
        var frames = PublicKeyboardFrames()
        XCTAssertFalse(frames.observe(sample(), hittable: { true }))
        XCTAssertFalse(frames.observe(sample(), hittable: { false }))
        XCTAssertFalse(frames.observe(sample(), hittable: { true }))
        XCTAssertTrue(frames.observe(sample(), hittable: { true }))
    }
}

// AX's Keyboard describes keys, not necessarily the native keyboard surface.
// iOS26.2 exposes full-width background siblings above an ASCII key plane;
// prediction rows may instead touch its top. Never use a device-specific offset.
private enum PublicKeyboardSurface {
    static func top(keys: CGRect, surrounding: [CGRect]) -> CGFloat? {
        guard usable(keys) else { return nil }
        let tops = surrounding.filter { frame in
            guard usable(frame), abs(frame.minX - keys.minX) <= 1,
                  abs(frame.width - keys.width) <= 1 else { return false }
            let predictionRow = abs(frame.maxY - keys.minY) <= 1
                && frame.height < keys.height / 2
            // Bound to keyboard-sized surfaces, not the containing app/window.
            let enclosingSurface = frame.contains(keys) && frame.minY < keys.minY
                && frame.height < keys.height * 2
            return predictionRow || enclosingSurface
        }.map { $0.minY }
        return tops.min() ?? keys.minY
    }

    private static func usable(_ frame: CGRect) -> Bool {
        !frame.isNull && !frame.isInfinite && !frame.isEmpty
            && frame.size.width > 0 && frame.size.height > 0
            && [frame.origin.x, frame.origin.y, frame.size.width, frame.size.height,
                frame.maxX, frame.maxY].allSatisfy({ $0.isFinite })
    }
}

final class PublicKeyboardSurfaceTests: XCTestCase {
    private let keys = CGRect(x: 0, y: 583, width: 402, height: 233)

    func testActualIos26AsciiBackgroundIsIncludedWithoutAnOffset() {
        let backgrounds = [CGRect(x: 0, y: 566, width: 402, height: 308),
                           CGRect(x: 0, y: 583, width: 402, height: 291)]
        XCTAssertEqual(PublicKeyboardSurface.top(keys: keys, surrounding: backgrounds), 566)
        XCTAssertEqual(PublicKeyboardSurface.top(keys: keys, surrounding: backgrounds)! - 550, 16)
    }

    func testPredictionRowAndBackgroundUseTheFullVisibleSurface() {
        let backgrounds = [CGRect(x: 0, y: 566, width: 402, height: 308),
                           CGRect(x: 0, y: 539, width: 402, height: 44)]
        XCTAssertEqual(PublicKeyboardSurface.top(keys: keys, surrounding: backgrounds), 539)
    }

    func testWholeWindowAndUnrelatedRowsCannotHideExcessiveSpacing() {
        let unrelated = [CGRect(x: 0, y: 0, width: 402, height: 874),
                         CGRect(x: 0, y: 442, width: 402, height: 48),
                         CGRect(x: 24, y: 566, width: 354, height: 308)]
        XCTAssertEqual(PublicKeyboardSurface.top(keys: keys, surrounding: unrelated), 583)
    }

    func testInvalidFramesDoNotManufactureAKeyboardBoundary() {
        XCTAssertNil(PublicKeyboardSurface.top(keys: .null, surrounding: []))
        XCTAssertNil(PublicKeyboardSurface.top(keys: .infinite, surrounding: []))
        XCTAssertNil(PublicKeyboardSurface.top(keys: .zero, surrounding: []))
        XCTAssertEqual(PublicKeyboardSurface.top(keys: keys, surrounding: [.null, .infinite, .zero]), 583)
    }
}
