package com.parlor.app.shell.multiplayer

import com.parlor.networking.room.RoomInputPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NameInputPolicyTest {
    @Test
    fun blankNamesCannotBeSubmittedAsASharedFallback() {
        val firstPeerName = normalizedMultiplayerDisplayName("   ")
        val secondPeerName = normalizedMultiplayerDisplayName("")

        assertEquals("", firstPeerName)
        assertEquals("", secondPeerName)
        assertFalse(RoomInputPolicy.isValidDisplayName(firstPeerName))
        assertFalse(RoomInputPolicy.isValidDisplayName(secondPeerName))
    }

    @Test
    fun explicitNameIsTrimmedAndRemainsSubmittable() {
        val name = normalizedMultiplayerDisplayName("  Ada  ")

        assertEquals("Ada", name)
        assertTrue(RoomInputPolicy.isValidDisplayName(name))
    }
}
