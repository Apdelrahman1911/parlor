"""Portable original-sample/public-scroll gates; never native observations."""
import copy
import hashlib
import json
from pathlib import Path
import tempfile
import unittest

import pin38_public_scroll_samples as control
import test_pin33_original_touch_delivery as touch
import test_pin36_public_scroll_boundary as scroll


def evidence(out):
    state = touch.evidence(out)
    state['profile'] = control.PROFILE
    state['original_inputs'][control.scroll.PERMISSION] = control.scroll.PERMISSION_SHA256
    plan = control.renderer_for_profile(control.PROFILE, control.SOURCE, control.pair.RUNTIME, control.pair.DEVICE)(
        {p:control.pair.fixture(p) for p in control.pair.MODIFIED}, control.pair.INPUTS, arm='compose')
    d = out/'compose'
    (d/'inputs.json').write_text(json.dumps(state['original_inputs'] | {
        p:hashlib.sha256(v.encode()).hexdigest() for p,v in plan.items()}))
    for path in d.glob('capture-*.json'):
        legacy = json.loads(path.read_bytes())
        v = scroll.sample()
        v.update(token=legacy['token'], simulator=state['arms'][0]['simulator'], bundle=state['debug_bundle'],
            process=legacy['process'], capturedMs=legacy['capturedMs'])
        v['scroll']['rows'] = [[0,0,0,2,1,0],[0,0,0,0,0,0]]
        (d/('public-scroll-'+legacy['token']+'.json')).write_text(json.dumps(v))
    # Independently spell XCTest's lexical sample order, not control.CASES.
    (d/'test.log').write_text(''.join(
        "Test Case '-[iosAppUITests.IOSAppLaunchUITests testPIN30Sample%02d%s]' started.\n" % (n,language)
        for n in range(1,6) for language in ('Arabic','English')))
    return state


class FixedPublicScrollControls(unittest.TestCase):
    def test_only_exact_predeclared_sample_context_has_no_retries_or_sentinel(self):
        self.assertEqual(control.CASES, control.native.CASES)
        self.assertEqual(len(control.CASES), 10)
        self.assertEqual(control.TEST_TIMEOUT_SECONDS, 4500)
        argv = control.test_command(['xcodebuild','-quiet'], Path('/owned/result'))
        self.assertEqual([a for a in argv if a.startswith('-only-testing:')], ['-only-testing:'+t for t in control.TESTS])
        self.assertFalse(any('sentinel' in a or 'retry' in a or 'test-iterations' in a for a in argv))
        self.assertEqual(control.native.run_pin03.completed_exit_code(control.RESULT), 0)
        with self.assertRaises(ValueError):
            control.native.run_pin03.main(public_scroll_samples=True, public_scroll_natural=True)

    def test_entire_previous_plan_and_original_sample_driver_restore_byte_for_byte(self):
        args = ({p:control.pair.fixture(p) for p in control.pair.MODIFIED}, control.pair.INPUTS)
        before = control.native.renderer_for_profile(control.native.PROFILE, control.SOURCE,
            control.pair.RUNTIME, control.pair.DEVICE)(*args, arm='compose')
        after = control.renderer_for_profile(control.PROFILE, control.SOURCE,
            control.pair.RUNTIME, control.pair.DEVICE)(*args, arm='compose')
        self.assertEqual(len(after), 16)
        self.assertEqual(control.scroll.restore(after), before)
        self.assertEqual(after[control.pin02_capture.DRIVER], before[control.pin02_capture.DRIVER])

    def test_all_previous_and_new_failure_companions_remain_required(self):
        with tempfile.TemporaryDirectory() as temporary:
            out = Path(temporary).resolve(); state = evidence(out)
            self.assertEqual(control.accept_native_export(out,state), control.RESULT)
            for prefix in ('public-scroll-', 'native-touch-', 'catalog-overscroll-', 'public-text-',
                           'native-root-', 'native-frame-', 'public-lifecycle-'):
                p = next((out/'compose').glob(prefix+'*.json')); raw = p.read_bytes(); p.unlink()
                with self.assertRaises((OSError,ValueError)):
                    control.accept_native_export(out,state)
                p.write_bytes(raw)
            r = json.loads((out/'public-scroll-sample-observations.json').read_bytes())
            self.assertFalse(r['shipping_qualified']); self.assertFalse(r['application_root_cause'])

    def test_wrong_order_missing_duplicates_or_repaired_command_are_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            out = Path(temporary).resolve(); state = evidence(out); p=out/'compose/test.log'
            lines = p.read_text().splitlines(keepends=True)
            for wrong in (lines[::-1],lines[:-1],lines+lines[:1]):
                p.write_text(''.join(wrong))
                with self.assertRaisesRegex(ValueError,'All ten fixed original cases'):
                    control.accept_native_export(out,state)
            p.write_text(''.join(lines))
            for change in (lambda v:v['arms'][0].update(cleanup='FAIL'),
                           lambda v:next(c for c in v['commands'] if c['label']=='compose-tests').update(timeout_seconds=4501),
                           lambda v:next(c for c in v['commands'] if c['label']=='compose-tests')['command'].append('-retry-tests-on-failure')):
                v=copy.deepcopy(state);change(v)
                with self.assertRaises(ValueError):control.accept_native_export(out,v)

    def test_all_passing_samples_require_no_failure_exports_or_scope_claim(self):
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve();state=evidence(out);d=out/'compose';row=state['arms'][0]
            row.update(test_exit=0,result='JOURNEYS_PASSED',methods=dict.fromkeys(control.CASES,'Passed'),attachment_test_ids=[])
            for key in ('captures','companion_captures','root_companion_captures','motion_companion_captures'):row[key]=[]
            next(c for c in state['commands'] if c['label']=='compose-tests')['exit_code']=0
            summary=json.loads((d/'summary.json').read_bytes());summary.update(result='Passed',passedTests=10,failedTests=0,testFailures=[])
            (d/'summary.json').write_text(json.dumps(summary))
            tests=json.loads((d/'tests.json').read_bytes())
            for node in tests['testNodes']:node['result']='Passed'
            (d/'tests.json').write_text(json.dumps(tests))
            for prefix in ('capture-','native-accessibility-','native-root-','native-frame-','catalog-motion-',
                           'public-text-','native-input-','input-attachment-','public-lifecycle-',
                           'catalog-overscroll-','native-touch-','public-scroll-'):
                for path in d.glob(prefix+'*.json'):path.unlink()
            import shutil
            shutil.rmtree(d/'attachments')
            self.assertEqual(control.accept_native_export(out,state),control.RESULT)
            r=json.loads((out/'public-scroll-sample-observations.json').read_bytes())
            self.assertEqual(r['public_scrolls'],[]);self.assertEqual(r['original_touches'],[])
            self.assertFalse(r['shipping_qualified']);self.assertFalse(r['application_root_cause'])

    def test_missing_unentered_scope_remains_absent_not_fabricated(self):
        with tempfile.TemporaryDirectory() as temporary:
            out = Path(temporary).resolve(); state=evidence(out);p=next((out/'compose').glob('public-scroll-*.json'))
            v=json.loads(p.read_bytes());v['scroll'].update(created=[0,0,0],factoryMissing=[False,False,False],rows=[])
            p.write_text(json.dumps(v))
            self.assertEqual(control.accept_native_export(out,state),control.RESULT)
            r=json.loads((out/'public-scroll-sample-observations.json').read_bytes())
            self.assertEqual(r['public_scrolls'][0]['mounted'],[False,False,False])


if __name__=='__main__':unittest.main()
