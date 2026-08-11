package com.parlor.games.whodunit.multidevice

import com.parlor.core.ids.CharacterId
import com.parlor.core.ids.GameId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.games.whodunit.domain.action.WhodunitAction
import com.parlor.games.whodunit.domain.event.WhodunitEvent
import com.parlor.games.whodunit.domain.state.WhodunitState
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.ProtocolVersion
import com.parlor.networking.protocol.SessionEnvelopeHeader
import com.parlor.networking.room.SendTarget
import com.parlor.networking.testing.InMemoryRoomBus
import com.parlor.session.passandplay.PassAndPlaySessionController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Test-only host simulator. Wraps a real `PassAndPlaySessionController` and
 * — on every public-state emission — encodes the projection into the public
 * half of a current [HostMessage.PlayerSnapshot] and ships it to every
 * registered peer through the shared [InMemoryRoomBus].
 *
 * The fixture intentionally does **not** wire peer-to-host action submission
 * through the bus: actions are submitted directly to the host session in
 * tests. This fixture covers projection propagation and redaction only; the
 * authoritative coordinator/bridge suites cover command authentication,
 * revisions, acknowledgements, and player-private payload installation.
 */
class WhodunitHostSimulator(
    val session: PassAndPlaySessionController<WhodunitState, WhodunitAction, WhodunitEvent>,
    private val bus: InMemoryRoomBus,
    private val json: Json,
    scope: CoroutineScope,
) {
    /**
     * Drop policy: when set to a non-null predicate, snapshots for which the
     * predicate returns `true` are *not* shipped over the bus. Used to
     * simulate dropped messages while keeping host state authoritative.
     */
    var dropPolicy: ((WhodunitState) -> Boolean)? = null

    /** Per-emission counter — useful for "drop the Nth snapshot" tests. */
    var emissionCount: Int = 0
        private set
    private var revision: Long = 0L

    private val collector: Job = scope.launch {
        session.publicState.collect { projection ->
            emissionCount++
            if (dropPolicy?.invoke(projection.state) == true) return@collect
            val payload = json
                .encodeToString(WhodunitState.serializer(), projection.state)
                .encodeToByteArray()
            bus.fromHost(SendTarget.Broadcast, snapshot(payload))
        }
    }

    /**
     * Re-broadcast the host's *current* public state to all peers. Useful
     * after a drop to verify the peer converges once delivery resumes.
     */
    suspend fun resendCurrentSnapshot() {
        val state = session.publicState.value.state
        val payload = json
            .encodeToString(WhodunitState.serializer(), state)
            .encodeToByteArray()
        bus.fromHost(SendTarget.Broadcast, snapshot(payload))
    }

    private fun snapshot(payload: ByteArray): HostMessage.PlayerSnapshot {
        val nextRevision = revision++
        return HostMessage.PlayerSnapshot(
            header = SessionEnvelopeHeader(
                protocol = ProtocolVersion(),
                sessionId = SessionId("whodunit-projection-fixture"),
                gameId = GameId("whodunit"),
                gameVersion = 1,
                messageId = "fixture-snapshot-${nextRevision.toString().padStart(16, '0')}",
                sequence = nextRevision,
            ),
            revision = nextRevision,
            nextExpectedClientSequence = 1L,
            publicPayload = payload,
            privatePayload = byteArrayOf(),
        )
    }

    fun close() {
        collector.cancel()
    }
}

/**
 * Test-only peer simulator. Listens to its bus inbox, decodes incoming
 * [HostMessage.PlayerSnapshot.publicPayload] values, and exposes the result as a [StateFlow]
 * that mirrors the host's *public projection*.
 *
 * `state` is null until the first snapshot arrives. The test asserts that
 * — after every action — the peer's state equals the host's public state.
 */
class WhodunitPeerSimulator(
    val playerId: PlayerId,
    private val bus: InMemoryRoomBus,
    private val json: Json,
    scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<WhodunitState?>(null)
    val state: StateFlow<WhodunitState?> = _state.asStateFlow()

    /** Counts how many snapshots have actually been applied to the peer's view. */
    var snapshotsReceived: Int = 0
        private set

    init {
        bus.registerPeer(playerId)
        scope.launch {
            bus.peerMessagesIn(playerId).collect { msg ->
                if (msg is HostMessage.PlayerSnapshot) {
                    _state.value = json.decodeFromString(
                        WhodunitState.serializer(),
                        msg.publicPayload.decodeToString(),
                    )
                    snapshotsReceived++
                }
            }
        }
    }
}

/**
 * Redaction assertion helpers. Per ARCHITECTURE.md §7, public-projected
 * state must not carry any field a peer could use to derive who the killer
 * is, the host's random seed, or any per-player private data.
 *
 * The Whodunit projection policy replaces `hostOnly` with a sentinel that
 * uses the literal string `"redacted"` for [PlayerId] and [CharacterId].
 * This helper centralises the checks so individual tests stay focused.
 */
object PeerStateRedactionAssertions {
    const val REDACTED_MARKER: String = "redacted"

    /** True iff the state is fully redacted for peer/public viewing. */
    fun isFullyRedacted(state: WhodunitState): Boolean =
        state.privatePerPlayer.isEmpty() &&
            state.hostOnly.killerId == PlayerId(REDACTED_MARKER) &&
            state.hostOnly.killerCharacterId == CharacterId(REDACTED_MARKER) &&
            state.hostOnly.randomSeed == 0L &&
            state.hostOnly.seatToCharacter.isEmpty() &&
            state.hostOnly.redHerringTargets.isEmpty() &&
            state.hostOnly.drawnClueIds.isEmpty()

    /** Returns a description of any redaction violations found. */
    fun violations(state: WhodunitState): List<String> = buildList {
        if (state.privatePerPlayer.isNotEmpty()) {
            add("privatePerPlayer leaks ${state.privatePerPlayer.size} entries")
        }
        if (state.hostOnly.killerId != PlayerId(REDACTED_MARKER)) {
            add("hostOnly.killerId leaked: ${state.hostOnly.killerId}")
        }
        if (state.hostOnly.killerCharacterId != CharacterId(REDACTED_MARKER)) {
            add("hostOnly.killerCharacterId leaked: ${state.hostOnly.killerCharacterId}")
        }
        if (state.hostOnly.randomSeed != 0L) {
            add("hostOnly.randomSeed leaked: ${state.hostOnly.randomSeed}")
        }
        if (state.hostOnly.seatToCharacter.isNotEmpty()) {
            add("hostOnly.seatToCharacter leaks ${state.hostOnly.seatToCharacter.size} entries")
        }
        if (state.hostOnly.redHerringTargets.isNotEmpty()) {
            add("hostOnly.redHerringTargets leaks ${state.hostOnly.redHerringTargets.size} entries")
        }
        if (state.hostOnly.drawnClueIds.isNotEmpty()) {
            add("hostOnly.drawnClueIds leaks ${state.hostOnly.drawnClueIds.size} entries")
        }
    }
}
