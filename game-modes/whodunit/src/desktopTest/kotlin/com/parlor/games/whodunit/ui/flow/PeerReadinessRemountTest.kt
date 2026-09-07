package com.parlor.games.whodunit.ui.flow

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import com.parlor.core.ids.CaseId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.core.random.RandomSource
import com.parlor.core.result.Result
import com.parlor.core.time.FakeClock
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.localization.ProvideAppLanguage
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.engine.session.SessionConfig
import com.parlor.engine.state.Player
import com.parlor.games.whodunit.WhodunitDefinition
import com.parlor.games.whodunit.WhodunitIds
import com.parlor.games.whodunit.domain.action.WhodunitAction
import com.parlor.games.whodunit.domain.event.WhodunitEvent
import com.parlor.games.whodunit.domain.party.WhodunitReadinessGate
import com.parlor.games.whodunit.domain.phase.WhodunitPhase
import com.parlor.games.whodunit.domain.projection.WhodunitProjectionPolicy
import com.parlor.games.whodunit.domain.reducer.WhodunitReducer
import com.parlor.games.whodunit.domain.reducer.WhodunitReducerContext
import com.parlor.games.whodunit.domain.state.WhodunitState
import com.parlor.games.whodunit.domain.state.WhodunitStateValidator
import com.parlor.games.whodunit.testing.whodunitPeerCaseForTest
import com.parlor.session.PlayMode
import com.parlor.session.SessionController
import com.parlor.session.SubmissionReceipt
import com.parlor.session.multidevice.ShadowSessionController
import com.parlor.session.party.PartyAwareSession
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Real peer router and passive controller, with explicit host-only reductions.
 * The cover deliberately removes the router for a frame before delivering an
 * authoritative snapshot. This tests Compose ownership, not P2pKit transport.
 */
@OptIn(ExperimentalTestApi::class)
class PeerReadinessRemountTest {
    @Test
    fun introAcknowledgementDoesNotRepeatAfterCommandCoverRemount() =
        verifyCommandCoverRemount(WhodunitPhase.PublicIntro)

    @Test
    fun briefingAcknowledgementDoesNotRepeatAfterCommandCoverRemount() =
        verifyCommandCoverRemount(WhodunitPhase.RulesBriefing)

    @Test
    fun acknowledgedIntroSurvivesUiRecreationAndDisconnectPause() =
        verifyAcknowledgedPauseReturn(WhodunitPhase.PublicIntro)

    @Test
    fun acknowledgedBriefingSurvivesUiRecreationAndDisconnectPause() =
        verifyAcknowledgedPauseReturn(WhodunitPhase.RulesBriefing)

    @Test
    fun freshIntroSnapshotWinsOverAStaleComposeProjection() =
        verifyAcknowledgedSnapshotAheadOfUi(WhodunitPhase.PublicIntro)

    @Test
    fun freshBriefingSnapshotWinsOverAStaleComposeProjection() =
        verifyAcknowledgedSnapshotAheadOfUi(WhodunitPhase.RulesBriefing)

    @Test
    fun phaseChangeBeforeEffectDoesNotSendThePreviousPhaseAcknowledgement() = runComposeUiTest {
        val fixture = Fixture(WhodunitPhase.PublicIntro)
        fixture.advanceHostToBriefing()
        fixture.publishSnapshot(updateUi = false)
        setContent { PeerContent(fixture) }

        runOnIdle { assertTrue(fixture.outgoing.isEmpty()) }
        runOnIdle { fixture.publishSnapshot() }
        runOnIdle {
            assertEquals(listOf<WhodunitAction>(WhodunitAction.AcknowledgeBriefing(fixture.selfId)), fixture.outgoing)
        }
    }

    @Test
    fun replayBeforeEffectDoesNotAcknowledgeTheStaleAssignmentGeneration() = runComposeUiTest {
        val fixture = Fixture(WhodunitPhase.PublicIntro)
        val previousGeneration = fixture.renderedProjection.state.public.roleAssignmentGeneration
        fixture.hostAction(WhodunitAction.EndGameEarly(withReveal = false))
        fixture.hostAction(WhodunitAction.BeginReplay)
        fixture.publishSnapshot(updateUi = false)
        assertEquals(previousGeneration + 1, fixture.hostState.public.roleAssignmentGeneration)
        setContent { PeerContent(fixture) }

        runOnIdle { assertTrue(fixture.outgoing.isEmpty()) }
        runOnIdle { fixture.publishSnapshot() }
        runOnIdle { assertEquals(listOf(fixture.ownAcknowledgement), fixture.outgoing) }
    }

    @Test
    fun replayInTheSameRenderedPhaseAcknowledgesTheNewGeneration() = runComposeUiTest {
        val fixture = Fixture(WhodunitPhase.PublicIntro)
        fixture.hostAction(fixture.ownAcknowledgement)
        fixture.publishSnapshot()
        setContent { PeerContent(fixture) }

        runOnIdle { assertTrue(fixture.outgoing.isEmpty()) }
        runOnIdle {
            fixture.hostAction(WhodunitAction.EndGameEarly(withReveal = false))
            fixture.hostAction(WhodunitAction.BeginReplay)
            fixture.publishSnapshot()
        }
        runOnIdle { assertEquals(listOf(fixture.ownAcknowledgement), fixture.outgoing) }
    }

    @Test
    fun unrelatedBriefingUpdatesDoNotResendWhileAuthorityHasNotResponded() = runComposeUiTest {
        val fixture = Fixture(WhodunitPhase.RulesBriefing)
        setContent { PeerContent(fixture) }

        runOnIdle { assertEquals(listOf(fixture.ownAcknowledgement), fixture.outgoing) }
        for (card in 1..3) {
            runOnIdle {
                fixture.hostAction(WhodunitAction.AdvanceBriefingCard(card))
                fixture.publishSnapshot()
            }
            runOnIdle { assertEquals(listOf(fixture.ownAcknowledgement), fixture.outgoing) }
        }
    }

    @Test
    fun rejectionWithoutHostAcknowledgementDoesNotCreateOptimisticReadiness() = runComposeUiTest {
        val fixture = Fixture(WhodunitPhase.PublicIntro)
        setContent { PeerContent(fixture) }

        runOnIdle { assertEquals(listOf(fixture.ownAcknowledgement), fixture.outgoing) }
        runOnIdle { fixture.routerVisible = false }
        onNodeWithTag(COVER_TAG).assertExists()
        // Model a definitive rejection: the host did not apply the first ACK.
        // Remount may submit this idempotent readiness again, not cache success.
        runOnIdle {
            fixture.publishSnapshot()
            fixture.routerVisible = true
        }
        runOnIdle { assertEquals(List(2) { fixture.ownAcknowledgement }, fixture.outgoing) }

        runOnIdle { fixture.routerVisible = false }
        onNodeWithTag(COVER_TAG).assertExists()
        runOnIdle {
            fixture.hostAction(fixture.ownAcknowledgement)
            fixture.publishSnapshot()
            fixture.routerVisible = true
        }
        runOnIdle { assertEquals(List(2) { fixture.ownAcknowledgement }, fixture.outgoing) }
    }

    @Test
    fun aReplacementControllerRechecksReadinessInTheSamePhase() = runComposeUiTest {
        val fixture = Fixture(WhodunitPhase.PublicIntro)
        setContent { PeerContent(fixture) }

        runOnIdle { assertEquals(listOf(fixture.ownAcknowledgement), fixture.outgoing) }
        runOnIdle { fixture.replaceController() }
        runOnIdle { assertEquals(List(2) { fixture.ownAcknowledgement }, fixture.outgoing) }
    }

    private fun verifyCommandCoverRemount(phase: WhodunitPhase) = runComposeUiTest {
        val fixture = Fixture(phase)
        fixture.hostAction(fixture.acknowledgementFor(fixture.otherId))
        fixture.publishSnapshot()
        setContent { PeerContent(fixture) }

        runOnIdle {
            assertEquals(listOf(fixture.ownAcknowledgement), fixture.outgoing)
            assertFalse(fixture.selfIsReady())
            assertNull(fixture.session.hostState)
            assertNull(fixture.session.canonicalState)
            val own = fixture.session.privateStateFor(fixture.selfId).value.state
            assertEquals(setOf(fixture.selfId), own.privatePerPlayer.keys)
            assertTrue(own.hostOnly.seatToCharacter.isEmpty())
            assertEquals(0L, own.hostOnly.randomSeed)
        }
        runOnIdle { fixture.routerVisible = false }
        onNodeWithTag(COVER_TAG).assertExists()
        mainClock.advanceTimeBy(160)
        runOnIdle {
            assertEquals(listOf(fixture.ownAcknowledgement), fixture.outgoing)
            fixture.hostAction(fixture.ownAcknowledgement)
            fixture.publishSnapshot()
            fixture.routerVisible = true
        }
        repeat(3) {
            runOnIdle { assertEquals(listOf(fixture.ownAcknowledgement), fixture.outgoing) }
            runOnIdle { fixture.routerVisible = false }
            onNodeWithTag(COVER_TAG).assertExists()
            runOnIdle { fixture.routerVisible = true }
        }
        runOnIdle {
            assertTrue(fixture.selfIsReady())
            assertEquals(listOf(fixture.ownAcknowledgement), fixture.outgoing)
        }
    }

    private fun verifyAcknowledgedPauseReturn(phase: WhodunitPhase) = runComposeUiTest {
        val fixture = Fixture(phase)
        fixture.hostAction(fixture.ownAcknowledgement)
        fixture.publishSnapshot()
        setContent { PeerContent(fixture) }

        runOnIdle { assertTrue(fixture.outgoing.isEmpty()) }
        runOnIdle {
            fixture.routerVisible = false
            fixture.hostAction(WhodunitAction.MarkPlayerDisconnected(fixture.otherId))
            fixture.publishSnapshot()
            assertTrue(fixture.hostState.public.paused)
        }
        onNodeWithTag(COVER_TAG).assertExists()
        runOnIdle {
            fixture.hostAction(WhodunitAction.MarkPlayerReconnected(fixture.otherId))
            fixture.hostAction(WhodunitAction.Resume)
            fixture.publishSnapshot()
            fixture.routerVisible = true
        }
        runOnIdle {
            assertFalse(fixture.hostState.public.paused)
            assertTrue(fixture.outgoing.isEmpty())
        }
    }

    private fun verifyAcknowledgedSnapshotAheadOfUi(phase: WhodunitPhase) = runComposeUiTest {
        val fixture = Fixture(phase)
        fixture.hostAction(fixture.ownAcknowledgement)
        // The retained own-player flow has committed before its Compose
        // collector. A fresh mount must not trust the stale render parameter.
        fixture.publishSnapshot(updateUi = false)
        setContent { PeerContent(fixture) }

        runOnIdle { assertTrue(fixture.outgoing.isEmpty()) }
        runOnIdle { fixture.publishSnapshot() }
        runOnIdle { assertTrue(fixture.outgoing.isEmpty()) }
    }

    @Composable
    private fun PeerContent(fixture: Fixture) {
        ProvideAppLanguage(AppLanguage.English) {
            ParlorTheme(reducedMotion = true) {
                if (fixture.routerVisible) {
                    PeerPhaseRouter(
                        playMode = fixture.playMode,
                        projection = fixture.renderedProjection,
                        payload = fixture.case.payload,
                        session = fixture.session,
                    )
                } else {
                    Text("Command or pause cover", Modifier.testTag(COVER_TAG))
                }
            }
        }
    }

    /** A validated synthetic four-seat Classic case, not bundled four-seat content. */
    private class Fixture(phase: WhodunitPhase) {
        val case = whodunitPeerCaseForTest("peer-readiness")
        val players = (1..4).map { Player(PlayerId("p$it"), "Player $it", it - 1) }
        val selfId = players[1].id
        val otherId = players[0].id
        val playMode = PlayMode.MultiDevice(selfPlayerId = selfId, isHost = false)
        private val context = WhodunitReducerContext(
            FakeClock(Instant.fromEpochMilliseconds(0)),
            RandomSource.seeded(SEED),
            case,
        )
        var hostState = initialState(phase)
            private set
        val outgoing = mutableListOf<WhodunitAction>()
        private var mirror = newMirror()
        var session by mutableStateOf<SessionController<WhodunitState, WhodunitAction, WhodunitEvent>>(
            PartyAwareSession(mirror, playMode, WhodunitReadinessGate),
        )
            private set
        var renderedProjection by mutableStateOf(mirror.privateStateFor(selfId).value)
            private set
        var routerVisible by mutableStateOf(true)
        val ownAcknowledgement: WhodunitAction get() = acknowledgementFor(selfId)

        fun acknowledgementFor(playerId: PlayerId): WhodunitAction = when (hostState.phase) {
            WhodunitPhase.PublicIntro -> WhodunitAction.AcknowledgeIntro(playerId)
            WhodunitPhase.RulesBriefing -> WhodunitAction.AcknowledgeBriefing(playerId)
            else -> error("Fixture must be in a readiness phase")
        }

        fun selfIsReady(): Boolean = when (hostState.phase) {
            WhodunitPhase.PublicIntro -> selfId in hostState.public.introAcknowledged
            WhodunitPhase.RulesBriefing -> selfId in hostState.public.briefingReady
            else -> error("Fixture must be in a readiness phase")
        }

        fun hostAction(action: WhodunitAction) {
            hostState = reduce(hostState, action)
        }

        fun advanceHostToBriefing() {
            players.forEach { hostAction(WhodunitAction.AcknowledgeIntro(it.id)) }
            hostAction(WhodunitAction.AdvanceFromIntro)
            assertEquals(WhodunitPhase.RulesBriefing, hostState.phase)
        }

        fun publishSnapshot(updateUi: Boolean = true) {
            val public = WhodunitProjectionPolicy.toPublic(hostState)
            val own = WhodunitProjectionPolicy.toPlayer(hostState, selfId)
            assertTrue(
                WhodunitStateValidator.isValidPeerProjectionForCase(
                    public.state, own.state.privatePerPlayer[selfId], selfId, case,
                ),
            )
            mirror.installPlayerSnapshot(public, own)
            if (updateUi) renderedProjection = own
        }

        fun replaceController() {
            mirror = newMirror()
            session = PartyAwareSession(mirror, playMode, WhodunitReadinessGate)
        }

        private fun newMirror() = ShadowSessionController<WhodunitState, WhodunitAction, WhodunitEvent>(
            selfPlayerId = selfId,
            sendActionToHost = { action ->
                outgoing += action
                Result.Success(SubmissionReceipt(stateChanged = false, awaitingAuthority = true))
            },
            initialPublic = WhodunitProjectionPolicy.toPublic(hostState),
            initialPrivate = WhodunitProjectionPolicy.toPlayer(hostState, selfId),
        )

        private fun initialState(phase: WhodunitPhase): WhodunitState {
            var state = WhodunitDefinition(Json { encodeDefaults = true }).createInitialState(
                SessionConfig(
                    SessionId("peer-readiness"), CaseId(case.envelope.caseId),
                    WhodunitIds.ClassicVoteModeId, players, SEED,
                ),
            )
            state = reduce(state, WhodunitAction.AssignRoles(SEED))
            if (phase == WhodunitPhase.RulesBriefing) {
                players.forEach { state = reduce(state, WhodunitAction.AcknowledgeIntro(it.id)) }
                state = reduce(state, WhodunitAction.AdvanceFromIntro)
            }
            assertEquals(phase, state.phase)
            return state
        }

        private fun reduce(state: WhodunitState, action: WhodunitAction): WhodunitState =
            WhodunitReducer.reduce(state, action, context).newState.also {
                WhodunitStateValidator.requireValidForCase(it, case)
            }
    }

    private companion object {
        const val COVER_TAG = "peer-readiness-cover"
        const val SEED = 73L
    }
}
