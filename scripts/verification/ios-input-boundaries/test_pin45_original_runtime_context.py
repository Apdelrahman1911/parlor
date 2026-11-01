"""Synthetic full-context controls; fixture outcomes are never Apple evidence."""
import copy
import hashlib
import json
import os
from pathlib import Path
import platform
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import pin45_original_runtime_context as control
import test_pin44_original_runtime as prior
import test_pin16_aggregate_context as aggregate_controls


def plan():
    leaf=control.runtime.pair_control.leaf
    return control.renderer_for_profile(control.PROFILE,control.SOURCE,control.RUNTIME,control.DEVICE)(
        {p:leaf.pair.fixture(p) for p in leaf.pair.MODIFIED},leaf.pair.INPUTS,arm='compose')


def evidence(out,failures=()):
    state=prior.evidence(out,failures);state['profile']=control.PROFILE
    state['build_profile'].update(combined_xcode=True,combined_timeout_seconds=3600,
        gradle_heap_bytes=6*1024**3,gradle_workers='original-default',gradle_parallel=True,
        boot_after_build=False,xcode_jobs='original-default',product_manifest_stage='after-combined-command')
    row=state['arms'][0];d=out/'compose'
    methods={c:'Failed' if c in failures else 'Passed' for c in control.CASES}
    row.update(methods=methods,combined_exit=row['test_exit'],product_manifest_stage='after-combined-command',
               build_success_evidence='all23-executed-native-outcomes')
    summary=control.load(d/'summary.json');summary.update(totalTestCount=23,passedTests=23-len(failures))
    tests=control.load(d/'tests.json');tests['testNodes']=[dict(nodeType='Test Case',nodeIdentifier=c,result=v,durationInSeconds=1)
        for c,v in methods.items()]
    (d/'summary.json').write_text(json.dumps(summary));(d/'tests.json').write_text(json.dumps(tests))
    (d/'test.log').write_text(''.join("Test Case '-[iosAppUITests."+c.split('/')[0]+' '+c.split('/')[1].removesuffix('()')+
        "]' started.\n" for c in control.CASES))
    raws,inputs=aggregate_controls.reports();state['original_inputs'].update(inputs)
    (d/'inputs.json').write_text(json.dumps(state['original_inputs']|{p:hashlib.sha256(v.encode()).hexdigest() for p,v in plan().items()}))
    for p,b in raws.items():
        target=out/'aggregate/xml'/p;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(b)
    aggregate=dict(result='ORIGINAL774_EXECUTED_NOT_QUALIFICATION',original_uninstrumented_inputs=True,
        exit_code=0,stop_exit=0,cleanup='PASS',workers=dict(result='PASS'),
        reports=control.aggregate.verify_prelude_xml(raws,state['original_inputs']),
        retained_reports={p:hashlib.sha256(b).hexdigest() for p,b in raws.items()})
    state['aggregate_prelude']=aggregate
    for stage in ('before','after'):
        raw=b'[]';(out/'aggregate'/('simulators-'+stage+'.json')).write_bytes(raw)
        aggregate['simulators_'+stage]=hashlib.sha256(raw).hexdigest()
    row['kernel_resources']={}
    for stage in control.aggregate.combined.STAGES:
        raw=json.dumps(dict(page_size=16384,pages={k:0 for k in control.aggregate.combined.resources.VM_FIELDS},
            swap_mib=[0,0,0],physical_bytes=8*1024**3,logical_cpus=3)).encode()
        (d/('kernel-'+stage+'.json')).write_bytes(raw);row['kernel_resources'][stage]=hashlib.sha256(raw).hexdigest()
    base=['/usr/bin/xcodebuild','-project','iosApp/iosApp.xcodeproj','-scheme','iosApp','-configuration','Debug',
        '-sdk','iphonesimulator','-destination','platform=iOS Simulator,id='+row['simulator'],
        '-parallel-testing-enabled','NO','-maximum-concurrent-test-simulator-destinations','1','-destination-timeout','120']
    common=dict(status='EXITED',direct_child_reaped=True,signals=[])
    wrapper=['/bin/bash','-c','exec "$@" > "$PARLOR_INPUT_LOG" 2>&1','--']
    state['commands']=[dict(common,label='original-kmp-aggregate',command=wrapper+control.aggregate.PRELUDE_COMMAND,
                           exit_code=0,timeout_seconds=2700),
        dict(common,label='original-kmp-stop',command=wrapper+['/owned/gradlew','--stop'],exit_code=0,timeout_seconds=120),
        dict(common,label='compose-build-and-tests',command=control.test_command(base,d/'tests.xcresult'),
             exit_code=row['test_exit'],timeout_seconds=3600),
        *(c for c in state['commands'] if c['label'] not in ('compose-build','compose-tests'))]
    for c in state['commands']:
        c.setdefault('command', []) # Legacy abstract fixture records, never actual execution.
    return state


class OriginalRuntimeContextControls(unittest.TestCase):
    def test_exact_source_driver_original23_identifiers_and_missing_precursor_reject(self):
        original=prior.plan();actual=plan()
        for p,v in actual.items():
            self.assertEqual(v.replace('PIN45Configure','PIN44Configure').replace('PARLOR_PIN45_','PARLOR_PIN44_').replace(control.PROFILE,control.runtime.PROFILE),original[p])
        self.assertEqual(len(control.CASES),23)
        self.assertEqual(control.CASES[:4],control.aggregate.combined.cohort.CASES[:4])
        self.assertNotIn('test-without-building',control.test_command(['xcodebuild','-quiet'],Path('/result')))
        with patch.object(control,'CONTEXT_RECEIPT_SHA256','a'*64),self.assertRaises(ValueError):control.require_context_binding()
        for i in range(4):
            args=[control.PROFILE,control.SOURCE,control.RUNTIME,control.DEVICE];args[i]+='-wrong'
            with self.assertRaises(ValueError):control.renderer_for_profile(*args)

    def test_complete_original_success_or_failed_target_retains_all_native_and_aggregate_evidence(self):
        for failures in ((),(control.runtime.CASES[0],),control.runtime.CASES):
            with self.subTest(failures=failures),tempfile.TemporaryDirectory() as temporary:
                out=Path(temporary).resolve();state=evidence(out,failures)
                self.assertEqual(control.accept_native_export(out,state),control.RESULT)
                receipt=control.load(out/'original-runtime-context.json')
                self.assertEqual(len(receipt['methods']),23);self.assertEqual(len(receipt['native']),len(failures))
                self.assertEqual(receipt['original_uninstrumented_kmp_methods'],774)
                for key in ('application_root_cause','shipping_qualified','native_timing_neutrality','physical_verified'):
                    self.assertIs(receipt[key],False)

    def test_wrong_resource_profile_replayed_commands_timeouts_or_unreaped_children_reject(self):
        for change in (
            lambda s:s['build_profile'].update(gradle_heap_bytes=3*1024**3),
            lambda s:s['build_profile'].update(gradle_parallel=False),
            lambda s:s['commands'].append(copy.deepcopy(s['commands'][0])),
            lambda s:s['commands'][2].update(timeout_seconds=3601),
            lambda s:s['commands'][2]['command'].append('-retry-tests-on-failure'),
            lambda s:s['commands'][2]['command'].append('test-without-building'),
            lambda s:s['commands'][0].update(direct_child_reaped=False),
            lambda s:s['aggregate_prelude'].update(cleanup='FAIL'),
        ):
            with tempfile.TemporaryDirectory() as temporary:
                out=Path(temporary).resolve();state=evidence(out);change(state)
                with self.assertRaises(ValueError):control.accept_native_export(out,state)

    def test_missing_or_corrupt_aggregate_report_kernel_or_original_method_order_reject(self):
        for change in (
            lambda o,s:(o/'aggregate/xml'/control.aggregate.case_manifest()['classes'][0]['path']).unlink(),
            lambda o,s:(o/'aggregate/xml'/control.aggregate.case_manifest()['classes'][0]['path']).write_text('<testsuite/>'),
            lambda o,s:(o/'compose/kernel-before-build.json').write_text('{}'),
            lambda o,s:(o/'compose/test.log').write_text(''),
            lambda o,s:s['aggregate_prelude']['retained_reports'].clear(),
        ):
            with tempfile.TemporaryDirectory() as temporary:
                out=Path(temporary).resolve();state=evidence(out);change(out,state)
                with self.assertRaises((ValueError,FileNotFoundError)):control.accept_native_export(out,state)

    def test_wrong_runtime_skips_other_failures_and_missing_original_identifiers_reject(self):
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve();state=evidence(out)
            summary,tests=control.load(out/'compose/summary.json'),control.load(out/'compose/tests.json')
            for change in (lambda a,b:b['devices'][0].update(osVersion='18.5'),
                           lambda a,b:a.update(skippedTests=1),
                           lambda a,b:b['testNodes'].pop(),
                           lambda a,b:b['testNodes'][0].update(result='Failed')):
                a,b=copy.deepcopy(summary),copy.deepcopy(tests);change(a,b)
                with self.assertRaises(ValueError):control.verify_results(a,b,state['arms'][0]['simulator'],0)

    @unittest.skipUnless(platform.system()=='Darwin','Actual Apple original-context driver compiler required')
    def test_actual_apple_driver_parse(self):
        with tempfile.TemporaryDirectory() as temporary:
            p=Path(temporary)/'driver.swift';p.write_text(plan()[control.runtime.pair_control.pin02_capture.DRIVER])
            r=subprocess.run(['/usr/bin/xcrun','swiftc','-frontend','-parse','-D','DEBUG','-target','arm64-apple-ios26.2-simulator',str(p)],capture_output=True,text=True,timeout=120)
            self.assertEqual(r.returncode,0,r.stdout+r.stderr)

    @unittest.skipUnless(platform.system()=='Darwin','Actual Apple original-context wrapper SDK required')
    def test_actual_apple_wrapper_typecheck(self):
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve();env=dict(os.environ,TMPDIR=str(out));p=out/'owner.swift'
            p.write_text(plan()[control.runtime.pair_control.pin02_capture.CONTAINER])
            sdk=subprocess.check_output(['/usr/bin/xcrun','--sdk','iphonesimulator','--show-sdk-path'],text=True,env=env,timeout=45).strip()
            r=subprocess.run(['/usr/bin/xcrun','swiftc','-typecheck','-D','DEBUG','-sdk',sdk,'-target','arm64-apple-ios26.2-simulator',
                '-module-cache-path',str(out/'cache'),str(p)],capture_output=True,text=True,env=env,timeout=180)
            self.assertEqual(r.returncode,0,r.stdout+r.stderr)
