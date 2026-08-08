package com.parlor.networking.protocol

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray

/**
 * Compact, transport-independent room wire codec.
 *
 * Byte arrays are encoded as CBOR byte strings instead of JSON integer arrays,
 * so the frame limit is enforced against the bytes P2pKit actually sends.
 */
@OptIn(ExperimentalSerializationApi::class)
class RoomMessageCodec {
    private val cbor: Cbor = Cbor {
        encodeDefaults = true
        ignoreUnknownKeys = false
        alwaysUseByteString = true
    }

    fun encode(message: RoomMessage): ByteArray {
        val encoded = cbor.encodeToByteArray(RoomMessage.serializer(), message)
        require(encoded.size <= MAX_ROOM_FRAME_BYTES) {
            "Encoded room frame exceeds $MAX_ROOM_FRAME_BYTES bytes"
        }
        return encoded
    }

    fun decode(frame: ByteArray): RoomMessage {
        require(frame.size <= MAX_ROOM_FRAME_BYTES) {
            "Encoded room frame exceeds $MAX_ROOM_FRAME_BYTES bytes"
        }
        return cbor.decodeFromByteArray(RoomMessage.serializer(), frame)
    }
}
