package com.parlor.games.lastlight.protocol

import com.parlor.games.lastlight.domain.action.LastLightAction
import com.parlor.games.lastlight.domain.rules.LastLightCardIds
import com.parlor.games.lastlight.domain.rules.LastLightRules
import com.parlor.games.lastlight.domain.rules.LastLightSessionRules
import com.parlor.networking.protocol.MAX_COMMAND_PAYLOAD_BYTES
import kotlinx.serialization.Serializable

/** Game-local versioning and bounds; authenticated sender/revision/deduplication remain in SessionController. */
object LastLightActionCodec {
    private val wire = LastLightWireFormat()

    fun encode(action: LastLightAction): ByteArray {
        requireValid(action)
        return wire.encode(ActionEnvelope.serializer(), ActionEnvelope(action = action), MAX_COMMAND_PAYLOAD_BYTES)
    }

    fun decode(payload: ByteArray): LastLightAction {
        val envelope = wire.decode(ActionEnvelope.serializer(), payload, MAX_COMMAND_PAYLOAD_BYTES)
        require(envelope.schemaVersion == LastLightWireFormat.SCHEMA_VERSION) { "Unsupported Last Light action version" }
        requireValid(envelope.action)
        return envelope.action
    }

    private fun requireValid(action: LastLightAction) {
        val actor = when (action) {
            is LastLightAction.PlayCards -> action.by
            is LastLightAction.Challenge -> action.by
            is LastLightAction.MarkPlayerDisconnected -> action.playerId
            is LastLightAction.MarkPlayerReconnected -> action.playerId
            is LastLightAction.ContinueWithoutPlayer -> action.playerId
            else -> null
        }
        require(actor == null || LastLightSessionRules.isValidPlayerId(actor.raw)) { "Invalid Last Light action actor" }
        if (action is LastLightAction.PlayCards) {
            require(action.cardIds.size in 1..LastLightRules.MAX_PLAY_CARDS) { "Invalid Last Light selection size" }
            require(action.cardIds.distinct().size == action.cardIds.size) { "Repeated Last Light card identifier" }
            require(action.cardIds.all(LastLightCardIds::isValid)) { "Invalid Last Light card identifier" }
        }
    }

    @Serializable
    private data class ActionEnvelope(val schemaVersion: Int = LastLightWireFormat.SCHEMA_VERSION, val action: LastLightAction)
}
