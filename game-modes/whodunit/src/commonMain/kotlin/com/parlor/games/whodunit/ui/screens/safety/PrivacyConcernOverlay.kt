package com.parlor.games.whodunit.ui.screens.safety

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.parlor.designsystem.components.EyebrowLabel
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorButtonVariant
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.whodunit.resources.Res
import com.parlor.games.whodunit.resources.privacy_body
import com.parlor.games.whodunit.resources.privacy_continue
import com.parlor.games.whodunit.resources.privacy_continue_description
import com.parlor.games.whodunit.resources.privacy_peer_body
import com.parlor.games.whodunit.resources.privacy_peer_close
import com.parlor.games.whodunit.resources.privacy_peer_close_description
import com.parlor.games.whodunit.resources.privacy_open
import com.parlor.games.whodunit.resources.privacy_open_description
import com.parlor.games.whodunit.resources.privacy_reroll
import com.parlor.games.whodunit.resources.privacy_reroll_description
import com.parlor.games.whodunit.resources.privacy_title
import org.jetbrains.compose.resources.stringResource

/**
 * Small, low-key text affordance for opening the [PrivacyConcernDialog]. Sits
 * on top of character-reveal chrome so the host can recover from an
 * accidental dossier exposure without leaving the reveal flow.
 */
@Composable
fun PrivacyConcernAffordance(
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val openLabel = stringResource(Res.string.privacy_open)
    val openDescription = stringResource(Res.string.privacy_open_description)
    EyebrowLabel(
        text = openLabel,
        accent = false,
        modifier = modifier
            .heightIn(min = ParlorTheme.spacing.xxl)
            .semantics { contentDescription = openDescription }
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(ParlorTheme.spacing.s),
    )
}

/**
 * Privacy-concern dialog. Used when a player believes someone saw a dossier
 * they shouldn't have during the character-reveal ceremony.
 *
 * - **Continue Anyway** — close the dialog, the game proceeds with the
 *   existing role assignment.
 * - On a host, **Reroll All Roles** submits
 *   [com.parlor.games.whodunit.domain.action.WhodunitAction.RequestReroll].
 *   The reducer reshuffles characters + killer with a new derived seed and
 *   sends the flow back to `CharacterReveal(0)` so the new mapping is
 *   revealed cleanly. Anyone who saw their *old* role must mentally forget
 *   it — the table-side convention the design doc calls out.
 * - On a peer, no reroll command is rendered because the authority layer
 *   rejects it. The dialog truthfully tells the player to contact the host.
 */
@Composable
internal fun PrivacyConcernDialog(
    policy: PrivacyConcernUiPolicy,
    onContinue: () -> Unit,
    onReroll: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    check((policy == PrivacyConcernUiPolicy.HostMayReroll) == (onReroll != null)) {
        "Privacy reroll callback must match host authority"
    }
    val title = stringResource(Res.string.privacy_title)
    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics { paneTitle = title },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ParlorTheme.colors.overlayScrim),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ParlorTheme.spacing.xl),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ParlorCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = ParlorTheme.radii.elevated,
                contentPadding = ParlorTheme.spacing.xl,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m)) {
                    EyebrowLabel(
                        text = title,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = stringResource(
                            if (policy == PrivacyConcernUiPolicy.HostMayReroll) {
                                Res.string.privacy_body
                            } else {
                                Res.string.privacy_peer_body
                            },
                        ),
                        style = ParlorTheme.typography.displayMedium,
                        color = ParlorTheme.colors.textPrimary,
                        textAlign = TextAlign.Start,
                    )
                    Spacer(modifier = Modifier.height(ParlorTheme.spacing.s))
                    if (onReroll != null) {
                        ParlorButton(
                            label = stringResource(Res.string.privacy_reroll),
                            contentDescription = stringResource(Res.string.privacy_reroll_description),
                            onClick = onReroll,
                            modifier = Modifier.fillMaxWidth(),
                            variant = ParlorButtonVariant.Destructive,
                        )
                    }
                    ParlorButton(
                        label = stringResource(
                            if (policy == PrivacyConcernUiPolicy.HostMayReroll) {
                                Res.string.privacy_continue
                            } else {
                                Res.string.privacy_peer_close
                            },
                        ),
                        contentDescription = stringResource(
                            if (policy == PrivacyConcernUiPolicy.HostMayReroll) {
                                Res.string.privacy_continue_description
                            } else {
                                Res.string.privacy_peer_close_description
                            },
                        ),
                        onClick = onContinue,
                        modifier = Modifier.fillMaxWidth(),
                        variant = ParlorButtonVariant.Ghost,
                    )
                }
            }
        }
    }
}
