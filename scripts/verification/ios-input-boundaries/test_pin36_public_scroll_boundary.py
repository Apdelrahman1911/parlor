"""Portable inverse/privacy/custody and actual SDK controls; never native input causality."""
import copy
import hashlib
import json
import platform
from pathlib import Path
import subprocess
import tempfile
import unittest

import pin01_copy
import pin02_capture
import pin36_public_scroll_boundary as control
import run_pin03
import test_pin34_touch_export_control as prior


def plan():
    return control.renderer_for_profile(control.PROFILE,control.SOURCE,control.pair.RUNTIME,control.pair.DEVICE)(
        {p:control.pair.fixture(p) for p in control.pair.MODIFIED},control.pair.INPUTS,arm='compose')


def sample():
    return dict(schema=1,token='sample-token',simulator='owned-simulator',bundle='public.control.debug',process=1,
        capturedMs=10,scroll=dict(created=[1,0,0],factoryMissing=[False,False,False],overflow=0,invalid=0,
            rows=[[1,1,0,2,1,0],[2,2,0,0,0,0],[3,4,0,1,0,0]]))


def intake(raw):
    value=sample()
    return control.decode(raw,token=value['token'],simulator=value['simulator'],bundle=value['bundle'],
        legacy=dict(process=1,capturedMs=11))


def evidence(out):
    state=prior.evidence(out);state['profile']=control.PROFILE;dest=out/'compose'
    state['original_inputs']=state['original_inputs']|{control.PERMISSION:control.PERMISSION_SHA256}
    (dest/'inputs.json').write_text(json.dumps(state['original_inputs']|
        {p:hashlib.sha256(v.encode()).hexdigest() for p,v in plan().items()}))
    legacy=json.loads(next(dest.glob('capture-*.json')).read_bytes());value=sample()
    value.update(token=legacy['token'],simulator=state['arms'][0]['simulator'],bundle=state['debug_bundle'],
        process=legacy['process'],capturedMs=legacy['capturedMs'])
    (dest/('public-scroll-'+legacy['token']+'.json')).write_text(json.dumps(value))
    return state


class PublicScrollBoundaryControls(unittest.TestCase):
    def test_exact_entire_previous_plan_and_original_driver_inverse(self):
        old=control.previous.renderer_for_profile(control.previous.PROFILE,control.SOURCE,control.pair.RUNTIME,
            control.pair.DEVICE)({p:control.pair.fixture(p) for p in control.pair.MODIFIED},control.pair.INPUTS,arm='compose')
        value=plan();self.assertEqual(len(value),16)
        self.assertEqual(control.restore(value),old)
        self.assertEqual(value[pin02_capture.DRIVER],old[pin02_capture.DRIVER])
        for path,kind in ((pin01_copy.PICKER,0),(control.PERMISSION,1),(control.previous.original.text.NAME,2)):
            self.assertEqual(value[path].count(f'.verticalScroll(rememberObservedPublicScrollState({kind}))'),1)
        with self.assertRaises(ValueError):control.extend(value)
        with self.assertRaises(ValueError):control.renderer_for_profile('foreign',control.SOURCE,control.pair.RUNTIME,control.pair.DEVICE)

    def test_only_existing_isolated_sentinel_commands_no_successful_replay(self):
        argv=control.test_command(['/usr/bin/xcodebuild'],Path('/owned/result'))
        self.assertEqual([a for a in argv if a.startswith('-only-testing:')],
            ['-only-testing:iosAppUITests/'+control.CASES[0].removesuffix('()')])
        self.assertEqual(run_pin03.completed_exit_code(control.RESULT),0)
        self.assertEqual(run_pin03.completed_exit_code('PUBLIC_SCROLL_QUALIFIED'),1)
        with self.assertRaises(ValueError):run_pin03.main(public_scroll_export=True,original_touch_cohort=True)

    def test_complete_numeric_observation_does_not_claim_causality(self):
        report=intake(json.dumps(sample()).encode());self.assertEqual(report['rows'],3)
        self.assertTrue(report['complete']);self.assertFalse(report['application_root_cause'])
        self.assertFalse(report['shipping_qualified'])

    def test_private_foreign_nonfinite_bool_fractional_enums_overflow_and_bounds_fail_closed(self):
        for change in (lambda v:v.update(text='never retained'),lambda v:v.update(process=2),
            lambda v:v.update(schema=True),lambda v:v.update(capturedMs=12),
            lambda v:v['scroll'].update(rows=[[1,1,0,0,1]]),
            lambda v:v['scroll']['rows'][0].__setitem__(0,float('nan')),
            lambda v:v['scroll']['rows'][0].__setitem__(2,True),
            lambda v:v['scroll']['rows'][0].__setitem__(2,0.5),
            lambda v:v['scroll']['rows'][0].__setitem__(5,0.5),
            lambda v:v['scroll']['rows'][0].__setitem__(5,1),
            lambda v:v['scroll']['rows'][-1].__setitem__(1,1),
            lambda v:v['scroll'].update(created=[False,0,0]),
            lambda v:v['scroll'].update(created=[0,0,0]),
            lambda v:v['scroll'].update(factoryMissing=[0,False,False]),
            lambda v:v['scroll'].update(overflow=1),lambda v:v['scroll'].update(invalid=1),
            lambda v:v['scroll'].update(rows=[[1,1,0,0,0,0]]*8193)):
            value=copy.deepcopy(sample());change(value)
            with self.subTest(change=change),self.assertRaises(ValueError):intake(json.dumps(value).encode())
        with self.assertRaises(ValueError):intake(json.dumps(sample()).replace('"schema": 1','"schema": 1, "schema": 1').encode())

    def test_complete_source_product_old_companions_and_new_scope_export_are_all_required(self):
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve();state=evidence(out)
            self.assertEqual(control.accept_native_export(out,state),control.RESULT)
            receipt=json.loads((out/'public-scroll-export.json').read_bytes())
            self.assertFalse(receipt['permission_name_runtime']);self.assertFalse(receipt['shipping_qualified'])
            for change in (lambda s:s['original_inputs'].update({control.PERMISSION:'foreign'}),
                lambda s:s['arms'][0].update(cleanup='FAIL'),lambda s:s['arms'][0].update(test_exit=0),
                lambda s:s.update(profile='foreign')):
                value=copy.deepcopy(state);change(value)
                with self.assertRaises(ValueError):control.accept_native_export(out,value)
            for prefix in ('public-scroll-','native-touch-','catalog-overscroll-','public-text-','native-root-'):
                p=next((out/'compose').glob(prefix+'*.json'));raw=p.read_bytes();p.unlink()
                with self.assertRaises((ValueError,OSError)):control.accept_native_export(out,state)
                p.write_bytes(raw)

    def test_missing_mount_idle_or_unexecuted_permission_name_facts_reject(self):
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve();state=evidence(out);p=next((out/'compose').glob('public-scroll-*.json'));raw=p.read_bytes()
            for change in (lambda v:v['scroll'].update(rows=[]),
                lambda v:v['scroll'].update(rows=v['scroll']['rows'][1:]),
                lambda v:v['scroll'].update(created=[1,1,0]),
                lambda v:v['scroll']['rows'].append([5,5,1,2,1,0])):
                v=json.loads(raw);change(v);p.write_text(json.dumps(v))
                with self.assertRaises(ValueError):control.accept_native_export(out,state)

    def test_source_owns_only_public_metadata_and_original_getter_delegate(self):
        source=control.template('PIN36PublicScroll.kt.in')
        for forbidden in ('scrollTo(', 'animateScroll', 'pointerInput(', 'launch {', 'delay(', 'text.value',
            'accessibilityLabel', 'Timer(', 'catch (', 'println(', 'value.length'):
            self.assertNotIn(forbidden,source)
        self.assertEqual(source.count('val result = original()'),1)
        self.assertIn('admitted = true',source)
        self.assertIn('snapshotFlow { state.isScrollInProgress to state.value }',source)
        native=control.template('PIN36PublicScroll.swift.in')
        self.assertIn('O_EXCL | O_NOFOLLOW',native);self.assertIn('!attempted',native)
        self.assertEqual(plan()[pin02_capture.CONTAINER].count(control.EXPORT),1)

    @unittest.skipUnless(platform.system()=='Darwin','Actual Apple SDK unavailable on Linux')
    def test_actual_apple_compiler_checks_exact_weak_native_export_and_template(self):
        # This proves Swift syntax/SDK linkage only; the copied native build
        # separately verifies the real Kotlin framework export symbol.
        source='''import Foundation
import Darwin
final class MainViewControllerKt {
    static func PIN36PublicScrollSnapshot() -> String { "{}" }
}
final class Container {
    private let pin36PublicScroll = PIN36PublicScrollExport()
    func observe() {
        let callback: () -> Void = { [weak self] in
'''+control.EXPORT+'''        }
        callback()
    }
}
'''+control.template('PIN36PublicScroll.swift.in')
        with tempfile.TemporaryDirectory() as temporary:
            path=Path(temporary).resolve()/'Export.swift';path.write_text(source)
            sdk=subprocess.check_output(['/usr/bin/xcrun','--sdk','iphonesimulator','--show-sdk-path']).decode().strip()
            result=subprocess.run(['/usr/bin/xcrun','--sdk','iphonesimulator','swiftc','-typecheck','-D','DEBUG',
                '-target','arm64-apple-ios18.5-simulator','-sdk',sdk,str(path)],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
            self.assertEqual(result.returncode,0,result.stderr.decode())


if __name__=='__main__':unittest.main()
