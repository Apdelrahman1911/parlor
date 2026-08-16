from __future__ import annotations

import io
import hashlib
import json
import os
import subprocess
import tempfile
import unittest
import urllib.request
import zipfile
from pathlib import Path
from unittest import mock

import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import release_tool  # noqa: E402


CANDIDATE = "a" * 40
TREE = "b" * 40
ANDROID_SHA = "1" * 64
IOS_SHA = "2" * 64


def source_record(commit: str = CANDIDATE, tree: str = TREE) -> dict:
    return {
        "schema_version": 1,
        "repository": {"full_name": "Apdelrahman1911/parlor", "id": "123"},
        "source": {"commit_sha": commit, "tree_sha": tree},
        "version": {"marketing_version": "1.0.0", "android_version_code": 1, "ios_build_number": "1"},
        "applications": {"android_application_id": "com.parlor.app", "ios_bundle_id": "com.parlor.app"},
    }


def artifact(platform: str, commit: str = CANDIDATE) -> dict:
    digest = ANDROID_SHA if platform == "android" else IOS_SHA
    return {
        "schema_version": 1,
        "platform": platform,
        "candidate_commit_sha": commit,
        "identity": "com.parlor.app",
        "marketing_version": "1.0.0",
        "build_number": 1 if platform == "android" else "1",
        "filename": "parlor.aab" if platform == "android" else "Parlor.ipa",
        "size_bytes": 42,
        "sha256": digest,
        "signing_fingerprint_sha256": "3" * 64,
        "github_artifact": {
            "id": "456",
            "name": f"parlor-candidate-{commit}-{platform}-attempt-1",
            "url": "https://github.com/Apdelrahman1911/parlor/actions/runs/1/artifacts/456",
            "archive_sha256": "4" * 64,
        },
        "attestation": {
            "id": f"attestation-{platform}",
            "url": "https://github.com/Apdelrahman1911/parlor/attestations/1",
        },
        "validation": {"passed": True},
    }


def receipt(platform: str, commit: str = CANDIDATE) -> dict:
    common = {
        "schema_version": 1,
        "platform": platform,
        "operation": "internal_upload",
        "candidate_commit_sha": commit,
        "artifact_sha256": ANDROID_SHA if platform == "android" else IOS_SHA,
        "read_back_at": "2026-08-15T12:00:00Z",
    }
    if platform == "android":
        return {
            **common,
            "state": "internal_track_committed",
            "package_name": "com.parlor.app",
            "version_code": 1,
            "track": "internal",
            "release_name": "Parlor 1.0.0 (1)",
            "release_status": "completed",
            "edit_id": "edit-1",
            "store_bundle_sha256": ANDROID_SHA,
            "upload_evidence": "committed_edit",
            "resumed_without_upload": False,
        }
    return {
        **common,
        "state": "available_to_internal_testers",
        "bundle_id": "com.parlor.app",
        "marketing_version": "1.0.0",
        "build_number": "1",
        "app_id": "app-1",
        "build_id": "apple-build-1",
        "upload_request_id": "upload-1",
        "internal_group_id": "internal-group-1",
        "processing_state": "VALID",
        "build_audience_type": "APP_STORE_ELIGIBLE",
        "uses_non_exempt_encryption": False,
        "expired": False,
        "resumed_without_upload": False,
    }


def manifest(commit: str = CANDIDATE, tree: str = TREE) -> dict:
    return release_tool.build_manifest(
        source_record(commit, tree),
        artifact("android", commit),
        artifact("ios", commit),
        receipt("android", commit),
        receipt("ios", commit),
        "99",
        1,
        "2026-08-15T12:00:00Z",
    )


class CandidateManifestTest(unittest.TestCase):
    def test_candidate_claim_binds_build_source_and_workflow(self) -> None:
        claim = release_tool.candidate_claim(
            source_record(),
            "99",
            "2",
            "2026-08-15T12:00:00Z",
        )
        self.assertEqual(claim["android_version_code"], 1)
        self.assertEqual(claim["ios_build_number"], "1")
        self.assertEqual(claim["candidate_commit_sha"], CANDIDATE)
        self.assertEqual(claim["workflow_run_attempt"], 2)

    def test_failed_atomic_evidence_write_removes_temporary_file(self) -> None:
        writers = (
            lambda path: release_tool.atomic_write_json(path, {"schema_version": 1}),
            lambda path: release_tool.atomic_write_bytes(path, b"evidence"),
        )
        for writer in writers:
            with self.subTest(writer=writer):
                with tempfile.TemporaryDirectory() as temporary:
                    target = Path(temporary) / "evidence.json"
                    with mock.patch.object(release_tool.os, "replace", side_effect=OSError("simulated failure")):
                        with self.assertRaises(OSError):
                            writer(target)
                    self.assertEqual(list(Path(temporary).iterdir()), [])

    def test_valid_manifest_round_trip(self) -> None:
        value = manifest()
        release_tool.validate_manifest(value)
        self.assertEqual(value["source"]["tree_sha"], TREE)
        self.assertEqual(value["stores"]["google_play"]["version_code"], 1)

    def test_artifact_digest_tampering_is_rejected(self) -> None:
        value = manifest()
        value["stores"]["google_play"]["artifact_sha256"] = "9" * 64
        with self.assertRaises(release_tool.ReleaseError):
            release_tool.validate_manifest(value)

    def test_store_receipt_cannot_smuggle_private_fields_into_manifest(self) -> None:
        google = receipt("android")
        google["service_account_private_key"] = "must-not-be-recorded"
        with self.assertRaises(release_tool.ReleaseError):
            release_tool.build_manifest(
                source_record(),
                artifact("android"),
                artifact("ios"),
                google,
                receipt("ios"),
                "99",
                1,
                "2026-08-15T12:00:00Z",
            )

    def test_debug_identity_is_rejected(self) -> None:
        value = artifact("android")
        value["identity"] = "com.parlor.app.debug"
        with self.assertRaises(release_tool.ReleaseError):
            release_tool.validate_artifact_descriptor(value, "android")

    def test_wrong_signature_fingerprint_is_rejected(self) -> None:
        value = artifact("ios")
        value["signing_fingerprint_sha256"] = "not-a-fingerprint"
        with self.assertRaises(release_tool.ReleaseError):
            release_tool.validate_artifact_descriptor(value, "ios")

    def test_github_prefixed_sha256_digest_is_normalized(self) -> None:
        self.assertEqual(release_tool.require_sha256(f"sha256:{'A' * 64}", "digest"), "a" * 64)

    def test_artifact_size_bound_is_enforced(self) -> None:
        value = artifact("android")
        value["size_bytes"] = 536_870_913
        with self.assertRaises(release_tool.ReleaseError):
            release_tool.validate_artifact_descriptor(value, "android")

    def test_version_mismatch_is_rejected(self) -> None:
        ios = artifact("ios")
        ios["build_number"] = "2"
        with self.assertRaises(release_tool.ReleaseError):
            release_tool.build_manifest(
                source_record(), artifact("android"), ios, receipt("android"), receipt("ios"), "99", 1, "2026-08-15T12:00:00Z"
            )

    def test_control_artifact_path_traversal_is_rejected(self) -> None:
        payload = io.BytesIO()
        with zipfile.ZipFile(payload, "w") as archive:
            archive.writestr("../candidate-manifest.json", "{}")
        with tempfile.TemporaryDirectory() as temporary:
            with self.assertRaises(release_tool.ReleaseError):
                release_tool.safe_control_artifact(
                    payload.getvalue(), Path(temporary) / "out.json", "candidate-manifest.json"
                )

    def test_platform_state_rejects_another_candidate(self) -> None:
        state = release_tool.platform_state(source_record(), artifact("android"), receipt("android"), "android")
        with self.assertRaises(release_tool.ReleaseError):
            release_tool.validate_platform_state(state, source_record("c" * 40), "android")

    def test_store_mutation_intent_is_bound_to_exact_candidate_bytes(self) -> None:
        source = source_record()
        descriptor = artifact("ios")
        intent = release_tool.mutation_intent(
            source,
            descriptor,
            "ios",
            "2026-08-15T12:00:00Z",
        )
        release_tool.validate_mutation_intent(intent, source, descriptor, "ios")
        descriptor["sha256"] = "9" * 64
        with self.assertRaises(release_tool.ReleaseError):
            release_tool.validate_mutation_intent(intent, source, descriptor, "ios")

    def test_store_mutation_intent_rejects_another_candidate(self) -> None:
        intent = release_tool.mutation_intent(
            source_record(),
            artifact("android"),
            "android",
            "2026-08-15T12:00:00Z",
        )
        with self.assertRaises(release_tool.ReleaseError):
            release_tool.validate_mutation_intent(
                intent,
                source_record("c" * 40),
                artifact("android", "c" * 40),
                "android",
            )

    def test_orphan_binary_checkpoint_refuses_rebuild(self) -> None:
        inventory = [{"name": f"parlor-candidate-{CANDIDATE}-android-attempt-1", "expired": False}]
        with mock.patch.object(release_tool, "list_run_artifacts", return_value=inventory):
            with self.assertRaises(release_tool.ReleaseError):
                release_tool.assert_artifact_prefix_absent(
                    "Apdelrahman1911/parlor",
                    "99",
                    "token",
                    f"parlor-candidate-{CANDIDATE}-android-attempt-",
                )

    def test_expired_orphan_binary_still_refuses_rebuild(self) -> None:
        inventory = [{"name": f"parlor-candidate-{CANDIDATE}-ios-attempt-1", "expired": True}]
        with mock.patch.object(release_tool, "list_run_artifacts", return_value=inventory):
            with self.assertRaises(release_tool.ReleaseError):
                release_tool.assert_artifact_prefix_absent(
                    "Apdelrahman1911/parlor",
                    "99",
                    "token",
                    f"parlor-candidate-{CANDIDATE}-ios-attempt-",
                )

    def test_github_artifact_inventory_is_bounded_and_paginated(self) -> None:
        first = {"artifacts": [{"id": value} for value in range(100)]}
        second = {"artifacts": [{"id": 100}]}
        with mock.patch.object(
            release_tool,
            "github_request",
            side_effect=[json.dumps(first).encode(), json.dumps(second).encode()],
        ) as request:
            artifacts = release_tool.list_run_artifacts("Apdelrahman1911/parlor", "99", "token")
        self.assertEqual(len(artifacts), 101)
        self.assertIn("page=2", request.call_args_list[1].args[0])

    def test_foreign_protected_candidate_claim_reserves_build_number(self) -> None:
        inventory = [
            {
                "name": "parlor-candidate-claim-build-1-run-88-attempt-1",
                "expired": False,
                "workflow_run": {"id": 88},
            }
        ]
        with (
            mock.patch.object(release_tool, "list_repository_artifacts", return_value=inventory),
            mock.patch.object(release_tool, "candidate_run_is_trusted", return_value=True) as trusted,
        ):
            with self.assertRaises(release_tool.ReleaseError):
                release_tool.assert_candidate_claim_exclusive(
                    "Apdelrahman1911/parlor", "123", "99", source_record(), "token"
                )
        trusted.assert_called_once_with("Apdelrahman1911/parlor", "123", 88, "token")

    def test_same_run_claim_allows_a_safe_rerun(self) -> None:
        inventory = [
            {
                "name": "parlor-candidate-claim-build-1-run-99-attempt-1",
                "expired": True,
                "workflow_run": {"id": 99},
            }
        ]
        with (
            mock.patch.object(release_tool, "list_repository_artifacts", return_value=inventory),
            mock.patch.object(release_tool, "candidate_run_is_trusted") as trusted,
        ):
            release_tool.assert_candidate_claim_exclusive(
                "Apdelrahman1911/parlor", "123", "99", source_record(), "token"
            )
        trusted.assert_not_called()

    def test_untrusted_artifact_cannot_reserve_candidate_build_number(self) -> None:
        inventory = [
            {
                "name": "parlor-candidate-claim-build-1-run-88-attempt-1",
                "expired": False,
                "workflow_run": {"id": 88},
            }
        ]
        with (
            mock.patch.object(release_tool, "list_repository_artifacts", return_value=inventory),
            mock.patch.object(release_tool, "candidate_run_is_trusted", return_value=False),
        ):
            release_tool.assert_candidate_claim_exclusive(
                "Apdelrahman1911/parlor", "123", "99", source_record(), "token"
            )

    def test_binary_checkpoint_recovers_exact_bytes(self) -> None:
        binary = b"signed-candidate-bytes"
        archive_bytes = io.BytesIO()
        with zipfile.ZipFile(archive_bytes, "w", compression=zipfile.ZIP_STORED) as archive:
            archive.writestr("Parlor.ipa", binary)
        archive_payload = archive_bytes.getvalue()
        descriptor = artifact("ios")
        descriptor["size_bytes"] = len(binary)
        descriptor["sha256"] = hashlib.sha256(binary).hexdigest()
        descriptor["github_artifact"]["archive_sha256"] = hashlib.sha256(archive_payload).hexdigest()

        class Response(io.BytesIO):
            status = 200

            def __enter__(self):
                return self

            def __exit__(self, *args):
                self.close()

        inventory = [
            {
                "id": int(descriptor["github_artifact"]["id"]),
                "name": descriptor["github_artifact"]["name"],
                "expired": False,
                "archive_download_url": "https://api.github.com/artifacts/456/zip",
            }
        ]
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / "Parlor.ipa"
            with (
                mock.patch.object(release_tool, "list_run_artifacts", return_value=inventory),
                mock.patch.object(release_tool, "github_api_request", return_value=Response(archive_payload)),
            ):
                release_tool.download_binary_artifact(
                    "Apdelrahman1911/parlor", "99", "token", descriptor, output
                )
            self.assertEqual(output.read_bytes(), binary)


class WorkflowRunRecordTest(unittest.TestCase):
    def run_payload(self, **overrides: object) -> bytes:
        value = {
            "id": 456,
            "run_attempt": 2,
            "event": "workflow_dispatch",
            "status": "completed",
            "conclusion": "success",
            "head_branch": "testing",
            "head_sha": "c" * 40,
            "path": ".github/workflows/testing-external-promotion.yml",
            "head_repository": {"id": 123, "full_name": "Apdelrahman1911/parlor"},
        }
        value.update(overrides)
        return json.dumps(value).encode()

    def record(self) -> dict:
        return release_tool.workflow_run_record(
            "Apdelrahman1911/parlor",
            "123",
            "456",
            "2",
            ".github/workflows/testing-external-promotion.yml",
            "testing",
            "token",
        )

    def test_successful_protected_external_run_is_bound_to_its_own_source_sha(self) -> None:
        with mock.patch.object(release_tool, "github_request", return_value=self.run_payload()) as request:
            result = self.record()
        self.assertEqual(result["head_sha"], "c" * 40)
        self.assertEqual(result["workflow_run_attempt"], 2)
        request.assert_called_once_with(
            "https://api.github.com/repos/Apdelrahman1911/parlor/actions/runs/456/attempts/2",
            "token",
        )

    def test_wrong_workflow_attempt_is_rejected(self) -> None:
        with mock.patch.object(release_tool, "github_request", return_value=self.run_payload(run_attempt=3)):
            with self.assertRaises(release_tool.ReleaseError):
                self.record()

    def test_untrusted_repository_or_branch_is_rejected(self) -> None:
        for override in (
            {"head_branch": "feature/untrusted"},
            {"head_repository": {"id": 999, "full_name": "attacker/parlor"}},
        ):
            with self.subTest(override=override):
                with mock.patch.object(release_tool, "github_request", return_value=self.run_payload(**override)):
                    with self.assertRaises(release_tool.ReleaseError):
                        self.record()

    def test_candidate_claim_accepts_only_the_protected_candidate_workflow(self) -> None:
        trusted = self.run_payload(
            id=88,
            run_attempt=1,
            status="in_progress",
            conclusion=None,
            path=".github/workflows/testing-candidate.yml",
            head_sha="d" * 40,
        )
        with mock.patch.object(release_tool, "github_request", return_value=trusted):
            self.assertTrue(
                release_tool.candidate_run_is_trusted(
                    "Apdelrahman1911/parlor", "123", 88, "token"
                )
            )
        untrusted = self.run_payload(
            id=88,
            path=".github/workflows/production-verification.yml",
            head_sha="d" * 40,
        )
        with mock.patch.object(release_tool, "github_request", return_value=untrusted):
            self.assertFalse(
                release_tool.candidate_run_is_trusted(
                    "Apdelrahman1911/parlor", "123", 88, "token"
                )
            )


class GitHubRedirectPolicyTest(unittest.TestCase):
    def setUp(self) -> None:
        self.handler = release_tool.SafeRedirectHandler()
        self.request = urllib.request.Request(
            "https://api.github.com/repos/owner/repo/actions/artifacts/1/zip",
            headers={"Authorization": "Bearer secret"},
        )

    def redirect(self, destination: str):
        return self.handler.redirect_request(self.request, None, 302, "Found", {}, destination)

    def test_https_cross_host_redirect_strips_github_authorization(self) -> None:
        redirected = self.redirect("https://pipelines.actions.githubusercontent.com/signed-artifact")
        self.assertIsNotNone(redirected)
        self.assertIsNone(redirected.get_header("Authorization"))

    def test_insecure_or_credentialed_redirect_is_rejected(self) -> None:
        for destination in (
            "http://api.github.com/insecure",
            "https://user:password@example.invalid/artifact",
            "file:///tmp/artifact",
        ):
            with self.subTest(destination=destination):
                self.assertIsNone(self.redirect(destination))


class GitTreeVerificationTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.repo = Path(self.temporary.name)
        self.git("init", "-q")
        self.git("config", "user.name", "Parlor Test")
        self.git("config", "user.email", "parlor-test@example.invalid")
        (self.repo / "file.txt").write_text("candidate\n")
        self.git("add", "file.txt")
        self.git("commit", "-q", "-m", "candidate")
        self.candidate = self.git("rev-parse", "HEAD")
        self.tree = self.git("rev-parse", "HEAD^{tree}")
        self.previous_root = release_tool.ROOT
        release_tool.ROOT = self.repo

    def tearDown(self) -> None:
        release_tool.ROOT = self.previous_root
        self.temporary.cleanup()

    def git(self, *args: str) -> str:
        return subprocess.check_output(["git", *args], cwd=self.repo, text=True).strip()

    def candidate_manifest(self) -> dict:
        return manifest(self.candidate, self.tree)

    def test_exact_candidate_commit_is_accepted(self) -> None:
        self.assertEqual(
            release_tool.verify_source(self.candidate_manifest(), "Apdelrahman1911/parlor", "123"),
            "exact-commit",
        )

    def test_shared_history_equal_tree_rebase_or_merge_is_accepted(self) -> None:
        self.git("commit", "--allow-empty", "-q", "-m", "protected merge metadata")
        self.assertEqual(
            release_tool.verify_source(self.candidate_manifest(), "Apdelrahman1911/parlor", "123"),
            "shared-history-equal-tree",
        )

    def test_divergent_tree_is_rejected(self) -> None:
        (self.repo / "file.txt").write_text("changed\n")
        self.git("add", "file.txt")
        self.git("commit", "-q", "-m", "changed")
        with self.assertRaises(release_tool.ReleaseError):
            release_tool.verify_source(self.candidate_manifest(), "Apdelrahman1911/parlor", "123")

    def test_unrelated_same_tree_is_rejected(self) -> None:
        self.git("checkout", "--orphan", "unrelated")
        self.git("rm", "-q", "-f", "file.txt")
        (self.repo / "file.txt").write_text("candidate\n")
        self.git("add", "file.txt")
        self.git("commit", "-q", "-m", "copied files")
        self.assertEqual(self.git("rev-parse", "HEAD^{tree}"), self.tree)
        with self.assertRaises(release_tool.ReleaseError):
            release_tool.verify_source(self.candidate_manifest(), "Apdelrahman1911/parlor", "123")


if __name__ == "__main__":
    unittest.main()
