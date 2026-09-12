"""Synthetic process tables/syscalls only: no OS signal, worker, adb, or emulator."""
import ctypes
from dataclasses import replace
import errno
import os
import signal
import unittest
from unittest import mock

from darwin_owned_processes import (
    DarwinBackend, Identity, MAX_PROCESSES, OwnedProcesses, ProcBsdInfo, SigInfo,
)


def identity(pid, parent, group, born=1, command='/owned/worker', generation=10):
    return Identity(pid, parent, group, os.getuid(), (1_000, born), command,
                    (0, os.getuid(), 0, os.getuid(), 0, pid, 7, generation))


class FakeBackend:
    def __init__(self):
        self.rows, self.exits = {}, {}
        self.groups, self.parents, self.attempted, self.delivered = [], [], [], []
        self.signal_race = None
        self.denied = False

    def read(self, pid):
        return self.rows.get(pid)

    def group_members(self, group):
        self.groups.append(group)
        return [pid for pid, item in self.rows.items() if item.group == group]

    def children(self, parent):
        self.parents.append(parent)
        return [pid for pid, item in self.rows.items() if item.parent == parent]

    def wait_status(self, pid):
        if pid not in self.exits:
            raise ChildProcessError('Not a waitable direct child')
        return self.exits[pid]

    def signal(self, record, signum):
        self.attempted.append((record, signum))
        if self.denied:
            raise OSError(errno.EPERM, 'synthetic denied token signal')
        if self.signal_race:
            self.signal_race(record)
            self.signal_race = None
        current = self.rows.get(record.pid)
        if current is None or current.token != record.token:
            return False  # Models the kernel PID-generation comparison.
        self.delivered.append((record.pid, record.token[7], signum))
        self.rows.pop(record.pid)
        if record.pid in self.exits:
            self.exits[record.pid] = -signum
        return True


class FakeProcess:
    def __init__(self, pid, backend):
        self.pid, self.backend = pid, backend
        self.returncode, self.waits = None, 0

    def wait(self, timeout):
        self.waits += 1
        result = self.backend.exits.pop(self.pid)
        if result is None:
            raise AssertionError('Attempted to reap a still-running child')
        self.returncode = result
        return result


class ProcessLifetimeTest(unittest.TestCase):
    def setUp(self):
        self.backend = FakeBackend()
        self.now = 0

        def sleep(delay):
            self.now += delay

        self.registry = OwnedProcesses(self.backend, clock=lambda: self.now, sleep=sleep)
        # Any numeric signaling, even harmless signal0, would violate the policy.
        self.kill = mock.patch('os.kill', side_effect=AssertionError('numeric kill forbidden')).start()
        self.killpg = mock.patch('os.killpg', side_effect=AssertionError('numeric group kill forbidden')).start()
        self.addCleanup(mock.patch.stopall)

    def launch(self, pid=100, alive=True, child=None):
        self.backend.exits[pid] = None if alive else 0
        if alive:
            self.backend.rows[pid] = identity(pid, os.getpid(), pid)
        if child:
            self.backend.rows[child.pid] = child
        process = FakeProcess(pid, self.backend)
        return self.registry.register(process, 'synthetic', ['/owned/worker'])

    def retire_synthetic_leader(self, launch):
        self.backend.rows.pop(launch.pid, None)
        self.backend.exits[launch.pid] = 0
        launch.retired = True
        launch.process.wait(timeout=1)

    def test_completed_launch_cannot_grant_recycled_group(self):
        launch = self.launch(alive=False)
        self.registry.finish(launch)
        before = list(self.backend.groups)
        self.backend.rows[100] = identity(100, 1, 100, born=2, command='/unrelated/process', generation=30)
        self.backend.rows[101] = identity(101, 100, 100, born=2, command='/unrelated/child', generation=31)
        self.assertEqual([], self.registry.shutdown())
        self.assertEqual(before, self.backend.groups)
        self.assertEqual([], self.backend.attempted)
        self.assertEqual({100, 101}, set(self.backend.rows))

    def test_completed_leader_surviving_attested_child_is_still_stopped(self):
        child = identity(101, 100, 100, generation=11)
        launch = self.launch(child=child)
        self.retire_synthetic_leader(launch)
        self.backend.rows[101] = replace(child, parent=1, group=101)
        self.backend.rows[100] = identity(100, 1, 100, born=2, command='/unrelated', generation=30)
        before = list(self.backend.groups)
        self.assertEqual([], self.registry.shutdown())
        self.assertEqual(before, self.backend.groups)
        self.assertEqual([(101, 11, signal.SIGTERM)], self.backend.delivered)
        self.assertIn(100, self.backend.rows)

    def test_recycled_descendant_pid_is_not_signaled(self):
        launch = self.launch(child=identity(101, 100, 100, generation=11))
        self.retire_synthetic_leader(launch)
        self.backend.rows[101] = identity(101, 1, 888, born=2, command='/unrelated', generation=22)
        self.assertEqual([], self.registry.shutdown())
        self.assertEqual([], self.backend.attempted)
        self.assertEqual(22, self.backend.rows[101].token[7])

    def test_recycled_parent_cannot_authorize_its_new_children(self):
        launch = self.launch(child=identity(101, 100, 100, generation=11))
        self.retire_synthetic_leader(launch)
        self.backend.rows[101] = identity(101, 1, 888, born=2, command='/unrelated', generation=22)
        self.backend.rows[102] = identity(102, 101, 888, born=2, generation=23)
        self.backend.parents.clear()
        self.registry.refresh()
        self.assertNotIn(101, self.backend.parents)
        self.assertFalse(any(record.pid == 102 for record, _ in self.registry.known.values()))
        self.assertEqual([], self.registry.shutdown())
        self.assertEqual([], self.backend.attempted)

    def test_pid_reuse_between_last_check_and_signal_is_rejected_by_token(self):
        child = identity(101, 100, 100, generation=11)
        launch = self.launch(child=child)
        self.retire_synthetic_leader(launch)

        def recycle(record):
            self.backend.rows[record.pid] = identity(record.pid, 1, 888, born=2,
                                                    command='/unrelated', generation=22)

        self.backend.signal_race = recycle
        self.assertEqual([], self.registry.shutdown())
        self.assertEqual(1, len(self.backend.attempted))
        self.assertEqual([], self.backend.delivered)
        self.assertEqual(22, self.backend.rows[101].token[7])

    def test_legitimate_exec_is_re_attested_with_current_token_and_command(self):
        child = identity(101, 100, 100, generation=11)
        launch = self.launch(child=child)
        self.retire_synthetic_leader(launch)
        self.backend.rows[101] = replace(child, parent=1, command='/owned/java',
                                         token=child.token[:-1] + (12,))
        self.assertEqual([], self.registry.shutdown())
        self.assertEqual([(101, 12, signal.SIGTERM)], self.backend.delivered)
        self.assertEqual('/owned/java', self.registry.events[0]['command'])

    def test_unreaped_terminated_leader_allows_final_descendant_capture(self):
        launch = self.launch(alive=False, child=identity(101, 1, 100, generation=11))
        self.registry.stop(launch)
        self.assertEqual([(101, 11, signal.SIGTERM)], self.backend.delivered)
        self.assertTrue(launch.retired)
        self.assertEqual(1, launch.process.waits)

    def test_poll_does_not_reap_or_release_the_group_pin(self):
        launch = self.launch()
        self.assertIsNone(launch.poll())
        self.backend.rows.pop(100)
        self.backend.exits[100] = 7
        self.assertEqual(7, launch.poll())
        self.assertEqual(0, launch.process.waits)
        self.assertFalse(launch.retired)
        self.registry.finish(launch)
        self.assertEqual(7, launch.process.returncode)
        self.assertEqual(1, launch.process.waits)

    def test_unexpected_external_reap_revokes_group_without_querying_reused_pid(self):
        launch = self.launch()
        self.backend.exits.pop(100)
        before = list(self.backend.groups)
        with self.assertRaises(ChildProcessError):
            launch.poll()
        self.assertTrue(launch.retired)
        self.assertEqual(before, self.backend.groups)
        self.assertEqual([], self.backend.attempted)

    def test_denied_token_signal_has_no_numeric_fallback_and_remains_failed(self):
        launch = self.launch()
        self.backend.denied = True
        errors = self.registry.shutdown()
        self.assertTrue(errors)
        self.assertIn('synthetic denied token signal', errors[0]['error'])
        self.assertFalse(launch.retired)
        self.assertIn(100, self.backend.rows)
        self.assertEqual([], self.backend.delivered)
        self.kill.assert_not_called()
        self.killpg.assert_not_called()

    def test_foreground_command_rejects_and_stops_leftover_workers(self):
        launch = self.launch(alive=False, child=identity(101, 1, 100, generation=11))
        with self.assertRaisesRegex(RuntimeError, 'left owned child workers'):
            self.registry.finish(launch)
        self.assertTrue(launch.retired)
        self.assertEqual({}, self.backend.rows)
        self.assertEqual([(101, 11, signal.SIGTERM)], self.backend.delivered)

    def test_failed_group_enumeration_cannot_report_finished_or_reap_worker(self):
        launch = self.launch(alive=False)
        native_backend = DarwinBackend.__new__(DarwinBackend)
        original_errno = ctypes.get_errno()
        self.addCleanup(ctypes.set_errno, original_errno)

        def failed_list(*_args):
            ctypes.set_errno(errno.EPERM)
            return 0  # Exact libproc error convention, not a valid empty group.

        self.backend.group_members = lambda owner: native_backend._list(failed_list, owner)
        with self.assertRaises(OSError) as failure:
            self.registry.finish(launch)
        self.assertEqual(errno.EPERM, failure.exception.errno)
        self.assertFalse(launch.retired)
        self.assertEqual(0, launch.process.waits)
        self.assertEqual([], self.backend.attempted)


class PublicDarwinAbiTest(unittest.TestCase):
    def test_sdk_structure_sizes_match_arm64_header_layout(self):
        self.assertEqual(136, ctypes.sizeof(ProcBsdInfo))
        self.assertEqual(104, ctypes.sizeof(SigInfo))

    def test_list_convenience_wrapper_returns_pid_count_not_bytes(self):
        backend = DarwinBackend.__new__(DarwinBackend)

        def native(_owner, output, _bytes):
            for index, pid in enumerate((101, 102, 103, 104)):
                output[index] = pid
            return 4

        self.assertEqual([101, 102, 103, 104], backend._list(native, 100))

    def test_zero_count_native_errors_are_rejected_by_both_list_wrappers(self):
        backend = DarwinBackend.__new__(DarwinBackend)
        backend.proc = mock.Mock()
        self.addCleanup(ctypes.set_errno, ctypes.get_errno())
        for method, native_name in (('group_members', 'proc_listpgrppids'),
                                    ('children', 'proc_listchildpids')):
            for error_code in (errno.EPERM, errno.EINTR):
                with self.subTest(method=method, error_code=error_code):
                    def native(owner, _output, size):
                        self.assertEqual(100, owner)
                        self.assertEqual((MAX_PROCESSES + 1) * ctypes.sizeof(ctypes.c_int), size)
                        ctypes.set_errno(error_code)
                        return 0

                    setattr(backend.proc, native_name, native)
                    with self.assertRaises(OSError) as failure:
                        getattr(backend, method)(100)
                    self.assertEqual(error_code, failure.exception.errno)

    def test_zero_count_without_errno_is_empty_and_clears_stale_errno(self):
        backend = DarwinBackend.__new__(DarwinBackend)
        backend.proc = mock.Mock()
        self.addCleanup(ctypes.set_errno, ctypes.get_errno())
        for method, native_name in (('group_members', 'proc_listpgrppids'),
                                    ('children', 'proc_listchildpids')):
            with self.subTest(method=method):
                def native(_owner, _output, _size):
                    self.assertEqual(0, ctypes.get_errno())
                    return 0

                ctypes.set_errno(errno.ENOMEM)
                setattr(backend.proc, native_name, native)
                self.assertEqual([], getattr(backend, method)(100))

    def test_negative_process_count_remains_failed(self):
        backend = DarwinBackend.__new__(DarwinBackend)
        self.addCleanup(ctypes.set_errno, ctypes.get_errno())

        def native(*_args):
            ctypes.set_errno(errno.EINVAL)
            return -1

        with self.assertRaises(OSError) as failure:
            backend._list(native, 100)
        self.assertEqual(errno.EINVAL, failure.exception.errno)

    def test_process_list_ceiling_fails_instead_of_truncating(self):
        backend = DarwinBackend.__new__(DarwinBackend)
        with self.assertRaisesRegex(RuntimeError, 'ceiling'):
            backend._list(lambda *_: MAX_PROCESSES + 1, 100)

    def test_wait_status_uses_public_non_reaping_flags(self):
        backend = DarwinBackend.__new__(DarwinBackend)
        backend.lib = mock.Mock()

        def wait(kind, pid, pointer, flags):
            self.assertEqual((1, 100, 1 | 4 | 32), (kind, pid, flags))
            info = ctypes.cast(pointer, ctypes.POINTER(SigInfo)).contents
            info.pid, info.code, info.status = pid, 1, 7
            return 0

        backend.lib.waitid = wait
        self.assertEqual(7, backend.wait_status(100))

    def test_native_signal_passes_complete_token_and_rejects_stale_generation(self):
        backend = DarwinBackend.__new__(DarwinBackend)
        backend.proc = mock.Mock()
        record = identity(100, 1, 100)

        def native(token, signum):
            self.assertEqual(record.token, tuple(token))
            self.assertEqual(signal.SIGTERM, signum)
            return errno.ESRCH

        backend.proc.proc_signal_with_audittoken = native
        self.assertFalse(backend.signal(record, signal.SIGTERM))


if __name__ == '__main__':
    unittest.main()
