package com.parlor.games.mafia.snapshot

import com.parlor.engine.snapshot.SnapshotCodec
import com.parlor.games.mafia.domain.state.MafiaState
import kotlinx.serialization.json.Json

/**
 * kotlinx.serialization-based codec for [MafiaState]. Module-local so adding
 * fields only versions the Mafia snapshot format, not the engine's.
 */
class MafiaSnapshotCodec(
    private val json: Json,
) : SnapshotCodec<MafiaState> {

    override fun encode(state: MafiaState): ByteArray =
        json.encodeToString(MafiaState.serializer(), state).encodeToByteArray()

    override fun decode(payload: ByteArray): MafiaState =
        json.decodeFromString(MafiaState.serializer(), payload.decodeToString())
}
