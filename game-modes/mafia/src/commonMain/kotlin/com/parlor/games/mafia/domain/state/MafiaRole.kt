package com.parlor.games.mafia.domain.state

import kotlinx.serialization.Serializable

@Serializable
enum class Role {
    Mafia,
    Detective,
    Doctor,
    Civilian,
}

@Serializable
enum class Team {
    /** Town: Detective, Doctor, Civilian. */
    Town,

    /** Mafia / Thieves. */
    Mafia,
}

val Role.team: Team
    get() = when (this) {
        Role.Mafia -> Team.Mafia
        Role.Detective, Role.Doctor, Role.Civilian -> Team.Town
    }

@Serializable
enum class DetectiveSeesAs {
    /** Detective inspected a Mafia (or "Mafia-aligned") player. */
    Mafia,

    /** Detective inspected a Town player. */
    Town,
}

/** What the Detective sees as a result of a night inspection. */
fun Role.detectiveSeesAs(): DetectiveSeesAs = when (this) {
    Role.Mafia -> DetectiveSeesAs.Mafia
    Role.Detective, Role.Doctor, Role.Civilian -> DetectiveSeesAs.Town
}
