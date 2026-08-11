package com.parlor.core.random

/**
 * Creates the secret seed for a fresh authoritative game session.
 *
 * Production bindings must use platform cryptographic entropy. Once created,
 * the seed is persisted in host-only state and gameplay derives a deterministic
 * [RandomSource] from it so recovery and tests remain reproducible.
 */
fun interface SessionSeedSource {
    fun nextSeed(): Long
}
