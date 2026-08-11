package com.parlor.app

import com.parlor.core.ids.GameId
import com.parlor.core.ids.PlayerId
import com.parlor.core.result.Result
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.PeerMessage
import com.parlor.networking.protocol.RoomMessage
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.networking.room.RoomInfo
import com.parlor.networking.room.RoomMember
import com.parlor.networking.room.SendTarget
import com.parlor.session.multidevice.MultiplayerSessionRoute
import com.parlor.session.multidevice.ProcessMultiplayerSessionOwner
import com.parlor.session.multidevice.ProcessMultiplayerState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DesktopProcessShutdownTest {

    @Test
    fun activeHostLeavesBeforeTheProcessScopeIsCancelled() = runTest {
        val processJob = SupervisorJob()
        val processScope = CoroutineScope(processJob + StandardTestDispatcher(testScheduler))
        val transportJob = SupervisorJob()
        val transportScope = CoroutineScope(
            transportJob + StandardTestDispatcher(testScheduler),
        )
        val owner = ProcessMultiplayerSessionOwner(processScope)
        val room = DesktopTestRoom(isHost = true)
        val route = MultiplayerSessionRoute.host(
            gameId = GameId("whodunit"),
            displayName = "Host",
        )

        assertIs<Result.Success<*>>(
            owner.acquire(route, hostSeed = 7L) { Result.Success(room) },
        )

        val result = shutdownDesktopMultiplayer(owner, processScope, transportScope)

        assertIs<Result.Success<Unit>>(result)
        assertEquals(1, room.leaveCalls)
        assertIs<ProcessMultiplayerState.Idle>(owner.state.value)
        assertFalse(processJob.isActive)
        assertFalse(transportJob.isActive)
    }

    @Test
    fun stalledLeaveIsBoundedAndStillCancelsTheProcessScope() = runTest {
        val processJob = SupervisorJob()
        val processScope = CoroutineScope(processJob + StandardTestDispatcher(testScheduler))
        val transportJob = SupervisorJob()
        val transportScope = CoroutineScope(
            transportJob + StandardTestDispatcher(testScheduler),
        )
        val owner = ProcessMultiplayerSessionOwner(processScope)
        val room = DesktopTestRoom(
            isHost = false,
            finalLeaveGate = CompletableDeferred(),
        )
        val route = MultiplayerSessionRoute.peer(
            gameId = GameId("whodunit"),
            displayName = "Peer",
            roomCode = "A23456",
        )
        assertIs<Result.Success<*>>(owner.acquire(route) { Result.Success(room) })

        val result = shutdownDesktopMultiplayer(
            sessionOwner = owner,
            sessionScope = processScope,
            transportScope = transportScope,
            timeoutMillis = 1_000L,
        )

        assertIs<Result.Failure<NetError>>(result)
        assertEquals(NetError.Timeout, result.error)
        assertEquals(1, room.finalLeaveCalls)
        assertFalse(processJob.isActive)
        assertFalse(transportJob.isActive)
    }

    @Test
    fun idleShutdownIsSuccessfulAndIdempotentlyCancelsTheScope() = runTest {
        val processJob = SupervisorJob()
        val processScope = CoroutineScope(processJob + StandardTestDispatcher(testScheduler))
        val transportJob = SupervisorJob()
        val transportScope = CoroutineScope(
            transportJob + StandardTestDispatcher(testScheduler),
        )
        val owner = ProcessMultiplayerSessionOwner(processScope)

        val first = shutdownDesktopMultiplayer(owner, processScope, transportScope)
        val second = shutdownDesktopMultiplayer(owner, processScope, transportScope)

        assertIs<Result.Success<Unit>>(first)
        assertIs<Result.Success<Unit>>(second)
        assertTrue(owner.state.value is ProcessMultiplayerState.Idle)
        assertFalse(processJob.isActive)
        assertFalse(transportJob.isActive)
    }
}

private class DesktopTestRoom(
    override val isHost: Boolean,
    private val finalLeaveGate: CompletableDeferred<Unit>? = null,
) : LocalRoom {
    private val hostId = PlayerId("host")
    override val info = MutableStateFlow(
        RoomInfo(
            code = "A23456",
            hostDisplayName = "Host",
            hostPlayerId = hostId,
            status = if (isHost) RoomInfo.Status.Hosting else RoomInfo.Status.Joined,
        ),
    )
    override val members = MutableStateFlow<List<RoomMember>>(emptyList())
    override val incoming: Flow<RoomMessage> = emptyFlow()
    override val selfPlayerId: PlayerId = if (isHost) hostId else PlayerId("peer")

    var finalLeaveCalls: Int = 0
    var leaveCalls: Int = 0

    override suspend fun send(
        target: SendTarget,
        message: HostMessage,
    ): Result<Unit, NetError> = Result.Success(Unit)

    override suspend fun sendToHost(message: PeerMessage): Result<Unit, NetError> =
        Result.Success(Unit)

    override suspend fun finalLeave(): Result<Unit, NetError> {
        finalLeaveCalls++
        finalLeaveGate?.await()
        return Result.Success(Unit)
    }

    override suspend fun leave() {
        leaveCalls++
    }
}
