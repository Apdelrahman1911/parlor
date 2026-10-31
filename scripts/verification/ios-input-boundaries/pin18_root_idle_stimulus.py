"""Deliberate AX idle stimulus, not a slowed production test or natural failure.

Published Compose1.10.3 schedules root cleanup after2000ms without root reads.
Original failed queries consumed8.200s, but had no native root observations.
This separate synthetic Host control inserts ONE2500ms AX-silent interval and
keeps the original forced transition failure. Original shipping tests, gestures,
queries, deadlines, dependencies and all prior profiles remain unchanged.
"""
import hashlib
import json
import math

import frozen_sources
import pin01_copy
import pin02_capture
import pin06_public_setup_pair as pair
import pin07_failure_control as fault
import pin07_native_adapter as prior
import pin08_accessibility as native
import pin10_root_boundary as root

SOURCE, TREE = root.SOURCE, root.TREE
PROFILE = 'synthetic-root-idle-stimulus'
ARMS = root.ARMS
METHOD = 'IOSAppLaunchUITests/testPIN18SyntheticRootIdleGapCapture()'
RESULT = 'SYNTHETIC_ROOT_IDLE_STIMULUS_COMPLETE_NOT_QUALIFICATION'
SCOPE = ('One explicitly altered2500ms AX-silent stimulus in a forced-failure control; '
         'not original reproduction, a slower passing application journey, root causality, '
         'full60 neutrality, shipping qualification or physical verification')
ATTACHMENT = 'PIN18 synthetic public idle stimulus (not natural failure)'
STIMULUS = '''        // COPY ONLY: deliberate idle-gap fault stimulus, never a passing-test workaround.
        let source = setupButton(app, prefix: "Open a room and show a code your friends can join.")
        func sample() -> [Double] {
            let started = ProcessInfo.processInfo.systemUptime * 1000
            let exists = source.exists
            let frame = exists ? source.frame : .zero
            let enabled = exists && source.isEnabled
            return [started, ProcessInfo.processInfo.systemUptime * 1000,
                    exists ? 1 : 0, enabled ? 1 : 0,
                    Double(frame.minX), Double(frame.minY), Double(frame.width), Double(frame.height)]
        }
        let before = sample()
        let gapStarted = ProcessInfo.processInfo.systemUptime * 1000
        Thread.sleep(forTimeInterval: 2.5) // Exactly one declared stimulus; no AX polling in this interval.
        let gapFinished = ProcessInfo.processInfo.systemUptime * 1000
        let after = sample()
        pin18IdleObservation = try JSONSerialization.data(withJSONObject: [
            "schema": 1, "stimulusMs": 2500, "gap": [gapStarted, gapFinished],
            "samples": [before, after]], options: [.sortedKeys])
'''
PROPERTY = '    private var pin18IdleObservation: Data?\n'
FAILED_ATTACHMENT = '''            if let data = pin18IdleObservation {
                let attachment = XCTAttachment(data: data, uniformTypeIdentifier: "public.json")
                attachment.name = "PIN18 synthetic public idle stimulus (not natural failure)"
                attachment.lifetime = .keepAlways
                add(attachment)
            }
'''


def renderer_for_profile(profile, source, runtime, device):
    native.require((profile, source, runtime, device) == (PROFILE, SOURCE, pair.RUNTIME, pair.DEVICE),
                   'Exact explicit root idle control required')
    base = root.renderer_for_profile(root.PROFILE, SOURCE, runtime, device)
    def render(sources, digests, *, arm):
        output = base(sources, digests, arm=arm)
        driver = output[pin02_capture.DRIVER]
        old = fault.CONTROL_METHOD.replace('testPIN07SyntheticHostFailureCapture',
                                          'testPIN08SyntheticAccessibilityCapture')
        new = old.replace('testPIN08SyntheticAccessibilityCapture', METHOD.split('/')[1][:-2])
        new = pin01_copy.once(new, '        try tapPublicSetupTransition(app,',
                             STIMULUS + '        try tapPublicSetupTransition(app,')
        driver = pin01_copy.once(driver, old, new)
        driver = pin01_copy.once(driver, 'final class IOSAppLaunchUITests: XCTestCase {\n',
                                'final class IOSAppLaunchUITests: XCTestCase {\n' + PROPERTY)
        driver = pin01_copy.once(driver, '        if result != .completed {\n            PIN02FailureRequest.captureAfterFailedTransition(app)\n            // Capture after the single tap,',
                                '        if result != .completed {\n' + FAILED_ATTACHMENT +
                                '            PIN02FailureRequest.captureAfterFailedTransition(app)\n            // Capture after the single tap,')
        return output | {pin02_capture.DRIVER: driver}
    return render


def test_command(base, result):
    return [*base, 'test-without-building', '-resultBundlePath', str(result),
            '-only-testing:iosAppUITests/' + METHOD.removesuffix('()')]


def verify_results(summary, tests, simulator_id, code):
    return native.verify_results(summary, tests, simulator_id, code, method=METHOD)


def failed_method_exports(result, destination, outcomes):
    native.require(outcomes == {METHOD: 'Failed'}, 'Only the original known synthetic failure may export')
    directory = destination / 'failed-01'
    return [(directory, ['export', 'attachments', '--path', str(result), '--output-path',
                         str(directory), '--test-id', METHOD])]


retain_companion = root.retain_companion
retain_root_companion = root.retain_root_companion


def validate_stimulus(value, captured_ms):
    native.require(type(value) is dict and set(value) == {'schema', 'stimulusMs', 'gap', 'samples'} and
        type(value['schema']) is int and value['schema'] == 1 and
        type(value['stimulusMs']) is int and value['stimulusMs'] == 2500,
        'Only the declared numeric synthetic stimulus fields allowed')
    gap, samples = value['gap'], value['samples']
    native.require(type(gap) is list and len(gap) == 2 and
        all(type(v) in (int, float) and math.isfinite(v) for v in gap) and
        0 <= gap[0] <= gap[1] <= captured_ms and 2500 <= gap[1]-gap[0] <= 30000,
        'Actual bounded2500ms or longer scheduled interval required')
    native.require(type(samples) is list and len(samples) == 2, 'Exactly two original sequential samples required')
    for row in samples:
        native.require(type(row) is list and len(row) == 8 and
            all(type(v) in (int, float) and math.isfinite(v) for v in row) and
            0 <= row[0] <= row[1] <= captured_ms and row[2] in (0, 1) and row[3] in (0, 1) and
            row[3] <= row[2] and all(abs(v) <= 100000 for v in row[4:]) and
            row[6] >= 0 and row[7] >= 0, 'Only bounded public query clocks, status and geometry allowed')
        if row[2] == 0:
            native.require(row[4:] == [0, 0, 0, 0], 'Absent control geometry must remain zero')
    native.require(samples[0][1] <= gap[0] and gap[1] <= samples[1][0],
                   'Actual ordered before/silence/after sequence required')
    return value


def accept_native_export(out, state):
    native.accept_capability_export(out, state, profile=PROFILE,
        renderer=renderer_for_profile(PROFILE, SOURCE, pair.RUNTIME, pair.DEVICE), method=METHOD)
    root.accept_root_companions(out, state)
    destination = out / 'native'
    candidates = []
    for p in (destination / 'attachments/failed-01').rglob('*'):
        if p.is_file() and not p.is_symlink() and 0 < p.stat().st_size <= 4096:
            try:
                value = prior.load(p, 4096)
            except (ValueError, UnicodeError):
                continue
            if type(value) is dict and 'stimulusMs' in value:
                candidates.append((p, value))
    native.require(len(candidates) == 1, 'Exactly one original failed-control stimulus attachment required')
    legacy = prior.load(next(destination.glob('capture-*.json')))
    path, value = candidates[0]
    validate_stimulus(value, legacy['capturedMs'])
    root_value = prior.load(destination / ('native-root-' + legacy['token'] + '.json'))
    nil_writes = [r for r in root_value['rows'] if r[2:] == [2, 0] and
                  value['gap'][0] <= r[0] <= r[1] <= value['gap'][1]]
    native_value = prior.load(destination / ('native-accessibility-' + legacy['token'] + '.json'), native.MAX_BYTES)
    root_reads = sum(r[2] == 1 and value['gap'][0] <= r[0] <= value['gap'][1]
                     for r in root_value['rows'])
    attribute_reads = sum(value['gap'][0] <= r[0] <= value['gap'][1] for r in native_value['rows'])
    report = dict(stimulus_attachment_sha256=hashlib.sha256(path.read_bytes()).hexdigest(),
        gap_ms=value['gap'][1]-value['gap'][0], samples=value['samples'],
        nil_root_writes_during_gap=len(nil_writes),
        root_reads_in_gap=root_reads, attribute_reads_in_gap=attribute_reads,
        observed_main_thread_ax_silent=root_reads == attribute_reads == 0,
        nil_root_write_intervals=nil_writes,
        naturally_reproduced=False, original_root_cause=False, shipping_qualified=False,
        physical_verified=False, scope=SCOPE)
    target = out / 'root-idle-stimulus.json'
    if target.exists():
        native.require(prior.load(target) == report, 'Exact original idle report required')
    else:
        with target.open('x') as f:
            json.dump(report, f, indent=2)
    return RESULT
