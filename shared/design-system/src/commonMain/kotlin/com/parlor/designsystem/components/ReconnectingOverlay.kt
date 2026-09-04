package com.parlor.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.parlor.designsystem.theme.ParlorTheme

/**
 * Excludes mounted content covered by a [ReconnectingOverlay] from semantics
 * and hardware-keyboard focus traversal. Apply this to the covered container,
 * while keeping the overlay itself as a sibling so its Leave action remains
 * reachable.
 */
fun Modifier.coveredByReconnectingOverlay(overlayVisible: Boolean): Modifier =
    focusProperties { canFocus = !overlayVisible }
        .then(if (overlayVisible) Modifier.clearAndSetSemantics { } else Modifier)

/**
 * Full-screen overlay shown on a peer device when the bridge synthesises
 * [com.parlor.networking.room.PeerEvent.HostLost]. The current gameplay
 * UI stays mounted behind the overlay so reconnect lands cleanly without
 * recreating Compose state.
 *
 * The escape button is the user's only graceful exit when the host
 * cannot be reached. Layout never assumes LTR — children center on
 * both axes so RTL "just works".
 */
@Composable
fun ReconnectingOverlay(
    title: String,
    leaveLabel: String,
    leaveContentDescription: String,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ParlorTheme.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.coverScreen)
            .semantics {
                paneTitle = title
                liveRegion = LiveRegionMode.Assertive
            }
            .parlorSafeContentPadding(ParlorTheme.spacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        // The observing session remains composed behind this surface so it can
        // report recovery. Consume blank-area pointer input here; the visible
        // leave button is a later sibling and retains its own hit target.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial)
                                .changes
                                .forEach { change -> change.consume() }
                        }
                    }
                },
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(
                ParlorTheme.spacing.l,
                Alignment.CenterVertically,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            ParlorActivityIndicator(
                modifier = Modifier.size(ParlorTheme.spacing.xxl),
                color = colors.accentEmber,
                trackColor = colors.borderSubtle,
                strokeWidth = ParlorTheme.borders.strong,
            )
            Text(
                text = title,
                style = ParlorTheme.typography.displayMedium,
                color = colors.coverScreenTextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            ParlorButton(
                label = leaveLabel,
                contentDescription = leaveContentDescription,
                onClick = onLeave,
                modifier = Modifier.fillMaxWidth(),
                variant = ParlorButtonVariant.Ghost,
            )
        }
    }
}
