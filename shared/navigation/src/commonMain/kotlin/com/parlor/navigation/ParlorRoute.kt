package com.parlor.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe nav routes for the Parlor shell. Game modules define their own
 * route hierarchies and contribute them to the [NavGraphRegistry].
 */
@Serializable
sealed interface ParlorRoute {
    @Serializable
    data object Home : ParlorRoute

    @Serializable
    data class GameDetails(val gameId: String) : ParlorRoute

    @Serializable
    data object Settings : ParlorRoute

    @Serializable
    data object HowToPlay : ParlorRoute

    /** Entry point into a game module's nav graph. */
    @Serializable
    data class GameGraph(val gameId: String, val caseId: String) : ParlorRoute
}
