package com.parlor.games.lastlight

import com.parlor.core.localization.UiText
import com.parlor.engine.definition.GameDefinition
import com.parlor.engine.definition.GameMetadata
import com.parlor.engine.definition.GameMode
import com.parlor.engine.projection.ProjectionPolicy
import com.parlor.engine.reducer.GameReducer
import com.parlor.engine.session.SessionConfig
import com.parlor.engine.snapshot.SnapshotCodec
import com.parlor.games.lastlight.domain.action.LastLightAction
import com.parlor.games.lastlight.domain.event.LastLightEvent
import com.parlor.games.lastlight.domain.modes.StandardMode
import com.parlor.games.lastlight.domain.projection.LastLightProjectionPolicy
import com.parlor.games.lastlight.domain.reducer.LastLightReducer
import com.parlor.games.lastlight.domain.state.LastLightState
import com.parlor.games.lastlight.random.LastLightRandom
import com.parlor.games.lastlight.snapshot.LastLightSnapshotCodec
import kotlinx.serialization.json.Json

/** Composition-root contribution; admission and transport remain owned by Parlor. */
class LastLightDefinition(private val json: Json = Json) : GameDefinition<LastLightState, LastLightAction, LastLightEvent> {
    private val rules = LastLightReducer(LastLightRandom::forRound)

    override val id = LastLightIds.GameId
    override val metadata = GameMetadata(
        displayName = UiText.Literal("Last Light"),
        tagline = UiText.Literal("Play a bluff. Call a claim. Keep your light on."),
        estimatedDuration = StandardMode.estimatedDuration,
        themeOverlayId = "midnight-noir",
    )
    override val supportedModes: List<GameMode> = listOf(StandardMode)
    override val supportedPlayerCounts = StandardMode.supportedPlayerCounts

    override fun createInitialState(config: SessionConfig): LastLightState = rules.createInitialState(config)
    override fun reducer(): GameReducer<LastLightState, LastLightAction, LastLightEvent> = rules
    override fun projectionPolicy(): ProjectionPolicy<LastLightState> = LastLightProjectionPolicy
    override fun snapshotCodec(): SnapshotCodec<LastLightState> = LastLightSnapshotCodec(json)
}
