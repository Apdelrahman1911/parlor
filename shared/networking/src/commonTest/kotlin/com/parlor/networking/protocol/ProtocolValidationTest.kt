package com.parlor.networking.protocol

import com.parlor.core.ids.GameId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.engine.state.Player
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
            ProtocolValidation.IncompatibleProtocol,
            header(
                protocol = ProtocolVersion(
                    PARLOR_PROTOCOL_MAJOR,
                    PARLOR_PROTOCOL_MINOR - 1,
                ),
            ).validateFor(session),
            "protocol 4.0 must not decode the required 4.1 host-identity fields",
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
    fun `rejects unsafe wire identities even when an initial peer trusts the supplied session`() {
        listOf(
            "session\nspoof",
            "session\u202Espoof",
            "session/../../spoof",
            "s".repeat(129),
        ).forEach { unsafeSessionId ->
            val supplied = session.copy(sessionId = SessionId(unsafeSessionId))
            assertEquals(
                ProtocolValidation.InvalidSessionIdentity,
                header(sessionId = supplied.sessionId).validateFor(supplied),
                "unsafe session id: ${unsafeSessionId.encodeToByteArray().contentToString()}",
            )
        }

        val unsafeGame = session.copy(gameId = GameId("game\nspoof"))
        assertEquals(
            ProtocolValidation.InvalidSessionIdentity,
            header(gameId = unsafeGame.gameId).validateFor(unsafeGame),
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
        assertEquals(
            ProtocolValidation.InvalidMessageId,
            valid.copy(
                commandId = "رسالة-0123456789abcdef",
                header = valid.header.copy(messageId = "رسالة-0123456789abcdef"),
            ).validateFor(session),
            "opaque correlation identifiers use canonical ASCII",
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
    fun `snapshot request permits only the pre-snapshot minus-one sentinel`() {
        val request = PeerMessage.SnapshotRequest(
            header = header(),
            actor = PlayerId("peer"),
            lastAppliedRevision = -1L,
        )

        assertEquals(ProtocolValidation.Valid, request.validateFor(session))
        assertEquals(
            ProtocolValidation.InvalidRevision,
            request.copy(lastAppliedRevision = -2L).validateFor(session),
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

    @Test
    fun `session start identity metadata and phase sequences are strict`() {
        val offer = HostMessage.SessionStarting(
            startId = MESSAGE_ID,
            caseId = "case",
            modeId = "classic",
            players = listOf(
                Player(PlayerId("host"), "Host", 0),
                Player(PlayerId("peer"), "Peer", 1),
            ),
            sessionNonce = 1L,
            header = header(messageId = MESSAGE_ID),
        )
        assertEquals(ProtocolValidation.Valid, offer.validateFor(session))
        assertEquals(
            ProtocolValidation.InvalidMessageId,
            offer.copy(startId = "different-start-012345678901").validateFor(session),
        )
        assertEquals(
            ProtocolValidation.InvalidSessionStart,
            offer.copy(players = offer.players + offer.players.last()).validateFor(session),
        )
        assertEquals(
            ProtocolValidation.InvalidSessionStart,
            offer.copy(
                players = offer.players.mapIndexed { index, player ->
                    if (index == 1) player.copy(displayName = "Host") else player
                },
            ).validateFor(session),
        )
        assertEquals(
            ProtocolValidation.InvalidSessionStart,
            offer.copy(
                players = offer.players.mapIndexed { index, player ->
                    player.copy(seat = index * 2)
                },
            ).validateFor(session),
        )
        assertEquals(
            ProtocolValidation.InvalidSessionStart,
            offer.copy(players = offer.players.reversed()).validateFor(session),
        )
        listOf("case/path", "case\nspoof", "case\u202Espoof", "c".repeat(129)).forEach {
            assertEquals(
                ProtocolValidation.InvalidSessionStart,
                offer.copy(caseId = it).validateFor(session),
                "unsafe case id: ${it.encodeToByteArray().contentToString()}",
            )
        }
        listOf("mode/path", "mode\nspoof", "mode\u202Espoof", "m".repeat(129)).forEach {
            assertEquals(
                ProtocolValidation.InvalidSessionStart,
                offer.copy(modeId = it).validateFor(session),
                "unsafe mode id: ${it.encodeToByteArray().contentToString()}",
            )
        }
        listOf("peer/path", "peer\nspoof", "peer\u202Espoof", "p".repeat(129)).forEach {
            assertEquals(
                ProtocolValidation.InvalidSessionStart,
                offer.copy(
                    players = offer.players.mapIndexed { index, player ->
                        if (index == 1) player.copy(id = PlayerId(it)) else player
                    },
                ).validateFor(session),
                "unsafe player id: ${it.encodeToByteArray().contentToString()}",
            )
        }
        listOf(
            "   ",
            "Alice\nAdmin",
            "Alice\u202EAdmin",
            "A".repeat(33),
        ).forEach { invalidName ->
            assertEquals(
                ProtocolValidation.InvalidSessionStart,
                offer.copy(
                    players = offer.players.mapIndexed { index, player ->
                        if (index == 1) player.copy(displayName = invalidName) else player
                    },
                ).validateFor(session),
                "invalid start display name: ${invalidName.encodeToByteArray().contentToString()}",
            )
        }
        assertEquals(
            ProtocolValidation.Valid,
            offer.copy(
                players = offer.players.mapIndexed { index, player ->
                    if (index == 1) player.copy(displayName = "عبد الرحمن 🎲") else player
                },
            ).validateFor(session),
        )
        val contentBound = offer.copy(
            caseVersion = "1.2.3",
            caseDigest = "a".repeat(64),
        )
        assertEquals(ProtocolValidation.Valid, contentBound.validateFor(session))
        assertEquals(
            ProtocolValidation.InvalidSessionStart,
            contentBound.copy(caseDigest = null).validateFor(session),
        )
        assertEquals(
            ProtocolValidation.InvalidSessionStart,
            contentBound.copy(caseVersion = "1.beta").validateFor(session),
        )
        assertEquals(
            ProtocolValidation.InvalidSessionStart,
            contentBound.copy(caseDigest = "A".repeat(64)).validateFor(session),
        )

        val commit = HostMessage.SessionStartCommitted(
            startId = MESSAGE_ID,
            header = header(sequence = 1L),
        )
        assertEquals(ProtocolValidation.Valid, commit.validateFor(session))
        assertEquals(
            ProtocolValidation.InvalidSequence,
            commit.copy(header = commit.header.copy(sequence = 0L)).validateFor(session),
        )
    }

    private fun header(
        protocol: ProtocolVersion = ProtocolVersion(),
        sessionId: SessionId = session.sessionId,
        gameId: GameId = session.gameId,
        gameVersion: Int = session.gameVersion,
        sequence: Long = 0,
        messageId: String = MESSAGE_ID,
    ) = SessionEnvelopeHeader(
        protocol = protocol,
        sessionId = sessionId,
        gameId = gameId,
        gameVersion = gameVersion,
        messageId = messageId,
        sequence = sequence,
    )

    private companion object {
        const val MESSAGE_ID = "0123456789abcdef0123456789abcdef"
    }
}
