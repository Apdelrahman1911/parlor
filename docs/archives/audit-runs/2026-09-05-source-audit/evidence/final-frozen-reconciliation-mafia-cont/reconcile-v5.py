#!/usr/bin/env python3
"""Read-only independent reconciliation of frozen audit data; no build/helper execution."""
import collections
import datetime
import hashlib
import json
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path('/Users/abdelrahman/Projects/parlor')
A = ROOT / 'audit-runs/2026-09-05-source-audit'
OUT = A / 'evidence/final-frozen-reconciliation-mafia-cont'
started = datetime.datetime.now(datetime.timezone.utc).isoformat()
checks = []
inputs = {}


def sha(p):
    data = p.read_bytes()
    result = hashlib.sha256(data).hexdigest()
    inputs[str(p.relative_to(ROOT)) if ROOT in p.parents else str(p)] = result
    return result


def read(p):
    sha(p)
    return json.loads(p.read_text())


def check(name, condition, detail=None):
    checks.append(dict(check=name, status='PASS' if condition else 'FAIL', detail=detail))


def jsonlines(p):
    sha(p)
    return [json.loads(line) for line in p.read_text().splitlines()]


def flatten(value):
    yield value
    if isinstance(value, dict):
        for child in value.values():
            yield from flatten(child)
    elif isinstance(value, list):
        for child in value:
            yield from flatten(child)


freeze = read(OUT / (sys.argv[2] if len(sys.argv) > 2 else 'inputs.json'))
for item in freeze['inputs']:
    p = ROOT / item['path']
    check('frozen input: ' + item['path'], sha(p) == item['sha256'])
    if item['snapshot']:
        check('snapshot: ' + item['path'], sha(ROOT / item['snapshot']) == item['sha256'])

baseline = read(A / 'baseline.json')
current = read(A / 'canonical-register.json')
oldpath = A / 'evidence/final-apphost-closeout-history/canonical-register.json'
old = read(oldpath)
check('canonical archive hash', sha(oldpath) == 'cc8d768a240cdf7d0c880a49913ca9658c7cc5999fb22ee154e497e85164f8bb')
new_by_id = {r['id']: r for r in current['findings']}
check('all 33 prior adjudications semantically unchanged', len(old['findings']) == 33 and all(new_by_id.get(r['id']) == r for r in old['findings']))
old_wd = next(r for r in old['unconfirmed_candidates'] if r['id'] == 'WD-C4')
check('WD-C4 blocked object unchanged', current['unconfirmed_candidates'] == [old_wd])
ios = new_by_id['IOS-R1']
check('IOS-R1 evidence-only move with null severity', ios['classification'] == 'TEST/EVIDENCE GAP' and ios['category'] == 'test_or_evidence_gap' and ios['severity'] is None and ios['finder'] != ios['independent_validator'])
all_records = current['findings'] + current['unconfirmed_candidates']
counts = collections.Counter(r['classification'] for r in all_records)
expected_counts = {'CONFIRMED DEFECT':16, 'DOCUMENTATION MISMATCH':9, 'TEST/EVIDENCE GAP':6, 'FALSE POSITIVE':3, 'UNCONFIRMED — BLOCKED':1}
check('35 unique records and final category counts', len({r['id'] for r in all_records}) == len(all_records) == 35 and counts == expected_counts, dict(counts))
confirmed = [r for r in all_records if r['classification'] == 'CONFIRMED DEFECT']
check('confirmed severities unchanged', collections.Counter(r['severity'] for r in confirmed) == {'Medium':9, 'Low':7})
check('counts correctly distinguish allocated/adjudicated/no-severity', current['counts']['canonical_adjudicated_ids'] == 34 and current['counts']['total_allocated_ids'] == 35 and current['counts']['positive_without_severity'] == 1 and current['counts']['positive_adjudications'] == 31)
source_problems = []
ref_problems = []
for r in all_records:
    if r['finder'] == r['independent_validator']:
        source_problems.append([r['id'],'same finder and validator'])
    for loc in r['primary_source_locations']:
        p = ROOT / loc['repository_relative_path']
        if str(p) != loc['absolute_path'] or sha(p) != loc['sha256']:
            source_problems.append([r['id'],str(p),'identity'])
        n = len(p.read_text().splitlines())
        if n != loc['line_count'] or any(not 1 <= start <= end <= n for start,end in loc['one_based_line_ranges']):
            source_problems.append([r['id'],str(p),'ranges'])
    for ref in r['candidate_refs'] + r['validation_refs']:
        if not (A/ref).is_file():
            ref_problems.append([r['id'],ref])
    for execution in r['execution_records']:
        for ref in execution['evidence_refs']:
            if not (A/ref).exists():
                ref_problems.append([r['id'],ref])
check('all canonical source hashes, paths, ranges and distinct validators', not source_problems, source_problems)
check('all canonical dossier/execution references exist', not ref_problems, ref_problems)
findings_ids = re.findall(r'^## ([A-Z0-9-]+) — ', (A/'FINDINGS.md').read_text(), re.M)
other_ids = re.findall(r'^### ([A-Z0-9-]+) — ', (A/'OTHER_CANDIDATES.md').read_text(), re.M)
check('16 report defect sections exactly match confirmed IDs', set(findings_ids) == {r['id'] for r in confirmed} and len(findings_ids) == 16)
check('19 other report sections exactly match remaining IDs', set(other_ids) == {r['id'] for r in all_records} - set(findings_ids) and len(other_ids) == 19)

inventory = jsonlines(A/'coverage/inventory.jsonl')
ledger = jsonlines(A/'coverage/FINAL_COVERAGE.jsonl')
summary = read(A/'coverage/summary.json')
base_by_path = {r['path']:r for r in inventory}
check('735 unique inventory dispositions', len(inventory) == len(base_by_path) == len(ledger) == 735 and set(base_by_path) == {r['path'] for r in ledger})
check('coverage summary hash binding', summary['inventory_sha256'] == sha(A/'coverage/inventory.jsonl') and summary['ledger_sha256'] == sha(A/'coverage/FINAL_COVERAGE.jsonl'))
check('exact kind and disposition counts', collections.Counter(r['kind'] for r in ledger) == summary['kind_counts'] == {'text':628,'binary':6,'protected':1,'prior-audit-material':72,'generated-local':28} and collections.Counter(r['status'] for r in ledger) == summary['disposition_counts'])
receipt_lookup = {}
for item in summary['read_receipt_inputs']:
    p = A/item['path']
    check('coverage receipt input hash: ' + item['path'], sha(p) == item['sha256'])
    for number,r in enumerate(jsonlines(p),1):
        receipt_lookup[f"{item['path']}:{number}"] = r
problems = []
source_compared = 0
for row in ledger:
    original = base_by_path[row['path']]
    for key in ['path','absolute_path','git_kind','kind','sha256','lines']:
        if row.get(key) != original.get(key):
            problems.append([row['path'],key])
    if row['behavioral_verification_complete'] is not False:
        problems.append([row['path'],'behavioral overclaim'])
    if original['kind'] in ('text','binary'):
        p = ROOT/row['path']
        source_compared += 1
        if sha(p) != original['sha256']:
            problems.append([row['path'],'current source hash'])
    for ref in row['receipt_refs']:
        r = receipt_lookup.get(ref)
        if not r or r['path'] != row['path'] or (row['sha256'] is not None and r.get('sha256') != row['sha256']):
            problems.append([row['path'],ref,'bad referenced receipt'])
    if row['kind'] == 'text':
        ranges = []
        reviewers = set()
        for ref in row['receipt_refs']:
            r = receipt_lookup[ref]
            reviewers.add(r.get('reviewer'))
            ranges += r.get('raw_text_ranges_read',[]) if r.get('status') == 'INSPECTED_GENERATED_CSV' else r.get('reviewed_ranges',[])
        read_lines = set()
        for start,end in ranges:
            if not 1 <= start <= end <= row['lines']:
                problems.append([row['path'],'out-of-bound receipt'])
            read_lines.update(range(start,end+1))
        if read_lines != set(range(1,row['lines']+1)) or set(row['reviewers']) != reviewers or not reviewers:
            problems.append([row['path'],'incomplete raw reading/reviewer attribution'])
        if row['reviewed_ranges'] != [[1,row['lines']]] or row['unread_verbatim_ranges'] or row['uninspected_in_scope_ranges']:
            problems.append([row['path'],'merged coverage mismatch'])
    elif row['kind'] == 'binary':
        if row['lines'] is not None or row['reviewed_ranges'] or not any(receipt_lookup[ref].get('status') == 'BINARY_INSPECTED' for ref in row['receipt_refs']):
            problems.append([row['path'],'binary disposition'])
    elif row['sha256'] is not None:
        problems.append([row['path'],'protected/excluded unexpectedly hashed'])
check('634 current safe input hashes and all source coverage receipts reconcile', source_compared == 634 and not problems, problems)
check('139888 verbatim lines with no behavior implication', sum(r['lines'] for r in ledger if r['kind']=='text') == 139888 and summary['verbatim_read_line_count'] == 139888 and summary['complete_verbatim_text_file_count'] == 628 and summary['errors'] == [] and summary['uninspected_applicable_ranges'] == [])
audit_receipts = jsonlines(A/'coverage/AUDIT_MATERIAL_REVIEW_RECEIPTS.jsonl')
audit_problems = []
for r in audit_receipts:
    if r['receipt_ref'] not in receipt_lookup:
        audit_problems.append([r['path'],'missing input'])
    p = ROOT/r['verified_review_version_path']
    if sha(p) != r['sha256'] or sha(ROOT/r['path']) != r['current_path_sha256'] or r['attests_current_version'] != (r['sha256']==r['current_path_sha256']):
        audit_problems.append([r['path'],'version binding'])
check('239 separately counted historical/audit evidence receipts version bound', len(audit_receipts) == summary['audit_material_read_receipts_excluded_from_source_counts'] == 239 and not audit_problems, audit_problems)
legacy_targets = {str((A/p).relative_to(ROOT)) for p in ['VERIFICATION.md','verification-ledger.json','CLEANUP_LEDGER.json','evidence/final-state.json']}
legacy_rows = [r for r in audit_receipts if r['path'] in legacy_targets]
check('all4 previously stale rows now explicitly historical with matching archive/current hashes',
      len(legacy_rows)==4 and all(not r['attests_current_version'] and r['verified_review_version_path']!=r['path'] and
      sha(ROOT/r['verified_review_version_path'])==r['sha256'] and sha(ROOT/r['path'])==r['current_path_sha256'] for r in legacy_rows))
check('source coverage ledger bytes unchanged from pre-ordering-correction freeze',
      sha(A/'coverage/FINAL_COVERAGE.jsonl')=='12b627daefc1af93d3e22be6d96fd002d029e3eadff45b18be4b7dd91c54d5df')

verification = read(A/'verification-ledger.json')
gates = verification['gates']
gate_counts = collections.Counter(g['status'] for g in gates)
check('46 final gates and exact disposition counts', len({g['id'] for g in gates}) == len(gates) == 46 and gate_counts == {'PASS':19,'FAIL':14,'BLOCKED':9,'NOT_APPLICABLE':4},dict(gate_counts))
rows = []
for line in (A/'VERIFICATION.md').read_text().splitlines():
    if re.match(r'^\| [a-z0-9-]+ \| \*\*',line):
        cells = [c.strip() for c in line.split('|')[1:-1]]
        rows.append(cells)
markdown_problems = []
for row,g in zip(rows,gates):
    if row[:3] != [g['id'],'**'+g['status']+'**',g['reason']]:
        markdown_problems.append(g['id'])
    links = re.findall(r'\]\(([^)]+)\)',row[3])
    if links != [str(Path(e['ref']).relative_to(A.relative_to(ROOT))) for e in g['evidence']]:
        markdown_problems.append([g['id'],'evidence links'])
    if any(not (ROOT/e['ref']).exists() for e in g['evidence']):
        markdown_problems.append([g['id'],'missing evidence'])
check('all 46 Markdown rows/evidence exactly reflect JSON', len(rows)==len(gates) and not markdown_problems, markdown_problems)
check('NOT_READY, no approved artifacts, no clean-worktree claim', verification['verdict']=='NOT_READY' and verification['artifacts']==[] and verification['candidate']['clean'] is False)
check('every blocked gate has matching owned manual action', {g['id'] for g in gates if g['status']=='BLOCKED'} == {a['id'] for a in verification['manual_actions']} and all(g.get('owner') for g in gates if g['status']=='BLOCKED'))
schema = read(A/freeze['current_evidence']['schema'])
check('schema execution is bound to exact final ledger', schema['exit_code']==0 and schema['ledger_sha256']==sha(A/'verification-ledger.json') and schema['script_sha256']==sha(Path(schema['command'][2])))
root_integrity = read(A/freeze['current_evidence']['root_integrity'])
prior_integrity_path = A/'evidence/final-root-integrity-01.json'
prior_integrity = read(prior_integrity_path)
check('historical count receipt re-reconciled against unchanged current canonical and coverage counts',
      sha(prior_integrity_path)=='527e25db3cc9e465b11d2f8cee1543c4b2b2155ea6822c23fdffa3cab1f202fa' and
      prior_integrity['classification_counts']==counts and prior_integrity['numbered_records_checked']==len(all_records) and
      prior_integrity['inventory_dispositions_checked']==len(ledger))
check('current narrow integrity02 independently reconciled against all its frozen output hashes',
      root_integrity['status']=='PASS' and root_integrity['errors']==[] and root_integrity['material_receipts_checked']==239 and
      root_integrity['final_state_exactly_embedded'] is True and len(root_integrity['frozen_hashes'])==10 and
      all(sha(A/path)==digest for path,digest in root_integrity['frozen_hashes'].items()))
link_count = 0
missing_links = []
for filename in ['README.md','FINAL_REPORT.md','CONTINUATION.md','FINDINGS.md','OTHER_CANDIDATES.md','COVERAGE.md','RESEARCH_AND_CLEANUP.md','VERIFICATION.md']:
    for ref in re.findall(r'\]\(([^)]+)\)',(A/filename).read_text()):
        if '://' in ref or ref.startswith('#'):
            continue
        link_count += 1
        if not (A/ref.split('#',1)[0]).exists():
            missing_links.append([filename,ref])
check('all 247 final document local links resolve', not missing_links and link_count == prior_integrity['local_links_checked'] == 247,dict(count=link_count,missing=missing_links))

generation = read(A/freeze['current_evidence']['generation'])
for command in generation['commands']:
    check('generation binding: '+Path(command['command'][2]).name, command['exit_code']==0 and sha(Path(command['command'][2]))==command['script_sha256'] and sha(A/command['log'])==command['log_sha256'])
generation_names = [Path(c['command'][2]).name for c in generation['commands']]
check('final preservation checker follows verification assembler and is last aggregate writer',
      generation_names[-2:]==['assemble_verification.py','final_preservation_check.py'])

cycles = {}
all_cycle_values = {}
for p in sorted((A/'evidence').glob('*/receipt.json')):
    value = read(p)
    all_cycle_values[str(p.relative_to(A))] = value
    if isinstance(value,dict):
        cycles[str(p.relative_to(A))] = value
cleanup = read(A/'CLEANUP_LEDGER.json')
projected = cleanup['primary_gradle_cycles']+cleanup['other_native_probe_or_companion_receipts']
check('all 31 object receipts projected; one research-list receipt correctly excluded', len(projected)==len(cycles)==31 and len(all_cycle_values)==32 and {r['receipt'] for r in projected}==set(cycles))
projection_problems = []
for item in projected:
    value = cycles[item['receipt']]
    for key,target in [('status','raw_status'),('finished_at','finished_at'),('exit_code','exit_code')]:
        if value.get(key)!=item.get(target):
            projection_problems.append([item['receipt'],key])
    if item['sha256'] != sha(A/item['receipt']):
        projection_problems.append([item['receipt'],'hash'])
    if item['command'] != value.get('command',value.get('commands')):
        projection_problems.append([item['receipt'],'command'])
    if 'cleanup_fields' in item:
        expected = {k:v for k,v in value.items() if any(word in k for word in ('cleanup','stop','removed','remaining','absent','shutdown','delete','owned_temp'))}
        if expected != item['cleanup_fields']:
            projection_problems.append([item['receipt'],'cleanup fields'])
    else:
        for key in ('stop_exit_code','stopped_at','removed_outputs','cleanup_errors','remaining_outputs','cleanup_completed_at','cleanup_method','source_before','source_after','report_collection_error'):
            if item.get(key)!=value.get(key):
                projection_problems.append([item['receipt'],key])
        if not (item['cleanup_status']=='PASS' and value.get('stop_exit_code')==0 and value.get('cleanup_errors')==[] and value.get('remaining_outputs')==[]):
            projection_problems.append([item['receipt'],'direct cleanup'])
check('every generated cleanup projection equals raw receipt values', not projection_problems,projection_problems)
check('16 direct cycles raw 7 PASS 9 FAIL; all cleanup PASS', len(cleanup['primary_gradle_cycles'])==16 and collections.Counter(r['raw_status'] for r in cleanup['primary_gradle_cycles'])=={'PASS':7,'FAIL':9} and cleanup['primary_cleanup_counts']=={'PASS':16})
check('cycle kinds not conflated', cleanup['cycle_kind_counts']==dict(direct_gradle=16,executed_android_harness=1,pre_gradle_android_attempt=1,source_aware_ios_apphost=2,secondary_apple_cleanup_supplements=1))

preservation = read(A/'evidence/final-preservation-and-hygiene.json')
check('preservation source comparison equals actual retained final-state', preservation['source_comparison']==read(A/'evidence/final-state.json'))
check('all 32 final checker inputs exactly hash-bound', len(preservation['inspected_cycle_receipts'])==len(all_cycle_values)==32 and {r['path'] for r in preservation['inspected_cycle_receipts']}==set(all_cycle_values) and all(r['sha256']==sha(A/r['path']) for r in preservation['inspected_cycle_receipts']))
expected_identities = {}
expected_uuids = set()
for value in cycles.values():
    for v in flatten(value):
        if isinstance(v,dict):
            if all(k in v for k in ('pid','start','role','proof')):
                expected_identities[(int(v['pid']),v['start'])] = {k:v[k] for k in ('pid','start','role','proof')}
            if v.get('owned_uuid'):
                expected_uuids.add(v['owned_uuid'])
actual_identities = {(int(v['pid']),v['start']):v for v in preservation['exact_pid_start_identities_checked']}
check('all 135 final attested PID/start identities derived from raw receipts', len(actual_identities)==135 and actual_identities==expected_identities)
check('all 7 exact simulator UUIDs derived from raw receipts', {v['uuid'] for v in preservation['owned_simulator_directories']}==expected_uuids and len(expected_uuids)==7 and all(v['device_directory_exists'] is False for v in preservation['owned_simulator_directories']))
empty_fields = ['coverage_errors','unfinished_cycle_receipts','remaining_output_directories','remaining_owned_temporaries','current_owned_process_matches','exact_owned_processes_still_alive','android_port_check_errors','current_gradle_8_13_processes']
check('final observed cleanup is scoped PASS with no remaining recorded resources', all(preservation[k]==[] for k in empty_fields) and preservation['phase']=='COMPLETE' and preservation['source_preservation_status']==preservation['cleanup_status']=='PASS' and all(v['listener_pids']==[] for v in preservation['owned_android_adb_port_checks']))
check('final output paths cover all 13 modules plus root/convention/iOS', len(preservation['output_directories_checked'])==17 and 'iosApp/build' in preservation['output_directories_checked'] and 'build-logic/convention/build' in preservation['output_directories_checked'])
secondary = read(A/'evidence/iosr1-apphost-02-secondary-cleanup/receipt.json')
initial = read(A/'evidence/iosr1-apphost-02-cleanup-independent-whodunit-cont.json')
post = read(A/'evidence/iosr1-apphost-02-post-secondary-cleanup-independent-whodunit-cont.json')
check('original incomplete cleanup kept as failed evidence', initial['cleanup_verified'] is False and cycles['evidence/iosr1-apphost-02/receipt.json']['cleanup_status']=='PASS')
check('secondary correction bound to independent postcheck', post['root_supplemental_receipt_sha256']==sha(A/'evidence/iosr1-apphost-02-secondary-cleanup/receipt.json') and post['status']=='PASS' and secondary['cleanup_status']=='PASS' and secondary['cleanup_errors']==[] and secondary['remaining_owned_secondary_temporaries']==[] and all(post['owned_path_absence'].values()))
check('final checker covers both exact secondary path aliases', len(preservation['owned_secondary_temporary_roots_checked'])==2 and all(p.endswith('/ibtoold-67921') for p in preservation['owned_secondary_temporary_roots_checked']))
check('aggregate cleanup after correction not original runner-only', cleanup['aggregate_cleanup_status']=='PASS' and cleanup['final_hygiene_status']=='PASS' and len(cleanup['secondary_cleanup_supplements'])==1 and cleanup['secondary_cleanup_supplements'][0]['status']=='PASS')

android = read(A/'evidence/android-managed-02/receipt.json')
av = read(A/'evidence/android-managed-02-independent-whodunit-cont.json')
check('Android runtime validation exact receipt binding', av['verdict']=='PASS' and android['exit_code']==0 and android['runtime_evidence_status']=='PASS' and any(v['path']==str((A/'evidence/android-managed-02/receipt.json').relative_to(ROOT)) and v['sha256']==sha(A/'evidence/android-managed-02/receipt.json') for v in av['evidence_hashes']))
android_cases = []
for p in (A/'evidence/android-managed-02').rglob('*.xml'):
    if p.name.startswith('TEST-'):
        sha(p)
        root = ET.fromstring(p.read_bytes())
        android_cases += list(root.iter('testcase'))
check('Android actual XML: 3 passed, no failures/errors/skips', len(android_cases)==3 and all(not any(n.tag in ('failure','error','skipped') for n in c) for c in android_cases),[c.attrib.get('classname','')+'.'+c.attrib['name'] for c in android_cases])

app = read(A/'evidence/iosr1-apphost-02/receipt.json')
app_review = read(A/'evidence/iosr1-apphost-02-independent-whodunit-cont.json')
probe = read(A/'evidence/iosr1-apphost-02/probe-result.json')
check('iOS independent result exact raw receipt and probe binding', app_review['root_receipt']['sha256']==sha(A/'evidence/iosr1-apphost-02/receipt.json') and app_review['binding']['all_match'] is True and app_review['runtime_observation']==probe and any(v['path']==str((A/'evidence/iosr1-apphost-02/probe-result.json').relative_to(ROOT)) and v['sha256']==sha(A/'evidence/iosr1-apphost-02/probe-result.json') for v in app_review['artifact_integrity']))
xcsummary = read(A/'evidence/iosr1-apphost-02/xcresult-summary.json')
xctree = read(A/'evidence/iosr1-apphost-02/xcresult-tests.json')
cases = [v for v in flatten(xctree) if isinstance(v,dict) and v.get('nodeType')=='Test Case']
check('one actual copied XCTest passes, not storage health', len(cases)==1 and cases[0]['nodeIdentifier']=='IOSAppLaunchUITests/testAuditProductionRecoverySourceAttribution()' and cases[0]['result']=='Passed' and xcsummary['totalTestCount']==xcsummary['passedTests']==1 and xcsummary['failedTests']==xcsummary['skippedTests']==xcsummary['expectedFailures']==0)
check('production result vs later native status explicitly separated', probe['original_home_invocation_intercepted'] is False and probe['local']=={'kind':'success_empty','entry_count':0} and probe['multiplayer']=={'kind':'failure','error_category':'secure_storage_unavailable'} and probe['combined']=={'has_unavailable_source':True,'local_count':0,'has_multiplayer':False} and probe['native_corroboration']=={'origin':'subsequent_same_app_equivalent_read','original_production_status':False,'os_status':-34018,'result_present':False,'constant_not_found':-25300,'constant_missing_entitlement':-34018})
first = cycles['evidence/iosr1-apphost-01/receipt.json']
first_summary = read(A/'evidence/iosr1-apphost-01/xcresult-summary.json')
first_tests = read(A/'evidence/iosr1-apphost-01/xcresult-tests.json')
check('failed first apphost retained without fake test outcome', first['framework_exit_code']==0 and first['xcodebuild_exit_code']==65 and first['runtime_evidence_status']=='NOT_RUN' and first_summary['totalTestCount']==first_summary['passedTests']==first_summary['failedTests']==first_summary['skippedTests']==0 and first_tests['testNodes']==[])
manifest = read(A/'evidence/iosr1-apphost-02/input-manifest.json')
check('all 12 actual apphost inputs still match', len(manifest['files'])==12 and all(sha(ROOT/v['path'])==v['sha256'] for v in manifest['files']))
header = A/'evidence/iosr1-apphost-02/ComposeApp.generated.h'
excerpt = A/'evidence/iosr1-apphost-02/probe-export.h'
check('actual void callback export byte-exact at153–166', '\n'.join(header.read_text().splitlines()[152:166])+'\n'==excerpt.read_text() and '- (void)startOnJson:(void (^)(NSString *))onJson' in excerpt.read_text() and sha(header)==app['header_sha256'])
stop_delays = []
for cycle in ('iosr1-apphost-01','iosr1-apphost-02'):
    value = cycles['evidence/'+cycle+'/receipt.json']
    commands = value['commands']
    for n,c in enumerate(commands):
        if c['command'][0]=='xcodebuild' or (c['command'][0]=='./gradlew' and '--stop' not in c['command']):
            following = commands[n+1]
            delay=(datetime.datetime.fromisoformat(following['started_at'])-datetime.datetime.fromisoformat(c['finished_at'])).total_seconds()
            stop_delays.append(dict(cycle=cycle,task=c['command'][0],delay_seconds=delay))
            check('immediate stop after '+cycle+'/'+c['command'][0],following['command']==['./gradlew','--stop'] and following['exit_code']==0 and 0<=delay<1,delay)
    check('three successful final/immediate stops: '+cycle,len(value['gradle_stops'])==3 and all(c['exit_code']==0 for c in value['gradle_stops']))

# Independent safe Git metadata comparison only; never checkout/reset/stash/mutate refs.
def git(*args):
    return subprocess.check_output(['git',*args],cwd=ROOT,text=True).strip()
state = dict(branch=git('branch','--show-current'),commit=git('rev-parse','HEAD'),tree=git('rev-parse','HEAD^{tree}'),tracked_status=git('status','--porcelain=v1','--untracked-files=no'))
check('current branch/full SHA/tree/tracked state unchanged', all(state[k]==baseline[k] for k in ('branch','commit','tree')) and not state['tracked_status'])
check('current refs and stashes unchanged', git('for-each-ref','--format=%(refname) %(objectname)')==baseline['refs'].strip() and git('stash','list','--format=%gd %H')==baseline['stashes'].strip())
def nonaudit(s):
    return sorted(line for line in s.splitlines() if 'audit-runs/' not in line)
check('existing untracked file names unchanged',nonaudit(git('status','--porcelain=v1','--untracked-files=all'))==nonaudit(baseline['status_including_audit_path']))
check('git diff check',subprocess.run(['git','diff','--check'],cwd=ROOT,capture_output=True).returncode==0)

end_drift = [item['path'] for item in freeze['inputs'] if sha(ROOT/item['path'])!=item['sha256']]
check('all explicitly frozen final inputs remain unchanged through reconciliation', not end_drift, end_drift)

result=dict(reviewer='/root/mafia_cont',scope='Frozen final outputs and bound raw evidence; not new complete application reading or live cleanup execution',started_at=started,finished_at=datetime.datetime.now(datetime.timezone.utc).isoformat(),status='FAIL' if any(c['status']=='FAIL' for c in checks) else 'PASS',checks=checks,check_counts=dict(collections.Counter(c['status'] for c in checks)),classification_counts=dict(counts),gate_counts=dict(gate_counts),stop_delays=stop_delays,inputs=inputs,limits=['No root helper/assembler/final checker imported or executed; frozen files untouched.','All generated JSON loaded; selected raw text ranges read separately. Machine integrity checks do not certify human semantic reading or app correctness.','No process/native/device/temp inspection by this reviewer; cleanup assessment reconciles exact bound root/independent receipts only.','No excluded private/configuration/player/prior-audit contents read or hashed. No release artifact byte rehash after mandatory cleanup.'],cleanup='Only this required Python source and a bounded JSON receipt generated, with -B. No build, daemon, device, server, subprocess left running.')
output=OUT/sys.argv[1]
with output.open('x') as handle:
    json.dump(result,handle,indent=2);handle.write('\n')
print(json.dumps({k:result[k] for k in ('status','check_counts','classification_counts','gate_counts')},indent=2))
for c in checks:
    if c['status']=='FAIL': print(json.dumps(c))
sys.exit(0 if result['status']=='PASS' else 1)
