"""Synthetic source/intake controls are not actual original XCTest evidence."""
import copy
import hashlib
import json
from pathlib import Path
import platform
import subprocess
import tempfile
import unittest

import pin02_capture
import pin29_equivalent_query_boundary as query
import run_pin03
import test_pin28_original_lifecycle_boundaries as previous


def plan():
    return query.renderer_for_profile(query.PROFILE, query.SOURCE, query.original.motion.pair.RUNTIME,
        query.original.motion.pair.DEVICE)({p:query.original.motion.pair.fixture(p) for p in query.original.motion.pair.MODIFIED},
        query.original.motion.pair.INPUTS, arm='compose')


def fixture(token='00000000-0000-4000-8000-000000000001', captured=20):
    row = dict(startedMs=captured+1, finishedMs=captured+2, snapshotPresent=True,
               enabled=True, frame=[24, 200, 354, 48], geometryValid=True,
               stringValue=None, empty=None, exact=None, errorCode=None)
    return dict(schema=1, token=token, kind=1, originalWaiterFailed=True,
                order='retained-then-equivalent', rows=[row, row|dict(startedMs=captured+3, finishedMs=captured+4)])


def intake(v):
    return query.decode_query(json.dumps(v).encode(), token=fixture()['token'], legacy=dict(capturedMs=20))


def evidence(out):
    state = previous.evidence(out); state['profile'] = query.PROFILE; dest = out/'compose'
    (dest/'inputs.json').write_text(json.dumps(state['original_inputs']|{p:hashlib.sha256(v.encode()).hexdigest() for p,v in plan().items()}))
    legacy = json.loads(next(dest.glob('capture-*.json')).read_bytes())
    attachment = dest/'attachments/failed-01/query.json'; attachment.parent.mkdir(parents=True, exist_ok=True)
    attachment.write_text(json.dumps(fixture(legacy['token'], legacy['capturedMs'])))
    for path in (dest/'attachments').rglob('*.json'):
        value = json.loads(path.read_bytes())
        if type(value) is dict and 'originalTransitionFailed' in value and path.parent != attachment.parent:
            path.rename(attachment.parent/path.name) # Reuse the existing original synthetic ACK, never duplicate it.
    return state


class EquivalentQueryControls(unittest.TestCase):
    def test_inverse_preserves_every_original_source_and_event(self):
        before = previous.plan(); after = plan()
        self.assertEqual(set(before), set(after))
        for path in before:
            restored = query.restore_driver(after[path]) if path == pin02_capture.DRIVER else after[path]
            self.assertEqual(before[path], restored, path)
        for token in ('XCTAssert', 'XCTFail(', '.tap()', '.typeText(', '.swipeUp(', '.swipeDown(', 'timeout: '):
            self.assertEqual(before[pin02_capture.DRIVER].count(token), after[pin02_capture.DRIVER].count(token), token)

    def test_only_failure_branches_probe_after_capture_before_unchanged_assertions(self):
        driver = plan()[pin02_capture.DRIVER]
        for addition in (query.PRE_TAP, query.TEXT, query.TRANSITION):
            position = driver.index(addition)
            block = driver[driver.rfind('        if result != .completed {', 0, position):position+len(addition)]
            self.assertIn('pin11CapturePublicFailureOnce(', block)
            self.assertLess(block.index('pin11CapturePublicFailureOnce('), block.index('PIN29EquivalentQuery.capture('))
            self.assertIn('XCTAssertEqual(result, .completed', driver[position+len(addition):position+len(addition)+100])

    def test_new_template_has_no_input_waiter_private_content_or_active_queries(self):
        source = query.template()
        for token in ('.tap(', '.typeText(', '.swipe', 'XCTWaiter', 'sleep(', 'Timer(', '.label', '.identifier',
                      'debugDescription', '.children(', 'DispatchQueue', 'Task {'):
            self.assertNotIn(token, source)
        self.assertEqual(source.count('try control.snapshot()'), 1)
        self.assertLess(source.index('if let expected {'), source.index('snapshot.value'))
        self.assertNotIn('snapshot.value', source[:source.index('if let expected {')])

    def test_wrong_profile_source_runtime_arm_and_mixed_entry_point_reject(self):
        for args in ((query.original.PROFILE, query.SOURCE, query.original.motion.pair.RUNTIME, query.original.motion.pair.DEVICE),
                     (query.PROFILE, 'a'*40, query.original.motion.pair.RUNTIME, query.original.motion.pair.DEVICE)):
            with self.assertRaises(ValueError): query.renderer_for_profile(*args)
        with self.assertRaises(ValueError): run_pin03.main(equivalent_query=True, original_input_lifecycle=True)
        self.assertEqual(run_pin03.completed_exit_code(query.RESULT), 0)
        self.assertEqual(run_pin03.completed_exit_code('APP_QUALIFIED'), 1)

    def test_real_missing_snapshot_is_not_an_empty_field_or_success(self):
        v = fixture(); v['rows'][0].update(snapshotPresent=False, enabled=None, geometryValid=False,
                                         frame=[], errorCode=1001)
        self.assertEqual(intake(v)['rows'], 2)
        for field, value in (('empty', True), ('exact', True), ('frame', [0, 0, 0, 0]), ('errorCode', True)):
            bad = copy.deepcopy(v); bad['rows'][0][field] = value
            with self.subTest(field=field), self.assertRaises(ValueError): intake(bad)

    def test_geometry_identity_clocks_size_and_private_fields_fail_closed(self):
        self.assertEqual(intake(fixture())['kind'], 1)
        for change in (lambda v:v.update(token='foreign'), lambda v:v.update(schema=True), lambda v:v.update(kind=True),
            lambda v:v.update(private='forbidden'), lambda v:v.update(originalWaiterFailed=False), lambda v:v.update(rows=[]),
            lambda v:v['rows'][0].update(startedMs=19), lambda v:v['rows'][1].update(startedMs=20),
            lambda v:v['rows'][0].update(frame=[0,0,-1,5]), lambda v:v['rows'][0].update(frame=[0,0,float('nan'),5]),
            lambda v:v['rows'][0].update(label='forbidden'), lambda v:v['rows'][0].update(enabled=1)):
            v = fixture(); change(v)
            with self.subTest(change=change), self.assertRaises(ValueError): intake(v)
        with self.assertRaises(ValueError): query.decode_query(b' '*16385, token=fixture()['token'], legacy=dict(capturedMs=20))
        raw = json.dumps(fixture()).replace('"schema": 1', '"schema": 1, "schema": 1').encode()
        with self.assertRaises(ValueError): query.decode_query(raw, token=fixture()['token'], legacy=dict(capturedMs=20))

    def test_exact_synthetic_name_status_never_retains_text_or_equates_nonstring_with_empty(self):
        v = fixture(); v['kind'] = 2
        for row in v['rows']:row.update(stringValue=True, empty=False, exact=True)
        self.assertEqual(intake(v)['kind'], 2)
        for field, value in (('stringValue', False), ('empty', True), ('exact', 'Table Test')):
            bad = copy.deepcopy(v); bad['rows'][0][field] = value
            with self.subTest(field=field), self.assertRaises(ValueError): intake(bad)
        for row in v['rows']:row.update(stringValue=False, empty=False, exact=False)
        self.assertEqual(intake(v)['kind'], 2)

    def test_all_prior_source_native_lifecycle_and_cleanup_gates_remain_mandatory(self):
        with tempfile.TemporaryDirectory() as temporary:
            out = Path(temporary).resolve(); state = evidence(out)
            self.assertEqual(query.accept_native_export(out, state), query.RESULT)
            for change in (lambda v:v['arms'][0].update(cleanup='FAIL'), lambda v:v.update(source='foreign'),
                lambda v:next(c for c in v['commands'] if c['label']=='compose-tests')['command'].append('-retry-tests-on-failure')):
                bad = copy.deepcopy(state); change(bad)
                with self.subTest(change=change), self.assertRaises(ValueError):query.accept_native_export(out, bad)
            next((out/'compose').glob('public-lifecycle-*.json')).unlink()
            with self.assertRaises((ValueError, OSError)): query.accept_native_export(out, state)

    def test_missing_duplicate_private_or_unowned_query_is_incomplete(self):
        for mutation in ('missing', 'duplicate', 'private', 'token'):
            with tempfile.TemporaryDirectory() as temporary:
                out = Path(temporary).resolve(); state = evidence(out); path = out/'compose/attachments/failed-01/query.json'
                if mutation == 'missing':path.unlink()
                elif mutation == 'duplicate':path.with_name('other.json').write_bytes(path.read_bytes())
                else:
                    v = json.loads(path.read_text()); v['private' if mutation=='private' else 'token'] = 'forbidden';path.write_text(json.dumps(v))
                with self.subTest(mutation=mutation), self.assertRaises((ValueError, OSError)): query.accept_native_export(out, state)

    @unittest.skipUnless(platform.system() == 'Darwin', 'Actual generated XCTest driver needs Apple SDK')
    def test_actual_copied_driver_and_new_xctest_api_typecheck(self):
        with tempfile.TemporaryDirectory() as temporary:
            out = Path(temporary).resolve(); source = out/'driver.swift'; source.write_text(plan()[pin02_capture.DRIVER])
            sdk = subprocess.check_output(['/usr/bin/xcrun','--sdk','iphonesimulator','--show-sdk-path'],text=True,timeout=45).strip()
            platform_path = subprocess.check_output(['/usr/bin/xcrun','--sdk','iphonesimulator','--show-sdk-platform-path'],text=True,timeout=45).strip()
            result = subprocess.run(['/usr/bin/xcrun','swiftc','-typecheck','-D','DEBUG','-sdk',sdk,
                '-target','arm64-apple-ios18.5-simulator','-F',platform_path+'/Developer/Library/Frameworks',
                '-I',platform_path+'/Developer/usr/lib','-module-cache-path',str(out/'cache'),str(source)],
                capture_output=True,text=True,timeout=180)
            self.assertEqual(result.returncode, 0, result.stdout+result.stderr)


if __name__ == '__main__': unittest.main()
