#!/usr/bin/env python3
"""Synthetic process tables only. No ps/lsof/kill/subprocess/build/device calls."""
import ast
import datetime
import hashlib
import json
from pathlib import Path
import sys
from types import SimpleNamespace

BASE = Path(__file__).resolve().parent
ROOT = BASE.parents[3]
OUT = ROOT / 'audit-runs/2026-09-05-source-audit'
source = Path(sys.argv[1]).resolve()
source.relative_to(BASE)
parent = OUT / 'run_android_managed_cycle.py'
parent_bytes = parent.read_bytes()
assert hashlib.sha256(parent_bytes).hexdigest() == 'b34ad4662ac906bff0a2f4e8ebe25c0757229098361e8e34c29b7c06681730c0'


class SyntheticPath:
    def __init__(self, path, symlink=False):
        self.path = path
        self.symlink = symlink

    def __truediv__(self, path):
        return SyntheticPath(self.path + '/' + path)

    def __str__(self):
        return self.path

    def is_dir(self):
        return True

    def is_symlink(self):
        return self.symlink


state = {'processes': {}, 'holders': set()}
checks = []


def unexpected(*args, **kwargs):
    raise AssertionError('No actual OS/process function may execute')


ns = {'os': SimpleNamespace(getpid=lambda: 1, kill=unexpected), 'snapshot_processes': lambda: copy_rows(state['processes']),
      'lsof_pids': lambda arguments: set(state['holders']), 'SDK': SyntheticPath('/synthetic-sdk'),
      'launch_log_writers': unexpected, 'signal': SimpleNamespace(SIGTERM=15, SIGKILL=9),
      'time': SimpleNamespace(monotonic=unexpected, sleep=unexpected), 'sanitized': str}


def copy_rows(rows):
    return {pid: dict(item) for pid, item in rows.items()}


for raw, class_name in [(parent_bytes, 'Ownership'), (source.read_bytes(), 'AppHostOwnership')]:
    module = ast.parse(raw.decode())
    node = next(n for n in module.body if isinstance(n, ast.ClassDef) and n.name == class_name)
    exec(compile(ast.Module(body=[node], type_ignores=[]), class_name + '<synthetic>', 'exec'), ns)


def proc(pid, command, parent=700, group=700, start=None):
    return {'pid': pid, 'command': command, 'ppid': parent, 'pgid': group, 'start': start or ('SYNTHETIC_START_' + str(pid))}


def owner_for(items, holders, preexisting=()):
    own = proc(1, 'audit-python')
    baseline = {1: own, **{item['pid']: dict(item) for item in preexisting}}
    state['processes'] = {**baseline, **{item['pid']: dict(item) for item in items}}
    state['holders'] = set(holders)
    return ns['AppHostOwnership'](baseline, SyntheticPath('/synthetic-unique-task'), [], SyntheticPath('/synthetic-evidence'))


for name, command in [
    ('unrelated tail viewer', '/usr/bin/tail -f /synthetic-unique-task/DerivedData/log'),
    ('unrelated Java reader', '/jdk/bin/java Viewer /synthetic-unique-task/DerivedData/log'),
    ('new shared Xcode service', '/Applications/Xcode.app/Contents/SharedFrameworks/XCBuild.framework/XCBBuildService'),
    ('new detached Swift compiler without launch ownership', '/Applications/Xcode.app/Contents/usr/bin/swift-frontend -o /synthetic-unique-task/DerivedData/result.o'),
]:
    item = proc(2, command)
    owner = owner_for([item], [2])
    assert owner.refresh(inspect_files=True) == []
    assert 2 not in owner.members and [p['pid'] for p in owner.unknown_holders] == [2]
    checks.append(name + ': not adopted; blocks deletion')

item = proc(3, '/jdk/bin/java -Djava.io.tmpdir=/synthetic-unique-task/tmp org.gradle.launcher.daemon.bootstrap.GradleDaemon')
owner = owner_for([item], [3])
assert [p['pid'] for p in owner.refresh(inspect_files=True)] == [3]
assert not owner.unknown_holders and owner.members[3]['proof'] == 'Exact task-only JVM temp-directory argument'
checks.append('new exact task-temp Gradle worker: correctly attributed')

owner = owner_for([item], [3], preexisting=[item])
assert owner.refresh(inspect_files=True) == [] and 3 not in owner.members
assert owner.unknown_holders[0]['pid'] == 3
checks.append('pre-existing exact-temp process: never adopted; blocks deletion')

owned = proc(10, 'owned-launch', parent=1, group=10)
child = proc(11, 'owned-child', parent=10, group=10)
grandchild = proc(12, 'owned-grandchild', parent=11, group=12)
owner = owner_for([owned, child, grandchild], [])
owner.remember(owned, 'command', 'Synthetic Popen identity already registered')
assert {p['pid'] for p in owner.refresh(inspect_files=True)} == {10, 11, 12}
assert not owner.unknown_holders
checks.append('identity-attested Popen ancestry/group recursively attributes descendants')

reused = dict(owned, start='REUSED_OTHER_START')
state['processes'][10] = reused
state['processes'].pop(11); state['processes'].pop(12)
assert owner.refresh(inspect_files=True) == []
checks.append('PID reuse with different start metadata never treated as old process')

owner = owner_for([], [])
owner.temporary.symlink = True
try:
    owner.refresh(inspect_files=True)
except RuntimeError:
    checks.append('symlink root refuses cleanup ownership traversal')
else:
    raise AssertionError('expected symlink rejection')

receipt = {'at': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'status': 'PASS', 'runner_sha256': hashlib.sha256(source.read_bytes()).hexdigest(), 'imported_helper_sha256': hashlib.sha256(parent_bytes).hexdigest(), 'synthetic_script_sha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(), 'runner_snapshot': str(source.relative_to(ROOT)), 'checks': len(checks), 'cases': checks, 'limitations': 'Only fake process tables/fake paths/fake lsof sets. No actual process, signal, file traversal, simulator/device, Gradle or Xcode execution. Cleanup of real detached workers remains runtime evidence.', 'cleanup': 'No temporary files/processes/generated build outputs created; this compact evidence retained.'}
path = BASE / ('ownership-synthetic-' + receipt['runner_sha256'][:12] + '.json')
path.write_text(json.dumps(receipt, indent=2) + '\n')
print(json.dumps({'status': receipt['status'], 'checks': len(checks), 'evidence': str(path.relative_to(ROOT))}, indent=2))
