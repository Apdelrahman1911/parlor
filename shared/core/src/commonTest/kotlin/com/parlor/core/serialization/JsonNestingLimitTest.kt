package com.parlor.core.serialization

import kotlin.test.Test
import kotlin.test.assertFailsWith

class JsonNestingLimitTest {
    @Test
    fun acceptsTheFullSupportedDepth() {
        requireBoundedJsonNesting(nested(MAX_JSON_NESTING_DEPTH))
    }

    @Test
    fun rejectsOneLevelBeyondTheSupportedDepth() {
        assertFailsWith<IllegalArgumentException> {
            requireBoundedJsonNesting(nested(MAX_JSON_NESTING_DEPTH + 1))
        }
    }

    @Test
    fun rejectsStackExhaustionPayloadInsideTheCommandByteLimit() {
        val payload = nested(4_096)
        check(payload.size < 32 * 1024)
        assertFailsWith<IllegalArgumentException> { requireBoundedJsonNesting(payload) }
    }

    @Test
    fun ignoresContainerCharactersInsideStringsAndEscapedQuotes() {
        val encoded = "{\"text\":\"" + "[{}]".repeat(1_000) + "\\\"[\\\\\",\"ok\":[0]}"
        requireBoundedJsonNesting(encoded.encodeToByteArray())
    }

    @Test
    fun acceptsUnicodeAndEscapedUnicodeWithoutCountingItsTextAsStructure() {
        requireBoundedJsonNesting("{\"name\":\"مرحبا 🎉 \\u005b\"}".encodeToByteArray())
    }

    @Test
    fun rejectsNegativeDepthRatherThanLettingItOffsetLaterNesting() {
        assertFailsWith<IllegalArgumentException> {
            requireBoundedJsonNesting(("]" + "[".repeat(65)).encodeToByteArray())
        }
    }

    private fun nested(depth: Int): ByteArray =
        ("[".repeat(depth) + "0" + "]".repeat(depth)).encodeToByteArray()
}
