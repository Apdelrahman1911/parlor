import unittest
from scripts.release import workflow_contract as c


class OriginalRuntimeContextWorkflowControls(unittest.TestCase):
    def test_exact_free_full_original_workload_and_all_evidence_admitted(self):
        text=(c.WORKFLOWS/c.ORIGINAL_RUNTIME_CONTEXT).read_text()
        c.verify_original_runtime_context(text)
        self.assertEqual(text.count('runs-on: macos-15'),1)
        self.assertNotIn('matrix:',text)
        self.assertNotIn('cache/save',text)
        self.assertNotIn('--arm',text)
        self.assertIn('run_pin45.py',text)
        for member in ('/aggregate/xml','/aggregate/*.json','/aggregate/*.log','/compose/tests.xcresult',
                       '/compose/attachments','/original-runtime-context.json','/original-runtime-context-native.json'):
            self.assertIn(member,text)

    def test_old_runtime_paid_runner_retries_wrong_source_and_missing_workload_evidence_reject(self):
        text=(c.WORKFLOWS/c.ORIGINAL_RUNTIME_CONTEXT).read_text()
        for before,after in (
            ('macos-15','macos-15-large'),('run_pin45.py','run_pin44.py'),
            ('--profile original-runtime-full-workload-context','--profile original-runtime-full-workload-context --retry-tests-on-failure'),
            ('retention-days: 14','retention-days: 1'),('/aggregate/xml','/**'),('/compose/attachments','/**'),
            ('diagnostic/ios-original-runtime-context-2026-11-01','main'),
            ('com.apple.CoreSimulator.SimRuntime.iOS-26-2','com.apple.CoreSimulator.SimRuntime.iOS-18-5'),
            ('5029b3f48c2d012b34dd2748f809dc775276a622','706e0ecb8766bb8de1671f64e2250c7a53b0bf9c'),
        ):
            with self.subTest(after=after),self.assertRaises(RuntimeError):c.verify_original_runtime_context(text.replace(before,after))
