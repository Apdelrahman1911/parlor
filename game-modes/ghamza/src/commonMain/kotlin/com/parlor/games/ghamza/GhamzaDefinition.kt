package com.parlor.games.ghamza

import com.parlor.core.localization.UiText
import com.parlor.core.time.DurationRange
import com.parlor.engine.definition.GameDefinition
import com.parlor.engine.definition.GameMetadata
import com.parlor.engine.definition.GameMode
import com.parlor.engine.session.SessionConfig
import com.parlor.games.ghamza.domain.GhamzaAction
import com.parlor.games.ghamza.domain.GhamzaChanged
import com.parlor.games.ghamza.domain.GhamzaProjection
import com.parlor.games.ghamza.domain.GhamzaReducer
import com.parlor.games.ghamza.domain.GhamzaRoster
import com.parlor.games.ghamza.domain.GhamzaState
import com.parlor.games.ghamza.protocol.GhamzaSnapshotCodec
import kotlin.time.Duration.Companion.minutes

class GhamzaDefinition : GameDefinition<GhamzaState, GhamzaAction, GhamzaChanged> {
    private val rules = GhamzaReducer()
    override val id = GhamzaIds.Game
    override val supportedPlayerCounts = GhamzaRoster.MIN_PLAYERS..GhamzaRoster.MAX_PLAYERS
    override val metadata = GameMetadata(
        displayName = UiText.Resource("gh_title"), tagline = UiText.Resource("gh_tagline"),
        estimatedDuration = DurationRange(10.minutes, 30.minutes), themeOverlayId = "ghamza",
    )
    override val supportedModes = listOf(object : GameMode {
        override val id = GhamzaIds.Standard
        override val displayName = UiText.Resource("gh_title")
        override val description = UiText.Resource("gh_rules")
        override val supportedPlayerCounts = this@GhamzaDefinition.supportedPlayerCounts
        override val estimatedDuration = metadata.estimatedDuration
    })
    override fun createInitialState(config: SessionConfig) = rules.initial(config)
    override fun reducer() = rules
    override fun projectionPolicy() = GhamzaProjection
    override fun snapshotCodec() = GhamzaSnapshotCodec()
}
