"""Post-catalog failure custody with no added Compose pointer modifiers.

PIN30 reproduced a lost original Word Impostor opening, not causality. This
explicit finite arm removes only added pointer observers, keeps callbacks and
all native companions, and fills the missing catalog failed-query boundary.
No shipping change, gesture repair, replay or qualification is performed.
"""
import json
import re

import frozen_sources
import pin01_copy
import pin02_capture
import pin03_catalog_copy as catalog
import pin30_public_session_stress as stress

observed = stress.observed
SOURCE, TREE = stress.SOURCE, stress.TREE
PROFILE = 'catalog-input-without-compose-pointer-observers'
ARMS = ('compose',)
TEST_TIMEOUT_SECONDS = stress.TEST_TIMEOUT_SECONDS
RESULT = 'CATALOG_INPUT_BOUNDARY_COMPLETE_NOT_QUALIFICATION'
SCOPE = ('Five predeclared EN/AR pairs once, original actions/deadlines; omit only added '
         'Compose pointer observers, retain callbacks/native/root/frame/motion/input/lifecycle '
         'and failure-only queries; no gesture ledger, timing neutrality, causality or qualification')
CASES, TESTS = stress.CASES, stress.TESTS
PROPERTY = '    @MainActor private var pin31CatalogControl: XCUIElement?\n'
REMOVALS = {
    pin01_copy.PICKER: (
        'import com.parlor.app.inputcopy.pin01PublicInput\n',
        '.pin01PublicInput(1)', '.pin01PublicInput(2)',
    ),
    catalog.HOME: (
        'import com.parlor.app.inputcopy.pin01PublicInput\n',
        '                .pin01PublicInput(3)\n',
    ),
}
REFERENCE = '''                if pin11PublicApp === app && ["Open Egyptian Dominoes", "Open Ghamza", "Open Word Impostor",
                    "افتح الدومنة المصرية", "افتح غمزة", "افتح لعبة الإمبوستر"].contains(prefix) {
                    pin31CatalogControl = button // The exact original tap reference; no new query.
                }
'''
OLD_CATALOG = '''            let catalogOpened = setupButton(app, prefix: copy.host).waitForExistence(timeout: 10)
            if !catalogOpened { pin11CapturePublicFailureOnce(app) }
            XCTAssertTrue(catalogOpened)'''
NEW_CATALOG = '''            let catalogOpened = setupButton(app, prefix: copy.host).waitForExistence(timeout: 10)
            if !catalogOpened {
                let screenshot = XCTAttachment(screenshot: app.screenshot())
                screenshot.name = "Failed public catalog opening after original single tap"
                screenshot.lifetime = .keepAlways
                add(screenshot)
                pin11CapturePublicFailureOnce(app) // Freeze native rows before either new query.
                if let source = pin31CatalogControl {
                    PIN29EquivalentQuery.capture(source, equivalent: self.setupButton(app, prefix: game),
                                                 app: app, kind: 3)
                }
            }
            XCTAssertTrue(catalogOpened)'''


def extend_driver(driver):
    once = pin01_copy.once
    driver = once(driver, '    @MainActor private var pin11PublicApp: XCUIApplication?\n',
                  '    @MainActor private var pin11PublicApp: XCUIApplication?\n'+PROPERTY)
    driver = once(driver, '            pin11PublicApp = app\n',
                  '            pin11PublicApp = app\n            pin31CatalogControl = nil\n')
    driver = once(driver, '                try tapSettledControl(button, pin06FailureOwner:\n',
                  REFERENCE+'                try tapSettledControl(button, pin06FailureOwner:\n')
    return once(driver, OLD_CATALOG, NEW_CATALOG)


def restore_driver(driver):
    once = pin01_copy.once
    driver = once(driver, NEW_CATALOG, OLD_CATALOG)
    driver = once(driver, REFERENCE, '')
    driver = once(driver, '            pin11PublicApp = app\n            pin31CatalogControl = nil\n',
                  '            pin11PublicApp = app\n')
    return once(driver, PROPERTY, '')


def renderer_for_profile(profile, source, runtime, device):
    pair = observed.original.motion.pair
    observed.original.text.require((profile, source, runtime, device) ==
        (PROFILE, SOURCE, pair.RUNTIME, pair.DEVICE), 'Exact catalog boundary source/profile/device required')
    base = stress.renderer_for_profile(stress.PROFILE, source, runtime, device)
    def render(sources, digests, *, arm):
        observed.original.text.require(arm == 'compose', 'Only declared no-pointer observation arm')
        plan = base(sources, digests, arm=arm)
        for path, removals in REMOVALS.items():
            for removal in removals:
                plan[path] = pin01_copy.once(plan[path], removal, '')
        plan[pin02_capture.DRIVER] = extend_driver(plan[pin02_capture.DRIVER])
        return plan
    return render


test_command = stress.test_command
verify_results = stress.verify_results
failed_method_exports = stress.failed_method_exports
retain_companion = observed.retain_companion
retain_root_companion = observed.retain_root_companion
retain_motion_companion = observed.retain_motion_companion
verify_commands = stress.verify_commands


def accept_native_export(out, state):
    req = observed.original.text.require
    pair = observed.original.motion.pair
    req(state['original_inputs'].get(observed.original.text.NAME) == observed.original.text.NAME_SHA256,
        'Exact original Name source, no input substitution')
    renderer = renderer_for_profile(PROFILE, SOURCE, pair.RUNTIME, pair.DEVICE)
    observed.original.motion.boundary.accept_observation_export(out, state, profile=PROFILE,
        renderer=renderer, result_verifier=verify_results, command_verifier=verify_commands)
    observed.original.verify_companion_files(out, state)
    observed.verify_failed_queries(out, state)
    dest = out/'compose'
    for path in dest.glob('capture-*.json'):
        value = json.loads(frozen_sources._read(path, pin02_capture.MAX_BYTES))
        req(not any(row[1] in (1, 2) for row in value['snapshot']['rows']),
            'No invented native or Compose gesture observations in the no-pointer arm')
    log = frozen_sources._read(dest/'test.log', 4*1024*1024)
    starts = re.findall(rb"Test Case '-\[iosAppUITests\.([A-Za-z0-9_]+) ([A-Za-z0-9_]+)\]' started\.", log)
    req(tuple(c.decode()+'/'+m.decode()+'()' for c,m in starts) == CASES,
        'Every fixed original sample executes once, in the original order')
    row = state['arms'][0]
    receipt = dict(methods=row['methods'], planned_pairs=5, maximum_public_flows=60,
        actual_completed_flows=60 if row['test_exit'] == 0 else None,
        compose_pointer_observers=False, native_touch_observer=False,
        all_failed_samples_retained=True, application_root_cause=False,
        shipping_qualified=False, native_timing_neutrality=False, physical_verified=False)
    target = out/'catalog-input-observations.json'
    if target.exists():
        req(json.loads(target.read_bytes()) == receipt, 'Exact original saved boundary receipt')
    else:
        with target.open('x') as output: json.dump(receipt, output, indent=2)
    return RESULT
