package com.parlor.games.whodunit.ui.flow.multiplayer

import com.parlor.core.ids.PlayerId
import com.parlor.engine.state.Player
import com.parlor.games.whodunit.WhodunitIds
import com.parlor.games.whodunit.domain.action.WhodunitAction
import com.parlor.games.whodunit.domain.action.WhodunitActionCodec
import com.parlor.games.whodunit.domain.event.WhodunitEvent
import com.parlor.games.whodunit.domain.projection.WhodunitProjectionPolicy
import com.parlor.games.whodunit.domain.state.WhodunitPrivate
import com.parlor.games.whodunit.domain.state.WhodunitState
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.PeerMessage
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.SendTarget
import com.parlor.session.passandplay.PassAndPlaySessionController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.json.Json

/**
 * Host-side bridge between a canonical [PassAndPlaySessionController] and the
 * room transport.
 *
 * Responsibilities:
 *  1. Broadcast the public projection as `HostMessage.PublicStateSnapshot`
 *     on every host-state emission (after the projection policy strips
 *     `hostOnly` + `privatePerPlayer`).
 *  2. Send each peer **only their own** `WhodunitPrivate` whenever the
 *     `privatePerPlayer` map changes. A peer never sees another peer's
 *     private bucket; the redaction is enforced at the wire level here.
 *  3. Decode inbound `PeerMessage.ActionSubmit` and feed the action into
 *     the canonical controller. The host's reducer remains the only
 *     mutator of game state.
 *
 * Lifecycle: created when the host taps "Start" in the lobby, kept alive
 * by the enclosing Compose scope, torn down by [close] when the flow
 * leaves the game.
 */
class WhodunitHostRoomBridge(
    private val controller: PassAndPlaySessionController<WhodunitState, WhodunitAction, WhodunitEvent>,
    private val room: LocalRoom,
    private val players: List<Player>,
    private val scope: CoroutineScope,
    private val json: Json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        encodeDefaults = true
    },
) {
    private val publicStateSerializer = WhodunitState.serializer()
    private val privateSerializer = WhodunitPrivate.serializer()

    private val jobs: MutableList<Job> = mutableListOf()

    init {
        startBroadcasts()
        startActionInbox()
    }

    /**
     * Send the SessionStarting envelope. Called once by the lobby after the
     * controller and bridge are set up so peers know to transition into the
     * game flow.
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
        // Send the initial state immediately so peers have something to render
        // before the first action arrives.
        broadcastPublicSnapshot()
        broadcastPrivatesForAllPlayers()
    }

    fun close() {
        jobs.forEach { it.cancel() }
        jobs.clear()
    }

    // ============================================================ Broadcasts ==

    private fun startBroadcasts() {
        // PublicStateSnapshot on every host-state change. drop(1) avoids
        // re-broadcasting the initial value the controller emits on subscribe
        // — `announceStart()` already shipped that explicitly.
        jobs += scope.launch {
            controller.publicState
                .drop(1)
                .distinctUntilChanged()
                .collect { _ -> broadcastPublicSnapshot() }
        }

        // PrivateStateForPlayer: whenever any peer's WhodunitPrivate changes,
        // direct-message that peer. The map identity change is sufficient —
        // distinctUntilChanged dedupes redundant emissions.
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
        val publicState = WhodunitProjectionPolicy.toPublic(
            controller.hostState!!.value.state,
        ).state
        val payload = json
            .encodeToString(publicStateSerializer, publicState)
            .encodeToByteArray()
        room.send(SendTarget.Broadcast, HostMessage.PublicStateSnapshot(payload))
    }

    private suspend fun broadcastPrivatesForAllPlayers() {
        val map = controller.hostState!!.value.state.privatePerPlayer
        // The host doesn't know which `PeerId` (P2pKit's identifier) maps to
        // which game `PlayerId` yet — Phase 8 sends every private slice as a
        // broadcast targeted by player id; each peer's filter only accepts
        // its own. Future hardening can swap to true SendTarget.Direct once
        // peer↔player mapping is established at join time.
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
        jobs += scope.launch {
            room.incoming.filterIsInstance<PeerMessage.ActionSubmit>().collect { msg ->
                runCatching {
                    WhodunitActionCodec.decode(msg.payload)
                }.onSuccess { action ->
                    controller.submit(action)
                }
            }
        }
    }
}
