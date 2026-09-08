"""Read-only result/source/process metadata reconciliation, not test execution."""
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
HERE = BASE/'native/dsc01_apphost_v11'
REVIEW = BASE/'reviews'
EVIDENCE = BASE/'evidence/native-harness-focused-11'


def sha(data): return hashlib.sha256(data).hexdigest()
def record(path): return dict(path=str(path.relative_to(ROOT)), sha256=sha(path.read_bytes()))
def require(condition, message):
    if not condition: raise RuntimeError(message)

priorpath = REVIEW/'independent-v11-frozen-focused-approval-01.json'
require(record(priorpath)['sha256'] == '46e13615bf6e2867995e3ef2f859f24502c1af5fcf57e831b8141be73ae88de1','Prior approval drift')
prior = json.loads(priorpath.read_bytes())
receipt = json.loads((EVIDENCE/'receipt.json').read_bytes())
log = (EVIDENCE/'gradle.log').read_text()
pattern = re.compile(r'^(test_[A-Za-z0-9_]+) \(([A-Za-z0-9_.]+)\) \.\.\. (.+)$')
seen = {}
for line in log.splitlines():
    if not line.startswith('test_'): continue
    match = pattern.fullmatch(line)
    require(match is not None, 'Unknown raw test descriptor/result: '+line)
    key=match[2]+'.'+match[1]
    require(key not in seen, 'Duplicate raw test descriptor')
    seen[key]=match[3]
require(set(seen)==set(prior['method_identities']), 'Actual discovery differs from independently read AST')
require(len(seen)==177 and set(seen.values())=={'ok'},'Failures/skips/errors in raw methods')
summary=re.findall(r'^Ran (\d+) tests in ([0-9.]+)s$',log,re.M)
require(summary==[('177','4.974')], 'Raw summary mismatch')
require(log.rstrip().endswith('\nOK'), 'No actual unittest success footer')
require(not any(token in log for token in ('FAILED (','ERROR:','FAIL:','skipped ', 'expected failure', 'unexpected success')), 'Nonpass method/summary')
require(receipt['command']==['/usr/bin/python3','-B','-m','unittest','discover','-s',str(HERE.relative_to(ROOT)),'-p','test_*.py','-v'],'Unexpected focused command')
require(receipt['status']=='PASS' and receipt['exit_code']==receipt['stop_exit_code']==0,'Focused cycle did not pass/stop')

frozen=json.loads((HERE/'author-frozen-control-manifest-01.json').read_bytes())
current=[record(ROOT/row['path']) for row in frozen['files']]
aggregate=sha(json.dumps(current,separators=(',',':')).encode())
require(current==frozen['files'] and aggregate==prior['control_manifest_sha256'],'Frozen controls changed during/after execution')
freeze=json.loads((BASE/'source-freeze-02.json').read_bytes())
require(receipt['source_before']==receipt['source_after']==freeze['source'],'Focused source identity changed')
require(receipt['runner_before']==receipt['runner_after'],'Focused cycler identity changed')
for row in freeze['source']['source_manifest']:
    require(sha((ROOT/row[0]).read_bytes())==row[1],'Application source input changed')
for path,expected in receipt['runner_before']['manifest']:
    require(sha((ROOT/path).read_bytes())==expected,'Cycler changed after execution')
require(not receipt['source_changed_during_cycle'] and not receipt['runner_changed_during_cycle'],'Cycle drift reported')
for key in ['outputs_before','remaining_outputs','retained_outputs','cleanup_errors','deferred_signals']:
    require(not receipt[key], 'Unexpected cleanup/preservation condition: '+key)
require(not receipt['workers']['remaining_owned_workers'],'Owned workers reported alive')
stop=(EVIDENCE/'stop.log').read_text()
require(stop=='No Gradle daemons are running.\n','Unexpected stop result')
finished=datetime.datetime.fromisoformat(receipt['finished_at']); stopped=datetime.datetime.fromisoformat(receipt['stopped_at'])
require(finished<stopped<datetime.datetime.fromisoformat(receipt['cleanup_completed_at']),'Cleanup order incorrect')
modules=[p.parent for p in ROOT.glob('shared/*/build.gradle.kts')]
modules += [p.parent for p in ROOT.glob('game-modes/*/build.gradle.kts')]
modules += [ROOT,ROOT/'composeApp',ROOT/'build-logic',ROOT/'build-logic/convention']
outputs=[p/'build' for p in modules]
require(len(outputs)==16,'Expected generated module/root output inventory differs')
outputs.append(EVIDENCE/'scratch')
require(len(outputs)==17,'Expected generated outputs plus cycle scratch inventory differs')
require(all(not p.exists() and not p.is_symlink() for p in outputs),'Task build outputs remain')
# Filter unrelated process metadata out; never stop a process. Require exact
# Python unittest invocation and exact V11 discovery root, not a name-only hit.
processes=subprocess.check_output(['ps','-axo','pid=,lstart=,command='],text=True)
live=[]
for line in processes.splitlines():
    match=re.match(r'^\s*(\d+)\s+(\w{3}\s+\w{3}\s+\d+\s+\d\d:\d\d:\d\d\s+\d{4})\s+(.*)$',line)
    if not match: continue
    try: argv=shlex.split(match[3])
    except ValueError: continue
    if not argv or not re.fullmatch(r'python(?:3(?:\.\d+)?)?',Path(argv[0]).name,re.I): continue
    if '-m' in argv and 'unittest' in argv and 'discover' in argv and str(HERE.relative_to(ROOT)) in argv:
        live.append(dict(pid=int(match[1]),start=match[2]))
require(not live,'Exact focused Python processes remain')

report={
 'schema_version':1, 'reviewer':'/root/release_fix_review',
 'recorded_at':datetime.datetime.now(datetime.timezone.utc).isoformat(),
 'status':'PASS_FOCUSED_CONTROLS_AND_GO_FOR_ONE_ROOT_OWNED_NATIVE_ATTEMPT',
 'cycle':'native-harness-focused-11', 'prior_review':record(priorpath),
 'evidence':[record(EVIDENCE/name) for name in ['receipt.json','gradle.log','stop.log']],
 'executed_tests':dict(total=177,passed=177,failed=0,errors=0,skipped=0,duration_seconds=float(summary[0][1]),
   all_raw_descriptors_match_independent_ast=True,module_counts=dict(sorted(Counter(k.split('.')[0] for k in seen).items())),
   limitation='Python synthetic source/receipt/cleanup contracts only, not native Swift execution or application runtime.'),
 'new_negative_case_assertions_reviewed':[
   'Malformed/missing/unknown/wrong-pane/duplicate/method/bool provenance rejected with fresh fixtures.',
   'One-element aliases accepted; ambiguous two-element union rejected independently by additional and combined gates.',
   'Original otherwise-valid geometry remains old-gate PASS while missing stage/action/stable-sample identity makes new-gate FAIL.',
   'Fresh identity cannot override existing pane/target visibility, foreground, keyboard, count or geometry guards.',
   'Original absent08pane fixture remains BLOCKED despite diagnostic Settings count1; additional PASS explicitly not OS authority.',
   'Missing/bool/wrong-count native contract rejected; separate native execution still required to exercise real NSPredicate.'
 ],
 'control_manifest_sha256':aggregate,'controls_rehashed_after_execution':len(current),
 'source_manifest_sha256':freeze['source']['source_manifest_sha256'],
 'source_inputs_rehashed_after_execution':len(freeze['source']['source_manifest']),
 'source_and_runner_unchanged':True,
 'cleanup':dict(stop_exit_code=0,stop_log=stop.strip(),stop_completed_seconds_after_task_finish=(stopped-finished).total_seconds(),
   cleanup_errors=[],output_paths_independently_checked_absent=len(outputs),generated_build_paths=16,cycle_scratch_paths=1,retained_outputs=[],reported_remaining_owned_workers=[],
   current_exact_python_test_processes=live,
   limitation='No global-idleness claim; reviewer started no build/test/app/background process and stopped/deleted no other-task resources.'),
 'native_authorization':dict(status='GO',executor='/root',cycle='dsc01-apphost-09',approved_control_sha256=aggregate,
   single_owned_fresh_simulator_attempt_only=True,requirements=[
    'Root sole build lane and immutable sourcefreeze02/control hash.',
    'Use unchanged finalizer including immediate Gradle stops and PID/FIFO/simulator/copy/DerivedData cleanup on all exits.',
    'Preserve raw structured/native/original-probe evidence before cleanup.',
    'Independent runtime/source-provenance/cleanup review before any DS-C01 status change.',
    'BLOCKED actual OS selection remains partially verified; no physical LAN, signed candidate, Store-qualified Xcode, Store or full-app readiness claim.',
    'Do not count artifact list as complete binary provenance: Parlor.debug.dylib is still omitted.'
   ]),
 'reviewer_execution':'Read-only metadata/source/process-identity arithmetic; not a test rerun.',
 'reviewer_arithmetic_correction':'Initial metadata-only attempt correctly halted before any report when it expected17build roots; actual unchanged helper returns16build roots. Added distinct cycle scratch absence check (17paths total). No source/test execution or application failure.'
}
path=REVIEW/'independent-v11-focused-result-native-go-01.json'
with path.open('x') as f:f.write(json.dumps(report,indent=2,ensure_ascii=False)+'\n')
print(json.dumps(dict(report=str(path),sha256=sha(path.read_bytes()),status=report['status'],native_cycle='dsc01-apphost-09',tests=177)))
