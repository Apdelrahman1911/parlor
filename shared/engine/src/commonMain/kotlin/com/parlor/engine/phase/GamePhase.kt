package com.parlor.engine.phase

/**
 * Marker for a game phase. Each module supplies a sealed hierarchy of phases
 * (Whodunit: Setup, Reveal, Round(n), Vote, …).
 *
 * The engine treats phases opaquely; only the module's reducer interprets them.
 */
interface GamePhase {
    /** Stable id for telemetry, snapshots, debugging. Not localized. */
    val id: String
}
