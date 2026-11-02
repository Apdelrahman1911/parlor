"""Owned editor attachment and original-call text ledger, diagnostic copy only.

First execute one separately named public-name/export control. Never replay the
accepted original pair merely to hope for a failure. No shipping qualification.
"""
import hashlib
import json
import math
from pathlib import Path
import re

import frozen_sources
import pin01_copy
import pin01_receipts
import pin02_capture
import pin44_original_runtime as runtime
import pin47_failure_runloop as prior
import run_pin03

SOURCE, TREE, RUNTIME, DEVICE = runtime.SOURCE, runtime.TREE, runtime.RUNTIME, runtime.DEVICE
PROFILE = 'isolated-owned-native-text-export'
ARMS = ('compose',)
CASE = 'IOSAppLaunchUITests/testPIN51OwnedTextExportCapability()'
CASES = (CASE,)
TEST_TIMEOUT_SECONDS = 900
RESULT = 'OWNED_NATIVE_TEXT_EXPORT_COMPLETE_NOT_QUALIFICATION'
SCOPE = ('One separately named public Join/name/export control, original helpers and 10s guards; '
         'owned native attachment/mutator/resign calls, inherited become untouched, failure-only numeric export; '
         'no accepted original AR/EN pair replay, repair, retry, networking, causality or shipping qualification')
FAILURE = 'PIN51 intentional failure after original public name input; exporter control only'
HERE = Path(__file__).resolve().parent
MAX_BYTES = 524288
PINS = {'PIN02Capture.swift.in': '4a3814846d15f854f2e7ad2510fa04795a220ed55f0d65793ee185e8d5716433', 'PIN46MainRunLoop.swift.in': '6dec78a0716ad3c8841f884f7e076294e95b00c567124d9f0ab0bdc3a1e82aad', 'PIN51OwnedTextDelivery.swift.in': '519ea395c579b6e86f1ed333ff208be74b4c66fb52a1c3991db09653668dc846', 'PIN51OwnedTextObservation.swift.in': 'fba462c5290cc6bc294deebaa714fec6c7fb73de000a609514c838edee520e58'}
SENTINEL = '''
    @MainActor
    func testPIN51OwnedTextExportCapability() throws {
        continueAfterFailure = false
        let app = XCUIApplication()
        PIN51Configure(app)
        app.launchArguments += ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        defer { app.terminate() }
        XCTAssertTrue(app.staticTexts["parlor-home-brand"].waitForExistence(timeout: 30))
        try tapSetupButton(app, prefix: "Open Egyptian Dominoes")
        try tapPublicSetupTransition(app,
            from: "Enter the host's room code to join their table.",
            to: "Continue and retry the real local-network operation.")
        try tapSetupButton(app, prefix: "Continue and retry the real local-network operation.")
        let name = app.textViews.firstMatch
        XCTAssertTrue(name.waitForExistence(timeout: 10))
        try tapSettledControl(name)
        XCTAssertTrue(app.keyboards.firstMatch.waitForExistence(timeout: 10))
        let introduction = dismissKeyboardIntroduction(app)
        name.typeText("Table Test")
        assertTextValue(name, in: app, equals: "Table Test", introduction: introduction)
        name.typeText("\\n")
        XCTFail("PIN51 intentional failure after original public name input; exporter control only")
    }

'''


def require(value, message):
    if not value:
        raise ValueError(message)


def load(path):
    return json.loads(frozen_sources._read(path, 4194304), object_pairs_hook=pin01_receipts.reject_duplicates,
                      parse_constant=lambda _: (_ for _ in ()).throw(ValueError('Nonfinite data')))


def template(name):
    raw = frozen_sources._read(HERE/name, MAX_BYTES)
    require(hashlib.sha256(raw).hexdigest() == PINS[name], 'Exact owned text source '+name)
    return raw.decode()


def renderer_for_profile(profile, source, os, device):
    require((profile, source, os, device) == (PROFILE, SOURCE, RUNTIME, DEVICE), 'Exact original source/runtime')
    base = prior.renderer_for_profile(prior.QUERY_PROFILE, source, os, device)
    def render(sources, digests, *, arm):
        old = base(sources, digests, arm=arm)
        container = pin01_copy.once(sources[pin02_capture.CONTAINER],
            '    private let contentController: UIViewController\n',
            '    private let contentController: UIViewController\n#if DEBUG && targetEnvironment(simulator)\n'
            '    private var pin51Text: PIN51OwnedTextObservation?\n#endif\n')
        container = pin01_copy.once(container, '        contentController.didMove(toParent: self)\n',
            '        contentController.didMove(toParent: self)\n#if DEBUG && targetEnvironment(simulator)\n'
            '        pin51Text = PIN51OwnedTextObservation()\n#endif\n')
        container = pin01_copy.once(container, '    override var childForStatusBarStyle:',
            '#if DEBUG && targetEnvironment(simulator)\n'
            '    override func viewDidAppear(_ animated: Bool) {\n'
            '        super.viewDidAppear(animated)\n'
            '        pin51Text?.bindOriginalInputOnce(view: contentController.view!)\n'
            '    }\n#endif\n\n    override var childForStatusBarStyle:')
        container += '\n'+'\n'.join(template(name) for name in PINS)
        driver = old[pin02_capture.DRIVER].replace('PIN47Configure', 'PIN51Configure').replace('PARLOR_PIN47_', 'PARLOR_PIN51_')
        # The owner and driver use the same explicit profile, never inherited timing-profile labels.
        driver = pin01_copy.once(driver, prior.PROFILE, PROFILE)
        driver = pin01_copy.once(driver, '    private struct KeyboardCopy {', SENTINEL+'    private struct KeyboardCopy {')
        return sources | {pin02_capture.CONTAINER: container, pin02_capture.DRIVER: driver}
    return render


def test_command(base, result):
    return [*(a for a in base if a != '-quiet'), 'test-without-building', '-resultBundlePath', str(result),
            '-only-testing:iosAppUITests/'+CASE.removesuffix('()')]


def verify_results(summary, tests, simulator, code, *, require_exporter=False):
    require(type(code) is int and code == 65, 'Exporter must retain real XCTest65')
    require(all(type(summary.get(k)) is int and summary[k] == n for k,n in
                (('totalTestCount',1),('passedTests',0),('failedTests',1),('skippedTests',0),('expectedFailures',0)))
            and summary.get('result') == 'Failed', 'One actual failed control, no skipped/expected failure')
    configs, devices = summary.get('devicesAndConfigurations',[]), tests.get('devices',[])
    require(len(configs) == len(devices) == len(tests.get('testPlanConfigurations',[])) == 1, 'One configuration')
    for d in (configs[0].get('device',{}),devices[0]):
        require((d.get('deviceId'),d.get('architecture'),d.get('platform'),d.get('modelName'),d.get('osVersion')) ==
                (simulator,'arm64','iOS Simulator','iPhone 17 Pro','26.2'), 'Exact original runtime/model')
    nodes = prior.visit(tests.get('testNodes',[]))
    require(len(nodes) == 1 and nodes[0].get('nodeIdentifier') == CASE and nodes[0].get('result') == 'Failed' and
            type(nodes[0].get('durationInSeconds')) in (int,float) and math.isfinite(nodes[0]['durationInSeconds']) and
            0 < nodes[0]['durationInSeconds'] < 900, 'Exact executed method and real duration')
    failures = summary.get('testFailures',[])
    require(failures and all(f.get('testIdentifierString') == CASE for f in failures), 'No lost/foreign failures')
    if require_exporter:
        require(len(failures) == 1 and FAILURE in failures[0].get('failureText',''),
                'Must fail only at declared exporter, not input/startup')
    return {CASE:'Failed'}


def failed_method_exports(result, destination, outcomes):
    require(outcomes == {CASE:'Failed'}, 'One actual control failure')
    return [(destination/'failed-01', ['export','attachments','--path',str(result),
              '--output-path',str(destination/'failed-01'),'--test-id',CASE])]


def decode(raw, *, token, simulator, bundle, legacy, require_complete=False):
    require(type(raw) is bytes and 0 < len(raw) <= MAX_BYTES, 'Bounded native text evidence')
    v = json.loads(raw, object_pairs_hook=pin01_receipts.reject_duplicates,
                   parse_constant=lambda _: (_ for _ in ()).throw(ValueError('Nonfinite native text evidence')))
    require(type(v) is dict and set(v) == {'schema','token','simulator','bundle','process','capturedMs',
        'viewCount','eligibleInputViews','treeComplete','deliveryBound','inputBindingAttempted','delivery','runloop'},
        'Exact numeric/private-free evidence schema')
    require(type(v['schema']) is int and v['schema'] == 51 and type(v['process']) is int and v['process'] > 0 and
            (v['token'],v['simulator'],v['bundle'],v['process']) == (token,simulator,bundle,legacy['process']), 'Exact failure PID/device/bundle/token')
    require(type(v['capturedMs']) in (int,float) and math.isfinite(v['capturedMs']) and
            legacy['installedMs'] <= v['capturedMs'] <= legacy['capturedMs'], 'Same-PID original capture clocks')
    require(type(v['viewCount']) is int and 0 <= v['viewCount'] <= 256 and type(v['eligibleInputViews']) is int and
            0 <= v['eligibleInputViews'] <= 256 and all(type(v[k]) is bool for k in
            ('treeComplete','deliveryBound','inputBindingAttempted')), 'Bounded attachment status')
    d = v['delivery']
    require(type(d) is dict and set(d) == {'schema','capturedMs','installed','installation','overflow','attachments',
            'rows','inheritedResponderUntouched'} and type(d['schema']) is int and d['schema'] == 51 and
            type(d['capturedMs']) in (int,float) and math.isfinite(d['capturedMs']) and
            legacy['installedMs'] <= d['capturedMs'] <= v['capturedMs'], 'Exact delivery schema and clock')
    require(type(d['installed']) is bool and type(d['inheritedResponderUntouched']) is bool and
            type(d['installation']) is list and len(d['installation']) == 3 and
            all(type(n) is int and n in (0,1) for n in d['installation']) and
            type(d['overflow']) is int and 0 <= d['overflow'] <= 1000000 and
            type(d['attachments']) is int and 0 <= d['attachments'] <= 128 and
            type(d['rows']) is list and len(d['rows']) <= 4096, 'Bounded native calls/status')
    previous = legacy['installedMs']
    for r in d['rows']:
        require(type(r) is list and len(r) == 5 and all(type(n) in (int,float) and math.isfinite(n) for n in r),
                'Only finite numeric native rows')
        now, kind, phase, ordinal, flag = r
        require(previous <= now <= d['capturedMs'] and kind in (1,2,3,4,5,6,7,9) and phase in (0,1) and
                ordinal == int(ordinal) and 1 <= ordinal <= d['attachments'] and flag in (0,1) and
                (flag == 0 or kind == 9 and phase == 1), 'Ordered actual call/attachment/return bits')
        previous = now
    runloop = prior.runloop.decode(json.dumps(v['runloop']).encode())
    require(legacy['installedMs'] <= runloop['capturedMs'] <= v['capturedMs'], 'Frozen same-PID runloop clocks')
    complete = (v['treeComplete'] and v['eligibleInputViews'] == 1 and v['deliveryBound'] and v['inputBindingAttempted'] and
                d['installed'] and d['installation'] == [1,1,1] and d['inheritedResponderUntouched'] and d['overflow'] == 0)
    if require_complete:
        stack = []
        attached = set()
        for r in d['rows']:
            key = (r[1],r[3])
            if r[2] == 0:
                if r[1] == 7:
                    require(r[3] not in attached, 'Only one attachment entry per ordinal')
                    attached.add(r[3])
                else:
                    require(r[3] in attached, 'Actual owned attachment precedes each native call')
                stack.append(key)
            else:
                require(stack and stack.pop() == key, 'Original call return must pair without invented rows')
        require(not stack and attached == set(range(1,d['attachments']+1)), 'All control calls/attachments return')
        require(complete and d['attachments'] > 0 and any(r[1] in (1,2,3,5,6) and r[2] == 0 for r in d['rows']),
                'Actual complete attachment/mutation/press arrival required for exporter control')
    return dict(sha256=hashlib.sha256(raw).hexdigest(), complete=complete, attachments=d['attachments'],
                rows=d['rows'], runloop=runloop, application_root_cause=False, shipping_qualified=False)


def retain_companion(sandbox, destination, token, simulator, bundle, legacy):
    path = sandbox/'tmp'/('parlor-pin51-'+token)/'delivery.json'
    require(not path.is_symlink() and path.resolve(strict=True) == path.absolute(), 'Exact owned native text file')
    raw = run_pin03.read_capture(path, maximum=MAX_BYTES)
    report = decode(raw, token=token, simulator=simulator, bundle=bundle, legacy=legacy)
    with (destination/('native-text-'+token+'.json')).open('xb') as output:
        output.write(raw)  # Retain incomplete safe installation; admission still rejects it.
    return report


def accept_native_export(out, state):
    require((state['source'],state['source_tree'],state['profile'],state['runtime'],state['device'],state['xcode'],state['sdk'],state['debug_bundle']) ==
            (SOURCE,TREE,PROFILE,RUNTIME,DEVICE,'Xcode 26.3\nBuild version 17C529\n','26.2','me.parlor.ios.debug') and
            len(state['arms']) == 1, 'Exact source/compiler/Debug ownership')
    row = state['arms'][0]; d = out/'compose'
    require(row['build_exit'] == 0 and row['cleanup'] == 'PASS' and row['result'] == 'JOURNEYS_FAILED', 'Real build/failure/cleanup')
    require(verify_results(load(d/'summary.json'), load(d/'tests.json'), row['simulator'], row['test_exit'],require_exporter=True) == row['methods'] and
            row['attachment_test_ids'] == [CASE], 'Actual original control assertion/attachments')
    leaf = runtime.pair_control.leaf
    require(all(state['original_inputs'].get(p) == h for p,h in leaf.pair.INPUTS.items()), 'Original source binding')
    plan = renderer_for_profile(PROFILE,SOURCE,RUNTIME,DEVICE)(
        {p:leaf.pair.fixture(p) for p in leaf.pair.MODIFIED}, leaf.pair.INPUTS, arm='compose')
    require(load(d/'inputs.json') == state['original_inputs'] | {p:hashlib.sha256(v.encode()).hexdigest() for p,v in plan.items()},
            'Exact copied inputs, no original changes')
    product = frozen_sources._read(d/'product.json',2097152)
    require(hashlib.sha256(product).hexdigest() == row['product_manifest_sha256'] and load(d/'product.json'), 'Product custody')
    owners = runtime.pair_control.samples.requests_by_method(d,row['methods'])
    require(len(owners) == 1, 'One actual acknowledged failure request')
    reports, captures = [], []
    for token in owners:
        raw = frozen_sources._read(d/('capture-'+token+'.json'),pin02_capture.MAX_BYTES)
        legacy = json.loads(raw)
        capture = run_pin03.decode_capture(raw,set(owners),row['simulator'],state['debug_bundle'])
        require(capture['snapshot'] == dict(schema=1,nativeMode=0,overflow=0,invalid=0,rows=[]) and
                capture['numeric_buffer_complete'] is False, 'ACK only; no fabricated state')
        captures.append(capture)
        reports.append(decode(frozen_sources._read(d/('native-text-'+token+'.json'),MAX_BYTES), token=token,
            simulator=row['simulator'],bundle=state['debug_bundle'],legacy=legacy,require_complete=True))
    require(captures == row['captures'] and reports == row['companion_captures'],
            'Exact retained same-PID evidence')
    for prefix in ('capture-','native-text-'):
        require({p.name for p in d.glob(prefix+'*.json')} == {prefix+t+'.json' for t in owners}, 'One file per real failure')
    for prefix in ('native-runloop-','native-touch-','native-only-','native-accessibility-','native-frame-','native-root-'):
        require(not any(d.glob(prefix+'*.json')), 'No invented absent observation')
    cleanup = load(d/'cleanup.json')
    require(cleanup['gradle_exit'] == 0 and cleanup['workers_before']['result'] == cleanup['workers_after']['result'] ==
            cleanup['simulator']['result'] == 'PASS' and cleanup['simulator']['udid'] == row['simulator'], 'Owned cleanup')
    require(all(c['status'] == 'EXITED' and c['direct_child_reaped'] and not c.get('signals') for c in state['commands']), 'Reaped children')
    for stage, code, budget in (('build',0,2700),('tests',65,900),('gradle-stop',0,120)):
        calls = [c for c in state['commands'] if c['label'] == 'compose-'+stage]
        require(len(calls) == 1 and calls[0]['exit_code'] == code and calls[0]['timeout_seconds'] == budget,
                'One actual command per original bounded stage')
        if stage != 'tests': continue
        argv = calls[0]['command']
        require([a for a in argv if a.startswith('-only-testing:')] == ['-only-testing:iosAppUITests/'+CASE.removesuffix('()')] and
                argv.count('test-without-building') == argv.count('/usr/bin/xcodebuild') == 1 and '-quiet' not in argv and
                not any(a.startswith(('-skip-testing','-retry-tests','-test-iterations','-run-tests-until-failure')) for a in argv),
                'One exact control, no retry/skip')
        for flag,value in (('-project','iosApp/iosApp.xcodeproj'),('-scheme','iosApp'),('-configuration','Debug'),
            ('-sdk','iphonesimulator'),('-destination','platform=iOS Simulator,id='+row['simulator']),
            ('-parallel-testing-enabled','NO'),('-maximum-concurrent-test-simulator-destinations','1')):
            require(argv.count(flag) == 1 and argv[argv.index(flag)+1] == value, 'Exact owned Debug command flags')
    require(state['build_profile']['test_timeout_seconds'] == 900, 'Original process limit unchanged')
    starts = re.findall(rb"Test Case '-\[iosAppUITests\.([A-Za-z0-9_]+) ([A-Za-z0-9_]+)\]' started\.",frozen_sources._read(d/'test.log',4194304))
    require(tuple(c.decode()+'/'+m.decode()+'()' for c,m in starts) == CASES, 'Only separately named control executed once')
    receipt = dict(methods=row['methods'],failed_sample_tokens=owners,native=reports,application_root_cause=False,
                   shipping_qualified=False,physical_verified=False,native_timing_neutrality=False)
    target = out/'owned-text-export.json'
    if target.exists(): require(load(target) == receipt, 'Exact saved receipt')
    else:
        with target.open('x') as output: json.dump(receipt,output,indent=2)
    return RESULT
