import unittest
from scripts.release import workflow_contract as c


class PassiveRetainedWorkflowControls(unittest.TestCase):
    def test_free_artifact_only_validation_preserves_original_failed_evidence(self):
        text = (c.WORKFLOWS/c.PASSIVE_RETAINED_CORRECTION).read_text()
        c.verify_passive_retained_correction(text)
        self.assertEqual(text.count('runs-on: ubuntu-24.04'),1)
        for command in ('gradlew','xcodebuild','simctl','run_pin47.py','actions/cache'):
            self.assertNotIn(command,text)
        for member in ('original-artifact.zip','corrective-readback/state.json',
                       'corrected-pin47-validation.json','corrective-readback/corrected-diagnostic-observations.json'):
            self.assertIn('/'+member,text)

    def test_paid_runner_replay_permission_or_missing_original_custody_rejects(self):
        text = (c.WORKFLOWS/c.PASSIVE_RETAINED_CORRECTION).read_text()
        for before,after in (('ubuntu-24.04','ubuntu-latest-16-cores'),('timeout-minutes: 15','timeout-minutes: 120'),
                             ('contents: read','contents: write'),('original-artifact.zip','absent.zip'),
                             ('read_pin47_retained_correction.py','run_pin47.py'),
                             ('diagnostic/ios-passive-retained-correction-2026-11-01','main')):
            with self.subTest(after=after),self.assertRaises(RuntimeError):
                c.verify_passive_retained_correction(text.replace(before,after))
