import unittest
from scripts.release import workflow_contract as c


class NativeStackWorkflowControls(unittest.TestCase):
    def test_one_free_runner_same_original_source_pair_with_bounded_host_only_observation(self):
        text = (c.WORKFLOWS/c.NATIVE_STACK_BOUNDARY).read_text()
        c.verify_native_stack_boundary(text)
        self.assertEqual(text.count('runs-on: macos-15'), 1)
        for token in ('java-version: "21"', 'run_pin49.py', 'test_pin49_native_stacks.py',
                      'compose/native-stacks', 'compose/tests.xcresult', 'compose/attachments'):
            self.assertIn(token, text)
        for forbidden in ('sudo', '--all-processes', 'actions/cache/save', '-retry-tests', '-skip-testing',
                          'productionCheck', 'run_pin45.py', 'run_pin39.py'):
            self.assertNotIn(forbidden, text)

    def test_privileges_paid_runner_source_budget_or_lost_evidence_reject(self):
        text = (c.WORKFLOWS/c.NATIVE_STACK_BOUNDARY).read_text()
        for before, after in (('macos-15', 'macos-15-large'), ('contents: read', 'contents: write'),
                              ('timeout-minutes: 90', 'timeout-minutes: 240'), ('java-version: "21"', 'java-version: "17"'),
                              ('compose/native-stacks', 'compose/absent'), ('iPhone-17-Pro', 'iPhone-16-Pro'),
                              ('5029b3f48c2d012b34dd2748f809dc775276a622', '0'*40)):
            with self.subTest(after=after), self.assertRaises(RuntimeError):
                c.verify_native_stack_boundary(text.replace(before, after))
