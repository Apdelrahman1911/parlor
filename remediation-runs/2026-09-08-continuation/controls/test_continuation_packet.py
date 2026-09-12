"""Synthetic packet/finalizer controls: no child process or real signal is sent.

Only the coordinator executes this suite in its ordinary lane. Each test owns
its TemporaryDirectory; all subprocess entry points are forbidden unless replaced
by an explicit fake. Passing does not prove actual process/signal cleanup or any
application, platform, compiler, or Store behavior.
"""
import ast
from contextlib import redirect_stdout
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import tempfile
import unittest
from unittest import mock


PATH = Path(__file__).with_name("run_continuation_controls.py")
SPEC = importlib.util.spec_from_file_location("parlor_packet_under_test", PATH)
subject = importlib.util.module_from_spec(SPEC)
with mock.patch("subprocess.Popen", side_effect=AssertionError("Import must not start a worker")):
    SPEC.loader.exec_module(subject)


class ContinuationPacketTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix="parlor-packet-controls-")
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name).resolve()
        self.destination = self.root / subject.C / "evidence/synthetic-cycle"
        self.tmp = self.destination / "scratch/tmp"
        self.tmp.mkdir(parents=True)
        for name, value in (("ROOT", self.root), ("FINALIZING", False), ("LAUNCHING", False),
                            ("STOPPING_CHILD", False), ("PENDING_SIGNALS", []), ("DEFERRED_SIGNALS", [])):
            patcher = mock.patch.object(subject, name, value)
            patcher.start()
            self.addCleanup(patcher.stop)
        for patcher in (
            mock.patch.dict(os.environ, {"TMPDIR": str(self.tmp)}),
            mock.patch.object(sys, "argv", [str(PATH)]),
            mock.patch.object(sys, "dont_write_bytecode", True),
            mock.patch.object(subprocess, "Popen", side_effect=AssertionError("No real worker allowed")),
            mock.patch.object(signal, "signal", side_effect=AssertionError("No real signal handler changes")),
        ):
            patcher.start()
            self.addCleanup(patcher.stop)
        self.receipt = {
            "cycle": self.destination.name, "status": "RUNNING",
            "command": ["/usr/bin/python3", "-B", str(subject.N / "controls/run_continuation_controls.py")],
        }
        self.write_receipt()
        self.rows = []
        self.output = io.StringIO()

    def write_receipt(self):
        (self.destination / "receipt.json").write_text(json.dumps(self.receipt))

    def fake_process(self, *, exit_code=0, timeout=False, stubborn=False, never_reaped=False):
        process = mock.Mock()
        process.returncode = None
        events = []

        def wait(*, timeout):
            events.append(("wait", timeout))
            if timeout == 600 and timeout_failure:
                raise subprocess.TimeoutExpired("synthetic", timeout)
            if timeout == 20 and stubborn or timeout == 5 and never_reaped:
                raise subprocess.TimeoutExpired("synthetic", timeout)
            process.returncode = exit_code if timeout == 600 else (-15 if timeout == 20 else -9)
            return process.returncode

        timeout_failure = timeout
        process.wait.side_effect = wait
        process.poll.side_effect = lambda: process.returncode
        process.terminate.side_effect = lambda: events.append(("terminate",))
        process.kill.side_effect = lambda: events.append(("kill",))
        return process, events

    def invoke_fake(self, process, name="synthetic"):
        with mock.patch.object(subprocess, "Popen", return_value=process), redirect_stdout(self.output):
            subject.run_command(self.destination, name, ["inert-synthetic.py"], self.rows)

    def main_with(self, manifests, process_factory=None):
        factory = process_factory or (lambda *_args, **_kwargs: self.fake_process()[0])
        with mock.patch.object(subject, "manifest", side_effect=manifests), \
                mock.patch.object(subprocess, "Popen", side_effect=factory), redirect_stdout(self.output):
            return subject.main()

    def test_fixed_packet_has_only_declared_control_entries(self):
        expected = {"lane", "single-tap", "schema-bootstrap", "toolchain", "functional-copy",
                    "composition", "normal", "schema-closure"}
        names = [name for name, _ in subject.COMMANDS]
        # The coordinator may add this exact synthetic driver as a ninth entry.
        self.assertTrue(expected <= set(names) <= expected | {"packet"})
        self.assertEqual(len(names), len(set(names)))
        for name, arguments in subject.COMMANDS:
            self.assertTrue(arguments)
            self.assertNotIn("--image-observer=libproc", arguments)
            self.assertNotIn("--ios-simulator", arguments)
            if name == "packet":
                self.assertEqual(arguments, [str(subject.N / "controls/test_continuation_packet.py")])

    def test_manifest_records_sorted_bytes_and_rejects_redirects_or_bad_types(self):
        controls = self.root / "controls"
        controls.mkdir()
        (controls / "test.py").write_bytes(b"inert source\n")
        (controls / "skip.bin").write_bytes(b"not a selected source kind")
        extra = self.root / "extra.json"
        extra.write_text("{}")
        with mock.patch.object(subject, "DIRECTORIES", (Path("controls"),)), \
                mock.patch.object(subject, "EXTRA", (Path("extra.json"),)):
            rows = subject.manifest()
            self.assertEqual([row["path"] for row in rows], ["controls/test.py", "extra.json"])
            self.assertEqual(rows[0]["sha256"], hashlib.sha256(b"inert source\n").hexdigest())
            (controls / "test.py").write_bytes(b"different\n")
            self.assertNotEqual(rows, subject.manifest())
            link = controls / "link.py"
            link.symlink_to(extra)
            with self.assertRaises(RuntimeError):
                subject.manifest()
            link.unlink()
            extra.unlink()
            extra.mkdir()
            with self.assertRaises(RuntimeError):
                subject.manifest()
            extra.rmdir()
            with extra.open("wb") as stream:
                stream.truncate(4 * 1024 * 1024 + 1)
            with self.assertRaises(RuntimeError):
                subject.manifest()

    def test_destination_requires_existing_uid_and_exact_running_receipt(self):
        self.assertEqual(subject.owned_destination(), self.destination)
        for key, value in (("status", "PASS"), ("cycle", "different"), ("command", ["unreviewed.py"])):
            previous = self.receipt[key]
            self.receipt[key] = value
            self.write_receipt()
            with self.subTest(key=key), self.assertRaises(RuntimeError):
                subject.owned_destination()
            self.receipt[key] = previous
        self.write_receipt()
        uid = os.getuid()
        with mock.patch.object(os, "getuid", return_value=uid + 1), self.assertRaises(RuntimeError):
            subject.owned_destination()
        receipt = self.destination / "receipt.json"
        actual = self.destination / "preserved-receipt.json"
        receipt.rename(actual)
        receipt.symlink_to(actual)
        with self.assertRaises(OSError):
            subject.owned_destination()
        self.assertEqual(json.loads(actual.read_text()), self.receipt)

    def test_destination_and_cli_reject_redirect_or_argument_bypass(self):
        alias = self.root / "alias"
        alias.symlink_to(self.tmp, target_is_directory=True)
        for tmp in (alias, self.root, self.tmp / "missing"):
            with self.subTest(tmp=tmp), mock.patch.dict(os.environ, {"TMPDIR": str(tmp)}), \
                    self.assertRaises((RuntimeError, OSError)):
                subject.owned_destination()
        with mock.patch.object(sys, "argv", [str(PATH), "unreviewed"]), self.assertRaises(RuntimeError):
            subject.main()
        with mock.patch.object(sys, "dont_write_bytecode", False), self.assertRaises(RuntimeError):
            subject.main()
        self.assertFalse((self.destination / "control-inputs-before.json").exists())

    def test_success_and_failed_exit_keep_distinct_attempt_rows_and_logs(self):
        for code in (0, 17):
            process, events = self.fake_process(exit_code=code)
            name = "exit-" + str(code)
            self.invoke_fake(process, name)
            self.assertEqual(self.rows[-1]["status"], "PASS" if code == 0 else "FAIL")
            self.assertEqual(self.rows[-1]["exit_code"], code)
            self.assertEqual(events, [("wait", 600)])
            self.assertTrue((self.destination / (name + ".log")).is_file())
            process.terminate.assert_not_called()
            process.kill.assert_not_called()

    def test_launch_failure_keeps_attempt_and_raw_partial_log(self):
        def fail(_command, **kwargs):
            self.assertEqual(self.rows[0]["status"], "PENDING")
            self.assertTrue(subject.LAUNCHING)
            kwargs["stdout"].write(b"partial launch evidence\n")
            raise OSError("synthetic launch failure")

        with mock.patch.object(subprocess, "Popen", side_effect=fail), redirect_stdout(self.output), \
                self.assertRaises(OSError):
            subject.run_command(self.destination, "synthetic", ["inert.py"], self.rows)
        self.assertEqual(self.rows[0]["error_type"], "OSError")
        self.assertEqual(self.rows[0]["status"], "ERROR")
        self.assertIsNone(self.rows[0]["exit_code"])
        self.assertFalse(subject.LAUNCHING)
        self.assertEqual((self.destination / "synthetic.log").read_bytes(), b"partial launch evidence\n")

    def test_timeout_attempt_gets_graceful_termination_before_escalation(self):
        process, events = self.fake_process(timeout=True)
        with self.assertRaises(subprocess.TimeoutExpired):
            self.invoke_fake(process)
        self.assertEqual(events, [("wait", 600), ("terminate",), ("wait", 20)])
        self.assertEqual(self.rows[0]["shutdown"], {"signal": "SIGTERM", "escalated": False, "exit_code": -15})
        self.assertEqual(self.rows[0]["status"], "ERROR")
        process.kill.assert_not_called()

    def test_stubborn_composition_child_records_unproven_external_cleanup(self):
        process, events = self.fake_process(timeout=True, stubborn=True)
        with self.assertRaises(subprocess.TimeoutExpired):
            self.invoke_fake(process, "composition")
        self.assertEqual(events, [("wait", 600), ("terminate",), ("wait", 20), ("kill",), ("wait", 5)])
        shutdown = self.rows[0]["shutdown"]
        self.assertTrue(shutdown["escalated"])
        self.assertTrue(shutdown["external_cleanup_requires_inspection"])
        self.assertEqual(shutdown["exit_code"], -9)
        self.assertEqual(self.rows[0]["status"], "ERROR")
        self.assertFalse(subject.STOPPING_CHILD)

    def test_shutdown_timeout_stays_error_and_does_not_lose_attempt(self):
        process, _ = self.fake_process(timeout=True, stubborn=True, never_reaped=True)
        with self.assertRaises(subprocess.TimeoutExpired):
            self.invoke_fake(process, "composition")
        self.assertEqual(self.rows[0]["shutdown_error_type"], "TimeoutExpired")
        self.assertEqual(self.rows[0]["status"], "ERROR")
        self.assertIsNone(self.rows[0]["exit_code"])
        self.assertFalse(subject.STOPPING_CHILD)
        self.assertEqual(json.loads(self.output.getvalue())["status"], "ERROR")

    def test_signal_during_launch_still_retires_returned_child(self):
        process, events = self.fake_process()

        def launch(*_args, **_kwargs):
            self.assertTrue(subject.LAUNCHING)
            subject.PENDING_SIGNALS.append(signal.SIGTERM)
            return process

        with mock.patch.object(subprocess, "Popen", side_effect=launch), redirect_stdout(self.output), \
                self.assertRaises(KeyboardInterrupt):
            subject.run_command(self.destination, "synthetic", ["inert.py"], self.rows)
        self.assertEqual(events, [("terminate",), ("wait", 20)])
        self.assertEqual(self.rows[0]["error_type"], "KeyboardInterrupt")
        self.assertFalse(subject.LAUNCHING)

    def test_actual_main_writes_both_manifests_and_all_command_results(self):
        before = [{"path": "synthetic.py", "sha256": "a" * 64}]
        self.assertEqual(self.main_with([before, before]), 0)
        report = json.loads((self.destination / "focused-command-results.json").read_text())
        self.assertTrue(report["inputs_unchanged"])
        self.assertIsNone(report["error_type"])
        self.assertEqual(len(report["commands"]), len(subject.COMMANDS))
        self.assertTrue(all(row["status"] == "PASS" for row in report["commands"]))
        for suffix in ("before", "after"):
            self.assertEqual(json.loads((self.destination / ("control-inputs-" + suffix + ".json")).read_text()), before)

    def test_actual_main_preserves_child_failure_when_postflight_manifest_also_fails(self):
        before = [{"path": "synthetic.py", "sha256": "a" * 64}]

        def fail(_command, **_kwargs):
            raise OSError("synthetic launch failure")

        with self.assertRaises(OSError):
            self.main_with([before, RuntimeError("synthetic invalid postflight")], fail)
        report = json.loads((self.destination / "focused-command-results.json").read_text())
        self.assertEqual(report["error_type"], "OSError")
        self.assertEqual(report["postflight_manifest_error"], "RuntimeError")
        self.assertFalse(report["inputs_unchanged"])
        self.assertEqual(len(report["commands"]), 1)
        self.assertEqual(report["commands"][0]["status"], "ERROR")
        self.assertTrue((self.destination / "control-inputs-before.json").is_file())

    def test_preflight_failure_retains_closed_results_without_launching_children(self):
        with self.assertRaises(FileNotFoundError):
            self.main_with([FileNotFoundError("synthetic missing input"), []])
        report = json.loads((self.destination / "focused-command-results.json").read_text())
        self.assertEqual(report["commands"], [])
        self.assertEqual(report["error_type"], "FileNotFoundError")
        self.assertFalse(report["inputs_unchanged"])

    def test_source_drift_alone_cannot_pass_outer_packet(self):
        before = [{"path": "synthetic.py", "sha256": "a" * 64}]
        after = [{"path": "synthetic.py", "sha256": "b" * 64}]
        self.assertEqual(self.main_with([before, after]), 1)
        report = json.loads((self.destination / "focused-command-results.json").read_text())
        self.assertFalse(report["inputs_unchanged"])
        self.assertTrue(all(row["status"] == "PASS" and row["exit_code"] == 0 for row in report["commands"]))

    def test_nonzero_child_alone_cannot_pass_outer_packet(self):
        before = [{"path": "synthetic.py", "sha256": "a" * 64}]
        factory = lambda *_args, **_kwargs: self.fake_process(exit_code=23)[0]
        self.assertEqual(self.main_with([before, before], factory), 1)
        report = json.loads((self.destination / "focused-command-results.json").read_text())
        self.assertTrue(report["inputs_unchanged"])
        self.assertTrue(all(row["status"] == "FAIL" and row["exit_code"] == 23 for row in report["commands"]))

    def test_signal_handler_defers_only_launch_shutdown_and_finalization(self):
        tree = ast.parse(PATH.read_text())
        handler = next(node for node in ast.walk(tree) if isinstance(node, ast.FunctionDef) and node.name == "interrupted")
        namespace = subject.__dict__.copy()
        exec(compile(ast.Module(body=[handler], type_ignores=[]), "<isolated-packet-handler>", "exec"), namespace)
        for flag, destination in (("LAUNCHING", "PENDING_SIGNALS"), ("STOPPING_CHILD", "DEFERRED_SIGNALS"),
                                  ("FINALIZING", "DEFERRED_SIGNALS")):
            for name in ("LAUNCHING", "STOPPING_CHILD", "FINALIZING"):
                namespace[name] = name == flag
            namespace["PENDING_SIGNALS"], namespace["DEFERRED_SIGNALS"] = [], []
            namespace["interrupted"](signal.SIGTERM, None)
            self.assertEqual(namespace[destination], [signal.SIGTERM])
        namespace.update(LAUNCHING=False, STOPPING_CHILD=False, FINALIZING=False)
        with self.assertRaises(KeyboardInterrupt):
            namespace["interrupted"](signal.SIGINT, None)


if __name__ == "__main__":
    unittest.main(verbosity=2)
