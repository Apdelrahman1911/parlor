from __future__ import annotations

import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

from scripts.release import workflow_contract as contract


class PublicTestingWorkflowTest(unittest.TestCase):
    def setUp(self):
        self.workflow = (contract.ROOT / ".github/workflows/github-test-release.yml").read_text()

    def test_testing_is_explicit_and_preserves_the_signed_and_store_workflows(self):
        contract.verify_public_testing(self.workflow)
        contract.verify_github_distribution((contract.ROOT / ".github/workflows/github-distribution.yml").read_text())
        self.assertNotIn("github-test-release.yml", contract.STORE_WORKFLOWS)
        for name in contract.STORE_WORKFLOWS:
            contract.verify_store_workflow(name, (contract.ROOT / ".github/workflows" / name).read_text())

    def test_mutated_authority_build_signing_and_custody_controls_fail(self):
        mutations = (
            ("default: build", "default: publish"),
            ("default: false", "default: true"),
            ("  workflow_dispatch:", "  push:\n  workflow_dispatch:"),
            ("branches: [feat/last-light]", "branches: ['**']"),
            ("paths: [.github/workflows/github-test-release.yml]", "paths: ['**']"),
            ("    permissions: {}", "    permissions: {contents: write}"),
            ("run: echo 'Registration only; use an explicit workflow dispatch.'", "run: ./gradlew build"),
            ("if: github.event_name == 'workflow_dispatch'", "if: always()"),
            ("cancel-in-progress: false", "cancel-in-progress: true"),
            ("  GRADLE_OPTS:", "  PRIVATE_KEY: ${{ secrets.KEY }}\n  GRADLE_OPTS:"),
            ("overwrite: false", "overwrite: true"),
            ("if-no-files-found: error", "if-no-files-found: warn"),
            ("      contents: read", "      contents: write"),
            ("      contents: write", "      contents: write\n      actions: write"),
            ("platform: macos-x64", "platform: macos-arm64"),
            ("      PYTHONPATH: ${{ github.workspace }}\n", ""),
            ("needs: [preflight, build]", "needs: preflight"),
            ("if: needs.preflight.outputs.mode == 'publish'", "if: always()"),
            ("assert_new(os.environ['GH_TEST_PLATFORM'])", "pass"),
            ("build(os.environ['GH_TEST_PLATFORM'])", "pass"),
            ("python3 -m scripts.release.github_test_release seal", "echo seal"),
            ('publish --run "$GH_TEST_INPUT_RUN"', 'publish --run 1'),
            ("      - name: Check out verified source without rebuilding\n", "      - name: Check out verified source without rebuilding\n        run: ./gradlew build\n"),
            ("actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1", "actions/checkout@main"),
        )
        for before, after in mutations:
            changed = self.workflow.replace(before, after, 1)
            self.assertNotEqual(changed, self.workflow)
            with self.subTest(before=before), self.assertRaises(RuntimeError):
                contract.verify_public_testing(changed)

    def test_actions_temporary_python_script_imports_only_with_the_checkout_path(self):
        # Actions executes `shell: python` from a temporary .py file outside
        # the checkout. Its directory, not cwd, is sys.path[0]. Import the real
        # build helpers without invoking builds, GitHub mutations or secrets.
        job = contract.validation_job(self.workflow, "build")
        self.assertIn("      PYTHONPATH: ${{ github.workspace }}\n", job)
        environment = {key: value for key, value in os.environ.items()
                       if key not in {"PYTHONPATH", "GH_TOKEN", "GITHUB_TOKEN"}}
        with tempfile.TemporaryDirectory(prefix="parlor-actions-python-") as temporary:
            script = Path(temporary) / "actions-step.py"
            script.write_text(
                "from scripts.release.github_test_release import assert_new, build, filename\n"
                "assert callable(assert_new) and callable(build)\n"
                "assert filename('android').endswith('-android.apk')\n"
                "print('CHECKOUT_IMPORT_OK')\n", encoding="utf-8")
            command = [sys.executable, "-S", str(script)]
            missing = subprocess.run(command, cwd=contract.ROOT, env=environment,
                                     capture_output=True, text=True, timeout=30, check=False)
            self.assertNotEqual(missing.returncode, 0)
            self.assertIn("No module named 'scripts'", missing.stderr)
            environment["PYTHONPATH"] = str(contract.ROOT)
            result = subprocess.run(command, cwd=contract.ROOT, env=environment,
                                    capture_output=True, text=True, timeout=30, check=False)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(result.stdout.strip(), "CHECKOUT_IMPORT_OK")


if __name__ == "__main__":
    unittest.main()
