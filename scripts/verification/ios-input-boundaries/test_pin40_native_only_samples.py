"""Fabricated source/intake controls are not native execution evidence."""
import copy
import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import tempfile
import unittest

import pin40_native_only_samples as control
import test_pin39_native_only_export as prior


def plan():
    return control.renderer_for_profile(control.PROFILE,control.SOURCE,control.native.pair.RUNTIME,control.native.pair.DEVICE)(
        {p:control.native.pair.fixture(p) for p in control.native.pair.MODIFIED},control.native.pair.INPUTS,arm='compose')


def evidence(out, *, failures=()):
    state = prior.evidence(out); d = out/'compose'; row = state['arms'][0]
    state['profile'] = control.PROFILE
    state['build_profile'] = dict(test_timeout_seconds=control.TEST_TIMEOUT_SECONDS)
    methods = {c:'Failed' if c in failures else 'Passed' for c in control.CASES}
    require_failures = sorted(failures)
    row.update(methods=methods,attachment_test_ids=require_failures,test_exit=65 if failures else 0,
        result='JOURNEYS_FAILED' if failures else 'JOURNEYS_PASSED',captures=[],companion_captures=[],
        root_companion_captures=[],motion_companion_captures=[])
    tests = control.load(d/'tests.json'); node = tests['testNodes'][0]
    tests['testNodes'] = [node | dict(nodeIdentifier=c,result=v) for c,v in methods.items()]
    (d/'tests.json').write_text(json.dumps(tests))
    summary = control.load(d/'summary.json'); summary.update(totalTestCount=10,passedTests=10-len(failures),
        failedTests=len(failures),result='Failed' if failures else 'Passed',testFailures=[
            dict(testIdentifierString=c,failureText='Original assertion fails before its first tap') for c in require_failures])
    (d/'summary.json').write_text(json.dumps(summary))
    (d/'inputs.json').write_text(json.dumps(state['original_inputs'] | {
        p:hashlib.sha256(v.encode()).hexdigest() for p,v in plan().items()}))
    (d/'test.log').write_text(''.join("Test Case '-[iosAppUITests."+c.removesuffix('()').replace('/',' ')+"]' started.\n"
        for c in control.CASES))
    for c in state['commands']:
        if c['label'] == 'compose-tests':
            c.update(exit_code=row['test_exit'],timeout_seconds=control.TEST_TIMEOUT_SECONDS,
                command=control.test_command(['xcodebuild'],d/'tests.xcresult'))
    prefixes = ('capture-','native-only-','native-touch-','native-frame-','native-root-','native-accessibility-')
    original = {prefix:control.load(next(d.glob(prefix+'*.json'))) for prefix in prefixes}
    for p in d.glob('*.json'):
        if p.name not in ('cleanup.json','product.json','inputs.json','summary.json','tests.json'): p.unlink()
    shutil.rmtree(d/'attachments'); (d/'attachments').mkdir()
    for index, method in enumerate(require_failures,1):
        token = '00000000-0000-4000-8000-%012d'%(index+1)
        for prefix, value in original.items():
            v = copy.deepcopy(value); v['token'] = token
            if prefix == 'native-touch-': v['rows'] = []  # Complete installation, genuine pre-tap boundary.
            (d/(prefix+token+'.json')).write_text(json.dumps(v))
        attachment = d/'attachments'/('failed-%02d'%index); attachment.mkdir()
        (attachment/'request.json').write_text(json.dumps(dict(schema=1,token=token,ackObserved=True,originalTransitionFailed=True)))
        raw = (d/('capture-'+token+'.json')).read_bytes(); legacy = json.loads(raw)
        row['captures'].append(control.run_pin03.decode_capture(raw,{token},row['simulator'],state['debug_bundle']))
        row['companion_captures'].append(control.decode_companions(d,token,row['simulator'],state['debug_bundle'],legacy))
    return state


class NativeOnlySampleControls(unittest.TestCase):
    def test_verified_wrapper_bytes_and_every_original_action_assertion_restored_exactly(self):
        v = plan(); previous = prior.plan()
        self.assertEqual(v[control.pin02_capture.CONTAINER],previous[control.pin02_capture.CONTAINER])
        self.assertEqual(control.restore_driver(v[control.pin02_capture.DRIVER]),
            previous[control.pin02_capture.DRIVER].replace(control.native.SENTINEL+'\n',''))
        for p in v:
            if p != control.pin02_capture.DRIVER: self.assertEqual(v[p],previous[p])
        self.assertNotIn('testPIN39NativeOnlyExportCapability',v[control.pin02_capture.DRIVER])
        self.assertIn(control.failure.HELPER,v[control.pin02_capture.DRIVER])
        self.assertEqual(v[control.pin02_capture.DRIVER].count('            PIN39Configure(app)\n'),1)

    def test_exact_single_arm_finite_order_without_sentinel_retries_or_deadline_extension(self):
        self.assertEqual(control.CASES,control.samples.CASES)
        self.assertEqual(control.TEST_TIMEOUT_SECONDS,4500)
        argv = control.test_command(['xcodebuild','-quiet'],Path('/owned/result'))
        self.assertEqual([a for a in argv if a.startswith('-only-testing:')],['-only-testing:'+t for t in control.TESTS])
        for forbidden in ('-quiet','-retry-tests-on-failure','-test-iterations','-skip-testing'): self.assertNotIn(forbidden,argv)
        self.assertEqual(control.run_pin03.completed_exit_code(control.RESULT),0)
        self.assertEqual(control.run_pin03.completed_exit_code('NATIVE_ONLY_APPLICATION_QUALIFIED'),1)
        with self.assertRaises(ValueError): control.run_pin03.main(native_only_samples=True,native_only_export=True)
        args = [control.PROFILE,control.SOURCE,control.native.pair.RUNTIME,control.native.pair.DEVICE]
        for i in range(4):
            wrong=args.copy();wrong[i]+='-wrong'
            with self.assertRaises(ValueError):control.renderer_for_profile(*wrong)

    def test_all_pass_requires_zero_exports_and_no_root_cause_claim(self):
        with tempfile.TemporaryDirectory() as temporary:
            out = Path(temporary).resolve(); state = evidence(out)
            self.assertEqual(control.accept_native_export(out,state),control.RESULT)
            receipt = control.load(out/'native-only-samples.json')
            self.assertEqual(receipt['native'],[])
            self.assertEqual(receipt['failed_sample_tokens'],{})
            for key in ('application_root_cause','shipping_qualified','native_timing_neutrality','physical_verified'):
                self.assertIs(receipt[key],False)
            (out/'compose/native-only-unrequested.json').write_text('{}')
            with self.assertRaises(ValueError):control.accept_native_export(out,state)

    def test_all_actual_failures_retained_once_even_before_first_tap(self):
        for failures in ((control.CASES[0],),(control.CASES[0],control.CASES[-1])):
            with self.subTest(failures=failures),tempfile.TemporaryDirectory() as temporary:
                out=Path(temporary).resolve();state=evidence(out,failures=failures)
                self.assertEqual(control.accept_native_export(out,state),control.RESULT)
                receipt=control.load(out/'native-only-samples.json')
                self.assertEqual(len(receipt['native']),len(failures))
                self.assertTrue(all(r['touches']['complete'] and r['touches']['rows']==0 for r in receipt['native']))
                self.assertIsNone(receipt['actual_completed_flows'])
                # The old isolated sentinel STILL rejects zero calls.
                legacy=control.load(next((out/'compose').glob('capture-*.json')))
                with self.assertRaises(ValueError):control.native.decode_companions(out/'compose',legacy['token'],
                    state['arms'][0]['simulator'],state['debug_bundle'],legacy)

    def test_missing_installation_overflow_or_partial_companions_reject(self):
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve();state=evidence(out,failures=(control.CASES[0],));d=out/'compose'
            for prefix in ('capture-','native-only-','native-touch-','native-frame-','native-root-','native-accessibility-'):
                p=next(d.glob(prefix+'*.json'));raw=p.read_bytes();p.unlink()
                with self.assertRaises((OSError,ValueError)):control.accept_native_export(out,state)
                p.write_bytes(raw)
            p=next(d.glob('native-touch-*.json'));raw=p.read_bytes()
            for change in (lambda v:v.update(installed=False),lambda v:v.update(overflow=1),lambda v:v.update(installation=[1,0,0])):
                v=json.loads(raw);change(v);p.write_text(json.dumps(v))
                with self.assertRaises(ValueError):control.accept_native_export(out,state)
            p.write_bytes(raw)

    def test_unacknowledged_duplicate_missing_or_foreign_failure_requests_reject(self):
        for change in ('unacknowledged','duplicate','missing','foreign'):
            with self.subTest(change=change),tempfile.TemporaryDirectory() as temporary:
                out=Path(temporary).resolve();state=evidence(out,failures=(control.CASES[0],));p=next((out/'compose/attachments').rglob('request.json'))
                if change=='missing':p.unlink()
                elif change=='duplicate':(p.parent/'duplicate.json').write_bytes(p.read_bytes())
                else:
                    v=json.loads(p.read_bytes());v.update(ackObserved=False) if change=='unacknowledged' else v.update(token='foreign')
                    p.write_text(json.dumps(v))
                with self.assertRaises(ValueError):control.accept_native_export(out,state)

    def test_counts_skips_device_durations_and_order_not_weakened(self):
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve();state=evidence(out);d=out/'compose';s,t=control.load(d/'summary.json'),control.load(d/'tests.json')
            for change in (lambda s,t:s.update(skippedTests=1),lambda s,t:s.update(expectedFailures=1),
                lambda s,t:t['testNodes'][0].update(durationInSeconds=float('nan')),
                lambda s,t:t['devices'][0].update(osVersion='26.2'),lambda s,t:t['testNodes'].pop()):
                a,b=copy.deepcopy(s),copy.deepcopy(t);change(a,b)
                with self.assertRaises(ValueError):control.verify_results(a,b,state['arms'][0]['simulator'],0)
            p=d/'test.log';raw=p.read_text();p.write_text(raw+raw)
            with self.assertRaises(ValueError):control.accept_native_export(out,state)

    def test_source_product_cleanup_child_and_command_custody_not_waived(self):
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve();state=evidence(out)
            for change in (lambda s:s.update(source='foreign'),lambda s:s['arms'][0].update(cleanup='FAIL'),
                lambda s:s['commands'][0].update(direct_child_reaped=False),lambda s:s['commands'][0].update(status='TIMED_OUT'),
                lambda s:s['commands'].append(s['commands'][0]),lambda s:s['build_profile'].update(test_timeout_seconds=5000),
                lambda s:next(c for c in s['commands'] if c['label']=='compose-tests')['command'].append('-retry-tests-on-failure')):
                v=copy.deepcopy(state);change(v)
                with self.assertRaises(ValueError):control.accept_native_export(out,v)
            for name in ('inputs.json','product.json','cleanup.json'):
                p=out/'compose'/name;raw=p.read_bytes();p.write_text('{}')
                with self.assertRaises((ValueError,KeyError)):control.accept_native_export(out,state)
                p.write_bytes(raw)

    @unittest.skipUnless(platform.system()=='Darwin','Actual copied driver requires Apple SDK')
    def test_actual_generated_sample_driver_parses_with_apple_compiler(self):
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve();env=dict(os.environ,TMPDIR=str(out));p=out/'driver.swift'
            p.write_text(plan()[control.pin02_capture.DRIVER])
            result=subprocess.run(['/usr/bin/xcrun','swiftc','-frontend','-parse','-D','DEBUG',
                '-target','arm64-apple-ios18.5-simulator',str(p)],capture_output=True,text=True,timeout=120,env=env)
            self.assertEqual(result.returncode,0,result.stdout+result.stderr)


if __name__=='__main__':unittest.main()
