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
import com.parlor.networking.room.RoomLifecycleState
import com.parlor.networking.room.RoomMember
import com.parlor.networking.room.SendTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
class ProcessMultiplayerSessionOwnerTest {
    @Test
    fun multiplayerRoutesRejectNonCanonicalUserInputAtTheOwnershipBoundary() {
        listOf("", " Host", "Host\nAdmin", "A".repeat(33)).forEach { invalidName ->
            assertFailsWith<IllegalArgumentException> {
                MultiplayerSessionRoute.host(GameId("whodunit"), invalidName)
            }
        }
        listOf("ROOM", "123456", "ABC0DE", "abcdef").forEach { invalidCode ->
            assertFailsWith<IllegalArgumentException> {
                MultiplayerSessionRoute.peer(GameId("whodunit"), "Peer", invalidCode)
            }
        }
        assertFailsWith<IllegalArgumentException> {
            MultiplayerSessionRoute.peer(
                gameId = GameId("whodunit"),
                displayName = "Peer\nAdmin",
                roomCode = "A23456",
                resumeExistingSession = true,
            )
        }

        MultiplayerSessionRoute.peer(
            gameId = GameId("whodunit"),
            displayName = "",
            roomCode = "",
            resumeExistingSession = true,
        )
    }

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
    fun explicitLeaveDuringOpenWaitsForCancellationAndClosesAnOrphanRoom() = runTest {
        val owner = ProcessMultiplayerSessionOwner(backgroundScope)
        val route = peerRoute()
        val openStarted = CompletableDeferred<Unit>()
        val allowOpenToReturn = CompletableDeferred<Unit>()
        val room = FakeRoom(isHost = false)

        val waiter = async {
            owner.acquire(route) {
                openStarted.complete(Unit)
                withContext(NonCancellable) { allowOpenToReturn.await() }
                Result.Success(room)
            }
        }
        openStarted.await()
        val leave = async { owner.leaveRoute(route, SessionEndReason.Cancelled) }
        runCurrent()
        assertThat(owner.state.value).isInstanceOf<ProcessMultiplayerState.Closing>()
        val competingOpen = owner.acquire(
            MultiplayerSessionRoute.host(GameId("mafia"), "Host"),
            hostSeed = 2L,
        ) { Result.Success(FakeRoom(true)) }
        assertThat(competingOpen).isEqualTo(Result.Failure(NetError.CommandInFlight))
        allowOpenToReturn.complete(Unit)
        advanceUntilIdle()

        assertThat(leave.await()).isEqualTo(Result.Success(Unit))
        assertThat(waiter.isCancelled).isTrue()
        assertThat(room.leaveCalls).isEqualTo(1)
        assertThat(owner.state.value).isEqualTo(ProcessMultiplayerState.Idle)
    }

    @Test
    fun cancelledOpenRestoresIdleAndDoesNotPoisonTheNextAcquire() = runTest {
        val owner = ProcessMultiplayerSessionOwner(backgroundScope)
        val route = peerRoute()

        assertFailsWith<CancellationException> {
            owner.acquire(route) { throw CancellationException("adapter cancelled") }
        }
        assertThat(owner.state.value).isEqualTo(ProcessMultiplayerState.Idle)

        val replacement = FakeRoom(isHost = false)
        val reopened = owner.acquire(route) { Result.Success(replacement) }
        assertThat((reopened as Result.Success).data.room).isSameInstanceAs(replacement)
    }

    @Test
    fun explicitLeaveReportsOrphanCleanupFailureWithoutStrandingOwnership() = runTest {
        val owner = ProcessMultiplayerSessionOwner(backgroundScope)
        val route = peerRoute()
        val openStarted = CompletableDeferred<Unit>()
        val allowOpenToReturn = CompletableDeferred<Unit>()
        val room = FakeRoom(isHost = false).apply {
            leaveFailure = IllegalStateException("adapter cleanup failed")
        }
        val waiter = async {
            owner.acquire(route) {
                openStarted.complete(Unit)
                withContext(NonCancellable) { allowOpenToReturn.await() }
                Result.Success(room)
            }
        }
        openStarted.await()

        val leave = async { owner.leaveRoute(route, SessionEndReason.Cancelled) }
        allowOpenToReturn.complete(Unit)
        advanceUntilIdle()

        assertThat(leave.await()).isEqualTo(
            Result.Failure(NetError.TransportFailure("orphan room close failed")),
        )
        assertThat(waiter.isCancelled).isTrue()
        assertThat(room.leaveCalls).isEqualTo(1)
        assertThat(owner.state.value).isEqualTo(ProcessMultiplayerState.Idle)
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
    fun thrownAdmissionCloseBecomesRetryableWithoutPoisoningTheGate() = runTest {
        val owner = ProcessMultiplayerSessionOwner(backgroundScope)
        val member = RoomMember(PlayerId("peer"), "Peer", connected = true)
        var attempts = 0
        val room = FakeRoom(isHost = true).apply {
            closeAdmissionsBlock = {
                attempts++
                if (attempts == 1) throw IllegalStateException("adapter failure")
                Result.Success(listOf(member))
            }
        }
        val session = (owner.acquire(hostRoute(), 7L) { Result.Success(room) } as Result.Success).data

        assertThat(session.freezeAdmissions()).isEqualTo(
            Result.Failure(NetError.TransportFailure("admission close failed")),
        )
        assertThat(session.frozenRoster.value).isEqualTo(null)

        assertThat(session.freezeAdmissions()).isEqualTo(Result.Success(listOf(member)))
        assertThat(room.closeAdmissionsCalls).isEqualTo(2)
        assertThat(session.frozenRoster.value).isEqualTo(listOf(member))
    }

    @Test
    fun cancelledAdmissionCloseDoesNotPoisonTheGate() = runTest {
        val owner = ProcessMultiplayerSessionOwner(backgroundScope)
        val member = RoomMember(PlayerId("peer"), "Peer", connected = true)
        var attempts = 0
        val room = FakeRoom(isHost = true).apply {
            closeAdmissionsBlock = {
                attempts++
                if (attempts == 1) throw CancellationException("adapter cancelled")
                Result.Success(listOf(member))
            }
        }
        val session = (owner.acquire(hostRoute(), 7L) { Result.Success(room) } as Result.Success).data

        assertFailsWith<CancellationException> { session.freezeAdmissions() }

        assertThat(session.freezeAdmissions()).isEqualTo(Result.Success(listOf(member)))
        assertThat(room.closeAdmissionsCalls).isEqualTo(2)
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
    fun retainedFactoryFailureIsExplicitAndDoesNotPoisonAReplacement() = runTest {
        val owner = ProcessMultiplayerSessionOwner(backgroundScope)
        val session = (
            owner.acquire(hostRoute(), 5L) { Result.Success(FakeRoom(true)) } as Result.Success
        ).data

        val checkpointFailure = session.getOrCreateCheckpoint("checkpoint") {
            throw IllegalStateException("checkpoint constructor failed")
        }
        val runtimeFailure = session.getOrCreateRuntime("runtime") {
            throw IllegalStateException("runtime constructor failed")
        }

        assertThat(checkpointFailure).isEqualTo(
            RetainedValueResult.CreationFailed(
                NetError.TransportFailure("checkpoint creation failed"),
            ),
        )
        assertThat(runtimeFailure).isEqualTo(
            RetainedValueResult.CreationFailed(
                NetError.TransportFailure("runtime creation failed"),
            ),
        )

        val checkpoint = FakeCheckpoint("checkpoint")
        val runtime = FakeRuntime("runtime")
        assertThat(
            (session.getOrCreateCheckpoint("checkpoint") { checkpoint } as
                RetainedValueResult.Ready).value,
        ).isSameInstanceAs(checkpoint)
        assertThat(
            (session.getOrCreateRuntime("runtime") { runtime } as
                RetainedValueResult.Ready).value,
        ).isSameInstanceAs(runtime)
    }

    @Test
    fun runtimeFactoryKindMismatchClosesTheUninstalledCandidate() = runTest {
        val owner = ProcessMultiplayerSessionOwner(backgroundScope)
        val session = (
            owner.acquire(hostRoute(), 5L) { Result.Success(FakeRoom(true)) } as Result.Success
        ).data
        val wrong = FakeRuntime("wrong-runtime")

        val mismatch = session.getOrCreateRuntime("expected-runtime") { wrong }

        assertThat(mismatch).isEqualTo(
            RetainedValueResult.KindConflict("expected-runtime", "wrong-runtime"),
        )
        assertThat(wrong.closed).isTrue()
        val replacement = FakeRuntime("expected-runtime")
        val installed = session.getOrCreateRuntime("expected-runtime") { replacement }
        assertThat((installed as RetainedValueResult.Ready).value).isSameInstanceAs(replacement)
    }

    @Test
    fun retainedValuesCannotBeCreatedAfterTheSessionIsReleased() = runTest {
        val owner = ProcessMultiplayerSessionOwner(backgroundScope)
        val session = (
            owner.acquire(hostRoute(), 5L) { Result.Success(FakeRoom(true)) } as Result.Success
        ).data
        assertThat(owner.finalLeave(session, SessionEndReason.Cancelled)).isEqualTo(
            Result.Success(Unit),
        )
        var checkpointFactories = 0
        var runtimeFactories = 0

        val checkpoint = session.getOrCreateCheckpoint("late-checkpoint") {
            checkpointFactories++
            FakeCheckpoint("late-checkpoint")
        }
        val runtime = session.getOrCreateRuntime("late-runtime") {
            runtimeFactories++
            FakeRuntime("late-runtime")
        }

        assertThat(checkpoint).isEqualTo(
            RetainedValueResult.CreationFailed(NetError.NotConnected),
        )
        assertThat(runtime).isEqualTo(
            RetainedValueResult.CreationFailed(NetError.NotConnected),
        )
        assertThat(checkpointFactories).isEqualTo(0)
        assertThat(runtimeFactories).isEqualTo(0)
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
    fun leaveRacingPeerRetryWaitsThenDiscardsTheRetainedCredential() = runTest {
        val owner = ProcessMultiplayerSessionOwner(backgroundScope)
        val route = peerRoute()
        val retryCloseStarted = CompletableDeferred<Unit>()
        val allowRetryClose = CompletableDeferred<Unit>()
        val room = FakeRoom(isHost = false).apply {
            closeForRetryBlock = {
                retryCloseStarted.complete(Unit)
                allowRetryClose.await()
                Result.Success(Unit)
            }
        }
        val session = (owner.acquire(route) { Result.Success(room) } as Result.Success).data

        val retry = async { owner.preparePeerRetry(session, NetError.Timeout) }
        retryCloseStarted.await()
        val leave = async { owner.leaveRoute(route, SessionEndReason.Cancelled) }
        allowRetryClose.complete(Unit)
        advanceUntilIdle()

        assertThat(retry.await()).isEqualTo(Result.Success(Unit))
        assertThat(leave.await()).isEqualTo(Result.Success(Unit))
        assertThat(room.discardRejoinCalls).isEqualTo(1)
        assertThat(owner.state.value).isEqualTo(ProcessMultiplayerState.Idle)
    }

    @Test
    fun failedPeerRetryKeepsTheLiveSessionAndCanBeRetried() = runTest {
        val owner = ProcessMultiplayerSessionOwner(backgroundScope)
        val route = peerRoute()
        val room = FakeRoom(isHost = false).apply {
            closeForRetryResults += Result.Failure(NetError.TransportFailure("close failed"))
            closeForRetryResults += Result.Success(Unit)
        }
        val session = (owner.acquire(route) { Result.Success(room) } as Result.Success).data
        val runtime = FakeRuntime("peer-runtime")
        session.getOrCreateRuntime(runtime.runtimeKind) { runtime }

        val failed = owner.preparePeerRetry(session, NetError.Timeout)

        assertThat(failed).isEqualTo(Result.Failure(NetError.TransportFailure("close failed")))
        assertThat((owner.state.value as ProcessMultiplayerState.Active).session)
            .isSameInstanceAs(session)
        assertThat(runtime.closed).isFalse()

        val retried = owner.preparePeerRetry(session, NetError.Timeout)

        assertThat(retried).isEqualTo(Result.Success(Unit))
        assertThat(room.closeForRetryCalls).isEqualTo(2)
        assertThat(runtime.closed).isTrue()
        assertThat(owner.state.value).isInstanceOf<ProcessMultiplayerState.Retryable>()
    }

    @Test
    fun peerRetryRuntimeCloseFailureCannotStrandTheOwnerInClosing() = runTest {
        val owner = ProcessMultiplayerSessionOwner(backgroundScope)
        val route = peerRoute()
        val room = FakeRoom(isHost = false)
        val session = (owner.acquire(route) { Result.Success(room) } as Result.Success).data
        val runtime = FakeRuntime(
            runtimeKind = "peer-runtime",
            closeFailure = IllegalStateException("runtime close failed"),
        )
        session.getOrCreateRuntime(runtime.runtimeKind) { runtime }

        val prepared = owner.preparePeerRetry(session, NetError.Timeout)

        assertThat(prepared).isEqualTo(
            Result.Failure(NetError.TransportFailure("runtime close failed")),
        )
        assertThat(runtime.closeCalls).isEqualTo(1)
        assertThat(owner.state.value).isInstanceOf<ProcessMultiplayerState.Retryable>()

        val replacement = FakeRoom(isHost = false)
        val resumed = owner.acquire(route) { mode ->
            assertThat(mode).isEqualTo(MultiplayerOpenMode.Resume)
            Result.Success(replacement)
        }
        assertThat((resumed as Result.Success).data.room).isSameInstanceAs(replacement)
    }

    @Test
    fun cancelledPeerRetryCloseRestoresActiveStateAndPropagatesCancellation() = runTest {
        val owner = ProcessMultiplayerSessionOwner(backgroundScope)
        val route = peerRoute()
        val room = FakeRoom(isHost = false).apply {
            closeForRetryBlock = { throw CancellationException("adapter cancelled") }
        }
        val session = (owner.acquire(route) { Result.Success(room) } as Result.Success).data
        val runtime = FakeRuntime("peer-runtime")
        session.getOrCreateRuntime(runtime.runtimeKind) { runtime }

        assertFailsWith<CancellationException> {
            owner.preparePeerRetry(session, NetError.Timeout)
        }

        assertThat((owner.state.value as ProcessMultiplayerState.Active).session)
            .isSameInstanceAs(session)
        assertThat(runtime.closed).isFalse()
    }

    @Test
    fun cancelledCredentialDiscardRemainsRetryableAndPropagatesCancellation() = runTest {
        val owner = ProcessMultiplayerSessionOwner(backgroundScope)
        val route = peerRoute()
        val room = FakeRoom(isHost = false)
        val session = (owner.acquire(route) { Result.Success(room) } as Result.Success).data
        assertThat(owner.preparePeerRetry(session, NetError.Timeout)).isEqualTo(Result.Success(Unit))
        room.discardRejoinBlock = { throw CancellationException("secure store cancelled") }

        assertFailsWith<CancellationException> {
            owner.discardRetainedRoute(route)
        }

        val failed = owner.state.value as ProcessMultiplayerState.Failed
        assertThat(failed.error).isEqualTo(NetError.SessionSuspended)
        room.discardRejoinBlock = { Result.Success(Unit) }
        assertThat(owner.discardRetainedRoute(route)).isEqualTo(Result.Success(Unit))
        assertThat(room.discardRejoinCalls).isEqualTo(2)
        assertThat(owner.state.value).isEqualTo(ProcessMultiplayerState.Idle)
    }

    @Test
    fun leavingAFailedOpenClearsTheRouteForANewSession() = runTest {
        val owner = ProcessMultiplayerSessionOwner(backgroundScope)
        val route = peerRoute()

        val failed = owner.acquire(route) { Result.Failure(NetError.Timeout) }
        assertThat(failed).isEqualTo(Result.Failure(NetError.Timeout))
        assertThat(owner.state.value).isInstanceOf<ProcessMultiplayerState.Failed>()

        assertThat(owner.leaveRoute(route, SessionEndReason.Cancelled))
            .isEqualTo(Result.Success(Unit))
        assertThat(owner.state.value).isEqualTo(ProcessMultiplayerState.Idle)

        val replacement = FakeRoom(isHost = false)
        val reopened = owner.acquire(route) { Result.Success(replacement) }
        assertThat((reopened as Result.Success).data.room).isSameInstanceAs(replacement)
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
    fun hostFinalLeaveRuntimeCloseFailureStillReleasesOwnership() = runTest {
        val owner = ProcessMultiplayerSessionOwner(backgroundScope)
        val room = FakeRoom(isHost = true)
        val session = (
            owner.acquire(hostRoute(), 71L) { Result.Success(room) } as Result.Success
        ).data
        val runtime = FakeRuntime(
            runtimeKind = "host-runtime",
            closeFailure = IllegalStateException("runtime close failed"),
        )
        session.getOrCreateRuntime(runtime.runtimeKind) { runtime }

        val closed = owner.finalLeave(session, SessionEndReason.Cancelled)

        assertThat(closed).isEqualTo(
            Result.Failure(NetError.TransportFailure("runtime close failed")),
        )
        assertThat(runtime.terminatedWith).isEqualTo(listOf(SessionEndReason.Cancelled))
        assertThat(runtime.closeCalls).isEqualTo(1)
        assertThat(room.leaveCalls).isEqualTo(1)
        assertThat(owner.state.value).isEqualTo(ProcessMultiplayerState.Idle)
    }

    @Test
    fun aNewOwnerModelsProcessDeathAsNoInMemoryHostSession() = runTest {
        val oldOwner = ProcessMultiplayerSessionOwner(backgroundScope)
        oldOwner.acquire(hostRoute(), 1L) { Result.Success(FakeRoom(true)) }

        val relaunchedOwner = ProcessMultiplayerSessionOwner(backgroundScope)

        assertThat(relaunchedOwner.state.value).isEqualTo(ProcessMultiplayerState.Idle)
    }

    @Test
    fun peerLifecycleExpiryReleasesRuntimeAndLeavesAnExplicitTerminalOwnerState() = runTest {
        val owner = ProcessMultiplayerSessionOwner(backgroundScope)
        val route = peerRoute()
        val room = FakeRoom(isHost = false)
        val session = (owner.acquire(route) { Result.Success(room) } as Result.Success).data
        val runtime = FakeRuntime("peer-runtime")
        session.getOrCreateRuntime(runtime.runtimeKind) { runtime }

        room.lifecycleState.value = RoomLifecycleState.Expired
        runCurrent()

        val failed = owner.state.value as ProcessMultiplayerState.Failed
        assertThat(failed.route).isEqualTo(route)
        assertThat(failed.error).isEqualTo(NetError.RejoinExpired)
        assertThat(failed.retryMode).isEqualTo(MultiplayerOpenMode.Resume)
        assertThat(runtime.closed).isTrue()
    }

    @Test
    fun finalLeaveWaitsForSessionChildrenToFinishCancellationCleanup() = runTest {
        val owner = ProcessMultiplayerSessionOwner(backgroundScope)
        val room = FakeRoom(isHost = true)
        val session = (
            owner.acquire(hostRoute(), 1L) { Result.Success(room) } as Result.Success
        ).data
        val cleanupStarted = CompletableDeferred<Unit>()
        val allowCleanup = CompletableDeferred<Unit>()
        val child = session.scope.launch {
            try {
                awaitCancellation()
            } finally {
                cleanupStarted.complete(Unit)
                withContext(NonCancellable) { allowCleanup.await() }
            }
        }
        runCurrent()

        val leaving = async { owner.finalLeave(session, SessionEndReason.Cancelled) }
        cleanupStarted.await()
        runCurrent()

        val completedBeforeCleanup = leaving.isCompleted
        allowCleanup.complete(Unit)
        assertThat(leaving.await()).isEqualTo(Result.Success(Unit))
        assertThat(completedBeforeCleanup).isFalse()
        assertThat(child.isCompleted).isTrue()
    }

    @Test
    fun staleExpiredLifecycleCannotReplaceANewerActiveSession() = runTest {
        val owner = ProcessMultiplayerSessionOwner(backgroundScope)
        val firstRoom = FakeRoom(isHost = false)
        val first = (owner.acquire(peerRoute()) { Result.Success(firstRoom) } as Result.Success).data
        assertThat(owner.finalLeave(first, SessionEndReason.Cancelled)).isEqualTo(Result.Success(Unit))

        val newerRoute = MultiplayerSessionRoute.peer(
            gameId = GameId("mafia"),
            displayName = "Peer",
            roomCode = "B23456",
        )
        val newer = (
            owner.acquire(newerRoute) { Result.Success(FakeRoom(isHost = false)) } as Result.Success
        ).data
        firstRoom.lifecycleState.value = RoomLifecycleState.Expired
        advanceUntilIdle()

        assertThat((owner.state.value as ProcessMultiplayerState.Active).session)
            .isSameInstanceAs(newer)
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
        roomCode = "A23456",
    )
}

private class FakeCheckpoint(
    override val checkpointKind: String,
) : RetainedMultiplayerCheckpoint

private class FakeRuntime(
    override val runtimeKind: String,
    private val closeFailure: Exception? = null,
) : RetainedMultiplayerRuntime {
    val terminatedWith = mutableListOf<SessionEndReason>()
    var closed = false
    var closeCalls = 0

    override suspend fun terminate(reason: SessionEndReason) {
        terminatedWith += reason
    }

    override suspend fun close() {
        closeCalls++
        closeFailure?.let { throw it }
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
    val lifecycleState = MutableStateFlow<RoomLifecycleState>(RoomLifecycleState.Active)
    override val lifecycle = lifecycleState
    override val incoming: Flow<RoomMessage> = emptyFlow()
    override val selfPlayerId: PlayerId = if (isHost) hostId else PlayerId("peer")

    var closeAdmissionsCalls = 0
    var closeAdmissionsBlock: suspend () -> Result<List<RoomMember>, NetError> = {
        Result.Success(members.value)
    }
    var closeForRetryCalls = 0
    var closeForRetryBlock: suspend () -> Result<Unit, NetError> = { Result.Success(Unit) }
    val closeForRetryResults = ArrayDeque<Result<Unit, NetError>>()
    var discardRejoinCalls = 0
    var discardRejoinBlock: suspend () -> Result<Unit, NetError> = { Result.Success(Unit) }
    var finalLeaveCalls = 0
    var leaveCalls = 0
    var leaveFailure: Exception? = null
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
        return closeForRetryResults.removeFirstOrNull() ?: closeForRetryBlock()
    }

    override suspend fun discardRejoinCapability(): Result<Unit, NetError> {
        discardRejoinCalls++
        return discardRejoinBlock()
    }

    override suspend fun finalLeave(): Result<Unit, NetError> {
        finalLeaveCalls++
        return finalLeaveResults.removeFirstOrNull() ?: Result.Success(Unit)
    }

    override suspend fun leave() {
        leaveCalls++
        leaveFailure?.let { throw it }
    }
}
