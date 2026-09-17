from __future__ import annotations

from pathlib import Path
import unittest

from scripts.release import workflow_contract as contract


class DistributionWorkflowTest(unittest.TestCase):
    def setUp(self):
        self.workflow = (contract.ROOT / ".github/workflows/github-distribution.yml").read_text()

    def test_new_github_channel_does_not_enable_or_replace_store_contracts(self):
        contract.verify_github_distribution(self.workflow)
        contract.verify_common("github-distribution.yml", self.workflow)
        self.assertNotIn("github-distribution.yml", contract.STORE_WORKFLOWS)
        self.assertEqual(contract.STORE_WORKFLOWS, {"testing-candidate.yml", "testing-external-promotion.yml", "production-promotion.yml"})
        for name in contract.STORE_WORKFLOWS:
            text = (contract.ROOT / ".github/workflows" / name).read_text()
            contract.verify_store_workflow(name, text)

    def test_race_unsafe_permissions_missing_signing_unprotected_upload_and_rebuild_mutations_fail(self):
        mutations = (
            ("cancel-in-progress: false", "cancel-in-progress: true"),
            ("default: rehearsal", "default: candidate"),
            ("overwrite: false", "overwrite: true"),
            ("if-no-files-found: error", "if-no-files-found: warn"),
            ("    needs: preflight\n", "    needs: []\n"),
            ("environment: github-publish", "environment: unprotected"),
            ("github-sign-windows", "github-sign-android"),
            ("GH_DIST_ANDROID_CERT_SHA256:", "UNPINNED_ANDROID_CERT:"),
            ("GH_DIST_MACOS_CERT_SHA256:", "UNPINNED_MAC_CERT:"),
            ("GH_DIST_WINDOWS_CERT_SHA256:", "UNPINNED_WINDOWS_CERT:"),
            ("GH_DIST_ACCEPTANCE_SHA: ${{ vars.GH_DIST_ACCEPTANCE_SHA }}", "GH_DIST_ACCEPTANCE_SHA: ${{ github.sha }}"),
            ("needs.preflight.outputs.mode == 'candidate' && matrix.platform == 'android'", "matrix.platform == 'android'"),
            ("from scripts.release.sign_distribution import sign", "from fake.signer import sign"),
            ("      contents: write\n", "      contents: write\n      actions: write\n"),
            ("      - name: Check out tagged source without rebuilding\n", "      - name: Check out tagged source without rebuilding\n        run: ./gradlew build\n"),
        )
        for old, new in mutations:
            changed = self.workflow.replace(old, new, 1)
            self.assertNotEqual(changed, self.workflow)
            with self.subTest(old=old), self.assertRaises(RuntimeError):
                contract.verify_github_distribution(changed)

    def test_unpinned_actions_or_secrets_outside_scoped_signing_are_rejected(self):
        changed = self.workflow.replace("actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1", "actions/checkout@main", 1)
        with self.assertRaises(RuntimeError):
            contract.verify_common("github-distribution.yml", changed)
        changed = self.workflow.replace("GRADLE_OPTS:", "PRIVATE_KEY: ${{ secrets.KEY }}\n  GRADLE_OPTS:", 1)
        with self.assertRaisesRegex(RuntimeError, "secrets cannot reach"):
            contract.verify_github_distribution(changed)


if __name__ == "__main__":
    unittest.main()
