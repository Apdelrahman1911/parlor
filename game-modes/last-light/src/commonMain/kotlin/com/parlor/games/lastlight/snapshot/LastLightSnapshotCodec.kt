package com.parlor.games.lastlight.snapshot

import com.parlor.engine.snapshot.SnapshotCodec
import com.parlor.games.lastlight.domain.reducer.LastLightReducer
import com.parlor.games.lastlight.domain.state.LastLightState
import com.parlor.games.lastlight.domain.state.detached
import com.parlor.games.lastlight.protocol.LastLightWireFormat
import com.parlor.games.lastlight.random.LastLightRandom
import com.parlor.networking.protocol.MAX_SNAPSHOT_PAYLOAD_BYTES
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Local encrypted authority persistence only. Peers use LastLightProjectionCodec instead. */
class LastLightSnapshotCodec(json: Json = Json) : SnapshotCodec<LastLightState> {
    private val wire = LastLightWireFormat(json)
    private val validator = LastLightCanonicalStateValidator(LastLightReducer(LastLightRandom::forRound))

    override fun encode(state: LastLightState): ByteArray {
        validator.requireValid(state)
        return wire.encode(CanonicalEnvelope.serializer(), CanonicalEnvelope(state = state), MAX_SNAPSHOT_PAYLOAD_BYTES)
    }

    override fun decode(payload: ByteArray): LastLightState {
        val envelope = wire.decode(CanonicalEnvelope.serializer(), payload, MAX_SNAPSHOT_PAYLOAD_BYTES)
        require(envelope.schemaVersion == LastLightWireFormat.SCHEMA_VERSION) { "Unsupported Last Light snapshot version" }
        validator.requireValid(envelope.state)
        return envelope.state.detached()
    }

    @Serializable
    private data class CanonicalEnvelope(
        val schemaVersion: Int = LastLightWireFormat.SCHEMA_VERSION,
        val state: LastLightState,
    )
}
