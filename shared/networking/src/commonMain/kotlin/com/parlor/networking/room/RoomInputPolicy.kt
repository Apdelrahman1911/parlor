package com.parlor.networking.room

/** Authoritative boundaries for user-controlled room admission fields. */
object RoomInputPolicy {
    const val ROOM_CODE_LENGTH: Int = 6
    const val ROOM_CODE_ALPHABET: String = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    const val MAX_DISPLAY_NAME_LENGTH: Int = 32

    /** Normalizes keyboard input without accepting ambiguous or non-ASCII code points. */
    fun normalizeRoomCode(input: String): String = input
        .asSequence()
        .filter { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' }
        .map(Char::uppercaseChar)
        .filter { it in ROOM_CODE_ALPHABET }
        .take(ROOM_CODE_LENGTH)
        .joinToString(separator = "")

    fun isValidRoomCode(code: String): Boolean =
        code.length == ROOM_CODE_LENGTH && code.all { it in ROOM_CODE_ALPHABET }

    fun normalizeDisplayName(input: String): String = input.trim()

    /**
     * Keeps international letters, marks, spaces, emoji and punctuation, but
     * rejects control/format characters that can alter surrounding UI, logs,
     * accessibility output, or the visual ordering of another player's name.
     */
    fun isValidDisplayName(name: String): Boolean {
        val normalized = normalizeDisplayName(name)
        return normalized.length in 1..MAX_DISPLAY_NAME_LENGTH &&
            normalized.hasSafeDisplayScalars()
    }

    /**
     * Authoritative roster boundary for display labels.
     *
     * Names are compared after the only supported normalization ([String.trim])
     * and must already be canonical. Equality is intentionally exact and
     * case-sensitive: display names are presentation labels, while transport-
     * authenticated [com.parlor.core.ids.PlayerId] values remain identities.
     */
    fun areValidDistinctDisplayNames(names: List<String>): Boolean =
        names.all { name ->
            name == normalizeDisplayName(name) && isValidDisplayName(name)
        } && names.toSet().size == names.size

    /** UI convenience only; [isValidDisplayName] remains authoritative. */
    fun sanitizeDisplayNameInput(input: String): String = buildString {
        var index = 0
        while (index < input.length && length < MAX_DISPLAY_NAME_LENGTH) {
            val character = input[index]
            when {
                character.isHighSurrogateCodeUnit() -> {
                    val low = input.getOrNull(index + 1)
                    if (low != null && low.isLowSurrogateCodeUnit()) {
                        if (!isUnsafeSupplementaryDisplayCharacter(character, low)) {
                            if (length + SURROGATE_PAIR_LENGTH > MAX_DISPLAY_NAME_LENGTH) break
                            append(character)
                            append(low)
                        }
                        index += SURROGATE_PAIR_LENGTH
                    } else {
                        // An unpaired surrogate cannot be encoded consistently
                        // across JVM/Native UTF-8 boundaries. Drop it at input.
                        index++
                    }
                }
                character.isLowSurrogateCodeUnit() || isUnsafeDisplayCharacter(character) -> index++
                else -> {
                    append(character)
                    index++
                }
            }
        }
    }

    private fun isUnsafeDisplayCharacter(character: Char): Boolean =
        character.isISOControl() || character.category == CharCategory.FORMAT

    private fun String.hasSafeDisplayScalars(): Boolean {
        var index = 0
        while (index < length) {
            val character = this[index]
            when {
                character.isHighSurrogateCodeUnit() -> {
                    val low = getOrNull(index + 1)
                    if (low?.isLowSurrogateCodeUnit() != true || isUnsafeSupplementaryDisplayCharacter(character, low)) return false
                    index += SURROGATE_PAIR_LENGTH
                }
                character.isLowSurrogateCodeUnit() || isUnsafeDisplayCharacter(character) -> return false
                else -> index++
            }
        }
        return true
    }

    /** Unicode 17.0.0 supplementary Cf scalars; Char.category sees only surrogate units. */
    private fun isUnsafeSupplementaryDisplayCharacter(high: Char, low: Char): Boolean {
        val codePoint = MIN_SUPPLEMENTARY_CODE_POINT +
            ((high.code - HIGH_SURROGATE_START) shl SURROGATE_PAYLOAD_BITS) + (low.code - LOW_SURROGATE_START)
        return codePoint == KAITHI_NUMBER_SIGN || codePoint == KAITHI_NUMBER_SIGN_ABOVE || codePoint == LANGUAGE_TAG ||
            codePoint in 0x13430..0x1343F || codePoint in 0x1BCA0..0x1BCA3 ||
            codePoint in 0x1D173..0x1D17A || codePoint in 0xE0020..0xE007F
    }

    private fun Char.isHighSurrogateCodeUnit(): Boolean = this in '\uD800'..'\uDBFF'

    private fun Char.isLowSurrogateCodeUnit(): Boolean = this in '\uDC00'..'\uDFFF'

    private const val MIN_SUPPLEMENTARY_CODE_POINT = 0x10000
    private const val HIGH_SURROGATE_START = 0xD800
    private const val LOW_SURROGATE_START = 0xDC00
    private const val SURROGATE_PAYLOAD_BITS = 10
    private const val KAITHI_NUMBER_SIGN = 0x110BD
    private const val KAITHI_NUMBER_SIGN_ABOVE = 0x110CD
    private const val LANGUAGE_TAG = 0xE0001
    private const val SURROGATE_PAIR_LENGTH = 2
}
