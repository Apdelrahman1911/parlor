package com.parlor.core.random

import kotlin.random.Random

/**
 * Randomness abstraction. The engine takes a RandomSource via constructor
 * parameter so role assignment, clue draw, and seat shuffling can be
 * deterministically reproduced in tests by passing a seeded instance.
 */
interface RandomSource {
    fun nextLong(): Long
    fun nextInt(): Int
    fun nextInt(until: Int): Int
    fun nextDouble(): Double
    fun <T> pick(items: List<T>): T
    fun <T> shuffled(items: List<T>): List<T>

    companion object {
        fun seeded(seed: Long): RandomSource = SeededRandomSource(seed)
    }
}

private class SeededRandomSource(seed: Long) : RandomSource {
    private val random = Random(seed)
    override fun nextLong(): Long = random.nextLong()
    override fun nextInt(): Int = random.nextInt()
    override fun nextInt(until: Int): Int = random.nextInt(until)
    override fun nextDouble(): Double = random.nextDouble()
    override fun <T> pick(items: List<T>): T {
        require(items.isNotEmpty()) { "Cannot pick from empty list" }
        return items[random.nextInt(items.size)]
    }
    override fun <T> shuffled(items: List<T>): List<T> = items.shuffled(random)
}
