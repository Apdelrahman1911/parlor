package com.parlor.networking.room

/**
 * Session-owned, authenticated round trip before a retained socket may accept
 * local input again. No game command is replayed or optimistically applied.
 * A transport bounds this operation and falls back to ordinary secure recovery.
 */
interface ForegroundConnectionValidator {
    val ready: Boolean
    suspend fun validate(): Boolean
}
