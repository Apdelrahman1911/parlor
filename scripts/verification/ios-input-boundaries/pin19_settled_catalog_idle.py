"""PIN18 cleared a root but started absent: require the original positive boundary.

This distinct synthetic control first observes an already-settled Word Impostor
catalog card with the exact original readiness algorithm, WITHOUT its final tap.
Only then introduce the declared idle stimulus. Do not slow/replay shipping tests
or infer that a root nil setter identifies a timer, root instance or original bug.
"""
import json

import pin01_copy
import pin02_capture
import pin06_public_setup_pair as pair
import pin08_accessibility as native
import pin18_root_idle_stimulus as idle

SOURCE, TREE = idle.SOURCE, idle.TREE
PROFILE = 'synthetic-settled-catalog-idle'
ARMS = idle.ARMS
METHOD = 'IOSAppLaunchUITests/testPIN19SettledWordCatalogIdleCapture()'
RESULT = 'SETTLED_CATALOG_IDLE_STIMULUS_COMPLETE_NOT_QUALIFICATION'
SCOPE = ('One positive-baseline Word Impostor catalog idle stimulus; original readiness '
         'without its final tap, then original single opening/Host taps and forced failure; '
         'not natural reproduction, source fix, root identity/timer causality or qualification')


def readiness_helpers(driver):
    first = driver.index('    @MainActor\n    private func tapSetupButton(')
    last = driver.index('    @MainActor\n    private func tapPublicSetupTransition(', first)
    reach = driver[first:last].replace('tapSetupButton(', 'pin19ObserveSettledSetupButton(')
    # Match the callee, not its argument spelling. PIN06 adds a failure-owner
    # argument, so matching `tapSettledControl(button)` left the original tapping
    # callee alive in ee7773d1. This must be the sole call in the cloned reach path.
    reach = pin01_copy.once(reach, 'tapSettledControl(', 'pin19ObserveSettledControl(')
    first = driver.index('    @MainActor\n    private func tapSettledControl(')
    last = driver.index('    @MainActor\n    private func verifyKeyboardCycle(', first)
    settle = driver[first:last].replace('tapSettledControl(', 'pin19ObserveSettledControl(')
    settle = pin01_copy.once(settle, '        control.tap()\n', '')
    return reach + settle


def renderer_for_profile(profile, source, runtime, device):
    native.require((profile, source, runtime, device) == (PROFILE, SOURCE, pair.RUNTIME, pair.DEVICE),
                   'Exact positive-baseline synthetic catalog profile required')
    base = idle.renderer_for_profile(idle.PROFILE, SOURCE, runtime, device)
    def render(sources, digests, *, arm):
        plan = base(sources, digests, arm=arm)
        driver = plan[pin02_capture.DRIVER]
        driver = pin01_copy.once(driver, idle.METHOD.split('/')[1][:-2], METHOD.split('/')[1][:-2])
        driver = pin01_copy.once(driver, '        try tapSetupButton(app, prefix: "Open Ghamza")\n'+idle.STIMULUS,
            '        try pin19ObserveSettledSetupButton(app, prefix: "Open Word Impostor")\n'+
            idle.STIMULUS.replace('let source = setupButton(app, prefix: "Open a room and show a code your friends can join.")',
                                 'let source = setupButton(app, prefix: "Open Word Impostor")')+
            '        try tapSetupButton(app, prefix: "Open Word Impostor")\n')
        driver = pin01_copy.once(driver, '    @MainActor\n    private func verifyKeyboardCycle(',
            readiness_helpers(driver)+'    @MainActor\n    private func verifyKeyboardCycle(')
        return plan | {pin02_capture.DRIVER: driver}
    return render


def test_command(base, result):
    return [*base, 'test-without-building', '-resultBundlePath', str(result),
            '-only-testing:iosAppUITests/'+METHOD.removesuffix('()')]


def verify_results(summary, tests, simulator_id, code):
    return native.verify_results(summary, tests, simulator_id, code, method=METHOD)


def failed_method_exports(result, destination, outcomes):
    native.require(outcomes == {METHOD: 'Failed'}, 'One exact forced control failure required')
    directory = destination/'failed-01'
    return [(directory, ['export','attachments','--path',str(result),'--output-path',
                         str(directory),'--test-id',METHOD])]


retain_companion = idle.retain_companion
retain_root_companion = idle.retain_root_companion


def accept_native_export(out, state):
    idle.accept_stimulus_export(out, state, profile=PROFILE,
        renderer=renderer_for_profile(PROFILE,SOURCE,pair.RUNTIME,pair.DEVICE), method=METHOD)
    report = json.loads((out/'root-idle-stimulus.json').read_bytes())
    first = report['samples'][0]
    native.require(first[2:4] == [1,1] and first[6] > 0 and first[7] > 0,
                   'Actual enabled/nonzero positive baseline required, not PIN18 absent-to-present substitution')
    target = out/'settled-catalog-idle.json'
    record = dict(result=RESULT, stimulus=report, original_root_cause=False, shipping_qualified=False,
                  physical_verified=False, scope=SCOPE)
    if target.exists():
        native.require(json.loads(target.read_bytes()) == record, 'Exact original settled-catalog report required')
    else:
        with target.open('x') as f: json.dump(record,f,indent=2)
    return RESULT
