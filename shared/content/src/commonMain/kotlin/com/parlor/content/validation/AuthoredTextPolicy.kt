package com.parlor.content.validation

import com.parlor.core.text.DisplayTextScalars

/** Safety boundary for untrusted text that may be rendered, announced, or logged. */
object AuthoredTextPolicy {
    fun isSafeForDisplay(value: String): Boolean =
        DisplayTextScalars.containsOnlySafeScalars(value)
}
