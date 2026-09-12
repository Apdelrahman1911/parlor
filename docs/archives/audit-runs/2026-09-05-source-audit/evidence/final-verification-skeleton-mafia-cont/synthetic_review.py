#!/usr/bin/env python3
"""Data-only checks of the frozen audit-ledger classification expressions."""
import ast
import copy
import datetime
import hashlib
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
AUDIT = HERE.parents[1]
SOURCE = HERE / 'assemble_verification.py'
raw = SOURCE.read_bytes()
assert hashlib.sha256(raw).hexdigest() == '293d0d50cbd02a248326f680109f31370efb8bd97d8a7db92848046657628f15'
tree = ast.parse(raw, str(SOURCE))
classification = [n for n in tree.body if 29 <= n.lineno and n.end_lineno <= 55]
runtime_expression = next(n for n in tree.body if isinstance(n, ast.Assign) and
                          any(isinstance(t, ast.Name) and t.id == 'android_runtime_ok' for t in n.targets))
checks = []


def check(name, condition):
    assert condition, name
    checks.append(dict(name=name, status='PASS'))


def classify(cycles, preservation):
    values = dict(cycle_values=copy.deepcopy(cycles), preservation=preservation)
    exec(compile(ast.Module(body=classification, type_ignores=[]), str(SOURCE), 'exec'), values)
    return values


inputs, actual = [], []
for p in sorted((AUDIT / 'evidence').glob('*/receipt.json')):
    b = p.read_bytes()
    value = json.loads(b)
    if isinstance(value, dict):
        actual.append((p, value))
        inputs.append(dict(path=p.relative_to(AUDIT).as_posix(), sha256=hashlib.sha256(b).hexdigest()))
real = classify(actual, {})
counts = {name: len(real[name]) for name in ['direct_cycles', 'android_cycles', 'android_executed', 'android_prebuild', 'apphost_cycles']}
check('current frozen snapshot has16direct/2Android/1executed/1prebuild/0apphost',
      counts == dict(direct_cycles=16, android_cycles=2, android_executed=1, android_prebuild=1, apphost_cycles=0))
check('all current direct cleanup fields reconcile', real['direct_clean'])
check('both Android attempt cleanup dispositions reconcile', real['android_clean'])
check('missing final hygiene never passes aggregate cleanup', not real['cleanup_ok'])

direct = dict(command=['./gradlew'], cleanup_method='synthetic exact outputs', stop_exit_code=0,
              cleanup_errors=[], remaining_outputs=[])
owned = dict(cleanup_status='PASS', cleanup_errors=[], remaining_outputs=[],
             temporary_directory_removed=True, owned_processes_remaining=[])
android = dict(owned, execution_kind='gradle-via-checked-in-disposable-signing-harness',
               owned_build_pid=100, stop_exit_code=0)
prebuild = dict(owned, execution_kind='gradle-via-checked-in-disposable-signing-harness',
                stop_not_required='Synthetic: no Gradle launched')
apphost = dict(owned, execution_kind='source-aware-unsigned-ios-apphost-diagnostic',
               gradle_stops=[dict(exit_code=0)])
base = [('direct', direct), ('android', android), ('prebuild', prebuild), ('apphost', apphost)]
check('complete synthetic types and final hygiene pass', classify(base, dict(cleanup_status='PASS'))['cleanup_ok'])


def amended(kind, **delta):
    return [(p, dict(v, **delta) if p == kind else dict(v)) for p, v in base]


for name, key, value in [
    ('nonzero direct stop', 'stop_exit_code', 1),
    ('missing direct errors field', 'cleanup_errors', None),
    ('remaining direct output', 'remaining_outputs', ['synthetic/build']),
]:
    check(name + ' blocks', not classify(amended('direct', **{key: value}), dict(cleanup_status='PASS'))['cleanup_ok'])
check('nonzero Android stop blocks', not classify(amended('android', stop_exit_code=1), dict(cleanup_status='PASS'))['cleanup_ok'])
for key, value in [
    ('cleanup_status', 'BLOCKED'), ('cleanup_errors', ['synthetic']),
    ('remaining_outputs', ['synthetic/build']), ('temporary_directory_removed', False),
    ('owned_processes_remaining', [dict(pid=123)]),
    ('unattributed_open_file_holders', [dict(pid=123)]),
    ('unknown_holders', [dict(pid=123)]),
    ('owned_uuid', 'synthetic-device-without-absence-proof'),
]:
    check('Android owned cleanup blocks ' + key,
          not classify(amended('android', **{key: value}), dict(cleanup_status='PASS'))['cleanup_ok'])
check('ambiguous prebuild Android phase blocks',
      not classify(amended('prebuild', stop_not_required=None), dict(cleanup_status='PASS'))['cleanup_ok'])
check('missing iOS stops block',
      not classify(amended('apphost', gradle_stops=[]), dict(cleanup_status='PASS'))['cleanup_ok'])
check('nonzero iOS stop blocks',
      not classify(amended('apphost', gradle_stops=[dict(exit_code=0), dict(exit_code=1)]), dict(cleanup_status='PASS'))['cleanup_ok'])
check('owned simulator without absence proof blocks',
      not classify(amended('apphost', owned_uuid='synthetic-uuid'), dict(cleanup_status='PASS'))['cleanup_ok'])
check('owned simulator with absence proof accepted',
      classify(amended('apphost', owned_uuid='synthetic-uuid', owned_device_absent=True), dict(cleanup_status='PASS'))['cleanup_ok'])


def runtime(result, validated):
    values = dict(android_result=result, android_validated=validated)
    exec(compile(ast.Module(body=[runtime_expression], type_ignores=[]), str(SOURCE), 'exec'), values)
    return values['android_runtime_ok']


result = json.loads((AUDIT / 'evidence/android-managed-02/receipt.json').read_text())
validator = json.loads((AUDIT / 'evidence/android-managed-02-independent-whodunit-cont.json').read_text())
check('actual Android status fields qualify narrow runtime gate', runtime(result, validator))
check('missing independent validation blocks runtime gate', not runtime(result, {}))
check('failed independent validation blocks runtime gate', not runtime(result, dict(verdict='FAIL')))
check('nonzero command exit blocks runtime gate', not runtime(dict(result, exit_code=1), validator))
check('non-PASS runtime evidence blocks runtime gate', not runtime(dict(result, runtime_evidence_status='BLOCKED'), validator))

# This records the skeleton limitation, not an application failure or a claim
# that the real receipt changed. Root receives the recommendation separately.
changed = dict(result, verification=dict(result['verification'], instrumented_cases=999))
stale_accepted = runtime(changed, validator)
check('stale-validation limitation independently demonstrated with synthetic changed receipt', stale_accepted)
bound = next(x['sha256'] for x in validator['evidence_hashes']
             if x['path'].endswith('/android-managed-02/receipt.json'))
actual_receipt_hash = hashlib.sha256((AUDIT / 'evidence/android-managed-02/receipt.json').read_bytes()).hexdigest()
check('actual Android receipt still matches independent validation hash', actual_receipt_hash == bound)

record = dict(recorded_at=datetime.datetime.now(datetime.timezone.utc).isoformat(),
              reviewer='/root/mafia_cont', source_sha256=hashlib.sha256(raw).hexdigest(),
              scope='Only selected classification functions/assignments executed; no complete ledger generator or report writes.',
              input_receipt_hashes=inputs, current_cycle_counts=counts,
              assertions=len(checks), results=checks,
              observations=['Current completed Android evidence agrees; final aggregate intentionally remains BLOCKED without final hygiene.',
                            'A pre-Gradle apphost attempt would remain conservatively blocked until explicit no-stop-required handling is added.',
                            'The skeleton Android gate uses status fields, not current receipt hash versus independent validator binding; synthetic stale acceptance observed, current bytes match.'],
              cleanup='No Gradle/native/device/process calls or build outputs; only compact audit source/test/JSON evidence retained.')
(HERE / 'synthetic-receipt.json').write_text(json.dumps(record, indent=2) + '\n')
print(json.dumps(dict(assertions=len(checks), status='PASS', current_cycle_counts=counts)))
