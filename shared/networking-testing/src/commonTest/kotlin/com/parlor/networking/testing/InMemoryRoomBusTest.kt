package com.parlor.networking.testing

import com.parlor.core.ids.GameId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.ProtocolVersion
import com.parlor.networking.room.SendTarget
import com.parlor.networking.protocol.SessionEnvelopeHeader
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

class InMemoryRoomBusTest {
    @Test
    fun directSendToUnknownPeerFailsInsteadOfBeingSilentlyDropped() = runTest {
        val bus = InMemoryRoomBus()
        val message = HostMessage.Heartbeat(
            header = SessionEnvelopeHeader(
                protocol = ProtocolVersion(),
                sessionId = SessionId("test-session"),
                gameId = GameId("test-game"),
                gameVersion = 1,
                messageId = "message-1",
                sequence = 1L,
            ),
            authoritativeRevision = 1L,
        )

        assertFailsWith<NoSuchElementException> {
            bus.fromHost(SendTarget.Direct(PlayerId("missing")), message)
        }
    }
}
