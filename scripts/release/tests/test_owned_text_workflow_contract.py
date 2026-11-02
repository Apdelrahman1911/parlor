import unittest
from pathlib import Path
from unittest.mock import patch

from scripts.release import workflow_contract as c


class OwnedTextWorkflowControls(unittest.TestCase):
    def test_exact_free_native_control_not_original_replay_or_qualification(self):
        source=(c.WORKFLOWS/c.OWNED_TEXT_BOUNDARY).read_text()
        c.verify_owned_text_boundary(source)
        for forbidden in ('secrets.','continue-on-error','run_pin50.py','run_pin49.py','macos-15-large','cache/save','productionCheck'):
            self.assertNotIn(forbidden,source)

    def test_mutated_runner_source_exports_or_deadlines_reject(self):
        source=(c.WORKFLOWS/c.OWNED_TEXT_BOUNDARY).read_text()
        for before,after in (('macos-15','macos-15-large'),('contents: read','contents: write'),
                ('timeout-minutes: 90','timeout-minutes: 120'),('run_pin51.py','run_pin50.py'),
                ('5029b3f48c2d012b34dd2748f809dc775276a622','706e0ecb8766bb8de1671f64e2250c7a53b0bf9c'),
                ('if: always()','if: success()'),('/compose/*.json','/source/**')):
            with self.subTest(after=after),self.assertRaises(RuntimeError):c.verify_owned_text_boundary(source.replace(before,after))

    def test_changed_or_symlinked_companion_rejects(self):
        source=(c.WORKFLOWS/c.OWNED_TEXT_BOUNDARY).read_text()
        original=Path.read_bytes
        for name in c.OWNED_TEXT_BOUNDARY_COMPANIONS:
            target=c.ROOT/'scripts/verification/ios-input-boundaries'/name
            def changed(path):return original(path)+b'changed' if path==target else original(path)
            with self.subTest(name=name),patch.object(Path,'read_bytes',changed),self.assertRaises(RuntimeError):c.verify_owned_text_boundary(source)
