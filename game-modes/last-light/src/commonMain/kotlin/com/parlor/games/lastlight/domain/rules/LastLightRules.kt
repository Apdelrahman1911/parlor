// Adapted from PartyDeck df649e6c896203bdf93130f6497e229757d5da30, core/src/commonMain/kotlin/dev/partydeck/core/.
package com.parlor.games.lastlight.domain.rules

/** Fixed, version-one rules. Match setup cannot silently change these values. */
object LastLightRules {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 6
    const val HAND_SIZE = 5
    const val MAX_PLAY_CARDS = 3
    const val FUSE_LIGHTS = 6
    const val DECK_SIZE = 30
    const val COPIES_PER_RANK = 9
    const val WILD_CARDS = 3
    const val MAX_PLAYER_ID_LENGTH = 64
    // Parlor admission permits canonical names through 32 UTF-16 code units.
    const val MAX_DISPLAY_NAME_LENGTH = 32
    const val MAX_ROUNDS = FUSE_LIGHTS * MAX_PLAYERS - 1
    const val MAX_PLAYS = (HAND_SIZE * MAX_PLAYERS - 1) * MAX_ROUNDS
    const val MAX_HISTORY_ENTRIES = MAX_PLAYS + MAX_ROUNDS * 2
    const val MAX_CARD_ID_LENGTH = 16
}
