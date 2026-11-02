import unittest
from pathlib import Path
from unittest.mock import patch
from scripts.release import workflow_contract as c


class NativeAXReturnsWorkflowTests(unittest.TestCase):
    def test_exact_free_scope_keeps_failure_evidence_and_no_production_qualification(self):
        source=(c.WORKFLOWS/c.NATIVE_AX_RETURNS).read_text()
        c.verify_native_ax_returns(source)
        for forbidden in ('secrets.','continue-on-error','macos-15-large','cache/save','productionCheck','-retry-tests','pull_request:'):
            self.assertNotIn(forbidden,source)

    def test_runner_source_budgets_and_failure_retention_changes_reject(self):
        source=(c.WORKFLOWS/c.NATIVE_AX_RETURNS).read_text()
        for old,new in (('macos-15','macos-15-large'),('contents: read','contents: write'),
            ('timeout-minutes: 130','timeout-minutes: 160'),('run_pin58.py','run_pin57.py'),
            ('5029b3f48c2d012b34dd2748f809dc775276a622','706e0ecb8766bb8de1671f64e2250c7a53b0bf9c'),
            ('if: always()','if: success()'),('/compose/*.json','/source/**')):
            with self.subTest(new=new),self.assertRaises(RuntimeError):c.verify_native_ax_returns(source.replace(old,new))

    def test_all_new_companion_hashes_reject_changes(self):
        source=(c.WORKFLOWS/c.NATIVE_AX_RETURNS).read_text(); original=Path.read_bytes
        for name in c.NATIVE_AX_RETURNS_COMPANIONS:
            target=c.ROOT/'scripts/verification/ios-input-boundaries'/name
            def changed(path):return original(path)+b'changed' if path==target else original(path)
            with self.subTest(name=name),patch.object(Path,'read_bytes',changed),self.assertRaises(RuntimeError):c.verify_native_ax_returns(source)
