"""Native coverage controls remain free/read-only and cannot qualify Parlor."""
from pathlib import Path
import unittest
from unittest.mock import patch

from scripts.release import workflow_contract as contract


class PublicFailureCoverageWorkflowContractTest(unittest.TestCase):
    def test_exact_workflow_and_companions_accepted(self):
        contract.verify_public_failure_coverage_control(
            (contract.WORKFLOWS / contract.PUBLIC_FAILURE_COVERAGE_CONTROL).read_text())

    def test_runner_privilege_trigger_source_upload_and_failure_masking_reject(self):
        text = (contract.WORKFLOWS / contract.PUBLIC_FAILURE_COVERAGE_CONTROL).read_text()
        for before, after in (('macos-15', 'macos-15-large'), ('macos-15', 'self-hosted'),
                             ('contents: read', 'contents: write'), ('  push:', '  workflow_dispatch:'),
                             ('persist-credentials: false', 'persist-credentials: true'),
                             ('cancel-in-progress: false', 'cancel-in-progress: true'),
                             ('timeout-minutes: 10', 'timeout-minutes: 180'),
                             ('set -euo pipefail', 'set +e'), ('if: always()', 'if: success()'),
                             ('if-no-files-found: error', 'if-no-files-found: warn'),
                             ('test_pin11_public_failure_coverage -v', 'test_pin11_public_failure_coverage -v || true'),
                             ('"$(git rev-parse HEAD)" = "$GITHUB_SHA"', '"ignored" = "ignored"')):
            with self.subTest(before=before), self.assertRaises(RuntimeError):
                contract.verify_public_failure_coverage_control(text.replace(before, after))

    def test_changed_or_redirected_companions_reject(self):
        text = (contract.WORKFLOWS / contract.PUBLIC_FAILURE_COVERAGE_CONTROL).read_text()
        for name in contract.PUBLIC_FAILURE_COVERAGE_CONTROL_COMPANIONS:
            target = contract.ROOT / 'scripts/verification/ios-input-boundaries' / name
            read, link = Path.read_bytes, Path.is_symlink
            with patch.object(Path, 'read_bytes', lambda p: read(p) + b'\n' if p == target else read(p)):
                with self.assertRaises(RuntimeError):
                    contract.verify_public_failure_coverage_control(text)
            with patch.object(Path, 'is_symlink', lambda p: True if p == target else link(p)):
                with self.assertRaises(RuntimeError):
                    contract.verify_public_failure_coverage_control(text)


if __name__ == '__main__':
    unittest.main()
