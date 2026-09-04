package com.parlor.transport.p2p

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import com.parlor.core.ids.GameId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.core.result.DataError
import com.parlor.core.result.EmptyResult
import com.parlor.core.result.Result
import com.parlor.networking.protocol.AdmissionRejection
import com.parlor.networking.protocol.CommandStatus
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.MAX_COMMAND_PAYLOAD_BYTES
import com.parlor.networking.protocol.PeerMessage
import com.parlor.networking.protocol.ProtocolVersion
import com.parlor.networking.protocol.RoomMessage
import com.parlor.networking.protocol.RoomMessageCodec
import com.parlor.networking.protocol.SessionEnvelopeHeader
import com.parlor.networking.protocol.SessionEndReason
import com.parlor.networking.room.NetError
import com.parlor.networking.room.PeerEvent
import com.parlor.networking.room.RoomInfo
import com.parlor.networking.room.RoomLifecycleState
import com.parlor.networking.room.RoomMember
import com.parlor.networking.room.SendTarget
import com.parlor.networking.room.SessionEndCommitStatus
import com.parlor.networking.transport.LocalNetworkAccess
import com.parlor.networking.transport.HostConfig
import dev.p2pkit.core.AppId
import dev.p2pkit.core.ConnectionState
import dev.p2pkit.core.P2pKit
import dev.p2pkit.core.P2pError
import dev.p2pkit.core.P2pMessage
import dev.p2pkit.core.P2pSession
import dev.p2pkit.core.P2pState
import dev.p2pkit.core.Peer
import dev.p2pkit.core.PeerFingerprint
import dev.p2pkit.core.PeerIdentity
import dev.p2pkit.core.PeerId as P2pPeerId
import dev.p2pkit.core.NetworkPathStatus
import dev.p2pkit.core.Platform
import dev.p2pkit.core.TransportKind
import dev.p2pkit.core.permission.P2pPermissionManager
import dev.p2pkit.core.permission.P2pPermission
import dev.p2pkit.core.provisioning.NetworkProvisioningManager
import dev.p2pkit.core.transfer.P2pFileOffer
import dev.p2pkit.core.transfer.P2pFileTransfer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import kotlinx.io.RawSource
import com.parlor.storage.secure.InMemorySecureKeyValueBacking
import com.parlor.storage.secure.PlatformKeyedSecureStorage
import com.parlor.storage.secure.SecureStorage
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds

/**
 * Lifecycle regression tests for [HostP2pRoom] / [PeerP2pRoom].
 *
 * These exercise the production P2pKit adapter directly with hand-rolled
 * [P2pKit] / [P2pSession] fakes whose `state` and `incomingSessions`
 * flows are driven deterministically by the test. They pin the
 * contracts the host-lobby UI and game-side host/peer bridges depend on:
 *
 *  - **Host membership** (`room.members`) reflects connected peers and
 *    removes them when their sessions terminate.
 *  - **PeerEvent.PeerJoined** fires on a brand-new incoming session.
 *  - **PeerEvent.PeerLeft** fires when a session terminates (Closed /
 *    Failed) and the corresponding member is removed.
 *  - **PeerEvent.PeerReconnected** fires either on a session walk
 *    `Connected → Reconnecting → Connected` or on a fresh incoming
 *    session for a previously-known `PlayerId` (post-Closed re-join).
 *  - **PeerP2pRoom** emits **HostLost** on session
 *    `Reconnecting` / `Failed` / `Closed` and **HostRestored** on the
 *    recovery `Connected` after a prior loss.
 *  - **send** returns `NetError.NotConnected` once the underlying
 *    session is gone, instead of silently dropping bytes.
 */
class P2pKitRoomTransportLifecycleTest {

    // Unconfined so child coroutines start their `collect` blocks synchronously
    // — `kit.incomingSessions` is a SharedFlow with replay=0, and emissions made
    // before the production-side collector subscribes are otherwise lost.
    @Suppress("InjectDispatcher") // Replay-zero fake emissions require eager test-only collection startup.
    private val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val codec = RoomMessageCodec()

    @AfterTest
    fun cancelScope() {
        testScope.coroutineContext[Job]?.cancel()
    }

    // ---------------------------------------------------------------- Host ----

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun transport_arms_the_host_session_acceptor_before_advertising() = runTest {
        val kit = FakeP2pKit(P2pPeerId("host-pid"))
        var subscribersAtAdvertising = -1
        kit.startAdvertisingHandler = {
            subscribersAtAdvertising = kit.incomingSessionsFlow.subscriptionCount.value
        }
        val transport = P2pKitRoomTransport(
            appId = AppId("com.parlor.test"),
            deviceName = "host-device",
            scope = backgroundScope,
            kitFactory = object : P2pKitFactory {
                override suspend fun createKit(appId: AppId, deviceName: String): P2pKit = kit
            },
        )

        val hosted = transport.host(HostConfig("Host"))

        assertThat(hosted).isInstanceOf(Result.Success::class)
        assertThat(subscribersAtAdvertising).isEqualTo(1)
        (hosted as Result.Success).data.leave()
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun host_arms_a_new_sessions_inbound_collector_before_accepting_another_session() = runTest {
        val alice = FakeP2pSession(peer("alice-pid", "Alice"))
        val incomingSessions = MutableSharedFlow<P2pSession>(replay = 1)
        incomingSessions.emit(alice)
        val kit = FakeP2pKit(P2pPeerId("host-pid"), incomingSessions)

        val room = HostP2pRoom(
            kit = kit,
            roomCode = "ABCDEF",
            hostDisplayName = "Host",
            hostPlayerId = PlayerId("host-pid"),
            maxRemotePlayers = 4,
            scope = backgroundScope,
            codec = codec,
        )

        assertThat(alice.incomingFlow.subscriptionCount.value).isEqualTo(1)
        room.leave()
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun peer_arms_its_host_message_collector_before_room_construction_returns() = runTest {
        val host = peer("host-pid", "Host")
        val kit = FakeP2pKit(P2pPeerId("peer-pid"))
        val session = FakeP2pSession(host)

        val room = PeerP2pRoom(
            kit = kit,
            session = session,
            hostPeer = host,
            roomCode = "ABCDEF",
            scope = backgroundScope,
            codec = codec,
            hostDisplayName = "Host",
        )

        assertThat(session.incomingFlow.subscriptionCount.value).isEqualTo(1)
        room.leave()
    }

    @Test
    fun host_emits_peer_joined_and_updates_members_when_session_arrives() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("host-pid"))
        val room = newHostRoom(kit)

        val events = mutableListOf<PeerEvent>()
        val collector = testScope.async {
            room.peerEvents.collect { events += it }
        }
        yield(); yield()

        val alice = FakeP2pSession(peer = peer("alice-pid", "Alice"))
        admit(room, kit, alice)
        awaitCondition { events.any { it is PeerEvent.PeerJoined } }

        assertThat(room.members.value).hasSize(1)
        assertThat(room.members.value.first()).isEqualTo(
            RoomMember(PlayerId("alice-pid"), "Alice", connected = true),
        )
        assertThat(events.filterIsInstance<PeerEvent.PeerJoined>().single().displayName)
            .isEqualTo("Alice")

        collector.cancel()
    }

    @Test
    fun host_does_not_publish_peer_joined_until_peer_inbound_collector_is_ready() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("host-pid"))
        val room = newHostRoom(kit)
        val events = mutableListOf<PeerEvent>()
        val collector = testScope.async { room.peerEvents.collect { events += it } }
        val alice = FakeP2pSession(peer("alice-pid", "Alice")).apply {
            autoAdmissionReady = false
        }
        requestAdmission(room, kit, alice)

        val approval = async { room.approveAdmission(PlayerId("alice-pid")) }
        awaitCondition {
            alice.sent.filterIsInstance<P2pMessage.Binary>().any {
                codec.decode(it.bytes) is HostMessage.AdmissionCommitted
            }
        }
        assertThat(events.filterIsInstance<PeerEvent.PeerJoined>()).isEmpty()
        val committed = alice.sent
            .filterIsInstance<P2pMessage.Binary>()
            .map { codec.decode(it.bytes) }
            .filterIsInstance<HostMessage.AdmissionCommitted>()
            .single()
        alice.incomingFlow.emit(
            P2pMessage.Binary(
                codec.encode(
                    PeerMessage.AdmissionReady(
                        actor = PlayerId("forged-body-id"),
                        offerId = committed.offerId,
                        generation = committed.generation,
                    ),
                ),
            ),
        )

        assertThat(approval.await()).isInstanceOf(Result.Success::class)
        awaitCondition { events.any { it is PeerEvent.PeerJoined } }
        collector.cancel()
    }

    @Test
    fun host_keeps_a_correct_code_request_pending_until_explicit_approval() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("host-pid"))
        val room = newHostRoom(kit)
        val alice = FakeP2pSession(peer("alice-pid", "Alice"))
        kit.incomingSessionsFlow.emit(alice)
        yield()
        alice.incomingFlow.emit(
            P2pMessage.Binary(
                codec.encode(
                    PeerMessage.AdmissionRequest(
                        protocol = ProtocolVersion(),
                        actor = PlayerId("forged-body-id"),
                        roomCode = "ABCDEF",
                        displayName = "Alice",
                    ),
                ),
            ),
        )

        awaitCondition { room.pendingAdmissions.value.size == 1 }
        assertThat(room.members.value).isEmpty()
        assertThat(
            alice.sent
                .filterIsInstance<P2pMessage.Binary>()
                .map { codec.decode(it.bytes) }
                .filterIsInstance<HostMessage.AdmissionPending>(),
        ).hasSize(1)

        assertThat(room.approveAdmission(PlayerId("alice-pid")))
            .isInstanceOf(Result.Success::class)
        awaitCondition { room.members.value.singleOrNull()?.connected == true }
        assertThat(room.pendingAdmissions.value).isEmpty()
    }

    @Test
    fun host_capacity_is_atomic_across_concurrent_approvals() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("host-pid"))
        val room = newHostRoom(kit, maxRemotePlayers = 1)
        val alice = FakeP2pSession(peer("alice-pid", "Alice"))
        val bob = FakeP2pSession(peer("bob-pid", "Bob"))
        requestAdmission(room, kit, alice)
        requestAdmission(room, kit, bob)

        val acceptanceStarted = CompletableDeferred<Unit>()
        val releaseAcceptance = CompletableDeferred<Unit>()
        alice.sendHandler = { message ->
            if (message.isAdmissionOffered()) {
                acceptanceStarted.complete(Unit)
                releaseAcceptance.await()
            }
        }

        val aliceApproval = testScope.async {
            room.approveAdmission(PlayerId("alice-pid"))
        }
        acceptanceStarted.await()

        val bobApproval = room.approveAdmission(PlayerId("bob-pid"))
        assertThat(bobApproval).isInstanceOf(Result.Failure::class)
        assertThat((bobApproval as Result.Failure).error).isEqualTo(NetError.RoomFull)
        assertThat(room.members.value).isEmpty()

        releaseAcceptance.complete(Unit)
        assertThat(aliceApproval.await()).isInstanceOf(Result.Success::class)
        assertThat(room.members.value.map(RoomMember::playerId))
            .containsExactly(PlayerId("alice-pid"))
        assertThat(bob.admissionRejections()).containsExactly(AdmissionRejection.RoomFull)
    }

    @Test
    fun host_atomically_reserves_exact_display_names_across_host_pending_and_member_seats() =
        runBlocking {
            val kit = FakeP2pKit(P2pPeerId("host-pid"))
            val room = newHostRoom(kit)

            val hostCollision = FakeP2pSession(peer("host-collision", "Parlor Room"))
            emitAdmissionRequest(kit, hostCollision)
            awaitCondition { hostCollision.admissionRejections().isNotEmpty() }
            assertThat(hostCollision.admissionRejections())
                .containsExactly(AdmissionRejection.DisplayNameInUse)
            assertThat(room.pendingAdmissions.value).isEmpty()

            val alice = FakeP2pSession(peer("alice-pid", "Alice"))
            requestAdmission(room, kit, alice)
            val pendingCollision = FakeP2pSession(peer("alice-copy-pid", "Alice"))
            emitAdmissionRequest(kit, pendingCollision)
            awaitCondition { pendingCollision.admissionRejections().isNotEmpty() }
            assertThat(pendingCollision.admissionRejections())
                .containsExactly(AdmissionRejection.DisplayNameInUse)
            assertThat(room.pendingAdmissions.value.map { it.playerId })
                .containsExactly(PlayerId("alice-pid"))

            assertThat(room.approveAdmission(PlayerId("alice-pid")))
                .isInstanceOf(Result.Success::class)
            awaitCondition { room.members.value.singleOrNull()?.connected == true }

            val memberCollision = FakeP2pSession(peer("alice-copy-2-pid", "Alice"))
            emitAdmissionRequest(kit, memberCollision)
            awaitCondition { memberCollision.admissionRejections().isNotEmpty() }
            assertThat(memberCollision.admissionRejections())
                .containsExactly(AdmissionRejection.DisplayNameInUse)

            alice.stateFlow.value = ConnectionState.Closed
            awaitCondition { room.members.value.singleOrNull()?.connected == false }
            val disconnectedCollision = FakeP2pSession(
                peer("alice-copy-3-pid", "Alice"),
            )
            emitAdmissionRequest(kit, disconnectedCollision)
            awaitCondition { disconnectedCollision.admissionRejections().isNotEmpty() }
            assertThat(disconnectedCollision.admissionRejections())
                .containsExactly(AdmissionRejection.DisplayNameInUse)

            val caseVariant = FakeP2pSession(peer("lower-alice-pid", "alice"))
            requestAdmission(room, kit, caseVariant)
            assertThat(room.pendingAdmissions.value.map { it.playerId })
                .containsExactly(PlayerId("lower-alice-pid"))
        }

    @Test
    fun simultaneous_same_name_requests_create_only_one_pending_seat() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("host-pid"))
        val room = newHostRoom(kit)
        val first = FakeP2pSession(peer("first-pid", "Same Name"))
        val second = FakeP2pSession(peer("second-pid", "Same Name"))

        attachSession(kit, first)
        attachSession(kit, second)
        val firstRequest = testScope.async { sendAdmissionRequest(first) }
        val secondRequest = testScope.async { sendAdmissionRequest(second) }
        firstRequest.await()
        secondRequest.await()
        awaitCondition {
            room.pendingAdmissions.value.size == 1 &&
                first.admissionRejections().size + second.admissionRejections().size == 1
        }

        assertThat(room.pendingAdmissions.value.single().displayName).isEqualTo("Same Name")
        assertThat(first.admissionRejections() + second.admissionRejections())
            .containsExactly(AdmissionRejection.DisplayNameInUse)
    }

    @Test
    fun failed_acceptance_delivery_rolls_back_the_seat_without_a_ghost_member() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("host-pid"))
        val room = newHostRoom(kit, maxRemotePlayers = 1)
        val alice = FakeP2pSession(peer("alice-pid", "Alice"))
        requestAdmission(room, kit, alice)
        alice.sendHandler = { message ->
            if (message.isAdmissionOffered()) error("injected send failure")
        }

        val failure = room.approveAdmission(PlayerId("alice-pid"))
        assertThat(failure).isInstanceOf(Result.Failure::class)
        assertThat((failure as Result.Failure).error)
            .isEqualTo(NetError.TransportFailure("admission offer failed"))
        assertThat(room.members.value).isEmpty()
        assertThat(room.pendingAdmissions.value).isEmpty()

        val bob = FakeP2pSession(peer("bob-pid", "Bob"))
        requestAdmission(room, kit, bob)
        assertThat(room.approveAdmission(PlayerId("bob-pid")))
            .isInstanceOf(Result.Success::class)
        assertThat(room.members.value.map(RoomMember::playerId))
            .containsExactly(PlayerId("bob-pid"))
    }

    @Test
    fun disconnect_during_acceptance_rolls_back_the_seat_without_a_ghost_member() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("host-pid"))
        val room = newHostRoom(kit, maxRemotePlayers = 1)
        val alice = FakeP2pSession(peer("alice-pid", "Alice"))
        requestAdmission(room, kit, alice)
        alice.sendHandler = { message ->
            if (message.isAdmissionOffered()) {
                alice.stateFlow.value = ConnectionState.Closed
                yield()
            }
        }

        val failure = room.approveAdmission(PlayerId("alice-pid"))
        assertThat(failure).isInstanceOf(Result.Failure::class)
        assertThat((failure as Result.Failure).error).isEqualTo(NetError.NotConnected)
        assertThat(room.members.value).isEmpty()
        assertThat(room.pendingAdmissions.value).isEmpty()

        val bob = FakeP2pSession(peer("bob-pid", "Bob"))
        requestAdmission(room, kit, bob)
        assertThat(room.approveAdmission(PlayerId("bob-pid")))
            .isInstanceOf(Result.Success::class)
    }

    @Test
    fun cancellation_during_acceptance_propagates_after_rolling_back_the_seat() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("host-pid"))
        val room = newHostRoom(kit, maxRemotePlayers = 1)
        val alice = FakeP2pSession(peer("alice-pid", "Alice"))
        requestAdmission(room, kit, alice)
        alice.sendHandler = { message ->
            if (message.isAdmissionOffered()) throw CancellationException("cancel acceptance")
        }

        assertFailsWith<CancellationException> {
            room.approveAdmission(PlayerId("alice-pid"))
        }
        assertThat(room.members.value).isEmpty()
        assertThat(room.pendingAdmissions.value).isEmpty()

        val bob = FakeP2pSession(peer("bob-pid", "Bob"))
        requestAdmission(room, kit, bob)
        assertThat(room.approveAdmission(PlayerId("bob-pid")))
            .isInstanceOf(Result.Success::class)
    }

    @Test
    fun closing_admissions_is_an_atomic_barrier_around_in_flight_approval() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("host-pid"))
        val room = newHostRoom(kit, maxRemotePlayers = 2)
        val alice = FakeP2pSession(peer("alice-pid", "Alice"))
        val bob = FakeP2pSession(peer("bob-pid", "Bob"))
        requestAdmission(room, kit, alice)
        requestAdmission(room, kit, bob)

        val acceptanceStarted = CompletableDeferred<Unit>()
        val releaseAcceptance = CompletableDeferred<Unit>()
        alice.sendHandler = { message ->
            if (message.isAdmissionOffered()) {
                acceptanceStarted.complete(Unit)
                releaseAcceptance.await()
            }
        }
        val approval = testScope.async { room.approveAdmission(PlayerId("alice-pid")) }
        acceptanceStarted.await()

        val blocked = room.closeAdmissions()
        assertThat(blocked).isInstanceOf(Result.Failure::class)
        assertThat((blocked as Result.Failure).error).isEqualTo(NetError.CommandInFlight)
        assertThat(bob.admissionRejections()).isEmpty()
        assertThat(room.pendingAdmissions.value.map { it.playerId })
            .containsExactlyInAnyOrder(PlayerId("alice-pid"), PlayerId("bob-pid"))

        releaseAcceptance.complete(Unit)
        assertThat(approval.await()).isInstanceOf(Result.Success::class)
        val frozen = room.closeAdmissions()
        assertThat(frozen).isInstanceOf(Result.Success::class)
        assertThat((frozen as Result.Success).data.map(RoomMember::playerId))
            .containsExactly(PlayerId("alice-pid"))
        assertThat(bob.admissionRejections()).containsExactly(AdmissionRejection.SessionStarted)
        assertThat(room.pendingAdmissions.value).isEmpty()
    }

    @Test
    fun closing_admissions_excludes_and_invalidates_disconnected_lobby_members() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("host-pid"))
        val room = newHostRoom(kit, maxRemotePlayers = 2)
        val alice = FakeP2pSession(peer("alice-pid", "Alice"))
        val credential = admit(room, kit, alice)

        alice.stateFlow.value = ConnectionState.Closed
        awaitCondition { room.members.value.singleOrNull()?.connected == false }

        val frozen = room.closeAdmissions()
        assertThat(frozen).isInstanceOf(Result.Success::class)
        assertThat((frozen as Result.Success).data).isEmpty()
        assertThat(room.members.value).isEmpty()

        val returningAlice = FakeP2pSession(peer("alice-pid", "Alice"))
        kit.incomingSessionsFlow.emit(returningAlice)
        yield()
        returningAlice.incomingFlow.emit(
            P2pMessage.Binary(
                codec.encode(
                    PeerMessage.ResumeRequested(
                        protocol = ProtocolVersion(),
                        actor = PlayerId("forged-body-id"),
                        roomCode = "ABCDEF",
                        displayName = "Alice",
                        secret = credential,
                        generation = 1L,
                    ),
                ),
            ),
        )
        awaitCondition { returningAlice.admissionRejections().isNotEmpty() }
        assertThat(returningAlice.admissionRejections())
            .containsExactly(AdmissionRejection.InvalidCredential)
    }

    @Test
    fun host_rejects_a_session_without_an_authenticated_peer_fingerprint() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("host-pid"))
        val room = newHostRoom(kit)
        val alicePeer = peer("alice-pid", "Alice")
        val alice = FakeP2pSession(
            peer = alicePeer,
            peerIdentity = PeerIdentity(alicePeer.id, fingerprint = null),
        )

        attachSession(kit, alice)

        awaitCondition { alice.state.value == ConnectionState.Closed }
        assertThat(alice.admissionRejections())
            .containsExactly(AdmissionRejection.InvalidCredential)
        assertThat(alice.closeCalls).isEqualTo(1)
        assertThat(room.members.value).isEmpty()
        assertThat(room.pendingAdmissions.value).isEmpty()
    }

    @Test
    fun host_rejects_a_session_whose_authenticated_peer_id_disagrees_with_the_peer() =
        runBlocking {
            val kit = FakeP2pKit(P2pPeerId("host-pid"))
            val room = newHostRoom(kit)
            val alicePeer = peer("alice-pid", "Alice")
            val alice = FakeP2pSession(
                peer = alicePeer,
                peerIdentity = PeerIdentity(
                    peerId = P2pPeerId("different-pid"),
                    fingerprint = TEST_PEER_FINGERPRINT,
                ),
            )

            attachSession(kit, alice)

            awaitCondition { alice.state.value == ConnectionState.Closed }
            assertThat(alice.admissionRejections())
                .containsExactly(AdmissionRejection.InvalidCredential)
            assertThat(alice.closeCalls).isEqualTo(1)
            assertThat(room.members.value).isEmpty()
            assertThat(room.pendingAdmissions.value).isEmpty()
        }

    @Test
    fun host_rejects_resume_when_the_presented_fingerprint_differs_from_the_stored_credential() =
        runBlocking {
            val kit = FakeP2pKit(P2pPeerId("host-pid"))
            val room = newHostRoom(kit)
            val firstAlice = FakeP2pSession(peer("alice-pid", "Alice"))
            val rejoinSecret = admit(room, kit, firstAlice)
            firstAlice.stateFlow.value = ConnectionState.Closed
            awaitCondition { room.members.value.singleOrNull()?.connected == false }

            val returningPeer = peer("alice-pid", "Alice")
            val returningAlice = FakeP2pSession(
                peer = returningPeer,
                peerIdentity = PeerIdentity(returningPeer.id, TEST_OTHER_PEER_FINGERPRINT),
            )
            attachSession(kit, returningAlice)
            returningAlice.incomingFlow.emit(
                P2pMessage.Binary(
                    codec.encode(
                        PeerMessage.ResumeRequested(
                            protocol = ProtocolVersion(),
                            actor = PlayerId("forged-body-id"),
                            roomCode = "ABCDEF",
                            displayName = "Alice",
                            secret = rejoinSecret,
                            generation = 1L,
                        ),
                    ),
                ),
            )

            awaitCondition { returningAlice.state.value == ConnectionState.Closed }
            assertThat(returningAlice.admissionRejections())
                .containsExactly(AdmissionRejection.InvalidCredential)
            assertThat(returningAlice.closeCalls).isEqualTo(1)
            assertThat(room.members.value.single().connected).isFalse()
            assertThat(room.pendingAdmissions.value).isEmpty()
        }

    @Test
    fun host_rejects_a_wrong_code_without_creating_a_member_or_pending_seat() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("host-pid"))
        val room = newHostRoom(kit)
        val alice = FakeP2pSession(peer("alice-pid", "Alice"))
        kit.incomingSessionsFlow.emit(alice)
        yield()
        alice.incomingFlow.emit(
            P2pMessage.Binary(
                codec.encode(
                    PeerMessage.AdmissionRequest(
                        protocol = ProtocolVersion(),
                        actor = PlayerId("alice-pid"),
                        roomCode = "WRONG2",
                        displayName = "Alice",
                    ),
                ),
            ),
        )

        awaitCondition { alice.state.value == ConnectionState.Closed }
        val rejection = alice.sent
            .filterIsInstance<P2pMessage.Binary>()
            .map {
                codec.decode(it.bytes)
            }
            .filterIsInstance<HostMessage.AdmissionRejected>()
            .single()
        assertThat(rejection.reason)
            .isEqualTo(com.parlor.networking.protocol.AdmissionRejection.WrongCode)
        assertThat(room.members.value).isEmpty()
        assertThat(room.pendingAdmissions.value).isEmpty()
    }

    @Test
    fun host_overwrites_forged_client_command_actor_with_authenticated_peer() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("host-pid"))
        val diagnostics = RecordingP2pDiagnostics()
        val room = newHostRoom(kit, diagnostics = diagnostics)
        val alice = FakeP2pSession(peer("alice-pid", "Alice"))
        admit(room, kit, alice)
        val received = testScope.async { room.incoming.first() }
        yield()
        val forged = PeerMessage.ClientCommand(
            header = SessionEnvelopeHeader(
                protocol = ProtocolVersion(),
                sessionId = SessionId("session"),
                gameId = GameId("test-game"),
                gameVersion = 1,
                messageId = "command-message",
                sequence = 0,
            ),
            actor = PlayerId("victim-pid"),
            commandId = "command-id",
            clientSequence = 1,
            expectedRevision = 0,
            payload = byteArrayOf(1),
        )
        alice.incomingFlow.emit(
            P2pMessage.Binary(
                codec.encode(forged),
            ),
        )

        val stamped = withTimeout(2_000) { received.await() } as PeerMessage.ClientCommand
        assertThat(stamped.actor).isEqualTo(PlayerId("alice-pid"))
        assertThat(diagnostics.events.map { it.name })
            .contains(P2pDiagnosticEventName.COMMAND_RECEIVED)

        val outcome = HostMessage.CommandResult(
            header = forged.header.copy(messageId = "result-message", sequence = 1L),
            commandId = forged.commandId,
            status = CommandStatus.StaleRevision,
            authoritativeRevision = 2L,
        )
        assertThat(room.send(SendTarget.Direct(PlayerId("alice-pid")), outcome))
            .isInstanceOf(Result.Success::class)
        assertThat(diagnostics.events.last()).isEqualTo(
            P2pDiagnosticEvent(
                name = P2pDiagnosticEventName.COMMAND_REJECTED,
                role = P2pDiagnosticRole.HOST,
                result = P2pDiagnosticResult.REJECTED,
                reason = P2pDiagnosticReason.STALE_REVISION,
            ),
        )
    }

    @Test
    fun host_overwrites_forged_reliable_start_ack_actors_with_authenticated_peer() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("host-pid"))
        val room = newHostRoom(kit)
        val alice = FakeP2pSession(peer("alice-pid", "Alice"))
        admit(room, kit, alice)
        val forgedActor = PlayerId("victim-pid")
        val authenticatedActor = PlayerId("alice-pid")
        val header = SessionEnvelopeHeader(
            protocol = ProtocolVersion(),
            sessionId = SessionId("session"),
            gameId = GameId("test-game"),
            gameVersion = 1,
            messageId = "start-message-id",
            sequence = 0L,
        )
        val startId = "start-id-1234567"
        val acknowledgements = listOf<PeerMessage>(
            PeerMessage.SessionStartReady(header, forgedActor, startId),
            PeerMessage.SessionStartCommitAck(header, forgedActor, startId),
        )

        acknowledgements.forEach { forged ->
            val received = testScope.async { room.incoming.first() }
            yield()
            alice.incomingFlow.emit(P2pMessage.Binary(codec.encode(forged)))

            when (val stamped = withTimeout(2_000L) { received.await() }) {
                is PeerMessage.SessionStartReady -> {
                    assertThat(stamped.actor).isEqualTo(authenticatedActor)
                    assertThat(stamped.header).isEqualTo(header)
                    assertThat(stamped.startId).isEqualTo(startId)
                }
                is PeerMessage.SessionStartCommitAck -> {
                    assertThat(stamped.actor).isEqualTo(authenticatedActor)
                    assertThat(stamped.header).isEqualTo(header)
                    assertThat(stamped.startId).isEqualTo(startId)
                }
                else -> error("unexpected reliable-start acknowledgement: $stamped")
            }
        }

        room.leave()
        assertThat(kit.stopCalls).isEqualTo(1)
    }

    @Test
    fun host_rejects_reliable_start_acknowledgements_before_admission() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("host-pid"))
        val room = newHostRoom(kit)
        val header = SessionEnvelopeHeader(
            protocol = ProtocolVersion(),
            sessionId = SessionId("session"),
            gameId = GameId("test-game"),
            gameVersion = 1,
            messageId = "start-message-id",
            sequence = 0L,
        )
        val messages = listOf<(PlayerId) -> PeerMessage>(
            { actor -> PeerMessage.SessionStartReady(header, actor, "start-id-1234567") },
            { actor -> PeerMessage.SessionStartCommitAck(header, actor, "start-id-1234567") },
        )

        messages.forEachIndexed { index, message ->
            val session = FakeP2pSession(peer("unadmitted-$index", "Unadmitted $index"))
            kit.incomingSessionsFlow.emit(session)
            yield()
            session.incomingFlow.emit(
                P2pMessage.Binary(codec.encode(message(PlayerId("forged-actor")))),
            )

            awaitCondition { session.state.value == ConnectionState.Closed }
            assertThat(session.admissionRejections())
                .containsExactly(AdmissionRejection.InvalidRequest)
        }

        assertThat(room.pendingAdmissions.value).isEmpty()
        assertThat(room.members.value).isEmpty()
        room.leave()
        assertThat(kit.stopCalls).isEqualTo(1)
    }

    @Test
    fun terminal_unadmitted_sessions_release_their_state_collectors() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("host-pid"))
        val room = newHostRoom(kit)

        repeat(32) { index ->
            val session = FakeP2pSession(peer("closed-$index", "Closed $index"))
            kit.incomingSessionsFlow.emit(session)
            awaitCondition { session.stateFlow.subscriptionCount.value == 1 }

            session.stateFlow.value = ConnectionState.Closed

            awaitCondition { session.stateFlow.subscriptionCount.value == 0 }
        }

        room.leave()
        assertThat(kit.stopCalls).isEqualTo(1)
    }

    @Test
    fun host_application_queue_applies_bounded_backpressure_without_dropping_gameplay() =
        runBlocking {
            val kit = FakeP2pKit(P2pPeerId("host-pid"))
            val room = newHostRoom(kit)
            val alice = FakeP2pSession(
                peer = peer("alice-pid", "Alice"),
                incomingExtraBufferCapacity = 0,
            )
            admit(room, kit, alice)
            val heartbeat = P2pMessage.Binary(codec.encode(testPeerHeartbeat()))

            val producer = async {
                // One frame may already be executing inside the collector in
                // addition to the channel's configured buffered capacity.
                repeat(P2pTrafficLimits.HOST_APPLICATION_QUEUE_CAPACITY + 2) {
                    alice.incomingFlow.emit(heartbeat)
                }
            }
            yield()
            assertThat(producer.isCompleted).isFalse()

            assertThat(withTimeout(2_000) { room.incoming.first() })
                .isEqualTo(testPeerHeartbeat())
            withTimeout(2_000) { producer.await() }
            assertThat(alice.state.value).isEqualTo(ConnectionState.Connected)
        }

    @Test
    fun collectors_drop_non_binary_frames_and_accept_followup() = runBlocking {
        val frame = P2pMessage.Text("reserved text channel")

        assertHostCollectorRejects(frame, P2pDiagnosticReason.MALFORMED)
        assertPeerCollectorRejects(frame, P2pDiagnosticReason.MALFORMED)
    }

    @Test
    fun collectors_drop_truncated_cbor_frames_and_accept_followup() = runBlocking {
        val truncatedMap = P2pMessage.Binary(byteArrayOf(0xBF.toByte()))

        assertHostCollectorRejects(truncatedMap, P2pDiagnosticReason.MALFORMED)
        assertPeerCollectorRejects(truncatedMap, P2pDiagnosticReason.MALFORMED)
    }

    @Test
    fun collectors_drop_wrong_direction_frames_and_accept_followup() = runBlocking {
        assertHostCollectorRejects(
            P2pMessage.Binary(codec.encode(testHostHeartbeat())),
            P2pDiagnosticReason.WRONG_DIRECTION,
        )
        assertPeerCollectorRejects(
            P2pMessage.Binary(codec.encode(testPeerHeartbeat())),
            P2pDiagnosticReason.WRONG_DIRECTION,
        )
    }

    @Test
    fun collectors_drop_oversized_wire_frames_and_accept_followup() = runBlocking {
        assertHostCollectorRejects(
            P2pMessage.Binary(
                ByteArray(P2pTrafficLimits.MAX_PEER_TO_HOST_FRAME_BYTES + 1),
            ),
            P2pDiagnosticReason.RATE_LIMIT,
        )
        assertPeerCollectorRejects(
            P2pMessage.Binary(
                ByteArray(P2pTrafficLimits.MAX_HOST_TO_PEER_FRAME_BYTES + 1),
            ),
            P2pDiagnosticReason.RATE_LIMIT,
        )
    }

    @Test
    fun host_collector_drops_decoded_command_with_oversized_payload_and_accepts_followup() =
        runBlocking {
            val encoded = codec.encode(
                testClientCommand(ByteArray(MAX_COMMAND_PAYLOAD_BYTES + 1)),
            )
            assertThat(
                encoded.size <= P2pTrafficLimits.MAX_PEER_TO_HOST_FRAME_BYTES,
            ).isTrue()

            assertHostCollectorRejects(
                P2pMessage.Binary(encoded),
                P2pDiagnosticReason.WRONG_DIRECTION,
            )
        }

    @Test
    fun wrong_direction_frames_disconnect_only_the_modified_peer_after_three_strikes() =
        runBlocking {
            val kit = FakeP2pKit(P2pPeerId("host-pid"))
            val room = newHostRoom(kit, maxRemotePlayers = 2)
            val alice = FakeP2pSession(peer("alice-pid", "Alice"))
            val bob = FakeP2pSession(peer("bob-pid", "Bob"))
            admit(room, kit, alice)
            admit(room, kit, bob)
            val hostOnlyFrame = P2pMessage.Binary(codec.encode(testTerminalMessage()))

            repeat(P2pTrafficLimits.MAX_TRAFFIC_VIOLATIONS) {
                alice.incomingFlow.emit(hostOnlyFrame)
            }

            awaitCondition { alice.state.value == ConnectionState.Closed }
            assertThat(bob.state.value).isEqualTo(ConnectionState.Connected)
            assertThat(
                room.members.value.single { it.playerId == PlayerId("bob-pid") }.connected,
            ).isTrue()
        }

    @Test
    fun retransmitted_request_on_same_pending_session_is_idempotent_not_rate_limited() =
        runBlocking {
            val kit = FakeP2pKit(P2pPeerId("host-pid"))
            val room = newHostRoom(kit)
            val alice = FakeP2pSession(peer("alice-pid", "Alice"))
            val request = P2pMessage.Binary(
                codec.encode(
                    PeerMessage.AdmissionRequest(
                        protocol = ProtocolVersion(),
                        actor = PlayerId("forged"),
                        roomCode = "ABCDEF",
                        displayName = "Alice",
                    ),
                ),
            )
            kit.incomingSessionsFlow.emit(alice)
            yield()

            repeat(10) { alice.incomingFlow.emit(request) }

            awaitCondition { room.pendingAdmissions.value.size == 1 }
            assertThat(alice.state.value).isEqualTo(ConnectionState.Connected)
            assertThat(alice.admissionRejections()).isEmpty()
        }

    @Test
    fun fourth_fresh_wrong_code_attempt_from_same_identity_is_rate_limited() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("host-pid"))
        val room = newHostRoom(kit)
        val attempts = mutableListOf<FakeP2pSession>()

        repeat(P2pTrafficLimits.ADMISSION_PER_PEER_BURST + 1) {
            val session = FakeP2pSession(peer("attacker-pid", "Attacker"))
            attempts += session
            kit.incomingSessionsFlow.emit(session)
            yield()
            session.incomingFlow.emit(
                P2pMessage.Binary(
                    codec.encode(
                        PeerMessage.AdmissionRequest(
                            protocol = ProtocolVersion(),
                            actor = PlayerId("forged"),
                            roomCode = "WRONG2",
                            displayName = "Attacker",
                        ),
                    ),
                ),
            )
            awaitCondition { session.state.value == ConnectionState.Closed }
        }

        attempts.take(P2pTrafficLimits.ADMISSION_PER_PEER_BURST).forEach { session ->
            assertThat(session.admissionRejections())
                .containsExactly(AdmissionRejection.WrongCode)
        }
        assertThat(attempts.last().admissionRejections())
            .containsExactly(AdmissionRejection.RateLimited)
    }

    @Test
    fun pending_admissions_and_pre_admission_sessions_have_hard_room_limits() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("host-pid"))
        val room = newHostRoom(kit)
        repeat(P2pTrafficLimits.MAX_PENDING_ADMISSION_REQUESTS) { index ->
            requestAdmission(
                room,
                kit,
                FakeP2pSession(peer("pending-$index", "Player $index")),
            )
        }
        assertThat(room.pendingAdmissions.value)
            .hasSize(P2pTrafficLimits.MAX_PENDING_ADMISSION_REQUESTS)

        val overflow = FakeP2pSession(peer("pending-overflow", "Overflow"))
        kit.incomingSessionsFlow.emit(overflow)
        yield()
        overflow.incomingFlow.emit(
            P2pMessage.Binary(
                codec.encode(
                    PeerMessage.AdmissionRequest(
                        protocol = ProtocolVersion(),
                        actor = PlayerId("forged"),
                        roomCode = "ABCDEF",
                        displayName = "Overflow",
                    ),
                ),
            ),
        )
        awaitCondition { overflow.state.value == ConnectionState.Closed }
        assertThat(overflow.admissionRejections())
            .containsExactly(AdmissionRejection.RateLimited)

        val smallKit = FakeP2pKit(P2pPeerId("small-host"))
        val smallRoom = newHostRoom(smallKit, maxRemotePlayers = 1)
        repeat(1 + P2pTrafficLimits.SESSION_ADMISSION_HEADROOM) { index ->
            smallKit.incomingSessionsFlow.emit(
                FakeP2pSession(peer("idle-$index", "Idle $index")),
            )
        }
        val sessionOverflow = FakeP2pSession(peer("idle-overflow", "Idle overflow"))
        smallKit.incomingSessionsFlow.emit(sessionOverflow)
        awaitCondition { sessionOverflow.state.value == ConnectionState.Closed }
        assertThat(sessionOverflow.admissionRejections())
            .containsExactly(AdmissionRejection.RateLimited)
        smallRoom.leave()
    }

    @Test
    fun host_advertisement_never_contains_the_room_code() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("host-pid"))
        var advertisedName: String? = null
        val transport = P2pKitRoomTransport(
            appId = AppId("com.parlor.test"),
            deviceName = "host-device",
            scope = testScope,
            kitFactory = object : P2pKitFactory {
                override suspend fun createKit(appId: AppId, deviceName: String): P2pKit {
                    advertisedName = deviceName
                    return kit
                }
            },
        )

        val hosted = transport.host(com.parlor.networking.transport.HostConfig("Room"))
        assertThat(hosted).isInstanceOf(Result.Success::class)
        val room = (hosted as Result.Success).data
        assertThat(advertisedName).isEqualTo("${P2pKitRoomTransport.P2P_ROOM_PREFIX}host-device")
        assertThat(advertisedName.orEmpty().contains(room.info.value.code)).isFalse()
        room.leave()
    }

    @Test
    fun room_code_entry_is_not_claimed_as_manual_endpoint_connection() {
        val transport = P2pKitRoomTransport(
            appId = AppId("com.parlor.test"),
            deviceName = "peer-device",
            scope = testScope,
            kitFactory = object : P2pKitFactory {
                override suspend fun createKit(appId: AppId, deviceName: String): P2pKit =
                    FakeP2pKit(P2pPeerId("peer-pid"))
            },
        )

        assertThat(transport.capability.supportsDiscovery).isFalse()
        assertThat(transport.capability.supportsManualEndpointConnection).isFalse()
    }

    @Test
    fun host_reports_operational_access_only_after_real_advertising_succeeds() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("host-pid"))
        val transport = P2pKitRoomTransport(
            AppId("com.parlor.test"),
            "host-device",
            testScope,
            object : P2pKitFactory {
                override suspend fun createKit(appId: AppId, deviceName: String): P2pKit = kit
            },
        )

        val result = transport.host(com.parlor.networking.transport.HostConfig("Room"))

        assertThat(result).isInstanceOf(Result.Success::class)
        assertThat(transport.localNetworkAccess.value)
            .isEqualTo(LocalNetworkAccess.Operational)
        (result as Result.Success).data.leave()
    }

    @Test
    fun host_keeps_generic_start_failure_unclassified_instead_of_guessing_denial() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("host-pid")).apply {
            startHandler = { error("listener failed") }
        }
        val transport = P2pKitRoomTransport(
            AppId("com.parlor.test"),
            "host-device",
            testScope,
            object : P2pKitFactory {
                override suspend fun createKit(appId: AppId, deviceName: String): P2pKit = kit
            },
        )

        assertThat(transport.host(com.parlor.networking.transport.HostConfig("Room")))
            .isInstanceOf(Result.Failure::class)
        assertThat(transport.localNetworkAccess.value)
            .isEqualTo(LocalNetworkAccess.FailureUnclassified)
    }

    @Test
    fun host_reports_actionable_denial_only_for_typed_permission_evidence() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("host-pid")).apply {
            startHandler = {
                throw P2pError.PermissionMissing(listOf(P2pPermission.LocalNetwork))
            }
        }
        val transport = P2pKitRoomTransport(
            AppId("com.parlor.test"),
            "host-device",
            testScope,
            object : P2pKitFactory {
                override suspend fun createKit(appId: AppId, deviceName: String): P2pKit = kit
            },
        )

        assertThat(transport.host(com.parlor.networking.transport.HostConfig("Room")))
            .isInstanceOf(Result.Failure::class)
        assertThat(transport.localNetworkAccess.value)
            .isEqualTo(LocalNetworkAccess.PermissionDenied)
    }

    @Test
    fun host_emits_peer_left_and_removes_member_when_session_closes() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("host-pid"))
        val room = newHostRoom(kit)
        val events = mutableListOf<PeerEvent>()
        val collector = testScope.async { room.peerEvents.collect { events += it } }
        yield(); yield()

        val alice = FakeP2pSession(peer("alice-pid", "Alice"))
        admit(room, kit, alice)

        alice.stateFlow.value = ConnectionState.Closed
        awaitCondition { events.any { it is PeerEvent.PeerLeft } }

        assertThat(room.members.value.single().connected).isFalse()
        val left = events.filterIsInstance<PeerEvent.PeerLeft>().single()
        assertThat(left.playerId).isEqualTo(PlayerId("alice-pid"))
        assertThat(left.displayName).isEqualTo("Alice")

        collector.cancel()
    }

    @Test
    fun host_emits_peer_left_and_removes_member_when_session_fails() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("host-pid"))
        val room = newHostRoom(kit)
        val events = mutableListOf<PeerEvent>()
        val collector = testScope.async { room.peerEvents.collect { events += it } }
        yield(); yield()

        val alice = FakeP2pSession(peer("alice-pid", "Alice"))
        admit(room, kit, alice)

        alice.stateFlow.value = ConnectionState.Failed
        awaitCondition { events.any { it is PeerEvent.PeerLeft } }

        assertThat(room.members.value.single().connected).isFalse()
        collector.cancel()
    }

    @Test
    fun host_marks_member_disconnected_on_reconnecting_then_emits_reconnected_on_recovery() =
        runBlocking {
            val kit = FakeP2pKit(P2pPeerId("host-pid"))
            val room = newHostRoom(kit)
            val events = mutableListOf<PeerEvent>()
            val collector = testScope.async { room.peerEvents.collect { events += it } }
            yield(); yield()

            val alice = FakeP2pSession(peer("alice-pid", "Alice"))
            admit(room, kit, alice)

            alice.stateFlow.value = ConnectionState.Reconnecting
            awaitCondition { room.members.value.first().connected.not() }
            // Member persists in the list during the soft drop — it's the
            // PeerReconnected event that depends on the entry still being
            // there.
            assertThat(room.members.value).hasSize(1)
            assertThat(room.members.value.first().connected).isFalse()
            // No PeerLeft / PeerReconnected yet — only the initial PeerJoined.
            assertThat(events.filterIsInstance<PeerEvent.PeerLeft>()).isEqualTo(emptyList())

            alice.stateFlow.value = ConnectionState.Connected
            awaitCondition { events.any { it is PeerEvent.PeerReconnected } }

            assertThat(room.members.value.first().connected).isTrue()
            assertThat(events.filterIsInstance<PeerEvent.PeerReconnected>().single().playerId)
                .isEqualTo(PlayerId("alice-pid"))
            collector.cancel()
        }

    @Test
    fun host_emits_peer_reconnected_for_a_returning_player_via_new_session() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("host-pid"))
        val room = newHostRoom(kit)
        val events = mutableListOf<PeerEvent>()
        val collector = testScope.async { room.peerEvents.collect { events += it } }
        yield(); yield()

        // First session: Alice joins, then leaves entirely.
        val firstAlice = FakeP2pSession(peer("alice-pid", "Alice"))
        val rejoinToken = admit(room, kit, firstAlice)
        firstAlice.stateFlow.value = ConnectionState.Closed
        awaitCondition { room.members.value.singleOrNull()?.connected == false }

        // Same PlayerId returns on a brand-new session. Should be
        // PeerReconnected, NOT PeerJoined again — the host-side bridges
        // use this signal to re-ship the snapshot rather than treat the
        // peer as someone who has never seen the game state.
        val secondAlice = FakeP2pSession(peer("alice-pid", "Alice")).apply {
            autoResumeReady = false
        }
        resume(room, kit, secondAlice, rejoinToken)
        assertThat(events.filterIsInstance<PeerEvent.PeerReconnected>()).isEmpty()
        val committed = secondAlice.sent
            .filterIsInstance<P2pMessage.Binary>()
            .map { codec.decode(it.bytes) }
            .filterIsInstance<HostMessage.ResumeCommitted>()
            .single()
        secondAlice.incomingFlow.emit(
            P2pMessage.Binary(
                codec.encode(
                    PeerMessage.ResumeReady(
                        actor = PlayerId("forged-body-id"),
                        offerId = committed.offerId,
                        generation = committed.generation,
                    ),
                ),
            ),
        )
        awaitCondition { events.count { it is PeerEvent.PeerReconnected } >= 1 }

        assertThat(room.members.value).hasSize(1)
        assertThat(room.members.value.first().connected).isTrue()
        // Exactly one Joined (initial) + one Left + one Reconnected.
        assertThat(events.filterIsInstance<PeerEvent.PeerJoined>()).hasSize(1)
        assertThat(events.filterIsInstance<PeerEvent.PeerLeft>()).hasSize(1)
        assertThat(events.filterIsInstance<PeerEvent.PeerReconnected>()).hasSize(1)
        collector.cancel()
    }

    @Test
    fun retiring_a_frozen_disconnected_seat_revokes_rejoin_and_is_idempotent() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("host-pid"))
        val room = newHostRoom(kit)
        val firstAlice = FakeP2pSession(peer("alice-pid", "Alice"))
        val rejoinToken = admit(room, kit, firstAlice)
        assertThat(room.closeAdmissions()).isInstanceOf(Result.Success::class)

        firstAlice.stateFlow.value = ConnectionState.Closed
        awaitCondition { room.members.value.singleOrNull()?.connected == false }

        assertThat(room.retireDisconnectedMember(PlayerId("alice-pid")))
            .isInstanceOf(Result.Success::class)
        assertThat(room.retireDisconnectedMember(PlayerId("alice-pid")))
            .isInstanceOf(Result.Success::class)
        assertThat(room.members.value).isEmpty()

        val replay = FakeP2pSession(peer("alice-pid", "Alice"))
        kit.incomingSessionsFlow.emit(replay)
        yield()
        replay.incomingFlow.emit(
            P2pMessage.Binary(
                codec.encode(
                    PeerMessage.ResumeRequested(
                        protocol = ProtocolVersion(),
                        actor = PlayerId("forged-body-id"),
                        roomCode = "ABCDEF",
                        displayName = "Alice",
                        secret = rejoinToken,
                        generation = 1L,
                    ),
                ),
            ),
        )
        awaitCondition {
            AdmissionRejection.InvalidCredential in replay.admissionRejections()
        }
        assertThat(room.members.value).isEmpty()
    }

    @Test
    fun retirement_rolls_back_a_resume_offer_that_is_mid_delivery() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("host-pid"))
        val room = newHostRoom(kit)
        val firstAlice = FakeP2pSession(peer("alice-pid", "Alice"))
        val rejoinToken = admit(room, kit, firstAlice)
        assertThat(room.closeAdmissions()).isInstanceOf(Result.Success::class)
        firstAlice.stateFlow.value = ConnectionState.Closed
        awaitCondition { room.members.value.singleOrNull()?.connected == false }

        val offerEntered = CompletableDeferred<Unit>()
        val releaseOffer = CompletableDeferred<Unit>()
        val returning = FakeP2pSession(peer("alice-pid", "Alice")).apply {
            sendHandler = { message ->
                val decoded = (message as? P2pMessage.Binary)?.let { codec.decode(it.bytes) }
                if (decoded is HostMessage.ResumeOffered) {
                    offerEntered.complete(Unit)
                    releaseOffer.await()
                }
            }
        }
        kit.incomingSessionsFlow.emit(returning)
        yield()
        returning.incomingFlow.emit(
            P2pMessage.Binary(
                codec.encode(
                    PeerMessage.ResumeRequested(
                        protocol = ProtocolVersion(),
                        actor = PlayerId("forged-body-id"),
                        roomCode = "ABCDEF",
                        displayName = "Alice",
                        secret = rejoinToken,
                        generation = 1L,
                    ),
                ),
            ),
        )
        offerEntered.await()

        assertThat(room.retireDisconnectedMember(PlayerId("alice-pid")))
            .isInstanceOf(Result.Success::class)
        releaseOffer.complete(Unit)
        awaitCondition { returning.closeCalls > 0 }

        assertThat(room.members.value).isEmpty()
        assertThat(
            returning.sent
                .filterIsInstance<P2pMessage.Binary>()
                .map { codec.decode(it.bytes) }
                .filterIsInstance<HostMessage.ResumeCommitted>(),
        ).isEmpty()
    }

    @Test
    fun host_send_direct_returns_not_connected_after_session_closes() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("host-pid"))
        val room = newHostRoom(kit)

        val alice = FakeP2pSession(peer("alice-pid", "Alice"))
        admit(room, kit, alice)

        // Sanity: send succeeds while connected.
        val before = room.send(SendTarget.Direct(PlayerId("alice-pid")), testTerminalMessage())
        assertThat(before).isInstanceOf(Result.Success::class)

        alice.stateFlow.value = ConnectionState.Closed
        awaitCondition { room.members.value.singleOrNull()?.connected == false }

        val after = room.send(SendTarget.Direct(PlayerId("alice-pid")), testTerminalMessage())
        assertThat(after).isInstanceOf(Result.Failure::class)
        assertThat((after as Result.Failure).error).isEqualTo(NetError.NotConnected)
    }

    @Test
    fun host_leave_clears_members_and_emits_no_events_after_cancellation() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("host-pid"))
        val room = newHostRoom(kit)
        val events = mutableListOf<PeerEvent>()
        val collector = testScope.async { room.peerEvents.collect { events += it } }
        yield()

        val alice = FakeP2pSession(peer("alice-pid", "Alice"))
        admit(room, kit, alice)

        room.leave()

        assertThat(room.members.value).isEqualTo(emptyList())
        collector.cancel()
    }

    // ---------------------------------------------------------------- Peer ----

    @Test
    fun peer_application_queue_applies_bounded_backpressure_to_host_snapshots() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("peer-pid"))
        val hostPeer = peer("host-pid", "Host Device")
        val session = FakeP2pSession(hostPeer, incomingExtraBufferCapacity = 0)
        val room = PeerP2pRoom(
            kit = kit,
            session = session,
            hostPeer = hostPeer,
            roomCode = "ABCDEF",
            scope = testScope,
            codec = codec,
        )
        val applicationMessage = testHostHeartbeat()
        val frame = P2pMessage.Binary(codec.encode(applicationMessage))

        val producer = async {
            // One frame may already be executing inside the collector in
            // addition to the channel's configured buffered capacity.
            repeat(P2pTrafficLimits.PEER_APPLICATION_QUEUE_CAPACITY + 2) {
                session.incomingFlow.emit(frame)
            }
        }
        yield()
        assertThat(producer.isCompleted).isFalse()

        val received = withTimeout(2_000) { room.incoming.first() }
        assertThat(received).isEqualTo(applicationMessage)
        withTimeout(2_000) { producer.await() }
        assertThat(session.state.value).isEqualTo(ConnectionState.Connected)
        room.leave()
    }

    @Test
    fun peer_disconnects_a_modified_host_that_sends_peer_protocol_frames() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("peer-pid"))
        val hostPeer = peer("host-pid", "Host Device")
        val session = FakeP2pSession(hostPeer)
        PeerP2pRoom(
            kit = kit,
            session = session,
            hostPeer = hostPeer,
            roomCode = "ABCDEF",
            scope = testScope,
            codec = codec,
        )
        val wrongDirection = P2pMessage.Binary(codec.encode(testPeerHeartbeat()))

        repeat(P2pTrafficLimits.MAX_TRAFFIC_VIOLATIONS) {
            session.incomingFlow.emit(wrongDirection)
        }

        awaitCondition { session.state.value == ConnectionState.Closed }
    }

    @Test
    fun peer_emits_host_lost_then_host_restored_on_reconnect_cycle() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("peer-pid"))
        val hostPeer = peer("host-pid", "Host Device")
        val session = FakeP2pSession(hostPeer)
        val room = PeerP2pRoom(
            kit = kit,
            session = session,
            hostPeer = hostPeer,
            roomCode = "ABCDEF",
            scope = testScope,
            codec = codec,
        )

        val events = mutableListOf<PeerEvent>()
        val collector = testScope.async { room.peerEvents.collect { events += it } }
        yield(); yield()

        session.stateFlow.value = ConnectionState.Reconnecting
        awaitCondition { events.contains(PeerEvent.HostLost) }
        assertThat(room.info.value.status).isEqualTo(RoomInfo.Status.Lost)
        assertThat(room.members.value.first().connected).isFalse()

        session.stateFlow.value = ConnectionState.Connected
        awaitCondition { events.contains(PeerEvent.HostRestored) }
        assertThat(room.info.value.status).isEqualTo(RoomInfo.Status.Joined)
        assertThat(room.members.value.first().connected).isTrue()

        collector.cancel()
    }

    @Test
    fun peer_replaces_terminal_session_with_rotated_pinned_resume_before_host_restored() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("peer-pid"))
        val hostPeer = peer("host-pid", "Host Device")
        val firstSession = FakeP2pSession(hostPeer)
        val replacementSession = FakeP2pSession(hostPeer)
        val initialCredential = resumableCredential(generation = 1L)
        val rotatedCredential = resumableCredential(generation = 2L)
        val resumeRequests = mutableListOf<ResumableSessionCredential>()
        val room = PeerP2pRoom(
            kit = kit,
            session = firstSession,
            hostPeer = hostPeer,
            roomCode = "ABCDEF",
            scope = testScope,
            codec = codec,
            initialCredential = initialCredential,
            resumeConnector = { credential ->
                resumeRequests += credential
                Result.Success(
                    ResumedPeerConnection(
                        session = replacementSession,
                        hostPeer = hostPeer,
                        credential = rotatedCredential,
                        hostDisplayName = hostPeer.name,
                    ),
                )
            },
        )
        val events = mutableListOf<PeerEvent>()
        val collector = testScope.async { room.peerEvents.collect { events += it } }
        yield(); yield()

        room.appBackgrounded(atEpochMillis = 1_000L)
        firstSession.stateFlow.value = ConnectionState.Closed
        awaitCondition { events.contains(PeerEvent.HostLost) }
        room.appForegrounded(atEpochMillis = 2_000L)
        awaitCondition { events.contains(PeerEvent.HostRestored) }

        assertThat(resumeRequests).containsExactly(initialCredential)
        assertThat(room.lifecycle.value).isEqualTo(RoomLifecycleState.Active)
        assertThat(room.rejoinToken).isEqualTo(null)
        assertThat(firstSession.state.value).isEqualTo(ConnectionState.Closed)
        val sent = room.sendToHost(PeerMessage.LeaveNotice)
        assertThat(sent).isInstanceOf(Result.Success::class)
        assertThat(replacementSession.sent).hasSize(3)
        assertThat(
            replacementSession.sent
                .filterIsInstance<P2pMessage.Binary>()
                .map { codec.decode(it.bytes) },
        ).containsExactly(
            PeerMessage.ResumeReady(PlayerId("peer-pid"), rotatedCredential.offerId, 2L),
            PeerMessage.ResumeCommitAck(PlayerId("peer-pid"), rotatedCredential.offerId, 2L),
            PeerMessage.LeaveNotice,
        )
        collector.cancel()
    }

    @Test
    fun peer_terminal_disconnect_while_foreground_starts_credential_resume() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("peer-pid"))
        val hostPeer = peer("host-pid", "Host Device")
        val firstSession = FakeP2pSession(hostPeer)
        val replacementSession = FakeP2pSession(hostPeer)
        val initialCredential = resumableCredential(generation = 1L)
        val rotatedCredential = resumableCredential(generation = 2L)
        val resumeRequests = mutableListOf<ResumableSessionCredential>()
        val room = PeerP2pRoom(
            kit = kit,
            session = firstSession,
            hostPeer = hostPeer,
            roomCode = "ABCDEF",
            scope = testScope,
            codec = codec,
            initialCredential = initialCredential,
            resumeConnector = { credential ->
                resumeRequests += credential
                Result.Success(
                    ResumedPeerConnection(
                        session = replacementSession,
                        hostPeer = hostPeer,
                        credential = rotatedCredential,
                        hostDisplayName = hostPeer.name,
                    ),
                )
            },
        )
        val events = mutableListOf<PeerEvent>()
        val collector = testScope.async { room.peerEvents.collect { events += it } }
        yield(); yield()

        firstSession.stateFlow.value = ConnectionState.Closed

        awaitCondition { events.contains(PeerEvent.HostRestored) }
        assertThat(resumeRequests).containsExactly(initialCredential)
        assertThat(room.lifecycle.value).isEqualTo(RoomLifecycleState.Active)
        assertThat(room.info.value.status).isEqualTo(RoomInfo.Status.Joined)
        assertThat(room.members.value.single().connected).isTrue()
        assertThat(
            replacementSession.sent
                .filterIsInstance<P2pMessage.Binary>()
                .map { codec.decode(it.bytes) },
        ).containsExactly(
            PeerMessage.ResumeReady(PlayerId("peer-pid"), rotatedCredential.offerId, 2L),
            PeerMessage.ResumeCommitAck(PlayerId("peer-pid"), rotatedCredential.offerId, 2L),
        )

        collector.cancel()
        room.leave()
    }

    @Test
    fun foreground_identity_mismatch_is_transient_and_retries_without_expiring_membership() =
        runBlocking {
            val kit = FakeP2pKit(P2pPeerId("peer-pid"))
            val hostPeer = peer("host-pid", "Host Device")
            val firstSession = FakeP2pSession(hostPeer)
            val replacementSession = FakeP2pSession(hostPeer)
            val credential = resumableCredential(generation = 1L)
            var attempts = 0
            val room = PeerP2pRoom(
                kit = kit,
                session = firstSession,
                hostPeer = hostPeer,
                roomCode = credential.roomCode,
                scope = testScope,
                codec = codec,
                initialCredential = credential,
                resumeConnector = {
                    attempts += 1
                    if (attempts == 1) {
                        Result.Failure(
                            ResumeConnectionFailure(
                                error = NetError.Unauthorized,
                                invalidatesCredential = false,
                            ),
                        )
                    } else {
                        Result.Success(
                            ResumedPeerConnection(
                                session = replacementSession,
                                hostPeer = hostPeer,
                                credential = credential.copy(generation = 2L),
                                hostDisplayName = hostPeer.name,
                            ),
                        )
                    }
                },
            )
            val events = mutableListOf<PeerEvent>()
            val collector = testScope.async { room.peerEvents.collect { events += it } }

            firstSession.stateFlow.value = ConnectionState.Closed

            awaitCondition { events.contains(PeerEvent.HostRestored) }
            assertThat(attempts).isEqualTo(2)
            assertThat(room.lifecycle.value).isEqualTo(RoomLifecycleState.Active)
            assertThat(kit.stopCalls).isEqualTo(0)

            collector.cancel()
            room.leave()
        }

    @Test
    fun foreground_authenticated_permanent_rejection_expires_and_invalidates_credential() =
        runBlocking {
            val kit = FakeP2pKit(P2pPeerId("peer-pid"))
            val hostPeer = peer("host-pid", "Host Device")
            val firstSession = FakeP2pSession(hostPeer)
            val credential = resumableCredential(generation = 1L)
            val store = ResumableCredentialStore(testSecureStorage())
            store.stage(credential)
            store.commit(credential.offerId, credential.generation)
            val room = PeerP2pRoom(
                kit = kit,
                session = firstSession,
                hostPeer = hostPeer,
                roomCode = credential.roomCode,
                scope = testScope,
                codec = codec,
                initialCredential = credential,
                credentialStore = store,
                resumeConnector = {
                    Result.Failure(
                        ResumeConnectionFailure(
                            error = NetError.Unauthorized,
                            invalidatesCredential = true,
                        ),
                    )
                },
            )

            firstSession.stateFlow.value = ConnectionState.Closed

            awaitCondition { kit.stopCalls == 1 }
            assertThat(room.lifecycle.value).isEqualTo(RoomLifecycleState.Expired)
            assertThat(store.loadResumeCandidate()).isEqualTo(Result.Success(null))
            assertThat(kit.stopCalls).isEqualTo(1)
        }

    @Test
    fun foreground_permanent_rejection_revokes_the_entire_rotated_membership() =
        runBlocking {
            val kit = FakeP2pKit(P2pPeerId("peer-pid"))
            val hostPeer = peer("host-pid", "Host Device")
            val firstSession = FakeP2pSession(hostPeer)
            val generationOne = resumableCredential(generation = 1L)
            val generationTwo = resumableCredential(generation = 2L)
            val store = ResumableCredentialStore(testSecureStorage())
            store.stage(generationOne)
            store.commit(generationOne.offerId, generationOne.generation)
            val room = PeerP2pRoom(
                kit = kit,
                session = firstSession,
                hostPeer = hostPeer,
                roomCode = generationOne.roomCode,
                scope = testScope,
                codec = codec,
                initialCredential = generationOne,
                credentialStore = store,
                resumeConnector = {
                    store.stage(generationTwo)
                    store.commit(generationTwo.offerId, generationTwo.generation)
                    Result.Failure(
                        ResumeConnectionFailure(
                            error = NetError.Unauthorized,
                            invalidatesCredential = true,
                        ),
                    )
                },
            )

            firstSession.stateFlow.value = ConnectionState.Closed

            awaitCondition { kit.stopCalls == 1 }
            assertThat(room.lifecycle.value).isEqualTo(RoomLifecycleState.Expired)
            assertThat(store.loadResumeCandidate()).isEqualTo(Result.Success(null))
            assertThat(kit.stopCalls).isEqualTo(1)
        }

    @Test
    fun authoritative_session_end_disables_credential_resume_after_socket_close() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("peer-pid"))
        val hostPeer = peer("host-pid", "Host Device")
        val session = FakeP2pSession(hostPeer)
        val resumeRequests = mutableListOf<ResumableSessionCredential>()
        val room = PeerP2pRoom(
            kit = kit,
            session = session,
            hostPeer = hostPeer,
            roomCode = "ABCDEF",
            scope = testScope,
            codec = codec,
            initialCredential = resumableCredential(generation = 1L),
            resumeConnector = { credential ->
                resumeRequests += credential
                Result.Failure(ResumeConnectionFailure(NetError.Timeout))
            },
        )
        val terminal = HostMessage.SessionEnded(
            header = SessionEnvelopeHeader(
                protocol = ProtocolVersion(),
                sessionId = SessionId("session-terminal"),
                gameId = GameId("game-terminal"),
                gameVersion = 1,
                messageId = "terminal-message-000000000001",
                sequence = 1L,
            ),
            reason = SessionEndReason.HostLeft,
            finalRevision = 2L,
        )

        session.incomingFlow.emit(P2pMessage.Binary(codec.encode(terminal)))
        assertThat(withTimeout(2_000) { room.incoming.first() }).isEqualTo(terminal)
        assertThat(room.commitValidatedSessionEnd(terminal))
            .isEqualTo(Result.Success(SessionEndCommitStatus.Committed))
        session.stateFlow.value = ConnectionState.Closed
        repeat(10) { yield() }

        assertThat(resumeRequests).isEmpty()
        assertThat(room.sendToHost(testPeerHeartbeat()))
            .isEqualTo(Result.Failure(NetError.NotConnected))
        room.leave()
    }

    @Test
    fun terminal_frame_is_staged_until_the_session_layer_validates_it() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("peer-pid"))
        val hostPeer = peer("host-pid", "Host Device")
        val session = FakeP2pSession(hostPeer)
        val credential = resumableCredential(generation = 1L)
        val store = ResumableCredentialStore(testSecureStorage())
        store.stage(credential)
        store.commit(credential.offerId, credential.generation)
        val room = PeerP2pRoom(
            kit = kit,
            session = session,
            hostPeer = hostPeer,
            roomCode = credential.roomCode,
            scope = testScope,
            codec = codec,
            initialCredential = credential,
            credentialStore = store,
        )
        val wrongSessionTerminal = testTerminalMessage().copy(
            header = testTerminalMessage().header.copy(
                sessionId = SessionId("different-session"),
            ),
        )

        session.incomingFlow.emit(P2pMessage.Binary(codec.encode(wrongSessionTerminal)))
        assertThat(withTimeout(2_000L) { room.incoming.first() })
            .isEqualTo(wrongSessionTerminal)

        // Authentication at the physical transport is not enough to revoke a
        // logical membership. The session layer has not validated this frame.
        assertThat(store.loadResumeCandidate()).isEqualTo(Result.Success(credential))
        assertThat(room.sendToHost(testPeerHeartbeat())).isEqualTo(Result.Success(Unit))
        room.leave()
    }

    @Test
    fun failed_terminal_commit_does_not_disable_the_live_room() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("peer-pid"))
        val hostPeer = peer("host-pid", "Host Device")
        val session = FakeP2pSession(hostPeer)
        val credential = resumableCredential(generation = 1L)
        val failingStorage = FailingRemoveSecureStorage(testSecureStorage())
        val store = ResumableCredentialStore(failingStorage)
        store.stage(credential)
        store.commit(credential.offerId, credential.generation)
        val room = PeerP2pRoom(
            kit = kit,
            session = session,
            hostPeer = hostPeer,
            roomCode = credential.roomCode,
            scope = testScope,
            codec = codec,
            initialCredential = credential,
            credentialStore = store,
        )
        failingStorage.failRemove = true

        session.incomingFlow.emit(P2pMessage.Binary(codec.encode(testTerminalMessage())))
        assertThat(withTimeout(2_000L) { room.incoming.first() })
            .isEqualTo(testTerminalMessage())

        assertThat(room.commitValidatedSessionEnd(testTerminalMessage()))
            .isEqualTo(Result.Failure(NetError.SecureStorageUnavailable))
        assertThat(store.loadResumeCandidate()).isEqualTo(Result.Success(credential))
        assertThat(room.sendToHost(testPeerHeartbeat())).isEqualTo(Result.Success(Unit))

        failingStorage.failRemove = false
        failingStorage.fatalRemove = true
        assertFailsWith<AssertionError> {
            room.commitValidatedSessionEnd(testTerminalMessage())
        }
        assertThat(store.loadResumeCandidate()).isEqualTo(Result.Success(credential))
        assertThat(room.sendToHost(testPeerHeartbeat())).isEqualTo(Result.Success(Unit))

        failingStorage.fatalRemove = false
        failingStorage.cancelRemove = true
        assertFailsWith<CancellationException> {
            room.commitValidatedSessionEnd(testTerminalMessage())
        }
        assertThat(store.loadResumeCandidate()).isEqualTo(Result.Success(credential))
        assertThat(room.sendToHost(testPeerHeartbeat())).isEqualTo(Result.Success(Unit))

        failingStorage.cancelRemove = false
        assertThat(room.commitValidatedSessionEnd(testTerminalMessage()))
            .isEqualTo(Result.Success(SessionEndCommitStatus.Committed))
        assertThat(store.loadResumeCandidate()).isEqualTo(Result.Success(null))
        assertThat(room.sendToHost(testPeerHeartbeat()))
            .isEqualTo(Result.Failure(NetError.NotConnected))
        room.leave()
    }

    @Test
    fun validated_terminal_commit_cancels_and_joins_resume_before_revocation() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("peer-pid"))
        val hostPeer = peer("host-pid", "Host Device")
        val session = FakeP2pSession(hostPeer)
        val credential = resumableCredential(generation = 1L)
        val store = ResumableCredentialStore(testSecureStorage())
        store.stage(credential)
        store.commit(credential.offerId, credential.generation)
        val resumeStarted = CompletableDeferred<Unit>()
        val resumeStopped = CompletableDeferred<Unit>()
        val room = PeerP2pRoom(
            kit = kit,
            session = session,
            hostPeer = hostPeer,
            roomCode = credential.roomCode,
            scope = testScope,
            codec = codec,
            initialCredential = credential,
            credentialStore = store,
            resumeConnector = {
                resumeStarted.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    resumeStopped.complete(Unit)
                }
            },
        )
        val terminal = testTerminalMessage()
        session.incomingFlow.emit(P2pMessage.Binary(codec.encode(terminal)))
        assertThat(withTimeout(2_000L) { room.incoming.first() }).isEqualTo(terminal)

        session.stateFlow.value = ConnectionState.Closed
        withTimeout(2_000L) { resumeStarted.await() }

        assertThat(room.commitValidatedSessionEnd(terminal))
            .isEqualTo(Result.Success(SessionEndCommitStatus.Committed))
        assertThat(resumeStopped.isCompleted).isTrue()
        assertThat(store.loadResumeCandidate()).isEqualTo(Result.Success(null))
        room.leave()
    }

    @Test
    fun staged_terminal_from_replaced_session_cannot_revoke_rotated_membership() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("peer-pid"))
        val hostPeer = peer("host-pid", "Host Device")
        val firstSession = FakeP2pSession(hostPeer)
        val replacementSession = FakeP2pSession(hostPeer)
        val generationOne = resumableCredential(generation = 1L)
        val generationTwo = resumableCredential(generation = 2L)
        val store = ResumableCredentialStore(testSecureStorage())
        store.stage(generationOne)
        store.commit(generationOne.offerId, generationOne.generation)
        val room = PeerP2pRoom(
            kit = kit,
            session = firstSession,
            hostPeer = hostPeer,
            roomCode = generationOne.roomCode,
            scope = testScope,
            codec = codec,
            initialCredential = generationOne,
            credentialStore = store,
            resumeConnector = {
                store.stage(generationTwo)
                store.commit(generationTwo.offerId, generationTwo.generation)
                Result.Success(
                    ResumedPeerConnection(
                        replacementSession,
                        hostPeer,
                        generationTwo,
                        hostPeer.name,
                    ),
                )
            },
        )
        val events = mutableListOf<PeerEvent>()
        val eventCollector = testScope.async { room.peerEvents.collect { events += it } }
        val terminal = testTerminalMessage()
        firstSession.incomingFlow.emit(P2pMessage.Binary(codec.encode(terminal)))
        assertThat(withTimeout(2_000L) { room.incoming.first() }).isEqualTo(terminal)

        // The terminal was staged by G1, but G2 wins physical-session
        // ownership before the session coordinator reaches its commit call.
        firstSession.stateFlow.value = ConnectionState.Closed
        awaitCondition { events.contains(PeerEvent.HostRestored) }
        assertThat(store.loadResumeCandidate()).isEqualTo(Result.Success(generationTwo))

        assertThat(room.commitValidatedSessionEnd(terminal))
            .isEqualTo(Result.Success(SessionEndCommitStatus.NotOwned))
        assertThat(store.loadResumeCandidate()).isEqualTo(Result.Success(generationTwo))
        assertThat(room.sendToHost(testPeerHeartbeat())).isEqualTo(Result.Success(Unit))

        eventCollector.cancel()
        room.leave()
    }

    @Test
    fun authenticated_terminal_frame_invalidates_matching_credential_before_ui_cleanup() =
        runBlocking {
            val kit = FakeP2pKit(P2pPeerId("peer-pid"))
            val hostPeer = peer("host-pid", "Host Device")
            val session = FakeP2pSession(hostPeer)
            val credential = resumableCredential(generation = 1L)
            val store = ResumableCredentialStore(testSecureStorage())
            assertThat(store.stage(credential)).isInstanceOf(Result.Success::class)
            assertThat(store.commit(credential.offerId, credential.generation))
                .isInstanceOf(Result.Success::class)
            val room = PeerP2pRoom(
                kit = kit,
                session = session,
                hostPeer = hostPeer,
                roomCode = credential.roomCode,
                scope = testScope,
                codec = codec,
                initialCredential = credential,
                credentialStore = store,
            )
            val terminal = HostMessage.SessionEnded(
                header = SessionEnvelopeHeader(
                    protocol = ProtocolVersion(),
                    sessionId = SessionId("session-terminal-cleanup"),
                    gameId = GameId("game-terminal-cleanup"),
                    gameVersion = 1,
                    messageId = "terminal-cleanup-message-000001",
                    sequence = 1L,
                ),
                reason = SessionEndReason.Completed,
                finalRevision = 7L,
            )

            session.incomingFlow.emit(P2pMessage.Binary(codec.encode(terminal)))
            assertThat(withTimeout(2_000L) { room.incoming.first() }).isEqualTo(terminal)

            // Physical authentication only stages provenance. The validated
            // session-layer commit is the credential-revocation boundary.
            assertThat(store.loadResumeCandidate()).isEqualTo(Result.Success(credential))
            assertThat(room.commitValidatedSessionEnd(terminal))
                .isEqualTo(Result.Success(SessionEndCommitStatus.Committed))
            assertThat(store.loadResumeCandidate()).isEqualTo(Result.Success(null))
            assertThat(kit.stopCalls).isEqualTo(0)

            // A second terminal frame and duplicate leave are both idempotent.
            session.incomingFlow.emit(P2pMessage.Binary(codec.encode(testTerminalMessage())))
            assertThat(withTimeoutOrNull(100L) { room.incoming.first() }).isEqualTo(null)
            room.leave()
            room.leave()
            assertThat(kit.stopCalls).isEqualTo(1)
            assertThat(store.loadResumeCandidate()).isEqualTo(Result.Success(null))
        }

    @Test
    fun stale_room_leave_or_expiry_cannot_erase_a_newer_different_room_credential() =
        runBlocking {
            suspend fun runPath(expire: Boolean) {
                val kit = FakeP2pKit(P2pPeerId("peer-pid"))
                val hostPeer = peer("host-pid", "Host Device")
                val oldCredential = resumableCredential(generation = 1L)
                val replacement = resumableCredential(
                    generation = 1L,
                    offerId = "replacement-room-offer",
                    roomCode = "XYZ789",
                    playerId = "replacement-player",
                    hostPeerId = "replacement-host",
                )
                val store = ResumableCredentialStore(testSecureStorage())
                store.stage(oldCredential)
                store.commit(oldCredential.offerId, oldCredential.generation)
                val oldRoom = PeerP2pRoom(
                    kit = kit,
                    session = FakeP2pSession(hostPeer),
                    hostPeer = hostPeer,
                    roomCode = oldCredential.roomCode,
                    scope = testScope,
                    codec = codec,
                    initialCredential = oldCredential,
                    credentialStore = store,
                )
                store.stage(replacement)
                store.commit(replacement.offerId, replacement.generation)

                if (expire) {
                    oldRoom.appBackgrounded(1_000L)
                    oldRoom.appForegrounded(121_001L)
                } else {
                    oldRoom.leave()
                }

                assertThat(store.loadResumeCandidate()).isEqualTo(Result.Success(replacement))
            }

            runPath(expire = false)
            runPath(expire = true)
        }

    @Test
    fun peer_leave_and_lifecycle_expiry_conditionally_invalidate_their_matching_credential() =
        runBlocking {
            suspend fun runPath(expire: Boolean) {
                val kit = FakeP2pKit(P2pPeerId("peer-pid"))
                val hostPeer = peer("host-pid", "Host Device")
                val credential = resumableCredential(generation = 1L)
                val store = ResumableCredentialStore(testSecureStorage())
                store.stage(credential)
                store.commit(credential.offerId, credential.generation)
                val room = PeerP2pRoom(
                    kit = kit,
                    session = FakeP2pSession(hostPeer),
                    hostPeer = hostPeer,
                    roomCode = credential.roomCode,
                    scope = testScope,
                    codec = codec,
                    initialCredential = credential,
                    credentialStore = store,
                )

                if (expire) {
                    room.appBackgrounded(1_000L)
                    room.appForegrounded(121_001L)
                    assertThat(room.lifecycle.value).isEqualTo(RoomLifecycleState.Expired)
                } else {
                    room.leave()
                    room.leave()
                    assertThat(room.lifecycle.value).isEqualTo(RoomLifecycleState.Closed)
                }

                assertThat(store.loadResumeCandidate()).isEqualTo(Result.Success(null))
                assertThat(kit.stopCalls).isEqualTo(1)
            }

            runPath(expire = false)
            runPath(expire = true)
        }

    @Test
    fun failed_start_cleanup_preserves_membership_until_explicit_final_discard() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("peer-pid"))
        val hostPeer = peer("host-pid", "Host Device")
        val session = FakeP2pSession(hostPeer)
        val credential = resumableCredential(generation = 1L)
        val store = ResumableCredentialStore(testSecureStorage())
        store.stage(credential)
        store.commit(credential.offerId, credential.generation)
        val room = PeerP2pRoom(
            kit = kit,
            session = session,
            hostPeer = hostPeer,
            roomCode = credential.roomCode,
            scope = testScope,
            codec = codec,
            initialCredential = credential,
            credentialStore = store,
        )

        assertThat(room.closeForRetry()).isEqualTo(Result.Success(Unit))
        assertThat(session.state.value).isEqualTo(ConnectionState.Closed)
        assertThat(kit.stopCalls).isEqualTo(1)
        assertThat(store.loadResumeCandidate()).isEqualTo(Result.Success(credential))
        assertThat(
            session.sent
                .filterIsInstance<P2pMessage.Binary>()
                .map { codec.decode(it.bytes) }
                .filterIsInstance<PeerMessage.LeaveNotice>(),
        ).isEmpty()

        // The composition owner's normal idempotent cleanup must not turn the
        // retained retry capability into a final Leave.
        room.leave()
        assertThat(store.loadResumeCandidate()).isEqualTo(Result.Success(credential))

        assertThat(room.discardRejoinCapability()).isEqualTo(Result.Success(Unit))
        assertThat(store.loadResumeCandidate()).isEqualTo(Result.Success(null))
        assertThat(kit.stopCalls).isEqualTo(1)
    }

    @Test
    fun final_discard_from_abandoned_room_revokes_a_concurrently_rotated_membership() =
        runBlocking {
            val kit = FakeP2pKit(P2pPeerId("peer-pid"))
            val hostPeer = peer("host-pid", "Host Device")
            val generationOne = resumableCredential(generation = 1L)
            val generationTwo = generationOne.copy(
                offerId = "resume-offer-2",
                secret = "b".repeat(64),
                generation = 2L,
            )
            val store = ResumableCredentialStore(testSecureStorage())
            store.stage(generationOne)
            store.commit(generationOne.offerId, generationOne.generation)
            val room = PeerP2pRoom(
                kit = kit,
                session = FakeP2pSession(hostPeer),
                hostPeer = hostPeer,
                roomCode = generationOne.roomCode,
                scope = testScope,
                codec = codec,
                initialCredential = generationOne,
                credentialStore = store,
            )

            assertThat(room.closeForRetry()).isEqualTo(Result.Success(Unit))
            store.stage(generationTwo)
            store.commit(generationTwo.offerId, generationTwo.generation)
            assertThat(store.loadResumeCandidate()).isEqualTo(Result.Success(generationTwo))

            assertThat(room.discardRejoinCapability()).isEqualTo(Result.Success(Unit))
            assertThat(store.loadResumeCandidate()).isEqualTo(Result.Success(null))
        }

    @Test
    fun failed_final_discard_is_reported_and_remains_retryable() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("peer-pid"))
        val hostPeer = peer("host-pid", "Host Device")
        val credential = resumableCredential(generation = 1L)
        val failingStorage = FailingRemoveSecureStorage(testSecureStorage())
        val store = ResumableCredentialStore(failingStorage)
        store.stage(credential)
        store.commit(credential.offerId, credential.generation)
        val room = PeerP2pRoom(
            kit = kit,
            session = FakeP2pSession(hostPeer),
            hostPeer = hostPeer,
            roomCode = credential.roomCode,
            scope = testScope,
            codec = codec,
            initialCredential = credential,
            credentialStore = store,
        )
        room.closeForRetry()

        failingStorage.failRemove = true
        assertThat(room.discardRejoinCapability())
            .isEqualTo(Result.Failure(NetError.SecureStorageUnavailable))
        assertThat(store.loadResumeCandidate()).isEqualTo(Result.Success(credential))

        failingStorage.failRemove = false
        failingStorage.cancelRemove = true
        assertFailsWith<CancellationException> { room.discardRejoinCapability() }
        assertThat(store.loadResumeCandidate()).isEqualTo(Result.Success(credential))

        failingStorage.cancelRemove = false
        failingStorage.fatalRemove = true
        assertFailsWith<AssertionError> { room.discardRejoinCapability() }
        assertThat(store.loadResumeCandidate()).isEqualTo(Result.Success(credential))

        failingStorage.fatalRemove = false
        assertThat(room.discardRejoinCapability()).isEqualTo(Result.Success(Unit))
        assertThat(store.loadResumeCandidate()).isEqualTo(Result.Success(null))
    }

    @Test
    fun terminal_policy_tracks_the_current_rotated_generation_not_the_old_collector() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("peer-pid"))
        val hostPeer = peer("host-pid", "Host Device")
        val firstSession = FakeP2pSession(hostPeer)
        val replacementSession = FakeP2pSession(hostPeer)
        val generationOne = resumableCredential(generation = 1L)
        val generationTwo = resumableCredential(generation = 2L)
        val store = ResumableCredentialStore(testSecureStorage())
        store.stage(generationOne)
        store.commit(generationOne.offerId, generationOne.generation)
        val room = PeerP2pRoom(
            kit = kit,
            session = firstSession,
            hostPeer = hostPeer,
            roomCode = generationOne.roomCode,
            scope = testScope,
            codec = codec,
            initialCredential = generationOne,
            credentialStore = store,
            resumeConnector = {
                store.stage(generationTwo)
                store.commit(generationTwo.offerId, generationTwo.generation)
                Result.Success(
                    ResumedPeerConnection(
                        replacementSession,
                        hostPeer,
                        generationTwo,
                        hostPeer.name,
                    ),
                )
            },
        )
        val events = mutableListOf<PeerEvent>()
        val eventCollector = testScope.async { room.peerEvents.collect { events += it } }
        yield(); yield()

        firstSession.stateFlow.value = ConnectionState.Closed
        awaitCondition { events.contains(PeerEvent.HostRestored) }
        assertThat(store.loadResumeCandidate()).isEqualTo(Result.Success(generationTwo))

        // The replaced collector is generation-bound and cannot revoke G2.
        firstSession.incomingFlow.emit(P2pMessage.Binary(codec.encode(testTerminalMessage())))
        repeat(5) { yield() }
        assertThat(store.loadResumeCandidate()).isEqualTo(Result.Success(generationTwo))

        // The active replacement collector is bound to G2, so the validated
        // terminal commit revokes G2 before game/UI accepts the end.
        replacementSession.incomingFlow.emit(
            P2pMessage.Binary(codec.encode(testTerminalMessage())),
        )
        assertThat(withTimeout(2_000L) { room.incoming.first() })
            .isEqualTo(testTerminalMessage())
        assertThat(room.commitValidatedSessionEnd(testTerminalMessage()))
            .isEqualTo(Result.Success(SessionEndCommitStatus.Committed))
        assertThat(store.loadResumeCandidate()).isEqualTo(Result.Success(null))

        eventCollector.cancel()
        room.leave()
    }

    @Test
    fun successful_resume_that_loses_terminal_ownership_race_closes_unadopted_session() =
        runBlocking {
            val kit = FakeP2pKit(P2pPeerId("peer-pid"))
            val hostPeer = peer("host-pid", "Host Device")
            val firstSession = FakeP2pSession(hostPeer)
            val unadoptedSession = FakeP2pSession(hostPeer)
            val initialCredential = resumableCredential(generation = 1L)
            val rotatedCredential = initialCredential.copy(
                offerId = "resume-offer-2",
                secret = "b".repeat(64),
                generation = 2L,
            )
            val store = ResumableCredentialStore(testSecureStorage())
            store.stage(initialCredential)
            store.commit(initialCredential.offerId, initialCredential.generation)
            val room = PeerP2pRoom(
                kit = kit,
                session = firstSession,
                hostPeer = hostPeer,
                roomCode = initialCredential.roomCode,
                scope = testScope,
                codec = codec,
                initialCredential = initialCredential,
                credentialStore = store,
            )
            val terminal = testScope.async { room.incoming.first() }
            yield(); yield()

            // The connector has durably committed G2, but has not yet crossed
            // PeerP2pRoom's physical-session adoption boundary.
            store.stage(rotatedCredential)
            store.commit(rotatedCredential.offerId, rotatedCredential.generation)

            firstSession.incomingFlow.emit(
                P2pMessage.Binary(codec.encode(testTerminalMessage())),
            )
            assertThat(withTimeout(2_000L) { terminal.await() })
                .isEqualTo(testTerminalMessage())
            assertThat(room.commitValidatedSessionEnd(testTerminalMessage()))
                .isEqualTo(Result.Success(SessionEndCommitStatus.Committed))
            assertThat(store.loadResumeCandidate()).isEqualTo(Result.Success(null))

            // Model the exact post-connector race: Success is already in hand,
            // but authenticated terminal ownership committed first.
            assertThat(
                room.adoptResumedConnection(
                    ResumedPeerConnection(
                        unadoptedSession,
                        hostPeer,
                        rotatedCredential,
                        hostPeer.name,
                    ),
                ),
            ).isEqualTo(ResumeAdoptionOutcome.Terminal)
            assertThat(unadoptedSession.closeCalls).isEqualTo(1)
            assertThat(unadoptedSession.state.value).isEqualTo(ConnectionState.Closed)
            assertThat(store.loadResumeCandidate()).isEqualTo(Result.Success(null))
            assertThat(room.sendToHost(testPeerHeartbeat()))
                .isEqualTo(Result.Failure(NetError.NotConnected))

            room.leave()
            assertThat(kit.stopCalls).isEqualTo(1)
        }

    @Test
    fun final_leave_revokes_a_committed_rotation_that_has_not_been_adopted() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("peer-pid"))
        val hostPeer = peer("host-pid", "Host Device")
        val firstSession = FakeP2pSession(hostPeer)
        val unadoptedSession = FakeP2pSession(hostPeer)
        val generationOne = resumableCredential(generation = 1L)
        val generationTwo = generationOne.copy(
            offerId = "resume-offer-2",
            secret = "b".repeat(64),
            generation = 2L,
        )
        val store = ResumableCredentialStore(testSecureStorage())
        store.stage(generationOne)
        store.commit(generationOne.offerId, generationOne.generation)
        val room = PeerP2pRoom(
            kit = kit,
            session = firstSession,
            hostPeer = hostPeer,
            roomCode = generationOne.roomCode,
            scope = testScope,
            codec = codec,
            initialCredential = generationOne,
            credentialStore = store,
        )

        // Model Success already returned by the connector: G2 is durable, but
        // final Leave wins before adoptResumedConnection can install it.
        store.stage(generationTwo)
        store.commit(generationTwo.offerId, generationTwo.generation)
        room.leave()
        assertThat(store.loadResumeCandidate()).isEqualTo(Result.Success(null))

        assertThat(
            room.adoptResumedConnection(
                ResumedPeerConnection(
                    unadoptedSession,
                    hostPeer,
                    generationTwo,
                    hostPeer.name,
                ),
            ),
        ).isEqualTo(ResumeAdoptionOutcome.Terminal)
        assertThat(unadoptedSession.closeCalls).isEqualTo(1)
        assertThat(unadoptedSession.state.value).isEqualTo(ConnectionState.Closed)
        assertThat(store.loadResumeCandidate()).isEqualTo(Result.Success(null))
        assertThat(kit.stopCalls).isEqualTo(1)
    }

    @Test
    fun resume_ready_failure_or_cancellation_closes_the_adopted_physical_session() = runBlocking {
        suspend fun runPath(cancelSend: Boolean) {
            val kit = FakeP2pKit(P2pPeerId("peer-pid"))
            val hostPeer = peer("host-pid", "Host Device")
            val firstSession = FakeP2pSession(hostPeer)
            val replacementSession = FakeP2pSession(hostPeer).apply {
                sendHandler = {
                    if (cancelSend) {
                        throw CancellationException("cancel resume-ready handoff")
                    } else {
                        error("fail resume-ready handoff")
                    }
                }
            }
            val initialCredential = resumableCredential(generation = 1L)
            val rotatedCredential = resumableCredential(generation = 2L)
            val blockRetry = CompletableDeferred<Unit>()
            var attempts = 0
            val room = PeerP2pRoom(
                kit = kit,
                session = firstSession,
                hostPeer = hostPeer,
                roomCode = initialCredential.roomCode,
                scope = testScope,
                codec = codec,
                initialCredential = initialCredential,
                resumeConnector = {
                    attempts += 1
                    if (attempts == 1) {
                        Result.Success(
                            ResumedPeerConnection(
                                replacementSession,
                                hostPeer,
                                rotatedCredential,
                                hostPeer.name,
                            ),
                        )
                    } else {
                        blockRetry.await()
                        Result.Failure(ResumeConnectionFailure(NetError.Timeout))
                    }
                },
            )

            firstSession.stateFlow.value = ConnectionState.Closed

            awaitCondition { replacementSession.closeCalls == 1 }
            assertThat(replacementSession.state.value).isEqualTo(ConnectionState.Closed)
            room.leave()
            assertThat(kit.stopCalls).isEqualTo(1)
        }

        runPath(cancelSend = false)
        runPath(cancelSend = true)
    }

    @Test
    fun credential_store_failure_is_diagnostic_and_never_exports_credential_material() =
        runBlocking {
            val kit = FakeP2pKit(P2pPeerId("peer-pid"))
            val hostPeer = peer("host-pid", "Host Device")
            val session = FakeP2pSession(hostPeer)
            val credential = resumableCredential(generation = 1L)
            val failingStorage = FailingRemoveSecureStorage(testSecureStorage())
            val store = ResumableCredentialStore(failingStorage)
            store.stage(credential)
            store.commit(credential.offerId, credential.generation)
            failingStorage.failRemove = true
            val diagnostics = BoundedP2pDiagnostics(
                scope = testScope,
                writer = P2pDiagnosticWriter { _ -> },
                outputIntervalMillis = 0L,
            )
            val room = PeerP2pRoom(
                kit = kit,
                session = session,
                hostPeer = hostPeer,
                roomCode = credential.roomCode,
                scope = testScope,
                codec = codec,
                diagnostics = diagnostics,
                initialCredential = credential,
                credentialStore = store,
            )

            session.incomingFlow.emit(P2pMessage.Binary(codec.encode(testTerminalMessage())))
            assertThat(withTimeout(2_000L) { room.incoming.first() })
                .isEqualTo(testTerminalMessage())
            assertThat(room.commitValidatedSessionEnd(testTerminalMessage()))
                .isEqualTo(Result.Failure(NetError.SecureStorageUnavailable))

            assertThat(
                diagnostics.snapshot().any {
                    it.event.name == P2pDiagnosticEventName.CREDENTIAL_INVALIDATION_FAILED &&
                        it.event.role == P2pDiagnosticRole.PEER &&
                        it.event.result == P2pDiagnosticResult.FAILURE &&
                        it.event.reason == P2pDiagnosticReason.INTERNAL
                },
            ).isTrue()
            assertThat(store.loadResumeCandidate()).isEqualTo(Result.Success(credential))
            val exported = diagnostics.export()
            listOf(
                credential.secret,
                credential.offerId,
                credential.roomCode,
                credential.playerId,
                credential.hostPeerId,
                credential.hostFingerprint,
            ).forEach { sensitive ->
                assertThat(exported.contains(sensitive)).isFalse()
            }

            room.leave()
        }

    @Test
    fun explicit_final_leave_reports_secure_delete_failure_and_can_retry_after_close() =
        runBlocking {
            val kit = FakeP2pKit(P2pPeerId("peer-pid"))
            val hostPeer = peer("host-pid", "Host Device")
            val session = FakeP2pSession(hostPeer)
            val credential = resumableCredential(generation = 1L)
            val failingStorage = FailingRemoveSecureStorage(testSecureStorage())
            val store = ResumableCredentialStore(failingStorage)
            store.stage(credential)
            store.commit(credential.offerId, credential.generation)
            failingStorage.failRemove = true
            val room = PeerP2pRoom(
                kit = kit,
                session = session,
                hostPeer = hostPeer,
                roomCode = credential.roomCode,
                scope = testScope,
                codec = codec,
                initialCredential = credential,
                credentialStore = store,
            )

            assertThat(room.finalLeave())
                .isEqualTo(Result.Failure(NetError.SecureStorageUnavailable))
            assertThat(session.state.value).isEqualTo(ConnectionState.Closed)
            assertThat(kit.stopCalls).isEqualTo(1)
            assertThat(store.loadResumeCandidate()).isEqualTo(Result.Success(credential))

            failingStorage.failRemove = false

            assertThat(room.finalLeave()).isEqualTo(Result.Success(Unit))
            assertThat(store.loadResumeCandidate()).isEqualTo(Result.Success(null))
            assertThat(kit.stopCalls).isEqualTo(1)
        }

    @Test
    fun cancellation_from_credential_invalidation_propagates_after_room_cleanup() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("peer-pid"))
        val hostPeer = peer("host-pid", "Host Device")
        val session = FakeP2pSession(hostPeer)
        val credential = resumableCredential(generation = 1L)
        val cancellingStorage = FailingRemoveSecureStorage(testSecureStorage())
        val store = ResumableCredentialStore(cancellingStorage)
        store.stage(credential)
        store.commit(credential.offerId, credential.generation)
        cancellingStorage.cancelRemove = true
        val room = PeerP2pRoom(
            kit = kit,
            session = session,
            hostPeer = hostPeer,
            roomCode = credential.roomCode,
            scope = testScope,
            codec = codec,
            initialCredential = credential,
            credentialStore = store,
        )

        assertFailsWith<CancellationException> { room.leave() }

        assertThat(session.state.value).isEqualTo(ConnectionState.Closed)
        assertThat(kit.stopCalls).isEqualTo(1)
        assertThat(room.lifecycle.value).isEqualTo(RoomLifecycleState.Closed)
    }

    @Test
    fun peer_emits_host_lost_on_terminal_session_close() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("peer-pid"))
        val hostPeer = peer("host-pid", "Host Device")
        val session = FakeP2pSession(hostPeer)
        val room = PeerP2pRoom(kit, session, hostPeer, "ABCDEF", testScope, codec)
        val events = mutableListOf<PeerEvent>()
        val collector = testScope.async { room.peerEvents.collect { events += it } }
        yield()

        session.stateFlow.value = ConnectionState.Closed
        awaitCondition { events.contains(PeerEvent.HostLost) }
        assertThat(room.info.value.status).isEqualTo(RoomInfo.Status.Lost)
        collector.cancel()
    }

    @Test
    fun peer_send_to_host_returns_not_connected_after_session_closes() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("peer-pid"))
        val hostPeer = peer("host-pid", "Host Device")
        val session = FakeP2pSession(hostPeer)
        val room = PeerP2pRoom(kit, session, hostPeer, "ABCDEF", testScope, codec)
        yield()

        // While Connected: send succeeds.
        val before = room.sendToHost(testPeerHeartbeat())
        assertThat(before).isInstanceOf(Result.Success::class)

        session.stateFlow.value = ConnectionState.Closed
        yield(); yield()

        val after = room.sendToHost(testPeerHeartbeat())
        assertThat(after).isInstanceOf(Result.Failure::class)
        assertThat((after as Result.Failure).error).isEqualTo(NetError.NotConnected)
    }

    // ------------------------------------------------------- LeaveNotice ----

    /**
     * Phase C, Issue 2: peer's explicit "I'm leaving" must update the
     * host lobby immediately, instead of falling back to the TCP-teardown
     * path (which can lag by seconds on flaky LANs). The wire signal is
     * [PeerMessage.LeaveNotice] — defined long ago in the protocol but
     * never actually wired through the transport until this fix.
     *
     * The host must also NOT forward the LeaveNotice into `room.incoming`:
     * it is transport plumbing, not a game-layer message, and would surface
     * as a foreign `RoomMessage` to whichever game module is collecting.
     */
    @Test
    fun host_emits_peer_left_when_peer_sends_leave_notice() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("host-pid"))
        val room = newHostRoom(kit)
        val events = mutableListOf<PeerEvent>()
        val forwardedMessages = mutableListOf<RoomMessage>()
        val eventsCollector = testScope.async { room.peerEvents.collect { events += it } }
        val incomingCollector = testScope.async { room.incoming.collect { forwardedMessages += it } }
        yield(); yield()

        val alice = FakeP2pSession(peer("alice-pid", "Alice"))
        admit(room, kit, alice)

        // Peer-side simulation: the JSON-encoded LeaveNotice arrives on
        // the session's incoming binary channel.
        val noticeBytes = codec.encode(PeerMessage.LeaveNotice)
        alice.incomingFlow.emit(P2pMessage.Binary(noticeBytes))

        awaitCondition { events.any { it is PeerEvent.PeerLeft } }
        assertThat(room.members.value).isEqualTo(emptyList())
        val left = events.filterIsInstance<PeerEvent.PeerLeft>().single()
        assertThat(left.playerId).isEqualTo(PlayerId("alice-pid"))
        assertThat(left.displayName).isEqualTo("Alice")
        // Critical: LeaveNotice is transport plumbing — it must NOT leak
        // into the game-layer message stream.
        assertThat(forwardedMessages.filterIsInstance<PeerMessage.LeaveNotice>())
            .isEmpty()

        eventsCollector.cancel()
        incomingCollector.cancel()
    }

    /**
     * Phase C, Issue 2: when a peer calls `leave()`, it must send the
     * [PeerMessage.LeaveNotice] BEFORE closing the session, so the host
     * receives the application-level signal before the TCP socket dies.
     * The order is asserted by inspecting `session.sent`: at least one
     * Binary frame must decode to LeaveNotice, and after `leave()` the
     * session must end up Closed (so the kit stop path runs cleanly).
     */
    @Test
    fun peer_sends_leave_notice_before_closing_session_on_leave() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("peer-pid"))
        val hostPeer = peer("host-pid", "Host Device")
        val session = FakeP2pSession(hostPeer)
        val room = PeerP2pRoom(kit, session, hostPeer, "ABCDEF", testScope, codec)
        yield()

        room.leave()

        val decoded: List<RoomMessage> = session.sent
            .filterIsInstance<P2pMessage.Binary>()
            .map { codec.decode(it.bytes) }
        assertThat(decoded).contains(PeerMessage.LeaveNotice)
        assertThat(session.stateFlow.value).isEqualTo(ConnectionState.Closed)
        // And the kit was stopped on the way out.
        assertThat(kit.stopCalls).isEqualTo(1)
    }

    /**
     * Phase C, Issue 1: when the host leaves the room, P2pKit must stop
     * advertising *before* the kit itself is stopped, so the Bonjour
     * "service-removed" packet can be flushed onto the wire. Without this
     * ordering, remote peers can keep showing the dead room in their join
     * lobby for the full Bonjour eviction window (5–30s on iOS).
     */
    @Test
    fun host_leave_stops_advertising_before_stopping_kit() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("host-pid"))
        val room = newHostRoom(kit)
        yield()

        room.leave()

        assertThat(kit.stopAdvertisingCalls).isEqualTo(1)
        assertThat(kit.stopCalls).isEqualTo(1)
        val stopAdvIdx = kit.callLog.indexOf("stopAdvertising")
        val stopIdx = kit.callLog.indexOf("stop")
        // Both calls were recorded.
        assertThat(stopAdvIdx >= 0).isTrue()
        assertThat(stopIdx >= 0).isTrue()
        // The actual ordering invariant: advertise stops first.
        assertThat(stopAdvIdx < stopIdx).isTrue()
    }

    /**
     * Phase C, Issue 1: a bogus room code (no matching advertisement on
     * the LAN) must fail with [NetError.Timeout] in bounded time, instead
     * of hanging forever waiting for the kit's peer flow to produce a
     * match. The bounded budget is `joinTimeoutMs` (test overrides it to
     * 200 ms to keep the suite fast).
     */
    @Test
    fun join_times_out_when_no_matching_room_appears() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("self-pid"))
        val factory = object : P2pKitFactory {
            override suspend fun createKit(appId: AppId, deviceName: String): P2pKit = kit
        }
        val transport = P2pKitRoomTransport(
            appId = AppId("com.parlor.test"),
            deviceName = "self-device",
            scope = testScope,
            kitFactory = factory,
            joinTimeoutMs = 200L,
        )
        // peers list deliberately stays empty for the entire join attempt.
        val result = transport.join("ZZZZZZ", "Alice")

        assertThat(result).isInstanceOf(Result.Failure::class)
        assertThat((result as Result.Failure).error).isEqualTo(NetError.Timeout)
        assertThat(transport.localNetworkAccess.value)
            .isEqualTo(LocalNetworkAccess.FailureUnclassified)
        // Kit must be cleaned up on timeout — otherwise an abandoned
        // join leaks a discovering instance.
        assertThat(kit.stopCalls).isEqualTo(1)
    }

    @Test
    fun invalid_room_code_or_display_name_is_rejected_before_kit_initialization() = runBlocking {
        var createCalls = 0
        val transport = P2pKitRoomTransport(
            appId = AppId("com.parlor.test"),
            deviceName = "self-device",
            scope = testScope,
            kitFactory = object : P2pKitFactory {
                override suspend fun createKit(appId: AppId, deviceName: String): P2pKit {
                    createCalls += 1
                    return FakeP2pKit(P2pPeerId("unused"))
                }
            },
        )

        listOf(
            transport.join("ABCDE", "Alice"),
            transport.join("ABC0DE", "Alice"),
            transport.join("ABCDEF", "Alice\u202EAdmin"),
            transport.host(HostConfig(hostDisplayName = "Host\nAdmin")),
        ).forEach { result ->
            assertThat(result).isEqualTo(Result.Failure(NetError.InvalidInput))
        }
        assertThat(createCalls).isEqualTo(0)
        assertThat(transport.localNetworkAccess.value).isEqualTo(LocalNetworkAccess.Unknown)
    }

    @Test
    fun cancelling_discovery_aborts_immediately_and_stops_the_kit() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("self-pid"))
        val transport = P2pKitRoomTransport(
            appId = AppId("com.parlor.test"),
            deviceName = "self-device",
            scope = testScope,
            kitFactory = object : P2pKitFactory {
                override suspend fun createKit(appId: AppId, deviceName: String): P2pKit = kit
            },
        )

        val joining = async { transport.join("ABCDEF", "Alice") }
        awaitCondition { kit.callLog.contains("startDiscovery") }
        joining.cancel(CancellationException("user aborted join"))

        assertFailsWith<CancellationException> { joining.await() }
        awaitCondition { kit.stopCalls == 1 }
    }

    @Test
    fun cancelling_an_in_flight_candidate_dial_propagates_and_stops_the_kit() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("self-pid"))
        val hostPeer = peer(
            "host-pid",
            "${P2pKitRoomTransport.P2P_ROOM_PREFIX}Host",
        )
        val dialStarted = CompletableDeferred<Unit>()
        val dialCancelled = CompletableDeferred<Unit>()
        val neverCompletes = CompletableDeferred<Unit>()
        kit.connectHandler = {
            dialStarted.complete(Unit)
            try {
                neverCompletes.await()
                error("unreachable")
            } finally {
                dialCancelled.complete(Unit)
            }
        }
        kit.peersFlow.value = listOf(hostPeer)
        val transport = P2pKitRoomTransport(
            appId = AppId("com.parlor.test"),
            deviceName = "self-device",
            scope = testScope,
            kitFactory = object : P2pKitFactory {
                override suspend fun createKit(appId: AppId, deviceName: String): P2pKit = kit
            },
        )

        val joining = async { transport.join("ABCDEF", "Alice") }
        withTimeout(2_000L) { dialStarted.await() }
        joining.cancel(CancellationException("user aborted candidate dial"))

        assertFailsWith<CancellationException> { joining.await() }
        withTimeout(2_000L) { dialCancelled.await() }
        awaitCondition { kit.stopCalls == 1 }
    }

    /**
     * Phase C, Issue 1: even if a Peer entry whose name matches the room
     * code appears in `kit.peers`, the join path must reject it when its
     * `lastSeen` is older than the freshness window. Otherwise stale
     * Bonjour leftovers (a common iOS artifact after the host disappears
     * without a clean goodbye) cause join() to connect to a ghost host
     * that no longer exists.
     *
     * The test sets `lastSeen` an hour into the past and asserts the
     * join attempt times out, never invokes `connect`, and tears the
     * kit back down. (`connectHandler` is left null; if the freshness
     * gate fails and connect() is reached, the default `error("not
     * exercised")` would surface as a `TransportFailure`, which the
     * Timeout assertion below would catch.)
     */
    @Test
    fun join_rejects_stale_peer_whose_last_seen_is_too_old() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("self-pid"))
        val factory = object : P2pKitFactory {
            override suspend fun createKit(appId: AppId, deviceName: String): P2pKit = kit
        }
        val transport = P2pKitRoomTransport(
            appId = AppId("com.parlor.test"),
            deviceName = "self-device",
            scope = testScope,
            kitFactory = factory,
            joinTimeoutMs = 200L,
        )
        // Name matches the room-code prefix — this is the gate-bypass
        // attempt — but lastSeen is stale, so the production freshness
        // check must drop it.
        val staleHost = peer(
            id = "ghost-host-pid",
            name = "${P2pKitRoomTransport.P2P_ROOM_PREFIX}ABCDEF|Ghost Host",
        )
        val nowMs = kotlin.time.Clock.System.now().toEpochMilliseconds()
        kit.lastSeenByPeer[staleHost.id] = nowMs - 3_600_000L
        kit.peersFlow.value = listOf(staleHost)

        val result = transport.join("ABCDEF", "Alice")

        assertThat(result).isInstanceOf(Result.Failure::class)
        assertThat((result as Result.Failure).error).isEqualTo(NetError.Timeout)
        assertThat(kit.stopCalls).isEqualTo(1)
    }

    /**
     * Issue 3 follow-up: the freshness gate must NOT reject a peer
     * whose `lastSeen` is `null`. Some P2pKit platforms — notably the
     * current Android adapter on the diag/issue-21-android-discovery-trace
     * branch — emit peers via `kit.peers` without populating per-peer
     * timestamps. Rejecting null-lastSeen unconditionally blocks every
     * Android-side join even when discovery is otherwise healthy: the
     * Bonjour-goodbye ghost scenario this gate was added to defend
     * against shows up as an *explicitly old* timestamp, not a missing
     * one. The fix is to treat "no timestamp" as "trust the emission."
     */
    @Test
    fun join_accepts_peer_with_null_last_seen_when_name_matches_room_code() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("self-pid"))
        val factory = object : P2pKitFactory {
            override suspend fun createKit(appId: AppId, deviceName: String): P2pKit = kit
        }
        val transport = P2pKitRoomTransport(
            appId = AppId("com.parlor.test"),
            deviceName = "self-device",
            scope = testScope,
            kitFactory = factory,
            secureStorage = testSecureStorage(),
            joinTimeoutMs = 2_000L,
        )

        val hostPeer = peer(
            id = "live-host-pid",
            name = "${P2pKitRoomTransport.P2P_ROOM_PREFIX}Live Host",
        )
        // Deliberately leave lastSeenByPeer unset for hostPeer.id, so
        // kit.lastSeen(hostPeer.id) returns null — this is the Android
        // adapter's current behavior.
        val fakeSession = FakeP2pSession(hostPeer)
        val offer = testCredentialOffer(
            playerId = PlayerId("self-pid"),
            hostPeerId = hostPeer.id.value,
        )
        fakeSession.sendHandler = { message ->
            val request = (message as? P2pMessage.Binary)
                ?.let { codec.decode(it.bytes) }
            when (request) {
                is PeerMessage.AdmissionRequest -> fakeSession.incomingFlow.emit(
                    P2pMessage.Binary(
                        codec.encode(HostMessage.AdmissionOffered(offer, "Host Alice")),
                    ),
                )
                is PeerMessage.AdmissionConfirmed -> fakeSession.incomingFlow.emit(
                    P2pMessage.Binary(
                        codec.encode(
                            HostMessage.AdmissionCommitted(
                                playerId = offer.playerId,
                                offerId = offer.offerId,
                                generation = offer.generation,
                            ),
                        ),
                    ),
                )
                else -> Unit
            }
        }
        kit.connectHandler = { fakeSession }
        kit.peersFlow.value = listOf(hostPeer)

        val result = transport.join("ABCDEF", "Alice")

        assertThat(result).isInstanceOf(Result.Success::class)
        val joinedRoom = (result as Result.Success).data
        assertThat(joinedRoom.info.value.hostDisplayName).isEqualTo("Host Alice")
        assertThat(joinedRoom.members.value.single().displayName).isEqualTo("Host Alice")
        // And connect() actually ran (proven by the session being the
        // one we configured — the default handler would have thrown).
        assertThat(fakeSession.state.value).isEqualTo(ConnectionState.Connected)
    }

    @Test
    fun process_recreation_resumes_from_protected_storage_with_pinned_identity_and_ready_handoff() =
        runBlocking {
            val backing = InMemorySecureKeyValueBacking()
            val secureStorage = PlatformKeyedSecureStorage(backing)
            val hostPeer = peer(
                id = "live-host-pid",
                name = "${P2pKitRoomTransport.P2P_ROOM_PREFIX}Live Host",
            )
            val initialKit = FakeP2pKit(P2pPeerId("self-pid"))
            val initialSession = FakeP2pSession(hostPeer)
            val initialOffer = testCredentialOffer(PlayerId("self-pid"), hostPeer.id.value)
            initialSession.sendHandler = { message ->
                when (
                    val request = (message as? P2pMessage.Binary)
                        ?.let { codec.decode(it.bytes) }
                ) {
                    is PeerMessage.AdmissionRequest -> initialSession.incomingFlow.emit(
                        P2pMessage.Binary(
                            codec.encode(
                                HostMessage.AdmissionOffered(initialOffer, "Host Alice"),
                            ),
                        ),
                    )
                    is PeerMessage.AdmissionConfirmed -> initialSession.incomingFlow.emit(
                        P2pMessage.Binary(
                            codec.encode(
                                HostMessage.AdmissionCommitted(
                                    initialOffer.playerId,
                                    initialOffer.offerId,
                                    initialOffer.generation,
                                ),
                            ),
                        ),
                    )
                    else -> Unit
                }
            }
            initialKit.connectHandler = { initialSession }
            initialKit.peersFlow.value = listOf(hostPeer)
            val initialTransport = P2pKitRoomTransport(
                appId = AppId("com.parlor.test"),
                deviceName = "self-device",
                scope = testScope,
                kitFactory = object : P2pKitFactory {
                    override suspend fun createKit(appId: AppId, deviceName: String): P2pKit =
                        initialKit
                },
                secureStorage = secureStorage,
                joinTimeoutMs = 2_000L,
            )
            assertThat(initialTransport.join("ABCDEF", "Alice"))
                .isInstanceOf(Result.Success::class)

            // New transport + kit model a fresh process; only the platform
            // secure backing and P2pKit identity survive.
            val relaunchedKit = FakeP2pKit(P2pPeerId("self-pid"))
            val replacementSession = FakeP2pSession(hostPeer)
            val rotatedOffer = testCredentialOffer(
                PlayerId("self-pid"),
                hostPeer.id.value,
                generation = 2L,
            )
            val resumeRequests = mutableListOf<PeerMessage.ResumeRequested>()
            replacementSession.sendHandler = { message ->
                when (
                    val request = (message as? P2pMessage.Binary)
                        ?.let { codec.decode(it.bytes) }
                ) {
                    is PeerMessage.ResumeRequested -> {
                        resumeRequests += request
                        replacementSession.incomingFlow.emit(
                            P2pMessage.Binary(
                                codec.encode(
                                    HostMessage.ResumeOffered(rotatedOffer, "Host Alice"),
                                ),
                            ),
                        )
                    }
                    is PeerMessage.ResumeConfirmed -> replacementSession.incomingFlow.emit(
                        P2pMessage.Binary(
                            codec.encode(
                                HostMessage.ResumeCommitted(
                                    rotatedOffer.playerId,
                                    rotatedOffer.offerId,
                                    rotatedOffer.generation,
                                ),
                            ),
                        ),
                    )
                    is PeerMessage.ResumeReady -> replacementSession.incomingFlow.emit(
                        P2pMessage.Binary(codec.encode(testTerminalMessage())),
                    )
                    else -> Unit
                }
            }
            relaunchedKit.connectHandler = { replacementSession }
            relaunchedKit.peersFlow.value = listOf(hostPeer)
            val relaunchedTransport = P2pKitRoomTransport(
                appId = AppId("com.parlor.test"),
                deviceName = "ignored-after-resume",
                scope = testScope,
                kitFactory = object : P2pKitFactory {
                    override suspend fun createKit(appId: AppId, deviceName: String): P2pKit =
                        relaunchedKit
                },
                secureStorage = PlatformKeyedSecureStorage(backing),
                joinTimeoutMs = 2_000L,
            )

            val summary = relaunchedTransport.resumableSession()
            assertThat(summary).isInstanceOf(Result.Success::class)
            assertThat((summary as Result.Success).data?.gameId).isEqualTo(GameId("whodunit"))
            val resumed = relaunchedTransport.resumeLastSession()
            assertThat(resumed).isInstanceOf(Result.Success::class)
            val room = (resumed as Result.Success).data
            assertThat(room.info.value.hostDisplayName).isEqualTo("Host Alice")
            assertThat(room.members.value.single().displayName).isEqualTo("Host Alice")
            assertThat(resumeRequests).hasSize(1)
            assertThat(resumeRequests.single().secret).isEqualTo(initialOffer.secret)
            assertThat(resumeRequests.single().generation).isEqualTo(1L)
            assertThat(relaunchedKit.lastExpectedFingerprint).isEqualTo(TEST_PEER_FINGERPRINT)
            assertThat(room.rejoinToken).isEqualTo(null)
            assertThat(room.incoming.first()).isEqualTo(testTerminalMessage())
            assertThat(room.commitValidatedSessionEnd(testTerminalMessage()))
                .isEqualTo(Result.Success(SessionEndCommitStatus.Committed))

            room.leave()
            assertThat(relaunchedTransport.resumableSession())
                .isEqualTo(Result.Success(null))
        }

    @Test
    fun process_resume_rejects_a_host_session_presenting_a_different_fingerprint() = runBlocking {
        val secureStorage = testSecureStorage()
        val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
        val credential = resumableCredential(generation = 1L).copy(
            issuedAtEpochMillis = now - 1_000L,
            expiresAtEpochMillis = now + 60_000L,
        )
        val store = ResumableCredentialStore(secureStorage)
        store.stage(credential)
        store.commit(credential.offerId, credential.generation)
        val hostPeer = peer(
            credential.hostPeerId,
            "${P2pKitRoomTransport.P2P_ROOM_PREFIX}Pinned Host",
        )
        val mismatchedSession = FakeP2pSession(
            peer = hostPeer,
            peerIdentity = PeerIdentity(hostPeer.id, TEST_OTHER_PEER_FINGERPRINT),
        )
        val kit = FakeP2pKit(P2pPeerId(credential.playerId)).apply {
            peersFlow.value = listOf(hostPeer)
            connectHandler = { mismatchedSession }
        }
        val transport = P2pKitRoomTransport(
            appId = AppId("com.parlor.test"),
            deviceName = "self-device",
            scope = testScope,
            kitFactory = object : P2pKitFactory {
                override suspend fun createKit(appId: AppId, deviceName: String): P2pKit = kit
            },
            secureStorage = secureStorage,
        )

        assertThat(transport.resumeLastSession())
            .isEqualTo(Result.Failure(NetError.Unauthorized))
        assertThat(kit.lastExpectedFingerprint).isEqualTo(TEST_PEER_FINGERPRINT)
        assertThat(mismatchedSession.sent).isEmpty()
        assertThat(mismatchedSession.closeCalls).isEqualTo(1)
        assertThat(store.loadResumeCandidate()).isEqualTo(Result.Success(credential))
        assertThat(kit.stopCalls).isEqualTo(1)
    }

    @Test
    fun process_recreation_prefers_a_pending_different_room_that_the_host_may_have_committed() =
        runBlocking {
            val secureStorage = testSecureStorage()
            val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
            val oldCredential = resumableCredential(
                generation = 1L,
                offerId = "old-room-offer",
                roomCode = "ABCDEF",
                playerId = "old-player-pid",
                hostPeerId = "old-host-pid",
            ).copy(
                issuedAtEpochMillis = now,
                expiresAtEpochMillis = now + 86_400_000L,
            )
            val pendingNewRoom = resumableCredential(
                generation = 1L,
                offerId = "new-room-offer",
                roomCode = "XYZ789",
                playerId = "new-player-pid",
                hostPeerId = "new-host-pid",
            ).copy(
                secret = "c".repeat(64),
                issuedAtEpochMillis = now,
                expiresAtEpochMillis = now + 86_400_000L,
            )
            val credentials = ResumableCredentialStore(secureStorage)
            credentials.stage(oldCredential)
            credentials.commit(oldCredential.offerId, oldCredential.generation)
            credentials.stage(pendingNewRoom)

            val hostPeer = peer(
                id = pendingNewRoom.hostPeerId,
                name = "${P2pKitRoomTransport.P2P_ROOM_PREFIX}New Host",
            )
            val session = FakeP2pSession(hostPeer)
            val rotatedOffer = testCredentialOffer(
                playerId = PlayerId(pendingNewRoom.playerId),
                hostPeerId = pendingNewRoom.hostPeerId,
                generation = 2L,
            )
            val requests = mutableListOf<PeerMessage.ResumeRequested>()
            session.sendHandler = { message ->
                when (
                    val request = (message as? P2pMessage.Binary)
                        ?.let { codec.decode(it.bytes) }
                ) {
                    is PeerMessage.ResumeRequested -> {
                        requests += request
                        session.incomingFlow.emit(
                            P2pMessage.Binary(
                                codec.encode(
                                    HostMessage.ResumeOffered(rotatedOffer, "Host Alice"),
                                ),
                            ),
                        )
                    }
                    is PeerMessage.ResumeConfirmed -> session.incomingFlow.emit(
                        P2pMessage.Binary(
                            codec.encode(
                                HostMessage.ResumeCommitted(
                                    rotatedOffer.playerId,
                                    rotatedOffer.offerId,
                                    rotatedOffer.generation,
                                ),
                            ),
                        ),
                    )
                    is PeerMessage.ResumeReady -> Unit
                    else -> Unit
                }
            }
            val kit = FakeP2pKit(P2pPeerId(pendingNewRoom.playerId)).apply {
                connectHandler = { candidate ->
                    check(candidate.id.value == pendingNewRoom.hostPeerId)
                    session
                }
                peersFlow.value = listOf(hostPeer)
            }
            val transport = P2pKitRoomTransport(
                appId = AppId("com.parlor.test"),
                deviceName = "ignored-after-resume",
                scope = testScope,
                kitFactory = object : P2pKitFactory {
                    override suspend fun createKit(appId: AppId, deviceName: String): P2pKit = kit
                },
                secureStorage = secureStorage,
                joinTimeoutMs = 2_000L,
            )

            val resumed = transport.resumeLastSession()

            assertThat(resumed).isInstanceOf(Result.Success::class)
            assertThat(requests).hasSize(1)
            assertThat(requests.single().roomCode).isEqualTo(pendingNewRoom.roomCode)
            assertThat(requests.single().secret).isEqualTo(pendingNewRoom.secret)
            assertThat((credentials.loadResumeCandidate() as Result.Success).data?.membershipId)
                .isEqualTo(pendingNewRoom.membershipId)
            (resumed as Result.Success).data.leave()
        }

    @Test
    fun expired_process_credential_is_conditionally_invalidated_before_resume_is_offered() =
        runBlocking {
            val secureStorage = testSecureStorage()
            val expired = resumableCredential(generation = 1L)
            val store = ResumableCredentialStore(secureStorage)
            store.stage(expired)
            store.commit(expired.offerId, expired.generation)
            val kit = FakeP2pKit(P2pPeerId("peer-pid"))
            val transport = P2pKitRoomTransport(
                appId = AppId("com.parlor.test"),
                deviceName = "self-device",
                scope = testScope,
                kitFactory = object : P2pKitFactory {
                    override suspend fun createKit(appId: AppId, deviceName: String): P2pKit = kit
                },
                secureStorage = secureStorage,
            )

            assertThat(transport.resumableSession()).isEqualTo(Result.Success(null))
            assertThat(store.loadResumeCandidate()).isEqualTo(Result.Success(null))
            store.stage(expired)
            store.commit(expired.offerId, expired.generation)
            assertThat(transport.resumeLastSession())
                .isEqualTo(Result.Failure(NetError.RejoinExpired))
            assertThat(store.loadResumeCandidate()).isEqualTo(Result.Success(null))
            assertThat(kit.stopCalls).isEqualTo(0)
        }

    @Test
    fun expired_process_credential_invalidation_failure_is_explicit_and_redacted() = runBlocking {
        val failingStorage = FailingRemoveSecureStorage(testSecureStorage())
        val expired = resumableCredential(generation = 1L)
        val store = ResumableCredentialStore(failingStorage)
        store.stage(expired)
        store.commit(expired.offerId, expired.generation)
        failingStorage.failRemove = true
        val diagnostics = RecordingP2pDiagnostics()
        val transport = P2pKitRoomTransport(
            appId = AppId("com.parlor.test"),
            deviceName = "self-device",
            scope = testScope,
            kitFactory = object : P2pKitFactory {
                override suspend fun createKit(appId: AppId, deviceName: String): P2pKit =
                    FakeP2pKit(P2pPeerId("peer-pid"))
            },
            diagnostics = diagnostics,
            secureStorage = failingStorage,
        )

        assertThat(transport.resumableSession())
            .isEqualTo(Result.Failure(NetError.SecureStorageUnavailable))
        assertThat(
            diagnostics.events.any {
                it.name == P2pDiagnosticEventName.CREDENTIAL_INVALIDATION_FAILED &&
                    it.role == P2pDiagnosticRole.PEER &&
                    it.result == P2pDiagnosticResult.FAILURE &&
                    it.reason == P2pDiagnosticReason.INTERNAL
            },
        ).isTrue()
        listOf(
            expired.secret,
            expired.offerId,
            expired.roomCode,
            expired.playerId,
            expired.hostPeerId,
            expired.hostFingerprint,
        ).forEach { sensitive ->
            assertThat(diagnostics.export().contains(sensitive)).isFalse()
        }
    }

    @Test
    fun authenticated_permanent_resume_rejection_invalidates_matching_credential() = runBlocking {
        val secureStorage = testSecureStorage()
        val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
        val credential = resumableCredential(generation = 1L).copy(
            issuedAtEpochMillis = now - 1_000L,
            expiresAtEpochMillis = now + 60_000L,
        )
        val store = ResumableCredentialStore(secureStorage)
        store.stage(credential)
        store.commit(credential.offerId, credential.generation)
        val hostPeer = peer(
            credential.hostPeerId,
            "${P2pKitRoomTransport.P2P_ROOM_PREFIX}Pinned Host",
        )
        val session = FakeP2pSession(hostPeer).apply {
            sendHandler = { message ->
                val decoded = (message as? P2pMessage.Binary)?.let { codec.decode(it.bytes) }
                if (decoded is PeerMessage.ResumeRequested) {
                    incomingFlow.emit(
                        P2pMessage.Binary(
                            codec.encode(
                                HostMessage.AdmissionRejected(
                                    AdmissionRejection.ExpiredCredential,
                                ),
                            ),
                        ),
                    )
                }
            }
        }
        val kit = FakeP2pKit(P2pPeerId(credential.playerId)).apply {
            peersFlow.value = listOf(hostPeer)
            connectHandler = { session }
        }
        val transport = P2pKitRoomTransport(
            appId = AppId("com.parlor.test"),
            deviceName = "self-device",
            scope = testScope,
            kitFactory = object : P2pKitFactory {
                override suspend fun createKit(appId: AppId, deviceName: String): P2pKit = kit
            },
            secureStorage = secureStorage,
        )

        assertThat(transport.resumeLastSession())
            .isEqualTo(Result.Failure(NetError.RejoinExpired))
        assertThat(store.loadResumeCandidate()).isEqualTo(Result.Success(null))
        assertThat(kit.stopCalls).isEqualTo(1)
    }

    @Test
    fun stale_permanent_rejection_cannot_erase_a_concurrently_rotated_generation() = runBlocking {
        val secureStorage = testSecureStorage()
        val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
        val generationOne = resumableCredential(generation = 1L).copy(
            issuedAtEpochMillis = now - 2_000L,
            expiresAtEpochMillis = now + 60_000L,
        )
        val generationTwo = resumableCredential(generation = 2L).copy(
            issuedAtEpochMillis = now - 1_000L,
            expiresAtEpochMillis = now + 120_000L,
        )
        val store = ResumableCredentialStore(secureStorage)
        store.stage(generationOne)
        store.commit(generationOne.offerId, generationOne.generation)
        val hostPeer = peer(
            generationOne.hostPeerId,
            "${P2pKitRoomTransport.P2P_ROOM_PREFIX}Pinned Host",
        )
        val session = FakeP2pSession(hostPeer).apply {
            sendHandler = { message ->
                val decoded = (message as? P2pMessage.Binary)?.let { codec.decode(it.bytes) }
                if (decoded is PeerMessage.ResumeRequested) {
                    store.stage(generationTwo)
                    store.commit(generationTwo.offerId, generationTwo.generation)
                    incomingFlow.emit(
                        P2pMessage.Binary(
                            codec.encode(
                                HostMessage.AdmissionRejected(
                                    AdmissionRejection.InvalidCredential,
                                ),
                            ),
                        ),
                    )
                }
            }
        }
        val kit = FakeP2pKit(P2pPeerId(generationOne.playerId)).apply {
            peersFlow.value = listOf(hostPeer)
            connectHandler = { session }
        }
        val transport = P2pKitRoomTransport(
            appId = AppId("com.parlor.test"),
            deviceName = "self-device",
            scope = testScope,
            kitFactory = object : P2pKitFactory {
                override suspend fun createKit(appId: AppId, deviceName: String): P2pKit = kit
            },
            secureStorage = secureStorage,
        )

        assertThat(transport.resumeLastSession())
            .isEqualTo(Result.Failure(NetError.Unauthorized))
        assertThat(store.loadResumeCandidate()).isEqualTo(Result.Success(generationTwo))
        assertThat(kit.stopCalls).isEqualTo(1)
    }

    @Test
    fun transient_process_resume_failure_retains_exact_credential() = runBlocking {
        val secureStorage = testSecureStorage()
        val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
        val credential = resumableCredential(generation = 1L).copy(
            issuedAtEpochMillis = now - 1_000L,
            expiresAtEpochMillis = now + 60_000L,
        )
        val store = ResumableCredentialStore(secureStorage)
        store.stage(credential)
        store.commit(credential.offerId, credential.generation)
        val kit = FakeP2pKit(P2pPeerId(credential.playerId)).apply {
            startDiscoveryHandler = { error("transient discovery failure") }
        }
        val transport = P2pKitRoomTransport(
            appId = AppId("com.parlor.test"),
            deviceName = "self-device",
            scope = testScope,
            kitFactory = object : P2pKitFactory {
                override suspend fun createKit(appId: AppId, deviceName: String): P2pKit = kit
            },
            secureStorage = secureStorage,
        )

        assertThat(transport.resumeLastSession()).isEqualTo(
            Result.Failure(NetError.TransportFailure("transient discovery failure")),
        )
        assertThat(store.loadResumeCandidate()).isEqualTo(Result.Success(credential))
        assertThat(kit.stopCalls).isEqualTo(1)
    }

    @Test
    fun explicit_cold_resume_discard_revokes_the_stored_membership() = runBlocking {
        val secureStorage = testSecureStorage()
        val credential = resumableCredential(generation = 1L)
        val pendingRotation = resumableCredential(generation = 2L)
        val store = ResumableCredentialStore(secureStorage)
        store.stage(credential)
        store.commit(credential.offerId, credential.generation)
        store.stage(pendingRotation)
        val transport = P2pKitRoomTransport(
            appId = AppId("com.parlor.test"),
            deviceName = "self-device",
            scope = testScope,
            kitFactory = object : P2pKitFactory {
                override suspend fun createKit(appId: AppId, deviceName: String): P2pKit =
                    error("discard must not start P2pKit")
            },
            secureStorage = secureStorage,
        )

        assertThat(transport.discardResumableSession()).isEqualTo(Result.Success(Unit))
        assertThat(store.loadResumeCandidate()).isEqualTo(Result.Success(null))
    }

    @Test
    fun explicit_cold_resume_discard_fails_closed_when_secure_removal_fails() = runBlocking {
        val secureStorage = FailingRemoveSecureStorage(testSecureStorage())
        val credential = resumableCredential(generation = 1L)
        val store = ResumableCredentialStore(secureStorage)
        store.stage(credential)
        store.commit(credential.offerId, credential.generation)
        secureStorage.failRemove = true
        val transport = P2pKitRoomTransport(
            appId = AppId("com.parlor.test"),
            deviceName = "self-device",
            scope = testScope,
            kitFactory = object : P2pKitFactory {
                override suspend fun createKit(appId: AppId, deviceName: String): P2pKit =
                    error("discard must not start P2pKit")
            },
            secureStorage = secureStorage,
        )

        assertThat(transport.discardResumableSession())
            .isEqualTo(Result.Failure(NetError.SecureStorageUnavailable))
        secureStorage.failRemove = false
        assertThat(store.loadResumeCandidate()).isEqualTo(Result.Success(credential))
    }

    @Test
    fun permanent_rejection_invalidation_failure_surfaces_secure_storage_error() = runBlocking {
        val failingStorage = FailingRemoveSecureStorage(testSecureStorage())
        val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
        val credential = resumableCredential(generation = 1L).copy(
            issuedAtEpochMillis = now - 1_000L,
            expiresAtEpochMillis = now + 60_000L,
        )
        val store = ResumableCredentialStore(failingStorage)
        store.stage(credential)
        store.commit(credential.offerId, credential.generation)
        failingStorage.failRemove = true
        val hostPeer = peer(
            credential.hostPeerId,
            "${P2pKitRoomTransport.P2P_ROOM_PREFIX}Pinned Host",
        )
        val session = FakeP2pSession(hostPeer).apply {
            sendHandler = { message ->
                val decoded = (message as? P2pMessage.Binary)?.let { codec.decode(it.bytes) }
                if (decoded is PeerMessage.ResumeRequested) {
                    incomingFlow.emit(
                        P2pMessage.Binary(
                            codec.encode(
                                HostMessage.AdmissionRejected(
                                    AdmissionRejection.InvalidCredential,
                                ),
                            ),
                        ),
                    )
                }
            }
        }
        val kit = FakeP2pKit(P2pPeerId(credential.playerId)).apply {
            peersFlow.value = listOf(hostPeer)
            connectHandler = { session }
        }
        val diagnostics = RecordingP2pDiagnostics()
        val transport = P2pKitRoomTransport(
            appId = AppId("com.parlor.test"),
            deviceName = "self-device",
            scope = testScope,
            kitFactory = object : P2pKitFactory {
                override suspend fun createKit(appId: AppId, deviceName: String): P2pKit = kit
            },
            diagnostics = diagnostics,
            secureStorage = failingStorage,
        )

        assertThat(transport.resumeLastSession())
            .isEqualTo(Result.Failure(NetError.SecureStorageUnavailable))
        assertThat(store.loadResumeCandidate()).isEqualTo(Result.Success(credential))
        assertThat(
            diagnostics.events.any {
                it.name == P2pDiagnosticEventName.CREDENTIAL_INVALIDATION_FAILED &&
                    it.result == P2pDiagnosticResult.FAILURE
            },
        ).isTrue()
        assertThat(kit.stopCalls).isEqualTo(1)
    }

    @Test
    fun join_surfaces_wrong_code_and_cleans_up_when_visible_hosts_reject_it() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("self-pid"))
        val transport = P2pKitRoomTransport(
            appId = AppId("com.parlor.test"),
            deviceName = "self-device",
            scope = testScope,
            kitFactory = object : P2pKitFactory {
                override suspend fun createKit(appId: AppId, deviceName: String): P2pKit = kit
            },
            joinTimeoutMs = 2_000L,
        )
        val hostPeer = peer(
            id = "live-host-pid",
            name = "${P2pKitRoomTransport.P2P_ROOM_PREFIX}Live Host",
        )
        val fakeSession = FakeP2pSession(hostPeer)
        fakeSession.sendHandler = { message ->
            val request = (message as? P2pMessage.Binary)
                ?.let { codec.decode(it.bytes) }
            if (request is PeerMessage.AdmissionRequest) {
                fakeSession.incomingFlow.emit(
                    P2pMessage.Binary(
                        codec.encode(
                            HostMessage.AdmissionRejected(
                                com.parlor.networking.protocol.AdmissionRejection.WrongCode,
                            ),
                        ),
                    ),
                )
            }
        }
        kit.connectHandler = { fakeSession }
        kit.peersFlow.value = listOf(hostPeer)

        val result = transport.join("BAD222", "Alice")

        assertThat(result).isInstanceOf(Result.Failure::class)
        assertThat((result as Result.Failure).error).isEqualTo(NetError.WrongCode)
        // The secure connection and host response prove LAN operation even
        // though the application-level room secret was wrong.
        assertThat(transport.localNetworkAccess.value)
            .isEqualTo(LocalNetworkAccess.Operational)
        assertThat(kit.stopCalls).isEqualTo(1)
    }

    @Test
    fun join_surfaces_display_name_conflict_and_cleans_up() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("self-pid"))
        val hostPeer = peer(
            id = "live-host-pid",
            name = "${P2pKitRoomTransport.P2P_ROOM_PREFIX}Live Host",
        )
        val fakeSession = FakeP2pSession(hostPeer)
        fakeSession.sendHandler = { message ->
            val request = (message as? P2pMessage.Binary)?.let { codec.decode(it.bytes) }
            if (request is PeerMessage.AdmissionRequest) {
                fakeSession.incomingFlow.emit(
                    P2pMessage.Binary(
                        codec.encode(
                            HostMessage.AdmissionRejected(
                                AdmissionRejection.DisplayNameInUse,
                            ),
                        ),
                    ),
                )
            }
        }
        kit.connectHandler = { fakeSession }
        kit.peersFlow.value = listOf(hostPeer)
        val transport = P2pKitRoomTransport(
            appId = AppId("com.parlor.test"),
            deviceName = "self-device",
            scope = testScope,
            kitFactory = object : P2pKitFactory {
                override suspend fun createKit(appId: AppId, deviceName: String): P2pKit = kit
            },
            joinTimeoutMs = 2_000L,
        )

        assertThat(transport.join("ABCDEF", "Alice"))
            .isEqualTo(Result.Failure(NetError.DisplayNameInUse))
        assertThat(transport.localNetworkAccess.value)
            .isEqualTo(LocalNetworkAccess.Operational)
        assertThat(kit.stopCalls).isEqualTo(1)
    }

    @Test
    fun join_rejects_a_noncanonical_host_display_identity_before_storing_membership() =
        runBlocking {
            val storage = testSecureStorage()
            val kit = FakeP2pKit(P2pPeerId("self-pid"))
            val hostPeer = peer(
                id = "live-host-pid",
                name = "${P2pKitRoomTransport.P2P_ROOM_PREFIX}Transport Label",
            )
            val offer = testCredentialOffer(PlayerId("self-pid"), hostPeer.id.value)
            val session = FakeP2pSession(hostPeer).apply {
                sendHandler = { message ->
                    val request = (message as? P2pMessage.Binary)?.let { codec.decode(it.bytes) }
                    if (request is PeerMessage.AdmissionRequest) {
                        incomingFlow.emit(
                            P2pMessage.Binary(
                                codec.encode(
                                    HostMessage.AdmissionOffered(
                                        offer,
                                        " Host Alice ",
                                    ),
                                ),
                            ),
                        )
                    }
                }
            }
            kit.connectHandler = { session }
            kit.peersFlow.value = listOf(hostPeer)
            val transport = P2pKitRoomTransport(
                appId = AppId("com.parlor.test"),
                deviceName = "self-device",
                scope = testScope,
                kitFactory = object : P2pKitFactory {
                    override suspend fun createKit(appId: AppId, deviceName: String): P2pKit = kit
                },
                secureStorage = storage,
                joinTimeoutMs = 2_000L,
            )

            assertThat(transport.join("ABCDEF", "Alice"))
                .isEqualTo(Result.Failure(NetError.Unauthorized))
            assertThat(transport.resumableSession()).isEqualTo(Result.Success(null))
            assertThat(kit.stopCalls).isEqualTo(1)
        }

    @Test
    fun wrong_room_does_not_end_search_before_a_late_correct_candidate_appears() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("self-pid"))
        val wrongPeer = peer(
            "wrong-host",
            "${P2pKitRoomTransport.P2P_ROOM_PREFIX}Wrong Host",
        )
        val correctPeer = peer(
            "correct-host",
            "${P2pKitRoomTransport.P2P_ROOM_PREFIX}Correct Host",
        )
        val wrongSession = FakeP2pSession(wrongPeer)
        val correctSession = FakeP2pSession(correctPeer)
        val offer = testCredentialOffer(PlayerId("self-pid"), correctPeer.id.value)
        wrongSession.sendHandler = { message ->
            if (
                (message as? P2pMessage.Binary)?.let { codec.decode(it.bytes) }
                    is PeerMessage.AdmissionRequest
            ) {
                wrongSession.incomingFlow.emit(
                    P2pMessage.Binary(
                        codec.encode(
                            HostMessage.AdmissionRejected(AdmissionRejection.WrongCode),
                        ),
                    ),
                )
            }
        }
        correctSession.sendHandler = { message ->
            when ((message as? P2pMessage.Binary)?.let { codec.decode(it.bytes) }) {
                is PeerMessage.AdmissionRequest -> correctSession.incomingFlow.emit(
                    P2pMessage.Binary(
                        codec.encode(HostMessage.AdmissionOffered(offer, "Host Alice")),
                    ),
                )
                is PeerMessage.AdmissionConfirmed -> correctSession.incomingFlow.emit(
                    P2pMessage.Binary(
                        codec.encode(
                            HostMessage.AdmissionCommitted(
                                offer.playerId,
                                offer.offerId,
                                offer.generation,
                            ),
                        ),
                    ),
                )
                else -> Unit
            }
        }
        kit.connectHandler = { candidate ->
            if (candidate.id == wrongPeer.id) wrongSession else correctSession
        }
        kit.peersFlow.value = listOf(wrongPeer)
        val transport = P2pKitRoomTransport(
            appId = AppId("com.parlor.test"),
            deviceName = "self-device",
            scope = testScope,
            kitFactory = object : P2pKitFactory {
                override suspend fun createKit(appId: AppId, deviceName: String): P2pKit = kit
            },
            secureStorage = testSecureStorage(),
            joinTimeoutMs = 2_000L,
        )

        val joining = async { transport.join("ABCDEF", "Alice") }
        awaitCondition { wrongSession.state.value == ConnectionState.Closed }
        assertThat(joining.isCompleted).isFalse()
        kit.peersFlow.value = listOf(wrongPeer, correctPeer)

        assertThat(withTimeout(2_000) { joining.await() }).isInstanceOf(Result.Success::class)
    }

    @Test
    fun transient_connect_failures_retry_same_candidate_with_bounded_backoff() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("self-pid"))
        val hostPeer = peer(
            "live-host",
            "${P2pKitRoomTransport.P2P_ROOM_PREFIX}Live Host",
        )
        val session = FakeP2pSession(hostPeer)
        val offer = testCredentialOffer(PlayerId("self-pid"), hostPeer.id.value)
        session.sendHandler = { message ->
            when ((message as? P2pMessage.Binary)?.let { codec.decode(it.bytes) }) {
                is PeerMessage.AdmissionRequest -> session.incomingFlow.emit(
                    P2pMessage.Binary(
                        codec.encode(HostMessage.AdmissionOffered(offer, "Host Alice")),
                    ),
                )
                is PeerMessage.AdmissionConfirmed -> session.incomingFlow.emit(
                    P2pMessage.Binary(
                        codec.encode(
                            HostMessage.AdmissionCommitted(
                                offer.playerId,
                                offer.offerId,
                                offer.generation,
                            ),
                        ),
                    ),
                )
                else -> Unit
            }
        }
        var attempts = 0
        kit.connectHandler = {
            attempts += 1
            if (attempts < 3) error("injected transient dial failure")
            session
        }
        kit.peersFlow.value = listOf(hostPeer)
        val transport = P2pKitRoomTransport(
            appId = AppId("com.parlor.test"),
            deviceName = "self-device",
            scope = testScope,
            kitFactory = object : P2pKitFactory {
                override suspend fun createKit(appId: AppId, deviceName: String): P2pKit = kit
            },
            secureStorage = testSecureStorage(),
            joinTimeoutMs = 4_000L,
        )

        assertThat(transport.join("ABCDEF", "Alice")).isInstanceOf(Result.Success::class)
        assertThat(attempts).isEqualTo(3)
    }

    @Test
    fun admission_pending_opens_a_separate_host_approval_window() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("self-pid"))
        val hostPeer = peer(
            "live-host",
            "${P2pKitRoomTransport.P2P_ROOM_PREFIX}Live Host",
        )
        val session = FakeP2pSession(hostPeer)
        val offer = testCredentialOffer(PlayerId("self-pid"), hostPeer.id.value)
        val pendingDelivered = CompletableDeferred<Unit>()
        session.sendHandler = { message ->
            when ((message as? P2pMessage.Binary)?.let { codec.decode(it.bytes) }) {
                is PeerMessage.AdmissionRequest -> {
                    session.incomingFlow.emit(
                        P2pMessage.Binary(
                            codec.encode(HostMessage.AdmissionPending(PlayerId("self-pid"))),
                        ),
                    )
                    pendingDelivered.complete(Unit)
                }
                is PeerMessage.AdmissionConfirmed -> session.incomingFlow.emit(
                    P2pMessage.Binary(
                        codec.encode(
                            HostMessage.AdmissionCommitted(
                                offer.playerId,
                                offer.offerId,
                                offer.generation,
                            ),
                        ),
                    ),
                )
                else -> Unit
            }
        }
        kit.connectHandler = { session }
        kit.peersFlow.value = listOf(hostPeer)
        val transport = P2pKitRoomTransport(
            appId = AppId("com.parlor.test"),
            deviceName = "self-device",
            scope = testScope,
            kitFactory = object : P2pKitFactory {
                override suspend fun createKit(appId: AppId, deviceName: String): P2pKit = kit
            },
            secureStorage = testSecureStorage(),
            joinTimeoutMs = 200L,
        )

        val joining = async { transport.join("ABCDEF", "Alice") }
        pendingDelivered.await()
        val premature = withTimeoutOrNull(250L) { joining.await() }
        assertThat(premature).isEqualTo(null)
        session.incomingFlow.emit(
            P2pMessage.Binary(
                codec.encode(HostMessage.AdmissionOffered(offer, "Host Alice")),
            ),
        )

        assertThat(withTimeout(2_000) { joining.await() }).isInstanceOf(Result.Success::class)
    }

    @Test
    fun admission_waits_ignore_reordered_and_duplicate_frames_from_other_phases() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("self-pid"))
        val hostPeer = peer(
            "live-host",
            "${P2pKitRoomTransport.P2P_ROOM_PREFIX}Live Host",
        )
        val session = FakeP2pSession(hostPeer)
        val playerId = PlayerId("self-pid")
        val offer = testCredentialOffer(playerId, hostPeer.id.value)
        var admissionRequests = 0
        var confirmations = 0
        session.sendHandler = { message ->
            when ((message as? P2pMessage.Binary)?.let { codec.decode(it.bytes) }) {
                is PeerMessage.AdmissionRequest -> {
                    admissionRequests += 1
                    // Frames from a previous/later phase and another actor must
                    // be consumed but never selected as this phase's response.
                    session.incomingFlow.emit(
                        P2pMessage.Binary(
                            codec.encode(
                                HostMessage.AdmissionCommitted(
                                    playerId,
                                    "stale-offer",
                                    offer.generation - 1L,
                                ),
                            ),
                        ),
                    )
                    session.incomingFlow.emit(
                        P2pMessage.Binary(
                            codec.encode(HostMessage.AdmissionPending(PlayerId("other-player"))),
                        ),
                    )
                    session.incomingFlow.emit(
                        P2pMessage.Binary(codec.encode(HostMessage.AdmissionPending(playerId))),
                    )
                    session.incomingFlow.emit(
                        P2pMessage.Binary(
                            codec.encode(
                                HostMessage.AdmissionOffered(
                                    offer.copy(playerId = PlayerId("other-player")),
                                    "Host Alice",
                                ),
                            ),
                        ),
                    )
                    session.incomingFlow.emit(
                        P2pMessage.Binary(
                            codec.encode(HostMessage.AdmissionOffered(offer, "Host Alice")),
                        ),
                    )
                }
                is PeerMessage.AdmissionConfirmed -> {
                    confirmations += 1
                    // Repeated pending/offered frames and mismatched commit
                    // generations are delayed duplicates, not a rejection.
                    session.incomingFlow.emit(
                        P2pMessage.Binary(codec.encode(HostMessage.AdmissionPending(playerId))),
                    )
                    session.incomingFlow.emit(
                        P2pMessage.Binary(
                            codec.encode(HostMessage.AdmissionOffered(offer, "Host Alice")),
                        ),
                    )
                    session.incomingFlow.emit(
                        P2pMessage.Binary(
                            codec.encode(
                                HostMessage.AdmissionCommitted(
                                    playerId,
                                    "stale-offer",
                                    offer.generation,
                                ),
                            ),
                        ),
                    )
                    session.incomingFlow.emit(
                        P2pMessage.Binary(
                            codec.encode(
                                HostMessage.AdmissionCommitted(
                                    PlayerId("other-player"),
                                    offer.offerId,
                                    offer.generation,
                                ),
                            ),
                        ),
                    )
                    session.incomingFlow.emit(
                        P2pMessage.Binary(
                            codec.encode(
                                HostMessage.AdmissionCommitted(
                                    playerId,
                                    offer.offerId,
                                    offer.generation,
                                ),
                            ),
                        ),
                    )
                }
                else -> Unit
            }
        }
        kit.connectHandler = { session }
        kit.peersFlow.value = listOf(hostPeer)
        val transport = P2pKitRoomTransport(
            appId = AppId("com.parlor.test"),
            deviceName = "self-device",
            scope = testScope,
            kitFactory = object : P2pKitFactory {
                override suspend fun createKit(appId: AppId, deviceName: String): P2pKit = kit
            },
            secureStorage = testSecureStorage(),
            joinTimeoutMs = 2_000L,
        )

        val result = transport.join("ABCDEF", "Alice")

        assertThat(result).isInstanceOf(Result.Success::class)
        assertThat(admissionRequests).isEqualTo(1)
        assertThat(confirmations).isEqualTo(1)
        (result as Result.Success).data.leave()
        assertThat(kit.stopCalls).isEqualTo(1)
    }

    @Test
    fun cancellation_while_only_mismatched_admission_frames_arrive_propagates_and_cleans_up() =
        runBlocking {
            val kit = FakeP2pKit(P2pPeerId("self-pid"))
            val hostPeer = peer(
                "live-host",
                "${P2pKitRoomTransport.P2P_ROOM_PREFIX}Live Host",
            )
            val session = FakeP2pSession(hostPeer)
            val mismatchDelivered = CompletableDeferred<Unit>()
            session.sendHandler = { message ->
                if (
                    (message as? P2pMessage.Binary)?.let { codec.decode(it.bytes) }
                        is PeerMessage.AdmissionRequest
                ) {
                    session.incomingFlow.emit(
                        P2pMessage.Binary(
                            codec.encode(
                                HostMessage.AdmissionCommitted(
                                    PlayerId("self-pid"),
                                    "stale-offer",
                                    0L,
                                ),
                            ),
                        ),
                    )
                    session.incomingFlow.emit(
                        P2pMessage.Binary(
                            codec.encode(HostMessage.AdmissionPending(PlayerId("other-player"))),
                        ),
                    )
                    mismatchDelivered.complete(Unit)
                }
            }
            kit.connectHandler = { session }
            kit.peersFlow.value = listOf(hostPeer)
            val transport = P2pKitRoomTransport(
                appId = AppId("com.parlor.test"),
                deviceName = "self-device",
                scope = testScope,
                kitFactory = object : P2pKitFactory {
                    override suspend fun createKit(appId: AppId, deviceName: String): P2pKit = kit
                },
                secureStorage = testSecureStorage(),
                joinTimeoutMs = 10_000L,
            )

            val joining = async { transport.join("ABCDEF", "Alice") }
            mismatchDelivered.await()
            joining.cancel(CancellationException("cancel filtered admission wait"))

            assertFailsWith<CancellationException> { joining.await() }
            awaitCondition { kit.stopCalls == 1 }
        }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun idle_pre_admission_sessions_expire_on_virtual_deadline_and_release_tracking_slots() =
        runTest {
            val kit = FakeP2pKit(P2pPeerId("host-pid"))
            val diagnostics = RecordingP2pDiagnostics()
            val timeoutMs = 1_000L
            val room = HostP2pRoom(
                kit = kit,
                roomCode = "ABCDEF",
                hostDisplayName = "Parlor Room",
                hostPlayerId = PlayerId(kit.localPeerId.value),
                maxRemotePlayers = 1,
                scope = this,
                codec = codec,
                diagnostics = diagnostics,
                firstApplicationMessageTimeoutMs = timeoutMs,
            )
            runCurrent()
            val trackedLimit = 1 + P2pTrafficLimits.SESSION_ADMISSION_HEADROOM
            val idleSessions = List(trackedLimit) { index ->
                FakeP2pSession(peer("idle-$index", "Idle $index"))
            }
            idleSessions.forEach { kit.incomingSessionsFlow.emit(it) }
            runCurrent()

            val overflow = FakeP2pSession(peer("idle-overflow", "Idle overflow"))
            kit.incomingSessionsFlow.emit(overflow)
            runCurrent()
            advanceTimeBy(P2pKitRoomTransport.ADMISSION_REJECTION_FLUSH_MS)
            runCurrent()
            assertThat(overflow.state.value).isEqualTo(ConnectionState.Closed)
            assertThat(overflow.admissionRejections())
                .containsExactly(AdmissionRejection.RateLimited)

            advanceTimeBy(timeoutMs - P2pKitRoomTransport.ADMISSION_REJECTION_FLUSH_MS - 1L)
            runCurrent()
            idleSessions.forEach { session ->
                assertThat(session.state.value).isEqualTo(ConnectionState.Connected)
            }

            advanceTimeBy(1L)
            runCurrent()
            idleSessions.forEach { session ->
                assertThat(session.state.value).isEqualTo(ConnectionState.Closed)
                assertThat(session.closeCalls).isEqualTo(1)
            }
            assertThat(
                diagnostics.events.any { event ->
                    event.name == P2pDiagnosticEventName.ADMISSION_REJECTED &&
                        event.result == P2pDiagnosticResult.TIMEOUT &&
                        event.reason == P2pDiagnosticReason.RATE_LIMIT
                },
            ).isTrue()
            assertThat(room.pendingAdmissions.value).isEmpty()

            // Terminal state cleanup must remove each expired session from the
            // tracked set so a legitimate replacement can immediately enter.
            val replacement = FakeP2pSession(peer("replacement", "Replacement"))
            kit.incomingSessionsFlow.emit(replacement)
            runCurrent()
            replacement.incomingFlow.emit(
                P2pMessage.Binary(
                    codec.encode(
                        PeerMessage.AdmissionRequest(
                            protocol = ProtocolVersion(),
                            actor = PlayerId("forged"),
                            roomCode = "ABCDEF",
                            displayName = replacement.peer.name,
                        ),
                    ),
                ),
            )
            runCurrent()
            assertThat(room.pendingAdmissions.value.map { it.playerId })
                .containsExactly(PlayerId("replacement"))
            advanceTimeBy(timeoutMs)
            runCurrent()
            assertThat(replacement.state.value).isEqualTo(ConnectionState.Connected)
            assertThat(room.pendingAdmissions.value.map { it.playerId })
                .containsExactly(PlayerId("replacement"))

            room.leave()
            assertThat(kit.stopCalls).isEqualTo(1)
            assertThat(replacement.closeCalls).isEqualTo(1)
            advanceTimeBy(timeoutMs)
            runCurrent()
            assertThat(replacement.closeCalls).isEqualTo(1)
        }

    // ------------------------------------------------------- App lifecycle ----

    @Test
    fun transport_serializes_platform_lifecycle_into_the_active_host_room() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("host-pid"))
        val transport = P2pKitRoomTransport(
            appId = AppId("com.parlor.test"),
            deviceName = "host-device",
            scope = testScope,
            kitFactory = object : P2pKitFactory {
                override suspend fun createKit(appId: AppId, deviceName: String): P2pKit = kit
            },
        )
        val room = (transport.host(com.parlor.networking.transport.HostConfig("Room")) as Result.Success)
            .data as HostP2pRoom

        transport.notifyAppBackgrounded()
        awaitCondition { room.lifecycle.value is RoomLifecycleState.Suspended }
        assertThat(kit.backgroundCalls).isEqualTo(1)
        assertThat(kit.stopAdvertisingCalls).isEqualTo(1)

        transport.notifyAppForegrounded()
        awaitCondition { room.lifecycle.value == RoomLifecycleState.Active }
        assertThat(kit.foregroundCalls).isEqualTo(1)
        assertThat(kit.startAdvertisingCalls).isEqualTo(2)

        room.leave()
    }

    @Test
    fun host_foreground_does_not_reactivate_the_session_retired_on_background() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("host-pid"))
        val room = newHostRoom(kit)
        val alice = FakeP2pSession(peer("alice-pid", "Alice"))
        admit(room, kit, alice)

        room.appBackgrounded(1_000L)
        assertThat(room.lifecycle.value)
            .isEqualTo(RoomLifecycleState.Suspended(121_000L))
        assertThat(room.members.value.single().connected).isFalse()

        assertThat(alice.state.value).isEqualTo(ConnectionState.Closed)
        room.appForegrounded(2_000L)
        assertThat(room.lifecycle.value)
            .isEqualTo(RoomLifecycleState.Resuming(121_000L))

        // A late state write from the retired physical generation cannot make
        // the logical room active. Only credential-bound new-session admission
        // may restore this seat after the close-on-background policy runs.
        alice.stateFlow.value = ConnectionState.Connected
        repeat(5) { yield() }
        assertThat(room.lifecycle.value)
            .isEqualTo(RoomLifecycleState.Resuming(121_000L))
        assertThat(room.members.value.single().connected).isFalse()

        room.leave()
    }

    @Test
    fun repeated_background_notification_is_idempotent_and_does_not_extend_deadline() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("host-pid"))
        val room = newHostRoom(kit)

        room.appBackgrounded(1_000L)
        val first = room.lifecycle.value as RoomLifecycleState.Suspended
        room.appBackgrounded(80_000L)

        assertThat(room.lifecycle.value).isEqualTo(first)
        assertThat(first.resumeDeadlineEpochMillis).isEqualTo(121_000L)
        assertThat(kit.backgroundCalls).isEqualTo(1)
        assertThat(kit.stopAdvertisingCalls).isEqualTo(1)
        room.leave()
    }

    @Test
    fun host_background_closes_existing_sessions_before_the_lifecycle_call_returns() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("host-pid"))
        val room = newHostRoom(kit)
        val alice = FakeP2pSession(peer("alice-pid", "Alice"))
        admit(room, kit, alice)

        room.appBackgrounded(1_000L)

        assertThat(alice.closeCalls).isEqualTo(1)
        assertThat(alice.state.value).isEqualTo(ConnectionState.Closed)
        assertThat(room.lifecycle.value)
            .isEqualTo(RoomLifecycleState.Suspended(121_000L))
        assertThat(room.members.value.single().connected).isFalse()

        room.leave()
    }

    @Test
    fun host_background_close_preserves_cancellation() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("host-pid"))
        val room = newHostRoom(kit)
        val alice = FakeP2pSession(peer("alice-pid", "Alice"))
        admit(room, kit, alice)
        alice.closeHandler = { throw CancellationException("cancel background close") }

        assertFailsWith<CancellationException> {
            room.appBackgrounded(1_000L)
        }

        assertThat(alice.closeCalls).isEqualTo(1)
        assertThat(kit.stopCalls).isEqualTo(0)

        alice.closeHandler = null
        room.leave()
    }

    @Test
    fun host_rejects_a_physical_session_that_arrives_after_background_retirement() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("host-pid"))
        val room = newHostRoom(kit)
        val lateSession = FakeP2pSession(peer("late-pid", "Late peer"))

        room.appBackgrounded(1_000L)
        kit.incomingSessionsFlow.emit(lateSession)
        withTimeout(5.seconds) {
            lateSession.state.first { it == ConnectionState.Closed }
        }

        assertThat(lateSession.closeCalls).isEqualTo(1)
        assertThat(lateSession.state.value).isEqualTo(ConnectionState.Closed)
        assertThat(room.pendingAdmissions.value).isEmpty()
        assertThat(room.members.value).isEmpty()
        assertThat(room.lifecycle.value)
            .isEqualTo(RoomLifecycleState.Suspended(121_000L))

        room.leave()
    }

    @Test
    fun peer_background_retires_the_old_session_before_the_lifecycle_call_returns() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("peer-pid"))
        val hostPeer = peer("host-pid", "Host Device")
        val session = FakeP2pSession(hostPeer)
        val room = PeerP2pRoom(
            kit,
            session,
            hostPeer,
            "ABCDEF",
            testScope,
            codec,
        )

        room.appBackgrounded(1_000L)

        assertThat(session.closeCalls).isEqualTo(1)
        assertThat(session.state.value).isEqualTo(ConnectionState.Closed)
        assertThat(room.lifecycle.value)
            .isEqualTo(RoomLifecycleState.Suspended(121_000L))
        assertThat(room.members.value.single().connected).isFalse()

        room.leave()
    }

    @Test
    fun peer_foreground_within_grace_uses_a_fresh_credential_bound_session() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("peer-pid"))
        val hostPeer = peer("host-pid", "Host Device")
        val initialSession = FakeP2pSession(hostPeer)
        val resumedSession = FakeP2pSession(hostPeer)
        val initialCredential = resumableCredential(generation = 1L)
        val rotatedCredential = resumableCredential(generation = 2L)
        val room = PeerP2pRoom(
            kit,
            initialSession,
            hostPeer,
            "ABCDEF",
            testScope,
            codec,
            initialCredential = initialCredential,
            resumeConnector = {
                Result.Success(
                    ResumedPeerConnection(
                        session = resumedSession,
                        hostPeer = hostPeer,
                        credential = rotatedCredential,
                        hostDisplayName = "Host Device",
                    ),
                )
            },
        )

        room.appBackgrounded(1_000L)
        assertThat(room.lifecycle.value)
            .isEqualTo(RoomLifecycleState.Suspended(121_000L))
        assertThat(room.members.value.single().connected).isFalse()

        room.appForegrounded(2_000L)
        awaitCondition { room.lifecycle.value == RoomLifecycleState.Active }
        assertThat(room.members.value.single().connected).isTrue()
        assertThat(initialSession.closeCalls).isEqualTo(1)
        assertThat(resumedSession.state.value).isEqualTo(ConnectionState.Connected)
        assertThat(kit.backgroundCalls).isEqualTo(1)
        assertThat(kit.foregroundCalls).isEqualTo(1)
        room.leave()
    }

    @Test
    fun peer_transport_recovery_while_backgrounded_cannot_reactivate_retired_session() =
        runBlocking {
            val kit = FakeP2pKit(P2pPeerId("peer-pid"))
            val hostPeer = peer("host-pid", "Host Device")
            val session = FakeP2pSession(hostPeer)
            val room = PeerP2pRoom(
                kit = kit,
                session = session,
                hostPeer = hostPeer,
                roomCode = "ABCDEF",
                scope = testScope,
                codec = codec,
            )

            room.appBackgrounded(1_000L)
            session.stateFlow.value = ConnectionState.Reconnecting
            awaitCondition { room.info.value.status == RoomInfo.Status.Lost }

            session.stateFlow.value = ConnectionState.Connected
            repeat(5) { yield() }

            assertThat(room.lifecycle.value)
                .isEqualTo(RoomLifecycleState.Suspended(121_000L))
            assertThat(room.info.value.status).isEqualTo(RoomInfo.Status.Lost)
            assertThat(room.members.value.single().connected).isFalse()

            room.appForegrounded(2_000L)
            repeat(5) { yield() }
            assertThat(room.lifecycle.value)
                .isEqualTo(RoomLifecycleState.Resuming(121_000L))
            assertThat(room.info.value.status).isEqualTo(RoomInfo.Status.Lost)
            assertThat(room.members.value.single().connected).isFalse()
            room.leave()
        }

    @Test
    fun credential_resume_replaces_retired_session_even_if_old_session_reports_connected() =
        runBlocking {
            val kit = FakeP2pKit(P2pPeerId("peer-pid"))
            val hostPeer = peer("host-pid", "Host Device")
            val originalSession = FakeP2pSession(hostPeer)
            val staleReplacement = FakeP2pSession(hostPeer)
            val initialCredential = resumableCredential(generation = 1L)
            val rotatedCredential = resumableCredential(generation = 2L)
            val connectorEntered = CompletableDeferred<Unit>()
            val releaseConnector = CompletableDeferred<Unit>()
            val room = PeerP2pRoom(
                kit = kit,
                session = originalSession,
                hostPeer = hostPeer,
                roomCode = initialCredential.roomCode,
                scope = testScope,
                codec = codec,
                initialCredential = initialCredential,
                resumeConnector = {
                    connectorEntered.complete(Unit)
                    releaseConnector.await()
                    Result.Success(
                        ResumedPeerConnection(
                            session = staleReplacement,
                            hostPeer = hostPeer,
                            credential = rotatedCredential,
                            hostDisplayName = hostPeer.name,
                        ),
                    )
                },
            )

            room.appBackgrounded(1_000L)
            originalSession.stateFlow.value = ConnectionState.Reconnecting
            awaitCondition { room.info.value.status == RoomInfo.Status.Lost }
            room.appForegrounded(2_000L)
            connectorEntered.await()
            assertThat(room.lifecycle.value)
                .isEqualTo(RoomLifecycleState.Resuming(121_000L))

            originalSession.stateFlow.value = ConnectionState.Connected
            repeat(5) { yield() }
            assertThat(room.lifecycle.value)
                .isEqualTo(RoomLifecycleState.Resuming(121_000L))
            releaseConnector.complete(Unit)
            awaitCondition { room.lifecycle.value == RoomLifecycleState.Active }

            assertThat(originalSession.closeCalls).isEqualTo(2)
            assertThat(originalSession.state.value).isEqualTo(ConnectionState.Closed)
            assertThat(staleReplacement.closeCalls).isEqualTo(0)
            assertThat(staleReplacement.sent.isNotEmpty()).isTrue()
            assertThat(room.sendToHost(testPeerHeartbeat())).isEqualTo(Result.Success(Unit))
            assertThat(staleReplacement.sent.last()).isInstanceOf(P2pMessage.Binary::class)
            room.leave()
        }

    @Test
    fun lifecycle_grace_expiry_is_terminal_and_stops_the_kit_once() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("peer-pid"))
        val hostPeer = peer("host-pid", "Host Device")
        val room = PeerP2pRoom(
            kit,
            FakeP2pSession(hostPeer),
            hostPeer,
            "ABCDEF",
            testScope,
            codec,
        )

        room.appBackgrounded(1_000L)
        room.appForegrounded(121_001L)

        assertThat(room.lifecycle.value).isEqualTo(RoomLifecycleState.Expired)
        assertThat(kit.stopCalls).isEqualTo(1)
        room.leave()
        assertThat(kit.stopCalls).isEqualTo(1)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun lifecycle_grace_expires_without_an_additional_platform_callback() = runTest {
        val kit = FakeP2pKit(P2pPeerId("peer-pid"))
        val hostPeer = peer("host-pid", "Host Device")
        val room = PeerP2pRoom(
            kit = kit,
            session = FakeP2pSession(hostPeer),
            hostPeer = hostPeer,
            roomCode = "ABCDEF",
            scope = this,
            codec = codec,
            appResumeGraceMs = 100L,
        )

        room.appBackgrounded(1_000L)
        advanceTimeBy(99L)
        runCurrent()
        assertThat(room.lifecycle.value).isEqualTo(RoomLifecycleState.Suspended(1_100L))

        advanceTimeBy(1L)
        runCurrent()
        assertThat(room.lifecycle.value).isEqualTo(RoomLifecycleState.Expired)
        assertThat(kit.stopCalls).isEqualTo(1)
    }

    // ------------------------------------------------------ Idempotent leave ----

    /**
     * p2p-016: `leave()` is invoked from both a "Leave" tap and the
     * `DisposableEffect.onDispose` that the same navigation triggers, so a
     * double-call is realistic. `kit.stop()` is terminal (the fake throws on a
     * second call, matching the real kit), so an unguarded `leave()` would throw
     * `IllegalStateException` out of a disposal path. The guard makes it a no-op.
     */
    @Test
    fun host_leave_is_idempotent_and_stops_the_kit_once() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("host-pid"))
        val room = newHostRoom(kit)
        val alice = FakeP2pSession(peer("alice-pid", "Alice"))
        admit(room, kit, alice)

        room.leave()
        room.leave() // must not throw

        assertThat(kit.stopCalls).isEqualTo(1)
        assertThat(room.members.value).isEqualTo(emptyList())
    }

    @Test
    fun host_leave_continues_cleanup_after_a_session_close_fails() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("host-pid"))
        val room = newHostRoom(kit)
        val alice = FakeP2pSession(peer("alice-pid", "Alice"))
        val bob = FakeP2pSession(peer("bob-pid", "Bob"))
        admit(room, kit, alice)
        admit(room, kit, bob)
        alice.closeHandler = { error("socket already dead") }

        room.leave()

        assertThat(alice.closeCalls).isEqualTo(1)
        assertThat(bob.closeCalls).isEqualTo(1)
        assertThat(bob.state.value).isEqualTo(ConnectionState.Closed)
        assertThat(kit.stopCalls).isEqualTo(1)
        assertThat(room.members.value).isEmpty()
    }

    @Test
    fun host_leave_finishes_cleanup_after_caller_is_cancelled() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("host-pid"))
        val room = newHostRoom(kit)
        admit(room, kit, FakeP2pSession(peer("alice-pid", "Alice")))

        val leaving = async { room.leave() }
        yield()
        leaving.cancel()
        withTimeout(2_000) { leaving.join() }

        assertThat(kit.stopAdvertisingCalls).isEqualTo(1)
        assertThat(kit.stopCalls).isEqualTo(1)
        assertThat(room.lifecycle.value).isEqualTo(RoomLifecycleState.Closed)
        assertThat(room.members.value).isEmpty()
    }

    @Test
    fun peer_leave_is_idempotent_and_stops_the_kit_once() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("peer-pid"))
        val hostPeer = peer("host-pid", "Host Device")
        val session = FakeP2pSession(hostPeer)
        val room = PeerP2pRoom(kit, session, hostPeer, "ABCDEF", testScope, codec)
        yield()

        room.leave()
        room.leave() // must not throw

        assertThat(kit.stopCalls).isEqualTo(1)
    }

    @Test
    fun peer_leave_finishes_cleanup_after_caller_is_cancelled() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("peer-pid"))
        val hostPeer = peer("host-pid", "Host Device")
        val session = FakeP2pSession(hostPeer)
        val room = PeerP2pRoom(kit, session, hostPeer, "ABCDEF", testScope, codec)

        val leaving = async { room.leave() }
        yield()
        leaving.cancel()
        withTimeout(2_000) { leaving.join() }

        assertThat(session.state.value).isEqualTo(ConnectionState.Closed)
        assertThat(kit.stopCalls).isEqualTo(1)
        assertThat(room.lifecycle.value).isEqualTo(RoomLifecycleState.Closed)
    }

    @Test
    fun peer_rejects_encoded_frames_above_the_directional_host_limit() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("peer-pid"))
        val hostPeer = peer("host-pid", "Host Device")
        val session = FakeP2pSession(hostPeer)
        val room = PeerP2pRoom(kit, session, hostPeer, "ABCDEF", testScope, codec)
        session.sent.clear()

        val result = room.sendToHost(
            testClientCommand(
                ByteArray(P2pTrafficLimits.MAX_PEER_TO_HOST_FRAME_BYTES),
            ),
        )

        assertThat(result).isEqualTo(Result.Failure(NetError.PayloadTooLarge))
        assertThat(session.sent).isEmpty()
        room.leave()
    }

    /**
     * p2p-014: a broadcast that reaches zero Connected peers is a delivery
     * failure, not a silent success — otherwise a caller treats a snapshot that
     * reached nobody as delivered.
     */
    @Test
    fun host_broadcast_returns_not_connected_when_no_peer_is_connected() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("host-pid"))
        val room = newHostRoom(kit)
        yield()

        val result = room.send(SendTarget.Broadcast, testTerminalMessage())

        assertThat(result).isInstanceOf(Result.Failure::class)
        assertThat((result as Result.Failure).error).isEqualTo(NetError.NotConnected)
    }

    @Test
    fun host_broadcast_isolates_peer_failure_and_attempts_later_connected_peers() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("host-pid"))
        val room = newHostRoom(kit)
        val failing = FakeP2pSession(peer("alice-pid", "Alice"))
        val healthy = FakeP2pSession(peer("bob-pid", "Bob"))
        admit(room, kit, failing)
        admit(room, kit, healthy)
        failing.sent.clear()
        healthy.sent.clear()
        failing.sendHandler = { error("injected Alice send failure") }

        val result = room.send(SendTarget.Broadcast, testTerminalMessage())

        assertThat(result).isInstanceOf(Result.Failure::class)
        assertThat((result as Result.Failure).error)
            .isEqualTo(NetError.TransportFailure("injected Alice send failure"))
        assertThat(
            healthy.sent
                .filterIsInstance<P2pMessage.Binary>()
                .map { codec.decode(it.bytes) },
        ).containsExactly(testTerminalMessage())
        room.leave()
    }

    @Test
    fun host_send_propagates_coroutine_cancellation() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("host-pid"))
        val room = newHostRoom(kit)
        val alice = FakeP2pSession(peer("alice-pid", "Alice"))
        admit(room, kit, alice)
        alice.sendHandler = { throw CancellationException("cancel host send") }

        assertFailsWith<CancellationException> {
            room.send(SendTarget.Direct(PlayerId("alice-pid")), testTerminalMessage())
        }
    }

    @Test
    fun peer_send_propagates_coroutine_cancellation() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("peer-pid"))
        val hostPeer = peer("host-pid", "Host Device")
        val session = FakeP2pSession(hostPeer).apply {
            sendHandler = { throw CancellationException("cancel peer send") }
        }
        val room = PeerP2pRoom(kit, session, hostPeer, "ABCDEF", testScope, codec)

        assertFailsWith<CancellationException> {
            room.sendToHost(PeerMessage.LeaveNotice)
        }
    }

    @Test
    fun kit_factory_cancellation_is_never_mapped_to_transport_failure() = runBlocking {
        val transport = P2pKitRoomTransport(
            appId = AppId("com.parlor.test"),
            deviceName = "self-device",
            scope = testScope,
            kitFactory = object : P2pKitFactory {
                override suspend fun createKit(appId: AppId, deviceName: String): P2pKit {
                    throw CancellationException("cancel kit initialization")
                }
            },
        )

        assertFailsWith<CancellationException> {
            transport.host(com.parlor.networking.transport.HostConfig("Room"))
        }
        assertFailsWith<CancellationException> {
            transport.join("ABCDEF", "Alice")
        }
    }

    @Test
    fun cancellation_during_kit_start_cleans_up_and_still_propagates() = runBlocking {
        suspend fun assertPathCleansUp(host: Boolean) {
            val kit = FakeP2pKit(P2pPeerId(if (host) "host-pid" else "peer-pid")).apply {
                startHandler = { throw CancellationException("cancel kit start") }
            }
            val transport = P2pKitRoomTransport(
                appId = AppId("com.parlor.test"),
                deviceName = "self-device",
                scope = testScope,
                kitFactory = object : P2pKitFactory {
                    override suspend fun createKit(appId: AppId, deviceName: String): P2pKit = kit
                },
            )

            assertFailsWith<CancellationException> {
                if (host) {
                    transport.host(com.parlor.networking.transport.HostConfig("Room"))
                } else {
                    transport.join("ABCDEF", "Alice")
                }
            }
            assertThat(kit.stopCalls).isEqualTo(1)
        }

        assertPathCleansUp(host = true)
        assertPathCleansUp(host = false)
    }

    @Test
    fun fatal_kit_factory_and_startup_failures_are_not_mapped_to_transport_results() = runBlocking {
        val fatalFactory = object : P2pKitFactory {
            override suspend fun createKit(appId: AppId, deviceName: String): P2pKit {
                throw AssertionError("fatal kit factory")
            }
        }
        val factoryTransport = P2pKitRoomTransport(
            appId = AppId("com.parlor.test"),
            deviceName = "self-device",
            scope = testScope,
            kitFactory = fatalFactory,
        )
        assertFailsWith<AssertionError> {
            factoryTransport.host(com.parlor.networking.transport.HostConfig("Room"))
        }
        assertFailsWith<AssertionError> { factoryTransport.join("ABCDEF", "Alice") }

        suspend fun assertStartupFatalIsCleanedUp(host: Boolean, failDiscovery: Boolean) {
            val kit = FakeP2pKit(P2pPeerId(if (host) "host-pid" else "peer-pid")).apply {
                if (failDiscovery) {
                    startDiscoveryHandler = { throw AssertionError("fatal discovery startup") }
                } else {
                    startHandler = { throw AssertionError("fatal kit start") }
                }
            }
            val transport = P2pKitRoomTransport(
                appId = AppId("com.parlor.test"),
                deviceName = "self-device",
                scope = testScope,
                kitFactory = object : P2pKitFactory {
                    override suspend fun createKit(appId: AppId, deviceName: String): P2pKit = kit
                },
            )

            assertFailsWith<AssertionError> {
                if (host) {
                    transport.host(com.parlor.networking.transport.HostConfig("Room"))
                } else {
                    transport.join("ABCDEF", "Alice")
                }
            }
            assertThat(kit.stopCalls).isEqualTo(1)
        }

        assertStartupFatalIsCleanedUp(host = true, failDiscovery = false)
        assertStartupFatalIsCleanedUp(host = false, failDiscovery = false)
        assertStartupFatalIsCleanedUp(host = false, failDiscovery = true)
    }

    @Test
    fun caller_timeout_cancellation_is_not_confused_with_the_join_deadline() = runTest {
        val kit = FakeP2pKit(P2pPeerId("peer-pid"))
        val transport = P2pKitRoomTransport(
            appId = AppId("com.parlor.test"),
            deviceName = "self-device",
            scope = backgroundScope,
            kitFactory = object : P2pKitFactory {
                override suspend fun createKit(appId: AppId, deviceName: String): P2pKit = kit
            },
            joinTimeoutMs = 30_000L,
        )

        assertFailsWith<TimeoutCancellationException> {
            withTimeout(1L) { transport.join("ABCDEF", "Alice") }
        }
        assertThat(kit.stopCalls).isEqualTo(1)
    }

    @Test
    fun fatal_send_and_cleanup_failures_are_never_converted_to_normal_results() = runBlocking {
        val hostKit = FakeP2pKit(P2pPeerId("host-pid"))
        val hostRoom = newHostRoom(hostKit)
        val alice = FakeP2pSession(peer("alice-pid", "Alice"))
        admit(hostRoom, hostKit, alice)
        alice.sendHandler = { throw AssertionError("fatal host send") }
        assertFailsWith<AssertionError> {
            hostRoom.send(SendTarget.Direct(PlayerId("alice-pid")), testTerminalMessage())
        }
        alice.sendHandler = null
        hostKit.stopHandler = { throw AssertionError("fatal host cleanup") }
        assertFailsWith<AssertionError> { hostRoom.leave() }

        val peerKit = FakeP2pKit(P2pPeerId("peer-pid"))
        val hostPeer = peer("host-pid", "Host Device")
        val peerSession = FakeP2pSession(hostPeer).apply {
            sendHandler = { throw AssertionError("fatal peer send") }
        }
        val peerRoom = PeerP2pRoom(peerKit, peerSession, hostPeer, "ABCDEF", testScope, codec)
        assertFailsWith<AssertionError> { peerRoom.sendToHost(PeerMessage.LeaveNotice) }
        peerSession.sendHandler = null
        peerKit.stopHandler = { throw AssertionError("fatal peer cleanup") }
        assertFailsWith<AssertionError> { peerRoom.leave() }
    }

    @Test
    fun cancellation_during_process_resume_stops_the_fresh_kit_and_propagates() = runBlocking {
        val secureStorage = testSecureStorage()
        val credential = resumableCredential(generation = 1L).copy(
            expiresAtEpochMillis = kotlin.time.Clock.System.now().toEpochMilliseconds() + 60_000L,
        )
        val credentials = ResumableCredentialStore(secureStorage)
        credentials.stage(credential)
        credentials.commit(credential.offerId, credential.generation)
        val kit = FakeP2pKit(P2pPeerId("peer-pid"))
        val transport = P2pKitRoomTransport(
            appId = AppId("com.parlor.test"),
            deviceName = "self-device",
            scope = testScope,
            kitFactory = object : P2pKitFactory {
                override suspend fun createKit(appId: AppId, deviceName: String): P2pKit = kit
            },
            secureStorage = secureStorage,
        )

        val attempt = async { transport.resumeLastSession() }
        yield()
        attempt.cancel(CancellationException("cancel resume"))
        assertFailsWith<CancellationException> { attempt.await() }
        awaitCondition { kit.stopCalls == 1 }
    }

    // -------------------------------------------------------------- Helpers ----

    private suspend fun assertHostCollectorRejects(
        invalidFrame: P2pMessage,
        reason: P2pDiagnosticReason,
    ) {
        val kit = FakeP2pKit(P2pPeerId("host-pid"))
        val diagnostics = RecordingP2pDiagnostics()
        val room = newHostRoom(kit, diagnostics = diagnostics)
        val alice = FakeP2pSession(peer("alice-pid", "Alice"))
        admit(room, kit, alice)
        val valid = testPeerHeartbeat()
        val received = testScope.async { room.incoming.first() }
        yield()

        alice.incomingFlow.emit(invalidFrame)
        awaitCondition {
            diagnostics.hasTrafficEvent(
                P2pDiagnosticEventName.FRAME_DROPPED,
                P2pDiagnosticRole.HOST,
                reason,
            )
        }
        assertThat(alice.state.value).isEqualTo(ConnectionState.Connected)

        alice.incomingFlow.emit(P2pMessage.Binary(codec.encode(valid)))
        assertThat(withTimeout(2_000L) { received.await() }).isEqualTo(valid)

        repeat(P2pTrafficLimits.MAX_TRAFFIC_VIOLATIONS - 1) {
            alice.incomingFlow.emit(invalidFrame)
        }
        awaitCondition { alice.state.value == ConnectionState.Closed }
        assertThat(
            diagnostics.hasTrafficEvent(
                P2pDiagnosticEventName.PEER_RATE_LIMITED,
                P2pDiagnosticRole.HOST,
                reason,
            ),
        ).isTrue()
        room.leave()
    }

    private suspend fun assertPeerCollectorRejects(
        invalidFrame: P2pMessage,
        reason: P2pDiagnosticReason,
    ) {
        val kit = FakeP2pKit(P2pPeerId("peer-pid"))
        val diagnostics = RecordingP2pDiagnostics()
        val hostPeer = peer("host-pid", "Host Device")
        val session = FakeP2pSession(hostPeer)
        val room = PeerP2pRoom(
            kit = kit,
            session = session,
            hostPeer = hostPeer,
            roomCode = "ABCDEF",
            scope = testScope,
            codec = codec,
            diagnostics = diagnostics,
        )
        val valid = testHostHeartbeat()
        val received = testScope.async { room.incoming.first() }
        yield()

        session.incomingFlow.emit(invalidFrame)
        awaitCondition {
            diagnostics.hasTrafficEvent(
                P2pDiagnosticEventName.FRAME_DROPPED,
                P2pDiagnosticRole.PEER,
                reason,
            )
        }
        assertThat(session.state.value).isEqualTo(ConnectionState.Connected)

        session.incomingFlow.emit(P2pMessage.Binary(codec.encode(valid)))
        assertThat(withTimeout(2_000L) { received.await() }).isEqualTo(valid)

        repeat(P2pTrafficLimits.MAX_TRAFFIC_VIOLATIONS - 1) {
            session.incomingFlow.emit(invalidFrame)
        }
        awaitCondition { session.state.value == ConnectionState.Closed }
        assertThat(
            diagnostics.hasTrafficEvent(
                P2pDiagnosticEventName.PEER_RATE_LIMITED,
                P2pDiagnosticRole.PEER,
                reason,
            ),
        ).isTrue()
        room.leave()
    }

    private fun RecordingP2pDiagnostics.hasTrafficEvent(
        name: P2pDiagnosticEventName,
        role: P2pDiagnosticRole,
        reason: P2pDiagnosticReason,
    ): Boolean = events.any { event ->
        event.name == name &&
            event.role == role &&
            event.result == P2pDiagnosticResult.REJECTED &&
            event.reason == reason
    }

    /**
     * Direct room-unit tests do not run the admission wire handshake. This
     * test-only overload supplies the fake peer label explicitly; shipping
     * construction has no fallback and must pass the validated protocol value.
     */
    @Suppress("FunctionNaming", "LongParameterList")
    private fun PeerP2pRoom(
        kit: P2pKit,
        session: P2pSession,
        hostPeer: Peer,
        roomCode: String,
        scope: CoroutineScope,
        codec: RoomMessageCodec,
        diagnostics: P2pDiagnostics = NoOpP2pDiagnostics,
        initialCredential: ResumableSessionCredential? = null,
        credentialStore: ResumableCredentialStore? = null,
        resumeConnector: (
            suspend (ResumableSessionCredential) ->
                Result<ResumedPeerConnection, ResumeConnectionFailure>
        )? = null,
        onClosed: suspend () -> Unit = {},
        appResumeGraceMs: Long = P2pKitRoomTransport.APP_RESUME_GRACE_MS,
    ): PeerP2pRoom = PeerP2pRoom(
        kit = kit,
        session = session,
        hostPeer = hostPeer,
        roomCode = roomCode,
        scope = scope,
        codec = codec,
        diagnostics = diagnostics,
        initialCredential = initialCredential,
        credentialStore = credentialStore,
        resumeConnector = resumeConnector,
        onClosed = onClosed,
        appResumeGraceMs = appResumeGraceMs,
        hostDisplayName = hostPeer.name,
    )

    private fun newHostRoom(
        kit: FakeP2pKit,
        maxRemotePlayers: Int = 17,
        diagnostics: P2pDiagnostics = NoOpP2pDiagnostics,
    ): HostP2pRoom = HostP2pRoom(
        kit = kit,
        roomCode = "ABCDEF",
        hostDisplayName = "Parlor Room",
        hostPlayerId = PlayerId(kit.localPeerId.value),
        maxRemotePlayers = maxRemotePlayers,
        scope = testScope,
        codec = codec,
        diagnostics = diagnostics,
    )

    private suspend fun requestAdmission(
        room: HostP2pRoom,
        kit: FakeP2pKit,
        session: FakeP2pSession,
    ) {
        emitAdmissionRequest(kit, session)
        awaitCondition {
            room.pendingAdmissions.value.any {
                it.playerId == PlayerId(session.peer.id.value)
            }
        }
    }

    private suspend fun emitAdmissionRequest(
        kit: FakeP2pKit,
        session: FakeP2pSession,
    ) {
        attachSession(kit, session)
        sendAdmissionRequest(session)
    }

    @Suppress("RedundantSuspendModifier") // SharedFlow.emit and yield are real test suspension points.
    private suspend fun attachSession(kit: FakeP2pKit, session: FakeP2pSession) {
        kit.incomingSessionsFlow.emit(session)
        yield()
    }

    private suspend fun sendAdmissionRequest(session: FakeP2pSession) {
        session.incomingFlow.emit(
            P2pMessage.Binary(
                codec.encode(
                    PeerMessage.AdmissionRequest(
                        protocol = ProtocolVersion(),
                        actor = PlayerId("forged-body-id"),
                        roomCode = "ABCDEF",
                        displayName = session.peer.name,
                    ),
                ),
            ),
        )
    }

    private suspend fun admit(
        room: HostP2pRoom,
        kit: FakeP2pKit,
        session: FakeP2pSession,
    ): String {
        requestAdmission(room, kit, session)
        assertThat(room.approveAdmission(PlayerId(session.peer.id.value)))
            .isInstanceOf(Result.Success::class)
        awaitCondition {
            room.members.value.any {
                it.playerId == PlayerId(session.peer.id.value) && it.connected
            }
        }
        return session.sent
            .filterIsInstance<P2pMessage.Binary>()
            .mapNotNull {
                runCatching {
                    codec.decode(it.bytes)
                }.getOrNull() as? HostMessage.AdmissionOffered
            }
            .last()
            .offer
            .secret
    }

    private suspend fun resume(
        room: HostP2pRoom,
        kit: FakeP2pKit,
        session: FakeP2pSession,
        secret: String,
        generation: Long = 1L,
    ) {
        kit.incomingSessionsFlow.emit(session)
        yield()
        session.incomingFlow.emit(
            P2pMessage.Binary(
                codec.encode(
                    PeerMessage.ResumeRequested(
                        protocol = ProtocolVersion(),
                        actor = PlayerId("forged-body-id"),
                        roomCode = "ABCDEF",
                        displayName = session.peer.name,
                        secret = secret,
                        generation = generation,
                    ),
                ),
            ),
        )
        awaitCondition {
            room.members.value.any {
                it.playerId == PlayerId(session.peer.id.value) && it.connected
            }
        }
    }

    private fun P2pMessage.isAdmissionOffered(): Boolean =
        this is P2pMessage.Binary && codec.decode(bytes) is HostMessage.AdmissionOffered

    private fun FakeP2pSession.admissionRejections(): List<AdmissionRejection> = sent
        .filterIsInstance<P2pMessage.Binary>()
        .map { codec.decode(it.bytes) }
        .filterIsInstance<HostMessage.AdmissionRejected>()
        .map(HostMessage.AdmissionRejected::reason)

    private fun testSecureStorage() =
        PlatformKeyedSecureStorage(InMemorySecureKeyValueBacking())

    private fun resumableCredential(
        generation: Long,
        offerId: String = "resume-offer-$generation",
        roomCode: String = "ABCDEF",
        playerId: String = "peer-pid",
        hostPeerId: String = "host-pid",
        membershipId: String = "membership-$roomCode-$playerId-$hostPeerId",
    ) = ResumableSessionCredential(
        offerId = offerId,
        membershipId = membershipId,
        roomCode = roomCode,
        displayName = "fake-device",
        playerId = playerId,
        hostPeerId = hostPeerId,
        hostFingerprint = TEST_PEER_FINGERPRINT.value,
        secret = if (generation == 1L) "a".repeat(64) else "b".repeat(64),
        generation = generation,
        issuedAtEpochMillis = generation * 1_000L,
        expiresAtEpochMillis = 500_000L,
        gameId = "whodunit",
        gameVersion = 1,
    )

    private class FailingRemoveSecureStorage(
        private val delegate: SecureStorage,
    ) : SecureStorage {
        var failRemove: Boolean = false
        var cancelRemove: Boolean = false
        var fatalRemove: Boolean = false

        override suspend fun put(key: String, value: ByteArray): EmptyResult<DataError> =
            delegate.put(key, value)

        override suspend fun get(key: String): Result<ByteArray?, DataError> = delegate.get(key)

        override suspend fun remove(key: String): EmptyResult<DataError> =
            if (fatalRemove) {
                throw AssertionError("fatal credential invalidation")
            } else if (cancelRemove) {
                throw CancellationException("cancel credential invalidation")
            } else if (failRemove) {
                Result.Failure(DataError.IoError("injected-sensitive-storage-failure"))
            } else {
                delegate.remove(key)
            }
    }

    private fun testCredentialOffer(
        playerId: PlayerId,
        hostPeerId: String,
        generation: Long = 1L,
    ): com.parlor.networking.protocol.ResumableCredentialOffer {
        val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
        return com.parlor.networking.protocol.ResumableCredentialOffer(
        offerId = "0123456789abcdef0123456789abcde$generation",
        playerId = playerId,
        hostPeerId = hostPeerId,
        hostFingerprint = TEST_PEER_FINGERPRINT.value,
        secret = if (generation == 1L) "a".repeat(64) else "b".repeat(64),
        generation = generation,
        issuedAtEpochMillis = now,
        expiresAtEpochMillis = now + 86_400_000L,
        gameId = "whodunit",
        gameVersion = 1,
    )
    }

    private fun peer(id: String, name: String): Peer = Peer(
        id = P2pPeerId(id),
        name = name,
        platform = Platform.UNKNOWN,
        supportedTransports = setOf(TransportKind.LAN),
    )

    /**
     * Bounded busy-wait: the production code dispatches via launch on
     * [testScope], so we yield the test coroutine until the assertion
     * holds or the budget runs out. Keeps tests deterministic without
     * relying on coroutine-test schedulers (we deliberately use a real
     * dispatcher because the production code uses one).
     */
    @Suppress("RedundantSuspendModifier") // withTimeout/yield are real test suspension points.
    private suspend fun awaitCondition(timeoutMs: Long = 2_000, block: () -> Boolean) {
        withTimeout(timeoutMs) {
            while (!block()) yield()
        }
    }
}

internal class RecordingP2pDiagnostics : P2pDiagnostics {
    val events = mutableListOf<P2pDiagnosticEvent>()

    override fun record(event: P2pDiagnosticEvent) {
        events += event
    }

    override fun snapshot(): List<P2pDiagnosticRecord> = events.mapIndexed { index, event ->
        P2pDiagnosticRecord(
            sequence = index + 1L,
            elapsedMillis = 0L,
            event = event,
        )
    }

    override fun export(): String = snapshot().joinToString("\n", transform = P2pDiagnosticRecord::exportLine)
}

// -----------------------------------------------------------------------------
// Fakes
//
// Minimal implementations of the P2pKit interfaces that satisfy the
// adapter's needs. Anything we don't drive throws / no-ops; if a future
// adapter change starts using a new surface, the fake will need to grow
// in lockstep — which is the whole point of writing them against the
// real interface instead of a mock.
// -----------------------------------------------------------------------------

internal class FakeP2pKit(
    override val localPeerId: P2pPeerId,
    incomingSessionsOverride: SharedFlow<P2pSession>? = null,
) : P2pKit {
    val incomingSessionsFlow = MutableSharedFlow<P2pSession>(extraBufferCapacity = 16)
    // Mutable backing for peers — tests push entries here to simulate
    // P2pKit discovery state.
    val peersFlow: MutableStateFlow<List<Peer>> = MutableStateFlow(emptyList())
    // lastSeen() return values keyed by peer id. Default null means
    // "never seen" — production code treats that as not-fresh.
    val lastSeenByPeer: MutableMap<P2pPeerId, Long> = mutableMapOf()
    // Ordered log of lifecycle calls; tests assert ordering between
    // stopAdvertising and stop (Bonjour goodbye-flush invariant).
    val callLog: MutableList<String> = mutableListOf()
    var stopAdvertisingCalls: Int = 0
        private set
    var startAdvertisingCalls: Int = 0
        private set
    var backgroundCalls: Int = 0
        private set
    var foregroundCalls: Int = 0
        private set
    var stopCalls: Int = 0
        private set
    // Custom connect() handler for join-path tests; default throws so
    // accidental invocations stand out instead of silently no-op'ing.
    var connectHandler: (suspend (Peer) -> P2pSession)? = null
    var lastExpectedFingerprint: PeerFingerprint? = null
    var startHandler: (suspend () -> Unit)? = null
    var startAdvertisingHandler: (suspend () -> Unit)? = null
    var startDiscoveryHandler: (suspend () -> Unit)? = null
    var stopHandler: (suspend () -> Unit)? = null

    override val appId: AppId = AppId("com.parlor.test")
    override val localDeviceName: String = "fake-device"
    override val localFingerprint: PeerFingerprint = TEST_PEER_FINGERPRINT
    override val localPairingQr: String? = null
    override fun parsePeerPairingQr(value: String): PeerFingerprint? = null
    override val state: StateFlow<P2pState> = MutableStateFlow(P2pState.Running)
    override val peers: StateFlow<List<Peer>> = peersFlow.asStateFlow()
    override val incomingSessions: SharedFlow<P2pSession> =
        incomingSessionsOverride ?: incomingSessionsFlow.asSharedFlow()
    override val sessions: StateFlow<List<P2pSession>> = MutableStateFlow(emptyList())
    override val networkPathStatus: StateFlow<NetworkPathStatus> =
        MutableStateFlow(NetworkPathStatus.Unknown)
    override val permissions: P2pPermissionManager = object : P2pPermissionManager {
        override suspend fun requiredPermissions() = emptyList<dev.p2pkit.core.permission.P2pPermission>()
        override suspend fun missingPermissions() = emptyList<dev.p2pkit.core.permission.P2pPermission>()
        override suspend fun hasRequiredPermissions() = true
    }
    override val networkProvisioning: NetworkProvisioningManager =
        dev.p2pkit.core.provisioning.UnsupportedNetworkProvisioningManager()

    override suspend fun start() {
        callLog += "start"
        startHandler?.invoke()
    }
    override suspend fun startAdvertising() {
        callLog += "startAdvertising"
        startAdvertisingCalls += 1
        startAdvertisingHandler?.invoke()
    }
    override suspend fun stopAdvertising() {
        callLog += "stopAdvertising"
        stopAdvertisingCalls += 1
    }
    override suspend fun startDiscovery() {
        callLog += "startDiscovery"
        startDiscoveryHandler?.invoke()
    }
    override suspend fun stopDiscovery() { callLog += "stopDiscovery" }
    override suspend fun connect(peer: Peer): P2pSession =
        connectHandler?.invoke(peer)
            ?: error("connect() not exercised by these tests")
    override suspend fun connect(
        peer: Peer,
        expectedFingerprint: PeerFingerprint,
    ): P2pSession {
        lastExpectedFingerprint = expectedFingerprint
        return connect(peer)
    }
    override fun lastSeen(peerId: P2pPeerId): Long? = lastSeenByPeer[peerId]
    override fun notifyAppBackgrounded() {
        backgroundCalls += 1
    }
    override fun notifyAppForegrounded() {
        foregroundCalls += 1
    }
    override suspend fun stop() {
        // Faithful to the real kit: stop() is terminal and a second call throws
        // IllegalStateException (States.kt). The transport must guard against it.
        check(stopCalls == 0) { "kit already stopped" }
        stopHandler?.invoke()
        callLog += "stop"
        stopCalls += 1
    }
}

internal class FakeP2pSession(
    override val peer: Peer,
    incomingExtraBufferCapacity: Int = 16,
    override val peerIdentity: PeerIdentity = PeerIdentity(peer.id, TEST_PEER_FINGERPRINT),
) : P2pSession {
    override val id: String = "fake-${peer.id.value}"
    val stateFlow: MutableStateFlow<ConnectionState> = MutableStateFlow(ConnectionState.Connected)
    val incomingFlow: MutableSharedFlow<P2pMessage> = MutableSharedFlow(
        extraBufferCapacity = incomingExtraBufferCapacity,
    )
    private val incomingFilesFlow: MutableSharedFlow<P2pFileOffer> = MutableSharedFlow()
    val sent: MutableList<P2pMessage> = mutableListOf()
    var sendHandler: (suspend (P2pMessage) -> Unit)? = null
    var closeHandler: (suspend () -> Unit)? = null
    var autoAdmissionReady: Boolean = true
    var autoResumeReady: Boolean = true
    var closeCalls: Int = 0
        private set

    override val state: StateFlow<ConnectionState> = stateFlow.asStateFlow()
    override val incoming: SharedFlow<P2pMessage> = incomingFlow.asSharedFlow()
    @Suppress("OVERRIDE_DEPRECATION")
    override val incomingFiles: SharedFlow<P2pFileOffer> = incomingFilesFlow.asSharedFlow()

    override suspend fun send(message: P2pMessage) {
        sent.add(message)
        sendHandler?.invoke(message)
        val offer = (message as? P2pMessage.Binary)
            ?.let { runCatching { RoomMessageCodec().decode(it.bytes) }.getOrNull() }
        when (offer) {
            is HostMessage.AdmissionOffered -> incomingFlow.emit(
                P2pMessage.Binary(
                    RoomMessageCodec().encode(
                        PeerMessage.AdmissionConfirmed(
                            actor = PlayerId(peer.id.value),
                            offerId = offer.offer.offerId,
                            generation = offer.offer.generation,
                        ),
                    ),
                ),
            )
            is HostMessage.ResumeOffered -> incomingFlow.emit(
                P2pMessage.Binary(
                    RoomMessageCodec().encode(
                        PeerMessage.ResumeConfirmed(
                            actor = PlayerId(peer.id.value),
                            offerId = offer.offer.offerId,
                            generation = offer.offer.generation,
                        ),
                    ),
                ),
            )
            is HostMessage.AdmissionCommitted -> if (autoAdmissionReady) {
                incomingFlow.emit(
                    P2pMessage.Binary(
                        RoomMessageCodec().encode(
                            PeerMessage.AdmissionReady(
                                actor = PlayerId(peer.id.value),
                                offerId = offer.offerId,
                                generation = offer.generation,
                            ),
                        ),
                    ),
                )
                incomingFlow.emit(
                    P2pMessage.Binary(
                        RoomMessageCodec().encode(
                            PeerMessage.AdmissionCommitAck(
                                actor = PlayerId(peer.id.value),
                                offerId = offer.offerId,
                                generation = offer.generation,
                            ),
                        ),
                    ),
                )
            }
            is HostMessage.ResumeCommitted -> if (autoResumeReady) {
                incomingFlow.emit(
                    P2pMessage.Binary(
                        RoomMessageCodec().encode(
                            PeerMessage.ResumeReady(
                                actor = PlayerId(peer.id.value),
                                offerId = offer.offerId,
                                generation = offer.generation,
                            ),
                        ),
                    ),
                )
                incomingFlow.emit(
                    P2pMessage.Binary(
                        RoomMessageCodec().encode(
                            PeerMessage.ResumeCommitAck(
                                actor = PlayerId(peer.id.value),
                                offerId = offer.offerId,
                                generation = offer.generation,
                            ),
                        ),
                    ),
                )
            }
            else -> Unit
        }
    }
    @Suppress("OVERRIDE_DEPRECATION")
    override suspend fun sendFile(
        name: String,
        sizeBytes: Long,
        mimeType: String?,
        source: RawSource,
    ): P2pFileTransfer = error("sendFile not exercised by these tests")

    override suspend fun close() {
        closeCalls += 1
        closeHandler?.invoke()
        stateFlow.value = ConnectionState.Closed
    }
}

private val TEST_PEER_FINGERPRINT = PeerFingerprint(
    "p2f1-zlmerarbaugm753v5mvipavkkhwxbvlu3cpx4unzvuvov7zu7dkq",
)

private val TEST_OTHER_PEER_FINGERPRINT = PeerFingerprint(
    "p2f1-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
)

private fun testEnvelopeHeader(
    sequence: Long,
    messageId: String,
): SessionEnvelopeHeader = SessionEnvelopeHeader(
    protocol = ProtocolVersion(),
    sessionId = SessionId("transport-test-session"),
    gameId = GameId("transport-test-game"),
    gameVersion = 1,
    messageId = messageId,
    sequence = sequence,
)

private fun testPeerHeartbeat(
    actor: PlayerId = PlayerId("alice-pid"),
): PeerMessage.SessionHeartbeat = PeerMessage.SessionHeartbeat(
    header = testEnvelopeHeader(
        sequence = 0L,
        messageId = "peer-heartbeat-000000000001",
    ),
    actor = actor,
    lastAppliedRevision = 0L,
)

private fun testHostHeartbeat(): HostMessage.Heartbeat = HostMessage.Heartbeat(
    header = testEnvelopeHeader(
        sequence = 1L,
        messageId = "host-heartbeat-000000000001",
    ),
    authoritativeRevision = 1L,
)

private fun testTerminalMessage(): HostMessage.SessionEnded = HostMessage.SessionEnded(
    header = testEnvelopeHeader(
        sequence = 2L,
        messageId = "host-terminal-0000000000001",
    ),
    reason = SessionEndReason.Cancelled,
    finalRevision = 1L,
)

private fun testClientCommand(payload: ByteArray): PeerMessage.ClientCommand =
    PeerMessage.ClientCommand(
        header = testEnvelopeHeader(
            sequence = 0L,
            messageId = "oversized-command-0000000001",
        ),
        actor = PlayerId("forged"),
        commandId = "oversized-command-0000000001",
        clientSequence = 1L,
        expectedRevision = 0L,
        payload = payload,
    )
