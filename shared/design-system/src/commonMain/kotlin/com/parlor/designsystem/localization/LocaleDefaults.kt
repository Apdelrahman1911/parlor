package com.parlor.designsystem.localization

/** Match Android's adjusted defaults without discarding the system fallbacks. */
internal fun <T> orderedLocaleDefaults(selected: T?, platformDefaults: List<T>): List<T> =
    if (selected == null) platformDefaults else listOf(selected) + platformDefaults.filterNot { it == selected }
