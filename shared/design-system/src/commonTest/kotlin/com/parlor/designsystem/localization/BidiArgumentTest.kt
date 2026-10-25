package com.parlor.designsystem.localization

import kotlin.test.Test
import kotlin.test.assertEquals

class BidiArgumentTest {
    @Test
    fun names_are_isolated_individually_without_reordering_or_normalizing_their_contents() {
        for (name in listOf("Ahmed 12", "سارة", "محمد (Mo)", "Player-1 🦊", "a\u0301")) {
            val argument = name.asBidiArgument()
            assertEquals('\u2068', argument.first())
            assertEquals('\u2069', argument.last())
            assertEquals(name, argument.substring(1, argument.lastIndex))
        }
        assertEquals("\u2068Ahmed\u2069 · \u2068سارة\u2069",
            listOf("Ahmed", "سارة").joinToString(" · ") { it.asBidiArgument() })
    }
}
