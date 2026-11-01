import unittest
from scripts.release import workflow_contract as c


class OwnedLeafPairWorkflowControls(unittest.TestCase):
    def test_exact_free_same_runner_comparison_retains_both_original_outcomes(self):
        text = (c.WORKFLOWS / c.OWNED_LEAF_PAIR).read_text()
        c.verify_owned_leaf_pair(text)
        self.assertEqual(text.count('runs-on: macos-15'), 1)
        self.assertNotIn('matrix:', text)
        self.assertNotIn('cache/save', text)
        self.assertIn('run_pin43.py', text)
        for arm in ('original', 'compose'):
            for member in ('*.json', '*.log', 'tests.xcresult', 'attachments'):
                self.assertIn('/' + arm + '/' + member, text)
        self.assertIn('/owned-leaf-observed-samples.json', text)
        self.assertIn('/owned-leaf-pair.json', text)

    def test_paid_runner_retries_wrong_source_and_missing_arm_evidence_reject(self):
        text = (c.WORKFLOWS / c.OWNED_LEAF_PAIR).read_text()
        for before, after in (
            ('macos-15', 'macos-15-large'),
            ('run_pin43.py', 'run_pin42.py'),
            ('--profile fixed-original-owned-leaf-pair', '--profile fixed-original-owned-leaf-pair --retry-tests-on-failure'),
            ('retention-days: 14', 'retention-days: 1'),
            ('/original/attachments', '/**'),
            ('/compose/tests.xcresult', '/original/tests.xcresult'),
            ('diagnostic/ios-owned-leaf-pair-2026-11-01', 'main'),
            ('5029b3f48c2d012b34dd2748f809dc775276a622', '706e0ecb8766bb8de1671f64e2250c7a53b0bf9c'),
        ):
            with self.subTest(after=after), self.assertRaises(RuntimeError):
                c.verify_owned_leaf_pair(text.replace(before, after))
