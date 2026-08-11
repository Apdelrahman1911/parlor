package com.parlor.networking.protocol

import com.parlor.core.ids.GameId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
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
    fun `obsolete pre-authoritative message variants are absent from the wire schema`() {
        val sealedValueDescriptor = RoomMessage.serializer().descriptor.getElementDescriptor(1)
        val registeredVariants = (0 until sealedValueDescriptor.elementsCount)
            .map(sealedValueDescriptor::getElementName)
            .joinToString(separator = "\n")

        listOf(
            "PublicStateSnapshot",
            "PublicStateDelta",
            "PrivateStateForPlayer",
            "EventBroadcast",
            "EventDirect",
            "TimerSync",
            "EndSession",
            "JoinRequest",
            "ActionSubmit",
            "PeerMessage.Heartbeat",
        ).forEach { obsoleteVariant ->
            assertFalse(
                obsoleteVariant in registeredVariants,
                "$obsoleteVariant must not remain decodable as a silent protocol-4 no-op",
            )
        }
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
}
