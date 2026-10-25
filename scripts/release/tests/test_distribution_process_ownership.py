"""Real local processes only; no network, signing, package build, or publication."""
from __future__ import annotations

import contextlib
import ctypes
import io
import os
from pathlib import Path
import signal
import subprocess
import sys
import tempfile
import time
import unittest
from unittest.mock import Mock, patch

from scripts.release import desktop_package as packages
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
    def exercise(self, mode, *, cancel=False, logged=False):
        with tempfile.TemporaryDirectory(prefix='parlor-distribution-process-') as temporary:
            marker = Path(temporary) / 'owned'
            unrelated = subprocess.Popen([sys.executable, '-B', '-c', 'import time;time.sleep(30)'])
            try:
                command = [sys.executable, '-B', '-c', PARENT, CHILD, str(marker), mode]
                log = Path(temporary) / 'retained-build.log'

                def execute(timeout):
                    if logged:
                        return dist.run_logged(command, log, timeout=timeout, output_limit=16 * dist.MAX_JSON)
                    return dist.run(command, timeout=timeout)

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
                            execute(timeout=10)
                elif mode == 'normal':
                    self.assertEqual(execute(timeout=10), 0 if logged else '')
                elif mode == 'output':
                    with patch.object(dist, 'MAX_JSON', 1024):
                        with self.assertRaisesRegex(RuntimeError, 'output exceeds'):
                            execute(timeout=10)
                else:
                    with self.assertRaisesRegex(RuntimeError, 'time bound'):
                        execute(timeout=3)
                if logged:
                    self.assertTrue(log.is_file(), 'Retain build log even on timeout, overflow or cancellation')
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

    def test_logged_timeout_retires_descendant_and_keeps_log(self):
        self.exercise('timeout', logged=True)

    def test_logged_success_retires_descendant_and_keeps_log(self):
        self.exercise('normal', logged=True)

    def test_logged_overflow_retires_descendant_and_keeps_log(self):
        self.exercise('output', logged=True)

    def test_logged_cancellation_retires_descendant_and_keeps_log(self):
        self.exercise('timeout', cancel=True, logged=True)

    def test_success_output_and_nonzero_allowed_exit_are_preserved(self):
        self.assertEqual(dist.run([sys.executable, '-c', "print('verified');raise SystemExit(7)"],
                                  success_codes=(7,)), 'verified' + os.linesep)


class DistributionBuildLogTest(unittest.TestCase):
    def test_log_budget_is_large_but_finite_and_read_tail_is_bounded(self):
        self.assertEqual(packages.BUILD_LOG_LIMIT, 256 * 1024 * 1024)
        self.assertEqual(packages.BUILD_LOG_TAIL_BYTES, 18_000)
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / 'large.log'
            # Sparse fixture: logical size far larger than the tail, without
            # allocating/printing a giant log in this test or the implementation.
            with path.open('wb') as stream:
                stream.seek(64 * 1024 * 1024)
                stream.write(bytes((0xe2, 0x82, 0xac)) + b'TAIL')
            original_open = Path.open
            reads = []

            class BoundedReader:
                def __enter__(self):
                    self.stream = original_open(path, 'rb')
                    return self

                def __exit__(self, *args):
                    self.stream.close()

                def seek(self, *args):
                    return self.stream.seek(*args)

                def tell(self):
                    return self.stream.tell()

                def read(self, size=-1):
                    reads.append(size)
                    if size < 0 or size > 6:
                        raise AssertionError('Unbounded log read')
                    return self.stream.read(size)

            with patch.object(Path, 'open', return_value=BoundedReader()):
                self.assertEqual(dist.read_log_tail(path, 6), '��TAIL')
            self.assertEqual(reads, [6])
            for invalid in (0, -1, dist.MAX_JSON + 1):
                with self.subTest(invalid=invalid), self.assertRaises(RuntimeError):
                    dist.read_log_tail(path, invalid)

    def test_output_budget_is_rechecked_after_descendant_finalization(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / 'build.log'
            original = dist._stop_owned_command

            def trailing_output(process):
                original(process)
                # Deterministic stand-in for descendant output racing the
                # direct process exit, before finalizer returns to its caller.
                with path.open('ab') as output:
                    output.write(b'x' * 1025)

            with patch.object(dist, '_stop_owned_command', side_effect=trailing_output):
                with self.assertRaisesRegex(RuntimeError, 'output exceeds'):
                    dist.run_logged([sys.executable, '-c', 'pass'], path, timeout=10, output_limit=1024)
            self.assertEqual(path.stat().st_size, 1025)

    def test_logged_command_returns_nonzero_and_retains_output(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / 'build.log'
            command = [sys.executable, '-c', "print('retained build evidence');raise SystemExit(7)"]
            self.assertEqual(dist.run_logged(command, path, timeout=10, output_limit=1024), 7)
            self.assertEqual(path.read_bytes(), ('retained build evidence' + os.linesep).encode())

    def test_gradle_keeps_wrapper_strict_graph_no_daemon_and_retained_failure_tail(self):
        with tempfile.TemporaryDirectory() as temporary, patch.object(packages, 'OUT', Path(temporary)):
            def failed(command, log, **kwargs):
                expected_wrapper = 'gradlew.bat' if packages.host.system() == 'Windows' else 'gradlew'
                self.assertEqual(command[0], str(packages.ROOT / expected_wrapper))
                if '--stop' in command:
                    self.assertEqual(command[1:], ['--stop', self.registry_option, '--console=plain'])
                    self.assertEqual(kwargs, {'timeout': 90, 'output_limit': 1024 * 1024})
                    log.write_bytes(b'No Gradle daemons are running.\n')
                    return 0
                self.registry_option = command[-1]
                self.assertTrue(self.registry_option.startswith('-Dorg.gradle.daemon.registry.base='))
                registry = Path(self.registry_option.split('=', 1)[1])
                # Windows TEMP may use a short (RUNNER~1) alias; production canonicalizes it.
                self.assertEqual(registry.parent, Path(temporary).resolve())
                self.assertTrue(registry.is_dir())
                self.assertEqual(command[1:-1], ['fixture-task', '--dependency-verification=strict', '--no-daemon',
                                                '--console=plain', '--stacktrace', '-Pkotlin.compiler.execution.strategy=in-process'])
                self.assertEqual(kwargs, {'timeout': 5400, 'output_limit': 256 * 1024 * 1024})
                log.write_bytes(b'not in tail' + b'x' * 18_000)
                return 7

            output = io.StringIO()
            with patch.object(packages, 'run_logged', side_effect=failed), contextlib.redirect_stdout(output):
                with self.assertRaisesRegex(RuntimeError, 'Wrapper build failed'):
                    packages.gradle(['fixture-task'])
            self.assertEqual(output.getvalue(), 'x' * 18_000 + '\n')
            self.assertTrue((Path(temporary) / 'build.log').is_file())

    def test_gradle_timeout_retains_and_reads_only_the_tail(self):
        with tempfile.TemporaryDirectory() as temporary, patch.object(packages, 'OUT', Path(temporary)):
            def timeout(command, log, **kwargs):
                if '--stop' in command:
                    log.write_bytes(b'No Gradle daemons are running.\n')
                    return 0
                log.write_bytes(b'failed build evidence')
                raise RuntimeError('Distribution tool exceeded its time bound')

            with patch.object(packages, 'run_logged', side_effect=timeout), contextlib.redirect_stdout(io.StringIO()):
                with self.assertRaisesRegex(RuntimeError, 'time bound'):
                    packages.gradle(['fixture-task'])
            self.assertEqual((Path(temporary) / 'build.log').read_bytes(), b'failed build evidence')

    def test_gradle_cleanup_failure_is_not_suppressed_and_registry_is_retained(self):
        for stop_failure in (7, RuntimeError('fixture stop timeout')):
            with tempfile.TemporaryDirectory() as temporary, patch.object(packages, 'OUT', Path(temporary)):
                registries = []

                def command(args, log, **kwargs):
                    registry = Path(next(arg.split('=', 1)[1] for arg in args if arg.startswith('-Dorg.gradle.daemon.registry.base=')))
                    registries.append(registry)
                    log.write_bytes(b'retained fixture evidence')
                    if '--stop' in args:
                        if isinstance(stop_failure, Exception):
                            raise stop_failure
                        return stop_failure
                    return 0

                with patch.object(packages, 'run_logged', side_effect=command):
                    with self.subTest(failure=stop_failure), self.assertRaisesRegex(RuntimeError, 'cleanup failed'):
                        packages.gradle(['fixture-task'])
                self.assertEqual(len(registries), 2)
                self.assertEqual(registries[0], registries[1])
                self.assertTrue((registries[0] / 'stop.log').is_file())
                self.assertTrue((Path(temporary) / 'build.log').is_file())


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
