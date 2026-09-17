package com.parlor.games.lastlight.ui.flow.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.parlor.games.lastlight.resources.Res
import com.parlor.games.lastlight.resources.local_options_close
import com.parlor.games.lastlight.resources.local_options_haptics
import com.parlor.games.lastlight.resources.local_options_sound
import com.parlor.games.lastlight.resources.local_options_title
import com.parlor.games.lastlight.resources.table_options_detail
import com.parlor.games.lastlight.resources.table_keep_awake
import com.parlor.games.lastlight.resources.table_keep_awake_detail
import com.parlor.games.lastlight.resources.game_fuse_label
import com.parlor.games.lastlight.ui.game.FuseCylinder
import com.parlor.games.lastlight.ui.game.FuseExplanation
import com.parlor.games.lastlight.ui.game.FuseLegend
import com.parlor.games.lastlight.ui.game.TablePanel
import com.parlor.games.lastlight.ui.theme.DeckButton
import com.parlor.games.lastlight.ui.theme.LastLightColors
import com.parlor.games.lastlight.ui.theme.SectionLabel
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun LastLightTableOptions(
    soundEnabled: Boolean,
    hapticsEnabled: Boolean,
    keepScreenAwake: Boolean,
    onSoundChanged: (Boolean) -> Unit,
    onHapticsChanged: (Boolean) -> Unit,
    onKeepScreenAwakeChanged: (Boolean) -> Unit,
    onClose: () -> Unit,
    availableWidth: Dp,
    availableHeight: Dp,
) {
    val title = stringResource(Res.string.local_options_title)
    val focusRequester = remember { FocusRequester() }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
        TablePanel(
            modifier = Modifier.widthIn(max = minOf(440.dp, availableWidth)).heightIn(max = availableHeight)
                .fillMaxWidth().padding(20.dp)
                .testTag("game-options-dialog").semantics { paneTitle = title }
                .onPreviewKeyEvent { event ->
                    if (event.key == Key.Escape || event.key == Key.Back) {
                        if (event.type == KeyEventType.KeyDown) onClose()
                        true
                    } else false
                }
                .focusRequester(focusRequester).focusable()
                .verticalScroll(rememberScrollState()),
            accent = LastLightColors.Outline,
            padding = 20.dp,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (LocalDensity.current.fontScale < 1.3f) TableControlGlyph(TableGlyph.Options, Modifier.size(28.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = LastLightColors.Paper,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
            }
            Text(
                stringResource(Res.string.table_options_detail),
                style = MaterialTheme.typography.bodySmall,
                color = LastLightColors.Muted,
            )
            FeedbackToggle(stringResource(Res.string.local_options_sound), TableGlyph.Sound, soundEnabled, onSoundChanged)
            FeedbackToggle(stringResource(Res.string.local_options_haptics), TableGlyph.Haptics, hapticsEnabled, onHapticsChanged)
            FeedbackToggle(stringResource(Res.string.table_keep_awake), TableGlyph.Screen, keepScreenAwake, onKeepScreenAwakeChanged)
            Text(
                stringResource(Res.string.table_keep_awake_detail),
                style = MaterialTheme.typography.bodySmall,
                color = LastLightColors.Muted,
            )
            HorizontalDivider(color = LastLightColors.Divider)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                // This is an untested illustration, not a peek at any player's hidden chamber.
                Box(Modifier.size(54.dp).clearAndSetSemantics { }) {
                    FuseCylinder(0, false, Modifier.fillMaxSize())
                }
                SectionLabel(stringResource(Res.string.game_fuse_label), Modifier.weight(1f), LastLightColors.Citron)
            }
            FuseExplanation()
            FuseLegend()
            DeckButton(
                text = stringResource(Res.string.local_options_close),
                onClick = onClose,
                modifier = Modifier.fillMaxWidth().testTag("game-options-close"),
            )
        }
    }
}

@Composable
private fun FeedbackToggle(label: String, glyph: TableGlyph, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .clip(RoundedCornerShape(14.dp))
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (LocalDensity.current.fontScale < 1.3f) TableControlGlyph(glyph, Modifier.size(22.dp), LastLightColors.Muted)
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = LastLightColors.Paper)
        Switch(checked = checked, onCheckedChange = null)
    }
}
