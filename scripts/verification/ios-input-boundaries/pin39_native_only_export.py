"""Native-only capability: original Kotlin/Compose, genuinely failed sentinel.

PIN30 baseline and observed arms disagreed. This removes every Kotlin observer,
not a speculative production repair or proof that earlier instrumentation masks
failures. Empty PIN02 rows mean ACK-only; actual native files remain mandatory.
"""
import hashlib
import json
import math
from pathlib import Path
import re

import current_boundary_adapter as current
import frozen_sources
import pin01_copy
import pin01_receipts
import pin02_capture
import pin06_public_setup_pair as pair
import pin08_accessibility as attributes
import pin10_root_boundary as root
import pin22_native_frame_boundary as frames
import pin33_original_touch_delivery as touch
import run_pin03

SOURCE, TREE = current.SOURCE, current.TREE
PROFILE = 'isolated-native-only-export'
ARMS = ('compose',)  # Runner arm name only; no Kotlin/Compose instrumentation.
CASE = 'IOSAppLaunchUITests/testPIN39NativeOnlyExportCapability()'
CASES = (CASE,)
RESULT = 'ISOLATED_NATIVE_ONLY_EXPORT_CAPABILITY_NOT_QUALIFICATION'
SCOPE = ('One isolated deliberately absent-destination control with original Kotlin/Compose bytes, '
         'unchanged original Host action/predicate/10s deadline; original unexpected assertion/exit65 '
         'and failure-only native exports required; not causality, timing neutrality or qualification')
HERE = Path(__file__).resolve().parent
TEMPLATE = 'PIN39NativeOnly.swift.in'
TEMPLATE_SHA256 = 'fc0d5e27eba7d9b7efc7762d4861a3b6f9a8540912102cd9201598b8a0619bb7'
MAX_BYTES = 65_536
require = current.require
ABSENT = 'PIN39 intentionally absent destination; capability control only'
SENTINEL = '''
    @MainActor
    func testPIN39NativeOnlyExportCapability() throws {
        // This test, never an original journey, deliberately expects an absent
        // destination. The unchanged Host helper must produce an actual failure.
        continueAfterFailure = true
        let app = XCUIApplication()
        PIN39Configure(app)
        app.launchArguments += ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        defer { app.terminate() }
        XCTAssertTrue(app.staticTexts["parlor-home-brand"].waitForExistence(timeout: 30))
        try tapSetupButton(app, prefix: "Open Egyptian Dominoes")
        try tapPublicSetupTransition(app,
            from: "Open a room and show a code your friends can join.",
            to: "PIN39 intentionally absent destination; capability control only")
        // Runs AFTER that original assertion. If startup/input failed instead,
        // acceptance rejects the exact result/assertion evidence, not waives it.
        PIN02FailureRequest.captureAfterFailedTransition(app)
    }
'''
CONFIGURE = '''
#if DEBUG && targetEnvironment(simulator)
@MainActor private func PIN39Configure(_ app: XCUIApplication) {
    PIN02FailureRequest.configure(app)
    app.launchEnvironment["PARLOR_PIN39_SCOPE"] = "isolated-native-only-export"
    app.launchEnvironment["PARLOR_PIN39_SOURCE"] = "5029b3f48c2d012b34dd2748f809dc775276a622"
}
#endif
'''


def templates():
    pins = {TEMPLATE: TEMPLATE_SHA256,
        'PIN02Capture.swift.in': pair.TEMPLATES['PIN02Capture.swift.in'],
        'PIN02FailureRequest.swift.in': pair.TEMPLATES['PIN02FailureRequest.swift.in'],
        attributes.TEMPLATE: attributes.TEMPLATE_SHA256, root.TEMPLATE: root.TEMPLATE_SHA256,
        frames.TEMPLATE: frames.TEMPLATE_SHA256, touch.TEMPLATE: touch.TEMPLATE_SHA256}
    result = {}
    for name, digest in pins.items():
        raw = frozen_sources._read(HERE/name, frozen_sources.MAX_SOURCE_BYTES)
        require(hashlib.sha256(raw).hexdigest() == digest, 'Exact reviewed native-only template: '+name)
        result[name] = raw.decode()
    return result


def renderer_for_profile(profile, source, runtime, device):
    require((profile, source, runtime, device) == (PROFILE, SOURCE, pair.RUNTIME, pair.DEVICE),
        'Exact native-only source/profile/device required')
    def render(sources, digests, *, arm):
        require(arm == 'compose' and set(sources) == set(digests) == set(pair.MODIFIED), 'Exact original seven inputs')
        require(digests == pair.INPUTS and all(hashlib.sha256(v.encode()).hexdigest() == digests[p]
            for p,v in sources.items()), 'Original Kotlin/Compose and Swift bytes must match')
        t = templates()
        container = pin01_copy.once(sources[pin02_capture.CONTAINER],
            '    private let contentController: UIViewController\n',
            '    private let contentController: UIViewController\n#if DEBUG && targetEnvironment(simulator)\n'
            '    private var pin39Native: PIN39NativeOnlyObservation?\n#endif\n')
        container = pin01_copy.once(container, '        contentController.didMove(toParent: self)\n',
            '        contentController.didMove(toParent: self)\n#if DEBUG && targetEnvironment(simulator)\n'
            '        pin39Native = PIN39NativeOnlyObservation(view: contentView)\n#endif\n')
        container += '\n'+'\n'.join((t['PIN02Capture.swift.in'],
            frames.native_template_extension(t[attributes.TEMPLATE]), t[frames.TEMPLATE],
            t[root.TEMPLATE], t[touch.TEMPLATE], t[TEMPLATE]))
        driver = pin01_copy.once(sources[pin02_capture.DRIVER],
            '    private struct KeyboardCopy {', SENTINEL+'\n    private struct KeyboardCopy {')
        driver += '\n'+t['PIN02FailureRequest.swift.in']+CONFIGURE
        return sources | {pin02_capture.CONTAINER: container, pin02_capture.DRIVER: driver}
    return render


def test_command(base, result):
    return [*(a for a in base if a != '-quiet'), 'test-without-building', '-resultBundlePath', str(result),
        '-only-testing:iosAppUITests/'+CASE.removesuffix('()')]


def verify_results(summary, tests, simulator, code):
    require(type(code) is int and code == 65, 'Unexpected native sentinel failure must retain actual exit65')
    for key, expected in dict(totalTestCount=1, passedTests=0, failedTests=1, skippedTests=0, expectedFailures=0).items():
        require(type(summary.get(key)) is int and summary[key] == expected, 'Exactly one failed, unskipped sentinel')
    require(summary.get('result') == 'Failed' and len(tests.get('testPlanConfigurations', [])) == 1,
        'Actual failed single test configuration')
    configurations, devices = summary.get('devicesAndConfigurations', []), tests.get('devices', [])
    require(len(configurations) == len(devices) == 1, 'One owned device')
    for d in (configurations[0].get('device', {}), devices[0]):
        require((d.get('deviceId'), d.get('architecture'), d.get('platform'), d.get('modelName'), d.get('osVersion')) ==
            (simulator, 'arm64', 'iOS Simulator', 'iPhone 16 Pro', '18.5'), 'Exact owned native device')
    def visit(nodes):
        return [case for n in nodes for case in ([n] if n.get('nodeType') == 'Test Case' else visit(n.get('children', [])))]
    cases = visit(tests.get('testNodes', []))
    require(len(cases) == 1 and cases[0].get('nodeIdentifier') == CASE and cases[0].get('result') == 'Failed' and
        type(cases[0].get('durationInSeconds')) in (int,float) and
        math.isfinite(cases[0]['durationInSeconds']) and 0 < cases[0]['durationInSeconds'] < 900, 'Exact actual sentinel')
    failures = summary.get('testFailures', [])
    require(len(failures) == 1 and failures[0].get('testIdentifierString') == CASE and
        'One public setup tap must remove the source and expose its destination' in failures[0].get('failureText',''),
        'Sentinel must fail at unchanged transition assertion, not startup or settlement')
    return {CASE: 'Failed'}


def failed_method_exports(result, destination, outcomes):
    require(outcomes == {CASE: 'Failed'}, 'Only actual isolated sentinel failure may export')
    return [(destination/'failed-01', ['export','attachments','--path',str(result),'--output-path',
        str(destination/'failed-01'),'--test-id',CASE])]


def verify_commands(state, row):
    for label, code, deadline in (('compose-build',0,2700), ('compose-tests',65,900), ('compose-gradle-stop',0,120)):
        rows = [c for c in state['commands'] if c.get('label') == label]
        require(len(rows) == 1 and rows[0].get('exit_code') == code and rows[0].get('timeout_seconds') == deadline,
            'Single bounded native build/test/cleanup command')
        if label == 'compose-tests':
            argv = rows[0]['command']
            require([a for a in argv if a.startswith('-only-testing:')] ==
                ['-only-testing:iosAppUITests/'+CASE.removesuffix('()')] and
                argv.count('test-without-building') == 1 and '-quiet' not in argv and
                not any(a.startswith(('-skip-testing','-test-iterations','-retry-tests')) for a in argv),
                'Only new sentinel once, no original cohort replay or retries')


def decode_status(raw, *, token, simulator, bundle, legacy):
    v = json.loads(raw, object_pairs_hook=pin01_receipts.reject_duplicates,
        parse_constant=lambda _: (_ for _ in ()).throw(ValueError('Nonfinite status')))
    require(type(v) is dict and set(v) == {'schema','token','simulator','bundle','process','capturedMs',
        'viewCount','eligibleInputViews','treeComplete','attributesInstalled','rootInstalled','touchInstalled',
        'kotlinInstrumentation','composePointerObservers','addedRecognizer','primaryEnvelopePurpose'}, 'Only native-only status fields')
    require(type(v['schema']) is int and v['schema'] == 1 and type(v['process']) is int and
        (v['token'],v['simulator'],v['bundle'],v['process']) == (token,simulator,bundle,legacy['process']), 'Exact status owner')
    require(type(v['capturedMs']) in (int,float) and math.isfinite(v['capturedMs']) and
        0 <= v['capturedMs'] <= legacy['capturedMs'], 'Actual status clock')
    require(type(v['viewCount']) is int and 1 <= v['viewCount'] <= 256 and
        type(v['eligibleInputViews']) is int and v['eligibleInputViews'] == 1 and
        all(v[k] is True for k in ('treeComplete','attributesInstalled','rootInstalled','touchInstalled')),
        'Complete bounded original input-tree and all actual installations required')
    require(all(v[k] is False for k in ('kotlinInstrumentation','composePointerObservers','addedRecognizer')) and
        v['primaryEnvelopePurpose'] == 'durable-failure-ack-only', 'No invented Kotlin observations')
    return v


def retain_companion(sandbox, destination, token, simulator, bundle, legacy):
    # Preserve raw files BEFORE validating installation; failures never erase evidence.
    specs = (('parlor-pin08-', 'accessibility.json', 'native-accessibility-', attributes.MAX_BYTES),
        ('parlor-pin22-', 'frames.json', 'native-frame-', frames.MAX_BYTES),
        ('parlor-pin10-', 'root.json', 'native-root-', root.MAX_BYTES),
        ('parlor-pin33-', 'touches.json', 'native-touch-', touch.decoder.MAX_BYTES),
        ('parlor-pin39-', 'status.json', 'native-only-', MAX_BYTES))
    for prefix, filename, retained, maximum in specs:
        path = sandbox/'tmp'/(prefix+token)/filename
        require(not path.is_symlink() and path.resolve(strict=True) == path.absolute(), 'Exact owned native-only companion')
        raw = run_pin03.read_capture(path, maximum=maximum)
        with (destination/(retained+token+'.json')).open('xb') as f: f.write(raw)
    return decode_companions(destination, token, simulator, bundle, legacy)


def decode_companions(destination, token, simulator, bundle, legacy):
    args = dict(token=token, simulator=simulator, bundle=bundle, legacy=legacy)
    def read(prefix, maximum):
        return frozen_sources._read(destination/(prefix+token+'.json'), maximum)
    raw = read('native-accessibility-', attributes.MAX_BYTES)
    attribute = attributes.decode_native(raw, **args)
    frame = frames.decode_frames(read('native-frame-', frames.MAX_BYTES), attributes=json.loads(raw), **args)
    root_report = root.decode_root(read('native-root-', root.MAX_BYTES), **args)
    touch_raw = read('native-touch-', touch.decoder.MAX_BYTES)
    touch_report = touch.decoder.decode(touch_raw, **args)
    require(touch_report['complete'], 'Unique original recognizer, exact ABI and complete native touch buffer')
    rows = json.loads(touch_raw)['rows']
    require(sum(r[1:3] == [1,0] and r[4] == 1 for r in rows) >= 2 and
        sum(r[1:3] == [3,0] and r[4] == 1 for r in rows) >= 2 and
        any(r[1] == 5 and r[2] == 0 and r[4] == 1 for r in rows), 'Actual original catalog/Host began/ended/state calls')
    status = decode_status(read('native-only-', MAX_BYTES), **args)
    return dict(attributes=attribute, frames=frame, root=root_report, touches=touch_report, status=status)


def accept_native_export(out, state):
    # Standalone ACK-only consumer. Legacy numeric-completeness acceptance remains unchanged.
    require((state['profile'],state['source'],state['source_tree'],state['runtime'],state['device']) ==
        (PROFILE,SOURCE,TREE,pair.RUNTIME,pair.DEVICE), 'Exact native-only source identity')
    require(state['debug_bundle'] == 'me.parlor.ios.debug' and state['xcode'] == 'Xcode 26.3\nBuild version 17C529\n' and
        state['sdk'] == '26.2' and len(state['arms']) == 1, 'Actual native compiler/Debug product/single arm')
    row = state['arms'][0]; d = out/'compose'
    require(row['arm'] == 'compose' and row['build_exit'] == 0 and row['cleanup'] == 'PASS' and
        row['result'] == 'JOURNEYS_FAILED', 'Actual native build/failure/cleanup')
    load = lambda p: json.loads(frozen_sources._read(p, 2_097_152), object_pairs_hook=pin01_receipts.reject_duplicates)
    require(verify_results(load(d/'summary.json'),load(d/'tests.json'),row['simulator'],row['test_exit']) == row['methods'] and
        row['attachment_test_ids'] == [CASE], 'Actual isolated failed assertion and attachments')
    require(all(state['original_inputs'].get(p) == h for p,h in pair.INPUTS.items()), 'All original binding bytes')
    plan = renderer_for_profile(PROFILE,SOURCE,pair.RUNTIME,pair.DEVICE)(
        {p:pair.fixture(p) for p in pair.MODIFIED},pair.INPUTS,arm='compose')
    require(load(d/'inputs.json') == state['original_inputs'] | {p:hashlib.sha256(v.encode()).hexdigest() for p,v in plan.items()},
        'Only reviewed native-only source plan')
    product = frozen_sources._read(d/'product.json', 2_097_152)
    require(json.loads(product) and hashlib.sha256(product).hexdigest() == row['product_manifest_sha256'], 'Actual product custody')
    requests = []
    for p in (d/'attachments').rglob('*'):
        if p.is_file() and not p.is_symlink() and 0 < p.stat().st_size <= 4096:
            try: v = load(p)
            except (ValueError, UnicodeError): continue
            if type(v) is dict and 'originalTransitionFailed' in v: requests.append(v)
    require(len(requests) == 1 and set(requests[0]) == {'schema','token','ackObserved','originalTransitionFailed'} and
        type(requests[0]['schema']) is int and requests[0]['schema'] == 1 and requests[0]['ackObserved'] is True and
        requests[0]['originalTransitionFailed'] is True, 'One actual post-assertion acknowledged request')
    token = requests[0]['token']
    require(type(token) is str and pin02_capture.UUID.fullmatch(token), 'Exact failed-only token')
    raw = frozen_sources._read(d/('capture-'+token+'.json'), pin02_capture.MAX_BYTES)
    legacy = json.loads(raw)
    decoded = run_pin03.decode_capture(raw,{token},row['simulator'],state['debug_bundle'])
    require(decoded['snapshot'] == dict(schema=1,nativeMode=0,overflow=0,invalid=0,rows=[]) and
        decoded['numeric_buffer_complete'] is False and row['captures'] == [decoded], 'ACK-only envelope, never invented numeric evidence')
    for prefix in ('capture-','native-accessibility-','native-frame-','native-root-','native-touch-','native-only-'):
        require(len(list(d.glob(prefix+'*.json'))) == 1, 'Exactly one companion per actual failure')
    report = decode_companions(d,token,row['simulator'],state['debug_bundle'],legacy)
    require(row['companion_captures'] == [report], 'Native evidence matches retained originals')
    cleanup = load(d/'cleanup.json')
    require(cleanup['gradle_exit'] == 0 and cleanup['workers_before']['result'] == cleanup['workers_after']['result'] ==
        cleanup['simulator']['result'] == 'PASS' and cleanup['simulator']['udid'] == row['simulator'], 'Owned cleanup')
    require(state['commands'] and all(c['status'] == 'EXITED' and c['direct_child_reaped'] and not c.get('signals')
        for c in state['commands']), 'Every child exited and reaped')
    verify_commands(state,row)
    starts = re.findall(rb"Test Case '-\[iosAppUITests\.([A-Za-z0-9_]+) ([A-Za-z0-9_]+)\]' started\.",
        frozen_sources._read(d/'test.log', 2_097_152))
    require(tuple(c.decode()+'/'+m.decode()+'()' for c,m in starts) == CASES, 'Only new sentinel executed once')
    receipt = dict(methods=row['methods'], native=report, primary_envelope_purpose='durable-failure-ack-only',
        control_cause='Deliberately absent destination in isolated XCTest, not application suppression',
        application_root_cause=False,shipping_qualified=False,native_timing_neutrality=False,physical_verified=False)
    target = out/'native-only-export.json'
    if target.exists(): require(load(target) == receipt, 'Exact saved native-only capability receipt')
    else:
        with target.open('x') as f: json.dump(receipt,f,indent=2)
    return RESULT
