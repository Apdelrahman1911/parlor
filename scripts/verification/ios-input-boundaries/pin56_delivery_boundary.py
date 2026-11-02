"""Narrow prepared discriminator: original callback threads and failure-only owned focus state.

Never a shipping fix, replay, or claim that native delivery is the application cause.
"""
import hashlib
import json
import math
import re
from pathlib import Path

import pin55_first_responder as prior
import frozen_sources
import pin02_capture
import run_pin03

base, original = prior.base, prior.original

SOURCE, TREE, RUNTIME, DEVICE = prior.SOURCE, prior.TREE, prior.RUNTIME, prior.DEVICE
PROFILE = 'original-delivery-and-current-focus-boundary'
ARMS = prior.ARMS
CONTROL_CASE = 'IOSAppLaunchUITests/testAPIN56DeliveryStateCapability()'
CASES = (*prior.original.QUERY_CASES, CONTROL_CASE)
FAILURE = 'PIN56 intentional failure after original public name input; current focus exporter control only'
TEST_TIMEOUT_SECONDS = prior.TEST_TIMEOUT_SECONDS
RESULT = 'ORIGINAL_DELIVERY_AND_CURRENT_FOCUS_BOUNDARY_COMPLETE_NOT_QUALIFICATION'
SCOPE = ('Original Arabic/English once then exporter; callback thread before old main-thread filter, '
         'post-failure owned responder/window flags and original snapshot kind; no new hook, '
         'input, thread, focus repair, timeout, Kotlin change or qualification')
require, load = prior.require, prior.load
runtime, MAX_BYTES = prior.runtime, prior.MAX_BYTES
TEMPLATE = Path(__file__).with_name('PIN56DeliveryObservation.swift.in')
TEMPLATE_SHA256 = '33a096cd2e94e7a6bd532bbb13601a89fbd648d88ca68a0514a769bb3c13d437'
PREREQUISITE = Path(__file__).resolve().parents[3]/'docs/audits/2026-11-02-IOS-ORIGINAL-FIRST-RESPONDER-ACCEPTANCE.json'
PREREQUISITE_SHA256 = 'e122ca11e90b888cc4c6d701c4ae4d5b3b5abe342a0f64343cd5d306f33715a0'


def observation_source():
    raw = prior.frozen_sources._read(TEMPLATE, 524288)
    require(hashlib.sha256(raw).hexdigest() == TEMPLATE_SHA256, 'Exact reviewed bounded callback observer')
    return raw.decode()


def require_capability():
    prior.require_capability()
    raw = prior.frozen_sources._read(PREREQUISITE, 65536)
    require(hashlib.sha256(raw).hexdigest() == PREREQUISITE_SHA256, 'Exact independently accepted original responder receipt')
    value = load(PREREQUISITE)
    require(value['run'] == 41515928915 and value['harness'] == '3b5c138383295e432637944c1a5ee73991537e6b' and
        value['workflow_conclusion'] == 'success' and value['collection_admitted'] is True and
        value['original_source'] == SOURCE and value['original_tree'] == TREE and
        value['original_inputs'] == 1214 and value['supporting_inputs'] == 525 and
        value['actual_mac_controls'] == 10 and value['skipped_controls'] == 0 and
        value['actual_methods'] == {prior.CASES[0]: 'Passed', prior.CASES[1]: 'Passed', prior.CONTROL_CASE: 'Failed'} and
        value['artifact_sha256'] == 'd8319ea47c6a3cf8a8bd8ffd0f2ebf49e9fa92b151ef109ff2e1fabf435c9f06' and
        value['control_native_insert_pairs'] == 10 and value['control_focus']['editorsAlive'] == [True] and
        value['control_focus']['parentUntouched'] and value['control_focus']['leafHookIntact'] and
        any(r[1] == 1 and r[3] == 1 for r in value['control_focus']['rows']) and
        value['owned_cleanup'] == 'PASS' and all(value[k] is False for k in
        ('application_root_cause', 'shipping_qualified', 'physical_verified')),
        'Actual unprimed originals and responder capability, not application qualification')


def delivery_source():
    source = prior.delivery_source()
    source = prior.base.pin01_copy.once(source,
        '    var onOwnedEditor: ((AnyObject, AnyClass) -> Void)?',
        '    var onOriginalCall: ((AnyObject, Int, Int, Bool) -> Void)?\n'
        '    var onOwnedEditor: ((AnyObject, AnyClass) -> Void)?')
    return prior.base.pin01_copy.once(source,
        '        guard Thread.isMainThread, armed() else { return }\n',
        '        onOriginalCall?(object, kind, phase, flag) // No private argument inspection; original forwarding unchanged.\n'
        '        guard Thread.isMainThread, armed() else { return }\n')


def focus_source():
    source = prior.base.pin01_copy.once(prior.focus_source(),
        '    private let armed: () -> Bool',
        '    var onOriginalCall: ((AnyObject, Int, Int, Bool) -> Void)?\n'
        '    private let armed: () -> Bool')
    return prior.base.pin01_copy.once(source,
        '        guard Thread.isMainThread, armed(), let index = editors.firstIndex(where: { $0.object === object }) else { return }',
        '        onOriginalCall?(object, 8, phase, result) // Observe the existing call before its old main-thread filter.\n'
        '        guard Thread.isMainThread, armed(), let index = editors.firstIndex(where: { $0.object === object }) else { return }')


def renderer_for_profile(profile, source, os, device):
    require((profile, source, os, device) == (PROFILE, SOURCE, RUNTIME, DEVICE), 'Exact prepared delivery profile')
    original = prior.renderer_for_profile(prior.PROFILE, source, os, device)

    def render(sources, digests, *, arm):
        plan = original(sources, digests, arm=arm)
        owner = prior.base.pin01_copy.once(plan[prior.pin02_capture.CONTAINER], prior.delivery_source(), delivery_source())
        owner = prior.base.pin01_copy.once(owner, prior.focus_source(), focus_source())
        owner = prior.base.pin01_copy.once(owner, '    private var focus: PIN55FirstResponderObservation?',
            '    private var deliveryState: PIN56DeliveryObservation?\n    private var focus: PIN55FirstResponderObservation?')
        owner = prior.base.pin01_copy.once(owner, '        focus = PIN55FirstResponderObservation(armed: armed)',
            '        deliveryState = PIN56DeliveryObservation()\n'
            '        focus = PIN55FirstResponderObservation(armed: armed)\n'
            '        focus?.onOriginalCall = { [weak self] object, kind, phase, result in\n'
            '            self?.deliveryState?.observeOriginalCall(object, kind: kind, phase: phase, result: result)\n'
            '        }')
        owner = prior.base.pin01_copy.once(owner,
            '        delivery.onOwnedEditor = { [weak self] object, target in self?.focus?.bindVerifiedEditor(object, target: target) }',
            '        delivery.onOwnedEditor = { [weak self] object, target in\n'
            '            self?.deliveryState?.bindVerifiedEditor(object)\n'
            '            self?.focus?.bindVerifiedEditor(object, target: target)\n'
            '        }\n'
            '        delivery.onOriginalCall = { [weak self] object, kind, phase, result in\n'
            '            self?.deliveryState?.observeOriginalCall(object, kind: kind, phase: phase, result: result)\n'
            '        }')
        owner = prior.base.pin01_copy.once(owner, '        let frozenFocus = focus?.freeze() ?? [:]',
            '        let frozenFocus = focus?.freeze() ?? [:]\n        let frozenDeliveryState = deliveryState?.freeze() ?? [:]')
        owner = prior.base.pin01_copy.once(owner, '["schema": 55, "token": token', '["schema": 56, "token": token')
        owner = prior.base.pin01_copy.once(owner, '"focus": frozenFocus]', '"focus": frozenFocus, "deliveryState": frozenDeliveryState]')
        owner = owner.replace('PARLOR_PIN55_', 'PARLOR_PIN56_').replace('parlor-pin55-', 'parlor-pin56-')
        owner = prior.base.pin01_copy.once(owner, prior.PROFILE, PROFILE) + '\n' + observation_source()
        driver = plan[prior.pin02_capture.DRIVER].replace('PIN55Configure', 'PIN56Configure').replace('PARLOR_PIN55_', 'PARLOR_PIN56_')
        driver = prior.base.pin01_copy.once(driver, prior.PROFILE, PROFILE)
        driver = prior.base.pin01_copy.once(driver, prior.CONTROL_CASE.split('/')[1].removesuffix('()'),
            CONTROL_CASE.split('/')[1].removesuffix('()'))
        driver = prior.base.pin01_copy.once(driver, prior.FAILURE, FAILURE)
        # Both attributes belong to the snapshot already returned by the unchanged
        # original query. No additional remote query, predicate or timeout.
        driver = prior.base.pin01_copy.once(driver, '            let value = snapshot?.value as? String',
            '            let returnedValue = snapshot?.value\n'
            '            let value = returnedValue as? String\n'
            '            let snapshotIsTextView = snapshot.map { $0.elementType == .textView }\n'
            '            let valueCategory = returnedValue == nil ? 0 : (returnedValue is String ? 1 : (returnedValue is NSNumber ? 2 : 3))')
        driver = prior.base.pin01_copy.once(driver,
            '                    "snapshotPresent=\\(snapshot != nil), stringValue=\\(value != nil), " +',
            '                    "snapshotPresent=\\(snapshot != nil), stringValue=\\(value != nil), " +\n'
            '                    "snapshotIsTextView=\\(String(describing: snapshotIsTextView)), valueCategory=\\(valueCategory), " +')
        return plan | {prior.pin02_capture.CONTAINER: owner, prior.pin02_capture.DRIVER: driver}

    return render


def test_command(command, result):
    command = prior.test_command(command, result)
    return [a.replace(prior.CONTROL_CASE.removesuffix('()'), CONTROL_CASE.removesuffix('()')) for a in command]


def verify_results(summary, tests, simulator, code):
    # Reuse unchanged strict result validation without mutating prior globals.
    def rename(value):
        if isinstance(value, str):
            return value.replace(CONTROL_CASE, prior.CONTROL_CASE).replace(FAILURE, prior.FAILURE)
        if isinstance(value, list):
            return [rename(v) for v in value]
        if isinstance(value, dict):
            return {k: rename(v) for k, v in value.items()}
        return value
    methods = prior.verify_results(rename(summary), rename(tests), simulator, code)
    return {k.replace(prior.CONTROL_CASE, CONTROL_CASE): v for k, v in methods.items()}


def decode(raw, *, token, simulator, bundle, legacy, require_control=False):
    require(type(raw) is bytes and 0 < len(raw) <= MAX_BYTES, 'Bounded current-focus report')
    value = json.loads(raw, object_pairs_hook=prior.base.pin01_receipts.reject_duplicates,
        parse_constant=lambda _: (_ for _ in ()).throw(ValueError('Nonfinite current-focus evidence')))
    require(type(value) is dict and type(value.get('schema')) is int and value['schema'] == 56 and
        'deliveryState' in value, 'Exact new delivery schema')
    previous = {k: v for k, v in value.items() if k != 'deliveryState'}
    previous['schema'] = 55
    native = prior.decode(json.dumps(previous).encode(), token=token, simulator=simulator,
        bundle=bundle, legacy=legacy, require_control=require_control)
    state = value['deliveryState']
    require(type(state) is dict and set(state) == {'schema', 'capturedMs', 'rows', 'threads', 'editorsAlive',
        'current', 'invalid', 'overflow', 'scope'} and type(state['schema']) is int and state['schema'] == 56,
        'Exact private-free delivery keys')
    require(type(state['capturedMs']) in (float, int) and math.isfinite(state['capturedMs']) and
        legacy['installedMs'] <= state['capturedMs'] <= value['capturedMs'], 'Actual bounded capture clock')
    require(all(type(state[k]) is int and 0 <= state[k] <= 1000000 for k in ('invalid', 'overflow')) and
        type(state['threads']) is int and 0 <= state['threads'] <= 64 and
        type(state['rows']) is list and len(state['rows']) <= 4096, 'Bounded concurrent numeric intake')
    alive = state['editorsAlive']
    require(type(alive) is list and len(alive) <= 128 and all(type(v) is bool for v in alive) and
        len(alive) == len(native['focus']['editorsAlive']) and type(state['current']) is list and len(state['current']) == len(alive),
        'Owned editor ordinals and current-state count')
    expected_scope = 'Original call thread and post-failure owned-view state; not pre-failure ownership or text'
    require(state['scope'] == expected_scope, 'Exact limitation retained')
    for current in state['current']:
        require(type(current) is dict and type(current.get('available')) is bool, 'Owned UIKit state availability')
        if not current['available']:
            require(current == {'available': False, 'firstResponder': None, 'ancestors': []}, 'Absent views remain absent')
            continue
        require(set(current) == {'available', 'firstResponder', 'ancestors', 'truncated'} and
            type(current['firstResponder']) is bool and type(current['truncated']) is bool and
            type(current['ancestors']) is list and 1 <= len(current['ancestors']) <= 12, 'Bounded UIKit flags only')
        for i, ancestor in enumerate(current['ancestors']):
            require(type(ancestor) is list and len(ancestor) == 7 and type(ancestor[0]) is int and ancestor[0] == i and
                all(type(v) is int and v in (0, 1) for v in ancestor[1:]), 'No private ancestor values')
    now = legacy['installedMs']
    threads, stacks = set(), {}
    for row in state['rows']:
        require(type(row) is list and len(row) == 7 and all(type(n) in (float, int) and math.isfinite(n) for n in row),
            'Only numeric original callback flags')
        at, main, thread, kind, phase, editor, result = row
        require(now <= at <= state['capturedMs'] and main in (0, 1) and phase in (0, 1) and
            thread == int(thread) and 1 <= thread <= state['threads'] and editor == int(editor) and 1 <= editor <= len(alive) and
            kind in (1, 2, 3, 4, 5, 6, 8, 9) and result in (0, 1) and (result == 0 or (phase == 1 and kind in (8, 9))),
            'Actual ordered original callback clocks and flags')
        now = at
        threads.add(thread)
        if require_control:
            stack = stacks.setdefault((thread, editor), [])
            if phase == 0:
                stack.append(kind)
            else:
                require(stack and stack.pop() == kind, 'Positive original callback return pair')
    require(threads == set(range(1, state['threads'] + 1)), 'Exact bounded thread ordinals, no ports exported')
    if require_control:
        require(state['invalid'] == state['overflow'] == 0 and alive == [True] and
            state['current'][0]['available'] and state['current'][0]['firstResponder'] and
            state['current'][0]['ancestors'][0][-1] == 1 and not any(stacks.values()) and
            any(r[3:5] == [8, 1] and r[6] == 1 for r in state['rows']) and
            any(r[3:5] == [1, 1] for r in state['rows']), 'Actual focused-live native delivery control')
    return {'sha256': hashlib.sha256(raw).hexdigest(), 'prior': native, 'deliveryState': state,
        'post_failure_state_only': True, 'application_root_cause': False, 'shipping_qualified': False}


def failed_method_exports(result,destination,outcomes):
    require(set(outcomes)==set(CASES),'All actual new control and original outcomes')
    return [(destination/('failed-%02d'%i),['export','attachments','--path',str(result),'--output-path',str(destination/('failed-%02d'%i)),'--test-id',c])
        for i,c in enumerate(sorted(k for k,v in outcomes.items() if v=='Failed'),1)]

def retain_companion(sandbox,destination,token,simulator,bundle,legacy):
    p=sandbox/'tmp'/('parlor-pin56-'+token)/'delivery.json'
    require(not p.is_symlink() and p.resolve(strict=True)==p.absolute(),'Owned new focus evidence file')
    raw=base.run_pin03.read_capture(p,maximum=MAX_BYTES)
    report=decode(raw,token=token,simulator=simulator,bundle=bundle,legacy=legacy)
    with (destination/('native-text-'+token+'.json')).open('xb') as f:f.write(raw)
    return report

def accept_native_export(out, state):
    require_capability()
    require((state['source'],state['source_tree'],state['profile'],state['runtime'],state['device'],state['xcode'],state['sdk'],state['debug_bundle']) ==
            (SOURCE,TREE,PROFILE,RUNTIME,DEVICE,'Xcode 26.3\nBuild version 17C529\n','26.2','me.parlor.ios.debug') and
            len(state['arms']) == 1, 'Exact source/compiler/Debug ownership')
    row = state['arms'][0]; d = out/'compose'
    require(row['arm']=='compose' and row['build_exit'] == 0 and row['cleanup'] == 'PASS' and
            row['result'] == ('JOURNEYS_PASSED' if row['test_exit']==0 else 'JOURNEYS_FAILED'), 'Real build/failure/cleanup')
    require(verify_results(load(d/'summary.json'), load(d/'tests.json'), row['simulator'], row['test_exit']) == row['methods'] and
            row['attachment_test_ids'] == sorted(k for k,v in row['methods'].items() if v=='Failed'), 'Actual original control assertion/attachments')
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
        if owners[token]==CONTROL_CASE:
            decode(frozen_sources._read(d/('native-text-'+token+'.json'),MAX_BYTES),token=token,simulator=row['simulator'],bundle=state['debug_bundle'],legacy=legacy,require_control=True)
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
    for stage, code, budget in (('build',0,2700),('tests',row['test_exit'],900),('gradle-stop',0,120)):
        calls = [c for c in state['commands'] if c['label'] == 'compose-'+stage]
        require(len(calls) == 1 and calls[0]['exit_code'] == code and calls[0]['timeout_seconds'] == budget,
                'One actual command per original bounded stage')
        if stage != 'tests': continue
        argv = calls[0]['command']
        require([a for a in argv if a.startswith('-only-testing:')] == ['-only-testing:iosAppUITests/'+case.removesuffix('()') for case in CASES] and
                argv.count('test-without-building') == argv.count('/usr/bin/xcodebuild') == 1 and '-quiet' not in argv and
                not any(a.startswith(('-skip-testing','-retry-tests','-test-iterations','-run-tests-until-failure')) for a in argv),
                'One exact control, no retry/skip')
        for flag,value in (('-project','iosApp/iosApp.xcodeproj'),('-scheme','iosApp'),('-configuration','Debug'),
            ('-sdk','iphonesimulator'),('-destination','platform=iOS Simulator,id='+row['simulator']),
            ('-parallel-testing-enabled','NO'),('-maximum-concurrent-test-simulator-destinations','1')):
            require(argv.count(flag) == 1 and argv[argv.index(flag)+1] == value, 'Exact owned Debug command flags')
    require(state['build_profile']['test_timeout_seconds'] == 900, 'Original process limit unchanged')
    starts = re.findall(rb"Test Case '-\[iosAppUITests\.([A-Za-z0-9_]+) ([A-Za-z0-9_]+)\]' started\.",frozen_sources._read(d/'test.log',4194304))
    require(tuple(c.decode()+'/'+m.decode()+'()' for c,m in starts) == CASES, 'Exact original Arabic, English then new responder control order required')
    receipt = dict(root_capability_run=41497962566,original_focus_capability_run=41515928915,prerequisite_failure_run=41502134475,root_queries_added=False,methods=row['methods'],failed_sample_tokens=owners,native=reports,application_root_cause=False,
                   shipping_qualified=False,physical_verified=False,native_timing_neutrality=False)
    target = out/'delivery-focus-boundary.json'
    if target.exists(): require(load(target) == receipt, 'Exact saved receipt')
    else:
        with target.open('x') as output: json.dump(receipt,output,indent=2)
    return RESULT
