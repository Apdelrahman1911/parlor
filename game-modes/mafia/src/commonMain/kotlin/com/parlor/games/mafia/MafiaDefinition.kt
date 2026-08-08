package com.parlor.games.mafia

import com.parlor.core.localization.UiText
import com.parlor.core.time.DurationRange
import com.parlor.engine.definition.GameDefinition
import com.parlor.engine.definition.GameMetadata
import com.parlor.engine.definition.GameMode
import com.parlor.engine.projection.ProjectionPolicy
import com.parlor.engine.reducer.GameReducer
import com.parlor.engine.session.SessionConfig
import com.parlor.engine.snapshot.SnapshotCodec
import com.parlor.games.mafia.domain.action.MafiaAction
import com.parlor.games.mafia.domain.event.MafiaEvent
import com.parlor.games.mafia.domain.modes.ClassicMode
import com.parlor.games.mafia.domain.phase.MafiaPhase
import com.parlor.games.mafia.domain.projection.MafiaProjectionPolicy
import com.parlor.games.mafia.domain.reducer.MafiaReducer
import com.parlor.games.mafia.domain.settings.MafiaSettingsPresets
import com.parlor.games.mafia.domain.settings.MafiaSettings
import com.parlor.games.mafia.domain.state.MafiaHostOnly
import com.parlor.games.mafia.domain.state.MafiaPublic
import com.parlor.games.mafia.domain.state.MafiaState
import com.parlor.games.mafia.domain.state.PublicPlayerSlot
import com.parlor.games.mafia.snapshot.MafiaSnapshotCodec
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.minutes

class MafiaDefinition(
    private val json: Json,
) : GameDefinition<MafiaState, MafiaAction, MafiaEvent> {

    override val id = MafiaIds.GameId

    override val metadata = GameMetadata(
        displayName = UiText.Literal("Mafia"),
        tagline = UiText.Literal("Hidden roles, day-night cycle. Find the Mafia before they find you."),
        estimatedDuration = DurationRange(15.minutes, 45.minutes),
        themeOverlayId = "midnight-noir",
    )

    override val supportedModes: List<GameMode> = listOf(ClassicMode)
    override val supportedPlayerCounts: IntRange =
        MafiaSettings.MIN_PLAYERS..MafiaSettings.MAX_PLAYERS

    override fun createInitialState(config: SessionConfig): MafiaState {
        val preset = MafiaSettingsPresets.forPlayerCount(config.players.size)
        val roster = config.players.map { p ->
            PublicPlayerSlot(playerId = p.id, displayName = p.displayName, seat = p.seat)
        }
        return MafiaState(
            public = MafiaPublic(
                settings = preset,
                day = 0,
                roster = roster,
            ),
            privatePerPlayer = emptyMap(),
            hostOnly = MafiaHostOnly(
                fullRoleMap = emptyMap(),
                randomSeed = config.randomSeed,
            ),
            phase = MafiaPhase.Setup,
            players = config.players,
        )
    }

    override fun reducer(): GameReducer<MafiaState, MafiaAction, MafiaEvent> = MafiaReducer
    override fun projectionPolicy(): ProjectionPolicy<MafiaState> = MafiaProjectionPolicy
    override fun snapshotCodec(): SnapshotCodec<MafiaState> = MafiaSnapshotCodec(json)
}
