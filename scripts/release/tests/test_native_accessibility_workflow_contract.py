"""No paid runner, write privilege, arbitrary source or failure bypass."""
from pathlib import Path
import unittest
from unittest.mock import patch

from scripts.release import workflow_contract as contract


class NativeAccessibilityWorkflowContractTest(unittest.TestCase):
    def test_exact_workflow_and_companions_accepted(self):
        text = (contract.WORKFLOWS / contract.NATIVE_ACCESSIBILITY_CAPABILITY).read_text()
        contract.verify_native_accessibility_capability(text)

    def test_privilege_runner_source_trigger_capture_and_gate_substitutions_reject(self):
        text = (contract.WORKFLOWS / contract.NATIVE_ACCESSIBILITY_CAPABILITY).read_text()
        for old, new in (
            ('contents: read', 'contents: write'), ('actions: read', 'actions: write'),
            ('macos-15', 'macos-15-large'), ('macos-15', 'self-hosted'),
            ('timeout-minutes: 75', 'timeout-minutes: 180'),
            ('  push:', '  workflow_dispatch:'), ('persist-credentials: false', 'persist-credentials: true'),
            ('cancel-in-progress: false', 'cancel-in-progress: true'),
            ('--source 5029b3f48c2d012b34dd2748f809dc775276a622', '--source ${{ github.sha }}'),
            ('run_pin08.py', 'run_pin03.py'), ('/native/attachments', '/**'),
            ('if: always()', 'if: success()'), ('if-no-files-found: error', 'if-no-files-found: warn'),
            ('--profile synthetic-native-accessibility-capability', '--profile public-setup-pair'),
            ('actions/cache/restore@', 'actions/cache/save@'),
            ('--output "$RUNNER_TEMP/$PARLOR_INPUT_OUTPUT"', '--output "$RUNNER_TEMP/$PARLOR_INPUT_OUTPUT" || true'),
        ):
            self.assertIn(old, text)
            with self.subTest(old=old), self.assertRaises(RuntimeError):
                contract.verify_native_accessibility_capability(text.replace(old, new))

    def test_altered_or_redirected_companions_reject(self):
        text = (contract.WORKFLOWS / contract.NATIVE_ACCESSIBILITY_CAPABILITY).read_text()
        for name in contract.NATIVE_ACCESSIBILITY_CAPABILITY_COMPANIONS:
            target = contract.ROOT / 'scripts/verification/ios-input-boundaries' / name
            original = Path.read_bytes
            with patch.object(Path, 'read_bytes', lambda p: original(p) + b'\n' if p == target else original(p)):
                with self.subTest(name=name), self.assertRaises(RuntimeError):
                    contract.verify_native_accessibility_capability(text)
            original_link = Path.is_symlink
            with patch.object(Path, 'is_symlink', lambda p: True if p == target else original_link(p)):
                with self.subTest(name=name), self.assertRaises(RuntimeError):
                    contract.verify_native_accessibility_capability(text)


if __name__ == '__main__':
    unittest.main()
