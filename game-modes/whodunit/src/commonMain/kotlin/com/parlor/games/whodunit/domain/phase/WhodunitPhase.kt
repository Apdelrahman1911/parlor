package com.parlor.games.whodunit.domain.phase

import com.parlor.engine.phase.GamePhase
import kotlinx.serialization.Serializable

/**
 * Whodunit phases — the sealed flow from Setup through to PostGame.
 *
 * Pause and PrivateReview are NOT phases — they are modal overlays that
 * suspend the active phase without changing it.
 */
@Serializable
sealed class WhodunitPhase : GamePhase {
    @Serializable data object Setup : WhodunitPhase() { override val id = "setup" }
    @Serializable data object PublicIntro : WhodunitPhase() { override val id = "public-intro" }
    @Serializable data object RulesBriefing : WhodunitPhase() { override val id = "rules-briefing" }

    @Serializable
    data class CharacterReveal(val playerIndex: Int) : WhodunitPhase() {
        override val id = "character-reveal"
    }

    @Serializable
    data class Round(val index: Int) : WhodunitPhase() {
        override val id = "round"
    }

    @Serializable data object FinalVote : WhodunitPhase() { override val id = "final-vote" }
    @Serializable data object TiedRevote : WhodunitPhase() { override val id = "tied-revote" }
    @Serializable data object Reveal : WhodunitPhase() { override val id = "reveal" }
    @Serializable data object PostGame : WhodunitPhase() { override val id = "post-game" }
}
