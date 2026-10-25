"""Pinned wrapper, standalone offline fixture; never builds the application.

Exercises Gradle's real detached single-use daemon, not a Python stand-in.
Both registries belong solely to this test. Shared caches are never removed.
"""
from __future__ import annotations

import contextlib
import io
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import time
import unittest
from unittest.mock import patch

from scripts.release import desktop_package as packages
from scripts.release import github_distribution as dist


SCRIPT = '''tasks.register('markDaemon') { doLast {
    new File(rootDir, 'started.pid').text = ProcessHandle.current().pid().toString()
} }
tasks.register('holdOwned') {
    dependsOn('markDaemon')
    doLast {
        long until = System.nanoTime() + 120000000000L
        while (System.nanoTime() < until) {
            try { Thread.sleep(100) } catch (InterruptedException ignored) { }
        }
    }
}
'''


def running(pid: int) -> bool:
    if os.name == 'nt':
        import ctypes
        from ctypes import wintypes
        kernel = ctypes.WinDLL('kernel32', use_last_error=True)
        kernel.OpenProcess.argtypes = [wintypes.DWORD, wintypes.BOOL, wintypes.DWORD]
        kernel.OpenProcess.restype = wintypes.HANDLE
        kernel.WaitForSingleObject.argtypes = [wintypes.HANDLE, wintypes.DWORD]
        kernel.WaitForSingleObject.restype = wintypes.DWORD
        kernel.CloseHandle.argtypes = [wintypes.HANDLE]
        kernel.CloseHandle.restype = wintypes.BOOL
        process = kernel.OpenProcess(0x00100000, False, pid)  # SYNCHRONIZE, not terminate authority.
        if not process:
            if ctypes.get_last_error() == 87:
                return False
            raise RuntimeError('Could not inspect owned fixture process')
        try:
            state = kernel.WaitForSingleObject(process, 0)
            if state not in (0, 258):
                raise RuntimeError('Could not query owned fixture process state')
            return state == 258
        finally:
            kernel.CloseHandle(process)
    result = subprocess.run(['ps', '-o', 'stat=', '-p', str(pid)], text=True, capture_output=True, timeout=5)
    if result.returncode not in (0, 1):
        raise RuntimeError('Could not inspect owned fixture process')
    state = result.stdout.strip()
    return bool(state) and not state.startswith('Z')


class GradleDaemonRegistryTest(unittest.TestCase):
    def test_single_use_timeout_retires_only_its_registry(self):
        root = Path(tempfile.mkdtemp(prefix='parlor-gradle-registry-test-'))
        wrapper = str(packages.ROOT / ('gradlew.bat' if os.name == 'nt' else 'gradlew'))
        other_registry = root / 'other-registry'
        other_registry.mkdir(mode=0o700)
        output = root / 'owned-output'
        for name in ('other', 'owned', 'busy'):
            project = root / name
            project.mkdir()
            (project / 'settings.gradle').write_text("rootProject.name='daemon-ownership-fixture'\n")
            (project / 'build.gradle').write_text(SCRIPT)
        busy_registry = root / 'busy-registry'
        busy_registry.mkdir(mode=0o700)
        all_stopped = False
        try:
            # Windows Job Objects deliberately retire even successful detached
            # descendants. Launch this unrelated control without dist.run's job
            # so it remains an independently owned idle Gradle registry.
            with (root / 'other.log').open('wb') as log:
                result = subprocess.run([wrapper, '-p', str(root / 'other'), 'markDaemon', '--daemon', '--offline',
                    '--max-workers=1', '--console=plain', '--dependency-verification=strict',
                    '-Dorg.gradle.jvmargs=-Xmx256m', '-Dorg.gradle.daemon.registry.base=' + str(other_registry)],
                    stdout=log, stderr=subprocess.STDOUT, timeout=90, cwd=packages.ROOT, check=False)
            self.assertEqual(result.returncode, 0, 'Standalone unrelated Gradle fixture must start')
            other_pid = int((root / 'other/started.pid').read_text())
            self.assertTrue(running(other_pid))
            with patch.object(packages, 'OUT', output), patch.object(packages, 'BUILD_TIMEOUT_SECONDS', 30), \
                    contextlib.redirect_stdout(io.StringIO()):
                with self.assertRaisesRegex(RuntimeError, 'time bound'):
                    packages.gradle(['-p', str(root / 'owned'), 'holdOwned', '--offline', '--max-workers=1',
                                     '-Dorg.gradle.jvmargs=-Xmx256m'])
            marker = root / 'owned/started.pid'
            self.assertTrue(marker.is_file(), 'Timeout must exercise an executing daemon, not just wrapper startup')
            owned_pid = int(marker.read_text())
            deadline = time.monotonic() + 5
            while running(owned_pid) and time.monotonic() < deadline:
                time.sleep(.1)
            self.assertFalse(running(owned_pid), 'Owned daemon remains alive after bounded scoped finalization')
            self.assertTrue(running(other_pid), 'Another registry must remain alive')
            registries = list(output.glob('owned-gradle-daemons-*'))
            self.assertEqual(len(registries), 1)
            self.assertTrue((registries[0] / 'stop.log').is_file())
            self.assertTrue((output / 'build.log').is_file())
            # Positive control: stop a genuinely busy daemon while its client
            # is STILL connected, rather than relying on automatic client-loss
            # shutdown. Another registry must remain unaffected throughout.
            with (root / 'busy.log').open('wb') as log:
                busy = subprocess.Popen([wrapper, '-p', str(root / 'busy'), 'holdOwned', '--no-daemon', '--offline',
                    '--max-workers=1', '--console=plain', '--dependency-verification=strict',
                    '-Dorg.gradle.jvmargs=-Xmx256m', '-Dorg.gradle.daemon.registry.base=' + str(busy_registry)],
                    stdout=log, stderr=subprocess.STDOUT, cwd=packages.ROOT, start_new_session=os.name != 'nt')
                try:
                    marker = root / 'busy/started.pid'
                    deadline = time.monotonic() + 60
                    while not marker.is_file() and busy.poll() is None and time.monotonic() < deadline:
                        time.sleep(.1)
                    self.assertTrue(marker.is_file(), 'Busy stop must target an executing daemon')
                    busy_pid = int(marker.read_text())
                    self.assertIsNone(busy.poll())
                    self.assertTrue(running(busy_pid))
                    code = dist.run_logged([wrapper, '--stop', '-Dorg.gradle.daemon.registry.base=' + str(busy_registry),
                                            '--console=plain'], busy_registry / 'busy-stop.log',
                                           timeout=30, output_limit=1024 * 1024)
                    self.assertEqual(code, 0)
                    deadline = time.monotonic() + 5
                    while running(busy_pid) and time.monotonic() < deadline:
                        time.sleep(.1)
                    self.assertFalse(running(busy_pid), 'Busy daemon must actually terminate after scoped stop')
                    self.assertTrue(running(other_pid), 'Busy scoped stop must not affect another registry')
                finally:
                    dist._stop_owned_command(busy)
        finally:
            # Only registry paths created by this fixture are selected. No bare
            # PID signaling, global --stop, or deletion of shared caches.
            registries = [other_registry, busy_registry, *output.glob('owned-gradle-daemons-*')]
            cleanup = []
            for registry in registries:
                try:
                    code = dist.run_logged([wrapper, '--stop', '-Dorg.gradle.daemon.registry.base=' + str(registry),
                                            '--console=plain'], registry / 'fixture-final-stop.log',
                                           timeout=30, output_limit=1024 * 1024)
                    cleanup.append(code == 0)
                except (RuntimeError, OSError):
                    cleanup.append(False)
            all_stopped = all(cleanup)
            for name in ('other', 'owned', 'busy'):
                marker = root / name / 'started.pid'
                if marker.exists():
                    pid = int(marker.read_text())
                    deadline = time.monotonic() + 5
                    while running(pid) and time.monotonic() < deadline:
                        time.sleep(.1)
                    all_stopped = all_stopped and not running(pid)
            if all_stopped:
                shutil.rmtree(root)  # Only the exclusively created disposable fixture.
            else:
                raise RuntimeError('Owned Gradle fixture cleanup incomplete; private evidence retained')
