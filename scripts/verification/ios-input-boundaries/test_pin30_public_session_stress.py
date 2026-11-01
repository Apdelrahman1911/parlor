"""Synthetic preparation/rejection controls, not native stress evidence."""
import copy
import hashlib
import json
from pathlib import Path
import platform
import shutil
import subprocess
import tempfile
import unittest

import pin02_capture
import pin30_public_session_stress as stress
import run_pin03
import test_pin29_equivalent_query_boundary as previous


def plan(arm):
    pair = stress.observed.original.motion.pair
    return stress.renderer_for_profile(stress.PROFILE,stress.SOURCE,pair.RUNTIME,pair.DEVICE)(
        {p:pair.fixture(p) for p in pair.MODIFIED},pair.INPUTS,arm=arm)


def evidence(out,arm='compose'):
    state = previous.evidence(out); state['profile'] = stress.PROFILE; dest = out/'compose'; row = state['arms'][0]
    methods = {case:'Failed' if index == 0 else 'Passed' for index,case in enumerate(stress.CASES)}
    row.update(methods=methods,attachment_test_ids=[stress.CASES[0]])
    summary = json.loads((dest/'summary.json').read_bytes()); summary.update(totalTestCount=10,passedTests=9,failedTests=1)
    summary['testFailures'][0]['testIdentifierString'] = stress.CASES[0]
    tests = json.loads((dest/'tests.json').read_bytes())
    tests['testPlanConfigurations'] = [dict(configurationId='1',configurationName='Test Scheme Action')]
    tests['testNodes'] = [dict(nodeType='Test Case',nodeIdentifier=case,result=result,durationInSeconds=1)
                          for case,result in methods.items()]
    (dest/'summary.json').write_text(json.dumps(summary)); (dest/'tests.json').write_text(json.dumps(tests))
    for c in state['commands']:
        if c['label'] == 'compose-build':c['timeout_seconds'] = 2700
        if c['label'] == 'compose-gradle-stop':c['timeout_seconds'] = 120
    command = next(c for c in state['commands'] if c['label'] == 'compose-tests')
    command.update(command=stress.test_command(['xcodebuild'],dest/'tests.xcresult'),timeout_seconds=stress.TEST_TIMEOUT_SECONDS)
    (dest/'test.log').write_text(''.join("Test Case '-[iosAppUITests."+case.split('/')[0]+' '+case.split('/')[1].removesuffix('()')+"]' started.\n" for case in stress.CASES))
    if arm == 'original':
        dest.rename(out/'original'); dest = out/'original'; row['arm'] = arm
        for c in state['commands']:
            if c['label'].startswith('compose-'):c['label'] = 'original-'+c['label'][len('compose-'):]
        for key in ('captures','companion_captures','root_companion_captures','motion_companion_captures'):row[key] = []
        for prefix in ('capture-','native-accessibility-','native-root-','native-frame-','catalog-motion-',
                       'public-text-','native-input-','input-attachment-','public-lifecycle-'):
            for path in dest.glob(prefix+'*.json'):path.unlink()
        shutil.rmtree(dest/'attachments'); (dest/'attachments/failed-01').mkdir(parents=True)
        (dest/'attachments/failed-01/original.json').write_text('{"fixture":true}')
    (dest/'inputs.json').write_text(json.dumps(state['original_inputs']|{p:hashlib.sha256(b.encode()).hexdigest() for p,b in plan(arm).items()}))
    return state


class PublicSessionStressControls(unittest.TestCase):
    def test_exact_inverse_preserves_original_and_observed_sources_actions_and_deadlines(self):
        pair = stress.observed.original.motion.pair
        originals = {p:pair.fixture(p) for p in pair.MODIFIED}
        for arm in stress.ARMS:
            before = pair.render(originals,pair.INPUTS,arm='original') if arm == 'original' else previous.plan()
            after = plan(arm)
            self.assertEqual(set(before),set(after))
            for path in before:
                self.assertEqual(before[path],stress.restore_driver(after[path]) if path == pin02_capture.DRIVER else after[path])
            for token in ('XCTAssert','XCTFail(','.tap()', '.typeText(', '.swipeUp(', '.swipeDown(', 'timeout: '):
                self.assertEqual(before[pin02_capture.DRIVER].count(token),after[pin02_capture.DRIVER].count(token),token)
        self.assertEqual(stress.wrappers().count('func testPIN30Sample'),10)
        self.assertNotIn('for ',stress.wrappers()); self.assertNotIn('catch',stress.wrappers())
        self.assertEqual(stress.wrappers().count('try testAdditionalGamesExposeOnlyMultiplayerSetupInArabic()'),5)
        self.assertEqual(stress.wrappers().count('try testAdditionalGamesExposeOnlyMultiplayerSetupInEnglish()'),5)
        with self.assertRaises(ValueError):stress.extend_driver(plan('original')[pin02_capture.DRIVER])

    def test_only_fixed_explicit_profile_arm_and_ten_case_selection(self):
        pair = stress.observed.original.motion.pair
        with self.assertRaises(ValueError):stress.renderer_for_profile(stress.PROFILE,'a'*40,pair.RUNTIME,pair.DEVICE)
        with self.assertRaises(ValueError):stress.renderer_for_profile(stress.PROFILE,stress.SOURCE,'foreign',pair.DEVICE)
        with self.assertRaises(ValueError):stress.renderer_for_profile(stress.PROFILE,stress.SOURCE,pair.RUNTIME,pair.DEVICE)({}, {},arm='native')
        with self.assertRaises(ValueError):run_pin03.main(public_session_stress=True,equivalent_query=True)
        self.assertEqual(run_pin03.completed_exit_code(stress.RESULT),0)
        self.assertEqual(run_pin03.completed_exit_code('APP_QUALIFIED'),1)
        argv = stress.test_command(['xcodebuild','-quiet'],Path('/result'))
        self.assertEqual([a for a in argv if a.startswith('-only-testing:')],['-only-testing:'+t for t in stress.TESTS])
        for token in ('-quiet','-retry-tests-on-failure','-test-iterations','-skip-testing'):self.assertNotIn(token,argv)

    def test_actual_failures_stay_failed_and_all_samples_required(self):
        with tempfile.TemporaryDirectory() as temporary:
            out = Path(temporary).resolve(); state = evidence(out)
            row = state['arms'][0]; summary = json.loads((out/'compose/summary.json').read_bytes()); tests = json.loads((out/'compose/tests.json').read_bytes())
            self.assertEqual(stress.verify_results(summary,tests,row['simulator'],65),row['methods'])
            for change in (lambda s,t:s.update(skippedTests=1),lambda s,t:s.update(expectedFailures=1),
                           lambda s,t:s.update(totalTestCount=9),lambda s,t:t['testNodes'].pop(),
                           lambda s,t:t['testNodes'].append(t['testNodes'][0]),
                           lambda s,t:t['testNodes'][0].update(durationInSeconds=0),
                           lambda s,t:t['testNodes'][0].update(durationInSeconds=float('nan')),
                           lambda s,t:t['devices'][0].update(deviceId='foreign')):
                s,t = copy.deepcopy(summary),copy.deepcopy(tests); change(s,t)
                with self.subTest(change=change),self.assertRaises(ValueError):stress.verify_results(s,t,row['simulator'],65)
            with self.assertRaises(ValueError):stress.verify_results(summary,tests,row['simulator'],0)
            exports = stress.failed_method_exports(Path('/xcresult'),Path('/attachments'),row['methods'])
            self.assertEqual(len(exports),1); self.assertEqual(exports[0][1][-1],stress.CASES[0])

    def test_every_observed_gate_and_baseline_custody_remain_mandatory(self):
        for arm in stress.ARMS:
            with tempfile.TemporaryDirectory() as temporary:
                out = Path(temporary).resolve(); state = evidence(out,arm)
                self.assertEqual(stress.accept_native_export(out,state),stress.RESULT)
                for change in (lambda v:v.update(source='foreign'), lambda v:v['arms'][0].update(cleanup='FAIL'),
                    lambda v:next(c for c in v['commands'] if c['label']==arm+'-tests').update(timeout_seconds=4501),
                    lambda v:next(c for c in v['commands'] if c['label']==arm+'-tests')['command'].append('-retry-tests-on-failure'),
                    lambda v:v['arms'][0].update(methods={}),lambda v:v['commands'][0].update(direct_child_reaped=False)):
                    v = copy.deepcopy(state); change(v)
                    with self.subTest(arm=arm,change=change),self.assertRaises(ValueError):stress.accept_native_export(out,v)
                if arm == 'compose':
                    next((out/arm).glob('public-lifecycle-*.json')).unlink()
                else:
                    shutil.rmtree(out/arm/'attachments/failed-01')
                with self.subTest(arm=arm),self.assertRaises((ValueError,OSError)):stress.accept_native_export(out,state)

    def test_start_order_source_and_query_ownership_cannot_be_replaced(self):
        with tempfile.TemporaryDirectory() as temporary:
            out = Path(temporary).resolve(); state = evidence(out)
            log = out/'compose/test.log'; raw = log.read_bytes(); log.write_bytes(raw+raw)
            with self.assertRaises(ValueError):stress.accept_native_export(out,state)
            log.write_bytes(raw)
            query = out/'compose/attachments/failed-01/query.json'; value = json.loads(query.read_bytes()); value['token'] = 'foreign'; query.write_text(json.dumps(value))
            with self.assertRaises(ValueError):stress.accept_native_export(out,state)

    def test_baseline_source_has_no_app_observer_and_no_invented_capture(self):
        pair = stress.observed.original.motion.pair; p = plan('original')
        self.assertEqual(set(p),set(pair.MODIFIED))
        for path in p:
            restored = stress.restore_driver(p[path]) if path == pin02_capture.DRIVER else p[path]
            self.assertEqual(restored,pair.fixture(path))
        with tempfile.TemporaryDirectory() as temporary:
            out = Path(temporary).resolve(); state = evidence(out,'original')
            (out/'original/capture-fake.json').write_text('{}')
            with self.assertRaises(ValueError):stress.accept_native_export(out,state)

    def test_original_entry_gates_still_reject_stress_command(self):
        with tempfile.TemporaryDirectory() as temporary:
            out = Path(temporary).resolve(); state = evidence(out)
            with self.assertRaises(ValueError):stress.observed.original.verify_original_companions(out,state)
            with self.assertRaises(ValueError):stress.observed.accept_native_export(out,state)

    def test_all_passing_samples_require_no_failure_exports_and_are_not_shipping_qualification(self):
        for arm in stress.ARMS:
            with tempfile.TemporaryDirectory() as temporary:
                out = Path(temporary).resolve(); state = evidence(out,arm); dest = out/arm; row = state['arms'][0]
                row.update(methods=dict.fromkeys(stress.CASES,'Passed'),test_exit=0,result='JOURNEYS_PASSED',attachment_test_ids=[])
                for key in ('captures','companion_captures','root_companion_captures','motion_companion_captures'):row[key] = []
                next(c for c in state['commands'] if c['label'] == arm+'-tests')['exit_code'] = 0
                summary = json.loads((dest/'summary.json').read_bytes()); summary.update(result='Passed',passedTests=10,failedTests=0,testFailures=[])
                tests = json.loads((dest/'tests.json').read_bytes())
                for node in tests['testNodes']:node['result'] = 'Passed'
                (dest/'summary.json').write_text(json.dumps(summary)); (dest/'tests.json').write_text(json.dumps(tests))
                for prefix in ('capture-','native-','catalog-motion-','public-text-','input-attachment-','public-lifecycle-'):
                    for path in dest.glob(prefix+'*.json'):path.unlink()
                shutil.rmtree(dest/'attachments')
                self.assertEqual(stress.accept_native_export(out,state),stress.RESULT)
                receipt = json.loads((out/'stress-observations.json').read_bytes())
                self.assertFalse(receipt['shipping_qualified']);self.assertFalse(receipt['physical_verified'])
                self.assertEqual(receipt['actual_completed_flows'],60)

    def test_both_generated_drivers_typecheck_against_actual_apple_sdk(self):
        if platform.system() != 'Darwin':self.skipTest('Actual generated XCTest drivers require Apple SDK')
        sdk = subprocess.check_output(['/usr/bin/xcrun','--sdk','iphonesimulator','--show-sdk-path'],text=True,timeout=45).strip()
        platform_path = subprocess.check_output(['/usr/bin/xcrun','--sdk','iphonesimulator','--show-sdk-platform-path'],text=True,timeout=45).strip()
        for arm in stress.ARMS:
            with tempfile.TemporaryDirectory() as temporary:
                out = Path(temporary).resolve(); source = out/'driver.swift'; source.write_text(plan(arm)[pin02_capture.DRIVER])
                result = subprocess.run(['/usr/bin/xcrun','swiftc','-typecheck','-D','DEBUG','-sdk',sdk,
                    '-target','arm64-apple-ios18.5-simulator','-F',platform_path+'/Developer/Library/Frameworks',
                    '-I',platform_path+'/Developer/usr/lib','-module-cache-path',str(out/'cache'),str(source)],
                    capture_output=True,text=True,timeout=180)
                self.assertEqual(result.returncode,0,result.stdout+result.stderr)


if __name__ == '__main__':unittest.main()
