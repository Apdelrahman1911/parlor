package com.parlor.games.mafia.ui.screens.night

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.parlor.core.ids.PlayerId
import com.parlor.games.mafia.resources.Res
import com.parlor.games.mafia.resources.night_civilian_eyebrow_format
import com.parlor.games.mafia.resources.night_civilian_headline
import com.parlor.games.mafia.resources.night_civilian_instructions
import com.parlor.games.mafia.resources.night_civilian_skip
import com.parlor.games.mafia.resources.night_civilian_submit
import org.jetbrains.compose.resources.stringResource

@Composable
fun CivilianSuspectScreen(
    civilianName: String,
    targets: List<PickableTarget>,
    onSubmit: (PlayerId?) -> Unit,
    modifier: Modifier = Modifier,
) {
    TargetPickerScreen(
        eyebrow = stringResource(Res.string.night_civilian_eyebrow_format, civilianName),
        headline = stringResource(Res.string.night_civilian_headline),
        instructions = stringResource(Res.string.night_civilian_instructions),
        targets = targets,
        submitLabel = stringResource(Res.string.night_civilian_submit),
        onSubmit = onSubmit,
        allowSkip = true,
        skipLabel = stringResource(Res.string.night_civilian_skip),
        modifier = modifier,
    )
}
