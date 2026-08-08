package com.parlor.networking.protocol

import com.parlor.core.ids.GameId
import com.parlor.core.ids.SessionId

/** The immutable identity/version tuple for one authoritative game session. */
data class SessionProtocol(
    val sessionId: SessionId,
    val gameId: GameId,
    val gameVersion: Int,
    val protocol: ProtocolVersion = ProtocolVersion(),
)

sealed interface ProtocolValidation {
    data object Valid : ProtocolValidation
    data object IncompatibleProtocol : ProtocolValidation
    data object WrongSession : ProtocolValidation
    data object WrongGame : ProtocolValidation
    data object IncompatibleGameVersion : ProtocolValidation
    data object InvalidMessageId : ProtocolValidation
    data object InvalidSequence : ProtocolValidation
    data object CommandPayloadTooLarge : ProtocolValidation
    data object SnapshotPayloadTooLarge : ProtocolValidation
}

fun SessionEnvelopeHeader.validateFor(expected: SessionProtocol): ProtocolValidation = when {
    !protocol.isCompatibleWith(expected.protocol) -> ProtocolValidation.IncompatibleProtocol
    sessionId != expected.sessionId -> ProtocolValidation.WrongSession
    gameId != expected.gameId -> ProtocolValidation.WrongGame
    gameVersion != expected.gameVersion -> ProtocolValidation.IncompatibleGameVersion
    !messageId.isValidOpaqueId() -> ProtocolValidation.InvalidMessageId
    sequence < 0L -> ProtocolValidation.InvalidSequence
    else -> ProtocolValidation.Valid
}

fun PeerMessage.ClientCommand.validateFor(expected: SessionProtocol): ProtocolValidation {
    val headerResult = header.validateFor(expected)
    if (headerResult != ProtocolValidation.Valid) return headerResult
    return when {
        commandId != header.messageId || !commandId.isValidOpaqueId() ->
            ProtocolValidation.InvalidMessageId
        clientSequence <= 0L -> ProtocolValidation.InvalidSequence
        expectedRevision < 0L -> ProtocolValidation.InvalidSequence
        payload.size > MAX_COMMAND_PAYLOAD_BYTES -> ProtocolValidation.CommandPayloadTooLarge
        else -> ProtocolValidation.Valid
    }
}

fun HostMessage.PlayerSnapshot.validateFor(expected: SessionProtocol): ProtocolValidation {
    val headerResult = header.validateFor(expected)
    if (headerResult != ProtocolValidation.Valid) return headerResult
    return when {
        revision < 0L -> ProtocolValidation.InvalidSequence
        publicPayload.size > MAX_SNAPSHOT_PAYLOAD_BYTES ||
            privatePayload.size > MAX_SNAPSHOT_PAYLOAD_BYTES ->
            ProtocolValidation.SnapshotPayloadTooLarge
        else -> ProtocolValidation.Valid
    }
}

private fun String.isValidOpaqueId(): Boolean =
    length in 16..128 && all { it.isLetterOrDigit() || it == '-' || it == '_' }

