package com.parlor.core.text

/** Pure Kotlin public-name contract shared by admission, game rules and persistence. */
object DisplayNamePolicy {
    const val MAX_DISPLAY_NAME_LENGTH: Int = 32

    fun normalizeDisplayName(input: String): String = input.trim()

    /**
     * Keeps international letters, marks, spaces, emoji and punctuation, but
     * rejects control/format characters that can alter surrounding UI, logs,
     * accessibility output, or the visual ordering of another player's name.
     */
    fun isValidDisplayName(name: String): Boolean {
        val normalized = normalizeDisplayName(name)
        return normalized.length in 1..MAX_DISPLAY_NAME_LENGTH &&
            DisplayTextScalars.containsOnlySafeScalars(normalized)
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
                        if (!DisplayTextScalars.isUnsafeSupplementaryPair(character, low)) {
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
                character.isLowSurrogateCodeUnit() || DisplayTextScalars.isUnsafeBmpCharacter(character) -> index++
                else -> {
                    append(character)
                    index++
                }
            }
        }
    }

    private fun Char.isHighSurrogateCodeUnit(): Boolean = this in '\uD800'..'\uDBFF'

    private fun Char.isLowSurrogateCodeUnit(): Boolean = this in '\uDC00'..'\uDFFF'

    private const val SURROGATE_PAIR_LENGTH = 2
}
