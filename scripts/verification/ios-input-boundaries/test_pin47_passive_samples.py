"""Fixed passive sampling controls; fabricated outcomes never certify application behavior."""
import hashlib,json,platform,subprocess,tempfile,unittest
from pathlib import Path
import pin47_failure_runloop as c
from test_pin47_failure_runloop import evidence,plan


def sample_plan():
    leaf=c.runtime.pair_control.leaf
    return c.renderer_for_profile(c.SAMPLE_PROFILE,c.SOURCE,c.RUNTIME,c.DEVICE)(
        {p:leaf.pair.fixture(p) for p in leaf.pair.MODIFIED},leaf.pair.INPUTS,arm='compose')


def passing_evidence(out):
    state=evidence(out);d=out/'compose';row=state['arms'][0];control=c.select_profile(c.SAMPLE_PROFILE)
    methods={k:'Passed' for k in c.SAMPLE_CASES};state['profile']=control.PROFILE
    row.update(methods=methods,test_exit=0,result='JOURNEYS_PASSED',attachment_test_ids=[],captures=[],companion_captures=[])
    summary=c.load(d/'summary.json');tests=c.load(d/'tests.json')
    summary.update(totalTestCount=10,passedTests=10,failedTests=0,result='Passed',testFailures=[])
    tests['testNodes']=[dict(nodeType='Test Case',nodeIdentifier=k,result=v,durationInSeconds=200) for k,v in methods.items()]
    for name,value in (('summary',summary),('tests',tests)):(d/(name+'.json')).write_text(json.dumps(value))
    for p in d.glob('capture-*.json'):p.unlink()
    for p in d.glob('native-runloop-*.json'):p.unlink()
    for p in (d/'attachments').rglob('request.json'):p.unlink();p.parent.rmdir()
    (d/'inputs.json').write_text(json.dumps(state['original_inputs']|{p:hashlib.sha256(v.encode()).hexdigest() for p,v in sample_plan().items()}))
    command=next(x for x in state['commands'] if x['label']=='compose-tests')
    command['command']=[a for a in command['command'] if not a.startswith('-only-testing:')]+[
        a for a in control.test_command([],d/'tests.xcresult') if a.startswith('-only-testing:')]
    command.update(exit_code=0,timeout_seconds=2700);state['build_profile']['test_timeout_seconds']=2700
    (d/'test.log').write_text(''.join("Test Case '-[iosAppUITests.IOSAppLaunchUITests "+k.split('/')[1].removesuffix('()')+"]' started.\n" for k in c.SAMPLE_CASES))
    return state


class PassiveSampleControls(unittest.TestCase):
    def test_only_ten_fixed_wrappers_change_from_accepted_export_plan(self):
        baseline=plan();sample=sample_plan();self.assertEqual(set(sample),set(baseline))
        for p in sample:
            if p!=c.pin02_capture.DRIVER:self.assertEqual(sample[p],baseline[p])
        inverse=sample[c.pin02_capture.DRIVER].replace(c.sample_wrappers(),'')
        inverse=inverse.replace('        print("PIN47 failed-record clock ms: \\(ProcessInfo.processInfo.systemUptime * 1000)")\n','')
        inverse=inverse.replace('            print("PIN47 failed-text-query clock origin ms: \\(started * 1000)")\n','')
        self.assertEqual(inverse,baseline[c.pin02_capture.DRIVER])
        self.assertEqual(len(c.SAMPLE_CASES),10);self.assertEqual(tuple(sorted(c.SAMPLE_CASES)),c.SAMPLE_CASES)
        self.assertEqual(c.sample_wrappers().count('        try testAdditionalGamesExposeOnlyMultiplayerSetupInArabic()'),5)
        self.assertEqual(c.sample_wrappers().count('        try testAdditionalGamesExposeOnlyMultiplayerSetupInEnglish()'),5)
        control=c.select_profile(c.SAMPLE_PROFILE);argv=control.test_command(['/usr/bin/xcodebuild','-quiet'],Path('/result'))
        self.assertEqual([a for a in argv if a.startswith('-only-testing:')],['-only-testing:iosAppUITests/'+k.removesuffix('()') for k in c.SAMPLE_CASES])
        self.assertNotIn('-quiet',argv);self.assertNotIn(c.SENTINEL_CASE,''.join(argv))
        self.assertEqual(control.TEST_TIMEOUT_SECONDS,2700)

    def test_complete_pass_means_only_diagnostic_acceptance_and_failures_cannot_be_lost(self):
        with tempfile.TemporaryDirectory() as tmp:
            out=Path(tmp).resolve();state=passing_evidence(out);control=c.select_profile(c.SAMPLE_PROFILE)
            self.assertEqual(control.accept_native_export(out,state),c.RESULT)
            receipt=c.load(out/'failure-runloop.json');self.assertEqual(receipt['native'],[])
            for k in ('application_root_cause','shipping_qualified','native_timing_neutrality','physical_verified'):self.assertIs(receipt[k],False)
            summary=c.load(out/'compose/summary.json');tests=c.load(out/'compose/tests.json');row=state['arms'][0]
            summary.update(passedTests=9,failedTests=1,result='Failed',testFailures=[dict(testIdentifierString=c.SAMPLE_CASES[0],failureText='Actual assertion retained')])
            tests['testNodes'][0]['result']='Failed'
            actual=control.verify_results(summary,tests,row['simulator'],65)
            self.assertEqual(actual[c.SAMPLE_CASES[0]],'Failed')
            with self.assertRaises(ValueError):control.verify_results(summary,tests,row['simulator'],0)
            summary['testFailures']=[]
            with self.assertRaises(ValueError):control.verify_results(summary,tests,row['simulator'],65)

    def test_missing_reordered_skipped_retried_wrong_runtime_or_budget_evidence_rejects(self):
        mutations=(lambda o,s:(o/'compose/test.log').write_text(''),lambda o,s:s['arms'][0].update(cleanup='FAIL'),
            lambda o,s:s.update(profile=c.EXPORT_PROFILE),lambda o,s:s['commands'][1].update(timeout_seconds=2701),
            lambda o,s:s['commands'][1]['command'].append('-retry-tests-on-failure'))
        for mutate in mutations:
            with tempfile.TemporaryDirectory() as tmp:
                out=Path(tmp).resolve();state=passing_evidence(out);mutate(out,state)
                with self.assertRaises(ValueError):c.select_profile(c.SAMPLE_PROFILE).accept_native_export(out,state)
        with tempfile.TemporaryDirectory() as tmp:
            out=Path(tmp).resolve();state=passing_evidence(out);summary=c.load(out/'compose/summary.json');tests=c.load(out/'compose/tests.json')
            summary['skippedTests']=1
            with self.assertRaises(ValueError):c.select_profile(c.SAMPLE_PROFILE).verify_results(summary,tests,state['arms'][0]['simulator'],0)

    @unittest.skipUnless(platform.system()=='Darwin','Actual Apple copied wrapper driver parser required')
    def test_actual_apple_new_sample_wrappers_parse(self):
        with tempfile.TemporaryDirectory() as tmp:
            p=Path(tmp)/'driver.swift';p.write_text(sample_plan()[c.pin02_capture.DRIVER])
            r=subprocess.run(['/usr/bin/xcrun','swiftc','-frontend','-parse','-D','DEBUG','-target','arm64-apple-ios26.2-simulator',str(p)],capture_output=True,text=True,timeout=120)
            self.assertEqual(r.returncode,0,r.stdout+r.stderr)
