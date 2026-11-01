import unittest
from scripts.release import workflow_contract as c


class OriginalRuntimeLeafWorkflowControls(unittest.TestCase):
    def test_exact_original_runtime_free_workflow_retains_all_failure_evidence(self):
        text = (c.WORKFLOWS / c.ORIGINAL_RUNTIME_LEAF).read_text()
        c.verify_original_runtime_leaf(text)
        self.assertEqual(text.count('runs-on: macos-15'), 1)
        self.assertNotIn('matrix:', text)
        self.assertNotIn('cache/save', text)
        self.assertNotIn('--arm', text)
        self.assertIn('run_pin44.py', text)
        for member in ('*.json', '*.log', 'tests.xcresult', 'attachments'):
            self.assertIn('/compose/' + member, text)
        self.assertIn('/original-runtime-journeys.json', text)

    def test_paid_runner_retries_old_runtime_wrong_source_and_missing_evidence_reject(self):
        text = (c.WORKFLOWS / c.ORIGINAL_RUNTIME_LEAF).read_text()
        for before, after in (
            ('macos-15', 'macos-15-large'),
            ('run_pin44.py', 'run_pin43.py'),
            ('--profile original-runtime-owned-leaf-public-journeys', '--profile original-runtime-owned-leaf-public-journeys --retry-tests-on-failure'),
            ('retention-days: 14', 'retention-days: 1'),
            ('/compose/attachments', '/**'),
            ('/compose/tests.xcresult', '/original/tests.xcresult'),
            ('diagnostic/ios-original-runtime-owned-leaf-2026-11-01', 'main'),
            ('com.apple.CoreSimulator.SimRuntime.iOS-26-2', 'com.apple.CoreSimulator.SimRuntime.iOS-18-5'),
            ('com.apple.CoreSimulator.SimDeviceType.iPhone-17-Pro', 'com.apple.CoreSimulator.SimDeviceType.iPhone-16-Pro'),
            ('5029b3f48c2d012b34dd2748f809dc775276a622', '706e0ecb8766bb8de1671f64e2250c7a53b0bf9c'),
        ):
            with self.subTest(after=after), self.assertRaises(RuntimeError):
                c.verify_original_runtime_leaf(text.replace(before, after))
