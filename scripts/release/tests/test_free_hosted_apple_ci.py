"""Release fan-out must be free, complete, source-bound and fail closed."""
from __future__ import annotations

from copy import deepcopy
import hashlib
import json
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch

from scripts.ci import apple_release_gate as gate
from scripts.release import workflow_contract as contract


class AppleReleaseGateTest(unittest.TestCase):
    def setUp(self):
        self.head, self.tree = "a" * 40, "b" * 40
        self.needs = {lane: dict(result="success", outputs=dict(
            source_sha=self.head, source_tree=self.tree, evidence_id=str(123 + index),
            evidence_sha256="c" * 64, cleanup_sha256="d" * 64)) for index, lane in enumerate(gate.LANES)}

    def test_all_three_successful_current_lanes_qualify(self):
        value = gate.aggregate(self.needs, self.head, self.tree)
        self.assertEqual((value["result"], value["lanes"], value["source_sha"]), ("PASS", self.needs, self.head))

    def test_failed_cancelled_skipped_or_missing_lane_never_qualifies(self):
        for lane in gate.LANES:
            for result in ("failure", "cancelled", "skipped", "", None):
                needs = deepcopy(self.needs)
                needs[lane]["result"] = result
                with self.subTest(lane=lane, result=result), self.assertRaises(RuntimeError):
                    gate.aggregate(needs, self.head, self.tree)
            needs = deepcopy(self.needs)
            del needs[lane]
            with self.subTest(lane=lane), self.assertRaises(RuntimeError):
                gate.aggregate(needs, self.head, self.tree)

    def test_stale_source_missing_or_malformed_custody_never_qualifies(self):
        for lane in gate.LANES:
            for field in gate.FIELDS:
                for replacement in (None, "", "x\nsource_sha=forged", "0", "e" * 64):
                    needs = deepcopy(self.needs)
                    needs[lane]["outputs"][field] = replacement
                    if field.endswith("sha256") and replacement == "e" * 64:
                        continue  # A different syntactically valid digest is allowed.
                    with self.subTest(lane=lane, field=field, replacement=replacement), self.assertRaises(RuntimeError):
                        gate.aggregate(needs, self.head, self.tree)
                needs = deepcopy(self.needs)
                del needs[lane]["outputs"][field]
                with self.assertRaises(RuntimeError):
                    gate.aggregate(needs, self.head, self.tree)

    def test_unknown_lanes_outputs_and_duplicate_artifacts_are_rejected(self):
        for mutation in (lambda n: n.update(unreviewed=n[gate.LANES[0]]),
                         lambda n: n[gate.LANES[0]]["outputs"].update(unreviewed="yes"),
                         lambda n: n[gate.LANES[1]]["outputs"].update(evidence_id="123")):
            needs = deepcopy(self.needs)
            mutation(needs)
            with self.assertRaises(RuntimeError):
                gate.aggregate(needs, self.head, self.tree)

    def test_lane_identity_requires_current_source_and_successful_native_cycles(self):
        with TemporaryDirectory() as temporary:
            root = Path(temporary).resolve()
            prefix = root / "claim"
            source = dict(root=str(root), head=self.head, tree=self.tree, outputs=[])
            task = dict(GITHUB_JOB="ios-release-simulator", GITHUB_RUN_ID="123", GITHUB_RUN_ATTEMPT="1")
            claim = {**source, "task": task}
            cleanup = dict(task=task, result="PASS", errors=[], retained=[], preparation_outcome="success",
                           gradle_stop=dict(exit_code=0), upload=dict(outcome="success", artifact_id="456", artifact_digest="c" * 64),
                           apple_cleanup={})
            receipts = {}
            for cycle in ("apple-aggregate", "apple-wrapper"):
                receipt = dict(task=task, result="PASS", source={k: source[k] for k in ("root", "head", "tree")},
                               prepare_outcome="success", run_outcome="success", errors=[])
                path = Path(str(prefix) + "-stop-" + cycle + ".json")
                path.write_text(json.dumps(receipt))
                receipts[cycle] = (receipt, path)
                cleanup["apple_cleanup"][cycle] = dict(result="PASS", sha256=hashlib.sha256(path.read_bytes()).hexdigest())
            claim_path = Path(str(prefix) + "-ownership.json")
            cleanup_path = Path(str(prefix) + "-cleanup.json")
            claim_path.write_text(json.dumps(claim))
            cleanup_path.write_text(json.dumps(cleanup))
            with patch.object(gate.hygiene, "source_identity", return_value=source), patch.object(gate.hygiene, "git", return_value=""):
                self.assertEqual(gate.lane_identity(root, prefix, task)["source_sha"], self.head)
                for cycle, (receipt, path) in receipts.items():
                    for result in ("failure", "cancelled", "skipped"):
                        path.write_text(json.dumps({**receipt, "run_outcome": result}))
                        with self.subTest(cycle=cycle, result=result), self.assertRaises(RuntimeError):
                            gate.lane_identity(root, prefix, task)
                    path.write_text(json.dumps(receipt))
                claim_path.write_text(json.dumps({**claim, "head": "f" * 40}))
                with self.assertRaises(RuntimeError):
                    gate.lane_identity(root, prefix, task)
                claim_path.write_text(json.dumps(claim))
                for field, value in (("result", "FAIL"), ("retained", ["build"]), ("apple_cleanup", {})):
                    cleanup_path.write_text(json.dumps({**cleanup, field: value}))
                    with self.subTest(field=field), self.assertRaises(RuntimeError):
                        gate.lane_identity(root, prefix, task)

    def test_bounded_receipt_reader_rejects_links_large_files_and_non_objects(self):
        with TemporaryDirectory() as temporary:
            root = Path(temporary)
            target = root / "value.json"
            for text in ("[]", " " * (1024 * 1024 + 1)):
                target.write_text(text)
                with self.assertRaises(RuntimeError):
                    gate.read_json(target)
            target.write_text("{}")
            link = root / "link.json"
            link.symlink_to(target)
            with self.assertRaises(RuntimeError):
                gate.read_json(link)


class FreeHostedCIContractTest(unittest.TestCase):
    def setUp(self):
        self.workflow = (contract.ROOT / ".github/workflows/production-verification.yml").read_text()

    def test_current_workflow_is_complete_and_free_only(self):
        contract.verify_validation(self.workflow)
        self.assertEqual(self.workflow.count(contract.FREE_APPLE_RELEASE_RUNNER), 3)
        self.assertNotIn("OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED=YES", self.workflow)
        self.assertNotIn("smallBinary", self.workflow)

    def test_required_gate_cannot_drop_architectures_or_accept_optional_results(self):
        for old, new in (("needs: [ios-release-simulator, ios-release-device, ios-release-x64]", "needs: [ios-release-simulator]"),
                         ("run: python3 -B -m scripts.ci.apple_release_gate aggregate", "run: true"),
                         ("PARLOR_APPLE_RELEASE_NEEDS: ${{ toJSON(needs) }}", "PARLOR_APPLE_RELEASE_NEEDS: '{}'")):
            changed = self.workflow.replace(old, new)
            self.assertNotEqual(changed, self.workflow)
            with self.assertRaises(RuntimeError):
                contract.verify_validation(changed)

    def test_release_runners_cannot_use_paid_or_arbitrary_labels(self):
        for label in ("macos-15-large", "macos-15-xlarge", "self-hosted", "${{ inputs.apple_release_runner }}"):
            changed = self.workflow.replace(contract.FREE_APPLE_RELEASE_RUNNER, label, 1)
            with self.subTest(label=label), self.assertRaises(RuntimeError):
                contract.verify_validation(changed)

    def test_caches_cannot_write_on_prs_or_restore_reports_builds_or_secrets(self):
        for old, new in (("if: success() && github.event_name == 'push' && github.ref == 'refs/heads/main'", "if: always()"),
                         ("~/.gradle/caches/modules-2", "~/.gradle/caches"), ("~/.gradle/wrapper/dists", "~/.konan"),
                         ("~/.gradle/wrapper/dists", "composeApp/build"), ("actions/cache/restore", "gradle/actions/setup-gradle")):
            changed = self.workflow.replace(old, new, 1)
            with self.subTest(old=old, new=new), self.assertRaises(RuntimeError):
                contract.verify_validation(changed)


if __name__ == "__main__":
    unittest.main()
