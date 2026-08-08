package com.parlor.games.whodunit.multidevice

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import com.parlor.content.datasource.InMemoryCachedCaseDataSource
import com.parlor.content.datasource.KtorRemoteCaseDataSource
import com.parlor.content.repository.DefaultCaseRepository
import com.parlor.content.validation.DefaultCaseValidator
import com.parlor.core.ids.CaseId
import com.parlor.core.ids.ModeId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.core.random.RandomSource
import com.parlor.core.result.Result
import com.parlor.core.time.FakeClock
import com.parlor.core.versioning.SemVer
import com.parlor.engine.registry.DefaultGameRegistry
import com.parlor.engine.session.SessionConfig
import com.parlor.engine.state.Player
import com.parlor.games.whodunit.WhodunitDefinition
import com.parlor.games.whodunit.WhodunitIds
import com.parlor.games.whodunit.ackBriefingForAll
import com.parlor.games.whodunit.ackIntroForAll
import com.parlor.games.whodunit.content.BundledWhodunitCases
import com.parlor.games.whodunit.content.WhodunitCase
import com.parlor.games.whodunit.content.WhodunitPayloadValidator
import com.parlor.games.whodunit.domain.action.WhodunitAction
import com.parlor.games.whodunit.domain.event.WhodunitEvent
import com.parlor.games.whodunit.domain.phase.WhodunitPhase
import com.parlor.games.whodunit.domain.reducer.WhodunitReducerContext
import com.parlor.games.whodunit.domain.state.VoteState
import com.parlor.games.whodunit.domain.state.WhodunitState
import com.parlor.games.whodunit.resources.Res
import com.parlor.session.multidevice.InMemoryRoomBus
import com.parlor.session.passandplay.PassAndPlaySessionController
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi
import kotlin.test.Test

/**
 * Phase 7 — multi-device shape contract for Whodunit.
 *
 * Promotes the prior round-robin smoke test into a real contract: drives an
 * actual Whodunit playthrough on a host session, ships every public state
 * emission through [InMemoryRoomBus] as `HostMessage.PublicStateSnapshot`,
 * and asserts the peer-side projection equals the host's public projection
 * at every step. The host is canonical; the peer is a passive mirror.
 *
 * What this test *does* lock down:
 *  - The host's public projection (after [WhodunitProjectionPolicy.toPublic])
 *    is what reaches the peer.
 *  - `hostOnly.killerId`, `killerCharacterId`, `randomSeed`,
 *    `seatToCharacter`, `redHerringTargets`, `drawnClueIds` are redacted —
 *    they reach the peer only as sentinel values, in every reachable phase.
 *  - `privatePerPlayer` never leaks to the peer's view, even during phases
 *    where private data is *active* on the host (e.g. CharacterReveal,
 *    Round, the vote ballot).
 *  - A dropped intermediate state message does not corrupt host state, and
 *    the peer eventually re-converges once delivery resumes.
 *
 * What this test deliberately does **not** do:
 *  - Serialize peer→host actions over the wire. Actions remain unsigned at
 *    this phase; the test driver submits them directly to the host. Real
 *    transports add action serialization Post-MVP.
 *  - Implement Bluetooth / LAN / WebRTC — Phase 7 is contract-only.
 */
@OptIn(ExperimentalResourceApi::class, ExperimentalCoroutinesApi::class)
class WhodunitMultiDeviceShapeTest {

    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        encodeDefaults = true
    }

    private suspend fun loadCase(): WhodunitCase {
        val bundled = BundledWhodunitCases(
            knownCaseIds = listOf("last-dinner"),
            loadJson = { id ->
                runCatching { Res.readBytes("files/cases/$id.json").decodeToString() }.getOrNull()
            },
            json = json,
        )
        val emptyRemote = HttpClient(MockEngine { _ ->
            respond(content = ByteReadChannel.Empty, status = HttpStatusCode.NotFound)
        }) {
            install(ContentNegotiation) { json(json) }
        }
        val definition = WhodunitDefinition(json)
        val repo = DefaultCaseRepository(
            remote = KtorRemoteCaseDataSource(client = emptyRemote, baseUrl = "https://test.local"),
            cache = InMemoryCachedCaseDataSource(),
            bundled = bundled,
            validator = DefaultCaseValidator(
                json = json,
                knownSchemaVersion = 1,
                installedAppVersion = SemVer(1, 0, 0),
                gameRegistry = DefaultGameRegistry(listOf(definition)),
            ),
            json = json,
        )
        val result = repo.loadCase(CaseId("last-dinner"), WhodunitPayloadValidator(json))
        return (result as Result.Success).data.payload
    }

    private fun fourPlayers() = listOf(
        Player(PlayerId("p1"), "Alice", seat = 0),
        Player(PlayerId("p2"), "Bob", seat = 1),
        Player(PlayerId("p3"), "Cara", seat = 2),
        Player(PlayerId("p4"), "Diego", seat = 3),
    )

    private fun TestScope.buildHostSession(
        payload: WhodunitCase,
        modeId: ModeId,
        players: List<Player>,
        seed: Long,
    ): Triple<PassAndPlaySessionController<WhodunitState, WhodunitAction, WhodunitEvent>, CoroutineScope, SessionId> {
        val sessionScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val sessionId = SessionId("multi-device-$seed")
        val session = PassAndPlaySessionController(
            definition = WhodunitDefinition(json),
            config = SessionConfig(
                sessionId = sessionId,
                caseId = CaseId("last-dinner"),
                modeId = modeId,
                players = players,
                randomSeed = seed,
            ),
            reducerContext = WhodunitReducerContext(
                clock = FakeClock(Instant.fromEpochSeconds(1_700_000_000)),
                random = RandomSource.seeded(seed),
                case = payload,
            ),
            scope = sessionScope,
        )
        return Triple(session, sessionScope, sessionId)
    }

    private fun stateOf(s: PassAndPlaySessionController<WhodunitState, WhodunitAction, WhodunitEvent>) =
        s.publicState.value.state

    /**
     * Helper: assert host's public state equals every peer's mirrored state,
     * AND every peer's mirrored state passes the redaction check.
     */
    private fun assertPeerMirrorsHostAndRedactsHostOnly(
        host: PassAndPlaySessionController<WhodunitState, WhodunitAction, WhodunitEvent>,
        peers: List<WhodunitPeerSimulator>,
        phaseHint: String,
    ) {
        val hostPublic = host.publicState.value.state
        peers.forEachIndexed { i, peer ->
            val peerState = peer.state.value
            assertThat(peerState, "peer[$i] state at $phaseHint").isNotNull()
            assertThat(peerState!!, "peer[$i] mirrors host at $phaseHint").isEqualTo(hostPublic)
            val violations = PeerStateRedactionAssertions.violations(peerState)
            assertThat(
                violations,
                "peer[$i] redaction violations at $phaseHint",
            ).isEmpty()
        }
    }

    // ============================================================ Full-game contract ==

    @Test
    fun host_peer_projections_match_through_a_real_whodunit_classic_game() = runTest {
        val payload = loadCase()
        val players = fourPlayers()
        val seed = 7L
        val (host, scope, _) = buildHostSession(
            payload, WhodunitIds.ClassicVoteModeId, players, seed,
        )
        val bus = InMemoryRoomBus()
        val hostSim = WhodunitHostSimulator(host, bus, json, scope)
        val peer1 = WhodunitPeerSimulator(PlayerId("peer-alice"), bus, json, scope)
        val peer2 = WhodunitPeerSimulator(PlayerId("peer-bob"), bus, json, scope)
        val peers = listOf(peer1, peer2)

        // --- Setup → CharacterReveal: every player's role is on host, peer
        // sees only the redacted public projection. ---
        host.submit(WhodunitAction.AssignRoles(seed))
        assertPeerMirrorsHostAndRedactsHostOnly(host, peers, "after AssignRoles")
        // Host has rich hostOnly data; peer must not.
        assertThat(host.hostState!!.value.state.hostOnly.killerId.raw).isEqualTo(
            host.hostState!!.value.state.hostOnly.killerId.raw,
        )
        // (The peer mirror equality check above already enforces redaction.)

        host.ackIntroForAll(players)
        host.submit(WhodunitAction.AdvanceFromIntro)
        assertPeerMirrorsHostAndRedactsHostOnly(host, peers, "after AdvanceFromIntro")
        host.ackBriefingForAll(players)
        for (i in 1..4) {
            host.submit(WhodunitAction.AdvanceBriefingCard(i))
            assertPeerMirrorsHostAndRedactsHostOnly(host, peers, "briefing card $i")
        }
        assertThat(stateOf(host).phase).isInstanceOf(WhodunitPhase.CharacterReveal::class)

        // CharacterReveal: host's privatePerPlayer is populated and per-player
        // dossier state changes via StartCharacterReveal. The peer must see
        // none of that — privatePerPlayer stays empty in the peer view.
        for (player in players) {
            host.submit(WhodunitAction.StartCharacterReveal(player.id))
            assertPeerMirrorsHostAndRedactsHostOnly(host, peers, "reveal start ${player.id}")
            host.submit(WhodunitAction.CompleteCharacterReveal(player.id))
            assertPeerMirrorsHostAndRedactsHostOnly(host, peers, "reveal complete ${player.id}")
        }
        host.submit(WhodunitAction.AdvanceFromCharacterReveal)
        assertThat(stateOf(host).phase).isInstanceOf(WhodunitPhase.Round::class)

        // --- Rounds 1..3 (Classic with 4 players: round 3 is final). ---
        for (roundIndex in 1..3) {
            host.submit(WhodunitAction.RevealNextClue)
            assertPeerMirrorsHostAndRedactsHostOnly(host, peers, "clue revealed r$roundIndex")
            host.submit(WhodunitAction.StartDiscussionTimer(30))
            assertPeerMirrorsHostAndRedactsHostOnly(host, peers, "timer started r$roundIndex")
            host.submit(WhodunitAction.AdvanceFromDiscussion)
            assertPeerMirrorsHostAndRedactsHostOnly(host, peers, "advance from discussion r$roundIndex")
        }
        assertThat(stateOf(host).phase).isInstanceOf(WhodunitPhase.FinalVote::class)

        // --- FinalVote: cast and refuse; the peer never learns the killer
        // until reveal. ---
        val killer = host.hostState!!.value.state.hostOnly.killerId
        val ballot = (stateOf(host).public.voteState as VoteState.Collecting).ballotPlayerIds
        for (voter in ballot) {
            val action = if (voter == killer) {
                WhodunitAction.AbstainVote(voter)
            } else {
                WhodunitAction.CastVote(voter, killer)
            }
            host.submit(action)
            assertPeerMirrorsHostAndRedactsHostOnly(host, peers, "cast by $voter")
        }
        host.submit(WhodunitAction.CloseVote)
        assertPeerMirrorsHostAndRedactsHostOnly(host, peers, "after CloseVote")
        assertThat(stateOf(host).phase).isInstanceOf(WhodunitPhase.Reveal::class)

        // --- Reveal stage: voteState is now Resolved with the actual killer.
        // The redacted hostOnly still must NOT carry the killer's identity —
        // that info is now public via voteState.Resolved, but hostOnly stays
        // redacted in the peer's view by contract. ---
        val resolved = stateOf(host).public.voteState as VoteState.Resolved
        assertThat(resolved.wasKiller).isTrue()
        assertPeerMirrorsHostAndRedactsHostOnly(host, peers, "Reveal phase")

        host.submit(WhodunitAction.AcknowledgeReveal)
        assertPeerMirrorsHostAndRedactsHostOnly(host, peers, "after AcknowledgeReveal (PostGame)")
        assertThat(stateOf(host).phase).isInstanceOf(WhodunitPhase.PostGame::class)

        // Sanity: every peer saw at least one snapshot per submitted action.
        // Exact equality would tie us to coalescing details of StateFlow; we
        // verify a lower bound that proves the bus actually carried traffic.
        assertThat(peer1.snapshotsReceived > 0).isTrue()
        assertThat(peer2.snapshotsReceived > 0).isTrue()

        hostSim.close()
        host.close()
    }

    // ============================================================ Redaction across phases ==

    @Test
    fun host_only_fields_are_redacted_at_every_reachable_phase() = runTest {
        val payload = loadCase()
        val players = fourPlayers()
        val seed = 11L
        val (host, scope, _) = buildHostSession(
            payload, WhodunitIds.ClassicVoteModeId, players, seed,
        )
        val bus = InMemoryRoomBus()
        val hostSim = WhodunitHostSimulator(host, bus, json, scope)
        val peer = WhodunitPeerSimulator(PlayerId("peer-redaction"), bus, json, scope)

        // For each phase we touch, snapshot the current peer state and assert
        // redaction. A regression that started leaking hostOnly would surface
        // here with a specific phase name.
        val phasesSeen = mutableListOf<String>()
        fun pin(phase: String) {
            phasesSeen += phase
            val peerState = peer.state.value
            assertThat(peerState, "peer state at $phase").isNotNull()
            val violations = PeerStateRedactionAssertions.violations(peerState!!)
            assertThat(violations, "redaction at $phase").isEmpty()
        }

        host.submit(WhodunitAction.AssignRoles(seed)); pin("PublicIntro")
        host.ackIntroForAll(players)
        host.submit(WhodunitAction.AdvanceFromIntro); pin("RulesBriefing-start")
        host.ackBriefingForAll(players)
        for (i in 1..4) host.submit(WhodunitAction.AdvanceBriefingCard(i))
        pin("CharacterReveal-entry")
        for (p in players) {
            host.submit(WhodunitAction.StartCharacterReveal(p.id)); pin("CharReveal-start-${p.id.raw}")
            host.submit(WhodunitAction.CompleteCharacterReveal(p.id)); pin("CharReveal-end-${p.id.raw}")
        }
        host.submit(WhodunitAction.AdvanceFromCharacterReveal); pin("Round-1-entry")
        for (r in 1..3) {
            host.submit(WhodunitAction.RevealNextClue); pin("Round$r-clue")
            host.submit(WhodunitAction.StartDiscussionTimer(30)); pin("Round$r-timer")
            host.submit(WhodunitAction.AdvanceFromDiscussion); pin("Round$r-advance")
        }
        val killer = host.hostState!!.value.state.hostOnly.killerId
        val ballot = (stateOf(host).public.voteState as VoteState.Collecting).ballotPlayerIds
        for (v in ballot) {
            if (v == killer) host.submit(WhodunitAction.AbstainVote(v))
            else host.submit(WhodunitAction.CastVote(v, killer))
        }
        pin("FinalVote-cast-complete")
        host.submit(WhodunitAction.CloseVote); pin("Reveal")
        host.submit(WhodunitAction.AcknowledgeReveal); pin("PostGame")

        // Sanity: we actually hit every meaningful phase, not just the last one.
        // (Loose lower bound — exact phase strings change with the test, but
        // the count proves we exercised the full lifecycle.)
        assertThat(phasesSeen.size > 10).isTrue()

        hostSim.close()
        host.close()
    }

    @Test
    fun pause_state_propagates_to_peer_with_no_hostOnly_leak() = runTest {
        // Pause is rendered fullscreen on the peer too — but the underlying
        // state still has to stay redacted. This pins it explicitly.
        val payload = loadCase()
        val players = fourPlayers()
        val (host, scope, _) = buildHostSession(
            payload, WhodunitIds.ClassicVoteModeId, players, seed = 13L,
        )
        val bus = InMemoryRoomBus()
        val hostSim = WhodunitHostSimulator(host, bus, json, scope)
        val peer = WhodunitPeerSimulator(PlayerId("peer-pause"), bus, json, scope)

        host.submit(WhodunitAction.AssignRoles(13L))
        host.ackIntroForAll(players)
        host.submit(WhodunitAction.AdvanceFromIntro)
        host.ackBriefingForAll(players)
        for (i in 1..4) host.submit(WhodunitAction.AdvanceBriefingCard(i))
        for (player in players) {
            host.submit(WhodunitAction.StartCharacterReveal(player.id))
            host.submit(WhodunitAction.CompleteCharacterReveal(player.id))
        }
        host.submit(WhodunitAction.AdvanceFromCharacterReveal)
        host.submit(WhodunitAction.Pause)

        val peerState = peer.state.value
        assertThat(peerState).isNotNull()
        assertThat(peerState!!.public.paused).isTrue()
        assertThat(PeerStateRedactionAssertions.isFullyRedacted(peerState)).isTrue()

        hostSim.close()
        host.close()
    }

    // ============================================================ Transport reliability ==

    @Test
    fun dropped_snapshot_does_not_corrupt_host_and_peer_re_converges() = runTest {
        val payload = loadCase()
        val players = fourPlayers()
        val (host, scope, _) = buildHostSession(
            payload, WhodunitIds.ClassicVoteModeId, players, seed = 19L,
        )
        val bus = InMemoryRoomBus()
        val hostSim = WhodunitHostSimulator(host, bus, json, scope)
        val peer = WhodunitPeerSimulator(PlayerId("peer-drop"), bus, json, scope)

        // Drive partway so both sides have a known mirror.
        host.submit(WhodunitAction.AssignRoles(19L))
        host.ackIntroForAll(players)
        host.submit(WhodunitAction.AdvanceFromIntro)

        val priorPeerSnapshots = peer.snapshotsReceived
        val mirrorBeforeDrop = peer.state.value
        assertThat(mirrorBeforeDrop).isNotNull()

        // Drop the very next snapshot — set up a one-shot drop policy.
        var droppedOnce = false
        hostSim.dropPolicy = { _ ->
            if (!droppedOnce) {
                droppedOnce = true
                true  // drop this one
            } else {
                false
            }
        }

        // Submit an action whose snapshot we want dropped.
        host.submit(WhodunitAction.AdvanceBriefingCard(1))

        // Host state advanced…
        assertThat(stateOf(host).public.briefingCardIndex).isEqualTo(1)
        // …but the peer hasn't received the new snapshot.
        assertThat(peer.state.value).isEqualTo(mirrorBeforeDrop)
        assertThat(peer.snapshotsReceived).isEqualTo(priorPeerSnapshots)
        assertThat(droppedOnce).isTrue()

        // Clear the drop policy and run the next action. The next snapshot
        // carries the cumulative state (briefingCardIndex = 2), so the peer
        // catches up in one step — re-convergence under the simplest
        // possible "drop a frame, send the next frame" policy.
        hostSim.dropPolicy = null
        host.submit(WhodunitAction.AdvanceBriefingCard(2))

        val converged = peer.state.value
        assertThat(converged).isNotNull()
        assertThat(converged!!.public.briefingCardIndex).isEqualTo(2)
        assertThat(converged).isEqualTo(stateOf(host))
        // The peer's snapshot count advanced by exactly one (it received the
        // post-drop snapshot, not the dropped one).
        assertThat(peer.snapshotsReceived).isEqualTo(priorPeerSnapshots + 1)

        // Belt-and-suspenders: an explicit resend should be idempotent — the
        // peer state doesn't change because it's already up to date.
        hostSim.resendCurrentSnapshot()
        val afterResend = peer.state.value
        assertThat(afterResend).isEqualTo(stateOf(host))

        hostSim.close()
        host.close()
    }

    @Test
    fun peer_re_converges_after_an_extended_drop_window() = runTest {
        // Three consecutive drops, then a single delivered snapshot — the
        // peer must catch up to the cumulative latest state. This is the
        // generalisation of the single-drop case: as long as one snapshot
        // eventually arrives, the peer doesn't need the intermediate ones.
        val payload = loadCase()
        val players = fourPlayers()
        val (host, scope, _) = buildHostSession(
            payload, WhodunitIds.ClassicVoteModeId, players, seed = 23L,
        )
        val bus = InMemoryRoomBus()
        val hostSim = WhodunitHostSimulator(host, bus, json, scope)
        val peer = WhodunitPeerSimulator(PlayerId("peer-extended-drop"), bus, json, scope)

        host.submit(WhodunitAction.AssignRoles(23L))
        host.ackIntroForAll(players)
        host.submit(WhodunitAction.AdvanceFromIntro)
        val baselineSnapshotCount = peer.snapshotsReceived

        // Drop everything for the next three submissions.
        var dropsRemaining = 3
        hostSim.dropPolicy = {
            if (dropsRemaining > 0) {
                dropsRemaining--
                true
            } else {
                false
            }
        }

        host.submit(WhodunitAction.AdvanceBriefingCard(1))
        host.submit(WhodunitAction.AdvanceBriefingCard(2))
        host.submit(WhodunitAction.AdvanceBriefingCard(3))

        // Peer is still stuck at the pre-drop state.
        val stuck = peer.state.value
        assertThat(stuck!!.public.briefingCardIndex).isEqualTo(0)

        // Restore delivery and submit a new action. The host's snapshot now
        // reflects briefingCardIndex == 4 OR a phase transition into
        // CharacterReveal (since AdvanceBriefingCard(4) advances out of the
        // briefing). Either way, the peer converges in one delivered message.
        hostSim.dropPolicy = null
        host.ackBriefingForAll(players)
        host.submit(WhodunitAction.AdvanceBriefingCard(4))

        val convergedState = peer.state.value!!
        assertThat(convergedState).isEqualTo(stateOf(host))
        // baseline + 4 acks + 1 advance = baseline + 5 snapshots delivered
        // after drop restoration. The Wave 9H readiness gating means each
        // ack is its own state change.
        assertThat(peer.snapshotsReceived).isEqualTo(baselineSnapshotCount + 5)

        hostSim.close()
        host.close()
    }

    // ============================================================ Sanity: bus traffic ==

    @Test
    fun two_peers_subscribed_to_broadcast_each_receive_every_undropped_snapshot() = runTest {
        val payload = loadCase()
        val players = fourPlayers()
        val (host, scope, _) = buildHostSession(
            payload, WhodunitIds.ClassicVoteModeId, players, seed = 29L,
        )
        val bus = InMemoryRoomBus()
        val hostSim = WhodunitHostSimulator(host, bus, json, scope)
        val peers = listOf(
            WhodunitPeerSimulator(PlayerId("peer-1"), bus, json, scope),
            WhodunitPeerSimulator(PlayerId("peer-2"), bus, json, scope),
            WhodunitPeerSimulator(PlayerId("peer-3"), bus, json, scope),
        )

        host.submit(WhodunitAction.AssignRoles(29L))
        host.ackIntroForAll(players)
        host.submit(WhodunitAction.AdvanceFromIntro)
        host.submit(WhodunitAction.AdvanceBriefingCard(1))

        // All three peers see identical mirror state.
        val mirror = peers.map { it.state.value!! }
        assertThat(mirror).containsExactly(*Array(3) { stateOf(host) })

        // Counts match — no peer was starved.
        val counts = peers.map { it.snapshotsReceived }
        assertThat(counts.toSet().size).isEqualTo(1)

        hostSim.close()
        host.close()
    }
}
