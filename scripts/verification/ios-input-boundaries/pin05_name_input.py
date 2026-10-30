"""Copy-only name-input callback/session observation; never a shipping repair.

Exact original source, unchanged successful driver paths and ten-second predicate.
No native execution, writes, signal, allocation, cleanup or automatic retry here.
"""
from pathlib import Path
import hashlib
import json
import math

import frozen_sources
import pin01_copy
import pin01_receipts
import pin02_capture

HERE = Path(__file__).resolve().parent
SOURCE = 'c11d8ca100a7e522878fff85bedb1f1dc9077c54'
TREE = 'cf57bb255240d26c92f888dca02105b83a38c2d9'
RUNTIME = 'com.apple.CoreSimulator.SimRuntime.iOS-18-5'
DEVICE = 'com.apple.CoreSimulator.SimDeviceType.iPhone-16-Pro'
MAIN = pin01_copy.MAIN
NAME = 'composeApp/src/commonMain/kotlin/com/parlor/app/shell/multiplayer/NameInputScreen.kt'
CONTAINER = pin02_capture.CONTAINER
DRIVER = pin02_capture.DRIVER
MODIFIED = (MAIN, NAME, CONTAINER, DRIVER)
ARMS = ('original', 'compose', 'native')
INPUTS = {
    MAIN: 'f7da68fa3b2f8ad986d758982e72eeef5f235e6c8e8e644879c63cbac5aa0b33',
    NAME: '4730d58c86ec11cb64a8a8540c523b9ce84c259d7250178bc1aa249bafa01686',
    CONTAINER: '9bdeeaf67a569f4016bc5359ae865b6df9eeaa2a9f6ee36e01041bf79000a903',
    DRIVER: 'c65fe65aee8442aa06f02007b57daaf3e9113f5f61046edb59e361c2e4a6e115',
}
FIXTURES = HERE / 'fixtures/name-commit-2026-10-30'
MANIFEST_SHA256 = '716e010cf95c0dcfba4f7d9222a15702ba61c5d7dee8d4147978cd8e3e81ca1e'
RECORDER = 'composeApp/src/commonMain/kotlin/com/parlor/app/inputcopy/PIN05TextObservation.kt'
INTERCEPTOR = 'composeApp/src/iosMain/kotlin/com/parlor/app/inputcopy/PIN05TextInputInterceptor.kt'
MAX_BYTES = 128 * 1024
TEMPLATES = {
    'PIN05TextObservation.kt.in': '88ceae03dc07cba145d32c8c6c8de58de1b976f3d3183ca8ab6174461a3a6efe',
    'PIN05TextInputInterceptor.kt.in': '282e8795c19940e4013f0173f694b0fbaebd44408aa5674c75e3984575aa8f48',
    'PIN05TextInputControlTest.kt.in': '77bcea0365b83978baac69a8d85657eda3ba2b53714209ddd5f96c97791e5a38',
    'PIN02Capture.swift.in': '4a3814846d15f854f2e7ad2510fa04795a220ed55f0d65793ee185e8d5716433',
    'PIN02FailureRequest.swift.in': '07a613781ea793d323f7a54aede4dba8c94281bbc0ff94b18b437e1d71391740',
}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def template(filename, *, directory=HERE):
    """Exact bounded regular bytes; no redirected, linked or changing copy inputs."""
    require(type(filename) is str and filename in TEMPLATES, 'Registered name-input template required')
    raw = frozen_sources._read(directory / filename, MAX_BYTES)
    require(hashlib.sha256(raw).hexdigest() == TEMPLATES[filename], 'Name-input template changed')
    return raw.decode('utf-8')


def fixture(path, *, directory=FIXTURES):
    """Software controls only; native intake must archive SOURCE, not these fixtures."""
    require(type(path) is str and path in INPUTS, 'Exact frozen name-input path required')
    raw = frozen_sources._read(directory / 'manifest.json', 32 * 1024)
    require(hashlib.sha256(raw).hexdigest() == MANIFEST_SHA256, 'Name-input fixture manifest changed')
    manifest = json.loads(raw)
    require(manifest['source'] == SOURCE and manifest['tree'] == TREE and
            set(manifest['inputs']) == set(INPUTS), 'Original fixture provenance changed')
    entry = manifest['inputs'][path]
    require(entry['sha256'] == INPUTS[path] and entry['file'] == Path(path).name + '.source',
            'Original fixture binding changed')
    value = frozen_sources._read(directory / entry['file'], MAX_BYTES)
    require(len(value) == entry['size'] and hashlib.sha256(value).hexdigest() == INPUTS[path],
            'Name-input fixture bytes changed')
    return value.decode('utf-8')


def render(sources, digests, *, arm):
    require(arm in ARMS and set(sources) == set(digests) == set(MODIFIED),
            'Exact four bindings and explicit name-input arm required')
    for path, text in sources.items():
        require(type(text) is str and 'PIN05' not in text and digests[path] == INPUTS[path] and
                hashlib.sha256(text.encode()).hexdigest() == INPUTS[path], 'Frozen name-input source changed')
    if arm == 'original':
        return dict(sources)
    # Guard the inherited renderer's template before calling it; do not edit the
    # original PIN02 helper or silently accept a changed exporter/driver copy.
    templates = {filename: template(filename) for filename in TEMPLATES}
    once = pin01_copy.once
    result = dict(sources)
    main = once(sources[MAIN], 'import androidx.compose.ui.window.ComposeUIViewController\n',
                'import androidx.compose.ui.window.ComposeUIViewController\n'
                'import com.parlor.app.inputcopy.PIN05ObserveInput\n'
                'import com.parlor.app.inputcopy.PIN05Observation\n')
    main = once(main, '        startKoinOnce()\n',
                '        PIN05Observation.clock = { platform.Foundation.NSProcessInfo.processInfo.systemUptime * 1000.0 }\n'
                f'        PIN05Observation.nativeMode = {1 if arm == "native" else 0}\n'
                '        startKoinOnce()\n')
    main = once(main, '    App()\n', '    PIN05ObserveInput { App() }\n')
    main += '''
fun PIN05InputSnapshot(): String {
    check(platform.Foundation.NSThread.isMainThread)
    return PIN05Observation.snapshot()
}
fun PIN05NativeStatus(firstResponder: Boolean, textResponder: Boolean) {
    check(platform.Foundation.NSThread.isMainThread)
    PIN05Observation.native(firstResponder, textResponder)
}
fun PIN05RejectNativeStatus() {
    check(platform.Foundation.NSThread.isMainThread)
    PIN05Observation.rejectNativeObservation()
}
'''
    result[MAIN] = main
    name = once(sources[NAME], 'import androidx.compose.runtime.Composable\n',
                'import androidx.compose.runtime.Composable\n'
                'import androidx.compose.runtime.DisposableEffect\n'
                'import androidx.compose.runtime.SideEffect\n'
                'import com.parlor.app.inputcopy.PIN05Observation\n')
    name = once(name, '    var name by remember { mutableStateOf(initial) }\n',
                '    var name by remember { mutableStateOf(initial) }\n'
                '    val pin05Owner = remember { Any() }\n'
                '    DisposableEffect(pin05Owner) {\n'
                '        PIN05Observation.mount(pin05Owner, isHost)\n'
                '        onDispose { PIN05Observation.unmount(pin05Owner) }\n'
                '    }\n'
                '    SideEffect { PIN05Observation.composed(pin05Owner, name.isEmpty()) }\n')
    name = once(name, '                            name = RoomInputPolicy.sanitizeDisplayNameInput(input)\n',
                '                            PIN05Observation.callback(pin05Owner, 0, input.isEmpty())\n'
                '                            name = RoomInputPolicy.sanitizeDisplayNameInput(input)\n'
                '                            PIN05Observation.callback(pin05Owner, 1, name.isEmpty())\n')
    result[NAME] = name
    container = pin02_capture.render_container(sources[CONTAINER])
    require(container.endswith('\n' + templates['PIN02Capture.swift.in']),
            'Inherited capture template changed while rendering')
    container = once(container, 'MainViewControllerKt.PIN01InputSnapshot()',
                     'MainViewControllerKt.PIN05InputSnapshot()')
    container = once(container, 'integer(value["schema"], range: 1...1)',
                     'integer(value["schema"], range: 5...5)')
    if arm == 'native':
        container = once(container,
            'pin02Capture = PIN02Capture.install(snapshot: { MainViewControllerKt.PIN05InputSnapshot() })',
            'pin02Capture = PIN02Capture.install(snapshot: { [weak self] in self?.pin05Snapshot() ?? "{}" })')
        container = once(container, '    private let contentController: UIViewController\n',
                         '    private let contentController: UIViewController\n' + NATIVE_SNAPSHOT)
    result[CONTAINER] = container
    driver = sources[DRIVER]
    require(driver.count('        let app = XCUIApplication()\n') == 4, 'Actual four launch owners changed')
    driver = driver.replace('        let app = XCUIApplication()\n',
                            '        let app = XCUIApplication()\n        PIN02FailureRequest.configure(app)\n')
    driver = once(driver, '        if result != .completed {\n            print("Public text commit: "',
                  '        if result != .completed {\n'
                  '            PIN02FailureRequest.captureAfterFailedTransition(app)\n'
                  '            print("Public text commit: "')
    result[DRIVER] = driver + '\n' + templates['PIN02FailureRequest.swift.in']
    result[RECORDER] = templates['PIN05TextObservation.kt.in']
    result[INTERCEPTOR] = templates['PIN05TextInputInterceptor.kt.in']
    return result


# Query only after the original predicate has FAILED. Public UIKit booleans, no
# view values/labels/classes/coordinates. Child hierarchy is bounded; not a claim
# about SpringBoard's keyboard, physical input or the original injection cause.
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
