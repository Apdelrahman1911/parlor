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
    /**
     * Session-level annotations that aren't part of game state but the
     * shell needs to restore the session correctly — e.g. the play mode
     * the user originally chose. Encoded as strings to keep the codec
     * format-agnostic and to avoid coupling the engine schema to any
     * particular shell's value types. Optional; defaults to empty so
     * pre-9I snapshots deserialise unchanged.
     */
    val metadata: Map<String, String> = emptyMap(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GameSnapshot) return false
        if (sessionId != other.sessionId) return false
        if (gameId != other.gameId) return false
        if (engineVersion != other.engineVersion) return false
        if (createdAt != other.createdAt) return false
        if (phaseId != other.phaseId) return false
        if (!payload.contentEquals(other.payload)) return false
        if (metadata != other.metadata) return false
        return true
    }

    override fun hashCode(): Int {
        var result = sessionId.hashCode()
        result = 31 * result + gameId.hashCode()
        result = 31 * result + engineVersion.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + phaseId.hashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + metadata.hashCode()
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
