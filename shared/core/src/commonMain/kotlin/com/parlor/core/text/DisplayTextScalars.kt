package com.parlor.core.text

/** Shared scalar safety for public labels; no normalization or identity rewriting. */
object DisplayTextScalars {
    fun containsOnlySafeScalars(value: String): Boolean {
        var index = 0
        while (index < value.length) {
            val character = value[index]
            when {
                character in '\uD800'..'\uDBFF' -> {
                    val low = value.getOrNull(index + 1)
                    if (low == null || isUnsafeSupplementaryPair(character, low)) return false
                    index += SURROGATE_PAIR_LENGTH
                }
                character in '\uDC00'..'\uDFFF' || isUnsafeBmpCharacter(character) -> return false
                else -> index++
            }
        }
        return true
    }

    fun isUnsafeBmpCharacter(character: Char): Boolean =
        character.isISOControl() || character.category == CharCategory.FORMAT

    /** Unicode 17.0.0 supplementary Cf scalars: Char.category sees surrogate units only. */
    fun isUnsafeSupplementaryPair(high: Char, low: Char): Boolean {
        if (high !in '\uD800'..'\uDBFF' || low !in '\uDC00'..'\uDFFF') return true
        val codePoint = MIN_SUPPLEMENTARY_CODE_POINT +
            ((high.code - HIGH_SURROGATE_START) shl SURROGATE_PAYLOAD_BITS) + (low.code - LOW_SURROGATE_START)
        return codePoint == KAITHI_NUMBER_SIGN || codePoint == KAITHI_NUMBER_SIGN_ABOVE || codePoint == LANGUAGE_TAG ||
            codePoint in 0x13430..0x1343F || codePoint in 0x1BCA0..0x1BCA3 ||
            codePoint in 0x1D173..0x1D17A || codePoint in 0xE0020..0xE007F
    }

    private const val MIN_SUPPLEMENTARY_CODE_POINT = 0x10000
    private const val HIGH_SURROGATE_START = 0xD800
    private const val LOW_SURROGATE_START = 0xDC00
    private const val SURROGATE_PAYLOAD_BITS = 10
    private const val KAITHI_NUMBER_SIGN = 0x110BD
    private const val KAITHI_NUMBER_SIGN_ABOVE = 0x110CD
    private const val LANGUAGE_TAG = 0xE0001
    private const val SURROGATE_PAIR_LENGTH = 2
}
