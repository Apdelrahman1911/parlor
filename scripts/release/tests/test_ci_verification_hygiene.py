from __future__ import annotations

import importlib.util
import json
import os
import subprocess
import sys
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[3]
SPEC = importlib.util.spec_from_file_location("ci_verification_hygiene", ROOT / "scripts/ci/verification_hygiene.py")
assert SPEC and SPEC.loader
hygiene = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(hygiene)


class VerificationHygieneTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = TemporaryDirectory(prefix="parlor-ci-hygiene-test-")
        self.addCleanup(self.temporary.cleanup)
        self.base = Path(self.temporary.name).resolve()
        self.root = self.base / "checkout"
        self.root.mkdir()
        self.claim = self.base / "claim.json"
        self.task = {"GITHUB_RUN_ID": "123", "GITHUB_RUN_ATTEMPT": "1", "GITHUB_JOB": "test"}
        self.upload = {"outcome": "success", "artifact_id": "456", "artifact_digest": "d" * 64}
        self.preparation_outcome = "success"
        self.identity = {"root": str(self.root), "head": "a" * 40, "tree": "b" * 40,
                         "outputs": ["build", "build-logic/convention/build", "composeApp/build"]}
        self.identity_patch = patch.object(hygiene, "source_identity", return_value=self.identity)
        self.identity_patch.start()
        self.addCleanup(self.identity_patch.stop)
        self.git_patch = patch.object(hygiene, "git", return_value="")
        self.git = self.git_patch.start()
        self.addCleanup(self.git_patch.stop)
        self.stop_patch = patch.object(hygiene, "stop_gradle", return_value={"exit_code": 0})
        self.stop = self.stop_patch.start()
        self.addCleanup(self.stop_patch.stop)

    def prepare(self) -> None:
        hygiene.prepare(self.root, self.claim, self.task)

    def test_roots_include_build_logic_and_only_known_generated_directories(self) -> None:
        roots = hygiene.output_roots(["build.gradle.kts", "build-logic/settings.gradle.kts",
                                     "build-logic/convention/build.gradle.kts", "composeApp/build.gradle.kts",
                                     "scripts/release/tests/test_fixture.py"])
        self.assertEqual(roots, ["build", "build-logic/build", "build-logic/convention/build",
                                 "composeApp/build", "iosApp/build", "scripts/release/tests/__pycache__"])
        with self.assertRaisesRegex(RuntimeError, "tracked files"):
            hygiene.output_roots(["build.gradle.kts", "build/keep-source.txt"])

    def test_preexisting_outputs_are_never_claimed(self) -> None:
        (self.root / "build").mkdir()
        (self.root / "build/user-work.txt").write_text("preserve")
        with self.assertRaisesRegex(RuntimeError, "Pre-existing"):
            self.prepare()
        self.assertFalse(self.claim.exists())
        self.assertEqual((self.root / "build/user-work.txt").read_text(), "preserve")

    def test_cleanup_removes_only_claimed_outputs_and_keeps_evidence_and_source(self) -> None:
        self.prepare()
        source = self.root / "source.kt"
        source.write_text("preserve")
        for name in self.identity["outputs"]:
            output = self.root / name
            output.mkdir(parents=True)
            (output / "generated.bin").write_bytes(b"generated")
        receipt = hygiene.cleanup(self.root, self.claim, self.task, self.upload, self.preparation_outcome)
        self.assertEqual(receipt["result"], "PASS")
        self.assertEqual(receipt["removed"], self.identity["outputs"])
        self.assertTrue(self.claim.is_file())
        self.assertEqual(receipt["upload"], self.upload)
        self.assertEqual(receipt["retained"], [])
        self.assertEqual(source.read_text(), "preserve")
        self.stop.assert_called_once_with(self.root)

    def test_failed_stop_preserves_generated_output_and_reports_failure(self) -> None:
        self.prepare()
        (self.root / "build").mkdir()
        self.stop.return_value = {"exit_code": 1}
        receipt = hygiene.cleanup(self.root, self.claim, self.task, self.upload, self.preparation_outcome)
        self.assertEqual(receipt["result"], "FAIL")
        self.assertTrue((self.root / "build").is_dir())

    def test_unsuccessful_upload_preserves_evidence_and_still_stops_gradle(self) -> None:
        self.prepare()
        (self.root / "build").mkdir()
        evidence = self.root / "build/test-results.xml"
        evidence.write_text("required evidence")
        for outcome in ("failure", "cancelled", "skipped", ""):
            with self.subTest(outcome=outcome):
                upload = {**self.upload, "outcome": outcome}
                receipt = hygiene.cleanup(self.root, self.claim, self.task, upload, self.preparation_outcome)
                self.assertEqual(receipt["result"], "FAIL")
                self.assertEqual(receipt["removed"], [])
                self.assertEqual(receipt["retained"], ["build"])
                self.assertEqual(receipt["upload"], upload)
                self.assertEqual(evidence.read_text(), "required evidence")
        self.assertEqual(self.stop.call_count, 4)

    def test_success_without_actual_artifact_outputs_cannot_authorize_deletion(self) -> None:
        self.prepare()
        (self.root / "build").mkdir()
        invalid = ({"artifact_id": ""}, {"artifact_digest": ""}, {"artifact_id": "0"},
                   {"artifact_id": "456garbage"}, {"artifact_digest": "not-a-sha256"},
                   {"artifact_id": None}, {"artifact_digest": None})
        for change in invalid:
            with self.subTest(change=change):
                receipt = hygiene.cleanup(self.root, self.claim, self.task, {**self.upload, **change}, self.preparation_outcome)
                self.assertEqual(receipt["result"], "FAIL")
                self.assertEqual(receipt["removed"], [])
                self.assertTrue((self.root / "build").is_dir())

    def test_dirty_worktree_with_unchanged_head_tree_and_paths_cannot_authorize_cleanup(self) -> None:
        self.prepare()
        (self.root / "build").mkdir()
        self.git.return_value = " M composeApp/build.gradle.kts\n"
        receipt = hygiene.cleanup(self.root, self.claim, self.task, self.upload, self.preparation_outcome)
        self.assertEqual(receipt["result"], "FAIL")
        self.assertIn("Working-tree source changed", receipt["errors"][0]["error"])
        self.assertTrue((self.root / "build").is_dir())

    def test_historical_other_task_claim_does_not_authorize_current_cleanup(self) -> None:
        self.prepare()
        (self.root / "build").mkdir()
        other_task = {**self.task, "GITHUB_RUN_ATTEMPT": "2"}
        receipt = hygiene.cleanup(self.root, self.claim, other_task, self.upload, self.preparation_outcome)
        self.assertEqual(receipt["result"], "FAIL")
        self.assertEqual(receipt["removed"], [])
        self.assertTrue((self.root / "build").is_dir())

    def test_new_claim_never_overwrites_a_historical_receipt(self) -> None:
        self.claim.write_text("historical evidence")
        with self.assertRaises(FileExistsError):
            self.prepare()
        self.assertEqual(self.claim.read_text(), "historical evidence")

    def test_changed_source_or_task_cannot_authorize_cleanup(self) -> None:
        self.prepare()
        (self.root / "build").mkdir()
        tampered = json.loads(self.claim.read_text())
        tampered["head"] = "c" * 40
        self.claim.write_text(json.dumps(tampered))
        self.assertEqual(hygiene.cleanup(self.root, self.claim, self.task, self.upload, self.preparation_outcome)["result"], "FAIL")
        self.assertTrue((self.root / "build").is_dir())

    def test_failed_prepare_cannot_reuse_a_historical_same_task_claim(self) -> None:
        self.prepare()
        (self.root / "build").mkdir()
        evidence = self.root / "build/historical-results.xml"
        evidence.write_text("preserve historical evidence")
        for outcome in ("failure", "cancelled", "skipped", ""):
            with self.subTest(outcome=outcome):
                receipt = hygiene.cleanup(self.root, self.claim, self.task, self.upload, outcome)
                self.assertEqual(receipt["result"], "FAIL")
                self.assertEqual(receipt["removed"], [])
                self.assertIn("Fresh ownership preparation did not succeed", receipt["errors"][0]["error"])
                self.assertEqual(evidence.read_text(), "preserve historical evidence")

    def test_non_object_ownership_record_fails_with_a_cleanup_receipt(self) -> None:
        self.prepare()
        (self.root / "build").mkdir()
        for malformed in ("[]", "null", "42", "not-json"):
            with self.subTest(malformed=malformed):
                self.claim.write_text(malformed)
                receipt = hygiene.cleanup(self.root, self.claim, self.task, self.upload, self.preparation_outcome)
                self.assertEqual(receipt["result"], "FAIL")
                self.assertEqual(receipt["removed"], [])
                self.assertTrue((self.root / "build").is_dir())

    def test_tampered_source_path_claim_is_rejected(self) -> None:
        self.prepare()
        source = self.root / "source"
        source.mkdir()
        (source / "keep.kt").write_text("keep")
        tampered = json.loads(self.claim.read_text())
        tampered["outputs"].append("source")
        self.claim.write_text(json.dumps(tampered))
        self.assertEqual(hygiene.cleanup(self.root, self.claim, self.task, self.upload, self.preparation_outcome)["result"], "FAIL")
        self.assertTrue((source / "keep.kt").is_file())

    def test_symlink_sibling_rejects_cleanup_before_any_deletion(self) -> None:
        self.prepare()
        (self.root / "build").mkdir()
        outside = self.base / "outside"
        outside.mkdir()
        (outside / "keep").write_text("preserve")
        (self.root / "composeApp").mkdir()
        try:
            (self.root / "composeApp/build").symlink_to(outside, target_is_directory=True)
        except OSError:
            self.skipTest("This host does not permit an owned symlink fixture")
        self.assertEqual(hygiene.cleanup(self.root, self.claim, self.task, self.upload, self.preparation_outcome)["result"], "FAIL")
        self.assertTrue((self.root / "build").is_dir())
        self.assertEqual((outside / "keep").read_text(), "preserve")

    def test_nested_output_symlink_does_not_delete_external_target(self) -> None:
        self.prepare()
        (self.root / "build").mkdir()
        outside = self.base / "outside"
        outside.mkdir()
        (outside / "keep").write_text("preserve")
        try:
            (self.root / "build/link").symlink_to(outside, target_is_directory=True)
        except OSError:
            self.skipTest("This host does not permit an owned symlink fixture")
        self.assertEqual(hygiene.cleanup(self.root, self.claim, self.task, self.upload, self.preparation_outcome)["result"], "PASS")
        self.assertEqual((outside / "keep").read_text(), "preserve")

    def test_missing_claim_fails_without_deleting_outputs(self) -> None:
        (self.root / "build").mkdir()
        self.assertEqual(hygiene.cleanup(self.root, self.claim, self.task, self.upload, self.preparation_outcome)["result"], "FAIL")
        self.assertTrue((self.root / "build").is_dir())

    def test_both_hosts_use_the_repository_wrapper_for_stop(self) -> None:
        self.assertEqual(hygiene.gradle_stop_command(False), ["./gradlew", "--stop"])
        self.assertEqual(hygiene.gradle_stop_command(True)[1:], ["/d", "/c", "gradlew.bat", "--stop"])

    def test_cli_rejects_local_worktrees_before_cleaning(self) -> None:
        with patch.dict(os.environ, {"GITHUB_ACTIONS": "false"}):
            with self.assertRaisesRegex(RuntimeError, "restricted"):
                hygiene.context()


class VerificationHygieneGitIntegrationTest(unittest.TestCase):
    """Exercise real Git identity and the wrapper subprocess in an owned fixture."""

    def setUp(self) -> None:
        temporary = TemporaryDirectory(prefix="parlor-ci-hygiene-git-")
        self.addCleanup(temporary.cleanup)
        self.base = Path(temporary.name).resolve()
        self.root = self.base / "checkout"
        self.root.mkdir()
        self.claim = self.base / "claim.json"
        self.task = {"GITHUB_RUN_ID": "123", "GITHUB_RUN_ATTEMPT": "1", "GITHUB_JOB": "fixture"}
        self.upload = {"outcome": "success", "artifact_id": "456", "artifact_digest": "d" * 64}
        files = {
            ".gitignore": "build/\n__pycache__/\n",
            "build.gradle.kts": "// owned fixture\n",
            "composeApp/build.gradle.kts": "// owned module\n",
            "build-logic/convention/build.gradle.kts": "// owned build logic\n",
            "source.kt": "// preserve source\n",
            "gradlew": '#!/bin/sh\n[ "$1" = "--stop" ] || exit 9\necho owned-fixture-stop\n',
            "gradlew.bat": "@echo off\nif not \"%1\"==\"--stop\" exit /b 9\necho owned-fixture-stop\nexit /b 0\n",
        }
        for name, contents in files.items():
            path = self.root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(contents, encoding="utf-8")
        (self.root / "gradlew").chmod(0o700)
        self.run_git("init", "--quiet")
        self.run_git("config", "core.autocrlf", "false")
        self.run_git("add", ".")
        self.run_git("-c", "user.name=Owned Fixture", "-c", "user.email=fixture@example.invalid",
                     "-c", "commit.gpgsign=false", "-c", f"core.hooksPath={self.base / 'no-hooks'}",
                     "commit", "--quiet", "-m", "Owned test fixture")

    def run_git(self, *args: str) -> str:
        return subprocess.check_output(["git", "-C", str(self.root), *args], text=True)

    def create_outputs(self) -> list[str]:
        claim = hygiene.prepare(self.root, self.claim, self.task)
        for relative in claim["outputs"]:
            output = self.root / relative
            output.mkdir(parents=True)
            (output / "generated.bin").write_bytes(b"owned generated output")
        return claim["outputs"]

    def test_real_clean_checkout_removes_exact_outputs_after_wrapper_stop(self) -> None:
        outputs = self.create_outputs()
        source = (self.root / "source.kt").read_bytes()
        receipt = hygiene.cleanup(self.root, self.claim, self.task, self.upload, "success")
        self.assertEqual(receipt["result"], "PASS", receipt)
        self.assertEqual(receipt["removed"], outputs)
        self.assertEqual(receipt["gradle_stop"]["exit_code"], 0)
        self.assertIn("owned-fixture-stop", receipt["gradle_stop"]["output"])
        self.assertTrue(self.claim.is_file())
        self.assertEqual((self.root / "source.kt").read_bytes(), source)
        self.assertEqual(self.run_git("status", "--porcelain"), "")
        self.assertTrue(all(not (self.root / path).exists() for path in outputs))

    def test_real_modified_build_input_preserves_all_generated_evidence(self) -> None:
        outputs = self.create_outputs()
        build_file = self.root / "composeApp/build.gradle.kts"
        build_file.write_text("// changed after preparation\n", encoding="utf-8")
        receipt = hygiene.cleanup(self.root, self.claim, self.task, self.upload, "success")
        self.assertEqual(receipt["result"], "FAIL")
        self.assertEqual(receipt["removed"], [])
        self.assertIn("Working-tree source changed", receipt["errors"][0]["error"])
        self.assertIn("owned-fixture-stop", receipt["gradle_stop"]["output"])
        self.assertTrue(all((self.root / path / "generated.bin").is_file() for path in outputs))
        self.assertEqual(build_file.read_text(), "// changed after preparation\n")


class VerificationWorkflowHygieneTest(unittest.TestCase):
    def test_kotlin_compiler_work_is_owned_by_the_gradle_lane(self) -> None:
        workflow = (ROOT / ".github/workflows/production-verification.yml").read_text()
        configuration = workflow.split("jobs:\n", 1)[0]
        self.assertIn(
            "GRADLE_OPTS: -Dorg.gradle.project.kotlin.compiler.execution.strategy=in-process",
            configuration,
        )
        self.assertEqual(workflow.count("GRADLE_OPTS:"), 1)

    def test_every_job_retains_xml_before_always_run_cleanup(self) -> None:
        import re
        workflow = (ROOT / ".github/workflows/production-verification.yml").read_text()
        jobs = re.split(r"(?m)^  (?=[a-z][a-z0-9-]*:\n)", workflow.split("jobs:\n", 1)[1])[1:]
        self.assertEqual(len(jobs), 5)
        expected_stops = {"desktop-android": 2, "desktop-linux-arm64": 1, "desktop-macos-x64": 1,
                          "desktop-windows-x64": 1, "ios": 3}
        for job in jobs:
            name = job.split(":\n", 1)[0]
            with self.subTest(job=name):
                self.assertEqual(job.count('"verification_hygiene.py", "prepare"'), 1)
                stop_command = ('scripts.ci.apple_verification_hygiene finish ' if name == "ios" else
                                '"verification_hygiene.py", "stop",')
                self.assertEqual(job.count(stop_command), expected_stops[name])
                self.assertIn("**/build/test-results/**/*.xml", job)
                self.assertEqual(job.count("id: verification_artifact"), 1)
                self.assertEqual(job.count("id: verification_ownership"), 1)
                for output in ("steps.verification_ownership.outcome", "steps.verification_artifact.outcome",
                               "steps.verification_artifact.outputs.artifact-id",
                               "steps.verification_artifact.outputs.artifact-digest"):
                    self.assertIn(output, job)
                self.assertLess(job.index("**/build/test-results/**/*.xml"),
                                job.index("- name: Clean only attested verification outputs"))
                steps = re.split(r"(?m)^      - name: ", job)[1:]
                cleanup_steps = [step for step in steps if step.startswith(("Stop Gradle after", "Clean only", "Upload verification cleanup"))]
                self.assertEqual(len(cleanup_steps), expected_stops[name] + 2)
                for step in cleanup_steps:
                    self.assertIn("        if: always()", step)


if __name__ == "__main__":
    unittest.main()
