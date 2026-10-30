package com.parlor.games.lastlight.domain

import com.parlor.games.lastlight.LastLightTestFixture
import com.parlor.games.lastlight.domain.rules.LastLightSessionRules
import com.parlor.games.lastlight.protocol.LastLightProjectionCodec
import com.parlor.games.lastlight.snapshot.LastLightSnapshotCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LastLightDisplayScalarBoundaryTest {
    private val fixture = LastLightTestFixture()

    @Test
    fun supplementaryFormatControlsAreRejectedByNamesAndRosters() {
        val name = "Player 0\uDB40\uDC01"
        val config = fixture.config().let { initial ->
            initial.copy(players = initial.players.mapIndexed { index, player ->
                if (index == 0) player.copy(displayName = name) else player
            })
        }
        assertEquals(listOf(false, false), listOf(
            LastLightSessionRules.isValidDisplayName(name), LastLightSessionRules.isValidConfig(config),
        ))
    }

    @Test
    fun initialStateRejectsAllSupplementaryFormatControls() {
        val codePoints = listOf(0x110BD, 0x110CD, 0xE0001) + (0x13430..0x1343F) +
            (0x1BCA0..0x1BCA3) + (0x1D173..0x1D17A) + (0xE0020..0xE007F)
        for (codePoint in codePoints) {
            val payload = codePoint - 0x10000
            val scalar = charArrayOf((0xD800 + (payload shr 10)).toChar(), (0xDC00 + (payload and 0x3FF)).toChar())
                .concatToString()
            val config = fixture.config().let { initial ->
                initial.copy(players = initial.players.mapIndexed { index, player ->
                    if (index == 0) player.copy(displayName = player.displayName + scalar) else player
                })
            }
            assertFailsWith<IllegalArgumentException> { fixture.definition.createInitialState(config) }
        }
    }

    @Test
    fun identifierValidationRejectsUnsafeScalarsWithoutRewritingValidIds() {
        assertFalse(LastLightSessionRules.isValidPlayerId("p0\uDB40\uDC01"))
        assertTrue(LastLightSessionRules.isValidPlayerId("界".repeat(64)))
        assertTrue(LastLightSessionRules.isValidPlayerId(" p0 "))
    }

    @Test
    fun publicSnapshotsRejectFormatControlsInsteadOfRepairingThem() {
        val state = fixture.initial()
        val label = state.players.first().displayName
        val original = LastLightProjectionCodec.encodePublic(state).decodeToString()
        val corrupt = original.replace(label, label + "\uDB40\uDC01").encodeToByteArray()
        assertFailsWith<IllegalArgumentException> { LastLightProjectionCodec.decodePublic(corrupt) }
    }

    @Test
    fun authoritySnapshotsRejectFormatControlsInsteadOfChangingIdentity() {
        val state = fixture.initial()
        val label = state.players.first().displayName
        val original = LastLightSnapshotCodec().encode(state).decodeToString()
        val corrupt = original.replace(label, label + "\uDB40\uDC01").encodeToByteArray()
        assertFailsWith<IllegalArgumentException> { LastLightSnapshotCodec().decode(corrupt) }
    }

    @Test
    fun supplementaryLettersEmojiAndVariationMarksRemainCanonical() {
        val names = listOf("عبد الرحمن", "Ada 🌙", "Writer \uD801\uDC00\uDB40\uDD00", "Alice")
        val config = fixture.config().let { initial ->
            initial.copy(players = initial.players.mapIndexed { index, player -> player.copy(displayName = names[index]) })
        }
        assertTrue(LastLightSessionRules.isValidConfig(config))
        assertEquals(names, fixture.definition.createInitialState(config).players.map { it.displayName })
    }
}
