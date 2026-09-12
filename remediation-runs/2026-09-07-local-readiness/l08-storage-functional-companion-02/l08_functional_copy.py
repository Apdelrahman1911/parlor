"""Namespaced functional observations in one owned copy; original strict L08 stays intact."""
from pathlib import Path

import l08_copy as strict

L08_ADDITIONAL_MODIFIED = strict.L08_ADDITIONAL_MODIFIED
L08_ADDITIONS = dict(strict.L08_ADDITIONS)
OLD_PROBE = 'composeApp/src/iosMain/kotlin/com/parlor/app/readiness/L08StorageProbe.kt'
NEW_PROBE = 'composeApp/src/iosMain/kotlin/com/parlor/app/readiness/L08StorageFunctionalProbe.kt'
if L08_ADDITIONS.pop(OLD_PROBE) != 'L08StorageProbe.kt.in':
    raise RuntimeError('Original storage observer registration drifted')
L08_ADDITIONS[NEW_PROBE] = 'L08StorageFunctionalProbe.kt.in'

OLD_TEST = 'testDSC01ActualSettingsLocalSessionsAndOSInvestigation'
NEW_TEST = 'testDSC01ActualSettingsLocalSessionsAndOSWithL08FunctionalObservation'


def instrument_l08_kotlin(copy_root):
    # The original transform enforces exact paths and rejects double application.
    strict.instrument_l08_kotlin(copy_root)
    path = Path(copy_root).absolute() / strict.MAIN
    text = strict.once(path.read_text(), '''fun L08StorageCommand(action: String, onJson: (String) -> Unit) {
    com.parlor.app.readiness.L08StorageProbe.command(action, onJson)
}''', '''fun L08StorageFunctionalCommand(action: String, onJson: (String) -> Unit) {
    com.parlor.app.readiness.L08StorageFunctionalProbe.command(action, onJson)
}''')
    path.write_text(text)
    # A metadata-only tag on the actual scroll container, never a substitute
    # Home, programmatic scroll, changed ordering, or callback invocation.
    home = Path(copy_root).absolute() / strict.HOME
    home.write_text(strict.once(home.read_text(), '''        LazyColumn(
            modifier = Modifier.fillMaxSize(),''', '''        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("l08-home-list"),'''))


def instrument_probe_swift(text):
    text = strict.instrument_probe_swift(text)
    text = strict.once(text, '"readiness", "l08-storage", "l08-host"',
                       '"readiness", "l08-storage-functional", "l08-host"')
    return strict.once(text, 'if probe.isL08StorageScenario { L08StorageControls(probe: probe) }',
                       'if probe.isL08StorageFunctionalScenario { L08StorageFunctionalControls(probe: probe) }')


def instrument_ui_test(text):
    text = strict.instrument_ui_test(text)
    text = strict.once(text, 'try verifyL08RealStoreResume(app)', 'try verifyL08FunctionalStoreResume(app)')
    # Fail fast at retained-host boundaries, but keep native readiness first:
    # its boot-1 owned cleanup restores the real System preference baseline.
    # The complete matrix and every original check still execute on success.
    text = strict.once(text, '        try verifyActualNativeReadiness(app)\n'
                       '        try verifyL08FunctionalStoreResume(app)\n'
                       '        try verifyL08StartedHosts(app)',
                       '        try verifyActualNativeReadiness(app)\n'
                       '        try verifyL08StartedHosts(app)\n'
                       '        try verifyL08FunctionalStoreResume(app)')
    return strict.once(text, 'func ' + OLD_TEST + '()', 'func ' + NEW_TEST + '()')
