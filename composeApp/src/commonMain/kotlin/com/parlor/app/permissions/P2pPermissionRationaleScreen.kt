package com.parlor.app.permissions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.parlor.app.resources.Res
import com.parlor.app.resources.permission_back
import com.parlor.app.resources.permission_back_description
import com.parlor.app.resources.permission_body
import com.parlor.app.resources.permission_continue
import com.parlor.app.resources.permission_continue_description
import com.parlor.app.resources.permission_denied_body
import com.parlor.app.resources.permission_denied_eyebrow
import com.parlor.app.resources.permission_eyebrow
import com.parlor.app.resources.permission_grant
import com.parlor.app.resources.permission_grant_description
import com.parlor.app.resources.permission_open_settings
import com.parlor.app.resources.permission_open_settings_description
import com.parlor.app.resources.permission_permanently_denied_body
import com.parlor.app.resources.permission_permanently_denied_eyebrow
import com.parlor.app.resources.permission_title
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.EyebrowLabel
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorButtonVariant
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.theme.ParlorTheme
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * Sits in front of HostName / JoinName when [gate.status] isn't Granted.
 * Three states:
 *  - NotRequested / Denied → rationale + "Grant access" (calls `gate.request()`)
 *  - PermanentlyDenied → "Open Settings" instructions
 *  - Granted → automatically routed past this screen by the caller
 */
@Composable
fun P2pPermissionRationaleScreen(
    gate: P2pPermissionGate,
    onGranted: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status by gate.status.collectAsState()
    val scope = rememberCoroutineScope()

    // If the OS state flips to Granted (e.g. user came back from Settings),
    // hand off to the caller automatically. Keep the callback in an effect:
    // invoking it directly during composition can mutate the parent
    // navigation state while Compose is still applying the current frame.
    if (status == PermissionStatus.Granted) {
        LaunchedEffect(Unit) { onGranted() }
        return
    }

    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ParlorTheme.spacing.xl)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.l),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EyebrowLabel(
                text = if (status == PermissionStatus.PermanentlyDenied) {
                    stringResource(Res.string.permission_permanently_denied_eyebrow)
                } else if (status == PermissionStatus.Denied) {
                    stringResource(Res.string.permission_denied_eyebrow)
                } else {
                    stringResource(Res.string.permission_eyebrow)
                },
            )
            Text(
                text = stringResource(Res.string.permission_title),
                style = ParlorTheme.typography.displayLarge,
                color = ParlorTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            ParlorCard(
                modifier = Modifier.fillMaxWidth(),
                elevation = ParlorTheme.elevation.high,
                cornerRadius = ParlorTheme.radii.elevated,
                contentPadding = ParlorTheme.spacing.xl,
                hero = true,
            ) {
                Text(
                    text = when (status) {
                        PermissionStatus.PermanentlyDenied ->
                            stringResource(Res.string.permission_permanently_denied_body)
                        PermissionStatus.Denied ->
                            stringResource(Res.string.permission_denied_body)
                        else ->
                            stringResource(Res.string.permission_body)
                    },
                    style = ParlorTheme.typography.bodyLarge,
                    color = ParlorTheme.colors.textPrimary,
                )
            }

            Spacer(modifier = Modifier.height(ParlorTheme.spacing.m))

            if (status == PermissionStatus.PermanentlyDenied) {
                ParlorButton(
                    label = stringResource(Res.string.permission_open_settings),
                    contentDescription = stringResource(Res.string.permission_open_settings_description),
                    onClick = { gate.openAppSettings() },
                    modifier = Modifier.fillMaxWidth(),
                )
                ParlorButton(
                    label = stringResource(Res.string.permission_continue),
                    contentDescription = stringResource(Res.string.permission_continue_description),
                    onClick = {
                        // The user has come back from Settings — re-check.
                        scope.launch { gate.request() }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    variant = ParlorButtonVariant.Secondary,
                )
            } else {
                ParlorButton(
                    label = stringResource(Res.string.permission_grant),
                    contentDescription = stringResource(Res.string.permission_grant_description),
                    onClick = {
                        scope.launch {
                            if (gate.request() == PermissionStatus.Granted) onGranted()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            ParlorButton(
                label = stringResource(Res.string.permission_back),
                contentDescription = stringResource(Res.string.permission_back_description),
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                variant = ParlorButtonVariant.Ghost,
            )
        }
    }
}
