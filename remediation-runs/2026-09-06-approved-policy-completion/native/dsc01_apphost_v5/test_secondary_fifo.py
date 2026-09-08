"""Lightweight synthetic fixtures; root executes after independent review.

No simctl, Gradle, Xcode, preference access or process termination is invoked.
Every FIFO/file is under unittest TemporaryDirectory; symlink target is synthetic.
"""
import os
from pathlib import Path
import tempfile
import time
import unittest
from unittest.mock import patch
import secondary_fifo

from secondary_fifo import SecondaryFifoLedger, UnsafeSecondaryPath


class SecondaryFifoSafetyTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='parlor-dsc01-fifo-test-')
        self.addCleanup(self.temp.cleanup)
        self.prefix = Path(self.temp.name).resolve()
        self.started = time.time() - 1
        self.directory = self.prefix / 'ab/cdef/T/ibtoold-1234/IB'
        self.directory.mkdir(parents=True)
        self.paths = [self.directory / ('613ED9B4-D4E6-4CDA-ACB8-AA81DFFB0280.' + suffix)
                      for suffix in ('HostToRemote', 'RemoteToHost')]
        for path in self.paths:
            os.mkfifo(path)
        self.parent = dict(pid=1234, ppid=100, pgid=1234, start='owned-parent', command='/Xcode.app/Contents/Developer/usr/bin/ibtoold')
        self.child = dict(pid=1235, ppid=1234, pgid=1234, start='owned-child',
                          command='/Xcode.app/Contents/Developer/Agent --hostToRemoteFIFO ' + str(self.paths[0]) +
                          ' --remoteToHostFIFO ' + str(self.paths[1]))
        self.current = {1234:self.parent, 1235:self.child}
        self.members = dict(self.current)
        self.receipts = []
        self.holders = set()
        self.ledger = SecondaryFifoLedger(self.started, self.receipts.append, lambda pid:os.getuid(),
                                          lambda:self.current, lambda args:self.holders, allowed_prefix=self.prefix)

    def attest(self):
        self.ledger.observe(self.child, self.members, self.current)

    def stop_fixture_processes(self):
        # These are dictionaries, not real PIDs. No signals are sent.
        self.current = {}

    def test_attested_pair_removed_only_after_workers_stop(self):
        self.attest()
        self.assertEqual(2, len(self.ledger.records))
        with self.assertRaises(UnsafeSecondaryPath): self.ledger.cleanup()
        self.stop_fixture_processes()
        self.assertEqual('PASS', self.ledger.cleanup()['status'])
        self.assertFalse(self.directory.parent.exists())
        self.assertTrue(self.prefix.exists())

    def test_unowned_child_cannot_attest_from_name_alone(self):
        self.members.pop(1235)
        with self.assertRaises(UnsafeSecondaryPath): self.attest()
        self.assertTrue(self.paths[0].exists())

    def test_pid_reuse_during_observation_rejected(self):
        self.current = {1234:self.parent, 1235:{**self.child,'start':'different'}}
        with self.assertRaises(UnsafeSecondaryPath): self.attest()

    def test_pid_reuse_during_cleanup_preserves_files(self):
        self.attest(); self.current = {1235:{**self.child,'start':'reused'}}
        with self.assertRaises(UnsafeSecondaryPath): self.ledger.cleanup()
        self.assertTrue(self.paths[0].exists())

    def test_stale_preexisting_fifo_is_not_owned(self):
        self.ledger.started = time.time() + 2
        with self.assertRaises(UnsafeSecondaryPath): self.attest()

    def test_process_uid_mismatch_is_not_owned(self):
        self.ledger.uid_of = lambda pid:os.getuid()+1
        with self.assertRaises(UnsafeSecondaryPath): self.attest()

    def test_inode_uid_mismatch_is_not_owned(self):
        original = secondary_fifo.metadata
        def other_inode_owner(path):
            value = original(path)
            return {**value, 'uid':os.getuid()+1} if path == self.paths[0] else value
        with patch('secondary_fifo.metadata', side_effect=other_inode_owner):
            with self.assertRaises(UnsafeSecondaryPath): self.attest()
        self.assertTrue(self.paths[0].exists())

    def test_preexisting_parent_birthtime_rejects_new_fifo(self):
        original = secondary_fifo.metadata
        def older_parent(path):
            value = original(path)
            return {**value, 'birth_at':self.started-60} if path == self.directory.parent else value
        with patch('secondary_fifo.metadata', side_effect=older_parent):
            with self.assertRaises(UnsafeSecondaryPath): self.attest()
        self.assertTrue(self.paths[0].exists())

    def test_holder_query_error_never_becomes_empty_holder_success(self):
        self.attest(); self.stop_fixture_processes()
        def unavailable(_arguments):
            raise RuntimeError('synthetic lsof permission failure')
        self.ledger.holders = unavailable
        with self.assertRaises(RuntimeError): self.ledger.cleanup()
        self.assertTrue(self.paths[0].exists()); self.assertTrue(self.paths[1].exists())

    def test_symlink_fifo_not_followed_or_deleted(self):
        self.paths[0].unlink()
        self.paths[0].symlink_to(self.paths[1])
        with self.assertRaises(UnsafeSecondaryPath): self.attest()
        self.assertTrue(self.paths[0].is_symlink())

    def test_symlink_parent_not_followed(self):
        original = self.directory.parent / 'actual'
        self.directory.rename(original); self.directory.symlink_to(original, target_is_directory=True)
        with self.assertRaises(UnsafeSecondaryPath): self.attest()

    def test_replaced_inode_rejected(self):
        self.attest()
        old = self.paths[0].with_suffix('.held'); self.paths[0].rename(old)
        os.mkfifo(self.paths[0]); self.stop_fixture_processes()
        with self.assertRaises(UnsafeSecondaryPath): self.ledger.cleanup()
        self.assertTrue(old.exists()); self.assertTrue(self.paths[0].exists())

    def test_unknown_directory_contents_preserve_all(self):
        self.attest(); (self.directory/'other-task.txt').write_text('synthetic unrelated fixture')
        self.stop_fixture_processes()
        with self.assertRaises(UnsafeSecondaryPath): self.ledger.cleanup()
        self.assertTrue(self.paths[0].exists())

    def test_unknown_root_contents_preserve_all(self):
        self.attest(); (self.directory.parent/'other-task.txt').write_text('synthetic')
        self.stop_fixture_processes()
        with self.assertRaises(UnsafeSecondaryPath): self.ledger.cleanup()
        self.assertTrue(self.paths[0].exists())

    def test_file_holder_prevents_any_unlink(self):
        self.attest(); self.stop_fixture_processes(); self.holders={99}
        with self.assertRaises(UnsafeSecondaryPath): self.ledger.cleanup()
        self.assertTrue(self.paths[0].exists())

    def test_timeout_or_cancellation_cleanup_uses_same_attestation(self):
        for error in (TimeoutError, KeyboardInterrupt):
            # Restore only this synthetic pair between the two finalizer paths.
            if not self.directory.exists():
                self.directory.mkdir(parents=True)
                for path in self.paths: os.mkfifo(path)
                self.ledger.records.clear(); self.ledger.pending.clear()
            self.current = dict(self.members)
            self.attest()
            try:
                raise error('synthetic interruption')
            except BaseException:
                self.stop_fixture_processes()
                self.assertEqual('PASS', self.ledger.cleanup()['status'])

    def test_missing_unattested_then_late_created_fifo_blocks_cleanup(self):
        self.paths[0].unlink(); self.attest(); os.mkfifo(self.paths[0])
        self.stop_fixture_processes()
        with self.assertRaises(UnsafeSecondaryPath): self.ledger.cleanup()

    def test_unknown_external_argument_is_rejected(self):
        self.child['command'] = self.child['command'].replace(str(self.prefix), '/unrelated-task')
        with self.assertRaises(UnsafeSecondaryPath): self.attest()

    def test_missing_or_duplicate_flags_rejected(self):
        self.child['command'] += ' --hostToRemoteFIFO '+str(self.paths[0])
        with self.assertRaises(UnsafeSecondaryPath): self.attest()

    def fail_uid_query(self, _pid):
        raise RuntimeError('synthetic worker exited before UID query')

    def test_fully_attested_same_identity_and_pair_avoids_redundant_uid_query(self):
        self.attest()
        original = self.ledger.dump()
        self.ledger.uid_of = self.fail_uid_query
        self.attest()
        self.assertEqual(original, self.ledger.dump())
        self.stop_fixture_processes()
        self.assertEqual('PASS', self.ledger.cleanup()['status'])

    def test_initial_uid_query_failure_never_attests_or_deletes_paths(self):
        self.ledger.uid_of = self.fail_uid_query
        with self.assertRaises(RuntimeError): self.attest()
        self.assertFalse(self.ledger.records)
        self.assertTrue(all(path.exists() for path in self.paths))

    def test_partial_record_or_pending_claim_cannot_use_cached_uid(self):
        for mode in ('missing-record', 'pending'):
            with self.subTest(mode=mode):
                self.ledger.records.clear(); self.ledger.pending.clear()
                self.ledger.uid_of = lambda pid: os.getuid()
                self.attest()
                if mode == 'missing-record':
                    self.ledger.records.pop(str(self.paths[0]))
                else:
                    self.ledger.pending[str(self.paths[0])] = {'path': str(self.paths[0])}
                self.ledger.uid_of = self.fail_uid_query
                with self.assertRaises(RuntimeError): self.attest()

    def test_current_new_pair_cannot_hide_behind_members_old_argv(self):
        self.attest()
        old = dict(self.child)
        self.members[1235] = old
        new_command = self.child['command'].replace('613ED9B4-D4E6-4CDA-ACB8-AA81DFFB0280',
                                                    'CEB01D2A-3F45-4321-A0BC-123456789ABC')
        self.current[1235] = {**old, 'command': new_command}
        self.ledger.uid_of = self.fail_uid_query
        with self.assertRaises(RuntimeError):
            self.ledger.observe(old, self.members, self.current)
        self.assertEqual({str(path) for path in self.paths}, set(self.ledger.records))

    def test_changed_child_or_parent_start_cannot_use_attested_pair(self):
        self.attest()
        self.ledger.uid_of = self.fail_uid_query
        for pid in (1234, 1235):
            with self.subTest(pid=pid):
                original = self.current[pid]
                self.current[pid] = {**original, 'start': 'new-start'}
                with self.assertRaises(UnsafeSecondaryPath): self.attest()
                self.current[pid] = original

    def test_cached_pair_still_rechecks_changed_inode(self):
        self.attest()
        self.ledger.uid_of = self.fail_uid_query
        held = self.paths[0].with_suffix('.held')
        self.paths[0].rename(held); os.mkfifo(self.paths[0])
        with self.assertRaises(UnsafeSecondaryPath): self.attest()
        self.assertTrue(held.exists()); self.assertTrue(self.paths[0].exists())

    def test_cached_pair_still_rechecks_inode_uid_and_symlink(self):
        self.attest()
        self.ledger.uid_of = self.fail_uid_query
        original = secondary_fifo.metadata
        def wrong_uid(path):
            value = original(path)
            return {**value, 'uid': os.getuid() + 1} if path == self.paths[0] else value
        with patch('secondary_fifo.metadata', side_effect=wrong_uid):
            with self.assertRaises(UnsafeSecondaryPath): self.attest()
        self.paths[0].unlink(); self.paths[0].symlink_to(self.paths[1])
        with self.assertRaises(UnsafeSecondaryPath): self.attest()
        self.assertTrue(self.paths[0].is_symlink())

    def test_new_pair_argv_change_during_uid_attestation_is_rejected(self):
        after = {**self.current, 1235: {**self.child, 'command': self.child['command'] + ' --changed'}}
        self.ledger.current_processes = lambda: after
        with self.assertRaises(UnsafeSecondaryPath): self.attest()
        self.assertFalse(self.ledger.records)


if __name__ == '__main__':
    unittest.main()
