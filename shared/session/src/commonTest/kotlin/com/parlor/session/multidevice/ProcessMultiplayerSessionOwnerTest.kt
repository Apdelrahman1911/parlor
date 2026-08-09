package com.parlor.session.multidevice

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import com.parlor.core.ids.GameId
import com.parlor.core.ids.PlayerId
import com.parlor.core.result.Result
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.PeerMessage
import com.parlor.networking.protocol.RoomMessage
import com.parlor.networking.protocol.SessionEndReason
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.networking.room.RoomInfo
import com.parlor.networking.room.RoomMember
import com.parlor.networking.room.SendTarget
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProcessMultiplayerSessionOwnerTest {
    @Test
    fun sameRouteReattachesWithoutOpeningAnotherRoom() = runTest {
        val owner = ProcessMultiplayerSessionOwner(backgroundScope)
        val route = hostRoute()
        val room = FakeRoom(isHost = true)
        var opens = 0

        val first = owner.acquire(route, hostSeed = 41L) {
            opens++
            Result.Success(room)
        }
        val second = owner.acquire(route, hostSeed = 999L) {
            opens++
            Result.Success(FakeRoom(isHost = true))
        }

        assertThat((first as Result.Success).data).isSameInstanceAs((second as Result.Success).data)
        assertThat(second.data.room).isSameInstanceAs(room)
        assertThat(second.data.hostSeed).isEqualTo(41L)
        assertThat(opens).isEqualTo(1)
    }

    @Test
    fun aDifferentRouteCannotReplaceAnActiveSession() = runTest {
        val owner = ProcessMultiplayerSessionOwner(backgroundScope)
        owner.acquire(hostRoute(), hostSeed = 1L) { Result.Success(FakeRoom(true)) }

        val conflict = owner.acquire(
            route = MultiplayerSessionRoute.host(GameId("mafia"), "Host"),
            hostSeed = 2L,
        ) { Result.Success(FakeRoom(true)) }

        assertThat(conflict).isEqualTo(Result.Failure(NetError.AlreadyConnected))
    }

    @Test
    fun cancellingOneUiWaiterDoesNotCancelProcessOwnedOpen() = runTest {
        val owner = ProcessMultiplayerSessionOwner(backgroundScope)
        val route = peerRoute()
        val gate = CompletableDeferred<Unit>()
        val room = FakeRoom(isHost = false)
        var opens = 0

        val firstWaiter = launch {
            owner.acquire(route) {
                opens++
                gate.await()
                Result.Success(room)
            }
        }
        runCurrent()
        firstWaiter.cancelAndJoin()

        val replacementWaiter = async {
            owner.acquire(route) {
                opens++
                Result.Success(FakeRoom(false))
            }
        }
        gate.complete(Unit)
        advanceUntilIdle()

        assertThat((replacementWaiter.await() as Result.Success).data.room).isSameInstanceAs(room)
        assertThat(opens).isEqualTo(1)
    }

    @Test
    fun admissionFreezeIsExactlyOnceAcrossConcurrentUiRequests() = runTest {
        val owner = ProcessMultiplayerSessionOwner(backgroundScope)
        val member = RoomMember(PlayerId("peer"), "Peer", connected = true)
        val gate = CompletableDeferred<Unit>()
        val room = FakeRoom(isHost = true).apply {
            closeAdmissionsBlock = {
                gate.await()
                Result.Success(listOf(member))
            }
        }
        val session = (owner.acquire(hostRoute(), 7L) { Result.Success(room) } as Result.Success).data

        val first = async { session.freezeAdmissions() }
        val second = async { session.freezeAdmissions() }
        runCurrent()
        assertThat(room.closeAdmissionsCalls).isEqualTo(1)
        gate.complete(Unit)
        advanceUntilIdle()

        assertThat(first.await()).isEqualTo(Result.Success(listOf(member)))
        assertThat(second.await()).isEqualTo(Result.Success(listOf(member)))
        assertThat(session.frozenRoster.value).isEqualTo(listOf(member))
    }

    @Test
    fun checkpointAndRuntimeSurviveUiReattachmentAndRejectWrongKinds() = runTest {
        val owner = ProcessMultiplayerSessionOwner(backgroundScope)
        val route = hostRoute()
        val session = (owner.acquire(route, 5L) { Result.Success(FakeRoom(true)) } as Result.Success).data
        val checkpoint = FakeCheckpoint("whodunit-start")
        val runtime = FakeRuntime("whodunit-host")

        session.getOrCreateCheckpoint(checkpoint.checkpointKind) { checkpoint }
        session.getOrCreateRuntime(runtime.runtimeKind) { runtime }
        val reattached = (owner.acquire(route, 999L) { Result.Success(FakeRoom(true)) } as Result.Success).data
        val sameCheckpoint = reattached.getOrCreateCheckpoint(checkpoint.checkpointKind) {
            FakeCheckpoint("whodunit-start")
        }
        val sameRuntime = reattached.getOrCreateRuntime(runtime.runtimeKind) {
            FakeRuntime("whodunit-host")
        }
        val conflict = reattached.getOrCreateRuntime("mafia-host") {
            FakeRuntime("mafia-host")
        }

        assertThat((sameCheckpoint as RetainedValueResult.Ready).value).isSameInstanceAs(checkpoint)
        assertThat((sameRuntime as RetainedValueResult.Ready).value).isSameInstanceAs(runtime)
        assertThat(conflict).isInstanceOf<RetainedValueResult.KindConflict>()
    }

    @Test
    fun peerRetryRetainsMembershipAndForcesResumeOnTheNextRoom() = runTest {
        val owner = ProcessMultiplayerSessionOwner(backgroundScope)
        val route = peerRoute()
        val firstRoom = FakeRoom(isHost = false)
        val first = (owner.acquire(route) { mode ->
            assertThat(mode).isEqualTo(MultiplayerOpenMode.Join)
            Result.Success(firstRoom)
        } as Result.Success).data

        assertThat(owner.preparePeerRetry(first, NetError.Timeout)).isEqualTo(Result.Success(Unit))
        assertThat(firstRoom.closeForRetryCalls).isEqualTo(1)
        assertThat(owner.state.value).isInstanceOf<ProcessMultiplayerState.Retryable>()

        val resumedRoom = FakeRoom(isHost = false)
        val resumed = owner.acquire(route) { mode ->
            assertThat(mode).isEqualTo(MultiplayerOpenMode.Resume)
            Result.Success(resumedRoom)
        }

        assertThat((resumed as Result.Success).data.room).isSameInstanceAs(resumedRoom)
        assertThat(resumed.data).isNotSameInstanceAs(first)
        assertThat(firstRoom.discardRejoinCalls).isEqualTo(0)
    }

    @Test
    fun failedPeerFinalLeaveKeepsSessionAndConcurrentRetryClosesExactlyOnce() = runTest {
        val owner = ProcessMultiplayerSessionOwner(backgroundScope)
        val route = peerRoute()
        val room = FakeRoom(isHost = false).apply {
            finalLeaveResults += Result.Failure(NetError.TransportFailure("secure delete failed"))
            finalLeaveResults += Result.Success(Unit)
        }
        val session = (owner.acquire(route) { Result.Success(room) } as Result.Success).data
        val runtime = FakeRuntime("peer-runtime")
        session.getOrCreateRuntime(runtime.runtimeKind) { runtime }

        val failed = owner.finalLeave(session, SessionEndReason.Cancelled)
        assertThat(failed).isEqualTo(Result.Failure(NetError.TransportFailure("secure delete failed")))
        assertThat((owner.state.value as ProcessMultiplayerState.Active).session).isSameInstanceAs(session)
        assertThat(runtime.closed).isFalse()

        val first = async { owner.finalLeave(session, SessionEndReason.Cancelled) }
        val second = async { owner.finalLeave(session, SessionEndReason.Cancelled) }
        advanceUntilIdle()

        assertThat(first.await()).isEqualTo(Result.Success(Unit))
        assertThat(second.await()).isEqualTo(Result.Success(Unit))
        assertThat(room.finalLeaveCalls).isEqualTo(2)
        assertThat(runtime.closed).isTrue()
        assertThat(owner.state.value).isEqualTo(ProcessMultiplayerState.Idle)
    }

    @Test
    fun hostRetryTerminatesRuntimeBeforeLeavingAndKeepsRouteRecoverable() = runTest {
        val owner = ProcessMultiplayerSessionOwner(backgroundScope)
        val route = hostRoute()
        val firstRoom = FakeRoom(isHost = true)
        val first = (owner.acquire(route, 71L) { Result.Success(firstRoom) } as Result.Success).data
        val runtime = FakeRuntime("host-runtime")
        first.getOrCreateRuntime(runtime.runtimeKind) { runtime }

        val closed = owner.prepareHostRetry(first)

        assertThat(closed).isEqualTo(Result.Success(Unit))
        assertThat(runtime.terminatedWith).isEqualTo(listOf(SessionEndReason.Cancelled))
        assertThat(runtime.closed).isTrue()
        assertThat(firstRoom.leaveCalls).isEqualTo(1)
        assertThat(owner.state.value).isInstanceOf<ProcessMultiplayerState.Retryable>()

        val replacement = FakeRoom(isHost = true)
        val reopened = owner.acquire(route, 72L) { mode ->
            assertThat(mode).isEqualTo(MultiplayerOpenMode.Host)
            Result.Success(replacement)
        }
        assertThat((reopened as Result.Success).data.room).isSameInstanceAs(replacement)
        assertThat(reopened.data.hostSeed).isEqualTo(72L)
    }

    @Test
    fun aNewOwnerModelsProcessDeathAsNoInMemoryHostSession() = runTest {
        val oldOwner = ProcessMultiplayerSessionOwner(backgroundScope)
        oldOwner.acquire(hostRoute(), 1L) { Result.Success(FakeRoom(true)) }

        val relaunchedOwner = ProcessMultiplayerSessionOwner(backgroundScope)

        assertThat(relaunchedOwner.state.value).isEqualTo(ProcessMultiplayerState.Idle)
    }

    private fun hostRoute(): MultiplayerSessionRoute = MultiplayerSessionRoute.host(
        gameId = GameId("whodunit"),
        displayName = "Host",
        contentId = "last-dinner",
        modeId = "classic-vote",
    )

    private fun peerRoute(): MultiplayerSessionRoute = MultiplayerSessionRoute.peer(
        gameId = GameId("whodunit"),
        displayName = "Peer",
        roomCode = "123456",
    )
}

private class FakeCheckpoint(
    override val checkpointKind: String,
) : RetainedMultiplayerCheckpoint

private class FakeRuntime(
    override val runtimeKind: String,
) : RetainedMultiplayerRuntime {
    val terminatedWith = mutableListOf<SessionEndReason>()
    var closed = false

    override suspend fun terminate(reason: SessionEndReason) {
        terminatedWith += reason
    }

    override fun close() {
        closed = true
    }
}

private class FakeRoom(
    override val isHost: Boolean,
) : LocalRoom {
    private val hostId = PlayerId("host")
    override val info = MutableStateFlow(
        RoomInfo(
            code = "123456",
            displayName = "Room",
            hostPlayerId = hostId,
            status = if (isHost) RoomInfo.Status.Hosting else RoomInfo.Status.Joined,
        ),
    )
    override val members = MutableStateFlow<List<RoomMember>>(emptyList())
    override val incoming: Flow<RoomMessage> = emptyFlow()
    override val selfPlayerId: PlayerId = if (isHost) hostId else PlayerId("peer")

    var closeAdmissionsCalls = 0
    var closeAdmissionsBlock: suspend () -> Result<List<RoomMember>, NetError> = {
        Result.Success(members.value)
    }
    var closeForRetryCalls = 0
    var discardRejoinCalls = 0
    var finalLeaveCalls = 0
    var leaveCalls = 0
    val finalLeaveResults = ArrayDeque<Result<Unit, NetError>>()

    override suspend fun closeAdmissions(): Result<List<RoomMember>, NetError> {
        closeAdmissionsCalls++
        return closeAdmissionsBlock()
    }

    override suspend fun send(
        target: SendTarget,
        message: HostMessage,
    ): Result<Unit, NetError> = Result.Success(Unit)

    override suspend fun sendToHost(message: PeerMessage): Result<Unit, NetError> =
        Result.Success(Unit)

    override suspend fun closeForRetry(): Result<Unit, NetError> {
        closeForRetryCalls++
        return Result.Success(Unit)
    }

    override suspend fun discardRejoinCapability(): Result<Unit, NetError> {
        discardRejoinCalls++
        return Result.Success(Unit)
    }

    override suspend fun finalLeave(): Result<Unit, NetError> {
        finalLeaveCalls++
        return finalLeaveResults.removeFirstOrNull() ?: Result.Success(Unit)
    }

    override suspend fun leave() {
        leaveCalls++
    }
}
