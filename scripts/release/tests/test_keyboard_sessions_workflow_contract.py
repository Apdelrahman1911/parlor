"""Only the newly declared selection; do not rerun passed Apple controls."""
from pathlib import Path
import unittest
from unittest.mock import patch

from scripts.release import workflow_contract as c


class KeyboardSessionsWorkflowTests(unittest.TestCase):
    def test_exact_free_read_only_selection_preserves_original_adapter_chain(self):
        c.verify_keyboard_sessions((c.WORKFLOWS / c.KEYBOARD_SESSIONS).read_text())
        self.assertIn(c.KEYBOARD_SESSIONS, c.load_files())

    def test_runner_retries_source_architecture_and_evidence_changes_reject(self):
        source = (c.WORKFLOWS / c.KEYBOARD_SESSIONS).read_text()
        for old, new in (
            ('runs-on: macos-15', 'runs-on: macos-15-large'),
            ('contents: read', 'contents: write'),
            ('iPhone-17-Pro', 'iPhone-16-Pro'),
            ('--profile original-keyboard-session-focus-reproduction', '--profile retry-for-green'),
            ('if: always()', 'if: success()'),
            ('test_pin59_keyboard_sessions.py', 'test_pin58_native_ax_returns.py'),
            ('5029b3f48c2d012b34dd2748f809dc775276a622', 'main'),
        ):
            with self.subTest(new=new), self.assertRaises(RuntimeError):
                c.verify_keyboard_sessions(source.replace(old, new))

    def test_changed_selection_companion_or_receipt_rejects(self):
        source = (c.WORKFLOWS / c.KEYBOARD_SESSIONS).read_text()
        original = Path.read_bytes
        for selected in (*c.KEYBOARD_SESSIONS_COMPANIONS, Path(c.KEYBOARD_SESSIONS_RECEIPT).name):
            def changed(path, selected=selected):
                return b'changed' if path.name == selected else original(path)
            with self.subTest(selected=selected), patch.object(Path, 'read_bytes', changed), self.assertRaises(RuntimeError):
                c.verify_keyboard_sessions(source)


if __name__ == '__main__':
    unittest.main()
