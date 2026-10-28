package com.parlor.transport.p2p

import com.parlor.core.ids.GameId
import com.parlor.core.ids.PlayerId
import com.parlor.core.result.Result
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.PeerMessage
import com.parlor.networking.protocol.ResumableCredentialOffer
import com.parlor.networking.protocol.RoomMessageCodec
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.networking.transport.HostedGameProtocol
import com.parlor.networking.transport.JoinConfig
import com.parlor.networking.transport.ResumableSessionInfo
import com.parlor.storage.secure.InMemorySecureKeyValueBacking
import com.parlor.storage.secure.PlatformKeyedSecureStorage
import dev.p2pkit.core.AppId
import dev.p2pkit.core.ConnectionState
import dev.p2pkit.core.P2pKit
import dev.p2pkit.core.P2pMessage
import dev.p2pkit.core.Peer
import dev.p2pkit.core.PeerId
import dev.p2pkit.core.Platform
import dev.p2pkit.core.TransportKind
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PeerOpeningCancellationTest {
    @Test
    fun crossGameAdmissionRejectsBeforeCredentialStageOrConfirmation() = runTest {
        verifyGameAdmissionRejected(HostedGameProtocol(GameId("different-game"), 1))
    }

    @Test
    fun mismatchedGameVersionRejectsBeforeCredentialStageOrConfirmation() = runTest {
        verifyGameAdmissionRejected(HostedGameProtocol(GameId("fixture-game"), 2))
    }

    @Test
    fun missingAuthoritativeGameRejectsASelectedGameBinding() = runTest {
        verifyGameAdmissionRejected(HostedGameProtocol(GameId("fixture-game"), 1), missingGame = true)
    }

    @Test
    fun matchingGameAdmissionConfirmsAndRetainsTheSelectedBinding() = runTest {
        val fixture = OpeningFixture(backgroundScope, resume = false)
        fixture.prepare()
        val joined = fixture.transport.join(JoinConfig(
            code = "ABCDEF", displayName = "Alice",
            expectedGameProtocol = HostedGameProtocol(GameId("fixture-game"), 1),
        ))
        val room = assertIs<Result.Success<LocalRoom>>(joined).data
        try {
            assertEquals("fixture-game", fixture.committedCredential().gameId)
            assertEquals(1, fixture.committedCredential().gameVersion)
            assertTrue(fixture.session.sent.filterIsInstance<P2pMessage.Binary>().any {
                RoomMessageCodec().decode(it.bytes) is PeerMessage.AdmissionConfirmed
            })
        } finally {
            room.closeForRetry()
        }
    }

    private suspend fun TestScope.verifyGameAdmissionRejected(
        expected: HostedGameProtocol,
        missingGame: Boolean = false,
    ) {
        val fixture = OpeningFixture(backgroundScope, resume = false, missingGame = missingGame)
        fixture.prepare()
        try {
            assertEquals(Result.Failure(NetError.IncompatibleProtocol), fixture.transport.join(JoinConfig(
                code = "ABCDEF", displayName = "Alice", expectedGameProtocol = expected,
            )))
            assertEquals(Result.Success(null), fixture.store.loadResumeCandidate())
            assertFalse(fixture.session.sent.filterIsInstance<P2pMessage.Binary>().any {
                RoomMessageCodec().decode(it.bytes) is PeerMessage.AdmissionConfirmed
            })
            fixture.assertReleased()
        } finally {
            fixture.stopFakeIfNeeded()
        }
    }

    @Test
    fun crossGameResumeRejectsBeforeKitCreationAndPreservesCredential() = runTest {
        verifySelectedResumeRejected(HostedGameProtocol(GameId("different-game"), 1))
    }

    @Test
    fun mismatchedResumeVersionRejectsBeforeKitCreationAndPreservesCredential() = runTest {
        verifySelectedResumeRejected(HostedGameProtocol(GameId("fixture-game"), 2))
    }

    @Test
    fun missingResumeGameRejectsBeforeKitCreationAndPreservesCredential() = runTest {
        verifySelectedResumeRejected(HostedGameProtocol(GameId("fixture-game"), 1), missingGame = true)
    }

    @Test
    fun matchingSelectedResumeAuthenticatesAndRotatesWithoutChangingSeat() = runTest {
        val fixture = OpeningFixture(backgroundScope, resume = true)
        fixture.prepare()
        val before = fixture.committedCredential()
        val selection = assertNotNull(assertIs<Result.Success<ResumableSessionInfo?>>(
            fixture.transport.resumableSession(),
        ).data?.deletionSelection)
        var room: LocalRoom? = null
        try {
            room = assertIs<Result.Success<LocalRoom>>(fixture.transport.resumeLastSession(
                HostedGameProtocol(GameId("fixture-game"), 1), selection,
            )).data
            assertEquals(1, fixture.createCalls)
            assertEquals(1, fixture.connectCalls)
            val rotated = fixture.committedCredential()
            assertEquals(before.playerId, rotated.playerId)
            assertEquals(before.membershipId, rotated.membershipId)
            assertEquals(before.gameId, rotated.gameId)
            assertEquals(before.gameVersion, rotated.gameVersion)
            assertEquals(fixture.offer.generation, rotated.generation)
            assertEquals(Result.Success(Unit), room.closeForRetry())
            fixture.assertReleased()
        } finally {
            room?.closeForRetry()
            fixture.stopFakeIfNeeded()
        }
    }

    @Test
    fun replacedGameMetadataBetweenSummaryAndResumeIsCheckedOnTheUsedCredential() = runTest {
        val fixture = OpeningFixture(backgroundScope, resume = true)
        fixture.prepare()
        val summary = assertNotNull(assertIs<Result.Success<ResumableSessionInfo?>>(
            fixture.transport.resumableSession(),
        ).data)
        assertEquals(GameId("fixture-game"), summary.gameId)
        assertEquals(Result.Success(Unit), fixture.store.updateGame("different-game", 2))
        val replaced = fixture.committedCredential()
        try {
            assertEquals(Result.Failure(NetError.IncompatibleProtocol), fixture.transport.resumeLastSession(
                HostedGameProtocol(summary.gameId, summary.gameVersion),
            ))
            assertEquals(0, fixture.createCalls)
            assertEquals(0, fixture.connectCalls)
            assertTrue(fixture.session.sent.isEmpty())
            assertEquals(replaced, fixture.committedCredential())
        } finally {
            fixture.stopFakeIfNeeded()
        }
    }

    @Test
    fun sameGameReplacementAfterHomeReadCannotOpenAnotherMembership() = runTest {
        val fixture = OpeningFixture(backgroundScope, resume = true)
        fixture.prepare()
        val summary = assertNotNull(assertIs<Result.Success<ResumableSessionInfo?>>(
            fixture.transport.resumableSession(),
        ).data)
        val replacement = fixture.committedCredential().copy(
            membershipId = "another-membership", offerId = "another-offer",
        )
        assertEquals(Result.Success(Unit), fixture.store.stage(replacement))
        assertEquals(Result.Success(Unit), fixture.store.commit(replacement.offerId, replacement.generation))
        var room: LocalRoom? = null
        try {
            val result = fixture.transport.resumeLastSession(
                HostedGameProtocol(summary.gameId, summary.gameVersion), assertNotNull(summary.deletionSelection),
            )
            room = (result as? Result.Success)?.data
            assertEquals(0, fixture.createCalls, "Home's observed membership must survive the caller boundary")
            assertEquals(0, fixture.connectCalls)
            assertEquals(Result.Failure(NetError.NotConnected), result)
            assertEquals(replacement, fixture.committedCredential())
            assertEquals(Result.Success(Unit), fixture.transport.discardResumableMembership(
                assertNotNull(summary.deletionSelection),
            ))
            assertEquals(replacement, fixture.committedCredential(), "Leaving a stale route must preserve its replacement")
        } finally {
            room?.closeForRetry()
            fixture.stopFakeIfNeeded()
        }
    }

    @Test
    fun foreignOrAbsentSelectedMembershipCannotCreateAKit() = runTest {
        val fixture = OpeningFixture(backgroundScope, resume = true)
        fixture.prepare()
        val selection = assertNotNull(assertIs<Result.Success<ResumableSessionInfo?>>(
            fixture.transport.resumableSession(),
        ).data?.deletionSelection)
        val foreign = object : com.parlor.networking.transport.ResumableSessionSelection {}
        val protocol = HostedGameProtocol(GameId("fixture-game"), 1)
        try {
            assertEquals(Result.Failure(NetError.NotConnected), fixture.transport.resumeLastSession(protocol, foreign))
            assertEquals(Result.Failure(NetError.SecureStorageUnavailable), fixture.transport.discardResumableMembership(foreign))
            assertEquals(Result.Success(Unit), fixture.store.clear())
            assertEquals(Result.Failure(NetError.NotConnected), fixture.transport.resumeLastSession(protocol, selection))
            assertEquals(Result.Success(Unit), fixture.transport.discardResumableMembership(selection))
            assertEquals(0, fixture.createCalls)
            assertEquals(0, fixture.connectCalls)
        } finally {
            fixture.stopFakeIfNeeded()
        }
    }

    private suspend fun TestScope.verifySelectedResumeRejected(
        expected: HostedGameProtocol,
        missingGame: Boolean = false,
    ) {
        val fixture = OpeningFixture(backgroundScope, resume = true, missingGame = missingGame)
        fixture.prepare()
        val before = fixture.committedCredential()
        try {
            assertEquals(Result.Failure(NetError.IncompatibleProtocol), fixture.transport.resumeLastSession(expected))
            assertEquals(0, fixture.createCalls)
            assertEquals(0, fixture.connectCalls)
            assertEquals(0, fixture.stopAttempts)
            assertEquals(0, fixture.session.closeCalls)
            assertTrue(fixture.session.sent.isEmpty())
            assertEquals(before, fixture.committedCredential())
        } finally {
            fixture.stopFakeIfNeeded()
        }
    }

    @Test
    fun cancellingAdmissionReadyClosesUnreturnedRoomWithoutRevokingMembership() = runTest {
        verifyCancelledHandoff(resume = false, duringAck = false)
    }

    @Test
    fun cancellingAdmissionAckClosesUnreturnedRoomWithoutRevokingMembership() = runTest {
        verifyCancelledHandoff(resume = false, duringAck = true)
    }

    @Test
    fun cancellingResumeReadyClosesUnreturnedRoomWithoutRevokingMembership() = runTest {
        verifyCancelledHandoff(resume = true, duringAck = false)
    }

    @Test
    fun cancellingResumeAckClosesUnreturnedRoomWithoutRevokingMembership() = runTest {
        verifyCancelledHandoff(resume = true, duringAck = true)
    }

    @Test
    fun failedAdmissionReadyStopsKitOnlyOnceAndReleasesCollectors() = runTest {
        verifyReadyFailure(resume = false)
    }

    @Test
    fun failedResumeReadyStopsKitOnlyOnceAndReleasesCollectors() = runTest {
        verifyReadyFailure(resume = true)
    }

    @Test
    fun cancelledJoinRegistrationCannotResumeOrExpireAReplacementCredential() = runTest {
        verifyCancelledRegistration(resume = false)
    }

    @Test
    fun cancelledResumeRegistrationCannotResumeOrExpireAReplacementCredential() = runTest {
        verifyCancelledRegistration(resume = true)
    }

    @Test
    fun failedJoinRegistrationReleasesRoomAndDetachesLifecycle() = runTest {
        verifyRegistrationFailure(resume = false)
    }

    @Test
    fun failedResumeRegistrationReleasesRoomAndDetachesLifecycle() = runTest {
        verifyRegistrationFailure(resume = true)
    }

    @Test
    fun ordinaryAdmissionAckLossStillReturnsAnOwnedRoom() = runTest {
        verifyBestEffortAck(resume = false)
    }

    @Test
    fun ordinaryResumeAckLossStillReturnsAnOwnedRoom() = runTest {
        verifyBestEffortAck(resume = true)
    }

    private suspend fun TestScope.verifyCancelledHandoff(resume: Boolean, duringAck: Boolean) {
        val fixture = OpeningFixture(backgroundScope, resume)
        fixture.prepare()
        val entered = CompletableDeferred<Unit>()
        fixture.onPeerMessage = { message ->
            if (message.isHandoff(duringAck)) {
                entered.complete(Unit)
                awaitCancellation()
            }
        }
        val opening = async { fixture.open() }
        try {
            withTimeout(10_000L) { entered.await() }
            runCurrent()
            fixture.assertSubscribed()
            val committed = fixture.committedCredential()
            opening.cancelAndJoin()
            runCurrent()
            assertTrue(opening.isCancelled)
            fixture.assertReleased()
            assertEquals(committed, fixture.committedCredential())
            fixture.transport.notifyAppForegrounded()
            runCurrent()
            assertEquals(0, fixture.fake.foregroundCalls)
            assertEquals(1, fixture.connectCalls)
        } finally {
            opening.cancelAndJoin()
            fixture.stopFakeIfNeeded()
        }
    }

    private suspend fun TestScope.verifyReadyFailure(resume: Boolean) {
        val fixture = OpeningFixture(backgroundScope, resume)
        fixture.prepare()
        fixture.onPeerMessage = { message ->
            if (message.isHandoff(duringAck = false)) error("synthetic ready failure")
        }
        try {
            val result = fixture.open()
            assertIs<NetError.TransportFailure>(assertIs<Result.Failure<NetError>>(result).error)
            runCurrent()
            fixture.assertReleased()
            assertEquals(fixture.offer.generation, fixture.committedCredential().generation)
        } finally {
            fixture.stopFakeIfNeeded()
        }
    }

    private suspend fun TestScope.verifyCancelledRegistration(resume: Boolean) {
        val fixture = OpeningFixture(backgroundScope, resume)
        fixture.prepare()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        fixture.session.closeHandler = {
            if (fixture.session.closeCalls == 1) {
                // The registration's native close can finish NonCancellable;
                // caller cancellation must still close the unreturned owner.
                withContext(NonCancellable) {
                    entered.complete(Unit)
                    release.await()
                }
            }
        }
        fixture.transport.notifyAppBackgrounded()
        runCurrent()
        val opening = async { fixture.open() }
        try {
            withTimeout(10_000L) { entered.await() }
            runCurrent()
            fixture.assertSubscribed()
            fixture.transport.notifyAppForegrounded()
            runCurrent()
            opening.cancel()
            release.complete(Unit)
            opening.join()
            runCurrent()
            assertTrue(opening.isCancelled)
            fixture.assertReleased()
            assertEquals(0, fixture.fake.foregroundCalls)

            // Model a later successful same-membership rotation. The abandoned
            // owner's former expiry must not invalidate this newer credential.
            val committed = fixture.committedCredential()
            val replacement = committed.copy(
                offerId = "replacement-offer",
                generation = committed.generation + 1L,
                secret = "c".repeat(64),
            )
            assertEquals(Result.Success(Unit), fixture.store.stage(replacement))
            assertEquals(Result.Success(Unit), fixture.store.commit(replacement.offerId, replacement.generation))
            advanceTimeBy(P2pKitRoomTransport.APP_RESUME_GRACE_MS + 1L)
            runCurrent()
            assertEquals(replacement, fixture.committedCredential())
            assertEquals(1, fixture.connectCalls)
            fixture.transport.notifyAppBackgrounded()
            fixture.transport.notifyAppForegrounded()
            runCurrent()
            assertEquals(1, fixture.fake.backgroundCalls)
            assertEquals(0, fixture.fake.foregroundCalls)
        } finally {
            release.complete(Unit)
            opening.cancelAndJoin()
            fixture.stopFakeIfNeeded()
        }
    }

    private suspend fun TestScope.verifyRegistrationFailure(resume: Boolean) {
        val fixture = OpeningFixture(backgroundScope, resume)
        fixture.prepare()
        fixture.onBackground = { error("synthetic registration failure") }
        fixture.transport.notifyAppBackgrounded()
        runCurrent()
        try {
            val result = fixture.open()
            assertIs<NetError.TransportFailure>(assertIs<Result.Failure<NetError>>(result).error)
            runCurrent()
            fixture.assertReleased()
            val committed = fixture.committedCredential()
            advanceTimeBy(P2pKitRoomTransport.APP_RESUME_GRACE_MS + 1L)
            fixture.transport.notifyAppForegrounded()
            runCurrent()
            assertEquals(committed, fixture.committedCredential())
            assertEquals(0, fixture.fake.foregroundCalls)
            assertEquals(1, fixture.connectCalls)
        } finally {
            fixture.stopFakeIfNeeded()
        }
    }

    private suspend fun TestScope.verifyBestEffortAck(resume: Boolean) {
        val fixture = OpeningFixture(backgroundScope, resume)
        fixture.prepare()
        fixture.onPeerMessage = { message ->
            if (message.isHandoff(duringAck = true)) error("synthetic lost cleanup acknowledgement")
        }
        var room: LocalRoom? = null
        try {
            val returned = assertIs<Result.Success<LocalRoom>>(fixture.open()).data
            room = returned
            runCurrent()
            assertEquals(0, fixture.stopAttempts)
            fixture.assertSubscribed()
            assertEquals(fixture.offer.generation, fixture.committedCredential().generation)
            assertEquals(Result.Success(Unit), returned.closeForRetry())
            assertEquals(Result.Success(Unit), returned.closeForRetry())
            runCurrent()
            fixture.assertReleased()
            assertEquals(fixture.offer.generation, fixture.committedCredential().generation)
        } finally {
            room?.closeForRetry()
            fixture.stopFakeIfNeeded()
        }
    }

    private fun PeerMessage.isHandoff(duringAck: Boolean): Boolean = if (duringAck) {
        this is PeerMessage.AdmissionCommitAck || this is PeerMessage.ResumeCommitAck
    } else {
        this is PeerMessage.AdmissionReady || this is PeerMessage.ResumeReady
    }
}

/** Drives the production adapter and codec, not a replacement room/session owner. */
private class OpeningFixture(
    scope: CoroutineScope,
    private val resume: Boolean,
    missingGame: Boolean = false,
) {
    val fake = FakeP2pKit(PeerId("opening-peer"))
    private val host = Peer(
        id = PeerId("opening-host"),
        name = "${P2pKitRoomTransport.P2P_ROOM_PREFIX}Synthetic Host",
        platform = Platform.UNKNOWN,
        supportedTransports = setOf(TransportKind.LAN),
    )
    val session = FakeP2pSession(host)
    private val codec = RoomMessageCodec()
    private val storage = PlatformKeyedSecureStorage(InMemorySecureKeyValueBacking())
    val store = ResumableCredentialStore(storage)
    var onPeerMessage: suspend (PeerMessage) -> Unit = {}
    var onBackground: () -> Unit = {}
    var createCalls = 0
        private set
    var connectCalls = 0
        private set
    var stopAttempts = 0
        private set
    private val kit = object : P2pKit by fake {
        override fun notifyAppBackgrounded() {
            fake.notifyAppBackgrounded()
            onBackground()
        }

        override suspend fun stop() {
            stopAttempts++
            fake.stop()
        }
    }
    val transport = P2pKitRoomTransport(
        appId = AppId("com.parlor.test"),
        deviceName = "synthetic-peer",
        scope = scope,
        kitFactory = object : P2pKitFactory {
            override suspend fun createKit(appId: AppId, deviceName: String): P2pKit {
                createCalls++
                return kit
            }
        },
        secureStorage = storage,
    )
    private val initial = ResumableSessionCredential(
        offerId = "opening-offer-1",
        roomCode = "ABCDEF",
        displayName = "Alice",
        playerId = fake.localPeerId.value,
        hostPeerId = host.id.value,
        hostFingerprint = fake.localFingerprint.value,
        secret = "a".repeat(64),
        generation = 1L,
        issuedAtEpochMillis = kotlin.time.Clock.System.now().toEpochMilliseconds(),
        expiresAtEpochMillis = kotlin.time.Clock.System.now().toEpochMilliseconds() + 86_400_000L,
        gameId = if (missingGame) null else "fixture-game",
        gameVersion = if (missingGame) null else 1,
    )
    val offer = ResumableCredentialOffer(
        offerId = if (resume) "opening-offer-2" else initial.offerId,
        playerId = PlayerId(initial.playerId),
        hostPeerId = initial.hostPeerId,
        hostFingerprint = initial.hostFingerprint,
        secret = if (resume) "b".repeat(64) else initial.secret,
        generation = if (resume) 2L else 1L,
        issuedAtEpochMillis = initial.issuedAtEpochMillis,
        expiresAtEpochMillis = initial.expiresAtEpochMillis,
        gameId = initial.gameId,
        gameVersion = initial.gameVersion,
    )

    suspend fun prepare() {
        if (resume) {
            assertEquals(Result.Success(Unit), store.stage(initial))
            assertEquals(Result.Success(Unit), store.commit(initial.offerId, initial.generation))
        }
        fake.peersFlow.value = listOf(host)
        fake.connectHandler = {
            connectCalls++
            // A leaked unreturned room must not silently auto-rejoin. Keep an
            // unexpected retry suspended so the test can detect it deterministically.
            if (connectCalls > 1) awaitCancellation()
            session
        }
        session.sendHandler = { frame ->
            val message = codec.decode((frame as P2pMessage.Binary).bytes) as PeerMessage
            when (message) {
                is PeerMessage.AdmissionRequest -> emit(HostMessage.AdmissionOffered(offer, "Host Alice"))
                is PeerMessage.AdmissionConfirmed -> emit(
                    HostMessage.AdmissionCommitted(offer.playerId, offer.offerId, offer.generation),
                )
                is PeerMessage.ResumeRequested -> emit(HostMessage.ResumeOffered(offer, "Host Alice"))
                is PeerMessage.ResumeConfirmed -> emit(
                    HostMessage.ResumeCommitted(offer.playerId, offer.offerId, offer.generation),
                )
                else -> onPeerMessage(message)
            }
        }
    }

    suspend fun open(): Result<LocalRoom, NetError> =
        if (resume) transport.resumeLastSession(HostedGameProtocol(GameId("fixture-game"), 1))
        else transport.join("ABCDEF", "Alice")

    suspend fun committedCredential(): ResumableSessionCredential = assertNotNull(
        assertIs<Result.Success<ResumableSessionCredential?>>(store.loadResumeCandidate()).data,
    )

    fun assertSubscribed() {
        assertEquals(1, session.incomingFlow.subscriptionCount.value)
        assertEquals(1, session.stateFlow.subscriptionCount.value)
    }

    fun assertReleased() {
        assertEquals(1, stopAttempts)
        assertEquals(1, fake.stopCalls)
        assertEquals(0, session.incomingFlow.subscriptionCount.value)
        assertEquals(0, session.stateFlow.subscriptionCount.value)
        assertEquals(ConnectionState.Closed, session.state.value)
        assertTrue(session.closeCalls >= 1)
        assertFalse(session.sent.filterIsInstance<P2pMessage.Binary>().any {
            codec.decode(it.bytes) == PeerMessage.LeaveNotice
        })
    }

    suspend fun stopFakeIfNeeded() {
        // Fakes have no native resources. Release red-run kit ownership too;
        // runTest cancels its backgroundScope after the leak assertions ran.
        if (fake.stopCalls == 0) fake.stop()
    }

    private suspend fun emit(message: HostMessage) {
        session.incomingFlow.emit(P2pMessage.Binary(codec.encode(message)))
    }
}
