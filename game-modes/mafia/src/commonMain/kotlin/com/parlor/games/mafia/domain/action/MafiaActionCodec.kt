package com.parlor.games.mafia.domain.action

import com.parlor.networking.protocol.MAX_COMMAND_PAYLOAD_BYTES
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Codec for sending [MafiaAction] payloads over the wire as `ByteArray`.
 *
 * Mirrors [com.parlor.games.whodunit.domain.action.WhodunitActionCodec]: a peer
 * submits a `MafiaAction` to the host by wrapping the encoded bytes in
 * `PeerMessage.ClientCommand(payload)`; the shared coordinator authenticates,
 * orders, and acknowledges the command before the host decodes it to a typed
 * action and feeds it to the same reducer that pass-and-play uses.
 */
object MafiaActionCodec {

    private val json: Json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        encodeDefaults = true
    }

    fun encode(action: MafiaAction): ByteArray =
        json.encodeToString(MafiaAction.serializer(), action)
            .encodeToByteArray()
            .also(::requireBounded)

    fun decode(bytes: ByteArray): MafiaAction {
        requireBounded(bytes)
        val encoded = bytes.decodeToString(throwOnInvalidSequence = true)
        rejectRetiredAction(encoded)
        return json.decodeFromString(MafiaAction.serializer(), encoded)
    }

    private fun rejectRetiredAction(encoded: String) {
        val root = json.parseToJsonElement(encoded) as? JsonObject ?: return
        val actionType = root[TYPE_DISCRIMINATOR]?.jsonPrimitive?.contentOrNull
        if (actionType in RETIRED_ACTION_TYPES) {
            throw UnsupportedLegacyMafiaActionException()
        }
    }

    private fun requireBounded(bytes: ByteArray) {
        require(bytes.size <= MAX_COMMAND_PAYLOAD_BYTES) {
            "Mafia action exceeds $MAX_COMMAND_PAYLOAD_BYTES bytes"
        }
    }

    private const val TYPE_DISCRIMINATOR = "type"
    private val RETIRED_ACTION_TYPES = setOf(
        "com.parlor.games.mafia.domain.action.MafiaAction.AcknowledgePostGame",
        "com.parlor.games.mafia.domain.action.MafiaAction.ReadmitPlayer",
    )
}

internal class UnsupportedLegacyMafiaActionException : IllegalArgumentException(
    "Retired Mafia actions are not supported by the shipping game rules",
)
