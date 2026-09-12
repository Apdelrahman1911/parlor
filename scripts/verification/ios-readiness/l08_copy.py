"""Additive, closed L08 observations of owned source copies, never repository writes."""
from pathlib import Path

WD = 'game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/ui/flow/WhodunitGameFlow.kt'
MF = 'game-modes/mafia/src/commonMain/kotlin/com/parlor/games/mafia/ui/flow/passandplay/MafiaGameFlow.kt'
MAIN = 'composeApp/src/iosMain/kotlin/com/parlor/app/MainViewController.kt'
APP = 'composeApp/src/commonMain/kotlin/com/parlor/app/App.kt'
HOME = 'composeApp/src/commonMain/kotlin/com/parlor/app/shell/home/HomeScreen.kt'
RECOVERY = 'composeApp/src/commonMain/kotlin/com/parlor/app/shell/home/LocalResumeFailureScreen.kt'
MAFIA_SETUP = 'game-modes/mafia/src/commonMain/kotlin/com/parlor/games/mafia/ui/screens/setup/MafiaSetupScreen.kt'
L08_ADDITIONAL_MODIFIED = (APP, HOME, RECOVERY, MAFIA_SETUP)
L08_MODIFIED = (WD, MF, MAIN, *L08_ADDITIONAL_MODIFIED)
L08_ADDITIONS = {
    'game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/readiness/L08WhodunitSnapshots.kt': 'L08WhodunitSnapshots.kt.in',
    'game-modes/mafia/src/commonMain/kotlin/com/parlor/games/mafia/readiness/L08MafiaSnapshots.kt': 'L08MafiaSnapshots.kt.in',
    'game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/readiness/L08WhodunitHost.kt': 'L08WhodunitHost.kt.in',
    'game-modes/mafia/src/commonMain/kotlin/com/parlor/games/mafia/readiness/L08MafiaHost.kt': 'L08MafiaHost.kt.in',
    'composeApp/src/commonMain/kotlin/com/parlor/app/readiness/L08UiSeam.kt': 'L08UiSeam.kt.in',
    'composeApp/src/iosMain/kotlin/com/parlor/app/readiness/L08StorageProbe.kt': 'L08StorageProbe.kt.in',
    'composeApp/src/iosMain/kotlin/com/parlor/app/readiness/L08HostProbe.kt': 'L08HostProbe.kt.in',
    'composeApp/src/iosMain/kotlin/com/parlor/networking/testing/ControlledStartRoom.kt': 'ControlledStartRoom.kt.in',
    'composeApp/src/iosMain/kotlin/com/parlor/networking/testing/InMemoryRoomBus.kt': 'InMemoryRoomBus.kt.in',
}
VERBATIM_FIXTURES = {
    'ControlledStartRoom.kt.in': 'shared/networking-testing/src/commonMain/kotlin/com/parlor/networking/testing/ControlledStartRoom.kt',
    'InMemoryRoomBus.kt.in': 'shared/networking-testing/src/commonMain/kotlin/com/parlor/networking/testing/InMemoryRoomBus.kt',
}


def once(text, old, new):
    if text.count(old) != 1:
        raise RuntimeError('L08 reviewed copy-only source anchor is missing or ambiguous')
    return text.replace(old, new)


def transform(path, text):
    if path not in L08_MODIFIED:
        raise RuntimeError('Unregistered L08 source transformation')
    if '.readiness.L08' in text:
        raise RuntimeError('L08 instrumentation cannot be applied twice or over an existing observer')
    if path in (WD, MF):
        name = 'Whodunit' if path == WD else 'Mafia'
        game = name.lower()
        anchor = ('    val canonicalState = requireNotNull(session.canonicalState) {\n'
                  '        "The local ' + name + ' flow requires an authoritative controller"\n    }')
        return once(text, anchor, anchor + '\n    com.parlor.games.' + game + '.readiness.L08Observe' +
                    name + 'Resume(restoredSessionId, session)')
    if path == MAIN:
        text = once(text, '    startKoin { modules(allModules) }',
                    '    startKoin { modules(allModules + com.parlor.app.readiness.L08HostProbe.extraModules()) }')
        return text + '''

// COPY-ONLY app-host controls; production Main/Notify methods remain in place.
fun L08StorageCommand(action: String, onJson: (String) -> Unit) {
    com.parlor.app.readiness.L08StorageProbe.command(action, onJson)
}
fun L08HostCommand(action: String, onJson: (String) -> Unit) {
    com.parlor.app.readiness.L08HostProbe.command(action, onJson)
}
fun L08UiObservation(): String = kotlinx.serialization.json.buildJsonObject {
    val observed = com.parlor.app.readiness.L08UiSeam
    put("resume_taps", kotlinx.serialization.json.JsonPrimitive(observed.resumeTaps))
    put("retry_taps", kotlinx.serialization.json.JsonPrimitive(observed.retryTaps))
    put("discard_taps", kotlinx.serialization.json.JsonPrimitive(observed.discardTaps))
    put("recovery_shows", kotlinx.serialization.json.JsonPrimitive(observed.recoveryShows))
}.toString()
'''
    if path == APP:
        text = once(text, '            CompositionLocalProvider(LocalParlorToastState provides toastState) {',
                    '            CompositionLocalProvider(LocalParlorToastState provides toastState) {\n'
                    '                if (com.parlor.app.readiness.L08UiSeam.renderHostIfActive()) return@CompositionLocalProvider')
        return once(text, '                                    navigator.showLocalResumeFailure(destination.sessionId)',
                    '                                    navigator.showLocalResumeFailure(destination.sessionId)\n'
                    '                                    com.parlor.app.readiness.L08UiSeam.recoveryShown(destination.sessionId)')
    if path == HOME:
        text = once(text, '        onTap = { onResume(entry.sessionId) },',
                    '        onTap = {\n            com.parlor.app.readiness.L08UiSeam.homeTapped(entry.sessionId)\n'
                    '            onResume(entry.sessionId)\n        },\n'
                    '        modifier = com.parlor.app.readiness.L08UiSeam.homeModifier(entry.sessionId),')
        text = once(text, '''    onTap: () -> Unit,
) {
    ParlorCard(
        modifier = Modifier''', '''    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ParlorCard(
        modifier = modifier''')
        return text
    if path == RECOVERY:
        text = once(text, 'import androidx.compose.ui.Modifier\n',
                    'import androidx.compose.ui.Modifier\nimport androidx.compose.ui.platform.testTag\n')
        text = once(text, '                onClick = onRetry,\n                modifier = Modifier.fillMaxWidth(),',
                    '                onClick = { com.parlor.app.readiness.L08UiSeam.retryTapped(); onRetry() },\n'
                    '                modifier = Modifier.fillMaxWidth().testTag("l08-recovery-retry"),')
        return once(text, '''                onClick = onDiscard,
                variant = ParlorButtonVariant.Ghost,
                modifier = Modifier.fillMaxWidth(),''', '''                onClick = { com.parlor.app.readiness.L08UiSeam.discardTapped(); onDiscard() },
                variant = ParlorButtonVariant.Ghost,
                modifier = Modifier.fillMaxWidth().testTag("l08-recovery-discard"),''')
    if path == MAFIA_SETUP:
        text = once(text, 'import androidx.compose.ui.Modifier\n',
                    'import androidx.compose.ui.Modifier\nimport androidx.compose.ui.platform.testTag\n')
        text = once(text, '    val canStart = validation is MafiaSettingsValidation.Valid',
                    '    val canStart = validation is MafiaSettingsValidation.Valid\n'
                    '    com.parlor.games.mafia.readiness.L08MafiaSetupObservation.observeDraft(settings)')
        text = once(text, '''                checked = draft.doctorCanProtectSamePlayerConsecutively,
                onCheckedChange = {
                    draft = draft.copy(doctorCanProtectSamePlayerConsecutively = it)
                },''', '''                checked = draft.doctorCanProtectSamePlayerConsecutively,
                onCheckedChange = {
                    com.parlor.games.mafia.readiness.L08MafiaSetupObservation.repeatChanged(it)
                    draft = draft.copy(doctorCanProtectSamePlayerConsecutively = it)
                },
                modifier = Modifier.testTag("l08-mafia-repeat"),''')
        text = once(text, '''                onClick = { onStart(settings) },
                enabled = canStart,
                modifier = Modifier.fillMaxWidth(),''', '''                onClick = {
                    com.parlor.games.mafia.readiness.L08MafiaSetupObservation.start(settings)
                    onStart(settings)
                },
                enabled = canStart,
                modifier = Modifier.fillMaxWidth().testTag("l08-mafia-start"),''')
        return once(text, '''    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier''', '''    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier''')
    raise RuntimeError('Unregistered L08 source transformation')


def instrument_l08_kotlin(copy_root):
    root = Path(copy_root).absolute()
    if root.is_symlink() or root.resolve() != root:
        raise RuntimeError('L08 instrumentation requires an exact owned source-copy root')
    for path in L08_MODIFIED:
        target = root / path
        if not target.is_file() or target.is_symlink() or target.resolve() != target:
            raise RuntimeError('Missing/escaping L08 owned-copy input')
        target.write_text(transform(path, target.read_text()))


def instrument_probe_swift(text):
    text = once(text, '    @Published private(set) var nativeReadinessDisplay = "pending"',
        '''    @Published private(set) var nativeReadinessDisplay = "pending"
    @Published private(set) var l08Display = "pending"
    @Published private(set) var l08UiDisplay = "{}"
    @Published private(set) var l08HostDisplay = "pending"
    private var l08HostIndex = 0''')
    text = once(text, '["settings", "whodunit", "mafia", "os", "readiness"].contains(requestedScenario)',
                '["settings", "whodunit", "mafia", "os", "readiness", "l08-storage", "l08-host"].contains(requestedScenario)')
    return once(text, '            if probe.isReadinessScenario {',
                '''            if probe.isL08StorageScenario { L08StorageControls(probe: probe) }
            if probe.isL08HostScenario { L08HostControls(probe: probe) }
            if probe.isReadinessScenario {''')


def instrument_ui_test(text):
    if 'try verifyL08' in text:
        raise RuntimeError('L08 XCTest extension already present')
    return once(text, '        try verifyActualNativeReadiness(app)',
                '        try verifyActualNativeReadiness(app)\n'
                '        try verifyL08RealStoreResume(app)\n'
                '        try verifyL08StartedHosts(app)')
