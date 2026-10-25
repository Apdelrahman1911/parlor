package com.parlor.games.wordimpostor

import com.parlor.core.localization.UiText
import com.parlor.core.time.DurationRange
import com.parlor.engine.definition.GameDefinition
import com.parlor.engine.definition.GameMetadata
import com.parlor.engine.definition.GameMode
import com.parlor.engine.session.SessionConfig
import com.parlor.games.wordimpostor.domain.WordImpostorAction
import com.parlor.games.wordimpostor.domain.WordImpostorChanged
import com.parlor.games.wordimpostor.domain.WordImpostorProjection
import com.parlor.games.wordimpostor.domain.WordImpostorReducer
import com.parlor.games.wordimpostor.domain.WordImpostorRoster
import com.parlor.games.wordimpostor.domain.WordImpostorState
import com.parlor.games.wordimpostor.protocol.WordImpostorSnapshotCodec
import kotlin.time.Duration.Companion.minutes

class WordImpostorDefinition : GameDefinition<WordImpostorState, WordImpostorAction, WordImpostorChanged> {
    private val rules = WordImpostorReducer()
    override val id = WordImpostorIds.Game
    override val supportedPlayerCounts = WordImpostorRoster.MIN_PLAYERS..WordImpostorRoster.MAX_PLAYERS
    override val metadata = GameMetadata(
        displayName = UiText.Resource("wi_title"), tagline = UiText.Resource("wi_tagline"),
        estimatedDuration = DurationRange(10.minutes, 30.minutes), themeOverlayId = "word-impostor",
    )
    override val supportedModes = listOf(object : GameMode {
        override val id = WordImpostorIds.Standard
        override val displayName = UiText.Resource("wi_title")
        override val description = UiText.Resource("wi_rules")
        override val supportedPlayerCounts = this@WordImpostorDefinition.supportedPlayerCounts
        override val estimatedDuration = metadata.estimatedDuration
    })
    override fun createInitialState(config: SessionConfig) = rules.initial(config)
    override fun reducer() = rules
    override fun projectionPolicy() = WordImpostorProjection
    override fun snapshotCodec() = WordImpostorSnapshotCodec()
}
