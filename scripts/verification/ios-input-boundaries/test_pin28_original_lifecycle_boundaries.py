"""Synthetic software controls are not historical application/native evidence."""
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
import pin28_original_lifecycle_boundaries as original
import run_pin03
import test_pin27_attached_input_delivery as previous


def plan():
    return original.renderer_for_profile(original.PROFILE,original.SOURCE,original.motion.pair.RUNTIME,
        original.motion.pair.DEVICE)({p:original.motion.pair.fixture(p) for p in original.motion.pair.MODIFIED},
        original.motion.pair.INPUTS,arm='compose')


def fixture():
    v=previous.fixture()
    return {k:v[k] for k in ('schema','token','simulator','bundle','process','capturedMs','installed','overflow')} | dict(
        rows=[[1,1],[2,5]],state=[0,0,1])


def intake(v):
    f=fixture()
    return original.decode_lifecycle(json.dumps(v).encode(),token=f['token'],simulator=f['simulator'],
        bundle=f['bundle'],legacy=dict(process=100,capturedMs=20))


def evidence(out):
    state=previous.evidence(out);state['profile']=original.PROFILE;dest=out/'compose';row=state['arms'][0]
    methods=dict(zip(original.CASES,('Failed','Passed')));row['methods']=methods;row['attachment_test_ids']=[original.CASES[0]]
    summary=json.loads((dest/'summary.json').read_bytes());tests=json.loads((dest/'tests.json').read_bytes())
    summary.update(totalTestCount=2,passedTests=1,failedTests=1)
    summary['testFailures'][0]['testIdentifierString']=original.CASES[0]
    node=next(n for n in tests['testNodes'] if n.get('nodeType')=='Test Case')
    node.update(nodeIdentifier=original.CASES[0]);tests['testNodes'].append(node|dict(nodeIdentifier=original.CASES[1],result='Passed'))
    (dest/'summary.json').write_text(json.dumps(summary));(dest/'tests.json').write_text(json.dumps(tests))
    (dest/'inputs.json').write_text(json.dumps(state['original_inputs']|{p:hashlib.sha256(v.encode()).hexdigest() for p,v in plan().items()}))
    command=next(c for c in state['commands'] if c['label']=='compose-tests')
    command['command']=original.test_command(['xcodebuild'],dest/'tests.xcresult')
    public=next(dest.glob('public-text-*.json'));value=json.loads(public.read_bytes());value['syntheticControl']=False;public.write_text(json.dumps(value))
    legacy=json.loads(next(dest.glob('capture-*.json')).read_bytes());value=fixture()
    value.update(token=legacy['token'],simulator=row['simulator'],process=legacy['process'],capturedMs=legacy['capturedMs'])
    (dest/('public-lifecycle-'+legacy['token']+'.json')).write_text(json.dumps(value))
    return state


class OriginalLifecycleControls(unittest.TestCase):
    def test_original_actions_queries_assertions_and_adapter_stay_exact(self):
        before=previous.plan();after=plan()
        self.assertEqual(set(before),set(after))
        for p in before:
            if p!=pin02_capture.CONTAINER:self.assertEqual(before[p],after[p])
        self.assertEqual(after[pin02_capture.CONTAINER].removesuffix('\n'+original.template()).replace(
            original.PROPERTY,'').replace(original.INSTALL,'').replace(original.EXPORT,''),before[pin02_capture.CONTAINER])
        self.assertEqual(original.test_command(['xcodebuild'],Path('/r')),run_pin03.test_command(['xcodebuild'],Path('/r')))
        self.assertNotIn(original.text.CONTROL,original.CASES)
        self.assertEqual(run_pin03.completed_exit_code(original.RESULT),0)
        self.assertEqual(run_pin03.completed_exit_code('APP_QUALIFIED'),1)
        with self.assertRaises(ValueError):run_pin03.main(original_input_lifecycle=True,attached_input_delivery=True)

    def test_original_native_events_no_active_queries_polling_content_or_input(self):
        s=original.template()
        for value in ('userInfo','.object','Timer(','DispatchQueue','Task {','accessibilityValue','accessibilityLabel','.tap(','.typeText('):
            self.assertNotIn(value,s)
        self.assertEqual(s.count('let originalState = state()'),1)
        self.assertLess(s.index('attempted = true'),s.index('let originalState = state()'))
        self.assertIn('queue: nil',s)
        self.assertLess(s.index('guard Thread.isMainThread else'),s.index('guard self.armed() else'))
        self.assertIn('removeObserver($0)',s)
        self.assertIn('O_EXCL | O_NOFOLLOW',s)

    def test_lifecycle_shape_privacy_identity_clock_and_bounds(self):
        self.assertTrue(intake(fixture())['complete'])
        for change in (lambda v:v.update(private='forbidden'),lambda v:v.update(process=True),lambda v:v.update(token='foreign'),
            lambda v:v.update(capturedMs=21),lambda v:v.update(rows=v['rows']*129),lambda v:v.update(state=[0,0,True]),
            lambda v:v.update(state=[0,3,1]),lambda v:v['rows'][0].__setitem__(0,float('nan')),
            lambda v:v['rows'][0].__setitem__(1,True),lambda v:v['rows'][1].__setitem__(0,0)):
            v=fixture();change(v)
            with self.subTest(change=change),self.assertRaises(ValueError):intake(v)
        v=fixture();v.update(installed=False,rows=[]);self.assertFalse(intake(v)['complete'])
        v=fixture();v.update(overflow=1);self.assertFalse(intake(v)['complete'])

    def test_all_old_gates_original_methods_and_new_companions_mandatory(self):
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve();state=evidence(out)
            self.assertEqual(original.accept_native_export(out,state),original.RESULT)
            for change in (lambda v:v['arms'][0].update(cleanup='FAIL'),lambda v:v.update(source='foreign'),
                lambda v:next(c for c in v['commands'] if c['label']=='compose-tests').update(timeout_seconds=901),
                lambda v:next(c for c in v['commands'] if c['label']=='compose-tests')['command'].append('-retry-tests-on-failure')):
                v=copy.deepcopy(state);change(v)
                with self.subTest(change=change),self.assertRaises(ValueError):original.accept_native_export(out,v)
            p=next((out/'compose').glob('public-lifecycle-*.json'));raw=p.read_bytes()
            v=json.loads(raw);v['installed']=False;p.write_text(json.dumps(v))
            with self.assertRaises(ValueError):original.accept_native_export(out,state)
            p.write_bytes(raw);p.unlink()
            with self.assertRaises((ValueError,OSError)):original.accept_native_export(out,state)

    def test_successful_original_methods_have_no_failure_exports_and_skips_reject(self):
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve();state=evidence(out);dest=out/'compose';row=state['arms'][0]
            row.update(test_exit=0,result='JOURNEYS_PASSED',methods=dict.fromkeys(original.CASES,'Passed'),attachment_test_ids=[])
            for key in ('captures','companion_captures','root_companion_captures','motion_companion_captures'):row[key]=[]
            next(c for c in state['commands'] if c['label']=='compose-tests')['exit_code']=0
            summary=json.loads((dest/'summary.json').read_bytes());summary.update(result='Passed',passedTests=2,failedTests=0,testFailures=[])
            tests=json.loads((dest/'tests.json').read_bytes())
            for node in tests['testNodes']:node['result']='Passed'
            (dest/'summary.json').write_text(json.dumps(summary));(dest/'tests.json').write_text(json.dumps(tests))
            for prefix in ('capture-','native-accessibility-','native-root-','native-frame-','catalog-motion-','public-text-',
                'native-input-','input-attachment-','public-lifecycle-'):
                for p in dest.glob(prefix+'*.json'):p.unlink()
            self.assertEqual(original.accept_native_export(out,state),original.RESULT)
            summary.update(skippedTests=1,passedTests=1);(dest/'summary.json').write_text(json.dumps(summary))
            with self.assertRaises(ValueError):original.accept_native_export(out,state)

    def test_pre_name_failure_original_zero_rows_never_fabricated_into_input_delivery(self):
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve();state=evidence(out);dest=out/'compose'
            for prefix in ('public-text-','native-input-','input-attachment-'):
                p=next(dest.glob(prefix+'*.json'));v=json.loads(p.read_bytes())
                if prefix=='public-text-':v['text']['rows']=[]
                elif prefix=='native-input-':v.update(rows=[],installed=False,installation=[0,0,0])
                else:v['rows']=[]
                p.write_text(json.dumps(v))
            self.assertEqual(original.accept_native_export(out,state),original.RESULT)
            p=next(dest.glob('public-text-*.json'));raw=p.read_bytes();v=json.loads(raw);v['text']['schema']=True;p.write_text(json.dumps(v))
            with self.assertRaises(ValueError):original.accept_native_export(out,state)
            p.write_bytes(raw);v=json.loads(raw);v['text']['rows']=[[1,1,0,0,0,0,0,0,0,0]];p.write_text(json.dumps(v))
            with self.assertRaises(ValueError):original.accept_native_export(out,state)

    def test_actual_owned_companions_retained_once_and_bounded(self):
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve();state=evidence(out);dest=out/'compose';sandbox=out/'sandbox';retained=out/'retained';retained.mkdir()
            legacy=json.loads(next(dest.glob('capture-*.json')).read_bytes());token=legacy['token']
            for directory,name,prefix in [('parlor-pin23-','text.json','public-text-'),('parlor-pin25-','delivery.json','native-input-'),
                ('parlor-pin27-','attachment.json','input-attachment-'),('parlor-pin28-','lifecycle.json','public-lifecycle-')]:
                p=sandbox/'tmp'/(directory+token)/name;p.parent.mkdir(parents=True);p.write_bytes((dest/(prefix+token+'.json')).read_bytes())
            with patch.object(original.frame,'retain_companion',return_value={'previous':'unchanged'}):
                args=(sandbox,retained,token,state['arms'][0]['simulator'],state['debug_bundle'],legacy)
                self.assertEqual(original.retain_companion(*args),{'previous':'unchanged'})
                self.assertEqual(len(list(retained.glob('*.json'))),4)
                with self.assertRaises(FileExistsError):original.retain_companion(*args)
            p.write_bytes(b'x'*(original.MAX_BYTES+1))
            with self.assertRaises(ValueError):run_pin03.read_capture(p,maximum=original.MAX_BYTES)

    @unittest.skipUnless(platform.system()=='Darwin','Actual Apple runtime/SDK unavailable on Linux')
    def test_actual_notification_forwarding_scope_once_bounds_and_sdk(self):
        control='''
var armed = true
var stateReads = 0
var originalNotifications = 0
let names = (1...8).map { Notification.Name("PIN28-control-" + String($0)) }
let originalToken = NotificationCenter.default.addObserver(forName: names[0], object: nil, queue: nil) { _ in originalNotifications += 1 }
let observer = PIN28Lifecycle(armed: { armed }, state: { stateReads += 1; return [0,0,1] })
precondition(observer.installVerifiedNames(names))
precondition(!observer.installVerifiedNames(names))
NotificationCenter.default.post(name: names[0], object: nil)
armed = false
NotificationCenter.default.post(name: names[0], object: nil)
armed = true
NotificationCenter.default.post(name: names[0], object: nil)
precondition(originalNotifications == 3 && stateReads == 0)
precondition((observer.controlSnapshot()["rows"] as! [[Double]]).count == 2)
for _ in 0..<127 { NotificationCenter.default.post(name: names[1], object: nil) }
precondition((observer.controlSnapshot()["rows"] as! [[Double]]).count == 128)
precondition((observer.controlSnapshot()["overflow"] as! Int) == 1)
NotificationCenter.default.removeObserver(originalToken)
print("ACTUAL_APPLE_ORIGINAL_NOTIFICATION_SCOPE_ONCE_PASS_NOT_APP_QUALIFICATION")
'''
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve();source=out/'main.swift';source.write_text(original.template()+control);binary=out/'control'
            result=subprocess.run(['/usr/bin/xcrun','swiftc','-D','PIN28_CONTROL','-module-cache-path',str(out/'cache'),str(source),'-o',str(binary)],
                capture_output=True,text=True,timeout=180)
            self.assertEqual(result.returncode,0,result.stdout+result.stderr)
            result=subprocess.run([str(binary)],capture_output=True,text=True,timeout=30)
            self.assertEqual(result.returncode,0,result.stdout+result.stderr)
            self.assertIn('ACTUAL_APPLE_ORIGINAL_NOTIFICATION_SCOPE_ONCE_PASS_NOT_APP_QUALIFICATION',result.stdout)
            sdk=subprocess.check_output(['/usr/bin/xcrun','--sdk','iphonesimulator','--show-sdk-path'],text=True,timeout=45).strip()
            source=out/'lifecycle.swift';source.write_text(original.template())
            result=subprocess.run(['/usr/bin/xcrun','swiftc','-typecheck','-D','DEBUG','-sdk',sdk,'-target','arm64-apple-ios18.5-simulator',
                '-module-cache-path',str(out/'sim-cache'),str(source)],capture_output=True,text=True,timeout=180)
            self.assertEqual(result.returncode,0,result.stdout+result.stderr)


if __name__=='__main__':unittest.main()
