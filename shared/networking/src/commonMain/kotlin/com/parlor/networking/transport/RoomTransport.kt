package com.parlor.networking.transport

import com.parlor.core.result.Result
import com.parlor.networking.room.DiscoveredRoom
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Pluggable transport — Android Nearby, iOS Multipeer, Desktop mDNS+WebSocket,
 * or an in-memory test stub.
 *
 * Phase 7 ships an in-memory stub only. Post-MVP wires real implementations
 * via expect/actual or DI.
 */
interface RoomTransport {
    val capability: TransportCapability

    suspend fun host(config: HostConfig): Result<LocalRoom, NetError>
    suspend fun join(code: String, displayName: String): Result<LocalRoom, NetError>

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
}

data class TransportCapability(
    val supportsDiscovery: Boolean,
    val latencyHintMs: Int,
    val maxPayloadBytes: Int,
)

data class HostConfig(
    val roomDisplayName: String,
    val visible: Boolean = true,
)
