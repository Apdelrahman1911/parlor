#!/usr/bin/env python3
"""One reviewed native09 recovery only. No scanning, signals, recursive deletion, or imports of app/harness code."""
import argparse
from contextlib import ExitStack
import hashlib
import json
import os
from pathlib import Path
import stat
import subprocess
import sys
import time

REPO = Path('/Users/abdelrahman/Projects/parlor')
CAMPAIGN = REPO / 'remediation-runs/2026-09-07-local-readiness'
PLAN = CAMPAIGN / 'reviews/native09-secondary-fifo-recovery-independent-proposal-01.json'
PLAN_SHA = '691caf73451570aae8cab30279ef84d180d15496d8d3fa39625593901855cc37'
ROOT = Path('/private/var/folders/6m/vxwlbjsn7vs6h80_98w6x7p40000gn/T/ibtoold-41768')
NONCE = '1F091456-2735-42C0-A7F7-942CBB4E01BE'
NAMES = (NONCE + '.RemoteToHost', NONCE + '.HostToRemote')
PIDS = (41785, 41768)
KEYS = ('device', 'inode', 'uid', 'mode', 'birth_at')


def require(ok, message):
    if not ok:
        raise RuntimeError(message)


def identity(value):
    require(hasattr(value, 'st_birthtime'), 'Creation metadata unavailable')
    return dict(device=value.st_dev, inode=value.st_ino, uid=value.st_uid,
                mode=value.st_mode, birth_at=value.st_birthtime)


def checked_bytes(path, limit):
    require(path.resolve(strict=True) == path and not path.is_symlink(), 'Redirected input')
    with path.open('rb') as stream:
        data = stream.read(limit + 1)
    require(len(data) <= limit, 'Oversized input')
    return data


def bound_plan():
    data = checked_bytes(PLAN, 65536)
    require(hashlib.sha256(data).hexdigest() == PLAN_SHA, 'Independent plan changed')
    plan = json.loads(data)
    for item in plan['immutable_evidence']:
        path = Path(item['path'])
        require(path.parent == CAMPAIGN / 'evidence/ios-readiness-09', 'Unexpected evidence input')
        require(hashlib.sha256(checked_bytes(path, 1024 * 1024)).hexdigest() == item['sha256'], 'Original evidence changed')
    observations = plan['actual_observation']['current_lstat_observations']
    expected = {Path(row['path']): {key: row[key] for key in KEYS} for row in observations}
    wanted = set(reversed((ROOT / 'IB').parents)) | {ROOT / 'IB'} | {ROOT / 'IB' / name for name in NAMES}
    require(set(expected) == wanted and len(expected) == len(observations), 'Unexpected frozen paths')
    for path in (ROOT, ROOT / 'IB', *(ROOT / 'IB' / name for name in NAMES)):
        require(expected[path]['uid'] == 501 and expected[path]['birth_at'] >= 1788820727.136113, 'Wrong cycle/UID')
    require(expected[ROOT / 'IB' / NAMES[1]]['inode'] == 85822501, 'Wrong newly reviewed pending inode')
    return expected


def query(arguments):
    # Exact ps selection: <=2 PIDs. Exact lsof target: this four-object directory only.
    result = subprocess.run(arguments, capture_output=True, timeout=15, check=False)
    require(len(result.stdout) <= 32768 and len(result.stderr) <= 4096, 'Unbounded ownership query')
    require(result.returncode in (0, 1) and not result.stderr.strip(), 'Ownership query failed')
    text = result.stdout.decode('ascii', errors='strict').strip()
    require(all(line.strip().isdigit() for line in text.splitlines()), 'Malformed PID query')
    values = {int(line) for line in text.splitlines()}
    require((result.returncode == 0 and bool(values)) or (result.returncode == 1 and not values), 'Ambiguous ownership query')
    return values


def check_pids():
    require(not query(['/bin/ps', '-p', ','.join(map(str, PIDS)), '-o', 'pid=']), 'Old worker PID exists or was reused')


def check_holders(root):
    # Our own directory descriptors are deliberate; no FIFO is ever opened/read.
    require(not (query(['/usr/sbin/lsof', '-nP', '-w', '-t', '+D', str(root)]) - {os.getpid()}), 'Secondary files have other holders')


class ExactRecovery:
    """Descriptor-relative operations; production caller supplies only frozen literals above."""
    def __init__(self, root, names, expected, process_guard, holder_guard, event):
        self.root, self.names, self.expected = root, names, expected
        self.process_guard, self.holder_guard, self.event = process_guard, holder_guard, event
        self.fds = {}

    def run(self, execute=False):
        require(os.open in os.supports_dir_fd and os.stat in os.supports_dir_fd and
                os.unlink in os.supports_dir_fd and os.rmdir in os.supports_dir_fd and
                os.listdir in os.supports_fd, 'Required descriptor-relative APIs unavailable')
        require(self.root.is_absolute() and len(set(self.names)) == 2 and
                all('/' not in name and name not in ('', '.', '..') for name in self.names), 'Bad exact target')
        removed = set()
        flags = os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW
        with ExitStack() as stack:
            chain = list(reversed((self.root / 'IB').parents)) + [self.root / 'IB']
            for path in chain:
                fd = os.open(str(path) if path == Path('/') else path.name, flags,
                             **({} if path == Path('/') else {'dir_fd': self.fds[path.parent]}))
                stack.callback(os.close, fd)
                self.fds[path] = fd
                require(identity(os.fstat(fd)) == self.expected[path], 'Ancestor identity changed')
            def inspect(remaining):
                for path in chain:
                    if path in removed:
                        continue
                    current = os.fstat(self.fds[path]) if path == Path('/') else os.stat(
                        path.name, dir_fd=self.fds[path.parent], follow_symlinks=False)
                    require(identity(current) == self.expected[path] and
                            identity(os.fstat(self.fds[path])) == self.expected[path], 'Ancestor moved or changed')
                require(set(os.listdir(self.fds[self.root])) == ({'IB'} if self.root / 'IB' not in removed else set()), 'Unknown root contents')
                if self.root / 'IB' not in removed:
                    require(set(os.listdir(self.fds[self.root / 'IB'])) == set(remaining), 'Unknown/missing FIFO contents')
                    for name in remaining:
                        path = self.root / 'IB' / name
                        current = os.stat(name, dir_fd=self.fds[path.parent], follow_symlinks=False)
                        require(identity(current) == self.expected[path] and stat.S_ISFIFO(current.st_mode)
                                and current.st_size == 0 and current.st_nlink == 1, 'FIFO identity/type/links changed')
            def guard(remaining):
                inspect(remaining)
                self.process_guard()
                self.holder_guard(self.root)
                inspect(remaining)  # Recheck all identities and children after external queries.
            remaining = set(self.names)
            guard(remaining)
            self.event(dict(stage='all-targets-verified', execute=execute))
            if not execute:
                return dict(status='VERIFIED_NO_DELETION', removed=[])
            for name in self.names:
                guard(remaining)
                path = self.root / 'IB' / name
                current = os.stat(name, dir_fd=self.fds[path.parent], follow_symlinks=False)
                require(identity(current) == self.expected[path] and stat.S_ISFIFO(current.st_mode)
                        and current.st_nlink == 1, 'FIFO changed immediately before unlink')
                os.unlink(name, dir_fd=self.fds[path.parent])
                remaining.remove(name)
                self.event(dict(stage='unlinked-exact-fifo', path=str(path)))
            guard(remaining)
            os.rmdir('IB', dir_fd=self.fds[self.root])
            removed.add(self.root / 'IB')
            self.event(dict(stage='removed-empty-parent', path=str(self.root / 'IB')))
            guard(remaining)
            os.rmdir(self.root.name, dir_fd=self.fds[self.root.parent])
            self.event(dict(stage='removed-empty-parent', path=str(self.root)))
            try:
                os.stat(self.root.name, dir_fd=self.fds[self.root.parent], follow_symlinks=False)
            except FileNotFoundError:
                pass
            else:
                raise RuntimeError('Exact root unexpectedly remains/reappeared')
            return dict(status='RECOVERED_CLEANUP_ONLY', removed=[str(self.root / 'IB' / name) for name in self.names] + [str(self.root / 'IB'), str(self.root)])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--execute', action='store_true', help='Explicitly unlink only the independently frozen four task-owned objects')
    parser.add_argument('--approved-script-sha256', required=True)
    args = parser.parse_args()
    self_path = Path(__file__).absolute()
    actual_sha = hashlib.sha256(checked_bytes(self_path, 32768)).hexdigest()
    require(actual_sha == args.approved_script_sha256, 'Script differs from reviewed approved bytes')
    expected = bound_plan()
    destination = CAMPAIGN / ('evidence/native09-secondary-fifo-recovery-' + ('execute-01' if args.execute else 'verify-01'))
    require(destination.parent.resolve(strict=True) == destination.parent, 'Redirected evidence destination')
    destination.mkdir(mode=0o700)  # Deliberately fail if old evidence already exists.
    receipt = dict(status='RUNNING', started_at=time.time(), script_sha256=actual_sha, plan_sha256=PLAN_SHA,
                   execute=args.execute, original_native09_status='FAIL', original_native09_cleanup_status='FAIL')
    try:
        with (destination / 'new-independent-claim.json').open('x') as stream:
            json.dump([dict(path=str(path), **expected[path]) for path in (ROOT, ROOT / 'IB', *(ROOT / 'IB' / name for name in NAMES))], stream, indent=2)
            stream.flush(); os.fsync(stream.fileno())
        with (destination / 'events.jsonl').open('x') as events:
            def event(value):
                events.write(json.dumps(dict(at=time.time(), **value)) + '\n')
                events.flush(); os.fsync(events.fileno())
            receipt.update(ExactRecovery(ROOT, NAMES, expected, check_pids, check_holders, event).run(args.execute))
        return_code = 0
    except BaseException as error:
        receipt.update(status='BLOCKED_PRESERVE_REMAINDER', error_type=type(error).__name__, error=str(error)[:300])
        return_code = 1
    finally:
        receipt['finished_at'] = time.time()
        with (destination / 'receipt.json').open('x') as stream:
            json.dump(receipt, stream, indent=2); stream.write('\n')
    print(json.dumps(receipt))
    return return_code


if __name__ == '__main__':
    sys.exit(main())
