package com.parlor.games.lastlight.ui.feedback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class LastLightFeedbackControllerTest {
    @Test
    fun initialAndRepeatedSnapshotsAreSilentAndNativeForegroundIsSynchronized() = runTest {
        val fixture = Fixture(this)
        val restored = frame(plays = 9, outcomes = 3, result = LastLightFeedbackCue.LIGHT_OUT)
        fixture.accept(restored)
        repeat(4) { fixture.accept(restored) }
        advanceTimeBy(1_000)
        runCurrent()

        assertTrue(fixture.output.cues.isEmpty())
        assertEquals(listOf(false, true), fixture.output.foregroundChanges)
    }

    @Test
    fun firstAvailableGameAfterNullIsABaseline() = runTest {
        val fixture = Fixture(this)
        fixture.accept(null)
        fixture.accept(frame(plays = 4))
        fixture.accept(frame(plays = 5))

        assertEquals(listOf(LastLightFeedbackCue.CARD_PLAY), fixture.output.cues)
        assertEquals(listOf(false, false, false, true), fixture.output.foregroundChanges)
    }

    @Test
    fun distinctAcceptedPlayIdsEmitEvenWhenClaimsAreIndistinguishable() = runTest {
        val fixture = Fixture(this)
        fixture.accept(frame())
        fixture.accept(frame(plays = 1))
        repeat(3) { fixture.accept(frame(plays = 1)) }
        fixture.accept(frame(plays = 2))
        fixture.accept(frame(plays = 2))

        assertEquals(List(2) { LastLightFeedbackCue.CARD_PLAY }, fixture.output.cues)
    }

    @Test
    fun staleFramesCannotLowerTheAcceptedEventWatermark() = runTest {
        val fixture = Fixture(this)
        fixture.accept(frame())
        fixture.accept(frame(plays = 3))
        fixture.accept(frame(plays = 1))
        fixture.accept(frame(plays = 3))
        fixture.accept(frame(plays = 4))

        assertEquals(List(2) { LastLightFeedbackCue.CARD_PLAY }, fixture.output.cues)
    }

    @Test
    fun mergedAcceptedEventsProduceOnlyTheLatestChallengeAndItsDelayedVerdict() = runTest {
        val fixture = Fixture(this)
        fixture.accept(frame())
        val outcome = frame(plays = 8, outcomes = 2, result = LastLightFeedbackCue.LIGHT_OUT)
        fixture.accept(outcome)
        fixture.accept(outcome)
        runCurrent()
        advanceTimeBy(259)
        runCurrent()
        assertEquals(listOf(LastLightFeedbackCue.CHALLENGE), fixture.output.cues)

        advanceTimeBy(1)
        runCurrent()
        fixture.accept(outcome)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(listOf(LastLightFeedbackCue.CHALLENGE, LastLightFeedbackCue.LIGHT_OUT), fixture.output.cues)
    }

    @Test
    fun aNewOutcomeCancelsThePreviousPendingResult() = runTest {
        val fixture = Fixture(this)
        fixture.accept(frame())
        fixture.accept(frame(outcomes = 1, result = LastLightFeedbackCue.ROUND_END))
        runCurrent()
        advanceTimeBy(100)
        fixture.accept(frame(outcomes = 2, result = LastLightFeedbackCue.LIGHT_OUT))
        runCurrent()
        advanceTimeBy(160)
        runCurrent()
        assertEquals(List(2) { LastLightFeedbackCue.CHALLENGE }, fixture.output.cues)

        advanceTimeBy(100)
        runCurrent()
        assertEquals(
            listOf(LastLightFeedbackCue.CHALLENGE, LastLightFeedbackCue.CHALLENGE, LastLightFeedbackCue.LIGHT_OUT),
            fixture.output.cues,
        )
    }

    @Test
    fun backgroundCancelsResultAndForegroundDoesNotReplayIt() = runTest {
        val fixture = Fixture(this)
        fixture.accept(frame())
        val outcome = frame(outcomes = 1, result = LastLightFeedbackCue.WIN)
        fixture.accept(outcome)
        runCurrent()
        fixture.accept(outcome, foreground = false)
        fixture.accept(outcome, foreground = true)
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(listOf(LastLightFeedbackCue.CHALLENGE), fixture.output.cues)
        assertEquals(listOf(false, true, false, false, false, true), fixture.output.foregroundChanges)
    }

    @Test
    fun disconnectedAndReconnectedSnapshotsAreSilentUntilANewAcceptedEvent() = runTest {
        val fixture = Fixture(this)
        fixture.accept(frame())
        fixture.accept(frame(plays = 1), connected = false)
        val recovered = frame(plays = 12, outcomes = 4, result = LastLightFeedbackCue.ROUND_END)
        fixture.accept(recovered, connected = false)
        fixture.accept(recovered, connected = true)
        fixture.accept(recovered)
        advanceTimeBy(1_000)
        runCurrent()
        assertTrue(fixture.output.cues.isEmpty())

        fixture.accept(frame(plays = 13, outcomes = 4))
        assertEquals(listOf(LastLightFeedbackCue.CARD_PLAY), fixture.output.cues)
    }

    @Test
    fun interruptionEpochCancelsFeedbackEvenWhenForegroundRemainsTrue() = runTest {
        val fixture = Fixture(this)
        fixture.accept(frame())
        val outcome = frame(outcomes = 1, result = LastLightFeedbackCue.ROUND_END)
        fixture.accept(outcome)
        runCurrent()
        fixture.accept(outcome, epoch = 1)
        advanceTimeBy(1_000)
        runCurrent()
        fixture.accept(outcome, epoch = 1)

        assertEquals(listOf(LastLightFeedbackCue.CHALLENGE), fixture.output.cues)
        fixture.accept(frame(plays = 1, outcomes = 1), epoch = 1)
        assertEquals(listOf(LastLightFeedbackCue.CHALLENGE, LastLightFeedbackCue.CARD_PLAY), fixture.output.cues)
    }

    @Test
    fun changingSessionCancelsOldResultAndResetsTheBaseline() = runTest {
        val fixture = Fixture(this)
        fixture.accept(frame())
        fixture.accept(frame(outcomes = 1, result = LastLightFeedbackCue.WIN))
        runCurrent()
        fixture.accept(frame(), sessionKey = "replacement")
        advanceTimeBy(1_000)
        runCurrent()
        fixture.accept(frame(plays = 1), sessionKey = "replacement")

        assertEquals(listOf(LastLightFeedbackCue.CHALLENGE, LastLightFeedbackCue.CARD_PLAY), fixture.output.cues)
    }

    @Test
    fun roundContinuationCancelsAResultThatHasBecomeHistory() = runTest {
        val fixture = Fixture(this)
        fixture.accept(frame())
        fixture.accept(frame(outcomes = 1, result = LastLightFeedbackCue.ROUND_END))
        runCurrent()
        fixture.accept(frame(outcomes = 1, claim = false))
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(listOf(LastLightFeedbackCue.CHALLENGE), fixture.output.cues)
    }

    @Test
    fun closeCancelsPendingResultAndRejectsAllLaterFrames() = runTest {
        val fixture = Fixture(this)
        fixture.accept(frame())
        fixture.accept(frame(outcomes = 1, result = LastLightFeedbackCue.WIN))
        runCurrent()
        fixture.controller.close()
        fixture.controller.close()
        fixture.accept(frame(plays = 1, outcomes = 1))
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(listOf(LastLightFeedbackCue.CHALLENGE), fixture.output.cues)
        assertEquals(1, fixture.output.closeCount)
    }

    @Test
    fun soundAndHapticsAreIndependentAndEnablingDoesNotReplayMutedEvents() = runTest {
        val fixture = Fixture(this)
        val muted = LastLightFeedbackSettings(soundEnabled = false, hapticsEnabled = false)
        val hapticsOnly = LastLightFeedbackSettings(soundEnabled = false, hapticsEnabled = true)
        val soundOnly = LastLightFeedbackSettings(soundEnabled = true, hapticsEnabled = false)
        fixture.accept(frame(), settings = muted)
        fixture.accept(frame(plays = 1), settings = muted)
        fixture.accept(frame(plays = 1), settings = hapticsOnly)
        assertTrue(fixture.output.cues.isEmpty())

        fixture.accept(frame(plays = 2), settings = hapticsOnly)
        fixture.accept(frame(plays = 3), settings = soundOnly)
        assertEquals(List(2) { LastLightFeedbackCue.CARD_PLAY }, fixture.output.cues)
        assertEquals(listOf(hapticsOnly, soundOnly), fixture.output.settings)
        assertEquals(1, fixture.output.stopCount)
    }

    @Test
    fun pendingVerdictUsesCurrentIndependentPreferences() = runTest {
        val fixture = Fixture(this)
        val hapticsOnly = LastLightFeedbackSettings(soundEnabled = false, hapticsEnabled = true)
        fixture.accept(frame())
        val outcome = frame(outcomes = 1, result = LastLightFeedbackCue.ROUND_END)
        fixture.accept(outcome)
        runCurrent()
        fixture.accept(outcome, settings = hapticsOnly)
        advanceTimeBy(260)
        runCurrent()

        assertEquals(listOf(LastLightFeedbackCue.CHALLENGE, LastLightFeedbackCue.ROUND_END), fixture.output.cues)
        assertEquals(hapticsOnly, fixture.output.settings.last())
        assertEquals(1, fixture.output.stopCount)
    }

    @Test
    fun mutingBothPreferencesCancelsRatherThanDefersThePendingVerdict() = runTest {
        val fixture = Fixture(this)
        fixture.accept(frame())
        val outcome = frame(outcomes = 1, result = LastLightFeedbackCue.ROUND_END)
        fixture.accept(outcome)
        runCurrent()
        fixture.accept(outcome, settings = LastLightFeedbackSettings(false, false))
        fixture.accept(outcome)
        advanceTimeBy(260)
        runCurrent()

        assertEquals(listOf(LastLightFeedbackCue.CHALLENGE), fixture.output.cues)
    }

    private class Fixture(scope: TestScope) {
        val output = RecordingLastLightFeedback()
        val controller = LastLightFeedbackController(output, scope.backgroundScope)

        fun accept(
            frame: LastLightFeedbackFrame?,
            connected: Boolean = true,
            foreground: Boolean = true,
            epoch: Long = 0L,
            sessionKey: String = "session",
            settings: LastLightFeedbackSettings = LastLightFeedbackSettings(),
        ) {
            controller.accept(sessionKey, frame, connected, foreground, settings, epoch)
        }
    }

    private fun frame(
        plays: Long = 0,
        outcomes: Long = 0,
        claim: Boolean = true,
        result: LastLightFeedbackCue? = null,
    ) = LastLightFeedbackFrame(plays, outcomes, claim, result)
}

internal class RecordingLastLightFeedback : LastLightFeedbackOutput {
    val cues = mutableListOf<LastLightFeedbackCue>()
    val settings = mutableListOf<LastLightFeedbackSettings>()
    val foregroundChanges = mutableListOf<Boolean>()
    var closeCount = 0
    var stopCount = 0
    var prepareCount = 0

    override fun prepare() {
        prepareCount++
    }

    override fun play(cue: LastLightFeedbackCue, settings: LastLightFeedbackSettings) {
        cues += cue
        this.settings += settings
    }

    override fun setForeground(value: Boolean) {
        foregroundChanges += value
    }

    override fun stopSound() {
        stopCount++
    }

    override fun close() {
        closeCount++
    }
}
