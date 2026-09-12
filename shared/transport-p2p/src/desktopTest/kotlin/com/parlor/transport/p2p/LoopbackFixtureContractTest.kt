package com.parlor.transport.p2p

import com.parlor.core.ids.GameId
import com.parlor.core.ids.PlayerId
import com.parlor.core.result.Result
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.networking.room.PendingAdmission
import com.parlor.networking.transport.HostConfig
import com.parlor.networking.transport.HostedGameProtocol
import com.parlor.networking.transport.ResumableSessionInfo
import dev.p2pkit.core.AppId
import dev.p2pkit.core.ConnectionState
import dev.p2pkit.core.P2pKit
import dev.p2pkit.core.P2pMessage
import dev.p2pkit.core.P2pSession
import dev.p2pkit.core.Peer
import dev.p2pkit.core.PeerId
import dev.p2pkit.core.Platform
import dev.p2pkit.core.TransportKind
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Production adapter + codec + credential transactions, but fake SDK sessions and no LAN operations. */
@OptIn(ExperimentalCoroutinesApi::class)
class LoopbackFixtureContractTest {
    @Test
    fun device_stores_are_isolated_retained_and_defensively_copied(): Unit = runTest {
        val first = LoopbackDeviceFixture(StandardTestDispatcher(testScheduler))
        val second = LoopbackDeviceFixture(StandardTestDispatcher(testScheduler))
        val bytes = byteArrayOf(1, 2, 3)
        val namespace = "synthetic-same-app-namespace"
        val winner = first.identityStore.putIfAbsent(namespace, bytes)
        bytes.fill(0)
        winner.fill(0)
        assertContentEquals(byteArrayOf(1, 2, 3), first.identityStore.read(namespace))
        assertNull(second.identityStore.read(namespace))
        assertContentEquals(byteArrayOf(1, 2, 3), first.identityStore.putIfAbsent(namespace, byteArrayOf(4)))

        assertIs<Result.Success<Unit>>(first.secureStorage.put("synthetic-credential", byteArrayOf(5)))
        assertContentEquals(byteArrayOf(5), assertIs<Result.Success<ByteArray?>>(
            first.secureStorage.get("synthetic-credential"),
        ).data)
        assertNull(assertIs<Result.Success<ByteArray?>>(second.secureStorage.get("synthetic-credential")).data)
        assertTrue(first.identityStore.delete(namespace))
        assertNull(first.identityStore.read(namespace))
    }

    @Test
    fun join_without_an_approval_owner_remains_pending_even_with_working_fake_delivery(): Unit = runTest {
        val network = Network(this)
        try {
            val host = network.host()
            val peer = network.peer(host, "alice")
            val joining = async { peer.transport.join(host.info.value.code, "alice") }
            try {
                runCurrent()
                assertFalse(joining.isCompleted)
                assertEquals(listOf("alice"), host.pendingAdmissions.value.map { it.displayName })
                assertTrue(host.members.value.isEmpty())
            } finally {
                joining.cancelAndJoin()
            }
            assertEquals(1, peer.kit.stopCalls)
        } finally {
            network.close()
        }
    }

    @Test
    fun shared_fixture_approves_distinct_peers_and_commits_each_devices_credentials(): Unit = runTest {
        val network = Network(this)
        try {
            val host = network.host()
            val approved = mutableListOf<PlayerId>()
            val approvingHost = object : LocalRoom by host {
                override suspend fun approveAdmission(playerId: PlayerId): Result<Unit, NetError> {
                    assertTrue(host.members.value.none { it.playerId == playerId })
                    approved += playerId
                    return host.approveAdmission(playerId)
                }
            }
            val peers = listOf("alice", "bob").map { name ->
                val device = network.peer(host, name)
                val room = joinApprovedLoopbackPeer(approvingHost, device.transport, name, network::own)
                val membership = requireNotNull(assertIs<Result.Success<ResumableSessionInfo?>>(
                    device.transport.resumableSession(),
                ).data)
                assertEquals(GameId("loopback-test-game"), membership.gameId)
                assertEquals(name, membership.displayName)
                // Capabilities remain inside the device store, not the public room/UI contract.
                assertNull(room.rejoinToken)
                room
            }
            assertEquals(peers.map { it.selfPlayerId }, approved)
            assertEquals(3, (peers.map { it.selfPlayerId } + host.selfPlayerId).toSet().size)
            assertEquals(peers.map { it.selfPlayerId }.toSet(), host.members.value.map { it.playerId }.toSet())
            assertTrue(host.pendingAdmissions.value.isEmpty())
        } finally {
            network.close()
        }
        assertTrue(network.kits.all { it.stopCalls == 1 })
    }

    @Test
    fun failed_join_cancels_the_pending_approval_collector(): Unit = runTest {
        val network = Network(this)
        try {
            val host = network.host()
            val peer = network.peer(host, "alice")
            peer.kit.startDiscoveryHandler = { error("synthetic discovery failure") }
            val requests = MutableStateFlow(emptyList<PendingAdmission>())
            val observedHost = object : LocalRoom by host {
                override val pendingAdmissions = requests
            }
            assertFailsWith<IllegalStateException> {
                joinApprovedLoopbackPeer(observedHost, peer.transport, "alice", network::own)
            }
            assertEquals(0, requests.subscriptionCount.value)
            assertEquals(1, peer.kit.stopCalls)
        } finally {
            network.close()
        }
    }

    @Test
    fun caller_cancellation_stops_join_and_approval_without_claiming_room_ownership(): Unit = runTest {
        val network = Network(this)
        try {
            val host = network.host()
            val peer = network.peer(host, "alice")
            val approving = CompletableDeferred<Unit>()
            val suspendedHost = object : LocalRoom by host {
                override suspend fun approveAdmission(playerId: PlayerId): Result<Unit, NetError> {
                    approving.complete(Unit)
                    awaitCancellation()
                }
            }
            val joining = async {
                joinApprovedLoopbackPeer(suspendedHost, peer.transport, "alice") {
                    network.own(it)
                    error("Cancelled join must not return an owned room")
                }
            }
            approving.await()
            joining.cancelAndJoin()
            assertTrue(joining.isCancelled)
            assertEquals(1, peer.kit.stopCalls)
        } finally {
            network.close()
        }
    }

    private class Network(private val scope: TestScope) {
        private val appId = AppId("com.parlor.synthetic.loopback")
        val kits = mutableListOf<FakeP2pKit>()
        private val rooms = mutableListOf<LocalRoom>()
        private val hostKit = FakeP2pKit(PeerId("fixture-host")).also(kits::add)

        fun own(room: LocalRoom) { rooms += room }

        suspend fun host(): LocalRoom = assertIs<Result.Success<LocalRoom>>(
            device(hostKit).transport(appId, "host", scope.backgroundScope).host(
                HostConfig("Fixture Host", gameProtocol = HostedGameProtocol(GameId("loopback-test-game"), 1)),
            ),
        ).data.also(::own)

        fun peer(host: LocalRoom, name: String): Device {
            val kit = FakeP2pKit(PeerId("fixture-$name")).also(kits::add)
            val hostPeer = peerInfo(PeerId(host.selfPlayerId.raw), "${P2pKitRoomTransport.P2P_ROOM_PREFIX}Host")
            val hostSide = FakeP2pSession(peerInfo(kit.localPeerId, name))
            val peerSide = FakeP2pSession(hostPeer)
            val toHost = linkedEndpoint(peerSide, hostSide)
            val toPeer = linkedEndpoint(hostSide, peerSide)
            kit.peersFlow.value = listOf(hostPeer)
            kit.connectHandler = {
                hostKit.incomingSessionsFlow.emit(toPeer)
                hostSide.incomingFlow.subscriptionCount.first { it > 0 }
                toHost
            }
            return Device(kit, device(kit).transport(appId, name, scope.backgroundScope))
        }

        suspend fun close() {
            withContext<Unit>(NonCancellable) {
                val failures = rooms.asReversed().mapNotNull { cleanupFailure { it.leave() } } +
                    kits.filter { it.stopCalls == 0 }.mapNotNull { cleanupFailure { it.stop() } }
                // Fakes own no native resources; all rooms and never-returned kits are still closed on red paths.
                failures.firstOrNull()?.let { first ->
                    failures.drop(1).filter { it !== first }.forEach(first::addSuppressed)
                    throw first
                }
            }
        }

        private suspend fun cleanupFailure(cleanup: suspend () -> Unit): Throwable? =
            runCatching { cleanup() }.exceptionOrNull()

        private fun device(kit: FakeP2pKit): LoopbackDeviceFixture = LoopbackDeviceFixture(
            StandardTestDispatcher(scope.testScheduler),
            object : P2pKitFactory {
                override suspend fun createKit(appId: AppId, deviceName: String): P2pKit = kit
            },
        )
    }

    private data class Device(val kit: FakeP2pKit, val transport: P2pKitRoomTransport)

    private companion object {
        fun peerInfo(id: PeerId, name: String): Peer = Peer(
            id = id,
            name = name,
            platform = Platform.UNKNOWN,
            supportedTransports = setOf(TransportKind.LAN),
        )

        // Override fake auto-ACKs: only the real opposing adapter may generate handshake frames.
        fun linkedEndpoint(local: FakeP2pSession, remote: FakeP2pSession): P2pSession = object : P2pSession by local {
            override suspend fun send(message: P2pMessage) {
                check(local.stateFlow.value == ConnectionState.Connected)
                check(remote.stateFlow.value == ConnectionState.Connected)
                check(remote.incomingFlow.subscriptionCount.value > 0) { "Synthetic receiver is not armed" }
                local.sent += message
                remote.incomingFlow.emit(message)
            }

            override suspend fun close() {
                local.close()
                remote.stateFlow.value = ConnectionState.Closed
            }
        }
    }
}
