package com.parlor.session.multidevice

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import com.parlor.core.ids.CaseId
import com.parlor.core.ids.ModeId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.core.random.RandomSource
import com.parlor.core.result.Result
import com.parlor.core.time.FakeClock
import com.parlor.engine.projection.PrivateProjection
import com.parlor.engine.projection.PublicProjection
import com.parlor.engine.reducer.DefaultReducerContext
import com.parlor.engine.session.SessionConfig
import com.parlor.engine.session.SubmitError
import com.parlor.engine.state.Player
import com.parlor.engine.testing.fakes.RoundRobinAnnounceGame
import com.parlor.engine.testing.fakes.RrAction
import com.parlor.engine.testing.fakes.RrEvent
import com.parlor.engine.testing.fakes.RrPhase
import com.parlor.engine.testing.fakes.RrState
import com.parlor.networking.protocol.SessionProtocol
import com.parlor.networking.room.NetError
import com.parlor.networking.testing.InMemoryHostRoom
import com.parlor.networking.testing.InMemoryPeerRoom
import com.parlor.networking.testing.InMemoryRoomBus
import com.parlor.session.SubmissionReceipt
import com.parlor.session.passandplay.PassAndPlaySessionController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant
import kotlin.test.Test

/**
 * Proves that the topology-independent reducer produces the same public state
 * trajectory locally and through the host-authoritative wire path. The remote
 * run uses real host and peer coordinators, serialized actions and snapshots,
 * an in-memory room bus, and peer-side [ShadowSessionController] instances.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MultiDeviceShapeTest {

    @Test
    fun reducer_state_trajectory_matches_across_real_in_memory_topologies() = runTest {
        val players = listOf(
            Player(PlayerId("p1"), "Alice", seat = 0),
            Player(PlayerId("p2"), "Bob", seat = 1),
            Player(PlayerId("p3"), "Cara", seat = 2),
        )
        val game = RoundRobinAnnounceGame()
        val config = SessionConfig(
            sessionId = SessionId("shape-session"),
            caseId = CaseId("none"),
            modeId = ModeId("round-robin"),
            players = players,
            randomSeed = 42L,
        )
        val actions = players.map { RrAction.Announce(it.id) }
        val passAndPlayTrajectory = passAndPlayTrajectory(
            game = game,
            config = config,
            actions = actions,
            scope = backgroundScope,
        )
        val topology = RoundRobinTopology(game, config, backgroundScope)

        try {
            topology.publishInitialSnapshot()
            runCurrent()
            val hostTrajectory = mutableListOf(topology.hostPublicState())
            val shadowTrajectories = topology.remotePlayerIds.associateWith { playerId ->
                mutableListOf(topology.shadowPublicState(playerId))
            }

            actions.forEach { action ->
                assertThat(topology.submit(action)).isTrue()
                runCurrent()
                hostTrajectory += topology.hostPublicState()
                shadowTrajectories.forEach { (playerId, trajectory) ->
                    trajectory += topology.shadowPublicState(playerId)
                }
            }

            assertThat(hostTrajectory).isEqualTo(passAndPlayTrajectory)
            shadowTrajectories.forEach { (playerId, trajectory) ->
                assertThat(trajectory).isEqualTo(passAndPlayTrajectory)
                assertThat(topology.shadowPrivateState(playerId).playerId).isEqualTo(playerId)
                assertThat(topology.shadowPrivateState(playerId).state)
                    .isEqualTo(passAndPlayTrajectory.last())
            }
            assertThat(hostTrajectory.last().phase).isInstanceOf(RrPhase.Finished::class)
        } finally {
            topology.close()
        }
    }

    private suspend fun passAndPlayTrajectory(
        game: RoundRobinAnnounceGame,
        config: SessionConfig,
        actions: List<RrAction.Announce>,
        scope: CoroutineScope,
    ): List<RrState> {
        val session = PassAndPlaySessionController(game, config, reducerContext(), scope)
        return try {
            buildList {
                add(game.projectionPolicy().toPublic(session.currentState()).state)
                actions.forEach { action ->
                    session.submit(action)
                    add(game.projectionPolicy().toPublic(session.currentState()).state)
                }
            }
        } finally {
            session.close()
        }
    }
}

private class RoundRobinTopology(
    private val game: RoundRobinAnnounceGame,
    config: SessionConfig,
    scope: CoroutineScope,
) {
    private val hostPlayerId = config.players.first().id
    private val bus = InMemoryRoomBus()
    private val hostSession = PassAndPlaySessionController(
        definition = game,
        config = config,
        reducerContext = reducerContext(),
        scope = scope,
    )
    private val protocol = SessionProtocol(
        sessionId = config.sessionId,
        gameId = game.id,
        gameVersion = 1,
    )
    private val snapshotCodec = game.snapshotCodec()
    private val projectionPolicy = game.projectionPolicy()
    val remotePlayerIds: List<PlayerId> = config.players.drop(1).map(Player::id)
    private val hostCoordinator = HostAuthoritativeSessionCoordinator(
        room = InMemoryHostRoom(bus, hostPlayerId, config.players.first().displayName),
        protocol = protocol,
        remotePlayers = remotePlayerIds.toSet(),
        scope = scope,
        applyCommand = ::applyPeerCommand,
        snapshotFor = ::snapshotFor,
        heartbeatIntervalMs = 0L,
        requireStartHandshake = false,
    )
    private val peers: Map<PlayerId, RoundRobinPeer> = config.players.drop(1).associate { player ->
        player.id to createPeer(player, scope)
    }

    suspend fun publishInitialSnapshot() {
        hostCoordinator.publishState(incrementRevision = false)
    }

    suspend fun submit(action: RrAction.Announce): Boolean {
        if (action.by == hostPlayerId) {
            return hostCoordinator.applyHostMutation {
                when (val submitted = hostSession.submit(action)) {
                    is Result.Failure -> false
                    is Result.Success -> submitted.data.stateChanged
                }
            } == HostMutationResult.Applied
        }
        return when (val submitted = peers.getValue(action.by).controller.submit(action)) {
            is Result.Failure -> false
            is Result.Success -> submitted.data.awaitingAuthority
        }
    }

    fun hostPublicState(): RrState =
        projectionPolicy.toPublic(hostSession.currentState()).state

    fun shadowPublicState(playerId: PlayerId): RrState =
        peers.getValue(playerId).controller.publicState.value.state

    fun shadowPrivateState(playerId: PlayerId): PrivateProjection<RrState> =
        peers.getValue(playerId).controller.privateStateFor(playerId).value

    suspend fun close() {
        peers.values.forEach { peer ->
            peer.coordinator.close()
            peer.controller.close()
        }
        hostCoordinator.close()
        hostSession.close()
    }

    private fun createPeer(player: Player, scope: CoroutineScope): RoundRobinPeer {
        bus.registerPeer(player.id)
        val room = InMemoryPeerRoom(
            bus = bus,
            selfPlayerId = player.id,
            displayName = player.displayName,
            hostId = hostPlayerId,
        )
        lateinit var coordinator: PeerAuthoritativeSessionCoordinator
        val controller = ShadowSessionController<RrState, RrAction, RrEvent>(
            selfPlayerId = player.id,
            sendActionToHost = { action ->
                when (val sent = coordinator.submit(RrActionWireCodec.encode(action))) {
                    is Result.Success -> Result.Success(
                        SubmissionReceipt(stateChanged = false, awaitingAuthority = true),
                    )
                    is Result.Failure -> Result.Failure(sent.error.toSubmitError())
                }
            },
            initialPublic = projectionPolicy.toPublic(hostSession.currentState()),
            initialPrivate = projectionPolicy.toPlayer(hostSession.currentState(), player.id),
        )
        coordinator = PeerAuthoritativeSessionCoordinator(
            room = room,
            protocol = protocol,
            selfPlayerId = player.id,
            scope = scope,
            onSnapshot = { payload, _ ->
                val publicState = snapshotCodec.decode(payload.publicPayload)
                val privateState = snapshotCodec.decode(payload.privatePayload)
                if (publicState != privateState) {
                    false
                } else {
                    controller.installPlayerSnapshot(
                        publicProjection = PublicProjection(publicState),
                        playerProjection = PrivateProjection(privateState, player.id),
                    )
                    true
                }
            },
        )
        return RoundRobinPeer(controller, coordinator)
    }

    private suspend fun applyPeerCommand(
        actor: PlayerId,
        payload: ByteArray,
    ): CommandApplication {
        val action = RrActionWireCodec.decode(payload)
            ?: return CommandApplication.InvalidAction
        if (action.by != actor) return CommandApplication.Unauthorized
        return when (val submitted = hostSession.submit(action)) {
            is Result.Failure -> CommandApplication.InvalidAction
            is Result.Success -> if (submitted.data.stateChanged) {
                CommandApplication.Applied
            } else {
                CommandApplication.InvalidAction
            }
        }
    }

    private fun snapshotFor(playerId: PlayerId): PlayerSnapshotPayload {
        val state = hostSession.currentState()
        return PlayerSnapshotPayload(
            publicPayload = snapshotCodec.encode(projectionPolicy.toPublic(state).state),
            privatePayload = snapshotCodec.encode(projectionPolicy.toPlayer(state, playerId).state),
        )
    }
}

private data class RoundRobinPeer(
    val controller: ShadowSessionController<RrState, RrAction, RrEvent>,
    val coordinator: PeerAuthoritativeSessionCoordinator,
)

private object RrActionWireCodec {
    private const val PREFIX = "announce:"

    fun encode(action: RrAction): ByteArray = when (action) {
        is RrAction.Announce -> "$PREFIX${action.by.raw}".encodeToByteArray()
    }

    fun decode(payload: ByteArray): RrAction.Announce? {
        val encoded = payload.decodeToString()
        if (!encoded.startsWith(PREFIX)) return null
        val playerId = encoded.removePrefix(PREFIX)
        return playerId.takeIf { it.isNotEmpty() }?.let { RrAction.Announce(PlayerId(it)) }
    }
}

private fun NetError.toSubmitError(): SubmitError = when (this) {
    NetError.CommandInFlight -> SubmitError.CommandPending
    NetError.SessionSuspended -> SubmitError.SessionSuspended
    else -> SubmitError.SessionClosed
}

private fun reducerContext() = DefaultReducerContext(
    clock = FakeClock(Instant.fromEpochSeconds(1_700_000_000)),
    random = RandomSource.seeded(42),
)
