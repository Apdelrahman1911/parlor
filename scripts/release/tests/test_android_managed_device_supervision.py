from __future__ import annotations

from contextlib import redirect_stdout
import io
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
from scripts.android import supervise_managed_device as supervision


DETACHED_CHILD = r"""
import os, pathlib, signal, sys
read_fd, write_fd = os.pipe()
if os.fork():
    os.close(write_fd)
    ready = os.read(read_fd, 1)
    os.close(read_fd)
    sys.exit(int(sys.argv[1]) if ready == b'R' else 99)
os.close(read_fd)
os.setsid()
def terminate(_signum, _frame):
    pathlib.Path(sys.argv[2]).write_text('owned child received TERM\n')
    os._exit(0)
signal.signal(signal.SIGTERM, terminate)
# The fixture must not survive an unexpected supervisor/test failure.
signal.alarm(20)
os.write(write_fd, b'R')
os.close(write_fd)
while True:
    signal.pause()
"""


FIXTURE_GUARDIAN = r"""
import ctypes, json, os, pathlib, signal, subprocess, sys, time
# Independent outer containment: even a broken supervisor must not leave a
# detached fixture/zombie with the VPS init process. Never mutate unittest's
# process, adopt its unrelated sibling, or use process-name/group signalling.
if ctypes.CDLL(None).prctl(36, 1, 0, 0, 0) != 0:
    raise SystemExit(125)
process = None
status = 125
try:
    process = subprocess.Popen(sys.argv[2:])
    try:
        status = process.wait(timeout=25)
    except subprocess.TimeoutExpired:
        status = 124
finally:
    if process is not None and process.poll() is None:
        process.kill()
        process.wait(timeout=3)
    deadline = time.monotonic() + 5
    rescued = 0
    clean = False
    while time.monotonic() < deadline:
        try:
            pid, _ = os.waitpid(-1, os.WNOHANG)
        except ChildProcessError:
            clean = True
            break
        if pid:
            rescued += 1
            continue
        for child in pathlib.Path(f'/proc/self/task/{os.getpid()}/children').read_text().split():
            descriptor = None
            try:
                descriptor = os.pidfd_open(int(child))
                waited, _ = os.waitpid(int(child), os.WNOHANG)
                if waited:
                    rescued += 1
                else:
                    signal.pidfd_send_signal(descriptor, signal.SIGKILL)
            except (ProcessLookupError, ChildProcessError):
                pass
            finally:
                if descriptor is not None:
                    os.close(descriptor)
        time.sleep(0.01)
    pathlib.Path(sys.argv[1]).write_text(json.dumps({'result': 'PASS' if clean else 'FAIL',
                                                   'rescue_reaped': rescued}))
    if not clean:
        status = 125
sys.exit(status if status >= 0 else 128 - status)
"""


@unittest.skipUnless(sys.platform == "linux", "Linux-only subreaper/pidfd ownership")
class AndroidManagedDeviceSupervisionTest(unittest.TestCase):
    """Tiny owned processes and synthetic adapters; never Gradle or an emulator."""

    def setUp(self):
        temporary = TemporaryDirectory(prefix="parlor-managed-supervision-test-")
        self.addCleanup(temporary.cleanup)
        self.base = Path(temporary.name).resolve()

    def test_detached_descendants_retire_without_touching_a_preexisting_sibling(self):
        try:
            with Path(f"/proc/self/task/{os.getpid()}/children").open("rb"):
                pass
        except FileNotFoundError:
            reason = "Linux host lacks its /proc/self/task/<pid>/children file; live subreaper fixture cannot run"
            if os.environ.get("GITHUB_ACTIONS") == "true":
                self.fail(reason + "; this capability is required on GitHub Actions")
            self.skipTest(reason)

        unrelated = subprocess.Popen(
            [sys.executable, "-I", "-B", "-c", "import signal; signal.alarm(45); signal.pause()"],
            stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
            start_new_session=True,
        )
        try:
            for runtime_exit in (0, 23):
                with self.subTest(runtime_exit=runtime_exit):
                    receipt = self.base / f"receipt-{runtime_exit}.json"
                    guardian_receipt = self.base / f"fixture-cleanup-{runtime_exit}.json"
                    marker = self.base / f"owned-term-{runtime_exit}.txt"
                    completed = subprocess.run(
                        [sys.executable, "-I", "-B", "-c", FIXTURE_GUARDIAN, str(guardian_receipt),
                         sys.executable, "-I", "-B", str(ROOT / "scripts/android/supervise_managed_device.py"),
                         "--receipt", str(receipt), "--", sys.executable, "-I", "-B", "-c",
                         DETACHED_CHILD, str(runtime_exit), str(marker)],
                        stdin=subprocess.DEVNULL, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                        timeout=40, check=False,
                    )
                    try:
                        self.assertEqual(json.loads(guardian_receipt.read_text()), {"result": "PASS", "rescue_reaped": 0})
                        self.assertEqual(completed.returncode, runtime_exit)
                        self.assertEqual(marker.read_text(), "owned child received TERM\n")
                        evidence = json.loads(receipt.read_text())
                        self.assertEqual(evidence["runtime_exit_code"], runtime_exit)
                        self.assertEqual(evidence["cleanup"]["result"], "PASS")
                        self.assertEqual(evidence["cleanup"]["remaining"], [])
                        self.assertGreaterEqual(evidence["cleanup"]["term_signals"], 1)
                        self.assertGreaterEqual(evidence["cleanup"]["reaped"], 1)
                        self.assertIsNone(unrelated.poll(), "preexisting non-descendant was terminated")
                    except Exception:
                        # Retain bounded failure evidence in the test log before
                        # TemporaryDirectory removes these synthetic receipts.
                        diagnostic = {"exit_code": completed.returncode,
                                      "stdout_tail": completed.stdout[-8192:].decode(errors="replace"),
                                      "stderr_tail": completed.stderr[-8192:].decode(errors="replace")}
                        for label, path in (("supervisor_receipt", receipt), ("guardian_receipt", guardian_receipt)):
                            try:
                                with path.open("rb") as stream:
                                    diagnostic[label] = stream.read(8192).decode(errors="replace")
                            except OSError as error:
                                diagnostic[label] = {"error": type(error).__name__, "errno": error.errno}
                        print("Managed-device fixture failure: " + json.dumps(diagnostic), file=sys.stderr)
                        raise
        finally:
            if unrelated.poll() is None:
                unrelated.terminate()
            try:
                unrelated.wait(timeout=3)
            except subprocess.TimeoutExpired:
                unrelated.kill()
                unrelated.wait(timeout=3)

    def test_pidfd_signal_requires_a_waitable_child_and_always_closes_the_handle(self):
        for observation in ("foreign", "reaped", "ambiguous", "live", "exiting"):
            with self.subTest(observation=observation), \
                    patch.object(supervision.os, "pidfd_open", return_value=91) as opened, \
                    patch.object(supervision.os, "close") as closed, \
                    patch.object(supervision.os, "waitpid") as waited, \
                    patch.object(supervision.signal, "pidfd_send_signal") as sent:
                if observation == "foreign":
                    waited.side_effect = ChildProcessError("not this supervisor's child")
                    with self.assertRaises(ChildProcessError):
                        supervision.signal_owned_child(123, signal.SIGTERM)
                else:
                    waited.return_value = {"reaped": (123, 0), "ambiguous": (456, 0),
                                           "live": (0, 0), "exiting": (0, 0)}[observation]
                    if observation == "ambiguous":
                        with self.assertRaises(RuntimeError):
                            supervision.signal_owned_child(123, signal.SIGTERM)
                    else:
                        if observation == "exiting":
                            sent.side_effect = ProcessLookupError()
                        self.assertEqual(supervision.signal_owned_child(123, signal.SIGTERM),
                                         {"reaped": "reaped", "live": "signalled", "exiting": "exiting"}[observation])
                opened.assert_called_once_with(123)
                waited.assert_called_once_with(123, os.WNOHANG)
                closed.assert_called_once_with(91)
                if observation in ("live", "exiting"):
                    sent.assert_called_once_with(91, signal.SIGTERM)
                else:
                    sent.assert_not_called()

    def retirement(self, *, graceful: bool, stuck: bool = False):
        live, zombies, signals, clock = {123}, [], [], [0.0]

        def wait(pid, options):
            self.assertEqual((pid, options), (-1, os.WNOHANG))
            if zombies:
                return zombies.pop(0), 0
            if live:
                return 0, 0
            raise ChildProcessError()

        def send(pid, number):
            self.assertIn(pid, live)
            signals.append((pid, number))
            if not stuck and (graceful or number == signal.SIGKILL):
                live.remove(pid)
                zombies.append(pid)
                if graceful and pid == 123:
                    # Killing the original parent adopts a formerly detached
                    # grandchild after the first enumeration has completed.
                    live.add(456)
            return "signalled"

        def sleep(seconds):
            clock[0] += seconds

        with patch.object(supervision.os, "waitpid", side_effect=wait), \
                patch.object(supervision, "owned_children", side_effect=lambda: sorted(live)), \
                patch.object(supervision, "signal_owned_child", side_effect=send), \
                patch.object(supervision.time, "monotonic", side_effect=lambda: clock[0]), \
                patch.object(supervision.time, "sleep", side_effect=sleep):
            result = supervision.retire_owned_children(term_seconds=0.1, kill_seconds=0.1)
        return result, signals, clock[0]

    def test_retirement_rediscovers_adopted_grandchildren_and_escalates_only_if_needed(self):
        for graceful in (True, False):
            with self.subTest(graceful=graceful):
                result, signals, _ = self.retirement(graceful=graceful)
                self.assertEqual(result["result"], "PASS")
                self.assertEqual(result["remaining"], [])
                self.assertEqual(result["errors"], [])
                self.assertEqual(result["reaped"], 2 if graceful else 1)
                self.assertEqual(signals, [(123, signal.SIGTERM),
                                           (456, signal.SIGTERM) if graceful else (123, signal.SIGKILL)])

    def test_unretired_child_fails_with_a_bounded_attempt_not_an_absence_pass(self):
        result, signals, elapsed = self.retirement(graceful=False, stuck=True)
        self.assertEqual(result["result"], "FAIL")
        self.assertEqual(result["remaining"], [123])
        self.assertIn("OWNED_CHILDREN_DID_NOT_EXIT", result["errors"])
        self.assertEqual(signals, [(123, signal.SIGTERM), (123, signal.SIGKILL)])
        self.assertLessEqual(elapsed, 0.3)

    def test_runtime_failure_is_preserved_but_cleanup_failure_invalidates_runtime_success(self):
        for runtime, cleanup_result, expected in ((0, "PASS", 0), (23, "PASS", 23),
                                                  (0, "FAIL", 1), (23, "FAIL", 23), (-9, "PASS", 137)):
            with self.subTest(runtime=runtime, cleanup=cleanup_result):
                path = self.base / f"status-{runtime}-{cleanup_result}.json"
                child = Mock(returncode=runtime)
                child.poll.return_value = runtime
                with patch.object(supervision, "owned_children", return_value=[]), \
                        patch.object(supervision, "enable_subreaper"), \
                        patch.object(supervision.subprocess, "Popen", return_value=child) as launched, \
                        patch.object(supervision, "retire_owned_children", return_value={"result": cleanup_result}) as retired, \
                        redirect_stdout(io.StringIO()):
                    self.assertEqual(supervision.supervise(["fake-gradle"], path), expected)
                launched.assert_called_once_with(["fake-gradle"], start_new_session=True)
                retired.assert_called_once_with()
                evidence = json.loads(path.read_text())
                self.assertEqual(evidence["runtime_exit_code"], runtime)
                self.assertEqual(evidence["cleanup"]["result"], cleanup_result)
                self.assertEqual(evidence["result"], "PASS" if expected == 0 else "FAIL")

    def test_receipt_collision_preexisting_children_and_missing_subreaper_prevent_launch(self):
        for refused in ("receipt", "children", "proc_children", "subreaper"):
            with self.subTest(refused=refused):
                path = self.base / f"refused-{refused}.json"
                if refused == "receipt":
                    path.write_text("preserve prior evidence")
                with patch.object(supervision, "owned_children", return_value=[123] if refused == "children" else [],
                                  side_effect=FileNotFoundError(2, "missing children file") if refused == "proc_children" else None), \
                        patch.object(supervision, "enable_subreaper",
                                     side_effect=RuntimeError("unavailable") if refused == "subreaper" else None), \
                        patch.object(supervision.subprocess, "Popen") as launched, \
                        patch.object(supervision, "retire_owned_children") as retired, \
                        redirect_stdout(io.StringIO()):
                    if refused == "receipt":
                        with self.assertRaises(FileExistsError):
                            supervision.supervise(["fake-gradle"], path)
                        self.assertEqual(path.read_text(), "preserve prior evidence")
                    else:
                        self.assertEqual(supervision.supervise(["fake-gradle"], path), 1)
                        evidence = json.loads(path.read_text())
                        self.assertEqual(evidence["cleanup"]["result"], "NOT_STARTED")
                        self.assertTrue(evidence["errors"])
                launched.assert_not_called()
                retired.assert_not_called()

        # Kernel support is probed before prctl/adoption or any build starts;
        # Python merely exporting pidfd_* does not establish syscall support.
        for operation in ("open", "signal"):
            with self.subTest(kernel_failure=operation), \
                    patch.object(supervision.os, "pidfd_open", return_value=91,
                                 side_effect=OSError("unsupported") if operation == "open" else None), \
                    patch.object(supervision.signal, "pidfd_send_signal",
                                 side_effect=OSError("unsupported") if operation == "signal" else None), \
                    patch.object(supervision.os, "close") as closed, \
                    patch.object(supervision.ctypes, "CDLL") as libc:
                with self.assertRaises(OSError):
                    supervision.enable_subreaper()
                libc.assert_not_called()
                if operation == "signal":
                    closed.assert_called_once_with(91)
                else:
                    closed.assert_not_called()

    def test_interrupt_still_retires_owned_children_and_retains_signal_exit_status(self):
        handlers = {}

        def handler(number, callback):
            previous = handlers.get(number, signal.SIG_DFL)
            handlers[number] = callback
            return previous

        def poll():
            handlers[signal.SIGTERM](signal.SIGTERM, None)
            return None

        child = Mock(returncode=None)
        child.poll.side_effect = poll
        path = self.base / "interrupted.json"
        with patch.object(supervision, "owned_children", return_value=[]), \
                patch.object(supervision, "enable_subreaper"), \
                patch.object(supervision.signal, "signal", side_effect=handler), \
                patch.object(supervision.subprocess, "Popen", return_value=child), \
                patch.object(supervision, "retire_owned_children", return_value={"result": "PASS"}) as retired, \
                redirect_stdout(io.StringIO()):
            self.assertEqual(supervision.supervise(["fake-gradle"], path), 128 + signal.SIGTERM)
        retired.assert_called_once_with()
        evidence = json.loads(path.read_text())
        self.assertEqual(evidence["interrupted_signal"], signal.SIGTERM)
        self.assertIsNone(evidence["runtime_exit_code"])
        self.assertEqual(evidence["cleanup"]["result"], "PASS")
        self.assertTrue(all(callback == signal.SIG_DFL for callback in handlers.values()))


if __name__ == "__main__":
    unittest.main()
