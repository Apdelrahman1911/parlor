package com.parlor.games.mafia.ui.flow.multidevice

import com.parlor.core.ids.PlayerId
import com.parlor.engine.state.Player
import com.parlor.games.mafia.domain.action.MafiaAction
import com.parlor.games.mafia.domain.action.MafiaActionCodec
import com.parlor.games.mafia.domain.authority.MafiaActionAuthority
import com.parlor.games.mafia.domain.event.MafiaEvent
import com.parlor.games.mafia.domain.projection.MafiaProjectionPolicy
import com.parlor.games.mafia.domain.state.MafiaPrivate
import com.parlor.games.mafia.domain.state.MafiaState
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.PeerMessage
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.PeerEvent
import com.parlor.networking.room.SendTarget
import com.parlor.session.passandplay.PassAndPlaySessionController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Host-side bridge between a canonical [PassAndPlaySessionController] running
 * the Mafia reducer and the room transport. Mirrors the Whodunit pattern.
 *
 * Responsibilities:
 *  1. Broadcast `MafiaProjectionPolicy.toPublic` on every host-state change
 *     as `HostMessage.PublicStateSnapshot`. The projection strips
 *     `hostOnly` and the entire `privatePerPlayer` map — peers never see
 *     each other's roles, coordination snapshot, or detective result over
 *     the wire because the host never serialises them into the public
 *     payload.
 *  2. Per-peer `MafiaPrivate` direct-send. The host loops over
 *     `privatePerPlayer.entries` and ships each slice to its owner with
 *     `SendTarget.Direct(playerId)`. Mafia coordination, detective inspect
 *     results, doctor protect picks, and civilian suspicions are delivered
 *     this way — only living Mafia members have a non-null `mafiaCoordination`
 *     in their slice, so Town peers physically cannot receive it.
 *  3. Inbox: decode `PeerMessage.ActionSubmit` via [MafiaActionCodec], run
 *     [MafiaActionAuthority.isAllowed] against the sender/host/dropped set,
 *     and forward to the canonical controller. Bad senders are dropped.
 *  4. Translate transport-level peer-events into game actions
 *     (`MarkPlayerDisconnected` / `MarkPlayerReconnected`) and resend the
 *     current snapshot to a peer that has just reconnected so its shadow
 *     catches up without forcing a rejoin.
 *
 * Lifecycle: created when the host taps Start in the lobby; closed when
 * the flow leaves the game. The room is owned outside the bridge.
 */
class MafiaHostRoomBridge(
    private val controller: PassAndPlaySessionController<MafiaState, MafiaAction, MafiaEvent>,
    private val room: LocalRoom,
    private val players: List<Player>,
    private val scope: CoroutineScope,
    private val json: Json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        encodeDefaults = true
    },
) {
    private val publicStateSerializer = MafiaState.serializer()
    private val privateSerializer = MafiaPrivate.serializer()

    private val jobs: MutableList<Job> = mutableListOf()

    init {
        startBroadcasts()
        startActionInbox()
        startPeerEventsListener()
    }

    /**
     * Ship the SessionStarting envelope and an initial snapshot so peers can
     * render without waiting for the first reducer emission. Called once by
     * the lobby after the controller and bridge are constructed.
     *
     * Mafia has no external case content, so `caseId` is a sentinel
     * (`"default"`); `modeId` is the Mafia mode id (Classic for now).
     */
    suspend fun announceStart(caseId: String, modeId: String, seed: Long) {
        room.send(
            target = SendTarget.Broadcast,
            message = HostMessage.SessionStarting(
                caseId = caseId,
                modeId = modeId,
                players = players,
                seed = seed,
            ),
        )
        broadcastPublicSnapshot()
        broadcastPrivatesForAllPlayers()
    }

    fun close() {
        jobs.forEach { it.cancel() }
        jobs.clear()
    }

    // ============================================================ Broadcasts ==

    private fun startBroadcasts() {
        // PublicStateSnapshot on every host-state change. drop(1) skips the
        // initial subscribe emission — `announceStart` already shipped that
        // explicitly.
        jobs += scope.launch {
            controller.publicState
                .drop(1)
                .distinctUntilChanged()
                .collect { _ -> broadcastPublicSnapshot() }
        }

        // PrivateStateForPlayer: whenever any peer's MafiaPrivate slice
        // changes, redeliver every slice to its owner. The map-equality
        // distinctUntilChanged dedupes redundant emissions when only public
        // fields changed.
        jobs += scope.launch {
            controller.hostState!!
                .drop(1)
                .distinctUntilChanged { old, new ->
                    old.state.privatePerPlayer === new.state.privatePerPlayer ||
                        old.state.privatePerPlayer == new.state.privatePerPlayer
                }
                .collect { _ -> broadcastPrivatesForAllPlayers() }
        }
    }

    private suspend fun broadcastPublicSnapshot() {
        val publicState = MafiaProjectionPolicy.toPublic(
            controller.hostState!!.value.state,
        ).state
        val payload = json
            .encodeToString(publicStateSerializer, publicState)
            .encodeToByteArray()
        room.send(SendTarget.Broadcast, HostMessage.PublicStateSnapshot(payload))
    }

    private suspend fun broadcastPrivatesForAllPlayers() {
        val map = controller.hostState!!.value.state.privatePerPlayer
        for ((playerId, slice) in map) {
            val bytes = json
                .encodeToString(privateSerializer, slice)
                .encodeToByteArray()
            room.send(
                target = SendTarget.Direct(playerId),
                message = HostMessage.PrivateStateForPlayer(
                    target = playerId,
                    payload = bytes,
                ),
            )
        }
    }

    // ============================================================ Inbox ==

    private fun startActionInbox() {
        val hostId = room.info.value.hostPlayerId
        jobs += scope.launch {
            room.incoming.filterIsInstance<PeerMessage.ActionSubmit>().collect { msg ->
                val action = runCatching { MafiaActionCodec.decode(msg.payload) }.getOrNull()
                    ?: return@collect
                val droppedPlayers = controller.publicState.value.state.public.droppedPlayers
                if (!MafiaActionAuthority.isAllowed(action, msg.sender, hostId, droppedPlayers)) {
                    return@collect
                }
                controller.submit(action)
            }
        }
    }

    /**
     * Translate transport-level peer connection transitions into game
     * actions, mirroring the Whodunit bridge:
     *  - `PeerLeft` → `MarkPlayerDisconnected` (public state records it).
     *  - `PeerReconnected` → `MarkPlayerReconnected` plus a targeted resend
     *    of the public snapshot and the reconnected peer's private slice,
     *    so the peer's shadow catches up to whatever screen everyone else
     *    is on without forcing a rejoin.
     */
    private fun startPeerEventsListener() {
        jobs += scope.launch {
            room.peerEvents.collect { event ->
                when (event) {
                    is PeerEvent.PeerLeft -> {
                        controller.submit(MafiaAction.MarkPlayerDisconnected(event.playerId))
                    }
                    is PeerEvent.PeerReconnected -> {
                        controller.submit(MafiaAction.MarkPlayerReconnected(event.playerId))
                        resendSnapshotTo(event.playerId)
                    }
                    is PeerEvent.PeerJoined -> Unit
                    PeerEvent.HostLost,
                    PeerEvent.HostRestored,
                    PeerEvent.SelfOffline,
                    PeerEvent.SelfOnline -> Unit
                }
            }
        }
    }

    private suspend fun resendSnapshotTo(playerId: PlayerId) {
        val state = controller.hostState!!.value.state
        val publicState = MafiaProjectionPolicy.toPublic(state).state
        val publicBytes = json.encodeToString(publicStateSerializer, publicState).encodeToByteArray()
        room.send(SendTarget.Direct(playerId), HostMessage.PublicStateSnapshot(publicBytes))
        val slice = state.privatePerPlayer[playerId] ?: return
        val privateBytes = json.encodeToString(privateSerializer, slice).encodeToByteArray()
        room.send(
            target = SendTarget.Direct(playerId),
            message = HostMessage.PrivateStateForPlayer(target = playerId, payload = privateBytes),
        )
    }
}
