import unittest
from scripts.release import workflow_contract as c

class FailureRunLoopWorkflowControls(unittest.TestCase):
    def test_free_focused_original_methods_new_control_once_and_original_failure_evidence(self):
        text=(c.WORKFLOWS/c.FAILURE_RUNLOOP).read_text();c.verify_failure_runloop(text)
        self.assertEqual(text.count('runs-on: macos-15'),1)
        self.assertIn('-p test_pin47_failure_runloop.py',text)
        self.assertNotIn('gradlew',text);self.assertNotIn('actions/cache/save',text)
        for member in ('state.json','failure-runloop.json','compose/tests.xcresult','compose/attachments'):self.assertIn('/'+member,text)

    def test_paid_runner_retries_wrong_source_unbounded_export_or_removed_failure_custody_reject(self):
        text=(c.WORKFLOWS/c.FAILURE_RUNLOOP).read_text()
        for before,after in (('macos-15','macos-15-large'),('-p test_pin47_failure_runloop.py','-p test_pin46_main_runloop.py'),
            ('--source 5029b3f48c2d012b34dd2748f809dc775276a622','--source main'),('compose/*.json','/**'),
            ('retention-days: 14','retention-days: 1'),('diagnostic/ios-failure-runloop-2026-11-01','main')):
            with self.subTest(after=after),self.assertRaises(RuntimeError):c.verify_failure_runloop(text.replace(before,after))
