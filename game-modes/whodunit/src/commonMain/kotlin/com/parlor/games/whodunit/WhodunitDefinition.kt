package com.parlor.games.whodunit

import com.parlor.core.ids.CaseId
import com.parlor.core.localization.UiText
import com.parlor.core.time.DurationRange
import com.parlor.engine.definition.GameDefinition
import com.parlor.engine.definition.GameMetadata
import com.parlor.engine.definition.GameMode
import com.parlor.engine.projection.ProjectionPolicy
import com.parlor.engine.reducer.GameReducer
import com.parlor.engine.session.SessionConfig
import com.parlor.engine.snapshot.SnapshotCodec
import com.parlor.games.whodunit.domain.action.WhodunitAction
import com.parlor.games.whodunit.domain.event.WhodunitEvent
import com.parlor.games.whodunit.domain.modes.ClassicVoteMode
import com.parlor.games.whodunit.domain.modes.EliminationMode
import com.parlor.games.whodunit.domain.phase.WhodunitPhase
import com.parlor.games.whodunit.domain.projection.WhodunitProjectionPolicy
import com.parlor.games.whodunit.domain.reducer.WhodunitReducer
import com.parlor.games.whodunit.domain.state.WhodunitHostOnly
import com.parlor.games.whodunit.domain.state.WhodunitPublic
import com.parlor.games.whodunit.domain.state.WhodunitState
import com.parlor.games.whodunit.snapshot.WhodunitSnapshotCodec
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.minutes

class WhodunitDefinition(
    private val json: Json,
) : GameDefinition<WhodunitState, WhodunitAction, WhodunitEvent> {

    override val id = WhodunitIds.GameId

    override val metadata = GameMetadata(
        displayName = UiText.Literal("Whodunit"),
        tagline = UiText.Literal("A murder mystery for the table."),
        estimatedDuration = DurationRange(15.minutes, 35.minutes),
        themeOverlayId = "cozy-noir",
    )

    override val supportedModes: List<GameMode> = listOf(ClassicVoteMode, EliminationMode)
    override val supportedPlayerCounts: IntRange = 4..8

    override fun createInitialState(config: SessionConfig): WhodunitState =
        WhodunitState(
            public = WhodunitPublic(
                caseId = CaseId(config.caseId.raw),
                modeId = config.modeId,
                playersAtTable = config.players,
            ),
            privatePerPlayer = emptyMap(),
            hostOnly = WhodunitHostOnly(
                killerId = com.parlor.core.ids.PlayerId("unassigned"),
                killerCharacterId = com.parlor.core.ids.CharacterId("unassigned"),
                randomSeed = config.randomSeed,
                seatToCharacter = emptyMap(),
                redHerringTargets = emptyList(),
            ),
            phase = WhodunitPhase.Setup,
            players = config.players,
        )

    override fun reducer(): GameReducer<WhodunitState, WhodunitAction, WhodunitEvent> = WhodunitReducer

    override fun projectionPolicy(): ProjectionPolicy<WhodunitState> = WhodunitProjectionPolicy

    override fun snapshotCodec(): SnapshotCodec<WhodunitState> = WhodunitSnapshotCodec(json)
}
