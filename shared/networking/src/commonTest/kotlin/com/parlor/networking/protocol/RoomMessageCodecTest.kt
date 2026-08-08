package com.parlor.networking.protocol

import com.parlor.core.ids.GameId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import kotlin.test.Test
import kotlin.test.assertContentEquals
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
            publicPayload = payload,
            privatePayload = byteArrayOf(),
        )

        val encoded = codec.encode(message)
        val decoded = assertIs<HostMessage.PlayerSnapshot>(codec.decode(encoded))

        assertTrue(encoded.size <= MAX_ROOM_FRAME_BYTES)
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
            HostMessage.AdmissionOffered(offer),
            HostMessage.AdmissionCommitted(offer.playerId, offer.offerId, offer.generation),
            HostMessage.ResumeOffered(offer),
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

    private fun header() = SessionEnvelopeHeader(
        protocol = ProtocolVersion(),
        sessionId = SessionId("session-0123456789"),
        gameId = GameId("fixture-game"),
        gameVersion = 1,
        messageId = "message-01234567890123456789",
        sequence = 1,
    )
}
