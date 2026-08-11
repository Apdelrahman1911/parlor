package com.parlor.core.versioning

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SemVerTest {

    @Test
    fun parse_accepts_one_to_three_non_negative_integer_components() {
        assertEquals(SemVer(1, 0, 0), SemVer.parse("1"))
        assertEquals(SemVer(1, 2, 0), SemVer.parse("1.2"))
        assertEquals(SemVer(1, 2, 3), SemVer.parse("1.2.3"))
        assertEquals(SemVer.ZERO, SemVer.parse(" 0.0.0 "))
    }

    @Test
    fun parse_rejects_missing_extra_negative_and_non_numeric_components() {
        listOf(
            "",
            ".",
            "1.",
            ".1",
            "1..2",
            "1.2.3.4",
            "-1.0.0",
            "1.-2.0",
            "1.2.-3",
            "1.x.0",
        ).forEach { malformed ->
            assertFailsWith<IllegalArgumentException>("Expected '$malformed' to be rejected") {
                SemVer.parse(malformed)
            }
        }
    }
}
