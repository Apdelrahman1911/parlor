"""Portable intake controls, not actual native input or application qualification."""
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
import pin23_text_session_boundary as text
import test_pin22_native_frame_boundary as previous
import run_pin03


def plan():
    return text.renderer_for_profile(text.PROFILE,text.SOURCE,text.motion.pair.RUNTIME,text.motion.pair.DEVICE)(
        {p:text.motion.pair.fixture(p) for p in text.motion.pair.MODIFIED},text.motion.pair.INPUTS,arm='compose')


def fixture():
    old=previous.fixture()
    return dict(schema=1,token=old['token'],simulator=old['simulator'],bundle=old['bundle'],process=100,
        capturedMs=19,syntheticControl=True,text=dict(schema=5,nativeMode=1,overflow=0,invalid=0,rows=[
            [1,1,0,0,0,0,0,0,0,0], [2,1,2,1,0,0,0,0,0,0], [3,2,0,0,0,0,0,0,0,0],
            [4,3,0,0,0,0,0,0,0,0], [5,4,0,0,0,0,0,0,0,0], [6,4,1,0,0,0,0,0,0,0],
            [7,3,1,0,0,0,0,0,0,0], [8,1,2,0,0,0,0,0,0,0], [9,5,0,1,1,0,0,0,0,0]]))


def decode(value):
    p=fixture()
    return text.decode_text(json.dumps(value).encode(),token=p['token'],simulator=p['simulator'],bundle=p['bundle'],
        legacy=dict(process=100,capturedMs=20))


def evidence(out):
    s=previous.evidence(out);s['profile']=text.PROFILE;s['original_inputs']=s['original_inputs']|{text.NAME:text.NAME_SHA256}
    dest=out/'compose';row=s['arms'][0]
    (dest/'inputs.json').write_text(json.dumps(s['original_inputs']|{p:hashlib.sha256(v.encode()).hexdigest() for p,v in plan().items()}))
    row['methods']={text.NATURAL:'Passed',text.CONTROL:'Failed'};row['attachment_test_ids']=[text.CONTROL]
    for command in s['commands']:
        if command['label']=='compose-tests':command['command']=text.test_command(['xcodebuild'],Path('/owned/tests.xcresult'))
    summary=json.loads((dest/'summary.json').read_bytes());summary.update(totalTestCount=2,passedTests=1,failedTests=1,
        testFailures=[dict(testIdentifierString=text.CONTROL,failureText='The full typed value must reach the actual Compose field')])
    (dest/'summary.json').write_text(json.dumps(summary))
    tests=json.loads((dest/'tests.json').read_bytes());tests['testNodes']=[dict(nodeType='Test Case',nodeIdentifier=t,result=row['methods'][t]) for t in text.CASES]
    (dest/'tests.json').write_text(json.dumps(tests))
    legacy=json.loads(next(dest.glob('capture-*.json')).read_bytes())
    p=next(dest.glob('catalog-motion-*.json'));motion=json.loads(p.read_bytes());motion['motion']['syntheticControl']=False;p.write_text(json.dumps(motion))
    row['motion_companion_captures']=[text.motion.decode_motion(p.read_bytes(),token=legacy['token'],simulator=row['simulator'],bundle=s['debug_bundle'],legacy=legacy)]
    value=fixture();value.update(token=legacy['token'],simulator=row['simulator'],process=legacy['process'],capturedMs=legacy['capturedMs'])
    (dest/('public-text-'+legacy['token']+'.json')).write_text(json.dumps(value))
    line=next(i for i,s in enumerate(plan()[pin02_capture.DRIVER].splitlines(),1) if 'equals: "PIN23 isolated export sentinel"' in s)
    (dest/'test.log').write_text(f'IOSAppLaunchUITests.swift:{line}: error: -[iosAppUITests.IOSAppLaunchUITests testPIN23IsolatedPublicTextExportControl] : XCTAssertEqual failed: synthetic portable fixture\n')
    return s


class PublicTextSessionControls(unittest.TestCase):
    def test_exact_prepared_adapter_reused_and_original_arabic_journey_driver_unchanged(self):
        before=previous.plan();after=plan()
        for p in before:
            if p not in (pin01_copy.MAIN,pin02_capture.CONTAINER,pin02_capture.DRIVER):self.assertEqual(before[p],after[p])
        self.assertEqual(after[pin02_capture.DRIVER].replace(text.CONTROL_METHOD,''),before[pin02_capture.DRIVER])
        self.assertEqual(after[pin01_copy.MAIN].removesuffix(text.BRIDGE).replace(text.CONFIGURE,'')
            .replace('    com.parlor.app.inputcopy.PIN05ObserveInput { App() }\n','    App()\n'),before[pin01_copy.MAIN])
        name=after[text.NAME].replace('import androidx.compose.runtime.DisposableEffect\n','').replace('import androidx.compose.runtime.SideEffect\n','')
        name=name.replace('import com.parlor.app.inputcopy.PIN05Observation\n','').replace(text.MOUNT,'')
        name=name.replace(text.CALLBACK,'                            name = RoomInputPolicy.sanitizeDisplayNameInput(input)\n')
        self.assertEqual(name,text.name_fixture())
        for n,digest in text.TEMPLATES.items():self.assertEqual(hashlib.sha256(text.template(n).encode()).hexdigest(),digest)
        self.assertEqual(text.CASES,(text.NATURAL,text.CONTROL))
        self.assertLess(text.CONTROL_METHOD.index('pin11PublicApp = app'),text.CONTROL_METHOD.index('app.launch()'))
        self.assertIn('pin11PublicApp = nil\n            app.terminate()',text.CONTROL_METHOD)
        self.assertNotIn('testAdditionalGamesExposeOnlyMultiplayerSetupInEnglish',str(text.test_command(['xcodebuild'],Path('/owned/result'))))
        self.assertEqual(text.CONTROL_METHOD.count('name.typeText('),1)
        self.assertEqual(text.CONTROL_METHOD.count('equals: "Table Test"'),1)
        self.assertIn('equals: "PIN23 isolated export sentinel"',text.CONTROL_METHOD)
        with self.assertRaises(ValueError):run_pin03.main(text_boundary=True,frame_boundary=True)
        self.assertEqual(run_pin03.completed_exit_code(text.RESULT),0)
        self.assertEqual(run_pin03.completed_exit_code('PUBLIC_TEXT_QUALIFIED'),1)

    def test_template_preserves_once_synchronous_delivery_and_public_owner_scope(self):
        interceptor=text.template('PIN05TextInputInterceptor.kt.in')
        self.assertEqual(interceptor.count('request.onEditCommand(commands)'),1)
        self.assertIn('if (owner == null) next.startInputMethod(request)',interceptor)
        self.assertIn('finally {',interceptor)
        self.assertIn('private val rows = DoubleArray(128 * 10)',text.template('PIN05TextObservation.kt.in'))
        swift=text.template(text.SWIFT)
        for forbidden in ('accessibilityLabel','accessibilityValue','insertText','replaceRange','Timer(','.tap()'):
            self.assertNotIn(forbidden,swift)
        self.assertIn('Thread.isMainThread, !attempted',swift)
        self.assertIn('O_EXCL | O_NOFOLLOW',swift)
        self.assertIn('guard visited < 64',text.NATIVE_SNAPSHOT)
        self.assertNotIn('accessibility',text.NATIVE_SNAPSHOT)

    def test_private_nonfinite_foreign_overflow_or_unknown_numeric_evidence_rejects(self):
        self.assertTrue(decode(fixture())['complete'])
        for change in (lambda v:v.update(private='forbidden'),lambda v:v.update(schema=True),
            lambda v:v.update(process=True),lambda v:v.update(token='foreign'),lambda v:v.update(capturedMs=21),
            lambda v:v.update(syntheticControl=1),lambda v:v['text'].update(invalid=1),lambda v:v['text'].update(overflow=1),
            lambda v:v['text'].update(rows=[]),lambda v:v['text'].update(value='never'),
            lambda v:v['text']['rows'][0].__setitem__(0,float('nan')),
            lambda v:v['text']['rows'][0].__setitem__(5,1),lambda v:v['text']['rows'][-1].__setitem__(0,21)):
            value=fixture();change(value)
            with self.subTest(change=change),self.assertRaises(ValueError):decode(value)
        raw=json.dumps(fixture()).replace('"schema": 1','"schema": 1,"schema": 1').encode()
        with self.assertRaises(ValueError):text.decode_text(raw,token=fixture()['token'],simulator=fixture()['simulator'],bundle=fixture()['bundle'],legacy=dict(process=100,capturedMs=20))

    def test_all_original_source_native_root_frame_motion_and_cleanup_checks_remain_required(self):
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve();state=evidence(out)
            self.assertEqual(text.accept_native_export(out,state),text.RESULT)
            for change in (lambda s:s['original_inputs'].update({text.NAME:'0'*64}),
                lambda s:s['arms'][0].update(cleanup='FAIL'),lambda s:s['arms'][0].update(companion_captures=[]),
                lambda s:s['arms'][0].update(motion_companion_captures=[]),
                lambda s:next(c for c in s['commands'] if c['label']=='compose-tests').update(timeout_seconds=901)):
                value=copy.deepcopy(state);change(value)
                with self.assertRaises(ValueError):text.accept_native_export(out,value)
            p=next((out/'compose').glob('public-text-*.json'));original=p.read_bytes();v=json.loads(original)
            for change in (lambda v:v.update(syntheticControl=False),lambda v:v['text'].update(rows=[[1,1,0,0,0,0,0,0,0,0]])):
                wrong=copy.deepcopy(v);change(wrong);p.write_text(json.dumps(wrong))
                with self.assertRaises(ValueError):text.accept_native_export(out,state)
            p.write_bytes(original);p.unlink()
            with self.assertRaises((ValueError,OSError)):text.accept_native_export(out,state)

    @unittest.skipUnless(platform.system()=='Darwin','Actual Apple SDK unavailable on Linux')
    def test_actual_new_swift_export_and_generated_driver_typecheck_with_simulator_sdk(self):
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve()
            sdk=subprocess.check_output(['/usr/bin/xcrun','--sdk','iphonesimulator','--show-sdk-path'],text=True,timeout=45).strip()
            framework=subprocess.check_output(['/usr/bin/xcrun','--sdk','iphonesimulator','--show-sdk-platform-path'],text=True,timeout=45).strip()
            for name,source in [('Export.swift',text.template(text.SWIFT)),('Driver.swift',plan()[pin02_capture.DRIVER])]:
                p=out/name;p.write_text(source)
                result=subprocess.run(['/usr/bin/xcrun','swiftc','-typecheck','-D','DEBUG','-sdk',sdk,
                    '-target','arm64-apple-ios18.5-simulator','-F',framework+'/Developer/Library/Frameworks',
                    '-I',framework+'/Developer/usr/lib','-module-cache-path',str(out/'cache'),str(p)],
                    capture_output=True,text=True,timeout=180)
                self.assertEqual(result.returncode,0,result.stdout+result.stderr)


if __name__=='__main__':unittest.main()
