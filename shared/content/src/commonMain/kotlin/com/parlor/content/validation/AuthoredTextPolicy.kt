package com.parlor.content.validation

/** Safety boundary for untrusted text that may be rendered, announced, or logged. */
object AuthoredTextPolicy {
    fun isSafeForDisplay(value: String): Boolean =
        value.hasWellFormedSurrogatePairs() && value.none(::isUnsafeCharacter)

    private fun isUnsafeCharacter(character: Char): Boolean =
        character.isISOControl() || character.category == CharCategory.FORMAT

    private fun String.hasWellFormedSurrogatePairs(): Boolean {
        var index = 0
        while (index < length) {
            when {
                this[index].isHighSurrogateCodeUnit() -> {
                    if (getOrNull(index + 1)?.isLowSurrogateCodeUnit() != true) return false
                    index += SURROGATE_PAIR_LENGTH
                }
                this[index].isLowSurrogateCodeUnit() -> return false
                else -> index++
            }
        }
        return true
    }

    private fun Char.isHighSurrogateCodeUnit(): Boolean = this in '\uD800'..'\uDBFF'

    private fun Char.isLowSurrogateCodeUnit(): Boolean = this in '\uDC00'..'\uDFFF'

    private const val SURROGATE_PAIR_LENGTH = 2
}
