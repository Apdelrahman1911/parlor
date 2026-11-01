"""Fabricated controls are not actual native input or application evidence."""
import copy
import hashlib
import json
import os
from pathlib import Path
import platform
import subprocess
import tempfile
import unittest

import pin41_original_input_only as control
import test_pin40_native_only_samples as prior


def plan():
    return control.renderer_for_profile(control.PROFILE,control.SOURCE,control.native.pair.RUNTIME,control.native.pair.DEVICE)(
        {p:control.native.pair.fixture(p) for p in control.native.pair.MODIFIED},control.native.pair.INPUTS,arm='compose')


def evidence(out,failures=()):
    state=prior.evidence(out,failures=failures);d=out/'compose';row=state['arms'][0];state['profile']=control.PROFILE
    (d/'inputs.json').write_text(json.dumps(state['original_inputs'] | {
        p:hashlib.sha256(v.encode()).hexdigest() for p,v in plan().items()}))
    for prefix in ('native-accessibility-','native-frame-','native-root-'):
        for p in d.glob(prefix+'*.json'):p.unlink()
    for p in d.glob('native-only-*.json'):
        v=control.samples.load(p);v.pop('attributesInstalled');v.pop('rootInstalled')
        v.update(accessibilityGetterHooks=False,frameGetterHooks=False,rootGetterHooks=False,classListEnumeration=False)
        p.write_text(json.dumps(v))
    row['companion_captures']=[control.decode_companions(d,v['token'],row['simulator'],state['debug_bundle'],v)
        for v in [control.samples.load(p) for p in sorted(d.glob('capture-*.json'))]]
    return state


class OriginalInputOnlyControls(unittest.TestCase):
    def test_exact_original_driver_inverse_touch_template_and_no_getter_or_kotlin_interception(self):
        v=plan();previous=prior.plan();driver=v[control.pin02_capture.DRIVER]
        driver=driver.replace('PIN41Configure','PIN39Configure').replace('PARLOR_PIN41_','PARLOR_PIN39_').replace(control.PROFILE,'isolated-native-only-export')
        self.assertEqual(driver,previous[control.pin02_capture.DRIVER])
        container=v[control.pin02_capture.CONTAINER]
        self.assertIn((control.HERE/control.native.touch.TEMPLATE).read_text(),container)
        for absent in ('PIN08AccessibilityObserver','PIN10RootObserver','PIN22NativeFrame','objc_copyClassList',
            'MainViewControllerKt.','addGestureRecognizer(','layoutIfNeeded(','Timer('):self.assertNotIn(absent,container)
        self.assertIn('pin41Input?.bindOriginalInputOnce(view: contentController.view!)',container)
        for p in v:
            if p not in control.pin02_capture.MODIFIED:self.assertEqual(v[p],control.native.pair.fixture(p))

    def test_original_fixed_order_actions_assertions_and_process_deadline_unchanged(self):
        self.assertEqual(control.CASES,control.samples.CASES)
        self.assertEqual(control.TEST_TIMEOUT_SECONDS,4500)
        self.assertEqual(control.test_command(['xcodebuild'],Path('/result')),
            control.samples.test_command(['xcodebuild'],Path('/result')))
        self.assertEqual(control.samples.run_pin03.completed_exit_code(control.RESULT),0)
        self.assertEqual(control.samples.run_pin03.completed_exit_code('INPUT_ONLY_APP_QUALIFIED'),1)
        with self.assertRaises(ValueError):control.samples.run_pin03.main(original_input_only=True,native_only_samples=True)
        args=[control.PROFILE,control.SOURCE,control.native.pair.RUNTIME,control.native.pair.DEVICE]
        for i in range(4):
            wrong=args.copy();wrong[i]+='-wrong'
            with self.assertRaises(ValueError):control.renderer_for_profile(*wrong)

    def test_all_pass_zero_exports_or_actual_failures_retained_without_causal_claim(self):
        for failures in ((),(control.CASES[0],),(control.CASES[0],control.CASES[-1])):
            with self.subTest(failures=failures),tempfile.TemporaryDirectory() as temporary:
                out=Path(temporary).resolve();state=evidence(out,failures)
                self.assertEqual(control.accept_native_export(out,state),control.RESULT)
                r=control.samples.load(out/'original-input-only-samples.json')
                self.assertEqual(len(r['native']),len(failures))
                for key in ('application_root_cause','shipping_qualified','native_timing_neutrality','physical_verified'):
                    self.assertIs(r[key],False)
                for report in r['native']:
                    self.assertTrue(report['touches']['complete']);self.assertEqual(report['touches']['rows'],0)

    def test_unknown_or_forged_hooks_installation_private_or_foreign_status_reject(self):
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve();state=evidence(out,(control.CASES[0],));p=next((out/'compose').glob('native-only-*.json'));raw=p.read_bytes()
            for change in (lambda v:v.update(accessibilityGetterHooks=True),lambda v:v.update(frameGetterHooks=True),
                lambda v:v.update(rootGetterHooks=True),lambda v:v.update(classListEnumeration=True),lambda v:v.update(touchInstalled=False),
                lambda v:v.update(inputBindingAttempted=False),lambda v:v.update(eligibleInputViews=0),lambda v:v.update(addedRecognizer=True),
                lambda v:v.update(kotlinInstrumentation=True),lambda v:v.update(process=999),lambda v:v.update(private='forbidden')):
                v=json.loads(raw);change(v);p.write_text(json.dumps(v))
                with self.assertRaises(ValueError):control.accept_native_export(out,state)
            p.write_bytes(raw)
            (out/'compose/native-accessibility-invented.json').write_text('{}')
            with self.assertRaises(ValueError):control.accept_native_export(out,state)

    def test_missing_or_overflowed_original_input_or_ack_cannot_be_waived(self):
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve();state=evidence(out,(control.CASES[0],));d=out/'compose'
            for prefix in ('capture-','native-only-','native-touch-'):
                p=next(d.glob(prefix+'*.json'));raw=p.read_bytes();p.unlink()
                with self.assertRaises((OSError,ValueError)):control.accept_native_export(out,state)
                p.write_bytes(raw)
            p=next(d.glob('native-touch-*.json'));v=control.samples.load(p);v['overflow']=1;p.write_text(json.dumps(v))
            with self.assertRaises(ValueError):control.accept_native_export(out,state)

    def test_original_full_native_consumers_reject_input_only_substitution(self):
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve();state=evidence(out,(control.CASES[0],))
            with self.assertRaises(ValueError):control.samples.accept_native_export(out,state)
            state['profile']=control.samples.PROFILE
            with self.assertRaises((OSError,ValueError)):control.samples.accept_native_export(out,state)

    def test_every_source_product_method_cleanup_and_command_requirement_remains(self):
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve();state=evidence(out)
            for change in (lambda s:s.update(source='foreign'),lambda s:s['arms'][0].update(cleanup='FAIL'),
                lambda s:s['commands'][0].update(direct_child_reaped=False),lambda s:s['commands'][0].update(status='TIMED_OUT'),
                lambda s:s['arms'][0].update(methods={}),lambda s:s['build_profile'].update(test_timeout_seconds=5000),
                lambda s:s['arms'][0].update(product_manifest_sha256='foreign')):
                v=copy.deepcopy(state);change(v)
                with self.assertRaises(ValueError):control.accept_native_export(out,v)

    @unittest.skipUnless(platform.system()=='Darwin','Actual input-only wrapper requires UIKit SDK')
    def test_entire_wrapper_typechecks_against_actual_apple_sdk(self):
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve();env=dict(os.environ,TMPDIR=str(out))
            sdk=subprocess.check_output(['/usr/bin/xcrun','--sdk','iphonesimulator','--show-sdk-path'],text=True,timeout=45,env=env).strip()
            p=out/'input-only.swift';p.write_text(plan()[control.pin02_capture.CONTAINER])
            r=subprocess.run(['/usr/bin/xcrun','swiftc','-typecheck','-D','DEBUG','-sdk',sdk,
                '-target','arm64-apple-ios18.5-simulator','-module-cache-path',str(out/'cache'),str(p)],
                capture_output=True,text=True,timeout=180,env=env)
            self.assertEqual(r.returncode,0,r.stdout+r.stderr)

    @unittest.skipUnless(platform.system()=='Darwin','Actual input-only driver requires Apple SDK')
    def test_entire_original_input_only_driver_parses_with_apple_compiler(self):
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve();env=dict(os.environ,TMPDIR=str(out));p=out/'driver.swift'
            p.write_text(plan()[control.pin02_capture.DRIVER])
            r=subprocess.run(['/usr/bin/xcrun','swiftc','-frontend','-parse','-D','DEBUG',
                '-target','arm64-apple-ios18.5-simulator',str(p)],capture_output=True,text=True,timeout=120,env=env)
            self.assertEqual(r.returncode,0,r.stdout+r.stderr)


if __name__=='__main__':unittest.main()
