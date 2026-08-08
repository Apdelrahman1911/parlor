package com.parlor.networking.transport

import com.parlor.core.ids.GameId
import com.parlor.core.result.Result
import com.parlor.networking.room.DiscoveredRoom
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Pluggable transport — the production app currently supplies the P2pKit
 * authenticated LAN adapter; in-memory implementations remain test-only.
 *
 * The interface deliberately stays independent of P2pKit so additional
 * transports can be added without changing game/session code.
 */
interface RoomTransport {
    val capability: TransportCapability

    suspend fun host(config: HostConfig): Result<LocalRoom, NetError>
    suspend fun join(code: String, displayName: String): Result<LocalRoom, NetError>

    suspend fun join(config: JoinConfig): Result<LocalRoom, NetError> =
        join(config.code, config.displayName)

    /** Non-secret metadata for an encrypted resumable membership, if present. */
    suspend fun resumableSession(): Result<ResumableSessionInfo?, NetError> =
        Result.Success(null)

    /** Resume the last protected membership on a fresh physical connection. */
    suspend fun resumeLastSession(): Result<LocalRoom, NetError> =
        Result.Failure(NetError.NotConnected)

    /**
     * Stream of rooms currently visible to this transport. Transports that
     * don't support discovery (in-memory stub, P2pKit before it has its
     * `peers` flow wired through) emit an empty list once and complete;
     * the controller will fall back to manual code entry.
     *
     * The default no-op keeps existing transports source-compatible — only
     * transports that actually expose discovery override this.
     */
    fun discoverRooms(): Flow<List<DiscoveredRoom>> = flowOf(emptyList())

    /** Ordered app lifecycle inputs. Implementations must be idempotent. */
    fun notifyAppBackgrounded() = Unit

    fun notifyAppForegrounded() = Unit
}

data class TransportCapability(
    val supportsDiscovery: Boolean,
    val latencyHintMs: Int,
    val maxPayloadBytes: Int,
)

data class HostConfig(
    val roomDisplayName: String,
    val visible: Boolean = true,
    /** Product seat limit excluding the host; enforced atomically by the transport. */
    val maxRemotePlayers: Int = 17,
    /** Shipping game identity persisted into resumable membership credentials. */
    val gameProtocol: HostedGameProtocol? = null,
) {
    init {
        require(maxRemotePlayers in 1..17) { "maxRemotePlayers must be in 1..17" }
    }
}

data class HostedGameProtocol(
    val gameId: GameId,
    val gameVersion: Int,
) {
    init {
        require(gameVersion > 0) { "gameVersion must be positive" }
    }
}

data class JoinConfig(
    val code: String,
    val displayName: String,
    val rejoinToken: String? = null,
)

data class ResumableSessionInfo(
    val gameId: GameId,
    val gameVersion: Int,
    val displayName: String,
    val expiresAtEpochMillis: Long,
)
