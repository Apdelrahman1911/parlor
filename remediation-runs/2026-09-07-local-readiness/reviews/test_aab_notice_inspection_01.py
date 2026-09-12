"""Coordinator-only synthetic package-boundary tests, not Android execution."""
import importlib.util
import json
from pathlib import Path
from tempfile import TemporaryDirectory
from types import SimpleNamespace
import unittest
from unittest.mock import patch
import zipfile

CONTROL = Path(__file__).resolve().parents[1] / 'run_gradle_cycle.py'
SPEC = importlib.util.spec_from_file_location('aab_notice_cycle', CONTROL)
cycle = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(cycle)


class AabNoticeInspectionTest(unittest.TestCase):
    def setUp(self):
        allocation = TemporaryDirectory(prefix='parlor-aab-notice-inspection-test-')
        self.addCleanup(allocation.cleanup)
        self.root = Path(allocation.name).resolve()
        self.dest = self.root / 'evidence'
        self.dest.mkdir()
        self.app = self.root / 'composeApp/build/outputs/bundle/release/fixture.aab'
        self.app.parent.mkdir(parents=True)
        with zipfile.ZipFile(self.app, 'w') as output:
            output.writestr('base/dex/classes.dex', b'synthetic, not executable')

    def inspect(self, data, code=0):
        def command(args, **options):
            self.assertEqual(['/usr/bin/python3', '-B', str(self.root / 'scripts/verification/third_party_notices.py'),
                              '--root', str(self.root), '--package', str(self.app), '--json'], args)
            self.assertEqual(60, options['timeout'])
            options['stdout'].write(data)
            return SimpleNamespace(returncode=code)
        with patch.object(cycle, 'ROOT', self.root), patch.object(cycle.subprocess, 'run', side_effect=command) as run:
            cycle.inspect_generated_artifacts(self.dest)
            run.assert_called_once()

    def test_success_keeps_byte_bound_report_before_artifact_can_be_cleaned(self):
        self.inspect(json.dumps({'status': 'PASS', 'package': {'verified': 26}}).encode())
        records = json.loads((self.dest / 'artifact-receipts.json').read_text())
        self.assertEqual(1, len(records))
        report = self.dest / records[0]['notice_receipt']
        self.assertEqual(cycle.hashlib.sha256(report.read_bytes()).hexdigest(), records[0]['notice_receipt_sha256'])
        self.assertTrue(self.app.is_file())

    def test_failed_verifier_retains_the_artifact_and_failure_report(self):
        with self.assertRaisesRegex(RuntimeError, 'retain artifact'):
            self.inspect(b'{"status":"FAIL"}', code=2)
        self.assertTrue(self.app.is_file())
        self.assertEqual(b'{"status":"FAIL"}', (self.dest / 'artifacts/android-notices-1.json').read_bytes())

    def test_source_only_success_is_not_package_success(self):
        with self.assertRaisesRegex(RuntimeError, 'no successful package'):
            self.inspect(b'{"status":"PASS"}')
        self.assertTrue(self.app.is_file())

    def test_oversized_report_cannot_be_accepted(self):
        with self.assertRaisesRegex(RuntimeError, 'inspection failed'):
            self.inspect(b' ' * (1024 * 1024 + 1))
        self.assertTrue(self.app.is_file())


if __name__ == '__main__':
    unittest.main(verbosity=2)
