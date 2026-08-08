package com.parlor.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.parlor.designsystem.theme.ParlorTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Severity buckets that map to a Parlor color from [com.parlor.designsystem.tokens.ParlorColors].
 */
enum class ParlorToastSeverity { Info, Success, Warning, Danger }

/**
 * One toast — created by features, rendered by [ParlorToastHost].
 *
 * `text` is already-localized; `severity` picks the leading-edge accent.
 * `id` is monotonic so a feature can de-dupe ("Alice reconnected" should
 * not stack three deep if the connection flaps).
 */
data class ParlorToast(
    val id: Long,
    val text: String,
    val severity: ParlorToastSeverity = ParlorToastSeverity.Info,
)

/**
 * State holder for the toast queue. One instance per app — install it
 * at the App root via [LocalParlorToastState]. Features emit via
 * `show(...)`; the host composable consumes [toasts] for rendering.
 *
 * The queue auto-expires entries after [defaultDurationMs]. Same-text
 * back-to-back toasts coalesce on insertion — the test for "Alice
 * disconnected" + "Alice disconnected" yields one toast, not two.
 */
class ParlorToastState(
    private val defaultDurationMs: Long = 3_500L,
) {
    private val _toasts: MutableStateFlow<List<ParlorToast>> = MutableStateFlow(emptyList())
    val toasts: StateFlow<List<ParlorToast>> = _toasts
    private var nextId: Long = 1L

    fun show(text: String, severity: ParlorToastSeverity = ParlorToastSeverity.Info) {
        val current = _toasts.value
        // Coalesce: if the most recent toast carries the same text we leave it alone.
        if (current.lastOrNull()?.text == text) return
        val toast = ParlorToast(id = nextId++, text = text, severity = severity)
        _toasts.update { it + toast }
    }

    /** Internal — the host invokes this when its dismissal timer fires. */
    internal fun dismiss(id: Long) {
        _toasts.update { current -> current.filterNot { it.id == id } }
    }

    /** Read so the host can compute the dismiss delay. */
    internal fun durationFor(toast: ParlorToast): Long = defaultDurationMs
}

/**
 * CompositionLocal that gives any descendant Composable a handle to the
 * app-wide toast queue. Wrap your App root in:
 *
 * ```
 * val toasts = remember { ParlorToastState() }
 * CompositionLocalProvider(LocalParlorToastState provides toasts) {
 *     Box { App(); ParlorToastHost(toasts) }
 * }
 * ```
 */
val LocalParlorToastState = compositionLocalOf<ParlorToastState> {
    error("LocalParlorToastState not provided — wrap the App root with one.")
}

/**
 * Renders the queue. Stack toasts bottom-up so the newest sits closest
 * to the user. Each toast auto-dismisses after its duration; users
 * cannot dismiss manually for the first pass — feature would have to
 * lean on a different primitive if interactive dismissal mattered.
 */
@Composable
fun ParlorToastHost(
    state: ParlorToastState = LocalParlorToastState.current,
    modifier: Modifier = Modifier,
) {
    val toasts by state.toasts.collectAsState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(ParlorTheme.spacing.l),
        verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        toasts.forEach { toast ->
            // Per-toast auto-dismiss keyed on the stable toast id, so EVERY
            // queued toast expires — not just the newest. The previous single
            // LaunchedEffect keyed on toasts.lastOrNull() left older toasts
            // stranded forever (see PROBLEMS_PARLOR.md → ds-01).
            key(toast.id) {
                LaunchedEffect(toast.id) {
                    delay(state.durationFor(toast))
                    state.dismiss(toast.id)
                }
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
                ) {
                    ToastChip(toast)
                }
            }
        }
    }
}

@Composable
private fun ToastChip(toast: ParlorToast) {
    val accent: Color = when (toast.severity) {
        ParlorToastSeverity.Info -> ParlorTheme.colors.accentBrass
        ParlorToastSeverity.Success -> ParlorTheme.colors.semanticSuccess
        ParlorToastSeverity.Warning -> ParlorTheme.colors.accentEmber
        ParlorToastSeverity.Danger -> ParlorTheme.colors.semanticDanger
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(ParlorTheme.radii.pill))
            .background(ParlorTheme.colors.surfaceElevated)
            .padding(
                horizontal = ParlorTheme.spacing.l,
                vertical = ParlorTheme.spacing.s,
            ),
    ) {
        Text(
            text = toast.text,
            style = ParlorTheme.typography.bodyMedium,
            color = accent,
        )
    }
}
