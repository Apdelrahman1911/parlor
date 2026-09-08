@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.parlor.app

import platform.UIKit.NSLayoutConstraint
import platform.UIKit.UIInterfaceOrientation
import platform.UIKit.UIInterfaceOrientationMask
import platform.UIKit.UIViewController

/**
 * Gives SwiftUI its own outer view while Compose owns the child's semantics.
 * Host updates must not compete with the language provider's explicit native
 * direction on the Compose view. Navigation and session ownership stay in Compose.
 */
internal class ComposeContainerViewController(
    private val contentController: UIViewController,
) : UIViewController(nibName = null, bundle = null) {
    override fun viewDidLoad() {
        super.viewDidLoad()
        addChildViewController(contentController)
        val contentView = contentController.view
        contentView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(contentView)
        // Full physical bounds, not directional or safe-area anchors: parent
        // and child languages may differ, and Compose already handles insets.
        NSLayoutConstraint.activateConstraints(listOf(
            contentView.leftAnchor.constraintEqualToAnchor(view.leftAnchor),
            contentView.rightAnchor.constraintEqualToAnchor(view.rightAnchor),
            contentView.topAnchor.constraintEqualToAnchor(view.topAnchor),
            contentView.bottomAnchor.constraintEqualToAnchor(view.bottomAnchor),
        ))
        contentController.didMoveToParentViewController(this)
    }

    override fun childViewControllerForStatusBarStyle(): UIViewController = contentController

    override fun childViewControllerForStatusBarHidden(): UIViewController = contentController

    override fun childViewControllerForHomeIndicatorAutoHidden(): UIViewController = contentController

    override fun childViewControllerForScreenEdgesDeferringSystemGestures(): UIViewController = contentController

    override fun supportedInterfaceOrientations(): UIInterfaceOrientationMask =
        contentController.supportedInterfaceOrientations()

    override fun preferredInterfaceOrientationForPresentation(): UIInterfaceOrientation =
        contentController.preferredInterfaceOrientationForPresentation()

    override fun shouldAutorotate(): Boolean = contentController.shouldAutorotate()
}
