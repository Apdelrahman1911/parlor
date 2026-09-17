package com.parlor.games.lastlight.ui.flow.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.parlor.designsystem.components.SessionExitKind
import com.parlor.designsystem.components.parlorSafeContentPadding
import com.parlor.designsystem.components.sessionExitCopy
import com.parlor.games.lastlight.resources.Res
import com.parlor.games.lastlight.resources.table_leaving
import com.parlor.games.lastlight.resources.table_saving
import com.parlor.games.lastlight.ui.game.TableBackdrop
import com.parlor.games.lastlight.ui.game.TablePanel
import com.parlor.games.lastlight.ui.theme.DeckButton
import com.parlor.games.lastlight.ui.theme.LastLightColors
import com.parlor.games.lastlight.ui.theme.LastLightTheme
import org.jetbrains.compose.resources.stringResource

/** Replaces gameplay entirely; no private pixels or semantics remain behind the confirmation. */
@Composable
internal fun LastLightExitConfirmation(
    kind: SessionExitKind,
    onStay: () -> Unit,
    onExit: () -> Unit,
    exitInFlight: Boolean,
    modifier: Modifier = Modifier,
) {
    val copy = sessionExitCopy(kind)
    val destructive = kind != SessionExitKind.Local
    val progress = stringResource(if (destructive) Res.string.table_leaving else Res.string.table_saving)
    LastLightTheme {
        TableBackdrop(modifier.fillMaxSize().testTag("game-exit-confirmation").semantics { paneTitle = copy.title }) {
            Column(
                modifier = Modifier.widthIn(max = 520.dp).fillMaxSize().align(Alignment.Center)
                    .verticalScroll(rememberScrollState()).parlorSafeContentPadding(20.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                TablePanel(accent = if (destructive) LastLightColors.Copper.copy(alpha = 0.65f) else LastLightColors.Divider) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TableControlGlyph(TableGlyph.Leave, Modifier.size(42.dp), LastLightColors.Muted)
                    }
                    Text(
                        copy.title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = LastLightColors.Paper,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().semantics { heading() },
                    )
                    Text(
                        copy.body,
                        style = MaterialTheme.typography.bodyLarge,
                        color = LastLightColors.Muted,
                        textAlign = TextAlign.Center,
                    )
                    DeckButton(
                        text = copy.stay,
                        onClick = onStay,
                        enabled = !exitInFlight,
                        secondary = true,
                        modifier = Modifier.fillMaxWidth().testTag("game-stay")
                            .semantics { contentDescription = copy.stayDescription },
                    )
                    DeckButton(
                        text = if (exitInFlight) progress else copy.confirm,
                        onClick = onExit,
                        enabled = !exitInFlight,
                        accent = if (destructive) LastLightColors.Copper else LastLightColors.Citron,
                        modifier = Modifier.fillMaxWidth().testTag("game-confirm-leave").semantics {
                            contentDescription = copy.confirmDescription
                            if (exitInFlight) stateDescription = progress
                            liveRegion = LiveRegionMode.Polite
                        },
                    )
                }
            }
        }
    }
}
