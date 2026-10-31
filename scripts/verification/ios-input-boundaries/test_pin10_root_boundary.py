"""Fabricated software controls are not UIKit or natural failure evidence."""
import hashlib
import importlib
import json
from pathlib import Path
import platform
import subprocess
import sys
import tempfile
import unittest

import pin01_copy
import pin02_capture
import pin06_public_setup_pair as pair
import pin08_accessibility as native
import pin10_root_boundary as root
import run_pin03

old = sys.modules.get('_parlor_native_accessibility_controls') or importlib.import_module('test_pin08_accessibility')


def fixture():
    return dict(schema=1, token=old.fixture()['token'], simulator=old.fixture()['simulator'],
        bundle=old.fixture()['bundle'], process=100, capturedMs=19, installed=True, overflow=0,
        installation=[2, 1, 64, 118, 5], rows=[[10, 11, 1, 1], [11, 12, 2, 1], [12, 13, 2, 0]])


def decode(value):
    return root.decode_root(json.dumps(value).encode(), token=fixture()['token'],
        simulator=fixture()['simulator'], bundle=fixture()['bundle'], legacy=dict(process=100, capturedMs=20))


def evidence(out):
    state = old.evidence(out); state['profile'] = root.PROFILE
    plan = root.renderer_for_profile(root.PROFILE, root.SOURCE, pair.RUNTIME, pair.DEVICE)(
        {p: pair.fixture(p) for p in pair.MODIFIED}, pair.INPUTS, arm='native')
    dest = out / 'native'
    (dest / 'inputs.json').write_text(json.dumps(pair.INPUTS |
        {p: hashlib.sha256(v.encode()).hexdigest() for p, v in plan.items()}))
    value = fixture(); value.update(token=state['arms'][0]['captures'][0]['token'],
                                    simulator=state['arms'][0]['simulator'])
    path = dest / ('native-root-' + value['token'] + '.json'); path.write_text(json.dumps(value))
    legacy = json.loads(next(dest.glob('capture-*.json')).read_bytes())
    state['arms'][0]['root_companion_captures'] = [root.decode_root(path.read_bytes(), token=value['token'],
        simulator=value['simulator'], bundle=value['bundle'], legacy=legacy)]
    return state


class RootBoundaryControls(unittest.TestCase):
    def test_root_extension_is_exactly_reversible_without_driver_input_or_existing_template_changes(self):
        originals = {p: pair.fixture(p) for p in pair.MODIFIED}
        baseline = native.renderer_for_profile(native.PROFILE, native.SOURCE, pair.RUNTIME, pair.DEVICE)(
            originals, pair.INPUTS, arm='native')
        plan = root.renderer_for_profile(root.PROFILE, root.SOURCE, pair.RUNTIME, pair.DEVICE)(
            originals, pair.INPUTS, arm='native')
        self.assertEqual(set(plan), set(baseline))
        self.assertEqual([p for p in plan if plan[p] != baseline[p]], [pin02_capture.CONTAINER])
        template = '\n' + (pair.HERE / root.TEMPLATE).read_text()
        text = plan[pin02_capture.CONTAINER]; self.assertTrue(text.endswith(template)); text = text[:-len(template)]
        text = text.replace('\n    private var pin10Root: PIN10RootObserver?', '')
        text = text.replace(root.INSTALL_PREFIX, '')
        text = text.replace('snapshot: { [weak observation, weak rootObservation] in', 'snapshot: { [weak observation] in')
        text = text.replace('            rootObservation?.exportAfterFailureOnce()\n', '')
        self.assertEqual(text, baseline[pin02_capture.CONTAINER])
        self.assertEqual(plan[pin02_capture.DRIVER], baseline[pin02_capture.DRIVER])
        self.assertEqual(root.test_command(['xcodebuild'], Path('/r')), native.test_command(['xcodebuild'], Path('/r')))

    def test_exact_profile_arm_source_and_exclusive_entry_points(self):
        args = [root.PROFILE, root.SOURCE, pair.RUNTIME, pair.DEVICE]
        for i in range(4):
            changed = args.copy(); changed[i] += '-changed'
            with self.assertRaises(ValueError): root.renderer_for_profile(*changed)
        renderer = root.renderer_for_profile(*args)
        for arm in ('compose', 'original', None):
            with self.assertRaises(ValueError):
                renderer({p: pair.fixture(p) for p in pair.MODIFIED}, pair.INPUTS, arm=arm)
        for other in ('synthetic', 'current', 'accessibility', 'native_boundary', 'root_boundary'):
            with self.assertRaises(ValueError): run_pin03.main(root_capability=True, **{other: True})

    def test_only_complete_original_getter_and_setter_rows_qualify(self):
        self.assertEqual(decode(fixture())['nil_writes'], 1)
        for change in (dict(installed=False), dict(overflow=1), dict(installation=[2,1,64,118,4]),
            dict(installation=[2,0,64,118,5]), dict(installation=[2,1,81,118,5]),
            dict(rows=[[10,11,1,1]]), dict(rows=[[10,11,2,1]])):
            value = fixture(); value.update(change)
            with self.subTest(change=change), self.assertRaises(ValueError): decode(value)

    def test_private_foreign_duplicate_nonfinite_oversized_or_impossible_reports_reject(self):
        for change in (dict(private='forbidden'), dict(token='foreign'), dict(process=True), dict(schema=True),
            dict(capturedMs=21), dict(installation=[1,2,64,118,5]), dict(rows=[[10,9,1,1]]),
            dict(rows=[[10,11,True,1]]), dict(rows=[[10,11,3,1]]), dict(rows=[[10,11,1,2]]),
            dict(rows=[[float('nan'),11,1,1]]), dict(overflow=1000001)):
            value = fixture(); value.update(change)
            with self.subTest(change=change), self.assertRaises(ValueError): decode(value)
        for raw in (b'x' * (root.MAX_BYTES + 1), json.dumps(fixture()).replace('"schema": 1', '"schema": 1, "schema": 1').encode()):
            with self.assertRaises(ValueError):
                root.decode_root(raw, token=fixture()['token'], simulator=fixture()['simulator'],
                    bundle=fixture()['bundle'], legacy=dict(process=100, capturedMs=20))

    def test_root_rows_ceiling_is_enforced_without_loosening_legacy_capture(self):
        value = fixture(); value['rows'] = [[10,10,1+i%2,1] for i in range(root.MAX_ROWS)]
        self.assertEqual(decode(value)['rows'], root.MAX_ROWS)
        value['rows'].append([10,10,1,1])
        with self.assertRaises(ValueError): decode(value)

    def test_safe_incomplete_root_is_retained_exclusively_then_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp).resolve(); dest = out/'retained'; dest.mkdir()
            value = fixture(); value['overflow'] = 1
            path = out/'sandbox/tmp'/('parlor-pin10-'+value['token'])/'root.json'
            path.parent.mkdir(parents=True); raw = json.dumps(value).encode(); path.write_bytes(raw)
            with self.assertRaises(ValueError):
                root.retain_root_companion(out/'sandbox', dest, value['token'], value['simulator'],
                    value['bundle'], dict(process=100,capturedMs=20))
            self.assertEqual(next(dest.glob('native-root-*.json')).read_bytes(), raw)
            with self.assertRaises(FileExistsError):
                root.retain_root_companion(out/'sandbox', dest, value['token'], value['simulator'],
                    value['bundle'], dict(process=100,capturedMs=20))

    def test_foreign_report_never_exports(self):
        with tempfile.TemporaryDirectory() as tmp:
            out=Path(tmp).resolve(); dest=out/'retained'; dest.mkdir(); value=fixture()
            path=out/'sandbox/tmp'/('parlor-pin10-'+value['token'])/'root.json'; path.parent.mkdir(parents=True)
            path.write_text(json.dumps(value | {'private': 'forbidden'}))
            with self.assertRaises(ValueError):
                root.retain_root_companion(out/'sandbox',dest,value['token'],value['simulator'],
                    value['bundle'],dict(process=100,capturedMs=20))
            self.assertEqual(list(dest.iterdir()), [])

    def test_software_capability_requires_old_element_export_and_new_root_custody(self):
        with tempfile.TemporaryDirectory() as tmp:
            out=Path(tmp).resolve()
            self.assertEqual(root.accept_native_export(out,evidence(out)), 'SYNTHETIC_NATIVE_ROOT_CAPABILITY_ONLY')
        for mutate in (lambda o,s: s.update(profile=native.PROFILE),
            lambda o,s: next((o/'native').glob('native-root-*.json')).unlink(),
            lambda o,s: (o/'native/native-root-foreign.json').write_text('{}'),
            lambda o,s: s['arms'][0].update(root_companion_captures=[]),
            lambda o,s: s['arms'][0].update(cleanup='FAIL'),
            lambda o,s: next((o/'native').glob('native-accessibility-*.json')).unlink()):
            with tempfile.TemporaryDirectory() as tmp:
                out=Path(tmp).resolve(); state=evidence(out); mutate(out,state)
                with self.assertRaises((ValueError,FileNotFoundError)): root.accept_native_export(out,state)

    @unittest.skipUnless(platform.system() == 'Darwin', 'Actual Apple SDK unavailable on Linux')
    def test_actual_simulator_sdk_typechecks_combined_root_and_existing_observer(self):
        sdk=subprocess.check_output(['/usr/bin/xcrun','--sdk','iphonesimulator','--show-sdk-path'],text=True).strip()
        with tempfile.TemporaryDirectory() as tmp:
            file=Path(tmp)/'Observer.swift'
            file.write_text((pair.HERE/native.TEMPLATE).read_text()+'\n'+(pair.HERE/root.TEMPLATE).read_text())
            p=subprocess.run(['/usr/bin/xcrun','swiftc','-typecheck','-D','DEBUG','-sdk',sdk,
                '-target','arm64-apple-ios18.5-simulator',str(file)],capture_output=True,text=True,timeout=120)
            self.assertEqual(p.returncode,0,p.stdout+p.stderr)

    @unittest.skipUnless(platform.system() == 'Darwin', 'Actual Objective-C runtime unavailable on Linux')
    def test_actual_original_root_getter_and_inherited_setter_preserve_objects_and_nil(self):
        text=(pair.HERE/native.TEMPLATE).read_text()
        inventory=text[text.index('enum PIN08ClassInventory {'):text.index('/// COPY ONLY.')]
        driver=r'''import Foundation
import ObjectiveC.runtime
import Darwin
@objc(CMPAccessibilityElement) class Base: NSObject {
    var stored: AnyObject?
    @objc(accessibilityElements) dynamic func read() -> AnyObject? { stored }
    @objc(setAccessibilityElements:) dynamic func put(_ value: AnyObject?) { stored=value }
    @objc(isAccessibilityElement) dynamic func element() -> Bool { false }
    @objc(accessibilityTraits) dynamic func traits() -> UInt64 { 0 }
}
@objc(PIN10ActualRoot) class Root: Base {
    override func read() -> AnyObject? { super.read() }
    override func element() -> Bool { false }
}
@objc(PIN10OtherElement) class Element: Base {
    override func traits() -> UInt64 { 1 }
    override func element() -> Bool { true }
}
extension PIN10RootObserver {
    var controlRows: [[Int]] { rows.map { [$0.kind,$0.present] } }
}
_ = Element()
let object=Root(); let observation=PIN10RootObserver(armed:{true})
precondition(observation.install())
typealias Read = @convention(c) (AnyObject,Selector)->AnyObject?
typealias Put = @convention(c) (AnyObject,Selector,AnyObject?)->Void
let get=NSSelectorFromString("accessibilityElements"), set=NSSelectorFromString("setAccessibilityElements:")
let read=unsafeBitCast(method_getImplementation(class_getInstanceMethod(Root.self,get)!),to:Read.self)
let put=unsafeBitCast(method_getImplementation(class_getInstanceMethod(Root.self,set)!),to:Put.self)
let value=NSArray(object:NSObject())
put(object,set,value); precondition(read(object,get) === value)
put(object,set,nil); precondition(read(object,get)==nil)
precondition(observation.controlRows == [[2,1],[1,1],[2,0],[1,0]])
'''
        with tempfile.TemporaryDirectory() as tmp:
            file=Path(tmp)/'Root.swift'; executable=Path(tmp)/'root'
            file.write_text(inventory+'\n'+(pair.HERE/root.TEMPLATE).read_text()+'\n'+driver)
            build=subprocess.run(['/usr/bin/xcrun','swiftc','-Onone','-D','DEBUG','-D','PIN10_CONTROL',
                str(file),'-o',str(executable)],capture_output=True,text=True,timeout=120)
            self.assertEqual(build.returncode,0,build.stdout+build.stderr)
            actual=subprocess.run([str(executable)],capture_output=True,text=True,timeout=30)
            self.assertEqual(actual.returncode,0,actual.stdout+actual.stderr)


if __name__ == '__main__':
    unittest.main()
