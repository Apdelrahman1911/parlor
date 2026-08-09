package com.parlor.networking.room

/** Authoritative boundaries for user-controlled room admission fields. */
object RoomInputPolicy {
    const val ROOM_CODE_LENGTH: Int = 6
    const val ROOM_CODE_ALPHABET: String = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    const val MAX_DISPLAY_NAME_LENGTH: Int = 32

    /** Normalizes keyboard input without accepting ambiguous or non-ASCII code points. */
    fun normalizeRoomCode(input: String): String = input
        .uppercase()
        .filter { it in ROOM_CODE_ALPHABET }
        .take(ROOM_CODE_LENGTH)

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
            normalized.none(::isUnsafeDisplayCharacter)
    }

    /** UI convenience only; [isValidDisplayName] remains authoritative. */
    fun sanitizeDisplayNameInput(input: String): String = input
        .filterNot(::isUnsafeDisplayCharacter)
        .take(MAX_DISPLAY_NAME_LENGTH)

    private fun isUnsafeDisplayCharacter(character: Char): Boolean =
        character.isISOControl() || character.category == CharCategory.FORMAT
}
