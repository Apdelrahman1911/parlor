package com.parlor.games.mafia.multidevice

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isFalse
import assertk.assertions.isNull
import com.parlor.core.ids.PlayerId
import com.parlor.core.random.RandomSource
import com.parlor.engine.state.Player
import com.parlor.games.mafia.domain.phase.MafiaPhase
import com.parlor.games.mafia.domain.projection.MafiaProjectionPolicy
import com.parlor.games.mafia.domain.rules.RoleAssignment
import com.parlor.games.mafia.domain.settings.MafiaRoleCounts
import com.parlor.games.mafia.domain.settings.MafiaSettingsPresets
import com.parlor.games.mafia.domain.state.ActiveVote
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
import com.parlor.games.mafia.domain.state.VoteAnnouncement
import com.parlor.games.mafia.domain.state.VoteOutcome
import com.parlor.games.mafia.domain.state.team
import kotlin.test.Test

/**
 * Multi-device privacy contract: for EVERY phase and EVERY pair
 * (non-Mafia viewer `p`, Mafia member `m`), the host-side
 * `toPlayer(state, p)` projection must not contain any reference to
 * `m`'s role, coordination snapshot, or detective result. This is the
 * structural guarantee the host bridge relies on — the host never ships
 * a peer another peer's `MafiaPrivate` because `toPlayer` never produces
 * it.
 *
 * Complements `MafiaProjectionPolicyTest`, which proves the same
 * invariants on isolated fields. This test sweeps **every** [MafiaPhase]
 * variant with realistic per-phase data (active votes, pending detective
 * results, coordination snapshots, mid-vote tallies, post-game roster
 * with revealed dead players, …) so a future reducer change that smuggles
 * private info into a public bucket on a specific phase is caught.
 */
class MafiaProjectionLeakTest {

    private val players = (0 until 7).map {
        Player(PlayerId("p$it"), "P$it", seat = it)
    }
    private val counts = MafiaRoleCounts(mafia = 2, detective = 1, doctor = 1)
    private val assignment = RoleAssignment.assign(players, counts, RandomSource.seeded(11L))
    private val mafiaIds: Set<PlayerId> = assignment.roles.filterValues { it == Role.Mafia }.keys
    private val townIds: Set<PlayerId> = assignment.roles.filterValues { it.team == Team.Town }.keys
    private val coordSnapshot = MafiaCoordinationSnapshot(
        round = 1,
        submissions = mafiaIds.associateWith { players[6].id },
    )

    private fun stateAt(phase: MafiaPhase): MafiaState {
        val privatePerPlayer = players.associate { p ->
            val role = assignment.roles.getValue(p.id)
            p.id to MafiaPrivate(
                role = role,
                team = role.team,
                knownTeammates = assignment.knownTeammates.getValue(p.id),
                mafiaCoordination = if (role.team == Team.Mafia) coordSnapshot else null,
                pendingDetectiveResult = if (role == Role.Detective) {
                    DetectiveResult(
                        day = 1,
                        target = players[6].id,
                        seesAs = DetectiveSeesAs.Mafia,
                    )
                } else null,
                lastSuspicion = if (role == Role.Civilian) players[6].id else null,
                pendingNightChoice = if (role == Role.Mafia) players[6].id else null,
            )
        }

        // PostGame reveals dead roles on the public roster — make sure
        // even *that* projection doesn't leak coordination/detective data.
        val roster = players.mapIndexed { i, p ->
            val revealed = if (phase is MafiaPhase.PostGame) assignment.roles[p.id] else null
            val alive = phase !is MafiaPhase.PostGame || i < 5
            PublicPlayerSlot(p.id, p.displayName, p.seat, alive = alive, revealedRole = revealed)
        }

        return MafiaState(
            public = MafiaPublic(
                settings = MafiaSettingsPresets.forPlayerCount(players.size),
                day = 1,
                roster = roster,
                lastNight = NightAnnouncement(
                    day = 1,
                    killedPlayerId = players[6].id,
                    wasSaved = false,
                ),
                lastVote = VoteAnnouncement(
                    day = 1,
                    tally = mapOf(players[6].id to 4),
                    eliminatedPlayerId = players[6].id,
                    outcome = VoteOutcome.Eliminated,
                ),
                activeVote = if (phase is MafiaPhase.Voting) {
                    ActiveVote(
                        day = phase.day,
                        revoteRound = phase.revoteRound,
                        candidates = players.map { it.id },
                        ballot = players.map { it.id },
                        castSoFar = mapOf(players[0].id to players[6].id),
                        abstained = emptySet(),
                    )
                } else null,
                winner = if (phase is MafiaPhase.PostGame) Team.Town else null,
            ),
            privatePerPlayer = privatePerPlayer,
            hostOnly = MafiaHostOnly(
                fullRoleMap = assignment.roles,
                randomSeed = 11L,
                nightLog = listOf(
                    NightResolutionRecord(
                        day = 1,
                        mafiaTarget = players[6].id,
                        mafiaTargetTied = false,
                        doctorProtect = null,
                        detectiveInspect = players[6].id,
                        detectiveResult = DetectiveSeesAs.Mafia,
                        killedPlayerId = players[6].id,
                    ),
                ),
                voteLog = emptyList(),
            ),
            phase = phase,
            players = players,
        )
    }

    private val allPhases: List<MafiaPhase> = listOf(
        MafiaPhase.Setup,
        MafiaPhase.RoleAssignment,
        MafiaPhase.Night(day = 1, mafiaCoordinationRound = 1),
        MafiaPhase.Night(day = 1, mafiaCoordinationRound = 2),
        MafiaPhase.NightAnnouncement(day = 1),
        MafiaPhase.Discussion(day = 1),
        MafiaPhase.Voting(day = 1, revoteRound = 0),
        MafiaPhase.Voting(day = 1, revoteRound = 1),
        MafiaPhase.VoteAnnouncement(day = 1),
        MafiaPhase.PostGame,
    )

    @Test
    fun no_non_mafia_projection_references_any_mafia_private_data() {
        for (phase in allPhases) {
            val state = stateAt(phase)
            for (townId in townIds) {
                val view = MafiaProjectionPolicy.toPlayer(state, townId).state
                val ownSlice = view.privatePerPlayer[townId]

                // Coordination is Mafia-only; town viewer must not see it.
                assertThat(ownSlice?.mafiaCoordination, "phase=$phase town=$townId mafiaCoordination").isNull()
                // No Mafia teammate id leaks into a non-Mafia viewer's known list.
                for (m in mafiaIds) {
                    assertThat(
                        ownSlice?.knownTeammates.orEmpty().contains(m),
                        "phase=$phase town=$townId knows mafia=$m",
                    ).isFalse()
                }
                // hostOnly fully redacted on peer projection.
                assertThat(view.hostOnly.fullRoleMap, "phase=$phase town=$townId").isEmpty()
                assertThat(view.hostOnly.nightLog, "phase=$phase town=$townId").isEmpty()
                // Other players' private slices never present.
                val foreignIds = view.privatePerPlayer.keys - townId
                assertThat(foreignIds, "phase=$phase town=$townId foreign slices").isEmpty()
            }
        }
    }

    @Test
    fun no_non_detective_projection_references_any_detective_result() {
        for (phase in allPhases) {
            val state = stateAt(phase)
            for ((id, role) in assignment.roles) {
                if (role == Role.Detective) continue
                val view = MafiaProjectionPolicy.toPlayer(state, id).state
                val ownSlice = view.privatePerPlayer[id]
                assertThat(
                    ownSlice?.pendingDetectiveResult,
                    "phase=$phase non-detective=$id (role=$role)",
                ).isNull()
            }
        }
    }

    @Test
    fun public_projection_never_carries_private_or_host_only_buckets() {
        for (phase in allPhases) {
            val state = stateAt(phase)
            val pub = MafiaProjectionPolicy.toPublic(state).state
            assertThat(pub.privatePerPlayer, "phase=$phase public privatePerPlayer").isEmpty()
            assertThat(pub.hostOnly.fullRoleMap, "phase=$phase public hostOnly.fullRoleMap").isEmpty()
            assertThat(pub.hostOnly.nightLog, "phase=$phase public hostOnly.nightLog").isEmpty()
            assertThat(pub.hostOnly.voteLog, "phase=$phase public hostOnly.voteLog").isEmpty()
        }
    }
}
