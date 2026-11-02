import unittest
from pathlib import Path
from unittest.mock import patch

from scripts.release import workflow_contract as c


class AppSpindumpWorkflowControls(unittest.TestCase):
    def test_exact_free_original_pair_and_private_exclusion(self):
        text=(c.WORKFLOWS/c.APP_SPINDUMP_BOUNDARY).read_text()
        c.verify_app_spindump_boundary(text)
        for forbidden in ('secrets.','private-native-captures','*.opaque-output','continue-on-error','test_pin49_native_stacks.py'):
            self.assertNotIn(forbidden,text)

    def test_runner_privilege_sources_original_budget_or_artifact_mutation_rejects(self):
        text=(c.WORKFLOWS/c.APP_SPINDUMP_BOUNDARY).read_text()
        for before,after in (('macos-15','macos-15-large'),('contents: read','contents: write'),('timeout-minutes: 90','timeout-minutes: 120'),
                             ('5029b3f48c2d012b34dd2748f809dc775276a622','706e0ecb8766bb8de1671f64e2250c7a53b0bf9c'),
                             ('run_pin50.py','run_pin49.py'),('if: always()','if: success()'),
                             ('/compose/native-stacks','/private-native-captures')):
            with self.subTest(after=after),self.assertRaises(RuntimeError):c.verify_app_spindump_boundary(text.replace(before,after))

    def test_changed_or_redirected_adapter_is_not_admitted(self):
        text=(c.WORKFLOWS/c.APP_SPINDUMP_BOUNDARY).read_text()
        for name in c.APP_SPINDUMP_BOUNDARY_COMPANIONS:
            target=c.ROOT/'scripts/verification/ios-input-boundaries'/name
            original=Path.read_bytes
            def changed(path):return original(path)+b'\n' if path==target else original(path)
            with self.subTest(name=name),patch.object(Path,'read_bytes',changed),self.assertRaises(RuntimeError):c.verify_app_spindump_boundary(text)
