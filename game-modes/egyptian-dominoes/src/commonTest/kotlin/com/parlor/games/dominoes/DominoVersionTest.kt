package com.parlor.games.dominoes

import com.parlor.core.ids.PlayerId
import com.parlor.engine.state.Player
import com.parlor.games.dominoes.domain.DominoAction
import com.parlor.games.dominoes.domain.DominoReducer
import com.parlor.games.dominoes.domain.DominoSettings
import com.parlor.games.dominoes.protocol.DominoCodec
import com.parlor.games.dominoes.protocol.DominoSnapshotCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class DominoVersionTest {
    @Test fun legacy_and_future_actions_projections_and_authority_snapshots_fail_closed() {
        assertEquals(2, DominoIds.VERSION)
        val players = List(3) { Player(PlayerId("p$it"), "Player $it", it) }
        val state = DominoReducer().initial(players, DominoSettings(), 17)
        val id = players.first().id
        val publicBytes = DominoCodec.encodePublic(state)
        val public = DominoCodec.decodePublic(publicBytes)
        val privateBytes = DominoCodec.encodePrivate(state, id)
        val actionBytes = DominoCodec.encodeAction(DominoAction.Draw(id, state.public.token, state.public.move))
        val snapshots = DominoSnapshotCodec()
        val snapshotBytes = snapshots.encode(state)
        for (version in listOf(1, 3)) {
            fun changed(bytes: ByteArray) = bytes.decodeToString()
                .replace("\"version\":2", "\"version\":$version").encodeToByteArray()
            assertFails { DominoCodec.decodeAction(changed(actionBytes)) }
            assertFails { DominoCodec.decodePublic(changed(publicBytes)) }
            assertFails { DominoCodec.decodePlayer(public, changed(privateBytes), id) }
            assertFails { snapshots.decode(changed(snapshotBytes)) }
        }
    }
}
