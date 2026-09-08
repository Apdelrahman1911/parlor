"""Read-only source/hash/AST arithmetic; does not import or run application/tests."""
import ast
import datetime
import difflib
import hashlib
import json
from pathlib import Path
import subprocess

ROOT = Path('/Users/abdelrahman/Projects/parlor')
BASE = ROOT / 'remediation-runs/2026-09-06-approved-policy-completion'
HERE = BASE / 'native/dsc01_apphost_v10'
OLD = HERE.parent / 'dsc01_apphost_v9'
REVIEW = BASE / 'reviews'
EXPECTED = 'b283a47498a024538b6aed6dfa9cc75bea04f6281fdf2bd210ea07b0a9c6c0f0'


def sha(data):
    return hashlib.sha256(data).hexdigest()


def record(path):
    data = path.read_bytes()
    return {'path': str(path.relative_to(ROOT)), 'sha256': sha(data)}


def require(ok, message):
    if not ok:
        raise RuntimeError(message)


def methods(path):
    text = path.read_text()
    result = {}
    for node in ast.parse(text).body:
        if isinstance(node, ast.ClassDef):
            for method in node.body:
                if isinstance(method, (ast.FunctionDef, ast.AsyncFunctionDef)) and method.name.startswith('test_'):
                    result[path.stem + '.' + node.name + '.' + method.name] = sha(ast.get_source_segment(text, method).encode())
    return result


frozen = json.loads((HERE / 'author-frozen-control-manifest-01.json').read_bytes())
runner = (HERE / 'run_dsc01_apphost_cycle.py').read_text()
runner_tree = ast.parse(runner)
control_node = next(n for n in runner_tree.body if isinstance(n, ast.FunctionDef) and n.name == 'control_files')
# Evaluate only the reopened pure Path/list-expression helper. No imports, test
# functions, runner main, process helper or application code are evaluated.
namespace = {'Path': Path, '__file__': str(HERE / 'run_dsc01_apphost_cycle.py'), 'HERE': HERE, 'RUN': BASE, 'ROOT': ROOT}
exec(compile(ast.Module(body=[control_node], type_ignores=[]), '<isolated-pure-control-path-expression>', 'exec'), namespace)
paths = namespace['control_files']()
require(len(paths) == len(set(paths)) == 107, 'Control count/uniqueness changed')
require(all(p.is_file() and not p.is_symlink() for p in paths), 'Missing/symlink control')
current = [record(p) for p in paths]
control_sha = sha(json.dumps(current, separators=(',', ':')).encode())
require(current == frozen['files'] and control_sha == frozen['control_sha256'] == EXPECTED, 'Frozen control mismatch')

freeze = json.loads((BASE / 'source-freeze-02.json').read_bytes())
binding = json.loads((HERE / 'source-bindings.json').read_bytes())
require(freeze['source'] == binding['source_identity'], 'Binding does not match complete source freeze')
source_rows = freeze['source']['source_manifest']
require(len(source_rows) == 669 and len({r[0] for r in source_rows}) == 669, 'Source manifest count changed')
for path, expected in source_rows:
    p = ROOT / path
    require(p.is_file() and not p.is_symlink() and sha(p.read_bytes()) == expected, 'Frozen source mismatch: ' + path)
for row in binding['copy_only']:
    p = ROOT / row['path']
    require(record(p) == row, 'Copied source binding mismatch: ' + row['path'])
require(len(binding['copy_only']) == 613, 'Unexpected input-copy size')
require(sha(json.dumps(source_rows, separators=(',', ':')).encode()) == freeze['source']['source_manifest_sha256'], 'Source manifest digest mismatch')
def git(*args):
    return subprocess.check_output(['git'] + list(args), cwd=ROOT)
require(git('rev-parse', 'HEAD').decode().strip() == freeze['source']['commit'], 'HEAD changed')
require(git('rev-parse', 'HEAD^{tree}').decode().strip() == freeze['source']['tree'], 'Tree changed')
require(git('branch', '--show-current').decode().strip() == freeze['source']['branch'], 'Branch changed')
require(sha(git('diff', '--binary', 'HEAD')) == freeze['source']['diff_sha256'], 'Tracked diff changed')

old_binding = json.loads((OLD / 'source-bindings.json').read_bytes())
fresh_binding = dict(binding)
fresh_binding['created_at'] = old_binding['created_at']
require(fresh_binding == old_binding, 'Unexpected V9 to V10 binding difference')
prebinding = json.loads((HERE / 'author-prebinding-static-inspection-01.json').read_bytes())
refs = [p for p in paths if p.parent == OLD]
require(len(refs) == 24, 'Wrong immutable V9 reference graph')
for p in refs:
    if p.name != 'run_dsc01_apphost_cycle.py':
        require(p.read_bytes() == (HERE / p.name).read_bytes(), 'V9 original changed: ' + p.name)
old_runner = (OLD / 'run_dsc01_apphost_cycle.py').read_text()
marker = '        finally:\n            deferred = []'
tail = runner[runner.index(marker):]
require(tail == old_runner[old_runner.index(marker):], 'Cleanup finalizer changed')
require(sha(tail.encode()) == '7b1c9a2faa58be765205acfae064d203b896069fd382c1f2bfb1923ceac032c0', 'Unexpected finalizer identity')

testfiles = sorted(HERE.glob('test_*.py'))
discovery = {}
for path in testfiles:
    discovery.update(methods(path))
old_discovery = {}
for path in sorted(OLD.glob('test_*.py')):
    old_discovery.update(methods(path))
require(len(discovery) == 168 and len(old_discovery) == 148, 'Static method counts changed')
require(all(discovery.get(k) == v for k, v in old_discovery.items()), 'Existing test body changed')

# Independently reconstruct the two literal-only copy edits, without importing
# the author renderer. Hashes bind the read source used for this reconstruction.
source = (HERE / 'v10_sources.py').read_text()
constants = {}
for node in ast.parse(source).body:
    if isinstance(node, ast.Assign) and len(node.targets) == 1 and isinstance(node.targets[0], ast.Name):
        constants[node.targets[0].id] = ast.literal_eval(node.value)
ui = (HERE / 'IOSAppLaunchUITests.swift.in').read_text()
probe = (HERE / 'DSC01Probe.swift.in').read_text()
require(sha(ui.encode()) == constants['ORIGINAL_UI_SHA'], 'Original UI hash changed')
require(sha(probe.encode()) == constants['ORIGINAL_PROBE_SHA'], 'Original probe hash changed')
start, end = ui.index(constants['OS_FUNCTION']), ui.index(constants['POST_SELECTION'])
require(sha(ui[start:end].encode()) == constants['ORIGINAL_PREFIX_SHA'], 'Original OS boundary changed')
require(ui.count(constants['OLD_VERIFIED']) == 1, 'Original verification action ambiguous')
rendered_ui = (ui[:start] + constants['NEW_PREFIX'] + ui[end:]).replace(constants['OLD_VERIFIED'], constants['NEW_VERIFIED'], 1)
require(rendered_ui[:start] == ui[:start], 'Non-OS UI prefix changed')
require(rendered_ui[rendered_ui.index(constants['POST_SELECTION']):] == ui[end:].replace(constants['OLD_VERIFIED'], constants['NEW_VERIFIED'], 1), 'Original OS postselection oracles changed')
rendered_probe = probe
for old, new in [('    private var osDisposition = "not-investigated"', constants['MARKER_PROPERTY'] + '    private var osDisposition = "not-investigated"'),
                 ('    func markOS(_ status: String) {', constants['MARKER_REVEAL'] + '    func markOS(_ status: String) {'),
                 (constants['OLD_MARKER_LAYOUT'], constants['NEW_MARKER_LAYOUT'])]:
    require(rendered_probe.count(old) == 1, 'Probe boundary ambiguous')
    rendered_probe = rendered_probe.replace(old, new, 1)
require(sha(rendered_ui.encode()) == '0abafd9a83542a1794a2536eb23ddcc093ba1f963ed885c65d0ee486b0466333', 'Rendered UI differs')
require(sha(rendered_probe.encode()) == 'e4e89855815c4bf2769e26dc7965dad329e39fbc076e218f183fb27ebdc41772', 'Rendered probe differs')

delta_names = ['run_dsc01_apphost_cycle.py', 'v10_sources.py', 'DSC01OSAppSettings.swift.in', 'v10_receipts.py', 'test_v10_contract.py', 'source-bindings.json', 'README.md', 'research-public-os-controls-01.json']
rebuilt_delta = ''
for name in delta_names:
    p = OLD / name
    rebuilt_delta += ''.join(difflib.unified_diff(p.read_text().splitlines(True) if p.exists() else [], (HERE / name).read_text().splitlines(True), fromfile='v9/' + name if p.exists() else '/dev/null', tofile='v10/' + name))
retained_delta = (HERE / 'v9-to-v10-controls-final-01.diff').read_bytes()
readme_new = ''.join(difflib.unified_diff([], (HERE / 'README.md').read_text().splitlines(True), fromfile='/dev/null', tofile='v10/README.md'))
readme_comparative = ''.join(difflib.unified_diff((OLD / 'README.md').read_text().splitlines(True), (HERE / 'README.md').read_text().splitlines(True), fromfile='v9/README.md', tofile='v10/README.md'))
require(rebuilt_delta.replace(readme_comparative, readme_new, 1).encode() == retained_delta, 'Unexpected full-delta difference beyond README presentation')
require(sha(retained_delta) == '2285484d1a0fac76f71e1183ca1de30470967dafdb1958645657928b1e868df5', 'Unexpected full delta hash')
comparative_path = REVIEW / 'independent-v9-to-v10-complete-comparative-01.diff'
with comparative_path.open('x') as handle:
    handle.write(rebuilt_delta)

report = {
    'schema_version': 1,
    'reviewer': '/root/release_fix_review',
    'author': '/root/native_fix_review',
    'recorded_at': datetime.datetime.now(datetime.timezone.utc).isoformat(),
    'status': 'APPROVED_FOR_ROOT_FOCUSED_CONTROLS_ONLY',
    'native_execution_approval': False,
    'method': 'Complete captured V9-to-V10 delta read, complete current runner read; source-only AST/byte/hash arithmetic; no test/build execution or harness import.',
    'control_manifest_sha256': control_sha,
    'control_count_independently_rehashed': len(current),
    'frozen_manifest': record(HERE / 'author-frozen-control-manifest-01.json'),
    'source_binding': record(HERE / 'source-bindings.json'),
    'source_manifest_sha256': freeze['source']['source_manifest_sha256'],
    'source_inputs_independently_rehashed': len(source_rows),
    'copy_inputs_independently_rehashed': len(binding['copy_only']),
    'commit': freeze['source']['commit'],
    'tree': freeze['source']['tree'],
    'tracked_diff_sha256': freeze['source']['diff_sha256'],
    'delta': dict(record(HERE / 'v9-to-v10-controls-final-01.diff'), lines=len(retained_delta.decode().splitlines()), reviewed_ranges=[[1, len(retained_delta.decode().splitlines())]], executable_delta_reconstructed_byte_identical=True, documentation_presentation_note='Author presents V10 README as addition from /dev/null, although V9 README exists. Actual comparative README diff was separately read and retained; no executable/source discrepancy.'),
    'complete_comparative_delta': dict(record(comparative_path), lines=len(rebuilt_delta.splitlines()), reviewed_ranges=[[1, len(rebuilt_delta.splitlines())]], exact_source_diff=True),
    'read_coverage': [dict(record(HERE / name), reviewed_ranges=[[1, len((HERE / name).read_text().splitlines())]]) for name in ['run_dsc01_apphost_cycle.py', 'v10_sources.py', 'DSC01OSAppSettings.swift.in', 'v10_receipts.py', 'test_v10_contract.py', 'README.md', 'research-public-os-controls-01.json']],
    'unchanged_original_references': len(refs) - 1,
    'finalizer_identical': {'sha256': sha(tail.encode()), 'v10_start_line': runner[:runner.index(marker)].count('\n') + 1},
    'independent_rendering': {'ui_sha256': sha(rendered_ui.encode()), 'ui_lines': len(rendered_ui.splitlines()), 'probe_sha256': sha(rendered_probe.encode()), 'probe_lines': len(rendered_probe.splitlines()), 'original_postselection_oracles_unchanged': True},
    'expected_test_methods_static_only': len(discovery),
    'original_test_method_bodies_byte_identical': len(old_discovery),
    'new_test_methods_static_only': len(discovery) - len(old_discovery),
    'method_identities': discovery,
    'resolved_review_feedback': [
        'All three geometry samples must match final target, including cumulative drift and final requery negatives.',
        'Missing/reordered-stage mutations use fresh fixtures; stableFrames have independent objects and untouched-target/sibling assertions.',
        'Expanded marker rows only appear within terminal finish after original restart assertions or explicitly blocked route.',
        'Durable marker sample and original probe oracle precede completion; OS blocked reason must match actual public route.'
    ],
    'assessment': 'No remaining source-level blocking concern found for executing focused controls on this exact hash. Actual Settings pane availability and hit delivery remain unexecuted in V10. Earlier four native tests and local-game observations do not close the OS gate.',
    'limitations': [
        'This is not a new complete reread of all 107 inherited control files or all 669 application inputs; unchanged controls are identity-bound to prior reviews.',
        '168 methods are AST discovery expectations, not passing tests. Root must provide raw focused results and cleanup before separate native GO.',
        '24 synthetic Swift guard cases remain inside one of five XCTest methods, not 24 independent runtime tests.',
        'Artifact receipt still omits Parlor.debug.dylib; any future result cannot claim complete binary artifact provenance from that list.',
        'A correctly classified OS BLOCKED result remains DS-C01 PARTIALLY VERIFIED. No physical-device, Store-qualified-toolchain, signing or Store claim.'
    ],
    'cleanup': {'reviewer_builds_tests_apps_started': 0, 'reviewer_background_workers_started': 0, 'generated_build_outputs_created': 0, 'other_tasks_resources_touched': False}
}
path = REVIEW / 'independent-v10-frozen-focused-approval-01.json'
with path.open('x') as handle:
    handle.write(json.dumps(report, indent=2) + '\n')
print(json.dumps({'report': str(path), 'sha256': sha(path.read_bytes()), 'status': report['status'], 'control_sha256': control_sha, 'static_test_methods': len(discovery)}))
