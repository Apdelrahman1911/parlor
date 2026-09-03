package com.parlor.app

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation3.scene.Scene

private const val IOS_NAVIGATION_TRANSITION_MILLIS = 500
private val IosNavigationEasing = CubicBezierEasing(0.2833f, 0.99f, 0.31833f, 0.99f)

/**
 * Navigation 3 UI 1.0.0-alpha06 hard-codes regular iOS motion for LTR. Keep its
 * duration/easing, but mirror the public transition in Arabic/RTL.
 */
@OptIn(ExperimentalAnimationApi::class)
internal actual fun <T : Any> platformNavigationTransitionSpec(
    layoutDirection: LayoutDirection,
): AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform = {
    val towards = navigationForwardDirection(layoutDirection)
    ContentTransform(
        targetContentEnter = slideIntoContainer(
            towards = towards,
            animationSpec = tween(IOS_NAVIGATION_TRANSITION_MILLIS, easing = IosNavigationEasing),
        ),
        initialContentExit = slideOutOfContainer(
            towards = towards,
            targetOffset = { it / 4 },
            animationSpec = tween(IOS_NAVIGATION_TRANSITION_MILLIS, easing = IosNavigationEasing),
        ),
    )
}

@OptIn(ExperimentalAnimationApi::class)
internal actual fun <T : Any> platformNavigationPopTransitionSpec(
    layoutDirection: LayoutDirection,
): AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform = {
    val towards = navigationBackDirection(layoutDirection)
    ContentTransform(
        targetContentEnter = slideIntoContainer(
            towards = towards,
            initialOffset = { it / 4 },
            animationSpec = tween(IOS_NAVIGATION_TRANSITION_MILLIS, easing = IosNavigationEasing),
        ),
        initialContentExit = slideOutOfContainer(
            towards = towards,
            animationSpec = tween(IOS_NAVIGATION_TRANSITION_MILLIS, easing = IosNavigationEasing),
        ),
    )
}

@OptIn(ExperimentalAnimationApi::class)
internal actual fun <T : Any> platformNavigationPredictivePopTransitionSpec(
    layoutDirection: LayoutDirection,
): AnimatedContentTransitionScope<Scene<T>>.(Int) -> ContentTransform = { swipeEdge ->
    val towards = navigationPredictiveBackDirection(swipeEdge, layoutDirection)
    ContentTransform(
        targetContentEnter = slideIntoContainer(
            towards = towards,
            initialOffset = { it / 4 },
            animationSpec = tween(IOS_NAVIGATION_TRANSITION_MILLIS, easing = LinearEasing),
        ),
        initialContentExit = slideOutOfContainer(
            towards = towards,
            animationSpec = tween(IOS_NAVIGATION_TRANSITION_MILLIS, easing = LinearEasing),
        ),
    )
}
