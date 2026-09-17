package com.parlor.games.dominoes.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.parlor.core.ids.PlayerId
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.dominoes.domain.DominoState
import com.parlor.games.dominoes.domain.PlacedDomino
import kotlin.math.PI
import kotlin.math.sin

internal data class DominoFlightPose(val center: Offset, val length: Float, val angle: Float)
internal data class DominoFlight(val tile: PlacedDomino?, val from: DominoFlightPose, val to: DominoFlightPose)

/** Transient geometry only. This object is discarded at every concealment/recovery epoch. */
internal class DominoMotion(state: DominoState) {
    var previous = state
    var rootOrigin = Offset.Zero
    val hand = mutableMapOf<Int, DominoFlightPose>()
    val seats = mutableMapOf<PlayerId, DominoFlightPose>()
    var board = emptyMap<Int, DominoFlightPose>()
    var stock: DominoFlightPose? = null
    var rack: DominoFlightPose? = null
    var flight by mutableStateOf<DominoFlight?>(null)
    var hiddenBoardTile by mutableStateOf<Int?>(null)
    var hiddenHandTile by mutableStateOf<Int?>(null)
    val progress = Animatable(0f)
}

@Composable
internal fun rememberDominoMotion(state: DominoState, self: PlayerId, privacyEpoch: Long, handVisible: Boolean): DominoMotion {
    val motion = remember(state.public.token, privacyEpoch, handVisible) { DominoMotion(state) }
    val reducedMotion = ParlorTheme.reducedMotion
    LaunchedEffect(state.public.token, state.public.move, motion, reducedMotion) {
        val before = motion.previous
        motion.previous = state
        if (reducedMotion || state.public.move != before.public.move + 1 || state.public.token != before.public.token) {
            return@LaunchedEffect // Initial snapshots, skipped revisions and recovery never replay old moves.
        }
        val added = state.public.chain.singleOrNull { next -> before.public.chain.none { it.tile.id == next.tile.id } }
        try {
            if (added != null) motion.hiddenBoardTile = added.tile.id
            val drew = if (before.public.stockCount == state.public.stockCount + 1) {
                state.players.firstOrNull { state.public.handCounts.getValue(it.id) == before.public.handCounts.getValue(it.id) + 1 }?.id
            } else null
            val drawnTile = if (drew == self && handVisible) {
                state.privatePerPlayer.getValue(self).hand.singleOrNull { it !in before.privatePerPlayer.getValue(self).hand }
            } else null
            motion.hiddenHandTile = drawnTile?.id
            withFrameNanos { } // Resolve final table/rack positions after this accepted revision is laid out.
            val flight = if (added != null) placementFlight(motion, added, self) else {
                val target = drawnTile?.let { motion.hand[it.id] }
                    ?: if (drew == self) motion.rack else motion.seats[drew]
                val source = motion.stock
                if (drew != null && source != null && target != null) DominoFlight(null, source, target) else null
            }
            if (flight != null) {
                motion.progress.snapTo(0f)
                motion.flight = flight
                motion.progress.animateTo(1f, tween(620, easing = FastOutSlowInEasing))
            }
        } finally {
            motion.flight = null
            motion.hiddenBoardTile = null
            motion.hiddenHandTile = null
        }
    }
    return motion
}

private fun placementFlight(motion: DominoMotion, placed: PlacedDomino, self: PlayerId): DominoFlight? {
    val target = motion.board[placed.tile.id] ?: return null
    val source = if (placed.by == self) motion.hand[placed.tile.id] ?: motion.rack else motion.seats[placed.by]
    val origin = source ?: return null
    // Hand tiles are low/high. Rotate through the matching end instead of changing their pips in mid-air.
    val from = if (placed.by == self && placed.left != placed.tile.low) origin.copy(angle = origin.angle + 180f) else origin
    return DominoFlight(placed, from, target)
}

@Composable
internal fun DominoFlightOverlay(motion: DominoMotion, modifier: Modifier = Modifier) {
    val flight = motion.flight ?: return
    val density = LocalDensity.current
    val baseLength = with(density) { 72.dp.toPx() }
    DominoTileFace(
        flight.tile?.left ?: 0, flight.tile?.right ?: 0,
        modifier.size(72.dp, 36.dp).clearAndSetSemantics {}.graphicsLayer {
            val t = motion.progress.value
            val target = flight.tile?.let { motion.board[it.tile.id] } ?: flight.to
            val lift = sin(t * PI).toFloat()
            val position = flight.from.center * (1f - t) + target.center * t - Offset(0f, 58.dp.toPx() * lift)
            val size = (flight.from.length * (1f - t) + target.length * t) / baseLength
            val rotation = ((target.angle - flight.from.angle + 540f) % 360f) - 180f
            translationX = position.x - motion.rootOrigin.x - baseLength / 2
            translationY = position.y - motion.rootOrigin.y - baseLength / 4
            rotationZ = flight.from.angle + rotation * t
            scaleX = size * (1f + .08f * lift)
            scaleY = scaleX
            shadowElevation = 12.dp.toPx() * lift
        },
        covered = flight.tile == null,
    )
}
