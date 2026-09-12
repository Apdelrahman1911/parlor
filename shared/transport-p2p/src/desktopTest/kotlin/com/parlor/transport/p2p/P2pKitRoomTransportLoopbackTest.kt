package com.parlor.transport.p2p

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import com.parlor.core.ids.GameId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.core.result.Result
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.PeerMessage
import com.parlor.networking.protocol.ProtocolVersion
import com.parlor.networking.protocol.SessionEnvelopeHeader
import com.parlor.networking.protocol.SessionEndReason
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.RoomInfo
import com.parlor.networking.room.SendTarget
import com.parlor.networking.transport.HostConfig
import dev.p2pkit.core.AppId
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Real-kit advertising smoke test plus disabled multi-kit LAN transport fixtures.
 * Peer fixtures give each simulated device separate identity/credential storage,
 * explicitly approve admission, then assert membership and envelope delivery.
 * They do not exercise game reducers or the session-start transaction.
 *
 * P2pKit is wired into the production mobile dependency graph; this JVM test
 * independently exercises the same adapter boundary with a real LAN kit.
 *
 * Discovery scope:
 *  - The `host_*` test runs in CI/locally. It verifies the adapter brings
 *    a real P2pKit instance up, starts advertising, and surfaces the
 *    room code through Parlor's `RoomInfo` contract.
 *  - The peer-side tests are `@Ignore`d in this single-JVM environment
 *    because JmDNS multicast on Windows loopback inside one JVM process is
 *    unreliable — two P2pKit instances need a real network interface they
 *    can both bind to. These ignored fixtures are not physical-device evidence.
 *    End-to-end Android/iOS checks still require fresh controlled-device
 *    execution of `docs/P2P_MANUAL_TEST.md`; no historical success is asserted.
 *
 * P2pKit is a required production dependency. This module and test are always
 * included and resolve the pinned publication from Maven Central.
 */
class P2pKitRoomTransportLoopbackTest {

    private val testDispatcher = Executors.newCachedThreadPool().asCoroutineDispatcher()
    private val testScope = CoroutineScope(testDispatcher + SupervisorJob())
    private val rooms: MutableList<LocalRoom> = mutableListOf()

    @AfterTest
    fun teardown() {
        try {
            runBlocking {
                val failures = rooms.asReversed().mapNotNull { runCatching { it.leave() }.exceptionOrNull() }
                testScope.coroutineContext[Job]?.cancelAndJoin()
                failures.firstOrNull()?.let { first ->
                    failures.drop(1).filter { it !== first }.forEach(first::addSuppressed)
                    throw first
                }
            }
        } finally {
            rooms.clear()
            testDispatcher.close()
        }
    }

    @Test
    fun host_advertises_a_room_code_that_join_resolves_to() = runBlocking {
        // Use a unique appId per test run so concurrent test invocations
        // can't see each other's advertisements.
        val appId = AppId("com.parlor.p2p.test.${randomTag()}")

        val hostTransport = deviceTransport(appId, "host-device")
        val hostResult = withTimeout(15.seconds) {
            hostTransport.host(HostConfig(hostDisplayName = "Test Room"))
        }
        assertThat(hostResult).isInstanceOf(Result.Success::class)
        val hostRoom = (hostResult as Result.Success).data
        rooms += hostRoom

        val roomCode = hostRoom.info.value.code
        assertThat(roomCode.length).isEqualTo(6)
        assertThat(hostRoom.info.value.status).isEqualTo(RoomInfo.Status.Hosting)
        assertThat(hostRoom.isHost).isTrue()
    }

    @Test
    @Ignore("Needs two physical devices on the same LAN; mDNS multicast on " +
        "single-JVM loopback is unreliable. Run docs/P2P_MANUAL_TEST.md.")
    fun peer_can_join_a_hosted_room_and_membership_appears_on_host() = runBlocking {
        val appId = AppId("com.parlor.p2p.test.${randomTag()}")

        val hostTransport = deviceTransport(appId, "host-device")
        val peerTransport = deviceTransport(appId, "peer-alice")

        val hostRoom = (
            withTimeout(15.seconds) {
                hostTransport.host(HostConfig(hostDisplayName = "Test Room"))
            } as Result.Success
            ).data
        rooms += hostRoom
        val peerRoom = joinApprovedLoopbackPeer(hostRoom, peerTransport, "peer-alice", rooms::add)
        assertThat(peerRoom.isHost).isEqualTo(false)
        assertThat(peerRoom.info.value.status).isEqualTo(RoomInfo.Status.Joined)

        // Host membership eventually includes the peer.
        val firstMember = withTimeout(10.seconds) {
            hostRoom.members.first { it.isNotEmpty() }.first()
        }
        assertThat(firstMember.displayName).isEqualTo("peer-alice")
        assertThat(firstMember.connected).isTrue()
    }

    @Test
    @Ignore("Needs two physical devices on the same LAN; mDNS multicast on " +
        "single-JVM loopback is unreliable. Run docs/P2P_MANUAL_TEST.md.")
    fun peer_to_host_message_round_trips_and_host_to_peer_message_arrives_back(): Unit = runBlocking {
        val appId = AppId("com.parlor.p2p.test.${randomTag()}")

        val hostTransport = deviceTransport(appId, "host-device")
        val peerTransport = deviceTransport(appId, "peer-alice")

        val hostRoom = (
            withTimeout(15.seconds) {
                hostTransport.host(HostConfig(hostDisplayName = "Test Room"))
            } as Result.Success
            ).data
        rooms += hostRoom
        val peerRoom = joinApprovedLoopbackPeer(hostRoom, peerTransport, "peer-alice", rooms::add)

        // Wait for host membership to be observed (the host's accept-loop
        // needs to have processed the inbound session before its outbound
        // send can find the session in its lookup map).
        val joinedPlayerId = withTimeout(10.seconds) {
            hostRoom.members.first { it.isNotEmpty() }.first().playerId
        }

        // Peer → Host: send a current gameplay heartbeat; the host observes
        // the authenticated connection identity rather than the forged body.
        val hostInbox = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(10.seconds) { hostRoom.incoming.first() }
        }
        val peerHeartbeat = loopbackPeerHeartbeat(PlayerId("forged"))
        assertThat(peerRoom.sendToHost(peerHeartbeat)).isInstanceOf(Result.Success::class)
        val received = hostInbox.await()
        assertThat(received).isInstanceOf(PeerMessage.SessionHeartbeat::class)
        assertThat((received as PeerMessage.SessionHeartbeat).actor).isEqualTo(joinedPlayerId)
        assertThat(received.header).isEqualTo(peerHeartbeat.header)

        // Host → Peer (direct): send the current terminal envelope.
        val peerInbox = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(10.seconds) { peerRoom.incoming.first() }
        }
        assertThat(hostRoom.send(SendTarget.Direct(joinedPlayerId), loopbackTerminal()))
            .isInstanceOf(Result.Success::class)
        val hostMsg = peerInbox.await()
        assertThat(hostMsg).isInstanceOf(HostMessage.SessionEnded::class)
    }

    @Test
    @Ignore("Needs three physical devices on the same LAN; mDNS multicast on " +
        "single-JVM loopback is unreliable. Run docs/P2P_MANUAL_TEST.md.")
    fun host_broadcast_reaches_every_peer(): Unit = runBlocking {
        val appId = AppId("com.parlor.p2p.test.${randomTag()}")

        val hostTransport = deviceTransport(appId, "host-device")
        val peer1Transport = deviceTransport(appId, "peer-1")
        val peer2Transport = deviceTransport(appId, "peer-2")

        val hostRoom = (
            withTimeout(15.seconds) {
                hostTransport.host(HostConfig(hostDisplayName = "Test Room"))
            } as Result.Success
            ).data
        rooms += hostRoom
        val peer1Room = joinApprovedLoopbackPeer(hostRoom, peer1Transport, "peer-1", rooms::add)
        val peer2Room = joinApprovedLoopbackPeer(hostRoom, peer2Transport, "peer-2", rooms::add)
        assertThat(setOf(hostRoom.selfPlayerId, peer1Room.selfPlayerId, peer2Room.selfPlayerId).size)
            .isEqualTo(3)

        // Wait until both peers are known to the host.
        withTimeout(10.seconds) {
            hostRoom.members.first { it.size >= 2 }
        }

        // Broadcast a current terminal frame; both peers receive it.
        val peer1Inbox = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(10.seconds) { peer1Room.incoming.first() }
        }
        val peer2Inbox = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(10.seconds) { peer2Room.incoming.first() }
        }
        assertThat(hostRoom.send(SendTarget.Broadcast, loopbackTerminal())).isInstanceOf(Result.Success::class)
        assertThat(peer1Inbox.await()).isInstanceOf(HostMessage.SessionEnded::class)
        assertThat(peer2Inbox.await()).isInstanceOf(HostMessage.SessionEnded::class)
    }

    private fun randomTag(): String =
        (1..6).map { ('a'..'z').random() }.joinToString("")

    private fun deviceTransport(appId: AppId, deviceName: String): P2pKitRoomTransport =
        LoopbackDeviceFixture(testDispatcher).transport(appId, deviceName, testScope)
}

private fun loopbackHeader(sequence: Long, messageId: String): SessionEnvelopeHeader =
    SessionEnvelopeHeader(
        protocol = ProtocolVersion(),
        sessionId = SessionId("loopback-test-session"),
        gameId = GameId("loopback-test-game"),
        gameVersion = 1,
        messageId = messageId,
        sequence = sequence,
    )

private fun loopbackPeerHeartbeat(actor: PlayerId): PeerMessage.SessionHeartbeat =
    PeerMessage.SessionHeartbeat(
        header = loopbackHeader(0L, "loopback-peer-heartbeat-0001"),
        actor = actor,
        lastAppliedRevision = 0L,
    )

private fun loopbackTerminal(): HostMessage.SessionEnded = HostMessage.SessionEnded(
    header = loopbackHeader(1L, "loopback-host-terminal-00001"),
    reason = SessionEndReason.Cancelled,
    finalRevision = 0L,
)
