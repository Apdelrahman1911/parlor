package com.parlor.games.whodunit.multidevice

import com.parlor.core.ids.GameId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.games.whodunit.domain.action.WhodunitAction
import com.parlor.games.whodunit.domain.action.WhodunitActionCodec
import com.parlor.networking.protocol.CommandStatus
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.PeerMessage
import com.parlor.networking.protocol.ProtocolVersion
import com.parlor.networking.protocol.SessionEnvelopeHeader
import com.parlor.networking.protocol.SessionProtocol
import com.parlor.networking.testing.InMemoryHostRoom
import com.parlor.networking.testing.InMemoryRoomBus
import com.parlor.session.multidevice.CommandApplication
import com.parlor.session.multidevice.HostAuthoritativeSessionCoordinator
import com.parlor.session.multidevice.PlayerSnapshotPayload
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Real coordinator + game decoder, synthetic authenticated room (not LAN evidence). */
@OptIn(ExperimentalCoroutinesApi::class)
class HostMalformedJsonRecoveryTest {
    @Test
    fun malformedNestedActionCannotKillTheHostMailboxOrBlockTheNextAction() = runTest {
        val peer = PlayerId("peer")
        val bus = InMemoryRoomBus().also { it.registerPeer(peer) }
        val protocol = SessionProtocol(SessionId("session-json-test"), GameId("whodunit"), 1)
        val outcomes = mutableListOf<CommandStatus>()
        backgroundScope.launch {
            bus.peerMessagesIn(peer).collect { if (it is HostMessage.CommandResult) outcomes += it.status }
        }
        var applied = 0
        val coordinator = HostAuthoritativeSessionCoordinator(
            room = InMemoryHostRoom(bus, PlayerId("host"), "Host"),
            protocol = protocol,
            remotePlayers = setOf(peer),
            scope = backgroundScope,
            applyCommand = { _, payload ->
                try {
                    WhodunitActionCodec.decode(payload)
                    applied++
                    CommandApplication.Applied
                } catch (_: IllegalArgumentException) {
                    CommandApplication.InvalidAction
                }
            },
            snapshotFor = { PlayerSnapshotPayload(byteArrayOf(), byteArrayOf()) },
            heartbeatIntervalMs = 0,
            requireStartHandshake = false,
        )
        try {
            val malformed = ("[".repeat(4_096) + "0" + "]".repeat(4_096)).encodeToByteArray()
            bus.fromPeer(command(protocol, peer, 1, malformed))
            runCurrent()
            bus.fromPeer(command(protocol, peer, 2, WhodunitActionCodec.encode(WhodunitAction.Pause)))
            runCurrent()
            assertEquals(listOf(CommandStatus.InvalidAction, CommandStatus.Applied), outcomes)
            assertEquals(1, applied)
            assertEquals(1L, coordinator.revision.value)
        } finally {
            coordinator.close()
        }
    }

    private fun command(protocol: SessionProtocol, peer: PlayerId, sequence: Long, payload: ByteArray): PeerMessage {
        val id = "json-command-$sequence"
        return PeerMessage.ClientCommand(
            header = SessionEnvelopeHeader(
                protocol = ProtocolVersion(), sessionId = protocol.sessionId,
                gameId = protocol.gameId, gameVersion = protocol.gameVersion,
                messageId = id, sequence = 0,
            ),
            actor = peer, commandId = id, clientSequence = sequence, expectedRevision = 0, payload = payload,
        )
    }
}
