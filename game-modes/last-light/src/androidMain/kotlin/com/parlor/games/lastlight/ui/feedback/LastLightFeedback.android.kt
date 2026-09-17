package com.parlor.games.lastlight.ui.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.parlor.games.lastlight.resources.Res
import java.io.File
import java.io.IOException
import java.lang.ref.WeakReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi

@Composable
internal actual fun rememberLastLightFeedbackOutput(): LastLightFeedbackOutput {
    val context = LocalContext.current.applicationContext
    val view = LocalView.current
    val output = remember(context) { AndroidLastLightFeedback(context) }
    DisposableEffect(output, view) {
        output.attachView(view)
        val observer = view.viewTreeObserver
        val focusListener = ViewTreeObserver.OnWindowFocusChangeListener { focused ->
            if (!focused) output.stopSound()
        }
        val attachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = Unit
            override fun onViewDetachedFromWindow(view: View) = output.stopSound()
        }
        observer.addOnWindowFocusChangeListener(focusListener)
        view.addOnAttachStateChangeListener(attachListener)
        onDispose {
            if (observer.isAlive) observer.removeOnWindowFocusChangeListener(focusListener)
            view.removeOnAttachStateChangeListener(attachListener)
            output.detachView(view)
        }
    }
    return output
}

/** Native playback and haptics stay on main; only bundled sample reads run on IO. */
private class AndroidLastLightFeedback(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : LastLightFeedbackOutput {
    private val applicationContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val audioManager = applicationContext.getSystemService(AudioManager::class.java)
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(audioAttributes)
        .setOnAudioFocusChangeListener({ change -> if (change < 0) stopSound() }, mainHandler)
        .build()
    private var viewReference = WeakReference<View>(null)
    private var soundPool: SoundPool? = null
    private val sampleIds = mutableMapOf<LastLightFeedbackCue, Int>()
    private val loadedSamples = mutableSetOf<Int>()
    private val streams = ArrayDeque<Int>(MAX_STREAMS)
    private var prepared = false
    private var closed = false
    private var foreground = false
    private var hasAudioFocus = false
    private var focusEndsAt = 0L
    private val finishPlayback = Runnable { stopSound() }

    fun attachView(view: View) {
        if (!closed) viewReference = WeakReference(view)
    }

    fun detachView(view: View) {
        if (viewReference.get() === view) {
            stopSound()
            viewReference.clear()
        }
    }

    @OptIn(ExperimentalResourceApi::class)
    override fun prepare() {
        if (prepared || closed) return
        prepared = true
        scope.launch {
            val pool = try {
                SoundPool.Builder().setMaxStreams(MAX_STREAMS).setAudioAttributes(audioAttributes).build()
            } catch (@Suppress("TooGenericExceptionCaught") failure: RuntimeException) {
                // Optional native audio may be unavailable on a device; no suspension occurs in this boundary.
                Log.w(TAG, "Game sound effects are unavailable", failure)
                return@launch
            }
            soundPool = pool
            pool.setOnLoadCompleteListener { completedPool, sampleId, status ->
                if (!closed && soundPool === completedPool && status == 0) loadedSamples.add(sampleId)
            }
            for (cue in LastLightFeedbackCue.entries) {
                try {
                    val file = withContext(ioDispatcher) {
                        val directory = File(applicationContext.cacheDir, "last_light_audio")
                        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Audio cache unavailable")
                        File(directory, cue.fileName).also { it.writeBytes(Res.readBytes(cue.resourcePath)) }
                    }
                    if (closed) return@launch
                    pool.load(file.absolutePath, 1).takeIf { it != 0 }?.let { sampleIds[cue] = it }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
                    // Resource/decoder failure disables only this optional cue; cancellation remains observable.
                    Log.w(TAG, "A game sound effect could not be prepared", failure)
                }
            }
        }
    }

    override fun play(cue: LastLightFeedbackCue, settings: LastLightFeedbackSettings) {
        if (closed || !foreground) return
        val view = viewReference.get()?.takeIf { it.isAttachedToWindow && it.hasWindowFocus() } ?: return
        if (settings.hapticsEnabled) view.performHapticFeedback(cue.hapticConstant())
        if (settings.soundEnabled) {
            try {
                playSound(cue)
            } catch (_: SecurityException) {
                // Focus can be refused when Android changes top-app ownership between the checks above.
                stopSound()
            }
        }
    }

    override fun setForeground(value: Boolean) {
        if (closed) return
        foreground = value
        if (!value) stopSound()
    }

    override fun stopSound() {
        mainHandler.removeCallbacks(finishPlayback)
        streams.forEach { soundPool?.stop(it) }
        streams.clear()
        focusEndsAt = 0L
        if (hasAudioFocus) {
            hasAudioFocus = false
            audioManager?.abandonAudioFocusRequest(focusRequest)
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        foreground = false
        scope.cancel()
        stopSound()
        viewReference.clear()
        soundPool?.setOnLoadCompleteListener(null)
        soundPool?.release()
        soundPool = null
        sampleIds.clear()
        loadedSamples.clear()
    }

    private fun playSound(cue: LastLightFeedbackCue) {
        val pool = soundPool ?: return
        val sampleId: Int? = sampleIds[cue]
        if (sampleId == null) return
        if (!loadedSamples.contains(sampleId)) return
        if (!hasAudioFocus) {
            hasAudioFocus = audioManager?.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        if (!hasAudioFocus) return
        val stream = pool.play(sampleId, 1f, 1f, 1, 0, 1f)
        if (stream != 0) {
            if (streams.size == MAX_STREAMS) pool.stop(streams.removeFirst())
            streams.addLast(stream)
        }
        focusEndsAt = maxOf(focusEndsAt, SystemClock.uptimeMillis() + cue.durationMillis + AUDIO_TAIL_MILLIS)
        mainHandler.removeCallbacks(finishPlayback)
        mainHandler.postAtTime(finishPlayback, focusEndsAt)
    }

    private fun LastLightFeedbackCue.hapticConstant(): Int = when (this) {
        LastLightFeedbackCue.CLICK, LastLightFeedbackCue.CARD_PLAY -> HapticFeedbackConstants.CONTEXT_CLICK
        LastLightFeedbackCue.CHALLENGE, LastLightFeedbackCue.LIGHT_OUT -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
        }
        LastLightFeedbackCue.ROUND_END, LastLightFeedbackCue.WIN -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK
        }
    }

    private companion object {
        const val TAG = "LastLightFeedback"
        const val MAX_STREAMS = 4
        const val AUDIO_TAIL_MILLIS = 40L
    }
}
