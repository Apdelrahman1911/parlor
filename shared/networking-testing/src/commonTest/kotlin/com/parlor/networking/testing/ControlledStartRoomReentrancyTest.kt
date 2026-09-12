package com.parlor.networking.testing

import com.parlor.core.ids.GameId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.core.result.Result
import com.parlor.engine.state.Player
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.PeerMessage
import com.parlor.networking.protocol.ProtocolValidation
import com.parlor.networking.protocol.SessionEnvelopeHeader
import com.parlor.networking.protocol.SessionProtocol
import com.parlor.networking.protocol.validateFor
import com.parlor.networking.room.SendTarget
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ControlledStartRoomReentrancyTest {
    @Test
    fun readyBookkeepingPrecedesSynchronousCommitAndSnapshot() = runTest {
        // The queued test body is the external caller; actual bus collectors may
        // resume synchronously inside trySend. This is not iOS runtime evidence.
        val failures = mutableListOf<String?>()
        val workerJob = SupervisorJob(coroutineContext[Job])
        val workers = CoroutineScope(
            workerJob + UnconfinedTestDispatcher(testScheduler) +
                CoroutineExceptionHandler { _, error -> failures += error.message },
        )
        val players = (1..6).map { Player(PlayerId("l08-host-seat-$it"), "Audit Player $it", it - 1) }
        val remotes = players.drop(1).map { it.id }.toSet()
        val room = ControlledStartRoom(players, workers)
        val protocol = SessionProtocol(SessionId("fixture-session"), GameId("fixture-game"), 1)
        val header = SessionEnvelopeHeader(
            protocol = protocol.protocol, sessionId = protocol.sessionId, gameId = protocol.gameId,
            gameVersion = protocol.gameVersion, messageId = "fixture-start-0123456789", sequence = 0L,
        )
        val offer = HostMessage.SessionStarting(
            startId = header.messageId, caseId = "fixture-case", modeId = "fixture-mode",
            players = players, sessionNonce = 91L, header = header,
        )
        val commit = HostMessage.SessionStartCommitted(
            startId = offer.startId,
            header = header.copy(messageId = "fixture-commit-0123456789", sequence = 1L),
        )
        val ready = mutableSetOf<PlayerId>()
        val acknowledged = mutableSetOf<PlayerId>()
        workers.launch {
            room.incoming.collect { message ->
                when (message) {
                    is PeerMessage.SessionStartReady -> {
                        assertEquals(ProtocolValidation.Valid, message.validateFor(protocol))
                        assertEquals(offer.startId, message.startId)
                        assertTrue(message.actor in remotes && ready.add(message.actor))
                        if (ready == remotes) {
                            // Commit immediately, without a yield or test-clock
                            // advance to hide reentrancy in the real fixture.
                            remotes.forEach { id -> room.send(SendTarget.Direct(id), commit) }
                        }
                    }
                    is PeerMessage.SessionStartCommitAck -> {
                        assertEquals(ProtocolValidation.Valid, message.validateFor(protocol))
                        assertEquals(offer.startId, message.startId)
                        assertTrue(message.actor in remotes && acknowledged.add(message.actor))
                    }
                    is PeerMessage.SnapshotRequest -> {
                        assertEquals(ProtocolValidation.Valid, message.validateFor(protocol))
                        assertTrue(message.actor in acknowledged)
                        assertEquals(-1L, message.lastAppliedRevision)
                        room.send(
                            SendTarget.Direct(message.actor),
                            HostMessage.PlayerSnapshot(
                                header.copy(messageId = "fixture-snapshot-0123456789", sequence = 2L),
                                revision = 0L, nextExpectedClientSequence = 1L,
                                publicPayload = byteArrayOf(), privatePayload = byteArrayOf(),
                            ),
                        )
                    }
                    else -> error("Unexpected synthetic peer frame")
                }
            }
        }
        try {
            assertTrue(room.closeAdmissions() is Result.Success)
            remotes.forEach { id -> assertTrue(room.send(SendTarget.Direct(id), offer) is Result.Success) }
            assertEquals(5, room.offeredPeerCount)
            assertEquals(0, room.readyPeerCount)
            assertEquals(0, room.committedPeerCount)
            room.releaseReady(protocol) { it == offer }
            assertEquals(emptyList(), failures, "Synchronous fixture worker failed")
            assertEquals(remotes, ready)
            assertEquals(remotes, acknowledged)
            assertEquals(5, room.readyPeerCount)
            assertEquals(5, room.committedPeerCount)
            assertEquals(5, room.snapshottedPeerCount)
            room.requireNoDrops()
        } finally {
            try {
                room.leave()
            } finally {
                workerJob.cancelAndJoin()
            }
        }
        assertEquals(0, room.workerCount)
        assertTrue(workerJob.isCompleted)
    }
}
