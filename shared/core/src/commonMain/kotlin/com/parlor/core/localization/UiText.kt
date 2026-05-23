package com.parlor.core.localization

import kotlinx.serialization.Serializable

/**
 * UiText — a deferred-resolution string for the UI.
 *
 * Models may emit UiText so the actual string is resolved against the current
 * locale at display time, not at construction time.
 */
sealed interface UiText {
    /** Plain literal — no localization. */
    @Serializable
    data class Literal(val value: String) : UiText

    /** Reference to a string resource by key. Resolution is platform-dependent. */
    @Serializable
    data class Resource(val key: String, val args: List<String> = emptyList()) : UiText

    /** A value sourced from validated case content. Already in the case's language. */
    @Serializable
    data class FromContent(val value: String) : UiText
}

/**
 * LocalizedString — a snapshot of text already resolved to a particular language.
 * Used by content payloads that carry per-language variants.
 */
@Serializable
data class LocalizedString(val value: String, val language: String)
