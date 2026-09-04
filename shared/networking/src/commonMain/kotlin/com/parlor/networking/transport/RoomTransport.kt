package com.parlor.networking.transport

import com.parlor.core.ids.GameId
import com.parlor.core.result.Result
import com.parlor.networking.room.DiscoveredRoom
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

private val unreportedLocalNetworkAccess =
    MutableStateFlow<LocalNetworkAccess>(LocalNetworkAccess.NotApplicable)

/**
 * Pluggable transport — the production app currently supplies the P2pKit
 * authenticated LAN adapter; in-memory implementations remain test-only.
 *
 * The interface deliberately stays independent of P2pKit so additional
 * transports can be added without changing game/session code.
 */
interface RoomTransport {
    val capability: TransportCapability

    /**
     * Evidence available to the app about the LAN path used by this transport.
     *
     * Apple exposes no truthful Local Network preflight API. Implementations
     * therefore report [LocalNetworkAccess.Operational] only after a real
     * advertise/connect operation succeeds and must not turn a timeout into a
     * claimed permission denial. The default keeps non-LAN/test transports
     * source compatible.
     */
    val localNetworkAccess: StateFlow<LocalNetworkAccess>
        get() = unreportedLocalNetworkAccess

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
     * Permanently discards the protected membership returned by
     * [resumableSession], even when no [LocalRoom] could be reconstructed.
     * Implementations must use an ownership-checked transaction so a stale
     * discard cannot erase a concurrently replaced membership. The default is
     * only appropriate for transports that never persist resumable sessions.
     */
    suspend fun discardResumableSession(): Result<Unit, NetError> = Result.Success(Unit)

    /**
     * Stream of rooms currently visible to this transport. Transports that
     * don't support discovery emit an empty list once and complete. A UI may
     * still collect a human room code, but the transport remains responsible
     * for locating the endpoint; this is not a raw-IP/manual-endpoint fallback.
     *
     * The default no-op keeps existing transports source-compatible — only
     * transports that actually expose discovery override this.
     */
    fun discoverRooms(): Flow<List<DiscoveredRoom>> = flowOf(emptyList())

    /** Ordered app lifecycle inputs. Implementations must be idempotent. */
    fun notifyAppBackgrounded() = Unit

    fun notifyAppForegrounded() = Unit
}

/** Truthful, transport-observed local-network state; never a guessed OS grant. */
sealed interface LocalNetworkAccess {
    /** This transport/platform does not expose LAN-operational evidence. */
    data object NotApplicable : LocalNetworkAccess

    /** No real LAN operation has completed in this process yet. */
    data object Unknown : LocalNetworkAccess

    /** A host, join, or resume attempt is currently touching the LAN. */
    data object Attempting : LocalNetworkAccess

    /** Advertising or an authenticated peer connection succeeded. */
    data object Operational : LocalNetworkAccess

    /** A stable platform/API signal proved that user action is required. */
    data object PermissionDenied : LocalNetworkAccess

    /**
     * LAN startup/discovery failed without proof of permission denial. This
     * can be denial, Wi-Fi state, routing, Bonjour, firewall, or transport.
     */
    data object FailureUnclassified : LocalNetworkAccess
}

val LocalNetworkAccess.needsRecoveryGuidance: Boolean
    get() = this == LocalNetworkAccess.PermissionDenied ||
        this == LocalNetworkAccess.FailureUnclassified

data class TransportCapability(
    /** True only when [RoomTransport.discoverRooms] exposes a browsable room list. */
    val supportsDiscovery: Boolean,
    val latencyHintMs: Int,
    val maxPayloadBytes: Int,
    /**
     * True only when callers can supply a host endpoint plus authenticated
     * identity pin without LAN discovery. Typing a room code does not imply
     * this capability: [RoomTransport.join] may still discover the endpoint.
     */
    val supportsManualEndpointConnection: Boolean = false,
)

data class HostConfig(
    /** Canonical player-facing name of the authoritative host. */
    val hostDisplayName: String,
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
