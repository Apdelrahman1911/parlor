package com.parlor.games.dominoes.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.dominoes.domain.DominoState
import com.parlor.games.dominoes.resources.Res
import com.parlor.games.dominoes.resources.dom_empty
import com.parlor.games.dominoes.resources.dom_mark_a
import com.parlor.games.dominoes.resources.dom_mark_b
import com.parlor.games.dominoes.resources.dom_table
import com.parlor.games.dominoes.resources.dom_tile
import org.jetbrains.compose.resources.stringResource

private class BoardOrigin(var value: Offset = Offset.Zero)

@Composable
internal fun DominoBoard(state: DominoState, motion: DominoMotion) {
    val layout = remember(state.public.chain) { dominoChainLayout(state.public.chain) }
    val density = LocalDensity.current
    val title = stringResource(Res.string.dom_table)
    val duration = if (ParlorTheme.reducedMotion) 0 else 280
    val origin = remember(motion) { BoardOrigin() }
    // A physical chain is spatial, not a sentence: all peers see the same orientation in both locales.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val scale = constraints.maxWidth / TABLE_WIDTH
            val top = (with(density) { 200.dp.toPx() } - layout.height * scale).coerceAtLeast(0f) / 2
            val height = with(density) { (layout.height * scale + top * 2).toDp() }
            val poses = layout.poses.mapValues { (_, pose) ->
                DominoFlightPose(Offset(pose.x * scale, pose.y * scale + top), TILE_LONG * scale, pose.angle)
            }
            val updateGeometry = {
                motion.board = poses.mapValues { (_, pose) -> pose.copy(center = pose.center + origin.value) }
            }
            SideEffect { updateGeometry() }
            Box(
                Modifier.fillMaxWidth().height(height).background(DominoInk.copy(alpha = .22f), RoundedCornerShape(28.dp))
                    .border(1.dp, DominoGold.copy(alpha = .18f), RoundedCornerShape(28.dp))
                    .semantics { contentDescription = title }
                    .onGloballyPositioned { origin.value = it.positionInRoot(); updateGeometry() },
            ) {
                if (state.public.chain.isEmpty()) {
                    Text(stringResource(Res.string.dom_empty), color = DominoGold.copy(alpha = .7f),
                        style = ParlorTheme.typography.headingMedium, modifier = Modifier.align(Alignment.Center))
                }
                state.public.chain.forEach { placed ->
                    key(placed.tile.id) {
                        val pose = poses.getValue(placed.tile.id)
                        val position by animateOffsetAsState(pose.center, tween(duration))
                        val angle by animateFloatAsState(pose.angle, tween(duration))
                        val label = stringResource(Res.string.dom_tile, placed.left, placed.right)
                        DominoTileFace(placed.left, placed.right,
                            Modifier.size(with(density) { pose.length.toDp() }, with(density) { (pose.length / 2).toDp() })
                                .graphicsLayer {
                                    translationX = position.x - pose.length / 2
                                    translationY = position.y - pose.length / 4
                                    rotationZ = angle
                                    alpha = if (motion.hiddenBoardTile == placed.tile.id) 0f else 1f
                                }.semantics { contentDescription = label })
                    }
                }
                state.public.chain.firstOrNull()?.let { first ->
                    val pose = poses.getValue(first.tile.id)
                    EndMark(stringResource(Res.string.dom_mark_a), pose.center - Offset(0f, pose.length * .72f))
                }
                state.public.chain.lastOrNull()?.let { last ->
                    val pose = poses.getValue(last.tile.id)
                    EndMark(stringResource(Res.string.dom_mark_b), pose.center + Offset(0f, pose.length * .6f))
                }
            }
        }
    }
}

@Composable
private fun EndMark(label: String, center: Offset) {
    val density = LocalDensity.current
    Text(label, color = DominoGold, style = ParlorTheme.typography.labelMedium,
        modifier = Modifier.offset(with(density) { center.x.toDp() - 5.dp }, with(density) { center.y.toDp() - 8.dp }))
}
