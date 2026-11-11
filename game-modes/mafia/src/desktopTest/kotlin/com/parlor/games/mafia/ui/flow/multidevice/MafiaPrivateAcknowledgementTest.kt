package com.parlor.games.mafia.ui.flow.multidevice

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.parlor.core.ids.CaseId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.core.random.RandomSource
import com.parlor.core.result.Result
import com.parlor.core.time.FakeClock
import com.parlor.designsystem.components.InPlaceBackOwner
import com.parlor.designsystem.components.ProvideInPlaceBackOwner
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.localization.ProvideAppLanguage
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.engine.reducer.DefaultReducerContext
import com.parlor.engine.session.SessionConfig
import com.parlor.engine.session.SubmitError
import com.parlor.engine.state.Player
import com.parlor.games.mafia.MafiaDefinition
import com.parlor.games.mafia.MafiaIds
import com.parlor.games.mafia.domain.action.MafiaAction
import com.parlor.games.mafia.domain.event.MafiaEvent
import com.parlor.games.mafia.domain.projection.MafiaProjectionPolicy
import com.parlor.games.mafia.domain.reducer.MafiaReducer
import com.parlor.games.mafia.domain.state.MafiaState
import com.parlor.games.mafia.domain.state.Role
import com.parlor.session.SessionController
import com.parlor.session.SubmissionReceipt
import com.parlor.session.ViewerContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json

@OptIn(ExperimentalTestApi::class)
class MafiaPrivateAcknowledgementTest {
    @Test
    fun english_role_hides_before_acknowledgement_and_stays_hidden_after_rejection() =
        verifyAcknowledgement(AppLanguage.English, detective = false)

    @Test
    fun arabic_role_hides_before_acknowledgement_and_stays_hidden_after_rejection() =
        verifyAcknowledgement(AppLanguage.Arabic, detective = false)

    @Test
    fun english_detective_result_hides_before_acknowledgement_and_stays_hidden_after_rejection() =
        verifyAcknowledgement(AppLanguage.English, detective = true)

    @Test
    fun arabic_detective_result_hides_before_acknowledgement_and_stays_hidden_after_rejection() =
        verifyAcknowledgement(AppLanguage.Arabic, detective = true)

    private fun verifyAcknowledgement(language: AppLanguage, detective: Boolean) = runComposeUiTest {
        val canonical = privateScreenState(detective)
        val role = if (detective) Role.Detective else Role.Mafia
        val self = canonical.privatePerPlayer.entries.first { it.value.role == role }.key
        val state = MafiaProjectionPolicy.toPlayer(canonical, self).state
        val session = PendingSession(state, self)
        val back = InPlaceBackOwner()
        val arabic = language == AppLanguage.Arabic
        val reveal = if (arabic) "اكشف" else "Reveal"
        val hide = if (arabic) "فهمت — اخفِ الشاشة" else "Got it — hide screen"
        val privateText = if (detective) {
            val target = checkNotNull(state.privatePerPlayer.getValue(self).pendingDetectiveResult).target
            val targetName = state.players.first { it.id == target }.displayName
            if (arabic) "$targetName من الحرامية." else "$targetName is Mafia."
        } else if (arabic) "حرامي" else "Mafia"
        val privateNodeCount = if (!detective && !arabic) 2 else 1
        val expected = if (detective) MafiaAction.AcknowledgeDetectiveResult(self) else MafiaAction.AcknowledgeRoleViewed(self)
        var mounted by mutableStateOf(true)
        setContent {
            val scope = rememberCoroutineScope()
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                ProvideAppLanguage(language) {
                    ParlorTheme(reducedMotion = true) {
                        ProvideInPlaceBackOwner(back) {
                            if (mounted) {
                                MafiaMultiDevicePhaseRouter(
                                    state, self, false, session, scope, {}, Modifier.size(320.dp, 740.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        if (onAllNodesWithText(reveal).fetchSemanticsNodes().isNotEmpty()) {
            onNodeWithText(reveal).performScrollTo().performClick()
        }
        onAllNodesWithText(privateText, useUnmergedTree = true).assertCountEquals(privateNodeCount)
        onNodeWithText(hide).performScrollTo().performClick()
        onAllNodesWithText(privateText, useUnmergedTree = true).assertCountEquals(0)
        runOnIdle {
            assertEquals(listOf(expected), session.submitted)
            assertFalse(session.outcome.isCompleted)
            assertEquals(state, session.privateStateFor(self).value.state)
            mounted = false
            session.outcome.complete(Result.Failure(SubmitError.SessionSuspended))
        }
        runOnIdle { mounted = true }
        onAllNodesWithText(privateText, useUnmergedTree = true).assertCountEquals(0)
        onNodeWithText(reveal).performScrollTo().performClick()
        onAllNodesWithText(privateText, useUnmergedTree = true).assertCountEquals(privateNodeCount)
        runOnIdle {
            assertEquals(listOf(expected), session.submitted)
            assertTrue(back.handleBack())
        }
        onAllNodesWithText(privateText, useUnmergedTree = true).assertCountEquals(0)
        runOnIdle { assertEquals(listOf(expected), session.submitted) }
    }

    private fun privateScreenState(detective: Boolean): MafiaState {
        val seed = 1357L
        val context = DefaultReducerContext(
            FakeClock(Instant.fromEpochSeconds(1_700_000_000)), RandomSource.seeded(seed),
        )
        var state = MafiaDefinition(Json).createInitialState(
            SessionConfig(
                sessionId = SessionId("private-ack-test"), caseId = CaseId("default"),
                modeId = MafiaIds.ClassicModeId, randomSeed = seed,
                players = List(6) { Player(PlayerId("player-$it"), "Player $it", it) },
            ),
        )
        state = MafiaReducer.reduce(state, MafiaAction.StartGame, context).newState
        if (detective) {
            state.players.forEach { player ->
                state = MafiaReducer.reduce(state, MafiaAction.AcknowledgeRoleViewed(player.id), context).newState
            }
            state = MafiaReducer.reduce(state, MafiaAction.AdvanceFromRoleAssignment, context).newState
            val investigator = state.privatePerPlayer.entries.first { it.value.role == Role.Detective }.key
            val target = state.privatePerPlayer.entries.first { it.value.role == Role.Mafia }.key
            state = MafiaReducer.reduce(state, MafiaAction.SubmitDetectiveInspect(investigator, target), context).newState
        }
        return state
    }
}

private class PendingSession(state: MafiaState, self: PlayerId) : SessionController<MafiaState, MafiaAction, MafiaEvent> {
    val submitted = mutableListOf<MafiaAction>()
    val outcome = CompletableDeferred<Result<SubmissionReceipt, SubmitError>>()
    override val publicState = MutableStateFlow(MafiaProjectionPolicy.toPublic(state))
    override val hostState = null
    override val canonicalState = null
    override val events = MutableSharedFlow<MafiaEvent>()
    override val activeViewer = MutableStateFlow<ViewerContext>(ViewerContext.Player(self))
    private val own = MutableStateFlow(MafiaProjectionPolicy.toPlayer(state, self))

    override fun privateStateFor(playerId: PlayerId) = own

    override suspend fun submit(action: MafiaAction): Result<SubmissionReceipt, SubmitError> {
        submitted += action
        return outcome.await()
    }

    override suspend fun setActiveViewer(viewer: ViewerContext) {
        activeViewer.value = viewer
    }

    override suspend fun close() {
        outcome.cancel()
    }
}
