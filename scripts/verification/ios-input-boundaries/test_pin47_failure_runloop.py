"""Fabricated source/custody rejections plus new actual-Apple wrapper/driver checks."""
import copy,hashlib,json,os,platform,subprocess,tempfile,unittest
from pathlib import Path
from unittest.mock import patch
import pin47_failure_runloop as c
import test_pin44_original_runtime as prior
from test_pin46_main_runloop import report


def plan():
    leaf=c.runtime.pair_control.leaf
    return c.renderer_for_profile(c.PROFILE,c.SOURCE,c.RUNTIME,c.DEVICE)(
        {p:leaf.pair.fixture(p) for p in leaf.pair.MODIFIED},leaf.pair.INPUTS,arm='compose')


def evidence(out,natural_failures=()):
    # Synthetic software fixture only; never remap or mutate real run artifacts.
    state=prior.evidence(out);d=out/'compose';row=state['arms'][0]
    methods={k:'Failed' if k in natural_failures or k==c.SENTINEL_CASE else 'Passed' for k in c.CASES}
    failed=sorted(k for k,v in methods.items() if v=='Failed')
    state.update(profile=c.PROFILE);row.update(methods=methods,test_exit=65,result='JOURNEYS_FAILED',attachment_test_ids=failed)
    summary=c.load(d/'summary.json');tests=c.load(d/'tests.json')
    summary.update(totalTestCount=3,passedTests=3-len(failed),failedTests=len(failed),result='Failed',testFailures=[dict(
        testIdentifierString=k,failureText='One public setup tap must remove the source and expose its destination') for k in failed])
    tests['testNodes']=[dict(nodeType='Test Case',nodeIdentifier=k,result=v,durationInSeconds=1) for k,v in methods.items()]
    for name,value in (('summary',summary),('tests',tests)):(d/(name+'.json')).write_text(json.dumps(value))
    (d/'inputs.json').write_text(json.dumps(state['original_inputs']|{p:hashlib.sha256(v.encode()).hexdigest() for p,v in plan().items()}))
    argv=next(x for x in state['commands'] if x['label']=='compose-tests')
    argv['command']=c.test_command(['/usr/bin/xcodebuild','-project','iosApp/iosApp.xcodeproj','-scheme','iosApp','-configuration','Debug',
        '-sdk','iphonesimulator','-destination','platform=iOS Simulator,id='+row['simulator'],'-parallel-testing-enabled','NO',
        '-maximum-concurrent-test-simulator-destinations','1'],d/'tests.xcresult');argv['exit_code']=65
    (d/'test.log').write_text(''.join("Test Case '-[iosAppUITests."+k.split('/')[0]+' '+k.split('/')[1].removesuffix('()')+"]' started.\n" for k in c.CASES))
    row['captures']=[];row['companion_captures']=[]
    for index,method in enumerate(failed,1):
        token=f'00000000-0000-0000-0000-{index:012d}'
        legacy=dict(schema=1,token=token,simulator=row['simulator'],bundle=state['debug_bundle'],process=42,
            boot='00000000-0000-0000-0000-000000000100',installedMs=0,capturedMs=501,
            snapshot=dict(schema=1,nativeMode=0,overflow=0,invalid=0,rows=[]))
        req=d/'attachments'/('failed-%02d'%index)/'request.json';req.parent.mkdir(parents=True,exist_ok=True)
        req.write_text(json.dumps(dict(schema=1,token=token,ackObserved=True,originalTransitionFailed=True)))
        raw=json.dumps(legacy).encode();(d/('capture-'+token+'.json')).write_bytes(raw)
        (d/('native-runloop-'+token+'.json')).write_text(json.dumps(dict(schema=1,token=token,simulator=row['simulator'],bundle=state['debug_bundle'],process=42,observation=report())))
        row['captures'].append(c.runtime.pair_control.leaf.run_pin03.decode_capture(raw,set([token]),row['simulator'],state['debug_bundle']))
        row['companion_captures'].append(c.decode_companions(d,token,row['simulator'],state['debug_bundle'],legacy))
    return state


class FailureRunLoopControls(unittest.TestCase):
    def test_original_helpers_actions_assertions_deadlines_and_application_bytes_preserved(self):
        v=plan();old=prior.plan();container=v[c.pin02_capture.CONTAINER];driver=v[c.pin02_capture.DRIVER]
        leaf=c.runtime.pair_control.leaf
        self.assertEqual(set(v),set(leaf.pair.MODIFIED))
        for p in v:
            if p not in c.pin02_capture.MODIFIED:self.assertEqual(v[p],leaf.pair.fixture(p))
        for forbidden in ('method_setImplementation','class_addMethod(','accessibilityElements','addGestureRecognizer','Timer(','DispatchQueue.','MainViewControllerKt.'):
            self.assertNotIn(forbidden,container)
        self.assertNotIn('viewDidAppear',container);self.assertIn('CFRunLoopMode.commonModes',container)
        sentinel=leaf.native.SENTINEL.replace('testPIN39NativeOnlyExportCapability',c.SENTINEL_CASE.split('/')[1].removesuffix('()')).replace('PIN39Configure','PIN47Configure')
        inverse=driver.replace(sentinel+'\n','').replace('            print("PIN47 failed-query clock origin ms: \\(started * 1000)")\n','')
        inverse=inverse.replace('PIN47Configure','PIN44Configure').replace('PARLOR_PIN47_','PARLOR_PIN44_').replace(c.PROFILE,c.runtime.PROFILE)
        self.assertEqual(inverse,old[c.pin02_capture.DRIVER])
        owner=c.owner_source();self.assertIn('O_EXCL | O_NOFOLLOW',owner);self.assertIn('0o700',owner);self.assertIn('fsync(fd)',owner)
        self.assertIn('guard let self, self.exportAfterFailureOnce()',owner)

    def test_exact_actual_capability_source_device_and_single_original_methods_then_new_control(self):
        c.require_capability_binding()
        with patch.object(c,'CAPABILITY_STATE_SHA256','a'*64),self.assertRaises(ValueError):c.require_capability_binding()
        for pos in range(4):
            args=[c.PROFILE,c.SOURCE,c.RUNTIME,c.DEVICE];args[pos]+='-wrong'
            with self.assertRaises(ValueError):c.renderer_for_profile(*args)
        self.assertEqual(c.CASES[:2],c.runtime.CASES);self.assertTrue(c.CASES[-1].endswith('testZPIN47FailureRunLoopExport()'))
        argv=c.test_command(['xcodebuild','-quiet'],Path('/result'))
        self.assertEqual([a for a in argv if a.startswith('-only-testing:')],['-only-testing:'+t for t in c.TESTS])
        self.assertEqual(c.TEST_TIMEOUT_SECONDS,900)

    def test_additive_dispatch_rejects_multiple_profiles_and_collection_is_not_qualification(self):
        runner=c.runtime.pair_control.leaf.run_pin03
        with self.assertRaises(ValueError):runner.main(failure_runloop=True,original_runtime=True)
        self.assertEqual(runner.completed_exit_code(c.RESULT),0)
        for result in ('SUCCESS','SHIPPING_QUALIFIED',None):self.assertEqual(runner.completed_exit_code(result),1)

    def test_original_success_or_failure_and_new_unexpected_sentinel_preserve_all_outcomes(self):
        for failed in ((),(c.runtime.CASES[0],),c.runtime.CASES):
            with self.subTest(failed=failed),tempfile.TemporaryDirectory() as tmp:
                out=Path(tmp).resolve();state=evidence(out,failed)
                self.assertEqual(c.accept_native_export(out,state),c.RESULT)
                receipt=c.load(out/'failure-runloop.json');self.assertEqual(len(receipt['native']),1+len(failed))
                for key in ('application_root_cause','shipping_qualified','native_timing_neutrality','physical_verified'):self.assertIs(receipt[key],False)

    def test_missing_wrong_process_private_duplicate_and_invalid_phase_companion_reject(self):
        changes=(lambda v:v.update(value='private'),lambda v:v.update(schema=True),lambda v:v.update(process=43),
            lambda v:v['observation'].update(observerRemoved=False),lambda v:v['observation'].update(capturedMs=502),
            lambda v:v['observation'].update(invalid=1),lambda v:v['observation']['phaseCounts'].__setitem__(0,True))
        for change in changes:
            with tempfile.TemporaryDirectory() as tmp:
                out=Path(tmp).resolve();state=evidence(out);p=next((out/'compose').glob('native-runloop-*.json'));v=c.load(p);change(v);p.write_text(json.dumps(v))
                with self.assertRaises(ValueError):c.accept_native_export(out,state)
        with tempfile.TemporaryDirectory() as tmp:
            out=Path(tmp).resolve();state=evidence(out);p=next((out/'compose').glob('native-runloop-*.json'));p.unlink()
            with self.assertRaises(FileNotFoundError):c.accept_native_export(out,state)

    def test_skipped_or_passing_control_changed_runtime_extra_failure_order_command_cleanup_or_source_reject(self):
        changes=(lambda o,s:(o/'compose/test.log').write_text(''),lambda o,s:s['arms'][0].update(cleanup='FAIL'),
            lambda o,s:s['commands'].append(copy.deepcopy(s['commands'][0])),lambda o,s:s['commands'][1]['command'].append('-retry-tests-on-failure'),
            lambda o,s:s['commands'][1].update(timeout_seconds=901),lambda o,s:s['commands'][1].update(direct_child_reaped=False),
            lambda o,s:s.update(runtime='com.apple.CoreSimulator.SimRuntime.iOS-18-5'),
            lambda o,s:(o/'compose/inputs.json').write_text('{}'))
        for change in changes:
            with tempfile.TemporaryDirectory() as tmp:
                out=Path(tmp).resolve();state=evidence(out);change(out,state)
                with self.assertRaises(ValueError):c.accept_native_export(out,state)
        with tempfile.TemporaryDirectory() as tmp:
            out=Path(tmp).resolve();state=evidence(out);summary=c.load(out/'compose/summary.json');tests=c.load(out/'compose/tests.json')
            for change in (lambda a,b:a.update(skippedTests=1),lambda a,b:b['testNodes'][-1].update(result='Passed'),
                lambda a,b:a['testFailures'][0].update(failureText='Startup failed'),lambda a,b:b['devices'][0].update(osVersion='18.5')):
                a,b=copy.deepcopy(summary),copy.deepcopy(tests);change(a,b)
                with self.assertRaises(ValueError):c.verify_results(a,b,state['arms'][0]['simulator'],65)

    def test_redirected_retention_and_unacknowledged_or_duplicate_failed_requests_reject(self):
        for ack in (False,True):
            with tempfile.TemporaryDirectory() as tmp:
                out=Path(tmp).resolve();state=evidence(out);p=next((out/'compose/attachments').rglob('request.json'))
                if not ack:v=c.load(p);v['ackObserved']=False;p.write_text(json.dumps(v))
                else:(p.parent/'duplicate.json').write_bytes(p.read_bytes())
                with self.assertRaises(ValueError):c.accept_native_export(out,state)
        with tempfile.TemporaryDirectory() as tmp:
            out=Path(tmp).resolve();sandbox=out/'sandbox';target=out/'owned';target.mkdir();(target/'runloop.json').write_text('{}');token='00000000-0000-0000-0000-000000000001'
            p=sandbox/'tmp'/('parlor-pin47-runloop-'+token);p.parent.mkdir(parents=True);p.symlink_to(target,target_is_directory=True)
            with self.assertRaises(ValueError):c.retain_companion(sandbox,out,token,'unused','unused',{})

    def test_real_secure_reader_retains_valid_bytes_and_rejects_empty_oversized_or_linked_before_destination(self):
        for mutation in ('valid','empty','oversized','hardlink'):
            with self.subTest(mutation=mutation),tempfile.TemporaryDirectory() as tmp:
                out=Path(tmp).resolve();state=evidence(out);row=state['arms'][0];d=out/'compose'
                destination=next(d.glob('native-runloop-*.json'));raw=destination.read_bytes();destination.unlink()
                token=destination.name.removeprefix('native-runloop-').removesuffix('.json')
                legacy=c.load(d/('capture-'+token+'.json'))
                sandbox=out/'sandbox';p=sandbox/'tmp'/('parlor-pin47-runloop-'+token)/'runloop.json'
                p.parent.mkdir(parents=True)
                p.write_bytes(b'' if mutation=='empty' else b' '*(c.MAX_BYTES+1) if mutation=='oversized' else raw)
                if mutation=='hardlink':os.link(p,p.parent/'alias.json')
                if mutation=='valid':
                    actual=c.retain_companion(sandbox,d,token,row['simulator'],state['debug_bundle'],legacy)
                    self.assertEqual(actual,c.decode_companions(d,token,row['simulator'],state['debug_bundle'],legacy))
                    self.assertEqual(destination.read_bytes(),raw)
                else:
                    with self.assertRaises(ValueError):c.retain_companion(sandbox,d,token,row['simulator'],state['debug_bundle'],legacy)
                    self.assertFalse(destination.exists(),'Rejected retention must not create an empty evidence file')

    def test_correction_profile_executes_only_failed_exporter_and_rejects_replayed_natural_cases(self):
        control=c.select_profile(c.EXPORT_PROFILE)
        self.assertIs(c.select_profile(c.PROFILE),c)
        with self.assertRaises(ValueError):c.select_profile('arbitrary')
        argv=control.test_command(['/usr/bin/xcodebuild'],Path('/result'))
        self.assertEqual([a for a in argv if a.startswith('-only-testing:')],['-only-testing:iosAppUITests/'+c.SENTINEL_CASE.removesuffix('()')])
        self.assertEqual(control.TEST_TIMEOUT_SECONDS,900)
        with tempfile.TemporaryDirectory() as tmp:
            out=Path(tmp).resolve();state=evidence(out);d=out/'compose';row=state['arms'][0]
            summary=c.load(d/'summary.json');tests=c.load(d/'tests.json')
            with self.assertRaises(ValueError):control.verify_results(summary,tests,row['simulator'],65)
            summary.update(totalTestCount=1,passedTests=0)
            tests['testNodes']=[n for n in tests['testNodes'] if n['nodeIdentifier']==c.SENTINEL_CASE]
            methods=control.verify_results(summary,tests,row['simulator'],65)
            self.assertEqual(methods,{c.SENTINEL_CASE:'Failed'})
            state['profile']=control.PROFILE;row['methods']=methods
            for name,value in (('summary',summary),('tests',tests)):(d/(name+'.json')).write_text(json.dumps(value))
            original=next(s for s in state['commands'] if s['label']=='compose-tests')
            original['command']=[a for a in original['command'] if not a.startswith('-only-testing:')]+[a for a in argv if a.startswith('-only-testing:')]
            (d/'test.log').write_text("Test Case '-[iosAppUITests.IOSAppLaunchUITests testZPIN47FailureRunLoopExport]' started.\n")
            self.assertEqual(control.accept_native_export(out,state),c.RESULT)
            original['command'].append('-only-testing:iosAppUITests/'+c.runtime.CASES[0].removesuffix('()'))
            with self.assertRaises(ValueError):control.accept_native_export(out,state)

    @unittest.skipUnless(platform.system()=='Darwin','Actual Apple runloop wrapper SDK required')
    def test_actual_apple_passive_wrapper_typecheck(self):
        with tempfile.TemporaryDirectory() as tmp:
            out=Path(tmp).resolve();env=dict(os.environ,TMPDIR=str(out));p=out/'owner.swift';p.write_text(plan()[c.pin02_capture.CONTAINER])
            sdk=subprocess.check_output(['/usr/bin/xcrun','--sdk','iphonesimulator','--show-sdk-path'],text=True,env=env,timeout=45).strip()
            r=subprocess.run(['/usr/bin/xcrun','swiftc','-typecheck','-D','DEBUG','-sdk',sdk,'-target','arm64-apple-ios26.2-simulator',
                '-module-cache-path',str(out/'cache'),str(p)],capture_output=True,text=True,env=env,timeout=180)
            self.assertEqual(r.returncode,0,r.stdout+r.stderr)

    @unittest.skipUnless(platform.system()=='Darwin','Actual Apple unchanged-original driver parser required')
    def test_actual_apple_original_driver_and_new_export_control_parse(self):
        with tempfile.TemporaryDirectory() as tmp:
            p=Path(tmp)/'driver.swift';p.write_text(plan()[c.pin02_capture.DRIVER])
            r=subprocess.run(['/usr/bin/xcrun','swiftc','-frontend','-parse','-D','DEBUG','-target','arm64-apple-ios26.2-simulator',str(p)],capture_output=True,text=True,timeout=120)
            self.assertEqual(r.returncode,0,r.stdout+r.stderr)
