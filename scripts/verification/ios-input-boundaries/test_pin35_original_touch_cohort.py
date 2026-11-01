"""Portable profile rejection fixtures; not actual application observations."""
import copy
import hashlib
import json
import shutil
from pathlib import Path
import tempfile
import unittest

import pin02_capture
import pin35_original_touch_cohort as control
import run_pin03
import test_pin15_combined_xcode as combined
import test_pin33_original_touch_delivery as delivery


def evidence(out,failed=False):
    state=combined.evidence(out,failed)
    state['profile']=control.PROFILE
    state['original_inputs']=state['original_inputs']|{control.original.text.NAME:control.original.text.NAME_SHA256}
    dest=out/'compose'
    (dest/'inputs.json').write_text(json.dumps(state['original_inputs']|
        {p:hashlib.sha256(v.encode()).hexdigest() for p,v in delivery.plan().items()}))
    tests=json.loads((dest/'tests.json').read_bytes())
    for node in tests['testNodes']:node['durationInSeconds']=2.0
    tests.setdefault('testPlanConfigurations',[{}])
    (dest/'tests.json').write_text(json.dumps(tests))
    state['arms'][0]['motion_companion_captures']=[]
    next(c for c in state['commands'] if c['label']=='compose-gradle-stop')['timeout_seconds']=120
    return state


def failed_touch_evidence(out):
    state=delivery.evidence(out)
    with tempfile.TemporaryDirectory() as temporary:
        previous=Path(temporary).resolve();context=evidence(previous,True)
        for name in ('summary.json','tests.json','test.log','kernel-before-build.json','kernel-after-tests.json'):
            shutil.copyfile(previous/'compose'/name,out/'compose'/name)
    state.update(profile=control.PROFILE,commands=context['commands'],build_profile=context['build_profile'])
    row=state['arms'][0];original=context['arms'][0]
    for key in ('test_exit','methods','attachment_test_ids','combined_exit','product_manifest_stage',
                'build_success_evidence','kernel_resources'):
        row[key]=original[key]
    return state


class OriginalTouchCohortControls(unittest.TestCase):
    def test_added_name_identity_does_not_mutate_shared_frozen_fixture_inputs(self):
        before=dict(control.pair.INPUTS)
        with tempfile.TemporaryDirectory() as temporary:
            state=evidence(Path(temporary).resolve())
            self.assertEqual(control.pair.INPUTS,before)
            self.assertIsNot(state['original_inputs'],control.pair.INPUTS)
        self.assertEqual(delivery.plan(),control.renderer_for_profile(control.PROFILE,control.SOURCE,
            control.pair.RUNTIME,control.pair.DEVICE)({p:control.pair.fixture(p) for p in control.pair.MODIFIED},
            control.pair.INPUTS,arm='compose'))

    def test_entire_existing_application_driver_plan_is_unchanged(self):
        renderer=control.renderer_for_profile(control.PROFILE,control.SOURCE,control.pair.RUNTIME,control.pair.DEVICE)
        self.assertEqual(renderer({p:control.pair.fixture(p) for p in control.pair.MODIFIED},
            control.pair.INPUTS,arm='compose'),delivery.plan())
        for profile,source,runtime,device in (('foreign',control.SOURCE,control.pair.RUNTIME,control.pair.DEVICE),
            (control.PROFILE,'foreign',control.pair.RUNTIME,control.pair.DEVICE),
            (control.PROFILE,control.SOURCE,'foreign',control.pair.DEVICE)):
            with self.assertRaises(ValueError):control.renderer_for_profile(profile,source,runtime,device)

    def test_only_exact_original23_identifiers_single_combined_action_selected(self):
        argv=control.test_command(['/usr/bin/xcodebuild','-quiet'],Path('/owned/result'))
        self.assertEqual(tuple(a.removeprefix('-only-testing:iosAppUITests/')+'()' for a in argv
            if a.startswith('-only-testing:')),control.CASES)
        self.assertEqual(argv.count('test'),1)
        self.assertNotIn('-quiet',argv);self.assertNotIn('test-without-building',argv)
        self.assertFalse(any('testPIN' in a for a in argv))
        self.assertEqual(run_pin03.completed_exit_code(control.RESULT),0)
        self.assertEqual(run_pin03.completed_exit_code('ORIGINAL_TOUCH_COHORT_QUALIFIED'),1)
        with self.assertRaises(ValueError):run_pin03.main(original_touch_cohort=True,original_touch_delivery=True)

    def test_complete_passed_context_preserves_no_capture_no_qualification(self):
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve();state=evidence(out)
            self.assertEqual(control.accept_native_export(out,state),control.RESULT)
            receipt=json.loads((out/'original-touch-cohort.json').read_bytes())
            self.assertEqual(receipt['original_case_count'],23)
            self.assertEqual(receipt['original_touches'],[])
            self.assertFalse(receipt['shipping_qualified']);self.assertFalse(receipt['application_root_cause'])

    def test_complete_original_failed_context_requires_existing_native_companion_gates(self):
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve();state=failed_touch_evidence(out)
            self.assertEqual(control.accept_native_export(out,state),control.RESULT)
            receipt=json.loads((out/'original-touch-cohort.json').read_bytes())
            self.assertIn('Failed',receipt['methods'].values())
            self.assertEqual(len(receipt['original_touches']),1)
            self.assertFalse(receipt['shipping_qualified'])
            for prefix in ('native-touch-','catalog-overscroll-','public-text-','public-lifecycle-'):
                path=next((out/'compose').glob(prefix+'*.json'));raw=path.read_bytes();path.unlink()
                with self.assertRaises((ValueError,OSError)):control.accept_native_export(out,state)
                path.write_bytes(raw)

    def test_original_public_failure_preserved_but_missing_new_companions_reject(self):
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve();state=evidence(out,True);dest=out/'compose'
            methods=control.verify_results(json.loads((dest/'summary.json').read_bytes()),
                json.loads((dest/'tests.json').read_bytes()),state['arms'][0]['simulator'],65)
            self.assertEqual(methods,state['arms'][0]['methods']);self.assertIn('Failed',methods.values())
            with self.assertRaises((ValueError,OSError)):control.accept_native_export(out,state)

    def test_extra_skipped_expected_duplicate_foreign_or_nonfinite_results_reject(self):
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve();state=evidence(out);dest=out/'compose'
            summary=json.loads((dest/'summary.json').read_bytes());tests=json.loads((dest/'tests.json').read_bytes())
            for change in (lambda s,t:s.update(skippedTests=1),lambda s,t:s.update(expectedFailures=1),
                lambda s,t:t['testNodes'].pop(),lambda s,t:t['testNodes'].append(t['testNodes'][0]),
                lambda s,t:t['testNodes'][0].update(durationInSeconds=float('nan')),
                lambda s,t:t['testNodes'][0].update(durationInSeconds=0),
                lambda s,t:t['testNodes'][0].update(durationInSeconds=True),
                lambda s,t:t.update(testPlanConfigurations=[])):
                s,t=copy.deepcopy(summary),copy.deepcopy(tests);change(s,t)
                with self.assertRaises(ValueError):control.verify_results(s,t,state['arms'][0]['simulator'],0)

    def test_replayed_retargeted_extended_or_synthetic_commands_fail_closed(self):
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve();state=evidence(out)
            def command(s):return next(c for c in s['commands'] if c['label']=='compose-build-and-tests')
            for change in (lambda s:command(s).update(timeout_seconds=3601),
                lambda s:command(s)['command'].append('-retry-tests-on-failure'),
                lambda s:command(s)['command'].append('-skip-testing:iosAppUITests/Foreign'),
                lambda s:command(s)['command'].append('-only-testing:iosAppUITests/IOSAppLaunchUITests/testPIN20IsolatedMotionExportControl'),
                lambda s:s['commands'].append(dict(label='compose-tests',exit_code=0)),
                lambda s:s.update(profile=control.combined.PROFILE)):
                value=copy.deepcopy(state);change(value)
                with self.assertRaises(ValueError):control.verify_commands(value,value['arms'][0])

    def test_source_kernel_cleanup_or_original_resource_drift_reject(self):
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve();state=evidence(out)
            for change in (lambda s:s['original_inputs'].update({control.original.text.NAME:'foreign'}),
                lambda s:s['arms'][0].update(cleanup='FAIL'),
                lambda s:s['build_profile'].update(gradle_heap_bytes=3*1024**3),
                lambda s:s['arms'][0]['kernel_resources'].pop('after-tests'),
                lambda s:s['commands'][0].update(direct_child_reaped=False)):
                value=copy.deepcopy(state);change(value)
                with self.assertRaises(ValueError):control.accept_native_export(out,value)


if __name__=='__main__':unittest.main()
