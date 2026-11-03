"""Only changed selection; verified native probes are not replayed."""
from pathlib import Path
import unittest
from unittest.mock import patch
from scripts.release import workflow_contract as c


class OriginalSuiteWorkflowTests(unittest.TestCase):
    def test_exact_selection_reuses_all_existing_guards(self):
        c.verify_original_suite_input((c.WORKFLOWS / c.ORIGINAL_SUITE_INPUT).read_text())
        self.assertIn(c.ORIGINAL_SUITE_INPUT, c.load_files())

    def test_changed_runner_permissions_source_profile_or_retention_reject(self):
        source=(c.WORKFLOWS / c.ORIGINAL_SUITE_INPUT).read_text()
        for old,new in (
            ('runs-on: macos-15','runs-on: macos-15-large'),
            ('contents: read','contents: write'),
            ('--profile original-suite-verified-native-input','--profile retry-for-green'),
            ('iPhone-17-Pro','iPhone-16-Pro'),
            ('if: always()','if: success()'),
            ('5029b3f48c2d012b34dd2748f809dc775276a622','main'),
            ('test_pin60_original_suite.py','test_pin59_keyboard_sessions.py'),
        ):
            with self.subTest(new=new),self.assertRaises(RuntimeError):
                c.verify_original_suite_input(source.replace(old,new))

    def test_changed_companions_reject(self):
        original=Path.read_bytes;source=(c.WORKFLOWS/c.ORIGINAL_SUITE_INPUT).read_text()
        for selected in c.ORIGINAL_SUITE_INPUT_COMPANIONS:
            def changed(path,selected=selected):return b'changed' if path.name==selected else original(path)
            with self.subTest(selected=selected),patch.object(Path,'read_bytes',changed),self.assertRaises(RuntimeError):
                c.verify_original_suite_input(source)


if __name__=='__main__':unittest.main()
