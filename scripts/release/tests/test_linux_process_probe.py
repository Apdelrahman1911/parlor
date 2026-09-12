from __future__ import annotations

import errno
import json
import os
from pathlib import Path
import subprocess
import sys
from tempfile import TemporaryDirectory
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[3]))
from scripts.ci import linux_process_probe as probe


@unittest.skipUnless(sys.platform == "linux", "Linux-only procfs/UID/symlink controls")
class LinuxProcessProbeTest(unittest.TestCase):
    """Synthetic adapter controls; these tests never spawn or inspect real PIDs."""

    def setUp(self):
        temporary = TemporaryDirectory(prefix="parlor-linux-probe-test-")
        self.addCleanup(temporary.cleanup)
        self.base = Path(temporary.name).resolve()
        self.prefix = self.base / "receipt"
        self.task = dict(GITHUB_RUN_ID="123", GITHUB_RUN_ATTEMPT="1", GITHUB_JOB="desktop-android")
        self.source = dict(root=str(self.base), head="a" * 40, tree="b" * 40, outputs=[])
        self.sdk = dict(uid=1001, sdk_device=1, sdk_inode=2, sdk_path_sha256="c" * 64)
        self.manifest = dict(files=[], control_sha256="d" * 64)
        self.proof = dict(task=self.task, source=self.source, controls=self.manifest, android_sdk=self.sdk)

    def test_source_scope_review_host_and_unused_inputs_are_fail_closed(self):
        env = dict(GITHUB_EVENT_NAME="workflow_dispatch", GITHUB_REPOSITORY=probe.REPOSITORY,
            GITHUB_REF="refs/heads/" + probe.BRANCH, GITHUB_JOB="desktop-android", RUNNER_OS="Linux", RUNNER_ARCH="X64",
            GITHUB_WORKFLOW_REF=probe.REPOSITORY + "/" + probe.WORKFLOW + "@refs/heads/" + probe.BRANCH,
            GITHUB_SHA="a" * 40, GITHUB_WORKFLOW_SHA="a" * 40)
        def git(_root, *arguments):
            return probe.BRANCH if arguments[0] == "branch" else "false" if arguments[0] == "rev-parse" else ""
        with patch.object(probe, "ROOT", self.base), patch.object(probe.hygiene, "context", return_value=(self.base, self.prefix, self.task)), \
                patch.object(probe.hygiene, "source_identity", return_value=self.source), patch.object(probe.hygiene, "git", side_effect=git), \
                patch.object(probe, "controls", return_value=self.manifest) as controls, patch.object(probe.hygiene, "android_sdk_binding", return_value=(self.base, self.sdk)), \
                patch.object(probe.sys, "platform", "linux"), patch.object(probe.platform, "machine", return_value="x86_64"), \
                patch.object(probe.platform, "freedesktop_os_release", return_value=dict(ID="ubuntu", VERSION_ID="24.04")):
            for scope in (probe.SCOPE, probe.ANDROID_CLEANUP_SCOPE):
                inputs = dict(verification_scope=scope, frozen_source_sha="a" * 40,
                              approved_probe_control_sha256="d" * 64, native_selection="paired")
                with patch.dict(os.environ, {**env, "PARLOR_LINUX_PROBE_INPUTS": json.dumps(inputs)}, clear=True):
                    proof = probe.admit(scope=scope)[4]
                    self.assertEqual((proof["verification_scope"], proof["android_sdk"]), (scope, self.sdk))
                    controls.assert_called_with(self.base, scope=scope)
                other_scope = probe.ANDROID_CLEANUP_SCOPE if scope == probe.SCOPE else probe.SCOPE
                for change, input_change in (({"GITHUB_EVENT_NAME": "push"}, {}), ({"GITHUB_WORKFLOW_SHA": "b" * 40}, {}),
                        ({"RUNNER_ARCH": "ARM64"}, {}), ({}, {"verification_scope": "full"}),
                        ({}, {"verification_scope": other_scope}),
                        ({}, {"approved_probe_control_sha256": "e" * 64}), ({}, {"preflight_run_id": "123"}),
                        ({}, {"native_selection": "l08-only"}), ({}, {"unreviewed": "input"})):
                    with self.subTest(scope=scope, change=change, input_change=input_change), \
                            patch.dict(os.environ, {**env, **change, "PARLOR_LINUX_PROBE_INPUTS": json.dumps({**inputs, **input_change})}, clear=True), \
                            self.assertRaises(RuntimeError):
                        probe.admit(scope=scope)
            with self.assertRaises(RuntimeError):
                probe.admit(scope="full")

    def test_controls_hash_exact_regular_bounded_bytes(self):
        (self.base / "a").write_bytes(b"a")
        (self.base / "b").write_bytes(b"b")
        with patch.object(probe, "CONTROL_PATHS", ("b", "a")):
            before = probe.controls(self.base)
            self.assertEqual([item["path"] for item in before["files"]], ["a", "b"])
            (self.base / "a").write_bytes(b"changed")
            self.assertNotEqual(before["control_sha256"], probe.controls(self.base)["control_sha256"])
            (self.base / "a").unlink()
            (self.base / "a").symlink_to(self.base / "b")
            with self.assertRaises(RuntimeError):
                probe.controls(self.base)
        (self.base / "large").write_bytes(b"x" * (1024 * 1024 + 1))
        with self.assertRaises(RuntimeError):
            probe.file_bytes(self.base / "large")

    def test_android_cleanup_control_binding_cannot_use_the_smaller_probe_manifest(self):
        for name in ("base", "smoke"):
            (self.base / name).write_bytes(name.encode())
        with patch.object(probe, "CONTROL_PATHS", ("base",)), \
                patch.object(probe, "ANDROID_CLEANUP_EXTRA_CONTROLS", ("smoke",)):
            original = probe.controls(self.base)
            android = probe.controls(self.base, scope=probe.ANDROID_CLEANUP_SCOPE)
            self.assertEqual([row["path"] for row in android["files"]], ["base", "smoke"])
            self.assertNotEqual(original["control_sha256"], android["control_sha256"])
            (self.base / "smoke").write_bytes(b"changed")
            self.assertEqual(probe.controls(self.base), original)
            self.assertNotEqual(probe.controls(self.base, scope=probe.ANDROID_CLEANUP_SCOPE), android)
            (self.base / "smoke").unlink()
            (self.base / "smoke").symlink_to(self.base / "base")
            with self.assertRaises(RuntimeError):
                probe.controls(self.base, scope=probe.ANDROID_CLEANUP_SCOPE)
            with self.assertRaises(RuntimeError):
                probe.controls(self.base, scope="full")

    def control(self, mode, *, reader="unprivileged", audit=None, ready=True, extra_file=False, unreaped=False,
                timeouts=0, interrupt=False):
        child = SimpleNamespace(pid=2147000000, stdin=Mock(), stdout=Mock(), returncode=None, terminate=Mock(), kill=Mock())
        child.stdout.fileno.return_value = 29
        pending_timeouts = [timeouts]
        def wait(timeout):
            if unreaped:
                raise OSError("private retirement path")
            if pending_timeouts[0]:
                pending_timeouts[0] -= 1
                raise subprocess.TimeoutExpired("owned-child", timeout)
            child.returncode = 0
            return 0
        child.wait = Mock(side_effect=wait)
        observed = (dict(result="PASS", errors=[], enumerated=2, sampled=2, selected_uid=2, matching_executables=0)
                    if mode or reader == probe.EXE_READER else dict(result="FAIL", errors=[{"code": "PermissionError"}], enumerated=2,
                        sampled=1, selected_uid=1, matching_executables=0,
                        failure_context=dict(operation="before_exe_readlink", selection=True, errno=errno.EACCES)))
        observed["exe_reader"] = dict(mode=reader, eacces_denials=int(not mode),
                                      privileged_reads=int(not mode and reader == probe.EXE_READER))
        observed.update(mixed_uid_samples=0, mixed_uid_privileged_samples=0)
        def sample(binding, proc, **options):
            self.assertEqual(binding, self.sdk)
            self.assertEqual(options, {"reader": reader})
            self.assertEqual({p.name for p in proc.iterdir()}, {str(os.getpid()), str(child.pid)})
            self.assertTrue(all(p.is_symlink() for p in proc.iterdir()))
            if extra_file:
                (proc / "unowned-in-control").write_text("retain")
            if interrupt:
                probe.signal.getsignal(probe.signal.SIGTERM)(probe.signal.SIGTERM, None)
            return observed if audit is None else audit
        with patch.object(probe.hygiene, "android_sdk_binding", return_value=(self.base, self.sdk)), \
                patch.object(probe.subprocess, "Popen", return_value=child) as launch, \
                patch.object(probe.select, "select", return_value=([child.stdout] if ready else [], [], [])), \
                patch.object(probe.os, "read", return_value=(json.dumps(dict(pid=child.pid, uid=1001, dumpable=mode)) + "\n").encode()), \
                patch.object(probe.hygiene, "audit_android_emulator_absence", side_effect=sample) as sampler:
            row = probe.owned_dumpability_control(self.base, self.sdk, mode, reader=reader)
        return row, child, launch, sampler

    def test_negative_control_remains_failed_audit_and_uses_only_owned_isolated_child(self):
        positive, _, _, _ = self.control(1)
        self.assertEqual(positive["result"], "CONTROL_PASS")
        row, child, launch, _ = self.control(0)
        self.assertEqual((row["result"], row["audit"]["result"]), ("CONTROL_PASS", "FAIL"))
        self.assertTrue(row["child_reaped"] and row["fixture_removed"])
        self.assertEqual(launch.call_args.args[0], ["/usr/bin/python3", "-I", "-B", "-c", probe.CHILD, "0"])
        self.assertEqual(launch.call_args.kwargs["env"], {"PATH": "/usr/bin:/bin", "LANG": "C.UTF-8"})
        child.stdin.close.assert_called_once()
        child.wait.assert_called_once_with(timeout=2)
        child.terminate.assert_not_called()
        child.kill.assert_not_called()

    def test_explicit_privileged_control_requires_an_actual_reader_success_record(self):
        row, child, _, _ = self.control(0, reader=probe.EXE_READER)
        self.assertEqual((row["result"], row["audit"]["result"]), ("CONTROL_PASS", "PASS"))
        child.terminate.assert_not_called()
        good = row["audit"]
        for changed in (
            {**good, "exe_reader": dict(mode="unprivileged", eacces_denials=1, privileged_reads=1)},
            {**good, "exe_reader": dict(mode=probe.EXE_READER, eacces_denials=1, privileged_reads=0)},
            {**good, "result": "FAIL"},
            {**good, "mixed_uid_samples": 1},
            {**good, "mixed_uid_privileged_samples": 1},
            {key: value for key, value in good.items() if key != "mixed_uid_samples"},
        ):
            with self.subTest(audit=changed):
                observed, _, _, _ = self.control(0, reader=probe.EXE_READER, audit=changed)
                self.assertEqual(observed["result"], "CONTROL_FAIL")
                self.assertTrue(observed["child_reaped"] and observed["fixture_removed"])

    def test_readiness_failure_reaps_child_without_sampling_or_private_text(self):
        row, child, _, sampler = self.control(1, ready=False)
        self.assertEqual(row["result"], "CONTROL_FAIL")
        self.assertTrue(row["child_reaped"] and row["fixture_removed"])
        child.wait.assert_called_once()
        sampler.assert_not_called()
        self.assertNotIn(str(self.base), json.dumps(row))

    def test_timeout_and_interrupt_retire_only_the_exact_owned_child(self):
        row, child, _, _ = self.control(1, timeouts=2)
        self.assertEqual(row["result"], "CONTROL_FAIL")
        self.assertTrue(row["child_reaped"] and row["fixture_removed"])
        child.terminate.assert_called_once()
        child.kill.assert_called_once()
        row, child, _, _ = self.control(1, interrupt=True)
        self.assertEqual(row["result"], "CONTROL_FAIL")
        self.assertTrue(row["interrupted"] and row["child_reaped"] and row["fixture_removed"])
        child.terminate.assert_not_called()
        child.kill.assert_not_called()

    def test_unknown_selected_denial_is_not_the_expected_nondumpable_control(self):
        for context in (dict(operation="before_stat", selection="unknown", errno=errno.EACCES),
                        dict(operation="before_exe_readlink", selection=False, errno=errno.EACCES),
                        dict(operation="before_exe_readlink", selection=True, errno=errno.EPERM)):
            with self.subTest(context=context):
                row, _, _, _ = self.control(0, audit=dict(result="FAIL", errors=[{"code": "PermissionError"}],
                    enumerated=2, matching_executables=0, failure_context=context))
                self.assertEqual(row["result"], "CONTROL_FAIL")
                self.assertTrue(row["child_reaped"] and row["fixture_removed"])

    def test_unreaped_child_or_changed_fixture_is_not_cleaned_or_passed(self):
        for options in (dict(unreaped=True), dict(extra_file=True)):
            with self.subTest(options=options):
                row, _, _, _ = self.control(1, **options)
                self.assertEqual(row["result"], "CONTROL_FAIL")
                self.assertFalse(row["fixture_removed"])
                self.assertTrue((self.base / row["fixture_basename"]).is_dir())
                self.assertNotIn("private retirement path", json.dumps(row))

    def test_host_failure_is_not_promoted_and_new_child_waits_for_previous_cleanup(self):
        for unretired in (False, True):
            prefix = self.base / ("unretired" if unretired else "host-fail")
            probe.hygiene.write_new(Path(str(prefix) + "-ownership.json"), {**self.source, "task": self.task, "android_sdk": self.sdk})
            def control(_parent, _binding, mode, *, reader):
                return dict(dumpable=mode, reader=reader, result="CONTROL_PASS", child_reaped=not unretired, fixture_removed=not unretired)
            with patch.object(probe.hygiene, "context", return_value=(self.base, prefix, self.task)), \
                    patch.object(probe, "admit", return_value=(self.base, prefix, self.task, self.source, self.proof)), \
                    patch.dict(os.environ, {"PARLOR_VERIFICATION_PREPARE_OUTCOME": "success"}), \
                    patch.object(probe.hygiene, "audit_android_emulator_absence", return_value=dict(result="FAIL", errors=[{"code": "PermissionError"}])), \
                    patch.object(probe, "owned_dumpability_control", side_effect=control) as controls, \
                    patch.object(probe.hygiene, "stop_gradle", return_value={"exit_code": 0}) as stop, patch("builtins.print"):
                self.assertEqual(probe.run(), 1)
            row = json.loads(Path(str(prefix) + "-linux-probe.json").read_text())
            self.assertEqual((row["result"], row["host_audit"]["result"]), ("FAIL", "FAIL"))
            self.assertEqual(controls.call_count, 1 if unretired else 3)
            stop.assert_called_once()

    def test_finalizer_requires_retired_control_receipts_before_unchanged_cleanup(self):
        rows = [dict(dumpable=mode, reader=reader, child_reaped=True, fixture_removed=True)
                for mode, reader in probe.CONTROL_MODES]
        for unretired in (False, True):
            prefix = self.base / ("bad-cleanup" if unretired else "good-cleanup")
            observed = dict(binding=self.proof, reader=probe.EXE_READER,
                            controls=[{**item, "child_reaped": not unretired} for item in rows])
            probe.hygiene.write_new(Path(str(prefix) + "-linux-probe.json"), observed)
            probe.hygiene.write_new(Path(str(prefix) + "-stop-linux-probe.json"), dict(binding=self.proof, task=self.task, exit_code=0))
            with patch.object(probe.hygiene, "context", return_value=(self.base, prefix, self.task)), \
                    patch.object(probe, "admit", return_value=(self.base, prefix, self.task, self.source, self.proof)), \
                    patch.dict(os.environ, dict(PARLOR_VERIFICATION_PREPARE_OUTCOME="success", PARLOR_VERIFICATION_UPLOAD_OUTCOME="success",
                        PARLOR_VERIFICATION_ARTIFACT_ID="456", PARLOR_VERIFICATION_ARTIFACT_DIGEST="f" * 64)), \
                    patch.object(probe.hygiene, "cleanup", return_value={"result": "PASS"}) as cleanup, \
                    patch.object(probe.hygiene, "stop_gradle", return_value={"exit_code": 0}), patch("builtins.print"):
                self.assertEqual(probe.cleanup(), 1 if unretired else 0)
            if unretired:
                cleanup.assert_not_called()
            else:
                self.assertEqual(cleanup.call_args.args[3], dict(outcome="success", artifact_id="456", artifact_digest="f" * 64))
                self.assertEqual(cleanup.call_args.kwargs, {"android_reader": probe.EXE_READER})


if __name__ == "__main__":
    unittest.main()
