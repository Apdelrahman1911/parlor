"""Fabricated rejection controls; not native root or application evidence."""
import copy
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
import pin07_failure_control as fault
import pin08_accessibility as native
import pin10_root_boundary as root
import pin18_root_idle_stimulus as idle
import run_pin03

prior = sys.modules.get('_parlor_root_boundary_controls') or importlib.import_module('test_pin10_root_boundary')


def plan():
    return idle.renderer_for_profile(idle.PROFILE, idle.SOURCE, pair.RUNTIME, pair.DEVICE)(
        {p: pair.fixture(p) for p in pair.MODIFIED}, pair.INPUTS, arm='native')


def stimulus():
    return dict(schema=1, stimulusMs=2500, gap=[20, 2520],
                samples=[[10, 19, 1, 1, 24, 270, 354, 101],
                         [2530, 2540, 1, 1, 24, 270, 354, 101]])


def evidence(out):
    state = prior.evidence(out)
    state['profile'] = idle.PROFILE
    dest = out / 'native'
    (dest / 'inputs.json').write_text(json.dumps(state['original_inputs'] |
        {p: hashlib.sha256(v.encode()).hexdigest() for p, v in plan().items()}))
    row = state['arms'][0]
    for name in ('summary.json', 'tests.json'):
        p = dest / name
        p.write_text(p.read_text().replace(native.METHOD, idle.METHOD))
    row['methods'] = {idle.METHOD: 'Failed'}
    row['attachment_test_ids'] = [idle.METHOD]
    raw_capture = next(dest.glob('capture-*.json'))
    value = json.loads(raw_capture.read_bytes()); value['capturedMs'] = 5000
    raw_capture.write_text(json.dumps(value))
    row['captures'] = [run_pin03.decode_capture(raw_capture.read_bytes(), {value['token']},
                                              row['simulator'], state['debug_bundle'])]
    (dest / 'attachments/failed-01/idle.json').write_text(json.dumps(stimulus()))
    return state


class RootIdleStimulusControls(unittest.TestCase):
    def test_only_declared_control_and_failed_attachment_change_original_plan(self):
        baseline = root.renderer_for_profile(root.PROFILE, root.SOURCE, pair.RUNTIME, pair.DEVICE)(
            {p: pair.fixture(p) for p in pair.MODIFIED}, pair.INPUTS, arm='native')
        output = plan()
        self.assertEqual([p for p in output if output[p] != baseline[p]], [pin02_capture.DRIVER])
        driver = output[pin02_capture.DRIVER]
        self.assertEqual(driver.count('Thread.sleep(forTimeInterval: 2.5)'), 1)
        self.assertEqual(driver.count(idle.STIMULUS), 1)
        self.assertEqual(driver.count(idle.FAILED_ATTACHMENT), 1)
        self.assertIn('if result != .completed {\n' + idle.FAILED_ATTACHMENT, driver)
        restored = driver.replace(idle.STIMULUS, '').replace(idle.PROPERTY, '').replace(idle.FAILED_ATTACHMENT, '')
        restored = restored.replace(idle.METHOD.split('/')[1][:-2], native.METHOD.split('/')[1][:-2])
        self.assertEqual(restored, baseline[pin02_capture.DRIVER])
        self.assertEqual(output[pin01_copy.SHELL], baseline[pin01_copy.SHELL])
        for other in ('original', 'compose'):
            with self.assertRaises(ValueError):
                idle.renderer_for_profile(idle.PROFILE,idle.SOURCE,pair.RUNTIME,pair.DEVICE)(
                    {p: pair.fixture(p) for p in pair.MODIFIED}, pair.INPUTS, arm=other)
        with self.assertRaises(ValueError): run_pin03.main(root_idle=True, observer_comparison=True)
        self.assertEqual(run_pin03.completed_exit_code(idle.RESULT), 0)
        self.assertEqual(run_pin03.completed_exit_code('ROOT_IDLE_PRODUCTION_QUALIFIED'), 1)
        args = idle.test_command(['xcodebuild'], Path('/owned/control.xcresult'))
        self.assertEqual([a for a in args if a.startswith('-only-testing:')],
                         ['-only-testing:iosAppUITests/' + idle.METHOD.removesuffix('()')])

    def test_numeric_stimulus_rejects_changed_private_unbounded_or_reordered_fields(self):
        self.assertEqual(idle.validate_stimulus(stimulus(), 5000), stimulus())
        for change in (lambda v: v.update(schema=True), lambda v: v.update(stimulusMs=2000),
            lambda v: v.update(private='unallowed'), lambda v: v.update(gap=[20,2519]),
            lambda v: v.update(gap=[20,31000]), lambda v: v['samples'].reverse(),
            lambda v: v['samples'][0].__setitem__(2,0), lambda v: v['samples'][0].__setitem__(5,float('nan')),
            lambda v: v['samples'][1].__setitem__(0,2510)):
            value = stimulus(); change(value)
            with self.assertRaises(ValueError): idle.validate_stimulus(value, 50000)

    def test_complete_control_acceptance_retains_root_gap_outcomes_without_natural_claim(self):
        for nil_write in (False, True):
            with tempfile.TemporaryDirectory() as temporary:
                out = Path(temporary).resolve(); state = evidence(out)
                if nil_write:
                    p = next((out/'native').glob('native-root-*.json'))
                    value = json.loads(p.read_bytes()); value['capturedMs'] = 3000
                    value['rows'].append([2200,2201,2,0]); p.write_text(json.dumps(value))
                    legacy = json.loads(next((out/'native').glob('capture-*.json')).read_bytes())
                    state['arms'][0]['root_companion_captures'] = [root.decode_root(p.read_bytes(),
                        token=value['token'], simulator=value['simulator'], bundle=value['bundle'], legacy=legacy)]
                self.assertEqual(idle.accept_native_export(out, state), idle.RESULT)
                report = json.loads((out/'root-idle-stimulus.json').read_bytes())
                self.assertEqual(report['nil_root_writes_during_gap'], int(nil_write))
                for key in ('naturally_reproduced','original_root_cause','shipping_qualified','physical_verified'):
                    self.assertFalse(report[key])

    def test_wrong_method_unqualified_failure_missing_or_duplicate_stimulus_rejects(self):
        for change in (lambda o,s: s['arms'][0]['methods'].update({native.METHOD: 'Failed'}),
            lambda o,s: (o/'native/attachments/failed-01/idle.json').unlink(),
            lambda o,s: (o/'native/attachments/failed-01/duplicate.json').write_text(json.dumps(stimulus())),
            lambda o,s: (o/'native/inputs.json').write_text('{}')):
            with tempfile.TemporaryDirectory() as temporary:
                out = Path(temporary).resolve(); state = evidence(out); change(out,state)
                with self.assertRaises((ValueError, KeyError)): idle.accept_native_export(out, state)

    @unittest.skipUnless(platform.system() == 'Darwin', 'Actual Apple SDK unavailable on Linux')
    def test_actual_generated_idle_control_typechecks_with_simulator_sdk(self):
        with tempfile.TemporaryDirectory() as temporary:
            out = Path(temporary).resolve(); driver = out / 'Driver.swift'
            driver.write_text(plan()[pin02_capture.DRIVER])
            sdk = subprocess.check_output(['/usr/bin/xcrun','--sdk','iphonesimulator','--show-sdk-path'],
                                          text=True, timeout=45).strip()
            platform_path = subprocess.check_output(['/usr/bin/xcrun','--sdk','iphonesimulator','--show-sdk-platform-path'],
                                                    text=True, timeout=45).strip()
            result = subprocess.run(['/usr/bin/xcrun','swiftc','-typecheck','-D','DEBUG','-sdk',sdk,
                '-target','arm64-apple-ios18.5-simulator','-F',platform_path+'/Developer/Library/Frameworks',
                '-I',platform_path+'/Developer/usr/lib','-module-cache-path',str(out/'cache'),str(driver)],
                capture_output=True,text=True,timeout=180)
            self.assertEqual(result.returncode,0,result.stdout+result.stderr)


if __name__ == '__main__':
    unittest.main()
