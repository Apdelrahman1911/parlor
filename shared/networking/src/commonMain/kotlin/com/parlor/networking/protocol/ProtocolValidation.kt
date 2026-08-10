package com.parlor.networking.protocol

import com.parlor.core.versioning.SemVer

import com.parlor.core.ids.GameId
import com.parlor.core.ids.SessionId
import com.parlor.networking.room.RoomInputPolicy

/** The immutable identity/version tuple for one authoritative game session. */
data class SessionProtocol(
    val sessionId: SessionId,
    val gameId: GameId,
    val gameVersion: Int,
    val protocol: ProtocolVersion = ProtocolVersion(),
    val connectionEpoch: Long = 1L,
    /** Peer-side id of the authoritative start barrier; not part of headers. */
    val startId: String? = null,
)

sealed interface ProtocolValidation {
    data object Valid : ProtocolValidation
    data object IncompatibleProtocol : ProtocolValidation
    data object WrongSession : ProtocolValidation
    data object WrongGame : ProtocolValidation
    data object IncompatibleGameVersion : ProtocolValidation
    data object InvalidMessageId : ProtocolValidation
    data object InvalidSequence : ProtocolValidation
    data object WrongConnectionEpoch : ProtocolValidation
    data object InvalidRevision : ProtocolValidation
    data object CommandPayloadTooLarge : ProtocolValidation
    data object SnapshotPayloadTooLarge : ProtocolValidation
    /** Envelope is valid, but the game-specific snapshot payload cannot be installed. */
    data object SnapshotPayloadInvalid : ProtocolValidation
    data object InvalidSessionStart : ProtocolValidation
}

fun SessionEnvelopeHeader.validateFor(expected: SessionProtocol): ProtocolValidation = when {
    !protocol.isCompatibleWith(expected.protocol) -> ProtocolValidation.IncompatibleProtocol
    sessionId != expected.sessionId -> ProtocolValidation.WrongSession
    gameId != expected.gameId -> ProtocolValidation.WrongGame
    gameVersion != expected.gameVersion -> ProtocolValidation.IncompatibleGameVersion
    !messageId.isValidOpaqueId() -> ProtocolValidation.InvalidMessageId
    sequence < 0L -> ProtocolValidation.InvalidSequence
    connectionEpoch <= 0L -> ProtocolValidation.InvalidSequence
    connectionEpoch != expected.connectionEpoch -> ProtocolValidation.WrongConnectionEpoch
    else -> ProtocolValidation.Valid
}

fun PeerMessage.ClientCommand.validateFor(expected: SessionProtocol): ProtocolValidation {
    val headerResult = header.validateFor(expected)
    if (headerResult != ProtocolValidation.Valid) return headerResult
    return when {
        commandId != header.messageId || !commandId.isValidOpaqueId() ->
            ProtocolValidation.InvalidMessageId
        clientSequence <= 0L -> ProtocolValidation.InvalidSequence
        expectedRevision < 0L -> ProtocolValidation.InvalidRevision
        payload.size > MAX_COMMAND_PAYLOAD_BYTES -> ProtocolValidation.CommandPayloadTooLarge
        else -> ProtocolValidation.Valid
    }
}

fun PeerMessage.SnapshotRequest.validateFor(expected: SessionProtocol): ProtocolValidation {
    val headerResult = header.validateFor(expected)
    if (headerResult != ProtocolValidation.Valid) return headerResult
    // -1 is the explicit "no authoritative snapshot installed" sentinel.
    // It lets a newly committed peer request revision zero without pretending
    // that its locally constructed placeholder state came from the host.
    return if (lastAppliedRevision < -1L) {
        ProtocolValidation.InvalidRevision
    } else {
        ProtocolValidation.Valid
    }
}

fun PeerMessage.SessionHeartbeat.validateFor(expected: SessionProtocol): ProtocolValidation {
    val headerResult = header.validateFor(expected)
    if (headerResult != ProtocolValidation.Valid) return headerResult
    return if (lastAppliedRevision < 0L) {
        ProtocolValidation.InvalidRevision
    } else {
        ProtocolValidation.Valid
    }
}

fun PeerMessage.CommandOutcomeRequest.validateFor(expected: SessionProtocol): ProtocolValidation {
    val headerResult = header.validateFor(expected)
    if (headerResult != ProtocolValidation.Valid) return headerResult
    return if (commandId != header.messageId || !commandId.isValidOpaqueId()) {
        ProtocolValidation.InvalidMessageId
    } else {
        ProtocolValidation.Valid
    }
}

fun HostMessage.PlayerSnapshot.validateFor(expected: SessionProtocol): ProtocolValidation {
    val headerResult = header.validateFor(expected)
    if (headerResult != ProtocolValidation.Valid) return headerResult
    return when {
        revision < 0L -> ProtocolValidation.InvalidRevision
        publicPayload.size.toLong() + privatePayload.size.toLong() >
            MAX_SNAPSHOT_PAYLOAD_BYTES.toLong() ->
            ProtocolValidation.SnapshotPayloadTooLarge
        else -> ProtocolValidation.Valid
    }
}

fun HostMessage.CommandResult.validateFor(expected: SessionProtocol): ProtocolValidation {
    val headerResult = header.validateFor(expected)
    if (headerResult != ProtocolValidation.Valid) return headerResult
    return when {
        !commandId.isValidOpaqueId() -> ProtocolValidation.InvalidMessageId
        authoritativeRevision < 0L -> ProtocolValidation.InvalidRevision
        nextExpectedClientSequence <= 0L -> ProtocolValidation.InvalidSequence
        else -> ProtocolValidation.Valid
    }
}

fun HostMessage.Heartbeat.validateFor(expected: SessionProtocol): ProtocolValidation {
    val headerResult = header.validateFor(expected)
    if (headerResult != ProtocolValidation.Valid) return headerResult
    return if (authoritativeRevision < 0L) {
        ProtocolValidation.InvalidRevision
    } else {
        ProtocolValidation.Valid
    }
}

fun HostMessage.SessionEnded.validateFor(expected: SessionProtocol): ProtocolValidation {
    val headerResult = header.validateFor(expected)
    if (headerResult != ProtocolValidation.Valid) return headerResult
    return if (finalRevision < 0L) {
        ProtocolValidation.InvalidRevision
    } else {
        ProtocolValidation.Valid
    }
}

fun HostMessage.SessionStarting.validateFor(expected: SessionProtocol): ProtocolValidation {
    val headerResult = header.validateFor(expected)
    if (headerResult != ProtocolValidation.Valid) return headerResult
    return when {
        startId != header.messageId || !startId.isValidOpaqueId() ->
            ProtocolValidation.InvalidMessageId
        header.sequence != 0L -> ProtocolValidation.InvalidSequence
        caseId.length !in 1..MAX_START_TEXT_LENGTH ||
            modeId.length !in 1..MAX_START_TEXT_LENGTH ->
            ProtocolValidation.InvalidSessionStart
        players.size !in MIN_START_PLAYERS..MAX_START_PLAYERS ->
            ProtocolValidation.InvalidSessionStart
        players.map { it.id }.distinct().size != players.size ||
            players.map { it.seat } != players.indices.toList() ||
            players.any {
                it.id.raw.length !in 1..MAX_START_TEXT_LENGTH ||
                !RoomInputPolicy.isValidDisplayName(it.displayName) ||
                    it.seat < 0
            } -> ProtocolValidation.InvalidSessionStart
        (caseVersion == null) != (caseDigest == null) ->
            ProtocolValidation.InvalidSessionStart
        caseVersion != null && !caseVersion.isValidCaseVersion() ->
            ProtocolValidation.InvalidSessionStart
        caseDigest != null && !caseDigest.isValidSha256() ->
            ProtocolValidation.InvalidSessionStart
        else -> ProtocolValidation.Valid
    }
}

fun HostMessage.SessionStartCommitted.validateFor(
    expected: SessionProtocol,
): ProtocolValidation {
    val headerResult = header.validateFor(expected)
    if (headerResult != ProtocolValidation.Valid) return headerResult
    return when {
        !startId.isValidOpaqueId() -> ProtocolValidation.InvalidMessageId
        header.sequence <= 0L -> ProtocolValidation.InvalidSequence
        else -> ProtocolValidation.Valid
    }
}

fun PeerMessage.SessionStartReady.validateFor(expected: SessionProtocol): ProtocolValidation =
    validateSessionStartAcknowledgement(header, startId, expected)

fun PeerMessage.SessionStartCommitAck.validateFor(expected: SessionProtocol): ProtocolValidation =
    validateSessionStartAcknowledgement(header, startId, expected)

private fun validateSessionStartAcknowledgement(
    header: SessionEnvelopeHeader,
    startId: String,
    expected: SessionProtocol,
): ProtocolValidation {
    val headerResult = header.validateFor(expected)
    if (headerResult != ProtocolValidation.Valid) return headerResult
    return when {
        !startId.isValidOpaqueId() -> ProtocolValidation.InvalidMessageId
        header.sequence != 0L -> ProtocolValidation.InvalidSequence
        else -> ProtocolValidation.Valid
    }
}

private fun String.isValidOpaqueId(): Boolean =
    length in 16..128 && all { it.isLetterOrDigit() || it == '-' || it == '_' }

private fun String.isValidCaseVersion(): Boolean {
    if (length > MAX_CASE_VERSION_LENGTH) return false
    return try {
        SemVer.parse(this)
        true
    } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
        false
    }
}

private fun String.isValidSha256(): Boolean =
    length == SHA_256_HEX_LENGTH && all { it in '0'..'9' || it in 'a'..'f' }

private const val MIN_START_PLAYERS = 2
private const val MAX_START_PLAYERS = 16
private const val MAX_START_TEXT_LENGTH = 128
private const val MAX_CASE_VERSION_LENGTH = 32
private const val SHA_256_HEX_LENGTH = 64
