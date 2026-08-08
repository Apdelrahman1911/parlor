package com.parlor.games.mafia.ui.screens.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.EyebrowLabel
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorButtonVariant
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.mafia.domain.settings.MafiaRoleCounts
import com.parlor.games.mafia.domain.settings.MafiaSettings
import com.parlor.games.mafia.domain.settings.MafiaSettingsError
import com.parlor.games.mafia.domain.settings.MafiaSettingsValidation
import com.parlor.games.mafia.resources.Res
import com.parlor.games.mafia.resources.role_civilian
import com.parlor.games.mafia.resources.role_detective
import com.parlor.games.mafia.resources.role_doctor
import com.parlor.games.mafia.resources.role_mafia
import com.parlor.games.mafia.resources.settings_error_duration_too_short_format
import com.parlor.games.mafia.resources.settings_error_detective_above_max_format
import com.parlor.games.mafia.resources.settings_error_detective_negative_format
import com.parlor.games.mafia.resources.settings_error_doctor_above_max_format
import com.parlor.games.mafia.resources.settings_error_doctor_negative_format
import com.parlor.games.mafia.resources.settings_error_mafia_below_one
import com.parlor.games.mafia.resources.settings_error_mafia_not_minority_format
import com.parlor.games.mafia.resources.settings_error_negative_max_revotes_format
import com.parlor.games.mafia.resources.settings_error_not_enough_civilians_format
import com.parlor.games.mafia.resources.settings_error_player_count_above_maximum_format
import com.parlor.games.mafia.resources.settings_error_player_count_below_minimum_format
import com.parlor.games.mafia.resources.settings_error_timers_not_supported
import com.parlor.games.mafia.resources.settings_player_count_format
import com.parlor.games.mafia.resources.settings_role_count_decrement_description_format
import com.parlor.games.mafia.resources.settings_role_count_decrement_label
import com.parlor.games.mafia.resources.settings_role_count_increment_description_format
import com.parlor.games.mafia.resources.settings_role_count_increment_label
import com.parlor.games.mafia.resources.settings_roles_card
import com.parlor.games.mafia.resources.settings_start
import com.parlor.games.mafia.resources.settings_start_description
import com.parlor.games.mafia.resources.settings_title
import com.parlor.games.mafia.resources.settings_toggle_allow_self_vote
import com.parlor.games.mafia.resources.settings_toggle_doctor_self_heal
import com.parlor.games.mafia.resources.settings_toggle_mafia_target_mafia
import com.parlor.games.mafia.resources.settings_toggle_reveal_role
import com.parlor.games.mafia.resources.settings_validation_card_title
import com.parlor.games.mafia.resources.setup_eyebrow
import org.jetbrains.compose.resources.stringResource

/**
 * Host-only setup screen. Role counts are editable via +/− steppers (Mafia,
 * Detective, Doctor); Civilians are derived from the remainder. Steppers only
 * guard the impossible cases (negative counts; Mafia below 1). Anything else
 * that makes [MafiaSettings.validate] return Invalid renders inline and
 * disables Start. Submitting fires [onStart] — the flow then dispatches
 * `ApplySettings` followed by `StartGame`, after which this screen no longer
 * renders (the phase moves off Setup), so the settings are structurally
 * locked.
 */
@Composable
fun MafiaSetupScreen(
    playerCount: Int,
    initialSettings: MafiaSettings,
    onStart: (MafiaSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    var mafiaCount by remember { mutableStateOf(initialSettings.roleCounts.mafia) }
    var detectiveCount by remember { mutableStateOf(initialSettings.roleCounts.detective) }
    var doctorCount by remember { mutableStateOf(initialSettings.roleCounts.doctor) }
    var revealRoleOnDeath by remember { mutableStateOf(initialSettings.revealRoleOnDeath) }
    var doctorCanSelfHeal by remember { mutableStateOf(initialSettings.doctorCanSelfHeal) }
    var allowSelfVote by remember { mutableStateOf(initialSettings.allowSelfVote) }
    var mafiaCanTargetMafia by remember { mutableStateOf(initialSettings.mafiaCanTargetMafia) }

    val settings = initialSettings.copy(
        roleCounts = MafiaRoleCounts(
            mafia = mafiaCount,
            detective = detectiveCount,
            doctor = doctorCount,
        ),
        revealRoleOnDeath = revealRoleOnDeath,
        doctorCanSelfHeal = doctorCanSelfHeal,
        allowSelfVote = allowSelfVote,
        mafiaCanTargetMafia = mafiaCanTargetMafia,
    )
    val validation = settings.validate(playerCount)
    val canStart = validation is MafiaSettingsValidation.Valid

    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(ParlorTheme.spacing.xl)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.l),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EyebrowLabel(text = stringResource(Res.string.setup_eyebrow), accent = false)
            Text(
                text = stringResource(Res.string.settings_title),
                style = ParlorTheme.typography.displayMedium,
                color = ParlorTheme.colors.textPrimary,
            )
            Text(
                text = stringResource(Res.string.settings_player_count_format, playerCount),
                style = ParlorTheme.typography.bodyLarge,
                color = ParlorTheme.colors.textSecondary,
            )

            RoleCountsEditorCard(
                playerCount = playerCount,
                roleCounts = settings.roleCounts,
                onMafiaChange = { mafiaCount = it },
                onDetectiveChange = { detectiveCount = it },
                onDoctorChange = { doctorCount = it },
            )

            if (validation is MafiaSettingsValidation.Invalid) {
                ValidationMessagesCard(errors = validation.errors)
            }

            ToggleRow(
                label = stringResource(Res.string.settings_toggle_reveal_role),
                checked = revealRoleOnDeath,
                onCheckedChange = { revealRoleOnDeath = it },
            )
            ToggleRow(
                label = stringResource(Res.string.settings_toggle_doctor_self_heal),
                checked = doctorCanSelfHeal,
                onCheckedChange = { doctorCanSelfHeal = it },
            )
            ToggleRow(
                label = stringResource(Res.string.settings_toggle_allow_self_vote),
                checked = allowSelfVote,
                onCheckedChange = { allowSelfVote = it },
            )
            ToggleRow(
                label = stringResource(Res.string.settings_toggle_mafia_target_mafia),
                checked = mafiaCanTargetMafia,
                onCheckedChange = { mafiaCanTargetMafia = it },
            )

            ParlorButton(
                label = stringResource(Res.string.settings_start),
                contentDescription = stringResource(Res.string.settings_start_description),
                onClick = { onStart(settings) },
                enabled = canStart,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun RoleCountsEditorCard(
    playerCount: Int,
    roleCounts: MafiaRoleCounts,
    onMafiaChange: (Int) -> Unit,
    onDetectiveChange: (Int) -> Unit,
    onDoctorChange: (Int) -> Unit,
) {
    val mafiaLabel = stringResource(Res.string.role_mafia)
    val detectiveLabel = stringResource(Res.string.role_detective)
    val doctorLabel = stringResource(Res.string.role_doctor)
    val civilianLabel = stringResource(Res.string.role_civilian)

    // Block only impossibility: counts below their floor, or any increment
    // that would force civilians negative. Everything else (e.g. Mafia
    // majority, civilians == 0) is allowed in the UI and surfaced via the
    // validation card + disabled Start.
    val assigned = roleCounts.mafia + roleCounts.detective + roleCounts.doctor
    val canIncrement = assigned < playerCount

    ParlorCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s),
        ) {
            EyebrowLabel(text = stringResource(Res.string.settings_roles_card), accent = false)
            RoleCountStepperRow(
                name = mafiaLabel,
                count = roleCounts.mafia,
                onDecrement = { onMafiaChange(roleCounts.mafia - 1) },
                onIncrement = { onMafiaChange(roleCounts.mafia + 1) },
                decEnabled = roleCounts.mafia > 1,
                incEnabled = canIncrement,
            )
            RoleCountStepperRow(
                name = detectiveLabel,
                count = roleCounts.detective,
                onDecrement = { onDetectiveChange(roleCounts.detective - 1) },
                onIncrement = { onDetectiveChange(roleCounts.detective + 1) },
                decEnabled = roleCounts.detective > 0,
                incEnabled = canIncrement && roleCounts.detective < MafiaSettings.MAX_DETECTIVES,
            )
            RoleCountStepperRow(
                name = doctorLabel,
                count = roleCounts.doctor,
                onDecrement = { onDoctorChange(roleCounts.doctor - 1) },
                onIncrement = { onDoctorChange(roleCounts.doctor + 1) },
                decEnabled = roleCounts.doctor > 0,
                incEnabled = canIncrement && roleCounts.doctor < MafiaSettings.MAX_DOCTORS,
            )
            RoleCountReadOnlyRow(
                name = civilianLabel,
                count = roleCounts.civilians(playerCount),
            )
        }
    }
}

@Composable
private fun RoleCountStepperRow(
    name: String,
    count: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    decEnabled: Boolean,
    incEnabled: Boolean,
) {
    val decLabel = stringResource(Res.string.settings_role_count_decrement_label)
    val incLabel = stringResource(Res.string.settings_role_count_increment_label)
    val decDesc = stringResource(Res.string.settings_role_count_decrement_description_format, name)
    val incDesc = stringResource(Res.string.settings_role_count_increment_description_format, name)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = ParlorTheme.typography.bodyLarge,
            color = ParlorTheme.colors.textPrimary,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ParlorButton(
                label = decLabel,
                contentDescription = decDesc,
                onClick = onDecrement,
                enabled = decEnabled,
                variant = ParlorButtonVariant.Ghost,
            )
            Text(
                text = count.toString(),
                style = ParlorTheme.typography.bodyLarge,
                color = ParlorTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 32.dp),
            )
            ParlorButton(
                label = incLabel,
                contentDescription = incDesc,
                onClick = onIncrement,
                enabled = incEnabled,
                variant = ParlorButtonVariant.Ghost,
            )
        }
    }
}

@Composable
private fun RoleCountReadOnlyRow(name: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = ParlorTheme.typography.bodyLarge,
            color = ParlorTheme.colors.textPrimary,
        )
        Text(
            text = count.toString(),
            style = ParlorTheme.typography.bodyLarge,
            color = ParlorTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun ValidationMessagesCard(errors: List<MafiaSettingsError>) {
    ParlorCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s),
        ) {
            EyebrowLabel(text = stringResource(Res.string.settings_validation_card_title), accent = false)
            errors.forEach { error ->
                Text(
                    text = validationErrorText(error),
                    style = ParlorTheme.typography.bodyMedium,
                    color = ParlorTheme.colors.semanticDanger,
                )
            }
        }
    }
}

@Composable
private fun validationErrorText(error: MafiaSettingsError): String = when (error) {
    is MafiaSettingsError.PlayerCountBelowMinimum -> stringResource(
        Res.string.settings_error_player_count_below_minimum_format,
        MafiaSettings.MIN_PLAYERS,
        error.playerCount,
    )
    is MafiaSettingsError.PlayerCountAboveMaximum -> stringResource(
        Res.string.settings_error_player_count_above_maximum_format,
        MafiaSettings.MAX_PLAYERS,
        error.playerCount,
    )
    MafiaSettingsError.MafiaCountBelowOne -> stringResource(Res.string.settings_error_mafia_below_one)
    is MafiaSettingsError.NegativeDetectiveCount -> stringResource(
        Res.string.settings_error_detective_negative_format,
        error.count,
    )
    is MafiaSettingsError.NegativeDoctorCount -> stringResource(
        Res.string.settings_error_doctor_negative_format,
        error.count,
    )
    is MafiaSettingsError.TooManyDetectives -> stringResource(
        Res.string.settings_error_detective_above_max_format,
        MafiaSettings.MAX_DETECTIVES,
        error.count,
    )
    is MafiaSettingsError.TooManyDoctors -> stringResource(
        Res.string.settings_error_doctor_above_max_format,
        MafiaSettings.MAX_DOCTORS,
        error.count,
    )
    is MafiaSettingsError.NotEnoughCivilians -> stringResource(
        Res.string.settings_error_not_enough_civilians_format,
        error.computed,
    )
    is MafiaSettingsError.MafiaNotMinority -> stringResource(
        Res.string.settings_error_mafia_not_minority_format,
        error.mafiaCount,
        error.playerCount,
    )
    is MafiaSettingsError.NegativeMaxRevotes -> stringResource(
        Res.string.settings_error_negative_max_revotes_format,
        error.value,
    )
    MafiaSettingsError.TimersNotSupported ->
        stringResource(Res.string.settings_error_timers_not_supported)
    is MafiaSettingsError.DurationTooShort -> stringResource(
        Res.string.settings_error_duration_too_short_format,
        error.kind,
        error.seconds,
    )
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = ParlorTheme.typography.bodyLarge,
            color = ParlorTheme.colors.textPrimary,
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ParlorTheme.colors.accentEmber,
                checkedTrackColor = ParlorTheme.colors.accentEmber.copy(alpha = 0.4f),
                uncheckedThumbColor = ParlorTheme.colors.textSecondary,
                uncheckedTrackColor = ParlorTheme.colors.surfaceElevated,
            ),
        )
    }
}
