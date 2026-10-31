"""Reuse prepared PIN05 public-name session adapter at the real PIN22 failure.

Original Arabic journey only; successful English/PIN21 controls are not replayed.
One separate text-export control types once and deliberately asserts a sentinel.
No shipping edit, altered natural gesture/deadline, retyping or input repair.
"""
import hashlib
import json
import math

import frozen_sources
import pin01_copy
import pin01_receipts
import pin02_capture
import pin07_failure_control as fault
import pin22_native_frame_boundary as frame
import run_pin03

motion = frame.motion
require = motion.current.require
SOURCE, TREE, ARMS = frame.SOURCE, frame.TREE, frame.ARMS
PROFILE = 'public-name-text-session-boundaries'
NATURAL = 'IOSAppLaunchUITests/testAdditionalGamesExposeOnlyMultiplayerSetupInArabic()'
CONTROL = 'IOSAppLaunchUITests/testPIN23IsolatedPublicTextExportControl()'
CASES = (NATURAL, CONTROL)
RESULT = 'PUBLIC_NAME_TEXT_SESSION_OBSERVATIONS_COMPLETE_NOT_QUALIFICATION'
SCOPE = ('Original5029 Arabic journey with byte-exact reused PIN05 public-name session/command/callback '
         'adapter and existing native/root/frame/motion observations; one distinct sentinel export control; '
         'no successful English replay, retyping, deadline change, shipping repair or qualification')
NAME = 'composeApp/src/commonMain/kotlin/com/parlor/app/shell/multiplayer/NameInputScreen.kt'
NAME_SHA256 = '4730d58c86ec11cb64a8a8540c523b9ce84c259d7250178bc1aa249bafa01686'
RECORDER = 'composeApp/src/commonMain/kotlin/com/parlor/app/inputcopy/PIN05TextObservation.kt'
INTERCEPTOR = 'composeApp/src/iosMain/kotlin/com/parlor/app/inputcopy/PIN05TextInputInterceptor.kt'
SWIFT = 'PIN23TextInput.swift.in'
MAX_BYTES = 131_072
TEMPLATES = {
    'PIN05TextObservation.kt.in': '88ceae03dc07cba145d32c8c6c8de58de1b976f3d3183ca8ab6174461a3a6efe',
    'PIN05TextInputInterceptor.kt.in': '282e8795c19940e4013f0173f694b0fbaebd44408aa5674c75e3984575aa8f48',
    'PIN05TextInputControlTest.kt.in': '77bcea0365b83978baac69a8d85657eda3ba2b53714209ddd5f96c97791e5a38',
    SWIFT: '7e00616eb94b04afdd890142bb7e8c9e5aaec898c9fe798d19ee876af0c56612',
}
CONFIGURE = '''        com.parlor.app.inputcopy.PIN05Observation.clock = {
            platform.Foundation.NSProcessInfo.processInfo.systemUptime * 1000.0
        }
        com.parlor.app.inputcopy.PIN05Observation.nativeMode = 1
'''
BRIDGE = '''
fun PIN05InputSnapshot(): String {
    check(platform.Foundation.NSThread.isMainThread)
    return com.parlor.app.inputcopy.PIN05Observation.snapshot()
}
fun PIN05NativeStatus(firstResponder: Boolean, textResponder: Boolean) {
    check(platform.Foundation.NSThread.isMainThread)
    com.parlor.app.inputcopy.PIN05Observation.native(firstResponder, textResponder)
}
fun PIN05RejectNativeStatus() {
    check(platform.Foundation.NSThread.isMainThread)
    com.parlor.app.inputcopy.PIN05Observation.rejectNativeObservation()
}
'''
MOUNT = '''    val pin05Owner = remember { Any() }
    DisposableEffect(pin05Owner) {
        PIN05Observation.mount(pin05Owner, isHost)
        onDispose { PIN05Observation.unmount(pin05Owner) }
    }
    SideEffect { PIN05Observation.composed(pin05Owner, name.isEmpty()) }
'''
CALLBACK = '''                            PIN05Observation.callback(pin05Owner, 0, input.isEmpty())
                            name = RoomInputPolicy.sanitizeDisplayNameInput(input)
                            PIN05Observation.callback(pin05Owner, 1, name.isEmpty())
'''
CONTROL_METHOD = '''    // COPY ONLY: distinct public-name export control, not an original journey.
    // A single injection must first reach the actual value. The second predicate
    // deliberately fails on a sentinel; no retyping, corrective tap or networking.
    @MainActor
    func testPIN23IsolatedPublicTextExportControl() throws {
        let app = XCUIApplication()
        PIN02FailureRequest.configure(app)
        app.launchArguments += ["-AppleLanguages", "(ar)", "-AppleLocale", "ar_EG"]
        app.launchEnvironment["PARLOR_PIN23_CONTROL"] = "1"
        pin11PublicApp = app // Same once-only failure owner as the original public journey.
        app.launch()
        defer {
            pin11PublicApp = nil
            app.terminate()
        }
        XCTAssertTrue(app.staticTexts["parlor-home-brand"].waitForExistence(timeout: 30))
        try tapSetupButton(app, prefix: "افتح الدومنة المصرية")
        try tapPublicSetupTransition(app, from: "أدخل رمز المضيف للدخول إلى طاولته.",
                                     to: "تابع وأعد تنفيذ عملية الشبكة المحلية الفعلية.")
        try tapSetupButton(app, prefix: "تابع وأعد تنفيذ عملية الشبكة المحلية الفعلية.")
        let name = app.textViews.firstMatch
        XCTAssertTrue(name.waitForExistence(timeout: 10))
        try tapSettledControl(name)
        XCTAssertTrue(app.keyboards.firstMatch.waitForExistence(timeout: 10))
        let introduction = dismissKeyboardIntroduction(app)
        name.typeText("Table Test")
        assertTextValue(name, in: app, equals: "Table Test", introduction: introduction)
        // Failure-only capture remains inside the unchanged failed-value helper.
        assertTextValue(name, in: app, equals: "PIN23 isolated export sentinel", introduction: introduction)
    }

'''


def template(name):
    raw = frozen_sources._read(motion.pair.HERE/name, MAX_BYTES)
    require(hashlib.sha256(raw).hexdigest() == TEMPLATES[name], 'Exact reused/reviewed text template required')
    return raw.decode()


def name_fixture():
    raw = frozen_sources._read(motion.pair.HERE/'fixtures/PIN23NameInputScreen.kt.source', MAX_BYTES)
    require(hashlib.sha256(raw).hexdigest() == NAME_SHA256, 'Exact original5029 public-name bytes required')
    return raw.decode()


def renderer_for_profile(profile, source, runtime, device):
    require((profile, source, runtime, device) == (PROFILE,SOURCE,motion.pair.RUNTIME,motion.pair.DEVICE),
            'Exact public text-session source/profile/runtime/device required')
    base = frame.renderer_for_profile(frame.PROFILE,source,runtime,device)
    def render(sources,digests,*,arm):
        templates = {n:template(n) for n in TEMPLATES}
        plan = base(sources,digests,arm=arm)
        main = pin01_copy.once(plan[pin01_copy.MAIN], '        startKoinOnce()\n', CONFIGURE+'        startKoinOnce()\n')
        main = pin01_copy.once(main,'    App()\n','    com.parlor.app.inputcopy.PIN05ObserveInput { App() }\n')
        plan[pin01_copy.MAIN] = main+BRIDGE
        name = pin01_copy.once(name_fixture(), 'import androidx.compose.runtime.Composable\n',
            'import androidx.compose.runtime.Composable\nimport androidx.compose.runtime.DisposableEffect\n'
            'import androidx.compose.runtime.SideEffect\nimport com.parlor.app.inputcopy.PIN05Observation\n')
        name = pin01_copy.once(name, '    var name by remember { mutableStateOf(initial) }\n',
            '    var name by remember { mutableStateOf(initial) }\n'+MOUNT)
        plan[NAME] = pin01_copy.once(name,'                            name = RoomInputPolicy.sanitizeDisplayNameInput(input)\n',CALLBACK)
        plan[RECORDER],plan[INTERCEPTOR] = templates['PIN05TextObservation.kt.in'],templates['PIN05TextInputInterceptor.kt.in']
        container = pin01_copy.once(plan[pin02_capture.CONTAINER], '    private let pin20Motion = PIN20CatalogMotionExport()',
            '    private let pin20Motion = PIN20CatalogMotionExport()\n    private let pin23Text = PIN23TextInputExport()')
        container = pin01_copy.once(container,'weak motionExport] in','weak motionExport, weak self] in')
        container = pin01_copy.once(container,'            return original',
            '            self?.pin23Text.exportAfterFailureOnce(snapshot: { self?.pin05Snapshot() ?? "{}" })\n'
            '            return original')
        container = pin01_copy.once(container,'    private let contentController: UIViewController\n',
            '    private let contentController: UIViewController\n'+NATIVE_SNAPSHOT)
        plan[pin02_capture.CONTAINER] = container+'\n'+templates[SWIFT]
        plan[pin02_capture.DRIVER] = pin01_copy.once(plan[pin02_capture.DRIVER],fault.DRIVER_ANCHOR,CONTROL_METHOD+fault.DRIVER_ANCHOR)
        return plan
    return render


def test_command(base,result):
    return [*(a for a in base if a!='-quiet'),'test-without-building','-resultBundlePath',str(result),
        *('-only-testing:iosAppUITests/'+t.removesuffix('()') for t in CASES)]


def verify_results(summary,tests,simulator_id,code):
    return verify_text_results(summary,tests,simulator_id,code,cases=CASES)


def verify_text_results(summary,tests,simulator_id,code,*,cases):
    require(cases in (CASES,(CONTROL,)), 'Only unresolved original cohort or isolated text export control')
    require(type(code) is int and code==65,'Original sentinel control must remain a genuine XCTest failure')
    nodes=[]
    def visit(items):
        for n in items:
            if n.get('nodeType')=='Test Case':nodes.append(n)
            visit(n.get('children',[]))
    visit(tests.get('testNodes',[]))
    require(len(nodes)==len(cases) and {n.get('nodeIdentifier') for n in nodes}==set(cases) and
        all(n.get('result') in ('Passed','Failed') for n in nodes),'Exact Arabic and text-control outcomes; zero skips')
    methods={n['nodeIdentifier']:n['result'] for n in nodes}
    require(methods[CONTROL]=='Failed' and summary.get('result')=='Failed','Sentinel is never a passing application test')
    for k,v in dict(totalTestCount=len(cases),passedTests=sum(v=='Passed' for v in methods.values()),
        failedTests=sum(v=='Failed' for v in methods.values()),skippedTests=0,expectedFailures=0).items():
        require(type(summary.get(k)) is int and summary[k]==v,'Exact unskipped native counts required')
    config=summary.get('devicesAndConfigurations',[]);devices=tests.get('devices',[])
    require(len(config)==len(devices)==1,'Single owned Simulator required')
    for d in (config[0].get('device',{}),devices[0]):
        require((d.get('deviceId'),d.get('architecture'),d.get('platform'),d.get('modelName'),d.get('osVersion'))==
            (simulator_id,'arm64','iOS Simulator','iPhone 16 Pro','18.5'),'Exact actual native device required')
    failures=summary.get('testFailures',[])
    require(len(failures)==sum(v=='Failed' for v in methods.values()) and
        {f.get('testIdentifierString') for f in failures}=={t for t,v in methods.items() if v=='Failed'},
        'Every actual failed case needs original assertion evidence')
    forced=[f for f in failures if f.get('testIdentifierString')==CONTROL]
    require(len(forced)==1 and 'The full typed value must reach the actual Compose field' in forced[0].get('failureText',''),
        'Text control must fail on its original exact-value assertion, not startup/navigation')
    return methods


def failed_method_exports(result,destination,outcomes):
    return text_method_exports(result,destination,outcomes,cases=CASES)


def text_method_exports(result,destination,outcomes,*,cases):
    require(cases in (CASES,(CONTROL,)) and set(outcomes)==set(cases),'Only exact Arabic and isolated text-control failures eligible')
    return [(destination/('failed-%02d'%i),['export','attachments','--path',str(result),'--output-path',
        str(destination/('failed-%02d'%i)),'--test-id',t])
        for i,t in enumerate(sorted(t for t,v in outcomes.items() if v=='Failed'),1)]


retain_root_companion = frame.retain_root_companion
retain_motion_companion = frame.retain_motion_companion


def decode_text(raw,*,token,simulator,bundle,legacy):
    require(type(raw) is bytes and 0<len(raw)<=MAX_BYTES,'Bounded original public text companion required')
    v=json.loads(raw,object_pairs_hook=pin01_receipts.reject_duplicates,
        parse_constant=lambda _:(_ for _ in ()).throw(ValueError('Nonfinite text field')))
    require(type(v) is dict and set(v)=={'schema','token','simulator','bundle','process','capturedMs','text','syntheticControl'},
        'No text, lengths, classes, labels or private fields allowed')
    require(type(v['schema']) is int and v['schema']==1 and (v['token'],v['simulator'],v['bundle'],v['process'])==
        (token,simulator,bundle,legacy['process']) and type(v['process']) is int and v['process']>0,
        'Exact text process/token/device/product required')
    require(type(v['capturedMs']) in (int,float) and math.isfinite(v['capturedMs']) and
        0<=v['capturedMs']<=legacy['capturedMs'] and type(v['syntheticControl']) is bool,'Original text capture clock/control required')
    decoded=decode_snapshot(json.dumps(v['text']).encode())
    require(v['text']['nativeMode']==1 and all(r[0]<=v['capturedMs'] for r in v['text']['rows']),
        'Actual public-scope text/native observation clock required')
    return dict(sha256=hashlib.sha256(raw).hexdigest(),token=token,complete=True,synthetic_control=v['syntheticControl'],**decoded)


def retain_companion(sandbox,destination,token,simulator,bundle,legacy):
    report=frame.retain_companion(sandbox,destination,token,simulator,bundle,legacy)
    path=sandbox/'tmp'/('parlor-pin23-'+token)/'text.json'
    require(not path.is_symlink() and path.resolve(strict=True)==path.absolute(),'Owned original text companion required')
    raw=run_pin03.read_capture(path,maximum=motion.MAX_BYTES)
    decode_text(raw,token=token,simulator=simulator,bundle=bundle,legacy=legacy)
    with (destination/('public-text-'+token+'.json')).open('xb') as f:f.write(raw)
    return report # Keep the existing native companion schema/receipt unchanged.


def accept_native_export(out,state):
    renderer=renderer_for_profile(PROFILE,SOURCE,motion.pair.RUNTIME,motion.pair.DEVICE)
    return accept_text_export(out,state,profile=PROFILE,renderer=renderer,cases=CASES,result=RESULT)


def accept_text_export(out,state,*,profile,renderer,cases,result):
    require((profile,cases,result) in (
        (PROFILE,CASES,RESULT),
        ('isolated-public-text-session-export-control',(CONTROL,),
         'ISOLATED_PUBLIC_TEXT_SESSION_EXPORT_COMPLETE_NOT_QUALIFICATION'),
        ('public-name-native-input-delivery',CASES,
         'PUBLIC_NAME_NATIVE_INPUT_DELIVERY_COMPLETE_NOT_QUALIFICATION'),
        ('isolated-native-input-delivery-export-control',(CONTROL,),
         'ISOLATED_NATIVE_INPUT_DELIVERY_EXPORT_COMPLETE_NOT_QUALIFICATION'),
        ('isolated-attached-native-input-delivery',(CONTROL,),
         'ISOLATED_ATTACHED_NATIVE_INPUT_DELIVERY_COMPLETE_NOT_QUALIFICATION')),
        'Exact original or isolated-control acceptance contract required')
    require(state['original_inputs'].get(NAME)==NAME_SHA256,'Actual original public-name source binding required')
    def selected_verifier(summary,tests,simulator,code):
        return verify_text_results(summary,tests,simulator,code,cases=cases)
    frame.motion.boundary.accept_observation_export(out,state,profile=profile,renderer=renderer,result_verifier=selected_verifier)
    frame.motion.root.accept_root_companions(out,state)
    row=state['arms'][0];dest=out/'compose';captures=sorted(dest.glob('capture-*.json'))
    commands=[c for c in state['commands'] if c.get('label')=='compose-tests']
    require(len(commands)==1 and commands[0].get('timeout_seconds')==900 and
        [a for a in commands[0].get('command',[]) if a.startswith('-only-testing:')]==
        ['-only-testing:iosAppUITests/'+t.removesuffix('()') for t in cases],
        'One original-budget command; no English replay or old export-control replay')
    # The unchanged Swift helper uses caller #line. This proves the first exact
    # value check completed and the failing assertion is the sentinel, not a
    # partial/nonempty natural injection failure masquerading as the control.
    driver=renderer({p:motion.pair.fixture(p) for p in motion.pair.MODIFIED},motion.pair.INPUTS,arm='compose')[pin02_capture.DRIVER]
    sentinel_lines=[i for i,line in enumerate(driver.splitlines(),1) if 'equals: "PIN23 isolated export sentinel"' in line]
    require(len(sentinel_lines)==1,'One exact original sentinel callsite required')
    log=frozen_sources._read(dest/'test.log',4*1024*1024).decode()
    assertion=f'IOSAppLaunchUITests.swift:{sentinel_lines[0]}: error: -[iosAppUITests.IOSAppLaunchUITests testPIN23IsolatedPublicTextExportControl] : XCTAssertEqual failed:'
    require(log.count(assertion)==1,'Control must actually fail at sentinel callsite after its exact original value check')
    tokens={}
    for i,method in enumerate(sorted(t for t,v in row['methods'].items() if v=='Failed'),1):
        requests=[]
        for p in (dest/'attachments'/('failed-%02d'%i)).rglob('*'):
            if p.is_file() and not p.is_symlink() and 0<p.stat().st_size<=4096:
                try:v=json.loads(p.read_bytes())
                except (ValueError,UnicodeError):continue
                if type(v) is dict and 'originalTransitionFailed' in v:requests.append(v)
        require(len(requests)==1 and requests[0]['token'] not in tokens,'Original failed-method ownership required')
        tokens[requests[0]['token']]=method
    decoded_motion=[]
    for p in captures:
        legacy=json.loads(frozen_sources._read(p,pin02_capture.MAX_BYTES));token=legacy['token']
        attributes=json.loads(frozen_sources._read(dest/('native-accessibility-'+token+'.json'),frame.native.MAX_BYTES))
        frame.decode_frames(frozen_sources._read(dest/('native-frame-'+token+'.json'),frame.MAX_BYTES),
            token=token,simulator=row['simulator'],bundle=state['debug_bundle'],legacy=legacy,attributes=attributes)
        motion_report=motion.decode_motion(frozen_sources._read(dest/('catalog-motion-'+token+'.json'),motion.MAX_BYTES),
            token=token,simulator=row['simulator'],bundle=state['debug_bundle'],legacy=legacy)
        require(motion_report['synthetic_control'] is False,'No Host suppression in either text-profile process')
        decoded_motion.append(motion_report)
        text=json.loads(frozen_sources._read(dest/('public-text-'+token+'.json'),MAX_BYTES))
        decode_text(json.dumps(text).encode(),token=token,simulator=row['simulator'],bundle=state['debug_bundle'],legacy=legacy)
        require(text['syntheticControl']==(tokens[token]==CONTROL),'Sentinel flag only belongs to isolated control process')
        if tokens[token]==CONTROL:
            rows=text['text']['rows']
            require(any(r[1:4]==[4,1,0] for r in rows) and any(r[1:4]==[1,2,0] for r in rows) and
                any(r[1:3]==[3,0] for r in rows) and rows[-1][1:5]==[5,0,1,1],
                'Actual control must have command delivery, nonempty sanitized/composed value and public text first responder')
    require(row.get('motion_companion_captures')==decoded_motion,'Actual motion receipt unchanged')
    for prefix in ('public-text-','native-frame-','catalog-motion-'):
        require(len(list(dest.glob(prefix+'*.json')))==len(captures),'One exact companion per real failed method; none on success')
    return result


# Byte-exact prepared PIN05 public responder snapshot and decoder.
NATIVE_SNAPSHOT = '''#if DEBUG && targetEnvironment(simulator)
    private func pin05Snapshot() -> String {
        guard Thread.isMainThread, let root = contentController.viewIfLoaded else {
            MainViewControllerKt.PIN05RejectNativeStatus()
            return MainViewControllerKt.PIN05InputSnapshot()
        }
        var pending = [root]
        var visited = 0
        var first = false
        var text = false
        while let view = pending.popLast() {
            guard visited < 64, pending.count + view.subviews.count <= 64 else {
                MainViewControllerKt.PIN05RejectNativeStatus()
                return MainViewControllerKt.PIN05InputSnapshot()
            }
            visited += 1
            if view.isFirstResponder {
                first = true
                text = view is any UITextInput
            }
            pending.append(contentsOf: view.subviews)
        }
        MainViewControllerKt.PIN05NativeStatus(firstResponder: first, textResponder: text)
        return MainViewControllerKt.PIN05InputSnapshot()
    }
#endif
'''


def decode_snapshot(raw):
    require(type(raw) is bytes and 0 < len(raw) <= MAX_BYTES, 'Bounded name-input snapshot required')
    value = json.loads(raw, object_pairs_hook=pin01_receipts.reject_duplicates,
                       parse_constant=lambda _: (_ for _ in ()).throw(ValueError('Nonfinite snapshot')))
    require(type(value) is dict and set(value) == {'schema', 'nativeMode', 'overflow', 'invalid', 'rows'},
            'Unknown/private name-input field')
    require(type(value['schema']) is int and value['schema'] == 5 and
            type(value['nativeMode']) is int and value['nativeMode'] in (0, 1), 'Unknown name-input schema/mode')
    require(all(type(value[k]) is int and value[k] == 0 for k in ('overflow', 'invalid')),
            'Incomplete name-input buffer cannot qualify')
    rows = value['rows']
    require(type(rows) is list and 0 < len(rows) <= 128, 'Nonempty bounded name-input rows required')
    last = 0
    counts = {kind: 0 for kind in range(1, 6)}
    for row in rows:
        require(type(row) is list and len(row) == 10 and
                all(type(n) in (int, float) and math.isfinite(n) for n in row), 'Numeric name-input rows required')
        now, kind, phase, flag, second = row[:5]
        require(last <= now <= 1e12, 'Name-input clock reversal/overflow')
        require(kind in counts and int(kind) == kind and
                phase in ((0, 1, 2) if kind == 1 else (0,) if kind == 5 else (0, 1)) and
                flag in (0, 1) and second in (0, 1) and all(n == 0 for n in row[5:]),
                'Unknown name-input category/reserved field')
        require((kind == 5 or second == 0) and (kind not in (2, 3) or flag == 0) and
                (kind != 5 or (value['nativeMode'] == 1 and second <= flag)) and
                (kind != 1 or phase != 1 or flag == 0), 'Invalid name-input flag/mode')
        counts[kind] += 1
        last = now
    return dict(schema=5, rows=len(rows), category_counts=counts,
                scope='Numeric shared input-session/callback boundaries only; missing rows and native booleans are not causal or neutrality proof')
