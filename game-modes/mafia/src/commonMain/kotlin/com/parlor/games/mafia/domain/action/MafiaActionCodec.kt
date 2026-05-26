package com.parlor.games.mafia.domain.action

import kotlinx.serialization.json.Json

/**
 * Codec for sending [MafiaAction] payloads over the wire as `ByteArray`.
 *
 * Mirrors [com.parlor.games.whodunit.domain.action.WhodunitActionCodec]: a peer
 * submits a `MafiaAction` to the host by wrapping the encoded bytes in
 * `PeerMessage.ActionSubmit(payload)`; the host decodes back to a typed action
 * and feeds it to the same reducer that pass-and-play uses.
 */
object MafiaActionCodec {

    private val json: Json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        encodeDefaults = true
    }

    fun encode(action: MafiaAction): ByteArray =
        json.encodeToString(MafiaAction.serializer(), action).encodeToByteArray()

    fun decode(bytes: ByteArray): MafiaAction =
        json.decodeFromString(MafiaAction.serializer(), bytes.decodeToString())
}
