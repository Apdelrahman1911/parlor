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
import com.parlor.networking.room.SendTarget
import com.parlor.networking.transport.HostConfig
import dev.p2pkit.core.AppId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.system.exitProcess

/** Opt-in synthetic process harness; not shipping or normal test-discovery source. */
object MultiProcessEndpoint {
    private fun header(sequence: Long, id: String) = SessionEnvelopeHeader(
        protocol = ProtocolVersion(), sessionId = SessionId("process-fixture-session"),
        gameId = GameId("process-fixture-game"), gameVersion = 1,
        messageId = id, sequence = sequence,
    )

    @JvmStatic
    fun main(args: Array<String>) {
        try {
            check(args.size == 2 && args[0] in setOf("host", "peer"))
            check(args[1].matches(Regex("[a-f0-9]{32}")))
            runBlocking {
                val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
                var room: LocalRoom? = null
                try {
                    withTimeout(90_000) {
                        val transport = LoopbackDeviceFixture(Dispatchers.IO).transport(
                            AppId("com.parlor.p2p.process.${args[1]}"), args[0] + "-fixture", scope,
                        )
                        if (args[0] == "host") {
                            val result = transport.host(HostConfig(hostDisplayName = "Process Fixture"))
                            check(result is Result.Success)
                            room = result.data
                            verifyHost(result.data)
                        } else {
                            val code = readln()
                            check(code.matches(Regex("[A-Z0-9]{6}")))
                            val result = transport.join(code, "process-peer")
                            check(result is Result.Success)
                            room = result.data
                            verifyPeer(result.data)
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
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (@Suppress("TooGenericExceptionCaught") failure: Throwable) {
            // Terminal CLI-only failure boundary: fail the owned JVM, never normalize/retry gameplay.
            // Never echo assertion values, credentials, endpoints or exception messages.
            println("ENDPOINT_FAIL type=${failure.javaClass.name}")
            exitProcess(1)
        }
    }

    private suspend fun verifyHost(room: LocalRoom) = coroutineScope {
        check(room.isHost)
        val admission = async(start = CoroutineStart.UNDISPATCHED) {
            val pending = withTimeout(35_000) {
                room.pendingAdmissions.first { it.size == 1 }.single()
            }
            check(pending.displayName == "process-peer" && !pending.isRejoin)
            check(pending.playerId != room.selfPlayerId)
            check(room.approveAdmission(pending.playerId) is Result.Success)
            pending.playerId
        }
        val inbound = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(45_000) { room.incoming.first() }
        }
        println("ROOM_CODE " + room.info.value.code)
        System.out.flush()
        val player = admission.await()
        withTimeout(10_000) { room.members.first { members -> members.any { it.playerId == player && it.connected } } }
        val message = inbound.await()
        check(message is PeerMessage.SessionHeartbeat)
        check(message.actor == player && message.actor != PlayerId("forged-process-actor"))
        check(message.header == header(0, "process-peer-heartbeat-0001"))
        println("AUTHENTICATED_ACTOR_PASS")
        check(room.send(SendTarget.Direct(player), HostMessage.SessionEnded(
            header(1, "process-host-terminal-0001"), SessionEndReason.Cancelled, 0,
        )) is Result.Success)
        withTimeout(15_000) { room.members.first { members -> members.none { it.playerId == player && it.connected } } }
        println("PEER_DISCONNECT_PASS")
    }

    private suspend fun verifyPeer(room: LocalRoom) = coroutineScope {
        check(!room.isHost)
        val inbound = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(15_000) { room.incoming.first() }
        }
        check(room.sendToHost(PeerMessage.SessionHeartbeat(
            header(0, "process-peer-heartbeat-0001"), PlayerId("forged-process-actor"), 0,
        )) is Result.Success)
        val response = inbound.await()
        check(response is HostMessage.SessionEnded)
        check(response.header == header(1, "process-host-terminal-0001"))
        check(response.reason == SessionEndReason.Cancelled && response.finalRevision == 0L)
        println("BIDIRECTIONAL_PASS")
    }
}
