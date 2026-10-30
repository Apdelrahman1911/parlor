package com.parlor.networking.room

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoomInputPolicyTest {
    @Test
    fun room_codes_are_exact_unambiguous_ascii() {
        assertTrue(RoomInputPolicy.isValidRoomCode("A2BCDE"))
        listOf("ABCDE", "ABCDEFG", "ABC0DE", "ABC1DE", "ABCODE", "ABCI12", "ÄBCDEF")
            .forEach { assertFalse(RoomInputPolicy.isValidRoomCode(it), it) }
    }

    @Test
    fun room_code_normalization_drops_formatting_and_ambiguous_characters() {
        assertEquals("A2BCDE", RoomInputPolicy.normalizeRoomCode(" a2-bc de "))
        assertEquals("ABC", RoomInputPolicy.normalizeRoomCode("a0b1cOI"))
        assertEquals("", RoomInputPolicy.normalizeRoomCode("ßßß"))
        assertEquals("ABC234", RoomInputPolicy.normalizeRoomCode("ＡＢＣ²³⁴abc234"))
    }

    @Test
    fun display_names_support_international_text_but_reject_control_and_bidi_formatting() {
        assertTrue(RoomInputPolicy.isValidDisplayName("عبد الرحمن"))
        assertTrue(RoomInputPolicy.isValidDisplayName("Zoë 🎲"))
        assertFalse(RoomInputPolicy.isValidDisplayName("   "))
        assertFalse(RoomInputPolicy.isValidDisplayName("Alice\nAdmin"))
        assertFalse(RoomInputPolicy.isValidDisplayName("Alice\u202EAdmin"))
        assertFalse(RoomInputPolicy.isValidDisplayName("A".repeat(33)))
        assertFalse(RoomInputPolicy.isValidDisplayName("Alice\uD83C"))
        assertFalse(RoomInputPolicy.isValidDisplayName("Alice\uDFB2"))
    }

    @Test
    fun display_name_sanitization_never_splits_a_unicode_surrogate_pair() {
        val die = "🎲"

        assertEquals(
            "A".repeat(31),
            RoomInputPolicy.sanitizeDisplayNameInput("A".repeat(31) + die),
        )
        assertEquals(
            "A".repeat(30) + die,
            RoomInputPolicy.sanitizeDisplayNameInput("A".repeat(30) + die + "B"),
        )
        assertEquals(
            "Alice",
            RoomInputPolicy.sanitizeDisplayNameInput("Ali\uD83Cce\uDFB2"),
        )
    }

    @Test
    fun display_name_rosters_require_valid_canonical_exactly_distinct_names() {
        assertTrue(RoomInputPolicy.areValidDistinctDisplayNames(listOf("Alice", "Bob")))
        assertTrue(RoomInputPolicy.areValidDistinctDisplayNames(listOf("Alice", "alice")))

        assertFalse(RoomInputPolicy.areValidDistinctDisplayNames(listOf("Alice", "Alice")))
        assertFalse(RoomInputPolicy.areValidDistinctDisplayNames(listOf("Alice", " Alice ")))
        assertFalse(RoomInputPolicy.areValidDistinctDisplayNames(listOf("Alice", "   ")))
    }

    @Test
    fun supplementary_format_controls_are_rejected_by_name_and_roster_validation() {
        supplementaryFormatCodePoints().forEach { codePoint ->
            val format = supplementaryScalar(codePoint)
            assertFalse(RoomInputPolicy.isValidDisplayName("Alice$format"), codePoint.toString(16))
            assertFalse(RoomInputPolicy.isValidDisplayName(format), codePoint.toString(16))
            assertFalse(RoomInputPolicy.areValidDistinctDisplayNames(listOf("Alice", "Alice$format")))
            assertEquals("Alice", RoomInputPolicy.sanitizeDisplayNameInput("Ali${format}ce"))
        }
    }

    @Test
    fun discarded_supplementary_format_controls_do_not_consume_the_input_length_budget() {
        val languageTag = supplementaryScalar(0xE0001)
        assertEquals("A".repeat(31) + "B", RoomInputPolicy.sanitizeDisplayNameInput("A".repeat(31) + languageTag + "B"))
        assertEquals("عبد الرحمن", RoomInputPolicy.sanitizeDisplayNameInput("عبد${languageTag} الرحمن"))
    }

    @Test
    fun supplementary_letters_emoji_and_variation_marks_remain_valid() {
        listOf(0x10400, 0x1F3B2, 0xE0100).forEach { codePoint ->
            val name = "Alice" + supplementaryScalar(codePoint)
            assertTrue(RoomInputPolicy.isValidDisplayName(name), codePoint.toString(16))
            assertEquals(name, RoomInputPolicy.sanitizeDisplayNameInput(name))
        }
        assertTrue(RoomInputPolicy.areValidDistinctDisplayNames(listOf("Alice", "alice", "عبد الرحمن")))
    }

    // All 127 supplementary Cf scalars in UnicodeData 17.0.0; no network at test time.
    private fun supplementaryFormatCodePoints(): List<Int> =
        listOf(0x110BD, 0x110CD, 0xE0001) + (0x13430..0x1343F) +
            (0x1BCA0..0x1BCA3) + (0x1D173..0x1D17A) + (0xE0020..0xE007F)

    private fun supplementaryScalar(codePoint: Int): String {
        val payload = codePoint - 0x10000
        return charArrayOf((0xD800 + (payload shr 10)).toChar(), (0xDC00 + (payload and 0x3FF)).toChar()).concatToString()
    }
}
