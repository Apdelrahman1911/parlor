package com.parlor.games.whodunit.domain.modes

import com.parlor.core.ids.ModeId
import com.parlor.core.localization.UiText
import com.parlor.core.time.DurationRange
import com.parlor.engine.definition.GameMode
import com.parlor.games.whodunit.WhodunitIds
import kotlin.time.Duration.Companion.minutes

object ClassicVoteMode : GameMode {
    override val id: ModeId = WhodunitIds.ClassicVoteModeId
    override val displayName = UiText.Literal("Classic Vote Mode")
    override val description = UiText.Literal(
        "Investigate the full case, discuss every clue, then vote once at the end. " +
            "Best for story and deduction.",
    )
    override val supportedPlayerCounts: IntRange = 4..8
    override val estimatedDuration: DurationRange = DurationRange(25.minutes, 35.minutes)
}

object EliminationMode : GameMode {
    override val id: ModeId = WhodunitIds.EliminationModeId
    override val displayName = UiText.Literal("Elimination Mode")
    override val description = UiText.Literal(
        "Vote after every round. Eliminate suspects one by one. Find the killer " +
            "before they survive to the end. Best for fast and tense games.",
    )
    override val supportedPlayerCounts: IntRange = 5..8
    override val estimatedDuration: DurationRange = DurationRange(15.minutes, 25.minutes)
}
