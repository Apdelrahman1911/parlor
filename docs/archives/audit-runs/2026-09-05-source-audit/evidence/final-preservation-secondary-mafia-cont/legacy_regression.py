#!/usr/bin/env python3
"""Exercise exact checker fragments with synthetic process/port metadata only."""
import ast
import datetime
import hashlib
import json
from pathlib import Path
import re
from types import SimpleNamespace

HERE = Path(__file__).resolve().parent
SOURCE = HERE / 'final_preservation_check-93d85c.py'
raw = SOURCE.read_bytes()
assert hashlib.sha256(raw).hexdigest() == '93d85c028fbb0050f2c53b2fdebe3b653aec4151356cbf5077b569ab95fd89dc'
tree = ast.parse(raw, str(SOURCE))
guarded = next(n for n in tree.body if isinstance(n, ast.Try))
results = []


def run(nodes, values):
    exec(compile(ast.Module(body=nodes, type_ignores=[]), str(SOURCE), 'exec'), values)
    return values


def check(name, value):
    assert value, name
    results.append(dict(name=name, status='PASS'))


def assignment(name):
    return next(n for n in guarded.body if isinstance(n, ast.Assign) and
                any(isinstance(t, ast.Name) and t.id == name for t in n.targets))


class SyntheticCommands:
    def __init__(self, processes='', code=1, stdout='', stderr=''):
        self.processes = processes
        self.result = SimpleNamespace(returncode=code, stdout=stdout, stderr=stderr)
        self.calls = []

    def check_output(self, args, **kwargs):
        assert args == ['ps', '-axo', 'pid=,ppid=,lstart=,command=']
        self.calls.append(dict(kind='MOCK_ONLY', command=args))
        return self.processes

    def run(self, args, **kwargs):
        assert args == ['/usr/sbin/lsof', '-nP', '-w', '-t', '-iTCP:32123', '-sTCP:LISTEN']
        self.calls.append(dict(kind='MOCK_ONLY', command=args))
        return self.result


def process_check(extra='', identities=None):
    own = '100 1 Sat Sep 5 10:00:00 2026 python checker.py\n'
    commands = SyntheticCommands(own + extra)
    values = dict(subprocess=commands, os=SimpleNamespace(environ={}, getpid=lambda: 100),
                  re=re, devices={'owned-uuid'}, temporaries={'/tmp/parlor-audit-owned'},
                  attested_processes=identities or {})
    return run([n for n in guarded.body if 80 <= n.lineno and n.end_lineno <= 105], values)


base = process_check()
check('self-process parsed; no synthetic workers', 100 in base['current_processes'] and not base['attested_still_alive'])
attested = {(200, 'Sat Sep 5 10:01:00 2026'): dict(pid=200, start='Sat Sep 5 10:01:00 2026', role='worker', proof='synthetic')}
alive = process_check('200 100 Sat Sep 5 10:01:00 2026 hidden-worker\n', attested)
check('PID+start attested worker blocks', len(alive['attested_still_alive']) == 1)
reused = process_check('200 1 Sat Sep 5 10:02:00 2026 unrelated-worker\n', attested)
check('PID reuse not misclassified as owned', not reused['attested_still_alive'])
check('recorded temp argv marker detected', len(process_check('201 1 Sat Sep 5 10:02:00 2026 job /tmp/parlor-audit-owned\n')['owned_matches']) == 1)
check('recorded simulator UUID marker detected', len(process_check('202 1 Sat Sep 5 10:02:00 2026 sim owned-uuid\n')['owned_matches']) == 1)
check('Gradle8.13 conservatively blocks', len(process_check('203 1 Sat Sep 5 10:02:00 2026 java org.gradle.launcher.daemon.bootstrap.GradleDaemon 8.13\n')['gradle8']) == 1)
unrelated = process_check('204 1 Sat Sep 5 10:02:00 2026 java org.gradle.launcher.daemon.bootstrap.GradleDaemon 9.0\n')
check('unrelated Gradle version preserved', not unrelated['gradle8'] and len(unrelated['unrelated_workers']) == 1)
try:
    run([n for n in guarded.body if 80 <= n.lineno and n.end_lineno <= 105], dict(
        subprocess=SyntheticCommands('malformed\n'), os=SimpleNamespace(environ={}, getpid=lambda: 100),
        re=re, devices=set(), temporaries=set(), attested_processes={}))
except RuntimeError:
    check('missing checker PID fails closed', True)
else:
    raise AssertionError('missing self-PID must fail')


def port_check(code, out='', err=''):
    commands = SyntheticCommands(code=code, stdout=out, stderr=err)
    return run([n for n in guarded.body if 107 <= n.lineno and n.end_lineno <= 121],
               dict(subprocess=commands, android_ports=[dict(cycle='synthetic', port=32123, owned_pid=200)],
                    current_processes={}))


none = port_check(1)
check('no listener exit1 accepted', not none['port_check_errors'] and none['adb_port_checks'][0]['listener_pids'] == [])
listener = port_check(0, '999\n')
check('new listener is uncertainty, not presumed exited', listener['adb_port_checks'][0]['listeners'][0]['state'] == 'not-in-earlier-process-snapshot')
check('lsof invalid exit fails closed', bool(port_check(2)['port_check_errors']))
check('lsof stderr fails closed', bool(port_check(0, err='synthetic warning')['port_check_errors']))
check('lsof malformed PID fails closed', bool(port_check(0, 'bad\n')['port_check_errors']))

clean = dict(remaining_outputs=[], remaining_temp=[], owned_matches=[], attested_still_alive=[],
             port_check_errors=[], adb_port_checks=[], gradle8=[], running_receipts=[], device_records=[])
check('empty synthetic cleanup state accepted', run([assignment('cleanup_ok')], dict(clean))['cleanup_ok'])
for key in ['remaining_outputs', 'remaining_temp', 'owned_matches', 'attested_still_alive',
            'port_check_errors', 'gradle8', 'running_receipts']:
    check('cleanup blocks ' + key, not run([assignment('cleanup_ok')], dict(clean, **{key: ['synthetic']}))['cleanup_ok'])
check('cleanup blocks any observed port listener', not run([assignment('cleanup_ok')], dict(clean, adb_port_checks=listener['adb_port_checks']))['cleanup_ok'])
check('cleanup blocks owned simulator directory', not run([assignment('cleanup_ok')], dict(clean, device_records=[dict(device_directory_exists=True)]))['cleanup_ok'])

saved = []
checkpoint_nodes = [n for n in tree.body if isinstance(n, (ast.Assign, ast.Expr)) and 21 <= n.lineno <= 24]
values = dict(datetime=datetime, save_receipt=lambda value: saved.append(dict(value)))
run(checkpoint_nodes, values)
check('early checkpoint invalidates previous PASS', saved[-1]['source_preservation_status'] == saved[-1]['cleanup_status'] == 'BLOCKED' and saved[-1]['phase'] == 'RUNNING')
values['error'] = RuntimeError('synthetic text must not enter evidence')
run(guarded.handlers[0].body[:-1], values)
check('exception receipt remains fail closed and sanitizes message', saved[-1]['phase'] == 'FAILED' and saved[-1]['cleanup_status'] == 'BLOCKED' and saved[-1]['error_type'] == 'RuntimeError' and 'synthetic text' not in json.dumps(saved[-1]))

receipt = dict(recorded_at=datetime.datetime.now(datetime.timezone.utc).isoformat(), reviewer='/root/mafia_cont',
               source_path=str(SOURCE), source_sha256=hashlib.sha256(raw).hexdigest(),
               execution='Pure extracted AST with mocked ps/lsof; no subprocess, Gradle, ADB, emulator, device, or process-termination command executed.',
               assertions=len(results), results=results,
               limits=['Does not reproduce kernel races, I/O failures, permission denial, or actual runtime cleanup.',
                       'Only exact fragments are executed; complete helper and dependency source also read separately.'],
               cleanup='No build outputs/processes created. Only this script and compact JSON retained as required evidence.')
(HERE/'legacy-regression-receipt.json').write_text(json.dumps(receipt, indent=2)+'\n')
print(json.dumps(dict(assertions=len(results), status='PASS', source_sha256=receipt['source_sha256'])))
