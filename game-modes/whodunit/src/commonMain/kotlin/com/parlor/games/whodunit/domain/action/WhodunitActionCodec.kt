package com.parlor.games.whodunit.domain.action

import com.parlor.networking.protocol.MAX_COMMAND_PAYLOAD_BYTES
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Codec for sending [WhodunitAction] payloads over the wire as `ByteArray`.
 *
 * Used by the multi-device path: a peer submits a `WhodunitAction` to the
 * host by wrapping the encoded bytes in
 * `com.parlor.networking.protocol.PeerMessage.ClientCommand(payload)`. The
 * shared coordinator authenticates, orders, and acknowledges the command;
 * the host then decodes back to a typed `WhodunitAction` and feeds it to the
 * same reducer that pass-and-play uses.
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

    /** Encode an action to JSON bytes suitable for `PeerMessage.ClientCommand.payload`. */
    fun encode(action: WhodunitAction): ByteArray =
        json.encodeToString(WhodunitAction.serializer(), action.requireValidRevealGeneration())
            .encodeToByteArray()
            .also(::requireBounded)

    /** Decode a `PeerMessage.ClientCommand.payload` back to a typed action. */
    fun decode(bytes: ByteArray): WhodunitAction {
        requireBounded(bytes)
        val encoded = bytes.decodeToString()
        rejectRetiredAction(encoded)
        return json.decodeFromString(WhodunitAction.serializer(), encoded)
            .requireValidRevealGeneration()
    }

    /**
     * The pre-v4 game-action contract advertised structured actions even though
     * its reducer silently ignored them. They are deliberately absent from the
     * v4 action model. Recognising the retired discriminator here turns an old payload
     * into an explicit, auditable InvalidAction result instead of relying on
     * an incidental unknown-subclass serialization error.
     */
    private fun rejectRetiredAction(encoded: String) {
        val root = json.parseToJsonElement(encoded) as? JsonObject ?: return
        val actionType = root[TYPE_DISCRIMINATOR]?.jsonPrimitive?.contentOrNull
        if (actionType == RETIRED_STRUCTURED_ACTION_TYPE) {
            throw UnsupportedLegacyWhodunitActionException()
        }
    }

    private fun requireBounded(bytes: ByteArray) {
        require(bytes.size <= MAX_COMMAND_PAYLOAD_BYTES) {
            "Whodunit action exceeds $MAX_COMMAND_PAYLOAD_BYTES bytes"
        }
    }

    private fun WhodunitAction.requireValidRevealGeneration(): WhodunitAction = also {
        val generation = when (this) {
            is WhodunitAction.StartCharacterReveal -> roleAssignmentGeneration
            is WhodunitAction.CompleteCharacterReveal -> roleAssignmentGeneration
            is WhodunitAction.OpenPrivateReview -> roleAssignmentGeneration
            is WhodunitAction.CloseHide -> roleAssignmentGeneration
            is WhodunitAction.ConfirmRoleViewed -> roleAssignmentGeneration
            else -> return@also
        }
        require(generation > 0L) { "Reveal action has an invalid assignment generation" }
    }

    private const val TYPE_DISCRIMINATOR = "type"
    private const val RETIRED_STRUCTURED_ACTION_TYPE =
        "com.parlor.games.whodunit.domain.action.WhodunitAction.SubmitStructuredAction"
}

internal class UnsupportedLegacyWhodunitActionException : IllegalArgumentException(
    "Structured actions are not supported by the shipping Whodunit rules",
)
