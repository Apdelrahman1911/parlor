package com.parlor.designsystem.localization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.uikit.LocalUIViewController
import androidx.compose.ui.unit.LayoutDirection
import platform.Foundation.NSBundle
import platform.Foundation.NSLocale
import platform.Foundation.NSUserDefaults
import platform.Foundation.preferredLanguages
import platform.UIKit.UISemanticContentAttributeForceLeftToRight
import platform.UIKit.UISemanticContentAttributeForceRightToLeft
import platform.UIKit.UIViewController

/**
 * Compose resources read [NSLocale.preferredLanguages]. An explicit in-app
 * language therefore needs the platform AppleLanguages override used by the
 * Compose resource implementation. Persistent ownership bookkeeping lets
 * System mode release a Parlor override even after an interrupted process.
 * Unowned platform preferences are never deleted or promoted from a fallback.
 */
@Composable
internal actual fun PlatformAppLocale(
    languageTag: String?,
    loading: @Composable () -> Unit,
    content: @Composable (activeLanguageTag: String?) -> Unit,
) {
    var appliedLanguageTag by remember { mutableStateOf<String?>(null) }
    val overrideOwner = remember {
        IosLanguageOverrideOwner(
            NSUserDefaults.standardUserDefaults,
            checkNotNull(NSBundle.mainBundle.bundleIdentifier),
        )
    }

    DisposableEffect(languageTag) {
        overrideOwner.apply(languageTag)
        appliedLanguageTag = languageTag ?: NSLocale.preferredLanguages
            .firstOrNull()
            ?.toString()

        onDispose {
            overrideOwner.release()
        }
    }

    val activeTag = appliedLanguageTag ?: run {
        loading()
        return
    }
    val viewController = LocalUIViewController.current
    val layoutDirection = resolveAppLanguage(null, activeTag).layoutDirection
    SideEffect {
        applyNativeLayoutDirection(viewController, layoutDirection)
    }
    content(activeTag)
}

/** Keeps Compose layout and the UIKit-owned start-edge Back gesture aligned. */
private fun applyNativeLayoutDirection(
    viewController: UIViewController,
    layoutDirection: LayoutDirection,
) {
    val view = viewController.view
    val semanticAttribute = if (layoutDirection == LayoutDirection.Rtl) {
        UISemanticContentAttributeForceRightToLeft
    } else {
        UISemanticContentAttributeForceLeftToRight
    }
    if (view.semanticContentAttribute == semanticAttribute) return

    view.semanticContentAttribute = semanticAttribute
    if (view.window != null) {
        // Compose UI 1.10.3 resolves the physical start edge when the host
        // view attaches. Refresh only after a real runtime direction change.
        view.didMoveToWindow()
    }
}
