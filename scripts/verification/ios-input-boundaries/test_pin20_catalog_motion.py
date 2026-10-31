"""Fabricated portable controls, not actual scroll/UIKit/host causal evidence."""
import copy
import hashlib
import importlib
import json
import os
from pathlib import Path
import platform
import subprocess
import sys
import tempfile
import unittest

import pin01_copy
import pin02_capture
import pin06_public_setup_pair as pair
import pin08_accessibility as native
import pin12_public_boundaries as public
import pin20_catalog_motion as motion
import run_pin03

prior = sys.modules.get('_parlor_root_boundary_controls') or importlib.import_module('test_pin10_root_boundary')


def plan():
    return motion.renderer_for_profile(motion.PROFILE,motion.SOURCE,pair.RUNTIME,pair.DEVICE)(
        {p:pair.fixture(p) for p in pair.MODIFIED},pair.INPUTS,arm='compose')


def fixture():
    return dict(schema=1,token=prior.fixture()['token'],simulator=prior.fixture()['simulator'],
        bundle=prior.fixture()['bundle'],process=100,capturedMs=19,
        motion=dict(overflow=0,invalid=0,syntheticControl=True,rows=[[1,1,0,0,0],[2,1,1,2,10],[3,1,0,4,20],[4,0,0,0,0]]))


def decode(v):
    f=fixture()
    return motion.decode_motion(json.dumps(v).encode(),token=f['token'],simulator=f['simulator'],
                                bundle=f['bundle'],legacy=dict(process=100,capturedMs=20))


def evidence(out):
    state=prior.evidence(out);state['profile']=motion.PROFILE
    dest=out/'compose';(out/'native').rename(dest);row=state['arms'][0];row['arm']='compose'
    state['commands']=[{k:v.replace('native-', 'compose-') if k=='label' else v for k,v in r.items()}
                       for r in state['commands']]
    for command in state['commands']:
        if command['label']=='compose-tests':
            command['command']=motion.test_command(['xcodebuild'],Path('/owned/tests.xcresult'))
            command['timeout_seconds']=900
    summary=json.loads((dest/'summary.json').read_bytes())
    summary.update(totalTestCount=3,passedTests=2,failedTests=1,skippedTests=0,expectedFailures=0,result='Failed',
        testFailures=[dict(testIdentifierString=motion.CONTROL,
            failureText='XCTAssertEqual failed - One public setup tap must remove the source and expose its destination')])
    (dest/'summary.json').write_text(json.dumps(summary))
    tests=json.loads((dest/'tests.json').read_bytes());tests['testNodes']=[
        dict(nodeType='Test Case',nodeIdentifier=t,result='Failed' if t==motion.CONTROL else 'Passed')
        for t in motion.CASES];(dest/'tests.json').write_text(json.dumps(tests))
    row['methods']={t:'Failed' if t==motion.CONTROL else 'Passed' for t in motion.CASES}
    row['attachment_test_ids']=[motion.CONTROL]
    (dest/'inputs.json').write_text(json.dumps(state['original_inputs'] |
        {p:hashlib.sha256(v.encode()).hexdigest() for p,v in plan().items()}))
    p=next(dest.glob('capture-*.json'));legacy=json.loads(p.read_bytes());legacy['snapshot']['nativeMode']=0
    legacy['snapshot']['rows']=[r for r in legacy['snapshot']['rows'] if r[1]!=1]
    p.write_text(json.dumps(legacy));row['captures']=[run_pin03.decode_capture(p.read_bytes(),{legacy['token']},
        row['simulator'],state['debug_bundle'])]
    value=fixture();value.update(token=legacy['token'],simulator=row['simulator'])
    q=dest/('catalog-motion-'+legacy['token']+'.json');q.write_text(json.dumps(value))
    row['motion_companion_captures']=[motion.decode_motion(q.read_bytes(),token=legacy['token'],
        simulator=row['simulator'],bundle=state['debug_bundle'],legacy=legacy)]
    return state


class CatalogMotionControls(unittest.TestCase):
    def test_real_retention_path_accepts_reviewed_motion_ceiling_and_preserves_original_bytes(self):
        value = fixture()
        # Exercise the actual reader, not just the JSON decoder: PIN20's
        # hosted failure occurred before its small numeric file could be read.
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary).resolve(); sandbox = root / 'sandbox'
            dest = root / 'retained'; dest.mkdir()
            path = sandbox / 'tmp' / ('parlor-pin20-' + value['token']) / 'motion.json'
            path.parent.mkdir(parents=True)
            raw = json.dumps(value).encode(); path.write_bytes(raw)
            result = motion.retain_motion_companion(sandbox, dest, value['token'], value['simulator'],
                value['bundle'], dict(process=100, capturedMs=20))
            self.assertTrue(result['complete'])
            self.assertEqual((dest / ('catalog-motion-' + value['token'] + '.json')).read_bytes(), raw)

    def test_motion_reader_preserves_file_safety_and_does_not_expand_default_legacy_limit(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary).resolve(); path = root / 'motion.json'
            path.write_bytes(b'x' * (pin02_capture.MAX_BYTES + 1))
            with self.assertRaises(ValueError): run_pin03.read_capture(path)
            self.assertEqual(run_pin03.read_capture(path, maximum=motion.MAX_BYTES), path.read_bytes())
            for maximum in (True, 0, motion.MAX_BYTES - 1, motion.MAX_BYTES + 1):
                with self.subTest(maximum=maximum), self.assertRaises(ValueError):
                    run_pin03.read_capture(path, maximum=maximum)
            link = root / 'link'; link.symlink_to(path)
            with self.assertRaises(OSError): run_pin03.read_capture(link, maximum=motion.MAX_BYTES)
            hard = root / 'hard'; os.link(path, hard)
            with self.assertRaises(ValueError): run_pin03.read_capture(path, maximum=motion.MAX_BYTES)
            hard.unlink()
            fifo = root / 'fifo'; os.mkfifo(fifo)
            with self.assertRaises(ValueError): run_pin03.read_capture(fifo, maximum=motion.MAX_BYTES)
            for raw in (b'', b'x' * (motion.MAX_BYTES + 1)):
                path.write_bytes(raw)
                with self.assertRaises(ValueError): run_pin03.read_capture(path, maximum=motion.MAX_BYTES)

    def test_original_journeys_only_gain_declared_control_and_passive_catalog_observation(self):
        baseline=public.renderer_for_profile(public.PROFILE,public.SOURCE,pair.RUNTIME,pair.DEVICE)(
            {p:pair.fixture(p) for p in pair.MODIFIED},pair.INPUTS,arm='compose')
        p=plan();driver=p[pin02_capture.DRIVER]
        first=driver.index('    // COPY ONLY: this method must FAIL')
        last=driver.index('    @MainActor\n    func testColdLaunchRendersComposeHomeWithoutUnexpectedAlert()',first)
        added=driver[first:last]
        self.assertEqual(driver[:first]+driver[last:],baseline[pin02_capture.DRIVER])
        self.assertEqual(added.count('app.launchEnvironment["PARLOR_PIN20_SYNTHETIC"] = "1"'),1)
        self.assertEqual(driver.count('PARLOR_PIN20_SYNTHETIC'),1)
        self.assertEqual(p[pin01_copy.PICKER],baseline[pin01_copy.PICKER])
        self.assertEqual(p[pin01_copy.CONTENT],baseline[pin01_copy.CONTENT])
        self.assertEqual(p[pin01_copy.SHELL].replace(motion.FLAGGED_HOST,motion.HOST),baseline[pin01_copy.SHELL])
        self.assertEqual(p[pin01_copy.MAIN].removesuffix(motion.BRIDGE).replace(motion.CONFIGURE,''),
                         baseline[pin01_copy.MAIN])
        self.assertEqual(p[motion.catalog.HOME].replace(
            '    com.parlor.app.inputcopy.PIN20ObserveCatalogMotion(catalogScrollState.listState)\n',''),
            baseline[motion.catalog.HOME])
        container=p[pin02_capture.CONTAINER].removesuffix('\n'+(pair.HERE/motion.SWIFT).read_text())
        container=container.replace('\n    private let pin20Motion = PIN20CatalogMotionExport()','')
        container=container.replace('        let motionExport = pin20Motion\n','')
        container=container.replace(', weak motionExport','')
        container=container.replace('            motionExport?.exportAfterFailureOnce(snapshot: { MainViewControllerKt.PIN20CatalogMotionSnapshot() })\n','')
        self.assertEqual(container,baseline[pin02_capture.CONTAINER])
        self.assertEqual(p[motion.ADDITION],(pair.HERE/motion.KOTLIN).read_text())
        self.assertIn('weak motionExport',p[pin02_capture.CONTAINER])
        for other in ('original','native'):
            with self.assertRaises(ValueError):
                motion.renderer_for_profile(motion.PROFILE,motion.SOURCE,pair.RUNTIME,pair.DEVICE)(
                    {p:pair.fixture(p) for p in pair.MODIFIED},pair.INPUTS,arm=other)
        with self.assertRaises(ValueError):run_pin03.main(public_motion=True,settled_idle=True)
        self.assertEqual(run_pin03.completed_exit_code(motion.RESULT),0)
        self.assertEqual(run_pin03.completed_exit_code('PUBLIC_MOTION_PRODUCTION_QUALIFIED'),1)

    def test_motion_template_never_reads_labels_keys_values_or_mutates_scroll(self):
        kotlin=(pair.HERE/motion.KOTLIN).read_text();swift=(pair.HERE/motion.SWIFT).read_text()
        for forbidden in ('scrollToItem','animateScroll','delay(','while (true)','Timer(',
                          'displayName','roomCode','game.definition','visibleItemsInfo','print('):
            self.assertNotIn(forbidden,kotlin)
        self.assertIn('snapshotFlow { Triple(state.isScrollInProgress',kotlin)
        self.assertIn('private const val CAPACITY = 4096',kotlin)
        self.assertIn('onDispose { PIN20CatalogMotion.record(false, false, 0, 0) }',kotlin)
        self.assertIn('Thread.isMainThread, !attempted',swift)
        self.assertIn('O_EXCL | O_NOFOLLOW',swift)
        self.assertIn('0o600',swift)
        for forbidden in ('accessibilityLabel','accessibilityValue','UIAccessibilityPostNotification','Timer(','.tap()'):
            self.assertNotIn(forbidden,swift)

    def test_foreign_private_unbounded_nonfinite_boolean_or_reordered_motion_rejects(self):
        self.assertTrue(decode(fixture())['complete'])
        for change in (lambda v:v.update(private='unallowed'),lambda v:v.update(process=True),
            lambda v:v.update(schema=True),lambda v:v.update(token='foreign'),lambda v:v.update(capturedMs=21),
            lambda v:v['motion'].update(invalid=1),lambda v:v['motion'].update(overflow=1),
            lambda v:v['motion'].update(rows=[]),lambda v:v['motion'].update(value='unallowed'),
            lambda v:v['motion'].update(syntheticControl=1),
            lambda v:v['motion']['rows'].reverse(),lambda v:v['motion']['rows'][0].__setitem__(2,True),
            lambda v:v['motion']['rows'][0].__setitem__(3,129),lambda v:v['motion']['rows'][0].__setitem__(3,1.2),
            lambda v:v['motion']['rows'][0].__setitem__(4,100001),lambda v:v['motion']['rows'][0].__setitem__(0,float('nan')),
            lambda v:v['motion']['rows'][-1].__setitem__(3,1)):
            value=fixture();change(value)
            with self.subTest(change=change),self.assertRaises(ValueError):decode(value)
        f=fixture()
        for raw in (b'x'*(motion.MAX_BYTES+1),json.dumps(f).replace('"schema": 1','"schema": 1,"schema": 1').encode()):
            with self.assertRaises(ValueError):motion.decode_motion(raw,token=f['token'],simulator=f['simulator'],
                bundle=f['bundle'],legacy=dict(process=100,capturedMs=20))

    def test_complete_original_export_accepts_only_exact_failure_source_and_companions(self):
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve();state=evidence(out)
            self.assertEqual(motion.accept_native_export(out,state),motion.RESULT)
            for change in (lambda s:s['arms'][0].update(motion_companion_captures=[]),
                           lambda s:s['arms'][0]['methods'].update({motion.CONTROL:'Passed'}),
                           lambda s:next(c for c in s['commands'] if c['label']=='compose-tests').update(timeout_seconds=901)):
                wrong=copy.deepcopy(state);change(wrong)
                with self.assertRaises(ValueError):motion.accept_native_export(out,wrong)
            p=next((out/'compose').glob('catalog-motion-*.json'))
            value=json.loads(p.read_bytes());value['motion']['syntheticControl']=False;p.write_text(json.dumps(value))
            with self.assertRaises(ValueError):motion.accept_native_export(out,state)
            p.unlink()
            with self.assertRaises((ValueError,OSError)):motion.accept_native_export(out,state)

    def test_control_startup_or_frame_failure_cannot_substitute_for_actual_host_assertion(self):
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve();state=evidence(out)
            summary=json.loads((out/'compose/summary.json').read_bytes())
            tests=json.loads((out/'compose/tests.json').read_bytes());simulator=state['arms'][0]['simulator']
            self.assertEqual(motion.verify_results(summary,tests,simulator,65),state['arms'][0]['methods'])
            for change in (lambda s:s.update(skippedTests=1),lambda s:s.update(testFailures=[]),
                          lambda s:s['testFailures'][0].update(failureText='Public setup control must stop scrolling before it is tapped')):
                wrong=copy.deepcopy(summary);change(wrong)
                with self.assertRaises(ValueError):motion.verify_results(wrong,tests,simulator,65)
            with self.assertRaises(ValueError):motion.verify_results(summary,tests,simulator,0)

    @unittest.skipUnless(platform.system()=='Darwin','Actual Apple SDK unavailable on Linux')
    def test_actual_new_swift_export_and_generated_driver_typecheck_with_simulator_sdk(self):
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve()
            sdk=subprocess.check_output(['/usr/bin/xcrun','--sdk','iphonesimulator','--show-sdk-path'],text=True,timeout=45).strip()
            framework=subprocess.check_output(['/usr/bin/xcrun','--sdk','iphonesimulator','--show-sdk-platform-path'],text=True,timeout=45).strip()
            for name,source in [('Export.swift',(pair.HERE/motion.SWIFT).read_text()),('Driver.swift',plan()[pin02_capture.DRIVER])]:
                p=out/name;p.write_text(source)
                result=subprocess.run(['/usr/bin/xcrun','swiftc','-typecheck','-D','DEBUG','-sdk',sdk,
                    '-target','arm64-apple-ios18.5-simulator','-F',framework+'/Developer/Library/Frameworks',
                    '-I',framework+'/Developer/usr/lib','-module-cache-path',str(out/'cache'),str(p)],
                    capture_output=True,text=True,timeout=180)
                self.assertEqual(result.returncode,0,result.stdout+result.stderr)


if __name__=='__main__':unittest.main()
