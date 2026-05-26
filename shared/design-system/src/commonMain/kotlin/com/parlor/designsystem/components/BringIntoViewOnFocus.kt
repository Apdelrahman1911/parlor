package com.parlor.designsystem.components

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusEvent
import kotlinx.coroutines.launch

/**
 * Scrolls the host composable into the viewport whenever it gains focus —
 * the missing iOS-native behavior where tapping a text field below the
 * keyboard auto-scrolls it into view. Without this, a text field at the
 * bottom of a scrollable column stays hidden behind the keyboard after the
 * user taps it.
 *
 * Apply on any text field that may live below the fold:
 * ```
 * OutlinedTextField(modifier = Modifier.bringIntoViewOnFocus(), ...)
 * ```
 */
@Composable
fun Modifier.bringIntoViewOnFocus(): Modifier = composed {
    val requester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    this
        .bringIntoViewRequester(requester)
        .onFocusEvent { state ->
            if (state.isFocused) {
                scope.launch { requester.bringIntoView() }
            }
        }
}
