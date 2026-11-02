import unittest
from pathlib import Path
from unittest.mock import patch
from scripts.release import workflow_contract as c


class OriginalOwnedTextWorkflowControls(unittest.TestCase):
    def test_exact_free_original_pair_not_shipping_qualification(self):
        source=(c.WORKFLOWS/c.ORIGINAL_OWNED_TEXT).read_text();c.verify_original_owned_text(source)
        for forbidden in ('secrets.','continue-on-error','run_pin51.py','macos-15-large','cache/save','productionCheck'):
            self.assertNotIn(forbidden,source)

    def test_mutated_runner_source_profile_exports_or_deadlines_reject(self):
        source=(c.WORKFLOWS/c.ORIGINAL_OWNED_TEXT).read_text()
        for before,after in (('macos-15','macos-15-large'),('contents: read','contents: write'),
                ('timeout-minutes: 90','timeout-minutes: 120'),('run_pin52.py','run_pin51.py'),
                ('5029b3f48c2d012b34dd2748f809dc775276a622','706e0ecb8766bb8de1671f64e2250c7a53b0bf9c'),
                ('original-owned-native-text-pair','isolated-owned-native-text-export'),
                ('if: always()','if: success()'),('/compose/*.json','/source/**')):
            with self.subTest(after=after),self.assertRaises(RuntimeError):c.verify_original_owned_text(source.replace(before,after))

    def test_changed_new_companion_or_actual_prerequisite_receipt_rejects(self):
        source=(c.WORKFLOWS/c.ORIGINAL_OWNED_TEXT).read_text();original=Path.read_bytes
        targets=[c.ROOT/'scripts/verification/ios-input-boundaries'/name for name in c.ORIGINAL_OWNED_TEXT_COMPANIONS]
        targets.append(c.ROOT/c.ORIGINAL_OWNED_TEXT_RECEIPT)
        for target in targets:
            def changed(path):return original(path)+b'changed' if path==target else original(path)
            with self.subTest(target=target),patch.object(Path,'read_bytes',changed),self.assertRaises(RuntimeError):c.verify_original_owned_text(source)
