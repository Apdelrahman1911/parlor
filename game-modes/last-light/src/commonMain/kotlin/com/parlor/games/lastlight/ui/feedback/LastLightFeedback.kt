package com.parlor.games.lastlight.ui.feedback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import com.parlor.games.lastlight.domain.model.GameView

/**
 * Observes accepted recipient views. Gameplay drawing and card selection never emit feedback.
 * [interruptionEpoch] must change after an interruption even when foreground changes are conflated.
 */
@Composable
fun LastLightFeedbackEffect(
    sessionKey: Any,
    game: GameView?,
    connected: Boolean,
    foreground: Boolean,
    soundEnabled: Boolean = true,
    hapticsEnabled: Boolean = true,
    interruptionEpoch: Long = 0L,
    celebrateAnyWinner: Boolean = false,
) {
    val output = LocalLastLightFeedbackOutput.current ?: rememberLastLightFeedbackOutput()
    val scope = rememberCoroutineScope()
    val controller = remember(output, scope) { LastLightFeedbackController(output, scope) }
    val frame = game?.feedbackFrame(celebrateAnyWinner)
    val settings = LastLightFeedbackSettings(soundEnabled, hapticsEnabled)

    DisposableEffect(controller) {
        output.prepare()
        onDispose { controller.close() }
    }
    LaunchedEffect(controller, sessionKey, frame, connected, foreground, settings, interruptionEpoch) {
        controller.accept(sessionKey, frame, connected, foreground, settings, interruptionEpoch)
    }
}

internal data class LastLightFeedbackSettings(
    val soundEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
) {
    val enabled: Boolean get() = soundEnabled || hapticsEnabled
}

internal enum class LastLightFeedbackCue(val fileName: String, val durationMillis: Long) {
    CLICK("ui_tap.wav", 70L),
    CARD_PLAY("card_place.wav", 240L),
    CHALLENGE("challenge.wav", 480L),
    ROUND_END("safe.wav", 640L),
    LIGHT_OUT("light_out.wav", 520L),
    WIN("victory.wav", 1_280L),
    ;

    val resourcePath: String get() = "files/audio/$fileName"
}

/** Main-thread, bounded native effects. Unavailable or unprepared samples are dropped, never queued. */
internal interface LastLightFeedbackOutput {
    fun prepare()
    fun play(cue: LastLightFeedbackCue, settings: LastLightFeedbackSettings)
    fun setForeground(value: Boolean)
    fun stopSound()
    fun close()
}

/** Allows deterministic presentation tests to observe the same effect without opening audio devices. */
internal val LocalLastLightFeedbackOutput = staticCompositionLocalOf<LastLightFeedbackOutput?> { null }

@Composable
internal expect fun rememberLastLightFeedbackOutput(): LastLightFeedbackOutput
