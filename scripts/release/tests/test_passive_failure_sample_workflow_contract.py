import unittest
from scripts.release import workflow_contract as c

class PassiveSampleWorkflowControls(unittest.TestCase):
    def test_fixed_free_source_specific_sampling_is_not_application_qualification(self):
        text=(c.WORKFLOWS/c.PASSIVE_FAILURE_SAMPLES).read_text();c.verify_passive_failure_samples(text)
        self.assertEqual(text.count('runs-on: macos-15'),1)
        self.assertIn('-p test_pin47_passive_samples.py',text)
        self.assertIn('--profile failure-owned-main-runloop-fixed-samples',text)
        self.assertNotIn('actions/cache/save',text);self.assertNotIn('gradlew',text)
        for member in ('state.json','failure-runloop.json','compose/tests.xcresult','compose/attachments'):self.assertIn('/'+member,text)

    def test_paid_retry_skipped_control_changed_source_budget_or_missing_failure_evidence_rejects(self):
        text=(c.WORKFLOWS/c.PASSIVE_FAILURE_SAMPLES).read_text()
        for before,after in (('macos-15','macos-15-large'),('timeout-minutes: 120','timeout-minutes: 150'),
            ('--source 5029b3f48c2d012b34dd2748f809dc775276a622','--source main'),('compose/*.json','/**'),
            ('-p test_pin47_passive_samples.py','-p missing.py'),('--profile failure-owned-main-runloop-fixed-samples','--profile failure-owned-main-runloop-export-control'),
            ('diagnostic/ios-passive-failure-samples-2026-11-01','main'),('retention-days: 14','retention-days: 1')):
            with self.subTest(after=after),self.assertRaises(RuntimeError):c.verify_passive_failure_samples(text.replace(before,after))
