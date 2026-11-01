"""Synthetic source/intake controls; never claimed as actual app-leaf evidence."""
import copy
import json
from pathlib import Path
import tempfile
import unittest

import pin34_touch_export_control as control
import run_pin03
import test_pin33_original_touch_delivery as previous


def evidence(out):
    state=previous.evidence(out);state['profile']=control.PROFILE;dest=out/'compose'
    row=state['arms'][0];row['methods']={control.motion.CONTROL:'Failed'}
    row['attachment_test_ids']=[control.motion.CONTROL]
    tests=json.loads((dest/'tests.json').read_bytes());tests['testNodes']=tests['testNodes'][:1]
    tests['testNodes'][0]['nodeIdentifier']=control.motion.CONTROL
    (dest/'tests.json').write_text(json.dumps(tests))
    summary=json.loads((dest/'summary.json').read_bytes());summary.update(totalTestCount=1,passedTests=0)
    summary['testFailures']=[dict(testIdentifierString=control.motion.CONTROL,
        failureText='One public setup tap must remove the source and expose its destination')]
    (dest/'summary.json').write_text(json.dumps(summary))
    for c in state['commands']:
        if c['label']=='compose-tests':
            c['timeout_seconds']=900;c['command']=control.test_command(['xcodebuild'],dest/'tests.xcresult')
    (dest/'test.log').write_text("Test Case '-[iosAppUITests.IOSAppLaunchUITests testPIN20IsolatedMotionExportControl]' started.\n")
    path=next(dest.glob('catalog-motion-*.json'));value=json.loads(path.read_bytes())
    value['motion']['syntheticControl']=True;path.write_text(json.dumps(value))
    legacy=json.loads(next(dest.glob('capture-*.json')).read_bytes())
    row['motion_companion_captures']=[control.motion.decode_motion(path.read_bytes(),token=legacy['token'],
        simulator=row['simulator'],bundle=state['debug_bundle'],legacy=legacy)]
    for p in (dest/'attachments').rglob('*.json'):
        v=json.loads(p.read_bytes())
        if type(v) is dict and 'originalWaiterFailed' in v:
            v['kind']=3
            for q in v['rows']:q.update(stringValue=None,empty=None,exact=None)
            p.write_text(json.dumps(v))
    path=next(dest.glob('native-touch-*.json'));value=json.loads(path.read_bytes())
    value['rows']=[[0,1,0,0,1],[1,5,0,0,1],[2,5,1,0,1],[3,1,1,0,1],
                   [4,3,0,0,1],[5,3,1,0,1],[6,1,0,0,1],[7,1,1,0,1],
                   [8,3,0,0,1],[9,5,0,3,1],[10,5,1,3,1],[11,3,1,0,1]]
    path.write_text(json.dumps(value))
    return state


class IsolatedTouchExportControls(unittest.TestCase):
    def test_same_entire_source_and_driver_plan_only_existing_control_selected(self):
        renderer=control.renderer_for_profile(control.PROFILE,control.SOURCE,control.pair.RUNTIME,control.pair.DEVICE)
        self.assertEqual(renderer({p:control.pair.fixture(p) for p in control.pair.MODIFIED},
            control.pair.INPUTS,arm='compose'),previous.plan())
        argv=control.test_command(['xcodebuild','-quiet'],Path('/owned/result'))
        self.assertEqual([a for a in argv if a.startswith('-only-testing:')],
            ['-only-testing:iosAppUITests/'+control.motion.CONTROL.removesuffix('()')])
        self.assertNotIn('-quiet',argv)
        self.assertEqual(run_pin03.completed_exit_code(control.RESULT),0)
        self.assertEqual(run_pin03.completed_exit_code('TOUCH_FIX_QUALIFIED'),1)
        with self.assertRaises(ValueError):run_pin03.main(original_touch_delivery=True,original_touch_export_control=True)

    def test_exact_unexpected_failure_and_all_real_companion_requirements(self):
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary);state=evidence(out)
            self.assertEqual(control.accept_native_export(out,state),control.RESULT)
            for change in (lambda s:s.update(source='foreign'),lambda s:s['arms'][0].update(cleanup='FAIL'),
                           lambda s:s['arms'][0].update(test_exit=0),
                           lambda s:s['arms'][0].update(methods={control.motion.CONTROL:'Passed'})):
                value=copy.deepcopy(state);change(value)
                with self.assertRaises(ValueError):control.accept_native_export(out,value)
            for prefix in ('native-touch-','catalog-overscroll-','native-frame-','public-lifecycle-'):
                p=next((out/'compose').glob(prefix+'*.json'));raw=p.read_bytes();p.unlink()
                with self.assertRaises((ValueError,OSError)):control.accept_native_export(out,state)
                p.write_bytes(raw)

    def test_installation_overflow_primary_begin_end_state_and_permission_negatives(self):
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary);state=evidence(out);dest=out/'compose'
            p=next(dest.glob('native-touch-*.json'));raw=p.read_bytes()
            for change in (lambda v:v.update(installed=False),lambda v:v.update(overflow=1),
                lambda v:v.update(rows=[]),lambda v:[r.__setitem__(4,0) for r in v['rows']],
                lambda v:v.update(rows=[r for r in v['rows'] if r[1]!=3]),
                lambda v:v.update(rows=[r for r in v['rows'] if r[1]!=5])):
                v=json.loads(raw);change(v);p.write_text(json.dumps(v))
                with self.assertRaises(ValueError):control.accept_native_export(out,state)
            p.write_bytes(raw);p=next(dest.glob('catalog-overscroll-*.json'));v=json.loads(p.read_bytes())
            v['overscroll']['permissionCallbacks']=1;p.write_text(json.dumps(v))
            with self.assertRaises(ValueError):control.accept_native_export(out,state)

    def test_original_profiles_cannot_admit_synthetic_motion(self):
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary);state=evidence(out);token=json.loads(next((out/'compose').glob('capture-*.json')).read_bytes())['token']
            with self.assertRaises(ValueError):control.original.verify_companion_files(out,state)
            for profile in ('original-input-lifecycle-boundaries',control.delivery.PROFILE,'foreign'):
                state['profile']=profile
                with self.assertRaises(ValueError):control.original.verify_companion_files(out,state,isolated_touch_token=token)

    def test_no_wrong_predicate_no_skips_no_nonfinite_duration_or_extra_command(self):
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary);state=evidence(out);dest=out/'compose'
            summary=json.loads((dest/'summary.json').read_bytes());tests=json.loads((dest/'tests.json').read_bytes())
            for change in (lambda s,t:s.update(skippedTests=1),lambda s,t:s.update(expectedFailures=1),
                lambda s,t:s['testFailures'][0].update(failureText='Wrong earlier predicate'),
                lambda s,t:t['testNodes'][0].update(durationInSeconds=float('nan'))):
                s,t=copy.deepcopy(summary),copy.deepcopy(tests);change(s,t)
                with self.assertRaises(ValueError):control.verify_results(s,t,state['arms'][0]['simulator'],65)
            state['commands'][1]['command'].append('-only-testing:iosAppUITests/IOSAppLaunchUITests/testPIN30Sample01English')
            with self.assertRaises(ValueError):control.accept_native_export(out,state)


if __name__=='__main__':unittest.main()
