package com.parlor.session.multidevice

import com.parlor.core.ids.GameId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.core.result.Result
import com.parlor.engine.state.Player
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.PARLOR_PROTOCOL_MAJOR
import com.parlor.networking.protocol.PARLOR_PROTOCOL_MINOR
import com.parlor.networking.protocol.PeerMessage
import com.parlor.networking.protocol.ProtocolValidation
import com.parlor.networking.protocol.ProtocolVersion
import com.parlor.networking.protocol.RoomMessage
import com.parlor.networking.protocol.SessionEndReason
import com.parlor.networking.protocol.SessionEnvelopeHeader
import com.parlor.networking.protocol.SessionProtocol
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.networking.room.PeerEvent
import com.parlor.networking.room.RoomInfo
import com.parlor.networking.room.RoomLifecycleState
import com.parlor.networking.room.RoomMember
import com.parlor.networking.room.SendTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.supervisorScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SessionStartHandshakeTest {
    private val hostId = PlayerId("host")
    private val peerId = PlayerId("peer")
    private val secondPeerId = PlayerId("peer-two")
    private val players = listOf(
        Player(hostId, "Host", 0),
        Player(peerId, "Peer", 1),
    )
    private val threePlayers = players + Player(secondPeerId, "Peer Two", 2)
    private val protocol = SessionProtocol(
        sessionId = SessionId("session-0123456789"),
        gameId = GameId("fixture-game"),
        gameVersion = 2,
    )

    @Test
    fun `host commits after all ready and commit acknowledgement is delivery only`() = runTest {
        val room = StartRoom(isHost = true, selfPlayerId = hostId)
        var ids = 0
        val coordinator = hostCoordinator(room, this) {
            "generated-${(++ids).toString().padStart(20, '0')}"
        }
        val start = async {
            coordinator.startSession(
                caseId = "case",
                modeId = "classic",
                players = players,
                sessionNonce = 7L,
                initialRetryMs = 50L,
                maxRetryMs = 100L,
                deadlineMs = 1_000L,
            )
        }
        runCurrent()

        val firstOffer = room.sent.mapNotNull { it.message as? HostMessage.SessionStarting }
            .single()
        advanceTimeBy(50L)
        runCurrent()
        assertEquals(
            listOf(firstOffer.startId, firstOffer.startId),
            room.sent.mapNotNull { (it.message as? HostMessage.SessionStarting)?.startId },
        )
        assertFalse(start.isCompleted)
        assertTrue(room.sent.none { it.message is HostMessage.PlayerSnapshot })

        room.receive(startReady(firstOffer.startId))
        runCurrent()

        assertIs<Result.Success<HostMessage.SessionStarting>>(start.await())
        assertEquals(HostSessionStartState.Started, coordinator.startState.value)
        assertEquals(1, room.sent.count { it.message is HostMessage.SessionStartCommitted })
        assertEquals(1, room.sent.count { it.message is HostMessage.PlayerSnapshot })

        room.receive(startCommitAck(firstOffer.startId))
        runCurrent()
        assertEquals(HostSessionStartState.Started, coordinator.startState.value)
        coordinator.close()
    }

    @Test
    fun `lost commit acknowledgement in a multi-peer room never rolls commit back`() = runTest {
        val room = StartRoom(isHost = true, selfPlayerId = hostId)
        val coordinator = hostCoordinator(
            room = room,
            scope = this,
            remotePlayers = setOf(peerId, secondPeerId),
        )
        val start = async {
            coordinator.startSession(
                "case",
                "classic",
                threePlayers,
                1L,
                initialRetryMs = 25L,
                maxRetryMs = 50L,
                deadlineMs = 200L,
            )
        }
        runCurrent()
        val offer = room.sent.filterIsInstanceStartOffers().first()

        room.receive(startReady(offer.startId, peerId))
        room.receive(startReady(offer.startId, secondPeerId))
        runCurrent()
        assertIs<Result.Success<HostMessage.SessionStarting>>(start.await())
        room.receive(startCommitAck(offer.startId, peerId))
        runCurrent()

        advanceTimeBy(200L)
        runCurrent()

        assertEquals(HostSessionStartState.Started, coordinator.startState.value)
        assertTrue(room.sent.none { it.message is HostMessage.SessionEnded })
        assertEquals(2, room.sent.count { it.message is HostMessage.PlayerSnapshot })
        assertTrue(
            room.sent.count {
                it.target == SendTarget.Direct(secondPeerId) &&
                    it.message is HostMessage.SessionStartCommitted
            } > 1,
        )
        coordinator.close()
    }

    @Test
    fun `caller cancellation racing irreversible commit cannot abort the session`() = runTest {
        val room = StartRoom(isHost = true, selfPlayerId = hostId)
        lateinit var start: kotlinx.coroutines.Deferred<Result<HostMessage.SessionStarting, NetError>>
        val coordinator = HostAuthoritativeSessionCoordinator(
            room = room,
            protocol = protocol,
            remotePlayers = setOf(peerId),
            scope = this,
            applyCommand = { _, _ -> CommandApplication.InvalidAction },
            snapshotFor = {
                // commitStart marks the transaction irreversible before it
                // creates this initial projection. Cancel the caller in that
                // exact window, before completion.await can resume.
                start.cancel(CancellationException("screen disposed after commit"))
                PlayerSnapshotPayload(byteArrayOf(1), byteArrayOf(2))
            },
            heartbeatIntervalMs = 0L,
            idGenerator = { "host-message-012345678901234" },
            requireStartHandshake = true,
        )
        start = async {
            coordinator.startSession(
                "case",
                "classic",
                players,
                1L,
                initialRetryMs = 100L,
                maxRetryMs = 100L,
                deadlineMs = 1_000L,
            )
        }
        runCurrent()
        val offer = room.sent.filterIsInstanceStartOffers().single()

        room.receive(startReady(offer.startId))
        runCurrent()

        assertTrue(start.isCancelled)
        assertEquals(HostSessionStartState.Started, coordinator.startState.value)
        assertTrue(room.sent.any { it.message is HostMessage.SessionStartCommitted })
        assertTrue(room.sent.none { it.message is HostMessage.SessionEnded })
        coordinator.close()
    }

    @Test
    fun `slow preparation and first dropped commit use fresh delivery deadlines`() = runTest {
        val hostRoom = StartRoom(isHost = true, selfPlayerId = hostId).apply {
            dropStartCommits = 1
        }
        val peerRoom = StartRoom(isHost = false, selfPlayerId = peerId)
        hostRoom.hostMessageSink = { _, message -> peerRoom.receive(message) }
        peerRoom.peerMessageSink = { message -> hostRoom.receive(message) }
        val coordinator = hostCoordinator(hostRoom, this)
        val peerStart = async {
            awaitAuthoritativeSessionStart(
                room = peerRoom,
                expectedGameId = protocol.gameId,
                expectedGameVersion = protocol.gameVersion,
                deadlineMs = 100L,
                sendTimeoutMs = 20L,
            ) { _, _ ->
                kotlinx.coroutines.delay(90L)
                true
            }
        }
        val hostStart = async {
            coordinator.startSession(
                "case",
                "classic",
                players,
                1L,
                initialRetryMs = 25L,
                maxRetryMs = 50L,
                deadlineMs = 100L,
            )
        }
        runCurrent()

        advanceTimeBy(90L)
        runCurrent()
        assertIs<Result.Success<HostMessage.SessionStarting>>(hostStart.await())
        assertFalse(peerStart.isCompleted)
        assertEquals(HostSessionStartState.Started, coordinator.startState.value)

        advanceTimeBy(25L)
        runCurrent()
        assertIs<Result.Success<ValidatedSessionStart>>(peerStart.await())
        assertTrue(
            hostRoom.sent.count { it.message is HostMessage.SessionStartCommitted } >= 2,
        )
        assertTrue(hostRoom.sent.none { it.message is HostMessage.SessionEnded })
        coordinator.close()
    }

    @Test
    fun `wrong actor and stale ready cannot advance host transaction`() = runTest {
        val room = StartRoom(isHost = true, selfPlayerId = hostId)
        val coordinator = hostCoordinator(room, this)
        val start = async {
            coordinator.startSession(
                "case",
                "classic",
                players,
                1L,
                initialRetryMs = 100L,
                maxRetryMs = 100L,
                deadlineMs = 500L,
            )
        }
        runCurrent()
        val offer = room.sent.filterIsInstanceStartOffers().single()

        room.receive(startReady("stale-start-012345678901234", peerId))
        room.receive(startReady(offer.startId, PlayerId("intruder")))
        runCurrent()
        assertTrue(room.sent.none { it.message is HostMessage.SessionStartCommitted })
        assertFalse(start.isCompleted)

        room.receive(startReady(offer.startId))
        runCurrent()
        assertIs<Result.Success<HostMessage.SessionStarting>>(start.await())
        coordinator.close()
    }

    @Test
    fun `duplicate ready flood during commit does not amplify responses`() = runTest {
        val room = StartRoom(isHost = true, selfPlayerId = hostId)
        val coordinator = hostCoordinator(room, this)
        val start = async {
            coordinator.startSession(
                "case",
                "classic",
                players,
                1L,
                initialRetryMs = 100L,
                maxRetryMs = 100L,
                deadlineMs = 1_000L,
            )
        }
        runCurrent()
        val offer = room.sent.filterIsInstanceStartOffers().single()
        room.receive(startReady(offer.startId))
        runCurrent()
        start.await()
        val commitsBeforeFlood = room.sent.count {
            it.message is HostMessage.SessionStartCommitted
        }

        repeat(200) { room.receive(startReady(offer.startId)) }
        runCurrent()
        assertEquals(
            commitsBeforeFlood,
            room.sent.count { it.message is HostMessage.SessionStartCommitted },
        )

        advanceTimeBy(100L)
        runCurrent()
        assertEquals(
            commitsBeforeFlood + 1,
            room.sent.count { it.message is HostMessage.SessionStartCommitted },
        )
        coordinator.close()
    }

    @Test
    fun `host pre-commit deadline is terminal and bounded`() = runTest {
        val room = StartRoom(isHost = true, selfPlayerId = hostId)
        val coordinator = hostCoordinator(room, this)
        val start = async {
            coordinator.startSession(
                "case",
                "classic",
                players,
                1L,
                initialRetryMs = 25L,
                maxRetryMs = 50L,
                deadlineMs = 200L,
            )
        }
        runCurrent()
        advanceTimeBy(200L)
        runCurrent()

        assertEquals(NetError.Timeout, assertIs<Result.Failure<NetError>>(start.await()).error)
        assertEquals(HostSessionStartState.Failed, coordinator.startState.value)
        assertEquals(
            listOf(SessionEndReason.Cancelled),
            room.sent.mapNotNull { (it.message as? HostMessage.SessionEnded)?.reason },
        )
        assertTrue(room.sent.none { it.message is HostMessage.PlayerSnapshot })
        coordinator.close()
    }

    @Test
    fun `hanging start send cannot block absolute host deadline`() = runTest {
        val room = StartRoom(isHost = true, selfPlayerId = hostId).apply {
            hangStartFrames = true
        }
        val coordinator = hostCoordinator(
            room = room,
            scope = this,
            remotePlayers = setOf(peerId, secondPeerId),
            startSendTimeoutMs = 25L,
        )
        val start = async {
            coordinator.startSession(
                "case",
                "classic",
                threePlayers,
                1L,
                initialRetryMs = 20L,
                maxRetryMs = 40L,
                deadlineMs = 100L,
            )
        }
        runCurrent()
        assertEquals(
            setOf(SendTarget.Direct(peerId), SendTarget.Direct(secondPeerId)),
            room.sent.filter { it.message is HostMessage.SessionStarting }
                .map(StartSent::target)
                .toSet(),
        )

        advanceTimeBy(100L)
        runCurrent()

        assertEquals(NetError.Timeout, assertIs<Result.Failure<NetError>>(start.await()).error)
        assertEquals(HostSessionStartState.Failed, coordinator.startState.value)
        assertTrue(room.sent.any { it.message is HostMessage.SessionEnded })
        coordinator.close()
    }

    @Test
    fun `host coordinator requires the start barrier by default`() = runTest {
        val room = StartRoom(isHost = true, selfPlayerId = hostId)
        val coordinator = HostAuthoritativeSessionCoordinator(
            room = room,
            protocol = protocol,
            remotePlayers = setOf(peerId),
            scope = this,
            applyCommand = { _, _ -> CommandApplication.Applied },
            snapshotFor = { PlayerSnapshotPayload(byteArrayOf(1), byteArrayOf(2)) },
            heartbeatIntervalMs = 0L,
        )

        coordinator.publishState()
        runCurrent()

        assertEquals(HostSessionStartState.Waiting, coordinator.startState.value)
        assertTrue(room.sent.none { it.message is HostMessage.PlayerSnapshot })
        coordinator.close()
    }

    @Test
    fun `host start gate retains typed failure and exit transition is idempotent`() {
        val failed = Result.Failure(NetError.Timeout)
            .toHostStartGateState()

        assertEquals(
            NetError.Timeout,
            assertIs<HostStartGateState.Failed>(failed).error,
        )
        assertEquals(HostStartGateState.Exiting, failed.beginExit())
        assertEquals(
            HostStartGateState.Exiting,
            failed.beginExit().beginExit(),
        )
    }

    @Test
    fun `cancelling host start propagates cancellation and aborts transaction`() = runTest {
        val room = StartRoom(isHost = true, selfPlayerId = hostId)
        val coordinator = hostCoordinator(room, this)
        val start = async {
            coordinator.startSession("case", "classic", players, 1L, deadlineMs = 5_000L)
        }
        runCurrent()
        start.cancel(CancellationException("host left start screen"))
        runCurrent()

        val offer = room.sent.mapNotNull { it.message as? HostMessage.SessionStarting }.first()
        room.receive(startReady(offer.startId))
        runCurrent()

        assertTrue(start.isCancelled)
        assertEquals(HostSessionStartState.Failed, coordinator.startState.value)
        assertTrue(room.sent.none { it.message is HostMessage.SessionStartCommitted })
        assertTrue(
            room.sent.any {
                (it.message as? HostMessage.SessionEnded)?.reason == SessionEndReason.Cancelled
            },
        )
        coordinator.close()
    }

    @Test
    fun `resend start retries each phase and only succeeds after commit acknowledgement`() =
        runTest {
            val room = StartRoom(isHost = true, selfPlayerId = hostId)
            val coordinator = hostCoordinator(room, this)
            val startId = completeHostStart(coordinator, room)
            room.sent.clear()

            val resend = async {
                coordinator.resendStart(
                    playerId = peerId,
                    initialRetryMs = 25L,
                    maxRetryMs = 50L,
                    readyDeadlineMs = 100L,
                    commitAckDeadlineMs = 100L,
                )
            }
            runCurrent()
            assertEquals(1, room.sent.filterIsInstanceStartOffers().size)

            // A dropped offer is retried with the same immutable start id.
            advanceTimeBy(25L)
            runCurrent()
            assertEquals(
                listOf(startId, startId),
                room.sent.filterIsInstanceStartOffers().map { it.startId },
            )

            // Ready near the first phase deadline receives a fresh commit-ack
            // deadline; the overall transaction does not time out at t=100.
            advanceTimeBy(65L)
            room.receive(startReady(startId))
            runCurrent()
            assertFalse(resend.isCompleted)
            assertEquals(1, room.sent.count { it.message is HostMessage.SessionStartCommitted })

            // Model one dropped commit. The stable commit is retried, but the
            // host still does not declare this seat recovered without its Ack.
            advanceTimeBy(25L)
            runCurrent()
            assertFalse(resend.isCompleted)
            assertEquals(2, room.sent.count { it.message is HostMessage.SessionStartCommitted })

            room.receive(startCommitAck(startId))
            runCurrent()
            assertIs<Result.Success<Unit>>(resend.await())
            coordinator.close()
        }

    @Test
    fun `resend ready deadline is bounded and delayed frames cannot revive it`() = runTest {
        val room = StartRoom(isHost = true, selfPlayerId = hostId)
        val coordinator = hostCoordinator(room, this)
        val startId = completeHostStart(coordinator, room)
        room.sent.clear()

        val resend = async {
            coordinator.resendStart(
                playerId = peerId,
                initialRetryMs = 25L,
                maxRetryMs = 50L,
                readyDeadlineMs = 100L,
                commitAckDeadlineMs = 100L,
            )
        }
        runCurrent()
        advanceTimeBy(100L)
        runCurrent()

        assertEquals(NetError.Timeout, assertIs<Result.Failure<NetError>>(resend.await()).error)
        val commitsAtFailure = room.sent.count {
            it.message is HostMessage.SessionStartCommitted
        }
        room.receive(startReady(startId))
        room.receive(startCommitAck(startId))
        runCurrent()
        assertEquals(
            commitsAtFailure,
            room.sent.count { it.message is HostMessage.SessionStartCommitted },
        )
        coordinator.close()
    }

    @Test
    fun `concurrent resend transactions are isolated per player`() = runTest {
        val room = StartRoom(isHost = true, selfPlayerId = hostId)
        val coordinator = hostCoordinator(
            room = room,
            scope = this,
            remotePlayers = setOf(peerId, secondPeerId),
        )
        val startId = completeHostStart(
            coordinator = coordinator,
            room = room,
            remotePlayers = setOf(peerId, secondPeerId),
            roster = threePlayers,
        )
        room.sent.clear()

        val first = async {
            coordinator.resendStart(peerId, 25L, 50L, 200L, 200L)
        }
        val second = async {
            coordinator.resendStart(secondPeerId, 25L, 50L, 200L, 200L)
        }
        runCurrent()
        assertEquals(
            setOf(SendTarget.Direct(peerId), SendTarget.Direct(secondPeerId)),
            room.sent.filter { it.message is HostMessage.SessionStarting }
                .map(StartSent::target)
                .toSet(),
        )

        room.receive(startReady(startId, peerId))
        room.receive(startCommitAck(startId, peerId))
        runCurrent()
        assertIs<Result.Success<Unit>>(first.await())
        assertFalse(second.isCompleted)
        assertTrue(
            room.sent.none {
                it.target == SendTarget.Direct(secondPeerId) &&
                    it.message is HostMessage.SessionStartCommitted
            },
        )

        room.receive(startReady(startId, secondPeerId))
        room.receive(startCommitAck(startId, secondPeerId))
        runCurrent()
        assertIs<Result.Success<Unit>>(second.await())
        coordinator.close()
    }

    @Test
    fun `seat resend progresses while another initial commit acknowledgement is outstanding`() =
        runTest {
            val room = StartRoom(isHost = true, selfPlayerId = hostId)
            val coordinator = hostCoordinator(
                room = room,
                scope = this,
                remotePlayers = setOf(peerId, secondPeerId),
            )
            val starting = async {
                coordinator.startSession(
                    "case",
                    "classic",
                    threePlayers,
                    1L,
                    initialRetryMs = 25L,
                    maxRetryMs = 50L,
                    deadlineMs = 500L,
                )
            }
            runCurrent()
            val startId = room.sent.filterIsInstanceStartOffers().first().startId
            room.receive(startReady(startId, peerId))
            room.receive(startReady(startId, secondPeerId))
            runCurrent()
            assertIs<Result.Success<HostMessage.SessionStarting>>(starting.await())
            // Peer two deliberately leaves the original commit-delivery
            // attempt open while peer one starts an independent replay.
            room.receive(startCommitAck(startId, peerId))
            runCurrent()
            room.sent.clear()

            val resend = async {
                coordinator.resendStart(peerId, 25L, 50L, 200L, 200L)
            }
            runCurrent()
            room.receive(startReady(startId, peerId))
            runCurrent()
            assertTrue(
                room.sent.any {
                    it.target == SendTarget.Direct(peerId) &&
                        it.message is HostMessage.SessionStartCommitted
                },
            )
            room.receive(startCommitAck(startId, peerId))
            runCurrent()

            assertIs<Result.Success<Unit>>(resend.await())
            coordinator.close()
        }

    @Test
    fun `cancelled resend cannot commit when its abort signal loses to a full mailbox`() = runTest {
        val room = StartRoom(isHost = true, selfPlayerId = hostId)
        val coordinator = hostCoordinator(room, this)
        val startId = completeHostStart(coordinator, room)
        room.sent.clear()
        val resend = async {
            coordinator.resendStart(peerId, 25L, 50L, 1_000L, 1_000L)
        }
        runCurrent()

        val releaseWorker = CompletableDeferred<Unit>()
        val blocker = async {
            coordinator.applyHostMutation {
                releaseWorker.await()
                false
            }
        }
        runCurrent()
        val queued = List(8) {
            async { coordinator.publishState(incrementRevision = false) }
        }
        runCurrent()
        assertTrue(queued.all { it.isCompleted })

        resend.cancel(CancellationException("rejoin grace expired"))
        room.receive(startReady(startId))
        releaseWorker.complete(Unit)
        runCurrent()

        assertTrue(resend.isCancelled)
        assertTrue(room.sent.none { it.message is HostMessage.SessionStartCommitted })
        blocker.await()
        queued.forEach { it.await() }
        coordinator.close()
    }

    @Test
    fun `peer lobby has no pre-offer timeout and late valid start succeeds`() = runTest {
        val room = StartRoom(isHost = false, selfPlayerId = peerId)
        val waiting = async {
            awaitAuthoritativeSessionStart(
                room,
                protocol.gameId,
                protocol.gameVersion,
                deadlineMs = 100L,
            ) { _, _ -> true }
        }
        runCurrent()

        advanceTimeBy(10_000L)
        runCurrent()
        assertFalse(waiting.isCompleted)

        val offer = offer()
        room.receive(offer)
        runCurrent()
        room.receive(commit(offer.startId))
        runCurrent()
        assertIs<Result.Success<ValidatedSessionStart>>(waiting.await())
    }

    @Test
    fun `peer ignores transient host loss and reacks ready on restoration`() = runTest {
        val room = StartRoom(isHost = false, selfPlayerId = peerId)
        val waiting = async {
            awaitAuthoritativeSessionStart(
                room,
                protocol.gameId,
                protocol.gameVersion,
                deadlineMs = 500L,
            ) { _, _ -> true }
        }
        runCurrent()
        val offer = offer()
        room.receive(offer)
        runCurrent()
        room.emit(PeerEvent.HostLost)
        advanceTimeBy(100L)
        room.emit(PeerEvent.HostRestored)
        runCurrent()
        assertFalse(waiting.isCompleted)
        assertEquals(2, room.sentToHost.count { it is PeerMessage.SessionStartReady })

        room.receive(commit(offer.startId))
        runCurrent()
        assertIs<Result.Success<ValidatedSessionStart>>(waiting.await())
    }

    @Test
    fun `peer transaction deadline begins at offer and terminal lifecycle fails`() = runTest {
        val timeoutRoom = StartRoom(isHost = false, selfPlayerId = peerId)
        val timedOut = async {
            awaitAuthoritativeSessionStart(
                timeoutRoom,
                protocol.gameId,
                protocol.gameVersion,
                deadlineMs = 100L,
            ) { _, _ -> true }
        }
        runCurrent()
        timeoutRoom.receive(offer())
        runCurrent()
        advanceTimeBy(100L)
        runCurrent()
        assertEquals(NetError.Timeout, networkFailure(timedOut.await()))

        val closedRoom = StartRoom(isHost = false, selfPlayerId = peerId)
        val closed = async {
            awaitAuthoritativeSessionStart(
                closedRoom,
                protocol.gameId,
                protocol.gameVersion,
            ) { _, _ -> true }
        }
        runCurrent()
        closedRoom.lifecycle.value = RoomLifecycleState.Closed
        runCurrent()
        assertEquals(NetError.NotConnected, networkFailure(closed.await()))
    }

    @Test
    fun `peer validates wire version against local protocol major and minor`() = runTest {
        suspend fun rejected(version: ProtocolVersion): SessionStartFailure.Protocol {
            val room = StartRoom(isHost = false, selfPlayerId = peerId)
            var prepared = false
            val waiting = async {
                awaitAuthoritativeSessionStart(
                    room,
                    protocol.gameId,
                    protocol.gameVersion,
                ) { _, _ ->
                    prepared = true
                    true
                }
            }
            runCurrent()
            room.receive(offer(wireVersion = version))
            runCurrent()
            assertFalse(prepared)
            return assertIs(
                assertIs<Result.Failure<SessionStartFailure>>(waiting.await()).error,
            )
        }

        assertEquals(
            ProtocolValidation.IncompatibleProtocol,
            rejected(
                ProtocolVersion(PARLOR_PROTOCOL_MAJOR - 1, PARLOR_PROTOCOL_MINOR),
            ).validation,
        )
        assertEquals(
            ProtocolValidation.IncompatibleProtocol,
            rejected(
                ProtocolVersion(PARLOR_PROTOCOL_MAJOR, PARLOR_PROTOCOL_MINOR + 1),
            ).validation,
        )
    }

    @Test
    fun `peer rejects non-canonical start seats before local preparation`() = runTest {
        val malformedRosters = listOf(
            players.mapIndexed { index, player -> player.copy(seat = index * 2) },
            players.reversed(),
        )

        malformedRosters.forEach { malformedPlayers ->
            val room = StartRoom(isHost = false, selfPlayerId = peerId)
            var prepared = false
            val waiting = async {
                awaitAuthoritativeSessionStart(
                    room,
                    protocol.gameId,
                    protocol.gameVersion,
                ) { _, _ ->
                    prepared = true
                    true
                }
            }
            runCurrent()
            room.receive(offer().copy(players = malformedPlayers))
            runCurrent()

            val failure = assertIs<SessionStartFailure.Protocol>(
                assertIs<Result.Failure<SessionStartFailure>>(waiting.await()).error,
            )
            assertEquals(ProtocolValidation.InvalidSessionStart, failure.validation)
            assertFalse(prepared)
        }
    }

    @Test
    fun `unexpected local preparation failure remains typed and cancellation propagates`() = runTest {
        val room = StartRoom(isHost = false, selfPlayerId = peerId)
        val cause = IllegalStateException("fixture preparation failure")
        val waiting = async {
            awaitAuthoritativeSessionStart(
                room,
                protocol.gameId,
                protocol.gameVersion,
            ) { _, _ -> throw cause }
        }
        runCurrent()
        room.receive(offer())
        runCurrent()

        val failure = assertIs<SessionStartFailure.PreparationFailed>(
            assertIs<Result.Failure<SessionStartFailure>>(waiting.await()).error,
        )
        assertTrue(failure.cause === cause)

        val cancelledRoom = StartRoom(isHost = false, selfPlayerId = peerId)
        val cancelled = async {
            awaitAuthoritativeSessionStart(
                cancelledRoom,
                protocol.gameId,
                protocol.gameVersion,
            ) { _, _ -> awaitCancellation() }
        }
        runCurrent()
        cancelledRoom.receive(offer())
        runCurrent()
        cancelled.cancel(CancellationException("screen disposed"))
        runCurrent()
        assertTrue(cancelled.isCancelled)
    }

    @Test
    fun `fatal preparation error is never converted into a start failure`() = runTest {
        supervisorScope {
            val room = StartRoom(isHost = false, selfPlayerId = peerId)
            val fatal = AssertionError("fatal fixture")
            val waiting = async {
                awaitAuthoritativeSessionStart(
                    room,
                    protocol.gameId,
                    protocol.gameVersion,
                ) { _, _ -> throw fatal }
            }
            runCurrent()
            room.receive(offer())
            runCurrent()

            val thrown = assertFailsWith<AssertionError> { waiting.await() }
            assertEquals("fatal fixture", thrown.message)
        }
    }

    @Test
    fun `peer enters on irreversible commit even when acknowledgement send is lost`() = runTest {
        val room = StartRoom(isHost = false, selfPlayerId = peerId)
        room.failNextCommitAck = true
        val waiting = async {
            awaitAuthoritativeSessionStart(
                room,
                protocol.gameId,
                protocol.gameVersion,
                deadlineMs = 1_000L,
            ) { _, _ -> true }
        }
        runCurrent()
        val offer = offer()
        room.receive(offer)
        runCurrent()
        room.receive(commit(offer.startId))
        runCurrent()

        val result = assertIs<Result.Success<ValidatedSessionStart>>(waiting.await()).data
        assertEquals(offer.startId, result.protocol.startId)
        assertEquals(1, room.sentToHost.count { it is PeerMessage.SessionStartCommitAck })
    }

    @Test
    fun `dropped eager snapshot and initial request recover from revision-zero heartbeat`() = runTest {
        val room = StartRoom(isHost = false, selfPlayerId = peerId).apply {
            dropSnapshotRequests = 1
        }
        val startId = "start-012345678901234567890"
        val peerProtocol = protocol.copy(startId = startId)
        val coordinator = PeerAuthoritativeSessionCoordinator(
            room = room,
            protocol = peerProtocol,
            selfPlayerId = peerId,
            scope = this,
            onSnapshot = { _, _ -> true },
            idGenerator = { "peer-message-012345678901234" },
            initialSnapshotRetryMs = 5_000L,
            maxInitialSnapshotRetryMs = 5_000L,
            initialSnapshotDeadlineMs = 10_000L,
        )
        runCurrent()

        assertEquals(-1L, coordinator.revision.value)
        assertFalse(coordinator.hasAuthoritativeSnapshot.value)
        assertEquals(
            listOf(-1L),
            room.sentToHost.filterIsInstance<PeerMessage.SnapshotRequest>()
                .map { it.lastAppliedRevision },
        )

        room.receive(
            HostMessage.Heartbeat(
                header = header("heartbeat-0123456789012345678", sequence = 2L),
                authoritativeRevision = 0L,
            ),
        )
        runCurrent()
        assertEquals(2, room.sentToHost.count { it is PeerMessage.SnapshotRequest })

        room.receive(
            HostMessage.PlayerSnapshot(
                header = header("snapshot-01234567890123456789", sequence = 3L),
                revision = 0L,
                publicPayload = byteArrayOf(1),
                privatePayload = byteArrayOf(2),
            ),
        )
        runCurrent()
        assertTrue(coordinator.hasAuthoritativeSnapshot.value)
        assertEquals(0L, coordinator.revision.value)
        assertEquals(null, coordinator.initialSnapshotError.value)
        coordinator.close()
    }

    @Test
    fun `initial snapshot recovery retries are bounded and expose timeout`() = runTest {
        val room = StartRoom(isHost = false, selfPlayerId = peerId)
        val coordinator = PeerAuthoritativeSessionCoordinator(
            room = room,
            protocol = protocol.copy(startId = "start-012345678901234567890"),
            selfPlayerId = peerId,
            scope = this,
            onSnapshot = { _, _ -> true },
            idGenerator = { "peer-message-012345678901234" },
            initialSnapshotRetryMs = 25L,
            maxInitialSnapshotRetryMs = 50L,
            initialSnapshotDeadlineMs = 100L,
        )
        runCurrent()
        advanceTimeBy(100L)
        runCurrent()

        assertFalse(coordinator.hasAuthoritativeSnapshot.value)
        assertEquals(NetError.Timeout, coordinator.initialSnapshotError.value)
        assertTrue(room.sentToHost.count { it is PeerMessage.SnapshotRequest } in 2..4)
        assertEquals(
            NetError.SessionSuspended,
            assertIs<Result.Failure<NetError>>(coordinator.submit(byteArrayOf(1))).error,
        )
        coordinator.close()
    }

    @Test
    fun `throwing snapshot transport is retried and cannot strand loading forever`() = runTest {
        val room = StartRoom(isHost = false, selfPlayerId = peerId).apply {
            throwSnapshotRequests = true
        }
        val coordinator = PeerAuthoritativeSessionCoordinator(
            room = room,
            protocol = protocol.copy(startId = "start-012345678901234567890"),
            selfPlayerId = peerId,
            scope = this,
            onSnapshot = { _, _ -> true },
            idGenerator = { "peer-message-012345678901234" },
            initialSnapshotRetryMs = 25L,
            maxInitialSnapshotRetryMs = 50L,
            initialSnapshotDeadlineMs = 100L,
        )
        runCurrent()
        advanceTimeBy(100L)
        runCurrent()

        assertFalse(coordinator.hasAuthoritativeSnapshot.value)
        assertEquals(NetError.Timeout, coordinator.initialSnapshotError.value)
        assertTrue(room.sentToHost.count { it is PeerMessage.SnapshotRequest } in 2..4)
        coordinator.close()
    }

    @Test
    fun `game coordinator reacks delayed duplicate commit`() = runTest {
        val room = StartRoom(isHost = false, selfPlayerId = peerId)
        val startId = "start-012345678901234567890"
        val coordinator = PeerAuthoritativeSessionCoordinator(
            room = room,
            protocol = protocol.copy(startId = startId),
            selfPlayerId = peerId,
            scope = this,
            onSnapshot = { _, _ -> true },
            idGenerator = { "peer-message-012345678901234" },
        )
        runCurrent()
        room.receive(commit(startId))
        runCurrent()
        assertEquals(1, room.sentToHost.count { it is PeerMessage.SessionStartCommitAck })
        coordinator.close()
    }

    @Test
    fun `throwing duplicate commit acknowledgement cannot kill peer inbox`() = runTest {
        val room = StartRoom(isHost = false, selfPlayerId = peerId).apply {
            throwCommitAcks = true
        }
        val startId = "start-012345678901234567890"
        val coordinator = PeerAuthoritativeSessionCoordinator(
            room = room,
            protocol = protocol.copy(startId = startId),
            selfPlayerId = peerId,
            scope = this,
            onSnapshot = { _, _ -> true },
            idGenerator = { "peer-message-012345678901234" },
        )
        runCurrent()
        room.receive(commit(startId))
        runCurrent()
        room.receive(
            HostMessage.PlayerSnapshot(
                header = header("snapshot-01234567890123456789", sequence = 2L),
                revision = 0L,
                publicPayload = byteArrayOf(1),
                privatePayload = byteArrayOf(2),
            ),
        )
        runCurrent()

        assertTrue(coordinator.hasAuthoritativeSnapshot.value)
        coordinator.close()
    }

    private fun hostCoordinator(
        room: StartRoom,
        scope: kotlinx.coroutines.CoroutineScope,
        remotePlayers: Set<PlayerId> = setOf(peerId),
        startSendTimeoutMs: Long = DEFAULT_START_FRAME_SEND_TIMEOUT_MS,
        idGenerator: () -> String = { "host-message-012345678901234" },
    ) = HostAuthoritativeSessionCoordinator(
        room = room,
        protocol = protocol,
        remotePlayers = remotePlayers,
        scope = scope,
        applyCommand = { _, _ -> CommandApplication.InvalidAction },
        snapshotFor = { PlayerSnapshotPayload(byteArrayOf(1), byteArrayOf(2)) },
        heartbeatIntervalMs = 0L,
        idGenerator = idGenerator,
        requireStartHandshake = true,
        startSendTimeoutMs = startSendTimeoutMs,
    )

    private suspend fun kotlinx.coroutines.test.TestScope.completeHostStart(
        coordinator: HostAuthoritativeSessionCoordinator,
        room: StartRoom,
        remotePlayers: Set<PlayerId> = setOf(peerId),
        roster: List<Player> = players,
    ): String {
        val starting = async {
            coordinator.startSession(
                caseId = "case",
                modeId = "classic",
                players = roster,
                sessionNonce = 1L,
                initialRetryMs = 25L,
                maxRetryMs = 50L,
                deadlineMs = 500L,
            )
        }
        runCurrent()
        val startId = room.sent.filterIsInstanceStartOffers().first().startId
        remotePlayers.forEach { actor -> room.receive(startReady(startId, actor)) }
        runCurrent()
        assertIs<Result.Success<HostMessage.SessionStarting>>(starting.await())
        remotePlayers.forEach { actor -> room.receive(startCommitAck(startId, actor)) }
        runCurrent()
        return startId
    }

    private fun offer(
        startId: String = "start-012345678901234567890",
        wireVersion: ProtocolVersion = ProtocolVersion(),
    ) = HostMessage.SessionStarting(
        startId = startId,
        caseId = "case",
        modeId = "classic",
        players = players,
        sessionNonce = 9L,
        header = header(messageId = startId, sequence = 0L).copy(protocol = wireVersion),
    )

    private fun commit(startId: String) = HostMessage.SessionStartCommitted(
        startId = startId,
        header = header(messageId = "commit-012345678901234567890", sequence = 1L),
    )

    private fun startReady(startId: String, actor: PlayerId = peerId) =
        PeerMessage.SessionStartReady(
            header = header(messageId = "ready-0123456789012345678901", sequence = 0L),
            actor = actor,
            startId = startId,
        )

    private fun startCommitAck(startId: String, actor: PlayerId = peerId) =
        PeerMessage.SessionStartCommitAck(
            header = header(messageId = "commit-ack-01234567890123456", sequence = 0L),
            actor = actor,
            startId = startId,
        )

    private fun header(messageId: String, sequence: Long) = SessionEnvelopeHeader(
        protocol = ProtocolVersion(),
        sessionId = protocol.sessionId,
        gameId = protocol.gameId,
        gameVersion = protocol.gameVersion,
        messageId = messageId,
        sequence = sequence,
    )

    private fun networkFailure(
        result: Result<ValidatedSessionStart, SessionStartFailure>,
    ): NetError = assertIs<SessionStartFailure.Network>(
        assertIs<Result.Failure<SessionStartFailure>>(result).error,
    ).error

    private fun List<StartSent>.filterIsInstanceStartOffers(): List<HostMessage.SessionStarting> =
        mapNotNull { it.message as? HostMessage.SessionStarting }
}

private data class StartSent(val target: SendTarget, val message: HostMessage)

private class StartRoom(
    override val isHost: Boolean,
    override val selfPlayerId: PlayerId,
) : LocalRoom {
    private val inbox = Channel<RoomMessage>(capacity = 64)
    private val events = MutableSharedFlow<PeerEvent>(extraBufferCapacity = 8)
    override val incoming: Flow<RoomMessage> = inbox.receiveAsFlow()
    override val peerEvents: SharedFlow<PeerEvent> = events.asSharedFlow()
    override val info = MutableStateFlow(
        RoomInfo("ROOM42", "Fixture", PlayerId("host"), RoomInfo.Status.Joined),
    )
    override val members = MutableStateFlow<List<RoomMember>>(emptyList())
    override val lifecycle = MutableStateFlow<RoomLifecycleState>(RoomLifecycleState.Active)
    val sent = mutableListOf<StartSent>()
    val sentToHost = mutableListOf<PeerMessage>()
    var failNextCommitAck = false
    var throwCommitAcks = false
    var hangStartFrames = false
    var dropSnapshotRequests = 0
    var throwSnapshotRequests = false
    var dropStartCommits = 0
    var hostMessageSink: suspend (SendTarget, HostMessage) -> Unit = { _, _ -> }
    var peerMessageSink: suspend (PeerMessage) -> Unit = {}

    suspend fun receive(message: RoomMessage) {
        inbox.send(message)
    }

    suspend fun emit(event: PeerEvent) {
        events.emit(event)
    }

    override suspend fun send(
        target: SendTarget,
        message: HostMessage,
    ): Result<Unit, NetError> {
        sent += StartSent(target, message)
        if (
            hangStartFrames &&
            (message is HostMessage.SessionStarting ||
                message is HostMessage.SessionStartCommitted)
        ) {
            awaitCancellation()
        }
        if (message is HostMessage.SessionStartCommitted && dropStartCommits > 0) {
            dropStartCommits -= 1
            return Result.Success(Unit)
        }
        hostMessageSink(target, message)
        return Result.Success(Unit)
    }

    override suspend fun sendToHost(message: PeerMessage): Result<Unit, NetError> {
        sentToHost += message
        if (message is PeerMessage.SnapshotRequest && throwSnapshotRequests) {
            throw IllegalStateException("fixture snapshot transport failure")
        }
        if (message is PeerMessage.SessionStartCommitAck && throwCommitAcks) {
            throw IllegalStateException("fixture commit acknowledgement failure")
        }
        if (message is PeerMessage.SessionStartCommitAck && failNextCommitAck) {
            failNextCommitAck = false
            return Result.Failure(NetError.NotConnected)
        }
        if (message is PeerMessage.SnapshotRequest && dropSnapshotRequests > 0) {
            dropSnapshotRequests -= 1
            return Result.Success(Unit)
        }
        peerMessageSink(message)
        return Result.Success(Unit)
    }

    override suspend fun leave() = Unit
}
