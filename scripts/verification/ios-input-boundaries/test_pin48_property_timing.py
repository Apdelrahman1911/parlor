"""Source/negative controls; fabricated clocks are not native causal evidence."""
import copy
import hashlib
import json
from pathlib import Path
import platform
import subprocess
import tempfile
import unittest

import pin47_failure_runloop as c
import pin48_property_timing as timing
from test_pin47_passive_samples import passing_evidence
from test_pin47_failure_runloop import plan

SAMPLE = ('Public control settlement: at=2, querySeconds=8.5, frame=Optional((24, 419, 354, 268)), '
          'enabled=false, stable=false, hittable=nil, existsSeconds=0.1, frameSeconds=0.2, '
          'enabledSeconds=8.1, hittableSeconds=0.001\n')


def query_plan():
    leaf=c.runtime.pair_control.leaf
    return c.renderer_for_profile(c.QUERY_PROFILE,c.SOURCE,c.RUNTIME,c.DEVICE)(
        {p:leaf.pair.fixture(p) for p in leaf.pair.MODIFIED},leaf.pair.INPUTS,arm='compose')


def query_evidence(out):
    state=passing_evidence(out);d=out/'compose';row=state['arms'][0]
    methods=dict.fromkeys(c.QUERY_CASES,'Passed');state['profile']=c.QUERY_PROFILE;row['methods']=methods
    a=c.load(d/'summary.json');b=c.load(d/'tests.json');a.update(totalTestCount=2,passedTests=2)
    b['testNodes']=[dict(nodeType='Test Case',nodeIdentifier=k,result=v,durationInSeconds=1) for k,v in methods.items()]
    for name,value in (('summary',a),('tests',b)):(d/(name+'.json')).write_text(json.dumps(value))
    (d/'inputs.json').write_text(json.dumps(state['original_inputs']|{p:hashlib.sha256(v.encode()).hexdigest() for p,v in query_plan().items()}))
    control=c.select_profile(c.QUERY_PROFILE);cmd=state['commands'][1]
    cmd['command']=[a for a in cmd['command'] if not a.startswith('-only-testing:')]+[
        a for a in control.test_command([],d/'tests.xcresult') if a.startswith('-only-testing:')]
    cmd['timeout_seconds']=900;state['build_profile']['test_timeout_seconds']=900
    (d/'test.log').write_text(''.join("Test Case '-[iosAppUITests."+k.split('/')[0]+' '+k.split('/')[1].removesuffix('()')+"]' started.\n" for k in methods))
    return state


class PropertyTimingControls(unittest.TestCase):
    def test_only_clocks_and_failure_string_differ_from_original_pair_plan(self):
        actual=query_plan();baseline=plan();leaf=c.runtime.pair_control.leaf
        sentinel=leaf.native.SENTINEL.replace('testPIN39NativeOnlyExportCapability',c.SENTINEL_CASE.split('/')[1].removesuffix('()')).replace('PIN39Configure','PIN47Configure')
        self.assertEqual(set(actual),set(baseline))
        for p in actual:
            if p==c.pin02_capture.DRIVER:
                self.assertEqual(timing.restore(actual[p]),baseline[p].replace(sentinel+'\n',''))
                original=leaf.pair.fixture(p)
                for _,statement in timing.QUERIES:self.assertEqual(actual[p].count(statement),original.count(statement))
                for query in ('control.exists','control.frame','control.isEnabled','control.isHittable','control.tap()',
                              'timeout: 10','XCTAssertEqual(result, .completed'):
                    # Same driver plus existing failure-ownership interceptor; no new query/action.
                    self.assertEqual(actual[p].count(query),timing.restore(actual[p]).count(query))
            else:self.assertEqual(actual[p],baseline[p])
        driver=actual[c.pin02_capture.DRIVER]
        self.assertIn('let frame = exists ? control.frame : nil',driver)
        self.assertIn('let enabled = exists && control.isEnabled',driver)
        self.assertIn('let hittable = stable ? control.isHittable : nil',driver)
        self.assertIn('if samples.count < 12',driver)
        self.assertNotIn(c.SENTINEL_CASE.split('/')[1].removesuffix('()'),driver)
        self.assertNotIn('testPIN47ArabicSample01',driver)

    def test_exact_original_two_methods_no_exporter_samples_retry_or_budget_extension(self):
        control=c.select_profile(c.QUERY_PROFILE)
        argv=control.test_command(['/usr/bin/xcodebuild','-quiet'],Path('/result'))
        self.assertEqual([v for v in argv if v.startswith('-only-testing:')],['-only-testing:iosAppUITests/'+k.removesuffix('()') for k in c.runtime.CASES])
        self.assertEqual(control.TEST_TIMEOUT_SECONDS,900);self.assertNotIn('-quiet',argv)
        self.assertEqual(c.QUERY_CASES,c.runtime.CASES)
        for position in range(4):
            args=[c.QUERY_PROFILE,c.SOURCE,c.RUNTIME,c.DEVICE];args[position]+='-foreign'
            with self.assertRaises(ValueError):c.renderer_for_profile(*args)

    def test_property_decoder_is_fixed_numeric_bounded_and_checks_interval_containment(self):
        decoded=timing.decode_failed_samples(SAMPLE)
        self.assertEqual(decoded[0][0]['enabledSeconds'],8.1)
        self.assertEqual(set(decoded[0][0]),{'at','querySeconds','existsSeconds','frameSeconds','enabledSeconds','hittableSeconds'})
        for log in (SAMPLE.replace('enabledSeconds=8.1','enabledSeconds=9'),SAMPLE.replace('existsSeconds=0.1','existsSeconds=-1'),
                    SAMPLE.replace('frameSeconds=0.2','frameSeconds=1e999'),SAMPLE.replace(', enabledSeconds=8.1',''),
                    SAMPLE.replace('at=2','at=2, at=3'),SAMPLE*3,
                    'Public control settlement: '+('; '.join(SAMPLE.split(': ',1)[1].strip() for _ in range(13)))):
            with self.subTest(log=log[:60]),self.assertRaises(ValueError):timing.decode_failed_samples(log)
        self.assertEqual(timing.decode_failed_samples('No failure printed\n'),[])

    def test_all_pass_pair_and_unchanged_strict_native_source_outcome_cleanup_gates(self):
        with tempfile.TemporaryDirectory() as tmp:
            out=Path(tmp).resolve();state=query_evidence(out)
            self.assertEqual(c.select_profile(c.QUERY_PROFILE).accept_native_export(out,state),c.RESULT)
            receipt=c.load(out/'query-property-boundaries.json')
            self.assertEqual(receipt['failed_settlement_property_samples'],{})
            for key in ('application_root_cause','shipping_qualified','native_timing_neutrality','physical_verified'):self.assertIs(receipt[key],False)
            before=(out/'query-property-boundaries.json').read_bytes()
            self.assertEqual(timing.write_receipt(out,state),receipt)
            self.assertEqual((out/'query-property-boundaries.json').read_bytes(),before)
            (out/'query-property-boundaries.json').write_text('{}')
            with self.assertRaises(ValueError):timing.write_receipt(out,state)
        changes=(lambda o,s:s['arms'][0].update(cleanup='FAIL'),lambda o,s:s['commands'][1].update(timeout_seconds=901),
                 lambda o,s:s['commands'][1]['command'].append('-retry-tests-on-failure'),
                 lambda o,s:(o/'compose/inputs.json').write_text('{}'),lambda o,s:s.update(runtime='foreign'))
        for mutate in changes:
            with tempfile.TemporaryDirectory() as tmp:
                out=Path(tmp).resolve();state=query_evidence(out);mutate(out,state)
                with self.assertRaises(ValueError):c.select_profile(c.QUERY_PROFILE).accept_native_export(out,state)
                self.assertFalse((out/'query-property-boundaries.json').exists())

    def test_failure_property_reports_require_exact_actual_failed_method_ownership(self):
        with tempfile.TemporaryDirectory() as tmp:
            out=Path(tmp).resolve();state=query_evidence(out);p=out/'compose/test.log';p.write_text(p.read_text()+SAMPLE)
            with self.assertRaises(ValueError):timing.write_receipt(out,state)
            method=c.QUERY_CASES[-1];state['arms'][0]['methods'][method]='Failed'
            summary=c.load(out/'compose/summary.json');summary['testFailures']=[dict(testIdentifierString=method,
                failureText='Public setup control must stop scrolling before it is tapped')]
            (out/'compose/summary.json').write_text(json.dumps(summary))
            receipt=timing.write_receipt(out,state)
            self.assertEqual(set(receipt['failed_settlement_property_samples']),{method})
            (out/'query-property-boundaries.json').unlink();p.write_text(p.read_text().replace(SAMPLE,''))
            with self.assertRaises(ValueError):timing.write_receipt(out,state)

    @unittest.skipUnless(platform.system()=='Darwin','Actual Apple copied-driver parser required')
    def test_actual_apple_property_timed_driver_parses(self):
        with tempfile.TemporaryDirectory() as tmp:
            p=Path(tmp)/'driver.swift';p.write_text(query_plan()[c.pin02_capture.DRIVER])
            result=subprocess.run(['/usr/bin/xcrun','swiftc','-frontend','-parse','-D','DEBUG','-target','arm64-apple-ios26.2-simulator',str(p)],capture_output=True,text=True,timeout=120)
            self.assertEqual(result.returncode,0,result.stdout+result.stderr)


if __name__=='__main__':unittest.main()
