"""Independent read-only AST/literal/source arithmetic, not harness/test execution."""
import ast
import datetime
import difflib
import hashlib
import json
from pathlib import Path
import subprocess

ROOT = Path('/Users/abdelrahman/Projects/parlor')
BASE = ROOT / 'remediation-runs/2026-09-06-approved-policy-completion'
HERE = BASE / 'native/dsc01_apphost_v12'
OLD = HERE.parent / 'dsc01_apphost_v11'
REVIEW = BASE / 'reviews'


def sha(data):
    return hashlib.sha256(data).hexdigest()


def require(ok, message):
    if not ok:
        raise RuntimeError(message)


def file_record(path):
    return dict(path=str(path.relative_to(ROOT)), sha256=sha(path.read_bytes()))


def path_expression(node, environment):
    """Interpret only the inspected literal/Path/list grammar, not Python code."""
    if isinstance(node, ast.Constant):
        return node.value
    if isinstance(node, ast.Name):
        return environment[node.id]
    if isinstance(node, (ast.List, ast.Tuple)):
        return [path_expression(item, environment) for item in node.elts]
    if isinstance(node, ast.BinOp):
        left, right = path_expression(node.left, environment), path_expression(node.right, environment)
        if isinstance(node.op, ast.Add) and isinstance(left, list) and isinstance(right, list):
            return left + right
        if isinstance(node.op, ast.Div) and isinstance(left, Path) and isinstance(right, str):
            return left / right
    if isinstance(node, ast.Attribute) and node.attr == 'parent':
        value = path_expression(node.value, environment)
        require(isinstance(value, Path), 'Not a Path.parent expression')
        return value.parent
    if isinstance(node, ast.Call) and not node.keywords:
        if isinstance(node.func, ast.Name) and node.func.id == 'Path' and len(node.args) == 1:
            return Path(path_expression(node.args[0], environment))
        if isinstance(node.func, ast.Attribute) and node.func.attr == 'resolve' and not node.args:
            value = path_expression(node.func.value, environment)
            require(isinstance(value, Path), 'Not a Path.resolve expression')
            return value.resolve()
    if isinstance(node, ast.ListComp) and len(node.generators) == 1:
        generator = node.generators[0]
        require(isinstance(generator.target, ast.Name) and not generator.ifs and not generator.is_async,
                'Unexpected control-list comprehension')
        return [path_expression(node.elt, dict(environment, **{generator.target.id: item}))
                for item in path_expression(generator.iter, environment)]
    raise RuntimeError('Unsupported metadata AST: ' + ast.dump(node))


def static_control_paths(source, directory):
    node = next(n for n in ast.parse(source).body if isinstance(n, ast.FunctionDef) and n.name == 'control_files')
    require(len(node.body) == 1 and isinstance(node.body[0], ast.Return), 'Control helper no longer a pure path list')
    return path_expression(node.body[0].value,
                           dict(HERE=directory, RUN=BASE, ROOT=ROOT, __file__=str(directory / 'run_dsc01_apphost_cycle.py')))


def literals(path):
    result = {}
    for node in ast.parse(path.read_text()).body:
        if isinstance(node, ast.Assign) and len(node.targets) == 1 and isinstance(node.targets[0], ast.Name):
            try:
                result[node.targets[0].id] = ast.literal_eval(node.value)
            except (ValueError, TypeError):
                pass
    return result


def method_identities(directory):
    identities, positions = {}, {}
    for path in sorted(directory.glob('test_*.py')):
        source = path.read_text()
        positions[path.name] = []
        for cls in ast.parse(source).body:
            if not isinstance(cls, ast.ClassDef):
                continue
            for method in cls.body:
                if isinstance(method, (ast.FunctionDef, ast.AsyncFunctionDef)) and method.name.startswith('test_'):
                    local_name = cls.name + '.' + method.name
                    name = path.stem + '.' + local_name
                    require(name not in identities, 'Duplicate test method: ' + name)
                    identities[name] = sha(ast.get_source_segment(source, method).encode())
                    positions[path.name].append(dict(name=local_name, start_line=method.lineno, end_line=method.end_lineno))
    return identities, positions


metadata_path = HERE / 'author-prebinding-static-inspection-01.json'
require(sha(metadata_path.read_bytes()) == 'c9cdd1ac6573cc92c008d2d3f11017deea1a977d76346005bfdeaa8509e00672',
        'Author stable metadata changed')
metadata = json.loads(metadata_path.read_bytes())
runner = (HERE / 'run_dsc01_apphost_cycle.py').read_text()
old_runner = (OLD / 'run_dsc01_apphost_cycle.py').read_text()
paths = static_control_paths(runner, HERE)
require(len(paths) == len(set(paths)) == 171, 'Control graph cardinality changed')
records = [dict(path=str(path.relative_to(ROOT)), sha256=sha(path.read_bytes()) if path.is_file() else None,
                exists=path.is_file(), symlink=path.is_symlink()) for path in paths]
require(records == metadata['controls'], 'Author170present-control inventory differs from independent hashes')
require([path for path in paths if not path.exists()] == [HERE / 'source-bindings.json'], 'Unexpected prebinding missing path')
require(not any(path.is_symlink() for path in paths), 'Symlink control not permitted')

old_frozen = json.loads((OLD / 'author-frozen-control-manifest-01.json').read_bytes())
old_approval = json.loads((REVIEW / 'independent-v11-frozen-focused-approval-01.json').read_bytes())
old_paths = static_control_paths(old_runner, OLD)
old_records = [file_record(path) for path in old_paths]
require(len(old_records) == 137 and old_records == old_frozen['files'], 'Previously approved V11control changed')
require(sha(json.dumps(old_records, separators=(',', ':')).encode()) == old_frozen['control_sha256'] ==
        old_approval['control_manifest_sha256'], 'V11control aggregate approval differs')
old_refs = [path for path in paths if path.parent == OLD]
require(len(old_refs) == 30, 'V11reference graph changed')
for path in old_refs:
    if path.name != 'run_dsc01_apphost_cycle.py':
        require(path.read_bytes() == (HERE / path.name).read_bytes(), 'Inherited control bytes changed: ' + path.name)

freeze = json.loads((BASE / 'source-freeze-02.json').read_bytes())
source_rows = freeze['source']['source_manifest']
require(len(source_rows) == len({row[0] for row in source_rows}) == 669, 'Source manifest count changed')
for name, expected in source_rows:
    path = ROOT / name
    require(path.is_file() and not path.is_symlink() and sha(path.read_bytes()) == expected, 'Frozen source changed: ' + name)
require(sha(json.dumps(source_rows, separators=(',', ':')).encode()) == freeze['source']['source_manifest_sha256'],
        'Frozen source digest changed')
old_binding = json.loads((OLD / 'source-bindings.json').read_bytes())
require(old_binding['source_identity'] == freeze['source'], 'V11copy inputs no longer bound to freeze02')
require(len(old_binding['copy_only']) == 613, 'Copy input count changed')
for row in old_binding['copy_only']:
    require(file_record(ROOT / row['path']) == row, 'Copy input changed: ' + row['path'])


def git(*args):
    return subprocess.check_output(['git'] + list(args), cwd=ROOT)


for args, expected in [(('branch', '--show-current'), freeze['source']['branch']),
                       (('rev-parse', 'HEAD'), freeze['source']['commit']),
                       (('rev-parse', 'HEAD^{tree}'), freeze['source']['tree'])]:
    require(git(*args).decode().strip() == expected, 'Git source identity changed: ' + str(args))
require(sha(git('diff', '--binary', 'HEAD')) == freeze['source']['diff_sha256'], 'Tracked diff changed')
require(git('status', '--short', '--untracked-files=no').decode().strip() == freeze['source']['tracked_status'],
        'Tracked status changed')

finalizer_marker = '        finally:\n            deferred = []'
finalizer = runner[runner.index(finalizer_marker):]
require(finalizer == old_runner[old_runner.index(finalizer_marker):] and
        sha(finalizer.encode()) == '7b1c9a2faa58be765205acfae064d203b896069fd382c1f2bfb1923ceac032c0', 'Finalizer changed')
old_nodes = {node.name: node for node in ast.parse(old_runner).body if isinstance(node, (ast.FunctionDef, ast.ClassDef))}
new_nodes = {node.name: node for node in ast.parse(runner).body if isinstance(node, (ast.FunctionDef, ast.ClassDef))}
require(set(old_nodes) == set(new_nodes), 'Runner declarations changed')
for name in old_nodes.keys() - {'main', 'control_files'}:
    require(ast.get_source_segment(old_runner, old_nodes[name]) == ast.get_source_segment(runner, new_nodes[name]),
            'Unrelated runner declaration changed: ' + name)

old_methods, _ = method_identities(OLD)
new_methods, positions = method_identities(HERE)
require(len(old_methods) == 177 and len(new_methods) == 199, 'Static method count changed')
require(old_methods == old_approval['method_identities'], 'Old177method identity changed')
require(all(new_methods.get(name) == value for name, value in old_methods.items()), 'Inherited method body changed')
require(positions == metadata['focused_methods'], 'Independent AST method line positions differ from metadata')
for path in OLD.glob('test_*.py'):
    require(path.read_bytes() == (HERE / path.name).read_bytes(), 'Inherited test module differs')

delta_names = ['run_dsc01_apphost_cycle.py', 'v12_sources.py', 'DSC01OSPreferenceBaseline.swift.in',
               'v12_receipts.py', 'test_v12_contract.py']
delta = ''
for name in delta_names:
    old, new = OLD / name, HERE / name
    delta += ''.join(difflib.unified_diff(old.read_text().splitlines(True) if old.exists() else [],
                new.read_text().splitlines(True), fromfile=str(old.relative_to(ROOT)) if old.exists() else '/dev/null',
                tofile=str(new.relative_to(ROOT))))
delta_path = HERE / 'v11-to-v12-prebinding-controls-01.diff'
require(delta == delta_path.read_text() and len(delta.splitlines()) == 832 and
        sha(delta.encode()) == '8a3c821f6a5234713b13b5ee0515c358058e1e66f8642dbf7a786c0db9a73f88', 'Full delta differs')

# Reconstruct the reviewed renderer with literal substitution, not imports or
# function calls into harness/test code.
v10 = literals(HERE / 'v10_sources.py')
v12 = literals(HERE / 'v12_sources.py')
template = (HERE / 'IOSAppLaunchUITests.swift.in').read_text()
require(sha(template.encode()) == v10['ORIGINAL_UI_SHA'], 'Original rendered template digest changed')
start = template.index(v10['OS_FUNCTION'])
end = template.index(v10['POST_SELECTION'], start)
prefix = template[start:end]
require(sha(prefix.encode()) == v10['ORIGINAL_PREFIX_SHA'] and template.count(prefix) == 1, 'V10original prefix differs')
rendered_v11 = template.replace(prefix, v10['NEW_PREFIX'], 1)
require(rendered_v11.count(v10['OLD_VERIFIED']) == 1, 'V10verified replacement ambiguous')
rendered_v11 = rendered_v11.replace(v10['OLD_VERIFIED'], v10['NEW_VERIFIED'], 1)
require(sha(rendered_v11.encode()) == v12['V11_RENDERED_UI_SHA'] ==
        '0abafd9a83542a1794a2536eb23ddcc093ba1f963ed885c65d0ee486b0466333', 'V11render identity differs')
require(rendered_v11.count(v12['BEGIN']) == rendered_v11.count(v12['END']) == 1, 'V12boundaries ambiguous')
start = rendered_v11.index(v12['BEGIN']); end = rendered_v11.index(v12['END'], start)
region = rendered_v11[start:end]
for old, new in v12['REPLACEMENTS']:
    require(region.count(old) == 1, 'V12replacement absent/ambiguous')
    region = region.replace(old, new, 1)
rendered_v12 = rendered_v11[:start] + region + rendered_v11[end:]
require(sha(rendered_v12.encode()) == 'f1926d606d567eeccd43721898a05a8c9394949a95f31ca3c4b2bf642e5fa20f',
        'V12render identity differs')
render_delta = ''.join(difflib.unified_diff(rendered_v11.splitlines(True), rendered_v12.splitlines(True),
                        fromfile='V11-copy-only-IOSAppLaunchUITests.swift', tofile='V12-copy-only-IOSAppLaunchUITests.swift'))
render_delta_path = HERE / 'v11-to-v12-rendered-os-region-01.diff'
require(render_delta == render_delta_path.read_text() and len(render_delta.splitlines()) == 56 and
        sha(render_delta.encode()) == '9cc11f23599d51355e0801dc437d1d3a7f98ff48287ed32be92759023182a6ab',
        'Rendered OS delta differs')
for old, new in reversed(v12['REPLACEMENTS']):
    require(region.count(new) == 1, 'V12inverse replacement ambiguous')
    region = region.replace(new, old, 1)
require(rendered_v11 == rendered_v11[:start] + region + rendered_v11[end:], 'Inverse OS-only reconstruction differs')

report = dict(
    schema_version=1, reviewer='/root/release_fix_review', author='/root/native_fix_review',
    recorded_at=datetime.datetime.now(datetime.timezone.utc).isoformat(),
    status='APPROVED_FOR_ROOT_SOURCE_BINDING12_ONLY', focused_execution_approval=False, native_execution_approval=False,
    method='Full832line final delta, all52/149/141/397 new source/helper/parser/test lines and60line README directly read; entire56line rendered OS diff read. Read-only AST/literal/Path and byte/hash/source arithmetic, no repository module import, test/harness/build/app execution.',
    exact_author_metadata=file_record(metadata_path),
    controls_rehashed_present=170, expected_control_graph=171,
    missing_binding_only=str((HERE / 'source-bindings.json').relative_to(ROOT)),
    controls=records,
    previous_v11_control_sha256=old_frozen['control_sha256'], previous_v11_controls_rehashed=137,
    source_inputs_rehashed=669, copy_inputs_rehashed=613,
    commit=freeze['source']['commit'], tree=freeze['source']['tree'], branch=freeze['source']['branch'],
    source_manifest_sha256=freeze['source']['source_manifest_sha256'], tracked_diff_sha256=freeze['source']['diff_sha256'],
    delta=dict(file_record(delta_path), lines=832, reviewed_ranges=[[1,832]], independently_reconstructed=True),
    rendered_ui_delta=dict(file_record(render_delta_path), lines=56, reviewed_ranges=[[1,56]], independently_reconstructed=True,
                           v11_sha256=sha(rendered_v11.encode()), v12_sha256=sha(rendered_v12.encode()),
                           prefix_and_suffix_unchanged=True, inverse_reconstruction_exact=True),
    read_coverage=[dict(file_record(HERE / name), lines=len((HERE / name).read_text().splitlines()),
                       reviewed_ranges=[[1,len((HERE / name).read_text().splitlines())]])
                   for name in ['v12_sources.py','DSC01OSPreferenceBaseline.swift.in','v12_receipts.py','test_v12_contract.py','README.md']],
    runner_coverage=dict(file_record(HERE / 'run_dsc01_apphost_cycle.py'), full_delta_read=True,
                         inherited_full_review=file_record(REVIEW / 'independent-v11-frozen-focused-approval-01.json'),
                         all_other_declarations_byte_identical=True, finalizer_byte_identical=True,
                         finalizer_sha256=sha(finalizer.encode())),
    unchanged_inherited_controls=29, inherited_test_methods_byte_identical=177,
    added_test_methods_static_only=22, expected_focused_methods_static_only=199, method_identities=new_methods,
    native_contract_cases_static_only=dict(shape=14, ownership=6, restoration=4),
    review_conclusions=[
        'The sole removed application-state assumption is mandatory nonempty app-domain AppleLanguages after genuine OS English selection; no Apple source examined requires that representation.',
        'New baseline binds actual post-choice presence and ordered array to fresh-process BEFORE-App evidence, before actual appArabic mutation; it cannot silently rebase present to absent.',
        'Actual English Settings before Arabic, exact Arabic owner previouspresence/value, actual System and fresh restart restore the same pair with real Compose/native direction and original controller/geometry guards.',
        'V10 public action/sampling/one-activation guards and V11 exact pane provenance remain byte-identical; fourstage logs are ordered after actual English action and before terminal reporting.',
        'PRESENT additionally requires original OS verification. ABSENT explicitly says original PRESENT-only oracle did not execute. BLOCKED cannot acquire a baseline or PASS.',
        'Original177test modules and full finalizer are unchanged;22new methods include malformed/rebased/missing BEFORE-App, stale boots, wrong owner, native/Compose/geometry, invalid scope and retroactivePASS rejection.',
        'No blocking source-level concern found for root to create the sole missing source-binding file. This is not approval to execute199focused tests or any native app yet.'
    ],
    limitations=[
        'This is prebinding source review, not an executed-test result. Root must bind12, preserve cleanup evidence, freeze171control hashes and seek separate exact-control focused approval.',
        'After focused199results are independently reconciled, root must receive separate nativeGO for one fresh owned simulator attempt; native09 remains FAIL.',
        'If native10 measures ABSENT it is not real OS-created PRESENT preservation. Counterpart representation is a specifically named locally investigable evidence gap, not automatically physical-only.',
        'PRESENT-empty arrays or other unobserved representations remain outside this bounded English/Arabic synthetic-profile oracle; rejection is not proof of an application defect.',
        'Inherited controls/application source were rehashed and bound to prior review; this bounded continuation is not a fresh reread of every171control or669production input.',
        'Existing binary artifact receipt omits Parlor.debug.dylib; complete binary provenance cannot be claimed.',
        'DS-C01 remains PARTIALLY_VERIFIED. No physical-device/LAN, Store-qualified toolchain, signing, Store or overallREADY claim.'
    ],
    references=[file_record(REVIEW / name) for name in ['independent-dsc01-apphost09-outcome-01.json','independent-apphost09-domain-research-01.json']],
    cleanup=dict(reviewer_builds_tests_apps_started=0, reviewer_background_workers_started=0,
                 generated_build_outputs_created=0, other_task_resources_touched=False)
)
out = REVIEW / 'independent-v12-prebinding-approval-01.json'
with out.open('x') as handle:
    handle.write(json.dumps(report, indent=2, ensure_ascii=False) + '\n')
print(json.dumps(dict(report=str(out), sha256=sha(out.read_bytes()), status=report['status'],
                      controls_present=170, static_test_methods=199)))
