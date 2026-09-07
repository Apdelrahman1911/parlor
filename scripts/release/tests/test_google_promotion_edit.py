from __future__ import annotations

import copy
import json
import sys
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import store_api  # noqa: E402
from test_release_tool import manifest  # noqa: E402


class EditSnapshotClient(store_api.GoogleClient):
    """Production edit helpers with only the HTTP boundary replaced.

    Models documented Play edit snapshots and external invalidation. Never
    loads credentials or calls the network; it is not real Store evidence.
    """

    def __init__(self, candidate: dict) -> None:
        self.package = candidate["applications"]["android_application_id"]
        self.version = candidate["version"]["android_version_code"]
        self.release = {"versionCodes": [str(self.version)], "status": "completed"}
        self.live_tracks = [
            {"track": "closed-testing", "releases": [copy.deepcopy(self.release)]},
            {"track": "production", "releases": []},
        ]
        self.live_bundles = [{"versionCode": self.version, "sha256": candidate["artifacts"]["android"]["sha256"]}]
        self.edits: dict[str, dict] = {}
        self.invalidated: set[str] = set()
        self.trace: list[tuple[str, str, str]] = []
        self.sequence = 0
        self.fail_operation = ""
        self.stage_after_bundle_read = False

    def request(self, method, path, body=None, *, safe_retry=False, expected=(200,)):
        if method != "GET":
            assert not safe_retry, "Mutation must never be blindly retried"
        prefix = f"/applications/{store_api.quote(self.package)}/edits"
        if method == "POST" and path == prefix:
            self.sequence += 1
            edit_id = f"edit-{self.sequence}"
            self.invalidated.update(self.edits)
            self.edits[edit_id] = {
                "tracks": copy.deepcopy(self.live_tracks),
                "bundles": copy.deepcopy(self.live_bundles),
            }
            self.trace.append(("INSERT", edit_id, ""))
            return {"id": edit_id}
        assert path.startswith(prefix + "/"), path
        token, _, resource = path[len(prefix) + 1:].partition("/")
        edit_id, _, operation = token.partition(":")
        resource = operation or resource
        self.trace.append((method, edit_id, resource))
        if method == "DELETE":
            self.edits.pop(edit_id, None)
            self.invalidated.discard(edit_id)
            return {}
        if edit_id in self.invalidated or edit_id not in self.edits:
            raise store_api.ReleaseError("synthetic edit invalidated by external change")
        if operation and operation == self.fail_operation:
            raise store_api.ReleaseError(f"synthetic {operation} failure")
        snapshot = self.edits[edit_id]
        if method == "GET" and resource in {"tracks", "bundles"}:
            response = {resource: copy.deepcopy(snapshot[resource])}
            if resource == "bundles" and self.stage_after_bundle_read:
                self.live_tracks[1]["releases"] = [dict(self.release, status="inProgress", userFraction=0.05)]
                self.invalidated.update(self.edits)
            return response
        if method == "PUT" and resource == "tracks/production":
            snapshot["tracks"][1] = copy.deepcopy(body)
            return copy.deepcopy(body)
        if method == "POST" and operation == "validate":
            return {}
        if method == "POST" and operation == "commit":
            self.live_tracks = snapshot["tracks"]
            self.live_bundles = snapshot["bundles"]
            del self.edits[edit_id]
            return {}
        raise AssertionError((method, path))


class GooglePromotionEditTest(unittest.TestCase):
    def setUp(self) -> None:
        temporary = tempfile.TemporaryDirectory(prefix="parlor-promotion-fixture-")
        self.addCleanup(temporary.cleanup)
        root = Path(temporary.name)
        candidate = manifest()
        manifest_path = root / "manifest.json"
        manifest_path.write_text(json.dumps(candidate))
        self.client = EditSnapshotClient(candidate)
        self.args = SimpleNamespace(
            manifest=str(manifest_path), package=self.client.package,
            source_track="closed-testing", destination_track="production",
            operation="production", credentials=str(root / "nonexistent.json"),
        )

    def promote(self) -> dict:
        with (
            mock.patch.object(store_api, "GoogleClient", return_value=self.client),
            mock.patch.object(store_api, "open_store_request", side_effect=AssertionError("network forbidden")),
            mock.patch.object(store_api, "openssl_sign", side_effect=AssertionError("signing forbidden")),
        ):
            return store_api.google_promote_execute(self.args)

    def assert_no_mutation(self) -> None:
        self.assertFalse(any(method == "PUT" or resource == "commit" for method, _, resource in self.client.trace))
        self.assertEqual(self.client.edits, {}, "Rejected/idempotent edits must be deleted")

    def test_read_validation_and_commit_use_the_same_edit_snapshot(self) -> None:
        receipt = self.promote()
        edit_id = receipt["edit_id"]
        self.assertEqual(receipt["result"], "committed")
        self.assertEqual(self.client.trace[:6], [
            ("INSERT", edit_id, ""),
            ("GET", edit_id, "tracks"),
            ("GET", edit_id, "bundles"),
            ("PUT", edit_id, "tracks/production"),
            ("POST", edit_id, "validate"),
            ("POST", edit_id, "commit"),
        ])
        # The only other edit is the preserved post-commit readback.
        self.assertEqual(self.client.trace[6:], [
            ("INSERT", "edit-2", ""), ("GET", "edit-2", "tracks"),
            ("GET", "edit-2", "bundles"), ("DELETE", "edit-2", ""),
        ])
        self.assertEqual(self.client.edits, {})

    def test_external_change_after_reads_invalidates_mutation_without_retry(self) -> None:
        self.client.stage_after_bundle_read = True
        with self.assertRaisesRegex(store_api.ReleaseError, "invalidated"):
            self.promote()
        self.assertEqual(self.client.live_tracks[1]["releases"][0]["status"], "inProgress")
        self.assertEqual(self.client.sequence, 1)
        self.assertEqual(self.client.edits, {})
        self.assertFalse(any(resource == "commit" for _, _, resource in self.client.trace))

    def test_staged_destination_in_mutation_snapshot_is_not_completed(self) -> None:
        self.client.live_tracks[1]["releases"] = [dict(self.client.release, status="inProgress", userFraction=0.05)]
        with self.assertRaisesRegex(store_api.ReleaseError, "staged"):
            self.promote()
        self.assert_no_mutation()
        self.assertEqual(self.client.live_tracks[1]["releases"][0]["userFraction"], 0.05)

    def test_wrong_bundle_digest_in_mutation_snapshot_is_rejected(self) -> None:
        self.client.live_bundles[0]["sha256"] = "9" * 64
        with self.assertRaisesRegex(store_api.ReleaseError, "immutable AAB digest"):
            self.promote()
        self.assert_no_mutation()

    def test_unavailable_or_unfinished_source_in_mutation_snapshot_is_rejected(self) -> None:
        for releases in ([], [dict(self.client.release, status="draft")]):
            with self.subTest(releases=releases):
                self.client.live_tracks[0]["releases"] = releases
                with self.assertRaises(store_api.ReleaseError):
                    self.promote()
                self.assert_no_mutation()

    def test_missing_configured_destination_is_rejected(self) -> None:
        self.client.live_tracks.pop()
        with self.assertRaisesRegex(store_api.ReleaseError, "destination track does not exist"):
            self.promote()
        self.assert_no_mutation()

    def test_already_present_candidate_is_validated_and_edit_is_deleted(self) -> None:
        self.client.live_tracks[1]["releases"] = [copy.deepcopy(self.client.release)]
        receipt = self.promote()
        self.assertEqual(receipt["result"], "already_present")
        self.assertEqual(receipt["edit_id"], "")
        self.assert_no_mutation()
        self.assertEqual(self.client.sequence, 1)

    def test_validation_failure_deletes_uncommitted_edit_without_retry(self) -> None:
        self.client.fail_operation = "validate"
        with self.assertRaisesRegex(store_api.ReleaseError, "validate failure"):
            self.promote()
        self.assertEqual(self.client.edits, {})
        self.assertEqual(self.client.live_tracks[1]["releases"], [])
        self.assertEqual(sum(resource == "validate" for _, _, resource in self.client.trace), 1)
        self.assertFalse(any(resource == "commit" for _, _, resource in self.client.trace))

    def test_commit_failure_is_not_blindly_retried(self) -> None:
        self.client.fail_operation = "commit"
        with self.assertRaisesRegex(store_api.ReleaseError, "commit failure"):
            self.promote()
        self.assertEqual(self.client.edits, {})
        self.assertEqual(self.client.live_tracks[1]["releases"], [])
        self.assertEqual(sum(resource == "commit" for _, _, resource in self.client.trace), 1)
