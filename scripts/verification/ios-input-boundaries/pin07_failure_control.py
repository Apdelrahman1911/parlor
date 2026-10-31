"""Pure plans for one synthetic failed-export control; never shipping input.

The completed PIN06 experiment produced no failed journey and therefore never
exercised its failed-only export. This distinct plan intentionally suppresses a
copied Host transition, AFTER recording its callback. It does not repair or
reexecute the original failed app suite and has no native execution adapter.
"""
import hashlib

import pin01_copy
import pin02_capture
import pin03_catalog_copy as catalog
import pin06_public_setup_pair as pair


PROFILE = 'synthetic-suppressed-host-transition'
METHOD = 'IOSAppLaunchUITests/testPIN07SyntheticHostFailureCapture()'
HOST_CALLBACK = (
    'onHost = { com.parlor.app.inputcopy.PIN01Observation.callback(1); '
    'step = ShellStep.HostPermission }'
)
SUPPRESSED_CALLBACK = (
    'onHost = { com.parlor.app.inputcopy.PIN01Observation.callback(1) '
    '/* PIN07: synthetic copied-app transition suppression only. */ }'
)
DRIVER_ANCHOR = '    @MainActor\n    func testColdLaunchRendersComposeHomeWithoutUnexpectedAlert() throws {'
CONTROL_METHOD = '''    // COPY ONLY: this method must FAIL at the original transition assertion.
    // A collection reader may accept that known counterexample, never app CI.
    @MainActor
    func testPIN07SyntheticHostFailureCapture() throws {
        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        PIN02FailureRequest.configure(app)
        app.launch()
        defer { app.terminate() }
        XCTAssertTrue(app.staticTexts["parlor-home-brand"].waitForExistence(timeout: 30))
        try tapSetupButton(app, prefix: "Open Ghamza")
        try tapPublicSetupTransition(app,
            from: "Open a room and show a code your friends can join.",
            to: "Continue and retry the real local-network operation.")
    }

'''


def render(sources, digests, *, profile):
    """Return only an explicitly selected, hash-bound synthetic source-copy plan.

    All ordinary driver methods and observed source changes remain byte-exact
    relative to the PIN06 native plan. Only this added method may be selected by
    a future reviewed runner. No original/production arm can select the fault.
    """
    if type(profile) is not str or profile != PROFILE:
        raise ValueError('Explicit synthetic failed-export profile required')
    output = pair.render(sources, digests, arm='native')
    output[pin01_copy.SHELL] = pin01_copy.once(
        output[pin01_copy.SHELL], HOST_CALLBACK, SUPPRESSED_CALLBACK)
    output[pin02_capture.DRIVER] = pin01_copy.once(
        output[pin02_capture.DRIVER], DRIVER_ANCHOR, CONTROL_METHOD + DRIVER_ANCHOR)
    return output


def inspect_numeric_fixture(raw):
    """Validate a synthetic control's numeric buffer, NOT native/export custody.

    The caller must separately bind the failed XCTest method/assertion, its
    original exit/summary, ACK/token envelope, source/product/device and cleanup.
    A plausible buffer cannot attest any of those or the original input cause.
    """
    decoded = catalog.decode_snapshot(raw)
    if not decoded['numeric_buffer_complete']:
        raise ValueError('Complete bounded numeric fixture required')
    snapshot = decoded['snapshot']
    if snapshot['nativeMode'] != 1:
        raise ValueError('Explicit native synthetic arm required')
    rows = snapshot['rows']
    # No permission, name, room, game or private input is reachable in this
    # fixture. Both public catalog and picker scopes must actually be observed.
    stages = [row[3] for row in rows if row[1] == 4]
    if 6 not in stages or 0 not in stages or any(stage not in (0, 6) for stage in stages):
        raise ValueError('Synthetic fixture advanced beyond its public scopes')
    callbacks = [row[2] for row in rows if row[1] == 3]
    if callbacks != [3, 1]:
        raise ValueError('Exactly one catalog and one suppressed Host callback required')
    host_passes = [row[3] for row in rows if row[1] == 2 and row[2] == 1]
    if not all(phase in host_passes for phase in (0, 1)):
        raise ValueError('Actual Host Initial/Final observations required')
    native_phases = [row[3] for row in rows if row[1] == 1]
    if not all(phase in native_phases for phase in (1, 3)):
        raise ValueError('Actual public native began/ended observations required')
    return {
        'result': 'NUMERIC_SYNTHETIC_CONTROL_REQUIRES_NATIVE_EXPORT_CUSTODY',
        'profile': PROFILE,
        'expected_failed_method': METHOD,
        'snapshot_sha256': hashlib.sha256(raw).hexdigest(),
        'source_runtime_product_cleanup_verified': False,
        'failed_only_export_verified': False,
        'native_input_neutrality': False,
        'root_cause_proven': False,
    }
