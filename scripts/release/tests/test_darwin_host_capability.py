"""Synthetic capability-driver controls; never spawn or load a native library."""
from __future__ import annotations

import json
import os
from pathlib import Path
import signal
import subprocess
import sys
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import Mock, patch

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT))
from scripts.ci import darwin_host_capability as control
from scripts.ci.darwin_worker_identity import ProcessIdentity

TRACKING = "github_01234567-89ab-cdef-0123-456789abcdef"
UID = getattr(os, "getuid", lambda: 501)()


class CapabilityObservationTest(unittest.TestCase):
    def setUp(self):
        self.identity = ProcessIdentity(101, UID, (2, 3), "/bin/sleep", (0, UID, 0, UID, 0, 101, 0, 1))
        self.backend = Mock()
        self.backend.read.return_value = self.identity
        self.backend.tracking_id.return_value = TRACKING
        self.backend.signal.return_value = True
        self.child = Mock(pid=101, returncode=None)
        self.child.poll.side_effect = lambda: self.child.returncode

        def wait(timeout):
            self.child.returncode = -signal.SIGTERM
            return self.child.returncode

        self.child.wait.side_effect = wait
        self.spawn = Mock(return_value=self.child)
        uid = patch.object(control.os, "getuid", return_value=UID, create=True)
        uid.start()
        self.addCleanup(uid.stop)

    def observe(self, interrupted=lambda: False, tracking=TRACKING):
        return control.observe(self.backend, tracking, interrupted, self.spawn)

    def test_success_requires_exact_native_identity_marker_signal_and_reaping(self):
        row = self.observe()
        self.assertEqual(row, dict(result="PASS", stage="complete", native_signal=True,
                                   direct_child_reaped=True, fallback_cleanup=False, interrupted=False))
        self.backend.signal.assert_called_once_with(self.identity, signal.SIGTERM)
        self.child.wait.assert_called_once_with(timeout=5)
        self.child.terminate.assert_not_called()
        self.child.kill.assert_not_called()
        self.spawn.assert_called_once_with(["/bin/sleep", "30"],
            env=dict(PATH="/usr/bin:/bin", LC_ALL="C", RUNNER_TRACKING_ID=TRACKING),
            stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, close_fds=True)
        for private in (TRACKING, "RUNNER_TRACKING_ID", "/bin/sleep", "101"):
            self.assertNotIn(private, json.dumps(row))

    def test_invalid_tracking_or_initial_cancellation_cannot_spawn(self):
        for tracking, interrupted in (("", False), (TRACKING + "suffix", False), (TRACKING, True)):
            with self.subTest(tracking=tracking, interrupted=interrupted):
                row = self.observe(lambda: interrupted, tracking)
                self.assertEqual(row["result"], "FAIL")
                self.spawn.assert_not_called()
                self.backend.read.assert_not_called()

    def test_identity_and_marker_mutations_never_authorize_native_signal(self):
        variants = [None, self.identity._replace(pid=102), self.identity._replace(uid=UID + 1),
                    self.identity._replace(started=(0, 1)), self.identity._replace(started=(2, 1000000)),
                    self.identity._replace(command="/other"), self.identity._replace(token=(0,) * 8),
                    self.identity._replace(token=(0,) * 7)]
        for identity in variants:
            with self.subTest(identity=identity):
                self.child.returncode = None
                self.backend.read.return_value = identity
                row = self.observe()
                self.assertEqual(row["result"], "FAIL")
                self.assertTrue(row["fallback_cleanup"])
                self.assertTrue(row["direct_child_reaped"])
                self.backend.signal.assert_not_called()
        self.backend.read.return_value = self.identity
        for tracking in (None, "other", TRACKING + "suffix"):
            with self.subTest(marker=tracking):
                self.child.returncode = None
                self.backend.tracking_id.return_value = tracking
                self.assertEqual(self.observe()["result"], "FAIL")
                self.backend.signal.assert_not_called()

    def test_changed_identity_or_native_denial_cannot_become_success_via_fallback(self):
        self.backend.read.side_effect = [self.identity, self.identity._replace(started=(3, 0))]
        row = self.observe()
        self.assertEqual(row["result"], "FAIL")
        self.backend.signal.assert_not_called()
        self.backend.read.side_effect = None
        self.child.returncode = None
        self.backend.signal.return_value = False
        row = self.observe()
        self.assertEqual(row["result"], "FAIL")
        self.assertTrue(row["direct_child_reaped"])
        self.assertFalse(row["native_signal"])
        self.assertTrue(row["fallback_cleanup"])

    def test_cancellation_in_spawn_window_retires_only_assigned_direct_child(self):
        cancelled = []

        def spawn(*args, **kwargs):
            cancelled.append(True)
            return self.child

        self.spawn.side_effect = spawn
        row = self.observe(lambda: bool(cancelled))
        self.assertEqual(row["result"], "FAIL")
        self.assertTrue(row["interrupted"])
        self.assertTrue(row["direct_child_reaped"])
        self.child.terminate.assert_called_once()
        self.backend.read.assert_not_called()
        self.backend.signal.assert_not_called()

    def test_native_exceptions_are_redacted_and_cleanup_remains_sticky(self):
        self.backend.tracking_id.side_effect = RuntimeError("PRIVATE_RUNNER_ENV=" + TRACKING)
        row = self.observe()
        self.assertEqual(row["error_type"], "RuntimeError")
        self.assertEqual(row["stage"], "native-tracking")
        self.assertEqual(row["result"], "FAIL")
        self.assertTrue(row["direct_child_reaped"])
        self.assertNotIn(TRACKING, json.dumps(row))
        self.assertNotIn("PRIVATE", json.dumps(row))

    def test_timeout_uses_bounded_handle_fallback_but_retains_native_failure(self):
        self.child.wait.side_effect = [subprocess.TimeoutExpired("private", 5),
                                      subprocess.TimeoutExpired("private", 2), -signal.SIGKILL]
        self.child.poll.return_value = None
        row = self.observe()
        self.assertEqual(row["result"], "FAIL")
        self.assertTrue(row["fallback_cleanup"])
        self.child.terminate.assert_called_once()
        self.child.kill.assert_called_once()
        self.assertEqual([call.kwargs["timeout"] for call in self.child.wait.call_args_list], [5, 2, 2])

    def test_unexpected_exit_or_cleanup_error_is_not_success(self):
        self.child.wait.side_effect = lambda timeout: setattr(self.child, "returncode", 0) or 0
        self.assertEqual(self.observe()["result"], "FAIL")
        self.child.returncode = None
        self.backend.read.side_effect = RuntimeError("private")
        self.child.terminate.side_effect = PermissionError("private")
        row = self.observe()
        self.assertEqual(row["result"], "FAIL")
        self.assertFalse(row["direct_child_reaped"])
        self.assertEqual(row["cleanup_error_type"], "PermissionError")


class CapabilityContextTest(unittest.TestCase):
    def setUp(self):
        self.temporary = TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name).resolve()
        self.runner_temp = self.root / "runner-temp"
        self.root = self.root / "checkout"
        self.root.mkdir()
        self.runner_temp.mkdir()
        self.environment = dict(GITHUB_ACTIONS="true", GITHUB_REPOSITORY=control.REPOSITORY,
            RUNNER_ENVIRONMENT="github-hosted", GITHUB_WORKFLOW_SHA="a" * 40,
            GITHUB_EVENT_NAME="workflow_dispatch", GITHUB_JOB=control.SCOPE, PARLOR_DISPATCH_SCOPE=control.SCOPE,
            GITHUB_RUN_ID="201", GITHUB_RUN_ATTEMPT="1", PARLOR_EXPECTED_ARCHITECTURE="arm64",
            PARLOR_FROZEN_SOURCE_SHA="a" * 40, GITHUB_SHA="a" * 40, GITHUB_WORKSPACE=str(self.root),
            RUNNER_TEMP=str(self.runner_temp), RUNNER_TRACKING_ID=TRACKING)
        self.git_values = {("rev-parse", "--show-toplevel"): str(self.root),
                           ("rev-parse", "HEAD"): "a" * 40, ("rev-parse", "HEAD^{tree}"): "b" * 40,
                           ("status", "--porcelain", "--untracked-files=all"): ""}
        replacement = patch.object(control, "git", side_effect=lambda root, *args: self.git_values[args])
        replacement.start()
        self.addCleanup(replacement.stop)
        replacement = patch.object(control.os, "getuid", return_value=self.root.stat().st_uid, create=True)
        replacement.start()
        self.addCleanup(replacement.stop)

    def test_exact_explicit_run_source_and_architecture_bind_new_external_receipt(self):
        for architecture in ("arm64", "x86_64"):
            with self.subTest(architecture=architecture):
                self.environment["PARLOR_EXPECTED_ARCHITECTURE"] = architecture
                output, receipt = control.context(self.environment, self.root)
                self.assertEqual(output.parent, self.runner_temp)
                self.assertEqual(output.name, "parlor-darwin-capability-201-1-" + architecture + ".json")
                self.assertEqual(receipt["source"], dict(head="a" * 40, tree="b" * 40))
                self.assertNotIn(TRACKING, json.dumps(receipt))

    def test_missing_or_mismatched_context_fails_before_native_library_or_spawn(self):
        for key in self.environment:
            with self.subTest(key=key), patch.object(control, "DarwinWorkerBackend") as native:
                changed = {name: value for name, value in self.environment.items() if name != key}
                with self.assertRaises(RuntimeError):
                    control.context(changed, self.root)
                native.assert_not_called()

    def test_dirty_stale_wrong_tree_and_reused_receipt_are_rejected(self):
        for key, value in ((('rev-parse', 'HEAD'), "c" * 40),
                           (('rev-parse', 'HEAD^{tree}'), "invalid"),
                           (('status', '--porcelain', '--untracked-files=all'), "?? unreviewed")):
            with self.subTest(key=key), patch.dict(self.git_values, {key: value}):
                with self.assertRaises(RuntimeError):
                    control.context(self.environment, self.root)
        output, _ = control.context(self.environment, self.root)
        output.write_text("retained")
        with self.assertRaisesRegex(RuntimeError, "fresh-receipt"):
            control.context(self.environment, self.root)
        self.assertEqual(output.read_text(), "retained")

    def test_main_retains_constructor_failure_redacts_private_errors_and_restores_handlers(self):
        output, receipt = control.context(self.environment, self.root)
        with patch.object(control, "context", return_value=(output, receipt)), \
                patch.object(control, "DarwinWorkerBackend", side_effect=RuntimeError("PRIVATE " + TRACKING)), \
                patch.object(control.signal, "getsignal", side_effect=["original-int", "original-term"]), \
                patch.object(control.signal, "signal") as install, patch("builtins.print") as emit:
            self.assertEqual(control.main(), 1)
        result = json.loads(output.read_text())
        self.assertEqual(result["result"], "FAIL")
        self.assertEqual(result["error_type"], "RuntimeError")
        self.assertNotIn(TRACKING, output.read_text())
        self.assertNotIn("PRIVATE", output.read_text())
        self.assertEqual(install.call_args_list[-2].args, (signal.SIGINT, "original-int"))
        self.assertEqual(install.call_args_list[-1].args, (signal.SIGTERM, "original-term"))
        emit.assert_called_once()


if __name__ == "__main__":
    unittest.main()
