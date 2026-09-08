"""Read-only source identity and explicit child PID/start ownership for one lane.

Derived from the independently reviewed native10 support routines. No build or
process-control operation runs on import. Never adopt arbitrary file viewers.
"""
import datetime
import hashlib
import json
import os
from pathlib import Path
import shlex
import signal
import subprocess
import time

ROOT = Path(__file__).resolve().parents[3]


def now():
    return datetime.datetime.now(datetime.timezone.utc).isoformat()


def sanitized(value):
    return str(value)  # Only allowlisted, non-secret task commands are retained.


def digest(path):
    result = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            result.update(block)
    return result.hexdigest()


def write_json(path, value):
    # Only task-owned evidence paths are replaced; old cycles are refused.
    partial = path.with_name(path.name + '.writing')
    partial.write_text(json.dumps(value, indent=2) + '\n')
    partial.replace(path)


def snapshot_processes():
    result = subprocess.run(
        ['ps', '-axo', 'pid=,ppid=,pgid=,lstart=,command='], text=True,
        capture_output=True, timeout=15, env={**os.environ, 'LC_ALL': 'C'},
    )
    if result.returncode:
        raise RuntimeError('Cannot establish process ownership from ps')
    rows = {}
    for line in result.stdout.splitlines():
        parts = line.split(None, 8)
        if len(parts) == 9 and all(x.isdigit() for x in parts[:3]):
            pid, parent, group = map(int, parts[:3])
            rows[pid] = dict(pid=pid, ppid=parent, pgid=group,
                             start=' '.join(parts[3:8]), command=parts[8])
    if os.getpid() not in rows:
        raise RuntimeError('Process snapshot is incomplete')
    return rows


def lsof_pids(arguments):
    result = subprocess.run(
        # -t also suppresses warnings. Re-enable them afterwards so a failed
        # directory/file selection cannot masquerade as an empty holder set.
        ['/usr/sbin/lsof', '-nP', '-t', '+w', *arguments], text=True,
        capture_output=True, timeout=30,
    )
    if len(result.stdout.encode()) > 65536 or len(result.stderr.encode()) > 4096:
        raise RuntimeError('Ownership lsof output exceeds its acceptance limit')
    # Exit1 also covers a partially unmatched +D selection: preserve every PID.
    # Diagnostics may mention unrelated native paths; do not echo them to logs.
    if result.returncode not in (0, 1) or result.stderr.strip():
        raise RuntimeError('Ownership lsof failed or reported an inspection warning')
    lines = result.stdout.splitlines()
    if result.returncode == 0 and not lines:
        raise RuntimeError('Ownership lsof success did not identify any holder')
    if any(not line.isdigit() for line in lines):
        raise RuntimeError('Unexpected lsof ownership output')
    return {int(line) for line in lines}


def owns_java_tmp_argument(command, temporary):
    """Exact argv match: a prefix, duplicate override or quoted echo is not ours."""
    try:
        arguments = shlex.split(command)
    except ValueError:
        return False
    return (bool(arguments) and Path(arguments[0]).name == 'java' and
            [argument for argument in arguments if argument.startswith('-Djava.io.tmpdir=')] ==
            ['-Djava.io.tmpdir=' + str(temporary / 'tmp')])



class Ownership:
    """Keep PID + start-time identity; never kill by AVD name or port alone."""
    def __init__(self, baseline, temporary, outputs, dest):
        self.baseline = {pid: item['start'] for pid, item in baseline.items()}
        self.temporary = temporary
        self.outputs = outputs
        self.members = {}
        self.last = baseline
        self.unknown_holders = []
        self.handles = []
        self.handle_roles = {}
        self.launch_logs = [dest / name for name in ('adb-server.log', 'gradle.log', 'stop.log')]

    def remember(self, item, role, proof):
        if item['pid'] == os.getpid() or self.baseline.get(item['pid']) == item['start']:
            raise RuntimeError('Refusing ownership of a pre-existing process')
        if len(self.members) >= 1024 and item['pid'] not in self.members:
            raise RuntimeError('Task process ownership ledger limit reached')
        self.members[item['pid']] = {**item, 'role': role, 'proof': proof}

    def register(self, child, role):
        self.handles.append(child)
        self.handle_roles[child.pid] = role
        current = snapshot_processes()
        item = current.get(child.pid)
        if item is not None:
            self.remember(item, role, 'Popen PID observed immediately after owned launch')
        self.last = current

    def refresh(self, inspect_files=False, include_outputs=False):
        current = snapshot_processes()
        # If the first metadata query failed after Popen, an unreaped live child
        # handle still proves ownership; recover that proof before termination.
        for child in self.handles:
            if child.poll() is None and child.pid in current:
                self.remember(current[child.pid], self.handle_roles[child.pid],
                              'Identity recovered from still-live owned Popen child')
        # Repeatedly follow current, identity-checked ancestors/process groups.
        while True:
            alive = {pid: item for pid, item in self.members.items()
                     if pid in current and current[pid]['start'] == item['start']}
            groups = {current[pid]['pgid']: item for pid, item in alive.items()}
            additions = []
            for pid, item in current.items():
                if pid == os.getpid() or pid in alive or self.baseline.get(pid) == item['start']:
                    continue
                owner = alive.get(item['ppid']) or groups.get(item['pgid'])
                if owner is not None:
                    additions.append((item, owner['role'], 'Observed child/group of identity-checked task process'))
            if not additions:
                break
            for args in additions:
                self.remember(*args)
        if inspect_files:
            raise RuntimeError('AppHostOwnership must implement inspection; never adopt by arbitrary file access')
        # A new detached Gradle worker carries our unique java.io.tmpdir even
        # before it opens files. Do not use a generic repository-name match.
        for pid, item in current.items():
            if (pid != os.getpid() and self.baseline.get(pid) != item['start']
                    and pid not in self.members and owns_java_tmp_argument(item['command'], self.temporary)):
                self.remember(item, 'detached-task-worker', 'Exact task-only JVM temp-directory argument')
        self.last = current
        return [item for pid, item in self.members.items()
                if pid in current and item['start'] == current[pid]['start']]

    def signal_owned(self, item, signum):
        current = snapshot_processes().get(item['pid'])
        if current is None or current['start'] != item['start']:
            return
        try:
            os.kill(item['pid'], signum)
        except ProcessLookupError:
            pass

    def stop(self, selected=lambda item: True):
        # Never remove a live worker's files; discover children during shutdown
        # and escalate only verified, task-owned identities after a bounded wait.
        for signum, seconds in ((signal.SIGTERM, 20), (signal.SIGKILL, 10)):
            deadline = time.monotonic() + seconds
            signaled = set()
            while time.monotonic() < deadline:
                live = [item for item in self.refresh(inspect_files=True) if selected(item)]
                if not live:
                    return
                for item in live:
                    key = (item['pid'], item['start'])
                    if key not in signaled:
                        self.signal_owned(item, signum)
                        signaled.add(key)
                for child in self.handles:
                    child.poll()  # reap our direct children; zombies are not leaks
                time.sleep(0.25)
        live = [item for item in self.refresh(inspect_files=True) if selected(item)]
        if live:
            raise RuntimeError('Verified task workers did not terminate: ' + str([x['pid'] for x in live]))

    def receipt_members(self, items=None):
        return [{**item, 'command': sanitized(item['command'])[:1600]}
                for item in (self.members.values() if items is None else items)]



def owned_outputs():
    modules = [path.parent for path in ROOT.glob('shared/*/build.gradle.kts')]
    modules += [path.parent for path in ROOT.glob('game-modes/*/build.gradle.kts')]
    modules += [ROOT, ROOT/'composeApp', ROOT/'build-logic', ROOT/'build-logic/convention']
    return [path/'build' for path in modules]


def identity():
    from copied_sources import PROTECTED, allowed_path
    result = {key: subprocess.check_output(['git', *args], cwd=ROOT, text=True).strip()
              for key, args in {'commit': ['rev-parse', 'HEAD'], 'tree': ['rev-parse', 'HEAD^{tree}'],
                  'branch': ['branch', '--show-current'],
                  'tracked_status': ['status', '--porcelain=v1', '--untracked-files=no']}.items()}
    diff = subprocess.check_output(['git', 'diff', '--binary', 'HEAD', '--'], cwd=ROOT)
    result['diff_sha256'] = hashlib.sha256(diff).hexdigest()
    paths = subprocess.check_output(['git', 'ls-files', '--cached', '--others', '--exclude-standard', '-z'],
                                    cwd=ROOT).decode().split('\0')
    records = []
    for relative in sorted(set(paths)):
        if not relative or relative.startswith(('audit-runs/', 'remediation-runs/', 'project-code-audit/', 'design/')):
            continue
        if any(token in relative.lower() for token in PROTECTED):
            continue
        path = ROOT/relative
        if path.is_symlink():
            if allowed_path(relative):
                raise RuntimeError('Build-consumed input is a symlink; no source copy authorized')
            continue
        if path.is_file():
            records.append([relative, digest(path)])
    result['source_manifest'] = records
    result['source_manifest_sha256'] = hashlib.sha256(json.dumps(records, separators=(',', ':')).encode()).hexdigest()
    return result
