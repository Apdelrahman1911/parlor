import unittest
from pathlib import Path
from unittest.mock import patch
from scripts.release import workflow_contract as c


class BootedProfilerWorkflowControls(unittest.TestCase):
    def test_exact_free_owned_boot_and_unmodified_profiler_scope(self):
        text=(c.WORKFLOWS/c.PROFILER_BOOTED_READINESS).read_text();c.verify_profiler_booted_readiness(text)
        for token in ('gradlew','xcodebuild','secrets.','sudo','actions/cache','*.trace','/owned.trace'):
            self.assertNotIn(token,text)

    def test_changed_runner_privilege_budget_execution_or_upload_scope_rejected(self):
        text=(c.WORKFLOWS/c.PROFILER_BOOTED_READINESS).read_text()
        for a,b in (('macos-15','macos-15-large'),('contents: read','contents: write'),('timeout-minutes: 10','timeout-minutes: 30'),
                    ('profiler_booted_readiness.py','run_pin50.py'),('if: always()','if: success()'),('/profiler/toc.xml','/profiler/owned.trace')):
            with self.subTest(b=b),self.assertRaises(RuntimeError):c.verify_profiler_booted_readiness(text.replace(a,b))

    def test_changed_companion_never_admitted(self):
        text=(c.WORKFLOWS/c.PROFILER_BOOTED_READINESS).read_text()
        for name in c.PROFILER_BOOTED_READINESS_COMPANIONS:
            target=c.ROOT/'scripts/verification/ios-input-boundaries'/name;original=Path.read_bytes
            def changed(p):return original(p)+b'\n' if p==target else original(p)
            with patch.object(Path,'read_bytes',changed),self.assertRaises(RuntimeError):c.verify_profiler_booted_readiness(text)
