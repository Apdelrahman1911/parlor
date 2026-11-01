"""Portable natural-profile admission controls, never actual native observations."""
import copy
import hashlib
import json
from pathlib import Path
import tempfile
import unittest

import pin13_original_cohort as original_cohort
import pin37_public_scroll_natural as control
import test_pin33_original_touch_delivery as touch
import test_pin36_public_scroll_boundary as scroll


def evidence(out):
    state=touch.evidence(out);state['profile']=control.PROFILE
    state['original_inputs']=state['original_inputs']|{control.public.PERMISSION:control.public.PERMISSION_SHA256}
    d=out/'compose';row=state['arms'][0]
    row.update(methods=dict(zip(control.CASES,('Failed','Passed'))),attachment_test_ids=[control.CASES[0]])
    summary=json.loads((d/'summary.json').read_bytes());summary.update(totalTestCount=2,passedTests=1,failedTests=1)
    summary['testFailures'][0]['testIdentifierString']=control.CASES[0]
    (d/'summary.json').write_text(json.dumps(summary))
    tests=json.loads((d/'tests.json').read_bytes());cases=[n for n in tests['testNodes'] if n.get('nodeType')=='Test Case']
    tests['testNodes']=[cases[0]|dict(nodeIdentifier=control.CASES[0],result='Failed',durationInSeconds=2.0),
        cases[0]|dict(nodeIdentifier=control.CASES[1],result='Passed',durationInSeconds=2.0)]
    (d/'tests.json').write_text(json.dumps(tests))
    plan=control.renderer_for_profile(control.PROFILE,control.SOURCE,control.pair.RUNTIME,control.pair.DEVICE)(
        {p:control.pair.fixture(p) for p in control.pair.MODIFIED},control.pair.INPUTS,arm='compose')
    (d/'inputs.json').write_text(json.dumps(state['original_inputs']|{p:hashlib.sha256(v.encode()).hexdigest() for p,v in plan.items()}))
    for command in state['commands']:
        if command['label']=='compose-tests':command.update(command=control.test_command(['xcodebuild'],d/'tests.xcresult'),timeout_seconds=900)
    # Independent actual XCTest order, deliberately not generated from CASES.
    (d/'test.log').write_text(
        "Test Case '-[iosAppUITests.IOSAppLaunchUITests testAdditionalGamesExposeOnlyMultiplayerSetupInArabic]' started.\n"
        "Test Case '-[iosAppUITests.IOSAppLaunchUITests testAdditionalGamesExposeOnlyMultiplayerSetupInEnglish]' started.\n")
    legacy=json.loads(next(d.glob('capture-*.json')).read_bytes());v=scroll.sample()
    v.update(token=legacy['token'],simulator=row['simulator'],bundle=state['debug_bundle'],process=legacy['process'],capturedMs=legacy['capturedMs'])
    v['scroll']['rows']=[[0,0,0,2,1,0],[0,0,0,0,0,0]]
    (d/('public-scroll-'+legacy['token']+'.json')).write_text(json.dumps(v))
    return state


class NaturalPublicScrollControls(unittest.TestCase):
    def test_only_one_explicit_natural_entry_and_no_qualification_substitution(self):
        self.assertEqual(control.run_pin03.completed_exit_code(control.RESULT),0)
        self.assertEqual(control.run_pin03.completed_exit_code('PUBLIC_SCROLL_SHIPPING_QUALIFIED'),1)
        with self.assertRaises(ValueError):control.run_pin03.main(public_scroll_natural=True,public_scroll_export=True)
        argv=control.test_command(['xcodebuild','-quiet'],Path('/owned/result'))
        self.assertEqual([a for a in argv if a.startswith('-only-testing:')],['-only-testing:'+t for t in control.run_pin03.TESTS])
        self.assertNotIn('-quiet',argv)
        self.assertFalse(any('testPIN' in a for a in argv))

    def test_actual_order_is_independent_of_command_flags_and_remains_strict(self):
        self.assertEqual(control.CASES, (
            'IOSAppLaunchUITests/testAdditionalGamesExposeOnlyMultiplayerSetupInArabic()',
            'IOSAppLaunchUITests/testAdditionalGamesExposeOnlyMultiplayerSetupInEnglish()',
        ))
        self.assertEqual(control.CASES, tuple(
            c for c in original_cohort.CASES
            if c.startswith('IOSAppLaunchUITests/testAdditionalGames')))
        self.assertEqual(control.run_pin03.TESTS, (
            'iosAppUITests/IOSAppLaunchUITests/testAdditionalGamesExposeOnlyMultiplayerSetupInEnglish',
            'iosAppUITests/IOSAppLaunchUITests/testAdditionalGamesExposeOnlyMultiplayerSetupInArabic',
        ))
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve(); state=evidence(out); path=out/'compose/test.log'
            lines=path.read_text().splitlines(keepends=True)
            for wrong in (lines[::-1], lines[:1], lines+lines[:1], lines+lines):
                path.write_text(''.join(wrong))
                with self.assertRaisesRegex(ValueError, 'Original two methods once in original order'):
                    control.accept_native_export(out,state)
            path.write_text(''.join(lines))
            self.assertEqual(control.accept_native_export(out,state),control.RESULT)

    def test_entire_prior_plan_restores_and_original_driver_is_unchanged(self):
        plan=control.renderer_for_profile(control.PROFILE,control.SOURCE,control.pair.RUNTIME,control.pair.DEVICE)(
            {p:control.pair.fixture(p) for p in control.pair.MODIFIED},control.pair.INPUTS,arm='compose')
        before=touch.plan()
        self.assertEqual(control.public.restore(plan),before)
        self.assertEqual(plan[control.pin02_capture.DRIVER],before[control.pin02_capture.DRIVER])
        self.assertEqual(len(plan),16)

    def test_complete_natural_failure_and_all_prior_new_companions_are_required(self):
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve();state=evidence(out)
            self.assertEqual(control.accept_native_export(out,state),control.RESULT)
            receipt=json.loads((out/'public-scroll-observations.json').read_bytes())
            self.assertFalse(receipt['shipping_qualified']);self.assertFalse(receipt['application_root_cause'])
            for prefix in ('public-scroll-','native-touch-','catalog-overscroll-','public-text-','public-lifecycle-'):
                path=next((out/'compose').glob(prefix+'*.json'));raw=path.read_bytes();path.unlink()
                with self.assertRaises((ValueError,OSError)):control.accept_native_export(out,state)
                path.write_bytes(raw)

    def test_original_case_command_deadline_and_cleanup_cannot_be_repaired(self):
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve();state=evidence(out)
            for change in (lambda s:s['arms'][0].update(cleanup='FAIL'),lambda s:s.update(profile=control.public.PROFILE),
                lambda s:next(c for c in s['commands'] if c['label']=='compose-tests').update(timeout_seconds=901),
                lambda s:next(c for c in s['commands'] if c['label']=='compose-tests')['command'].append('-retry-tests-on-failure')):
                v=copy.deepcopy(state);change(v)
                with self.assertRaises(ValueError):control.accept_native_export(out,v)

    def test_pre_setup_failure_preserves_actual_empty_public_scope_without_claiming_execution(self):
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve();state=evidence(out);p=next((out/'compose').glob('public-scroll-*.json'))
            v=json.loads(p.read_bytes());v['scroll'].update(created=[0,0,0],factoryMissing=[False,False,False],rows=[])
            p.write_text(json.dumps(v))
            self.assertEqual(control.accept_native_export(out,state),control.RESULT)
            receipt=json.loads((out/'public-scroll-observations.json').read_bytes())
            self.assertEqual(receipt['observations'][0]['mounted'],[False,False,False])

    def test_successful_natural_journeys_have_no_failure_exports_and_no_shipping_claim(self):
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve();state=evidence(out);d=out/'compose';row=state['arms'][0]
            row.update(test_exit=0,result='JOURNEYS_PASSED',methods=dict.fromkeys(control.CASES,'Passed'),attachment_test_ids=[])
            for key in ('captures','companion_captures','root_companion_captures','motion_companion_captures'):row[key]=[]
            next(c for c in state['commands'] if c['label']=='compose-tests')['exit_code']=0
            summary=json.loads((d/'summary.json').read_bytes());summary.update(result='Passed',passedTests=2,failedTests=0,testFailures=[])
            (d/'summary.json').write_text(json.dumps(summary))
            tests=json.loads((d/'tests.json').read_bytes())
            for node in tests['testNodes']:node['result']='Passed'
            (d/'tests.json').write_text(json.dumps(tests))
            for prefix in ('capture-','native-accessibility-','native-root-','native-frame-','catalog-motion-',
                'public-text-','native-input-','input-attachment-','public-lifecycle-','catalog-overscroll-','native-touch-','public-scroll-'):
                for path in d.glob(prefix+'*.json'):path.unlink()
            import shutil
            shutil.rmtree(d/'attachments')
            self.assertEqual(control.accept_native_export(out,state),control.RESULT)
            receipt=json.loads((out/'public-scroll-observations.json').read_bytes())
            self.assertEqual(receipt['observations'],[]);self.assertFalse(receipt['shipping_qualified'])
            self.assertFalse(receipt['application_root_cause'])


if __name__=='__main__':unittest.main()
