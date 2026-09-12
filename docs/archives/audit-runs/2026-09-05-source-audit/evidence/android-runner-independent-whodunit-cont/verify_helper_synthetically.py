#!/usr/bin/env python3
"""Independent audit-helper checks. No Gradle/ADB/native/process execution."""
import ast
import datetime
import hashlib
import json
from pathlib import Path
import shutil
import tempfile
import types
import xml.etree.ElementTree as ET

HERE = Path(__file__).resolve().parent
SOURCE = HERE.parents[1] / 'run_android_managed_cycle.py'
EXPECTED_HASH = '74cf484d1e629fbe82ca0e91aa0b36691314044fdbe2d25c0af81f380afdcec1'
data = SOURCE.read_bytes()
assert hashlib.sha256(data).hexdigest() == EXPECTED_HASH
tree = ast.parse(data, filename=str(SOURCE))
names = {'sanitized', 'digest', 'write_json', 'launch_log_writers', 'Ownership', 'collect_evidence'}
nodes = [node for node in tree.body if isinstance(node, (ast.FunctionDef, ast.ClassDef)) and node.name in names]
constants = {node.targets[0].id: ast.literal_eval(node.value) for node in tree.body
             if isinstance(node, ast.Assign) and isinstance(node.targets[0], ast.Name)
             and node.targets[0].id in {'PASSWORD', 'EXPECTED_CASES'}}
ns = dict(Path=Path, hashlib=hashlib, json=json, shutil=shutil, ET=ET, SDK=Path('/synthetic-sdk'), **constants)
ns['os'] = types.SimpleNamespace(getpid=lambda: 700000)
exec(compile(ast.Module(body=nodes, type_ignores=[]), str(SOURCE), 'exec'), ns)
checks = []


def check(name, condition):
    assert condition, name
    checks.append({'name': name, 'status': 'PASS'})


with tempfile.TemporaryDirectory(prefix='owned-fixtures-', dir=HERE) as root_name:
    root = Path(root_name)
    log = root / 'gradle.log'
    log.write_text('Synthetic audit helper fixture only.\n')
    stat = log.stat()
    current_payload = ['']
    calls = []

    def synthetic_run(args, **kwargs):
        check('native-query-is-mocked-' + str(len(calls)), args == ['/usr/sbin/lsof', '-nP', '-w', '-FpfaiD', str(log)])
        calls.append(args)
        return types.SimpleNamespace(stdout=current_payload[0], stderr='', returncode=0)

    ns['subprocess'] = types.SimpleNamespace(run=synthetic_run)

    def fd(pid, descriptor='1', mode='w', inode=stat.st_ino, device=stat.st_dev):
        return f'p{pid}\nf{descriptor}\na{mode}\ni{inode}\nD{device:#x}\n'

    current_payload[0] = fd(700001, mode='r') + fd(700002, descriptor='3') + fd(700003) + fd(700004, descriptor='2', mode='u')
    check('only-exact-stdout-stderr-writers-not-readers', ns['launch_log_writers'](log) == {700003, 700004})
    current_payload[0] = fd(700003, inode=stat.st_ino + 1) + fd(700004, device=stat.st_dev + 1)
    check('wrong-device-or-inode-not-owned', ns['launch_log_writers'](log) == set())
    current_payload[0] = f'p700003\nf1\naw\ni{stat.st_ino + 1}\n'
    check('mismatched-inode-rejects-before-missing-device', ns['launch_log_writers'](log) == set())
    current_payload[0] = f'p700003\nf1\naw\ni{stat.st_ino}\n'
    try:
        ns['launch_log_writers'](log)
        raise AssertionError('missing writable file identity accepted')
    except RuntimeError:
        check('missing-writer-identity-fails-closed', True)
    current_payload[0] = 'p700003\nf1\naw\nUNEXPECTED\n'
    try:
        ns['launch_log_writers'](log)
        raise AssertionError('unknown field accepted')
    except RuntimeError:
        check('unexpected-field-fails-closed', True)

    def proc(pid, command, start='new'):
        return dict(pid=pid, ppid=1, pgid=pid, start=start, command=command)

    baseline = {700005: proc(700005, '/synthetic-jdk/bin/java ExistingViewer', 'old')}
    current = {**baseline, 700001: proc(700001, '/synthetic-jdk/bin/java NewReader'),
               700003: proc(700003, '/synthetic-jdk/bin/java DetachedWriter')}
    ns['snapshot_processes'] = lambda: current
    ns['lsof_pids'] = lambda args: set()
    current_payload[0] = fd(700001, mode='r') + fd(700003) + fd(700005)
    owner = ns['Ownership'](baseline, root, [], root)
    live = owner.refresh(inspect_files=True)
    check('ownership-does-not-adopt-log-reader-or-baseline', {item['pid'] for item in live} == {700003})
    foreign = proc(700006, '/synthetic-bin/unrelated-reader')
    current[700006] = foreign
    ns['lsof_pids'] = lambda args: {700006}
    owner.refresh(inspect_files=True)
    check('unattributed-root-holder-remains-explicit', owner.unknown_holders == [foreign])

    repository = root / 'synthetic-repository'
    ns['ROOT'] = repository
    build = repository / 'composeApp/build'
    reports = build / 'outputs/androidTest-results/managed'
    reports.mkdir(parents=True)
    (build / 'outputs/synthetic.apk').write_bytes(b'synthetic hash fixture, not an APK')
    cases = sorted(ns['EXPECTED_CASES'])

    def collect(label, selected, skip=False, declared=None):
        xml = ET.Element('testsuite', name='synthetic', tests=str(len(selected) if declared is None else declared),
                         failures='0', errors='0', skipped='1' if skip else '0')
        for index, (classname, name) in enumerate(selected):
            case = ET.SubElement(xml, 'testcase', classname=classname, name=name)
            if index == 0 and skip:
                ET.SubElement(case, 'skipped')
        ET.ElementTree(xml).write(reports / 'TEST-synthetic.xml')
        dest = root / ('report-' + label)
        dest.mkdir()
        return ns['collect_evidence'](dest, [build])

    evidence = collect('all', cases)
    check('three-exact-methods-required', evidence['missing_expected_cases'] == [] and evidence['instrumented_cases'] == 3
          and not evidence['report_errors'] and evidence['artifacts_hashed'] == 1)
    evidence = collect('missing', cases[:-1])
    check('missing-instrumented-method-detected', evidence['missing_expected_cases'] == [list(cases[-1])])
    evidence = collect('skip', cases, skip=True)
    check('skipped-method-is-not-passing-evidence', evidence['skipped_cases'] == 1 and bool(evidence['missing_expected_cases']))
    evidence = collect('bad-counters', cases, declared=4)
    check('junit-counter-inconsistency-detected', bool(evidence['report_errors']))
    shared = root / 'synthetic-global-cache'
    shared.mkdir()
    sentinel = shared / 'preserve'
    sentinel.write_text('synthetic user-owned dependency bytes')
    owned = root / 'synthetic-task-home'
    owned.mkdir()
    pointer = owned / 'caches'
    pointer.symlink_to(shared, target_is_directory=True)
    pointer.unlink()
    shutil.rmtree(owned)
    check('unlink-and-rmtree-preserves-cache-target', sentinel.read_text() == 'synthetic user-owned dependency bytes')

check('all-fixtures-cleaned', not Path(root_name).exists())
check('reviewed-helper-unchanged', hashlib.sha256(SOURCE.read_bytes()).hexdigest() == EXPECTED_HASH)
receipt = dict(recorded_at=datetime.datetime.now(datetime.timezone.utc).isoformat(), reviewer='/root/whodunit_cont',
               helper_sha256=EXPECTED_HASH, status='PASS', checks=checks, checks_count=len(checks),
               scope='Pure AST-extracted Python helper behavior, synthetic fixtures, mocked process/FD queries. No app/runtime/device proof.',
               subprocesses_launched=0, gradle_tasks_run=0, native_commands_run=0, generated_fixtures_removed=True)
with (HERE / 'synthetic-receipt.json').open('x') as target:
    json.dump(receipt, target, indent=2)
    target.write('\n')
print(json.dumps(receipt, indent=2))
