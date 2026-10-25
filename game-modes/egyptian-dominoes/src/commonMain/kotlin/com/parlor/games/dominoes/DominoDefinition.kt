package com.parlor.games.dominoes

import com.parlor.core.localization.UiText
import com.parlor.core.time.DurationRange
import com.parlor.engine.definition.GameDefinition
import com.parlor.engine.definition.GameMetadata
import com.parlor.engine.definition.GameMode
import com.parlor.engine.session.SessionConfig
import com.parlor.games.dominoes.domain.DominoAction
import com.parlor.games.dominoes.domain.DominoChanged
import com.parlor.games.dominoes.domain.DominoProjection
import com.parlor.games.dominoes.domain.DominoReducer
import com.parlor.games.dominoes.domain.DominoRoster
import com.parlor.games.dominoes.domain.DominoState
import com.parlor.games.dominoes.protocol.DominoSnapshotCodec
import kotlin.time.Duration.Companion.minutes

class DominoDefinition : GameDefinition<DominoState, DominoAction, DominoChanged> {
    private val rules = DominoReducer()
    override val id = DominoIds.Game
    override val supportedPlayerCounts = DominoRoster.MIN_PLAYERS..DominoRoster.MAX_PLAYERS
    override val metadata = GameMetadata(
        displayName = UiText.Resource("dom_title"), tagline = UiText.Resource("dom_tagline"),
        estimatedDuration = DurationRange(10.minutes, 30.minutes), themeOverlayId = "egyptian-dominoes",
    )
    override val supportedModes = listOf(object : GameMode {
        override val id = DominoIds.Standard
        override val displayName = UiText.Resource("dom_title")
        override val description = UiText.Resource("dom_rules")
        override val supportedPlayerCounts = this@DominoDefinition.supportedPlayerCounts
        override val estimatedDuration = metadata.estimatedDuration
    })
    override fun createInitialState(config: SessionConfig) = rules.initial(config)
    override fun reducer() = rules
    override fun projectionPolicy() = DominoProjection
    override fun snapshotCodec() = DominoSnapshotCodec()
}
