package com.parlor.networking.protocol

import com.parlor.core.ids.GameId
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

    private fun header() = SessionEnvelopeHeader(
        protocol = ProtocolVersion(),
        sessionId = SessionId("session-0123456789"),
        gameId = GameId("fixture-game"),
        gameVersion = 1,
        messageId = "message-01234567890123456789",
        sequence = 1,
    )
}
