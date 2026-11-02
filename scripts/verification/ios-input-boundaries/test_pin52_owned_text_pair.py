"""Synthetic intake/source guards; new assembled Swift checks on actual Apple SDK."""
import copy
import hashlib
import json
from pathlib import Path
import platform
import shutil
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import pin02_capture
import pin48_property_timing
import pin52_owned_text_pair as pair
import run_pin52
import test_pin51_owned_text as capability


def plan():
    leaf = pair.runtime.pair_control.leaf
    return pair.renderer_for_profile(pair.PROFILE,pair.SOURCE,pair.RUNTIME,pair.DEVICE)(
        {p:leaf.pair.fixture(p) for p in leaf.pair.MODIFIED},leaf.pair.INPUTS,arm='compose')


def evidence(out, failed=()):
    # Fabricated fixtures only. Never rewrite real artifacts or custody.
    state = capability.evidence(out)
    d=out/'compose';row=state['arms'][0]
    methods={k:'Failed' if k in failed else 'Passed' for k in pair.CASES}
    state['profile']=pair.PROFILE
    row.update(methods=methods,test_exit=65 if failed else 0,
        result='JOURNEYS_FAILED' if failed else 'JOURNEYS_PASSED',attachment_test_ids=sorted(failed),
        captures=[],companion_captures=[])
    summary=pair.load(d/'summary.json');tests=pair.load(d/'tests.json')
    summary.update(totalTestCount=2,passedTests=2-len(failed),failedTests=len(failed),
        result='Failed' if failed else 'Passed',testFailures=[dict(testIdentifierString=k,
        failureText='Original public input assertion failed') for k in sorted(failed)])
    tests['testNodes']=[dict(nodeType='Test Case',nodeIdentifier=k,result=v,durationInSeconds=1)
                        for k,v in methods.items()]
    for name,v in (('summary',summary),('tests',tests)):(d/(name+'.json')).write_text(json.dumps(v))
    (d/'inputs.json').write_text(json.dumps(state['original_inputs'] |
        {p:hashlib.sha256(v.encode()).hexdigest() for p,v in plan().items()}))
    cmd=next(c for c in state['commands'] if c['label']=='compose-tests')
    cmd.update(exit_code=row['test_exit'],command=pair.test_command([
        '/usr/bin/xcodebuild','-project','iosApp/iosApp.xcodeproj','-scheme','iosApp','-configuration','Debug',
        '-sdk','iphonesimulator','-destination','platform=iOS Simulator,id='+row['simulator'],
        '-parallel-testing-enabled','NO','-maximum-concurrent-test-simulator-destinations','1'],d/'tests.xcresult'))
    (d/'test.log').write_text(''.join("Test Case '-[iosAppUITests."+k.split('/')[0]+' '+
        k.split('/')[1].removesuffix('()')+"]' started.\n" for k in pair.CASES))
    shutil.rmtree(d/'attachments')
    for prefix in ('capture-','native-'):
        for p in d.glob(prefix+'*.json'):p.unlink()
    for i,method in enumerate(sorted(failed),1):
        token=f'00000000-0000-0000-0000-{i:012d}'
        legacy=dict(schema=1,token=token,simulator=row['simulator'],bundle=state['debug_bundle'],process=42,
            boot='00000000-0000-0000-0000-000000000100',installedMs=0,capturedMs=501,
            snapshot=dict(schema=1,nativeMode=0,overflow=0,invalid=0,rows=[]))
        req=d/'attachments'/('failed-%02d'%i)/'request.json';req.parent.mkdir(parents=True)
        req.write_text(json.dumps(dict(schema=1,token=token,ackObserved=True,originalTransitionFailed=True)))
        raw=json.dumps(legacy).encode();(d/('capture-'+token+'.json')).write_bytes(raw)
        row['captures'].append(pair.run_pin03.decode_capture(raw,{token},row['simulator'],state['debug_bundle']))
        v=capability.fixture();v.update(token=token,simulator=row['simulator'],bundle=state['debug_bundle'],
            process=42,capturedMs=500,runloop=capability.runloop_tests.report());v['delivery']['capturedMs']=499
        raw=json.dumps(v).encode();(d/('native-text-'+token+'.json')).write_bytes(raw)
        row['companion_captures'].append(pair.decode(raw,token=token,simulator=row['simulator'],
            bundle=state['debug_bundle'],legacy=legacy))
    return state


class OriginalOwnedTextControls(unittest.TestCase):
    def test_actual_previously_admitted_capability_required(self):
        pair.require_capability()
        with patch.object(pair,'CAPABILITY_SHA256','a'*64),self.assertRaises(ValueError):pair.require_capability()
        with patch.object(pair.frozen_sources,'_read',return_value=b'{}'),self.assertRaises(ValueError):pair.require_capability()
        self.assertNotIn('PENDING',pair.CAPABILITY_SHA256)

    def test_exact_reverse_driver_and_collector_preserve_original_actions_assertions(self):
        current=plan();old=capability.plan();leaf=pair.runtime.pair_control.leaf
        driver=current[pin02_capture.DRIVER]
        inverse=driver.replace(pair.TEXT_CLOCK,'').replace(pair.RECORD_CLOCK,'')
        inverse=pair.base.pin01_copy.once(inverse,pair.PROFILE,pair.base.PROFILE)
        expected=pair.base.pin01_copy.once(old[pin02_capture.DRIVER],pair.base.SENTINEL,'')
        self.assertEqual(inverse,expected)
        original_driver=pair.original.renderer_for_profile(pair.original.QUERY_PROFILE,pair.SOURCE,pair.RUNTIME,pair.DEVICE)(
            {p:leaf.pair.fixture(p) for p in leaf.pair.MODIFIED},leaf.pair.INPUTS,arm='compose')[pin02_capture.DRIVER]
        inverse=inverse.replace('PIN51Configure','PIN47Configure').replace('PARLOR_PIN51_','PARLOR_PIN47_')
        inverse=pair.base.pin01_copy.once(inverse,pair.base.PROFILE,pair.original.PROFILE)
        self.assertEqual(inverse,original_driver)
        self.assertEqual(current[pin02_capture.CONTAINER].replace(pair.PROFILE,pair.base.PROFILE),old[pin02_capture.CONTAINER])
        for path in current:
            if path not in pin02_capture.MODIFIED:self.assertEqual(current[path],leaf.pair.fixture(path))
        self.assertNotIn('testPIN51OwnedTextExportCapability',driver)
        self.assertEqual(driver.count('name.typeText("Table Test")'),1)
        self.assertEqual(pair.CASES,pair.original.QUERY_CASES)
        # Native collector bytes/ABIs are unchanged from the actual accepted control.
        for name in pair.base.PINS:pair.base.template(name)
        restored=pin48_property_timing.restore(driver)
        self.assertIn('let exists = control.exists',restored)

    def test_all_pass_partial_failure_and_all_fail_are_retained_not_qualification(self):
        for failed in ((),pair.CASES[:1],pair.CASES):
            with self.subTest(failed=failed),tempfile.TemporaryDirectory() as tmp:
                out=Path(tmp).resolve();state=evidence(out,failed)
                self.assertEqual(pair.accept_native_export(out,state),pair.RESULT)
                receipt=pair.load(out/'owned-text-pair.json')
                self.assertEqual(len(receipt['native']),len(failed))
                self.assertEqual(len(receipt['failed_sample_tokens']),len(failed))
                for k in ('application_root_cause','shipping_qualified','physical_verified','native_timing_neutrality'):
                    self.assertIs(receipt[k],False)
                if not failed:self.assertFalse(list((out/'compose').glob('native-text-*.json')))

    def test_missing_duplicate_ack_and_foreign_success_capture_rejected(self):
        for mode in ('missing','duplicate','unacknowledged','wrong-pid','extra-on-success'):
            with self.subTest(mode=mode),tempfile.TemporaryDirectory() as tmp:
                out=Path(tmp).resolve();state=evidence(out,() if mode=='extra-on-success' else pair.CASES[:1]);d=out/'compose'
                if mode=='extra-on-success':(d/'native-text-unowned.json').write_text('{}')
                elif mode=='missing':next(d.glob('native-text-*.json')).unlink()
                elif mode=='wrong-pid':
                    p=next(d.glob('native-text-*.json'));v=pair.load(p);v['process']=43;p.write_text(json.dumps(v))
                else:
                    p=next((d/'attachments').rglob('request.json'))
                    if mode=='duplicate':(p.parent/'duplicate.json').write_bytes(p.read_bytes())
                    else:v=pair.load(p);v['ackObserved']=False;p.write_text(json.dumps(v))
                with self.assertRaises((ValueError,FileNotFoundError)):pair.accept_native_export(out,state)

    def test_no_retries_skips_budget_changes_wrong_source_or_unowned_cleanup(self):
        changes=(lambda s:s.update(source='0'*40),lambda s:s['arms'][0].update(cleanup='FAIL'),
            lambda s:s['arms'][0].update(result='JOURNEYS_PASSED'),lambda s:s['arms'][0].update(attachment_test_ids=[]),
            lambda s:s['commands'].append(copy.deepcopy(s['commands'][0])),
            lambda s:next(c for c in s['commands'] if c['label']=='compose-tests')['command'].append('-retry-tests-on-failure'),
            lambda s:next(c for c in s['commands'] if c['label']=='compose-tests').update(timeout_seconds=901),
            lambda s:s['build_profile'].update(test_timeout_seconds=901))
        for change in changes:
            with tempfile.TemporaryDirectory() as tmp:
                out=Path(tmp).resolve();state=evidence(out,pair.CASES[:1]);change(state)
                with self.assertRaises(ValueError):pair.accept_native_export(out,state)
        with tempfile.TemporaryDirectory() as tmp:
            out=Path(tmp).resolve();state=evidence(out);summary=pair.load(out/'compose/summary.json');summary['skippedTests']=1
            with self.assertRaises(ValueError):pair.verify_results(summary,pair.load(out/'compose/tests.json'),state['arms'][0]['simulator'],0)

    def test_incomplete_safe_installation_retained_without_delivery_claim(self):
        with tempfile.TemporaryDirectory() as tmp:
            out=Path(tmp).resolve();state=evidence(out,pair.CASES[:1]);p=next((out/'compose').glob('native-text-*.json'))
            v=pair.load(p);v['delivery'].update(installed=False,installation=[1,1,0],inheritedResponderUntouched=False,rows=[])
            raw=json.dumps(v).encode();p.write_bytes(raw)
            token=v['token'];legacy=pair.load(out/'compose'/('capture-'+token+'.json'))
            state['arms'][0]['companion_captures']=[pair.decode(raw,token=token,simulator=v['simulator'],bundle=v['bundle'],legacy=legacy)]
            self.assertEqual(pair.accept_native_export(out,state),pair.RESULT)
            self.assertFalse(pair.load(out/'owned-text-pair.json')['native'][0]['complete'])

    def test_external_dispatch_restores_and_never_accepts_old_success_labels(self):
        profiles,select=run_pin52.prior.PROFILES,run_pin52.prior.select_profile
        with patch.object(run_pin52.sys,'argv',['run','--profile',pair.PROFILE]),patch.object(run_pin52.run_pin03,'main',side_effect=RuntimeError('real failure')):
            with self.assertRaises(RuntimeError):run_pin52.main()
        self.assertEqual(run_pin52.prior.PROFILES,profiles);self.assertIs(run_pin52.prior.select_profile,select)
        self.assertEqual(pair.run_pin03.completed_exit_code(pair.RESULT),1)
        for args in ((pair.PROFILE,'0'*40,pair.RUNTIME,pair.DEVICE),(pair.base.PROFILE,pair.SOURCE,pair.RUNTIME,pair.DEVICE)):
            with self.assertRaises(ValueError):pair.renderer_for_profile(*args)

    @unittest.skipUnless(platform.system()=='Darwin','Actual Apple SDK unavailable on Linux')
    def test_actual_apple_new_assembled_wrapper_typecheck(self):
        with tempfile.TemporaryDirectory() as tmp:
            out=Path(tmp).resolve();p=out/'owner.swift';p.write_text(plan()[pin02_capture.CONTAINER])
            sdk=subprocess.check_output(['/usr/bin/xcrun','--sdk','iphonesimulator','--show-sdk-path'],text=True,timeout=45).strip()
            r=subprocess.run(['/usr/bin/xcrun','swiftc','-typecheck','-D','DEBUG','-sdk',sdk,
                '-target','arm64-apple-ios16.0-simulator','-module-cache-path',str(out/'cache'),str(p)],
                capture_output=True,text=True,timeout=180)
            self.assertEqual(r.returncode,0,r.stdout+r.stderr)

    @unittest.skipUnless(platform.system()=='Darwin','Actual Apple parser unavailable on Linux')
    def test_actual_apple_new_original_driver_parse(self):
        with tempfile.TemporaryDirectory() as tmp:
            p=Path(tmp)/'driver.swift';p.write_text(plan()[pin02_capture.DRIVER])
            r=subprocess.run(['/usr/bin/xcrun','swiftc','-frontend','-parse','-D','DEBUG',
                '-target','arm64-apple-ios26.2-simulator',str(p)],capture_output=True,text=True,timeout=120)
            self.assertEqual(r.returncode,0,r.stdout+r.stderr)


if __name__=='__main__':unittest.main()
