"""Real local processes only; no network, signing, package build, or publication."""
from __future__ import annotations

import ctypes
import os
from pathlib import Path
import signal
import subprocess
import sys
import tempfile
import time
import unittest
from unittest.mock import Mock, patch

from scripts.release import github_distribution as dist
from scripts.release import _windows_job_supervisor as windows


CHILD = r'''
import os, signal, sys, time
from pathlib import Path
if os.name != 'nt':
    signal.signal(signal.SIGTERM, signal.SIG_IGN)
p = Path(sys.argv[1])
p.write_text(str(os.getpid()) + '\n')
for _ in range(600):
    with p.open('a') as stream:
        stream.write('alive\n')
    time.sleep(.05)
'''
PARENT = r'''
import subprocess, sys, time
from pathlib import Path
subprocess.Popen([sys.executable, '-B', '-c', sys.argv[1], sys.argv[2]])
for _ in range(500):
    if Path(sys.argv[2]).exists():
        break
    time.sleep(.01)
else:
    raise RuntimeError('Fixture descendant did not start')
if sys.argv[3] == 'output':
    print('x' * 40000, flush=True)
if sys.argv[3] != 'normal':
    time.sleep(30)
'''


class DistributionOwnedProcessTest(unittest.TestCase):
    def exercise(self, mode, *, cancel=False):
        with tempfile.TemporaryDirectory(prefix='parlor-distribution-process-') as temporary:
            marker = Path(temporary) / 'owned'
            unrelated = subprocess.Popen([sys.executable, '-B', '-c', 'import time;time.sleep(30)'])
            try:
                command = [sys.executable, '-B', '-c', PARENT, CHILD, str(marker), mode]
                if cancel:
                    original_sleep = time.sleep
                    interrupted = False

                    def interrupt_after_child_starts(seconds):
                        nonlocal interrupted
                        if marker.exists() and not interrupted:
                            interrupted = True
                            raise KeyboardInterrupt
                        original_sleep(seconds)

                    with patch.object(dist.time, 'sleep', side_effect=interrupt_after_child_starts):
                        with self.assertRaises(KeyboardInterrupt):
                            dist.run(command, timeout=10)
                elif mode == 'normal':
                    self.assertEqual(dist.run(command, timeout=10), '')
                elif mode == 'output':
                    with patch.object(dist, 'MAX_JSON', 1024):
                        with self.assertRaisesRegex(RuntimeError, 'output exceeds'):
                            dist.run(command, timeout=10)
                else:
                    with self.assertRaisesRegex(RuntimeError, 'time bound'):
                        dist.run(command, timeout=3)
                self.assertTrue(marker.exists(), 'Owned descendant must actually execute')
                # Job termination can finish just after its supervisor is reaped.
                time.sleep(.2)
                before = marker.read_bytes()
                time.sleep(.25)
                self.assertEqual(marker.read_bytes(), before, 'Descendant survived runner finalization')
                self.assertIsNone(unrelated.poll(), 'Unrelated process must not be terminated')
            finally:
                # Safety when exercising a broken runner: terminate only the PID
                # reported by this private fixture, never process-name matching.
                if marker.exists():
                    pid = int(marker.read_text().splitlines()[0])
                    try:
                        os.kill(pid, signal.SIGTERM if os.name == 'nt' else signal.SIGKILL)
                    except ProcessLookupError:
                        pass
                    except OSError as error:
                        # Windows reports an already-exited PID as invalid
                        # parameter or access denied instead of ESRCH.
                        if os.name != 'nt' or getattr(error, 'winerror', None) not in (5, 87):
                            raise
                unrelated.terminate()
                unrelated.wait(timeout=5)

    def test_timeout_retires_term_resistant_descendant_inheriting_output(self):
        self.exercise('timeout')

    def test_successful_root_exit_also_retires_descendant(self):
        self.exercise('normal')

    def test_output_limit_retires_descendant(self):
        self.exercise('output')

    def test_python_cancellation_retires_descendant(self):
        self.exercise('timeout', cancel=True)

    def test_success_output_and_nonzero_allowed_exit_are_preserved(self):
        self.assertEqual(dist.run([sys.executable, '-c', "print('verified');raise SystemExit(7)"],
                                  success_codes=(7,)), 'verified' + os.linesep)


class WindowsJobContractTest(unittest.TestCase):
    def kernel(self):
        kernel = Mock()
        kernel.CreateJobObjectW.return_value = 77
        kernel.SetInformationJobObject.return_value = True
        kernel.GetCurrentProcess.return_value = 88
        kernel.AssignProcessToJobObject.return_value = True
        return kernel

    def test_job_is_unnamed_noninheritable_and_kills_on_close(self):
        kernel = self.kernel()
        self.assertEqual(windows.own_job(kernel), 77)
        kernel.CreateJobObjectW.assert_called_once_with(None, None)
        call = kernel.SetInformationJobObject.call_args.args
        self.assertEqual(call[:2], (77, 9))
        limits = ctypes.cast(call[2], ctypes.POINTER(windows.ExtendedLimits)).contents
        self.assertEqual(limits.BasicLimitInformation.LimitFlags, 0x2000)
        kernel.AssignProcessToJobObject.assert_called_once_with(77, 88)
        kernel.CloseHandle.assert_not_called()

    def test_setup_failure_closes_handle_and_never_launches_tool(self):
        for step in ('CreateJobObjectW', 'SetInformationJobObject', 'AssignProcessToJobObject'):
            kernel = self.kernel()
            getattr(kernel, step).return_value = 0
            with patch.object(windows.sys, 'platform', 'win32'), \
                    patch.object(windows.ctypes, 'WinDLL', return_value=kernel, create=True), \
                    patch.object(windows.subprocess, 'call') as launch:
                with self.subTest(step=step), self.assertRaises(RuntimeError):
                    windows.main(['never-executed'])
                launch.assert_not_called()
                if step == 'CreateJobObjectW':
                    kernel.CloseHandle.assert_not_called()
                else:
                    kernel.CloseHandle.assert_called_once_with(77)

    def test_assignment_precedes_tool_launch(self):
        kernel = self.kernel()

        def started(command):
            kernel.AssignProcessToJobObject.assert_called_once_with(77, 88)
            return 7

        with patch.object(windows.sys, 'platform', 'win32'), \
                patch.object(windows.ctypes, 'WinDLL', return_value=kernel, create=True), \
                patch.object(windows.subprocess, 'call', side_effect=started):
            self.assertEqual(windows.main(['fixture']), 7)


if __name__ == '__main__':
    unittest.main()
