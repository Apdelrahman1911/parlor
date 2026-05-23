package com.parlor.designsystem.sound

/**
 * Sound contract per docs/DESIGN_TOKENS.md §9.
 *
 * Each cue is a named, platform-implemented effect. The session controller
 * triggers cues in response to engine events; the UI may also trigger small
 * UI cues directly.
 *
 * Sound respects the user's `soundEnabled` preference (independent of
 * `reduceMotion`).
 */
interface SoundscapeController {
    fun play(cue: SoundCue)
    fun startAmbient(ambient: AmbientLayer)
    fun stopAmbient()
    fun setEnabled(enabled: Boolean)
}

/** Named one-shot sound cues. */
enum class SoundCue {
    REVEAL,            // wax-seal crack
    COVER,             // soft swipe + candle-glow hum
    CHIME,             // round transition
    GASP,              // reveal punctuation
    WAX_SEAL_PULSE,    // hold-to-reveal loop
    TIMER_WARNING,     // last 10 seconds of a discussion timer
    VOTE_CAST,         // quiet vote confirmation
}

/** Looping ambient layer. */
enum class AmbientLayer {
    PARLOR,            // distant piano, fireplace, faint clock tick
}

/** No-op implementation — useful for previews and tests. */
object NoOpSoundscapeController : SoundscapeController {
    override fun play(cue: SoundCue) = Unit
    override fun startAmbient(ambient: AmbientLayer) = Unit
    override fun stopAmbient() = Unit
    override fun setEnabled(enabled: Boolean) = Unit
}
