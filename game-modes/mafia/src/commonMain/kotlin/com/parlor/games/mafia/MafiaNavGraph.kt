package com.parlor.games.mafia

import com.parlor.core.ids.GameId
import com.parlor.navigation.ModuleNavGraph
import com.parlor.navigation.ParlorRoute

class MafiaNavGraph : ModuleNavGraph {
    override val gameId: GameId = MafiaIds.GameId
    override val entryRoute: ParlorRoute = ParlorRoute.GameGraph(
        gameId = MafiaIds.GameId.raw,
        caseId = "default",
    )
}
