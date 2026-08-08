package com.parlor.games.mafia.domain.modes

import com.parlor.core.ids.ModeId
import com.parlor.core.localization.UiText
import com.parlor.core.time.DurationRange
import com.parlor.engine.definition.GameMode
import com.parlor.games.mafia.MafiaIds
import com.parlor.games.mafia.domain.settings.MafiaSettings
import kotlin.time.Duration.Companion.minutes

object ClassicMode : GameMode {
    override val id: ModeId = MafiaIds.ClassicModeId
    override val displayName = UiText.Literal("Classic")
    override val description = UiText.Literal(
        "Mafia, Detective, Doctor, and Civilians. Hidden roles, day-night cycle, " +
            "majority wins.",
    )
    override val supportedPlayerCounts: IntRange =
        MafiaSettings.MIN_PLAYERS..MafiaSettings.MAX_PLAYERS
    override val estimatedDuration: DurationRange = DurationRange(15.minutes, 45.minutes)
}
