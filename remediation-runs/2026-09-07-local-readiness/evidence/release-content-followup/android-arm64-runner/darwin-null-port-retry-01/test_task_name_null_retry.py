"""Synthetic syscall sequences only; never creates native workers or signals."""
import ctypes
import errno
import os
import unittest
from unittest import mock

from darwin_owned_processes import DarwinBackend, ProcBsdInfo


PID = 100
PORT = 77
TOKEN = (0, os.getuid(), 0, os.getuid(), 0, PID, 7, 11)


def basic(born=1):
    row = ProcBsdInfo()
    row.pid, row.ppid, row.pgid = PID, 90, PID
    row.uid, row.status = os.getuid(), 2
    row.start_sec, row.start_usec = 1_000, born
    row.comm = b'owned-worker'
    return row


def return_port(code, value):
    def native(_self_port, _pid, pointer):
        ctypes.cast(pointer, ctypes.POINTER(ctypes.c_uint32)).contents.value = value
        return code
    return native


def return_token(token=TOKEN, count=8, code=0):
    def native(_port, _flavor, output, count_pointer):
        for index, value in enumerate(token):
            output[index] = value
        ctypes.cast(count_pointer, ctypes.POINTER(ctypes.c_uint32)).contents.value = count
        return code
    return native


class NullTaskNamePortTest(unittest.TestCase):
    def setUp(self):
        self.backend = DarwinBackend.__new__(DarwinBackend)
        self.backend.self_port = 41
        self.backend.lib = mock.Mock(spec=('task_name_for_pid', 'task_info', 'mach_port_deallocate'))
        self.backend.proc = mock.Mock(spec=('proc_pidpath',))
        self.backend._basic = mock.Mock(return_value=basic())
        self.backend.lib.task_name_for_pid.side_effect = return_port(0, PORT)
        self.backend.lib.task_info.side_effect = return_token()
        self.backend.lib.mach_port_deallocate.return_value = 0

        def path(_pid, output, _size):
            output.value = b'/owned/worker'
            return len(output.value)

        self.backend.proc.proc_pidpath.side_effect = path
        self.sleep = mock.patch('darwin_owned_processes.time.sleep').start()
        self.kill = mock.patch('os.kill', side_effect=AssertionError('numeric PID signal forbidden')).start()
        self.killpg = mock.patch('os.killpg', side_effect=AssertionError('numeric group signal forbidden')).start()
        self.addCleanup(mock.patch.stopall)

    def port_sequence(self, sequence):
        replies = iter(sequence)

        def native(*args):
            return return_port(*next(replies))(*args)

        self.backend.lib.task_name_for_pid.side_effect = native

    def assert_no_port_use(self):
        self.backend.lib.task_info.assert_not_called()
        self.backend.lib.mach_port_deallocate.assert_not_called()
        self.kill.assert_not_called()
        self.killpg.assert_not_called()

    def test_successful_null_port_then_valid_token_is_retried_not_denied(self):
        self.port_sequence([(0, 0), (0, PORT)])
        self.assertEqual(TOKEN, self.backend._token(PID))
        self.assertEqual(2, self.backend.lib.task_name_for_pid.call_count)
        self.backend._basic.assert_called_once_with(PID)
        self.sleep.assert_called_once_with(0.01)
        self.backend.lib.task_info.assert_called_once()
        self.assertEqual((PORT, 15), self.backend.lib.task_info.call_args.args[:2])
        self.backend.lib.mach_port_deallocate.assert_called_once_with(41, PORT)

    def test_null_port_then_independently_confirmed_exit_returns_absent(self):
        self.port_sequence([(0, 0), (0, 0)])
        self.backend._basic.side_effect = [basic(), None]
        self.assertIsNone(self.backend._token(PID))
        self.assertEqual(2, self.backend.lib.task_name_for_pid.call_count)
        self.assertEqual(2, self.backend._basic.call_count)
        self.sleep.assert_called_once_with(0.01)
        self.assert_no_port_use()

    def test_null_port_with_immediately_confirmed_exit_needs_no_retry(self):
        self.port_sequence([(0, 0)])
        self.backend._basic.return_value = None
        self.assertIsNone(self.backend._token(PID))
        self.backend.lib.task_name_for_pid.assert_called_once()
        self.sleep.assert_not_called()
        self.assert_no_port_use()

    def test_persistent_null_live_candidate_fails_after_exact_bound(self):
        self.backend.lib.task_name_for_pid.side_effect = return_port(0, 0)
        with self.assertRaisesRegex(RuntimeError, 'Persistent null task-name port.*no signal authorized'):
            self.backend._token(PID)
        self.assertEqual(3, self.backend.lib.task_name_for_pid.call_count)
        self.assertEqual(3, self.backend._basic.call_count)
        self.assertEqual([mock.call(0.01), mock.call(0.01)], self.sleep.call_args_list)
        self.assert_no_port_use()

    def test_genuine_native_denial_for_live_candidate_is_not_retried(self):
        self.port_sequence([(5, 0)])
        with self.assertRaisesRegex(RuntimeError, 'denied.*100: 5'):
            self.backend._token(PID)
        self.backend.lib.task_name_for_pid.assert_called_once()
        self.sleep.assert_not_called()
        self.assert_no_port_use()

    def test_native_denial_after_confirmed_exit_retains_absence_semantics(self):
        self.port_sequence([(5, 0)])
        self.backend._basic.return_value = None
        self.assertIsNone(self.backend._token(PID))
        self.backend.lib.task_name_for_pid.assert_called_once()
        self.sleep.assert_not_called()
        self.assert_no_port_use()

    def test_null_then_genuine_denial_fails_without_consuming_more_retries(self):
        self.port_sequence([(0, 0), (5, 0)])
        with self.assertRaisesRegex(RuntimeError, 'denied.*100: 5'):
            self.backend._token(PID)
        self.assertEqual(2, self.backend.lib.task_name_for_pid.call_count)
        self.sleep.assert_called_once_with(0.01)
        self.assert_no_port_use()

    def test_bsd_inspection_failure_is_not_misclassified_as_absence(self):
        self.port_sequence([(0, 0)])
        self.backend._basic.side_effect = OSError(errno.EPERM, 'synthetic BSD denial')
        with self.assertRaises(OSError) as error:
            self.backend._token(PID)
        self.assertEqual(errno.EPERM, error.exception.errno)
        self.backend.lib.task_name_for_pid.assert_called_once()
        self.sleep.assert_not_called()
        self.assert_no_port_use()

    def test_non_null_port_without_race_has_no_delay_and_is_released(self):
        self.assertEqual(TOKEN, self.backend._token(PID))
        self.backend._basic.assert_not_called()
        self.backend.lib.task_name_for_pid.assert_called_once()
        self.sleep.assert_not_called()
        self.backend.lib.mach_port_deallocate.assert_called_once_with(41, PORT)

    def test_task_info_failure_for_live_candidate_is_not_retried_and_releases_port(self):
        self.backend.lib.task_info.side_effect = return_token(code=5)
        with self.assertRaisesRegex(RuntimeError, 'TASK_AUDIT_TOKEN failed.*100: 5'):
            self.backend._token(PID)
        self.backend.lib.task_name_for_pid.assert_called_once()
        self.backend.lib.task_info.assert_called_once()
        self.sleep.assert_not_called()
        self.backend.lib.mach_port_deallocate.assert_called_once_with(41, PORT)

    def test_task_info_failure_after_confirmed_exit_releases_port(self):
        self.backend.lib.task_info.side_effect = return_token(code=5)
        self.backend._basic.return_value = None
        self.assertIsNone(self.backend._token(PID))
        self.backend.lib.mach_port_deallocate.assert_called_once_with(41, PORT)
        self.sleep.assert_not_called()

    def test_malformed_token_count_still_fails_and_releases_port(self):
        self.backend.lib.task_info.side_effect = return_token(count=7)
        with self.assertRaisesRegex(RuntimeError, 'Malformed kernel audit token'):
            self.backend._token(PID)
        self.backend.lib.mach_port_deallocate.assert_called_once_with(41, PORT)
        self.sleep.assert_not_called()

    def test_wrong_token_pid_still_fails_and_releases_port(self):
        self.backend.lib.task_info.side_effect = return_token(TOKEN[:5] + (PID + 1,) + TOKEN[6:])
        with self.assertRaisesRegex(RuntimeError, 'Malformed kernel audit token'):
            self.backend._token(PID)
        self.backend.lib.mach_port_deallocate.assert_called_once_with(41, PORT)
        self.sleep.assert_not_called()

    def test_failed_port_release_is_not_hidden_by_successful_token(self):
        self.backend.lib.mach_port_deallocate.return_value = 5
        with self.assertRaisesRegex(RuntimeError, 'deallocation failed'):
            self.backend._token(PID)
        self.backend.lib.mach_port_deallocate.assert_called_once_with(41, PORT)

    def test_keyboard_interrupt_during_null_retry_propagates_without_port_use(self):
        self.port_sequence([(0, 0)])
        self.sleep.side_effect = KeyboardInterrupt('synthetic cancellation')
        with self.assertRaisesRegex(KeyboardInterrupt, 'synthetic cancellation'):
            self.backend._token(PID)
        self.backend.lib.task_name_for_pid.assert_called_once()
        self.assert_no_port_use()

    def test_interrupted_wait_during_null_retry_propagates_without_port_use(self):
        self.port_sequence([(0, 0)])
        self.sleep.side_effect = InterruptedError('synthetic interrupted wait')
        with self.assertRaisesRegex(InterruptedError, 'synthetic interrupted wait'):
            self.backend._token(PID)
        self.backend.lib.task_name_for_pid.assert_called_once()
        self.assert_no_port_use()

    def test_keyboard_interrupt_during_token_read_still_releases_owned_port(self):
        self.backend.lib.task_info.side_effect = KeyboardInterrupt('synthetic cancellation')
        with self.assertRaisesRegex(KeyboardInterrupt, 'synthetic cancellation'):
            self.backend._token(PID)
        self.backend.lib.mach_port_deallocate.assert_called_once_with(41, PORT)

    def test_read_restarts_if_null_retry_crosses_bsd_process_lifetimes(self):
        self.port_sequence([(0, 0)] + [(0, PORT)] * 4)
        self.backend._basic.side_effect = [basic(1), basic(1), basic(2), basic(2), basic(2)]
        result = self.backend.read(PID)
        self.assertEqual((1_000, 2), result.started)
        self.assertEqual(TOKEN, result.token)
        self.assertEqual('/owned/worker', result.command)
        self.assertEqual(5, self.backend._basic.call_count)
        self.assertEqual(5, self.backend.lib.task_name_for_pid.call_count)
        self.assertEqual(4, self.backend.lib.task_info.call_count)
        self.assertEqual(4, self.backend.lib.mach_port_deallocate.call_count)
        self.assertEqual(2, self.backend.proc.proc_pidpath.call_count)
        self.sleep.assert_called_once_with(0.01)

    def test_read_still_rejects_unstable_tokens_after_null_retry(self):
        self.port_sequence([(0, 0)] + [(0, PORT)] * 6)
        tokens = iter([TOKEN[:-1] + (generation,) for generation in range(11, 17)])

        def changing_token(*args):
            return return_token(next(tokens))(*args)

        self.backend.lib.task_info.side_effect = changing_token
        with self.assertRaisesRegex(RuntimeError, 'Unstable process identity.*no signal authorized'):
            self.backend.read(PID)
        self.assertEqual(7, self.backend.lib.task_name_for_pid.call_count)
        self.assertEqual(6, self.backend.lib.task_info.call_count)
        self.assertEqual(6, self.backend.lib.mach_port_deallocate.call_count)
        self.assertEqual(3, self.backend.proc.proc_pidpath.call_count)
        self.sleep.assert_called_once_with(0.01)

    def test_read_never_uses_path_after_null_retry_independently_confirms_exit(self):
        self.port_sequence([(0, 0), (0, 0)])
        self.backend._basic.side_effect = [basic(), basic(), None]
        self.assertIsNone(self.backend.read(PID))
        self.backend.proc.proc_pidpath.assert_not_called()
        self.assertEqual(2, self.backend.lib.task_name_for_pid.call_count)
        self.assert_no_port_use()


if __name__ == '__main__':
    unittest.main()
