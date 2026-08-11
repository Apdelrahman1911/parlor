package com.parlor.games.mafia.domain.state

import com.parlor.core.ids.PlayerId
import com.parlor.engine.state.Player
import com.parlor.games.mafia.domain.phase.MafiaPhase
import com.parlor.games.mafia.domain.settings.MafiaKillTie
import com.parlor.games.mafia.domain.settings.MafiaSettingsPresets
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MafiaPeerSnapshotValidatorTest {
    private val players = (0 until 7).map { seat ->
        Player(PlayerId("p${seat + 1}"), "Player ${seat + 1}", seat)
    }

    @Test
    fun round_two_projection_requires_revote_policy_and_a_bounded_first_round_tally() {
        val publicState = roundTwoPublicState()
        val self = players[1].id
        val teammate = players[5].id
        val validPrivate = MafiaPrivate(
            role = Role.Mafia,
            team = Team.Mafia,
            knownTeammates = setOf(teammate),
            mafiaCoordination = MafiaCoordinationSnapshot(
                round = 2,
                previousRoundTally = mapOf(players[0].id to 1, players[2].id to 1),
            ),
        )
        assertTrue(MafiaPeerSnapshotValidator.isValid(publicState, validPrivate, self))

        assertFalse(
            MafiaPeerSnapshotValidator.isValid(
                publicState.copy(
                    public = publicState.public.copy(
                        settings = publicState.public.settings.copy(
                            mafiaKillTieBehavior = MafiaKillTie.RANDOM_TIED,
                        ),
                    ),
                ),
                validPrivate,
                self,
            ),
            "RANDOM_TIED resolves round one and cannot expose round two",
        )

        assertFalse(
            MafiaPeerSnapshotValidator.isValid(
                publicState,
                validPrivate.copy(
                    mafiaCoordination = requireNotNull(validPrivate.mafiaCoordination).copy(
                        previousRoundTally = mapOf(players[0].id to 2, players[2].id to 2),
                    ),
                ),
                self,
            ),
            "two living Mafia cannot produce four first-round votes",
        )
    }

    @Test
    fun town_projection_in_round_two_must_retain_its_completed_round_one_action() {
        val publicState = roundTwoPublicState()
        val self = players.first().id
        val impossiblePrivate = MafiaPrivate(role = Role.Civilian, team = Team.Town)

        assertFalse(
            MafiaPeerSnapshotValidator.isValid(publicState, impossiblePrivate, self),
            "the reducer clears only Mafia submissions when opening their revote",
        )
    }

    @Test
    fun terminal_projection_winner_exactly_matches_the_public_role_reveal() {
        val terminalPlayers = players.take(5)
        val mafia = terminalPlayers.first().id
        val survivingTown = terminalPlayers[1].id
        val roles = terminalPlayers.mapIndexed { index, player ->
            player.id to when (index) {
                0 -> Role.Mafia
                1 -> Role.Detective
                else -> Role.Civilian
            }
        }.toMap()
        val base = MafiaState(
            public = MafiaPublic(
                settings = MafiaSettingsPresets.forPlayerCount(terminalPlayers.size),
                day = 1,
                roster = terminalPlayers.map { player ->
                    val alive = player.id == mafia || player.id == survivingTown
                    PublicPlayerSlot(
                        playerId = player.id,
                        displayName = player.displayName,
                        seat = player.seat,
                        alive = alive,
                        revealedRole = roles.getValue(player.id),
                    )
                },
                winner = Team.Mafia,
            ),
            privatePerPlayer = emptyMap(),
            hostOnly = MafiaHostOnly(fullRoleMap = emptyMap(), randomSeed = 0L),
            phase = MafiaPhase.PostGame,
            players = terminalPlayers,
        )
        val own = MafiaPrivate(role = Role.Mafia, team = Team.Mafia)
        assertTrue(MafiaPeerSnapshotValidator.isValid(base, own, mafia))

        assertFalse(
            MafiaPeerSnapshotValidator.isValid(
                base.copy(public = base.public.copy(winner = null)),
                own,
                mafia,
            ),
            "a public Mafia-parity result cannot omit its winner",
        )

        val earlyEnd = base.copy(
            public = base.public.copy(
                roster = base.public.roster.mapIndexed { index, slot ->
                    slot.copy(alive = index < 4)
                },
                winner = null,
            ),
        )
        assertTrue(
            MafiaPeerSnapshotValidator.isValid(earlyEnd, own, mafia),
            "an explicit early end before a natural winner remains representable",
        )

        assertFalse(
            MafiaPeerSnapshotValidator.isValid(
                base.copy(
                    public = base.public.copy(
                        lastNight = NightAnnouncement(
                            day = 1,
                            killedPlayerId = null,
                            wasSaved = false,
                        ),
                        lastVote = VoteAnnouncement(
                            day = 1,
                            tally = mapOf(
                                terminalPlayers[1].id to Int.MAX_VALUE,
                                terminalPlayers[2].id to Int.MAX_VALUE,
                            ),
                            eliminatedPlayerId = null,
                            outcome = VoteOutcome.MaxRevotesReached,
                        ),
                    ),
                ),
                own,
                mafia,
            ),
            "vote-count bounds must not be bypassable through Int overflow",
        )

        assertFalse(
            MafiaPeerSnapshotValidator.isValid(
                base.copy(public = base.public.copy(day = 5, lastNight = null, lastVote = null)),
                own,
                mafia,
            ),
            "terminal day five cannot exist without any prior resolution",
        )
    }

    @Test
    fun role_assignment_and_first_night_reject_private_history_from_a_future_transition() {
        val assignment = roundTwoPublicState().copy(
            phase = MafiaPhase.RoleAssignment,
            public = roundTwoPublicState().public.copy(day = 0),
        )
        val self = players.first().id
        val target = players[1].id
        val doctor = MafiaPrivate(role = Role.Doctor, team = Team.Town)
        assertTrue(MafiaPeerSnapshotValidator.isValid(assignment, doctor, self))
        assertFalse(
            MafiaPeerSnapshotValidator.isValid(
                assignment,
                doctor.copy(previousDoctorProtect = target),
                self,
            ),
        )

        val firstNight = roundTwoPublicState().copy(phase = MafiaPhase.Night(day = 1))
        val civilian = MafiaPrivate(
            role = Role.Civilian,
            team = Team.Town,
            lastSuspicion = target,
            nightChoiceSubmitted = false,
        )
        assertFalse(MafiaPeerSnapshotValidator.isValid(firstNight, civilian, self))
        assertTrue(
            MafiaPeerSnapshotValidator.isValid(
                firstNight,
                civilian.copy(nightChoiceSubmitted = true),
                self,
            ),
        )
    }

    private fun roundTwoPublicState(): MafiaState = MafiaState(
        public = MafiaPublic(
            settings = MafiaSettingsPresets.forPlayerCount(players.size),
            day = 1,
            roster = players.map { PublicPlayerSlot(it.id, it.displayName, it.seat) },
        ),
        privatePerPlayer = emptyMap(),
        hostOnly = MafiaHostOnly(fullRoleMap = emptyMap(), randomSeed = 0L),
        phase = MafiaPhase.Night(day = 1, mafiaCoordinationRound = 2),
        players = players,
    )
}
