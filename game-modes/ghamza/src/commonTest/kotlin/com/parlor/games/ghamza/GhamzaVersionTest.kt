package com.parlor.games.ghamza

import com.parlor.core.ids.PlayerId
import com.parlor.engine.state.Player
import com.parlor.games.ghamza.domain.GhamzaAction
import com.parlor.games.ghamza.domain.GhamzaReducer
import com.parlor.games.ghamza.domain.GhamzaSettings
import com.parlor.games.ghamza.protocol.GhamzaCodec
import com.parlor.games.ghamza.protocol.GhamzaSnapshotCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class GhamzaVersionTest {
    @Test fun legacy_and_future_actions_projections_and_authority_snapshots_fail_closed() {
        assertEquals(2, GhamzaIds.VERSION)
        val players = List(3) { Player(PlayerId("p$it"), "Player $it", it) }
        val state = GhamzaReducer().initial(players, GhamzaSettings(), 17)
        val id = players.first().id
        val publicBytes = GhamzaCodec.encodePublic(state)
        val public = GhamzaCodec.decodePublic(publicBytes)
        val privateBytes = GhamzaCodec.encodePrivate(state, id)
        val actionBytes = GhamzaCodec.encodeAction(GhamzaAction.Ready(id, state.public.token))
        val snapshots = GhamzaSnapshotCodec()
        val snapshotBytes = snapshots.encode(state)
        for (version in listOf(1, 3)) {
            fun changed(bytes: ByteArray) = bytes.decodeToString()
                .replace("\"version\":2", "\"version\":$version").encodeToByteArray()
            assertFails { GhamzaCodec.decodeAction(changed(actionBytes)) }
            assertFails { GhamzaCodec.decodePublic(changed(publicBytes)) }
            assertFails { GhamzaCodec.decodePlayer(public, changed(privateBytes), id) }
            assertFails { snapshots.decode(changed(snapshotBytes)) }
        }
    }
}
