"""New numeric focus intake and original forwarding controls; no fixture is runtime evidence."""
import copy
import hashlib
import json
from pathlib import Path
import platform
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import pin55_first_responder as p
import run_pin55
import test_pin53_root_lifecycle as previous
import test_pin54_original_root_pair as original


def plan():
    leaf=p.runtime.pair_control.leaf
    return p.renderer_for_profile(p.PROFILE,p.SOURCE,p.RUNTIME,p.DEVICE)(
        {s:leaf.pair.fixture(s) for s in leaf.pair.MODIFIED},leaf.pair.INPUTS,arm='compose')


def sentinel():
    return p.base.SENTINEL.replace('PIN51Configure','PIN55Configure').replace('testPIN51OwnedTextExportCapability',
        p.CONTROL_CASE.split('/')[1].removesuffix('()')).replace(p.base.FAILURE,p.FAILURE).replace('        name.typeText("\\n")\n','')


def evidence(out,failed=()):
    state=previous.evidence(out);state['profile']=p.PROFILE;d=out/'compose';row=state['arms'][0]
    methods={c:'Failed' if c==p.CONTROL_CASE or c in failed else 'Passed' for c in p.CASES}
    row.update(methods=methods,attachment_test_ids=sorted(c for c,v in methods.items() if v=='Failed'))
    summary=p.load(d/'summary.json');tests=p.load(d/'tests.json')
    summary.update(totalTestCount=3,passedTests=2-len(failed),failedTests=1+len(failed),testFailures=[
        dict(testIdentifierString=c,failureText=p.FAILURE if c==p.CONTROL_CASE else 'Original full value failed')
        for c,v in methods.items() if v=='Failed'])
    tests['testNodes']=[dict(nodeType='Test Case',nodeIdentifier=c,result=v,durationInSeconds=100) for c,v in methods.items()]
    for name,v in (('summary',summary),('tests',tests)):(d/(name+'.json')).write_text(json.dumps(v))
    (d/'inputs.json').write_text(json.dumps(state['original_inputs']|{s:hashlib.sha256(v.encode()).hexdigest() for s,v in plan().items()}))
    call=next(c for c in state['commands'] if c['label']=='compose-tests')
    command=call['command'];call['command']=p.test_command(command[:command.index('test-without-building')],d/'tests.xcresult')
    (d/'test.log').write_text(''.join("Test Case '-[iosAppUITests."+c.split('/')[0]+' '+c.split('/')[1].removesuffix('()')+"]' started.\n" for c in p.CASES))
    prototype=p.load(next(d.glob('native-text-*.json')));legacy=p.load(next(d.glob('capture-*.json')))
    for path in d.glob('native-text-*.json'):path.unlink()
    for path in d.glob('capture-*.json'):path.unlink()
    import shutil
    shutil.rmtree(d/'attachments')
    row['captures']=[];row['companion_captures']=[]
    for i,c in enumerate(sorted(k for k,v in methods.items() if v=='Failed'),1):
        token=f'00000000-0000-0000-0000-{i:012d}';raw=copy.deepcopy(prototype);env=copy.deepcopy(legacy)
        raw.update(schema=55,token=token);env['token']=token
        raw['focus']=dict(schema=55,attempted=True,installed=True,installedMs=3,parentUntouched=True,leafHookIntact=True,
            editorsAlive=[True] if c==p.CONTROL_CASE else [False],invalid=0,overflow=0,
            rows=[[5,0,1,0],[6,1,1,1]] if c==p.CONTROL_CASE else [],capturedMs=499)
        req=d/'attachments'/('failed-%02d'%i)/'request.json';req.parent.mkdir(parents=True)
        req.write_text(json.dumps(dict(schema=1,token=token,ackObserved=True,originalTransitionFailed=True)))
        bytes_=json.dumps(env).encode();(d/('capture-'+token+'.json')).write_bytes(bytes_)
        row['captures'].append(p.base.run_pin03.decode_capture(bytes_,{token},row['simulator'],state['debug_bundle']))
        bytes_=json.dumps(raw).encode();(d/('native-text-'+token+'.json')).write_bytes(bytes_)
        row['companion_captures'].append(p.decode(bytes_,token=token,simulator=row['simulator'],bundle=state['debug_bundle'],legacy=env))
    return state


CONTROL=r'''
import Foundation
class Parent: NSObject {
    var calls = 0
    var result = false
    @objc dynamic func becomeFirstResponder() -> Bool { calls += 1; return result }
}
class Leaf: Parent {}
class BadParent: NSObject { @objc dynamic func becomeFirstResponder() -> Int { 7 } }
class BadLeaf: BadParent {}
class OwnedLeaf: Parent { @objc override dynamic func becomeFirstResponder() -> Bool { true } }
var armed = true
let observer = PIN55FirstResponderObservation(armed: { armed })
var first: Leaf? = Leaf()
let selector = NSSelectorFromString("becomeFirstResponder")
let originalParent = method_getImplementation(class_getInstanceMethod(Parent.self,selector)!)
observer.bindVerifiedEditor(first!,target: Leaf.self)
precondition(first!.calls == 0) // Installation must never prime or repair focus.
precondition(first!.becomeFirstResponder() == false && first!.calls == 1)
first!.result = true
precondition(first!.becomeFirstResponder() == true && first!.calls == 2)
let unrelated = Leaf(); precondition(!unrelated.becomeFirstResponder() && unrelated.calls == 1)
precondition((observer.freeze()["rows"] as! [[Double]]).count == 4)
let second = Leaf(); second.result = true
observer.bindVerifiedEditor(second,target: Leaf.self)
precondition(second.becomeFirstResponder() && second.calls == 1)
precondition(unsafeBitCast(originalParent,to: UInt.self) == unsafeBitCast(method_getImplementation(class_getInstanceMethod(Parent.self,selector)!),to: UInt.self))
first = nil
let frozen = observer.freeze()
precondition(frozen["editorsAlive"] as! [Bool] == [false,true])
precondition(frozen["parentUntouched"] as! Bool && frozen["leafHookIntact"] as! Bool)
armed = false; precondition(second.becomeFirstResponder() && second.calls == 2)
precondition((observer.freeze()["rows"] as! [[Double]]).count == 6)
armed = true
for _ in 0..<4096 { precondition(second.becomeFirstResponder()) }
precondition((observer.freeze()["rows"] as! [[Double]]).count == 4096 && observer.freeze()["overflow"] as! Int > 0)
let bad = BadLeaf(); let negative = PIN55FirstResponderObservation(armed: { true })
negative.bindVerifiedEditor(bad,target: BadLeaf.self)
precondition(negative.freeze()["installed"] as! Bool == false && bad.becomeFirstResponder() == 7)
let own = OwnedLeaf(); let owned = PIN55FirstResponderObservation(armed: { true })
owned.bindVerifiedEditor(own,target: OwnedLeaf.self)
precondition(owned.freeze()["installed"] as! Bool == false && own.becomeFirstResponder())
print("ACTUAL_ORIGINAL_FOCUS_FALSE_TRUE_RETURN_NO_PRIMING_WEAK_INSTANCE_PARENT_ABI_BOUNDING_PASS")
'''


class FirstResponderControls(unittest.TestCase):
    def test_actual_natural_failure_prerequisite_and_new_source_hash_required(self):
        p.require_capability()
        with patch.object(p,'PREREQUISITE_SHA256','a'*64),self.assertRaises(ValueError):p.require_capability()
        with patch.object(p,'FOCUS_SHA256','a'*64),self.assertRaises(ValueError):p.focus_source()
        self.assertEqual(p.TEST_TIMEOUT_SECONDS,900)
        self.assertEqual(p.CASES[:-1],p.previous.CASES)

    def test_original_driver_helpers_and_mutator_calls_preserved(self):
        current=plan();old=original.plan();leaf=p.runtime.pair_control.leaf
        driver=current[p.pin02_capture.DRIVER].replace(sentinel(),'').replace('PIN55Configure','PIN53Configure').replace('PARLOR_PIN55_','PARLOR_PIN53_')
        self.assertEqual(driver.replace(p.PROFILE,p.previous.PROFILE),old[p.pin02_capture.DRIVER])
        self.assertNotIn('name.typeText("\\n")',sentinel())
        self.assertEqual(driver.count('name.typeText("\\n")'),1)
        source=p.delivery_source().replace('PIN55FocusTextDelivery','PIN51OwnedTextDelivery')
        source=source.replace('    var onOwnedEditor: ((AnyObject, AnyClass) -> Void)?\n','').replace(
            '        onOwnedEditor?(child, target) // After original delivery installation; before original attachment returns.\n','')
        self.assertEqual(source,p.base.template('PIN51OwnedTextDelivery.swift.in'))
        self.assertIn(p.root.root_source(),current[p.pin02_capture.CONTAINER])
        for path in current:
            if path not in p.pin02_capture.MODIFIED:self.assertEqual(current[path],leaf.pair.fixture(path))

    def test_no_focus_repair_queries_private_values_events_or_threads(self):
        source=p.focus_source()
        for forbidden in ('.becomeFirstResponder(','.isFirstResponder','accessibilityValue','accessibilityLabel','objc_copyClassList',
                'DispatchQueue','Timer(','addGestureRecognizer','selectedTextRange','setValue(','value(forKey'):
            self.assertNotIn(forbidden,source)
        self.assertEqual(source.count('call(object, originalSelector)'),1)
        self.assertNotIn('selector = self.selector',source)
        self.assertIn('originalSelector = self.selector',source)
        self.assertIn('class_addMethod(target, selector',source)
        self.assertNotIn('method_setImplementation',source)

    def test_real_exporter_and_all_original_outcomes_retained_without_application_qualification(self):
        for failed in ((),p.CASES[:1],p.CASES[:-1]):
            with self.subTest(failed=failed),tempfile.TemporaryDirectory() as tmp:
                out=Path(tmp).resolve();state=evidence(out,failed)
                self.assertEqual(p.accept_native_export(out,state),p.RESULT)
                receipt=p.load(out/'first-responder-boundary.json')
                self.assertEqual(len(receipt['native']),1+len(failed))
                for k in ('application_root_cause','shipping_qualified','physical_verified','native_timing_neutrality','root_queries_added'):
                    self.assertIs(receipt[k],False)
                self.assertIs(receipt['native'][0]['current_leaf_inherited_responder_untouched'],False)
                if failed:self.assertFalse(receipt['native'][1]['focus']['editorsAlive'][0])

    def test_incomplete_focus_control_private_data_bad_parent_return_pair_or_clock_reject(self):
        changes=(lambda v:v['focus'].update(installed=False,installedMs=None),lambda v:v['focus'].update(parentUntouched=False),
            lambda v:v['focus'].update(editorsAlive=[False]),lambda v:v['focus'].update(overflow=1),
            lambda v:v['focus'].update(label='private'),lambda v:v['focus'].update(capturedMs=501),
            lambda v:v['focus']['rows'].pop(),lambda v:v['focus']['rows'][1].__setitem__(3,0),
            lambda v:v['focus']['rows'][0].__setitem__(0,float('nan')))
        for change in changes:
            with tempfile.TemporaryDirectory() as tmp:
                out=Path(tmp).resolve();state=evidence(out);file=next((out/'compose').glob('native-text-*.json'));v=p.load(file);change(v);file.write_text(json.dumps(v))
                with self.assertRaises(ValueError):p.accept_native_export(out,state)

    def test_missing_or_duplicate_request_source_commands_budget_skips_cleanup_or_early_control_failure_reject(self):
        changes=(lambda o,s:s.update(source='0'*40),lambda o,s:s['arms'][0].update(cleanup='FAIL'),
            lambda o,s:s['build_profile'].update(test_timeout_seconds=901),lambda o,s:s['commands'].append(s['commands'][0]),
            lambda o,s:next((o/'compose').glob('native-text-*.json')).unlink(),
            lambda o,s:next(c for c in s['commands'] if c['label']=='compose-tests')['command'].append('-retry-tests-on-failure'))
        for change in changes:
            with tempfile.TemporaryDirectory() as tmp:
                out=Path(tmp).resolve();state=evidence(out);change(out,state)
                with self.assertRaises((ValueError,FileNotFoundError)):p.accept_native_export(out,state)
        with tempfile.TemporaryDirectory() as tmp:
            out=Path(tmp).resolve();state=evidence(out);summary=p.load(out/'compose/summary.json');tests=p.load(out/'compose/tests.json')
            summary['testFailures'][0]['failureText']='Original helper failed before exporter'
            with self.assertRaises(ValueError):p.verify_results(summary,tests,state['arms'][0]['simulator'],65)
            summary['skippedTests']=1
            with self.assertRaises(ValueError):p.verify_results(summary,tests,state['arms'][0]['simulator'],65)

    def test_observed_order_and_requested_order_each_required_no_capitalization_assumption(self):
        self.assertEqual(p.CASES, (*p.original.QUERY_CASES, p.CONTROL_CASE))
        with tempfile.TemporaryDirectory() as tmp:
            out=Path(tmp).resolve();state=evidence(out)
            self.assertEqual(p.accept_native_export(out,state),p.RESULT)
            (out/'first-responder-boundary.json').unlink()
            log=out/'compose/test.log';lines=log.read_text().splitlines(True)
            log.write_text(lines[-1]+''.join(lines[:-1]))
            with self.assertRaisesRegex(ValueError, 'Exact original Arabic, English'):
                p.accept_native_export(out,state)
        with tempfile.TemporaryDirectory() as tmp:
            out=Path(tmp).resolve();state=evidence(out)
            argv=next(c for c in state['commands'] if c['label']=='compose-tests')['command']
            flags=[a for a in argv if a.startswith('-only-testing:')]
            argv[:]=[a for a in argv if not a.startswith('-only-testing:')]+[flags[-1],*flags[:-1]]
            with self.assertRaisesRegex(ValueError, 'One exact control'):
                p.accept_native_export(out,state)

    def test_external_profile_dispatch_restores_and_never_maps_collection_to_shipping_success(self):
        profiles,select=run_pin55.prior.PROFILES,run_pin55.prior.select_profile
        with patch.object(run_pin55.sys,'argv',['run','--profile',p.PROFILE]),patch.object(run_pin55.run_pin03,'main',side_effect=RuntimeError('failure')):
            with self.assertRaises(RuntimeError):run_pin55.main()
        self.assertEqual(run_pin55.prior.PROFILES,profiles);self.assertIs(run_pin55.prior.select_profile,select)
        self.assertEqual(run_pin55.run_pin03.completed_exit_code(p.RESULT),1)

    @unittest.skipUnless(platform.system()=='Darwin','Actual Apple Objective-C ABI unavailable on Linux')
    def test_actual_apple_focus_abi_false_true_original_return_weak_lifetime_parent_and_bounding(self):
        with tempfile.TemporaryDirectory() as tmp:
            out=Path(tmp).resolve();source=out/'main.swift';source.write_text(p.focus_source()+CONTROL);binary=out/'control'
            r=subprocess.run(['/usr/bin/xcrun','swiftc','-D','PIN55_CONTROL','-module-cache-path',str(out/'cache'),str(source),'-o',str(binary)],capture_output=True,text=True,timeout=180)
            self.assertEqual(r.returncode,0,r.stdout+r.stderr)
            # Actual failed run41508753655: the earlier closure captured a later
            # local selector, not the instance property. Preserve negative compiler
            # reproduction instead of relying solely on a source-text assertion.
            source.write_text(p.focus_source().replace('originalSelector', 'selector')+CONTROL)
            negative=subprocess.run(['/usr/bin/xcrun','swiftc','-D','PIN55_CONTROL','-module-cache-path',str(out/'cache'),str(source),'-o',str(out/'negative')],capture_output=True,text=True,timeout=180)
            self.assertNotEqual(negative.returncode,0)
            self.assertIn("closure captures 'selector' before it is declared",negative.stdout+negative.stderr)
            r=subprocess.run([str(binary)],capture_output=True,text=True,timeout=30)
            self.assertEqual(r.returncode,0,r.stdout+r.stderr)
            self.assertIn('ACTUAL_ORIGINAL_FOCUS_FALSE_TRUE_RETURN_NO_PRIMING_WEAK_INSTANCE_PARENT_ABI_BOUNDING_PASS',r.stdout)

    @unittest.skipUnless(platform.system()=='Darwin','Actual Apple Simulator SDK unavailable on Linux')
    def test_actual_apple_new_full_wrapper_typecheck_and_three_case_driver_parse(self):
        with tempfile.TemporaryDirectory() as tmp:
            out=Path(tmp).resolve();sdk=subprocess.check_output(['/usr/bin/xcrun','--sdk','iphonesimulator','--show-sdk-path'],text=True,timeout=45).strip()
            file=out/'owner.swift';file.write_text(plan()[p.pin02_capture.CONTAINER])
            r=subprocess.run(['/usr/bin/xcrun','swiftc','-typecheck','-D','DEBUG','-sdk',sdk,'-target','arm64-apple-ios16.0-simulator','-module-cache-path',str(out/'cache'),str(file)],capture_output=True,text=True,timeout=180)
            self.assertEqual(r.returncode,0,r.stdout+r.stderr)
            file=out/'driver.swift';file.write_text(plan()[p.pin02_capture.DRIVER])
            r=subprocess.run(['/usr/bin/xcrun','swiftc','-frontend','-parse','-D','DEBUG','-target','arm64-apple-ios26.2-simulator',str(file)],capture_output=True,text=True,timeout=120)
            self.assertEqual(r.returncode,0,r.stdout+r.stderr)


if __name__=='__main__':unittest.main()
