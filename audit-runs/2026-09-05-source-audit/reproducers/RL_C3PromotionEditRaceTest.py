"""Audit-only reproducer; run only in root's shared verification lane.

Calls production google_promote_execute and inherited GoogleClient edit helpers,
but replaces the HTTP request boundary with synthetic edit snapshots. No Store
API, credentials, key creation, signing, or CLI --execute invocation occurs.
Google's documented edit-copy/invalidation rules are modeled, not device/Store
runtime verified. Existing schema-valid test fixture is reused unmodified.
"""
from __future__ import annotations

import copy
import json
import sys
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest import mock

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "scripts/release"))
sys.path.insert(0, str(ROOT / "scripts/release/tests"))
import store_api  # noqa: E402
from test_release_tool import manifest  # noqa: E402


class SnapshotClient(store_api.GoogleClient):
    """Overrides request only: production edit/read/update helpers are retained."""

    def __init__(self, candidate: dict, interleave: str):
        # Intentionally do not load or manufacture a real credentials file.
        self.package = candidate["applications"]["android_application_id"]
        self.version = candidate["version"]["android_version_code"]
        self.digest = candidate["artifacts"]["android"]["sha256"]
        self.interleave = interleave
        self.live = [
            {"track": "closed-testing", "releases": [{"versionCodes": [str(self.version)], "status": "completed"}]},
            {"track": "production", "releases": []},
        ]
        self.edits: dict[str, list[dict]] = {}
        self.invalidated: set[str] = set()
        self.sequence = 0
        self.puts = 0
        self.commits = 0
        self.trace: list[dict] = []

    def console_stages_candidate(self) -> None:
        # Same already-uploaded candidate at 5% avoids rollback/version-order
        # assumptions: Google's documented next step may complete this release.
        self.live[1] = {
            "track": "production",
            "releases": [{"versionCodes": [str(self.version)], "status": "inProgress", "userFraction": 0.05}],
        }
        self.invalidated.update(self.edits)
        self.trace.append({"event": "synthetic_console_stage", "open_edits_invalidated": sorted(self.edits)})

    def request(self, method, path, body=None, *, safe_retry=False, expected=(200,)):
        prefix = f"/applications/{store_api.quote(self.package)}/edits"
        if method == "POST" and path == prefix:
            self.sequence += 1
            edit_id = f"edit-{self.sequence}"
            # Existing caller creates only one edit per user; mimic new-edit
            # invalidation too so the test never relies on unsupported overlap.
            self.invalidated.update(self.edits)
            self.edits[edit_id] = copy.deepcopy(self.live)
            self.trace.append({"event": "insert", "id": edit_id, "destination": copy.deepcopy(self.live[1])})
            if self.interleave == "after-mutation-edit" and edit_id == "edit-2":
                self.console_stages_candidate()
            return {"id": edit_id}
        if not path.startswith(prefix + "/"):
            raise AssertionError(f"Unexpected synthetic API path: {method} {path}")
        relative = path[len(prefix) + 1 :]
        token, _, resource = relative.partition("/")
        edit_id, separator, operation = token.partition(":")
        self.trace.append({"event": method, "id": edit_id, "resource": operation if separator else resource})
        if method == "DELETE" and not resource and not separator:
            self.edits.pop(edit_id, None)
            self.invalidated.discard(edit_id)
            if self.interleave == "between-edits" and edit_id == "edit-1":
                self.console_stages_candidate()
            return {}
        if edit_id in self.invalidated or edit_id not in self.edits:
            raise store_api.ReleaseError("synthetic edit invalidated by Console change")
        if method == "GET" and resource == "tracks":
            return {"tracks": copy.deepcopy(self.edits[edit_id])}
        if method == "GET" and resource == "bundles":
            return {"bundles": [{"versionCode": self.version, "sha256": self.digest}]}
        if method == "PUT" and resource == "tracks/production":
            self.puts += 1
            self.edits[edit_id][1] = copy.deepcopy(body)
            return copy.deepcopy(body)
        if method == "POST" and operation == "validate":
            return {}
        if method == "POST" and operation == "commit":
            self.commits += 1
            self.live = self.edits.pop(edit_id)
            self.invalidated.update(self.edits)
            return {}
        raise AssertionError(f"Unexpected synthetic request: {method} {path}")


class PromotionEditRaceTest(unittest.TestCase):
    def run_promotion(self, interleave: str):
        candidate = manifest()
        client = SnapshotClient(candidate, interleave)
        outcome = None
        error = None
        with tempfile.TemporaryDirectory(prefix="parlor-audit-rl-c3-") as temporary:
            manifest_path = Path(temporary) / "candidate.json"
            manifest_path.write_text(json.dumps(candidate), encoding="utf-8")
            args = SimpleNamespace(
                manifest=str(manifest_path), package=client.package,
                source_track="closed-testing", destination_track="production",
                operation="production", credentials=str(Path(temporary) / "nonexistent.json"),
            )
            with (
                mock.patch.object(store_api, "GoogleClient", return_value=client),
                mock.patch.object(store_api, "open_store_request", side_effect=AssertionError("real network forbidden")),
                mock.patch.object(store_api, "openssl_sign", side_effect=AssertionError("real signing forbidden")),
            ):
                try:
                    outcome = store_api.google_promote_execute(args)
                except store_api.ReleaseError as caught:
                    error = str(caught)
        print(json.dumps({"synthetic_only": True, "interleave": interleave, "outcome": outcome,
                          "error": error, "puts": client.puts, "commits": client.commits,
                          "final_destination": client.live[1], "trace": client.trace}, sort_keys=True))
        return client, outcome, error

    def test_newly_staged_destination_between_edits_must_be_refused(self):
        client, outcome, error = self.run_promotion("between-edits")
        self.assertIsNotNone(error, "Promotion silently completed the newly staged 5% rollout instead of refusing it")
        self.assertIsNone(outcome)
        self.assertEqual(client.puts, 0)
        self.assertEqual(client.commits, 0)
        self.assertEqual(client.live[1]["releases"][0]["status"], "inProgress")

    def test_counterevidence_change_after_mutation_insert_invalidates_edit(self):
        client, outcome, error = self.run_promotion("after-mutation-edit")
        self.assertIn("invalidated", error or "")
        self.assertIsNone(outcome)
        self.assertEqual(client.commits, 0)
        self.assertEqual(client.live[1]["releases"][0]["status"], "inProgress")


if __name__ == "__main__":
    unittest.main(verbosity=2)
