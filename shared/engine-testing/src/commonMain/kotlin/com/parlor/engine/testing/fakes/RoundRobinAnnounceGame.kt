package com.parlor.engine.testing.fakes

import com.parlor.core.ids.GameId
import com.parlor.core.ids.ModeId
import com.parlor.core.ids.PlayerId
import com.parlor.core.localization.UiText
import com.parlor.core.time.DurationRange
import com.parlor.engine.action.GameAction
import com.parlor.engine.definition.GameDefinition
import com.parlor.engine.definition.GameMetadata
import com.parlor.engine.definition.GameMode
import com.parlor.engine.event.GameEvent
import com.parlor.engine.phase.GamePhase
import com.parlor.engine.projection.HostProjection
import com.parlor.engine.projection.PrivateProjection
import com.parlor.engine.projection.ProjectionPolicy
import com.parlor.engine.projection.PublicProjection
import com.parlor.engine.reducer.GameReducer
import com.parlor.engine.reducer.Reduction
import com.parlor.engine.reducer.ReducerContext
import com.parlor.engine.session.SessionConfig
import com.parlor.engine.snapshot.SnapshotCodec
import com.parlor.engine.state.GameState
import com.parlor.engine.state.Player
import kotlin.time.Duration.Companion.minutes

/**
 * A trivial test game: each player, in seat order, "announces" once; after
 * the last seat the game ends. Exercises the entire engine contract surface
 * (Definition, Mode, State, Action, Event, Reducer, ProjectionPolicy, Snapshot)
 * without touching any Whodunit code.
 *
 * Lives in :shared:engine-testing — a dedicated test-fixtures module — so
 * any module's test source set can depend on these fakes without pulling
 * them into a production binary.
 */
class RoundRobinAnnounceGame : GameDefinition<RrState, RrAction, RrEvent> {
    override val id: GameId = GameId("round-robin-test")
    override val metadata: GameMetadata = GameMetadata(
        displayName = UiText.Literal("Round Robin Test"),
        tagline = UiText.Literal("Engine smoke test."),
        estimatedDuration = DurationRange(1.minutes, 1.minutes),
        themeOverlayId = "parlor-base",
    )
    override val supportedModes: List<GameMode> = listOf(RrMode)
    override val supportedPlayerCounts: IntRange = 1..16

    override fun createInitialState(config: SessionConfig): RrState =
        RrState(
            phase = RrPhase.Announcing(currentSeat = 0),
            players = config.players,
            announcedBy = emptyList(),
        )

    override fun reducer(): GameReducer<RrState, RrAction, RrEvent> = RrReducer
    override fun projectionPolicy(): ProjectionPolicy<RrState> = RrProjectionPolicy
    override fun snapshotCodec(): SnapshotCodec<RrState> = RrSnapshotCodec
}

object RrMode : GameMode {
    override val id: ModeId = ModeId("round-robin")
    override val displayName: UiText = UiText.Literal("Round Robin")
    override val description: UiText = UiText.Literal("Every player announces once, in seat order.")
    override val supportedPlayerCounts: IntRange = 1..16
    override val estimatedDuration: DurationRange = DurationRange(1.minutes, 1.minutes)
}

data class RrState(
    override val phase: RrPhase,
    override val players: List<Player>,
    val announcedBy: List<PlayerId>,
) : GameState

sealed class RrPhase(override val id: String) : GamePhase {
    data class Announcing(val currentSeat: Int) : RrPhase("announcing")
    data object Finished : RrPhase("finished")
}

sealed interface RrAction : GameAction {
    data class Announce(val by: PlayerId) : RrAction
}

sealed interface RrEvent : GameEvent {
    data class PlayerAnnounced(val by: PlayerId) : RrEvent
    data object SessionEnded : RrEvent
}

object RrReducer : GameReducer<RrState, RrAction, RrEvent>() {
    override fun reduce(
        state: RrState,
        action: RrAction,
        ctx: ReducerContext,
    ): Reduction<RrState, RrEvent> {
        val phase = state.phase as? RrPhase.Announcing
            ?: return Reduction(state)  // Already finished.
        val expectedPlayer = state.players.getOrNull(phase.currentSeat)
            ?: return Reduction(state.copy(phase = RrPhase.Finished), listOf(RrEvent.SessionEnded))

        return when (action) {
            is RrAction.Announce -> {
                if (action.by != expectedPlayer.id) {
                    Reduction(state)
                } else {
                    val nextSeat = phase.currentSeat + 1
                    val announcedNext = state.announcedBy + action.by
                    val events = mutableListOf<RrEvent>(RrEvent.PlayerAnnounced(action.by))
                    if (nextSeat >= state.players.size) {
                        events += RrEvent.SessionEnded
                        Reduction(
                            state.copy(phase = RrPhase.Finished, announcedBy = announcedNext),
                            events,
                        )
                    } else {
                        Reduction(
                            state.copy(
                                phase = RrPhase.Announcing(nextSeat),
                                announcedBy = announcedNext,
                            ),
                            events,
                        )
                    }
                }
            }
        }
    }
}

/** Minimal projection — no private or host-only data in this test game. */
object RrProjectionPolicy : ProjectionPolicy<RrState> {
    override fun toPublic(state: RrState) = PublicProjection(state)
    override fun toPlayer(state: RrState, playerId: PlayerId) = PrivateProjection(state, playerId)
    override fun toHost(state: RrState) = HostProjection(state)
}

/** Placeholder codec — the real engine uses kotlinx.serialization. */
object RrSnapshotCodec : SnapshotCodec<RrState> {
    override fun encode(state: RrState): ByteArray = state.toString().encodeToByteArray()
    override fun decode(payload: ByteArray): RrState =
        throw NotImplementedError("RrSnapshotCodec.decode is a smoke stub")
}
