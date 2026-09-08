#!/usr/bin/env python3
"""Frozen-source AST/path/process doubles; no real OS or complete helper calls."""
import ast
import copy
import datetime
import hashlib
import json
from pathlib import Path
import re
from types import SimpleNamespace

HERE = Path(__file__).resolve().parent
AUDIT = HERE.parents[1]
OLD = HERE / 'final_preservation_check.py'
NEW = HERE / 'final_preservation_check-93d85c.py'
APPROVED = HERE.parent / 'final-preservation-delta-mafia-cont/final_preservation_check.py'
assert hashlib.sha256(OLD.read_bytes()).hexdigest() == '41faf6dc4ef4cb1e0ee12880a5062aa78e9ddd044359afc7445a3a135274edf5'
assert hashlib.sha256(NEW.read_bytes()).hexdigest() == '93d85c028fbb0050f2c53b2fdebe3b653aec4151356cbf5077b569ab95fd89dc'
assert hashlib.sha256(APPROVED.read_bytes()).hexdigest() == '433f254527e4409d9b7dcf9f2851427951a350986b4cd8cd5ec9290a54282d3c'
old_tree, tree, prior_tree = [ast.parse(p.read_bytes(), str(p)) for p in [OLD, NEW, APPROVED]]
checks = []


def check(name, condition):
    assert condition, name
    checks.append(dict(name=name, status='PASS'))


def guarded(t):
    return next(n for n in t.body if isinstance(n, ast.Try))


def assign(t, name):
    return next(n for n in guarded(t).body if isinstance(n, ast.Assign) and
                any(isinstance(v, ast.Name) and v.id == name for v in n.targets))


def run(nodes, values):
    exec(compile(ast.Module(body=nodes, type_ignores=[]), 'reviewed-ast-fragments', 'exec'), values)
    return values


def visitor(t=tree):
    values = dict(Path=Path, re=re, temporaries=set(), secondary_temporaries=set(), devices=set(),
                  attested_processes={}, android_ports=[])
    run([next(n for n in guarded(t).body if isinstance(n, ast.FunctionDef) and n.name == 'visit')], values)
    return values


raw_root = '/var/folders/aa/bb/T/ibtoold-67921'
private_root = '/private' + raw_root
for supplied in [raw_root, private_root]:
    values = visitor()
    values['visit'](dict(owned_secondary_temporary_roots=[supplied]))
    check('both exact aliases retained when supplied ' + supplied,
          values['secondary_temporaries'] == values['temporaries'] == {raw_root, private_root})

values = visitor()
values['visit'](dict(nested=[dict(owned_secondary_temporary_roots=[raw_root, private_root, raw_root])]))
check('nested ownership receipt aliases deduplicate', values['secondary_temporaries'] == {raw_root, private_root})
values = visitor()
values['visit'](dict(unowned_note=raw_root, arbitrary_path='/var/folders/aa/bb/T/ibtoold-70000'))
check('unlabelled ibtoold strings do not expand ownership inventory', not values['temporaries'])
values['visit']('/tmp/parlor-audit-existing/sub/file')
check('prior task-primary prefix extraction retained', values['temporaries'] == {'/tmp/parlor-audit-existing'})

for invalid in [None, 42, True, '', '/tmp/ibtoold-67921', raw_root + '/IB', raw_root + '/',
                raw_root.replace('67921', 'not-a-pid'), raw_root + '\n', '/private/private' + raw_root]:
    values = visitor()
    try:
        values['visit'](dict(owned_secondary_temporary_roots=[invalid]))
    except ValueError:
        check('invalid secondary path rejected: ' + repr(invalid), True)
    else:
        raise AssertionError('Malformed secondary path accepted: ' + repr(invalid))


class MockCommands:
    def __init__(self, text):
        self.text = text
        self.calls = []

    def check_output(self, args, **kwargs):
        assert args == ['ps', '-axo', 'pid=,ppid=,lstart=,command=']
        self.calls.append(dict(kind='MOCK_ONLY', args=args))
        return self.text


def processes(t, receipt, command, pid=300, started='Sat Sep 5 10:01:00 2026'):
    values = visitor(t)
    values['visit'](receipt)
    listing = '100 1 Sat Sep 5 10:00:00 2026 python checker\n'
    listing += f'{pid} 1 {started} {command}\n'
    values.update(subprocess=MockCommands(listing), os=SimpleNamespace(environ={}, getpid=lambda: 100))
    body = guarded(t).body
    start = body.index(assign(t, 'process_output'))
    # Everything before ADB queries is only synthetic process classification.
    finish = next(i for i, n in enumerate(body) if isinstance(n, ast.Assign) and
                  isinstance(n.targets[0], ast.Tuple) and
                  any(isinstance(v, ast.Name) and v.id == 'adb_port_checks' for v in n.targets[0].elts))
    return run(body[start:finish], values)


def cleanup(t, **delta):
    values = dict(remaining_outputs=[], remaining_temp=[], owned_matches=[], attested_still_alive=[],
                  port_check_errors=[], adb_port_checks=[], gradle8=[], running_receipts=[], device_records=[])
    values.update(delta)
    return run([assign(t, 'cleanup_ok')], values)['cleanup_ok']


receipt_path = AUDIT / 'evidence/iosr1-apphost-02-secondary-cleanup/receipt.json'
receipt_bytes = receipt_path.read_bytes()
assert hashlib.sha256(receipt_bytes).hexdigest() == '86f62321ef1c39952b6f9a9aeeae54b7d273e810335622655540f2991ba8359d'
actual = json.loads(receipt_bytes)
actual_private = actual['owned_secondary_temporary_roots'][0]
actual_raw = actual_private[len('/private'):]
old = processes(old_tree, actual, 'synthetic-late-worker --fifo ' + actual_raw + '/IB/synthetic')
check('old41faf6 counterexample misses late PID raw alias', not old['owned_matches'] and not old['attested_still_alive'])
check('old alias miss could yield false green when paths gone',
      cleanup(old_tree, owned_matches=old['owned_matches'], attested_still_alive=old['attested_still_alive']))
for spelling in [actual_raw, actual_private]:
    current = processes(tree, actual, 'synthetic-late-worker --fifo ' + spelling + '/IB/synthetic')
    check('late or previously unlisted PID detected for ' + spelling,
          len(current['owned_matches']) == 1 and not current['attested_still_alive'])
    check('live alias marker blocks cleanup for ' + spelling,
          not cleanup(tree, owned_matches=current['owned_matches']))
known = actual['ownership_evidence'][1]
known_old = processes(old_tree, actual, known['command'], known['pid'], known['start'])
known_new = processes(tree, actual, known['command'], known['pid'], known['start'])
check('counter-evidence: old checker still detects exact known PID/start', len(known_old['attested_still_alive']) == 1)
check('new checker retains exact known PID/start detection', len(known_new['attested_still_alive']) == 1)
unrelated = processes(tree, actual, 'unrelated-ibtoold --fifo /var/folders/aa/bb/T/ibtoold-70000/IB/test')
check('unrelated different root/PID not adopted', not unrelated['owned_matches'] and not unrelated['attested_still_alive'])


class MockPath:
    present = set()
    links = set()
    fail = set()
    calls = []

    def __init__(self, path):
        self.path = path

    def exists(self):
        self.calls.append(('exists', self.path))
        if self.path in self.fail:
            raise PermissionError('synthetic private text must not be logged')
        return self.path in self.present

    def is_symlink(self):
        self.calls.append(('is_symlink', self.path))
        return self.path in self.links


def remaining(present=(), links=(), fail=()):
    MockPath.present, MockPath.links, MockPath.fail, MockPath.calls = set(present), set(links), set(fail), []
    return run([assign(tree, 'remaining_temp')], dict(Path=MockPath, temporaries={raw_root, private_root}))['remaining_temp']


check('both missing aliases leave no secondary residue', remaining() == [])
for spelling in [raw_root, private_root]:
    present = remaining(present=[spelling])
    check('either existing alias blocks: ' + spelling, present == [spelling] and not cleanup(tree, remaining_temp=present))
    linked = remaining(links=[spelling])
    check('either dangling alias blocks: ' + spelling, linked == [spelling] and not cleanup(tree, remaining_temp=linked))
check('only explicit two owned aliases get metadata calls', {p for _, p in MockPath.calls} == {raw_root, private_root})


def failure_receipt(action):
    saved = []
    values = dict(datetime=datetime, save_receipt=lambda v: saved.append(copy.deepcopy(v)))
    run([n for n in tree.body if isinstance(n, (ast.Assign, ast.Expr)) and 21 <= n.lineno <= 24], values)
    try:
        action()
    except BaseException as error:
        values['error'] = error
        run(guarded(tree).handlers[0].body[:-1], values)
    else:
        raise AssertionError('Expected a synthetic metadata/schema failure')
    return saved


failed = failure_receipt(lambda: remaining(fail=[private_root]))
check('failed secondary existence query invalidates earlier PASS',
      failed[0]['phase'] == 'RUNNING' and failed[0]['cleanup_status'] == 'BLOCKED' and
      failed[-1]['phase'] == 'FAILED' and failed[-1]['cleanup_status'] == 'BLOCKED' and
      failed[-1]['source_preservation_status'] == 'BLOCKED')
check('failed metadata receipt retains type but not private exception text',
      failed[-1]['error_type'] == 'PermissionError' and 'synthetic private text' not in json.dumps(failed))
failed_schema = failure_receipt(lambda: visitor()['visit'](dict(owned_secondary_temporary_roots=[raw_root, '/not-owned'])))
check('partially collected then invalid secondary list fails closed',
      failed_schema[-1]['phase'] == 'FAILED' and failed_schema[-1]['cleanup_status'] == 'BLOCKED')

for name in ['outputs', 'remaining_outputs', 'remaining_temp', 'device_records', 'source_ok', 'cleanup_ok']:
    check('unchanged approved expression: ' + name,
          ast.dump(assign(tree, name), include_attributes=False) == ast.dump(assign(prior_tree, name), include_attributes=False))
for forbidden in ['unlink', 'rmdir', 'rmtree', 'kill', 'killpg', 'terminate', 'Popen']:
    check('no destructive/launch API introduced: ' + forbidden,
          not any(isinstance(n, ast.Call) and isinstance(n.func, ast.Attribute) and n.func.attr == forbidden for n in ast.walk(tree)))

result = dict(recorded_at=datetime.datetime.now(datetime.timezone.utc).isoformat(),
              reviewer='/root/mafia_cont', source_sha256=hashlib.sha256(NEW.read_bytes()).hexdigest(),
              rejected_source_sha256=hashlib.sha256(OLD.read_bytes()).hexdigest(),
              secondary_receipt_sha256=hashlib.sha256(receipt_bytes).hexdigest(),
              assertions=len(checks), results=checks,
              old_false_green=dict(stored_aliases=sorted(old['secondary_temporaries']),
                                   marker_used=actual_raw, owned_matches=old['owned_matches'],
                                   observed_only_in_synthetic_process_listing=True),
              decision='SAFE TO EXECUTE AFTER ROOT BUILD LANE IDLE for these ownership-attested receipt inputs; not an executed final cleanup PASS.',
              limits=['The path shape regex is not an ownership attestation or general untrusted-input parser; exact supplied receipt ownership reviewed separately.',
                      'No actual filesystem metadata, FIFO content, process, simulator, Gradle, Xcode or device operation was performed by these tests.',
                      'The helper regenerates audit reports and writes its own receipt; it never deletes files or stops processes.',
                      'Point-in-time observations cannot guarantee future process state or absence of unrecorded temporary roots.'],
              cleanup='No build outputs or persistent processes created; only required compact audit evidence retained.')
(HERE / 'secondary-receipt.json').write_text(json.dumps(result, indent=2) + '\n')
print(json.dumps(dict(assertions=len(checks), status='PASS', source_sha256=result['source_sha256'])))
