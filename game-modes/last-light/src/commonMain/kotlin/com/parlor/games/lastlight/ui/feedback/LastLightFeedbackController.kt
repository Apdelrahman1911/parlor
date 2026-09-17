package com.parlor.games.lastlight.ui.feedback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Serial accepted-view consumer shared by local and LAN tables. The first active frame is a baseline;
 * missed views never become a queue of sounds. All calls run on the presentation dispatcher.
 */
internal class LastLightFeedbackController(
    private val output: LastLightFeedbackOutput,
    private val scope: CoroutineScope,
) {
    private var continuity: Continuity? = null
    private var currentFrame: LastLightFeedbackFrame? = null
    private var settings = LastLightFeedbackSettings()
    private var active = false
    private var closed = false
    private var resultJob: Job? = null

    fun accept(
        sessionKey: Any,
        frame: LastLightFeedbackFrame?,
        connected: Boolean,
        foreground: Boolean,
        settings: LastLightFeedbackSettings = LastLightFeedbackSettings(),
        interruptionEpoch: Long = 0L,
    ) {
        if (closed) return
        if (this.settings.soundEnabled && !settings.soundEnabled) output.stopSound()
        this.settings = settings
        if (!settings.enabled) cancelResult()

        val nextContinuity = Continuity(sessionKey, interruptionEpoch)
        val nextActive = connected && foreground && frame != null
        if (continuity != nextContinuity || !active || !nextActive) {
            cancelResult()
            // Synchronize the native inactive default explicitly, including first composition.
            output.setForeground(false)
            continuity = nextContinuity
            currentFrame = frame
            active = nextActive
            output.setForeground(nextActive)
            return
        }

        val previous = currentFrame ?: return
        // An obsolete delivery must not lower the watermark and re-arm a previously heard event.
        if (frame.acceptedPlaySequence < previous.acceptedPlaySequence || frame.outcomeSequence < previous.outcomeSequence) {
            return
        }
        currentFrame = frame
        when {
            frame.outcomeSequence > previous.outcomeSequence && frame.resultCue != null -> {
                cancelResult()
                if (settings.enabled) {
                    output.play(LastLightFeedbackCue.CHALLENGE, settings)
                    scheduleResult(frame.outcomeSequence, nextContinuity)
                }
            }
            frame.acceptedPlaySequence > previous.acceptedPlaySequence && frame.hasLatestClaim -> {
                cancelResult()
                if (settings.enabled) output.play(LastLightFeedbackCue.CARD_PLAY, settings)
            }
            frame.resultCue == null -> cancelResult()
        }
    }

    fun close() {
        if (closed) return
        closed = true
        active = false
        currentFrame = null
        cancelResult()
        output.close()
    }

    private fun scheduleResult(outcomeSequence: Long, expectedContinuity: Continuity) {
        resultJob = scope.launch {
            delay(CHALLENGE_RESULT_DELAY_MILLIS)
            val frame = currentFrame
            if (!closed && active && continuity == expectedContinuity && frame?.outcomeSequence == outcomeSequence) {
                frame.resultCue?.takeIf { settings.enabled }?.let { output.play(it, settings) }
            }
            resultJob = null
        }
    }

    private fun cancelResult() {
        resultJob?.cancel()
        resultJob = null
    }

    private data class Continuity(val sessionKey: Any, val interruptionEpoch: Long)

    private companion object {
        const val CHALLENGE_RESULT_DELAY_MILLIS = 260L
    }
}
