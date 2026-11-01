import unittest
from scripts.release import workflow_contract as c


class OriginalInputOnlyWorkflowControls(unittest.TestCase):
    def test_exact_original_input_only_free_workflow_and_evidence_admitted(self):
        text=(c.WORKFLOWS/c.ORIGINAL_INPUT_ONLY).read_text()
        c.verify_original_input_only(text)
        self.assertIn('runs-on: macos-15',text)
        self.assertNotIn('cache/save',text)
        self.assertNotIn('run_pin39.py',text)
        self.assertNotIn('--arm',text)
        self.assertIn('/compose/tests.xcresult',text)
        self.assertIn('/compose/attachments',text)

    def test_wrong_source_paid_runner_retries_production_and_missing_evidence_reject(self):
        text=(c.WORKFLOWS/c.ORIGINAL_INPUT_ONLY).read_text()
        for before,after in (('macos-15','macos-15-large'),('run_pin41.py','run_pin40.py'),
            ('--profile original-input-only-samples','--profile original-input-only-samples --retry-tests-on-failure'),
            ('retention-days: 14','retention-days: 1'),('/compose/attachments','/**'),
            ('diagnostic/ios-original-input-only-2026-11-01','main'),
            ('5029b3f48c2d012b34dd2748f809dc775276a622','706e0ecb8766bb8de1671f64e2250c7a53b0bf9c')):
            with self.subTest(after=after),self.assertRaises(RuntimeError):c.verify_original_input_only(text.replace(before,after))
