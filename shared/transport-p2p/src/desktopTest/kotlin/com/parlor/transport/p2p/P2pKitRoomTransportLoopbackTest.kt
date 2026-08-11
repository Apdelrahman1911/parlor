package com.parlor.transport.p2p

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
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
import dev.p2pkit.core.ExplicitSecurityRisk
import dev.p2pkit.core.PeerAuthorizationPolicy
import dev.p2pkit.core.P2pKit
import dev.p2pkit.core.SecurityMode
import dev.p2pkit.core.dsl.jvmSecureIdentityStore
import dev.p2pkit.core.security.JvmSecureIdentityStore
import dev.p2pkit.transport.lan.lan
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

/**
 * End-to-end loopback verification: two `P2pKitRoomTransport` instances
 * stand up in one JVM process, one hosts and one joins via LAN/loopback,
 * and the host↔peer protocol round-trips successfully.
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
 *    can both bind to. The end-to-end host↔peer round-trip is validated
 *    manually on two physical devices over real Wi-Fi (see
 *    `docs/P2P_MANUAL_TEST.md` runbook), which mirrors how friends-testing
 *    will actually happen.
 *
 * P2pKit is a required production dependency. This module and test are always
 * included and resolve the pinned publication from Maven Central.
 */
class P2pKitRoomTransportLoopbackTest {

    private val testDispatcher = Executors.newCachedThreadPool().asCoroutineDispatcher()
    private val testScope = CoroutineScope(testDispatcher + SupervisorJob())
    private val rooms: MutableList<LocalRoom> = mutableListOf()
    private val identityStore = LoopbackIdentityStore()

    @OptIn(ExplicitSecurityRisk::class)
    private val testKitFactory = object : P2pKitFactory {
        override suspend fun createKit(appId: AppId, deviceName: String) = P2pKit.create {
            this.appId = appId
            this.deviceName = deviceName
            transports { lan() }
            jvmSecureIdentityStore(identityStore)
            security {
                mode = SecurityMode.AuthenticatedV2(
                    PeerAuthorizationPolicy.AcceptAnyAuthenticatedSameApp,
                )
            }
        }
    }

    @AfterTest
    fun teardown() {
        runBlocking {
            rooms.forEach { runCatching { it.leave() } }
            rooms.clear()
            testScope.coroutineContext[Job]?.cancel()
        }
        testDispatcher.close()
    }

    @Test
    fun host_advertises_a_room_code_that_join_resolves_to() = runBlocking {
        // Use a unique appId per test run so concurrent test invocations
        // can't see each other's advertisements.
        val appId = AppId("com.parlor.p2p.test.${randomTag()}")

        val hostTransport = P2pKitRoomTransport(
            appId = appId,
            deviceName = "host-device",
            scope = testScope,
            kitFactory = testKitFactory,
        )
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

        val hostTransport = P2pKitRoomTransport(appId, "host-device", testScope, testKitFactory)
        val peerTransport = P2pKitRoomTransport(appId, "peer-alice", testScope, testKitFactory)

        val hostRoom = (
            withTimeout(15.seconds) {
                hostTransport.host(HostConfig(hostDisplayName = "Test Room"))
            } as Result.Success
            ).data
        rooms += hostRoom
        val code = hostRoom.info.value.code

        val peerRoom = (
            withTimeout(30.seconds) { peerTransport.join(code, "peer-alice") } as Result.Success
            ).data
        rooms += peerRoom
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
    fun peer_to_host_message_round_trips_and_host_to_peer_message_arrives_back() = runBlocking {
        val appId = AppId("com.parlor.p2p.test.${randomTag()}")

        val hostTransport = P2pKitRoomTransport(appId, "host-device", testScope, testKitFactory)
        val peerTransport = P2pKitRoomTransport(appId, "peer-alice", testScope, testKitFactory)

        val hostRoom = (
            withTimeout(15.seconds) {
                hostTransport.host(HostConfig(hostDisplayName = "Test Room"))
            } as Result.Success
            ).data
        rooms += hostRoom
        val code = hostRoom.info.value.code

        val peerRoom = (
            withTimeout(30.seconds) { peerTransport.join(code, "peer-alice") } as Result.Success
            ).data
        rooms += peerRoom

        // Wait for host membership to be observed (the host's accept-loop
        // needs to have processed the inbound session before its outbound
        // send can find the session in its lookup map).
        val joinedPlayerId = withTimeout(10.seconds) {
            hostRoom.members.first { it.isNotEmpty() }.first().playerId
        }

        // Peer → Host: send a current gameplay heartbeat; the host observes
        // the authenticated connection identity rather than the forged body.
        val hostInbox = async {
            withTimeout(10.seconds) { hostRoom.incoming.first() }
        }
        val peerHeartbeat = loopbackPeerHeartbeat(PlayerId("forged"))
        peerRoom.sendToHost(peerHeartbeat)
        val received = hostInbox.await()
        assertThat(received).isInstanceOf(PeerMessage.SessionHeartbeat::class)
        assertThat((received as PeerMessage.SessionHeartbeat).actor).isEqualTo(joinedPlayerId)
        assertThat(received.header).isEqualTo(peerHeartbeat.header)

        // Host → Peer (direct): send the current terminal envelope.
        val peerInbox = async {
            withTimeout(10.seconds) { peerRoom.incoming.first() }
        }
        hostRoom.send(SendTarget.Direct(joinedPlayerId), loopbackTerminal())
        val hostMsg = peerInbox.await()
        assertThat(hostMsg).isInstanceOf(HostMessage.SessionEnded::class)
    }

    @Test
    @Ignore("Needs three physical devices on the same LAN; mDNS multicast on " +
        "single-JVM loopback is unreliable. Run docs/P2P_MANUAL_TEST.md.")
    fun host_broadcast_reaches_every_peer() = runBlocking {
        val appId = AppId("com.parlor.p2p.test.${randomTag()}")

        val hostTransport = P2pKitRoomTransport(appId, "host-device", testScope, testKitFactory)
        val peer1Transport = P2pKitRoomTransport(appId, "peer-1", testScope, testKitFactory)
        val peer2Transport = P2pKitRoomTransport(appId, "peer-2", testScope, testKitFactory)

        val hostRoom = (
            withTimeout(15.seconds) {
                hostTransport.host(HostConfig(hostDisplayName = "Test Room"))
            } as Result.Success
            ).data
        rooms += hostRoom
        val code = hostRoom.info.value.code

        val peer1Room = (
            withTimeout(30.seconds) { peer1Transport.join(code, "peer-1") } as Result.Success
            ).data
        rooms += peer1Room
        val peer2Room = (
            withTimeout(30.seconds) { peer2Transport.join(code, "peer-2") } as Result.Success
            ).data
        rooms += peer2Room

        // Wait until both peers are known to the host.
        withTimeout(10.seconds) {
            hostRoom.members.first { it.size >= 2 }
        }

        // Broadcast a current terminal frame; both peers receive it.
        val peer1Inbox = async {
            withTimeout(10.seconds) { peer1Room.incoming.first() }
        }
        val peer2Inbox = async {
            withTimeout(10.seconds) { peer2Room.incoming.first() }
        }
        hostRoom.send(SendTarget.Broadcast, loopbackTerminal())
        assertThat(peer1Inbox.await()).isInstanceOf(HostMessage.SessionEnded::class)
        assertThat(peer2Inbox.await()).isInstanceOf(HostMessage.SessionEnded::class)
    }

    private fun randomTag(): String =
        (1..6).map { ('a'..'z').random() }.joinToString("")
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

private class LoopbackIdentityStore : JvmSecureIdentityStore {
    private val values = mutableMapOf<String, ByteArray>()

    override fun read(namespace: String): ByteArray? =
        synchronized(values) { values[namespace]?.copyOf() }

    override fun putIfAbsent(namespace: String, value: ByteArray): ByteArray =
        synchronized(values) {
            values.getOrPut(namespace) { value.copyOf() }.copyOf()
        }

    override fun delete(namespace: String): Boolean =
        synchronized(values) { values.remove(namespace) != null }
}
