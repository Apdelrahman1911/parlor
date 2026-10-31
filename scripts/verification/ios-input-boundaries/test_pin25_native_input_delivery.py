"""Portable intake controls plus explicitly gated actual Apple ABI controls."""
import copy
import hashlib
import json
from pathlib import Path
import platform
import subprocess
import tempfile
import unittest

import pin01_copy
import pin02_capture
import pin25_native_input_delivery as delivery
import run_pin03
import test_pin23_text_session_boundary as previous


def plan():
    return delivery.renderer_for_profile(delivery.PROFILE,delivery.SOURCE,delivery.text.motion.pair.RUNTIME,
        delivery.text.motion.pair.DEVICE)({p:delivery.text.motion.pair.fixture(p) for p in delivery.text.motion.pair.MODIFIED},
                                        delivery.text.motion.pair.INPUTS,arm='compose')


def fixture():
    p=previous.fixture()
    return dict(schema=1,token=p['token'],simulator=p['simulator'],bundle=p['bundle'],process=100,
        capturedMs=19,installed=True,installation=[50000,1,3],overflow=0,
        rows=[[1,5,0,0],[2,5,1,1],[3,1,0,0],[4,1,1,0]])


def decode(value):
    p=fixture()
    return delivery.decode_delivery(json.dumps(value).encode(),token=p['token'],simulator=p['simulator'],
        bundle=p['bundle'],legacy=dict(process=100,capturedMs=20))


def evidence(out):
    state=previous.evidence(out);state['profile']=delivery.PROFILE;dest=out/'compose'
    (dest/'inputs.json').write_text(json.dumps(state['original_inputs']|
        {p:hashlib.sha256(v.encode()).hexdigest() for p,v in plan().items()}))
    legacy=json.loads(next(dest.glob('capture-*.json')).read_bytes())
    value=fixture();value.update(token=legacy['token'],simulator=state['arms'][0]['simulator'],
        process=legacy['process'],capturedMs=legacy['capturedMs'])
    (dest/('native-input-'+legacy['token']+'.json')).write_text(json.dumps(value))
    return state


class NativeInputDeliveryControls(unittest.TestCase):
    def test_new_boundary_preserves_all_original_app_actions_assertions_and_text_forwarding(self):
        before=previous.plan();after=plan()
        self.assertEqual(set(before),set(after))
        for p in before:
            if p not in (pin01_copy.MAIN,pin02_capture.CONTAINER):self.assertEqual(before[p],after[p])
        self.assertEqual(after[pin01_copy.MAIN].removesuffix(delivery.BRIDGE),before[pin01_copy.MAIN])
        self.assertEqual(after[pin02_capture.CONTAINER].removesuffix('\n'+delivery.template())
            .replace(delivery.PROPERTY,'').replace(delivery.INSTALL,'').replace(delivery.EXPORT,''),
            before[pin02_capture.CONTAINER])
        self.assertEqual(delivery.CASES,delivery.text.CASES)
        self.assertEqual(delivery.test_command(['xcodebuild'],Path('/owned/result')),
                         delivery.text.test_command(['xcodebuild'],Path('/owned/result')))
        self.assertEqual(run_pin03.completed_exit_code(delivery.RESULT),0)
        self.assertEqual(run_pin03.completed_exit_code('NATIVE_INPUT_QUALIFIED'),1)
        with self.assertRaises(ValueError):run_pin03.main(native_input_delivery=True,text_boundary=True)

    def test_native_observation_never_reads_retains_or_exports_input_arguments(self):
        source=delivery.template()
        self.assertEqual(hashlib.sha256(source.encode()).hexdigest(),delivery.TEMPLATE_SHA256)
        for forbidden in ('accessibilityLabel','accessibilityValue','accessibilityIdentifier','textInRange',
                          'UIAccessibilityPostNotification','Timer(','.tap()', 'text.count', 'text.length'):
            self.assertNotIn(forbidden,source)
        self.assertEqual(source.count('insert(object, selectors[0], text)'),1)
        self.assertEqual(source.count('replace(object, selectors[1], range, text)'),1)
        self.assertEqual(source.count('marked(object, selectors[2], text, range)'),1)
        self.assertIn('guard Thread.isMainThread, armed()',source)
        self.assertIn('O_EXCL | O_NOFOLLOW',source)
        self.assertIn('class_conformsToProtocol(validated, inputProtocol)',source)
        self.assertIn('guard cocoa && view',source)
        self.assertIn('String(cString: name(parent)) == "CMPEditMenuView"',source)

    def test_only_complete_bounded_original_call_bits_are_accepted(self):
        self.assertEqual(decode(fixture())['mutation_entries'],1)
        empty=fixture();empty['rows']=[]
        self.assertEqual(decode(empty)['rows'],0) # No arrivals can be real evidence, not a forced pass.
        for change in (lambda v:v.update(private='forbidden'),lambda v:v.update(schema=True),
            lambda v:v.update(process=True),lambda v:v.update(token='foreign'),lambda v:v.update(capturedMs=21),
            lambda v:v.update(installed=False),lambda v:v.update(overflow=1),
            lambda v:v.update(installation=[50000,2,3]),lambda v:v.update(installation=[50000,1,2]),
            lambda v:v.update(installation=[True,1,3]),lambda v:v.update(rows=v['rows']*(delivery.MAX_ROWS+1)),
            lambda v:v['rows'][0].__setitem__(0,float('nan')),lambda v:v['rows'][0].__setitem__(1,True),
            lambda v:v['rows'][0].__setitem__(1,7),lambda v:v['rows'][0].__setitem__(2,2),
            lambda v:v['rows'][0].__setitem__(3,1),lambda v:v['rows'][-1].__setitem__(0,1),
            lambda v:v['rows'][-1].__setitem__(3,1)):
            value=fixture();change(value)
            with self.subTest(change=change),self.assertRaises(ValueError):decode(value)
        raw=json.dumps(fixture()).replace('"schema": 1','"schema": 1,"schema": 1').encode()
        with self.assertRaises(ValueError):delivery.decode_delivery(raw,token=fixture()['token'],simulator=fixture()['simulator'],
            bundle=fixture()['bundle'],legacy=dict(process=100,capturedMs=20))

    def test_original_source_cleanup_and_control_actual_delivery_are_still_required(self):
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve();state=evidence(out)
            self.assertEqual(delivery.accept_native_export(out,state),delivery.RESULT)
            for change in (lambda s:s['arms'][0].update(cleanup='FAIL'),lambda s:s['arms'][0].update(captures=[]),
                           lambda s:s['arms'][0].update(companion_captures=[])):
                value=copy.deepcopy(state);change(value)
                with self.assertRaises(ValueError):delivery.accept_native_export(out,value)
            p=next((out/'compose').glob('native-input-*.json'));original=p.read_bytes()
            value=json.loads(original);value['rows']=[];p.write_text(json.dumps(value))
            with self.assertRaises(ValueError):delivery.accept_native_export(out,state)
            p.write_bytes(original);p.unlink()
            with self.assertRaises((ValueError,OSError)):delivery.accept_native_export(out,state)

    @unittest.skipUnless(platform.system()=='Darwin','Actual Objective-C/Apple SDK unavailable on Linux')
    def test_actual_objc_mutation_struct_nil_forwarding_and_parent_isolation(self):
        control='''
import Foundation
class PIN25Parent: NSObject {
    static var becomeCalls = 0
    static var resignCalls = 0
    @objc dynamic func becomeFirstResponder() -> Bool { PIN25Parent.becomeCalls += 1; return true }
    @objc dynamic func resignFirstResponder() -> Bool { PIN25Parent.resignCalls += 1; return false }
}
let input = NSString(string: "synthetic public control")
let rangeObject = NSObject()
let selected = NSRange(location: 3, length: 9)
class PIN25Leaf: PIN25Parent {
    var insertCalls = 0
    var replaceCalls = 0
    var markedCalls = 0
    var deleteCalls = 0
    @objc(insertText:) dynamic func insertText(_ text: AnyObject) {
        precondition(text === input); insertCalls += 1
    }
    @objc(replaceRange:withText:) dynamic func replaceRange(_ range: AnyObject, withText text: AnyObject) {
        precondition(range === rangeObject && text === input); replaceCalls += 1
    }
    @objc(setMarkedText:selectedRange:) dynamic func setMarkedText(_ text: AnyObject?, selectedRange range: NSRange) {
        precondition(range == selected && (text == nil || text === input)); markedCalls += 1
    }
    @objc dynamic func deleteBackward() { deleteCalls += 1 }
}
var armed = true
let observer = PIN25NativeInputDelivery(armed: { armed })
precondition(observer.installVerifiedTarget(PIN25Leaf.self))
let leaf = PIN25Leaf()
precondition(leaf.becomeFirstResponder())
leaf.insertText(input)
leaf.replaceRange(rangeObject, withText: input)
leaf.setMarkedText(input, selectedRange: selected)
leaf.setMarkedText(nil, selectedRange: selected)
leaf.deleteBackward()
precondition(!leaf.resignFirstResponder())
let rows = observer.controlSnapshot()["rows"] as! [[Double]]
precondition(rows.count == 14 && leaf.insertCalls == 1 && leaf.replaceCalls == 1 &&
             leaf.markedCalls == 2 && leaf.deleteCalls == 1)
precondition(rows[1][1...] == [5,1,1] && rows.last![1...] == [6,1,0])
precondition(PIN25Parent().becomeFirstResponder() && !PIN25Parent().resignFirstResponder())
precondition((observer.controlSnapshot()["rows"] as! [[Double]]).count == 14)
armed = false
leaf.insertText(input)
precondition(leaf.insertCalls == 2 && (observer.controlSnapshot()["rows"] as! [[Double]]).count == 14)
precondition(!observer.installVerifiedTarget(PIN25Leaf.self))
precondition((observer.controlSnapshot()["overflow"] as! Int) == 0)
print("ACTUAL_APPLE_INPUT_ABI_FORWARDING_AND_PARENT_ISOLATION_PASS_NOT_APP_QUALIFICATION")
'''
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve();source=out/'main.swift';source.write_text(delivery.template()+control)
            binary=out/'control'
            compilation=subprocess.run(['/usr/bin/xcrun','swiftc','-D','PIN25_CONTROL','-module-cache-path',str(out/'cache'),
                str(source),'-o',str(binary)],capture_output=True,text=True,timeout=180)
            self.assertEqual(compilation.returncode,0,compilation.stdout+compilation.stderr)
            result=subprocess.run([str(binary)],capture_output=True,text=True,timeout=30)
            self.assertEqual(result.returncode,0,result.stdout+result.stderr)
            self.assertIn('ACTUAL_APPLE_INPUT_ABI_FORWARDING_AND_PARENT_ISOLATION_PASS_NOT_APP_QUALIFICATION',result.stdout)
            sdk=subprocess.check_output(['/usr/bin/xcrun','--sdk','iphonesimulator','--show-sdk-path'],text=True,timeout=45).strip()
            standalone=out/'delivery.swift';standalone.write_text(delivery.template())
            typecheck=subprocess.run(['/usr/bin/xcrun','swiftc','-typecheck','-D','DEBUG','-sdk',sdk,
                '-target','arm64-apple-ios18.5-simulator','-module-cache-path',str(out/'sim-cache'),str(standalone)],
                capture_output=True,text=True,timeout=180)
            self.assertEqual(typecheck.returncode,0,typecheck.stdout+typecheck.stderr)


if __name__=='__main__':unittest.main()
