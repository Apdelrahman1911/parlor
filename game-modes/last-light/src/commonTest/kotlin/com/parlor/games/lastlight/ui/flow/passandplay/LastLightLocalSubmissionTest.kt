package com.parlor.games.lastlight.ui.flow.passandplay

import com.parlor.core.ids.PlayerId
import com.parlor.core.result.Result
import com.parlor.games.lastlight.domain.model.GamePhase
import com.parlor.games.lastlight.ui.LastLightProcessVisibility
import com.parlor.games.lastlight.ui.PendingAction
import com.parlor.storage.snapshot.SnapshotWriteStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LastLightLocalSubmissionTest {
    @Test
    fun rapid_play_and_challenge_taps_reserve_only_one_submission_before_launch() = runTest {
        val fixture = LastLightLocalFixture(backgroundScope)
        assertTrue(fixture.runtime.takeDevice())
        val before = fixture.raw.currentState()
        val card = fixture.runtime.presentation.value.game.yourHand.first().id

        assertTrue(fixture.runtime.play(listOf(card)))
        assertEquals(PendingAction.PLAY_CARDS, fixture.runtime.presentation.value.pendingAction)
        assertFalse(fixture.runtime.play(listOf(card)))
        assertFalse(fixture.runtime.challenge())
        assertFalse(fixture.runtime.nextRound())
        assertFalse(fixture.runtime.takeDevice())
        assertTrue(fixture.runtime.presentation.value.game.yourHand.isEmpty())
        assertEquals(before, fixture.raw.currentState())
        runCurrent()

        assertEquals(1, fixture.submissions)
        assertEquals(before.public.acceptedPlaySequence + 1, fixture.raw.currentState().public.acceptedPlaySequence)
        assertNull(fixture.runtime.presentation.value.pendingAction)
    }

    @Test
    fun each_turn_requires_handoff_and_only_that_players_private_slice_crosses_the_ui_boundary() = runTest {
        val fixture = LastLightLocalFixture(backgroundScope)
        assertNull(fixture.runtime.presentation.value.game.viewerId)
        assertTrue(fixture.runtime.presentation.value.game.yourHand.isEmpty())
        assertNotNull(fixture.runtime.presentation.value.handoffPlayerName)
        assertFalse(fixture.runtime.play(listOf("not-an-owned-card")))

        assertTrue(fixture.runtime.takeDevice())
        val firstView = fixture.runtime.presentation.value.game
        assertEquals(
            fixture.raw.currentState().privatePerPlayer.getValue(PlayerId(requireNotNull(firstView.viewerId))).hand,
            firstView.yourHand,
        )
        assertTrue(fixture.runtime.play(listOf(firstView.yourHand.first().id)))
        // Privacy is removed even before the launch has a chance to run.
        assertNull(fixture.runtime.presentation.value.game.viewerId)
        assertTrue(fixture.runtime.presentation.value.game.yourHand.isEmpty())
        runCurrent()
        val handoff = fixture.runtime.presentation.value
        assertNull(handoff.game.viewerId)
        assertTrue(handoff.game.yourHand.isEmpty())
        assertNotNull(handoff.handoffPlayerName)

        assertTrue(fixture.runtime.takeDevice())
        val nextView = fixture.runtime.presentation.value.game
        assertTrue(firstView.viewerId != nextView.viewerId)
        assertTrue(firstView.yourHand.map { it.id }.intersect(nextView.yourHand.map { it.id }.toSet()).isEmpty())
        assertEquals(
            fixture.raw.currentState().privatePerPlayer.getValue(PlayerId(requireNotNull(nextView.viewerId))).hand,
            nextView.yourHand,
        )
    }

    @Test
    fun background_or_conflated_interruption_invalidates_queued_actions_and_requires_fresh_handoff() = runTest {
        val fixture = LastLightLocalFixture(backgroundScope)
        assertTrue(fixture.runtime.takeDevice())
        val before = fixture.raw.currentState()
        assertTrue(fixture.runtime.play(listOf(fixture.runtime.presentation.value.game.yourHand.first().id)))
        fixture.runtime.setVisibility(LastLightProcessVisibility(false, 1L))
        assertFalse(fixture.runtime.takeDevice())
        fixture.runtime.setVisibility(LastLightProcessVisibility(true, 1L))
        runCurrent()

        assertEquals(0, fixture.submissions)
        assertEquals(before, fixture.raw.currentState())
        assertTrue(fixture.runtime.presentation.value.game.yourHand.isEmpty())
        assertFalse(fixture.runtime.play(listOf("stale-card")))
        assertTrue(fixture.runtime.takeDevice())
        fixture.runtime.setVisibility(LastLightProcessVisibility(true, 2L))
        assertTrue(fixture.runtime.presentation.value.game.yourHand.isEmpty())
        assertFalse(fixture.runtime.challenge())
    }

    @Test
    fun live_platform_visibility_is_checked_even_before_compose_delivers_its_next_state() = runTest {
        var actual = LastLightProcessVisibility(true, 0L)
        val fixture = LastLightLocalFixture(backgroundScope, visibilityReader = { actual })
        assertTrue(fixture.runtime.takeDevice())
        val before = fixture.raw.currentState()
        assertTrue(fixture.runtime.play(listOf(fixture.runtime.presentation.value.game.yourHand.first().id)))
        actual = LastLightProcessVisibility(false, 1L)
        runCurrent()
        assertEquals(before, fixture.raw.currentState())
        assertEquals(0, fixture.submissions)

        actual = LastLightProcessVisibility(true, 1L)
        assertTrue(fixture.runtime.takeDevice())
        val card = fixture.runtime.presentation.value.game.yourHand.first().id
        actual = LastLightProcessVisibility(false, 2L)
        assertFalse(fixture.runtime.play(listOf(card)))
        assertTrue(fixture.runtime.presentation.value.game.yourHand.isEmpty())
    }

    @Test
    fun rejected_action_is_reported_without_automatic_retry_or_private_handoff_skip() = runTest {
        val fixture = LastLightLocalFixture(backgroundScope)
        assertTrue(fixture.runtime.takeDevice())
        val before = fixture.raw.currentState()
        assertTrue(fixture.runtime.play(listOf("unowned")))
        runCurrent()

        assertEquals(1, fixture.submissions)
        assertEquals(before, fixture.raw.currentState())
        assertEquals(LastLightLocalIssue.ActionRejected, fixture.runtime.presentation.value.issue)
        assertTrue(fixture.runtime.presentation.value.game.yourHand.isEmpty())
        assertNotNull(fixture.runtime.presentation.value.handoffPlayerName)
        runCurrent()
        assertEquals(1, fixture.submissions)
    }

    @Test
    fun exit_confirmation_blocks_queued_and_new_actions_and_closes_only_after_a_successful_flush() = runTest {
        val fixture = LastLightLocalFixture(backgroundScope)
        assertTrue(fixture.runtime.takeDevice())
        val before = fixture.raw.currentState()
        assertTrue(fixture.runtime.play(listOf(fixture.runtime.presentation.value.game.yourHand.first().id)))
        fixture.runtime.requestExit()
        assertTrue(fixture.runtime.presentation.value.game.yourHand.isEmpty())
        assertFalse(fixture.runtime.takeDevice())
        var exits = 0
        assertTrue(fixture.runtime.saveAndExit { exits++ })
        assertFalse(fixture.runtime.saveAndExit { exits++ })
        runCurrent()

        assertEquals(1, exits)
        assertEquals(0, fixture.submissions)
        assertEquals(before, fixture.raw.currentState())
        assertEquals(GamePhase.PLAYING, fixture.raw.currentState().phase)
        assertTrue(fixture.store.load(fixture.config.sessionId) is Result.Success)
        assertFalse(fixture.runtime.takeDevice())
    }

    @Test
    fun failed_exit_flush_keeps_the_match_open_and_can_retry_without_losing_the_latest_action() = runTest {
        val store = FailingLocalSnapshotStore()
        val fixture = LastLightLocalFixture(backgroundScope, store)
        runCurrent()
        store.failSave = true
        fixture.submitNextLegalAction()
        runCurrent()
        assertTrue(fixture.runtime.persistenceStatus.value is SnapshotWriteStatus.Failed)
        fixture.runtime.requestExit()
        var exits = 0
        assertTrue(fixture.runtime.saveAndExit { exits++ })
        runCurrent()

        assertEquals(0, exits)
        assertFalse(fixture.runtime.presentation.value.exitInFlight)
        assertEquals(LastLightLocalIssue.SaveFailed, fixture.runtime.presentation.value.issue)
        store.failSave = false
        assertTrue(fixture.runtime.saveAndExit { exits++ })
        runCurrent()
        assertEquals(1, exits)
        val snapshot = (store.load(fixture.config.sessionId) as Result.Success).data
        assertEquals(fixture.raw.currentState(), fixture.definition.snapshotCodec().decode(snapshot.payload))
    }

    @Test
    fun exit_waits_for_an_already_started_submission_and_flushes_its_committed_result() = runTest {
        val release = CompletableDeferred<Unit>()
        val fixture = LastLightLocalFixture(backgroundScope, beforeSubmit = { release.await() })
        fixture.submitNextLegalAction()
        runCurrent()
        assertEquals(1, fixture.submissions)
        assertEquals(0L, fixture.raw.currentState().public.acceptedPlaySequence)
        fixture.runtime.requestExit()
        var exited = false
        assertTrue(fixture.runtime.saveAndExit { exited = true })
        runCurrent()
        assertFalse(exited)

        release.complete(Unit)
        runCurrent()
        assertTrue(exited)
        assertEquals(1L, fixture.raw.currentState().public.acceptedPlaySequence)
        val snapshot = (fixture.store.load(fixture.config.sessionId) as Result.Success).data
        assertEquals(fixture.raw.currentState(), fixture.definition.snapshotCodec().decode(snapshot.payload))
    }
}
