"""Portable exact-plan/intake controls and actual Apple leaf-attachment ABI control."""
import copy
import hashlib
import json
from pathlib import Path
import platform
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import pin01_copy
import pin02_capture
import pin27_attached_input_delivery as attached
import run_pin03
import test_pin26_delivery_export_control as previous
import test_pin25_native_input_delivery as native


def plan():
    return attached.renderer_for_profile(attached.PROFILE,attached.SOURCE,attached.delivery.text.motion.pair.RUNTIME,
        attached.delivery.text.motion.pair.DEVICE)({p:attached.delivery.text.motion.pair.fixture(p)
        for p in attached.delivery.text.motion.pair.MODIFIED},attached.delivery.text.motion.pair.INPUTS,arm='compose')


def fixture():
    p=native.fixture()
    return dict(schema=1,token=p['token'],simulator=p['simulator'],bundle=p['bundle'],process=100,
        capturedMs=19,installed=True,installation=[1,1,1],overflow=0,rows=[[0,0,1],[0.5,1,0]])


def intake(value):
    p=fixture()
    return attached.validate_attachment(json.dumps(value).encode(),token=p['token'],simulator=p['simulator'],
        bundle=p['bundle'],legacy=dict(process=100,capturedMs=20))


def evidence(out):
    state=previous.evidence(out);state['profile']=attached.PROFILE;dest=out/'compose'
    (dest/'inputs.json').write_text(json.dumps(state['original_inputs']|
        {p:hashlib.sha256(v.encode()).hexdigest() for p,v in plan().items()}))
    legacy=json.loads(next(dest.glob('capture-*.json')).read_bytes())
    value=fixture();value.update(token=legacy['token'],simulator=state['arms'][0]['simulator'],
        process=legacy['process'],capturedMs=legacy['capturedMs'])
    (dest/('input-attachment-'+legacy['token']+'.json')).write_text(json.dumps(value))
    return state


class AttachedNativeInputControls(unittest.TestCase):
    def test_original_actions_assertions_callbacks_and_native_methods_stay_exact(self):
        before=native.plan();after=plan()
        self.assertEqual(set(after),set(before)|{attached.KOTLIN})
        for p in before:
            if p not in (pin01_copy.MAIN,pin02_capture.CONTAINER):self.assertEqual(before[p],after[p])
        self.assertEqual(after[pin01_copy.MAIN].removesuffix(attached.BRIDGE).replace(
            'com.parlor.app.inputcopy.PIN27InputContainerBinding.Observe { App() }','App()'),before[pin01_copy.MAIN])
        self.assertEqual(after[pin02_capture.CONTAINER].removesuffix('\n'+attached.template('PIN27InputAttachment.swift.in')+attached.ADAPTER)
            .replace(attached.PROPERTY,'').replace(attached.INSTALL,attached.delivery.INSTALL).replace(attached.EXPORT,''),
            before[pin02_capture.CONTAINER])
        self.assertNotIn('_ = delivery.install()\n',attached.INSTALL)
        self.assertEqual(attached.CASES,(attached.delivery.text.CONTROL,))
        self.assertEqual(attached.test_command(['xcodebuild'],Path('/owned/result')),
            attached.isolated.test_command(['xcodebuild'],Path('/owned/result')))
        self.assertEqual(run_pin03.completed_exit_code(attached.RESULT),0)
        self.assertEqual(run_pin03.completed_exit_code('INPUT_REPAIR_QUALIFIED'),1)
        with self.assertRaises(ValueError):run_pin03.main(attached_input_delivery=True,native_input_export_control=True)

    def test_no_global_view_hook_queries_private_input_or_repaired_gestures(self):
        swift=attached.template('PIN27InputAttachment.swift.in');kotlin=attached.template('PIN27InputContainer.kt.in')
        for forbidden in ('accessibilityValue','accessibilityLabel','textInRange','UIAccessibilityPostNotification',
            'Timer(','.tap()', '.subviews', '.text', 'setFirstResponder'):
            self.assertNotIn(forbidden,swift)
        self.assertEqual(swift.count('original(object, selector, child)'),1)
        self.assertNotIn('method_setImplementation',swift)
        self.assertIn('class_addMethod(target, selector, implementation, encoding)',swift)
        self.assertIn('String(cString: class_getName(parent)) == "CMPScrollView"',swift)
        self.assertIn('String(cString: class_getName(parent)) == "CMPEditMenuView"',swift)
        self.assertIn('guard Thread.isMainThread, armed(), !deliveryAttempted',swift)
        self.assertIn('deliveryAttempted = true',swift)
        self.assertIn('O_EXCL | O_NOFOLLOW',swift)
        self.assertIn('val view = LocalUIView.current',kotlin)
        self.assertNotIn('view.',kotlin)
        self.assertNotIn('delay(',kotlin)

    def test_safe_failed_attachment_is_not_complete_and_privacy_bounds_remain_strict(self):
        self.assertTrue(intake(fixture())['complete'])
        value=fixture();value.update(installed=False,installation=[0,0,0],rows=[])
        self.assertFalse(intake(value)['complete'])
        for change in (lambda v:v.update(private='forbidden'),lambda v:v.update(schema=True),
            lambda v:v.update(process=True),lambda v:v.update(token='foreign'),lambda v:v.update(capturedMs=21),
            lambda v:v.update(installation=[1,True,1]),lambda v:v.update(installation=[1,2,1]),
            lambda v:v.update(rows=v['rows']*129),lambda v:v['rows'][0].__setitem__(0,float('nan')),
            lambda v:v['rows'][0].__setitem__(1,True),lambda v:v['rows'][1].__setitem__(2,1),
            lambda v:v['rows'][1].__setitem__(0,-1)):
            value=fixture();change(value)
            with self.subTest(change=change),self.assertRaises(ValueError):intake(value)

    def test_all_existing_companions_and_one_successful_original_attachment_are_required(self):
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve();state=evidence(out)
            self.assertEqual(attached.accept_native_export(out,state),attached.RESULT)
            p=next((out/'compose').glob('input-attachment-*.json'));raw=p.read_bytes()
            for change in (lambda v:v.update(installed=False),lambda v:v.update(rows=[]),
                lambda v:v.update(overflow=1),lambda v:v['rows'][0].__setitem__(2,0),
                lambda v:v.update(rows=v['rows']*2),lambda v:v.update(rows=[[5,0,1],[6,1,0]])):
                value=json.loads(raw);change(value);p.write_text(json.dumps(value))
                with self.assertRaises(ValueError):attached.accept_native_export(out,state)
            p.write_bytes(raw)
            value=copy.deepcopy(state);value['arms'][0]['cleanup']='FAIL'
            with self.assertRaises(ValueError):attached.accept_native_export(out,value)
            next((out/'compose').glob('native-input-*.json')).unlink()
            with self.assertRaises((ValueError,OSError)):attached.accept_native_export(out,state)

    def test_actual_owned_attachment_file_retained_once_with_exact_lower_read_ceiling(self):
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve();sandbox=out/'sandbox';destination=out/'retained';destination.mkdir()
            value=fixture();value.update(installed=False,installation=[1,0,0],rows=[])
            raw=json.dumps(value).encode();legacy=dict(process=100,capturedMs=20)
            path=sandbox/'tmp'/('parlor-pin27-'+value['token'])/'attachment.json'
            path.parent.mkdir(parents=True);path.write_bytes(raw)
            args=(sandbox,destination,value['token'],value['simulator'],value['bundle'],legacy)
            with patch.object(attached.delivery,'retain_companion',return_value={'old':'unchanged'}):
                self.assertEqual(attached.retain_companion(*args),{'old':'unchanged'})
                self.assertEqual((destination/('input-attachment-'+value['token']+'.json')).read_bytes(),raw)
                with self.assertRaises(FileExistsError):attached.retain_companion(*args)
            path.write_bytes(b'x'*(attached.MAX_BYTES+1))
            with self.assertRaises(ValueError):run_pin03.read_capture(path,maximum=attached.MAX_BYTES)

    @unittest.skipUnless(platform.system()=='Darwin','Actual Objective-C/Apple SDK unavailable on Linux')
    def test_actual_apple_attachment_original_child_once_parent_isolation_and_no_retries(self):
        control='''
var installationCalls = 0
var armed = true
var originalCalls = 0
var requireInstalledBeforeOriginal = false
let input = NSObject()
let foreign = NSObject()
class PIN27Parent: NSObject {
    @objc(didAddSubview:) dynamic func didAddSubview(_ child: AnyObject) {
        precondition(child === input || child === foreign)
        if requireInstalledBeforeOriginal { precondition(installationCalls == 1) }
        originalCalls += 1
    }
}
class PIN27Leaf: PIN27Parent { }
let observer = PIN27InputAttachment(armed: { armed }, installDelivery: { installationCalls += 1; return true })
observer.controlEligible = { $0 === input }
let selector = NSSelectorFromString("didAddSubview:")
let before = method_getImplementation(class_getInstanceMethod(PIN27Parent.self, selector)!)
precondition(observer.installVerifiedContainer(PIN27Leaf.self))
let leaf = PIN27Leaf()
leaf.didAddSubview(foreign)
precondition(installationCalls == 0 && originalCalls == 1)
requireInstalledBeforeOriginal = true
leaf.didAddSubview(input)
precondition(installationCalls == 1 && originalCalls == 2)
leaf.didAddSubview(input) // original forwarding continues; installation never repeats.
precondition(installationCalls == 1 && originalCalls == 3)
armed = false
leaf.didAddSubview(foreign)
PIN27Parent().didAddSubview(input)
precondition(originalCalls == 5)
let rows = observer.controlSnapshot()["rows"] as! [[Double]]
precondition(rows.count == 2 && Array(rows[0][1...]) == [0,1] && Array(rows[1][1...]) == [1,0])
let after = method_getImplementation(class_getInstanceMethod(PIN27Parent.self, selector)!)
precondition(unsafeBitCast(before,to:UInt.self) == unsafeBitCast(after,to:UInt.self))
precondition(!observer.installVerifiedContainer(PIN27Leaf.self))
precondition((observer.controlSnapshot()["overflow"] as! Int) == 0)
print("ACTUAL_APPLE_ATTACHMENT_ORIGINAL_ONCE_AND_PARENT_ISOLATION_PASS_NOT_APP_QUALIFICATION")
'''
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve();source=out/'main.swift';source.write_text(attached.template('PIN27InputAttachment.swift.in')+control)
            binary=out/'control'
            compilation=subprocess.run(['/usr/bin/xcrun','swiftc','-D','PIN27_CONTROL','-module-cache-path',str(out/'cache'),
                str(source),'-o',str(binary)],capture_output=True,text=True,timeout=180)
            self.assertEqual(compilation.returncode,0,compilation.stdout+compilation.stderr)
            result=subprocess.run([str(binary)],capture_output=True,text=True,timeout=30)
            self.assertEqual(result.returncode,0,result.stdout+result.stderr)
            self.assertIn('ACTUAL_APPLE_ATTACHMENT_ORIGINAL_ONCE_AND_PARENT_ISOLATION_PASS_NOT_APP_QUALIFICATION',result.stdout)
            sdk=subprocess.check_output(['/usr/bin/xcrun','--sdk','iphonesimulator','--show-sdk-path'],text=True,timeout=45).strip()
            standalone=out/'attachment.swift';standalone.write_text(attached.template('PIN27InputAttachment.swift.in'))
            typecheck=subprocess.run(['/usr/bin/xcrun','swiftc','-typecheck','-D','DEBUG','-sdk',sdk,
                '-target','arm64-apple-ios18.5-simulator','-module-cache-path',str(out/'sim-cache'),str(standalone)],
                capture_output=True,text=True,timeout=180)
            self.assertEqual(typecheck.returncode,0,typecheck.stdout+typecheck.stderr)


if __name__=='__main__':unittest.main()
