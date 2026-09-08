"""Task-only, metadata-only Apple FIFO cleanup planning; never deletes or signals.

The caller owns process observation, lstat/listdir/lsof, durable receipts, and
last-moment identity checks while executing a plan. A plan is NOT continuing
authorization after metadata or process state changes. No import has side effects.
"""
from dataclasses import dataclass
from pathlib import PurePosixPath
import re
import shlex
import stat
from typing import Mapping, Sequence


class Refused(RuntimeError):
    """Insufficient/changed ownership evidence: retain paths, report cleanup FAIL."""


@dataclass(frozen=True)
class Process:
    pid: int
    parent: int
    group: int
    started: str
    command: str


@dataclass(frozen=True)
class Pair:
    host: Process
    agent: Process
    temporary_parent: str
    root: str
    ib: str
    host_to_remote: str
    remote_to_host: str
    cycle_started: float
    observed_at: float
    uid: int

    @property
    def paths(self):
        return (self.root, self.ib, self.host_to_remote, self.remote_to_host)


@dataclass(frozen=True)
class Entry:
    """One lstat observation: mode includes type; birth is macOS st_birthtime."""
    path: str
    device: int
    inode: int
    mode: int
    uid: int
    birth: float
    size: int
    links: int

    @property
    def identity(self):
        # Directory size/link count may change as our known children disappear.
        return (self.device, self.inode, self.mode, self.uid, self.birth)


@dataclass(frozen=True)
class Step:
    operation: str
    path: str
    identity: tuple


def normalized_path(value):
    """Only normalize the documented macOS /var alias, not arbitrary symlinks.

    Caller must lstat the canonical path components; this function does not
    establish that they are directories or safe to traverse.
    """
    if (not isinstance(value, str) or not value.startswith('/') or
            len(value) > 4096 or any(ord(c) < 32 or ord(c) == 127 for c in value) or
            any(c in ('', '.', '..') for c in value.split('/')[1:])):
        raise Refused('Not an unambiguous absolute path')
    if value.startswith('/var/'):
        value = '/private' + value
    if str(PurePosixPath(value)) != value:
        raise Refused('Non-canonical path')
    return value


def attest_pair(agent_pid: int, members: Mapping[int, Process],
                current: Mapping[int, Process], *, temporary_parent: str,
                ibtoold_executable: str, agent_executable: str,
                cycle_started: float, observed_at: float, uid: int) -> Pair:
    """Consume already-attested task members; never adopt a process from a path.

    `members` must come from this cycle's PID/start/lineage tracker, excluding
    baseline processes. `current` is a fresh full snapshot from the same refresh.
    Both host and agent must be identity-equal live members in that observation.
    Exact executable paths are explicit, reviewed inputs from the selected Xcode.
    """
    if cycle_started > observed_at or uid < 0:
        raise Refused('Invalid cycle bounds or UID')
    agent = members.get(agent_pid)
    if agent is None or current.get(agent_pid) != agent:
        raise Refused('Agent is not a live identity-matching task member')
    host = members.get(agent.parent)
    if host is None or current.get(agent.parent) != host:
        raise Refused('Agent parent is not a live identity-matching task member')
    if (agent.pid != agent_pid or host.pid != agent.parent or
            agent.pid <= 0 or host.pid <= 0 or agent.pid == host.pid or
            not agent.started or not host.started or agent.group != host.group):
        raise Refused('Invalid same-generation host/agent lineage')
    try:
        agent_args, host_args = shlex.split(agent.command), shlex.split(host.command)
    except ValueError as error:
        raise Refused('Ambiguous process command') from error
    if (not agent_args or agent_args[0] != agent_executable or
            not host_args or host_args[0] != ibtoold_executable or
            host_args[1:] != ['--sending-client-environment']):
        raise Refused('Unreviewed Apple worker command')
    flag_a, flag_b = '--hostToRemoteFIFO', '--remoteToHostFIFO'
    if (len(agent_args) != 5 or agent_args.count(flag_a) != 1 or
            agent_args.count(flag_b) != 1 or set(agent_args[1::2]) != {flag_a, flag_b}):
        raise Refused('Unreviewed or repeated Apple FIFO arguments')
    args = dict(zip(agent_args[1::2], agent_args[2::2]))
    parent = normalized_path(temporary_parent)
    root = str(PurePosixPath(parent) / ('ibtoold-' + str(host.pid)))
    ib = root + '/IB'
    outgoing, incoming = normalized_path(args[flag_a]), normalized_path(args[flag_b])
    if str(PurePosixPath(outgoing).parent) != ib or str(PurePosixPath(incoming).parent) != ib:
        raise Refused('FIFO is outside the exact task-host temporary root')
    match = re.fullmatch(r'([0-9A-Fa-f]{8}(?:-[0-9A-Fa-f]{4}){3}-[0-9A-Fa-f]{12})\.HostToRemote',
                         PurePosixPath(outgoing).name)
    if match is None or PurePosixPath(incoming).name != match.group(1) + '.RemoteToHost':
        raise Refused('FIFO names are not one exact UUID pair')
    return Pair(host, agent, parent, root, ib, outgoing, incoming,
                cycle_started, observed_at, uid)


def validate_entry(pair: Pair, entry: Entry):
    if (entry.path not in pair.paths or entry.device < 0 or entry.inode <= 0 or
            entry.uid != pair.uid or not pair.cycle_started <= entry.birth <= pair.observed_at):
        raise Refused('Unowned file metadata or birth outside observation window')
    directory = entry.path in (pair.root, pair.ib)
    expected_type = stat.S_IFDIR if directory else stat.S_IFIFO
    if stat.S_IFMT(entry.mode) != expected_type:
        raise Refused('Unexpected file type, including symlink or regular file')
    if not directory and (entry.size != 0 or entry.links != 1):
        raise Refused('Unexpected FIFO size or link count')


def plan_cleanup(pair: Pair, observed: Mapping[str, Entry], current: Mapping[str, Entry], *,
                 current_processes: Mapping[int, Process], open_holders: Sequence[int],
                 root_children: Sequence[str], ib_children: Sequence[str],
                 canonical_ancestors: Sequence[Entry]) -> tuple:
    """Return only exact unlink/rmdir steps, or refuse without touching any path.

    Metadata, process and holder scans must succeed, be bounded, and be fresh.
    Pass all canonical ancestors from '/' to temporary_parent as no-follow
    observations. An unknown child, unreadable path/scan, PID reuse, live worker,
    symlink, or inode replacement is a retained-output error, never cleanup PASS.
    This function handles a fully observed pair. Partial cleanup is a separate
    recorded recovery action, not permission to infer missing ownership.
    """
    if set(observed) != set(pair.paths) or set(current) != set(pair.paths):
        raise Refused('Incomplete exact four-path metadata')
    # Even a reused PID blocks automation: it is unrelated, not ours to stop.
    if pair.host.pid in current_processes or pair.agent.pid in current_processes:
        raise Refused('Recorded worker PID is live or has been reused')
    if open_holders:
        raise Refused('File holders remain; no ownership inferred from access')
    ancestors = list(reversed(PurePosixPath(pair.temporary_parent).parents))
    expected = [str(p) for p in ancestors] + [pair.temporary_parent]
    if [e.path for e in canonical_ancestors] != expected:
        raise Refused('Incomplete canonical ancestor no-follow inspection')
    if any(stat.S_IFMT(e.mode) != stat.S_IFDIR for e in canonical_ancestors):
        raise Refused('Canonical ancestor is not a directory')
    if canonical_ancestors[-1].uid != pair.uid:
        raise Refused('Native temporary parent belongs to another user')
    if (list(root_children) != ['IB'] or len(ib_children) != 2 or
            set(ib_children) != {PurePosixPath(pair.host_to_remote).name,
                                 PurePosixPath(pair.remote_to_host).name}):
        raise Refused('Unknown or duplicate directory entry')
    for path in pair.paths:
        before, after = observed[path], current[path]
        if before.path != path or after.path != path:
            raise Refused('Mismatched metadata map path')
        validate_entry(pair, before)
        validate_entry(pair, after)
        if before.identity != after.identity:
            raise Refused('Observed path identity changed')
    ordered = [('unlink', pair.host_to_remote), ('unlink', pair.remote_to_host),
               ('rmdir', pair.ib), ('rmdir', pair.root)]
    return tuple(Step(operation, path, current[path].identity) for operation, path in ordered)
