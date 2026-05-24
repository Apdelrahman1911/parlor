package com.parlor.core.logging

import kotlin.jvm.JvmInline

/**
 * Logger contract. Platform actuals provide the underlying implementation
 * (Android Log, NSLog, println on Desktop).
 *
 * Discipline (per ARCHITECTURE.md §A.3): never log private or host-only content.
 * Pass [SafeForLogs] wrappers if the data has been intentionally redacted.
 */
interface Logger {
    fun debug(tag: String, message: String)
    fun info(tag: String, message: String)
    fun warn(tag: String, message: String, cause: Throwable? = null)
    fun error(tag: String, message: String, cause: Throwable? = null)
}

/**
 * Marker for values that have been intentionally redacted and are safe to log.
 * Reduces accidental logging of dossier text, roles, killer ids, etc.
 */
@JvmInline
value class SafeForLogs(val text: String) {
    override fun toString(): String = text
}
