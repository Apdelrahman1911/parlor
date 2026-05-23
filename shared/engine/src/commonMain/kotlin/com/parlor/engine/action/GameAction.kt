package com.parlor.engine.action

/**
 * Marker for an action submitted into a `GameSession`. Each module supplies a
 * sealed hierarchy (Whodunit: AssignRoles, RevealClue, CastVote, …).
 */
interface GameAction
