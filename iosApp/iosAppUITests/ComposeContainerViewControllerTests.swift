import UIKit
import XCTest

/// The test target compiles the same production container source, not a replica.
/// These UIKit checks do not substitute for actual SwiftUI/Compose app-host tests.
final class ComposeContainerViewControllerTests: XCTestCase {
    @MainActor
    func testOneStableChildKeepsItsExplicitSemanticsIndependentOfTheOuterView() {
        XCTAssertTrue(Thread.isMainThread)
        let child = UIViewController()
        let container = ComposeContainerViewController(contentController: child)
        container.loadViewIfNeeded()
        let childView = child.view!
        let directions: [UISemanticContentAttribute] = [.forceLeftToRight, .forceRightToLeft]
        for direction in directions + [.forceLeftToRight] {
            childView.semanticContentAttribute = direction
            for hostDirection in directions {
                container.view.semanticContentAttribute = hostDirection
                container.loadViewIfNeeded()
                XCTAssertEqual(childView.semanticContentAttribute, direction)
                XCTAssertTrue(child.view === childView)
                XCTAssertEqual(container.children.count, 1)
                XCTAssertTrue(container.children.first === child)
                XCTAssertTrue(child.parent === container)
                XCTAssertTrue(childView.superview === container.view)
            }
        }
    }

    @MainActor
    func testChildUsesFullBoundsAcrossSizesAndOpposingSemanticDirections() {
        XCTAssertTrue(Thread.isMainThread)
        let child = UIViewController()
        let container = ComposeContainerViewController(contentController: child)
        container.loadViewIfNeeded()
        let directions: [UISemanticContentAttribute] = [.forceLeftToRight, .forceRightToLeft]
        let sizes = [CGSize(width: 320, height: 480), CGSize(width: 640, height: 320),
                     CGSize(width: 1024, height: 768)]
        for size in sizes {
            for outerDirection in directions {
                for childDirection in directions {
                    container.view.semanticContentAttribute = outerDirection
                    child.view.semanticContentAttribute = childDirection
                    container.view.frame = CGRect(origin: .zero, size: size)
                    container.view.setNeedsLayout()
                    container.view.layoutIfNeeded()
                    XCTAssertEqual(child.view.frame.origin.x, 0, accuracy: 0.01)
                    XCTAssertEqual(child.view.frame.origin.y, 0, accuracy: 0.01)
                    XCTAssertEqual(child.view.frame.width, size.width, accuracy: 0.01)
                    XCTAssertEqual(child.view.frame.height, size.height, accuracy: 0.01)
                }
            }
        }
    }

    @MainActor
    func testSystemDecorationAndOrientationPoliciesStillBelongToTheChild() {
        XCTAssertTrue(Thread.isMainThread)
        let child = RecordingChild()
        let container = ComposeContainerViewController(contentController: child)
        container.loadViewIfNeeded()
        XCTAssertTrue(container.childForStatusBarStyle === child)
        XCTAssertTrue(container.childForStatusBarHidden === child)
        XCTAssertTrue(container.childForHomeIndicatorAutoHidden === child)
        XCTAssertTrue(container.childForScreenEdgesDeferringSystemGestures === child)
        XCTAssertEqual(container.supportedInterfaceOrientations, .landscape)
        XCTAssertEqual(container.preferredInterfaceOrientationForPresentation, .landscapeRight)
    }

    @MainActor
    func testDefaultAppearanceForwardingReachesTheSameChildExactlyOnce() {
        XCTAssertTrue(Thread.isMainThread)
        let child = RecordingChild()
        let container = ComposeContainerViewController(contentController: child)
        container.loadViewIfNeeded()
        container.beginAppearanceTransition(true, animated: false)
        container.endAppearanceTransition()
        XCTAssertEqual(child.appearances, 1)
        XCTAssertEqual(child.disappearances, 0)
        container.beginAppearanceTransition(false, animated: false)
        container.endAppearanceTransition()
        XCTAssertEqual(child.appearances, 1)
        XCTAssertEqual(child.disappearances, 1)
        XCTAssertTrue(container.children.first === child)
    }

    private final class RecordingChild: UIViewController {
        var appearances = 0
        var disappearances = 0

        override func viewDidAppear(_ animated: Bool) {
            super.viewDidAppear(animated)
            appearances += 1
        }

        override func viewDidDisappear(_ animated: Bool) {
            super.viewDidDisappear(animated)
            disappearances += 1
        }

        override var supportedInterfaceOrientations: UIInterfaceOrientationMask { .landscape }
        override var preferredInterfaceOrientationForPresentation: UIInterfaceOrientation { .landscapeRight }
    }
}
