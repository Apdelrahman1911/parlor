@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.parlor.app

import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSThread
import platform.UIKit.UIInterfaceOrientation
import platform.UIKit.UIInterfaceOrientationLandscapeRight
import platform.UIKit.UIInterfaceOrientationMask
import platform.UIKit.UIInterfaceOrientationMaskLandscape
import platform.UIKit.UISemanticContentAttributeForceLeftToRight
import platform.UIKit.UISemanticContentAttributeForceRightToLeft
import platform.UIKit.UIViewController
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Actual UIKit containment; SwiftUI/Compose interaction also requires app-host verification. */
class ComposeContainerViewControllerTest {
    @Test
    fun oneStableChildKeepsItsExplicitSemanticsIndependentOfTheOuterView() {
        assertTrue(NSThread.isMainThread)
        val child = UIViewController(nibName = null, bundle = null)
        val container = ComposeContainerViewController(child)
        container.loadViewIfNeeded()
        val childView = child.view
        for (direction in listOf(
            UISemanticContentAttributeForceLeftToRight,
            UISemanticContentAttributeForceRightToLeft,
            UISemanticContentAttributeForceLeftToRight,
        )) {
            childView.semanticContentAttribute = direction
            for (hostDirection in listOf(
                UISemanticContentAttributeForceRightToLeft,
                UISemanticContentAttributeForceLeftToRight,
            )) {
                container.view.semanticContentAttribute = hostDirection
                container.loadViewIfNeeded()
                assertEquals(direction, childView.semanticContentAttribute)
                assertTrue(child.view === childView)
                assertEquals(1, container.childViewControllers.size)
                assertTrue(container.childViewControllers.single() === child)
                assertTrue(child.parentViewController === container)
                assertTrue(childView.superview === container.view)
            }
        }
    }

    @Test
    fun childUsesFullBoundsAcrossSizesAndOpposingSemanticDirections() {
        assertTrue(NSThread.isMainThread)
        val child = UIViewController(nibName = null, bundle = null)
        val container = ComposeContainerViewController(child)
        container.loadViewIfNeeded()
        val directions = listOf(
            UISemanticContentAttributeForceLeftToRight,
            UISemanticContentAttributeForceRightToLeft,
        )
        for ((width, height) in listOf(320.0 to 480.0, 640.0 to 320.0, 1024.0 to 768.0)) {
            for (outerDirection in directions) for (childDirection in directions) {
                container.view.semanticContentAttribute = outerDirection
                child.view.semanticContentAttribute = childDirection
                container.view.frame = CGRectMake(0.0, 0.0, width, height)
                container.view.setNeedsLayout()
                container.view.layoutIfNeeded()
                child.view.frame.useContents {
                    assertEquals(0.0, origin.x, absoluteTolerance = 0.01)
                    assertEquals(0.0, origin.y, absoluteTolerance = 0.01)
                    assertEquals(width, size.width, absoluteTolerance = 0.01)
                    assertEquals(height, size.height, absoluteTolerance = 0.01)
                }
            }
        }
    }

    @Test
    fun systemDecorationAndOrientationPoliciesStillBelongToTheChild() {
        assertTrue(NSThread.isMainThread)
        val child = RecordingChild()
        val container = ComposeContainerViewController(child)
        container.loadViewIfNeeded()
        assertTrue(container.childViewControllerForStatusBarStyle() === child)
        assertTrue(container.childViewControllerForStatusBarHidden() === child)
        assertTrue(container.childViewControllerForHomeIndicatorAutoHidden() === child)
        assertTrue(container.childViewControllerForScreenEdgesDeferringSystemGestures() === child)
        assertEquals(UIInterfaceOrientationMaskLandscape, container.supportedInterfaceOrientations())
        assertEquals(UIInterfaceOrientationLandscapeRight, container.preferredInterfaceOrientationForPresentation())
        assertFalse(container.shouldAutorotate())
    }

    @Test
    fun defaultAppearanceForwardingReachesTheSameChildExactlyOnce() {
        assertTrue(NSThread.isMainThread)
        val child = RecordingChild()
        val container = ComposeContainerViewController(child)
        container.loadViewIfNeeded()
        container.beginAppearanceTransition(true, animated = false)
        container.endAppearanceTransition()
        assertEquals(1, child.appearances)
        assertEquals(0, child.disappearances)
        container.beginAppearanceTransition(false, animated = false)
        container.endAppearanceTransition()
        assertEquals(1, child.appearances)
        assertEquals(1, child.disappearances)
        assertTrue(container.childViewControllers.single() === child)
    }

    private class RecordingChild : UIViewController(nibName = null, bundle = null) {
        var appearances = 0
        var disappearances = 0

        override fun viewDidAppear(animated: Boolean) {
            super.viewDidAppear(animated)
            appearances++
        }

        override fun viewDidDisappear(animated: Boolean) {
            super.viewDidDisappear(animated)
            disappearances++
        }

        override fun supportedInterfaceOrientations(): UIInterfaceOrientationMask = UIInterfaceOrientationMaskLandscape

        override fun preferredInterfaceOrientationForPresentation(): UIInterfaceOrientation =
            UIInterfaceOrientationLandscapeRight

        override fun shouldAutorotate(): Boolean = false
    }
}
