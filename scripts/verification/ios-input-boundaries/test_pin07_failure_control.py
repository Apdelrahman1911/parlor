"""Synthetic failure-control software checks, not iOS runtime evidence."""
import hashlib
import json
import os
from pathlib import Path
import platform
import subprocess
import sys
import tempfile
import unittest

import pin01_copy
import pin02_capture
import pin06_public_setup_pair as pair
import pin07_failure_control as control


def sources():
    return {path: pair.fixture(path) for path in pair.MODIFIED}


def snapshot():
    # Public-only software fixture. These are NOT executed native event rows.
    def row(time, kind, target, phase):
        count = int(kind in (1, 2))
        pressed = int((kind, phase) in ((1, 1), (2, 0)))
        return [time, kind, target, phase, count, pressed, 0, 0, 0, 0]
    return {
        'schema': 1, 'nativeMode': 1, 'overflow': 0, 'invalid': 0,
        'rows': [row(0, 4, 0, 6), row(1, 3, 3, 0), row(2, 4, 0, 0),
                 row(3, 1, 0, 1), row(4, 2, 1, 0), row(5, 1, 0, 3),
                 row(6, 2, 1, 1), row(7, 3, 1, 0)],
    }


def encoded(value):
    return json.dumps(value, allow_nan=False).encode()


class FailedExportControlTests(unittest.TestCase):
    def test_only_two_exact_copy_deltas_and_no_original_methods_rewritten(self):
        original = sources()
        baseline = pair.render(original, pair.INPUTS, arm='native')
        plan = control.render(original, pair.INPUTS, profile=control.PROFILE)
        self.assertEqual(set(plan), set(baseline))
        modified = {path for path in plan if plan[path] != baseline[path]}
        self.assertEqual(modified, {pin01_copy.SHELL, pin02_capture.DRIVER})
        self.assertEqual(plan[pin01_copy.SHELL].replace(
            control.SUPPRESSED_CALLBACK, control.HOST_CALLBACK), baseline[pin01_copy.SHELL])
        self.assertEqual(plan[pin02_capture.DRIVER].replace(control.CONTROL_METHOD, ''),
                         baseline[pin02_capture.DRIVER])
        self.assertEqual(sources(), original)

    def test_explicit_profile_and_original_source_hashes_required(self):
        original = sources()
        for profile in (None, False, '', 'native', 'original', control.PROFILE + '-changed'):
            with self.subTest(profile=profile), self.assertRaises(ValueError):
                control.render(original, pair.INPUTS, profile=profile)
        for path in pair.MODIFIED:
            changed = original | {path: original[path] + '\n'}
            digests = pair.INPUTS | {path: hashlib.sha256(changed[path].encode()).hexdigest()}
            with self.subTest(path=path), self.assertRaises(ValueError):
                control.render(changed, digests, profile=control.PROFILE)

    def test_new_method_selects_only_public_ghamza_host_without_private_or_network_actions(self):
        method = control.CONTROL_METHOD
        self.assertEqual(method.count('func testPIN07SyntheticHostFailureCapture()'), 1)
        self.assertEqual(method.count('PIN02FailureRequest.configure(app)'), 1)
        self.assertEqual(method.count('app.launch()'), 1)
        self.assertIn('defer { app.terminate() }', method)
        self.assertIn('try tapSetupButton(app, prefix: "Open Ghamza")', method)
        self.assertEqual(method.count('try tapPublicSetupTransition(app,'), 1)
        for forbidden in ('typeText', 'textViews', 'roomCode', 'findRoom', 'tap()',
                          'swipe', 'XCTExpectFailure', 'continueAfterFailure', 'captureAfterFailedTransition'):
            self.assertNotIn(forbidden, method)
        self.assertNotIn('ShellStep.HostPermission', control.SUPPRESSED_CALLBACK)
        self.assertEqual(control.SUPPRESSED_CALLBACK.count('PIN01Observation.callback(1)'), 1)

    def test_original_failure_capture_deadlines_single_tap_and_tripwire_remain(self):
        baseline = pair.render(sources(), pair.INPUTS, arm='native')[pin02_capture.DRIVER]
        driver = control.render(sources(), pair.INPUTS, profile=control.PROFILE)[pin02_capture.DRIVER]
        for anchor in ('control.tap()', 'app.swipeUp(velocity: .slow)',
                       'destination.exists && !source.exists',
                       'XCTWaiter.wait(for: [transitioned], timeout: 10)',
                       'XCTWaiter.wait(for: [settled], timeout: 10)',
                       'PIN02FailureRequest.captureAfterFailedTransition(app)',
                       'One public setup tap must remove the source and expose its destination',
                       'Public setup must not request network access', 'continueAfterFailure = false'):
            self.assertEqual(driver.count(anchor), baseline.count(anchor), anchor)
        helper = driver[driver.index('    private func tapPublicSetupTransition'):]
        self.assertLess(helper.index('let result = XCTWaiter.wait'),
                        helper.index('PIN02FailureRequest.captureAfterFailedTransition(app)'))
        self.assertLess(helper.index('PIN02FailureRequest.captureAfterFailedTransition(app)'),
                        helper.index('XCTAssertEqual(result, .completed,'))

    def test_valid_numeric_fixture_remains_explicitly_unqualified(self):
        result = control.inspect_numeric_fixture(encoded(snapshot()))
        self.assertEqual(result['expected_failed_method'], control.METHOD)
        for key in ('source_runtime_product_cleanup_verified', 'failed_only_export_verified',
                    'native_input_neutrality', 'root_cause_proven'):
            self.assertIs(result[key], False)

    def test_missing_duplicate_foreign_or_reordered_callbacks_reject(self):
        for targets in ([], [3], [1, 3], [3, 1, 1], [3, 2], [3, 3, 1]):
            value = snapshot()
            value['rows'] = [row for row in value['rows'] if row[1] != 3]
            for index, target in enumerate(targets):
                value['rows'].append([8 + index, 3, target, 0, 0, 0, 0, 0, 0, 0])
            with self.subTest(targets=targets), self.assertRaises(ValueError):
                control.inspect_numeric_fixture(encoded(value))

    def test_stage_advance_overflow_invalid_private_fields_or_missing_delivery_reject(self):
        mutations = []
        for key, value in (('nativeMode', 0), ('overflow', 1), ('invalid', 1), ('secret', 'forbidden')):
            mutations.append((key, lambda original, k=key, v=value: original | {k: v}))
        for phase in (1, 2, 3, 4):
            mutations.append(('stage-' + str(phase), lambda original, p=phase: original | {
                'rows': original['rows'] + [[9, 4, 0, p, 0, 0, 0, 0, 0, 0]]}))
        for kind, phase in ((1, 1), (1, 3), (2, 0), (2, 1), (4, 6), (4, 0)):
            mutations.append(('missing-' + str((kind, phase)), lambda original, k=kind, p=phase: original | {
                'rows': [row for row in original['rows'] if not (row[1] == k and row[3] == p)]}))
        for name, mutation in mutations:
            with self.subTest(name=name), self.assertRaises(ValueError):
                control.inspect_numeric_fixture(encoded(mutation(snapshot())))

    def test_no_execution_adapter_workflow_or_original_profile_mutation(self):
        module = Path(control.__file__).read_text()
        for forbidden in ('subprocess', 'os.system', 'write_text(', 'write_bytes(', 'xcodebuild',
                          'simctl', 'gh run', 'shutil', 'Thread(', 'sleep('):
            self.assertNotIn(forbidden, module)
        self.assertEqual(pair.render(sources(), pair.INPUTS, arm='original'), sources())

    @unittest.skipUnless(sys.platform == 'darwin', 'Synthetic Swift driver requires actual Apple SDK')
    def test_actual_generated_failure_control_driver_typechecks_with_simulator_sdk(self):
        with tempfile.TemporaryDirectory(prefix='parlor-pin07-swift-') as temporary:
            root = Path(temporary).resolve()
            environment = dict(os.environ, TMPDIR=str(root))
            driver = root / 'Driver.swift'
            driver.write_text(control.render(sources(), pair.INPUTS,
                                             profile=control.PROFILE)[pin02_capture.DRIVER])
            sdk = subprocess.check_output(['/usr/bin/xcrun', '--sdk', 'iphonesimulator', '--show-sdk-path'],
                                          text=True, timeout=45, env=environment).strip()
            sdk_platform = subprocess.check_output([
                '/usr/bin/xcrun', '--sdk', 'iphonesimulator', '--show-sdk-platform-path'],
                text=True, timeout=45, env=environment).strip()
            architecture = {'arm64': 'arm64', 'x86_64': 'x86_64'}[platform.machine()]
            result = subprocess.run(['/usr/bin/xcrun', 'swiftc', '-typecheck', '-D', 'DEBUG', '-sdk', sdk,
                '-target', architecture + '-apple-ios16.0-simulator',
                '-F', sdk_platform + '/Developer/Library/Frameworks', '-I', sdk_platform + '/Developer/usr/lib',
                '-module-cache-path', str(root / 'cache'), str(driver)],
                capture_output=True, text=True, timeout=180, env=environment)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)


if __name__ == '__main__':
    unittest.main()
