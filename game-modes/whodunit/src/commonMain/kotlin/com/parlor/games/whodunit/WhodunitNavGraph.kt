package com.parlor.games.whodunit

import com.parlor.core.ids.GameId
import com.parlor.navigation.ModuleNavGraph
import com.parlor.navigation.ParlorRoute

/**
 * Whodunit's contribution to the Parlor nav registry. Phase 4 wires the
 * actual Compose NavHost destinations behind this declaration.
 */
class WhodunitNavGraph : ModuleNavGraph {
    override val gameId: GameId = WhodunitIds.GameId
    override val entryRoute: ParlorRoute = ParlorRoute.GameGraph(
        gameId = WhodunitIds.GameId.raw,
        caseId = "last-dinner",  // The Last Dinner is the MVP-shipped case.
    )
}
