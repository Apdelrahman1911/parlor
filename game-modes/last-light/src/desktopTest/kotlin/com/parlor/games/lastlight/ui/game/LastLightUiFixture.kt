package com.parlor.games.lastlight.ui.game

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.localization.ProvideAppLanguage
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.lastlight.domain.model.AvailableActions
import com.parlor.games.lastlight.domain.model.Card
import com.parlor.games.lastlight.domain.model.CardId
import com.parlor.games.lastlight.domain.model.CardRank
import com.parlor.games.lastlight.domain.model.GamePhase
import com.parlor.games.lastlight.domain.model.GameView
import com.parlor.games.lastlight.domain.model.PlayerView
import com.parlor.games.lastlight.domain.model.PublicClaim
import com.parlor.games.lastlight.domain.model.RoundOutcome
import com.parlor.games.lastlight.ui.PendingAction
import com.parlor.games.lastlight.ui.LastLightProcessVisibility
import com.parlor.games.lastlight.ui.LocalLastLightProcessVisibility
import com.parlor.games.lastlight.ui.feedback.LastLightFeedbackCue
import com.parlor.games.lastlight.ui.feedback.LastLightFeedbackOutput
import com.parlor.games.lastlight.ui.feedback.LastLightFeedbackSettings
import com.parlor.games.lastlight.ui.feedback.LocalLastLightFeedbackOutput
import com.parlor.games.lastlight.ui.flow.common.LastLightSessionTable
import com.parlor.games.lastlight.ui.theme.LastLightColors
import com.parlor.games.lastlight.ui.theme.LastLightTheme

internal const val LAST_LIGHT_VIEWPORT = "last-light-viewport"

internal fun playingView(): GameView = GameView(
    viewerId = "a",
    phase = GamePhase.PLAYING,
    roundNumber = 1,
    tableRank = CardRank.CROWN,
    players = listOf(
        PlayerView("a", "Guest", 5, 0, false),
        PlayerView("b", "Guest", 5, 0, false),
        PlayerView("c", "Mina", 5, 0, false),
        PlayerView("d", "Ibrahim", 5, 0, false),
        PlayerView("e", "Alexandria Longname", 5, 0, false),
        PlayerView("f", "Noor", 5, 0, false),
    ),
    yourHand = listOf(
        Card("private-0", CardRank.MOON),
        Card("private-1", CardRank.MOON),
        Card("private-2", CardRank.STAR),
        Card("private-3", CardRank.WILD),
        Card("private-4", CardRank.CROWN),
    ),
    turnPlayerId = "a",
    latestClaim = null,
    forcedChallenge = false,
    availableActions = AvailableActions(canPlay = true, maxPlayableCards = 3),
    roundOutcome = null,
    winnerId = null,
)

internal fun forcedChallengeView(): GameView = playingView().let { view ->
    view.copy(
        players = view.players.map { if (it.id == view.viewerId) it else it.copy(handCount = 0) },
        latestClaim = PublicClaim("b", 1),
        forcedChallenge = true,
        availableActions = AvailableActions(canChallenge = true),
    )
}

internal fun roundResultView(): GameView = playingView().let { view ->
    view.copy(
        phase = GamePhase.ROUND_ENDED,
        players = view.players.map {
            if (it.id == "a") it.copy(handCount = 0, eliminated = true, penaltyAttempts = 1) else it
        },
        yourHand = emptyList(),
        turnPlayerId = null,
        latestClaim = null,
        availableActions = AvailableActions(),
        roundOutcome = RoundOutcome(
            roundNumber = 1,
            tableRank = CardRank.CROWN,
            claimantId = "a",
            challengerId = "b",
            revealedCards = listOf(Card("proof-wild", CardRank.WILD), Card("proof-star", CardRank.STAR)),
            truthful = false,
            penalizedPlayerId = "a",
            penaltyAttempt = 1,
            burnedOut = true,
        ),
    )
}

internal fun finishedView(): GameView = roundResultView().let { view ->
    view.copy(
        phase = GamePhase.FINISHED,
        players = view.players.map {
            if (it.id == "b") it else it.copy(handCount = 0, penaltyAttempts = 1, eliminated = true)
        },
        winnerId = "b",
    )
}

@Composable
internal fun LastLightTestFrame(
    width: Dp = 360.dp,
    height: Dp = 640.dp,
    fontScale: Float = 1f,
    language: AppLanguage = AppLanguage.English,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
        ProvideAppLanguage(language) {
            ParlorTheme(reducedMotion = true) {
                LastLightTheme(reduceMotion = ParlorTheme.reducedMotion) {
                    Surface(
                        Modifier.size(width, height).testTag(LAST_LIGHT_VIEWPORT),
                        color = LastLightColors.Ink,
                        content = content,
                    )
                }
            }
        }
    }
}

@Composable
internal fun TestTable(
    view: GameView = playingView(),
    isHost: Boolean = true,
    canSendAction: Boolean = true,
    pendingAction: PendingAction? = null,
    privateContentVisible: Boolean = true,
    privacyEpoch: Long = 0,
    canAdvanceRound: Boolean = true,
    canReturnToLobby: Boolean = true,
    onPlay: (List<CardId>) -> Unit = {},
    onChallenge: () -> Unit = {},
    onNextRound: () -> Unit = {},
    onReturnToLobby: () -> Unit = {},
) {
    GameplayScreen(
        view = view,
        isHost = isHost,
        canSendAction = canSendAction,
        pendingAction = pendingAction,
        privateContentVisible = privateContentVisible,
        canAdvanceRound = canAdvanceRound,
        canReturnToLobby = canReturnToLobby,
        onPlay = onPlay,
        onChallenge = onChallenge,
        onNextRound = onNextRound,
        onReturnToLobby = onReturnToLobby,
        privacyEpoch = privacyEpoch,
    )
}

/** Exercises the production table wrapper without opening an audio device in desktop tests. */
@Composable
internal fun TestSessionTable(
    view: GameView,
    sessionId: String = "layout-session",
    visibility: LastLightProcessVisibility = LastLightProcessVisibility(true, 0L),
    onPlay: (List<CardId>) -> Unit = {},
    onNextRound: () -> Unit = {},
    onReturnToLobby: () -> Unit = {},
) {
    val output = remember { SilentTestFeedbackOutput() }
    CompositionLocalProvider(
        LocalLastLightProcessVisibility provides visibility,
        LocalLastLightFeedbackOutput provides output,
    ) {
        LastLightSessionTable(
            sessionId = sessionId,
            view = view,
            isHost = true,
            canSendAction = true,
            pendingAction = null,
            privateContentVisible = true,
            canAdvanceRound = true,
            canReturnToLobby = true,
            onPlay = onPlay,
            onChallenge = {},
            onNextRound = onNextRound,
            onReturnToLobby = onReturnToLobby,
        )
    }
}

private class SilentTestFeedbackOutput : LastLightFeedbackOutput {
    override fun prepare() = Unit
    override fun play(cue: LastLightFeedbackCue, settings: LastLightFeedbackSettings) = Unit
    override fun setForeground(value: Boolean) = Unit
    override fun stopSound() = Unit
    override fun close() = Unit
}

@OptIn(ExperimentalTestApi::class)
internal fun SemanticsNodeInteraction.bringIntoView(): SemanticsNodeInteraction {
    if (!isDisplayed() || getUnclippedBoundsInRoot() != getBoundsInRoot()) performScrollTo()
    return assertIsDisplayed()
}
