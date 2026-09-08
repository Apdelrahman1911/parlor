"""Read-only exact binding/control arithmetic; no repository module execution."""
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
EVIDENCE = BASE / 'evidence/native-binding-12'


def sha(data): return hashlib.sha256(data).hexdigest()
def record(path): return dict(path=str(path.relative_to(ROOT)), sha256=sha(path.read_bytes()))
def require(ok, message):
    if not ok: raise RuntimeError(message)


prepath = REVIEW / 'independent-v12-prebinding-approval-01.json'
require(record(prepath)['sha256'] == 'f148a67085d27035ec53b7cd9cc79778a7072b3f1e9f0a7f4594ba0555560aa7', 'Prebinding approval changed')
pre = json.loads(prepath.read_bytes())
mfpath = HERE / 'author-frozen-control-manifest-01.json'
require(record(mfpath)['sha256'] == '02f88b8fba23c2a969a0068a0b877fec949347493b546560b0208db9ff13dc80', 'Frozen author manifest changed')
mf = json.loads(mfpath.read_bytes())
current = []
for item in pre['controls']:
    path = ROOT / item['path']
    require(path.is_file() and not path.is_symlink(), 'Missing or symlinked control: ' + item['path'])
    value = record(path)
    if item['exists']:
        require(value['sha256'] == item['sha256'], 'Prebinding executable changed: ' + item['path'])
    else:
        require(path == HERE / 'source-bindings.json' and value['sha256'] ==
                'aaa58b8ab5f7cf41e6a8e188df4098e6e31fff40198e934162ac9a8947d28a1d', 'Unexpected new binding')
    current.append(value)
aggregate = sha(json.dumps(current, separators=(',', ':')).encode())
require(len(current) == len({v['path'] for v in current}) == 171 and current == mf['files'] and
        aggregate == mf['control_sha256'] == '1f709a4f0af27e085c4d36665c90b0102f70c185f97ce93b6175a29f4bc5b201',
        'Full171graph mismatch')
binding = json.loads((HERE / 'source-bindings.json').read_bytes())
old_binding = json.loads((OLD / 'source-bindings.json').read_bytes())
require(dict(binding, created_at=old_binding['created_at']) == old_binding, 'Binding change beyond timestamp')
freeze = json.loads((BASE / 'source-freeze-02.json').read_bytes())
require(binding['source_identity'] == freeze['source'], 'Binding differs from sourcefreeze02')
for path, expected in freeze['source']['source_manifest']:
    require(record(ROOT / path)['sha256'] == expected and not (ROOT / path).is_symlink(), 'Source changed: ' + path)
require(len(freeze['source']['source_manifest']) == 669 and len(binding['copy_only']) == 613, 'Source/copy count differs')
for row in binding['copy_only']:
    require(record(ROOT / row['path']) == row, 'Copy input differs')
require(sha(subprocess.check_output(['git','diff','--binary','HEAD'], cwd=ROOT)) == freeze['source']['diff_sha256'], 'Tracked diff changed')

delta = ''
names = ['run_dsc01_apphost_cycle.py','v12_sources.py','DSC01OSPreferenceBaseline.swift.in',
         'v12_receipts.py','test_v12_contract.py','source-bindings.json']
for name in names:
    old, new = OLD / name, HERE / name
    delta += ''.join(difflib.unified_diff(old.read_text().splitlines(True) if old.exists() else [],
                new.read_text().splitlines(True), fromfile=str(old.relative_to(ROOT)) if old.exists() else '/dev/null',
                tofile=str(new.relative_to(ROOT))))
deltapath = HERE / 'v11-to-v12-controls-final-01.diff'
require(delta == deltapath.read_text() and len(delta.splitlines()) == 843 and sha(delta.encode()) ==
        'ce98c084db401823392e6a4cf88a8f6d8b00e5439803f8f0f6331ef1e6a07ee5', 'Full843delta mismatch')
require(delta.startswith((HERE / 'v11-to-v12-prebinding-controls-01.diff').read_text()), 'Reviewed832delta no longer exact prefix')
method_identities = {}
for path in sorted(HERE.glob('test_*.py')):
    source = path.read_text()
    for node in ast.parse(source).body:
        if isinstance(node, ast.ClassDef):
            for method in node.body:
                if isinstance(method, (ast.FunctionDef, ast.AsyncFunctionDef)) and method.name.startswith('test_'):
                    key = path.stem + '.' + node.name + '.' + method.name
                    require(key not in method_identities, 'Duplicate static test')
                    method_identities[key] = sha(ast.get_source_segment(source, method).encode())
require(method_identities == pre['method_identities'] and len(method_identities) == 199, 'Static199methods changed')

receiptpath = EVIDENCE / 'receipt.json'
require(record(receiptpath)['sha256'] == 'be7ea188bf704cb20616e44e6066e78e6753922680a010f1efaef9027a7253db', 'Binding12receipt changed')
receipt = json.loads(receiptpath.read_bytes())
require(receipt['source_before'] == receipt['source_after'] == freeze['source'], 'Binding12source changed')
require(receipt['runner_before'] == receipt['runner_after'], 'Binding12runner drift')
for path, expected in receipt['runner_before']['manifest']:
    require(record(ROOT / path)['sha256'] == expected, 'Binding12cycler changed')
require(receipt['exit_code'] == receipt['stop_exit_code'] == 0 and receipt['status'] == 'PASS', 'Binding12command/stop failed')
require(not receipt['source_changed_during_cycle'] and not receipt['runner_changed_during_cycle'], 'Binding12drift reported')
require(not any(receipt[k] for k in ['outputs_before','remaining_outputs','retained_outputs','cleanup_errors','deferred_signals']),
        'Binding12cleanup incomplete')
require(not receipt['workers']['remaining_owned_workers'], 'Binding12workers remain')
require(receipt['finished_at'] < receipt['stopped_at'] < receipt['cleanup_completed_at'], 'Binding12cleanup chronology')
require((EVIDENCE / 'stop.log').read_text() == 'No Gradle daemons are running.\n', 'Binding12stop log differs')
outputs = [path.parent/'build' for pattern in ['shared/*/build.gradle.kts','game-modes/*/build.gradle.kts'] for path in ROOT.glob(pattern)]
outputs += [ROOT/'build', ROOT/'composeApp/build', ROOT/'build-logic/build', ROOT/'build-logic/convention/build']
require(len(outputs) == 16 and all(not path.exists() and not path.is_symlink() for path in outputs + [EVIDENCE/'scratch']),
        'Binding12generated output remains')

report = dict(
    schema_version=1, reviewer='/root/release_fix_review', author='/root/native_fix_review',
    recorded_at=datetime.datetime.now(datetime.timezone.utc).isoformat(),
    status='APPROVED_FOR_ROOT_FOCUSED_CONTROLS_ONLY', native_execution_approval=False,
    method='Read final timestamp-only binding hunk833–843 and frozen metadata; independently reconstructed843line full delta and rehashed exact171control graph plus669source/613copy inputs. Prebinding full832line direct review remains byte-identical. AST/literal/hash/receipt arithmetic only.',
    prebinding_review=record(prepath), frozen_manifest=record(mfpath), source_binding=record(HERE/'source-bindings.json'),
    control_manifest_sha256=aggregate, control_count_independently_rehashed=171,
    source_manifest_sha256=freeze['source']['source_manifest_sha256'], source_inputs_independently_rehashed=669,
    copy_inputs_independently_rehashed=613, commit=freeze['source']['commit'], tree=freeze['source']['tree'],
    tracked_diff_sha256=freeze['source']['diff_sha256'],
    delta=dict(record(deltapath), lines=843, reviewed_ranges=[[1,843]], previous832lines_byte_identical=True,
               new833_to843_timestamp_only=True, full_independent_reconstruction=True),
    binding_receipt=dict(record(receiptpath), actual_exit=0, stop_exit=0, source_and_runner_unchanged=True,
                         cleanup_errors=[], generated_build_paths_absent=16, cycle_scratch_absent=True),
    method_identities=method_identities, expected_test_methods_static_only=199,
    inherited_test_methods_byte_identical=177, new_test_methods_static_only=22,
    finalizer_identical=pre['runner_coverage']['finalizer_sha256'],
    conclusions=[
        'Binding12actual command/stop/cleanup completed; sole new binding differs from V11only by creationtimestamp.',
        'All170previously read executable controls unchanged; full171path/hash graph matches author freeze exactly.',
        'No blocking issue remains for root to execute199focused synthetic source/receipt/cleanup contracts.',
        'Native execution is explicitly not authorized by this report. Raw199descriptors/results plus source/control/cleanup checks require independent review before separate singleattemptGO.'
    ],
    limitations=pre['limitations'],
    cleanup=dict(reviewer_build_test_app_background_processes_started=0, generated_outputs_created=0,
                 other_tasks_resources_touched=False)
)
out = REVIEW/'independent-v12-frozen-focused-approval-01.json'
with out.open('x') as handle:
    handle.write(json.dumps(report, indent=2, ensure_ascii=False)+'\n')
print(json.dumps(dict(report=str(out), sha256=sha(out.read_bytes()), status=report['status'],
                      controls=171, expected_tests=199, control_sha256=aggregate)))
