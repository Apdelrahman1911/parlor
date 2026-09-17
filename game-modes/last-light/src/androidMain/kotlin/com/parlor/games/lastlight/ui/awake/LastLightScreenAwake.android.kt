package com.parlor.games.lastlight.ui.awake

import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import java.lang.ref.WeakReference
import java.util.WeakHashMap

@Composable
internal actual fun rememberLastLightScreenAwake(): LastLightScreenAwake {
    val view = LocalView.current
    return remember(view) { AndroidScreenAwake(view) }
}

private object ViewAwakeLeases {
    private val pools = WeakHashMap<View, ScreenAwakeLeases>()

    fun forView(view: View): ScreenAwakeLeases = pools.getOrPut(view) {
        val reference = WeakReference(view)
        ScreenAwakeLeases(
            read = { reference.get()?.keepScreenOn == true },
            write = { enabled -> reference.get()?.keepScreenOn = enabled },
        )
    }
}

private class AndroidScreenAwake(private val view: View) : LastLightScreenAwake {
    private val leases = ViewAwakeLeases.forView(view)
    private val owner = Any()
    private var requested = false
    private var closed = false
    private var observer: ViewTreeObserver? = null
    private val focusListener = ViewTreeObserver.OnWindowFocusChangeListener { focused ->
        if (focused) update() else leases.release(owner)
    }
    private val attachmentListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) {
            observeFocus()
            update()
        }
        override fun onViewDetachedFromWindow(view: View) {
            removeFocusObserver()
            leases.release(owner)
        }
    }

    override fun prepare() {
        if (closed || observer != null) return
        observeFocus()
        view.addOnAttachStateChangeListener(attachmentListener)
    }

    private fun observeFocus() {
        if (closed || observer === view.viewTreeObserver) return
        removeFocusObserver()
        observer = view.viewTreeObserver.also { it.addOnWindowFocusChangeListener(focusListener) }
    }

    private fun removeFocusObserver() {
        observer?.takeIf { it.isAlive }?.removeOnWindowFocusChangeListener(focusListener)
        observer = null
    }

    override fun setEnabled(enabled: Boolean) {
        if (closed) return
        requested = enabled
        update()
    }

    private fun update() {
        if (!closed && requested && view.isAttachedToWindow && view.hasWindowFocus() && view.windowVisibility == View.VISIBLE) {
            leases.acquire(owner)
        } else {
            leases.release(owner)
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        requested = false
        leases.release(owner)
        removeFocusObserver()
        view.removeOnAttachStateChangeListener(attachmentListener)
    }
}
