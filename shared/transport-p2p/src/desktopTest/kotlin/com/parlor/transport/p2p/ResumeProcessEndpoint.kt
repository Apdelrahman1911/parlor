package com.parlor.transport.p2p

import com.parlor.core.ids.GameId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.core.result.Result
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.PeerMessage
import com.parlor.networking.protocol.ProtocolVersion
import com.parlor.networking.protocol.SessionEndReason
import com.parlor.networking.protocol.SessionEnvelopeHeader
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.networking.room.SendTarget
import com.parlor.networking.room.SessionEndCommitStatus
import com.parlor.networking.transport.HostConfig
import com.parlor.networking.transport.HostedGameProtocol
import com.parlor.networking.transport.JoinConfig
import dev.p2pkit.core.AppId
import java.util.concurrent.Executors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.system.exitProcess

/** Real-LAN, synthetic same-process credential retention; no mobile/process-death claim. */
object ResumeProcessEndpoint {
    private val game = HostedGameProtocol(GameId("process-fixture-game"), 1)

    private fun header(sequence: Long, id: String) = SessionEnvelopeHeader(
        protocol = ProtocolVersion(), sessionId = SessionId("process-fixture-session"),
        gameId = game.gameId, gameVersion = game.gameVersion, messageId = id, sequence = sequence,
    )

    @JvmStatic
    fun main(args: Array<String>) {
        try {
            check(args.size == 2 && args[0] in setOf("host", "peer"))
            check(args[1].matches(Regex("[a-f0-9]{32}")))
            Executors.newFixedThreadPool(4).asCoroutineDispatcher().use { dispatcher ->
                executeEndpoint(args, dispatcher)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (@Suppress("TooGenericExceptionCaught") failure: Throwable) {
            // Retain type only: no credential, endpoint, player state or exception text.
            println("ENDPOINT_FAIL type=${failure.javaClass.name}")
            exitProcess(1)
        }
    }

    private fun executeEndpoint(args: Array<String>, dispatcher: CoroutineDispatcher) = runBlocking {
        val scope = CoroutineScope(dispatcher + SupervisorJob())
        var room: LocalRoom? = null
        try {
            withTimeout(90_000) {
                val fixture = LoopbackDeviceFixture(dispatcher)
                val transport = fixture.transport(
                    AppId("com.parlor.p2p.process.${args[1]}"), args[0] + "-fixture", scope,
                )
                if (args[0] == "host") {
                    val result = transport.host(HostConfig("Process Fixture", maxRemotePlayers = 1, gameProtocol = game))
                    check(result is Result.Success)
                    room = result.data
                    verifyHost(result.data)
                } else {
                    val code = readln()
                    check(code.matches(Regex("[A-Z0-9]{6}")))
                    val result = transport.join(JoinConfig(code, "process-peer", expectedGameProtocol = game))
                    check(result is Result.Success)
                    room = result.data
                    verifyPeer(result.data, fixture, transport) { room = it }
                }
            }
            println("ENDPOINT_PASS")
        } finally {
            withContext(NonCancellable) {
                try {
                    room?.leave()
                } finally {
                    scope.coroutineContext[Job]?.cancelAndJoin()
                }
            }
            println("TEARDOWN_PASS")
        }
    }

    private suspend fun verifyHost(room: LocalRoom) = coroutineScope {
        check(room.isHost)
        val admission = async(start = CoroutineStart.UNDISPATCHED) {
            val pending = withTimeout(35_000) { room.pendingAdmissions.first { it.size == 1 }.single() }
            check(pending.displayName == "process-peer" && !pending.isRejoin)
            check(room.approveAdmission(pending.playerId) is Result.Success)
            pending.playerId
        }
        val first = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(45_000) { room.incoming.first() }
        }
        println("ROOM_CODE " + room.info.value.code)
        System.out.flush()
        val player = admission.await()
        val message = first.await()
        check(message is PeerMessage.SessionHeartbeat && message.actor == player)
        check(message.header == header(0, "process-peer-heartbeat-0001"))
        check(message.actor != PlayerId("forged-process-actor"))
        println("AUTHENTICATED_ACTOR_PASS")
        val roster = room.closeAdmissions()
        check(roster is Result.Success && roster.data.size == 2 && roster.data.count { it.playerId == player } == 1)
        println("FROZEN_ROSTER_PASS")
        val resumedInbound = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(45_000) { room.incoming.first() }
        }
        check(room.send(SendTarget.Direct(player), HostMessage.Heartbeat(
            header(1, "process-host-checkpoint-0001"), 0,
        )) is Result.Success)
        withTimeout(15_000) { room.members.first { members -> members.none { it.playerId == player && it.connected } } }
        println("PHYSICAL_CLOSE_PASS")
        val resumed = resumedInbound.await()
        check(resumed is PeerMessage.SessionHeartbeat && resumed.actor == player)
        check(resumed.header == header(2, "process-peer-heartbeat-0002"))
        withTimeout(10_000) { room.members.first { members -> members.any { it.playerId == player && it.connected } } }
        check(room.members.value.size == 2 && room.pendingAdmissions.value.isEmpty())
        println("REJOIN_SAME_SEAT_PASS")
        check(room.send(SendTarget.Direct(player), HostMessage.SessionEnded(
            header(3, "process-host-terminal-0001"), SessionEndReason.Cancelled, 0,
        )) is Result.Success)
        withTimeout(15_000) { room.members.first { members -> members.none { it.playerId == player && it.connected } } }
        println("PEER_DISCONNECT_PASS")
    }

    private suspend fun verifyPeer(
        room: LocalRoom,
        fixture: LoopbackDeviceFixture,
        transport: P2pKitRoomTransport,
        onRoom: (LocalRoom) -> Unit,
    ) = coroutineScope {
        check(!room.isHost)
        val store = ResumableCredentialStore(fixture.secureStorage)
        val loaded = store.loadResumeCandidate()
        check(loaded is Result.Success && loaded.data != null)
        val before = checkNotNull(loaded.data)
        val checkpoint = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(15_000) { room.incoming.first() }
        }
        check(room.sendToHost(PeerMessage.SessionHeartbeat(
            header(0, "process-peer-heartbeat-0001"), PlayerId("forged-process-actor"), 0,
        )) is Result.Success)
        val received = checkpoint.await()
        check(received is HostMessage.Heartbeat && received.header == header(1, "process-host-checkpoint-0001"))
        check(room.closeForRetry() is Result.Success)
        // Test runner synchronizes only after the separate host observed closure.
        check(readln() == "CLOSE_OBSERVED")
        val creations = fixture.realKitCreationAttempts
        check(transport.resumeLastSession(HostedGameProtocol(GameId("different-game"), 1)) ==
            Result.Failure(NetError.IncompatibleProtocol))
        check(fixture.realKitCreationAttempts == creations && store.loadResumeCandidate() == Result.Success(before))
        println("SELECTED_GAME_REJECTION_PASS")
        check(transport.resumeLastSession(HostedGameProtocol(game.gameId, 2)) ==
            Result.Failure(NetError.IncompatibleProtocol))
        check(fixture.realKitCreationAttempts == creations && store.loadResumeCandidate() == Result.Success(before))
        println("SELECTED_VERSION_REJECTION_PASS")
        val result = transport.resumeLastSession(game)
        check(result is Result.Success)
        val resumed = result.data
        onRoom(resumed)
        check(resumed.selfPlayerId == room.selfPlayerId && fixture.realKitCreationAttempts == creations + 1)
        println("SAME_SEAT_RESUME_PASS")
        val rotatedResult = store.loadResumeCandidate()
        check(rotatedResult is Result.Success)
        val rotated = checkNotNull(rotatedResult.data)
        check(rotated.membershipId == before.membershipId && rotated.playerId == before.playerId)
        check(rotated.generation > before.generation && rotated.secret != before.secret)
        check(rotated.gameId == game.gameId.raw && rotated.gameVersion == game.gameVersion)
        println("ROTATED_CREDENTIAL_PASS")
        val terminal = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(15_000) { resumed.incoming.first() }
        }
        check(resumed.sendToHost(PeerMessage.SessionHeartbeat(
            header(2, "process-peer-heartbeat-0002"), PlayerId("forged-process-actor"), 0,
        )) is Result.Success)
        val response = terminal.await()
        check(response is HostMessage.SessionEnded && response.header == header(3, "process-host-terminal-0001"))
        check(response.reason == SessionEndReason.Cancelled && response.finalRevision == 0L)
        println("BIDIRECTIONAL_PASS")
        check(resumed.commitValidatedSessionEnd(response) == Result.Success(SessionEndCommitStatus.Committed))
        check(store.loadResumeCandidate() == Result.Success(null))
        println("REVOKED_MEMBERSHIP_PASS")
    }
}
