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
import com.parlor.core.result.Result
import com.parlor.networking.protocol.AdmissionRejection
import com.parlor.networking.protocol.CommandStatus
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.PeerMessage
import com.parlor.networking.protocol.ProtocolVersion
import com.parlor.networking.protocol.RoomMessage
import com.parlor.networking.protocol.RoomMessageCodec
import com.parlor.networking.protocol.SessionEnvelopeHeader
import com.parlor.networking.room.NetError
import com.parlor.networking.room.PeerEvent
import com.parlor.networking.room.RoomInfo
import com.parlor.networking.room.RoomLifecycleState
import com.parlor.networking.room.RoomMember
import com.parlor.networking.room.SendTarget
import com.parlor.networking.transport.LocalNetworkAccess
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
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
    private val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val codec = RoomMessageCodec()

    @AfterTest
    fun cancelScope() {
        testScope.coroutineContext[Job]?.cancel()
    }

    // ---------------------------------------------------------------- Host ----

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
    fun host_application_queue_applies_bounded_backpressure_without_dropping_gameplay() =
        runBlocking {
            val kit = FakeP2pKit(P2pPeerId("host-pid"))
            val room = newHostRoom(kit)
            val alice = FakeP2pSession(
                peer = peer("alice-pid", "Alice"),
                incomingExtraBufferCapacity = 0,
            )
            admit(room, kit, alice)
            val heartbeat = P2pMessage.Binary(codec.encode(PeerMessage.Heartbeat))

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
                .isEqualTo(PeerMessage.Heartbeat)
            withTimeout(2_000) { producer.await() }
            assertThat(alice.state.value).isEqualTo(ConnectionState.Connected)
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
            val hostOnlyFrame = P2pMessage.Binary(codec.encode(HostMessage.EndSession))

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
    fun host_send_direct_returns_not_connected_after_session_closes() = runBlocking {
        val kit = FakeP2pKit(P2pPeerId("host-pid"))
        val room = newHostRoom(kit)

        val alice = FakeP2pSession(peer("alice-pid", "Alice"))
        admit(room, kit, alice)

        // Sanity: send succeeds while connected.
        val before = room.send(SendTarget.Direct(PlayerId("alice-pid")), HostMessage.EndSession)
        assertThat(before).isInstanceOf(Result.Success::class)

        alice.stateFlow.value = ConnectionState.Closed
        awaitCondition { room.members.value.singleOrNull()?.connected == false }

        val after = room.send(SendTarget.Direct(PlayerId("alice-pid")), HostMessage.EndSession)
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
        val frame = P2pMessage.Binary(codec.encode(HostMessage.EndSession))

        val producer = async {
            // One frame may already be executing inside the collector in
            // addition to the channel's configured buffered capacity.
            repeat(P2pTrafficLimits.PEER_APPLICATION_QUEUE_CAPACITY + 2) {
                session.incomingFlow.emit(frame)
            }
        }
        yield()
        assertThat(producer.isCompleted).isFalse()

        assertThat(withTimeout(2_000) { room.incoming.first() })
            .isEqualTo(HostMessage.EndSession)
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
        val wrongDirection = P2pMessage.Binary(codec.encode(PeerMessage.Heartbeat))

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
        val before = room.sendToHost(PeerMessage.Heartbeat)
        assertThat(before).isInstanceOf(Result.Success::class)

        session.stateFlow.value = ConnectionState.Closed
        yield(); yield()

        val after = room.sendToHost(PeerMessage.Heartbeat)
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
                    P2pMessage.Binary(codec.encode(HostMessage.AdmissionOffered(offer))),
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
                        P2pMessage.Binary(codec.encode(HostMessage.AdmissionOffered(initialOffer))),
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
                            P2pMessage.Binary(codec.encode(HostMessage.ResumeOffered(rotatedOffer))),
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
                        P2pMessage.Binary(codec.encode(HostMessage.EndSession)),
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
            assertThat(resumeRequests).hasSize(1)
            assertThat(resumeRequests.single().secret).isEqualTo(initialOffer.secret)
            assertThat(resumeRequests.single().generation).isEqualTo(1L)
            assertThat(relaunchedKit.lastExpectedFingerprint).isEqualTo(TEST_PEER_FINGERPRINT)
            assertThat(room.rejoinToken).isEqualTo(null)
            assertThat(room.incoming.first()).isEqualTo(HostMessage.EndSession)

            room.leave()
            assertThat(relaunchedTransport.resumableSession())
                .isEqualTo(Result.Success(null))
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
                    P2pMessage.Binary(codec.encode(HostMessage.AdmissionOffered(offer))),
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
                    P2pMessage.Binary(codec.encode(HostMessage.AdmissionOffered(offer))),
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
            P2pMessage.Binary(codec.encode(HostMessage.AdmissionOffered(offer))),
        )

        assertThat(withTimeout(2_000) { joining.await() }).isInstanceOf(Result.Success::class)
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
    fun peer_foreground_within_grace_restores_a_still_connected_session() = runBlocking {
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
        assertThat(room.lifecycle.value)
            .isEqualTo(RoomLifecycleState.Suspended(121_000L))
        assertThat(room.members.value.single().connected).isFalse()

        room.appForegrounded(2_000L)
        assertThat(room.lifecycle.value)
            .isEqualTo(RoomLifecycleState.Active)
        assertThat(room.members.value.single().connected).isTrue()
        assertThat(kit.backgroundCalls).isEqualTo(1)
        assertThat(kit.foregroundCalls).isEqualTo(1)
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

        val result = room.send(SendTarget.Broadcast, HostMessage.EndSession)

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

        val result = room.send(SendTarget.Broadcast, HostMessage.EndSession)

        assertThat(result).isInstanceOf(Result.Failure::class)
        assertThat((result as Result.Failure).error)
            .isEqualTo(NetError.TransportFailure("injected Alice send failure"))
        assertThat(
            healthy.sent
                .filterIsInstance<P2pMessage.Binary>()
                .map { codec.decode(it.bytes) },
        ).containsExactly(HostMessage.EndSession)
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
            room.send(SendTarget.Direct(PlayerId("alice-pid")), HostMessage.EndSession)
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

    private fun newHostRoom(
        kit: FakeP2pKit,
        maxRemotePlayers: Int = 17,
        diagnostics: P2pDiagnostics = NoOpP2pDiagnostics,
    ): HostP2pRoom = HostP2pRoom(
        kit = kit,
        roomCode = "ABCDEF",
        roomDisplayName = "Parlor Room",
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
        kit.incomingSessionsFlow.emit(session)
        yield()
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
        awaitCondition {
            room.pendingAdmissions.value.any {
                it.playerId == PlayerId(session.peer.id.value)
            }
        }
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

    private fun resumableCredential(generation: Long) = ResumableSessionCredential(
        offerId = "resume-offer-$generation",
        roomCode = "ABCDEF",
        displayName = "fake-device",
        playerId = "peer-pid",
        hostPeerId = "host-pid",
        hostFingerprint = TEST_PEER_FINGERPRINT.value,
        secret = if (generation == 1L) "a".repeat(64) else "b".repeat(64),
        generation = generation,
        issuedAtEpochMillis = generation * 1_000L,
        expiresAtEpochMillis = 500_000L,
        gameId = "whodunit",
        gameVersion = 1,
    )

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

    override val appId: AppId = AppId("com.parlor.test")
    override val localDeviceName: String = "fake-device"
    override val localFingerprint: PeerFingerprint = TEST_PEER_FINGERPRINT
    override val localPairingQr: String? = null
    override fun parsePeerPairingQr(value: String): PeerFingerprint? = null
    override val state: StateFlow<P2pState> = MutableStateFlow(P2pState.Running)
    override val peers: StateFlow<List<Peer>> = peersFlow.asStateFlow()
    override val incomingSessions: SharedFlow<P2pSession> = incomingSessionsFlow.asSharedFlow()
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
    }
    override suspend fun stopAdvertising() {
        callLog += "stopAdvertising"
        stopAdvertisingCalls += 1
    }
    override suspend fun startDiscovery() { callLog += "startDiscovery" }
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
        callLog += "stop"
        stopCalls += 1
    }
}

internal class FakeP2pSession(
    override val peer: Peer,
    incomingExtraBufferCapacity: Int = 16,
) : P2pSession {
    override val id: String = "fake-${peer.id.value}"
    override val peerIdentity: PeerIdentity = PeerIdentity(peer.id, TEST_PEER_FINGERPRINT)
    val stateFlow: MutableStateFlow<ConnectionState> = MutableStateFlow(ConnectionState.Connected)
    val incomingFlow: MutableSharedFlow<P2pMessage> = MutableSharedFlow(
        extraBufferCapacity = incomingExtraBufferCapacity,
    )
    private val incomingFilesFlow: MutableSharedFlow<P2pFileOffer> = MutableSharedFlow()
    val sent: MutableList<P2pMessage> = mutableListOf()
    var sendHandler: (suspend (P2pMessage) -> Unit)? = null
    var autoAdmissionReady: Boolean = true
    var autoResumeReady: Boolean = true

    override val state: StateFlow<ConnectionState> = stateFlow.asStateFlow()
    override val incoming: SharedFlow<P2pMessage> = incomingFlow.asSharedFlow()
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
    override suspend fun sendFile(
        name: String,
        sizeBytes: Long,
        mimeType: String?,
        source: RawSource,
    ): P2pFileTransfer = error("sendFile not exercised by these tests")

    override suspend fun close() {
        stateFlow.value = ConnectionState.Closed
    }
}

private val TEST_PEER_FINGERPRINT = PeerFingerprint(
    "p2f1-zlmerarbaugm753v5mvipavkkhwxbvlu3cpx4unzvuvov7zu7dkq",
)
