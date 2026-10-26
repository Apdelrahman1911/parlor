"""No networking required: bounds, source custody and real owned-process cleanup."""
import importlib.util
import os
from pathlib import Path
import signal
import subprocess
import sys
import tempfile
import time
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location('real_kit_probe', Path(__file__).with_name('run.py'))
probe = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(probe)


class PreconditionsTest(unittest.TestCase):
    def test_no_implicit_network_consent(self):
        with patch.object(probe.owned, 'run') as run:
            with self.assertRaisesRegex(RuntimeError, 'allow-local-lan'):
                probe.check_environment(False, 3)
            run.assert_not_called()

    def test_unsupported_platform_is_failure_not_skipped_success(self):
        with patch.object(probe.platform, 'system', return_value='Darwin'):
            with self.assertRaisesRegex(RuntimeError, 'Linux only'):
                probe.check_environment(True, 3)

    def test_case_count_is_bounded_before_tool_execution(self):
        with patch.object(probe.platform, 'system', return_value='Linux'), patch.object(probe.owned, 'run') as run:
            for invalid in (0, -1, 4, 1000000):
                with self.subTest(invalid=invalid), self.assertRaisesRegex(RuntimeError, 'between 1 and 3'):
                    probe.check_environment(True, invalid)
            run.assert_not_called()

    def test_total_deadline_cannot_be_reset_per_case(self):
        with patch.object(probe.time, 'monotonic', return_value=100):
            self.assertEqual(probe.remaining(110, 120), 10)
            with self.assertRaisesRegex(RuntimeError, 'Total probe'):
                probe.remaining(99, 120)

    def test_dirty_source_rejected_without_dumping_paths(self):
        with patch.object(probe.owned, 'run', return_value=' M secret-name\n'):
            with self.assertRaisesRegex(RuntimeError, 'clean committed') as error:
                probe.source()
        self.assertNotIn('secret-name', str(error.exception))

    def test_commit_and_tree_bound_and_rechecked(self):
        with patch.object(probe.owned, 'run', side_effect=['', 'a' * 40, 'b' * 40]):
            before = probe.source()
        self.assertEqual(before, {'commit': 'a' * 40, 'tree': 'b' * 40})
        with patch.object(probe, 'source', return_value={'commit': 'c' * 40, 'tree': 'b' * 40}):
            with self.assertRaisesRegex(RuntimeError, 'Source changed'):
                probe.require_same_source(before)

    def test_real_git_worktree_mutation_is_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            def git(*args):
                return subprocess.run(['git', '-C', str(root), *args], check=True,
                                      capture_output=True, text=True, timeout=10)
            git('init', '-q')
            tracked = root / 'tracked.txt'
            tracked.write_text('before')
            git('add', 'tracked.txt')
            git('-c', 'user.name=Probe Test', '-c', 'user.email=probe@example.invalid',
                '-c', 'core.hooksPath=/dev/null', 'commit', '-qm', 'test fixture')
            with patch.object(probe.owned, 'ROOT', root):
                before = probe.source()
                self.assertEqual(before['commit'], git('rev-parse', 'HEAD').stdout.strip())
                tracked.write_text('after')
                with self.assertRaisesRegex(RuntimeError, 'clean committed'):
                    probe.require_same_source(before)

    def test_runtime_custody_hashes_bytes_and_optional_empty_outputs(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            jar = root / 'test.jar'
            jar.write_bytes(b'first')
            first = probe.classpath_manifest(str(jar) + os.pathsep + str(root / 'absent-classes'), deadline=time.monotonic() + 10)
            jar.write_bytes(b'second')
            second = probe.classpath_manifest(str(jar), deadline=time.monotonic() + 10)
            self.assertNotEqual(first[0]['sha256'], second[0]['sha256'])
            with self.assertRaisesRegex(RuntimeError, 'absolute'):
                probe.classpath_manifest('relative.jar', deadline=time.monotonic() + 10)

    def test_incremental_traversal_stops_before_materializing_whole_tree(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            for index in range(20):
                (root / str(index)).write_text('fixture')
            original = os.scandir
            observed = []
            class TrackedIterator:
                def __enter__(self):
                    self.iterator = original(root)
                    return self
                def __exit__(self, *args):
                    self.iterator.close()
                def __iter__(self):
                    for entry in self.iterator:
                        observed.append(entry.name)
                        yield entry
            with patch.object(probe, 'MAX_MANIFEST_ENTRIES', 3), patch.object(probe.os, 'scandir', return_value=TrackedIterator()):
                with self.assertRaisesRegex(RuntimeError, 'traversal exceeds'):
                    probe.classpath_manifest(str(root), deadline=time.monotonic() + 10)
            self.assertEqual(len(observed), 3, 'Must stop before enumerating the remaining directory entries')

    def test_hashing_and_walking_obey_absolute_deadline(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / 'runtime.jar'
            path.write_bytes(b'fixture')
            with self.assertRaisesRegex(RuntimeError, 'Total probe time bound'):
                probe.classpath_manifest(str(path), deadline=time.monotonic() - 1)
            with self.assertRaisesRegex(RuntimeError, 'Total probe time bound'):
                probe.sha256(path, time.monotonic() - 1)

    def test_mutated_helper_is_rejected_separately_from_runtime_jars(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            jar = root / 'runtime.jar'
            helper = root / 'RealAdapterProbe.class'
            jar.write_bytes(b'runtime')
            helper.write_bytes(b'original helper')
            deadline = time.monotonic() + 10
            artifacts = probe.classpath_manifest(str(jar), deadline=deadline)
            before = probe.sha256(helper, deadline)
            probe.require_same_runtime(str(jar), artifacts, helper, before, deadline)
            helper.write_bytes(b'replaced helper')
            with self.assertRaisesRegex(RuntimeError, 'Java helper changed'):
                probe.require_same_runtime(str(jar), artifacts, helper, before, deadline)

    def test_helper_allowlist_matches_python_and_preserves_ignore(self):
        java = Path(__file__).with_name('RealAdapterProbe.java').read_text()
        for case in probe.CASES:
            self.assertIn('"' + case + '"', java)
        self.assertIn('!ALLOWED.contains(args[0])', java)
        self.assertIn('fixtureType.getMethod("teardown").invoke(fixture)', java)
        self.assertIn('finally', java)
        fixture = probe.ROOT / 'shared/transport-p2p/src/desktopTest/kotlin/com/parlor/transport/p2p/P2pKitRoomTransportLoopbackTest.kt'
        self.assertEqual(fixture.read_text().count('@Ignore('), 3)

    def test_private_gradle_registry_survives_build_failure_and_cleanup_is_narrow(self):
        with tempfile.TemporaryDirectory() as temporary:
            out = Path(temporary)
            calls = []
            def fake_run(command, log, **kwargs):
                calls.append((command, kwargs))
                return 1 if len(calls) == 1 else 0
            with patch.object(probe.owned, 'run_logged', side_effect=fake_run):
                with self.assertRaisesRegex(RuntimeError, 'Strict compilation failed'):
                    probe.prepare(out, time.monotonic() + 100)
            self.assertEqual(len(calls), 2)
            options = [arg for arg in calls[0][0] if arg.startswith('-Dorg.gradle.daemon.registry.base=')]
            self.assertEqual(options, ['-Dorg.gradle.daemon.registry.base=' + str(out / 'owned-gradle-daemons')])
            self.assertEqual(calls[1][0], [str(probe.ROOT / 'gradlew'), '--stop', options[0], '--console=plain'])
            self.assertIn('--dependency-verification=strict', calls[0][0])
            self.assertIn('--max-workers=2', calls[0][0])


@unittest.skipUnless(sys.platform.startswith('linux'), 'Actual process-group tests require Linux')
class OwnedProcessTest(unittest.TestCase):
    def exercise(self, mode):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            marker = root / 'owned-heartbeat'
            child = '''import pathlib,signal,sys,time
signal.signal(signal.SIGTERM, signal.SIG_IGN)
p=pathlib.Path(sys.argv[1]);p.write_text(str(__import__('os').getpid())+'\\n')
while True:
 with p.open('a') as stream: stream.write('tick\\n')
 time.sleep(.03)
'''
            parent = '''import pathlib,subprocess,sys,time
subprocess.Popen([sys.executable,'-c',sys.argv[1],sys.argv[2]])
for _ in range(500):
 if pathlib.Path(sys.argv[2]).exists(): break
 time.sleep(.01)
else: raise RuntimeError('Owned child did not start')
if sys.argv[3]=='output': print('x'*20000,flush=True)
if sys.argv[3]!='normal': time.sleep(30)
'''
            unrelated = subprocess.Popen([sys.executable, '-c', 'import time;time.sleep(30)'])
            row = {}
            try:
                def sampler(pid, evidence):
                    if mode == 'cancel' and marker.exists():
                        raise KeyboardInterrupt()
                command = [sys.executable, '-c', parent, child, str(marker), mode]
                if mode == 'normal':
                    result = probe.run_case(command, root / 'case.log', timeout=5, row=row, sampler=sampler)
                    self.assertEqual(result['exit_code'], 0)
                    self.assertTrue(result['root_natural_exit'])
                else:
                    exception = KeyboardInterrupt if mode == 'cancel' else RuntimeError
                    with self.assertRaises(exception):
                        probe.run_case(command, root / 'case.log', timeout=.7 if mode == 'timeout' else 5,
                                       output_limit=1000, row=row, sampler=sampler)
                self.assertTrue(marker.exists(), 'Descendant must really execute')
                self.assertTrue(row['cleanup_completed'])
                time.sleep(.1)
                before = marker.read_bytes()
                time.sleep(.2)
                self.assertEqual(marker.read_bytes(), before, 'Owned descendant still running')
                self.assertIsNone(unrelated.poll(), 'Unrelated process was affected')
            finally:
                if marker.exists():
                    try:
                        os.kill(int(marker.read_text().splitlines()[0]), signal.SIGKILL)
                    except ProcessLookupError:
                        pass
                unrelated.terminate()
                unrelated.wait(timeout=5)

    def test_timeout_retires_owned_descendant_only(self):
        self.exercise('timeout')

    def test_output_overflow_retires_owned_descendant_only(self):
        self.exercise('output')

    def test_root_success_does_not_leave_descendant_running(self):
        self.exercise('normal')

    def test_cancellation_retires_owned_descendant_only(self):
        self.exercise('cancel')

    def test_expired_work_budget_does_not_interrupt_owned_cleanup(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / 'timeout.log'
            row = {}
            original = probe.owned._stop_owned_command
            completed = []
            def slow_cleanup(process):
                time.sleep(.1)  # Cleanup crosses the already-expired work deadline.
                original(process)
                completed.append(True)
            with patch.object(probe.owned, '_stop_owned_command', side_effect=slow_cleanup):
                with self.assertRaisesRegex(RuntimeError, 'time bound'):
                    probe.run_case([sys.executable, '-c', 'import time;time.sleep(5)'], path,
                                   timeout=.01, row=row)
            self.assertEqual(completed, [True])
            self.assertTrue(row['cleanup_completed'])

    def test_post_cleanup_output_bound_is_enforced(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / 'case.log'
            original = probe.owned._stop_owned_command
            def append_after_cleanup(process):
                original(process)
                with path.open('ab') as output:
                    output.write(b'x' * 1025)
            with patch.object(probe.owned, '_stop_owned_command', side_effect=append_after_cleanup):
                with self.assertRaisesRegex(RuntimeError, 'output exceeds'):
                    probe.run_case([sys.executable, '-c', 'pass'], path, timeout=5, output_limit=1024)


if __name__ == '__main__':
    unittest.main()
