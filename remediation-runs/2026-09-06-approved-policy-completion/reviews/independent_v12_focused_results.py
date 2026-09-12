"""Read-only reconciliation of root-run evidence, not test/harness execution."""
import ast
from collections import Counter
import datetime
import hashlib
import json
from pathlib import Path
import re
import shlex
import subprocess

ROOT = Path('/Users/abdelrahman/Projects/parlor')
BASE = ROOT/'remediation-runs/2026-09-06-approved-policy-completion'
HERE = BASE/'native/dsc01_apphost_v12'
REVIEW = BASE/'reviews'
EV = BASE/'evidence/native-harness-focused-12'
sha = lambda data: hashlib.sha256(data).hexdigest()
record = lambda path: dict(path=str(path.relative_to(ROOT)), sha256=sha(path.read_bytes()))


def require(condition, message):
    if not condition: raise RuntimeError(message)


priorpath = REVIEW/'independent-v12-frozen-focused-approval-01.json'
require(record(priorpath)['sha256'] == '1b46066c73b09622544d812d0e2115ed182f3df085e75b1f0e72352e431d098b', 'Prior approval differs')
prior = json.loads(priorpath.read_bytes())
receipt = json.loads((EV/'receipt.json').read_bytes())
require(record(EV/'receipt.json')['sha256'] == '58856b544c8407590dba21d4203b1447a3da642e22c4617b8e02e0f1323ebd6d', 'Focused receipt changed')
log = (EV/'gradle.log').read_text()
pattern = re.compile(r'^(test_[A-Za-z0-9_]+) \(([A-Za-z0-9_.]+)\) \.\.\. (.+)$')
seen = {}
for line in log.splitlines():
    if not line.startswith('test_'): continue
    match = pattern.fullmatch(line)
    require(match is not None, 'Unrecognized raw test descriptor/result')
    key = match[2]+'.'+match[1]
    require(key not in seen, 'Duplicate actual descriptor')
    seen[key] = match[3]
require(set(seen) == set(prior['method_identities']) and len(seen) == 199 and set(seen.values()) == {'ok'},
        'Actual discovery/results do not match reviewed199methods')
summary = re.findall(r'^Ran (\d+) tests in ([0-9.]+)s$', log, re.M)
require(summary == [('199','5.800')] and log.rstrip().endswith('\nOK'), 'Raw actual result footer differs')
require(not any(token in log for token in ['FAILED (','ERROR:','FAIL:','skipped ', 'expected failure','unexpected success']),
        'Failure/nonpass present in raw output')
require(receipt['command'] == ['/usr/bin/python3','-B','-m','unittest','discover','-s',str(HERE.relative_to(ROOT)),'-p','test_*.py','-v'],
        'Actual test invocation changed')
require(receipt['status'] == 'PASS' and receipt['exit_code'] == receipt['stop_exit_code'] == 0, 'Task or stop failed')

mf = json.loads((HERE/'author-frozen-control-manifest-01.json').read_bytes())
current = [record(ROOT/row['path']) for row in mf['files']]
aggregate = sha(json.dumps(current,separators=(',',':')).encode())
require(current == mf['files'] and len(current) == 171 and aggregate == prior['control_manifest_sha256'], 'Frozen171controls drifted')
freeze = json.loads((BASE/'source-freeze-02.json').read_bytes())
require(receipt['source_before'] == receipt['source_after'] == freeze['source'], 'Source drift during cycle')
require(receipt['runner_before'] == receipt['runner_after'], 'Cycler drift during cycle')
for path, expected in freeze['source']['source_manifest']:
    require(record(ROOT/path)['sha256'] == expected, 'Current application input differs')
for path, expected in receipt['runner_before']['manifest']:
    require(record(ROOT/path)['sha256'] == expected, 'Current cycler input differs')
require(sha(subprocess.check_output(['git','diff','--binary','HEAD'], cwd=ROOT)) == freeze['source']['diff_sha256'], 'Tracked diff changed')
require(not receipt['source_changed_during_cycle'] and not receipt['runner_changed_during_cycle'], 'Cycle change reported')
methods = {}
for path in HERE.glob('test_*.py'):
    source = path.read_text()
    for node in ast.parse(source).body:
        if isinstance(node, ast.ClassDef):
            for method in node.body:
                if isinstance(method, ast.FunctionDef) and method.name.startswith('test_'):
                    methods[path.stem+'.'+node.name+'.'+method.name] = sha(ast.get_source_segment(source,method).encode())
require(methods == prior['method_identities'], 'Method bodies changed after root execution')

for key in ['outputs_before','remaining_outputs','retained_outputs','cleanup_errors','deferred_signals']:
    require(not receipt[key], 'Cleanup issue: '+key)
require(not receipt['workers']['remaining_owned_workers'], 'Owned workers reported alive')
stop = (EV/'stop.log').read_text()
require(stop == 'No Gradle daemons are running.\n', 'Stop result differs')
finished = datetime.datetime.fromisoformat(receipt['finished_at'])
stopped = datetime.datetime.fromisoformat(receipt['stopped_at'])
require(finished < stopped < datetime.datetime.fromisoformat(receipt['cleanup_completed_at']), 'Cleanup order differs')
outputs = [p.parent/'build' for pat in ['shared/*/build.gradle.kts','game-modes/*/build.gradle.kts'] for p in ROOT.glob(pat)]
outputs += [ROOT/'build',ROOT/'composeApp/build',ROOT/'build-logic/build',ROOT/'build-logic/convention/build']
require(len(outputs) == 16 and all(not p.exists() and not p.is_symlink() for p in outputs+[EV/'scratch']),
        'Generated module/buildlogic/scratch output remains')
processes = subprocess.check_output(['ps','-axo','pid=,lstart=,command='],text=True)
live = []
for line in processes.splitlines():
    match = re.match(r'^\s*(\d+)\s+(\w{3}\s+\w{3}\s+\d+\s+\d\d:\d\d:\d\d\s+\d{4})\s+(.*)$',line)
    if not match: continue
    try: argv = shlex.split(match[3])
    except ValueError: continue
    if argv and re.fullmatch(r'python(?:3(?:\.\d+)?)?',Path(argv[0]).name,re.I) and '-m' in argv and 'unittest' in argv and 'discover' in argv and str(HERE.relative_to(ROOT)) in argv:
        live.append(dict(pid=int(match[1]),start=match[2]))
require(not live, 'Exact focused interpreter still live')

report = dict(
    schema_version=1, reviewer='/root/release_fix_review', recorded_at=datetime.datetime.now(datetime.timezone.utc).isoformat(),
    status='PASS_FOCUSED_CONTROLS_AND_GO_FOR_ONE_ROOT_OWNED_NATIVE_ATTEMPT', cycle='native-harness-focused-12',
    prior_review=record(priorpath), evidence=[record(EV/name) for name in ['receipt.json','gradle.log','stop.log']],
    executed_tests=dict(total=199,passed=199,failed=0,errors=0,skipped=0,duration_seconds=5.800,
                        all_raw_descriptors_match_independent_ast=True,
                        module_counts=dict(sorted(Counter(k.split('.')[0] for k in seen).items())),
                        limitation='Python synthetic source/receipt/cleanup contracts only, not native Swift/app runtime.'),
    new_negative_case_assertions_reviewed=[
        'PRESENT and ABSENT preserve exact pair; only PRESENT executes legacyOSoracle; ABSENT fails old unsupported presence assumption.',
        'Missing/nonboolean/malformed/oversized representation, rebased presence/order, wrong ownerprevious, missing/reused BEFORE-App processes fail.',
        'Exact nativecontroller/window/fullgeometry, real Compose Settings direction, preArabicEnglish navigation and actualArabic ownership remain mandatory.',
        'Fourordered stage records cannot replace one realOSaction or pane provenance, precede actual action, duplicate or appear after reporting.',
        'Incomplete native09-shaped data cannot be relabeled PASS; unavailableOS remains BLOCKED without invented baseline.',
        'Wrapped original Settings/local validators execute and reject invalid matrix/token/localidentity; all177original bodies/finalizer remain identical.',
        'The22new Python methods and native14+6+4contract shape are not native application execution evidence.'
    ],
    control_manifest_sha256=aggregate, controls_rehashed_after_execution=171,
    source_manifest_sha256=freeze['source']['source_manifest_sha256'], source_inputs_rehashed_after_execution=669,
    source_and_runner_unchanged=True,
    cleanup=dict(stop_exit_code=0, stop_log=stop.strip(), stop_completed_seconds_after_task_finish=(stopped-finished).total_seconds(),
                 cleanup_errors=[], output_paths_independently_checked_absent=17,generated_build_paths=16,cycle_scratch_paths=1,
                 reported_remaining_owned_workers=[], current_exact_python_test_processes=live,
                 limitation='No global-idleness claim; reviewer started no build/test/app/background process and touched no other-task resource.'),
    native_authorization=dict(status='GO',executor='/root',cycle='dsc01-apphost-10',approved_control_sha256=aggregate,
        single_owned_fresh_simulator_attempt_only=True,requirements=[
            'Root sole lane; productionfreeze02 and exact171controls frozen; no production or priorreceipt modification.',
            'Execute original fiveXCTest selection and fullmatrix; preserve rawresults/sourcecopy/probe evidence before ownedcleanup.',
            'Use unchanged immediate Gradlestops and PID/start/FIFO/ownedprofile/copy/DerivedData finalizer on every exit.',
            'PRESENT and ABSENT coverage are separate. ABSENT success cannot establish OS-created PRESENT; counterpart is explicitly locally investigable, not assumed external-only.',
            'Independent actualresult/provenance/cleanup review required before changing DS-C01status. Native09 remains FAIL.',
            'Artifactreceipt omits Parlor.debug.dylib; no completebinaryprovenance, physicalLAN, signedcandidate, Storequalifiedtoolchain or readiness claim.'
        ]),
    reviewer_execution='Read-only rawresult/AST/hash/source/process metadata arithmetic; no test rerun.'
)
out = REVIEW/'independent-v12-focused-result-native-go-01.json'
with out.open('x') as handle: handle.write(json.dumps(report,indent=2,ensure_ascii=False)+'\n')
print(json.dumps(dict(report=str(out),sha256=sha(out.read_bytes()),status=report['status'],tests=199,native_cycle='dsc01-apphost-10')))
