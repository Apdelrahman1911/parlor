#!/usr/bin/env python3
"""AST-only runner tests. Never imports/runs the orchestration or calls build/native tools."""
import ast
import copy
from contextlib import contextmanager
import datetime
import hashlib
import json
from pathlib import Path
import re
import sys
from types import SimpleNamespace

BASE = Path(__file__).resolve().parent
ROOT = BASE.parents[3]
OUT = ROOT / 'audit-runs/2026-09-05-source-audit'
source = Path(sys.argv[1]).resolve()
source.relative_to(BASE)
raw = source.read_bytes()
module = ast.parse(raw.decode())
checks = []


def check(name, operation):
    operation()
    checks.append(name)


def must_reject(fn, *args):
    try:
        fn(*args)
    except (RuntimeError, TypeError, KeyError, ValueError):
        return
    raise AssertionError('expected rejection')


def require(condition):
    assert condition


ns = {'re': re, 'contextmanager': contextmanager}
select = {'defer_parent_signals', 'apply_one_file_patch', 'verify_xctest', 'verify_probe'}
functions = [n for n in module.body if isinstance(n, ast.FunctionDef) and n.name in select]
assert {n.name for n in functions} == select
ns['EXPECTED_TEST'] = 'IOSAppLaunchUITests/testAuditProductionRecoverySourceAttribution()'
exec(compile(ast.Module(body=functions, type_ignores=[]), str(source), 'exec'), ns)
check('whole_runner_parses_as_python_without_execution', lambda: compile(raw.decode(), str(source), 'exec'))

fixture = OUT / 'reproducers/iosr1_apphost'
for filename, patchname in [('ContentView.swift', 'ContentView.swift.patch.in'), ('IOSAppLaunchUITests.swift', 'IOSAppLaunchUITests.swift.patch.in')]:
    subdirectory = 'iosApp' if filename == 'ContentView.swift' else 'iosAppUITests'
    path = ROOT / 'iosApp' / subdirectory / filename
    original = path.read_text()
    patch = (fixture / patchname).read_text()
    updated = ns['apply_one_file_patch'](original, patch)
    check(filename + ': exact patch adds audit trigger/test', lambda text=updated: require('parlor-audit-iosr1' in text))
    check(filename + ': context drift rejects', lambda old=original, p=patch: must_reject(ns['apply_one_file_patch'], old.replace('import ', 'BROKEN import ', 1), p))
    check(filename + ': malformed hunk rejects', lambda old=original, p=patch: must_reject(ns['apply_one_file_patch'], old, p.replace('@@ -', '@@ x', 1)))
    if filename == 'ContentView.swift':
        suffix = original[original.index('    private func reportScenePhase'):]
        check('original Swift lifecycle/controller/privacy callbacks remain byte-equivalent', lambda: require(updated.endswith(suffix)))
        check('original privacy cover remains', lambda: require('if scenePhase != .active {\n                Color.black\n                    .accessibilityHidden(true)\n            }' in updated))

uuid = '00000000-0000-0000-0000-000000000001'
device = {'deviceId': uuid, 'architecture': 'arm64', 'platform': 'iOS Simulator'}
summary = {'result': 'Passed', 'totalTestCount': 1, 'passedTests': 1, 'failedTests': 0, 'skippedTests': 0, 'expectedFailures': 0, 'testFailures': [], 'devicesAndConfigurations': [{'device': device}]}
case = {'nodeType': 'Test Case', 'nodeIdentifier': ns['EXPECTED_TEST'], 'result': 'Passed'}
tests = {'devices': [device], 'testNodes': [{'nodeType': 'Test Bundle', 'children': [{'nodeType': 'Test Class', 'children': [case]}]}]}
check('exact owned-device one-method XCTest accepted', lambda: ns['verify_xctest'](summary, tests, uuid))
for key, value in [('totalTestCount', 0), ('passedTests', 0), ('failedTests', 1), ('skippedTests', 1), ('expectedFailures', 1), ('result', 'Failed'), ('totalTestCount', '1')]:
    altered = copy.deepcopy(summary); altered[key] = value
    check('XCTest rejects ' + key + '=' + str(value), lambda altered=altered: must_reject(ns['verify_xctest'], altered, tests, uuid))
for label, alter in [
    ('another method', lambda value: value['testNodes'][0]['children'][0]['children'][0].update(nodeIdentifier='Other/test()')),
    ('duplicate case', lambda value: value['testNodes'][0]['children'][0]['children'].append(copy.deepcopy(case))),
    ('skipped actual case', lambda value: value['testNodes'][0]['children'][0]['children'][0].update(result='Skipped')),
    ('wrong device UUID', lambda value: value['devices'][0].update(deviceId='different')),
    ('wrong architecture', lambda value: value['devices'][0].update(architecture='x86_64')),
    ('real-device platform', lambda value: value['devices'][0].update(platform='iOS')),
]:
    altered = copy.deepcopy(tests); alter(altered)
    check('XCTest rejects ' + label, lambda altered=altered: must_reject(ns['verify_xctest'], summary, altered, uuid))

healthy = {'schema_version': 1, 'probe_kind': 'production_koin_rerun', 'harness_status': 'observation_complete', 'original_home_invocation_intercepted': False, 'local': {'kind': 'success_empty', 'entry_count': 0}, 'multiplayer': {'kind': 'success_null'}, 'combined': {'has_unavailable_source': False, 'local_count': 0, 'has_multiplayer': False}, 'native_corroboration': {'kind': 'not_applicable_to_result'}}
failed = copy.deepcopy(healthy)
failed['multiplayer'] = {'kind': 'failure', 'error_category': 'secure_storage_unavailable'}
failed['combined']['has_unavailable_source'] = True
failed['native_corroboration'] = {'origin': 'subsequent_same_app_equivalent_read', 'original_production_status': False, 'os_status': -34018, 'result_present': False, 'constant_not_found': -25300, 'constant_missing_entitlement': -34018}
check('healthy empty/null production observation accepted', lambda: ns['verify_probe'](healthy))
check('source failure accepted as completed observation, not healthy result', lambda: ns['verify_probe'](failed))
local_failure = copy.deepcopy(healthy)
local_failure['local'] = {'kind': 'failure', 'error_category': 'io_error'}
local_failure['combined']['has_unavailable_source'] = True
check('local source failure with inapplicable native read accepted', lambda: ns['verify_probe'](local_failure))
for label, seed, alter in [
    ('unexpected secret-shaped field', healthy, lambda v: v.update(secret='synthetic')),
    ('another schema', healthy, lambda v: v.update(schema_version=2)),
    ('harness abort', healthy, lambda v: v.update(harness_status='aborted')),
    ('claimed original interception', healthy, lambda v: v.update(original_home_invocation_intercepted=True)),
    ('nonempty local list', healthy, lambda v: v['local'].update(entry_count=1)),
    ('non-null multiplayer', healthy, lambda v: v['multiplayer'].update(kind='success_value')),
    ('false combined availability', failed, lambda v: v['combined'].update(has_unavailable_source=False)),
    ('Boolean combined local count', healthy, lambda v: v['combined'].update(local_count=False)),
    ('native skipped despite its precise source guard', failed, lambda v: v.update(native_corroboration={'kind': 'not_applicable_to_result'})),
    ('native executed outside guard', healthy, lambda v: v.update(native_corroboration=copy.deepcopy(failed['native_corroboration']))),
    ('native OSStatus string', failed, lambda v: v['native_corroboration'].update(os_status='-34018')),
    ('native secret result present', failed, lambda v: v['native_corroboration'].update(result_present=True)),
    ('native attributed to original query', failed, lambda v: v['native_corroboration'].update(original_production_status=True)),
]:
    altered = copy.deepcopy(seed); alter(altered)
    check('probe rejects ' + label, lambda altered=altered: must_reject(ns['verify_probe'], altered))

# No real signals or processes: call the temporary handler through a pure fake.
deliveries = []
old_handlers = {2: lambda sig, _: deliveries.append(('old_int', sig)), 15: lambda sig, _: deliveries.append(('old_term', sig))}
handlers = dict(old_handlers)

def install(sig, handler):
    old = handlers[sig]; handlers[sig] = handler; return old

ns['signal'] = SimpleNamespace(SIGINT=2, SIGTERM=15, SIG_IGN=1, signal=install)
with ns['defer_parent_signals']():
    handlers[2](2, None); handlers[15](15, None)
    check('signal delivery deferred until parent registration can finish', lambda: require(not deliveries))
check('parent handlers restored after atomic allocation/registration', lambda: require(handlers == old_handlers))
check('deferred signals forwarded after protected operation', lambda: require(deliveries == [('old_int', 2), ('old_term', 15)]))

binding = json.loads((fixture / 'source-bindings.json').read_text())
for entry in binding['original_files'] + binding['additive_kotlin_files']:
    p = ROOT / entry['path']
    check('bound hash: ' + entry['path'], lambda p=p, entry=entry: require(not p.is_symlink() and hashlib.sha256(p.read_bytes()).hexdigest() == entry['sha256']))
manifest = json.loads((fixture / 'copy-adjustments.json').read_text())
check('wrapper allowlist contains exactly18 unique relative repository files', lambda: require(len(manifest['copy_only']) == len({e['path'] for e in manifest['copy_only']}) == 18 and all(not Path(e['path']).is_absolute() and '..' not in Path(e['path']).parts for e in manifest['copy_only'])))

receipt = {'at': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'runner_snapshot': str(source.relative_to(ROOT)), 'runner_sha256': hashlib.sha256(raw).hexdigest(), 'synthetic_script_sha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(), 'status': 'PASS', 'checks': len(checks), 'cases': checks, 'limitations': 'Pure function/AST and synthetic data only; no actual subprocess, signal, Gradle, Xcode, simulator, device, app, Keychain, or production write. Ownership safety tested separately.', 'cleanup': 'No temporary/build outputs or processes created; evidence-only JSON retained.'}
name = 'pure-synthetic-' + hashlib.sha256(raw).hexdigest()[:12] + '.json'
(BASE / name).write_text(json.dumps(receipt, indent=2) + '\n')
print(json.dumps({'status': receipt['status'], 'checks': len(checks), 'evidence': str((BASE / name).relative_to(ROOT))}, indent=2))
