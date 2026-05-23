package com.parlor.core.result

/**
 * Layer-specific error hierarchies. Each layer defines its own; the UI maps to UiText
 * via a single resolver so raw exception text never reaches a user.
 *
 * These are sealed so the compiler enforces exhaustive handling.
 */

/** Data-layer errors — cache, IO, persistence. */
sealed interface DataError {
    data object NotFound : DataError
    data object CorruptedData : DataError
    data class IoError(val cause: String) : DataError
    data object DiskFull : DataError
    data object PermissionDenied : DataError
    data class Unknown(val message: String? = null) : DataError
}

/** Network-layer errors — request/response problems. */
sealed interface NetworkError {
    data object Timeout : NetworkError
    data object Unreachable : NetworkError
    data class Server(val httpStatus: Int) : NetworkError
    data object Unauthorized : NetworkError
    data class Serialization(val message: String) : NetworkError
    data class Unknown(val message: String? = null) : NetworkError
}

/** Validation errors — content schema, payloads, action submissions. */
sealed interface ValidationError {
    data object MalformedJson : ValidationError
    data class UnsupportedSchema(val schemaVersion: Int) : ValidationError
    data class AppUpdateRequired(val minimum: String) : ValidationError
    data class UnknownGame(val gameId: String) : ValidationError
    data class MalformedField(val path: String, val reason: String) : ValidationError
    data class PlayerCountOutOfRange(val supplied: IntRange, val allowed: IntRange) : ValidationError
    data class UnknownMode(val modeId: String) : ValidationError
    data class PayloadInvalid(val detail: String) : ValidationError
}
