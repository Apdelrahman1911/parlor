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
}
