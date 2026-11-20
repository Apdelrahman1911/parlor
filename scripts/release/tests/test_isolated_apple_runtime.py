"""Both ARM64 runtime suites must qualify; no stale, skipped or partial join."""
from __future__ import annotations

from copy import deepcopy
import hashlib
import json
import os
from pathlib import Path
import sys
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch

from scripts.ci import apple_runtime_gate as gate
from scripts.release import workflow_contract as contract


class AppleRuntimeGateTest(unittest.TestCase):
    def setUp(self):
        self.head, self.tree = "a" * 40, "b" * 40
        self.needs = {lane: dict(result="success", outputs=dict(
            source_sha=self.head, source_tree=self.tree, evidence_id=str(123 + index),
            evidence_sha256="c" * 64, cleanup_sha256="d" * 64, architecture="arm64")) for index, lane in enumerate(gate.LANES)}

    def test_both_successful_current_arm64_lanes_qualify(self):
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
            task = dict(GITHUB_JOB="ios", GITHUB_RUN_ID="123", GITHUB_RUN_ATTEMPT="1")
            claim = {**source, "task": task}
            cleanup = dict(task=task, result="PASS", errors=[], retained=[], preparation_outcome="success",
                           gradle_stop=dict(exit_code=0), upload=dict(outcome="success", artifact_id="456", artifact_digest="c" * 64),
                           apple_cleanup={})
            receipts = {}
            for cycle in ("apple-ui",):
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
            with patch.object(gate.hygiene, "source_identity", return_value=source), patch.object(gate.hygiene, "git", return_value=""), patch.object(gate.platform, "machine", return_value="arm64"):
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


    def test_non_arm64_execution_cannot_publish_runtime_lane_identity(self):
        for architecture in ("x86_64", "AMD64", "", "aarch64"):
            with patch.object(gate.platform, "machine", return_value=architecture), self.subTest(architecture=architecture), self.assertRaisesRegex(RuntimeError, "ARM64"):
                gate.lane_identity(Path("/unused"), Path("/unused"), dict(GITHUB_JOB="ios"))

    def test_actual_aggregate_entrypoint_binds_its_own_job_run_and_receipt(self):
        with TemporaryDirectory() as temporary:
            root = Path(temporary).resolve()
            environment = dict(GITHUB_JOB="ios-runtime", GITHUB_WORKSPACE=str(root), RUNNER_TEMP=str(root),
                               GITHUB_RUN_ID="123", GITHUB_RUN_ATTEMPT="1",
                               PARLOR_APPLE_RUNTIME_NEEDS=json.dumps(self.needs))
            with patch.dict(os.environ, environment), patch.object(sys, "argv", ["gate", "aggregate"]), \
                    patch.object(gate.hygiene, "source_identity", return_value=dict(head=self.head, tree=self.tree)), \
                    patch.object(gate.hygiene, "git", return_value=""), patch("builtins.print"):
                self.assertEqual(gate.main(), 0)
                path = root / "parlor-apple-runtime-123-1.json"
                self.assertEqual(json.loads(path.read_text()), {
                    **gate.aggregate(self.needs, self.head, self.tree), "run_id": "123", "run_attempt": "1"})
                for job in ("ios", "ios-kmp", "ios-release", "", "unreviewed"):
                    with patch.dict(os.environ, GITHUB_JOB=job), self.subTest(job=job), self.assertRaisesRegex(RuntimeError, "original required gate"):
                        gate.main()
                for key, value in (("GITHUB_RUN_ID", "0"), ("GITHUB_RUN_ATTEMPT", "1\n"),
                                   ("PARLOR_APPLE_RUNTIME_NEEDS", " " * 65537)):
                    with patch.dict(os.environ, {key: value}), self.subTest(key=key), self.assertRaises(RuntimeError):
                        gate.main()
                with patch.object(gate.hygiene, "git", return_value=" M tracked-source"), self.assertRaisesRegex(RuntimeError, "not clean"):
                    gate.main()

    def test_actual_lane_entrypoint_emits_only_its_reviewed_output_fields(self):
        with TemporaryDirectory() as temporary:
            output = Path(temporary) / "output"
            identity = self.needs[gate.LANES[0]]["outputs"]
            with patch.dict(os.environ, GITHUB_OUTPUT=str(output)), patch.object(sys, "argv", ["gate", "lane"]), \
                    patch.object(gate.hygiene, "context", return_value=(Path(temporary), Path(temporary) / "claim", {})), \
                    patch.object(gate, "lane_identity", return_value=identity), patch("builtins.print"):
                self.assertEqual(gate.main(), 0)
                self.assertEqual(output.read_text(), "".join(key + "=" + value + "\n" for key, value in identity.items()))


class RuntimeIsolationWorkflowTest(unittest.TestCase):
    def test_gate_and_exact_full_swift_command_cannot_be_weakened(self):
        workflow = (contract.ROOT / ".github/workflows/production-verification.yml").read_text()
        for old, new in (
            ("needs: [ios-kmp, ios]", "needs: [ios]"),
            ("run: python3 -B -m scripts.ci.apple_runtime_gate aggregate", "run: true"),
            ("PARLOR_APPLE_RUNTIME_NEEDS: ${{ toJSON(needs) }}", "PARLOR_APPLE_RUNTIME_NEEDS: '{}'"),
            ("run: /usr/bin/python3 -B -m scripts.ci.apple_runtime_gate lane", "run: true"),
            ("${{ steps.runtime_lane.outputs.source_sha }}", "cached-source"),
            ("${{ steps.runtime_lane.outputs.architecture }}", "x86_64"),
            ("            test | tee build/ci-evidence/xcode-ui-test.log", "            test -only-testing:iosAppUITests/IOSAppLaunchUITests | tee build/ci-evidence/xcode-ui-test.log"),
            ("            test | tee build/ci-evidence/xcode-ui-test.log", "            test | tee build/ci-evidence/xcode-ui-test.log || true"),
            ("            -destination-timeout 120", "            -destination-timeout 240"),
        ):
            changed = workflow.replace(old, new, 1)
            self.assertNotEqual(changed, workflow)
            with self.subTest(mutation=new), self.assertRaises(RuntimeError):
                contract.verify_validation(changed)

    def test_both_native_runtime_lanes_reject_paid_or_non_arm64_hosts(self):
        workflow = (contract.ROOT / ".github/workflows/production-verification.yml").read_text()
        for name in ("ios-kmp", "ios"):
            job = contract.validation_job(workflow, name)
            for label in ("macos-15-intel", "macos-15-large", "macos-15-xlarge", "self-hosted"):
                changed = workflow.replace(job, job.replace("runs-on: macos-15", "runs-on: " + label, 1), 1)
                with self.subTest(job=name, label=label), self.assertRaises(RuntimeError):
                    contract.verify_validation(changed)
            for old, new in (('test "$(cat build/ci-evidence/architecture.txt)" = arm64', "true"),
                             ('test "$(cat build/ci-evidence/architecture.txt)" = arm64', 'test "$(cat build/ci-evidence/architecture.txt)" = x86_64')):
                changed = workflow.replace(job, job.replace(old, new, 1), 1)
                with self.subTest(job=name, mutation=new), self.assertRaises(RuntimeError):
                    contract.verify_validation(changed)


if __name__ == "__main__":
    unittest.main()
