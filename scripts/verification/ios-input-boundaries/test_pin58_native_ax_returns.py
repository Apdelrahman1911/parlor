"""Portable negative/plan fixtures and actual Apple ABI controls; not application qualification."""
import copy
import hashlib
import json
from pathlib import Path
import platform
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import pin58_native_ax_returns as p
import run_pin58
import test_pin57_input_intermittency as seed


def plan():
    leaf = p.runtime.pair_control.leaf
    return p.renderer_for_profile(p.PROFILE,p.SOURCE,p.RUNTIME,p.DEVICE)(
        {s:leaf.pair.fixture(s) for s in leaf.pair.MODIFIED},leaf.pair.INPUTS,arm='compose')


def evidence(out, failed=()):
    state = seed.evidence(out,failed=failed)
    d=out/'compose'; row=state['arms'][0]
    state['profile']=p.PROFILE
    (d/'inputs.json').write_text(json.dumps(state['original_inputs'] |
        {s:hashlib.sha256(v.encode()).hexdigest() for s,v in plan().items()}))
    row['companion_captures']=[]
    for path in sorted(d.glob('native-text-*.json')):
        value=p.load(path)
        value['root']['leafReturns']=dict(schema=58,installed=True,capturedMs=498,
            rows=[[497,1,1,0,1,0],[498,1,1,1,1,0]],evicted=0,rejected=0,nodesAlive=[True],
            scope='Original native NotEnabled and isAccessibilityElement returns with original public catalog prefix classification; weak ordinals, rolling window; no text retained, not full semantics, CPU, causality or qualification')
        raw=json.dumps(value).encode(); path.write_bytes(raw)
        legacy=p.load(d/('capture-'+value['token']+'.json'))
        row['companion_captures'].append(p.decode(raw,token=value['token'],simulator=row['simulator'],
            bundle=state['debug_bundle'],legacy=legacy))
    return state


OBJC_HEADER=r'''
#import <Foundation/Foundation.h>
#include <stdint.h>
#include <stdbool.h>
@interface PIN58ObjCReturnNode : NSObject {
    __weak id _lastLabel;
}
@property(nonatomic) NSInteger labelKind;
- (uint64_t)accessibilityTraits;
- (bool)isAccessibilityElement;
- (id)accessibilityLabel;
- (id)lastLabel;
@end
'''
OBJC_SOURCE=r'''
#import "return_control.h"
@implementation PIN58ObjCReturnNode
- (uint64_t)accessibilityTraits { return UINT64_C(0x4000000000000101); }
- (bool)isAccessibilityElement { return true; }
- (id)accessibilityLabel {
    if (self.labelKind == 0) { _lastLabel = nil; return nil; }
    NSArray *labels = @[@"Open Egyptian Dominoes, public", @"افتح الدومنة المصرية، عام",
        @"Open Ghamza, public", @"افتح غمزة، عام", @"Open Word Impostor, public",
        @"افتح لعبة الإمبوستر، عام", @"Unrelated label is never retained"];
    // Fresh heap object; ordinary +0 ObjC getter with ARC autoreleased return.
    NSMutableString *result = self.labelKind == 8
        ? [[NSMutableString alloc] initWithString:[@"Open Word Impostor" stringByPaddingToLength:513 withString:@"x" startingAtIndex:0]]
        : [[NSMutableString alloc] initWithString:labels[self.labelKind - 1]];
    _lastLabel = result;
    return result;
}
- (id)lastLabel { return _lastLabel; }
@end
'''


CONTROL=r'''
import Foundation
class Parent: NSObject {
    @objc(accessibilityTraits) dynamic func traits() -> UInt64 { 99 }
    @objc(isAccessibilityElement) dynamic func element() -> Bool { false }
    @objc(accessibilityLabel) dynamic func label() -> AnyObject? { nil }
}
class Node: Parent {
    var gets = 0
    var traitsValue: UInt64 = 0x4000000000000101
    @objc(accessibilityTraits) override dynamic func traits() -> UInt64 { gets += 1; return traitsValue }
    @objc(isAccessibilityElement) override dynamic func element() -> Bool { gets += 1; return true }
    @objc(accessibilityLabel) override dynamic func label() -> AnyObject? { "Open Word Impostor, public" as NSString }
}
class Bad: NSObject {
    @objc(accessibilityTraits) dynamic func traits() -> UInt64 { 1 }
    @objc(isAccessibilityElement) dynamic func element() -> UInt64 { 1 }
    @objc(accessibilityLabel) dynamic func label() -> AnyObject? { nil }
}
let sel=NSSelectorFromString("accessibilityTraits")
let parent=method_getImplementation(class_getInstanceMethod(Parent.self,sel)!)
let observer=PIN58NativeAXReturns(armed: { true })
observer.controlEligible={ $0 === Node.self }
let node=Node()
observer.observeOriginalRootArray(NSArray(object:node))
precondition(node.gets == 0) // No priming query.
precondition(observer.freeze()["installed"] as! Bool)
// A frozen observer still forwards originals with exact high bits/Boolean returns.
precondition(node.traits() == 0x4000000000000101 && node.element() && node.gets == 2)
precondition(node.label() as? String == "Open Word Impostor, public")
precondition((observer.freeze()["rows"] as! [[Double]]).isEmpty)
precondition(unsafeBitCast(parent,to:UInt.self)==unsafeBitCast(method_getImplementation(class_getInstanceMethod(Parent.self,sel)!),to:UInt.self))
class Other: Parent {
    @objc(accessibilityTraits) override dynamic func traits() -> UInt64 { 257 }
    @objc(isAccessibilityElement) override dynamic func element() -> Bool { true }
    @objc(accessibilityLabel) override dynamic func label() -> AnyObject? { "افتح لعبة الإمبوستر، عام" as NSString }
}
let active=PIN58NativeAXReturns(armed:{true})
active.controlEligible={ $0 === Other.self }
let other=Other()
active.observeOriginalRootArray(NSArray(object:other))
precondition(other.traits()==257 && other.element())
precondition(other.label() as? String == "افتح لعبة الإمبوستر، عام")
let done=DispatchSemaphore(value:0)
Thread { precondition(other.traits()==257); done.signal() }.start()
precondition(done.wait(timeout:.now()+5) == .success)
let early=active.controlRows()
precondition(early.contains { $0[1]==0 && $0[2]==1 && $0[3]==1 && $0[5]==1 })
precondition(early.contains { $0[2]==3 && $0[3]==1 && $0[5]==3 })
for _ in 0..<2500 { precondition(other.traits()==257) }
let data=active.freeze(); let rows=data["rows"] as! [[Double]]
precondition(rows.count==4096 && data["evicted"] as! Int==912 && data["rejected"] as! Int==0)
precondition(Array(rows.last![2...])==[1,1,1,1])
// Actual ObjC-origin +0 object return: identity, no retained label, nil, all six prefixes.
let baseline=PIN58ObjCReturnNode(); baseline.labelKind=1
weak var baselineText: AnyObject?
autoreleasepool {
    let result=baseline.perform(NSSelectorFromString("accessibilityLabel"))!.takeUnretainedValue()
    precondition(result === baseline.perform(NSSelectorFromString("lastLabel"))!.takeUnretainedValue())
    precondition(result as? String == "Open Egyptian Dominoes, public")
    baselineText=result
}
precondition(baselineText == nil && baseline.perform(NSSelectorFromString("lastLabel")) == nil)
baseline.labelKind=0; precondition(baseline.perform(NSSelectorFromString("accessibilityLabel")) == nil)
let native=PIN58ObjCReturnNode()
let ownership=PIN58NativeAXReturns(armed:{true}); ownership.controlEligible={ $0 === PIN58ObjCReturnNode.self }
ownership.observeOriginalRootArray(NSArray(object:native))
precondition(native.perform(NSSelectorFromString("lastLabel")) == nil) // No priming.
precondition(native.accessibilityTraits()==0x4000000000000101 && native.isAccessibilityElement())
for (kind, expected) in [(1,1),(2,1),(3,2),(4,2),(5,3),(6,3),(7,0),(8,0)] {
    native.labelKind=kind
    weak var text: AnyObject?
    autoreleasepool {
        let result=native.perform(NSSelectorFromString("accessibilityLabel"))!.takeUnretainedValue()
        precondition(result === native.perform(NSSelectorFromString("lastLabel"))!.takeUnretainedValue())
        if kind == 8 { precondition((result as! NSString).length == 513) }
        else {
            let originals=["Open Egyptian Dominoes, public", "افتح الدومنة المصرية، عام", "Open Ghamza, public",
                "افتح غمزة، عام", "Open Word Impostor, public", "افتح لعبة الإمبوستر، عام", "Unrelated label is never retained"]
            precondition(result as? String == originals[kind-1])
        }
        text=result
    }
    precondition(text == nil && native.perform(NSSelectorFromString("lastLabel")) == nil)
    precondition(ownership.controlRows().last![5] == Double(expected))
}
native.labelKind=0
precondition(native.perform(NSSelectorFromString("accessibilityLabel")) == nil)
let frozenOwnership=ownership.freeze()
precondition((frozenOwnership["rows"] as! [[Double]]).last![5] == 0)
precondition(!(String(describing:frozenOwnership).contains("Open ")))
let negative=PIN58NativeAXReturns(armed:{true}); negative.controlEligible={ $0 === Bad.self }
let bad=Bad(); negative.observeOriginalRootArray(NSArray(object:bad))
precondition(!(negative.freeze()["installed"] as! Bool) && bad.traits()==1 && bad.element()==1)
print("ACTUAL_NATIVE_AX_RETURN_ABI_ORIGINAL_HIGH_BITS_BOOL_PARENT_NO_PRIMING_ROLLING_BOUNDS_PASS")
'''


class NativeAXReturnTests(unittest.TestCase):
    def test_only_root_setter_observation_and_new_numeric_class_change_the_plan(self):
        old=seed.plan(); new=plan()
        self.assertEqual(set(old),set(new))
        for name in new:
            if name != p.prior.pin02_capture.CONTAINER:
                self.assertEqual(new[name],old[name])
        owner=new[p.prior.pin02_capture.CONTAINER]
        self.assertIn('if observed { self?.leafReturns.observeOriginalRootArray(value) }',owner)
        self.assertEqual(owner.count('originalSet(object, setSelector, value)'),1)
        self.assertEqual(owner.count('name.typeText("Table Test")'),0)
        self.assertEqual(p.CASES,p.prior.CASES)
        self.assertEqual(p.TEST_TIMEOUT_SECONDS,p.prior.TEST_TIMEOUT_SECONDS)

    def test_observer_has_no_extra_attribute_queries_events_threads_or_private_payloads(self):
        source=p.observation_source()
        for forbidden in ('accessibilityValue','accessibilityFrame','objc_copyClassList',
                          'DispatchQueue','Timer(','thread_suspend','setText','typeText','readValue','value(forKey'):
            self.assertNotIn(forbidden,source)
        self.assertEqual(source.count('originalTraits(object, traitsSelector)'),1)
        self.assertEqual(source.count('originalElement(object, elementSelector)'),1)
        self.assertIn('private weak',source.replace('weak var','private weak var'))
        self.assertIn('private let limit = 4096',source)
        self.assertIn('nodes.count < 512',source)
        self.assertIn('UIAccessibilityTraits.notEnabled.rawValue',source)
        with patch.object(p,'TEMPLATE_SHA256','0'*64),self.assertRaises(ValueError):
            p.observation_source()

    def test_all_original_failures_and_exact_source_commands_cleanup_stay_required(self):
        for failed in ((),(p.CASES[1],),(p.CASES[0],p.CASES[1])):
            with tempfile.TemporaryDirectory() as tmp:
                out=Path(tmp).resolve(); state=evidence(out,failed)
                self.assertEqual(p.accept_native_export(out,state),p.RESULT)
                receipt=p.load(out/'input-intermittency.json')
                self.assertEqual(receipt['methods'],state['arms'][0]['methods'])
                self.assertFalse(receipt['shipping_qualified'])
                self.assertFalse(receipt['application_root_cause'])
                self.assertFalse(receipt['physical_verified'])
        with tempfile.TemporaryDirectory() as tmp:
            out=Path(tmp).resolve(); state=evidence(out)
            state['source']='0'*40
            with self.assertRaises(ValueError):p.accept_native_export(out,state)

    def test_unknown_private_unbounded_or_wrong_clock_native_fields_reject(self):
        changes=(lambda v:v.update(private='x'),lambda v:v.update(schema=True),
            lambda v:v.update(nodesAlive=[1]),lambda v:v.update(rows=[[499,1,1,1,1,0]]),
            lambda v:v.update(rows=[[498,1,1,0,1,1]]),lambda v:v.update(rows=[[498,1,1,1,2,0]]),
            lambda v:v.update(rows=[[float('nan'),1,1,1,1,0]]),lambda v:v.update(installed=False),
            lambda v:v.update(evicted=-1),lambda v:v.update(evicted=1),lambda v:v.update(rejected=1),
            lambda v:v.update(installed=False,rows=[]),lambda v:v.update(rows=[] ,nodesAlive=[True]*513))
        for change in changes:
            with tempfile.TemporaryDirectory() as tmp:
                out=Path(tmp).resolve();state=evidence(out,(p.CASES[1],))
                path=next((out/'compose').glob('native-text-*.json')); value=p.load(path)
                change(value['root']['leafReturns']); path.write_text(json.dumps(value))
                with self.assertRaises(ValueError):p.accept_native_export(out,state)

    def test_failed_acceptance_does_not_leave_prior_profile_mutated(self):
        saved={k:getattr(p.prior,k) for k in ('PROFILE','RESULT','renderer_for_profile','decode')}
        with tempfile.TemporaryDirectory() as tmp:
            out=Path(tmp).resolve();state=evidence(out);state['arms'][0]['cleanup']='FAIL'
            with self.assertRaises(ValueError):p.accept_native_export(out,state)
        for k,v in saved.items():self.assertIs(getattr(p.prior,k),v)
        self.assertEqual(run_pin58.run_pin03.completed_exit_code(p.RESULT),1)

    @unittest.skipUnless(platform.system()=='Darwin','Actual Objective-C ABI requires Apple runtime')
    def test_actual_apple_original_return_abi_and_no_priming(self):
        with tempfile.TemporaryDirectory() as tmp:
            out=Path(tmp).resolve(); source=out/'main.swift'; source.write_text(p.observation_source()+CONTROL)
            binary=out/'control'; header=out/'return_control.h'; objc=out/'return_control.m'; obj=out/'return_control.o'
            header.write_text(OBJC_HEADER); objc.write_text(OBJC_SOURCE)
            r=subprocess.run(['/usr/bin/xcrun','clang','-fobjc-arc','-c',str(objc),'-o',str(obj)],capture_output=True,text=True,timeout=90)
            self.assertEqual(r.returncode,0,r.stdout+r.stderr)
            r=subprocess.run(['/usr/bin/xcrun','swiftc','-D','PIN58_CONTROL','-import-objc-header',str(header),
                '-module-cache-path',str(out/'cache'),str(source),str(obj),'-o',str(binary)],capture_output=True,text=True,timeout=180)
            self.assertEqual(r.returncode,0,r.stdout+r.stderr)
            r=subprocess.run([str(binary)],capture_output=True,text=True,timeout=30)
            self.assertEqual(r.returncode,0,r.stdout+r.stderr)
            self.assertIn('ACTUAL_NATIVE_AX_RETURN_ABI_ORIGINAL_HIGH_BITS_BOOL_PARENT_NO_PRIMING_ROLLING_BOUNDS_PASS',r.stdout)

    @unittest.skipUnless(platform.system()=='Darwin','Actual Simulator UIKit SDK requires Apple runtime')
    def test_actual_apple_full_owner_typecheck_and_unchanged_driver_parse(self):
        with tempfile.TemporaryDirectory() as tmp:
            out=Path(tmp).resolve(); sdk=subprocess.check_output(['/usr/bin/xcrun','--sdk','iphonesimulator','--show-sdk-path'],text=True,timeout=45).strip()
            owner=out/'owner.swift'; owner.write_text(plan()[p.prior.pin02_capture.CONTAINER])
            r=subprocess.run(['/usr/bin/xcrun','swiftc','-typecheck','-D','DEBUG','-sdk',sdk,'-target','arm64-apple-ios16.0-simulator',
                '-module-cache-path',str(out/'cache'),str(owner)],capture_output=True,text=True,timeout=180)
            self.assertEqual(r.returncode,0,r.stdout+r.stderr)
            driver=out/'driver.swift'; driver.write_text(plan()[p.prior.pin02_capture.DRIVER])
            r=subprocess.run(['/usr/bin/xcrun','swiftc','-frontend','-parse','-D','DEBUG','-target','arm64-apple-ios26.2-simulator',str(driver)],capture_output=True,text=True,timeout=120)
            self.assertEqual(r.returncode,0,r.stdout+r.stderr)


if __name__=='__main__':unittest.main()
