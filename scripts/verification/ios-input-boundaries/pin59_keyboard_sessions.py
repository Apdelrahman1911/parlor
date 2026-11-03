"""Exercise original keyboard sessions before the fixed public setup samples.

Reuse the exact PIN58 native adapter and its existing once-only failure request.
Request capture after a failed waiter returns, before the original screenshot
can evict its observations. No new input, probe, query, repair, or deadline.
"""
from contextlib import contextmanager
import hashlib
import math
from pathlib import Path

import pin58_native_ax_returns as native

SOURCE, TREE, RUNTIME, DEVICE, ARMS = native.SOURCE, native.TREE, native.RUNTIME, native.DEVICE, native.ARMS
PROFILE = 'original-keyboard-session-focus-reproduction'
RESULT = 'ORIGINAL_KEYBOARD_SESSION_REPRODUCTION_COMPLETE_NOT_QUALIFICATION'
SCOPE = ('Original EN/AR keyboard open/Done/reopen and room-code sessions, then five fixed AR/EN setup pairs; '
         'same PIN58 adapter and original actions/assertions/deadlines, all failures retained; '
         'not a native timing-neutrality, shipping, causal or physical attestation')
KEYBOARD_CASES = tuple('IOSAppLaunchUITests/testKeyboardInsetsOnNameAndRoomCodeIn' + language + '()'
                       for language in ('Arabic', 'English'))
CASES = KEYBOARD_CASES + native.CASES
TESTS = tuple('iosAppUITests/' + case.removesuffix('()') for case in CASES)
TEST_TIMEOUT_SECONDS = native.TEST_TIMEOUT_SECONDS
require, load, runtime, MAX_BYTES = native.require, native.load, native.runtime, native.MAX_BYTES
decode, retain_companion = native.decode, native.retain_companion
PREREQUISITE = Path(__file__).resolve().parents[3] / 'docs/audits/2026-11-02-IOS-NATIVE-AX-RETURNS-CAPABILITY.json'
PREREQUISITE_SHA256 = '070c1b6c31dfd9ae8f160b1036f1beec7e75ffd67205aa73a336a7bfca1f7985'
KEYBOARD_ANCHOR = '    @MainActor\n    private func verifyKeyboardLayout(language: String, locale: String, copy: KeyboardCopy) throws {'
NEXT_ANCHOR = '    @MainActor\n    private func setupButton('
ORIGINAL_LAUNCH = '        app.launch()\n        defer { app.terminate() }'
OWNED_LAUNCH = ('        PIN56Configure(app)\n        pin11PublicApp = app\n        app.launch()\n'
                '        defer {\n            pin11PublicApp = nil\n            app.terminate()\n        }')
FAILURE_BRANCH = '        if result != .completed {\n'
EARLY_CAPTURE = ('            if let app = pin11PublicApp {\n'
                 '                pin11PublicApp = nil\n'
                 '                pin11CapturePublicFailureOnce(app)\n'
                 '            }\n')
CAPTURE_REGIONS = (
    ('    @MainActor\n    private func tapSettledControl(',
     '    @MainActor\n    private func verifyKeyboardCycle('),
    ('    @MainActor\n    private func assertTextValue(',
     '    @MainActor\n    private func fullKeyboardTop('),
)


def failure_capture_before_screenshot(driver, *, restore=False):
    """Move the existing failure request, not application/test observation timing."""
    for start, end in CAPTURE_REGIONS:
        require(driver.count(start) == driver.count(end) == 1, 'Exact original failed-waiter helper boundaries')
        prefix, region = driver.split(start)
        body, suffix = region.split(end)
        old, new = ((FAILURE_BRANCH + EARLY_CAPTURE, FAILURE_BRANCH) if restore else
                    (FAILURE_BRANCH, FAILURE_BRANCH + EARLY_CAPTURE))
        require(body.count(old) == 1 and body.count('let result = XCTWaiter.wait(') == 1,
                'Exactly one existing waiter and failure-only capture site')
        require(body.index('let result = XCTWaiter.wait(') < body.index(old) < body.index('XCTAttachment(screenshot:'),
                'Capture only after the waiter returns and before the original screenshot')
        if not restore:
            require(EARLY_CAPTURE not in body, 'No duplicate failure request insertion')
        driver = prefix + start + body.replace(old, new, 1) + end + suffix
    return driver


def keyboard_launch_owner(driver, *, restore=False):
    require(driver.count(KEYBOARD_ANCHOR) == driver.count(NEXT_ANCHOR) == 1, 'Exact original helper boundaries')
    prefix, region = driver.split(KEYBOARD_ANCHOR)
    body, suffix = region.split(NEXT_ANCHOR)
    old, new = (OWNED_LAUNCH, ORIGINAL_LAUNCH) if restore else (ORIGINAL_LAUNCH, OWNED_LAUNCH)
    require(body.count(old) == 1, 'Exactly one original keyboard launch, no repeated ownership')
    return prefix + KEYBOARD_ANCHOR + body.replace(old, new, 1) + NEXT_ANCHOR + suffix


def require_capability():
    native.require_capability()
    raw = native.prior.frozen_sources._read(PREREQUISITE, 65536)
    require(hashlib.sha256(raw).hexdigest() == PREREQUISITE_SHA256, 'Frozen independent native capability receipt')
    receipt = load(PREREQUISITE)
    require(receipt == dict(run=41540767002, harness='d36ada5929cb10b8d5e0d2cb052fa227031a255a',
        original_source=SOURCE, original_tree=TREE, actual_mac_controls=7, skipped_controls=0,
        original_wrappers_passed=10, original_failures=0, owned_cleanup='PASS',
        artifact_sha256='2648c70b1248a2bcbc9799cec2c94e6371a26e47dd21920900134cf9ceff3bcb',
        shipping_qualified=False, application_root_cause=False, native_timing_neutrality=False,
        physical_verified=False), 'Actual accepted unchanged PIN58 capability, not an application fix')


def renderer_for_profile(profile, source, os, device):
    require((profile, source, os, device) == (PROFILE, SOURCE, RUNTIME, DEVICE), 'Exact original keyboard cohort')
    previous = native.renderer_for_profile(native.PROFILE, source, os, device)
    def render(sources, digests, *, arm):
        plan = previous(sources, digests, arm=arm)
        driver = native.prior.pin02_capture.DRIVER
        plan[driver] = failure_capture_before_screenshot(keyboard_launch_owner(plan[driver]))
        return plan
    return render


def test_command(base, result):
    return [*(a for a in base if a != '-quiet'), 'test-without-building', '-resultBundlePath', str(result),
            *('-only-testing:' + test for test in TESTS)]


def verify_results(summary, tests, simulator, code):
    count = len(CASES)
    require(type(code) is int and code in (0, 65), 'Actual XCTest pass or retained failure')
    for key in ('totalTestCount', 'passedTests', 'failedTests', 'skippedTests', 'expectedFailures'):
        require(type(summary.get(key)) is int and 0 <= summary[key] <= count, 'Exact executed integer counts')
    require(summary['totalTestCount'] == count and summary['skippedTests'] == summary['expectedFailures'] == 0
        and summary['passedTests'] + summary['failedTests'] == count, 'All twelve selected original methods execute')
    configs, devices = summary.get('devicesAndConfigurations', []), tests.get('devices', [])
    require(len(configs) == len(devices) == len(tests.get('testPlanConfigurations', [])) == 1, 'One owned configuration')
    for device in (configs[0].get('device', {}), devices[0]):
        require((device.get('deviceId'), device.get('architecture'), device.get('platform'),
                 device.get('modelName'), device.get('osVersion')) ==
                (simulator, 'arm64', 'iOS Simulator', 'iPhone 17 Pro', '26.2'), 'Exact original runtime and device')
    nodes = native.prior.p.original.visit(tests.get('testNodes', []))
    require(len(nodes) == count and {n.get('nodeIdentifier') for n in nodes} == set(CASES) and
        all(n.get('result') in ('Passed', 'Failed') and type(n.get('durationInSeconds')) in (int, float)
            and math.isfinite(n['durationInSeconds']) and 0 < n['durationInSeconds'] < TEST_TIMEOUT_SECONDS
            for n in nodes), 'Unique original methods, actual positive-duration outcomes')
    methods = {n['nodeIdentifier']: n['result'] for n in nodes}
    passed = sum(value == 'Passed' for value in methods.values())
    require(passed == summary['passedTests'] and (code == 0) == (passed == count) and
        summary.get('result') == ('Passed' if passed == count else 'Failed'), 'Actual outcomes agree with exit')
    failures = summary.get('testFailures', [])
    require(type(failures) is list and len(failures) == summary['failedTests'] and
        {f.get('testIdentifierString') for f in failures} == {k for k, v in methods.items() if v == 'Failed'} and
        all(f.get('targetName') == 'iosAppUITests' and type(f.get('failureText')) is str and
            f['failureText'] for f in failures), 'Every original failed assertion retained')
    return methods


def failed_method_exports(result, destination, outcomes):
    require(set(outcomes) == set(CASES) and all(v in ('Passed', 'Failed') for v in outcomes.values()),
            'All twelve original outcomes before failure-only export')
    return [(destination / ('failed-%02d' % i), ['export', 'attachments', '--path', str(result),
        '--output-path', str(destination / ('failed-%02d' % i)), '--test-id', case])
        for i, case in enumerate(sorted(k for k, v in outcomes.items() if v == 'Failed'), 1)]


@contextmanager
def admission_scope(renderer):
    # Reader-only substitution of the declared selection. Keep all source,
    # product, command, failed-only capture, decoder, and cleanup checks intact.
    prior = native.prior
    updates = dict(PROFILE=PROFILE, RESULT=RESULT, CASES=CASES, TESTS=TESTS,
        renderer_for_profile=renderer, verify_results=verify_results, decode=decode)
    saved = {key: getattr(prior, key) for key in updates}
    try:
        for key, value in updates.items():
            setattr(prior, key, value)
        yield
    finally:
        for key, value in saved.items():
            setattr(prior, key, value)


def accept_native_export(out, state):
    require_capability()
    prepared = renderer_for_profile(PROFILE, SOURCE, RUNTIME, DEVICE)
    def exact(profile, source, os, device):
        require((profile, source, os, device) == (PROFILE, SOURCE, RUNTIME, DEVICE), 'Declared selection only')
        return prepared
    with admission_scope(exact):
        return native.PRIOR_ACCEPT(out, state)
