package com.parlor.networking.protocol

import com.parlor.core.ids.GameId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import kotlin.test.Test
import kotlin.test.assertEquals

class ProtocolValidationTest {
    private val session = SessionProtocol(
        sessionId = SessionId("session-1234567890"),
        gameId = GameId("test-game"),
        gameVersion = 3,
    )

    @Test
    fun `rejects incompatible protocol game and session`() {
        assertEquals(
            ProtocolValidation.IncompatibleProtocol,
            header(protocol = ProtocolVersion(PARLOR_PROTOCOL_MAJOR + 1, 0)).validateFor(session),
        )
        assertEquals(
            ProtocolValidation.IncompatibleProtocol,
            header(
                protocol = ProtocolVersion(
                    PARLOR_PROTOCOL_MAJOR,
                    PARLOR_PROTOCOL_MINOR + 1,
                ),
            ).validateFor(session),
        )
        assertEquals(
            ProtocolValidation.WrongGame,
            header(gameId = GameId("other-game")).validateFor(session),
        )
        assertEquals(
            ProtocolValidation.WrongSession,
            header(sessionId = SessionId("other-session-123")).validateFor(session),
        )
        assertEquals(
            ProtocolValidation.IncompatibleGameVersion,
            header(gameVersion = 4).validateFor(session),
        )
    }

    @Test
    fun `enforces command identifiers sequence revision and payload cap`() {
        val valid = PeerMessage.ClientCommand(
            header = header(),
            actor = PlayerId("peer"),
            commandId = MESSAGE_ID,
            clientSequence = 1,
            expectedRevision = 0,
            payload = ByteArray(MAX_COMMAND_PAYLOAD_BYTES),
        )
        assertEquals(ProtocolValidation.Valid, valid.validateFor(session))
        assertEquals(
            ProtocolValidation.CommandPayloadTooLarge,
            valid.copy(payload = ByteArray(MAX_COMMAND_PAYLOAD_BYTES + 1)).validateFor(session),
        )
        assertEquals(
            ProtocolValidation.InvalidSequence,
            valid.copy(clientSequence = 0).validateFor(session),
        )
        assertEquals(
            ProtocolValidation.InvalidMessageId,
            valid.copy(commandId = "different-command-id").validateFor(session),
        )
    }

    @Test
    fun `snapshot is atomic and independently caps public and private slices`() {
        val snapshot = HostMessage.PlayerSnapshot(
            header = header(sequence = 8),
            revision = 8,
            publicPayload = ByteArray(MAX_SNAPSHOT_PAYLOAD_BYTES / 2),
            privatePayload = ByteArray(MAX_SNAPSHOT_PAYLOAD_BYTES / 2),
        )
        assertEquals(ProtocolValidation.Valid, snapshot.validateFor(session))
        assertEquals(
            ProtocolValidation.SnapshotPayloadTooLarge,
            snapshot.copy(privatePayload = ByteArray(MAX_SNAPSHOT_PAYLOAD_BYTES / 2 + 1))
                .validateFor(session),
        )
    }

    @Test
    fun `validates epoch and control message semantics`() {
        assertEquals(
            ProtocolValidation.WrongConnectionEpoch,
            header().copy(connectionEpoch = 2L).validateFor(session),
        )
        assertEquals(
            ProtocolValidation.InvalidRevision,
            HostMessage.CommandResult(
                header = header(sequence = 1),
                commandId = MESSAGE_ID,
                status = CommandStatus.Applied,
                authoritativeRevision = -1,
            ).validateFor(session),
        )
        assertEquals(
            ProtocolValidation.InvalidRevision,
            HostMessage.Heartbeat(
                header = header(sequence = 2),
                authoritativeRevision = -1,
            ).validateFor(session),
        )
        assertEquals(
            ProtocolValidation.InvalidRevision,
            HostMessage.SessionEnded(
                header = header(sequence = 3),
                reason = SessionEndReason.HostLeft,
                finalRevision = -1,
            ).validateFor(session),
        )
    }

    private fun header(
        protocol: ProtocolVersion = ProtocolVersion(),
        sessionId: SessionId = session.sessionId,
        gameId: GameId = session.gameId,
        gameVersion: Int = session.gameVersion,
        sequence: Long = 0,
    ) = SessionEnvelopeHeader(
        protocol = protocol,
        sessionId = sessionId,
        gameId = gameId,
        gameVersion = gameVersion,
        messageId = MESSAGE_ID,
        sequence = sequence,
    )

    private companion object {
        const val MESSAGE_ID = "0123456789abcdef0123456789abcdef"
    }
}
