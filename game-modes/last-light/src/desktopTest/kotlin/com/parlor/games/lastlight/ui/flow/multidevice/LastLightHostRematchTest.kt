package com.parlor.games.lastlight.ui.flow.multidevice

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.parlor.core.ids.PlayerId
import com.parlor.core.result.Result
import com.parlor.core.time.Clock
import com.parlor.core.time.FakeClock
import com.parlor.designsystem.components.LocalParlorToastState
import com.parlor.designsystem.components.ParlorToastState
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.localization.ProvideAppLanguage
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.lastlight.LastLightDefinition
import com.parlor.games.lastlight.LastLightIds
import com.parlor.games.lastlight.domain.action.LastLightAction
import com.parlor.games.lastlight.domain.model.GamePhase
import com.parlor.games.lastlight.protocol.LastLightActionCodec
import com.parlor.games.lastlight.ui.LastLightProcessVisibility
import com.parlor.games.lastlight.ui.LocalLastLightProcessVisibility
import com.parlor.games.lastlight.ui.LocalLastLightVisibilityReader
import com.parlor.networking.protocol.CommandStatus
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.PeerMessage
import com.parlor.networking.protocol.RoomMessage
import com.parlor.networking.protocol.SessionEndReason
import com.parlor.networking.protocol.SessionEnvelopeHeader
import com.parlor.networking.protocol.SessionProtocol
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.networking.room.PeerEvent
import com.parlor.networking.room.RoomInfo
import com.parlor.networking.room.SendTarget
import com.parlor.networking.testing.ControlledStartRoom
import com.parlor.networking.transport.HostConfig
import com.parlor.networking.transport.RoomTransport
import com.parlor.networking.transport.TransportCapability
import com.parlor.session.multidevice.HostStartGateState
import com.parlor.session.multidevice.ProcessMultiplayerSession
import com.parlor.session.multidevice.ProcessMultiplayerSessionOwner
import com.parlor.session.multidevice.ProcessMultiplayerState
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.koin.compose.KoinIsolatedContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Exercises the real lobby, reducer, terminal transaction and a close spanning UI frames. */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class LastLightHostRematchTest {
    @Test
    fun winner_returns_to_a_fresh_room_after_old_game_child_is_disposed_during_close() {
        val lane = RematchLane()
        val application = koinApplication {
            modules(module {
                single { LastLightDefinition() }
                single<Clock> { FakeClock(Instant.fromEpochSeconds(0)) }
                single { lane.owner }
            })
        }
        try {
            runComposeUiTest {
                var exits = 0
                val visibility = LastLightProcessVisibility(true, 0L)
                val toast = ParlorToastState()
                setContent {
                    KoinIsolatedContext(application) {
                        CompositionLocalProvider(
                            LocalParlorToastState provides toast,
                            LocalLastLightProcessVisibility provides visibility,
                            LocalLastLightVisibilityReader provides { visibility },
                        ) {
                            ProvideAppLanguage(AppLanguage.English) {
                                ParlorTheme(reducedMotion = true) {
                                    LastLightHostLobbyFlow(
                                        transport = lane,
                                        hostName = "Host",
                                        onBackToHome = { exits++ },
                                        modifier = Modifier.size(480.dp, 820.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                waitUntil(timeoutMillis = 5_000) {
                    lane.pump()
                    onAllNodesWithText("OLD1").fetchSemanticsNodes().isNotEmpty()
                }
                onNodeWithContentDescription("Start the Last Light game").assertIsDisplayed().performClick()
                waitUntil(timeoutMillis = 5_000) {
                    lane.pump()
                    lane.active?.runtime?.value is LastLightHostRuntime && lane.rooms.first().controlled.offeredPeerCount == 1
                }
                val oldSession = checkNotNull(lane.active)
                val runtime = oldSession.runtime.value as LastLightHostRuntime
                lane.runQueued {
                    lane.rooms.first().controlled.releaseReady(runtime.bridge.protocol) {
                        it.caseId == LastLightIds.CaseId.raw && it.modeId == LastLightIds.StandardModeId.raw
                    }
                }
                waitUntil(timeoutMillis = 5_000) { lane.pump(); runtime.startGate.value == HostStartGateState.Started }
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Table options").fetchSemanticsNodes().isNotEmpty() }
                onNodeWithText("Table options").performClick()
                onNodeWithText("Sound").assertIsOn().performClick().assertIsOff()
                onNodeWithText("Haptics").assertIsOn().performClick().assertIsOff()
                onNodeWithText("Done").performClick()
                onNodeWithContentDescription("Open options to leave this game.").performClick()
                onNodeWithText("Stay in game").performClick()
                onNodeWithText("Table options").performClick()
                onNodeWithText("Sound").assertIsOff()
                onNodeWithText("Haptics").assertIsOff()
                onNodeWithText("Done").performClick()
                onNodeWithText("Show hand").assertIsDisplayed().performClick()
                val beforeRecovery = runtime.bridge.recoveryEpoch.value
                runOnIdle {
                    // Both topology transitions finish while the UI thread is
                    // occupied, so it observes only the recovered connection.
                    lane.rooms.first().disconnectAndReconnectBeforeRuntimeRuns()
                    assertEquals(beforeRecovery, runtime.bridge.recoveryEpoch.value)
                    lane.pump()
                    assertTrue(runtime.bridge.recoveryEpoch.value > beforeRecovery)
                    assertTrue(checkNotNull(runtime.session.canonicalState).value.public.disconnectedPlayers.isEmpty())
                }
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Show hand").fetchSemanticsNodes().isNotEmpty() }
                onNodeWithText("Hide hand").assertDoesNotExist()
                lane.playToWinner(runtime)
                waitUntil(timeoutMillis = 5_000) {
                    lane.pump()
                    onAllNodesWithText("Back to room").fetchSemanticsNodes().isNotEmpty()
                }
                val oldIdentity = runtime.bridge.protocol.sessionId
                val oldSeed = oldSession.hostSeed
                val gate = CompletableDeferred<Unit>()
                lane.rooms.first().leaveGate = gate
                onNodeWithText("Back to room").performScrollTo().performClick()
                waitUntil(timeoutMillis = 5_000) {
                    lane.pump()
                    lane.owner.state.value is ProcessMultiplayerState.Closing && lane.rooms.first().leaveStarted
                }
                // Owner Closing removes the game child. Multiple rendered frames
                // must not cancel the lobby-owned completion that starts the rematch.
                mainClock.advanceTimeByFrame()
                mainClock.advanceTimeByFrame()
                waitForIdle()
                onNodeWithContentDescription("Cancel hosting and return home").assertIsNotEnabled()
                assertEquals(1, lane.rooms.size)
                assertEquals(listOf(SessionEndReason.Completed), lane.rooms.first().terminalReasons)
                assertEquals(0, exits)

                gate.complete(Unit)
                waitUntil(timeoutMillis = 5_000) {
                    lane.pump()
                    lane.rooms.size == 2 && onAllNodesWithText("NEW2").fetchSemanticsNodes().isNotEmpty()
                }
                val fresh = checkNotNull(lane.active)
                assertTrue(fresh !== oldSession)
                assertNotEquals(oldSeed, fresh.hostSeed)
                assertNull(oldSession.runtime.value)
                assertTrue(oldSession.scope.coroutineContext[Job]?.isCompleted == true)
                assertNull(fresh.runtime.value)
                assertEquals(1, lane.rooms.first().controlled.leaves)
                assertEquals(0, exits)
                assertEquals(oldIdentity, runtime.bridge.protocol.sessionId)
                assertFalse(lane.rooms.first().controlled.workerCount > 0)
            }
        } finally {
            lane.close()
            application.close()
        }
    }

    private class RematchLane : RoomTransport {
        private val scheduler = TestCoroutineScheduler()
        private val processJob = SupervisorJob()
        private val failure = AtomicReference<Throwable?>()
        private val scope = CoroutineScope(
            processJob + StandardTestDispatcher(scheduler) + CoroutineExceptionHandler { _, error -> failure.set(error) },
        )
        val owner = ProcessMultiplayerSessionOwner(scope)
        val rooms = mutableListOf<HeldCloseRoom>()
        val active: ProcessMultiplayerSession?
            get() = (owner.state.value as? ProcessMultiplayerState.Active)?.session
        override val capability = TransportCapability(false, 0, 272 * 1024)

        override suspend fun host(config: HostConfig): Result<LocalRoom, NetError> {
            assertEquals(LastLightIds.GameId, config.gameProtocol?.gameId)
            assertEquals(1, config.gameProtocol?.gameVersion)
            assertEquals(5, config.maxRemotePlayers)
            val controlled = ControlledStartRoom(lastLightTestPlayers.take(2), scope)
            val room = HeldCloseRoom(controlled, if (rooms.isEmpty()) "OLD1" else "NEW2")
            rooms += room
            return Result.Success(room)
        }

        override suspend fun join(code: String, displayName: String): Result<LocalRoom, NetError> =
            Result.Failure(NetError.Unauthorized)

        fun playToWinner(runtime: LastLightHostRuntime) {
            val state = checkNotNull(runtime.session.canonicalState)
            repeat(20) {
                if (state.value.phase == GamePhase.FINISHED) return
                if (state.value.phase == GamePhase.ROUND_ENDED) perform(runtime, LastLightAction.NextRound)
                val actor = PlayerId(checkNotNull(state.value.public.turnPlayerId))
                val card = state.value.privatePerPlayer.getValue(actor).hand.first()
                perform(runtime, LastLightAction.PlayCards(actor, listOf(card.id)))
                perform(runtime, LastLightAction.Challenge(PlayerId(checkNotNull(state.value.public.turnPlayerId))))
            }
            error("Two-player game did not reach its bounded fuse outcome")
        }

        private fun perform(runtime: LastLightHostRuntime, action: LastLightAction) {
            val actor = when (action) {
                is LastLightAction.PlayCards -> action.by
                is LastLightAction.Challenge -> action.by
                else -> testHostId
            }
            if (actor == testHostId) {
                val result = runQueued { runtime.session.submit(action) }
                assertTrue(assertIs<Result.Success<com.parlor.session.SubmissionReceipt>>(result).data.stateChanged)
            } else {
                runQueued { rooms.first().submitRemote(action, runtime.bridge.protocol) }
                assertEquals(CommandStatus.Applied, rooms.first().lastCommandResult?.status)
            }
        }

        fun pump() {
            scheduler.runCurrent()
            failure.get()?.let { throw AssertionError("Retained Last Light worker failed", it) }
            rooms.forEach { it.controlled.requireNoDrops() }
        }

        fun <T> runQueued(block: suspend () -> T): T {
            val operation = scope.async { block() }
            pump()
            check(operation.isCompleted) { "Synthetic operation did not settle" }
            return operation.getCompleted()
        }

        fun close() {
            rooms.forEach { it.leaveGate?.complete(Unit) }
            pump()
            active?.let { current ->
                assertIs<Result.Success<Unit>>(runQueued { owner.finalLeave(current, SessionEndReason.Cancelled) })
            }
            processJob.cancel()
            scheduler.runCurrent()
        }
    }

    private class HeldCloseRoom(val controlled: ControlledStartRoom, code: String) : LocalRoom by controlled {
        override val info = MutableStateFlow(RoomInfo(code, "Host", testHostId, RoomInfo.Status.Hosting)).asStateFlow()
        val memberState = MutableStateFlow(controlled.members.value)
        override val members = memberState.asStateFlow()
        private val rawPeerEvents = MutableSharedFlow<PeerEvent>(extraBufferCapacity = 8)
        override val peerEvents = rawPeerEvents.asSharedFlow()
        private val remoteCommands = Channel<PeerMessage>(8)
        override val incoming: Flow<RoomMessage> = merge(controlled.incoming, remoteCommands.receiveAsFlow())
        private var latestSnapshot: HostMessage.PlayerSnapshot? = null
        var lastCommandResult: HostMessage.CommandResult? = null
            private set
        private var nextId = 0
        var leaveGate: CompletableDeferred<Unit>? = null
        var leaveStarted = false
            private set
        val terminalReasons = mutableListOf<SessionEndReason>()

        fun disconnectAndReconnectBeforeRuntimeRuns() {
            check(rawPeerEvents.subscriptionCount.value > 0)
            memberState.value = memberState.value.map { it.copy(connected = false) }
            check(rawPeerEvents.tryEmit(PeerEvent.PeerLeft(testAliceId, "Alice")))
            memberState.value = memberState.value.map { it.copy(connected = true) }
            check(rawPeerEvents.tryEmit(PeerEvent.PeerReconnected(testAliceId, "Alice")))
        }

        override suspend fun send(target: SendTarget, message: HostMessage): Result<Unit, NetError> {
            when (message) {
                is HostMessage.PlayerSnapshot -> latestSnapshot = message
                is HostMessage.CommandResult -> lastCommandResult = message
                is HostMessage.SessionEnded -> terminalReasons += message.reason
                else -> Unit
            }
            return controlled.send(target, message)
        }

        /** Synthetic admitted Alice endpoint stamps identity independently of the encoded action. */
        fun submitRemote(action: LastLightAction, protocol: SessionProtocol) {
            val snapshot = checkNotNull(latestSnapshot)
            val id = "rematch-command-${++nextId}".padEnd(32, 'x')
            val header = SessionEnvelopeHeader(protocol.protocol, protocol.sessionId, protocol.gameId, protocol.gameVersion, id, 0L)
            check(remoteCommands.trySend(PeerMessage.ClientCommand(
                header, testAliceId, id, snapshot.nextExpectedClientSequence, snapshot.revision, LastLightActionCodec.encode(action),
            )).isSuccess) { "Synthetic command inbox overflowed" }
        }

        override suspend fun leave() {
            leaveStarted = true
            leaveGate?.await()
            controlled.leave()
            remoteCommands.close()
        }
    }
}
