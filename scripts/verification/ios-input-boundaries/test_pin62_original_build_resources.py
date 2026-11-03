"""Changed resource-context controls; no repetition of successful native probes."""
import ast
import copy
import hashlib
import json
from pathlib import Path
import tempfile
import unittest

import pin62_original_build_resources as p
import run_pin03
import test_pin61_original_185 as seed


def fixture(out, failed=()):
    state = seed.evidence(out, failed)
    state['build_profile'] = dict(gradle_heap_bytes=6*1024**3, gradle_workers='original-default',
        gradle_parallel=True, kotlin_compiler='in-process', xcode_jobs='original-default',
        verbose_build=True, boot_after_build=False, build_timeout_seconds=2700,
        test_timeout_seconds=p.TEST_TIMEOUT_SECONDS, scope=p.SCOPE)
    row = state['arms'][0]
    build = next(c for c in state['commands'] if c['label']=='compose-build')
    build['command'] = ['/bin/bash', '-c', 'exec "$@" > "$PARLOR_INPUT_LOG" 2>&1', '--',
        *p.build_command(['/usr/bin/xcodebuild', '-quiet', '-project', 'iosApp/iosApp.xcodeproj',
            '-scheme', 'iosApp', '-configuration', 'Debug', '-sdk', 'iphonesimulator',
            '-destination', 'platform=iOS Simulator,id='+row['simulator'],
            '-derivedDataPath', str(out/'derived'), '-parallel-testing-enabled', 'NO',
            '-maximum-concurrent-test-simulator-destinations', '1'])]
    boot = copy.deepcopy(build)
    boot.update(label='simctl-bootstatus', command=['/usr/bin/xcrun','simctl','bootstatus',row['simulator'],'-b'])
    state['commands'].insert(0, boot)
    row['kernel_resources'] = {}
    for stage in p.resources.STAGES:
        value = dict(page_size=16384, pages={k:0 for k in p.resources.VM_FIELDS},
                     swap_mib=[0,0,0], physical_bytes=14*1024**3, logical_cpus=3)
        raw = json.dumps(value).encode()
        (out/'compose'/('kernel-'+stage+'.json')).write_bytes(raw)
        row['kernel_resources'][stage] = hashlib.sha256(raw).hexdigest()
    return state


class OriginalBuildResourcesTests(unittest.TestCase):
    def test_app_driver_observer_roster_and_assertion_budgets_are_unchanged(self):
        leaf = p.runtime.pair_control.leaf
        actual = p.renderer_for_profile(p.PROFILE,p.SOURCE,p.RUNTIME,p.DEVICE)(
            {name:leaf.pair.fixture(name) for name in leaf.pair.MODIFIED},leaf.pair.INPUTS,arm='compose')
        self.assertEqual(actual, seed.plan())
        self.assertEqual(p.CASES, seed.p.CASES)
        self.assertEqual(p.TEST_TIMEOUT_SECONDS, seed.p.TEST_TIMEOUT_SECONDS)

    def test_original_resources_remove_only_copy_build_caps_and_keep_owned_dirs(self):
        env = p.build_environment({'GRADLE_OPTS':'-Xmx3g'},Path('/owned/registry'),Path('/owned/tmp'))
        self.assertNotIn('-Xmx',env['GRADLE_OPTS'])
        self.assertNotIn('workers.max',env['GRADLE_OPTS'])
        self.assertIn('in-process',env['GRADLE_OPTS'])
        self.assertIn('/owned/registry',env['GRADLE_OPTS'])
        self.assertNotIn('-jobs',p.build_command(['/usr/bin/xcodebuild','-quiet']))

    def test_exact_changed_context_admits_actual_pass_or_original_failures(self):
        for failed in ((), (p.CASES[4],)):
            with self.subTest(failed=failed),tempfile.TemporaryDirectory() as tmp:
                out=Path(tmp).resolve();state=fixture(out,failed)
                self.assertEqual(p.accept_native_export(out,state),p.RESULT)
                receipt=p.load(out/'original-resource-binding.json')
                self.assertEqual(receipt['methods'],state['arms'][0]['methods'])
                self.assertFalse(receipt['shipping_qualified'])
                self.assertFalse(receipt['combined_xcode_matched'])

    def test_caps_reversed_boot_missing_or_mutated_metrics_and_prelude_claim_reject(self):
        mutations = (
            lambda o,s:s['build_profile'].update(gradle_heap_bytes=3*1024**3),
            lambda o,s:s['build_profile'].update(boot_after_build=True),
            lambda o,s:s['commands'].append(s['commands'].pop(0)),
            lambda o,s:s['arms'][0]['kernel_resources'].pop('before-build'),
            lambda o,s:(o/'compose/kernel-after-build.json').write_text('{}'),
            lambda o,s:s['commands'][0].update(label='original-kmp-aggregate'),
            lambda o,s:next(c for c in s['commands'] if c['label']=='compose-build')['command'].extend(['-jobs','1']),
        )
        for mutation in mutations:
            with self.subTest(mutation=mutation),tempfile.TemporaryDirectory() as tmp:
                out=Path(tmp).resolve();state=fixture(out,(p.CASES[4],));mutation(out,state)
                with self.assertRaises(ValueError):p.accept_native_export(out,state)
                self.assertFalse((out/'original-resource-binding.json').exists())

    def test_resource_execution_never_selects_absent_root_capture_side_channel(self):
        # Exercise the actual runner branch expressions, not only the reader fixture.
        source = Path(run_pin03.__file__).read_text()
        tree = ast.parse(source)
        assignments = [n for n in ast.walk(tree) if isinstance(n, ast.Assign)
            and any(isinstance(t, ast.Name) and t.id == 'resource_context' for t in n.targets)]
        self.assertEqual(len(assignments), 1)
        assignment = assignments[0]
        self.assertEqual(ast.unparse(assignment.value), 'original_resources or original_build_resources')
        scope = dict(original_resources=False, original_build_resources=True,
            combined_execution=False, accessibility=False, native_boundary=True,
            root_capability=False, root_boundary=False, public_boundary=False,
            original_cohort=False)
        scope['resource_context'] = eval(compile(ast.Expression(assignment.value), '<resource>', 'eval'), {}, scope)
        self.assertTrue(scope['resource_context'])
        self.assertFalse(any(isinstance(n, ast.Name) and n.id == 'resource_context'
            and isinstance(n.ctx, ast.Load) and n.lineno < assignment.lineno
            for n in ast.walk(tree)))
        capture_branches = [n for n in ast.walk(tree) if isinstance(n, ast.If)
            and isinstance(n.test, ast.BoolOp)
            and 'root_capability' in ast.unparse(n.test)
            and 'original_resources' in ast.unparse(n.test)]
        self.assertEqual(len(capture_branches), 4)
        for branch in capture_branches:
            expression = ast.unparse(branch.test)
            active = eval(compile(ast.Expression(branch.test), '<capture>', 'eval'), {}, scope)
            self.assertEqual(active, 'native_boundary' in expression)
        self.assertFalse(hasattr(p, 'retain_root_companion'))
        # The resource modifier must not mutate the older collector selection.
        self.assertFalse(any(isinstance(n, ast.Assign)
            and any(isinstance(t, ast.Name) and t.id == 'original_resources' for t in n.targets)
            for n in ast.walk(tree)))

    def test_resource_modifier_cannot_select_other_entrypoints(self):
        with self.assertRaisesRegex(ValueError,'explicitly bound'):
            run_pin03.main(original_build_resources=True)


if __name__ == '__main__':
    unittest.main()
