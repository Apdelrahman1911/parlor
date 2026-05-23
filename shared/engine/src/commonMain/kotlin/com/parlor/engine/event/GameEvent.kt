package com.parlor.engine.event

/**
 * Marker for one-shot events emitted by the reducer (sound cue, UI motion,
 * persistence trigger). Distinct from state — state describes what *is*; events
 * describe what *just happened*.
 *
 * Game modules supply a sealed hierarchy.
 */
interface GameEvent
