"""Portable frame controls; actual Apple ABI control is separately marked."""
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
import pin22_native_frame_boundary as frame
import run_pin03
import test_pin20_catalog_motion as previous


def plan():
    return frame.renderer_for_profile(frame.PROFILE,frame.SOURCE,frame.motion.pair.RUNTIME,frame.motion.pair.DEVICE)(
        {p:frame.motion.pair.fixture(p) for p in frame.motion.pair.MODIFIED},frame.motion.pair.INPUTS,arm='compose')


def fixture():
    p=previous.fixture()
    return dict(schema=1,token=p['token'],simulator=p['simulator'],bundle=p['bundle'],process=100,
                capturedMs=18,installed=True,overflow=0,invalid=0,rows=[[1,2,1,24,696,354,74]])


def decode(value):
    p=fixture()
    return frame.decode_frames(json.dumps(value).encode(),token=p['token'],simulator=p['simulator'],bundle=p['bundle'],
        legacy=dict(process=100,capturedMs=20),attributes=dict(capturedMs=19,rows=[[1,2,1,1,1]]))


def evidence(out):
    state=previous.evidence(out);state['profile']=frame.PROFILE;dest=out/'compose';row=state['arms'][0]
    (dest/'inputs.json').write_text(json.dumps(state['original_inputs']|
        {p:hashlib.sha256(v.encode()).hexdigest() for p,v in plan().items()}))
    legacy=json.loads(next(dest.glob('capture-*.json')).read_bytes())
    attributes=json.loads(next(dest.glob('native-accessibility-*.json')).read_bytes())
    value=fixture();value.update(token=legacy['token'],simulator=row['simulator'],process=legacy['process'],
                                capturedMs=attributes['capturedMs'])
    value['rows'][0][2]=next(r[2] for r in attributes['rows'] if r[3]==1 and int(r[4])&1)
    (dest/('native-frame-'+legacy['token']+'.json')).write_text(json.dumps(value))
    return state


class NativeFrameControls(unittest.TestCase):
    def test_original_actions_driver_catalog_and_callback_source_are_unchanged(self):
        before=previous.plan();after=plan()
        self.assertEqual(set(before),set(after))
        for p in before:
            if p != pin02_capture.CONTAINER:self.assertEqual(before[p],after[p])
        original=(frame.motion.pair.HERE/frame.native.TEMPLATE).read_text()
        extended=frame.native_template_extension(original)
        inverse=extended.replace('    private var pin22Frames: PIN22NativeFrameObserver?\n','')
        inverse=inverse.replace('        let ordinal = pin22PublicOrdinal(object)\n        guard ordinal > 0 else { return }\n',frame.IDENTITY)
        inverse=inverse.replace('element: ordinal, getter:','element: elements[identity]!, getter:')
        inverse=inverse.replace(frame.ORDINAL,'').replace(frame.INSTALL,'')
        inverse=inverse.replace('        pin22Frames?.exportAfterFailureOnce()\n','')
        self.assertEqual(inverse,original)
        self.assertEqual(after[pin02_capture.CONTAINER].removesuffix('\n'+(frame.motion.pair.HERE/frame.TEMPLATE).read_text())
                         .replace(extended,original),before[pin02_capture.CONTAINER])
        self.assertEqual(frame.test_command(['xcodebuild'],Path('/owned/result')),
                         frame.motion.test_command(['xcodebuild'],Path('/owned/result')))
        with self.assertRaises(ValueError):frame.native_template_extension(original+'\n')
        with self.assertRaises(ValueError):run_pin03.main(frame_boundary=True,public_motion=True)
        self.assertEqual(run_pin03.completed_exit_code(frame.RESULT),0)
        self.assertEqual(run_pin03.completed_exit_code('NATIVE_FRAME_QUALIFIED'),1)

    def test_frame_hook_reads_only_original_return_and_never_changes_parent_or_input(self):
        text=(frame.motion.pair.HERE/frame.TEMPLATE).read_text()
        self.assertEqual(hashlib.sha256(text.encode()).hexdigest(),frame.TEMPLATE_SHA256)
        self.assertIn('class_addMethod(target, selector, replacement, encoding)',text)
        self.assertNotIn('method_setImplementation',text)
        self.assertEqual(text.count('original(object, selector)'),1)
        for forbidden in ('accessibilityLabel','accessibilityValue','accessibilityIdentifier','description(',
                          'UIAccessibilityPostNotification','Timer(','.tap()','object_getClassName'):
            self.assertNotIn(forbidden,text)
        self.assertIn('return result // Entire original CGRect unchanged',text)
        self.assertIn('guard Thread.isMainThread, armed()',text)
        self.assertIn('O_EXCL | O_NOFOLLOW',text)

    def test_only_bounded_original_geometry_and_shared_public_ordinals_accept(self):
        self.assertEqual(decode(fixture())['matched_button_ordinals'],1)
        for change in (lambda v:v.update(private='forbidden'),lambda v:v.update(schema=True),
            lambda v:v.update(process=True),lambda v:v.update(token='foreign'),lambda v:v.update(capturedMs=21),
            lambda v:v.update(installed=False),lambda v:v.update(invalid=1),lambda v:v.update(overflow=1),
            lambda v:v.update(rows=[]),lambda v:v.update(rows=v['rows']*(frame.MAX_ROWS+1)),
            lambda v:v['rows'][0].__setitem__(0,float('nan')),lambda v:v['rows'][0].__setitem__(0,3),
            lambda v:v['rows'][0].__setitem__(2,True),lambda v:v['rows'][0].__setitem__(2,2),
            lambda v:v['rows'][0].__setitem__(3,100001),lambda v:v['rows'][0].__setitem__(5,-1),
            lambda v:v['rows'][0].__setitem__(6,0)):
            value=fixture();change(value)
            with self.subTest(change=change),self.assertRaises(ValueError):decode(value)
        raw=json.dumps(fixture()).replace('"schema": 1','"schema": 1,"schema": 1').encode()
        with self.assertRaises(ValueError):frame.decode_frames(raw,token=fixture()['token'],simulator=fixture()['simulator'],
            bundle=fixture()['bundle'],legacy=dict(process=100,capturedMs=20),attributes=dict(capturedMs=19,rows=[]))

    def test_complete_source_bound_failure_requires_actual_frame_file_and_all_prior_checks(self):
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve();state=evidence(out)
            self.assertEqual(frame.accept_native_export(out,state),frame.RESULT)
            for change in (lambda s:s.update(profile=frame.motion.PROFILE),
                lambda s:s['arms'][0].update(cleanup='FAIL'),lambda s:s['arms'][0].update(companion_captures=[]),
                lambda s:s['arms'][0]['methods'].update({frame.CONTROL:'Passed'})):
                value=copy.deepcopy(state);change(value)
                with self.assertRaises(ValueError):frame.accept_native_export(out,value)
            p=next((out/'compose').glob('native-frame-*.json'));p.unlink()
            with self.assertRaises((ValueError,OSError)):frame.accept_native_export(out,state)

    @unittest.skipUnless(platform.system()=='Darwin','Actual Apple CGRect/Objective-C ABI unavailable on Linux')
    def test_actual_apple_objc_struct_return_and_parent_isolation(self):
        control='''
import Foundation
import CoreGraphics
class PIN22Parent: NSObject {
    static var reads = 0
    @objc dynamic func accessibilityFrame() -> CGRect {
        PIN22Parent.reads += 1
        return CGRect(x: 24, y: 696, width: 354, height: 74)
    }
}
class PIN22Leaf: PIN22Parent {}
let leaf = PIN22Leaf()
var armed = true
let expected = leaf.accessibilityFrame()
precondition(PIN22Parent.reads == 1)
let observer = PIN22NativeFrameObserver(armed: { armed }, ordinal: { _ in 7 })
precondition(observer.install(target: PIN22Leaf.self))
precondition(leaf.accessibilityFrame() == expected)
precondition(PIN22Parent.reads == 2)
precondition(PIN22Parent().accessibilityFrame() == expected)
precondition(PIN22Parent.reads == 3)
precondition((observer.controlSnapshot()["rows"] as! [[Double]]).count == 1)
armed = false
precondition(leaf.accessibilityFrame() == expected)
precondition(PIN22Parent.reads == 4)
precondition((observer.controlSnapshot()["rows"] as! [[Double]]).count == 1)
precondition(!observer.install(target: PIN22Leaf.self))
print("ACTUAL_APPLE_STRUCT_RETURN_AND_PARENT_ISOLATION_PASS_NOT_APP_QUALIFICATION")
'''
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve();p=out/'main.swift';p.write_text((frame.motion.pair.HERE/frame.TEMPLATE).read_text()+control)
            binary=out/'control'
            compilation=subprocess.run(['/usr/bin/xcrun','swiftc','-D','PIN22_CONTROL','-module-cache-path',str(out/'cache'),
                            str(p),'-o',str(binary)],capture_output=True,text=True,timeout=180)
            self.assertEqual(compilation.returncode,0,compilation.stdout+compilation.stderr)
            result=subprocess.run([str(binary)],capture_output=True,text=True,timeout=30)
            self.assertEqual(result.returncode,0,result.stdout+result.stderr)
            self.assertIn('ACTUAL_APPLE_STRUCT_RETURN_AND_PARENT_ISOLATION_PASS_NOT_APP_QUALIFICATION',result.stdout)
            sdk=subprocess.check_output(['/usr/bin/xcrun','--sdk','iphonesimulator','--show-sdk-path'],text=True,timeout=45).strip()
            standalone=out/'frame.swift';standalone.write_text((frame.motion.pair.HERE/frame.TEMPLATE).read_text())
            typecheck=subprocess.run(['/usr/bin/xcrun','swiftc','-typecheck','-D','DEBUG','-sdk',sdk,
                '-target','arm64-apple-ios18.5-simulator','-module-cache-path',str(out/'sim-cache'),str(standalone)],
                capture_output=True,text=True,timeout=180)
            self.assertEqual(typecheck.returncode,0,typecheck.stdout+typecheck.stderr)


if __name__=='__main__':unittest.main()
