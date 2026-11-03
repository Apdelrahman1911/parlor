"""Changed execution admission only; existing Apple observers are not replayed."""
import ast
import copy
import hashlib
import json
from pathlib import Path
import tempfile
import unittest

import pin63_original_full_context as p
import run_pin03
import test_pin61_original_185 as seed
import test_pin16_aggregate_context as aggregate_seed


def evidence(out, failed=()):
    state = seed.evidence(out, failed)
    state['task'] = dict(GITHUB_RUN_ID='123456', GITHUB_RUN_ATTEMPT='1', GITHUB_JOB='ios')
    state['producer_output'] = '/Users/runner/work/_temp/parlor-original-full-context-123456-1'
    producer = Path(state['producer_output'])
    state['build_profile'] = dict(gradle_heap_bytes=6*1024**3, gradle_workers='original-default',
        gradle_parallel=True, kotlin_compiler='in-process', xcode_jobs='original-default',
        verbose_build=True, boot_after_build=False, build_timeout_seconds=2700,
        test_timeout_seconds=p.TEST_TIMEOUT_SECONDS, scope=p.SCOPE, combined_xcode=True,
        combined_timeout_seconds=p.COMBINED_TIMEOUT_SECONDS, product_manifest_stage='after-combined-command')
    row = state['arms'][0]
    test = next(c for c in state['commands'] if c['label'] == 'compose-tests')
    test.update(label='compose-build-and-tests', timeout_seconds=p.COMBINED_TIMEOUT_SECONDS,
        command=p.WRAPPER + p.test_command(['/usr/bin/xcodebuild', '-quiet', '-project', 'iosApp/iosApp.xcodeproj',
            '-scheme', 'iosApp', '-configuration', 'Debug', '-sdk', 'iphonesimulator',
            '-destination', 'platform=iOS Simulator,id='+row['simulator'], '-derivedDataPath', str(producer/'derived'),
            '-parallel-testing-enabled', 'NO', '-maximum-concurrent-test-simulator-destinations', '1',
            '-destination-timeout', '120'], producer/'compose/tests.xcresult'))
    state['commands'] = [c for c in state['commands'] if c['label'] != 'compose-build']
    row.update(combined_exit=row['test_exit'], product_manifest_stage='after-combined-command',
               build_success_evidence='all23-executed-native-outcomes')
    boot = copy.deepcopy(test)
    boot.update(label='simctl-bootstatus', exit_code=0, timeout_seconds=120,
        command=['/usr/bin/xcrun', 'simctl', 'bootstatus', row['simulator'], '-b'])
    state['commands'].insert(0, boot)
    row['kernel_resources'] = {}
    for stage in p.STAGES:
        value = dict(page_size=16384, pages={k:0 for k in p.resources.VM_FIELDS},
                     swap_mib=[0,0,0], physical_bytes=7*1024**3, logical_cpus=3)
        raw = json.dumps(value).encode();(out/'compose'/('kernel-'+stage+'.json')).write_bytes(raw)
        row['kernel_resources'][stage] = hashlib.sha256(raw).hexdigest()
    raws, inputs = aggregate_seed.reports()
    state['original_inputs'].update(inputs)
    (out/'compose/inputs.json').write_text(json.dumps(p.load(out/'compose/inputs.json') | inputs))
    for name, raw in raws.items():
        target=out/'aggregate/xml'/name;target.parent.mkdir(parents=True, exist_ok=True);target.write_bytes(raw)
    prelude = dict(original_uninstrumented_inputs=True, result='ORIGINAL774_EXECUTED_NOT_QUALIFICATION',
        exit_code=0, stop_exit=0, cleanup='PASS', workers=dict(result='PASS'),
        reports=p.aggregate.verify_prelude_xml(raws,state['original_inputs']),
        retained_reports={name:hashlib.sha256(raw).hexdigest() for name,raw in raws.items()})
    for stage in ('before','after'):
        raw=b'[]';(out/'aggregate'/('simulators-'+stage+'.json')).write_bytes(raw)
        prelude['simulators_'+stage]=hashlib.sha256(raw).hexdigest()
    state['aggregate_prelude']=prelude
    state['commands'] = [dict(label='original-kmp-aggregate',command=p.WRAPPER+p.aggregate.PRELUDE_COMMAND,
        exit_code=0,timeout_seconds=p.aggregate.PRELUDE_TIMEOUT,status='EXITED',direct_child_reaped=True),
        dict(label='original-kmp-stop',command=['/owned/gradlew','--stop'],exit_code=0,timeout_seconds=120,
             status='EXITED',direct_child_reaped=True)] + state['commands']
    return state


class OriginalFullContextTests(unittest.TestCase):
    def test_entire_app_driver_native_plan_and_assertion_budgets_unchanged(self):
        self.assertIs(p.renderer_for_profile, seed.p.renderer_for_profile)
        self.assertEqual(p.CASES, seed.p.CASES)
        self.assertEqual(p.TEST_TIMEOUT_SECONDS, seed.p.TEST_TIMEOUT_SECONDS)
        self.assertEqual(p.COMBINED_TIMEOUT_SECONDS,3600)
        args=['/usr/bin/xcodebuild','-quiet']
        expected=seed.p.test_command(args,Path('/result'))
        self.assertEqual(p.test_command(args,Path('/result')),
            ['test' if v=='test-without-building' else v for v in expected])

    def test_actual_full_context_admits_original_pass_or_failure_without_rewriting_commands(self):
        for failed in ((),(p.CASES[4],)):
            with self.subTest(failed=failed),tempfile.TemporaryDirectory() as tmp:
                out=Path(tmp).resolve();state=evidence(out,failed);original=copy.deepcopy(state)
                self.assertEqual(p.accept_native_export(out,state),p.RESULT)
                self.assertEqual(state,original)
                binding=p.load(out/'original-full-context-binding.json')
                self.assertTrue(binding['kmp_prelude_matched'])
                self.assertTrue(binding['combined_xcode_matched'])
                self.assertFalse(binding['application_root_cause'])
                self.assertFalse(binding['shipping_qualified'])

    def test_missing_replayed_mutated_or_later_prelude_and_incomplete_reports_reject(self):
        changes=(
            lambda o,s:s['commands'].pop(0),
            lambda o,s:s['commands'].append(copy.deepcopy(s['commands'][0])),
            lambda o,s:s['commands'].append(s['commands'].pop(0)),
            lambda o,s:s['commands'][0]['command'].append('--tests=*Foo'),
            lambda o,s:s['aggregate_prelude'].update(cleanup='FAIL'),
            lambda o,s:s['aggregate_prelude']['reports'].pop(),
            lambda o,s:(o/'aggregate/xml'/p.aggregate.case_manifest()['classes'][0]['path']).write_text('<testsuite/>'),
        )
        self.reject(changes)

    def test_wrong_actual_paths_standalone_receipts_caps_skips_and_longer_deadlines_reject(self):
        def combined(s):return next(c for c in s['commands'] if c['label']=='compose-build-and-tests')
        changes=(
            lambda o,s:combined(s)['command'].append('-retry-tests-on-failure'),
            lambda o,s:combined(s)['command'].append('-skip-testing:Foo'),
            lambda o,s:combined(s).update(timeout_seconds=3601),
            lambda o,s:combined(s).update(label='compose-tests'),
            lambda o,s:combined(s)['command'].__setitem__(combined(s)['command'].index('-derivedDataPath')+1,'/unowned/derived'),
            lambda o,s:s.update(producer_output=str(o)),
            lambda o,s:s['task'].update(GITHUB_RUN_ATTEMPT='2'),
            lambda o,s:s['build_profile'].update(gradle_heap_bytes=3*1024**3),
            lambda o,s:s['arms'][0].update(cleanup='FAIL'),
            lambda o,s:s['arms'][0]['kernel_resources'].pop('before-build'),
        )
        self.reject(changes)

    def reject(self, changes):
        original=p.prior.native.prior.verify_commands
        for change in changes:
            with self.subTest(change=change),tempfile.TemporaryDirectory() as tmp:
                out=Path(tmp).resolve();state=evidence(out,(p.CASES[4],));change(out,state)
                with self.assertRaises((ValueError,StopIteration)):p.accept_native_export(out,state)
                self.assertFalse((out/'original-full-context-binding.json').exists())
                self.assertIs(p.prior.native.prior.verify_commands,original)

    def test_execution_modifiers_do_not_select_absent_native_root_collectors(self):
        tree=ast.parse(Path(run_pin03.__file__).read_text())
        scope=dict(combined_execution=False,original_full_context=True,aggregate_context=False,
            original_resources=False,original_build_resources=False,accessibility=False,native_boundary=True,
            root_capability=False,root_boundary=False,public_boundary=False,original_cohort=False)
        for name in ('combined_context','prelude_context','resource_context'):
            value=next(n.value for n in ast.walk(tree) if isinstance(n,ast.Assign) and
                any(isinstance(t,ast.Name) and t.id==name for t in n.targets))
            scope[name]=eval(compile(ast.Expression(value),'<execution>','eval'),{},scope)
        self.assertTrue(scope['combined_context']);self.assertTrue(scope['prelude_context'])
        for n in ast.walk(tree):
            if isinstance(n,ast.If) and isinstance(n.test,ast.BoolOp) and 'root_capability' in ast.unparse(n.test) and 'original_resources' in ast.unparse(n.test):
                actual=eval(compile(ast.Expression(n.test),'<collector>','eval'),{},scope)
                self.assertEqual(actual,'native_boundary' in ast.unparse(n.test))
        self.assertFalse(hasattr(p,'retain_root_companion'))
        with self.assertRaisesRegex(ValueError,'explicitly bound'):run_pin03.main(original_full_context=True)
        with self.assertRaises(ValueError):run_pin03.main(failure_runloop=True,original_full_context=True,original_build_resources=True)

    def test_command_admission_hook_restores_even_on_failure(self):
        original=p.prior.native.prior.verify_commands
        with self.assertRaises(RuntimeError):
            with p.combined_admission():
                self.assertIs(p.prior.native.prior.verify_commands,p.verify_commands)
                raise RuntimeError('retained failure')
        self.assertIs(p.prior.native.prior.verify_commands,original)


if __name__ == '__main__':unittest.main()
