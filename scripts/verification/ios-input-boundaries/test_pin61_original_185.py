"""New runtime consumer only; no replay of verified native capability probes."""
import copy
import hashlib
from pathlib import Path
import tempfile
import unittest

import pin61_original_185 as p
import test_pin60_original_suite as seed


def plan():
    leaf=p.runtime.pair_control.leaf
    return p.renderer_for_profile(p.PROFILE,p.SOURCE,p.RUNTIME,p.DEVICE)(
        {name:leaf.pair.fixture(name) for name in leaf.pair.MODIFIED},leaf.pair.INPUTS,arm='compose')


def evidence(out,failed=()):
    state=seed.evidence(out,failed)
    state.update(runtime=p.RUNTIME,device=p.DEVICE)
    for name in ('summary.json','tests.json'):
        value=p.load(out/'compose'/name)
        devices=([x['device'] for x in value['devicesAndConfigurations']]
                 if name=='summary.json' else value['devices'])
        for device in devices: device.update(modelName='iPhone 16 Pro',osVersion='18.5')
        (out/'compose'/name).write_text(__import__('json').dumps(value))
    return state


class Original185Tests(unittest.TestCase):
    def test_rendered_full_plan_selection_and_all_budgets_are_byte_identical(self):
        self.assertEqual(plan(),seed.plan())
        self.assertEqual(p.CASES,seed.p.CASES)
        self.assertEqual(len(p.CASES),23)
        self.assertEqual(p.TEST_TIMEOUT_SECONDS,seed.p.TEST_TIMEOUT_SECONDS)
        self.assertEqual(p.test_command(['/usr/bin/xcodebuild'],Path('/result')),
                         seed.p.test_command(['/usr/bin/xcodebuild'],Path('/result')))

    def test_source_runtime_and_device_are_exact_and_old_runtime_is_rejected(self):
        for os,device in ((seed.p.RUNTIME,seed.p.DEVICE),(p.RUNTIME,seed.p.DEVICE),(seed.p.RUNTIME,p.DEVICE)):
            with self.assertRaises(ValueError): p.renderer_for_profile(p.PROFILE,p.SOURCE,os,device)
        with self.assertRaises(ValueError): p.renderer_for_profile(p.PROFILE,'0'*40,p.RUNTIME,p.DEVICE)

    def test_complete18_5_outcomes_custody_and_original_failures_are_admitted(self):
        for failed in ((),(p.CASES[4],),(p.CASES[4],p.CASES[7])):
            with self.subTest(failed=failed),tempfile.TemporaryDirectory() as tmp:
                out=Path(tmp).resolve();state=evidence(out,failed)
                self.assertEqual(p.accept_native_export(out,state),p.RESULT)
                binding=p.load(out/'original-185-binding.json')
                self.assertEqual(binding['runtime'],p.RUNTIME)
                self.assertEqual(binding['methods'],state['arms'][0]['methods'])
                self.assertFalse(binding['app_fix'])
                self.assertFalse(binding['shipping_qualified'])

    def test_old26_2_raw_results_missing_duplicate_skipped_or_failed_cleanup_reject(self):
        for mutation in (
            lambda o,s:s.update(runtime=seed.p.RUNTIME,device=seed.p.DEVICE),
            lambda o,s:s['arms'][0].update(cleanup='FAIL'),
            lambda o,s:(o/'compose/summary.json').write_text(__import__('json').dumps(p.load(o/'compose/summary.json')|{'skippedTests':1})),
            lambda o,s:(o/'compose/test.log').write_text(seed.seed.seed.seed.starts(p.CASES[::-1])),
        ):
            with self.subTest(mutation=mutation),tempfile.TemporaryDirectory() as tmp:
                out=Path(tmp).resolve();state=evidence(out,(p.CASES[4],));mutation(out,state)
                with self.assertRaises(ValueError): p.accept_native_export(out,state)
                self.assertFalse((out/'original-185-binding.json').exists())
                self.assertEqual(p.prior.RUNTIME,p.ORIGINAL_RUNTIME)
                self.assertEqual(p.prior.prior.RUNTIME,p.ORIGINAL_RUNTIME)
                self.assertEqual(p.native.prior.RUNTIME,p.ORIGINAL_RUNTIME)

    def test_raw_runtime_is_never_normalized_or_relabelled_by_verifier(self):
        with tempfile.TemporaryDirectory() as tmp:
            out=Path(tmp).resolve();state=evidence(out)
            summary,tests=p.load(out/'compose/summary.json'),p.load(out/'compose/tests.json')
            original=copy.deepcopy(tests)
            p.verify_results(summary,tests,state['arms'][0]['simulator'],0)
            self.assertEqual(tests,original)
            tests['devices'][0]['osVersion']='26.2'
            with self.assertRaises(ValueError):p.verify_results(summary,tests,state['arms'][0]['simulator'],0)

    def test_standard_free_source_bound_workflow_and_unmodified_native_adapter(self):
        import sys
        sys.path.insert(0,str(Path(__file__).resolve().parents[3]))
        from scripts.release import workflow_contract as c
        text=(c.WORKFLOWS/'ios-original-suite-185.yml').read_text()
        self.assertEqual(hashlib.sha256(text.encode()).hexdigest(),c.ORIGINAL_185_WORKFLOW_SHA256)
        self.assertIn('--runtime '+p.RUNTIME,text)
        self.assertIn('--device '+p.DEVICE,text)
        self.assertIn('runs-on: macos-15',text)
        self.assertIn('persist-credentials: false',text)
        self.assertNotIn('actions/cache/save',text)
        self.assertNotIn('contents: write',text)
