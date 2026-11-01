"""Synthetic controls never constitute application/root-cause acceptance."""
import copy
import hashlib
import json
import os
from pathlib import Path
import platform
import subprocess
import tempfile
import unittest

import pin42_owned_leaf_export as control
import test_pin39_native_only_export as prior


def plan():
    return control.renderer_for_profile(control.PROFILE,control.SOURCE,control.pair.RUNTIME,control.pair.DEVICE)(
        {p:control.pair.fixture(p) for p in control.pair.MODIFIED},control.pair.INPUTS,arm='compose')


def evidence(out):
    state=prior.evidence(out);d=out/'compose';row=state['arms'][0];state['profile']=control.PROFILE
    row.update(methods={control.CASE:'Failed'},attachment_test_ids=[control.CASE])
    for name in ('summary','tests'):
        p=d/(name+'.json');p.write_text(p.read_text().replace(control.native.CASE,control.CASE))
    (d/'inputs.json').write_text(json.dumps(state['original_inputs']|{p:hashlib.sha256(v.encode()).hexdigest() for p,v in plan().items()}))
    (d/'test.log').write_text("Test Case '-[iosAppUITests.IOSAppLaunchUITests testPIN42OwnedLeafExportCapability]' started.\n")
    for c in state['commands']:
        if c['label']=='compose-tests':c['command']=control.test_command(['xcodebuild'],d/'tests.xcresult')
    for prefix in ('native-accessibility-','native-frame-','native-root-'):
        for p in d.glob(prefix+'*.json'):p.unlink()
    for p in d.glob('native-touch-*.json'):
        v=json.loads(p.read_bytes());v.update(schema=2,observedMethodKinds=control.METHOD_KINDS,inheritedStateSetterUntouched=True)
        v['rows']=[r for r in v['rows'] if r[1]!=5];p.write_text(json.dumps(v))
    for p in d.glob('native-only-*.json'):
        v=json.loads(p.read_bytes());v.pop('attributesInstalled');v.pop('rootInstalled')
        v.update(accessibilityGetterHooks=False,frameGetterHooks=False,rootGetterHooks=False,classListEnumeration=False)
        p.write_text(json.dumps(v))
    legacy=json.loads(next(d.glob('capture-*.json')).read_bytes())
    row['companion_captures']=[control.decode_companions(d,legacy['token'],row['simulator'],state['debug_bundle'],legacy)]
    return state


class OwnedLeafControls(unittest.TestCase):
    def test_original_app_helpers_assertions_and_deadlines_are_exact(self):
        v=plan();original={p:control.pair.fixture(p) for p in control.pair.MODIFIED}
        self.assertEqual({p for p in v if v[p]!=original[p]},set(control.pin02_capture.MODIFIED))
        driver=v[control.pin02_capture.DRIVER]
        restored=driver.replace(control.SENTINEL+'\n','').removesuffix('\n'+(control.HERE/'PIN02FailureRequest.swift.in').read_text()+control.CONFIGURE)
        self.assertEqual(restored,original[control.pin02_capture.DRIVER])
        self.assertNotIn('typeText',control.SENTINEL)
        self.assertLess(control.SENTINEL.index('try tapPublicSetupTransition'),control.SENTINEL.index('captureAfterFailedTransition'))
        self.assertNotIn('retry',driver.lower().replace('retry the real local-network operation',''))

    def test_seven_owned_methods_and_inherited_setter_never_added_or_replaced(self):
        s=(control.HERE/control.TOUCH).read_text()
        for absent in ('class_addMethod(','typealias State =','stateBlock','"setState:",','kind: 5',
            'addGestureRecognizer(','removeGestureRecognizer(','location(in:','accessibilityValue','event.timestamp','Timer('):
            self.assertNotIn(absent,s)
        self.assertIn('class_copyMethodList(target',s)
        self.assertIn('method_getName(owned[$0]) == stateSelector',s)
        self.assertIn('afterCount == count',s)
        self.assertIn('Self.same(class_getInstanceMethod(target, stateSelector)!, parentState)',s)
        self.assertEqual(s.count('original(object, selector, input, event)'),1)
        self.assertEqual(s.count('original(object, selector, other)'),1)
        container=plan()[control.pin02_capture.CONTAINER]
        for absent in ('objc_copyClassList','PIN08Accessibility','PIN10Root','PIN22Native','MainViewControllerKt.','layoutIfNeeded('):
            self.assertNotIn(absent,container)
        self.assertIn('pin42Input?.bindOriginalInputOnce(view: contentController.view!)',container)

    def test_complete_failed_export_preserves_actual_cause_limits(self):
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve();state=evidence(out)
            self.assertEqual(control.accept_native_export(out,state),control.RESULT)
            r=json.loads((out/'owned-leaf-export.json').read_bytes())
            self.assertTrue(r['native']['touches']['inherited_state_setter_untouched'])
            self.assertEqual(r['native']['touches']['observed_method_kinds'],control.METHOD_KINDS)
            for k in ('application_root_cause','shipping_qualified','native_timing_neutrality','physical_verified'):self.assertIs(r[k],False)

    def test_private_foreign_overflow_missing_setter_claim_and_invented_rows_reject(self):
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve();state=evidence(out);p=next((out/'compose').glob('native-touch-*.json'));raw=p.read_bytes()
            for change in (lambda v:v.update(schema=1),lambda v:v.update(schema=True),lambda v:v.update(observedMethodKinds=[1,2,3,4,5,6,7,8]),
                lambda v:v.update(inheritedStateSetterUntouched=False),lambda v:v.update(inheritedStateSetterUntouched=1),
                lambda v:v.update(overflow=1),lambda v:v.update(installed=False),lambda v:v.update(process=123456),
                lambda v:v.update(text='forbidden'),lambda v:v['rows'][0].__setitem__(1,5),lambda v:v['rows'].pop(),
                lambda v:v.update(rows=[]),lambda v:v.update(installation=[5,0,0])):
                v=json.loads(raw);change(v);p.write_text(json.dumps(v))
                with self.subTest(change=change),self.assertRaises(ValueError):control.accept_native_export(out,state)
            p.write_bytes(raw)
            for prefix in ('capture-','native-touch-','native-only-'):
                f=next((out/'compose').glob(prefix+'*.json'));saved=f.read_bytes();f.unlink()
                with self.assertRaises((ValueError,OSError)):control.accept_native_export(out,state)
                f.write_bytes(saved)

    def test_eight_method_readers_remain_strict_and_reject_narrower_metadata(self):
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve();state=evidence(out);p=next((out/'compose').glob('native-touch-*.json'))
            legacy=json.loads(next((out/'compose').glob('capture-*.json')).read_bytes());row=state['arms'][0]
            with self.assertRaises(ValueError):control.original_decoder.decode(p.read_bytes(),token=legacy['token'],simulator=row['simulator'],bundle=state['debug_bundle'],legacy=legacy)
            with self.assertRaises(ValueError):control.native.accept_native_export(out,state)
            with self.assertRaises(ValueError):control.prior.accept_native_export(out,state)

    def test_commands_cleanup_product_actual_assertion_and_exclusive_entry_remain_required(self):
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve();state=evidence(out)
            for change in (lambda v:v.update(source='foreign'),lambda v:v['arms'][0].update(cleanup='FAIL'),
                lambda v:v['arms'][0].update(test_exit=0),lambda v:v['arms'][0].update(product_manifest_sha256='foreign'),
                lambda v:v['commands'][0].update(direct_child_reaped=False)):
                v=copy.deepcopy(state);change(v)
                with self.assertRaises(ValueError):control.accept_native_export(out,v)
        with self.assertRaises(ValueError):control.run_pin03.main(owned_leaf_export=True,original_input_only=True)
        self.assertEqual(control.run_pin03.completed_exit_code(control.RESULT),0)
        self.assertEqual(control.run_pin03.completed_exit_code('OWNED_LEAF_APP_QUALIFIED'),1)
        for code in (0,1,True):
            with self.assertRaises(ValueError):control.verify_results({}, {},'owned',code)

    @unittest.skipUnless(platform.system()=='Darwin','Actual owned-leaf ABI requires Apple Objective-C runtime')
    def test_actual_argument_return_parent_setter_scope_and_overflow_forwarding(self):
        source=(control.HERE/control.TOUCH).read_text()+'\n'+(control.HERE/'PIN42OwnedLeafABIControl.swift.in').read_text()
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve();p=out/'main.swift';p.write_text(source)
            r=subprocess.run(['/usr/bin/xcrun','swiftc','-D','PIN42_CONTROL',str(p),'-o',str(out/'control')],capture_output=True,text=True,timeout=180)
            self.assertEqual(r.returncode,0,r.stdout+r.stderr)
            r=subprocess.run([str(out/'control')],capture_output=True,text=True,timeout=30)
            self.assertEqual(r.returncode,0,r.stdout+r.stderr)
            self.assertIn('PIN42_OWNED_LEAF_ARGUMENT_RETURN_INHERITED_SETTER_PARENT_SCOPE_CONTROLS_PASS',r.stdout)

    @unittest.skipUnless(platform.system()=='Darwin','Actual generated wrapper requires UIKit SDK')
    def test_entire_wrapper_actual_apple_typecheck(self):
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve();env=dict(os.environ,TMPDIR=str(out));p=out/'wrapper.swift';p.write_text(plan()[control.pin02_capture.CONTAINER])
            sdk=subprocess.check_output(['/usr/bin/xcrun','--sdk','iphonesimulator','--show-sdk-path'],text=True,timeout=45,env=env).strip()
            r=subprocess.run(['/usr/bin/xcrun','swiftc','-typecheck','-D','DEBUG','-sdk',sdk,'-target','arm64-apple-ios18.5-simulator',
                '-module-cache-path',str(out/'cache'),str(p)],capture_output=True,text=True,timeout=180,env=env)
            self.assertEqual(r.returncode,0,r.stdout+r.stderr)

    @unittest.skipUnless(platform.system()=='Darwin','Actual generated driver requires Apple compiler')
    def test_entire_driver_actual_apple_parse(self):
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve();p=out/'driver.swift';p.write_text(plan()[control.pin02_capture.DRIVER])
            r=subprocess.run(['/usr/bin/xcrun','swiftc','-frontend','-parse','-D','DEBUG','-target','arm64-apple-ios18.5-simulator',str(p)],capture_output=True,text=True,timeout=120)
            self.assertEqual(r.returncode,0,r.stdout+r.stderr)


if __name__=='__main__':unittest.main()
