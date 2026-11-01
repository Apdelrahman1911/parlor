"""Fixed-size original/observed public-session experiment, never a green retry.

Five predeclared EN/AR pairs per arm call the original methods unchanged. Each
failed sample stays failed; all ten cases must execute once. No adaptive sample
count, retry, slower gesture, input repair or assertion deadline change exists.
This is NOT the Desktop short/cancel/scroll/recomposition neutrality matrix.
"""
import hashlib
import json
import re

import current_boundary_adapter as current
import frozen_sources
import pin01_copy
import pin02_capture
import pin29_equivalent_query_boundary as observed
import public_attachments

SOURCE, TREE = observed.SOURCE, observed.TREE
PROFILE = 'fixed-public-session-stress'
ARMS = ('original', 'compose')
PAIRS = 5
TEST_TIMEOUT_SECONDS = 900 * PAIRS  # Same per-pair process budget; assertions stay 10s.
RESULT = 'FIXED_PUBLIC_SESSION_STRESS_COMPLETE_NOT_QUALIFICATION'
SCOPE = ('Five fixed EN/AR pairs, at most60 public Host/Join flows per arm; all failures retained, '
         'no retries/repair/deadline changes; different free runners are not a timing benchmark, '
         'full Desktop60-journey native neutrality, original causality or shipping qualification')
METHODS = tuple((f'testPIN30Sample{sample:02d}{language}',
                 'testAdditionalGamesExposeOnlyMultiplayerSetupIn'+language)
                for sample in range(1, PAIRS+1) for language in ('Arabic', 'English'))
CASES = tuple('IOSAppLaunchUITests/'+name+'()' for name, _ in METHODS)
TESTS = tuple('iosAppUITests/'+case.removesuffix('()') for case in CASES)
ANCHOR = '    override func setUpWithError() throws {\n'


def wrappers():
    return ''.join('    @MainActor\n    func '+name+'() throws {\n        try '+original+'()\n    }\n\n'
                   for name, original in METHODS)


def extend_driver(driver):
    current.require('testPIN30Sample' not in driver, 'No reused or duplicate stress driver')
    return pin01_copy.once(driver, ANCHOR, wrappers()+ANCHOR)


def restore_driver(driver):
    return pin01_copy.once(driver, wrappers()+ANCHOR, ANCHOR)


def renderer_for_profile(profile, source, runtime, device):
    pair = observed.original.motion.pair
    current.require((profile, source, runtime, device) == (PROFILE, SOURCE, pair.RUNTIME, pair.DEVICE),
                    'Exact fixed stress source/profile/runtime/device required')
    full = observed.renderer_for_profile(observed.PROFILE, SOURCE, runtime, device)
    def render(sources, digests, *, arm):
        current.require(arm in ARMS, 'Only original or fully observed stress arms')
        plan = pair.render(sources, digests, arm='original') if arm == 'original' else full(sources, digests, arm='compose')
        plan[pin02_capture.DRIVER] = extend_driver(plan[pin02_capture.DRIVER])
        return plan
    return render


def test_command(base, result):
    # Ordinary output identifies actual case starts; never an iteration/retry flag.
    return [*(a for a in base if a != '-quiet'), 'test-without-building', '-resultBundlePath', str(result),
            *('-only-testing:'+test for test in TESTS)]


def verify_results(summary, tests, simulator_id, code):
    require = current.require
    require(type(code) is int and code in (0, 65), 'Actual native pass or preserved exit65 required')
    for key in ('totalTestCount', 'passedTests', 'failedTests', 'skippedTests', 'expectedFailures'):
        require(type(summary.get(key)) is int and 0 <= summary[key] <= len(CASES), 'Exact bounded stress counts')
    require(summary['totalTestCount'] == len(CASES) == 10 and summary['skippedTests'] ==
            summary['expectedFailures'] == 0 and summary['passedTests']+summary['failedTests'] == 10,
            'All ten predeclared samples execute; no skips or expected failures')
    configurations, devices = summary.get('devicesAndConfigurations', []), tests.get('devices', [])
    require(len(configurations) == len(devices) == len(tests.get('testPlanConfigurations', [])) == 1,
            'Exactly one owned execution configuration per arm')
    for device in (configurations[0].get('device', {}), devices[0]):
        require(device.get('deviceId') == simulator_id and device.get('architecture') == 'arm64' and
                device.get('platform') == 'iOS Simulator' and device.get('modelName') == 'iPhone 16 Pro' and
                device.get('osVersion') == '18.5', 'Exact actual owned ARM64 Simulator required')
    def visit(nodes):
        return [case for node in nodes for case in
                ([node] if node.get('nodeType') == 'Test Case' else visit(node.get('children', [])))]
    nodes = visit(tests.get('testNodes', []))
    require(len(nodes) == 10 and {n.get('nodeIdentifier') for n in nodes} == set(CASES) and
            all(n.get('result') in ('Passed', 'Failed') and type(n.get('durationInSeconds')) in (int, float)
                and 0 < n['durationInSeconds'] < TEST_TIMEOUT_SECONDS for n in nodes),
            'Every unique sample has an actual positive-duration executed outcome')
    methods = {n['nodeIdentifier']: n['result'] for n in nodes}
    passed = sum(v == 'Passed' for v in methods.values())
    require(passed == summary['passedTests'] and (code == 0) == (passed == 10) and
            summary.get('result') == ('Passed' if passed == 10 else 'Failed'), 'All original outcomes agree')
    return methods


def failed_method_exports(result, destination, outcomes):
    current.require(set(outcomes) == set(CASES) and all(v in ('Passed', 'Failed') for v in outcomes.values()),
                    'Only exact original stress failures may export')
    return [(destination/('failed-%02d' % index), ['export', 'attachments', '--path', str(result),
             '--output-path', str(destination/('failed-%02d' % index)), '--test-id', method])
            for index, method in enumerate(sorted(k for k,v in outcomes.items() if v == 'Failed'), 1)]


retain_companion = observed.retain_companion
retain_root_companion = observed.retain_root_companion
retain_motion_companion = observed.retain_motion_companion


def retain_baseline_failures(command, result, dest, row):
    # Original XCTest screenshots/activities, not an invented application ACK.
    attachments = dest/'attachments'; attachments.mkdir()
    rc, helptext = command(['/usr/bin/xcrun','xcresulttool','help','export','attachments'], 'original-attachments-help')
    current.require(rc == 0, 'Installed original attachment exporter required')
    public_attachments.verify_export_help(helptext)
    exports = failed_method_exports(result, attachments, row['methods'])
    row['attachment_test_ids'] = [argv[-1] for _,argv in exports]
    for index,(directory,argv) in enumerate(exports,1):
        directory.mkdir()
        rc,_ = command(['/usr/bin/xcrun','xcresulttool',*argv], 'original-attachments-%02d' % index)
        current.require(rc == 0, 'Every exact original failed-method attachment retained')


def verify_commands(state, row):
    arm = row['arm']
    for suffix, code, deadline in (('build', 0, 2700), ('tests', row['test_exit'], TEST_TIMEOUT_SECONDS),
                                   ('gradle-stop', 0, 120)):
        commands = [c for c in state['commands'] if c.get('label') == arm+'-'+suffix]
        current.require(len(commands) == 1 and commands[0].get('exit_code') == code and
                        commands[0].get('timeout_seconds') == deadline, 'One original bounded command per arm/stage')
        if suffix == 'tests':
            argv = commands[0]['command']
            current.require([a for a in argv if a.startswith('-only-testing:')] == ['-only-testing:'+t for t in TESTS]
                and argv.count('test-without-building') == 1 and '-quiet' not in argv and
                not any(a.startswith(('-skip-testing', '-test-iterations', '-retry-tests')) for a in argv),
                'Exactly ten predeclared samples; no iterations, retries or skips')


def accept_native_export(out, state):
    require = current.require
    pair = observed.original.motion.pair
    require(len(state['arms']) == 1 and state['arms'][0]['arm'] in ARMS, 'One explicit matrix arm per runner')
    require(state['original_inputs'].get(observed.original.text.NAME) == observed.original.text.NAME_SHA256,
            'Exact original public Name source required')
    row = state['arms'][0]; arm = row['arm']; dest = out/arm
    renderer = renderer_for_profile(PROFILE, SOURCE, pair.RUNTIME, pair.DEVICE)
    if arm == 'compose':
        # Retain ALL existing source/product/result/failed-only/native/privacy/
        # root/frame/motion/text/attachment/lifecycle/query/cleanup gates.
        observed.original.motion.boundary.accept_observation_export(out, state, profile=PROFILE,
            renderer=renderer, result_verifier=verify_results, command_verifier=verify_commands)
        observed.original.verify_companion_files(out,state)
        observed.verify_failed_queries(out,state)
    else:
        require((state['profile'],state['source'],state['source_tree'],state['runtime'],state['device'],
                 state['xcode'],state['sdk'],state['debug_bundle']) ==
                (PROFILE,SOURCE,TREE,pair.RUNTIME,pair.DEVICE,'Xcode 26.3\nBuild version 17C529\n','26.2','me.parlor.ios.debug'),
                'Exact original baseline source/toolchain/product binding')
        require(row['build_exit'] == 0 and row['cleanup'] == 'PASS' and row['result'] in
                ('JOURNEYS_PASSED','JOURNEYS_FAILED'), 'Actual baseline build, outcomes and cleanup')
        for path,digest in pair.INPUTS.items():
            require(state['original_inputs'].get(path) == digest, 'Original baseline input identity')
        plan = renderer({p:pair.fixture(p) for p in pair.MODIFIED},pair.INPUTS,arm=arm)
        expected = state['original_inputs'] | {p:hashlib.sha256(b.encode()).hexdigest() for p,b in plan.items()}
        require(current.synthetic.load(dest/'inputs.json') == expected, 'Original source plus ONLY the ten wrapper methods')
        require(verify_results(current.synthetic.load(dest/'summary.json'), current.synthetic.load(dest/'tests.json'),
                              row['simulator'],row['test_exit']) == row['methods'], 'All original baseline outcomes retained')
        require(current.synthetic.load(dest/'product.json') and hashlib.sha256((dest/'product.json').read_bytes()).hexdigest() ==
                row['product_manifest_sha256'], 'Actual original built product custody')
        require(not any(dest.glob('capture-*.json')) and not any(row.get(k) for k in
                ('captures','companion_captures','root_companion_captures','motion_companion_captures')),
                'No invented baseline observer ACK or capture')
        require(not any(path for prefix in ('native-','public-text-','public-lifecycle-','input-attachment-','catalog-motion-')
                        for path in dest.glob(prefix+'*.json')), 'Baseline cannot retain invented native companion files')
        failures = sorted(k for k,v in row['methods'].items() if v == 'Failed')
        require(row.get('attachment_test_ids',[]) == failures and
                len(list((dest/'attachments').glob('failed-*'))) == len(failures),
                'Original failed samples retain their own actual XCTest attachments')
        cleanup = current.synthetic.load(dest/'cleanup.json')
        require(cleanup.get('gradle_exit') == 0 and all(cleanup.get(k,{}).get('result') == 'PASS' for k in
                ('workers_before','workers_after','simulator')) and cleanup['simulator'].get('udid') == row['simulator'],
                'Owned original workers and device retired')
        verify_commands(state,row)
        require(state['commands'] and all(c.get('status') == 'EXITED' and c.get('direct_child_reaped') and
                not c.get('signals') for c in state['commands']), 'Every baseline child exited/reaped without interruption')
    log = frozen_sources._read(dest/'test.log', 4*1024*1024)
    starts = re.findall(rb"Test Case '-\[iosAppUITests\.([A-Za-z0-9_]+) ([A-Za-z0-9_]+)\]' started\.",log)
    require(tuple(c.decode()+'/'+m.decode()+'()' for c,m in starts) == CASES, 'Each predeclared sample executes exactly once, in order')
    receipt = dict(arm=arm,methods=row['methods'],planned_pairs=5,maximum_public_flows=60,
                   actual_completed_flows=60 if row['test_exit'] == 0 else None,
                   all_failed_samples_retained=True,application_root_cause=False,shipping_qualified=False,
                   native_timing_neutrality=False,full_desktop60_journey_neutrality=False,physical_verified=False)
    target = out/'stress-observations.json'
    if target.exists():require(current.synthetic.load(target) == receipt, 'Exact original saved stress receipt')
    else:
        with target.open('x') as f:json.dump(receipt,f,indent=2)
    return RESULT
