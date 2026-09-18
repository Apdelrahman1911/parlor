from __future__ import annotations

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
            ("cancel-in-progress: false", "cancel-in-progress: true"),
            ("  GRADLE_OPTS:", "  PRIVATE_KEY: ${{ secrets.KEY }}\n  GRADLE_OPTS:"),
            ("overwrite: false", "overwrite: true"),
            ("if-no-files-found: error", "if-no-files-found: warn"),
            ("      contents: read", "      contents: write"),
            ("      contents: write", "      contents: write\n      actions: write"),
            ("platform: macos-x64", "platform: macos-arm64"),
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


if __name__ == "__main__":
    unittest.main()
