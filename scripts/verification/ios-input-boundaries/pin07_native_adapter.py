"""Strict native acceptance of the PIN07 synthetic failed-only export control.

Reuse the existing archive/build/process/device/cleanup owner. Do not reinterpret
arbitrary failed journeys as successful collection or relax the ordinary runner.
"""
import hashlib
import json

import frozen_sources
import pin01_receipts
import pin02_capture
import pin06_public_setup_pair as pair
import pin07_failure_control as control

PROFILE = control.PROFILE
METHOD = control.METHOD
FAILURE = ('XCTAssertEqual failed: ("XCTWaiterResult(rawValue: 2)") is not equal to '
           '("XCTWaiterResult(rawValue: 1)") - '
           'One public setup tap must remove the source and expose its destination')


def require(condition, message):
    if not condition:
        raise ValueError(message)


def renderer_for_profile(profile, source, runtime, device):
    require((profile, source, runtime, device) == (PROFILE, pair.SOURCE, pair.RUNTIME, pair.DEVICE),
            'Exact synthetic profile/source/runtime/device required; no fallback')
    def render(sources, digests, *, arm):
        require(arm == 'native', 'Only the synthetic native arm is executable')
        return control.render(sources, digests, profile=PROFILE)
    return render


def test_command(base, result):
    return [*base, 'test-without-building', '-resultBundlePath', str(result),
            '-only-testing:iosAppUITests/' + METHOD.removesuffix('()')]


def verify_results(summary, tests, simulator_id, code):
    require(type(code) is int and code == 65, 'Actual failed XCTest exit 65 required')
    for key, expected in (('totalTestCount', 1), ('failedTests', 1), ('passedTests', 0),
                          ('skippedTests', 0), ('expectedFailures', 0)):
        require(type(summary.get(key)) is int and summary[key] == expected,
                'Exactly one unskipped unexpected failed synthetic method required')
    require(summary.get('result') == 'Failed', 'Original XCTest failure must be retained')
    devices = summary.get('devicesAndConfigurations', [])
    require(len(devices) == len(tests.get('devices', [])) == 1,
            'Exactly one actual result device required')
    for device in (devices[0].get('device', {}), tests['devices'][0]):
        require(device.get('deviceId') == simulator_id and device.get('architecture') == 'arm64' and
                device.get('platform') == 'iOS Simulator' and device.get('modelName') == 'iPhone 16 Pro' and
                device.get('osVersion') == '18.5', 'Actual owned ARM64 iOS18.5 iPhone16Pro required')
    cases = []
    def visit(nodes):
        for node in nodes:
            if node.get('nodeType') == 'Test Case':
                cases.append(node)
            visit(node.get('children', []))
    visit(tests.get('testNodes', []))
    require(len(cases) == 1 and cases[0].get('nodeIdentifier') == METHOD and
            cases[0].get('result') == 'Failed', 'Only the exact synthetic method may execute')
    failures = summary.get('testFailures', [])
    require(len(failures) == 1 and failures[0].get('testIdentifierString') == METHOD and
            failures[0].get('targetName') == 'iosAppUITests' and failures[0].get('failureText') == FAILURE,
            'The original transition assertion must be the ONLY failed assertion')
    return {METHOD: 'Failed'}


def failed_method_exports(result, destination, outcomes):
    require(type(outcomes) is dict and outcomes == {METHOD: 'Failed'},
            'Only the verified synthetic failure can export attachments')
    directory = destination / 'failed-01'
    return [(directory, ['export', 'attachments', '--path', str(result), '--output-path',
                         str(directory), '--test-id', METHOD])]


def load(path, limit=1024 * 1024):
    return json.loads(frozen_sources._read(path, limit), object_pairs_hook=pin01_receipts.reject_duplicates,
                      parse_constant=lambda _: (_ for _ in ()).throw(ValueError('Nonfinite evidence')))


def accept_native_export(out, state):
    require((state['profile'], state['source'], state['source_tree'], state['runtime'], state['device']) ==
            (PROFILE, pair.SOURCE, pair.TREE, pair.RUNTIME, pair.DEVICE), 'Original source/tree/device binding missing')
    require(state['xcode'] == 'Xcode 26.3\nBuild version 17C529\n' and state['sdk'] == '26.2' and
            state['debug_bundle'] == 'me.parlor.ios.debug', 'Exact compiler/SDK/Debug identity required')
    require(len(state['arms']) == 1 and state['arms'][0]['arm'] == 'native', 'Exactly one synthetic native arm required')
    row = state['arms'][0]
    require(row['build_exit'] == 0 and row['test_exit'] == 65 and row['result'] == 'JOURNEYS_FAILED' and
            row['cleanup'] == 'PASS', 'Actual build, failed test and owned cleanup required')
    dest = out / 'native'
    outcomes = verify_results(load(dest / 'summary.json'), load(dest / 'tests.json'), row['simulator'], row['test_exit'])
    require(row['methods'] == outcomes and row['attachment_test_ids'] == [METHOD], 'Exact failed-test export binding required')
    for path, digest in pair.INPUTS.items():
        require(state['original_inputs'].get(path) == digest, 'Frozen original source bytes changed')
    inputs = load(dest / 'inputs.json')
    plan = control.render({p: pair.fixture(p) for p in pair.MODIFIED}, pair.INPUTS, profile=PROFILE)
    expected = state['original_inputs'] | {p: hashlib.sha256(raw.encode()).hexdigest() for p, raw in plan.items()}
    require(inputs == expected, 'Copied source must match ONLY the reviewed synthetic plan')
    product_raw = frozen_sources._read(dest / 'product.json', 1024 * 1024)
    product = load(dest / 'product.json')
    require(product and all(type(p) is str and type(d) is str and len(d) == 64 for p, d in product.items()) and
            hashlib.sha256(product_raw).hexdigest() == row['product_manifest_sha256'], 'Built product custody missing')
    requests = []
    for path in (dest / 'attachments/failed-01').rglob('*'):
        if path.is_file() and not path.is_symlink() and 0 < path.stat().st_size <= 4096:
            try:
                value = load(path, 4096)
            except (ValueError, UnicodeError):
                continue
            if type(value) is dict and 'originalTransitionFailed' in value:
                requests.append(value)
    require(len(requests) == 1, 'Exactly one retained post-failure request required')
    request = requests[0]
    require(set(request) == {'schema', 'token', 'ackObserved', 'originalTransitionFailed'} and
            type(request['schema']) is int and request['schema'] == 1 and request['ackObserved'] is True and
            request['originalTransitionFailed'] is True and type(request['token']) is str and
            pin02_capture.UUID.fullmatch(request['token']), 'Real once-only failed-request ACK/token required')
    paths = list(dest.glob('capture-*.json'))
    require(len(paths) == 1 and paths[0].name == 'capture-' + request['token'] + '.json', 'Exactly one matching Kotlin export required')
    raw = frozen_sources._read(paths[0], pin02_capture.MAX_BYTES)
    capture = load(paths[0], pin02_capture.MAX_BYTES)
    import run_pin03
    decoded = run_pin03.decode_capture(raw, {request['token']}, row['simulator'], state['debug_bundle'])
    control.inspect_numeric_fixture(json.dumps(capture['snapshot'], allow_nan=False).encode())
    require(len(row['captures']) == 1 and row['captures'][0] == decoded, 'Retained original export differs from collector')
    cleanup = load(dest / 'cleanup.json')
    require(cleanup.get('gradle_exit') == 0 and cleanup.get('workers_before', {}).get('result') == 'PASS' and
            cleanup.get('workers_after', {}).get('result') == 'PASS' and
            cleanup.get('simulator', {}).get('result') == 'PASS' and
            cleanup['simulator'].get('udid') == row['simulator'], 'Actual owned worker/device cleanup required')
    commands = state.get('commands', [])
    require(commands and not any(p.get('status') != 'EXITED' or not p.get('direct_child_reaped') or
                    p.get('signals') for p in commands), 'Every native child must exit and be reaped')
    for label, code in (('native-build', 0), ('native-tests', 65), ('native-gradle-stop', 0)):
        selected = [p for p in commands if p.get('label') == label]
        require(len(selected) == 1 and selected[0].get('exit_code') == code,
                'Exactly one real build, failed test and Gradle stop required')
    return 'SYNTHETIC_FAILED_EXPORT_CAPTURE_ONLY'
