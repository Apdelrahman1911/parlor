"""Synthetic owned TemporaryDirectory fixtures only. Root executes; no native worker/control access."""
import importlib.util
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

PATH = Path(__file__).with_name('native09_secondary_fifo_recovery_03.py')
SPEC = importlib.util.spec_from_file_location('native09_recovery', PATH)
recovery = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(recovery)


class ExactRecoveryTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='parlor-native09-recovery-control-')
        self.addCleanup(self.temporary.cleanup)
        self.base = Path(self.temporary.name).resolve()
        self.root = self.base / 'ibtoold-1234'
        self.directory = self.root / 'IB'
        self.directory.mkdir(parents=True)
        self.paths = [self.directory / name for name in recovery.NAMES]
        for path in self.paths:
            os.mkfifo(path)
        selected = list(reversed(self.directory.parents)) + [self.directory] + self.paths
        self.expected = {path: recovery.identity(path.lstat()) for path in selected}
        self.events = []
        self.live_pid = False
        self.holder = False
        self.engine = recovery.ExactRecovery(self.root, recovery.NAMES, self.expected,
            lambda: recovery.require(not self.live_pid, 'live or reused PID'),
            lambda root: recovery.require(root == self.root and not self.holder, 'holder'), self.events.append)

    def blocked(self):
        with self.assertRaises((RuntimeError, OSError)):
            self.engine.run(True)
        self.assertFalse(any(item['stage'].startswith(('unlinked', 'removed')) for item in self.events))

    def test_verification_does_not_delete(self):
        self.assertEqual('VERIFIED_NO_DELETION', self.engine.run()['status'])
        self.assertTrue(all(path.exists() for path in self.paths))

    def test_exact_positive_removes_four_objects_and_preserves_sibling(self):
        unrelated = self.base / 'unrelated.txt'
        unrelated.write_text('synthetic user work')
        result = self.engine.run(True)
        self.assertEqual('RECOVERED_CLEANUP_ONLY', result['status'])
        self.assertEqual(4, len(result['removed']))
        self.assertEqual(5, len(self.events))
        self.assertFalse(self.root.exists())
        self.assertEqual('synthetic user work', unrelated.read_text())

    def test_replaced_leaf_inode_is_preserved(self):
        self.paths[0].rename(self.base / 'original-held-fifo')
        os.mkfifo(self.paths[0])
        self.blocked()
        self.assertTrue(self.paths[0].exists())

    def test_replaced_parent_inode_is_preserved(self):
        self.root.rename(self.base / 'original-held-root')
        self.directory.mkdir(parents=True)
        for path in self.paths:
            os.mkfifo(path)
        self.blocked()

    def test_symlink_leaf_is_not_followed(self):
        self.paths[0].unlink()
        self.paths[0].symlink_to(self.paths[1])
        self.blocked()
        self.assertTrue(self.paths[0].is_symlink())

    def test_symlink_parent_is_not_followed(self):
        self.directory.rename(self.root / 'original-IB')
        self.directory.symlink_to(self.root / 'original-IB')
        self.blocked()
        self.assertTrue(self.directory.is_symlink())

    def test_non_fifo_is_preserved(self):
        self.paths[0].unlink()
        self.paths[0].write_text('not a fifo')
        self.blocked()
        self.assertEqual('not a fifo', self.paths[0].read_text())

    def test_uid_and_birth_mismatch_are_rejected(self):
        for key in ('uid', 'birth_at'):
            original = self.expected[self.paths[0]][key]
            self.expected[self.paths[0]][key] = original + 1
            self.blocked()
            self.expected[self.paths[0]][key] = original

    def test_extra_hard_link_is_rejected(self):
        os.link(self.paths[0], self.base / 'additional-link')
        self.blocked()

    def test_unknown_contents_preserve_all(self):
        for directory in (self.root, self.directory):
            path = directory / 'unrelated.txt'
            path.write_text('synthetic user work')
            self.blocked()
            self.assertEqual('synthetic user work', path.read_text())
            path.unlink()

    def test_changed_nonce_is_not_adopted(self):
        self.paths[0].rename(self.paths[0].with_name('unknown.RemoteToHost'))
        self.blocked()

    def test_live_or_reused_pid_blocks_all_mutation(self):
        self.live_pid = True
        self.blocked()

    def test_holder_or_query_failure_blocks_all_mutation(self):
        self.holder = True
        self.blocked()
        def unavailable(root):
            raise subprocess.TimeoutExpired('synthetic lsof', 15)
        self.engine.holder_guard = unavailable
        with self.assertRaises(subprocess.TimeoutExpired):
            self.engine.run(True)
        self.assertTrue(all(path.exists() for path in self.paths))

    def test_new_unknown_content_during_query_blocks_before_first_unlink(self):
        def concurrent(root):
            (self.directory / 'unrelated.txt').write_text('synthetic user work')
        self.engine.holder_guard = concurrent
        self.blocked()
        self.assertTrue(all(path.exists() for path in self.paths))

    def test_partial_failure_keeps_remaining_objects_and_progress_receipt(self):
        def interrupted(event):
            self.events.append(event)
            if event['stage'] == 'unlinked-exact-fifo':
                raise KeyboardInterrupt('synthetic interruption')
        self.engine.event = interrupted
        with self.assertRaises(KeyboardInterrupt):
            self.engine.run(True)
        self.assertFalse(self.paths[0].exists())
        self.assertTrue(self.paths[1].exists())
        self.assertTrue(self.root.exists())
        self.assertEqual('unlinked-exact-fifo', self.events[-1]['stage'])
        with self.assertRaises(RuntimeError):
            self.engine.run(True)  # No automatic retry/adoption after partial cleanup.


class QueryControlTests(unittest.TestCase):
    def test_query_requires_exact_empty_exit_and_bounded_integer_output(self):
        for code, out, err, valid in ((1,b'',b'',True),(0,b'123\n',b'',True),
            (0,b'',b'',False),(1,b'123\n',b'',False),(2,b'',b'',False),
            (1,b'',b'error',False),(0,b'not-a-pid',b'',False),(0,b'1\n'*20000,b'',False)):
            result = subprocess.CompletedProcess(['synthetic'], code, out, err)
            with patch.object(recovery.subprocess, 'run', return_value=result):
                if valid:
                    self.assertEqual(set() if code else {123}, recovery.query(['synthetic']))
                else:
                    with self.assertRaises(RuntimeError): recovery.query(['synthetic'])

    def test_pid_check_never_accepts_reused_id(self):
        with patch.object(recovery, 'query', return_value={41785}):
            with self.assertRaises(RuntimeError): recovery.check_pids()
        with patch.object(recovery, 'query', return_value=set()): recovery.check_pids()

    def test_holder_exemption_is_only_this_helpers_directory_fds(self):
        with patch.object(recovery, 'query', return_value={os.getpid()}): recovery.check_holders(recovery.ROOT)
        with patch.object(recovery, 'query', return_value={os.getpid(), 99999}):
            with self.assertRaises(RuntimeError): recovery.check_holders(recovery.ROOT)

    def test_exact_lsof_partial_selection_keeps_all_reported_pids(self):
        arguments = ['/usr/sbin/lsof', '-nP', '-t', '+w', '+D', str(recovery.ROOT)]
        result = subprocess.CompletedProcess(arguments, 1, b'123\n456\n', b'')
        with patch.object(recovery.subprocess, 'run', return_value=result):
            self.assertEqual({123, 456}, recovery.query(arguments))

    def test_lsof_partial_selection_with_only_helpers_dirfds_is_accepted(self):
        result = subprocess.CompletedProcess(['synthetic'], 1, (str(os.getpid())+'\n').encode(), b'')
        with patch.object(recovery.subprocess, 'run', return_value=result):
            recovery.check_holders(recovery.ROOT)

    def test_lsof_partial_selection_cannot_hide_another_holder(self):
        other = os.getpid() + 1
        result = subprocess.CompletedProcess(['synthetic'], 1, (str(os.getpid())+'\n'+str(other)+'\n').encode(), b'')
        with patch.object(recovery.subprocess, 'run', return_value=result):
            with self.assertRaisesRegex(RuntimeError, 'other holders'): recovery.check_holders(recovery.ROOT)

    def test_ps_and_other_lsof_targets_keep_strict_exit_output_contract(self):
        commands = [['/bin/ps', '-p', '41785,41768', '-o', 'pid='],
            ['/usr/sbin/lsof', '-nP', '-t', '+w', '+D', '/unrelated-task'], ['synthetic']]
        for command in commands:
            with patch.object(recovery.subprocess, 'run', return_value=subprocess.CompletedProcess(command, 1, b'123\n', b'')):
                with self.assertRaisesRegex(RuntimeError, 'Ambiguous ownership query'): recovery.query(command)

    def test_exact_lsof_still_rejects_errors_stderr_malformed_or_oversized_output(self):
        arguments = ['/usr/sbin/lsof', '-nP', '-t', '+w', '+D', str(recovery.ROOT)]
        for code, out, err in ((2,b'123\n',b''),(-1,b'',b''),(1,b'123\n',b'error'),
                              (1,b'not-a-pid',b''),(1,b'1\n'*20000,b''),(0,b'',b'')):
            with patch.object(recovery.subprocess, 'run', return_value=subprocess.CompletedProcess(arguments, code, out, err)):
                with self.assertRaises(RuntimeError): recovery.query(arguments)

    def test_holder_query_constructs_exact_warning_enabled_command(self):
        arguments = ['/usr/sbin/lsof', '-nP', '-t', '+w', '+D', str(recovery.ROOT)]
        result = subprocess.CompletedProcess(arguments, 1, (str(os.getpid()) + '\n').encode(), b'')
        with patch.object(recovery.subprocess, 'run', return_value=result) as run:
            recovery.check_holders(recovery.ROOT)
        run.assert_called_once_with(arguments, capture_output=True, timeout=15, check=False)

    def test_relaxed_lsof_contract_rejects_suppressed_missing_or_misordered_warning_flags(self):
        # -t itself enables warning suppression. Only later +w reverses it.
        prefix = ['/usr/sbin/lsof', '-nP']
        suffix = ['+D', str(recovery.ROOT)]
        for flags in (['-w', '-t'], ['-t'], ['+w', '-t'], ['-t', '+w', '-w'], ['-w', '-t', '+w']):
            arguments = prefix + flags + suffix
            result = subprocess.CompletedProcess(arguments, 1, (str(os.getpid()) + '\n').encode(), b'')
            with patch.object(recovery.subprocess, 'run', return_value=result):
                with self.assertRaisesRegex(RuntimeError, 'Ambiguous ownership query'):
                    recovery.query(arguments)

    def test_warning_stderr_is_fatal_regardless_of_exit_or_reported_pids(self):
        arguments = ['/usr/sbin/lsof', '-nP', '-t', '+w', '+D', str(recovery.ROOT)]
        for code in (0, 1):
            for output in (b'', (str(os.getpid()) + '\n').encode(), b'123\n456\n'):
                result = subprocess.CompletedProcess(arguments, code, output,
                    b'lsof: WARNING: cannot inspect a synthetic selected object\n')
                with patch.object(recovery.subprocess, 'run', return_value=result):
                    with self.assertRaisesRegex(RuntimeError, 'Ownership query failed'):
                        recovery.query(arguments)

    def test_plan_hash_failure_never_reads_or_mutates_recovery_paths(self):
        with patch.object(recovery, 'checked_bytes', return_value=b'{}'):
            with self.assertRaisesRegex(RuntimeError, 'Independent plan changed'): recovery.bound_plan()


if __name__ == '__main__':
    unittest.main()
