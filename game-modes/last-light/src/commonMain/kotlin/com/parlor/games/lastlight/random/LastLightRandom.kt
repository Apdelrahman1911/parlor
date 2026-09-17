package com.parlor.games.lastlight.random

import com.parlor.games.lastlight.domain.rules.LastLightRules
import com.parlor.networking.security.SecureHashes
import kotlin.random.Random

/**
 * Deterministic HMAC-SHA-256 counter stream using the platform hash primitive.
 * Domain code receives only Random. Round separation lets snapshots retain the
 * seed rather than a process-local cursor; observing cards cannot reveal a
 * reversible Kotlin Random state. Entropy remains limited by Parlor's secret
 * 64-bit SessionSeedSource contract; this expansion does not add entropy.
 */
internal object LastLightRandom {
    fun forRound(seed: Long, round: Int): Random {
        require(round in 1..LastLightRules.MAX_ROUNDS) { "Invalid Last Light entropy round" }
        return RoundRandom(seed, round)
    }

    private class RoundRandom(seed: Long, private val round: Int) : Random() {
        private val pads = pads(seed)
        private var counter = 0L
        private var block = byteArrayOf()
        private var offset = 0

        override fun nextBits(bitCount: Int): Int {
            require(bitCount in 0..WORD_BITS)
            if (bitCount == 0) return 0
            if (offset == block.size) {
                block.fill(0)
                block = nextBlock()
                offset = 0
            }
            var word = 0
            repeat(WORD_BYTES) { word = (word shl BYTE_BITS) or (block[offset++].toInt() and BYTE_MASK) }
            return word ushr (WORD_BITS - bitCount)
        }

        private fun nextBlock(): ByteArray {
            val message = "parlor:last-light:entropy:v1:$round:${counter++}".encodeToByteArray()
            val inner = SecureHashes.sha256(pads.first + message)
            return try {
                SecureHashes.sha256(pads.second + inner)
            } finally {
                inner.fill(0)
                message.fill(0)
            }
        }
    }

    private fun pads(seed: Long): Pair<ByteArray, ByteArray> {
        val key = seed.toString().encodeToByteArray()
        return try {
            ByteArray(HASH_BLOCK_BYTES) { (key.getOrElse(it) { 0 }.toInt() xor INNER_PAD).toByte() } to
                ByteArray(HASH_BLOCK_BYTES) { (key.getOrElse(it) { 0 }.toInt() xor OUTER_PAD).toByte() }
        } finally {
            key.fill(0)
        }
    }

    private const val HASH_BLOCK_BYTES = 64
    private const val WORD_BYTES = 4
    private const val WORD_BITS = 32
    private const val BYTE_BITS = 8
    private const val BYTE_MASK = 0xff
    private const val INNER_PAD = 0x36
    private const val OUTER_PAD = 0x5c
}
