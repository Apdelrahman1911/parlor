package com.parlor.core.telemetry

import com.parlor.core.logging.SafeForLogs

/**
 * Telemetry contract. Phase 8 ships concrete implementations per platform
 * (Firebase / App Center / nothing). The interface accepts only [SafeForLogs]
 * values to prevent dossier or host-only data from leaking into payloads.
 *
 * Discipline: every call site is responsible for wrapping any string in
 * [SafeForLogs]. The wrapper carries no validation — it is a marker. The
 * authoritative no-leak guarantee is enforced by:
 *  1. A custom detekt rule that flags `Telemetry.event(...)` calls that pass a
 *     raw `String` argument for fields known to carry private types.
 *  2. The architecture test in `:shared:engine` ensuring private types stay
 *     out of any package that imports `Telemetry`.
 */
interface Telemetry {
    fun event(name: String, attributes: Map<String, SafeForLogs> = emptyMap())
    fun setUserAttribute(key: String, value: SafeForLogs)
    fun reportException(throwable: Throwable, attributes: Map<String, SafeForLogs> = emptyMap())
}

/** No-op telemetry for dev builds and tests. */
object NoOpTelemetry : Telemetry {
    override fun event(name: String, attributes: Map<String, SafeForLogs>) {}
    override fun setUserAttribute(key: String, value: SafeForLogs) {}
    override fun reportException(throwable: Throwable, attributes: Map<String, SafeForLogs>) {}
}

/** Canonical attribute keys — keep narrow and explicit. */
object TelemetryKeys {
    const val GAME_ID = "game_id"
    const val CASE_ID = "case_id"
    const val CASE_VERSION = "case_version"
    const val MODE_ID = "mode_id"
    const val PLAYER_COUNT = "player_count"
    const val SESSION_DURATION_SECONDS = "session_duration_seconds"
    const val WHODUNIT_OUTCOME = "outcome"           // "players_win" | "killer_wins_<cause>"
    const val WHODUNIT_ROUNDS_PLAYED = "rounds_played"
    const val VALIDATION_ERROR = "validation_error"
}
