package com.parlor.engine.snapshot

import com.parlor.core.ids.GameId
import com.parlor.core.ids.SessionId
import com.parlor.core.versioning.SemVer
import com.parlor.engine.state.GameState
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * A persisted game state. Carries enough envelope to detect mismatched engine
 * versions and refuse to restore an incompatible snapshot.
 */
@Serializable
data class GameSnapshot(
    val sessionId: SessionId,
    val gameId: GameId,
    val engineVersion: SemVer,
    val createdAt: Instant,
    val phaseId: String,
    val payload: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GameSnapshot) return false
        if (sessionId != other.sessionId) return false
        if (engineVersion != other.engineVersion) return false
        if (!payload.contentEquals(other.payload)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = sessionId.hashCode()
        result = 31 * result + engineVersion.hashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }
}

/**
 * Module-supplied codec — serializes a module's state to a byte payload, and
 * back. Implementations use kotlinx.serialization with module-specific schema.
 */
interface SnapshotCodec<S : GameState> {
    fun encode(state: S): ByteArray
    fun decode(payload: ByteArray): S
}
