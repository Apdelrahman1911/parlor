package com.parlor.games.dominoes.ui

import com.parlor.core.ids.PlayerId
import com.parlor.core.random.RandomSource
import com.parlor.games.dominoes.domain.DominoTile
import com.parlor.games.dominoes.domain.PlacedDomino
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DominoChainLayoutTest {
    @Test
    fun all_chain_lengths_and_double_heavy_turns_stay_connected_unique_and_nonoverlapping() {
        for (seed in 0L..99L) {
            val shuffled = RandomSource.seeded(seed).shuffled(DominoTile.Set)
            for (length in 0..28) verify(shuffled.take(length))
        }
        verify(DominoTile.Set.sortedByDescending { it.isDouble })
        verify(DominoTile.Set.sortedBy { it.isDouble })
    }

    @Test
    fun duplicate_or_oversized_boards_fail_instead_of_hiding_a_tile() {
        val tile = PlacedDomino(DominoTile(6, 6), 6, 6, PlayerId("a"), 1)
        assertFailsWith<IllegalStateException> { dominoChainLayout(listOf(tile, tile)) }
        assertFailsWith<IllegalArgumentException> { dominoChainLayout(List(29) { tile }) }
    }

    private fun verify(tiles: List<DominoTile>) {
        val chain = tiles.mapIndexed { index, tile -> PlacedDomino(tile, tile.low, tile.high, PlayerId("a"), index + 1) }
        val layout = dominoChainLayout(chain)
        assertEquals(tiles.size, layout.poses.size)
        assertEquals(layout, dominoChainLayout(chain))
        val poses = tiles.map { layout.poses.getValue(it.id) }
        poses.forEach { pose ->
            assertTrue(pose.x - pose.width / 2 >= 0f && pose.x + pose.width / 2 <= TABLE_WIDTH)
            assertTrue(pose.y - pose.height / 2 >= 0f && pose.y + pose.height / 2 <= layout.height)
            assertEquals(TILE_LONG * TILE_SHORT, pose.width * pose.height)
        }
        poses.forEachIndexed { i, a ->
            poses.drop(i + 1).forEachIndexed { offset, b ->
                val dx = abs(a.x - b.x) - (a.width + b.width) / 2
                val dy = abs(a.y - b.y) - (a.height + b.height) / 2
                assertTrue(dx >= 0 || dy >= 0, "Physical tiles $i and ${i + offset + 1} overlap")
                if (offset == 0) assertTrue(hypot(dx.coerceAtLeast(0f), dy.coerceAtLeast(0f)) <= 2.01f,
                    "Adjacent halves of the domino chain must touch, not become a grid")
            }
        }
    }
}
