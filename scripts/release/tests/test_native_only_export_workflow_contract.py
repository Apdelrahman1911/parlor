import unittest
from scripts.release import workflow_contract as c


class NativeOnlyWorkflowControls(unittest.TestCase):
    def test_exact_free_runner_capability_workflow_is_admitted(self):
        text = (c.WORKFLOWS/c.NATIVE_ONLY_EXPORT).read_text()
        c.verify_native_only_export(text)
        self.assertIn('runs-on: macos-15',text)
        self.assertNotIn('cache/save',text)
        self.assertNotIn('--arm',text)

    def test_paid_runner_production_trigger_retries_skips_and_missing_evidence_reject(self):
        text = (c.WORKFLOWS/c.NATIVE_ONLY_EXPORT).read_text()
        for before,after in (('macos-15','macos-15-large'),('run_pin39.py','run_pin03.py'),
            ('--profile isolated-native-only-export','--profile isolated-native-only-export --retry-tests-on-failure'),
            ('retention-days: 14','retention-days: 1'),('/compose/attachments','/**'),
            ('diagnostic/ios-native-only-export-2026-11-01','main')):
            with self.subTest(after=after),self.assertRaises(RuntimeError):
                c.verify_native_only_export(text.replace(before,after))
