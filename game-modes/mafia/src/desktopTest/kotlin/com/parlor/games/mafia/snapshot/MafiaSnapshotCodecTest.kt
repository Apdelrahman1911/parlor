package com.parlor.games.mafia.snapshot

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.parlor.core.ids.PlayerId
import com.parlor.core.random.RandomSource
import com.parlor.engine.state.Player
import com.parlor.games.mafia.domain.phase.MafiaPhase
import com.parlor.games.mafia.domain.rules.RoleAssignment
import com.parlor.games.mafia.domain.settings.MafiaRoleCounts
import com.parlor.games.mafia.domain.settings.MafiaSettingsPresets
import com.parlor.games.mafia.domain.state.DetectiveResult
import com.parlor.games.mafia.domain.state.DetectiveSeesAs
import com.parlor.games.mafia.domain.state.MafiaCoordinationSnapshot
import com.parlor.games.mafia.domain.state.MafiaHostOnly
import com.parlor.games.mafia.domain.state.MafiaPrivate
import com.parlor.games.mafia.domain.state.MafiaPublic
import com.parlor.games.mafia.domain.state.MafiaState
import com.parlor.games.mafia.domain.state.NightAnnouncement
import com.parlor.games.mafia.domain.state.NightResolutionRecord
import com.parlor.games.mafia.domain.state.PublicPlayerSlot
import com.parlor.games.mafia.domain.state.Role
import com.parlor.games.mafia.domain.state.Team
import com.parlor.games.mafia.domain.state.VoteRoundRecord
import com.parlor.games.mafia.domain.state.team
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertFailsWith

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

    private fun players(n: Int): List<Player> =
        (0 until n).map { Player(PlayerId("p$it"), "P$it", seat = it) }

    private fun fixture(seed: Long = 42L): MafiaState {
        val ps = players(7)
        val counts = MafiaRoleCounts(mafia = 2, detective = 1, doctor = 1)
        val assignment = RoleAssignment.assign(ps, counts, RandomSource.seeded(seed))
        val mafiaIds = assignment.roles.filterValues { it == Role.Mafia }.keys
        val coord = MafiaCoordinationSnapshot(round = 1, submissions = mapOf(mafiaIds.first() to ps.last().id))
        val privatePerPlayer = ps.associate { p ->
            val role = assignment.roles.getValue(p.id)
            p.id to MafiaPrivate(
                role = role,
                team = role.team,
                knownTeammates = assignment.knownTeammates.getValue(p.id),
                mafiaCoordination = if (role.team == Team.Mafia) coord else null,
                pendingDetectiveResult = if (role == Role.Detective) {
                    DetectiveResult(day = 1, target = ps.first().id, seesAs = DetectiveSeesAs.Town)
                } else null,
                pendingNightChoice = if (role == Role.Mafia) ps.last().id else null,
                lastSuspicion = if (role == Role.Civilian) ps[1].id else null,
            )
        }
        return MafiaState(
            public = MafiaPublic(
                settings = MafiaSettingsPresets.forPlayerCount(ps.size),
                day = 1,
                roster = ps.map { PublicPlayerSlot(it.id, it.displayName, it.seat) },
            ),
            privatePerPlayer = privatePerPlayer,
            hostOnly = MafiaHostOnly(
                fullRoleMap = assignment.roles,
                randomSeed = seed,
            ),
            phase = MafiaPhase.Night(day = 1, mafiaCoordinationRound = 1),
            players = ps,
        )
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
        val state = MafiaState(
            public = MafiaPublic(
                settings = MafiaSettingsPresets.forPlayerCount(5),
                roster = ps.map { PublicPlayerSlot(it.id, it.displayName, it.seat) },
            ),
            privatePerPlayer = emptyMap(),
            hostOnly = MafiaHostOnly(fullRoleMap = emptyMap(), randomSeed = 9L),
            phase = MafiaPhase.Setup,
            players = ps,
        )
        assertThat(codec.decode(codec.encode(state))).isEqualTo(state)
    }

    @Test
    fun post_game_state_round_trips_with_winner() {
        val base = fixture()
        val state = base.copy(
            phase = MafiaPhase.PostGame,
            public = base.public.copy(
                roster = base.public.roster.map { slot ->
                    slot.copy(
                        alive = base.hostOnly.fullRoleMap[slot.playerId]?.team == Team.Town,
                        revealedRole = base.hostOnly.fullRoleMap.getValue(slot.playerId),
                    )
                },
                winner = Team.Town,
            ),
        )
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
        val base = fixture()
        val voting = base.copy(
            phase = MafiaPhase.Voting(day = 1, revoteRound = 0),
            public = base.public.copy(
                lastNight = NightAnnouncement(day = 1, killedPlayerId = null, wasSaved = false),
                activeVote = com.parlor.games.mafia.domain.state.ActiveVote(
                    day = 1,
                    revoteRound = 0,
                    candidates = base.players.map { it.id },
                    ballot = base.players.map { it.id },
                    castSoFar = mapOf(base.players[0].id to base.players[1].id),
                    abstained = setOf(base.players[2].id),
                ),
            ),
        )
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

        val bounded = base.copy(hostOnly = hostOnly)
        assertThat(codec.decode(codec.encode(bounded))).isEqualTo(bounded)
    }

    @Test
    fun serialization_boundaries_trim_legacy_oversized_history() {
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

        val decoded = codec.decode(codec.encode(oversized))
        val decodedLegacyPayload = codec.decode(
            json.encodeToString(MafiaState.serializer(), oversized).encodeToByteArray(),
        )
        assertThat(decodedLegacyPayload).isEqualTo(decoded)
        assertThat(decoded.hostOnly.nightLog.size)
            .isEqualTo(MafiaHostOnly.MAX_SERIALIZED_LOG_ENTRIES)
        assertThat(decoded.hostOnly.voteLog.size)
            .isEqualTo(MafiaHostOnly.MAX_SERIALIZED_LOG_ENTRIES)
        assertThat(decoded.hostOnly.nightLog.first().day).isEqualTo(7)
        assertThat(decoded.hostOnly.voteLog.first().day).isEqualTo(7)
        assertThat(decoded.hostOnly.nightLog.last().day).isEqualTo(extra - 1)
        assertThat(decoded.hostOnly.voteLog.last().day).isEqualTo(extra - 1)
    }
}
