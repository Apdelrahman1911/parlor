package com.parlor.games.lastlight.ui.feedback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.parlor.games.lastlight.resources.Res
import java.awt.GraphicsEnvironment
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi

@Composable
internal actual fun rememberLastLightFeedbackOutput(): LastLightFeedbackOutput =
    remember { DesktopLastLightFeedback() }

/** Optional desktop development audio. Headless tests never open an audio line; desktop has no haptics. */
private class DesktopLastLightFeedback(
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : LastLightFeedbackOutput {
    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val clips = mutableMapOf<LastLightFeedbackCue, Clip>()
    private var prepared = false
    private var closed = false
    private var foreground = false

    @OptIn(ExperimentalResourceApi::class)
    override fun prepare() {
        synchronized(lock) {
            if (prepared || closed || GraphicsEnvironment.isHeadless()) return
            prepared = true
        }
        scope.launch {
            for (cue in LastLightFeedbackCue.entries) {
                var clip: Clip? = null
                try {
                    val bytes = Res.readBytes(cue.resourcePath)
                    val loadedClip = AudioSystem.getClip()
                    clip = loadedClip
                    AudioSystem.getAudioInputStream(bytes.inputStream()).use { loadedClip.open(it) }
                    synchronized(lock) {
                        if (closed) loadedClip.close() else clips[cue] = loadedClip
                    }
                    clip = null
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
                    // Audio devices/codecs are optional in desktop test/dev environments; never queue a retry.
                } finally {
                    clip?.close()
                }
            }
        }
    }

    override fun play(cue: LastLightFeedbackCue, settings: LastLightFeedbackSettings) {
        synchronized(lock) {
            if (closed || !foreground || !settings.soundEnabled) return
            val clip = clips[cue] ?: return
            stopClips()
            clip.framePosition = 0
            clip.start()
        }
    }

    override fun setForeground(value: Boolean) {
        synchronized(lock) {
            if (closed) return
            foreground = value
            if (!value) stopClips()
        }
    }

    override fun stopSound() {
        synchronized(lock) { stopClips() }
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            foreground = false
            scope.cancel()
            stopClips()
            clips.values.forEach(Clip::close)
            clips.clear()
        }
    }

    private fun stopClips() {
        clips.values.forEach(Clip::stop)
    }
}
