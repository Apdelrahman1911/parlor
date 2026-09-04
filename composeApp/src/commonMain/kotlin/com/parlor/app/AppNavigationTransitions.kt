package com.parlor.app

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation3.scene.Scene
import androidx.navigationevent.NavigationEvent

/** Logical directions used by the iOS transition implementation. */
internal fun navigationForwardDirection(
    layoutDirection: LayoutDirection,
): AnimatedContentTransitionScope.SlideDirection = when (layoutDirection) {
    LayoutDirection.Ltr -> AnimatedContentTransitionScope.SlideDirection.Left
    LayoutDirection.Rtl -> AnimatedContentTransitionScope.SlideDirection.Right
}

internal fun navigationBackDirection(
    layoutDirection: LayoutDirection,
): AnimatedContentTransitionScope.SlideDirection = when (layoutDirection) {
    LayoutDirection.Ltr -> AnimatedContentTransitionScope.SlideDirection.Right
    LayoutDirection.Rtl -> AnimatedContentTransitionScope.SlideDirection.Left
}

internal fun navigationPredictiveBackDirection(
    swipeEdge: Int,
    layoutDirection: LayoutDirection,
): AnimatedContentTransitionScope.SlideDirection = when (swipeEdge) {
    NavigationEvent.EDGE_LEFT -> AnimatedContentTransitionScope.SlideDirection.Right
    NavigationEvent.EDGE_RIGHT -> AnimatedContentTransitionScope.SlideDirection.Left
    else -> navigationBackDirection(layoutDirection)
}

internal expect fun <T : Any> platformNavigationTransitionSpec(
    layoutDirection: LayoutDirection,
): AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform

internal expect fun <T : Any> platformNavigationPopTransitionSpec(
    layoutDirection: LayoutDirection,
): AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform

internal expect fun <T : Any> platformNavigationPredictivePopTransitionSpec(
    layoutDirection: LayoutDirection,
): AnimatedContentTransitionScope<Scene<T>>.(Int) -> ContentTransform

internal fun <T : Any> reducedNavigationTransitionSpec(
    durationMillis: Int,
    easing: Easing,
): AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform = {
    ContentTransform(
        targetContentEnter = fadeIn(tween(durationMillis, easing = easing)),
        initialContentExit = fadeOut(tween(durationMillis, easing = easing)),
    )
}

internal fun <T : Any> reducedNavigationPredictivePopTransitionSpec(
    durationMillis: Int,
): AnimatedContentTransitionScope<Scene<T>>.(Int) -> ContentTransform = {
    ContentTransform(
        targetContentEnter = fadeIn(tween(durationMillis, easing = LinearEasing)),
        initialContentExit = fadeOut(tween(durationMillis, easing = LinearEasing)),
    )
}
