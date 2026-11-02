"""New copied source/custody controls; fixtures do not certify real application runs."""
import copy
import hashlib
import json
from pathlib import Path
import platform
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import pin52_owned_text_pair as text
import pin54_original_root_pair as pair
import run_pin54
import test_pin52_owned_text_pair as previous


def plan():
    leaf=pair.runtime.pair_control.leaf
    return pair.renderer_for_profile(pair.PROFILE,pair.SOURCE,pair.RUNTIME,pair.DEVICE)(
        {p:leaf.pair.fixture(p) for p in leaf.pair.MODIFIED},leaf.pair.INPUTS,arm='compose')


def evidence(out,failed=()):
    state=previous.evidence(out,failed);state['profile']=pair.PROFILE
    d=out/'compose';row=state['arms'][0]
    (d/'inputs.json').write_text(json.dumps(state['original_inputs']|
        {p:hashlib.sha256(s.encode()).hexdigest() for p,s in plan().items()}))
    reports=[]
    for p in sorted(d.glob('native-text-*.json')):
        v=pair.load(p);v['schema']=53
        # A safe incomplete natural observation is retained, never accepted as
        # complete capability or causal evidence. Actual capability is separate.
        v['root']=dict(schema=53,installation=[1,0,0],attemptedRoot=False,overflow=0,
            overlayAlive=True,rootAlive=False,rows=[],capturedMs=498)
        raw=json.dumps(v).encode();p.write_bytes(raw)
        reports.append(pair.decode(raw,token=v['token'],simulator=row['simulator'],bundle=v['bundle'],
            legacy=pair.load(d/('capture-'+v['token']+'.json'))))
    row['companion_captures']=reports
    return state


class OriginalRootPairControls(unittest.TestCase):
    def test_exact_actual_root_capability_required_before_runtime(self):
        pair.require_capability()
        with patch.object(pair,'CAPABILITY_SHA256','a'*64),self.assertRaises(ValueError):pair.require_capability()
        with patch.object(pair,'CAPABILITY_ARTIFACT','a'*64),self.assertRaises(ValueError):pair.require_capability()
        with patch.object(pair.frozen_sources,'_read',return_value=b'{}'),self.assertRaises(ValueError):pair.require_capability()
        self.assertNotIn('PENDING',pair.CAPABILITY_SHA256)

    def test_exact_original_driver_and_root_owner_with_failure_only_clocks(self):
        current=plan();old=previous.plan();leaf=pair.runtime.pair_control.leaf
        driver=current[pair.pin02_capture.DRIVER]
        inverse=driver.replace('PIN53Configure','PIN51Configure').replace('PARLOR_PIN53_','PARLOR_PIN51_')
        inverse=pair.base.pin01_copy.once(inverse,pair.PROFILE,text.PROFILE)
        self.assertEqual(inverse,old[pair.pin02_capture.DRIVER])
        owned=pair.root.renderer_for_profile(pair.root.PROFILE,pair.SOURCE,pair.RUNTIME,pair.DEVICE)(
            {p:leaf.pair.fixture(p) for p in leaf.pair.MODIFIED},leaf.pair.INPUTS,arm='compose')
        self.assertEqual(current[pair.pin02_capture.CONTAINER].replace(pair.PROFILE,pair.root.PROFILE),owned[pair.pin02_capture.CONTAINER])
        self.assertIn(pair.root.root_source(),current[pair.pin02_capture.CONTAINER])
        self.assertIn(pair.base.template('PIN51OwnedTextDelivery.swift.in'),current[pair.pin02_capture.CONTAINER])
        for p in current:
            if p not in pair.pin02_capture.MODIFIED:self.assertEqual(current[p],leaf.pair.fixture(p))
        self.assertEqual(pair.CASES,text.CASES)
        self.assertNotIn('testPIN53RootLifecycleExportCapability',driver)
        self.assertEqual(driver.count('name.typeText("Table Test")'),1)
        self.assertIn('        if result != .completed {\n            print("PIN47 failed-query clock origin ms:',driver)

    def test_pass_partial_failure_and_all_fail_are_retained_not_qualification(self):
        for failed in ((),pair.CASES[:1],pair.CASES):
            with self.subTest(failed=failed),tempfile.TemporaryDirectory() as tmp:
                out=Path(tmp).resolve();state=evidence(out,failed)
                self.assertEqual(pair.accept_native_export(out,state),pair.RESULT)
                receipt=pair.load(out/'root-lifecycle-pair.json')
                self.assertEqual(len(receipt['native']),len(failed))
                self.assertEqual(receipt['capability_run'],41497962566)
                for k in ('application_root_cause','shipping_qualified','physical_verified','native_timing_neutrality','root_queries_added'):
                    self.assertIs(receipt[k],False)
                if not failed:self.assertFalse(list((out/'compose').glob('native-text-*.json')))
                else:self.assertFalse(receipt['native'][0]['root_complete'])

    def test_missing_duplicate_unacknowledged_wrong_pid_or_foreign_success_capture_reject(self):
        for mode in ('missing','duplicate','unacknowledged','wrong-pid','foreign-success'):
            with self.subTest(mode=mode),tempfile.TemporaryDirectory() as tmp:
                out=Path(tmp).resolve();state=evidence(out,() if mode=='foreign-success' else pair.CASES[:1]);d=out/'compose'
                if mode=='foreign-success':(d/'native-text-unowned.json').write_text('{}')
                elif mode=='missing':next(d.glob('native-text-*.json')).unlink()
                elif mode=='wrong-pid':
                    p=next(d.glob('native-text-*.json'));v=pair.load(p);v['process']=43;p.write_text(json.dumps(v))
                else:
                    p=next((d/'attachments').rglob('request.json'))
                    if mode=='duplicate':(p.parent/'duplicate.json').write_bytes(p.read_bytes())
                    else:v=pair.load(p);v['ackObserved']=False;p.write_text(json.dumps(v))
                with self.assertRaises((ValueError,FileNotFoundError)):pair.accept_native_export(out,state)

    def test_original_source_commands_budgets_assertions_and_cleanup_remain_required(self):
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

    def test_private_root_metadata_clock_nonfinite_or_wrong_schema_reject(self):
        changes=(lambda v:v['root'].update(label='private'),lambda v:v['root'].update(schema=True),
            lambda v:v['root'].update(capturedMs=501),lambda v:v['root'].update(rows=[[float('nan'),3,0,0]]),
            lambda v:v.update(schema=51))
        for change in changes:
            with tempfile.TemporaryDirectory() as tmp:
                out=Path(tmp).resolve();state=evidence(out,pair.CASES[:1]);p=next((out/'compose').glob('native-text-*.json'))
                v=pair.load(p);change(v);p.write_text(json.dumps(v))
                with self.assertRaises(ValueError):pair.accept_native_export(out,state)

    def test_external_profile_restores_and_completed_diagnostics_never_shipping_success(self):
        profiles,select=run_pin54.prior.PROFILES,run_pin54.prior.select_profile
        with patch.object(run_pin54.sys,'argv',['run','--profile',pair.PROFILE]),patch.object(run_pin54.run_pin03,'main',side_effect=RuntimeError('actual failure')):
            with self.assertRaises(RuntimeError):run_pin54.main()
        self.assertEqual(run_pin54.prior.PROFILES,profiles);self.assertIs(run_pin54.prior.select_profile,select)
        self.assertEqual(run_pin54.run_pin03.completed_exit_code(pair.RESULT),1)
        for args in ((pair.PROFILE,'0'*40,pair.RUNTIME,pair.DEVICE),(pair.root.PROFILE,pair.SOURCE,pair.RUNTIME,pair.DEVICE)):
            with self.assertRaises(ValueError):pair.renderer_for_profile(*args)

    @unittest.skipUnless(platform.system()=='Darwin','Actual Apple Simulator SDK unavailable on Linux')
    def test_actual_apple_new_assembled_owner_typecheck(self):
        with tempfile.TemporaryDirectory() as tmp:
            out=Path(tmp).resolve();sdk=subprocess.check_output(['/usr/bin/xcrun','--sdk','iphonesimulator','--show-sdk-path'],text=True,timeout=45).strip()
            p=out/'owner.swift';p.write_text(plan()[pair.pin02_capture.CONTAINER])
            r=subprocess.run(['/usr/bin/xcrun','swiftc','-typecheck','-D','DEBUG','-sdk',sdk,'-target','arm64-apple-ios16.0-simulator',
                '-module-cache-path',str(out/'cache'),str(p)],capture_output=True,text=True,timeout=180)
            self.assertEqual(r.returncode,0,r.stdout+r.stderr)

    @unittest.skipUnless(platform.system()=='Darwin','Actual Apple Swift parser unavailable on Linux')
    def test_actual_apple_new_original_driver_parse(self):
        with tempfile.TemporaryDirectory() as tmp:
            out=Path(tmp).resolve();p=out/'driver.swift';p.write_text(plan()[pair.pin02_capture.DRIVER])
            r=subprocess.run(['/usr/bin/xcrun','swiftc','-frontend','-parse','-D','DEBUG','-target','arm64-apple-ios26.2-simulator',str(p)],capture_output=True,text=True,timeout=120)
            self.assertEqual(r.returncode,0,r.stdout+r.stderr)


if __name__=='__main__':unittest.main()
