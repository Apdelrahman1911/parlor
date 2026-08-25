package com.parlor.networking.protocol

import com.parlor.core.ids.GameId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.engine.state.Player
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
class RoomMessageCodecTest {
    private val codec = RoomMessageCodec()

    @Test
    fun `round trips binary snapshot without JSON byte expansion`() {
        val payload = ByteArray(MAX_SNAPSHOT_PAYLOAD_BYTES) { (it % 251).toByte() }
        val message = HostMessage.PlayerSnapshot(
            header = header(),
            revision = 1,
            nextExpectedClientSequence = 7L,
            publicPayload = payload,
            privatePayload = byteArrayOf(),
        )

        val encoded = codec.encode(message)
        val decoded = assertIs<HostMessage.PlayerSnapshot>(codec.decode(encoded))

        assertTrue(encoded.size <= MAX_ROOM_FRAME_BYTES)
        assertTrue(decoded.nextExpectedClientSequence == 7L)
        assertContentEquals(payload, decoded.publicPayload)
        assertContentEquals(byteArrayOf(), decoded.privatePayload)
    }

    @Test
    fun `rejects oversized encoded input before decode`() {
        assertFailsWith<IllegalArgumentException> {
            codec.decode(ByteArray(MAX_ROOM_FRAME_BYTES + 1))
        }
    }

    @Test
    fun `registered room message variants match the complete wire allowlist`() {
        val sealedValueDescriptor = RoomMessage.serializer().descriptor.getElementDescriptor(1)
        val registeredVariants = (0 until sealedValueDescriptor.elementsCount)
            .map(sealedValueDescriptor::getElementName)
            .toSet()

        val expectedVariants = setOf(
            "com.parlor.networking.protocol.HostMessage.AdmissionAccepted",
            "com.parlor.networking.protocol.HostMessage.AdmissionCommitted",
            "com.parlor.networking.protocol.HostMessage.AdmissionOffered",
            "com.parlor.networking.protocol.HostMessage.AdmissionPending",
            "com.parlor.networking.protocol.HostMessage.AdmissionRejected",
            "com.parlor.networking.protocol.HostMessage.CommandResult",
            "com.parlor.networking.protocol.HostMessage.Heartbeat",
            "com.parlor.networking.protocol.HostMessage.PlayerSnapshot",
            "com.parlor.networking.protocol.HostMessage.ResumeCommitted",
            "com.parlor.networking.protocol.HostMessage.ResumeOffered",
            "com.parlor.networking.protocol.HostMessage.SessionEnded",
            "com.parlor.networking.protocol.HostMessage.SessionStartCommitted",
            "com.parlor.networking.protocol.HostMessage.SessionStarting",
            "com.parlor.networking.protocol.PeerMessage.AdmissionCommitAck",
            "com.parlor.networking.protocol.PeerMessage.AdmissionConfirmed",
            "com.parlor.networking.protocol.PeerMessage.AdmissionReady",
            "com.parlor.networking.protocol.PeerMessage.AdmissionRequest",
            "com.parlor.networking.protocol.PeerMessage.ClientCommand",
            "com.parlor.networking.protocol.PeerMessage.CommandOutcomeRequest",
            "com.parlor.networking.protocol.PeerMessage.LeaveNotice",
            "com.parlor.networking.protocol.PeerMessage.ResumeCommitAck",
            "com.parlor.networking.protocol.PeerMessage.ResumeConfirmed",
            "com.parlor.networking.protocol.PeerMessage.ResumeReady",
            "com.parlor.networking.protocol.PeerMessage.ResumeRequested",
            "com.parlor.networking.protocol.PeerMessage.SessionHeartbeat",
            "com.parlor.networking.protocol.PeerMessage.SessionStartCommitAck",
            "com.parlor.networking.protocol.PeerMessage.SessionStartReady",
            "com.parlor.networking.protocol.PeerMessage.SnapshotRequest",
        )

        assertEquals(
            expectedVariants,
            registeredVariants,
            "Wire variants changed; review protocol compatibility and versioning before updating this allowlist",
        )
    }

    @Test
    fun `player snapshot matches its golden wire vector`() {
        assertGoldenVector(
            HostMessage.PlayerSnapshot(
                header = goldenHeader(),
                revision = 3,
                nextExpectedClientSequence = 4,
                publicPayload = byteArrayOf(0x00, 0x01, 0xff.toByte()),
                privatePayload = byteArrayOf(0x02),
            ),
            GOLDEN_PLAYER_SNAPSHOT,
        )
    }

    @Test
    fun `session starting matches its golden wire vector`() {
        assertGoldenVector(
            HostMessage.SessionStarting(
                startId = "start",
                caseId = "case",
                modeId = "mode",
                players = listOf(Player(PlayerId("p"), "P", 0)),
                sessionNonce = 5,
                header = goldenHeader(),
            ),
            GOLDEN_SESSION_STARTING,
        )
    }

    @Test
    fun `client command matches its golden wire vector`() {
        assertGoldenVector(
            PeerMessage.ClientCommand(
                header = goldenHeader(),
                actor = PlayerId("p"),
                commandId = "c",
                clientSequence = 6,
                expectedRevision = 7,
                payload = byteArrayOf(0x10, 0x20, 0xff.toByte()),
            ),
            GOLDEN_CLIENT_COMMAND,
        )
    }

    @Test
    fun `admission offered matches its golden wire vector`() {
        assertGoldenVector(
            HostMessage.AdmissionOffered(
                offer = ResumableCredentialOffer(
                    offerId = "o",
                    playerId = PlayerId("p"),
                    hostPeerId = "h",
                    hostFingerprint = "f",
                    secret = "s",
                    generation = 2,
                    issuedAtEpochMillis = 3,
                    expiresAtEpochMillis = 4,
                    gameId = "g",
                    gameVersion = 1,
                ),
                hostDisplayName = "H",
            ),
            GOLDEN_ADMISSION_OFFERED,
        )
    }

    @Test
    fun `round trips every transactional admission and resume frame`() {
        val offer = ResumableCredentialOffer(
            offerId = "0123456789abcdef0123456789abcdef",
            playerId = PlayerId("alice-pid"),
            hostPeerId = "host-pid",
            hostFingerprint = "p2f1-zlmerarbaugm753v5mvipavkkhwxbvlu3cpx4unzvuvov7zu7dkq",
            secret = "a".repeat(64),
            generation = 2,
            issuedAtEpochMillis = 1_000,
            expiresAtEpochMillis = 100_000,
            gameId = "whodunit",
            gameVersion = 1,
        )
        val frames: List<RoomMessage> = listOf(
            HostMessage.AdmissionOffered(offer, "Host"),
            HostMessage.AdmissionPending(offer.playerId),
            HostMessage.AdmissionCommitted(offer.playerId, offer.offerId, offer.generation),
            HostMessage.AdmissionRejected(AdmissionRejection.DisplayNameInUse),
            HostMessage.ResumeOffered(offer, "Host"),
            HostMessage.ResumeCommitted(offer.playerId, offer.offerId, offer.generation),
            PeerMessage.AdmissionConfirmed(offer.playerId, offer.offerId, offer.generation),
            PeerMessage.AdmissionCommitAck(offer.playerId, offer.offerId, offer.generation),
            PeerMessage.AdmissionReady(offer.playerId, offer.offerId, offer.generation),
            PeerMessage.ResumeRequested(
                protocol = ProtocolVersion(),
                actor = offer.playerId,
                roomCode = "ABC234",
                displayName = "Alice",
                secret = offer.secret,
                generation = 1,
            ),
            PeerMessage.ResumeConfirmed(offer.playerId, offer.offerId, offer.generation),
            PeerMessage.ResumeCommitAck(offer.playerId, offer.offerId, offer.generation),
            PeerMessage.ResumeReady(offer.playerId, offer.offerId, offer.generation),
        )

        frames.forEach { frame ->
            kotlin.test.assertEquals(frame, codec.decode(codec.encode(frame)))
        }
    }

    @Test
    fun `round trips every acknowledged session-start frame`() {
        val startId = "start-012345678901234567890123"
        val startHeader = header().copy(messageId = startId, sequence = 0L)
        val ackHeader = header().copy(
            messageId = "acknowledgement-012345678901",
            sequence = 0L,
        )
        val frames: List<RoomMessage> = listOf(
            HostMessage.SessionStarting(
                startId = startId,
                caseId = "fixture-case",
                modeId = "classic",
                players = listOf(
                    com.parlor.engine.state.Player(PlayerId("host"), "Host", 0),
                    com.parlor.engine.state.Player(PlayerId("alice"), "Alice", 1),
                ),
                sessionNonce = 42L,
                header = startHeader,
            ),
            PeerMessage.SessionStartReady(
                header = ackHeader,
                actor = PlayerId("alice"),
                startId = startId,
            ),
            HostMessage.SessionStartCommitted(
                startId = startId,
                header = header().copy(
                    messageId = "commit-0123456789012345678901",
                    sequence = 1L,
                ),
            ),
            PeerMessage.SessionStartCommitAck(
                header = ackHeader.copy(messageId = "commit-ack-01234567890123456"),
                actor = PlayerId("alice"),
                startId = startId,
            ),
        )

        frames.forEach { frame ->
            kotlin.test.assertEquals(frame, codec.decode(codec.encode(frame)))
        }
    }

    private fun header() = SessionEnvelopeHeader(
        protocol = ProtocolVersion(),
        sessionId = SessionId("session-0123456789"),
        gameId = GameId("fixture-game"),
        gameVersion = 1,
        messageId = "message-01234567890123456789",
        sequence = 1,
    )

    private fun goldenHeader() = SessionEnvelopeHeader(
        protocol = ProtocolVersion(),
        sessionId = SessionId("s"),
        gameId = GameId("g"),
        gameVersion = 1,
        messageId = "m",
        sequence = 2,
    )

    private fun assertGoldenVector(expected: RoomMessage, expectedHex: String) {
        assertEquals(
            expectedHex,
            codec.encode(expected).toHex(),
            "Wire bytes changed; review protocol compatibility and versioning before updating this vector",
        )
        assertRoomMessagesEqual(expected, codec.decode(expectedHex.hexToByteArray()))
    }

    private fun assertRoomMessagesEqual(expected: RoomMessage, actual: RoomMessage) {
        when (expected) {
            is HostMessage.PlayerSnapshot -> {
                val actualSnapshot = assertIs<HostMessage.PlayerSnapshot>(actual)
                assertEquals(expected.header, actualSnapshot.header)
                assertEquals(expected.revision, actualSnapshot.revision)
                assertEquals(expected.nextExpectedClientSequence, actualSnapshot.nextExpectedClientSequence)
                assertContentEquals(expected.publicPayload, actualSnapshot.publicPayload)
                assertContentEquals(expected.privatePayload, actualSnapshot.privatePayload)
            }

            is PeerMessage.ClientCommand -> {
                val actualCommand = assertIs<PeerMessage.ClientCommand>(actual)
                assertEquals(expected.header, actualCommand.header)
                assertEquals(expected.actor, actualCommand.actor)
                assertEquals(expected.commandId, actualCommand.commandId)
                assertEquals(expected.clientSequence, actualCommand.clientSequence)
                assertEquals(expected.expectedRevision, actualCommand.expectedRevision)
                assertContentEquals(expected.payload, actualCommand.payload)
            }

            else -> assertEquals(expected, actual)
        }
    }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { byte ->
        byte.toUByte().toString(radix = 16).padStart(length = 2, padChar = '0')
    }

    private fun String.hexToByteArray(): ByteArray {
        require(length % 2 == 0) { "Hex input must contain complete bytes" }
        return ByteArray(length / 2) { index ->
            substring(index * 2, index * 2 + 2).toInt(radix = 16).toByte()
        }
    }

    private companion object {
        // Protocol 4.2 vectors. Change only after deliberate wire compatibility and version review.
        const val GOLDEN_PLAYER_SNAPSHOT =
            "9f7839636f6d2e7061726c6f722e6e6574776f726b696e672e70726f746f636f6c2e486f73744d6573736167652e506c" +
                "61796572536e617073686f74bf66686561646572bf6870726f746f636f6cbf656d616a6f7204656d696e6f7202ff6973" +
                "657373696f6e496461736667616d65496461676b67616d6556657273696f6e01696d6573736167654964616d68736571" +
                "75656e6365026f636f6e6e656374696f6e45706f636801ff687265766973696f6e03781a6e6578744578706563746564" +
                "436c69656e7453657175656e6365046d7075626c69635061796c6f6164430001ff6e707269766174655061796c6f6164" +
                "4102ffff"

        const val GOLDEN_SESSION_STARTING =
            "9f783a636f6d2e7061726c6f722e6e6574776f726b696e672e70726f746f636f6c2e486f73744d6573736167652e5365" +
                "7373696f6e5374617274696e67bf6773746172744964657374617274666361736549646463617365666d6f6465496464" +
                "6d6f646567706c61796572739fbf62696461706b646973706c61794e616d656150647365617400ffff6c73657373696f" +
                "6e4e6f6e63650566686561646572bf6870726f746f636f6cbf656d616a6f7204656d696e6f7202ff6973657373696f6e" +
                "496461736667616d65496461676b67616d6556657273696f6e01696d6573736167654964616d6873657175656e636502" +
                "6f636f6e6e656374696f6e45706f636801ff6b6361736556657273696f6ef66a63617365446967657374f6ffff"

        const val GOLDEN_CLIENT_COMMAND =
            "9f7838636f6d2e7061726c6f722e6e6574776f726b696e672e70726f746f636f6c2e506565724d6573736167652e436c" +
                "69656e74436f6d6d616e64bf66686561646572bf6870726f746f636f6cbf656d616a6f7204656d696e6f7202ff697365" +
                "7373696f6e496461736667616d65496461676b67616d6556657273696f6e01696d6573736167654964616d6873657175" +
                "656e6365026f636f6e6e656374696f6e45706f636801ff656163746f72617069636f6d6d616e64496461636e636c6965" +
                "6e7453657175656e6365067065787065637465645265766973696f6e07677061796c6f6164431020ffffff"

        const val GOLDEN_ADMISSION_OFFERED =
            "9f783b636f6d2e7061726c6f722e6e6574776f726b696e672e70726f746f636f6c2e486f73744d6573736167652e4164" +
                "6d697373696f6e4f666665726564bf656f66666572bf676f666665724964616f68706c61796572496461706a686f7374" +
                "50656572496461686f686f737446696e6765727072696e7461666673656372657461736a67656e65726174696f6e0273" +
                "697373756564417445706f63684d696c6c6973037465787069726573417445706f63684d696c6c6973046667616d6549" +
                "6461676b67616d6556657273696f6e01ff6f686f7374446973706c61794e616d656148ffff"
    }
}
