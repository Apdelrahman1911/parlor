"""Original EN/AR identifiers on the original failed OS; diagnostic evidence, not qualification."""
import hashlib
import json
import math
import re

import frozen_sources
import pin43_owned_leaf_pair as pair_control

SOURCE, TREE = pair_control.SOURCE, pair_control.TREE
PROFILE = 'original-runtime-owned-leaf-public-journeys'
RUNTIME = 'com.apple.CoreSimulator.SimRuntime.iOS-26-2'
DEVICE = 'com.apple.CoreSimulator.SimDeviceType.iPhone-17-Pro'
ARMS = ('compose',)
CASES = tuple('IOSAppLaunchUITests/testAdditionalGamesExposeOnlyMultiplayerSetupIn' + language + '()'
              for language in ('Arabic', 'English'))
TESTS = tuple('iosAppUITests/' + case.removesuffix('()') for case in CASES)
TEST_TIMEOUT_SECONDS = 900
RESULT = 'ORIGINAL_RUNTIME_PUBLIC_JOURNEYS_COMPLETE_NOT_QUALIFICATION'
SCOPE = ('Original two AR/EN XCTest identifiers on original failed iOS26.2/iPhone17Pro; '
         'seven owned methods/inherited setter untouched; original application/actions/assertions/10s deadlines; '
         'all failures retained; not a successful sampled-wrapper replay, causality, neutrality or qualification')
require = pair_control.require
load = pair_control.samples.load
decode_companions = pair_control.decode_companions
retain_companion = pair_control.retain_companion
PAIR_RUN = 41313523238
PAIR_HARNESS = 'c1a64034d159a3cfada558ce723d48dad45084a4'
PAIR_RECEIPT_SHA256 = 'e948960af9135c2e14f53bb198940557fdea83d7f7bc3a27d356b2f6c7dd9ec1'


def require_pair_binding():
    require(PAIR_RUN == 41313523238 and PAIR_HARNESS == 'c1a64034d159a3cfada558ce723d48dad45084a4'
            and PAIR_RECEIPT_SHA256 == 'e948960af9135c2e14f53bb198940557fdea83d7f7bc3a27d356b2f6c7dd9ec1',
            'Independently accepted terminal pair is required before publication/execution')


def renderer_for_profile(profile, source, runtime, device):
    require((profile, source, runtime, device) == (PROFILE, SOURCE, RUNTIME, DEVICE),
            'Exact original failed runtime/source/device, no platform fallback')
    leaf = pair_control.leaf
    # Reuse the hash-bound template assembler, not its old runtime validator.
    # The templates contain no old OS/device identity. Actual new runtime,
    # device and ABI must independently pass this consumer's execution gates.
    base = pair_control.renderer_for_profile(pair_control.PROFILE, source, leaf.pair.RUNTIME, leaf.pair.DEVICE)

    def render(sources, digests, *, arm):
        require(arm == 'compose', 'One explicitly native-only original-runtime arm')
        plan = base(sources, digests, arm=arm)
        driver = pair_control.baseline.restore_driver(plan[pair_control.pin02_capture.DRIVER])
        driver = driver.replace('PIN43Configure', 'PIN44Configure').replace('PARLOR_PIN43_', 'PARLOR_PIN44_')
        driver = pair_control.pin01_copy.once(driver, pair_control.PROFILE, PROFILE)
        owner = plan[pair_control.pin02_capture.CONTAINER].replace('PARLOR_PIN43_', 'PARLOR_PIN44_')
        owner = pair_control.pin01_copy.once(owner, pair_control.PROFILE, PROFILE)
        return plan | {pair_control.pin02_capture.DRIVER: driver, pair_control.pin02_capture.CONTAINER: owner}
    return render


def test_command(base, result):
    return [*(a for a in base if a != '-quiet'), 'test-without-building', '-resultBundlePath', str(result),
            *('-only-testing:' + test for test in TESTS)]


def verify_results(summary, tests, simulator, code):
    require(type(code) is int and code in (0, 65), 'Actual XCTest pass or retained exit65')
    for key in ('totalTestCount', 'passedTests', 'failedTests', 'skippedTests', 'expectedFailures'):
        require(type(summary.get(key)) is int and 0 <= summary[key] <= 2, 'Exact finite original counts')
    require(summary['totalTestCount'] == 2 and summary['skippedTests'] == summary['expectedFailures'] == 0
            and summary['passedTests'] + summary['failedTests'] == 2, 'Both original tests execute without skips')
    configurations, devices = summary.get('devicesAndConfigurations', []), tests.get('devices', [])
    require(len(configurations) == len(devices) == len(tests.get('testPlanConfigurations', [])) == 1,
            'One actual owned runtime configuration')
    for device in (configurations[0].get('device', {}), devices[0]):
        require(device.get('deviceId') == simulator and device.get('architecture') == 'arm64'
                and device.get('platform') == 'iOS Simulator' and device.get('modelName') == 'iPhone 17 Pro'
                and device.get('osVersion') == '26.2', 'Actual original failed OS/model, not relabeled18.5 results')

    def visit(nodes):
        return [case for node in nodes for case in
                ([node] if node.get('nodeType') == 'Test Case' else visit(node.get('children', [])))]
    nodes = visit(tests.get('testNodes', []))
    require(len(nodes) == 2 and {n.get('nodeIdentifier') for n in nodes} == set(CASES)
            and all(n.get('result') in ('Passed', 'Failed') and type(n.get('durationInSeconds')) in (int, float)
                    and math.isfinite(n['durationInSeconds']) and 0 < n['durationInSeconds'] < TEST_TIMEOUT_SECONDS
                    for n in nodes), 'Both exact original identifiers with actual finite positive durations')
    methods = {n['nodeIdentifier']: n['result'] for n in nodes}
    passed = sum(v == 'Passed' for v in methods.values())
    require(passed == summary['passedTests'] and (code == 0) == (passed == 2)
            and summary.get('result') == ('Passed' if passed == 2 else 'Failed'), 'Original failure outcomes agree')
    return methods


def failed_method_exports(result, destination, outcomes):
    require(set(outcomes) == set(CASES) and all(v in ('Passed', 'Failed') for v in outcomes.values()),
            'Only original-runtime failed methods own exports')
    return [(destination / ('failed-%02d' % i), ['export', 'attachments', '--path', str(result),
             '--output-path', str(destination / ('failed-%02d' % i)), '--test-id', method])
            for i, method in enumerate(sorted(k for k, v in outcomes.items() if v == 'Failed'), 1)]


def verify_commands(state, row):
    for stage, exit_code, budget in (('build', 0, 2700), ('tests', row['test_exit'], 900), ('gradle-stop', 0, 120)):
        selected = [c for c in state['commands'] if c['label'] == 'compose-' + stage]
        require(len(selected) == 1 and selected[0]['exit_code'] == exit_code and selected[0]['timeout_seconds'] == budget,
                'One actual bounded original-runtime command per stage')
        if stage != 'tests':
            continue
        argv = selected[0]['command']
        require([a for a in argv if a.startswith('-only-testing:')] == ['-only-testing:' + t for t in TESTS]
                and argv.count('test-without-building') == 1 and '-quiet' not in argv
                and not any(a.startswith(('-skip-testing', '-test-iterations', '-retry-tests', '-run-tests-until-failure'))
                            for a in argv), 'Original identifiers once, no retries, iterations or skipped assertions')
        require(argv.count('/usr/bin/xcodebuild') == 1, 'One actual declared Xcode executable')
        for flag, value in (('-project', 'iosApp/iosApp.xcodeproj'), ('-scheme', 'iosApp'), ('-configuration', 'Debug'),
                            ('-sdk', 'iphonesimulator'), ('-destination', 'platform=iOS Simulator,id=' + row['simulator']),
                            ('-parallel-testing-enabled', 'NO'), ('-maximum-concurrent-test-simulator-destinations', '1')):
            require(argv.count(flag) == 1 and argv[argv.index(flag) + 1] == value,
                    'Exact actual Debug source/platform/owned-device arguments')
    labels = [c['label'] for c in state['commands']]
    require(labels.index('compose-build') < labels.index('compose-tests') < labels.index('compose-gradle-stop'),
            'Actual build, original tests and owned cleanup in order')


def accept_native_export(out, state):
    return accept_original_runtime_export(out, state, profile=PROFILE, renderer=renderer_for_profile,
        result_verifier=verify_results, command_verifier=verify_commands, cases=CASES,
        result=RESULT, receipt_name='original-runtime-journeys.json')


def accept_original_runtime_export(out, state, *, profile, renderer, result_verifier,
                                   command_verifier, cases, result, receipt_name):
    require_pair_binding()
    require((state['profile'], state['source'], state['source_tree'], state['runtime'], state['device'],
             state['debug_bundle'], state['xcode'], state['sdk']) ==
            (profile, SOURCE, TREE, RUNTIME, DEVICE, 'me.parlor.ios.debug',
             'Xcode 26.3\nBuild version 17C529\n', '26.2') and len(state['arms']) == 1,
            'Exact original failed runtime/compiler/source/product identity')
    row = state['arms'][0]; d = out / 'compose'
    require(row['arm'] == 'compose' and row['build_exit'] == 0 and row['cleanup'] == 'PASS'
            and row['result'] in ('JOURNEYS_PASSED', 'JOURNEYS_FAILED'), 'Actual complete build, outcomes and cleanup')
    methods = result_verifier(load(d / 'summary.json'), load(d / 'tests.json'), row['simulator'], row['test_exit'])
    require(methods == row['methods'] and row.get('attachment_test_ids', []) == sorted(k for k, v in methods.items() if v == 'Failed'),
            'All actual original outcomes and failed-method attachment ownership')
    leaf = pair_control.leaf
    require(all(state['original_inputs'].get(p) == h for p, h in leaf.pair.INPUTS.items()), 'All original input bindings')
    plan = renderer(profile, SOURCE, RUNTIME, DEVICE)(
        {p: leaf.pair.fixture(p) for p in leaf.pair.MODIFIED}, leaf.pair.INPUTS, arm='compose')
    require(load(d / 'inputs.json') == state['original_inputs'] | {p: hashlib.sha256(v.encode()).hexdigest() for p, v in plan.items()},
            'Entire original input manifest plus only reviewed copied Swift wrapper/driver')
    product = frozen_sources._read(d / 'product.json', 2_097_152)
    require(load(d / 'product.json') and hashlib.sha256(product).hexdigest() == row['product_manifest_sha256'],
            'Recorded built product custody, not live binary attestation')
    owners = pair_control.samples.requests_by_method(d, methods)
    captures, reports = [], []
    for token in sorted(owners):
        raw = frozen_sources._read(d / ('capture-' + token + '.json'), pair_control.pin02_capture.MAX_BYTES)
        decoded = leaf.run_pin03.decode_capture(raw, set(owners), row['simulator'], state['debug_bundle'])
        require(decoded['snapshot'] == dict(schema=1, nativeMode=0, overflow=0, invalid=0, rows=[])
                and decoded['numeric_buffer_complete'] is False, 'ACK only; no invented Kotlin/native evidence')
        captures.append(decoded)
        reports.append(decode_companions(d, token, row['simulator'], state['debug_bundle'], json.loads(raw)))
    require(row.get('captures', []) == captures and row.get('companion_captures', []) == reports
            and not row.get('root_companion_captures') and not row.get('motion_companion_captures'), 'All actual failure-only captures')
    for prefix in ('capture-', 'native-touch-', 'native-only-'):
        require({p.name for p in d.glob(prefix + '*.json')} == {prefix + t + '.json' for t in owners},
                'One actual companion per failure, zero exports on success')
    for prefix in ('native-accessibility-', 'native-frame-', 'native-root-'):
        require(not any(d.glob(prefix + '*.json')), 'No fabricated absent getter evidence')
    cleanup = load(d / 'cleanup.json')
    require(cleanup['gradle_exit'] == 0 and cleanup['workers_before']['result'] == cleanup['workers_after']['result'] ==
            cleanup['simulator']['result'] == 'PASS' and cleanup['simulator']['udid'] == row['simulator'], 'Exact owned workers/device retired')
    require(state['commands'] and all(c['status'] == 'EXITED' and c['direct_child_reaped'] and not c.get('signals')
            for c in state['commands']), 'Every actual child exited and reaped')
    command_verifier(state, row)
    require(state['build_profile']['test_timeout_seconds'] == 900, 'Original process budget, unchanged10s assertions')
    starts = re.findall(rb"Test Case '-\[iosAppUITests\.([A-Za-z0-9_]+) ([A-Za-z0-9_]+)\]' started\.",
                        frozen_sources._read(d / 'test.log', 4 * 1024 * 1024))
    require(tuple(c.decode() + '/' + m.decode() + '()' for c, m in starts) == cases, 'All original selected identifiers execute exactly once in order')
    receipt = dict(methods=methods, failed_sample_tokens=owners, native=reports, original_runtime='26.2',
                   original_device='iPhone 17 Pro', pair_run=PAIR_RUN, pair_harness=PAIR_HARNESS,
                   pair_receipt_sha256=PAIR_RECEIPT_SHA256, all_failed_samples_retained=True,
                   application_root_cause=False, shipping_qualified=False, native_timing_neutrality=False, physical_verified=False)
    require(receipt_name in ('original-runtime-journeys.json', 'original-runtime-context-native.json'),
            'One declared original-runtime receipt owner')
    target = out / receipt_name
    if target.exists(): require(load(target) == receipt, 'Exact original saved receipt')
    else:
        with target.open('x') as f: json.dump(receipt, f, indent=2)
    return result
