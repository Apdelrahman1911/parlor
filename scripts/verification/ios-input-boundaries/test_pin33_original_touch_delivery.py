import copy
import json
from pathlib import Path
import unittest
import hashlib
import platform
import subprocess
import tempfile
from unittest.mock import patch

import pin01_copy
import pin02_capture
import pin33_original_touch_delivery as boundary
import run_pin03
import test_pin32_catalog_overscroll_boundary as previous

import pin33_touch_decoder as decoder

C = Path(__file__).resolve().parent


def plan():
    pair=boundary.previous.previous.observed.original.motion.pair
    return boundary.renderer_for_profile(boundary.PROFILE,boundary.SOURCE,pair.RUNTIME,pair.DEVICE)(
        {p:pair.fixture(p) for p in pair.MODIFIED},pair.INPUTS,arm='compose')


def evidence(out):
    state=previous.evidence(out); state['profile']=boundary.PROFILE; dest=out/'compose'
    (dest/'inputs.json').write_text(json.dumps(state['original_inputs']|
        {p:hashlib.sha256(v.encode()).hexdigest() for p,v in plan().items()}))
    for path in dest.glob('capture-*.json'):
        legacy=json.loads(path.read_bytes()); value=fixture()
        value.update(token=legacy['token'],simulator=state['arms'][0]['simulator'],bundle=state['debug_bundle'],
            process=legacy['process'],capturedMs=legacy['capturedMs'])
        (dest/('native-touch-'+legacy['token']+'.json')).write_text(json.dumps(value))
    return state


def fixture():
    return dict(schema=1, token='public-control-token', simulator='owned-control-simulator',
        bundle='public.control.debug', process=100, capturedMs=20,
        installed=True, installation=[5, 1, 1], overflow=0,
        rows=[[1, 1, 0, 0, 1], [2, 5, 0, 0, 1], [3, 5, 1, 0, 1], [4, 1, 1, 0, 1],
              [5, 8, 0, 0, 1], [6, 8, 1, 1, 1]])


def intake(raw):
    v=fixture()
    return decoder.decode(raw, token=v['token'], simulator=v['simulator'], bundle=v['bundle'],
        legacy=dict(process=100, capturedMs=21))


class OriginalTouchControls(unittest.TestCase):
    def test_nested_state_and_arbitration_original_returns_are_admitted(self):
        r=intake(json.dumps(fixture()).encode())
        self.assertTrue(r['complete'])
        self.assertEqual(r['touch_entries'],1)
        self.assertFalse(r['application_root_cause'])
        self.assertFalse(r['shipping_qualified'])

    def test_no_original_calls_or_incomplete_installation_never_invents_calls(self):
        value=fixture(); value['rows']=[]
        self.assertEqual(intake(json.dumps(value).encode())['touch_entries'],0)
        value.update(installed=False, installation=[5,0,0])
        self.assertFalse(intake(json.dumps(value).encode())['complete'])

    def test_foreign_private_nonfinite_bounds_and_broken_forwarding_are_rejected(self):
        for change in (
            lambda v:v.update(text='never retained'), lambda v:v.update(process=101),
            lambda v:v.update(schema=True), lambda v:v.update(capturedMs=22),
            lambda v:v.update(installation=[5,True,1]), lambda v:v.update(installation=[33,1,1]),
            lambda v:v.update(overflow=1000001), lambda v:v.update(rows=[[1,1,1,0]]),
            lambda v:v['rows'][0].__setitem__(0,float('nan')),
            lambda v:v['rows'][0].__setitem__(1,True), lambda v:v['rows'][0].__setitem__(1,9),
            lambda v:v['rows'][0].__setitem__(2,2), lambda v:v['rows'][0].__setitem__(3,1),
            lambda v:v['rows'][2].__setitem__(3,3), lambda v:v['rows'][-1].__setitem__(3,6),
            lambda v:v['rows'][-1].__setitem__(0,1),
            lambda v:v['rows'][0].__setitem__(4,2),
            lambda v:v['rows'][-1].__setitem__(4,0), lambda v:v['rows'].pop(),
        ):
            value=copy.deepcopy(fixture());change(value)
            with self.subTest(change=change),self.assertRaises(ValueError):intake(json.dumps(value).encode())
        duplicate=json.dumps(fixture()).replace('"schema": 1','"schema": 1,"schema": 1').encode()
        with self.assertRaises(ValueError):intake(duplicate)

    def test_prepared_source_does_not_add_recognizers_read_arguments_or_change_original_input(self):
        source=(C/'PIN33OriginalTouchDelivery.swift.in').read_text()
        for forbidden in ('addGestureRecognizer(', 'removeGestureRecognizer(', '.state', '.tap()',
            'accessibilityValue', 'accessibilityLabel', 'location(in:', 'input.count', 'event.timestamp',
            'Timer(', 'UIAccessibilityPostNotification'):
            self.assertNotIn(forbidden,source)
        self.assertEqual(source.count('original(object, selector, input, event)'),1)
        self.assertEqual(source.count('original(object, selector, other)'),1)
        self.assertEqual(source.count('state(object, selectors[4], value)'),1)
        self.assertIn('return result',source)
        self.assertIn('Self.same(methods[index], inherited) == (index == 4)',source)
        self.assertIn('class_addMethod(target, selectors[4]',source)
        self.assertIn('O_EXCL | O_NOFOLLOW',source)

    def test_exact_inverse_preserves_every_previous_original_input_and_driver(self):
        before=previous.plan();after=plan()
        self.assertEqual(set(before),set(after))
        for path in before:
            restored=after[path]
            if path==pin02_capture.CONTAINER:
                restored=restored.removesuffix('\n'+boundary.template())
                restored=pin01_copy.once(restored,boundary.OBSERVED_ADAPTER,boundary.ADAPTER)
                restored=pin01_copy.once(restored,boundary.OBSERVED_REGISTER,boundary.REGISTER)
                restored=pin01_copy.once(restored,boundary.attached.PROPERTY+boundary.PROPERTY,boundary.attached.PROPERTY)
                restored=pin01_copy.once(restored,boundary.attached.EXPORT+boundary.EXPORT,boundary.attached.EXPORT)
            self.assertEqual(restored,before[path],path)
        self.assertEqual(after[pin02_capture.DRIVER],before[pin02_capture.DRIVER])
        self.assertEqual(boundary.CASES,boundary.previous.CASES)
        self.assertEqual(boundary.TEST_TIMEOUT_SECONDS,4500)
        self.assertEqual(boundary.test_command(['xcodebuild'],Path('/owned/result')),
                         boundary.previous.test_command(['xcodebuild'],Path('/owned/result')))
        with self.assertRaises(ValueError):run_pin03.main(original_touch_delivery=True,overscroll_getter=True)
        self.assertEqual(run_pin03.completed_exit_code(boundary.RESULT),0)
        self.assertEqual(run_pin03.completed_exit_code('NATIVE_TOUCH_FIX_QUALIFIED'),1)

    def test_new_native_companion_never_replaces_previous_source_query_cleanup_security_gates(self):
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve();state=evidence(out)
            self.assertEqual(boundary.accept_native_export(out,state),boundary.RESULT)
            for change in (lambda v:v.update(source='foreign'),lambda v:v['arms'][0].update(cleanup='FAIL'),
                           lambda v:v['arms'][0].update(test_exit=0)):
                value=copy.deepcopy(state);change(value)
                with self.assertRaises(ValueError):boundary.accept_native_export(out,value)
            path=next((out/'compose').glob('native-touch-*.json')); raw=path.read_bytes();path.unlink()
            with self.assertRaises((ValueError,OSError)):boundary.accept_native_export(out,state)
            path.write_bytes(raw);value=json.loads(raw);value.update(installed=False)
            path.write_text(json.dumps(value))
            with self.assertRaises(ValueError):boundary.accept_native_export(out,state)
            path.write_bytes(raw)
            next((out/'compose').glob('catalog-overscroll-*.json')).unlink()
            with self.assertRaises((ValueError,OSError)):boundary.accept_native_export(out,state)

    def test_owned_companion_retained_once_no_symlink_or_size_fallback(self):
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve();sandbox=out/'sandbox';dest=out/'retained';dest.mkdir()
            value=fixture();raw=json.dumps(value).encode();legacy=dict(process=100,capturedMs=21)
            path=sandbox/'tmp'/('parlor-pin33-'+value['token'])/'touches.json'
            path.parent.mkdir(parents=True);path.write_bytes(raw)
            args=(sandbox,dest,value['token'],value['simulator'],value['bundle'],legacy)
            with patch.object(boundary.previous,'retain_companion',return_value={'original':'unchanged'}):
                self.assertEqual(boundary.retain_companion(*args),{'original':'unchanged'})
                self.assertEqual((dest/('native-touch-'+value['token']+'.json')).read_bytes(),raw)
                with self.assertRaises(FileExistsError):boundary.retain_companion(*args)
            path.unlink();path.symlink_to(dest/('native-touch-'+value['token']+'.json'))
            with patch.object(boundary.previous,'retain_companion',return_value={}):
                with self.assertRaises(ValueError):boundary.retain_companion(*args)

    @unittest.skipUnless(platform.system()=='Darwin','Actual Objective-C ABI requires Apple runtime')
    def test_actual_original_native_calls_arguments_returns_state_parent_and_scope_forward(self):
        source=boundary.template()+'\n'+(C/'PIN33OriginalTouchABIControl.swift.in').read_text()
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve();path=out/'main.swift';path.write_text(source)
            result=subprocess.run(['/usr/bin/xcrun','swiftc','-D','PIN33_CONTROL',str(path),'-o',str(out/'control')],
                capture_output=True,text=True,timeout=180)
            self.assertEqual(result.returncode,0,result.stdout+result.stderr)
            executed=subprocess.run([str(out/'control')],capture_output=True,text=True,timeout=30)
            self.assertEqual(executed.returncode,0,executed.stdout+executed.stderr)
            self.assertIn('PIN33_ORIGINAL_CALL_ARGUMENT_RETURN_STATE_PARENT_SCOPE_CONTROLS_PASS',executed.stdout)

    @unittest.skipUnless(platform.system()=='Darwin','Actual UIKit leaf binding requires Apple SDK')
    def test_actual_original_recognizer_binding_and_export_typecheck_against_apple_sdk(self):
        sdk=subprocess.check_output(['/usr/bin/xcrun','--sdk','iphonesimulator','--show-sdk-path'],text=True,timeout=45).strip()
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve();source=out/'original-touch.swift';source.write_text(boundary.template())
            result=subprocess.run(['/usr/bin/xcrun','swiftc','-typecheck','-D','DEBUG','-sdk',sdk,
                '-target','arm64-apple-ios18.5-simulator','-module-cache-path',str(out/'cache'),str(source)],
                capture_output=True,text=True,timeout=180)
            self.assertEqual(result.returncode,0,result.stdout+result.stderr)


if __name__=='__main__':unittest.main()
