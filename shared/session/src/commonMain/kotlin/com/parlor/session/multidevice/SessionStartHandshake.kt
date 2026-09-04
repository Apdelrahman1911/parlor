package com.parlor.session.multidevice

import com.parlor.core.ids.GameId
import com.parlor.core.result.Result
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.PeerMessage
import com.parlor.networking.protocol.ProtocolValidation
import com.parlor.networking.protocol.ProtocolVersion
import com.parlor.networking.protocol.SessionEnvelopeHeader
import com.parlor.networking.protocol.SessionProtocol
import com.parlor.networking.protocol.validateFor
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.networking.room.PeerEvent
import com.parlor.networking.room.RoomLifecycleState
import com.parlor.networking.room.SessionEndCommitStatus
import com.parlor.networking.security.SecureIds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.withTimeoutOrNull

/** A peer-validated, authoritatively committed multiplayer game start. */
data class ValidatedSessionStart(
    val offer: HostMessage.SessionStarting,
    val protocol: SessionProtocol,
)

/**
 * Typed peer-start failures keep local content/setup defects distinct from an
 * incompatible host. Callers may present a generic message, but diagnostics
 * and tests retain the real failure class instead of mislabelling everything
 * as a protocol-version error.
 */
sealed interface SessionStartFailure {
    data class Network(val error: NetError) : SessionStartFailure
    data class Protocol(val validation: ProtocolValidation) : SessionStartFailure
    data object PreparationRejected : SessionStartFailure
    data class PreparationFailed(val cause: Throwable) : SessionStartFailure
}

/** UI-independent gate shared by both shipping host game flows. */
sealed interface HostStartGateState {
    data object Starting : HostStartGateState
    data object Started : HostStartGateState
    data class Failed(val error: NetError) : HostStartGateState
    data object Exiting : HostStartGateState
}

fun <T> Result<T, NetError>.toHostStartGateState(): HostStartGateState =
    when (this) {
        is Result.Success -> HostStartGateState.Started
        is Result.Failure -> HostStartGateState.Failed(error)
    }

/** Idempotent transition used to guard Retry/Cancel against duplicate taps. */
fun HostStartGateState.beginExit(): HostStartGateState = HostStartGateState.Exiting

/** Conservative compatibility mapping for UI surfaces that still store [NetError]. */
fun SessionStartFailure.asNetError(): NetError = when (this) {
    is SessionStartFailure.Network -> error
    is SessionStartFailure.Protocol,
    SessionStartFailure.PreparationRejected -> NetError.IncompatibleProtocol
    is SessionStartFailure.PreparationFailed ->
        NetError.TransportFailure("local game preparation failed")
}

private sealed interface PeerStartEvent {
    data class Message(val value: com.parlor.networking.protocol.RoomMessage) : PeerStartEvent
    data class Connection(val value: PeerEvent) : PeerStartEvent
    data class Lifecycle(val value: RoomLifecycleState) : PeerStartEvent
}

/**
 * Runs protocol-v4's peer game-start barrier as the sole collector of the room
 * inbox. Waiting in the human-controlled lobby has no arbitrary timeout. The
 * bounded preparation deadline begins only when a structurally and locally
 * version-compatible offer arrives. Sending Ready starts a fresh bounded
 * commit-delivery phase, so a slow valid preparation cannot consume the
 * entire commit window.
 *
 * A valid commit is irreversible authority to enter gameplay. Its acknowledgement
 * is best-effort delivery confirmation; a lost acknowledgement is recovered by
 * the game coordinator when the host retries the same commit. Transient
 * [PeerEvent.HostLost] is therefore not terminal: the transport may restore the
 * authenticated session before the transaction deadline.
 */
suspend fun awaitAuthoritativeSessionStart(
    room: LocalRoom,
    expectedGameId: GameId,
    expectedGameVersion: Int,
    deadlineMs: Long = DEFAULT_PEER_START_DEADLINE_MS,
    sendTimeoutMs: Long = DEFAULT_START_FRAME_SEND_TIMEOUT_MS,
    idGenerator: () -> String = SecureIds::id128,
    prepareOffer: suspend (HostMessage.SessionStarting, SessionProtocol) -> Boolean,
): Result<ValidatedSessionStart, SessionStartFailure> = coroutineScope {
    require(deadlineMs > 0L) { "deadlineMs must be positive" }
    require(sendTimeoutMs > 0L) { "sendTimeoutMs must be positive" }

    val merged: Flow<PeerStartEvent> = merge(
        room.incoming.map(PeerStartEvent::Message),
        room.peerEvents.map(PeerStartEvent::Connection),
        room.lifecycle.map(PeerStartEvent::Lifecycle),
    )
    val events = merged.produceIn(this)

    try {
        while (true) {
            val event = events.receiveCatching().getOrNull()
                ?: return@coroutineScope startNetworkFailure(NetError.NotConnected)
            when (event) {
                is PeerStartEvent.Connection -> Unit // HostLost is resumable, HostRestored is informational.
                is PeerStartEvent.Lifecycle -> if (event.value.isTerminal()) {
                    return@coroutineScope startNetworkFailure(NetError.NotConnected)
                }
                is PeerStartEvent.Message -> when (val message = event.value) {
                    is HostMessage.SessionStarting -> {
                        // The received header supplies session identity/epoch only.
                        // Compatibility is always checked against this binary's
                        // local protocol version, never against the untrusted frame.
                        val expected = SessionProtocol(
                            sessionId = message.header.sessionId,
                            gameId = expectedGameId,
                            gameVersion = expectedGameVersion,
                            protocol = ProtocolVersion(),
                            connectionEpoch = message.header.connectionEpoch,
                        )
                        val validation = message.validateFor(expected)
                        if (validation != ProtocolValidation.Valid) {
                            return@coroutineScope Result.Failure(
                                SessionStartFailure.Protocol(validation),
                            )
                        }
                        return@coroutineScope runStartTransaction(
                            room = room,
                            events = events,
                            offer = message,
                            expected = expected,
                            deadlineMs = deadlineMs,
                            sendTimeoutMs = sendTimeoutMs,
                            idGenerator = idGenerator,
                            prepareOffer = prepareOffer,
                        )
                    }
                    is HostMessage.SessionEnded -> {
                        val expected = SessionProtocol(
                            sessionId = message.header.sessionId,
                            gameId = expectedGameId,
                            gameVersion = expectedGameVersion,
                            protocol = ProtocolVersion(),
                            connectionEpoch = message.header.connectionEpoch,
                        )
                        terminalStartFailureOrNull(room, message, expected)?.let {
                            return@coroutineScope it
                        }
                    }
                    else -> Unit
                }
            }
        }
        // Kotlin does not infer the non-returning type of this receive loop.
        @Suppress("UNREACHABLE_CODE")
        startNetworkFailure(NetError.NotConnected)
    } finally {
        events.cancel()
    }
}

private suspend fun runStartTransaction(
    room: LocalRoom,
    events: ReceiveChannel<PeerStartEvent>,
    offer: HostMessage.SessionStarting,
    expected: SessionProtocol,
    deadlineMs: Long,
    sendTimeoutMs: Long,
    idGenerator: () -> String,
    prepareOffer: suspend (HostMessage.SessionStarting, SessionProtocol) -> Boolean,
): Result<ValidatedSessionStart, SessionStartFailure> {
    val preparation = withTimeoutOrNull(deadlineMs) {
        try {
            Result.Success<Boolean>(prepareOffer(offer, expected))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
            Result.Failure(
                SessionStartFailure.PreparationFailed(failure),
            )
        }
    } ?: return startNetworkFailure(NetError.Timeout)
    val preparedSuccessfully = when (preparation) {
        is Result.Success -> preparation.data
        is Result.Failure -> return Result.Failure(preparation.error)
    }
    if (!preparedSuccessfully) {
        return Result.Failure(SessionStartFailure.PreparationRejected)
    }

    val prepared = ValidatedSessionStart(
        offer = offer,
        protocol = expected.copy(startId = offer.startId),
    )
    sendReady(room, prepared.protocol, offer.startId, sendTimeoutMs, idGenerator)

    // Ready establishes a new bounded phase. Preparation consuming most of
    // its budget must not leave only a few milliseconds for commit delivery.
    val committed = withTimeoutOrNull(deadlineMs) {
        while (true) {
            val event = events.receiveCatching().getOrNull()
                ?: return@withTimeoutOrNull startNetworkFailure(NetError.NotConnected)
            when (event) {
                is PeerStartEvent.Connection -> {
                    if (event.value == PeerEvent.HostRestored) {
                        sendReady(
                            room,
                            prepared.protocol,
                            offer.startId,
                            sendTimeoutMs,
                            idGenerator,
                        )
                    }
                    // HostLost is a transient transport signal. Closed/Expired
                    // lifecycle or this transaction's deadline is terminal.
                }
                is PeerStartEvent.Lifecycle -> if (event.value.isTerminal()) {
                    return@withTimeoutOrNull startNetworkFailure(NetError.NotConnected)
                }
                is PeerStartEvent.Message -> when (val message = event.value) {
                    is HostMessage.SessionStarting -> {
                        val validation = message.validateFor(expected)
                        if (validation != ProtocolValidation.Valid) {
                            return@withTimeoutOrNull Result.Failure(
                                SessionStartFailure.Protocol(validation),
                            )
                        }
                        if (message.startId != offer.startId) continue
                        if (message != offer) {
                            return@withTimeoutOrNull Result.Failure(
                                SessionStartFailure.Protocol(
                                    ProtocolValidation.InvalidSessionStart,
                                ),
                            )
                        }
                        sendReady(
                            room,
                            prepared.protocol,
                            offer.startId,
                            sendTimeoutMs,
                            idGenerator,
                        )
                    }
                    is HostMessage.SessionStartCommitted -> {
                        if (message.startId != offer.startId) continue
                        val validation = message.validateFor(prepared.protocol)
                        if (validation != ProtocolValidation.Valid) {
                            return@withTimeoutOrNull Result.Failure(
                                SessionStartFailure.Protocol(validation),
                            )
                        }
                        // Commit authority is irreversible. Do not keep the peer
                        // in limbo merely because delivery confirmation was lost.
                        sendCommitAck(
                            room,
                            prepared.protocol,
                            offer.startId,
                            sendTimeoutMs,
                            idGenerator,
                        )
                        return@withTimeoutOrNull Result.Success(prepared)
                    }
                    is HostMessage.SessionEnded -> {
                        terminalStartFailureOrNull(room, message, prepared.protocol)?.let {
                            return@withTimeoutOrNull it
                        }
                    }
                    else -> Unit
                }
            }
        }
        // Kotlin does not infer the non-returning type of this receive loop.
        @Suppress("UNREACHABLE_CODE")
        startNetworkFailure(NetError.NotConnected)
    }
    return committed ?: startNetworkFailure(NetError.Timeout)
}

private suspend fun terminalStartFailureOrNull(
    room: LocalRoom,
    message: HostMessage.SessionEnded,
    expected: SessionProtocol,
): Result<ValidatedSessionStart, SessionStartFailure>? {
    if (message.validateFor(expected) != ProtocolValidation.Valid) return null
    return when (val committed = commitValidatedTerminal(room, message)) {
        is Result.Failure -> Result.Failure(committed.error)
        is Result.Success -> if (committed.data == SessionEndCommitStatus.Committed) {
            startNetworkFailure(NetError.NotConnected)
        } else {
            null
        }
    }
}

private suspend fun commitValidatedTerminal(
    room: LocalRoom,
    message: HostMessage.SessionEnded,
): Result<SessionEndCommitStatus, SessionStartFailure> = try {
    when (val committed = room.commitValidatedSessionEnd(message)) {
        is Result.Success -> committed
        is Result.Failure -> Result.Failure(SessionStartFailure.Network(committed.error))
    }
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
    Result.Failure(
        SessionStartFailure.Network(
            NetError.TransportFailure("session end commit failed"),
        ),
    )
}

private suspend fun sendReady(
    room: LocalRoom,
    protocol: SessionProtocol,
    startId: String,
    sendTimeoutMs: Long,
    idGenerator: () -> String,
) {
    sendStartAcknowledgement(room, sendTimeoutMs) {
        PeerMessage.SessionStartReady(
            header = peerStartHeader(protocol, idGenerator()),
            actor = room.selfPlayerId,
            startId = startId,
        )
    }
}

private suspend fun sendCommitAck(
    room: LocalRoom,
    protocol: SessionProtocol,
    startId: String,
    sendTimeoutMs: Long,
    idGenerator: () -> String,
) {
    sendStartAcknowledgement(room, sendTimeoutMs) {
        PeerMessage.SessionStartCommitAck(
            header = peerStartHeader(protocol, idGenerator()),
            actor = room.selfPlayerId,
            startId = startId,
        )
    }
}

/** Start acknowledgements are best effort; host retries provide reliability. */
private suspend fun sendStartAcknowledgement(
    room: LocalRoom,
    sendTimeoutMs: Long,
    message: () -> PeerMessage,
) {
    try {
        withTimeoutOrNull(sendTimeoutMs) { room.sendToHost(message()) }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
        // LocalRoom's contract returns NetError. A defensive adapter throw is
        // treated like a dropped acknowledgement and recovered by host retry.
    }
}

private fun RoomLifecycleState.isTerminal(): Boolean =
    this == RoomLifecycleState.Expired || this == RoomLifecycleState.Closed

private fun startNetworkFailure(
    error: NetError,
): Result<ValidatedSessionStart, SessionStartFailure> =
    Result.Failure(SessionStartFailure.Network(error))

internal fun peerStartHeader(
    protocol: SessionProtocol,
    messageId: String,
): SessionEnvelopeHeader = SessionEnvelopeHeader(
    protocol = protocol.protocol,
    sessionId = protocol.sessionId,
    gameId = protocol.gameId,
    gameVersion = protocol.gameVersion,
    messageId = messageId,
    sequence = 0L,
    connectionEpoch = protocol.connectionEpoch,
)

const val DEFAULT_PEER_START_DEADLINE_MS: Long = 20_000L
const val DEFAULT_START_FRAME_SEND_TIMEOUT_MS: Long = 2_000L
