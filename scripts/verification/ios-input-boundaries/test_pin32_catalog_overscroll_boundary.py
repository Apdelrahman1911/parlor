"""Copy-only inverse/privacy controls; synthetic fixtures are not native evidence."""
import copy
import hashlib
import json
from pathlib import Path
import platform
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import pin01_copy
import pin02_capture
import pin32_catalog_overscroll_boundary as boundary
import run_pin03
import test_pin31_catalog_input_boundary as previous


def plan():
    pair = boundary.previous.observed.original.motion.pair
    return boundary.renderer_for_profile(boundary.PROFILE,boundary.SOURCE,pair.RUNTIME,pair.DEVICE)(
        {p:pair.fixture(p) for p in pair.MODIFIED},pair.INPUTS,arm='compose')


def fixture():
    return dict(schema=1,token='A'*8+'-'+ 'A'*4+'-'+ 'A'*4+'-'+ 'A'*4+'-'+ 'A'*12,
        simulator='B'*8+'-'+ 'B'*4+'-'+ 'B'*4+'-'+ 'B'*4+'-'+ 'B'*12,
        bundle='me.parlor.ios.debug',process=100,capturedMs=19,
        overscroll=dict(created=1,factoryMissing=False,permissionCallbacks=0,overflow=0,invalid=0,rows=[[1,2,0],[3,4,1]]))


def decode(v):
    f=fixture()
    return boundary.decode_overscroll(json.dumps(v).encode(),token=f['token'],simulator=f['simulator'],
        bundle=f['bundle'],legacy=dict(process=100,capturedMs=20))


def evidence(out):
    state=previous.evidence(out);state['profile']=boundary.PROFILE;dest=out/'compose'
    (dest/'inputs.json').write_text(json.dumps(state['original_inputs']|
        {p:hashlib.sha256(v.encode()).hexdigest() for p,v in plan().items()}))
    legacy=json.loads(next(dest.glob('capture-*.json')).read_bytes())
    value=fixture();value.update(token=legacy['token'],simulator=state['arms'][0]['simulator'],
        process=legacy['process'],capturedMs=legacy['capturedMs'])
    # Fabricated rows are bounded by this fabricated original capture, not native observations.
    value['overscroll']['rows']=[[0,0,0]]
    (dest/('catalog-overscroll-'+legacy['token']+'.json')).write_text(json.dumps(value))
    return state


class CatalogOverscrollControls(unittest.TestCase):
    def test_only_factory_provider_bridge_and_failure_export_deltas_are_exactly_invertible(self):
        before=previous.plan();after=plan()
        self.assertEqual(set(after),set(before)|{boundary.ADDITION})
        for p in before:
            restored=after[p]
            if p==pin01_copy.SHELL:
                restored=pin01_copy.once(restored,boundary.OBSERVED_PERMISSION_CALLBACK,boundary.PERMISSION_CALLBACK)
            elif p==pin02_capture.DRIVER: restored=boundary.restore_permission_driver(restored)
            elif p==pin01_copy.MAIN: restored=restored.removesuffix(boundary.BRIDGE)
            elif p==boundary.previous.catalog.HOME:
                restored=pin01_copy.once(restored,boundary.OBSERVED_OPEN,boundary.HOME_OPEN)
                restored=pin01_copy.once(restored,boundary.OBSERVED_CLOSE,boundary.HOME_CLOSE)
            elif p==pin02_capture.CONTAINER:
                restored=restored.removesuffix('\n'+boundary.template('PIN32CatalogOverscroll.swift.in'))
                restored=pin01_copy.once(restored,boundary.previous.observed.original.PROPERTY+boundary.PROPERTY,
                    boundary.previous.observed.original.PROPERTY)
                restored=pin01_copy.once(restored,boundary.EXPORT+boundary.previous.observed.original.EXPORT,
                    boundary.previous.observed.original.EXPORT)
            self.assertEqual(restored,before[p],p)
        self.assertEqual(boundary.restore_permission_driver(after[pin02_capture.DRIVER]),before[pin02_capture.DRIVER])
        for token in ('.tap()', '.typeText(', '.swipeUp(', '.swipeDown(', 'timeout: ', 'XCTAssert'):
            self.assertEqual(after[pin02_capture.DRIVER].count(token),before[pin02_capture.DRIVER].count(token),token)
        kotlin=boundary.template('PIN32CatalogOverscroll.kt.in')
        self.assertEqual(kotlin.count('original.isInProgress'),1)
        self.assertEqual(kotlin.count('original.createOverscrollEffect()'),1)
        self.assertIn('OverscrollEffect by original',kotlin)
        self.assertIn('data class PIN32ObservedFactory',kotlin)
        for forbidden in ('pointerInput','GestureRecognizer','snapshotFlow','delay(', 'applyToScroll(',
            'applyToFling(','.cancel(','.scrollToItem(','.isScrollInProgress'):
            self.assertNotIn(forbidden,kotlin)
        swift=boundary.template('PIN32CatalogOverscroll.swift.in')
        for forbidden in ('Timer(','.tap()', 'accessibilityLabel','accessibilityValue','isInProgress','UIApplication'):
            self.assertNotIn(forbidden,swift)
        self.assertIn('!attempted',swift)
        self.assertIn('O_EXCL | O_NOFOLLOW',swift)
        self.assertIn('0o600',swift)

    def test_permission_query_is_only_after_original_failed_host_name_wait_and_native_capture(self):
        driver=plan()[pin02_capture.DRIVER]
        section=boundary.HOST_NAME_CAPTURE
        self.assertLess(section.index('waitForExistence(timeout: 10)'),section.index('if !pin32HostNameAppeared'))
        self.assertLess(section.index('app.screenshot()'),section.index('pin11CapturePublicFailureOnce(app)'))
        self.assertLess(section.index('pin11CapturePublicFailureOnce(app)'),section.index('PIN29EquivalentQuery.capture'))
        self.assertLess(section.index('PIN29EquivalentQuery.capture'),section.index('XCTAssertTrue(pin32HostNameAppeared)'))
        self.assertNotIn('.exists',boundary.PERMISSION_REFERENCE)
        self.assertNotIn('.frame',boundary.PERMISSION_REFERENCE)
        self.assertEqual(driver.count(boundary.HOST_NAME_WAIT),1)
        with self.assertRaises(ValueError):boundary.extend_permission_driver(driver)

    @unittest.skipUnless(platform.system()=='Darwin','Actual generated XCTest driver requires Apple SDK')
    def test_actual_permission_failure_driver_typechecks_against_apple_sdk(self):
        sdk=subprocess.check_output(['/usr/bin/xcrun','--sdk','iphonesimulator','--show-sdk-path'],text=True,timeout=45).strip()
        platform_path=subprocess.check_output(['/usr/bin/xcrun','--sdk','iphonesimulator','--show-sdk-platform-path'],text=True,timeout=45).strip()
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve();source=out/'driver.swift';source.write_text(plan()[pin02_capture.DRIVER])
            result=subprocess.run(['/usr/bin/xcrun','swiftc','-typecheck','-D','DEBUG','-sdk',sdk,
                '-target','arm64-apple-ios18.5-simulator','-F',platform_path+'/Developer/Library/Frameworks',
                '-I',platform_path+'/Developer/usr/lib','-module-cache-path',str(out/'cache'),str(source)],
                capture_output=True,text=True,timeout=180)
            self.assertEqual(result.returncode,0,result.stdout+result.stderr)

    def test_exact_profile_source_arm_and_original_case_budget_required(self):
        pair=boundary.previous.observed.original.motion.pair
        for args in ((boundary.PROFILE,'0'*40,pair.RUNTIME,pair.DEVICE),
            (boundary.PROFILE,boundary.SOURCE,'foreign',pair.DEVICE)):
            with self.assertRaises(ValueError):boundary.renderer_for_profile(*args)
        with self.assertRaises(ValueError):
            boundary.renderer_for_profile(boundary.PROFILE,boundary.SOURCE,pair.RUNTIME,pair.DEVICE)({}, {},arm='original')
        self.assertEqual(boundary.CASES,boundary.previous.CASES)
        self.assertEqual(boundary.TEST_TIMEOUT_SECONDS,4500)
        self.assertEqual(boundary.test_command(['xcodebuild'],Path('/owned/result')),
            boundary.previous.test_command(['xcodebuild'],Path('/owned/result')))
        with self.assertRaises(ValueError):run_pin03.main(overscroll_getter=True,catalog_input_boundary=True)
        self.assertEqual(run_pin03.completed_exit_code(boundary.RESULT),0)
        self.assertEqual(run_pin03.completed_exit_code('OVERSCROLL_FIX_QUALIFIED'),1)

    def test_only_finite_ordered_original_getter_bits_are_accepted(self):
        self.assertEqual(decode(fixture())['busy_reads'],1)
        for change in (lambda v:v.update(private='forbidden'),lambda v:v.update(schema=True),
            lambda v:v.update(process=True),lambda v:v.update(token='foreign'),lambda v:v.update(capturedMs=21),
            lambda v:v['overscroll'].update(overflow=1),lambda v:v['overscroll'].update(invalid=1),
            lambda v:v['overscroll'].update(created=True),lambda v:v['overscroll'].update(factoryMissing=1),
            lambda v:v['overscroll'].update(permissionCallbacks=True),
            lambda v:v['overscroll'].update(permissionCallbacks=513),
            lambda v:v['overscroll'].update(rows=v['overscroll']['rows']*513),
            lambda v:v['overscroll']['rows'][0].__setitem__(0,float('nan')),
            lambda v:v['overscroll']['rows'][0].__setitem__(2,True),
            lambda v:v['overscroll']['rows'][0].__setitem__(2,2),
            lambda v:v['overscroll']['rows'][0].__setitem__(1,22),
            lambda v:v['overscroll']['rows'][1].__setitem__(0,1),
            lambda v:v['overscroll'].update(created=0,factoryMissing=False),
            lambda v:v['overscroll'].update(created=0,factoryMissing=True)):
            value=fixture();change(value)
            with self.subTest(change=change),self.assertRaises(ValueError):decode(value)
        raw=json.dumps(fixture()).encode().replace(b'"schema": 1',b'"schema": 1, "schema": 1')
        f=fixture()
        with self.assertRaises(ValueError):boundary.decode_overscroll(raw,token=f['token'],simulator=f['simulator'],
            bundle=f['bundle'],legacy=dict(process=100,capturedMs=20))

    def test_empty_real_getter_ledger_or_absent_factory_never_proves_undelivered_input(self):
        value=fixture();value['overscroll']['rows']=[]
        self.assertEqual(decode(value)['rows'],0)
        value['overscroll'].update(created=0,factoryMissing=True)
        self.assertTrue(decode(value)['factory_missing'])
        self.assertNotIn('root_cause',decode(value))

    def test_all_previous_companions_and_new_failed_getter_file_are_mandatory(self):
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve();state=evidence(out)
            self.assertEqual(boundary.accept_native_export(out,state),boundary.RESULT)
            receipt=json.loads((out/'catalog-overscroll-observations.json').read_bytes())
            for key in ('application_root_cause','shipping_qualified','native_timing_neutrality','physical_verified'):
                self.assertIs(receipt[key],False)
            q=next((out/'compose').glob('catalog-overscroll-*.json'));raw=q.read_bytes();q.unlink()
            with self.assertRaises((ValueError,OSError)):boundary.accept_native_export(out,state)
            q.write_bytes(raw)
            for change in (lambda v:v.update(source='foreign'),lambda v:v['arms'][0].update(cleanup='FAIL'),
                lambda v:v['arms'][0].update(test_exit=0)):
                value=copy.deepcopy(state);change(value)
                with self.assertRaises(ValueError):boundary.accept_native_export(out,value)
            next((out/'compose').glob('public-lifecycle-*.json')).unlink()
            with self.assertRaises((ValueError,OSError)):boundary.accept_native_export(out,state)

    def test_original_owned_file_is_retained_once_with_the_reviewed_ceiling(self):
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve();sandbox=out/'sandbox';destination=out/'retained';destination.mkdir()
            value=fixture();raw=json.dumps(value).encode();legacy=dict(process=100,capturedMs=20)
            path=sandbox/'tmp'/('parlor-pin32-'+value['token'])/'overscroll.json'
            path.parent.mkdir(parents=True);path.write_bytes(raw)
            args=(sandbox,destination,value['token'],value['simulator'],value['bundle'],legacy)
            with patch.object(boundary.previous,'retain_companion',return_value={'old':'unchanged'}):
                self.assertEqual(boundary.retain_companion(*args),{'old':'unchanged'})
                self.assertEqual((destination/('catalog-overscroll-'+value['token']+'.json')).read_bytes(),raw)
                with self.assertRaises(FileExistsError):boundary.retain_companion(*args)
            path.write_bytes(b'x'*(boundary.MAX_BYTES+1))
            with self.assertRaises(ValueError):run_pin03.read_capture(path,maximum=boundary.MAX_BYTES)

    @unittest.skipUnless(platform.system()=='Darwin','Actual Apple SDK unavailable on Linux')
    def test_actual_native_export_template_typechecks_against_apple_sdk(self):
        sdk=subprocess.check_output(['/usr/bin/xcrun','--sdk','iphonesimulator','--show-sdk-path'],text=True,timeout=45).strip()
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve();source=out/'getter-export.swift'
            source.write_text(boundary.template('PIN32CatalogOverscroll.swift.in'))
            result=subprocess.run(['/usr/bin/xcrun','swiftc','-typecheck','-D','DEBUG','-sdk',sdk,
                '-target','arm64-apple-ios18.5-simulator','-module-cache-path',str(out/'cache'),str(source)],
                capture_output=True,text=True,timeout=180)
            self.assertEqual(result.returncode,0,result.stdout+result.stderr)


if __name__=='__main__':unittest.main()
