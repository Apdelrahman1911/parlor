"""Portable execution of only the pending-query deadline/one-profile policy.

Synthetic process/clock adapters are unit fixtures, not native stack evidence.
"""
import importlib.util
import os
from pathlib import Path
from tempfile import TemporaryDirectory
from types import SimpleNamespace
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('query_probe', Path(__file__).with_name('launch.py'))
p = importlib.util.module_from_spec(spec)
spec.loader.exec_module(p)


class FirstPendingControls(unittest.TestCase):
    def execute(self, *, finish_at=None, state=None, report_format="spindump"):
        clock = SimpleNamespace(now=1000.0)
        child = SimpleNamespace(pid=123, returncode=None, killed=False)
        def wait(timeout=None):
            if child.killed:
                return -9
            if child.returncode is not None:
                return child.returncode
            clock.now += timeout
            if finish_at is not None and clock.now >= 1000 + finish_at:
                child.returncode = 0
                child.stdout.write(b'2565\n')
                child.stdout.flush()
                return 0
            raise p.subprocess.TimeoutExpired(['owned-query'], timeout)
        child.wait = wait
        child.kill = lambda: setattr(child, 'killed', True)
        child.poll = lambda: -9 if child.killed else child.returncode
        info = SimpleNamespace(pid=123, uid=os.getuid(), ppid=os.getpid(),
                               start_sec=1000, start_usec=0, comm=b'simctl')
        with TemporaryDirectory() as temp:
            out = Path(temp)
            tool = out / 'simctl'
            tool.write_bytes(b'pinned fixture')
            backend = SimpleNamespace(_basic=lambda pid: info, _path=lambda pid: str(tool),
                                      _pids=lambda: self.fail('No Simulator parent: process scan forbidden'))
            state = {'attempted': False} if state is None else state
            def launch(argv, **streams):
                self.assertNotEqual(streams['stdout'], p.subprocess.PIPE)
                self.assertNotEqual(streams['stderr'], p.subprocess.PIPE)
                child.stdout = streams['stdout']
                return child
            with patch.object(p.Path, 'resolve', return_value=tool), \
                 patch.dict(os.environ, {'DEVELOPER_DIR': '/pinned'}), \
                 patch.object(p.time, 'time', side_effect=lambda: clock.now), \
                 patch.object(p.time, 'monotonic', side_effect=lambda: clock.now), \
                 patch.object(p.subprocess, 'Popen', side_effect=launch) as opened, \
                 patch.object(p, 'profile_command', return_value=0) as sampled:
                code, _ = p.observe_print(out, ['owned-query'], backend, None, None,
                                         label='managerpid', capture_state=state, trusted_tools=(str(tool),),
                                         report_format=report_format)
                self.last_profile_call = sampled.call_args
            import json
            record = json.loads((out / 'managerpid.json').read_text())
            target = json.loads((out / 'target-before.json').read_text()) if sampled.called else None
        return code, record, target, sampled.call_count, opened.call_count, state, clock.now

    def test_stalled_manager_query_is_profiled_once_and_keeps_original_twenty_seconds(self):
        code, record, target, profiles, opens, state, end = self.execute()
        self.assertIsNone(code)
        self.assertEqual(record['status'], 'TIMED_OUT')
        self.assertEqual(end, 1020.0)
        self.assertEqual((profiles, opens), (1, 1))
        self.assertTrue(state['attempted'])
        self.assertTrue(record['direct_child_reaped'])
        self.assertEqual(record['stream_transport'], 'PRIVATE_TEMPORARY_FILES_NOT_PIPE_EOF')
        self.assertEqual(record['cleanup_wait_limit_seconds'], 1)
        self.assertEqual(target['scope'], 'direct-query-dispatcher-not-keyboard')
        self.assertEqual(target['query_argv'], ['owned-query'])
        self.assertEqual(target['original_deadline_seconds'], 20)

    def test_fast_native_query_does_not_profile_or_delay_completion(self):
        code, record, target, profiles, opens, state, end = self.execute(finish_at=1)
        self.assertEqual(code, 0)
        self.assertEqual(record['status'], 'EXITED')
        self.assertEqual((profiles, opens), (0, 1))
        self.assertFalse(state['attempted'])
        self.assertIsNone(target)
        self.assertEqual(end, 1001.0)

    def test_already_attempted_profile_never_grants_a_second_query_capture(self):
        _, record, target, profiles, opens, _, end = self.execute(state={'attempted': True})
        self.assertEqual((profiles, opens), (0, 1))
        self.assertIsNone(target)
        self.assertEqual(record['status'], 'TIMED_OUT')
        self.assertEqual(end, 1020.0)


if __name__ == '__main__':
    unittest.main()
