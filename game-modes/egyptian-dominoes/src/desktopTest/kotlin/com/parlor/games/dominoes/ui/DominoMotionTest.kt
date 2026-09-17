package com.parlor.games.dominoes.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import com.parlor.core.ids.PlayerId
import com.parlor.games.dominoes.domain.DominoAction
import com.parlor.games.dominoes.domain.DominoPhase
import com.parlor.games.dominoes.domain.DominoProjection
import com.parlor.games.dominoes.domain.DominoReducer
import com.parlor.games.dominoes.domain.DominoRules
import com.parlor.games.dominoes.domain.DominoSettings
import com.parlor.games.dominoes.domain.DominoState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Real Compose clock and production rack/seat/stock/board measurements, not synthetic animation poses. */
@OptIn(ExperimentalTestApi::class)
class DominoMotionTest {
    private val reducer = DominoReducer()

    @Test
    fun accepted_placements_fly_from_the_actual_hand_or_opponent_seat_and_settle_on_the_chain() {
        for (ownMove in listOf(true, false)) runComposeUiTest {
            mainClock.autoAdvance = false
            val initial = reducer.initial(uiPlayers(3), DominoSettings(), 42)
            val actor = checkNotNull(initial.public.turn)
            val self = if (ownMove) actor else initial.players.first { it.id != actor }.id
            val action = legalMove(initial) as DominoAction.Place
            val accepted = reducer.apply(initial, action)
            var shown by mutableStateOf(DominoProjection.toPlayer(initial, self).state)
            lateinit var motion: DominoMotion
            setContent {
                GameUiFrame(width = 800, height = 900, reducedMotion = false) {
                    MotionTable(shown, self, 0, ownMove) { motion = it }
                }
            }
            mainClock.advanceTimeBy(100)
            waitForIdle()
            val source = runOnIdle {
                assertNull(motion.flight, "An initial snapshot is not a newly played tile")
                assertNotNull(if (ownMove) motion.hand[action.tileId] else motion.seats[actor])
            }
            runOnIdle { shown = DominoProjection.toPlayer(accepted, self).state }
            mainClock.advanceTimeBy(100)
            waitForIdle()
            runOnIdle {
                val flight = assertNotNull(motion.flight)
                assertEquals(action.tileId, flight.tile?.tile?.id)
                assertEquals(source.center, flight.from.center)
                assertEquals(source.length, flight.from.length)
                assertEquals(motion.board[action.tileId], flight.to)
                assertEquals(action.tileId, motion.hiddenBoardTile)
                assertTrue(motion.progress.value > 0f && motion.progress.value < 1f)
                assertEquals(6, shown.public.handCounts[actor])
                assertEquals(1, shown.public.chain.size)
            }
            mainClock.advanceTimeBy(800)
            runOnIdle {
                assertNull(motion.flight)
                assertNull(motion.hiddenBoardTile)
                assertEquals(1f, motion.progress.value)
            }
        }
    }

    @Test
    fun draws_travel_covered_from_stock_to_the_receiving_hand_or_seat_without_disclosing_the_tile() {
        for (ownDraw in listOf(true, false)) runComposeUiTest {
            mainClock.autoAdvance = false
            val initial = firstDrawableState()
            val actor = checkNotNull(initial.public.turn)
            val self = if (ownDraw) actor else initial.players.first { it.id != actor }.id
            val accepted = reducer.apply(initial, DominoAction.Draw(actor, initial.public.token, initial.public.move))
            val drawn = accepted.privatePerPlayer.getValue(actor).hand.last()
            var shown by mutableStateOf(DominoProjection.toPlayer(initial, self).state)
            lateinit var motion: DominoMotion
            setContent {
                GameUiFrame(width = 800, height = 900, reducedMotion = false) {
                    MotionTable(shown, self, 0, ownDraw) { motion = it }
                }
            }
            mainClock.advanceTimeBy(100)
            waitForIdle()
            val source = runOnIdle { assertNotNull(motion.stock) }
            runOnIdle { shown = DominoProjection.toPlayer(accepted, self).state }
            mainClock.advanceTimeBy(100)
            waitForIdle()
            runOnIdle {
                val flight = assertNotNull(motion.flight)
                assertNull(flight.tile, "Drawing never displays a private tile in the shared flight overlay")
                assertEquals(source, flight.from)
                assertEquals(if (ownDraw) motion.hand[drawn.id] else motion.seats[actor], flight.to)
                assertEquals(if (ownDraw) drawn.id else null, motion.hiddenHandTile)
                assertTrue(motion.progress.value > 0f && motion.progress.value < 1f)
                assertEquals(initial.public.stockCount - 1, shown.public.stockCount)
            }
            mainClock.advanceTimeBy(800)
            runOnIdle {
                assertNull(motion.flight)
                assertNull(motion.hiddenHandTile)
            }
        }
    }

    @Test
    fun recovery_concealment_skipped_revisions_and_reduced_motion_never_replay_old_moves() = runComposeUiTest {
        mainClock.autoAdvance = false
        val initial = reducer.initial(uiPlayers(3), DominoSettings(), 42)
        val self = checkNotNull(initial.public.turn)
        val first = reducer.apply(initial, legalMove(initial))
        val second = reducer.apply(first, legalMove(first))
        var shown by mutableStateOf(DominoProjection.toPlayer(initial, self).state)
        var epoch by mutableStateOf(0L)
        var visible by mutableStateOf(true)
        var reducedMotion by mutableStateOf(false)
        lateinit var motion: DominoMotion
        setContent {
            GameUiFrame(width = 800, height = 900, reducedMotion = reducedMotion) {
                MotionTable(shown, self, epoch, visible) { motion = it }
            }
        }
        mainClock.advanceTimeBy(100)
        runOnIdle { shown = DominoProjection.toPlayer(first, self).state }
        mainClock.advanceTimeBy(100)
        val interrupted = runOnIdle { assertNotNull(motion.flight); motion }
        runOnIdle { epoch++ }
        mainClock.advanceTimeBy(100)
        runOnIdle {
            assertNotSame(interrupted, motion)
            assertNull(interrupted.flight)
            assertNull(interrupted.hiddenBoardTile)
            assertNull(motion.flight)
            visible = false
            shown = DominoProjection.toPlayer(second, self).state
        }
        mainClock.advanceTimeBy(100)
        runOnIdle { assertNull(motion.flight); visible = true }
        mainClock.advanceTimeBy(100)
        runOnIdle { assertNull(motion.flight); shown = DominoProjection.toPlayer(initial, self).state }
        mainClock.advanceTimeBy(100)
        runOnIdle { shown = DominoProjection.toPlayer(second, self).state }
        mainClock.advanceTimeBy(100)
        runOnIdle {
            assertNull(motion.flight, "A skipped revision must not invent the missing move sequence")
            shown = DominoProjection.toPlayer(initial, self).state
            reducedMotion = true
        }
        mainClock.advanceTimeBy(100)
        runOnIdle { shown = DominoProjection.toPlayer(first, self).state }
        mainClock.advanceTimeBy(100)
        runOnIdle { assertNull(motion.flight); assertNull(motion.hiddenBoardTile) }
    }

    private fun firstDrawableState(): DominoState {
        for (seed in 0L..30L) {
            var state = reducer.initial(uiPlayers(2), DominoSettings(), seed)
            repeat(DominoRules.MAX_MOVE) {
                if (state.phase != DominoPhase.Playing) return@repeat
                if (DominoRules.canDraw(state, checkNotNull(state.public.turn))) return state
                state = reducer.apply(state, legalMove(state))
            }
        }
        error("Expected a legal ordered-stock draw in the deterministic fixture matrix")
    }

    private fun legalMove(state: DominoState): DominoAction {
        val by = checkNotNull(state.public.turn)
        val tile = state.privatePerPlayer.getValue(by).hand.firstOrNull { DominoRules.playableEnds(state, by, it).isNotEmpty() }
        return when {
            tile != null -> DominoAction.Place(by, state.public.token, state.public.move, tile.id,
                DominoRules.playableEnds(state, by, tile).first())
            DominoRules.canDraw(state, by) -> DominoAction.Draw(by, state.public.token, state.public.move)
            else -> DominoAction.Pass(by, state.public.token, state.public.move)
        }
    }
}

@Composable
private fun MotionTable(state: DominoState, self: PlayerId, epoch: Long, visible: Boolean, capture: (DominoMotion) -> Unit) {
    val motion = rememberDominoMotion(state, self, epoch, visible)
    SideEffect { capture(motion) }
    Box(Modifier.fillMaxSize().onGloballyPositioned { motion.rootOrigin = it.positionInRoot() }) {
        Column {
            DominoOpponents(state, self, motion)
            DominoBoard(state, motion)
            DominoStock(state.public.stockCount, motion)
            DominoHand(state, self, true, visible, motion, {}, {})
        }
        DominoFlightOverlay(motion, Modifier.align(AbsoluteAlignment.TopLeft))
    }
}
