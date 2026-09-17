package com.parlor.games.lastlight.protocol

import com.parlor.core.ids.PlayerId
import com.parlor.games.lastlight.domain.action.LastLightAction
import com.parlor.networking.protocol.MAX_COMMAND_PAYLOAD_BYTES
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith

class LastLightActionCodecTest {
    @Test
    fun actionVocabularyRoundTripsWithAStableVersionAndExplicitActor() {
        val self = PlayerId("player-1")
        val actions = listOf(
            LastLightAction.PlayCards(self, listOf("r1-c0", "r1-c10", "r1-c29")),
            LastLightAction.Challenge(self),
            LastLightAction.NextRound,
            LastLightAction.EndGame,
            LastLightAction.MarkPlayerDisconnected(self),
            LastLightAction.MarkPlayerReconnected(self),
            LastLightAction.ContinueWithoutPlayer(self),
        )
        actions.forEach { assertEquals(it, LastLightActionCodec.decode(LastLightActionCodec.encode(it))) }
        assertEquals(
            "{\"schemaVersion\":1,\"action\":{\"type\":\"next-round\"}}",
            LastLightActionCodec.encode(LastLightAction.NextRound).decodeToString(),
        )
    }

    @Test
    fun malformedCountsDuplicateOrForgedIdentifiersCannotCrossTheActionCodec() {
        val self = PlayerId("player-1")
        val invalidSelections = listOf(
            emptyList(),
            listOf("r1-c0", "r1-c1", "r1-c2", "r1-c3"),
            listOf("r1-c0", "r1-c0"),
            listOf("r0-c0"),
            listOf("r36-c0"),
            listOf("r1-c30"),
            listOf("r1-c01"),
            listOf("r1-c-1"),
            listOf("CROWN-secret"),
            listOf("x".repeat(1000)),
        )
        invalidSelections.forEach { cards ->
            assertFailsWith<IllegalArgumentException> { LastLightActionCodec.encode(LastLightAction.PlayCards(self, cards)) }
        }
        assertFailsWith<IllegalArgumentException> { LastLightActionCodec.encode(LastLightAction.Challenge(PlayerId("unsafe\n"))) }
        val duplicate = "{\"schemaVersion\":1,\"action\":{\"type\":\"play-cards\",\"by\":\"player-1\",\"cardIds\":[\"r1-c0\",\"r1-c0\"]}}"
        assertFails { LastLightActionCodec.decode(duplicate.encodeToByteArray()) }
    }

    @Test
    fun unknownActionFieldsVersionsDuplicateKeysMalformedUtf8AndPayloadLimitsFailClosed() {
        val valid = LastLightActionCodec.encode(LastLightAction.NextRound).decodeToString()
        val malformed = listOf(
            valid.replace("next-round", "reveal-every-hand"),
            valid.replace("\"schemaVersion\":1", "\"schemaVersion\":2"),
            valid.replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"extra\":true"),
            valid.replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"schemaVersion\":1"),
            " $valid",
            "",
            "{}",
        )
        malformed.forEach { assertFails { LastLightActionCodec.decode(it.encodeToByteArray()) } }
        assertFails { LastLightActionCodec.decode(byteArrayOf(0xc3.toByte())) }
        assertFailsWith<IllegalArgumentException> { LastLightActionCodec.decode(ByteArray(MAX_COMMAND_PAYLOAD_BYTES + 1)) }
    }
}
