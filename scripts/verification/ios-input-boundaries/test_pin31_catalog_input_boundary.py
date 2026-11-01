"""Preparation/rejection controls; fixtures are never native causal evidence."""
import copy
import hashlib
import json
from pathlib import Path
import platform
import subprocess
import tempfile
import unittest

import pin02_capture
import pin31_catalog_input_boundary as boundary
import run_pin03
import test_pin30_public_session_stress as previous


def plan():
    pair = boundary.observed.original.motion.pair
    return boundary.renderer_for_profile(boundary.PROFILE, boundary.SOURCE, pair.RUNTIME, pair.DEVICE)(
        {p: pair.fixture(p) for p in pair.MODIFIED}, pair.INPUTS, arm='compose')


def evidence(out):
    state = previous.evidence(out)
    state['profile'] = boundary.PROFILE
    dest = out/'compose'
    (dest/'inputs.json').write_text(json.dumps(state['original_inputs'] |
        {p: hashlib.sha256(b.encode()).hexdigest() for p,b in plan().items()}))
    capture = next(dest.glob('capture-*.json'))
    value = json.loads(capture.read_bytes())
    value['snapshot']['rows'] = [r for r in value['snapshot']['rows'] if r[1] not in (1, 2)]
    capture.write_text(json.dumps(value))
    state['arms'][0]['captures'] = [run_pin03.decode_capture(capture.read_bytes(), {value['token']},
        state['arms'][0]['simulator'], state['debug_bundle'])]
    return state


class CatalogInputBoundaryTest(unittest.TestCase):
    def test_only_declared_pointer_and_failed_driver_deltas_are_invertible(self):
        before = previous.plan('compose'); after = plan()
        self.assertEqual(set(before), set(after))
        for path in before:
            restored = after[path]
            if path == pin02_capture.DRIVER:
                restored = boundary.restore_driver(restored)
            elif path in boundary.REMOVALS:
                # Rebuild the exact three original additions, not arbitrary replacements.
                for removal in boundary.REMOVALS[path]:
                    self.assertEqual(before[path].count(removal), 1)
                expected = before[path]
                for removal in boundary.REMOVALS[path]: expected = expected.replace(removal, '')
                self.assertEqual(restored, expected)
                continue
            self.assertEqual(restored, before[path], path)
        for path in boundary.REMOVALS:
            self.assertNotIn('pin01PublicInput', after[path])
            self.assertEqual(after[path].count('PIN01Observation.callback'), before[path].count('PIN01Observation.callback'))
        self.assertIn('PIN01Observation.callback', after[boundary.pin01_copy.SHELL])
        self.assertIn('PIN01Observation.callback', after[boundary.catalog.HOME])
        driver = after[pin02_capture.DRIVER]
        for token in ('.tap()', '.typeText(', '.swipeUp(', '.swipeDown(', 'timeout: ', 'XCTAssert'):
            self.assertEqual(driver.count(token), before[pin02_capture.DRIVER].count(token), token)

    def test_catalog_capture_and_both_queries_follow_original_failed_wait(self):
        driver = plan()[pin02_capture.DRIVER]
        start = driver.index(boundary.NEW_CATALOG)
        end = start+len(boundary.NEW_CATALOG)
        section = driver[start:end]
        self.assertLess(section.index('waitForExistence(timeout: 10)'), section.index('if !catalogOpened'))
        self.assertLess(section.index('if !catalogOpened'), section.index('app.screenshot()'))
        self.assertLess(section.index('pin11CapturePublicFailureOnce(app)'), section.index('PIN29EquivalentQuery.capture'))
        self.assertLess(section.index('PIN29EquivalentQuery.capture'), section.index('XCTAssertTrue(catalogOpened)'))
        self.assertNotIn('.exists', boundary.REFERENCE)
        self.assertNotIn('.frame', boundary.REFERENCE)
        self.assertNotIn('.tap', boundary.NEW_CATALOG)
        with self.assertRaises(RuntimeError): boundary.extend_driver(driver)

    def test_exact_profile_native_gate_and_fixed_methods_cannot_be_replaced(self):
        pair = boundary.observed.original.motion.pair
        for args in ((boundary.PROFILE, 'a'*40, pair.RUNTIME, pair.DEVICE),
                     (boundary.PROFILE, boundary.SOURCE, 'foreign', pair.DEVICE)):
            with self.assertRaises(ValueError): boundary.renderer_for_profile(*args)
        with self.assertRaises(ValueError):
            boundary.renderer_for_profile(boundary.PROFILE, boundary.SOURCE, pair.RUNTIME, pair.DEVICE)({}, {}, arm='original')
        with self.assertRaises(ValueError): run_pin03.main(catalog_input_boundary=True, public_session_stress=True)
        self.assertEqual(run_pin03.completed_exit_code(boundary.RESULT), 0)
        self.assertEqual(run_pin03.completed_exit_code('APP_QUALIFIED'), 1)
        self.assertEqual(boundary.CASES, boundary.stress.CASES)
        self.assertEqual(boundary.TEST_TIMEOUT_SECONDS, 4500)

    def test_all_existing_companions_and_actual_failure_outcomes_remain_required(self):
        with tempfile.TemporaryDirectory() as temporary:
            out = Path(temporary).resolve(); state = evidence(out)
            self.assertEqual(boundary.accept_native_export(out, state), boundary.RESULT)
            for change in (lambda v: v.update(source='foreign'),
                           lambda v: v['arms'][0].update(cleanup='FAIL'),
                           lambda v: v['arms'][0].update(test_exit=0),
                           lambda v: v['commands'][0].update(direct_child_reaped=False),
                           lambda v: next(c for c in v['commands'] if c['label']=='compose-tests').update(timeout_seconds=4501)):
                v = copy.deepcopy(state); change(v)
                with self.subTest(change=change), self.assertRaises(ValueError): boundary.accept_native_export(out, v)
            next((out/'compose').glob('public-lifecycle-*.json')).unlink()
            with self.assertRaises((ValueError, OSError)): boundary.accept_native_export(out, state)

    def test_missing_query_or_fake_gesture_rows_never_supply_a_failure_boundary(self):
        with tempfile.TemporaryDirectory() as temporary:
            out = Path(temporary).resolve(); state = evidence(out)
            query = out/'compose/attachments/failed-01/query.json'; query.unlink()
            with self.assertRaises(ValueError): boundary.accept_native_export(out, state)
        with tempfile.TemporaryDirectory() as temporary:
            out = Path(temporary).resolve(); state = evidence(out)
            capture = next((out/'compose').glob('capture-*.json')); value = json.loads(capture.read_bytes())
            value['snapshot']['rows'].append([value['capturedMs'], 2, 3, 0, 1, 1, 0, 0, 0, 0])
            capture.write_text(json.dumps(value))
            state['arms'][0]['captures'] = [run_pin03.decode_capture(capture.read_bytes(), {value['token']},
                state['arms'][0]['simulator'], state['debug_bundle'])]
            with self.assertRaisesRegex(ValueError, 'gesture'): boundary.accept_native_export(out, state)

    def test_saved_receipt_never_claims_native_touch_causality_or_qualification(self):
        with tempfile.TemporaryDirectory() as temporary:
            out = Path(temporary).resolve(); state = evidence(out)
            boundary.accept_native_export(out, state)
            receipt = json.loads((out/'catalog-input-observations.json').read_bytes())
            self.assertIsNone(receipt['actual_completed_flows'])
            for key in ('compose_pointer_observers', 'native_touch_observer', 'application_root_cause',
                        'shipping_qualified', 'native_timing_neutrality', 'physical_verified'):
                self.assertIs(receipt[key], False)

    def test_actual_generated_driver_typechecks_against_apple_sdk(self):
        if platform.system() != 'Darwin': self.skipTest('Actual generated XCTest driver requires Apple SDK')
        sdk = subprocess.check_output(['/usr/bin/xcrun','--sdk','iphonesimulator','--show-sdk-path'], text=True, timeout=45).strip()
        platform_path = subprocess.check_output(['/usr/bin/xcrun','--sdk','iphonesimulator','--show-sdk-platform-path'], text=True, timeout=45).strip()
        with tempfile.TemporaryDirectory() as temporary:
            out = Path(temporary).resolve(); source = out/'driver.swift'; source.write_text(plan()[pin02_capture.DRIVER])
            result = subprocess.run(['/usr/bin/xcrun','swiftc','-typecheck','-D','DEBUG','-sdk',sdk,
                '-target','arm64-apple-ios18.5-simulator','-F',platform_path+'/Developer/Library/Frameworks',
                '-I',platform_path+'/Developer/usr/lib','-module-cache-path',str(out/'cache'),str(source)],
                capture_output=True, text=True, timeout=180)
            self.assertEqual(result.returncode, 0, result.stdout+result.stderr)


if __name__ == '__main__': unittest.main()
