package com.parlor.designsystem.localization

/**
 * Isolates an already-validated, dynamic UI argument (for example a player name)
 * from the surrounding sentence's direction. Never apply this to stored or wire data.
 * The characters inside are preserved; admission/input validation owns control rejection.
 */
fun String.asBidiArgument(): String = "\u2068$this\u2069"
