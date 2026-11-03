"""Bounded original-input intermittency reproduction using the already verified adapter.

No new native probe or successful control replay. Ten fixed wrapper samples retain
all failures and original actions/assertions/deadlines; never retry-for-green.
"""
import hashlib
import json
import re
from pathlib import Path

import frozen_sources
import pin02_capture
import run_pin03
import pin30_public_session_stress as stress
import pin56_delivery_boundary as p

SOURCE, TREE, RUNTIME, DEVICE, ARMS = p.SOURCE, p.TREE, p.RUNTIME, p.DEVICE, p.ARMS
PROFILE = 'fixed-original-delivery-focus-intermittency'
CASES, TESTS = stress.CASES, stress.TESTS
TEST_TIMEOUT_SECONDS = stress.TEST_TIMEOUT_SECONDS  # Aggregate cap for five fixed pairs; original per-assertion deadlines unchanged.
RESULT = 'FIXED_ORIGINAL_DELIVERY_FOCUS_INTERMITTENCY_COMPLETE_NOT_QUALIFICATION'
SCOPE = 'Five fixed original AR/EN pairs; reuse verified native adapter; no new probe, control, retry, repair, assertion changes or qualification'
require, load, runtime, MAX_BYTES = p.require, p.load, p.runtime, p.MAX_BYTES
decode, retain_companion = p.decode, p.retain_companion
PREREQUISITE = Path(__file__).resolve().parents[3]/'docs/audits/2026-11-02-IOS-DELIVERY-FOCUS-CAPABILITY.json'
PREREQUISITE_SHA256 = '6ee0b15e862ce285e34e583d94dce1eae92fb637e3cf9769e85c1cf85e008007'


def require_capability():
    p.require_capability()
    raw = frozen_sources._read(PREREQUISITE, 65536)
    require(hashlib.sha256(raw).hexdigest() == PREREQUISITE_SHA256, 'Frozen independent native capability receipt')
    v = load(PREREQUISITE)
    require(v['run'] == 41520882626 and v['harness'] == 'b4d2ea69dae5ea18f14d6220c1818a6dbf9c6517' and
            v['workflow_conclusion'] == 'success' and v['collection_admitted'] is True and
            (v['original_source'], v['original_tree']) == (SOURCE,TREE) and
            v['actual_mac_controls'] == 12 and v['skipped_controls'] == 0 and v['original_inputs'] == 1214 and
            v['supporting_inputs'] == 533 and v['owned_cleanup'] == 'PASS' and
            v['actual_methods'] == {p.CASES[0]:'Passed',p.CASES[1]:'Passed',p.CONTROL_CASE:'Failed'} and
            v['control_native_insert_pairs'] == 10 and v['control_first_responder'] is True and
            v['control_editor_alive'] == [True] and v['control_invalid'] == v['control_overflow'] == 0 and
            all(v[k] is False for k in ('application_root_cause','shipping_qualified','physical_verified')),
            'Actual unchanged adapter capability, not application qualification')


def renderer_for_profile(profile, source, os, device):
    require((profile,source,os,device) == (PROFILE,SOURCE,RUNTIME,DEVICE), 'Exact declared intermittency source/runtime')
    base = p.renderer_for_profile(p.PROFILE, source, os, device)
    def render(sources,digests,*,arm):
        plan = base(sources,digests,arm=arm)
        plan[pin02_capture.DRIVER] = stress.extend_driver(plan[pin02_capture.DRIVER])
        return plan
    return render


def test_command(base,result):
    return [*(a for a in base if a != '-quiet'), 'test-without-building', '-resultBundlePath', str(result),
            *('-only-testing:'+test for test in TESTS)]

def verify_results(summary, tests, simulator_id, code):
    require = p.require
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
                device.get('platform') == 'iOS Simulator' and device.get('modelName') == 'iPhone 17 Pro' and
                device.get('osVersion') == '26.2', 'Exact actual owned ARM64 Simulator required')
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
    failures = summary.get('testFailures', [])
    require(type(failures) is list and len(failures) == summary['failedTests'] and
            {f.get('testIdentifierString') for f in failures} == {k for k,v in methods.items() if v == 'Failed'} and
            all(f.get('targetName') == 'iosAppUITests' and type(f.get('failureText')) is str and
                f['failureText'] for f in failures), 'Every actual assertion failure remains owned and visible')
    return methods


def failed_method_exports(result,destination,outcomes):
    require(set(outcomes)==set(CASES) and all(v in ('Passed','Failed') for v in outcomes.values()),'All ten original wrapper outcomes')
    return [(destination/('failed-%02d'%i),['export','attachments','--path',str(result),'--output-path',str(destination/('failed-%02d'%i)),'--test-id',c])
        for i,c in enumerate(sorted(k for k,v in outcomes.items() if v=='Failed'),1)]


def verify_commands(state, row):
    for stage, code, budget in (('build',0,2700),('tests',row['test_exit'],TEST_TIMEOUT_SECONDS),('gradle-stop',0,120)):
        calls = [c for c in state['commands'] if c['label'] == 'compose-'+stage]
        require(len(calls) == 1 and calls[0]['exit_code'] == code and calls[0]['timeout_seconds'] == budget,
                'One actual command per original bounded stage')
        if stage != 'tests': continue
        argv = calls[0]['command']
        require([a for a in argv if a.startswith('-only-testing:')] == ['-only-testing:iosAppUITests/'+case.removesuffix('()') for case in CASES] and
                argv.count('test-without-building') == argv.count('/usr/bin/xcodebuild') == 1 and '-quiet' not in argv and
                not any(a.startswith(('-skip-testing','-retry-tests','-test-iterations','-run-tests-until-failure')) for a in argv),
                'All ten fixed samples, no retry/skip')
        for flag,value in (('-project','iosApp/iosApp.xcodeproj'),('-scheme','iosApp'),('-configuration','Debug'),
            ('-sdk','iphonesimulator'),('-destination','platform=iOS Simulator,id='+row['simulator']),
            ('-parallel-testing-enabled','NO'),('-maximum-concurrent-test-simulator-destinations','1')):
            require(argv.count(flag) == 1 and argv[argv.index(flag)+1] == value, 'Exact owned Debug command flags')
    require(state['build_profile']['test_timeout_seconds'] == TEST_TIMEOUT_SECONDS, 'Aggregate five-pair process cap; original assertion deadlines unchanged')


def accept_native_export(out, state):
    require_capability()
    require((state['source'],state['source_tree'],state['profile'],state['runtime'],state['device'],state['xcode'],state['sdk'],state['debug_bundle']) ==
            (SOURCE,TREE,PROFILE,RUNTIME,DEVICE,'Xcode 26.3\nBuild version 17C529\n','26.2','me.parlor.ios.debug') and
            len(state['arms']) == 1, 'Exact source/compiler/Debug ownership')
    row = state['arms'][0]; d = out/'compose'
    require(row['arm']=='compose' and row['build_exit'] == 0 and row['cleanup'] == 'PASS' and
            row['result'] == ('JOURNEYS_PASSED' if row['test_exit']==0 else 'JOURNEYS_FAILED'), 'Real build/failure/cleanup')
    require(verify_results(load(d/'summary.json'), load(d/'tests.json'), row['simulator'], row['test_exit']) == row['methods'] and
            row['attachment_test_ids'] == sorted(k for k,v in row['methods'].items() if v=='Failed'), 'Actual original assertion failures and attachments')
    leaf = runtime.pair_control.leaf
    require(all(state['original_inputs'].get(p) == h for p,h in leaf.pair.INPUTS.items()), 'Original source binding')
    plan = renderer_for_profile(PROFILE,SOURCE,RUNTIME,DEVICE)(
        {p:leaf.pair.fixture(p) for p in leaf.pair.MODIFIED}, leaf.pair.INPUTS, arm='compose')
    require(load(d/'inputs.json') == state['original_inputs'] | {p:hashlib.sha256(v.encode()).hexdigest() for p,v in plan.items()},
            'Exact copied inputs, no original changes')
    product = frozen_sources._read(d/'product.json',2097152)
    require(hashlib.sha256(product).hexdigest() == row['product_manifest_sha256'] and load(d/'product.json'), 'Product custody')
    owners = runtime.pair_control.samples.requests_by_method(d,row['methods'])
    require(len(owners) == sum(v=='Failed' for v in row['methods'].values()), 'One actual acknowledged request per original failure')
    reports, captures = [], []
    for token in sorted(owners):
        raw = frozen_sources._read(d/('capture-'+token+'.json'),pin02_capture.MAX_BYTES)
        legacy = json.loads(raw)
        capture = run_pin03.decode_capture(raw,set(owners),row['simulator'],state['debug_bundle'])
        require(capture['snapshot'] == dict(schema=1,nativeMode=0,overflow=0,invalid=0,rows=[]) and
                capture['numeric_buffer_complete'] is False, 'ACK only; no fabricated state')
        captures.append(capture)
        reports.append(decode(frozen_sources._read(d/('native-text-'+token+'.json'),MAX_BYTES), token=token,
            simulator=row['simulator'],bundle=state['debug_bundle'],legacy=legacy,require_control=False))
    require(not row.get('root_companion_captures') and not row.get('motion_companion_captures'), 'No absent side-channel reports')
    require(captures == row.get('captures',[]) and reports == row.get('companion_captures',[]),
            'Exact retained same-PID evidence')
    for prefix in ('capture-','native-text-'):
        require({p.name for p in d.glob(prefix+'*.json')} == {prefix+t+'.json' for t in owners}, 'One file per real failure')
    for prefix in ('native-runloop-','native-touch-','native-only-','native-accessibility-','native-frame-','native-root-'):
        require(not any(d.glob(prefix+'*.json')), 'No invented absent observation')
    cleanup = load(d/'cleanup.json')
    require(cleanup['gradle_exit'] == 0 and cleanup['workers_before']['result'] == cleanup['workers_after']['result'] ==
            cleanup['simulator']['result'] == 'PASS' and cleanup['simulator']['udid'] == row['simulator'], 'Owned cleanup')
    require(all(c['status'] == 'EXITED' and c['direct_child_reaped'] and not c.get('signals') for c in state['commands']), 'Reaped children')
    verify_commands(state, row)
    starts = re.findall(rb"Test Case '-\[iosAppUITests\.([A-Za-z0-9_]+) ([A-Za-z0-9_]+)\]' started\.",frozen_sources._read(d/'test.log',4194304))
    require(tuple(c.decode()+'/'+m.decode()+'()' for c,m in starts) == CASES, 'All ten fixed original wrapper cases once in declared AR/EN order')
    receipt = dict(root_capability_run=41497962566,original_focus_capability_run=41515928915,delivery_capability_run=41520882626,prerequisite_failure_run=41502134475,root_queries_added=False,methods=row['methods'],failed_sample_tokens=owners,native=reports,application_root_cause=False,
                   shipping_qualified=False,physical_verified=False,native_timing_neutrality=False)
    target = out/'input-intermittency.json'
    if target.exists(): require(load(target) == receipt, 'Exact saved receipt')
    else:
        with target.open('x') as output: json.dump(receipt,output,indent=2)
    return RESULT
