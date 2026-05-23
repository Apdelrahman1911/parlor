package com.parlor.networking.transport

import com.parlor.core.result.Result
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError

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
