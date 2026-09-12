"""Pure synthetic ownership tests: no file/process/native/Gradle operations."""
from dataclasses import replace
from pathlib import PurePosixPath
import stat
import unittest

from apple_fifo_plan import Entry, Process, Refused, attest_pair, plan_cleanup


class AppleFifoPlanTest(unittest.TestCase):
    def setUp(self):
        self.parent = '/private/var/folders/aa/synthetic/T'
        self.host_exe = '/Applications/Xcode.app/Contents/Developer/usr/bin/ibtoold'
        self.agent_exe = '/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/Library/Xcode/Overlays/AssetCatalogSimulatorAgent'
        self.uuid = '613ED9B4-D4E6-4CDA-ACB8-AA81DFFB0280'
        self.root = self.parent + '/ibtoold-67921'
        self.host = Process(67921, 67919, 67921, 'synthetic host generation',
                            self.host_exe + ' --sending-client-environment')
        a = self.root + '/IB/' + self.uuid + '.HostToRemote'
        b = self.root + '/IB/' + self.uuid + '.RemoteToHost'
        self.agent = Process(68144, self.host.pid, self.host.group, 'synthetic child generation',
                             self.agent_exe + ' --hostToRemoteFIFO ' + a + ' --remoteToHostFIFO ' + b)
        self.members = {self.host.pid: self.host, self.agent.pid: self.agent}
        self.pair = self.attest()
        self.entries = {
            path: Entry(path, 42, index + 100, (stat.S_IFDIR if index < 2 else stat.S_IFIFO) | 0o700,
                        501, 101.0, 96 if index < 2 else 0, 2 if index < 2 else 1)
            for index, path in enumerate(self.pair.paths)
        }
        paths = list(reversed(PurePosixPath(self.parent).parents)) + [PurePosixPath(self.parent)]
        self.ancestors = [Entry(str(p), 42, index + 10, stat.S_IFDIR | 0o755,
                               501 if str(p) == self.parent else 0, 0.0, 96, 2)
                          for index, p in enumerate(paths)]

    def attest(self, members=None, current=None, **changes):
        options = dict(temporary_parent=self.parent, ibtoold_executable=self.host_exe,
                       agent_executable=self.agent_exe, cycle_started=100.0, observed_at=102.0, uid=501)
        options.update(changes)
        return attest_pair(self.agent.pid, self.members if members is None else members,
                           self.members if current is None else current, **options)

    def plan(self, **changes):
        options = dict(observed=self.entries, current=self.entries, current_processes={}, open_holders=[],
                       root_children=['IB'], ib_children=[PurePosixPath(self.pair.host_to_remote).name,
                                                         PurePosixPath(self.pair.remote_to_host).name],
                       canonical_ancestors=self.ancestors)
        options.update(changes)
        return plan_cleanup(self.pair, **options)

    def test_exact_owned_pair_produces_only_four_identity_guarded_steps(self):
        steps = self.plan()
        self.assertEqual(['unlink', 'unlink', 'rmdir', 'rmdir'], [s.operation for s in steps])
        self.assertEqual([self.pair.host_to_remote, self.pair.remote_to_host, self.pair.ib, self.pair.root],
                         [s.path for s in steps])
        self.assertTrue(all(s.identity == self.entries[s.path].identity for s in steps))

    def test_only_var_private_var_alias_is_normalized(self):
        aliased = replace(self.agent, command=self.agent.command.replace('/private/var/', '/var/'))
        members = {self.host.pid: self.host, aliased.pid: aliased}
        self.assertEqual(self.root, self.attest(members=members, current=members).root)

    def test_unowned_agent_or_host_is_never_adopted_by_path(self):
        for absent in [self.agent.pid, self.host.pid]:
            with self.subTest(absent=absent):
                members = {k: v for k, v in self.members.items() if k != absent}
                with self.assertRaises(Refused):
                    self.attest(members=members)

    def test_changed_generation_or_command_or_lineage_is_refused(self):
        for pid, item in self.members.items():
            for changed in [replace(item, started='reused PID'), replace(item, command='different')]:
                with self.subTest(pid=pid, changed=changed):
                    current = {**self.members, pid: changed}
                    with self.assertRaises(Refused):
                        self.attest(current=current)
        child = replace(self.agent, group=self.host.group + 1)
        members = {self.host.pid: self.host, child.pid: child}
        with self.assertRaises(Refused):
            self.attest(members=members, current=members)

    def test_command_shape_and_fifo_name_and_root_binding_are_exact(self):
        commands = [
            self.agent.command + ' --hostToRemoteFIFO /elsewhere',
            self.agent.command.replace('--hostToRemoteFIFO', '--other'),
            self.agent.command.replace('ibtoold-67921', 'ibtoold-99999'),
            self.agent.command.replace(self.root, self.parent + '/../../elsewhere/ibtoold-67921'),
            self.agent.command.replace('.RemoteToHost', '.WrongDirection'),
            self.agent.command.replace(self.uuid + '.RemoteToHost', '00000000-0000-0000-0000-000000000000.RemoteToHost'),
            self.agent.command.replace(self.parent, '/another/user/T'),
            self.agent.command.replace(self.agent_exe, '/unowned/AssetCatalogSimulatorAgent'),
        ]
        for command in commands:
            with self.subTest(command=command):
                changed = replace(self.agent, command=command)
                members = {self.host.pid: self.host, changed.pid: changed}
                with self.assertRaises(Refused):
                    self.attest(members=members, current=members)

    def test_unfamiliar_host_options_are_refused_instead_of_guessed(self):
        host = replace(self.host, command=self.host.command + ' --unknown-option')
        members = {host.pid: host, self.agent.pid: self.agent}
        with self.assertRaises(Refused):
            self.attest(members=members, current=members)

    def test_live_or_reused_pid_and_any_holder_block_cleanup(self):
        for item in [self.host, self.agent, replace(self.host, started='another owner')]:
            with self.subTest(item=item):
                with self.assertRaises(Refused):
                    self.plan(current_processes={item.pid: item})
        with self.assertRaises(Refused):
            self.plan(open_holders=[12345])

    def test_changed_inode_mode_or_owner_refuses_removal(self):
        for path, entry in self.entries.items():
            for changed in [replace(entry, inode=entry.inode + 1000), replace(entry, uid=0),
                            replace(entry, mode=stat.S_IFLNK | 0o700),
                            replace(entry, mode=stat.S_IFREG | 0o700)]:
                with self.subTest(path=path, changed=changed):
                    with self.assertRaises(Refused):
                        self.plan(current={**self.entries, path: changed})

    def test_birth_before_cycle_or_after_observation_is_not_owned(self):
        for birth in (99.9, 102.1):
            entries = {p: replace(e, birth=birth) for p, e in self.entries.items()}
            with self.subTest(birth=birth):
                with self.assertRaises(Refused):
                    self.plan(observed=entries, current=entries)

    def test_nonempty_or_multilink_fifo_is_refused(self):
        path = self.pair.host_to_remote
        entry = self.entries[path]
        for changed in [replace(entry, size=42), replace(entry, links=2)]:
            entries = {**self.entries, path: changed}
            with self.subTest(changed=changed):
                with self.assertRaises(Refused):
                    self.plan(observed=entries, current=entries)

    def test_unknown_directory_content_and_incomplete_pair_are_retained(self):
        cases = [dict(root_children=['IB', 'unrelated']), dict(ib_children=['unrelated']),
                 dict(observed={}), dict(current={}), dict(canonical_ancestors=[])]
        for case in cases:
            with self.subTest(case=case):
                with self.assertRaises(Refused):
                    self.plan(**case)

    def test_symlink_ancestor_and_other_user_temp_parent_are_refused(self):
        for index in range(len(self.ancestors)):
            entries = list(self.ancestors)
            entries[index] = replace(entries[index], mode=stat.S_IFLNK | 0o777)
            with self.subTest(index=index):
                with self.assertRaises(Refused):
                    self.plan(canonical_ancestors=entries)
        entries = list(self.ancestors)
        entries[-1] = replace(entries[-1], uid=999)
        with self.assertRaises(Refused):
            self.plan(canonical_ancestors=entries)


if __name__ == '__main__':
    unittest.main()
