"""Synthetic paired/source rejection controls, never native application evidence."""
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
from unittest.mock import patch

import pin43_owned_leaf_pair as control
import test_pin30_public_session_stress as original
import test_pin41_original_input_only as observed


def plan(arm):
    return control.renderer_for_profile(control.PROFILE,control.SOURCE,control.leaf.pair.RUNTIME,control.leaf.pair.DEVICE)(
        {p:control.leaf.pair.fixture(p) for p in control.leaf.pair.MODIFIED},control.leaf.pair.INPUTS,arm=arm)


def evidence(out,failures=()):
    a=original.evidence(out,arm='original')
    with tempfile.TemporaryDirectory() as temporary:
        t=Path(temporary).resolve();b=observed.evidence(t,failures=failures);shutil.copytree(t/'compose',out/'compose')
    a['profile']=control.PROFILE;a['arms']+=b['arms'];a['commands']+=b['commands'];a['build_profile']=b['build_profile']
    # Distinct hypothetical devices. Original arm has no invented native files.
    r=a['arms'][0];r['simulator']='PUBLIC-ORIGINAL-CONTROL-DEVICE';d=out/'original'
    for name in ('summary','tests','cleanup'):
        p=d/(name+'.json');v=json.loads(p.read_bytes())
        if name=='summary':v['devicesAndConfigurations'][0]['device']['deviceId']=r['simulator']
        elif name=='tests':v['devices'][0]['deviceId']=r['simulator']
        else:v['simulator']['udid']=r['simulator']
        p.write_text(json.dumps(v))
    for arm in control.ARMS:
        (out/arm/'inputs.json').write_text(json.dumps(a['original_inputs']|{p:hashlib.sha256(v.encode()).hexdigest() for p,v in plan(arm).items()}))
    for p in (out/'compose').glob('native-touch-*.json'):
        v=json.loads(p.read_bytes());v.update(schema=2,observedMethodKinds=control.leaf.METHOD_KINDS,inheritedStateSetterUntouched=True)
        v['rows']=[r for r in v['rows'] if r[1]!=5];p.write_text(json.dumps(v))
    r=a['arms'][1];d=out/'compose'
    r['companion_captures']=[control.decode_companions(d,v['token'],r['simulator'],a['debug_bundle'],v)
        for v in [control.samples.load(p) for p in sorted(d.glob('capture-*.json'))]]
    return a


class OwnedLeafPairControls(unittest.TestCase):
    def test_original_app_is_exact_and_observed_inverse_keeps_original_queries_actions_and_deadlines(self):
        pair=control.leaf.pair;source={p:pair.fixture(p) for p in pair.MODIFIED}
        v=plan('original')
        self.assertEqual({p for p in v if v[p]!=source[p]},{control.pin02_capture.DRIVER})
        self.assertEqual(control.baseline.restore_driver(v[control.pin02_capture.DRIVER]),source[control.pin02_capture.DRIVER])
        driver=plan('compose')[control.pin02_capture.DRIVER]
        driver=driver.replace('PIN43Configure','PIN39Configure').replace('PARLOR_PIN43_','PARLOR_PIN39_').replace(control.PROFILE,control.leaf.native.PROFILE)
        restored=control.samples.restore_driver(driver)
        self.assertEqual(restored,source[control.pin02_capture.DRIVER]+'\n'+(control.leaf.HERE/'PIN02FailureRequest.swift.in').read_text()+control.leaf.native.CONFIGURE)
        owner=plan('compose')[control.pin02_capture.CONTAINER]
        original_owner=control.leaf.renderer_for_profile(control.leaf.PROFILE,control.SOURCE,pair.RUNTIME,pair.DEVICE)(source,pair.INPUTS,arm='compose')[control.pin02_capture.CONTAINER]
        self.assertEqual(owner.replace('PARLOR_PIN43_','PARLOR_PIN42_').replace(control.PROFILE,control.leaf.PROFILE),original_owner)
        self.assertNotIn('class_addMethod(',owner)
        self.assertNotIn('objc_copyClassList',owner)

    def test_same_fixed_original_cases_and_no_automatic_retries_or_conflicting_entry(self):
        self.assertEqual(control.CASES,control.baseline.CASES)
        self.assertEqual(control.ARMS,('original','compose'))
        self.assertEqual(control.TEST_TIMEOUT_SECONDS,4500)
        self.assertEqual(control.test_command(['xcodebuild'],Path('/result')),control.baseline.test_command(['xcodebuild'],Path('/result')))
        with self.assertRaises(ValueError):control.leaf.run_pin03.main(paired_owned_leaf=True,owned_leaf_export=True)
        self.assertEqual(control.leaf.run_pin03.completed_exit_code(control.RESULT),0)
        self.assertEqual(control.leaf.run_pin03.completed_exit_code('PAIRED_APP_QUALIFIED'),1)
        control.require_capability_binding()
        for receipt in ('PENDING_NATIVE_ACCEPTANCE','0'*64):
            with patch.object(control,'CAPABILITY_RECEIPT_SHA256',receipt):
                with self.assertRaises(ValueError):control.require_capability_binding()

    def test_actual_original_failures_stay_failed_and_zero_call_observed_failures_require_installation(self):
        for failures in ((),(control.CASES[0],),(control.CASES[0],control.CASES[-1])):
            with self.subTest(failures=failures),tempfile.TemporaryDirectory() as temporary:
                out=Path(temporary).resolve();state=evidence(out,failures)
                self.assertEqual(control.accept_native_export(out,state),control.RESULT)
                r=control.samples.load(out/'owned-leaf-pair.json')
                self.assertEqual(r['original']['actual_xctest_exit'],65)
                self.assertEqual(r['original']['methods'][control.CASES[0]],'Failed')
                self.assertEqual(len(r['observed']['native']),len(failures))
                for k in ('application_root_cause','shipping_qualified','native_timing_neutrality','physical_verified'):self.assertIs(r[k],False)

    def test_wrong_arm_order_duplicate_device_incomplete_execution_and_changed_cleanup_reject(self):
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve();state=evidence(out)
            for change in (lambda v:v['arms'].reverse(),lambda v:v['arms'].pop(),lambda v:v['arms'][1].update(simulator=v['arms'][0]['simulator']),
                lambda v:v['arms'][0].update(cleanup='FAIL'),lambda v:v['arms'][1].update(cleanup='FAIL'),lambda v:v['arms'][0].update(test_exit=0),
                lambda v:v['commands'][0].update(direct_child_reaped=False),lambda v:v['commands'].reverse(),lambda v:v['build_profile'].update(test_timeout_seconds=5000)):
                v=copy.deepcopy(state);change(v)
                with self.subTest(change=change),self.assertRaises(ValueError):control.accept_native_export(out,v)

    def test_no_invented_baseline_native_or_absent_observed_getter_evidence(self):
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve();state=evidence(out,(control.CASES[0],))
            for arm,prefix in (('original','capture-'),('original','native-touch-'),('compose','native-accessibility-'),('compose','native-frame-')):
                p=out/arm/(prefix+'invented.json');p.write_text('{}')
                with self.assertRaises(ValueError):control.accept_native_export(out,state)
                p.unlink()
            p=next((out/'compose').glob('native-touch-*.json'));v=control.samples.load(p);v['inheritedStateSetterUntouched']=False;p.write_text(json.dumps(v))
            with self.assertRaises(ValueError):control.accept_native_export(out,state)

    def test_existing_isolated_positive_call_gate_and_full_native_readers_unchanged(self):
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve();state=evidence(out,(control.CASES[0],));d=out/'compose';r=state['arms'][1]
            legacy=control.samples.load(next(d.glob('capture-*.json')))
            # Natural pre-tap zero calls accepted by this caller, never its sentinel capability.
            self.assertTrue(control.decode_companions(d,legacy['token'],r['simulator'],state['debug_bundle'],legacy)['touches']['complete'])
            with self.assertRaises(ValueError):control.leaf.decode_companions(d,legacy['token'],r['simulator'],state['debug_bundle'],legacy)
            with self.assertRaises(ValueError):control.leaf.prior.accept_native_export(out,state)

    @unittest.skipUnless(platform.system()=='Darwin','Generated original and observed drivers require Apple compiler')
    def test_both_drivers_parse_actual_apple_compiler(self):
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve()
            for arm in control.ARMS:
                p=out/(arm+'.swift');p.write_text(plan(arm)[control.pin02_capture.DRIVER])
                r=subprocess.run(['/usr/bin/xcrun','swiftc','-frontend','-parse','-D','DEBUG','-target','arm64-apple-ios18.5-simulator',str(p)],capture_output=True,text=True,timeout=120)
                self.assertEqual(r.returncode,0,r.stdout+r.stderr)

    @unittest.skipUnless(platform.system()=='Darwin','Paired wrapper requires actual UIKit SDK')
    def test_observed_scope_wrapper_typechecks_actual_apple_sdk(self):
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve();env=dict(os.environ,TMPDIR=str(out));p=out/'wrapper.swift';p.write_text(plan('compose')[control.pin02_capture.CONTAINER])
            sdk=subprocess.check_output(['/usr/bin/xcrun','--sdk','iphonesimulator','--show-sdk-path'],text=True,timeout=45,env=env).strip()
            r=subprocess.run(['/usr/bin/xcrun','swiftc','-typecheck','-D','DEBUG','-sdk',sdk,'-target','arm64-apple-ios18.5-simulator',
                '-module-cache-path',str(out/'cache'),str(p)],capture_output=True,text=True,timeout=180,env=env)
            self.assertEqual(r.returncode,0,r.stdout+r.stderr)


if __name__=='__main__':unittest.main()
