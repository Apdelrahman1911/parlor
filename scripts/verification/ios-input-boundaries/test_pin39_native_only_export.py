"""Fabricated intake/source controls are not native app execution evidence."""
import copy
import hashlib
import json
import os
from pathlib import Path
import platform
import subprocess
import tempfile
import unittest

import pin39_native_only_export as control
import test_pin34_touch_export_control as prior


def plan():
    return control.renderer_for_profile(control.PROFILE,control.SOURCE,control.pair.RUNTIME,control.pair.DEVICE)(
        {p:control.pair.fixture(p) for p in control.pair.MODIFIED},control.pair.INPUTS,arm='compose')


def evidence(out):
    state = prior.evidence(out); d = out/'compose'; row = state['arms'][0]
    state['profile'] = control.PROFILE
    row.update(methods={control.CASE:'Failed'},attachment_test_ids=[control.CASE])
    def edit(path, mutate):
        v = json.loads(path.read_bytes()); mutate(v); path.write_text(json.dumps(v)); return v
    edit(d/'summary.json', lambda v:v['testFailures'][0].update(testIdentifierString=control.CASE))
    edit(d/'tests.json', lambda v:v['testNodes'][0].update(nodeIdentifier=control.CASE))
    (d/'inputs.json').write_text(json.dumps(state['original_inputs'] | {
        p:hashlib.sha256(v.encode()).hexdigest() for p,v in plan().items()}))
    (d/'test.log').write_text("Test Case '-[iosAppUITests.IOSAppLaunchUITests testPIN39NativeOnlyExportCapability]' started.\n")
    for c in state['commands']:
        if c['label'] == 'compose-tests': c['command'] = control.test_command(['xcodebuild'],d/'tests.xcresult')
    path = next(d.glob('capture-*.json'))
    legacy = edit(path, lambda v:v.update(snapshot=dict(schema=1,nativeMode=0,overflow=0,invalid=0,rows=[])))
    token = legacy['token']
    row['captures'] = [control.run_pin03.decode_capture(path.read_bytes(),{token},row['simulator'],state['debug_bundle'])]
    status = dict(schema=1,token=token,simulator=row['simulator'],bundle=state['debug_bundle'],
        process=legacy['process'],capturedMs=legacy['capturedMs'],viewCount=5,eligibleInputViews=1,treeComplete=True,
        attributesInstalled=True,rootInstalled=True,touchInstalled=True,kotlinInstrumentation=False,
        composePointerObservers=False,addedRecognizer=False,primaryEnvelopePurpose='durable-failure-ack-only')
    (d/('native-only-'+token+'.json')).write_text(json.dumps(status))
    row['companion_captures'] = [control.decode_companions(d,token,row['simulator'],state['debug_bundle'],legacy)]
    return state


class NativeOnlyControls(unittest.TestCase):
    def test_exact_plan_preserves_all_kotlin_and_swiftui_source_bytes(self):
        v = plan(); original = {p:control.pair.fixture(p) for p in control.pair.MODIFIED}
        self.assertEqual(len(v),7)
        self.assertEqual({p for p in v if v[p] != original[p]},set(control.pin02_capture.MODIFIED))
        for p in original:
            if p not in control.pin02_capture.MODIFIED: self.assertEqual(v[p],original[p])
        container = v[control.pin02_capture.CONTAINER]
        self.assertNotIn('MainViewControllerKt.',container)
        self.assertNotIn('addGestureRecognizer(',container)
        self.assertNotIn('PIN01Public',container)
        self.assertIn('PIN39NativeOnlyObservation(view: contentView)',container)
        self.assertIn('contentController.didMove(toParent: self)',container)

    def test_original_helper_byte_identical_and_sentinel_capture_after_assertion(self):
        v = plan()[control.pin02_capture.DRIVER]
        original = control.pair.fixture(control.pin02_capture.DRIVER)
        self.assertEqual(v.replace(control.SENTINEL+'\n','').split('\n#if DEBUG')[0].rstrip(),original.rstrip())
        self.assertIn('continueAfterFailure = true',control.SENTINEL)
        self.assertLess(control.SENTINEL.index('try tapPublicSetupTransition'),
            control.SENTINEL.index('PIN02FailureRequest.captureAfterFailedTransition'))
        self.assertIn(control.ABSENT,control.SENTINEL)
        self.assertNotIn('permission)',control.SENTINEL)
        self.assertNotIn('typeText',control.SENTINEL)

    def test_exact_profile_source_device_arm_no_original_retry(self):
        args = [control.PROFILE,control.SOURCE,control.pair.RUNTIME,control.pair.DEVICE]
        for n in range(4):
            wrong = args.copy(); wrong[n] += '-wrong'
            with self.assertRaises(ValueError): control.renderer_for_profile(*wrong)
        renderer = control.renderer_for_profile(*args)
        for arm in ('original','native'):
            with self.assertRaises(ValueError): renderer({}, {}, arm=arm)
        with self.assertRaises(ValueError): control.run_pin03.main(native_only_export=True,public_scroll_samples=True)
        self.assertEqual(control.run_pin03.completed_exit_code(control.RESULT),0)
        self.assertEqual(control.run_pin03.completed_exit_code('NATIVE_ONLY_APPLICATION_QUALIFIED'),1)

    def test_complete_ack_only_fixture_never_invents_kotlin_evidence(self):
        with tempfile.TemporaryDirectory() as temporary:
            out = Path(temporary).resolve(); state = evidence(out)
            self.assertEqual(control.accept_native_export(out,state),control.RESULT)
            r = json.loads((out/'native-only-export.json').read_bytes())
            for key in ('application_root_cause','shipping_qualified','native_timing_neutrality','physical_verified'):
                self.assertIs(r[key],False)
            self.assertFalse(state['arms'][0]['captures'][0]['numeric_buffer_complete'])

    def test_legacy_numeric_acceptance_still_rejects_native_ack_only_substitution(self):
        with tempfile.TemporaryDirectory() as temporary:
            out = Path(temporary).resolve(); state = evidence(out)
            state['profile'] = control.current.PROFILE
            with self.assertRaises(ValueError):
                control.current.accept_observations(out,state,profile=control.current.PROFILE,arms=control.ARMS,
                    renderer=control.renderer_for_profile(control.PROFILE,control.SOURCE,control.pair.RUNTIME,control.pair.DEVICE),
                    result_verifier=control.verify_results,command_verifier=control.verify_commands)

    def test_missing_companions_or_actual_installation_rejects(self):
        with tempfile.TemporaryDirectory() as temporary:
            out = Path(temporary).resolve(); state = evidence(out); d = out/'compose'
            for prefix in ('capture-','native-accessibility-','native-frame-','native-root-','native-touch-','native-only-'):
                p = next(d.glob(prefix+'*.json')); raw = p.read_bytes(); p.unlink()
                with self.assertRaises((ValueError,OSError)): control.accept_native_export(out,state)
                p.write_bytes(raw)
            p = next(d.glob('native-only-*.json')); raw = p.read_bytes()
            for change in (lambda v:v.update(treeComplete=False),lambda v:v.update(eligibleInputViews=2),
                lambda v:v.update(viewCount=257),lambda v:v.update(attributesInstalled=False),
                lambda v:v.update(rootInstalled=False),lambda v:v.update(touchInstalled=False),
                lambda v:v.update(kotlinInstrumentation=True),lambda v:v.update(addedRecognizer=True),
                lambda v:v.update(process=999),lambda v:v.update(capturedMs=float('nan')),
                lambda v:v.update(label='private')):
                v = json.loads(raw); change(v); p.write_text(json.dumps(v))
                with self.assertRaises(ValueError): control.accept_native_export(out,state)
            p.write_bytes(raw)

    def test_failed_only_once_ack_no_duplicate_private_or_forged_numeric_capture(self):
        for change in ('noack','duplicate','numeric','private','foreign'):
            with tempfile.TemporaryDirectory() as temporary:
                out = Path(temporary).resolve(); state = evidence(out); d = out/'compose'
                p = next((d/'attachments').rglob('*request*.json'))
                if change == 'duplicate': (p.parent/'duplicate.json').write_bytes(p.read_bytes())
                elif change == 'noack':
                    v = json.loads(p.read_bytes()); v['ackObserved'] = False; p.write_text(json.dumps(v))
                else:
                    p = next(d.glob('capture-*.json')); v = json.loads(p.read_bytes())
                    if change == 'numeric': v['snapshot']['rows'] = [[1,4,0,0,0,0,0,0,0,0]]
                    elif change == 'private': v['snapshot']['label'] = 'never accepted'
                    else: v['process'] += 1
                    p.write_text(json.dumps(v))
                with self.assertRaises(ValueError): control.accept_native_export(out,state)

    def test_actual_assertion_exit_duration_device_required(self):
        with tempfile.TemporaryDirectory() as temporary:
            out = Path(temporary).resolve(); state = evidence(out); d = out/'compose'
            s,t = json.loads((d/'summary.json').read_bytes()),json.loads((d/'tests.json').read_bytes())
            for code in (0,1,64,66,True):
                with self.assertRaises(ValueError): control.verify_results(s,t,state['arms'][0]['simulator'],code)
            for change in (lambda s,t:s.update(skippedTests=1),lambda s,t:s.update(expectedFailures=1),
                lambda s,t:s['testFailures'][0].update(failureText='Earlier startup failure'),
                lambda s,t:t['testNodes'][0].update(durationInSeconds=float('nan')),
                lambda s,t:t['devices'][0].update(osVersion='26.2')):
                a,b = copy.deepcopy(s),copy.deepcopy(t); change(a,b)
                with self.assertRaises(ValueError): control.verify_results(a,b,state['arms'][0]['simulator'],65)

    def test_source_product_cleanup_child_and_command_custody_cannot_be_waived(self):
        with tempfile.TemporaryDirectory() as temporary:
            out = Path(temporary).resolve(); state = evidence(out)
            for change in (lambda v:v.update(source='foreign'),lambda v:v['arms'][0].update(cleanup='FAIL'),
                lambda v:v['commands'][0].update(direct_child_reaped=False),lambda v:v['commands'][0].update(status='TIMED_OUT'),
                lambda v:v['commands'].append(v['commands'][1]),
                lambda v:next(c for c in v['commands'] if c['label']=='compose-tests')['command'].append('-retry-tests-on-failure')):
                v = copy.deepcopy(state); change(v)
                with self.assertRaises(ValueError): control.accept_native_export(out,v)
            for name in ('inputs.json','product.json','cleanup.json'):
                p = out/'compose'/name; raw = p.read_bytes(); p.write_text('{}')
                with self.assertRaises((ValueError,KeyError)): control.accept_native_export(out,state)
                p.write_bytes(raw)

    def test_original_methods_not_executed_or_replayed(self):
        with tempfile.TemporaryDirectory() as temporary:
            out = Path(temporary).resolve(); state = evidence(out); p = out/'compose/test.log'
            raw = p.read_text(); p.write_text(raw+raw)
            with self.assertRaises(ValueError): control.accept_native_export(out,state)

    @unittest.skipUnless(platform.system()=='Darwin','Actual native-only wrapper requires UIKit SDK')
    def test_entire_native_only_wrapper_typechecks_against_actual_apple_sdk(self):
        with tempfile.TemporaryDirectory() as temporary:
            out = Path(temporary).resolve(); env = dict(os.environ,TMPDIR=str(out))
            sdk = subprocess.check_output(['/usr/bin/xcrun','--sdk','iphonesimulator','--show-sdk-path'],text=True,timeout=45,env=env).strip()
            p = out/'native-only.swift'; p.write_text(plan()[control.pin02_capture.CONTAINER])
            result = subprocess.run(['/usr/bin/xcrun','swiftc','-typecheck','-D','DEBUG','-sdk',sdk,
                '-target','arm64-apple-ios18.5-simulator','-module-cache-path',str(out/'cache'),str(p)],
                capture_output=True,text=True,timeout=180,env=env)
            self.assertEqual(result.returncode,0,result.stdout+result.stderr)

    @unittest.skipUnless(platform.system()=='Darwin','Actual new XCTest driver requires Apple SDK')
    def test_actual_copied_driver_parses_against_apple_swift_compiler(self):
        with tempfile.TemporaryDirectory() as temporary:
            out = Path(temporary).resolve(); env = dict(os.environ,TMPDIR=str(out))
            p = out/'driver.swift'; p.write_text(plan()[control.pin02_capture.DRIVER])
            result = subprocess.run(['/usr/bin/xcrun','swiftc','-frontend','-parse','-D','DEBUG',
                '-target','arm64-apple-ios18.5-simulator',str(p)],capture_output=True,text=True,timeout=120,env=env)
            self.assertEqual(result.returncode,0,result.stdout+result.stderr)


if __name__ == '__main__': unittest.main()
