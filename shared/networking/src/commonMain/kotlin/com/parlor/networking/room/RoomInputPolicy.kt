package com.parlor.networking.room

import com.parlor.core.text.DisplayNamePolicy

/** Authoritative boundaries for user-controlled room admission fields. */
object RoomInputPolicy {
    const val ROOM_CODE_LENGTH: Int = 6
    const val ROOM_CODE_ALPHABET: String = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    const val MAX_DISPLAY_NAME_LENGTH: Int = DisplayNamePolicy.MAX_DISPLAY_NAME_LENGTH

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

    fun normalizeDisplayName(input: String): String = DisplayNamePolicy.normalizeDisplayName(input)

    fun isValidDisplayName(name: String): Boolean = DisplayNamePolicy.isValidDisplayName(name)

    fun areValidDistinctDisplayNames(names: List<String>): Boolean = DisplayNamePolicy.areValidDistinctDisplayNames(names)

    fun sanitizeDisplayNameInput(input: String): String = DisplayNamePolicy.sanitizeDisplayNameInput(input)
}
