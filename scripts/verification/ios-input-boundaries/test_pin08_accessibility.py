"""Native getter source/decoder controls; software data is not native evidence."""
import copy
import hashlib
import json
from pathlib import Path
import platform
import subprocess
import tempfile
import unittest

import pin01_copy
import pin02_capture
import pin06_public_setup_pair as pair
import pin07_failure_control as fault
import pin08_accessibility as native
import run_pin03
import sys
if '_parlor_native_failure_export_controls' in sys.modules:
    earlier_controls = sys.modules['_parlor_native_failure_export_controls']
else:
    import test_pin07_native_adapter as earlier_controls


def sources():
    return {p: pair.fixture(p) for p in pair.MODIFIED}


def fixture():
    return dict(schema=2, token='00000000-0000-4000-8000-000000000001',
        simulator='00000000-0000-4000-8000-000000000002', bundle='me.parlor.ios.debug', process=100,
        capturedMs=20, installed=True, overflow=0, installation=[5000,2,2,1,81,66,6], rows=[[10,11,1,1,1],[12,13,1,2,1]])


def decode(value):
    v = fixture()
    return native.decode_native(json.dumps(value).encode(), token=v['token'], simulator=v['simulator'],
                                bundle=v['bundle'], legacy=dict(process=100, capturedMs=21))


def evidence(out):
    """New software fixtures only, not relabeled historical/native evidence."""
    state = earlier_controls.evidence(out)
    state.update(source=native.SOURCE, source_tree=native.TREE, profile=native.PROFILE)
    row = state['arms'][0]; dest = out / 'native'
    for name in ('summary', 'tests'):
        text = (dest / (name + '.json')).read_text().replace(earlier_controls.adapter.METHOD, native.METHOD)
        (dest / (name + '.json')).write_text(text)
    row.update(methods={native.METHOD: 'Failed'}, attachment_test_ids=[native.METHOD])
    plan = native.renderer_for_profile(native.PROFILE, native.SOURCE, pair.RUNTIME, pair.DEVICE)(
        sources(), pair.INPUTS, arm='native')
    earlier_controls.write(dest / 'inputs.json', pair.INPUTS |
        {p: hashlib.sha256(v.encode()).hexdigest() for p, v in plan.items()})
    value = fixture(); value.update(token=earlier_controls.TOKEN, simulator=earlier_controls.DEVICE)
    value['capturedMs'] = 19
    path = dest / ('native-accessibility-' + earlier_controls.TOKEN + '.json')
    earlier_controls.write(path, value)
    row['companion_captures'] = [native.decode_native(path.read_bytes(), token=earlier_controls.TOKEN,
        simulator=earlier_controls.DEVICE, bundle=earlier_controls.BUNDLE, legacy=dict(process=100, capturedMs=20))]
    return state


class NativeAccessibilityControls(unittest.TestCase):
    def test_exact_full_current_source_and_single_distinct_capability_method(self):
        self.assertEqual(native.SOURCE, '5029b3f48c2d012b34dd2748f809dc775276a622')
        self.assertEqual(native.TREE, 'fcf9abf3dba3ee40c4c9aec7966260f8d848ff53')
        values = [native.PROFILE, native.SOURCE, pair.RUNTIME, pair.DEVICE]
        renderer = native.renderer_for_profile(*values)
        with self.assertRaises(ValueError):
            renderer(sources(), pair.INPUTS, arm='original')
        for index in range(4):
            wrong = values.copy(); wrong[index] += '-wrong'
            with self.assertRaises(ValueError):
                native.renderer_for_profile(*wrong)
        command = native.test_command(['xcodebuild'], Path('/r'))
        self.assertEqual(sum(v.startswith('-only-testing:') for v in command), 1)
        self.assertTrue(command[-1].endswith('/testPIN08SyntheticAccessibilityCapture'))
        for forbidden in ('retry', 'iterations', 'testAdditionalGames'):
            self.assertNotIn(forbidden, ' '.join(command))

    def test_inverse_restoration_preserves_every_original_action_assertion_and_other_file(self):
        original = fault.render(sources(), pair.INPUTS, profile=fault.PROFILE)
        plan = native.renderer_for_profile(native.PROFILE, native.SOURCE, pair.RUNTIME, pair.DEVICE)(
            sources(), pair.INPUTS, arm='native')
        self.assertEqual(set(plan), set(original))
        for p in plan:
            if p not in (pin02_capture.DRIVER, pin02_capture.CONTAINER):
                self.assertEqual(plan[p], original[p])
        self.assertEqual(plan[pin02_capture.DRIVER].replace('testPIN08SyntheticAccessibilityCapture',
            'testPIN07SyntheticHostFailureCapture'), original[pin02_capture.DRIVER])
        template = '\n' + (pair.HERE / native.TEMPLATE).read_text()
        container = plan[pin02_capture.CONTAINER]
        self.assertTrue(container.endswith(template))
        container = container[:-len(template)].replace(native.INSTALL,
            '        pin02Capture = PIN02Capture.install(snapshot: { MainViewControllerKt.PIN01InputSnapshot() })')
        container = container.replace('\n    private var pin08Accessibility: PIN08AccessibilityObserver?', '')
        self.assertEqual(container, original[pin02_capture.CONTAINER])
        self.assertEqual(native.INSTALL.count('exportAfterFailureOnce()'), 1)

    def test_observer_never_queries_labels_values_or_hierarchies_or_synthesizes_input(self):
        text = (pair.HERE / native.TEMPLATE).read_text()
        self.assertEqual(hashlib.sha256(text.encode()).hexdigest(), native.TEMPLATE_SHA256)
        for forbidden in ('accessibilityLabel', 'accessibilityValue', 'accessibilityElements',
                          'performSelector', '.tap()', '.typeText(', 'UIAccessibilityPostNotification',
                          'Timer(', 'DispatchQueue', 'print(', 'displayName', 'roomCode'):
            self.assertNotIn(forbidden, text)
        for expected in ('"CMPAccessibilityElement"', 'matches.count == 1',
                         'Self.signature(traits, returns: "Q")', 'Self.signature(element, returns: "B")',
                         'let result = readTraits(object, traitsSelector)',
                         'let result = readElement(object, elementSelector)',
                         'Thread.isMainThread, armed()', 'O_EXCL | O_NOFOLLOW',
                         'private static let capacity = 16_384', 'return result'):
            self.assertIn(expected, text)
        self.assertEqual(text.count('return result'), 2)
        self.assertIn('not lifetime IDs or root cause', decode(fixture())['scope'])

    def test_swift_inventory_never_materializes_unverified_runtime_classes(self):
        text = (pair.HERE / native.TEMPLATE).read_text()
        self.assertIn('Thread.isMainThread && hooks.isEmpty)', text)
        self.assertNotIn('hooks.isEmpty()', text)
        self.assertEqual(text.count('objc_copyClassList(&count)'), 1)
        self.assertEqual(text.count('defer { free(storage) }'), 1)
        self.assertNotIn('classes[$0]', text)
        self.assertNotIn('classes[index]', text)
        self.assertIn('guard count <= 100_000 else { return Inventory', text)
        self.assertIn('storage.load(fromByteOffset:', text)
        self.assertIn('guard isNSObject else { continue }', text)
        self.assertEqual(text.count('unsafeBitCast(candidate, to: AnyClass.self)'), 1)
        self.assertIn('for _ in 0..<32', text)
        for symbol in ('class_getSuperclass', 'class_getName'):
            self.assertIn('dlsym(runtime, "' + symbol + '")', text)
        self.assertIn('typealias Superclass = @convention(c) (UnsafeRawPointer) -> UnsafeRawPointer?', text)
        self.assertIn('typealias ClassName = @convention(c) (UnsafeRawPointer) -> UnsafePointer<CChar>', text)
        self.assertIn('the unique subclass overriding BOTH', text)
        # Native compilation and executable controls below verify the actual ABI.

    def test_valid_software_rows_only_attest_shape_never_native_or_root_cause(self):
        result = decode(fixture())
        self.assertEqual(result['rows'], 2)
        self.assertEqual(result['native'], dict(traits_reads=1, element_reads=1,
            disabled_button_reads=0, non_element_reads=0))
        self.assertNotIn('application_qualified', result)

    def test_foreign_private_missing_or_incomplete_companion_rejects(self):
        mutations = [lambda v: v.update(token='foreign'), lambda v: v.update(simulator='foreign'),
            lambda v: v.update(bundle='foreign.debug'), lambda v: v.update(process=True),
            lambda v: v.update(process=101), lambda v: v.update(installed=False),
            lambda v: v.update(overflow=1), lambda v: v.update(rows=[]),
            lambda v: v.update(private='forbidden'), lambda v: v.update(capturedMs=22),
            lambda v: v.update(capturedMs=float('nan')), lambda v: v.update(schema=True)]
        for change in mutations:
            value = fixture(); change(value)
            with self.subTest(value=value), self.assertRaises(ValueError):
                decode(value)

    def test_invalid_getter_enums_nonfinite_reversed_clocks_and_lost_reads_reject(self):
        for rows in ([[10,11,1,1,4],[12,13,1,2,1]], [[10,11,1,2,2],[12,13,1,1,1]],
                     [[12,11,1,1,1],[12,13,1,2,1]], [[10,11,1,1,1]],
                     [[10,11,0,1,1],[12,13,1,2,1]], [[10,11,1,1,True],[12,13,1,2,1]],
                     [[10,11,1,1,1],[9,10,1,2,1]], [[10,11,1.5,1,1],[12,13,1,2,1]]):
            value = fixture(); value['rows'] = rows
            with self.subTest(rows=rows), self.assertRaises(ValueError):
                decode(value)

    def test_duplicate_json_and_oversized_companions_cannot_be_accepted(self):
        value = fixture()
        for raw in (json.dumps(value).replace('"schema": 2', '"schema": 2, "schema": 2').encode(),
                    b'x' * (native.MAX_BYTES + 1)):
            with self.assertRaises(ValueError):
                native.decode_native(raw, token=value['token'], simulator=value['simulator'], bundle=value['bundle'],
                                     legacy=dict(process=100,capturedMs=21))

    def test_native_failure_export_profile_does_not_change_ordinary_driver_commands(self):
        self.assertEqual(run_pin03.test_command(['xcodebuild'], Path('/r'))[-2:],
            ['-only-testing:' + t for t in run_pin03.TESTS])
        with self.assertRaises(ValueError):
            native.failed_method_exports(Path('/r'), Path('/d'), {native.METHOD: 'Passed'})
        with self.assertRaises(ValueError):
            native.verify_results({}, {}, 'device', 0)

    def test_complete_software_collector_is_distinct_capability_never_application_qualification(self):
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp).resolve()
            self.assertEqual(native.accept_native_export(out, evidence(out)),
                             'SYNTHETIC_NATIVE_ACCESSIBILITY_CAPABILITY_ONLY')

    def test_missing_foreign_changed_duplicate_companion_or_old_source_never_completes_collection(self):
        mutations = [lambda out,s: s.update(source=pair.SOURCE),
            lambda out,s: s['arms'][0].update(companion_captures=[]),
            lambda out,s: (out / 'native' / ('native-accessibility-' + earlier_controls.TOKEN + '.json')).unlink(),
            lambda out,s: earlier_controls.write(out / 'native/native-accessibility-duplicate.json', fixture()),
            lambda out,s: s['commands'][0].update(direct_child_reaped=False),
            lambda out,s: s['arms'][0].update(test_exit=0),
            lambda out,s: s.update(original_inputs={}),
            lambda out,s: s['arms'][0].update(cleanup='FAIL')]
        for change in mutations:
            with tempfile.TemporaryDirectory() as tmp:
                out = Path(tmp).resolve(); state = evidence(out); change(out, state)
                with self.assertRaises((ValueError, FileNotFoundError)):
                    native.accept_native_export(out, state)

    def test_incomplete_but_safe_native_status_is_retained_then_rejected_not_qualified(self):
        for update in (dict(installed=False, rows=[], installation=[5000,2,2,0,0,0,3]),
                       dict(overflow=1), dict(overflow=native.MAX_OVERFLOW)):
            with tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp).resolve(); sandbox = root / 'sandbox'; dest = root / 'retained'; dest.mkdir()
                value = fixture(); value.update(update)
                source = sandbox / 'tmp' / ('parlor-pin08-' + value['token']) / 'accessibility.json'
                source.parent.mkdir(parents=True); raw = json.dumps(value).encode(); source.write_bytes(raw)
                with self.assertRaises(ValueError):
                    native.retain_companion(sandbox, dest, value['token'], value['simulator'], value['bundle'],
                                            dict(process=100, capturedMs=21))
                retained = dest / ('native-accessibility-' + value['token'] + '.json')
                self.assertEqual(retained.read_bytes(), raw)
                with self.assertRaises(FileExistsError):
                    native.retain_companion(sandbox, dest, value['token'], value['simulator'], value['bundle'],
                                            dict(process=100, capturedMs=21))
                self.assertEqual(retained.read_bytes(), raw)

    def test_private_or_foreign_native_report_is_rejected_before_any_retention(self):
        for update in (dict(private='forbidden'), dict(token='foreign'), dict(installation=[0]*8),
                       dict(installation=[5000,2,2,1,81,66,7]), dict(installation=[1,2,2,1,81,66,6])):
            with tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp).resolve(); sandbox = root / 'sandbox'; dest = root / 'retained'; dest.mkdir()
                value = fixture(); token = value['token']; value.update(update)
                source = sandbox / 'tmp' / ('parlor-pin08-' + token) / 'accessibility.json'
                source.parent.mkdir(parents=True); source.write_text(json.dumps(value))
                with self.assertRaises(ValueError):
                    native.retain_companion(sandbox, dest, token, value['simulator'], value['bundle'],
                                            dict(process=100, capturedMs=21))
                self.assertEqual(list(dest.iterdir()), [])

    def test_old_schema_or_false_installation_success_never_qualifies(self):
        for update in (dict(schema=1), dict(installation=[5000,2,2,1,81,66,5]),
                       dict(installation=[5000,2,2,0,81,66,6]), dict(installation=[5000,2,2,1,81,99,6]),
                       dict(installation=[5000,2,2,1,True,66,6])):
            value = fixture(); value.update(update)
            with self.assertRaises(ValueError):
                decode(value)

    def test_native_capacity_is_bounded_and_legacy_file_ceiling_is_not_expanded(self):
        value = fixture()
        value['rows'] = [[10+i/100000, 10+i/100000+0.000001, 1, 1+i%2, 1]
                         for i in range(native.MAX_ROWS)]
        raw = json.dumps(value).encode()
        self.assertGreater(len(raw), pin02_capture.MAX_BYTES)
        self.assertLess(len(raw), native.MAX_BYTES)
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp).resolve(); sandbox = root / 'sandbox'; dest = root / 'retained'; dest.mkdir()
            source = sandbox / 'tmp' / ('parlor-pin08-' + value['token']) / 'accessibility.json'
            source.parent.mkdir(parents=True); source.write_bytes(raw)
            with self.assertRaises(ValueError):
                run_pin03.read_capture(source)
            for maximum in (0, True, native.MAX_BYTES+1, 64*1024*1024):
                with self.subTest(maximum=maximum), self.assertRaises(ValueError):
                    run_pin03.read_capture(source, maximum=maximum)
            result = native.retain_companion(sandbox, dest, value['token'], value['simulator'], value['bundle'],
                                             dict(process=100, capturedMs=21))
            self.assertEqual(result['rows'], native.MAX_ROWS)
            self.assertEqual((dest / ('native-accessibility-' + value['token'] + '.json')).read_bytes(), raw)
        value['rows'].append(value['rows'][-1])
        with self.assertRaises(ValueError):
            decode(value)
        value = fixture(); value['overflow'] = native.MAX_OVERFLOW+1
        with self.assertRaises(ValueError):
            decode(value)

    @unittest.skipUnless(platform.system() == 'Darwin', 'Actual Apple runtime unavailable on Linux')
    def test_actual_objc_inventory_handles_unretainable_root_and_two_compose_subclasses(self):
        text = (pair.HERE / native.TEMPLATE).read_text()
        inventory = text[text.index('enum PIN08ClassInventory {'):text.index('/// COPY ONLY.')]
        # Separate macOS executable, never the application/Simulator or evidence.
        # A registered non-NSObject root cannot receive +retain: old map crashes.
        driver = r'''import Foundation
import ObjectiveC.runtime
import Darwin
@objc(CMPAccessibilityElement) class Base: NSObject {}
@objc(PIN08ControlRoot) class Root: Base {}
@objc(PIN08ControlElement) class Element: Base {}
let handle = dlopen(nil, RTLD_LAZY)!
typealias Allocate = @convention(c) (UnsafeRawPointer?, UnsafePointer<CChar>, Int) -> UnsafeRawPointer?
typealias Register = @convention(c) (UnsafeRawPointer) -> Void
let allocate = unsafeBitCast(dlsym(handle, "objc_allocateClassPair")!, to: Allocate.self)
let register = unsafeBitCast(dlsym(handle, "objc_registerClassPair")!, to: Register.self)
let unrelated = "PIN08UnretainableRoot".withCString { allocate(nil, $0, 0) }!
register(unrelated)
_ = Root(); _ = Element()
let result = PIN08ClassInventory.composeSubclasses().classes
precondition(Set(result.map { String(cString: class_getName($0)) }) ==
             Set(["PIN08ControlRoot", "PIN08ControlElement"]))
dlclose(handle)
'''
        with tempfile.TemporaryDirectory() as tmp:
            file = Path(tmp) / 'Inventory.swift'; executable = Path(tmp) / 'inventory'
            file.write_text(inventory + driver)
            build = subprocess.run(['/usr/bin/xcrun', 'swiftc', '-Onone', str(file), '-o', str(executable)],
                                   capture_output=True, text=True, timeout=120)
            self.assertEqual(build.returncode, 0, build.stdout + build.stderr)
            run = subprocess.run([str(executable)], capture_output=True, text=True, timeout=30)
            self.assertEqual(run.returncode, 0, run.stdout + run.stderr)

    @unittest.skipUnless(platform.system() == 'Darwin', 'Actual Apple SDK unavailable on Linux')
    def test_actual_simulator_sdk_typechecks_native_getter_abi_and_export_template(self):
        sdk = subprocess.check_output(['/usr/bin/xcrun', '--sdk', 'iphonesimulator', '--show-sdk-path'], text=True).strip()
        with tempfile.TemporaryDirectory() as tmp:
            file = Path(tmp) / 'Observer.swift'
            file.write_text((pair.HERE / native.TEMPLATE).read_text())
            completed = subprocess.run(['/usr/bin/xcrun', 'swiftc', '-typecheck', '-D', 'DEBUG', '-sdk', sdk,
                '-target', 'arm64-apple-ios18.5-simulator', str(file)], capture_output=True, text=True, timeout=120)
            self.assertEqual(completed.returncode, 0, completed.stdout + completed.stderr)


if __name__ == '__main__':
    unittest.main()
