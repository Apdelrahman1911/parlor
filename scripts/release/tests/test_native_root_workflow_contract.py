"""Exact free, read-only root diagnostics; no shipping or verification waiver."""
from pathlib import Path
import re
import unittest
from unittest.mock import patch

from scripts.release import workflow_contract as contract


class NativeRootWorkflowContractTest(unittest.TestCase):
    def profiles(self):
        return ((contract.NATIVE_ROOT_CAPABILITY,contract.NATIVE_ROOT_CAPABILITY_COMPANIONS,
                 contract.verify_native_root_capability),
                (contract.NATIVE_ROOT_OBSERVATIONS,contract.NATIVE_ROOT_OBSERVATIONS_COMPANIONS,
                 contract.verify_native_root_observations))

    def test_exact_free_readonly_root_workflows_accepted(self):
        for name,_,verify in self.profiles(): verify((contract.WORKFLOWS/name).read_text())

    def test_runner_privilege_source_upload_trigger_or_failure_masking_rejects(self):
        for name,_,verify in self.profiles():
            text=(contract.WORKFLOWS/name).read_text()
            budget = re.search(r'(?m)^    timeout-minutes: (\d+)$', text).group(1)
            for before,after in (('macos-15','macos-15-large'),('macos-15','self-hosted'),
                ('contents: read','contents: write'),('actions: read','actions: write'),
                ('  push:','  workflow_dispatch:'),('persist-credentials: false','persist-credentials: true'),
                ('cancel-in-progress: false','cancel-in-progress: true'),
                ('timeout-minutes: '+budget,'timeout-minutes: 180'),('if: always()','if: success()'),
                ('if-no-files-found: error','if-no-files-found: warn'),
                ('actions/cache/restore@','actions/cache/save@'),
                ('--source 5029b3f48c2d012b34dd2748f809dc775276a622','--source ${{ github.sha }}'),
                ('--output "$RUNNER_TEMP/$PARLOR_INPUT_OUTPUT"','--output "$RUNNER_TEMP/$PARLOR_INPUT_OUTPUT" || true')):
                changed = text.replace(before,after)
                self.assertNotEqual(changed, text, 'Each rejection must mutate the actual reviewed workflow')
                with self.subTest(name=name,before=before),self.assertRaises(RuntimeError):
                    verify(changed)

    def test_changed_or_redirected_companions_reject(self):
        for name,companions,verify in self.profiles():
            text=(contract.WORKFLOWS/name).read_text()
            for p in companions:
                target=contract.ROOT/'scripts/verification/ios-input-boundaries'/p
                original=Path.read_bytes
                with patch.object(Path,'read_bytes',lambda path:original(path)+b'\n' if path==target else original(path)):
                    with self.assertRaises(RuntimeError):verify(text)
                link=Path.is_symlink
                with patch.object(Path,'is_symlink',lambda path:True if path==target else link(path)):
                    with self.assertRaises(RuntimeError):verify(text)


if __name__=='__main__':
    unittest.main()
