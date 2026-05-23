package com.parlor.games.whodunit.snapshot

import com.parlor.engine.snapshot.SnapshotCodec
import com.parlor.games.whodunit.domain.state.WhodunitState
import kotlinx.serialization.json.Json

/**
 * kotlinx.serialization-based codec for [WhodunitState]. The codec is module-
 * local so adding fields to the state only versions the Whodunit snapshot
 * format, not the engine's.
 */
class WhodunitSnapshotCodec(
    private val json: Json,
) : SnapshotCodec<WhodunitState> {

    override fun encode(state: WhodunitState): ByteArray =
        json.encodeToString(WhodunitState.serializer(), state).encodeToByteArray()

    override fun decode(payload: ByteArray): WhodunitState =
        json.decodeFromString(WhodunitState.serializer(), payload.decodeToString())
}
