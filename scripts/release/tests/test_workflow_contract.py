from __future__ import annotations

import json
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest.mock import patch

import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import workflow_contract  # noqa: E402


class WorkflowContractTest(unittest.TestCase):
    def test_repository_workflows_satisfy_release_contract(self) -> None:
        self.assertEqual(workflow_contract.main(), 0)

    def test_xcode_identity_guard_matches_the_exact_build_setting(self) -> None:
        workflow = (workflow_contract.ROOT / ".github/workflows/production-verification.yml").read_text(
            encoding="utf-8"
        )
        self.assertEqual(workflow.count('key == "PRODUCT_BUNDLE_IDENTIFIER"'), 2)
        broken = workflow.replace(
            '{key=$1; gsub(/^[[:space:]]+|[[:space:]]+$/, "", key); if (key == "PRODUCT_BUNDLE_IDENTIFIER") {print $2; exit}}',
            '$1 ~ /PRODUCT_BUNDLE_IDENTIFIER$/ {print $2; exit}',
        )
        with self.assertRaisesRegex(RuntimeError, "Mac Catalyst"):
            workflow_contract.verify_validation(broken)

    def test_candidate_bundletool_download_must_be_bounded(self) -> None:
        workflow = (workflow_contract.ROOT / ".github/workflows/testing-candidate.yml").read_text(
            encoding="utf-8"
        )
        with self.assertRaisesRegex(RuntimeError, "bundletool download"):
            workflow_contract.verify_candidate(workflow.replace("--max-filesize 209715200", "", 1))

    def test_candidate_dependency_report_must_use_release_runtime(self) -> None:
        workflow = (workflow_contract.ROOT / ".github/workflows/testing-candidate.yml").read_text(
            encoding="utf-8"
        )
        with self.assertRaisesRegex(RuntimeError, "release runtime"):
            workflow_contract.verify_candidate(
                workflow.replace("--configuration releaseRuntimeClasspath", "", 1)
            )

    def test_candidate_dependency_and_validation_evidence_must_be_retained(self) -> None:
        workflow = (workflow_contract.ROOT / ".github/workflows/testing-candidate.yml").read_text(
            encoding="utf-8"
        )
        with self.assertRaisesRegex(RuntimeError, "retain Android dependency/validation"):
            workflow_contract.verify_candidate(
                workflow.replace("Retain Android dependency and deep-validation evidence", "Evidence removed", 1)
            )

    def test_candidate_build_number_claim_cannot_be_removed(self) -> None:
        workflow = (workflow_contract.ROOT / ".github/workflows/testing-candidate.yml").read_text(
            encoding="utf-8"
        )
        with self.assertRaisesRegex(RuntimeError, "build-once recovery"):
            workflow_contract.verify_candidate(
                workflow.replace("assert-candidate-claim-exclusive", "claim-check-removed", 1)
            )

    def test_candidate_claim_transaction_cannot_be_scoped_per_sha(self) -> None:
        workflow = (workflow_contract.ROOT / ".github/workflows/testing-candidate.yml").read_text(
            encoding="utf-8"
        )
        broken = workflow.replace(
            "group: parlor-store-candidate-claim",
            "group: parlor-store-candidate-${{ inputs.candidate_sha }}",
            1,
        )
        with self.assertRaisesRegex(RuntimeError, "claim check/create transaction"):
            workflow_contract.verify_candidate(broken)

    def test_candidate_claim_requires_a_secretless_protected_environment(self) -> None:
        workflow = (workflow_contract.ROOT / ".github/workflows/testing-candidate.yml").read_text(
            encoding="utf-8"
        )
        with self.assertRaisesRegex(RuntimeError, "testing-candidate environment"):
            workflow_contract.verify_candidate(
                workflow.replace("    environment: testing-candidate\n", "", 1)
            )
        poisoned = workflow.replace(
            "    environment: testing-candidate\n",
            "    environment: testing-candidate\n    env:\n      BAD: ${{ secrets.STORE_KEY }}\n",
            1,
        )
        with self.assertRaisesRegex(RuntimeError, "must not receive or reference Store secrets"):
            workflow_contract.verify_candidate(poisoned)

    def test_release_policy_includes_the_candidate_control_environment(self) -> None:
        policy = json.loads(
            (workflow_contract.ROOT / "config/release-policy.json").read_text(encoding="utf-8")
        )
        self.assertEqual(
            policy["github"]["environments"]["candidate_control"],
            "testing-candidate",
        )

    def test_external_partial_rerun_evidence_cannot_be_removed(self) -> None:
        workflow = (
            workflow_contract.ROOT / ".github/workflows/testing-external-promotion.yml"
        ).read_text(encoding="utf-8")
        with self.assertRaisesRegex(RuntimeError, "partial rerun"):
            workflow_contract.verify_external_receipt_attestations(
                workflow.replace("create-external-evidence", "evidence-removed", 1)
            )

    def test_mutable_action_reference_is_rejected(self) -> None:
        with self.assertRaises(RuntimeError):
            workflow_contract.verify_action_pins("bad.yml", "steps:\n  - uses: actions/checkout@v4\n")

    def test_unreviewed_workflow_file_is_rejected(self) -> None:
        with TemporaryDirectory() as directory:
            workflows = Path(directory)
            for name in workflow_contract.EXPECTED:
                (workflows / name).write_text("name: reviewed\n", encoding="utf-8")
            (workflows / "unreviewed.yml").write_text("name: bypass\n", encoding="utf-8")
            with patch.object(workflow_contract, "WORKFLOWS", workflows):
                with self.assertRaisesRegex(RuntimeError, "Unreviewed workflow"):
                    workflow_contract.load_files()

    def test_production_build_command_is_rejected(self) -> None:
        with self.assertRaises(RuntimeError):
            workflow_contract.verify_promotions(
                "bad.yml",
                "candidate_run_id candidate_run_attempt fetch-artifact verify-source xcodebuild",
            )

    def test_production_push_trigger_is_rejected(self) -> None:
        with self.assertRaises(RuntimeError):
            workflow_contract.verify_store_workflow(
                "bad.yml",
                "workflow_dispatch:\npush:\npermissions:\n contents: read\ntimeout-minutes: 1\nenvironment: production\n",
            )

    def test_missing_shared_store_lock_is_rejected(self) -> None:
        workflows = {
            name: "group: parlor-google-play-com-parlor-app\ngroup: parlor-app-store-connect-com-parlor-app"
            for name in workflow_contract.STORE_WORKFLOWS
        }
        workflows["testing-candidate.yml"] = "group: parlor-google-play-com-parlor-app"
        with self.assertRaises(RuntimeError):
            workflow_contract.verify_store_serialization(workflows)

    def test_promotion_without_candidate_attestation_is_rejected(self) -> None:
        with self.assertRaises(RuntimeError):
            workflow_contract.verify_promotions(
                "bad.yml",
                "candidate_run_id candidate_run_attempt fetch-artifact verify-source",
            )

    def test_production_without_external_receipt_attestation_is_rejected(self) -> None:
        with self.assertRaises(RuntimeError):
            workflow_contract.verify_production(
                "platform: android ios both\nrefs/heads/release\nproduction-android\nproduction-ios\n"
                "external-receipt\nexternal_run_id\n",
            )

    def test_production_without_its_own_receipt_attestations_is_rejected(self) -> None:
        workflow = (workflow_contract.ROOT / ".github/workflows/production-promotion.yml").read_text(
            encoding="utf-8"
        )
        workflow = workflow.replace(
            "subject-path: build/release-promotion/production-ios-receipt.json",
            "subject-path: build/release-promotion/not-the-ios-production-receipt.json",
        )
        with self.assertRaises(RuntimeError):
            workflow_contract.verify_production(workflow)

    def test_ios_profile_refusal_cannot_delete_a_preexisting_profile(self) -> None:
        script = (workflow_contract.ROOT / "scripts/release/build_ios_candidate.sh").read_text(encoding="utf-8")
        refusal = script.index('if [[ -e "$profile_destination" ]]')
        install = script.index('install -m 600 "$PARLOR_APPLE_PROFILE_PATH" "$profile_destination"')
        mark_owned = script.index("installed_profile=$profile_destination")
        self.assertLess(refusal, install)
        self.assertLess(install, mark_owned)

    def test_ios_signing_cleanup_is_fail_closed(self) -> None:
        script = (workflow_contract.ROOT / "scripts/release/build_ios_candidate.sh").read_text(encoding="utf-8")
        self.assertIn("cleanup_status=0", script)
        self.assertIn('exit "$cleanup_status"', script)
        self.assertIn("trap 'cleanup $?' EXIT", script)
        self.assertNotIn("|| true", script)

    def test_ios_build_phases_do_not_inherit_source_signing_secrets(self) -> None:
        script = (workflow_contract.ROOT / "scripts/release/build_ios_candidate.sh").read_text(encoding="utf-8")
        xcode = script.index("xcodebuild \\")
        for token in (
            "unset PARLOR_APPLE_CERTIFICATE_PASSWORD",
            "unset PARLOR_APPLE_CERTIFICATE_P12_PATH",
            "unset PARLOR_APPLE_PROFILE_PATH",
        ):
            self.assertLess(script.index(token), xcode)

    def test_ios_artifact_validator_enforces_pinned_store_toolchain(self) -> None:
        script = (workflow_contract.ROOT / "scripts/release/validate_ios_artifact.sh").read_text(encoding="utf-8")
        for token in (
            "DTXcodeBuild",
            "DTSDKName",
            "MinimumOSVersion",
            '"xcrun", "vtool", "-show-build"',
            'platform != "IOS"',
            "macho_minimum_os_versions",
        ):
            self.assertIn(token, script)

    def test_all_release_temporary_directories_use_signal_safe_cleanup(self) -> None:
        for name in (
            "build_ios_candidate.sh",
            "upload_ios_candidate.sh",
            "validate_android_artifact.sh",
            "validate_ios_artifact.sh",
            "validate_release_system.sh",
        ):
            with self.subTest(script=name):
                script = (workflow_contract.ROOT / "scripts/release" / name).read_text(encoding="utf-8")
                self.assertIn("trap 'cleanup $?' EXIT", script)
                self.assertIn("trap 'exit 130' INT", script)
                self.assertIn("trap 'exit 143' TERM", script)

    def test_ios_upload_uses_scoped_key_directory_and_removes_raw_response(self) -> None:
        script = (workflow_contract.ROOT / "scripts/release/upload_ios_candidate.sh").read_text(encoding="utf-8")
        self.assertIn('export API_PRIVATE_KEYS_DIR="$temporary_dir/private_keys"', script)
        self.assertIn('rm -f "$raw_log"', script)
        self.assertNotIn("export HOME=", script)


if __name__ == "__main__":
    unittest.main()
