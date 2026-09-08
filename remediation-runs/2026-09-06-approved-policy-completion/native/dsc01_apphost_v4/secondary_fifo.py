"""Exact Apple worker FIFO ownership; never scan/delete arbitrary /var/folders.

Caller supplies already PID/start/ancestry-attested task processes. Directory names
or open files alone NEVER authorize process ownership/termination. This module
only unlinks individually attested FIFOs then rmdirs empty attested directories.
"""
import os
from pathlib import Path
import re
import shlex
import stat
import time


class UnsafeSecondaryPath(RuntimeError):
    pass


def metadata(path):
    value = path.lstat()
    if stat.S_ISLNK(value.st_mode):
        raise UnsafeSecondaryPath('Secondary path is a symlink')
    birth = getattr(value, 'st_birthtime', None)
    if birth is None:
        raise UnsafeSecondaryPath('Filesystem cannot attest creation time')
    return dict(device=value.st_dev, inode=value.st_ino, uid=value.st_uid,
                mode=value.st_mode, birth_at=birth)


def no_symlink_components(path):
    current = Path(path.anchor)
    for part in path.parts[1:]:
        current /= part
        if current.is_symlink():
            raise UnsafeSecondaryPath('Secondary path traverses a symlink')


class SecondaryFifoLedger:
    def __init__(self, cycle_started, persist, uid_of, current_processes, holders,
                 allowed_prefix=Path('/private/var/folders'), owned_primary=None):
        self.started = cycle_started
        self.persist = persist
        self.uid_of = uid_of
        self.current_processes = current_processes
        self.holders = holders
        self.prefix = allowed_prefix
        self.primary = owned_primary
        self.records = {}
        self.pending = {}
        self.removed = []

    def dump(self):
        return dict(schema_version=1, cycle_started=self.started,
                    records=list(self.records.values()), pending=list(self.pending.values()),
                    removed=list(self.removed))

    def canonical_argument(self, raw):
        # macOS's system-owned /var alias is the only normalization allowed.
        if raw.startswith('/var/folders/'):
            raw = '/private' + raw
        path = Path(raw)
        if not path.is_absolute() or str(path) != raw or '..' in path.parts:
            raise UnsafeSecondaryPath('Noncanonical secondary FIFO argument')
        if self.primary is not None and self.primary in path.parents:
            return None  # Already covered by exact primary-task-root cleanup.
        try:
            parts = path.relative_to(self.prefix).parts
        except ValueError as error:
            raise UnsafeSecondaryPath('External FIFO outside reviewed Apple temp prefix') from error
        if (len(parts) != 6 or not re.fullmatch('[a-zA-Z0-9_]+', parts[0]) or
                not re.fullmatch('[a-zA-Z0-9_]+', parts[1]) or parts[2] != 'T' or
                not re.fullmatch('ibtoold-[0-9]+', parts[3]) or parts[4] != 'IB' or
                not re.fullmatch(r'[A-Fa-f0-9-]{36}\.(?:HostToRemote|RemoteToHost)', parts[5])):
            raise UnsafeSecondaryPath('Unexpected secondary FIFO shape')
        no_symlink_components(path)
        return path

    def observe(self, process, members, current):
        args = shlex.split(process['command'])
        flags = ['--hostToRemoteFIFO', '--remoteToHostFIFO']
        if not any(flag in args for flag in flags):
            return
        if any(args.count(flag) != 1 or args.index(flag) + 1 >= len(args) for flag in flags):
            raise UnsafeSecondaryPath('Incomplete/duplicate Apple FIFO arguments')
        pid, parent_id = process['pid'], process['ppid']
        member, parent = members.get(pid), members.get(parent_id)
        if (member is None or member['start'] != process['start'] or parent is None or
                current.get(pid, {}).get('start') != process['start'] or
                current.get(parent_id, {}).get('start') != parent['start'] or
                Path(shlex.split(parent['command'])[0]).name != 'ibtoold'):
            raise UnsafeSecondaryPath('Missing live attested Apple process ancestry')
        if self.uid_of(pid) != os.getuid() or self.uid_of(parent_id) != os.getuid():
            raise UnsafeSecondaryPath('Apple worker UID differs from owned user')
        after = self.current_processes()
        if (after.get(pid, {}).get('start') != process['start'] or
                after.get(parent_id, {}).get('start') != parent['start']):
            raise UnsafeSecondaryPath('Apple process changed during UID verification')
        paths = [self.canonical_argument(args[args.index(flag) + 1]) for flag in flags]
        if paths == [None, None]:
            return
        if any(path is None for path in paths) or paths[0].parent != paths[1].parent:
            raise UnsafeSecondaryPath('Apple FIFO pair is not in one attested parent')
        if (paths[0].parent.parent.name != 'ibtoold-' + str(parent_id) or
                paths[0].stem != paths[1].stem or paths[0].suffix != '.HostToRemote' or
                paths[1].suffix != '.RemoteToHost'):
            raise UnsafeSecondaryPath('Apple FIFO pair differs from owned parent PID')
        for path in paths:
            key = str(path)
            if len(self.pending) + len(self.records) >= 256 and key not in self.pending and key not in self.records:
                raise UnsafeSecondaryPath('Secondary path ledger bound exceeded')
            claim = dict(path=key, process=dict(pid=pid, start=process['start']),
                         parent=dict(pid=parent_id, start=parent['start']))
            self.pending[key] = claim
            if not path.exists():
                continue  # Capture early claim; finalization fails if an unattested path later appears.
            relatives = [path.parent.parent, path.parent, path]
            values = [metadata(item) for item in relatives]
            for index, value in enumerate(values):
                right_kind = stat.S_ISFIFO(value['mode']) if index == 2 else stat.S_ISDIR(value['mode'])
                if (not right_kind or value['uid'] != os.getuid() or
                        value['birth_at'] < self.started or value['birth_at'] > time.time() + 1):
                    raise UnsafeSecondaryPath('Secondary inode/type/UID/birthtime is not task-owned')
            record = {**claim, 'metadata': [dict(path=str(item), **value) for item, value in zip(relatives, values)]}
            existing = self.records.get(key)
            if existing is not None and existing != record:
                raise UnsafeSecondaryPath('Secondary inode or owner was replaced')
            self.records[key] = record
            self.pending.pop(key, None)
        self.persist(self.dump())  # Durable path/inode proof BEFORE workers may exit.

    def cleanup(self):
        current = self.current_processes()
        for claim in self.pending.values():
            if Path(claim['path']).exists() or Path(claim['path']).is_symlink():
                raise UnsafeSecondaryPath('Observed but never inode-attested secondary path remains')
        dirs = {}
        expected_children = {}
        for record in self.records.values():
            # Refuse PID reuse too: no other process is signaled or adopted.
            if record['process']['pid'] in current or record['parent']['pid'] in current:
                raise UnsafeSecondaryPath('Secondary worker/parent PID still exists or was reused')
            for entry in record['metadata']:
                path = Path(entry['path'])
                no_symlink_components(path)
                if path.exists():
                    if metadata(path) != {key: value for key, value in entry.items() if key != 'path'}:
                        raise UnsafeSecondaryPath('Secondary file/directory metadata changed')
                if stat.S_ISDIR(entry['mode']):
                    dirs[str(path)] = entry
                    expected_children.setdefault(str(path), set())
                expected_children.setdefault(str(path.parent), set()).add(path.name)
        for raw in dirs:
            path = Path(raw)
            if path.exists():
                unknown = {child.name for child in path.iterdir()} - expected_children[raw]
                if unknown:
                    raise UnsafeSecondaryPath('Unknown files in secondary directory; preserve entire root')
                if self.holders(['+D', raw]):
                    raise UnsafeSecondaryPath('Open secondary files still have holders')
        for record in self.records.values():
            path = Path(record['path'])
            if path.exists():
                no_symlink_components(path)
                if self.holders([str(path)]):
                    raise UnsafeSecondaryPath('Secondary FIFO acquired a holder')
                if metadata(path) != {key: value for key, value in record['metadata'][-1].items() if key != 'path'}:
                    raise UnsafeSecondaryPath('Secondary FIFO changed before unlink')
                path.unlink()
                self.removed.append(str(path)); self.persist(self.dump())
        for raw in sorted(dirs, key=lambda value: len(Path(value).parts), reverse=True):
            path = Path(raw)
            if path.exists():
                no_symlink_components(path)
                if metadata(path) != {key: value for key, value in dirs[raw].items() if key != 'path'}:
                    raise UnsafeSecondaryPath('Secondary parent changed before rmdir')
                path.rmdir()  # Unknown concurrent contents cause failure, never recursive deletion.
                self.removed.append(raw); self.persist(self.dump())
        remaining = [path for path in list(self.records) + list(dirs) if Path(path).exists() or Path(path).is_symlink()]
        if remaining:
            raise UnsafeSecondaryPath('Attested secondary paths remain')
        self.persist(self.dump())
        return dict(status='PASS', removed=self.removed, remaining=[])
