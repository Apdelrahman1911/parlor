package com.parlor.app

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.defaultPopTransitionSpec
import androidx.navigation3.ui.defaultPredictivePopTransitionSpec
import androidx.navigation3.ui.defaultTransitionSpec

internal actual fun <T : Any> platformNavigationTransitionSpec(
    layoutDirection: LayoutDirection,
): AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform = defaultTransitionSpec()

internal actual fun <T : Any> platformNavigationPopTransitionSpec(
    layoutDirection: LayoutDirection,
): AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform = defaultPopTransitionSpec()

internal actual fun <T : Any> platformNavigationPredictivePopTransitionSpec(
    layoutDirection: LayoutDirection,
): AnimatedContentTransitionScope<Scene<T>>.(Int) -> ContentTransform =
    defaultPredictivePopTransitionSpec()
