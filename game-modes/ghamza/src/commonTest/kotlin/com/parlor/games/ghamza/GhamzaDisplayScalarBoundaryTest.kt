package com.parlor.games.ghamza

import com.parlor.core.ids.PlayerId
import com.parlor.engine.state.Player
import com.parlor.games.ghamza.domain.GhamzaReducer
import com.parlor.games.ghamza.domain.GhamzaRoster
import com.parlor.games.ghamza.domain.GhamzaSettings
import com.parlor.games.ghamza.protocol.GhamzaCodec
import com.parlor.games.ghamza.protocol.GhamzaSnapshotCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GhamzaDisplayScalarBoundaryTest {
    private val players = List(3) { Player(PlayerId("p$it"), "Player $it", it) }

    @Test
    fun supplementaryFormatControlsAreRejectedByNamesAndRosters() {
        for (codePoint in supplementaryFormatCodePoints()) {
            val name = "Player 0" + supplementaryScalar(codePoint)
            val corrupt = players.mapIndexed { index, player ->
                if (index == 0) player.copy(displayName = name) else player
            }
            assertEquals(listOf(false, false), listOf(
                GhamzaRoster.isValidDisplayName(name), GhamzaRoster.isValidRoster(corrupt),
            ), codePoint.toString(16))
        }
    }

    @Test
    fun identifierValidationRejectsUnsafeScalarsWithoutRewritingValidIds() {
        assertFalse(GhamzaRoster.isValidPlayerId("p0\uDB40\uDC01"))
        assertTrue(GhamzaRoster.isValidPlayerId("界".repeat(64)))
        assertTrue(GhamzaRoster.isValidPlayerId(" p0 "))
    }

    @Test
    fun initialStateRejectsSupplementaryFormatControls() {
        for (codePoint in supplementaryFormatCodePoints()) {
            val corrupt = players.mapIndexed { index, player ->
                if (index == 0) player.copy(displayName = player.displayName + supplementaryScalar(codePoint)) else player
            }
            assertFailsWith<IllegalArgumentException> { GhamzaReducer().initial(corrupt, GhamzaSettings(), 91L) }
        }
    }

    @Test
    fun authorityPersistenceRejectsFormatControlsInsteadOfChangingIdentity() {
        val state = GhamzaReducer().initial(players, GhamzaSettings(), 91L)
        val codec = GhamzaSnapshotCodec()
        val original = codec.encode(state).decodeToString()
        val corrupt = original.replace("Player 0", "Player 0\uDB40\uDC01").encodeToByteArray()
        assertFailsWith<IllegalArgumentException> { codec.decode(corrupt) }
    }

    @Test
    fun publicSnapshotRejectsFormatControlsInsteadOfSilentlyRepairingTheName() {
        val initial = GhamzaReducer().initial(players, GhamzaSettings(), 91L)
        val encoded = GhamzaCodec.encodePublic(initial).decodeToString()
        val corrupt = encoded.replace("Player 0", "Player 0\uDB40\uDC01").encodeToByteArray()
        assertFalse(encoded.encodeToByteArray().contentEquals(corrupt))
        assertFailsWith<IllegalArgumentException> { GhamzaCodec.decodePublic(corrupt) }
    }

    @Test
    fun supplementaryLettersEmojiAndVariationMarksRemainCanonical() {
        val names = listOf("عبد الرحمن", "Ada 🌙", "Writer \uD801\uDC00\uDB40\uDD00")
        val canonical = players.mapIndexed { index, player -> player.copy(displayName = names[index]) }
        assertTrue(GhamzaRoster.isValidRoster(canonical))
        val initial = GhamzaReducer().initial(canonical, GhamzaSettings(), 91L)
        assertEquals(canonical, GhamzaCodec.decodePublic(GhamzaCodec.encodePublic(initial)).players)
    }

    private fun supplementaryFormatCodePoints(): List<Int> =
        listOf(0x110BD, 0x110CD, 0xE0001) + (0x13430..0x1343F) + (0x1BCA0..0x1BCA3) +
            (0x1D173..0x1D17A) + (0xE0020..0xE007F)

    private fun supplementaryScalar(codePoint: Int): String {
        val payload = codePoint - 0x10000
        return charArrayOf((0xD800 + (payload shr 10)).toChar(), (0xDC00 + (payload and 0x3FF)).toChar()).concatToString()
    }
}
