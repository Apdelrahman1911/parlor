package com.parlor.games.lastlight.ui.feedback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.parlor.games.lastlight.resources.Res
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryAmbient
import platform.AVFAudio.AVAudioSessionInterruptionNotification
import platform.AVFAudio.AVAudioSessionInterruptionTypeBegan
import platform.AVFAudio.AVAudioSessionInterruptionTypeKey
import platform.AVFAudio.AVAudioSessionRouteChangeNotification
import platform.AVFAudio.AVAudioSessionRouteChangeReasonKey
import platform.AVFAudio.AVAudioSessionRouteChangeReasonOldDeviceUnavailable
import platform.AVFAudio.AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation
import platform.AVFAudio.setActive
import platform.Foundation.NSLog
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationState.UIApplicationStateActive
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle.UIImpactFeedbackStyleLight
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType.UINotificationFeedbackTypeError
import platform.UIKit.UINotificationFeedbackType.UINotificationFeedbackTypeSuccess
import platform.UIKit.UINotificationFeedbackType.UINotificationFeedbackTypeWarning
import platform.UIKit.UISelectionFeedbackGenerator

@Composable
internal actual fun rememberLastLightFeedbackOutput(): LastLightFeedbackOutput =
    remember { IosLastLightFeedback() }

/** Bundled files open off-main. Playback, UIKit feedback and notification handling are main-thread only. */
@OptIn(ExperimentalForeignApi::class)
private class IosLastLightFeedback(
    private val preloadDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : LastLightFeedbackOutput {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val session = AVAudioSession.sharedInstance()
    private val selection = UISelectionFeedbackGenerator()
    private val impact = UIImpactFeedbackGenerator(style = UIImpactFeedbackStyleLight)
    private val notification = UINotificationFeedbackGenerator()
    private val players = mutableMapOf<LastLightFeedbackCue, AVAudioPlayer>()
    private val removeObservers = mutableListOf<() -> Unit>()
    private var prepared = false
    private var foreground = false
    private var closed = false
    private var interrupted = false
    private var audioActive = false
    private var categoryConfigured = false
    private var audioTail: Job? = null

    override fun prepare() {
        if (prepared || closed) return
        prepared = true
        observe(UIApplicationWillResignActiveNotification) { stopSound() }
        observe(AVAudioSessionInterruptionNotification) { notice ->
            val type = (notice?.userInfo?.get(AVAudioSessionInterruptionTypeKey) as? NSNumber)?.unsignedLongLongValue
            interrupted = type == null || type == AVAudioSessionInterruptionTypeBegan
            if (interrupted) stopSound()
            // Ending an interruption never resumes a finished or interrupted cue.
        }
        observe(AVAudioSessionRouteChangeNotification) { notice ->
            val reason = (notice?.userInfo?.get(AVAudioSessionRouteChangeReasonKey) as? NSNumber)?.unsignedLongLongValue
            if (reason == AVAudioSessionRouteChangeReasonOldDeviceUnavailable) stopSound()
        }
        preload()
    }

    override fun play(cue: LastLightFeedbackCue, settings: LastLightFeedbackSettings) {
        if (closed || !foreground || UIApplication.sharedApplication.applicationState != UIApplicationStateActive) return
        if (settings.hapticsEnabled) playHaptic(cue)
        if (settings.soundEnabled && !interrupted) playSound(cue)
    }

    override fun setForeground(value: Boolean) {
        if (closed) return
        foreground = value
        if (!value) stopSound()
    }

    override fun stopSound() {
        audioTail?.cancel()
        audioTail = null
        stopPlayers()
        if (audioActive) {
            audioActive = false
            session.setActive(false, withOptions = AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation, error = null)
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        foreground = false
        scope.cancel()
        removeObservers.forEach { it() }
        removeObservers.clear()
        stopSound()
        players.clear()
    }

    @OptIn(ExperimentalResourceApi::class)
    private fun preload() {
        scope.launch {
            for (cue in LastLightFeedbackCue.entries) {
                try {
                    val player = withContext(preloadDispatcher) {
                        val uri = Res.getUri(cue.resourcePath)
                        require(uri.startsWith("file:")) { "Audio resource must be bundled" }
                        val url = requireNotNull(NSURL.URLWithString(uri))
                        AVAudioPlayer(contentsOfURL = url, error = null).apply { numberOfLoops = 0 }
                    }
                    if (closed) {
                        player.stop()
                        return@launch
                    }
                    players[cue] = player
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
                    // An optional bundled decoder/resource failure must not terminate the game; preserve cancellation above.
                    NSLog("A Last Light sound effect could not be prepared.")
                }
            }
        }
    }

    private fun playSound(cue: LastLightFeedbackCue) {
        val player = players[cue] ?: return
        if (!categoryConfigured) {
            // Ambient effects respect the silent switch and mix with the user's existing audio.
            categoryConfigured = session.setCategory(AVAudioSessionCategoryAmbient, error = null)
        }
        if (!categoryConfigured) return
        if (!audioActive) audioActive = session.setActive(true, error = null)
        if (!audioActive) return
        audioTail?.cancel()
        stopPlayers()
        player.currentTime = 0.0
        if (!player.prepareToPlay() || !player.play()) {
            stopSound()
            return
        }
        // One audible cue at a time; release the session after the known bundled duration.
        audioTail = scope.launch {
            delay(cue.durationMillis + AUDIO_TAIL_MILLIS)
            stopSound()
        }
    }

    private fun playHaptic(cue: LastLightFeedbackCue) {
        when (cue) {
            LastLightFeedbackCue.CLICK -> selection.selectionChanged()
            LastLightFeedbackCue.CARD_PLAY -> impact.impactOccurred()
            LastLightFeedbackCue.CHALLENGE -> notification.notificationOccurred(UINotificationFeedbackTypeWarning)
            LastLightFeedbackCue.LIGHT_OUT -> notification.notificationOccurred(UINotificationFeedbackTypeError)
            LastLightFeedbackCue.ROUND_END, LastLightFeedbackCue.WIN -> {
                notification.notificationOccurred(UINotificationFeedbackTypeSuccess)
            }
        }
    }

    private fun stopPlayers() {
        players.values.forEach {
            it.stop()
            it.currentTime = 0.0
        }
    }

    private fun observe(name: String?, action: (NSNotification?) -> Unit) {
        // A nil notification name would subscribe to every notification.
        if (name == null) return
        val center = NSNotificationCenter.defaultCenter
        val observer = center.addObserverForName(name, `object` = null, queue = NSOperationQueue.mainQueue) { notice ->
            if (!closed) action(notice)
        }
        removeObservers += { center.removeObserver(observer) }
    }

    private companion object {
        const val AUDIO_TAIL_MILLIS = 40L
    }
}
