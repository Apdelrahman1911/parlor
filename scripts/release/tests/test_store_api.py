from __future__ import annotations

import io
import hashlib
import json
import tempfile
import unittest
import urllib.error
from pathlib import Path
from types import SimpleNamespace
from unittest import mock

import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import store_api  # noqa: E402
import release_tool  # noqa: E402
from test_release_tool import artifact, manifest, source_record  # noqa: E402


def eligible_apple_build(**attribute_overrides: object) -> dict:
    attributes = {
        "version": "1",
        "processingState": "VALID",
        "expired": False,
        "buildAudienceType": "APP_STORE_ELIGIBLE",
        "usesNonExemptEncryption": False,
        "minOsVersion": "16.0.0",
        "uploadedDate": "2026-08-15T12:00:00Z",
        "expirationDate": "2099-08-15T12:00:00Z",
    }
    attributes.update(attribute_overrides)
    return {"type": "builds", "id": "apple-build-1", "attributes": attributes}


class EcdsaSignatureTest(unittest.TestCase):
    def test_der_signature_converts_to_fixed_width_jwt_signature(self) -> None:
        der = bytes([0x30, 0x06, 0x02, 0x01, 0x01, 0x02, 0x01, 0x02])
        raw = store_api.der_ecdsa_to_raw(der)
        self.assertEqual(len(raw), 64)
        self.assertEqual(raw[31], 1)
        self.assertEqual(raw[63], 2)

    def test_malformed_der_signature_is_rejected(self) -> None:
        with self.assertRaises(store_api.ReleaseError):
            store_api.der_ecdsa_to_raw(b"not-der")


class HttpRetryContractTest(unittest.TestCase):
    def response_error(self) -> urllib.error.HTTPError:
        return urllib.error.HTTPError("https://example.invalid", 503, "unavailable", {}, io.BytesIO(b"{}"))

    @mock.patch("store_api.time.sleep")
    @mock.patch("store_api.open_store_request")
    def test_mutation_is_never_blindly_retried(self, open_request: mock.Mock, sleep: mock.Mock) -> None:
        open_request.side_effect = self.response_error()
        client = store_api.JsonClient("https://example.invalid", lambda: "token")
        with self.assertRaises(store_api.ReleaseError):
            client.request("POST", "/mutation", {}, safe_retry=False)
        self.assertEqual(open_request.call_count, 1)
        sleep.assert_not_called()

    @mock.patch("store_api.time.sleep")
    @mock.patch("store_api.open_store_request")
    def test_safe_read_uses_bounded_retry(self, open_request: mock.Mock, sleep: mock.Mock) -> None:
        open_request.side_effect = [self.response_error(), self.response_error(), self.response_error(), self.response_error()]
        client = store_api.JsonClient("https://example.invalid", lambda: "token")
        with self.assertRaises(store_api.ReleaseError):
            client.request("GET", "/read", safe_retry=True)
        self.assertEqual(open_request.call_count, 4)
        self.assertEqual(sleep.call_count, 3)


class PromotionReceiptTest(unittest.TestCase):
    def write(self, value: dict) -> Path:
        temporary = tempfile.NamedTemporaryFile(mode="w", delete=False)
        __import__("json").dump(value, temporary)
        temporary.close()
        self.addCleanup(Path(temporary.name).unlink)
        return Path(temporary.name)

    def test_google_external_track_commit_is_accepted(self) -> None:
        candidate = manifest()
        receipt = {
            "schema_version": 1,
            "platform": "android",
            "operation": "external_promotion",
            "candidate_commit_sha": candidate["source"]["commit_sha"],
            "artifact_sha256": candidate["artifacts"]["android"]["sha256"],
            "package_name": "com.parlor.app",
            "version_code": 1,
            "source_track": "internal",
            "destination_track": "closed-testing",
            "release_status": "completed",
            "edit_id": "edit-1",
            "result": "committed",
            "state": "external_track_committed",
            "read_back_at": "2026-08-15T12:00:00Z",
        }
        self.assertEqual(
            store_api.validate_external_receipt(self.write(receipt), candidate, "android")["state"],
            "external_track_committed",
        )

    def test_apple_unapproved_external_build_is_rejected(self) -> None:
        candidate = manifest()
        receipt = {
            "schema_version": 1,
            "platform": "ios",
            "operation": "external_promotion",
            "candidate_commit_sha": candidate["source"]["commit_sha"],
            "artifact_sha256": candidate["artifacts"]["ios"]["sha256"],
            "bundle_id": "com.parlor.app",
            "build_number": "1",
            "build_id": "apple-build-1",
            "external_group_id": "external-group-1",
            "beta_review_submission_id": "review-1",
            "beta_review_state": "IN_REVIEW",
            "state": "beta_app_review",
            "read_back_at": "2026-08-15T12:00:00Z",
        }
        with self.assertRaises(store_api.ReleaseError):
            store_api.validate_external_receipt(self.write(receipt), candidate, "ios")

    def test_receipt_for_other_artifact_is_rejected(self) -> None:
        candidate = manifest()
        receipt = {
            "schema_version": 1,
            "platform": "android",
            "operation": "external_promotion",
            "candidate_commit_sha": candidate["source"]["commit_sha"],
            "artifact_sha256": "9" * 64,
            "package_name": "com.parlor.app",
            "version_code": 1,
            "source_track": "internal",
            "destination_track": "closed-testing",
            "release_status": "completed",
            "edit_id": "edit-1",
            "result": "committed",
            "state": "external_track_committed",
            "read_back_at": "2026-08-15T12:00:00Z",
        }
        with self.assertRaisesRegex(store_api.ReleaseError, "different artifact bytes"):
            store_api.validate_external_receipt(self.write(receipt), candidate, "android")


class ExternalEvidenceTest(unittest.TestCase):
    def write(self, value: dict) -> Path:
        temporary = tempfile.NamedTemporaryFile(mode="w", delete=False)
        json.dump(value, temporary)
        temporary.close()
        self.addCleanup(Path(temporary.name).unlink)
        return Path(temporary.name)

    def write_json(self, directory: Path, name: str, value: dict) -> Path:
        path = directory / name
        release_tool.atomic_write_json(path, value)
        return path

    def create(self, directory: Path) -> tuple[Path, dict]:
        candidate = manifest()
        manifest_path = self.write_json(directory, "candidate-manifest.json", candidate)
        android = self.write_json(
            directory,
            "external-android-receipt.json",
            ValidationOnlyTest.external_receipt(candidate, "android"),
        )
        ios = self.write_json(
            directory,
            "external-ios-receipt.json",
            ValidationOnlyTest.external_receipt(candidate, "ios"),
        )
        evidence = store_api.create_external_evidence(
            manifest_path,
            "both",
            android,
            ios,
            "200",
            "2",
            "c" * 40,
            "2026-08-15T12:00:00Z",
        )
        return manifest_path, evidence

    def test_successful_run_can_aggregate_valid_receipts_from_prior_attempts(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            manifest_path, evidence = self.create(Path(temporary))
            result = store_api.validate_external_evidence(
                evidence,
                manifest_path,
                "200",
                "2",
                "c" * 40,
            )
            self.assertEqual(result["platforms"], ["android", "ios"])
            self.assertEqual(result["receipts"]["android"]["state"], "external_track_committed")
            self.assertEqual(result["receipts"]["ios"]["state"], "available_to_external_testers")

    def test_external_evidence_rejects_manifest_or_attempt_tampering(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            manifest_path, evidence = self.create(Path(temporary))
            for mutation in ("digest", "attempt"):
                changed = json.loads(json.dumps(evidence))
                if mutation == "digest":
                    changed["candidate_manifest_sha256"] = "9" * 64
                else:
                    changed["workflow_run_attempt"] = 3
                with self.subTest(mutation=mutation):
                    with self.assertRaises(store_api.ReleaseError):
                        store_api.validate_external_evidence(
                            changed,
                            manifest_path,
                            "200",
                            "2",
                            "c" * 40,
                        )

    def test_receipt_with_unexpected_sensitive_field_is_rejected(self) -> None:
        candidate = manifest()
        receipt = {
            "schema_version": 1,
            "platform": "android",
            "operation": "external_promotion",
            "candidate_commit_sha": candidate["source"]["commit_sha"],
            "artifact_sha256": candidate["artifacts"]["android"]["sha256"],
            "package_name": "com.parlor.app",
            "version_code": 1,
            "source_track": "internal",
            "destination_track": "closed-testing",
            "release_status": "completed",
            "edit_id": "edit-1",
            "result": "committed",
            "state": "external_track_committed",
            "read_back_at": "2026-08-15T12:00:00Z",
            "private_token": "must-not-enter-evidence",
        }
        with self.assertRaisesRegex(store_api.ReleaseError, "unsupported or missing fields"):
            store_api.validate_external_receipt(self.write(receipt), candidate, "android")


class GoogleTrackTest(unittest.TestCase):
    def test_promotion_requires_exact_candidate_bundle_digest(self) -> None:
        with self.assertRaisesRegex(store_api.ReleaseError, "immutable AAB digest"):
            store_api.require_candidate_bundle_digest(
                [{"versionCode": 1, "sha256": "9" * 64}],
                1,
                "1" * 64,
            )
        store_api.require_candidate_bundle_digest(
            [{"versionCode": 1, "sha256": "1" * 64}],
            1,
            "1" * 64,
        )

    def test_google_promotion_checks_store_bytes_before_mutation(self) -> None:
        candidate = manifest()
        with tempfile.TemporaryDirectory() as temporary:
            manifest_path = Path(temporary) / "candidate-manifest.json"
            manifest_path.write_text(json.dumps(candidate), encoding="utf-8")
            args = SimpleNamespace(
                manifest=str(manifest_path),
                package="com.parlor.app",
                source_track="internal",
                destination_track="closed-testing",
                operation="external",
                credentials=str(Path(temporary) / "credentials.json"),
            )
            client = mock.Mock()
            client.read_inventory.return_value = (
                [
                    {
                        "track": "internal",
                        "releases": [{"versionCodes": ["1"], "status": "completed"}],
                    },
                    {"track": "closed-testing", "releases": []},
                ],
                [{"versionCode": 1, "sha256": "9" * 64}],
            )
            with mock.patch.object(store_api, "GoogleClient", return_value=client):
                with self.assertRaisesRegex(store_api.ReleaseError, "immutable AAB digest"):
                    store_api.google_promote_execute(args)
            client.insert_edit.assert_not_called()

    def test_existing_google_promotion_with_exact_store_bytes_is_idempotent(self) -> None:
        candidate = manifest()
        with tempfile.TemporaryDirectory() as temporary:
            manifest_path = Path(temporary) / "candidate-manifest.json"
            manifest_path.write_text(json.dumps(candidate), encoding="utf-8")
            args = SimpleNamespace(
                manifest=str(manifest_path),
                package="com.parlor.app",
                source_track="internal",
                destination_track="closed-testing",
                operation="external",
                credentials=str(Path(temporary) / "credentials.json"),
            )
            release = {"versionCodes": ["1"], "status": "completed"}
            client = mock.Mock()
            client.read_inventory.return_value = (
                [
                    {"track": "internal", "releases": [release]},
                    {"track": "closed-testing", "releases": [release]},
                ],
                [
                    {
                        "versionCode": 1,
                        "sha256": candidate["artifacts"]["android"]["sha256"],
                    }
                ],
            )
            with mock.patch.object(store_api, "GoogleClient", return_value=client):
                result = store_api.google_promote_execute(args)
            self.assertEqual(result["result"], "already_present")
            client.insert_edit.assert_not_called()

    def test_prebuild_uniqueness_rejects_existing_store_version(self) -> None:
        client = mock.Mock()
        client.read_inventory.return_value = (
            [{"track": "internal", "releases": [{"versionCodes": ["1"]}]}],
            [{"versionCode": 1, "sha256": "1" * 64}],
        )
        args = SimpleNamespace(
            package="com.parlor.app",
            version_code="1",
            credentials="credentials.json",
        )
        with mock.patch.object(store_api, "GoogleClient", return_value=client):
            with self.assertRaises(store_api.ReleaseError):
                store_api.google_check_unique_execute(args)

    def test_prebuild_uniqueness_allows_unused_store_version(self) -> None:
        client = mock.Mock()
        client.read_inventory.return_value = (
            [{"track": "internal", "releases": [{"versionCodes": ["2"]}]}],
            [{"versionCode": 2, "sha256": "2" * 64}],
        )
        args = SimpleNamespace(
            package="com.parlor.app",
            version_code="1",
            credentials="credentials.json",
        )
        with mock.patch.object(store_api, "GoogleClient", return_value=client):
            store_api.google_check_unique_execute(args)

    def test_duplicate_named_tracks_are_rejected(self) -> None:
        tracks = [{"track": "internal"}, {"track": "internal"}]
        with self.assertRaises(store_api.ReleaseError):
            store_api.find_track(tracks, "internal")

    def test_duplicate_version_is_detected_across_tracks(self) -> None:
        tracks = [{"track": "internal", "releases": [{"versionCodes": ["1"]}]}]
        with self.assertRaises(store_api.ReleaseError):
            store_api.ensure_version_unique(tracks, 1)

    def test_other_version_is_allowed(self) -> None:
        tracks = [{"track": "internal", "releases": [{"versionCodes": ["2"]}]}]
        store_api.ensure_version_unique(tracks, 1)

    def test_duplicate_version_in_bundle_inventory_is_rejected(self) -> None:
        with self.assertRaises(store_api.ReleaseError):
            store_api.ensure_version_unique([], 1, [{"versionCode": 1}])

    def test_only_completed_play_release_is_promotable(self) -> None:
        for status in ("draft", "inProgress", "halted"):
            with self.subTest(status=status):
                track = {"track": "closed-testing", "releases": [{"versionCodes": ["1"], "status": status}]}
                with self.assertRaises(store_api.ReleaseError):
                    store_api.completed_release_for_version(track, 1, "test track")
        completed = {"track": "closed-testing", "releases": [{"versionCodes": ["1"], "status": "completed"}]}
        self.assertEqual(store_api.completed_release_for_version(completed, 1, "test track")["status"], "completed")

    def test_ambiguous_duplicate_track_release_is_rejected(self) -> None:
        track = {
            "track": "internal",
            "releases": [
                {"versionCodes": ["1"], "status": "completed"},
                {"versionCodes": ["1"], "status": "completed"},
            ],
        }
        with self.assertRaises(store_api.ReleaseError):
            store_api.completed_release_for_version(track, 1, "test track")

    def test_empty_or_single_completed_destination_can_be_replaced(self) -> None:
        store_api.require_replaceable_destination(
            {"track": "production", "releases": []},
            "destination",
        )
        store_api.require_replaceable_destination(
            {
                "track": "production",
                "releases": [{"versionCodes": ["7"], "status": "completed"}],
            },
            "destination",
        )

    def test_active_or_ambiguous_destination_cannot_be_replaced(self) -> None:
        unsafe = (
            {"releases": [{"versionCodes": ["7"], "status": "draft"}]},
            {"releases": [{"versionCodes": ["7"], "status": "inProgress"}]},
            {"releases": [{"versionCodes": ["7"], "status": "halted"}]},
            {
                "releases": [
                    {"versionCodes": ["6"], "status": "completed"},
                    {"versionCodes": ["7"], "status": "completed"},
                ]
            },
            {"releases": [{"versionCodes": ["6", "7"], "status": "completed"}]},
            {"releases": "not-a-list"},
        )
        for track in unsafe:
            with self.subTest(track=track):
                with self.assertRaises(store_api.ReleaseError):
                    store_api.require_replaceable_destination(track, "destination")


class GoogleInternalRecoveryTest(unittest.TestCase):
    def candidate_files(self, directory: Path) -> tuple[SimpleNamespace, dict, dict]:
        bundle = directory / "parlor.aab"
        bundle.write_bytes(b"exact-signed-aab")
        source = source_record()
        descriptor = artifact("android")
        descriptor["size_bytes"] = bundle.stat().st_size
        descriptor["sha256"] = hashlib.sha256(bundle.read_bytes()).hexdigest()
        source_path = directory / "source.json"
        artifact_path = directory / "artifact.json"
        source_path.write_text(json.dumps(source), encoding="utf-8")
        artifact_path.write_text(json.dumps(descriptor), encoding="utf-8")
        args = SimpleNamespace(
            source=str(source_path),
            artifact=str(artifact_path),
            package="com.parlor.app",
            artifact_sha256=descriptor["sha256"],
            bundle=str(bundle),
            credentials=str(directory / "credentials.json"),
            prior_receipt=None,
        )
        return args, source, descriptor

    @staticmethod
    def completed_internal_track() -> list[dict]:
        return [
            {
                "track": "internal",
                "releases": [
                    {
                        "name": "Parlor 1.0.0 (1)",
                        "status": "completed",
                        "versionCodes": ["1"],
                    }
                ],
            }
        ]

    def test_new_candidate_cannot_adopt_an_existing_byte_identical_store_bundle(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            args, _, descriptor = self.candidate_files(Path(temporary))
            client = mock.Mock()
            client.read_inventory.return_value = (
                self.completed_internal_track(),
                [{"versionCode": 1, "sha256": descriptor["sha256"]}],
            )
            with mock.patch.object(store_api, "GoogleClient", return_value=client):
                with self.assertRaisesRegex(store_api.ReleaseError, "trusted upload intent"):
                    store_api.google_internal_execute(args)
            client.insert_edit.assert_not_called()
            client.upload_bundle.assert_not_called()

    def test_existing_version_with_different_store_bytes_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            args, _, _ = self.candidate_files(Path(temporary))
            args.recover_only = True
            client = mock.Mock()
            client.read_inventory.return_value = (
                self.completed_internal_track(),
                [{"versionCode": 1, "sha256": "9" * 64}],
            )
            with mock.patch.object(store_api, "GoogleClient", return_value=client):
                with self.assertRaisesRegex(store_api.ReleaseError, "immutable AAB digest"):
                    store_api.google_internal_execute(args)
            client.insert_edit.assert_not_called()

    def test_existing_exact_bundle_on_wrong_track_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            args, _, descriptor = self.candidate_files(Path(temporary))
            args.recover_only = True
            tracks = self.completed_internal_track()
            tracks[0]["track"] = "closed-testing"
            client = mock.Mock()
            client.read_inventory.return_value = (
                tracks,
                [{"versionCode": 1, "sha256": descriptor["sha256"]}],
            )
            with mock.patch.object(store_api, "GoogleClient", return_value=client):
                with self.assertRaisesRegex(store_api.ReleaseError, "exactly once"):
                    store_api.google_internal_execute(args)

    def test_new_upload_rejects_google_digest_mismatch_before_commit(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            args, _, _ = self.candidate_files(Path(temporary))
            client = mock.Mock()
            client.read_inventory.return_value = ([], [])
            client.insert_edit.return_value = "edit-1"
            client.upload_bundle.return_value = {"versionCode": 1, "sha256": "9" * 64}
            with mock.patch.object(store_api, "GoogleClient", return_value=client):
                with self.assertRaisesRegex(store_api.ReleaseError, "SHA-256"):
                    store_api.google_internal_execute(args)
            client.commit_edit.assert_not_called()
            client.delete_edit.assert_called_once_with("com.parlor.app", "edit-1")

    def test_recovered_upload_intent_never_sends_a_second_bundle(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            args, _, _ = self.candidate_files(Path(temporary))
            args.recover_only = True
            client = mock.Mock()
            client.read_inventory.return_value = ([], [])
            with mock.patch.object(store_api, "GoogleClient", return_value=client):
                with self.assertRaisesRegex(store_api.ReleaseError, "refuse another upload"):
                    store_api.google_internal_execute(args)
            client.insert_edit.assert_not_called()
            client.upload_bundle.assert_not_called()

    def test_recovered_upload_intent_can_resume_by_exact_store_digest(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            args, _, descriptor = self.candidate_files(Path(temporary))
            args.recover_only = True
            client = mock.Mock()
            client.read_inventory.return_value = (
                self.completed_internal_track(),
                [{"versionCode": 1, "sha256": descriptor["sha256"]}],
            )
            with mock.patch.object(store_api, "GoogleClient", return_value=client):
                result = store_api.google_internal_execute(args)
            self.assertEqual(result["upload_evidence"], "store_sha256_readback")
            client.insert_edit.assert_not_called()
            client.upload_bundle.assert_not_called()


class AppleUploadTransportTest(unittest.TestCase):
    def write(self, value: dict) -> Path:
        temporary = tempfile.NamedTemporaryFile(mode="w", delete=False)
        json.dump(value, temporary)
        temporary.close()
        self.addCleanup(Path(temporary.name).unlink)
        return Path(temporary.name)

    def test_transport_receipt_is_bound_to_exact_ipa(self) -> None:
        descriptor = artifact("ios")
        transport = {
            "schema_version": 1,
            "transport": "xcrun-altool",
            "accepted": True,
            "upload_request_id": "request-1",
            "response_sha256": "5" * 64,
            "artifact_sha256": descriptor["sha256"],
        }
        store_api.validate_apple_upload_transport(self.write(transport), descriptor, "request-1")

    def test_transport_receipt_for_other_ipa_is_rejected(self) -> None:
        descriptor = artifact("ios")
        transport = {
            "schema_version": 1,
            "transport": "xcrun-altool",
            "accepted": True,
            "upload_request_id": "request-1",
            "response_sha256": "5" * 64,
            "artifact_sha256": "9" * 64,
        }
        with self.assertRaises(store_api.ReleaseError):
            store_api.validate_apple_upload_transport(self.write(transport), descriptor, "request-1")


class AppleStoreReadbackTest(unittest.TestCase):
    def test_store_candidate_requires_current_app_store_eligible_build_metadata(self) -> None:
        attributes = store_api.validate_store_eligible_apple_build(eligible_apple_build(), "1")
        self.assertEqual(attributes["buildAudienceType"], "APP_STORE_ELIGIBLE")
        self.assertFalse(attributes["expired"])
        for mutation in (
            {"expired": True},
            {"buildAudienceType": "INTERNAL_ONLY"},
            {"usesNonExemptEncryption": None},
            {"minOsVersion": "17.0"},
            {"processingState": "PROCESSING"},
        ):
            with self.subTest(mutation=mutation):
                with self.assertRaises(store_api.ReleaseError):
                    store_api.validate_store_eligible_apple_build(
                        eligible_apple_build(**mutation),
                        "1",
                    )

    def test_build_group_readback_uses_supported_beta_group_filter(self) -> None:
        client = object.__new__(store_api.AppleClient)
        client.paginated_data = mock.Mock(return_value=[])
        self.assertEqual(client.build_groups("build-1"), [])
        path = client.paginated_data.call_args.args[0]
        self.assertTrue(path.startswith("/betaGroups?"))
        self.assertIn("filter%5Bbuilds%5D=build-1", path)
        self.assertNotIn("/builds/build-1/betaGroups", path)

    def write(self, value: dict) -> Path:
        temporary = tempfile.NamedTemporaryFile(mode="w", delete=False)
        json.dump(value, temporary)
        temporary.close()
        self.addCleanup(Path(temporary.name).unlink)
        return Path(temporary.name)

    def test_external_receipt_uses_beta_review_readback(self) -> None:
        candidate = manifest()

        class Client:
            def app(self, _app_id: str) -> dict:
                return {"data": {"id": "app-1", "attributes": {"bundleId": "com.parlor.app"}}}

            def group(self, _group_id: str) -> dict:
                return {
                    "data": {
                        "id": "external-group-1",
                        "attributes": {"isInternalGroup": False},
                    }
                }

            def group_app(self, _group_id: str) -> dict:
                return {"data": {"type": "apps", "id": "app-1"}}

            def build(self, _build_id: str) -> dict:
                return {"data": eligible_apple_build()}

            def add_build_to_group(self, _build_id: str, _group_id: str) -> bool:
                return True

            def submit_beta_review(self, _build_id: str) -> dict:
                return {"id": "review-1", "attributes": {"betaReviewState": "WAITING_FOR_REVIEW"}}

            def beta_review(self, _build_id: str) -> dict:
                return {"id": "review-1", "attributes": {"betaReviewState": "APPROVED"}}

        args = mock.Mock(
            manifest=str(self.write(candidate)),
            app_id="app-1",
            bundle_id="com.parlor.app",
            external_group_id="external-group-1",
        )
        with mock.patch.object(store_api, "apple_client", return_value=Client()):
            receipt = store_api.apple_external_execute(args)
        self.assertEqual(receipt["state"], "available_to_external_testers")
        self.assertEqual(receipt["beta_review_state"], "APPROVED")

    def test_beta_group_for_another_app_is_rejected_before_association(self) -> None:
        response = {
            "data": {
                "id": "external-group-1",
                "attributes": {"isInternalGroup": False},
            }
        }
        with self.assertRaises(store_api.ReleaseError):
            store_api.validate_beta_group(
                response,
                {"data": {"type": "apps", "id": "other-app"}},
                "external-group-1",
                "app-1",
                internal=False,
            )

    def test_beta_group_app_binding_uses_the_relationship_endpoint(self) -> None:
        client = object.__new__(store_api.AppleClient)
        client.request = mock.Mock(return_value={"data": {"type": "apps", "id": "app-1"}})
        self.assertEqual(client.group_app("group-1")["data"]["id"], "app-1")
        client.request.assert_called_once_with(
            "GET",
            "/betaGroups/group-1/relationships/app",
            safe_retry=True,
        )

    def test_candidate_rejects_a_different_app_store_connect_app_id(self) -> None:
        candidate = manifest()
        args = mock.Mock(app_id="other-app", bundle_id="com.parlor.app")
        with self.assertRaises(store_api.ReleaseError):
            store_api.validate_apple_manifest_args(candidate, args)

    def test_production_rejects_lost_external_group_membership(self) -> None:
        candidate = manifest()
        external = ValidationOnlyTest.external_receipt(candidate, "ios")

        class Client:
            def app(self, _app_id: str) -> dict:
                return {"data": {"id": "app-1", "attributes": {"bundleId": "com.parlor.app"}}}

            def build(self, _build_id: str) -> dict:
                return {"data": eligible_apple_build()}

            def build_groups(self, _build_id: str) -> list[dict]:
                return []

        args = mock.Mock(
            manifest=str(self.write(candidate)),
            external_receipt=str(self.write(external)),
            app_id="app-1",
            bundle_id="com.parlor.app",
            app_store_version_id="version-1",
            submit=False,
        )
        with mock.patch.object(store_api, "apple_client", return_value=Client()):
            with self.assertRaises(store_api.ReleaseError):
                store_api.apple_production_execute(args)

    def test_review_submission_requires_api_readback(self) -> None:
        client = object.__new__(store_api.AppleClient)
        client.submission_for_version = mock.Mock(side_effect=[None, None])
        client.reusable_empty_review_submission = mock.Mock(return_value=None)
        client.request = mock.Mock(
            side_effect=[
                {"data": {"id": "submission-1"}},
                {},
                {"data": {"id": "submission-1"}},
            ]
        )
        with self.assertRaises(store_api.ReleaseError):
            client.create_and_submit_review("app-1", "version-1")

    def test_review_submission_rerun_reuses_one_empty_draft(self) -> None:
        draft = {"id": "submission-1", "attributes": {"state": "READY_FOR_REVIEW"}}
        submitted = {"id": "submission-1", "attributes": {"state": "WAITING_FOR_REVIEW"}}
        client = object.__new__(store_api.AppleClient)
        client.submission_for_version = mock.Mock(side_effect=[None, submitted])
        client.reusable_empty_review_submission = mock.Mock(return_value=draft)
        client.request = mock.Mock(return_value={})
        self.assertEqual(client.create_and_submit_review("app-1", "version-1"), submitted)
        request_paths = [call.args[1] for call in client.request.call_args_list]
        self.assertNotIn("/reviewSubmissions", request_paths)
        self.assertIn("/reviewSubmissionItems", request_paths)
        self.assertIn("/reviewSubmissions/submission-1", request_paths)

    def test_unrelated_active_review_submission_fails_closed(self) -> None:
        client = object.__new__(store_api.AppleClient)
        client.list_review_submissions = mock.Mock(
            return_value=[{"id": "other-submission", "attributes": {"state": "READY_FOR_REVIEW"}}]
        )
        client.submission_items = mock.Mock(
            return_value=[{"relationships": {"appStoreVersion": {"data": {"id": "other-version"}}}}]
        )
        with self.assertRaisesRegex(store_api.ReleaseError, "Another active"):
            client.reusable_empty_review_submission("app-1")

    def test_beta_review_state_is_not_accepted_as_a_production_review_state(self) -> None:
        client = object.__new__(store_api.AppleClient)
        client.submission_for_version = mock.Mock(
            return_value={"id": "submission-1", "attributes": {"state": "APPROVED"}}
        )
        client.request = mock.Mock()
        with self.assertRaises(store_api.ReleaseError):
            client.create_and_submit_review("app-1", "version-1")
        client.request.assert_not_called()

    def test_existing_submitted_production_review_is_resumed_without_mutation(self) -> None:
        expected = {"id": "submission-1", "attributes": {"state": "IN_REVIEW"}}
        client = object.__new__(store_api.AppleClient)
        client.submission_for_version = mock.Mock(return_value=expected)
        client.request = mock.Mock()
        self.assertEqual(client.create_and_submit_review("app-1", "version-1"), expected)
        client.request.assert_not_called()

    def test_apple_pagination_is_bounded_to_official_host(self) -> None:
        client = object.__new__(store_api.AppleClient)
        client.request = mock.Mock(
            side_effect=[
                {
                    "data": [{"id": "one"}],
                    "links": {"next": "https://api.appstoreconnect.apple.com/v1/apps/app-1/reviewSubmissions?cursor=next"},
                },
                {"data": [{"id": "two"}], "links": {}},
            ]
        )
        self.assertEqual(
            [item["id"] for item in client.paginated_data("/apps/app-1/reviewSubmissions?limit=200")],
            ["one", "two"],
        )
        self.assertEqual(client.request.call_count, 2)

    def test_apple_pagination_rejects_foreign_redirect_target(self) -> None:
        client = object.__new__(store_api.AppleClient)
        client.request = mock.Mock(
            return_value={"data": [], "links": {"next": "https://attacker.invalid/v1/builds"}}
        )
        with self.assertRaises(store_api.ReleaseError):
            client.paginated_data("/builds?limit=200")


class ValidationOnlyTest(unittest.TestCase):
    def write_json(self, directory: Path, name: str, value: dict) -> Path:
        path = directory / name
        path.write_text(json.dumps(value), encoding="utf-8")
        return path

    def run_without_store_client(self, argv: list[str]) -> None:
        with (
            mock.patch.object(sys, "argv", argv),
            mock.patch.object(store_api, "GoogleClient", side_effect=AssertionError("Google client created")),
            mock.patch.object(store_api, "apple_client", side_effect=AssertionError("Apple client created")),
        ):
            self.assertEqual(store_api.main(), 0)

    def test_execute_mode_cannot_bypass_blocked_store_identity_ownership(self) -> None:
        commands = [
            [
                "store_api.py",
                "google-check-unique",
                "--package", "com.parlor.app",
                "--version-code", "1",
                "--credentials", "/private/credential.json",
                "--execute",
            ],
            [
                "store_api.py",
                "apple-check-unique",
                "--app-id", "app-1",
                "--bundle-id", "com.parlor.app",
                "--build-number", "1",
                "--issuer-id", "issuer-1",
                "--key-id", "KEY1234567",
                "--private-key", "/private/AuthKey.p8",
                "--execute",
            ],
        ]
        for argv in commands:
            with self.subTest(command=argv[1]), mock.patch.object(sys, "argv", argv):
                with self.assertRaisesRegex(store_api.ReleaseError, "known public Store collision"):
                    store_api.main()

    @staticmethod
    def external_receipt(candidate: dict, platform: str) -> dict:
        common = {
            "schema_version": 1,
            "platform": platform,
            "operation": "external_promotion",
            "candidate_commit_sha": candidate["source"]["commit_sha"],
            "artifact_sha256": candidate["artifacts"][platform]["sha256"],
            "read_back_at": "2026-08-15T12:00:00Z",
        }
        if platform == "android":
            return {
                **common,
                "package_name": "com.parlor.app",
                "version_code": 1,
                "source_track": "internal",
                "destination_track": "closed-testing",
                "release_status": "completed",
                "edit_id": "edit-1",
                "result": "committed",
                "state": "external_track_committed",
            }
        return {
            **common,
            "bundle_id": "com.parlor.app",
            "build_number": "1",
            "build_id": "apple-build-1",
            "external_group_id": "external-group-1",
            "beta_review_submission_id": "review-1",
            "beta_review_state": "APPROVED",
            "state": "available_to_external_testers",
        }

    def test_google_internal_validation_only_never_constructs_store_client(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            bundle = directory / "parlor.aab"
            bundle.write_bytes(b"signed-aab")
            descriptor = artifact("android")
            descriptor["size_bytes"] = bundle.stat().st_size
            descriptor["sha256"] = hashlib.sha256(bundle.read_bytes()).hexdigest()
            source_path = directory / "source.json"
            artifact_path = directory / "artifact.json"
            output = directory / "result.json"
            source_path.write_text(json.dumps(source_record()))
            artifact_path.write_text(json.dumps(descriptor))
            argv = [
                "store_api.py",
                "google-internal",
                "--source",
                str(source_path),
                "--artifact",
                str(artifact_path),
                "--bundle",
                str(bundle),
                "--artifact-sha256",
                descriptor["sha256"],
                "--package",
                "com.parlor.app",
                "--output",
                str(output),
            ]
            with (
                mock.patch.object(sys, "argv", argv),
                mock.patch.object(store_api, "GoogleClient", side_effect=AssertionError("network client created")),
            ):
                self.assertEqual(store_api.main(), 0)
            result = json.loads(output.read_text())
            self.assertEqual(result["mode"], "validation_only")
            self.assertFalse(result["mutation_performed"])

    def test_google_unique_validation_mode_avoids_store_client(self) -> None:
        self.run_without_store_client(
            [
                "store_api.py",
                "google-check-unique",
                "--package",
                "com.parlor.app",
                "--version-code",
                "1",
            ]
        )

    def test_every_promotion_validation_mode_avoids_store_clients(self) -> None:
        candidate = manifest()
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            candidate_path = self.write_json(directory, "candidate.json", candidate)
            android_external = self.write_json(
                directory,
                "external-android.json",
                self.external_receipt(candidate, "android"),
            )
            ios_external = self.write_json(
                directory,
                "external-ios.json",
                self.external_receipt(candidate, "ios"),
            )
            commands = [
                [
                    "store_api.py",
                    "google-promote",
                    "--manifest", str(candidate_path),
                    "--operation", "external",
                    "--source-track", "internal",
                    "--destination-track", "closed-testing",
                    "--package", "com.parlor.app",
                    "--output", str(directory / "google-external-plan.json"),
                ],
                [
                    "store_api.py",
                    "google-promote",
                    "--manifest", str(candidate_path),
                    "--operation", "production",
                    "--source-track", "closed-testing",
                    "--destination-track", "production",
                    "--package", "com.parlor.app",
                    "--external-receipt", str(android_external),
                    "--output", str(directory / "google-production-plan.json"),
                ],
                [
                    "store_api.py",
                    "apple-external",
                    "--manifest", str(candidate_path),
                    "--app-id", "app-1",
                    "--bundle-id", "com.parlor.app",
                    "--external-group-id", "external-group-1",
                    "--output", str(directory / "apple-external-plan.json"),
                ],
                [
                    "store_api.py",
                    "apple-production",
                    "--manifest", str(candidate_path),
                    "--external-receipt", str(ios_external),
                    "--app-id", "app-1",
                    "--bundle-id", "com.parlor.app",
                    "--app-store-version-id", "version-1",
                    "--submit",
                    "--output", str(directory / "apple-production-plan.json"),
                ],
            ]
            for argv in commands:
                with self.subTest(command=argv[1]):
                    self.run_without_store_client(argv)
                    plan = json.loads(Path(argv[-1]).read_text(encoding="utf-8"))
                    self.assertEqual(plan["mode"], "validation_only")
                    self.assertFalse(plan["mutation_performed"])

    def test_apple_internal_validation_mode_avoids_store_client(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            source_path = self.write_json(directory, "source.json", source_record())
            descriptor = artifact("ios")
            artifact_path = self.write_json(directory, "artifact.json", descriptor)
            output = directory / "apple-internal-plan.json"
            self.run_without_store_client(
                [
                    "store_api.py",
                    "apple-internal",
                    "--source", str(source_path),
                    "--artifact", str(artifact_path),
                    "--artifact-sha256", descriptor["sha256"],
                    "--app-id", "app-1",
                    "--internal-group-id", "internal-group-1",
                    "--output", str(output),
                ]
            )
            plan = json.loads(output.read_text(encoding="utf-8"))
            self.assertFalse(plan["mutation_performed"])

    def test_apple_unique_validation_mode_avoids_store_client(self) -> None:
        self.run_without_store_client(
            [
                "store_api.py",
                "apple-check-unique",
                "--app-id", "app-1",
                "--bundle-id", "com.parlor.app",
                "--build-number", "1",
            ]
        )


if __name__ == "__main__":
    unittest.main()
