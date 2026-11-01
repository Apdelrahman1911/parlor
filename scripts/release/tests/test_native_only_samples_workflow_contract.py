import unittest
from scripts.release import workflow_contract as c


class NativeOnlySamplesWorkflowControls(unittest.TestCase):
    def test_exact_finite_free_native_only_samples_admitted_without_replaying_capability(self):
        text = (c.WORKFLOWS/c.NATIVE_ONLY_SAMPLES).read_text()
        c.verify_native_only_samples(text)
        self.assertIn('runs-on: macos-15',text)
        self.assertNotIn('cache/save',text)
        self.assertNotIn('--arm',text)
        self.assertNotIn('run_pin39.py',text)
        self.assertIn('/compose/tests.xcresult',text)
        self.assertIn('/compose/attachments',text)

    def test_paid_runner_production_trigger_retries_missing_evidence_and_wrong_source_reject(self):
        text = (c.WORKFLOWS/c.NATIVE_ONLY_SAMPLES).read_text()
        for before,after in (('macos-15','macos-15-large'),('run_pin40.py','run_pin39.py'),
            ('--profile fixed-native-only-public-samples','--profile fixed-native-only-public-samples --retry-tests-on-failure'),
            ('retention-days: 14','retention-days: 1'),('/compose/attachments','/**'),
            ('diagnostic/ios-native-only-samples-2026-11-01','main'),
            ('5029b3f48c2d012b34dd2748f809dc775276a622','706e0ecb8766bb8de1671f64e2250c7a53b0bf9c')):
            with self.subTest(after=after),self.assertRaises(RuntimeError):
                c.verify_native_only_samples(text.replace(before,after))
