package com.parlor.games.dominoes.ui

import com.parlor.games.dominoes.domain.PlacedDomino

/** Physical tile poses on a bounded felt surface. Doubles sit across the line, never act as spinners. */
internal data class DominoBoardPose(val x: Float, val y: Float, val angle: Float) {
    val width: Float get() = if (angle == 90f || angle == 270f) TILE_SHORT else TILE_LONG
    val height: Float get() = if (angle == 90f || angle == 270f) TILE_LONG else TILE_SHORT
}

internal data class DominoChainLayout(val poses: Map<Int, DominoBoardPose>, val height: Float)

internal const val TABLE_WIDTH = 620f
internal const val TILE_LONG = 72f
internal const val TILE_SHORT = 36f
private const val MARGIN = 64f
private const val GAP = 2f

/**
 * Pack a continuous snake. A corner tile touches the last tile's outside half;
 * the next row leaves its lower half in the opposite direction, as on a real table.
 * One unique tile is consumed per pose, including corner pieces.
 */
internal fun dominoChainLayout(chain: List<PlacedDomino>): DominoChainLayout {
    require(chain.size <= 28)
    val poses = linkedMapOf<Int, DominoBoardPose>()
    var direction = 1
    var cursor = MARGIN
    var row = MARGIN
    var last: DominoBoardPose? = null
    chain.forEachIndexed { index, placed ->
        val width = if (placed.tile.isDouble) TILE_SHORT else TILE_LONG
        val fits = if (direction > 0) cursor + width <= TABLE_WIDTH - MARGIN else cursor - width >= MARGIN
        val previous = last
        val pose = if (fits || previous == null) {
            val angle = if (placed.tile.isDouble) 90f else if (direction > 0) 0f else 180f
            DominoBoardPose(cursor + direction * width / 2, row, angle).also { cursor += direction * (width + GAP) }
        } else {
            val height = if (placed.tile.isDouble) TILE_SHORT else TILE_LONG
            val corner = DominoBoardPose(
                previous.x + direction * (previous.width / 2 - TILE_SHORT / 2),
                row + previous.height / 2 + GAP + height / 2,
                if (placed.tile.isDouble) 0f else 90f,
            )
            direction = -direction
            val nextHalfHeight = if (chain.getOrNull(index + 1)?.tile?.isDouble == true) TILE_LONG / 2 else TILE_SHORT / 2
            row = corner.y + height / 2 + GAP + nextHalfHeight
            cursor = corner.x - direction * TILE_SHORT / 2
            corner
        }
        check(poses.put(placed.tile.id, pose) == null) { "Duplicate tile in table layout" }
        last = pose
    }
    val low = poses.values.minOfOrNull { it.x - it.width / 2 } ?: 0f
    val high = poses.values.maxOfOrNull { it.x + it.width / 2 } ?: TABLE_WIDTH
    val shift = (TABLE_WIDTH - low - high) / 2
    return DominoChainLayout(
        poses.mapValues { (_, pose) -> pose.copy(x = pose.x + shift) },
        ((poses.values.maxOfOrNull { it.y + it.height / 2 } ?: MARGIN) + MARGIN).coerceAtLeast(220f),
    )
}
