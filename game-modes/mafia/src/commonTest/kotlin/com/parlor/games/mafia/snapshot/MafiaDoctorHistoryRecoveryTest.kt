package com.parlor.games.mafia.snapshot

import com.parlor.core.ids.PlayerId
import com.parlor.core.result.DataError
import com.parlor.core.result.Result
import com.parlor.engine.snapshot.GameSnapshot
import com.parlor.games.mafia.MafiaIds
import com.parlor.games.mafia.domain.action.MafiaAction
import com.parlor.games.mafia.domain.phase.MafiaPhase
import com.parlor.games.mafia.domain.projection.MafiaProjectionPolicy
import com.parlor.games.mafia.domain.settings.MafiaRoleCounts
import com.parlor.games.mafia.domain.settings.MafiaSettings
import com.parlor.games.mafia.domain.state.MafiaHostOnly
import com.parlor.games.mafia.domain.state.MafiaPeerSnapshotValidator
import com.parlor.games.mafia.domain.state.MafiaState
import com.parlor.games.mafia.domain.state.Role
import com.parlor.games.mafia.domain.state.Team
import com.parlor.games.mafia.testing.MafiaDoctorFixture
import com.parlor.session.SubmissionReceipt
import com.parlor.session.passandplay.PassAndPlaySessionController
import com.parlor.storage.snapshot.InMemorySnapshotStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class MafiaDoctorHistoryRecoveryTest {
    private val base = MafiaSettings(MafiaRoleCounts(mafia = 1, detective = 1, doctor = 1))

    @Test
    fun private_previous_protection_must_match_retained_history_in_every_active_phase(): Unit = runTest {
        for (enabled in listOf(false, true)) {
            val game = MafiaDoctorFixture(base.copy(doctorCanProtectSamePlayerConsecutively = enabled))
            game.firstNight()
            val (target, wrongTarget) = game.civilianTargets
            val states = buildList {
                add(game.resolveNight(target))
                add(game.openDiscussion())
                add(game.apply(MafiaAction.OpenVote))
                add(game.finishVoting())
                add(game.nextNight())
                add(game.apply(MafiaAction.SubmitDoctorProtect(game.doctor, if (enabled) target else wrongTarget)))
            }
            for (valid in states) {
                assertRoundTrip(game, valid)
                for (forged in listOf<PlayerId?>(null, wrongTarget)) {
                    assertRejected(game, valid.withPreviousProtection(game.doctor, forged))
                }
            }
        }
    }

    @Test
    fun history_binding_also_applies_during_a_day_revote(): Unit = runTest {
        val game = MafiaDoctorFixture()
        game.firstNight()
        val target = game.civilianTargets.first()
        game.resolveNight(target)
        game.openVoting()
        val ballot = assertNotNull(game.state.public.activeVote).ballot
        game.apply(MafiaAction.CastVote(ballot[0], ballot[1]))
        game.apply(MafiaAction.CastVote(ballot[1], ballot[0]))
        ballot.drop(2).forEach { game.apply(MafiaAction.AbstainVote(it)) }
        game.apply(MafiaAction.CloseVote)
        assertEquals(MafiaPhase.Voting(1, revoteRound = 1), game.state.phase)
        assertRoundTrip(game, game.state)
        assertRejected(game, game.state.withPreviousProtection(game.doctor, null))
    }

    @Test
    fun mafia_coordination_revote_preserves_doctor_history_and_own_submission(): Unit = runTest {
        for (enabled in listOf(false, true)) {
            val game = MafiaDoctorFixture(
                base.copy(
                    roleCounts = base.roleCounts.copy(mafia = 2),
                    doctorCanProtectSamePlayerConsecutively = enabled,
                ),
                playerCount = 7,
            )
            game.firstNight()
            val (first, second) = game.civilianTargets
            game.resolveNight(first)
            game.nextNight()
            val mafia = game.state.hostOnly.fullRoleMap.filterValues { it == Role.Mafia }.keys.toList()
            game.apply(MafiaAction.SubmitMafiaKillVote(mafia[0], first))
            game.apply(MafiaAction.SubmitMafiaKillVote(mafia[1], second))
            game.state.privatePerPlayer.forEach { (id, private) ->
                when (private.role) {
                    Role.Doctor -> game.apply(MafiaAction.SubmitDoctorProtect(id, second))
                    Role.Detective -> game.apply(MafiaAction.SubmitDetectiveInspect(id, null))
                    Role.Civilian -> game.apply(MafiaAction.SubmitCivilianSuspicion(id, null))
                    Role.Mafia -> Unit
                }
            }
            game.apply(MafiaAction.ResolveNight)
            assertEquals(MafiaPhase.Night(2, mafiaCoordinationRound = 2), game.state.phase)
            assertEquals(second, game.state.privatePerPlayer.getValue(game.doctor).pendingNightChoice)
            assertRoundTrip(game, game.state)
            assertRejected(game, game.state.withPreviousProtection(game.doctor, null))
        }
    }

    @Test
    fun null_effective_protection_cannot_be_replaced_by_an_old_legal_target(): Unit = runTest {
        for (enabled in listOf(false, true)) {
            val game = MafiaDoctorFixture(base.copy(doctorCanProtectSamePlayerConsecutively = enabled))
            game.firstNight()
            val target = game.civilianTargets.first()
            game.resolveNight(target)
            game.nextNight()
            game.resolveNight(null)
            game.nextNight()
            assertEquals(null, game.state.hostOnly.nightLog.last().doctorProtect)
            assertRoundTrip(game, game.state)
            assertRejected(game, game.state.withPreviousProtection(game.doctor, target))
        }
    }

    @Test
    fun initial_setup_role_assignment_and_first_night_have_no_previous_protection(): Unit = runTest {
        for (enabled in listOf(false, true)) {
            val game = MafiaDoctorFixture(base.copy(doctorCanProtectSamePlayerConsecutively = enabled))
            assertRoundTrip(game, game.state)
            game.start()
            assertRoundTrip(game, game.state)
            assertRejected(game, game.state.withPreviousProtection(game.doctor, game.civilianTargets.first()))
            game.config.players.forEach { game.apply(MafiaAction.AcknowledgeRoleViewed(it.id)) }
            game.apply(MafiaAction.AdvanceFromRoleAssignment)
            assertRoundTrip(game, game.state)
            assertRejected(game, game.state.withPreviousProtection(game.doctor, game.civilianTargets.first()))
        }
    }

    @Test
    fun repeated_protection_history_is_valid_only_with_setting_enabled(): Unit = runTest {
        val game = MafiaDoctorFixture(base.copy(doctorCanProtectSamePlayerConsecutively = true))
        game.firstNight()
        val target = game.civilianTargets.first()
        repeat(3) {
            game.resolveNight(target)
            game.nextNight()
        }
        assertEquals(listOf(target, target, target), game.state.hostOnly.nightLog.map { it.doctorProtect })
        assertRoundTrip(game, game.state)
        val forgedSettings = game.state.copy(public = game.state.public.copy(settings = base))
        assertRejected(game, forgedSettings)
        assertRejected(game, game.state.withPreviousProtection(game.doctor, null))
    }

    @Test
    fun latest_binding_survives_history_capping_without_requiring_missing_predecessors(): Unit = runTest {
        for (enabled in listOf(false, true)) {
            val game = MafiaDoctorFixture(base.copy(doctorCanProtectSamePlayerConsecutively = enabled))
            game.firstNight()
            val targets = game.civilianTargets.take(2)
            repeat(MafiaHostOnly.MAX_SERIALIZED_LOG_ENTRIES + 2) { index ->
                game.resolveNight(targets[if (enabled) 0 else index % 2])
                game.nextNight()
            }
            assertEquals(MafiaHostOnly.MAX_SERIALIZED_LOG_ENTRIES, game.state.hostOnly.nightLog.size)
            assertEquals(MafiaHostOnly.MAX_SERIALIZED_LOG_ENTRIES, game.state.hostOnly.voteLog.size)
            assertEquals(3, game.state.hostOnly.nightLog.first().day)
            assertRoundTrip(game, game.state)
            assertRejected(game, game.state.withPreviousProtection(game.doctor, null))
        }
    }

    @Test
    fun killed_doctor_keeps_last_effective_target_until_next_resolution_then_clears_it(): Unit = runTest {
        for (enabled in listOf(false, true)) {
            val game = MafiaDoctorFixture(base.copy(doctorCanProtectSamePlayerConsecutively = enabled))
            game.firstNight()
            val doctor = game.doctor
            val target = game.civilianTargets.first()
            game.resolveNight(target, kill = doctor)
            assertFalse(game.state.public.roster.single { it.playerId == doctor }.alive)
            assertEquals(target, game.state.privatePerPlayer.getValue(doctor).previousDoctorProtect)
            assertRoundTrip(game, game.state)
            assertRejected(game, game.state.withPreviousProtection(doctor, null))
            game.nextNight()
            val before = game.state
            assertEquals(before, game.apply(MafiaAction.SubmitDoctorProtect(doctor, target)))
            assertRoundTrip(game, game.state)
            game.resolveNight(null)
            assertEquals(null, game.state.privatePerPlayer.getValue(doctor).previousDoctorProtect)
            assertEquals(null, game.state.hostOnly.nightLog.last().doctorProtect)
            assertRoundTrip(game, game.state)
            assertRejected(game, game.state.withPreviousProtection(doctor, target))
        }
    }

    @Test
    fun doctor_eliminated_by_day_vote_retains_previous_night_binding(): Unit = runTest {
        val game = MafiaDoctorFixture()
        game.firstNight()
        val target = game.civilianTargets.first()
        game.resolveNight(target)
        game.openVoting()
        game.finishVoting(eliminate = game.doctor)
        assertEquals(MafiaPhase.VoteAnnouncement(1), game.state.phase)
        assertRoundTrip(game, game.state)
        assertRejected(game, game.state.withPreviousProtection(game.doctor, null))
        game.nextNight()
        assertRoundTrip(game, game.state)
    }

    @Test
    fun death_of_previous_target_does_not_erase_last_effective_protection(): Unit = runTest {
        val game = MafiaDoctorFixture()
        game.firstNight()
        val target = game.civilianTargets.first()
        game.resolveNight(target)
        game.openVoting()
        game.finishVoting(eliminate = target)
        game.nextNight()
        assertFalse(game.state.public.roster.single { it.playerId == target }.alive)
        assertEquals(target, game.state.privatePerPlayer.getValue(game.doctor).previousDoctorProtect)
        assertRoundTrip(game, game.state)
        assertRejected(game, game.state.withPreviousProtection(game.doctor, null))
    }

    @Test
    fun no_doctor_configuration_keeps_null_history_and_rejects_foreign_private_history(): Unit = runTest {
        for (enabled in listOf(false, true)) {
            val settings = base.copy(
                roleCounts = base.roleCounts.copy(doctor = 0),
                doctorCanProtectSamePlayerConsecutively = enabled,
            )
            val game = MafiaDoctorFixture(settings)
            game.firstNight()
            game.resolveNight(null)
            game.nextNight()
            assertEquals(null, game.state.hostOnly.nightLog.last().doctorProtect)
            assertRoundTrip(game, game.state)
            val civilian = game.civilianTargets.first()
            assertRejected(game, game.state.withPreviousProtection(civilian, game.civilianTargets[1]))
        }
    }

    @Test
    fun terminal_cleanup_does_not_require_historical_protection_to_be_erased(): Unit = runTest {
        for (enabled in listOf(false, true)) {
            val game = MafiaDoctorFixture(base.copy(doctorCanProtectSamePlayerConsecutively = enabled))
            game.firstNight()
            val target = game.civilianTargets.first()
            game.resolveNight(target)
            game.apply(MafiaAction.EndGame)
            assertEquals(MafiaPhase.PostGame, game.state.phase)
            assertEquals(null, game.state.public.winner)
            assertEquals(null, game.state.privatePerPlayer.getValue(game.doctor).previousDoctorProtect)
            assertEquals(target, game.state.hostOnly.nightLog.last().doctorProtect)
            assertRoundTrip(game, game.state)
            assertRejected(game, game.state.withPreviousProtection(game.doctor, target))
        }
    }

    @Test
    fun normal_terminal_win_also_clears_private_history_without_discarding_host_history(): Unit = runTest {
        for (enabled in listOf(false, true)) {
            val game = MafiaDoctorFixture(base.copy(doctorCanProtectSamePlayerConsecutively = enabled))
            game.firstNight()
            val target = game.civilianTargets.first()
            game.resolveNight(target)
            game.openVoting()
            val mafia = game.state.hostOnly.fullRoleMap.entries.single { it.value == Role.Mafia }.key
            game.finishVoting(eliminate = mafia)
            assertEquals(MafiaPhase.PostGame, game.state.phase)
            assertEquals(Team.Town, game.state.public.winner)
            assertEquals(null, game.state.privatePerPlayer.getValue(game.doctor).previousDoctorProtect)
            assertEquals(target, game.state.hostOnly.nightLog.last().doctorProtect)
            assertRoundTrip(game, game.state)
        }
    }

    @Test
    fun peer_projections_keep_only_own_private_history_and_do_not_require_host_logs(): Unit {
        for (enabled in listOf(false, true)) {
            val game = MafiaDoctorFixture(base.copy(doctorCanProtectSamePlayerConsecutively = enabled))
            game.firstNight()
            val target = game.civilianTargets.first()
            game.resolveNight(target)
            game.nextNight()
            val public = MafiaProjectionPolicy.toPublic(game.state).state
            assertTrue(public.privatePerPlayer.isEmpty())
            assertTrue(public.hostOnly.nightLog.isEmpty())
            assertTrue(public.hostOnly.fullRoleMap.isEmpty())
            assertEquals(0L, public.hostOnly.randomSeed)
            for (player in game.config.players) {
                val own = MafiaProjectionPolicy.toPlayer(game.state, player.id).state
                assertEquals(setOf(player.id), own.privatePerPlayer.keys)
                assertTrue(own.hostOnly.nightLog.isEmpty())
                assertTrue(own.hostOnly.voteLog.isEmpty())
                assertTrue(own.hostOnly.fullRoleMap.isEmpty())
                assertEquals(0L, own.hostOnly.randomSeed)
                assertEquals(
                    if (player.id == game.doctor) target else null,
                    own.privatePerPlayer.getValue(player.id).previousDoctorProtect,
                )
                assertTrue(MafiaPeerSnapshotValidator.isValid(public, own.privatePerPlayer[player.id], player.id))
            }
        }
    }

    @Test
    fun resumed_local_controller_enforces_the_persisted_repeat_setting(): Unit = runTest {
        for (enabled in listOf(false, true)) {
            val game = MafiaDoctorFixture(base.copy(doctorCanProtectSamePlayerConsecutively = enabled))
            game.firstNight()
            val target = game.civilianTargets.first()
            game.resolveNight(target)
            game.nextNight()
            val store = InMemorySnapshotStore()
            store.save(snapshot(game, game.state, game.definition.snapshotCodec().encode(game.state)))
            val loaded = assertIs<Result.Success<ResumedMafiaSession>>(
                loadMafiaResumedSession(store, game.definition, game.config.sessionId),
            )
            val session = PassAndPlaySessionController(
                definition = game.definition,
                config = game.config,
                reducerContext = game.context,
                scope = backgroundScope,
                restoredState = loaded.data.state,
            )
            try {
                val submitted = assertIs<Result.Success<SubmissionReceipt>>(
                    session.submit(MafiaAction.SubmitDoctorProtect(game.doctor, target)),
                )
                assertEquals(enabled, submitted.data.stateChanged)
                assertEquals(enabled, session.currentState().privatePerPlayer.getValue(game.doctor).nightChoiceSubmitted)
                assertEquals(game.settings, session.currentState().public.settings)
            } finally {
                session.close()
            }
        }
    }

    private suspend fun assertRoundTrip(game: MafiaDoctorFixture, state: MafiaState) {
        assertTrue(state.isValidRecoveryState(), "Invalid legal phase ${state.phase}")
        val codec = game.definition.snapshotCodec()
        val encoded = codec.encode(state)
        assertEquals(state, codec.decode(encoded))
        val store = InMemorySnapshotStore()
        assertIs<Result.Success<Unit>>(store.save(snapshot(game, state, encoded)))
        val loaded = assertIs<Result.Success<ResumedMafiaSession>>(
            loadMafiaResumedSession(store, game.definition, game.config.sessionId),
        )
        assertEquals(state, loaded.data.state)
    }

    private suspend fun assertRejected(game: MafiaDoctorFixture, forged: MafiaState) {
        assertFalse(forged.isValidRecoveryState(), "Accepted impossible phase ${forged.phase}")
        assertFailsWith<IllegalArgumentException> { game.definition.snapshotCodec().encode(forged) }
        val raw = game.json.encodeToString(MafiaState.serializer(), forged).encodeToByteArray()
        assertFailsWith<IllegalArgumentException> { game.definition.snapshotCodec().decode(raw) }
        val store = InMemorySnapshotStore()
        assertIs<Result.Success<Unit>>(store.save(snapshot(game, forged, raw)))
        assertEquals(
            Result.Failure(DataError.CorruptedData),
            loadMafiaResumedSession(store, game.definition, game.config.sessionId),
        )
    }

    private fun snapshot(game: MafiaDoctorFixture, state: MafiaState, payload: ByteArray) = GameSnapshot(
        sessionId = game.config.sessionId,
        gameId = MafiaIds.GameId,
        engineVersion = MAFIA_SNAPSHOT_VERSION,
        createdAt = Instant.fromEpochSeconds(1),
        phaseId = state.phase.id,
        payload = payload,
        metadata = mapOf(MAFIA_PLAY_MODE_KEY to MAFIA_PASS_AND_PLAY_MODE),
    )

    private fun MafiaState.withPreviousProtection(doctor: PlayerId, target: PlayerId?): MafiaState = copy(
        privatePerPlayer = privatePerPlayer + (
            doctor to privatePerPlayer.getValue(doctor).copy(previousDoctorProtect = target)
        ),
    )
}
