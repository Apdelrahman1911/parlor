"""Copy-only native getter observation. Synthetic capability, NOT app qualification.

The successful two-arm current-source experiment did not reproduce a failure.
Pointer callbacks cannot distinguish native AX invalidity from XCTest resolution.
This distinct control first verifies interception/export of original native getter
returns. It never retries the original failed suite or changes production input.
"""
import hashlib
import json
import math

import frozen_sources
import pin01_copy
import pin01_receipts
import pin02_capture
import pin06_public_setup_pair as pair
import pin07_failure_control as fault
import pin07_native_adapter as prior
import current_boundary_adapter as current

SOURCE, TREE = current.SOURCE, current.TREE
PROFILE = 'synthetic-native-accessibility-capability'
ARMS = ('native',)
METHOD = 'IOSAppLaunchUITests/testPIN08SyntheticAccessibilityCapture()'
SCOPE = ('One distinct intentionally failed current-source copied Host transition with native getter '
         'observation; capability only, not original cause, neutrality, shipping or physical qualification')
TEMPLATE = 'PIN08AccessibilityObserver.swift.in'
# Reviewed separately from PIN01/PIN02; their templates/schema/limits stay exact.
TEMPLATE_SHA256 = 'ec9e300cd79259a9c589962f2a35949c8b066baa7ebb5a998d5edec5a38afcce'
INSTALL = '''        let observation = PIN08AccessibilityObserver(armed: { MainViewControllerKt.PIN01InputArmed() })
        _ = observation.install()
        pin08Accessibility = observation
        pin02Capture = PIN02Capture.install(snapshot: { [weak observation] in
            let original = MainViewControllerKt.PIN01InputSnapshot()
            observation?.exportAfterFailureOnce()
            return original
        })'''


def require(ok, message):
    if not ok:
        raise ValueError(message)


def renderer_for_profile(profile, source, runtime, device):
    require((profile, source, runtime, device) == (PROFILE, SOURCE, pair.RUNTIME, pair.DEVICE),
            'Exact distinct current-source native capability control required')
    def render(sources, digests, *, arm):
        require(arm == 'native', 'Only the explicitly observed native capability arm allowed')
        raw = frozen_sources._read(pair.HERE / TEMPLATE, frozen_sources.MAX_SOURCE_BYTES)
        require(hashlib.sha256(raw).hexdigest() == TEMPLATE_SHA256, 'Native getter template changed')
        output = fault.render(sources, digests, profile=fault.PROFILE)
        output[pin02_capture.DRIVER] = pin01_copy.once(output[pin02_capture.DRIVER],
            'func testPIN07SyntheticHostFailureCapture()', 'func testPIN08SyntheticAccessibilityCapture()')
        container = pin01_copy.once(output[pin02_capture.CONTAINER],
            '    private var pin02Capture: PIN02Capture?',
            '    private var pin02Capture: PIN02Capture?\n    private var pin08Accessibility: PIN08AccessibilityObserver?')
        container = pin01_copy.once(container,
            '        pin02Capture = PIN02Capture.install(snapshot: { MainViewControllerKt.PIN01InputSnapshot() })', INSTALL)
        output[pin02_capture.CONTAINER] = container + '\n' + raw.decode()
        return output
    return render


def test_command(base, result):
    return [*base, 'test-without-building', '-resultBundlePath', str(result),
            '-only-testing:iosAppUITests/' + METHOD.removesuffix('()')]


def verify_results(summary, tests, simulator_id, code):
    require(type(code) is int and code == 65, 'Original unexpected failed XCTest exit65 required')
    for key, value in [('totalTestCount', 1), ('failedTests', 1), ('passedTests', 0),
                       ('skippedTests', 0), ('expectedFailures', 0)]:
        require(type(summary.get(key)) is int and summary[key] == value, 'Exactly one unexpected failure required')
    require(summary.get('result') == 'Failed', 'Original failure retained')
    devices = summary.get('devicesAndConfigurations', [])
    require(len(devices) == len(tests.get('devices', [])) == 1, 'Single owned result device required')
    for d in [devices[0].get('device', {}), tests['devices'][0]]:
        require((d.get('deviceId'), d.get('architecture'), d.get('platform'), d.get('modelName'),
                 d.get('osVersion')) == (simulator_id, 'arm64', 'iOS Simulator', 'iPhone 16 Pro', '18.5'),
                'Exact owned current-source runtime/device required')
    cases = []
    def visit(nodes):
        for n in nodes:
            if n.get('nodeType') == 'Test Case':
                cases.append(n)
            visit(n.get('children', []))
    visit(tests.get('testNodes', []))
    require(len(cases) == 1 and cases[0].get('nodeIdentifier') == METHOD and cases[0].get('result') == 'Failed',
            'Only the distinct synthetic getter method may execute')
    failures = summary.get('testFailures', [])
    require(len(failures) == 1 and failures[0].get('testIdentifierString') == METHOD and
            failures[0].get('targetName') == 'iosAppUITests' and failures[0].get('failureText') == prior.FAILURE,
            'Only the original suppressed Host transition assertion may fail')
    return {METHOD: 'Failed'}


def failed_method_exports(result, destination, outcomes):
    require(outcomes == {METHOD: 'Failed'}, 'Only the exact verified failed method may export')
    directory = destination / 'failed-01'
    return [(directory, ['export', 'attachments', '--path', str(result), '--output-path',
                         str(directory), '--test-id', METHOD])]


def decode_native(raw, *, token, simulator, bundle, legacy):
    require(type(raw) is bytes and 0 < len(raw) <= 131_072, 'Bounded original native companion required')
    value = json.loads(raw, object_pairs_hook=pin01_receipts.reject_duplicates,
                       parse_constant=lambda _: (_ for _ in ()).throw(ValueError('Nonfinite native value')))
    require(type(value) is dict and set(value) == {'schema', 'token', 'simulator', 'bundle', 'process',
            'capturedMs', 'installed', 'overflow', 'rows'}, 'Unknown/private native companion field')
    require(type(value['schema']) is int and value['schema'] == 1 and value['token'] == token and
            value['simulator'] == simulator and value['bundle'] == bundle and value['process'] == legacy['process'],
            'Exact original native token/device/product/process required')
    require(type(value['process']) is int and value['process'] > 0 and
            type(value['capturedMs']) in (int, float) and math.isfinite(value['capturedMs']) and
            0 <= value['capturedMs'] <= legacy['capturedMs'], 'Valid original native process/clock required')
    require(value['installed'] is True and type(value['overflow']) is int and value['overflow'] == 0,
            'Exact ABI hooks must install and bounded buffer must be complete')
    require(type(value['rows']) is list and 0 < len(value['rows']) <= 1024, 'Complete bounded native getter rows required')
    finished = 0
    for row in value['rows']:
        require(type(row) is list and len(row) == 5 and
                all(type(v) in (int, float) and math.isfinite(v) for v in row), 'Numeric native row only')
        require(0 <= row[0] <= row[1] <= value['capturedMs'] and row[1] >= finished,
                'Original native getter clock/order required')
        finished = row[1]
        require(row[2] == int(row[2]) and 1 <= row[2] <= 1024 and row[3] in (1, 2) and
                row[4] == int(row[4]) and 0 <= row[4] <= (3 if row[3] == 1 else 1),
                'Only local ordinals/getter/public-bit enums allowed')
    require(any(r[3] == 1 and int(r[4]) & 1 for r in value['rows']) and
            any(r[3] == 2 and r[4] == 1 for r in value['rows']), 'Actual button-traits and element getters required')
    return dict(sha256=hashlib.sha256(raw).hexdigest(), token=token, rows=len(value['rows']),
                native=getter_summary(value['rows']), complete=True, scope='main-thread public scopes only, not lifetime IDs or root cause')


def getter_summary(rows):
    return {'traits_reads': sum(r[3] == 1 for r in rows), 'element_reads': sum(r[3] == 2 for r in rows),
            'disabled_button_reads': sum(r[3] == 1 and r[4] == 3 for r in rows),
            'non_element_reads': sum(r[3] == 2 and r[4] == 0 for r in rows)}


def retain_companion(sandbox, destination, token, simulator, bundle, legacy):
    import run_pin03
    source = sandbox / 'tmp' / ('parlor-pin08-' + token) / 'accessibility.json'
    require(not source.is_symlink() and source.resolve(strict=True) == source.absolute(),
            'Exact nonredirected owned native companion required')
    raw = run_pin03.read_capture(source)
    decoded = decode_native(raw, token=token, simulator=simulator, bundle=bundle, legacy=legacy)
    with (destination / ('native-accessibility-' + token + '.json')).open('xb') as f:
        f.write(raw)
    return decoded


def accept_native_export(out, state):
    require((state['source'], state['source_tree'], state['profile'], state['runtime'], state['device']) ==
            (SOURCE, TREE, PROFILE, pair.RUNTIME, pair.DEVICE), 'Actual full current-source capability identity required')
    require(state['xcode'] == 'Xcode 26.3\nBuild version 17C529\n' and state['sdk'] == '26.2' and
            state['debug_bundle'] == 'me.parlor.ios.debug', 'Exact current native compiler/Debug identity required')
    require(len(state['arms']) == 1 and state['arms'][0]['arm'] == 'native', 'Single observed capability arm required')
    row = state['arms'][0]; dest = out / 'native'
    require(row['build_exit'] == 0 and row['cleanup'] == 'PASS' and row['result'] == 'JOURNEYS_FAILED',
            'Real build/failure/owned cleanup required')
    require(row['methods'] == verify_results(prior.load(dest / 'summary.json'), prior.load(dest / 'tests.json'),
            row['simulator'], row['test_exit']) and row['attachment_test_ids'] == [METHOD], 'Original exact failed method required')
    require(all(state['original_inputs'].get(p) == h for p, h in pair.INPUTS.items()), 'Original seven current source bytes required')
    plan = renderer_for_profile(PROFILE, SOURCE, pair.RUNTIME, pair.DEVICE)(
        {p: pair.fixture(p) for p in pair.MODIFIED}, pair.INPUTS, arm='native')
    require(prior.load(dest / 'inputs.json') == state['original_inputs'] |
            {p: hashlib.sha256(v.encode()).hexdigest() for p, v in plan.items()}, 'Only reviewed native observer plan allowed')
    product = prior.load(dest / 'product.json')
    require(product and hashlib.sha256((dest / 'product.json').read_bytes()).hexdigest() == row['product_manifest_sha256'],
            'Actual product custody required')
    requests = []
    for p in (dest / 'attachments/failed-01').rglob('*'):
        if p.is_file() and not p.is_symlink() and 0 < p.stat().st_size <= 4096:
            try:
                v = prior.load(p, 4096)
            except (ValueError, UnicodeError):
                continue
            if type(v) is dict and 'originalTransitionFailed' in v:
                requests.append(v)
    require(len(requests) == 1, 'Single original failed-only request required')
    request = requests[0]; token = request.get('token')
    require(set(request) == {'schema', 'token', 'ackObserved', 'originalTransitionFailed'} and
            type(request['schema']) is int and request['schema'] == 1 and request['ackObserved'] is True and
            request['originalTransitionFailed'] is True and type(token) is str and pin02_capture.UUID.fullmatch(token),
            'Original acknowledged failed-only token required')
    require(len(list(dest.glob('capture-*.json'))) == len(row['captures']) ==
            len(list(dest.glob('native-accessibility-*.json'))) == len(row['companion_captures']) == 1,
            'Exactly one complete original and companion export required')
    import run_pin03
    raw = frozen_sources._read(dest / ('capture-' + token + '.json'), pin02_capture.MAX_BYTES)
    decoded = run_pin03.decode_capture(raw, {token}, row['simulator'], state['debug_bundle'])
    fault.inspect_numeric_fixture(json.dumps(decoded['snapshot'], allow_nan=False).encode())
    require(row['captures'] == [decoded], 'Original Kotlin capture matches collection')
    native = decode_native(frozen_sources._read(dest / ('native-accessibility-' + token + '.json'), 131_072),
        token=token, simulator=row['simulator'], bundle=state['debug_bundle'], legacy=json.loads(raw))
    require(row['companion_captures'] == [native], 'Actual native companion matches original file')
    cleanup = prior.load(dest / 'cleanup.json')
    require(cleanup['gradle_exit'] == 0 and cleanup['workers_before']['result'] == cleanup['workers_after']['result'] ==
            cleanup['simulator']['result'] == 'PASS' and cleanup['simulator']['udid'] == row['simulator'], 'Owned cleanup required')
    require(state['commands'] and all(r['status'] == 'EXITED' and r['direct_child_reaped'] and not r.get('signals')
            for r in state['commands']), 'Original native children exited and reaped')
    for label, code in [('native-build', 0), ('native-tests', 65), ('native-gradle-stop', 0)]:
        selected = [r for r in state['commands'] if r['label'] == label]
        require(len(selected) == 1 and selected[0]['exit_code'] == code, 'Single actual native command required')
    return 'SYNTHETIC_NATIVE_ACCESSIBILITY_CAPABILITY_ONLY'
