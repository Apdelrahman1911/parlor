package com.parlor.games.mafia.snapshot

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.parlor.core.ids.CaseId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.core.random.RandomSource
import com.parlor.core.time.FakeClock
import com.parlor.engine.reducer.DefaultReducerContext
import com.parlor.engine.session.SessionConfig
import com.parlor.engine.state.Player
import com.parlor.games.mafia.MafiaDefinition
import com.parlor.games.mafia.MafiaIds
import com.parlor.games.mafia.domain.action.MafiaAction
import com.parlor.games.mafia.domain.phase.MafiaPhase
import com.parlor.games.mafia.domain.reducer.MafiaReducer
import com.parlor.games.mafia.domain.state.MafiaHostOnly
import com.parlor.games.mafia.domain.state.MafiaState
import com.parlor.games.mafia.domain.state.NightResolutionRecord
import com.parlor.games.mafia.domain.state.Role
import com.parlor.games.mafia.domain.state.Team
import com.parlor.games.mafia.domain.state.VoteRoundRecord
import com.parlor.networking.protocol.MAX_SNAPSHOT_PAYLOAD_BYTES
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.time.Instant

/**
 * Snapshot codec is the persistence/transport boundary for full MafiaState.
 * Any field added to MafiaState must round-trip — otherwise a host crash
 * recovery (or a peer joining mid-game in M3) would silently drop data.
 */
class MafiaSnapshotCodecTest {

    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        encodeDefaults = true
    }

    private val codec = MafiaSnapshotCodec(json)
    private val definition = MafiaDefinition(json)
    private val context = DefaultReducerContext(
        clock = FakeClock(Instant.fromEpochSeconds(1_700_000_000)),
        random = RandomSource.seeded(42L),
    )

    private fun players(n: Int): List<Player> =
        (0 until n).map { Player(PlayerId("p$it"), "P$it", seat = it) }

    private fun fixture(seed: Long = 42L): MafiaState {
        val ps = players(7)
        var state = definition.createInitialState(config(ps, seed))
        state = reduce(state, MafiaAction.StartGame)
        ps.forEach { player ->
            state = reduce(state, MafiaAction.AcknowledgeRoleViewed(player.id))
        }
        return reduce(state, MafiaAction.AdvanceFromRoleAssignment)
    }

    @Test
    fun full_state_round_trips() {
        val original = fixture()
        val decoded = codec.decode(codec.encode(original))
        assertThat(decoded).isEqualTo(original)
    }

    @Test
    fun setup_phase_state_round_trips() {
        val ps = players(5)
        val state = definition.createInitialState(config(ps, seed = 9L))
        assertThat(codec.decode(codec.encode(state))).isEqualTo(state)
    }

    @Test
    fun post_game_state_round_trips_with_winner() {
        val state = townWinFixture()
        assertThat(state.public.winner).isEqualTo(Team.Town)
        assertThat(codec.decode(codec.encode(state))).isEqualTo(state)
    }

    @Test
    fun impossible_public_state_is_rejected_on_encode() {
        val invalid = fixture().copy(
            public = fixture().public.copy(day = 0),
        )

        assertFailsWith<IllegalArgumentException> { codec.encode(invalid) }
    }

    @Test
    fun impossible_public_state_is_rejected_on_decode() {
        val invalid = fixture().copy(
            public = fixture().public.copy(day = 0),
        )
        val payload = json.encodeToString(MafiaState.serializer(), invalid).encodeToByteArray()

        assertFailsWith<IllegalArgumentException> { codec.decode(payload) }
    }

    @Test
    fun voting_phase_with_active_vote_round_trips() {
        var voting = votingFixture(players(7), seed = 42L)
        val ballot = requireNotNull(voting.public.activeVote).ballot
        voting = reduce(voting, MafiaAction.CastVote(ballot[0], ballot[1]))
        voting = reduce(voting, MafiaAction.AbstainVote(ballot[2]))
        assertThat(codec.decode(codec.encode(voting))).isEqualTo(voting)
    }

    @Test
    fun sustained_session_history_keeps_only_the_newest_serialized_records() {
        val base = fixture()
        val totalRecords = MafiaHostOnly.MAX_SERIALIZED_LOG_ENTRIES * 8
        var hostOnly = base.hostOnly

        repeat(totalRecords) { index ->
            hostOnly = hostOnly
                .recordNight(
                    NightResolutionRecord(
                        day = index,
                        mafiaTarget = base.players.first().id,
                        mafiaTargetTied = false,
                        doctorProtect = null,
                        detectiveInspect = null,
                        detectiveResult = null,
                        killedPlayerId = null,
                    ),
                )
                .recordVote(
                    VoteRoundRecord(
                        day = index,
                        revoteRound = 0,
                        tally = mapOf(base.players.first().id to 1),
                        eliminatedPlayerId = null,
                    ),
                )
        }

        assertThat(hostOnly.nightLog.size).isEqualTo(MafiaHostOnly.MAX_SERIALIZED_LOG_ENTRIES)
        assertThat(hostOnly.voteLog.size).isEqualTo(MafiaHostOnly.MAX_SERIALIZED_LOG_ENTRIES)
        assertThat(hostOnly.nightLog.first().day)
            .isEqualTo(totalRecords - MafiaHostOnly.MAX_SERIALIZED_LOG_ENTRIES)
        assertThat(hostOnly.voteLog.first().day)
            .isEqualTo(totalRecords - MafiaHostOnly.MAX_SERIALIZED_LOG_ENTRIES)
        assertThat(hostOnly.nightLog.last().day).isEqualTo(totalRecords - 1)
        assertThat(hostOnly.voteLog.last().day).isEqualTo(totalRecords - 1)

    }

    @Test
    fun current_snapshot_boundaries_reject_oversized_history_instead_of_rewriting_it() {
        val base = fixture()
        val extra = MafiaHostOnly.MAX_SERIALIZED_LOG_ENTRIES + 7
        val oversized = base.copy(
            hostOnly = base.hostOnly.copy(
                nightLog = (0 until extra).map { index ->
                    NightResolutionRecord(
                        day = index,
                        mafiaTarget = null,
                        mafiaTargetTied = false,
                        doctorProtect = null,
                        detectiveInspect = null,
                        detectiveResult = null,
                        killedPlayerId = null,
                    )
                },
                voteLog = (0 until extra).map { index ->
                    VoteRoundRecord(
                        day = index,
                        revoteRound = 0,
                        tally = emptyMap(),
                        eliminatedPlayerId = null,
                    )
                },
            ),
        )

        assertFailsWith<IllegalArgumentException> { codec.encode(oversized) }
        assertFailsWith<IllegalArgumentException> {
            codec.decode(json.encodeToString(MafiaState.serializer(), oversized).encodeToByteArray())
        }
    }

    @Test
    fun current_payload_must_be_canonical_and_bounded() {
        val encoded = codec.encode(fixture())
        assertFailsWith<IllegalArgumentException> {
            codec.decode(" ${encoded.decodeToString()}".encodeToByteArray())
        }
        assertFailsWith<IllegalArgumentException> {
            codec.decode(ByteArray(MAX_SNAPSHOT_PAYLOAD_BYTES + 1) { 'x'.code.toByte() })
        }
    }

    private fun config(roster: List<Player>, seed: Long) = SessionConfig(
        sessionId = SessionId("mafia-codec-$seed-${roster.size}"),
        caseId = CaseId("default"),
        modeId = MafiaIds.ClassicModeId,
        players = roster,
        randomSeed = seed,
    )

    private fun reduce(state: MafiaState, action: MafiaAction): MafiaState =
        MafiaReducer.reduce(state, action, context).newState

    private fun votingFixture(roster: List<Player>, seed: Long): MafiaState {
        var state = definition.createInitialState(config(roster, seed))
        state = reduce(state, MafiaAction.StartGame)
        roster.forEach { state = reduce(state, MafiaAction.AcknowledgeRoleViewed(it.id)) }
        state = reduce(state, MafiaAction.AdvanceFromRoleAssignment)
        roster.forEach { player ->
            val private = state.privatePerPlayer.getValue(player.id)
            state = reduce(
                state,
                when (private.role) {
                    Role.Mafia -> MafiaAction.SubmitMafiaKillVote(player.id, null)
                    Role.Doctor -> MafiaAction.SubmitDoctorProtect(player.id, null)
                    Role.Detective -> MafiaAction.SubmitDetectiveInspect(player.id, null)
                    Role.Civilian -> MafiaAction.SubmitCivilianSuspicion(player.id, null)
                },
            )
        }
        state = reduce(state, MafiaAction.ResolveNight)
        roster.forEach { state = reduce(state, MafiaAction.AcknowledgeNightAnnouncement(it.id)) }
        state = reduce(state, MafiaAction.OpenDiscussion)
        return reduce(state, MafiaAction.OpenVote)
    }

    private fun townWinFixture(): MafiaState {
        var state = votingFixture(players(5), seed = 77L)
        val mafiaId = state.hostOnly.fullRoleMap.entries.single { it.value == Role.Mafia }.key
        requireNotNull(state.public.activeVote).ballot.forEach { voter ->
            state = reduce(
                state,
                if (voter == mafiaId) {
                    MafiaAction.AbstainVote(voter)
                } else {
                    MafiaAction.CastVote(voter, mafiaId)
                },
            )
        }
        return reduce(state, MafiaAction.CloseVote).also {
            check(it.phase == MafiaPhase.PostGame)
        }
    }
}
