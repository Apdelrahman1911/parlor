package com.parlor.games.lastlight.domain.modes

import com.parlor.core.localization.UiText
import com.parlor.core.time.DurationRange
import com.parlor.engine.definition.GameMode
import com.parlor.games.lastlight.LastLightIds
import com.parlor.games.lastlight.domain.rules.LastLightRules
import kotlin.time.Duration.Companion.minutes

object StandardMode : GameMode {
    override val id = LastLightIds.StandardModeId
    override val displayName = UiText.Literal("Standard")
    override val description = UiText.Literal("Bluff with Crown, Moon, Star, and Wild cards. Keep your light on.")
    override val supportedPlayerCounts = LastLightRules.MIN_PLAYERS..LastLightRules.MAX_PLAYERS
    override val estimatedDuration = DurationRange(10.minutes, 30.minutes)
}
