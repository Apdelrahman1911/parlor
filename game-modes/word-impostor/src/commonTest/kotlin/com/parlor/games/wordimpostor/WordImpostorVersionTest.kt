package com.parlor.games.wordimpostor

import com.parlor.core.ids.PlayerId
import com.parlor.engine.state.Player
import com.parlor.games.wordimpostor.domain.WordImpostorAction
import com.parlor.games.wordimpostor.domain.WordImpostorReducer
import com.parlor.games.wordimpostor.domain.WordImpostorSettings
import com.parlor.games.wordimpostor.protocol.WordImpostorCodec
import com.parlor.games.wordimpostor.protocol.WordImpostorSnapshotCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class WordImpostorVersionTest {
    @Test fun legacy_and_future_actions_projections_and_authority_snapshots_fail_closed() {
        assertEquals(2, WordImpostorIds.VERSION)
        val players = List(3) { Player(PlayerId("p$it"), "Player $it", it) }
        val state = WordImpostorReducer().initial(players, WordImpostorSettings(), 17)
        val id = players.first().id
        val publicBytes = WordImpostorCodec.encodePublic(state)
        val public = WordImpostorCodec.decodePublic(publicBytes)
        val privateBytes = WordImpostorCodec.encodePrivate(state, id)
        val actionBytes = WordImpostorCodec.encodeAction(WordImpostorAction.Ready(id, state.public.token))
        val snapshots = WordImpostorSnapshotCodec()
        val snapshotBytes = snapshots.encode(state)
        for (version in listOf(1, 3)) {
            fun changed(bytes: ByteArray) = bytes.decodeToString()
                .replace("\"version\":2", "\"version\":$version").encodeToByteArray()
            assertFails { WordImpostorCodec.decodeAction(changed(actionBytes)) }
            assertFails { WordImpostorCodec.decodePublic(changed(publicBytes)) }
            assertFails { WordImpostorCodec.decodePlayer(public, changed(privateBytes), id) }
            assertFails { snapshots.decode(changed(snapshotBytes)) }
        }
    }
}
