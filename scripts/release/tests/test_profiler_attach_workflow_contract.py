import unittest
from pathlib import Path
from unittest.mock import patch

from scripts.release import workflow_contract as c


class ProfilerAttachWorkflowControls(unittest.TestCase):
    def test_exact_free_tool_only_profile(self):
        text = (c.WORKFLOWS/c.PROFILER_ATTACH_CONTROL).read_text()
        c.verify_profiler_attach_control(text)
        for token in ('xcodebuild', 'simctl', 'gradlew', 'secrets.', 'sudo', 'actions/cache'):
            self.assertNotIn(token, text)

    def test_privilege_scope_budget_or_evidence_mutation_rejects(self):
        text = (c.WORKFLOWS/c.PROFILER_ATTACH_CONTROL).read_text()
        for before, after in (('macos-15', 'macos-15-large'), ('contents: read', 'contents: write'),
                              ('timeout-minutes: 5', 'timeout-minutes: 90'),
                              ('if: always()', 'if: success()'),
                              ('test_profiler_attach_control.py', 'test_pin49_native_stacks.py'),
                              ('profiler_attach_control.py', 'run_pin49.py')):
            with self.subTest(after=after), self.assertRaises(RuntimeError):
                c.verify_profiler_attach_control(text.replace(before, after))

    def test_changed_or_redirected_companion_rejects(self):
        text = (c.WORKFLOWS/c.PROFILER_ATTACH_CONTROL).read_text()
        for name in c.PROFILER_ATTACH_CONTROL_COMPANIONS:
            target = c.ROOT/'scripts/verification/ios-input-boundaries'/name
            original = Path.read_bytes
            def changed(path):
                return original(path) + b'\n' if path == target else original(path)
            with self.subTest(name=name), patch.object(Path, 'read_bytes', changed), self.assertRaises(RuntimeError):
                c.verify_profiler_attach_control(text)
            symlink = Path.is_symlink
            def redirected(path):
                return path == target or symlink(path)
            with self.subTest(name=name), patch.object(Path, 'is_symlink', redirected), self.assertRaises(RuntimeError):
                c.verify_profiler_attach_control(text)
