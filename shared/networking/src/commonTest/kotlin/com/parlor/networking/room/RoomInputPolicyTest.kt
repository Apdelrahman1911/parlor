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
    }

    @Test
    fun display_names_support_international_text_but_reject_control_and_bidi_formatting() {
        assertTrue(RoomInputPolicy.isValidDisplayName("عبد الرحمن"))
        assertTrue(RoomInputPolicy.isValidDisplayName("Zoë 🎲"))
        assertFalse(RoomInputPolicy.isValidDisplayName("   "))
        assertFalse(RoomInputPolicy.isValidDisplayName("Alice\nAdmin"))
        assertFalse(RoomInputPolicy.isValidDisplayName("Alice\u202EAdmin"))
        assertFalse(RoomInputPolicy.isValidDisplayName("A".repeat(33)))
    }
}
