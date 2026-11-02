"""Portable privacy/source controls; actual Apple ABI and Simulator typecheck on Mac."""
import copy
import hashlib
import json
from pathlib import Path
import platform
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import pin02_capture
import pin51_owned_text as text
import run_pin51
import test_pin47_failure_runloop as runloop_tests


def plan():
    leaf = text.runtime.pair_control.leaf
    return text.renderer_for_profile(text.PROFILE,text.SOURCE,text.RUNTIME,text.DEVICE)(
        {p:leaf.pair.fixture(p) for p in leaf.pair.MODIFIED},leaf.pair.INPUTS,arm='compose')


def fixture():
    return dict(schema=51,token='11111111-1111-1111-1111-111111111111',
        simulator='22222222-2222-2222-2222-222222222222',bundle='me.parlor.ios.debug',process=100,
        capturedMs=20,viewCount=5,eligibleInputViews=1,treeComplete=True,deliveryBound=True,inputBindingAttempted=True,
        delivery=dict(schema=51,capturedMs=19,installed=True,installation=[1,1,1],overflow=0,attachments=1,
            inheritedResponderUntouched=True,rows=[[4,7,0,1,0],[5,7,1,1,0],[6,1,0,1,0],[7,1,1,1,0]]),
        runloop=runloop_tests.report())


def decode(v, complete=True):
    f = fixture()
    v = copy.deepcopy(v)
    v['runloop']['capturedMs'] = 18
    v['runloop'].update(longestActiveIntervalsMs=[],longestWaitingIntervalsMs=[],largestActiveMs=[],largestWaitingMs=[],
                        completedActiveIntervals=0,completedWaitingIntervals=0,partialActiveMs=[])
    return text.decode(json.dumps(v).encode(),token=f['token'],simulator=f['simulator'],bundle=f['bundle'],
                       legacy=dict(process=100,installedMs=2,capturedMs=21),require_complete=complete)


def evidence(out):
    state = runloop_tests.evidence(out)
    dest = out/'compose';row = state['arms'][0]
    state['profile'] = text.PROFILE
    row.update(methods={text.CASE:'Failed'}, attachment_test_ids=[text.CASE])
    summary = text.load(dest/'summary.json');tests = text.load(dest/'tests.json')
    summary.update(totalTestCount=1,passedTests=0,failedTests=1,
                   testFailures=[dict(testIdentifierString=text.CASE,failureText=text.FAILURE)])
    tests['testNodes'] = [dict(nodeType='Test Case',nodeIdentifier=text.CASE,result='Failed',durationInSeconds=100)]
    for name, value in (('summary',summary),('tests',tests)):
        (dest/(name+'.json')).write_text(json.dumps(value))
    (dest/'inputs.json').write_text(json.dumps(state['original_inputs'] |
        {path:hashlib.sha256(value.encode()).hexdigest() for path,value in plan().items()}))
    argv = next(c for c in state['commands'] if c['label']=='compose-tests')
    argv['command'] = text.test_command(['/usr/bin/xcodebuild','-project','iosApp/iosApp.xcodeproj','-scheme','iosApp',
        '-configuration','Debug','-sdk','iphonesimulator','-destination','platform=iOS Simulator,id='+row['simulator'],
        '-parallel-testing-enabled','NO','-maximum-concurrent-test-simulator-destinations','1'],dest/'tests.xcresult')
    (dest/'test.log').write_text("Test Case '-[iosAppUITests.IOSAppLaunchUITests testPIN51OwnedTextExportCapability]' started.\n")
    for path in dest.glob('native-*.json'):path.unlink()
    legacy = text.load(next(dest.glob('capture-*.json')))
    v=fixture();v.update(token=legacy['token'],simulator=row['simulator'],bundle=state['debug_bundle'],
                        process=legacy['process'],capturedMs=500,runloop=runloop_tests.report())
    v['delivery']['capturedMs']=499
    raw=json.dumps(v).encode();(dest/('native-text-'+legacy['token']+'.json')).write_bytes(raw)
    row['companion_captures']=[text.decode(raw,token=legacy['token'],simulator=row['simulator'],
        bundle=state['debug_bundle'],legacy=legacy)]
    return state


CONTROL = r'''
import Foundation
class HostParent: NSObject {
    static var becomeCalls = 0
    @objc dynamic func becomeFirstResponder() -> Bool { Self.becomeCalls += 1; return true }
}
let input = NSString(string: "synthetic public ABI fixture")
let rangeObject = NSObject()
let selected = NSRange(location: 1 << 40, length: 13)
class HostLeaf: HostParent {
    var calls = [0,0,0,0,0,0,0]
    @objc(insertText:) dynamic func insertText(_ text: AnyObject) { precondition(text === input); calls[0] += 1 }
    @objc(replaceRange:withText:) dynamic func replaceRange(_ range: AnyObject, withText text: AnyObject) {
        precondition(range === rangeObject && text === input); calls[1] += 1
    }
    @objc(setMarkedText:selectedRange:) dynamic func setMarkedText(_ text: AnyObject?, selectedRange range: NSRange) {
        precondition(range == selected && (text == nil || text === input)); calls[2] += 1
    }
    @objc dynamic func deleteBackward() { calls[3] += 1 }
    @objc(pressesBegan:withEvent:) dynamic func pressesBegan(_ presses: AnyObject, withEvent event: AnyObject?) {
        precondition(presses === rangeObject && event === input); calls[4] += 1
    }
    @objc(pressesEnded:withEvent:) dynamic func pressesEnded(_ presses: AnyObject, withEvent event: AnyObject?) {
        precondition(presses === rangeObject && event == nil); calls[5] += 1
    }
    @objc dynamic func resignFirstResponder() -> Bool { calls[6] += 1; return false }
}
class BadLeaf: HostParent {
    @objc(insertText:) dynamic func insertText(_ value: Int) {}
}
class ContainerParent: NSObject {
    var children = 0
    @objc(didAddSubview:) dynamic func didAddSubview(_ child: AnyObject) { children += 1 }
}
class ContainerLeaf: ContainerParent {}
var armed = true
let observer = PIN51OwnedTextDelivery(armed: { armed })
let container = ContainerLeaf()
observer.controlEligible = { $0 is HostLeaf }
precondition(observer.bindVerifiedContainer(container,target: ContainerLeaf.self))
precondition(!observer.bindVerifiedContainer(container,target: ContainerLeaf.self))
let unrelated = ContainerLeaf()
let first = HostLeaf()
let second = HostLeaf()
unrelated.didAddSubview(first)
container.didAddSubview(NSObject())
precondition((observer.freeze()["attachments"] as! Int) == 0)
container.didAddSubview(first)
precondition(first.becomeFirstResponder())
first.insertText(input)
first.replaceRange(rangeObject,withText: input)
first.setMarkedText(input,selectedRange: selected)
first.setMarkedText(nil,selectedRange: selected)
first.deleteBackward()
first.pressesBegan(rangeObject,withEvent: input)
first.pressesEnded(rangeObject,withEvent: nil)
precondition(!first.resignFirstResponder())
precondition(first.calls == [1,1,2,1,1,1,1])
precondition((observer.freeze()["rows"] as! [[Double]]).count == 18)
container.didAddSubview(second)
first.insertText(input) // Formerly owned editor: preserve delayed/stale-delivery boundary too.
second.insertText(input)
precondition((observer.freeze()["rows"] as! [[Double]]).count == 24)
armed = false
second.insertText(input)
precondition((observer.freeze()["rows"] as! [[Double]]).count == 24)
precondition(container.children == 3 && unrelated.children == 1)
precondition(HostParent().becomeFirstResponder() && HostParent.becomeCalls == 2)
let keptRows = observer.freeze()["rows"] as! [[Double]]
precondition(keptRows[20][3] == 1 && keptRows[22][3] == 2)
let status = observer.freeze()
precondition(status["installed"] as! Bool && status["inheritedResponderUntouched"] as! Bool)
precondition(status["installation"] as! [Int] == [1,1,1])
armed = true
for _ in 0..<4096 { second.insertText(input) }
precondition((observer.freeze()["rows"] as! [[Double]]).count == 4096)
precondition((observer.freeze()["overflow"] as! Int) > 0 && second.calls[0] == 4098)
print("ACTUAL_OWNED_TEXT_ABI_ARGUMENT_INSTANCE_PARENT_BOUNDING_PASS_NOT_APP_QUALIFICATION")
'''


class OwnedTextControls(unittest.TestCase):
    def test_plan_changes_only_copied_swift_container_and_driver(self):
        leaf = text.runtime.pair_control.leaf
        p = plan()
        self.assertEqual(set(p),set(leaf.pair.MODIFIED))
        for path in p:
            if path not in (pin02_capture.CONTAINER,pin02_capture.DRIVER):
                self.assertEqual(p[path],leaf.pair.fixture(path))
        driver=p[pin02_capture.DRIVER]
        self.assertIn(text.SENTINEL,driver)
        self.assertEqual(driver.count('name.typeText("Table Test")'),2) # Original helper body + separately named control.
        self.assertNotIn('test-iterations',text.test_command([],Path('/owned/result')))
        self.assertEqual(text.CASES,(text.CASE,))

    def test_native_templates_forward_once_and_never_read_private_arguments_or_ax(self):
        s = text.template('PIN51OwnedTextDelivery.swift.in')
        for forbidden in ('text.count','text.length','textInRange','accessibilityValue','accessibilityLabel',
                          'objc_copyClassList','method_exchangeImplementations','Timer(','DispatchQueue','class_addMethod(target, become'):
            self.assertNotIn(forbidden,s)
        for call in ('insert(object, selectors[0], text)','replace(object, selectors[1], range, text)',
                     'marked(object, selectors[2], text, range)','delete(object, selectors[3])',
                     'began(object, selectors[4], presses, event)','ended(object, selectors[5], presses, event)',
                     'resign(object, selectors[6])','original(object, selector, child)'):
            self.assertEqual(s.count(call),1)
        self.assertIn('editors[index].object === object',s)
        self.assertIn('container === object',s)
        self.assertIn('class_copyMethodList',s)
        owner=text.template('PIN51OwnedTextObservation.swift.in')
        self.assertIn('O_EXCL | O_NOFOLLOW',owner)
        self.assertIn('0o700',owner)
        self.assertIn('0o600',owner)
        self.assertNotIn('touches',owner)
        self.assertNotIn('onValueChange',owner)

    def test_duplicate_or_changed_source_and_wrong_profile_reject(self):
        with self.assertRaises(ValueError):text.renderer_for_profile(text.PROFILE,'0'*40,text.RUNTIME,text.DEVICE)
        for name in text.PINS:
            with patch.object(text.frozen_sources,'_read',return_value=b'changed'),self.assertRaises(ValueError):text.template(name)

    def test_numeric_privacy_pid_clock_and_complete_gate(self):
        self.assertTrue(decode(fixture())['complete'])
        changes=(lambda v:v.update(private='forbidden'),lambda v:v.update(schema=True),lambda v:v.update(process=True),
            lambda v:v.update(process=101),lambda v:v.update(capturedMs=22),lambda v:v.update(deliveryBound=False),
            lambda v:v['delivery'].update(private='forbidden'),lambda v:v['delivery'].update(schema=True),
            lambda v:v['delivery'].update(installed=False),lambda v:v['delivery'].update(inheritedResponderUntouched=False),
            lambda v:v['delivery'].update(overflow=1),lambda v:v['delivery'].update(installation=[1,1,0]),
            lambda v:v['delivery'].update(capturedMs=21),lambda v:v['delivery'].update(attachments=129),
            lambda v:v['delivery'].update(rows=[[3,1,0,1,'text']]),lambda v:v['delivery'].update(rows=[]),
            lambda v:v['delivery']['rows'][0].__setitem__(0,float('nan')),
            lambda v:v['delivery']['rows'][0].__setitem__(1,True),
            lambda v:v['delivery']['rows'][0].__setitem__(4,1))
        for change in changes:
            v=fixture();change(v)
            with self.subTest(change=change),self.assertRaises(ValueError):decode(v)
        v=fixture();v['delivery'].update(installed=False,installation=[1,1,0],inheritedResponderUntouched=False,rows=[])
        self.assertFalse(decode(v,False)['complete']) # Preserve safe failed-installation evidence, never admit as complete.
        raw=json.dumps(fixture()).replace('"schema": 51','"schema": 51,"schema": 51',1).encode()
        with self.assertRaises(ValueError):text.decode(raw,token=fixture()['token'],simulator=fixture()['simulator'],
            bundle=fixture()['bundle'],legacy=dict(process=100,installedMs=2,capturedMs=21))

    def test_control_must_fail_at_exporter_not_previous_original_helper(self):
        device=dict(deviceId=fixture()['simulator'],architecture='arm64',platform='iOS Simulator',modelName='iPhone 17 Pro',osVersion='26.2')
        summary=dict(totalTestCount=1,passedTests=0,failedTests=1,skippedTests=0,expectedFailures=0,result='Failed',
            devicesAndConfigurations=[dict(device=device)],testFailures=[dict(testIdentifierString=text.CASE,failureText=text.FAILURE)])
        tests=dict(devices=[device],testPlanConfigurations=[{}],testNodes=[dict(nodeType='Test Case',nodeIdentifier=text.CASE,
                                                                            result='Failed',durationInSeconds=100)])
        self.assertEqual(text.verify_results(summary,tests,device['deviceId'],65),{text.CASE:'Failed'})
        for edit in (lambda s:s.update(skippedTests=1),lambda s:s['testFailures'][0].update(failureText='Public pre-tap failed'),
                     lambda s:s['testFailures'].append(s['testFailures'][0]),lambda s:s.update(expectedFailures=1)):
            value=copy.deepcopy(summary);edit(value)
            with self.assertRaises(ValueError):text.verify_results(value,tests,device['deviceId'],65,require_exporter=True)
        with self.assertRaises(ValueError):text.verify_results(summary,tests,device['deviceId'],0)

    def test_external_profile_dispatch_restored_on_failure_and_no_old_success_remapping(self):
        profiles, select = run_pin51.prior.PROFILES, run_pin51.prior.select_profile
        with patch.object(run_pin51.sys,'argv',['run','--profile',text.PROFILE]),patch.object(run_pin51.run_pin03,'main',side_effect=RuntimeError('actual failure')):
            with self.assertRaises(RuntimeError):run_pin51.main()
        self.assertEqual(run_pin51.prior.PROFILES,profiles)
        self.assertIs(run_pin51.prior.select_profile,select)
        self.assertEqual(run_pin51.run_pin03.completed_exit_code(text.RESULT),1)

    def test_source_product_actual_failure_custody_cleanup_and_no_retry_required(self):
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve();state=evidence(out)
            self.assertEqual(text.accept_native_export(out,state),text.RESULT)
            for change in (lambda s:s.update(source='0'*40),lambda s:s['arms'][0].update(cleanup='FAIL'),
                lambda s:s['arms'][0].update(companion_captures=[]),lambda s:s['arms'][0].update(captures=[]),
                lambda s:s['commands'].append(s['commands'][0]),
                lambda s:s['build_profile'].update(test_timeout_seconds=1000)):
                value=copy.deepcopy(state);change(value)
                with self.subTest(change=change),self.assertRaises(ValueError):text.accept_native_export(out,value)
            path=next((out/'compose').glob('native-text-*.json'));v=text.load(path)
            v['delivery']['rows'].pop();path.write_text(json.dumps(v))
            with self.assertRaises(ValueError):text.accept_native_export(out,state)

    def test_safe_incomplete_companion_retained_but_never_qualified(self):
        with tempfile.TemporaryDirectory() as temporary:
            root=Path(temporary).resolve();sandbox=root/'sandbox';dest=root/'dest';dest.mkdir()
            v=fixture();v['delivery'].update(installed=False,installation=[1,1,0],inheritedResponderUntouched=False,rows=[])
            v['runloop'].update(capturedMs=18,longestActiveIntervalsMs=[],longestWaitingIntervalsMs=[],largestActiveMs=[],
                largestWaitingMs=[],completedActiveIntervals=0,completedWaitingIntervals=0,partialActiveMs=[])
            raw=json.dumps(v).encode();path=sandbox/'tmp'/('parlor-pin51-'+v['token'])/'delivery.json'
            path.parent.mkdir(parents=True);path.write_bytes(raw)
            arguments=dict(token=v['token'],simulator=v['simulator'],bundle=v['bundle'],legacy=dict(process=100,installedMs=2,capturedMs=21))
            self.assertFalse(text.retain_companion(sandbox,dest,**arguments)['complete'])
            self.assertEqual((dest/('native-text-'+v['token']+'.json')).read_bytes(),raw)
            with self.assertRaises(ValueError):text.decode(raw,**arguments,require_complete=True)

    @unittest.skipUnless(platform.system()=='Darwin','Actual Apple SDK unavailable on Linux')
    def test_actual_owned_objc_abi_and_simulator_typecheck(self):
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve();source=out/'main.swift'
            source.write_text(text.template('PIN51OwnedTextDelivery.swift.in')+CONTROL)
            binary=out/'control'
            compile=subprocess.run(['/usr/bin/xcrun','swiftc','-D','PIN51_CONTROL','-module-cache-path',str(out/'cache'),
                str(source),'-o',str(binary)],capture_output=True,text=True,timeout=180)
            self.assertEqual(compile.returncode,0,compile.stdout+compile.stderr)
            execution=subprocess.run([str(binary)],capture_output=True,text=True,timeout=30)
            self.assertEqual(execution.returncode,0,execution.stdout+execution.stderr)
            self.assertIn('ACTUAL_OWNED_TEXT_ABI_ARGUMENT_INSTANCE_PARENT_BOUNDING_PASS_NOT_APP_QUALIFICATION',execution.stdout)
            sdk=subprocess.check_output(['/usr/bin/xcrun','--sdk','iphonesimulator','--show-sdk-path'],text=True,timeout=45).strip()
            standalone=out/'observer.swift';standalone.write_text('\n'.join(text.template(n) for n in text.PINS))
            check=subprocess.run(['/usr/bin/xcrun','swiftc','-typecheck','-D','DEBUG','-sdk',sdk,
                '-target','arm64-apple-ios16.0-simulator','-module-cache-path',str(out/'sim-cache'),str(standalone)],
                capture_output=True,text=True,timeout=180)
            self.assertEqual(check.returncode,0,check.stdout+check.stderr)


if __name__=='__main__':unittest.main()
