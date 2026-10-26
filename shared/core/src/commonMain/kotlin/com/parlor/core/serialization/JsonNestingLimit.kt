package com.parlor.core.serialization

/**
 * Bounds recursive JSON decoding before a serializer allocates a syntax tree.
 *
 * The current game schemas are shallow, non-recursive records. Sixty-four
 * container levels leave ample room for their envelopes while rejecting hostile
 * nesting within otherwise legal byte limits. This scanner is iterative and
 * allocation-free; it is a resource guard, not a JSON/UTF-8 validator. Callers
 * must still enforce byte limits and use their strict schema decoder afterwards.
 * Brackets inside strings (including escaped quotes/backslashes) are ignored.
 */
fun requireBoundedJsonNesting(payload: ByteArray) {
    var depth = 0
    var inString = false
    var escaped = false
    for (byte in payload) {
        val character = byte.toInt()
        if (inString) {
            when {
                escaped -> escaped = false
                character == BACKSLASH -> escaped = true
                character == QUOTE -> inString = false
            }
        } else {
            when (character) {
                QUOTE -> inString = true
                OPEN_OBJECT, OPEN_ARRAY -> {
                    depth++
                    require(depth <= MAX_JSON_NESTING_DEPTH) { "JSON nesting exceeds the supported limit" }
                }
                CLOSE_OBJECT, CLOSE_ARRAY -> {
                    depth--
                    require(depth >= 0) { "JSON container nesting is invalid" }
                }
            }
        }
    }
}

const val MAX_JSON_NESTING_DEPTH: Int = 64
private const val QUOTE = 0x22
private const val BACKSLASH = 0x5c
private const val OPEN_OBJECT = 0x7b
private const val CLOSE_OBJECT = 0x7d
private const val OPEN_ARRAY = 0x5b
private const val CLOSE_ARRAY = 0x5d
