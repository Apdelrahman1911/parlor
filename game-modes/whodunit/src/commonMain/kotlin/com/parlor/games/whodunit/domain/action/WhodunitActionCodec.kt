package com.parlor.games.whodunit.domain.action

import com.parlor.networking.protocol.MAX_COMMAND_PAYLOAD_BYTES
import kotlinx.serialization.json.Json

/**
 * Codec for sending [WhodunitAction] payloads over the wire as `ByteArray`.
 *
 * Used by the multi-device path: a peer submits a `WhodunitAction` to the
 * host by wrapping the encoded bytes in
 * `com.parlor.networking.protocol.PeerMessage.ActionSubmit(payload)`. The
 * host decodes back to a typed `WhodunitAction` and feeds it to the same
 * reducer that pass-and-play uses.
 *
 * The codec is intentionally JSON for now — easy to inspect on the wire,
 * easy to evolve via kotlinx-serialization's `@SerialName` / `@JsonNames`
 * facilities, and tied to the same `Json` config the snapshot codec and
 * room-protocol codec use so there's only one schema to reason about.
 * Phase 8+ may swap in a binary format (CBOR) once the protocol is stable;
 * the function surface here will not change.
 */
object WhodunitActionCodec {

    private val json: Json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        encodeDefaults = true
    }

    /** Encode an action to JSON bytes suitable for `PeerMessage.ActionSubmit.payload`. */
    fun encode(action: WhodunitAction): ByteArray =
        json.encodeToString(WhodunitAction.serializer(), action)
            .encodeToByteArray()
            .also(::requireBounded)

    /** Decode a `PeerMessage.ActionSubmit.payload` back to a typed action. */
    fun decode(bytes: ByteArray): WhodunitAction {
        requireBounded(bytes)
        return json.decodeFromString(WhodunitAction.serializer(), bytes.decodeToString())
    }

    private fun requireBounded(bytes: ByteArray) {
        require(bytes.size <= MAX_COMMAND_PAYLOAD_BYTES) {
            "Whodunit action exceeds $MAX_COMMAND_PAYLOAD_BYTES bytes"
        }
    }
}
