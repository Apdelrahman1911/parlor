package com.parlor.engine.action

/**
 * Marker for an action submitted through the session authority to a game
 * reducer. Each module supplies a sealed hierarchy (Whodunit: AssignRoles,
 * RevealClue, CastVote, …).
 */
interface GameAction
