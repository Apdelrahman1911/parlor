package com.parlor.games.mafia.domain.projection

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.containsOnly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.parlor.core.ids.PlayerId
import com.parlor.core.random.RandomSource
import com.parlor.engine.state.Player
import com.parlor.games.mafia.domain.phase.MafiaPhase
import com.parlor.games.mafia.domain.rules.RoleAssignment
import com.parlor.games.mafia.domain.settings.MafiaRoleCounts
import com.parlor.games.mafia.domain.settings.MafiaSettings
import com.parlor.games.mafia.domain.settings.MafiaSettingsPresets
import com.parlor.games.mafia.domain.state.DetectiveResult
import com.parlor.games.mafia.domain.state.DetectiveSeesAs
import com.parlor.games.mafia.domain.state.MafiaCoordinationSnapshot
import com.parlor.games.mafia.domain.state.MafiaHostOnly
import com.parlor.games.mafia.domain.state.MafiaPrivate
import com.parlor.games.mafia.domain.state.MafiaPublic
import com.parlor.games.mafia.domain.state.MafiaState
import com.parlor.games.mafia.domain.state.PublicPlayerSlot
import com.parlor.games.mafia.domain.state.Role
import com.parlor.games.mafia.domain.state.Team
import com.parlor.games.mafia.domain.state.team
import kotlin.test.Test

/**
 * The privacy contract test. Mafia's correctness rests on these invariants:
 *  - Living-player roles never appear in PublicPlayerSlot.revealedRole.
 *  - `toPublic` strips privatePerPlayer AND hostOnly.
 *  - `toPlayer(p)` exposes only `p`'s private slice — no other player's
 *    role, coordination snapshot, or detective result is recoverable from
 *    the projected state.
 *  - For any non-Mafia viewer, MafiaCoordinationSnapshot is null.
 *  - For any non-Detective viewer, pendingDetectiveResult is null.
 *
 * The property test seeds role assignment across multiple RNG states and
 * verifies the projection invariants hold for every (non-Mafia, Mafia)
 * pair regardless of the specific role layout.
 */
class MafiaProjectionPolicyTest {

    private fun players(n: Int): List<Player> =
        (0 until n).map { Player(PlayerId("p$it"), "P$it", seat = it) }

    private fun buildState(
        players: List<Player>,
        roles: Map<PlayerId, Role>,
        knownTeammates: Map<PlayerId, Set<PlayerId>>,
        seed: Long,
        phase: MafiaPhase = MafiaPhase.Night(day = 1),
        detectivePending: DetectiveResult? = null,
    ): MafiaState {
        val mafiaIds = roles.filterValues { it == Role.Mafia }.keys
        val coordSnapshot = MafiaCoordinationSnapshot(
            round = 1,
            submissions = mapOf(mafiaIds.first() to players.first().id),
        )
        val privatePerPlayer = players.associate { p ->
            val role = roles.getValue(p.id)
            p.id to MafiaPrivate(
                role = role,
                team = role.team,
                knownTeammates = knownTeammates.getValue(p.id),
                mafiaCoordination = if (role.team == Team.Mafia) coordSnapshot else null,
                pendingDetectiveResult = if (role == Role.Detective) detectivePending else null,
                lastSuspicion = if (role == Role.Civilian) players.first().id else null,
                pendingNightChoice = if (role == Role.Mafia) players.first().id else null,
            )
        }
        return MafiaState(
            public = MafiaPublic(
                settings = MafiaSettingsPresets.forPlayerCount(players.size),
                day = 1,
                roster = players.map { PublicPlayerSlot(it.id, it.displayName, it.seat) },
            ),
            privatePerPlayer = privatePerPlayer,
            hostOnly = MafiaHostOnly(
                fullRoleMap = roles,
                randomSeed = seed,
            ),
            phase = phase,
            players = players,
        )
    }

    @Test
    fun to_public_strips_private_and_host_only_buckets() {
        val ps = players(7)
        val assignment = RoleAssignment.assign(
            players = ps,
            counts = MafiaRoleCounts(mafia = 2, detective = 1, doctor = 1),
            random = RandomSource.seeded(11L),
        )
        val state = buildState(ps, assignment.roles, assignment.knownTeammates, seed = 11L)

        val public = MafiaProjectionPolicy.toPublic(state).state

        assertThat(public.privatePerPlayer).isEmpty()
        assertThat(public.hostOnly.fullRoleMap).isEmpty()
        assertThat(public.hostOnly.randomSeed).isEqualTo(0L)
        assertThat(public.hostOnly.nightLog).isEmpty()
        assertThat(public.hostOnly.voteLog).isEmpty()
    }

    @Test
    fun to_public_keeps_living_player_roles_null() {
        val ps = players(7)
        val assignment = RoleAssignment.assign(
            players = ps,
            counts = MafiaRoleCounts(mafia = 2, detective = 1, doctor = 1),
            random = RandomSource.seeded(11L),
        )
        val state = buildState(ps, assignment.roles, assignment.knownTeammates, seed = 11L)
        val public = MafiaProjectionPolicy.toPublic(state).state

        for (slot in public.public.roster) {
            // All players are alive in this fixture.
            assertThat(slot.revealedRole).isNull()
        }
    }

    @Test
    fun to_player_exposes_only_own_private_slice() {
        val ps = players(7)
        val assignment = RoleAssignment.assign(
            players = ps,
            counts = MafiaRoleCounts(mafia = 2, detective = 1, doctor = 1),
            random = RandomSource.seeded(11L),
        )
        val state = buildState(ps, assignment.roles, assignment.knownTeammates, seed = 11L)

        val viewer = ps[3].id
        val view = MafiaProjectionPolicy.toPlayer(state, viewer).state

        assertThat(view.privatePerPlayer.keys).containsExactlyInAnyOrder(viewer)
        assertThat(view.hostOnly.fullRoleMap).isEmpty()
    }

    @Test
    fun non_mafia_viewer_sees_no_mafia_coordination_snapshot() {
        val ps = players(7)
        val assignment = RoleAssignment.assign(
            players = ps,
            counts = MafiaRoleCounts(mafia = 2, detective = 1, doctor = 1),
            random = RandomSource.seeded(11L),
        )
        val state = buildState(ps, assignment.roles, assignment.knownTeammates, seed = 11L)

        val townViewer = assignment.roles.entries.first { it.value.team == Team.Town }.key
        val view = MafiaProjectionPolicy.toPlayer(state, townViewer).state
        val ownSlice = view.privatePerPlayer.getValue(townViewer)
        assertThat(ownSlice.mafiaCoordination).isNull()
    }

    @Test
    fun mafia_viewer_sees_mafia_coordination_snapshot() {
        val ps = players(7)
        val assignment = RoleAssignment.assign(
            players = ps,
            counts = MafiaRoleCounts(mafia = 2, detective = 1, doctor = 1),
            random = RandomSource.seeded(11L),
        )
        val state = buildState(ps, assignment.roles, assignment.knownTeammates, seed = 11L)

        val mafiaViewer = assignment.roles.entries.first { it.value == Role.Mafia }.key
        val view = MafiaProjectionPolicy.toPlayer(state, mafiaViewer).state
        val ownSlice = view.privatePerPlayer.getValue(mafiaViewer)
        assertThat(ownSlice.mafiaCoordination != null).isTrue()
    }

    @Test
    fun non_detective_viewer_has_no_pending_detective_result() {
        val ps = players(7)
        val assignment = RoleAssignment.assign(
            players = ps,
            counts = MafiaRoleCounts(mafia = 2, detective = 1, doctor = 1),
            random = RandomSource.seeded(11L),
        )
        val state = buildState(
            ps, assignment.roles, assignment.knownTeammates, seed = 11L,
            detectivePending = DetectiveResult(day = 1, target = ps.first().id, seesAs = DetectiveSeesAs.Mafia),
        )

        for ((id, role) in assignment.roles) {
            if (role == Role.Detective) continue
            val view = MafiaProjectionPolicy.toPlayer(state, id).state
            val slice = view.privatePerPlayer[id] ?: continue
            assertThat(slice.pendingDetectiveResult).isNull()
        }
    }

    @Test
    fun detective_viewer_sees_their_own_pending_result() {
        val ps = players(7)
        val assignment = RoleAssignment.assign(
            players = ps,
            counts = MafiaRoleCounts(mafia = 2, detective = 1, doctor = 1),
            random = RandomSource.seeded(11L),
        )
        val target = ps.first { it.id != assignment.roles.entries.first { e -> e.value == Role.Detective }.key }.id
        val state = buildState(
            ps, assignment.roles, assignment.knownTeammates, seed = 11L,
            detectivePending = DetectiveResult(day = 1, target = target, seesAs = DetectiveSeesAs.Mafia),
        )
        val detectiveId = assignment.roles.entries.first { it.value == Role.Detective }.key
        val view = MafiaProjectionPolicy.toPlayer(state, detectiveId).state
        val ownSlice = view.privatePerPlayer.getValue(detectiveId)
        assertThat(ownSlice.pendingDetectiveResult).isEqualTo(
            DetectiveResult(day = 1, target = target, seesAs = DetectiveSeesAs.Mafia),
        )
    }

    @Test
    fun to_host_returns_full_state_unmodified() {
        val ps = players(7)
        val assignment = RoleAssignment.assign(
            players = ps,
            counts = MafiaRoleCounts(mafia = 2, detective = 1, doctor = 1),
            random = RandomSource.seeded(11L),
        )
        val state = buildState(ps, assignment.roles, assignment.knownTeammates, seed = 11L)
        val host = MafiaProjectionPolicy.toHost(state).state
        assertThat(host).isEqualTo(state)
    }

    @Test
    fun property_no_non_mafia_projection_references_any_mafia_role() {
        // Property: for several random seeds, after assignment, for every
        // non-Mafia player p and every Mafia player m: toPlayer(p) contains
        // no copy of m's role, m's coordination snapshot, or m's detective
        // result.
        val ps = players(9)
        val counts = MafiaRoleCounts(mafia = 3, detective = 1, doctor = 1)
        for (seed in 1L..40L) {
            val assignment = RoleAssignment.assign(ps, counts, RandomSource.seeded(seed))
            val mafiaIds = assignment.roles.filterValues { it == Role.Mafia }.keys
            val townIds = assignment.roles.filterValues { it.team == Team.Town }.keys
            val state = buildState(ps, assignment.roles, assignment.knownTeammates, seed = seed)

            for (town in townIds) {
                val view = MafiaProjectionPolicy.toPlayer(state, town).state
                // Public roster does not reveal anyone's role while alive.
                for (slot in view.public.roster) {
                    assertThat(slot.revealedRole).isNull()
                }
                // privatePerPlayer contains only the town viewer's slice.
                assertThat(view.privatePerPlayer.keys).containsOnly(town)
                // The viewer's own slice references no Mafia teammate.
                val slice = view.privatePerPlayer.getValue(town)
                assertThat(slice.mafiaCoordination).isNull()
                for (m in mafiaIds) {
                    assertThat(slice.knownTeammates.contains(m)).isFalse()
                }
                // hostOnly is fully redacted.
                assertThat(view.hostOnly.fullRoleMap).isEmpty()
            }
        }
    }

    @Test
    fun property_to_player_for_mafia_only_contains_own_slice_not_other_mafia() {
        // Mafia know each other (knownTeammates), which is intentional. But
        // the projection still must hide other Mafia's *MafiaPrivate*, e.g.
        // their pendingNightChoice for the current night, suspicion, etc.
        val ps = players(9)
        val counts = MafiaRoleCounts(mafia = 3, detective = 1, doctor = 1)
        for (seed in 1L..20L) {
            val assignment = RoleAssignment.assign(ps, counts, RandomSource.seeded(seed))
            val mafiaIds = assignment.roles.filterValues { it == Role.Mafia }.keys
            val state = buildState(ps, assignment.roles, assignment.knownTeammates, seed = seed)

            for (m in mafiaIds) {
                val view = MafiaProjectionPolicy.toPlayer(state, m).state
                // Only own slice survives — other Mafia's MafiaPrivate is stripped.
                assertThat(view.privatePerPlayer.keys).containsOnly(m)
            }
        }
    }
}
