from __future__ import annotations

import errno
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


    def test_android_audit_follows_upload_gate_and_failure_retains_outputs(self) -> None:
        self.task["GITHUB_JOB"] = "desktop-android"
        binding = {"sdk_path_sha256": "e" * 64, "sdk_device": 1, "sdk_inode": 2, "uid": 1001}
        with patch.object(hygiene, "android_sdk_binding", return_value=(self.base, binding)):
            self.prepare()
        self.assertEqual(json.loads(self.claim.read_text())["android_sdk"], binding)
        (self.root / "build").mkdir()
        evidence = self.root / "build/evidence.xml"
        evidence.write_text("preserve")
        failure = {"result": "FAIL", "errors": [{"code": "SDK_EMULATOR_EXECUTABLE_OBSERVED"}]}
        with patch.object(hygiene, "audit_android_emulator_absence", return_value=failure) as audit:
            receipt = hygiene.cleanup(self.root, self.claim, self.task,
                                      {**self.upload, "outcome": "failure"}, "success")
            self.assertEqual(receipt["result"], "FAIL")
            audit.assert_not_called()
            receipt = hygiene.cleanup(self.root, self.claim, self.task, self.upload, "success")
            audit.assert_called_once_with(binding)
        self.assertEqual(receipt["result"], "FAIL")
        self.assertEqual(receipt["removed"], [])
        self.assertEqual(receipt["retained"], ["build"])
        self.assertEqual(evidence.read_text(), "preserve")
        self.assertEqual(receipt["android_emulator_absence"]["source"],
                         {key: self.identity[key] for key in ("head", "tree")})

    def test_android_successful_sample_allows_existing_exact_output_cleanup(self) -> None:
        self.task["GITHUB_JOB"] = "desktop-android"
        binding = {"sdk_path_sha256": "e" * 64, "sdk_device": 1, "sdk_inode": 2, "uid": 1001}
        with patch.object(hygiene, "android_sdk_binding", return_value=(self.base, binding)):
            self.prepare()
        (self.root / "build").mkdir()
        with patch.object(hygiene, "audit_android_emulator_absence", return_value={"result": "PASS"}):
            receipt = hygiene.cleanup(self.root, self.claim, self.task, self.upload, "success")
        self.assertEqual(receipt["result"], "PASS")
        self.assertEqual(receipt["removed"], ["build"])
        self.assertEqual(receipt["retained"], [])
        self.assertTrue(self.claim.is_file())
        self.stop.assert_called_once_with(self.root)


@unittest.skipUnless(sys.platform == "linux", "Linux-only procfs symlink fixture")
class AndroidEmulatorAbsenceTest(unittest.TestCase):
    """Owned metadata files/symlinks; never observes or signals real processes."""

    def setUp(self) -> None:
        temporary = TemporaryDirectory(prefix="parlor-android-absence-")
        self.addCleanup(temporary.cleanup)
        self.base = Path(temporary.name).resolve()
        self.sdk = self.base / "sdk"
        (self.sdk / "emulator/qemu/linux-x86_64").mkdir(parents=True)
        self.emulator = self.sdk / "emulator/qemu/linux-x86_64/qemu-system-x86_64"
        self.emulator.write_bytes(b"owned fake executable")
        self.observer = self.base / "observer"
        self.observer.write_bytes(b"owned non-SDK executable")
        self.proc = self.base / "proc"
        self.proc.mkdir()
        for mock in (patch.dict(os.environ, {"ANDROID_HOME": str(self.sdk), "ANDROID_SDK_ROOT": str(self.sdk)}),
                     patch.object(hygiene.os, "getuid", return_value=1001),
                     patch.object(hygiene.os, "geteuid", return_value=1001),
                     patch.object(hygiene.os, "getpid", return_value=101)):
            mock.start()
            self.addCleanup(mock.stop)
        self.binding = hygiene.android_sdk_binding()[1]
        self.process(101, self.observer)

    def process(self, pid: int, executable: Path | None, uid: int = 1001, start: int = 123) -> Path:
        path = self.proc / str(pid)
        path.mkdir(exist_ok=True)
        # stat comm can contain spaces, parentheses and newlines; starttime is field22.
        tail = ["S"] + ["0"] * 49
        tail[19] = str(start)
        (path / "stat").write_text(f"{pid} (worker ) with\nparen) " + " ".join(tail) + "\n")
        (path / "status").write_text(f"Name:\tworker\nPid:\t{pid}\nUid:\t{uid}\t{uid}\t{uid}\t{uid}\n")
        link = path / "exe"
        if link.is_symlink():
            link.unlink()
        if executable is not None:
            link.symlink_to(executable)
        return path

    def audit(self) -> dict:
        return hygiene.audit_android_emulator_absence(self.binding, proc=self.proc)

    def test_coherent_sample_excludes_other_uid_without_reading_its_executable(self) -> None:
        self.process(202, None, uid=0)
        receipt = self.audit()
        self.assertEqual(receipt["result"], "PASS", receipt)
        self.assertEqual((receipt["enumerated"], receipt["sampled"], receipt["selected_uid"]), (2, 2, 1))
        self.assertEqual(receipt["matching_executables"], 0)
        self.assertNotIn(str(self.base), json.dumps(receipt))

    def test_same_uid_sdk_qemu_fails_without_attributing_or_killing_process(self) -> None:
        self.process(202, self.emulator)
        receipt = self.audit()
        self.assertEqual(receipt["result"], "FAIL")
        self.assertEqual(receipt["matching_executables"], 1)
        self.assertEqual(receipt["errors"], [{"code": "SDK_EMULATOR_EXECUTABLE_OBSERVED"}])
        self.assertTrue((self.proc / "202/exe").is_symlink())
        self.assertNotIn(str(self.base), json.dumps(receipt))

    def test_same_prefix_sibling_is_outside_this_explicit_path_predicate(self) -> None:
        sibling = self.sdk / "emulator-other"
        sibling.mkdir()
        binary = sibling / "qemu-system-x86_64"
        binary.write_bytes(b"not covered by the configured SDK/emulator subtree")
        self.process(202, binary)
        self.assertEqual(self.audit()["result"], "PASS")

    def test_malformed_missing_oversized_and_mixed_uid_records_fail_closed(self) -> None:
        cases = (("stat", b"101 (truncated)", "PROC_STAT_INVALID"),
                 ("status", b"Name: worker\n", "PROC_UID_INVALID"),
                 ("status", b"Uid:\t1001\t0\t1001\t1001\n", "PROC_UID_AMBIGUOUS"),
                 ("status", b"x" * (hygiene.ANDROID_PROC_MAX_BYTES + 1), "PROC_RECORD_LIMIT"),
                 ("exe", None, "FileNotFoundError"))
        for name, data, code in cases:
            with self.subTest(name=name, code=code):
                process = self.process(101, self.observer)
                if data is None:
                    (process / name).unlink()
                else:
                    (process / name).write_bytes(data)
                receipt = self.audit()
                self.assertEqual(receipt["result"], "FAIL")
                self.assertEqual(receipt["errors"], [{"code": code}])

    def test_executable_read_denial_or_disappearing_pid_is_not_absence(self) -> None:
        read_bytes, readlink, path_stat = hygiene.android_proc_bytes, os.readlink, Path.stat
        selected = [(phase + "_" + kind, 101, 1001, "unknown" if phase == "before" and
                     kind in ("stat", "status") else True)
                    for phase in ("before", "after") for kind in ("stat", "status", "exe_readlink", "exe_stat")]
        # Observer101 is completed first. A new PID must not inherit its selection.
        other = [(phase + "_" + kind, 202, 1002, "unknown" if phase == "before" else False)
                 for phase in ("before", "after") for kind in ("stat", "status")]
        cases = [(operation, pid, uid, selection, error, number, number)
                 for operation, pid, uid, selection in selected + other
                 for error, number in ((PermissionError, errno.EACCES), (FileNotFoundError, errno.ENOENT))]
        cases += [("before_exe_readlink", 101, 1001, True, PermissionError, number, None)
                  for number in (None, True, -1, 4096)]
        for operation, pid, uid, selection, error, number, expected_errno in cases:
            with self.subTest(operation=operation, pid=pid, error=error.__name__, errno=number):
                process = self.process(pid, self.observer, uid=uid)
                counts = {}
                def access(kind):
                    counts[kind] = counts.get(kind, 0) + 1
                    if ("before_" if counts[kind] == 1 else "after_") + kind == operation:
                        raise error(number, "private-PID-999999 " + str(self.base), str(process))
                def bytes_(path):
                    if path.parent == process: access(path.name)
                    return read_bytes(path)
                def link_(path, *args, **kwargs):
                    if Path(path) == process / "exe": access("exe_readlink")
                    return readlink(path, *args, **kwargs)
                def stat_(path, *args, **kwargs):
                    if path == process / "exe": access("exe_stat")
                    return path_stat(path, *args, **kwargs)
                with patch.object(hygiene, "android_proc_bytes", side_effect=bytes_), \
                        patch.object(hygiene.os, "readlink", side_effect=link_), patch.object(Path, "stat", new=stat_):
                    receipt = self.audit()
                self.assertEqual(receipt["result"], "FAIL")
                self.assertEqual(receipt["errors"], [{"code": error.__name__}])
                self.assertEqual(receipt["failure_context"], dict(operation=operation,
                    selection=selection, errno=expected_errno))
                self.assertNotIn("private-PID-999999", json.dumps(receipt))
                self.assertNotIn(str(self.base), json.dumps(receipt))

    def test_deleted_executable_path_is_ambiguous_not_absence(self) -> None:
        deleted = self.base / "observer (deleted)"
        deleted.write_bytes(b"models a deleted executable still reachable through procfs")
        self.process(101, deleted)
        self.assertEqual(self.audit()["errors"], [{"code": "PROC_EXECUTABLE_AMBIGUOUS"}])

    def test_pid_lifetime_uid_and_executable_changes_during_sample_fail(self) -> None:
        original = os.readlink
        for change in ("lifetime", "uid", "executable"):
            with self.subTest(change=change):
                self.process(101, self.observer)
                changed = False
                def changing(path, *args, **kwargs):
                    nonlocal changed
                    value = original(path, *args, **kwargs)
                    if Path(path) == self.proc / "101/exe" and not changed:
                        changed = True
                        self.process(101, self.emulator if change == "executable" else self.observer,
                                     uid=1002 if change == "uid" else 1001,
                                     start=124 if change == "lifetime" else 123)
                    return value
                with patch.object(hygiene.os, "readlink", side_effect=changing):
                    receipt = self.audit()
                self.assertEqual(receipt["result"], "FAIL")
                code = "PROC_EXECUTABLE_AMBIGUOUS" if change == "executable" else "PROC_LIFETIME_OR_UID_CHANGED"
                self.assertEqual(receipt["errors"], [{"code": code}])

    def test_binding_and_observer_omission_fail_closed(self) -> None:
        receipt = hygiene.audit_android_emulator_absence({**self.binding, "uid": 1002}, proc=self.proc)
        self.assertEqual(receipt["errors"], [{"code": "SDK_UID_BINDING_CHANGED"}])
        with patch.object(hygiene.os, "getpid", return_value=303):
            self.assertEqual(self.audit()["errors"], [{"code": "OBSERVER_PID_NOT_SAMPLED"}])
        with patch.dict(os.environ, {"ANDROID_SDK_ROOT": str(self.base)}):
            self.assertEqual(self.audit()["errors"], [{"code": "SDK_ROOT_AMBIGUOUS"}])

    def test_enumeration_and_clock_limits_fail_closed(self) -> None:
        self.process(202, None, uid=0)
        with patch.object(hygiene, "ANDROID_PROC_MAX_ENTRIES", 1):
            self.assertEqual(self.audit()["errors"], [{"code": "PROC_ENTRY_LIMIT"}])
        with patch.object(hygiene.time, "monotonic", side_effect=[0.0, 6.0]):
            self.assertEqual(self.audit()["errors"], [{"code": "PROC_SCAN_DEADLINE"}])


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
