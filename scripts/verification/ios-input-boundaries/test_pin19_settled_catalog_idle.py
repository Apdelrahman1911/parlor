"""Portable fabricated controls; actual SDK test is explicitly Apple-only."""
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
import pin18_root_idle_stimulus as idle
import pin19_settled_catalog_idle as settled
import run_pin03

prior = sys.modules.get('_parlor_root_idle_stimulus_controls') or importlib.import_module('test_pin18_root_idle_stimulus')


def plan():
    return settled.renderer_for_profile(settled.PROFILE, settled.SOURCE, pair.RUNTIME, pair.DEVICE)(
        {p: pair.fixture(p) for p in pair.MODIFIED}, pair.INPUTS, arm='native')


def evidence(out):
    state = prior.evidence(out)
    state['profile'] = settled.PROFILE
    dest = out / 'native'
    (dest / 'inputs.json').write_text(json.dumps(state['original_inputs'] |
        {p: hashlib.sha256(v.encode()).hexdigest() for p, v in plan().items()}))
    for name in ('summary.json', 'tests.json'):
        p = dest / name
        p.write_text(p.read_text().replace(idle.METHOD, settled.METHOD))
    state['arms'][0]['methods'] = {settled.METHOD: 'Failed'}
    state['arms'][0]['attachment_test_ids'] = [settled.METHOD]
    return state


class SettledCatalogIdleControls(unittest.TestCase):
    def test_cloned_helpers_preserve_original_algorithm_only_omit_final_tap(self):
        driver = prior.plan()[pin02_capture.DRIVER]
        helpers = settled.readiness_helpers(driver)
        restored = helpers.replace('pin19ObserveSettledSetupButton(', 'tapSetupButton(')
        restored = restored.replace('pin19ObserveSettledControl(', 'tapSettledControl(')
        first = driver.index('    @MainActor\n    private func tapSetupButton(')
        last = driver.index('    @MainActor\n    private func tapPublicSetupTransition(', first)
        start = driver.index('    @MainActor\n    private func tapSettledControl(')
        end = driver.index('    @MainActor\n    private func verifyKeyboardCycle(', start)
        original = driver[first:last] + driver[start:end]
        self.assertEqual(original.count('        control.tap()\n'), 1)
        self.assertEqual(restored, original.replace('        control.tap()\n', ''))

    def test_observer_reachability_calls_only_the_no_tap_clone_with_instrumented_arguments(self):
        driver=prior.plan()[pin02_capture.DRIVER]
        helpers=settled.readiness_helpers(driver)
        self.assertNotIn('tapSettledControl(',helpers)
        self.assertEqual(helpers.count('pin19ObserveSettledControl('),2)  # call and definition
        self.assertIn('try pin19ObserveSettledControl(button, pin06FailureOwner:',helpers)
        self.assertNotIn('control.tap()',helpers)
        rendered=plan()[pin02_capture.DRIVER]
        start=rendered.index('private func pin19ObserveSettledSetupButton(')
        end=rendered.index('private func pin19ObserveSettledControl(',start)
        self.assertNotIn('tapSettledControl(',rendered[start:end])
        self.assertIn('try pin19ObserveSettledControl(',rendered[start:end])

    def test_duplicate_callee_in_clone_is_rejected_not_silently_left_as_a_tap_path(self):
        driver=prior.plan()[pin02_capture.DRIVER]
        needle='                try tapSettledControl(button, pin06FailureOwner:'
        self.assertIn(needle,driver)
        drift=driver.replace(needle,'                try tapSettledControl(button)\n'+needle,1)
        with self.assertRaises(RuntimeError):settled.readiness_helpers(drift)

    def test_only_declared_driver_control_changes_original_plan(self):
        baseline = prior.plan()
        output = plan()
        self.assertEqual([p for p in output if output[p] != baseline[p]], [pin02_capture.DRIVER])
        driver = output[pin02_capture.DRIVER]
        stimulus = idle.STIMULUS.replace(
            'let source = setupButton(app, prefix: "Open a room and show a code your friends can join.")',
            'let source = setupButton(app, prefix: "Open Word Impostor")')
        restored = pin01_copy.once(driver, settled.readiness_helpers(baseline[pin02_capture.DRIVER]), '')
        restored = pin01_copy.once(restored,
            '        try pin19ObserveSettledSetupButton(app, prefix: "Open Word Impostor")\n' +
            stimulus + '        try tapSetupButton(app, prefix: "Open Word Impostor")\n',
            '        try tapSetupButton(app, prefix: "Open Ghamza")\n' + idle.STIMULUS)
        restored = restored.replace(settled.METHOD.split('/')[1][:-2], idle.METHOD.split('/')[1][:-2])
        self.assertEqual(restored, baseline[pin02_capture.DRIVER])
        self.assertEqual(driver.count('Thread.sleep(forTimeInterval: 2.5)'), 1)
        self.assertEqual(driver.count(idle.FAILED_ATTACHMENT), 1)
        for change in ((idle.PROFILE, settled.SOURCE, pair.RUNTIME, pair.DEVICE),
                       (settled.PROFILE, 'main', pair.RUNTIME, pair.DEVICE)):
            with self.assertRaises(ValueError): settled.renderer_for_profile(*change)
        for other in ('original', 'compose'):
            with self.assertRaises(ValueError):
                settled.renderer_for_profile(settled.PROFILE, settled.SOURCE, pair.RUNTIME, pair.DEVICE)(
                    {p: pair.fixture(p) for p in pair.MODIFIED}, pair.INPUTS, arm=other)
        with self.assertRaises(ValueError): run_pin03.main(settled_idle=True, root_idle=True)
        self.assertEqual(run_pin03.completed_exit_code(settled.RESULT), 0)
        self.assertEqual(run_pin03.completed_exit_code('SETTLED_CATALOG_PRODUCTION_QUALIFIED'), 1)
        self.assertEqual([a for a in settled.test_command(['xcodebuild'], Path('/owned/test.xcresult'))
                          if a.startswith('-only-testing:')],
            ['-only-testing:iosAppUITests/' + settled.METHOD.removesuffix('()')])

    def test_positive_baseline_accepts_either_after_status_without_causal_claim(self):
        for after in ([2530, 2540, 1, 0, 24, 270, 354, 101],
                      [2530, 2540, 1, 1, 24, 270, 354, 101]):
            with tempfile.TemporaryDirectory() as temporary:
                out = Path(temporary).resolve(); state = evidence(out)
                value = prior.stimulus(); value['samples'][1] = after
                (out/'native/attachments/failed-01/idle.json').write_text(json.dumps(value))
                self.assertEqual(settled.accept_native_export(out, state), settled.RESULT)
                self.assertEqual(settled.accept_native_export(out, state), settled.RESULT)
                report = json.loads((out/'settled-catalog-idle.json').read_bytes())
                self.assertEqual(report['stimulus']['samples'][1], after)
                for key in ('original_root_cause', 'shipping_qualified', 'physical_verified'):
                    self.assertFalse(report[key])

    def test_absent_disabled_or_zero_baseline_cannot_substitute_for_settled_control(self):
        for first in ([10,19,0,0,0,0,0,0], [10,19,1,0,24,270,354,101],
                      [10,19,1,1,24,270,0,101], [10,19,1,1,24,270,354,0]):
            with tempfile.TemporaryDirectory() as temporary:
                out = Path(temporary).resolve(); state = evidence(out)
                value = prior.stimulus(); value['samples'][0] = first
                (out/'native/attachments/failed-01/idle.json').write_text(json.dumps(value))
                with self.assertRaises(ValueError): settled.accept_native_export(out, state)
                self.assertFalse((out/'settled-catalog-idle.json').exists())

    def test_wrong_method_source_missing_duplicate_or_modified_custody_rejects(self):
        for change in (lambda o,s: s['arms'][0]['methods'].update({idle.METHOD:'Failed'}),
            lambda o,s: (o/'native/inputs.json').write_text('{}'),
            lambda o,s: (o/'native/attachments/failed-01/idle.json').unlink(),
            lambda o,s: (o/'native/attachments/failed-01/duplicate.json').write_text(json.dumps(prior.stimulus())),
            lambda o,s: (o/'settled-catalog-idle.json').write_text('{}')):
            with tempfile.TemporaryDirectory() as temporary:
                out = Path(temporary).resolve(); state = evidence(out); change(out,state)
                with self.assertRaises((ValueError, KeyError)): settled.accept_native_export(out, state)

    @unittest.skipUnless(platform.system() == 'Darwin', 'Actual Apple SDK unavailable on Linux')
    def test_actual_generated_settled_catalog_control_typechecks_with_simulator_sdk(self):
        with tempfile.TemporaryDirectory() as temporary:
            out = Path(temporary).resolve(); driver = out/'Driver.swift'
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
