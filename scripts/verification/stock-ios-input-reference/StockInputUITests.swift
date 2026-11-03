import XCTest
import CoreGraphics

final class StockInputUITests: XCTestCase {
    @MainActor func test01FirstArabic() throws { try verify(language: "ar", locale: "ar_EG", reopened: false) }
    @MainActor func test02FirstEnglish() throws { try verify(language: "en", locale: "en_US", reopened: false) }
    @MainActor func test03PostDoneArabic() throws { try verify(language: "ar", locale: "ar_EG", reopened: true) }
    @MainActor func test04PostDoneEnglish() throws { try verify(language: "en", locale: "en_US", reopened: true) }

    @MainActor private func verify(language: String, locale: String, reopened: Bool) throws {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(\(language))", "-AppleLocale", locale]
        app.launch()
        defer { app.terminate() }
        let field = app.textViews.firstMatch
        XCTAssertTrue(field.waitForExistence(timeout: 10))
        XCTAssertEqual(app.textViews.count, 1)
        var introduction = try open(field, app: app)
        if reopened {
            // Done/reopen an unchanged blank view: no clearing, deletion or caret manipulation.
            dismissKeyboardIntroduction(app, dismissal: introduction)
            done(field, app: app)
            introduction = try open(field, app: app)
        }
        field.typeText("Table Test")
        assertTextValue(field, in: app, equals: "Table Test", introduction: introduction)
        keyboardScreenshot(app, name: "\(language) \(reopened ? "post-Done" : "first") actual keyboard")
        done(field, app: app)
        let retainedIntroduction = try open(field, app: app)
        assertTextValue(field, in: app, equals: "Table Test", introduction: retainedIntroduction)
        keyboardScreenshot(app, name: "\(language) preserved draft on reopen")
        done(field, app: app)
    }
    @MainActor private func open(_ field: XCUIElement, app: XCUIApplication) throws -> KeyboardIntroductionDismissal {
        try tapSettledControl(field)
        XCTAssertTrue(app.keyboards.firstMatch.waitForExistence(timeout: 10))
        return dismissKeyboardIntroduction(app)
    }
    @MainActor private func done(_ field: XCUIElement, app: XCUIApplication) {
        field.typeText("\n")
        let restored = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
            !app.keyboards.firstMatch.exists
        }, object: nil)
        XCTAssertEqual(XCTWaiter.wait(for: [restored], timeout: 10), .completed,
                       "Done must dismiss the keyboard without changing the draft")
    }
    @MainActor private func keyboardScreenshot(_ app: XCUIApplication, name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
    @MainActor private func requestNativeState(_ app: XCUIApplication) {
        // Once-only app-side guard; only after the original text waiter already failed.
        let request = app.buttons["stock-failure-request"]
        guard request.exists, request.isHittable else { print("Stock native state: unavailable"); return }
        request.tap()
        print("Stock native state: " + app.staticTexts["stock-native-state"].label)
    }

    @MainActor
    private func tapSettledControl(_ control: XCUIElement) throws {
        // Public setup only. Full snapshot() also faults in value, placeholder,
        // identifier and window attributes that frame settlement never uses.
        // Read only existence, one complete CGRect and enabled state. These
        // are sequential observations, not an atomic accessibility snapshot;
        // absence/disabled state breaks adjacency and native hittability is
        // still required immediately before admitting the single ordinary tap.
        // Keep the same exact-frame comparison and original ten-second budget.
        var frames = PublicControlFrames()
        var samples: [String] = []
        let started = ProcessInfo.processInfo.systemUptime
        let settled = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
            let sampleStarted = ProcessInfo.processInfo.systemUptime
            let exists = control.exists
            let frame = exists ? control.frame : nil
            let enabled = exists && control.isEnabled
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
            requestNativeState(app)
            print("Public text commit: " + samples.joined(separator: "; "))
            let screenshot = XCTAttachment(screenshot: app.screenshot())
            screenshot.name = "Failed public text commit"
            screenshot.lifetime = .keepAlways
            add(screenshot)
        }
        XCTAssertEqual(result, .completed,
                       "The full typed value must reach the stock UIKit field", file: file, line: line)
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
}

private func matchesPublicTextValue(_ value: Any?, expected: String) -> Bool {
    guard let value = value as? String else { return false }
    return value == expected
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
