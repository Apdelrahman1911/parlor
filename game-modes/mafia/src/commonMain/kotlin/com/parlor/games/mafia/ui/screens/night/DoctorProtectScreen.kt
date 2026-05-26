package com.parlor.games.mafia.ui.screens.night

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.parlor.core.ids.PlayerId
import com.parlor.games.mafia.resources.Res
import com.parlor.games.mafia.resources.night_doctor_eyebrow_format
import com.parlor.games.mafia.resources.night_doctor_headline
import com.parlor.games.mafia.resources.night_doctor_instructions
import com.parlor.games.mafia.resources.night_doctor_skip
import com.parlor.games.mafia.resources.night_doctor_submit
import org.jetbrains.compose.resources.stringResource

@Composable
fun DoctorProtectScreen(
    doctorName: String,
    targets: List<PickableTarget>,
    onSubmit: (PlayerId?) -> Unit,
    modifier: Modifier = Modifier,
) {
    TargetPickerScreen(
        eyebrow = stringResource(Res.string.night_doctor_eyebrow_format, doctorName),
        headline = stringResource(Res.string.night_doctor_headline),
        instructions = stringResource(Res.string.night_doctor_instructions),
        targets = targets,
        submitLabel = stringResource(Res.string.night_doctor_submit),
        onSubmit = onSubmit,
        allowSkip = true,
        skipLabel = stringResource(Res.string.night_doctor_skip),
        modifier = modifier,
    )
}
