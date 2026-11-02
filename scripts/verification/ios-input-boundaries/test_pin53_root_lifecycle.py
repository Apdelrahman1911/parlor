"""Portable exact-source/privacy/custody checks; actual ABI/typecheck on standard Mac."""
import copy
import hashlib
import json
from pathlib import Path
import platform
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import pin53_root_lifecycle as c
import run_pin53
import test_pin51_owned_text as prior


def plan():
    leaf=c.runtime.pair_control.leaf
    return c.renderer_for_profile(c.PROFILE,c.SOURCE,c.RUNTIME,c.DEVICE)(
        {p:leaf.pair.fixture(p) for p in leaf.pair.MODIFIED},leaf.pair.INPUTS,arm='compose')


def evidence(out):
    state=prior.evidence(out);d=out/'compose';row=state['arms'][0]
    state['profile']=c.PROFILE;row.update(methods={c.CASE:'Failed'},attachment_test_ids=[c.CASE])
    for name in ('summary','tests'):
        raw=(d/(name+'.json')).read_text().replace(c.base.CASE,c.CASE).replace(c.base.FAILURE,c.FAILURE)
        (d/(name+'.json')).write_text(raw)
    (d/'inputs.json').write_text(json.dumps(state['original_inputs']|{p:hashlib.sha256(v.encode()).hexdigest() for p,v in plan().items()}))
    cmd=next(v for v in state['commands'] if v['label']=='compose-tests')
    cmd['command']=[a.replace(c.base.CASE.removesuffix('()'),c.CASE.removesuffix('()')) for a in cmd['command']]
    (d/'test.log').write_text((d/'test.log').read_text().replace('testPIN51OwnedTextExportCapability','testPIN53RootLifecycleExportCapability'))
    p=next(d.glob('native-text-*.json'));v=c.load(p);v['schema']=53
    v['root']=dict(schema=53,installation=[1,1,1],attemptedRoot=True,overflow=0,overlayAlive=True,rootAlive=True,
        capturedMs=498,rows=[[1,3,0,0],[2,3,1,1],[3,1,0,0],[4,2,0,1],[5,2,1,0],[6,1,1,1]])
    raw=json.dumps(v).encode();p.write_bytes(raw)
    legacy=c.load(next(d.glob('capture-*.json')))
    row['companion_captures']=[c.decode(raw,token=v['token'],simulator=row['simulator'],bundle=v['bundle'],legacy=legacy)]
    return state


CONTROL=r'''
import Foundation
let childList = NSArray(object: NSObject())
class RootParent: NSObject {
    var content: AnyObject?
    var sets = 0
    @objc(accessibilityElements) dynamic func elements() -> AnyObject? { content }
    @objc(setAccessibilityElements:) dynamic func assign(_ value: AnyObject?) {
        precondition(value == nil || value === childList)
        content = value; sets += 1
    }
}
class Root: RootParent {
    var gets = 0
    @objc(accessibilityElements) override dynamic func elements() -> AnyObject? {
        gets += 1
        if gets == 1 { assign(childList) }
        return content
    }
}
class OverlayParent: NSObject {
    var content: AnyObject?
    var gets = 0
    @objc(accessibilityElements) dynamic func elements() -> AnyObject? { gets += 1; return content }
}
class OverlayLeaf: OverlayParent {}
class BadRoot: NSObject {
    @objc(accessibilityElements) dynamic func elements() -> Int { 7 }
}
class BadOverlayLeaf: OverlayParent {}
var armed = true
let root = Root()
let overlay = OverlayLeaf()
let rootArray = NSArray(object: root)
overlay.content = rootArray
let unrelated = OverlayLeaf(); unrelated.content = rootArray
let getSel = NSSelectorFromString("accessibilityElements")
let setSel = NSSelectorFromString("setAccessibilityElements:")
let parentGet = method_getImplementation(class_getInstanceMethod(OverlayParent.self, getSel)!)
let parentSet = method_getImplementation(class_getInstanceMethod(RootParent.self, setSel)!)
let observer = PIN53RootLifecycle(armed: { armed })
observer.controlEligible = { $0 is Root }
precondition(observer.bindVerifiedOverlay(overlay, target: OverlayLeaf.self))
precondition(!observer.bindVerifiedOverlay(overlay, target: OverlayLeaf.self))
precondition(unrelated.elements() === rootArray && unrelated.gets == 1)
precondition((observer.freeze()["installation"] as! [Int]) == [1,0,0])
precondition(overlay.elements() === rootArray && overlay.gets == 1)
precondition(root.gets == 0 && root.sets == 0) // No proactive root getter, activation or assignment.
precondition(root.elements() === childList && root.gets == 1 && root.sets == 1)
root.assign(nil)
precondition(root.elements() == nil && root.gets == 2 && root.sets == 2)
precondition(unsafeBitCast(parentGet,to: UInt.self) == unsafeBitCast(method_getImplementation(class_getInstanceMethod(OverlayParent.self,getSel)!),to: UInt.self))
precondition(unsafeBitCast(parentSet,to: UInt.self) == unsafeBitCast(method_getImplementation(class_getInstanceMethod(RootParent.self,setSel)!),to: UInt.self))
let data = observer.freeze()
precondition(data["installation"] as! [Int] == [1,1,1] && data["rootAlive"] as! Bool)
let rows = data["rows"] as! [[Double]]
precondition(rows.count == 10 && rows[3][1] == 2 && rows[3][3] == 1 && rows[6][1] == 2 && rows[6][3] == 0)
armed = false
precondition(root.elements() == nil && root.gets == 3 && (observer.freeze()["rows"] as! [[Double]]).count == 10)
armed = true
for _ in 0..<4096 { precondition(root.elements() == nil) }
precondition(root.gets == 4099 && (observer.freeze()["rows"] as! [[Double]]).count == 4096)
precondition(observer.freeze()["overflow"] as! Int > 0)
let bad = BadRoot(); let badOverlay = BadOverlayLeaf(); badOverlay.content = NSArray(object: bad)
let negative = PIN53RootLifecycle(armed: { true }); negative.controlEligible = { $0 is BadRoot }
precondition(negative.bindVerifiedOverlay(badOverlay,target: BadOverlayLeaf.self))
_ = badOverlay.elements()
precondition(negative.freeze()["installation"] as! [Int] == [1,0,0] && bad.elements() == 7)
print("ACTUAL_ROOT_GETTER_SETTER_ARGUMENT_RETURN_NIL_NO_PRIMING_INSTANCE_PARENT_BOUNDING_PASS")
'''


class RootLifecycleControls(unittest.TestCase):
    def test_plan_preserves_original_successful_helpers_and_unchanged_native_text_collector(self):
        current=plan();old=prior.plan();leaf=c.runtime.pair_control.leaf
        driver=current[c.pin02_capture.DRIVER]
        inverse=driver.replace('PIN53Configure','PIN51Configure').replace('PARLOR_PIN53_','PARLOR_PIN51_').replace(c.PROFILE,c.base.PROFILE)
        inverse=inverse.replace('testPIN53RootLifecycleExportCapability','testPIN51OwnedTextExportCapability').replace(c.FAILURE,c.base.FAILURE)
        self.assertEqual(inverse,old[c.pin02_capture.DRIVER])
        for p in current:
            if p not in c.pin02_capture.MODIFIED:self.assertEqual(current[p],leaf.pair.fixture(p))
        self.assertIn('pin11PublicApp = app',driver)
        self.assertEqual(driver.count('name.typeText("Table Test")'),2)
        self.assertIn(c.base.template('PIN51OwnedTextDelivery.swift.in'),current[c.pin02_capture.CONTAINER])
        self.assertIn(c.owner_source(),current[c.pin02_capture.CONTAINER])

    def test_actual_capability_prerequisite_and_source_profile_hash_required(self):
        c.prerequisite.require_capability()
        with patch.object(c,'ROOT_SHA256','a'*64),self.assertRaises(ValueError):c.root_source()
        with self.assertRaises(ValueError):c.renderer_for_profile(c.PROFILE,'0'*40,c.RUNTIME,c.DEVICE)
        self.assertEqual(c.TEST_TIMEOUT_SECONDS,900)
        self.assertEqual(c.CASES,(c.CASE,))

    def test_no_proactive_root_queries_private_getters_events_or_runtime_class_enumeration(self):
        source=c.root_source()
        for forbidden in ('accessibilityLabel','accessibilityValue','objc_copyClassList','DispatchQueue','Timer(',
            'addGestureRecognizer','textInRange','selectedTextRange','NSMethodSignature','object.value'):
            self.assertNotIn(forbidden,source)
        for call in ('original(object, selector)','originalGet(object, getSelector)','originalSet(object, setSelector, value)'):
            self.assertEqual(source.count(call),1)
        self.assertIn('let result = original(object, selector)',source)
        self.assertIn('self?.bindReturnedRootOnce(result)',source)
        self.assertNotIn('.accessibilityElements',source)
        self.assertIn('own(target, getter) == nil',source)
        self.assertIn('own(target, setter) == nil',source)

    def test_numeric_root_source_product_request_and_original_failure_admission(self):
        with tempfile.TemporaryDirectory() as tmp:
            out=Path(tmp).resolve();state=evidence(out)
            self.assertEqual(c.accept_native_export(out,state),c.RESULT)
            receipt=c.load(out/'root-lifecycle-export.json')
            for k in ('application_root_cause','shipping_qualified','physical_verified','native_timing_neutrality','root_queries_added'):
                self.assertIs(receipt[k],False)

    def test_private_invalid_abi_status_clock_pid_overflow_and_unpaired_calls_reject(self):
        changes=(lambda v:v.update(private='forbidden'),lambda v:v.update(schema=True),lambda v:v.update(process=43),
            lambda v:v['root'].update(private='forbidden'),lambda v:v['root'].update(schema=True),
            lambda v:v['root'].update(installation=[1,0,1]),lambda v:v['root'].update(overflow=1),
            lambda v:v['root'].update(rootAlive=False),lambda v:v['root'].update(capturedMs=501),
            lambda v:v['root']['rows'].pop(),lambda v:v['root']['rows'][0].__setitem__(0,float('nan')),
            lambda v:v['root']['rows'][0].__setitem__(3,1))
        for edit in changes:
            with tempfile.TemporaryDirectory() as tmp:
                out=Path(tmp).resolve();state=evidence(out);p=next((out/'compose').glob('native-text-*.json'));v=c.load(p);edit(v);p.write_text(json.dumps(v))
                with self.assertRaises(ValueError):c.accept_native_export(out,state)

    def test_old_early_failure_missing_ack_changed_source_commands_cleanup_or_deadline_reject(self):
        changes=(lambda o,s:s.update(source='0'*40),lambda o,s:s['arms'][0].update(cleanup='FAIL'),
            lambda o,s:s['arms'][0].update(companion_captures=[]),lambda o,s:s['commands'].append(s['commands'][0]),
            lambda o,s:s['build_profile'].update(test_timeout_seconds=901),
            lambda o,s:next((o/'compose').glob('native-text-*.json')).unlink())
        for change in changes:
            with tempfile.TemporaryDirectory() as tmp:
                out=Path(tmp).resolve();state=evidence(out);change(out,state)
                with self.assertRaises((ValueError,FileNotFoundError)):c.accept_native_export(out,state)
        with tempfile.TemporaryDirectory() as tmp:
            out=Path(tmp).resolve();state=evidence(out);summary=c.load(out/'compose/summary.json');tests=c.load(out/'compose/tests.json')
            summary['testFailures'][0]['failureText']='Original input assertion failed'
            with self.assertRaises(ValueError):c.verify_results(summary,tests,state['arms'][0]['simulator'],65,require_exporter=True)

    def test_external_profile_restore_and_new_diagnostic_never_shipping_mapping(self):
        profiles,select=run_pin53.prior.PROFILES,run_pin53.prior.select_profile
        with patch.object(run_pin53.sys,'argv',['run','--profile',c.PROFILE]),patch.object(run_pin53.run_pin03,'main',side_effect=RuntimeError('actual failure')):
            with self.assertRaises(RuntimeError):run_pin53.main()
        self.assertEqual(run_pin53.prior.PROFILES,profiles);self.assertIs(run_pin53.prior.select_profile,select)
        self.assertEqual(run_pin53.run_pin03.completed_exit_code(c.RESULT),1)

    @unittest.skipUnless(platform.system()=='Darwin','Actual Apple Objective-C ABI unavailable on Linux')
    def test_actual_apple_root_abi_nil_return_argument_parent_instance_and_no_priming(self):
        with tempfile.TemporaryDirectory() as tmp:
            out=Path(tmp).resolve();source=out/'main.swift';source.write_text(c.root_source()+CONTROL);binary=out/'control'
            r=subprocess.run(['/usr/bin/xcrun','swiftc','-D','PIN53_CONTROL','-module-cache-path',str(out/'cache'),str(source),'-o',str(binary)],capture_output=True,text=True,timeout=180)
            self.assertEqual(r.returncode,0,r.stdout+r.stderr)
            r=subprocess.run([str(binary)],capture_output=True,text=True,timeout=30)
            self.assertEqual(r.returncode,0,r.stdout+r.stderr)
            self.assertIn('ACTUAL_ROOT_GETTER_SETTER_ARGUMENT_RETURN_NIL_NO_PRIMING_INSTANCE_PARENT_BOUNDING_PASS',r.stdout)

    @unittest.skipUnless(platform.system()=='Darwin','Actual Apple Simulator SDK unavailable on Linux')
    def test_actual_apple_new_assembled_owner_typecheck_and_original_driver_parse(self):
        with tempfile.TemporaryDirectory() as tmp:
            out=Path(tmp).resolve();sdk=subprocess.check_output(['/usr/bin/xcrun','--sdk','iphonesimulator','--show-sdk-path'],text=True,timeout=45).strip()
            p=out/'owner.swift';p.write_text(plan()[c.pin02_capture.CONTAINER])
            r=subprocess.run(['/usr/bin/xcrun','swiftc','-typecheck','-D','DEBUG','-sdk',sdk,'-target','arm64-apple-ios16.0-simulator',
                '-module-cache-path',str(out/'cache'),str(p)],capture_output=True,text=True,timeout=180)
            self.assertEqual(r.returncode,0,r.stdout+r.stderr)
            p=out/'driver.swift';p.write_text(plan()[c.pin02_capture.DRIVER])
            r=subprocess.run(['/usr/bin/xcrun','swiftc','-frontend','-parse','-D','DEBUG','-target','arm64-apple-ios26.2-simulator',str(p)],capture_output=True,text=True,timeout=120)
            self.assertEqual(r.returncode,0,r.stdout+r.stderr)


if __name__=='__main__':unittest.main()
