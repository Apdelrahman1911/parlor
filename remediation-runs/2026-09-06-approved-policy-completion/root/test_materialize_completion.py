"""Synthetic report-control checks; never application or independent-review proof."""
import copy
import json
from pathlib import Path
import tempfile
import unittest
from unittest import mock

import materialize_completion as control


class CompletionOutcomeGuardTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="parlor-report-control-")
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        patch = mock.patch.object(control, "ROOT", self.root)
        patch.start()
        self.addCleanup(patch.stop)
        self.frozen = {"source_manifest_sha256": "synthetic-frozen-source"}
        self.common = dict(status="PASS", xcodebuild_exit_code=0, cleanup_status="PASS",
            cleanup_errors=[], remaining_outputs=[], owned_processes_remaining=[],
            source_before=self.frozen, source_after=self.frozen, source_unchanged=True,
            controls_unchanged=True, copied_sources_unchanged=True,
            gradle_stops=[dict(exit_code=0)], embedded_gradle_stop_status="PASS")
        self.language = dict(self.common,
            execution_kind="manifest-owned-copy-unsigned-ios-dsc01-observation-invocation-matrix",
            runtime_evidence_status="PASS", xctest=dict(result="Passed", total=5, skipped=0, failures=0),
            os_settings_gate=dict(status="PASS"),
            dsc01_matrix=dict(actual_settings_and_local_sessions_gate="PASS",
                local_games={game: dict(status="PASS") for game in ("whodunit", "mafia")}))
        self.inventory = self.write("inventory.json", {"scope": "synthetic control fixture only"})
        self.release = dict(self.common,
            execution_kind="manifest-owned-copy-unsigned-ios-source-identical-release-wrapper-build",
            build_evidence_status="PASS", artifact_inspection_status="PASS",
            build_configuration="Release", build_platform="iOS Simulator", architectures=["arm64"],
            artifact_inventory_sha256=self.inventory["sha256"])
        self.review = self.write("review.json", {"scope": "synthetic control fixture, not actual approval"})

    def write(self, name, value):
        path = self.root / name
        path.write_text(json.dumps(value))
        return control.reference(path)

    def select(self, key, receipt):
        return {key: dict(receipt=self.write("receipt.json", receipt),
                          independent_review=self.review, artifact_inventory=self.inventory)}

    def test_actual_typed_language_and_release_outcomes_can_be_selected(self):
        for key, receipt in (("native_language", self.language), ("release_wrapper", self.release)):
            with self.subTest(key=key):
                self.assertEqual(receipt, control.selected_native(self.select(key, receipt), key, self.frozen))

    def test_debug_smoke_cannot_satisfy_either_slot(self):
        for key, template in (("native_language", self.language), ("release_wrapper", self.release)):
            receipt = dict(template, execution_kind="unsigned-debug-smoke")
            with self.subTest(key=key), self.assertRaises(ValueError):
                control.selected_native(self.select(key, receipt), key, self.frozen)

    def test_os_matrix_and_both_local_games_are_mandatory(self):
        mutations = [lambda r: r["os_settings_gate"].update(status="BLOCKED"),
                     lambda r: r["dsc01_matrix"].update(actual_settings_and_local_sessions_gate="FAIL")]
        for game in ("whodunit", "mafia"):
            mutations.append(lambda r, game=game: r["dsc01_matrix"]["local_games"].pop(game))
        for mutation in mutations:
            receipt = copy.deepcopy(self.language)
            mutation(receipt)
            with self.assertRaises(ValueError):
                control.selected_native(self.select("native_language", receipt), "native_language", self.frozen)

    def test_failed_skipped_or_unexecuted_xctest_cannot_complete_language(self):
        for field, value in (("result", "Failed"), ("total", 4), ("skipped", 1), ("failures", 1)):
            receipt = copy.deepcopy(self.language)
            receipt["xctest"][field] = value
            with self.subTest(field=field), self.assertRaises(ValueError):
                control.selected_native(self.select("native_language", receipt), "native_language", self.frozen)

    def test_release_configuration_architecture_and_artifact_inspection_are_mandatory(self):
        for field, value in (("build_configuration", "Debug"), ("build_platform", "iOS"),
                             ("architectures", ["x86_64"]), ("artifact_inspection_status", "NOT_RUN"),
                             ("build_evidence_status", "FAIL"), ("embedded_gradle_stop_status", "FAIL")):
            receipt = dict(self.release, **{field: value})
            with self.subTest(field=field), self.assertRaises(ValueError):
                control.selected_native(self.select("release_wrapper", receipt), "release_wrapper", self.frozen)

    def test_stale_output_and_missing_cleanup_cannot_pass(self):
        for field, value in (("xcodebuild_exit_code", 65), ("cleanup_status", "FAIL"),
                             ("cleanup_errors", ["failed deletion"]), ("remaining_outputs", ["build"]),
                             ("owned_processes_remaining", ["worker"]), ("gradle_stops", []),
                             ("gradle_stops", [dict(exit_code=1)])):
            for key, template in (("native_language", self.language), ("release_wrapper", self.release)):
                receipt = dict(template, **{field: value})
                with self.subTest(key=key, field=field), self.assertRaises(ValueError):
                    control.selected_native(self.select(key, receipt), key, self.frozen)

    def test_source_or_control_drift_cannot_pass(self):
        for field, value in (("source_before", {}), ("source_after", {}), ("source_unchanged", False),
                             ("controls_unchanged", False), ("copied_sources_unchanged", False)):
            receipt = dict(self.language, **{field: value})
            with self.subTest(field=field), self.assertRaises(ValueError):
                control.selected_native(self.select("native_language", receipt), "native_language", self.frozen)

    def test_review_and_inventory_hashes_are_checked(self):
        selection = self.select("release_wrapper", self.release)
        (self.root / "review.json").write_text("changed")
        with self.assertRaises(ValueError):
            control.selected_native(selection, "release_wrapper", self.frozen)
        self.review = self.write("review.json", {"scope": "synthetic fixture"})
        selection = self.select("release_wrapper", self.release)
        (self.root / "inventory.json").write_text("changed")
        with self.assertRaises(ValueError):
            control.selected_native(selection, "release_wrapper", self.frozen)

    def test_valid_other_inventory_cannot_replace_the_build_inventory(self):
        selection = self.select("release_wrapper", self.release)
        selection["release_wrapper"]["artifact_inventory"] = self.write("other.json", {"other": True})
        with self.assertRaises(ValueError):
            control.selected_native(selection, "release_wrapper", self.frozen)

    def test_unknown_outcome_slot_and_unsafe_reference_are_rejected(self):
        with self.assertRaises(ValueError):
            control.selected_native(self.select("unknown", self.language), "unknown", self.frozen)
        for path in ("../review.json", "/review.json"):
            with self.subTest(path=path), self.assertRaises(ValueError):
                control.checked_reference(dict(path=path, sha256=self.review["sha256"]))


class ReviewIndexAdoptionTest(unittest.TestCase):
    def test_draft_partial_stays_historical_and_current_chain_is_explicit(self):
        old = [dict(finding_id="DS-C01", status_at_draft="PARTIALLY VERIFIED", review_chain=["old"])]
        index = dict(issue_review_chains=copy.deepcopy(old))
        issues = [dict(finding_id="DS-C01", status="FIXED AND VERIFIED",
                       independent_review_evidence=[dict(path="new-review", sha256="new-hash")],
                       independent_conclusion="Explicit current conclusion")]
        control.adopt_issue_review_chains(index, issues)
        self.assertEqual(old, index["historical_issue_review_chains"])
        current = index["issue_review_chains"][0]
        self.assertEqual("FIXED AND VERIFIED", current["status"])
        self.assertNotIn("status_at_draft", current)
        self.assertEqual(issues[0]["independent_review_evidence"], current["independent_review_evidence"])
        issues[0]["independent_review_evidence"].clear()
        self.assertEqual(1, len(current["independent_review_evidence"]))


if __name__ == "__main__":
    unittest.main()
