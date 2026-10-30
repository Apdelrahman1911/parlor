package com.parlor.games.dominoes

import com.parlor.core.ids.PlayerId
import com.parlor.engine.state.Player
import com.parlor.games.dominoes.domain.DominoReducer
import com.parlor.games.dominoes.domain.DominoRoster
import com.parlor.games.dominoes.domain.DominoSettings
import com.parlor.games.dominoes.protocol.DominoCodec
import com.parlor.games.dominoes.protocol.DominoSnapshotCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DominoDisplayScalarBoundaryTest {
    private val players = List(3) { Player(PlayerId("p$it"), "Player $it", it) }

    @Test
    fun supplementaryFormatControlsAreRejectedByNamesAndRosters() {
        for (codePoint in supplementaryFormatCodePoints()) {
            val name = "Player 0" + supplementaryScalar(codePoint)
            val corrupt = players.mapIndexed { index, player ->
                if (index == 0) player.copy(displayName = name) else player
            }
            assertEquals(listOf(false, false), listOf(
                DominoRoster.isValidDisplayName(name), DominoRoster.isValidRoster(corrupt),
            ), codePoint.toString(16))
        }
    }

    @Test
    fun identifierValidationRejectsUnsafeScalarsWithoutRewritingValidIds() {
        assertFalse(DominoRoster.isValidPlayerId("p0\uDB40\uDC01"))
        assertTrue(DominoRoster.isValidPlayerId("界".repeat(64)))
        assertTrue(DominoRoster.isValidPlayerId(" p0 "))
    }

    @Test
    fun initialStateRejectsSupplementaryFormatControls() {
        for (codePoint in supplementaryFormatCodePoints()) {
            val corrupt = players.mapIndexed { index, player ->
                if (index == 0) player.copy(displayName = player.displayName + supplementaryScalar(codePoint)) else player
            }
            assertFailsWith<IllegalArgumentException> { DominoReducer().initial(corrupt, DominoSettings(), 91L) }
        }
    }

    @Test
    fun authorityPersistenceRejectsFormatControlsInsteadOfChangingIdentity() {
        val state = DominoReducer().initial(players, DominoSettings(), 91L)
        val codec = DominoSnapshotCodec()
        val original = codec.encode(state).decodeToString()
        val corrupt = original.replace("Player 0", "Player 0\uDB40\uDC01").encodeToByteArray()
        assertFailsWith<IllegalArgumentException> { codec.decode(corrupt) }
    }

    @Test
    fun publicSnapshotRejectsFormatControlsInsteadOfSilentlyRepairingTheName() {
        val initial = DominoReducer().initial(players, DominoSettings(), 91L)
        val encoded = DominoCodec.encodePublic(initial).decodeToString()
        val corrupt = encoded.replace("Player 0", "Player 0\uDB40\uDC01").encodeToByteArray()
        assertFalse(encoded.encodeToByteArray().contentEquals(corrupt))
        assertFailsWith<IllegalArgumentException> { DominoCodec.decodePublic(corrupt) }
    }

    @Test
    fun supplementaryLettersEmojiAndVariationMarksRemainCanonical() {
        val names = listOf("عبد الرحمن", "Ada 🌙", "Writer \uD801\uDC00\uDB40\uDD00")
        val canonical = players.mapIndexed { index, player -> player.copy(displayName = names[index]) }
        assertTrue(DominoRoster.isValidRoster(canonical))
        val initial = DominoReducer().initial(canonical, DominoSettings(), 91L)
        assertEquals(canonical, DominoCodec.decodePublic(DominoCodec.encodePublic(initial)).players)
    }

    private fun supplementaryFormatCodePoints(): List<Int> =
        listOf(0x110BD, 0x110CD, 0xE0001) + (0x13430..0x1343F) + (0x1BCA0..0x1BCA3) +
            (0x1D173..0x1D17A) + (0xE0020..0xE007F)

    private fun supplementaryScalar(codePoint: Int): String {
        val payload = codePoint - 0x10000
        return charArrayOf((0xD800 + (payload shr 10)).toChar(), (0xDC00 + (payload and 0x3FF)).toChar()).concatToString()
    }
}
