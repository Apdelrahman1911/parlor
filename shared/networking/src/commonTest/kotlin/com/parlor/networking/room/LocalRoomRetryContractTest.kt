package com.parlor.networking.room

import com.parlor.core.ids.PlayerId
import com.parlor.core.result.Result
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.PeerMessage
import com.parlor.networking.protocol.RoomMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LocalRoomRetryContractTest {
    @Test
    fun `fallback retry close maps an ordinary adapter exception`() = runTest {
        val room = ThrowingRoom(IllegalStateException("private adapter detail"))

        assertEquals(
            Result.Failure(NetError.TransportFailure("room close failed")),
            room.closeForRetry(),
        )
        assertEquals(
            Result.Failure(NetError.TransportFailure("membership discard failed")),
            room.discardRejoinCapability(),
        )
    }

    @Test
    fun `fallback retry close propagates cancellation`() = runTest {
        val room = ThrowingRoom(CancellationException("cancelled"))

        assertFailsWith<CancellationException> { room.closeForRetry() }
        assertFailsWith<CancellationException> { room.discardRejoinCapability() }
    }

    @Test
    fun `fallback retry close propagates fatal errors`() = runTest {
        val room = ThrowingRoom(FatalTestError())

        assertFailsWith<FatalTestError> { room.closeForRetry() }
        assertFailsWith<FatalTestError> { room.discardRejoinCapability() }
    }
}

private class FatalTestError : Error()

private class ThrowingRoom(
    private val failure: Throwable,
) : LocalRoom {
    override val info: StateFlow<RoomInfo> = MutableStateFlow(
        RoomInfo("TEST", "Test", PlayerId("host"), RoomInfo.Status.Joined),
    )
    override val members: StateFlow<List<RoomMember>> = MutableStateFlow(emptyList())
    override val isHost: Boolean = false
    override val incoming: Flow<RoomMessage> = emptyFlow()
    override val selfPlayerId: PlayerId = PlayerId("peer")

    override suspend fun send(
        target: SendTarget,
        message: HostMessage,
    ): Result<Unit, NetError> = Result.Success(Unit)

    override suspend fun sendToHost(message: PeerMessage): Result<Unit, NetError> =
        Result.Success(Unit)

    override suspend fun leave(): Nothing = throw failure
}
