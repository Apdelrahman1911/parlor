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
            dismissKeyboardIntroduction(app, keyboard: keyboard)
            name.typeText("Table Test")
            assertTextValue(name, equals: "Table Test")
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
        dismissKeyboardIntroduction(app, keyboard: app.keyboards.firstMatch)
        for index in 1...6 {
            let field = app.textViews["Player \(index)"]
            XCTAssertTrue(field.waitForExistence(timeout: 10))
            field.typeText("Keyboard Player \(index)")
            assertTextValue(field, equals: "Keyboard Player \(index)")
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
            assertTextValue(app.textViews["Player \(index)"], equals: "Keyboard Player \(index)")
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
        // Public setup only. One coherent snapshot per frame sample avoids
        // separate exists/hittable/frame queries exhausting the same deadline.
        // Preserve exact adjacent-frame equality and the native hittability
        // check; do not lengthen, replay, or replace the actual tap.
        var frames = PublicControlFrames()
        var samples: [String] = []
        let started = ProcessInfo.processInfo.systemUptime
        let settled = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
            let sampleStarted = ProcessInfo.processInfo.systemUptime
            let snapshot = try? control.snapshot()
            let frame = snapshot?.frame
            let enabled = snapshot?.isEnabled == true
            let stable = frames.observe(frame: frame, enabled: enabled)
            let hittable = stable ? control.isHittable : nil
            if stable && hittable != true { frames.reset() }
            if samples.count < 12 {
                // Geometry/status/timing only: never retain labels, typed
                // values, arbitrary hierarchies or private player information.
                samples.append("at=\(sampleStarted - started), " +
                    "querySeconds=\(ProcessInfo.processInfo.systemUptime - sampleStarted), " +
                    "frame=\(String(describing: frame)), enabled=\(enabled), " +
                    "stable=\(stable), hittable=\(String(describing: hittable))")
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
            dismissKeyboardIntroduction(app, keyboard: keyboard)
            if cycle == 0 { field.typeText(input) }
            assertTextValue(field, equals: input)
            let keyboardTop = fullKeyboardTop(app, keyboard: keyboard)
            let settled = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
                let gap = keyboardTop - action.frame.maxY
                return keyboard.exists && keyboard.frame.height > 100 && field.isHittable
                    && field.frame.minY >= 0
                    && field.frame.maxY <= action.frame.minY && gap >= 0 && gap <= 32
            }, object: nil)
            let result = XCTWaiter.wait(for: [settled], timeout: 10)
            let gap = keyboardTop - action.frame.maxY
            print("Keyboard geometry: gap=\(gap), inputBottom=\(field.frame.maxY), " +
                  "actionTop=\(action.frame.minY), keyboardTop=\(keyboardTop)")
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
    private func assertTextValue(
        _ field: XCUIElement,
        equals expected: String,
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        // UIKit's idle notification can precede Compose's accessibility
        // snapshot update. Wait for the exact value, never retype, shorten the
        // input, or advance Next while characters are still unobserved.
        let committed = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
            field.exists && (field.value as? String) == expected
        }, object: nil)
        XCTAssertEqual(XCTWaiter.wait(for: [committed], timeout: 10), .completed,
                       "The full typed value must reach the actual Compose field", file: file, line: line)
        XCTAssertEqual(field.value as? String, expected, file: file, line: line)
    }

    @MainActor
    private func dismissKeyboardIntroduction(_ app: XCUIApplication, keyboard: XCUIElement) {
        // A fresh simulator may show Apple's QuickPath introduction over
        // the keys. Dismiss only its native, below-keyboard Continue button.
        for label in ["Continue", "متابعة"] {
            let introduction = app.buttons[label]
            if introduction.exists, introduction.frame.minY >= keyboard.frame.minY {
                introduction.tap()
            }
        }
    }

    @MainActor
    private func fullKeyboardTop(_ app: XCUIApplication, keyboard: XCUIElement) -> CGFloat {
        let keys = keyboard.frame
        // XCUI's Keyboard frame excludes the native prediction row. Include
        // its full-width sibling touching the keys, using geometry rather than
        // reading prediction text or relying on an English accessibility label.
        let frames: [CGRect] = app.otherElements.allElementsBoundByIndex.map { $0.frame }
        let adjacentRows: [CGRect] = frames.filter { frame in
            let aligned = abs(frame.minX - keys.minX) <= 1 && abs(frame.width - keys.width) <= 1
            let touching = abs(frame.maxY - keys.minY) <= 1
            let accessoryHeight = frame.height > 0 && frame.height < keys.height / 2
            return aligned && touching && accessoryHeight
        }
        return adjacentRows.map { $0.minY }.min() ?? keys.minY
    }
}

// Test-only frame state. Losing a snapshot, enabled state or hittability must
// break adjacency; a matching frame before an interruption cannot admit a tap.
private struct PublicControlFrames {
    private var previous: CGRect?

    mutating func observe(frame: CGRect?, enabled: Bool) -> Bool {
        guard enabled, let frame, !frame.isEmpty,
              [frame.minX, frame.minY, frame.width, frame.height].allSatisfy({ $0.isFinite }) else {
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

    func testMissingSnapshotBreaksFrameAdjacency() {
        var frames = PublicControlFrames()
        XCTAssertFalse(frames.observe(frame: resting, enabled: true))
        XCTAssertFalse(frames.observe(frame: nil, enabled: true))
        XCTAssertFalse(frames.observe(frame: resting, enabled: true))
        XCTAssertTrue(frames.observe(frame: resting, enabled: true))
    }

    func testDisabledSnapshotBreaksFrameAdjacency() {
        var frames = PublicControlFrames()
        XCTAssertFalse(frames.observe(frame: resting, enabled: true))
        XCTAssertFalse(frames.observe(frame: resting, enabled: false))
        XCTAssertFalse(frames.observe(frame: resting, enabled: true))
        XCTAssertTrue(frames.observe(frame: resting, enabled: true))
    }

    func testInvalidOrEmptyGeometryCannotSettleAndBreaksAdjacency() {
        for invalid in [CGRect.zero, CGRect.null, CGRect.infinite,
                        CGRect(x: CGFloat.nan, y: 279, width: 354, height: 101),
                        CGRect(x: 24, y: 279, width: CGFloat.infinity, height: 101)] {
            var frames = PublicControlFrames()
            XCTAssertFalse(frames.observe(frame: resting, enabled: true))
            XCTAssertFalse(frames.observe(frame: invalid, enabled: true))
            XCTAssertFalse(frames.observe(frame: invalid, enabled: true))
            XCTAssertFalse(frames.observe(frame: resting, enabled: true))
            XCTAssertTrue(frames.observe(frame: resting, enabled: true))
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
