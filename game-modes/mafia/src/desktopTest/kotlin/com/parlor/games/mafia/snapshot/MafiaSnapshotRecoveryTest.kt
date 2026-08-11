package com.parlor.games.mafia.snapshot

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.parlor.core.ids.CaseId
import com.parlor.core.ids.GameId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.core.result.DataError
import com.parlor.core.result.Result
import com.parlor.core.random.RandomSource
import com.parlor.core.time.FakeClock
import com.parlor.engine.reducer.DefaultReducerContext
import com.parlor.engine.session.SessionConfig
import com.parlor.engine.snapshot.GameSnapshot
import com.parlor.engine.state.Player
import com.parlor.games.mafia.MafiaDefinition
import com.parlor.games.mafia.MafiaIds
import com.parlor.games.mafia.domain.action.MafiaAction
import com.parlor.games.mafia.domain.phase.MafiaPhase
import com.parlor.games.mafia.domain.reducer.MafiaReducer
import com.parlor.games.mafia.domain.settings.MafiaKillTie
import com.parlor.games.mafia.domain.settings.TieBehavior
import com.parlor.games.mafia.domain.state.DetectiveSeesAs
import com.parlor.games.mafia.domain.state.MafiaCoordinationSnapshot
import com.parlor.games.mafia.domain.state.MafiaState
import com.parlor.games.mafia.domain.state.Role
import com.parlor.games.mafia.domain.state.Team
import com.parlor.games.mafia.domain.state.team
import com.parlor.storage.snapshot.InMemorySnapshotStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant as DateTimeInstant
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class MafiaSnapshotRecoveryTest {
    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        encodeDefaults = true
    }
    private val definition = MafiaDefinition(json)
    private val players = (0 until 5).map { seat ->
        Player(PlayerId("p${seat + 1}"), "Player ${seat + 1}", seat)
    }

    @Test
    fun valid_local_snapshot_restores_full_authoritative_state() = runTest {
        val sessionId = SessionId("mafia-resume")
        val state = definition.createInitialState(config(sessionId))
        val store = InMemorySnapshotStore()
        store.save(snapshot(sessionId, state))

        val result = loadMafiaResumedSession(store, definition, sessionId)

        assertThat(result).isInstanceOf(Result.Success::class)
        assertThat((result as Result.Success).data.state).isEqualTo(state)
        assertThat(result.data.sessionId).isEqualTo(sessionId)
    }

    @Test
    fun structurally_inconsistent_roster_is_rejected_after_authentic_decoding() = runTest {
        val sessionId = SessionId("mafia-bad-roster")
        val valid = definition.createInitialState(config(sessionId))
        val corrupted = valid.copy(public = valid.public.copy(roster = valid.public.roster.dropLast(1)))
        val store = InMemorySnapshotStore()
        store.save(snapshot(sessionId, corrupted, encodeThroughValidatedCodec = false))

        val result = loadMafiaResumedSession(store, definition, sessionId)

        assertThat(result).isInstanceOf(Result.Failure::class)
        assertThat((result as Result.Failure).error).isEqualTo(DataError.CorruptedData)
    }

    @Test
    fun snapshot_from_another_game_is_rejected_before_state_installation() = runTest {
        val sessionId = SessionId("mafia-foreign-game")
        val state = definition.createInitialState(config(sessionId))
        val store = InMemorySnapshotStore()
        store.save(snapshot(sessionId, state).copy(gameId = GameId("another-game")))

        val result = loadMafiaResumedSession(store, definition, sessionId)

        assertThat(result).isInstanceOf(Result.Failure::class)
        assertThat((result as Result.Failure).error).isEqualTo(DataError.CorruptedData)
    }

    @Test
    fun unknown_local_play_mode_is_not_silently_changed_to_pass_and_play() = runTest {
        val sessionId = SessionId("mafia-unknown-mode")
        val state = definition.createInitialState(config(sessionId))
        val store = InMemorySnapshotStore()
        store.save(
            snapshot(sessionId, state).copy(
                metadata = mapOf(MAFIA_PLAY_MODE_KEY to "ModifiedMode"),
            ),
        )

        val result = loadMafiaResumedSession(store, definition, sessionId)

        assertThat(result).isInstanceOf(Result.Failure::class)
        assertThat((result as Result.Failure).error).isEqualTo(DataError.CorruptedData)
    }

    @Test
    fun missing_play_mode_is_rejected_because_mafia_has_no_legacy_snapshot_format() = runTest {
        val sessionId = SessionId("mafia-missing-mode")
        val state = definition.createInitialState(config(sessionId))
        val store = InMemorySnapshotStore()
        store.save(snapshot(sessionId, state).copy(metadata = emptyMap()))

        val result = loadMafiaResumedSession(store, definition, sessionId)

        assertThat(result).isInstanceOf(Result.Failure::class)
        assertThat((result as Result.Failure).error).isEqualTo(DataError.CorruptedData)
    }

    @Test
    fun cancellation_during_snapshot_validation_is_not_reported_as_corruption() = runTest {
        val sessionId = SessionId("mafia-cancelled-recovery")
        val state = definition.createInitialState(config(sessionId))
        val store = InMemorySnapshotStore()
        store.save(
            snapshot(sessionId, state).copy(
                metadata = ThrowingMetadata(CancellationException("screen disposed")),
            ),
        )

        assertFailsWith<CancellationException> {
            loadMafiaResumedSession(store, definition, sessionId)
        }
    }

    @Test
    fun fatal_error_during_snapshot_validation_is_not_reported_as_corruption() = runTest {
        val sessionId = SessionId("mafia-fatal-recovery")
        val state = definition.createInitialState(config(sessionId))
        val store = InMemorySnapshotStore()
        store.save(
            snapshot(sessionId, state).copy(
                metadata = ThrowingMetadata(FatalRecoveryError()),
            ),
        )

        assertFailsWith<FatalRecoveryError> {
            loadMafiaResumedSession(store, definition, sessionId)
        }
    }

    @Test
    fun phase_day_and_active_vote_must_form_a_reducer_reachable_state() = runTest {
        val sessionId = SessionId("mafia-impossible-phase")
        val state = nightState(sessionId)
        val phase = state.phase as MafiaPhase.Night
        val corrupted = state.copy(phase = phase.copy(day = phase.day + 1))
        val store = InMemorySnapshotStore()
        store.save(snapshot(sessionId, corrupted, encodeThroughValidatedCodec = false))

        val result = loadMafiaResumedSession(store, definition, sessionId)

        assertThat(result).isInstanceOf(Result.Failure::class)
        assertThat((result as Result.Failure).error).isEqualTo(DataError.CorruptedData)
    }

    @Test
    fun role_map_counts_and_private_team_identity_cannot_be_forged() = runTest {
        val sessionId = SessionId("mafia-forged-role")
        val state = nightState(sessionId)
        val playerId = state.players.first().id
        val original = state.privatePerPlayer.getValue(playerId)
        val corrupted = state.copy(
            privatePerPlayer = state.privatePerPlayer +
                (playerId to original.copy(team = if (original.team == com.parlor.games.mafia.domain.state.Team.Mafia) {
                    com.parlor.games.mafia.domain.state.Team.Town
                } else {
                    com.parlor.games.mafia.domain.state.Team.Mafia
                })),
        )
        val store = InMemorySnapshotStore()
        store.save(snapshot(sessionId, corrupted, encodeThroughValidatedCodec = false))

        val result = loadMafiaResumedSession(store, definition, sessionId)

        assertThat(result).isInstanceOf(Result.Failure::class)
        assertThat((result as Result.Failure).error).isEqualTo(DataError.CorruptedData)
    }

    @Test
    fun private_night_action_and_detective_result_must_be_reducer_reachable() {
        var state = nightState(SessionId("mafia-private-action-shape"))
        val detectiveId = state.privatePerPlayer.entries.single { it.value.role == Role.Detective }.key
        val targetId = state.players.first { it.id != detectiveId }.id
        state = MafiaReducer.reduce(
            state,
            MafiaAction.SubmitDetectiveInspect(detectiveId, targetId),
            reducerContext(),
        ).newState
        assertTrue(state.isValidRecoveryState())

        val detective = state.privatePerPlayer.getValue(detectiveId)
        val result = requireNotNull(detective.pendingDetectiveResult)
        val forgedResult = result.copy(
            seesAs = if (result.seesAs == DetectiveSeesAs.Mafia) {
                DetectiveSeesAs.Town
            } else {
                DetectiveSeesAs.Mafia
            },
        )
        assertFalse(
            state.copy(
                privatePerPlayer = state.privatePerPlayer +
                    (detectiveId to detective.copy(pendingDetectiveResult = forgedResult)),
            ).isValidRecoveryState(),
            "a restored inspection result must match the authoritative role map",
        )

        val nonDetectiveId = state.privatePerPlayer.entries.first {
            it.value.role == Role.Civilian
        }.key
        val nonDetective = state.privatePerPlayer.getValue(nonDetectiveId)
        assertFalse(
            state.copy(
                privatePerPlayer = state.privatePerPlayer +
                    (nonDetectiveId to nonDetective.copy(detectiveResultAcknowledged = true)),
            ).isValidRecoveryState(),
            "a non-Detective cannot carry inspection acknowledgement state",
        )
    }

    @Test
    fun pending_night_target_requires_the_reducer_submission_flag() {
        val state = nightState(SessionId("mafia-pending-without-submission"))
        val mafiaId = state.privatePerPlayer.entries.single { it.value.role == Role.Mafia }.key
        val target = state.hostOnly.fullRoleMap.entries.first { it.value.team == Team.Town }.key
        val mafiaPrivate = state.privatePerPlayer.getValue(mafiaId)
        val impossible = state.copy(
            privatePerPlayer = state.privatePerPlayer +
                (mafiaId to mafiaPrivate.copy(
                    pendingNightChoice = target,
                    nightChoiceSubmitted = false,
                )),
        )

        assertFalse(
            impossible.isValidRecoveryState(),
            "the reducer never stores a non-null target without committing the submission flag",
        )
    }

    @Test
    fun terminal_winner_must_exactly_match_the_revealed_alive_roles() {
        val naturallyCompleted = townWinState(SessionId("mafia-terminal-winner"))
        assertThat(naturallyCompleted.public.winner).isEqualTo(Team.Town)
        assertTrue(naturallyCompleted.isValidRecoveryState())

        assertFalse(
            naturallyCompleted.copy(
                public = naturallyCompleted.public.copy(winner = null),
            ).isValidRecoveryState(),
            "a natural win cannot be restored as an unexplained winner-less terminal state",
        )

        val earlyEnd = MafiaReducer.reduce(
            nightState(SessionId("mafia-early-end")),
            MafiaAction.EndGame,
            reducerContext(),
        ).newState
        assertThat(earlyEnd.public.winner).isEqualTo(null)
        assertTrue(earlyEnd.isValidRecoveryState(), "an explicit early end before parity remains valid")
    }

    @Test
    fun non_terminal_state_cannot_already_satisfy_a_win_condition() {
        val state = nightState(SessionId("mafia-active-after-win"))
        val roles = state.hostOnly.fullRoleMap
        val mafiaId = roles.entries.single { it.value == Role.Mafia }.key
        val survivingTown = roles.entries.first { it.value.team == Team.Town }.key
        val impossible = state.copy(
            public = state.public.copy(
                roster = state.public.roster.map { slot ->
                    val survives = slot.playerId == mafiaId || slot.playerId == survivingTown
                    slot.copy(
                        alive = survives,
                        revealedRole = if (survives) null else roles.getValue(slot.playerId),
                    )
                },
            ),
        )

        assertFalse(
            impossible.isValidRecoveryState(),
            "Mafia parity is terminal and cannot be restored into Night",
        )
    }

    @Test
    fun round_two_mafia_coordination_requires_revote_rules_and_reachable_round_one_history() {
        val valid = mafiaRevoteState(SessionId("mafia-coordination-round-two"))
        assertTrue(valid.isValidRecoveryState())

        val wrongTiePolicy = valid.copy(
            public = valid.public.copy(
                settings = valid.public.settings.copy(mafiaKillTieBehavior = MafiaKillTie.RANDOM_TIED),
            ),
        )
        assertFalse(wrongTiePolicy.isValidRecoveryState(), "RANDOM_TIED cannot open a second round")

        val townTargets = valid.hostOnly.fullRoleMap.entries
            .filter { it.value.team == Team.Town }
            .take(2)
            .map { it.key }
        val impossibleTally = mapOf(townTargets[0] to 2, townTargets[1] to 2)
        assertFalse(
            valid.withCoordination { it.copy(previousRoundTally = impossibleTally) }
                .isValidRecoveryState(),
            "two living Mafia cannot have cast four first-round votes",
        )

        val townId = valid.privatePerPlayer.entries.first { it.value.team == Team.Town }.key
        val townPrivate = valid.privatePerPlayer.getValue(townId)
        assertTrue(townPrivate.nightChoiceSubmitted)
        assertFalse(
            valid.copy(
                privatePerPlayer = valid.privatePerPlayer +
                    (townId to townPrivate.copy(nightChoiceSubmitted = false)),
            ).isValidRecoveryState(),
            "round two opens only after every active Town night action was already submitted",
        )
    }

    @Test
    fun public_announcements_must_match_the_authoritative_resolution_log() {
        val valid = votingState(SessionId("mafia-announcement-log"))
        assertTrue(valid.isValidRecoveryState())
        val announcement = requireNotNull(valid.public.lastNight)

        assertFalse(
            valid.copy(
                public = valid.public.copy(lastNight = announcement.copy(wasSaved = true)),
            ).isValidRecoveryState(),
            "a no-target night cannot be rewritten as a Doctor save",
        )
    }

    @Test
    fun authoritative_resolution_history_must_be_complete_and_reducer_valid() {
        val afterNight = votingState(SessionId("mafia-resolution-history"))
        assertTrue(afterNight.isValidRecoveryState())
        assertFalse(
            afterNight.copy(
                hostOnly = afterNight.hostOnly.copy(nightLog = emptyList()),
            ).isValidRecoveryState(),
            "a public night announcement cannot survive without its authoritative record",
        )

        val nightRecord = afterNight.hostOnly.nightLog.single()
        val target = afterNight.players.first().id
        val actualResult = afterNight.hostOnly.fullRoleMap.getValue(target).let { role ->
            if (role == Role.Mafia) DetectiveSeesAs.Mafia else DetectiveSeesAs.Town
        }
        val forgedResult = if (actualResult == DetectiveSeesAs.Mafia) {
            DetectiveSeesAs.Town
        } else {
            DetectiveSeesAs.Mafia
        }
        assertFalse(
            afterNight.copy(
                hostOnly = afterNight.hostOnly.copy(
                    nightLog = listOf(
                        nightRecord.copy(
                            detectiveInspect = target,
                            detectiveResult = forgedResult,
                        ),
                    ),
                ),
            ).isValidRecoveryState(),
            "the authoritative Detective result must agree with the assigned target role",
        )

        val afterVote = townWinState(SessionId("mafia-vote-history"))
        val voteRecord = afterVote.hostOnly.voteLog.single()
        assertFalse(
            afterVote.copy(
                hostOnly = afterVote.hostOnly.copy(
                    voteLog = listOf(
                        voteRecord.copy(revoteRound = afterVote.public.settings.maxRevotes + 1),
                    ),
                ),
            ).isValidRecoveryState(),
            "an authoritative vote record cannot exceed the configured revote limit",
        )
    }

    @Test
    fun pregame_and_first_night_private_history_must_be_reducer_reachable() {
        val roster = (0 until 6).map { seat ->
            Player(PlayerId("h${seat + 1}"), "History ${seat + 1}", seat)
        }
        val initial = definition.createInitialState(config(SessionId("mafia-private-history"), roster))
        val roleAssignment = MafiaReducer.reduce(
            initial,
            MafiaAction.StartGame,
            reducerContext(),
        ).newState
        val doctorId = roleAssignment.privatePerPlayer.entries.single { it.value.role == Role.Doctor }.key
        val civilianId = roleAssignment.privatePerPlayer.entries.first { it.value.role == Role.Civilian }.key
        val target = roster.first { it.id != doctorId && it.id != civilianId }.id

        assertFalse(
            roleAssignment.copy(
                privatePerPlayer = roleAssignment.privatePerPlayer +
                    (doctorId to roleAssignment.privatePerPlayer.getValue(doctorId).copy(
                        previousDoctorProtect = target,
                    )),
            ).isValidRecoveryState(),
            "RoleAssignment cannot contain protection history",
        )
        assertFalse(
            roleAssignment.copy(
                privatePerPlayer = roleAssignment.privatePerPlayer +
                    (civilianId to roleAssignment.privatePerPlayer.getValue(civilianId).copy(
                        lastSuspicion = target,
                    )),
            ).isValidRecoveryState(),
            "RoleAssignment cannot contain a Civilian action",
        )

        val firstNight = nightState(SessionId("mafia-first-night-history"), roster)
        assertFalse(
            firstNight.copy(
                privatePerPlayer = firstNight.privatePerPlayer +
                    (doctorId to firstNight.privatePerPlayer.getValue(doctorId).copy(
                        previousDoctorProtect = target,
                    )),
            ).isValidRecoveryState(),
            "Night one has no previous Doctor target",
        )
        assertFalse(
            firstNight.copy(
                privatePerPlayer = firstNight.privatePerPlayer +
                    (civilianId to firstNight.privatePerPlayer.getValue(civilianId).copy(
                        lastSuspicion = target,
                        nightChoiceSubmitted = false,
                    )),
            ).isValidRecoveryState(),
            "a first-night suspicion must come from a committed action",
        )
    }

    @Test
    fun terminal_state_erases_transient_private_action_data_before_persistence() {
        val state = nightState(SessionId("mafia-terminal-clean"))
        val actingPlayer = state.players.first()
        val role = state.privatePerPlayer.getValue(actingPlayer.id).role
        val withAction = MafiaReducer.reduce(
            state,
            when (role) {
                com.parlor.games.mafia.domain.state.Role.Mafia ->
                    MafiaAction.SubmitMafiaKillVote(actingPlayer.id, null)
                com.parlor.games.mafia.domain.state.Role.Doctor ->
                    MafiaAction.SubmitDoctorProtect(actingPlayer.id, null)
                com.parlor.games.mafia.domain.state.Role.Detective ->
                    MafiaAction.SubmitDetectiveInspect(actingPlayer.id, null)
                com.parlor.games.mafia.domain.state.Role.Civilian ->
                    MafiaAction.SubmitCivilianSuspicion(actingPlayer.id, null)
            },
            reducerContext(),
        ).newState

        val terminal = MafiaReducer.reduce(withAction, MafiaAction.EndGame, reducerContext()).newState

        assertThat(terminal.phase).isEqualTo(MafiaPhase.PostGame)
        assertThat(terminal.isValidRecoveryState()).isEqualTo(true)
        terminal.privatePerPlayer.values.forEach { private ->
            assertThat(private.mafiaCoordination).isEqualTo(null)
            assertThat(private.pendingNightChoice).isEqualTo(null)
            assertThat(private.pendingDetectiveResult).isEqualTo(null)
            assertThat(private.nightChoiceSubmitted).isEqualTo(false)
        }
    }

    @Test
    fun initial_vote_requires_the_exact_ordered_eligible_ballot_and_candidate_set() {
        val valid = votingState(SessionId("mafia-vote-shape"))
        val vote = requireNotNull(valid.public.activeVote)
        assertTrue(valid.isValidRecoveryState())

        val outsider = PlayerId("not-at-the-table")
        val mutations = listOf(
            "missing eligible voter" to vote.copy(ballot = vote.ballot.dropLast(1)),
            "reordered ballot" to vote.copy(ballot = vote.ballot.reversed()),
            "extra voter" to vote.copy(ballot = vote.ballot + outsider),
            "missing initial candidate" to vote.copy(candidates = vote.candidates.dropLast(1)),
            "reordered initial candidates" to vote.copy(candidates = vote.candidates.reversed()),
            "extra initial candidate" to vote.copy(candidates = vote.candidates + outsider),
        )

        mutations.forEach { (label, mutatedVote) ->
            val mutated = valid.copy(public = valid.public.copy(activeVote = mutatedVote))
            assertFalse(mutated.isValidRecoveryState(), label)
        }

        val deadId = vote.ballot.last()
        val withDeadEligibleVoter = valid.copy(
            public = valid.public.copy(
                roster = valid.public.roster.map { slot ->
                    if (slot.playerId == deadId) {
                        slot.copy(
                            alive = false,
                            revealedRole = valid.hostOnly.fullRoleMap.getValue(deadId),
                        )
                    } else {
                        slot
                    }
                },
            ),
        )
        assertFalse(
            withDeadEligibleVoter.isValidRecoveryState(),
            "a dead player cannot remain in an active ballot",
        )
    }

    @Test
    fun revote_candidates_must_be_reducer_reachable_for_the_configured_tie_policy() {
        val valid = tiedRevoteState(SessionId("mafia-revote-shape"))
        val vote = requireNotNull(valid.public.activeVote)
        assertTrue(vote.revoteRound > 0)
        assertTrue(valid.isValidRecoveryState())

        val reversed = vote.candidates.reversed()
        val mutations = listOf(
            "a tied-only revote needs at least two candidates" to
                valid.copy(public = valid.public.copy(activeVote = vote.copy(candidates = vote.candidates.take(1)))),
            "tied-only candidates use deterministic PlayerId order" to
                valid.copy(public = valid.public.copy(activeVote = vote.copy(candidates = reversed))),
            "skip-on-tie cannot produce a revote" to
                valid.copy(public = valid.public.copy(
                    settings = valid.public.settings.copy(voteTieBehavior = TieBehavior.SKIP_ELIMINATION),
                )),
            "revote-all must retain every eligible candidate" to
                valid.copy(public = valid.public.copy(
                    settings = valid.public.settings.copy(voteTieBehavior = TieBehavior.REVOTE_ALL),
                )),
            "a revote cannot exceed the configured maximum" to
                valid.copy(
                    phase = (valid.phase as MafiaPhase.Voting).copy(revoteRound = 2),
                    public = valid.public.copy(activeVote = vote.copy(revoteRound = 2)),
                ),
        )

        mutations.forEach { (label, mutated) ->
            assertFalse(mutated.isValidRecoveryState(), label)
        }

        val revoteAll = valid.copy(
            public = valid.public.copy(
                settings = valid.public.settings.copy(voteTieBehavior = TieBehavior.REVOTE_ALL),
                activeVote = vote.copy(candidates = vote.ballot),
            ),
        )
        assertTrue(revoteAll.isValidRecoveryState())
    }

    @Test
    fun player_and_public_roster_order_or_name_mutations_are_not_recoverable() {
        val valid = definition.createInitialState(config(SessionId("mafia-roster-shape")))
        val reorderedRoster = valid.copy(public = valid.public.copy(roster = valid.public.roster.reversed()))
        val unnormalizedName = valid.copy(
            players = valid.players.toMutableList().also {
                it[0] = it[0].copy(displayName = " Player 1")
            },
            public = valid.public.copy(
                roster = valid.public.roster.toMutableList().also {
                    it[0] = it[0].copy(displayName = " Player 1")
                },
            ),
        )
        val duplicatePlayers = valid.players.toMutableList().also {
            it[1] = it[1].copy(displayName = it[0].displayName)
        }
        val duplicateNames = valid.copy(
            players = duplicatePlayers,
            public = valid.public.copy(
                roster = valid.public.roster.toMutableList().also {
                    it[1] = it[1].copy(displayName = it[0].displayName)
                },
            ),
        )

        assertFalse(reorderedRoster.isValidRecoveryState())
        assertFalse(unnormalizedName.isValidRecoveryState())
        assertFalse(duplicateNames.isValidRecoveryState())
    }

    @Test
    fun unassigned_post_game_accepts_only_the_exact_setup_cancellation_shape() {
        val initial = definition.createInitialState(config(SessionId("mafia-unassigned-terminal")))
        val ended = MafiaReducer.reduce(
            initial,
            MafiaAction.EndGame,
            reducerContext(),
        ).newState

        assertTrue(ended.isValidRecoveryState(), "the reducer-produced setup cancellation must restore")

        val mutations = listOf(
            "terminal setup cancellation cannot claim a played day" to ended.copy(
                public = ended.public.copy(day = 1),
            ),
            "terminal setup cancellation cannot contain a dead seat" to ended.copy(
                public = ended.public.copy(
                    roster = ended.public.roster.mapIndexed { index, slot ->
                        if (index == 0) slot.copy(alive = false, revealedRole = Role.Civilian) else slot
                    },
                ),
            ),
            "terminal setup cancellation cannot expose an unassigned role" to ended.copy(
                public = ended.public.copy(
                    roster = ended.public.roster.mapIndexed { index, slot ->
                        if (index == 0) slot.copy(revealedRole = Role.Mafia) else slot
                    },
                ),
            ),
        )

        mutations.forEach { (label, mutated) ->
            assertFalse(mutated.isValidRecoveryState(), label)
        }
    }

    @Test
    fun eliminated_spectator_cannot_be_restored_as_gameplay_disconnected() {
        var valid = votingState(SessionId("mafia-dead-disconnected"))
        val target = valid.hostOnly.fullRoleMap.entries.first { it.value.team == Team.Town }.key
        requireNotNull(valid.public.activeVote).ballot.forEach { voter ->
            valid = MafiaReducer.reduce(
                valid,
                if (voter == target) {
                    MafiaAction.AbstainVote(voter)
                } else {
                    MafiaAction.CastVote(voter, target)
                },
                reducerContext(),
            ).newState
        }
        valid = MafiaReducer.reduce(valid, MafiaAction.CloseVote, reducerContext()).newState
        assertTrue(valid.public.roster.single { it.playerId == target }.alive.not())
        assertTrue(valid.isValidRecoveryState())

        val impossible = valid.copy(
            public = valid.public.copy(disconnectedPlayers = setOf(target)),
        )
        assertFalse(
            impossible.isValidRecoveryState(),
            "an eliminated spectator can reconnect but cannot freeze a living game",
        )
    }

    private fun config(
        sessionId: SessionId,
        roster: List<Player> = players,
    ) = SessionConfig(
        sessionId = sessionId,
        caseId = CaseId("default"),
        modeId = MafiaIds.ClassicModeId,
        players = roster,
        randomSeed = 42L,
    )

    private fun reducerContext() = DefaultReducerContext(
        clock = FakeClock(DateTimeInstant.fromEpochSeconds(1_700_000_000)),
        random = RandomSource.seeded(42L),
    )

    private fun nightState(
        sessionId: SessionId,
        roster: List<Player> = players,
    ): com.parlor.games.mafia.domain.state.MafiaState {
        var state = definition.createInitialState(config(sessionId, roster))
        state = MafiaReducer.reduce(state, MafiaAction.StartGame, reducerContext()).newState
        roster.forEach { player ->
            state = MafiaReducer.reduce(
                state,
                MafiaAction.AcknowledgeRoleViewed(player.id),
                reducerContext(),
            ).newState
        }
        return MafiaReducer.reduce(
            state,
            MafiaAction.AdvanceFromRoleAssignment,
            reducerContext(),
        ).newState
    }

    private fun mafiaRevoteState(sessionId: SessionId): MafiaState {
        val roster = (0 until 7).map { seat ->
            Player(PlayerId("r${seat + 1}"), "Revote ${seat + 1}", seat)
        }
        var state = nightState(sessionId, roster)
        val mafiaIds = state.hostOnly.fullRoleMap.entries
            .filter { it.value == Role.Mafia }
            .map { it.key }
        val townTargets = state.hostOnly.fullRoleMap.entries
            .filter { it.value.team == Team.Town }
            .take(2)
            .map { it.key }
        mafiaIds.forEachIndexed { index, mafiaId ->
            state = MafiaReducer.reduce(
                state,
                MafiaAction.SubmitMafiaKillVote(mafiaId, townTargets[index]),
                reducerContext(),
            ).newState
        }
        state.players.forEach { player ->
            val private = state.privatePerPlayer.getValue(player.id)
            if (private.nightChoiceSubmitted) return@forEach
            val action = when (private.role) {
                Role.Mafia -> MafiaAction.SubmitMafiaKillVote(player.id, null)
                Role.Doctor -> MafiaAction.SubmitDoctorProtect(player.id, null)
                Role.Detective -> MafiaAction.SubmitDetectiveInspect(player.id, null)
                Role.Civilian -> MafiaAction.SubmitCivilianSuspicion(player.id, null)
            }
            state = MafiaReducer.reduce(state, action, reducerContext()).newState
        }
        return MafiaReducer.reduce(state, MafiaAction.ResolveNight, reducerContext()).newState.also {
            check(it.phase == MafiaPhase.Night(day = 1, mafiaCoordinationRound = 2))
        }
    }

    private fun townWinState(sessionId: SessionId): MafiaState {
        var state = votingState(sessionId)
        val mafiaId = state.hostOnly.fullRoleMap.entries.single { it.value == Role.Mafia }.key
        requireNotNull(state.public.activeVote).ballot.forEach { voter ->
            state = MafiaReducer.reduce(
                state,
                if (voter == mafiaId) {
                    MafiaAction.AbstainVote(voter)
                } else {
                    MafiaAction.CastVote(voter, mafiaId)
                },
                reducerContext(),
            ).newState
        }
        return MafiaReducer.reduce(state, MafiaAction.CloseVote, reducerContext()).newState.also {
            check(it.phase == MafiaPhase.PostGame)
        }
    }

    private fun MafiaState.withCoordination(
        transform: (MafiaCoordinationSnapshot) -> MafiaCoordinationSnapshot,
    ): MafiaState = copy(
        privatePerPlayer = privatePerPlayer.mapValues { (_, private) ->
            private.copy(mafiaCoordination = private.mafiaCoordination?.let(transform))
        },
    )

    private fun votingState(sessionId: SessionId): com.parlor.games.mafia.domain.state.MafiaState {
        var state = nightState(sessionId)
        state.players.forEach { player ->
            val action = when (state.privatePerPlayer.getValue(player.id).role) {
                Role.Mafia -> MafiaAction.SubmitMafiaKillVote(player.id, null)
                Role.Doctor -> MafiaAction.SubmitDoctorProtect(player.id, null)
                Role.Detective -> MafiaAction.SubmitDetectiveInspect(player.id, null)
                Role.Civilian -> MafiaAction.SubmitCivilianSuspicion(player.id, null)
            }
            state = MafiaReducer.reduce(state, action, reducerContext()).newState
        }
        state = MafiaReducer.reduce(state, MafiaAction.ResolveNight, reducerContext()).newState
        state.players.forEach { player ->
            state = MafiaReducer.reduce(
                state,
                MafiaAction.AcknowledgeNightAnnouncement(player.id),
                reducerContext(),
            ).newState
        }
        state = MafiaReducer.reduce(state, MafiaAction.OpenDiscussion, reducerContext()).newState
        return MafiaReducer.reduce(state, MafiaAction.OpenVote, reducerContext()).newState
    }

    private fun tiedRevoteState(sessionId: SessionId): com.parlor.games.mafia.domain.state.MafiaState {
        var state = votingState(sessionId)
        val ballot = requireNotNull(state.public.activeVote).ballot
        state = MafiaReducer.reduce(
            state,
            MafiaAction.CastVote(ballot[0], ballot[1]),
            reducerContext(),
        ).newState
        state = MafiaReducer.reduce(
            state,
            MafiaAction.CastVote(ballot[1], ballot[0]),
            reducerContext(),
        ).newState
        ballot.drop(2).forEach { voter ->
            state = MafiaReducer.reduce(
                state,
                MafiaAction.AbstainVote(voter),
                reducerContext(),
            ).newState
        }
        return MafiaReducer.reduce(state, MafiaAction.CloseVote, reducerContext()).newState
    }

    private fun snapshot(
        sessionId: SessionId,
        state: MafiaState,
        encodeThroughValidatedCodec: Boolean = true,
    ) = GameSnapshot(
        sessionId = sessionId,
        gameId = MafiaIds.GameId,
        engineVersion = MAFIA_SNAPSHOT_VERSION,
        createdAt = Instant.fromEpochSeconds(1),
        phaseId = state.phase.id,
        // Recovery tests for malformed records must bypass the production
        // encoder: the encoder is intentionally fail-closed and cannot emit
        // an impossible state. The strict decoder still exercises the real
        // persistence boundary and must reject this raw serialized fixture.
        payload = if (encodeThroughValidatedCodec) {
            definition.snapshotCodec().encode(state)
        } else {
            json.encodeToString(MafiaState.serializer(), state).encodeToByteArray()
        },
        metadata = mapOf(MAFIA_PLAY_MODE_KEY to MAFIA_PASS_AND_PLAY_MODE),
    )

    private class ThrowingMetadata(
        private val failure: Throwable,
    ) : Map<String, String> by emptyMap() {
        override fun get(key: String): String? = throw failure
    }

    private class FatalRecoveryError : Error("fatal recovery failure")
}
