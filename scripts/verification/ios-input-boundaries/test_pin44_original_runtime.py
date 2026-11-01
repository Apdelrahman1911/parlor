"""Fabricated runtime/source controls, never Apple execution evidence."""
import copy
import hashlib
import json
import os
from pathlib import Path
import platform
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import pin44_original_runtime as control
import test_pin41_original_input_only as prior


def plan():
    leaf = control.pair_control.leaf
    return control.renderer_for_profile(control.PROFILE, control.SOURCE, control.RUNTIME, control.DEVICE)(
        {p: leaf.pair.fixture(p) for p in leaf.pair.MODIFIED}, leaf.pair.INPUTS, arm='compose')


def evidence(out, failures=()):
    # Change only fabricated software fixtures; no real18.5 results are remapped.
    old_cases = prior.control.CASES
    mapping = dict(zip(control.CASES, (old_cases[0], old_cases[-1])))
    state = prior.evidence(out, tuple(mapping[c] for c in failures))
    d = out / 'compose'; row = state['arms'][0]
    state.update(profile=control.PROFILE, runtime=control.RUNTIME, device=control.DEVICE)
    state['build_profile']['test_timeout_seconds'] = 900
    methods = {c: 'Failed' if c in failures else 'Passed' for c in control.CASES}
    row.update(methods=methods, test_exit=65 if failures else 0,
               result='JOURNEYS_FAILED' if failures else 'JOURNEYS_PASSED', attachment_test_ids=sorted(failures))
    summary = control.load(d / 'summary.json'); tests = control.load(d / 'tests.json')
    summary.update(totalTestCount=2, failedTests=len(failures), passedTests=2-len(failures),
                   result='Failed' if failures else 'Passed')
    for device in (summary['devicesAndConfigurations'][0]['device'], tests['devices'][0]):
        device.update(modelName='iPhone 17 Pro', osVersion='26.2')
    tests['testNodes'] = [dict(nodeType='Test Case', nodeIdentifier=c, result=v, durationInSeconds=1)
                          for c, v in methods.items()]
    (d / 'summary.json').write_text(json.dumps(summary)); (d / 'tests.json').write_text(json.dumps(tests))
    (d / 'inputs.json').write_text(json.dumps(state['original_inputs'] | {
        p: hashlib.sha256(v.encode()).hexdigest() for p, v in plan().items()}))
    command = next(c for c in state['commands'] if c['label'] == 'compose-tests')
    command.update(command=control.test_command([
        '/usr/bin/xcodebuild', '-project', 'iosApp/iosApp.xcodeproj', '-scheme', 'iosApp', '-configuration', 'Debug',
        '-sdk', 'iphonesimulator', '-destination', 'platform=iOS Simulator,id='+row['simulator'],
        '-parallel-testing-enabled', 'NO', '-maximum-concurrent-test-simulator-destinations', '1',
    ], d / 'tests.xcresult'), timeout_seconds=900)
    (d / 'test.log').write_text(''.join("Test Case '-[iosAppUITests." + c.split('/')[0] + ' ' +
        c.split('/')[1].removesuffix('()') + "]' started.\n" for c in control.CASES))
    for p in d.glob('native-touch-*.json'):
        v = control.load(p)
        v.update(schema=2, observedMethodKinds=control.pair_control.leaf.METHOD_KINDS,
                 inheritedStateSetterUntouched=True)
        v['rows'] = [r for r in v['rows'] if r[1] != 5]
        p.write_text(json.dumps(v))
    row['companion_captures'] = [control.decode_companions(d, v['token'], row['simulator'], state['debug_bundle'], v)
        for v in [control.load(p) for p in sorted(d.glob('capture-*.json'))]]
    return state


class OriginalRuntimeControls(unittest.TestCase):
    def test_original_driver_inverse_and_no_sample_wrappers_or_application_changes(self):
        leaf = control.pair_control.leaf
        v = plan(); driver = v[control.pair_control.pin02_capture.DRIVER]
        self.assertNotIn('testPIN30Sample', driver)
        driver = driver.replace('PIN44Configure', 'PIN39Configure').replace('PARLOR_PIN44_', 'PARLOR_PIN39_')
        driver = driver.replace(control.PROFILE, leaf.native.PROFILE)
        original = leaf.pair.fixture(control.pair_control.pin02_capture.DRIVER)
        # Add wrappers only to invoke the existing verified inverse; never run them.
        restored = control.pair_control.samples.restore_driver(control.pair_control.baseline.extend_driver(driver))
        self.assertEqual(restored, original + '\n' + (leaf.HERE/'PIN02FailureRequest.swift.in').read_text() + leaf.native.CONFIGURE)
        for p in v:
            if p not in control.pair_control.pin02_capture.MODIFIED:
                self.assertEqual(v[p], leaf.pair.fixture(p))
        owner = v[control.pair_control.pin02_capture.CONTAINER]
        for forbidden in ('class_addMethod(', 'objc_copyClassList', 'MainViewControllerKt.', 'addGestureRecognizer(', 'Timer('):
            self.assertNotIn(forbidden, owner)

    def test_exact_original_runtime_cases_budgets_and_pending_precursor_block(self):
        for position in range(4):
            args = [control.PROFILE, control.SOURCE, control.RUNTIME, control.DEVICE]; args[position] += '-wrong'
            with self.assertRaises(ValueError): control.renderer_for_profile(*args)
        self.assertEqual(control.TEST_TIMEOUT_SECONDS, 900)
        argv = control.test_command(['xcodebuild', '-quiet'], Path('/result'))
        self.assertEqual([a for a in argv if a.startswith('-only-testing:')], ['-only-testing:'+t for t in control.TESTS])
        self.assertEqual(len(control.CASES), 2)
        self.assertTrue(all('testAdditionalGamesExposeOnlyMultiplayerSetupIn' in c for c in control.CASES))
        control.require_pair_binding()
        for wrong in ('PENDING_TERMINAL_PAIR', 'a'*64, 'e948960af9135c2e14f53bb198940557fdea83d7f7bc3a27d356b2f6c7dd9ec0'):
            with patch.object(control, 'PAIR_RECEIPT_SHA256', wrong), self.assertRaises(ValueError):
                control.require_pair_binding()

    def test_actual_failures_preserved_and_zero_calls_require_complete_installation(self):
        for failures in ((), (control.CASES[0],), control.CASES):
            with self.subTest(failures=failures), tempfile.TemporaryDirectory() as temporary:
                out = Path(temporary).resolve(); state = evidence(out, failures)
                # Synthetic precursor binding grants no execution/publication permission.
                with patch.object(control, 'PAIR_RECEIPT_SHA256', 'e948960af9135c2e14f53bb198940557fdea83d7f7bc3a27d356b2f6c7dd9ec1'):
                    self.assertEqual(control.accept_native_export(out, state), control.RESULT)
                receipt = control.load(out/'original-runtime-journeys.json')
                self.assertEqual(len(receipt['native']), len(failures))
                self.assertEqual(receipt['methods'], state['arms'][0]['methods'])
                for key in ('application_root_cause', 'shipping_qualified', 'native_timing_neutrality', 'physical_verified'):
                    self.assertIs(receipt[key], False)

    def test_old_runtime_results_missing_cases_changed_budget_or_retry_reject(self):
        with tempfile.TemporaryDirectory() as temporary:
            out = Path(temporary).resolve(); state = evidence(out, (control.CASES[0],))
            for change in (lambda v: v.update(runtime=control.pair_control.leaf.pair.RUNTIME),
                           lambda v: v['build_profile'].update(test_timeout_seconds=1000),
                           lambda v: v['arms'][0].update(test_exit=0),
                           lambda v: v['arms'][0].update(cleanup='FAIL'),
                           lambda v: next(c for c in v['commands'] if c['label']=='compose-tests')['command'].append('-retry-tests-on-failure')):
                v = copy.deepcopy(state); change(v)
                with patch.object(control, 'PAIR_RECEIPT_SHA256', 'e948960af9135c2e14f53bb198940557fdea83d7f7bc3a27d356b2f6c7dd9ec1'), self.assertRaises(ValueError):
                    control.accept_native_export(out, v)
            summary, tests = control.load(out/'compose/summary.json'), control.load(out/'compose/tests.json')
            tests['devices'][0]['osVersion'] = '18.5'
            with self.assertRaises(ValueError): control.verify_results(summary, tests, state['arms'][0]['simulator'], 65)
            p = next((out/'compose').glob('native-touch-*.json')); v = control.load(p)
            v['inheritedStateSetterUntouched'] = False; p.write_text(json.dumps(v))
            with patch.object(control, 'PAIR_RECEIPT_SHA256', 'e948960af9135c2e14f53bb198940557fdea83d7f7bc3a27d356b2f6c7dd9ec1'), self.assertRaises(ValueError):
                control.accept_native_export(out, state)

    def test_wrong_release_destination_executable_or_duplicate_flags_reject(self):
        with tempfile.TemporaryDirectory() as temporary:
            out = Path(temporary).resolve(); state = evidence(out)
            for before, after in (('Debug', 'Release'), ('/usr/bin/xcodebuild', 'xcodebuild'),
                                  ('iphonesimulator', 'iphoneos'),
                                  ('platform=iOS Simulator,id='+state['arms'][0]['simulator'], 'platform=iOS Simulator,id=FOREIGN')):
                v = copy.deepcopy(state); command = next(c for c in v['commands'] if c['label']=='compose-tests')
                command['command'] = [after if a == before else a for a in command['command']]
                with self.assertRaises(ValueError): control.verify_commands(v, v['arms'][0])
            v = copy.deepcopy(state); next(c for c in v['commands'] if c['label']=='compose-tests')['command'] += ['-sdk', 'iphonesimulator']
            with self.assertRaises(ValueError): control.verify_commands(v, v['arms'][0])

    @unittest.skipUnless(platform.system() == 'Darwin', 'Actual Apple original-runtime driver compiler required')
    def test_driver_parses_actual_original_runtime_apple_compiler(self):
        with tempfile.TemporaryDirectory() as temporary:
            out = Path(temporary).resolve(); p = out/'driver.swift'
            p.write_text(plan()[control.pair_control.pin02_capture.DRIVER])
            r = subprocess.run(['/usr/bin/xcrun', 'swiftc', '-frontend', '-parse', '-D', 'DEBUG',
                                '-target', 'arm64-apple-ios26.2-simulator', str(p)], capture_output=True, text=True, timeout=120)
            self.assertEqual(r.returncode, 0, r.stdout+r.stderr)

    @unittest.skipUnless(platform.system() == 'Darwin', 'Actual Apple original-runtime wrapper SDK required')
    def test_wrapper_typechecks_actual_original_runtime_apple_sdk(self):
        with tempfile.TemporaryDirectory() as temporary:
            out = Path(temporary).resolve(); env = dict(os.environ, TMPDIR=str(out)); p = out/'owner.swift'
            p.write_text(plan()[control.pair_control.pin02_capture.CONTAINER])
            sdk = subprocess.check_output(['/usr/bin/xcrun', '--sdk', 'iphonesimulator', '--show-sdk-path'],
                                          text=True, env=env, timeout=45).strip()
            r = subprocess.run(['/usr/bin/xcrun', 'swiftc', '-typecheck', '-D', 'DEBUG', '-sdk', sdk,
                                '-target', 'arm64-apple-ios26.2-simulator', '-module-cache-path', str(out/'cache'), str(p)],
                               capture_output=True, text=True, env=env, timeout=180)
            self.assertEqual(r.returncode, 0, r.stdout+r.stderr)
