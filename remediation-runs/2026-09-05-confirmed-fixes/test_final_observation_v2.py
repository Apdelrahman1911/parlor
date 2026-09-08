"""Synthetic evidence-contract tests. No builds, signals, devices or source writes."""
import copy
from pathlib import Path
import tempfile
import unittest
from unittest.mock import Mock, patch

import record_final_observation_v2 as recorder


def modern():
    return dict(cycle="synthetic", status="FAIL", cleanup_completed_at="2026-09-06T00:00:00Z",
                cleanup_errors=[], remaining_outputs=[], retained_outputs=[], stop_exit_code=0,
                workers=dict(remaining_owned_workers=[]), command=["./gradlew", "desktopTest"])


def native():
    return dict(cleanup_errors=[], owned_device_absent=True, preexisting_devices_preserved=True,
                remaining_owned_uuid_processes=[])


def apphost():
    return dict(status="FAIL", cleanup_completed_at="2026-09-06T00:00:00Z",
                execution_kind="source-original-kotlin-unsigned-ios-dsc01-apphost-matrix",
                cleanup_errors=[], remaining_outputs=[], cleanup_status="PASS",
                gradle_stops=[dict(label=label, exit_code=0) for label in ("stop-xcode-immediate", "stop-final")],
                owned_processes_remaining=[], unknown_holders=[], secondary_attestation_errors=[],
                temporary_directory_removed=True, owned_device_absent=True,
                secondary_cleanup=dict(status="PASS", remaining=[]),
                finalization_stages=[dict(stage="synthetic", status="PASS")])


class CleanupVerdictTest(unittest.TestCase):
    def test_failed_test_does_not_mean_failed_cleanup(self):
        self.assertEqual("PASS", recorder.cleanup_evaluation(modern())["status"])

    def test_successful_test_with_failed_cleanup_never_passes(self):
        value = modern(); value.update(status="PASS", cleanup_errors=["synthetic failed deletion"])
        self.assertEqual("FAIL", recorder.cleanup_evaluation(value)["status"])

    def test_nonzero_or_missing_stop_rejected(self):
        for status in (None, 1, False):
            value = modern(); value["stop_exit_code"] = status
            with self.subTest(status=status):
                self.assertEqual("FAIL", recorder.cleanup_evaluation(value)["status"])

    def test_survivors_retained_outputs_and_missing_fields_rejected(self):
        for change in (dict(workers={}), dict(workers=dict(remaining_owned_workers=[1])),
                       dict(remaining_outputs=["build"]), dict(retained_outputs=["build"]),
                       dict(cleanup_completed_at=None)):
            with self.subTest(change=change):
                self.assertEqual("FAIL", recorder.cleanup_evaluation({**modern(), **change})["status"])

    def test_running_cycle_is_not_complete(self):
        self.assertEqual("FAIL", recorder.cleanup_evaluation({**modern(), "status": "RUNNING"})["status"])

    def test_native_cycle_requires_associated_receipt(self):
        value = modern(); value["command"].append("owned_ios_simulator.init.gradle")
        self.assertEqual("FAIL", recorder.cleanup_evaluation(value)["status"])
        self.assertEqual("PASS", recorder.cleanup_evaluation(value, simulator=native())["status"])

    def test_native_errors_device_or_workers_fail(self):
        for field, incorrect in (("owned_device_absent", False), ("preexisting_devices_preserved", False),
                                 ("remaining_owned_uuid_processes", [1]), ("cleanup_errors", ["failed"])):
            with self.subTest(field=field):
                self.assertEqual("FAIL", recorder.cleanup_evaluation(modern(), simulator={**native(), field: incorrect})["status"])

    def test_failed_apphost_runtime_can_have_complete_cleanup(self):
        self.assertEqual("PASS", recorder.cleanup_evaluation(apphost())["status"])

    def test_every_apphost_cleanup_proof_required(self):
        fields = ("gradle_stops", "owned_processes_remaining", "unknown_holders", "secondary_attestation_errors",
                  "temporary_directory_removed", "owned_device_absent", "secondary_cleanup", "finalization_stages")
        for field in fields:
            value = apphost(); del value[field]
            with self.subTest(field=field):
                self.assertEqual("FAIL", recorder.cleanup_evaluation(value)["status"])

    def test_apphost_unsuccessful_stop_stage_and_secondary_cleanup_fail(self):
        for mutate in (lambda v: v["gradle_stops"][0].update(exit_code=1),
                       lambda v: v["secondary_cleanup"].update(remaining=["owned-fifo"]),
                       lambda v: v["finalization_stages"][0].update(status="FAIL")):
            value = copy.deepcopy(apphost()); mutate(value)
            self.assertEqual("FAIL", recorder.cleanup_evaluation(value)["status"])

    def test_exact_legacy_identity_preserves_gap_without_inventing_workers(self):
        value = modern(); value.update(cycle="ios-b1-red", process_scan_exit_code=1)
        del value["workers"]; del value["retained_outputs"]
        self.assertEqual("FAIL", recorder.cleanup_evaluation(value)["status"])
        result = recorder.cleanup_evaluation(value, legacy_digest=recorder.LEGACY, legacy_scan="")
        self.assertEqual("PASS", result["status"])
        self.assertFalse(result["historical_pid_evidence_complete"])
        self.assertTrue(result["limitations"])
        self.assertEqual("FAIL", recorder.cleanup_evaluation(value, recorder.LEGACY, "unexpected")["status"])


class EvidencePathTest(unittest.TestCase):
    def test_parent_alias_rejected_before_lane_identity_reads_source(self):
        with tempfile.TemporaryDirectory(prefix="parlor-observation-contract-") as temporary:
            root = Path(temporary).resolve(); (root / "actual").mkdir()
            (root / "actual/source.kt").write_text("synthetic")
            (root / "alias").symlink_to(root / "actual", target_is_directory=True)
            lane = Mock()
            with patch.object(recorder, "ROOT", root), patch.object(recorder, "source_inventory", return_value=[root / "alias/source.kt"]):
                with self.assertRaises(RuntimeError): recorder.validated_source_identity(lane)
            lane.identity.assert_not_called()

    def test_safe_inventory_precedes_source_identity(self):
        with tempfile.TemporaryDirectory(prefix="parlor-observation-contract-") as temporary:
            root = Path(temporary).resolve(); path = root / "source.kt"; path.write_text("synthetic")
            lane = Mock(); lane.identity.return_value = {"synthetic": True}
            with patch.object(recorder, "ROOT", root), patch.object(recorder, "source_inventory", return_value=[path]):
                self.assertEqual({"synthetic": True}, recorder.validated_source_identity(lane))
            lane.identity.assert_called_once_with()

    def test_current_unclassified_workers_block_cleanup_without_signals(self):
        cycles = [{"cleanup_evaluation": {"status": "PASS"}}]
        self.assertEqual("PASS", recorder.current_cleanup_verdict(cycles, [], [], []))
        self.assertEqual("BLOCKED", recorder.current_cleanup_verdict(cycles, [], [], [{"pid": 1}]))
        self.assertEqual("FAIL", recorder.current_cleanup_verdict(cycles, ["build"], [], []))

    def test_symlink_parent_rejected_before_reading(self):
        with tempfile.TemporaryDirectory(prefix="parlor-observation-contract-") as temporary:
            root = Path(temporary).resolve(); (root / "actual").mkdir()
            (root / "actual/fixture.json").write_text("{}")
            (root / "alias").symlink_to(root / "actual", target_is_directory=True)
            with self.assertRaises(RuntimeError): recorder.safe_path(root / "alias/fixture.json", root)

    def test_dotdot_and_outside_boundary_rejected(self):
        with tempfile.TemporaryDirectory(prefix="parlor-observation-contract-") as temporary:
            root = Path(temporary).resolve()
            with self.assertRaises(RuntimeError): recorder.safe_path(root / "../outside", root)
            with self.assertRaises(ValueError): recorder.safe_path(root.parent / "outside", root)

    def test_regular_file_allowed_and_symlink_leaf_rejected(self):
        with tempfile.TemporaryDirectory(prefix="parlor-observation-contract-") as temporary:
            root = Path(temporary).resolve(); path = root / "fixture.json"; path.write_text("{}")
            self.assertEqual(path, recorder.safe_path(path, root))
            (root / "alias").symlink_to(path)
            with self.assertRaises(RuntimeError): recorder.safe_path(root / "alias", root)


if __name__ == "__main__":
    unittest.main()
