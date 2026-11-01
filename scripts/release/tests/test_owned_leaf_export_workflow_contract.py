import unittest
from scripts.release import workflow_contract as c


class OwnedLeafWorkflowControls(unittest.TestCase):
    def test_exact_free_failed_only_new_capability(self):
        text=(c.WORKFLOWS/c.OWNED_LEAF_EXPORT).read_text()
        c.verify_owned_leaf_export(text)
        self.assertIn('runs-on: macos-15',text)
        self.assertNotIn('cache/save',text)
        self.assertIn('run_pin42.py',text)
        self.assertIn('/compose/tests.xcresult',text)
        self.assertIn('/compose/attachments',text)
        self.assertNotIn('--retry',text)

    def test_paid_runner_retries_wrong_source_and_missing_evidence_reject(self):
        text=(c.WORKFLOWS/c.OWNED_LEAF_EXPORT).read_text()
        for before,after in (('macos-15','macos-15-large'),('run_pin42.py','run_pin41.py'),
            ('--profile isolated-owned-leaf-export','--profile isolated-owned-leaf-export --retry-tests-on-failure'),
            ('retention-days: 14','retention-days: 1'),('/compose/attachments','/**'),
            ('diagnostic/ios-owned-leaf-export-2026-11-01','main'),
            ('5029b3f48c2d012b34dd2748f809dc775276a622','706e0ecb8766bb8de1671f64e2250c7a53b0bf9c')):
            with self.subTest(after=after),self.assertRaises(RuntimeError):c.verify_owned_leaf_export(text.replace(before,after))
