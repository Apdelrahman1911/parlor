import unittest
from scripts.release import workflow_contract as c


class MainRunLoopWorkflowControls(unittest.TestCase):
    def test_free_focused_native_control_once_preserves_failure_artifacts(self):
        text=(c.WORKFLOWS/c.MAIN_RUNLOOP_CAPABILITY).read_text();c.verify_main_runloop_capability(text)
        self.assertEqual(text.count('runs-on: macos-15'),1)
        self.assertNotIn('gradlew',text);self.assertNotIn('cache/',text)
        self.assertIn('-p test_pin46_main_runloop.py',text)
        for member in ('state.json','main.swift','compiler.log','control.stdout','control.stderr'):
            self.assertIn('/'+member,text)

    def test_paid_runner_repeated_control_removed_evidence_and_production_trigger_reject(self):
        text=(c.WORKFLOWS/c.MAIN_RUNLOOP_CAPABILITY).read_text()
        for before,after in (('macos-15','macos-15-large'),('-p test_pin46_main_runloop.py','-p test_pin45_original_runtime_context.py'),
            ('/control.stdout','/**'),('retention-days: 14','retention-days: 1'),
            ('diagnostic/ios-main-runloop-boundary-2026-11-01','main')):
            with self.subTest(after=after),self.assertRaises(RuntimeError):c.verify_main_runloop_capability(text.replace(before,after))
