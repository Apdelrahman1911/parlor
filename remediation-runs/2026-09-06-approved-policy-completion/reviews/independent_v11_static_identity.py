"""Read-only source/hash/AST arithmetic. Never imports/runs harness, tests or builds."""
import ast
import datetime
import difflib
import hashlib
import json
from pathlib import Path
import subprocess

ROOT = Path('/Users/abdelrahman/Projects/parlor')
BASE = ROOT / 'remediation-runs/2026-09-06-approved-policy-completion'
HERE = BASE / 'native/dsc01_apphost_v11'
OLD = HERE.parent / 'dsc01_apphost_v10'
REVIEW = BASE / 'reviews'
EXPECTED = '87b924da2d8b39f6c965d4d94321282e130faf30d5dcffe91a723273bb9505e5'


def sha(data):
    return hashlib.sha256(data).hexdigest()


def record(path):
    return dict(path=str(path.relative_to(ROOT)), sha256=sha(path.read_bytes()))


def require(ok, message):
    if not ok:
        raise RuntimeError(message)


def methods(path):
    source = path.read_text()
    result = {}
    for node in ast.parse(source).body:
        if isinstance(node, ast.ClassDef):
            for method in node.body:
                if isinstance(method, (ast.FunctionDef, ast.AsyncFunctionDef)) and method.name.startswith('test_'):
                    key = path.stem + '.' + node.name + '.' + method.name
                    require(key not in result, 'Duplicate static test name: ' + key)
                    result[key] = sha(ast.get_source_segment(source, method).encode())
    return result


expected_files = {
    'author-frozen-control-manifest-01.json': 'fe83be618f34745e58d67b5631f0050cfff14ea83f882cd734af00a791df6ff9',
    'source-bindings.json': '50bcbd098ed51cf310821e3aa679dbe094bf043a08613f03e3f2c22aecccb263',
    'v10-to-v11-controls-final-01.diff': '437f261395e6258aa566dcce4bd80d2af3c6e6aa7940dcbd9a73e5c101bde14f',
    'author-prebinding-static-inspection-01.json': '20f11f459f3147a21046420eea6c211eda8f639623e97fd8c7cf3ccb3a3cd8e6',
    'DSC01OSAppSettings.swift.in': 'b0f427d976f886e2f092dc0c59752f0fe010316f7dd7621f8c08b3c563b34210',
    'v11_receipts.py': '8307a118d414900fbf43399b569ac066c687470f8ac7fb74ce4fadb1b73b7f7b',
    'test_v11_contract.py': 'c555f583205c2fa288be9334eb24f5eb50a4fa55161c13c43fdb36f01979f4f0',
    'run_dsc01_apphost_cycle.py': '29a579cd7b1908786d3a9de21a3cf6b43365a51de1360ad99673cdc550cf0889',
    'README.md': '5e7c9dafcf189738040756d7e0cfb4f1f53e3cfd40a7739c3487c34d499f9da7',
}
for name, expected in expected_files.items():
    require(record(HERE / name)['sha256'] == expected, 'Previously read frozen file changed: ' + name)

frozen = json.loads((HERE / 'author-frozen-control-manifest-01.json').read_bytes())
runner = (HERE / 'run_dsc01_apphost_cycle.py').read_text()
old_runner = (OLD / 'run_dsc01_apphost_cycle.py').read_text()
node = next(n for n in ast.parse(runner).body if isinstance(n, ast.FunctionDef) and n.name == 'control_files')
# Only this completely reopened pure Path/list helper is evaluated. No harness
# imports, main, process helpers, application methods or tests are executed.
namespace = {'Path': Path, '__file__': str(HERE / 'run_dsc01_apphost_cycle.py'), 'HERE': HERE, 'RUN': BASE, 'ROOT': ROOT}
exec(compile(ast.Module(body=[node], type_ignores=[]), '<isolated-pure-path-expression>', 'exec'), namespace)
paths = namespace['control_files']()
require(len(paths) == len(set(paths)) == 137, 'Control graph cardinality changed')
require(all(p.is_file() and not p.is_symlink() for p in paths), 'Missing/symlink control')
controls = [record(p) for p in paths]
control_sha = sha(json.dumps(controls, separators=(',', ':')).encode())
require(controls == frozen['files'] and control_sha == frozen['control_sha256'] == EXPECTED, 'Frozen137control mismatch')

freeze = json.loads((BASE / 'source-freeze-02.json').read_bytes())
binding = json.loads((HERE / 'source-bindings.json').read_bytes())
require(binding['source_identity'] == freeze['source'], 'Full source binding differs from freeze02')
rows = freeze['source']['source_manifest']
require(len(rows) == len({r[0] for r in rows}) == 669, 'Source manifest count/uniqueness changed')
for path, expected in rows:
    p = ROOT / path
    require(p.is_file() and not p.is_symlink() and sha(p.read_bytes()) == expected, 'Frozen source changed: ' + path)
require(sha(json.dumps(rows, separators=(',', ':')).encode()) == freeze['source']['source_manifest_sha256'], 'Source manifest digest differs')
require(len(binding['copy_only']) == 613, 'Copy input count changed')
for row in binding['copy_only']:
    require(record(ROOT / row['path']) == row, 'Copy binding changed: ' + row['path'])

def git(*args):
    return subprocess.check_output(['git'] + list(args), cwd=ROOT)
for args, expected in [(('rev-parse', 'HEAD'), freeze['source']['commit']),
                       (('rev-parse', 'HEAD^{tree}'), freeze['source']['tree']),
                       (('branch', '--show-current'), freeze['source']['branch'])]:
    require(git(*args).decode().strip() == expected, 'Git source identity differs: ' + str(args))
require(sha(git('diff', '--binary', 'HEAD')) == freeze['source']['diff_sha256'], 'Tracked diff changed')
require(git('status', '--short', '--untracked-files=no').decode().strip() == freeze['source']['tracked_status'], 'Tracked status differs')
old_binding = json.loads((OLD / 'source-bindings.json').read_bytes())
normalized = dict(binding, created_at=old_binding['created_at'])
require(normalized == old_binding, 'Binding difference beyond timestamp')

refs = [p for p in paths if p.parent == OLD]
require(len(refs) == 28, 'V10 reference graph differs')
for p in refs:
    if p.name not in {'run_dsc01_apphost_cycle.py', 'DSC01OSAppSettings.swift.in'}:
        require(p.read_bytes() == (HERE / p.name).read_bytes(), 'V10 immutable original changed: ' + p.name)
old_approval = json.loads((REVIEW / 'independent-v10-frozen-focused-approval-01.json').read_bytes())
old_manifest = json.loads((OLD / 'author-frozen-control-manifest-01.json').read_bytes())
for row in old_manifest['files']:
    require(record(ROOT / row['path']) == row, 'Previously approved V10 control changed')
require(old_approval['control_manifest_sha256'] == old_manifest['control_sha256'], 'Prior review/control binding differs')

marker = '        finally:\n            deferred = []'
tail = runner[runner.index(marker):]
require(tail == old_runner[old_runner.index(marker):], 'Entire finalizer changed')
require(sha(tail.encode()) == '7b1c9a2faa58be765205acfae064d203b896069fd382c1f2bfb1923ceac032c0', 'Finalizer digest differs')
helper = (HERE / 'DSC01OSAppSettings.swift.in').read_text()
old_helper = (OLD / 'DSC01OSAppSettings.swift.in').read_text()
start = '    @MainActor private func osRowObservation('
end = '    @MainActor private func verifyOSInteractionDecisionContract('
actions = helper[helper.index(start):helper.index(end)]
require(actions == old_helper[old_helper.index(start):old_helper.index(end)], 'Target/action/sampling/reporting changed')
require(sha(actions.encode()) == 'fd6e1fc6f550dae103bfd7f070fe4a0bb4d382f6caaec7e776340fffccf8d355', 'Action-region digest differs')
old_nodes = {n.name: n for n in ast.parse(old_runner).body if isinstance(n, (ast.FunctionDef, ast.ClassDef))}
new_nodes = {n.name: n for n in ast.parse(runner).body if isinstance(n, (ast.FunctionDef, ast.ClassDef))}
require(set(old_nodes) == set(new_nodes), 'Runner declaration graph differs')
for name in old_nodes.keys() - {'control_files', 'main'}:
    require(ast.get_source_segment(old_runner, old_nodes[name]) == ast.get_source_segment(runner, new_nodes[name]), 'Unrelated runner declaration differs: ' + name)

old_methods = {}; new_methods = {}
for path in sorted(OLD.glob('test_*.py')):
    old_methods.update(methods(path))
for path in sorted(HERE.glob('test_*.py')):
    new_methods.update(methods(path))
require(len(old_methods) == 168 and len(new_methods) == 177, 'Static discovery counts differ')
require(all(new_methods.get(k) == v for k, v in old_methods.items()), 'Existing method body changed')
require(old_methods == old_approval['method_identities'], 'Prior approved168method identities differ')
for p in OLD.glob('test_*.py'):
    require(p.read_bytes() == (HERE / p.name).read_bytes(), 'Existing full test module changed')

names = ['DSC01OSAppSettings.swift.in', 'run_dsc01_apphost_cycle.py', 'source-bindings.json', 'test_v11_contract.py', 'v11_receipts.py']
delta = ''
for name in names:
    old = OLD / name; new = HERE / name
    delta += ''.join(difflib.unified_diff(old.read_text().splitlines(True) if old.exists() else [], new.read_text().splitlines(True),
                                        fromfile=str(old.relative_to(ROOT)) if old.exists() else '/dev/null',
                                        tofile=str(new.relative_to(ROOT))))
retained = (HERE / 'v10-to-v11-controls-final-01.diff').read_text()
require(delta == retained and len(delta.splitlines()) == 394, 'Complete final delta mismatch')

receipt_path = BASE / 'evidence/native-binding-11/receipt.json'
require(record(receipt_path)['sha256'] == 'c8bfdcfd3510466badca70e615c901ea9e968abd180385fa800f71e9c5d25353', 'Binding11receipt changed')
receipt = json.loads(receipt_path.read_bytes())
require(receipt['source_before'] == receipt['source_after'] == freeze['source'], 'Binding11source identity mismatch')
require(receipt['runner_before'] == receipt['runner_after'], 'Binding11runner changed')
for path, expected in receipt['runner_before']['manifest']:
    require(sha((ROOT / path).read_bytes()) == expected, 'Binding11runner now changed')
require(receipt['exit_code'] == receipt['stop_exit_code'] == 0 and receipt['status'] == 'PASS', 'Binding11failed')
require(not any(receipt[k] for k in ['outputs_before', 'remaining_outputs', 'retained_outputs', 'cleanup_errors', 'deferred_signals']), 'Binding11cleanup issue')
require(not receipt['workers']['remaining_owned_workers'], 'Binding11remainingworker reported')
require(receipt['finished_at'] < receipt['stopped_at'] < receipt['cleanup_completed_at'], 'Binding11cleanup chronology differs')

report = {
    'schema_version': 1,
    'reviewer': '/root/release_fix_review',
    'author': '/root/native_fix_review',
    'recorded_at': datetime.datetime.now(datetime.timezone.utc).isoformat(),
    'status': 'APPROVED_FOR_ROOT_FOCUSED_CONTROLS_ONLY',
    'native_execution_approval': False,
    'method': 'Complete400line helper, complete153line new tests, complete66line parser, complete46line README and all394final delta lines read; reopened full control-expression/gating/finalizer. Prior full V10runner review plus exact reconstructed delta accounts for V11runner; AST/byte/hash arithmetic only, no harness imports/tests/builds.',
    'control_manifest_sha256': control_sha,
    'control_count_independently_rehashed': len(controls),
    'frozen_manifest': record(HERE / 'author-frozen-control-manifest-01.json'),
    'source_binding': record(HERE / 'source-bindings.json'),
    'binding_receipt': dict(record(receipt_path), source_and_runner_unchanged=True, exit_code=0, stop_exit_code=0, cleanup_errors=[]),
    'source_manifest_sha256': freeze['source']['source_manifest_sha256'],
    'source_inputs_independently_rehashed': len(rows),
    'copy_inputs_independently_rehashed': len(binding['copy_only']),
    'commit': freeze['source']['commit'], 'tree': freeze['source']['tree'],
    'tracked_diff_sha256': freeze['source']['diff_sha256'],
    'delta': dict(record(HERE / 'v10-to-v11-controls-final-01.diff'), lines=394, reviewed_ranges=[[1,394]], byte_identical_independent_reconstruction=True),
    'read_coverage': [dict(record(HERE / name), lines=len((HERE / name).read_text().splitlines()), reviewed_ranges=[[1,len((HERE / name).read_text().splitlines())]]) for name in ['DSC01OSAppSettings.swift.in', 'v11_receipts.py', 'test_v11_contract.py', 'README.md']],
    'runner_coverage': dict(record(HERE / 'run_dsc01_apphost_cycle.py'), lines=841, unchanged_v10_full_read_reference=record(REVIEW / 'independent-v10-frozen-focused-approval-01.json'), full_delta_read=True, directly_reopened_ranges=[[1,170],[695,841]], every_unchanged_declaration_byte_compared=True),
    'immutable_original_v10_references': 26,
    'original_actions_sampling_reporting_identical': dict(sha256=sha(actions.encode()), start_line=helper[:helper.index(start)].count('\n')+1, end_line=helper[:helper.index(end)].count('\n')),
    'finalizer_identical': dict(sha256=sha(tail.encode()), v11_start_line=runner[:runner.index(marker)].count('\n')+1),
    'expected_test_methods_static_only': len(new_methods),
    'original_test_method_bodies_byte_identical': len(old_methods),
    'new_test_methods_static_only': len(new_methods)-len(old_methods),
    'method_identities': new_methods,
    'research': [record(REVIEW / name) for name in ['independent-v11-exact-identifier-research-02.json', 'independent-v11-pinned-xctest-declarations-01.json']],
    'review_conclusions': [
        'Exact pane union follows all five documented XCUI identifying properties, not an invented identifier-only semantic. Only allowlisted actual attribute/literal matches are logged.',
        'Union counts elements once, not matching aliases/properties; ambiguous or stale identities fail existing geometric/foreground guards.',
        'Original target/action selectors, all stable samples, one-action routing, terminal reporting, and post-OS Arabic/System/restart assertions remain unchanged.',
        'Additional provenance parser supplements unchanged V10/probe gates and explicitly disclaims actual OS-selection authority. A BLOCKED OS gate cannot be replaced by synthetic/provenance PASS.',
        'No source-level blocking concern remains for root focused177method execution on this exact control/source identity.'
    ],
    'limitations': [
        '177methods are static expectations only, not executed/passing tests. Root must provide raw focused descriptors/results and cleanup for independent review before separate native GO.',
        'This bounded review is not a fresh reread of all137inherited controls or669application inputs. Unchanged inherited code is identity-bound to prior reviews.',
        'Native decision cases are24old plus12attribute/two union/two identityguard cases inside one of five XCTest methods, not40XCTest methods.',
        'The actual property producing apphost08matchingSettings diagnostic has not been observed. Corrected query still does not prove public Language availability or OS selection.',
        'README is independently read but is not in the executable control graph. It is a proposal/binding note, not a runtime result.',
        'Author proposal metadata raw08witness section was not fully line-by-line read; independent raw08and authoritative research evidence separately establishes the bounded selector claim.',
        'Artifact receipt still omits Parlor.debug.dylib; binary-artifact provenance cannot be called complete.',
        'DS-C01 remains PARTIALLY_VERIFIED until original real OS English→appArabic→System→restart oracles execute successfully. No physical-device, LAN, Store-qualified Xcode, signing or Store claim.'
    ],
    'cleanup': dict(reviewer_builds_tests_apps_started=0, reviewer_background_workers_started=0, generated_build_outputs_created=0, other_tasks_resources_touched=False)
}
path = REVIEW / 'independent-v11-frozen-focused-approval-01.json'
with path.open('x') as f:
    f.write(json.dumps(report, indent=2, ensure_ascii=False) + '\n')
print(json.dumps(dict(report=str(path), sha256=sha(path.read_bytes()), status=report['status'], control_sha256=control_sha, static_test_methods=len(new_methods))))
